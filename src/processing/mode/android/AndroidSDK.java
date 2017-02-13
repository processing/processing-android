/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-16 The Processing Foundation

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

import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;


class AndroidSDK {
  public static boolean adbDisabled = false;
  
  private final File folder;
  private final File tools;
  private final File platforms;
  private final File targetPlatform;
  private final File androidJar;
  private final File platformTools;
  private final File buildTools;
  private final File androidTool;

  static final String DOWNLOAD_URL ="https://developer.android.com/studio/index.html#downloads";
  
  private static final String ANDROID_SDK_PRIMARY =
    "Is the Android SDK installed?";

  private static final String ANDROID_SDK_SECONDARY =
      "The Android SDK does not appear to be installed, <br>" +
      "because the ANDROID_SDK variable is not set. <br>" +
      "If it is installed, click “Locate SDK path” to select the <br>" +
      "location of the SDK, or “Download SDK” to let <br>" +
      "Processing download the SDK automatically.<br><br>" +
      "If you want to download the SDK manually, you can get <br>"+
      "the command line tools from <a href=\"" + DOWNLOAD_URL + "\">here</a>. Make sure to install<br>" +
      "the SDK platform for API " + AndroidBuild.target_sdk + ".";
    
  private static final String ANDROID_SYS_IMAGE_PRIMARY =
      "Download emulator?";

  private static final String ANDROID_SYS_IMAGE_SECONDARY =
      "The emulator does not appear to be installed, <br>" +
      "Do you want Processing to download and install it now? <br>" +
      "Otherwise, you will need to do it through SDK manager.";

  private static final String ANDROID_SYS_IMAGE_WEAR_PRIMARY =
      "Download watch emulator?";

  private static final String ANDROID_SYS_IMAGE_WEAR_SECONDARY =
      "The watch emulator does not appear to be installed, <br>" +
      "Do you want Processing to download and install it now? <br>" +
      "Otherwise, you will need to do it through SDK manager.";    
    
  private static final String SELECT_ANDROID_SDK_FOLDER =
    "Choose the location of the Android SDK";

  public AndroidSDK(File folder) throws BadSDKException, IOException {
    this.folder = folder;
    if (!folder.exists()) {
      throw new BadSDKException(folder + " does not exist");
    }

    tools = new File(folder, "tools");
    if (!tools.exists()) {
      throw new BadSDKException("There is no tools folder in " + folder);
    }

    platformTools = new File(folder, "platform-tools");
    if (!platformTools.exists()) {
      throw new BadSDKException("There is no platform-tools folder in " + folder);
    }

    buildTools = new File(folder, "build-tools");
    if (!buildTools.exists()) {
      throw new BadSDKException("There is no build-tools folder in " + folder);
    }
    
    platforms = new File(folder, "platforms");
    if (!platforms.exists()) {
      throw new BadSDKException("There is no platforms folder in " + folder);
    }
    
    targetPlatform = new File(platforms, AndroidBuild.target_platform);
    if (!targetPlatform.exists()) {
      throw new BadSDKException("There is no Android " + 
                                AndroidBuild.target_sdk + " in " + platforms.getAbsolutePath());
    }

    androidJar = new File(targetPlatform, "android.jar");
    if (!androidJar.exists()) {
      throw new BadSDKException("android.jar for plaform " + 
                                AndroidBuild.target_sdk + " is missing from " + targetPlatform.getAbsolutePath());
    }
    
    androidTool = findAndroidTool(tools);

    String path = Platform.getenv("PATH");

    Platform.setenv("ANDROID_SDK", folder.getCanonicalPath());
    path = platformTools.getCanonicalPath() + File.pathSeparator +
      tools.getCanonicalPath() + File.pathSeparator + path;

    String javaHomeProp = System.getProperty("java.home");
    File javaHome = new File(javaHomeProp).getCanonicalFile();
    Platform.setenv("JAVA_HOME", javaHome.getCanonicalPath());

    path = new File(javaHome, "bin").getCanonicalPath() + File.pathSeparator + path;
    Platform.setenv("PATH", path);

    checkDebugCertificate();
  }


  /**
   * If a debug certificate exists, check its expiration date. If it's expired,
   * remove it so that it doesn't cause problems during the build.
   */
  protected void checkDebugCertificate() {
    File dotAndroidFolder = new File(System.getProperty("user.home"), ".android");
    File keystoreFile = new File(dotAndroidFolder, "debug.keystore");
    if (keystoreFile.exists()) {
      // keytool -list -v -storepass android -keystore debug.keystore
      ProcessHelper ph = new ProcessHelper(new String[] {
        "keytool", "-list", "-v",
        "-storepass", "android",
        "-keystore", keystoreFile.getAbsolutePath()
      });
      try {
        ProcessResult result = ph.execute();
        if (result.succeeded()) {
          // Valid from: Mon Nov 02 15:38:52 EST 2009 until: Tue Nov 02 16:38:52 EDT 2010
          String[] lines = PApplet.split(result.getStdout(), '\n');
          for (String line : lines) {
            String[] m = PApplet.match(line, "Valid from: .* until: (.*)");
            if (m != null) {
              String timestamp = m[1].trim();
              // "Sun Jan 22 11:09:08 EST 2012"
              // Hilariously, this is the format of Date.toString(), however
              // it isn't the default for SimpleDateFormat or others. Yay!
              DateFormat df = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
              try {
                Date date = df.parse(timestamp);
                long expireMillis = date.getTime();
                if (expireMillis < System.currentTimeMillis()) {
                  System.out.println("Removing expired debug.keystore file.");
                  String hidingName = "debug.keystore." + AndroidMode.getDateStamp(expireMillis);
                  File hidingFile = new File(keystoreFile.getParent(), hidingName);
                  if (!keystoreFile.renameTo(hidingFile)) {
                    System.err.println("Could not remove the expired debug.keystore file.");
                    System.err.println("Please remove the file " + keystoreFile.getAbsolutePath());
                  }
//                } else {
//                  System.out.println("Nah, that won't expire until " + date); //timestamp);
                }
              } catch (ParseException pe) {
                System.err.println("The date “" + timestamp + "” could not be parsed.");
                System.err.println("Please report this as a bug so we can fix it.");
              }
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  public File getAndroidTool() {
    return androidTool;
  }


  public String getAndroidToolPath() {
    return androidTool.getAbsolutePath();
  }


  public File getSdkFolder() {
    return folder;
  }


  public File getAndroidJarPath() {
    return androidJar;  
  }
  
  
  public File getBuildToolsFolder() {
    return buildTools;
  }
  
  
  public File getPlatformToolsFolder() {
    return platformTools;
  }


  /**
   * Checks a path to see if there's a tools/android file inside, a rough check
   * for the SDK installation. Also figures out the name of android/android.bat
   * so that it can be called explicitly.
   */
  private static File findAndroidTool(final File tools) throws BadSDKException {
    if (new File(tools, "android.exe").exists()) {
      return new File(tools, "android.exe");
    }
    if (new File(tools, "android.bat").exists()) {
      return new File(tools, "android.bat");
    }
    if (new File(tools, "android").exists()) {
      return new File(tools, "android");
    }
    throw new BadSDKException("Cannot find the android tool in " + tools);
  }


  /**
   * Check for the ANDROID_SDK environment variable. If the variable is set,
   * and refers to a legitimate Android SDK, then use that and save the pref.
   *
   * Check for a previously set android.sdk.path preference. If the pref
   * is set, and refers to a legitimate Android SDK, then use that.
   *
   * Prompt the user to select an Android SDK. If the user selects a
   * legitimate Android SDK, then use that, and save the preference.
   *
   * @return an AndroidSDK
   * @throws BadSDKException
   * @throws IOException
   */
  public static AndroidSDK load() throws IOException {
    // The environment variable is king. The preferences.txt entry is a page.
    final String sdkEnvPath = Platform.getenv("ANDROID_SDK");
    if (sdkEnvPath != null) {
      try {
        final AndroidSDK androidSDK = new AndroidSDK(new File(sdkEnvPath));
        // Set this value in preferences.txt, in case ANDROID_SDK
        // gets knocked out later. For instance, by that pesky Eclipse,
        // which nukes all env variables when launching from the IDE.
        Preferences.set("android.sdk.path", sdkEnvPath);
        return androidSDK;
      } catch (final BadSDKException drop) { }
    }

    // If android.sdk.path exists as a preference, make sure that the folder
    // is not bogus, otherwise the SDK may have been removed or deleted.
    final String sdkPrefsPath = Preferences.get("android.sdk.path");
    if (sdkPrefsPath != null) {
      try {
        final AndroidSDK androidSDK = new AndroidSDK(new File(sdkPrefsPath));
        // Set this value in preferences.txt, in case ANDROID_SDK
        // gets knocked out later. For instance, by that pesky Eclipse,
        // which nukes all env variables when launching from the IDE.
        Preferences.set("android.sdk.path", sdkPrefsPath);
        return androidSDK;
      } catch (final BadSDKException wellThatsThat) {
        Preferences.unset("android.sdk.path");
      }
    }
    return null;
  }


  static public AndroidSDK locate(final Frame window, final AndroidMode androidMode)
      throws BadSDKException, CancelException, IOException {
    final int result = showLocateDialog(window);
    if (result == JOptionPane.YES_OPTION) {
      return download(window, androidMode);
    } else if (result == JOptionPane.NO_OPTION) {
      // user will manually select folder containing SDK folder
      File folder = selectFolder(SELECT_ANDROID_SDK_FOLDER, null, window);
      if (folder == null) {
        throw new CancelException("User canceled attempt to find SDK"); 
      } else {
        final AndroidSDK androidSDK = new AndroidSDK(folder);
        Preferences.set("android.sdk.path", folder.getAbsolutePath());
        return androidSDK;
      }
    } else {
      throw new CancelException("User canceled attempt to find SDK"); 
    }
  }
  
  static public boolean locateSysImage(final Frame window, 
      final AndroidMode androidMode, boolean wear)
      throws BadSDKException, CancelException, IOException {
    final int result = showDownloadSysImageDialog(window, wear);
    if (result == JOptionPane.YES_OPTION) {
      return downloadSysImage(window, androidMode, wear);
    } else if (result == JOptionPane.NO_OPTION) {
      return false;
    } else {
      return false; 
    }
  }

  static public AndroidSDK download(final Frame editor, final AndroidMode androidMode) 
      throws BadSDKException, CancelException {
    final SDKDownloader downloader = new SDKDownloader(editor, androidMode);    
    downloader.run(); // This call blocks until the SDK download complete, or user cancels.
    
    if (downloader.cancelled()) {
      throw new CancelException("User canceled SDK download");  
    } 
    AndroidSDK sdk = downloader.getSDK();
    if (sdk == null) {
      throw new BadSDKException("SDK could not be downloaded");
    }
    return sdk;
  }
  
  static public boolean downloadSysImage(final Frame editor, 
      final AndroidMode androidMode, final boolean wear) 
      throws BadSDKException, CancelException {
    final SysImageDownloader downloader = new SysImageDownloader(editor, androidMode, wear);    
    downloader.run(); // This call blocks until the SDK download complete, or user cancels.
    
    if (downloader.cancelled()) {
      throw new CancelException("User canceled emulator download");  
    } 
    boolean res = downloader.getResult();
    if (!res) {
      throw new BadSDKException("Emulator could not be downloaded");
    }
    return res;
  }

  static public int showLocateDialog(Frame editor) {
    // Pane formatting adapted from the Quaqua guide
    // http://www.randelshofer.ch/quaqua/guide/joptionpane.html
    JOptionPane pane =
        new JOptionPane("<html> " +
            "<head> <style type=\"text/css\">"+
            "b { font: 13pt \"Lucida Grande\" }"+
            "p { font: 11pt \"Lucida Grande\"; margin-top: 8px; width: 300px }"+
            "</style> </head>" +
            "<b>" + ANDROID_SDK_PRIMARY + "</b>" +
            "<p>" + ANDROID_SDK_SECONDARY + "</p>",
            JOptionPane.QUESTION_MESSAGE);

    String[] options = new String[] {
        "Download SDK automatically", "Locate SDK path manually"
    };
    pane.setOptions(options);

    // highlight the safest option ala apple hig
    pane.setInitialValue(options[0]);

    JDialog dialog = pane.createDialog(editor, null);
    dialog.setTitle("");
    dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);  
    dialog.setVisible(true);

    Object result = pane.getValue();
    if (result == options[0]) {
      return JOptionPane.YES_OPTION;
    } else if (result == options[1]) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }
  
  
  static public int showDownloadSysImageDialog(Frame editor, boolean wear) {
    String msg1 = wear ? ANDROID_SYS_IMAGE_WEAR_PRIMARY : ANDROID_SYS_IMAGE_PRIMARY;
    String msg2 = wear ? ANDROID_SYS_IMAGE_WEAR_SECONDARY : ANDROID_SYS_IMAGE_SECONDARY;
    
    JOptionPane pane =
        new JOptionPane("<html> " +
            "<head> <style type=\"text/css\">"+
            "b { font: 13pt \"Lucida Grande\" }"+
            "p { font: 11pt \"Lucida Grande\"; margin-top: 8px; width: 300px }"+
            "</style> </head>" +
            "<b>" + msg1 + "</b>" +
            "<p>" + msg2 + "</p>",
            JOptionPane.QUESTION_MESSAGE);

    String[] options = new String[] { "Yes", "No" };
    pane.setOptions(options);

    // highlight the safest option ala apple hig
    pane.setInitialValue(options[0]);

    JDialog dialog = pane.createDialog(editor, null);
    dialog.setTitle("");
    dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);  
    dialog.setVisible(true);

    Object result = pane.getValue();
    if (result == options[0]) {
      return JOptionPane.YES_OPTION;
    } else if (result == options[1]) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }
  

  // this was banished from Base because it encourages bad practice.
  // TODO figure out a better way to handle the above.
  static public File selectFolder(String prompt, File folder, Frame frame) {
    if (Platform.isMacOS()) {
      if (frame == null) frame = new Frame(); //.pack();
      FileDialog fd = new FileDialog(frame, prompt, FileDialog.LOAD);
      if (folder != null) {
        fd.setDirectory(folder.getParent());
        //fd.setFile(folder.getName());
      }
      System.setProperty("apple.awt.fileDialogForDirectories", "true");
      fd.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);  
      fd.setVisible(true);
      System.setProperty("apple.awt.fileDialogForDirectories", "false");
      if (fd.getFile() == null) {
        return null;
      }
      return new File(fd.getDirectory(), fd.getFile());

    } else {
      JFileChooser fc = new JFileChooser();
      fc.setDialogTitle(prompt);
      if (folder != null) {
        fc.setSelectedFile(folder);
      }
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

      int returned = fc.showOpenDialog(frame);
      if (returned == JFileChooser.APPROVE_OPTION) {
        return fc.getSelectedFile();
      }
    }
    return null;
  }


  private static final String ADB_DAEMON_MSG_1 = "daemon not running";
  private static final String ADB_DAEMON_MSG_2 = "daemon started successfully";
    
  public static ProcessResult runADB(final String... cmd)
    throws InterruptedException, IOException {
    
    if (adbDisabled) {
      throw new IOException("adb is currently disabled");
    }
        
    final String[] adbCmd;
    if (!cmd[0].equals("adb")) {
      adbCmd = PApplet.splice(cmd, "adb", 0);
    } else {
      adbCmd = cmd;
    }
    // printing this here to see if anyone else is killing the adb server
    if (processing.app.Base.DEBUG) {
      PApplet.printArray(adbCmd);
    }
    try {
      ProcessResult adbResult = new ProcessHelper(adbCmd).execute();
      // Ignore messages about starting up an adb daemon
      String out = adbResult.getStdout();
      if (out.contains(ADB_DAEMON_MSG_1) && out.contains(ADB_DAEMON_MSG_2)) {
        StringBuilder sb = new StringBuilder();
        for (String line : out.split("\n")) {
          if (!out.contains(ADB_DAEMON_MSG_1) &&
              !out.contains(ADB_DAEMON_MSG_2)) {
            sb.append(line).append("\n");
          }
        }
        return new ProcessResult(adbResult.getCmd(),
                                 adbResult.getResult(),
                                 sb.toString(),
                                 adbResult.getStderr(),
                                 adbResult.getTime());
      }
      return adbResult;
    } catch (IOException ioe) {
      if (-1 < ioe.getMessage().indexOf("Permission denied")) {
        Messages.showWarning("Trouble with adb!",
            "Could not run the adb tool from the Android SDK.\n" +
            "One possibility is that its executable permission\n" +
            "is not properly set. You can try setting this\n" +
            "permission manually, or re-installing the SDK.\n\n" +
            "The mode will be disabled until this problem is fixed.\n");
        adbDisabled = true;
      }
      throw ioe;
    }
  }

  static class SDKTarget {
    public int version = 0;
    public String name;
  }

  public ArrayList<SDKTarget> getAvailableSdkTargets() throws IOException {
    ArrayList<SDKTarget> targets = new ArrayList<SDKTarget>();

    for(File platform : platforms.listFiles()) {
      File propFile = new File(platform, "build.prop");
      if (!propFile.exists()) continue;

      SDKTarget target = new SDKTarget();

      BufferedReader br = new BufferedReader(new FileReader(propFile));
      String line;
      while ((line = br.readLine()) != null) {
        String[] lineData = line.split("=");
        if (lineData[0].equals("ro.build.version.sdk")) {
          target.version = Integer.valueOf(lineData[1]);
        }

        if (lineData[0].equals("ro.build.version.release")) {
          target.name = lineData[1];
          break;
        }
      }
      br.close();

      if (target.version != 0 && target.name != null) targets.add(target);
    }

    return targets;
  }
  
  @SuppressWarnings("serial")
  static public class BadSDKException extends Exception {
    public BadSDKException(final String message) {
      super(message);
    }
  }
  
  @SuppressWarnings("serial")
  static public class CancelException extends Exception {
    public CancelException(final String message) {
      super(message);
    }
  }    
}