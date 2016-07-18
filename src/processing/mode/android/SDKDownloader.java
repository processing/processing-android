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

// http://dl-ssl.google.com/android/repository/sys-img/android/sys-img.xml
// http://dl-ssl.google.com/android/repository/sys-img/android-wear/sys-img.xml
// http://dl-ssl.google.com/android/repository/sys-img/android-wear/android-wear-sys-img.xml

@SuppressWarnings("serial")
public class SDKDownloader extends JDialog implements PropertyChangeListener {
  public static final String PLATFORM_API_LEVEL = "23";
  
  private static final String URL_REPOSITORY = "https://dl-ssl.google.com/android/repository/repository-11.xml";
  private static final String URL_REPOSITORY_FOLDER = "http://dl-ssl.google.com/android/repository/";
  private static final String URL_USB_DRIVER = "https://dl-ssl.google.com//android/repository/latest_usb_driver_windows.zip";

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
    public String platformVersion;
    public String platformToolsUrl, buildToolsUrl, platformUrl, toolsUrl;
    public String platformToolsFilename, buildToolsFilename, platformFilename, toolsFilename;
    public int totalSize = 0;
  }

  class SDKDownloadTask extends SwingWorker<Object, Object> {

    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    @Override
    protected Object doInBackground() throws Exception {
      
      
//      File modeDirectory = new File(folder, getTypeName());
//      
//      File modeFolder = new File(Base.getSketchbookModesFolder() + "/AndroidMode");

      File modeFolder = mode.getFolder();
      
      // creating sdk folders
      File sdkFolder = new File(modeFolder, "sdk");
      if (!sdkFolder.exists()) sdkFolder.mkdir();
      File platformsFolder = new File(sdkFolder, "platforms");
      if (!platformsFolder.exists()) platformsFolder.mkdir();
      File buildToolsFolder = new File(sdkFolder, "build-tools");
      if (!buildToolsFolder.exists()) buildToolsFolder.mkdir();
      File sysImgFolder = new File(sdkFolder, "system-images");
      if (!sysImgFolder.exists()) sysImgFolder.mkdir();
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

        // usb driver
        if (Platform.isWindows()) {
          File usbDriverFolder = new File(extrasFolder, "google");
          File downloadedFolder = new File(tempFolder, "latest_usb_driver_windows.zip");
          downloadAndUnpack(URL_USB_DRIVER, downloadedFolder, usbDriverFolder, false);
        }
        
        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sdkFolder.getAbsolutePath());
        }

        tempFolder.delete();
        
        // Normalize platform folder to android-<API LEVEL>
        File expectedPath = new File(platformsFolder, "android-" + PLATFORM_API_LEVEL);
        File actualPath = new File(platformsFolder, "android-" + downloadUrls.platformVersion);
        if (!expectedPath.exists()) {
          if (actualPath.exists()) {
            actualPath.renameTo(expectedPath);
          } else {
            throw new IOException("Error unpacking platform to " + actualPath.getAbsolutePath());
          }
        }

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

      AndroidMode.extractFolder(saveTo, unpackTo, setExec);
    }

    private SDKUrlHolder getDownloadUrls(String repositoryUrl, String requiredHostOs) 
        throws ParserConfigurationException, IOException, SAXException {
      SDKUrlHolder urlHolder = new SDKUrlHolder();

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new URL(repositoryUrl).openStream());

      // platform
      String platformDescription = "Android SDK Platform " +  PLATFORM_API_LEVEL;
      NodeList platformList = doc.getElementsByTagName("sdk:platform");
      for (int i = 0; i < platformList.getLength(); i++) {
        Node platform = platformList.item(i);
        NodeList version = ((Element) platform).getElementsByTagName("sdk:version");
        NodeList level = ((Element) platform).getElementsByTagName("sdk:api-level");
        NodeList desc = ((Element) platform).getElementsByTagName("sdk:description");
        // API level and platform description are both used to avoid ambiguity with 
        // preview versions, which might share the API level with the earlier stable 
        // platform, but use the letter codename in their description.        
        if (level.item(0).getTextContent().equals(PLATFORM_API_LEVEL) && 
            desc.item(0).getTextContent().equals(platformDescription)) {
          Node archiveListItem = ((Element) platform).getElementsByTagName("sdk:archives").item(0);
          Node archiveItem = ((Element) archiveListItem).getElementsByTagName("sdk:archive").item(0);
          urlHolder.platformVersion = version.item(0).getTextContent();
          urlHolder.platformUrl = ((Element) archiveItem).getElementsByTagName("sdk:url").item(0).getTextContent();
          urlHolder.platformFilename = urlHolder.platformUrl.split("/")[urlHolder.platformUrl.split("/").length-1];
          urlHolder.totalSize += Integer.parseInt(((Element) archiveItem).getElementsByTagName("sdk:size").item(0).getTextContent());
        }
      }

      // platform-tools
      Node platformToolItem = doc.getElementsByTagName("sdk:platform-tool").item(0);
      Node archiveListItem = ((Element) platformToolItem).getElementsByTagName("sdk:archives").item(0);
      NodeList archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
      for (int i = 0; i < archiveList.getLength(); i++) {
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
      NodeList buildToolList = doc.getElementsByTagName("sdk:build-tool");
      for (int i = 0; i < buildToolList.getLength(); i++) {
        Node buildTool = buildToolList.item(i);
        NodeList revision = ((Element) buildTool).getElementsByTagName("sdk:revision");
        NodeList major = ((Element) revision).getElementsByTagName("sdk:major");
        NodeList minor = ((Element) revision).getElementsByTagName("sdk:minor");
        NodeList micro = ((Element) revision).getElementsByTagName("sdk:micro");
        
        
      }
      
      Node buildToolsItem = doc.getElementsByTagName("sdk:build-tool").item(doc.getElementsByTagName("sdk:build-tool").getLength()-1);
      archiveListItem = ((Element) buildToolsItem).getElementsByTagName("sdk:archives").item(0);
      archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
      for (int i = 0; i < archiveList.getLength(); i++) {
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
      for (int i = 0; i < archiveList.getLength(); i++) {
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