/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-16 The Processing Foundation
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class AndroidMode extends JavaMode {
  private AndroidSDK sdk;
  private File coreZipLocation;
  private AndroidRunner runner;
  
  private boolean checkingSDK = false;
  private boolean userCancelledSDKSearch = false;

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
      coreZipLocation = getContentFile("android-core.zip");
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

    String path = sdk.getSdkFolder().getAbsolutePath() + File.separator + 
                  "platforms" + File.separator + "android-";
    String level = AndroidBuild.target_api_level;
    String name = AndroidBuild.target_sdk_version;
    String androidJarPath = path + level + File.separator + "android.jar";    
    if (!new File(androidJarPath).exists()) {
      // Try again using SDK name, I have seen the SDK stored as platforms/android-x.y.z
      androidJarPath = path + name + File.separator + "android.jar";
      if (!new File(androidJarPath).exists()) {
        Messages.log("Android SDK path couldn't be loaded.");
        return "";        
      }
    }
    
    String processingAndroidCoreJarPath = new File(getFolder(), "android-core.zip").getAbsolutePath();
    return androidJarPath + File.pathSeparatorChar + processingAndroidCoreJarPath;
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
  public void handleRunEmulator(Sketch sketch, RunnerListener listener) throws SketchException, IOException {
    listener.startIndeterminate();
    listener.statusNotice("Starting build...");
    AndroidBuild build = new AndroidBuild(sketch, this);

    listener.statusNotice("Building Android project...");
    build.build("debug");

    boolean avd = AVD.ensureProperAVD(sdk);
    if (!avd) {
      SketchException se =
        new SketchException("Could not create a virtual device for the emulator.");
      se.hideStackTrace();
      throw se;
    }

    listener.statusNotice("Running sketch on emulator...");
    runner = new AndroidRunner(build, listener);
    runner.launch(Devices.getInstance().getEmulator());
  }


  public void handleRunDevice(Sketch sketch, RunnerListener listener)
    throws SketchException, IOException {
//    JavaBuild build = new JavaBuild(sketch);
//    String appletClassName = build.build();
//    if (appletClassName != null) {
//      runtime = new Runner(build, listener);
//      runtime.launch(true);
//    }

//    try {
//      runSketchOnDevice(Environment.getInstance().getHardware(), "debug", this);
//    } catch (final MonitorCanceled ok) {
//      sketchStopped();
//      statusNotice("Canceled.");
//    }
    listener.startIndeterminate();
    listener.statusNotice("Starting build...");
    AndroidBuild build = new AndroidBuild(sketch, this);

    listener.statusNotice("Building Android project...");
    build.build("debug");

    listener.statusNotice("Running sketch on device...");
    runner = new AndroidRunner(build, listener);
    runner.launch(Devices.getInstance().getHardware());
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

  public static void extractFolder(File file, File newPath, boolean setExec) throws IOException {
    int BUFFER = 2048;
    ZipFile zip = new ZipFile(file);
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {
      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();
      String currentEntry = entry.getName();
      File destFile = new File(newPath, currentEntry);
      //destFile = new File(newPath, destFile.getName());
      File destinationParent = destFile.getParentFile();

      // create the parent directory structure if needed
      destinationParent.mkdirs();

      String ext = PApplet.getExtension(currentEntry);
      if (setExec && ext.equals("unknown")) {        
        // On some OS X machines the android binaries loose their executable
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
//  public void handleExport(Sketch sketch, )


  /*
  protected void buildReleaseForExport(Sketch sketch, String target) throws MonitorCanceled {
//    final IndeterminateProgressMonitor monitor =
//      new IndeterminateProgressMonitor(this,
//                                       "Building and exporting...",
//                                       "Creating project...");
    try {
      AndroidBuild build = new AndroidBuild(sketch, sdk);
      File tempFolder = null;
      try {
        tempFolder = build.createProject(target, getCoreZipLocation());
        if (tempFolder == null) {
          return;
        }
      } catch (IOException e) {
        e.printStackTrace();
      } catch (SketchException se) {
        se.printStackTrace();
      }
      try {
        if (monitor.isCanceled()) {
          throw new MonitorCanceled();
        }
        monitor.setNote("Building release version...");
//        if (!build.antBuild("release")) {
//          return;
//        }

        if (monitor.isCanceled()) {
          throw new MonitorCanceled();
        }

        // If things built successfully, copy the contents to the export folder
        File exportFolder = build.createExportFolder();
        if (exportFolder != null) {
          Base.copyDir(tempFolder, exportFolder);
          listener.statusNotice("Done with export.");
          Base.openFolder(exportFolder);
        } else {
          listener.statusError("Could not copy files to export folder.");
        }
      } catch (IOException e) {
        listener.statusError(e);

      } finally {
        build.cleanup();
      }
    } finally {
      monitor.close();
    }
  }


  @SuppressWarnings("serial")
  private static class MonitorCanceled extends Exception {
  }
  */
}