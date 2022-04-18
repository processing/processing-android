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
  
  public static final boolean DOWNLOAD_EMU = false;

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private SDKDownloadTask downloadTask;
  
  private Frame editor;
  private AndroidSDK sdk;
  private boolean cancelled;
  
  private int totalSize = 0;  

  class SDKUrlHolder {
    public String platformVersion, buildToolsVersion;
    public String platformToolsUrl, buildToolsUrl, platformUrl, cmdlineToolsUrl, emulatorUrl;
    public String platformToolsFilename, buildToolsFilename, platformFilename, cmdlineToolsFilename, emulatorFilename;
    public String usbDriverUrl;
    public String usbDriverFilename;
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
      
      File sdkFolder = AndroidUtil.createSubFolder(androidFolder, "sdk");
            
      // creating sdk folders
      File platformsFolder = new File(sdkFolder, "platforms");
      if (!platformsFolder.exists()) platformsFolder.mkdir();
      File buildToolsFolder = new File(sdkFolder, "build-tools");
      if (!buildToolsFolder.exists()) buildToolsFolder.mkdir();
      File extrasFolder = new File(sdkFolder, "extras");
      if (!extrasFolder.exists()) extrasFolder.mkdir();
      File googleRepoFolder = new File(extrasFolder, "google");
      if (!googleRepoFolder.exists()) googleRepoFolder.mkdir();
      File haxmFolder = new File(extrasFolder, "intel/HAXM");
      if (!haxmFolder.exists()) haxmFolder.mkdirs();      

      if (DOWNLOAD_EMU) {
        File emulatorFolder = new File(sdkFolder, "emulator");
        if (!emulatorFolder.exists()) emulatorFolder.mkdir();        
      }
      
      // creating temp folder for downloaded zip packages
      File tempFolder = new File(androidFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        SDKUrlHolder downloadUrls = new SDKUrlHolder();
        String repositoryUrl = REPOSITORY_URL + REPOSITORY_LIST;
        String addonUrl = REPOSITORY_URL + ADDON_LIST;
        String haxmUrl = HAXM_URL + ADDON_LIST;

        String platformName = Platform.getName();
        System.out.println("PLATFORM NAME " + platformName);
        if (platformName.equals("macos")) {
          platformName = "macosx";
        }
        getMainDownloadUrls(downloadUrls, repositoryUrl, platformName);
        getExtrasDownloadUrls(downloadUrls, addonUrl, platformName);
        getHaxmDownloadUrl(downloadUrls, haxmUrl, platformName);
        firePropertyChange(AndroidMode.getTextString("download_property.change_event_total"), 0, downloadUrls.totalSize);

        // Command-line tools
        File downloadedCmdLineTools = new File(tempFolder, downloadUrls.cmdlineToolsFilename);
        downloadAndUnpack(downloadUrls.cmdlineToolsUrl, downloadedCmdLineTools, sdkFolder, true);
        File tmpFrom = new File(sdkFolder, "cmdline-tools");
        File tmpTo = new File(sdkFolder, "cmdline-tmp");
        AndroidUtil.moveDir(tmpFrom, tmpTo);
        File cmdlineToolsFolder = new File(sdkFolder, "cmdline-tools/latest");
        if (!cmdlineToolsFolder.exists()) cmdlineToolsFolder.mkdirs();        
        AndroidUtil.moveDir(tmpTo, cmdlineToolsFolder);

        // Platform tools
        File downloadedPlatformTools = new File(tempFolder, downloadUrls.platformToolsFilename);
        downloadAndUnpack(downloadUrls.platformToolsUrl, downloadedPlatformTools, sdkFolder, true);

        // Build tools
        File downloadedBuildTools = new File(tempFolder, downloadUrls.buildToolsFilename);
        downloadAndUnpack(downloadUrls.buildToolsUrl, downloadedBuildTools, buildToolsFolder, true);

        // Platform
        File downloadedPlatform = new File(tempFolder, downloadUrls.platformFilename);
        downloadAndUnpack(downloadUrls.platformUrl, downloadedPlatform, platformsFolder, false);
      
        // USB driver
        if (Platform.isWindows()) {
          File downloadedFolder = new File(tempFolder, downloadUrls.usbDriverFilename);
          downloadAndUnpack(downloadUrls.usbDriverUrl, downloadedFolder, googleRepoFolder, false);
        }

        // HAXM
        if (!Platform.isLinux()) {
          File downloadedFolder = new File(tempFolder, downloadUrls.haxmFilename);
          downloadAndUnpack(downloadUrls.haxmUrl, downloadedFolder, haxmFolder, true);
        }

        if (DOWNLOAD_EMU) {
          // Emulator, unpacks directly to sdk folder 
          File downloadedEmulator = new File(tempFolder, downloadUrls.emulatorFilename);
          downloadAndUnpack(downloadUrls.emulatorUrl, downloadedEmulator, sdkFolder, true);          
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
        sdk = AndroidSDK.load(false, null);        
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

        firePropertyChange(AndroidMode.getTextString("download_property.change_event_downloaded"), 0, downloadedSize);
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
      boolean found;
      
      // -----------------------------------------------------------------------
      // Platform
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
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_platform_files"));
      }

      // Difference between platform tools, build tools, and SDK (now command-line) tools: 
      // http://stackoverflow.com/questions/19911762/what-is-android-sdk-build-tools-and-which-version-should-be-used
      // Always get the latest!
      
      // -----------------------------------------------------------------------
      // Platform tools
      expr = xpath.compile("//remotePackage[@path=\"platform-tools\"]");
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        parseAndSet(urlHolder, remotePackages, requiredHostOs, PLATFORM_TOOLS);
      } else {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_platform_tools"));
      }

      // -----------------------------------------------------------------------
      // Build tools
      expr = xpath.compile("//remotePackage[starts-with(@path, \"build-tools;\")]");
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      found = false;
      if (remotePackages != null) {
        for (int buildTool = 0; buildTool < remotePackages.getLength(); buildTool++) {
          NodeList childNodes = remotePackages.item(buildTool).getChildNodes();

          NodeList channel = ((Element) childNodes).getElementsByTagName("channelRef");
          if (!channel.item(0).getAttributes().item(0).getNodeValue().equals("channel-0")) {
            continue; // Stable channel only, skip others
          }

          NodeList revision = ((Element) childNodes).getElementsByTagName("revision");
          String major = (((Element) revision.item(0)).getElementsByTagName("major")).item(0).getTextContent();
          String minor = (((Element) revision.item(0)).getElementsByTagName("minor")).item(0).getTextContent();
          String micro = (((Element) revision.item(0)).getElementsByTagName("micro")).item(0).getTextContent();
          if (!major.equals(AndroidBuild.TARGET_SDK))  {
            continue; // Allows only the latest build tools for the target platform
          }
            
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
              found = true;
              break;
            }
          }
          if (found) break;
        }
      } 
      if (!found) {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_build_tools"));
      }
      
      // -----------------------------------------------------------------------
      // Command-line tools
      expr = xpath.compile("//remotePackage[starts-with(@path, \"cmdline-tools;\")]");      
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      found = false;
      if (remotePackages != null) {
        for (int tool = 0; tool < remotePackages.getLength(); tool++) {
          NodeList childNodes = remotePackages.item(tool).getChildNodes();
          
          NodeList channel = ((Element) childNodes).getElementsByTagName("channelRef");
          if (!channel.item(0).getAttributes().item(0).getNodeValue().equals("channel-0")) {
            continue; // Stable channel only, skip others
          }
          
          NodeList archives = ((Element) childNodes).getElementsByTagName("archive");
          for (int i = 0; i < archives.getLength(); ++i) {
            NodeList archive = archives.item(i).getChildNodes();
            NodeList complete = ((Element) archive).getElementsByTagName("complete");

            NodeList os = ((Element) archive).getElementsByTagName("host-os");
            NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
            NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");
            
            if (os.item(0).getTextContent().equals(requiredHostOs)) {
              urlHolder.cmdlineToolsFilename =  url.item(0).getTextContent();
              urlHolder.cmdlineToolsUrl = REPOSITORY_URL + urlHolder.cmdlineToolsFilename;
              urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
              found = true;
              break;
            }            
          }
          if (found) break;          
        }
      } 
      if (!found) {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_tools"));
      }
      
      if (DOWNLOAD_EMU) {
        // -----------------------------------------------------------------------
        // Emulator
        expr = xpath.compile("//remotePackage[@path=\"emulator\"]");
        remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        found = false;
        if (remotePackages != null) {
          for (int i = 0; i < remotePackages.getLength(); ++i) {
            NodeList childNodes = remotePackages.item(i).getChildNodes();
            
            NodeList channel = ((Element) childNodes).getElementsByTagName("channelRef");
            if (!channel.item(0).getAttributes().item(0).getNodeValue().equals("channel-0")) {
              continue; //Stable channel only, skip others
            }

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
                found = true;
                break;
              }
            }
            if (found) break;
          }
        }         
      }
      if (!found) {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_emulator"));
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
      for (int i = 0; i < remotePackages.getLength(); ++i) {
        NodeList childNodes = remotePackages.item(i).getChildNodes();
        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

        NodeList archive = archives.item(0).getChildNodes();
        NodeList os = ((Element) archive).getElementsByTagName("host-os");

        if (!os.item(0).getTextContent().equals(requiredHostOs)) {
          continue;
        }

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
          if (!os.item(0).getTextContent().equals(requiredHostOs)) {
            continue;
          }            
          urlHolder.platformToolsFilename = url.item(0).getTextContent();
          urlHolder.platformToolsUrl = REPOSITORY_URL + urlHolder.platformToolsFilename;
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
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error.cannot_unpack_platform", actualPath.getAbsolutePath()));
      }
    }        
  }
  
  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals(AndroidMode.getTextString("download_property.change_event_total"))) {
      progressBar.setIndeterminate(false);
      totalSize = (Integer) evt.getNewValue();
      progressBar.setMaximum(totalSize);
    } else if (evt.getPropertyName().equals(AndroidMode.getTextString("download_property.change_event_downloaded"))) {
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
    super(editor, AndroidMode.getTextString("sdk_downloader.download_title"), true);
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

    String labelText = AndroidMode.getTextString("sdk_downloader.download_sdk_label");
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
    JButton cancelButton = new JButton(AndroidMode.getTextString("download_prompt.cancel"));
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