package processing.mode.android;

import processing.app.Platform;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class SDKUpdater extends JFrame implements PropertyChangeListener {

    private final Vector<String> columnsInstalled = new Vector<>(Arrays.asList("Path", "Version",
            "Description", "Location"));
    private final Class[] columnClassI = new Class[]{
            String.class, String.class, String.class, String.class
    };

    private final Vector<String> columnsUpdates = new Vector<>(Arrays.asList("ID", "Installed", "Available"));
    private final Class[] columnClassU = new Class[]{
            String.class, String.class, String.class
    };

    private static final String PROPERTY_CHANGE_QUERY = "query";

    private AndroidSDK sdk;
    private File toolsFolder;
    private Vector<Vector<String>> updatesList;
    private Vector<Vector<String>> installedList;
    private QueryTask queryTask;
    private DownloadTask downloadTask;
    private boolean downloadTaskRunning;
    private Process backgroundProcess;

    private DefaultTableModel modelInstalled;
    private DefaultTableModel modelUpdates;
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

        if (sdk == null)
            return;

        toolsFolder = sdk.getToolsFolder();
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
                if (updatesList.size() == 0) {
                    status.setText("No updates available");
                    status.setForeground(Color.GREEN);
                } else {
                    actionButton.setVisible(true);
                    status.setText("Update(s) found!");
                    status.setForeground(Color.BLUE);
                }
                break;
        }
    }

    class QueryTask extends SwingWorker {
        @Override
        protected Object doInBackground() throws Exception {
            ArrayList<String> cmd = new ArrayList<>();
            String path = toolsFolder + File.separator + "bin" + File.separator;
            if (Platform.isWindows())
                path += "sdkmanager.bat";
            else
                path += "sdkmanager";
            cmd.add(path);
            cmd.add("--list");

            ProcessBuilder process = new ProcessBuilder(cmd);
            backgroundProcess = process.start();

            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(backgroundProcess.getInputStream()));
            backgroundProcess.waitFor();

            updatesList = new Vector<>();
            installedList = new Vector<>();
            String line;
            boolean skip = false, updates = false;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    skip = !skip;

                if (!skip && !line.startsWith("d")) { //Skip all available packages
                    if (line.startsWith("  I"))
                        updates = true;
                    else if (!line.startsWith("  P") && !line.startsWith("  -") && !line.startsWith("I") &&
                            !line.startsWith("A") && !line.isEmpty()) {
                        String[] result = line.split("\\|");

                        if (updates)
                            updatesList.add(new Vector<>(Arrays.asList(result)));
                        else
                            installedList.add(new Vector<>(Arrays.asList(result)));
                    }
                }
            }

            return null;
        }

        @Override
        protected void done() {
            super.done();

            try {
                get();
                firePropertyChange(PROPERTY_CHANGE_QUERY, "query", "SUCCESS");

                if (updatesList != null && installedList != null) {
                    modelInstalled.setDataVector(installedList, columnsInstalled);
                    modelUpdates.setDataVector(updatesList, columnsUpdates);

                    modelUpdates.fireTableDataChanged();
                    modelInstalled.fireTableDataChanged();
                }
            } catch (InterruptedException | CancellationException e) {
                backgroundProcess.destroy();
            } catch (ExecutionException e) {
                JOptionPane.showMessageDialog(null,
                        e.getCause().toString(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class DownloadTask extends SwingWorker {
        @Override
        protected Object doInBackground() throws Exception {
            downloadTaskRunning = true;
            ArrayList<String> cmd = new ArrayList<>();
            String path = toolsFolder + File.separator + "bin" + File.separator;
            if (Platform.isWindows())
                path += "sdkmanager.bat";
            else
                path += "sdkmanager";
            cmd.add(path);
            cmd.add("--update");

            ProcessBuilder process = new ProcessBuilder(cmd);
            backgroundProcess = process.start();
            backgroundProcess.waitFor();

            return null;
        }

        @Override
        protected void done() {
            super.done();

            try {
                get();
                actionButton.setVisible(false); //Hide button after update completes
                status.setText("Refreshing packages...");

                queryTask = new QueryTask();
                queryTask.addPropertyChangeListener(SDKUpdater.this);
                queryTask.execute();
            } catch (InterruptedException | CancellationException e) {
                backgroundProcess.destroy();
            } catch (ExecutionException e) {
                JOptionPane.showMessageDialog(null,
                        e.getCause().toString(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                downloadTaskRunning = false;
                progressBar.setIndeterminate(false);
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
        packagesPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Packages"));

        BoxLayout boxLayout = new BoxLayout(packagesPanel, BoxLayout.Y_AXIS);
        packagesPanel.setLayout(boxLayout);

        /* Installed Packages panel */
        JPanel installedPanel = new JPanel(new BorderLayout());
        installedPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Installed"));

        //Installed Packages table
        modelInstalled = new DefaultTableModel(15, columnClassI.length) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnClassI[columnIndex];
            }
        };
        JTable tInstalled = new JTable(modelInstalled) {
            @Override
            public String getColumnName(int column) {
                return columnsInstalled.get(column);
            }
        };
        tInstalled.setFillsViewportHeight(true);

        tInstalled.setPreferredScrollableViewportSize(new Dimension(tInstalled.getPreferredSize().width,
                15 * tInstalled.getRowHeight()));
        installedPanel.add(new JScrollPane(tInstalled));


        /* Updates panel */
        JPanel updatesPanel = new JPanel(new BorderLayout());
        updatesPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Available Updates"));

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

        packagesPanel.add(installedPanel);
        packagesPanel.add(updatesPanel);

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
        actionButton.setVisible(false);
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
