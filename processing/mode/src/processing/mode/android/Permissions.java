/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-21 The Processing Foundation
 Copyright (c) 2010-12 Ben Fry and Casey Reas

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

package processing.mode.android;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;

import processing.app.Language;
import processing.app.Platform;
import processing.app.Sketch;
import processing.app.ui.Toolkit;


@SuppressWarnings("serial")
public class Permissions extends JFrame {
  static final String GUIDE_URL =
    "https://developer.android.com/training/articles/security-tips.html#Permissions";
 
  static final int BORDER_HORIZ = Toolkit.zoom(5);
  static final int BORDER_VERT = Toolkit.zoom(3);
  static final int CELL_HEIGHT = Toolkit.zoom(20);
  static final int BORDER = Toolkit.zoom(13);
  static final int TEXT_WIDTH = Toolkit.zoom(400);
  static final int TEXT_HEIGHT = Toolkit.zoom(80);
  static final int URL_WIDTH = Toolkit.zoom(400);
  static final int URL_HEIGHT = Toolkit.zoom(30);
  static final int DESC_WIDTH = Toolkit.zoom(400);
  static final int DESC_HEIGHT = Toolkit.zoom(50);
  static final int GAP = Toolkit.zoom(8);
  
  JScrollPane permissionScroller;
  JList<JCheckBox> permissionList;
  JLabel descriptionLabel;
  Sketch sketch;
  
  int appComp;
  
  File modeFolder;


  public Permissions(Sketch sketch, int appComp, File modeFolder) {
    super("Android Permissions Selector");
    this.appComp = appComp;
    this.sketch = sketch;
    this.modeFolder = modeFolder;

    permissionList = new CheckBoxList();
    permissionList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == false) {
          int index = permissionList.getSelectedIndex();
          if (index == -1) {
            descriptionLabel.setText("");
          } else {
            descriptionLabel.setText("<html>" + description[index] + "</html>");
          }
        }
      }
    });
    permissionList.setFixedCellHeight(CELL_HEIGHT);
    permissionList.setBorder(new EmptyBorder(BORDER_VERT, BORDER_HORIZ,
                                             BORDER_VERT, BORDER_HORIZ));

    DefaultListModel<JCheckBox> model = new DefaultListModel<JCheckBox>();
    permissionList.setModel(model);
    for (String item : title) {
      model.addElement(new JCheckBox(item));
    }

    permissionScroller =
      new JScrollPane(permissionList,
                      ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    permissionList.setVisibleRowCount(12);
    permissionList.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == ' ') {
          int index = permissionList.getSelectedIndex();
          JCheckBox checkbox =
            permissionList.getModel().getElementAt(index);
          checkbox.setSelected(!checkbox.isSelected());
          permissionList.repaint();
        }
      }
    });

    Container outer = getContentPane();
    Box vbox = Box.createVerticalBox();
    vbox.setBorder(new EmptyBorder(BORDER, BORDER, BORDER, BORDER));
    outer.add(vbox);

    String labelText = AndroidMode.getTextString("permissions.dialog.label");
    String urlText = AndroidMode.getTextString("permissions.dialog.url", GUIDE_URL);
    JLabel textarea = new JLabel(labelText);
    JLabel urlarea = new JLabel(urlText);
    textarea.setPreferredSize(new Dimension(TEXT_WIDTH, TEXT_HEIGHT));
    urlarea.setPreferredSize(new Dimension(URL_WIDTH, URL_HEIGHT));
    urlarea.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Platform.openURL(GUIDE_URL);
      }
    });
    urlarea.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
    textarea.setAlignmentX(LEFT_ALIGNMENT);
    urlarea.setAlignmentX(LEFT_ALIGNMENT);
    vbox.add(textarea);
    vbox.add(urlarea);

    permissionScroller.setAlignmentX(LEFT_ALIGNMENT);
    vbox.add(permissionScroller);
    vbox.add(Box.createVerticalStrut(GAP));

    descriptionLabel = new JLabel();
    descriptionLabel.setPreferredSize(new Dimension(DESC_WIDTH, DESC_HEIGHT));
    descriptionLabel.setVerticalAlignment(SwingConstants.TOP);
    descriptionLabel.setAlignmentX(LEFT_ALIGNMENT);
    vbox.add(descriptionLabel);
    vbox.add(Box.createVerticalStrut(GAP));

    JPanel buttons = new JPanel();
    buttons.setAlignmentX(LEFT_ALIGNMENT);
    JButton okButton = new JButton(Language.text("prompt.ok"));
    Dimension dim = new Dimension(Toolkit.getButtonWidth(),
                                  okButton.getPreferredSize().height);
    okButton.setPreferredSize(dim);
    okButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        saveSelections();
        setVisible(false);
      }
    });
    okButton.setEnabled(true);

    JButton cancelButton = new JButton(Language.text("prompt.cancel"));
    cancelButton.setPreferredSize(dim);
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
      }
    });
    cancelButton.setEnabled(true);

    // think different, biznatchios!
    if (Platform.isMacOS()) {
      buttons.add(cancelButton);
      buttons.add(okButton);
    } else {
      buttons.add(okButton);
      buttons.add(cancelButton);
    }
    vbox.add(buttons);

    JRootPane root = getRootPane();
    root.setDefaultButton(okButton);
    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        setVisible(false);
      }
    };
    Toolkit.registerWindowCloseKeys(root, disposer);
    Toolkit.setIcon(this);

    pack();

    Dimension screen = Toolkit.getScreenSize();
    Dimension windowSize = getSize();

    setLocation((screen.width - windowSize.width) / 2,
                (screen.height - windowSize.height) / 2);

    Manifest mf = new Manifest(sketch, appComp, modeFolder, false);
    setSelections(mf.getPermissions());

    // show the window and get to work
    setVisible(true);
  }


  @SuppressWarnings("rawtypes")
  protected void setSelections(String[] sel) {
//    processing.core.PApplet.println("permissions are:");
//    processing.core.PApplet.println(sel);
    HashMap<String,Object> map = new HashMap<String, Object>();
    for (String s : sel) {
      map.put(s, new Object());
    }
    DefaultListModel model = (DefaultListModel) permissionList.getModel();
    for (int i = 0; i < count; i++) {
      JCheckBox box = (JCheckBox) model.get(i);
//      System.out.println(map.containsKey(box.getText()) + " " + box.getText());
      box.setSelected(map.containsKey(box.getText()));
    }
  }


  @SuppressWarnings("rawtypes")
  protected String[] getSelections() {
    ArrayList<String> sel = new ArrayList<String>();
    DefaultListModel model = (DefaultListModel) permissionList.getModel();
    for (int i = 0; i < count; i++) {
      if (((JCheckBox) model.get(i)).isSelected()) {
        sel.add(title[i]);
      }
    }
    return sel.toArray(new String[0]);
  }


  protected void saveSelections() {
    String[] sel = getSelections();
    Manifest mf = new Manifest(sketch, appComp, modeFolder, false);
    mf.setPermissions(sel);
  }


  public String getMenuTitle() {
    return "Android Permissions";
  }


  // List of constants for each permission and a brief description:
  // https://developer.android.com/reference/android/Manifest.permission
  static final String[] listing = {
    "ACCEPT_HANDOVER", "Allows a calling app to continue a call which was started in another app.",
    "ACCESS_BACKGROUND_LOCATION", "Allows an app to access location in the background.",
    "ACCESS_BLOBS_ACROSS_USERS", "Allows an application to access data blobs across users.",
    "ACCESS_CHECKIN_PROPERTIES", "Allows read/write access to the \"properties\" table in the checkin database, to change values that get uploaded.",
    "ACCESS_COARSE_LOCATION", "Allows an app to access approximate location.",
    "ACCESS_FINE_LOCATION", "Allows an app to access precise location.",
    "ACCESS_LOCATION_EXTRA_COMMANDS", "Allows an application to access extra location provider commands.",
    "ACCESS_MEDIA_LOCATION", "Allows an application to access any geographic locations persisted in the user's shared collection.",
    "ACCESS_NETWORK_STATE", "Allows applications to access information about networks.",
    "ACCESS_NOTIFICATION_POLICY", "Marker permission for applications that wish to access notification policy.",
    "ACCESS_WIFI_STATE", "Allows applications to access information about Wi-Fi networks.",
    "ACCOUNT_MANAGER", "Allows applications to call into AccountAuthenticators.",
    "ACTIVITY_RECOGNITION", "Allows an application to recognize physical activity.",
    "ADD_VOICEMAIL", "Allows an application to add voicemails into the system.",
    "ANSWER_PHONE_CALLS", "Allows the app to answer an incoming phone call.",
    "BATTERY_STATS", "Allows an application to collect battery statistics",
    "BIND_ACCESSIBILITY_SERVICE", "Must be required by an <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/accessibilityservice/AccessibilityService\">AccessibilityService</a></code> , to ensure that only the system can bind to it.",
    "BIND_APPWIDGET", "Allows an application to tell the AppWidget service which application can access AppWidget's data.",
    "BIND_AUTOFILL_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/autofill/AutofillService\">AutofillService</a></code> , to ensure that only the system can bind to it.",
    "BIND_CALL_REDIRECTION_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/CallRedirectionService\">CallRedirectionService</a></code> , to ensure that only the system can bind to it.",
    "BIND_CARRIER_MESSAGING_CLIENT_SERVICE", "A subclass of <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/carrier/CarrierMessagingClientService\">CarrierMessagingClientService</a></code> must be protected with this permission.",
    "BIND_CARRIER_SERVICES", "The system process that is allowed to bind to services in carrier apps will have this permission.",
    "BIND_COMPANION_DEVICE_SERVICE", "Must be required by any <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/CompanionDeviceService\">CompanionDeviceService</a></code> s to ensure that only the system can bind to it.",
    "BIND_CONDITION_PROVIDER_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/notification/ConditionProviderService\">ConditionProviderService</a></code> , to ensure that only the system can bind to it.",
    "BIND_CONTROLS", "Allows SystemUI to request third party controls.",
    "BIND_DEVICE_ADMIN", "Must be required by device administration receiver, to ensure that only the system can interact with it.",
    "BIND_DREAM_SERVICE", "Must be required by an <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/dreams/DreamService\">DreamService</a></code> , to ensure that only the system can bind to it.",
    "BIND_INCALL_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/InCallService\">InCallService</a></code> , to ensure that only the system can bind to it.",
    "BIND_INPUT_METHOD", "Must be required by an <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/inputmethodservice/InputMethodService\">InputMethodService</a></code> , to ensure that only the system can bind to it.",
    "BIND_MIDI_DEVICE_SERVICE", "Must be required by an <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/media/midi/MidiDeviceService\">MidiDeviceService</a></code> , to ensure that only the system can bind to it.",
    "BIND_NFC_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/nfc/cardemulation/HostApduService\">HostApduService</a></code> or <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/nfc/cardemulation/OffHostApduService\">OffHostApduService</a></code> to ensure that only the system can bind to it.",
    "BIND_NOTIFICATION_LISTENER_SERVICE", "Must be required by an <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/notification/NotificationListenerService\">NotificationListenerService</a></code> , to ensure that only the system can bind to it.",
    "BIND_PRINT_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/printservice/PrintService\">PrintService</a></code> , to ensure that only the system can bind to it.",
    "BIND_QUICK_ACCESS_WALLET_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/quickaccesswallet/QuickAccessWalletService\">QuickAccessWalletService</a></code> to ensure that only the system can bind to it.",
    "BIND_QUICK_SETTINGS_TILE", "Allows an application to bind to third party quick settings tiles.",
    "BIND_REMOTEVIEWS", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/widget/RemoteViewsService\">RemoteViewsService</a></code> , to ensure that only the system can bind to it.",
    "BIND_SCREENING_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/CallScreeningService\">CallScreeningService</a></code> , to ensure that only the system can bind to it.",
    "BIND_TELECOM_CONNECTION_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/ConnectionService\">ConnectionService</a></code> , to ensure that only the system can bind to it.",
    "BIND_TEXT_SERVICE", "Must be required by a TextService (e.g. SpellCheckerService) to ensure that only the system can bind to it.",
    "BIND_TV_INPUT", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/media/tv/TvInputService\">TvInputService</a></code> to ensure that only the system can bind to it.",
    "BIND_TV_INTERACTIVE_APP", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/media/tv/interactive/TvInteractiveAppService\">TvInteractiveAppService</a></code> to ensure that only the system can bind to it.",
    "BIND_VISUAL_VOICEMAIL_SERVICE", "Must be required by a link <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telephony/VisualVoicemailService\">VisualVoicemailService</a></code> to ensure that only the system can bind to it.",
    "BIND_VOICE_INTERACTION", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/voice/VoiceInteractionService\">VoiceInteractionService</a></code> , to ensure that only the system can bind to it.",
    "BIND_VPN_SERVICE", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/net/VpnService\">VpnService</a></code> , to ensure that only the system can bind to it.",
    "BIND_VR_LISTENER_SERVICE", "Must be required by an <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/vr/VrListenerService\">VrListenerService</a></code> , to ensure that only the system can bind to it.",
    "BIND_WALLPAPER", "Must be required by a <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/service/wallpaper/WallpaperService\">WallpaperService</a></code> , to ensure that only the system can bind to it.",
    "BLUETOOTH", "Allows applications to connect to paired bluetooth devices.",
    "BLUETOOTH_ADMIN", "Allows applications to discover and pair bluetooth devices.",
    "BLUETOOTH_ADVERTISE", "Required to be able to advertise to nearby Bluetooth devices.",
    "BLUETOOTH_CONNECT", "Required to be able to connect to paired Bluetooth devices.",
    "BLUETOOTH_PRIVILEGED", "Allows applications to pair bluetooth devices without user interaction, and to allow or disallow phonebook access or message access.",
    "BLUETOOTH_SCAN", "Required to be able to discover and pair nearby Bluetooth devices.",
    "BODY_SENSORS", "Allows an application to access data from sensors that the user uses to measure what is happening inside their body, such as heart rate.",
    "BODY_SENSORS_BACKGROUND", "Allows an application to access data from sensors that the user uses to measure what is happening inside their body, such as heart rate.",
    "BROADCAST_PACKAGE_REMOVED", "Allows an application to broadcast a notification that an application package has been removed.",
    "BROADCAST_SMS", "Allows an application to broadcast an SMS receipt notification.",
    "BROADCAST_STICKY", "Allows an application to broadcast sticky intents.",
    "BROADCAST_WAP_PUSH", "Allows an application to broadcast a WAP PUSH receipt notification.",
    "CALL_COMPANION_APP", "Allows an app which implements the <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/InCallService\">InCallService</a></code> API to be eligible to be enabled as a calling companion app.",
    "CALL_PHONE", "Allows an application to initiate a phone call without going through the Dialer user interface for the user to confirm the call.",
    "CALL_PRIVILEGED", "Allows an application to call any phone number, including emergency numbers, without going through the Dialer user interface for the user to confirm the call being placed.",
    "CAMERA", "Required to be able to access the camera device.",
    "CAPTURE_AUDIO_OUTPUT", "Allows an application to capture audio output.",
    "CHANGE_COMPONENT_ENABLED_STATE", "Allows an application to change whether an application component (other than its own) is enabled or not.",
    "CHANGE_CONFIGURATION", "Allows an application to modify the current configuration, such as locale.",
    "CHANGE_NETWORK_STATE", "Allows applications to change network connectivity state.",
    "CHANGE_WIFI_MULTICAST_STATE", "Allows applications to enter Wi-Fi Multicast mode.",
    "CHANGE_WIFI_STATE", "Allows applications to change Wi-Fi connectivity state.",
    "CLEAR_APP_CACHE", "Allows an application to clear the caches of all installed applications on the device.",
    "CONTROL_LOCATION_UPDATES", "Allows enabling/disabling location update notifications from the radio.",
    "DELETE_CACHE_FILES", "Old permission for deleting an app's cache files, no longer used, but signals for us to quietly ignore calls instead of throwing an exception.",
    "DELETE_PACKAGES", "Allows an application to delete packages.",
    "DELIVER_COMPANION_MESSAGES", "Allows an application to deliver companion messages to system",
    "DIAGNOSTIC", "Allows applications to RW to diagnostic resources.",
    "DISABLE_KEYGUARD", "Allows applications to disable the keyguard if it is not secure.",
    "DUMP", "Allows an application to retrieve state dump information from system services.",
    "EXPAND_STATUS_BAR", "Allows an application to expand or collapse the status bar.",
    "FACTORY_TEST", "Run as a manufacturer test application, running as the root user.",
    "FOREGROUND_SERVICE", "Allows a regular application to use <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/app/Service#startForeground(int,%20android.app.Notification)\">Service.startForeground</a></code> .",
    "GET_ACCOUNTS", "Allows access to the list of accounts in the Accounts Service.",
    "GET_ACCOUNTS_PRIVILEGED", "Allows access to the list of accounts in the Accounts Service.",
    "GET_PACKAGE_SIZE", "Allows an application to find out the space used by any package.",
    "GLOBAL_SEARCH", "This permission can be used on content providers to allow the global search system to access their data.",
    "HIDE_OVERLAY_WINDOWS", "Allows an app to prevent non-system-overlay windows from being drawn on top of it",
    "HIGH_SAMPLING_RATE_SENSORS", "Allows an app to access sensor data with a sampling rate greater than 200 Hz.",
    "INSTALL_LOCATION_PROVIDER", "Allows an application to install a location provider into the Location Manager.",
    "INSTALL_PACKAGES", "Allows an application to install packages.",
    "INSTALL_SHORTCUT", "Allows an application to install a shortcut in Launcher.",
    "INSTANT_APP_FOREGROUND_SERVICE", "Allows an instant app to create foreground services.",
    "INTERACT_ACROSS_PROFILES", "Allows interaction across profiles in the same profile group.",
    "INTERNET", "Allows applications to open network sockets.",
    "KILL_BACKGROUND_PROCESSES", "Allows an application to call <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String)\">ActivityManager.killBackgroundProcesses(String)</a></code> .",
    "LAUNCH_MULTI_PANE_SETTINGS_DEEP_LINK", "An application needs this permission for <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/provider/Settings#ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY\">Settings.ACTION_SETTINGS_EMBED_DEEP_LINK_ACTIVITY</a></code> to show its <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/app/Activity\">Activity</a></code> embedded in Settings app.",
    "LOADER_USAGE_STATS", "Allows a data loader to read a package's access logs.",
    "LOCATION_HARDWARE", "Allows an application to use location features in hardware, such as the geofencing api.",
    "MANAGE_DOCUMENTS", "Allows an application to manage access to documents, usually as part of a document picker.",
    "MANAGE_EXTERNAL_STORAGE", "Allows an application a broad access to external storage in scoped storage.",
    "MANAGE_MEDIA", "Allows an application to modify and delete media files on this device or any connected storage device without user confirmation.",
    "MANAGE_ONGOING_CALLS", "Allows to query ongoing call details and manage ongoing calls",
    "MANAGE_OWN_CALLS", "Allows a calling application which manages its own calls through the self-managed <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/ConnectionService\">ConnectionService</a></code> APIs.",
    "MANAGE_WIFI_INTERFACES", "Allows applications to get notified when a Wi-Fi interface request cannot be satisfied without tearing down one or more other interfaces, and provide a decision whether to approve the request or reject it.",
    "MANAGE_WIFI_NETWORK_SELECTION", "This permission is used to let OEMs grant their trusted app access to a subset of privileged wifi APIs to improve wifi performance.",
    "MASTER_CLEAR", "Not for use by third-party applications.",
    "MEDIA_CONTENT_CONTROL", "Allows an application to know what content is playing and control its playback.",
    "MODIFY_AUDIO_SETTINGS", "Allows an application to modify global audio settings.",
    "MODIFY_PHONE_STATE", "Allows modification of the telephony state - power on, mmi, etc.",
    "MOUNT_FORMAT_FILESYSTEMS", "Allows formatting file systems for removable storage.",
    "MOUNT_UNMOUNT_FILESYSTEMS", "Allows mounting and unmounting file systems for removable storage.",
    "NEARBY_WIFI_DEVICES", "Required to be able to advertise and connect to nearby devices via Wi-Fi.",
    "NFC", "Allows applications to perform I/O operations over NFC.",
    "NFC_PREFERRED_PAYMENT_INFO", "Allows applications to receive NFC preferred payment service information.",
    "NFC_TRANSACTION_EVENT", "Allows applications to receive NFC transaction events.",
    "OVERRIDE_WIFI_CONFIG", "Allows an application to modify any wifi configuration, even if created by another application.",
    "PACKAGE_USAGE_STATS", "Allows an application to collect component usage statistics",
    "POST_NOTIFICATIONS", "Allows an app to post notifications",
    "QUERY_ALL_PACKAGES", "Allows query of any normal app on the device, regardless of manifest declarations.",
    "READ_ASSISTANT_APP_SEARCH_DATA", "Allows an application to query over global data in AppSearch that's visible to the ASSISTANT role.",
    "READ_BASIC_PHONE_STATE", "Allows read only access to phone state with a non dangerous permission, including the information like cellular network type, software version.",
    "READ_CALENDAR", "Allows an application to read the user's calendar data.",
    "READ_CALL_LOG", "Allows an application to read the user's call log.",
    "READ_CONTACTS", "Allows an application to read the user's contacts data.",
    "READ_EXTERNAL_STORAGE", "Allows an application to read from external storage.",
    "READ_HOME_APP_SEARCH_DATA", "Allows an application to query over global data in AppSearch that's visible to the HOME role.",
    "READ_LOGS", "Allows an application to read the low-level system log files.",
    "READ_MEDIA_AUDIO", "Allows an application to read audio files from external storage.",
    "READ_MEDIA_IMAGES", "Allows an application to read image files from external storage.",
    "READ_MEDIA_VIDEO", "Allows an application to read video files from external storage.",
    "READ_NEARBY_STREAMING_POLICY", "Allows an application to read nearby streaming policy.",
    "READ_PHONE_NUMBERS", "Allows read access to the device's phone number(s).",
    "READ_PHONE_STATE", "Allows read only access to phone state, including the current cellular network information, the status of any ongoing calls, and a list of any <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/telecom/PhoneAccount\">PhoneAccount</a></code> s registered on the device.",
    "READ_PRECISE_PHONE_STATE", "Allows read only access to precise phone state.",
    "READ_SMS", "Allows an application to read SMS messages.",
    "READ_SYNC_SETTINGS", "Allows applications to read the sync settings.",
    "READ_SYNC_STATS", "Allows applications to read the sync stats.",
    "READ_VOICEMAIL", "Allows an application to read voicemails in the system.",
    "REBOOT", "Required to be able to reboot the device.",
    "RECEIVE_BOOT_COMPLETED", "Allows an application to receive the <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/content/Intent#ACTION_BOOT_COMPLETED\">Intent.ACTION_BOOT_COMPLETED</a></code> that is broadcast after the system finishes booting.",
    "RECEIVE_MMS", "Allows an application to monitor incoming MMS messages.",
    "RECEIVE_SMS", "Allows an application to receive SMS messages.",
    "RECEIVE_WAP_PUSH", "Allows an application to receive WAP push messages.",
    "RECORD_AUDIO", "Allows an application to record audio.",
    "REORDER_TASKS", "Allows an application to change the Z-order of tasks.",
    "REQUEST_COMPANION_PROFILE_APP_STREAMING", "Allows application to request to be associated with a virtual display capable of streaming Android applications ( <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/AssociationRequest#DEVICE_PROFILE_APP_STREAMING\">AssociationRequest.DEVICE_PROFILE_APP_STREAMING</a></code> ) by <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/CompanionDeviceManager\">CompanionDeviceManager</a></code> .",
    "REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION", "Allows application to request to be associated with a vehicle head unit capable of automotive projection ( <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/AssociationRequest#DEVICE_PROFILE_AUTOMOTIVE_PROJECTION\">AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION</a></code> ) by <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/CompanionDeviceManager\">CompanionDeviceManager</a></code> .",
    "REQUEST_COMPANION_PROFILE_COMPUTER", "Allows application to request to be associated with a computer to share functionality and/or data with other devices, such as notifications, photos and media ( <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/AssociationRequest#DEVICE_PROFILE_COMPUTER\">AssociationRequest.DEVICE_PROFILE_COMPUTER</a></code> ) by <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/CompanionDeviceManager\">CompanionDeviceManager</a></code> .",
    "REQUEST_COMPANION_PROFILE_WATCH", "Allows app to request to be associated with a device via <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/companion/CompanionDeviceManager\">CompanionDeviceManager</a></code> as a \"watch\"",
    "REQUEST_COMPANION_RUN_IN_BACKGROUND", "Allows a companion app to run in the background.",
    "REQUEST_COMPANION_SELF_MANAGED", "Allows an application to create a \"self-managed\" association.",
    "REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND", "Allows a companion app to start a foreground service from the background.",
    "REQUEST_COMPANION_USE_DATA_IN_BACKGROUND", "Allows a companion app to use data in the background.",
    "REQUEST_DELETE_PACKAGES", "Allows an application to request deleting packages.",
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "Permission an application must hold in order to use <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/provider/Settings#ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS\">Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS</a></code> .",
    "REQUEST_INSTALL_PACKAGES", "Allows an application to request installing packages.",
    "REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE", "Allows an application to subscribe to notifications about the presence status change of their associated companion device",
    "REQUEST_PASSWORD_COMPLEXITY", "Allows an application to request the screen lock complexity and prompt users to update the screen lock to a certain complexity level.",
    "SCHEDULE_EXACT_ALARM", "Allows applications to use exact alarm APIs.",
    "SEND_RESPOND_VIA_MESSAGE", "Allows an application (Phone) to send a request to other applications to handle the respond-via-message action during incoming calls.",
    "SEND_SMS", "Allows an application to send SMS messages.",
    "SET_ALARM", "Allows an application to broadcast an Intent to set an alarm for the user.",
    "SET_ALWAYS_FINISH", "Allows an application to control whether activities are immediately finished when put in the background.",
    "SET_ANIMATION_SCALE", "Modify the global animation scaling factor.",
    "SET_DEBUG_APP", "Configure an application for debugging.",
    "SET_PROCESS_LIMIT", "Allows an application to set the maximum number of (not needed) application processes that can be running.",
    "SET_TIME", "Allows applications to set the system time directly.",
    "SET_TIME_ZONE", "Allows applications to set the system time zone directly.",
    "SET_WALLPAPER", "Allows applications to set the wallpaper.",
    "SET_WALLPAPER_HINTS", "Allows applications to set the wallpaper hints.",
    "SIGNAL_PERSISTENT_PROCESSES", "Allow an application to request that a signal be sent to all persistent processes.",
    "START_FOREGROUND_SERVICES_FROM_BACKGROUND", "Allows an application to start foreground services from the background at any time.",
    "START_VIEW_APP_FEATURES", "Allows the holder to start the screen with a list of app features.",
    "START_VIEW_PERMISSION_USAGE", "Allows the holder to start the permission usage screen for an app.",
    "STATUS_BAR", "Allows an application to open, close, or disable the status bar and its icons.",
    "SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE", "Allows an application to subscribe to keyguard locked (i.e., showing) state.",
    "SYSTEM_ALERT_WINDOW", "Allows an app to create windows using the type <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY\">WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY</a></code> , shown on top of all other apps.",
    "TRANSMIT_IR", "Allows using the device's IR transmitter, if available.",
    "UPDATE_DEVICE_STATS", "Allows an application to update device statistics.",
    "UPDATE_PACKAGES_WITHOUT_USER_ACTION", "Allows an application to indicate via <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)\">PackageInstaller.SessionParams.setRequireUserAction(int)</a></code> that user action should not be required for an app update.",
    "USE_BIOMETRIC", "Allows an app to use device supported biometric modalities.",
    "USE_EXACT_ALARM", "Allows apps to use exact alarms just like with <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/Manifest.permission#SCHEDULE_EXACT_ALARM\">SCHEDULE_EXACT_ALARM</a></code> but without needing to request this permission from the user.",
    "USE_FULL_SCREEN_INTENT", "Required for apps targeting <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/os/Build.VERSION_CODES#Q\">Build.VERSION_CODES.Q</a></code> that want to use <code dir=\"ltr\" translate=\"no\"><a href=\"/reference/android/app/Notification.Builder#setFullScreenIntent(android.app.PendingIntent,%20boolean)\">notification full screen intents</a></code> .",
    "USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER", "Allows to read device identifiers and use ICC based authentication like EAP-AKA.",
    "USE_SIP", "Allows an application to use SIP service.",
    "UWB_RANGING", "Required to be able to range to devices using ultra-wideband.",
    "VIBRATE", "Allows access to the vibrator.",
    "WAKE_LOCK", "Allows using PowerManager WakeLocks to keep processor from sleeping or screen from dimming.",
    "WRITE_APN_SETTINGS", "Allows applications to write the apn settings and read sensitive fields of an existing apn settings like user and password.",
    "WRITE_CALENDAR", "Allows an application to write the user's calendar data.",
    "WRITE_CALL_LOG", "Allows an application to write (but not read) the user's call log data.",
    "WRITE_CONTACTS", "Allows an application to write the user's contacts data.",
    "WRITE_EXTERNAL_STORAGE", "Allows an application to write to external storage.",
    "WRITE_GSERVICES", "Allows an application to modify the Google service map.",
    "WRITE_SECURE_SETTINGS", "Allows an application to read or write the secure system settings.",
    "WRITE_SETTINGS", "Allows an application to read or write the system settings.",
    "WRITE_SYNC_SETTINGS", "Allows applications to write the sync settings.",
    "WRITE_VOICEMAIL", "Allows an application to modify and remove existing voicemails in the system."
  };
  
  // Dangerous permissions that need runtime approval:
  // https://developer.android.com/guide/topics/permissions/overview#runtime
  public static final String[] dangerous = {
    "ACCEPT_HANDOVER",
    "ACCESS_BACKGROUND_LOCATION",
    "ACCESS_COARSE_LOCATION",
    "ACCESS_FINE_LOCATION",
    "ACCESS_MEDIA_LOCATION",
    "ACTIVITY_RECOGNITION",
    "ADD_VOICEMAIL",
    "ANSWER_PHONE_CALLS",
    "BLUETOOTH_ADVERTISE",
    "BLUETOOTH_CONNECT",
    "BLUETOOTH_SCAN",
    "BODY_SENSORS",
    "BODY_SENSORS_BACKGROUND",
    "CALL_PHONE",
    "CAMERA",
    "GET_ACCOUNTS",
    "NEARBY_WIFI_DEVICES",
    "POST_NOTIFICATIONS",
    "READ_CALENDAR",
    "READ_CALL_LOG",
    "READ_CONTACTS",
    "READ_EXTERNAL_STORAGE",
    "READ_MEDIA_AUDIO",
    "READ_MEDIA_IMAGES",
    "READ_MEDIA_VIDEO",
    "READ_PHONE_NUMBERS",
    "READ_PHONE_STATE",
    "READ_SMS",
    "RECEIVE_MMS",
    "RECEIVE_SMS",
    "RECEIVE_WAP_PUSH",
    "RECORD_AUDIO",
    "SEND_SMS",
    "USE_SIP",
    "UWB_RANGING",
    "WRITE_CALENDAR",
    "WRITE_CALL_LOG",
    "WRITE_CONTACTS",
    "WRITE_EXTERNAL_STORAGE"
  };

  static String[] title;
  static String[] description;
  static int count;
  static {
    count = listing.length / 2;
    title = new String[count];
    description = new String[count];
    for (int i = 0; i < count; i++) {
      title[i] = listing[i*2];
      description[i] = listing[i*2+1];
    }
  }
}


// Code for this CheckBoxList class found on the net, though I've lost the
// link. If you run across the original version, please let me know so that
// the original author can be credited properly. It was from a snippet
// collection, but it seems to have been picked up so many places with others
// placing their copyright on it that I haven't been able to determine the
// original author. [fry 20100216]
@SuppressWarnings("serial")
class CheckBoxList extends JList<JCheckBox> {
  protected static Border noFocusBorder = new EmptyBorder(1, 1, 1, 1);
  int checkboxWidth;

  public CheckBoxList() {
    setCellRenderer(new CellRenderer());

    // get the width of a checkbox so we can figure out if the mouse is inside
    checkboxWidth = new JCheckBox().getPreferredSize().width;
    // add the amount for the inset
    checkboxWidth += Permissions.BORDER_HORIZ;

    addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {
        if (isEnabled()) {
//          System.out.println("cbw = " + checkboxWidth);
          int index = locationToIndex(e.getPoint());
//          descriptionLabel.setText(description[index]);
          if (index != -1) {
            JCheckBox checkbox = getModel().getElementAt(index);
            //System.out.println("mouse event in list: " + e);
//            System.out.println(checkbox.getSize() + " ... " + checkbox);
//            if (e.getX() < checkbox.getSize().height) {
            if (e.getX() < checkboxWidth) {
              checkbox.setSelected(!checkbox.isSelected());
              repaint();
            }
          }
        }
      }
    });
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }


  protected class CellRenderer implements ListCellRenderer<JCheckBox> {
    public Component getListCellRendererComponent(JList<? extends JCheckBox> list,
                                                  JCheckBox checkbox,
                                                  int index, boolean isSelected,
                                                  boolean cellHasFocus) {
      checkbox.setBackground(isSelected ? getSelectionBackground() : getBackground());
      checkbox.setForeground(isSelected ? getSelectionForeground() : getForeground());
      checkbox.setEnabled(list.isEnabled());
      checkbox.setFont(getFont());
      checkbox.setFocusPainted(false);
      checkbox.setBorderPainted(true);
      checkbox.setBorder(isSelected ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);
      return checkbox;
    }
  }
}
