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

import processing.app.Base;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.exec.LineProcessor;
import processing.app.exec.StreamPump;
import processing.core.PApplet;

import java.awt.Frame;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;


public class AVD {
  final static private int PHONE = 0;
  final static private int WEAR  = 1;

  final static public String DEFAULT_ABI = "x86";
  
  public static final String TARGET_SDK_ARM = "24";   

  public final static String DEFAULT_PHONE_PORT = "5566";
  public final static String DEFAULT_WEAR_PORT  = "5576";  

  private static final String GETTING_START_TUT_URL = 
      "http://android.processing.org/tutorials/getting_started/index.html";  
  
  static final String DEFAULT_SDCARD_SIZE = "64M";
  
  static final String DEVICE_DEFINITION = "Nexus One";
  static final String DEVICE_SKIN = "480x800";
  
  static final String DEVICE_WEAR_DEFINITION = "wear_square_280_280dpi";
  static final String DEVICE_WEAR_SKIN = "280x280";

  private String image;
  
  /** Name of this avd. */
  protected String name;

  protected String device;
  protected String skin;
  protected int type;

  static ArrayList<String> avdList;
  static ArrayList<String> avdTypeList;
  static ArrayList<String> badList;
  
  /** "system-images;android-25;google_apis;x86" */
  static ArrayList<String> wearImages;
  static ArrayList<String> phoneImages;
  
  private static Process process;

//  /** Default virtual device used by Processing. */
//  static public final AVD phoneAVD =
//    new AVD("processing-phone",
//            DEVICE_DEFINITION, DEVICE_SKIN, PHONE);
//
//  /** Default virtual wear device used by Processing. */
//  static public final AVD watchAVD =
//    new AVD("processing-watch",
//            DEVICE_WEAR_DEFINITION, DEVICE_WEAR_SKIN, WEAR);

  
  public AVD(final String name, final String device, final String skin,
             final String image) {
    this.name = name;
    this.device = device;
    this.skin = skin;
    this.image = image;
  }


  public String getName() {
   return name;
  }
  
  
  static public String getPreferredPlatform(boolean wear, String abi) {
    if (wear) {
      return AndroidBuild.TARGET_PLATFORM;
    } else if (abi.equals("arm")) {
      // The ARM images using Google APIs are too slow, so use the 
      // older Android (AOSP) images.      
      return "android-" + TARGET_SDK_ARM;
    } else {
      return AndroidBuild.TARGET_PLATFORM;
    }
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
    } else if (abi.equals("arm")) {
      // The ARM images using Google APIs are too slow, so use the 
      // older Android (AOSP) images.      
      return "default";
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

  static protected String getSupportedABI() {
    String abi = Preferences.get("android.emulator.image.abi");
    if (abi == null) {
      Boolean x86 = true;
      // PROCESSOR_IDENTIFIER is only defined on Windows. For cross-platform CPU
      // info, in the future we could use OSHI: https://github.com/oshi/oshi
      String procId = System.getenv("PROCESSOR_IDENTIFIER");
      if (procId != null) {
        if (-1 < procId.indexOf("Intel")) {
          // Intel CPU: we go for the x86 abi
          x86 = true;
        } else {
          // Another CPU, can only be AMD, so we go for ARM abi
          x86 = false;
        }
      } else if (Platform.isMacOS()) {
        // Macs only have Intel CPUs, so we also go for the x86 abi
        x86 = true;
      }
      if (x86) {
        abi = "x86";
        SysImageDownloader.installHAXM();
      } else {
        abi = "arm";
      }
      Preferences.set("android.emulator.image.abi", abi);
    }
    return abi;
  }

  
  static protected void list(final AndroidSDK sdk) throws IOException {
    String prefABI = getPreferredABI();

    try {
      avdList = new ArrayList<String>();
      avdTypeList = new ArrayList<String>();
      badList = new ArrayList<String>();
      ProcessBuilder pb =
              new ProcessBuilder(sdk.getAvdManagerPath(), "list", "avd");
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
        String device = "";
        boolean badness = false;
        for (String line : lines) {
          String[] m = PApplet.match(line, "\\s+Name\\:\\s+(\\S+)");
          String[] t = PApplet.match(line, "\\s+Tag/ABI\\:\\s+(\\S+)");
          String[] d = PApplet.match(line, "\\s+Device\\:\\s+(\\S+)");

          if (d != null) {
            device = d[1];
          }
          if (m != null) {
            name = m[1];
          }  
          if (t != null) {
            abi = t[1];
            if (-1 < abi.indexOf("/" + prefABI)) {
              if (!badness) {
//              System.out.println("good: " + m[1]);
                avdList.add(name);
                avdTypeList.add(device);
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

  //return format {phoneDevices,phoneIds,wearDevices,wearIds}
  static protected Vector<Vector<String>> listDevices(final AndroidSDK sdk) {
    Vector<String> phoneDevices = new Vector<String>();
    Vector<String> wearDevices = new Vector<String>();
    Vector<String> phoneIds = new Vector<String>();
    Vector<String> wearIds = new Vector<String>();

    try {
      ProcessBuilder pb = new ProcessBuilder(sdk.getAvdManagerPath(), "list", "devices");
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
        for (int i=0;i<lines.length;i++) {
          String line = lines[i];
          if(line.contains("id:")) {
            String id = line.substring(line.indexOf('"'),line.length()-1);
            line = lines[++i];
            String name = line.substring(line.indexOf(": ")+1);
            line = lines[++i];
            if(line.contains("OEM : Generic")) continue;
            line = lines[++i];
            if(line.contains("Tag :")) {
              if (line.contains("android-wear")) {
                wearIds.add(id);
                wearDevices.add(name);
              } else continue;
            } else {
              phoneIds.add(id);
              phoneDevices.add(name);
            }
          }
        }
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    } finally {
      process.destroy();
    }
    Vector<Vector<String>> res = new Vector<Vector<String>>();
    res.add(phoneDevices); res.add(phoneIds);
    res.add(wearDevices); res.add(wearIds);
    return res;
  }

  static protected Vector<Vector<String>> listImages(AndroidSDK sdk, boolean wear) {
    Vector<Vector<String>> imageList = new Vector<Vector<String>>();
    try {
      ProcessBuilder pb = new ProcessBuilder(sdk.getSDKManagerPath(), "--list");
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
        boolean available = false;
        for (String line : lines) {
          if (line.contains("Available Packages:")) available = true;
          if(line.contains("system-images")) {
            String[] imageProps = line.split(";");
            //Search for only supported ABI . Mac and Intel other Intel will have x86
            //AMD will have arm
            String abi = getSupportedABI();
            //accept only x86 and not x86_64
            if(imageProps[3].substring(0,3).equals(abi) && !imageProps[3].contains("_64")) {
              Vector<String> image = new Vector<String>();
              image.add(imageProps[1]); //API Level
              image.add(imageProps[2]); //Tag
              image.add(imageProps[3].substring(0,3)); //ABI
              if (available) image.add("Not Installed");
              else image.add("Installed"); //installed status
              if (wear && imageProps[2].equals("android-wear")) imageList.add(image);
              if (!wear && imageProps[2].equals("google_apis")) imageList.add(image);

            }
          }
        }
      }
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    } finally {
      process.destroy();
    }
    return imageList;
  }

  static protected boolean deleteAVD(AndroidSDK sdk, String name) {
    try {
      ProcessBuilder pb = new ProcessBuilder(sdk.getAvdManagerPath(), "delete","avd", "-n",name);
      Map<String, String> env = pb.environment();
      env.clear();
      env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
      pb.redirectErrorStream(true);
      process = pb.start();

      StringWriter outWriter = new StringWriter();
      new StreamPump(process.getInputStream(), "out: ").addTarget(outWriter).start();
      process.waitFor();
      if (process.exitValue() == 0) return true;
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
    } finally {
      process.destroy();
    }
    return false;
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
    boolean wear = type == WEAR;
    final String imagePlatform = getPreferredPlatform(wear, imageAbi);
    final String imageTag = getPreferredTag(wear, imageAbi); 
    
    final String[] cmd = new String[] {
        sdk.getAvdManagerPath(),
        "create", "avd",
        "-n", "dummy",
        "-k", "dummy"
      };      
    
    // Dummy avdmanager creation command to get the list of installed images
    // TODO : Find a better way to get the list of installed images
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

      StreamPump output = new StreamPump(process.getInputStream(), "out: ");
      output.addTarget(new LineProcessor() {
        @Override
        public void processLine(String line) {
//          System.out.println("dummy output ---> " + line);
          if (images != null && 
              line.contains(";" + imagePlatform) &&
              line.contains(";" + imageTag) &&
              line.contains(";" + imageAbi)) {
//            System.out.println("  added image!");
            images.add(line);
          }
        }
      }).start();

      process.waitFor();
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


  protected boolean create(final AndroidSDK sdk) throws IOException {
    File sketchbookFolder = processing.app.Base.getSketchbookFolder();
    File androidFolder = new File(sketchbookFolder, "android");
    if (!androidFolder.exists()) androidFolder.mkdir();    
    File avdPath = new File(androidFolder, "avd/" + name);

    final String[] cmd = new String[] {
        sdk.getAvdManagerPath(),
        "create", "avd",
        "-n", name,
        "-k", image,
        "-c", DEFAULT_SDCARD_SIZE,
        "-d", device,
        "-p", avdPath.getAbsolutePath(),
        "-f"
    };
    
    ProcessBuilder pb = new ProcessBuilder(cmd);
    
    if (Base.DEBUG) {
      System.out.println(processing.core.PApplet.join(cmd, " "));
    }
    
    // avdmanager create avd -n "Wear-Processing-0254" -k "system-images;android-25;google_apis;x86" -c 64M

    // Set the list to null so that exists() will check again
    avdList = null;

    Map<String, String> env = pb.environment();
    env.clear();
    env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
    pb.redirectErrorStream(true);

    try {
      process = pb.start();

      // Passes 'no' to "Do you wish to create a custom hardware profile [no]"
      OutputStream os = process.getOutputStream();
      PrintWriter pw = new PrintWriter(new OutputStreamWriter(os));
      pw.println("no");
      pw.flush();
      pw.close();
      os.flush();
      os.close();

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
      //System.err.println(createAvdResult);
    } catch (final InterruptedException ie) {
      ie.printStackTrace(); 
    } finally {
      process.destroy();
    }

    return false;
  }

//  static public boolean checkFirstAVD(AndroidSDK sdk) throws IOException {
//    if (avdList == null) {
//      list(sdk);
//    }
//    if(avdList != null && avdList.size() > 0) {
//      return false;
//    }
//    return true;
//  }


//  static public boolean ensureProperAVD(final Frame window, final AndroidMode mode,
//      final AndroidSDK sdk, boolean wear) {
//    try {
//      AVD avd = wear ? watchAVD : phoneAVD;
//      if (avd.exists(sdk)) {
//        return true;
//      }
//      if (avd.badness()) {
//        AndroidUtil.showMessage(AndroidMode.getTextString("android_avd.error.cannot_load_avd_title"),
//                                AndroidMode.getTextString("android_avd.error.cannot_load_avd_body"));
//        return false;
//      }
//      if (!avd.hasImages(sdk)) {
//        // Check that the AVD for the other kind of device has been already
//        // downloaded, and if so, the downloader should not ask for an
//        // ABI again.
//        AVD other = wear ? phoneAVD : watchAVD;
//        boolean ask = !other.hasImages(sdk);
////        boolean res = AndroidSDK.locateSysImage(window, mode, wear, ask);
////        if (!res) {
////          return false;
////        } else {
////          avd.refreshImages(sdk);
////        }
//      }
//      if (avd.create(sdk)) {
//        return true;
//      }
//    } catch (final Exception e) {
//      e.printStackTrace();
//      AndroidUtil.showMessage(AndroidMode.getTextString("android_avd.error.cannot_create_avd_title"),
//                              AndroidMode.getTextString("android_avd.error.cannot_create_avd_body", AndroidBuild.TARGET_SDK));
//    }
//    return false;
//  }
}
