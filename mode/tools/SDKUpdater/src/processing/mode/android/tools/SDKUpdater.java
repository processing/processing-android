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
import com.android.sdklib.tool.sdkmanager.SdkManagerCli;

import processing.app.Base;
import processing.app.Messages;
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
import java.util.*;
import java.util.List;
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
  
  private final Vector<String> columns_tools = new Vector<>(Arrays.asList("Select",
      "Package name", "Installed version", "Available update"));
  private final Vector<String> columns_platforms = new Vector<>(Arrays.asList("Select","Platform",
          "Revision","Status"));
  private static final String PROPERTY_CHANGE_QUERY = "query";

  private File sdkFolder;

  private QueryTask queryTask;
  private DownloadTask downloadTask;
  private boolean downloadTaskRunning;

  private Vector<Vector> packageList;
  private Vector<Vector> platformList;
  private DefaultTableModel packageTable;
  private DefaultTableModel platformTable;
  private int numUpdates;

  private JProgressBar progressBar;
  private JProgressBar progressBarPlatform;
  private JLabel status;
  private JLabel statusPlatform;
  private JButton actionButton;
  private JButton actionButtonPlatform;
  private JTable table;
  private JTable tablePlatforms;
  private JTabbedPane tabs;

  private ArrayList<String> packagePathsList;
  private ArrayList<String> platformPathsList;

  
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
    statusPlatform.setText("Querying packages... ");
  }

  
  @Override
  public String getMenuTitle() {   
    return "menu.android.sdk_updater";
  }
  

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    switch (evt.getPropertyName()) {
    case PROPERTY_CHANGE_QUERY:
      progressBar.setIndeterminate(false);
      progressBarPlatform.setIndeterminate(false);
      actionButtonPlatform.setEnabled(true);
      statusPlatform.setText("Install a platform");
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
      platformList = new Vector<>();
      packagePathsList = new ArrayList<>();
      platformPathsList = new ArrayList<>();

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

      HashMap<String, List<String>> availSDKPlatforms = new HashMap<String, List<String>>();
      for (RemotePackage remote : packages.getRemotePackages().values()){
        String path = remote.getPath();
        int pathIndex = path.indexOf(";");
        if(pathIndex!=-1 && path.substring(0,pathIndex).equals("platforms")) {
          String name = remote.getDisplayName();
          int platformIndex = name.indexOf("Platform");
          String platformName = name.substring(platformIndex);
          String revision = remote.getVersion()+"";
          availSDKPlatforms.put(path,Arrays.asList(platformName,revision));
        }
      }

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

      for (String path: availSDKPlatforms.keySet()) {
        Vector info = new Vector(); //name,ver,installed
        List<String> platformInfo = availSDKPlatforms.get(path);
        platformPathsList.add(path);
        info.add(false);
        info.add(platformInfo.get(0));
        info.add(platformInfo.get(1));
        if (installed.containsKey(path)){
          info.add("Installed");
        } else {
          info.add("Not Installed");
        }
        platformList.add(info);
      }

      for (String path: installed.keySet()) {
        Vector info = new Vector();
        List locInfo = installed.get(path);
        if (!isPlatform(path)) {
          packagePathsList.add(path);
          info.add(false);
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
      }

      Collections.sort(platformList,comparePlatformInfo);
      Collections.sort(platformPathsList,comparePlatformPath);
      return null;
    }

    @Override
    protected void done() {
      super.done();

      try {
        get();
        firePropertyChange(PROPERTY_CHANGE_QUERY, "query", "SUCCESS");

        if (platformList != null) {
          platformTable.setDataVector(platformList, columns_platforms);
          platformTable.fireTableDataChanged();
        }

        if (packageList != null) {
          packageTable.setDataVector(packageList, columns_tools);
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

    private Boolean isPlatform(String path){
      int end_pos = path.indexOf(";");
      String pathType = end_pos != -1 ? path.substring(0,end_pos) : "" ;
      if (pathType.equals("platforms")) {
        return true;
      }
      return false;
    }

    Comparator<Vector> comparePlatformInfo = new Comparator<Vector>() {
      @Override
      public int compare(Vector o1, Vector o2) {
        String name1 = (String) o1.get(1);
        String name2 = (String) o2.get(1);
        int version1 = Integer.parseInt(name1.substring(name1.indexOf(" ")+1));
        int version2 = Integer.parseInt(name2.substring(name2.indexOf(" ")+1));
        return version2 - version1;
      }
    };

    Comparator<String> comparePlatformPath = new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        int version1 = Integer.parseInt(o1.substring(o1.indexOf("-")+1));
        int version2 = Integer.parseInt(o2.substring(o2.indexOf("-")+1));
        return version2 - version1;
      }
    };
  }

  class DownloadTask extends SwingWorker<Object, Object> {
    ProgressIndicator progress;
    JProgressBar progressBar;
    Boolean isPlatform;

    DownloadTask(JProgressBar progressBar, Boolean isPlatform) {
      super();   
      progress = new ConsoleProgressIndicator();
      this.progressBar = progressBar;
      this.isPlatform = isPlatform;
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
      for (String path : settings.getPaths(mRepoManager,isPlatform)) {
        RemotePackage p = mRepoManager.getPackages().getRemotePackages().get(path);
        if (p == null) {
          progress.logWarning("Failed to find package " + path);
          throw new SdkManagerCli.CommandFailedException();
        }
        remotes.add(p);
      }

      if (remotes.size() == 0) {
        actionButtonPlatform.setText("Install");
        actionButton.setText("Update");
        Messages.showWarning("SDK Updater","No platform was selected");
        return null;
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
        tabs.setEnabled(true);
        status.setText("Refreshing packages...");
        statusPlatform.setText("Refreshing packages...");
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
        this.progressBar.setIndeterminate(false);
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

      public java.util.List<String> getPaths(RepoManager mgr,Boolean isPlatform) {
        List<String> updates = new ArrayList<>();
//        for(UpdatablePackage upd : mgr.getPackages().getUpdatedPkgs()) {
//          if(!upd.getRemote().obsolete()) {
//            updates.add(upd.getRepresentative().getPath());
//          }
//        }
        if (isPlatform) {
          for (int i = 0; i < platformTable.getRowCount(); i++) {
            if ((Boolean) platformTable.getValueAt(i, 0)) {
              updates.add(platformPathsList.get(i));
            }
          }
        } else {
          for (int i = 0; i < packageTable.getRowCount(); i++) {
            if ((Boolean) packageTable.getValueAt(i, 0)) {
              updates.add(packagePathsList.get(i));
            }
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

    Box verticalBox1 = Box.createVerticalBox();
    verticalBox1.setBorder(new EmptyBorder(BORDER, BORDER, BORDER, BORDER));

    tabs = new JTabbedPane();
    tabs.add("SDK Tools",verticalBox);
    tabs.add("Platforms",verticalBox1);
    outer.add(tabs);

    /* ------------------------- TAB 1 ------------------------------ */
    /* Packages panel */
    JPanel packagesPanel = new JPanel();
    BoxLayout boxLayout = new BoxLayout(packagesPanel, BoxLayout.Y_AXIS);
    packagesPanel.setLayout(boxLayout);

    // Packages table
    packageTable = new DefaultTableModel(NUM_ROWS, columns_tools.size()) {
      @Override
      public boolean isCellEditable(int row, int column) {
        if (column == 0) return true;
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        if(columnIndex == 0) return Boolean.class;
        return String.class;
      }
    };

    table = new JTable(packageTable) {
      @Override
      public String getColumnName(int column) {
        return columns_tools.get(column);
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
          downloadTask = new DownloadTask(progressBar,false);
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
          tabs.setEnabled(false);
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
    /* -------------------------------- END OF TAB 1 -------------------- */
    /* -------------------------------- TAB 2 ---------------------------- */
    /* platforms panel */
    JPanel platformsPanel = new JPanel();
    BoxLayout boxLayout1 = new BoxLayout(platformsPanel, BoxLayout.Y_AXIS);
    platformsPanel.setLayout(boxLayout1);

    // Platforms table
    platformTable = new DefaultTableModel(NUM_ROWS, columns_platforms.size()) {
      @Override
      public boolean isCellEditable(int row, int column) {
        if (column == 0) return true;
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        if (columnIndex == 0) return Boolean.class;
        return String.class;
      }
    };

    tablePlatforms = new JTable(platformTable) {
      @Override
      public String getColumnName(int column) {
        return columns_platforms.get(column);
      }
    };
    tablePlatforms.setFillsViewportHeight(true);
    tablePlatforms.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    tablePlatforms.setRowHeight(Toolkit.zoom(tablePlatforms.getRowHeight()));
    tablePlatforms.setPreferredScrollableViewportSize(dim);

    platformsPanel.add(new JScrollPane(tablePlatforms));

    JPanel controlPanelPlat = new JPanel();
    GridBagLayout gridBagLayout1 = new GridBagLayout();
    controlPanelPlat.setLayout(gridBagLayout1);

    statusPlatform = new JLabel();
    statusPlatform.setText("Starting up...");
    gbc.fill = GridBagConstraints.NONE;
    gbc.gridx = 0;
    gbc.gridy = 0;
    controlPanelPlat.add(statusPlatform, gbc);

    // Using an indeterminate progress bar from now until we learn
    // how to update the fraction of the query/download process:
    // https://github.com/processing/processing-android/issues/362
    progressBarPlatform = new JProgressBar();
    progressBarPlatform.setIndeterminate(true);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanelPlat.add(progressBarPlatform, gbc);

    actionButtonPlatform = new JButton("Install");
    actionButtonPlatform.setEnabled(false);
    actionButtonPlatform.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
    actionButtonPlatform.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (downloadTaskRunning) { // i.e button state is Cancel
          cancelTasks();
        } else { // i.e button state is Update
          downloadTask = new DownloadTask(progressBarPlatform,true);
          progressBarPlatform.setIndeterminate(true);
          downloadTask.execute();
          actionButtonPlatform.setText("Cancel");
          tabs.setEnabled(false);
        }
      }
    });
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanelPlat.add(actionButtonPlatform, gbc);

    JButton closeButtonPlatform = new JButton("Close");
    closeButtonPlatform.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_HEIGHT));
    closeButtonPlatform.addActionListener(disposer);
    closeButtonPlatform.setEnabled(true);
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanelPlat.add(closeButtonPlatform, gbc);

    verticalBox1.add(platformsPanel);
    verticalBox1.add(Box.createVerticalStrut(GAP));
    verticalBox1.add(controlPanelPlat);

    /* ---------------------- END OF TAB2 ------------------------------------------- */

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
      actionButtonPlatform.setText("Install");
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
