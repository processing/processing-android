/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2011-12 Ben Fry and Casey Reas

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
import processing.app.Library;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.RunnerListener;
import processing.app.Sketch;
import processing.app.SketchException;
import processing.app.Util;
import processing.app.ui.Editor;
import processing.app.ui.EditorException;
import processing.app.ui.EditorState;
import processing.core.PApplet;
import processing.mode.android.AndroidSDK.CancelException;
import processing.mode.java.JavaMode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;


public class AndroidMode extends JavaMode {
  private AndroidSDK sdk;
  private File coreZipLocation;
  private AndroidRunner runner;
  
  private boolean showBluetoothDebugMessage = true;
  private boolean showWallpaperSelectMessage = true;
  
  private boolean checkingSDK = false;
  private boolean userCancelledSDKSearch = false;

  private static final String BLUETOOTH_DEBUG_URL = 
      "http://developer.android.com/training/wearables/apps/bt-debugging.html";
  
  private static final String WATCHFACE_DEBUG_TITLE =
      "Is Debugging over Bluetooth enabled?";
  
  private static final String WATCHFACE_DEBUG_MESSAGE =
      "Processing will access the smartwatch through the phone " +
      "currently paired to it. Your watch won't show up in the device list, " +
      "select the phone instead.<br><br>" +
      "Make sure to enable <a href=\"" + BLUETOOTH_DEBUG_URL + "\">debugging over bluetooth</a> " +
      "for this to work.";
  
  private static final String WALLPAPER_INSTALL_TITLE =
      "Wallpaper installed!";
  
  private static final String WALLPAPER_INSTALL_MESSAGE = 
      "Processing just built and installed your sketch as a " +
      "live wallpaper on the selected device.<br><br>" +
      "You need to open the wallpaper picker in the device in order "+ 
      "to select it as the new background.";
  
  private static final String DISTRIBUTING_APPS_TUT_URL = 
      "http://android.processing.org/tutorials/distributing/index.html";  
  
  private static final String EXPORT_DEFAULT_PACKAGE_TITLE =
      "Cannot export package...";

  private static final String EXPORT_DEFAULT_PACKAGE_MESSAGE =
      "The sketch still has the default package name. " +
      "Not good, since this name will uniquely identify your app on the Play store... for ever!<br>" +
      "Come up with a different package name and write in the AndroidManifest.xml file in the sketch folder, " +
      "after the \"package=\" attribute inside the manifest tag, which also contains version code and name. " +
      "Once you have done that, try exporting the sketch again.<br><br>" +
      "For more info on distributing apps from Processing,<br>" +
      "check <a href=\"" + DISTRIBUTING_APPS_TUT_URL + "\">this online tutorial</a>.";
  
  private static final String EXPORT_DEFAULT_ICONS_TITLE =
      "Cannot export package...";

  private static final String EXPORT_DEFAULT_ICONS_MESSAGE =
      "The sketch does not include all required app icons. " +
      "Processing could use its default set of Android icons, which are okay " +
      "to test the app on your device, but a bad idea to distribute it on the Play store. " +
      "Create a full set of unique icons for your app, and copy them into the sketch folder. " +
      "Once you have done that, try exporting the sketch again.<br><br>" +
      "For more info on distributing apps from Processing,<br>" +
      "check <a href=\"" + DISTRIBUTING_APPS_TUT_URL + "\">this online tutorial</a>.";  
    
  
  public AndroidMode(Base base, File folder) {
    super(base, folder);
  }


  @Override
  public Editor createEditor(Base base, String path,
                             EditorState state) throws EditorException {
    return new AndroidEditor(base, path, state, this);
  }


  @Override
  public String getTitle() {
    return "Android";
  }


  public File[] getKeywordFiles() {
    return new File[] {
      Platform.getContentFile("modes/java/keywords.txt")
    };
  }


  public File[] getExampleCategoryFolders() {
    return new File[] {
      new File(examplesFolder, "Basics"),
      new File(examplesFolder, "Topics"),
      new File(examplesFolder, "Demos"),
      new File(examplesFolder, "Sensors")
    };
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /** @return null so that it doesn't try to pass along the desktop version of core.jar */
  public Library getCoreLibrary() {
    return null;
  }


  protected File getCoreZipLocation() {
    if (coreZipLocation == null) {
      /*
      // for debugging only, check to see if this is an svn checkout
      File debugFile = new File("../../../android/core.zip");
      if (!debugFile.exists() && Base.isMacOS()) {
        // current path might be inside Processing.app, so need to go much higher
        debugFile = new File("../../../../../../../android/core.zip");
      }
      if (debugFile.exists()) {
        System.out.println("Using version of core.zip from local SVN checkout.");
//        return debugFile;
        coreZipLocation = debugFile;
      }
      */

      // otherwise do the usual
      //    return new File(base.getSketchbookFolder(), ANDROID_CORE_FILENAME);
      coreZipLocation = getContentFile("processing-core.zip");
    }
    return coreZipLocation;
  }


//  public AndroidSDK loadSDK() throws BadSDKException, IOException {
//    if (sdk == null) {
//      sdk = AndroidSDK.load();
//    }
//    return sdk;
//  }

  public void loadSDK() {
    try {
      sdk = AndroidSDK.load();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  
  public void resetUserSelection() {
    userCancelledSDKSearch = false;
  }
  
  
  public void checkSDK(Editor editor) {    
    if (checkingSDK) {
      // Some other thread has invoked SDK checking, so wait until the first one
      // is done (it might involve downloading the SDK, etc).
      while (checkingSDK) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) { 
          return;
        }      
      }
    }
    if (userCancelledSDKSearch) return;
    checkingSDK = true;
    Throwable tr = null;
    if (sdk == null) {      
      try {
        sdk = AndroidSDK.load();
        if (sdk == null) {
          sdk = AndroidSDK.locate(editor, this);
        }
      } catch (CancelException cancel) {
        userCancelledSDKSearch = true;
        tr = cancel;
      } catch (Exception other) {
        tr = other;
      }
    }
    if (sdk == null) {
      Messages.showWarning("Bad news...",
                           "The Android SDK could not be loaded.\n" +
                           "The Android Mode will be disabled.", tr);
    }
    checkingSDK = false;
  }


  public AndroidSDK getSDK() {
    return sdk;
  }
  
  
  @Override
  public String getSearchPath() {
    if (sdk == null) {
        checkSDK(null);
    }

    if (sdk == null) {
      Messages.log("Android SDK path couldn't be loaded.");
      return "";
    }
    
    String coreJarPath = new File(getFolder(), "processing-core.zip").getAbsolutePath();
    return sdk.getAndroidJarPath().getAbsolutePath() + File.pathSeparatorChar + coreJarPath;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd.HHmm");


  static public String getDateStamp() {
    return dateFormat.format(new Date());
  }


  static public String getDateStamp(long stamp) {
    return dateFormat.format(new Date(stamp));
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  public void handleRun(Sketch sketch, RunnerListener listener) throws SketchException {
//    JavaBuild build = new JavaBuild(sketch);
//    String appletClassName = build.build();
//    if (appletClassName != null) {
//      runtime = new Runner(build, listener);
//      runtime.launch(false);
//    }
//  }
  public void handleRunEmulator(Sketch sketch, AndroidEditor editor, 
      RunnerListener listener) throws SketchException, IOException {
    listener.startIndeterminate();
    listener.statusNotice("Starting build...");
    AndroidBuild build = new AndroidBuild(sketch, this, 
        editor.getAppComponent(), true);

    listener.statusNotice("Building Android project...");
    build.build("debug");
        
    boolean avd = AVD.ensureProperAVD(editor, this, sdk, build.isWear());
    if (!avd) {
      SketchException se =
        new SketchException("Could not create a virtual device for the emulator.");
      se.hideStackTrace();
      throw se;
    }

    listener.statusNotice("Running sketch on emulator...");
    runner = new AndroidRunner(build, listener);
    runner.launch(Devices.getInstance().getEmulator(build.isWear(), build.usesOpenGL()), 
        true, build.isWear());
  }


  public void handleRunDevice(Sketch sketch, AndroidEditor editor, 
      RunnerListener listener)
    throws SketchException, IOException {    
    
    final Devices devices = Devices.getInstance();
    java.util.List<Device> deviceList = devices.findMultiple(false);
    if (deviceList.size() == 0) {
      Messages.showWarning("No devices found!", 
                           "Processing did not find any device where to run\n" +
                           "your sketch on. Make sure that your handheld or\n" +
                           "wearable is properly connected to the computer\n" +
                           "and that USB or Bluetooth debugging is enabled.");
      listener.statusError("No devices found.");
      return;
    }
    
    listener.startIndeterminate();
    listener.statusNotice("Starting build...");
    AndroidBuild build = new AndroidBuild(sketch, this, 
        editor.getAppComponent(), false);

    listener.statusNotice("Building Android project...");
    File projectFolder = build.build("debug");
    if (projectFolder == null) {
      listener.statusError("Build failed.");      
      return;
    }
    
    listener.statusNotice("Running sketch on device...");
    runner = new AndroidRunner(build, listener);
    runner.launch(Devices.getInstance().getHardware(), false, build.isWear());
    
    showPostBuildMessage(build.getAppComponent());
  }

  
  public void showSelectComponentMessage(int appComp) {
    if (showBluetoothDebugMessage && appComp == AndroidBuild.WATCHFACE) {
      showMessage(WATCHFACE_DEBUG_TITLE, WATCHFACE_DEBUG_MESSAGE);
      showBluetoothDebugMessage = false;
    } 
  }
  
  
  public void showPostBuildMessage(int appComp) {
    if (showWallpaperSelectMessage && appComp == AndroidBuild.WALLPAPER) {
      showMessage(WALLPAPER_INSTALL_TITLE, WALLPAPER_INSTALL_MESSAGE);  
      showWallpaperSelectMessage = false;
    }    
  }
  
  
  public void handleStop(RunnerListener listener) {
    listener.statusNotice("");
    listener.stopIndeterminate();

//    if (runtime != null) {
//      runtime.close();  // kills the window
//      runtime = null; // will this help?
//    }
    if (runner != null) {
      runner.close();
      runner = null;
    }
  }

  
  public boolean checkPackageName(Sketch sketch, int comp) {
    Manifest manifest = new Manifest(sketch, comp, getFolder(), false);
    String defName = AndroidBuild.basePackage + "." + sketch.getName().toLowerCase();    
    String name = manifest.getPackageName();
    if (name.toLowerCase().equals(defName.toLowerCase())) {
      // The user did not set the package name, show error and stop
      AndroidMode.showMessage(EXPORT_DEFAULT_PACKAGE_TITLE, EXPORT_DEFAULT_PACKAGE_MESSAGE);
      return false;
    }
    return true;
  }
  
  
  public void initManifest(Sketch sketch, int comp) {
    new Manifest(sketch, comp, getFolder(), false);
  }  
  
  
  public void resetManifest(Sketch sketch, int comp) {
    new Manifest(sketch, comp, getFolder(), true);
  }
  

  // Utils
  
  static public void writeFile(final File file, String[] lines) {
    final PrintWriter writer = PApplet.createWriter(file);
    for (String line: lines) writer.println(line);
    writer.flush();
    writer.close();
  }
  
  
  static public File mkdirs(final File parent, final String name) throws SketchException {
    final File result = new File(parent, name);
    if (!(result.exists() || result.mkdirs())) {
      throw new SketchException("Could not create " + result);
    }
    return result;
  }
  
  
  static public boolean checkAppIcons(Sketch sketch) {
    File sketchFolder = sketch.getFolder();
    File localIcon36 = new File(sketchFolder, AndroidBuild.ICON_36);
    File localIcon48 = new File(sketchFolder, AndroidBuild.ICON_48);
    File localIcon72 = new File(sketchFolder, AndroidBuild.ICON_72);
    File localIcon96 = new File(sketchFolder, AndroidBuild.ICON_96);
    File localIcon144 = new File(sketchFolder, AndroidBuild.ICON_144);
    File localIcon192 = new File(sketchFolder, AndroidBuild.ICON_192);    
    boolean allExist = localIcon36.exists() &&
                       localIcon48.exists() &&
                       localIcon72.exists() &&
                       localIcon96.exists() &&
                       localIcon144.exists() &&
                       localIcon192.exists();    
    if (!allExist) {
      // The user did not set custom icons, show error and stop
      AndroidMode.showMessage(EXPORT_DEFAULT_ICONS_TITLE, EXPORT_DEFAULT_ICONS_MESSAGE);
      return false;      
    }
    return true;
  }
  
  
  static public void createFileFromTemplate(final File tmplFile, final File destFile) {
    createFileFromTemplate(tmplFile, destFile, null);
  }
  
  
  static public void createFileFromTemplate(final File tmplFile, final File destFile, 
      final HashMap<String, String> replaceMap) {
    PrintWriter pw = PApplet.createWriter(destFile);    
    String lines[] = PApplet.loadStrings(tmplFile);
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].indexOf("@@") != -1 && replaceMap != null) {
        StringBuilder sb = new StringBuilder(lines[i]);
        int index = 0;
        for (String key: replaceMap.keySet()) {
          String val = replaceMap.get(key);
          while ((index = sb.indexOf(key)) != -1) {
            sb.replace(index, index + key.length(), val);
          }          
        }    
        lines[i] = sb.toString();
      }
      // explicit newlines to avoid Windows CRLF
      pw.print(lines[i] + "\n");
    }
    pw.flush();
    pw.close();    
  }    
  
  static public void extractFolder(File file, File newPath, 
    boolean setExec) throws IOException {
    extractFolder(file, newPath, setExec, false);
  }
  
  static public void extractFolder(File file, File newPath, 
    boolean setExec, boolean remRoot) throws IOException {
    int BUFFER = 2048;
    ZipFile zip = new ZipFile(file);
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {
      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();
      String currentEntry = entry.getName();
      
      if (remRoot) {
        // Remove root folder from path
        int idx = currentEntry.indexOf(File.separator); 
        currentEntry = currentEntry.substring(idx + 1);
      }
      
      File destFile = new File(newPath, currentEntry);
      //destFile = new File(newPath, destFile.getName());
      File destinationParent = destFile.getParentFile();

      // create the parent directory structure if needed
      destinationParent.mkdirs();

      String ext = PApplet.getExtension(currentEntry);
      if (setExec && ext.equals("unknown")) {        
        // On some OS X machines the android binaries lose their executable
        // attribute, rendering the mode unusable
        destFile.setExecutable(true);
      }
      
      if (!entry.isDirectory()) {
        // should preserve permissions
        // https://bitbucket.org/atlassian/amps/pull-requests/21/amps-904-preserve-executable-file-status/diff
        BufferedInputStream is = new BufferedInputStream(zip
            .getInputStream(entry));
        int currentByte;
        // establish buffer for writing file
        byte data[] = new byte[BUFFER];

        // write the current file to disk
        FileOutputStream fos = new FileOutputStream(destFile);
        BufferedOutputStream dest = new BufferedOutputStream(fos,
            BUFFER);

        // read and write until last byte is encountered
        while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
          dest.write(data, 0, currentByte);
        }
        dest.flush();
        dest.close();
        is.close();
      }
    }
    zip.close();
  }

  
  static public void extractClassesJarFromAar(File wearFile, File explodeDir, 
      File jarFile) throws IOException {
    extractClassesJarFromAar(wearFile, explodeDir, jarFile, true);
  }
  
  
  static public void extractClassesJarFromAar(File wearFile, File explodeDir, 
      File jarFile, boolean removeDir) throws IOException {
    extractFolder(wearFile, explodeDir, false);
    File classFile = new File(explodeDir, "classes.jar");
    Util.copyFile(classFile, jarFile);
    Util.removeDir(explodeDir);
  }
  

  static public void showMessage(String title, String text) {
    if (title == null) title = "Message";
    if (Base.isCommandLine()) {      
      System.out.println(title + ": " + text);
    } else {
      String htmlString = "<html> " +
          "<head> <style type=\"text/css\">"+
          "p { font: 11pt \"Lucida Grande\"; margin-top: 8px; width: 300px }"+
          "</style> </head>" +
          "<body> <p>" + text + "</p> </body> </html>";      
      JEditorPane pane = new JEditorPane("text/html", htmlString);
      pane.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
            Platform.openURL(e.getURL().toString());
          }
        }
      });
      pane.setEditable(false);
      JLabel label = new JLabel();
      pane.setBackground(label.getBackground());      
      JOptionPane.showMessageDialog(null, pane, title, 
          JOptionPane.INFORMATION_MESSAGE);
    }
  }   
}