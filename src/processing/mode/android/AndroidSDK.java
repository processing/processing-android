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
  private final File tools;
  private final File platforms;
  private final File targetPlatform;
  private final File androidJar;
  private final File platformTools;
  private final File buildTools;
  private final File avdManager;
  private final File sdkManager;
  private final File wearablePath;
  private final File supportLibPath;
  
  private static final String SDK_DOWNLOAD_URL = 
      "https://developer.android.com/studio/index.html#downloads";

  private static final String SDK_LICENSE_URL = 
      "https://developer.android.com/studio/terms.html"; 
  
  private static final String USE_ENV_SDK_TITLE = "Found an Android SDK!";
  private static final String USE_ENV_SDK_MESSAGE = 
      "Processing found a valid Android SDK that seems to be in use already. " +
      "Processing could use this SDK too, or download a new one.<br><br>" +
      "Sharing the same SDK across different development tools, like Processing " +
      "and Android Studio, will save space (the SDK may use up to several GBs), " +
      "but when one tool updates the SDK, it can create problems in the other. " + 
      "If Processing downloads a new SDK, it will keep it separate from the one " +
      "it just found.<br><br>" +
      "What do you want to do?";

  private static final String MISSING_SDK_TITLE =
      "Cannot find an Android SDK...";    
  private static final String MISSING_SDK_MESSAGE =
      "Processing did not find an Android SDK on this computer. " +
      "If there is one, and you know where it is, click \"Locate SDK path\" " +
      "to select it, or \"Download SDK\" to let Processing download the SDK automatically.<br><br>" +
      "If you want to download the SDK manually, you can get "+
      "the command line tools from <a href=\"" + SDK_DOWNLOAD_URL + "\">here</a>. " +
      "Make sure to install the SDK platform for API " + AndroidBuild.TARGET_SDK + ".";
    
  private static final String INVALID_SDK_TITLE =
      "Android SDK is not valid...";  
  private static final String INVALID_SDK_MESSAGE =
      "Processing found an Android SDK, but is not valid. It could be missing " +
      "some files, or might not be including the required platform for " + 
      "API " + AndroidBuild.TARGET_SDK + ".<br><br>" + 
      "If a valid SDK is available in a different location, " +
      "click \"Locate SDK path\" to select it, or \"Download SDK\" to let " +
      "Processing download the SDK automatically.<br><br>" +
      "If you want to download the SDK manually, you can get "+
      "the command line tools from <a href=\"" + SDK_DOWNLOAD_URL + "\">here</a>. " +
      "Make sure to install the SDK platform for API " + AndroidBuild.TARGET_SDK + ".";  
  
  private static final String COMMAND_LINE_TUT_URL = 
      "http://android.processing.org/tutorials/command_line/index.html";  
  
  private static final String ANDROID_SYS_IMAGE_PRIMARY =
      "Download phone system image?";

  private static final String ANDROID_SYS_IMAGE_SECONDARY =
      "The system image needed by the emulator does not appear to be installed. " +
      "Do you want Processing to download and install it now? <br><br>" +
      "Otherwise, you will need to do it through the sdkmanager<br>" +
      "command line tool, check <a href=\"" + COMMAND_LINE_TUT_URL + 
      "\">this online tutorial</a> for more info.";

  private static final String ANDROID_SYS_IMAGE_WEAR_PRIMARY =
      "Download watch system image?";

  private static final String ANDROID_SYS_IMAGE_WEAR_SECONDARY =
      "The system image needed by the emulator does not appear to be installed. " +
      "Do you want Processing to download and install it now? <br><br>" +
      "Otherwise, you will need to do it through the sdkmanager<br>" +
      "command line tool, check <a href=\"" + COMMAND_LINE_TUT_URL + 
      "\">this online tutorial</a> for more info.";  
    
  private static final String SELECT_ANDROID_SDK_FOLDER =
    "Choose the location of the Android SDK";

  private static final String SDK_INSTALL_TITLE = "SDK installed!";

  private static final String PROCESSING_FOR_ANDROID_URL = 
      "http://android.processing.org/";    
  
  private static final String WHATS_NEW_URL =
      "http://android.processing.org/whatsnew.html";
  
  private static final String DRIVER_INSTALL_URL = 
      "https://developer.android.com/studio/run/oem-usb.html#InstallingDriver";    
  
  private static final String SDK_INSTALL_MESSAGE =
      "Processing just downloaded and installed the Android SDK succesfully. " + 
      "The Android mode is now ready to use!<br><br>" + 
      "For documentation, examples, and tutorials, " + 
      "visit the <a href=\"" + PROCESSING_FOR_ANDROID_URL + "\">Processing for Android website</a>, and " +
      "if you updated from version 3 of the mode, check the <a href=\"" + WHATS_NEW_URL + "\">what's new page</a>."; 
  
  private static final String SDK_EXISTS_TITLE = "SDK configured!";
  
  private static final String SDK_EXISTS_MESSAGE =
      "Processing will use the existing Android SDK. " + 
      "The Android mode is now ready to use!<br><br>" + 
      "For documentation, examples, and tutorials, " + 
      "visit the <a href=\"" + PROCESSING_FOR_ANDROID_URL + "\">Processing for Android website</a>, and " +
      "if you updated from version 3 of the mode, check the <a href=\"" + WHATS_NEW_URL + "\">what's new page</a>.";
  
  private static final String DRIVER_INSTALL_MESSAGE = "<br><br>" +
      "If you are planning to use Google Nexus devices, then you need the " + 
      "Google USB Driver to connect them to Processing. You will have to " + 
      "install the driver manually following <a href=\"" + DRIVER_INSTALL_URL + 
      "\">these instructions</a>.<br><br>" +
      "The installation files are available in this folder:</br>";     
  
  private static final int NO_ERROR     = 0;
  private static final int SKIP_ENV_SDK = 1;
  private static final int MISSING_SDK  = 2;
  private static final int INVALID_SDK  = 3;
  private static int loadError = NO_ERROR;

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
    
    targetPlatform = new File(platforms, AndroidBuild.TARGET_PLATFORM);
    if (!targetPlatform.exists()) {
      throw new BadSDKException("There is no Android " + 
                                AndroidBuild.TARGET_SDK + " in " + platforms.getAbsolutePath());
    }

    androidJar = new File(targetPlatform, "android.jar");
    if (!androidJar.exists()) {
      throw new BadSDKException("android.jar for plaform " + 
                                AndroidBuild.TARGET_SDK + " is missing from " + targetPlatform.getAbsolutePath());
    }
    
    wearablePath = new File(folder, "extras/google/m2repository/com/google/android/support/wearable");
    if (!wearablePath.exists()) {
      throw new BadSDKException("There is no wearable folder in " + folder);
    }
    
    supportLibPath = new File(folder, "extras/android/m2repository/com/android/support");
    if (!supportLibPath.exists()) {
      throw new BadSDKException("There is no support library folder in " + folder);
    }
        
    avdManager = findAvdManager(new File(tools, "bin"));
    sdkManager = findSdkManager(new File(tools, "bin"));

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
                System.err.println("The date â€œ" + timestamp + "â€� could not be parsed.");
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


  public File getToolsFolder() {
    return tools;
  }


  public String getAvdManagerPath() {
    return avdManager.getAbsolutePath();
  }


  public File getSdkFolder() {
    return folder;
  }


  public File getTargetPlatform() {
    return targetPlatform;
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

  
  public File getWearableFolder() {
    return wearablePath;
  }
  

  public File getSupportLibrary() {
    return supportLibPath;
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
  
  
  public void acceptLicenses() {
    ArrayList<String> commands = new ArrayList<String>();
    commands.add(sdkManager.getAbsolutePath());
    commands.add("--licenses");
    ProcessBuilder pb = new ProcessBuilder(commands);
    pb.redirectErrorStream(true);
    try {

        Process prs = pb.start();
        OutputStream writeTo = prs.getOutputStream();
        for (int i = 0; i < 7; i++) {
          Thread inThread = new Thread(new In(prs.getInputStream()));
          inThread.start();
          Thread.sleep(100);          
          writeTo.write("y\n".getBytes());
          writeTo.flush();
        }
        
        writeTo.close();        

    } catch (IOException e) {
        e.printStackTrace();
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }    
  }
  
  
  class In implements Runnable {
    private InputStream is;

    public In(InputStream is) {
        this.is = is;
    }

    @Override
    public void run() {
        byte[] b = new byte[1024];
        int size = 0;
        try {
            while ((size = is.read(b)) != -1) {
                System.err.println(new String(b));
            }
            is.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

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
   * for the SDK installation. Also figures out the name of android/android.bat
   * so that it can be called explicitly.
   */
  private static File findAvdManager(final File tools) throws BadSDKException {
    if (new File(tools, "avdmanager.bat").exists()) {
      return new File(tools, "avdmanager.bat");
    }
    if (new File(tools, "avdmanager").exists()) {
      return new File(tools, "avdmanager");
    }
    throw new BadSDKException("Cannot find avdmanager in " + tools);
  }

  
  private static File findSdkManager(final File tools) throws BadSDKException {
    if (new File(tools, "sdkmanager.bat").exists()) {
      return new File(tools, "sdkmanager.bat");
    }
    if (new File(tools, "sdkmanager").exists()) {
      return new File(tools, "sdkmanager");
    }
    throw new BadSDKException("Cannot find sdkdmanager in " + tools);
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
        androidSDK.acceptLicenses();
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
        AndroidUtil.showMessage(SDK_EXISTS_TITLE, SDK_EXISTS_MESSAGE);
        
        androidSDK.acceptLicenses();
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
      throw new CancelException("User canceled SDK download");  
    } 
    AndroidSDK sdk = downloader.getSDK();
    if (sdk == null) {
      throw new BadSDKException("SDK could not be downloaded");
    }

    String msg = SDK_INSTALL_MESSAGE;
    File driver = AndroidSDK.getGoogleDriverFolder();
    if (Platform.isWindows() && driver.exists()) {
      msg += DRIVER_INSTALL_MESSAGE + driver.getAbsolutePath();
    }
    AndroidUtil.showMessage(SDK_INSTALL_TITLE, msg);

    sdk.acceptLicenses();
    
    return sdk;
  }
  
  static public boolean downloadSysImage(final Frame editor, 
      final AndroidMode androidMode, final boolean wear, final boolean ask) 
      throws BadSDKException, CancelException {
    final SysImageDownloader downloader = new SysImageDownloader(editor, wear, ask);    
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
  

  static public int showEnvSDKDialog(Frame editor) {
    String title = USE_ENV_SDK_TITLE;
    String htmlString = "<html> " +
        "<head> <style type=\"text/css\">" +
        "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " + 
            "margin: " + TEXT_MARGIN + "px; " + 
            "width: " + TEXT_WIDTH + "px }" +
        "</style> </head>" +
        "<body> <p>" + USE_ENV_SDK_MESSAGE + "</p> </body> </html>";    
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
    
    String[] options = new String[] { "Use existing SDK", "Download new SDK" };
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
      htmlString += "<body> <p>" + MISSING_SDK_MESSAGE + "</p> </body> </html>";
      title = MISSING_SDK_TITLE;
    } else if (loadError == INVALID_SDK) {
      htmlString += "<body> <p>" + INVALID_SDK_MESSAGE + "</p> </body> </html>";
      title = INVALID_SDK_TITLE;
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
    
    String[] options = new String[] {
      "Download SDK automatically", "Locate SDK path manually"
    };    
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
    String title = wear ? ANDROID_SYS_IMAGE_WEAR_PRIMARY : ANDROID_SYS_IMAGE_PRIMARY;    
    String msg = wear ? ANDROID_SYS_IMAGE_WEAR_SECONDARY : ANDROID_SYS_IMAGE_SECONDARY;
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
    
    String[] options = new String[] { "Yes", "No" };
    
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