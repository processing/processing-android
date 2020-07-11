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

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.experimental.and

/**
 * Storage class for pixel data. This is the base class for most image and
 * pixel information, such as PGraphics and the video library classes.
 * <P>
 * Code for copying, resizing, scaling, and blending contributed
 * by <A HREF="http://www.toxi.co.uk">toxi</A>.
</P> * <P>
</P> */
open class PImage : PConstants, Cloneable {
    /**
     * Format for this image, one of RGB, ARGB or ALPHA.
     * note that RGB images still require 0xff in the high byte
     * because of how they'll be manipulated by other functions
     */
    @JvmField
    var format = 0

    @JvmField
    var pixels: IntArray? = null

    @JvmField
    var width = 0

    @JvmField
    var height = 0

    /**
     * For the time being, simply to ensure compatibility with Java mode code
     */
    @JvmField
    var pixelDensity = 1

    @JvmField
    var pixelWidth = 0

    @JvmField
    var pixelHeight = 0

    /**
     * Path to parent object that will be used with save().
     * This prevents users from needing savePath() to use PImage.save().
     */
    @JvmField
    var parent: PApplet? = null

    @JvmField
    var bitmap: Bitmap? = null

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /** for renderers that need to store info about the image  */
    private  var cacheMap: HashMap<PGraphics, Any>? = null

    /** for renderers that need to store parameters about the image  */
    @JvmField
    protected var paramMap: HashMap<PGraphics, Any>? = null// ignore

    //////////////////////////////////////////////////////////////

    // MARKING IMAGE AS MODIFIED / FOR USE w/ GET/SET

    /** modified portion of the image  */
    @JvmField
    var isModified = false


    // ignore
    @JvmField
    var modifiedX1 = 0

    // ignore
    @JvmField
    var modifiedY1 = 0

    // ignore
    @JvmField
    var modifiedX2 = 0

    // ignore
    @JvmField
    var modifiedY2 = 0

    //////////////////////////////////////////////////////////////

    // MARKING IMAGE AS LOADED / FOR USE IN RENDERERS

    // ignore

    /** Loaded pixels flag  */
    @JvmField
    var isLoaded = false

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    // private fields

    private var fracU = 0
    private var ifU = 0
    private var fracV = 0
    private var ifV = 0
    private var u1 = 0
    private var u2 = 0
    private var v1 = 0
    private var v2 = 0
    private var sX = 0
    private var sY = 0
    private var iw = 0
    private var iw1 = 0
    private var ih1 = 0
    private var ul = 0
    private var ll = 0
    private var ur = 0
    private var lr = 0
    private var cUL = 0
    private var cLL = 0
    private var cUR = 0
    private var cLR = 0
    private var srcXOffset = 0
    private var srcYOffset = 0
    private var r = 0
    private var g = 0
    private var b = 0
    private var a = 0
    private var srcBuffer: IntArray? = null

    // internal kernel stuff for the gaussian blur filter
    private var blurRadius = 0
    private var blurKernelSize = 0
    private lateinit var blurKernel: IntArray
    private lateinit var blurMult: Array<IntArray>

    //////////////////////////////////////////////////////////////

    /**
     * Create an empty image object, set its format to RGB.
     * The pixel array is not allocated.
     */
    constructor() {
        format = PConstants.ARGB // default to ARGB images for release 0116
        //    cache = null;
    }

    /**
     * Create a new RGB (alpha ignored) image of a specific size.
     * All pixels are set to zero, meaning black, but since the
     * alpha is zero, it will be transparent.
     */
    constructor(width: Int, height: Int) {
        init(width, height, PConstants.RGB)
    }

    constructor(width: Int, height: Int, format: Int) {
        init(width, height, format)
    }


    /**
     * Function to be used by subclasses of PImage to init later than
     * at the constructor, or re-init later when things changes.
     * Used by Capture and Movie classes (and perhaps others),
     * because the width/height will not be known when super() is called.
     * (Leave this public so that other libraries can do the same.)
     */
    fun init(width: Int, height: Int, format: Int) {  // ignore
        this.width = width
        this.height = height
        pixels = IntArray(width * height)
        this.format = format
        //    this.cache = null;
        pixelWidth = width * pixelDensity
        pixelHeight = height * pixelDensity
        pixels = IntArray(pixelWidth * pixelHeight)
    }


    /**
     * Check the alpha on an image, using a really primitive loop.
     */
    fun checkAlpha() {
        if (pixels == null) return
        for (i in pixels!!.indices) {
            // since transparency is often at corners, hopefully this
            // will find a non-transparent pixel quickly and exit
            if (pixels!![i] and -0x1000000 != -0x1000000) {
                format = PConstants.ARGB
                break
            }
        }
    }


    //////////////////////////////////////////////////////////////


    /**
     * Construct a new PImage from an Android bitmap. The pixels[] array is not
     * initialized, nor is data copied to it, until loadPixels() is called.
     */
    constructor(nativeObject: Any) {
        val bitmap = nativeObject as Bitmap
        this.bitmap = bitmap
        width = bitmap.width
        height = bitmap.height
        pixels = null
        format = if (bitmap.hasAlpha()) PConstants.ARGB else PConstants.RGB
        pixelDensity = 1
        pixelWidth = width
        pixelHeight = height
    }


    /**
     * Returns the native Bitmap object for this PImage.
     */
    var native: Any?
        get() = bitmap
        set(nativeObject) {
            val bitmap = nativeObject as Bitmap?
            this.bitmap = bitmap
        }

    fun SetModified() {  // ignore
        isModified = true
    }

    fun SetModified(m: Boolean) {  // ignore
        isModified = m
    }


    /**
     * Call this when you want to mess with the pixels[] array.
     *
     *
     * For subclasses where the pixels[] buffer isn't set by default,
     * this should copy all data into the pixels[] array
     */
    open fun loadPixels() {  // ignore
        if (pixels == null || pixels!!.size != width * height) {
            pixels = IntArray(width * height)
        }
        if (bitmap != null) {
            if (isModified) {
                // The pixels array has been used to do color manipulations, so
                // the bitmap should be updated
                if (!bitmap!!.isMutable) {
                    // create a mutable version of this bitmap
                    bitmap = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
                }
                bitmap!!.setPixels(pixels, 0, width, modifiedX1, modifiedY1, modifiedX2 - modifiedX1, modifiedY2 - modifiedY1)
                isModified = false
            } else {
                // Get wherever it is in the bitmap right now, we assume is the most
                // up-to-date version of the image.
                bitmap!!.getPixels(pixels, 0, width, 0, 0, width, height)
            }
        }
        setLoaded()
    }

    /**
     * Call this when finished messing with the pixels[] array.
     *
     *
     * Mark all pixels as needing update.
     */
    open fun updatePixels() {  // ignore
        updatePixelsImpl(0, 0, width, height)
    }

    /**
     * Mark the pixels in this region as needing an update.
     * <P>
     * This is not currently used by any of the renderers, however the api
     * is structured this way in the hope of being able to use this to
     * speed things up in the future.
    </P> */
    open fun updatePixels(x: Int, y: Int, w: Int, h: Int) {  // ignore

//    if (imageMode == CORNER) {  // x2, y2 are w/h
//      x2 += x1;
//      y2 += y1;
//
//    } else if (imageMode == CENTER) {
//      x1 -= x2 / 2;
//      y1 -= y2 / 2;
//      x2 += x1;
//      y2 += y1;
//    }
        updatePixelsImpl(x, y, w, h)
    }

    protected fun updatePixelsImpl(x: Int, y: Int, w: Int, h: Int) {
        val x2 = x + w
        val y2 = y + h
        if (!isModified) {
            modifiedX1 = PApplet.max(0, x)
            //mx2 = PApplet.min(width - 1, x2);
            modifiedX2 = PApplet.min(width, x2)
            modifiedY1 = PApplet.max(0, y)
            //my2 = PApplet.min(height - 1, y2);
            modifiedY2 = PApplet.min(height, y2)
            isModified = true
        } else {
            if (x < modifiedX1) modifiedX1 = PApplet.max(0, x)
            //if (x > mx2) mx2 = PApplet.min(width - 1, x);
            if (x > modifiedX2) modifiedX2 = PApplet.min(width, x)
            if (y < modifiedY1) modifiedY1 = PApplet.max(0, y)
            //if (y > my2) my2 = y;
            if (y > modifiedY2) modifiedY2 = PApplet.min(height, y)
            if (x2 < modifiedX1) modifiedX1 = PApplet.max(0, x2)
            //if (x2 > mx2) mx2 = PApplet.min(width - 1, x2);
            if (x2 > modifiedX2) modifiedX2 = PApplet.min(width, x2)
            if (y2 < modifiedY1) modifiedY1 = PApplet.max(0, y2)
            //if (y2 > my2) my2 = PApplet.min(height - 1, y2);
            if (y2 > modifiedY2) modifiedY2 = PApplet.min(height, y2)
        }
    }


    //////////////////////////////////////////////////////////////

    // COPYING IMAGE DATA

    /**
     * Duplicate an image, returns new PImage object.
     * The pixels[] array for the new object will be unique
     * and recopied from the source image. This is implemented as an
     * override of Object.clone(). We recommend using get() instead,
     * because it prevents you from needing to catch the
     * CloneNotSupportedException, and from doing a cast from the result.
     */
    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Any {  // ignore
        return get()
    }

    /**
     * Resize this image to a new width and height.
     * Use 0 for wide or high to make that dimension scale proportionally.
     */
    open fun resize(w: Int, h: Int) {  // ignore
        var w = w
        var h = h
        if (bitmap == null) {
            return  // Cannot resize an image not backed by a bitmap
        }
        require(!(w <= 0 && h <= 0)) { "width or height must be > 0 for resize" }
        if (w == 0) {  // Use height to determine relative size
            val diff = h.toFloat() / height.toFloat()
            w = (width * diff).toInt()
        } else if (h == 0) {  // Use the width to determine relative size
            val diff = w.toFloat() / width.toFloat()
            h = (height * diff).toInt()
        }
        bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true)

        if (pixels != null) {
            // Resize pixels array, if in use.
            pixels = IntArray(w * h)
            bitmap?.getPixels(pixels, 0, w, 0, 0, w, h)
        }
        width = w
        height = h
        pixelWidth = w * pixelDensity
        pixelHeight = h * pixelDensity
    }

    fun setLoaded() {  // ignore
        isLoaded = true
    }


    //////////////////////////////////////////////////////////////

    // GET/SET PIXELS

    /**
     * Returns an ARGB "color" type (a packed 32 bit int with the color.
     * If the coordinate is outside the image, zero is returned
     * (black, but completely transparent).
     * <P>
     * If the image is in RGB format (i.e. on a PVideo object),
     * the value will get its high bits set, just to avoid cases where
     * they haven't been set already.
    </P> * <P>
     * If the image is in ALPHA format, this returns a white with its
     * alpha value set.
    </P> * <P>
     * This function is included primarily for beginners. It is quite
     * slow because it has to check to see if the x, y that was provided
     * is inside the bounds, and then has to check to see what image
     * type it is. If you want things to be more efficient, access the
     * pixels[] array directly.
    </P> */
    open operator fun get(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        if (pixels == null) {
            return bitmap!!.getPixel(x, y)
        } else {
            // If the pixels array exists, it's fairly safe to assume that it's
            // the most up to date, and that it's faster for access.
            when (format) {
                PConstants.RGB -> return pixels!![y * width + x] or -0x1000000
                PConstants.ARGB -> return pixels!![y * width + x]
                PConstants.ALPHA -> return pixels!![y * width + x] shl 24 or 0xffffff
            }
        }
        return 0
    }


    /**
     * Grab a subsection of a PImage, and copy it into a fresh PImage.
     * As of release 0149, no longer honors imageMode() for the coordinates.
     */
    /**
     * @param w width of pixel rectangle to get
     * @param h height of pixel rectangle to get
     */
    operator fun get(x: Int, y: Int, w: Int, h: Int): PImage {
        var x = x
        var y = y
        var w = w
        var h = h
        var targetX = 0
        var targetY = 0
        val targetWidth = w
        val targetHeight = h
        var cropped = false
        if (x < 0) {
            w += x // x is negative, removes the left edge from the width
            targetX = -x
            cropped = true
            x = 0
        }
        if (y < 0) {
            h += y // y is negative, clip the number of rows
            targetY = -y
            cropped = true
            y = 0
        }
        if (x + w > width) {
            w = width - x
            cropped = true
        }
        if (y + h > height) {
            h = height - y
            cropped = true
        }
        if (w < 0) {
            w = 0
        }
        if (h < 0) {
            h = 0
        }
        var targetFormat = format
        if (cropped && format == PConstants.RGB) {
            targetFormat = PConstants.ARGB
        }
        val target = PImage(targetWidth, targetHeight, targetFormat)
        target.parent = parent // parent may be null so can't use createImage()
        if (w > 0 && h > 0) {
            getImpl(x, y, w, h, target, targetX, targetY)
            val nat = Bitmap.createBitmap(target.pixels, targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            target.native = nat
        }
        return target
    }


    /**
     * Internal function to actually handle getting a block of pixels that
     * has already been properly cropped to a valid region. That is, x/y/w/h
     * are guaranteed to be inside the image space, so the implementation can
     * use the fastest possible pixel copying method.
     */
    protected open fun getImpl(sourceX: Int, sourceY: Int,
                               sourceWidth: Int, sourceHeight: Int,
                               target: PImage, targetX: Int, targetY: Int) {
        if (bitmap != null) {
            bitmap!!.getPixels(target.pixels,
                    targetY * target.width + targetX, target.width,
                    sourceX, sourceY, sourceWidth, sourceHeight)
        } else if (pixels != null) {
            var sourceIndex = sourceY * width + sourceX
            var targetIndex = targetY * target.width + targetX
            for (row in 0 until sourceHeight) {
                System.arraycopy(pixels, sourceIndex, target.pixels, targetIndex, sourceWidth)
                sourceIndex += width
                targetIndex += target.width
            }
        }
    }


    /**
     * Returns a copy of this PImage. Equivalent to get(0, 0, width, height).
     */
    open fun get(): PImage {
        // Formerly this used clone(), which caused memory problems.
        // http://code.google.com/p/processing/issues/detail?id=42
        return get(0, 0, width, height)
    }

    fun copy(): PImage {
        return get(0, 0, pixelWidth, pixelHeight)
    }


    /**
     * Set a single pixel to the specified color.
     */
    open operator fun set(x: Int, y: Int, c: Int) {
        if (pixels == null) {
            bitmap!!.setPixel(x, y, c)
        } else {
            if (x < 0 || y < 0 || x >= width || y >= height) return
            pixels!![y * width + x] = c
            updatePixelsImpl(x, y, 1, 1) // slow?
        }
    }


    /**
     * Efficient method of drawing an image's pixels directly to this surface.
     * No variations are employed, meaning that any scale, tint, or imageMode
     * settings will be ignored.
     */
    open operator fun set(x: Int, y: Int, img: PImage) {
        var x = x
        var y = y
        require(img.format != PConstants.ALPHA) {
            // set() doesn't really make sense for an ALPHA image, since it
            // directly replaces pixels and does no blending.
            "set() not available for ALPHA images"
        }
        var sx = 0
        var sy = 0
        var sw = img.width
        var sh = img.height
        if (x < 0) {  // off left edge
            sx -= x
            sw += x
            x = 0
        }
        if (y < 0) {  // off top edge
            sy -= y
            sh += y
            y = 0
        }
        if (x + sw > width) {  // off right edge
            sw = width - x
        }
        if (y + sh > height) {  // off bottom edge
            sh = height - y
        }

        // this could be nonexistent
        if (sw <= 0 || sh <= 0) return
        setImpl(img, sx, sy, sw, sh, x, y)
    }


    /**
     * Internal function to actually handle setting a block of pixels that
     * has already been properly cropped from the image to a valid region.
     */
    protected open fun setImpl(sourceImage: PImage,
                               sourceX: Int, sourceY: Int,
                               sourceWidth: Int, sourceHeight: Int,
                               targetX: Int, targetY: Int) {
        if (sourceImage.pixels == null) {
            sourceImage.loadPixels()
        }

        // if this.pixels[] is null, copying directly into this.bitmap
        if (pixels == null) {
            // if this.pixels[] is null, this.bitmap cannot be null
            // make sure the bitmap is writable
            if (!bitmap!!.isMutable) {
                // create a mutable version of this bitmap
                bitmap = bitmap!!.copy(Bitmap.Config.ARGB_8888, true)
            }

            // copy from src.pixels to this.bitmap
            val offset = sourceY * sourceImage.width + sourceX
            bitmap!!.setPixels(sourceImage.pixels,
                    offset, sourceImage.width,
                    targetX, targetY, sourceWidth, sourceHeight)
        } else {  // pixels != null
            // copy into this.pixels[] and mark as modified
            var srcOffset = sourceY * sourceImage.width + sourceX
            var dstOffset = targetY * width + targetX
            for (y in sourceY until sourceY + sourceHeight) {
                System.arraycopy(sourceImage.pixels, srcOffset, pixels, dstOffset, sourceWidth)
                srcOffset += sourceImage.width
                dstOffset += width
            }
            updatePixelsImpl(targetX, targetY, sourceWidth, sourceHeight)
        }
    }

    //////////////////////////////////////////////////////////////

    // ALPHA CHANNEL

    /**
     * Set alpha channel for an image. Black colors in the source
     * image will make the destination image completely transparent,
     * and white will make things fully opaque. Gray values will
     * be in-between steps.
     * <P>
     * Strictly speaking the "blue" value from the source image is
     * used as the alpha color. For a fully grayscale image, this
     * is correct, but for a color image it's not 100% accurate.
     * For a more accurate conversion, first use filter(GRAY)
     * which will make the image into a "correct" grayscake by
     * performing a proper luminance-based conversion.
    </P> */
    open fun mask(alpha: IntArray?) {
        loadPixels()
        // don't execute if mask image is different size
        if (alpha!!.size != pixels!!.size) {
            throw RuntimeException("The PImage used with mask() must be " +
                    "the same size as the applet.")
        }
        for (i in pixels!!.indices) {
            pixels!![i] = alpha[i] and 0xff shl 24 or (pixels!![i] and 0xffffff)
        }
        format = PConstants.ARGB
        updatePixels()
    }


    /**
     * Set alpha channel for an image using another image as the source.
     */
    open fun mask(alpha: PImage) {
        if (alpha.pixels == null) {
            // if pixels haven't been loaded by the user, then only load them
            // temporarily to save memory when finished.
            alpha.loadPixels()
            mask(alpha.pixels)
            alpha.pixels = null
        } else {
            mask(alpha.pixels)
        }
    }


    //////////////////////////////////////////////////////////////

    // IMAGE FILTERS

    /**
     * Method to apply a variety of basic filters to this image.
     * <P>
    </P> * <UL>
     * <LI>filter(BLUR) provides a basic blur.
    </LI> * <LI>filter(GRAY) converts the image to grayscale based on luminance.
    </LI> * <LI>filter(INVERT) will invert the color components in the image.
    </LI> * <LI>filter(OPAQUE) set all the high bits in the image to opaque
    </LI> * <LI>filter(THRESHOLD) converts the image to black and white.
    </LI> * <LI>filter(DILATE) grow white/light areas
    </LI> * <LI>filter(ERODE) shrink white/light areas
    </LI></UL> *
     * Luminance conversion code contributed by
     * <A HREF="http://www.toxi.co.uk">toxi</A>
     * <P></P>
     * Gaussian blur code contributed by
     * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
     */
    open fun filter(kind: Int) {
        loadPixels()
        when (kind) {
            PConstants.BLUR ->         // TODO write basic low-pass filter blur here
                // what does photoshop do on the edges with this guy?
                // better yet.. why bother? just use gaussian with radius 1
                filter(PConstants.BLUR, 1f)
            PConstants.GRAY -> if (format == PConstants.ALPHA) {
                // for an alpha image, convert it to an opaque grayscale
                var i = 0
                while (i < pixels!!.size) {
                    val col = 255 - pixels!![i]
                    pixels!![i] = -0x1000000 or (col shl 16) or (col shl 8) or col
                    i++
                }
                format = PConstants.RGB
            } else {
                // Converts RGB image data into grayscale using
                // weighted RGB components, and keeps alpha channel intact.
                // [toxi 040115]
                var i = 0
                while (i < pixels!!.size) {
                    val col = pixels!![i]
                    // luminance = 0.3*red + 0.59*green + 0.11*blue
                    // 0.30 * 256 =  77
                    // 0.59 * 256 = 151
                    // 0.11 * 256 =  28
                    val lum = 77 * (col shr 16 and 0xff) + 151 * (col shr 8 and 0xff) + 28 * (col and 0xff) shr 8
                    pixels!![i] = col and ALPHA_MASK or (lum shl 16) or (lum shl 8) or lum
                    i++
                }
            }
            PConstants.INVERT -> {
                var i = 0
                while (i < pixels!!.size) {

                    //pixels[i] = 0xff000000 |
                    pixels!![i] = pixels!![i] xor 0xffffff
                    i++
                }
            }
            PConstants.POSTERIZE -> throw RuntimeException("Use filter(POSTERIZE, int levels) " +
                    "instead of filter(POSTERIZE)")
            PConstants.RGB -> {
                var i = 0
                while (i < pixels!!.size) {
                    pixels!![i] = pixels!![i] or -0x1000000
                    i++
                }
                format = PConstants.RGB
            }
            PConstants.THRESHOLD -> filter(PConstants.THRESHOLD, 0.5f)
            PConstants.ERODE -> dilate(true)
            PConstants.DILATE -> dilate(false)
        }
        updatePixels() // mark as modified
    }


    /**
     * Method to apply a variety of basic filters to this image.
     * These filters all take a parameter.
     * <P>
    </P> * <UL>
     * <LI>filter(BLUR, int radius) performs a gaussian blur of the
     * specified radius.
    </LI> * <LI>filter(POSTERIZE, int levels) will posterize the image to
     * between 2 and 255 levels.
    </LI> * <LI>filter(THRESHOLD, float center) allows you to set the
     * center point for the threshold. It takes a value from 0 to 1.0.
    </LI></UL> *
     * Gaussian blur code contributed by
     * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
     * and later updated by toxi for better speed.
     */
    open fun filter(kind: Int, param: Float) {
        loadPixels()
        when (kind) {
            PConstants.BLUR -> if (format == PConstants.ALPHA) blurAlpha(param) else if (format == PConstants.ARGB) blurARGB(param) else blurRGB(param)
            PConstants.GRAY -> throw RuntimeException("Use filter(GRAY) instead of " +
                    "filter(GRAY, param)")
            PConstants.INVERT -> throw RuntimeException("Use filter(INVERT) instead of " +
                    "filter(INVERT, param)")
            PConstants.OPAQUE -> throw RuntimeException("Use filter(OPAQUE) instead of " +
                    "filter(OPAQUE, param)")
            PConstants.POSTERIZE -> {
                val levels = param.toInt()
                if (levels < 2 || levels > 255) {
                    throw RuntimeException("Levels must be between 2 and 255 for " +
                            "filter(POSTERIZE, levels)")
                }
                val levels1 = levels - 1
                var i = 0
                while (i < pixels!!.size) {
                    var rlevel = pixels!![i] shr 16 and 0xff
                    var glevel = pixels!![i] shr 8 and 0xff
                    var blevel = pixels!![i] and 0xff
                    rlevel = (rlevel * levels shr 8) * 255 / levels1
                    glevel = (glevel * levels shr 8) * 255 / levels1
                    blevel = (blevel * levels shr 8) * 255 / levels1
                    pixels!![i] = -0x1000000 and pixels!![i] or
                            (rlevel shl 16) or
                            (glevel shl 8) or
                            blevel
                    i++
                }
            }
            PConstants.THRESHOLD -> {
                val thresh = (param * 255).toInt()
                var i = 0
                while (i < pixels!!.size) {
                    val max = Math.max(pixels!![i] and RED_MASK shr 16,
                            Math.max(pixels!![i] and GREEN_MASK shr 8,
                                    pixels!![i] and BLUE_MASK))
                    pixels!![i] = pixels!![i] and ALPHA_MASK or
                            if (max < thresh) 0x000000 else 0xffffff
                    i++
                }
            }
            PConstants.ERODE -> throw RuntimeException("Use filter(ERODE) instead of " +
                    "filter(ERODE, param)")
            PConstants.DILATE -> throw RuntimeException("Use filter(DILATE) instead of " +
                    "filter(DILATE, param)")
        }
        updatePixels() // mark as modified
    }


    /**
     * Optimized code for building the blur kernel.
     * further optimized blur code (approx. 15% for radius=20)
     * bigger speed gains for larger radii (~30%)
     * added support for various image types (ALPHA, RGB, ARGB)
     * [toxi 050728]
     */
    protected fun buildBlurKernel(r: Float) {
        var radius = (r * 3.5f).toInt()
        radius = if (radius < 1) 1 else if (radius < 248) radius else 248
        if (blurRadius != radius) {
            blurRadius = radius
            blurKernelSize = 1 + blurRadius shl 1
            blurKernel = IntArray(blurKernelSize)
            blurMult = Array(blurKernelSize) { IntArray(256) }
            val bk: Int
            var bki: Int
            var bm: IntArray
            var bmi: IntArray
            var i = 1
            var radiusi = radius - 1
            while (i < radius) {
                bki = radiusi * radiusi
                blurKernel[radiusi] = bki
                blurKernel[radius + i] = blurKernel[radiusi]
                bm = blurMult[radius + i]
                bmi = blurMult[radiusi--]
                for (j in 0..255) {
                    bmi[j] = bki * j
                    bm[j] = bmi[j]
                }
                i++
            }
            blurKernel[radius] = radius * radius
            bk = blurKernel[radius]
            bm = blurMult[radius]
            for (j in 0..255) bm[j] = bk * j
        }
    }


    protected fun blurAlpha(r: Float) {
        var sum: Int
        var cb: Int
        var read: Int
        var ri: Int
        var ym: Int
        var ymi: Int
        var bk0: Int
        val b2 = IntArray(pixels!!.size)
        var yi = 0
        buildBlurKernel(r)
        for (y in 0 until height) {
            for (x in 0 until width) {
                //cb = cg = cr = sum = 0;
                sum = 0
                cb = sum
                read = x - blurRadius
                if (read < 0) {
                    bk0 = -read
                    read = 0
                } else {
                    if (read >= width) break
                    bk0 = 0
                }
                for (i in bk0 until blurKernelSize) {
                    if (read >= width) break
                    val c = pixels!![read + yi]
                    val bm = blurMult[i]
                    cb += bm[c and BLUE_MASK]
                    sum += blurKernel[i]
                    read++
                }
                ri = yi + x
                b2[ri] = cb / sum
            }
            yi += width
        }
        yi = 0
        ym = -blurRadius
        ymi = ym * width
        for (y in 0 until height) {
            for (x in 0 until width) {
                //cb = cg = cr = sum = 0;
                sum = 0
                cb = sum
                if (ym < 0) {
                    ri = -ym
                    bk0 = ri
                    read = x
                } else {
                    if (ym >= height) break
                    bk0 = 0
                    ri = ym
                    read = x + ymi
                }
                for (i in bk0 until blurKernelSize) {
                    if (ri >= height) break
                    val bm = blurMult[i]
                    cb += bm[b2[read]]
                    sum += blurKernel[i]
                    ri++
                    read += width
                }
                pixels!![x + yi] = cb / sum
            }
            yi += width
            ymi += width
            ym++
        }
    }


    protected fun blurRGB(r: Float) {
        var sum: Int
        var cr: Int
        var cg: Int
        var cb: Int //, k;
        var   /*pixel,*/read: Int
        var ri: Int
        /*roff,*/
        var ym: Int
        var ymi: Int
        /*riw,*/
        var bk0: Int
        val r2 = IntArray(pixels!!.size)
        val g2 = IntArray(pixels!!.size)
        val b2 = IntArray(pixels!!.size)
        var yi = 0
        buildBlurKernel(r)
        for (y in 0 until height) {
            for (x in 0 until width) {
                sum = 0
                cr = sum
                cg = cr
                cb = cg
                read = x - blurRadius
                if (read < 0) {
                    bk0 = -read
                    read = 0
                } else {
                    if (read >= width) break
                    bk0 = 0
                }
                for (i in bk0 until blurKernelSize) {
                    if (read >= width) break
                    val c = pixels!![read + yi]
                    val bm = blurMult[i]
                    cr += bm[c and RED_MASK shr 16]
                    cg += bm[c and GREEN_MASK shr 8]
                    cb += bm[c and BLUE_MASK]
                    sum += blurKernel[i]
                    read++
                }
                ri = yi + x
                r2[ri] = cr / sum
                g2[ri] = cg / sum
                b2[ri] = cb / sum
            }
            yi += width
        }
        yi = 0
        ym = -blurRadius
        ymi = ym * width
        for (y in 0 until height) {
            for (x in 0 until width) {
                sum = 0
                cr = sum
                cg = cr
                cb = cg
                if (ym < 0) {
                    ri = -ym
                    bk0 = ri
                    read = x
                } else {
                    if (ym >= height) break
                    bk0 = 0
                    ri = ym
                    read = x + ymi
                }
                for (i in bk0 until blurKernelSize) {
                    if (ri >= height) break
                    val bm = blurMult[i]
                    cr += bm[r2[read]]
                    cg += bm[g2[read]]
                    cb += bm[b2[read]]
                    sum += blurKernel[i]
                    ri++
                    read += width
                }
                pixels!![x + yi] = -0x1000000 or (cr / sum shl 16) or (cg / sum shl 8) or cb / sum
            }
            yi += width
            ymi += width
            ym++
        }
    }


    protected fun blurARGB(r: Float) {
        var sum: Int
        var cr: Int
        var cg: Int
        var cb: Int
        var ca: Int
        var   /*pixel,*/read: Int
        var ri: Int
        /*roff,*/
        var ym: Int
        var ymi: Int
        /*riw,*/
        var bk0: Int
        val wh = pixels!!.size
        val r2 = IntArray(wh)
        val g2 = IntArray(wh)
        val b2 = IntArray(wh)
        val a2 = IntArray(wh)
        var yi = 0
        buildBlurKernel(r)
        for (y in 0 until height) {
            for (x in 0 until width) {
                sum = 0
                ca = sum
                cr = ca
                cg = cr
                cb = cg
                read = x - blurRadius
                if (read < 0) {
                    bk0 = -read
                    read = 0
                } else {
                    if (read >= width) break
                    bk0 = 0
                }
                for (i in bk0 until blurKernelSize) {
                    if (read >= width) break
                    val c = pixels!![read + yi]
                    val bm = blurMult[i]
                    ca += bm[c and ALPHA_MASK ushr 24]
                    cr += bm[c and RED_MASK shr 16]
                    cg += bm[c and GREEN_MASK shr 8]
                    cb += bm[c and BLUE_MASK]
                    sum += blurKernel[i]
                    read++
                }
                ri = yi + x
                a2[ri] = ca / sum
                r2[ri] = cr / sum
                g2[ri] = cg / sum
                b2[ri] = cb / sum
            }
            yi += width
        }
        yi = 0
        ym = -blurRadius
        ymi = ym * width
        for (y in 0 until height) {
            for (x in 0 until width) {
                sum = 0
                ca = sum
                cr = ca
                cg = cr
                cb = cg
                if (ym < 0) {
                    ri = -ym
                    bk0 = ri
                    read = x
                } else {
                    if (ym >= height) break
                    bk0 = 0
                    ri = ym
                    read = x + ymi
                }
                for (i in bk0 until blurKernelSize) {
                    if (ri >= height) break
                    val bm = blurMult[i]
                    ca += bm[a2[read]]
                    cr += bm[r2[read]]
                    cg += bm[g2[read]]
                    cb += bm[b2[read]]
                    sum += blurKernel[i]
                    ri++
                    read += width
                }
                pixels!![x + yi] = ca / sum shl 24 or (cr / sum shl 16) or (cg / sum shl 8) or cb / sum
            }
            yi += width
            ymi += width
            ym++
        }
    }



    /**
     * Generic dilate/erode filter using luminance values
     * as decision factor. [toxi 050728]
     */
    protected fun dilate(isInverted: Boolean) {
        var currIdx = 0
        val maxIdx = pixels!!.size
        val out = IntArray(maxIdx)
        if (!isInverted) {
            // erosion (grow light areas)
            while (currIdx < maxIdx) {
                val currRowIdx = currIdx
                val maxRowIdx = currIdx + width
                while (currIdx < maxRowIdx) {
                    var colOrig: Int
                    var colOut: Int
                    colOut = pixels!![currIdx]
                    colOrig = colOut
                    var idxLeft = currIdx - 1
                    var idxRight = currIdx + 1
                    var idxUp = currIdx - width
                    var idxDown = currIdx + width
                    if (idxLeft < currRowIdx) idxLeft = currIdx
                    if (idxRight >= maxRowIdx) idxRight = currIdx
                    if (idxUp < 0) idxUp = 0
                    if (idxDown >= maxIdx) idxDown = currIdx
                    val colUp = pixels!![idxUp]
                    val colLeft = pixels!![idxLeft]
                    val colDown = pixels!![idxDown]
                    val colRight = pixels!![idxRight]

                    // compute luminance
                    var currLum = 77 * (colOrig shr 16 and 0xff) + 151 * (colOrig shr 8 and 0xff) + 28 * (colOrig and 0xff)
                    val lumLeft = 77 * (colLeft shr 16 and 0xff) + 151 * (colLeft shr 8 and 0xff) + 28 * (colLeft and 0xff)
                    val lumRight = 77 * (colRight shr 16 and 0xff) + 151 * (colRight shr 8 and 0xff) + 28 * (colRight and 0xff)
                    val lumUp = 77 * (colUp shr 16 and 0xff) + 151 * (colUp shr 8 and 0xff) + 28 * (colUp and 0xff)
                    val lumDown = 77 * (colDown shr 16 and 0xff) + 151 * (colDown shr 8 and 0xff) + 28 * (colDown and 0xff)
                    if (lumLeft > currLum) {
                        colOut = colLeft
                        currLum = lumLeft
                    }
                    if (lumRight > currLum) {
                        colOut = colRight
                        currLum = lumRight
                    }
                    if (lumUp > currLum) {
                        colOut = colUp
                        currLum = lumUp
                    }
                    if (lumDown > currLum) {
                        colOut = colDown
                        currLum = lumDown
                    }
                    out[currIdx++] = colOut
                }
            }
        } else {
            // dilate (grow dark areas)
            while (currIdx < maxIdx) {
                val currRowIdx = currIdx
                val maxRowIdx = currIdx + width
                while (currIdx < maxRowIdx) {
                    var colOrig: Int
                    var colOut: Int
                    colOut = pixels!![currIdx]
                    colOrig = colOut
                    var idxLeft = currIdx - 1
                    var idxRight = currIdx + 1
                    var idxUp = currIdx - width
                    var idxDown = currIdx + width
                    if (idxLeft < currRowIdx) idxLeft = currIdx
                    if (idxRight >= maxRowIdx) idxRight = currIdx
                    if (idxUp < 0) idxUp = 0
                    if (idxDown >= maxIdx) idxDown = currIdx
                    val colUp = pixels!![idxUp]
                    val colLeft = pixels!![idxLeft]
                    val colDown = pixels!![idxDown]
                    val colRight = pixels!![idxRight]

                    // compute luminance
                    var currLum = 77 * (colOrig shr 16 and 0xff) + 151 * (colOrig shr 8 and 0xff) + 28 * (colOrig and 0xff)
                    val lumLeft = 77 * (colLeft shr 16 and 0xff) + 151 * (colLeft shr 8 and 0xff) + 28 * (colLeft and 0xff)
                    val lumRight = 77 * (colRight shr 16 and 0xff) + 151 * (colRight shr 8 and 0xff) + 28 * (colRight and 0xff)
                    val lumUp = 77 * (colUp shr 16 and 0xff) + 151 * (colUp shr 8 and 0xff) + 28 * (colUp and 0xff)
                    val lumDown = 77 * (colDown shr 16 and 0xff) + 151 * (colDown shr 8 and 0xff) + 28 * (colDown and 0xff)
                    if (lumLeft < currLum) {
                        colOut = colLeft
                        currLum = lumLeft
                    }
                    if (lumRight < currLum) {
                        colOut = colRight
                        currLum = lumRight
                    }
                    if (lumUp < currLum) {
                        colOut = colUp
                        currLum = lumUp
                    }
                    if (lumDown < currLum) {
                        colOut = colDown
                        currLum = lumDown
                    }
                    out[currIdx++] = colOut
                }
            }
        }
        System.arraycopy(out, 0, pixels, 0, maxIdx)
    }


    //////////////////////////////////////////////////////////////

    // COPY

    /**
     * Copy things from one area of this image
     * to another area in the same image.
     */
    open fun copy(sx: Int, sy: Int, sw: Int, sh: Int,
                  dx: Int, dy: Int, dw: Int, dh: Int) {
        blend(this, sx, sy, sw, sh, dx, dy, dw, dh, PConstants.REPLACE)
    }


    /**
     * Copies area of one image into another PImage object.
     */
    open fun copy(src: PImage,
                  sx: Int, sy: Int, sw: Int, sh: Int,
                  dx: Int, dy: Int, dw: Int, dh: Int) {
        blend(src, sx, sy, sw, sh, dx, dy, dw, dh, PConstants.REPLACE)
    }


    /**
     * Blends one area of this image to another area.
     * @see processing.core.PImage.blendColor
     */
    fun blend(sx: Int, sy: Int, sw: Int, sh: Int,
              dx: Int, dy: Int, dw: Int, dh: Int, mode: Int) {
        blend(this, sx, sy, sw, sh, dx, dy, dw, dh, mode)
    }

    /**
     * Copies area of one image into another PImage object.
     * @see processing.core.PImage.blendColor
     */
    fun blend(src: PImage,
              sx: Int, sy: Int, sw: Int, sh: Int,
              dx: Int, dy: Int, dw: Int, dh: Int, mode: Int) {
        val sx2 = sx + sw
        val sy2 = sy + sh
        val dx2 = dx + dw
        val dy2 = dy + dh
        loadPixels()
        if (src === this) {
            if (intersect(sx, sy, sx2, sy2, dx, dy, dx2, dy2)) {
                blit_resize(get(sx, sy, sw, sh),
                        0, 0, sw, sh,
                        pixels, pixelWidth, pixelHeight, dx, dy, dx2, dy2, mode)
            } else {
                // same as below, except skip the loadPixels() because it'd be redundant
                blit_resize(src, sx, sy, sx2, sy2,
                        pixels, pixelWidth, pixelHeight, dx, dy, dx2, dy2, mode)
            }
        } else {
            src.loadPixels()
            blit_resize(src, sx, sy, sx2, sy2,
                    pixels, pixelWidth, pixelHeight, dx, dy, dx2, dy2, mode)
            //src.updatePixels();
        }
        updatePixels()
    }


    /**
     * Check to see if two rectangles intersect one another
     */
    private fun intersect(sx1: Int, sy1: Int, sx2: Int, sy2: Int,
                          dx1: Int, dy1: Int, dx2: Int, dy2: Int): Boolean {
        val sw = sx2 - sx1 + 1
        val sh = sy2 - sy1 + 1
        var dw = dx2 - dx1 + 1
        var dh = dy2 - dy1 + 1
        if (dx1 < sx1) {
            dw += dx1 - sx1
            if (dw > sw) {
                dw = sw
            }
        } else {
            val w = sw + sx1 - dx1
            if (dw > w) {
                dw = w
            }
        }
        if (dy1 < sy1) {
            dh += dy1 - sy1
            if (dh > sh) {
                dh = sh
            }
        } else {
            val h = sh + sy1 - dy1
            if (dh > h) {
                dh = h
            }
        }
        return !(dw <= 0 || dh <= 0)
    }


    //////////////////////////////////////////////////////////////


    /**
     * Internal blitter/resizer/copier from toxi.
     * Uses bilinear filtering if smooth() has been enabled
     * 'mode' determines the blending mode used in the process.
     */
    private fun blit_resize(img: PImage,
                            srcX1: Int, srcY1: Int, srcX2: Int, srcY2: Int,
                            destPixels: IntArray?, screenW: Int, screenH: Int,
                            destX1: Int, destY1: Int, destX2: Int, destY2: Int,
                            mode: Int) {
        var srcX1 = srcX1
        var srcY1 = srcY1
        var srcX2 = srcX2
        var srcY2 = srcY2
        var destX1 = destX1
        var destY1 = destY1
        if (srcX1 < 0) srcX1 = 0
        if (srcY1 < 0) srcY1 = 0
        if (srcX2 > img.pixelWidth) srcX2 = img.pixelWidth
        if (srcY2 > img.pixelHeight) srcY2 = img.pixelHeight
        var srcW = srcX2 - srcX1
        var srcH = srcY2 - srcY1
        var destW = destX2 - destX1
        var destH = destY2 - destY1
        val smooth = true // may as well go with the smoothing these days
        if (!smooth) {
            srcW++
            srcH++
        }
        if (destW <= 0 || destH <= 0 || srcW <= 0 || srcH <= 0 || destX1 >= screenW || destY1 >= screenH || srcX1 >= img.pixelWidth || srcY1 >= img.pixelHeight) {
            return
        }
        val dx = (srcW / destW.toFloat() * PRECISIONF).toInt()
        val dy = (srcH / destH.toFloat() * PRECISIONF).toInt()
        srcXOffset = if (destX1 < 0) -destX1 * dx else srcX1 * PRECISIONF
        srcYOffset = if (destY1 < 0) -destY1 * dy else srcY1 * PRECISIONF
        if (destX1 < 0) {
            destW += destX1
            destX1 = 0
        }
        if (destY1 < 0) {
            destH += destY1
            destY1 = 0
        }
        destW = min(destW, screenW - destX1)
        destH = min(destH, screenH - destY1)
        var destOffset = destY1 * screenW + destX1
        srcBuffer = img.pixels
        if (smooth) {
            // use bilinear filtering
            iw = img.pixelWidth
            iw1 = img.pixelWidth - 1
            ih1 = img.pixelHeight - 1
            when (mode) {
                PConstants.BLEND -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {

                            // davbol  - renamed old blend_multiply to blend_blend
                            destPixels!![destOffset + x] = blend_blend(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.ADD -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_add_pin(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.SUBTRACT -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_sub_pin(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.LIGHTEST -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_lightest(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.DARKEST -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_darkest(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.REPLACE -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = filter_bilinear()
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.DIFFERENCE -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_difference(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.EXCLUSION -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_exclusion(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.MULTIPLY -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_multiply(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.SCREEN -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_screen(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.OVERLAY -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_overlay(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.HARD_LIGHT -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_hard_light(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.SOFT_LIGHT -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_soft_light(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.DODGE -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_dodge(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.BURN -> {
                    var y = 0
                    while (y < destH) {
                        filter_new_scanline()
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_burn(destPixels[destOffset + x], filter_bilinear())
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
            }
        } else {
            // nearest neighbour scaling (++fast!)
            when (mode) {
                PConstants.BLEND -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {

                            // davbol - renamed old blend_multiply to blend_blend
                            destPixels!![destOffset + x] = blend_blend(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.ADD -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_add_pin(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.SUBTRACT -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_sub_pin(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.LIGHTEST -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_lightest(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.DARKEST -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_darkest(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.REPLACE -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = srcBuffer!![sY + (sX shr PRECISIONB)]
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.DIFFERENCE -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_difference(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.EXCLUSION -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_exclusion(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.MULTIPLY -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_multiply(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.SCREEN -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_screen(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.OVERLAY -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_overlay(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.HARD_LIGHT -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_hard_light(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.SOFT_LIGHT -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_soft_light(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.DODGE -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_dodge(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
                PConstants.BURN -> {
                    var y = 0
                    while (y < destH) {
                        sX = srcXOffset
                        sY = (srcYOffset shr PRECISIONB) * img.pixelWidth
                        var x = 0
                        while (x < destW) {
                            destPixels!![destOffset + x] = blend_burn(destPixels[destOffset + x],
                                    srcBuffer!![sY + (sX shr PRECISIONB)])
                            sX += dx
                            x++
                        }
                        destOffset += screenW
                        srcYOffset += dy
                        y++
                    }
                }
            }
        }
    }


    private fun filter_new_scanline() {
        sX = srcXOffset
        fracV = srcYOffset and PREC_MAXVAL
        ifV = PREC_MAXVAL - fracV + 1
        v1 = (srcYOffset shr PRECISIONB) * iw
        v2 = min((srcYOffset shr PRECISIONB) + 1, ih1) * iw
    }


    private fun filter_bilinear(): Int {
        fracU = sX and PREC_MAXVAL
        ifU = PREC_MAXVAL - fracU + 1
        ul = ifU * ifV shr PRECISIONB
        ll = ifU - ul
        ur = ifV - ul
        lr = PREC_MAXVAL + 1 - ul - ll - ur
        u1 = sX shr PRECISIONB
        u2 = min(u1 + 1, iw1)

        // get color values of the 4 neighbouring texels
        cUL = srcBuffer!![v1 + u1]
        cUR = srcBuffer!![v1 + u2]
        cLL = srcBuffer!![v2 + u1]
        cLR = srcBuffer!![v2 + u2]
        r = (ul * (cUL and RED_MASK shr 16) + ll * (cLL and RED_MASK shr 16) + ur * (cUR and RED_MASK shr 16) + lr * (cLR and RED_MASK shr 16)
                shl PREC_RED_SHIFT) and RED_MASK
        g = (ul * (cUL and GREEN_MASK) + ll * (cLL and GREEN_MASK) + ur * (cUR and GREEN_MASK) + lr * (cLR and GREEN_MASK)
                ushr PRECISIONB) and GREEN_MASK
        b = (ul * (cUL and BLUE_MASK) + ll * (cLL and BLUE_MASK) + ur * (cUR and BLUE_MASK) + lr * (cLR and BLUE_MASK)
                ushr PRECISIONB)
        a = (ul * (cUL and ALPHA_MASK ushr 24) + ll * (cLL and ALPHA_MASK ushr 24) + ur * (cUR and ALPHA_MASK ushr 24) + lr * (cLR and ALPHA_MASK ushr 24)
                shl PREC_ALPHA_SHIFT) and ALPHA_MASK
        return a or r or g or b
    }


    protected fun saveTIFF(output: OutputStream): Boolean {
        // shutting off the warning, people can figure this out themselves
        /*
    if (format != RGB) {
      System.err.println("Warning: only RGB information is saved with " +
                         ".tif files. Use .tga or .png for ARGB images and others.");
    }
    */
        try {
            val tiff = ByteArray(768)
            System.arraycopy(TIFF_HEADER, 0, tiff, 0, TIFF_HEADER.size)
            tiff[30] = (width shr 8 and 0xff).toByte()
            tiff[31] = (width and 0xff).toByte()
            tiff[102] = (height shr 8 and 0xff).toByte()
            tiff[42] = tiff[102]
            tiff[103] = (height and 0xff).toByte()
            tiff[43] = tiff[103]
            val count = width * height * 3
            tiff[114] = (count shr 24 and 0xff).toByte()
            tiff[115] = (count shr 16 and 0xff).toByte()
            tiff[116] = (count shr 8 and 0xff).toByte()
            tiff[117] = (count and 0xff).toByte()

            // spew the header to the disk
            output.write(tiff)
            for (i in pixels!!.indices) {
                output.write(pixels!![i] shr 16 and 0xff)
                output.write(pixels!![i] shr 8 and 0xff)
                output.write(pixels!![i] and 0xff)
            }
            output.flush()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }


    /**
     * Creates a Targa32 formatted byte sequence of specified
     * pixel buffer using RLE compression.
     *
     * Also figured out how to avoid parsing the image upside-down
     * (there's a header flag to set the image origin to top-left)
     *
     * Starting with revision 0092, the format setting is taken into account:
     * <UL>
     * <LI><TT>ALPHA</TT> images written as 8bit grayscale (uses lowest byte)
    </LI> * <LI><TT>RGB</TT>  24 bits
    </LI> * <LI><TT>ARGB</TT>  32 bits
    </LI></UL> *
     * All versions are RLE compressed.
     *
     * Contributed by toxi 8-10 May 2005, based on this RLE
     * <A HREF="http://www.wotsit.org/download.asp?f=tga">specification</A>
     */
    protected fun saveTGA(output: OutputStream): Boolean {
        val header = ByteArray(18)
        if (format == PConstants.ALPHA) {  // save ALPHA images as 8bit grayscale
            header[2] = 0x0B
            header[16] = 0x08
            header[17] = 0x28
        } else if (format == PConstants.RGB) {
            header[2] = 0x0A
            header[16] = 24
            header[17] = 0x20
        } else if (format == PConstants.ARGB) {
            header[2] = 0x0A
            header[16] = 32
            header[17] = 0x28
        } else {
            throw RuntimeException("Image format not recognized inside save()")
        }
        // set image dimensions lo-hi byte order
        header[12] = (width and 0xff).toByte()
        header[13] = (width shr 8).toByte()
        header[14] = (height and 0xff).toByte()
        header[15] = (height shr 8).toByte()
        return try {
            output.write(header)
            val maxLen = height * width
            var index = 0
            var col: Int //, prevCol;
            val currChunk = IntArray(128)

            // 8bit image exporter is in separate loop
            // to avoid excessive conditionals...
            if (format == PConstants.ALPHA) {
                while (index < maxLen) {
                    var isRLE = false
                    var rle = 1
                    col = pixels!![index] and 0xff
                    currChunk[0] = col
                    while (index + rle < maxLen) {
                        if (col != pixels!![index + rle] and 0xff || rle == 128) {
                            isRLE = rle > 1
                            break
                        }
                        rle++
                    }
                    if (isRLE) {
                        output.write(0x80 or rle - 1)
                        output.write(col)
                    } else {
                        rle = 1
                        while (index + rle < maxLen) {
                            val cscan = pixels!![index + rle] and 0xff
                            if (col != cscan && rle < 128 || rle < 3) {
                                col = cscan
                                currChunk[rle] = col
                            } else {
                                if (col == cscan) rle -= 2
                                break
                            }
                            rle++
                        }
                        output.write(rle - 1)
                        for (i in 0 until rle) output.write(currChunk[i])
                    }
                    index += rle
                }
            } else {  // export 24/32 bit TARGA
                while (index < maxLen) {
                    var isRLE = false
                    col = pixels!![index]
                    currChunk[0] = col
                    var rle = 1
                    // try to find repeating bytes (min. len = 2 pixels)
                    // maximum chunk size is 128 pixels
                    while (index + rle < maxLen) {
                        if (col != pixels!![index + rle] || rle == 128) {
                            isRLE = rle > 1 // set flag for RLE chunk
                            break
                        }
                        rle++
                    }
                    if (isRLE) {
                        output.write(128 or rle - 1)
                        output.write(col and 0xff)
                        output.write(col shr 8 and 0xff)
                        output.write(col shr 16 and 0xff)
                        if (format == PConstants.ARGB) output.write(col ushr 24 and 0xff)
                    } else {  // not RLE
                        rle = 1
                        while (index + rle < maxLen) {
                            if (col != pixels!![index + rle] && rle < 128 || rle < 3) {
                                col = pixels!![index + rle]
                                currChunk[rle] = col
                            } else {
                                // check if the exit condition was the start of
                                // a repeating colour
                                if (col == pixels!![index + rle]) rle -= 2
                                break
                            }
                            rle++
                        }
                        // write uncompressed chunk
                        output.write(rle - 1)
                        if (format == PConstants.ARGB) {
                            for (i in 0 until rle) {
                                col = currChunk[i]
                                output.write(col and 0xff)
                                output.write(col shr 8 and 0xff)
                                output.write(col shr 16 and 0xff)
                                output.write(col ushr 24 and 0xff)
                            }
                        } else {
                            for (i in 0 until rle) {
                                col = currChunk[i]
                                output.write(col and 0xff)
                                output.write(col shr 8 and 0xff)
                                output.write(col shr 16 and 0xff)
                            }
                        }
                    }
                    index += rle
                }
            }
            output.flush()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }


    /**
     * Use ImageIO functions from Java 1.4 and later to handle image save.
     * Various formats are supported, typically jpeg, png, bmp, and wbmp.
     * To get a list of the supported formats for writing, use: <BR></BR>
     * <TT>println(javax.imageio.ImageIO.getReaderFormatNames())</TT>
     */
    //  protected void saveImageIO(String path) throws IOException {
    //    try {
    //      BufferedImage bimage =
    //        new BufferedImage(width, height, (format == ARGB) ?
    //                          BufferedImage.TYPE_INT_ARGB :
    //                          BufferedImage.TYPE_INT_RGB);
    //
    //      bimage.setRGB(0, 0, width, height, pixels, 0, width);
    //
    //      File file = new File(path);
    //      String extension = path.substring(path.lastIndexOf('.') + 1);
    //
    //      ImageIO.write(bimage, extension, file);
    //
    //    } catch (Exception e) {
    //      e.printStackTrace();
    //      throw new IOException("image save failed.");
    //    }
    //  }


    protected lateinit var saveImageFormats: Array<String>


    /**
     * Save this image to disk.
     *
     *
     * As of revision 0100, this function requires an absolute path,
     * in order to avoid confusion. To save inside the sketch folder,
     * use the function savePath() from PApplet, or use saveFrame() instead.
     * As of revision 0116, savePath() is not needed if this object has been
     * created (as recommended) via createImage() or createGraphics() or
     * one of its neighbors.
     *
     *
     * As of revision 0115, when using Java 1.4 and later, you can write
     * to several formats besides tga and tiff. If Java 1.4 is installed
     * and the extension used is supported (usually png, jpg, jpeg, bmp,
     * and tiff), then those methods will be used to write the image.
     * To get a list of the supported formats for writing, use: <BR></BR>
     * <TT>println(javax.imageio.ImageIO.getReaderFormatNames())</TT>
     *
     *
     * To use the original built-in image writers, use .tga or .tif as the
     * extension, or don't include an extension. When no extension is used,
     * the extension .tif will be added to the file name.
     *
     *
     * The ImageIO API claims to support wbmp files, however they probably
     * require a black and white image. Basic testing produced a zero-length
     * file with no error.
     */
    open fun save(path: String): Boolean {  // ignore
        var path = path
        var success = false

        // Make sure the pixel data is ready to go
        loadPixels()
        try {
            val output: OutputStream = BufferedOutputStream(parent!!.createOutput(path), 16 * 1024)
            val lower = path.toLowerCase()
            val extension = lower.substring(lower.lastIndexOf('.') + 1)
            if (extension == "jpg" || extension == "jpeg") {
                // TODO probably not necessary to create another bitmap
                val outgoing = Bitmap.createBitmap(pixels!!, width, height, Bitmap.Config.ARGB_8888)
                success = outgoing.compress(CompressFormat.JPEG, 100, output)
            } else if (extension == "png") {
                val outgoing = Bitmap.createBitmap(pixels!!, width, height, Bitmap.Config.ARGB_8888)
                success = outgoing.compress(CompressFormat.PNG, 100, output)
            } else if (extension == "tga") {
                success = saveTGA(output) //, pixels, width, height, format);
            } else {
                if (extension != "tif" && extension != "tiff") {
                    // if no .tif extension, add it..
                    path += ".tif"
                }
                success = saveTIFF(output)
            }
            output.flush()
            output.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (!success) {
            System.err.println("Could not write the image to $path")
        }
        return success
    }


    companion object {

        // fixed point precision is limited to 15 bits!!

        const val PRECISIONB = 15
        const val PRECISIONF = 1 shl PRECISIONB
        const val PREC_MAXVAL = PRECISIONF - 1
        const val PREC_ALPHA_SHIFT = 24 - PRECISIONB
        const val PREC_RED_SHIFT = 16 - PRECISIONB

        // colour component bitmasks (moved from PConstants in 2.0b7)
        const val ALPHA_MASK = -0x1000000
        const val RED_MASK = 0x00ff0000
        const val GREEN_MASK = 0x0000ff00
        const val BLUE_MASK = 0x000000ff


        //////////////////////////////////////////////////////////////

        // BLEND

        /**
         * Blend two colors based on a particular mode.
         * <UL>
         * <LI>REPLACE - destination colour equals colour of source pixel: C = A.
         * Sometimes called "Normal" or "Copy" in other software.
         *
        </LI> * <LI>BLEND - linear interpolation of colours:
         * <TT>C = A*factor + B</TT>
         *
        </LI> * <LI>ADD - additive blending with white clip:
         * <TT>C = min(A*factor + B, 255)</TT>.
         * Clipped to 0..255, Photoshop calls this "Linear Burn",
         * and Director calls it "Add Pin".
         *
        </LI> * <LI>SUBTRACT - substractive blend with black clip:
         * <TT>C = max(B - A*factor, 0)</TT>.
         * Clipped to 0..255, Photoshop calls this "Linear Dodge",
         * and Director calls it "Subtract Pin".
         *
        </LI> * <LI>DARKEST - only the darkest colour succeeds:
         * <TT>C = min(A*factor, B)</TT>.
         * Illustrator calls this "Darken".
         *
        </LI> * <LI>LIGHTEST - only the lightest colour succeeds:
         * <TT>C = max(A*factor, B)</TT>.
         * Illustrator calls this "Lighten".
         *
        </LI> * <LI>DIFFERENCE - subtract colors from underlying image.
         *
        </LI> * <LI>EXCLUSION - similar to DIFFERENCE, but less extreme.
         *
        </LI> * <LI>MULTIPLY - Multiply the colors, result will always be darker.
         *
        </LI> * <LI>SCREEN - Opposite multiply, uses inverse values of the colors.
         *
        </LI> * <LI>OVERLAY - A mix of MULTIPLY and SCREEN. Multiplies dark values,
         * and screens light values.
         *
        </LI> * <LI>HARD_LIGHT - SCREEN when greater than 50% gray, MULTIPLY when lower.
         *
        </LI> * <LI>SOFT_LIGHT - Mix of DARKEST and LIGHTEST.
         * Works like OVERLAY, but not as harsh.
         *
        </LI> * <LI>DODGE - Lightens light tones and increases contrast, ignores darks.
         * Called "Color Dodge" in Illustrator and Photoshop.
         *
        </LI> * <LI>BURN - Darker areas are applied, increasing contrast, ignores lights.
         * Called "Color Burn" in Illustrator and Photoshop.
        </LI></UL> *
         * <P>A useful reference for blending modes and their algorithms can be
         * found in the <A HREF="http://www.w3.org/TR/SVG12/rendering.html">SVG</A>
         * specification.</P>
         * <P>It is important to note that Processing uses "fast" code, not
         * necessarily "correct" code. No biggie, most software does. A nitpicker
         * can find numerous "off by 1 division" problems in the blend code where
         * <TT>&gt;&gt;8</TT> or <TT>&gt;&gt;7</TT> is used when strictly speaking
         * <TT>/255.0 or <TT>/127.0</TT> should have been used.</TT></P>
         * <P>For instance, exclusion (not intended for real-time use) reads
         * <TT>r1 + r2 - ((2 * r1 * r2) / 255)</TT> because <TT>255 == 1.0</TT>
         * not <TT>256 == 1.0</TT>. In other words, <TT>(255*255)>>8</TT> is not
         * the same as <TT>(255*255)/255</TT>. But for real-time use the shifts
         * are preferrable, and the difference is insignificant for applications
         * built with Processing.</P>
         */
        @JvmStatic
        fun blendColor(c1: Int, c2: Int, mode: Int): Int {  // ignore
            when (mode) {
                PConstants.REPLACE -> return c2
                PConstants.BLEND -> return blend_blend(c1, c2)
                PConstants.ADD -> return blend_add_pin(c1, c2)
                PConstants.SUBTRACT -> return blend_sub_pin(c1, c2)
                PConstants.LIGHTEST -> return blend_lightest(c1, c2)
                PConstants.DARKEST -> return blend_darkest(c1, c2)
                PConstants.DIFFERENCE -> return blend_difference(c1, c2)
                PConstants.EXCLUSION -> return blend_exclusion(c1, c2)
                PConstants.MULTIPLY -> return blend_multiply(c1, c2)
                PConstants.SCREEN -> return blend_screen(c1, c2)
                PConstants.HARD_LIGHT -> return blend_hard_light(c1, c2)
                PConstants.SOFT_LIGHT -> return blend_soft_light(c1, c2)
                PConstants.OVERLAY -> return blend_overlay(c1, c2)
                PConstants.DODGE -> return blend_dodge(c1, c2)
                PConstants.BURN -> return blend_burn(c1, c2)
            }
            return 0
        }


        //////////////////////////////////////////////////////////////

        // internal blending methods

        @JvmStatic
        private fun min(a: Int, b: Int): Int {
            return if (a < b) a else b
        }

        @JvmStatic
        private fun max(a: Int, b: Int): Int {
            return if (a > b) a else b
        }


        /////////////////////////////////////////////////////////////

        // BLEND MODE IMPLEMENTATIONS
  /*
   * Jakub Valtar
   *
   * All modes use SRC alpha to interpolate between DST and the result of
   * the operation:
   *
   * R = (1 - SRC_ALPHA) * DST + SRC_ALPHA * <RESULT OF THE OPERATION>
   *
   * Comments above each mode only specify the formula of its operation.
   *
   * These implementations treat alpha 127 (=255/2) as a perfect 50 % mix.
   *
   * One alpha value between 126 and 127 is intentionally left out,
   * so the step 126 -> 127 is twice as big compared to other steps.
   * This is because our colors are in 0..255 range, but we divide
   * by right shifting 8 places (=256) which is much faster than
   * (correct) float division by 255.0f. The missing value was placed
   * between 126 and 127, because limits of the range (near 0 and 255) and
   * the middle value (127) have to blend correctly.
   *
   * Below you will often see RED and BLUE channels (RB) manipulated together
   * and GREEN channel (GN) manipulated separately. It is sometimes possible
   * because the operation won't use more than 16 bits, so we process the RED
   * channel in the upper 16 bits and BLUE channel in the lower 16 bits. This
   * decreases the number of operations per pixel and thus makes things faster.
   *
   * Some of the modes are hand tweaked (various +1s etc.) to be more accurate
   * and to produce correct values in extremes. Below is a sketch you can use
   * to check any blending function for
   *
   * 1) Discrepancies between color channels:
   *    - highlighted by the offending color
   * 2) Behavior at extremes (set colorCount to 256):
   *    - values of all corners are printed to the console
   * 3) Rounding errors:
   *    - set colorCount to lower value to better see color bands
   *

// use powers of 2 in range 2..256
// to better see color bands
final int colorCount = 256;

final int blockSize = 3;

void settings() {
  size(blockSize * 256, blockSize * 256);
}

void setup() { }

void draw() {
  noStroke();
  colorMode(RGB, colorCount-1);
  int alpha = (mouseX / blockSize) << 24;
  int r, g, b, r2, g2, b2 = 0;
  for (int x = 0; x <= 0xFF; x++) {
    for (int y = 0; y <= 0xFF; y++) {
      int dst = (x << 16) | (x << 8) | x;
      int src = (y << 16) | (y << 8) | y | alpha;
      int result = testFunction(dst, src);
      r = r2 = (result >> 16 & 0xFF);
      g = g2 = (result >>  8 & 0xFF);
      b = b2 = (result >>  0 & 0xFF);
      if (r != g && r != b) r2 = (128 + r2) % 255;
      if (g != r && g != b) g2 = (128 + g2) % 255;
      if (b != r && b != g) b2 = (128 + b2) % 255;
      fill(r2 % colorCount, g2 % colorCount, b2 % colorCount);
      rect(x * blockSize, y * blockSize, blockSize, blockSize);
    }
  }
  println(
    "alpha:", mouseX/blockSize,
    "TL:", hex(get(0, 0)),
    "TR:", hex(get(width-1, 0)),
    "BR:", hex(get(width-1, height-1)),
    "BL:", hex(get(0, height-1)));
}

int testFunction(int dst, int src) {
  // your function here
  return dst;
}

   *
   *
   */
        private const val RB_MASK = 0x00FF00FF
        private const val GN_MASK = 0x0000FF00


        /**
         * Blend
         * O = S
         */
        private fun blend_blend(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a

            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + (src and RB_MASK) * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + (src and GN_MASK) * s_a ushr 8 and GN_MASK)
        }


        /**
         * Add
         * O = MIN(D + S, 1)
         */
        private fun blend_add_pin(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val rb = (dst and RB_MASK) + ((src and RB_MASK) * s_a ushr 8 and RB_MASK)
            val gn = (dst and GN_MASK) + ((src and GN_MASK) * s_a ushr 8)

            return min((dst ushr 24) + a, 0xFF) shl 24 or
                    min(rb and -0x10000, RED_MASK) or
                    min(gn and 0x00FFFF00, GREEN_MASK) or
                    min(rb and 0x0000FFFF, BLUE_MASK)
        }

        /**
         * Subtract
         * O = MAX(0, D - S)
         */
        private fun blend_sub_pin(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val rb = (src and RB_MASK) * s_a ushr 8

            val gn = (src and GREEN_MASK) * s_a ushr 8

            return min((dst ushr 24) + a, 0xFF) shl 24 or
                    max((dst and RED_MASK) - (rb and RED_MASK), 0) or
                    max((dst and GREEN_MASK) - (gn and GREEN_MASK), 0) or
                    max((dst and BLUE_MASK) - (rb and BLUE_MASK), 0)
        }

        /**
         * Lightest
         * O = MAX(D, S)
         */
        private fun blend_lightest(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a

            val rb = max(src and RED_MASK, dst and RED_MASK) or max(src and BLUE_MASK, dst and BLUE_MASK)

            val gn = max(src and GREEN_MASK, dst and GREEN_MASK)

            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + rb * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + gn * s_a ushr 8 and GN_MASK)
        }

        /**
         * Darkest
         * O = MIN(D, S)
         */
        private fun blend_darkest(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a

            val rb = min(src and RED_MASK, dst and RED_MASK) or min(src and BLUE_MASK, dst and BLUE_MASK)

            val gn = min(src and GREEN_MASK, dst and GREEN_MASK)

            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + rb * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + gn * s_a ushr 8 and GN_MASK)
        }

        /**
         * Difference
         * O = ABS(D - S)
         */
        private fun blend_difference(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val r = (dst and RED_MASK) - (src and RED_MASK)
            val b = (dst and BLUE_MASK) - (src and BLUE_MASK)
            val g = (dst and GREEN_MASK) - (src and GREEN_MASK)
            val rb = (if (r < 0) -r else r) or
                    if (b < 0) -b else b
            val gn = if (g < 0) -g else g
            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + rb * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + gn * s_a ushr 8 and GN_MASK)
        }

        /**
         * Exclusion
         * O = (1 - S)D + S(1 - D)
         * O = D + S - 2DS
         */
        private fun blend_exclusion(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val d_rb = dst and RB_MASK
            val d_gn = dst and GN_MASK
            val s_gn = src and GN_MASK
            val f_r = dst and RED_MASK shr 16
            val f_b = dst and BLUE_MASK

            val rb_sub = (src and RED_MASK) * (f_r + if (f_r >= 0x7F) 1 else 0) or (src and BLUE_MASK) * (f_b + if (f_b >= 0x7F) 1 else 0) ushr 7 and 0x01FF01FF

            val gn_sub = s_gn * (d_gn + if (d_gn >= 0x7F00) 0x100 else 0) ushr 15 and 0x0001FF00

            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    d_rb * d_a + (d_rb + (src and RB_MASK) - rb_sub) * s_a ushr 8 and RB_MASK) or (
                    d_gn * d_a + (d_gn + s_gn - gn_sub) * s_a ushr 8 and GN_MASK)
        }

        /*
   * Multiply
   * O = DS
   */
        private fun blend_multiply(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val d_gn = dst and GN_MASK
            val f_r = dst and RED_MASK shr 16
            val f_b = dst and BLUE_MASK

            val rb = (src and RED_MASK) * (f_r + 1) or (src and BLUE_MASK) * (f_b + 1) ushr 8 and RB_MASK

            val gn = (src and GREEN_MASK) * (d_gn + 0x100) ushr 16 and GN_MASK

            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + rb * s_a ushr 8 and RB_MASK) or (
                    d_gn * d_a + gn * s_a ushr 8 and GN_MASK)
        }

        /**
         * Screen
         * O = 1 - (1 - D)(1 - S)
         * O = D + S - DS
         */
        private fun blend_screen(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val d_rb = dst and RB_MASK
            val d_gn = dst and GN_MASK
            val s_gn = src and GN_MASK
            val f_r = dst and RED_MASK shr 16
            val f_b = dst and BLUE_MASK

            val rb_sub = (src and RED_MASK) * (f_r + 1) or (src and BLUE_MASK) * (f_b + 1) ushr 8 and RB_MASK

            val gn_sub = s_gn * (d_gn + 0x100) ushr 16 and GN_MASK

            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    d_rb * d_a + (d_rb + (src and RB_MASK) - rb_sub) * s_a ushr 8 and RB_MASK) or (
                    d_gn * d_a + (d_gn + s_gn - gn_sub) * s_a ushr 8 and GN_MASK)
        }

        /**
         * Overlay
         * O = 2 * MULTIPLY(D, S) = 2DS                   for D < 0.5
         * O = 2 * SCREEN(D, S) - 1 = 2(S + D - DS) - 1   otherwise
         */
        private fun blend_overlay(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val d_r = dst and RED_MASK
            val d_g = dst and GREEN_MASK
            val d_b = dst and BLUE_MASK
            val s_r = src and RED_MASK
            val s_g = src and GREEN_MASK
            val s_b = src and BLUE_MASK
            val r = if (d_r < 0x800000) d_r * ((s_r ushr 16) + 1) ushr 7 else 0xFF0000 - ((0x100 - (s_r ushr 16)) * (RED_MASK - d_r) ushr 7)
            val g = if (d_g < 0x8000) d_g * (s_g + 0x100) ushr 15 else 0xFF00 - ((0x10000 - s_g) * (GREEN_MASK - d_g) ushr 15)
            val b = if (d_b < 0x80) d_b * (s_b + 1) ushr 7 else 0xFF00 - ((0x100 - s_b) * (BLUE_MASK - d_b) shl 1) ushr 8
            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + (r or b and RB_MASK) * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + (g and GN_MASK) * s_a ushr 8 and GN_MASK)
        }

        /**
         * Hard Light
         * O = OVERLAY(S, D)
         *
         * O = 2 * MULTIPLY(D, S) = 2DS                   for S < 0.5
         * O = 2 * SCREEN(D, S) - 1 = 2(S + D - DS) - 1   otherwise
         */
        private fun blend_hard_light(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val d_r = dst and RED_MASK
            val d_g = dst and GREEN_MASK
            val d_b = dst and BLUE_MASK
            val s_r = src and RED_MASK
            val s_g = src and GREEN_MASK
            val s_b = src and BLUE_MASK
            val r = if (s_r < 0x800000) s_r * ((d_r ushr 16) + 1) ushr 7 else 0xFF0000 - ((0x100 - (d_r ushr 16)) * (RED_MASK - s_r) ushr 7)
            val g = if (s_g < 0x8000) s_g * (d_g + 0x100) ushr 15 else 0xFF00 - ((0x10000 - d_g) * (GREEN_MASK - s_g) ushr 15)
            val b = if (s_b < 0x80) s_b * (d_b + 1) ushr 7 else 0xFF00 - ((0x100 - d_b) * (BLUE_MASK - s_b) shl 1) ushr 8
            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + (r or b and RB_MASK) * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + (g and GN_MASK) * s_a ushr 8 and GN_MASK)
        }

        /**
         * Soft Light (Pegtop)
         * O = (1 - D) * MULTIPLY(D, S) + D * SCREEN(D, S)
         * O = (1 - D) * DS + D * (1 - (1 - D)(1 - S))
         * O = 2DS + DD - 2DDS
         */
        private fun blend_soft_light(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val d_r = dst and RED_MASK
            val d_g = dst and GREEN_MASK
            val d_b = dst and BLUE_MASK
            val s_r1 = src and RED_MASK shr 16
            val s_g1 = src and GREEN_MASK shr 8
            val s_b1 = src and BLUE_MASK
            val d_r1 = (d_r shr 16) + if (s_r1 < 7f) 1 else 0
            val d_g1 = (d_g shr 8) + if (s_g1 < 7f) 1 else 0
            val d_b1 = d_b + if (s_b1 < 7f) 1 else 0
            val r = (s_r1 * d_r shr 7) + 0xFF * d_r1 * (d_r1 + 1) -
                    (s_r1 * d_r1 * d_r1 shl 1) and RED_MASK
            val g = (s_g1 * d_g shl 1) + 0xFF * d_g1 * (d_g1 + 1) -
                    (s_g1 * d_g1 * d_g1 shl 1) ushr 8 and GREEN_MASK
            val b = (s_b1 * d_b shl 9) + 0xFF * d_b1 * (d_b1 + 1) -
                    (s_b1 * d_b1 * d_b1 shl 1) ushr 16
            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + (r or b) * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + g * s_a ushr 8 and GN_MASK)
        }

        /**
         * Dodge
         * O = D / (1 - S)
         */
        private fun blend_dodge(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val r = (dst and RED_MASK) / (256 - (src and RED_MASK shr 16))
            val g = (dst and GREEN_MASK shl 8) / (256 - (src and GREEN_MASK shr 8))
            val b = (dst and BLUE_MASK shl 8) / (256 - (src and BLUE_MASK))
            val rb = (if (r > 0xFF00) 0xFF0000 else r shl 8 and RED_MASK) or
                    if (b > 0x00FF) 0x0000FF else b
            val gn = if (g > 0xFF00) 0x00FF00 else g and GREEN_MASK
            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + rb * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + gn * s_a ushr 8 and GN_MASK)
        }

        /**
         * Burn
         * O = 1 - (1 - A) / B
         */
        private fun blend_burn(dst: Int, src: Int): Int {
            val a = src ushr 24
            val s_a = a + if (a >= 0x7F) 1 else 0
            val d_a = 0x100 - s_a
            val r = (0xFF0000 - (dst and RED_MASK)) / (1 + (src and RED_MASK shr 16))
            val g = (0x00FF00 - (dst and GREEN_MASK) shl 8) / (1 + (src and GREEN_MASK shr 8))
            val b = (0x0000FF - (dst and BLUE_MASK) shl 8) / (1 + (src and BLUE_MASK))
            val rb = RB_MASK -
                    (if (r > 0xFF00) 0xFF0000 else r shl 8 and RED_MASK) -
                    if (b > 0x00FF) 0x0000FF else b
            val gn = GN_MASK -
                    if (g > 0xFF00) 0x00FF00 else g and GREEN_MASK
            return min((dst ushr 24) + a, 0xFF) shl 24 or (
                    (dst and RB_MASK) * d_a + rb * s_a ushr 8 and RB_MASK) or (
                    (dst and GN_MASK) * d_a + gn * s_a ushr 8 and GN_MASK)
        }


        //////////////////////////////////////////////////////////////

        // FILE I/O

        var TIFF_HEADER = byteArrayOf(
                77, 77, 0, 42, 0, 0, 0, 8, 0, 9, 0, -2, 0, 4, 0, 0, 0, 1, 0, 0,
                0, 0, 1, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 3, 0, 0, 0, 1,
                0, 0, 0, 0, 1, 2, 0, 3, 0, 0, 0, 3, 0, 0, 0, 122, 1, 6, 0, 3, 0,
                0, 0, 1, 0, 2, 0, 0, 1, 17, 0, 4, 0, 0, 0, 1, 0, 0, 3, 0, 1, 21,
                0, 3, 0, 0, 0, 1, 0, 3, 0, 0, 1, 22, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0,
                1, 23, 0, 4, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 8, 0, 8
        )

        const val TIFF_ERROR = "Error: Processing can only read its own TIFF files."

        @JvmStatic
        protected fun loadTIFF(tiff: ByteArray): PImage? {
            if (tiff[42] != tiff[102] ||  // width/height in both places
                    tiff[43] != tiff[103]) {
                System.err.println(TIFF_ERROR)
                return null
            }
            val width: Int = ((tiff[30] and 0xff.toByte()).toInt() shl 8) or ((tiff[31] and 0xff.toByte()).toInt())
            val height: Int = (tiff[42] and 0xff.toByte()).toInt() shl 8 or ((tiff[43] and 0xff.toByte()).toInt())
            var count: Int = (tiff[114] and 0xff.toByte()).toInt() shl 24 or
                    ((tiff[115] and 0xff.toByte()).toInt() shl 16) or
                    ((tiff[116] and 0xff.toByte()).toInt() shl 8) or
                    ((tiff[117] and 0xff.toByte()).toInt())
            if (count != width * height * 3) {
                System.err.println("$TIFF_ERROR ($width, $height)")
                return null
            }

            // check the rest of the header
            for (i in TIFF_HEADER.indices) {
                if (i == 30 || i == 31 || i == 42 || i == 43 ||
                        i == 102 || i == 103 ||
                        i == 114 || i == 115 || i == 116 || i == 117) continue
                if (tiff[i] != TIFF_HEADER[i]) {
                    System.err.println("$TIFF_ERROR ($i)")
                    return null
                }
            }
            val outgoing = PImage(width, height, PConstants.RGB)

            // Not possible because this method is static, so careful when using it.
            // outgoing.parent = parent;
            var index = 768
            count /= 3
            for (i in 0 until count) {
                outgoing.pixels!![i] = -0x1000000 or (
                        (tiff[index++] and 0xff.toByte()).toInt() shl 16) or (
                        (tiff[index++] and 0xff.toByte()).toInt() shl 8) or
                        ((tiff[index++] and 0xff.toByte()).toInt())
            }
            return outgoing
        }
    }
}