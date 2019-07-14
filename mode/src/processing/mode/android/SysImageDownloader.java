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
  
  private static final String SYS_IMAGES_ARM_URL = "https://dl.google.com/android/repository/sys-img/android/";
  
  private static final String SYS_IMAGES_PHONE_URL = "https://dl.google.com/android/repository/sys-img/google_apis/";  
  private static final String SYS_IMAGES_PHONE_LIST = "sys-img2-1.xml";
  
  private static final String SYS_IMAGES_WEAR_URL = "https://dl.google.com/android/repository/sys-img/android-wear/";
  private static final String SYS_IMAGES_WEAR_LIST = "sys-img2-1.xml";
  
  private static final String EMULATOR_GUIDE_URL =
      "https://developer.android.com/studio/run/emulator-acceleration.html";

  private static final String KVM_LINUX_GUIDE_URL =
      "https://developer.android.com/studio/run/emulator-acceleration.html#vm-linux";

  private JProgressBar progressBar;
  private JLabel downloadedTextArea;

  private DownloadTask downloadTask;
  
  private Frame editor;
  private boolean result;
  private boolean wear;
  private boolean askABI;
  private String abi;
  private String api;
  private String tag;
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
        String repo;
        if (wear) {
          repo = SYS_IMAGES_WEAR_URL + SYS_IMAGES_WEAR_LIST;
        } else if (abi.equals("arm")) {
          // The ARM images using Google APIs are too slow, so use the 
          // older Android (AOSP) images.
          repo = SYS_IMAGES_PHONE_URL + SYS_IMAGES_PHONE_LIST;
        } else {
          repo = SYS_IMAGES_PHONE_URL + SYS_IMAGES_PHONE_LIST;
        }
        
        UrlHolder downloadUrls = new UrlHolder();
        getDownloadUrls(downloadUrls, repo, Platform.getName());
        firePropertyChange(AndroidMode.getTextString("download_property.change_event_total"), 0, downloadUrls.totalSize);
        totalSize = downloadUrls.totalSize;

        if (wear) {
          // wear system images
          File downloadedSysImgWear = new File(tempFolder, downloadUrls.sysImgWearFilename);
          File tmp = new File(sysImgFolder, api);

          if (!tmp.exists()) tmp.mkdir();
          File sysImgWearFinalFolder = new File(tmp, tag);
          if (!sysImgWearFinalFolder.exists()) sysImgWearFinalFolder.mkdir();
          downloadAndUnpack(downloadUrls.sysImgWearUrl, downloadedSysImgWear, sysImgWearFinalFolder, false);
          fixSourceProperties(sysImgWearFinalFolder);
        } else {
          // mobile system images
          File downloadedSysImg = new File(tempFolder, downloadUrls.sysImgFilename);
          File tmp = new File(sysImgFolder, api); //replaced target SDK with selected API
          
          if (!tmp.exists()) tmp.mkdir();
          File sysImgFinalFolder = new File(tmp, tag);
          if (!sysImgFinalFolder.exists()) sysImgFinalFolder.mkdir();
          downloadAndUnpack(downloadUrls.sysImgUrl, downloadedSysImg, sysImgFinalFolder, false);
          fixSourceProperties(sysImgFinalFolder);
        }

        if (Platform.isLinux() || Platform.isMacOS()) {
          Runtime.getRuntime().exec("chmod -R 755 " + sysImgFolder.getAbsolutePath());
        }

        for (File f: tempFolder.listFiles()) f.delete();    
        tempFolder.delete();

        if (Platform.isLinux() && Platform.getVariant().equals("64")) {          
          AndroidUtil.showMessage(AndroidMode.getTextString("sys_image_downloader.dialog.ia32libs_title"), AndroidMode.getTextString("sys_image_downloader.dialog.ia32libs_body"));
        }
        
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
        firePropertyChange(AndroidMode.getTextString("download_property.change_event_downloaded"), 0, downloadedSize);
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

      if (abi.equals("arm")) {
        String packageName = "system-images;" + api + ";" + tag + ";" + abi + "eabi-v7a";
        expr = xpath.compile(String.format("//remotePackage[starts-with(@path, \"%s\")]",packageName));
      }
      else {
        String packageName = "system-images;"+api+";"+tag+";"+abi;
        //Search for the selected package and not the package that you want.
        expr = xpath.compile(String.format("//remotePackage[starts-with(@path, \"%s\")]",packageName));
      }
      
      if (wear) {
        Document docSysImgWear = db.parse(new URL(repositoryUrl).openStream());
        remotePackages = (NodeList) expr.evaluate(docSysImgWear, XPathConstants.NODESET);
        NodeList childNodes = remotePackages.item(0).getChildNodes();

        NodeList typeDetails = ((Element) childNodes).getElementsByTagName("type-details");
//        NodeList tag = ((Element) typeDetails.item(0)).getElementsByTagName("tag");
//        NodeList id = ((Element) tag.item(0)).getElementsByTagName("id");
//        urlHolder.sysImgWearTag = id.item(0).getTextContent();

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
        //NodeList api = ((Element) typeDetails.item(0)).getElementsByTagName("api-level");
        //System.out.println(api.item(0).getTextContent());

        NodeList archives = ((Element) childNodes).getElementsByTagName("archive");
        NodeList archive = archives.item(0).getChildNodes();
        NodeList complete = ((Element) archive).getElementsByTagName("complete");

        NodeList url = ((Element) complete.item(0)).getElementsByTagName("url");
        NodeList size = ((Element) complete.item(0)).getElementsByTagName("size");

        urlHolder.sysImgFilename  =  url.item(0).getTextContent();
        String imgUrl = SYS_IMAGES_PHONE_URL;
        urlHolder.sysImgUrl = imgUrl + urlHolder.sysImgFilename;
        System.out.println(urlHolder.sysImgUrl);
        urlHolder.totalSize += Integer.parseInt(size.item(0).getTextContent());
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

  static public int showSysImageMessage() {
    String htmlString = "<html> " +
            "<head> <style type=\"text/css\">" +
            "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " +
            "margin: " + TEXT_MARGIN + "px; " +
            "width: " + TEXT_WIDTH + "px }" +
            "</style> </head>";
    htmlString += "<body> <p> " + AndroidMode.getTextString("sys_image_downloader.dialog.select_image_body", EMULATOR_GUIDE_URL) + " </p> </body> </html>";
    String title = AndroidMode.getTextString("sys_image_downloader.dialog.select_image_title");
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
            AndroidMode.getTextString("sys_image_downloader.option.x86_image"), 
            AndroidMode.getTextString("sys_image_downloader.option.arm_image")
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

  public SysImageDownloader(Frame editor, boolean wear, String ABI, String API, String TAG) {
    super(editor, AndroidMode.getTextString("sys_image_downloader.download_title"), true);
    this.editor = editor;
    this.wear = wear;
    this.abi = ABI;
    this.api = API;
    this.tag = TAG;
    this.result = false;
    createLayout();
  }
  
  public void run() {
    cancelled = false;

    abi = Preferences.get("android.emulator.image.abi");
    if (abi == null || askABI) {
      // Either there was no image architecture selected, or the default was set.
      // In this case, we give the user the option to choose between ARM and x86
      
      final int result;
      // PROCESSOR_IDENTIFIER is only defined on Windows. For cross-platform CPU
      // info, in the future we could use OSHI: https://github.com/oshi/oshi
      String procId = System.getenv("PROCESSOR_IDENTIFIER");
      if (procId != null) {
        if (-1 < procId.indexOf("Intel")) {
          // Intel CPU: we go for the x86 abi
          result = JOptionPane.YES_OPTION;
        } else {
          // Another CPU, can only be AMD, so we go for ARM abi          
          result = JOptionPane.NO_OPTION;
        }
      } else if (Platform.isMacOS()) {
        // Macs only have Intel CPUs, so we also go for the x86 abi
        result = JOptionPane.YES_OPTION;
      } else {
        result = showSysImageMessage();  
      }
      if (result == JOptionPane.YES_OPTION || result == JOptionPane.CLOSED_OPTION) {
        abi = "x86";
        installHAXM();
      } else {
        abi = "arm";        
      }
      Preferences.set("android.emulator.image.abi", abi);
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
      AndroidUtil.showMessage(AndroidMode.getTextString("sys_image_downloader.dialog.accel_images_title"), 
                              AndroidMode.getTextString("sys_image_downloader.dialog.kvm_config_body", KVM_LINUX_GUIDE_URL));      
    } else if (haxmFolder.exists()) {
      AndroidUtil.showMessage(AndroidMode.getTextString("sys_image_downloader.dialog.accel_images_title"), 
                              AndroidMode.getTextString("sys_image_downloader.dialog.haxm_install_body"));        
      
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

    String labelText = wear ? AndroidMode.getTextString("sys_image_downloader.download_watch_label") :
                              AndroidMode.getTextString("sys_image_downloader.download_phone_label");
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
    JButton cancelButton = new JButton(AndroidMode.getTextString("download_prompt.cancel"));
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