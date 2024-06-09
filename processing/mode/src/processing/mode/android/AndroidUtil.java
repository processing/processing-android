/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2017-21 The Processing Foundation

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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import processing.app.Base;
import processing.app.Messages;
import processing.app.Mode;
import processing.app.Platform;
import processing.app.SketchException;
import processing.app.Util;
import processing.app.exec.ProcessHelper;
import processing.app.exec.ProcessResult;
import processing.app.ui.Toolkit;
import processing.core.PApplet;

/** 
 * Some utilities.
 */
public class AndroidUtil {
  final static private int FONT_SIZE = Toolkit.zoom(11);
  final static private int TEXT_MARGIN = Toolkit.zoom(8);
  final static private int TEXT_WIDTH = Toolkit.zoom(300);  
  
  // Creates a message dialog, where the text can contain clickable links.
  static public void showMessage(String title, String text) {
    System.out.println(text);
    if (title == null) title = "Message";
    if (Base.isCommandLine()) {      
      System.out.println(title + ": " + text);
    } else {
      String htmlString = "<html> " +
          "<head> <style type=\"text/css\">" +
          "p { font: " + FONT_SIZE + "pt \"Lucida Grande\"; " + 
              "margin: " + TEXT_MARGIN + "px; " + 
              "width: " + TEXT_WIDTH + "px }" +
          "</style> </head>" +
          "<body> <p>" + text + "</p> </body> </html>";
      JEditorPane pane = new JEditorPane();
      pane.setContentType("text/html");
      pane.setText(htmlString);
      pane.setEditable(false);

      pane.addHyperlinkListener(new HyperlinkListener() {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
          if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED)) {
            if (e.getURL() != null) {
              Platform.openURL(e.getURL().toString());
            } else {
              String description = e.getDescription();
              System.err.println("Cannot open this URL: " + description);
            }
          }
        }
      });
      
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

  static public File createSubFolder(File parent, String name) throws IOException {
    File newFolder = new File(parent, name);
    if (newFolder.exists()) {
      String stamp = AndroidMode.getDateStamp(newFolder.lastModified());
      File dest = new File(parent, name + "." + stamp);
      boolean result = newFolder.renameTo(dest);
      if (!result) {
        ProcessHelper mv;
        ProcessResult pr;
        try {
          System.err.println("Cannot rename existing " + name + " folder, resorting to mv/move instead.");
          mv = new ProcessHelper("mv", newFolder.getAbsolutePath(), dest.getAbsolutePath());
          pr = mv.execute();
        } catch (InterruptedException e) {
          e.printStackTrace();
          return null;
        }
        if (!pr.succeeded()) {
          System.err.println(pr.getStderr());
          Messages.showWarning("Failed to rename",
              "Could not rename the old \"" + name + "\" folder.\n" +
              "Please delete, close, or rename the folder\n" +
              newFolder.getAbsolutePath() + "\n" +
              "and try again." , null);
          Platform.openFolder(newFolder);
          return null;
        }
      }
    } else {
      boolean result = newFolder.mkdirs();
      if (!result) {
        Messages.showWarning("Folders, folders, folders",
            "Could not create the necessary folders to build.\n" +
            "Perhaps you have some file permissions to sort out?", null);
        return null;
      }
    }
    return newFolder;
  }

  static public void extractFolder(File file, File newPath)
    throws IOException {
    int BUFFER = 2048;
    
    ZipFile zip = new ZipFile(file);
    Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();

    // Process each entry
    while (zipFileEntries.hasMoreElements()) {
      // grab a zip file entry
      ZipEntry entry = zipFileEntries.nextElement();
      String currentEntry = entry.getName();

      File destFile = new File(newPath, currentEntry);
      //destFile = new File(newPath, destFile.getName());
      File destinationParent = destFile.getParentFile();

      // create the parent directory structure if needed
      destinationParent.mkdirs();

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
    extractFolder(wearFile, explodeDir);
    File classFile = new File(explodeDir, "classes.jar");
    Util.copyFile(classFile, jarFile);
    Util.removeDir(explodeDir);
  }
  
  static public File[] getFileList(File folder, String[] names) {
    return getFileList(folder, names, null);
  }
  
  static public File[] getFileList(File folder, String[] names, String[] altNames) {
    File[] icons = new File[names.length];
    for (int i = 0; i < names.length; i++) {
      File f = new File(folder, names[i]);
      if (!f.exists() && altNames != null) {
        f = new File(folder, altNames[i]);        
      }
      icons[i] = f;
    }
    return icons;
  }

  static public File[] getFileList(Mode mode, String prefix, String[] names) {
    File[] icons = new File[names.length];
    for (int i = 0; i < names.length; i++) {
      icons[i] = mode.getContentFile(prefix + names[i]);
    }        
    return icons;
  }
  
  static public boolean allFilesExists(File[] files) {
    for (File f: files) {
      if (!f.exists()) return false;  
    }
    return true;
  }

  static public boolean noFileExists(File[] files) {
    for (File f: files) {
      if (f.exists()) return false;  
    }
    return true;
  }
  
  static public void moveDir(File from, File to) {
    try {
      Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);      
    } catch (IOException ex) {
      ex.printStackTrace();
    }  
  }

  static public void copyDir(File from, File to) {
    final Path source = Paths.get(from.toURI());
    final Path target = Paths.get(to.toURI());

    SimpleFileVisitor copyVisitor = new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path resolve = target.resolve(source.relativize(dir));
        if (Files.notExists(resolve)) Files.createDirectories(resolve);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path resolve = target.resolve(source.relativize(file));
        Files.copy(file, resolve, StandardCopyOption.REPLACE_EXISTING);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFileFailed(Path file, IOException exc) {
        System.err.format("Unable to copy: %s: %s%n", file, exc);
        return FileVisitResult.CONTINUE;
      }
    };

    try {
      Files.walkFileTree(source, copyVisitor);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
