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
package processing.mode.android

import processing.app.Base
import processing.app.Platform
import processing.app.Preferences
import processing.app.exec.StreamPump

import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.android.AndroidSDK.Companion.locateSysImage
import processing.mode.android.AndroidUtil.showMessage

import java.awt.Frame
import java.io.*
import java.util.*

/**
 * @author Aditya Rana
 * @constructor
 * @param name Name of this AVD
 * @param skin skin of the virtual device
 * @param type Type of the device
 * @param device represents the device itself
 */
internal class AVD(var name: String, var device: String, var skin: String, var type: Int) {

    @Throws(IOException::class)
     fun exists(sdk: AndroidSDK): Boolean {
        if (avdList == null) {
            list(sdk)
        }
        for (avd in avdList!!) {
            if (Base.DEBUG) {
                println("AVD.exists() checking for $name against $avd")
            }
            if (avd == name) {
                return true
            }
        }
        return false
    }

    /**
     * Return true if a member of the renowned and prestigious
     * "The following Android Virtual Devices could not be loaded:" club.
     * (Prestigious may also not be the right word.)
     */
     fun badness(): Boolean {
        for (avd in badList!!) {
            if (avd == name) {
                return true
            }
        }
        return false
    }

    @Throws(IOException::class)
     fun hasImages(sdk: AndroidSDK): Boolean {
        val abi = preferredABI

        return if (type == PHONE) {
            if (phoneImages == null) {
                phoneImages = ArrayList()
                getImages(phoneImages, sdk, abi)
            }
            !phoneImages!!.isEmpty()
        } else {
            if (wearImages == null) {
                wearImages = ArrayList()
                getImages(wearImages, sdk, abi)
            }
            !wearImages!!.isEmpty()
        }
    }

    @Throws(IOException::class)
     fun refreshImages(sdk: AndroidSDK) {
        val abi = preferredABI

        if (type == PHONE) {
            phoneImages = ArrayList()
            getImages(phoneImages, sdk, abi)
        } else {
            wearImages = ArrayList()
            getImages(wearImages, sdk, abi)
        }
    }

    @Throws(IOException::class)
     fun getImages(images: ArrayList<String>?, sdk: AndroidSDK,
                            imageAbi: String?) {
        val wear = type == WEAR
        val imagePlatform = getPreferredPlatform(wear, imageAbi)

        val imageTag = getPreferredTag(wear, imageAbi)
        val avdManager = sdk.aVDManagerTool

        val cmd = arrayOf(
                avdManager.canonicalPath,
                "create", "avd",
                "-n", "dummy",
                "-k", "dummy"
        )

        // Dummy avdmanager creation command to get the list of installed images
        // TODO : Find a better way to get the list of installed images
        val pb = ProcessBuilder(*cmd)

        if (Base.DEBUG) {
            println(PApplet.join(cmd, " "))
        }
        val env = pb.environment()
        env.clear()

        env["JAVA_HOME"] = Platform.getJavaHome().canonicalPath
        pb.redirectErrorStream(true)

        try {
            process = pb.start()
            val output = StreamPump(process!!.inputStream, "out: ")
            output.addTarget { line ->
                //          System.out.println("dummy output ---> " + line);
                if (images != null &&
                        line.contains(";$imagePlatform") &&
                        line.contains(";$imageTag") &&
                        line.contains(";$imageAbi")) {
//            System.out.println("  added image!");
                    images.add(line)
                }
            }.start()
            process!!.waitFor()
        } catch (ie: InterruptedException) {
            ie.printStackTrace()
        } finally {
            process!!.destroy()
        }
    }

    // Could not find any suitable package
    @get:Throws(IOException::class)
     val sdkId: String
         get() {
            val abi = preferredABI
            if (type == PHONE) {
                for (image in phoneImages!!) {
                    if (image.contains(";$abi")) return image
                }
            } else {
                for (image in wearImages!!) {
                    if (image.contains(";$abi")) return image
                }
            }

            // Could not find any suitable package
            return "null"
        }

    @Throws(IOException::class)
     fun create(sdk: AndroidSDK): Boolean {
        val sketchbookFolder = Base.getSketchbookFolder()
        val androidFolder = File(sketchbookFolder, "android")

        if (!androidFolder.exists()) androidFolder.mkdir()

        val avdPath = File(androidFolder, "avd/$name")
        val avdManager = sdk.aVDManagerTool

        val cmd = arrayOf(
                avdManager.canonicalPath,
                "create", "avd",
                "-n", name,
                "-k", sdkId,
                "-c", DEFAULT_SDCARD_SIZE,
                "-d", device,
                "-p", avdPath.absolutePath,
                "-f"
        )

        val pb = ProcessBuilder(*cmd)

        if (Base.DEBUG) {
            println(PApplet.join(cmd, " "))
        }

        // avdmanager create avd -n "Wear-Processing-0254" -k "system-images;android-25;google_apis;x86" -c 64M

        // Set the list to null so that exists() will check again
        avdList = null
        val env = pb.environment()

        env.clear()
        env["JAVA_HOME"] = Platform.getJavaHome().canonicalPath
        pb.redirectErrorStream(true)

        try {
            process = pb.start()

            // Passes 'no' to "Do you wish to create a custom hardware profile [no]"
            val os = process!!.outputStream
            val pw = PrintWriter(OutputStreamWriter(os))
            pw.println("no")
            pw.flush()
            pw.close()
            os.flush()
            os.close()

            val outWriter = StringWriter()
            StreamPump(process!!.inputStream, "out: ").addTarget(outWriter).start()

            process!!.waitFor()

            if (process!!.exitValue() == 0) {
                // Add skin to AVD's config file
                val configFile = File(avdPath, "config.ini")
                if (configFile.exists()) {
                    try {
                        PrintWriter(FileWriter(configFile.absolutePath, true)).use { output -> output.printf("%s\r\n", "skin.name=$skin") }
                    } catch (e: Exception) {
                    }
                }
                return true
            }

            if (outWriter.toString().contains("Package path is not valid")) {
                // They didn't install the Google APIs
                showMessage(getTextString("android_avd.error.sdk_wrong_install_title"),
                        getTextString("android_avd.error.sdk_wrong_install_body", GETTING_START_TUT_URL))
            } else {
                // Just generally not working
                showMessage(getTextString("android_avd.error.cannot_create_avd_title"),
                        getTextString("android_avd.error.cannot_create_avd_body", AndroidBuild.TARGET_SDK))
            }

            System.err.println(outWriter.toString())
            //System.err.println(createAvdResult);

        } catch (ie: InterruptedException) {
            ie.printStackTrace()
        } finally {
            process!!.destroy()
        }
        return false
    }

    companion object {
        private const val PHONE = 0
        private const val WEAR = 1

        const val DEFAULT_ABI = "x86"
        const val TARGET_SDK_ARM = "24"
        const val DEFAULT_PHONE_PORT = "5566"
        const val DEFAULT_WEAR_PORT = "5576"

        private const val GETTING_START_TUT_URL = "http://android.processing.org/tutorials/getting_started/index.html"

        const val DEFAULT_SDCARD_SIZE = "64M"
        const val DEVICE_DEFINITION = "Nexus One"

        const val DEVICE_SKIN = "480x800"
        const val DEVICE_WEAR_DEFINITION = "wear_square_280_280dpi"
        const val DEVICE_WEAR_SKIN = "280x280"

        var avdList: ArrayList<String>? = null
        var badList: ArrayList<String>? = null

        /** "system-images;android-25;google_apis;x86"  */
        var wearImages: ArrayList<String>? = null
        var phoneImages: ArrayList<String>? = null
        var process: Process? = null

        /** Default virtual device used by Processing.  */
        val phoneAVD = AVD("processing-phone",
                DEVICE_DEFINITION, DEVICE_SKIN, PHONE)

        /** Default virtual wear device used by Processing.  */
        val watchAVD = AVD("processing-watch",
                DEVICE_WEAR_DEFINITION, DEVICE_WEAR_SKIN, WEAR)

        @JvmStatic
        fun getName(wear: Boolean): String {
            return if (wear) {
                watchAVD.name
            } else {
                phoneAVD.name
            }
        }

        fun getPreferredPlatform(wear: Boolean, abi: String?): String? {
            return if (wear) {
                AndroidBuild.TARGET_PLATFORM
            } else if (abi == "arm") {
                // The ARM images using Google APIs are too slow, so use the 
                // older Android (AOSP) images.      
                "android-$TARGET_SDK_ARM"
            } else {
                AndroidBuild.TARGET_PLATFORM
            }
        }

        @JvmStatic
        fun getPreferredPort(wear: Boolean): String? {
            var port: String? = ""
            if (wear) {
                port = Preferences.get("android.emulator.port.wear")
                if (port == null) {
                    port = DEFAULT_WEAR_PORT
                    Preferences.set("android.emulator.port.wear", port)
                }
            } else {
                port = Preferences.get("android.emulator.port.phone")
                if (port == null) {
                    port = DEFAULT_PHONE_PORT
                    Preferences.set("android.emulator.port.phone", port)
                }
            }
            return port
        }

         fun getPreferredTag(wear: Boolean, abi: String?): String {
            return if (wear) {
                "android-wear"
            } else if (abi == "arm") {
                // The ARM images using Google APIs are too slow, so use the 
                // older Android (AOSP) images.      
                "default"
            } else {
                "google_apis"
            }
        }

         val preferredABI: String?
             get() {
                var abi = Preferences.get("android.emulator.image.abi")
                if (abi == null) {
                    abi = DEFAULT_ABI
                    Preferences.set("android.emulator.image.abi", abi)
                }
                return abi
            }

        @Throws(IOException::class)
         fun list(sdk: AndroidSDK) {
            val prefABI = preferredABI

            try {
                avdList = ArrayList()
                badList = ArrayList()

                val avdManager = sdk.aVDManagerTool
                val pb = ProcessBuilder(avdManager.canonicalPath, "list", "avd")
                val env = pb.environment()

                env.clear()
                env["JAVA_HOME"] = Platform.getJavaHome().canonicalPath
                pb.redirectErrorStream(true)
                process = pb.start()

                val outWriter = StringWriter()
                StreamPump(process!!.getInputStream(), "out: ").addTarget(outWriter).start()
                process!!.waitFor()

                val lines = PApplet.split(outWriter.toString(), '\n')

                if (process!!.exitValue() == 0) {
                    var name = ""
                    var abi = ""
                    var badness = false
                    for (line in lines) {
                        val m = PApplet.match(line, "\\s+Name\\:\\s+(\\S+)")
                        val t = PApplet.match(line, "\\s+Tag/ABI\\:\\s+(\\S+)")
                        if (m != null) {
                            name = m[1]
                        }
                        if (t != null) {
                            abi = t[1]
                            if (-1 < abi.indexOf("/$prefABI")) {
                                if (!badness) {
//              System.out.println("good: " + m[1]);
                                    avdList!!.add(name)
                                } else {
//              System.out.println("bad: " + m[1]);
                                    badList!!.add(name)
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
                            badness = true
                            //            break;
                        }
                    }
                } else {
                    System.err.println("Unhappy inside exists()")
                    System.err.println(outWriter.toString())
                }
            } catch (ie: InterruptedException) {
            } finally {
                process!!.destroy()
            }
        }

        fun ensureProperAVD(window: Frame?, mode: AndroidMode?,
                            sdk: AndroidSDK?, wear: Boolean): Boolean {
            try {
                val avd = if (wear) watchAVD else phoneAVD
                if (avd.exists(sdk!!)) {
                    return true
                }

                if (avd.badness()) {
                    showMessage(getTextString("android_avd.error.cannot_load_avd_title"),
                            getTextString("android_avd.error.cannot_load_avd_body")!!)
                    return false
                }

                if (!avd.hasImages(sdk)) {
                    // Check that the AVD for the other kind of device has been already
                    // downloaded, and if so, the downloader should not ask for an 
                    // ABI again.
                    val other = if (wear) phoneAVD else watchAVD
                    val ask = !other.hasImages(sdk)
                    val res = locateSysImage(window, mode, wear, ask)
                    if (!res) {
                        return false
                    } else {
                        avd.refreshImages(sdk)
                    }
                }

                if (avd.create(sdk)) {
                    return true
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showMessage(getTextString("android_avd.error.cannot_create_avd_title"),
                        getTextString("android_avd.error.cannot_create_avd_body", AndroidBuild.TARGET_SDK))
            }
            return false
        }
    }
}