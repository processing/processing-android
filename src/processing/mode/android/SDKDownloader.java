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

package processing.mode.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import processing.app.Platform;
import processing.app.Preferences;
import processing.app.ui.Toolkit;
import processing.core.PApplet;

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

@SuppressWarnings("serial")
public class SDKDownloader extends JDialog implements PropertyChangeListener {
  // Version 25.3.1 of the SDK tools break the mode, since the android tool
  // no longer works:
  // https://code.google.com/p/android/issues/detail?id=235455
  // as well as removing the ant scripts.
  // https://code.google.com/p/android/issues/detail?id=235410
  // See release notes:
  // https://developer.android.com/studio/releases/sdk-tools.html  
  private static final String REPOSITORY_URL = "https://dl.google.com/android/repository/";
  private static final String REPOSITORY_LIST = "repository-12.xml";
  private static final String ADDON_LIST = "addon.xml";
  
  // The Android Support Repository does not seem to include the 
  // android-support-v4 jar file, even somewhere inside the aar packages, so 
  // downloading the latest support library package available.
  // More info on the Support Library and latest releases:
  // https://developer.android.com/topic/libraries/support-library/index.html
  // This probably needs to be fixed so the Support Repository is used moving 
  // forward.
  private static final String LEGACY_SUPPORT_LIBRARY = "support_r23.2.1.zip";
  private static final int SUPPORT_LIBRARY_SIZE = 10850402;
  
  private static final String PROPERTY_CHANGE_EVENT_TOTAL = "total";
  private static final String PROPERTY_CHANGE_EVENT_DOWNLOADED = "downloaded";

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private SDKDownloadTask downloadTask;
  
  private Frame editor;
  private AndroidMode mode;
  private AndroidSDK sdk;
  private boolean cancelled;
  
  private int totalSize = 0;  

  class SDKUrlHolder {
    public String platformVersion, buildToolsVersion;
    public String platformToolsUrl, buildToolsUrl, platformUrl, toolsUrl;
    public String platformToolsFilename, buildToolsFilename, platformFilename, toolsFilename;
    public String supportRepoUrl, googleRepoUrl, usbDriverUrl;
    public String supportRepoFilename, googleRepoFilename, usbDriverFilename;    
    public int totalSize = 0;
  }
  
  class SDKDownloadTask extends SwingWorker<Object, Object> {

    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    @Override
    protected Object doInBackground() throws Exception {
      File modeFolder = mode.getFolder();
      
      // creating sdk folders
      File sdkFolder = new File(modeFolder, "sdk");
      if (!sdkFolder.exists()) sdkFolder.mkdir();
      File platformsFolder = new File(sdkFolder, "platforms");
      if (!platformsFolder.exists()) platformsFolder.mkdir();
      File buildToolsFolder = new File(sdkFolder, "build-tools");
      if (!buildToolsFolder.exists()) buildToolsFolder.mkdir();
      File extrasFolder = new File(sdkFolder, "extras");
      if (!extrasFolder.exists()) extrasFolder.mkdir();
      File googleRepoFolder = new File(extrasFolder, "google");
      if (!googleRepoFolder.exists()) googleRepoFolder.mkdir();
      File androidRepoFolder = new File(extrasFolder, "android");
      if (!androidRepoFolder.exists()) androidRepoFolder.mkdir();      
      
      // creating temp folder for downloaded zip packages
      File tempFolder = new File(modeFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        SDKUrlHolder downloadUrls = new SDKUrlHolder();
        String repositoryUrl = REPOSITORY_URL + REPOSITORY_LIST;
        String addonUrl = REPOSITORY_URL + ADDON_LIST;        
        getMainDownloadUrls(downloadUrls, repositoryUrl, Platform.getName());
        getExtrasDownloadUrls(downloadUrls, addonUrl, Platform.getName());        
        firePropertyChange(PROPERTY_CHANGE_EVENT_TOTAL, 0, downloadUrls.totalSize);
        totalSize = downloadUrls.totalSize + SUPPORT_LIBRARY_SIZE;

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
        
        // google repository
        File downloadedGoogleRepo = new File(tempFolder, downloadUrls.googleRepoFilename);
        downloadAndUnpack(downloadUrls.googleRepoUrl, downloadedGoogleRepo, googleRepoFolder, false);

        // android repository
        File downloadedSupportRepo = new File(tempFolder, downloadUrls.supportRepoFilename);
        downloadAndUnpack(downloadUrls.supportRepoUrl, downloadedSupportRepo, androidRepoFolder, false);
      
        // support library
        File downloadedSupportLibrary = new File(tempFolder, LEGACY_SUPPORT_LIBRARY);
        String supportLibraryUrl = REPOSITORY_URL + LEGACY_SUPPORT_LIBRARY;
        downloadAndUnpack(supportLibraryUrl, downloadedSupportLibrary, androidRepoFolder, false);        
        
        // usb driver
        if (Platform.isWindows()) {
          File downloadedFolder = new File(tempFolder, downloadUrls.usbDriverFilename);
          downloadAndUnpack(downloadUrls.usbDriverUrl, downloadedFolder, googleRepoFolder, false);
        }

        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sdkFolder.getAbsolutePath());
        }

        for (File f: tempFolder.listFiles()) f.delete();    
        tempFolder.delete();
        
         // Normalize built-tools and platform folders to android-<API LEVEL>
        String actualName = "android-" + downloadUrls.platformVersion;
        renameFolder(platformsFolder, "android-" + AndroidBuild.target_sdk, actualName);
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

      AndroidMode.extractFolder(saveTo, unpackTo, setExec);
    }

    private void getMainDownloadUrls(SDKUrlHolder urlHolder, 
        String repositoryUrl, String requiredHostOs) 
        throws ParserConfigurationException, IOException, SAXException {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new URL(repositoryUrl).openStream());
      Node archiveListItem;
      NodeList archiveList;
      Node archiveItem;
      
      // -----------------------------------------------------------------------
      // platform      
      Node platform = getLatestPlatform(doc.getElementsByTagName("sdk:platform"));
      if (platform != null) {
        NodeList version = ((Element) platform).getElementsByTagName("sdk:version");
        archiveListItem = ((Element) platform).getElementsByTagName("sdk:archives").item(0);
        archiveItem = ((Element) archiveListItem).getElementsByTagName("sdk:archive").item(0);
        urlHolder.platformVersion = version.item(0).getTextContent();
        urlHolder.platformUrl = ((Element) archiveItem).getElementsByTagName("sdk:url").item(0).getTextContent();
        urlHolder.platformFilename = urlHolder.platformUrl.split("/")[urlHolder.platformUrl.split("/").length-1];
        urlHolder.totalSize += Integer.parseInt(((Element) archiveItem).getElementsByTagName("sdk:size").item(0).getTextContent());        
      }

      // Difference between platform tools, build tools, and SDK tools: 
      // http://stackoverflow.com/questions/19911762/what-is-android-sdk-build-tools-and-which-version-should-be-used
      // Always get the latest!
      
      // -----------------------------------------------------------------------
      // platform-tools
      Node platformToolsItem = getLatestToolItem(doc.getElementsByTagName("sdk:platform-tool"));
      if (platformToolsItem != null) {
        archiveListItem = ((Element) platformToolsItem).getElementsByTagName("sdk:archives").item(0);
        archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
        for (int i = 0; i < archiveList.getLength(); i++) {
          Node archive = archiveList.item(i);
          String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
          if (hostOs.equals(requiredHostOs)) {
            urlHolder.platformToolsFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
            urlHolder.platformToolsUrl = REPOSITORY_URL + urlHolder.platformToolsFilename;
            urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
            break;
          }
        }
      }

      // -----------------------------------------------------------------------
      // build-tools
      Node buildToolsItem = getLatestToolItem(doc.getElementsByTagName("sdk:build-tool"));
      if (buildToolsItem != null) {
        Node revisionListItem = ((Element) buildToolsItem).getElementsByTagName("sdk:revision").item(0);
        String major = ((Element) revisionListItem).getElementsByTagName("sdk:major").item(0).getTextContent();
        String minor = ((Element) revisionListItem).getElementsByTagName("sdk:minor").item(0).getTextContent();
        String micro = ((Element) revisionListItem).getElementsByTagName("sdk:micro").item(0).getTextContent();
        urlHolder.buildToolsVersion = major + "." + minor + "." + micro;
        archiveListItem = ((Element) buildToolsItem).getElementsByTagName("sdk:archives").item(0);
        archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
        for (int i = 0; i < archiveList.getLength(); i++) {
          Node archive = archiveList.item(i);
          String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
          if (hostOs.equals(requiredHostOs)) {
            urlHolder.buildToolsFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
            urlHolder.buildToolsUrl = REPOSITORY_URL + urlHolder.buildToolsFilename;
            urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
            break;
          }
        }
      }
      
      // -----------------------------------------------------------------------
      // tools
      Node toolsItem = getLatestToolItem(doc.getElementsByTagName("sdk:tool"));
      if (toolsItem != null) {
        archiveListItem = ((Element) toolsItem).getElementsByTagName("sdk:archives").item(0);
        archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
        for (int i = 0; i < archiveList.getLength(); i++) {
          Node archive = archiveList.item(i);
          String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
          if (hostOs.equals(requiredHostOs)) {
            urlHolder.toolsFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
            urlHolder.toolsUrl = REPOSITORY_URL + urlHolder.toolsFilename;
            urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
            break;
          }
        }
      }      
    }
  }

  private void getExtrasDownloadUrls(SDKUrlHolder urlHolder, 
      String repositoryUrl, String requiredHostOs) 
      throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse(new URL(repositoryUrl).openStream());
    Node archiveListItem;
    NodeList archiveList;
    
    NodeList nodeList = doc.getElementsByTagName("sdk:extra");
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node sdkExtraItem = nodeList.item(i);
      if (sdkExtraItem != null) { 
        String name = ((Element) sdkExtraItem).getElementsByTagName("sdk:name-display").item(0).getTextContent();
        // ---------------------------------------------------------------------
        // Android Support repository
        if (name.equals("Android Support Repository")) {
          archiveListItem = ((Element) sdkExtraItem).getElementsByTagName("sdk:archives").item(0);
          archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");        
          Node archive = archiveList.item(0);
          urlHolder.supportRepoFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          urlHolder.supportRepoUrl = REPOSITORY_URL + urlHolder.supportRepoFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
        }
        
        // ---------------------------------------------------------------------
        // Google repository        
        if (name.equals("Google Repository")) {
          archiveListItem = ((Element) sdkExtraItem).getElementsByTagName("sdk:archives").item(0);
          archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");        
          Node archive = archiveList.item(0);
          urlHolder.googleRepoFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          urlHolder.googleRepoUrl = REPOSITORY_URL + urlHolder.googleRepoFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
        }
        
        // ---------------------------------------------------------------------
        // USB driver        
        if (name.equals("Google USB Driver")) {
          archiveListItem = ((Element) sdkExtraItem).getElementsByTagName("sdk:archives").item(0);
          archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");        
          Node archive = archiveList.item(0);
          urlHolder.usbDriverFilename = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          urlHolder.usbDriverUrl = REPOSITORY_URL + urlHolder.usbDriverFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archive).getElementsByTagName("sdk:size").item(0).getTextContent());
        }        
      }
    }
  }
  
  private Node getLatestPlatform(NodeList platformList) {
    Node latest = null;
    int maxRevision = -1;
    String platformDescription = "Android SDK Platform " +  AndroidBuild.target_sdk; 
    for (int i = 0; i < platformList.getLength(); i++) {
      Node platform = platformList.item(i);
      
      NodeList level = ((Element) platform).getElementsByTagName("sdk:api-level");
      NodeList desc = ((Element) platform).getElementsByTagName("sdk:description");
      NodeList revision = ((Element) platform).getElementsByTagName("sdk:revision");
      // API level and platform description are both used to avoid ambiguity with 
      // preview versions, which might share the API level with the earlier stable 
      // platform, but use the letter codename in their description.        
      if (level.item(0).getTextContent().equals(AndroidBuild.target_sdk) && 
          desc.item(0).getTextContent().equals(platformDescription)) {
        int intRevision = PApplet.parseInt(revision.item(0).getTextContent());
        if (maxRevision < intRevision) {
          latest = platform;
          maxRevision = intRevision;
        }
      }
    }
    return latest;
  }
  
  private Node getLatestToolItem(NodeList list) {
    Node latest = null;
    int maxMajor = -1;
    int maxMinor = -1;
    int maxMicro = -1; 
    for (int i = 0; i < list.getLength(); i++) {
      Node item = list.item(i);
      Node revision = ((Element)item).getElementsByTagName("sdk:revision").item(0);        
      NodeList major = ((Element)revision).getElementsByTagName("sdk:major");
      NodeList minor = ((Element)revision).getElementsByTagName("sdk:minor");
      NodeList micro = ((Element)revision).getElementsByTagName("sdk:micro");        
      int intMajor = PApplet.parseInt(major.item(0).getTextContent());
      int intMinor = PApplet.parseInt(minor.item(0).getTextContent());
      int intMicro = PApplet.parseInt(micro.item(0).getTextContent());
      if (maxMajor <= intMajor && maxMinor <= intMinor && maxMicro <= intMicro) {        
        latest = item;
        maxMajor = intMajor;
        maxMinor = intMinor;
        maxMicro = intMicro;
      }
    }
    return latest;
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

  public SDKDownloader(Frame editor, AndroidMode mode) {
    super(editor, "SDK download", true);
    this.editor = editor;
    this.mode = mode;
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

    Box pain = Box.createVerticalBox();
    pain.setBorder(new EmptyBorder(13, 13, 13, 13));
    outer.add(pain);

    String labelText = "Downloading Android SDK...";
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
    Dimension dim = new Dimension(Toolkit.getButtonWidth()*2,
        cancelButton.getPreferredSize().height);

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

    setResizable(false);
    setLocationRelativeTo(editor);
  }
}