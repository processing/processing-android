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

import android.graphics.*
import java.io.*
import java.util.*
import kotlin.experimental.and

/**
 * Grayscale bitmap font class used by Processing.
 * <P>
 * Awful (and by that, I mean awesome) ASCII (non-)art for how this works:
</P> * <PRE>
 * |
 * |                   height is the full used height of the image
 * |
 * |   ..XX..       }
 * |   ..XX..       }
 * |   ......       }
 * |   XXXX..       }  topExtent (top y is baseline - topExtent)
 * |   ..XX..       }
 * |   ..XX..       }  dotted areas are where the image data
 * |   ..XX..       }  is actually located for the character
 * +---XXXXXX----   }  (it extends to the right and down
 * |                   for power of two texture sizes)
 * ^^^^ leftExtent (amount to move over before drawing the image
 *
 * ^^^^^^^^^^^^^^ setWidth (width displaced by char)
</PRE> *
 */
open class PFont : PConstants {

    /** Number of character glyphs in this font.  */
    var glyphCount = 0
        protected set

    /**
     * Actual glyph data. The length of this array won't necessarily be the
     * same size as glyphCount, in cases where lazy font loading is in use.
     */
    protected lateinit var glyphs: Array<Glyph?>

    /**
     * Name of the font as seen by Java when it was created.
     * If the font is available, the native version will be used.
     */
    var name: String? = null
        protected set

    /**
     * Postscript name of the font that this bitmap was created from.
     */
    var postScriptName: String? = null
        protected set

    /**
     * Return size of this font.
     */
    /**
     * Returns the size that will be used when textFont(font) is called.
     */
    /**
     * The original size of the font when it was first created
     */
    @JvmField
    var size = 0

    /** true if smoothing was enabled for this font, used for native impl  */
    var isSmooth = false
        protected set

    /**
     * The ascent of the font. If the 'd' character is present in this PFont,
     * this value is replaced with its pixel height, because the values returned
     * by FontMetrics.getAscent() seem to be terrible.
     */
    protected var ascent = 0

    /**
     * The descent of the font. If the 'p' character is present in this PFont,
     * this value is replaced with its lowest pixel height, because the values
     * returned by FontMetrics.getDescent() are gross.
     */
    protected var descent = 0

    /**
     * A more efficient array lookup for straight ASCII characters. For Unicode
     * characters, a QuickSort-style search is used.
     */
    protected lateinit var ascii: IntArray

    /**
     * True if this font is set to load dynamically. This is the default when
     * createFont() method is called without a character set. Bitmap versions of
     * characters are only created when prompted by an index() call.
     */
    protected var lazy = false

    /**
     * Native Android version of the font. If possible, this allows the
     * PGraphics subclass to just use Android's font rendering stuff
     * in situations where that's faster.
     */
    protected var typeface: Typeface? = null

    /**
     * True if this font should return 'null' for getFont(), so that the native
     * font will be used to create a subset, but the native version of the font
     * will not be used.
     */
    protected var subsetting = false

    /**
     * True if we've already tried to find the native version of this font.
     */
    protected var typefaceSearched = false

    // objects to handle creation of font characters only as they're needed
    var lazyBitmap: Bitmap? = null
    var lazyCanvas: Canvas? = null

    lateinit var lazyPaint: Paint

    //  FontMetrics lazyMetrics;
    lateinit var lazySamples: IntArray

    /** for subclasses that need to store metadata about the font  */
    protected var cacheMap: HashMap<PGraphics, Any>? = null

    constructor() {} // for subclasses
    /**
     * Create a new image-based font on the fly. If charset is set to null,
     * the characters will only be created as bitmaps when they're drawn.
     *
     * @param font the font object to create from
     * @param charset array of all unicode chars that should be included
     * @param smooth true to enable smoothing/anti-aliasing
     */
    /**
     * Create a new Processing font from a native font, but don't create all the
     * characters at once, instead wait until they're used to include them.
     * @param font
     * @param smooth
     */
    @JvmOverloads
    constructor(font: Typeface?, size: Int, smooth: Boolean, charset: CharArray? = null) {
        // save this so that we can use the native version
        typeface = font
        isSmooth = smooth
        name = "" //font.getName();
        postScriptName = "" //font.getPSName();
        this.size = size //font.getSize();

        val initialCount = 10
        glyphs = arrayOfNulls(initialCount)
        ascii = IntArray(128)

        Arrays.fill(ascii, -1)
        val mbox3 = size * 3

//    lazyImage = new BufferedImage(mbox3, mbox3, BufferedImage.TYPE_INT_RGB);
        lazyBitmap = Bitmap.createBitmap(mbox3, mbox3, Bitmap.Config.ARGB_8888)
        //    lazyGraphics = (Graphics2D) lazyImage.getGraphics();
        lazyCanvas = Canvas(lazyBitmap)

//    lazyGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                                  smooth ?
//                                  RenderingHints.VALUE_ANTIALIAS_ON :
//                                  RenderingHints.VALUE_ANTIALIAS_OFF);
//    // adding this for post-1.0.9
//    lazyGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
//                                  smooth ?
//                                  RenderingHints.VALUE_TEXT_ANTIALIAS_ON :
//                                  RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        lazyPaint = Paint()
        lazyPaint.isAntiAlias = smooth

//    lazyGraphics.setFont(font);
        lazyPaint.typeface = font

        //    lazyMetrics = lazyGraphics.getFontMetrics();
        lazyPaint.textSize = size.toFloat()
        lazySamples = IntArray(mbox3 * mbox3)

//    ascent = lazyMetrics.getAscent();
//    descent = lazyMetrics.getDescent();
        if (charset == null) {
            lazy = true
        } else {
            // charset needs to be sorted to make index lookup run more quickly
            // http://dev.processing.org/bugs/show_bug.cgi?id=494
            Arrays.sort(charset)
            glyphs = arrayOfNulls(charset.size)
            glyphCount = 0

            for (c in charset) {
                val glyf = Glyph(c)
                if (glyf.value < 128) {
                    ascii[glyf.value] = glyphCount
                }
                glyf.index = glyphCount
                glyphs[glyphCount++] = glyf
            }

            // shorten the array if necessary
//      if (glyphCount != charset.length) {
//        glyphs = (Glyph[]) PApplet.subset(glyphs, 0, glyphCount);
//      }
        }

        // If not already created, just create these two characters to calculate
        // the ascent and descent values for the font. This was tested to only
        // require 5-10 ms on a 2.4 GHz MacBook Pro.
        // In versions 1.0.9 and earlier, fonts that could not display d or p
        // used the max up/down values as calculated by looking through the font.
        // That's no longer valid with the auto-generating fonts, so we'll just
        // use getAscent() and getDescent() in such (minor) cases.
        if (ascent == 0) {
            Glyph('d')
            if (ascent == 0) {  // character not valid
                ascent = PApplet.round(lazyPaint.ascent())
            }
        }
        if (descent == 0) {
            Glyph('p')
            if (descent == 0) {
                descent = PApplet.round(lazyPaint.descent())
            }
        }
    }


    constructor(input: InputStream?) {
        val `is` = DataInputStream(input)

        // number of character images stored in this font
        glyphCount = `is`.readInt()

        // used to be the bitCount, but now used for version number.
        // version 8 is any font before 69, so 9 is anything from 83+
        // 9 was buggy so gonna increment to 10.
        val version = `is`.readInt()

        // this was formerly ignored, now it's the actual font size
        //mbox = is.readInt();
        size = `is`.readInt()

        // this was formerly mboxY, the one that was used
        // this will make new fonts downward compatible
        `is`.readInt() // ignore the other mbox attribute
        ascent = `is`.readInt() // formerly baseHt (zero/ignored)
        descent = `is`.readInt() // formerly ignored struct padding

        // allocate enough space for the character info
        glyphs = arrayOfNulls(glyphCount)
        ascii = IntArray(128)
        Arrays.fill(ascii, -1)

        // read the information about the individual characters
        for (i in 0 until glyphCount) {

            val glyph = Glyph(`is`)

            // cache locations of the ascii charset
            if (glyph.value < 128) {
                ascii[glyph.value] = i
            }
            glyph.index = i
            glyphs[i] = glyph
        }

        // not a roman font, so throw an error and ask to re-build.
        // that way can avoid a bunch of error checking hacks in here.
        if (ascent == 0 && descent == 0) {
            throw RuntimeException("Please use \"Create Font\" to " +
                    "re-create this font.")
        }

        for (glyph in glyphs) {
            glyph!!.readBitmap(`is`)
        }

        if (version >= 10) {  // includes the font name at the end of the file
            name = `is`.readUTF()
            postScriptName = `is`.readUTF()
        }

        if (version == 11) {
            isSmooth = `is`.readBoolean()
        }

    }


    /**
     * Write this PFont to an OutputStream.
     *
     *
     * This is used by the Create Font tool, or whatever anyone else dreams
     * up for messing with fonts themselves.
     *
     *
     * It is assumed that the calling class will handle closing
     * the stream when finished.
     */
    @Throws(IOException::class)
    fun save(output: OutputStream?) {

        val os = DataOutputStream(output)

        os.writeInt(glyphCount)

        if (name == null || postScriptName == null) {
            name = ""
            postScriptName = ""
        }

        os.writeInt(11) // formerly numBits, now used for version number
        os.writeInt(size) // formerly mboxX (was 64, now 48)
        os.writeInt(0) // formerly mboxY, now ignored
        os.writeInt(ascent) // formerly baseHt (was ignored)
        os.writeInt(descent) // formerly struct padding for c version

        for (i in 0 until glyphCount) {
            glyphs[i]!!.writeHeader(os)
        }

        for (i in 0 until glyphCount) {
            glyphs[i]!!.writeBitmap(os)
        }

        // version 11
        os.writeUTF(name)
        os.writeUTF(postScriptName)
        os.writeBoolean(isSmooth)
        os.flush()
    }


    /**
     * Create a new glyph, and add the character to the current font.
     * @param c character to create an image for.
     */
    protected fun addGlyph(c: Char) {
        val glyph = Glyph(c)

        if (glyphCount == glyphs.size) {
            glyphs = PApplet.expand(glyphs) as Array<Glyph?>
        }

        if (glyphCount == 0) {
            glyph.index = 0
            glyphs[glyphCount] = glyph
            if (glyph.value < 128) {
                ascii[glyph.value] = 0
            }
        } else if (glyphs[glyphCount - 1]!!.value < glyph.value) {
            glyphs[glyphCount] = glyph
            if (glyph.value < 128) {
                ascii[glyph.value] = glyphCount
            }
        } else {
            for (i in 0 until glyphCount) {
                if (glyphs[i]!!.value > c.toInt()) {
                    for (j in glyphCount downTo i + 1) {
                        glyphs[j] = glyphs[j - 1]
                        if (glyphs[j]!!.value < 128) {
                            ascii[glyphs[j]!!.value] = j
                        }
                    }
                    glyph.index = i
                    glyphs[i] = glyph
                    // cache locations of the ascii charset
                    if (c.toInt() < 128) ascii[c.toInt()] = i
                    break
                }
            }
        }

        glyphCount++
    }

    fun setSubsetting() {
        subsetting = true
    }


    /**
     * Return the native Typeface object associated with this PFont (if any).
     */
    /**
     * Set the native complement of this font.
     */
    var native: Any?
        get() = if (subsetting) {
            null
        } else typeface
        set(typeface) {
            this.typeface = typeface as Typeface?
        }

    fun getGlyph(c: Char): Glyph? {
        val index = index(c)
        return if (index == -1) null else glyphs[index]
    }


    /**
     * Get index for the character.
     * @return index into arrays or -1 if not found
     */
    protected fun index(c: Char): Int {
        return if (lazy) {
            val index = indexActual(c)
            if (index != -1) {
                return index
            }
            //      if (font.canDisplay(c)) {
            // create the glyph
            addGlyph(c)
            // now where did i put that?
            indexActual(c)

//      } else {
//        return -1;
//      }
        } else {
            indexActual(c)
        }
    }


    protected fun indexActual(c: Char): Int {
        // degenerate case, but the find function will have trouble
        // if there are somehow zero chars in the lookup
        //if (value.length == 0) return -1;
        if (glyphCount == 0) return -1

        // quicker lookup for the ascii fellers
        return if (c.toInt() < 128) ascii[c.toInt()] else indexHunt(c.toInt(), 0, glyphCount - 1)

        // some other unicode char, hunt it out
        //return index_hunt(c, 0, value.length-1);
    }


    protected fun indexHunt(c: Int, start: Int, stop: Int): Int {
        val pivot = (start + stop) / 2

        // if this is the char, then return it
        if (c == glyphs[pivot]!!.value) return pivot

        // char doesn't exist, otherwise would have been the pivot
        //if (start == stop) return -1;
        if (start >= stop) return -1

        // if it's in the lower half, continue searching that
        return if (c < glyphs[pivot]!!.value) indexHunt(c, start, pivot - 1) else indexHunt(c, pivot + 1, stop)

        // if it's in the upper half, continue there
    }


    /**
     * Currently un-implemented for .vlw fonts,
     * but honored for layout in case subclasses use it.
     */
    fun kern(a: Char, b: Char): Float {
        return 0F
    }


    /**
     * Returns the ascent of this font from the baseline.
     * The value is based on a font of size 1.
     */
    fun ascent(): Float {
        return ascent.toFloat() / size.toFloat()
    }


    /**
     * Returns how far this font descends from the baseline.
     * The value is based on a font size of 1.
     */
    fun descent(): Float {
        return descent.toFloat() / size.toFloat()
    }


    /**
     * Width of this character for a font of size 1.
     */
    fun width(c: Char): Float {
        if (c.toInt() == 32) return width('i')
        val cc = index(c)
        return if (cc == -1) 0F else glyphs[cc]!!.setWidth.toFloat() / size.toFloat()
    }


    //////////////////////////////////////////////////////////////

    // METADATA REQUIRED BY THE RENDERERS

    /**
     * Store data of some kind for a renderer that requires extra metadata of
     * some kind. Usually this is a renderer-specific representation of the
     * font data, for instance a custom OpenGL texture for PGraphicsOpenGL2.
     * @param renderer The PGraphics renderer associated to the font
     * @param storage The metadata required by the renderer
     */
    fun setCache(renderer: PGraphics, storage: Any) {
        if (cacheMap == null) cacheMap = HashMap()
        cacheMap!![renderer] = storage
    }


    /**
     * Get cache storage data for the specified renderer. Because each renderer
     * will cache data in different formats, it's necessary to store cache data
     * keyed by the renderer object. Otherwise, attempting to draw the same
     * image to both a PGraphicsJava2D and a PGraphicsOpenGL2 will cause errors.
     * @param renderer The PGraphics renderer associated to the font
     * @return metadata stored for the specified renderer
     */
    fun getCache(renderer: PGraphics?): Any? {
        return if (cacheMap == null) null else cacheMap!![renderer]
    }


    /**
     * Remove information associated with this renderer from the cache, if any.
     * @param parent The PGraphics renderer whose cache data should be removed
     */
    fun removeCache(renderer: PGraphics?) {
        if (cacheMap != null) {
            cacheMap!!.remove(renderer)
        }
    }


    fun getGlyph(i: Int): Glyph? {
        return glyphs[i]
    }


    companion object {

        /**
         * Array of the native system fonts. Used to lookup native fonts by their
         * PostScript name. This is a workaround for a several year old Apple Java
         * bug that they can't be bothered to fix.
         */
        protected lateinit var typefaces: Array<Typeface>


        /**
         * Attempt to find the native version of this font.
         * (Public so that it can be used by OpenGL or other renderers.)
         */
        @JvmStatic
        fun findNative(name: String?): Any? {
            loadTypefaces()
            return typefaceMap!![name]
        }

        //////////////////////////////////////////////////////////////

        val EXTRA_CHARS = charArrayOf(
                0x0080.toChar(), 0x0081.toChar(), 0x0082.toChar(), 0x0083.toChar(), 0x0084.toChar(), 0x0085.toChar(), 0x0086.toChar(), 0x0087.toChar(),
                0x0088.toChar(), 0x0089.toChar(), 0x008A.toChar(), 0x008B.toChar(), 0x008C.toChar(), 0x008D.toChar(), 0x008E.toChar(), 0x008F.toChar(),
                0x0090.toChar(), 0x0091.toChar(), 0x0092.toChar(), 0x0093.toChar(), 0x0094.toChar(), 0x0095.toChar(), 0x0096.toChar(), 0x0097.toChar(),
                0x0098.toChar(), 0x0099.toChar(), 0x009A.toChar(), 0x009B.toChar(), 0x009C.toChar(), 0x009D.toChar(), 0x009E.toChar(), 0x009F.toChar(),
                0x00A0.toChar(), 0x00A1.toChar(), 0x00A2.toChar(), 0x00A3.toChar(), 0x00A4.toChar(), 0x00A5.toChar(), 0x00A6.toChar(), 0x00A7.toChar(),
                0x00A8.toChar(), 0x00A9.toChar(), 0x00AA.toChar(), 0x00AB.toChar(), 0x00AC.toChar(), 0x00AD.toChar(), 0x00AE.toChar(), 0x00AF.toChar(),
                0x00B0.toChar(), 0x00B1.toChar(), 0x00B4.toChar(), 0x00B5.toChar(), 0x00B6.toChar(), 0x00B7.toChar(), 0x00B8.toChar(), 0x00BA.toChar(),
                0x00BB.toChar(), 0x00BF.toChar(), 0x00C0.toChar(), 0x00C1.toChar(), 0x00C2.toChar(), 0x00C3.toChar(), 0x00C4.toChar(), 0x00C5.toChar(),
                0x00C6.toChar(), 0x00C7.toChar(), 0x00C8.toChar(), 0x00C9.toChar(), 0x00CA.toChar(), 0x00CB.toChar(), 0x00CC.toChar(), 0x00CD.toChar(),
                0x00CE.toChar(), 0x00CF.toChar(), 0x00D1.toChar(), 0x00D2.toChar(), 0x00D3.toChar(), 0x00D4.toChar(), 0x00D5.toChar(), 0x00D6.toChar(),
                0x00D7.toChar(), 0x00D8.toChar(), 0x00D9.toChar(), 0x00DA.toChar(), 0x00DB.toChar(), 0x00DC.toChar(), 0x00DD.toChar(), 0x00DF.toChar(),
                0x00E0.toChar(), 0x00E1.toChar(), 0x00E2.toChar(), 0x00E3.toChar(), 0x00E4.toChar(), 0x00E5.toChar(), 0x00E6.toChar(), 0x00E7.toChar(),
                0x00E8.toChar(), 0x00E9.toChar(), 0x00EA.toChar(), 0x00EB.toChar(), 0x00EC.toChar(), 0x00ED.toChar(), 0x00EE.toChar(), 0x00EF.toChar(),
                0x00F1.toChar(), 0x00F2.toChar(), 0x00F3.toChar(), 0x00F4.toChar(), 0x00F5.toChar(), 0x00F6.toChar(), 0x00F7.toChar(), 0x00F8.toChar(),
                0x00F9.toChar(), 0x00FA.toChar(), 0x00FB.toChar(), 0x00FC.toChar(), 0x00FD.toChar(), 0x00FF.toChar(), 0x0102.toChar(), 0x0103.toChar(),
                0x0104.toChar(), 0x0105.toChar(), 0x0106.toChar(), 0x0107.toChar(), 0x010C.toChar(), 0x010D.toChar(), 0x010E.toChar(), 0x010F.toChar(),
                0x0110.toChar(), 0x0111.toChar(), 0x0118.toChar(), 0x0119.toChar(), 0x011A.toChar(), 0x011B.toChar(), 0x0131.toChar(), 0x0139.toChar(),
                0x013A.toChar(), 0x013D.toChar(), 0x013E.toChar(), 0x0141.toChar(), 0x0142.toChar(), 0x0143.toChar(), 0x0144.toChar(), 0x0147.toChar(),
                0x0148.toChar(), 0x0150.toChar(), 0x0151.toChar(), 0x0152.toChar(), 0x0153.toChar(), 0x0154.toChar(), 0x0155.toChar(), 0x0158.toChar(),
                0x0159.toChar(), 0x015A.toChar(), 0x015B.toChar(), 0x015E.toChar(), 0x015F.toChar(), 0x0160.toChar(), 0x0161.toChar(), 0x0162.toChar(),
                0x0163.toChar(), 0x0164.toChar(), 0x0165.toChar(), 0x016E.toChar(), 0x016F.toChar(), 0x0170.toChar(), 0x0171.toChar(), 0x0178.toChar(),
                0x0179.toChar(), 0x017A.toChar(), 0x017B.toChar(), 0x017C.toChar(), 0x017D.toChar(), 0x017E.toChar(), 0x0192.toChar(), 0x02C6.toChar(),
                0x02C7.toChar(), 0x02D8.toChar(), 0x02D9.toChar(), 0x02DA.toChar(), 0x02DB.toChar(), 0x02DC.toChar(), 0x02DD.toChar(), 0x03A9.toChar(),
                0x03C0.toChar(), 0x2013.toChar(), 0x2014.toChar(), 0x2018.toChar(), 0x2019.toChar(), 0x201A.toChar(), 0x201C.toChar(), 0x201D.toChar(),
                0x201E.toChar(), 0x2020.toChar(), 0x2021.toChar(), 0x2022.toChar(), 0x2026.toChar(), 0x2030.toChar(), 0x2039.toChar(), 0x203A.toChar(),
                0x2044.toChar(), 0x20AC.toChar(), 0x2122.toChar(), 0x2202.toChar(), 0x2206.toChar(), 0x220F.toChar(), 0x2211.toChar(), 0x221A.toChar(),
                0x221E.toChar(), 0x222B.toChar(), 0x2248.toChar(), 0x2260.toChar(), 0x2264.toChar(), 0x2265.toChar(), 0x25CA.toChar(), 0xF8FF.toChar(),
                0xFB01.toChar(), 0xFB02.toChar())


        /**
         * The default Processing character set.
         * <P>
         * This is the union of the Mac Roman and Windows ANSI (CP1250)
         * character sets. ISO 8859-1 Latin 1 is Unicode characters 0x80 -> 0xFF,
         * and would seem a good standard, but in practice, most P5 users would
         * rather have characters that they expect from their platform's fonts.
        </P> * <P>
         * This is more of an interim solution until a much better
         * font solution can be determined. (i.e. create fonts on
         * the fly from some sort of vector format).
        </P> * <P>
         * Not that I expect that to happen.
        </P> */
        var CHARSET: CharArray

        var typefaceMap: HashMap<String, Typeface>? = null

        lateinit var fontList: Array<String?>

        /**
         * Get a list of the built-in fonts.
         */
        @JvmStatic
        fun list(): Array<String?> {
            loadTypefaces()
            return fontList
        }

        @JvmStatic
        fun loadTypefaces() {
            if (typefaceMap == null) {
                typefaceMap = HashMap()
                typefaceMap!!["Serif"] = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
                typefaceMap!!["Serif-Bold"] = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                typefaceMap!!["Serif-Italic"] = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                typefaceMap!!["Serif-BoldItalic"] = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
                typefaceMap!!["SansSerif"] = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                typefaceMap!!["SansSerif-Bold"] = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                typefaceMap!!["SansSerif-Italic"] = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                typefaceMap!!["SansSerif-BoldItalic"] = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
                typefaceMap!!["Monospaced"] = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                typefaceMap!!["Monospaced-Bold"] = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                typefaceMap!!["Monospaced-Italic"] = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
                typefaceMap!!["Monospaced-BoldItalic"] = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD_ITALIC)
                fontList = arrayOfNulls(typefaceMap!!.size)

                // toArray() doesn't work for Set<T> in kotlin, instead use toTypedArray()
                // https://stackoverflow.com/questions/51369214/how-to-convert-set-hashset-to-array-in-kotlin
                fontList = typefaceMap!!.keys.toTypedArray()
            }
        }

        init {
            CHARSET = CharArray(126 - 33 + 1 + EXTRA_CHARS.size)
            var index = 0
            for (i in 33..126) {
                CHARSET[index++] = i.toChar()
            }
            for (i in EXTRA_CHARS.indices) {
                CHARSET[index++] = EXTRA_CHARS[i]
            }
        }
    }


    /////////////////////////////////////////////////////////////


    /**
     * A single character, and its visage.
     */
    inner class Glyph {
        @JvmField
        var image: PImage? = null

        @JvmField
        var value = 0

        @JvmField
        var height = 0

        @JvmField
        var width = 0
        @JvmField
        var index = 0

        @JvmField
        var setWidth = 0

        @JvmField
        var topExtent = 0

        @JvmField
        var leftExtent = 0

        @JvmField
        var fromStream = false

        protected constructor() {
            // used when reading from a stream or for subclasses
        }

        constructor(`is`: DataInputStream) {
            readHeader(`is`)
        }

        @Throws(IOException::class)
        protected fun readHeader(`is`: DataInputStream) {
            value = `is`.readInt()
            height = `is`.readInt()
            width = `is`.readInt()
            setWidth = `is`.readInt()
            topExtent = `is`.readInt()
            leftExtent = `is`.readInt()

            // pointer from a struct in the c version, ignored
            `is`.readInt()

            // the values for getAscent() and getDescent() from FontMetrics
            // seem to be way too large.. perhaps they're the max?
            // as such, use a more traditional marker for ascent/descent
            if (value == 'd'.toInt()) {
                if (ascent == 0) ascent = topExtent
            }
            if (value == 'p'.toInt()) {
                if (descent == 0) descent = -topExtent + height
            }
        }

        @Throws(IOException::class)
        fun writeHeader(os: DataOutputStream) {
            os.writeInt(value)
            os.writeInt(height)
            os.writeInt(width)
            os.writeInt(setWidth)
            os.writeInt(topExtent)
            os.writeInt(leftExtent)
            os.writeInt(0) // padding
        }

        @Throws(IOException::class)
        fun readBitmap(`is`: DataInputStream) {
            image = PImage(width, height, PConstants.ALPHA)
            val bitmapSize = width * height
            val temp = ByteArray(bitmapSize)
            `is`.readFully(temp)

            // convert the bitmap to an alpha channel
            val w = width
            val h = height
            val pixels = image!!.pixels
            for (y in 0 until h) {
                for (x in 0 until w) {
                    pixels[y * width + x] = (temp[y * w + x] and 0xff.toByte()).toInt()
                    //          System.out.print((image.pixels[y*64+x] > 128) ? "*" : ".");
                }
                //        System.out.println();
            }
            fromStream = true
            //      System.out.println();
        }

        @Throws(IOException::class)
        fun writeBitmap(os: DataOutputStream) {
            val pixels = image!!.pixels
            for (y in 0 until height) {
                for (x in 0 until width) {
                    os.write(pixels[y * width + x] and 0xff)
                }
            }
        }

        constructor(c: Char) {
            val mbox3: Int = size * 3
            //      lazyGraphics.setColor(Color.white);
//      lazyGraphics.fillRect(0, 0, mbox3, mbox3);
            lazyCanvas!!.drawColor(Color.WHITE) // fill canvas with white
            //      lazyGraphics.setColor(Color.black);
            lazyPaint.color = Color.BLACK
            //      lazyGraphics.drawString(String.valueOf(c), size, size * 2);
            lazyCanvas!!.drawText(c.toString(), size.toFloat(), size * 2.toFloat(), lazyPaint)

//      WritableRaster raster = lazyImage.getRaster();
//      raster.getDataElements(0, 0, mbox3, mbox3, lazySamples);
            lazyBitmap!!.getPixels(lazySamples, 0, mbox3, 0, 0, mbox3, mbox3)

            var minX = 1000
            var maxX = 0

            var minY = 1000
            var maxY = 0

            var pixelFound = false

            for (y in 0 until mbox3) {
                for (x in 0 until mbox3) {
                    val sample = lazySamples[y * mbox3 + x] and 0xff
                    if (sample != 255) {
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                        pixelFound = true
                    }
                }
            }
            if (!pixelFound) {
                minY = 0
                minX = minY
                maxY = 0
                maxX = maxY
                // this will create a 1 pixel white (clear) character..
                // maybe better to set one to -1 so nothing is added?
            }
            value = c.toInt()
            height = maxY - minY + 1
            width = maxX - minX + 1
            //      setWidth = lazyMetrics.charWidth(c);
            setWidth = lazyPaint.measureText(c.toString()).toInt()

            // offset from vertical location of baseline
            // of where the char was drawn (size*2)
            topExtent = size * 2 - minY

            // offset from left of where coord was drawn
            leftExtent = minX - size

            image = PImage(width, height, PConstants.ALPHA)
            val pixels = image!!.pixels
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val `val` = 255 - (lazySamples[y * mbox3 + x] and 0xff)
                    val pindex = (y - minY) * width + (x - minX)
                    pixels[pindex] = `val`
                }
            }

            // replace the ascent/descent values with something.. err, decent.
            if (value == 'd'.toInt()) {
                if (ascent == 0) ascent = topExtent
            }
            if (value == 'p'.toInt()) {
                if (descent == 0) descent = -topExtent + height
            }
        }
    }
}