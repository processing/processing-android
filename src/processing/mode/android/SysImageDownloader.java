/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2016 The Processing Foundation
 
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

@SuppressWarnings("serial")
public class SysImageDownloader extends JDialog implements PropertyChangeListener {
  private static final String URL_SYS_IMAGES = "https://dl-ssl.google.com/android/repository/sys-img/android/sys-img.xml";
  private static final String URL_SYS_IMAGES_FOLDER = "http://dl-ssl.google.com/android/repository/sys-img/android/";
  private static final String SYSTEM_IMAGE = "Intel x86 Atom System Image";  
  
  private static final String PROPERTY_CHANGE_EVENT_TOTAL = "total";
  private static final String PROPERTY_CHANGE_EVENT_DOWNLOADED = "downloaded";

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private DownloadTask downloadTask;
  
  private Frame editor;
  private AndroidMode mode;
  private boolean result;
  private boolean cancelled;
  
  private int totalSize = 0;  

  class UrlHolder {
    public String platformVersion;
    public String sysImgUrl, sysImgTag;
    public String sysImgFilename;
    public int totalSize = 0;
  }

  class DownloadTask extends SwingWorker<Object, Object> {

    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    @Override
    protected Object doInBackground() throws Exception {
      result = false;

      File modeFolder = mode.getFolder();
      File sdkFolder = new File(modeFolder, "sdk");
      if (!sdkFolder.exists()) {
        throw new IOException("SDK folder does not exist " + sdkFolder.getAbsolutePath());
      }
      
      // creating sdk folders
      File sysImgFolder = new File(sdkFolder, "system-images");
      if (!sysImgFolder.exists()) sysImgFolder.mkdir();

      // creating temp folder for downloaded zip packages
      File tempFolder = new File(modeFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        UrlHolder downloadUrls = getDownloadUrls(URL_SYS_IMAGES, Platform.getName());
        firePropertyChange(PROPERTY_CHANGE_EVENT_TOTAL, 0, downloadUrls.totalSize);
        totalSize = downloadUrls.totalSize;

        // default system images
        File downloadedSysImg = new File(tempFolder, downloadUrls.sysImgFilename);
        File tmp = new File(sysImgFolder, "android-" + SDKDownloader.PLATFORM_API_LEVEL);
        if (!tmp.exists()) tmp.mkdir();
        File sysImgFinalFolder = new File(tmp, downloadUrls.sysImgTag);
        if (!sysImgFinalFolder.exists()) sysImgFinalFolder.mkdir();
        downloadAndUnpack(downloadUrls.sysImgUrl, downloadedSysImg, sysImgFinalFolder, false);  

        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sysImgFolder.getAbsolutePath());
        }

        tempFolder.delete();

        // Normalize platform folder to android-<API LEVEL>
//        File expectedPath = new File(platformsFolder, "android-" + AndroidBuild.target_sdk);
//        File actualPath = new File(platformsFolder, "android-" + downloadUrls.platformVersion);
//        if (!expectedPath.exists()) {
//          if (actualPath.exists()) {
//            actualPath.renameTo(expectedPath);
//          } else {
//            throw new IOException("Error unpacking platform to " + actualPath.getAbsolutePath());
//          }
//        }
//        
//        // Done, let's set the environment and load the new SDK!
//        Platform.setenv("ANDROID_SDK", sdkFolder.getAbsolutePath());
//        Preferences.set("android.sdk.path", sdkFolder.getAbsolutePath());
        
        result = true;
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
        e.printStackTrace();
        return;
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

    private UrlHolder getDownloadUrls(String repositoryUrl, String requiredHostOs) 
        throws ParserConfigurationException, IOException, SAXException {
      UrlHolder urlHolder = new UrlHolder();

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      
      // default system image
      Document docSysImg = db.parse(new URL(repositoryUrl).openStream());
      NodeList sysImgList = docSysImg.getElementsByTagName("sdk:system-image");
      for (int i = 0; i < sysImgList.getLength(); i++) {
        Node img = sysImgList.item(i);
        NodeList level = ((Element) img).getElementsByTagName("sdk:api-level");
        NodeList desc = ((Element) img).getElementsByTagName("sdk:description");
        NodeList codename = ((Element) img).getElementsByTagName("sdk:codename");
        // Only considering nodes without a codename, which correspond to the platform
        // pre-releases.        
        if (level.item(0).getTextContent().equals(SDKDownloader.PLATFORM_API_LEVEL) &&
            desc.item(0).getTextContent().equals(SYSTEM_IMAGE) && 
            codename.item(0) == null) {          
          NodeList tag = ((Element) img).getElementsByTagName("sdk:tag-id");
          urlHolder.sysImgTag = tag.item(0).getTextContent();          
          Node archiveListItem = ((Element) img).getElementsByTagName("sdk:archives").item(0);
          Node archiveItem = ((Element) archiveListItem).getElementsByTagName("sdk:archive").item(0);
          urlHolder.sysImgFilename = ((Element) archiveItem).getElementsByTagName("sdk:url").item(0).getTextContent();
          urlHolder.sysImgUrl = URL_SYS_IMAGES_FOLDER + urlHolder.sysImgFilename;
          urlHolder.totalSize += Integer.parseInt(((Element) archiveItem).getElementsByTagName("sdk:size").item(0).getTextContent());
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

  public SysImageDownloader(Frame editor, AndroidMode mode) {
    super(editor, "Emulator download", true);
    this.editor = editor;
    this.mode = mode;
    this.result = false;    
    createLayout();
  }
  
  public void run() {
    cancelled = false;
    downloadTask = new DownloadTask();
    downloadTask.addPropertyChangeListener(this);
    downloadTask.execute();
    setAlwaysOnTop(true);
    setVisible(true);
  }
  
  public boolean cancelled() {
    return cancelled;
  }
  
  public boolean getResult() {
    return result;
  }
  
  private void createLayout() {
    Container outer = getContentPane();
    outer.removeAll();

    Box pain = Box.createVerticalBox();
    pain.setBorder(new EmptyBorder(13, 13, 13, 13));
    outer.add(pain);

    String labelText = "Downloading Android Emulator...";
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