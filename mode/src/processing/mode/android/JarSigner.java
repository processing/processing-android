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

import java.io.*;

import java.security.*;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Base64;
import sun.security.pkcs.SignerInfo;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.ContentInfo;

/**
 * Created by ibziy_000 on 17.08.2014.
 */
public class JarSigner {
  private static final String DIGEST_ALGORITHM = "SHA1";
  private static final String DIGEST_ATTR = "SHA1-Digest";
  private static final String DIGEST_MANIFEST_ATTR = "SHA1-Digest-Manifest";
  private static SignatureOutputStream certFileContents = null;
  private static byte[] buffer;
  
  public static void signJarV1(File jarToSign, File outputJar, String alias, 
      String keypass, String keystore, String storepass)
      throws GeneralSecurityException, IOException, NoSuchAlgorithmException {

    PrivateKey privateKey = null;
    X509Certificate x509Cert = null;
    Manifest manifest = null;
    
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    FileInputStream fis = new FileInputStream(keystore);
    keyStore.load(fis, storepass.toCharArray());
    fis.close();

    KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(
        alias, new KeyStore.PasswordProtection(keypass.toCharArray()));
    if (entry != null) {
      privateKey = entry.getPrivateKey();
      x509Cert = (X509Certificate) entry.getCertificate();
    } else {
      throw new KeyStoreException("Couldn't get key");
    }
    
    JarOutputStream signedJar = new JarOutputStream(new FileOutputStream(outputJar, false));
    signedJar.setLevel(9);
    if (privateKey != null && x509Cert != null) {
      manifest = new Manifest();
      Attributes main = manifest.getMainAttributes();
      main.putValue("Manifest-Version", "1.0");      
      main.putValue("Created-By", "1.0 (Android)");      
      main.putValue("X-Android-APK-Signed", "true");
    } 
    
    writeZip(new FileInputStream(jarToSign), signedJar, manifest);
    
    closeJar(signedJar, manifest, privateKey, x509Cert);
  }
  
  private static void writeZip(InputStream input, JarOutputStream output, Manifest manifest)
      throws IOException, NoSuchAlgorithmException {    
    Base64.Encoder base64Encoder = Base64.getEncoder();
    MessageDigest messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
    buffer = new byte[4096];
    
    ZipInputStream zis = new ZipInputStream(input);

    try {
      // loop on the entries of the intermediary package and put them in the final package.
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String name = entry.getName();

        // do not take directories or anything inside a potential META-INF folder.
        if (entry.isDirectory() || name.startsWith("META-INF/")) {
          continue;
        }

        JarEntry newEntry;

        // Preserve the STORED method of the input entry.
        if (entry.getMethod() == JarEntry.STORED) {
          newEntry = new JarEntry(entry);
        } else {
          // Create a new entry so that the compressed len is recomputed.
          newEntry = new JarEntry(name);
        }

        writeEntry(output, zis, newEntry, messageDigest, manifest, base64Encoder);

        zis.closeEntry();
      }
    } finally {
      zis.close();
    }
  }

  private static void writeEntry(JarOutputStream output, InputStream input, JarEntry entry, 
      MessageDigest digest, Manifest manifest, Base64.Encoder encoder) throws IOException {
    output.putNextEntry(entry);

    // Write input stream to the jar output.
    int count;
    while ((count = input.read(buffer)) != -1) {
      output.write(buffer, 0, count);

      if (digest != null) digest.update(buffer, 0, count);
    }

    output.closeEntry();

    if (manifest != null) {
      Attributes attr = manifest.getAttributes(entry.getName());
      if (attr == null) {
        attr = new Attributes();
        manifest.getEntries().put(entry.getName(), attr);
      }
      attr.putValue(DIGEST_ATTR, encoder.encodeToString(digest.digest()));
    }
  }
  
  private static void closeJar(JarOutputStream jar, Manifest manifest, 
      PrivateKey key, X509Certificate cert) 
      throws IOException, GeneralSecurityException {
    if (manifest != null) {
      // write the manifest to the jar file
      jar.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
      manifest.write(jar);

      // CERT.SF
      Signature signature = Signature.getInstance("SHA1with" + key.getAlgorithm());
      signature.initSign(key);
      jar.putNextEntry(new JarEntry("META-INF/CERT.SF"));
      //Caching the SignatureOutputStream object for future use by the signature provider extensions.
      certFileContents = new SignatureOutputStream(jar, signature);
      writeSignatureFile(certFileContents, manifest);

      // CERT.*
      jar.putNextEntry(new JarEntry("META-INF/CERT." + key.getAlgorithm()));
      writeSignature(jar, signature, cert, key);
    }

    jar.close();
  }
  
  // Writes a .SF file with a digest to the manifest.
  private static void writeSignatureFile(SignatureOutputStream out, Manifest manifest)
      throws IOException, GeneralSecurityException {
    Manifest sf = new Manifest();
    Attributes main = sf.getMainAttributes();
    main.putValue("Signature-Version", "1.0");
    main.putValue("Created-By", "1.0 (Android)");

    Base64.Encoder base64 = Base64.getEncoder();
    MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
    PrintStream print = new PrintStream(
        new DigestOutputStream(new ByteArrayOutputStream(), md),
        true, "UTF-8");

    // Digest of the entire manifest
    manifest.write(print);
    print.flush();
    main.putValue(DIGEST_MANIFEST_ATTR, base64.encodeToString(md.digest()));

    Map<String, Attributes> entries = manifest.getEntries();
    for (Map.Entry<String, Attributes> entry : entries.entrySet()) {
      // Digest of the manifest stanza for this entry.
      print.print("Name: " + entry.getKey() + "\r\n");
      for (Map.Entry<Object, Object> att : entry.getValue().entrySet()) {
        print.print(att.getKey() + ": " + att.getValue() + "\r\n");
      }
      print.print("\r\n");
      print.flush();

      Attributes sfAttr = new Attributes();
      sfAttr.putValue(DIGEST_ATTR, base64.encodeToString(md.digest()));
      sf.getEntries().put(entry.getKey(), sfAttr);
    }

    sf.write(out);

    // A bug in the java.util.jar implementation of Android platforms
    // up to version 1.6 will cause a spurious IOException to be thrown
    // if the length of the signature file is a multiple of 1024 bytes.
    // As a workaround, add an extra CRLF in this case.
    if ((out.size() % 1024) == 0) {
      out.write('\r');
      out.write('\n');
    }
  }
  
  private static void writeSignature(JarOutputStream outputJar, 
      Signature signature, X509Certificate publicKey, PrivateKey privateKey)
    throws IOException, GeneralSecurityException{
    writeSignatureBlock(outputJar, signature, publicKey, privateKey);
  }
  
  // Write the certificate file with a digital signature.
  private static void writeSignatureBlock(JarOutputStream outputJar, 
      Signature signature, X509Certificate publicKey, PrivateKey privateKey)
      throws IOException, GeneralSecurityException {
    
    SignerInfo signerInfo = new SignerInfo(
        new X500Name(publicKey.getIssuerX500Principal().getName()),
        publicKey.getSerialNumber(),
        AlgorithmId.get(DIGEST_ALGORITHM),
        AlgorithmId.get(privateKey.getAlgorithm()),
        signature.sign());

    PKCS7 pkcs7 = new PKCS7(
        new AlgorithmId[] { AlgorithmId.get(DIGEST_ALGORITHM) },
        new ContentInfo(ContentInfo.DATA_OID, null),
        new X509Certificate[] { publicKey },
        new SignerInfo[] { signerInfo });

    pkcs7.encodeSignedData(outputJar);
  }  
  
  private static class SignatureOutputStream extends FilterOutputStream {
    private Signature signature;
    private int count = 0;
    private List<Byte> contents = new ArrayList<Byte>();

    public SignatureOutputStream(OutputStream out, Signature sig) {
      super(out);
      signature = sig;
    }

    @Override
    public void write(int b) throws IOException {
      try {
        signature.update((byte) b);
        contents.add((byte)b);
      } catch (SignatureException e) {
        throw new IOException("SignatureException: " + e);
      }
      super.write(b);
      count++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      try {
        signature.update(b, off, len);
        for (byte myByte: b) {
          contents.add(myByte);
        }
      } catch (SignatureException e) {
        throw new IOException("SignatureException: " + e);
      }
      super.write(b, off, len);
      count += len;
    }

    public int size() {
      return count;
    }
  }
}