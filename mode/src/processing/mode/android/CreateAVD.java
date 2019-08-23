package processing.mode.android;

import processing.app.Messages;
import processing.app.ui.Editor;
import processing.app.ui.Toolkit;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

public class CreateAVD extends JDialog {
  private AndroidSDK sdk;
  private Frame editor;
  private AndroidMode mode;

  private boolean cancelled = false;
  private boolean failed = false;
  final private int NUM_ROWS = 5;
  final static private int INSET = Toolkit.zoom(1);
  final static private int BAR_WIDTH = Toolkit.zoom(300);
  final static private int BAR_HEIGHT = Toolkit.zoom(30);
  final private Vector<String> columns_image =
          new Vector<String>(Arrays.asList("API Level", "Tag", "ABI", "Status"));

  private static Dimension currWindowSize;
  private static String deviceName;
  private static String avdName;
  private static String imageName;
  private static String imageAPI;
  private static boolean wear = false;

  private Vector<Vector<String>> devices;
  private DefaultTableModel imageTable;

  private AVD newAvd;
  private JPanel mainPanel;
  private JProgressBar createProgress;

  public CreateAVD(AndroidSDK sdk, Frame editor, AndroidMode mode) {
    super(editor,"Create AVD",true);
    this.sdk = sdk;
    this.editor = editor;
    this.mode = mode;
    createBaseLayout();
    showLoadingScreen(0);
  }

  class ListDevicesTask extends SwingWorker<Object,Object> {
    @Override
    protected Object doInBackground() throws Exception {
      devices = AVD.listDevices(sdk);
      return null;
    }

    @Override
    protected void done() {
      super.done();
      remove(mainPanel);
      revalidate();
      repaint();
      showDeviceSelector();
    }
  }

  class ListImagesTask extends SwingWorker<Object,Object> {
    private boolean imgExists;
    private Vector<String> image;
    @Override
    protected Object doInBackground() throws Exception {
      Vector<Vector<String>> images = AVD.listImages(sdk,wear);
      for(Vector<String> image:images){
        if (image.get(3).equals("Installed")) {
          imgExists = true;
          this.image = image;
          break;
        } else {
          imgExists = false;
        }
      }
      return images;
    }

    @Override
    protected void done() {
      super.done();
      remove(mainPanel);
      revalidate();
      repaint();
      if(imgExists){
        String API = image.get(0);
        String TAG = image.get(1);
        String ABI = image.get(2);
        imageName = "system-images;"+ API+ ";"+ TAG+ ";"+ ABI;
        imageAPI = API;
        showConfirmWindow();
      } else {
        Messages.showMessage(AndroidMode.getTextString("android_avd.error.image_not_available_title")
        , AndroidMode.getTextString("android_avd.error.image_not_available_body"));
        String platform = sdk.getAvailPlatforms().get(0);
        String API = platform;
        String ABI = AVD.getSupportedABI();
        String TAG = "google_apis";
        if(wear){
          TAG = "android-wear";
        }
        try {
          boolean result = false;
          if(!wear)
            result = AndroidSDK.downloadSysImage(editor, mode, false, ABI, API,TAG);
          else
            result = AndroidSDK.downloadSysImage(editor, mode, true, ABI, API,TAG);
          if (result) {
            showLoadingScreen(1);
          }
        } catch (AndroidSDK.BadSDKException | AndroidSDK.CancelException ex) {
          ex.printStackTrace();
        }
      }
    }
  }

  class CreateAvdTask extends SwingWorker<Object, Object> {
    @Override
    protected Object doInBackground() throws Exception {
      boolean result = false;
      try {
        result = newAvd.create(sdk);
      } catch (IOException ex) {
        ex.printStackTrace();
      }
      return result;
    }

    @Override
    protected void done() {
      super.done();
      boolean result = false;
      try {
        result = (boolean) get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      createProgress.setIndeterminate(false);
      setVisible(false);
      dispose();
      if (result) {
       System.out.println(AndroidMode.getTextString("android_avd.status.create_avd_completed"));
      }
    }
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public boolean isFailed() {
    return failed;
  }

  private void showLoadingScreen(int option) {
    mainPanel = new JPanel(new GridBagLayout());
    if(currWindowSize!=null) mainPanel.setPreferredSize(currWindowSize);
    else mainPanel.setPreferredSize(new Dimension(300,225));
    add(mainPanel,BorderLayout.EAST);

    JLabel loadingLabel = new JLabel("Loading . . . ");
    loadingLabel.setVerticalAlignment(SwingConstants.CENTER);
    loadingLabel.setHorizontalAlignment(SwingConstants.CENTER);
    mainPanel.add(loadingLabel);

    if (option == 0) {
      ListDevicesTask listDevicesTask = new ListDevicesTask();
      listDevicesTask.execute();
    } else if (option == 1) {
      ListImagesTask listImagesTask = new ListImagesTask();
      listImagesTask.execute();
    }

    pack();
    setLocationRelativeTo(editor);
    setResizable(false);
    setVisible(true);
  }

  private void createBaseLayout() {
    getContentPane().removeAll();

    setLayout(new BorderLayout());

    JPanel sidePanel = new JPanel(new BorderLayout());
    sidePanel.setPreferredSize(Toolkit.zoom(100,0));
    sidePanel.setBackground(Color.white);
    add(sidePanel,BorderLayout.WEST);
    sidePanel.setAlignmentY(CENTER_ALIGNMENT);
    JLabel logo = new JLabel(Toolkit.getLibIcon("/icons/pde-64.png"));
    sidePanel.add(logo,BorderLayout.CENTER);

    JRootPane root = getRootPane();
    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        cancelled = true;
        setVisible(false);
        dispose();
      }
    };
    Toolkit.registerWindowCloseKeys(root, disposer);
    Toolkit.setIcon(this);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        super.windowClosing(e);
        cancelled = true;
      }
    });
  }

  private void showDeviceSelector() {
    final JPanel mainPanel = new JPanel();
//    mainPanel.setPreferredSize(Toolkit.zoom(500,225));
    mainPanel.setLayout(new BorderLayout());
    mainPanel.setBorder(new EmptyBorder(0,10,0,0));
    add(mainPanel,BorderLayout.EAST);

    JPanel infoPanel = new JPanel();
    String infoString = AndroidMode.getTextString("android_avd.create.info_message");
    JLabel info = new JLabel(infoString);

    infoPanel.add(info);
    mainPanel.add(infoPanel,BorderLayout.NORTH);

    JPanel selector = new JPanel(new GridBagLayout());
    selector.setAlignmentX(RIGHT_ALIGNMENT);

    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = GridBagConstraints.NORTHWEST;
    gc.insets = new Insets(INSET, INSET, INSET, INSET);
    gc.weighty = 0.5;

    gc.gridx = 0; gc.gridy = 0; gc.weightx = 0.25;
    JLabel typeLabel = new JLabel("Select type: ");
    selector.add(typeLabel,gc);

    gc.gridx = 1; gc.gridy = 0; gc.weightx = 1;
    JRadioButton phoneRB = new JRadioButton("Phone");
    selector.add(phoneRB,gc);

    gc.gridx = 2; gc.gridy = 0; gc.weightx = 0.5;
    final JRadioButton wearRB = new JRadioButton("Wear");
    selector.add(wearRB,gc);

    ButtonGroup typeGroup = new ButtonGroup();
    phoneRB.setSelected(true);
    typeGroup.add(phoneRB); typeGroup.add(wearRB);

    gc.gridx = 0; gc.gridy = 1; gc.weightx = 0.5;
    JLabel deviceLabel = new JLabel("Select Device");
    selector.add(deviceLabel,gc);

    gc.gridx = 1; gc.gridy = 1; gc.weightx = 1;
    final Vector<String> phoneList = devices.get(0);
    final Vector<String> idsList = devices.get(1);
    final Vector<String> wearList = devices.get(2);
    final Vector<String> wearIds = devices.get(3);

    final DefaultComboBoxModel<String> cm = new DefaultComboBoxModel<String>();
    final JComboBox<String> deviceSelector = new JComboBox<String>(cm);

    //add phone elements by default
    cm.removeAllElements();
    for(String phone : phoneList) cm.addElement(phone);
    deviceSelector.setSelectedIndex(0);

    selector.add(deviceSelector,gc);
    mainPanel.add(selector,BorderLayout.CENTER);

    phoneRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cm.removeAllElements();
        for(String phone : phoneList) cm.addElement(phone);
      }
    });

    wearRB.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cm.removeAllElements();
        for(String phone : wearList) cm.addElement(phone);
      }
    });

    JPanel buttons = new JPanel(new FlowLayout());

    JButton cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        cancelled = true;
        setVisible(false);
        dispose();
      }
    });
    buttons.add(cancelButton);

    JButton nextButton = new JButton("Next");
    nextButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        currWindowSize = mainPanel.getSize();
        remove(mainPanel);
        revalidate();
        repaint();
        if (wearRB.isSelected()) wear = true;
        if (!wear) {
          deviceName = idsList.get(deviceSelector.getSelectedIndex());
          //remove the begining and trailing " "
          deviceName = deviceName.replaceAll("^\"|\"$", "");
        }
        else {
          deviceName = wearIds.get(deviceSelector.getSelectedIndex());
          deviceName = deviceName.replaceAll("^\"|\"$", "");
        }
        String baseName = deviceName.replaceAll("\\s+","");
        if (!wear) avdName = baseName + "_" + "phone"; //construct name automatically
        else avdName = baseName + "_" + "wear";
        showLoadingScreen(1);
      }
    });
    buttons.add(nextButton);

    mainPanel.add(buttons,BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(editor);
    setAlwaysOnTop(false);
    setVisible(true);
  }

  private void showConfirmWindow() {
    final AVD avd = new AVD(avdName, deviceName, imageName);
    newAvd = avd;
    final JPanel mainPanel = new JPanel();
    mainPanel.setLayout(new BorderLayout());
    add(mainPanel,BorderLayout.EAST);

    JPanel configPanel = new JPanel(new GridBagLayout());
    Border border = BorderFactory.createLineBorder(Color.black,1,true);
    configPanel.setBorder(BorderFactory.createTitledBorder(border,"Confirm your AVD: "));

    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = GridBagConstraints.NORTH;
    gc.weightx = 1; gc.weighty = 1;

    gc.gridx = 0; gc.gridy = 0;
    JLabel name = new JLabel("AVD Name: ");
    configPanel.add(name,gc);

    gc.gridx = 1; gc.gridy = 0;
    JLabel nameValue = new JLabel(avdName);
    configPanel.add(nameValue,gc);

    gc.gridx = 0; gc.gridy = 1;
    JLabel device = new JLabel("AVD Device: ");
    configPanel.add(device,gc);

    gc.gridx = 1; gc.gridy = 1;
    JLabel deviceValue = new JLabel(deviceName);
    configPanel.add(deviceValue,gc);

    gc.gridx = 0; gc.gridy = 2;
    JLabel image = new JLabel("Image: ");
    configPanel.add(image,gc);

    gc.gridx = 1; gc.gridy = 2;
    JLabel imageValue = new JLabel(imageAPI);
    configPanel.add(imageValue,gc);

    mainPanel.add(configPanel,BorderLayout.NORTH);

    createProgress = new JProgressBar();
    createProgress.setPreferredSize(new Dimension(BAR_WIDTH,BAR_HEIGHT));
    createProgress.setValue(0);
    mainPanel.add(createProgress, BorderLayout.CENTER);

    JPanel buttonsPanel = new JPanel(new FlowLayout());

    JButton backButton = new JButton("Back");
    backButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        currWindowSize = mainPanel.getSize();
        remove(mainPanel);
        revalidate();
        repaint();
        showLoadingScreen(0);
      }
    });
    buttonsPanel.add(backButton);

    JButton confirmButton = new JButton("Confirm");
    confirmButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        System.out.println(AndroidMode.getTextString("android_avd.status.create_avd_started"));
        createProgress.setIndeterminate(true);
        CreateAvdTask createAvdTask = new CreateAvdTask();
        createAvdTask.execute();
      }
    });
    buttonsPanel.add(confirmButton);

    mainPanel.add(buttonsPanel,BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(editor);
    setAlwaysOnTop(false);
    setVisible(true);
  }

  public AVD getNewAvd () {
    return newAvd;
  }
}
