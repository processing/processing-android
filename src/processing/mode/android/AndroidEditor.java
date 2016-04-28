/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2009-11 Ben Fry and Casey Reas

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

import processing.app.Base;
import processing.app.Messages;
import processing.app.Mode;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.SketchException;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
import processing.app.ui.EditorToolbar;
import processing.app.ui.Toolkit;
import processing.core.PApplet;
import processing.mode.java.JavaEditor;
import processing.mode.java.preproc.PdePreprocessor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TimerTask;


@SuppressWarnings("serial")
public class AndroidEditor extends JavaEditor {
  private AndroidMode androidMode;

  protected AndroidEditor(Base base, String path, EditorState state, 
                          Mode mode) throws EditorException {
    super(base, path, state, mode);
    androidMode = (AndroidMode) mode;
    androidMode.resetUserSelection();
    androidMode.checkSDK(this);
  }  

  @Override
  public PdePreprocessor createPreprocessor(final String sketchName) {
    return new AndroidPreprocessor(sketchName);  
  }

  class UpdateDeviceListTask extends TimerTask {

    private JMenu deviceMenu;

    public UpdateDeviceListTask(JMenu deviceMenu) {
      this.deviceMenu = deviceMenu;
    }

    @Override
    public void run() {
      if (androidMode == null || androidMode.getSDK() == null) return;

      final Devices devices = Devices.getInstance();
      java.util.List<Device> deviceList = devices.findMultiple(false);
      Device selectedDevice = devices.getSelectedDevice();

      if (deviceList.size() == 0) {
        //if (deviceMenu.getItem(0).isEnabled()) {
        if (0 < deviceMenu.getItemCount()) {
          deviceMenu.removeAll();
          JMenuItem noDevicesItem = new JMenuItem("No connected devices");
          noDevicesItem.setEnabled(false);
          deviceMenu.add(noDevicesItem);
        }
        devices.setSelectedDevice(null);
      } else {
        deviceMenu.removeAll();

        if (selectedDevice == null) {
          selectedDevice = deviceList.get(0);
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
            selectedDevice = deviceList.get(0);
            devices.setSelectedDevice(selectedDevice);
          }
        }

        for (final Device device : deviceList) {
          final JCheckBoxMenuItem deviceItem = new JCheckBoxMenuItem(device.getName());
          deviceItem.setEnabled(true);

          if (device.equals(selectedDevice)) deviceItem.setState(true);

          // prevent checkboxmenuitem automatic state changing onclick
          deviceItem.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
              if (device.equals(devices.getSelectedDevice())) deviceItem.setState(true);
              else deviceItem.setState(false);
            }
          });

          deviceItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
              devices.setSelectedDevice(device);

              for (int i = 0; i < deviceMenu.getItemCount(); i++) {
                ((JCheckBoxMenuItem) deviceMenu.getItem(i)).setState(false);
              }

              deviceItem.setState(true);
            }
          });

          deviceMenu.add(deviceItem);
        }
      }
    }
  }


  public EditorToolbar createToolbar() {
    return new AndroidToolbar(this, base);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public JMenu buildFileMenu() {
    String exportPkgTitle = AndroidToolbar.getTitle(AndroidToolbar.EXPORT, false);
    JMenuItem exportPackage = Toolkit.newJMenuItem(exportPkgTitle, 'E');
    exportPackage.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleExportPackage();
      }
    });

    String exportProjectTitle = AndroidToolbar.getTitle(AndroidToolbar.EXPORT, true);
    JMenuItem exportProject = Toolkit.newJMenuItemShift(exportProjectTitle, 'E');
    exportProject.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        handleExportProject();
      }
    });

    return buildFileMenu(new JMenuItem[] { exportPackage, exportProject});
  }


  public JMenu buildSketchMenu() {
    JMenuItem runItem = Toolkit.newJMenuItem(AndroidToolbar.getTitle(AndroidToolbar.RUN, false), 'R');
    runItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleRunDevice();
        }
      });

    JMenuItem presentItem = Toolkit.newJMenuItemShift(AndroidToolbar.getTitle(AndroidToolbar.RUN, true), 'R');
    presentItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleRunEmulator();
        }
      });

    JMenuItem stopItem = new JMenuItem(AndroidToolbar.getTitle(AndroidToolbar.STOP, false));
    stopItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          handleStop();
        }
      });
    return buildSketchMenu(new JMenuItem[] { runItem, presentItem, stopItem });
  }


  public JMenu buildModeMenu() {
    JMenu menu = new JMenu("Android");
    JMenuItem item;

    item = new JMenuItem("Sketch Permissions");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        new Permissions(sketch);
      }
    });
    menu.add(item);

    menu.addSeparator();

    /*item = new JMenuItem("Signing Key Setup");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        new Keys(AndroidEditor.this);
      }
    });
    item.setEnabled(false);
    menu.add(item); */

    final JMenu mobDeveMenu = new JMenu("Select mobile device");

    JMenuItem noMobDevItem = new JMenuItem("No connected devices");
    noMobDevItem.setEnabled(false);
    mobDeveMenu.add(noMobDevItem);
    menu.add(mobDeveMenu);
    
    // start updating device menus
    UpdateDeviceListTask task = new UpdateDeviceListTask(mobDeveMenu);
    java.util.Timer timer = new java.util.Timer();
    timer.schedule(task, 5000, 5000);
    
    final JMenu wearDevMenu = new JMenu("Select wearable device");

    JMenuItem noWearDevicesItem = new JMenuItem("No connected devices");
    noWearDevicesItem.setEnabled(false);
    wearDevMenu.add(noWearDevicesItem);
    menu.add(wearDevMenu);    
    
    menu.addSeparator();

    final JMenu publishMenu = new JMenu("Build sketch as...");    
    final JCheckBoxMenuItem fragmentItem = new JCheckBoxMenuItem("Regular app");
    final JCheckBoxMenuItem wallpaperItem = new JCheckBoxMenuItem("Live wallpaper");
    final JCheckBoxMenuItem watchfaceItem = new JCheckBoxMenuItem("Watch face");
    final JCheckBoxMenuItem cardboardItem = new JCheckBoxMenuItem("Cardboard app");

    fragmentItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidBuild.setPublishOption(AndroidBuild.FRAGMENT, sketch);
        fragmentItem.setState(true);
        wallpaperItem.setState(false);
        watchfaceItem.setSelected(false);
        cardboardItem.setSelected(false);
      }
    });
    wallpaperItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidBuild.setPublishOption(AndroidBuild.WALLPAPER, sketch);
        fragmentItem.setState(false);
        wallpaperItem.setState(true);
        watchfaceItem.setSelected(false);
        cardboardItem.setSelected(false);
      }
    });
    watchfaceItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidBuild.setPublishOption(AndroidBuild.WATCHFACE, sketch);
        fragmentItem.setState(false);
        wallpaperItem.setState(false);
        watchfaceItem.setSelected(true);
        cardboardItem.setSelected(false);
      }
    });
    cardboardItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        AndroidBuild.setPublishOption(AndroidBuild.CARDBOARD, sketch);
        fragmentItem.setState(false);
        wallpaperItem.setState(false);
        watchfaceItem.setSelected(false);
        cardboardItem.setSelected(true);
      }
    });    
    
    fragmentItem.setState(true);
    wallpaperItem.setState(false);
    watchfaceItem.setSelected(false);
    cardboardItem.setSelected(false);    

    publishMenu.add(fragmentItem);
    publishMenu.add(wallpaperItem);
    publishMenu.add(watchfaceItem);
    publishMenu.add(cardboardItem);
    menu.add(publishMenu);     
    
    // TODO: The SDK selection menu will be removed once app publishing is fully
    // functional (correct SDK level can be inferred from app type (fragment, 
    // wallpaper, etc).
    final JMenu sdkMenu = new JMenu("Select target SDK");
    JMenuItem defaultItem = new JCheckBoxMenuItem("No available targets");
    defaultItem.setEnabled(false);
    sdkMenu.add(defaultItem);

    new Thread() {
      @Override
      public void run() {
        while(androidMode == null || androidMode.getSDK() == null) {
          try {
            Thread.sleep(3000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        updateSdkMenu(sdkMenu);
      }
    }.start();

    menu.add(sdkMenu);

    menu.addSeparator();
    
    item = new JMenuItem("Android SDK Manager");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        File file = androidMode.getSDK().getAndroidTool();
        try {
          Runtime.getRuntime().exec(new String[] { file.getAbsolutePath(), "sdk" });
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
    });
    menu.add(item);

    item = new JMenuItem("Android AVD Manager");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        File file = androidMode.getSDK().getAndroidTool();
        PApplet.exec(new String[] { file.getAbsolutePath(), "avd" });
      }
    });
    menu.add(item);

    item = new JMenuItem("Reset Connections");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
//        editor.statusNotice("Resetting the Android Debug Bridge server.");
        Devices.killAdbServer();
      }
    });
    menu.add(item);

    return menu;
  }

  private void updateSdkMenu(final JMenu sdkMenu) {
    try {
      ArrayList<AndroidSDK.SDKTarget> targets = androidMode.getSDK().getAvailableSdkTargets();

      if (targets.size() != 0) sdkMenu.removeAll();

      AndroidSDK.SDKTarget lowestTargetAvailable = null;
      JCheckBoxMenuItem lowestTargetMenuItem = null;

//      String savedTargetVersion = Preferences.get("android.sdk.version");
      String savedSdkName = Preferences.get("android.sdk.name");
      boolean savedTargetSet = false;

      for (final AndroidSDK.SDKTarget target : targets) {
        if (target.version < 11) {
          //We do not support API level less than 11
          continue;
        }
        
        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("API " + target.name + " (" + target.version + ")");

        if (savedTargetSet == false && (lowestTargetAvailable == null || lowestTargetAvailable.version > target.version)) {
          lowestTargetAvailable = target;
          lowestTargetMenuItem = item;
        }

        if (target.name.equals(savedSdkName)) {
          AndroidBuild.setSdkTarget(target, sketch);
          item.setState(true);
          savedTargetSet = true;
        }

        item.addChangeListener(new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            if (target.name.equals(AndroidBuild.sdkName)) item.setState(true);
            else item.setState(false);
          }
        });

        item.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            AndroidBuild.setSdkTarget(target, sketch);

            for (int i = 0; i < sdkMenu.getItemCount(); i++) {
              ((JCheckBoxMenuItem) sdkMenu.getItem(i)).setState(false);
            }

            item.setState(true);
          }
        });

        sdkMenu.add(item);
      }

      if (!savedTargetSet && lowestTargetAvailable != null) {
        AndroidBuild.setSdkTarget(lowestTargetAvailable, sketch);
        lowestTargetMenuItem.setState(true);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   * Uses the main help menu, and adds a few extra options. If/when there's
   * Android-specific documentation, we'll switch to that.
   */
  public JMenu buildHelpMenu() {
    JMenu menu = super.buildHelpMenu();
    JMenuItem item;

    menu.addSeparator();

    item = new JMenuItem("Processing for Android Wiki");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Platform.openURL("http://wiki.processing.org/w/Android");
      }
    });
    menu.add(item);


    item = new JMenuItem("Android Developer Site");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Platform.openURL("http://developer.android.com/");
      }
    });
    menu.add(item);

    return menu;
  }


  /** override the standard grab reference to just show the java reference */
  public void showReference(String filename) {
    File javaReferenceFolder = Platform.getContentFile("modes/java/reference");
    File file = new File(javaReferenceFolder, filename);
    Platform.openURL(file.toURI().toString());
  }



//  protected void updateMode() {
//    // When the selection is made, the menu will update itself
//    boolean active = toggleItem.isSelected();
//    if (active) {
//      boolean rolling = true;
//      if (sdk == null) {
//        rolling = loadAndroid();
//      }
//      if (rolling) {
//        editor.setHandlers(new RunHandler(), new PresentHandler(),
//                           new StopHandler(),
//                           new ExportHandler(),  new ExportAppHandler());
//        build = new AndroidBuild(editor, sdk);
//        editor.statusNotice("Android mode enabled for this editor window.");
//      }
//    } else {
//      editor.resetHandlers();
//      editor.statusNotice("Android mode disabled.");
//    }
//  }


//  protected boolean loadAndroid() {
//    statusNotice("Loading Android tools.");
//
//    try {
//      sdk = AndroidSDK.find(this);
//    } catch (final Exception e) {
//      Base.showWarning("Android Tools Error", e.getMessage(), null);
//      statusNotice("Android mode canceled.");
//      return false;
//    }
//
//    // Make sure that the processing.android.core.* classes are available
//    if (!checkCore()) {
//      statusNotice("Android mode canceled.");
//      return false;
//    }
//
//    statusNotice("Done loading Android tools.");
//    return true;
//  }


//  static protected File getCoreZipLocation() {
//    if (coreZipLocation == null) {
//      coreZipLocation = checkCoreZipLocation();
//    }
//    return coreZipLocation;
//  }


//  private boolean checkCore() {
//    final File target = getCoreZipLocation();
//    if (!target.exists()) {
//      try {
//        final URL url = new URL(ANDROID_CORE_URL);
//        PApplet.saveStream(target, url.openStream());
//      } catch (final Exception e) {
//        Base.showWarning("Download Error",
//          "Could not download Android core.zip", e);
//        return false;
//      }
//    }
//    return true;
//  }


  public void statusError(String what) {
    super.statusError(what);
//    new Exception("deactivating RUN").printStackTrace();
//    toolbar.deactivate(AndroidToolbar.RUN);
    toolbar.deactivateRun();
  }


  public void sketchStopped() {
    deactivateRun();
    statusEmpty();
  }


  /**
   * Build the sketch and run it inside an emulator with the debugger.
   */
  public void handleRunEmulator() {
    new Thread() {
      public void run() {
//        toolbar.activate(AndroidToolbar.RUN);
        toolbar.activateRun();
        startIndeterminate();
        prepareRun();
        try {
          androidMode.handleRunEmulator(sketch, AndroidEditor.this);
        } catch (SketchException e) {
          statusError(e);
        } catch (IOException e) {
          statusError(e);
        }
        stopIndeterminate();
      }
    }.start();
  }


  /**
   * Build the sketch and run it on a device with the debugger connected.
   */
  public void handleRunDevice() {
    if (Platform.isWindows() && !Preferences.getBoolean("usbDriverWarningShown")) {
      Preferences.setBoolean("usbDriverWarningShown", true);

      String message = "";
      File usbDriverFile = new File(((AndroidMode) sketch.getMode()).getSDK().getSdkFolder(), "extras/google/usb_driver");
      if (usbDriverFile.exists()) {
        message = "<html><body>" +
            "You might need to install Google USB Driver to run the sketch on your device.<br>" +
            "Please follow the guide at <a href='http://developer.android.com/tools/extras/oem-usb.html#InstallingDriver'>http://developer.android.com/tools/extras/oem-usb.html#InstallingDriver</a> to install the driver.<br>" +
            "For your reference, the driver is located in: " + usbDriverFile.getAbsolutePath();
      } else {
        message = "<html><body>" +
            "You might need to install Google USB Driver to run the sketch on your device.<br>" +
            "Please follow the guide at <a href='http://developer.android.com/tools/extras/oem-usb.html#InstallingDriver'>http://developer.android.com/tools/extras/oem-usb.html#InstallingDriver</a> to install the driver.<br>" +
            "You will also need to download the driver from <a href='http://developer.android.com/sdk/win-usb.html'>http://developer.android.com/sdk/win-usb.html</a>";
      }
      Messages.showWarning("USB Driver warning", message);

    } else {
      new Thread() {
        public void run() {
          toolbar.activateRun();
//          toolbar.activate(AndroidToolbar.RUN);
          startIndeterminate();
          prepareRun();
          try {
            androidMode.handleRunDevice(sketch, AndroidEditor.this);
          } catch (SketchException e) {
            statusError(e);
          } catch (IOException e) {
            statusError(e);
          }
          stopIndeterminate();
        }
      }.start();
    }
  }


  public void handleStop() {
    toolbar.deactivateRun();
//    toolbar.deactivate(AndroidToolbar.RUN);
    stopIndeterminate();
    androidMode.handleStop(this);
  }


  /**
   * Create a release build of the sketch and have its apk files ready.
   * If users want a debug build, they can do that from the command line.
   */
  public void handleExportProject() {
    if (handleExportCheckModified()) {
      new Thread() {
        public void run() {
//          toolbar.activate(AndroidToolbar.EXPORT);
          ((AndroidToolbar) toolbar).activateExport();
          startIndeterminate();
          statusNotice("Exporting a debug version of the sketch...");
          AndroidBuild build = new AndroidBuild(sketch, androidMode);
          try {
            File exportFolder = build.exportProject();
            if (exportFolder != null) {
              Platform.openFolder(exportFolder);
              statusNotice("Done with export.");
            }
          } catch (IOException e) {
            statusError(e);
          } catch (SketchException e) {
            statusError(e);
          }
          stopIndeterminate();
//          toolbar.deactivate(AndroidToolbar.EXPORT);
          ((AndroidToolbar)toolbar).deactivateExport();
        }
      }.start();
    }

//    try {
//      buildReleaseForExport("debug");
//    } catch (final MonitorCanceled ok) {
//      statusNotice("Canceled.");
//    } finally {
//      deactivateExport();
//    }
  }


  /**
   * Create a release build of the sketch and install its apk files on the
   * attached device.
   */
  public void handleExportPackage() {
    // Need to implement an entire signing setup first
    // http://dev.processing.org/bugs/show_bug.cgi?id=1430
    if (handleExportCheckModified()) {
//      deactivateExport();
      new KeyStoreManager(this);
    }
  }

  public void startExportPackage(final String keyStorePassword) {
    new Thread() {
      public void run() {
        startIndeterminate();
        statusNotice("Exporting signed package...");
        AndroidBuild build = new AndroidBuild(sketch, androidMode);
        try {
          File projectFolder = build.exportPackage(keyStorePassword);
          if (projectFolder != null) {
            statusNotice("Done with export.");
            Platform.openFolder(projectFolder);
          } else {
            statusError("Error with export");
          }
        } catch (IOException e) {
          statusError(e);
        } catch (SketchException e) {
          statusError(e);
        } catch (InterruptedException e) {
          e.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
        }
        stopIndeterminate();
      }
    }.start();
  }
}