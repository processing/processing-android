package processing.mode.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import processing.app.Base;
import processing.app.Preferences;

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
import java.io.IOException;
import java.net.URL;

public class SDKDownloader extends JFrame implements PropertyChangeListener {

  private static final String URL_SDK_TOOLS_WINDOWS = "http://dl.google.com/android/android-sdk_r23-windows.zip";
  private static final String URL_SDK_TOOLS_MACOS = "http://dl.google.com/android/android-sdk_r23-macosx.zip";
  private static final String URL_SDK_TOOLS_LINUX = "http://dl.google.com/android/android-sdk_r23-linux.tgz";

  private static final String URL_REPOSITORY = "https://dl-ssl.google.com/android/repository/repository-10.xml";
  private static final String URL_REPOSITORY_FOLDER = "http://dl-ssl.google.com/android/repository/";

  private static final String PLATFORM_API_LEVEL = "10";

  class SDKUrlHolder {
    public String platformToolsUrl, buildToolsUrl, platformUrl;
  }

  class SDKDownloadTask extends SwingWorker {

    @Override
    protected Object doInBackground() throws Exception {
      String hostOs = getOsString();
      try {
        SDKUrlHolder downloadUrls = getDownloadUrls(URL_REPOSITORY, hostOs);
      } catch (ParserConfigurationException e) {
        // TODO Handle exceptions here somehow (ie show error message)
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
    }

    private String getOsString() {
      if(Base.isWindows()) {
        return "windows";
      } else if(Base.isLinux()) {
        return "linux";
      } else {
        return "macosx";
      }
    }

    private SDKUrlHolder getDownloadUrls(String repositoryUrl, String requiredHostOs) throws ParserConfigurationException, IOException, SAXException {
      SDKUrlHolder urlHolder = new SDKUrlHolder();

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new URL(repositoryUrl).openStream());

      // platform
      NodeList platformList = doc.getElementsByTagName("sdk:platform");
      for(int i = 0; i < platformList.getLength(); i++) {
        Node platform = platformList.item(i);
        if(((Element) platform).getElementsByTagName("sdk:api-level").item(0).getTextContent().equals(PLATFORM_API_LEVEL)) {
          Node archiveListItem = ((Element) platform).getElementsByTagName("sdk:archives").item(0);
          Node archiveItem = ((Element) archiveListItem).getElementsByTagName("sdk:archive").item(0);
          urlHolder.platformUrl = ((Element) archiveItem).getElementsByTagName("sdk:url").item(0).getTextContent();
        }
      }

      // platform-tools
      Node platformToolItem = doc.getElementsByTagName("sdk:platform-tool").item(0);
      Node archiveListItem = ((Element) platformToolItem).getElementsByTagName("sdk:archives").item(0);
      NodeList archiveList = ((Element) archiveListItem).getElementsByTagName("sdk:archive");
      for(int i = 0; i < archiveList.getLength(); i++) {
        Node archive = archiveList.item(i);
        String hostOs = ((Element) archive).getElementsByTagName("sdk:host-os").item(0).getTextContent();
        if(hostOs.equals(requiredHostOs)) {
          String platformToolsUrl = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          if(!platformToolsUrl.startsWith("http")) platformToolsUrl = URL_REPOSITORY_FOLDER + platformToolsUrl;
          urlHolder.platformToolsUrl = platformToolsUrl;
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
        if(hostOs.equals(requiredHostOs)) {
          String buildToolsUrl = (((Element) archive).getElementsByTagName("sdk:url").item(0).getTextContent());
          if(!buildToolsUrl.startsWith("http")) buildToolsUrl = URL_REPOSITORY_FOLDER + buildToolsUrl;
          urlHolder.buildToolsUrl = buildToolsUrl;
          break;
        }
      }

      return urlHolder;
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {

  }

  public SDKDownloader() {
    super("Android SDK downloading...");

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

    JProgressBar progressBar = new JProgressBar(0, 100);
    progressBar.setValue(0);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(true);
    pain.add(progressBar);

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
    Dimension dim = new Dimension(Preferences.BUTTON_WIDTH,
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
    processing.app.Toolkit.registerWindowCloseKeys(root, disposer);
    processing.app.Toolkit.setIcon(this);

    pack();

    Dimension screen = processing.app.Toolkit.getScreenSize();
    Dimension windowSize = getSize();

    setLocation((screen.width - windowSize.width) / 2,
        (screen.height - windowSize.height) / 2);

    setVisible(true);
    setAlwaysOnTop(true);
  }
}