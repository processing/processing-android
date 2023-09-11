/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2014-21 The Processing Foundation
 
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

import processing.app.Messages;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;
import java.io.File;

/** 
 * Class handling the keystore where the users can store the credentials for 
 * their apps.
 */
public class AndroidKeyStore {
  public static final String ALIAS_STRING = "p5android-key";
  public static final int KEY_VALIDITY_YEARS = 25;
  public static final String KEYSTORE_FILE_NAME = "processing-upload-keystore.jks";

  public static File getKeyStore() {
    return getKeyStore(KEYSTORE_FILE_NAME);  
  }
  
  public static File getKeyStore(String name) {
    File keyStore = getKeyStoreLocation(name);
    if (!keyStore.exists()) return null;
    return keyStore;
  }

  public static File getKeyStoreLocation(String name) {
    File sketchbookFolder = processing.app.Base.getSketchbookFolder();
    File androidFolder = new File(sketchbookFolder, "android");    
    File keyStoreFolder = new File(androidFolder, "keystore");
    if (!keyStoreFolder.exists()) {
      boolean result = keyStoreFolder.mkdirs();

      if (!result) {
        Messages.showWarning(AndroidMode.getTextString("android_keystore.warn.cannot_create_folders.title"),
                             AndroidMode.getTextString("android_keystore.warn.cannot_create_folders.body"));
        return null;
      }
    }

    File keyStore = new File(keyStoreFolder, name);
    return keyStore;
  }

  public static void generateKeyStore(String password,
                                      String commonName, String organizationalUnit,
                                      String organizationName, String locality,
                                      String state, String country) throws Exception {
    String dnamePlaceholder = "CN=%s, OU=%s, O=%s, L=%s, S=%s, C=%s";
    String dname = String.format(dnamePlaceholder,
        parseDnameField(commonName), parseDnameField(organizationalUnit), parseDnameField(organizationName),
        parseDnameField(locality), parseDnameField(state), parseDnameField(country));

    ProcessHelper ph = new ProcessHelper(new String[] {
        "keytool", "-genkey",
        "-keystore", getKeyStoreLocation(KEYSTORE_FILE_NAME).getAbsolutePath(),
        "-alias", ALIAS_STRING,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", Integer.toString(KEY_VALIDITY_YEARS * 365),
        "-keypass", password,
        "-storepass", password,
        "-dname", dname
      });
    
    try {
      ProcessResult result = ph.execute();
      if (result.succeeded()) {
        if (getKeyStore() == null) {
          Messages.showWarning(AndroidMode.getTextString("android_keystore.warn.cannot_find_keystore.title"),
                               AndroidMode.getTextString("android_keystore.warn.cannot_find_keystore.body"));
        }
      } else {
        String[] lines = PApplet.split(result.getStderr(), '\n');
        System.err.println(AndroidMode.getTextString("android_keystore.error.cannot_create_keystore"));
        for (String line: lines) {
          System.err.println(line);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }          
  }

  public static boolean resetKeyStore() {
    File keyStore = getKeyStore();
    if (keyStore == null) return true;

    File keyStoreBackup = getKeyStoreLocation(KEYSTORE_FILE_NAME + "-" + AndroidMode.getDateStamp());
    if (!keyStore.renameTo(keyStoreBackup)) return false;
    return true;
  }

  private static String parseDnameField(String content) {
    if (content == null || content.length() == 0) return "Unknown";
    else return content;
  }
}
