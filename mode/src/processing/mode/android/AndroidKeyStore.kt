/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2014-16 The Processing Foundation
 
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
import processing.app.Messages
import processing.app.exec.ProcessHelper
import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.dateStamp
import processing.mode.android.AndroidMode.Companion.getTextString

import java.io.File

/**
 * Class handling the keystore where the users can store the credentials for
 * their apps.
 */
 internal object AndroidKeyStore {
    const val ALIAS_STRING = "processing-keystore"
    const val KEYSTORE_FILE_NAME = "android-release-key.keystore"

    val keyStore: File?
        get() = getKeyStore(KEYSTORE_FILE_NAME)

    fun getKeyStore(name: String?): File? {
        val keyStore = getKeyStoreLocation(name)
        return if (!keyStore!!.exists()) null else keyStore
    }

    fun getKeyStoreLocation(name: String?): File? {
        val sketchbookFolder = Base.getSketchbookFolder()
        val androidFolder = File(sketchbookFolder, "android")
        val keyStoreFolder = File(androidFolder, "keystore")
        if (!keyStoreFolder.exists()) {
            val result = keyStoreFolder.mkdirs()
            if (!result) {
                Messages.showWarning(getTextString("android_keystore.warn.cannot_create_folders.title"),
                        getTextString("android_keystore.warn.cannot_create_folders.body"))
                return null
            }
        }
        return File(keyStoreFolder, name)
    }

    @Throws(Exception::class)
    fun generateKeyStore(password: String,
                         commonName: String?, organizationalUnit: String?,
                         organizationName: String?, locality: String?,
                         state: String?, country: String?) {

        val dnamePlaceholder = "CN=%s, OU=%s, O=%s, L=%s, S=%s, C=%s"

        val dname = String.format(dnamePlaceholder,
                parseDnameField(commonName), parseDnameField(organizationalUnit), parseDnameField(organizationName),
                parseDnameField(locality), parseDnameField(state), parseDnameField(country))

        val ph = ProcessHelper(*arrayOf(
                "keytool", "-genkey",
                "-keystore", getKeyStoreLocation(KEYSTORE_FILE_NAME)!!.absolutePath,
                "-alias", ALIAS_STRING,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-keypass", password,
                "-storepass", password,
                "-dname", dname
        ))

        try {
            val result = ph.execute()
            if (result.succeeded()) {
                if (keyStore == null) {
                    Messages.showWarning(getTextString("android_keystore.warn.cannot_find_keystore.title"),
                            getTextString("android_keystore.warn.cannot_find_keystore.body"))
                }
            } else {
                val lines = PApplet.split(result.stderr, '\n')
                System.err.println(getTextString("android_keystore.error.cannot_create_keystore"))
                for (line in lines) {
                    System.err.println(line)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resetKeyStore(): Boolean {
        val keyStore = keyStore ?: return true
        val keyStoreBackup = getKeyStoreLocation("$KEYSTORE_FILE_NAME-$dateStamp")
        return if (!keyStore.renameTo(keyStoreBackup)) false else true
    }

    private fun parseDnameField(content: String?): String {
        return if (content == null || content.length == 0) "Unknown" else content
    }
}