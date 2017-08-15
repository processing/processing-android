/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2017 The Processing Foundation
 
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.mode.android.tools;

import com.android.repository.api.*;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.sdklib.tool.SdkManagerCli;

import processing.app.Base;
import processing.app.Preferences;
import processing.app.tools.Tool;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("serial")
public class SDKUpdater extends JFrame implements PropertyChangeListener, Tool {
  final static private int NUM_ROWS = 10; 
  final static private int COL_WIDTH = Toolkit.zoom(220);
  
  final static private int BORDER = Toolkit.zoom(13);
  final static private int GAP = Toolkit.zoom(13);
  final static private int INSET = Toolkit.zoom(1);
  final static private int BUTTON_WIDTH = Toolkit.zoom(75);
  final static private int BUTTON_HEIGHT = Toolkit.zoom(25);
  
  private final Vector<String> columns = new Vector<>(Arrays.asList(
      "Package name", "Installed version", "Available update"));
  private static final String PROPERTY_CHANGE_QUERY = "query";

  private File sdkFolder;

  private QueryTask queryTask;
  private DownloadTask downloadTask;
  private boolean downloadTaskRunning;

  private Vector<Vector<String>> packageList;
  private DefaultTableModel packageTable;  
  private int numUpdates;

  private JProgressBar progressBar;
  private JLabel status;
  private JButton actionButton;
  private JTable table;

  
  @Override
  public void init(Base base) {
    createLayout(base.getActiveEditor() == null);
  }

  
  @Override
  public void run() {
    setVisible(true);
    String path = Preferences.get("android.sdk.path");
    sdkFolder = new File(path);    
    queryTask = new QueryTask();
    queryTask.addPropertyChangeListener(this);
    queryTask.execute();
    status.setText("Querying packages...");    
  }

  
  @Override
  public String getMenuTitle() {   
    return "SDK Updater";
  }
  
  
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    switch (evt.getPropertyName()) {
    case PROPERTY_CHANGE_QUERY:
      progressBar.setIndeterminate(false);
      if (numUpdates == 0) {
        actionButton.setEnabled(false);
        status.setText("No updates available");
      } else {
        actionButton.setEnabled(true);
        if (numUpdates == 1) {
          status.setText("1 update found!");
        } else { 
          status.setText(numUpdates + " updates found!");                    
        }                    
      }
      break;
    }
  }

  class QueryTask extends SwingWorker<Object, Object> {
    ProgressIndicator progress;
    
    QueryTask() {
      super();
      progress = new ConsoleProgressIndicator();
    }
    
    @Override
    protected Object doInBackground() throws Exception {
      numUpdates = 0;
      packageList = new Vector<>();

      /* Following code is from listPackages() of com.android.sdklib.tool.SdkManagerCli
               with some changes
       */
      AndroidSdkHandler mHandler = AndroidSdkHandler.getInstance(sdkFolder);
      
      FileSystemFileOp fop = (FileSystemFileOp) FileOpUtils.create();
      RepoManager mRepoManager = mHandler.getSdkManager(progress);
      mRepoManager.loadSynchronously(0, progress, new LegacyDownloader(fop, new SettingsController() {
        @Override
        public boolean getForceHttp() {
          return false;
        }

        @Override
        public void setForceHttp(boolean b) { }

        @Override
        public Channel getChannel() {
          return null;
        }
      }), null);

      RepositoryPackages packages = mRepoManager.getPackages();      
      HashMap<String, List<String>> installed = new HashMap<String, List<String>>();
      for (LocalPackage local : packages.getLocalPackages().values()) {
        String path = local.getPath();
        String name = local.getDisplayName();
        String ver = local.getVersion().toString();
        // Remove version from the display name
        int rev = name.indexOf(", rev");
        if (-1 < rev) {
          name = name.substring(0, rev);
        }
        int maj = ver.indexOf(".");
        if (-1 < maj) {
          String major = ver.substring(0, maj);
          int pos = name.indexOf(major);
          if (-1 < pos) {
            name = name.substring(0, pos).trim();  
          }
        }
        installed.put(path, Arrays.asList(name, ver));
      }

      HashMap<String, List<String>> updated = new HashMap<String, List<String>>();
      for (UpdatablePackage update : packages.getUpdatedPkgs()) {              
        String path = update.getPath();
        String loc = update.getLocal().getVersion().toString();
        String rem = update.getRemote().getVersion().toString();              
        updated.put(path, Arrays.asList(loc, rem));
      }

      for (String path: installed.keySet()) {
        Vector<String> info = new Vector<>();
        List<String> locInfo = installed.get(path);
        info.add(locInfo.get(0));
        info.add(locInfo.get(1));
        if (updated.containsKey(path)) {
          String upVer = updated.get(path).get(1);
          info.add(upVer);  
          numUpdates++;
        } else {
          info.add("");
        }
        packageList.add(info);  
      }

      return null;
    }

    @Override
    protected void done() {
      super.done();

      try {
        get();
        firePropertyChange(PROPERTY_CHANGE_QUERY, "query", "SUCCESS");
        
        if (packageList != null) {
          packageTable.setDataVector(packageList, columns);
          packageTable.fireTableDataChanged();
        }
      } catch (InterruptedException | CancellationException e) {
        this.cancel(false);
      } catch (ExecutionException e) {
        this.cancel(true);
        JOptionPane.showMessageDialog(null,
            e.getCause().toString(), "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
      }
    }
  }

  class DownloadTask extends SwingWorker<Object, Object> {
    ProgressIndicator progress;

    DownloadTask() {
      super();   
      progress = new ConsoleProgressIndicator();
    }
    
    @Override
    protected Object doInBackground() throws Exception {
      downloadTaskRunning = true;

      /* Following code is from installPackages() of com.android.sdklib.tool.SdkManagerCli
               with some changes
       */
      AndroidSdkHandler mHandler = AndroidSdkHandler.getInstance(sdkFolder);

      FileSystemFileOp fop = (FileSystemFileOp) FileOpUtils.create();
      CustomSettings settings = new CustomSettings();
      Downloader downloader = new LegacyDownloader(fop, settings);

      RepoManager mRepoManager = mHandler.getSdkManager(progress);
      mRepoManager.loadSynchronously(0, progress, downloader, settings);
      
      List<RemotePackage> remotes = new ArrayList<>();
      for (String path : settings.getPaths(mRepoManager)) {
        RemotePackage p = mRepoManager.getPackages().getRemotePackages().get(path);
        if (p == null) {
          progress.logWarning("Failed to find package " + path);
          throw new SdkManagerCli.CommandFailedException();
        }
        remotes.add(p);
      }
      remotes = InstallerUtil.computeRequiredPackages(
          remotes, mRepoManager.getPackages(), progress);
      if (remotes != null) {
        for (RemotePackage p : remotes) {
          Installer installer = SdkInstallerUtil.findBestInstallerFactory(p, mHandler)
              .createInstaller(p, mRepoManager, downloader, mHandler.getFileOp());
          if (!(installer.prepare(progress) && installer.complete(progress))) {
            // there was an error, abort.
            throw new SdkManagerCli.CommandFailedException();
          }
        }
      } else {
        progress.logWarning("Unable to compute a complete list of dependencies.");
        throw new SdkManagerCli.CommandFailedException();
      }

      return null;
    }

    @Override
    protected void done() {
      super.done();

      try {
        get();
        actionButton.setEnabled(false);
        status.setText("Refreshing packages...");
        queryTask = new QueryTask();
        queryTask.addPropertyChangeListener(SDKUpdater.this);
        queryTask.execute();
      } catch (InterruptedException | CancellationException e) {
        this.cancel(true);
      } catch (ExecutionException e) {
        this.cancel(true);
        JOptionPane.showMessageDialog(null,
            e.getCause().toString(), "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
      } finally {
        downloadTaskRunning = false;
        progressBar.setIndeterminate(false);
      }
    }

    class CustomSettings implements SettingsController {
      /* Dummy implementation with some necessary methods from the original
               implementation in com.android.sdklib.tool.SdkManagerCli
       */
      @Override
      public boolean getForceHttp() {
        return false;
      }

      @Override
      public void setForceHttp(boolean b) { }

      @Override
      public Channel getChannel() {
        return null;
      }

      public java.util.List<String> getPaths(RepoManager mgr) {
        List<String> updates = new ArrayList<>();
        for(UpdatablePackage upd : mgr.getPackages().getUpdatedPkgs()) {
          if(!upd.getRemote().obsolete()) {
            updates.add(upd.getRepresentative().getPath());
          }
        }
        return updates;
      }
    }
  }

  private void createLayout(final boolean standalone) {
    setTitle(getMenuTitle());
    
    Container outer = getContentPane();
    outer.removeAll();

    Box verticalBox = Box.createVerticalBox();
    verticalBox.setBorder(new EmptyBorder(BORDER, BORDER, BORDER, BORDER));
    outer.add(verticalBox);

    /* Packages panel */
    JPanel packagesPanel = new JPanel();

    BoxLayout boxLayout = new BoxLayout(packagesPanel, BoxLayout.Y_AXIS);
    packagesPanel.setLayout(boxLayout);

    // Packages table
    packageTable = new DefaultTableModel(NUM_ROWS, columns.size()) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return String.class;
      }
    };
    
    table = new JTable(packageTable) {
      @Override
      public String getColumnName(int column) {
        return columns.get(column);
      }
    };    
    table.setFillsViewportHeight(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    table.setRowHeight(Toolkit.zoom(table.getRowHeight()));
    Dimension dim = new Dimension(table.getColumnCount() * COL_WIDTH, 
                                  table.getRowHeight() * NUM_ROWS);
    table.setPreferredScrollableViewportSize(dim);
    
    packagesPanel.add(new JScrollPane(table));

    JPanel controlPanel = new JPanel();
    GridBagLayout gridBagLayout = new GridBagLayout();
    controlPanel.setLayout(gridBagLayout);

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(INSET, INSET, INSET, INSET);
    
    status = new JLabel();
    status.setText("Starting up...");
    gbc.gridx = 0;
    gbc.gridy = 0;
    controlPanel.add(status, gbc);

    // Using an indeterminate progress bar from now until we learn 
    // how to update the fraction of the query/download process:
    // https://github.com/processing/processing-android/issues/362
    progressBar = new JProgressBar();
    progressBar.setIndeterminate(true);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanel.add(progressBar, gbc);

    actionButton = new JButton("Update"); // handles Update/Cancel
    actionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (downloadTaskRunning) { // i.e button state is Cancel
          cancelTasks();
        } else { // i.e button state is Update
          downloadTask = new DownloadTask();
          progressBar.setIndeterminate(true);
          downloadTask.execute();

          // getFraction() always returns 0.0, needs to be set somewhere (??) 
//          Thread update = new Thread() {
//            @Override
//            public void run() {
//              while (downloadTaskRunning) {
//                try {
//                  Thread.sleep(100);
//                } catch (InterruptedException e) { }              
//                System.out.println("Updating: " + downloadTask.progress.getFraction());                  
//              }
//            }
//          };
//          update.start();    
          
          status.setText("Downloading available updates...");
          actionButton.setText("Cancel");
        }
      }
    });
    actionButton.setEnabled(false);
    actionButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
    gbc.gridx = 1;
    gbc.gridy = 0; 
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;   
    controlPanel.add(actionButton, gbc);

    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        cancelTasks();
        if (standalone) {
          System.exit(0);
        } else {
          setVisible(false);
        }
      }
    };
    
    JButton closeButton = new JButton("Close");
    closeButton.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
    closeButton.addActionListener(disposer);
    closeButton.setEnabled(true);
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanel.add(closeButton, gbc);

    verticalBox.add(packagesPanel);
    verticalBox.add(Box.createVerticalStrut(GAP));
    verticalBox.add(controlPanel);
    pack();

    JRootPane root = getRootPane();
    root.setDefaultButton(closeButton);
    processing.app.ui.Toolkit.registerWindowCloseKeys(root, disposer);
    processing.app.ui.Toolkit.setIcon(this);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        cancelTasks();
        super.windowClosing(e);
      }
    });
    
    registerWindowCloseKeys(getRootPane(), disposer);    
    
    setLocationRelativeTo(null);
    setResizable(false);
    setVisible(false);
  }
  
  public void cancelTasks() {
    queryTask.cancel(true);
    if (downloadTaskRunning) {
      downloadTask.cancel(true);
      status.setText("Download canceled");
      JOptionPane.showMessageDialog(null,
          "Download canceled", "Warning", JOptionPane.WARNING_MESSAGE);
      actionButton.setText("Update");
    }
  }
  
  
  /**
   * Registers key events for a Ctrl-W and ESC with an ActionListener
   * that will take care of disposing the window.
   */
  static public void registerWindowCloseKeys(JRootPane root,
                                             ActionListener disposer) {
    KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);

    int modifiers = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    stroke = KeyStroke.getKeyStroke('W', modifiers);
    root.registerKeyboardAction(disposer, stroke,
                                JComponent.WHEN_IN_FOCUSED_WINDOW);
  } 
}
