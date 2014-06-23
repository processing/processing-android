package processing.mode.android;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.SignJar;

import java.io.File;

public class JarSigner extends SignJar {

  public JarSigner() {
    setProject(new Project());
    getProject().init();
    setTaskName("signJar");
    setTaskType("signJar");
    setOwningTarget(new Target());
  }

  public static void signJar(File jarToSign, String alias, String keypass, String keystore, String storepass) {
    JarSigner signer = new JarSigner();
    signer.setJar(jarToSign);
    signer.setAlias(alias);
    signer.setKeypass(keypass);
    signer.setKeystore(keystore);
    signer.setStorepass(storepass);
    signer.setSignedjar(jarToSign);
    signer.execute();
  }
}