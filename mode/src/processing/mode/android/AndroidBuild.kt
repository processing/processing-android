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
package processing.mode.android

import org.gradle.tooling.*

import processing.app.*

import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.java.JavaBuild

import java.io.*
import java.util.*

/**
 * Class with all the infrastructure needed to build a sketch in the Android
 * mode and run it either on the device or in the emulator, using Gradle as the
 * build system. It also exports the sketch as a Gradle project file to build
 * from the command line or import into Android Studio, and a signed and aligned
 * package ready to upload to the Play Store.
 */
internal class AndroidBuild(sketch: Sketch?, mode: AndroidMode, comp: Int) : JavaBuild(sketch) {
    var appComponent = APP
    private val sdk: AndroidSDK?
    private val coreZipFile: File?

    /** whether this is a "debug" or "release" build  */
    private var target: String? = null

    /** The manifest for the sketch being built  */
    private var manifest: Manifest? = null

    /** temporary folder safely inside a 8.3-friendly folder  */
    private var tmpFolder: File? = null

    /** Determines which gradle build template will be used  */
    private var exportProject = false

    /** Renderer used by the sketch  */
    private var renderer: String? = ""

    /** Name of the Gradle module in the project, either app or wear  */
    private var module = ""

    val packageName: String
        get() = manifest!!.packageName

    val isWear: Boolean
        get() = appComponent == WATCHFACE

    fun cleanup() {
        tmpFolder!!.deleteOnExit()
    }

    fun usesOpenGL(): Boolean {
        return renderer != null && (renderer == "P2D" || renderer == "P3D")
    }

    val pathForAPK: String?
        get() {
            val suffix = if (target == "release") "release_unsigned" else "debug"
            val apkName = pathToAPK + sketch.name.toLowerCase() + "_" + suffix + ".apk"
            val apkFile = File(tmpFolder, apkName)
            return if (!apkFile.exists()) {
                null
            } else apkFile.absolutePath
        }

    /**
     * Constructor.
     * @param sketch the sketch to be built
     * @param mode reference to the mode
     * @param appComp component (regular handheld app, wallpaper, watch face, VR, AR)
     * @param emu build to run in emulator or on device if false
     */
    init {
        appComponent = comp
        sdk = mode.getSdk()
        coreZipFile = mode.coreZipLocation
        module = if (appComponent == WATCHFACE) "wear" else "app"
    }

    /**
     * Build into temporary folders (needed for the Windows 8.3 bugs in the Android SDK).
     * @param target "debug" or "release"
     * @throws SketchException
     * @throws IOException
     */
    @Throws(IOException::class, SketchException::class)
    fun build(target: String?): File? {
        this.target = target
        val folder = createProject(true) ?: return null
        return if (!gradleBuild()) null else folder
    }

    /**
     * Create an Gradle Android project folder, and run the preprocessor on the
     * sketch. Creates the top and app modules in the case of regular, VR, AR and
     * wallpapers, and top, mobile and wear modules in the case of watch faces.
     */
    @Throws(IOException::class, SketchException::class)
    protected fun createProject(external: Boolean): File? {
        tmpFolder = createTempBuildFolder(sketch)
        println(getTextString("android_build.error.build_folder", tmpFolder!!.absolutePath))

        // Create the 'src' folder with the preprocessed code.
        srcFolder = File(tmpFolder, "$module/src/main/java")

        // Needed in the the parent JavaBuild class
        binFolder = srcFolder

        if (Base.DEBUG) {
            Platform.openFolder(tmpFolder)
        }

        manifest = Manifest(sketch, appComponent, mode.folder, false)
        manifest!!.setSdkTarget(TARGET_SDK)

        // build the preproc and get to work
        val preproc = AndroidPreprocessor(sketch, packageName)

        // On Android, this init will throw a SketchException if there's a problem with size()
        val info = preproc.initSketchSize(sketch.mainProgram)

        preproc.initSketchSmooth(sketch.mainProgram)
        sketchClassName = preprocess(srcFolder, packageName, preproc, false)

        if (sketchClassName != null) {
            renderer = info.renderer
            writeMainClass(srcFolder, renderer, external)
            createTopModule("':$module'")
            createAppModule(module)
        }

        return tmpFolder
    }

    @Throws(SketchException::class)
    protected fun gradleBuild(): Boolean {
        val connection = GradleConnector.newConnector()
                .forProjectDirectory(tmpFolder)
                .connect()

        var success = false

        val build = connection.newBuild()
        build.setStandardOutput(System.out)
        build.setStandardError(System.err)

        success = try {
            if (target == "debug") build.forTasks("assembleDebug") else build.forTasks("assembleRelease")
            build.run()
            renameAPK()
            true
        } catch (e: UnsupportedVersionException) {
            e.printStackTrace()
            false
        } catch (e: BuildException) {
            e.printStackTrace()
            false
        } catch (e: BuildCancelledException) {
            e.printStackTrace()
            false
        } catch (e: GradleConnectionException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            connection.close()
        }

        return success
    }

    // ---------------------------------------------------------------------------
    // Gradle modules

    @Throws(IOException::class)
    private fun createTopModule(projectModules: String) {
        val buildTemplate = mode.getContentFile("templates/$TOP_GRADLE_BUILD_TEMPLATE")
        val buildlFile = File(tmpFolder, "build.gradle")

        Util.copyFile(buildTemplate, buildlFile)
        writeLocalProps(File(tmpFolder, "local.properties"))
        AndroidUtil.writeFile(File(tmpFolder, "gradle.properties"), arrayOf("org.gradle.jvmargs=-Xmx1536m"))

        val settingsTemplate = mode.getContentFile("templates/$GRADLE_SETTINGS_TEMPLATE")
        val settingsFile = File(tmpFolder, "settings.gradle")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@project_modules@@"] = projectModules
        AndroidUtil.createFileFromTemplate(settingsTemplate, settingsFile, replaceMap)
    }

    @Throws(SketchException::class, IOException::class)
    private fun createAppModule(moduleName: String) {
        val moduleFolder = AndroidUtil.createPath(tmpFolder, moduleName)
        val minSdk: String?
        val tmplFile: String

        if (appComponent == AR) {
            minSdk = MIN_SDK_AR
            tmplFile = if (exportProject) AR_GRADLE_BUILD_TEMPLATE else AR_GRADLE_BUILD_ECJ_TEMPLATE
        } else if (appComponent == VR) {
            minSdk = MIN_SDK_VR
            tmplFile = if (exportProject) VR_GRADLE_BUILD_TEMPLATE else VR_GRADLE_BUILD_ECJ_TEMPLATE
        } else if (appComponent == WATCHFACE) {
            minSdk = MIN_SDK_WATCHFACE
            tmplFile = if (exportProject) WEAR_GRADLE_BUILD_TEMPLATE else WEAR_GRADLE_BUILD_ECJ_TEMPLATE
        } else {
            minSdk = MIN_SDK_APP
            tmplFile = if (exportProject) APP_GRADLE_BUILD_TEMPLATE else APP_GRADLE_BUILD_ECJ_TEMPLATE
        }

        val appBuildTemplate = mode.getContentFile("templates/$tmplFile")
        val appBuildFile = File(moduleFolder, "build.gradle")
        val replaceMap = HashMap<String, String?>()

        replaceMap["@@tools_folder@@"] = Base.getToolsFolder().path.replace('\\', '/')
        replaceMap["@@target_platform@@"] = TARGET_SDK
        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@min_sdk@@"] = minSdk
        replaceMap["@@target_sdk@@"] = TARGET_SDK
        replaceMap["@@support_version@@"] = SUPPORT_VER
        replaceMap["@@play_services_version@@"] = PLAY_SERVICES_VER
        replaceMap["@@wear_version@@"] = WEAR_VER
        replaceMap["@@gvr_version@@"] = GVR_VER
        replaceMap["@@gar_version@@"] = GAR_VER
        replaceMap["@@version_code@@"] = manifest!!.versionCode
        replaceMap["@@version_name@@"] = manifest!!.versionName

        AndroidUtil.createFileFromTemplate(appBuildTemplate, appBuildFile, replaceMap)
        AndroidUtil.writeFile(File(moduleFolder, "proguard-rules.pro"), arrayOf("# Add project specific ProGuard rules here."))

        val libsFolder = AndroidUtil.createPath(moduleFolder, "libs")
        val mainFolder = File(moduleFolder, "src/main")
        val resFolder = AndroidUtil.createPath(mainFolder, "res")
        val assetsFolder = AndroidUtil.createPath(mainFolder, "assets")

        writeRes(resFolder)

        val tempManifest = File(mainFolder, "AndroidManifest.xml")
        manifest!!.writeCopy(tempManifest, sketchClassName)

        Util.copyFile(coreZipFile, File(libsFolder, "processing-core.jar"))

        // Copy any imported libraries (their libs and assets),
        // and anything in the code folder contents to the project.
        copyImportedLibs(libsFolder, mainFolder, assetsFolder)
        copyCodeFolder(libsFolder)

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
        val sketchDataFolder = sketch.dataFolder

        if (sketchDataFolder.exists()) {
            Util.copyDir(sketchDataFolder, assetsFolder)
        }

        // Do the same for the 'res' folder. The user can copy an entire res folder
        // into the sketch's folder, and it will be used in the project!
        val sketchResFolder = File(sketch.folder, "res")

        if (sketchResFolder.exists()) {
            Util.copyDir(sketchResFolder, resFolder)
        }
    }

    // ---------------------------------------------------------------------------
    // Templates

    private fun writeMainClass(srcDirectory: File,
                               renderer: String?, external: Boolean) {
        val comp = appComponent
        val permissions = manifest!!.permissions

        if (comp == APP) {
            writeFragmentActivity(srcDirectory, permissions, external)
        } else if (comp == WALLPAPER) {
            writeWallpaperService(srcDirectory, permissions, external)
        } else if (comp == WATCHFACE) {
            if (usesOpenGL()) {
                writeWatchFaceGLESService(srcDirectory, permissions, external)
            } else {
                writeWatchFaceCanvasService(srcDirectory, permissions, external)
            }
        } else if (comp == VR) {
            writeVRActivity(srcDirectory, permissions, external)
        } else if (comp == AR) {
            writeARActivity(srcDirectory, permissions, external)
        }
    }

    private fun writeFragmentActivity(srcDirectory: File,
                                      permissions: Array<String>, external: Boolean) {
        val javaTemplate = mode.getContentFile("templates/$APP_ACTIVITY_TEMPLATE")
        val javaFile = File(File(srcDirectory, packageName.replace(".", "/")), "MainActivity.java")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        replaceMap["@@external@@"] = if (external) "sketch.setExternal(true);" else ""

        AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap)
    }

    private fun writeWallpaperService(srcDirectory: File,
                                      permissions: Array<String>, external: Boolean) {
        val javaTemplate = mode.getContentFile("templates/$WALLPAPER_SERVICE_TEMPLATE")
        val javaFile = File(File(srcDirectory, packageName.replace(".", "/")), "MainService.java")
        val replaceMap = HashMap<String, String>()
        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        replaceMap["@@external@@"] = if (external) "sketch.setExternal(true);" else ""
        AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap)
    }

    private fun writeWatchFaceGLESService(srcDirectory: File,
                                          permissions: Array<String>, external: Boolean) {
        val javaTemplate = mode.getContentFile("templates/$WATCHFACE_SERVICE_TEMPLATE")
        val javaFile = File(File(srcDirectory, packageName.replace(".", "/")), "MainService.java")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@watchface_classs@@"] = "PWatchFaceGLES"
        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        replaceMap["@@external@@"] = if (external) "sketch.setExternal(true);" else ""

        AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap)
    }

    private fun writeWatchFaceCanvasService(srcDirectory: File,
                                            permissions: Array<String>, external: Boolean) {

        val javaTemplate = mode.getContentFile("templates/$WATCHFACE_SERVICE_TEMPLATE")
        val javaFile = File(File(srcDirectory, packageName.replace(".", "/")), "MainService.java")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@watchface_classs@@"] = "PWatchFaceCanvas"
        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        replaceMap["@@external@@"] = if (external) "sketch.setExternal(true);" else ""

        AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap)
    }

    private fun writeVRActivity(srcDirectory: File, permissions: Array<String>,
                                external: Boolean) {

        val javaTemplate = mode.getContentFile("templates/$VR_ACTIVITY_TEMPLATE")
        val javaFile = File(File(srcDirectory, packageName.replace(".", "/")), "MainActivity.java")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        replaceMap["@@external@@"] = if (external) "sketch.setExternal(true);" else ""

        AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap)
    }

    private fun writeARActivity(srcDirectory: File, permissions: Array<String>,
                                external: Boolean) {

        val javaTemplate = mode.getContentFile("templates/$AR_ACTIVITY_TEMPLATE")
        val javaFile = File(File(srcDirectory, packageName.replace(".", "/")), "MainActivity.java")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@package_name@@"] = packageName
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        replaceMap["@@external@@"] = if (external) "sketch.setExternal(true);" else ""

        AndroidUtil.createFileFromTemplate(javaTemplate, javaFile, replaceMap)
    }

    private fun writeResLayoutMainActivity(layoutFolder: File) {

        val xmlTemplate = mode.getContentFile("templates/$LAYOUT_ACTIVITY_TEMPLATE")
        val xmlFile = File(layoutFolder, "main.xml")
        val replaceMap = HashMap<String, String>()

        replaceMap["@@sketch_class_name@@"] = sketchClassName

        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap)
    }

    private fun writeResStylesFragment(valuesFolder: File) {
        val xmlTemplate = mode.getContentFile("templates/$STYLES_FRAGMENT_TEMPLATE")
        val xmlFile = File(valuesFolder, "styles.xml")

        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile)
    }

    private fun writeResStylesVR(valuesFolder: File) {
        val xmlTemplate = mode.getContentFile("templates/$STYLES_VR_TEMPLATE")
        val xmlFile = File(valuesFolder, "styles.xml")

        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile)
    }

    private fun writeResStylesAR(valuesFolder: File) {
        val xmlTemplate = mode.getContentFile("templates/$STYLES_AR_TEMPLATE")
        val xmlFile = File(valuesFolder, "styles.xml")
        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile)
    }

    private fun writeResXMLWallpaper(xmlFolder: File) {
        val xmlTemplate = mode.getContentFile("templates/$XML_WALLPAPER_TEMPLATE")
        val xmlFile = File(xmlFolder, "wallpaper.xml")
        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile)
    }

    private fun writeResStringsWallpaper(valuesFolder: File) {
        val xmlTemplate = mode.getContentFile("templates/$STRINGS_WALLPAPER_TEMPLATE")
        val xmlFile = File(valuesFolder, "strings.xml")
        val replaceMap = HashMap<String, String>()
        replaceMap["@@sketch_class_name@@"] = sketchClassName
        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap)
    }

    private fun writeResXMLWatchFace(xmlFolder: File) {
        val xmlTemplate = mode.getContentFile("templates/$XML_WATCHFACE_TEMPLATE")
        val xmlFile = File(xmlFolder, "watch_face.xml")
        AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile)
    }

    private fun writeLocalProps(file: File) {
        val writer = PApplet.createWriter(file)
        val sdkPath = sdk!!.folder.absolutePath

        if (Platform.isWindows()) {
            // Windows needs backslashes escaped, or it will also accept forward
            // slashes in the build file. We're using the forward slashes since this
            // path gets concatenated with a lot of others that use forwards anyway.
            writer.println("sdk.dir=" + sdkPath.replace('\\', '/'))
        } else {
            writer.println("sdk.dir=$sdkPath")
        }

        writer.flush()
        writer.close()
    }

    @Throws(SketchException::class)
    private fun writeRes(resFolder: File) {
        val layoutFolder = AndroidUtil.createPath(resFolder, "layout")
        writeResLayoutMainActivity(layoutFolder)

        val comp = appComponent

        if (comp == APP) {
            val valuesFolder = AndroidUtil.createPath(resFolder, "values")
            writeResStylesFragment(valuesFolder)
        } else if (comp == WALLPAPER) {
            val xmlFolder = AndroidUtil.createPath(resFolder, "xml")
            writeResXMLWallpaper(xmlFolder)
            val valuesFolder = AndroidUtil.createPath(resFolder, "values")
            writeResStringsWallpaper(valuesFolder)
        } else if (comp == WATCHFACE) {
            val xmlFolder = AndroidUtil.createPath(resFolder, "xml")
            writeResXMLWatchFace(xmlFolder)
        } else if (comp == VR) {
            val valuesFolder = AndroidUtil.createPath(resFolder, "values")
            writeResStylesVR(valuesFolder)
        } else if (comp == AR) {
            val valuesFolder = AndroidUtil.createPath(resFolder, "values")
            writeResStylesAR(valuesFolder)
        }

        val sketchFolder = sketch.folder

        writeLauncherIconFiles(sketchFolder, resFolder)

        if (comp == WATCHFACE) {
            // Need the preview icons for watch faces.
            writeWatchFaceIconFiles(sketchFolder, resFolder)
        }
    }

    // ---------------------------------------------------------------------------
    // Icons

    private fun writeLauncherIconFiles(sketchFolder: File, resFolder: File) {
        writeIconFiles(sketchFolder, resFolder, SKETCH_LAUNCHER_ICONS, SKETCH_OLD_LAUNCHER_ICONS, BUILD_LAUNCHER_ICONS)
    }

    private fun writeWatchFaceIconFiles(sketchFolder: File, resFolder: File) {
        writeIconFiles(sketchFolder, resFolder, SKETCH_WATCHFACE_ICONS, null, BUILD_WATCHFACE_ICONS)
    }

    private fun writeIconFiles(sketchFolder: File, resFolder: File,
                               sketchIconNames: Array<String>, oldIconNames: Array<String>?, buildIconNames: Array<String>) {
        val localIcons = AndroidUtil.getFileList(sketchFolder, sketchIconNames, oldIconNames)
        val buildIcons = AndroidUtil.getFileList(resFolder, buildIconNames)

        if (AndroidUtil.noFileExists(localIcons)) {
            // If no icons are in the sketch folder, then copy all the defaults      
            val defaultIcons = AndroidUtil.getFileList(mode, "icons/", sketchIconNames)
            try {
                for (i in localIcons.indices) {
                    copyIcon(defaultIcons[i], buildIcons[i])
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            // If at least one of the icons already exists, then use that across the board
            try {
                for (i in localIcons.indices) {
                    if (localIcons[i].exists()) copyIcon(localIcons[i], buildIcons[i])
                }
            } catch (e: IOException) {
                System.err.println(getTextString("android_build.error.cannot_copy_icons"))
                e.printStackTrace()
            }
        }
    }

    @Throws(IOException::class)
    private fun copyIcon(srcFile: File, destFile: File) {
        val parent = destFile.parentFile

        if (parent.exists() || parent.mkdirs()) {
            Util.copyFile(srcFile, destFile)
        } else {
            System.err.println(getTextString("android_build.error.cannot_create_icon_folder", destFile.parentFile))
        }
    }

    // ---------------------------------------------------------------------------
    // Export project

    @Throws(IOException::class, SketchException::class)
    fun exportProject(): File {
        target = "debug"
        exportProject = true

        val projectFolder = createProject(false)
        exportProject = false
        val exportFolder = createExportFolder("android")

        Util.copyDir(projectFolder, exportFolder)
        installGradlew(exportFolder)

        return exportFolder
    }

    // ---------------------------------------------------------------------------
    // Export package
    @Throws(Exception::class)
    fun exportPackage(keyStorePassword: String): File? {
        val projectFolder = build("release") ?: return null
        val signedPackage = signPackage(projectFolder, keyStorePassword) ?: return null

        // Final export folder
        val exportFolder = createExportFolder("build")

        Util.copyDir(File(projectFolder, pathToAPK), exportFolder)

        return exportFolder
    }

    @Throws(Exception::class)
    private fun signPackage(projectFolder: File, keyStorePassword: String): File? {
        val keyStore = AndroidKeyStore.keyStore ?: return null
        val unsignedPackage = File(projectFolder,
                pathToAPK + sketch.name.toLowerCase() + "_release_unsigned.apk")

        if (!unsignedPackage.exists()) return null
        val signedPackage = File(projectFolder,
                pathToAPK + sketch.name.toLowerCase() + "_release_signed.apk")

        JarSigner.signJar(unsignedPackage, signedPackage,
                AndroidKeyStore.ALIAS_STRING, keyStorePassword,
                keyStore.absolutePath, keyStorePassword)

        return zipalignPackage(signedPackage, projectFolder)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun zipalignPackage(signedPackage: File, projectFolder: File): File? {
        val zipAlign = sdk!!.zipAlignTool

        if (zipAlign == null || !zipAlign.exists()) {
            Messages.showWarning(getTextString("android_build.warn.cannot_find_zipalign.title"),
                    getTextString("android_build.warn.cannot_find_zipalign.body"))
            return null
        }

        val alignedPackage = File(projectFolder,
                pathToAPK + sketch.name.toLowerCase() + "_release_signed_aligned.apk")

        val args = arrayOf(
                zipAlign.absolutePath, "-v", "-f", "4",
                signedPackage.absolutePath, alignedPackage.absolutePath
        )

        val alignProcess = Runtime.getRuntime().exec(args)
        // Need to consume output for the process to finish, as discussed here
        // https://stackoverflow.com/questions/5483830/process-waitfor-never-returns 
        // Using StreamPump as in other parts of the mode does not seem to work for some reason
        val reader = BufferedReader(InputStreamReader(alignProcess.inputStream))

        while (reader.readLine() != null) {
        }

        alignProcess.waitFor()

        return if (alignedPackage.exists()) alignedPackage else null
    }
    //---------------------------------------------------------------------------
    // Build utils
    /**
     * Tell the PDE to not complain about android.* packages and others that are
     * part of the OS library set as if they're missing.
     */
    override fun ignorableImport(pkg: String): Boolean {
        if (pkg.startsWith("android.")) return true
        if (pkg.startsWith("java.")) return true
        if (pkg.startsWith("javax.")) return true
        if (pkg.startsWith("org.apache.http.")) return true
        if (pkg.startsWith("org.json.")) return true
        if (pkg.startsWith("org.w3c.dom.")) return true
        if (pkg.startsWith("org.xml.sax.")) return true
        if (pkg.startsWith("processing.core.")) return true
        if (pkg.startsWith("processing.data.")) return true
        if (pkg.startsWith("processing.event.")) return true

        return if (pkg.startsWith("processing.opengl.")) true else false
    }

    /**
     * For each library, copy .jar and .zip files to the 'libs' folder,
     * and copy anything else to the 'assets' folder.
     */
    @Throws(IOException::class)
    private fun copyImportedLibs(libsFolder: File,
                                 mainFolder: File,
                                 assetsFolder: File) {
        for (library in importedLibraries) {

            // add each item from the library folder / export list to the output
            for (exportFile in library.androidExports) {

                val exportName = exportFile.name

                // Skip the GVR and ARCore jars, because gradle will resolve the dependencies
                if (appComponent == VR && exportName.toLowerCase().startsWith("sdk")) continue
                if (appComponent == AR && exportName.toLowerCase().startsWith("core")) continue

                if (!exportFile.exists()) {
                    System.err.println(getTextString("android_build.error.export_file_does_not_exist", exportFile.name))
                } else if (exportFile.isDirectory) {
                    // Copy native library folders to the correct location
                    if (exportName == "armeabi" || exportName == "armeabi-v7a" || exportName == "x86") {
                        Util.copyDir(exportFile, File(libsFolder, exportName))
                    } else if (exportName == "jniLibs") {
                        Util.copyDir(exportFile, File(mainFolder, exportName))
                    } else {
                        // Copy any other directory to the assets folder
                        Util.copyDir(exportFile, File(assetsFolder, exportName))
                    }
                } else if (exportName.toLowerCase().endsWith(".zip")) {
                    // As of r4 of the Android SDK, it looks like .zip files
                    // are ignored in the libs folder, so rename to .jar
                    System.err.println(getTextString("android_build.error.zip_files_not_allowed", exportFile.name))
                    val jarName = exportName.substring(0, exportName.length - 4) + ".jar"
                    Util.copyFile(exportFile, File(libsFolder, jarName))
                } else if (exportName.toLowerCase().endsWith(".jar")) {
                    Util.copyFile(exportFile, File(libsFolder, exportName))
                } else {
                    Util.copyFile(exportFile, File(assetsFolder, exportName))
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun copyCodeFolder(libsFolder: File) {
        // Copy files from the 'code' directory into the 'libs' folder
        val codeFolder = sketch.codeFolder

        if (codeFolder != null && codeFolder.exists()) {
            for (item in codeFolder.listFiles()) {
                if (!item.isDirectory) {
                    val name = item.name
                    val lcname = name.toLowerCase()

                    if (lcname.endsWith(".jar") || lcname.endsWith(".zip")) {
                        val jarName = name.substring(0, name.length - 4) + ".jar"
                        Util.copyFile(item, File(libsFolder, jarName))
                    }
                }
            }
        }
    }

    private fun renameAPK() {
        val suffix = if (target == "release") "release-unsigned" else "debug"
        val apkName = "$pathToAPK$module-$suffix.apk"
        val apkFile = File(tmpFolder, apkName)

        if (apkFile.exists()) {
            val suffixNew = if (target == "release") "release_unsigned" else "debug"

            val apkNameNew = pathToAPK +
                    sketch.name.toLowerCase() + "_" + suffixNew + ".apk"

            val apkFileNew = File(tmpFolder, apkNameNew)

            apkFile.renameTo(apkFileNew)
        }
    }

    private val pathToAPK: String
        private get() = "$module/build/outputs/apk/$target/"

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
    @Throws(IOException::class)
    private fun createTempBuildFolder(sketch: Sketch): File {
        val tmp = File.createTempFile("android", "sketch")

        if (!(tmp.delete() && tmp.mkdir())) {
            throw IOException(getTextString("android_build.error.cannot_create_build_folder", tmp))
        }

        return tmp
    }

    @Throws(IOException::class)
    private fun installGradlew(exportFolder: File) {
        val gradlewFile = mode.getContentFile("mode/gradlew.zip")

        AndroidUtil.extractFolder(gradlewFile, exportFolder, false, true)

        if (Platform.isMacOS() || Platform.isLinux()) {
            val execFile = File(exportFolder, "gradlew")
            execFile.setExecutable(true)
        }
    }

    @Throws(IOException::class)
    private fun createExportFolder(name: String): File {
        return AndroidUtil.createSubFolder(sketch.folder, name)
    }

    companion object {
        const val APP = 0
        const val WALLPAPER = 1
        const val WATCHFACE = 2
        const val VR = 3
        const val AR = 4

        // Minimum SDK's API levels required for each component:
        @JvmField
        var MIN_SDK_APP: String? = null

        @JvmField
        var MIN_SDK_WALLPAPER: String? = null

        @JvmField
        var MIN_SDK_VR: String? = null

        @JvmField
        var MIN_SDK_AR: String? = null

        @JvmField
        var MIN_SDK_WATCHFACE: String? = null

        // Versions of all required dependencies
        @JvmField
        var TARGET_SDK: String? = null

        @JvmField
        var TARGET_PLATFORM: String? = null
        var SUPPORT_VER: String? = null
        var PLAY_SERVICES_VER: String? = null
        var WEAR_VER: String? = null
        var GVR_VER: String? = null
        var GAR_VER: String? = null

        // Main activity or service 
        private const val APP_ACTIVITY_TEMPLATE = "AppActivity.java.tmpl"
        private const val WALLPAPER_SERVICE_TEMPLATE = "WallpaperService.java.tmpl"
        private const val WATCHFACE_SERVICE_TEMPLATE = "WatchFaceService.java.tmpl"
        private const val VR_ACTIVITY_TEMPLATE = "VRActivity.java.tmpl"
        private const val AR_ACTIVITY_TEMPLATE = "ARActivity.java.tmpl"

        // Additional resources
        private const val LAYOUT_ACTIVITY_TEMPLATE = "LayoutActivity.xml.tmpl"
        private const val STYLES_FRAGMENT_TEMPLATE = "StylesFragment.xml.tmpl"
        private const val STYLES_VR_TEMPLATE = "StylesVR.xml.tmpl"
        private const val STYLES_AR_TEMPLATE = "StylesAR.xml.tmpl"
        private const val XML_WALLPAPER_TEMPLATE = "XMLWallpaper.xml.tmpl"
        private const val STRINGS_WALLPAPER_TEMPLATE = "StringsWallpaper.xml.tmpl"
        private const val XML_WATCHFACE_TEMPLATE = "XMLWatchFace.xml.tmpl"

        // Gradle build files
        private const val GRADLE_SETTINGS_TEMPLATE = "Settings.gradle.tmpl"
        private const val TOP_GRADLE_BUILD_TEMPLATE = "TopBuild.gradle.tmpl"
        private const val APP_GRADLE_BUILD_ECJ_TEMPLATE = "AppBuildECJ.gradle.tmpl"
        private const val APP_GRADLE_BUILD_TEMPLATE = "AppBuild.gradle.tmpl"
        private const val VR_GRADLE_BUILD_ECJ_TEMPLATE = "VRBuildECJ.gradle.tmpl"
        private const val VR_GRADLE_BUILD_TEMPLATE = "VRBuild.gradle.tmpl"
        private const val AR_GRADLE_BUILD_ECJ_TEMPLATE = "ARBuildECJ.gradle.tmpl"
        private const val AR_GRADLE_BUILD_TEMPLATE = "ARBuild.gradle.tmpl"
        private const val WEAR_GRADLE_BUILD_ECJ_TEMPLATE = "WearBuildECJ.gradle.tmpl"
        private const val WEAR_GRADLE_BUILD_TEMPLATE = "WearBuild.gradle.tmpl"

        // Launcher and watch face icon files
        val SKETCH_LAUNCHER_ICONS = arrayOf("launcher_36.png", "launcher_48.png",
                "launcher_72.png", "launcher_96.png",
                "launcher_144.png", "launcher_192.png")

        val SKETCH_OLD_LAUNCHER_ICONS = arrayOf("icon-36.png", "icon-48.png",
                "icon-72.png", "icon-96.png",
                "icon-144.png", "icon-192.png")

        val BUILD_LAUNCHER_ICONS = arrayOf("mipmap-ldpi/ic_launcher.png", "mipmap-mdpi/ic_launcher.png",
                "mipmap-hdpi/ic_launcher.png", "mipmap-xhdpi/ic_launcher.png",
                "mipmap-xxhdpi/ic_launcher.png", "mipmap-xxxhdpi/ic_launcher.png")

        val SKETCH_WATCHFACE_ICONS = arrayOf("preview_circular.png",
                "preview_rectangular.png")

        val BUILD_WATCHFACE_ICONS = arrayOf("drawable-nodpi/preview_circular.png",
                "drawable-nodpi/preview_rectangular.png")

        fun initVersions(file: File?) {
            val input: InputStream

            try {
                input = FileInputStream(file)

                val props = Properties()
                props.load(input)

                MIN_SDK_APP = props.getProperty("android-min-app")
                MIN_SDK_WALLPAPER = props.getProperty("android-min-wallpaper")
                MIN_SDK_VR = props.getProperty("android-min-vr")
                MIN_SDK_AR = props.getProperty("android-min-ar")
                MIN_SDK_WATCHFACE = props.getProperty("android-min-wear")

                // Versions of the target sdk, support, play services, wear, VR, and AR are stored in
                // preferences file so they can be changed by the user without having to rebuilt/reinstall
                // the mode.
                TARGET_SDK = Preferences.get("android.sdk.target")
                val defTargetSDK = props.getProperty("android-platform")

                if (TARGET_SDK == null || PApplet.parseInt(TARGET_SDK) != PApplet.parseInt(defTargetSDK)) {
                    TARGET_SDK = defTargetSDK
                    Preferences.set("android.sdk.target", TARGET_SDK)
                }

                TARGET_PLATFORM = "android-$TARGET_SDK"
                SUPPORT_VER = Preferences.get("android.sdk.support")

                val defSupportVer = props.getProperty("com.android.support%support-v4")

                if (SUPPORT_VER == null || !versionCheck(SUPPORT_VER, defSupportVer)) {
                    SUPPORT_VER = defSupportVer
                    Preferences.set("android.sdk.support", SUPPORT_VER)
                }

                PLAY_SERVICES_VER = Preferences.get("android.sdk.play_services")
                val defPlayServicesVer = props.getProperty("com.google.android.gms%play-services-wearable")

                if (PLAY_SERVICES_VER == null || !versionCheck(PLAY_SERVICES_VER, defPlayServicesVer)) {
                    PLAY_SERVICES_VER = defPlayServicesVer
                    Preferences.set("android.sdk.play_services", PLAY_SERVICES_VER)
                }

                WEAR_VER = Preferences.get("android.sdk.wear")
                val defWearVer = props.getProperty("com.google.android.support%wearable")

                if (WEAR_VER == null || !versionCheck(WEAR_VER, defWearVer)) {
                    WEAR_VER = defWearVer
                    Preferences.set("android.sdk.wear", WEAR_VER)
                }

                GVR_VER = Preferences.get("android.sdk.gvr")
                val defVRVer = props.getProperty("com.google.vr")

                if (GVR_VER == null || !versionCheck(GVR_VER, defVRVer)) {
                    GVR_VER = defVRVer
                    Preferences.set("android.sdk.gvr", GVR_VER)
                }

                GAR_VER = Preferences.get("android.sdk.ar")
                val defARVer = props.getProperty("com.google.ar")

                if (GAR_VER == null || !versionCheck(GAR_VER, defARVer)) {
                    GAR_VER = defARVer
                    Preferences.set("android.sdk.ar", GAR_VER)
                }

            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        private fun versionCheck(currentVersion: String?, minVersion: String): Boolean {
            val currentPieces = currentVersion!!.split("\\.").toTypedArray()
            val minPieces = minVersion.split("\\.").toTypedArray()

            if (currentPieces.size == 3 && minPieces.size == 3) {
                val currentMajor = PApplet.parseInt(currentPieces[0], -1)
                val currentMinor = PApplet.parseInt(currentPieces[1], -1)
                val currentMicro = PApplet.parseInt(currentPieces[2], -1)
                val minMajor = PApplet.parseInt(minPieces[0], -1)
                val minMinor = PApplet.parseInt(minPieces[1], -1)
                val minMicro = PApplet.parseInt(minPieces[2], -1)

                if (-1 < currentMajor && -1 < currentMinor && -1 < currentMicro && -1 < minMajor && -1 < minMinor && -1 < minMicro) {
                    return if (currentMajor < minMajor) {
                        false
                    } else if (currentMajor == minMajor) {
                        if (currentMinor < minMinor) {
                            return false
                        }
                        if (currentMinor == minMinor) {
                            if (currentMicro < minMicro) {
                                false
                            } else {
                                true
                            }
                        } else {
                            true
                        }
                    } else {
                        true
                    }
                }
            }
            return false
        }
    }

}