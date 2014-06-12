package processing.mode.android;

import processing.app.Base;
import processing.app.Preferences;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DeviceSelector extends JFrame {

  JList list;

  public DeviceSelector() {
    super("Android device selector");

    createLayout();
  }

  private void refreshList() {
    Devices devices = Devices.getInstance();
    java.util.List<Device> deviceList = devices.findMultiple(false);

    String[] data = new String[deviceList.size()];
    for(int i = 0; i < deviceList.size(); i++) {
      data[i] = deviceList.get(0).toString();
    }

    list.setListData(data);
  }

  private void createLayout() {
    Container outer = getContentPane();
    outer.removeAll();

    Box pain = Box.createVerticalBox();
    pain.setBorder(new EmptyBorder(13, 13, 13, 13));
    outer.add(pain);

    list = new JList();
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    list.setLayoutOrientation(JList.VERTICAL);
    list.setVisibleRowCount(-1);

    refreshList();

    JScrollPane listScroller = new JScrollPane(list);
    listScroller.setPreferredSize(new Dimension(250, 80));
    pain.add(listScroller);

    // buttons
    JPanel buttons = new JPanel();
//    buttons.setPreferredSize(new Dimension(400, 35));
//    JPanel buttons = new JPanel() {
//      public Dimension getPreferredSize() {
//        return new Dimension(400, 35);
//      }
//      public Dimension getMinimumSize() {
//        return new Dimension(400, 35);
//      }
//      public Dimension getMaximumSize() {
//        return new Dimension(400, 35);
//      }
//    };

//    Box buttons = Box.createHorizontalBox();
    buttons.setAlignmentX(LEFT_ALIGNMENT);
    JButton okButton = new JButton("OK");
    Dimension dim = new Dimension(Preferences.BUTTON_WIDTH,
        okButton.getPreferredSize().height);
    okButton.setPreferredSize(dim);
    okButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
      }
    });
    okButton.setEnabled(true);

    JButton refreshButton = new JButton("Refresh");
    refreshButton.setPreferredSize(dim);
    refreshButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        refreshList();
      }
    });
    refreshButton.setEnabled(true);

    JButton cancelButton = new JButton("Cancel");
    cancelButton.setPreferredSize(dim);
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
      }
    });
    cancelButton.setEnabled(true);

    // think different, biznatchios!
    if (Base.isMacOS()) {
      buttons.add(cancelButton);

      buttons.add(refreshButton);
//      buttons.add(Box.createHorizontalStrut(8));
      buttons.add(okButton);
    } else {
      buttons.add(okButton);

      buttons.add(refreshButton);
//      buttons.add(Box.createHorizontalStrut(8));
      buttons.add(cancelButton);
    }
//    buttons.setMaximumSize(new Dimension(300, buttons.getPreferredSize().height));
    pain.add(buttons);

    JRootPane root = getRootPane();
    root.setDefaultButton(okButton);
    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        setVisible(false);
      }
    };
    processing.app.Toolkit.registerWindowCloseKeys(root, disposer);
    processing.app.Toolkit.setIcon(this);

    pack();

    Dimension screen = processing.app.Toolkit.getScreenSize();
    Dimension windowSize = getSize();

    setLocation((screen.width - windowSize.width) / 2,
        (screen.height - windowSize.height) / 2);

    setVisible(true);
  }
}
