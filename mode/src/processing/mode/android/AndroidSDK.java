/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-17 The Processing Foundation

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

import processing.app.Language;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.app.ui.Toolkit;
import processing.core.PApplet;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/** 
 * Class holding all needed references (path, tools, etc) to the SDK used by 
 * the mode.
 */
class AndroidSDK {
  public static boolean adbDisabled = false;
  
  final static private int FONT_SIZE = Toolkit.zoom(11);
  final static private int TEXT_MARGIN = Toolkit.zoom(8);
  final static private int TEXT_WIDTH = Toolkit.zoom(300);
  
  private final File folder;  
  private final File platforms;
  private final File highestPlatform;  
  private final File androidJar;
  private final File platformTools;
  private final File buildTools;
  private final File cmdlineTools;
  private final File avdManager;
  private final File sdkManager;
  private final File emulator;
  
  private static final String SDK_DOWNLOAD_URL = 
      "https://developer.android.com/studio/index.html#downloads";

  private static final String PROCESSING_FOR_ANDROID_URL = 
      "http://android.processing.org/";    
  
  private static final String WHATS_NEW_URL =
      "http://android.processing.org/whatsnew.html";
  
  private static final String DRIVER_INSTALL_URL = 
      "https://developer.android.com/studio/run/oem-usb.html#InstallingDriver";
  
  private static final String SYSTEM_32BIT_URL = 
      "https://askubuntu.com/questions/710426/android-sdk-on-ubuntu-32bit";
  
  private static final String SDK_LICENSE_URL = 
      "https://developer.android.com/studio/terms.html";

  private static final int NO_ERROR     = 0;
  private static final int SKIP_ENV_SDK = 1;
  private static final int MISSING_SDK  = 2;
  private static final int INVALID_SDK  = 3;
  private static int loadError = NO_ERROR;

  public AndroidSDK(File folder) throws BadSDKException, IOException {
    this.folder = folder;
    if (!folder.exists()) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_sdk_folder", folder));
    }
    
    cmdlineTools = new File(folder, "cmdline-tools/latest");
    if (!cmdlineTools.exists()) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_tools_folder", folder));
    }

    platformTools = new File(folder, "platform-tools");
    if (!platformTools.exists()) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_platform_tools_folder", folder));
    }

    buildTools = new File(folder, "build-tools");
    if (!buildTools.exists()) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_build_tools_folder", folder));
    }
    
    platforms = new File(folder, "platforms");
    if (!platforms.exists()) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_platforms_folder", folder));
    }
    
    // Retrieve the highest platform from the available targets
    ArrayList<SDKTarget> targets = getAvailableSdkTargets();
    int highest = 1;
    for (SDKTarget targ: targets) {
      if (highest < targ.version) {
        highest = targ.version;
      }
    }
    
    if (highest < PApplet.parseInt(AndroidBuild.TARGET_SDK)) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_target_platform", 
          AndroidBuild.TARGET_SDK, platforms.getAbsolutePath()));      
    }
        
    highestPlatform = new File(platforms, "android-" + highest);

    androidJar = new File(highestPlatform, "android.jar");
    if (!androidJar.exists()) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.missing_android_jar", 
                                                          AndroidBuild.TARGET_SDK, highestPlatform.getAbsolutePath()));
    }

    avdManager = findCliTool(new File(cmdlineTools, "bin"), "avdmanager");
    sdkManager = findCliTool(new File(cmdlineTools, "bin"), "sdkmanager");
    
    File emuFolder = new File(folder, "emulator");
    if (emuFolder.exists()) {
      // First try the new location of the emulator inside its own folder
      emulator = findCliTool(emuFolder, "emulator");   
    } else {
      // If not found, use old location inside tools
      emulator = findCliTool(cmdlineTools, "emulator");
    }
    
    String path = Platform.getenv("PATH");

    Platform.setenv("ANDROID_SDK", folder.getCanonicalPath());
    path = platformTools.getCanonicalPath() + File.pathSeparator +
      cmdlineTools.getCanonicalPath() + File.pathSeparator + path;

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
                  System.out.println(AndroidMode.getTextString("android_debugger.info.removing_expired_keystore"));
                  String hidingName = "debug.keystore." + AndroidMode.getDateStamp(expireMillis);
                  File hidingFile = new File(keystoreFile.getParent(), hidingName);
                  if (!keystoreFile.renameTo(hidingFile)) {
                    System.err.println(AndroidMode.getTextString("android_debugger.error.cannot_remove_expired_keystore"));
                    System.err.println(AndroidMode.getTextString("android_debugger.error.request_removing_keystore", keystoreFile.getAbsolutePath()));
                  }
                }
              } catch (ParseException pe) {
                System.err.println(AndroidMode.getTextString("android_debugger.error.invalid_keystore_timestamp", timestamp));
                System.err.println(AndroidMode.getTextString("android_debugger.error.request_bug_report"));
              }
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }


  public File getFolder() {
    return folder;
  }
  
  
  public File getBuildToolsFolder() {
    return buildTools;
  }
  
  
  public File getPlatformToolsFolder() {
    return platformTools;
  }
  
  
  public File getAndroidJarPath() {
    return androidJar;  
  }  
  
  
  public File getCommandLineToolsFolder() {
    return cmdlineTools;
  }

  
  public File getEmulatorTool() {
    return emulator;
  }
  

  public File getAVDManagerTool() {
    return avdManager;
  }


  public File getHighestPlatform() {
    return highestPlatform;
  }

  
  public File getZipAlignTool() {    
    File[] files = buildTools.listFiles();
    String name = Platform.isWindows() ? "zipalign.exe" : "zipalign";
    for (File f: files) {
      File z = new File(f, name);
      if (z.exists()) return z;
    }
    return null;
  }
  
  
  // Write to the process input, so the licenses will be accepted. In 
  // principle, we only need 7 'y', one for the 'yes' to the first 
  // 'review licenses?' question, the rest for the 6 licenses, but adding
  // 10 just in case, having more does not cause any trouble.  
  private static final String response = "y\ny\ny\ny\ny\ny\ny\ny\ny\ny\n";
  
  private void acceptLicenses() {
    ProcessBuilder pb = new ProcessBuilder(sdkManager.getAbsolutePath(), "--licenses");
    pb.redirectErrorStream(true);
    try {
      Process process = pb.start();
      final OutputStream os = process.getOutputStream();
      final InputStream is = process.getInputStream();
      // Read the process output, otherwise read() will block and wait for new 
      // data to read
      new Thread(new Runnable() {
        public void run() {
          byte[] b = new byte[1024];
          try {
            while (is.read(b) != -1) { }
            is.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }, "AndroidSDK: reading licenses").start();
      Thread.sleep(1000);
      os.write(response.getBytes());
      os.flush();
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
  
  
  static public File getHAXMInstallerFolder() {
    String sdkPrefsPath = Preferences.get("android.sdk.path");    
    File sdkPath = new File(sdkPrefsPath);
    return new File(sdkPath, "extras/intel/HAXM");
  }
  

  static public File getGoogleDriverFolder() {
    String sdkPrefsPath = Preferences.get("android.sdk.path");    
    File sdkPath = new File(sdkPrefsPath);
    return new File(sdkPath, "extras/google/usb_driver");
  }  
  

  /**
   * Checks a path to see if there's a tools/android file inside, a rough check
   * for the SDK installation. Also figures out the name of android/android.bat/android.exe
   * so that it can be called explicitly.
   */
  private static File findCliTool(final File tools, String name) 
      throws BadSDKException {
    if (new File(tools, name + ".bat").exists()) {
      return new File(tools, name + ".bat");
    }
    if (new File(tools, name + ".exe").exists()) {
      return new File(tools, name + ".exe");
    }    
    if (new File(tools, name).exists()) {
      return new File(tools, name);
    }
    throw new BadSDKException("Cannot find " + name + " in " + tools);
  }
  

  /**
   * Check for a set android.sdk.path preference. If the pref
   * is set, and refers to a legitimate Android SDK, then use that.
   *
   * Check for the ANDROID_SDK environment variable. If the variable is set,
   * and refers to a legitimate Android SDK, then use that and save the pref.
   *
   * Prompt the user to select an Android SDK. If the user selects a
   * legitimate Android SDK, then use that, and save the preference.
   *
   * @return an AndroidSDK
   * @throws BadSDKException
   * @throws IOException
   */
  public static AndroidSDK load(boolean checkEnvSDK, Frame editor) throws IOException {
    loadError = NO_ERROR;
    
    // Give priority to preferences:
    // https://github.com/processing/processing-android/issues/372
    final String sdkPrefsPath = Preferences.get("android.sdk.path");
    if (sdkPrefsPath != null && !sdkPrefsPath.equals("")) {
      try {
        final AndroidSDK androidSDK = new AndroidSDK(new File(sdkPrefsPath));
        Preferences.set("android.sdk.path", sdkPrefsPath);
        return androidSDK;
      } catch (final BadSDKException badPref) {
        Preferences.unset("android.sdk.path");
        loadError = INVALID_SDK;
      }
    }    
    
    final String sdkEnvPath = Platform.getenv("ANDROID_SDK");
    if (sdkEnvPath != null && !sdkEnvPath.equals("")) {
      try {
        final AndroidSDK androidSDK = new AndroidSDK(new File(sdkEnvPath));
        
        if (checkEnvSDK && editor != null) {
          // There is a valid SDK in the environment, but let's give the user
          // the option to not to use it. After this, we should go straight to 
          // download a new SDK.
          int result = showEnvSDKDialog(editor);
          if (result != JOptionPane.YES_OPTION) {
            loadError = SKIP_ENV_SDK;
            return null;
          } 
        }
        
        // Set this value in preferences.txt, in case ANDROID_SDK
        // gets knocked out later. For instance, by that pesky Eclipse,
        // which nukes all env variables when launching from the IDE.
        Preferences.set("android.sdk.path", sdkEnvPath);
        
        // If we are here, it means that there was no SDK path in the preferences
        // and the user wants to use the SDK found in the environment. This
        // means we just installed the mode for the first time, so we show a 
        // welcome message with some useful info.
        AndroidUtil.showMessage(AndroidMode.getTextString("android_sdk.dialog.using_existing_sdk_title"),
                                AndroidMode.getTextString("android_sdk.dialog.using_existing_sdk_body", 
                                                          PROCESSING_FOR_ANDROID_URL, WHATS_NEW_URL));

        return androidSDK;
      } catch (final BadSDKException badEnv) { 
        Preferences.unset("android.sdk.path");        
        loadError = INVALID_SDK;
      }
    } else if (loadError == NO_ERROR) {
      loadError = MISSING_SDK; 
    }
    
    return null;
  }


  static public AndroidSDK locate(final Frame window, final AndroidMode androidMode)
      throws BadSDKException, CancelException, IOException {
    
    if (loadError == SKIP_ENV_SDK) {
      // The user does not want to use the environment SDK, so let's simply
      // download a new one to the sketchbook folder.
      return download(window, androidMode);
    }
    
    // At this point, there is no ANDROID_SDK env variable, no SDK in the preferences,
    // or either one was invalid, so we will continue by asking the user to either locate
    // a valid SDK manually, or download a new one.
    int result = showLocateDialog(window);
    
    if (result == JOptionPane.YES_OPTION) {
      return download(window, androidMode);
    } else if (result == JOptionPane.NO_OPTION) {
      // User will manually select folder containing SDK folder
      File folder = selectFolder(AndroidMode.getTextString("android_sdk.dialog.select_sdk_folder"), null, window);
      if (folder == null) {
        throw new CancelException(AndroidMode.getTextString("android_sdk.error.cancel_sdk_selection")); 
      } else {
        final AndroidSDK androidSDK = new AndroidSDK(folder);
        Preferences.set("android.sdk.path", folder.getAbsolutePath());
        return androidSDK;
      }
    } else {
      throw new CancelException(AndroidMode.getTextString("android_sdk.error.sdk_selection_canceled")); 
    }
  }
  
  static public boolean locateSysImage(final Frame window, 
      final AndroidMode androidMode, final boolean wear, final boolean ask)
      throws BadSDKException, CancelException, IOException {
    final int result = showDownloadSysImageDialog(window, wear);
    if (result == JOptionPane.YES_OPTION) {
      return downloadSysImage(window, androidMode, wear, ask);
    } else if (result == JOptionPane.NO_OPTION) {
      return false;
    } else {
      return false; 
    }
  }

  static public AndroidSDK download(final Frame editor, final AndroidMode androidMode) 
      throws BadSDKException, CancelException {
    final SDKDownloader downloader = new SDKDownloader(editor);    
    downloader.run(); // This call blocks until the SDK download complete, or user cancels.
    
    if (downloader.cancelled()) {
      throw new CancelException(AndroidMode.getTextString("android_sdk.error.sdk_download_canceled"));  
    } 
    AndroidSDK sdk = downloader.getSDK();
    if (sdk == null) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.sdk_download_failed"));
    }
    
    final int result = showSDKLicenseDialog(editor);
    if (result == JOptionPane.YES_OPTION) {
      sdk.acceptLicenses();   
      String msg = AndroidMode.getTextString("android_sdk.dialog.sdk_installed_body", PROCESSING_FOR_ANDROID_URL, WHATS_NEW_URL);
      File driver = AndroidSDK.getGoogleDriverFolder();
      if (Platform.isWindows() && driver.exists()) {
        msg += AndroidMode.getTextString("android_sdk.dialog.install_usb_driver", DRIVER_INSTALL_URL, driver.getAbsolutePath()); 
      }
      AndroidUtil.showMessage(AndroidMode.getTextString("android_sdk.dialog.sdk_installed_title"), msg);      
    } else {
      AndroidUtil.showMessage(AndroidMode.getTextString("android_sdk.dialog.sdk_license_rejected_title"), 
                              AndroidMode.getTextString("android_sdk.dialog.sdk_license_rejected_body"));
    }
    
    if (Platform.isLinux() && Platform.getNativeBits() == 32) {      
      AndroidUtil.showMessage(AndroidMode.getTextString("android_sdk.dialog.32bit_system_title"),
                              AndroidMode.getTextString("android_sdk.dialog.32bit_system_body", SYSTEM_32BIT_URL));
    }
    
    return sdk;
  }
  
  static public boolean downloadSysImage(final Frame editor, 
      final AndroidMode androidMode, final boolean wear, final boolean ask) 
      throws BadSDKException, CancelException {
    final SysImageDownloader downloader = new SysImageDownloader(editor, wear, ask);    
    downloader.run(); // This call blocks until the SDK download complete, or user cancels.
    
    if (downloader.cancelled()) {
      throw new CancelException(AndroidMode.getTextString("android_sdk.error.emulator_download_canceled"));  
    } 
    boolean res = downloader.getResult();
    if (!res) {
      throw new BadSDKException(AndroidMode.getTextString("android_sdk.error.emulator_download_failed"));
    }
    return res;
  }
  

  static public int showEnvSDKDialog(Frame editor) {
    String title = AndroidMode.getTextString("android_sdk.dialog.found_installed_sdk_title");
    String htmlString = "<html> " +
        "<head> <style type=\"text/css\">" +
        "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " + 
            "margin: " + TEXT_MARGIN + "px; " + 
            "width: " + TEXT_WIDTH + "px }" +
        "</style> </head>" +
        "<body> <p>" + AndroidMode.getTextString("android_sdk.dialog.found_installed_sdk_body") + "</p> </body> </html>";    
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
    
    String[] options = new String[] { AndroidMode.getTextString("android_sdk.option.use_existing_sdk"), 
                                      AndroidMode.getTextString("android_sdk.option.download_new_sdk") };
    int result = JOptionPane.showOptionDialog(null, pane, title, 
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, 
        null, options, options[0]);
    if (result == JOptionPane.YES_OPTION) {
      return JOptionPane.YES_OPTION;
    } else if (result == JOptionPane.NO_OPTION) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }  
  
  
  static public int showLocateDialog(Frame editor) {
    // How to show a option dialog containing clickable links:
    // http://stackoverflow.com/questions/8348063/clickable-links-in-joptionpane
    String htmlString = "<html> " +
        "<head> <style type=\"text/css\">" +
        "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " + 
            "margin: " + TEXT_MARGIN + "px; " + 
            "width: " + TEXT_WIDTH + "px }" +
        "</style> </head>";
    String title = "";
    if (loadError == MISSING_SDK) {
      htmlString += "<body> <p>" + AndroidMode.getTextString("android_sdk.dialog.cannot_find_sdk_body", SDK_DOWNLOAD_URL, AndroidBuild.TARGET_SDK) + "</p> </body> </html>";
      title = AndroidMode.getTextString("android_sdk.dialog.cannot_find_sdk_title");
    } else if (loadError == INVALID_SDK) {
      htmlString += "<body> <p>" + AndroidMode.getTextString("android_sdk.dialog.invalid_sdk_body", AndroidBuild.TARGET_SDK, SDK_DOWNLOAD_URL, AndroidBuild.TARGET_SDK) + "</p> </body> </html>";
      title = AndroidMode.getTextString("android_sdk.dialog.invalid_sdk_title");
    }    
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
    
    String[] options = new String[] { AndroidMode.getTextString("android_sdk.option.download_sdk"), 
                                      AndroidMode.getTextString("android_sdk.option.locate_sdk") };
    int result = JOptionPane.showOptionDialog(null, pane, title, 
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, 
        null, options, options[0]);
    if (result == JOptionPane.YES_OPTION) {
      return JOptionPane.YES_OPTION;
    } else if (result == JOptionPane.NO_OPTION) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }
  
  
  static public int showDownloadSysImageDialog(Frame editor, boolean wear) {
    String title = wear ? AndroidMode.getTextString("android_sdk.dialog.download_watch_image_title") : 
                          AndroidMode.getTextString("android_sdk.dialog.download_phone_image_title");    
    String msg = wear ? AndroidMode.getTextString("android_sdk.dialog.download_watch_image_body") : 
                        AndroidMode.getTextString("android_sdk.dialog.download_phone_image_body");
    String htmlString = "<html> " +
        "<head> <style type=\"text/css\">"+
        "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " + 
            "margin: " + TEXT_MARGIN + "px; " + 
            "width: " + TEXT_WIDTH + "px }" +
        "</style> </head>" + "<body> <p>" + msg + "</p> </body> </html>";
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
    
    String[] options = new String[] { Language.text("prompt.yes"), Language.text("prompt.no") };
    
    int result = JOptionPane.showOptionDialog(null, pane, title, 
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, 
        null, options, options[0]);
    if (result == JOptionPane.YES_OPTION) {
      return JOptionPane.YES_OPTION;
    } else if (result == JOptionPane.NO_OPTION) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }
  
  
  static public int showSDKLicenseDialog(Frame editor) {
    String title = AndroidMode.getTextString("android_sdk.dialog.accept_sdk_license_title");    
    String msg = AndroidMode.getTextString("android_sdk.dialog.accept_sdk_license_body", SDK_LICENSE_URL);
    String htmlString = "<html> " +
        "<head> <style type=\"text/css\">"+
        "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " + 
            "margin: " + TEXT_MARGIN + "px; " + 
            "width: " + TEXT_WIDTH + "px }" +
        "</style> </head>" + "<body> <p>" + msg + "</p> </body> </html>";
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
    
    String[] options = new String[] { Language.text("prompt.yes"), Language.text("prompt.no") };
    
    int result = JOptionPane.showOptionDialog(null, pane, title, 
        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, 
        null, options, options[0]);
    if (result == JOptionPane.YES_OPTION) {
      return JOptionPane.YES_OPTION;
    } else if (result == JOptionPane.NO_OPTION) {
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
    
  public ProcessResult runADB(final String... cmd)
    throws InterruptedException, IOException {
    
    if (adbDisabled) {
      throw new IOException("adb is currently disabled");
    }
        
    final String[] adbCmd;
    if (!cmd[0].contains("adb")) {      
      File abdPath = Platform.isWindows() ? new File(platformTools, "adb.exe") :
                                            new File(platformTools, "adb");
      adbCmd = PApplet.splice(cmd, abdPath.getCanonicalPath(), 0);
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
        Messages.showWarning(AndroidMode.getTextString("android_sdk.warn.cannot_run_adb_title"), 
                             AndroidMode.getTextString("android_sdk.warn.cannot_run_adb_body"));
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

    for (File platform : platforms.listFiles()) {
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