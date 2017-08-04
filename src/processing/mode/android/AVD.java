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


public class AVD {
  static private final String AVD_CREATE_TITLE =
    "Could not create the AVD";

  static private final String AVD_CREATE_MESSAGE =
    "The default Android emulator could not be set up. Make sure<br>" +
    "that the Android SDK is installed properly, and that the<br>" +
    "system images are installed for level %s.<br>" +
    "(Between you and me, occasionally, this error is a red herring,<br>" +
    "and your sketch may be launching shortly.)";

  private static final String COMMAND_LINE_TUT_URL = 
      "https://developer.android.com/studio/command-line/avdmanager.html";  
  
  static private final String AVD_LOAD_TITLE =
    "Could not load the AVD.";
  static private final String AVD_LOAD_MESSAGE =
    "This could mean that the Android tools need to be updated,<br>" +
    "or that the Processing AVD should be deleted (it will<br>" +
    "automatically re-created the next time you run Processing).<br><br>" +
    "You can use the avdmanager command line tool to create AVDs manually and list " +
    "the current AVDs, check <a href=\"" + COMMAND_LINE_TUT_URL + "\">this online tutorial</a> for more info.";

  private static final String GETTING_START_TUT_URL = 
      "http://android.processing.org/tutorials/getting_started/index.html";    
  
  static private final String AVD_TARGET_TITLE =
    "The SDK is not properly instaled";
  static private final String AVD_TARGET_MESSAGE =
      "Please re-read the installation instructions for Processing<br>" +
      "found in <a href=\"" + GETTING_START_TUT_URL + "\">this online tutorial</a>.";
  
  static final String DEFAULT_SDCARD_SIZE = "64M";
  
  static final String DEVICE_DEFINITION = "Nexus One";
  static final String DEVICE_SKIN = "480x800";
  
  static final String DEVICE_WEAR_DEFINITION = "wear_square_280_280dpi";
  static final String DEVICE_WEAR_SKIN = "280x280";  
  
  /** Name of this avd. */
  protected String name;

  protected String device;
  protected String skin;

  static ArrayList<String> avdList;
  static ArrayList<String> badList;
//  static ArrayList<String> skinList;
  
  /** "system-images;android-25;google_apis;x86" */
  static ArrayList<String> wearImages;
  static ArrayList<String> phoneImages;
  
  private static Process process;

  /** Default virtual device used by Processing. */
  static public final AVD mobileAVD =
    new AVD("processing-phone",
            DEVICE_DEFINITION, DEVICE_SKIN);

  /** Default virtual wear device used by Processing. */
  static public final AVD wearAVD =
    new AVD("processing-watch",
            DEVICE_WEAR_DEFINITION, DEVICE_WEAR_SKIN);

  
  public AVD(final String name, final String device, final String skin) {
    this.name = name;
    this.device = device;
    this.skin = skin;
  }


  static protected void list(final AndroidSDK sdk) throws IOException {
    try {
      avdList = new ArrayList<String>();
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
        boolean badness = false;
        for (String line : lines) {
          String[] m = PApplet.match(line, "\\s+Name\\:\\s+(\\S+)");
          if (m != null) {
            if (!badness) {
//              System.out.println("good: " + m[1]);
              avdList.add(m[1]);
            } else {
//              System.out.println("bad: " + m[1]);
              badList.add(m[1]);
            }
//          } else {
//            System.out.println("nope: " + line);
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
    if (phoneImages == null) {
      phoneImages = new ArrayList<String>();
      getImages(phoneImages, sdk, SysImageDownloader.SYSTEM_IMAGE_TAG);
    }
    return !phoneImages.isEmpty();
  }
  

  protected void refreshImages(final AndroidSDK sdk) throws IOException {
    phoneImages = new ArrayList<String>();
    getImages(phoneImages, sdk, SysImageDownloader.SYSTEM_IMAGE_TAG);
  } 
  
  
  protected boolean hasWearImages(final AndroidSDK sdk) throws IOException {
    if (wearImages == null) {
      wearImages = new ArrayList<String>();
      getImages(wearImages, sdk, SysImageDownloader.SYSTEM_IMAGE_WEAR_TAG);
    }
    return !wearImages.isEmpty();
  }  
  

  protected void refreshWearImages(final AndroidSDK sdk) throws IOException {
    wearImages = new ArrayList<String>();
    getImages(wearImages, sdk, SysImageDownloader.SYSTEM_IMAGE_WEAR_TAG);
  } 
  
  
  protected void getImages(final ArrayList<String> images, final AndroidSDK sdk, 
      final String imageTag) throws IOException {
    final String[] cmd = new String[] {
        sdk.getAvdManagerPath(),
        "create", "avd",
        "-n", "dummy",
        "-k", "dummy"
      };      
    
    // Dummy avdmanager creation command to get the list of installed images
    // TODO : Find a better way to get the list of installed images
    ProcessBuilder pb = new ProcessBuilder(cmd);
    
    //if (Base.DEBUG) {
    System.out.println(processing.core.PApplet.join(cmd, " "));
  //}    

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
          System.out.println("DUMMY ---> " + line);
          if (images != null && line.contains(AndroidBuild.TARGET_PLATFORM) &&
              line.contains(imageTag))
            System.out.println("  added!");
            images.add(line);
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
    if (Preferences.get("android.system.image.type") == null)
      Preferences.set("android.system.image.type", "x86"); // Prefer x86

    if (this.name.contains("phone")) {
      for (String image : phoneImages) {
        if (image.contains(Preferences.get("android.system.image.type")))
          return image;
      }
    } else {
      for (String image : wearImages) {
        if (image.contains(Preferences.get("android.system.image.type")))
          return image;
      }
    }

    // Could not find any suitable package
    return "null";
  }


  protected boolean create(final AndroidSDK sdk) throws IOException {
    //initTargets(sdk);
    File sketchbookFolder = processing.app.Base.getSketchbookFolder();
    File androidFolder = new File(sketchbookFolder, "android");
    if (!androidFolder.exists()) androidFolder.mkdir();    
    File avdPath = new File(androidFolder, "avd/" + name);
    
    final String[] cmd = new String[] {
        sdk.getAvdManagerPath(),
        "create", "avd",
        "-n", name,      
        "-k", getSdkId(),
        "-c", DEFAULT_SDCARD_SIZE,
        "-d", device,
        "-p", avdPath.getAbsolutePath(),
        "-f"
    };
    
    ProcessBuilder pb = new ProcessBuilder(cmd);
    
    //if (Base.DEBUG) {
      System.out.println(processing.core.PApplet.join(cmd, " "));
    //}
    
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
        File configFile = new File(avdPath, "config.ini");
        if (configFile.exists()) {
          try (PrintWriter output = new PrintWriter(new FileWriter(configFile.getAbsolutePath(), true))) {
            output.printf("%s\r\n", "skin.name=" + skin);
          } 
          catch (Exception e) {}
        }
        return true;
      }

      if (outWriter.toString().contains("Package path is not valid")) {
        // They didn't install the Google APIs
        AndroidUtil.showMessage(AVD_TARGET_TITLE, AVD_TARGET_MESSAGE);
      } else {
        // Just generally not working
        AndroidUtil.showMessage(AVD_CREATE_TITLE, 
                                String.format(AVD_CREATE_MESSAGE, AndroidBuild.TARGET_SDK));
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

  
  static public String getPort(boolean wear) {
    if (wear) {
      return EmulatorController.WEAR_PORT;
    } else {
      return EmulatorController.DEFAULT_PORT;
    }
  }
  

  static public boolean ensureProperAVD(final Frame window, final AndroidMode mode, 
      final AndroidSDK sdk, boolean wear) {
    try {      
      if (wear) {
        if (wearAVD.exists(sdk)) {
          return true;
        }
        if (wearAVD.badness()) {
          AndroidUtil.showMessage(AVD_LOAD_TITLE, AVD_LOAD_MESSAGE);
          return false;
        }
        if (!wearAVD.hasWearImages(sdk)) {
          boolean res = AndroidSDK.locateSysImage(window, mode, true);
          if (!res) {
            return false;
          } else {
            wearAVD.refreshWearImages(sdk);
          }
        }
        if (wearAVD.create(sdk)) {
          return true;
        }
      } else {
        if (mobileAVD.exists(sdk)) {
          return true;
        }
        if (mobileAVD.badness()) {
          AndroidUtil.showMessage(AVD_LOAD_TITLE, AVD_LOAD_MESSAGE);
          return false;
        }
        if (!mobileAVD.hasImages(sdk)) {
          boolean res = AndroidSDK.locateSysImage(window, mode, false);
          if (!res) {
            return false;
          } else {
            mobileAVD.refreshImages(sdk);
          }
        }
        if (mobileAVD.create(sdk)) {
          return true;
        }
      }
    } catch (final Exception e) {
      e.printStackTrace();
      AndroidUtil.showMessage(AVD_CREATE_TITLE, 
                              String.format(AVD_CREATE_MESSAGE, AndroidBuild.TARGET_SDK));
    }
    return false;
  }
  
  
  //////////////////////////////////////////////////////////////////////////////
  // To remove:
  
  /*

  private Map<String, String> preferredAbi = new HashMap<>(30);
  private List<String> abiList = new ArrayList<>();
 
  protected boolean noTargets(final AndroidSDK sdk) throws IOException {
    initTargets(sdk); 
//    return preferredAbi.size() == 0;
    return preferredAbi.get(AndroidBuild.TARGET_SDK) == null;
  }
  
  
  private void initializeAbiList(String tag) {
    if (abiList.size() == 0) {
      // The order in this list determines the preference of one abi over the other
      abiList.add(tag + "/x86");
      abiList.add(tag + "/x86_64");
      abiList.add(tag + "/armeabi-v7a");
//    abiList.add("google_apis/x86");      
//    abiList.add("google_apis/x86_64");      
//    abiList.add("google_apis/armeabi-v7a");      
    }
  }
  
  protected void initTargets(final AndroidSDK sdk) throws IOException {
    preferredAbi.clear();
    ProcessBuilder pb = new ProcessBuilder(sdk.getAvdManagerPath(), "list", "target");

    Map<String, String> env = pb.environment();
    env.clear();
    env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
    pb.redirectErrorStream(true);

    process = pb.start();
    InputStream stdout = process.getInputStream();
    BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));

    try {
      process.waitFor();
      
      String api = null;
      String[] abis = null;
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.equals("")) continue;

        if (line.indexOf("API level") == 0) {
          String[] m = line.split(":");
          if (1 < m.length) {
            api = m[1];
            api = api.trim();            
          }
        }
        
        if (line.indexOf("Tag/ABIs") == 0) {
          String[] m = line.split(":");
          if (1 < m.length) {
            String str = m[1];
            abis = str.split(",");
            for (int i = 0; i < abis.length; i++) {
              abis[i] = abis[i].trim();  
            }
          }
        }
        
        if (api != null && abis != null) {
          for (String abi: abis) {
            if (abiList.indexOf(abi) == -1) continue;
            if (preferredAbi.get(api) == null) {
              preferredAbi.put(api, abi);
            } else if (abiList.indexOf(preferredAbi.get(api)) < abiList.indexOf(abi)) {
              preferredAbi.put(api, abi);
            }            
          }
          api = null;
          abis = null; 
        }
      }
    } catch (InterruptedException e) {
    } finally {
      process.destroy();
    }
  }
  */
}
