/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-21 The Processing Foundation

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
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.exec.StreamPump;
import processing.core.PApplet;

import java.awt.Frame;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;


public class AVD {
  final static private int PHONE = 0;
  final static private int WEAR  = 1;

  final static public String DEFAULT_ABI = "x86";
  
  public static final String TARGET_SDK_ARM = "24";
  public static final String TARGET_SDK_WEAR = "30";

  public final static String DEFAULT_PHONE_PORT = "5566";
  public final static String DEFAULT_WEAR_PORT  = "5576";  

  private static final String GETTING_START_TUT_URL = 
      "https://android.processing.org/tutorials/getting_started/index.html";

  static final String DEVICE_DEFINITION = "pixel_6";
  static final String DEVICE_SKIN = "pixel_6";

  static final String DEVICE_WEAR_DEFINITION = "wearos_square";
  static final String DEVICE_WEAR_SKIN = "wearos_square";

  /** Name of this avd. */
  protected String name;

  protected String device;
  protected String skin;
  protected int type;

  static ArrayList<String> avdList;
  static ArrayList<String> badList;
  
  /** "system-images;android-25;google_apis;x86" */
  static ArrayList<String> wearImages;
  static ArrayList<String> phoneImages;
  
  private static Process process;

  /** Default virtual device used by Processing. */
  static public final AVD phoneAVD =
    new AVD("processing-phone",
            DEVICE_DEFINITION, DEVICE_SKIN, PHONE);

  /** Default virtual wear device used by Processing. */
  static public final AVD watchAVD =
    new AVD("processing-watch",
            DEVICE_WEAR_DEFINITION, DEVICE_WEAR_SKIN, WEAR);

  
  public AVD(final String name, final String device, final String skin, int type) {
    this.name = name;
    this.device = device;
    this.skin = skin;
    this.type = type;
  }


  static public String getName(boolean wear) {
    if (wear) {
      return AVD.watchAVD.name;
    } else {
      return AVD.phoneAVD.name;
    }
  }

  static public String getTargetSDK(boolean wear, String abi) {
    if (abi.equals("arm")) {
      // The ARM images using Google APIs are too slow, so use the
      // older Android (AOSP) images.
      // TODO check if we can move to the regular ARM images...
      return TARGET_SDK_ARM;
    } else if (abi.equals("arm64-v8a")) {
      return wear ? TARGET_SDK_WEAR : AndroidBuild.TARGET_PLATFORM;
    } else { // x86
      return wear ? TARGET_SDK_WEAR : AndroidBuild.TARGET_PLATFORM;
    }
  }
  
  static public String getPreferredPlatform(boolean wear, String abi) {
    return "android-" + getTargetSDK(wear, abi);
  }
  
  static public String getPreferredPort(boolean wear) {
    String port = "";
    if (wear) {
      port = Preferences.get("android.emulator.port.wear");
      if (port == null) {
        port = DEFAULT_WEAR_PORT;
        Preferences.set("android.emulator.port.wear", port);
      }
    } else {
      port = Preferences.get("android.emulator.port.phone");
      if (port == null) {
        port = DEFAULT_PHONE_PORT;
        Preferences.set("android.emulator.port.phone", port);
      }
    }
    return port;
  }

  
  static protected String getPreferredTag(boolean wear, String abi) {
    if (wear) {
      return "android-wear";
//    } else if (abi.contains("arm")) {
//      // The ARM images are located in the default folder. No, apparently ARM images are in google_apis too
//      return "default";
    } else {
      return "google_apis";
    }
  }  
    
  
  static protected String getPreferredABI() {
    String abi = Preferences.get("android.emulator.image.abi");
    if (abi == null) {
      abi = DEFAULT_ABI;
      Preferences.set("android.emulator.image.abi", abi); 
    }
    return abi;
  }

  
  static protected void list(final AndroidSDK sdk) throws IOException {
    String prefABI = getPreferredABI();

    try {
      avdList = new ArrayList<String>();
      badList = new ArrayList<String>();
      File avdManager = sdk.getAVDManagerTool();
      ProcessBuilder pb = new ProcessBuilder(avdManager.getCanonicalPath(), "list", "avd");
      Map<String, String> env = pb.environment();
      env.clear();
      env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
      pb.redirectErrorStream(true);

      process = pb.start();

      StringWriter outWriter = new StringWriter();
      new StreamPump(process.getInputStream(), "out: ").addTarget(outWriter).start();
      process.waitFor();

      String[] lines = PApplet.split(outWriter.toString(), '\n');

      if (process.exitValue() == 0) {
        String name = "";
        String abi = "";
        boolean badness = false;
        for (String line : lines) {
          String[] m = PApplet.match(line, "\\s+Name\\:\\s+(\\S+)");
          String[] t = PApplet.match(line, "\\s+Tag/ABI\\:\\s+(\\S+)");
          
          if (m != null) {
            name = m[1];
          }  
          if (t != null) {
            abi = t[1];
            if (-1 < abi.indexOf("/" + prefABI)) {
              if (!badness) {
//              System.out.println("good: " + m[1]);
                avdList.add(name);
              } else {
//              System.out.println("bad: " + m[1]);
                badList.add(name);
              }
//          } else {
//            System.out.println("nope: " + line);              
            }
          } 
          

          // "The following Android Virtual Devices could not be loaded:"
          if (line.contains("could not be loaded:")) {
//            System.out.println("starting the bad list");
//            System.err.println("Could not list AVDs:");
//            System.err.println(listResult);
            badness = true;
//            break;
          }
        }
      } else {
        System.err.println("Unhappy inside exists()");
        System.err.println(outWriter.toString());
      }
    } catch (final InterruptedException ie) { }
    finally {
      process.destroy();
    }
  }


  protected boolean exists(final AndroidSDK sdk) throws IOException {
    if (avdList == null) {
      list(sdk);
    }
    for (String avd : avdList) {
      if (Base.DEBUG) {
        System.out.println("AVD.exists() checking for " + name + " against " + avd);
      }
      if (avd.equals(name)) {
        return true;
      }
    }
    return false;
  }


  /**
   * Return true if a member of the renowned and prestigious
   * "The following Android Virtual Devices could not be loaded:" club.
   * (Prestigious may also not be the right word.)
   */
  protected boolean badness() {
    for (String avd : badList) {
      if (avd.equals(name)) {
        return true;
      }
    }
    return false;
  }

  
  protected boolean hasImages(final AndroidSDK sdk) throws IOException {
    String abi = getPreferredABI(); 
    if (type == PHONE) {
      if (phoneImages == null) {
        phoneImages = new ArrayList<String>();
        getImages(phoneImages, sdk, abi);
      }
      return !phoneImages.isEmpty();
    } else {
      if (wearImages == null) {
        wearImages = new ArrayList<String>();
        getImages(wearImages, sdk, abi);
      }
      return !wearImages.isEmpty();      
    }
  }
  

  protected void refreshImages(final AndroidSDK sdk) throws IOException {
    String abi = getPreferredABI();
    
    if (type == PHONE) {
      phoneImages = new ArrayList<String>();
      getImages(phoneImages, sdk, abi);
    } else {
      wearImages = new ArrayList<String>();
      getImages(wearImages, sdk, abi);
    }
  } 
  
  
  protected void getImages(final ArrayList<String> images, final AndroidSDK sdk, 
      final String imageAbi) throws IOException {
    final boolean wear = type == WEAR;
    final String imagePlatform = getPreferredPlatform(wear, imageAbi);
    final String imageTag = getPreferredTag(wear, imageAbi); 
    
    final File avdManager = sdk.getAVDManagerTool();
    final String[] cmd = new String[] {
        avdManager.getCanonicalPath(),
        "create", "avd",
        "-n", "dummy",
        "-k", "dummy"
      };

    // Dummy avdmanager creation command to get the list of installed images,
    // so far this is the only method available get that list.
    ProcessBuilder pb = new ProcessBuilder(cmd);
    
    if (Base.DEBUG) {
      System.out.println(processing.core.PApplet.join(cmd, " "));
    }

    Map<String, String> env = pb.environment();
    env.clear();
    env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
    pb.redirectErrorStream(true);
    
    try {
      process = pb.start();
      StringWriter outWriter = new StringWriter();
      new StreamPump(process.getInputStream(), "out: ").addTarget(outWriter).start();
      process.waitFor();

      String[] lines = PApplet.split(outWriter.toString(), '\n');
      for (String line : lines) {
        if (images != null &&
                line.contains(";" + imagePlatform) &&
                line.contains(";" + imageTag) &&
                line.contains(";" + imageAbi)) {
          images.add(line);
        }
      }

    } catch (final InterruptedException ie) {
      ie.printStackTrace();
    } finally {
      process.destroy();
    }
  }

  
  protected String getSdkId() throws IOException {
    String abi = getPreferredABI();
    
    if (type == PHONE) {
      for (String image : phoneImages) {
        if (image.contains(";" + abi)) return image;
      }
    } else {
      for (String image : wearImages) {
        if (image.contains(";" + abi)) return image;
      }
    }

    // Could not find any suitable package
    return "null";
  }

  protected void copyDeviceSkins(final AndroidSDK sdk, final AndroidMode mode) {
    File skinsFolder = new File(sdk.getFolder(), "skins");
    if (!skinsFolder.exists()) {
      // The skins in this folder come from Android Studio, on Mac they are in the folder:
      // /Applications/Android Studio.app/Contents/plugins/android/resources/device-art-resources
      // Apparently the skins are not available as a SDK download.
      File artFolder = new File(mode.getResourcesFolder(), "device-art-resources");
      AndroidUtil.copyDir(artFolder, skinsFolder);
    }
  }

  protected boolean create(final AndroidSDK sdk) throws IOException {
    File sketchbookFolder = processing.app.Base.getSketchbookFolder();
    File androidFolder = new File(sketchbookFolder, "android");
    if (!androidFolder.exists()) androidFolder.mkdir();    
    File avdPath = new File(androidFolder, "avd/" + name);
    
    File avdManager = sdk.getAVDManagerTool();
    final String[] cmd = new String[] {
        avdManager.getCanonicalPath(),
        "create", "avd",
        "-n", name,      
        "-k", getSdkId(),
        "-p", avdPath.getAbsolutePath(),
        "-d", device,
        "--skin", skin,
        "--force"
    };
    
    ProcessBuilder pb = new ProcessBuilder(cmd);
    
    if (Base.DEBUG) {
      System.out.println(processing.core.PApplet.join(cmd, " "));
    }
    
    // avdmanager create avd -n "Wear-Processing-0254" -k "system-images;android-25;google_apis;x86" -c 64M
    // Set the list to null so that exists() will check again
//    avdList = null;

    Map<String, String> env = pb.environment();
    env.clear();
    env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
    pb.redirectErrorStream(true);

    try {
      process = pb.start();

      // Passes 'no' to "Do you wish to create a custom hardware profile [no]"
//      OutputStream os = process.getOutputStream();
//      PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
//      pw.println("no");
//      pw.flush();
//      pw.close();
//      os.flush();
//      os.close();

      StringWriter outWriter = new StringWriter();
      new StreamPump(process.getInputStream(), "out: ").addTarget(outWriter).start();

      process.waitFor();

      if (process.exitValue() == 0) {
        // Add skin to AVD's config file
//        File configFile = new File(avdPath, "config.ini");
//        if (configFile.exists()) {
//          try (PrintWriter output = new PrintWriter(new FileWriter(configFile.getAbsolutePath(), true))) {
//            output.printf("%s\r\n", "skin.name=" + skin);
//          }
//          catch (Exception e) {}
//        }
        return true;
      }

      if (outWriter.toString().contains("Package path is not valid")) {
        // They didn't install the Google APIs
        AndroidUtil.showMessage(AndroidMode.getTextString("android_avd.error.sdk_wrong_install_title"),
                                AndroidMode.getTextString("android_avd.error.sdk_wrong_install_body", GETTING_START_TUT_URL));
      } else {
        // Just generally not working
        AndroidUtil.showMessage(AndroidMode.getTextString("android_avd.error.cannot_create_avd_title"),
                                AndroidMode.getTextString("android_avd.error.cannot_create_avd_body", AndroidBuild.TARGET_SDK));
      }
      System.err.println(outWriter.toString());
    } catch (final InterruptedException ie) { 
      ie.printStackTrace(); 
    } finally {
      process.destroy();
    }

    return false;
  }


  static public boolean ensureProperAVD(final Frame window, final AndroidMode mode, 
      final AndroidSDK sdk, boolean wear) {
    try {
      AVD avd = wear ? watchAVD : phoneAVD;
      if (avd.exists(sdk)) {
        return true;
      }
      if (avd.badness()) {
        AndroidUtil.showMessage(AndroidMode.getTextString("android_avd.error.cannot_load_avd_title"), 
                                AndroidMode.getTextString("android_avd.error.cannot_load_avd_body"));
        return false;
      }
      if (!avd.hasImages(sdk)) {
        // Check that the AVD for the other kind of device has been already
        // downloaded, and if so, the downloader should not ask for an 
        // ABI again.
        AVD other = wear ? phoneAVD : watchAVD;
        boolean ask = !other.hasImages(sdk);        
        boolean res = AndroidSDK.requestSysImage(window, mode, wear, ask);
        if (!res) {
          return false;
        } else {
          avd.refreshImages(sdk);
        }
      }
      avd.copyDeviceSkins(sdk, mode);
      if (avd.create(sdk)) {
        return true;
      }
    } catch (final Exception e) {
      e.printStackTrace();
      AndroidUtil.showMessage(AndroidMode.getTextString("android_avd.error.cannot_create_avd_title"),
                              AndroidMode.getTextString("android_avd.error.cannot_create_avd_body", AndroidBuild.TARGET_SDK));
    }
    return false;
  }
}
