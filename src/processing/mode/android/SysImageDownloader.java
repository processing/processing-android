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
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import processing.app.Platform;
import processing.app.Preferences;
import processing.app.exec.LineProcessor;
import processing.app.exec.StreamPump;
import processing.app.ui.Toolkit;
import processing.core.PApplet;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;

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
  final static private int FONT_SIZE = Toolkit.zoom(11);
  final static private int TEXT_MARGIN = Toolkit.zoom(8);
  final static private int TEXT_WIDTH = Toolkit.zoom(300);
  
  private static final String EMULATOR_GUIDE_URL =
      "https://developer.android.com/studio/run/emulator-acceleration.html";

  private static final String SYS_IMAGE_SELECTION_MESSAGE =
      "The Android emulator requires a system image to run. " +
      "There are two types of system images available:<br><br>" +
      "<b>1) ARM image -</b> slow but compatible with all computers, no extra configuration needed.<br><br>" +
      "<b>2) x86 image -</b> fast but compatible only with Intel CPUs, extra configuration may be needed, see " + 
      "<a href=\"" + EMULATOR_GUIDE_URL + "\">this guide</a> for more details.";

  private static final String HAXM_INSTALL_TITLE = "Some words of caution...";
  
  private static final String HAXM_INSTALL_MESSAGE =
      "You chose to run x86 images in the emulator. This is great but you need " + 
      "to install the Intel Hardware Accelerated Execution Manager (Intel HAXM).<br><br>" + 
      "Processing will try to run the HAXM installer now, which may ask for your " + 
      "administrator password or additional permissions. Good luck!";
  
  private static final String KVM_LINUX_GUIDE_URL =
      "https://developer.android.com/studio/run/emulator-acceleration.html#vm-linux";
  
  private static final String KVM_INSTALL_MESSAGE =
      "You chose to run x86 images in the emulator. This is great but you need " + 
      "to configure VM acceleration on Linux using the KVM package.<br><br>" + 
      "Follow <a href=\"" + KVM_LINUX_GUIDE_URL + "\">these instructions</a> " + 
      "to configure KVM. Good luck!";      
  
  private static final String SYS_IMAGES_URL = "https://dl.google.com/android/repository/sys-img/google_apis/";  
  private static final String SYS_IMAGES_LIST = "sys-img2-1.xml";
  
  private static final String SYS_IMAGES_WEAR_URL = "https://dl.google.com/android/repository/sys-img/android-wear/";
  private static final String SYS_IMAGES_WEAR_LIST = "sys-img2-1.xml";
  
  public static final String SYSTEM_IMAGE_TAG = "google_apis";

  public static final String SYSTEM_IMAGE_WEAR_TAG = "android-wear";

  private static final String PROPERTY_CHANGE_EVENT_TOTAL = "total";
  private static final String PROPERTY_CHANGE_EVENT_DOWNLOADED = "downloaded";

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private DownloadTask downloadTask;
  
  private Frame editor;
  private boolean result;
  private boolean wear;
  private boolean cancelled;
  
  private int totalSize = 0;  

  class UrlHolder {
    public String platformVersion;
    public String sysImgUrl, sysImgTag, sysImgWearUrl, sysImgWearTag;
    public String sysImgFilename, sysImgWearFilename;
    public int totalSize = 0;
  }

  class DownloadTask extends SwingWorker<Object, Object> {

    private int downloadedSize = 0;
    private int BUFFER_SIZE = 4096;

    @Override
    protected Object doInBackground() throws Exception {
      result = false;

      // The SDK should already be detected by the android mode
      String sdkPrefsPath = Preferences.get("android.sdk.path");
      
      File sketchbookFolder = processing.app.Base.getSketchbookFolder();
      File androidFolder = new File(sketchbookFolder, "android");
      if (!androidFolder.exists()) androidFolder.mkdir();
      
      File sdkFolder = new File(sdkPrefsPath); 
      if (!sdkFolder.exists()) {
        throw new IOException("SDK folder does not exist " + sdkFolder.getAbsolutePath());
      }
      
      // creating sdk folders
      File sysImgFolder = new File(sdkFolder, "system-images");
      if (!sysImgFolder.exists()) sysImgFolder.mkdir();

      // creating temp folder for downloaded zip packages
      File tempFolder = new File(androidFolder, "temp");
      if (!tempFolder.exists()) tempFolder.mkdir();

      try {
        String repo = wear ? SYS_IMAGES_WEAR_URL + SYS_IMAGES_WEAR_LIST : 
                             SYS_IMAGES_URL + SYS_IMAGES_LIST;
        
        UrlHolder downloadUrls = new UrlHolder();
        getDownloadUrls(downloadUrls, repo, Platform.getName());
        firePropertyChange(PROPERTY_CHANGE_EVENT_TOTAL, 0, downloadUrls.totalSize);
        totalSize = downloadUrls.totalSize;

        if (wear) {
          // wear system images
          File downloadedSysImgWear = new File(tempFolder, downloadUrls.sysImgWearFilename);
          File tmp = new File(sysImgFolder, "android-" + AndroidBuild.TARGET_SDK);
          if (!tmp.exists()) tmp.mkdir();
          File sysImgWearFinalFolder = new File(tmp, downloadUrls.sysImgWearTag);
          if (!sysImgWearFinalFolder.exists()) sysImgWearFinalFolder.mkdir();
          downloadAndUnpack(downloadUrls.sysImgWearUrl, downloadedSysImgWear, sysImgWearFinalFolder, false);
          fixSourceProperties(sysImgWearFinalFolder);
        } else {
          // mobile system images
          File downloadedSysImg = new File(tempFolder, downloadUrls.sysImgFilename);
          File tmp = new File(sysImgFolder, "android-" + AndroidBuild.TARGET_SDK);
          if (!tmp.exists()) tmp.mkdir();
          File sysImgFinalFolder = new File(tmp, downloadUrls.sysImgTag);
          if (!sysImgFinalFolder.exists()) sysImgFinalFolder.mkdir();
          downloadAndUnpack(downloadUrls.sysImgUrl, downloadedSysImg, sysImgFinalFolder, false);
          fixSourceProperties(sysImgFinalFolder);
        }

        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sysImgFolder.getAbsolutePath());
        }

        for (File f: tempFolder.listFiles()) f.delete();    
        tempFolder.delete();
        
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

      AndroidUtil.extractFolder(saveTo, unpackTo, setExec);
    }

    // For some reason the source.properties file includes Addon entries, 
    // and this breaks the image...
    private void fixSourceProperties(File imageFolder) {
      for (File d: imageFolder.listFiles()) {
        // Should iterate over the installed archs (x86, etc)
        if (d.isDirectory()) {
          for (File f: d.listFiles()) {
            if (PApplet.getExtension(f.getName()).equals("properties")) {
              String[] linesIn = PApplet.loadStrings(f);
              String concat = "";
              for (String l: linesIn) {
                if (l.indexOf("Addon") == -1) concat += l + "\n";
              }
              String[] linesOut = concat.split("\n");
              PApplet.saveStrings(f, linesOut);
            }
          }
        }
      }      
    }
    
    private void getDownloadUrls(UrlHolder urlHolder, 
        String repositoryUrl, String requiredHostOs) 
        throws ParserConfigurationException, IOException, SAXException, XPathException {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      XPathFactory xPathfactory = XPathFactory.newInstance();
      XPath xpath = xPathfactory.newXPath();
      XPathExpression expr;
      NodeList remotePackages;

      if (Preferences.get("android.system.image.type").equals("arm"))
        expr = xpath.compile("//remotePackage[contains(@path, '" + AndroidBuild.TARGET_SDK + "')" +
              "and contains(@path, \"armeabi-v7a\")]");
      else
        expr = xpath.compile("//remotePackage[contains(@path, '" + AndroidBuild.TARGET_SDK + "')" +
              "and contains(@path, \"x86\")]");
      
      if (wear) {
        Document docSysImgWear = db.parse(new URL(repositoryUrl).openStream());
        remotePackages = (NodeList) expr.evaluate(docSysImgWear, XPathConstants.NODESET);
        NodeList childNodes = remotePackages.item(0).getChildNodes();

        NodeList typeDetails = ((Element) childNodes).getElementsByTagName("type-details");
        NodeList tag = ((Element) typeDetails.item(0)).getElementsByTagName("tag");
        NodeList id = ((Element) tag.item(0)).getElementsByTagName("id");
        urlHolder.sysImgWearTag = id.item(0).getTextContent();

        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");
        NodeList archive = archives.item(0).getChildNodes();
        NodeList complete = ((Element) archive).getElementsByTagName("complete");

        NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
        NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

        urlHolder.sysImgWearFilename  =  url.item(0).getTextContent();
        urlHolder.sysImgWearUrl = SYS_IMAGES_WEAR_URL + urlHolder.sysImgWearFilename;
        urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
      } else {
        Document docSysImg = db.parse(new URL(repositoryUrl).openStream());
        remotePackages = (NodeList) expr.evaluate(docSysImg, XPathConstants.NODESET);
        NodeList childNodes = remotePackages.item(0).getChildNodes(); // Index 1 contains x86_64

        NodeList typeDetails = ((Element) childNodes).getElementsByTagName("type-details");
        //NodeList abi = ((Element) typeDetails.item(0)).getElementsByTagName("abi");
        NodeList tag = ((Element) typeDetails.item(0)).getElementsByTagName("tag");
        NodeList id = ((Element) tag.item(0)).getElementsByTagName("id");
        urlHolder.sysImgTag = id.item(0).getTextContent();

        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");
        NodeList archive = archives.item(0).getChildNodes();
        NodeList complete = ((Element) archive).getElementsByTagName("complete");

        NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
        NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

        urlHolder.sysImgFilename  =  url.item(0).getTextContent();
        urlHolder.sysImgUrl = SYS_IMAGES_URL + urlHolder.sysImgFilename;
        urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
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

  static public int showMessage() {
    String htmlString = "<html> " +
            "<head> <style type=\"text/css\">" +
            "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
            "margin: " + TEXT_MARGIN + "px; " +
            "width: " + TEXT_WIDTH + "px }" +
            "</style> </head>";
    htmlString += "<body> <p> " + SYS_IMAGE_SELECTION_MESSAGE + " </p> </body> </html>";
    String title = "Choose system image type to download...";
    JEditorPane pane = new JEditorPane("text/html", htmlString);
    pane.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
          Platform.openURL(e.getURL().toString());
        }
      }
    });
    pane.setEditable(false);
    JLabel label = new JLabel();
    pane.setBackground(label.getBackground());

    String[] options = new String[] {
            "Slow but safe", "I like speed!"
    };
    int result = JOptionPane.showOptionDialog(null, pane, title,
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
            null, options, options[0]);
    if (result == JOptionPane.YES_OPTION) {
      return JOptionPane.YES_OPTION;
    } else if (result == JOptionPane.NO_OPTION) {
      return JOptionPane.NO_OPTION;
    } else {
      return JOptionPane.CLOSED_OPTION;
    }
  }

  public SysImageDownloader(Frame editor, boolean wear) {
    super(editor, "Emulator download", true);
    this.editor = editor;
    this.wear = wear;
    this.result = false;    
    createLayout();
  }
  
  public void run() {
    cancelled = false;

    final int result = showMessage();
    if (result == JOptionPane.YES_OPTION || result == JOptionPane.CLOSED_OPTION) {
      // ARM
      Preferences.set("android.system.image.type", "arm");
    } else {
      // x86
      Preferences.set("android.system.image.type", "x86");
      installHAXM();
    }
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

  static public void installHAXM() {
    File haxmFolder = AndroidSDK.getHAXMInstallerFolder();
    if (Platform.isLinux()) {
      AndroidUtil.showMessage(HAXM_INSTALL_TITLE, KVM_INSTALL_MESSAGE);      
    } else if (haxmFolder.exists()) {
      AndroidUtil.showMessage(HAXM_INSTALL_TITLE, HAXM_INSTALL_MESSAGE);        
      
      ProcessBuilder pb;
      if (Platform.isWindows()) {
        File exec = new File(haxmFolder, "silent_install.bat");
        pb = new ProcessBuilder(exec.getAbsolutePath());
      } else {
        File exec = new File(haxmFolder, "HAXM installation");
        pb = new ProcessBuilder(exec.getAbsolutePath());
      }
      pb.directory(haxmFolder);
      pb.redirectErrorStream(true);
      
      Process process = null;      
      try {
        process = pb.start();
      } catch (IOException e) {
        e.printStackTrace();
      }
      
      if (process != null) {
        try {
          StreamPump output = new StreamPump(process.getInputStream(), "HAXM: ");
          output.addTarget(new LineProcessor() {
            @Override
            public void processLine(String line) {
              System.out.println("HAXM: " + line);
            }
          }).start();

          process.waitFor();
        } catch (final InterruptedException ie) {
          ie.printStackTrace();
        } finally {
          process.destroy();
        }              
      }
    }    
  }
  
  private void createLayout() {
    Container outer = getContentPane();
    outer.removeAll();

    Box pain = Box.createVerticalBox();
    pain.setBorder(new EmptyBorder(13, 13, 13, 13));
    outer.add(pain);

    String labelText = wear ? "Downloading Wear system image..." :
                              "Downloading system image...";
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