/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2010-12 Ben Fry and Casey Reas

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

import org.xml.sax.SAXException;
import processing.app.Messages;
import processing.app.Sketch;
import processing.core.PApplet;
import processing.data.XML;

import javax.xml.parsers.ParserConfigurationException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

/**
 * Class encapsulating the manifest file associated with a Processing sketch
 * in the Android mode.
 *
 */
public class Manifest {
  static final String MANIFEST_XML = "AndroidManifest.xml";

  static final String MANIFEST_ERROR_TITLE = "Error handling " + MANIFEST_XML;
  static final String MANIFEST_ERROR_MESSAGE =
    "Errors occurred while reading or writing " + MANIFEST_XML + ",\n" +
    "which means lots of things are likely to stop working properly.\n" +
    "To prevent losing any data, it's recommended that you use “Save As”\n" +
    "to save a separate copy of your sketch, and then restart Processing.";
  
  static private final String[] MANIFEST_TEMPLATE = {
    "AppManifest.xml.tmpl",
    "WallpaperManifest.xml.tmpl",
    "WatchFaceManifest.xml.tmpl",
    "VRManifest.xml.tmpl",
    "VRManifest.xml.tmpl",
    "ARManifest.xml.tmpl"
  };
  
  // Default base package name, user need to change when exporting package. 
  static final String BASE_PACKAGE = "processing.test";  
  
  static final String PERMISSION_PREFIX = "android.permission.";  
  
  private Sketch sketch;  
  private int appComp;  
  private File modeFolder;

  /** the manifest data read from the file */
  private XML xml;


  public Manifest(Sketch sketch, int appComp, File modeFolder, boolean forceNew) {
    this.sketch = sketch;
    this.appComp = appComp;
    this.modeFolder = modeFolder;
    load(forceNew);
  }


  private String defaultPackageName() {
    return BASE_PACKAGE + "." + sketch.getName().toLowerCase();
  }

  
  private String defaultVersionCode() {
    return "1";
  }

  
  private String defaultVersionName() {
    return "1.0";
  }
  

  // called by other classes who want an actual package name
  // internally, we'll figure this out ourselves whether it's filled or not
  public String getPackageName() {
    String pkg = xml.getString("package");
    return pkg.length() == 0 ? defaultPackageName() : pkg;
  }


  public String getVersionCode() {
    String code = xml.getString("android:versionCode");
    return code.length() == 0 ? defaultVersionCode() : code;
  }
  
  
  public String getVersionName() {
    String name = xml.getString("android:versionName");
    return name.length() == 0 ? defaultVersionName() : name;
  }
  
  
  public void setPackageName(String packageName) {
    xml.setString("package", packageName);
    save();
  }

  
  public void setSdkTarget(String version) {
    XML usesSdk = xml.getChild("uses-sdk");
    if (usesSdk != null) { 
      usesSdk.setString("android:targetSdkVersion", version);
      save();
    }    
  }


  public String[] getPermissions() {
    XML[] elements = xml.getChildren("uses-permission");
    int count = elements.length;
    String[] names = new String[count];
    for (int i = 0; i < count; i++) {
      String tmp = elements[i].getString("android:name");
      if (tmp.indexOf("android.permission") == 0) {
        // Standard permission, remove perfix
        int idx = tmp.lastIndexOf(".");
        names[i] = tmp.substring(idx + 1);        
      } else {
        // Non-standard permission (for example, wearables)
        // Store entire name.
        names[i] = tmp;
      }
    }
    return names;
  }


  public void setPermissions(String[] names) {
    boolean hasWakeLock = false;
    boolean hasVibrate = false;
    boolean hasReadExtStorage = false;
    boolean hasCameraAccess = false;
    
    // Remove all the old permissions...
    for (XML kid : xml.getChildren("uses-permission")) {
      String name = kid.getString("android:name");
      
      // ...except the ones for watch faces and VR apps.   
      if (appComp == AndroidBuild.WATCHFACE && name.equals(PERMISSION_PREFIX + "WAKE_LOCK")) {
        hasWakeLock = true;
        continue;
      }
      if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && name.equals(PERMISSION_PREFIX + "VIBRATE")) {
        hasVibrate = true;
        continue;
      }
      if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && name.equals(PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE")) {
        hasReadExtStorage = true;
        continue;
      }
      if(appComp == AndroidBuild.AR && name.equals(PERMISSION_PREFIX + "CAMERA")){
        hasCameraAccess = true;
        continue;
      }
      
      // Don't remove non-standard permissions, such as
      // com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA
      // because these are set manually by the user.
      if (-1 < name.indexOf("com.google.android")) continue;
      xml.removeChild(kid);
    }
    
    // ...and add the new permissions back
    for (String name : names) {
      
      // Don't add required permissions for watch faces and VR again...
      if (appComp == AndroidBuild.WATCHFACE && name.equals("WAKE_LOCK")) continue;
      if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && name.equals("VIBRATE")) continue;
      if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && name.equals("READ_EXTERNAL_STORAGE")) continue;
      if (appComp == AndroidBuild.AR && name.equals(PERMISSION_PREFIX + "CAMERA")) continue;
         
      XML newbie = xml.addChild("uses-permission");
      if (-1 < name.indexOf(".")) {
        // Permission string contains path
        newbie.setString("android:name", name);
      } else {
        newbie.setString("android:name", PERMISSION_PREFIX + name);
      }
    }

    // ...unless they were initially missing.
    if (appComp == AndroidBuild.WATCHFACE && !hasWakeLock) {
      xml.addChild("uses-permission").
          setString("android:name", PERMISSION_PREFIX + "WAKE_LOCK");
    }
    if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && !hasVibrate) {
      xml.addChild("uses-permission").
          setString("android:name", PERMISSION_PREFIX + "VIBRATE");      
    }
    if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && !hasReadExtStorage) {
      xml.addChild("uses-permission").
          setString("android:name", PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE");       
    }
    if (appComp == AndroidBuild.AR && !hasCameraAccess) {
      xml.addChild("uses-permission").
              setString("android:name", PERMISSION_PREFIX + "CAMERA");
    }
    
    save();
  }

  
  private void fixPermissions(XML mf) {
    boolean hasWakeLock = false;
    boolean hasVibrate = false;
    boolean hasReadExtStorage = false;
    boolean hasCameraAccess = false;
    for (XML kid : mf.getChildren("uses-permission")) {
      String name = kid.getString("android:name");
      if (appComp == AndroidBuild.WATCHFACE && name.equals(PERMISSION_PREFIX + "WAKE_LOCK")) {
        hasWakeLock = true;
        continue;
      }
      if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && name.equals(PERMISSION_PREFIX + "VIBRATE")) {
        hasVibrate = true;
        continue;
      }
      if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && name.equals(PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE")) {
        hasReadExtStorage = true;
        continue;
      }
      if (appComp == AndroidBuild.AR && name.equals(PERMISSION_PREFIX + "CAMERA")){
        hasCameraAccess = true;
        continue;
      }

      if (appComp == AndroidBuild.AR && !hasCameraAccess) {
        mf.addChild("uses-permission").
                setString("android:name", PERMISSION_PREFIX + "CAMERA");
      }
    }
    if (appComp == AndroidBuild.WATCHFACE && !hasWakeLock) {
      mf.addChild("uses-permission").
         setString("android:name", PERMISSION_PREFIX + "WAKE_LOCK");
    }
    if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && !hasVibrate) {
      mf.addChild("uses-permission").
         setString("android:name", PERMISSION_PREFIX + "VIBRATE");      
    }
    if ((appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD) && !hasReadExtStorage) {
      mf.addChild("uses-permission").
         setString("android:name", PERMISSION_PREFIX + "READ_EXTERNAL_STORAGE");       
    }    
  }
  

  private void writeBlankManifest(final File xmlFile, final int appComp) {
    File xmlTemplate = new File(modeFolder, "templates/" + MANIFEST_TEMPLATE[appComp]);
    
    HashMap<String, String> replaceMap = new HashMap<String, String>();    
    if (appComp == AndroidBuild.APP) {
      replaceMap.put("@@min_sdk@@", AndroidBuild.MIN_SDK_APP);
    } else if (appComp == AndroidBuild.WALLPAPER) {
      replaceMap.put("@@min_sdk@@", AndroidBuild.MIN_SDK_WALLPAPER);
    } else if (appComp == AndroidBuild.WATCHFACE) {
      replaceMap.put("@@min_sdk@@", AndroidBuild.MIN_SDK_WATCHFACE);
    } else if (appComp == AndroidBuild.VR_CARDBOARD) {
      replaceMap.put("@@min_sdk@@", AndroidBuild.MIN_SDK_VR);
      replaceMap.put("@@vr_type@@", "com.google.intent.category.CARDBOARD");
    } else if (appComp == AndroidBuild.VR_DAYDREAM) {
      replaceMap.put("@@min_sdk@@", AndroidBuild.MIN_SDK_VR);
      replaceMap.put("@@vr_type@@", "com.google.intent.category.DAYDREAM");
    } else if (appComp == AndroidBuild.AR) {
      replaceMap.put("@@min_sdk@@", AndroidBuild.MIN_SDK_AR);
    }
        
    AndroidUtil.createFileFromTemplate(xmlTemplate, xmlFile, replaceMap);     
  }


  /**
   * Save a new version of the manifest info to the build location.
   * Also fill in any missing attributes that aren't yet set properly.
   */
  protected void writeCopy(File file, String className) throws IOException {
    // write a copy to the build location
    save(file);

    // load the copy from the build location and start messing with it
    XML mf = null;
    try {
      mf = new XML(file);

      // package name, or default
      String p = mf.getString("package").trim();
      if (p.length() == 0) {
        mf.setString("package", defaultPackageName());
      }

      // app name and label, or the class name
      XML app = mf.getChild("application");
      String label = app.getString("android:label");
      if (label.length() == 0) {
        app.setString("android:label", className);
      }      
      
      // Services need the label also in the service section
      if (appComp == AndroidBuild.WALLPAPER || appComp == AndroidBuild.WATCHFACE) {
        XML serv = app.getChild("service");
        label = serv.getString("android:label");
        if (label.length() == 0) {
          serv.setString("android:label", className);
        }       
      }
      
      // Make sure that the required permissions for watch faces, AR and VR apps are
      // included. 
      if (appComp == AndroidBuild.WATCHFACE || appComp == AndroidBuild.VR_DAYDREAM || appComp == AndroidBuild.VR_CARDBOARD || appComp == AndroidBuild.AR) {
        fixPermissions(mf);
      }

      PrintWriter writer = PApplet.createWriter(file);
      writer.print(mf.format(4));
      writer.flush();
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  protected void load(boolean forceNew) { 
    File manifestFile = getManifestFile();
    if (manifestFile.exists()) {
      try {
        xml = new XML(manifestFile);
      } catch (Exception e) {
        e.printStackTrace();
        System.err.println("Problem reading AndroidManifest.xml, creating a new version");

        // remove the old manifest file, rename it with date stamp
        long lastModified = manifestFile.lastModified();
        String stamp = AndroidMode.getDateStamp(lastModified);
        File dest = new File(sketch.getFolder(), MANIFEST_XML + "." + stamp);
        boolean moved = manifestFile.renameTo(dest);
        if (!moved) {
          System.err.println("Could not move/rename " + manifestFile.getAbsolutePath());
          System.err.println("You'll have to move or remove it before continuing.");
          return;
        }
      }
    }
    
    String[] permissionNames = null;
    String pkgName = null;
    String versionCode = null;
    String versionName = null;
    if (xml != null && forceNew) {
      permissionNames = getPermissions();
      pkgName = getPackageName();
      versionCode = getVersionCode();
      versionName = getVersionName();
      xml = null;
    }

    if (xml == null) {
      writeBlankManifest(manifestFile, appComp);
      try {
        xml = new XML(manifestFile);
        if (permissionNames != null) {
          setPermissions(permissionNames);
        }
        if (pkgName != null) {
          xml.setString("package", pkgName);
        }
        if (versionCode != null) {
          xml.setString("android:versionCode", versionCode);
        }
        if (versionName != null) {
          xml.setString("android:versionName", versionName);
        }       
      } catch (FileNotFoundException e) {
        System.err.println("Could not read " + manifestFile.getAbsolutePath());
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } catch (ParserConfigurationException e) {
        e.printStackTrace();
      } catch (SAXException e) {
        e.printStackTrace();
      }
    }
    if (xml == null) {
      Messages.showWarning(AndroidMode.getTextString("manifest.warn.cannot_handle_file_title", MANIFEST_XML), 
                           AndroidMode.getTextString("manifest.warn.cannot_handle_file_body", MANIFEST_XML));
    }
  }

  protected void save() {
    save(getManifestFile());
  }


  /**
   * Save to the sketch folder, so that it can be copied in later.
   */
  protected void save(File file) {
    PrintWriter writer = PApplet.createWriter(file);
//    xml.write(writer);
    writer.print(xml.format(4));
    writer.flush();
    writer.close();
  }


  private File getManifestFile() {
    return new File(sketch.getFolder(), MANIFEST_XML);
  }
}
