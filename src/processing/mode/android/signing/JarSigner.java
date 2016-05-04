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

package processing.mode.android.signing;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;

/**
 * Created by ibziy_000 on 17.08.2014.
 */
public class JarSigner {
  public static void signJar(File jarToSign, File outputJar, String alias, String keypass, String keystore, String storepass)
      throws GeneralSecurityException, IOException, SignedJarBuilder.IZipEntryFilter.ZipAbortException {

    PrivateKey mPrivateKey;
    X509Certificate mCertificate;

    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    FileInputStream fis = new FileInputStream(keystore);
    keyStore.load(fis, storepass.toCharArray());
    fis.close();

    KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(
        alias, new KeyStore.PasswordProtection(keypass.toCharArray()));
    if (entry != null) {
      mPrivateKey = entry.getPrivateKey();
      mCertificate = (X509Certificate) entry.getCertificate();
    } else {
      throw new KeyStoreException("Couldn't get key");
    }

    SignedJarBuilder builder = new SignedJarBuilder(
        new FileOutputStream(outputJar, false), mPrivateKey, mCertificate);
    builder.writeZip(new FileInputStream(jarToSign), null);
    builder.close();
  }
}
