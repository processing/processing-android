/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-16 The Processing Foundation
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


public class Manifest {
  static final String MANIFEST_XML = "AndroidManifest.xml";

  static final String WORLD_OF_HURT_COMING =
    "Errors occurred while reading or writing " + MANIFEST_XML + ",\n" +
    "which means lots of things are likely to stop working properly.\n" +
    "To prevent losing any data, it's recommended that you use “Save As”\n" +
    "to save a separate copy of your sketch, and the restart Processing.";
  static final String MULTIPLE_ACTIVITIES =
    "Processing only supports a single Activity in the AndroidManifest.xml\n" +
    "file. Only the first activity entry will be updated, and you better \n" +
    "hope that's the right one, smartypants.";

//  private Editor editor;
  private Sketch sketch;
  
  private int appComp;

  // entries we care about from the manifest file
//  private String packageName;

  /** the manifest data read from the file */
  private XML xml;


//  public Manifest(Editor editor) {
//    this.editor = editor;
//    this.sketch = editor.getSketch();
//    load();
//  }
  public Manifest(Sketch sketch, int appComp, boolean forceNew) {
    this.sketch = sketch;
    this.appComp = appComp;
    load(forceNew);
  }


//  public void reset(int appComp) {
//    File manifestFile = getManifestFile();
//    writeBlankManifest(manifestFile, appComp);
//    try {
//      xml = new XML(manifestFile);
//    } catch (FileNotFoundException e) {
//      System.err.println("Could not read " + manifestFile.getAbsolutePath());
//      e.printStackTrace();
//    } catch (IOException e) {
//      e.printStackTrace();
//    } catch (ParserConfigurationException e) {
//      e.printStackTrace();
//    } catch (SAXException e) {
//      e.printStackTrace();
//    }    
//  }
  
  
  private String defaultPackageName() {
//    Sketch sketch = editor.getSketch();
    return AndroidBuild.basePackage + "." + sketch.getName().toLowerCase();
  }


  // called by other classes who want an actual package name
  // internally, we'll figure this out ourselves whether it's filled or not
  public String getPackageName() {
    String pkg = xml.getString("package");
    return pkg.length() == 0 ? defaultPackageName() : pkg;
  }


  public String getVersionCode() {
    String code = xml.getString("android:versionCode");
    return code.length() == 0 ? "1" : code;
  }
  
  
  public String getVersionName() {
    String name = xml.getString("android:versionName");
    return name.length() == 0 ? "1.0" : name;
  }
  
  
  public void setPackageName(String packageName) {
//    this.packageName = packageName;
    // this is the package attribute in the root <manifest> object
    xml.setString("package", packageName);
    save();
  }

  public void setSdkTarget(String version) {
    XML usesSdk = xml.getChild("uses-sdk");
    if (usesSdk != null) { 
//      usesSdk.setString("android:minSdkVersion", "15");
      usesSdk.setString("android:targetSdkVersion", version);
      save();
    }    
  }

//writer.println("  <uses-permission android:name=\"android.permission.INTERNET\" />");
//writer.println("  <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" />");
  static final String PERMISSION_PREFIX = "android.permission.";

  public String[] getPermissions() {
    XML[] elements = xml.getChildren("uses-permission");
    int count = elements.length;
    String[] names = new String[count];
    for (int i = 0; i < count; i++) {
      names[i] = elements[i].getString("android:name").substring(PERMISSION_PREFIX.length());
    }
    return names;
  }


  public void setPermissions(String[] names) {
    // just remove all the old ones
    for (XML kid : xml.getChildren("uses-permission")) {
      String name = kid.getString("android:name");
      // Don't remove required permissions for wallpapers, watchfaces and cardboard.      
      if (appComp == AndroidBuild.WALLPAPER) {
      } else if (appComp == AndroidBuild.WATCHFACE) {
        if (name.equals("android.permission.WAKE_LOCK")) continue;
      } else if (appComp == AndroidBuild.CARDBOARD) {
        if (name.equals("android.permission.INTERNET") ||
            name.equals("android.permission.NFC") ||
            name.equals("android.permission.VIBRATE") ||
            name.equals("android.permission.READ_EXTERNAL_STORAGE") ||
            name.equals("android.permission.WRITE_EXTERNAL_STORAGE")) continue;
      }      
      xml.removeChild(kid);
    }
    // ...and add the new kids back
    for (String name : names) {
//      PNode newbie = new PNodeXML("uses-permission");
//      newbie.setString("android:name", PERMISSION_PREFIX + name);
//      xml.addChild(newbie);
      XML newbie = xml.addChild("uses-permission");
      newbie.setString("android:name", PERMISSION_PREFIX + name);
    }
    save();
  }

/*
  public void setClassName(String className) {
    XML[] kids = xml.getChildren("application/activity");
    if (kids.length != 1) {
      Base.showWarning("Don't touch that", MULTIPLE_ACTIVITIES, null);
    }
    XML activity = kids[0];
    String currentName = activity.getString("android:name");
    // only update if there are changes
    if (currentName == null || !currentName.equals(className)) {
      activity.setString("android:name", "." + className);
      save();
    }
  }
*/

  // TODO: needs to be converted into a template file...
  private void writeBlankManifest(final File file, final int appComp) {
    final PrintWriter writer = PApplet.createWriter(file);
    writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
    writer.println("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" ");
//    writer.println("          package=\"" + defaultPackageName() + "\" ");
    writer.println("          package=\"\" ");
    
    // Tempting to use 'preferExternal' here, but might annoy some users.
    // 'auto' at least enables it to be moved back and forth
    // http://developer.android.com/guide/appendix/install-location.html
//    writer.println("          android:installLocation=\"auto\" ");
    // Disabling this for now (0190), requires default.properties to use API 8

    // This is just a number (like the Processing 'revision'). It should
    // increment with each release. Perhaps P5 should do this automatically
    // with each build or read/write of the manifest file?
    writer.println("          android:versionCode=\"1\" ");
    // This is the version number/name seen by users
    writer.println("          android:versionName=\"1.0\">");    
    
    // Publishing-specific features
    // for now including this... we're wiring to a particular SDK version anyway...
    
//    writer.println("  <uses-sdk android:targetSdkVersion=\"" + AndroidBuild.target_api_level + "\" />");
    if (appComp == AndroidBuild.FRAGMENT) {
      writer.println("  <uses-sdk android:minSdkVersion=\"" + AndroidBuild.min_sdk_fragment + "\" />");
    } else if (appComp == AndroidBuild.WALLPAPER) {
      writer.println("  <uses-sdk android:minSdkVersion=\"" + AndroidBuild.min_sdk_wallpaper + "\" />");
      writer.println("  <uses-feature android:name=\"android.software.live_wallpaper\" />");
    } else if (appComp == AndroidBuild.WATCHFACE) {
      writer.println("  <uses-sdk android:minSdkVersion=\"" + AndroidBuild.min_sdk_watchface + "\" />");
      writer.println("  <uses-feature android:name=\"android.hardware.type.watch\" />");
      writer.println("  <uses-permission android:name=\"com.google.android.permission.PROVIDE_BACKGROUND\" />");
      writer.println("  <uses-permission android:name=\"android.permission.WAKE_LOCK\" />");
    } else if (appComp == AndroidBuild.CARDBOARD) {
      writer.println("  <uses-sdk android:minSdkVersion=\"" + AndroidBuild.min_sdk_cardboard + "\" />");
      writer.println("  <uses-permission android:name=\"android.permission.INTERNET\" />");
      writer.println("  <uses-permission android:name=\"android.permission.NFC\" />");
      writer.println("  <uses-permission android:name=\"android.permission.VIBRATE\" />");
      writer.println("  <uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" />");
      writer.println("  <uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" />");
      writer.println("  <uses-feature android:name=\"android.hardware.sensor.accelerometer\" android:required=\"true\" />");
      writer.println("  <uses-feature android:name=\"android.hardware.sensor.gyroscope\" android:required=\"true\" />");
      writer.println("  <uses-feature android:glEsVersion=\"0x00020000\" android:required=\"true\" />");
    }
    
    writer.println("  <application android:label=\"\" ");  // insert pretty name
    writer.println("               android:icon=\"@drawable/icon\" ");
    if (appComp == AndroidBuild.WATCHFACE) {
      writer.println("               android:supportsRtl=\"true\" ");
    }
    writer.println("               android:debuggable=\"true\">");

    if (appComp == AndroidBuild.FRAGMENT) {
      // turns out label is not required for the activity, so nixing it
//    writer.println("    <activity android:name=\"\"");  // insert class name prefixed w/ dot
////    writer.println("              android:label=\"@string/app_name\">");  // pretty name
//    writer.println("              android:label=\"\">");
      
      
      // activity/android:name should be the full name (package + class name) of
      // the actual activity class. or the package can be replaced by a single
      // dot as a prefix as an easier shorthand.
      writer.println("    <activity android:name=\".MainActivity\" ");
      writer.println("              android:theme=\"@style/Theme.AppCompat.Light.NoActionBar.FullScreen\">");
      writer.println("      <intent-filter>");
      writer.println("        <action android:name=\"android.intent.action.MAIN\" />");
      writer.println("        <category android:name=\"android.intent.category.LAUNCHER\" />");
      writer.println("      </intent-filter>");
      writer.println("    </activity>"); 
    } else if (appComp == AndroidBuild.WALLPAPER) {
      writer.println("    <service android:name=\".MainService\" ");
      writer.println("             android:label=\"\" "); // insert pretty name
      writer.println("             android:permission=\"android.permission.BIND_WALLPAPER\" >");
      writer.println("      <intent-filter>");
      writer.println("         <action android:name=\"android.service.wallpaper.WallpaperService\" />");
      writer.println("      </intent-filter>");
      writer.println("    <meta-data android:name=\"android.service.wallpaper\" ");
      writer.println("               android:resource=\"@xml/wallpaper\" />");
      writer.println("  </service>");        
      writer.println("  <activity android:name=\".MainService$PermissionRequestActivity\" ></activity>");
    } else if (appComp == AndroidBuild.WATCHFACE) {
      writer.println("<service android:name=\".MainService\" ");
      writer.println("         android:label=\"\" "); // insert pretty name
      writer.println("         android:permission=\"android.permission.BIND_WALLPAPER\"> ");
      writer.println("   <meta-data android:name=\"android.service.wallpaper\" ");
      writer.println("              android:resource=\"@xml/watch_face\" /> ");
      writer.println("   <meta-data android:name=\"com.google.android.wearable.watchface.preview\" ");
      writer.println("              android:resource=\"@drawable/preview_rectangular\" /> ");
      writer.println("   <meta-data android:name=\"com.google.android.wearable.watchface.preview_circular\" ");
      writer.println("              android:resource=\"@drawable/preview_circular\" /> ");
      writer.println("   <meta-data android:name=\"com.google.android.wearable.watchface.companionConfigurationAction\" ");
      writer.println("              android:value=\"com.catinean.simpleandroidwatchface.CONFIG_DIGITAL\" /> ");
      writer.println("   <intent-filter> ");
      writer.println("     <action android:name=\"android.service.wallpaper.WallpaperService\" /> ");
      writer.println("     <category android:name=\"com.google.android.wearable.watchface.category.WATCH_FACE\" /> ");
      writer.println("   </intent-filter> ");
      writer.println("</service> ");
      writer.println("  <activity android:name=\".MainService$PermissionRequestActivity\" ></activity>");
    } else if (appComp == AndroidBuild.CARDBOARD) {
      writer.println("<activity android:name=\".MainActivity\" ");
//      writer.println("          android:label=\"\" "); // insert pretty name
      writer.println("          android:screenOrientation=\"landscape\" ");
      writer.println("          android:configChanges=\"orientation|keyboardHidden|screenSize\" > ");
      writer.println("  <intent-filter>");
      writer.println("    <action android:name=\"android.intent.action.MAIN\" /> ");
      writer.println("    <category android:name=\"android.intent.category.LAUNCHER\" /> ");
      writer.println("    <category android:name=\"com.google.intent.category.CARDBOARD\" /> ");
      writer.println("  </intent-filter>");
      writer.println("</activity>"); 
    }
    writer.println("  </application>");
    writer.println("</manifest>");
    writer.flush();
    writer.close();
  }


  /**
   * Save a new version of the manifest info to the build location.
   * Also fill in any missing attributes that aren't yet set properly.
   */
  protected void writeBuild(File file, String className,
                            boolean debug) throws IOException {
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
      
      if (appComp == AndroidBuild.WALLPAPER || appComp == AndroidBuild.WATCHFACE) {
        XML serv = app.getChild("service");
        label = serv.getString("android:label");
        if (label.length() == 0) {
          serv.setString("android:label", className);
        }       
      }
      
      app.setString("android:debuggable", debug ? "true" : "false");

//      XML activity = app.getChild("activity");
      // the '.' prefix is just an alias for the full package name
      // http://developer.android.com/guide/topics/manifest/activity-element.html#name
//      activity.setString("android:name", "." + className);  // this has to be right

      PrintWriter writer = PApplet.createWriter(file);
      writer.print(mf.format(2));
      writer.flush();
//    mf.write(writer);
      writer.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  protected void load(boolean forceNew) {
//  Sketch sketch = editor.getSketch();
//  File manifestFile = new File(sketch.getFolder(), MANIFEST_XML);
//  XMLElement xml = null;
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
    Messages.showWarning("Error handling " + MANIFEST_XML, WORLD_OF_HURT_COMING);
  }
//  return xml;
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
    writer.print(xml.format(2));
    writer.flush();
    writer.close();
  }


  private File getManifestFile() {
    return new File(sketch.getFolder(), MANIFEST_XML);
  }
}
