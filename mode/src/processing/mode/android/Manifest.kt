/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2010-12 Ben Fry and Casey Reas

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

import org.xml.sax.SAXException

import processing.app.Messages
import processing.app.Sketch

import processing.core.PApplet
import processing.data.XML

import processing.mode.android.AndroidMode.Companion.getDateStamp
import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.android.AndroidUtil.createFileFromTemplate

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import javax.xml.parsers.ParserConfigurationException

/**
 * Class encapsulating the manifest file associated with a Processing sketch
 * in the Android mode.
 *
 */
internal class Manifest(private val sketch: Sketch?, private val appComp: Int, private val modeFolder: File, forceNew: Boolean) {

    /** the manifest data read from the file  */
    private var xml: XML? = null

    private fun defaultPackageName(): String {
        return BASE_PACKAGE + "." + sketch!!.name.toLowerCase()
    }

    private fun defaultVersionCode(): String {
        return "1"
    }

    private fun defaultVersionName(): String {
        return "1.0"
    }

    // called by other classes who want an actual package name
    // internally, we'll figure this out ourselves whether it's filled or not
    var packageName: String?
        get() {
            val pkg = xml!!.getString("package")
            return if (pkg.length == 0) defaultPackageName() else pkg
        }
        set(packageName) {
            xml!!.setString("package", packageName)
            save()
        }

    val versionCode: String
        get() {
            val code = xml!!.getString("android:versionCode")
            return if (code.length == 0) defaultVersionCode() else code
        }

    val versionName: String
        get() {
            val name = xml!!.getString("android:versionName")
            return if (name.length == 0) defaultVersionName() else name
        }

    fun setSdkTarget(version: String?) {
        val usesSdk = xml!!.getChild("uses-sdk")
        if (usesSdk != null) {
            usesSdk.setString("android:targetSdkVersion", version)
            save()
        }
    }// Non-standard permission (for example, wearables)
    // Store entire name.// Permission string contains path

    // ...unless they were initially missing.
// Don't add required permissions for watch faces and VR again...// ...except the ones for watch faces and VR apps.   

    // Don't remove non-standard permissions, such as
    // com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA
    // because these are set manually by the user.

    // ...and add the new permissions back

    // Remove all the old permissions...
    // Standard permission, remove perfix
    var permissions: Array<String?>
        get() {
            val elements = xml!!.getChildren("uses-permission")
            val count = elements.size
            val names = arrayOfNulls<String>(count)
            for (i in 0 until count) {
                val tmp = elements[i].getString("android:name")
                if (tmp.indexOf("android.permission") == 0) {
                    // Standard permission, remove perfix
                    val idx = tmp.lastIndexOf(".")
                    names[i] = tmp.substring(idx + 1)
                } else {
                    // Non-standard permission (for example, wearables)
                    // Store entire name.
                    names[i] = tmp
                }
            }
            return names
        }
        set(names) {
            var hasWakeLock = false
            var hasVibrate = false
            var hasReadExtStorage = false
            var hasCameraAccess = false

            // Remove all the old permissions...
            for (kid in xml!!.getChildren("uses-permission")) {
                val name = kid.getString("android:name")

                // ...except the ones for watch faces and VR apps.   
                if (appComp == AndroidBuild.WATCHFACE && name == PERMISSION_PREFIX + "WAKE_LOCK") {
                    hasWakeLock = true
                    continue
                }
                if (appComp == AndroidBuild.VR && name == PERMISSION_PREFIX + "VIBRATE") {
                    hasVibrate = true
                    continue
                }
                if (appComp == AndroidBuild.VR && name == PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE") {
                    hasReadExtStorage = true
                    continue
                }
                if (appComp == AndroidBuild.AR && name == PERMISSION_PREFIX + "CAMERA") {
                    hasCameraAccess = true
                    continue
                }

                // Don't remove non-standard permissions, such as
                // com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA
                // because these are set manually by the user.
                if (-1 < name.indexOf("com.google.android")) continue
                xml!!.removeChild(kid)
            }

            // ...and add the new permissions back
            for (name in names) {

                // Don't add required permissions for watch faces and VR again...
                if (appComp == AndroidBuild.WATCHFACE && name == "WAKE_LOCK") continue
                if (appComp == AndroidBuild.VR && name == "VIBRATE") continue
                if (appComp == AndroidBuild.VR && name == "READ_EXTERNAL_STORAGE") continue
                if (appComp == AndroidBuild.AR && name == PERMISSION_PREFIX + "CAMERA") continue
                val newbie = xml!!.addChild("uses-permission")
                if (-1 < name!!.indexOf(".")) {
                    // Permission string contains path
                    newbie.setString("android:name", name)
                } else {
                    newbie.setString("android:name", PERMISSION_PREFIX + name)
                }
            }

            // ...unless they were initially missing.
            if (appComp == AndroidBuild.WATCHFACE && !hasWakeLock) {
                xml!!.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "WAKE_LOCK")
            }
            if (appComp == AndroidBuild.VR && !hasVibrate) {
                xml!!.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "VIBRATE")
            }
            if (appComp == AndroidBuild.VR && !hasReadExtStorage) {
                xml!!.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE")
            }
            if (appComp == AndroidBuild.AR && !hasCameraAccess) {
                xml!!.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "CAMERA")
            }
            save()
        }

    private fun fixPermissions(mf: XML) {
        var hasWakeLock = false
        var hasVibrate = false
        var hasReadExtStorage = false
        var hasCameraAccess = false

        for (kid in mf.getChildren("uses-permission")) {
            val name = kid.getString("android:name")
            if (appComp == AndroidBuild.WATCHFACE && name == PERMISSION_PREFIX + "WAKE_LOCK") {
                hasWakeLock = true
                continue
            }
            if (appComp == AndroidBuild.VR && name == PERMISSION_PREFIX + "VIBRATE") {
                hasVibrate = true
                continue
            }
            if (appComp == AndroidBuild.VR && name == PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE") {
                hasReadExtStorage = true
                continue
            }
            if (appComp == AndroidBuild.AR && name == PERMISSION_PREFIX + "CAMERA") {
                hasCameraAccess = true
                continue
            }
            if (appComp == AndroidBuild.AR && !hasCameraAccess) {
                mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "CAMERA")
            }
        }
        if (appComp == AndroidBuild.WATCHFACE && !hasWakeLock) {
            mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "WAKE_LOCK")
        }
        if (appComp == AndroidBuild.VR && !hasVibrate) {
            mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "VIBRATE")
        }
        if (appComp == AndroidBuild.VR && !hasReadExtStorage) {
            mf.addChild("uses-permission").setString("android:name", PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE")
        }
    }

    private fun writeBlankManifest(xmlFile: File, appComp: Int) {
        val xmlTemplate = File(modeFolder, "templates/" + MANIFEST_TEMPLATE[appComp])
        val replaceMap = HashMap<String?, String?>()

        if (appComp == AndroidBuild.APP) {
            replaceMap["@@min_sdk@@"] = AndroidBuild.MIN_SDK_APP
        } else if (appComp == AndroidBuild.WALLPAPER) {
            replaceMap["@@min_sdk@@"] = AndroidBuild.MIN_SDK_WALLPAPER
        } else if (appComp == AndroidBuild.WATCHFACE) {
            replaceMap["@@min_sdk@@"] = AndroidBuild.MIN_SDK_WATCHFACE
        } else if (appComp == AndroidBuild.VR) {
            replaceMap["@@min_sdk@@"] = AndroidBuild.MIN_SDK_VR
        } else if (appComp == AndroidBuild.AR) {
            replaceMap["@@min_sdk@@"] = AndroidBuild.MIN_SDK_AR
        }
        createFileFromTemplate(xmlTemplate, xmlFile, replaceMap)
    }

    /**
     * Save a new version of the manifest info to the build location.
     * Also fill in any missing attributes that aren't yet set properly.
     */
    @Throws(IOException::class)
    fun writeCopy(file: File?, className: String?) {
        // write a copy to the build location
        save(file)

        // load the copy from the build location and start messing with it
        var mf: XML? = null

        try {
            mf = XML(file)

            // package name, or default
            val p = mf.getString("package").trim { it <= ' ' }
            if (p.length == 0) {
                mf.setString("package", defaultPackageName())
            }

            // app name and label, or the class name
            val app = mf.getChild("application")
            var label = app.getString("android:label")

            if (label.length == 0) {
                app.setString("android:label", className)
            }

            // Services need the label also in the service section
            if (appComp == AndroidBuild.WALLPAPER || appComp == AndroidBuild.WATCHFACE) {
                val serv = app.getChild("service")
                label = serv.getString("android:label")
                if (label.length == 0) {
                    serv.setString("android:label", className)
                }
            }

            // Make sure that the required permissions for watch faces, AR and VR apps are
            // included. 
            if (appComp == AndroidBuild.WATCHFACE || appComp == AndroidBuild.VR || appComp == AndroidBuild.AR) {
                fixPermissions(mf)
            }

            val writer = PApplet.createWriter(file)
            writer.print(mf.format(4))
            writer.flush()
            writer.close()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected fun load(forceNew: Boolean) {
        val manifestFile = manifestFile

        if (manifestFile.exists()) {

            try {
                xml = XML(manifestFile)
                val app = xml!!.getChild("application")
                val icon = app.getString("android:icon")
                if (icon == "@drawable/icon") {
                    // Manifest file generated with older version of the mode, replace icon and save
                    app.setString("android:icon", "@mipmap/ic_launcher")
                    if (!forceNew) save()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                System.err.println("Problem reading AndroidManifest.xml, creating a new version")

                // remove the old manifest file, rename it with date stamp
                val lastModified = manifestFile.lastModified()
                val stamp = getDateStamp(lastModified)
                val dest = File(sketch!!.folder, "$MANIFEST_XML.$stamp")
                val moved = manifestFile.renameTo(dest)

                if (!moved) {
                    System.err.println("Could not move/rename " + manifestFile.absolutePath)
                    System.err.println("You'll have to move or remove it before continuing.")
                    return
                }
            }
        }

        var permissionNames: Array<String?>? = null
        var pkgName: String? = null
        var versionCode: String? = null
        var versionName: String? = null

        if (xml != null && forceNew) {
            permissionNames = permissions
            pkgName = packageName
            versionCode = versionCode
            versionName = versionName
            xml = null
        }
        if (xml == null) {
            writeBlankManifest(manifestFile, appComp)

            try {
                xml = XML(manifestFile)
                if (permissionNames != null) {
                    permissions = permissionNames
                }
                if (pkgName != null) {
                    xml!!.setString("package", pkgName)
                }
                if (versionCode != null) {
                    xml!!.setString("android:versionCode", versionCode)
                }
                if (versionName != null) {
                    xml!!.setString("android:versionName", versionName)
                }
            } catch (e: FileNotFoundException) {
                System.err.println("Could not read " + manifestFile.absolutePath)
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: ParserConfigurationException) {
                e.printStackTrace()
            } catch (e: SAXException) {
                e.printStackTrace()
            }
        }

        if (xml == null) {
            Messages.showWarning(getTextString("manifest.warn.cannot_handle_file_title", MANIFEST_XML),
                    getTextString("manifest.warn.cannot_handle_file_body", MANIFEST_XML))
        }
    }

    /**
     * Save to the sketch folder, so that it can be copied in later.
     */
     private fun save(file: File? = manifestFile) {
        val writer = PApplet.createWriter(file)
        //    xml.write(writer);
        writer.print(xml!!.format(4))
        writer.flush()
        writer.close()
    }

    private val manifestFile: File
        private get() = File(sketch!!.folder, MANIFEST_XML)

    companion object {
        const val MANIFEST_XML = "AndroidManifest.xml"
        const val MANIFEST_ERROR_TITLE = "Error handling $MANIFEST_XML"
        const val MANIFEST_ERROR_MESSAGE = "Errors occurred while reading or writing " + MANIFEST_XML + ",\n" +
                "which means lots of things are likely to stop working properly.\n" +
                "To prevent losing any data, it's recommended that you use “Save As”\n" +
                "to save a separate copy of your sketch, and then restart Processing."
        private val MANIFEST_TEMPLATE = arrayOf(
                "AppManifest.xml.tmpl",
                "WallpaperManifest.xml.tmpl",
                "WatchFaceManifest.xml.tmpl",
                "VRManifest.xml.tmpl",
                "ARManifest.xml.tmpl"
        )

        // Default base package name, user need to change when exporting package. 
        const val BASE_PACKAGE = "processing.test"
        const val PERMISSION_PREFIX = "android.permission."
    }

    // constructor or initializer block
    // will be executed before any other operation
    init {
        load(forceNew)
    }
}