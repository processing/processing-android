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

package processing.core;

import java.io.*;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;


/**
 * Storage class for pixel data. This is the base class for most image and
 * pixel information, such as PGraphics and the video library classes.
 * <P>
 * Code for copying, resizing, scaling, and blending contributed
 * by <A HREF="http://www.toxi.co.uk">toxi</A>.
 * <P>
 */
public class PImage implements PConstants, Cloneable {

  /**
   * Format for this image, one of RGB, ARGB or ALPHA.
   * note that RGB images still require 0xff in the high byte
   * because of how they'll be manipulated by other functions
   */
  public int format;

  public int[] pixels;
  public int width, height;

  /**
   * For the time being, simply to ensure compatibility with Java mode code
   */
  public int pixelDensity = 1;
  public int pixelWidth;
  public int pixelHeight;

  /**
   * Path to parent object that will be used with save().
   * This prevents users from needing savePath() to use PImage.save().
   */
  public PApplet parent;

  protected Bitmap bitmap;


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /** for renderers that need to store info about the image */
  protected HashMap<PGraphics, Object> cacheMap;

  /** for renderers that need to store parameters about the image */
  protected HashMap<PGraphics, Object> paramMap;


  /** modified portion of the image */
  protected boolean modified;
  protected int mx1, my1, mx2, my2;

  /** Loaded pixels flag */
  public boolean loaded = false;

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  // private fields
  private int fracU, ifU, fracV, ifV, u1, u2, v1, v2, sX, sY, iw, iw1, ih1;
  private int ul, ll, ur, lr, cUL, cLL, cUR, cLR;
  private int srcXOffset, srcYOffset;
  private int r, g, b, a;
  private int[] srcBuffer;

  // fixed point precision is limited to 15 bits!!
  static final int PRECISIONB = 15;
  static final int PRECISIONF = 1 << PRECISIONB;
  static final int PREC_MAXVAL = PRECISIONF-1;
  static final int PREC_ALPHA_SHIFT = 24-PRECISIONB;
  static final int PREC_RED_SHIFT = 16-PRECISIONB;

  // internal kernel stuff for the gaussian blur filter
  private int blurRadius;
  private int blurKernelSize;
  private int[] blurKernel;
  private int[][] blurMult;

  // colour component bitmasks (moved from PConstants in 2.0b7)
  public static final int ALPHA_MASK = 0xff000000;
  public static final int RED_MASK   = 0x00ff0000;
  public static final int GREEN_MASK = 0x0000ff00;
  public static final int BLUE_MASK  = 0x000000ff;


  //////////////////////////////////////////////////////////////


  /**
   * Create an empty image object, set its format to RGB.
   * The pixel array is not allocated.
   */
  public PImage() {
    format = ARGB;  // default to ARGB images for release 0116
//    cache = null;
  }


  /**
   * Create a new RGB (alpha ignored) image of a specific size.
   * All pixels are set to zero, meaning black, but since the
   * alpha is zero, it will be transparent.
   */
  public PImage(int width, int height) {
    init(width, height, RGB);
  }


  public PImage(int width, int height, int format) {
    init(width, height, format);
  }


  /**
   * Function to be used by subclasses of PImage to init later than
   * at the constructor, or re-init later when things changes.
   * Used by Capture and Movie classes (and perhaps others),
   * because the width/height will not be known when super() is called.
   * (Leave this public so that other libraries can do the same.)
   */
  public void init(int width, int height, int format) {  // ignore
    this.width = width;
    this.height = height;
    this.pixels = new int[width*height];
    this.format = format;
//    this.cache = null;

    pixelWidth = width * pixelDensity;
    pixelHeight = height * pixelDensity;
    this.pixels = new int[pixelWidth * pixelHeight];
  }


  /**
   * Check the alpha on an image, using a really primitive loop.
   */
  protected void checkAlpha() {
    if (pixels == null) return;

    for (int i = 0; i < pixels.length; i++) {
      // since transparency is often at corners, hopefully this
      // will find a non-transparent pixel quickly and exit
      if ((pixels[i] & 0xff000000) != 0xff000000) {
        format = ARGB;
        break;
      }
    }
  }


  //////////////////////////////////////////////////////////////


  /**
   * Construct a new PImage from an Android bitmap. The pixels[] array is not
   * initialized, nor is data copied to it, until loadPixels() is called.
   */
  public PImage(Object nativeObject) {
    Bitmap bitmap = (Bitmap) nativeObject;
    this.bitmap = bitmap;
    this.width = bitmap.getWidth();
    this.height = bitmap.getHeight();
    this.pixels = null;
    this.format = bitmap.hasAlpha() ? ARGB : RGB;
    this.pixelDensity = 1;
    this.pixelWidth = width;
    this.pixelHeight = height;
  }


  /**
   * Returns the native Bitmap object for this PImage.
   */
  public Object getNative() {
    return bitmap;
  }


  //////////////////////////////////////////////////////////////

  // MARKING IMAGE AS MODIFIED / FOR USE w/ GET/SET


  public boolean isModified() {  // ignore
    return modified;
  }


  public void setModified() {  // ignore
    modified = true;
  }


  public void setModified(boolean m) {  // ignore
    modified = m;
  }

  public int getModifiedX1() {  // ignore
    return mx1;
  }


  public int getModifiedX2() {  // ignore
    return mx2;
  }


  public int getModifiedY1() {  // ignore
    return my1;
  }


  public int getModifiedY2() {  // ignore
    return my2;
  }


  /**
   * Call this when you want to mess with the pixels[] array.
   * <p/>
   * For subclasses where the pixels[] buffer isn't set by default,
   * this should copy all data into the pixels[] array
   */
  public void loadPixels() {  // ignore
    if (pixels == null || pixels.length != width*height) {
      pixels = new int[width*height];
    }
    if (bitmap != null) {
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    }
    setLoaded();
  }


  /**
   * Call this when finished messing with the pixels[] array.
   * <p/>
   * Mark all pixels as needing update.
   */
  public void updatePixels() {  // ignore
    updatePixelsImpl(0, 0, width, height);
  }


  /**
   * Mark the pixels in this region as needing an update.
   * <P>
   * This is not currently used by any of the renderers, however the api
   * is structured this way in the hope of being able to use this to
   * speed things up in the future.
   */
  public void updatePixels(int x, int y, int w, int h) {  // ignore
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
    updatePixelsImpl(x, y, w, h);
  }


  protected void updatePixelsImpl(int x, int y, int w, int h) {
    int x2 = x + w;
    int y2 = y + h;

    if (!modified) {
      mx1 = PApplet.max(0, x);
      //mx2 = PApplet.min(width - 1, x2);
      mx2 = PApplet.min(width, x2);
      my1 = PApplet.max(0, y);
      //my2 = PApplet.min(height - 1, y2);
      my2 = PApplet.min(height, y2);
      modified = true;

    } else {
      if (x < mx1) mx1 = PApplet.max(0, x);
      //if (x > mx2) mx2 = PApplet.min(width - 1, x);
      if (x > mx2) mx2 = PApplet.min(width, x);
      if (y < my1) my1 = PApplet.max(0, y);
      //if (y > my2) my2 = y;
      if (y > my2) my2 = PApplet.min(height, y);

      if (x2 < mx1) mx1 = PApplet.max(0, x2);
      //if (x2 > mx2) mx2 = PApplet.min(width - 1, x2);
      if (x2 > mx2) mx2 = PApplet.min(width, x2);
      if (y2 < my1) my1 = PApplet.max(0, y2);
      //if (y2 > my2) my2 = PApplet.min(height - 1, y2);
      if (y2 > my2) my2 = PApplet.min(height, y2);
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
  @Override
  public Object clone() throws CloneNotSupportedException {  // ignore
    return get();
  }


  /**
   * Resize this image to a new width and height.
   * Use 0 for wide or high to make that dimension scale proportionally.
   */
  public void resize(int w, int h) {  // ignore
    if (w <= 0 && h <= 0) {
      throw new IllegalArgumentException("width or height must be > 0 for resize");
    }

    if (w == 0) {  // Use height to determine relative size
      float diff = (float) h / (float) height;
      w = (int) (width * diff);
    } else if (h == 0) {  // Use the width to determine relative size
      float diff = (float) w / (float) width;
      h = (int) (height * diff);
    }
    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
    this.width = w;
    this.height = h;

    // Mark the pixels array as altered
    updatePixels();
  }


  //////////////////////////////////////////////////////////////

  // MARKING IMAGE AS LOADED / FOR USE IN RENDERERS


  public boolean isLoaded() { // ignore
    return loaded;
  }


  public void setLoaded() {  // ignore
    loaded = true;
  }


  public void setLoaded(boolean l) {  // ignore
    loaded = l;
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
   * <P>
   * If the image is in ALPHA format, this returns a white with its
   * alpha value set.
   * <P>
   * This function is included primarily for beginners. It is quite
   * slow because it has to check to see if the x, y that was provided
   * is inside the bounds, and then has to check to see what image
   * type it is. If you want things to be more efficient, access the
   * pixels[] array directly.
   */
  public int get(int x, int y) {
    if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) return 0;

    if (pixels == null) {
      return bitmap.getPixel(x, y);

    } else {
      // If the pixels array exists, it's fairly safe to assume that it's
      // the most up to date, and that it's faster for access.
      switch (format) {
      case RGB:
        return pixels[y*width + x] | 0xff000000;

      case ARGB:
        return pixels[y*width + x];

      case ALPHA:
        return (pixels[y*width + x] << 24) | 0xffffff;
      }
    }
    return 0;
  }


  /**
   * Grab a subsection of a PImage, and copy it into a fresh PImage.
   * As of release 0149, no longer honors imageMode() for the coordinates.
   */
  /**
   * @param w width of pixel rectangle to get
   * @param h height of pixel rectangle to get
   */
  public PImage get(int x, int y, int w, int h) {
    int targetX = 0;
    int targetY = 0;
    int targetWidth = w;
    int targetHeight = h;
    boolean cropped = false;

    if (x < 0) {
      w += x; // x is negative, removes the left edge from the width
      targetX = -x;
      cropped = true;
      x = 0;
    }
    if (y < 0) {
      h += y; // y is negative, clip the number of rows
      targetY = -y;
      cropped = true;
      y = 0;
    }

    if (x + w > width) {
      w = width - x;
      cropped = true;
    }
    if (y + h > height) {
      h = height - y;
      cropped = true;
    }

    if (w < 0) {
      w = 0;
    }
    if (h < 0) {
      h = 0;
    }

    int targetFormat = format;
    if (cropped && format == RGB) {
      targetFormat = ARGB;
    }

    PImage target = new PImage(targetWidth, targetHeight, targetFormat);
    target.parent = parent;  // parent may be null so can't use createImage()
    if (w > 0 && h > 0) {
      getImpl(x, y, w, h, target, targetX, targetY);
    }
    return target;
  }


  /**
   * Internal function to actually handle getting a block of pixels that
   * has already been properly cropped to a valid region. That is, x/y/w/h
   * are guaranteed to be inside the image space, so the implementation can
   * use the fastest possible pixel copying method.
   */
  protected void getImpl(int sourceX, int sourceY,
                         int sourceWidth, int sourceHeight,
                         PImage target, int targetX, int targetY) {
    if (pixels == null) {
      bitmap.getPixels(target.pixels,
                       targetY*target.width + targetX, target.width,
                       sourceX, sourceY, sourceWidth, sourceHeight);
    } else {
      int sourceIndex = sourceY*width + sourceX;
      int targetIndex = targetY*target.width + targetX;
      for (int row = 0; row < sourceHeight; row++) {
        System.arraycopy(pixels, sourceIndex, target.pixels, targetIndex, sourceWidth);
        sourceIndex += width;
        targetIndex += target.width;
      }
    }
  }


  /**
   * Returns a copy of this PImage. Equivalent to get(0, 0, width, height).
   */
  public PImage get() {
    // Formerly this used clone(), which caused memory problems.
    // http://code.google.com/p/processing/issues/detail?id=42
    return get(0, 0, width, height);
  }


  /**
   * Set a single pixel to the specified color.
   */
  public void set(int x, int y, int c) {
    if (pixels == null) {
      bitmap.setPixel(x, y, c);

    } else {
      if ((x < 0) || (y < 0) || (x >= width) || (y >= height)) return;
      pixels[y*width + x] = c;
      updatePixelsImpl(x, y, 1, 1);  // slow?
    }
  }


  /**
   * Efficient method of drawing an image's pixels directly to this surface.
   * No variations are employed, meaning that any scale, tint, or imageMode
   * settings will be ignored.
   */
  public void set(int x, int y, PImage img) {
    if (img.format == ALPHA) {
      // set() doesn't really make sense for an ALPHA image, since it
      // directly replaces pixels and does no blending.
      throw new IllegalArgumentException("set() not available for ALPHA images");
    }

    int sx = 0;
    int sy = 0;
    int sw = img.width;
    int sh = img.height;

    if (x < 0) {  // off left edge
      sx -= x;
      sw += x;
      x = 0;
    }
    if (y < 0) {  // off top edge
      sy -= y;
      sh += y;
      y = 0;
    }
    if (x + sw > width) {  // off right edge
      sw = width - x;
    }
    if (y + sh > height) {  // off bottom edge
      sh = height - y;
    }

    // this could be nonexistent
    if ((sw <= 0) || (sh <= 0)) return;

    setImpl(img, sx, sy, sw, sh, x, y);
  }


  /**
   * Internal function to actually handle setting a block of pixels that
   * has already been properly cropped from the image to a valid region.
   */
  protected void setImpl(PImage sourceImage,
                         int sourceX, int sourceY,
                         int sourceWidth, int sourceHeight,
                         int targetX, int targetY) {
    if (sourceImage.pixels == null) {
      sourceImage.loadPixels();
    }

    // if this.pixels[] is null, copying directly into this.bitmap
    if (pixels == null) {
      // if this.pixels[] is null, this.bitmap cannot be null
      // make sure the bitmap is writable
      if (!bitmap.isMutable()) {
        // create a mutable version of this bitmap
        bitmap = bitmap.copy(Config.ARGB_8888, true);
      }

      // copy from src.pixels to this.bitmap
      int offset = sourceY * sourceImage.width + sourceX;
      bitmap.setPixels(sourceImage.pixels,
                       offset, sourceImage.width,
                       targetX, targetY, sourceWidth, sourceHeight);

    } else {  // pixels != null
      // copy into this.pixels[] and mark as modified
      int srcOffset = sourceY * sourceImage.width + sourceX;
      int dstOffset = targetY * width + targetX;
      for (int y = sourceY; y < sourceY + sourceHeight; y++) {
        System.arraycopy(sourceImage.pixels, srcOffset, pixels, dstOffset, sourceWidth);
        srcOffset += sourceImage.width;
        dstOffset += width;
      }
      updatePixelsImpl(targetX, targetY, sourceWidth, sourceHeight);
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
   */
  public void mask(int alpha[]) {
    loadPixels();
    // don't execute if mask image is different size
    if (alpha.length != pixels.length) {
      throw new RuntimeException("The PImage used with mask() must be " +
                                 "the same size as the applet.");
    }
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = ((alpha[i] & 0xff) << 24) | (pixels[i] & 0xffffff);
    }
    format = ARGB;
    updatePixels();
  }


  /**
   * Set alpha channel for an image using another image as the source.
   */
  public void mask(PImage alpha) {
    if (alpha.pixels == null) {
      // if pixels haven't been loaded by the user, then only load them
      // temporarily to save memory when finished.
      alpha.loadPixels();
      mask(alpha.pixels);
      alpha.pixels = null;
    } else {
      mask(alpha.pixels);
    }
  }



  //////////////////////////////////////////////////////////////

  // IMAGE FILTERS


  /**
   * Method to apply a variety of basic filters to this image.
   * <P>
   * <UL>
   * <LI>filter(BLUR) provides a basic blur.
   * <LI>filter(GRAY) converts the image to grayscale based on luminance.
   * <LI>filter(INVERT) will invert the color components in the image.
   * <LI>filter(OPAQUE) set all the high bits in the image to opaque
   * <LI>filter(THRESHOLD) converts the image to black and white.
   * <LI>filter(DILATE) grow white/light areas
   * <LI>filter(ERODE) shrink white/light areas
   * </UL>
   * Luminance conversion code contributed by
   * <A HREF="http://www.toxi.co.uk">toxi</A>
   * <P/>
   * Gaussian blur code contributed by
   * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
   */
  public void filter(int kind) {
    loadPixels();

    switch (kind) {
      case BLUR:
        // TODO write basic low-pass filter blur here
        // what does photoshop do on the edges with this guy?
        // better yet.. why bother? just use gaussian with radius 1
        filter(BLUR, 1);
        break;

      case GRAY:
        if (format == ALPHA) {
          // for an alpha image, convert it to an opaque grayscale
          for (int i = 0; i < pixels.length; i++) {
            int col = 255 - pixels[i];
            pixels[i] = 0xff000000 | (col << 16) | (col << 8) | col;
          }
          format = RGB;

        } else {
          // Converts RGB image data into grayscale using
          // weighted RGB components, and keeps alpha channel intact.
          // [toxi 040115]
          for (int i = 0; i < pixels.length; i++) {
            int col = pixels[i];
            // luminance = 0.3*red + 0.59*green + 0.11*blue
            // 0.30 * 256 =  77
            // 0.59 * 256 = 151
            // 0.11 * 256 =  28
            int lum = (77*(col>>16&0xff) + 151*(col>>8&0xff) + 28*(col&0xff))>>8;
            pixels[i] = (col & ALPHA_MASK) | lum<<16 | lum<<8 | lum;
          }
        }
        break;

      case INVERT:
        for (int i = 0; i < pixels.length; i++) {
          //pixels[i] = 0xff000000 |
          pixels[i] ^= 0xffffff;
        }
        break;

      case POSTERIZE:
        throw new RuntimeException("Use filter(POSTERIZE, int levels) " +
        "instead of filter(POSTERIZE)");

      case RGB:
        for (int i = 0; i < pixels.length; i++) {
          pixels[i] |= 0xff000000;
        }
        format = RGB;
        break;

      case THRESHOLD:
        filter(THRESHOLD, 0.5f);
        break;

        // [toxi20050728] added new filters
      case ERODE:
        dilate(true);
        break;

      case DILATE:
        dilate(false);
        break;
    }
    updatePixels();  // mark as modified
  }


  /**
   * Method to apply a variety of basic filters to this image.
   * These filters all take a parameter.
   * <P>
   * <UL>
   * <LI>filter(BLUR, int radius) performs a gaussian blur of the
   * specified radius.
   * <LI>filter(POSTERIZE, int levels) will posterize the image to
   * between 2 and 255 levels.
   * <LI>filter(THRESHOLD, float center) allows you to set the
   * center point for the threshold. It takes a value from 0 to 1.0.
   * </UL>
   * Gaussian blur code contributed by
   * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
   * and later updated by toxi for better speed.
   */
  public void filter(int kind, float param) {
    loadPixels();

    switch (kind) {
      case BLUR:
        if (format == ALPHA)
          blurAlpha(param);
        else if (format == ARGB)
          blurARGB(param);
        else
          blurRGB(param);
        break;

      case GRAY:
        throw new RuntimeException("Use filter(GRAY) instead of " +
                                   "filter(GRAY, param)");

      case INVERT:
        throw new RuntimeException("Use filter(INVERT) instead of " +
                                   "filter(INVERT, param)");

      case OPAQUE:
        throw new RuntimeException("Use filter(OPAQUE) instead of " +
                                   "filter(OPAQUE, param)");

      case POSTERIZE:
        int levels = (int)param;
        if ((levels < 2) || (levels > 255)) {
          throw new RuntimeException("Levels must be between 2 and 255 for " +
                                     "filter(POSTERIZE, levels)");
        }
        int levels1 = levels - 1;
        for (int i = 0; i < pixels.length; i++) {
          int rlevel = (pixels[i] >> 16) & 0xff;
          int glevel = (pixels[i] >> 8) & 0xff;
          int blevel = pixels[i] & 0xff;
          rlevel = (((rlevel * levels) >> 8) * 255) / levels1;
          glevel = (((glevel * levels) >> 8) * 255) / levels1;
          blevel = (((blevel * levels) >> 8) * 255) / levels1;
          pixels[i] = ((0xff000000 & pixels[i]) |
                       (rlevel << 16) |
                       (glevel << 8) |
                       blevel);
        }
        break;

      case THRESHOLD:  // greater than or equal to the threshold
        int thresh = (int) (param * 255);
        for (int i = 0; i < pixels.length; i++) {
          int max = Math.max((pixels[i] & RED_MASK) >> 16,
                             Math.max((pixels[i] & GREEN_MASK) >> 8,
                                      (pixels[i] & BLUE_MASK)));
          pixels[i] = (pixels[i] & ALPHA_MASK) |
            ((max < thresh) ? 0x000000 : 0xffffff);
        }
        break;

        // [toxi20050728] added new filters
        case ERODE:
          throw new RuntimeException("Use filter(ERODE) instead of " +
                                     "filter(ERODE, param)");
        case DILATE:
          throw new RuntimeException("Use filter(DILATE) instead of " +
                                     "filter(DILATE, param)");
    }
    updatePixels();  // mark as modified
  }


  /**
   * Optimized code for building the blur kernel.
   * further optimized blur code (approx. 15% for radius=20)
   * bigger speed gains for larger radii (~30%)
   * added support for various image types (ALPHA, RGB, ARGB)
   * [toxi 050728]
   */
  protected void buildBlurKernel(float r) {
    int radius = (int) (r * 3.5f);
    radius = (radius < 1) ? 1 : ((radius < 248) ? radius : 248);
    if (blurRadius != radius) {
      blurRadius = radius;
      blurKernelSize = 1 + blurRadius<<1;
      blurKernel = new int[blurKernelSize];
      blurMult = new int[blurKernelSize][256];

      int bk,bki;
      int[] bm,bmi;

      for (int i = 1, radiusi = radius - 1; i < radius; i++) {
        blurKernel[radius+i] = blurKernel[radiusi] = bki = radiusi * radiusi;
        bm=blurMult[radius+i];
        bmi=blurMult[radiusi--];
        for (int j = 0; j < 256; j++)
          bm[j] = bmi[j] = bki*j;
      }
      bk = blurKernel[radius] = radius * radius;
      bm = blurMult[radius];
      for (int j = 0; j < 256; j++)
        bm[j] = bk*j;
    }
  }


  protected void blurAlpha(float r) {
    int sum, cb;
    int read, ri, ym, ymi, bk0;
    int b2[] = new int[pixels.length];
    int yi = 0;

    buildBlurKernel(r);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        //cb = cg = cr = sum = 0;
        cb = sum = 0;
        read = x - blurRadius;
        if (read<0) {
          bk0=-read;
          read=0;
        } else {
          if (read >= width)
            break;
          bk0=0;
        }
        for (int i = bk0; i < blurKernelSize; i++) {
          if (read >= width)
            break;
          int c = pixels[read + yi];
          int[] bm=blurMult[i];
          cb += bm[c & BLUE_MASK];
          sum += blurKernel[i];
          read++;
        }
        ri = yi + x;
        b2[ri] = cb / sum;
      }
      yi += width;
    }

    yi = 0;
    ym=-blurRadius;
    ymi=ym*width;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        //cb = cg = cr = sum = 0;
        cb = sum = 0;
        if (ym<0) {
          bk0 = ri = -ym;
          read = x;
        } else {
          if (ym >= height)
            break;
          bk0 = 0;
          ri = ym;
          read = x + ymi;
        }
        for (int i = bk0; i < blurKernelSize; i++) {
          if (ri >= height)
            break;
          int[] bm=blurMult[i];
          cb += bm[b2[read]];
          sum += blurKernel[i];
          ri++;
          read += width;
        }
        pixels[x+yi] = (cb/sum);
      }
      yi += width;
      ymi += width;
      ym++;
    }
  }


  protected void blurRGB(float r) {
    int sum, cr, cg, cb; //, k;
    int /*pixel,*/ read, ri, /*roff,*/ ym, ymi, /*riw,*/ bk0;
    int r2[] = new int[pixels.length];
    int g2[] = new int[pixels.length];
    int b2[] = new int[pixels.length];
    int yi = 0;

    buildBlurKernel(r);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        cb = cg = cr = sum = 0;
        read = x - blurRadius;
        if (read<0) {
          bk0=-read;
          read=0;
        } else {
          if (read >= width)
            break;
          bk0=0;
        }
        for (int i = bk0; i < blurKernelSize; i++) {
          if (read >= width)
            break;
          int c = pixels[read + yi];
          int[] bm=blurMult[i];
          cr += bm[(c & RED_MASK) >> 16];
          cg += bm[(c & GREEN_MASK) >> 8];
          cb += bm[c & BLUE_MASK];
          sum += blurKernel[i];
          read++;
        }
        ri = yi + x;
        r2[ri] = cr / sum;
        g2[ri] = cg / sum;
        b2[ri] = cb / sum;
      }
      yi += width;
    }

    yi = 0;
    ym=-blurRadius;
    ymi=ym*width;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        cb = cg = cr = sum = 0;
        if (ym<0) {
          bk0 = ri = -ym;
          read = x;
        } else {
          if (ym >= height)
            break;
          bk0 = 0;
          ri = ym;
          read = x + ymi;
        }
        for (int i = bk0; i < blurKernelSize; i++) {
          if (ri >= height)
            break;
          int[] bm=blurMult[i];
          cr += bm[r2[read]];
          cg += bm[g2[read]];
          cb += bm[b2[read]];
          sum += blurKernel[i];
          ri++;
          read += width;
        }
        pixels[x+yi] = 0xff000000 | (cr/sum)<<16 | (cg/sum)<<8 | (cb/sum);
      }
      yi += width;
      ymi += width;
      ym++;
    }
  }


  protected void blurARGB(float r) {
    int sum, cr, cg, cb, ca;
    int /*pixel,*/ read, ri, /*roff,*/ ym, ymi, /*riw,*/ bk0;
    int wh = pixels.length;
    int r2[] = new int[wh];
    int g2[] = new int[wh];
    int b2[] = new int[wh];
    int a2[] = new int[wh];
    int yi = 0;

    buildBlurKernel(r);

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        cb = cg = cr = ca = sum = 0;
        read = x - blurRadius;
        if (read<0) {
          bk0=-read;
          read=0;
        } else {
          if (read >= width)
            break;
          bk0=0;
        }
        for (int i = bk0; i < blurKernelSize; i++) {
          if (read >= width)
            break;
          int c = pixels[read + yi];
          int[] bm=blurMult[i];
          ca += bm[(c & ALPHA_MASK) >>> 24];
          cr += bm[(c & RED_MASK) >> 16];
          cg += bm[(c & GREEN_MASK) >> 8];
          cb += bm[c & BLUE_MASK];
          sum += blurKernel[i];
          read++;
        }
        ri = yi + x;
        a2[ri] = ca / sum;
        r2[ri] = cr / sum;
        g2[ri] = cg / sum;
        b2[ri] = cb / sum;
      }
      yi += width;
    }

    yi = 0;
    ym=-blurRadius;
    ymi=ym*width;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        cb = cg = cr = ca = sum = 0;
        if (ym<0) {
          bk0 = ri = -ym;
          read = x;
        } else {
          if (ym >= height)
            break;
          bk0 = 0;
          ri = ym;
          read = x + ymi;
        }
        for (int i = bk0; i < blurKernelSize; i++) {
          if (ri >= height)
            break;
          int[] bm=blurMult[i];
          ca += bm[a2[read]];
          cr += bm[r2[read]];
          cg += bm[g2[read]];
          cb += bm[b2[read]];
          sum += blurKernel[i];
          ri++;
          read += width;
        }
        pixels[x+yi] = (ca/sum)<<24 | (cr/sum)<<16 | (cg/sum)<<8 | (cb/sum);
      }
      yi += width;
      ymi += width;
      ym++;
    }
  }


  /**
   * Generic dilate/erode filter using luminance values
   * as decision factor. [toxi 050728]
   */
  protected void dilate(boolean isInverted) {
    int currIdx=0;
    int maxIdx=pixels.length;
    int[] out=new int[maxIdx];

    if (!isInverted) {
      // erosion (grow light areas)
      while (currIdx<maxIdx) {
        int currRowIdx=currIdx;
        int maxRowIdx=currIdx+width;
        while (currIdx<maxRowIdx) {
          int colOrig,colOut;
          colOrig=colOut=pixels[currIdx];
          int idxLeft=currIdx-1;
          int idxRight=currIdx+1;
          int idxUp=currIdx-width;
          int idxDown=currIdx+width;
          if (idxLeft<currRowIdx)
            idxLeft=currIdx;
          if (idxRight>=maxRowIdx)
            idxRight=currIdx;
          if (idxUp<0)
            idxUp=0;
          if (idxDown>=maxIdx)
            idxDown=currIdx;

          int colUp=pixels[idxUp];
          int colLeft=pixels[idxLeft];
          int colDown=pixels[idxDown];
          int colRight=pixels[idxRight];

          // compute luminance
          int currLum =
            77*(colOrig>>16&0xff) + 151*(colOrig>>8&0xff) + 28*(colOrig&0xff);
          int lumLeft =
            77*(colLeft>>16&0xff) + 151*(colLeft>>8&0xff) + 28*(colLeft&0xff);
          int lumRight =
            77*(colRight>>16&0xff) + 151*(colRight>>8&0xff) + 28*(colRight&0xff);
          int lumUp =
            77*(colUp>>16&0xff) + 151*(colUp>>8&0xff) + 28*(colUp&0xff);
          int lumDown =
            77*(colDown>>16&0xff) + 151*(colDown>>8&0xff) + 28*(colDown&0xff);

          if (lumLeft>currLum) {
            colOut=colLeft;
            currLum=lumLeft;
          }
          if (lumRight>currLum) {
            colOut=colRight;
            currLum=lumRight;
          }
          if (lumUp>currLum) {
            colOut=colUp;
            currLum=lumUp;
          }
          if (lumDown>currLum) {
            colOut=colDown;
            currLum=lumDown;
          }
          out[currIdx++]=colOut;
        }
      }
    } else {
      // dilate (grow dark areas)
      while (currIdx<maxIdx) {
        int currRowIdx=currIdx;
        int maxRowIdx=currIdx+width;
        while (currIdx<maxRowIdx) {
          int colOrig,colOut;
          colOrig=colOut=pixels[currIdx];
          int idxLeft=currIdx-1;
          int idxRight=currIdx+1;
          int idxUp=currIdx-width;
          int idxDown=currIdx+width;
          if (idxLeft<currRowIdx)
            idxLeft=currIdx;
          if (idxRight>=maxRowIdx)
            idxRight=currIdx;
          if (idxUp<0)
            idxUp=0;
          if (idxDown>=maxIdx)
            idxDown=currIdx;

          int colUp=pixels[idxUp];
          int colLeft=pixels[idxLeft];
          int colDown=pixels[idxDown];
          int colRight=pixels[idxRight];

          // compute luminance
          int currLum =
            77*(colOrig>>16&0xff) + 151*(colOrig>>8&0xff) + 28*(colOrig&0xff);
          int lumLeft =
            77*(colLeft>>16&0xff) + 151*(colLeft>>8&0xff) + 28*(colLeft&0xff);
          int lumRight =
            77*(colRight>>16&0xff) + 151*(colRight>>8&0xff) + 28*(colRight&0xff);
          int lumUp =
            77*(colUp>>16&0xff) + 151*(colUp>>8&0xff) + 28*(colUp&0xff);
          int lumDown =
            77*(colDown>>16&0xff) + 151*(colDown>>8&0xff) + 28*(colDown&0xff);

          if (lumLeft<currLum) {
            colOut=colLeft;
            currLum=lumLeft;
          }
          if (lumRight<currLum) {
            colOut=colRight;
            currLum=lumRight;
          }
          if (lumUp<currLum) {
            colOut=colUp;
            currLum=lumUp;
          }
          if (lumDown<currLum) {
            colOut=colDown;
            currLum=lumDown;
          }
          out[currIdx++]=colOut;
        }
      }
    }
    System.arraycopy(out,0,pixels,0,maxIdx);
  }



  //////////////////////////////////////////////////////////////

  // COPY


  /**
   * Copy things from one area of this image
   * to another area in the same image.
   */
  public void copy(int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    blend(this, sx, sy, sw, sh, dx, dy, dw, dh, REPLACE);
  }


  /**
   * Copies area of one image into another PImage object.
   */
  public void copy(PImage src,
                   int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    blend(src, sx, sy, sw, sh, dx, dy, dw, dh, REPLACE);
  }



  //////////////////////////////////////////////////////////////

  // BLEND


  /**
   * Blend two colors based on a particular mode.
   * <UL>
   * <LI>REPLACE - destination colour equals colour of source pixel: C = A.
   *     Sometimes called "Normal" or "Copy" in other software.
   *
   * <LI>BLEND - linear interpolation of colours:
   *     <TT>C = A*factor + B</TT>
   *
   * <LI>ADD - additive blending with white clip:
   *     <TT>C = min(A*factor + B, 255)</TT>.
   *     Clipped to 0..255, Photoshop calls this "Linear Burn",
   *     and Director calls it "Add Pin".
   *
   * <LI>SUBTRACT - substractive blend with black clip:
   *     <TT>C = max(B - A*factor, 0)</TT>.
   *     Clipped to 0..255, Photoshop calls this "Linear Dodge",
   *     and Director calls it "Subtract Pin".
   *
   * <LI>DARKEST - only the darkest colour succeeds:
   *     <TT>C = min(A*factor, B)</TT>.
   *     Illustrator calls this "Darken".
   *
   * <LI>LIGHTEST - only the lightest colour succeeds:
   *     <TT>C = max(A*factor, B)</TT>.
   *     Illustrator calls this "Lighten".
   *
   * <LI>DIFFERENCE - subtract colors from underlying image.
   *
   * <LI>EXCLUSION - similar to DIFFERENCE, but less extreme.
   *
   * <LI>MULTIPLY - Multiply the colors, result will always be darker.
   *
   * <LI>SCREEN - Opposite multiply, uses inverse values of the colors.
   *
   * <LI>OVERLAY - A mix of MULTIPLY and SCREEN. Multiplies dark values,
   *     and screens light values.
   *
   * <LI>HARD_LIGHT - SCREEN when greater than 50% gray, MULTIPLY when lower.
   *
   * <LI>SOFT_LIGHT - Mix of DARKEST and LIGHTEST.
   *     Works like OVERLAY, but not as harsh.
   *
   * <LI>DODGE - Lightens light tones and increases contrast, ignores darks.
   *     Called "Color Dodge" in Illustrator and Photoshop.
   *
   * <LI>BURN - Darker areas are applied, increasing contrast, ignores lights.
   *     Called "Color Burn" in Illustrator and Photoshop.
   * </UL>
   * <P>A useful reference for blending modes and their algorithms can be
   * found in the <A HREF="http://www.w3.org/TR/SVG12/rendering.html">SVG</A>
   * specification.</P>
   * <P>It is important to note that Processing uses "fast" code, not
   * necessarily "correct" code. No biggie, most software does. A nitpicker
   * can find numerous "off by 1 division" problems in the blend code where
   * <TT>&gt;&gt;8</TT> or <TT>&gt;&gt;7</TT> is used when strictly speaking
   * <TT>/255.0</T> or <TT>/127.0</TT> should have been used.</P>
   * <P>For instance, exclusion (not intended for real-time use) reads
   * <TT>r1 + r2 - ((2 * r1 * r2) / 255)</TT> because <TT>255 == 1.0</TT>
   * not <TT>256 == 1.0</TT>. In other words, <TT>(255*255)>>8</TT> is not
   * the same as <TT>(255*255)/255</TT>. But for real-time use the shifts
   * are preferrable, and the difference is insignificant for applications
   * built with Processing.</P>
   */
  static public int blendColor(int c1, int c2, int mode) {  // ignore
    switch (mode) {
    case REPLACE:    return c2;
    case BLEND:      return blend_blend(c1, c2);

    case ADD:        return blend_add_pin(c1, c2);
    case SUBTRACT:   return blend_sub_pin(c1, c2);

    case LIGHTEST:   return blend_lightest(c1, c2);
    case DARKEST:    return blend_darkest(c1, c2);

    case DIFFERENCE: return blend_difference(c1, c2);
    case EXCLUSION:  return blend_exclusion(c1, c2);

    case MULTIPLY:   return blend_multiply(c1, c2);
    case SCREEN:     return blend_screen(c1, c2);

    case HARD_LIGHT: return blend_hard_light(c1, c2);
    case SOFT_LIGHT: return blend_soft_light(c1, c2);
    case OVERLAY:    return blend_overlay(c1, c2);

    case DODGE:      return blend_dodge(c1, c2);
    case BURN:       return blend_burn(c1, c2);
    }
    return 0;
  }


  /**
   * Blends one area of this image to another area.
   * @see processing.core.PImage#blendColor(int,int,int)
   */
  public void blend(int sx, int sy, int sw, int sh,
                    int dx, int dy, int dw, int dh, int mode) {
    blend(this, sx, sy, sw, sh, dx, dy, dw, dh, mode);
  }


  /**
   * Copies area of one image into another PImage object.
   * @see processing.core.PImage#blendColor(int,int,int)
   */
  public void blend(PImage src,
                    int sx, int sy, int sw, int sh,
                    int dx, int dy, int dw, int dh, int mode) {
    int sx2 = sx + sw;
    int sy2 = sy + sh;
    int dx2 = dx + dw;
    int dy2 = dy + dh;

    loadPixels();
    if (src == this) {
      if (intersect(sx, sy, sx2, sy2, dx, dy, dx2, dy2)) {
        blit_resize(get(sx, sy, sw, sh),
                    0, 0, sw, sh,
                    pixels, pixelWidth, pixelHeight, dx, dy, dx2, dy2, mode);
      } else {
        // same as below, except skip the loadPixels() because it'd be redundant
        blit_resize(src, sx, sy, sx2, sy2,
                    pixels, pixelWidth, pixelHeight, dx, dy, dx2, dy2, mode);
      }
    } else {
      src.loadPixels();
      blit_resize(src, sx, sy, sx2, sy2,
                  pixels, pixelWidth, pixelHeight, dx, dy, dx2, dy2, mode);
      //src.updatePixels();
    }
    updatePixels();
  }


  /**
   * Check to see if two rectangles intersect one another
   */
  private boolean intersect(int sx1, int sy1, int sx2, int sy2,
                            int dx1, int dy1, int dx2, int dy2) {
    int sw = sx2 - sx1 + 1;
    int sh = sy2 - sy1 + 1;
    int dw = dx2 - dx1 + 1;
    int dh = dy2 - dy1 + 1;

    if (dx1 < sx1) {
      dw += dx1 - sx1;
      if (dw > sw) {
        dw = sw;
      }
    } else {
      int w = sw + sx1 - dx1;
      if (dw > w) {
        dw = w;
      }
    }
    if (dy1 < sy1) {
      dh += dy1 - sy1;
      if (dh > sh) {
        dh = sh;
      }
    } else {
      int h = sh + sy1 - dy1;
      if (dh > h) {
        dh = h;
      }
    }
    return !(dw <= 0 || dh <= 0);
  }


  //////////////////////////////////////////////////////////////


  /**
   * Internal blitter/resizer/copier from toxi.
   * Uses bilinear filtering if smooth() has been enabled
   * 'mode' determines the blending mode used in the process.
   */
  private void blit_resize(PImage img,
                           int srcX1, int srcY1, int srcX2, int srcY2,
                           int[] destPixels, int screenW, int screenH,
                           int destX1, int destY1, int destX2, int destY2,
                           int mode) {
    if (srcX1 < 0) srcX1 = 0;
    if (srcY1 < 0) srcY1 = 0;
    if (srcX2 > img.pixelWidth) srcX2 = img.pixelWidth;
    if (srcY2 > img.pixelHeight) srcY2 = img.pixelHeight;

    int srcW = srcX2 - srcX1;
    int srcH = srcY2 - srcY1;
    int destW = destX2 - destX1;
    int destH = destY2 - destY1;

    boolean smooth = true;  // may as well go with the smoothing these days

    if (!smooth) {
      srcW++; srcH++;
    }

    if (destW <= 0 || destH <= 0 ||
        srcW <= 0 || srcH <= 0 ||
        destX1 >= screenW || destY1 >= screenH ||
        srcX1 >= img.pixelWidth || srcY1 >= img.pixelHeight) {
      return;
    }

    int dx = (int) (srcW / (float) destW * PRECISIONF);
    int dy = (int) (srcH / (float) destH * PRECISIONF);

    srcXOffset = destX1 < 0 ? -destX1 * dx : srcX1 * PRECISIONF;
    srcYOffset = destY1 < 0 ? -destY1 * dy : srcY1 * PRECISIONF;

    if (destX1 < 0) {
      destW += destX1;
      destX1 = 0;
    }
    if (destY1 < 0) {
      destH += destY1;
      destY1 = 0;
    }

    destW = min(destW, screenW - destX1);
    destH = min(destH, screenH - destY1);

    int destOffset = destY1 * screenW + destX1;
    srcBuffer = img.pixels;

    if (smooth) {
      // use bilinear filtering
      iw = img.pixelWidth;
      iw1 = img.pixelWidth - 1;
      ih1 = img.pixelHeight - 1;

      switch (mode) {

      case BLEND:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            // davbol  - renamed old blend_multiply to blend_blend
            destPixels[destOffset + x] =
              blend_blend(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case ADD:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_add_pin(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SUBTRACT:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_sub_pin(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case LIGHTEST:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_lightest(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case DARKEST:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_darkest(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case REPLACE:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] = filter_bilinear();
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case DIFFERENCE:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_difference(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case EXCLUSION:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_exclusion(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case MULTIPLY:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_multiply(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SCREEN:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_screen(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case OVERLAY:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_overlay(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case HARD_LIGHT:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_hard_light(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SOFT_LIGHT:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_soft_light(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      // davbol - proposed 2007-01-09
      case DODGE:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_dodge(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case BURN:
        for (int y = 0; y < destH; y++) {
          filter_new_scanline();
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_burn(destPixels[destOffset + x], filter_bilinear());
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      }

    } else {
      // nearest neighbour scaling (++fast!)
      switch (mode) {

      case BLEND:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            // davbol - renamed old blend_multiply to blend_blend
            destPixels[destOffset + x] =
              blend_blend(destPixels[destOffset + x],
                          srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case ADD:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_add_pin(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SUBTRACT:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_sub_pin(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case LIGHTEST:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_lightest(destPixels[destOffset + x],
                             srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case DARKEST:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_darkest(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case REPLACE:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] = srcBuffer[sY + (sX >> PRECISIONB)];
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case DIFFERENCE:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_difference(destPixels[destOffset + x],
                               srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case EXCLUSION:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_exclusion(destPixels[destOffset + x],
                              srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case MULTIPLY:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_multiply(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SCREEN:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_screen(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case OVERLAY:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_overlay(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case HARD_LIGHT:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_hard_light(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case SOFT_LIGHT:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_soft_light(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      // davbol - proposed 2007-01-09
      case DODGE:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_dodge(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      case BURN:
        for (int y = 0; y < destH; y++) {
          sX = srcXOffset;
          sY = (srcYOffset >> PRECISIONB) * img.pixelWidth;
          for (int x = 0; x < destW; x++) {
            destPixels[destOffset + x] =
              blend_burn(destPixels[destOffset + x],
                            srcBuffer[sY + (sX >> PRECISIONB)]);
            sX += dx;
          }
          destOffset += screenW;
          srcYOffset += dy;
        }
        break;

      }
    }
  }


  private void filter_new_scanline() {
    sX = srcXOffset;
    fracV = srcYOffset & PREC_MAXVAL;
    ifV = PREC_MAXVAL - fracV + 1;
    v1 = (srcYOffset >> PRECISIONB) * iw;
    v2 = min((srcYOffset >> PRECISIONB) + 1, ih1) * iw;
  }


  private int filter_bilinear() {
    fracU = sX & PREC_MAXVAL;
    ifU = PREC_MAXVAL - fracU + 1;
    ul = (ifU * ifV) >> PRECISIONB;
    ll = ifU - ul;
    ur = ifV - ul;
    lr = PREC_MAXVAL + 1 - ul - ll - ur;
    u1 = (sX >> PRECISIONB);
    u2 = min(u1 + 1, iw1);

    // get color values of the 4 neighbouring texels
    cUL = srcBuffer[v1 + u1];
    cUR = srcBuffer[v1 + u2];
    cLL = srcBuffer[v2 + u1];
    cLR = srcBuffer[v2 + u2];

    r = ((ul*((cUL&RED_MASK)>>16) + ll*((cLL&RED_MASK)>>16) +
          ur*((cUR&RED_MASK)>>16) + lr*((cLR&RED_MASK)>>16))
         << PREC_RED_SHIFT) & RED_MASK;

    g = ((ul*(cUL&GREEN_MASK) + ll*(cLL&GREEN_MASK) +
          ur*(cUR&GREEN_MASK) + lr*(cLR&GREEN_MASK))
         >>> PRECISIONB) & GREEN_MASK;

    b = (ul*(cUL&BLUE_MASK) + ll*(cLL&BLUE_MASK) +
         ur*(cUR&BLUE_MASK) + lr*(cLR&BLUE_MASK))
           >>> PRECISIONB;

    a = ((ul*((cUL&ALPHA_MASK)>>>24) + ll*((cLL&ALPHA_MASK)>>>24) +
          ur*((cUR&ALPHA_MASK)>>>24) + lr*((cLR&ALPHA_MASK)>>>24))
         << PREC_ALPHA_SHIFT) & ALPHA_MASK;

    return a | r | g | b;
  }



  //////////////////////////////////////////////////////////////

  // internal blending methods


  private static int min(int a, int b) {
    return (a < b) ? a : b;
  }


  private static int max(int a, int b) {
    return (a > b) ? a : b;
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

  private static final int RB_MASK = 0x00FF00FF;
  private static final int GN_MASK = 0x0000FF00;

  /**
   * Blend
   * O = S
   */
  private static int blend_blend(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + (src & RB_MASK) * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + (src & GN_MASK) * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Add
   * O = MIN(D + S, 1)
   */
  private static int blend_add_pin(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);

    int rb = (dst & RB_MASK) + ((src & RB_MASK) * s_a >>> 8 & RB_MASK);
    int gn = (dst & GN_MASK) + ((src & GN_MASK) * s_a >>> 8);

    return min((dst >>> 24) + a, 0xFF) << 24 |
        min(rb & 0xFFFF0000, RED_MASK)   |
        min(gn & 0x00FFFF00, GREEN_MASK) |
        min(rb & 0x0000FFFF, BLUE_MASK);
  }


  /**
   * Subtract
   * O = MAX(0, D - S)
   */
  private static int blend_sub_pin(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);

    int rb = ((src & RB_MASK)    * s_a >>> 8);
    int gn = ((src & GREEN_MASK) * s_a >>> 8);

    return min((dst >>> 24) + a, 0xFF) << 24 |
        max((dst & RED_MASK)   - (rb & RED_MASK), 0) |
        max((dst & GREEN_MASK) - (gn & GREEN_MASK), 0) |
        max((dst & BLUE_MASK)  - (rb & BLUE_MASK), 0);
  }


  /**
   * Lightest
   * O = MAX(D, S)
   */
  private static int blend_lightest(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int rb = max(src & RED_MASK,   dst & RED_MASK) |
             max(src & BLUE_MASK,  dst & BLUE_MASK);
    int gn = max(src & GREEN_MASK, dst & GREEN_MASK);

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + rb * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + gn * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Darkest
   * O = MIN(D, S)
   */
  private static int blend_darkest(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int rb = min(src & RED_MASK,   dst & RED_MASK) |
             min(src & BLUE_MASK,  dst & BLUE_MASK);
    int gn = min(src & GREEN_MASK, dst & GREEN_MASK);

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + rb * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + gn * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Difference
   * O = ABS(D - S)
   */
  private static int blend_difference(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int r = (dst & RED_MASK)   - (src & RED_MASK);
    int b = (dst & BLUE_MASK)  - (src & BLUE_MASK);
    int g = (dst & GREEN_MASK) - (src & GREEN_MASK);

    int rb = (r < 0 ? -r : r) |
             (b < 0 ? -b : b);
    int gn = (g < 0 ? -g : g);

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + rb * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + gn * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Exclusion
   * O = (1 - S)D + S(1 - D)
   * O = D + S - 2DS
   */
  private static int blend_exclusion(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int d_rb = dst & RB_MASK;
    int d_gn = dst & GN_MASK;

    int s_gn = src & GN_MASK;

    int f_r = (dst & RED_MASK) >> 16;
    int f_b = (dst & BLUE_MASK);

    int rb_sub =
        ((src & RED_MASK) * (f_r + (f_r >= 0x7F ? 1 : 0)) |
        (src & BLUE_MASK) * (f_b + (f_b >= 0x7F ? 1 : 0)))
        >>> 7 & 0x01FF01FF;
    int gn_sub = s_gn * (d_gn + (d_gn >= 0x7F00 ? 0x100 : 0))
        >>> 15 & 0x0001FF00;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        (d_rb * d_a + (d_rb + (src & RB_MASK) - rb_sub) * s_a) >>> 8 & RB_MASK |
        (d_gn * d_a + (d_gn + s_gn            - gn_sub) * s_a) >>> 8 & GN_MASK;
  }


  /*
   * Multiply
   * O = DS
   */
  private static int blend_multiply(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int d_gn = dst & GN_MASK;

    int f_r = (dst & RED_MASK) >> 16;
    int f_b = (dst & BLUE_MASK);

    int rb =
        ((src & RED_MASK)  * (f_r + 1) |
        (src & BLUE_MASK)  * (f_b + 1))
        >>>  8 & RB_MASK;
    int gn =
        (src & GREEN_MASK) * (d_gn + 0x100)
        >>> 16 & GN_MASK;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + rb * s_a) >>> 8 & RB_MASK |
        (d_gn            * d_a + gn * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Screen
   * O = 1 - (1 - D)(1 - S)
   * O = D + S - DS
   */
  private static int blend_screen(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int d_rb = dst & RB_MASK;
    int d_gn = dst & GN_MASK;

    int s_gn = src & GN_MASK;

    int f_r = (dst & RED_MASK) >> 16;
    int f_b = (dst & BLUE_MASK);

    int rb_sub =
        ((src & RED_MASK) * (f_r + 1) |
        (src & BLUE_MASK) * (f_b + 1))
        >>>  8 & RB_MASK;
    int gn_sub = s_gn * (d_gn + 0x100)
        >>> 16 & GN_MASK;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        (d_rb * d_a + (d_rb + (src & RB_MASK) - rb_sub) * s_a) >>> 8 & RB_MASK |
        (d_gn * d_a + (d_gn + s_gn            - gn_sub) * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Overlay
   * O = 2 * MULTIPLY(D, S) = 2DS                   for D < 0.5
   * O = 2 * SCREEN(D, S) - 1 = 2(S + D - DS) - 1   otherwise
   */
  private static int blend_overlay(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int d_r = dst & RED_MASK;
    int d_g = dst & GREEN_MASK;
    int d_b = dst & BLUE_MASK;

    int s_r = src & RED_MASK;
    int s_g = src & GREEN_MASK;
    int s_b = src & BLUE_MASK;

    int r = (d_r < 0x800000) ?
        d_r * ((s_r >>> 16) + 1) >>> 7 :
        0xFF0000 - ((0x100 - (s_r >>> 16)) * (RED_MASK - d_r) >>> 7);
    int g = (d_g < 0x8000) ?
        d_g * (s_g + 0x100) >>> 15 :
        (0xFF00 - ((0x10000 - s_g) * (GREEN_MASK - d_g) >>> 15));
    int b = (d_b < 0x80) ?
        d_b * (s_b + 1) >>> 7 :
        (0xFF00 - ((0x100 - s_b) * (BLUE_MASK - d_b) << 1)) >>> 8;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + ((r | b) & RB_MASK) * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + (g       & GN_MASK) * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Hard Light
   * O = OVERLAY(S, D)
   *
   * O = 2 * MULTIPLY(D, S) = 2DS                   for S < 0.5
   * O = 2 * SCREEN(D, S) - 1 = 2(S + D - DS) - 1   otherwise
   */
  private static int blend_hard_light(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int d_r = dst & RED_MASK;
    int d_g = dst & GREEN_MASK;
    int d_b = dst & BLUE_MASK;

    int s_r = src & RED_MASK;
    int s_g = src & GREEN_MASK;
    int s_b = src & BLUE_MASK;

    int r = (s_r < 0x800000) ?
        s_r * ((d_r >>> 16) + 1) >>> 7 :
        0xFF0000 - ((0x100 - (d_r >>> 16)) * (RED_MASK - s_r) >>> 7);
    int g = (s_g < 0x8000) ?
        s_g * (d_g + 0x100) >>> 15 :
        (0xFF00 - ((0x10000 - d_g) * (GREEN_MASK - s_g) >>> 15));
    int b = (s_b < 0x80) ?
        s_b * (d_b + 1) >>> 7 :
        (0xFF00 - ((0x100 - d_b) * (BLUE_MASK - s_b) << 1)) >>> 8;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + ((r | b) & RB_MASK) * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + (g       & GN_MASK) * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Soft Light (Pegtop)
   * O = (1 - D) * MULTIPLY(D, S) + D * SCREEN(D, S)
   * O = (1 - D) * DS + D * (1 - (1 - D)(1 - S))
   * O = 2DS + DD - 2DDS
   */
  private static int blend_soft_light(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int d_r = dst & RED_MASK;
    int d_g = dst & GREEN_MASK;
    int d_b = dst & BLUE_MASK;

    int s_r1 = src & RED_MASK >> 16;
    int s_g1 = src & GREEN_MASK >> 8;
    int s_b1 = src & BLUE_MASK;

    int d_r1 = (d_r >> 16) + (s_r1 < 7F ? 1 : 0);
    int d_g1 = (d_g >> 8)  + (s_g1 < 7F ? 1 : 0);
    int d_b1 = d_b         + (s_b1 < 7F ? 1 : 0);

    int r = (s_r1 * d_r >> 7) + 0xFF * d_r1 * (d_r1 + 1) -
        ((s_r1 * d_r1 * d_r1) << 1) & RED_MASK;
    int g = (s_g1 * d_g << 1) + 0xFF * d_g1 * (d_g1 + 1) -
        ((s_g1 * d_g1 * d_g1) << 1) >>> 8 & GREEN_MASK;
    int b = (s_b1 * d_b << 9) + 0xFF * d_b1 * (d_b1 + 1) -
        ((s_b1 * d_b1 * d_b1) << 1) >>> 16;

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + (r | b) * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + g       * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Dodge
   * O = D / (1 - S)
   */
  private static int blend_dodge(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int r = (dst & RED_MASK)          / (256 - ((src & RED_MASK) >> 16));
    int g = ((dst & GREEN_MASK) << 8) / (256 - ((src & GREEN_MASK) >> 8));
    int b = ((dst & BLUE_MASK)  << 8) / (256 - (src & BLUE_MASK));

    int rb =
        (r > 0xFF00 ? 0xFF0000 : ((r << 8) & RED_MASK)) |
        (b > 0x00FF ? 0x0000FF : b);
    int gn =
        (g > 0xFF00 ? 0x00FF00 : (g & GREEN_MASK));

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + rb * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + gn * s_a) >>> 8 & GN_MASK;
  }


  /**
   * Burn
   * O = 1 - (1 - A) / B
   */
  private static int blend_burn(int dst, int src) {
    int a = src >>> 24;

    int s_a = a + (a >= 0x7F ? 1 : 0);
    int d_a = 0x100 - s_a;

    int r = ((0xFF0000 - (dst & RED_MASK)))        / (1 + (src & RED_MASK >> 16));
    int g = ((0x00FF00 - (dst & GREEN_MASK)) << 8) / (1 + (src & GREEN_MASK >> 8));
    int b = ((0x0000FF - (dst & BLUE_MASK))  << 8) / (1 + (src & BLUE_MASK));

    int rb = RB_MASK -
        (r > 0xFF00 ? 0xFF0000 : ((r << 8) & RED_MASK)) -
        (b > 0x00FF ? 0x0000FF : b);
    int gn = GN_MASK -
        (g > 0xFF00 ? 0x00FF00 : (g & GREEN_MASK));

    return min((dst >>> 24) + a, 0xFF) << 24 |
        ((dst & RB_MASK) * d_a + rb * s_a) >>> 8 & RB_MASK |
        ((dst & GN_MASK) * d_a + gn * s_a) >>> 8 & GN_MASK;
  }


  //////////////////////////////////////////////////////////////

  // FILE I/O


  static byte TIFF_HEADER[] = {
    77, 77, 0, 42, 0, 0, 0, 8, 0, 9, 0, -2, 0, 4, 0, 0, 0, 1, 0, 0,
    0, 0, 1, 0, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 3, 0, 0, 0, 1,
    0, 0, 0, 0, 1, 2, 0, 3, 0, 0, 0, 3, 0, 0, 0, 122, 1, 6, 0, 3, 0,
    0, 0, 1, 0, 2, 0, 0, 1, 17, 0, 4, 0, 0, 0, 1, 0, 0, 3, 0, 1, 21,
    0, 3, 0, 0, 0, 1, 0, 3, 0, 0, 1, 22, 0, 3, 0, 0, 0, 1, 0, 0, 0, 0,
    1, 23, 0, 4, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 0, 8, 0, 8
  };


  static final String TIFF_ERROR =
    "Error: Processing can only read its own TIFF files.";

  static protected PImage loadTIFF(byte tiff[]) {
    if ((tiff[42] != tiff[102]) ||  // width/height in both places
        (tiff[43] != tiff[103])) {
      System.err.println(TIFF_ERROR);
      return null;
    }

    int width =
      ((tiff[30] & 0xff) << 8) | (tiff[31] & 0xff);
    int height =
      ((tiff[42] & 0xff) << 8) | (tiff[43] & 0xff);

    int count =
      ((tiff[114] & 0xff) << 24) |
      ((tiff[115] & 0xff) << 16) |
      ((tiff[116] & 0xff) << 8) |
      (tiff[117] & 0xff);
    if (count != width * height * 3) {
      System.err.println(TIFF_ERROR + " (" + width + ", " + height +")");
      return null;
    }

    // check the rest of the header
    for (int i = 0; i < TIFF_HEADER.length; i++) {
      if ((i == 30) || (i == 31) || (i == 42) || (i == 43) ||
          (i == 102) || (i == 103) ||
          (i == 114) || (i == 115) || (i == 116) || (i == 117)) continue;

      if (tiff[i] != TIFF_HEADER[i]) {
        System.err.println(TIFF_ERROR + " (" + i + ")");
        return null;
      }
    }

    PImage outgoing = new PImage(width, height, RGB);

    // Not possible because this method is static, so careful when using it.
    // outgoing.parent = parent;

    int index = 768;
    count /= 3;
    for (int i = 0; i < count; i++) {
      outgoing.pixels[i] =
        0xFF000000 |
        (tiff[index++] & 0xff) << 16 |
        (tiff[index++] & 0xff) << 8 |
        (tiff[index++] & 0xff);
    }
    return outgoing;
  }


  protected boolean saveTIFF(OutputStream output) {
    // shutting off the warning, people can figure this out themselves
    /*
    if (format != RGB) {
      System.err.println("Warning: only RGB information is saved with " +
                         ".tif files. Use .tga or .png for ARGB images and others.");
    }
    */
    try {
      byte tiff[] = new byte[768];
      System.arraycopy(TIFF_HEADER, 0, tiff, 0, TIFF_HEADER.length);

      tiff[30] = (byte) ((width >> 8) & 0xff);
      tiff[31] = (byte) ((width) & 0xff);
      tiff[42] = tiff[102] = (byte) ((height >> 8) & 0xff);
      tiff[43] = tiff[103] = (byte) ((height) & 0xff);

      int count = width*height*3;
      tiff[114] = (byte) ((count >> 24) & 0xff);
      tiff[115] = (byte) ((count >> 16) & 0xff);
      tiff[116] = (byte) ((count >> 8) & 0xff);
      tiff[117] = (byte) ((count) & 0xff);

      // spew the header to the disk
      output.write(tiff);

      for (int i = 0; i < pixels.length; i++) {
        output.write((pixels[i] >> 16) & 0xff);
        output.write((pixels[i] >> 8) & 0xff);
        output.write(pixels[i] & 0xff);
      }
      output.flush();
      return true;

    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }


  /**
   * Creates a Targa32 formatted byte sequence of specified
   * pixel buffer using RLE compression.
   * </p>
   * Also figured out how to avoid parsing the image upside-down
   * (there's a header flag to set the image origin to top-left)
   * </p>
   * Starting with revision 0092, the format setting is taken into account:
   * <UL>
   * <LI><TT>ALPHA</TT> images written as 8bit grayscale (uses lowest byte)
   * <LI><TT>RGB</TT> &rarr; 24 bits
   * <LI><TT>ARGB</TT> &rarr; 32 bits
   * </UL>
   * All versions are RLE compressed.
   * </p>
   * Contributed by toxi 8-10 May 2005, based on this RLE
   * <A HREF="http://www.wotsit.org/download.asp?f=tga">specification</A>
   */
  protected boolean saveTGA(OutputStream output) {
    byte header[] = new byte[18];

     if (format == ALPHA) {  // save ALPHA images as 8bit grayscale
       header[2] = 0x0B;
       header[16] = 0x08;
       header[17] = 0x28;

     } else if (format == RGB) {
       header[2] = 0x0A;
       header[16] = 24;
       header[17] = 0x20;

     } else if (format == ARGB) {
       header[2] = 0x0A;
       header[16] = 32;
       header[17] = 0x28;

     } else {
       throw new RuntimeException("Image format not recognized inside save()");
     }
     // set image dimensions lo-hi byte order
     header[12] = (byte) (width & 0xff);
     header[13] = (byte) (width >> 8);
     header[14] = (byte) (height & 0xff);
     header[15] = (byte) (height >> 8);

     try {
       output.write(header);

       int maxLen = height * width;
       int index = 0;
       int col; //, prevCol;
       int[] currChunk = new int[128];

       // 8bit image exporter is in separate loop
       // to avoid excessive conditionals...
       if (format == ALPHA) {
         while (index < maxLen) {
           boolean isRLE = false;
           int rle = 1;
           currChunk[0] = col = pixels[index] & 0xff;
           while (index + rle < maxLen) {
             if (col != (pixels[index + rle]&0xff) || rle == 128) {
               isRLE = (rle > 1);
               break;
             }
             rle++;
           }
           if (isRLE) {
             output.write(0x80 | (rle - 1));
             output.write(col);

           } else {
             rle = 1;
             while (index + rle < maxLen) {
               int cscan = pixels[index + rle] & 0xff;
               if ((col != cscan && rle < 128) || rle < 3) {
                 currChunk[rle] = col = cscan;
               } else {
                 if (col == cscan) rle -= 2;
                 break;
               }
               rle++;
             }
             output.write(rle - 1);
             for (int i = 0; i < rle; i++) output.write(currChunk[i]);
           }
           index += rle;
         }
       } else {  // export 24/32 bit TARGA
         while (index < maxLen) {
           boolean isRLE = false;
           currChunk[0] = col = pixels[index];
           int rle = 1;
           // try to find repeating bytes (min. len = 2 pixels)
           // maximum chunk size is 128 pixels
           while (index + rle < maxLen) {
             if (col != pixels[index + rle] || rle == 128) {
               isRLE = (rle > 1); // set flag for RLE chunk
               break;
             }
             rle++;
           }
           if (isRLE) {
             output.write(128 | (rle - 1));
             output.write(col & 0xff);
             output.write(col >> 8 & 0xff);
             output.write(col >> 16 & 0xff);
             if (format == ARGB) output.write(col >>> 24 & 0xff);

           } else {  // not RLE
             rle = 1;
             while (index + rle < maxLen) {
               if ((col != pixels[index + rle] && rle < 128) || rle < 3) {
                 currChunk[rle] = col = pixels[index + rle];
               } else {
                 // check if the exit condition was the start of
                 // a repeating colour
                 if (col == pixels[index + rle]) rle -= 2;
                 break;
               }
               rle++;
             }
             // write uncompressed chunk
             output.write(rle - 1);
             if (format == ARGB) {
               for (int i = 0; i < rle; i++) {
                 col = currChunk[i];
                 output.write(col & 0xff);
                 output.write(col >> 8 & 0xff);
                 output.write(col >> 16 & 0xff);
                 output.write(col >>> 24 & 0xff);
               }
             } else {
               for (int i = 0; i < rle; i++) {
                 col = currChunk[i];
                 output.write(col & 0xff);
                 output.write(col >> 8 & 0xff);
                 output.write(col >> 16 & 0xff);
               }
             }
           }
           index += rle;
         }
       }
       output.flush();
       return true;

     } catch (IOException e) {
       e.printStackTrace();
       return false;
     }
  }


  /**
   * Use ImageIO functions from Java 1.4 and later to handle image save.
   * Various formats are supported, typically jpeg, png, bmp, and wbmp.
   * To get a list of the supported formats for writing, use: <BR>
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


  protected String[] saveImageFormats;

  /**
   * Save this image to disk.
   * <p>
   * As of revision 0100, this function requires an absolute path,
   * in order to avoid confusion. To save inside the sketch folder,
   * use the function savePath() from PApplet, or use saveFrame() instead.
   * As of revision 0116, savePath() is not needed if this object has been
   * created (as recommended) via createImage() or createGraphics() or
   * one of its neighbors.
   * <p>
   * As of revision 0115, when using Java 1.4 and later, you can write
   * to several formats besides tga and tiff. If Java 1.4 is installed
   * and the extension used is supported (usually png, jpg, jpeg, bmp,
   * and tiff), then those methods will be used to write the image.
   * To get a list of the supported formats for writing, use: <BR>
   * <TT>println(javax.imageio.ImageIO.getReaderFormatNames())</TT>
   * <p>
   * To use the original built-in image writers, use .tga or .tif as the
   * extension, or don't include an extension. When no extension is used,
   * the extension .tif will be added to the file name.
   * <p>
   * The ImageIO API claims to support wbmp files, however they probably
   * require a black and white image. Basic testing produced a zero-length
   * file with no error.
   */
  public boolean save(String path) {  // ignore
    boolean success = false;

    // Make sure the pixel data is ready to go
    loadPixels();

    try {
      OutputStream output =
        new BufferedOutputStream(parent.createOutput(path), 16 * 1024);

      String lower = path.toLowerCase();
      String extension = lower.substring(lower.lastIndexOf('.') + 1);
      if (extension.equals("jpg") || extension.equals("jpeg")) {
        // TODO probably not necessary to create another bitmap
        Bitmap outgoing = Bitmap.createBitmap(pixels, width, height, Config.ARGB_8888);
        success = outgoing.compress(CompressFormat.JPEG, 100, output);

      } else if (extension.equals("png")) {
        Bitmap outgoing = Bitmap.createBitmap(pixels, width, height, Config.ARGB_8888);
        success = outgoing.compress(CompressFormat.PNG, 100, output);

      } else if (extension.equals("tga")) {
        success = saveTGA(output); //, pixels, width, height, format);

      } else {
        if (!extension.equals("tif") && !extension.equals("tiff")) {
          // if no .tif extension, add it..
          path += ".tif";
        }
        success = saveTIFF(output);
      }
      output.flush();
      output.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
    if (!success) {
      System.err.println("Could not write the image to " + path);
    }
    return success;
  }
}
