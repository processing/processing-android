package processing.mode.android;

import com.android.repository.api.*;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.io.FileOpUtils;
import com.android.repository.io.impl.FileSystemFileOp;
import com.android.repository.util.InstallerUtil;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallerUtil;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import com.android.sdklib.tool.SdkManagerCli;
import processing.app.ui.Editor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("serial")
public class SDKUpdater extends JFrame implements PropertyChangeListener {
  private final Vector<String> columns = new Vector<>(Arrays.asList(
      "Package name", "Installed version", "New version"));
  //    private final Class[] columnClassI = new Class[]{
  //            String.class, String.class, String.class
  //    };

  //    private final Vector<String> columnsUpdates = new Vector<>(Arrays.asList("ID", "Installed", "Available"));
  //    private final Class[] columnClassU = new Class[]{
  //            String.class, String.class, String.class
  //    };

  private static final String PROPERTY_CHANGE_QUERY = "query";

  private AndroidSDK sdk;
  private Vector<Vector<String>> installedList;
  //    private Vector<Vector<String>> updatesList;

  private QueryTask queryTask;
  private DownloadTask downloadTask;
  private boolean downloadTaskRunning;

  //    private Vector<Vector<String>> packageInfo;
  //    private DefaultTableModel packageTable;

  private DefaultTableModel modelInstalled;
  private int numUpdates;
  //    private DefaultTableModel modelUpdates;

  private JProgressBar progressBar;
  private JLabel status;
  private JButton actionButton;

  public SDKUpdater(Editor editor, AndroidMode androidMode) {
    super("SDK Updater");

    androidMode.checkSDK(editor);
    try {
      sdk = AndroidSDK.load();
      if (sdk == null) {
        sdk = AndroidSDK.locate(editor, androidMode);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (AndroidSDK.CancelException e) {
      e.printStackTrace();
    } catch (AndroidSDK.BadSDKException e) {
      e.printStackTrace();
    }

    if (sdk == null) return;

    queryTask = new QueryTask();
    queryTask.addPropertyChangeListener(this);
    queryTask.execute();
    createLayout();
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
    @Override
    protected Object doInBackground() throws Exception {
      numUpdates = 0;
      installedList = new Vector<>();

      /* Following code is from listPackages() of com.android.sdklib.tool.SdkManagerCli
               with some changes
       */
      AndroidSdkHandler mHandler = AndroidSdkHandler.getInstance(AndroidSDK.load().getSdkFolder());

      ProgressIndicator progress = new ConsoleProgressIndicator();

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
      HashMap<String, String[]> local = new HashMap();
      for (LocalPackage pck : packages.getLocalPackages().values()) {
        String path = pck.getPath();
        String name = pck.getDisplayName();
        String ver = pck.getVersion().toString();
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
        local.put(path, new String[]{name, ver});
      }

      HashMap<String, String[]> update = new HashMap();
      for (UpdatablePackage pck : packages.getUpdatedPkgs()) {              
        String path = pck.getPath();
        String inst = pck.getLocal().getVersion().toString();
        String updt = pck.getRemote().getVersion().toString();              
        update.put(path, new String[]{inst, updt});
      }

      for (String path: local.keySet()) {
        Vector<String> info = new Vector<>();
        String[] locInfo = local.get(path);
        info.add(locInfo[0]);
        info.add(locInfo[1]);
        if (update.containsKey(path)) {
          String[] updInfo = update.get(path);
          info.add(updInfo[1]);  
          numUpdates++;
        } else {
          info.add("");
        }
        installedList.add(info);  
      }


      /*
            for (LocalPackage local : packages.getLocalPackages().values()) {
                Vector<String> localPackages = new Vector<>();
                String name = local.getDisplayName();
                String ver = local.getVersion().toString();
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

                localPackages.add(local.getPath());
                localPackages.add(local.getVersion().toString());
                localPackages.add(name);

                System.out.println("*************************");
                System.out.println(local.getDisplayName());               
                System.out.println(name);


                installedList.add(localPackages);
            }

            for (UpdatablePackage update : packages.getUpdatedPkgs()) {
                Vector<String> updatePackages = new Vector<>();
                updatePackages.add(update.getPath());
                updatePackages.add(update.getLocal().getVersion().toString());
                updatePackages.add(update.getRemote().getVersion().toString());

                updatesList.add(updatePackages);
            }
       */


      return null;
    }

    @Override
    protected void done() {
      super.done();

      try {
        get();
        firePropertyChange(PROPERTY_CHANGE_QUERY, "query", "SUCCESS");

        if (installedList != null) {
          modelInstalled.setDataVector(installedList, columns);
          modelInstalled.fireTableDataChanged();

          //                    modelUpdates.setDataVector(updatesList, columnsUpdates);
          //                    modelUpdates.fireTableDataChanged();

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
    @Override
    protected Object doInBackground() throws Exception {
      downloadTaskRunning = true;

      /* Following code is from installPackages() of com.android.sdklib.tool.SdkManagerCli
               with some changes
       */
      AndroidSdkHandler mHandler = AndroidSdkHandler.getInstance(AndroidSDK.load().getSdkFolder());

      FileSystemFileOp fop = (FileSystemFileOp) FileOpUtils.create();
      CustomSettings settings = new CustomSettings();
      LegacyDownloader downloader = new LegacyDownloader(fop, settings);
      ProgressIndicator progress = new ConsoleProgressIndicator();

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
        //                actionButton.setVisible(false); //Hide button after update completes
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

  private void createLayout() {
    Container outer = getContentPane();
    outer.removeAll();

    Box verticalBox = Box.createVerticalBox();
    verticalBox.setBorder(new EmptyBorder(13, 13, 13, 13));
    outer.add(verticalBox);

    /* Packages panel */
    JPanel packagesPanel = new JPanel();
    //        packagesPanel.setBorder(BorderFactory.createTitledBorder(
    //                BorderFactory.createEtchedBorder(), "Packages"));

    BoxLayout boxLayout = new BoxLayout(packagesPanel, BoxLayout.Y_AXIS);
    packagesPanel.setLayout(boxLayout);

    /* Installed Packages panel */
    //        JPanel installedPanel = new JPanel(new BorderLayout());
    //        installedPanel.setBorder(BorderFactory.createTitledBorder(
    //                BorderFactory.createEtchedBorder(), "Installed"));

    //Installed Packages table
    modelInstalled = new DefaultTableModel(15, columns.size()) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return String.class;
      }
    };
    JTable tInstalled = new JTable(modelInstalled) {
      @Override
      public String getColumnName(int column) {
        return columns.get(column);
      }
    };
    tInstalled.setFillsViewportHeight(true);

    tInstalled.setPreferredScrollableViewportSize(new Dimension(tInstalled.getPreferredSize().width,
        15 * tInstalled.getRowHeight()));
    packagesPanel.add(new JScrollPane(tInstalled));


    /* Updates panel */
    //        JPanel updatesPanel = new JPanel(new BorderLayout());
    //        updatesPanel.setBorder(BorderFactory.createTitledBorder(
    //                BorderFactory.createEtchedBorder(), "Available Updates"));

    /*
        //Updates table
        modelUpdates = new DefaultTableModel(5, columnClassU.length) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnClassU[columnIndex];
            }
        };
        JTable tUpdates = new JTable(modelUpdates) {
            @Override
            public String getColumnName(int column) {
                return columnsUpdates.get(column);
            }
        };
        tUpdates.setFillsViewportHeight(true);
        tUpdates.setPreferredScrollableViewportSize(new Dimension(tUpdates.getPreferredSize().width,
                5 * tUpdates.getRowHeight()));
        updatesPanel.add(new JScrollPane(tUpdates), BorderLayout.CENTER);
     */



    //        packagesPanel.add(installedPanel);
    //        packagesPanel.add(updatesPanel);

    JPanel controlPanel = new JPanel();
    GridBagLayout gridBagLayout = new GridBagLayout();
    controlPanel.setLayout(gridBagLayout);

    GridBagConstraints gbc = new GridBagConstraints();

    status = new JLabel();
    status.setText("Querying packages...");
    gbc.gridx = 0;
    gbc.gridy = 0;
    controlPanel.add(status, gbc);

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
          downloadTask.cancel(true);
          status.setText("Download cancelled");
          actionButton.setText("Update");
        } else { // i.e button state is Update
          downloadTask = new DownloadTask();
          progressBar.setIndeterminate(true);
          downloadTask.execute();
          status.setText("Downloading available updates...");
          actionButton.setText("Cancel");
        }
      }
    });
    //        actionButton.setVisible(false);
    actionButton.setEnabled(false);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanel.add(actionButton, gbc);

    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        cancelTasks();
        dispose();
      }
    };

    JButton closeButton = new JButton("Close");
    closeButton.addActionListener(disposer);
    closeButton.setEnabled(true);
    gbc.gridx = 1;
    gbc.gridy = 1;
    gbc.weightx = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    controlPanel.add(closeButton, gbc);

    verticalBox.add(packagesPanel);
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

    setLocationRelativeTo(null);
    setResizable(true);
    setVisible(true);
  }

  public void cancelTasks() {
    queryTask.cancel(true);
    if (downloadTaskRunning) {
      JOptionPane.showMessageDialog(null,
          "Download cancelled", "Warning", JOptionPane.WARNING_MESSAGE);
      downloadTask.cancel(true);
    }
  }
}
