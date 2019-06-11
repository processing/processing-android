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
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.SAXException;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.ui.Toolkit;

import javax.swing.*;
import javax.swing.border.Border;
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
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;

import static java.awt.GridBagConstraints.NORTHWEST;
import static processing.mode.android.AndroidBuild.TARGET_SDK;

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

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private SDKDownloadTask downloadTask;

  private Frame editor;
  private AndroidSDK sdk;
  private boolean cancelled;
  
  private int totalSize = 0;  

  class SDKUrlHolder {
    public String platformToolsVersion, buildToolsVersion,platformVersion,toolsVersion,emulatorVersion ;
    public String platformToolsUrl, buildToolsUrl, platformUrl, toolsUrl, emulatorUrl;
    public String platformToolsFilename, buildToolsFilename, platformFilename, toolsFilename, emulatorFilename;
//    public String supportRepoUrl, googleRepoUrl;
//    public String supportRepoFilename, googleRepoFilename;
    public String usbDriverUrl;
    public String usbDriverFilename;
    public String haxmFilename, haxmUrl, haxmVersion;
    public int totalSize = 0;
  }
  
  class SDKDownloadTask extends SwingWorker<Object, Object> {

    SDKUrlHolder urlHolder;
    File downloadFolder;
    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    public SDKDownloadTask(){}

    public SDKDownloadTask(SDKUrlHolder urlHolder,File sdkFolder){
      this.urlHolder = urlHolder;
      this.downloadFolder = sdkFolder;
    }

    @Override
    protected Object doInBackground() throws Exception {
      File sketchbookFolder = downloadFolder;
      File androidFolder = new File(sketchbookFolder, "android");
      if (!androidFolder.exists()) androidFolder.mkdir();
      
      File sdkFolder = AndroidUtil.createSubFolder(androidFolder, "sdk");
            
      // creating sdk folders
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
//    File androidRepoFolder = new File(extrasFolder, "android");
//    if (!androidRepoFolder.exists()) androidRepoFolder.mkdir();  
      File haxmFolder = new File(extrasFolder, "intel/HAXM");
      if (!haxmFolder.exists()) haxmFolder.mkdirs();      
      
      // creating temp folder for downloaded zip packages
      File tempFolder = new File(androidFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        SDKUrlHolder downloadUrls = this.urlHolder;
        firePropertyChange(AndroidMode.getTextString("download_property.change_event_total"), 0, downloadUrls.totalSize);

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
//        File downloadedGoogleRepo = new File(tempFolder, downloadUrls.googleRepoFilename);
//        downloadAndUnpack(downloadUrls.googleRepoUrl, downloadedGoogleRepo, googleRepoFolder, false);

        // android repository
//        File downloadedSupportRepo = new File(tempFolder, downloadUrls.supportRepoFilename);
//        downloadAndUnpack(downloadUrls.supportRepoUrl, downloadedSupportRepo, androidRepoFolder, false);
      
        // usb driver
        if (Platform.isWindows()) {
          File downloadedFolder = new File(tempFolder, downloadUrls.usbDriverFilename);
          downloadAndUnpack(downloadUrls.usbDriverUrl, downloadedFolder, googleRepoFolder, false);
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
        renameFolder(platformsFolder, "android-" + downloadUrls.platformVersion, actualName);
        actualName = buildToolsFolder.listFiles()[0].getName();
        renameFolder(buildToolsFolder, downloadUrls.buildToolsVersion, actualName);
        
        // Done, let's set the environment and load the new SDK!
        Platform.setenv("ANDROID_SDK", sdkFolder.getAbsolutePath());
        Preferences.set("android.sdk.path", sdkFolder.getAbsolutePath());
        Preferences.set("android.sdk.target",downloadUrls.platformVersion);
        sdk = AndroidSDK.load(false, null);
      } catch (IOException e) {
        // TODO Handle exceptions here somehow (ie show error message)
        // and handle at least mkdir() results (above)
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
      // platform
      expr = xpath.compile("//remotePackage[starts-with(@path, \"platforms;\")]"); //Search for all Platforms
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      if (remotePackages != null) {
        ArrayList<String> recentsArray = getRecentVersion(remotePackages,1);
        NodeList childNodes = remotePackages.item(Integer.parseInt(recentsArray.get(0))).getChildNodes();

        ArrayList<String> urlData = parseURL(childNodes,false,requiredHostOs);

        urlHolder.platformVersion = recentsArray.get(1);
        urlHolder.platformFilename = urlData.get(0);
        urlHolder.platformUrl = REPOSITORY_URL + urlHolder.platformFilename;
        urlHolder.totalSize += Integer.parseInt(urlData.get(1));
      } else {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_platform_files"));
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
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_platform_tools"));
      }

      // -----------------------------------------------------------------------
      // build-tools
      expr = xpath.compile("//remotePackage[starts-with(@path, \"build-tools;\")]");
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      found = false;
      if (remotePackages != null) {
        ArrayList<String> recentsArray = getRecentVersion(remotePackages,2);
        NodeList childNode = remotePackages.item(Integer.parseInt(recentsArray.get(0))).getChildNodes();
        urlHolder.buildToolsVersion = recentsArray.get(1);

        try {
          ArrayList<String> urlData = parseURL(childNode, true, requiredHostOs);
          urlHolder.buildToolsFilename = urlData.get(0);
          urlHolder.buildToolsUrl = REPOSITORY_URL + urlHolder.buildToolsFilename;
          urlHolder.totalSize += Integer.parseInt(urlData.get(1));
          found = true;
        }
        catch (Error e){
          e.printStackTrace();
        }
      }
      if (!found) {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_build_tools"));
      }
      
      // -----------------------------------------------------------------------
      // tools
      expr = xpath.compile("//remotePackage[@path=\"tools\"]"); //Matches two items according to xml file
      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
      found = false;
      if (remotePackages != null) {
        ArrayList<String> recentsArray = getRecentVersion(remotePackages,2);

        urlHolder.toolsVersion = recentsArray.get(1);

        NodeList childNodes = remotePackages.item(Integer.parseInt(recentsArray.get(0))).getChildNodes(); //Second item is the latest tools for now
        urlHolder.buildToolsVersion = recentsArray.get(1);

        try {
          ArrayList<String> urlData = parseURL(childNodes, true, requiredHostOs);
          urlHolder.toolsFilename = urlData.get(0);
          urlHolder.toolsUrl = REPOSITORY_URL + urlHolder.buildToolsFilename;
          urlHolder.totalSize += Integer.parseInt(urlData.get(1));
          found = true;
        }
        catch (Error e){
          e.printStackTrace();
        }
      } 
      if (!found) {
        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_tools"));
      }

      // -----------------------------------------------------------------------
      // emulator
      //NOTE: Emulator Download moved from SDK Download,
      //Will be done along with Emulator Image installation
//      expr = xpath.compile("//remotePackage[@path=\"emulator\"]"); //Matches two items according to xml file
//      remotePackages = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
//      found = false;
//      if (remotePackages != null) {
//        ArrayList<String> recentsArray = getRecentVersion(remotePackages,2);
//
//        urlHolder.emulatorVersion = recentsArray.get(1);
//
//        NodeList childNodes = remotePackages.item(Integer.parseInt(recentsArray.get(0))).getChildNodes();
//
//        urlHolder.buildToolsVersion = recentsArray.get(1);
//
//        try {
//          ArrayList<String> urlData = parseURL(childNodes, true, requiredHostOs);
//          urlHolder.emulatorFilename = urlData.get(0);
//          urlHolder.emulatorUrl = REPOSITORY_URL + urlHolder.buildToolsFilename;
//          urlHolder.totalSize += Integer.parseInt(urlData.get(1));
//          found = true;
//        }
//        catch (Error e){
//          e.printStackTrace();
//        }
//      }
//      if (!found) {
//        throw new IOException(AndroidMode.getTextString("sdk_downloader.error_cannot_find_emulator"));
//      }
    }
  }

  class QueryUrl extends SwingWorker<Object,Object>{
    SDKUrlHolder downloadUrls;

    QueryUrl(SDKUrlHolder downloadUrls){
      this.downloadUrls = downloadUrls;
    }


    @Override
    protected Object doInBackground() throws Exception {
      downloadTask = new SDKDownloadTask();
      String repositoryUrl = REPOSITORY_URL + REPOSITORY_LIST;
      String addonUrl = REPOSITORY_URL + ADDON_LIST;
      String haxmUrl = HAXM_URL + ADDON_LIST;
      try {
        downloadTask.getMainDownloadUrls(downloadUrls, repositoryUrl, Platform.getName());
        getExtrasDownloadUrls(downloadUrls, addonUrl, Platform.getName());
        getHaxmDownloadUrl(downloadUrls, haxmUrl, Platform.getName());
      } catch (IOException e) {
        e.printStackTrace();
      } catch (XPathException e) {
        e.printStackTrace();
      } catch (ParserConfigurationException e) {
        e.printStackTrace();
      } catch (SAXException e) {
        e.printStackTrace();
      }
      return null;
    }

    @Override
    protected void done() {
      super.done();
      createInitLayout(downloadUrls);
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
      ArrayList<String> haxmDetailsArray = getRecentVersion(remotePackages,2);
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

        urlHolder.haxmVersion = haxmDetailsArray.get(1);
        urlHolder.haxmFilename = url.item(0).getTextContent();
        urlHolder.haxmUrl = HAXM_URL + urlHolder.haxmFilename;
        urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
        break;
      }
    }
  }

  private void parseAndSet(SDKUrlHolder urlHolder, NodeList remotePackages, String requiredHostOs, int packageN) {
    NodeList childNodes = remotePackages.item(0).getChildNodes();
    ArrayList<String> recentsArray = null;
    if(packageN == PLATFORM_TOOLS) { //if usb driver, the repository structure is different
      recentsArray = getRecentVersion(remotePackages,2);
      childNodes = remotePackages.item(Integer.parseInt(recentsArray.get(0))).getChildNodes();
    }

    ArrayList<String> urlData;

    switch (packageN) {
      case PLATFORM_TOOLS:
        urlData = parseURL(childNodes,true,requiredHostOs);
        urlHolder.platformToolsVersion = recentsArray.get(1);
        urlHolder.platformToolsFilename = urlData.get(0);
        urlHolder.platformToolsUrl = REPOSITORY_URL + urlHolder.platformToolsFilename;
        urlHolder.totalSize += Integer.parseInt(urlData.get(1));
        break;
//       case ANDROID_REPO:
//         urlHolder.supportRepoFilename = url.item(0).getTextContent();
//         urlHolder.supportRepoUrl = REPOSITORY_URL + urlHolder.supportRepoFilename;
//         urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
//         break;
//       case GOOGLE_REPO:
//          urlHolder.googleRepoFilename = url.item(0).getTextContent();
//          urlHolder.googleRepoUrl = REPOSITORY_URL + urlHolder.googleRepoFilename;
//          urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
//          break;
      case USB_DRIVER:
        urlData = parseURL(childNodes,false,requiredHostOs);
        urlHolder.usbDriverFilename = urlData.get(0);
        urlHolder.usbDriverUrl = REPOSITORY_URL + urlHolder.usbDriverFilename;
        urlHolder.totalSize += Integer.parseInt(urlData.get(1));
        break;
    }
  }


  //check stability of all listed versions and return the most recent one
  private ArrayList<String> getRecentVersion(NodeList remotePackages,int packageType){
    ArrayList versionList = new ArrayList();
    ArrayList<String> recentsArray = new ArrayList<String>();

    for(int i=0;i<remotePackages.getLength();i++){
      NodeList childNodes = remotePackages.item(i).getChildNodes();
      NodeList channel = ((Element) childNodes).getElementsByTagName("channelRef");

      if(!(channel.item(0).getAttributes().getNamedItem("ref").getTextContent().equals("channel-0"))) {
        continue;
      }
      switch (packageType){
        case 1://platforms
          NodeList typeDetails = ((Element) childNodes).getElementsByTagName("type-details");
          NodeList apiLevel = ((Element) typeDetails.item(0)).getElementsByTagName("api-level");
          versionList.add(Integer.parseInt(apiLevel.item(0).getTextContent()));
          break;

        case 2 ://platform-tools , tools, buildTools, Emulator
          NodeList revision = ((Element) childNodes).getElementsByTagName("revision");
          int majorVersion = Integer.parseInt(((Element) revision.item(0).getChildNodes()).getElementsByTagName("major").item(0).getTextContent()) * 100;
          int minorVersion = Integer.parseInt(((Element) revision.item(0).getChildNodes()).getElementsByTagName("minor").item(0).getTextContent()) * 10;
          int microVersion = Integer.parseInt(((Element) revision.item(0).getChildNodes()).getElementsByTagName("micro").item(0).getTextContent()) * 1;
          versionList.add(majorVersion+minorVersion+microVersion);
          break;
      }
    }
    recentsArray.add(versionList.indexOf(Collections.max(versionList))+"");
    int version = (int) Collections.max(versionList);
    if(packageType!=1){
      recentsArray.add(version/100 + "." + (version/10)%10 + "." + (version%10));
    } else{
      recentsArray.add(version+"");
    }
    return recentsArray;
  }

  private ArrayList<String> parseURL(NodeList childNodes,boolean checkPlatform,String requiredHostOs){

    ArrayList<String> parseURLArray = new ArrayList<String>();
    NodeList archives = ((Element) childNodes).getElementsByTagName("archive");

    for (int j = 0; j < archives.getLength(); ++j) {
      NodeList archive = archives.item(j).getChildNodes();
      NodeList complete = ((Element) archive).getElementsByTagName("complete");

      NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
      NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

      parseURLArray.add(url.item(0).getTextContent());
      parseURLArray.add(size.item(0).getTextContent());

      if (checkPlatform) {
        NodeList os = ((Element) archive).getElementsByTagName("host-os");
        if (os.item(0).getTextContent().equals(requiredHostOs)) {
          parseURLArray.add(os.item(0).getTextContent());
          break;
        }
      }
    }
    return parseURLArray;
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
    SDKUrlHolder downloadUrls = new SDKUrlHolder();
    QueryUrl queryTask = new QueryUrl(downloadUrls);
    queryTask.execute();

    //Base UX----------------------------------
    add(new JLabel("Querying...",SwingConstants.CENTER));
    setSize(150,100);
    setLocationRelativeTo(editor);
    setAlwaysOnTop(true);
    setVisible(true);
  }
  
  public void run(SDKUrlHolder downloadUrls, File sdkFolder) {
    cancelled = false;
    downloadTask = new SDKDownloadTask(downloadUrls,sdkFolder);
    downloadTask.addPropertyChangeListener(this);
    downloadTask.execute();
  }
  
  public boolean cancelled() {
    return cancelled;
  }
  
  public AndroidSDK getSDK() {
    return sdk;
  }

  private void addPackage(GridBagConstraints gc, int gridy, JPanel packagesPanel,
                          String packageName, String packageVersion){
    gc.gridx = 0; gc.gridy = gridy;
    gc.weightx = 0.5;
    JLabel packageLabel = new JLabel(packageName);
    packagesPanel.add(packageLabel,gc);
    JLabel versionLabel = new JLabel(packageVersion);
    gc.gridx = 1; gc.gridy = gridy;
    gc.weightx = 1;
    packagesPanel.add(versionLabel,gc);
  }

  private void createInitLayout(SDKUrlHolder downloadUrls) {
    Container outer = getContentPane();
    outer.removeAll();
    final SDKUrlHolder urlHolder = downloadUrls;

    outer.setLayout(new BorderLayout());

    JPanel mainPanel = new JPanel();
    mainPanel.setPreferredSize(Toolkit.zoom(250,225));
    outer.add(mainPanel,BorderLayout.EAST);

    //List Packages to be downloaded here:
    JPanel packagesPanel = new JPanel();
    packagesPanel.setPreferredSize(Toolkit.zoom(250,125));
    packagesPanel.setAlignmentX(RIGHT_ALIGNMENT);
    Border border = BorderFactory.createLineBorder(Color.black,1,true);
    packagesPanel.setBorder(BorderFactory.createTitledBorder(border,"Packages to download: "));
    packagesPanel.setLayout(new GridBagLayout());

    mainPanel.add(packagesPanel);

    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = NORTHWEST;
    gc.weighty = 0.5;

    addPackage(gc,0,packagesPanel,"SDK Platform: ",downloadUrls.platformVersion);
    addPackage(gc,1,packagesPanel,"SDK PlatformTools: ",downloadUrls.platformToolsVersion);
    addPackage(gc,2,packagesPanel,"Android Build Tools: ",downloadUrls.buildToolsVersion);
    addPackage(gc,3,packagesPanel,"Android Tools: ",downloadUrls.toolsVersion);
    //faddPackage(gc,4,packagesPanel,"Android Emulator: ",downloadUrls.emulatorVersion);
    if(Platform.getName()!="linux") addPackage(gc,5,packagesPanel,"Android Build Tools: ",downloadUrls.haxmVersion);

    //SDK_Path selection Panel
    JPanel downloadPathPanel = new JPanel();
    downloadPathPanel.setLayout(new BorderLayout());
    JLabel pathLabel = new JLabel("Installation Path: ");
    downloadPathPanel.add(pathLabel,BorderLayout.NORTH);
    final JLabel locationLabel = new JLabel(processing.app.Base.getSketchbookFolder().getAbsolutePath());
    downloadPathPanel.add(locationLabel,BorderLayout.WEST);
    JButton selectPathButton = new JButton("Change");
    Dimension dim = new Dimension(Toolkit.getButtonWidth(),
            Toolkit.zoom(selectPathButton.getPreferredSize().height));
    selectPathButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (Platform.isMacOS()){
          FileDialog fd = new FileDialog(editor,"Select Download Location", FileDialog.LOAD);
          fd.setDirectory(processing.app.Base.getSketchbookFolder().getAbsolutePath());
          System.setProperty("apple.awt.fileDialogForDirectories", "true");
          fd.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
          fd.setVisible(true);
          System.setProperty("apple.awt.fileDialogForDirectories", "false");
        } else {
          JFileChooser fc = new JFileChooser();
          fc.setDialogTitle("Select Download Location");
          fc.setCurrentDirectory(processing.app.Base.getSketchbookFolder());
          fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          fc.showOpenDialog(SDKDownloader.super.rootPane); //To put it on top of the modalDialog
          if (fc.getSelectedFile() != null) locationLabel.setText(fc.getSelectedFile().getAbsolutePath());
        }
      }
    });
    downloadPathPanel.add(selectPathButton,BorderLayout.EAST);
    mainPanel.add(downloadPathPanel);

    //Buttons Panel on the bottom
    JPanel buttons = new JPanel();
    buttons.setAlignmentX(LEFT_ALIGNMENT);
    JButton continueButton = new JButton("Continue");
    continueButton.setPreferredSize(dim);
    continueButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        File sdkFolder = new File(locationLabel.getText());
        run(urlHolder,sdkFolder);
        createLayout();
      }
    });
    buttons.add(continueButton);

    JButton cancelButton = new JButton("Cancel");
    cancelButton.setPreferredSize(dim);
    buttons.add(cancelButton);
    cancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });
    mainPanel.add(buttons);

    //The side panel with image Icon
    JPanel sidePanel = new JPanel(new BorderLayout());
    sidePanel.setPreferredSize(Toolkit.zoom(100,0));
    sidePanel.setBackground(Color.white);
    outer.add(sidePanel,BorderLayout.WEST);
    sidePanel.setAlignmentY(CENTER_ALIGNMENT);
    JLabel logo = new JLabel(Toolkit.getLibIcon("/icons/pde-64.png"));
    sidePanel.add(logo,BorderLayout.CENTER);

    pack();
    setLocationRelativeTo(editor);
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