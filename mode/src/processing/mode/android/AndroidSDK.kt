/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-17 The Processing Foundation

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
import processing.app.exec.ProcessHelper
import processing.app.exec.ProcessResult
import processing.app.ui.Toolkit

import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.getDateStamp
import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.android.AndroidUtil.showMessage

import java.awt.Dialog
import java.awt.FileDialog
import java.awt.Frame

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

import javax.swing.JEditorPane
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.event.HyperlinkEvent

/**
 * Class holding all needed references (path, tools, etc) to the SDK used by
 * the mode.
 */
internal class AndroidSDK(val folder: File) {
    val toolsFolder: File
    private val platforms: File
    private val highestPlatform: File
    val androidJarPath: File
    val platformToolsFolder: File
    private val buildToolsFolder: File
    val aVDManagerTool: File
    private val sdkManager: File
    var emulatorTool: File? = null

    /**
     * If a debug certificate exists, check its expiration date. If it's expired,
     * remove it so that it doesn't cause problems during the build.
     */
    fun checkDebugCertificate() {
        val dotAndroidFolder = File(System.getProperty("user.home"), ".android")
        val keystoreFile = File(dotAndroidFolder, "debug.keystore")

        if (keystoreFile.exists()) {
            // keytool -list -v -storepass android -keystore debug.keystore
            val ph = ProcessHelper(*arrayOf(
                    "keytool", "-list", "-v",
                    "-storepass", "android",
                    "-keystore", keystoreFile.absolutePath
            ))

            try {
                val result = ph.execute()
                if (result.succeeded()) {
                    // Valid from: Mon Nov 02 15:38:52 EST 2009 until: Tue Nov 02 16:38:52 EDT 2010
                    val lines = PApplet.split(result.stdout, '\n')

                    for (line in lines) {
                        val m = PApplet.match(line, "Valid from: .* until: (.*)")
                        if (m != null) {
                            val timestamp = m[1].trim { it <= ' ' }
                            // "Sun Jan 22 11:09:08 EST 2012"
                            // Hilariously, this is the format of Date.toString(), however
                            // it isn't the default for SimpleDateFormat or others. Yay!
                            val df: DateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")

                            try {
                                val date = df.parse(timestamp)
                                val expireMillis = date.time

                                if (expireMillis < System.currentTimeMillis()) {
                                    println(getTextString("android_debugger.info.removing_expired_keystore"))

                                    val hidingName = "debug.keystore." + getDateStamp(expireMillis)
                                    val hidingFile = File(keystoreFile.parent, hidingName)

                                    if (!keystoreFile.renameTo(hidingFile)) {
                                        System.err.println(getTextString("android_debugger.error.cannot_remove_expired_keystore"))
                                        System.err.println(getTextString("android_debugger.error.request_removing_keystore", keystoreFile.absolutePath))
                                    }
                                }
                            } catch (pe: ParseException) {
                                System.err.println(getTextString("android_debugger.error.invalid_keystore_timestamp", timestamp))
                                System.err.println(getTextString("android_debugger.error.request_bug_report"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val zipAlignTool: File?
        get() {
            val files = buildToolsFolder.listFiles()
            val name = if (Platform.isWindows()) "zipalign.exe" else "zipalign"
            for (f in files) {
                val z = File(f, name)
                if (z.exists()) return z
            }
            return null
        }

    private fun acceptLicenses() {
        val pb = ProcessBuilder(sdkManager.absolutePath, "--licenses")
        pb.redirectErrorStream(true)

        try {
            val process = pb.start()
            val os = process.outputStream
            val `is` = process.inputStream

            // Read the process output, otherwise read() will block and wait for new 
            // data to read
            Thread(Runnable {
                val b = ByteArray(1024)
                try {
                    while (`is`.read(b) != -1) {
                    }
                    `is`.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }, "AndroidSDK: reading licenses").start()

            Thread.sleep(1000)
            os.write(response.toByteArray())
            os.flush()
            os.close()

        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @Throws(InterruptedException::class, IOException::class)
    fun runADB(vararg cmd: String): ProcessResult {
        if (adbDisabled) {
            throw IOException("adb is currently disabled")
        }

        val adbCmd: Array<String>

        if (!cmd[0].contains("adb")) {
            val abdPath = if (Platform.isWindows()) File(platformToolsFolder, "adb.exe") else File(platformToolsFolder, "adb")
            adbCmd = PApplet.splice(cmd, abdPath.canonicalPath, 0)
        } else {
            adbCmd = cmd as Array<String>
        }

        // printing this here to see if anyone else is killing the adb server
        if (Base.DEBUG) {
            PApplet.printArray(adbCmd)
        }

        return try {
            val adbResult = ProcessHelper(*adbCmd).execute()
            // Ignore messages about starting up an adb daemon
            val out = adbResult.stdout
            if (out.contains(ADB_DAEMON_MSG_1) && out.contains(ADB_DAEMON_MSG_2)) {
                val sb = StringBuilder()

                for (line in out.split("\n").toTypedArray()) {
                    if (!out.contains(ADB_DAEMON_MSG_1) &&
                            !out.contains(ADB_DAEMON_MSG_2)) {
                        sb.append(line).append("\n")
                    }
                }

                return ProcessResult(adbResult.cmd,
                        adbResult.result,
                        sb.toString(),
                        adbResult.stderr,
                        adbResult.time)
            }

            adbResult

        } catch (ioe: IOException) {
            if (-1 < ioe.message!!.indexOf("Permission denied")) {
                Messages.showWarning(getTextString("android_sdk.warn.cannot_run_adb_title"),
                        getTextString("android_sdk.warn.cannot_run_adb_body"))
                adbDisabled = true
            }
            throw ioe
        }
    }

    class SDKTarget {
        var version = 0
        var name: String? = null
    }

    @get:Throws(IOException::class)
    val availableSdkTargets: ArrayList<SDKTarget>
        get() {
            val targets = ArrayList<SDKTarget>()

            for (platform in platforms.listFiles()) {
                val propFile = File(platform, "build.prop")
                if (!propFile.exists()) continue

                val target = SDKTarget()
                val br = BufferedReader(FileReader(propFile))
                var line: String

                while (br.readLine().also { line = it } != null) {
                    val lineData = line.split("=").toTypedArray()
                    if (lineData[0] == "ro.build.version.sdk") {
                        target.version = Integer.valueOf(lineData[1])
                    }
                    if (lineData[0] == "ro.build.version.release") {
                        target.name = lineData[1]
                        break
                    }
                }
                br.close()
                if (target.version != 0 && target.name != null) targets.add(target)
            }

            return targets

        }

    class BadSDKException(message: String?) : Exception(message) {

    }

    class CancelException(message: String?) : Exception(message) {

    }

    companion object {
        @JvmField
        var adbDisabled = false

        private val FONT_SIZE = Toolkit.zoom(11)
        private val TEXT_MARGIN = Toolkit.zoom(8)
        private val TEXT_WIDTH = Toolkit.zoom(300)

        private const val SDK_DOWNLOAD_URL = "https://developer.android.com/studio/index.html#downloads"
        private const val PROCESSING_FOR_ANDROID_URL = "http://android.processing.org/"
        private const val WHATS_NEW_URL = "http://android.processing.org/whatsnew.html"
        private const val DRIVER_INSTALL_URL = "https://developer.android.com/studio/run/oem-usb.html#InstallingDriver"
        private const val SYSTEM_32BIT_URL = "https://askubuntu.com/questions/710426/android-sdk-on-ubuntu-32bit"
        private const val SDK_LICENSE_URL = "https://developer.android.com/studio/terms.html"

        private const val NO_ERROR = 0
        private const val SKIP_ENV_SDK = 1
        private const val MISSING_SDK = 2
        private const val INVALID_SDK = 3
        private var loadError = NO_ERROR

        // Write to the process input, so the licenses will be accepted. In 
        // principle, We only need 7 'y', one for the 'yes' to the first 
        // 'review licenses?' question, the rest for the 6 licenses, but adding
        // 10 just in case, having more does not cause any trouble.  
        private const val response = "y\ny\ny\ny\ny\ny\ny\ny\ny\ny\n"

        @JvmStatic
        val hAXMInstallerFolder: File
            get() {
                val sdkPrefsPath = Preferences.get("android.sdk.path")
                val sdkPath = File(sdkPrefsPath)
                return File(sdkPath, "extras/intel/HAXM")
            }

        val googleDriverFolder: File
            get() {
                val sdkPrefsPath = Preferences.get("android.sdk.path")
                val sdkPath = File(sdkPrefsPath)
                return File(sdkPath, "extras/google/usb_driver")
            }

        /**
         * Checks a path to see if there's a tools/android file inside, a rough check
         * for the SDK installation. Also figures out the name of android/android.bat/android.exe
         * so that it can be called explicitly.
         */
        @Throws(BadSDKException::class)
        private fun findCliTool(tools: File, name: String): File {
            if (File(tools, "$name.bat").exists()) {
                return File(tools, "$name.bat")
            }
            if (File(tools, "$name.exe").exists()) {
                return File(tools, "$name.exe")
            }
            if (File(tools, name).exists()) {
                return File(tools, name)
            }
            throw BadSDKException("Cannot find $name in $tools")
        }

        /**
         * Check for a set android.sdk.path preference. If the pref
         * is set, and refers to a legitimate Android SDK, then use that.
         *
         * Check for the ANDROID_SDK environment variable. If the variable is set,
         * and refers to a legitimate Android SDK, then use that and save the pref.
         *
         * Prompt the user to select an Android SDK. If the user selects a
         * legitimate Android SDK, then use that, and save the preference.
         *
         * @return an AndroidSDK
         * @throws BadSDKException
         * @throws IOException
         */
        @JvmStatic
        @Throws(IOException::class)
        fun load(checkEnvSDK: Boolean, editor: Frame?): AndroidSDK? {
            loadError = NO_ERROR

            // Give priority to preferences:
            // https://github.com/processing/processing-android/issues/372
            val sdkPrefsPath = Preferences.get("android.sdk.path")

            if (sdkPrefsPath != null && sdkPrefsPath != "") {
                loadError = try {
                    val androidSDK = AndroidSDK(File(sdkPrefsPath))
                    Preferences.set("android.sdk.path", sdkPrefsPath)
                    return androidSDK
                } catch (badPref: BadSDKException) {
                    Preferences.unset("android.sdk.path")
                    INVALID_SDK
                }
            }

            val sdkEnvPath = Platform.getenv("ANDROID_SDK")

            if (sdkEnvPath != null && sdkEnvPath != "") {
                try {
                    val androidSDK = AndroidSDK(File(sdkEnvPath))

                    if (checkEnvSDK && editor != null) {
                        // There is a valid SDK in the environment, but let's give the user
                        // the option to not to use it. After this, we should go straight to 
                        // download a new SDK.
                        val result = showEnvSDKDialog(editor)

                        if (result != JOptionPane.YES_OPTION) {
                            loadError = SKIP_ENV_SDK
                            return null
                        }
                    }

                    // Set this value in preferences.txt, in case ANDROID_SDK
                    // gets knocked out later. For instance, by that pesky Eclipse,
                    // which nukes all env variables when launching from the IDE.
                    Preferences.set("android.sdk.path", sdkEnvPath)

                    // If we are here, it means that there was no SDK path in the preferences
                    // and the user wants to use the SDK found in the environment. This
                    // means we just installed the mode for the first time, so we show a 
                    // welcome message with some useful info.
                    showMessage(getTextString("android_sdk.dialog.using_existing_sdk_title"),
                            getTextString("android_sdk.dialog.using_existing_sdk_body",
                                    PROCESSING_FOR_ANDROID_URL, WHATS_NEW_URL))

                    return androidSDK

                } catch (badEnv: BadSDKException) {
                    Preferences.unset("android.sdk.path")
                    loadError = INVALID_SDK
                }
            } else if (loadError == NO_ERROR) {
                loadError = MISSING_SDK
            }

            return null

        }

        @Throws(BadSDKException::class, CancelException::class, IOException::class)
        fun locate(window: Frame?, androidMode: AndroidMode?): AndroidSDK {
            if (loadError == SKIP_ENV_SDK) {
                // The user does not want to use the environment SDK, so let's simply
                // download a new one to the sketchbook folder.
                return download(window, androidMode)
            }

            // At this point, there is no ANDROID_SDK env variable, no SDK in the preferences,
            // or either one was invalid, so we will continue by asking the user to either locate
            // a valid SDK manually, or download a new one.
            val result = showLocateDialog(window)

            return if (result == JOptionPane.YES_OPTION) {
                download(window, androidMode)
            } else if (result == JOptionPane.NO_OPTION) {
                // User will manually select folder containing SDK folder
                val folder = selectFolder(getTextString("android_sdk.dialog.select_sdk_folder"), null, window)

                if (folder == null) {
                    throw CancelException(getTextString("android_sdk.error.cancel_sdk_selection"))
                } else {
                    val androidSDK = AndroidSDK(folder)
                    Preferences.set("android.sdk.path", folder.absolutePath)
                    androidSDK
                }
            } else {
                throw CancelException(getTextString("android_sdk.error.sdk_selection_canceled"))
            }
        }

        @JvmStatic
        @Throws(BadSDKException::class, CancelException::class, IOException::class)
        fun locateSysImage(window: Frame?,
                           androidMode: AndroidMode?, wear: Boolean, ask: Boolean): Boolean {
            val result = showDownloadSysImageDialog(window, wear)

            return if (result == JOptionPane.YES_OPTION) {
                downloadSysImage(window, androidMode, wear, ask)
            } else if (result == JOptionPane.NO_OPTION) {
                false
            } else {
                false
            }
        }

        @Throws(BadSDKException::class, CancelException::class)
        fun download(editor: Frame?, androidMode: AndroidMode?): AndroidSDK {
            val downloader = SDKDownloader(editor!!)

            downloader.run() // This call blocks until the SDK download complete, or user cancels.

            if (downloader.cancelled()) {
                throw CancelException(getTextString("android_sdk.error.sdk_download_canceled"))
            }

            val sdk = downloader.sDK ?: throw BadSDKException(getTextString("android_sdk.error.sdk_download_failed"))
            val result = showSDKLicenseDialog(editor)

            if (result == JOptionPane.YES_OPTION) {
                sdk.acceptLicenses()
                var msg = getTextString("android_sdk.dialog.sdk_installed_body", PROCESSING_FOR_ANDROID_URL, WHATS_NEW_URL)
                val driver = googleDriverFolder

                if (Platform.isWindows() && driver.exists()) {
                    msg += getTextString("android_sdk.dialog.install_usb_driver", DRIVER_INSTALL_URL, driver.absolutePath)
                }

                showMessage(getTextString("android_sdk.dialog.sdk_installed_title"), msg)

            } else {
                showMessage(getTextString("android_sdk.dialog.sdk_license_rejected_title"),
                        getTextString("android_sdk.dialog.sdk_license_rejected_body")!!)
            }
            if (Platform.isLinux() && Platform.getNativeBits() == 32) {
                showMessage(getTextString("android_sdk.dialog.32bit_system_title"),
                        getTextString("android_sdk.dialog.32bit_system_body", SYSTEM_32BIT_URL))
            }
            return sdk
        }

        @Throws(BadSDKException::class, CancelException::class)
        fun downloadSysImage(editor: Frame?,
                             androidMode: AndroidMode?, wear: Boolean, ask: Boolean): Boolean {
            val downloader = SysImageDownloader(editor, wear, ask)
            downloader.run() // This call blocks until the SDK download complete, or user cancels.

            if (downloader.cancelled()) {
                throw CancelException(getTextString("android_sdk.error.emulator_download_canceled"))
            }

            val res = downloader.result

            if (!res) {
                throw BadSDKException(getTextString("android_sdk.error.emulator_download_failed"))
            }
            return res
        }

        fun showEnvSDKDialog(editor: Frame?): Int {
            val title = getTextString("android_sdk.dialog.found_installed_sdk_title")
            val htmlString = "<html> " +
                    "<head> <style type=\"text/css\">" +
                    "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
                    "margin: " + TEXT_MARGIN + "px; " +
                    "width: " + TEXT_WIDTH + "px }" +
                    "</style> </head>" +
                    "<body> <p>" + getTextString("android_sdk.dialog.found_installed_sdk_body") + "</p> </body> </html>"

            val pane = JEditorPane("text/html", htmlString)

            pane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    Platform.openURL(e.url.toString())
                }
            }

            pane.isEditable = false
            val label = JLabel()
            pane.background = label.background

            val options = arrayOf(getTextString("android_sdk.option.use_existing_sdk"),
                    getTextString("android_sdk.option.download_new_sdk"))
            val result = JOptionPane.showOptionDialog(null, pane, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0])

            return if (result == JOptionPane.YES_OPTION) {
                JOptionPane.YES_OPTION
            } else if (result == JOptionPane.NO_OPTION) {
                JOptionPane.NO_OPTION
            } else {
                JOptionPane.CLOSED_OPTION
            }
        }

        fun showLocateDialog(editor: Frame?): Int {
            // How to show a option dialog containing clickable links:
            // http://stackoverflow.com/questions/8348063/clickable-links-in-joptionpane
            var htmlString = "<html> " +
                    "<head> <style type=\"text/css\">" +
                    "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
                    "margin: " + TEXT_MARGIN + "px; " +
                    "width: " + TEXT_WIDTH + "px }" +
                    "</style> </head>"

            var title: String? = ""

            if (loadError == MISSING_SDK) {
                htmlString += "<body> <p>" + getTextString("android_sdk.dialog.cannot_find_sdk_body", SDK_DOWNLOAD_URL, AndroidBuild.TARGET_SDK) + "</p> </body> </html>"
                title = getTextString("android_sdk.dialog.cannot_find_sdk_title")
            } else if (loadError == INVALID_SDK) {
                htmlString += "<body> <p>" + getTextString("android_sdk.dialog.invalid_sdk_body", AndroidBuild.TARGET_SDK, SDK_DOWNLOAD_URL, AndroidBuild.TARGET_SDK) + "</p> </body> </html>"
                title = getTextString("android_sdk.dialog.invalid_sdk_title")
            }

            val pane = JEditorPane("text/html", htmlString)

            pane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    Platform.openURL(e.url.toString())
                }
            }

            pane.isEditable = false
            val label = JLabel()
            pane.background = label.background

            val options = arrayOf(getTextString("android_sdk.option.download_sdk"),
                    getTextString("android_sdk.option.locate_sdk"))

            val result = JOptionPane.showOptionDialog(null, pane, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0])

            return if (result == JOptionPane.YES_OPTION) {
                JOptionPane.YES_OPTION
            } else if (result == JOptionPane.NO_OPTION) {
                JOptionPane.NO_OPTION
            } else {
                JOptionPane.CLOSED_OPTION
            }
        }

        fun showDownloadSysImageDialog(editor: Frame?, wear: Boolean): Int {

            val title = if (wear) {
                getTextString("android_sdk.dialog.download_watch_image_title")
            } else {
                getTextString("android_sdk.dialog.download_phone_image_title")
            }

            val msg = if (wear) {
                getTextString("android_sdk.dialog.download_watch_image_body")
            } else {
                getTextString("android_sdk.dialog.download_phone_image_body")
            }

            val htmlString = "<html> " +
                    "<head> <style type=\"text/css\">" +
                    "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
                    "margin: " + TEXT_MARGIN + "px; " +
                    "width: " + TEXT_WIDTH + "px }" +
                    "</style> </head>" + "<body> <p>" + msg + "</p> </body> </html>"

            val pane = JEditorPane("text/html", htmlString)

            pane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    Platform.openURL(e.url.toString())
                }
            }

            pane.isEditable = false
            val label = JLabel()
            pane.background = label.background

            val options = arrayOf(Language.text("prompt.yes"), Language.text("prompt.no"))
            val result = JOptionPane.showOptionDialog(null, pane, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0])

            return if (result == JOptionPane.YES_OPTION) {
                JOptionPane.YES_OPTION
            } else if (result == JOptionPane.NO_OPTION) {
                JOptionPane.NO_OPTION
            } else {
                JOptionPane.CLOSED_OPTION
            }
        }

        fun showSDKLicenseDialog(editor: Frame?): Int {
            val title = getTextString("android_sdk.dialog.accept_sdk_license_title")
            val msg = getTextString("android_sdk.dialog.accept_sdk_license_body", SDK_LICENSE_URL)

            val htmlString = "<html> " +
                    "<head> <style type=\"text/css\">" +
                    "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
                    "margin: " + TEXT_MARGIN + "px; " +
                    "width: " + TEXT_WIDTH + "px }" +
                    "</style> </head>" + "<body> <p>" + msg + "</p> </body> </html>"

            val pane = JEditorPane("text/html", htmlString)

            pane.addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    Platform.openURL(e.url.toString())
                }
            }

            pane.isEditable = false
            val label = JLabel()
            pane.background = label.background

            val options = arrayOf(Language.text("prompt.yes"), Language.text("prompt.no"))
            val result = JOptionPane.showOptionDialog(null, pane, title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0])

            return if (result == JOptionPane.YES_OPTION) {
                JOptionPane.YES_OPTION
            } else if (result == JOptionPane.NO_OPTION) {
                JOptionPane.NO_OPTION
            } else {
                JOptionPane.CLOSED_OPTION
            }
        }

        // this was banished from Base because it encourages bad practice.
        // TODO figure out a better way to handle the above.
        fun selectFolder(prompt: String?, folder: File?, frame: Frame?): File? {
            var frame = frame

            if (Platform.isMacOS()) {
                if (frame == null) frame = Frame() //.pack();
                val fd = FileDialog(frame, prompt, FileDialog.LOAD)
                if (folder != null) {
                    fd.directory = folder.parent
                    //fd.setFile(folder.getName());
                }

                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                fd.modalityType = Dialog.ModalityType.APPLICATION_MODAL
                fd.isVisible = true

                System.setProperty("apple.awt.fileDialogForDirectories", "false")
                return if (fd.file == null) {
                    null
                } else File(fd.directory, fd.file)
            } else {
                val fc = JFileChooser()
                fc.dialogTitle = prompt
                if (folder != null) {
                    fc.selectedFile = folder
                }
                fc.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                val returned = fc.showOpenDialog(frame)
                if (returned == JFileChooser.APPROVE_OPTION) {
                    return fc.selectedFile
                }
            }
            return null
        }

        private const val ADB_DAEMON_MSG_1 = "daemon not running"
        private const val ADB_DAEMON_MSG_2 = "daemon started successfully"
    }

    // constructor or initializer block
    // this init{...} will be executed first irrespective of it's position in the code
    init {
        if (!folder.exists()) {
            throw BadSDKException(getTextString("android_sdk.error.missing_sdk_folder", folder))
        }

        toolsFolder = File(folder, "tools")

        if (!toolsFolder.exists()) {
            throw BadSDKException(getTextString("android_sdk.error.missing_tools_folder", folder))
        }

        platformToolsFolder = File(folder, "platform-tools")

        if (!platformToolsFolder.exists()) {
            throw BadSDKException(getTextString("android_sdk.error.missing_platform_tools_folder", folder))
        }

        buildToolsFolder = File(folder, "build-tools")

        if (!buildToolsFolder.exists()) {
            throw BadSDKException(getTextString("android_sdk.error.missing_build_tools_folder", folder))
        }

        platforms = File(folder, "platforms")

        if (!platforms.exists()) {
            throw BadSDKException(getTextString("android_sdk.error.missing_platforms_folder", folder))
        }

        // Retrieve the highest platform from the available targets
        val targets = availableSdkTargets
        var highest = 1

        for (targ in targets) {
            if (highest < targ.version) {
                highest = targ.version
            }
        }

        if (highest < PApplet.parseInt(AndroidBuild.TARGET_SDK)) {
            throw BadSDKException(getTextString("android_sdk.error.missing_target_platform",
                    AndroidBuild.TARGET_SDK, platforms.absolutePath))
        }

        highestPlatform = File(platforms, "android-$highest")
        androidJarPath = File(highestPlatform, "android.jar")

        if (!androidJarPath.exists()) {
            throw BadSDKException(getTextString("android_sdk.error.missing_android_jar",
                    AndroidBuild.TARGET_SDK, highestPlatform.absolutePath))
        }

        aVDManagerTool = findCliTool(File(toolsFolder, "bin"), "avdmanager")
        sdkManager = findCliTool(File(toolsFolder, "bin"), "sdkmanager")

        val emuFolder = File(folder, "emulator")
        if (emuFolder.exists()) {
            // First try the new location of the emulator inside its own folder
            emulatorTool = findCliTool(emuFolder, "emulator")
        } else {
            // If not found, use old location inside tools
            emulatorTool = findCliTool(toolsFolder, "emulator")
        }

        var path = Platform.getenv("PATH")
        Platform.setenv("ANDROID_SDK", folder.canonicalPath)
        path = platformToolsFolder.canonicalPath + File.pathSeparator +
                toolsFolder.canonicalPath + File.pathSeparator + path

        val javaHomeProp = System.getProperty("java.home")
        val javaHome = File(javaHomeProp).canonicalFile

        Platform.setenv("JAVA_HOME", javaHome.canonicalPath)
        path = File(javaHome, "bin").canonicalPath + File.pathSeparator + path

        Platform.setenv("PATH", path)

        checkDebugCertificate()
    }
}