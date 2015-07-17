package processing.mode.android;

import processing.app.Base;
//import processing.app.Preferences;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;

import java.io.IOException;
import java.util.ArrayList;


public class AVD {
  static private final String AVD_CREATE_PRIMARY =
    "An error occurred while running “android create avd”";

  static private final String AVD_CREATE_SECONDARY =
    "The default Android emulator could not be set up. Make sure<br>" +
    "that the Android SDK is installed properly, and that the<br>" +
    "Android and Google APIs are installed for level %s.<br>" +
    "(Between you and me, occasionally, this error is a red herring,<br>" +
    "and your sketch may be launching shortly.)";

  static private final String AVD_LOAD_PRIMARY =
    "There is an error with the Processing AVD.";
  static private final String AVD_LOAD_SECONDARY =
    "This could mean that the Android tools need to be updated,<br>" +
    "or that the Processing AVD should be deleted (it will<br>" +
    "automatically re-created the next time you run Processing).<br>" +
    "Open the Android SDK Manager (underneath the Android menu)<br>" + 
    "to check for any errors.";

  static private final String AVD_TARGET_PRIMARY =
    "The Google APIs are not installed properly";
  static private final String AVD_TARGET_SECONDARY =
    "Please re-read the installation instructions for Processing<br>" +
    "found at http://android.processing.org and try again.";

  static final String DEFAULT_SKIN = "WVGA800";
  static final String DEFAULT_SDCARD_SIZE = "64M";

  /** Name of this avd. */
  protected String name;

  /** "android-7" or "Google Inc.:Google APIs:7" */
  protected String target;
  
  /** x86, x86_64 or armeabi **/
  protected String abi;
  
  protected VirtualDevice virtualDevice;
  
  public static final String PREF_KEY_ABI = "android.sdk.abi";
  public static final String[] ABI = {"armeabi", "x86", "x86_64"};

  /** Default virtual device used by Processing. */
  static public AVD defaultAVD;
//            "Google Inc.:Google APIs:" + AndroidBuild.sdkVersion);

  static ArrayList<VirtualDevice> avdList;
  static ArrayList<VirtualDevice> badList;
  
  
  private static class VirtualDevice {
      public String name;
      public String target;
      public String abi;
      public VirtualDevice(String name, String target, String abi) {
          this.name = name;
          this.target = target;
          this.abi = abi;
      }

      @Override
      public boolean equals(Object o) {
          VirtualDevice device = (VirtualDevice) o;
          if (device.name.equals(name) && device.target.equals(target)
                  && device.abi.equals(abi)) {
              return true;
          }
          return false;
      }
  }
  

  public AVD(String name, String target, String abi) {
    this.name = name;
    this.target = target;
    this.abi = abi;
    virtualDevice = new VirtualDevice(name, target, abi);
  }


  static protected void list(final AndroidSDK sdk) throws IOException {
    try {
      avdList = new ArrayList<VirtualDevice>();
      badList = new ArrayList<VirtualDevice>();
      ProcessResult listResult =
        new ProcessHelper(sdk.getAndroidToolPath(), "list", "avds").execute();
      if (listResult.succeeded()) {
        boolean badness = false;
        String mTarget = null;
        String mAbi = null;
        String mName = null;
        for (String line : listResult) {
          String[] m = PApplet.match(line, "\\s+Name\\:\\s+(\\S+)");
          if (m != null) {
            mName = m[1];
            continue;
          }
              
          m = PApplet.match(line, "API\\slevel\\s([0-9]+)");
          if (m != null) {
            mTarget = m[1];
            continue;
          }
          
          m = PApplet.match(line, "\\s+Tag\\/ABI\\:\\s\\S+\\/(\\S+)");
          if (m != null) {
              mAbi = m[1];
          }
          
          if (mName != null && mTarget != null && mAbi != null) {
            VirtualDevice mVirtualDevice = new VirtualDevice(mName, mTarget, mAbi);
            mTarget = null;
            mAbi = null;
            if (!badness) {
              avdList.add(mVirtualDevice);
            } else {
              badList.add(mVirtualDevice);
            }
          }
          
          // "The following Android Virtual Devices could not be loaded:"
          if (line.contains("could not be loaded:")) {
//            System.out.println("starting the bad list");
//            System.err.println("Could not list AVDs:");
//            System.err.println(listResult);
            badness = true;
            break;
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
    virtualDevice.target = AndroidBuild.sdkVersion;
    virtualDevice.abi = abi;
    return avdList.contains(virtualDevice);
  }


  /** 
   * Return true if a member of the renowned and prestigious 
   * "The following Android Virtual Devices could not be loaded:" club. 
   * (Prestigious may also not be the right word.)
   */
  protected boolean badness() {
    return badList.contains(virtualDevice);
  }


  protected boolean create(final AndroidSDK sdk) throws IOException {
    final String[] params = {
      sdk.getAndroidToolPath(),
      "create", "avd",
      "-n", name, 
      "-t", target,
      "-c", DEFAULT_SDCARD_SIZE,
      "-s", DEFAULT_SKIN,
      "--abi", abi
    };

    // Set the list to null so that exists() will check again
    avdList = null;
    final ProcessHelper p = new ProcessHelper(params);
    try {
      // Passes 'no' to "Do you wish to create a custom hardware profile [no]"
//      System.out.println("CREATE AVD STARTING");
      final ProcessResult createAvdResult = p.execute("no");
//      System.out.println("CREATE AVD HAS COMPLETED");
      if (createAvdResult.succeeded()) {
        return true;
      }
      if (createAvdResult.toString().contains("Target id is not valid")) {
        // They didn't install the Google APIs
        Base.showWarningTiered("Android Error", AVD_TARGET_PRIMARY, AVD_TARGET_SECONDARY, null);
//        throw new IOException("Missing required SDK components");
      } else {
        // Just generally not working
//        Base.showWarning("Android Error", AVD_CREATE_ERROR, null);
        Base.showWarningTiered("Android Error", AVD_CREATE_PRIMARY,
                String.format(AVD_CREATE_SECONDARY, AndroidBuild.sdkVersion), null);
        System.out.println(createAvdResult);
//        throw new IOException("Error creating the AVD");
      }
      //System.err.println(createAvdResult);
    } catch (final InterruptedException ie) { }

    return false;
  }


  static public boolean ensureProperAVD(final AndroidSDK sdk, final String abi) {
    try {
      defaultAVD = new AVD("Processing-0" + Base.getRevision() + "-" + AndroidBuild.sdkVersion +
              "-" + abi,
          "android-" + AndroidBuild.sdkVersion, abi);
      if (defaultAVD.exists(sdk)) {
//        System.out.println("the avd exists");
        return true;
      }
//      if (badList.contains(defaultAVD)) {
      if (defaultAVD.badness()) {
//        Base.showWarning("Android Error", AVD_CANNOT_LOAD, null);
        Base.showWarningTiered("Android Error", AVD_LOAD_PRIMARY, AVD_LOAD_SECONDARY, null);
        return false;
      }
      if (defaultAVD.create(sdk)) {
//        System.out.println("the avd was created");
        return true;
      }
    } catch (final Exception e) {
        e.printStackTrace();
//      Base.showWarning("Android Error", AVD_CREATE_ERROR, e);
      Base.showWarningTiered("Android Error", AVD_CREATE_PRIMARY,
              String.format(AVD_CREATE_SECONDARY, AndroidBuild.sdkVersion), null);
    }
    System.out.println("at bottom of ensure proper");
    return false;
  }
}
