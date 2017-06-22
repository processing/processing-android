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
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;

import java.awt.Frame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
      "http://android.processing.org/tutorials/command_line/index.html";  
  
  static private final String AVD_LOAD_TITLE =
    "Could not load the AVD.";
  static private final String AVD_LOAD_MESSAGE =
    "This could mean that the Android tools need to be updated,<br>" +
    "or that the Processing AVD should be deleted (it will<br>" +
    "automatically re-created the next time you run Processing).<br><br>" +
    "You can use the SDK command-line tools to update the SDK or list<br>" +
    "the current AVDs, check <a href=\"" + COMMAND_LINE_TUT_URL + "\">this online tutorial</a> for more info.";

  private static final String GETTING_START_TUT_URL = 
      "http://android.processing.org/tutorials/getting_started/index.html";    
  
  static private final String AVD_TARGET_TITLE =
    "The SDK is not properly instaled";
  static private final String AVD_TARGET_MESSAGE =
      "Please re-read the installation instructions for Processing<br>" +
      "found in <a href=\"" + GETTING_START_TUT_URL + "\">this online tutorial</a>.";
  
  static final String DEFAULT_SDCARD_SIZE = "64M";
  
  static final String DEFAULT_SKIN = "WVGA800";
  static final String WEAR_SKIN = "AndroidWearSquare";
  
  /** Name of this avd. */
  protected String name;

  /** "android-7" or "Google Inc.:Google APIs:7" */
  protected String target;

  static ArrayList<String> avdList;
  static ArrayList<String> badList;
//  static ArrayList<String> skinList;

  private Map<String, String> preferredAbi = new HashMap<>(30);
  private List<String> abiList = new ArrayList<>();
  private String skin;

  /** Default virtual device used by Processing. */
  static public final AVD mobileAVD =
    new AVD("Processing-0" + Base.getRevision(),
            AndroidBuild.target_platform, SysImageDownloader.SYSTEM_IMAGE_TAG, DEFAULT_SKIN);
//            "Google Inc.:Google APIs:" + AndroidBuild.sdkVersion);

  /** Default virtual wear device used by Processing. */
  static public final AVD wearAVD =
    new AVD("Processing-Wear-0" + Base.getRevision(),
            AndroidBuild.target_platform, SysImageDownloader.SYSTEM_IMAGE_WEAR_TAG, WEAR_SKIN);  
  
  public AVD(final String name, final String target, 
      final String tag, final String skin) {
    this.name = name;
    this.target = target;
    this.skin = skin;
    initializeAbiList(tag);
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


  static protected void list(final AndroidSDK sdk) throws IOException {
    try {
      avdList = new ArrayList<String>();
      badList = new ArrayList<String>();
      ProcessResult listResult =
        new ProcessHelper(sdk.getAndroidToolPath(), "list", "avds").execute();
      if (listResult.succeeded()) {
        boolean badness = false;
        for (String line : listResult) {
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
        System.err.println(listResult);
      }
    } catch (final InterruptedException ie) { }
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

  
  protected void initTargets(final AndroidSDK sdk) throws IOException {
    preferredAbi.clear();
    final String[] list_abi = {
        sdk.getAndroidToolPath(),
        "list", "targets"
      };

    ProcessHelper p = new ProcessHelper(list_abi);
    try {
      final ProcessResult abiListResult = p.execute();
      
      String api = null;
      String[] abis = null;
      for (String line : abiListResult) {        
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
    }
  }
  
  
  protected boolean noTargets(final AndroidSDK sdk) throws IOException {
    initTargets(sdk); 
//    return preferredAbi.size() == 0;
    return preferredAbi.get(AndroidBuild.target_sdk) == null;
  }
  

  protected boolean create(final AndroidSDK sdk) throws IOException {
    initTargets(sdk);

    final String[] params = {
      sdk.getAndroidToolPath(),
      "create", "avd",
      "-n", name,
      "-t", target,
      "-c", DEFAULT_SDCARD_SIZE,
      "-s", skin,
      "--abi", preferredAbi.get(AndroidBuild.target_sdk)
    };
        
    // sdk/tools/android create avd -n "Wear-Processing-0254" -t android-23 -c 64M -s AndroidWearSquare --abi android-wear/x86
        
    // Set the list to null so that exists() will check again
    avdList = null;
    
    ProcessHelper p = new ProcessHelper(params);
    try {
      // Passes 'no' to "Do you wish to create a custom hardware profile [no]"
      
      final ProcessResult createAvdResult = p.execute("no");
      if (createAvdResult.succeeded()) {
        return true;
      }
      if (createAvdResult.toString().contains("Target id is not valid")) {
        // They didn't install the Google APIs
        AndroidMode.showMessage(AVD_TARGET_TITLE, AVD_TARGET_MESSAGE);
      } else {
        // Just generally not working
        AndroidMode.showMessage(AVD_CREATE_TITLE, 
                                String.format(AVD_CREATE_MESSAGE, AndroidBuild.target_sdk));
        System.out.println(createAvdResult);
      }
      //System.err.println(createAvdResult);
    } catch (final InterruptedException ie) { 
      ie.printStackTrace(); 
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
          AndroidMode.showMessage(AVD_LOAD_TITLE, AVD_LOAD_MESSAGE);
          return false;
        }
        if (wearAVD.noTargets(sdk)) {
          boolean res = AndroidSDK.locateSysImage(window, mode, true);          
          if (!res) {
            return false;  
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
          AndroidMode.showMessage(AVD_LOAD_TITLE, AVD_LOAD_MESSAGE);
          return false;
        }
        if (mobileAVD.noTargets(sdk)) {
          boolean res = AndroidSDK.locateSysImage(window, mode, false);
          if (!res) {
            return false;  
          }
        }        
        if (mobileAVD.create(sdk)) {
          return true;
        }
      }
    } catch (final Exception e) {
      e.printStackTrace();
      AndroidMode.showMessage(AVD_CREATE_TITLE, 
                              String.format(AVD_CREATE_MESSAGE, AndroidBuild.target_sdk));
    }
    return false;
  }
}
