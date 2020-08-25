/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2011-12 Ben Fry and Casey Reas

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

import processing.app.*
import processing.app.ui.Editor
import processing.app.ui.EditorException
import processing.app.ui.EditorState

import processing.core.PApplet

import processing.mode.java.JavaMode

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Aditya Rana
 * Programming mode to create and run Processing sketches on Android devices.
 */
internal class AndroidMode(base: Base?, folder: File?) : JavaMode(base, folder) {

    private var sdk: AndroidSDK? = null

    fun getSdk() = this.sdk

    // constructor or initialization block
    init {
        AndroidBuild.initVersions(getContentFile(VERSIONS_FILE))
        loadTextStrings()
    }

    /*
      // for debugging only, check to see if this is an svn checkout
      File debugFile = new File("../../../android/core.zip");
      if (!debugFile.exists() && Base.isMacOS()) {
        // current path might be inside Processing.app, so need to go much higher
        debugFile = new File("../../../../../../../android/core.zip");
      }
      if (debugFile.exists()) {
        System.out.println("Using version of core.zip from local SVN checkout.");
//        return debugFile;
        coreZipLocation = debugFile;
      }
      */


    // otherwise do the usual
    //    return new File(base.getSketchbookFolder(), ANDROID_CORE_FILENAME);
    var coreZipLocation: File? = null
        get() {
            if (field == null) {

                /*
          // for debugging only, check to see if this is an svn checkout
          File debugFile = new File("../../../android/core.zip");
          if (!debugFile.exists() && Base.isMacOS()) {
            // current path might be inside Processing.app, so need to go much higher
            debugFile = new File("../../../../../../../android/core.zip");
          }
          if (debugFile.exists()) {
            System.out.println("Using version of core.zip from local SVN checkout.");
    //        return debugFile;
            coreZipLocation = debugFile;
          }
          */

                // otherwise do the usual
                //    return new File(base.getSketchbookFolder(), ANDROID_CORE_FILENAME);
                field = getContentFile("processing-core.zip")
            }
            return field
        }
        private set

    private var runner: AndroidRunner? = null

    private var showWatchFaceDebugMessage = true
    private var showWatchFaceSelectMessage = true
    private var showWallpaperSelectMessage = true
    private var checkingSDK = false
    private var userCancelledSDKSearch = false

    @Throws(EditorException::class)
    override fun createEditor(base: Base, path: String,
                              state: EditorState): Editor {
        return AndroidEditor(base, path, state, this)
    }

    override fun getTitle(): String {
        return "Android"
    }

    override fun getKeywordFiles(): Array<File> {
        return arrayOf(
                Platform.getContentFile("modes/java/keywords.txt"),
                getContentFile("keywords.txt")
        )
    }

    override fun getExampleCategoryFolders(): Array<File> {
        return arrayOf(
                File(examplesFolder, "Basics"),
                File(examplesFolder, "Topics"),
                File(examplesFolder, "Demos"),
                File(examplesFolder, "Sensors")
        )
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /** @return null so that it doesn't try to pass along the desktop version of core.jar
     */
    override fun getCoreLibrary(): Library? {
        return null
    }

    fun resetUserSelection() {
        userCancelledSDKSearch = false
    }

    fun checkSDK(editor: Editor?) {
        if (checkingSDK) {

            // Some other thread has invoked SDK checking, so wait until the first one
            // is done (it might involve downloading the SDK, etc).
            while (checkingSDK) {
                try {
                    Thread.sleep(10)
                } catch (e: InterruptedException) {
                    return
                }
            }
        }
        if (userCancelledSDKSearch) return

        checkingSDK = true
        var tr: Throwable? = null

        if (sdk == null) {
            try {
                sdk = AndroidSDK.load(true, editor)
                if (sdk == null) {
                    sdk = AndroidSDK.locate(editor, this)
                }
            } catch (cancel: AndroidSDK.CancelException) {
                userCancelledSDKSearch = true
                tr = cancel
            } catch (other: Exception) {
                tr = other
            }
        }

        if (sdk == null) {
            Messages.showWarning(getTextString("android_mode.warn.cannot_load_sdk_title"),
                    getTextString("android_mode.warn.cannot_load_sdk_body"), tr)

        } else {
            val devices = Devices.instance
            devices.sDK = sdk
        }
        checkingSDK = false
    }

    override fun getSearchPath(): String {
        if (sdk == null) {
            checkSDK(null)
        }
        if (sdk == null) {
            Messages.log(getTextString("android_mode.info.cannot_open_sdk_path"))
            return ""
        }
        val coreJarPath = File(getFolder(), "processing-core.zip").absolutePath

        return sdk!!.androidJarPath.absolutePath + File.pathSeparatorChar + coreJarPath
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    @Throws(SketchException::class, IOException::class)
    fun handleRunEmulator(sketch: Sketch?, editor: AndroidEditor,
                          listener: RunnerListener) {
        listener.startIndeterminate()
        listener.statusNotice(getTextString("android_mode.status.starting_project_build"))

        val build = AndroidBuild(sketch, this, editor.appComponent)
        listener.statusNotice(getTextString("android_mode.status.building_project"))

        build.build("debug")

        val avd = AVD.ensureProperAVD(editor, this, sdk, build.isWear)

        if (!avd) {
            val se = SketchException(getTextString("android_mode.error.cannot_create_avd"))
            se.hideStackTrace()
            throw se
        }
        val comp = build.appComponent
        val emu = Devices.instance.getEmulator(build.isWear)

        runner = AndroidRunner(build, listener)
        runner!!.launch(emu, comp, true)
    }

    @Throws(SketchException::class, IOException::class)
    fun handleRunDevice(sketch: Sketch?, editor: AndroidEditor,
                        listener: RunnerListener) {
        val devices = Devices.instance
        val deviceList = devices.findMultiple(false)

        if (deviceList.size == 0) {
            Messages.showWarning(getTextString("android_mode.dialog.no_devices_found_title"),
                    getTextString("android_mode.dialog.no_devices_found_body"))

            listener.statusError(getTextString("android_mode.status.no_devices_found"))

            return
        }

        listener.startIndeterminate()

        listener.statusNotice(getTextString("android_mode.status.starting_project_build"))

        val build = AndroidBuild(sketch, this, editor.appComponent)

        listener.statusNotice(getTextString("android_mode.status.building_project"))

        val projectFolder = build.build("debug")

        if (projectFolder == null) {
            listener.statusError(getTextString("android_mode.status.project_build_failed"))
            return
        }

        val comp = build.appComponent
        val dev = Devices.instance.hardware

        runner = AndroidRunner(build, listener)

        if (runner!!.launch(dev, comp, false)) {
            showPostBuildMessage(comp)
        }
    }

    fun showSelectComponentMessage(appComp: Int) {
        if (showWatchFaceDebugMessage && appComp == AndroidBuild.WATCHFACE) {
            AndroidUtil.showMessage(getTextString("android_mode.dialog.watchface_debug_title"),
                    getTextString("android_mode.dialog.watchface_debug_body", BLUETOOTH_DEBUG_URL))
            showWatchFaceDebugMessage = false
        }
    }

    fun showPostBuildMessage(appComp: Int) {
        if (showWallpaperSelectMessage && appComp == AndroidBuild.WALLPAPER) {
            AndroidUtil.showMessage(getTextString("android_mode.dialog.wallpaper_installed_title"),
                    getTextString("android_mode.dialog.wallpaper_installed_body").toString())

            showWallpaperSelectMessage = false
        }

        if (showWatchFaceSelectMessage && appComp == AndroidBuild.WATCHFACE) {
            AndroidUtil.showMessage(getTextString("android_mode.dialog.watchface_installed_title"),
                    getTextString("android_mode.dialog.watchface_installed_body").toString())

            showWatchFaceSelectMessage = false
        }

    }

    fun handleStop(listener: RunnerListener) {
        listener.statusNotice("")
        listener.stopIndeterminate()

//    if (runtime != null) {
//      runtime.close();  // kills the window
//      runtime = null; // will this help?
//    }
        if (runner != null) {
            runner!!.close()
            runner = null
        }
    }

    fun checkPackageName(sketch: Sketch, comp: Int): Boolean {
        val manifest = Manifest(sketch, comp, getFolder(), false)
        val defName = Manifest.BASE_PACKAGE + "." + sketch.name.toLowerCase()
        val name = manifest.packageName

        if (name!!.toLowerCase() == defName.toLowerCase()) {
            // The user did not set the package name, show error and stop
            AndroidUtil.showMessage(getTextString("android_mode.dialog.cannot_export_package_title"),
                    getTextString("android_mode.dialog.cannot_export_package_body", DISTRIBUTING_APPS_TUT_URL))
            return false
        }

        return true
    }

    fun checkAppIcons(sketch: Sketch, comp: Int): Boolean {
        val sketchFolder = sketch.folder
        val launcherIcons = AndroidUtil.getFileList(sketchFolder, AndroidBuild.SKETCH_LAUNCHER_ICONS,
                AndroidBuild.SKETCH_OLD_LAUNCHER_ICONS)

        var allFilesExist = AndroidUtil.allFilesExists(launcherIcons)

        if (comp == AndroidBuild.WATCHFACE) {
            // Additional preview icons are needed for watch faces
            val watchFaceIcons = AndroidUtil.getFileList(sketchFolder, AndroidBuild.SKETCH_WATCHFACE_ICONS)
            allFilesExist = allFilesExist and AndroidUtil.allFilesExists(watchFaceIcons)
        }

        if (!allFilesExist) {
            // The user did not set custom icons, show error and stop
            AndroidUtil.showMessage(getTextString("android_mode.dialog.cannot_use_default_icons_title"),
                    getTextString("android_mode.dialog.cannot_use_default_icons_body", DISTRIBUTING_APPS_TUT_URL))
            return false
        }

        return true
    }

    fun initManifest(sketch: Sketch?, comp: Int) {
        Manifest(sketch, comp, getFolder(), false)
    }

    fun resetManifest(sketch: Sketch?, comp: Int) {
        Manifest(sketch, comp, getFolder(), true)
    }

    private fun loadTextStrings() {
        val baseFilename = "languages/mode.properties"
        val modeBaseFile = File(getFolder(), baseFilename)

        if (textStrings == null) {
            textStrings = HashMap()
            val lines = PApplet.loadStrings(modeBaseFile)
                    ?: throw NullPointerException("""
    File not found:
    ${modeBaseFile.absolutePath}
    """.trimIndent())
            //for (String line : lines) {
            for (i in lines.indices) {
                val line = lines[i]

                if (line.isEmpty() ||
                        line[0] == '#') continue

                // this won't properly handle = signs inside in the text
                val equals = line.indexOf('=')

                if (equals != -1) {
                    val key = line.substring(0, equals).trim { it <= ' ' }
                    var value = line.substring(equals + 1).trim { it <= ' ' }
                    value = value.replace("\\\\n".toRegex(), "\n")
                    value = value.replace("\\\\'".toRegex(), "'")
                    (textStrings as HashMap<String, String>)[key] = value
                }
            }
        }
    }

    companion object {
        // Using this temporarily until support for mode translations is finalized in the Processing app
        private var textStrings: MutableMap<String, String>? = null
        private const val VERSIONS_FILE = "version.properties"
        private const val BLUETOOTH_DEBUG_URL = "https://developer.android.com/training/wearables/apps/debugging.html"
        private const val DISTRIBUTING_APPS_TUT_URL = "http://android.processing.org/tutorials/distributing/index.html"

        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
        val dateFormat = SimpleDateFormat("yyMMdd.HHmm")

        @JvmStatic
        lateinit var dateStamp: String

        @JvmStatic
        fun getDateStamp(stamp: Long): String {
            return dateFormat.format(Date(stamp))
        }

        @JvmStatic
        fun getTextString(key: String): String? {
            return textStrings!![key]
            //    return Language.text(key);
        }

        @JvmStatic
        fun getTextString(key: String, vararg arguments: Any?): String {
            val value = textStrings!![key] ?: return key
            return String.format(value, *arguments)
        }
    }

}