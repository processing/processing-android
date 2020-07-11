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

import android.graphics.Color
import android.view.SurfaceHolder
import processing.android.AppComponent
import processing.opengl.PGL
import processing.opengl.PShader
import java.util.*
import java.util.concurrent.*

/**
 * Main graphics and rendering context, as well as the base API implementation.
 *
 * <h2>Subclassing and initializing PGraphics objects</h2>
 * Starting in release 0149, subclasses of PGraphics are handled differently.
 * The constructor for subclasses takes no parameters, instead a series of
 * functions are called by the hosting PApplet to specify its attributes.
 *
 *  * setParent(PApplet) - is called to specify the parent PApplet.
 *  * setPrimary(boolean) - called with true if this PGraphics will be the
 * primary drawing surface used by the sketch, or false if not.
 *  * setPath(String) - called when the renderer needs a filename or output
 * path, such as with the PDF or DXF renderers.
 *  * setSize(int, int) - this is called last, at which point it's safe for
 * the renderer to complete its initialization routine.
 *
 * The functions were broken out because of the growing number of parameters
 * such as these that might be used by a renderer, yet with the exception of
 * setSize(), it's not clear which will be necessary. So while the size could
 * be passed in to the constructor instead of a setSize() function, a function
 * would still be needed that would notify the renderer that it was time to
 * finish its initialization. Thus, setSize() simply does both.
 *
 * <h2>Know your rights: public vs. private methods</h2>
 * Methods that are protected are often subclassed by other renderers, however
 * they are not set 'public' because they shouldn't be part of the user-facing
 * public API accessible from PApplet. That is, we don't want sketches calling
 * textModeCheck() or vertexTexture() directly.
 *
 * <h2>Handling warnings and exceptions</h2>
 * Methods that are unavailable generally show a warning, unless their lack of
 * availability will soon cause another exception. For instance, if a method
 * like getMatrix() returns null because it is unavailable, an exception will
 * be thrown stating that the method is unavailable, rather than waiting for
 * the NullPointerException that will occur when the sketch tries to use that
 * method. As of release 0149, warnings will only be shown once, and exceptions
 * have been changed to warnings where possible.
 *
 * <h2>Using xxxxImpl() for subclassing smoothness</h2>
 * The xxxImpl() methods are generally renderer-specific handling for some
 * subset if tasks for a particular function (vague enough for you?) For
 * instance, imageImpl() handles drawing an image whose x/y/w/h and u/v coords
 * have been specified, and screen placement (independent of imageMode) has
 * been determined. There's no point in all renderers implementing the
 * <tt>if (imageMode == BLAH)</tt> placement/sizing logic, so that's handled
 * by PGraphics, which then calls imageImpl() once all that is figured out.
 *
 * <h2>His brother PImage</h2>
 * PGraphics subclasses PImage so that it can be drawn and manipulated in a
 * similar fashion. As such, many methods are inherited from PGraphics,
 * though many are unavailable: for instance, resize() is not likely to be
 * implemented; the same goes for mask(), depending on the situation.
 *
 * <h2>What's in PGraphics, what ain't</h2>
 * For the benefit of subclasses, as much as possible has been placed inside
 * PGraphics. For instance, bezier interpolation code and implementations of
 * the strokeCap() method (that simply sets the strokeCap variable) are
 * handled here. Features that will vary widely between renderers are located
 * inside the subclasses themselves. For instance, all matrix handling code
 * is per-renderer: Java 2D uses its own AffineTransform, P2D uses a PMatrix2D,
 * and PGraphics3D needs to keep continually update forward and reverse
 * transformations. A proper (future) OpenGL implementation will have all its
 * matrix madness handled by the card. Lighting also falls under this
 * category, however the base material property settings (emissive, specular,
 * et al.) are handled in PGraphics because they use the standard colorMode()
 * logic. Subclasses should override methods like emissiveFromCalc(), which
 * is a point where a valid color has been defined internally, and can be
 * applied in some manner based on the calcXxxx values.
 *
 * <h2>What's in the PGraphics documentation, what ain't</h2>
 * Some things are noted here, some things are not. For public API, always
 * refer to the [reference](http://processing.org/reference)
 * on Processing.org for proper explanations. **No attempt has been made to
 * keep the javadoc up to date or complete.** It's an enormous task for
 * which we simply do not have the time. That is, it's not something that
 * to be done onceit's a matter of keeping the multiple references
 * synchronized (to say nothing of the translation issues), while targeting
 * them for their separate audiences. Ouch.
 */
open class PGraphics: PImage(), PConstants {
    // ........................................................
    // width and height are already inherited from PImage
    //  /// width minus one (useful for many calculations)
    //  protected int width1;
    //
    //  /// height minus one (useful for many calculations)
    //  protected int height1;
    /// width * height (useful for many calculations)
    @JvmField
    var pixelCount = 0

    /// true if smoothing is enabled (read-only)
    @JvmField
    var smooth = 0

    // ........................................................

    /// true if defaults() has been called a first time

    @JvmField
    protected var settingsInited = false

    /// true if settings should be re-applied on next beginDraw()
    @JvmField
    protected var reapplySettings = false

    // ignore
    /// set to a PGraphics object being used inside a beginRaw/endRaw() block

    @JvmField
    var raw: PGraphics? = null


    // ........................................................


    /** path to the file being saved for this renderer (if any)  */
    @JvmField
    protected var path: String? = null

    /**
     * true if this is the main drawing surface for a particular sketch.
     * This would be set to false for an offscreen buffer or if it were
     * created any other way than size(). When this is set, the listeners
     * are also added to the sketch.
     */
    @JvmField
    protected var primaryGraphics = false


    // ........................................................


    /**
     * Array of hint[] items. These are hacks to get around various
     * temporary workarounds inside the environment.
     *
     *
     * Note that this array cannot be static, as a hint() may result in a
     * runtime change specific to a renderer. For instance, calling
     * hint(DISABLE_DEPTH_TEST) has to call glDisable() right away on an
     * instance of PGraphicsOpenGL.
     *
     *
     * The hints[] array is allocated early on because it might
     * be used inside beginDraw(), allocate(), etc.
     */
    @JvmField
    protected var hints = BooleanArray(PConstants.HINT_COUNT)


    // ........................................................

    /**
     * Storage for renderer-specific image data. In 1.x, renderers wrote cache
     * data into the image object. In 2.x, the renderer has a weak-referenced
     * map that points at any of the images it has worked on already. When the
     * images go out of scope, they will be properly garbage collected.
     */
    @JvmField
    protected var cacheMap = WeakHashMap<PImage, Any>()


    ////////////////////////////////////////////////////////////

    // STYLE PROPERTIES

    // Also inherits imageMode() and smooth() (among others) from PImage.

    /** The current colorMode  */
    @JvmField
    var colorMode = 0 // = RGB; = 0

    /** Max value for red (or hue) set by colorMode  */
    @JvmField
    var colorModeX = 0f // = 255; = 0f

    /** Max value for green (or saturation) set by colorMode  */
    @JvmField
    var colorModeY = 0f // = 255; = 0f

    /** Max value for blue (or value) set by colorMode  */
    @JvmField
    var colorModeZ = 0f// = 255; = 0f

    /** Max value for alpha set by colorMode  */
    @JvmField
    var colorModeA = 0f // = 255; = 0f

    /** True if colors are not in the range 0..1  */
    @JvmField
    var colorModeScale: Boolean = false // = true; = false

    /** True if colorMode(RGB, 255)  */
    @JvmField
    var colorModeDefault: Boolean = false // = true; = false


    // ........................................................

    // Tint color for images

    /**
     * True if tint() is enabled (read-only).
     *
     * Using tint/tintColor seems a better option for naming than
     * tintEnabled/tint because the latter seems ugly, even though
     * g.tint as the actual color seems a little more intuitive,
     * it's just that g.tintEnabled is even more unintuitive.
     * Same goes for fill and stroke, et al.
     */
    @JvmField
    var tint = false

    /** tint that was last set (read-only)  */
    @JvmField
    var tintColor = 0

    protected var tintAlpha = false
    protected var tintR = 0f
    protected var tintG = 0f
    protected var tintB = 0f
    protected var tintA = 0f
    protected var tintRi = 0
    protected var tintGi = 0
    protected var tintBi = 0
    protected var tintAi = 0


    // ........................................................

    // Fill color

    /** true if fill() is enabled, (read-only)  */
    @JvmField
    var fill = false

    /** fill that was last set (read-only)  */
    @JvmField
    var fillColor = -0x1

    protected var fillAlpha = false
    protected var fillR = 0f
    protected var fillG = 0f
    protected var fillB = 0f
    protected var fillA = 0f
    protected var fillRi = 0
    protected var fillGi = 0
    protected var fillBi = 0
    protected var fillAi = 0


    // ........................................................


    // Stroke color

    /** true if stroke() is enabled, (read-only)  */
    @JvmField
    var stroke = false

    /** stroke that was last set (read-only)  */
    @JvmField
    var strokeColor = -0x1000000

    protected var strokeAlpha = false
    protected var strokeR = 0f
    protected var strokeG = 0f
    protected var strokeB = 0f
    protected var strokeA = 0f
    protected var strokeRi = 0
    protected var strokeGi = 0
    protected var strokeBi = 0
    protected var strokeAi = 0

    /**
     * Last value set by strokeWeight() (read-only). This has a default
     * setting, rather than fighting with renderers about whether that
     * renderer supports thick lines.
     */
    @JvmField
    var strokeWeight = DEFAULT_STROKE_WEIGHT

    /**
     * Set by strokeJoin() (read-only). This has a default setting
     * so that strokeJoin() need not be called by defaults,
     * because subclasses may not implement it (i.e. PGraphicsGL)
     */
    @JvmField
    var strokeJoin = DEFAULT_STROKE_JOIN

    /**
     * Set by strokeCap() (read-only). This has a default setting
     * so that strokeCap() need not be called by defaults,
     * because subclasses may not implement it (i.e. PGraphicsGL)
     */
    @JvmField
    var strokeCap = DEFAULT_STROKE_CAP
    // ........................................................
    // Shape placement properties
    // imageMode() is inherited from PImage
    /** The current rect mode (read-only)  */
    @JvmField
    var rectMode = 0

    /** The current ellipse mode (read-only)  */
    @JvmField
    var ellipseMode = 0

    /** The current shape alignment mode (read-only)  */
    @JvmField
    var shapeMode = 0

    /** The current image alignment (read-only)  */
    @JvmField
    var imageMode = PConstants.CORNER


    // ........................................................


    // Text and font properties


    /** The current text font (read-only)  */
    @JvmField
    var textFont: PFont? = null

    /** The current text align (read-only)  */
    @JvmField
    var textAlign = PConstants.LEFT

    /** The current vertical text alignment (read-only)  */
    @JvmField
    var textAlignY = PConstants.BASELINE

    /** The current text mode (read-only)  */
    @JvmField
    var textMode = PConstants.MODEL

    /** The current text size (read-only)  */
    @JvmField
    var textSize = 0f

    /** The current text leading (read-only)  */
    @JvmField
    var textLeading = 0f

    // ........................................................
    // Material properties
    //  PMaterial material;
    //  PMaterial[] materialStack;
    //  int materialStackPointer;
    @JvmField
    var ambientColor = 0
    var ambientR = 0f
    var ambientG = 0f
    var ambientB = 0f

    @JvmField
    var setAmbient = false

    @JvmField
    var specularColor = 0

    var specularR = 0f
    var specularG = 0f
    var specularB = 0f

    @JvmField
    var emissiveColor = 0

    var emissiveR = 0f
    var emissiveG = 0f
    var emissiveB = 0f
    @JvmField
    var shininess = 0f

    var styleStack = arrayOfNulls<PStyle>(STYLE_STACK_DEPTH)

    var styleStackDepth = 0


    ////////////////////////////////////////////////////////////


    /** Last background color that was set, zero if an image  */
    @JvmField
    var backgroundColor = -0x333334

    protected var backgroundAlpha = false

    @JvmField
    protected var backgroundR = 0f

    @JvmField
    protected var backgroundG = 0f

    @JvmField
    protected var backgroundB = 0f

    @JvmField
    protected var backgroundA = 0f

    protected var backgroundRi = 0
    protected var backgroundGi = 0
    protected var backgroundBi = 0
    protected var backgroundAi = 0

    /** The current blending mode.  */
    @JvmField
    protected var blendMode = 0

    // ........................................................

    // internal color for setting/calculating

    @JvmField
    protected var calcR = 0f

    @JvmField
    protected var calcG = 0f

    @JvmField
    protected var calcB = 0f

    protected var calcA = 0f
    protected var calcRi = 0
    protected var calcGi = 0
    protected var calcBi = 0
    protected var calcAi = 0
    protected var calcColor = 0
    protected var calcAlpha = false

    /** The last RGB value converted to HSB  */
    @JvmField
    var cacheHsbKey = 0

    /** Result of the last conversion to HSB  */
    @JvmField
    var cacheHsbValue = FloatArray(3)


    // ........................................................


    /**
     * Type of shape passed to beginShape(),
     * zero if no shape is currently being drawn.
     */
    @JvmField
    protected var shape = 0
    @JvmField
    protected var vertices = Array(DEFAULT_VERTICES) { FloatArray(VERTEX_FIELD_COUNT) }

    @JvmField
    protected var vertexCount: Int = 0 // total number of vertices = 0


    // ........................................................

    protected var bezierInited = false

    @JvmField
    var bezierDetail = 20

    // used by both curve and bezier, so just init here

    protected var bezierBasisMatrix = PMatrix3D((-1).toFloat(), 3F, (-3).toFloat(), 1F,
            3F, (-6).toFloat(), 3F, 0F,
            (-3).toFloat(), 3F, 0F, 0F,
            1F, 0F, 0F, 0F)

    //protected PMatrix3D bezierForwardMatrix;

    @JvmField
    protected var bezierDrawMatrix: PMatrix3D? = null

    // ........................................................

    protected var curveInited = false

    @JvmField
    var curveDetail = 20

    @JvmField
    var curveTightness = 0f


    // catmull-rom basis matrix, perhaps with optional s parameter

    protected var curveBasisMatrix: PMatrix3D? = null

    @JvmField
    protected var curveDrawMatrix: PMatrix3D? = null

    protected var bezierBasisInverse: PMatrix3D? = null

    @JvmField
    protected var curveToBezierMatrix: PMatrix3D? = null

    // ........................................................
    // spline vertices
    @JvmField
    protected var curveVertices: Array<FloatArray>? = null

    @JvmField
    protected var curveVertexCount = 0

    fun lerpColor(c1: Int, c2: Int,amt: Float): Int {
       return  Companion.lerpColor(c1,c2,amt,colorMode)
    }

    companion object {
        ////////////////////////////////////////////////////////////
        // Vertex fields, moved from PConstants (after 2.0a8) because they're too
        // general to show up in all sketches as defined variables.
        // X, Y and Z are still stored in PConstants because of their general
        // usefulness, and that X we'll always want to be 0, etc.
        const val R = 3 // actual rgb, after lighting
        const val G = 4 // fill stored here, transform in place
        const val B = 5 // TODO don't do that anymore (?)
        const val A = 6
        const val U = 7 // texture
        const val V = 8
        const val NX = 9 // normal
        const val NY = 10
        const val NZ = 11
        const val EDGE = 12
        // stroke
        /** stroke argb values  */
        const val SR = 13
        const val SG = 14
        const val SB = 15
        const val SA = 16

        /** stroke weight  */
        const val SW = 17

        // transformations (2D and 3D)
        const val TX = 18 // transformed xyzw
        const val TY = 19
        const val TZ = 20
        const val VX = 21 // view space coords
        const val VY = 22
        const val VZ = 23
        const val VW = 24

        // material properties
        // Ambient color (usually to be kept the same as diffuse)
        // fill(_) sets both ambient and diffuse.
        const val AR = 25
        const val AG = 26
        const val AB = 27

        // Diffuse is shared with fill.
        const val DR = 3 // TODO needs to not be shared, this is a material property
        const val DG = 4
        const val DB = 5
        const val DA = 6

        // specular (by default kept white)
        const val SPR = 28
        const val SPG = 29
        const val SPB = 30
        const val SHINE = 31

        // emissive (by default kept black)
        const val ER = 32
        const val EG = 33
        const val EB = 34

        // has this vertex been lit yet
        const val BEEN_LIT = 35

        // has this vertex been assigned a normal yet
        const val HAS_NORMAL = 36
        const val VERTEX_FIELD_COUNT = 37

        // ........................................................
        // Additional stroke properties
        protected const val DEFAULT_STROKE_WEIGHT = 1f
        protected const val DEFAULT_STROKE_JOIN = PConstants.MITER
        protected const val DEFAULT_STROKE_CAP = PConstants.ROUND

        // Style stack
        const val STYLE_STACK_DEPTH = 64
        // ........................................................
        /**
         * Current model-view matrix transformation of the form m[row][column],
         * which is a "column vector" (as opposed to "row vector") matrix.
         */
        //  PMatrix matrix;
        //  public float m00, m01, m02, m03;
        //  public float m10, m11, m12, m13;
        //  public float m20, m21, m22, m23;
        //  public float m30, m31, m32, m33;
        //  static final int MATRIX_STACK_DEPTH = 32;
        //  float[][] matrixStack = new float[MATRIX_STACK_DEPTH][16];
        //  float[][] matrixInvStack = new float[MATRIX_STACK_DEPTH][16];
        //  int matrixStackDepth;
        const val MATRIX_STACK_DEPTH = 32

        // vertices
        const val DEFAULT_VERTICES = 512

        // ........................................................
        // precalculate sin/cos lookup tables [toxi]
        // circle resolution is determined from the actual used radii
        // passed to ellipse() method. this will automatically take any
        // scale transformations into account too
        // [toxi 031031]
        // changed table's precision to 0.5 degree steps
        // introduced new vars for more flexible code
        @JvmField
        protected val sinLUT: FloatArray

        @JvmField
        val cosLUT: FloatArray

        protected const val SINCOS_PRECISION = 0.5f

        const val SINCOS_LENGTH = (360f / SINCOS_PRECISION).toInt()

        // ........................................................
        /// normal calculated per triangle
        protected const val NORMAL_MODE_AUTO = 0

        /// one normal manually specified per shape
        protected const val NORMAL_MODE_SHAPE = 1

        /// normals specified for each shape vertex
        protected const val NORMAL_MODE_VERTEX = 2

        var lerpColorHSB1: FloatArray? = null

        lateinit var lerpColorHSB2: FloatArray

        lateinit var lerpColorHSB3: FloatArray
        /**
         * Interpolate between two colors. Like lerp(), but for the
         * individual color components of a color supplied as an int value.
         */

        //////////////////////////////////////////////////////////////

        // COLOR DATATYPE INTERPOLATION

        // Against our better judgement.

        /**
         * Interpolate between two colors, using the current color mode.
         */
        @JvmStatic
        fun lerpColor(c1: Int, c2: Int, amt: Float, mode: Int): Int {
            if (mode == PConstants.RGB) {
                val a1 = (c1 shr 24 and 0xff).toFloat()

                val r1 = (c1 shr 16 and 0xff.toFloat().toInt()).toFloat()

                val g1 = (c1 shr 8 and 0xff.toFloat().toInt()).toFloat()

                val b1 = (c1 and 0xff.toFloat().toInt()).toFloat()

                val a2 = (c2 shr 24 and 0xff.toFloat().toInt()).toFloat()

                val r2 = (c2 shr 16 and 0xff.toFloat().toInt()).toFloat()

                val g2 = (c2 shr 8 and 0xff.toFloat().toInt()).toFloat()

                val b2 = (c2 and 0xff.toFloat().toInt()).toFloat()

                return (a1 + (a2 - a1) * amt).toInt() shl 24 or
                        ((r1 + (r2 - r1) * amt).toInt() shl 16) or
                        ((g1 + (g2 - g1) * amt).toInt() shl 8) or
                        (b1 + (b2 - b1) * amt).toInt()
            } else if (mode == PConstants.HSB) {
                if (lerpColorHSB1 == null) {
                    lerpColorHSB1 = FloatArray(3)
                    lerpColorHSB2 = FloatArray(3)
                    lerpColorHSB3 = FloatArray(3)
                }

                val a1 = (c1 shr 24 and 0xff.toFloat().toInt()).toFloat()

                val a2 = (c2 shr 24 and 0xff.toFloat().toInt()).toFloat()

                val alfa = (a1 + (a2 - a1) * amt).toInt() shl 24

                Color.RGBToHSV(c1 shr 16 and 0xff, c1 shr 8 and 0xff, c1 and 0xff,
                        lerpColorHSB1)
                Color.RGBToHSV(c2 shr 16 and 0xff, c2 shr 8 and 0xff, c2 and 0xff,
                        lerpColorHSB2)

                /* If mode is HSB, this will take the shortest path around the
       * color wheel to find the new color. For instance, red to blue
       * will go red violet blue (backwards in hue space) rather than
       * cycling through ROYGBIV.
       */
                // Disabling rollover (wasn't working anyway) for 0126.
                // Otherwise it makes full spectrum scale impossible for
                // those who might want it...in spite of how despicable
                // a full spectrum scale might be.
                // roll around when 0.9 to 0.1
                // more than 0.5 away means that it should roll in the other direction
                /*
      float h1 = lerpColorHSB1[0];
      float h2 = lerpColorHSB2[0];
      if (Math.abs(h1 - h2) > 0.5f) {
        if (h1 > h2) {
          // i.e. h1 is 0.7, h2 is 0.1
          h2 += 1;
        } else {
          // i.e. h1 is 0.1, h2 is 0.7
          h1 += 1;
        }
      }
      float ho = (PApplet.lerp(lerpColorHSB1[0], lerpColorHSB2[0], amt)) % 1.0f;
      */

//      float ho = PActivity.lerp(lerpColorHSB1[0], lerpColorHSB2[0], amt);
//      float so = PActivity.lerp(lerpColorHSB1[1], lerpColorHSB2[1], amt);
//      float bo = PActivity.lerp(lerpColorHSB1[2], lerpColorHSB2[2], amt);
//    return alfa | (Color.HSVtoRGB(ho, so, bo) & 0xFFFFFF);
//    return Color.HSVToColor(alfa, new float[] { ho, so, bo });
                lerpColorHSB3[0] = PApplet.lerp(lerpColorHSB1!![0], lerpColorHSB2[0], amt)
                lerpColorHSB3[1] = PApplet.lerp(lerpColorHSB1!![1], lerpColorHSB2[1], amt)
                lerpColorHSB3[2] = PApplet.lerp(lerpColorHSB1!![2], lerpColorHSB2[2], amt)
                return Color.HSVToColor(alfa, lerpColorHSB3)
            }
            return 0
        }

        //////////////////////////////////////////////////////////////
        // WARNINGS and EXCEPTIONS
        protected var warnings: HashMap<String, Any?>? = null

        /**
         * Show a renderer error, and keep track of it so that it's only shown once.
         * @param msg the error message (which will be stored for later comparison)
         */
        @JvmStatic
        fun showWarning(msg: String) {  // ignore
            if (warnings == null) {
                warnings = HashMap()
            }
            if (!warnings!!.containsKey(msg)) {
                System.err.println(msg)
                warnings!![msg] = Any()
            }
        }

        /**
         * Version of showWarning() that takes a parsed String.
         */
        @JvmStatic
        fun showWarning(msg: String?, vararg args: Any?) {  // ignore
            showWarning(String.format(msg!!, *args))
        }

        /**
         * Display a warning that the specified method is only available with 3D.
         * @param method The method name (no parentheses)
         */
        @JvmStatic
        fun showDepthWarning(method: String) {
            showWarning(method + "() can only be used with a renderer that " +
                    "supports 3D, such as P3D or OPENGL.")
        }

        /**
         * Display a warning that the specified method that takes x, y, z parameters
         * can only be used with x and y parameters in this renderer.
         * @param method The method name (no parentheses)
         */
        @JvmStatic
        fun showDepthWarningXYZ(method: String) {
            showWarning(method + "() with x, y, and z coordinates " +
                    "can only be used with a renderer that " +
                    "supports 3D, such as P3D or OPENGL. " +
                    "Use a version without a z-coordinate instead.")
        }

        /**
         * Display a warning that the specified method is simply unavailable.
         */
        @JvmStatic
        fun showMethodWarning(method: String) {
            showWarning("$method() is not available with this renderer.")
        }

        /**
         * Error that a particular variation of a method is unavailable (even though
         * other variations are). For instance, if vertex(x, y, u, v) is not
         * available, but vertex(x, y) is just fine.
         */
        @JvmStatic
        fun showVariationWarning(str: String) {
            showWarning("$str is not available with this renderer.")
        }

        /**
         * Display a warning that the specified method is not implemented, meaning
         * that it could be either a completely missing function, although other
         * variations of it may still work properly.
         */
        @JvmStatic
        fun showMissingWarning(method: String) {
            showWarning(method + "(), or this particular variation of it, " +
                    "is not available with this renderer.")
        }

        /**
         * Show an renderer-related exception that halts the program. Currently just
         * wraps the message as a RuntimeException and throws it, but might do
         * something more specific might be used in the future.
         */
        @JvmStatic
        fun showException(msg: String?) {  // ignore
            throw RuntimeException(msg)
        }

        @JvmField
        protected var asyncImageSaver: AsyncImageSaver? = null

        init {
            sinLUT = FloatArray(SINCOS_LENGTH)
            cosLUT = FloatArray(SINCOS_LENGTH)
            for (i in 0 until SINCOS_LENGTH) {
                sinLUT[i] = Math.sin(i * PConstants.DEG_TO_RAD * SINCOS_PRECISION.toDouble()).toFloat()
                cosLUT[i] = Math.cos(i * PConstants.DEG_TO_RAD * SINCOS_PRECISION.toDouble()).toFloat()
            }
        }
    }
    // ........................................................
    /** The current font if a Java version of it is installed  */ //protected Font textFontNative;
    /** Metrics for the current native Java font  */ //protected FontMetrics textFontNativeMetrics;
    //  /** Last text position, because text often mixed on lines together */
    //  protected float textX, textY, textZ;
    /**
     * Internal buffer used by the text() functions
     * because the String object is slow
     */
    @JvmField
    protected var textBuffer = CharArray(8 * 1024)

    @JvmField
    protected var textWidthBuffer = CharArray(8 * 1024)

    @JvmField
    protected var textBreakCount = 0

    @JvmField
    protected var textBreakStart: IntArray? = null

    protected lateinit var textBreakStop: IntArray

    // ........................................................
    var edge = true

    /// Current mode for normals, one of AUTO, SHAPE, or VERTEX
    @JvmField
    protected var normalMode = 0

    /// Keep track of how many calls to normal, to determine the mode.
    //protected int normalCount;
    @JvmField
    protected var autoNormal = false

    /** Current normal vector.  */
    @JvmField
    var normalX = 0f

    @JvmField
    var normalY = 0f

    @JvmField
    var normalZ = 0f


    // ........................................................


    /**
     * Sets whether texture coordinates passed to
     * vertex() calls will be based on coordinates that are
     * based on the IMAGE or NORMALIZED.
     */
    @JvmField
    var textureMode = PConstants.IMAGE

    /**
     * Current horizontal coordinate for texture, will always
     * be between 0 and 1, even if using textureMode(IMAGE).
     */
    @JvmField
    var textureU = 0f

    /** Current vertical coordinate for texture, see above.  */
    @JvmField
    var textureV = 0f

    /** Current image being used as a texture  */
    @JvmField
    var textureImage: PImage? = null

    // ........................................................
    // [toxi031031] new & faster sphere code w/ support flexibile resolutions
    // will be set by sphereDetail() or 1st call to sphere()

    protected lateinit var sphereX: FloatArray


    protected lateinit var sphereY: FloatArray


    protected lateinit var sphereZ: FloatArray


    /// Number of U steps (aka "theta") around longitudinally spanning 2*pi
    @JvmField
    var sphereDetailU = 0

    /// Number of V steps (aka "phi") along latitudinally top-to-bottom spanning pi
    @JvmField
    var sphereDetailV = 0

    // ........................................................
    // Variables used to save the surface contents before the activity is taken to the background.
    @JvmField
    protected var restoreFilename: String? = null

    @JvmField
    protected var restoreWidth = 0

    @JvmField
    protected var restoreHeight = 0

    @JvmField
    protected var restoreCount = 0

    @JvmField
    protected var restartedLoopingAfterResume = false

    @JvmField
    protected var restoredSurface = true

    // This auxiliary variable is used to implement a little hack that fixes
    // https://github.com/processing/processing-android/issues/147
    // on older devices where the last frame cannot be maintained after ending
    // the rendering in GL. The trick consists in running one more frame after the
    // noLoop() call, which ensures that the FBO layer is properly initialized
    // and drawn with the contents of the previous frame.
    @JvmField
    var requestedNoLoop = false

    open fun setparent(parent: PApplet?) {  // ignore
        this.parent = parent
    }

    /**
     * Set (or unset) this as the main drawing surface. Meaning that it can
     * safely be set to opaque (and given a default gray background), or anything
     * else that goes along with that.
     */
    open fun setprimary(primary: Boolean) {  // ignore
        primaryGraphics = primary

        // base images must be opaque (for performance and general
        // headache reasons.. argh, a semi-transparent opengl surface?)
        // use createGraphics() if you want a transparent surface.
        if (primaryGraphics) {
            format = PConstants.RGB
        }
    }

    fun setpath(path: String?) {  // ignore
        this.path = path
    }

    open fun setFrameRate(framerate: Float) {  // ignore
    }

    open fun surfaceChanged() {  // ignore
    }

    open fun reset() { // ignore
    }

    /**
     * The final step in setting up a renderer, set its size of this renderer.
     * This was formerly handled by the constructor, but instead it's been broken
     * out so that setParent/setPrimary/setPath can be handled differently.
     *
     * Important that this is ignored by preproc.pl because otherwise it will
     * override setSize() in PApplet/Applet/Component, which will 1) not call
     * super.setSize(), and 2) will cause the renderer to be resized from the
     * event thread (EDT), causing a nasty crash as it collides with the
     * animation thread.
     */
    open fun setSize(w: Int, h: Int) {  // ignore
        width = w
        height = h
        pixelWidth = width * pixelDensity
        pixelHeight = height * pixelDensity
        reapplySettings = true
    }

    /**
     * Handle any takedown for this graphics context.
     *
     *
     * This is called when a sketch is shut down and this renderer was
     * specified using the size() command, or inside endRecord() and
     * endRaw(), in order to shut things off.
     */
    open fun dispose() {  // ignore
        parent = null
    }

    open fun createSurface(component: AppComponent?, holder: SurfaceHolder?, reset: Boolean): PSurface? {  // ignore
        return null
    }
    //////////////////////////////////////////////////////////////
    // IMAGE METADATA FOR THIS RENDERER
    /**
     * Store data of some kind for the renderer that requires extra metadata of
     * some kind. Usually this is a renderer-specific representation of the
     * image data, for instance a BufferedImage with tint() settings applied for
     * PGraphicsJava2D, or resized image data and OpenGL texture indices for
     * PGraphicsOpenGL.
     * @param image The image to be stored
     * @param storage The metadata required by the renderer
     */
    open fun setCache(image: PImage, storage: Any) {  // ignore
        cacheMap?.set(image, storage)
    }

    /**
     * Get cache storage data for the specified renderer. Because each renderer
     * will cache data in different formats, it's necessary to store cache data
     * keyed by the renderer object. Otherwise, attempting to draw the same
     * image to both a PGraphicsJava2D and a PGraphicsOpenGL will cause errors.
     * @return metadata stored for the specified renderer
     */
    open fun getCache(image: PImage?): Any? {  // ignore
        return cacheMap?.get(image)
    }

    /**
     * Remove information associated with this renderer from the cache, if any.
     * @param image The image whose cache data should be removed
     */
    open fun removeCache(image: PImage?) {  // ignore
        cacheMap!!.remove(image)
    }
    //////////////////////////////////////////////////////////////
    // FRAME
    /**
     * Handle grabbing the focus from the parent applet. Other renderers can
     * override this if handling needs to be different.
     */
    fun requestFocus() {  // ignore
    }
    /**
     * Some renderers have requirements re: when they are ready to draw.
     */
    //  public boolean canDraw() {  // ignore
    //    return true;
    //  }
    /**
     * Try to draw, or put a draw request on the queue.
     */
    //  public void requestDraw() {  // ignore
    //  }
    /**
     * Prepares the PGraphics for drawing.
     *
     *
     * When creating your own PGraphics, you should call this before
     * drawing anything.
     */
    open fun beginDraw() {  // ignore
    }

    /**
     * This will finalize rendering so that it can be shown on-screen.
     *
     *
     * When creating your own PGraphics, you should call this when
     * you're finished drawing.
     */
    open fun endDraw() {  // ignore
    }

    open fun flush() {
        // no-op, mostly for P3D to write sorted stuff
    }

    open fun beginPGL(): PGL? {
        showMethodWarning("beginPGL")
        return null
    }

    open fun endPGL() {
        showMethodWarning("endPGL")
    }

    protected open fun checkSettings() {
        if (!settingsInited) defaultSettings()
        if (reapplySettings) reapplySettings()
    }

    /**
     * Set engine's default values. This has to be called by PApplet,
     * somewhere inside setup() or draw() because it talks to the
     * graphics buffer, meaning that for subclasses like OpenGL, there
     * needs to be a valid graphics context to mess with otherwise
     * you'll get some good crashing action.
     *
     * This is currently called by checkSettings(), during beginDraw().
     */
    protected open fun defaultSettings() {  // ignore
        colorMode(PConstants.RGB, 255f)
        fill(255)
        stroke(0)

        // added for 0178 for subclasses that need them
        strokeWeight(DEFAULT_STROKE_WEIGHT)
        strokeJoin(DEFAULT_STROKE_JOIN)
        strokeCap(DEFAULT_STROKE_CAP)

        // init shape stuff
        shape = 0

        // init matrices (must do before lights)
        //matrixStackDepth = 0;
        rectMode(PConstants.CORNER)
        ellipseMode(PConstants.DIAMETER)
        autoNormal = true

        // no current font
        textFont = null
        textSize = 12f
        textLeading = 14f
        textAlign = PConstants.LEFT
        textMode = PConstants.MODEL

        // if this fella is associated with an applet, then clear its background.
        // if it's been created by someone else through createGraphics,
        // they have to call background() themselves, otherwise everything gets
        // a gray background (when just a transparent surface or an empty pdf
        // is what's desired).
        // this background() call is for the Java 2D and OpenGL renderers.
        if (primaryGraphics) {
            //System.out.println("main drawing surface bg " + getClass().getName());
            background(backgroundColor)
        }
        blendMode(PConstants.BLEND)
        settingsInited = true
        // defaultSettings() overlaps reapplySettings(), don't do both
        reapplySettings = false
    }

    /**
     * Re-apply current settings. Some methods, such as textFont(), require that
     * their methods be called (rather than simply setting the textFont variable)
     * because they affect the graphics context, or they require parameters from
     * the context (e.g. getting native fonts for text).
     *
     * This will only be called from an allocate(), which is only called from
     * size(), which is safely called from inside beginDraw(). And it cannot be
     * called before defaultSettings(), so we should be safe.
     */
    protected fun reapplySettings() {  // ignore
//    System.out.println("attempting reapplySettings()");
        if (!settingsInited) return  // if this is the initial setup, no need to reapply

//    System.out.println("  doing reapplySettings");
//    new Exception().printStackTrace(System.out);
        colorMode(colorMode, colorModeX, colorModeY, colorModeZ)
        if (fill) {
//      PApplet.println("  fill " + PApplet.hex(fillColor));
            fill(fillColor)
        } else {
            noFill()
        }
        if (stroke) {
            stroke(strokeColor)

            // The if() statements should be handled inside the functions,
            // otherwise an actual reset/revert won't work properly.
            //if (strokeWeight != DEFAULT_STROKE_WEIGHT) {
            strokeWeight(strokeWeight)
            //}
//      if (strokeCap != DEFAULT_STROKE_CAP) {
            strokeCap(strokeCap)
            //      }
//      if (strokeJoin != DEFAULT_STROKE_JOIN) {
            strokeJoin(strokeJoin)
            //      }
        } else {
            noStroke()
        }
        if (tint) {
            tint(tintColor)
        } else {
            noTint()
        }
        //    if (smooth) {
//      smooth();
//    } else {
//      // Don't bother setting this, cuz it'll anger P3D.
//      noSmooth();
//    }
        if (textFont != null) {
//      System.out.println("  textFont in reapply is " + textFont);
            // textFont() resets the leading, so save it in case it's changed
            val saveLeading = textLeading
            textFont(textFont, textSize)
            textLeading(saveLeading)
        }
        textMode(textMode)
        textAlign(textAlign, textAlignY)
        background(backgroundColor)
        blendMode(blendMode)
        reapplySettings = false
    }

    //////////////////////////////////////////////////////////////
    // RENDERER STATE
    open fun clearState() {  // ignore
        // Nothing to do here, it depends on the renderer's implementation.
    }

    open fun saveState() {  // ignore
        // Nothing to do here, it depends on the renderer's implementation.
    }

    open fun restoreState() {  // ignore
        // This method probably does not need to be re-implemented in the subclasses. All we need to
        // do is to check for the resume in no-loop state situation:
        restoredSurface = false
        if (!parent!!.looping) {
            // The sketch needs to draw a few frames after resuming so it has the chance to restore the
            // screen contents:
            // https://github.com/processing/processing-android/issues/492
            // so we restart looping:
            parent!!.loop()
            // and flag this situation when the surface has been restored:
            restartedLoopingAfterResume = true
        }
    }

    fun restoringState(): Boolean { // ignore
        return !restoredSurface && restartedLoopingAfterResume
    }

    protected open fun restoreSurface() { // ignore
        // When implementing this method in a subclass of PGraphics, it should add a call to the super
        // implementation, to make sure that the looping is stopped in the case where the sketch was
        // resumed in no-loop state (see comment in restoreState() method above).
        if (restoredSurface && restartedLoopingAfterResume) {
            restartedLoopingAfterResume = false
            parent!!.noLoop()
        }
    }

    open fun requestNoLoop(): Boolean { // ignore
        // Some renderers (OpenGL) cannot be set to no-loop right away, it has to be requested so
        // any pending frames are properly rendered. Override as needed.
        return false
    }

    // ignore
    protected open val isLooping: Boolean
        get() =// ignore
            parent!!.isLooping && (!requestNoLoop() || !requestedNoLoop)
    //////////////////////////////////////////////////////////////
    // HINTS
    /**
     * Enable a hint option.
     * <P>
     * For the most part, hints are temporary api quirks,
     * for which a proper api hasn't been properly worked out.
     * for instance SMOOTH_IMAGES existed because smooth()
     * wasn't yet implemented, but it will soon go away.
    </P> * <P>
     * They also exist for obscure features in the graphics
     * engine, like enabling/disabling single pixel lines
     * that ignore the zbuffer, the way they do in alphabot.
    </P> * <P>
     * Current hint options:
    </P> * <UL>
     * <LI><TT>DISABLE_DEPTH_TEST</TT> -
     * turns off the z-buffer in the P3D or OPENGL renderers.
    </LI></UL> *
     */
    open fun hint(which: Int) {
        if (which == PConstants.ENABLE_NATIVE_FONTS ||
                which == PConstants.DISABLE_NATIVE_FONTS) {
            showWarning("hint(ENABLE_NATIVE_FONTS) no longer supported. " +
                    "Use createFont() instead.")
        }
        if (which == PConstants.ENABLE_KEY_REPEAT) {
            parent!!.keyRepeatEnabled = true
        } else if (which == PConstants.DISABLE_KEY_REPEAT) {
            parent!!.keyRepeatEnabled = false
        }
        if (which > 0) {
            hints[which] = true
        } else {
            hints[-which] = false
        }
    }
    //////////////////////////////////////////////////////////////
    // VERTEX SHAPES
    /**
     * Start a new shape of type POLYGON
     */
    fun beginShape() {
        beginShape(PConstants.POLYGON)
    }

    /**
     * Start a new shape.
     * <P>
     * <B>Differences between beginShape() and line() and point() methods.</B>
    </P> * <P>
     * beginShape() is intended to be more flexible at the expense of being
     * a little more complicated to use. it handles more complicated shapes
     * that can consist of many connected lines (so you get joins) or lines
     * mixed with curves.
    </P> * <P>
     * The line() and point() command are for the far more common cases
     * (particularly for our audience) that simply need to draw a line
     * or a point on the screen.
    </P> * <P>
     * From the code side of things, line() may or may not call beginShape()
     * to do the drawing. In the beta code, they do, but in the alpha code,
     * they did not. they might be implemented one way or the other depending
     * on tradeoffs of runtime efficiency vs. implementation efficiency &mdash
     * meaning the speed that things run at vs. the speed it takes me to write
     * the code and maintain it. for beta, the latter is most important so
     * that's how things are implemented.
    </P> */
    open fun beginShape(kind: Int) {
        shape = kind
    }

    /**
     * Sets whether the upcoming vertex is part of an edge.
     * Equivalent to glEdgeFlag(), for people familiar with OpenGL.
     */
    fun edge(edge: Boolean) {
        this.edge = edge
    }

    /**
     * Sets the current normal vector. Only applies with 3D rendering
     * and inside a beginShape/endShape block.
     * <P></P>
     * This is for drawing three dimensional shapes and surfaces,
     * allowing you to specify a vector perpendicular to the surface
     * of the shape, which determines how lighting affects it.
     * <P></P>
     * For people familiar with OpenGL, this function is basically
     * identical to glNormal3f().
     */
    fun normal(nx: Float, ny: Float, nz: Float) {
        normalX = nx
        normalY = ny
        normalZ = nz

        // if drawing a shape and the normal hasn't been set yet,
        // then we need to set the normals for each vertex so far
        if (shape != 0) {
            if (normalMode == NORMAL_MODE_AUTO) {
                // One normal per begin/end shape
                normalMode = NORMAL_MODE_SHAPE
            } else if (normalMode == NORMAL_MODE_SHAPE) {
                // a separate normal for each vertex
                normalMode = NORMAL_MODE_VERTEX
            }
        }
    }

    open fun attribPosition(name: String?, x: Float, y: Float, z: Float) {
        showMissingWarning("attrib")
    }

    open fun attribNormal(name: String?, nx: Float, ny: Float, nz: Float) {
        showMissingWarning("attrib")
    }

    open fun attribColor(name: String?, color: Int) {
        showMissingWarning("attrib")
    }

    open fun attrib(name: String?, vararg values: Float) {
        showMissingWarning("attrib")
    }

    open fun attrib(name: String?, vararg values: Int) {
        showMissingWarning("attrib")
    }

    open fun attrib(name: String?, vararg values: Boolean) {
        showMissingWarning("attrib")
    }

    /**
     * Set texture mode to either to use coordinates based on the IMAGE
     * (more intuitive for new users) or NORMALIZED (better for advanced chaps)
     */
    fun textureMode(mode: Int) {
        textureMode = mode
    }

    open fun textureWrap(wrap: Int) {
        showMissingWarning("textureWrap")
    }

    /**
     * Set texture image for current shape.
     * Needs to be called between @see beginShape and @see endShape
     *
     * @param image reference to a PImage object
     */
    open fun texture(image: PImage?) {
        textureImage = image
    }

    /**
     * Removes texture image for current shape.
     * Needs to be called between @see beginShape and @see endShape
     *
     */
    fun noTexture() {
        textureImage = null
    }

    protected fun vertexCheck() {
        if (vertexCount == vertices.size) {
            val temp = Array(vertexCount shl 1) { FloatArray(VERTEX_FIELD_COUNT) }
            System.arraycopy(vertices, 0, temp, 0, vertexCount)
            vertices = temp
        }
    }

    open fun vertex(x: Float, y: Float) {
        vertexCheck()
        val vertex = vertices[vertexCount]
        curveVertexCount = 0
        vertex[PConstants.X] = x
        vertex[PConstants.Y] = y
        vertex[PConstants.Z] = 0F
        vertex[EDGE] = if (edge) 1F else 0F
        val textured = textureImage != null
        if (fill || textured) {
            if (!textured) {
                vertex[R] = fillR
                vertex[G] = fillG
                vertex[B] = fillB
                vertex[A] = fillA
            } else {
                if (tint) {
                    vertex[R] = tintR
                    vertex[G] = tintG
                    vertex[B] = tintB
                    vertex[A] = tintA
                } else {
                    vertex[R] = 1F
                    vertex[G] = 1F
                    vertex[B] = 1F
                    vertex[A] = 1F
                }
            }
        }
        if (stroke) {
            vertex[SR] = strokeR
            vertex[SG] = strokeG
            vertex[SB] = strokeB
            vertex[SA] = strokeA
            vertex[SW] = strokeWeight
        }
        vertex[U] = textureU
        vertex[V] = textureV
        if (autoNormal) {
            val norm2 = normalX * normalX + normalY * normalY + normalZ * normalZ
            if (norm2 < PConstants.EPSILON) {
                vertex[HAS_NORMAL] = 0F
            } else {
                if (Math.abs(norm2 - 1) > PConstants.EPSILON) {
                    // The normal vector is not normalized.
                    val norm = PApplet.sqrt(norm2)
                    normalX /= norm
                    normalY /= norm
                    normalZ /= norm
                }
                vertex[HAS_NORMAL] = 1F
            }
        } else {
            vertex[HAS_NORMAL] = 1F
        }
        vertex[NX] = normalX
        vertex[NY] = normalY
        vertex[NZ] = normalZ
        vertexCount++
    }

    open fun vertex(x: Float, y: Float, z: Float) {
        vertexCheck()
        val vertex = vertices[vertexCount]

        // only do this if we're using an irregular (POLYGON) shape that
        // will go through the triangulator. otherwise it'll do thinks like
        // disappear in mathematically odd ways
        // http://dev.processing.org/bugs/show_bug.cgi?id=444
        if (shape == PConstants.POLYGON) {
            if (vertexCount > 0) {
                val pvertex = vertices[vertexCount - 1]
                if (Math.abs(pvertex[PConstants.X] - x) < PConstants.EPSILON &&
                        Math.abs(pvertex[PConstants.Y] - y) < PConstants.EPSILON &&
                        Math.abs(pvertex[PConstants.Z] - z) < PConstants.EPSILON) {
                    // this vertex is identical, don't add it,
                    // because it will anger the triangulator
                    return
                }
            }
        }

        // User called vertex(), so that invalidates anything queued up for curve
        // vertices. If this is internally called by curveVertexSegment,
        // then curveVertexCount will be saved and restored.
        curveVertexCount = 0
        vertex[PConstants.X] = x
        vertex[PConstants.Y] = y
        vertex[PConstants.Z] = z
        vertex[EDGE] = if (edge) 1F else 0F
        val textured = textureImage != null
        if (fill || textured) {
            if (!textured) {
                vertex[R] = fillR
                vertex[G] = fillG
                vertex[B] = fillB
                vertex[A] = fillA
            } else {
                if (tint) {
                    vertex[R] = tintR
                    vertex[G] = tintG
                    vertex[B] = tintB
                    vertex[A] = tintA
                } else {
                    vertex[R] = 1F
                    vertex[G] = 1F
                    vertex[B] = 1F
                    vertex[A] = 1F
                }
            }

            /*
      vertex[AR] = ambientR;
      vertex[AG] = ambientG;
      vertex[AB] = ambientB;

      vertex[SPR] = specularR;
      vertex[SPG] = specularG;
      vertex[SPB] = specularB;
      //vertex[SPA] = specularA;

      vertex[SHINE] = shininess;

      vertex[ER] = emissiveR;
      vertex[EG] = emissiveG;
      vertex[EB] = emissiveB;
      */
        }
        if (stroke) {
            vertex[SR] = strokeR
            vertex[SG] = strokeG
            vertex[SB] = strokeB
            vertex[SA] = strokeA
            vertex[SW] = strokeWeight
        }
        vertex[U] = textureU
        vertex[V] = textureV
        if (autoNormal) {
            val norm2 = normalX * normalX + normalY * normalY + normalZ * normalZ
            if (norm2 < PConstants.EPSILON) {
                vertex[HAS_NORMAL] = 0F
            } else {
                if (Math.abs(norm2 - 1) > PConstants.EPSILON) {
                    // The normal vector is not normalized.
                    val norm = PApplet.sqrt(norm2)
                    normalX /= norm
                    normalY /= norm
                    normalZ /= norm
                }
                vertex[HAS_NORMAL] = 1F
            }
        } else {
            vertex[HAS_NORMAL] = 1F
        }
        vertex[NX] = normalX
        vertex[NY] = normalY
        vertex[NZ] = normalZ
        vertexCount++
    }

    /**
     * Used by renderer subclasses or PShape to efficiently pass in already
     * formatted vertex information.
     * @param v vertex parameters, as a float array of length VERTEX_FIELD_COUNT
     */
    fun vertex(v: FloatArray?) {
        vertexCheck()
        curveVertexCount = 0
        val vertex = vertices[vertexCount]
        System.arraycopy(v, 0, vertex, 0, VERTEX_FIELD_COUNT)
        vertexCount++
    }

    open fun vertex(x: Float, y: Float, u: Float, v: Float) {
        vertexTexture(u, v)
        vertex(x, y)
    }

    open fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        vertexTexture(u, v)
        vertex(x, y, z)
    }
    /**
     * Internal method to copy all style information for the given vertex.
     * Can be overridden by subclasses to handle only properties pertinent to
     * that renderer. (e.g. no need to copy the emissive color in P2D)
     */
    //  protected void vertexStyle() {
    //  }
    /**
     * Set (U, V) coords for the next vertex in the current shape.
     * This is ugly as its own function, and will (almost?) always be
     * coincident with a call to vertex. As of beta, this was moved to
     * the protected method you see here, and called from an optional
     * param of and overloaded vertex().
     *
     *
     * The parameters depend on the current textureMode. When using
     * textureMode(IMAGE), the coordinates will be relative to the size
     * of the image texture, when used with textureMode(NORMAL),
     * they'll be in the range 0..1.
     *
     *
     * Used by both PGraphics2D (for images) and PGraphics3D.
     */
    protected fun vertexTexture(u: Float, v: Float) {
        var u = u
        var v = v
        if (textureImage == null) {
            throw RuntimeException("You must first call texture() before " +
                    "using u and v coordinates with vertex()")
        }
        if (textureMode == PConstants.IMAGE) {
            u /= textureImage!!.width.toFloat()
            v /= textureImage!!.height.toFloat()
        }
        textureU = u
        textureV = v
    }

    /** This feature is in testing, do not use or rely upon its implementation  */
    open fun breakShape() {
        showWarning("This renderer cannot currently handle concave shapes, " +
                "or shapes with holes.")
    }

    open fun beginContour() {
        showMissingWarning("beginContour")
    }

    open fun endContour() {
        showMissingWarning("endContour")
    }

    fun endShape() {
        endShape(PConstants.OPEN)
    }

    open fun endShape(mode: Int) {}

    //////////////////////////////////////////////////////////////
    // CLIPPING
    fun clip(a: Float, b: Float, c: Float, d: Float) {
        var a = a
        var b = b
        var c = c
        var d = d
        if (imageMode == PConstants.CORNER) {
            if (c < 0) {  // reset a negative width
                a += c
                c = -c
            }
            if (d < 0) {  // reset a negative height
                b += d
                d = -d
            }
            clipImpl(a, b, a + c, b + d)
        } else if (imageMode == PConstants.CORNERS) {
            if (c < a) {  // reverse because x2 < x1
                val temp = a
                a = c
                c = temp
            }
            if (d < b) {  // reverse because y2 < y1
                val temp = b
                b = d
                d = temp
            }
            clipImpl(a, b, c, d)
        } else if (imageMode == PConstants.CENTER) {
            // c and d are width/height
            if (c < 0) c = -c
            if (d < 0) d = -d
            val x1 = a - c / 2
            val y1 = b - d / 2
            clipImpl(x1, y1, x1 + c, y1 + d)
        }
    }

    protected open fun clipImpl(x1: Float, y1: Float, x2: Float, y2: Float) {
        showMissingWarning("clip")
    }

    open fun noClip() {
        showMissingWarning("noClip")
    }

    //////////////////////////////////////////////////////////////
    // BLEND
    fun blendMode(mode: Int) {
        blendMode = mode
        blendModeImpl()
    }

    protected open fun blendModeImpl() {
        if (blendMode != PConstants.BLEND) {
            showMissingWarning("blendMode")
        }
    }

    //////////////////////////////////////////////////////////////
    // SHAPE I/O
    open fun loadShape(filename: String?): PShape? {
        showMissingWarning("loadShape")
        return null
    }

    fun loadShape(filename: String?, options: String?): PShape? {
        showMissingWarning("loadShape")
        return null
    }
    // POINTS, LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP, QUADS, QUAD_STRIP
    //////////////////////////////////////////////////////////////
    // SHAPE CREATION
    /**
     * @webref shape
     * @see PShape
     *
     * @see PShape.endShape
     * @see PApplet.loadShape
     */
    @JvmOverloads
    fun createShape(type: Int = PShape.GEOMETRY): PShape {
        // If it's a PRIMITIVE, it needs the 'params' field anyway
        if (type == PConstants.GROUP || type == PShape.PATH || type == PShape.GEOMETRY) {
            return createShapeFamily(type)
        }
        val msg = "Only GROUP, PShape.PATH, and PShape.GEOMETRY work with createShape()"
        throw IllegalArgumentException(msg)
    }

    /** Override this method to return an appropriate shape for your renderer  */
    protected open fun createShapeFamily(type: Int): PShape {
        return PShape(this, type)
        //    showMethodWarning("createShape()");
//    return null;
    }

    /**
     * @param kind either POINT, LINE, TRIANGLE, QUAD, RECT, ELLIPSE, ARC, BOX, SPHERE
     * @param p parameters that match the kind of shape
     */
    fun createShape(kind: Int, vararg p: Float): PShape {
        val len = p.size
        if (kind == PConstants.POINT) {
            require(!(is3D() && len != 2 && len != 3)) { "Use createShape(POINT, x, y) or createShape(POINT, x, y, z)" }
            require(len == 2) { "Use createShape(POINT, x, y)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.LINE) {
            require(!(is3D() && len != 4 && len != 6)) { "Use createShape(LINE, x1, y1, x2, y2) or createShape(LINE, x1, y1, z1, x2, y2, z1)" }
            require(len == 4) { "Use createShape(LINE, x1, y1, x2, y2)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.TRIANGLE) {
            require(len == 6) { "Use createShape(TRIANGLE, x1, y1, x2, y2, x3, y3)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.QUAD) {
            require(len == 8) { "Use createShape(QUAD, x1, y1, x2, y2, x3, y3, x4, y4)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.RECT) {
            require(!(len != 4 && len != 5 && len != 8 && len != 9)) { "Wrong number of parameters for createShape(RECT), see the reference" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.ELLIPSE) {
            require(!(len != 4 && len != 5)) { "Use createShape(ELLIPSE, x, y, w, h) or createShape(ELLIPSE, x, y, w, h, mode)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.ARC) {
            require(!(len != 6 && len != 7)) { "Use createShape(ARC, x, y, w, h, start, stop)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.BOX) {
            require(is3D()) { "createShape(BOX) is not supported in 2D" }
            require(!(len != 1 && len != 3)) { "Use createShape(BOX, size) or createShape(BOX, width, height, depth)" }
            return createShapePrimitive(kind, *p)
        } else if (kind == PConstants.SPHERE) {
            require(is3D()) { "createShape(SPHERE) is not supported in 2D" }
            require(len == 1) { "Use createShape(SPHERE, radius)" }
            return createShapePrimitive(kind, *p)
        }
        throw IllegalArgumentException("Unknown shape type passed to createShape()")
    }

    /** Override this to have a custom shape object used by your renderer.  */
    protected open fun createShapePrimitive(kind: Int, vararg p: Float): PShape {
//    showMethodWarning("createShape()");
//    return null;
        return PShape(this, kind, *p)
    }

    //////////////////////////////////////////////////////////////
    // SHADERS
    open fun loadShader(fragFilename: String?): PShader? {
        showMissingWarning("loadShader")
        return null
    }

    open fun loadShader(fragFilename: String?, vertFilename: String?): PShader? {
        showMissingWarning("loadShader")
        return null
    }

    open fun shader(shader: PShader?) {
        showMissingWarning("shader")
    }

    open fun shader(shader: PShader?, kind: Int) {
        showMissingWarning("shader")
    }

    open fun resetShader() {
        showMissingWarning("resetShader")
    }

    open fun resetShader(kind: Int) {
        showMissingWarning("resetShader")
    }

    fun getShader(kind: Int): PShader? {
        showMissingWarning("getShader")
        return null
    }

    open fun filter(shader: PShader?) {
        showMissingWarning("filter")
    }

    //////////////////////////////////////////////////////////////
    // CURVE/BEZIER VERTEX HANDLING
    protected fun bezierVertexCheck(shape: Int = this.shape, vertexCount: Int = this.vertexCount) {
        if (shape == 0 || shape != PConstants.POLYGON) {
            throw RuntimeException("beginShape() or beginShape(POLYGON) " +
                    "must be used before bezierVertex() or quadraticVertex()")
        }
        if (vertexCount == 0) {
            throw RuntimeException("vertex() must be used at least once" +
                    "before bezierVertex() or quadraticVertex()")
        }
    }

    open fun bezierVertex(x2: Float, y2: Float,
                          x3: Float, y3: Float,
                          x4: Float, y4: Float) {
        bezierInitCheck()
        bezierVertexCheck()
        val draw = bezierDrawMatrix
        val prev = vertices[vertexCount - 1]
        var x1 = prev[PConstants.X]
        var y1 = prev[PConstants.Y]
        var xplot1 = draw!!.m10 * x1 + draw.m11 * x2 + draw.m12 * x3 + draw.m13 * x4
        var xplot2 = draw.m20 * x1 + draw.m21 * x2 + draw.m22 * x3 + draw.m23 * x4
        val xplot3 = draw.m30 * x1 + draw.m31 * x2 + draw.m32 * x3 + draw.m33 * x4
        var yplot1 = draw.m10 * y1 + draw.m11 * y2 + draw.m12 * y3 + draw.m13 * y4
        var yplot2 = draw.m20 * y1 + draw.m21 * y2 + draw.m22 * y3 + draw.m23 * y4
        val yplot3 = draw.m30 * y1 + draw.m31 * y2 + draw.m32 * y3 + draw.m33 * y4
        for (j in 0 until bezierDetail) {
            x1 += xplot1
            xplot1 += xplot2
            xplot2 += xplot3
            y1 += yplot1
            yplot1 += yplot2
            yplot2 += yplot3
            vertex(x1, y1)
        }
    }

    open fun bezierVertex(x2: Float, y2: Float, z2: Float,
                          x3: Float, y3: Float, z3: Float,
                          x4: Float, y4: Float, z4: Float) {
        bezierInitCheck()
        bezierVertexCheck()
        val draw = bezierDrawMatrix
        val prev = vertices[vertexCount - 1]
        var x1 = prev[PConstants.X]
        var y1 = prev[PConstants.Y]
        var z1 = prev[PConstants.Z]
        var xplot1 = draw!!.m10 * x1 + draw.m11 * x2 + draw.m12 * x3 + draw.m13 * x4
        var xplot2 = draw.m20 * x1 + draw.m21 * x2 + draw.m22 * x3 + draw.m23 * x4
        val xplot3 = draw.m30 * x1 + draw.m31 * x2 + draw.m32 * x3 + draw.m33 * x4
        var yplot1 = draw.m10 * y1 + draw.m11 * y2 + draw.m12 * y3 + draw.m13 * y4
        var yplot2 = draw.m20 * y1 + draw.m21 * y2 + draw.m22 * y3 + draw.m23 * y4
        val yplot3 = draw.m30 * y1 + draw.m31 * y2 + draw.m32 * y3 + draw.m33 * y4
        var zplot1 = draw.m10 * z1 + draw.m11 * z2 + draw.m12 * z3 + draw.m13 * z4
        var zplot2 = draw.m20 * z1 + draw.m21 * z2 + draw.m22 * z3 + draw.m23 * z4
        val zplot3 = draw.m30 * z1 + draw.m31 * z2 + draw.m32 * z3 + draw.m33 * z4
        for (j in 0 until bezierDetail) {
            x1 += xplot1
            xplot1 += xplot2
            xplot2 += xplot3
            y1 += yplot1
            yplot1 += yplot2
            yplot2 += yplot3
            z1 += zplot1
            zplot1 += zplot2
            zplot2 += zplot3
            vertex(x1, y1, z1)
        }
    }

    open fun quadraticVertex(cx: Float, cy: Float,
                             x3: Float, y3: Float) {
        bezierVertexCheck()
        val prev = vertices[vertexCount - 1]
        val x1 = prev[PConstants.X]
        val y1 = prev[PConstants.Y]
        bezierVertex(x1 + (cx - x1) * 2 / 3.0f, y1 + (cy - y1) * 2 / 3.0f,
                x3 + (cx - x3) * 2 / 3.0f, y3 + (cy - y3) * 2 / 3.0f,
                x3, y3)
    }

    open fun quadraticVertex(cx: Float, cy: Float, cz: Float,
                             x3: Float, y3: Float, z3: Float) {
        bezierVertexCheck()
        val prev = vertices[vertexCount - 1]
        val x1 = prev[PConstants.X]
        val y1 = prev[PConstants.Y]
        val z1 = prev[PConstants.Z]
        bezierVertex(x1 + (cx - x1) * 2 / 3.0f, y1 + (cy - y1) * 2 / 3.0f, z1 + (cz - z1) * 2 / 3.0f,
                x3 + (cx - x3) * 2 / 3.0f, y3 + (cy - y3) * 2 / 3.0f, z3 + (cz - z3) * 2 / 3.0f,
                x3, y3, z3)
    }

    protected open fun curveVertexCheck() {
        curveVertexCheck(shape)
    }

    /**
     * Perform initialization specific to curveVertex(), and handle standard
     * error modes. Can be overridden by subclasses that need the flexibility.
     */
    protected fun curveVertexCheck(shape: Int) {
        if (shape != PConstants.POLYGON) {
            throw RuntimeException("You must use beginShape() or " +
                    "beginShape(POLYGON) before curveVertex()")
        }
        // to improve code init time, allocate on first use.
        if (curveVertices == null) {
            curveVertices = Array(128) { FloatArray(3) }
        }
        if (curveVertexCount == curveVertices!!.size) {
            // Can't use PApplet.expand() cuz it doesn't do the copy properly
            val temp = Array(curveVertexCount shl 1) { FloatArray(3) }
            System.arraycopy(curveVertices, 0, temp, 0, curveVertexCount)
            curveVertices = temp
        }
        curveInitCheck()
    }

    open fun curveVertex(x: Float, y: Float) {
        curveVertexCheck()
        val vertex = curveVertices!![curveVertexCount]
        vertex[PConstants.X] = x
        vertex[PConstants.Y] = y
        curveVertexCount++

        // draw a segment if there are enough points
        if (curveVertexCount > 3) {
            curveVertexSegment(curveVertices!![curveVertexCount - 4][PConstants.X],
                    curveVertices!![curveVertexCount - 4][PConstants.Y],
                    curveVertices!![curveVertexCount - 3][PConstants.X],
                    curveVertices!![curveVertexCount - 3][PConstants.Y],
                    curveVertices!![curveVertexCount - 2][PConstants.X],
                    curveVertices!![curveVertexCount - 2][PConstants.Y],
                    curveVertices!![curveVertexCount - 1][PConstants.X],
                    curveVertices!![curveVertexCount - 1][PConstants.Y])
        }
    }

    open fun curveVertex(x: Float, y: Float, z: Float) {
        curveVertexCheck()
        val vertex = curveVertices!![curveVertexCount]
        vertex[PConstants.X] = x
        vertex[PConstants.Y] = y
        vertex[PConstants.Z] = z
        curveVertexCount++

        // draw a segment if there are enough points
        if (curveVertexCount > 3) {
            curveVertexSegment(curveVertices!![curveVertexCount - 4][PConstants.X],
                    curveVertices!![curveVertexCount - 4][PConstants.Y],
                    curveVertices!![curveVertexCount - 4][PConstants.Z],
                    curveVertices!![curveVertexCount - 3][PConstants.X],
                    curveVertices!![curveVertexCount - 3][PConstants.Y],
                    curveVertices!![curveVertexCount - 3][PConstants.Z],
                    curveVertices!![curveVertexCount - 2][PConstants.X],
                    curveVertices!![curveVertexCount - 2][PConstants.Y],
                    curveVertices!![curveVertexCount - 2][PConstants.Z],
                    curveVertices!![curveVertexCount - 1][PConstants.X],
                    curveVertices!![curveVertexCount - 1][PConstants.Y],
                    curveVertices!![curveVertexCount - 1][PConstants.Z])
        }
    }

    /**
     * Handle emitting a specific segment of Catmull-Rom curve. This can be
     * overridden by subclasses that need more efficient rendering options.
     */
    protected open fun curveVertexSegment(x1: Float, y1: Float,
                                          x2: Float, y2: Float,
                                          x3: Float, y3: Float,
                                          x4: Float, y4: Float) {
        var x0 = x2
        var y0 = y2
        val draw = curveDrawMatrix
        var xplot1 = draw!!.m10 * x1 + draw.m11 * x2 + draw.m12 * x3 + draw.m13 * x4
        var xplot2 = draw.m20 * x1 + draw.m21 * x2 + draw.m22 * x3 + draw.m23 * x4
        val xplot3 = draw.m30 * x1 + draw.m31 * x2 + draw.m32 * x3 + draw.m33 * x4
        var yplot1 = draw.m10 * y1 + draw.m11 * y2 + draw.m12 * y3 + draw.m13 * y4
        var yplot2 = draw.m20 * y1 + draw.m21 * y2 + draw.m22 * y3 + draw.m23 * y4
        val yplot3 = draw.m30 * y1 + draw.m31 * y2 + draw.m32 * y3 + draw.m33 * y4

        // vertex() will reset splineVertexCount, so save it
        val savedCount = curveVertexCount
        vertex(x0, y0)
        for (j in 0 until curveDetail) {
            x0 += xplot1
            xplot1 += xplot2
            xplot2 += xplot3
            y0 += yplot1
            yplot1 += yplot2
            yplot2 += yplot3
            vertex(x0, y0)
        }
        curveVertexCount = savedCount
    }

    /**
     * Handle emitting a specific segment of Catmull-Rom curve. This can be
     * overridden by subclasses that need more efficient rendering options.
     */
    protected fun curveVertexSegment(x1: Float, y1: Float, z1: Float,
                                     x2: Float, y2: Float, z2: Float,
                                     x3: Float, y3: Float, z3: Float,
                                     x4: Float, y4: Float, z4: Float) {
        var x0 = x2
        var y0 = y2
        var z0 = z2
        val draw = curveDrawMatrix
        var xplot1 = draw!!.m10 * x1 + draw.m11 * x2 + draw.m12 * x3 + draw.m13 * x4
        var xplot2 = draw.m20 * x1 + draw.m21 * x2 + draw.m22 * x3 + draw.m23 * x4
        val xplot3 = draw.m30 * x1 + draw.m31 * x2 + draw.m32 * x3 + draw.m33 * x4
        var yplot1 = draw.m10 * y1 + draw.m11 * y2 + draw.m12 * y3 + draw.m13 * y4
        var yplot2 = draw.m20 * y1 + draw.m21 * y2 + draw.m22 * y3 + draw.m23 * y4
        val yplot3 = draw.m30 * y1 + draw.m31 * y2 + draw.m32 * y3 + draw.m33 * y4

        // vertex() will reset splineVertexCount, so save it
        val savedCount = curveVertexCount
        var zplot1 = draw.m10 * z1 + draw.m11 * z2 + draw.m12 * z3 + draw.m13 * z4
        var zplot2 = draw.m20 * z1 + draw.m21 * z2 + draw.m22 * z3 + draw.m23 * z4
        val zplot3 = draw.m30 * z1 + draw.m31 * z2 + draw.m32 * z3 + draw.m33 * z4
        vertex(x0, y0, z0)
        for (j in 0 until curveDetail) {
            x0 += xplot1
            xplot1 += xplot2
            xplot2 += xplot3
            y0 += yplot1
            yplot1 += yplot2
            yplot2 += yplot3
            z0 += zplot1
            zplot1 += zplot2
            zplot2 += zplot3
            vertex(x0, y0, z0)
        }
        curveVertexCount = savedCount
    }

    //////////////////////////////////////////////////////////////
    // SIMPLE SHAPES WITH ANALOGUES IN beginShape()
    open fun point(x: Float, y: Float) {
        beginShape(PConstants.POINTS)
        vertex(x, y)
        endShape()
    }

    open fun point(x: Float, y: Float, z: Float) {
        beginShape(PConstants.POINTS)
        vertex(x, y, z)
        endShape()
    }

    open fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        beginShape(PConstants.LINES)
        vertex(x1, y1)
        vertex(x2, y2)
        endShape()
    }

    open fun line(x1: Float, y1: Float, z1: Float,
                  x2: Float, y2: Float, z2: Float) {
        beginShape(PConstants.LINES)
        vertex(x1, y1, z1)
        vertex(x2, y2, z2)
        endShape()
    }

    open fun triangle(x1: Float, y1: Float, x2: Float, y2: Float,
                      x3: Float, y3: Float) {
        beginShape(PConstants.TRIANGLES)
        vertex(x1, y1)
        vertex(x2, y2)
        vertex(x3, y3)
        endShape()
    }

    open fun quad(x1: Float, y1: Float, x2: Float, y2: Float,
                  x3: Float, y3: Float, x4: Float, y4: Float) {
        beginShape(PConstants.QUADS)
        vertex(x1, y1)
        vertex(x2, y2)
        vertex(x3, y3)
        vertex(x4, y4)
        endShape()
    }

    //////////////////////////////////////////////////////////////
    // RECT
    fun rectMode(mode: Int) {
        rectMode = mode
    }

    fun rect(a: Float, b: Float, c: Float, d: Float) {
        var a = a
        var b = b
        var c = c
        var d = d
        val hradius: Float
        val vradius: Float
        when (rectMode) {
            PConstants.CORNERS -> {
            }
            PConstants.CORNER -> {
                c += a
                d += b
            }
            PConstants.RADIUS -> {
                hradius = c
                vradius = d
                c = a + hradius
                d = b + vradius
                a -= hradius
                b -= vradius
            }
            PConstants.CENTER -> {
                hradius = c / 2.0f
                vradius = d / 2.0f
                c = a + hradius
                d = b + vradius
                a -= hradius
                b -= vradius
            }
        }
        if (a > c) {
            val temp = a
            a = c
            c = temp
        }
        if (b > d) {
            val temp = b
            b = d
            d = temp
        }
        rectImpl(a, b, c, d)
    }

    protected open fun rectImpl(x1: Float, y1: Float, x2: Float, y2: Float) {
        quad(x1, y1, x2, y1, x2, y2, x1, y2)
    }

    fun rect(a: Float, b: Float, c: Float, d: Float, r: Float) {
        rect(a, b, c, d, r, r, r, r)
    }

    fun rect(a: Float, b: Float, c: Float, d: Float,
             tl: Float, tr: Float, br: Float, bl: Float) {
        var a = a
        var b = b
        var c = c
        var d = d
        var tl = tl
        var tr = tr
        var br = br
        var bl = bl
        val hradius: Float
        val vradius: Float
        when (rectMode) {
            PConstants.CORNERS -> {
            }
            PConstants.CORNER -> {
                c += a
                d += b
            }
            PConstants.RADIUS -> {
                hradius = c
                vradius = d
                c = a + hradius
                d = b + vradius
                a -= hradius
                b -= vradius
            }
            PConstants.CENTER -> {
                hradius = c / 2.0f
                vradius = d / 2.0f
                c = a + hradius
                d = b + vradius
                a -= hradius
                b -= vradius
            }
        }
        if (a > c) {
            val temp = a
            a = c
            c = temp
        }
        if (b > d) {
            val temp = b
            b = d
            d = temp
        }
        val maxRounding = PApplet.min((c - a) / 2, (d - b) / 2)
        if (tl > maxRounding) tl = maxRounding
        if (tr > maxRounding) tr = maxRounding
        if (br > maxRounding) br = maxRounding
        if (bl > maxRounding) bl = maxRounding
        rectImpl(a, b, c, d, tl, tr, br, bl)
    }

    protected open fun rectImpl(x1: Float, y1: Float, x2: Float, y2: Float,
                                tl: Float, tr: Float, br: Float, bl: Float) {
        beginShape()
        //    vertex(x1+tl, y1);
        if (tr != 0f) {
            vertex(x2 - tr, y1)
            quadraticVertex(x2, y1, x2, y1 + tr)
        } else {
            vertex(x2, y1)
        }
        if (br != 0f) {
            vertex(x2, y2 - br)
            quadraticVertex(x2, y2, x2 - br, y2)
        } else {
            vertex(x2, y2)
        }
        if (bl != 0f) {
            vertex(x1 + bl, y2)
            quadraticVertex(x1, y2, x1, y2 - bl)
        } else {
            vertex(x1, y2)
        }
        if (tl != 0f) {
            vertex(x1, y1 + tl)
            quadraticVertex(x1, y1, x1 + tl, y1)
        } else {
            vertex(x1, y1)
        }
        //    endShape();
        endShape(PConstants.CLOSE)
    }

    fun square(x: Float, y: Float, extent: Float) {
        rect(x, y, extent, extent)
    }

    //////////////////////////////////////////////////////////////
    // ELLIPSE AND ARC
    fun ellipseMode(mode: Int) {
        ellipseMode = mode
    }

    fun ellipse(a: Float, b: Float, c: Float, d: Float) {
        var x = a
        var y = b
        var w = c
        var h = d
        if (ellipseMode == PConstants.CORNERS) {
            w = c - a
            h = d - b
        } else if (ellipseMode == PConstants.RADIUS) {
            x = a - c
            y = b - d
            w = c * 2
            h = d * 2
        } else if (ellipseMode == PConstants.DIAMETER) {
            x = a - c / 2f
            y = b - d / 2f
        }
        if (w < 0) {  // undo negative width
            x += w
            w = -w
        }
        if (h < 0) {  // undo negative height
            y += h
            h = -h
        }
        ellipseImpl(x, y, w, h)
    }

    protected open fun ellipseImpl(x: Float, y: Float, w: Float, h: Float) {}

    /**
     * Identical parameters and placement to ellipse,
     * but draws only an arc of that ellipse.
     *
     *
     * start and stop are always radians because angleMode() was goofy.
     * ellipseMode() sets the placement.
     *
     *
     * also tries to be smart about start < stop.
     */
    @JvmOverloads
    fun arc(a: Float, b: Float, c: Float, d: Float,
            start: Float, stop: Float, mode: Int = 0) {
        var start = start
        var stop = stop
        var x = a
        var y = b
        var w = c
        var h = d
        if (ellipseMode == PConstants.CORNERS) {
            w = c - a
            h = d - b
        } else if (ellipseMode == PConstants.RADIUS) {
            x = a - c
            y = b - d
            w = c * 2
            h = d * 2
        } else if (ellipseMode == PConstants.CENTER) {
            x = a - c / 2f
            y = b - d / 2f
        }

        // make sure the loop will exit before starting while
        if (!java.lang.Float.isInfinite(start) && !java.lang.Float.isInfinite(stop)) {
            // ignore equal and degenerate cases
            if (stop > start) {
                // make sure that we're starting at a useful point
                while (start < 0) {
                    start += PConstants.TWO_PI
                    stop += PConstants.TWO_PI
                }
                if (stop - start > PConstants.TWO_PI) {
                    start = 0f
                    stop = PConstants.TWO_PI
                }
                arcImpl(x, y, w, h, start, stop, mode)
            }
        }
    }

    /**
     * Start and stop are in radians, converted by the parent function.
     * Note that the radians can be greater (or less) than TWO_PI.
     * This is so that an arc can be drawn that crosses zero mark,
     * and the user will still collect $200.
     */
    protected open fun arcImpl(x: Float, y: Float, w: Float, h: Float,
                               start: Float, stop: Float, mode: Int) {
        showMissingWarning("arc")
    }

    fun circle(x: Float, y: Float, extent: Float) {
        ellipse(x, y, extent, extent)
    }

    //////////////////////////////////////////////////////////////
    // BOX
    fun box(size: Float) {
        box(size, size, size)
    }

    // TODO not the least bit efficient, it even redraws lines
    // along the vertices. ugly ugly ugly!
    open fun box(w: Float, h: Float, d: Float) {
        val x1 = -w / 2f
        val x2 = w / 2f
        val y1 = -h / 2f
        val y2 = h / 2f
        val z1 = -d / 2f
        val z2 = d / 2f
        beginShape(PConstants.QUADS)

        // front
        normal(0f, 0f, 1f)
        vertex(x1, y1, z1)
        vertex(x2, y1, z1)
        vertex(x2, y2, z1)
        vertex(x1, y2, z1)

        // right
        normal(1f, 0f, 0f)
        vertex(x2, y1, z1)
        vertex(x2, y1, z2)
        vertex(x2, y2, z2)
        vertex(x2, y2, z1)

        // back
        normal(0f, 0f, -1f)
        vertex(x2, y1, z2)
        vertex(x1, y1, z2)
        vertex(x1, y2, z2)
        vertex(x2, y2, z2)

        // left
        normal(-1f, 0f, 0f)
        vertex(x1, y1, z2)
        vertex(x1, y1, z1)
        vertex(x1, y2, z1)
        vertex(x1, y2, z2)

        // top
        normal(0f, 1f, 0f)
        vertex(x1, y1, z2)
        vertex(x2, y1, z2)
        vertex(x2, y1, z1)
        vertex(x1, y1, z1)

        // bottom
        normal(0f, -1f, 0f)
        vertex(x1, y2, z1)
        vertex(x2, y2, z1)
        vertex(x2, y2, z2)
        vertex(x1, y2, z2)
        endShape()
    }

    //////////////////////////////////////////////////////////////
    // SPHERE
    fun sphereDetail(res: Int) {
        sphereDetail(res, res)
    }

    /**
     * Set the detail level for approximating a sphere. The ures and vres params
     * control the horizontal and vertical resolution.
     *
     * Code for sphereDetail() submitted by toxi [031031].
     * Code for enhanced u/v version from davbol [080801].
     */
    fun sphereDetail(ures: Int, vres: Int) {
        var ures = ures
        var vres = vres
        if (ures < 3) ures = 3 // force a minimum res
        if (vres < 2) vres = 2 // force a minimum res
        if (ures == sphereDetailU && vres == sphereDetailV) return
        val delta = SINCOS_LENGTH.toFloat() / ures
        val cx = FloatArray(ures)
        val cz = FloatArray(ures)
        // calc unit circle in XZ plane
        for (i in 0 until ures) {
            cx[i] = cosLUT[(i * delta).toInt() % SINCOS_LENGTH]
            cz[i] = sinLUT[(i * delta).toInt() % SINCOS_LENGTH]
        }
        // computing vertexlist
        // vertexlist starts at south pole
        val vertCount = ures * (vres - 1) + 2
        var currVert = 0

        // re-init arrays to store vertices
        sphereX = FloatArray(vertCount)
        sphereY = FloatArray(vertCount)
        sphereZ = FloatArray(vertCount)
        val angle_step = SINCOS_LENGTH * 0.5f / vres
        var angle = angle_step

        // step along Y axis
        for (i in 1 until vres) {
            val curradius = sinLUT[angle.toInt() % SINCOS_LENGTH]
            val currY = cosLUT[angle.toInt() % SINCOS_LENGTH]
            for (j in 0 until ures) {
                sphereX[currVert] = cx[j] * curradius
                sphereY[currVert] = currY
                sphereZ[currVert++] = cz[j] * curradius
            }
            angle += angle_step
        }
        sphereDetailU = ures
        sphereDetailV = vres
    }

    /**
     * Draw a sphere with radius r centered at coordinate 0, 0, 0.
     * <P>
     * Implementation notes:
    </P> * <P>
     * cache all the points of the sphere in a static array
     * top and bottom are just a bunch of triangles that land
     * in the center point
    </P> * <P>
     * sphere is a series of concentric circles who radii vary
     * along the shape, based on, er.. cos or something
    </P> * <PRE>
     * [toxi 031031] new sphere code. removed all multiplies with
     * radius, as scale() will take care of that anyway
     *
     * [toxi 031223] updated sphere code (removed modulos)
     * and introduced sphereAt(x,y,z,r)
     * to avoid additional translate()'s on the user/sketch side
     *
     * [davbol 080801] now using separate sphereDetailU/V
    </PRE> *
     */
    open fun sphere(r: Float) {
        if (sphereDetailU < 3 || sphereDetailV < 2) {
            sphereDetail(30)
        }
        edge(false)

        // 1st ring from south pole
        beginShape(PConstants.TRIANGLE_STRIP)
        for (i in 0 until sphereDetailU) {
            normal(0f, -1f, 0f)
            vertex(0f, -r, 0f)
            normal(sphereX[i], sphereY[i], sphereZ[i])
            vertex(r * sphereX[i], r * sphereY[i], r * sphereZ[i])
        }
        normal(0f, -r, 0f)
        vertex(0f, -r, 0f)
        normal(sphereX[0], sphereY[0], sphereZ[0])
        vertex(r * sphereX[0], r * sphereY[0], r * sphereZ[0])
        endShape()
        var v1: Int
        var v11: Int
        var v2: Int

        // middle rings
        var voff = 0
        for (i in 2 until sphereDetailV) {
            v11 = voff
            v1 = v11
            voff += sphereDetailU
            v2 = voff
            beginShape(PConstants.TRIANGLE_STRIP)
            for (j in 0 until sphereDetailU) {
                normal(sphereX[v1], sphereY[v1], sphereZ[v1])
                vertex(r * sphereX[v1], r * sphereY[v1], r * sphereZ[v1++])
                normal(sphereX[v2], sphereY[v2], sphereZ[v2])
                vertex(r * sphereX[v2], r * sphereY[v2], r * sphereZ[v2++])
            }
            // close each ring
            v1 = v11
            v2 = voff
            normal(sphereX[v1], sphereY[v1], sphereZ[v1])
            vertex(r * sphereX[v1], r * sphereY[v1], r * sphereZ[v1])
            normal(sphereX[v2], sphereY[v2], sphereZ[v2])
            vertex(r * sphereX[v2], r * sphereY[v2], r * sphereZ[v2])
            endShape()
        }

        // add the northern cap
        beginShape(PConstants.TRIANGLE_STRIP)
        for (i in 0 until sphereDetailU) {
            v2 = voff + i
            normal(sphereX[v2], sphereY[v2], sphereZ[v2])
            vertex(r * sphereX[v2], r * sphereY[v2], r * sphereZ[v2])
            normal(0f, 1f, 0f)
            vertex(0f, r, 0f)
        }
        normal(sphereX[voff], sphereY[voff], sphereZ[voff])
        vertex(r * sphereX[voff], r * sphereY[voff], r * sphereZ[voff])
        normal(0f, 1f, 0f)
        vertex(0f, r, 0f)
        endShape()
        edge(true)
    }
    //////////////////////////////////////////////////////////////
    // BEZIER
    /**
     * Evalutes quadratic bezier at point t for points a, b, c, d.
     * t varies between 0 and 1, and a and d are the on curve points,
     * b and c are the control points. this can be done once with the
     * x coordinates and a second time with the y coordinates to get
     * the location of a bezier curve at t.
     * <P>
     * For instance, to convert the following example:</P><PRE>
     * stroke(255, 102, 0);
     * line(85, 20, 10, 10);
     * line(90, 90, 15, 80);
     * stroke(0, 0, 0);
     * bezier(85, 20, 10, 10, 90, 90, 15, 80);
     *
     * // draw it in gray, using 10 steps instead of the default 20
     * // this is a slower way to do it, but useful if you need
     * // to do things with the coordinates at each step
     * stroke(128);
     * beginShape(LINE_STRIP);
     * for (int i = 0; i <= 10; i++) {
     * float t = i / 10.0f;
     * float x = bezierPoint(85, 10, 90, 15, t);
     * float y = bezierPoint(20, 10, 90, 80, t);
     * vertex(x, y);
     * }
     * endShape();</PRE>
     */
    fun bezierPoint(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val t1 = t - 1.0f
        return t * (3 * t1 * (b * t1 - c * t) + d * t * t) - a * t1 * t1 * t1
    }

    /**
     * Provide the tangent at the given point on the bezier curve.
     * Fix from davbol for 0136.
     */
    fun bezierTangent(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        return 3 * t * t * (-a + 3 * b - 3 * c + d) + 6 * t * (a - 2 * b + c) + 3 * (-a + b)
    }

    protected fun bezierInitCheck() {
        if (!bezierInited) {
            bezierInit()
        }
    }

    protected fun bezierInit() {
        // overkill to be broken out, but better parity with the curve stuff below
        bezierDetail(bezierDetail)
        bezierInited = true
    }

    open fun bezierDetail(detail: Int) {
        bezierDetail = detail
        if (bezierDrawMatrix == null) {
            bezierDrawMatrix = PMatrix3D()
        }

        // setup matrix for forward differencing to speed up drawing
        splineForward(detail, bezierDrawMatrix!!)

        // multiply the basis and forward diff matrices together
        // saves much time since this needn't be done for each curve
        //mult_spline_matrix(bezierForwardMatrix, bezier_basis, bezierDrawMatrix, 4);
        //bezierDrawMatrix.set(bezierForwardMatrix);
        bezierDrawMatrix!!.apply(bezierBasisMatrix)
    }

    /**
     * Draw a cubic bezier curve. The first and last points are
     * the on-curve points. The middle two are the 'control' points,
     * or 'handles' in an application like Illustrator.
     * <P>
     * Identical to typing:
    </P> * <PRE>beginShape();
     * vertex(x1, y1);
     * bezierVertex(x2, y2, x3, y3, x4, y4);
     * endShape();
    </PRE> *
     * In Postscript-speak, this would be:
     * <PRE>moveto(x1, y1);
     * curveto(x2, y2, x3, y3, x4, y4);</PRE>
     * If you were to try and continue that curve like so:
     * <PRE>curveto(x5, y5, x6, y6, x7, y7);</PRE>
     * This would be done in processing by adding these statements:
     * <PRE>bezierVertex(x5, y5, x6, y6, x7, y7)
    </PRE> *
     * To draw a quadratic (instead of cubic) curve,
     * use the control point twice by doubling it:
     * <PRE>bezier(x1, y1, cx, cy, cx, cy, x2, y2);</PRE>
     */
    fun bezier(x1: Float, y1: Float,
               x2: Float, y2: Float,
               x3: Float, y3: Float,
               x4: Float, y4: Float) {
        beginShape()
        vertex(x1, y1)
        bezierVertex(x2, y2, x3, y3, x4, y4)
        endShape()
    }

    fun bezier(x1: Float, y1: Float, z1: Float,
               x2: Float, y2: Float, z2: Float,
               x3: Float, y3: Float, z3: Float,
               x4: Float, y4: Float, z4: Float) {
        beginShape()
        vertex(x1, y1, z1)
        bezierVertex(x2, y2, z2,
                x3, y3, z3,
                x4, y4, z4)
        endShape()
    }
    //////////////////////////////////////////////////////////////
    // CATMULL-ROM CURVE
    /**
     * Get a location along a catmull-rom curve segment.
     *
     * @param t Value between zero and one for how far along the segment
     */
    fun curvePoint(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        curveInitCheck()
        val tt = t * t
        val ttt = t * tt
        val cb = curveBasisMatrix

        // not optimized (and probably need not be)
        return a * (ttt * cb!!.m00 + tt * cb.m10 + t * cb.m20 + cb.m30) + b * (ttt * cb.m01 + tt * cb.m11 + t * cb.m21 + cb.m31) + c * (ttt * cb.m02 + tt * cb.m12 + t * cb.m22 + cb.m32) + d * (ttt * cb.m03 + tt * cb.m13 + t * cb.m23 + cb.m33)
    }

    /**
     * Calculate the tangent at a t value (0..1) on a Catmull-Rom curve.
     * Code thanks to Dave Bollinger (Bug #715)
     */
    fun curveTangent(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        curveInitCheck()
        val tt3 = t * t * 3
        val t2 = t * 2
        val cb = curveBasisMatrix

        // not optimized (and probably need not be)
        return a * (tt3 * cb!!.m00 + t2 * cb.m10 + cb.m20) + b * (tt3 * cb.m01 + t2 * cb.m11 + cb.m21) + c * (tt3 * cb.m02 + t2 * cb.m12 + cb.m22) + d * (tt3 * cb.m03 + t2 * cb.m13 + cb.m23)
    }

    open fun curveDetail(detail: Int) {
        curveDetail = detail
        curveInit()
    }

    fun curveTightness(tightness: Float) {
        curveTightness = tightness
        curveInit()
    }

    protected fun curveInitCheck() {
        if (!curveInited) {
            curveInit()
        }
    }

    /**
     * Set the number of segments to use when drawing a Catmull-Rom
     * curve, and setting the s parameter, which defines how tightly
     * the curve fits to each vertex. Catmull-Rom curves are actually
     * a subset of this curve type where the s is set to zero.
     * <P>
     * (This function is not optimized, since it's not expected to
     * be called all that often. there are many juicy and obvious
     * opimizations in here, but it's probably better to keep the
     * code more readable)
    </P> */
    protected fun curveInit() {
        // allocate only if/when used to save startup time
        if (curveDrawMatrix == null) {
            curveBasisMatrix = PMatrix3D()
            curveDrawMatrix = PMatrix3D()
            curveInited = true
        }
        val s = curveTightness
        curveBasisMatrix!![(s - 1) / 2f, (s + 3) / 2f, (-3 - s) / 2f, (1 - s) / 2f, 1 - s, (-5 - s) / 2f, s + 2, (s - 1) / 2f, (s - 1) / 2f, 0f, (1 - s) / 2f, 0f, 0f, 1f, 0f] = 0f

        //setup_spline_forward(segments, curveForwardMatrix);
        splineForward(curveDetail, curveDrawMatrix!!)
        if (bezierBasisInverse == null) {
            bezierBasisInverse = bezierBasisMatrix.get()
            bezierBasisInverse!!.invert()
            curveToBezierMatrix = PMatrix3D()
        }

        // TODO only needed for PGraphicsJava2D? if so, move it there
        // actually, it's generally useful for other renderers, so keep it
        // or hide the implementation elsewhere.
        curveToBezierMatrix!!.set(curveBasisMatrix)
        curveToBezierMatrix!!.preApply(bezierBasisInverse)

        // multiply the basis and forward diff matrices together
        // saves much time since this needn't be done for each curve
        curveDrawMatrix!!.apply(curveBasisMatrix)
    }

    /**
     * Draws a segment of Catmull-Rom curve.
     * <P>
     * As of 0070, this function no longer doubles the first and
     * last points. The curves are a bit more boring, but it's more
     * mathematically correct, and properly mirrored in curvePoint().
    </P> * <P>
     * Identical to typing out:</P><PRE>
     * beginShape();
     * curveVertex(x1, y1);
     * curveVertex(x2, y2);
     * curveVertex(x3, y3);
     * curveVertex(x4, y4);
     * endShape();
    </PRE> *
     */
    fun curve(x1: Float, y1: Float,
              x2: Float, y2: Float,
              x3: Float, y3: Float,
              x4: Float, y4: Float) {
        beginShape()
        curveVertex(x1, y1)
        curveVertex(x2, y2)
        curveVertex(x3, y3)
        curveVertex(x4, y4)
        endShape()
    }

    fun curve(x1: Float, y1: Float, z1: Float,
              x2: Float, y2: Float, z2: Float,
              x3: Float, y3: Float, z3: Float,
              x4: Float, y4: Float, z4: Float) {
        beginShape()
        curveVertex(x1, y1, z1)
        curveVertex(x2, y2, z2)
        curveVertex(x3, y3, z3)
        curveVertex(x4, y4, z4)
        endShape()
    }
    //////////////////////////////////////////////////////////////
    // SPLINE UTILITY FUNCTIONS (used by both Bezier and Catmull-Rom)
    /**
     * Setup forward-differencing matrix to be used for speedy
     * curve rendering. It's based on using a specific number
     * of curve segments and just doing incremental adds for each
     * vertex of the segment, rather than running the mathematically
     * expensive cubic equation.
     * @param segments number of curve segments to use when drawing
     * @param matrix target object for the new matrix
     */
    protected fun splineForward(segments: Int, matrix: PMatrix3D) {
        val f = 1.0f / segments
        val ff = f * f
        val fff = ff * f
        matrix[0f, 0f, 0f, 1f, fff, ff, f, 0f, 6 * fff, 2 * ff, 0f, 0f, 6 * fff, 0f, 0f] = 0f
    }

    //////////////////////////////////////////////////////////////
    // SMOOTHING
    fun smooth() {  // ignore
        smooth(1)
    }

    open fun smooth(quality: Int) {  // ignore
        if (primaryGraphics) {
            parent!!.smooth(quality)
        } else {
            // for createGraphics(), make sure beginDraw() not called yet
            if (settingsInited) {
                // ignore if it's just a repeat of the current state
                if (smooth != quality) {
                    smoothWarning("smooth")
                }
            } else {
                smooth = quality
            }
        }
    }

    open fun noSmooth() {  // ignore
        smooth(0)
    }

    private fun smoothWarning(method: String) {
        showWarning("%s() can only be used before beginDraw()", method)
    }
    //////////////////////////////////////////////////////////////
    // IMAGE
    /**
     * The mode can only be set to CORNERS, CORNER, and CENTER.
     *
     *
     * Support for CENTER was added in release 0146.
     */
    fun imageMode(mode: Int) {
        imageMode = if (mode == PConstants.CORNER || mode == PConstants.CORNERS || mode == PConstants.CENTER) {
            mode
        } else {
            val msg = "imageMode() only works with CORNER, CORNERS, or CENTER"
            throw RuntimeException(msg)
        }
    }

    fun image(image: PImage, x: Float, y: Float) {
        // Starting in release 0144, image errors are simply ignored.
        // loadImageAsync() sets width and height to -1 when loading fails.
        if (image.width == -1 || image.height == -1) return

        // If not loaded yet, don't try to draw
        if (image.width == 0 || image.height == 0) return
        if (imageMode == PConstants.CORNER || imageMode == PConstants.CORNERS) {
            imageImpl(image,
                    x, y, x + image.width, y + image.height,
                    0, 0, image.width, image.height)
        } else if (imageMode == PConstants.CENTER) {
            val x1 = x - image.width / 2
            val y1 = y - image.height / 2
            imageImpl(image,
                    x1, y1, x1 + image.width, y1 + image.height,
                    0, 0, image.width, image.height)
        }
    }

    fun image(image: PImage, x: Float, y: Float, c: Float, d: Float) {
        image(image, x, y, c, d, 0, 0, image.width, image.height)
    }

    /**
     * Draw an image(), also specifying u/v coordinates.
     * In this method, the  u, v coordinates are always based on image space
     * location, regardless of the current textureMode().
     */
    fun image(image: PImage,
              a: Float, b: Float, c: Float, d: Float,
              u1: Int, v1: Int, u2: Int, v2: Int) {
        // Starting in release 0144, image errors are simply ignored.
        // loadImageAsync() sets width and height to -1 when loading fails.
        var a = a
        var b = b
        var c = c
        var d = d
        if (image.width == -1 || image.height == -1) return
        if (imageMode == PConstants.CORNER) {
            if (c < 0) {  // reset a negative width
                a += c
                c = -c
            }
            if (d < 0) {  // reset a negative height
                b += d
                d = -d
            }
            imageImpl(image,
                    a, b, a + c, b + d,
                    u1, v1, u2, v2)
        } else if (imageMode == PConstants.CORNERS) {
            if (c < a) {  // reverse because x2 < x1
                val temp = a
                a = c
                c = temp
            }
            if (d < b) {  // reverse because y2 < y1
                val temp = b
                b = d
                d = temp
            }
            imageImpl(image,
                    a, b, c, d,
                    u1, v1, u2, v2)
        } else if (imageMode == PConstants.CENTER) {
            // c and d are width/height
            if (c < 0) c = -c
            if (d < 0) d = -d
            val x1 = a - c / 2
            val y1 = b - d / 2
            imageImpl(image,
                    x1, y1, x1 + c, y1 + d,
                    u1, v1, u2, v2)
        }
    }

    /**
     * Expects x1, y1, x2, y2 coordinates where (x2 >= x1) and (y2 >= y1).
     * If tint() has been called, the image will be colored.
     *
     *
     * The default implementation draws an image as a textured quad.
     * The (u, v) coordinates are in image space (they're ints, after all..)
     */
    protected open fun imageImpl(image: PImage?,
                                 x1: Float, y1: Float, x2: Float, y2: Float,
                                 u1: Int, v1: Int, u2: Int, v2: Int) {
        val savedStroke = stroke
        val savedFill = fill
        val savedTextureMode = textureMode
        stroke = false
        fill = true
        textureMode = PConstants.IMAGE
        val savedFillR = fillR
        val savedFillG = fillG
        val savedFillB = fillB
        val savedFillA = fillA
        if (tint) {
            fillR = tintR
            fillG = tintG
            fillB = tintB
            fillA = tintA
        } else {
            fillR = 1f
            fillG = 1f
            fillB = 1f
            fillA = 1f
        }
        beginShape(PConstants.QUADS)
        texture(image)
        vertex(x1, y1, u1.toFloat(), v1.toFloat())
        vertex(x1, y2, u1.toFloat(), v2.toFloat())
        vertex(x2, y2, u2.toFloat(), v2.toFloat())
        vertex(x2, y1, u2.toFloat(), v1.toFloat())
        endShape()
        stroke = savedStroke
        fill = savedFill
        textureMode = savedTextureMode
        fillR = savedFillR
        fillG = savedFillG
        fillB = savedFillB
        fillA = savedFillA
    }
    //////////////////////////////////////////////////////////////
    // SHAPE
    /**
     * Set the orientation for the shape() command (like imageMode() or rectMode()).
     * @param mode Either CORNER, CORNERS, or CENTER.
     */
    fun shapeMode(mode: Int) {
        shapeMode = mode
    }

    open fun shape(shape: PShape) {
        if (shape.isVisible) {  // don't do expensive matrix ops if invisible
            if (shapeMode == PConstants.CENTER) {
                pushMatrix()
                translate(-shape.getWidth() / 2, -shape.getHeight() / 2)
            }
            shape.draw(this) // needs to handle recorder too
            if (shapeMode == PConstants.CENTER) {
                popMatrix()
            }
        }
    }

    /**
     * Convenience method to draw at a particular location.
     */
    open fun shape(shape: PShape, x: Float, y: Float) {
        if (shape.isVisible) {  // don't do expensive matrix ops if invisible
            pushMatrix()
            if (shapeMode == PConstants.CENTER) {
                translate(x - shape.getWidth() / 2, y - shape.getHeight() / 2)
            } else if (shapeMode == PConstants.CORNER || shapeMode == PConstants.CORNERS) {
                translate(x, y)
            }
            shape.draw(this)
            popMatrix()
        }
    }

    // TODO unapproved
    protected open fun shape(shape: PShape?, x: Float, y: Float, z: Float) {
        showMissingWarning("shape")
    }

    open fun shape(shape: PShape, x: Float, y: Float, c: Float, d: Float) {
        var c = c
        var d = d
        if (shape.isVisible) {  // don't do expensive matrix ops if invisible
            pushMatrix()
            if (shapeMode == PConstants.CENTER) {
                // x and y are center, c and d refer to a diameter
                translate(x - c / 2f, y - d / 2f)
                scale(c / shape.getWidth(), d / shape.getHeight())
            } else if (shapeMode == PConstants.CORNER) {
                translate(x, y)
                scale(c / shape.getWidth(), d / shape.getHeight())
            } else if (shapeMode == PConstants.CORNERS) {
                // c and d are x2/y2, make them into width/height
                c -= x
                d -= y
                // then same as above
                translate(x, y)
                scale(c / shape.getWidth(), d / shape.getHeight())
            }
            shape.draw(this)
            popMatrix()
        }
    }

    // TODO unapproved
    protected open fun shape(shape: PShape?, x: Float, y: Float, z: Float, c: Float, d: Float, e: Float) {
        showMissingWarning("shape")
    }
    //////////////////////////////////////////////////////////////
    // TEXT/FONTS
    /**
     * Sets the alignment of the text to one of LEFT, CENTER, or RIGHT.
     * This will also reset the vertical text alignment to BASELINE.
     */
    fun textAlign(align: Int) {
        textAlign(align, PConstants.BASELINE)
    }

    /**
     * Sets the horizontal and vertical alignment of the text. The horizontal
     * alignment can be one of LEFT, CENTER, or RIGHT. The vertical alignment
     * can be TOP, BOTTOM, CENTER, or the BASELINE (the default).
     */
    fun textAlign(alignX: Int, alignY: Int) {
        textAlign = alignX
        textAlignY = alignY
    }

    /**
     * Returns the ascent of the current font at the current size.
     * This is a method, rather than a variable inside the PGraphics object
     * because it requires calculation.
     */
    open fun textAscent(): Float {
        if (textFont == null) {
            defaultFontOrDeath("textAscent")
        }
        return textFont!!.ascent() * textSize
    }

    /**
     * Returns the descent of the current font at the current size.
     * This is a method, rather than a variable inside the PGraphics object
     * because it requires calculation.
     */
    open fun textDescent(): Float {
        if (textFont == null) {
            defaultFontOrDeath("textDescent")
        }
        return textFont!!.descent() * textSize
    }

    /**
     * Sets the current font. The font's size will be the "natural"
     * size of this font (the size that was set when using "Create Font").
     * The leading will also be reset.
     */
    open fun textFont(which: PFont?) {
        if (which == null) {
            throw RuntimeException(PConstants.ERROR_TEXTFONT_NULL_PFONT)
        }
        textFontImpl(which, which.size.toFloat())
    }

    /**
     * Useful function to set the font and size at the same time.
     */
    open fun textFont(which: PFont?, size: Float) {
        var size = size
        if (which == null) {
            throw RuntimeException(PConstants.ERROR_TEXTFONT_NULL_PFONT)
        }
        // https://github.com/processing/processing/issues/3110
        if (size <= 0) {
            // Using System.err instead of showWarning to avoid running out of
            // memory with a bunch of textSize() variants (cause of this bug is
            // usually something done with map() or in a loop).
            System.err.println("textFont: ignoring size " + size + " px:" +
                    "the text size must be larger than zero")
            size = textSize
        }
        textFontImpl(which, size)
    }

    /**
     * Called from textFont. Check the validity of args and
     * print possible errors to the user before calling this.
     * Subclasses will want to override this one.
     *
     * @param which font to set, not null
     * @param size size to set, greater than zero
     */
    protected fun textFontImpl(which: PFont?, size: Float) {
        textFont = which
        //      if (hints[ENABLE_NATIVE_FONTS]) {
//        //if (which.font == null) {
//        which.findNative();
//        //}
//      }
        /*
      textFontNative = which.font;

      //textFontNativeMetrics = null;
      // changed for rev 0104 for textMode(SHAPE) in opengl
      if (textFontNative != null) {
        // TODO need a better way to handle this. could use reflection to get
        // rid of the warning, but that'd be a little silly. supporting this is
        // an artifact of supporting java 1.1, otherwise we'd use getLineMetrics,
        // as recommended by the @deprecated flag.
        textFontNativeMetrics =
          Toolkit.getDefaultToolkit().getFontMetrics(textFontNative);
        // The following is what needs to be done, however we need to be able
        // to get the actual graphics context where the drawing is happening.
        // For instance, parent.getGraphics() doesn't work for OpenGL since
        // an OpenGL drawing surface is an embedded component.
//        if (parent != null) {
//          textFontNativeMetrics = parent.getGraphics().getFontMetrics(textFontNative);
//        }

        // float w = font.getStringBounds(text, g2.getFontRenderContext()).getWidth();
      }
      */handleTextSize(size)
    }

    /**
     * Set the text leading to a specific value. If using a custom
     * value for the text leading, you'll have to call textLeading()
     * again after any calls to textSize().
     */
    fun textLeading(leading: Float) {
        textLeading = leading
    }

    /**
     * Sets the text rendering/placement to be either SCREEN (direct
     * to the screen, exact coordinates, only use the font's original size)
     * or MODEL (the default, where text is manipulated by translate() and
     * can have a textSize). The text size cannot be set when using
     * textMode(SCREEN), because it uses the pixels directly from the font.
     */
    fun textMode(mode: Int) {
        // CENTER and MODEL overlap (they're both 3)
        if (mode == PConstants.LEFT || mode == PConstants.RIGHT) {
            showWarning("Since Processing beta, textMode() is now textAlign().")
            return
        }
        if (mode == PConstants.SCREEN) {
            showWarning("textMode(SCREEN) has been removed from Processing 2.0.")
        }
        if (textModeCheck(mode)) {
            textMode = mode
        } else {
            var modeStr = mode.toString()
            when (mode) {
                PConstants.MODEL -> modeStr = "MODEL"
                PConstants.SHAPE -> modeStr = "SHAPE"
            }
            showWarning("textMode($modeStr) is not supported by this renderer.")
        }
    }

    protected open fun textModeCheck(mode: Int): Boolean {
        return true
    }

    /**
     * Sets the text size, also resets the value for the leading.
     */
    open fun textSize(size: Float) {
        // https://github.com/processing/processing/issues/3110
        if (size <= 0) {
            // Using System.err instead of showWarning to avoid running out of
            // memory with a bunch of textSize() variants (cause of this bug is
            // usually something done with map() or in a loop).
            System.err.println("textSize(" + size + ") ignored: " +
                    "the text size must be larger than zero")
            return
        }
        if (textFont == null) {
            defaultFontOrDeath("textSize", size)
        }
        textSizeImpl(size)
    }

    /**
     * Called from textSize() after validating size. Subclasses
     * will want to override this one.
     * @param size size of the text, greater than zero
     */
    protected fun textSizeImpl(size: Float) {
        handleTextSize(size)
    }

    /**
     * Sets the actual size. Called from textSizeImpl and
     * from textFontImpl after setting the font.
     * @param size size of the text, greater than zero
     */
    protected open fun handleTextSize(size: Float) {
        textSize = size
        textLeading = (textAscent() + textDescent()) * 1.275f
    }

    // ........................................................
    fun textWidth(c: Char): Float {
        textWidthBuffer[0] = c
        return textWidthImpl(textWidthBuffer, 0, 1)
    }

    /**
     * Return the width of a line of text. If the text has multiple
     * lines, this returns the length of the longest line.
     */
    fun textWidth(str: String): Float {
        if (textFont == null) {
            defaultFontOrDeath("textWidth")
        }
        val length = str.length
        if (length > textWidthBuffer.size) {
            textWidthBuffer = CharArray(length + 10)
        }
        str.toCharArray(textWidthBuffer, 0, 0, length)
        var wide = 0f
        var index = 0
        var start = 0
        while (index < length) {
            if (textWidthBuffer[index] == '\n') {
                wide = Math.max(wide, textWidthImpl(textWidthBuffer, start, index))
                start = index + 1
            }
            index++
        }
        if (start < length) {
            wide = Math.max(wide, textWidthImpl(textWidthBuffer, start, index))
        }
        return wide
    }

    fun textWidth(chars: CharArray, start: Int, length: Int): Float {
        return textWidthImpl(chars, start, start + length)
    }

    /**
     * Implementation of returning the text width of
     * the chars [start, stop) in the buffer.
     * Unlike the previous version that was inside PFont, this will
     * return the size not of a 1 pixel font, but the actual current size.
     */
    protected open fun textWidthImpl(buffer: CharArray, start: Int, stop: Int): Float {
        var wide = 0f
        for (i in start until stop) {
            // could add kerning here, but it just ain't implemented
            wide += textFont!!.width(buffer[i]) * textSize
        }
        return wide
    }
    // ........................................................
    //  /**
    //   * Write text where we just left off.
    //   */
    //  public void text(char c) {
    //    text(c, textX, textY, textZ);
    //  }
    /**
     * Draw a single character on screen.
     * Extremely slow when used with textMode(SCREEN) and Java 2D,
     * because loadPixels has to be called first and updatePixels last.
     */
    open fun text(c: Char, x: Float, y: Float) {
        var y = y
        if (textFont == null) {
            defaultFontOrDeath("text")
        }
        if (textAlignY == PConstants.CENTER) {
            y += textAscent() / 2
        } else if (textAlignY == PConstants.TOP) {
            y += textAscent()
        } else if (textAlignY == PConstants.BOTTOM) {
            y -= textDescent()
            //} else if (textAlignY == BASELINE) {
            // do nothing
        }
        textBuffer[0] = c
        textLineAlignImpl(textBuffer, 0, 1, x, y)
    }

    /**
     * Draw a single character on screen (with a z coordinate)
     */
    fun text(c: Char, x: Float, y: Float, z: Float) {
//    if ((z != 0) && (textMode == SCREEN)) {
//      String msg = "textMode(SCREEN) cannot have a z coordinate";
//      throw new RuntimeException(msg);
//    }
        if (z != 0f) translate(0f, 0f, z) // slowness, badness
        text(c, x, y)
        //    textZ = z;
        if (z != 0f) translate(0f, 0f, -z)
    }

    /**
     * Draw a chunk of text.
     * Newlines that are \n (Unix newline or linefeed char, ascii 10)
     * are honored, but \r (carriage return, Windows and Mac OS) are
     * ignored.
     */
    open fun text(str: String, x: Float, y: Float) {
        var y = y
        if (textFont == null) {
            defaultFontOrDeath("text")
        }
        val length = str.length
        if (length > textBuffer.size) {
            textBuffer = CharArray(length + 10)
        }
        str.toCharArray(textBuffer, 0, 0, length)

        // If multiple lines, sum the height of the additional lines
        var high = 0f //-textAscent();
        for (i in 0 until length) {
            if (textBuffer[i] == '\n') {
                high += textLeading
            }
        }
        if (textAlignY == PConstants.CENTER) {
            // for a single line, this adds half the textAscent to y
            // for multiple lines, subtract half the additional height
            //y += (textAscent() - textDescent() - high)/2;
            y += (textAscent() - high) / 2
        } else if (textAlignY == PConstants.TOP) {
            // for a single line, need to add textAscent to y
            // for multiple lines, no different
            y += textAscent()
        } else if (textAlignY == PConstants.BOTTOM) {
            // for a single line, this is just offset by the descent
            // for multiple lines, subtract leading for each line
            y -= textDescent() + high
            //} else if (textAlignY == BASELINE) {
            // do nothing
        }
        var start = 0
        var index = 0
        while (index < length) {
            if (textBuffer[index] == '\n') {
                textLineAlignImpl(textBuffer, start, index, x, y)
                start = index + 1
                y += textLeading
            }
            index++
        }
        if (start < length) {
            textLineAlignImpl(textBuffer, start, index, x, y)
        }
    }

    /**
     * Method to draw text from an array of chars. This method will usually be
     * more efficient than drawing from a String object, because the String will
     * not be converted to a char array before drawing.
     */
    fun text(chars: CharArray, start: Int, stop: Int, x: Float, y: Float) {
        // If multiple lines, sum the height of the additional lines
        var start = start
        var y = y
        var high = 0f //-textAscent();
        for (i in start until stop) {
            if (chars[i] == '\n') {
                high += textLeading
            }
        }
        if (textAlignY == PConstants.CENTER) {
            // for a single line, this adds half the textAscent to y
            // for multiple lines, subtract half the additional height
            //y += (textAscent() - textDescent() - high)/2;
            y += (textAscent() - high) / 2
        } else if (textAlignY == PConstants.TOP) {
            // for a single line, need to add textAscent to y
            // for multiple lines, no different
            y += textAscent()
        } else if (textAlignY == PConstants.BOTTOM) {
            // for a single line, this is just offset by the descent
            // for multiple lines, subtract leading for each line
            y -= textDescent() + high
            //} else if (textAlignY == BASELINE) {
            // do nothing
        }

//    int start = 0;
        var index = 0
        while (index < stop) { //length) {
            if (chars[index] == '\n') {
                textLineAlignImpl(chars, start, index, x, y)
                start = index + 1
                y += textLeading
            }
            index++
        }
        if (start < stop) {  //length) {
            textLineAlignImpl(chars, start, index, x, y)
        }
    }

    /**
     * Same as above but with a z coordinate.
     */
    fun text(str: String, x: Float, y: Float, z: Float) {
//    if ((z != 0) && (textMode == SCREEN)) {
//      String msg = "textMode(SCREEN) cannot have a z coordinate";
//      throw new RuntimeException(msg);
//    }
        if (z != 0f) translate(0f, 0f, z) // slow!
        text(str, x, y)
        //    textZ = z;
        if (z != 0f) translate(0f, 0f, -z)
    }

    fun text(chars: CharArray, start: Int, stop: Int,
             x: Float, y: Float, z: Float) {
        if (z != 0f) translate(0f, 0f, z) // slow!
        text(chars, start, stop, x, y)
        //    textZ = z;
        if (z != 0f) translate(0f, 0f, -z) // inaccurate!
    }

    /**
     * Draw text in a box that is constrained to a particular size.
     * The current rectMode() determines what the coordinates mean
     * (whether x1/y1/x2/y2 or x/y/w/h).
     * <P></P>
     * Note that the x,y coords of the start of the box
     * will align with the *ascent* of the text, not the baseline,
     * as is the case for the other text() functions.
     * <P></P>
     * Newlines that are \n (Unix newline or linefeed char, ascii 10)
     * are honored, and \r (carriage return, Windows and Mac OS) are
     * ignored.
     */
    open fun text(str: String, x1: Float, y1: Float, x2: Float, y2: Float) {
        var x1 = x1
        var y1 = y1
        var x2 = x2
        var y2 = y2
        if (textFont == null) {
            defaultFontOrDeath("text")
        }
        val hradius: Float
        val vradius: Float
        when (rectMode) {
            PConstants.CORNER -> {
                x2 += x1
                y2 += y1
            }
            PConstants.RADIUS -> {
                hradius = x2
                vradius = y2
                x2 = x1 + hradius
                y2 = y1 + vradius
                x1 -= hradius
                y1 -= vradius
            }
            PConstants.CENTER -> {
                hradius = x2 / 2.0f
                vradius = y2 / 2.0f
                x2 = x1 + hradius
                y2 = y1 + vradius
                x1 -= hradius
                y1 -= vradius
            }
        }
        if (x2 < x1) {
            val temp = x1
            x1 = x2
            x2 = temp
        }
        if (y2 < y1) {
            val temp = y1
            y1 = y2
            y2 = temp
        }

//    float currentY = y1;
        val boxWidth = x2 - x1

//    // ala illustrator, the text itself must fit inside the box
//    currentY += textAscent(); //ascent() * textSize;
//    // if the box is already too small, tell em to f off
//    if (currentY > y2) return;
        val spaceWidth = textWidth(' ')
        if (textBreakStart == null) {
            textBreakStart = IntArray(20)
            textBreakStop = IntArray(20)
        }
        textBreakCount = 0
        var length = str.length
        if (length + 1 > textBuffer.size) {
            textBuffer = CharArray(length + 1)
        }
        str.toCharArray(textBuffer, 0, 0, length)
        // add a fake newline to simplify calculations
        textBuffer[length++] = '\n'
        var sentenceStart = 0
        for (i in 0 until length) {
            if (textBuffer[i] == '\n') {
//        currentY = textSentence(textBuffer, sentenceStart, i,
//                                lineX, boxWidth, currentY, y2, spaceWidth);
                val legit = textSentence(textBuffer, sentenceStart, i, boxWidth, spaceWidth)
                if (!legit) break
                //      if (Float.isNaN(currentY)) break;  // word too big (or error)
//      if (currentY > y2) break;  // past the box
                sentenceStart = i + 1
            }
        }

        // lineX is the position where the text starts, which is adjusted
        // to left/center/right based on the current textAlign
        var lineX = x1 //boxX1;
        if (textAlign == PConstants.CENTER) {
            lineX = lineX + boxWidth / 2f
        } else if (textAlign == PConstants.RIGHT) {
            lineX = x2 //boxX2;
        }
        val boxHeight = y2 - y1
        //int lineFitCount = 1 + PApplet.floor((boxHeight - textAscent()) / textLeading);
        // incorporate textAscent() for the top (baseline will be y1 + ascent)
        // and textDescent() for the bottom, so that lower parts of letters aren't
        // outside the box. [0151]
        val topAndBottom = textAscent() + textDescent()
        val lineFitCount = 1 + PApplet.floor((boxHeight - topAndBottom) / textLeading)
        val lineCount = Math.min(textBreakCount, lineFitCount)
        if (textAlignY == PConstants.CENTER) {
            val lineHigh = textAscent() + textLeading * (lineCount - 1)
            var y = y1 + textAscent() + (boxHeight - lineHigh) / 2
            for (i in 0 until lineCount) {
                textLineAlignImpl(textBuffer, textBreakStart!![i], textBreakStop[i], lineX, y)
                y += textLeading
            }
        } else if (textAlignY == PConstants.BOTTOM) {
            var y = y2 - textDescent() - textLeading * (lineCount - 1)
            for (i in 0 until lineCount) {
                textLineAlignImpl(textBuffer, textBreakStart!![i], textBreakStop[i], lineX, y)
                y += textLeading
            }
        } else {  // TOP or BASELINE just go to the default
            var y = y1 + textAscent()
            for (i in 0 until lineCount) {
                textLineAlignImpl(textBuffer, textBreakStart!![i], textBreakStop[i], lineX, y)
                y += textLeading
            }
        }
    }

    /**
     * Emit a sentence of text, defined as a chunk of text without any newlines.
     * @param stop non-inclusive, the end of the text in question
     */
    protected fun textSentence(buffer: CharArray, start: Int, stop: Int,
                               boxWidth: Float, spaceWidth: Float): Boolean {
        var runningX = 0f

        // Keep track of this separately from index, since we'll need to back up
        // from index when breaking words that are too long to fit.
        var lineStart = start
        var wordStart = start
        var index = start
        while (index <= stop) {
            // boundary of a word or end of this sentence
            if (buffer[index] == ' ' || index == stop) {
                var wordWidth = textWidthImpl(buffer, wordStart, index)
                if (runningX + wordWidth > boxWidth) {
                    if (runningX != 0f) {
                        // Next word is too big, output the current line and advance
                        index = wordStart
                        textSentenceBreak(lineStart, index)
                        // Eat whitespace because multiple spaces don't count for s*
                        // when they're at the end of a line.
                        while (index < stop && buffer[index] == ' ') {
                            index++
                        }
                    } else {  // (runningX == 0)
                        // If this is the first word on the line, and its width is greater
                        // than the width of the text box, then break the word where at the
                        // max width, and send the rest of the word to the next line.
                        do {
                            index--
                            if (index == wordStart) {
                                // Not a single char will fit on this line. screw 'em.
                                //System.out.println("screw you");
                                return false //Float.NaN;
                            }
                            wordWidth = textWidthImpl(buffer, wordStart, index)
                        } while (wordWidth > boxWidth)

                        //textLineImpl(buffer, lineStart, index, x, y);
                        textSentenceBreak(lineStart, index)
                    }
                    lineStart = index
                    wordStart = index
                    runningX = 0f
                } else if (index == stop) {
                    // last line in the block, time to unload
                    //textLineImpl(buffer, lineStart, index, x, y);
                    textSentenceBreak(lineStart, index)
                    //          y += textLeading;
                    index++
                } else {  // this word will fit, just add it to the line
                    runningX += wordWidth + spaceWidth
                    wordStart = index + 1 // move on to the next word
                    index++
                }
            } else {  // not a space or the last character
                index++ // this is just another letter
            }
        }
        //    return y;
        return true
    }

    protected fun textSentenceBreak(start: Int, stop: Int) {
        if (textBreakCount == textBreakStart!!.size) {
            textBreakStart = PApplet.expand(textBreakStart)
            textBreakStop = PApplet.expand(textBreakStop)
        }
        textBreakStart!![textBreakCount] = start
        textBreakStop[textBreakCount] = stop
        textBreakCount++
    }

    //  public void text(String s, float x1, float y1, float x2, float y2, float z) {
    //    if (z != 0) translate(0, 0, z);  // slowness, badness
    //
    //    text(s, x1, y1, x2, y2);
    //    textZ = z;
    //
    //    if (z != 0) translate(0, 0, -z);  // TEMPORARY HACK! SLOW!
    //  }
    fun text(num: Int, x: Float, y: Float) {
        text(num.toString(), x, y)
    }

    fun text(num: Int, x: Float, y: Float, z: Float) {
        text(num.toString(), x, y, z)
    }

    /**
     * This does a basic number formatting, to avoid the
     * generally ugly appearance of printing floats.
     * Users who want more control should use their own nf() cmmand,
     * or if they want the long, ugly version of float,
     * use String.valueOf() to convert the float to a String first.
     */
    fun text(num: Float, x: Float, y: Float) {
        text(PApplet.nfs(num, 0, 3), x, y)
    }

    fun text(num: Float, x: Float, y: Float, z: Float) {
        text(PApplet.nfs(num, 0, 3), x, y, z)
    }
    //////////////////////////////////////////////////////////////
    // TEXT IMPL
    // These are most likely to be overridden by subclasses, since the other
    // (public) functions handle generic features like setting alignment.
    /**
     * Handles placement of a text line, then calls textLineImpl
     * to actually render at the specific point.
     */
    protected fun textLineAlignImpl(buffer: CharArray, start: Int, stop: Int,
                                    x: Float, y: Float) {
        var x = x
        if (textAlign == PConstants.CENTER) {
            x -= textWidthImpl(buffer, start, stop) / 2f
        } else if (textAlign == PConstants.RIGHT) {
            x -= textWidthImpl(buffer, start, stop)
        }
        textLineImpl(buffer, start, stop, x, y)
    }

    /**
     * Implementation of actual drawing for a line of text.
     */
    protected open fun textLineImpl(buffer: CharArray, start: Int, stop: Int,
                                    x: Float, y: Float) {
        var x = x
        for (index in start until stop) {
            textCharImpl(buffer[index], x, y)

            // this doesn't account for kerning
            x += textWidth(buffer[index])
        }
        //    textX = x;
//    textY = y;
//    textZ = 0;  // this will get set by the caller if non-zero
    }

    protected open fun textCharImpl(ch: Char, x: Float, y: Float) {
        val glyph = textFont!!.getGlyph(ch)
        if (glyph != null) {
            if (textMode == PConstants.MODEL) {
                val high = glyph.height / textFont!!.size.toFloat()
                val bwidth = glyph.width / textFont!!.size.toFloat()
                val lextent = glyph.leftExtent / textFont!!.size.toFloat()
                val textent = glyph.topExtent / textFont!!.size.toFloat()
                val x1 = x + lextent * textSize
                val y1 = y - textent * textSize
                val x2 = x1 + bwidth * textSize
                val y2 = y1 + high * textSize
                textCharModelImpl(glyph.image,
                        x1, y1, x2, y2,
                        glyph.width, glyph.height)
            }
        }
    }

    protected fun textCharModelImpl(glyph: PImage?,
                                    x1: Float, y1: Float,  //float z1,
                                    x2: Float, y2: Float,  //float z2,
                                    u2: Int, v2: Int) {
        val savedTint = tint
        val savedTintColor = tintColor
        tint(fillColor)
        imageImpl(glyph, x1, y1, x2, y2, 0, 0, u2, v2)
        if (savedTint) {
            tint(savedTintColor)
        } else {
            noTint()
        }
    }

    //////////////////////////////////////////////////////////////
    // PARITY WITH P5.JS
    fun push() {
        pushStyle()
        pushMatrix()
    }

    fun pop() {
        popStyle()
        popMatrix()
    }
    //////////////////////////////////////////////////////////////
    // MATRIX STACK
    /**
     * Push a copy of the current transformation matrix onto the stack.
     */
    open fun pushMatrix() {
        showMethodWarning("pushMatrix")
    }

    /**
     * Replace the current transformation matrix with the top of the stack.
     */
    open fun popMatrix() {
        showMethodWarning("popMatrix")
    }
    //////////////////////////////////////////////////////////////
    // MATRIX TRANSFORMATIONS
    /**
     * Translate in X and Y.
     */
    open fun translate(tx: Float, ty: Float) {
        showMissingWarning("translate")
    }

    /**
     * Translate in X, Y, and Z.
     */
    open fun translate(tx: Float, ty: Float, tz: Float) {
        showMissingWarning("translate")
    }

    /**
     * Two dimensional rotation.
     *
     * Same as rotateZ (this is identical to a 3D rotation along the z-axis)
     * but included for clarity. It'd be weird for people drawing 2D graphics
     * to be using rotateZ. And they might kick our a-- for the confusion.
     *
     * <A HREF="http://www.xkcd.com/c184.html">Additional background</A>.
     */
    open fun rotate(angle: Float) {
        showMissingWarning("rotate")
    }

    /**
     * Rotate around the X axis.
     */
    open fun rotateX(angle: Float) {
        showMethodWarning("rotateX")
    }

    /**
     * Rotate around the Y axis.
     */
    open fun rotateY(angle: Float) {
        showMethodWarning("rotateY")
    }

    /**
     * Rotate around the Z axis.
     *
     * The functions rotate() and rotateZ() are identical, it's just that it make
     * sense to have rotate() and then rotateX() and rotateY() when using 3D;
     * nor does it make sense to use a function called rotateZ() if you're only
     * doing things in 2D. so we just decided to have them both be the same.
     */
    open fun rotateZ(angle: Float) {
        showMethodWarning("rotateZ")
    }

    /**
     * Rotate about a vector in space. Same as the glRotatef() function.
     */
    open fun rotate(angle: Float, vx: Float, vy: Float, vz: Float) {
        showMissingWarning("rotate")
    }

    /**
     * Scale in all dimensions.
     */
    open fun scale(s: Float) {
        showMissingWarning("scale")
    }

    /**
     * Scale in X and Y. Equivalent to scale(sx, sy, 1).
     *
     * Not recommended for use in 3D, because the z-dimension is just
     * scaled by 1, since there's no way to know what else to scale it by.
     */
    open fun scale(sx: Float, sy: Float) {
        showMissingWarning("scale")
    }

    /**
     * Scale in X, Y, and Z.
     */
    open fun scale(x: Float, y: Float, z: Float) {
        showMissingWarning("scale")
    }

    /**
     * Shear along X axis
     */
    open fun shearX(angle: Float) {
        showMissingWarning("shearX")
    }

    /**
     * Skew along Y axis
     */
    open fun shearY(angle: Float) {
        showMissingWarning("shearY")
    }
    //////////////////////////////////////////////////////////////
    // MATRIX FULL MONTY
    /**
     * Set the current transformation matrix to identity.
     */
    open fun resetMatrix() {
        showMethodWarning("resetMatrix")
    }

    fun applyMatrix(source: PMatrix?) {
        if (source is PMatrix2D) {
            applyMatrix(source)
        } else if (source is PMatrix3D) {
            applyMatrix(source)
        }
    }

    open fun applyMatrix(source: PMatrix2D) {
        applyMatrix(source.m00, source.m01, source.m02,
                source.m10, source.m11, source.m12)
    }

    /**
     * Apply a 3x2 affine transformation matrix.
     */
    open fun applyMatrix(n00: Float, n01: Float, n02: Float,
                         n10: Float, n11: Float, n12: Float) {
        showMissingWarning("applyMatrix")
    }

    open fun applyMatrix(source: PMatrix3D) {
        applyMatrix(source.m00, source.m01, source.m02, source.m03,
                source.m10, source.m11, source.m12, source.m13,
                source.m20, source.m21, source.m22, source.m23,
                source.m30, source.m31, source.m32, source.m33)
    }

    /**
     * Apply a 4x4 transformation matrix.
     */
    open fun applyMatrix(n00: Float, n01: Float, n02: Float, n03: Float,
                         n10: Float, n11: Float, n12: Float, n13: Float,
                         n20: Float, n21: Float, n22: Float, n23: Float,
                         n30: Float, n31: Float, n32: Float, n33: Float) {
        showMissingWarning("applyMatrix")
    }

    /**
     * Set the current transformation matrix to the contents of another.
     */
    //////////////////////////////////////////////////////////////
    // MATRIX GET/SET/PRINT
    private  var matrix: PMatrix? = null
//        get() {
//            showMissingWarning("getMatrix")
//            return null
//        }
//        set(source) {
//            if (source is PMatrix2D) {
//                setMatrix(source as PMatrix2D?)
//            } else if (source is PMatrix3D) {
//                setMatrix(source as PMatrix3D?)
//            }
//        }


    open fun getMatrix(): PMatrix? {
        showMissingWarning("getMatrix")
        return null
    }

     fun setMatrix(source: PMatrix?) {
        if (source is PMatrix2D) {
            setMatrix(source as PMatrix2D?)
        }
        else if(source is PMatrix3D){
            setMatrix(source as PMatrix3D?)
        }
    }
    /**
     * Copy the current transformation matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    open fun getMatrix(target: PMatrix2D?): PMatrix2D? {
        showMissingWarning("getMatrix")
        return null
    }

    /**
     * Copy the current transformation matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    open fun getMatrix(target: PMatrix3D?): PMatrix3D? {
        showMissingWarning("getMatrix")
        return null
    }

    /**
     * Returns a copy of the current object matrix.
     * Pass in null to create a new matrix.
     */
    open val objectMatrix: PMatrix3D?
        get() {
            showMissingWarning("getObjectMatrix")
            return null
        }

    /**
     * Copy the current object matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    open fun getObjectMatrix(target: PMatrix3D?): PMatrix3D? {
        showMissingWarning("getObjectMatrix")
        return null
    }

    /**
     * Returns a copy of the current eye matrix.
     * Pass in null to create a new matrix.
     */
    open val eyeMatrix: PMatrix3D?
        get() {
            showMissingWarning("getEyeMatrix")
            return null
        }

    /**
     * Copy the current eye matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    open fun getEyeMatrix(target: PMatrix3D?): PMatrix3D? {
        showMissingWarning("getEyeMatrix")
        return null
    }

    /**
     * Set the current transformation to the contents of the specified source.
     */
    open fun setMatrix(source: PMatrix2D?) {
        showMissingWarning("setMatrix")
    }

    /**
     * Set the current transformation to the contents of the specified source.
     */
    open fun setMatrix(source: PMatrix3D?) {
        showMissingWarning("setMatrix")
    }

    /**
     * Print the current model (or "transformation") matrix.
     */
    open fun printMatrix() {
        showMethodWarning("printMatrix")
    }

    //////////////////////////////////////////////////////////////
    // CAMERA
    open fun cameraUp() {
        showMethodWarning("cameraUp")
    }

    open fun beginCamera() {
        showMethodWarning("beginCamera")
    }

    open fun endCamera() {
        showMethodWarning("endCamera")
    }

    open fun camera() {
        showMissingWarning("camera")
    }

    open fun camera(eyeX: Float, eyeY: Float, eyeZ: Float,
                    centerX: Float, centerY: Float, centerZ: Float,
                    upX: Float, upY: Float, upZ: Float) {
        showMissingWarning("camera")
    }

    open fun printCamera() {
        showMethodWarning("printCamera")
    }

    open fun eye() {
        showMethodWarning("eye")
    }

    //////////////////////////////////////////////////////////////
    // PROJECTION
    open fun ortho() {
        showMissingWarning("ortho")
    }

    open fun ortho(left: Float, right: Float,
                   bottom: Float, top: Float) {
        showMissingWarning("ortho")
    }

    open fun ortho(left: Float, right: Float,
                   bottom: Float, top: Float,
                   near: Float, far: Float) {
        showMissingWarning("ortho")
    }

    open fun perspective() {
        showMissingWarning("perspective")
    }

    open fun perspective(fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
        showMissingWarning("perspective")
    }

    open fun frustum(left: Float, right: Float,
                     bottom: Float, top: Float,
                     near: Float, far: Float) {
        showMethodWarning("frustum")
    }

    open fun printProjection() {
        showMethodWarning("printCamera")
    }
    //////////////////////////////////////////////////////////////
    // SCREEN TRANSFORMS
    /**
     * Given an x and y coordinate, returns the x position of where
     * that point would be placed on screen, once affected by translate(),
     * scale(), or any other transformations.
     */
    open fun screenX(x: Float, y: Float): Float {
        showMissingWarning("screenX")
        return 0F
    }

    /**
     * Given an x and y coordinate, returns the y position of where
     * that point would be placed on screen, once affected by translate(),
     * scale(), or any other transformations.
     */
    open fun screenY(x: Float, y: Float): Float {
        showMissingWarning("screenY")
        return 0F
    }

    /**
     * Maps a three dimensional point to its placement on-screen.
     * <P>
     * Given an (x, y, z) coordinate, returns the x position of where
     * that point would be placed on screen, once affected by translate(),
     * scale(), or any other transformations.
    </P> */
    open fun screenX(x: Float, y: Float, z: Float): Float {
        showMissingWarning("screenX")
        return 0F
    }

    /**
     * Maps a three dimensional point to its placement on-screen.
     * <P>
     * Given an (x, y, z) coordinate, returns the y position of where
     * that point would be placed on screen, once affected by translate(),
     * scale(), or any other transformations.
    </P> */
    open fun screenY(x: Float, y: Float, z: Float): Float {
        showMissingWarning("screenY")
        return 0F
    }

    /**
     * Maps a three dimensional point to its placement on-screen.
     * <P>
     * Given an (x, y, z) coordinate, returns its z value.
     * This value can be used to determine if an (x, y, z) coordinate
     * is in front or in back of another (x, y, z) coordinate.
     * The units are based on how the zbuffer is set up, and don't
     * relate to anything "real". They're only useful for in
     * comparison to another value obtained from screenZ(),
     * or directly out of the zbuffer[].
    </P> */
    open fun screenZ(x: Float, y: Float, z: Float): Float {
        showMissingWarning("screenZ")
        return 0F
    }

    /**
     * Returns the model space x value for an x, y, z coordinate.
     * <P>
     * This will give you a coordinate after it has been transformed
     * by translate(), rotate(), and camera(), but not yet transformed
     * by the projection matrix. For instance, his can be useful for
     * figuring out how points in 3D space relate to the edge
     * coordinates of a shape.
    </P> */
    open fun modelX(x: Float, y: Float, z: Float): Float {
        showMissingWarning("modelX")
        return 0F
    }

    /**
     * Returns the model space y value for an x, y, z coordinate.
     */
    open fun modelY(x: Float, y: Float, z: Float): Float {
        showMissingWarning("modelY")
        return 0F
    }

    /**
     * Returns the model space z value for an x, y, z coordinate.
     */
    open fun modelZ(x: Float, y: Float, z: Float): Float {
        showMissingWarning("modelZ")
        return 0F
    }

    //////////////////////////////////////////////////////////////
    // RAY CASTING
    open fun getRayFromScreen(screenX: Float, screenY: Float, ray: Array<PVector?>?): Array<PVector?>? {
        showMissingWarning("getRayFromScreen")
        return null
    }

    open fun getRayFromScreen(screenX: Float, screenY: Float, origin: PVector?, direction: PVector?) {
        showMissingWarning("getRayFromScreen")
    }

    open fun intersectsSphere(r: Float, screenX: Float, screenY: Float): Boolean {
        showMissingWarning("intersectsSphere")
        return false
    }

    open fun intersectsSphere(r: Float, origin: PVector?, direction: PVector?): Boolean {
        showMissingWarning("intersectsSphere")
        return false
    }

    open fun intersectsBox(size: Float, screenX: Float, screenY: Float): Boolean {
        showMissingWarning("intersectsBox")
        return false
    }

    open fun intersectsBox(w: Float, h: Float, d: Float, screenX: Float, screenY: Float): Boolean {
        showMissingWarning("intersectsBox")
        return false
    }

    open fun intersectsBox(size: Float, origin: PVector?, direction: PVector?): Boolean {
        showMissingWarning("intersectsBox")
        return false
    }

    open fun intersectsBox(w: Float, h: Float, d: Float, origin: PVector?, direction: PVector?): Boolean {
        showMissingWarning("intersectsBox")
        return false
    }

    open fun intersectsPlane(screenX: Float, screenY: Float): PVector? {
        showMissingWarning("intersectsPlane")
        return null
    }

    open fun intersectsPlane(origin: PVector?, direction: PVector?): PVector? {
        showMissingWarning("intersectsPlane")
        return null
    }

    //////////////////////////////////////////////////////////////
    // STYLE
    fun pushStyle() {
        if (styleStackDepth == styleStack.size) {
            styleStack = PApplet.expand(styleStack) as Array<PStyle?>
        }
        if (styleStack[styleStackDepth] == null) {
            styleStack[styleStackDepth] = PStyle()
        }
        val s = styleStack[styleStackDepth++]
        getStyle(s)
    }

    open fun popStyle() {
        if (styleStackDepth == 0) {
            throw RuntimeException("Too many popStyle() without enough pushStyle()")
        }
        styleStackDepth--
        style(styleStack[styleStackDepth])
    }

    fun style(s: PStyle?) {
        //  if (s.smooth) {
        //    smooth();
        //  } else {
        //    noSmooth();
        //  }
        imageMode(s!!.imageMode)
        rectMode(s.rectMode)
        ellipseMode(s.ellipseMode)
        shapeMode(s.shapeMode)
        blendMode(s.blendMode)
        if (s.tint) {
            tint(s.tintColor)
        } else {
            noTint()
        }
        if (s.fill) {
            fill(s.fillColor)
        } else {
            noFill()
        }
        if (s.stroke) {
            stroke(s.strokeColor)
        } else {
            noStroke()
        }
        strokeWeight(s.strokeWeight)
        strokeCap(s.strokeCap)
        strokeJoin(s.strokeJoin)

        // Set the colorMode() for the material properties.
        // TODO this is really inefficient, need to just have a material() method,
        // but this has the least impact to the API.
        colorMode(PConstants.RGB, 1f)
        ambient(s.ambientR, s.ambientG, s.ambientB)
        emissive(s.emissiveR, s.emissiveG, s.emissiveB)
        specular(s.specularR, s.specularG, s.specularB)
        shininess(s.shininess)

        /*
  s.ambientR = ambientR;
  s.ambientG = ambientG;
  s.ambientB = ambientB;
  s.specularR = specularR;
  s.specularG = specularG;
  s.specularB = specularB;
  s.emissiveR = emissiveR;
  s.emissiveG = emissiveG;
  s.emissiveB = emissiveB;
  s.shininess = shininess;
     */
        //  material(s.ambientR, s.ambientG, s.ambientB,
        //           s.emissiveR, s.emissiveG, s.emissiveB,
        //           s.specularR, s.specularG, s.specularB,
        //           s.shininess);

        // Set this after the material properties.
        colorMode(s.colorMode,
                s.colorModeX, s.colorModeY, s.colorModeZ, s.colorModeA)

        // This is a bit asymmetric, since there's no way to do "noFont()",
        // and a null textFont will produce an error (since usually that means that
        // the font couldn't load properly). So in some cases, the font won't be
        // 'cleared' to null, even though that's technically correct.
        if (s.textFont != null) {
            textFont(s.textFont, s.textSize)
            textLeading(s.textLeading)
        }
        // These don't require a font to be set.
        textAlign(s.textAlign, s.textAlignY)
        textMode(s.textMode)
    }

    // ignore
    val style: PStyle
        get() =// ignore
            getStyle(null)

    fun getStyle(s: PStyle?): PStyle {  // ignore
        var s = s
        if (s == null) {
            s = PStyle()
        }
        s.imageMode = imageMode
        s.rectMode = rectMode
        s.ellipseMode = ellipseMode
        s.shapeMode = shapeMode
        s.blendMode = blendMode
        s.colorMode = colorMode
        s.colorModeX = colorModeX
        s.colorModeY = colorModeY
        s.colorModeZ = colorModeZ
        s.colorModeA = colorModeA
        s.tint = tint
        s.tintColor = tintColor
        s.fill = fill
        s.fillColor = fillColor
        s.stroke = stroke
        s.strokeColor = strokeColor
        s.strokeWeight = strokeWeight
        s.strokeCap = strokeCap
        s.strokeJoin = strokeJoin
        s.ambientR = ambientR
        s.ambientG = ambientG
        s.ambientB = ambientB
        s.specularR = specularR
        s.specularG = specularG
        s.specularB = specularB
        s.emissiveR = emissiveR
        s.emissiveG = emissiveG
        s.emissiveB = emissiveB
        s.shininess = shininess
        s.textFont = textFont
        s.textAlign = textAlign
        s.textAlignY = textAlignY
        s.textMode = textMode
        s.textSize = textSize
        s.textLeading = textLeading
        return s
    }

    //////////////////////////////////////////////////////////////
    // STROKE CAP/JOIN/WEIGHT
    open fun strokeWeight(weight: Float) {
        strokeWeight = weight
    }

    open fun strokeJoin(join: Int) {
        strokeJoin = join
    }

    open fun strokeCap(cap: Int) {
        strokeCap = cap
    }

    //////////////////////////////////////////////////////////////
    // STROKE COLOR
    fun noStroke() {
        stroke = false
    }

    /**
     * Set the tint to either a grayscale or ARGB value.
     * See notes attached to the fill() function.
     */
    fun stroke(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {  // see above
//      stroke((float) rgb);
//
//    } else {
//      colorCalcARGB(rgb, colorModeA);
//      strokeFromCalc();
//    }
        colorCalc(rgb)
        strokeFromCalc()
    }

    fun stroke(rgb: Int, alpha: Float) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      stroke((float) rgb, alpha);
//
//    } else {
//      colorCalcARGB(rgb, alpha);
//      strokeFromCalc();
//    }
        colorCalc(rgb, alpha)
        strokeFromCalc()
    }

    fun stroke(gray: Float) {
        colorCalc(gray)
        strokeFromCalc()
    }

    fun stroke(gray: Float, alpha: Float) {
        colorCalc(gray, alpha)
        strokeFromCalc()
    }

    fun stroke(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        strokeFromCalc()
    }

    fun stroke(x: Float, y: Float, z: Float, a: Float) {
        colorCalc(x, y, z, a)
        strokeFromCalc()
    }

    protected open fun strokeFromCalc() {
        stroke = true
        strokeR = calcR
        strokeG = calcG
        strokeB = calcB
        strokeA = calcA
        strokeRi = calcRi
        strokeGi = calcGi
        strokeBi = calcBi
        strokeAi = calcAi
        strokeColor = calcColor
        strokeAlpha = calcAlpha
    }

    //////////////////////////////////////////////////////////////
    // TINT COLOR
    fun noTint() {
        tint = false
    }

    /**
     * Set the tint to either a grayscale or ARGB value.
     */
    fun tint(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      tint((float) rgb);
//
//    } else {
//      colorCalcARGB(rgb, colorModeA);
//      tintFromCalc();
//    }
        colorCalc(rgb)
        tintFromCalc()
    }

    fun tint(rgb: Int, alpha: Float) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      tint((float) rgb, alpha);
//
//    } else {
//      colorCalcARGB(rgb, alpha);
//      tintFromCalc();
//    }
        colorCalc(rgb, alpha)
        tintFromCalc()
    }

    fun tint(gray: Float) {
        colorCalc(gray)
        tintFromCalc()
    }

    fun tint(gray: Float, alpha: Float) {
        colorCalc(gray, alpha)
        tintFromCalc()
    }

    fun tint(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        tintFromCalc()
    }

    fun tint(x: Float, y: Float, z: Float, a: Float) {
        colorCalc(x, y, z, a)
        tintFromCalc()
    }

    protected open fun tintFromCalc() {
        tint = true
        tintR = calcR
        tintG = calcG
        tintB = calcB
        tintA = calcA
        tintRi = calcRi
        tintGi = calcGi
        tintBi = calcBi
        tintAi = calcAi
        tintColor = calcColor
        tintAlpha = calcAlpha
    }

    //////////////////////////////////////////////////////////////
    // FILL COLOR
    fun noFill() {
        fill = false
    }

    /**
     * Set the fill to either a grayscale value or an ARGB int.
     */
    fun fill(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {  // see above
//      fill((float) rgb);
//
//    } else {
//      colorCalcARGB(rgb, colorModeA);
//      fillFromCalc();
//    }
        colorCalc(rgb)
        fillFromCalc()
    }

    fun fill(rgb: Int, alpha: Float) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {  // see above
//      fill((float) rgb, alpha);
//
//    } else {
//      colorCalcARGB(rgb, alpha);
//      fillFromCalc();
//    }
        colorCalc(rgb, alpha)
        fillFromCalc()
    }

    fun fill(gray: Float) {
        colorCalc(gray)
        fillFromCalc()
    }

    fun fill(gray: Float, alpha: Float) {
        colorCalc(gray, alpha)
        fillFromCalc()
    }

    fun fill(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        fillFromCalc()
    }

    fun fill(x: Float, y: Float, z: Float, a: Float) {
        colorCalc(x, y, z, a)
        fillFromCalc()
    }

    protected open fun fillFromCalc() {
        fill = true
        fillR = calcR
        fillG = calcG
        fillB = calcB
        fillA = calcA
        fillRi = calcRi
        fillGi = calcGi
        fillBi = calcBi
        fillAi = calcAi
        fillColor = calcColor
        fillAlpha = calcAlpha
    }

    //////////////////////////////////////////////////////////////
    // MATERIAL PROPERTIES
    fun ambient(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      ambient((float) rgb);
//
//    } else {
//      colorCalcARGB(rgb, colorModeA);
//      ambientFromCalc();
//    }
        colorCalc(rgb)
        ambientFromCalc()
    }

    fun ambient(gray: Float) {
        colorCalc(gray)
        ambientFromCalc()
    }

    fun ambient(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        ambientFromCalc()
    }

    protected fun ambientFromCalc() {
        ambientColor = calcColor
        ambientR = calcR
        ambientG = calcG
        ambientB = calcB
        setAmbient = true
    }

    fun specular(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      specular((float) rgb);
//
//    } else {
//      colorCalcARGB(rgb, colorModeA);
//      specularFromCalc();
//    }
        colorCalc(rgb)
        specularFromCalc()
    }

    fun specular(gray: Float) {
        colorCalc(gray)
        specularFromCalc()
    }

    fun specular(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        specularFromCalc()
    }

    protected fun specularFromCalc() {
        specularColor = calcColor
        specularR = calcR
        specularG = calcG
        specularB = calcB
    }

    fun shininess(shine: Float) {
        shininess = shine
    }

    fun emissive(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      emissive((float) rgb);
//
//    } else {
//      colorCalcARGB(rgb, colorModeA);
//      emissiveFromCalc();
//    }
        colorCalc(rgb)
        emissiveFromCalc()
    }

    fun emissive(gray: Float) {
        colorCalc(gray)
        emissiveFromCalc()
    }

    fun emissive(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        emissiveFromCalc()
    }

    protected fun emissiveFromCalc() {
        emissiveColor = calcColor
        emissiveR = calcR
        emissiveG = calcG
        emissiveB = calcB
    }

    //////////////////////////////////////////////////////////////
    // LIGHTS
    // The details of lighting are very implementation-specific, so this base
    // class does not handle any details of settings lights. It does however
    // display warning messages that the functions are not available.
    open fun lights() {
        showMethodWarning("lights")
    }

    open fun noLights() {
        showMethodWarning("noLights")
    }

    open fun ambientLight(red: Float, green: Float, blue: Float) {
        showMethodWarning("ambientLight")
    }

    open fun ambientLight(red: Float, green: Float, blue: Float,
                          x: Float, y: Float, z: Float) {
        showMethodWarning("ambientLight")
    }

    open fun directionalLight(red: Float, green: Float, blue: Float,
                              nx: Float, ny: Float, nz: Float) {
        showMethodWarning("directionalLight")
    }

    open fun pointLight(red: Float, green: Float, blue: Float,
                        x: Float, y: Float, z: Float) {
        showMethodWarning("pointLight")
    }

    open fun spotLight(red: Float, green: Float, blue: Float,
                       x: Float, y: Float, z: Float,
                       nx: Float, ny: Float, nz: Float,
                       angle: Float, concentration: Float) {
        showMethodWarning("spotLight")
    }

    open fun lightFalloff(constant: Float, linear: Float, quadratic: Float) {
        showMethodWarning("lightFalloff")
    }

    open fun lightSpecular(x: Float, y: Float, z: Float) {
        showMethodWarning("lightSpecular")
    }
    //////////////////////////////////////////////////////////////
    // BACKGROUND
    /**
     * Set the background to a gray or ARGB color.
     *
     *
     * For the main drawing surface, the alpha value will be ignored. However,
     * alpha can be used on PGraphics objects from createGraphics(). This is
     * the only way to set all the pixels partially transparent, for instance.
     *
     *
     * Note that background() should be called before any transformations occur,
     * because some implementations may require the current transformation matrix
     * to be identity before drawing.
     */
    fun background(rgb: Int) {
//    if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//      background((float) rgb);
//
//    } else {
//      if (format == RGB) {
//        rgb |= 0xff000000;  // ignore alpha for main drawing surface
//      }
//      colorCalcARGB(rgb, colorModeA);
//      backgroundFromCalc();
//      backgroundImpl();
//    }
        colorCalc(rgb)
        backgroundFromCalc()
    }

    /**
     * See notes about alpha in background(x, y, z, a).
     */
    fun background(rgb: Int, alpha: Float) {
//    if (format == RGB) {
//      background(rgb);  // ignore alpha for main drawing surface
//
//    } else {
//      if (((rgb & 0xff000000) == 0) && (rgb <= colorModeX)) {
//        background((float) rgb, alpha);
//
//      } else {
//        colorCalcARGB(rgb, alpha);
//        backgroundFromCalc();
//        backgroundImpl();
//      }
//    }
        colorCalc(rgb, alpha)
        backgroundFromCalc()
    }

    /**
     * Set the background to a grayscale value, based on the
     * current colorMode.
     */
    fun background(gray: Float) {
        colorCalc(gray)
        backgroundFromCalc()
        //    backgroundImpl();
    }

    /**
     * See notes about alpha in background(x, y, z, a).
     */
    fun background(gray: Float, alpha: Float) {
        if (format == PConstants.RGB) {
            background(gray) // ignore alpha for main drawing surface
        } else {
            colorCalc(gray, alpha)
            backgroundFromCalc()
            //      backgroundImpl();
        }
    }

    /**
     * Set the background to an r, g, b or h, s, b value,
     * based on the current colorMode.
     */
    fun background(x: Float, y: Float, z: Float) {
        colorCalc(x, y, z)
        backgroundFromCalc()
        //    backgroundImpl();
    }

    /**
     * Clear the background with a color that includes an alpha value. This can
     * only be used with objects created by createGraphics(), because the main
     * drawing surface cannot be set transparent.
     *
     *
     * It might be tempting to use this function to partially clear the screen
     * on each frame, however that's not how this function works. When calling
     * background(), the pixels will be replaced with pixels that have that level
     * of transparency. To do a semi-transparent overlay, use fill() with alpha
     * and draw a rectangle.
     */
    fun background(x: Float, y: Float, z: Float, a: Float) {
        colorCalc(x, y, z, a)
        backgroundFromCalc()
    }

    fun clear() {
        background(0f, 0f, 0f, 0f)
    }

    protected fun backgroundFromCalc() {
        backgroundR = calcR
        backgroundG = calcG
        backgroundB = calcB
        backgroundA = if (format == PConstants.RGB) colorModeA else calcA
        backgroundRi = calcRi
        backgroundGi = calcGi
        backgroundBi = calcBi
        backgroundAi = if (format == PConstants.RGB) 255 else calcAi
        backgroundAlpha = if (format == PConstants.RGB) false else calcAlpha
        backgroundColor = calcColor
        backgroundImpl()
    }

    /**
     * Takes an RGB or ARGB image and sets it as the background.
     * The width and height of the image must be the same size as the sketch.
     * Use image.resize(width, height) to make short work of such a task.
     * <P>
     * Note that even if the image is set as RGB, the high 8 bits of each pixel
     * should be set opaque (0xFF000000), because the image data will be copied
     * directly to the screen, and non-opaque background images may have strange
     * behavior. Using image.filter(OPAQUE) will handle this easily.
    </P> * <P>
     * When using 3D, this will also clear the zbuffer (if it exists).
    </P> */
    fun background(image: PImage) {
        if (image.width != width || image.height != height) {
            throw RuntimeException(PConstants.ERROR_BACKGROUND_IMAGE_SIZE)
        }
        if (image.format != PConstants.RGB && image.format != PConstants.ARGB) {
            throw RuntimeException(PConstants.ERROR_BACKGROUND_IMAGE_FORMAT)
        }
        backgroundColor = 0 // just zero it out for images
        backgroundImpl(image)
    }

    /**
     * Actually set the background image. This is separated from the error
     * handling and other semantic goofiness that is shared across renderers.
     */
    protected open fun backgroundImpl(image: PImage?) {
        // blit image to the screen
        set(0, 0, image!!)
    }

    /**
     * Actual implementation of clearing the background, now that the
     * internal variables for background color have been set. Called by the
     * backgroundFromCalc() method, which is what all the other background()
     * methods call once the work is done.
     */
    protected open fun backgroundImpl() {
        pushStyle()
        pushMatrix()
        resetMatrix()
        fill(backgroundColor)
        rect(0f, 0f, width.toFloat(), height.toFloat())
        popMatrix()
        popStyle()
    }

    fun colorMode(mode: Int, max: Float) {
        colorMode(mode, max, max, max, max)
    }
    /**
     * Callback to handle clearing the background when begin/endRaw is in use.
     * Handled as separate function for OpenGL (or other) subclasses that
     * override backgroundImpl() but still needs this to work properly.
     */
    //  protected void backgroundRawImpl() {
    //    if (raw != null) {
    //      raw.colorMode(RGB, 1);
    //      raw.noStroke();
    //      raw.fill(backgroundR, backgroundG, backgroundB);
    //      raw.beginShape(TRIANGLES);
    //
    //      raw.vertex(0, 0);
    //      raw.vertex(width, 0);
    //      raw.vertex(0, height);
    //
    //      raw.vertex(width, 0);
    //      raw.vertex(width, height);
    //      raw.vertex(0, height);
    //
    //      raw.endShape();
    //    }
    //  }
    //////////////////////////////////////////////////////////////
    // COLOR MODE

    fun colorMode(mode: Int) {
        colorMode(mode,colorModeX,colorModeY,colorModeZ,colorModeA)
    }


    fun colorMode(mode: Int,maxX: Float,maxY: Float,maxZ: Float) {
        colorMode(mode,maxX,maxY,maxZ,colorModeA)
    }

    /**
     * Set the colorMode and the maximum values for (r, g, b)
     * or (h, s, b).
     * <P>
     * Note that this doesn't set the maximum for the alpha value,
     * which might be confusing if for instance you switched to
    </P> * <PRE>colorMode(HSB, 360, 100, 100);</PRE>
     * because the alpha values were still between 0 and 255.
     */
    fun colorMode(mode: Int,
                  maxX: Float, maxY: Float, maxZ: Float, maxA: Float) {
        colorMode = mode
        colorModeX = maxX // still needs to be set for hsb
        colorModeY = maxY
        colorModeZ = maxZ
        colorModeA = maxA

        // if color max values are all 1, then no need to scale
        colorModeScale = maxA != 1f || maxX != maxY || maxY != maxZ || maxZ != maxA

        // if color is rgb/0..255 this will make it easier for the
        // red() green() etc functions
        colorModeDefault = colorMode == PConstants.RGB &&
                colorModeA == 255f && colorModeX == 255f &&
                colorModeY == 255f && colorModeZ == 255f
    }
    //////////////////////////////////////////////////////////////
    // COLOR CALCULATIONS
    // Given input values for coloring, these functions will fill the calcXxxx
    // variables with values that have been properly filtered through the
    // current colorMode settings.
    // Renderers that need to subclass any drawing properties such as fill or
    // stroke will usally want to override methods like fillFromCalc (or the
    // same for stroke, ambient, etc.) That way the color calcuations are
    // covered by this based PGraphics class, leaving only a single function
    // to override/implement in the subclass.
    /**
     * Set the fill to either a grayscale value or an ARGB int.
     * <P>
     * The problem with this code is that it has to detect between these two
     * situations automatically. This is done by checking to see if the high bits
     * (the alpha for 0xAA000000) is set, and if not, whether the color value
     * that follows is less than colorModeX (first param passed to colorMode).
    </P> * <P>
     * This auto-detect would break in the following situation:
    </P> * <PRE>size(256, 256);
     * for (int i = 0; i < 256; i++) {
     * color c = color(0, 0, 0, i);
     * stroke(c);
     * line(i, 0, i, 256);
     * }</PRE>
     * ...on the first time through the loop, where (i == 0), since the color
     * itself is zero (black) then it would appear indistinguishable from code
     * that reads "fill(0)". The solution is to use the four parameter versions
     * of stroke or fill to more directly specify the desired result.
     */
    protected fun colorCalc(rgb: Int) {
        if (rgb and -0x1000000 == 0 && rgb <= colorModeX) {
            colorCalc(rgb.toFloat())
        } else {
            colorCalcARGB(rgb, colorModeA)
        }
    }

    protected fun colorCalc(rgb: Int, alpha: Float) {
        if (rgb and -0x1000000 == 0 && rgb <= colorModeX) {  // see above
            colorCalc(rgb.toFloat(), alpha)
        } else {
            colorCalcARGB(rgb, alpha)
        }
    }

    @JvmOverloads
    protected fun colorCalc(gray: Float, alpha: Float = colorModeA) {
        var gray = gray
        var alpha = alpha
        if (gray > colorModeX) gray = colorModeX
        if (alpha > colorModeA) alpha = colorModeA
        if (gray < 0) gray = 0f
        if (alpha < 0) alpha = 0f
        calcR = if (colorModeScale) gray / colorModeX else gray
        calcG = calcR
        calcB = calcR
        calcA = if (colorModeScale) alpha / colorModeA else alpha
        calcRi = (calcR * 255).toInt()
        calcGi = (calcG * 255).toInt()
        calcBi = (calcB * 255).toInt()
        calcAi = (calcA * 255).toInt()
        calcColor = calcAi shl 24 or (calcRi shl 16) or (calcGi shl 8) or calcBi
        calcAlpha = calcAi != 255
    }

    @JvmOverloads
    protected fun colorCalc(x: Float, y: Float, z: Float, a: Float = colorModeA) {
        var x = x
        var y = y
        var z = z
        var a = a
        if (x > colorModeX) x = colorModeX
        if (y > colorModeY) y = colorModeY
        if (z > colorModeZ) z = colorModeZ
        if (a > colorModeA) a = colorModeA
        if (x < 0) x = 0f
        if (y < 0) y = 0f
        if (z < 0) z = 0f
        if (a < 0) a = 0f
        when (colorMode) {
            PConstants.RGB -> if (colorModeScale) {
                calcR = x / colorModeX
                calcG = y / colorModeY
                calcB = z / colorModeZ
                calcA = a / colorModeA
            } else {
                calcR = x
                calcG = y
                calcB = z
                calcA = a
            }
            PConstants.HSB -> {
                x /= colorModeX // h
                y /= colorModeY // s
                z /= colorModeZ // b
                calcA = if (colorModeScale) a / colorModeA else a
                if (y == 0f) {  // saturation == 0
                    calcB = z
                    calcG = calcB
                    calcR = calcG
                } else {
                    val which = (x - x.toInt()) * 6.0f
                    val f = which - which.toInt()
                    val p = z * (1.0f - y)
                    val q = z * (1.0f - y * f)
                    val t = z * (1.0f - y * (1.0f - f))
                    when (which.toInt()) {
                        0 -> {
                            calcR = z
                            calcG = t
                            calcB = p
                        }
                        1 -> {
                            calcR = q
                            calcG = z
                            calcB = p
                        }
                        2 -> {
                            calcR = p
                            calcG = z
                            calcB = t
                        }
                        3 -> {
                            calcR = p
                            calcG = q
                            calcB = z
                        }
                        4 -> {
                            calcR = t
                            calcG = p
                            calcB = z
                        }
                        5 -> {
                            calcR = z
                            calcG = p
                            calcB = q
                        }
                    }
                }
            }
        }
        calcRi = (255 * calcR).toInt()
        calcGi = (255 * calcG).toInt()
        calcBi = (255 * calcB).toInt()
        calcAi = (255 * calcA).toInt()
        calcColor = calcAi shl 24 or (calcRi shl 16) or (calcGi shl 8) or calcBi
        calcAlpha = calcAi != 255
    }

    /**
     * Unpacks AARRGGBB color for direct use with colorCalc.
     * <P>
     * Handled here with its own function since this is indepenent
     * of the color mode.
    </P> * <P>
     * Strangely the old version of this code ignored the alpha
     * value. not sure if that was a bug or what.
    </P> * <P>
     * Note, no need for a bounds check since it's a 32 bit number.
    </P> */
    protected fun colorCalcARGB(argb: Int, alpha: Float) {
        if (alpha == colorModeA) {
            calcAi = argb shr 24 and 0xff
            calcColor = argb
        } else {
            calcAi = ((argb shr 24 and 0xff) * (alpha / colorModeA)).toInt()
            calcColor = calcAi shl 24 or (argb and 0xFFFFFF)
        }
        calcRi = argb shr 16 and 0xff
        calcGi = argb shr 8 and 0xff
        calcBi = argb and 0xff
        calcA = calcAi.toFloat() / 255.0f
        calcR = calcRi.toFloat() / 255.0f
        calcG = calcGi.toFloat() / 255.0f
        calcB = calcBi.toFloat() / 255.0f
        calcAlpha = calcAi != 255
    }

    //////////////////////////////////////////////////////////////
    // COLOR DATATYPE STUFFING
    // The 'color' primitive type in Processing syntax is in fact a 32-bit int.
    // These functions handle stuffing color values into a 32-bit cage based
    // on the current colorMode settings.
    // These functions are really slow (because they take the current colorMode
    // into account), but they're easy to use. Advanced users can write their
    // own bit shifting operations to setup 'color' data types.
    fun color(gray: Int): Int {  // ignore
        var gray = gray
        if (gray and -0x1000000 == 0 && gray <= colorModeX) {
            if (colorModeDefault) {
                // bounds checking to make sure the numbers aren't to high or low
                if (gray > 255) gray = 255 else if (gray < 0) gray = 0
                return -0x1000000 or (gray shl 16) or (gray shl 8) or gray
            } else {
                colorCalc(gray)
            }
        } else {
            colorCalcARGB(gray, colorModeA)
        }
        return calcColor
    }

    fun color(gray: Float): Int {  // ignore
        colorCalc(gray)
        return calcColor
    }

    /**
     * @param gray can be packed ARGB or a gray in this case
     */
    fun color(gray: Int, alpha: Int): Int {  // ignore
        var gray = gray
        var alpha = alpha
        if (colorModeDefault) {
            // bounds checking to make sure the numbers aren't to high or low
            if (gray > 255) gray = 255 else if (gray < 0) gray = 0
            if (alpha > 255) alpha = 255 else if (alpha < 0) alpha = 0
            return alpha and 0xff shl 24 or (gray shl 16) or (gray shl 8) or gray
        }
        colorCalc(gray, alpha.toFloat())
        return calcColor
    }

    /**
     * @param rgb can be packed ARGB or a gray in this case
     */
    fun color(rgb: Int, alpha: Float): Int {  // ignore
        if (rgb and -0x1000000 == 0 && rgb <= colorModeX) {
            colorCalc(rgb, alpha)
        } else {
            colorCalcARGB(rgb, alpha)
        }
        return calcColor
    }

    fun color(gray: Float, alpha: Float): Int {  // ignore
        colorCalc(gray, alpha)
        return calcColor
    }

    fun color(x: Int, y: Int, z: Int): Int {  // ignore
        var x = x
        var y = y
        var z = z
        if (colorModeDefault) {
            // bounds checking to make sure the numbers aren't to high or low
            if (x > 255) x = 255 else if (x < 0) x = 0
            if (y > 255) y = 255 else if (y < 0) y = 0
            if (z > 255) z = 255 else if (z < 0) z = 0
            return -0x1000000 or (x shl 16) or (y shl 8) or z
        }
        colorCalc(x.toFloat(), y.toFloat(), z.toFloat())
        return calcColor
    }

    fun color(x: Float, y: Float, z: Float): Int {  // ignore
        colorCalc(x, y, z)
        return calcColor
    }

    fun color(x: Int, y: Int, z: Int, a: Int): Int {  // ignore
        var x = x
        var y = y
        var z = z
        var a = a
        if (colorModeDefault) {
            // bounds checking to make sure the numbers aren't to high or low
            if (a > 255) a = 255 else if (a < 0) a = 0
            if (x > 255) x = 255 else if (x < 0) x = 0
            if (y > 255) y = 255 else if (y < 0) y = 0
            if (z > 255) z = 255 else if (z < 0) z = 0
            return a shl 24 or (x shl 16) or (y shl 8) or z
        }
        colorCalc(x.toFloat(), y.toFloat(), z.toFloat(), a.toFloat())
        return calcColor
    }

    fun color(x: Float, y: Float, z: Float, a: Float): Int {  // ignore
        colorCalc(x, y, z, a)
        return calcColor
    }

    //////////////////////////////////////////////////////////////
    // COLOR DATATYPE EXTRACTION
    // Vee have veys of making the colors talk.
    fun alpha(what: Int): Float {
        val c = (what shr 24 and 0xff.toFloat().toInt()).toFloat()
        return if (colorModeA == 255f) c else c / 255.0f * colorModeA
    }

    fun red(what: Int): Float {
        val c = (what shr 16 and 0xff.toFloat().toInt()).toFloat()
        return if (colorModeDefault) c else c / 255.0f * colorModeX
    }

    fun green(what: Int): Float {
        val c = (what shr 8 and 0xff.toFloat().toInt()).toFloat()
        return if (colorModeDefault) c else c / 255.0f * colorModeY
    }

    fun blue(what: Int): Float {
        val c = (what and 0xff.toFloat().toInt()).toFloat()
        return if (colorModeDefault) c else c / 255.0f * colorModeZ
    }

    fun hue(what: Int): Float {
        if (what != cacheHsbKey) {
            Color.RGBToHSV(what shr 16 and 0xff, what shr 8 and 0xff,
                    what and 0xff, cacheHsbValue)
            cacheHsbKey = what
        }
        return cacheHsbValue[0] / 360f * colorModeX
    }

    fun saturation(what: Int): Float {
        if (what != cacheHsbKey) {
            Color.RGBToHSV(what shr 16 and 0xff, what shr 8 and 0xff,
                    what and 0xff, cacheHsbValue)
            cacheHsbKey = what
        }
        return cacheHsbValue[1] * colorModeY
    }

    fun brightness(what: Int): Float {
        if (what != cacheHsbKey) {
            Color.RGBToHSV(what shr 16 and 0xff, what shr 8 and 0xff,
                    what and 0xff, cacheHsbValue)
            cacheHsbKey = what
        }
        return cacheHsbValue[2] * colorModeZ
    }
    //////////////////////////////////////////////////////////////
    // BEGINRAW/ENDRAW
    /**
     * Record individual lines and triangles by echoing them to another renderer.
     */
    open fun beginRaw(rawGraphics: PGraphics) {  // ignore
        raw = rawGraphics
        rawGraphics.beginDraw()
    }

    open fun endRaw() {  // ignore
        if (raw != null) {
            // for 3D, need to flush any geometry that's been stored for sorting
            // (particularly if the ENABLE_DEPTH_SORT hint is set)
            flush()

            // just like beginDraw, this will have to be called because
            // endDraw() will be happening outside of draw()
            raw!!.endDraw()
            raw!!.dispose()
            raw = null
        }
    }

    fun haveRaw(): Boolean { // ignore
        return raw != null
    }

    /**
     * First try to create a default font, but if that's not possible, throw
     * an exception that halts the program because textFont() has not been used
     * prior to the specified method.
     */
    /**
     * Same as below, but defaults to a 12 point font, just as MacWrite intended.
     */
    @JvmOverloads
    protected fun defaultFontOrDeath(method: String, size: Float = 12f) {
        textFont = if (parent != null) {
            parent!!.createDefaultFont(size)
        } else {
            throw RuntimeException("Use textFont() before $method()")
        }
    }
    //////////////////////////////////////////////////////////////
    // RENDERER SUPPORT QUERIES
    /**
     * Return true if this renderer should be drawn to the screen. Defaults to
     * returning true, since nearly all renderers are on-screen beasts. But can
     * be overridden for subclasses like PDF so that a window doesn't open up.
     * <br></br> <br></br>
     * A better name? showFrame, displayable, isVisible, visible, shouldDisplay,
     * what to call this?
     */
    fun displayable(): Boolean {
        return true
    }

    /**
     * Return true if this renderer supports 2D drawing. Defaults to true.
     */
    open fun is2D(): Boolean {
        return true
    }

    /**
     * Return true if this renderer supports 2D drawing. Defaults to false.
     */
    open fun is3D(): Boolean {
        return false
    }

    /**
     * Return true if this renderer does rendering through OpenGL. Defaults to false.
     */
    open val isGL: Boolean
        get() = false

    //////////////////////////////////////////////////////////////
    // ASYNC IMAGE SAVING
    override fun save(filename: String): Boolean { // ignore
        if (hints[PConstants.DISABLE_ASYNC_SAVEFRAME]) {
            return super.save(filename)
        }
        if (asyncImageSaver == null) {
            asyncImageSaver = AsyncImageSaver()
        }
        if (!isLoaded) loadPixels()
        val target = asyncImageSaver!!.getAvailableTarget(pixelWidth, pixelHeight,
                format) ?: return false
        val count: Int = PApplet.min(pixels!!.size, target.pixels!!.size)
        System.arraycopy(pixels, 0, target.pixels, 0, count)
        asyncImageSaver!!.saveTargetAsync(this, target, filename)
        return true
    }

    protected open fun processImageBeforeAsyncSave(image: PImage?) {}
    class AsyncImageSaver // ignore
    {
        var targetPool: BlockingQueue<PImage> = ArrayBlockingQueue(TARGET_COUNT)
        var saveExecutor = Executors.newFixedThreadPool(TARGET_COUNT)
        var targetsCreated = 0

        @Volatile
        var avgNanos: Long = 0
        var lastTime: Long = 0
        var lastFrameCount = 0
        fun dispose() { // ignore
            saveExecutor.shutdown()
            try {
                saveExecutor.awaitTermination(5000, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
            }
        }

        fun hasAvailableTarget(): Boolean { // ignore
            return targetsCreated < TARGET_COUNT || targetPool.isEmpty()
        }

        /**
         * After taking a target, you must call saveTargetAsync() or
         * returnUnusedTarget(), otherwise one thread won't be able to run
         */
        fun getAvailableTarget(requestedWidth: Int, requestedHeight: Int,  // ignore
                               format: Int): PImage? {
            return try {
                val target: PImage
                if (targetsCreated < TARGET_COUNT && targetPool.isEmpty()) {
                    target = PImage(requestedWidth, requestedHeight)
                    targetsCreated++
                } else {
                    target = targetPool.take()
                    if (target.width != requestedWidth ||
                            target.height != requestedHeight) {
                        target.width = requestedWidth
                        target.height = requestedHeight
                        // TODO: this kills performance when saving different sizes
                        target.pixels = IntArray(requestedWidth * requestedHeight)
                    }
                }
                target.format = format
                target
            } catch (e: InterruptedException) {
                null
            }
        }

        fun returnUnusedTarget(target: PImage) { // ignore
            targetPool.offer(target)
        }

        fun saveTargetAsync(renderer: PGraphics, target: PImage,  // ignore
                            filename: String?) {
            target.parent = renderer.parent

            // if running every frame, smooth the framerate
            if (target.parent!!.frameCount - 1 == lastFrameCount && TARGET_COUNT > 1) {

                // count with one less thread to reduce jitter
                // 2 cores - 1 save thread - no wait
                // 4 cores - 3 save threads - wait 1/2 of save time
                // 8 cores - 7 save threads - wait 1/6 of save time
                val avgTimePerFrame = avgNanos / Math.max(1, TARGET_COUNT - 1)
                val now = System.nanoTime()
                val delay = PApplet.round((lastTime + avgTimePerFrame - now) / 1e6f).toLong()
                try {
                    if (delay > 0) Thread.sleep(delay)
                } catch (e: InterruptedException) {
                }
            }
            lastFrameCount = target.parent!!.frameCount
            lastTime = System.nanoTime()
            try {
                saveExecutor.submit { // ignore
                    try {
                        val startTime = System.nanoTime()
                        renderer.processImageBeforeAsyncSave(target)
                        target.save(filename!!)
                        val saveNanos = System.nanoTime() - startTime
                        synchronized(this@AsyncImageSaver) {
                            avgNanos = if (avgNanos == 0L) {
                                saveNanos
                            } else if (saveNanos < avgNanos) {
                                (avgNanos * (TIME_AVG_FACTOR - 1) + saveNanos) /
                                        TIME_AVG_FACTOR
                            } else {
                                saveNanos
                            }
                        }
                    } finally {
                        targetPool.offer(target)
                    }
                }
            } catch (e: RejectedExecutionException) {
                // the executor service was probably shut down, no more saving for us
            }
        }

        companion object {
            val TARGET_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
            const val TIME_AVG_FACTOR = 32
        }
    }
}