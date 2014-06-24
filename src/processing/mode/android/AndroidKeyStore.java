package processing.mode.android;

import processing.app.Base;

import java.io.File;

/**
 * Created with IntelliJ IDEA.
 * User: imilka
 * Date: 27.05.14
 * Time: 14:38
 */
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
        Base.showWarning("Folders, folders, folders",
            "Could not create the necessary folders to build.\n" +
                "Perhaps you have some file permissions to sort out?", null);
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

    String[] args = {
        System.getProperty("java.home")
            + System.getProperty("file.separator") + "bin"
            + System.getProperty("file.separator") + "keytool", "-genkey",
        "-keystore", getKeyStoreLocation().getAbsolutePath(),
        "-alias", ALIAS_STRING,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-keypass", password,
        "-storepass", password,
        "-dname", dname
    };

    Process generation = Runtime.getRuntime().exec(args);
    generation.waitFor();

    if (getKeyStore() == null) throw new Exception();
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
