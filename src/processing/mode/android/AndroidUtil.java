/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2017 The Processing Foundation

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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import processing.app.Base;
import processing.app.Platform;
import processing.app.SketchException;
import processing.app.Util;
import processing.core.PApplet;

/** 
 * Some utilities.
 */
public class AndroidUtil {
  
  // Creates a message dialog, where the text can contain clickable links.
  static public void showMessage(String title, String text) {
    if (title == null) title = "Message";
    if (Base.isCommandLine()) {      
      System.out.println(title + ": " + text);
    } else {
      String htmlString = "<html> " +
          "<head> <style type=\"text/css\">"+
          "p { font: 11pt \"Lucida Grande\"; margin-top: 8px; width: 300px }"+
          "</style> </head>" +
          "<body> <p>" + text + "</p> </body> </html>";      
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
      JOptionPane.showMessageDialog(null, pane, title, 
          JOptionPane.INFORMATION_MESSAGE);
    }
  }     
  
  static public void writeFile(final File file, String[] lines) {
    final PrintWriter writer = PApplet.createWriter(file);
    for (String line: lines) writer.println(line);
    writer.flush();
    writer.close();
  }
  
  
  static public File createPath(final File parent, final String name) 
      throws SketchException {
    final File result = new File(parent, name);
    if (!(result.exists() || result.mkdirs())) {
      throw new SketchException("Could not create " + result);
    }
    return result;
  }
  
  
  static public void createFileFromTemplate(final File tmplFile, final File destFile) {
    createFileFromTemplate(tmplFile, destFile, null);
  } 
  

  static public void createFileFromTemplate(final File tmplFile, final File destFile, 
      final HashMap<String, String> replaceMap) {
    PrintWriter pw = PApplet.createWriter(destFile);    
    String lines[] = PApplet.loadStrings(tmplFile);
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].indexOf("@@") != -1 && replaceMap != null) {
        StringBuilder sb = new StringBuilder(lines[i]);
        int index = 0;
        for (String key: replaceMap.keySet()) {
          String val = replaceMap.get(key);
          while ((index = sb.indexOf(key)) != -1) {
            sb.replace(index, index + key.length(), val);
          }          
        }    
        lines[i] = sb.toString();
      }
      // explicit newlines to avoid Windows CRLF
      pw.print(lines[i] + "\n");
    }
    pw.flush();
    pw.close();    
  }
  
  
  static public void extractFolder(File file, File newPath, boolean setExec) 
    throws IOException {
    extractFolder(file, newPath, setExec, false);
  }
  
  static public void extractFolder(File file, File newPath, boolean setExec, 
    boolean remRoot) throws IOException {
    int BUFFER = 2048;
    
    ZipFile zip = new ZipFile(file);
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {
      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();
      String currentEntry = entry.getName();
        
      if (remRoot) {
        // Remove root folder from path
        int idx = currentEntry.indexOf(File.separator); 
        currentEntry = currentEntry.substring(idx + 1);
      }
        
      File destFile = new File(newPath, currentEntry);
      //destFile = new File(newPath, destFile.getName());
      File destinationParent = destFile.getParentFile();

      // create the parent directory structure if needed
      destinationParent.mkdirs();

      String ext = PApplet.getExtension(currentEntry);
      if (setExec && ext.equals("unknown")) {        
        // On some OS X machines the android binaries lose their executable
        // attribute, rendering the mode unusable
        destFile.setExecutable(true);
      }
        
      if (!entry.isDirectory()) {
        // should preserve permissions
        // https://bitbucket.org/atlassian/amps/pull-requests/21/amps-904-preserve-executable-file-status/diff
        BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry));
        int currentByte;
        // establish buffer for writing file
        byte data[] = new byte[BUFFER];

        // write the current file to disk
        FileOutputStream fos = new FileOutputStream(destFile);
        BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);

        // read and write until last byte is encountered
        while ((currentByte = is.read(data, 0, BUFFER)) != -1) {
          dest.write(data, 0, currentByte);
        }
        dest.flush();
        dest.close();
        is.close();
      }
    }
    zip.close();
  }  
  
  static public void extractClassesJarFromAar(File wearFile, File explodeDir, 
    File jarFile) throws IOException {
    extractClassesJarFromAar(wearFile, explodeDir, jarFile, true);
  }
  
  
  static public void extractClassesJarFromAar(File wearFile, File explodeDir, 
    File jarFile, boolean removeDir) throws IOException {
    extractFolder(wearFile, explodeDir, false);
    File classFile = new File(explodeDir, "classes.jar");
    Util.copyFile(classFile, jarFile);
    Util.removeDir(explodeDir);
  }  
}
