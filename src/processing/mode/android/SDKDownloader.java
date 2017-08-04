/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2014-17 The Processing Foundation
 
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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

@SuppressWarnings("serial")
public class SDKDownloader extends JDialog implements PropertyChangeListener {
  final static private int BOX_BORDER = Toolkit.zoom(13);
  final static private int BAR_BORDER = Toolkit.zoom(10);
  final static private int BAR_WIDTH = Toolkit.zoom(300); 
  final static private int BAR_HEIGHT = Toolkit.zoom(30);
  final static private int GAP = Toolkit.zoom(13);
  
  private static final int PLATFORM_TOOLS = 2;
  private static final int ANDROID_REPO = 4;
  private static final int GOOGLE_REPO = 5;
  private static final int USB_DRIVER = 6;

  private static final String REPOSITORY_URL = "https://dl.google.com/android/repository/";
  private static final String HAXM_URL = "https://dl.google.com/android/repository/extras/intel/";
  private static final String REPOSITORY_LIST = "repository2-1.xml";
  private static final String ADDON_LIST = "addon2-1.xml";
  
  private static final String PROPERTY_CHANGE_EVENT_TOTAL = "total";
  private static final String PROPERTY_CHANGE_EVENT_DOWNLOADED = "downloaded";

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private SDKDownloadTask downloadTask;
  
  private Frame editor;
  private AndroidSDK sdk;
  private boolean cancelled;
  
  private int totalSize = 0;  

  class SDKUrlHolder {
    public String platformVersion, buildToolsVersion;
    public String platformToolsUrl, buildToolsUrl, platformUrl, toolsUrl, emulatorUrl;
    public String platformToolsFilename, buildToolsFilename, platformFilename, toolsFilename, emulatorFilename;
    public String supportRepoUrl, googleRepoUrl, usbDriverUrl;
    public String supportRepoFilename, googleRepoFilename, usbDriverFilename;
    public String haxmFilename, haxmUrl;
    public int totalSize = 0;
  }
  
  class SDKDownloadTask extends SwingWorker<Object, Object> {

    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    @Override
    protected Object doInBackground() throws Exception {
      File sketchbookFolder = processing.app.Base.getSketchbookFolder();
      File androidFolder = new File(sketchbookFolder, "android");
      if (!androidFolder.exists()) androidFolder.mkdir();
            
      // creating sdk folders
      File sdkFolder = new File(androidFolder, "sdk");
      if (!sdkFolder.exists()) sdkFolder.mkdirs();
      File platformsFolder = new File(sdkFolder, "platforms");
      if (!platformsFolder.exists()) platformsFolder.mkdir();
      File buildToolsFolder = new File(sdkFolder, "build-tools");
      if (!buildToolsFolder.exists()) buildToolsFolder.mkdir();
      File emulatorFolder = new File(sdkFolder, "emulator");
      if (!emulatorFolder.exists()) emulatorFolder.mkdir();
      File extrasFolder = new File(sdkFolder, "extras");
      if (!extrasFolder.exists()) extrasFolder.mkdir();
      File googleRepoFolder = new File(extrasFolder, "google");
      if (!googleRepoFolder.exists()) googleRepoFolder.mkdir();
      File googleDriverFolder = new File(googleRepoFolder, "usb_driver");
      if (!googleDriverFolder.exists()) googleDriverFolder.mkdir();      
      File haxmFolder = new File(extrasFolder, "intel/HAXM");
      if (!haxmFolder.exists()) haxmFolder.mkdirs();      
      File androidRepoFolder = new File(extrasFolder, "android");
      if (!androidRepoFolder.exists()) androidRepoFolder.mkdir();      
      
      // creating temp folder for downloaded zip packages
      File tempFolder = new File(androidFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        SDKUrlHolder downloadUrls = new SDKUrlHolder();
        String repositoryUrl = REPOSITORY_URL + REPOSITORY_LIST;
        String addonUrl = REPOSITORY_URL + ADDON_LIST;
        String haxmUrl = HAXM_URL + ADDON_LIST;
        getMainDownloadUrls(downloadUrls, repositoryUrl, Platform.getName());
        getExtrasDownloadUrls(downloadUrls, addonUrl, Platform.getName());        
        getHaxmDownloadUrl(downloadUrls, haxmUrl, Platform.getName());
        firePropertyChange(PROPERTY_CHANGE_EVENT_TOTAL, 0, downloadUrls.totalSize);

        // tools
        File downloadedTools = new File(tempFolder, downloadUrls.toolsFilename);
        downloadAndUnpack(downloadUrls.toolsUrl, downloadedTools, sdkFolder, true);

        // platform-tools
        File downloadedPlatformTools = new File(tempFolder, downloadUrls.platformToolsFilename);
        downloadAndUnpack(downloadUrls.platformToolsUrl, downloadedPlatformTools, sdkFolder, true);

        // build-tools
        File downloadedBuildTools = new File(tempFolder, downloadUrls.buildToolsFilename);
        downloadAndUnpack(downloadUrls.buildToolsUrl, downloadedBuildTools, buildToolsFolder, true);

        // platform
        File downloadedPlatform = new File(tempFolder, downloadUrls.platformFilename);
        downloadAndUnpack(downloadUrls.platformUrl, downloadedPlatform, platformsFolder, false);

        // emulator, unpacks directly to sdk folder 
        File downloadedEmulator = new File(tempFolder, downloadUrls.emulatorFilename);
        downloadAndUnpack(downloadUrls.emulatorUrl, downloadedEmulator, sdkFolder, true);
        
        // google repository
        File downloadedGoogleRepo = new File(tempFolder, downloadUrls.googleRepoFilename);
        downloadAndUnpack(downloadUrls.googleRepoUrl, downloadedGoogleRepo, googleRepoFolder, false);

        // android repository
        File downloadedSupportRepo = new File(tempFolder, downloadUrls.supportRepoFilename);
        downloadAndUnpack(downloadUrls.supportRepoUrl, downloadedSupportRepo, androidRepoFolder, false);
      
        // usb driver
        if (Platform.isWindows()) {
          File downloadedFolder = new File(tempFolder, downloadUrls.usbDriverFilename);
          downloadAndUnpack(downloadUrls.usbDriverUrl, downloadedFolder, googleDriverFolder, false);
        }

        // HAXM
        if (!Platform.isLinux()) {
          File downloadedFolder = new File(tempFolder, downloadUrls.haxmFilename);
          downloadAndUnpack(downloadUrls.haxmUrl, downloadedFolder, haxmFolder, true);
        }

        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sdkFolder.getAbsolutePath());
        }

        for (File f: tempFolder.listFiles()) f.delete();    
        tempFolder.delete();
        
         // Normalize built-tools and platform folders to android-<API LEVEL>
        String actualName = platformsFolder.listFiles()[0].getName();
        renameFolder(platformsFolder, "android-" + AndroidBuild.TARGET_SDK, actualName);
        actualName = buildToolsFolder.listFiles()[0].getName();
        renameFolder(buildToolsFolder, downloadUrls.buildToolsVersion, actualName);
        
        // Done, let's set the environment and load the new SDK!
        Platform.setenv("ANDROID_SDK", sdkFolder.getAbsolutePath());
        Preferences.set("android.sdk.path", sdkFolder.getAbsolutePath());        
        sdk = AndroidSDK.load();        
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
      dispose();
    }

    private void downloadAndUnpack(String urlString, File saveTo,
                                   File unpackTo, boolean setExec) throws IOException {
      URL url = null;
      try {
        url = new URL(urlString);
      } catch (MalformedURLException e) {
    	  //This is expected for API level 14 and more
    	  try {
    		  url = new URL(REPOSITORY_URL + urlString);
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

      AndroidUtil.extractFolder(saveTo, unpackTo, setExec);
    }

    private void getMainDownloadUrls(SDKUrlHolder urlHolder, 
        String repositoryUrl, String requiredHostOs) 
        throws ParserConfigurationException, IOException, SAXException, XPathException {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new URL(repositoryUrl).openStream());

      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();
      XPathExpression expr;
      NodeList remotePackages;
      
      // -----------------------------------------------------------------------
      // platform
      expr = xpath.compile("//remotePackage[starts-with(@path, \"platforms;\")" +
              "and contains(@path, '" + AndroidBuild.TARGET_SDK + "')]"); // Skip latest platform; download only the targeted
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        NodeList childNodes = remotePackages.item(0).getChildNodes();

        NodeList typeDetails = ((Element) childNodes).getElementsByTagName("type-details");
        NodeList apiLevel = ((Element) typeDetails.item(0)).getElementsByTagName("api-level");
        urlHolder.platformVersion = apiLevel.item(0).getTextContent();

        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");
        NodeList archive = archives.item(0).getChildNodes();
        NodeList complete = ((Element) archive).getElementsByTagName("complete");

        NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
        NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

        urlHolder.platformFilename = url.item(0).getTextContent();
        urlHolder.platformUrl = REPOSITORY_URL + urlHolder.platformFilename;
        urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
      } else {
        throw new IOException("Cannot find the platform files");
      }

      // Difference between platform tools, build tools, and SDK tools: 
      // http://stackoverflow.com/questions/19911762/what-is-android-sdk-build-tools-and-which-version-should-be-used
      // Always get the latest!
      
      // -----------------------------------------------------------------------
      // platform-tools
      expr = xpath.compile("//remotePackage[@path=\"platform-tools\"]");
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        parseAndSet(urlHolder, remotePackages, requiredHostOs, PLATFORM_TOOLS);
      } else {
        throw new IOException("Cannot find the platform-tools");
      }

      // -----------------------------------------------------------------------
      // build-tools
      expr = xpath.compile("//remotePackage[starts-with(@path, \"build-tools;\")]");
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        for(int buildTool=0; buildTool < remotePackages.getLength(); buildTool++) {
          NodeList childNodes = remotePackages.item(buildTool).getChildNodes();

          NodeList channel = ((Element) childNodes).getElementsByTagName("channelRef");
          if(!channel.item(0).getAttributes().item(0).getNodeValue().equals("channel-0"))
            continue; //Stable channel only, skip others

          NodeList revision = ((Element) childNodes).getElementsByTagName("revision");
          String major = (((Element) revision.item(0)).getElementsByTagName("major")).item(0).getTextContent();
          String minor = (((Element) revision.item(0)).getElementsByTagName("minor")).item(0).getTextContent();
          String micro = (((Element) revision.item(0)).getElementsByTagName("micro")).item(0).getTextContent();
          if(!major.equals(AndroidBuild.TARGET_SDK)) // Allows only the latest build tools for the target platform
            continue;
          urlHolder.buildToolsVersion = major + "." + minor + "." + micro;

          NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

          for (int j = 0; j < archives.getLength(); ++j) {
            NodeList archive = archives.item(j).getChildNodes();
            NodeList complete = ((Element) archive).getElementsByTagName("complete");

            NodeList os = ((Element) archive).getElementsByTagName("host-os");
            NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
            NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

            if (os.item(0).getTextContent().equals(requiredHostOs)) {
              urlHolder.buildToolsFilename = url.item(0).getTextContent();
              urlHolder.buildToolsUrl = REPOSITORY_URL + urlHolder.buildToolsFilename;
              urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
              break;
            }
          }
          break;
        }
      } else {
        throw new IOException("Cannot find the build-tools");
      }
      
      // -----------------------------------------------------------------------
      // tools
      expr = xpath.compile("//remotePackage[@path=\"tools\"]"); //Matches two items according to xml file
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        NodeList childNodes = remotePackages.item(1).getChildNodes(); //Second item is the latest tools for now
        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

        for (int i = 0; i < archives.getLength(); ++i) {
          NodeList archive = archives.item(i).getChildNodes();
          NodeList complete = ((Element) archive).getElementsByTagName("complete");

          NodeList os = ((Element) archive).getElementsByTagName("host-os");
          NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
          NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

          if (os.item(0).getTextContent().equals(requiredHostOs)) {
            urlHolder.toolsFilename =  url.item(0).getTextContent();
            urlHolder.toolsUrl = REPOSITORY_URL + urlHolder.toolsFilename;
            urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
            break;
          }
        }
      } else {
        throw new IOException("Cannot find the tools");
      }

      // -----------------------------------------------------------------------
      // emulator
      expr = xpath.compile("//remotePackage[@path=\"emulator\"]"); //Matches two items according to xml file
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        for(int i = 0; i < remotePackages.getLength(); ++i) {
          NodeList childNodes = remotePackages.item(i).getChildNodes();

          NodeList channel = ((Element) childNodes).getElementsByTagName("channelRef");
          if(!channel.item(0).getAttributes().item(0).getNodeValue().equals("channel-0"))
            continue; //Stable channel only, skip others

          NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

          for (int j = 0; j < archives.getLength(); ++j) {
            NodeList archive = archives.item(j).getChildNodes();
            NodeList complete = ((Element) archive).getElementsByTagName("complete");

            NodeList os = ((Element) archive).getElementsByTagName("host-os");
            NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
            NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

            if (os.item(0).getTextContent().equals(requiredHostOs)) {
              urlHolder.emulatorFilename = url.item(0).getTextContent();
              urlHolder.emulatorUrl = REPOSITORY_URL + urlHolder.emulatorFilename;
              urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
              break;
            }
          }
          break;
        }
      } else {
        throw new IOException("Cannot find the emulator");
      }
    }
  }

  private void getExtrasDownloadUrls(SDKUrlHolder urlHolder, 
      String repositoryUrl, String requiredHostOs) 
      throws ParserConfigurationException, IOException, SAXException, XPathException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(new URL(repositoryUrl).openStream());

    XPathFactory xPathfactory = XPathFactory.newInstance();
    XPath xpath = xPathfactory.newXPath();
    XPathExpression expr;
    NodeList remotePackages;

    // ---------------------------------------------------------------------
    // Android Support repository
    expr = xpath.compile("//remotePackage[@path=\"extras;android;m2repository\"]");
    remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    if (remotePackages != null) {
      parseAndSet(urlHolder, remotePackages, requiredHostOs, ANDROID_REPO);
    }

    // ---------------------------------------------------------------------
    // Google repository
    expr = xpath.compile("//remotePackage[@path=\"extras;google;m2repository\"]");
    remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    if (remotePackages != null) {
      parseAndSet(urlHolder, remotePackages, requiredHostOs, GOOGLE_REPO);
    }

    // ---------------------------------------------------------------------
    // USB driver
    expr = xpath.compile("//remotePackage[@path=\"extras;google;usb_driver\"]");
    remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    if (remotePackages != null && Platform.isWindows()) {
      parseAndSet(urlHolder, remotePackages, requiredHostOs, USB_DRIVER);
    }
  }

  private void getHaxmDownloadUrl(SDKUrlHolder urlHolder,
                                     String repositoryUrl, String requiredHostOs)
          throws ParserConfigurationException, IOException, SAXException, XPathException {
    if (requiredHostOs.equals("linux"))
      return;

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(new URL(repositoryUrl).openStream());

    XPathFactory xPathfactory = XPathFactory.newInstance();
    XPath xpath = xPathfactory.newXPath();
    XPathExpression expr;
    NodeList remotePackages;

    expr = xpath.compile("//remotePackage[@path=\"extras;intel;Hardware_Accelerated_Execution_Manager\"]");
    remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
    if (remotePackages != null) {
      for (int i=0; i < remotePackages.getLength(); ++i) {
        NodeList childNodes = remotePackages.item(i).getChildNodes();
        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

        NodeList archive = archives.item(0).getChildNodes();
        NodeList os = ((Element) archive).getElementsByTagName("host-os");

        if (!os.item(0).getTextContent().equals(requiredHostOs))
          continue;

        NodeList complete = ((Element) archive).getElementsByTagName("complete");
        NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
        NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

        urlHolder.haxmFilename = url.item(0).getTextContent();
        urlHolder.haxmUrl = HAXM_URL + urlHolder.haxmFilename;
        urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
        break;
      }
    }
  }

  private void parseAndSet(SDKUrlHolder urlHolder, NodeList remotePackages, String requiredHostOs, int packageN) {
    NodeList childNodes = remotePackages.item(0).getChildNodes();
    NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

    for (int i = 0; i < archives.getLength(); ++i) {
      NodeList archive = archives.item(i).getChildNodes();
      NodeList complete = ((Element) archive).getElementsByTagName("complete");

      NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
      NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

      switch (packageN) {
        case PLATFORM_TOOLS:
          NodeList os = ((Element) archive).getElementsByTagName("host-os");
          if (!os.item(0).getTextContent().equals(requiredHostOs))
            continue;
          urlHolder.platformToolsFilename = url.item(0).getTextContent();
          urlHolder.platformToolsUrl = REPOSITORY_URL + urlHolder.platformToolsFilename;
          urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
          break;
        case ANDROID_REPO:
          urlHolder.supportRepoFilename = url.item(0).getTextContent();
          urlHolder.supportRepoUrl = REPOSITORY_URL + urlHolder.supportRepoFilename;
          urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
          break;
        case GOOGLE_REPO:
          urlHolder.googleRepoFilename = url.item(0).getTextContent();
          urlHolder.googleRepoUrl = REPOSITORY_URL + urlHolder.googleRepoFilename;
          urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
          break;
        case USB_DRIVER:
          urlHolder.usbDriverFilename = url.item(0).getTextContent();
          urlHolder.usbDriverUrl = REPOSITORY_URL + urlHolder.usbDriverFilename;
          urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
          break;
      }
      break;
    }
  }
  
  private void renameFolder(File baseFolder, String expected, String actual) 
      throws IOException {
    File expectedPath = new File(baseFolder, expected);
    File actualPath = new File(baseFolder, actual);
    if (!expectedPath.exists()) {
      if (actualPath.exists()) {
        actualPath.renameTo(expectedPath);
      } else {
        throw new IOException("Error unpacking platform to " + 
            actualPath.getAbsolutePath());
      }
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

  public SDKDownloader(Frame editor) {
    super(editor, "SDK download", true);
    this.editor = editor;
    this.sdk = null;    
    createLayout();
  }
  
  public void run() {
    cancelled = false;
    downloadTask = new SDKDownloadTask();
    downloadTask.addPropertyChangeListener(this);
    downloadTask.execute();
    setAlwaysOnTop(true);
    setVisible(true);
  }
  
  public boolean cancelled() {
    return cancelled;
  }
  
  public AndroidSDK getSDK() {
    return sdk;
  }
  
  private void createLayout() {
    Container outer = getContentPane();
    outer.removeAll();

    Box vbox = Box.createVerticalBox();
    vbox.setBorder(new EmptyBorder(BOX_BORDER, BOX_BORDER, BOX_BORDER, BOX_BORDER));
    outer.add(vbox);

    String labelText = "Downloading Android SDK...";
    JLabel textarea = new JLabel(labelText);
    textarea.setAlignmentX(LEFT_ALIGNMENT);
    vbox.add(textarea);

    // Needed to put the progressBar inside this panel so we can set its size
    JPanel progressPanel = new JPanel(); 
    BoxLayout boxLayout = new BoxLayout(progressPanel, BoxLayout.Y_AXIS);
    progressPanel.setLayout(boxLayout);    
    progressBar = new JProgressBar(0, 100);
    progressBar.setPreferredSize(new Dimension(BAR_WIDTH, BAR_HEIGHT));
    progressBar.setValue(0);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    progressBar.setBorder(new EmptyBorder(BAR_BORDER, BAR_BORDER, BAR_BORDER, BAR_BORDER));
    progressPanel.add(progressBar);
    vbox.add(progressPanel);

    downloadedTextArea = new JLabel("0 / 0 MB");
    downloadedTextArea.setAlignmentX(LEFT_ALIGNMENT);
    vbox.add(downloadedTextArea);

    vbox.add(Box.createVerticalStrut(GAP));
    
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
    Dimension dim = new Dimension(Toolkit.getButtonWidth()*2,
                                  Toolkit.zoom(cancelButton.getPreferredSize().height));

    cancelButton.setPreferredSize(dim);
    cancelButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (downloadTask != null) {
          downloadTask.cancel(true);
        }
        setVisible(false);
        cancelled = true;
      }
    });
    cancelButton.setEnabled(true);

    buttons.add(cancelButton);
//    buttons.setMaximumSize(new Dimension(300, buttons.getPreferredSize().height));
    vbox.add(buttons);

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

    setResizable(false);
    setLocationRelativeTo(editor);
  }
}