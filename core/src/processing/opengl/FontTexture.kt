/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.opengl

import processing.core.PApplet
import processing.core.PConstants
import processing.core.PFont
import processing.core.PGraphics
import processing.core.PImage

import java.util.*

/**
 * All the infrastructure needed for optimized font rendering
 * in OpenGL. Basically, this special class is needed because
 * fonts in Processing are handled by a separate PImage for each
 * glyph. For performance reasons, all these glyphs should be
 * stored in a single OpenGL texture (otherwise, rendering a
 * string of text would involve binding and un-binding several
 * textures.
 * PFontTexture manages the correspondence between individual
 * glyphs and the large OpenGL texture containing them. Also,
 * in the case that the font size is very large, one single
 * OpenGL texture might not be enough to store all the glyphs,
 * so PFontTexture also takes care of spreading a single font
 * over several textures.
 * @author Andres Colubri
 */
open class FontTexture(pg: PGraphicsOpenGL?, font: PFont?, is3D: Boolean) : PConstants {
    protected var pgl: PGL?
    protected var is3D: Boolean
    protected var minSize = 0
    protected var maxSize = 0
    protected var offsetX = 0
    protected var offsetY = 0
    protected var lineHeight = 0

    @JvmField
    var textures: Array<Texture?>? = null

    protected var images: Array<PImage?>? = null
    protected var lastTex = 0
    protected lateinit var glyphTexinfos: Array<TextureInfo?>
    protected var texinfoMap: HashMap<PFont.Glyph, TextureInfo>? = null

    protected fun allocate() {
        // Nothing to do here: the font textures will allocate
        // themselves.
    }

    protected fun dispose() {
        for (i in textures!!.indices) {
            textures!![i]!!.dispose()
        }
    }

    protected fun initTexture(pg: PGraphicsOpenGL?, font: PFont?) {
        lastTex = -1
        val spow = PGL.nextPowerOfTwo(font!!.size)
        minSize = PApplet.min(PGraphicsOpenGL.maxTextureSize,
                PApplet.max(PGL.MIN_FONT_TEX_SIZE, spow))
        maxSize = PApplet.min(PGraphicsOpenGL.maxTextureSize,
                PApplet.max(PGL.MAX_FONT_TEX_SIZE, 2 * spow))
        if (maxSize < spow) {
            PGraphics.showWarning("The font size is too large to be properly " +
                    "displayed with OpenGL")
        }
        addTexture(pg)
        offsetX = 0
        offsetY = 0
        lineHeight = 0
        texinfoMap = HashMap()
        glyphTexinfos = arrayOfNulls(font.glyphCount)
        addAllGlyphsToTexture(pg, font)
    }

    fun addTexture(pg: PGraphicsOpenGL?): Boolean {
        val w: Int
        val h: Int
        val resize: Boolean
        w = maxSize

        if (-1 < lastTex && textures!![lastTex]!!.glHeight < maxSize) {
            // The height of the current texture is less than the maximum, this
            // means we can replace it with a larger texture.
            h = PApplet.min(2 * textures!![lastTex]!!.glHeight, maxSize)
            resize = true
        } else {
            h = minSize
            resize = false
        }

        val tex: Texture

        tex = if (is3D) {
            // Bilinear sampling ensures that the texture doesn't look pixelated
            // either when it is magnified or minified...
            Texture(pg, w, h,
                    Texture.Parameters(PConstants.ARGB, Texture.BILINEAR, false))
        } else {
            // ...however, the effect of bilinear sampling is to add some blurriness
            // to the text in its original size. In 2D, we assume that text will be
            // shown at its original size, so linear sampling is chosen instead (which
            // only affects minimized text).
            Texture(pg, w, h,
                    Texture.Parameters(PConstants.ARGB, Texture.LINEAR, false))
        }

        if (textures == null) {
            textures = arrayOfNulls(1)
            textures!![0] = tex
            images = arrayOfNulls(1)
            images!![0] = pg?.wrapTexture(tex)
            lastTex = 0
        } else if (resize) {
            // Replacing old smaller texture with larger one.
            // But first we must copy the contents of the older
            // texture into the new one.
            val tex0 = textures!![lastTex]
            tex.put(tex0)
            textures!![lastTex] = tex
            pg?.setCache(images?.get(lastTex)!!, tex)
            images!![lastTex]!!.width = tex.width
            images!![lastTex]!!.height = tex.height
        } else {
            // Adding new texture to the list.
            lastTex = textures!!.size
            val tempTex = arrayOfNulls<Texture>(lastTex + 1)
            PApplet.arrayCopy(textures, tempTex, textures!!.size)
            tempTex[lastTex] = tex
            textures = tempTex
            val tempImg = arrayOfNulls<PImage>(textures!!.size)
            PApplet.arrayCopy(images, tempImg, images!!.size)
            tempImg[lastTex] = pg?.wrapTexture(tex)
            images = tempImg
        }

        // Make sure that the current texture is bound.
        tex.bind()
        return resize
    }

    fun begin() {

    }

    fun end() {
        for (i in textures!!.indices) {
            pgl?.disableTexturing(textures!![i]!!.glTarget)
        }
    }

    fun getTexture(info: TextureInfo?): PImage? {
        return images?.get(info!!.texIndex)
    }

    // Add all the current glyphs to opengl texture.
    fun addAllGlyphsToTexture(pg: PGraphicsOpenGL?, font: PFont?) {
        // loop over current glyphs.
        for (i in 0 until font!!.glyphCount) {
            addToTexture(pg, i, font?.getGlyph(i))
        }
    }

    fun updateGlyphsTexCoords() {
        // loop over current glyphs.
        for (i in glyphTexinfos.indices) {
            val tinfo = glyphTexinfos[i]
            if (tinfo != null && tinfo.texIndex == lastTex) {
                tinfo.updateUV()
            }
        }
    }

    fun getTexInfo(glyph: PFont.Glyph?): TextureInfo? {
        return texinfoMap?.get(glyph)
    }

    fun addToTexture(pg: PGraphicsOpenGL?, glyph: PFont.Glyph?): TextureInfo? {
        val n = glyphTexinfos.size
        if (n == 0) {
            glyphTexinfos = arrayOfNulls(1)
        }
        addToTexture(pg, n, glyph)
        return glyphTexinfos[n]
    }

    fun contextIsOutdated(): Boolean {
        var outdated = false
        for (i in textures!!.indices) {
            if (textures!![i]!!.contextIsOutdated()) {
                outdated = true
            }
        }
        if (outdated) {
            for (i in textures!!.indices) {
                textures!![i]!!.dispose()
            }
        }
        return outdated
    }

    //  public void draw() {
    //    Texture tex = textures[lastTex];
    //    pgl.drawTexture(tex.glTarget, tex.glName,
    //                    tex.glWidth, tex.glHeight,
    //                    0, 0, tex.glWidth, tex.glHeight);
    //  }
    // Adds this glyph to the opengl texture in PFont.
    protected fun addToTexture(pg: PGraphicsOpenGL?, idx: Int, glyph: PFont.Glyph?) {
        // We add one pixel to avoid issues when sampling the font texture at
        // fractional screen positions. I.e.: the pixel on the screen only contains
        // half of the font rectangle, so it would sample half of the color from the
        // glyph area in the texture, and the other half from the contiguous pixel.
        // If the later contains a portion of the neighbor glyph and the former
        // doesn't, this would result in a shaded pixel when the correct output is
        // blank. This is a consequence of putting all the glyphs in a common
        // texture with bilinear sampling.
        val w = 1 + glyph!!.width + 1
        val h = 1 + glyph!!.height + 1

        // Converting the pixels array from the PImage into a valid RGBA array for
        // OpenGL.
        val rgba = IntArray(w * h)
        var t = 0
        var p = 0

        if (PGL.BIG_ENDIAN) {
            Arrays.fill(rgba, 0, w, -0x100) // Set the first row to blank pixels.
            t = w
            for (y in 0 until glyph.height) {
                rgba[t++] = -0x100 // Set the leftmost pixel in this row as blank
                for (x in 0 until glyph.width) {
                    rgba[t++] = -0x100 or glyph.image!!.pixels!![p++]
                }
                rgba[t++] = -0x100 // Set the rightmost pixel in this row as blank
            }
            Arrays.fill(rgba, (h - 1) * w, h * w, -0x100) // Set the last row to blank pixels.
        } else {
            Arrays.fill(rgba, 0, w, 0x00FFFFFF) // Set the first row to blank pixels.
            t = w
            for (y in 0 until glyph.height) {
                rgba[t++] = 0x00FFFFFF // Set the leftmost pixel in this row as blank
                for (x in 0 until glyph.width) {
                    rgba[t++] = glyph.image!!.pixels!![p++] shl 24 or 0x00FFFFFF
                }
                rgba[t++] = 0x00FFFFFF // Set the rightmost pixel in this row as blank
            }
            Arrays.fill(rgba, (h - 1) * w, h * w, 0x00FFFFFF) // Set the last row to blank pixels.
        }

        // Is there room for this glyph in the current line?
        if (offsetX + w > textures!![lastTex]!!.glWidth) {
            // No room, go to the next line:
            offsetX = 0
            offsetY += lineHeight
        }
        lineHeight = Math.max(lineHeight, h)
        var resized = false
        if (offsetY + lineHeight > textures!![lastTex]!!.glHeight) {
            // We run out of space in the current texture, so we add a new texture:
            resized = addTexture(pg)
            if (resized) {
                // Because the current texture has been resized, we need to
                // update the UV coordinates of all the glyphs associated to it:
                updateGlyphsTexCoords()
            } else {
                // A new texture has been created. Reseting texture coordinates
                // and line.
                offsetX = 0
                offsetY = 0
                lineHeight = 0
            }
        }

        val tinfo = TextureInfo(lastTex, offsetX, offsetY, w, h, rgba)
        offsetX += w
        if (idx == glyphTexinfos.size) {
            val temp = arrayOfNulls<TextureInfo>(glyphTexinfos.size + 1)
            System.arraycopy(glyphTexinfos, 0, temp, 0, glyphTexinfos.size)
            glyphTexinfos = temp
        }
        glyphTexinfos[idx] = tinfo
        texinfoMap!![glyph] = tinfo
    }

    inner class TextureInfo(@JvmField var texIndex: Int, cropX: Int, cropY: Int, cropW: Int, cropH: Int,
                                     pix: IntArray) {
        @JvmField
        var width = 0
        @JvmField
        var height = 0
        @JvmField
        var crop: IntArray

        @JvmField
        var u0 = 0f
        @JvmField
        var u1 = 0f
        @JvmField
        var v0 = 0f
        @JvmField
        var v1 = 0f
        @JvmField
        var pixels: IntArray

        fun updateUV() {
            width = textures!![texIndex]!!.glWidth
            height = textures!![texIndex]!!.glHeight
            u0 = crop[0].toFloat() / width.toFloat()
            u1 = u0 + crop[2].toFloat() / width.toFloat()
            v0 = (crop[1] + crop[3]).toFloat() / height.toFloat()
            v1 = v0 - crop[3].toFloat() / height.toFloat()
        }

        fun updateTex() {
            textures!![texIndex]!!.setNative(pixels, crop[0] - 1, crop[1] + crop[3] - 1,
                    crop[2] + 2, -crop[3] + 2)
        }

        // constructor or initializer for TextureInfo
        init {
            crop = IntArray(4)
            // The region of the texture corresponding to the glyph is surrounded by a
            // 1-pixel wide border to avoid artifacts due to bilinear sampling. This
            // is why the additions and subtractions to the crop values.
            crop[0] = cropX + 1
            crop[1] = cropY + 1 + cropH - 2
            crop[2] = cropW - 2
            crop[3] = -cropH + 2
            pixels = pix
            updateUV()
            updateTex()
        }
    }

    // constructor or initializer block for FontTexture
    init {
        pgl = pg?.pgl
        this.is3D = is3D
        initTexture(pg, font)
    }
}