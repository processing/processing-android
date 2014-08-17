package processing.mode.android.signing;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.jar.JarOutputStream;

public interface IApkSignatureProvider {

  public void writeSignatureBlock(byte[] message, String signatureAlgorithm, X509Certificate publicKey,
                                  PrivateKey privateKey,JarOutputStream mOutputJar)throws IOException, GeneralSecurityException;

}
