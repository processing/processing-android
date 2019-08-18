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
import processing.app.ui.Toolkit;
import processing.core.PApplet;

import javax.swing.*;
import java.awt.Frame;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Vector;


public class AVD {
  final static private int FONT_SIZE = Toolkit.zoom(11);
  final static private int TEXT_MARGIN = Toolkit.zoom(8);
  final static private int TEXT_WIDTH = Toolkit.zoom(300);
  final static public String DEFAULT_ABI = "x86";

  public final static String DEFAULT_PHONE_PORT = "5566";
  public final static String DEFAULT_WEAR_PORT = "5576";

  private static final String GETTING_START_TUT_URL =
          "http://android.processing.org/tutorials/getting_started/index.html";

  static final String DEFAULT_SDCARD_SIZE = "64M";

  private String image;

  /**
   * Name of this avd.
   */
  protected String name;

  protected String device;
  protected String skin;
  protected int type;

  static ArrayList<String> avdList;
  static ArrayList<String> avdTypeList;
  static ArrayList<String> badList;

  private static Process process;

  public AVD(final String name, final String device, final String image) {
    this.name = name;
    this.device = device;
    this.image = image;
  }


  public String getName() {
    return name;
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
    } catch (final InterruptedException ie) {
    } finally {
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
      env.put("JAVA_HOME", Platform.getJavaHome().getCanonicalPath());
      pb.redirectErrorStream(true);
      process = pb.start();

      StringWriter outWriter = new StringWriter();
      new StreamPump(process.getInputStream(), "out: ").addTarget(outWriter).start();
      process.waitFor();
      String[] lines = PApplet.split(outWriter.toString(), '\n');

      if (process.exitValue() == 0) {
        for (int i = 0; i < lines.length; i++) {
          String line = lines[i];
          if (line.contains("id:")) {
            String id = line.substring(line.indexOf('"'));
            line = lines[++i];
            String name = line.substring(line.indexOf(": ") + 1);
            line = lines[++i];
            if (line.contains("OEM : Generic")) continue;
            line = lines[++i];
            if (line.contains("Tag :")) {
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
    res.add(phoneDevices);
    res.add(phoneIds);
    res.add(wearDevices);
    res.add(wearIds);
    return res;
  }

  static protected boolean checkInstalled(Vector<String> image, Vector<Vector<String>> imageList) {
    for(Vector<String> item : imageList) {
      if(item.get(0).equals(image.get(0))) {
        return true;
      }
    }
    return false;
  }

  //Return installed images
  static protected Vector<Vector<String>> listImages(AndroidSDK sdk, boolean wear) {
    Vector<Vector<String>> imageList = new Vector<Vector<String>>();
    try {
      ProcessBuilder pb = new ProcessBuilder(sdk.getSDKManagerPath(), "--list");
      Map<String, String> env = pb.environment();
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
          if (line.contains("system-images")) {
            String[] imageProps = line.split(";");
            //Search for only supported ABI . Mac and Intel other Intel will have x86
            //AMD will have arm
            String abi = getSupportedABI();
            //accept only x86 and not x86_64
            if (imageProps[3].substring(0, 3).equals(abi) && (!imageProps[3].contains("_64") ||
                    imageProps[3].contains("armeabi-v7a")))  {
              Vector<String> image = new Vector<String>();
              image.add(imageProps[1]); //API Level
              image.add(imageProps[2]); //Tag
              image.add(imageProps[3].substring(0, 3)); //ABI
              if (available) image.add("Not Installed");
              else { //Add to return list only if it is already isntalled
                image.add("Installed"); //installed status
                if (wear && imageProps[2].equals("android-wear") &&
                        !checkInstalled(image, imageList)) imageList.add(image);
                if (!wear && imageProps[2].equals("google_apis") &&
                        !checkInstalled(image, imageList)) imageList.add(image);
              }
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
      ProcessBuilder pb = new ProcessBuilder(sdk.getAvdManagerPath(), "delete", "avd", "-n", name);
      Map<String, String> env = pb.environment();
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


  static protected boolean exists(final AndroidSDK sdk, String name) throws IOException {
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

  static public int showEmulatorNotFoundDialog() {
    String htmlString = "<html> " +
            "<head> <style type=\"text/css\">" +
            "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
            "margin: " + TEXT_MARGIN + "px; " +
            "width: " + TEXT_WIDTH + "px }" +
            "</style> </head>";
    String message = htmlString + "<body><p>"
            +AndroidMode.getTextString("android_avd.error.selected_emu_not_found_body")
            +"</p></body></html>";
    String title = AndroidMode.getTextString("android_avd.error.selected_emu_not_found_title");
    String options[] = new String[] {"Continue", "Cancel"};
    int result = JOptionPane.showOptionDialog(null, message, title,
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

  protected static String downloadImage(AndroidSDK sdk,AndroidEditor editor,AndroidMode mode) throws Error{
    String API = sdk.getAvailPlatforms().get(0);
    String ABI = getSupportedABI();
    String TAG = "google_apis";
    String imageName = "system-images;"+ API+ ";"+ TAG+ ";"+ ABI;
    try {
      boolean result = AndroidSDK.downloadSysImage(editor, mode, false, ABI, API,TAG);
      if(!result){
        throw new Error("Image Could not be downloaded");
      }
    } catch (AndroidSDK.BadSDKException | AndroidSDK.CancelException e) {
      e.printStackTrace();
    }
    return imageName;

  }

  protected boolean create(final AndroidSDK sdk) throws IOException {
    File sketchbookFolder = processing.app.Base.getSketchbookFolder();
    File androidFolder = new File(sketchbookFolder, "android");
    if (!androidFolder.exists()) androidFolder.mkdir();
    File avdPath = new File(androidFolder, "avd/" + name);

    final String[] cmd = new String[]{
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
      //reload the avdList array
      AVD.list(sdk);
      process.destroy();
    }

    return false;
  }
}