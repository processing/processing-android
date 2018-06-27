/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2009-12 Ben Fry and Casey Reas

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
import processing.app.Mode;
import processing.app.Platform;
import processing.app.Settings;
import processing.app.SketchException;
import processing.app.tools.Tool;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
import processing.app.ui.EditorToolbar;
import processing.app.ui.Toolkit;
import processing.mode.java.JavaEditor;
import processing.mode.java.debug.LineID;
import processing.mode.java.preproc.PdePreprocessor;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimerTask;


@SuppressWarnings("serial")
public class AndroidEditor extends JavaEditor {
  // Component selected by default
  static public final String DEFAULT_COMPONENT = "app";  

  private JMenu androidMenu;
  
  private int appComponent;

  private AndroidDebugger debugger;
  
  private Settings settings;
  private AndroidMode androidMode;

  private List<AndroidTool> androidTools;
  
  private JCheckBoxMenuItem fragmentItem;
  private JCheckBoxMenuItem wallpaperItem;
  private JCheckBoxMenuItem watchfaceItem;
  private JCheckBoxMenuItem vrItem;
  
  protected AndroidEditor(Base base, String path, EditorState state, 
                          Mode mode) throws EditorException {
    super(base, path, state, mode);
    
    androidMode = (AndroidMode) mode;
    androidMode.resetUserSelection();
    androidMode.checkSDK(this);

    debugger = new AndroidDebugger(this, androidMode);
    super.debugger = debugger;

    androidTools = loadAndroidTools();
    addToolsToMenu();
    
    loadModeSettings();    
  }

  @Override
  public PdePreprocessor createPreprocessor(final String sketchName) {
    return new AndroidPreprocessor(sketchName);  
  }


  public EditorToolbar createToolbar() {
    return new AndroidToolbar(this, base);
  }

  
  /*
  // Not for now, it is unclear if the package name should be reset after save
  // as, i.e.: sketch_1 -> sketch_2 ... 
  @Override
  public boolean handleSaveAs() {
    boolean saved = super.handleSaveAs();
    if (saved) {
      // Reset the manifest so package name and versions are blank 
      androidMode.resetManifest(sketch, appComponent);
    }
    return saved;
  }
  */
  

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
    return buildSketchMenu(new JMenuItem[] { buildDebugMenu(), runItem, presentItem, stopItem });
  }


  public JMenu buildModeMenu() {
    androidMenu = new JMenu("Android");
    JMenuItem item;

    item = new JMenuItem("Sketch Permissions");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        new Permissions(sketch, appComponent, androidMode.getFolder());
      }
    });
    androidMenu.add(item);

    androidMenu.addSeparator();
     
    fragmentItem = new JCheckBoxMenuItem("App");
    wallpaperItem = new JCheckBoxMenuItem("Wallpaper");
    watchfaceItem = new JCheckBoxMenuItem("Watch Face");
    vrItem = new JCheckBoxMenuItem("VR");

    fragmentItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {        
        fragmentItem.setState(true);
        wallpaperItem.setState(false);
        watchfaceItem.setSelected(false);
        vrItem.setSelected(false);
        setAppComponent(AndroidBuild.APP);
      }
    });
    wallpaperItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {        
        fragmentItem.setState(false);
        wallpaperItem.setState(true);
        watchfaceItem.setSelected(false);
        vrItem.setSelected(false);
        setAppComponent(AndroidBuild.WALLPAPER);        
      }
    });
    watchfaceItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {        
        fragmentItem.setState(false);
        wallpaperItem.setState(false);
        watchfaceItem.setSelected(true);
        vrItem.setSelected(false);
        setAppComponent(AndroidBuild.WATCHFACE);        
      }
    });
    vrItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {        
        fragmentItem.setState(false);
        wallpaperItem.setState(false);
        watchfaceItem.setSelected(false);
        vrItem.setSelected(true);
        setAppComponent(AndroidBuild.VR);
      }
    });    
       
    fragmentItem.setState(false);
    wallpaperItem.setState(false);
    watchfaceItem.setSelected(false);
    vrItem.setSelected(false);

    androidMenu.add(fragmentItem);
    androidMenu.add(wallpaperItem);
    androidMenu.add(watchfaceItem);
    androidMenu.add(vrItem);
    
    androidMenu.addSeparator();

    final JMenu devicesMenu = new JMenu("Devices");

    JMenuItem noDevicesItem = new JMenuItem("No connected devices");
    noDevicesItem.setEnabled(false);
    devicesMenu.add(noDevicesItem);
    androidMenu.add(devicesMenu);
  
    // Update the device list only when the Android menu is selected.
    androidMenu.addMenuListener(new MenuListener() {
      UpdateDeviceListTask task;
      java.util.Timer timer;
      
      @Override
      public void menuSelected(MenuEvent e) {
        task = new UpdateDeviceListTask(devicesMenu);
        timer = new java.util.Timer();
        timer.schedule(task, 400, 3000);        
      }

      @Override
      public void menuDeselected(MenuEvent e) {
        timer.cancel();
      }

      @Override
      public void menuCanceled(MenuEvent e) {
        timer.cancel();
      }
    });    
    
    androidMenu.addSeparator();

    return androidMenu;
  }


  private void setAppComponent(int comp) {
    if (appComponent != comp) {
      appComponent = comp;
      
      if (appComponent == AndroidBuild.APP) {
        settings.set("component", "app");  
      } else if (appComponent == AndroidBuild.WALLPAPER) {
        settings.set("component", "wallpaper");
      } else if (appComponent == AndroidBuild.WATCHFACE) {
        settings.set("component", "watchface");
      } else if (appComponent == AndroidBuild.VR) {
        settings.set("component", "vr");
      }
      settings.save();            
      androidMode.resetManifest(sketch, appComponent);
      androidMode.showSelectComponentMessage(comp); 
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

    item = new JMenuItem("Processing for Android Site");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Platform.openURL("http://android.processing.org/");
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
  
  
  public void statusError(String what) {
    super.statusError(what);
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
        toolbar.activateRun();
        startIndeterminate();
        prepareRun();
        try {
          androidMode.handleRunEmulator(sketch, AndroidEditor.this, AndroidEditor.this);
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
    new Thread() {
      public void run() {
        toolbar.activateRun();
        startIndeterminate();
        prepareRun();
        try {
          androidMode.handleRunDevice(sketch, AndroidEditor.this, AndroidEditor.this);
        } catch (SketchException e) {
          statusError(e);
        } catch (IOException e) {
          statusError(e);
        }
        stopIndeterminate();
      }
    }.start();
  }


  public void handleStop() {
    toolbar.deactivateRun();
    stopIndeterminate();
    androidMode.handleStop(this);
  }

  @Override
  public AndroidDebugger getDebugger() {
    return debugger;
  }

  @Override
  public void toggleDebug() {
    super.toggleDebug();
    debugger.toggleDebug();
  }

  @Override
  public void toggleBreakpoint(int lineIndex) {
    debugger.toggleBreakpoint(lineIndex);
  }

  @Override
  protected LineID getCurrentLineID() {
    return super.getCurrentLineID();
  }

  /**
   * Create a release build of the sketch and have its apk files ready.
   * If users want a debug build, they can do that from the command line.
   */
  public void handleExportProject() {
    if (handleExportCheckModified()) {
      new Thread() {
        public void run() {
          ((AndroidToolbar) toolbar).activateExport();
          startIndeterminate();
          statusNotice("Exporting a debug version of the sketch...");
          AndroidBuild build = new AndroidBuild(sketch, androidMode, appComponent);
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
          ((AndroidToolbar)toolbar).deactivateExport();
        }
      }.start();
    }
  }


  /**
   * Create a release build of the sketch and install its apk files on the
   * attached device.
   */
  public void handleExportPackage() {
    if (androidMode.checkPackageName(sketch, appComponent) &&
        androidMode.checkAppIcons(sketch, appComponent) && handleExportCheckModified()) {
      new KeyStoreManager(this);
    }
  }

  
  public void startExportPackage(final String keyStorePassword) {
    new Thread() {
      public void run() {
        startIndeterminate();
        statusNotice("Exporting signed package...");
        AndroidBuild build = new AndroidBuild(sketch, androidMode, appComponent);
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
  
  
  public int getAppComponent() {
    return appComponent;
  }
  
  
  private void loadModeSettings() {
    File sketchProps = new File(sketch.getCodeFolder(), "sketch.properties");    
    try {
      settings = new Settings(sketchProps);
      boolean save = false;
      String component;
      if (!sketchProps.exists()) {
        component = DEFAULT_COMPONENT;
        settings.set("component", component);
        save = true;
      } else {
        component = settings.get("component");
        if (component == null) {
          component = DEFAULT_COMPONENT;
          settings.set("component", component);
          save = true;
        }
      }
      if (save) settings.save();
      
      if (component.equals("app")) {
        appComponent = AndroidBuild.APP;
        fragmentItem.setState(true);
      } else if (component.equals("wallpaper")) {
        appComponent = AndroidBuild.WALLPAPER;
        wallpaperItem.setState(true);
      } else if (component.equals("watchface")) {
        appComponent = AndroidBuild.WATCHFACE;
        watchfaceItem.setState(true);
      } else if (component.equals("vr")) {
        appComponent = AndroidBuild.VR;
        vrItem.setState(true);
      }  
      
      if (save) androidMode.initManifest(sketch, appComponent);
    } catch (IOException e) {
      System.err.println("While creating " + sketchProps + ": " + e.getMessage());
    }
  }
  
  private List<AndroidTool> loadAndroidTools() {
    // This gets called before assigning mode to androidMode...
    ArrayList<AndroidTool> outgoing = new ArrayList<AndroidTool>();
    File toolPath = new File(androidMode.getFolder(), "tools/SDKUpdater");
    AndroidTool tool = null;
    try {
      tool = new AndroidTool(toolPath, androidMode.getSDK());
      tool.init(base);
      outgoing.add(tool);
    } catch (Throwable e) {
      e.printStackTrace();
    }     
    Collections.sort(outgoing);
    return outgoing;
  }
  
  private void addToolsToMenu() {
    JMenuItem item;
    
    for (final Tool tool : androidTools) {
      item = new JMenuItem(tool.getMenuTitle());
      item.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          tool.run();
        }
      });
      androidMenu.add(item);      
    }

//    item = new JMenuItem("AVD Manager");
//    item.addActionListener(new ActionListener() {
//      public void actionPerformed(ActionEvent e) {
//        File file = androidMode.getSDK().getAndroidTool();
//        PApplet.exec(new String[] { file.getAbsolutePath(), "avd" });
//      }
//    });
//    menu.add(item);

    item = new JMenuItem("Reset ADB");
    item.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
//        editor.statusNotice("Resetting the Android Debug Bridge server.");
        final Devices devices = Devices.getInstance();
        devices.killAdbServer();
        devices.startAdbServer();
      }
    });
    androidMenu.add(item);
  }
  
  
  class UpdateDeviceListTask extends TimerTask {

    private JMenu deviceMenu;

    public UpdateDeviceListTask(JMenu deviceMenu) {
      this.deviceMenu = deviceMenu;
    }
    
    private Device selectFirstDevice(java.util.List<Device> deviceList) {
      if (0 < deviceList.size()) return deviceList.get(0);
      return null;
    }

    @Override
    public void run() {
      if (androidMode == null || androidMode.getSDK() == null) return;

      final Devices devices = Devices.getInstance();
      
      if (appComponent == AndroidBuild.WATCHFACE) {        
        devices.enableBluetoothDebugging();
      }
      
      java.util.List<Device> deviceList = devices.findMultiple(false);
      Device selectedDevice = devices.getSelectedDevice();

      if (deviceList.size() == 0) {
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
}