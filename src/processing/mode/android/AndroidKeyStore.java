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

package processing.mode.android;

import processing.app.Messages;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.core.PApplet;

import java.io.File;


public class AndroidKeyStore {
  public static final String ALIAS_STRING = "processing-keystore";
  public static final String KEYSTORE_FILE_NAME = "android-release-key.keystore";

  public static File getKeyStore() {
    File keyStore = getKeyStoreLocation();
    if (!keyStore.exists()) return null;
    return keyStore;
  }

  public static File getKeyStoreLocation() {
    File sketchbookFolder = processing.app.Base.getSketchbookFolder();
    File keyStoreFolder = new File(sketchbookFolder, "keystore");
    if (!keyStoreFolder.exists()) {
      boolean result = keyStoreFolder.mkdirs();

      if (!result) {
        Messages.showWarning("Folders, folders, folders",
                             "Could not create the necessary folders to build.\n" +
                             "Perhaps you have some file permissions to sort out?");
        return null;
      }
    }

    File keyStore = new File(keyStoreFolder, KEYSTORE_FILE_NAME);
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
        "-keystore", getKeyStoreLocation().getAbsolutePath(),
        "-alias", ALIAS_STRING,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-keypass", password,
        "-storepass", password,
        "-dname", dname
      });
    
    try {
      ProcessResult result = ph.execute();
      if (result.succeeded()) {
        if (getKeyStore() == null) {
          Messages.showWarning("Well, this is unexpected...",
              "The keystore was succesfully cretated but cannot be found.\n" +
              "Perhaps was it deleted accidentally?");
        }
      } else {
        String[] lines = PApplet.split(result.getStderr(), '\n');
        System.err.println("The keystore could not be created, due to the following error:");
        for (String line: lines) {
          System.err.println(line);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }      
    
//    String[] args = {
//        System.getProperty("java.home")
//            + System.getProperty("file.separator") + "bin"
//            + System.getProperty("file.separator") + "keytool", "-genkey",
//        "-keystore", getKeyStoreLocation().getAbsolutePath(),
//        "-alias", ALIAS_STRING,
//        "-keyalg", "RSA",
//        "-keysize", "2048",
//        "-validity", "10000",
//        "-keypass", password,
//        "-storepass", password,
//        "-dname", dname
//    };
//
//    Process generation = Runtime.getRuntime().exec(args);
//    generation.waitFor();

//    if (getKeyStore() == null) throw new Exception();
  }

  public static boolean resetKeyStore() {
    File keyStore = getKeyStore();
    if (keyStore == null) return true;

    File keyStoreBackup = new File(processing.app.Base.getSketchbookFolder(), "keystore/" + KEYSTORE_FILE_NAME + "-" + AndroidMode.getDateStamp());
    if (!keyStore.renameTo(keyStoreBackup)) return false;
    return true;
  }

  private static String parseDnameField(String content) {
    if (content == null || content.length() == 0) return "Unknown";
    else return content;
  }
}
