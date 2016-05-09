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
import java.security.Permission;


class AndroidBuild extends JavaBuild {
  static public final int FRAGMENT  = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE = 2;
  static public final int CARDBOARD = 3;
  
  // TODO: make package parameter in the config file, as well as the SDK version
  // to download (latest, specific number)
  //  static final String basePackage = "changethispackage.beforesubmitting.tothemarket";
  // We should start with either 16 (4.1) or 17 (4.2) because combined usage of
  // all previous versions is less than 5%:
  // http://developer.android.com/about/dashboards/index.html
  // With 17, we have getRealSize and getRealMetrics:
  // http://developer.android.com/reference/android/view/Display.html#getRealSize(android.graphics.Point)
  // http://developer.android.com/reference/android/view/Display.html#getRealMetrics(android.util.DisplayMetrics)
  static final String basePackage = "processing.test";
  static public final int min_sdk_fragment = 14; // Ice Cream Sandwich
  static public final int min_sdk_wallpaper = 14;  
  static public final int min_sdk_watchface = 21; // Lillipop
  static public final int min_sdk_cardboard = 19; // Kitkat
  
  static String sdkName = "5.0";
  static String sdkVersion = "21";  // Android 5.0 (Lollipop)
  static String sdkTarget = "android-" + sdkVersion;

  static int publishOption = FRAGMENT;
  static boolean forceNewManifest = false;
  
  private final AndroidSDK sdk;
  private final File coreZipFile;

  /** whether this is a "debug" or "release" build */
  private String target;
  private Manifest manifest;

  /** temporary folder safely inside a 8.3-friendly folder */
  private File tmpFolder;

  /** build.xml file for this project */
  private File buildFile;


  public AndroidBuild(final Sketch sketch, final AndroidMode mode) {
    super(sketch);

    sdk = mode.getSDK();
    coreZipFile = mode.getCoreZipLocation();
  }

  public static void setPublishOption(int opt, Sketch sketch) {
    if (publishOption != opt) {
      publishOption = opt;
      forceNewManifest = true;
    } else {
      forceNewManifest = false;
    }
  }
  
  public static void setSdkTarget(AndroidSDK.SDKTarget target, Sketch sketch) {
    sdkName = target.name;
    sdkVersion = Integer.toString(target.version);
    sdkTarget = "android-" + sdkVersion;

    Preferences.set("android.sdk.version", sdkVersion);
    Preferences.set("android.sdk.name", target.name);
  }

  /**
   * Build into temporary folders (needed for the Windows 8.3 bugs in the Android SDK).
   * @param target "debug" or "release"
   * @throws SketchException
   * @throws IOException
   */
  public File build(String target) throws IOException, SketchException {
    this.target = target;
    File folder = createProject();
    if (folder != null) {
      if (!antBuild()) {
        return null;
      }
    }
    return folder;
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
  public File createProject() throws IOException, SketchException {
    tmpFolder = createTempBuildFolder(sketch);

    // Create the 'src' folder with the preprocessed code.
//    final File srcFolder = new File(tmpFolder, "src");
    srcFolder = new File(tmpFolder, "src");
    // this folder isn't actually used, but it's used by the java preproc to
    // figure out the classpath, so we have to set it to something
//    binFolder = new File(tmpFolder, "bin");
    // use the src folder, since 'bin' might be used by the ant build
    binFolder = srcFolder;
    if (processing.app.Base.DEBUG) {
      Platform.openFolder(tmpFolder);
    }

    manifest = new Manifest(sketch);
    manifest.setSdkTarget(sdkVersion);
    forceNewManifest = false;
    
    // grab code from current editing window (GUI only)
//    prepareExport(null);

    // build the preproc and get to work
    AndroidPreprocessor preproc = new AndroidPreprocessor(sketch, getPackageName());
    // On Android, this init will throw a SketchException if there's a problem with size()
    preproc.initSketchSize(sketch.getMainProgram());
    preproc.initSketchSmooth(sketch.getMainProgram());
    
    sketchClassName = preprocess(srcFolder, manifest.getPackageName(), preproc, false);
    if (sketchClassName != null) {
      File tempManifest = new File(tmpFolder, "AndroidManifest.xml");
      manifest.writeBuild(tempManifest, sketchClassName, target.equals("debug"));

      writeAntProps(new File(tmpFolder, "ant.properties"));
      buildFile = new File(tmpFolder, "build.xml");
      writeBuildXML(buildFile, sketch.getName());
      writeProjectProps(new File(tmpFolder, "project.properties"));
      writeLocalProps(new File(tmpFolder, "local.properties"));

      final File resFolder = new File(tmpFolder, "res");
      writeRes(resFolder, sketchClassName);

      // TODO: it would be great if we can just get the renderer from the SurfaceInfo
      // object returned by initSketchSize()
      writeMainClass(srcFolder, preproc.getRenderer(sketch.getMainProgram()));

      // new location for SDK Tools 17: /opt/android/tools/proguard/proguard-android.txt
//      File proguardSrc = new File(sdk.getSdkFolder(), "tools/lib/proguard.cfg");
//      File proguardDst = new File(tmpFolder, "proguard.cfg");
//      Base.copyFile(proguardSrc, proguardDst);

      final File libsFolder = mkdirs(tmpFolder, "libs");
      final File assetsFolder = mkdirs(tmpFolder, "assets");

//      InputStream input = PApplet.createInput(getCoreZipLocation());
//      PApplet.saveStream(new File(libsFolder, "processing-core.jar"), input);
      Util.copyFile(coreZipFile, new File(libsFolder, "processing-core.jar"));

      // Copy any imported libraries (their libs and assets),
      // and anything in the code folder contents to the project.
      copyLibraries(libsFolder, assetsFolder);
      copyCodeFolder(libsFolder);
      
      if (publishOption == WATCHFACE) {
        // TODO: temporary hack until I find a better way to include the wearable aar
        // package included in the SDK:
        
        File wearJarFile = mode.getContentFile("mode/wearable-1.3.0-classes.jar");
        System.out.println(wearJarFile.toString());
        Util.copyFile(wearJarFile, new File(libsFolder, "wearable-1.3.0-classes.jar"));
      }

      // Copy the data folder (if one exists) to the project's 'assets' folder
      final File sketchDataFolder = sketch.getDataFolder();
      if (sketchDataFolder.exists()) {
        Util.copyDir(sketchDataFolder, assetsFolder);
      }

      // Do the same for the 'res' folder.
      // http://code.google.com/p/processing/issues/detail?id=767
      final File sketchResFolder = new File(sketch.getFolder(), "res");
      if (sketchResFolder.exists()) {
        Util.copyDir(sketchResFolder, resFolder);
      }
    }
    return tmpFolder;
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
//    File projectFolder = build("debug");
//    if (projectFolder == null) {
//      return null;
//    }
    // this will set debuggable to true in the .xml file
    target = "debug";
    File projectFolder = createProject();
    if (projectFolder != null) {
      File exportFolder = createExportFolder();
      Util.copyDir(projectFolder, exportFolder);
      return exportFolder;
    }
    return null;
  }

  public File exportPackage(String keyStorePassword) throws Exception {
    File projectFolder = build("release");
    if (projectFolder == null) return null;

    File signedPackage = signPackage(projectFolder, keyStorePassword);
    if (signedPackage == null) return null;

    File exportFolder = createExportFolder();
    Util.copyDir(projectFolder, exportFolder);
    return new File(exportFolder, "/bin/");
  }

  private File signPackage(File projectFolder, String keyStorePassword) throws Exception {
    File keyStore = AndroidKeyStore.getKeyStore();
    if (keyStore == null) return null;

    File unsignedPackage = new File(projectFolder, "bin/" + sketch.getName() + "-release-unsigned.apk");
    if (!unsignedPackage.exists()) return null;
    File signedPackage = new File(projectFolder, "bin/" + sketch.getName() + "-release-signed.apk");

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

  /*private boolean verifySignedPackage(File signedPackage) throws Exception {
    String[] args = {
        "-verify", signedPackage.getCanonicalPath()
    };

    PrintStream defaultPrintStream = System.out;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos);
    System.setOut(printStream);

    SystemExitControl.forbidSystemExitCall();
    try {
      JarSigner.main(args);
    } catch (SystemExitControl.ExitTrappedException ignored) { }
    SystemExitControl.enableSystemExitCall();

    System.setOut(defaultPrintStream);
    String result = baos.toString();

    baos.close();
    printStream.close();

    return result.contains("verified");
  } */

  private File zipalignPackage(File signedPackage, File projectFolder) throws IOException, InterruptedException {

    File buildToolsFolder = new File(sdk.getSdkFolder(), "build-tools").listFiles()[0];
    String zipalignPath = buildToolsFolder.getAbsolutePath() + "/zipalign";

    File alignedPackage = new File(projectFolder, "bin/" + sketch.getName() + "-release-signed-aligned.apk");

    String[] args = {
        zipalignPath, "-v", "-f", "4",
        signedPackage.getAbsolutePath(), alignedPackage.getAbsolutePath()
    };

    Process alignProcess = Runtime.getRuntime().exec(args);
    alignProcess.waitFor();

    if (alignedPackage.exists()) return alignedPackage;
    return null;
  }

  /*
  // SDK tools 17 have a problem where 'dex' won't pick up the libs folder
  // (which contains our friend processing-core.jar) unless your current
  // working directory is the same as the build file. So this is an unpleasant
  // workaround, at least until things are fixed or we hear of a better way.
  // This was fixed in SDK 19 (and Processing revision 0205) so we've now
  // disabled this portion of the code.
  protected boolean antBuild_dexworkaround() throws SketchException {
    try {
//      ProcessHelper helper = new ProcessHelper(tmpFolder, new String[] { "ant", target });
      // Windows doesn't include full paths, so make 'em happen.
      String cp = System.getProperty("java.class.path");
      String[] cpp = PApplet.split(cp, File.pathSeparatorChar);
      for (int i = 0; i < cpp.length; i++) {
        cpp[i] = new File(cpp[i]).getAbsolutePath();
      }
      cp = PApplet.join(cpp, File.pathSeparator);

      // Since Ant may or may not be installed, call it from the .jar file,
      // though hopefully 'java' is in the classpath.. Given what we do in
      // processing.mode.java.runner (and it that it works), should be ok.
      String[] cmd = new String[] {
        "java",
        "-cp", cp, //System.getProperty("java.class.path"),
        "org.apache.tools.ant.Main", target
//        "ant", target
      };
      ProcessHelper helper = new ProcessHelper(tmpFolder, cmd);
      ProcessResult pr = helper.execute();
      if (pr.getResult() != 0) {
//        System.err.println("mo builds, mo problems");
        System.err.println(pr.getStderr());
        System.out.println(pr.getStdout());
        // the actual javac errors and whatnot go to stdout
        antBuildProblems(pr.getStdout(), pr.getStderr());
        return false;
      }

    } catch (InterruptedException e) {
      return false;

    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }
  */


  /*
  public class HopefullyTemporaryWorkaround extends org.apache.tools.ant.Main {

    protected void exit(int exitCode) {
      // I want to exit, but let's not System.exit()
      System.out.println("gonna exit");
      System.out.flush();
      System.err.flush();
    }
  }


  protected boolean antBuild() throws SketchException {
    String[] cmd = new String[] {
      "-main", "processing.mode.android.HopefullyTemporaryWorkaround",
      "-Duser.dir=" + tmpFolder.getAbsolutePath(),
      "-logfile", "/Users/fry/Desktop/ant-log.txt",
      "-verbose",
      "-help",
//      "debug"
    };
    HopefullyTemporaryWorkaround.main(cmd);
    return true;
//    ProcessResult listResult =
//      new ProcessHelper("ant", "debug", tmpFolder).execute();
//    if (listResult.succeeded()) {
//      boolean badness = false;
//      for (String line : listResult) {
//      }
//    }
  }
  */


  protected boolean antBuild() throws SketchException {
//    System.setProperty("user.dir", tmpFolder.getAbsolutePath());  // oh why not { because it doesn't help }
    final Project p = new Project();
//    p.setBaseDir(tmpFolder);  // doesn't seem to do anything

//    System.out.println(tmpFolder.getAbsolutePath());
//    p.setUserProperty("user.dir", tmpFolder.getAbsolutePath());
    String path = buildFile.getAbsolutePath().replace('\\', '/');
    p.setUserProperty("ant.file", path);

    // deals with a problem where javac error messages weren't coming through
    //p.setUserProperty("build.compiler", "extJavac");
    // p.setUserProperty("build.compiler.emacs", "true"); // does nothing

    // try to spew something useful to the console
    final DefaultLogger consoleLogger = new DefaultLogger();
    consoleLogger.setErrorPrintStream(System.err);
    consoleLogger.setOutputPrintStream(System.out);  // ? uncommented before
    // WARN, INFO, VERBOSE, DEBUG
    //    consoleLogger.setMessageOutputLevel(Project.MSG_ERR);
    consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
//    consoleLogger.setMessageOutputLevel(Project.MSG_DEBUG);
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
    //    errorLogger.setMessageOutputLevel(Project.MSG_DEBUG);
    p.addBuildListener(errorLogger);

    try {
//      editor.statusNotice("Building sketch for Android...");
      p.fireBuildStarted();
      p.init();
      final ProjectHelper helper = ProjectHelper.getProjectHelper();
      p.addReference("ant.projectHelper", helper);
      helper.parse(p, buildFile);
      // p.executeTarget(p.getDefaultTarget());
      p.executeTarget(target);
//      editor.statusNotice("Finished building sketch.");
      return true;

    } catch (final BuildException e) {
      // Send a "build finished" event to the build listeners for this project.
      p.fireBuildFinished(e);

      // PApplet.println(new String(errb.toByteArray()));
      // PApplet.println(new String(outb.toByteArray()));

      // String errorOutput = new String(errb.toByteArray());
      // String[] errorLines =
      // errorOutput.split(System.getProperty("line.separator"));
      // PApplet.println(errorLines);

      //final String outPile = new String(outb.toByteArray());
      //antBuildProblems(new String(outb.toByteArray())
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
      if (line.contains("Unable to resolve target '" + sdkTarget + "'")) {
        System.err.println("Use the Android SDK Manager (under the Android");
        System.err.println("menu) to install the SDK platform and ");
        System.err.println("Google APIs for Android " + sdkName +
                           " (API " + sdkVersion + ")");
        skex = new SketchException("Please install the SDK platform and " +
                                   "Google APIs for API " + sdkVersion);
      }
    }
    // Stack trace is not relevant, just the message.
    skex.hideStackTrace();
    throw skex;
  }


  String getPathForAPK() {
    String suffix = target.equals("release") ? "release-unsigned" : "debug";
    String apkName = "bin/" + sketch.getName() + "-" + suffix + ".apk";
    final File apkFile = new File(tmpFolder, apkName);
    if (!apkFile.exists()) {
      return null;
    }
    return apkFile.getAbsolutePath();
  }


  private void writeAntProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("application-package=" + getPackageName());
    writer.flush();
    writer.close();
  }


  private void writeBuildXML(final File file, final String projectName) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

    writer.println("<project name=\"" + projectName + "\" default=\"help\">");

    writer.println("  <property file=\"local.properties\" />");
    writer.println("  <property file=\"ant.properties\" />");

    writer.println("  <property environment=\"env\" />");
    writer.println("  <condition property=\"sdk.dir\" value=\"${env.ANDROID_HOME}\">");
    writer.println("       <isset property=\"env.ANDROID_HOME\" />");
    writer.println("  </condition>");

    writer.println("  <property name=\"jdt.core\" value=\"" + Base.getToolsFolder() + "/../modes/java/mode/org.eclipse.jdt.core.jar\" />");
    writer.println("  <property name=\"jdtCompilerAdapter\" value=\"" + Base.getToolsFolder() + "/../modes/java/mode/jdtCompilerAdapter.jar\" />");
    writer.println("  <property name=\"build.compiler\" value=\"org.eclipse.jdt.core.JDTCompilerAdapter\" />");

    writer.println("  <mkdir dir=\"bin\" />");

    writer.println("  <echo message=\"${build.compiler}\" />");

// Override target from maint android build file
    writer.println("    <target name=\"-compile\" depends=\"-pre-build, -build-setup, -code-gen, -pre-compile\">");
    writer.println("        <do-only-if-manifest-hasCode elseText=\"hasCode = false. Skipping...\">");
    writer.println("            <path id=\"project.javac.classpath\">");
    writer.println("                <path refid=\"project.all.jars.path\" />");
    writer.println("                <path refid=\"tested.project.classpath\" />");
    writer.println("                <path path=\"${java.compiler.classpath}\" />");
    writer.println("            </path>");
    writer.println("            <javac encoding=\"${java.encoding}\"");
    writer.println("                    source=\"${java.source}\" target=\"${java.target}\"");
    writer.println("                    debug=\"true\" extdirs=\"\" includeantruntime=\"false\"");
    writer.println("                    destdir=\"${out.classes.absolute.dir}\"");
    writer.println("                    bootclasspathref=\"project.target.class.path\"");
    writer.println("                    verbose=\"${verbose}\"");
    writer.println("                    classpathref=\"project.javac.classpath\"");
    writer.println("                    fork=\"${need.javac.fork}\">");
    writer.println("                <src path=\"${source.absolute.dir}\" />");
    writer.println("                <src path=\"${gen.absolute.dir}\" />");
    writer.println("                <compilerarg line=\"${java.compilerargs}\" />");
    writer.println("                <compilerclasspath path=\"${jdtCompilerAdapter};${jdt.core}\" />");
    writer.println("            </javac>");

    writer.println("            <if condition=\"${build.is.instrumented}\">");
    writer.println("                <then>");
    writer.println("                    <echo level=\"info\">Instrumenting classes from ${out.absolute.dir}/classes...</echo>");


    writer.println("                    <getemmafilter");
    writer.println("                            appPackage=\"${project.app.package}\"");
    writer.println("                            libraryPackagesRefId=\"project.library.packages\"");
    writer.println("                            filterOut=\"emma.default.filter\"/>");


    writer.println("                    <property name=\"emma.coverage.absolute.file\" location=\"${out.absolute.dir}/coverage.em\" />");


    writer.println("                    <emma enabled=\"true\">");
    writer.println("                        <instr verbosity=\"${verbosity}\"");
    writer.println("                               mode=\"overwrite\"");
    writer.println("                               instrpath=\"${out.absolute.dir}/classes\"");
    writer.println("                               outdir=\"${out.absolute.dir}/classes\"");
    writer.println("                               metadatafile=\"${emma.coverage.absolute.file}\">");
    writer.println("                            <filter excludes=\"${emma.default.filter}\" />");
    writer.println("                            <filter value=\"${emma.filter}\" />");
    writer.println("                        </instr>");
    writer.println("                    </emma>");
    writer.println("                </then>");
    writer.println("            </if>");

    writer.println("            <if condition=\"${project.is.library}\">");
    writer.println("                <then>");
    writer.println("                    <echo level=\"info\">Creating library output jar file...</echo>");
    writer.println("                    <property name=\"out.library.jar.file\" location=\"${out.absolute.dir}/classes.jar\" />");
    writer.println("                    <if>");
    writer.println("                        <condition>");
    writer.println("                            <length string=\"${android.package.excludes}\" trim=\"true\" when=\"greater\" length=\"0\" />");
    writer.println("                        </condition>");
    writer.println("                        <then>");
    writer.println("                            <echo level=\"info\">Custom jar packaging exclusion: ${android.package.excludes}</echo>");
    writer.println("                        </then>");
    writer.println("                    </if>");

    writer.println("                    <propertybyreplace name=\"project.app.package.path\" input=\"${project.app.package}\" replace=\".\" with=\"/\" />");

    writer.println("                    <jar destfile=\"${out.library.jar.file}\">");
    writer.println("                        <fileset dir=\"${out.classes.absolute.dir}\"");
    writer.println("                                includes=\"**/*.class\"");
    writer.println("                                excludes=\"${project.app.package.path}/R.class ${project.app.package.path}/R$*.class ${project.app.package.path}/BuildConfig.class\"/>");
    writer.println("                        <fileset dir=\"${source.absolute.dir}\" excludes=\"**/*.java ${android.package.excludes}\" />");
    writer.println("                    </jar>");
    writer.println("                </then>");
    writer.println("            </if>");

    writer.println("        </do-only-if-manifest-hasCode>");
    writer.println("    </target>");





    writer.println("  <loadproperties srcFile=\"project.properties\" />");

    writer.println("  <fail message=\"sdk.dir is missing. Make sure to generate local.properties using 'android update project'\" unless=\"sdk.dir\" />");

    writer.println("  <import file=\"custom_rules.xml\" optional=\"true\" />");

    writer.println("  <!-- version-tag: 1 -->");  // should this be 'custom' instead of 1?
    writer.println("  <import file=\"${sdk.dir}/tools/ant/build.xml\" />");

    writer.println("</project>");
    writer.flush();
    writer.close();
  }

  private void writeProjectProps(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("target=" + sdkTarget);
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

  static final String ICON_96 = "icon-96.png";
  static final String ICON_72 = "icon-72.png";
  static final String ICON_48 = "icon-48.png";
  static final String ICON_36 = "icon-36.png";

  static final String ICON_WATCHFACE_CIRCULAR = "preview_circular.png";
  static final String ICON_WATCHFACE_RECTANGULAR = "preview_rectangular.png";  
  
  private void writeRes(File resFolder,
                        String className) throws SketchException {
    File layoutFolder = mkdirs(resFolder, "layout");
    File mainActivityLayoutFile = new File(layoutFolder, "main.xml");
    writeResLayoutMainActivity(mainActivityLayoutFile);

    if (publishOption == WALLPAPER) {
      File xmlFolder = mkdirs(resFolder, "xml");
      File mainServiceWallpaperFile = new File(xmlFolder, "wallpaper.xml");
      writeResXMLWallpaper(mainServiceWallpaperFile);
            
      File valuesFolder = mkdirs(resFolder, "values");
      File mainServiceStringFile = new File(valuesFolder, "strings.xml");
      writeResStringsWallpaper(mainServiceStringFile);      
    }
    
//    File mainFragmentLayoutFile = new File(layoutFolder, "fragment_main.xml");
//    writeResLayoutMainFragment(mainFragmentLayoutFile);

    // write the icon files
    File sketchFolder = sketch.getFolder();
    File localIcon36 = new File(sketchFolder, ICON_36);
    File localIcon48 = new File(sketchFolder, ICON_48);
    File localIcon72 = new File(sketchFolder, ICON_72);
    File localIcon96 = new File(sketchFolder, ICON_96);

//    File drawableFolder = new File(resFolder, "drawable");
//    drawableFolder.mkdirs()
    File buildIcon48 = new File(resFolder, "drawable/icon.png");
    File buildIcon36 = new File(resFolder, "drawable-ldpi/icon.png");
    File buildIcon72 = new File(resFolder, "drawable-hdpi/icon.png");
    File buildIcon96 = new File(resFolder, "drawable-xhdpi/icon.png");

    if (!localIcon36.exists() &&
        !localIcon48.exists() &&
        !localIcon72.exists() &&
        !localIcon96.exists()) {
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
      } catch (IOException e) {
        System.err.println("Problem while copying icons.");
        e.printStackTrace();
      }
    }

    
    if (publishOption == WATCHFACE) {
      File xmlFolder = mkdirs(resFolder, "xml");
      File mainServiceWatchFaceFile = new File(xmlFolder, "watch_face.xml");
      writeResXMLWatchFace(mainServiceWatchFaceFile);      
      
      // write the preview files
      File localPrevCircle = new File(sketchFolder, ICON_WATCHFACE_CIRCULAR);
      File localPrevRect = new File(sketchFolder, ICON_WATCHFACE_RECTANGULAR);
      
      File buildPrevCircle = new File(resFolder, "drawable/" + ICON_WATCHFACE_CIRCULAR);
      File buildPrevRect = new File(resFolder, "drawable/" + ICON_WATCHFACE_RECTANGULAR);
      
      if (!localPrevCircle.exists()) {
//        if (buildPrevCircle.getParentFile().mkdirs()) {
          try {
            Util.copyFile(mode.getContentFile("icons/" + ICON_WATCHFACE_CIRCULAR), buildPrevCircle);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
//        } else {
//          System.err.println("Could not create \"drawable\" folder.");
//        }        
      } else {
//        if (new File(resFolder, "drawable").mkdirs()) {
          try {
            Util.copyFile(localPrevCircle, buildPrevCircle);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
//        }        
      }

      if (!localPrevRect.exists())  {
//        if (buildPrevRect.getParentFile().mkdirs()) {
          try {
            Util.copyFile(mode.getContentFile("icons/" + ICON_WATCHFACE_RECTANGULAR), buildPrevRect);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
//        } else {
//          System.err.println("Could not create \"drawable\" folder.");
//        }        
      } else {
//        if (new File(resFolder, "drawable").mkdirs()) {
          try {
            Util.copyFile(localPrevCircle, buildPrevRect);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
//        }        
      }     
    }
    
    
    
//    final File valuesFolder = mkdirs(resFolder, "values");
//    final File stringsFile = new File(valuesFolder, "strings.xml");
//    writeResValuesStrings(stringsFile, className);
  }


  private File mkdirs(final File parent, final String name) throws SketchException {
    final File result = new File(parent, name);
    if (!(result.exists() || result.mkdirs())) {
      throw new SketchException("Could not create " + result);
    }
    return result;
  }


  private void writeMainClass(final File srcDirectory, String renderer) {
    if (publishOption == FRAGMENT) {
      writeFragmentActivity(srcDirectory);
    } else if (publishOption == WALLPAPER) {
      writeWallpaperService(srcDirectory);
    } else if (publishOption == WATCHFACE) {
      if (renderer != null && (renderer.equals("P2D") || renderer.equals("P3D"))) {
        writeWatchFaceGLESService(srcDirectory);  
      } else {
        writeWatchFaceCanvasService(srcDirectory);  
      }      
    } else if (publishOption == CARDBOARD) {
      writeCardboardActivity(srcDirectory);
    }
  }

  
  private void writeFragmentActivity(final File srcDirectory) {
    File mainActivityFile = new File(new File(srcDirectory, manifest.getPackageName().replace(".", "/")),
        "MainActivity.java");
    final PrintWriter writer = PApplet.createWriter(mainActivityFile);
    writer.println("package " + manifest.getPackageName() +";");
    writer.println("import android.app.Activity;");
    writer.println("import android.os.Bundle;");    
    writer.println("import android.view.View;");
    writer.println("import android.view.ViewGroup;");    
    writer.println("import android.widget.FrameLayout;");
    writer.println("import android.app.FragmentTransaction;");
    writer.println("import processing.android.PFragment;");
    writer.println("import processing.core.PApplet;");
    writer.println("public class MainActivity extends Activity {");
    writer.println("  private static final String MAIN_FRAGMENT_TAG = \"main_fragment\";");
    writer.println("  private static final int viewId = View.generateViewId();");
    writer.println("  PFragment fragment;");
    writer.println("  @Override");
    writer.println("  protected void onCreate(Bundle savedInstanceState) {");
    writer.println("    super.onCreate(savedInstanceState);");    
    writer.println("    FrameLayout frame = new FrameLayout(this);");
    writer.println("    frame.setId(viewId);");
    writer.println("    setContentView(frame, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, "
        + "ViewGroup.LayoutParams.MATCH_PARENT));");
    writer.println("    PApplet sketch = new " + sketchClassName + "();");    
    writer.println("    if (savedInstanceState == null) {");
    writer.println("      fragment = new PFragment();");
    writer.println("      fragment.setSketch(sketch);");    
    writer.println("      FragmentTransaction ft = getFragmentManager().beginTransaction();");
    writer.println("      ft.add(frame.getId(), fragment, MAIN_FRAGMENT_TAG).commit();");
    writer.println("    } else {");
    writer.println("      fragment = (PFragment) getFragmentManager().findFragmentByTag(MAIN_FRAGMENT_TAG);");
    writer.println("      fragment.setSketch(sketch);");
    writer.println("    }");    
    writer.println("  }");
    writer.println("  @Override");
    writer.println("  public void onBackPressed() {");
    writer.println("    fragment.onBackPressed();");
    writer.println("    super.onBackPressed();");
    writer.println("  }");
    writer.println("}");
    writer.flush();
    writer.close();    
  }
  
  
  private void writeWallpaperService(final File srcDirectory) {
    File mainServiceFile = new File(new File(srcDirectory, manifest.getPackageName().replace(".", "/")),
        "MainService.java");
    final PrintWriter writer = PApplet.createWriter(mainServiceFile);
    writer.println("package " + manifest.getPackageName() +";");    
    writer.println("import processing.android.PWallpaper;");
    writer.println("import processing.core.PApplet;");
    writer.println("public class MainService extends PWallpaper {");
    writer.println("  @Override");
    writer.println("  public PApplet createSketch() {");
    writer.println("    PApplet sketch = new " + sketchClassName + "();");
    writer.println("    return sketch;");
    writer.println("  }");
    writer.println("}");
    writer.flush();
    writer.close();  
  }
  
  
  private void writeWatchFaceGLESService(final File srcDirectory) {
    File mainServiceFile = new File(new File(srcDirectory, manifest.getPackageName().replace(".", "/")),
        "MainService.java");
    final PrintWriter writer = PApplet.createWriter(mainServiceFile);
    writer.println("package " + manifest.getPackageName() +";");    
    writer.println("import processing.android.PWatchFaceGLES;");
    writer.println("import processing.core.PApplet;");
    writer.println("public class MainService extends PWatchFaceGLES {");
    writer.println("  public MainService() {");
    writer.println("    super();");
    writer.println("    PApplet sketch = new " + sketchClassName + "();");
    writer.println("    setSketch(sketch);");
    writer.println("  }");
    writer.println("}");
    writer.flush();
    writer.close();  
  }

  
  private void writeWatchFaceCanvasService(final File srcDirectory) {
    File mainServiceFile = new File(new File(srcDirectory, manifest.getPackageName().replace(".", "/")),
        "MainService.java");
    final PrintWriter writer = PApplet.createWriter(mainServiceFile);
    writer.println("package " + manifest.getPackageName() +";");    
    writer.println("import processing.android.PWatchFaceCanvas;");
    writer.println("import processing.core.PApplet;");
    writer.println("public class MainService extends PWatchFaceCanvas {");
    writer.println("  public MainService() {");
    writer.println("    super();");
    writer.println("    PApplet sketch = new " + sketchClassName + "();");
    writer.println("    setSketch(sketch);");
    writer.println("  }");
    writer.println("}");
    writer.flush();
    writer.close();  
  }  
  
  
  private void writeCardboardActivity(final File srcDirectory) {
    File mainServiceFile = new File(new File(srcDirectory, manifest.getPackageName().replace(".", "/")),
        "MainActivity.java");    
    final PrintWriter writer = PApplet.createWriter(mainServiceFile);
    writer.println("package " + manifest.getPackageName() +";");        
    writer.println("import android.os.Bundle;");
    writer.println("import processing.cardboard.PCardboard;");
    writer.println("public class MainActivity extends PCardboard {");
    writer.println("  @Override");
    writer.println("  public void onCreate(Bundle savedInstanceState) {");
    writer.println("    super.onCreate(savedInstanceState);");
    writer.println("    CardboardSketch sketch = new CardboardSketch();");
    writer.println("    setSketch(sketch);");
    writer.println("    init(sketch);");
    writer.println("    setConvertTapIntoTrigger(true);");
    writer.println("  }");
    writer.println("}");
    writer.flush();
    writer.close();    
  }

  private void writeResLayoutMainActivity(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<fragment xmlns:android=\"http://schemas.android.com/apk/res/android\"");
    writer.println("    xmlns:tools=\"http://schemas.android.com/tools\"");
    writer.println("    android:id=\"@+id/fragment\"");
    writer.println("    android:name=\"." + sketchClassName + "\"");
    writer.println("    tools:layout=\"@layout/fragment_main\"");
    writer.println("    android:layout_width=\"match_parent\"");
    writer.println("    android:layout_height=\"match_parent\" />");
    writer.flush();
    writer.close();
  }

  private void writeResXMLWallpaper(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<wallpaper xmlns:android=\"http://schemas.android.com/apk/res/android\"");
    writer.println("    android:thumbnail=\"@drawable/icon\"");
    writer.println("    android:description=\"@string/app_name\" />");
    writer.flush();
    writer.close();    
  }
  
  private void writeResStringsWallpaper(final File file) {
     final PrintWriter writer = PApplet.createWriter(file);
     writer.println("<resources>");
     writer.println("  <string name=\"app_name\">" + sketchClassName + "</string>");
     writer.println("</resources>");
     writer.flush();
     writer.close();    
  }
  
  private void writeResXMLWatchFace(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<wallpaper xmlns:android=\"http://schemas.android.com/apk/res/android\" />");
    writer.flush();
    writer.close();     
  }
  
/*
  private void writeResLayoutMainFragment(final File file) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
    writer.println("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"");
    writer.println("              android:orientation=\"vertical\"");
    writer.println("              android:layout_width=\"fill_parent\"");
    writer.println("              android:layout_height=\"fill_parent\">");
    writer.println("</LinearLayout>");
  }
*/

  // This recommended to be a string resource so that it can be localized.
  // nah.. we're gonna be messing with it in the GUI anyway...
  // people can edit themselves if they need to
//  private static void writeResValuesStrings(final File file,
//                                            final String className) {
//    final PrintWriter writer = PApplet.createWriter(file);
//    writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
//    writer.println("<resources>");
//    writer.println("  <string name=\"app_name\">" + className + "</string>");
//    writer.println("</resources>");
//    writer.flush();
//    writer.close();
//  }

/*
  private void copySupportV4(File libsFolder) throws SketchException {
    File sdkLocation = sdk.getSdkFolder();
    File supportV4Jar = new File(sdkLocation, "extras/android/support/v4/android-support-v4.jar");
    if (!supportV4Jar.exists()) {
      SketchException sketchException =
          new SketchException("Please install support repository from SDK manager");
      throw sketchException;
    } else {
      try {
        Base.copyFile(supportV4Jar, new File(libsFolder, "android-support-v4.jar"));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
*/
  /**
   * For each library, copy .jar and .zip files to the 'libs' folder,
   * and copy anything else to the 'assets' folder.
   */
  private void copyLibraries(final File libsFolder,
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
//  private void copyLibraries(final File libsFolder,
//                             final File assetsFolder) throws IOException {
//    // Copy any libraries to the 'libs' folder
//    for (Library library : getImportedLibraries()) {
//      File libraryFolder = new File(library.getPath());
//      // in the list is a File object that points the
//      // library sketch's "library" folder
//      final File exportSettings = new File(libraryFolder, "export.txt");
//      final HashMap<String, String> exportTable =
//        Base.readSettings(exportSettings);
//      final String androidList = exportTable.get("android");
//      String exportList[] = null;
//      if (androidList != null) {
//        exportList = PApplet.splitTokens(androidList, ", ");
//      } else {
//        exportList = libraryFolder.list();
//      }
//      for (int i = 0; i < exportList.length; i++) {
//        exportList[i] = PApplet.trim(exportList[i]);
//        if (exportList[i].equals("") || exportList[i].equals(".")
//            || exportList[i].equals("..")) {
//          continue;
//        }
//
//        final File exportFile = new File(libraryFolder, exportList[i]);
//        if (!exportFile.exists()) {
//          System.err.println("File " + exportList[i] + " does not exist");
//        } else if (exportFile.isDirectory()) {
//          System.err.println("Ignoring sub-folder \"" + exportList[i] + "\"");
//        } else {
//          final String name = exportFile.getName();
//          final String lcname = name.toLowerCase();
//          if (lcname.endsWith(".zip") || lcname.endsWith(".jar")) {
//            // As of r4 of the Android SDK, it looks like .zip files
//            // are ignored in the libs folder, so rename to .jar
//            final String jarName =
//              name.substring(0, name.length() - 4) + ".jar";
//            Base.copyFile(exportFile, new File(libsFolder, jarName));
//          } else {
//            // just copy other files over directly
//            Base.copyFile(exportFile, new File(assetsFolder, name));
//          }
//        }
//      }
//    }
//  }


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
}

// http://www.avanderw.co.za/preventing-calls-to-system-exit-in-java/
class SystemExitControl {

  @SuppressWarnings("serial")
  public static class ExitTrappedException extends SecurityException {
  }

  public static void forbidSystemExitCall() {
    final SecurityManager securityManager = new SecurityManager() {
      @Override
      public void checkPermission(Permission permission) {
        if (permission.getName().contains("exitVM")) {
          throw new ExitTrappedException();
        }
      }
    };
    System.setSecurityManager(securityManager);
  }

  public static void enableSystemExitCall() {
    System.setSecurityManager(null);
  }
}
