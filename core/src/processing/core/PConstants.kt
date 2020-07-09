/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core

import android.view.KeyEvent

//import java.awt.Cursor;
//import java.awt.event.KeyEvent;

/**
 * Numbers shared throughout processing.core.
 * <P>
 * An attempt is made to keep the constants as short/non-verbose
 * as possible. For instance, the constant is TIFF instead of
 * FILE_TYPE_TIFF. We'll do this as long as we can get away with it.
</P> */
interface PConstants {
    companion object {

        // vertex fields
        const val X = 0 // model coords xyz (formerly MX/MY/MZ)
        const val Y = 1
        const val Z = 2

        // built-in rendering options
        const val JAVA2D    = "processing.core.PGraphicsAndroid2D"
        const val P2D       = "processing.opengl.PGraphics2D"
        const val P2DX      = "processing.opengl.PGraphics2DX"
        const val P3D       = "processing.opengl.PGraphics3D"
        const val OPENGL    = P3D
        const val STEREO    = "processing.vr.VRGraphicsStereo"
        const val MONO      = "processing.vr.VRGraphicsMono"
        const val VR        = STEREO
        const val AR        = "processing.ar.ARGraphics"
        const val ARCORE    = AR

        // The PDF and DXF renderers are not available for Android.
        // platform IDs for PApplet.platform

        const val OTHER     = 0
        const val WINDOWS   = 1
        const val MACOSX    = 2
        const val LINUX     = 3

        val platformNames = arrayOf(
                "other", "windows", "macosx", "linux"
        )

        const val EPSILON = 0.0001f
        // max/min values for numbers

        /**
         * Same as Float.MAX_VALUE, but included for parity with MIN_VALUE,
         * and to avoid teaching static methods on the first day.
         */
        val MAX_FLOAT = Float.MAX_VALUE

        /**
         * Note that Float.MIN_VALUE is the smallest <EM>positive</EM> value
         * for a floating point number, not actually the minimum (negative) value
         * for a float. This constant equals 0xFF7FFFFF, the smallest (farthest
         * negative) value a float can have before it hits NaN.
         */
        val MIN_FLOAT = -Float.MAX_VALUE

        /** Largest possible (positive) integer value  */
        const val MAX_INT = Int.MAX_VALUE

        /** Smallest possible (negative) integer value  */
        const val MIN_INT = Int.MIN_VALUE

        // shapes
        const val VERTEX            = 0
        const val BEZIER_VERTEX     = 1
        const val QUADRATIC_VERTEX  = 2
        const val CURVE_VERTEX      = 3
        const val BREAK             = 4

        @Deprecated("")
        val QUAD_BEZIER_VERTEX  = 2 // should not have been exposed

        // useful goodness
        const val PI            = Math.PI.toFloat()
        const val HALF_PI       = PI / 2.0f
        const val THIRD_PI      = PI / 3.0f
        const val QUARTER_PI    = PI / 4.0f
        const val TWO_PI        = PI * 2.0f
        const val TAU           = PI * 2.0f
        const val DEG_TO_RAD    = PI / 180.0f
        const val RAD_TO_DEG    = 180.0f / PI

        // angle modes
        //static final int RADIANS = 0;
        //static final int DEGREES = 1;
        // used by split, all the standard whitespace chars
        // (also includes unicode nbsp, that little bostage)

        // using encoding here for formfeed character because kotlin doesnot support it.
        // valid escape charcters are  \t, \b, \n, \r, \', \", \\ and \$ and rest need to be used as unicodes
        // https://kotlinlang.org/docs/reference/basic-types.html#characters

        const val WHITESPACE = " \t\n\r\u000C\u00A0"

        // for colors and/or images

        const val RGB       = 1 // image & color
        const val ARGB      = 2 // image
        const val HSB       = 3 // color
        const val ALPHA     = 4 // image
        const val CMYK      = 5 // image & color (someday)
        const val YUV420    = 6 // Android video preview.

        // image file types
        const val TIFF      = 0
        const val TARGA     = 1
        const val JPEG      = 2
        const val GIF       = 3

        // filter/convert types
        const val BLUR          = 11
        const val GRAY          = 12
        const val INVERT        = 13
        const val OPAQUE        = 14
        const val POSTERIZE     = 15
        const val THRESHOLD     = 16
        const val ERODE         = 17
        const val DILATE        = 18

        // blend mode keyword definitions
        // @see processing.core.PImage#blendColor(int,int,int)
        const val REPLACE       = 0
        const val BLEND         = 1 shl 0
        const val ADD           = 1 shl 1
        const val SUBTRACT      = 1 shl 2
        const val LIGHTEST      = 1 shl 3
        const val DARKEST       = 1 shl 4
        const val DIFFERENCE    = 1 shl 5
        const val EXCLUSION     = 1 shl 6
        const val MULTIPLY      = 1 shl 7
        const val SCREEN        = 1 shl 8
        const val OVERLAY       = 1 shl 9
        const val HARD_LIGHT    = 1 shl 10
        const val SOFT_LIGHT    = 1 shl 11
        const val DODGE         = 1 shl 12
        const val BURN          = 1 shl 13

        // for messages
        const val CHATTER    = 0
        const val COMPLAINT  = 1
        const val PROBLEM    = 2

        // types of transformation matrices
        const val PROJECTION = 0
        const val MODELVIEW  = 1

        // types of projection matrices
        const val CUSTOM        = 0 // user-specified fanciness
        const val ORTHOGRAPHIC  = 2 // 2D isometric projection
        const val PERSPECTIVE   = 3 // perspective matrix

        // shapes

        // the low four bits set the variety,
        // higher bits set the specific shape type

        const val GROUP            = 0 // createShape()

        const val POINT            = 2 // primitive
        const val POINTS           = 3 // vertices

        const val LINE             = 4 // primitive
        const val LINES            = 5 // beginShape(), createShape()
        const val LINE_STRIP       = 50 // beginShape()
        const val LINE_LOOP        = 51

        const val TRIANGLE         = 8 // primitive
        const val TRIANGLES        = 9 // vertices
        const val TRIANGLE_STRIP   = 10 // vertices
        const val TRIANGLE_FAN     = 11 // vertices

        const val QUAD             = 16 // primitive
        const val QUADS            = 17 // vertices
        const val QUAD_STRIP       = 18 // vertices

        const val POLYGON          = 20 // in the end, probably cannot
        const val PATH             = 21 // separate these two

        const val RECT             = 30 // primitive
        const val ELLIPSE          = 31 // primitive
        const val ARC              = 32 // primitive

        const val SPHERE           = 40 // primitive
        const val BOX              = 41 // primitive

        //  static public final int LINE_STRIP    = 50;
        //  static public final int LINE_LOOP     = 51;
        //  static public final int POINT_SPRITES = 52;
        // shape closing modes
        const val OPEN  = 1
        const val CLOSE = 2

        // shape drawing modes

        /** Draw mode convention to use (x, y) to (width, height)  */
        const val CORNER = 0

        /** Draw mode convention to use (x1, y1) to (x2, y2) coordinates  */
        const val CORNERS = 1

        /** Draw mode from the center, and using the radius  */
        const val RADIUS = 2

        @Deprecated("Use RADIUS instead. ")
        val CENTER_RADIUS = 2

        /**
         * Draw from the center, using second pair of values as the diameter.
         * Formerly called CENTER_DIAMETER in alpha releases.
         */
        const val CENTER = 3

        /**
         * Synonym for the CENTER constant. Draw from the center,
         * using second pair of values as the diameter.
         */
        const val DIAMETER = 3

        @Deprecated("Use DIAMETER instead. ")
        val CENTER_DIAMETER = 3

        // arc drawing modes
        //static final int OPEN = 1;  // shared
        const val CHORD = 2
        const val PIE = 3

        // vertically alignment modes for text

        /** Default vertical alignment for text placement  */
        const val BASELINE = 0

        /** Align text to the top  */
        const val TOP = 101

        /** Align text from the bottom, using the baseline.  */
        const val BOTTOM = 102

        // uv texture orientation modes

        /** texture coordinates in 0..1 range  */
        const val NORMAL = 1

        /** texture coordinates based on image width/height  */
        const val IMAGE = 2

        // texture wrapping modes

        /** textures are clamped to their edges  */
        const val CLAMP = 0

        /** textures wrap around when uv values go outside 0..1 range  */
        const val REPEAT = 1

        // text placement modes

        /**
         * textMode(MODEL) is the default, meaning that characters
         * will be affected by transformations like any other shapes.
         *
         *
         * Changed value in 0093 to not interfere with LEFT, CENTER, and RIGHT.
         */
        const val MODEL = 4

        /**
         * textMode(SHAPE) draws text using the the glyph outlines of
         * individual characters rather than as textures. If the outlines are
         * not available, then textMode(SHAPE) will be ignored and textMode(MODEL)
         * will be used instead. For this reason, be sure to call textMode()
         * <EM>after</EM> calling textFont().
         *
         *
         * Currently, textMode(SHAPE) is only supported by OPENGL mode.
         * It also requires Java 1.2 or higher (OPENGL requires 1.4 anyway)
         */
        const val SHAPE = 5

        // text alignment modes
        // are inherited from LEFT, CENTER, RIGHT
        // stroke modes
        const val SQUARE  = 1 shl 0     // called 'butt' in the svg spec
        const val ROUND   = 1 shl 1
        const val PROJECT = 1 shl 2    // called 'square' in the svg spec
        const val MITER   = 1 shl 3
        const val BEVEL   = 1 shl 5

        // lighting
        const val AMBIENT = 0
        const val DIRECTIONAL = 1

        //static final int POINT  = 2;  // shared with shape feature
        const val SPOT = 3

        // key constants
        // only including the most-used of these guys
        // if people need more esoteric keys, they can learn about
        // the esoteric java KeyEvent api and of virtual keys
        // both key and keyCode will equal these values
        // for 0125, these were changed to 'char' values, because they
        // can be upgraded to ints automatically by Java, but having them
        // as ints prevented split(blah, TAB) from working

        const val BACKSPACE = KeyEvent.KEYCODE_DEL.toChar()
        const val TAB       = KeyEvent.KEYCODE_TAB.toChar()
        const val ENTER     = KeyEvent.KEYCODE_ENTER.toChar()
        const val RETURN    = KeyEvent.KEYCODE_ENTER.toChar()
        const val ESC       = KeyEvent.KEYCODE_ESCAPE.toChar()
        const val DELETE    = KeyEvent.KEYCODE_DEL.toChar()

        // i.e. if ((key == CODED) && (keyCode == UP))
        const val CODED = 0xffff

        // key will be CODED and keyCode will be this value
        const val UP    = KeyEvent.KEYCODE_DPAD_UP
        const val DOWN  = KeyEvent.KEYCODE_DPAD_DOWN
        const val LEFT  = KeyEvent.KEYCODE_DPAD_LEFT
        const val RIGHT = KeyEvent.KEYCODE_DPAD_RIGHT

        // These seem essential for most sketches, so they're included.
        // Others can be found in the KeyEvent reference:
        // http://developer.android.com/reference/android/view/KeyEvent.html
        const val BACK = KeyEvent.KEYCODE_BACK
        const val MENU = KeyEvent.KEYCODE_MENU
        const val DPAD = KeyEvent.KEYCODE_DPAD_CENTER

        // key will be CODED and keyCode will be this value
        //  static final int ALT       = KeyEvent.VK_ALT;
        //  static final int CONTROL   = KeyEvent.VK_CONTROL;

        const val SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT

        // cursor types
        //  static final int ARROW = Cursor.DEFAULT_CURSOR;
        //  static final int CROSS = Cursor.CROSSHAIR_CURSOR;
        //  static final int HAND  = Cursor.HAND_CURSOR;
        //  static final int MOVE  = Cursor.MOVE_CURSOR;
        //  static final int TEXT  = Cursor.TEXT_CURSOR;
        //  static final int WAIT  = Cursor.WAIT_CURSOR;

        /** Screen orientation constant for portrait (the hamburger way).  */
        const val PORTRAIT = 1

        /** Screen orientation constant for landscape (the hot dog way).  */
        const val LANDSCAPE = 2

        // hints - hint values are positive for the alternate version,
        // negative of the same value returns to the normal/default state

        @Deprecated("")
        const val ENABLE_NATIVE_FONTS           =  1

        @Deprecated("")
        const val DISABLE_NATIVE_FONTS          = -1

        const val DISABLE_DEPTH_TEST            =  2
        const val ENABLE_DEPTH_TEST             = -2

        const val ENABLE_DEPTH_SORT             = 3
        const val DISABLE_DEPTH_SORT            = -3

        const val DISABLE_OPENGL_ERRORS         =  4
        const val ENABLE_OPENGL_ERRORS          = -4

        const val DISABLE_DEPTH_MASK            =  5
        const val ENABLE_DEPTH_MASK             = -5

        const val DISABLE_OPTIMIZED_STROKE      =  6
        const val ENABLE_OPTIMIZED_STROKE       = -6

        const val ENABLE_STROKE_PERSPECTIVE     =  7
        const val DISABLE_STROKE_PERSPECTIVE    = -7

        const val DISABLE_TEXTURE_MIPMAPS       =  8
        const val ENABLE_TEXTURE_MIPMAPS        = -8

        const val ENABLE_STROKE_PURE            =  9
        const val DISABLE_STROKE_PURE           = -9

        const val ENABLE_BUFFER_READING         =  10
        const val DISABLE_BUFFER_READING        = -10

        const val DISABLE_KEY_REPEAT            =  11
        const val ENABLE_KEY_REPEAT             = -11

        const val DISABLE_ASYNC_SAVEFRAME       =  12
        const val ENABLE_ASYNC_SAVEFRAME        = -12

        const val HINT_COUNT                    =  13

        // error messages

        const val ERROR_BACKGROUND_IMAGE_SIZE   = "background image must be the same size as your application"
        const val ERROR_BACKGROUND_IMAGE_FORMAT = "background images should be RGB or ARGB"
        const val ERROR_TEXTFONT_NULL_PFONT     = "A null PFont was passed to textFont()"
        const val ERROR_PUSHMATRIX_OVERFLOW     = "Too many calls to pushMatrix()."
        const val ERROR_PUSHMATRIX_UNDERFLOW    = "Too many calls to popMatrix(), and not enough to pushMatrix()."

        // Some currently missing GLES constants.

        //  static final int GL_MIN_EXT = 0x8007;
        //  static final int GL_MAX_EXT = 0x8008;
    }
}