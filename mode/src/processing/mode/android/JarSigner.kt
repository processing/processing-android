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

import sun.security.pkcs.ContentInfo
import sun.security.pkcs.PKCS7
import sun.security.pkcs.SignerInfo
import sun.security.x509.AlgorithmId
import sun.security.x509.X500Name

import java.io.*
import java.security.*
import java.security.cert.X509Certificate

import java.util.*
import java.util.jar.*
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Created by ibziy_000 on 17.08.2014.
 */
internal object JarSigner {
    private const val DIGEST_ALGORITHM = "SHA1"
    private const val DIGEST_ATTR = "SHA1-Digest"
    private const val DIGEST_MANIFEST_ATTR = "SHA1-Digest-Manifest"
    private var certFileContents: SignatureOutputStream? = null
    private lateinit var buffer: ByteArray

    @Throws(GeneralSecurityException::class, IOException::class, NoSuchAlgorithmException::class)
    fun signJar(jarToSign: File?, outputJar: File?, alias: String?,
                keypass: String, keystore: String?, storepass: String) {

        var privateKey: PrivateKey? = null
        var x509Cert: X509Certificate? = null
        var manifest: Manifest? = null
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        val fis = FileInputStream(keystore)

        keyStore.load(fis, storepass.toCharArray())
        fis.close()

        val entry = keyStore.getEntry(
                alias, KeyStore.PasswordProtection(keypass.toCharArray())) as KeyStore.PrivateKeyEntry

        if (entry != null) {
            privateKey = entry.privateKey
            x509Cert = entry.certificate as X509Certificate
        } else {
            throw KeyStoreException("Couldn't get key")
        }

        val signedJar = JarOutputStream(FileOutputStream(outputJar, false))
        signedJar.setLevel(9)

        if (privateKey != null && x509Cert != null) {
            manifest = Manifest()
            val main = manifest.mainAttributes
            main.putValue("Manifest-Version", "1.0")
            main.putValue("Created-By", "1.0 (Android)")
        }

        writeZip(FileInputStream(jarToSign), signedJar, manifest)
        closeJar(signedJar, manifest, privateKey, x509Cert)
    }

    @Throws(IOException::class, NoSuchAlgorithmException::class)
    private fun writeZip(input: InputStream, output: JarOutputStream, manifest: Manifest?) {

        val base64Encoder = Base64.getEncoder()
        val messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        buffer = ByteArray(4096)
        val zis = ZipInputStream(input)

        try {
            // loop on the entries of the intermediary package and put them in the final package.
            var entry: ZipEntry
            while (zis.nextEntry.also { entry = it } != null) {
                val name = entry.name

                // do not take directories or anything inside a potential META-INF folder.
                if (entry.isDirectory || name.startsWith("META-INF/")) {
                    continue
                }
                var newEntry: JarEntry

                // Preserve the STORED method of the input entry.
                newEntry = if (entry.method == JarEntry.STORED) {
                    JarEntry(entry)
                } else {
                    // Create a new entry so that the compressed len is recomputed.
                    JarEntry(name)
                }
                writeEntry(output, zis, newEntry, messageDigest, manifest, base64Encoder)
                zis.closeEntry()
            }
        } finally {
            zis.close()
        }
    }

    @Throws(IOException::class)
    private fun writeEntry(output: JarOutputStream, input: InputStream, entry: JarEntry,
                           digest: MessageDigest?, manifest: Manifest?, encoder: Base64.Encoder) {
        output.putNextEntry(entry)

        // Write input stream to the jar output.
        var count: Int

        while (input.read(buffer).also { count = it } != -1) {
            output.write(buffer, 0, count)
            digest?.update(buffer, 0, count)
        }

        output.closeEntry()

        if (manifest != null) {
            var attr = manifest.getAttributes(entry.name)
            if (attr == null) {
                attr = Attributes()
                manifest.entries[entry.name] = attr
            }
            attr.putValue(DIGEST_ATTR, encoder.encodeToString(digest!!.digest()))
        }
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    private fun closeJar(jar: JarOutputStream, manifest: Manifest?,
                         key: PrivateKey?, cert: X509Certificate?) {
        if (manifest != null) {
            // write the manifest to the jar file
            jar.putNextEntry(JarEntry(JarFile.MANIFEST_NAME))
            manifest.write(jar)

            // CERT.SF
            val signature = Signature.getInstance("SHA1with" + key!!.algorithm)
            signature.initSign(key)
            jar.putNextEntry(JarEntry("META-INF/CERT.SF"))

            //Caching the SignatureOutputStream object for future use by the signature provider extensions.
            certFileContents = SignatureOutputStream(jar, signature)
            writeSignatureFile(certFileContents, manifest)

            // CERT.*
            jar.putNextEntry(JarEntry("META-INF/CERT." + key.algorithm))
            writeSignature(jar, signature, cert, key)
        }
        jar.close()
    }

    // Writes a .SF file with a digest to the manifest.
    @Throws(IOException::class, GeneralSecurityException::class)
    private fun writeSignatureFile(out: SignatureOutputStream?, manifest: Manifest) {
        val sf = Manifest()
        val main = sf.mainAttributes

        main.putValue("Signature-Version", "1.0")
        main.putValue("Created-By", "1.0 (Android)")

        val base64 = Base64.getEncoder()
        val md = MessageDigest.getInstance(DIGEST_ALGORITHM)
        val print = PrintStream(
                DigestOutputStream(ByteArrayOutputStream(), md),
                true, "UTF-8")

        // Digest of the entire manifest
        manifest.write(print)
        print.flush()
        main.putValue(DIGEST_MANIFEST_ATTR, base64.encodeToString(md.digest()))

        val entries = manifest.entries

        for ((key, value) in entries) {
            // Digest of the manifest stanza for this entry.
            print.print("""
    Name: $key
    
    """.trimIndent())
            for ((key1, value1) in value) {
                print.print("""
    $key1: $value1
    
    """.trimIndent())
            }
            print.print("\r\n")
            print.flush()

            val sfAttr = Attributes()
            sfAttr.putValue(DIGEST_ATTR, base64.encodeToString(md.digest()))
            sf.entries[key] = sfAttr
        }

        sf.write(out)

        // A bug in the java.util.jar implementation of Android platforms
        // up to version 1.6 will cause a spurious IOException to be thrown
        // if the length of the signature file is a multiple of 1024 bytes.
        // As a workaround, add an extra CRLF in this case.
        if (out!!.size() % 1024 == 0) {
            out.write('\r'.toInt())
            out.write('\n'.toInt())
        }
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    private fun writeSignature(outputJar: JarOutputStream,
                               signature: Signature, publicKey: X509Certificate?, privateKey: PrivateKey?) {
        writeSignatureBlock(outputJar, signature, publicKey, privateKey)
    }

    // Write the certificate file with a digital signature.
    @Throws(IOException::class, GeneralSecurityException::class)
    private fun writeSignatureBlock(outputJar: JarOutputStream,
                                    signature: Signature, publicKey: X509Certificate?, privateKey: PrivateKey?) {
        val signerInfo = SignerInfo(
                X500Name(publicKey!!.issuerX500Principal.name),
                publicKey.serialNumber,
                AlgorithmId.get(DIGEST_ALGORITHM),
                AlgorithmId.get(privateKey!!.algorithm),
                signature.sign())

        val pkcs7 = PKCS7(arrayOf(AlgorithmId.get(DIGEST_ALGORITHM)),
                ContentInfo(ContentInfo.DATA_OID, null), arrayOf<X509Certificate?>(publicKey), arrayOf(signerInfo))
        pkcs7.encodeSignedData(outputJar)
    }

    private class SignatureOutputStream(out: OutputStream?, private val signature: Signature) : FilterOutputStream(out) {
        private var count = 0
        private val contents: MutableList<Byte> = ArrayList()

        @Throws(IOException::class)
        override fun write(b: Int) {
            try {
                signature.update(b.toByte())
                contents.add(b.toByte())
            } catch (e: SignatureException) {
                throw IOException("SignatureException: $e")
            }
            super.write(b)
            count++
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {

            try {
                signature.update(b, off, len)
                for (myByte in b) {
                    contents.add(myByte)
                }
            } catch (e: SignatureException) {
                throw IOException("SignatureException: $e")
            }
            super.write(b, off, len)
            count += len
        }

        fun size(): Int {
            return count
        }

    }
}