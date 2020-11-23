package processing.mode.android;

import processing.app.*;
import processing.app.ui.Toolkit;
import processing.app.Language;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;


public class EmulatorManager extends JFrame {
  private AndroidSDK sdk;
  private AndroidEditor editor;
  private AndroidMode mode;
  private DefaultTableModel emuTableModel;
  private List<Device> deviceList;
  private Vector<Vector<String>> emuTableData;
  private Timer timer;
  private JRadioButton emuRB;

  static private JButton deleteButton;
  static private Devices devices;
  static private JTable table;
  static private String targetSDK ;
  static private String buildTools;

  final private int NUM_ROWS = 5;
  final private int COL_WIDTH = Toolkit.zoom(75);
  final static private int INSET = Toolkit.zoom(1);
  final private Vector<String> columns =
          new Vector<String>(Arrays.asList("AVD Name","Device Name"));

  private JPanel mainPanel;

  public EmulatorManager(AndroidSDK sdk, AndroidEditor editor, Mode mode) {
    super("Emulator Manager");
    this.sdk = sdk;
    this.editor = editor;
    this.mode = (AndroidMode) mode;
    createBaseLayout();
  }

  class GetEmulatorsTask extends SwingWorker<Object,Object> {

    @Override
    protected Object doInBackground() throws Exception {
      try {
        AVD.list(sdk);
      } catch (IOException e) {
        e.printStackTrace();
      }
      emuTableData = new Vector<Vector<String>>();
      for(int i=0; i<AVD.avdList.size(); i++) {
        String avdName = AVD.avdList.get(i);
        String deviceName= AVD.avdTypeList.get(i);
        Vector<String> row = new Vector<String>();
        row.add(avdName);
        row.add(deviceName);
        emuTableData.add(row);
      }
      return emuTableData;
    }

    @Override
    protected void done() {
      super.done();
      remove(mainPanel);
      createLayout();
      emuTableModel.setDataVector(emuTableData,columns);
      emuTableModel.fireTableDataChanged();
    }
  }

  class GetDevicesTask extends TimerTask {

    private Device selectFirstDevice(java.util.List<Device> deviceList) {
      if (0 < deviceList.size()) return deviceList.get(0);
      return null;
    }

    @Override
    public void run() {
      AndroidMode androidMode = (AndroidMode) mode;
      if (androidMode == null || androidMode.getSDK() == null) return;

      devices = Devices.getInstance();

      if (editor.getAppComponent() == AndroidBuild.WATCHFACE) {
        devices.enableBluetoothDebugging();
      }

      deviceList = devices.findMultiple(false);
      Device selectedDevice = devices.getSelectedDevice();

      if (deviceList.size() == 0) {
        Messages.showWarning(Language.text("android_mode.dialog.no_devices_found_title"),
                             Language.text("android_mode.dialog.no_devices_found_body"));
        emuRB.setSelected(true);
        devices.setSelectedDevice(null);
        deleteButton.setEnabled(true);
        timer.cancel();
      } else {
        if (selectedDevice == null) {
          selectedDevice = selectFirstDevice(deviceList);
          devices.setSelectedDevice(selectedDevice);
        } else {
          // check if selected device is still connected
          boolean found = false;
          for (Device device : deviceList) {
            if (device.equals(selectedDevice)) {
              found = true;
              break;
            }
          }

          if (!found) {
            selectedDevice = selectFirstDevice(deviceList);
            devices.setSelectedDevice(selectedDevice);
          }

          final Vector<Vector<String>> devicesVector = new Vector<>();

          DefaultTableModel devicesModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
              return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
              return String.class;
            }

          };
          for (final Device device : deviceList) {
            final Vector<String> deviceItem = new Vector<>();

            System.out.println(device.getName());
            deviceItem.add(device.getName());
            deviceItem.add("Device");

            devicesVector.add(deviceItem);

            int pos = deviceList.indexOf(device);
            //if (device.equals(selectedDevice)) table.addRowSelectionInterval(pos,pos);
          }
          table.setModel(devicesModel);
          devicesModel.setDataVector(devicesVector,columns);
          devicesModel.fireTableDataChanged();
        }
      }
    }
  }

  private void setConfiguration() {
    String api_level = targetSDK.substring(targetSDK.indexOf("-")+1);
    AndroidBuild.TARGET_SDK = api_level;
    AndroidBuild.SUPPORT_VER = buildTools;
    Preferences.set("android.sdk.target",api_level);
    Preferences.set("android.sdk.support",buildTools);
  }

  private void createBaseLayout() {
    setLayout(new BorderLayout());

    mainPanel = new JPanel(new GridBagLayout());
    mainPanel.setPreferredSize(Toolkit.zoom(300,225));

    JLabel loading = new JLabel("Loading . . .");
    mainPanel.add(loading);

    add(mainPanel,BorderLayout.EAST);

    GetEmulatorsTask getEmulatorsTask = new GetEmulatorsTask();
    getEmulatorsTask.execute();

    JPanel sidePanel = new JPanel(new BorderLayout());
    sidePanel.setPreferredSize(Toolkit.zoom(100,0));
    sidePanel.setBackground(Color.white);
    add(sidePanel,BorderLayout.WEST);
    sidePanel.setAlignmentY(CENTER_ALIGNMENT);
    JLabel logo = new JLabel(Toolkit.getLibIcon("/icons/pde-64.png"));
    sidePanel.add(logo,BorderLayout.CENTER);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        if(timer != null) timer.cancel();
      }
    });

    pack();
    setLocationRelativeTo(editor);
    setResizable(false);
    setVisible(true);
  }

  private void createLayout() {
    final JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());
    mainPanel.setBorder(new EmptyBorder(10,10,0,10));
    add(mainPanel,BorderLayout.EAST);

    JPanel sdkPanel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.insets = new Insets(INSET, INSET, INSET, INSET);
    gc.weightx = 1; gc.weighty = 1;

    gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.5;
    JLabel platformLabel = new JLabel("Select your platform: ");
    sdkPanel.add(platformLabel,gc);

    gc.gridx = 1; gc.gridy = 0; gc.weightx = 1;
    Vector<String> availPlatforms = sdk.getAvailPlatforms();
    final JComboBox platformSelector = new JComboBox(availPlatforms);
    sdkPanel.add(platformSelector,gc);

    gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.5;
    JLabel buildToolLabel = new JLabel("Select your buildTool: ");
    sdkPanel.add(buildToolLabel,gc);

    gc.gridx = 1; gc.gridy = 1; gc.weightx = 1;
    final JLabel bToolValue = new JLabel("26.0.2");
    sdkPanel.add(bToolValue,gc);

    gc.gridx = 0; gc.gridy = 2; gc.weightx = 0.5;
    JLabel typeLabel = new JLabel("Emulator / Device : ");
    sdkPanel.add(typeLabel,gc);

    gc.gridx = 1; gc.gridy = 2; gc.weightx = 1;
    emuRB = new JRadioButton("Emulator");
    sdkPanel.add(emuRB,gc);

    gc.gridx = 2; gc.gridy = 2; gc.weightx = 1;
    final JRadioButton devRB = new JRadioButton("Device");
    sdkPanel.add(devRB,gc);

    ButtonGroup typeGroup = new ButtonGroup();
    typeGroup.add(devRB); typeGroup.add(emuRB);

    emuRB.setSelected(true);

    mainPanel.add(sdkPanel, BorderLayout.NORTH);

    emuTableModel = new DefaultTableModel(NUM_ROWS,columns.size()) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return String.class;
      }

    };
    table = new JTable(emuTableModel){
      @Override
      public String getColumnName(int column) {
        return "Emulator/Image Name";
      }
    };
    table.setFillsViewportHeight(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    table.setRowHeight(Toolkit.zoom(table.getRowHeight()));
    Dimension dim = new Dimension(table.getColumnCount() * COL_WIDTH,
            table.getRowHeight() * NUM_ROWS);
    table.setPreferredScrollableViewportSize(dim);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    mainPanel.add(new JScrollPane(table), BorderLayout.CENTER);

    JPanel buttonPanel = new JPanel(new FlowLayout());

    JButton createButton = new JButton("Create");
    createButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CreateAVD createAVD = new CreateAVD(sdk,editor,mode);
        if (createAVD.isCancelled()) {
          Messages.showMessage(Language.text("android_avd.error.cannot_create_avd_title"),
                  Language.text("android_avd.error.create_avd_cancel"));
        } else if (createAVD.getNewAvd() != null) {
          remove(mainPanel);
          createBaseLayout();
        }
      }
    });
    buttonPanel.add(createButton);

    deleteButton = new JButton("Delete");
    deleteButton.setEnabled(false);
    deleteButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        System.out.println("Delete operation began");
        boolean res = AVD.deleteAVD(sdk,(String) table.getValueAt(table.getSelectedRow(),0));
        if (res) {
          remove(mainPanel);
          createBaseLayout();
        } else {
          Messages.showMessage(Language.text("android_avd.error.delete_failed_title"),
                  Language.text("android_avd.error.delete_failed_body"));
        }
      }
    });
    buttonPanel.add(deleteButton);

    final JButton runButton = new JButton("Run");
    runButton.setEnabled(false);
    runButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (table.getSelectedRow() == -1)
          Messages.showMessage("Could not run","Select a Device or an AVD");
        else {
          setVisible(false);
          dispose();
          if (devRB.isSelected()) {
            int deviceIndex = table.getSelectedRow();
            devices.setSelectedDevice(deviceList.get(deviceIndex));
            editor.handleRunDevice();
          } else {
            targetSDK = (String) platformSelector.getSelectedItem();
            buildTools = (String) bToolValue.getText();
            //save these changes to use for further run
            setConfiguration();
            editor.handleRunEmulator((String) table.getValueAt(table.getSelectedRow(), 0));
          }
        }
      }
    });
    buttonPanel.add(runButton);

    mainPanel.add(buttonPanel,BorderLayout.SOUTH);

    //Listener for radio buttons
    devRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        deleteButton.setEnabled(false); //delete is possible only if its emulator
        GetDevicesTask getDevicesTask = new GetDevicesTask();
        timer = new Timer();
        timer.schedule(getDevicesTask, 400, 10000);
      }
    });

    emuRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        timer.cancel();
        table.setModel(emuTableModel);
      }
    });

    //Listener for delete button enable
    table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if(!devRB.isSelected()) deleteButton.setEnabled(true);
        runButton.setEnabled(true);
      }
    });

    pack();
    setLocationRelativeTo(editor);
    setAlwaysOnTop(false);
    setVisible(true);
  }
}
