package processing.mode.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import processing.app.Base;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("serial")
public class SDKDownloader extends JFrame implements PropertyChangeListener {

  private static final String URL_REPOSITORY = "https://dl-ssl.google.com/android/repository/repository-11.xml";
  private static final String URL_REPOSITORY_FOLDER = "http://dl-ssl.google.com/android/repository/";
  private static final String URL_USB_DRIVER = "https://dl-ssl.google.com//android/repository/latest_usb_driver_windows.zip";

  private static final String PLATFORM_API_LEVEL = "22";

  public static final String PROPERTY_CHANGE_EVENT_TOTAL = "total";
  private static final String PROPERTY_CHANGE_EVENT_DOWNLOADED = "downloaded";

  private AndroidMode androidMode;

  JProgressBar progressBar;
  JLabel downloadedTextArea;

  private int totalSize = 0;
  private static ZipFile zip;

  class SDKUrlHolder {
    public String platformToolsUrl, buildToolsUrl, platformUrl, toolsUrl;
    public String platformToolsFilename, buildToolsFilename, platformFilename, toolsFilename;
    public int totalSize = 0;
  }

  class SDKDownloadTask extends SwingWorker<Object, Object> {

    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    @Override
    protected Object doInBackground() throws Exception {
      File modeFolder = new File(Base.getSketchbookModesFolder() + "/AndroidMode");

      // creating sdk folders
      File sdkFolder = new File(modeFolder, "sdk");
      if (!sdkFolder.exists()) sdkFolder.mkdir();
      File platformsFolder = new File(sdkFolder, "platforms");
      if (!platformsFolder.exists()) platformsFolder.mkdir();
      File buildToolsFolder = new File(sdkFolder, "build-tools");
      if (!buildToolsFolder.exists()) buildToolsFolder.mkdir();
      File extrasFolder = new File(sdkFolder, "extras");
      if(!extrasFolder.exists()) extrasFolder.mkdir();

      // creating temp folder for downloaded zip packages
      File tempFolder = new File(modeFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        SDKUrlHolder downloadUrls = getDownloadUrls(URL_REPOSITORY, Platform.getName());
        firePropertyChange(PROPERTY_CHANGE_EVENT_TOTAL, 0, downloadUrls.totalSize);
        totalSize = downloadUrls.totalSize;

        // tools
        File downloadedTools = new File(tempFolder, downloadUrls.toolsFilename);
        downloadAndUnpack(downloadUrls.toolsUrl, downloadedTools, sdkFolder);

        // platform-tools
        File downloadedPlatformTools = new File(tempFolder, downloadUrls.platformToolsFilename);
        downloadAndUnpack(downloadUrls.platformToolsUrl, downloadedPlatformTools, sdkFolder);

        // build-tools
        File downloadedBuildTools = new File(tempFolder, downloadUrls.buildToolsFilename);
        downloadAndUnpack(downloadUrls.buildToolsUrl, downloadedBuildTools, buildToolsFolder);

        // platform
        File downloadedPlatform = new File(tempFolder, downloadUrls.platformFilename);
        downloadAndUnpack(downloadUrls.platformUrl, downloadedPlatform, platformsFolder);

        // usb driver
        if (Platform.isWindows()) {
          File usbDriverFolder = new File(extrasFolder, "google");
          File downloadedFolder = new File(tempFolder, "latest_usb_driver_windows.zip");
          downloadAndUnpack(URL_USB_DRIVER, downloadedFolder, usbDriverFolder);
        }

        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sdkFolder.getAbsolutePath());
        }

        tempFolder.delete();

        Platform.setenv("ANDROID_SDK", sdkFolder.getAbsolutePath());
        Preferences.set("android.sdk.path", sdkFolder.getAbsolutePath());
        androidMode.loadSDK();
      } catch (ParserConfigurationException e) {
        // TODO Handle exceptions here somehow (ie show error message)
        // and handle at least mkdir() results (above)
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (SAXException e) {
        e.printStackTrace();
      }
      return null;
    }

    @Override
    protected void done() {
      super.done();
      setVisible(false);
    }

    private void downloadAndUnpack(String urlString, File saveTo,
                                   File unpackTo) throws IOException {
      URL url = null;
      try {
        url = new URL(urlString);
      } catch (MalformedURLException e) {
    	  //This is expected for API level 14 and more
    	  try {
    		  url = new URL(URL_REPOSITORY_FOLDER + urlString);
    	  } catch (MalformedURLException e1) {
    		  //This exception is not expected. Need to return.
    		  e1.printStackTrace();
    		  return;
    	  }
      }
      URLConnection conn = url.openConnection();

      InputStream inputStream = conn.getInputStream();
      FileOutputStream outputStream = new FileOutputStream(saveTo);

      byte[] b = new byte[BUFFER_SIZE];
      int count;
      while ((count = inputStream.read(b)) >= 0) {
        outputStream.write(b, 0, count);
        downloadedSize += count;

        firePropertyChange(PROPERTY_CHANGE_EVENT_DOWNLOADED, 0, downloadedSize);
      }
      outputStream.flush(); outputStream.close(); inputStream.close();

      inputStream.close();
      outputStream.close();

      extractFolder(saveTo, unpackTo);
    }

    /*
    private String getOsString() {
      if (Base.isWindows()) {
        return "windows";
      } else if (Base.isLinux()) {
        return "linux";
      } else {
        return "macosx";
      }
    }
    */

    private SDKUrlHolder getDownloadUrls(String repositoryUrl, String requiredHostOs) throws ParserConfigurationException, IOException, SAXException {
      SDKUrlHolder urlHolder = new SDKUrlHolder();

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new URL(repositoryUrl).openStream());

      // platform
      NodeList platformList = doc.getElementsByTagName("sdk:platform");
      for(int i = 0; i < platformList.getLength(); i++) {
        Node platform = platformList.item(i);
        if (((Element) platform).getElementsByTagName("sdk:api-level").item(0).getTextContent().equals(PLATFORM_API_LEVEL)) {
          Node archiveListItem = ((Element) platform).getElementsByTagName("sdk:archives").item(0);
          Node archiveItem = ((Element) archiveListItem).getElementsByTagName("sdk:archive").item(0);
          urlHolder.platformUrl = ((Element) archiveItem).getElementsByTagName("sdk:url").item(0).getTextContent();
          urlHolder.platformFilename = urlHolder.platformUrl.split("/")[urlHolder.platformUrl.split("/").length-1];
          urlHolder.totalSize += Integer.parseInt(((Element) archiveItem).getElementsByTagName("sdk:size").item(0).getTextContent());
        }
      }

      // platform-tools
      Node platformToolItem = doc.getElementsByTagName("sdk:platform-tool").item(0);
      Node archiveListItem = ((Element) platformToolItem).getElementsByTagName("sdk:archives").item(0);
      NodeList archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
      for(int i = 0; i < archiveList.getLength(); i++) {
        Node archive = archiveList.item(i);
        String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
        if (hostOs.equals(requiredHostOs)) {
          urlHolder.platformToolsFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          urlHolder.platformToolsUrl = URL_REPOSITORY_FOLDER + urlHolder.platformToolsFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
          break;
        }
      }

      // build-tools
      Node buildToolsItem = doc.getElementsByTagName("sdk:build-tool").item(doc.getElementsByTagName("sdk:build-tool").getLength()-1);
      archiveListItem = ((Element) buildToolsItem).getElementsByTagName("sdk:archives").item(0);
      archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
      for(int i = 0; i < archiveList.getLength(); i++) {
        Node archive = archiveList.item(i);
        String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
        if (hostOs.equals(requiredHostOs)) {
          urlHolder.buildToolsFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          urlHolder.buildToolsUrl = URL_REPOSITORY_FOLDER + urlHolder.buildToolsFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
          break;
        }
      }

      // tools
      Node toolsItem = doc.getElementsByTagName("sdk:tool").item(0);
      archiveListItem = ((Element) toolsItem).getElementsByTagName("sdk:archives").item(0);
      archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
      for(int i = 0; i < archiveList.getLength(); i++) {
        Node archive = archiveList.item(i);
        String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
        if (hostOs.equals(requiredHostOs)) {
          urlHolder.toolsFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          urlHolder.toolsUrl = URL_REPOSITORY_FOLDER + urlHolder.toolsFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
          break;
        }
      }

      return urlHolder;
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals(PROPERTY_CHANGE_EVENT_TOTAL)) {
      progressBar.setIndeterminate(false);
      totalSize = (Integer) evt.getNewValue();
      progressBar.setMaximum(totalSize);
    } else if (evt.getPropertyName().equals(PROPERTY_CHANGE_EVENT_DOWNLOADED)) {
      downloadedTextArea.setText(humanReadableByteCount((Integer) evt.getNewValue(), true)
          + " / " + humanReadableByteCount(totalSize, true));
      progressBar.setValue((Integer) evt.getNewValue());
    }
  }

  // http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
  public static String humanReadableByteCount(long bytes, boolean si) {
    int unit = si ? 1000 : 1024;
    if (bytes < unit) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  public SDKDownloader(AndroidMode androidMode) {
    super("Android SDK downloading...");

    this.androidMode = androidMode;

    createLayout();
  }

  public void startDownload() {
    SDKDownloadTask downloadTask = new SDKDownloadTask();
    downloadTask.addPropertyChangeListener(this);
    downloadTask.execute();
  }

  private void createLayout() {
    Container outer = getContentPane();
    outer.removeAll();

    Box pain = Box.createVerticalBox();
    pain.setBorder(new EmptyBorder(13, 13, 13, 13));
    outer.add(pain);

    String labelText =
        "Downloading Android SDK...";
    JLabel textarea = new JLabel(labelText);
    textarea.setAlignmentX(LEFT_ALIGNMENT);
    pain.add(textarea);

    progressBar = new JProgressBar(0, 100);
    progressBar.setValue(0);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    progressBar.setBorder(new EmptyBorder(10, 10, 10, 10) );
    pain.add(progressBar);

    downloadedTextArea = new JLabel("");
    downloadedTextArea.setAlignmentX(LEFT_ALIGNMENT);
    pain.add(downloadedTextArea);

    // buttons
    JPanel buttons = new JPanel();
//    buttons.setPreferredSize(new Dimension(400, 35));
//    JPanel buttons = new JPanel() {
//      public Dimension getPreferredSize() {
//        return new Dimension(400, 35);
//      }
//      public Dimension getMinimumSize() {
//        return new Dimension(400, 35);
//      }
//      public Dimension getMaximumSize() {
//        return new Dimension(400, 35);
//      }
//    };

//    Box buttons = Box.createHorizontalBox();
    buttons.setAlignmentX(LEFT_ALIGNMENT);
    JButton cancelButton = new JButton("Cancel download");
    Dimension dim = new Dimension(Toolkit.BUTTON_WIDTH*2,
        cancelButton.getPreferredSize().height);

    cancelButton.setPreferredSize(dim);
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
      }
    });
    cancelButton.setEnabled(true);

    buttons.add(cancelButton);
//    buttons.setMaximumSize(new Dimension(300, buttons.getPreferredSize().height));
    pain.add(buttons);

    JRootPane root = getRootPane();
    root.setDefaultButton(cancelButton);
    ActionListener disposer = new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        setVisible(false);
      }
    };
    Toolkit.registerWindowCloseKeys(root, disposer);
    Toolkit.setIcon(this);

    pack();

    /*
    Dimension screen = Toolkit.getScreenSize();
    Dimension windowSize = getSize();
    setLocation((screen.width - windowSize.width) / 2,
        (screen.height - windowSize.height) / 2);
     */
    setLocationRelativeTo(null);

    setVisible(true);
    setAlwaysOnTop(true);
  }


  static void extractFolder(File file, File newPath) throws IOException {
    int BUFFER = 2048;
    zip = new ZipFile(file);
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {
      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();
      String currentEntry = entry.getName();
      File destFile = new File(newPath, currentEntry);
      //destFile = new File(newPath, destFile.getName());
      File destinationParent = destFile.getParentFile();

      // create the parent directory structure if needed
      destinationParent.mkdirs();

      if (!entry.isDirectory()) {
        BufferedInputStream is = new BufferedInputStream(zip
            .getInputStream(entry));
        int currentByte;
        // establish buffer for writing file
        byte data[] = new byte[BUFFER];

        // write the current file to disk
        FileOutputStream fos = new FileOutputStream(destFile);
        BufferedOutputStream dest = new BufferedOutputStream(fos,
            BUFFER);

        // read and write until last byte is encountered
        while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
          dest.write(data, 0, currentByte);
        }
        dest.flush();
        dest.close();
        is.close();
      }
    }
  }
}