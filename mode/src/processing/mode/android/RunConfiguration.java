package processing.mode.android;

import processing.app.*;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.Vector;


public class RunConfiguration extends JFrame {
  private AndroidSDK sdk;
  private AndroidEditor editor;
  private AndroidMode mode;

  static private String targetSDK ;
  static private String buildTools;

  final private int NUM_ROWS = 5;
  final private int COL_WIDTH = Toolkit.zoom(125);
  final static private int INSET = Toolkit.zoom(1);
  final private Vector<String> columns =
          new Vector<String>(Arrays.asList("AVD Name","Device Name"));

  private JPanel mainPanel;

  public RunConfiguration(AndroidSDK sdk, AndroidEditor editor, Mode mode) {
    super("Run Configurations");
    this.sdk = sdk;
    this.editor = editor;
    this.mode = (AndroidMode) mode;
    createBaseLayout();
    createLayout();
  }

  private void setConfiguration() {
    AndroidBuild.TARGET_SDK = targetSDK.substring(targetSDK.length()-2);
    AndroidBuild.SUPPORT_VER = buildTools;
    Preferences.set("android.sdk.target",targetSDK.substring(targetSDK.length()-2));
    Preferences.set("android.sdk.support",buildTools);
  }

  private void createBaseLayout() {
    getContentPane().removeAll();

    setLayout(new BorderLayout());
    mainPanel = new JPanel();
    mainPanel.setPreferredSize(Toolkit.zoom(300,225));
    add(mainPanel,BorderLayout.EAST);

    JPanel sidePanel = new JPanel(new BorderLayout());
    sidePanel.setPreferredSize(Toolkit.zoom(100,0));
    sidePanel.setBackground(Color.white);
    add(sidePanel,BorderLayout.WEST);
    sidePanel.setAlignmentY(CENTER_ALIGNMENT);
    JLabel logo = new JLabel(Toolkit.getLibIcon("/icons/pde-64.png"));
    sidePanel.add(logo,BorderLayout.CENTER);
  }

  private void createLayout() {

    JPanel sdkPanel = new JPanel(new GridBagLayout());
    sdkPanel.setPreferredSize(new Dimension(300,75));

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

    mainPanel.add(sdkPanel);

    try {
      AVD.list(sdk);
    } catch (IOException e) {
      e.printStackTrace();
    }
    DefaultTableModel emuTable = new DefaultTableModel(NUM_ROWS,columns.size()) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }

      @Override
      public Class<?> getColumnClass(int columnIndex) {
        return String.class;
      }


    };
    final JTable table = new JTable(emuTable){
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

    Vector<Vector<String>> emuTableData = new Vector<Vector<String>>();
    for(int i=0; i<AVD.avdList.size(); i++) {
      String avdName = AVD.avdList.get(i);
      String deviceName= AVD.avdTypeList.get(i);
      Vector<String> row = new Vector<String>();
      row.add(avdName);
      row.add(deviceName);
      emuTableData.add(row);
    }
    emuTable.setDataVector(emuTableData,columns);

    mainPanel.add(new JScrollPane(table));

    JPanel buttonPanel = new JPanel(new FlowLayout());

    JButton createButton = new JButton("Create");
    createButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        CreateAVD createAVD = new CreateAVD(sdk,editor,mode);
        if (createAVD.isCancelled()) {
          Messages.showMessage(AndroidMode.getTextString("android_avd.error.cannot_create_avd_title"),
                  AndroidMode.getTextString("android_avd.error.create_avd_cancel"));
        }
      }
    });
    buttonPanel.add(createButton);

    JButton deleteButton = new JButton("Delete");
    deleteButton.setEnabled(false);
    deleteButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        boolean res = AVD.deleteAVD(sdk,(String) table.getValueAt(table.getSelectedRow(),0));
        if (res) {
          System.out.println("deleted"); //replace with reload Swing worker
        } else {
          Messages.showMessage(AndroidMode.getTextString("android_avd.error.delete_failed_title"),
                  AndroidMode.getTextString("android_avd.error.delete_failed_body"));
        }
      }
    });
    buttonPanel.add(deleteButton);

    JButton runButton = new JButton("Run");
    runButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
        dispose();
        targetSDK = (String) platformSelector.getSelectedItem();
        buildTools = (String) bToolValue.getText();
        //save these changes to use for further run
        setConfiguration();
        editor.handleRunEmulator((String) table.getValueAt(table.getSelectedRow(),0));
      }
    });
    buttonPanel.add(runButton);

    mainPanel.add(buttonPanel,BorderLayout.SOUTH);


    pack();
    setLocationRelativeTo(editor);
    setAlwaysOnTop(false);
    setVisible(true);
  }
}
