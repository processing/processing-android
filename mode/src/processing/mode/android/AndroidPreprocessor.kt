/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2009-12 Ben Fry and Casey Reas

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
package processing.mode.android

import processing.app.Messages
import processing.app.Sketch
import processing.app.SketchException
import processing.core.PApplet

import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.java.preproc.PdePreprocessor
import processing.mode.java.preproc.SurfaceInfo

import java.io.PrintWriter

/**
 * @author Aditya Rana
 * Processing preprocessor, supporting the Android specifics.
 */
internal class AndroidPreprocessor : PdePreprocessor {
    private var sketch: Sketch? = null
    private var packageName: String? = null
    private var smoothStatement: String? = null
    private var sketchQuality: String? = null
    private var kindStatement: String? = null
    private var sketchKind: String? = null

    constructor(sketchName: String?) : super(sketchName) {

    }

    constructor(sketch: Sketch,
                packageName: String?) : super(sketch.name) {
        this.sketch = sketch
        this.packageName = packageName
    }

    @Throws(SketchException::class)
    fun initSketchSize(code: String?): SurfaceInfo {
        val surfaceInfo = parseSketchSize(code, true)
        if (surfaceInfo == null) {
            System.err.println(getTextString("android_preprocessor.error.cannot_parse_size"))
            throw SketchException(getTextString("android_preprocessor.error.cannot_parse_size_exception"))
        }
        return surfaceInfo
    }

    @Throws(SketchException::class)
    fun initSketchSmooth(code: String?): Array<String?> {
        val info = parseSketchSmooth(code, true)

        if (info == null) {
            System.err.println(getTextString("android_preprocessor.error.cannot_smooth_size"))
            throw SketchException(getTextString("android_preprocessor.error.cannot_parse_smooth_exception"))
        }

        smoothStatement = info[0]
        sketchQuality = info[1]

        return info
    }

    override fun writeImports(out: PrintWriter,
                              programImports: List<String>,
                              codeFolderImports: List<String>): Int {
        out.println("package $packageName;")

        out.println()
        var count = 2

        count += super.writeImports(out, programImports, codeFolderImports)

        //    count += writeImportList(out, getAndroidImports());
        return count
    }

    override fun writeFooter(out: PrintWriter, className: String) {
        var info: SurfaceInfo? = null

        try {
            info = initSketchSize(sketch!!.mainProgram)
        } catch (e: SketchException) {
            e.printStackTrace()
        }

        if (info == null) {
            // Cannot get size info, just use parent's implementation.
            super.writeFooter(out, className)
        } else {

            // Same as in the parent, but without writing the main() method, which is
            // not needed in Android.
            if (mode == Mode.STATIC) {
                // close off setup() definition
                out.println(indent + indent + "noLoop();")
                out.println("$indent}")
                out.println()
            }

            if (mode == Mode.STATIC || mode == Mode.ACTIVE) {
                // doesn't remove the original size() method, but calling size()
                // again in setup() is harmless.        
                if (!hasMethod("settings") && info.hasSettings()) {
                    out.println(indent + "public void settings() { " + info.settings + " }")
                }

                // close off the class definition
                out.println("}")
            }
        }
    }

    companion object {
        const val SMOOTH_REGEX = "(?:^|\\s|;)smooth\\s*\\(\\s*([^\\s,]+)\\s*\\)\\s*\\;"

        fun parseSketchSmooth(code: String?, fussy: Boolean): Array<String?>? {
            val matches = PApplet.match(scrubComments(code), SMOOTH_REGEX)
            if (matches != null) {
                var badSmooth = false

                if (PApplet.parseInt(matches[1], -1) == -1) {
                    badSmooth = true
                }
                if (badSmooth && fussy) {
                    // found a reference to smooth, but it didn't seem to contain numbers
                    Messages.showWarning(getTextString("android_preprocessor.warn.cannot_find_smooth_level_title"),
                            getTextString("android_preprocessor.warn.cannot_find_smooth_level_body"), null)
                    return null
                }

                return matches
            }
            return arrayOf(null, null) // not an error, just empty
        }
    }

    ////////////////////////////////////////////////////////////////////////////////

    // Assorted commented out code
    //
    // As of revision 0215 (2.0b7-ish), the default imports are now identical
    // between desktop and Android (to avoid unintended incompatibilities).
    /*
  @Override
  public String[] getCoreImports() {
    return new String[] {
      "processing.core.*",
      "processing.data.*",
      "processing.event.*",
      "processing.opengl.*"
    };
  }


  @Override
  public String[] getDefaultImports() {
    final String prefsLine = Preferences.get("android.preproc.imports");
    if (prefsLine != null) {
      return PApplet.splitTokens(prefsLine, ", ");
    }

    // The initial values are stored in here for the day when Android
    // is broken out as a separate mode.

    // In the future, this may include standard classes for phone or
    // accelerometer access within the Android APIs. This is currently living
    // in code rather than preferences.txt because Android mode needs to
    // maintain its independence from the rest of processing.app.
    final String[] androidImports = new String[] {
//      "android.view.MotionEvent", "android.view.KeyEvent",
//      "android.graphics.Bitmap", //"java.awt.Image",
      "java.io.*", // for BufferedReader, InputStream, etc
      //"java.net.*", "java.text.*", // leaving otu for now
      "java.util.*" // for ArrayList and friends
      //"java.util.zip.*", "java.util.regex.*" // not necessary w/ newer i/o
    };

    Preferences.set("android.preproc.imports",
                    PApplet.join(androidImports, ","));

    return androidImports;
  }
  */
    // No need for it now
    /*
  public String[] getDefaultImports() {
//    String[] defs = super.getDefaultImports();    
//    return defs;
    return new String[] {
        "java.util.HashMap",
        "java.util.ArrayList",
        "java.io.File",
        "java.io.BufferedReader",
        "java.io.PrintWriter",
        "java.io.InputStream",
        "java.io.OutputStream",
        "java.io.IOException",
        "android.app.Activity",
        "android.app.Fragment"
      };    
  } 

  
  public String[] getAndroidImports() {
    return new String[] {
      "processing.android.ServiceEngine"
    };
  }  
  */
    /*
  protected boolean parseSketchSize() {
    // This matches against any uses of the size() function, whether numbers
    // or variables or whatever. This way, no warning is shown if size() isn't
    // actually used in the applet, which is the case especially for anyone
    // who is cutting/pasting from the reference.

    String scrubbed = processing.mode.java.JavaBuild.scrubComments(sketch.getCode(0).getProgram());
    String[] matches = PApplet.match(scrubbed, processing.mode.java.JavaBuild.SIZE_REGEX);
//    PApplet.println("matches: " + Sketch.SIZE_REGEX);
//    PApplet.println(matches);

    if (matches != null) {
      boolean badSize = false;

      if (matches[1].equals("screenWidth") ||
          matches[1].equals("screenHeight") ||
          matches[2].equals("screenWidth") ||
          matches[2].equals("screenHeight")) {
        final String message =
          "The screenWidth and screenHeight variables are named\n" +
          "displayWidth and displayHeight in this release of Processing.";
        Base.showWarning("Time for a quick update", message, null);
        return false;
      }

      if (!matches[1].equals("displayWidth") &&
          !matches[1].equals("displayHeight") &&
          PApplet.parseInt(matches[1], -1) == -1) {
        badSize = true;
      }
      if (!matches[2].equals("displayWidth") &&
          !matches[2].equals("displayHeight") &&
          PApplet.parseInt(matches[2], -1) == -1) {
        badSize = true;
      }

      if (badSize) {
        // found a reference to size, but it didn't seem to contain numbers
        final String message =
          "The size of this applet could not automatically be determined\n" +
          "from your code. Use only numeric values (not variables) for the\n" +
          "size() command. See the size() reference for more information.";
        Base.showWarning("Could not find sketch size", message, null);
        System.out.println("More about the size() command on Android can be");
        System.out.println("found here: http://wiki.processing.org/w/Android");
        return false;
      }

//      PApplet.println(matches);
      sizeStatement = matches[0];  // the full method to be removed from the source
      sketchWidth = matches[1];
      sketchHeight = matches[2];
      sketchRenderer = matches[3].trim();
      if (sketchRenderer.length() == 0) {
        sketchRenderer = null;
      }
    } else {
      sizeStatement = null;
      sketchWidth = null;
      sketchHeight = null;
      sketchRenderer = null;
    }
    return true;
  }
  */
    /*
  public PreprocessorResult write(Writer out, String program, String[] codeFolderPackages)
  throws SketchException, RecognitionException, TokenStreamException {
    if (sizeStatement != null) {
      int start = program.indexOf(sizeStatement);
      program = program.substring(0, start) +
      program.substring(start + sizeStatement.length());
    }
    // the OpenGL package is back in 2.0a5
    //program = program.replaceAll("import\\s+processing\\.opengl\\.\\S+;", "");
    return super.write(out, program, codeFolderPackages);
  }
  */

}