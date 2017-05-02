/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-16 The Processing Foundation
 Copyright (c) 2009-12 Ben Fry and Casey Reas

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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;

import processing.app.Base;
import processing.app.Library;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.Sketch;
import processing.app.SketchException;
import processing.app.Util;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;
import processing.mode.java.JavaBuild;
import processing.mode.java.preproc.SurfaceInfo;

import java.io.*;
import java.util.HashMap;

class AndroidBuild extends JavaBuild {
  static public final int FRAGMENT  = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE = 2;
  static public final int VR = 3;
  
  static public final String DEFAULT_COMPONENT = "app";
  
  static private final String FRAGMENT_ACTIVITY_TEMPLATE = "FragmentActivity.java.tmpl";
  static private final String WALLPAPER_SERVICE_TEMPLATE = "WallpaperService.java.tmpl";
  static private final String WATCHFACE_SERVICE_TEMPLATE = "WatchFaceService.java.tmpl";
  static private final String VR_ACTIVITY_TEMPLATE = "VRActivity.java.tmpl";
  static private final String HANDHELD_ACTIVITY_TEMPLATE = "HandheldActivity.java.tmpl";
  static private final String HANDHELD_MANIFEST_TEMPLATE = "HandheldManifest.xml.tmpl";
//  static private final String HANDHELD_LAYOUT_TEMPLATE = "HandheldLayout.xml.tmpl";
  static private final String WEARABLE_DESCRIPTION_TEMPLATE = "WearableDescription.xml.tmpl";
  static private final String LAYOUT_ACTIVITY_TEMPLATE = "LayoutActivity.xml.tmpl";
  static private final String STYLES_FRAGMENT_TEMPLATE = "StylesFragment.xml.tmpl";
  static private final String STYLES_VR_TEMPLATE = "StylesVR.xml.tmpl";
  static private final String XML_WALLPAPER_TEMPLATE = "XMLWallpaper.xml.tmpl";
  static private final String STRINGS_WALLPAPER_TEMPLATE = "StringsWallpaper.xml.tmpl";
  static private final String XML_WATCHFACE_TEMPLATE = "XMLWatchFace.xml.tmpl";
  static private final String ANT_BUILD_TEMPLATE = "Build.xml.tmpl";
  
  // Gradle files
  static private final String TOP_GRADLE_BUILD_TEMPLATE = "TopBuild.gradle.tmpl";
  static private final String GRADLE_SETTINGS_TEMPLATE = "Settings.gradle.tmpl";
  static private final String APP_GRADLE_BUILD_TEMPLATE = "FragmentBuild.gradle.tmpl";
  static private final String HANDHELD_GRADLE_BUILD_TEMPLATE = "HandheldBuild.gradle.tmpl";
  static private final String WEARABLE_GRADLE_BUILD_TEMPLATE = "WearableBuild.gradle.tmpl";
  static private final String VR_GRADLE_BUILD_TEMPLATE = "VRBuild.gradle.tmpl";
  
  // TODO: ask base package name when exporting signed apk
  //  static final String basePackage = "changethispackage.beforesubmitting.tothemarket";
  static final String basePackage = "processing.test";
  
  // Minimum SDK levels required for each app component
  // https://source.android.com/source/build-numbers.html
  // We should use 17 (4.2) as minimum for fragment and wallpaper at some point, 
  // once combined usage of all previous versions is falls below 5%:
  // http://developer.android.com/about/dashboards/index.html
  // because 17 give us getRealSize and getRealMetrics:
  // http://developer.android.com/reference/android/view/Display.html#getRealSize(android.graphics.Point)
  // http://developer.android.com/reference/android/view/Display.html#getRealMetrics(android.util.DisplayMetrics)
  // which allows us to exactly determine the size of the screen.
  static public final String min_sdk_fragment  = "16"; // Jelly Bean (4.1)
  static public final String min_sdk_wallpaper = "16"; // 
  static public final String min_sdk_gvr       = "19"; // KitKat (4.4)
  static public final String min_sdk_handheld  = "21"; // Lollipop (5.0)
  static public final String min_sdk_watchface = "23"; // Marshmallow (6.0)
  
  // Target SDK is stored in the preferences file.
  static public String target_sdk;  
  static public String target_platform;
  static {
    target_sdk = Preferences.get("android.sdk.target");
    if (PApplet.parseInt(target_sdk) < 25) { // Must be Nougat (7.1.1) or higher
      target_sdk = "25"; 
      Preferences.set("android.sdk.target", target_sdk);
    }
    target_platform = "android-" + target_sdk;
  }  

  // Versions of Support, AppCompat, Wear and VR in use
  // All of these are hard-coded, as the target_sdk. Should obtained from the
  // repository files? Or users being able to change them in the preferences 
  // file?
  static public final String support_version = "25.2.0";
  static public final String play_services_version = "10.2.0";  
  static public final String wear_version = "2.0.0";
  static public final String gvr_sdk_version = "1.40.0";
  
  private boolean runOnEmulator = false;
  private int appComponent = FRAGMENT;
  
  private String renderer = "";
  
  private final AndroidSDK sdk;
  private final File coreZipFile;

  /** whether this is a "debug" or "release" build */
  private String target;
  private Manifest manifest;

  /** temporary folder safely inside a 8.3-friendly folder */
  private File tmpFolder;

  /** build.xml file for this project */
  private File buildFile;


  public AndroidBuild(final Sketch sketch, final AndroidMode mode, 
      final int appComp, final boolean emu) {
    super(sketch);

    runOnEmulator = emu;
    appComponent = appComp;
    sdk = mode.getSDK();
    coreZipFile = mode.getCoreZipLocation();
  }
  
//  public static void setSdkTarget(AndroidSDK.SDKTarget target, Sketch sketch) {
//    sdkName = target.name;
//    sdkVersion = Integer.toString(target.version);
//    sdkTarget = "android-" + sdkVersion;
//
//    Preferences.set("android.sdk.version", target_);
//    Preferences.set("android.sdk.name", target.name);
//  }

  /**
   * Build into temporary folders (needed for the Windows 8.3 bugs in the Android SDK).
   * @param target "debug" or "release"
   * @throws SketchException
   * @throws IOException
   */
  public File build(String target) throws IOException, SketchException {
    this.target = target;
        
    // determine target id needed by library projects
    String targetID = getTargetID();

    if (appComponent == WATCHFACE && !runOnEmulator) {
      // We are building a watchface not to run on the emulator. We need the
      // handheld app:
      // https://developer.android.com/training/wearables/apps/creating.html
      // so the watchface can be uninstalled from the phone, and can be
      // published on Google Play.
      File wearFolder = createProject(targetID, true, true);
      if (wearFolder == null) return null;
      if (!antBuild()) return null;      
      File folder = createHandheldProject(targetID, wearFolder, null);
      if (folder == null) return null;
      if (!antBuild()) return null;
      return folder;      
    } else {
      File folder = createProject(targetID, true, false);
      if (folder != null) {
        if (!antBuild()) {
          return null;
        }
      }
      return folder;      
    }    
  }


  /**
   * Tell the PDE to not complain about android.* packages and others that are
   * part of the OS library set as if they're missing.
   */
  protected boolean ignorableImport(String pkg) {
    if (pkg.startsWith("android.")) return true;
    if (pkg.startsWith("java.")) return true;
    if (pkg.startsWith("javax.")) return true;
    if (pkg.startsWith("org.apache.http.")) return true;
    if (pkg.startsWith("org.json.")) return true;
    if (pkg.startsWith("org.w3c.dom.")) return true;
    if (pkg.startsWith("org.xml.sax.")) return true;

    if (pkg.startsWith("processing.core.")) return true;
    if (pkg.startsWith("processing.data.")) return true;
    if (pkg.startsWith("processing.event.")) return true;
    if (pkg.startsWith("processing.opengl.")) return true;

    return false;
  }


  /**
   * Create an Android project folder, and run the preprocessor on the sketch.
   * Populates the 'src' folder with Java code, and 'libs' folder with the
   * libraries and code folder contents. Also copies data folder to 'assets'.
   */
  public File createProject(String targetID, boolean external, boolean wear) 
      throws IOException, SketchException {
    tmpFolder = createTempBuildFolder(sketch);
    if (wear) {
      tmpFolder = new File(tmpFolder, "wear");
    }

    // Create the 'src' folder with the preprocessed code.
    srcFolder = new File(tmpFolder, "src");
    // use the src folder, since 'bin' might be used by the ant build
    binFolder = srcFolder;
    if (processing.app.Base.DEBUG) {
      Platform.openFolder(tmpFolder);
    }

    boolean rewriteManifest = false;
    if (!external) {
      // If creating an export project, then the manifest might have attributes
      // that create trouble with gradle, so we just re-write it...
      // TODO: the current manifest logic is exceeded by the complexity of the mode
      // need to rewrite.
      rewriteManifest = true;
    }
    manifest = new Manifest(sketch, appComponent, mode.getFolder(), rewriteManifest);    
    manifest.setSdkTarget(target_sdk);

    // build the preproc and get to work
    AndroidPreprocessor preproc = new AndroidPreprocessor(sketch, getPackageName());
    // On Android, this init will throw a SketchException if there's a problem with size()
    SurfaceInfo info = preproc.initSketchSize(sketch.getMainProgram());
    preproc.initSketchSmooth(sketch.getMainProgram());
    
    sketchClassName = preprocess(srcFolder, getPackageName(), preproc, false);
    if (sketchClassName != null) {
      File tempManifest = new File(tmpFolder, "AndroidManifest.xml");
            
      manifest.writeCopy(tempManifest, sketchClassName, external, target.equals("debug"));

      writeAntProps(new File(tmpFolder, "ant.properties"));
      buildFile = new File(tmpFolder, "build.xml");
      writeBuildXML(buildFile, sketch.getName());
      writeProjectProps(new File(tmpFolder, "project.properties"));
      writeLocalProps(new File(tmpFolder, "local.properties"));

      final File resFolder = new File(tmpFolder, "res");
      writeRes(resFolder);

      renderer = info.getRenderer();
      writeMainClass(srcFolder, renderer, external);

      final File libsFolder = mkdirs(tmpFolder, "libs");
      final File assetsFolder = mkdirs(tmpFolder, "assets");

      Util.copyFile(coreZipFile, new File(libsFolder, "processing-core.jar"));
      
      // Copy any imported libraries (their libs and assets),
      // and anything in the code folder contents to the project.
      copyImportedLibs(libsFolder, assetsFolder);
      copyCodeFolder(libsFolder);
      
      // Copy any system libraries needed by the project
      copyWearLib(tmpFolder, libsFolder);
      copySupportLibs(tmpFolder, libsFolder);
      if (getAppComponent() == FRAGMENT) {
        copyAppCompatLib(targetID, tmpFolder, libsFolder);   
      }
      if (getAppComponent() == VR) {
        copyGVRLibs(targetID, libsFolder);
      }
      
      // Copy the data folder (if one exists) to the project's 'assets' folder
      final File sketchDataFolder = sketch.getDataFolder();
      if (sketchDataFolder.exists()) {
        Util.copyDir(sketchDataFolder, assetsFolder);
      }

      // Do the same for the 'res' folder. The user can copy an entire res folder
      // into the sketch's folder, and it will be used in the project!      
      final File sketchResFolder = new File(sketch.getFolder(), "res");
      if (sketchResFolder.exists()) {
        Util.copyDir(sketchResFolder, resFolder);
      }
    }
    
    return tmpFolder;
  }

  
  // This creates the special activity project only needed to serve as the
  // (invisible) app on the handheld that allows to uninstall the watchface
  // from the watch.  
  public File createHandheldProject(String targetID, File wearFolder, File wearPackage) 
      throws IOException, SketchException {
    // According to these instructions:
    // https://developer.android.com/training/wearables/apps/packaging.html#PackageManually
    // we need to:
    // 1. Include all the permissions declared in the manifest file of the 
    //    wearable app in the manifest file of the mobile app    
    // 2. Ensure that both the wearable and mobile APKs have the same package name 
    //    and version number.
    // 3. Copy the signed wearable app to your handheld project's res/raw directory. 
    //    We'll refer to the APK as wearable_app.apk.
    // 4. Create a res/xml/wearable_app_desc.xml file that contains the version 
    //    and path information of the wearable app. For example:
    // 5. Add a meta-data tag to your handheld app's <application> tag to reference 
    //    the wearable_app_desc.xml file.
    
    // So, let's start by using the parent folder as the project file
    tmpFolder = wearFolder.getParentFile();
    
    // Now, we need to create the folder structure for the handheld project:
    // src
    //   package-name (same to wear app)
    //     HandheldActivity.java (this will be a dummy activity that quits right after starting up).
    // res
    //   drawable containing the app icon in 42x42 res
    //   drawable-hdpi (all the others res up to xxxhdpi)
    //   ...
    //   drawable-xxxhdpi
    //   layout
    //     activity_handheld.xml (the layout for the dummy activity)
    //   xml
    //     wearable_app_desc.xml (version and path info of the wearable app)
    //   raw
    //     wearable-apk (copied from the build process conducted in the wear folder)
    
    // Create manifest file
    String[] permissions = manifest.getPermissions(); 
    
    // Create source folder, and dummy handheld activity
    srcFolder = new File(tmpFolder, "src");    
    binFolder = srcFolder;  
    writeHandheldActivity(srcFolder, permissions);

    writeHandheldManifest(tmpFolder, "1", "1.0", permissions);
    
    // Write property and build files.
    writeAntProps(new File(tmpFolder, "ant.properties"));
    buildFile = new File(tmpFolder, "build.xml");
    writeBuildXML(buildFile, sketch.getName());
    writeProjectProps(new File(tmpFolder, "project.properties"));
    writeLocalProps(new File(tmpFolder, "local.properties"));    
    
    File libsFolder = mkdirs(tmpFolder, "libs");    
    copySupportLibs(tmpFolder, libsFolder);
    copyAppCompatLib(targetID, tmpFolder, libsFolder);
    
    final File resFolder = new File(tmpFolder, "res");        
    File layoutFolder = mkdirs(resFolder, "layout");    
    writeResLayoutMainActivity(layoutFolder);
    File valuesFolder = mkdirs(resFolder, "values");      
    writeResStylesFragment(valuesFolder);
    
    // Write icons for handheld app
    File sketchFolder = sketch.getFolder();
    writeIconFiles(sketchFolder, resFolder);    

    String apkName = copyWearApk(wearPackage, resFolder, wearFolder);
        
    // Create the wearable app description    
    writeWearableDescription(resFolder, apkName, "1", "1.0");
    
    return tmpFolder;
  }
  
  
  private void writeHandheldActivity(final File srcDirectory, String[] permissions) {
    File javaTemplate = mode.getContentFile("templates/" + HANDHELD_ACTIVITY_TEMPLATE);    
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "HandheldActivity.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    
    AndroidMode.createFileFromTemplate(javaTemplate, javaFile, replaceMap);
  }
  
  
  private void writeHandheldManifest(final File tmpFolder, String versionCode, String versionName, 
      String[] permissions) {    
    File xmlTemplate = mode.getContentFile("templates/" + HANDHELD_MANIFEST_TEMPLATE);
    File xmlFile = new File(tmpFolder, "AndroidManifest.xml");
    
    String usesPermissions = "";
    for (String name: permissions) {
      if (name.equals("WAKE_LOCK")) continue;
      usesPermissions += "    <uses-permission android:name=\"android.permission." + name + "\"/>\n"; 
    }
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());    
    replaceMap.put("@@version_code@@", versionCode);
    replaceMap.put("@@version_name@@", versionName);
    replaceMap.put("@@min_sdk@@", min_sdk_handheld);
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@uses_permissions@@", usesPermissions);
        
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap); 
  }  
  
  
  /*
  private void writeHandheldLayout(final File resFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + HANDHELD_LAYOUT_TEMPLATE);    
    File xmlFile = new File(resFolder, "layout/activity_handheld.xml");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());    

    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap);      
  }
  */
  
  private void writeWearableDescription(final File resFolder, final String apkName,
      final String versionCode, String versionName) {
    File xmlTemplate = mode.getContentFile("templates/" + WEARABLE_DESCRIPTION_TEMPLATE);
    File xmlFile = new File(resFolder, "xml/wearable_app_desc.xml");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@version_code@@", versionCode);
    replaceMap.put("@@version_name@@", versionName);
    replaceMap.put("@@apk_name@@", apkName);

    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap);  
  }
  
  protected boolean createLibraryProject(String name, String target, 
                                         String path, String pck) {
    final String[] params = {
        sdk.getAndroidToolPath(),
        "create", "lib-project",
        "--name", name,
        "--target", target,
        "--path", path,
        "--package", pck
    };

    ProcessHelper p = new ProcessHelper(params);
    ProcessResult pr;
    try {
      pr = p.execute();
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
      return false;
    }

    if (pr.succeeded()) {
      return true;
    } else {  
      System.err.println(pr.getStderr());
      Messages.showWarning("Failed to create library project", "Something wrong happened", null);
      return false;
    }    
  }
  
  protected boolean referenceLibraryProject(String target, String path, String lib) {
    final String[] params = {
        sdk.getAndroidToolPath(),
        "update", "project",
        "--target", target,
        "--path", path,
        "--library", lib
    };

    ProcessHelper p = new ProcessHelper(params);
    ProcessResult pr;
    try {
      pr = p.execute();
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
      return false;
    }

    if (pr.succeeded()) {
      return true;
    } else {  
      System.err.println(pr.getStderr());
      Messages.showWarning("Failed to add library project", "Something wrong happened", null);
      return false;
    }      
  }

  /**
   * The Android dex util pukes on paths containing spaces, which will happen
   * most of the time on Windows, since Processing sketches wind up in
   * "My Documents". Therefore, build android in a temp file.
   * http://code.google.com/p/android/issues/detail?id=4567
   *
   * TODO: better would be to retrieve the 8.3 name for the sketch folder!
   *
   * @param sketch
   * @return A folder in which to build the android sketch
   * @throws IOException
   */
  private File createTempBuildFolder(final Sketch sketch) throws IOException {
    final File tmp = File.createTempFile("android", "sketch");
    if (!(tmp.delete() && tmp.mkdir())) {
      throw new IOException("Cannot create temp dir " + tmp + " to build android sketch");
    }
    return tmp;
  }
  
  public boolean isWear() {
    return appComponent == WATCHFACE;
  }  
  
  
  public int getAppComponent() {
    return appComponent;
  }
  
//  public void setAppComponent(int opt) {
//    if (appComponent != opt) {
//      appComponent = opt;
//      resetManifest = true;
//    }
//  }
  
  protected boolean usesOpenGL() {
    return renderer != null && (renderer.equals("P2D") || renderer.equals("P3D")); 
  }


  protected File createExportFolder() throws IOException {
//    Sketch sketch = editor.getSketch();
    // Create the 'android' build folder, and move any existing version out.
    File androidFolder = new File(sketch.getFolder(), "android");
    if (androidFolder.exists()) {
//      Date mod = new Date(androidFolder.lastModified());
      String stamp = AndroidMode.getDateStamp(androidFolder.lastModified());
      File dest = new File(sketch.getFolder(), "android." + stamp);
      boolean result = androidFolder.renameTo(dest);
      if (!result) {
        ProcessHelper mv;
        ProcessResult pr;
        try {
          System.err.println("createProject renameTo() failed, resorting to mv/move instead.");
          mv = new ProcessHelper("mv", androidFolder.getAbsolutePath(), dest.getAbsolutePath());
          pr = mv.execute();

//        } catch (IOException e) {
//          editor.statusError(e);
//          return null;
//
        } catch (InterruptedException e) {
          e.printStackTrace();
          return null;
        }
        if (!pr.succeeded()) {
          System.err.println(pr.getStderr());
          Messages.showWarning("Failed to rename",
                               "Could not rename the old “android” build folder.\n" +
                               "Please delete, close, or rename the folder\n" +
                               androidFolder.getAbsolutePath() + "\n" +
                               "and try again." , null);
          Platform.openFolder(sketch.getFolder());
          return null;
        }
      }
    } else {
      boolean result = androidFolder.mkdirs();
      if (!result) {
        Messages.showWarning("Folders, folders, folders",
                             "Could not create the necessary folders to build.\n" +
                             "Perhaps you have some file permissions to sort out?", null);
        return null;
      }
    }
    return androidFolder;
  }


  public File exportProject() throws IOException, SketchException {
    String buildSystem = Preferences.get("android.export.build_system");
    if (buildSystem == null) {
      buildSystem = "gradle";
      Preferences.set("android.export.build_system", buildSystem);
    }

    this.target = "debug";    
    String targetID = getTargetID();
    
    if (appComponent == WATCHFACE) {
      // We are building a watchface not to run on the emulator. We need the
      // handheld app:
      File wearFolder = createProject(targetID, false, true);
      if (wearFolder == null) return null;
      if (!antBuild()) return null;
      
      File projectFolder = createHandheldProject(targetID, wearFolder, null);
      if (projectFolder == null) return null;
      if (antBuild()) {
        File exportFolder = createExportFolder();
        if (buildSystem.equals("gradle")) {
          createGradleProject(projectFolder, exportFolder);
        } else { // ant          
          Util.copyDir(projectFolder, exportFolder);
        }
        return exportFolder;
      }
      return null;      
    } else {
      File projectFolder = createProject(targetID, false, false);
      if (projectFolder != null) {
        File exportFolder = createExportFolder();
        if (buildSystem.equals("gradle")) {
          createGradleProject(projectFolder, exportFolder);
        } else { // ant   
          Util.copyDir(projectFolder, exportFolder);
        }
        return exportFolder;
      }
      return null;
    }    
  }

  public File exportPackage(String keyStorePassword) throws Exception {
    File projectFolder = null;
    if (appComponent == WATCHFACE) {
      this.target = "release";
      String targetID = getTargetID();
      // We need to sign and align the wearable and handheld apps:      
      File wearFolder = createProject(targetID, true, true);
      if (wearFolder == null) return null;
      if (!antBuild()) return null;      
      File signedWearPackage = signPackage(wearFolder, keyStorePassword);
      if (signedWearPackage == null) return null;
      
      // Handheld package
      projectFolder = createHandheldProject(targetID, wearFolder, signedWearPackage);
      if (projectFolder == null) return null;
      if (!antBuild()) return null;
      
      File signedPackage = signPackage(projectFolder, keyStorePassword);
      if (signedPackage == null) return null;       
    } else {
      projectFolder = build("release");
      if (projectFolder == null) return null;

      File signedPackage = signPackage(projectFolder, keyStorePassword);
      if (signedPackage == null) return null;
    }
    
    // Final export folder
    File exportFolder = createExportFolder();
    Util.copyDir(projectFolder, exportFolder);
    return new File(exportFolder, "/bin/");     
  }

  private File signPackage(File projectFolder, String keyStorePassword) throws Exception {
    File keyStore = AndroidKeyStore.getKeyStore();
    if (keyStore == null) return null;

    File unsignedPackage = new File(projectFolder, "bin/" + sketch.getName().toLowerCase() + "_release_unsigned.apk");
    if (!unsignedPackage.exists()) return null;
    File signedPackage = new File(projectFolder, "bin/" + sketch.getName().toLowerCase() + "_release_signed.apk");

    JarSigner.signJar(unsignedPackage, signedPackage, AndroidKeyStore.ALIAS_STRING, keyStorePassword, keyStore.getAbsolutePath(), keyStorePassword);

    //if (verifySignedPackage(unsignedPackage)) {
    /*File signedPackage = new File(projectFolder, "bin/" + sketch.getName() + "-release-signed.apk");
    if (signedPackage.exists()) {
      boolean deleteResult = signedPackage.delete();
      if (!deleteResult) {
        Base.showWarning("Error during package signing",
            "Unable to delete old signed package");
        return null;
      }
    }

    boolean renameResult = unsignedPackage.renameTo(signedPackage);
    if (!renameResult) {
      Base.showWarning("Error during package signing",
          "Unable to rename package file");
      return null;
    }*/

    File alignedPackage = zipalignPackage(signedPackage, projectFolder);
    return alignedPackage;
    /*} else {
      Base.showWarning("Error during package signing",
          "Verification of the signed package has failed");
      return null;
    }*/
  }

  
  private File zipalignPackage(File signedPackage, File projectFolder) throws IOException, InterruptedException {

    File buildToolsFolder = new File(sdk.getSdkFolder(), "build-tools").listFiles()[0];
    String zipalignPath = buildToolsFolder.getAbsolutePath() + "/zipalign";

    File alignedPackage = new File(projectFolder, "bin/" + sketch.getName().toLowerCase() + "_release_signed_aligned.apk");

    String[] args = {
        zipalignPath, "-v", "-f", "4",
        signedPackage.getAbsolutePath(), alignedPackage.getAbsolutePath()
    };

    Process alignProcess = Runtime.getRuntime().exec(args);
    alignProcess.waitFor();

    if (alignedPackage.exists()) return alignedPackage;
    return null;
  }


  protected boolean antBuild() throws SketchException {
    final Project p = new Project();

    String path = buildFile.getAbsolutePath().replace('\\', '/');
    p.setUserProperty("ant.file", path);

    // try to spew something useful to the console
    final DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    consoleLogger.setOutputPrintStream(System.out);  // ? uncommented before
    // WARN, INFO, VERBOSE, DEBUG
    consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
    p.addBuildListener(consoleLogger);

    // This logger is used to pick up javac errors to be parsed into
    // SketchException objects. Note that most errors seem to show up on stdout
    // since that's where the [javac] prefixed lines are coming through.
    final DefaultLogger errorLogger = new DefaultLogger();
    final ByteArrayOutputStream errb = new ByteArrayOutputStream();
    final PrintStream errp = new PrintStream(errb);
    errorLogger.setErrorPrintStream(errp);
    final ByteArrayOutputStream outb = new ByteArrayOutputStream();
    final PrintStream outp = new PrintStream(outb);
    errorLogger.setOutputPrintStream(outp);
    errorLogger.setMessageOutputLevel(Project.MSG_INFO);
    p.addBuildListener(errorLogger);

    try {
      p.fireBuildStarted();
      p.init();
      final ProjectHelper helper = ProjectHelper.getProjectHelper();
      p.addReference("ant.projectHelper", helper);
      helper.parse(p, buildFile);
      p.executeTarget(target);
      renameAPK();
      return true;
    } catch (final BuildException e) {
      // Send a "build finished" event to the build listeners for this project.
      p.fireBuildFinished(e);
      antBuildProblems(new String(outb.toByteArray()),
                       new String(errb.toByteArray()));
    }
    return false;
  }


  void antBuildProblems(String outPile, String errPile) throws SketchException {
    final String[] outLines =
      outPile.split(System.getProperty("line.separator"));
    final String[] errLines =
      errPile.split(System.getProperty("line.separator"));

    for (final String line : outLines) {
      final String javacPrefix = "[javac]";
      final int javacIndex = line.indexOf(javacPrefix);
      if (javacIndex != -1) {
//         System.out.println("checking: " + line);
//        final Sketch sketch = editor.getSketch();
        // String sketchPath = sketch.getFolder().getAbsolutePath();
        int offset = javacIndex + javacPrefix.length() + 1;
        String[] pieces =
          PApplet.match(line.substring(offset), "^(.+):([0-9]+):\\s+(.+)$");
        if (pieces != null) {
//          PApplet.println(pieces);
          String fileName = pieces[1];
          // remove the path from the front of the filename
          //fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
          fileName = fileName.substring(fileName.lastIndexOf(File.separatorChar) + 1);
          final int lineNumber = PApplet.parseInt(pieces[2]) - 1;
//          PApplet.println("looking for " + fileName + " line " + lineNumber);
          SketchException rex = placeException(pieces[3], fileName, lineNumber);
          if (rex != null) {
//            System.out.println("found a rex");
//            rex.hideStackTrace();
//            editor.statusError(rex);
//            return false; // get outta here
            throw rex;
          }
        }
      }
    }

    // Couldn't parse the exception, so send something generic
    SketchException skex =
      new SketchException("Error from inside the Android tools, " +
                          "check the console.");

    // Try to parse anything else we might know about
    for (final String line : errLines) {
      if (line.contains("Unable to resolve target '" + target_platform + "'")) {
        System.err.println("Use the Android SDK Manager (under the Android");
        System.err.println("menu) to install the SDK platform and ");
        System.err.println("Google APIs for Android " + target_sdk);
        skex = new SketchException("Please install the SDK platform and " +
                                   "Google APIs for Android " + target_sdk);
      }
    }
    // Stack trace is not relevant, just the message.
    skex.hideStackTrace();
    throw skex;
  }


  String getPathForAPK() {
    String suffix = target.equals("release") ? "release_unsigned" : "debug";
    String apkName = "bin/" + sketch.getName().toLowerCase() + "_" + suffix + ".apk";
    final File apkFile = new File(tmpFolder, apkName);
    if (!apkFile.exists()) {
      return null;
    }
    return apkFile.getAbsolutePath();
  }
  
  
  private void renameAPK() {
    String suffix = target.equals("release") ? "release-unsigned" : "debug";
    String apkName = "bin/" + sketch.getName() + "-" + suffix + ".apk";
    final File apkFile = new File(tmpFolder, apkName);
    if (apkFile.exists()) {
      String suffixNew = target.equals("release") ? "release_unsigned" : "debug";
      String apkNameNew = "bin/" + sketch.getName().toLowerCase() + "_" + suffixNew + ".apk";
      final File apkFileNew = new File(tmpFolder, apkNameNew);
      apkFile.renameTo(apkFileNew);
    }
  }

  
  private void writeAntProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("application-package=" + getPackageName());
    writer.flush();
    writer.close();
  }


  private void writeBuildXML(final File xmlFile, final String projectName) {    
    File xmlTemplate = mode.getContentFile("templates/" + ANT_BUILD_TEMPLATE);
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@project_name@@", projectName);
    replaceMap.put("@@tools_folder@@", Base.getToolsFolder().getPath());
        
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap);
  }

  private void writeProjectProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("target=" + target_platform);
    writer.println();
    // http://stackoverflow.com/questions/4821043/includeantruntime-was-not-set-for-android-ant-script
    writer.println("# Suppress the javac task warnings about \"includeAntRuntime\"");
    writer.println("build.sysclasspath=last");
    writer.flush();
    writer.close();
  }


  private void writeLocalProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    final String sdkPath = sdk.getSdkFolder().getAbsolutePath();
    if (Platform.isWindows()) {
      // Windows needs backslashes escaped, or it will also accept forward
      // slashes in the build file. We're using the forward slashes since this
      // path gets concatenated with a lot of others that use forwards anyway.
      writer.println("sdk.dir=" + sdkPath.replace('\\', '/'));
    } else {
      writer.println("sdk.dir=" + sdkPath);
    }
    writer.flush();
    writer.close();
  }

  
  static final String ICON_192 = "icon-192.png";
  static final String ICON_144 = "icon-144.png"; 
  static final String ICON_96 = "icon-96.png";
  static final String ICON_72 = "icon-72.png";
  static final String ICON_48 = "icon-48.png";
  static final String ICON_36 = "icon-36.png";

  static final String ICON_WATCHFACE_CIRCULAR = "preview_circular.png";
  static final String ICON_WATCHFACE_RECTANGULAR = "preview_rectangular.png";  
  
  private void writeRes(File resFolder) throws SketchException {
    File layoutFolder = mkdirs(resFolder, "layout");    
    writeResLayoutMainActivity(layoutFolder);

    int comp = getAppComponent();
    if (comp == FRAGMENT) {
      File valuesFolder = mkdirs(resFolder, "values");      
      writeResStylesFragment(valuesFolder);
    }
    
    if (comp == WALLPAPER) {
      File xmlFolder = mkdirs(resFolder, "xml");      
      writeResXMLWallpaper(xmlFolder);
            
      File valuesFolder = mkdirs(resFolder, "values");      
      writeResStringsWallpaper(valuesFolder);      
    }
    
    if (comp == VR) {
      File valuesFolder = mkdirs(resFolder, "values");      
      writeResStylesVR(valuesFolder);  
    }    

    File sketchFolder = sketch.getFolder();
    writeIconFiles(sketchFolder, resFolder);
    
    if (comp == WATCHFACE) {
      File xmlFolder = mkdirs(resFolder, "xml");      
      writeResXMLWatchFace(xmlFolder);      
      
      // write the preview files
      File localPrevCircle = new File(sketchFolder, ICON_WATCHFACE_CIRCULAR);
      File localPrevRect = new File(sketchFolder, ICON_WATCHFACE_RECTANGULAR);
      
      File buildPrevCircle = new File(resFolder, "drawable/" + ICON_WATCHFACE_CIRCULAR);
      File buildPrevRect = new File(resFolder, "drawable/" + ICON_WATCHFACE_RECTANGULAR);
      
      if (!localPrevCircle.exists()) {
        try {
          Util.copyFile(mode.getContentFile("icons/" + ICON_WATCHFACE_CIRCULAR), buildPrevCircle);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }     
      } else {
        try {
          Util.copyFile(localPrevCircle, buildPrevCircle);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }

      if (!localPrevRect.exists())  {
        try {
          Util.copyFile(mode.getContentFile("icons/" + ICON_WATCHFACE_RECTANGULAR), buildPrevRect);
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      } else {
        try {
          Util.copyFile(localPrevCircle, buildPrevRect);
        } catch (IOException e) {
           // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }     
    }
  }

  
  private void writeIconFiles(File sketchFolder, File resFolder) {
    // write the icon files
    File localIcon36 = new File(sketchFolder, ICON_36);
    File localIcon48 = new File(sketchFolder, ICON_48);
    File localIcon72 = new File(sketchFolder, ICON_72);
    File localIcon96 = new File(sketchFolder, ICON_96);
    File localIcon144 = new File(sketchFolder, ICON_144);
    File localIcon192 = new File(sketchFolder, ICON_192);    

//    File drawableFolder = new File(resFolder, "drawable");
//    drawableFolder.mkdirs()
    File buildIcon48 = new File(resFolder, "drawable/icon.png");
    File buildIcon36 = new File(resFolder, "drawable-ldpi/icon.png");
    File buildIcon72 = new File(resFolder, "drawable-hdpi/icon.png");
    File buildIcon96 = new File(resFolder, "drawable-xhdpi/icon.png");
    File buildIcon144 = new File(resFolder, "drawable-xxhdpi/icon.png");
    File buildIcon192 = new File(resFolder, "drawable-xxxhdpi/icon.png");    

    if (!localIcon36.exists() &&
        !localIcon48.exists() &&
        !localIcon72.exists() &&
        !localIcon96.exists() &&
        !localIcon144.exists() &&
        !localIcon192.exists()) {
      try {
        // if no icons are in the sketch folder, then copy all the defaults
        if (buildIcon36.getParentFile().mkdirs()) {
          Util.copyFile(mode.getContentFile("icons/" + ICON_36), buildIcon36);
        } else {
          System.err.println("Could not create \"drawable-ldpi\" folder.");
        }
        if (buildIcon48.getParentFile().mkdirs()) {
          Util.copyFile(mode.getContentFile("icons/" + ICON_48), buildIcon48);
        } else {
          System.err.println("Could not create \"drawable\" folder.");
        }
        if (buildIcon72.getParentFile().mkdirs()) {
          Util.copyFile(mode.getContentFile("icons/" + ICON_72), buildIcon72);
        } else {
          System.err.println("Could not create \"drawable-hdpi\" folder.");
        }
        if (buildIcon96.getParentFile().mkdirs()) {
          Util.copyFile(mode.getContentFile("icons/" + ICON_96), buildIcon96);
        } else {
          System.err.println("Could not create \"drawable-xhdpi\" folder.");
        }
        if (buildIcon144.getParentFile().mkdirs()) {
          Util.copyFile(mode.getContentFile("icons/" + ICON_144), buildIcon144);
        } else {
          System.err.println("Could not create \"drawable-xxhdpi\" folder.");
        }        
        if (buildIcon192.getParentFile().mkdirs()) {
          Util.copyFile(mode.getContentFile("icons/" + ICON_192), buildIcon192);
        } else {
          System.err.println("Could not create \"drawable-xxxhdpi\" folder.");
        }            
      } catch (IOException e) {
        e.printStackTrace();
        //throw new SketchException("Could not get Android icons");
      }
    } else {
      // if at least one of the icons already exists, then use that across the board
      try {
        if (localIcon36.exists()) {
          if (new File(resFolder, "drawable-ldpi").mkdirs()) {
            Util.copyFile(localIcon36, buildIcon36);
          }
        }
        if (localIcon48.exists()) {
          if (new File(resFolder, "drawable").mkdirs()) {
            Util.copyFile(localIcon48, buildIcon48);
          }
        }
        if (localIcon72.exists()) {
          if (new File(resFolder, "drawable-hdpi").mkdirs()) {
            Util.copyFile(localIcon72, buildIcon72);
          }
        }
        if (localIcon96.exists()) {
          if (new File(resFolder, "drawable-xhdpi").mkdirs()) {
            Util.copyFile(localIcon96, buildIcon96);
          }
        }
        if (localIcon144.exists()) {
          if (new File(resFolder, "drawable-xxhdpi").mkdirs()) {
            Util.copyFile(localIcon144, buildIcon144);
          }
        }
        if (localIcon192.exists()) {
          if (new File(resFolder, "drawable-xxxhdpi").mkdirs()) {
            Util.copyFile(localIcon192, buildIcon192);
          }
        }        
      } catch (IOException e) {
        System.err.println("Problem while copying icons.");
        e.printStackTrace();
      }
    }    
  }

  private File mkdirs(final File parent, final String name) throws SketchException {
    final File result = new File(parent, name);
    if (!(result.exists() || result.mkdirs())) {
      throw new SketchException("Could not create " + result);
    }
    return result;
  }


  private void writeMainClass(final File srcDirectory, 
      final String renderer, final boolean external) {
    int comp = getAppComponent();
    String[] permissions = manifest.getPermissions();
    if (comp == FRAGMENT) {
      writeFragmentActivity(srcDirectory, permissions, external);
    } else if (comp == WALLPAPER) {
      writeWallpaperService(srcDirectory, permissions, external);
    } else if (comp == WATCHFACE) {
      if (usesOpenGL()) {
        writeWatchFaceGLESService(srcDirectory, permissions, external);  
      } else {
        writeWatchFaceCanvasService(srcDirectory, permissions, external);  
      }      
    } else if (comp == VR) {
      writeVRActivity(srcDirectory, permissions, external);
    }
  }

  
  private void writeFragmentActivity(final File srcDirectory, 
      final String[] permissions, final boolean external) {    
    File javaTemplate = mode.getContentFile("templates/" + FRAGMENT_ACTIVITY_TEMPLATE);    
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainActivity.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
    
    AndroidMode.createFileFromTemplate(javaTemplate, javaFile, replaceMap);
  }
  
  
  private void writeWallpaperService(final File srcDirectory, 
      String[] permissions, final boolean external) {    
    File javaTemplate = mode.getContentFile("templates/" + WALLPAPER_SERVICE_TEMPLATE);
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainService.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");    
    
    AndroidMode.createFileFromTemplate(javaTemplate, javaFile, replaceMap); 
  }
  
  
  private void writeWatchFaceGLESService(final File srcDirectory, 
      String[] permissions, final boolean external) {
    File javaTemplate = mode.getContentFile("templates/" + WATCHFACE_SERVICE_TEMPLATE);
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainService.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@watchface_classs@@", "PWatchFaceGLES");
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");    
    
    AndroidMode.createFileFromTemplate(javaTemplate, javaFile, replaceMap);     
  }

  
  private void writeWatchFaceCanvasService(final File srcDirectory, 
      String[] permissions, final boolean external) {
    File javaTemplate = mode.getContentFile("templates/" + WATCHFACE_SERVICE_TEMPLATE);
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainService.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@watchface_classs@@", "PWatchFaceCanvas");
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : ""); 
    
    AndroidMode.createFileFromTemplate(javaTemplate, javaFile, replaceMap); 
  }  
  
  
  private void writeVRActivity(final File srcDirectory, String[] permissions, 
      final boolean external) {
    File javaTemplate = mode.getContentFile("templates/" + VR_ACTIVITY_TEMPLATE);    
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainActivity.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
    
    AndroidMode.createFileFromTemplate(javaTemplate, javaFile, replaceMap); 
  }

  
  private void writeResLayoutMainActivity(final File layoutFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + LAYOUT_ACTIVITY_TEMPLATE);
    File xmlFile = new File(layoutFolder, "main.xml");
        
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@sketch_class_name@@",sketchClassName);
        
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap); 
  }
  
  
  private void writeResStylesFragment(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STYLES_FRAGMENT_TEMPLATE);
    File xmlFile = new File(valuesFolder, "styles.xml");
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile); 
  }
  
  
  private void writeResStylesVR(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STYLES_VR_TEMPLATE);
    File xmlFile = new File(valuesFolder, "styles.xml");
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile);
  }
  

  private void writeResXMLWallpaper(final File xmlFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + XML_WALLPAPER_TEMPLATE);
    File xmlFile = new File(xmlFolder, "wallpaper.xml");
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile);
  }
  
  
  private void writeResStringsWallpaper(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STRINGS_WALLPAPER_TEMPLATE);
    File xmlFile = new File(valuesFolder, "strings.xml");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@sketch_class_name@@",sketchClassName);
        
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap);  
  }
  
  
  private void writeResXMLWatchFace(final File xmlFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + XML_WATCHFACE_TEMPLATE);
    File xmlFile = new File(xmlFolder, "watch_face.xml");
    AndroidMode.createFileFromTemplate(xmlTemplate, xmlFile);
  }
  
  
//  private String generatePermissionsString(final String[] permissions) {
//    String permissionsStr = "";
//    for (String p: permissions) {
//      permissionsStr += (0 < permissionsStr.length() ? "," : "");
//      if (p.indexOf("permission") == -1) {
//        permissionsStr += "Manifest.permission." + p;
//      } else if (p.indexOf("Manifest.permission") == 0) {
//        permissionsStr += p;
//      } else {
//        permissionsStr += "\"" + p + "\"";
//      }
//    }
//    permissionsStr = "{" + permissionsStr + "}";   
//    return permissionsStr;
//  }
 

  /**
   * For each library, copy .jar and .zip files to the 'libs' folder,
   * and copy anything else to the 'assets' folder.
   */
  private void copyImportedLibs(final File libsFolder,
                                final File assetsFolder) throws IOException {
    for (Library library : getImportedLibraries()) {
      // add each item from the library folder / export list to the output
      for (File exportFile : library.getAndroidExports()) {
        String exportName = exportFile.getName();
        if (!exportFile.exists()) {
          System.err.println(exportFile.getName() +
                             " is mentioned in export.txt, but it's " +
                             "a big fat lie and does not exist.");
        } else if (exportFile.isDirectory()) {
          // Copy native library folders to the correct location
          if (exportName.equals("armeabi") ||
              exportName.equals("armeabi-v7a") ||
              exportName.equals("x86")) {
            Util.copyDir(exportFile, new File(libsFolder, exportName));
          } else {
            // Copy any other directory to the assets folder
            Util.copyDir(exportFile, new File(assetsFolder, exportName));
          }
        } else if (exportName.toLowerCase().endsWith(".zip")) {
          // As of r4 of the Android SDK, it looks like .zip files
          // are ignored in the libs folder, so rename to .jar
          System.err.println(".zip files are not allowed in Android libraries.");
          System.err.println("Please rename " + exportFile.getName() + " to be a .jar file.");
          String jarName = exportName.substring(0, exportName.length() - 4) + ".jar";
          Util.copyFile(exportFile, new File(libsFolder, jarName));

        } else if (exportName.toLowerCase().endsWith(".jar")) {
          Util.copyFile(exportFile, new File(libsFolder, exportName));

        } else {
          Util.copyFile(exportFile, new File(assetsFolder, exportName));
        }
      }
    }
  }

  
  private void copyCodeFolder(final File libsFolder) throws IOException {
    // Copy files from the 'code' directory into the 'libs' folder
    final File codeFolder = sketch.getCodeFolder();
    if (codeFolder != null && codeFolder.exists()) {
      for (final File item : codeFolder.listFiles()) {
        if (!item.isDirectory()) {
          final String name = item.getName();
          final String lcname = name.toLowerCase();
          if (lcname.endsWith(".jar") || lcname.endsWith(".zip")) {
            String jarName = name.substring(0, name.length() - 4) + ".jar";
            Util.copyFile(item, new File(libsFolder, jarName));
          }
        }
      }
    }
  }


  protected String getPackageName() {
    return manifest.getPackageName();
  }


  public void cleanup() {
    // don't want to be responsible for this
    //rm(tempBuildFolder);
    tmpFolder.deleteOnExit();
  }
  
  
  protected void createGradleProject(File projectFolder, File exportFolder) 
      throws IOException, SketchException {
    installGradlew(exportFolder);
    
    File folder = sdk.getBuildToolsFolder();
    String[] versions = folder.list();
    String[] sorted = PApplet.sort(versions, versions.length);
    String buildToolVer = "";
    if (sorted != null && 0 < sorted.length) {
      buildToolVer = sorted[sorted.length - 1];
    }
    
    if (appComponent == WATCHFACE) {
      createTopModule(projectFolder, exportFolder, "':mobile', ':wear'");
      createMobileModule(projectFolder, exportFolder, buildToolVer);
      createWearModule(new File(projectFolder, "wear"), exportFolder, buildToolVer);
    } else {
      createTopModule(projectFolder, exportFolder, "':app'");
      createAppModule(projectFolder, exportFolder, buildToolVer);
    }
  }
  
  private void installGradlew(File exportFolder) throws IOException {
    File gradlewFile = mode.getContentFile("mode/gradlew.zip");
    AndroidMode.extractFolder(gradlewFile, exportFolder, true, true); 
    File execFile = new File(exportFolder, "gradlew");
    execFile.setExecutable(true);
  }
  
  private void createTopModule(File projectFolder, File exportFolder, 
      String projectModules) throws IOException {
    // Top level gradle files
    File buildTemplate = mode.getContentFile("templates/" + TOP_GRADLE_BUILD_TEMPLATE);
    File buildlFile = new File(exportFolder, "build.gradle");
    Util.copyFile(buildTemplate, buildlFile);
    
    writeLocalProps(new File(exportFolder, "local.properties"));
    writeFile(new File(exportFolder, "gradle.properties"), 
        new String[]{"org.gradle.jvmargs=-Xmx1536m"});
    
    File settingsTemplate = mode.getContentFile("templates/" + GRADLE_SETTINGS_TEMPLATE);    
    File settingsFile = new File(exportFolder, "settings.gradle");    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@project_modules@@", projectModules);    
    AndroidMode.createFileFromTemplate(settingsTemplate, settingsFile, replaceMap); 
  }
  
  private void createAppModule(File projectFolder, File exportFolder, String buildToolsVer) 
      throws SketchException, IOException {
    File moduleFolder = mkdirs(exportFolder, "app");
    
    String minSdk;
    String tmplFile;
    if (appComponent == VR) {
      minSdk = min_sdk_gvr;
      tmplFile = VR_GRADLE_BUILD_TEMPLATE;       
    } else {
      minSdk = min_sdk_fragment; 
      tmplFile = APP_GRADLE_BUILD_TEMPLATE;      
    }
    
    File appBuildTemplate = mode.getContentFile("templates/" + tmplFile);    
    File appBuildFile = new File(moduleFolder, "build.gradle");    
    HashMap<String, String> replaceMap = new HashMap<String, String>();    
    replaceMap.put("@@build_tools@@", buildToolsVer);
    replaceMap.put("@@package_name@@", getPackageName());    
    replaceMap.put("@@min_sdk@@", minSdk);  
    replaceMap.put("@@target_sdk@@", target_sdk);
    replaceMap.put("@@support_version@@", support_version);    
    replaceMap.put("@@wear_version@@", wear_version);        
    replaceMap.put("@@gvr_version@@", gvr_sdk_version);
    replaceMap.put("@@version_code@@", manifest.getVersionCode());
    replaceMap.put("@@version_name@@", manifest.getVersionName());
    AndroidMode.createFileFromTemplate(appBuildTemplate, appBuildFile, replaceMap); 
    
    writeFile(new File(moduleFolder, "proguard-rules.pro"), 
        new String[]{"# Add project specific ProGuard rules here."});
    
    File coreFile = new File(projectFolder, "libs/processing-core.jar");    
    File libsFolder = mkdirs(moduleFolder, "libs");
    Util.copyFile(coreFile, new File(libsFolder, "processing-core.jar"));

    if (appComponent == VR) {
      File vrFile = new File(projectFolder, "libs/vr.jar");
      Util.copyFile(vrFile, new File(libsFolder, "vr.jar"));
    }
    
    File mainFolder = mkdirs(moduleFolder, "src/main");
    File javaFolder = mkdirs(mainFolder, "java");
    File resFolder = mkdirs(mainFolder, "res");
    File assetsFolder = mkdirs(mainFolder, "assets");
    
    Util.copyFile(new File(projectFolder, "AndroidManifest.xml"), 
                  new File(mainFolder, "AndroidManifest.xml"));
    Util.copyDir(new File(projectFolder, "res"), resFolder);
    Util.copyDir(new File(projectFolder, "src"), javaFolder);
    Util.copyDir(new File(projectFolder, "assets"), assetsFolder);
  }
  
  private void createMobileModule(File projectFolder, File exportFolder, String buildToolsVer) 
      throws SketchException, IOException {
    File moduleFolder = mkdirs(exportFolder, "mobile");
    
    File appBuildTemplate = mode.getContentFile("templates/" + HANDHELD_GRADLE_BUILD_TEMPLATE);    
    File appBuildFile = new File(moduleFolder, "build.gradle");    
    HashMap<String, String> replaceMap = new HashMap<String, String>();    
    replaceMap.put("@@build_tools@@", buildToolsVer);
    replaceMap.put("@@package_name@@", getPackageName());    
    replaceMap.put("@@min_sdk@@", min_sdk_handheld);    
    replaceMap.put("@@target_sdk@@", target_sdk);
    replaceMap.put("@@support_version@@", support_version);
    replaceMap.put("@@play_services_version@@", play_services_version);
    replaceMap.put("@@wear_version@@", wear_version);
    replaceMap.put("@@version_code@@", manifest.getVersionCode());
    replaceMap.put("@@version_name@@", manifest.getVersionName());
    AndroidMode.createFileFromTemplate(appBuildTemplate, appBuildFile, replaceMap); 
    
    writeFile(new File(moduleFolder, "proguard-rules.pro"), 
        new String[]{"# Add project specific ProGuard rules here."});    
    
    File mainFolder = mkdirs(moduleFolder, "src/main");
    File javaFolder = mkdirs(mainFolder, "java");
    File resFolder = mkdirs(mainFolder, "res");    
    
    Util.copyFile(new File(projectFolder, "AndroidManifest.xml"), 
                  new File(mainFolder, "AndroidManifest.xml"));
    Util.copyDir(new File(projectFolder, "res"), resFolder);
    Util.copyDir(new File(projectFolder, "src"), javaFolder);
  }
  
  private void createWearModule(File projectFolder, File exportFolder, String buildToolsVer) 
      throws SketchException, IOException {
    File moduleFolder = mkdirs(exportFolder, "wear");
    
    File appBuildTemplate = mode.getContentFile("templates/" + WEARABLE_GRADLE_BUILD_TEMPLATE);    
    File appBuildFile = new File(moduleFolder, "build.gradle");    
    HashMap<String, String> replaceMap = new HashMap<String, String>();    
    replaceMap.put("@@build_tools@@", buildToolsVer);
    replaceMap.put("@@package_name@@", getPackageName());    
    replaceMap.put("@@min_sdk@@", min_sdk_watchface);    
    replaceMap.put("@@target_sdk@@", target_sdk);
    replaceMap.put("@@support_version@@", support_version);
    replaceMap.put("@@play_services_version@@", play_services_version);
    replaceMap.put("@@wear_version@@", wear_version);
    replaceMap.put("@@version_code@@", manifest.getVersionCode());
    replaceMap.put("@@version_name@@", manifest.getVersionName());
    AndroidMode.createFileFromTemplate(appBuildTemplate, appBuildFile, replaceMap);     
    
    writeFile(new File(moduleFolder, "proguard-rules.pro"), 
        new String[]{"# Add project specific ProGuard rules here."}); 
    
    File coreFile = new File(projectFolder, "libs/processing-core.jar");    
    File libsFolder = mkdirs(moduleFolder, "libs");
    Util.copyFile(coreFile, new File(libsFolder, "processing-core.jar"));
        
    File mainFolder = mkdirs(moduleFolder, "src/main");
    File javaFolder = mkdirs(mainFolder, "java");
    File resFolder = mkdirs(mainFolder, "res");
    File assetsFolder = mkdirs(mainFolder, "assets");
    
    Util.copyFile(new File(projectFolder, "AndroidManifest.xml"), 
                  new File(mainFolder, "AndroidManifest.xml"));
    Util.copyDir(new File(projectFolder, "res"), resFolder);
    Util.copyDir(new File(projectFolder, "src"), javaFolder);
  }  
  
  private void writeFile(final File file, String[] lines) {
    final PrintWriter writer = PApplet.createWriter(file);
    for (String line: lines) writer.println(line);
    writer.flush();
    writer.close();
  }  
  
  private void copyWearLib(File tmpFolder, File libsFolder) throws IOException {
    // The wear jar is needed even when the app is not a watch face, because on
    // devices with android < 5 the dependencies of the PWatchFace* classes
    // cannot be resolved.
    // TODO: temporary hack until I find a better way to include the wearable aar
    // package included in the SDK:      
    File aarFile = new File(sdk.getWearableFolder(), wear_version + "/wearable-" + wear_version + ".aar");
    File explodeDir = new File(tmpFolder, "aar");
    AndroidMode.extractClassesJarFromAar(aarFile, explodeDir, new File(libsFolder, "wearable-" + wear_version + ".jar"));    
  }
  
  private void copySupportLibs(File tmpFolder, File libsFolder) throws IOException {
    // Copy support packages (core-utils, compat, fragment, annotations, and 
    // vector-drawable)
    File aarFile = new File(sdk.getSupportLibrary(), "/support-core-utils/" + support_version + "/support-core-utils-" + support_version + ".aar");
    File explodeDir = new File(tmpFolder, "aar");
    AndroidMode.extractClassesJarFromAar(aarFile, explodeDir, new File(libsFolder, "support-core-utils-" + support_version + ".jar"));
    
    aarFile = new File(sdk.getSupportLibrary(), "/support-compat/" + support_version + "/support-compat-" + support_version + ".aar");
    explodeDir = new File(tmpFolder, "aar");
    AndroidMode.extractClassesJarFromAar(aarFile, explodeDir, new File(libsFolder, "support-compat-" + support_version + ".jar"));
    
    aarFile = new File(sdk.getSupportLibrary(), "/support-fragment/" + support_version + "/support-fragment-" + support_version + ".aar");
    explodeDir = new File(tmpFolder, "aar");
    AndroidMode.extractClassesJarFromAar(aarFile, explodeDir, new File(libsFolder, "support-fragment-" + support_version + ".jar"));

    aarFile = new File(sdk.getSupportLibrary(), "/support-vector-drawable/" + support_version + "/support-vector-drawable-" + support_version + ".aar");
    explodeDir = new File(tmpFolder, "aar");
    AndroidMode.extractClassesJarFromAar(aarFile, explodeDir, new File(libsFolder, "support-vector-drawable-" + support_version + ".jar"));
    
    File compatJarFile = new File(sdk.getSupportLibrary(), "/support-annotations/" + support_version + "/support-annotations-" + support_version + ".jar");
    Util.copyFile(compatJarFile, new File(libsFolder, "support-annotations-" + support_version + ".jar"));      
  }
  
  private void copyAppCompatLib(String targetID, File tmpFolder, File libsFolder) 
      throws IOException {
    ////////////////////////////////////////////////////////////////////////
    // first step: extract appcompat library project 
    File aarFile = new File(sdk.getSupportLibrary(), "/appcompat-v7/" + support_version + "/appcompat-v7-" + support_version + ".aar");        
    File appCompatFolder = new File(libsFolder, "appcompat");
    AndroidMode.extractFolder(aarFile, appCompatFolder, false);
    Util.removeDir(new File(appCompatFolder, "aidl"));
    Util.removeDir(new File(appCompatFolder, "android"));
    Util.removeDir(new File(appCompatFolder, "assets"));
    File classesJar = new File(appCompatFolder, "classes.jar");
    File appCompatLibsFolder = new File(appCompatFolder, "/libs");
    if (!appCompatLibsFolder.exists()) appCompatLibsFolder.mkdir();        
    File appCompatJar = new File(appCompatLibsFolder, "android-support-v7-appcompat.jar");
    Util.copyFile(classesJar, appCompatJar);
    classesJar.delete();
    
    ////////////////////////////////////////////////////////////////////////
    // second step: create library projects
    boolean appCompatRes = createLibraryProject("appcompat", targetID, 
        appCompatFolder.getAbsolutePath(), "android.support.v7.appcompat");

    ////////////////////////////////////////////////////////////////////////
    // third step: reference library projects from main project        
    if (appCompatRes) {
//      System.out.println("Library project created succesfully in " + libsFolder.toString());
      appCompatRes = referenceLibraryProject(targetID, tmpFolder.getAbsolutePath(), "libs/appcompat");
      if (appCompatRes) {
//        System.out.println("Library project referenced succesfully!");
        // Finally, re-write the build files so they use org.eclipse.jdt.core.JDTCompilerAdapter
        // instead of com.sun.tools.javac.Main
        // TODO: use the build file generated by the android tools, and 
        // add the custom section redefining the target
        File appCompatBuildFile = new File(appCompatFolder, "build.xml");
        writeBuildXML(appCompatBuildFile, "appcompat");
      }
    }
  }
  
  private void copyGVRLibs(String targetID, File libsFolder) throws IOException {
    // TODO: temporary hack until I find a better way to include the VR aar
    // packages included in the GVR SDK:
    
    ////////////////////////////////////////////////////////////////////////
    // first step: unpack the VR packages in the project's 
    // libs folder:
    File baseZipFile = mode.getContentFile("libraries/vr/gvrsdk/" + gvr_sdk_version + "/sdk-base.zip");
    File commonZipFile = mode.getContentFile("libraries/vr/gvrsdk/" + gvr_sdk_version + "/sdk-common.zip");
    File audioZipFile = mode.getContentFile("libraries/vr/gvrsdk/" + gvr_sdk_version + "/sdk-audio.zip");
    AndroidMode.extractFolder(baseZipFile, libsFolder, true);
    AndroidMode.extractFolder(commonZipFile, libsFolder, true);        
    AndroidMode.extractFolder(audioZipFile, libsFolder, true);        
    File baseLibsFolder = new File(libsFolder, "sdk-base");
    File commonLibsFolder = new File(libsFolder, "sdk-common");
    File audioLibsFolder = new File(libsFolder, "sdk-audio");

    ////////////////////////////////////////////////////////////////////////
    // second step: create library projects
    boolean baseRes = createLibraryProject("sdk_base", targetID, 
        baseLibsFolder.getAbsolutePath(), "com.google.vr.sdk.base");
    boolean commonRes = createLibraryProject("sdk_common", targetID, 
        commonLibsFolder.getAbsolutePath(), "com.google.vr.cardboard");        
    boolean audioRes = createLibraryProject("sdk_audio", targetID, 
        audioLibsFolder.getAbsolutePath(), "com.google.vr.sdk.audio");

    ////////////////////////////////////////////////////////////////////////
    // third step: reference library projects from main project        
    if (baseRes && commonRes && audioRes) {
      System.out.println("Library projects created succesfully in " + libsFolder.toString());          
      baseRes = referenceLibraryProject(targetID, tmpFolder.getAbsolutePath(), "libs/sdk-base");
      commonRes = referenceLibraryProject(targetID, tmpFolder.getAbsolutePath(), "libs/sdk-common");
      audioRes = referenceLibraryProject(targetID, tmpFolder.getAbsolutePath(), "libs/sdk-audio");
      if (baseRes && commonRes && audioRes) {
        System.out.println("Library projects referenced succesfully!");
        // Finally, re-write the build files so they use org.eclipse.jdt.core.JDTCompilerAdapter
        // instead of com.sun.tools.javac.Main
        // TODO: use the build file generated by the android tools, and 
        // add the custom section redefining the target
        File baseBuildFile = new File(baseLibsFolder, "build.xml");
        writeBuildXML(baseBuildFile, "sdk-base");
        File commonBuildFile = new File(commonLibsFolder, "build.xml");
        writeBuildXML(commonBuildFile, "sdk-common");            
        File audioBuildFile = new File(audioLibsFolder, "build.xml");
        writeBuildXML(audioBuildFile, "sdk-audio");          
      }
    }    
  }
  
  private String getTargetID() {
    String tid = "";
    
    final String[] params = {
      sdk.getAndroidToolPath(),
      "list", "targets"
    };

    ProcessHelper p = new ProcessHelper(params);
    try {
      final ProcessResult abiListResult = p.execute();
      String id = null;
      String platform = null;
      String api = null;          
      for (String line : abiListResult) {
        if (line.indexOf("id:") == 0) {
          String[] parts = line.substring(4).split("or");
          if (parts.length == 2) {
            id = parts[0];
            platform = parts[1].replaceAll("\"", "").trim();
          }
        }
            
        String[] mapi = PApplet.match(line, "API\\slevel:\\s(\\S+)");
        if (mapi != null) {
          api = mapi[1];
        }
          
        if (platform != null && platform.equals(target_platform) &&
            api != null && api.equals(target_sdk)) {
          tid = id;
          break;
        }            
      }
    } catch (InterruptedException | IOException e) {}
    
    return tid;
  }
  
  private String copyWearApk(File wearPackage, File resFolder, File wearFolder) 
      throws SketchException, IOException {
    // Copy the wearable apk
    String apkName = "";
    if (wearPackage == null) {
      String suffix = target.equals("release") ? "release_unsigned" : "debug";    
      apkName = sketch.getName().toLowerCase() + "_" + suffix;
    } else {
      String name = wearPackage.getName();      
      int dot = name.lastIndexOf('.');
      if (dot == -1) {
        apkName = name;
      } else {
        apkName = name.substring(0, dot);
      }
    }
    
    File rawFolder = mkdirs(resFolder, "raw");
    File wearApk = new File(wearFolder, "bin/" + apkName + ".apk");
    Util.copyFile(wearApk, new File(rawFolder, apkName + ".apk"));    
    return apkName;
  }
}
