/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
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

import org.gradle.tooling.*;
import processing.app.Base;
import processing.app.Library;
import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.Sketch;
import processing.app.SketchException;
import processing.app.Util;
import processing.core.PApplet;
import processing.mode.java.JavaBuild;
import processing.mode.java.preproc.SurfaceInfo;

import java.io.*;
import java.util.HashMap;

/** 
 * Class with all the infrastructure needed to build a sketch in the Android 
 * mode and run it either on the device or in the emulator, using Gradle as the
 * build system. It also exports the sketch as a Gradle project file to build 
 * from the command line or import into Android Studio, and a signed and aligned
 * package ready to upload to the Play Store.
 */
class AndroidBuild extends JavaBuild {
  static public final int APP       = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE = 2;
  static public final int VR        = 3;

  // Minimum SDK's API levels required for each component:
  static public final String MIN_SDK_APP       = "17"; // Android 4.2
  static public final String MIN_SDK_WALLPAPER = "17"; // Android 4.2
  static public final String MIN_SDK_VR        = "19"; // Android 4.4
  static public final String MIN_SDK_WATCHFACE = "25"; // Android 7.1.1
  
  // Target SDK is stored in the preferences file.
  static public String TARGET_SDK;  
  static public String TARGET_PLATFORM;
  static {
    TARGET_SDK = Preferences.get("android.sdk.target");
    if (TARGET_SDK == null || PApplet.parseInt(TARGET_SDK) < 26) { 
      // Must be 8.0 or higher
      TARGET_SDK = "26"; 
      Preferences.set("android.sdk.target", TARGET_SDK);
    }
    TARGET_PLATFORM = "android-" + TARGET_SDK;
  }

  // Versions of Support, AppCompat, Wear and VR in use
  // All of these are hard-coded, as the TARGET_SDK. Should obtained from the
  // repository files? Or users being able to change them in the preferences 
  // file?
  static public final String SUPPORT_VER       = "25.2.0";
  static public final String PLAY_SERVICES_VER = "10.2.0";  
  static public final String WEAR_VER          = "2.0.0";
  static public final String GVR_VER           = "1.60.1";  
  
  // Main activity or service 
  static private final String APP_ACTIVITY_TEMPLATE = "AppActivity.java.tmpl";
  static private final String WALLPAPER_SERVICE_TEMPLATE = "WallpaperService.java.tmpl";
  static private final String WATCHFACE_SERVICE_TEMPLATE = "WatchFaceService.java.tmpl";
  static private final String VR_ACTIVITY_TEMPLATE = "VRActivity.java.tmpl";
  
  // Additional resources
  static private final String LAYOUT_ACTIVITY_TEMPLATE = "LayoutActivity.xml.tmpl";
  static private final String STYLES_FRAGMENT_TEMPLATE = "StylesFragment.xml.tmpl";
  static private final String STYLES_VR_TEMPLATE = "StylesVR.xml.tmpl";
  static private final String XML_WALLPAPER_TEMPLATE = "XMLWallpaper.xml.tmpl";
  static private final String STRINGS_WALLPAPER_TEMPLATE = "StringsWallpaper.xml.tmpl";
  static private final String XML_WATCHFACE_TEMPLATE = "XMLWatchFace.xml.tmpl";
  
  // Gradle build files
  static private final String GRADLE_SETTINGS_TEMPLATE = "Settings.gradle.tmpl";  
  static private final String TOP_GRADLE_BUILD_TEMPLATE = "TopBuild.gradle.tmpl";
  static private final String APP_GRADLE_BUILD_ECJ_TEMPLATE = "AppBuildECJ.gradle.tmpl";
  static private final String APP_GRADLE_BUILD_TEMPLATE = "AppBuild.gradle.tmpl";
  static private final String VR_GRADLE_BUILD_ECJ_TEMPLATE = "VRBuildECJ.gradle.tmpl";
  static private final String VR_GRADLE_BUILD_TEMPLATE = "VRBuild.gradle.tmpl";  
  static private final String WEAR_GRADLE_BUILD_ECJ_TEMPLATE = "WearBuildECJ.gradle.tmpl";
  static private final String WEAR_GRADLE_BUILD_TEMPLATE = "WearBuild.gradle.tmpl";
  
  // Icon files
  static final String ICON_36 = "icon-36.png";
  static final String ICON_48 = "icon-48.png";
  static final String ICON_72 = "icon-72.png";
  static final String ICON_96 = "icon-96.png";
  static final String ICON_144 = "icon-144.png";
  static final String ICON_192 = "icon-192.png"; 
  static final String WATCHFACE_ICON_CIRCULAR = "preview_circular.png";
  static final String WATCHFACE_ICON_RECTANGULAR = "preview_rectangular.png";
  
  private int appComponent = APP;
  
  private final AndroidSDK sdk;
  private final File coreZipFile;

  /** whether this is a "debug" or "release" build */
  private String target;
  
  /** The manifest for the sketch being built */
  private Manifest manifest;

  /** temporary folder safely inside a 8.3-friendly folder */
  private File tmpFolder;

  /** Determines which gradle build template will be used */
  private boolean exportProject = false;

  /** Renderer used by the sketch */
  private String renderer = "";
  
  /** Name of the Gradle module in the project, either app or wear */
  private String module = "";
  
  /**
   * Constructor.
   * @param sketch the sketch to be built
   * @param mode reference to the mode
   * @param appComp component (regular handheld app, wallpaper, watch face, VR)
   * @param emu build to run in emulator or on device if false 
   */  
  public AndroidBuild(Sketch sketch, AndroidMode mode, int comp) {
    super(sketch);
    appComponent = comp;
    sdk = mode.getSDK();
    coreZipFile = mode.getCoreZipLocation();
    module = appComponent == WATCHFACE ? "wear" : "app";
  }

  
  public String getPackageName() {
    return manifest.getPackageName();
  }
  

  public int getAppComponent() {
    return appComponent;
  }
  
  
  public boolean isWear() {
    return appComponent == WATCHFACE;
  }  


  public void cleanup() {
    tmpFolder.deleteOnExit();
  }  

  
  public boolean usesOpenGL() {
    return renderer != null && (renderer.equals("P2D") || renderer.equals("P3D")); 
  }
  
  
  public String getPathForAPK() {
    String suffix = target.equals("release") ? "release_unsigned" : "debug";
    String apkName = module + "/build/outputs/apk/" + sketch.getName().toLowerCase() + "_" + suffix + ".apk";
    final File apkFile = new File(tmpFolder, apkName);
    if (!apkFile.exists()) {
      return null;
    }
    return apkFile.getAbsolutePath();
  }  


  /**
   * Build into temporary folders (needed for the Windows 8.3 bugs in the Android SDK).
   * @param target "debug" or "release"
   * @throws SketchException
   * @throws IOException
   */
  public File build(String target) throws IOException, SketchException {
    this.target = target;        
    File folder = createProject(true);
    if (folder == null) return null;
    if (!gradleBuild()) return null;
    return folder;      
  }


  /**
   * Create an Gradle Android project folder, and run the preprocessor on the 
   * sketch. Creates the top and app modules in the case of regular, VR, and 
   * wallpapers, and top, mobile and wear modules in the case of watch faces.
   */
  protected File createProject(boolean external) 
      throws IOException, SketchException {
    tmpFolder = createTempBuildFolder(sketch);
    System.out.println("Build folder: " + tmpFolder.getAbsolutePath());

    // Create the 'src' folder with the preprocessed code.
    srcFolder = new File(tmpFolder, module + "/src/main/java");
    binFolder = srcFolder; // Needed in the the parent JavaBuild class
    if (processing.app.Base.DEBUG) {
      Platform.openFolder(tmpFolder);
    }

    manifest = new Manifest(sketch, appComponent, mode.getFolder(), false);    
    manifest.setSdkTarget(TARGET_SDK);

    // build the preproc and get to work
    AndroidPreprocessor preproc = new AndroidPreprocessor(sketch, getPackageName());
    // On Android, this init will throw a SketchException if there's a problem with size()
    SurfaceInfo info = preproc.initSketchSize(sketch.getMainProgram());
    preproc.initSketchSmooth(sketch.getMainProgram());
    
    sketchClassName = preprocess(srcFolder, getPackageName(), preproc, false);
    File folder = sdk.getBuildToolsFolder();
    String[] versions = folder.list();
    String[] sorted = PApplet.sort(versions, versions.length);
    String buildToolsVer = "";
    if (sorted != null && 0 < sorted.length) {
      buildToolsVer = sorted[sorted.length - 1];
    } 

    if (sketchClassName != null) {
      renderer = info.getRenderer();
      writeMainClass(srcFolder, renderer, external);
      createTopModule(buildToolsVer, "':" + module +"'");
      createAppModule(buildToolsVer, module);
    }
    
    return tmpFolder;
  }
    
  
  protected boolean gradleBuild() throws SketchException {
    ProjectConnection connection = GradleConnector.newConnector()
            .forProjectDirectory(tmpFolder)
            .connect();

    boolean success = false;
    BuildLauncher build = connection.newBuild();
    build.setStandardOutput(System.out);
    build.setStandardError(System.err);

    try {      
      if (target.equals("debug")) build.forTasks("assembleDebug");
      else build.forTasks("assembleRelease");
      build.run();
      renameAPK();
      success = true;
    } catch (org.gradle.tooling.UnsupportedVersionException e) {
      e.printStackTrace();
      success = false;   
    } catch (org.gradle.tooling.BuildException e) {      
      e.printStackTrace();
      success = false;
    } catch (org.gradle.tooling.BuildCancelledException e) {
      e.printStackTrace();
      success = false;      
    } catch (org.gradle.tooling.GradleConnectionException e) {
      e.printStackTrace();
      success = false;        
    } catch (Exception e) {
      e.printStackTrace();
      success = false;      
    } finally {
      connection.close();
    }
    
    return success;
  }
  
  
  // ---------------------------------------------------------------------------
  // Gradle modules  
  
  
  private void createTopModule(String buildToolsVer, String projectModules) 
      throws IOException {
    File buildTemplate = mode.getContentFile("templates/" + TOP_GRADLE_BUILD_TEMPLATE);
    File buildlFile = new File(tmpFolder, "build.gradle");
    Util.copyFile(buildTemplate, buildlFile);
    
    writeLocalProps(new File(tmpFolder, "local.properties"));
    AndroidUtil.writeFile(new File(tmpFolder, "gradle.properties"),
        new String[]{"org.gradle.jvmargs=-Xmx1536m"});
    
    File settingsTemplate = mode.getContentFile("templates/" + GRADLE_SETTINGS_TEMPLATE);    
    File settingsFile = new File(tmpFolder, "settings.gradle");
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@project_modules@@", projectModules);    
    AndroidUtil.createFileFromTemplate(settingsTemplate, settingsFile, replaceMap); 
  }
  
  
  private void createAppModule(String buildToolsVer, String moduleName)
      throws SketchException, IOException {
    File moduleFolder = AndroidUtil.createPath(tmpFolder, moduleName);
    
    String minSdk;
    String tmplFile;
    if (appComponent == VR) {
      minSdk = MIN_SDK_VR;
      tmplFile = exportProject ? VR_GRADLE_BUILD_TEMPLATE : VR_GRADLE_BUILD_ECJ_TEMPLATE;
    } else if (appComponent == WATCHFACE) {
      minSdk = MIN_SDK_WATCHFACE;
      tmplFile = exportProject ? WEAR_GRADLE_BUILD_TEMPLATE : WEAR_GRADLE_BUILD_ECJ_TEMPLATE;      
    } else {
      minSdk = MIN_SDK_APP;
      tmplFile = exportProject ? APP_GRADLE_BUILD_TEMPLATE : APP_GRADLE_BUILD_ECJ_TEMPLATE;
    }
    
    File appBuildTemplate = mode.getContentFile("templates/" + tmplFile);    
    File appBuildFile = new File(moduleFolder, "build.gradle");    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@tools_folder@@", Base.getToolsFolder().getPath().replace('\\', '/'));
    replaceMap.put("@@target_platform@@", sdk.getTargetPlatform().getPath().replace('\\', '/'));
    replaceMap.put("@@build_tools@@", buildToolsVer);    
    replaceMap.put("@@package_name@@", getPackageName());    
    replaceMap.put("@@min_sdk@@", minSdk);  
    replaceMap.put("@@target_sdk@@", TARGET_SDK);
    replaceMap.put("@@support_version@@", SUPPORT_VER);  
    replaceMap.put("@@play_services_version@@", PLAY_SERVICES_VER);
    replaceMap.put("@@wear_version@@", WEAR_VER);        
    replaceMap.put("@@gvr_version@@", GVR_VER);
    replaceMap.put("@@version_code@@", manifest.getVersionCode());
    replaceMap.put("@@version_name@@", manifest.getVersionName());
    AndroidUtil.createFileFromTemplate(appBuildTemplate, appBuildFile, replaceMap);

    AndroidUtil.writeFile(new File(moduleFolder, "proguard-rules.pro"),
        new String[]{"# Add project specific ProGuard rules here."});

    File libsFolder = AndroidUtil.createPath(moduleFolder, "libs");
    File mainFolder = new File(moduleFolder, "src/main");
    File resFolder = AndroidUtil.createPath(mainFolder, "res");
    File assetsFolder = AndroidUtil.createPath(mainFolder, "assets");

    writeRes(resFolder);

    File tempManifest = new File(mainFolder, "AndroidManifest.xml");
    manifest.writeCopy(tempManifest, sketchClassName);

    Util.copyFile(coreZipFile, new File(libsFolder, "processing-core.jar"));

    // Copy any imported libraries (their libs and assets),
    // and anything in the code folder contents to the project.
    copyImportedLibs(libsFolder, assetsFolder);
    copyCodeFolder(libsFolder);

    // Copy any system libraries needed by the project
    copyWearLib(tmpFolder, libsFolder);
    copySupportLibs(tmpFolder, libsFolder);
    if (getAppComponent() == APP) {
      copyAppCompatLib(tmpFolder, libsFolder);
    }
    if (getAppComponent() == VR) {
      copyGVRLibs(libsFolder);
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
  
  
  // ---------------------------------------------------------------------------
  // Templates
  

  private void writeMainClass(final File srcDirectory, 
      final String renderer, final boolean external) {
    int comp = getAppComponent();
    String[] permissions = manifest.getPermissions();
    if (comp == APP) {
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
    File javaTemplate = mode.getContentFile("templates/" + APP_ACTIVITY_TEMPLATE);    
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainActivity.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
    
    AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap);
  }
  
  
  private void writeWallpaperService(final File srcDirectory, 
      String[] permissions, final boolean external) {    
    File javaTemplate = mode.getContentFile("templates/" + WALLPAPER_SERVICE_TEMPLATE);
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainService.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");    
    
    AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap); 
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
    
    AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap);     
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
    
    AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap); 
  }  
  
  
  private void writeVRActivity(final File srcDirectory, String[] permissions, 
      final boolean external) {
    File javaTemplate = mode.getContentFile("templates/" + VR_ACTIVITY_TEMPLATE);    
    File javaFile = new File(new File(srcDirectory, getPackageName().replace(".", "/")), "MainActivity.java");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@package_name@@", getPackageName());
    replaceMap.put("@@sketch_class_name@@", sketchClassName);
    replaceMap.put("@@external@@", external ? "sketch.setExternal(true);" : "");
    
    AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap); 
  }

  
  private void writeResLayoutMainActivity(final File layoutFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + LAYOUT_ACTIVITY_TEMPLATE);
    File xmlFile = new File(layoutFolder, "main.xml");
        
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@sketch_class_name@@",sketchClassName);
        
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap); 
  }
  
  
  private void writeResStylesFragment(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STYLES_FRAGMENT_TEMPLATE);
    File xmlFile = new File(valuesFolder, "styles.xml");
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile); 
  }
  
  
  private void writeResStylesVR(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STYLES_VR_TEMPLATE);
    File xmlFile = new File(valuesFolder, "styles.xml");
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile);
  }
  

  private void writeResXMLWallpaper(final File xmlFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + XML_WALLPAPER_TEMPLATE);
    File xmlFile = new File(xmlFolder, "wallpaper.xml");
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile);
  }
  
  
  private void writeResStringsWallpaper(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STRINGS_WALLPAPER_TEMPLATE);
    File xmlFile = new File(valuesFolder, "strings.xml");
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@@sketch_class_name@@",sketchClassName);
        
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap);  
  }
  
  
  private void writeResXMLWatchFace(final File xmlFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + XML_WATCHFACE_TEMPLATE);
    File xmlFile = new File(xmlFolder, "watch_face.xml");
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile);
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
  
  
  private void writeRes(File resFolder) throws SketchException {
    File layoutFolder = AndroidUtil.createPath(resFolder, "layout");    
    writeResLayoutMainActivity(layoutFolder);

    int comp = getAppComponent();
    if (comp == APP) {
      File valuesFolder = AndroidUtil.createPath(resFolder, "values");      
      writeResStylesFragment(valuesFolder);
    } else if (comp == WALLPAPER) {
      File xmlFolder = AndroidUtil.createPath(resFolder, "xml");      
      writeResXMLWallpaper(xmlFolder);
      File valuesFolder = AndroidUtil.createPath(resFolder, "values");      
      writeResStringsWallpaper(valuesFolder);      
    } else if (comp == WATCHFACE) { 
      File xmlFolder = AndroidUtil.createPath(resFolder, "xml");      
      writeResXMLWatchFace(xmlFolder); 
    } else if (comp == VR) {
      File valuesFolder = AndroidUtil.createPath(resFolder, "values");      
      writeResStylesVR(valuesFolder);  
    }    
    
    File sketchFolder = sketch.getFolder();
    writeAppIconFiles(sketchFolder, resFolder);
    if (comp == WATCHFACE) {
      // Need the preview icons for watch faces.
      writeWatchIconFiles(sketchFolder, resFolder);
    }
  }

  
  // ---------------------------------------------------------------------------
  // Icons  
  
  
  private void writeAppIconFiles(File sketchFolder, File resFolder) {
    File localIcon36 = new File(sketchFolder, ICON_36);
    File localIcon48 = new File(sketchFolder, ICON_48);
    File localIcon72 = new File(sketchFolder, ICON_72);
    File localIcon96 = new File(sketchFolder, ICON_96);
    File localIcon144 = new File(sketchFolder, ICON_144);
    File localIcon192 = new File(sketchFolder, ICON_192);    

    File buildIcon48 = new File(resFolder, "drawable/icon.png");
    File buildIcon36 = new File(resFolder, "drawable-ldpi/icon.png");
    File buildIcon72 = new File(resFolder, "drawable-hdpi/icon.png");
    File buildIcon96 = new File(resFolder, "drawable-xhdpi/icon.png");
    File buildIcon144 = new File(resFolder, "drawable-xxhdpi/icon.png");
    File buildIcon192 = new File(resFolder, "drawable-xxxhdpi/icon.png");    

    if (!localIcon36.exists() && !localIcon48.exists() &&
        !localIcon72.exists() && !localIcon96.exists() &&
        !localIcon144.exists() && !localIcon192.exists()) {
      try {
        // if no icons are in the sketch folder, then copy all the defaults
        copyIcon(mode.getContentFile("icons/" + ICON_36), buildIcon36);
        copyIcon(mode.getContentFile("icons/" + ICON_48), buildIcon48);
        copyIcon(mode.getContentFile("icons/" + ICON_72), buildIcon72);
        copyIcon(mode.getContentFile("icons/" + ICON_96), buildIcon96);
        copyIcon(mode.getContentFile("icons/" + ICON_144), buildIcon144);
        copyIcon(mode.getContentFile("icons/" + ICON_192), buildIcon192);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      // if at least one of the icons already exists, then use that across the board
      try {
        if (localIcon36.exists()) copyIcon(localIcon36, buildIcon36); 
        if (localIcon48.exists()) copyIcon(localIcon48, buildIcon48);
        if (localIcon72.exists()) copyIcon(localIcon72, buildIcon72);
        if (localIcon96.exists()) copyIcon(localIcon96, buildIcon96);        
        if (localIcon144.exists()) copyIcon(localIcon144, buildIcon144);
        if (localIcon192.exists()) copyIcon(localIcon192, buildIcon192);
      } catch (IOException e) {
        System.err.println("Problem while copying app icons.");
        e.printStackTrace();
      }
    }
  }
  
  
  private void writeWatchIconFiles(File sketchFolder, File resFolder) {
    copyWatchIcon(new File(sketchFolder, WATCHFACE_ICON_CIRCULAR), 
                  new File(resFolder, "drawable/preview_circular.png"), 
                  mode.getContentFile("icons/" + WATCHFACE_ICON_CIRCULAR));
    copyWatchIcon(new File(sketchFolder, WATCHFACE_ICON_RECTANGULAR), 
        new File(resFolder, "drawable/preview_rectangular.png"), 
        mode.getContentFile("icons/" + WATCHFACE_ICON_RECTANGULAR));
  }
  
  
  private void copyWatchIcon(File srcFile, File destFile, File defFile) {
    if (!srcFile.exists()) {
      try {
        copyIcon(defFile, destFile);
      } catch (IOException e) {
        e.printStackTrace();
      }      
    } else {
      try {
        copyIcon(srcFile, destFile);        
      } catch (IOException e) {
        System.err.println("Problem while copying watch face icon.");
        e.printStackTrace();
      }
    }
  }
  
  
  private void copyIcon(File srcFile, File destFile) throws IOException {
    File parent = destFile.getParentFile();
    if (parent.exists() || parent.mkdirs()) {
      Util.copyFile(srcFile, destFile);
    } else {
      System.err.println("Could not create \"" + destFile.getParentFile() + "\" folder.");
    }    
  }  
  
  
  // ---------------------------------------------------------------------------
  // Dependencies
  
  
  private void copyWearLib(File tmpFolder, File libsFolder) throws IOException {
    // The wear jar is needed even when the app is not a watch face, because on
    // devices with android < 5 the dependencies of the PWatchFace* classes
    // cannot be resolved.
    File aarFile = new File(sdk.getWearableFolder(), WEAR_VER + "/wearable-" + WEAR_VER + ".aar");
    Util.copyFile(aarFile, new File(libsFolder, aarFile.getName()));
  }
  
  
  private void copySupportLibs(File tmpFolder, File libsFolder) throws IOException {
    // Copy support packages (core-utils, compat, fragment, annotations, and 
    // vector-drawable)
    File aarFile = new File(sdk.getSupportLibrary(), 
        "/support-core-utils/" + SUPPORT_VER + "/support-core-utils-" + SUPPORT_VER + ".aar");
    Util.copyFile(aarFile, new File(libsFolder, aarFile.getName()));
    
    aarFile = new File(sdk.getSupportLibrary(), 
        "/support-compat/" + SUPPORT_VER + "/support-compat-" + SUPPORT_VER + ".aar");
    Util.copyFile(aarFile, new File(libsFolder, aarFile.getName()));
    
    aarFile = new File(sdk.getSupportLibrary(), 
        "/support-fragment/" + SUPPORT_VER + "/support-fragment-" + SUPPORT_VER + ".aar");
    Util.copyFile(aarFile, new File(libsFolder, aarFile.getName()));

    aarFile = new File(sdk.getSupportLibrary(), 
        "/support-vector-drawable/" + SUPPORT_VER + "/support-vector-drawable-" + SUPPORT_VER + ".aar");
    Util.copyFile(aarFile, new File(libsFolder, aarFile.getName()));
    
    File compatJarFile = new File(sdk.getSupportLibrary(), 
        "/support-annotations/" + SUPPORT_VER + "/support-annotations-" + SUPPORT_VER + ".jar");
    Util.copyFile(compatJarFile, new File(libsFolder, "support-annotations-" + SUPPORT_VER + ".jar"));      
  }
  
  
  private void copyAppCompatLib(File tmpFolder, File libsFolder) 
      throws IOException {
    File aarFile = new File(sdk.getSupportLibrary(), 
        "/appcompat-v7/" + SUPPORT_VER + "/appcompat-v7-" + SUPPORT_VER + ".aar");
    Util.copyFile(aarFile, new File(libsFolder, aarFile.getName()));
  }
  
  
  private void copyGVRLibs(File libsFolder) throws IOException {
    File baseAarFile = mode.getContentFile("libraries/vr/gvrsdk/" + GVR_VER + "/sdk-base-" + GVR_VER + ".aar");
    Util.copyFile(baseAarFile, new File(libsFolder, baseAarFile.getName()));
    
    File commonAarFile = mode.getContentFile("libraries/vr/gvrsdk/" + GVR_VER + "/sdk-common-" + GVR_VER + ".aar");
    Util.copyFile(commonAarFile, new File(libsFolder, commonAarFile.getName()));
    
    File audioAarFile = mode.getContentFile("libraries/vr/gvrsdk/" + GVR_VER + "/sdk-audio-" + GVR_VER + ".aar");
    Util.copyFile(audioAarFile, new File(libsFolder, audioAarFile.getName()));
  }

  
  // ---------------------------------------------------------------------------
  // Export project


  public File exportProject() throws IOException, SketchException {
    target = "debug";
    
    exportProject = true;
    File projectFolder = createProject(false);
    exportProject = false;
    
    File exportFolder = createExportFolder("android");      
    Util.copyDir(projectFolder, exportFolder);
    installGradlew(exportFolder);
    return exportFolder;    
  }  
  
  
  // ---------------------------------------------------------------------------
  // Export package
  
  
  public File exportPackage(String keyStorePassword) throws Exception {
    File projectFolder = build("release");
    if (projectFolder == null) return null;

    File signedPackage = signPackage(projectFolder, keyStorePassword);
    if (signedPackage == null) return null;

    // Final export folder
    File exportFolder = createExportFolder("build");
    Util.copyDir(new File(projectFolder, module + "/build/outputs/apk"), exportFolder);    
    return exportFolder;
  }

  
  private File signPackage(File projectFolder, String keyStorePassword) throws Exception {
    File keyStore = AndroidKeyStore.getKeyStore();
    if (keyStore == null) return null;
    
    File unsignedPackage = new File(projectFolder, 
        module + "/build/outputs/apk/" + sketch.getName().toLowerCase() + "_release_unsigned.apk");
    if (!unsignedPackage.exists()) return null;
    File signedPackage = new File(projectFolder, 
        module + "/build/outputs/apk/" + sketch.getName().toLowerCase() + "_release_signed.apk");

    JarSigner.signJar(unsignedPackage, signedPackage, 
        AndroidKeyStore.ALIAS_STRING, keyStorePassword, 
        keyStore.getAbsolutePath(), keyStorePassword);

    File alignedPackage = zipalignPackage(signedPackage, projectFolder);
    return alignedPackage;
  }

  
  private File zipalignPackage(File signedPackage, File projectFolder) 
      throws IOException, InterruptedException {
    File zipAlign = sdk.getZipAlignTool();
    if (zipAlign == null || !zipAlign.exists()) {
      Messages.showWarning("Cannot find zipaling...",
          "The zipalign build tool needed to prepare the export package is missing.\n" +
          "Make sure that your Android SDK was downloaded correctly.");
      return null;
    }
    
    File alignedPackage = new File(projectFolder, 
        module + "/build/outputs/apk/" + sketch.getName().toLowerCase() + "_release_signed_aligned.apk");

    String[] args = {
        zipAlign.getAbsolutePath(), "-v", "-f", "4",
        signedPackage.getAbsolutePath(), alignedPackage.getAbsolutePath()
    };
        
    Process alignProcess = Runtime.getRuntime().exec(args);
    // Need to consume output for the process to finish, as discussed here
    // https://stackoverflow.com/questions/5483830/process-waitfor-never-returns 
    // Using StreamPump as in other parts of the mode does not seem to work for some reason
    BufferedReader reader = new BufferedReader(new InputStreamReader(alignProcess.getInputStream()));
    while ((reader.readLine()) != null) {}
    alignProcess.waitFor();

    if (alignedPackage.exists()) return alignedPackage;
    return null;
  }   
  
  
  //---------------------------------------------------------------------------
  // Build utils
  
  
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
   * For each library, copy .jar and .zip files to the 'libs' folder,
   * and copy anything else to the 'assets' folder.
   */
  private void copyImportedLibs(final File libsFolder,
                                final File assetsFolder) throws IOException {
    for (Library library : getImportedLibraries()) {
      // add each item from the library folder / export list to the output
      for (File exportFile : library.getAndroidExports()) {
        String exportName = exportFile.getName();
        
        // Skip the GVR jars, because the full aar packages will be copied next
        if (exportName.toLowerCase().startsWith("gvr-")) continue; 
        
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


  private void renameAPK() {
    String suffix = target.equals("release") ? "release-unsigned" : "debug";
    String apkName = module + "/build/outputs/apk/" + module + "-" + suffix + ".apk";
    final File apkFile = new File(tmpFolder, apkName);
    if (apkFile.exists()) {
      String suffixNew = target.equals("release") ? "release_unsigned" : "debug";
      String apkNameNew = module + "/build/outputs/apk/" + 
        sketch.getName().toLowerCase() + "_" + suffixNew + ".apk";
      final File apkFileNew = new File(tmpFolder, apkNameNew);
      apkFile.renameTo(apkFileNew);
    }
  }  
  
  
  /**
   * The Android dex util pukes on paths containing spaces, which will happen
   * most of the time on Windows, since Processing sketches wind up in
   * "My Documents". Therefore, build android in a temp file.
   * http://code.google.com/p/android/issues/detail?id=4567
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
  
  
  private void installGradlew(File exportFolder) throws IOException {
    File gradlewFile = mode.getContentFile("mode/gradlew.zip");
    AndroidUtil.extractFolder(gradlewFile, exportFolder, true, true); 
  }
  
  
  private File createExportFolder(String name) throws IOException {
    return AndroidUtil.createSubFolder(sketch.getFolder(), name);
  }  
}