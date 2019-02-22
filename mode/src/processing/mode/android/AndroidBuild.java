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
  static public final int APP          = 0;
  static public final int WALLPAPER    = 1;
  static public final int WATCHFACE    = 2;
  static public final int VR           = 3;
  static public final int AR           = 4;
  
  // Minimum SDK's API levels required for each component:
  static public final String MIN_SDK_APP       = "17"; // Android 4.2
  static public final String MIN_SDK_WALLPAPER = "17"; // Android 4.2
  static public final String MIN_SDK_VR        = "19"; // Android 4.4
  static public final String MIN_SDK_AR        = "24"; // Android 7.0.0
  static public final String MIN_SDK_WATCHFACE = "25"; // Android 7.1.1
  
  // Target SDK is stored in the preferences file.
  static public String TARGET_SDK;  
  static public String TARGET_PLATFORM;
  static {
    TARGET_SDK = Preferences.get("android.sdk.target");
    if (TARGET_SDK == null || PApplet.parseInt(TARGET_SDK) < 26) { 
      TARGET_SDK = "26"; 
      Preferences.set("android.sdk.target", TARGET_SDK);
    }
    TARGET_PLATFORM = "android-" + TARGET_SDK;
  }

  // Versions of support, play services, wear and VR in use, also stored in
  // preferences file so they can be changed without having to rebuilt/reinstall
  // the mode.
  static public String SUPPORT_VER;
  static {
    SUPPORT_VER = Preferences.get("android.sdk.support");
    if (SUPPORT_VER == null || !versionCheck(SUPPORT_VER, "26.0.2")) {
      SUPPORT_VER = "26.0.2"; 
      Preferences.set("android.sdk.support", SUPPORT_VER);
    }
  }

  static public String PLAY_SERVICES_VER;
  static {
    PLAY_SERVICES_VER = Preferences.get("android.sdk.play_services");
    if (PLAY_SERVICES_VER == null || !versionCheck(PLAY_SERVICES_VER, "11.0.4")) {
      PLAY_SERVICES_VER = "11.0.4"; 
      Preferences.set("android.sdk.play_services", PLAY_SERVICES_VER);
    }
  }  
  
  static public String WEAR_VER;
  static {
    WEAR_VER = Preferences.get("android.sdk.wear");
    if (WEAR_VER == null || !versionCheck(WEAR_VER, "2.1.0")) {
      WEAR_VER = "2.1.0"; 
      Preferences.set("android.sdk.wear", WEAR_VER);
    }
  }  
  
  static public String GVR_VER;
  static {
    GVR_VER = Preferences.get("android.sdk.gvr");
    if (GVR_VER == null || !versionCheck(GVR_VER, "1.150.0")) {
      GVR_VER = "1.150.0";
      Preferences.set("android.sdk.gvr", GVR_VER);
    }
  }

  static public String GAR_VER;
  static {
    GAR_VER = Preferences.get("android.sdk.ar");
    if (GAR_VER == null) {
      GAR_VER = "1.2.0";
      Preferences.set("android.sdk.ar", GAR_VER);
    }
  }
  
  // Main activity or service 
  static private final String APP_ACTIVITY_TEMPLATE = "AppActivity.java.tmpl";
  static private final String WALLPAPER_SERVICE_TEMPLATE = "WallpaperService.java.tmpl";
  static private final String WATCHFACE_SERVICE_TEMPLATE = "WatchFaceService.java.tmpl";
  static private final String VR_ACTIVITY_TEMPLATE = "VRActivity.java.tmpl";
  static private final String AR_ACTIVITY_TEMPLATE = "ARActivity.java.tmpl";
  
  // Additional resources
  static private final String LAYOUT_ACTIVITY_TEMPLATE = "LayoutActivity.xml.tmpl";
  static private final String STYLES_FRAGMENT_TEMPLATE = "StylesFragment.xml.tmpl";
  static private final String STYLES_VR_TEMPLATE = "StylesVR.xml.tmpl";
  static private final String STYLES_AR_TEMPLATE = "StylesAR.xml.tmpl";
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
  static private final String AR_GRADLE_BUILD_ECJ_TEMPLATE = "ARBuildECJ.gradle.tmpl";
  static private final String AR_GRADLE_BUILD_TEMPLATE = "ARBuild.gradle.tmpl";
  static private final String WEAR_GRADLE_BUILD_ECJ_TEMPLATE = "WearBuildECJ.gradle.tmpl";
  static private final String WEAR_GRADLE_BUILD_TEMPLATE = "WearBuild.gradle.tmpl";
  
  // Launcher and watch face icon files
  static final String[] SKETCH_LAUNCHER_ICONS = {"launcher_36.png", "launcher_48.png", 
                                                 "launcher_72.png", "launcher_96.png", 
                                                 "launcher_144.png", "launcher_192.png"};
  static final String[] SKETCH_OLD_LAUNCHER_ICONS = {"icon-36.png", "icon-48.png", 
                                                     "icon-72.png", "icon-96.png", 
                                                     "icon-144.png", "icon-192.png"}; 
  static final String[] BUILD_LAUNCHER_ICONS = {"mipmap-ldpi/ic_launcher.png", "mipmap-mdpi/ic_launcher.png", 
                                                "mipmap-hdpi/ic_launcher.png", "mipmap-xhdpi/ic_launcher.png", 
                                                "mipmap-xxhdpi/ic_launcher.png", "mipmap-xxxhdpi/ic_launcher.png"};
  static final String[] SKETCH_WATCHFACE_ICONS = {"preview_circular.png", 
                                                  "preview_rectangular.png"};
  static final String[] BUILD_WATCHFACE_ICONS = {"drawable-nodpi/preview_circular.png", 
                                                 "drawable-nodpi/preview_rectangular.png"};
  
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
   * @param appComp component (regular handheld app, wallpaper, watch face, VR, AR)
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
    String apkName = getPathToAPK() + sketch.getName().toLowerCase() + "_" + suffix + ".apk";
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
   * sketch. Creates the top and app modules in the case of regular, VR, AR and
   * wallpapers, and top, mobile and wear modules in the case of watch faces.
   */
  protected File createProject(boolean external) 
      throws IOException, SketchException {
    tmpFolder = createTempBuildFolder(sketch);
    System.out.println(AndroidMode.getTextString("android_build.error.build_folder", tmpFolder.getAbsolutePath()));

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
    if (sketchClassName != null) {
      renderer = info.getRenderer();
      writeMainClass(srcFolder, renderer, external);
      createTopModule("':" + module +"'");
      createAppModule(module);
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
  
  
  private void createTopModule(String projectModules) 
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
  
  
  private void createAppModule(String moduleName)
      throws SketchException, IOException {
    File moduleFolder = AndroidUtil.createPath(tmpFolder, moduleName);
    
    String minSdk;
    String tmplFile;
    if (appComponent == AR) {
      minSdk = MIN_SDK_AR;
      tmplFile = exportProject ? AR_GRADLE_BUILD_TEMPLATE : AR_GRADLE_BUILD_ECJ_TEMPLATE;
    } else if (appComponent == VR) {
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
    replaceMap.put("@@package_name@@", getPackageName());    
    replaceMap.put("@@min_sdk@@", minSdk);  
    replaceMap.put("@@target_sdk@@", TARGET_SDK);
    replaceMap.put("@@support_version@@", SUPPORT_VER);  
    replaceMap.put("@@play_services_version@@", PLAY_SERVICES_VER);
    replaceMap.put("@@wear_version@@", WEAR_VER);        
    replaceMap.put("@@gvr_version@@", GVR_VER);
    replaceMap.put("@@gar_version@@", GAR_VER);
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
    copyImportedLibs(libsFolder, mainFolder, assetsFolder);
    copyCodeFolder(libsFolder);

    // Copy any system libraries needed by the project
//    copyWearLib(libsFolder);
//    copySupportLibs(libsFolder);
//    if (getAppComponent() == APP) {
//      copyAppCompatLib(libsFolder);
//    }
//    if (getAppComponent() == VR) {
//      copyGVRLibs(libsFolder);
//    }

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
    } else if (comp == AR) {
      writeARActivity(srcDirectory, permissions, external);
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

  private void writeARActivity(final File srcDirectory, String[] permissions,
      final boolean external) {
    File javaTemplate = mode.getContentFile("templates/" + AR_ACTIVITY_TEMPLATE);
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


  private void writeResStylesAR(final File valuesFolder) {
    File xmlTemplate = mode.getContentFile("templates/" + STYLES_AR_TEMPLATE);
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
    } else if (comp == AR) {
      File valuesFolder = AndroidUtil.createPath(resFolder, "values");
      writeResStylesAR(valuesFolder);
    }
    
    File sketchFolder = sketch.getFolder();
    writeLauncherIconFiles(sketchFolder, resFolder);
    if (comp == WATCHFACE) {
      // Need the preview icons for watch faces.
      writeWatchFaceIconFiles(sketchFolder, resFolder);
    }
  }

  
  // ---------------------------------------------------------------------------
  // Icons  
  
  
  private void writeLauncherIconFiles(File sketchFolder, File resFolder) {
    writeIconFiles(sketchFolder, resFolder, SKETCH_LAUNCHER_ICONS, SKETCH_OLD_LAUNCHER_ICONS, BUILD_LAUNCHER_ICONS);
  }
  
  
  private void writeWatchFaceIconFiles(File sketchFolder, File resFolder) {
    writeIconFiles(sketchFolder, resFolder, SKETCH_WATCHFACE_ICONS, null, BUILD_WATCHFACE_ICONS);
  }
  
  
  private void writeIconFiles(File sketchFolder, File resFolder, 
                              String[] sketchIconNames, String[] oldIconNames, String[] buildIconNames) {
    File[] localIcons = AndroidUtil.getFileList(sketchFolder, sketchIconNames, oldIconNames);
    File[] buildIcons = AndroidUtil.getFileList(resFolder, buildIconNames);
    if (AndroidUtil.noFileExists(localIcons)) {
      // If no icons are in the sketch folder, then copy all the defaults      
      File[] defaultIcons = AndroidUtil.getFileList(mode, "icons/", sketchIconNames);      
      try {
        for (int i = 0; i < localIcons.length; i++) {
          copyIcon(defaultIcons[i], buildIcons[i]);  
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      // If at least one of the icons already exists, then use that across the board
      try {
        for (int i = 0; i < localIcons.length; i++) {
          if (localIcons[i].exists()) copyIcon(localIcons[i], buildIcons[i]);
        }
      } catch (IOException e) {
        System.err.println(AndroidMode.getTextString("android_build.error.cannot_copy_icons"));
        e.printStackTrace();
      }
    }
  }
  
  
  private void copyIcon(File srcFile, File destFile) throws IOException {
    File parent = destFile.getParentFile();
    if (parent.exists() || parent.mkdirs()) {
      Util.copyFile(srcFile, destFile);
    } else {
      System.err.println(AndroidMode.getTextString("android_build.error.cannot_create_icon_folder", destFile.getParentFile()));
    }    
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
    Util.copyDir(new File(projectFolder, getPathToAPK()), exportFolder);    
    return exportFolder;
  }

  
  private File signPackage(File projectFolder, String keyStorePassword) throws Exception {
    File keyStore = AndroidKeyStore.getKeyStore();
    if (keyStore == null) return null;
    
    File unsignedPackage = new File(projectFolder, 
        getPathToAPK() + sketch.getName().toLowerCase() + "_release_unsigned.apk");
    if (!unsignedPackage.exists()) return null;
    File signedPackage = new File(projectFolder, 
        getPathToAPK() + sketch.getName().toLowerCase() + "_release_signed.apk");

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
      Messages.showWarning(AndroidMode.getTextString("android_build.warn.cannot_find_zipalign.title"),
                           AndroidMode.getTextString("android_build.warn.cannot_find_zipalign.body"));
      return null;
    }
    
    File alignedPackage = new File(projectFolder, 
        getPathToAPK() + sketch.getName().toLowerCase() + "_release_signed_aligned.apk");

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
                                final File mainFolder,
                                final File assetsFolder) throws IOException {
    for (Library library : getImportedLibraries()) {
      // add each item from the library folder / export list to the output
      for (File exportFile : library.getAndroidExports()) {
        String exportName = exportFile.getName();
        
        // Skip the GVR and ARCore jars, because the gradle will resolve the dependencies
        if (appComponent == VR && exportName.toLowerCase().startsWith("sdk-")) continue;
        if (appComponent == AR && exportName.toLowerCase().startsWith("core-")) continue;

        if (!exportFile.exists()) {
          System.err.println(AndroidMode.getTextString("android_build.error.export_file_does_not_exist", exportFile.getName()));
        } else if (exportFile.isDirectory()) {
          // Copy native library folders to the correct location
          if (exportName.equals("armeabi") ||
              exportName.equals("armeabi-v7a") ||
              exportName.equals("x86")) {
            Util.copyDir(exportFile, new File(libsFolder, exportName));
          }
          // Copy jni libraries (.so files) to the correct location
          else if (exportName.equals("jniLibs")) {
            Util.copyDir(exportFile, new File(mainFolder, exportName));
          }
          else {
            // Copy any other directory to the assets folder
            Util.copyDir(exportFile, new File(assetsFolder, exportName));
          }
        } else if (exportName.toLowerCase().endsWith(".zip")) {
          // As of r4 of the Android SDK, it looks like .zip files
          // are ignored in the libs folder, so rename to .jar
          System.err.println(AndroidMode.getTextString("android_build.error.zip_files_not_allowed", exportFile.getName()));
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
    String apkName = getPathToAPK() + module + "-" + suffix + ".apk";
    final File apkFile = new File(tmpFolder, apkName);
    if (apkFile.exists()) {
      String suffixNew = target.equals("release") ? "release_unsigned" : "debug";
      String apkNameNew = getPathToAPK() + 
        sketch.getName().toLowerCase() + "_" + suffixNew + ".apk";
      final File apkFileNew = new File(tmpFolder, apkNameNew);
      apkFile.renameTo(apkFileNew);
    }
  }  
  
  
  private String getPathToAPK() {
    return module + "/build/outputs/apk/" + target + "/";
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
      throw new IOException(AndroidMode.getTextString("android_build.error.cannot_create_build_folder", tmp));
    }
    return tmp;
  }
  
  
  private void installGradlew(File exportFolder) throws IOException {
    File gradlewFile = mode.getContentFile("mode/gradlew.zip");
    AndroidUtil.extractFolder(gradlewFile, exportFolder, false, true);
    if (Platform.isMacOS() || Platform.isLinux()) {
      File execFile = new File(exportFolder, "gradlew");    
      execFile.setExecutable(true);      
    }    
  }
  
  
  private File createExportFolder(String name) throws IOException {
    return AndroidUtil.createSubFolder(sketch.getFolder(), name);
  }  
  
  
  static private boolean versionCheck(String currentVersion, String minVersion) {
    String[] currentPieces = currentVersion.split("\\.");
    String[] minPieces = minVersion.split("\\.");
    
    if (currentPieces.length == 3 && minPieces.length == 3) {
      int currentMajor = PApplet.parseInt(currentPieces[0], -1);
      int currentMinor = PApplet.parseInt(currentPieces[1], -1);
      int currentMicro = PApplet.parseInt(currentPieces[2], -1);
      
      int minMajor = PApplet.parseInt(minPieces[0], -1);
      int minMinor = PApplet.parseInt(minPieces[1], -1);
      int minMicro = PApplet.parseInt(minPieces[2], -1);
      
      if (-1 < currentMajor && -1 < currentMinor && -1 < currentMicro &&
          -1 < minMajor && -1 < minMinor && -1 < minMicro) {
        if (currentMajor < minMajor) {
          return false;
        } else if (currentMajor == minMajor) {
          if (currentMinor < minMinor) {
            return false;  
          } if (currentMinor == minMinor) {
            if (currentMicro < minMicro) {
              return false;
            } else {
              return true;
            }
          } else {
            return true;
          }
        } else {
          return true;
        }
      }      
    }
    
    return false;
  }
}