package processing.mode.android.signing;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
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
