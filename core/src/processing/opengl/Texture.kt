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
import processing.core.PGraphics
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * @author Processing migrated into kotlin: Aditya Rana
 * This class wraps an OpenGL texture.
 * By Andres Colubri
 *
 */
open class Texture : PConstants {
    @JvmField
    var width = 0

    @JvmField
    var height = 0

    @JvmField
    var glName: Int

    @JvmField
    var glTarget = 0

    @JvmField
    var glFormat = 0

    @JvmField
    var glMinFilter = 0

    @JvmField
    var glMagFilter = 0

    @JvmField
    var glWrapS = 0

    @JvmField
    var glWrapT = 0

    @JvmField
    var glWidth = 0

    @JvmField
    var glHeight = 0

    private var glres: PGraphicsOpenGL.GLResourceTexture? = null

    @JvmField
    var pg: PGraphicsOpenGL?

    protected var pgl: PGL // The interface between Processing and OpenGL.

    @JvmField
    var context: Int // The context that created this texture.

    protected var colorBuffer: Boolean // true if it is the color attachment of

    // FrameBuffer object.
    @JvmField
    var usingMipmaps = false

    @JvmField
    var usingRepeat = false

    protected var maxTexcoordU = 0f
    protected var maxTexcoordV = 0f
    protected var bound = false
    protected var invertedX = false
    protected var invertedY = false
    protected var rgbaPixels: IntArray? = null
    protected var pixelBuffer: IntBuffer? = null
    protected var edgePixels: IntArray? = null
    protected var edgeBuffer: IntBuffer? = null
    protected var tempFbo: FrameBuffer? = null
    protected var pixBufUpdateCount = 0
    protected var rgbaPixUpdateCount = 0

    //////////////////////////////////////////////////////////////

    // Modified flag

    /** Modified portion of the texture  */
    @JvmField
    var isModified = false

    var modifiedX1 = 0
        protected set

    var modifiedY1 = 0
        protected set

    var modifiedX2 = 0
        protected set

    var modifiedY2 = 0
        protected set

    private var bufferSource: Any? = null

    protected var bufferCache: LinkedList<BufferData>? = null
    protected var usedBuffers: LinkedList<BufferData?>? = null
    protected var disposeBufferMethod: Method? = null

    ////////////////////////////////////////////////////////////

    // Constructors.
    constructor(pg: PGraphicsOpenGL?) {
        this.pg = pg
        pgl = pg?.pgl!!
        context = pgl.createEmptyContext()
        colorBuffer = false
        glName = 0
    }
    /**
     * Creates an instance of PTexture with size width x height and with the
     * specified parameters. The texture is initialized (empty) to that size.
     * @param width int
     * @param height int
     * @param params Parameters
     */
    /**
     * Creates an instance of PTexture with size width x height. The texture is
     * initialized (empty) to that size.
     * @param width  int
     * @param height  int
     */
    @JvmOverloads
    constructor(pg: PGraphicsOpenGL?, width: Int, height: Int, params: Any = Parameters()) {
        this.pg = pg
        pgl = pg?.pgl!!
        context = pgl.createEmptyContext()
        colorBuffer = false
        glName = 0
        init(width, height, params as Parameters)
    }

    ////////////////////////////////////////////////////////////

    // Init, resize methods

    /**
     * Sets the size of the image and texture to width x height. If the texture is
     * already initialized, it first destroys the current OpenGL texture object
     * and then creates a new one with the specified size.
     * @param width int
     * @param height int
     */
    fun init(width: Int, height: Int) {
        val params: Parameters = if (0 < glName) {
            // Re-initializing a pre-existing texture.
            // We use the current parameters as default:
            parameters
        } else {
            // Just built-in default parameters otherwise:
            Parameters()
        }
        init(width, height, params)
    }

    /**
     * Sets the size of the image and texture to width x height, and the
     * parameters of the texture to params. If the texture is already initialized,
     * it first destroys the current OpenGL texture object and then creates a new
     * one with the specified size.
     * @param width int
     * @param height int
     * @param params GLTextureParameters
     */
    fun init(width: Int, height: Int, params: Parameters) {
        parameters = params
        setSize(width, height)
        allocate()
    }

    /**
     * Initializes the texture using GL parameters
     */
    fun init(width: Int, height: Int,
             glName: Int, glTarget: Int, glFormat: Int,
             glWidth: Int, glHeight: Int,
             glMinFilter: Int, glMagFilter: Int,
             glWrapS: Int, glWrapT: Int) {
        this.width = width
        this.height = height
        this.glName = glName
        this.glTarget = glTarget
        this.glFormat = glFormat
        this.glWidth = glWidth
        this.glHeight = glHeight
        this.glMinFilter = glMinFilter
        this.glMagFilter = glMagFilter
        this.glWrapS = glWrapS
        this.glWrapT = glWrapT

        maxTexcoordU = width.toFloat() / glWidth
        maxTexcoordV = height.toFloat() / glHeight
        usingMipmaps = glMinFilter == PGL.LINEAR_MIPMAP_NEAREST ||
                glMinFilter == PGL.LINEAR_MIPMAP_LINEAR
        usingRepeat = glWrapS == PGL.REPEAT || glWrapT == PGL.REPEAT
    }

    fun resize(wide: Int, high: Int) {
        // Disposing current resources.
        dispose()

        // Creating new texture with the appropriate size.
        val tex = Texture(pg, wide, high, parameters)

        // Copying the contents of this texture into tex.
        tex.set(this)

        // Now, overwriting "this" with tex.
        copyObject(tex)

        // Nullifying some utility objects so they are recreated with the
        // appropriate size when needed.
        tempFbo = null
    }

    /**
     * Returns true if the texture has been initialized.
     * @return boolean
     */
    fun available(): Boolean {
        return 0 < glName
    }

    ////////////////////////////////////////////////////////////

    // Set methods

    fun set(tex: Texture?) {
        copyTexture(tex, 0, 0, tex!!.width, tex!!.height, true)
    }

    operator fun set(tex: Texture?, x: Int, y: Int, w: Int, h: Int) {
        copyTexture(tex, x, y, w, h, true)
    }

    operator fun set(texTarget: Int, texName: Int, texWidth: Int, texHeight: Int,
                     w: Int, h: Int) {
        copyTexture(texTarget, texName, texWidth, texHeight, 0, 0, w, h, true)
    }

    operator fun set(texTarget: Int, texName: Int, texWidth: Int, texHeight: Int,
                     target: Int, tex: Int, x: Int, y: Int, w: Int, h: Int) {
        copyTexture(texTarget, texName, texWidth, texHeight, x, y, w, h, true)
    }

    operator fun set(pixels: IntArray?, format: Int) {
        set(pixels, 0, 0, width, height, format)
    }


    fun set(pixels: IntArray?, x: Int, y: Int, w: Int, h: Int, format: Int) {
        if (pixels == null) {
            PGraphics.showWarning("The pixels array is null.")
            return
        }

        if (pixels.size < w * h) {
            PGraphics.showWarning("The pixel array has a length of " +
                    pixels.size + ", but it should be at least " + w * h)
            return
        }

        if (pixels.isEmpty() || w == 0 || h == 0) {
            return
        }

        var enabledTex = false

        if (!pgl.texturingIsEnabled(glTarget)) {
            pgl.enableTexturing(glTarget)
            enabledTex = true
        }

        pgl.bindTexture(glTarget, glName)
        loadPixels(w * h)
        convertToRGBA(pixels, format, w, h)

        if (invertedX) flipArrayOnX(rgbaPixels, 1)
        if (invertedY) flipArrayOnY(rgbaPixels, 1)

        updatePixelBuffer(rgbaPixels)
        pgl.texSubImage2D(glTarget, 0, x, y, w, h, PGL.RGBA, PGL.UNSIGNED_BYTE,
                pixelBuffer)

        fillEdges(x, y, w, h)

        if (usingMipmaps) {
            if (PGraphicsOpenGL.autoMipmapGenSupported) {
                pgl.generateMipmap(glTarget)
            } else {
                manualMipmap()
            }
        }

        pgl.bindTexture(glTarget, 0)

        if (enabledTex) {
            pgl.disableTexturing(glTarget)
        }

        releasePixelBuffer()
        releaseRGBAPixels()
        updateTexels(x, y, w, h)
    }

    ////////////////////////////////////////////////////////////

    // Native set methods

    fun setNative(pixels: IntArray?) {
        setNative(pixels, 0, 0, width, height)
    }

    fun setNative(pixels: IntArray?, x: Int, y: Int, w: Int, h: Int) {
        updatePixelBuffer(pixels)
        setNative(pixelBuffer, x, y, w, h)
        releasePixelBuffer()
    }

    fun setNative(pixBuf: IntBuffer?, x: Int, y: Int, w: Int, h: Int) {
        var pixBuf = pixBuf

        if (pixBuf == null) {
            pixBuf = null
            PGraphics.showWarning("The pixel buffer is null.")
            return
        }

        if (pixBuf.capacity() < w * h) {
            PGraphics.showWarning("The pixel bufer has a length of " +
                    pixBuf.capacity() + ", but it should be at least " + w * h)
            return
        }

        if (pixBuf.capacity() == 0) {
            // Nothing to do (means that w == h == 0) but not an erroneous situation
            return
        }

        var enabledTex = false

        if (!pgl.texturingIsEnabled(glTarget)) {
            pgl.enableTexturing(glTarget)
            enabledTex = true
        }

        pgl.bindTexture(glTarget, glName)
        pgl.texSubImage2D(glTarget, 0, x, y, w, h, PGL.RGBA, PGL.UNSIGNED_BYTE,
                pixBuf)

        fillEdges(x, y, w, h)

        if (usingMipmaps) {
            if (PGraphicsOpenGL.autoMipmapGenSupported) {
                pgl.generateMipmap(glTarget)
            } else {
                manualMipmap()
            }
        }

        pgl.bindTexture(glTarget, 0)

        if (enabledTex) {
            pgl.disableTexturing(glTarget)
        }

        updateTexels(x, y, w, h)
    }

    ////////////////////////////////////////////////////////////

    // Get methods
    /**
     * Copy texture to pixels. Involves video memory to main memory transfer (slow).
     */
    operator fun get(pixels: IntArray?) {
        if (pixels == null) {
            throw RuntimeException("Trying to copy texture to null pixels array")
        }

        if (pixels.size != width * height) {
            throw RuntimeException("Trying to copy texture to pixels array of " +
                    "wrong size")
        }

        if (tempFbo == null) {
            tempFbo = FrameBuffer(pg, glWidth, glHeight)
        }

        // Attaching the texture to the color buffer of a FBO, binding the FBO and
        // reading the pixels from the current draw buffer (which is the color
        // buffer of the FBO).
        tempFbo!!.setColorBuffer(this)
        pg?.pushFramebuffer()
        pg?.setFramebuffer(tempFbo)
        tempFbo!!.readPixels()
        pg?.popFramebuffer()
        tempFbo!!.getPixels(pixels)
        convertToARGB(pixels)

        if (invertedX) flipArrayOnX(pixels, 1)
        if (invertedY) flipArrayOnY(pixels, 1)
    }

    ////////////////////////////////////////////////////////////

    // Put methods (the source texture is not resized to cover the entire
    // destination).

    fun put(tex: Texture?) {
        copyTexture(tex, 0, 0, tex!!.width, tex!!.height, false)
    }

    fun put(tex: Texture?, x: Int, y: Int, w: Int, h: Int) {
        copyTexture(tex, x, y, w, h, false)
    }

    fun put(texTarget: Int, texName: Int, texWidth: Int, texHeight: Int,
            w: Int, h: Int) {
        copyTexture(texTarget, texName, texWidth, texHeight, 0, 0, w, h, false)
    }

    fun put(texTarget: Int, texName: Int, texWidth: Int, texHeight: Int,
            target: Int, tex: Int, x: Int, y: Int, w: Int, h: Int) {
        copyTexture(texTarget, texName, texWidth, texHeight, x, y, w, h, false)
    }

    ////////////////////////////////////////////////////////////

    // Get OpenGL parameters

    /**
     * Returns true or false whether or not the texture is using mipmaps.
     * @return boolean
     */
    fun usingMipmaps(): Boolean {
        return usingMipmaps
    }

    fun usingMipmaps(mipmaps: Boolean, sampling: Int) {
        val glMagFilter0 = glMagFilter
        val glMinFilter0 = glMinFilter

        if (mipmaps) {
            if (sampling == POINT) {
                glMagFilter = PGL.NEAREST
                glMinFilter = PGL.NEAREST
                usingMipmaps = false
            } else if (sampling == LINEAR) {
                glMagFilter = PGL.NEAREST
                glMinFilter = if (PGL.MIPMAPS_ENABLED) PGL.LINEAR_MIPMAP_NEAREST else PGL.LINEAR
                usingMipmaps = true
            } else if (sampling == BILINEAR) {
                glMagFilter = PGL.LINEAR
                glMinFilter = if (PGL.MIPMAPS_ENABLED) PGL.LINEAR_MIPMAP_NEAREST else PGL.LINEAR
                usingMipmaps = true
            } else if (sampling == TRILINEAR) {
                glMagFilter = PGL.LINEAR
                glMinFilter = if (PGL.MIPMAPS_ENABLED) PGL.LINEAR_MIPMAP_LINEAR else PGL.LINEAR
                usingMipmaps = true
            } else {
                throw RuntimeException("Unknown texture filtering mode")
            }
        } else {
            usingMipmaps = false
            if (sampling == POINT) {
                glMagFilter = PGL.NEAREST
                glMinFilter = PGL.NEAREST
            } else if (sampling == LINEAR) {
                glMagFilter = PGL.NEAREST
                glMinFilter = PGL.LINEAR
            } else if (sampling == BILINEAR || sampling == TRILINEAR) {
                glMagFilter = PGL.LINEAR
                glMinFilter = PGL.LINEAR
            } else {
                throw RuntimeException("Unknown texture filtering mode")
            }
        }

        if (glMagFilter0 != glMagFilter || glMinFilter0 != glMinFilter) {
            bind()
            pgl.texParameteri(glTarget, PGL.TEXTURE_MIN_FILTER, glMinFilter)
            pgl.texParameteri(glTarget, PGL.TEXTURE_MAG_FILTER, glMagFilter)
            if (usingMipmaps) {
                if (PGraphicsOpenGL.autoMipmapGenSupported) {
                    pgl.generateMipmap(glTarget)
                } else {
                    manualMipmap()
                }
            }
            unbind()
        }
    }

    /**
     * Returns true or false whether or not the texture is using repeat wrap mode
     * along either U or V directions.
     * @return boolean
     */
    fun usingRepeat(): Boolean {
        return usingRepeat
    }

    fun usingRepeat(repeat: Boolean) {
        if (repeat) {
            glWrapS = PGL.REPEAT
            glWrapT = PGL.REPEAT
            usingRepeat = true
        } else {
            glWrapS = PGL.CLAMP_TO_EDGE
            glWrapT = PGL.CLAMP_TO_EDGE
            usingRepeat = false
        }

        bind()

        pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_S, glWrapS)
        pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_T, glWrapT)
        unbind()
    }

    /**
     * Returns the maximum possible value for the texture coordinate U
     * (horizontal).
     * @return float
     */
    fun maxTexcoordU(): Float {
        return maxTexcoordU
    }

    /**
     * Returns the maximum possible value for the texture coordinate V (vertical).
     * @return float
     */
    fun maxTexcoordV(): Float {
        return maxTexcoordV
    }

    /**
     * Returns true if the texture is inverted along the horizontal direction.
     * @return boolean;
     */
    fun invertedX(): Boolean {
        return invertedX
    }

    /**
     * Sets the texture as inverted or not along the horizontal direction.
     * @param v boolean;
     */
    fun invertedX(v: Boolean) {
        invertedX = v
    }

    /**
     * Returns true if the texture is inverted along the vertical direction.
     * @return boolean;
     */
    fun invertedY(): Boolean {
        return invertedY
    }

    /**
     * Sets the texture as inverted or not along the vertical direction.
     * @param v boolean;
     */
    fun invertedY(v: Boolean) {
        invertedY = v
    }

    fun currentSampling(): Int {
        return if (glMagFilter == PGL.NEAREST && glMinFilter == PGL.NEAREST) {
            POINT
        } else if (glMagFilter == PGL.NEAREST &&
                glMinFilter == (if (PGL.MIPMAPS_ENABLED) PGL.LINEAR_MIPMAP_NEAREST else PGL.LINEAR)) {
            LINEAR
        } else if (glMagFilter == PGL.LINEAR &&
                glMinFilter == (if (PGL.MIPMAPS_ENABLED) PGL.LINEAR_MIPMAP_NEAREST else PGL.LINEAR)) {
            BILINEAR
        } else if (glMagFilter == PGL.LINEAR &&
                glMinFilter == PGL.LINEAR_MIPMAP_LINEAR) {
            TRILINEAR
        } else {
            -1
        }
    }

    ////////////////////////////////////////////////////////////

    // Bind/unbind

    fun bind() {
        // Binding a texture automatically enables texturing for the
        // texture target from that moment onwards. Unbinding the texture
        // won't disable texturing.
        if (!pgl.texturingIsEnabled(glTarget)) {
            pgl.enableTexturing(glTarget)
        }
        pgl.bindTexture(glTarget, glName)
        bound = true
    }

    fun unbind() {
        if (pgl.textureIsBound(glTarget, glName)) {
            // We don't want to unbind another texture
            // that might be bound instead of this one.
            if (!pgl.texturingIsEnabled(glTarget)) {
                pgl.enableTexturing(glTarget)
                pgl.bindTexture(glTarget, 0)
                pgl.disableTexturing(glTarget)
            } else {
                pgl.bindTexture(glTarget, 0)
            }
        }
        bound = false
    }

    fun bound(): Boolean {
        // A true result might not necessarily mean that texturing is enabled
        // (a texture can be bound to the target, but texturing is disabled).
        return bound
    }

    fun setModified() {
        isModified = true
    }

    fun updateTexels() {
        updateTexelsImpl(0, 0, width, height)
    }

    fun updateTexels(x: Int, y: Int, w: Int, h: Int) {
        updateTexelsImpl(x, y, w, h)
    }

    protected fun updateTexelsImpl(x: Int, y: Int, w: Int, h: Int) {
        val x2 = x + w
        val y2 = y + h
        if (!isModified) {
            modifiedX1 = PApplet.max(0, x)
            modifiedX2 = PApplet.min(width - 1, x2)
            modifiedY1 = PApplet.max(0, y)
            modifiedY2 = PApplet.min(height - 1, y2)
            isModified = true
        } else {
            if (x < modifiedX1) modifiedX1 = PApplet.max(0, x)
            if (x > modifiedX2) modifiedX2 = PApplet.min(width - 1, x)
            if (y < modifiedY1) modifiedY1 = PApplet.max(0, y)
            if (y > modifiedY2) modifiedY2 = y
            if (x2 < modifiedX1) modifiedX1 = PApplet.max(0, x2)
            if (x2 > modifiedX2) modifiedX2 = PApplet.min(width - 1, x2)
            if (y2 < modifiedY1) modifiedY1 = PApplet.max(0, y2)
            if (y2 > modifiedY2) modifiedY2 = PApplet.min(height - 1, y2)
        }
    }

    protected fun loadPixels(len: Int) {
        if (rgbaPixels == null || rgbaPixels!!.size < len) {
            rgbaPixels = IntArray(len)
        }
    }

    protected fun updatePixelBuffer(pixels: IntArray?) {
        pixelBuffer = PGL.updateIntBuffer(pixelBuffer, pixels, true)
        pixBufUpdateCount++
    }

    protected fun manualMipmap() {
        // TODO: finish manual mipmap generation,
        // https://github.com/processing/processing/issues/3335
    }

    ////////////////////////////////////////////////////////////

    // Buffer sink interface.

    fun setBufferSource(source: Any?) {
        bufferSource = source
        sourceMethods
    }

    fun copyBufferFromSource(natRef: Any?, byteBuf: ByteBuffer,
                             w: Int, h: Int) {
        if (bufferCache == null) {
            bufferCache = LinkedList()
        }

        if (bufferCache!!.size + 1 <= MAX_BUFFER_CACHE_SIZE) {
            bufferCache!!.add(BufferData(natRef, byteBuf.asIntBuffer(), w, h))
        } else {
            // The buffer cache reached the maximum size, so we just dispose
            // the new buffer by adding it to the list of used buffers.
            try {
                usedBuffers!!.add(BufferData(natRef, byteBuf.asIntBuffer(), w, h))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disposeSourceBuffer() {
        if (usedBuffers == null) return
        while (0 < usedBuffers!!.size) {
            var data: BufferData? = null
            try {
                data = usedBuffers!!.removeAt(0)
            } catch (ex: NoSuchElementException) {
                PGraphics.showWarning("Cannot remove used buffer")
            }
            data?.dispose()
        }
    }

    fun getBufferPixels(pixels: IntArray) {
        // We get the buffer either from the used buffers or the cache, giving
        // priority to the used buffers. Why? Because the used buffer was already
        // transferred to the texture, so the pixels should be in sync with the
        // texture.
        var data: BufferData? = null

        if (usedBuffers != null && 0 < usedBuffers!!.size) {
            data = usedBuffers!!.last
        } else if (bufferCache != null && 0 < bufferCache!!.size) {
            data = bufferCache!!.last
        }

        if (data != null) {
            if (data.w != width || data.h != height) {
                init(data.w, data.h)
            }

            data.rgbBuf!!.rewind()
            data.rgbBuf!![pixels]
            convertToARGB(pixels)

            // In order to avoid a cached buffer to overwrite the texture when the
            // renderer draws the texture, and hence put the pixels put of sync, we
            // simply empty the cache.
            if (usedBuffers == null) {
                usedBuffers = LinkedList()
            }

            while (0 < bufferCache!!.size) {
                data = bufferCache!!.removeAt(0)
                usedBuffers!!.add(data)
            }
        }
    }

    fun hasBufferSource(): Boolean {
        return bufferSource != null
    }

    fun hasBuffers(): Boolean {
        return bufferSource != null && bufferCache != null && 0 < bufferCache!!.size
    }

    fun bufferUpdate(): Boolean {
        var data: BufferData? = null

        try {
            data = bufferCache!!.removeAt(0)
        } catch (ex: NoSuchElementException) {
            PGraphics.showWarning("Don't have pixel data to copy to texture")
        }

        return if (data != null) {
            if (data.w != width || data.h != height) {
                init(data.w, data.h)
            }
            data.rgbBuf!!.rewind()
            setNative(data.rgbBuf, 0, 0, width, height)

            // Putting the buffer in the used buffers list to dispose at the end of
            // draw.
            if (usedBuffers == null) {
                usedBuffers = LinkedList()
            }
            usedBuffers!!.add(data)
            true
        } else {
            false
        }
    }

    protected val sourceMethods: Unit
        protected get() {
            disposeBufferMethod = try {
                bufferSource!!.javaClass.getMethod("disposeBuffer", *arrayOf<Class<*>>(Any::class.java))
            } catch (e: Exception) {
                throw RuntimeException("Provided source object doesn't have a " +
                        "disposeBuffer method.")
            }
        }

    ////////////////////////////////////////////////////////////

    // Utilities

    /**
     * Flips intArray along the X axis.
     * @param intArray int[]
     * @param mult int
     */
    protected fun flipArrayOnX(intArray: IntArray?, mult: Int) {
        var index = 0
        var xindex = mult * (width - 1)
        for (x in 0 until width / 2) {
            for (y in 0 until height) {
                var i = index + mult * y * width
                var j = xindex + mult * y * width
                for (c in 0 until mult) {
                    val temp = intArray!![i]
                    intArray[i] = intArray[j]
                    intArray[j] = temp
                    i++
                    j++
                }
            }
            index += mult
            xindex -= mult
        }
    }

    /**
     * Flips intArray along the Y axis.
     * @param intArray int[]
     * @param mult int
     */
    protected fun flipArrayOnY(intArray: IntArray?, mult: Int) {
        var index = 0
        var yindex = mult * (height - 1) * width
        for (y in 0 until height / 2) {
            for (x in 0 until mult * width) {
                val temp = intArray!![index]
                intArray[index] = intArray[yindex]
                intArray[yindex] = temp
                index++
                yindex++
            }
            yindex -= mult * width * 2
        }
    }

    /**
     * Reorders a pixel array in the given format into the order required by
     * OpenGL (RGBA) and stores it into rgbaPixels. The width and height
     * parameters are used in the YUV420 to RBGBA conversion.
     * @param pixels int[]
     * @param format int
     * @param w int
     * @param h int
     */
    protected fun convertToRGBA(pixels: IntArray, format: Int, w: Int, h: Int) {
        if (PGL.BIG_ENDIAN) {
            when (format) {
                PConstants.ALPHA ->         // Converting from xxxA into RGBA. RGB is set to white (0xFFFFFF, i.e.: (255, 255, 255))
                {
                    var i = 0
                    while (i < pixels.size) {
                        rgbaPixels!![i] = -0x100 or pixels[i]
                        i++
                    }
                }

                PConstants.RGB ->         // Converting xRGB into RGBA. A is set to 0xFF (255, full opacity).
                {
                    var i = 0
                    while (i < pixels.size) {
                        val pixel = pixels[i]
                        rgbaPixels!![i] = pixel shl 8 or 0xFF
                        i++
                    }
                }

                PConstants.ARGB ->         // Converting ARGB into RGBA. Shifting RGB to 8 bits to the left, and bringing A to the first byte.
                {
                    var i = 0
                    while (i < pixels.size) {
                        val pixel = pixels[i]
                        rgbaPixels!![i] = pixel shl 8 or (pixel shr 24 and 0xFF)
                        i++
                    }
                }
            }
        } else {
            // LITTLE_ENDIAN
            // ARGB native, and RGBA opengl means ABGR on windows
            // for the most part just need to swap two components here
            // the sun.cpu.endian here might be "false", oddly enough..
            // (that's why just using an "else", rather than check for "little")
            when (format) {
                PConstants.ALPHA ->         // Converting xxxA into ARGB, with RGB set to white.
                {
                    var i = 0
                    while (i < pixels.size) {
                        rgbaPixels!![i] = pixels[i] shl 24 or 0x00FFFFFF
                        i++
                    }
                }

                PConstants.RGB ->         // We need to convert xRGB into ABGR,
                    // so R and B must be swapped, and the x just made 0xFF.
                {
                    var i = 0
                    while (i < pixels.size) {
                        val pixel = pixels[i]
                        rgbaPixels!![i] = -0x1000000 or
                                (pixel and 0xFF shl 16) or (pixel and 0xFF0000 shr 16) or
                                (pixel and 0x0000FF00)
                        i++
                    }
                }

                PConstants.ARGB ->         // We need to convert ARGB into ABGR, so R and B must be swapped, A and G just brought back in.
                {
                    var i = 0
                    while (i < pixels.size) {
                        val pixel = pixels[i]
                        rgbaPixels!![i] = pixel and 0xFF shl 16 or (pixel and 0xFF0000 shr 16) or
                                (pixel and -0xff0100)
                        i++
                    }
                }
            }
        }
        rgbaPixUpdateCount++
    }

    /**
     * Reorders an OpenGL pixel array (RGBA) into ARGB. The array must be
     * of size width * height.
     * @param pixels int[]
     */
    protected fun convertToARGB(pixels: IntArray) {
        var t = 0
        var p = 0
        if (PGL.BIG_ENDIAN) {
            // RGBA to ARGB conversion: shifting RGB 8 bits to the right,
            // and placing A 24 bits to the left.
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[p++]
                    pixels[t++] = pixel ushr 8 or (pixel shl 24 and -0x1000000)
                }
            }
        } else {
            // We have to convert ABGR into ARGB, so R and B must be swapped,
            // A and G just brought back in.
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[p++]
                    pixels[t++] = pixel and 0xFF shl 16 or (pixel and 0xFF0000 shr 16) or
                            (pixel and -0xff0100)
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////

    // Allocate/release texture.

    protected fun setSize(w: Int, h: Int) {
        width = w
        height = h

        if (PGraphicsOpenGL.npotTexSupported) {
            glWidth = w
            glHeight = h
        } else {
            glWidth = PGL.nextPowerOfTwo(w)
            glHeight = PGL.nextPowerOfTwo(h)
        }

        if (glWidth > PGraphicsOpenGL.maxTextureSize ||
                glHeight > PGraphicsOpenGL.maxTextureSize) {
            glHeight = 0
            glWidth = glHeight
            throw RuntimeException("Image width and height cannot be" +
                    " larger than " +
                    PGraphicsOpenGL.maxTextureSize +
                    " with this graphics card.")
        }

        // If non-power-of-two textures are not supported, and the specified width
        // or height is non-power-of-two, then glWidth (glHeight) will be greater
        // than w (h) because it is chosen to be the next power of two, and this
        // quotient will give the appropriate maximum texture coordinate value given
        // this situation.
        maxTexcoordU = width.toFloat() / glWidth
        maxTexcoordV = height.toFloat() / glHeight
    }

    /**
     * Allocates the opengl texture object.
     */
    protected fun allocate() {
        dispose() // Just in the case this object is being re-allocated.
        var enabledTex = false

        if (!pgl.texturingIsEnabled(glTarget)) {
            pgl.enableTexturing(glTarget)
            enabledTex = true
        }

        context = pgl.currentContext
        glres = PGraphicsOpenGL.GLResourceTexture(this)
        pgl.bindTexture(glTarget, glName)

        pgl.texParameteri(glTarget, PGL.TEXTURE_MIN_FILTER, glMinFilter)
        pgl.texParameteri(glTarget, PGL.TEXTURE_MAG_FILTER, glMagFilter)
        pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_S, glWrapS)
        pgl.texParameteri(glTarget, PGL.TEXTURE_WRAP_T, glWrapT)

        if (PGraphicsOpenGL.anisoSamplingSupported) {
            pgl.texParameterf(glTarget, PGL.TEXTURE_MAX_ANISOTROPY,
                    PGraphicsOpenGL.maxAnisoAmount)
        }

        // First, we use glTexImage2D to set the full size of the texture (glW/glH
        // might be diff from w/h in the case that the GPU doesn't support NPOT
        // textures)
        pgl.texImage2D(glTarget, 0, glFormat, glWidth, glHeight, 0,
                PGL.RGBA, PGL.UNSIGNED_BYTE, null)

        // Makes sure that the texture buffer in video memory doesn't contain
        // any garbage.
        pgl.initTexture(glTarget, PGL.RGBA, width, height)
        pgl.bindTexture(glTarget, 0)

        if (enabledTex) {
            pgl.disableTexturing(glTarget)
        }
        bound = false
    }

    /**
     * Marks the texture object for deletion.
     */
    fun dispose() {
        if (glres != null) {
            glres!!.dispose()
            glres = null
            glName = 0
        }
    }

    fun contextIsOutdated(): Boolean {
        val outdated = !pgl.contextIsCurrent(context)
        if (outdated) {
            dispose()
        }
        return outdated
    }

    fun colorBuffer(value: Boolean) {
        colorBuffer = value
    }

    fun colorBuffer(): Boolean {
        return colorBuffer
    }

    ///////////////////////////////////////////////////////////

    // Utilities.
    // Copies source texture tex into this.

    protected fun copyTexture(tex: Texture?, x: Int, y: Int, w: Int, h: Int,
                              scale: Boolean) {
        if (tex == null) {
            throw RuntimeException("Source texture is null")
        }

        if (tempFbo == null) {
            tempFbo = FrameBuffer(pg, glWidth, glHeight)
        }

        // This texture is the color (destination) buffer of the FBO.
        tempFbo!!.setColorBuffer(this)
        tempFbo!!.disableDepthTest()

        // FBO copy:
        pg?.pushFramebuffer()
        pg?.setFramebuffer(tempFbo)

        // Replaces anything that this texture might contain in the area being
        // replaced by the new one.
        pg?.pushStyle()
        pg?.blendMode(PConstants.REPLACE)

        if (scale) {
            // Rendering tex into "this", and scaling the source rectangle
            // to cover the entire destination region.
            pgl.drawTexture(tex.glTarget, tex.glName, tex.glWidth, tex.glHeight,
                    0, 0, tempFbo!!.width, tempFbo!!.height, 1,
                    x, y, x + w, y + h, 0, 0, width, height)
        } else {
            // Rendering tex into "this" but without scaling so the contents
            // of the source texture fall in the corresponding texels of the
            // destination.
            pgl.drawTexture(tex.glTarget, tex.glName, tex.glWidth, tex.glHeight,
                    0, 0, tempFbo!!.width, tempFbo!!.height, 1,
                    x, y, x + w, y + h, x, y, x + w, y + h)
        }

        pgl.flush() // Needed to make sure that the change in this texture is available immediately.

        pg?.popStyle()
        pg?.popFramebuffer()
        updateTexels(x, y, w, h)
    }

    // Copies source texture tex into this.
    protected fun copyTexture(texTarget: Int, texName: Int,
                              texWidth: Int, texHeight: Int,
                              x: Int, y: Int, w: Int, h: Int, scale: Boolean) {
        if (tempFbo == null) {
            tempFbo = FrameBuffer(pg, glWidth, glHeight)
        }

        // This texture is the color (destination) buffer of the FBO.
        tempFbo!!.setColorBuffer(this)
        tempFbo!!.disableDepthTest()

        // FBO copy:
        pg?.pushFramebuffer()
        pg?.setFramebuffer(tempFbo)

        // Replaces anything that this texture might contain in the area being replaced by the new one.
        pg?.pushStyle()

        pg?.blendMode(PConstants.REPLACE)
        if (scale) {
            // Rendering tex into "this", and scaling the source rectangle
            // to cover the entire destination region.
            pgl.drawTexture(texTarget, texName, texWidth, texHeight,
                    0, 0, tempFbo!!.width, tempFbo!!.height,
                    x, y, w, h, 0, 0, width, height)
        } else {
            // Rendering tex into "this" but without scaling so the contents
            // of the source texture fall in the corresponding texels of the
            // destination.
            pgl.drawTexture(texTarget, texName, texWidth, texHeight,
                    0, 0, tempFbo!!.width, tempFbo!!.height,
                    x, y, w, h, x, y, w, h)
        }
        pgl.flush() // Needed to make sure that the change in this texture is
        // available immediately.
        pg?.popStyle()
        pg?.popFramebuffer()
        updateTexels(x, y, w, h)
    }

    protected fun copyObject(src: Texture) {
        // The OpenGL texture of this object is replaced with the one from the
        // source object, so we delete the former to avoid resource wasting.
        dispose()

        width = src.width
        height = src.height
        glName = src.glName
        glTarget = src.glTarget
        glFormat = src.glFormat
        glMinFilter = src.glMinFilter
        glMagFilter = src.glMagFilter
        glWidth = src.glWidth
        glHeight = src.glHeight
        usingMipmaps = src.usingMipmaps
        usingRepeat = src.usingRepeat
        maxTexcoordU = src.maxTexcoordU
        maxTexcoordV = src.maxTexcoordV
        invertedX = src.invertedX
        invertedY = src.invertedY
    }

    // Releases the memory used by pixelBuffer either if the buffer hasn't been
    // used many times yet, or if the JVM is running low in free memory.
    protected fun releasePixelBuffer() {
        val freeMB = Runtime.getRuntime().freeMemory() / 1E6
        if (pixBufUpdateCount < MAX_UPDATES || freeMB < MIN_MEMORY) {
            pixelBuffer = null
        }
    }

    // Releases the memory used by rgbaPixels either if the array hasn't been
    // used many times yet, or if the JVM is running low in free memory.
    protected fun releaseRGBAPixels() {
        val freeMB = Runtime.getRuntime().freeMemory() / 1E6
        if (rgbaPixUpdateCount < MAX_UPDATES || freeMB < MIN_MEMORY) {
            rgbaPixels = null
        }
    }

    /**
     * Sets texture target and internal format according to the target and
     * type specified.
     * @param target int
     * @param params GLTextureParameters
     */
    ///////////////////////////////////////////////////////////

    // Parameter handling
    var parameters: Parameters
        get() {
            val res = Parameters()
            if (glTarget == PGL.TEXTURE_2D) {
                res.target = TEX2D
            }

            if (glFormat == PGL.RGB) {
                res.format = PConstants.RGB
            } else if (glFormat == PGL.RGBA) {
                res.format = PConstants.ARGB
            } else if (glFormat == PGL.ALPHA) {
                res.format = PConstants.ALPHA
            }

            if (glMagFilter == PGL.NEAREST && glMinFilter == PGL.NEAREST) {
                res.sampling = POINT
                res.mipmaps = false
            } else if (glMagFilter == PGL.NEAREST && glMinFilter == PGL.LINEAR) {
                res.sampling = LINEAR
                res.mipmaps = false
            } else if (glMagFilter == PGL.NEAREST &&
                    glMinFilter == PGL.LINEAR_MIPMAP_NEAREST) {
                res.sampling = LINEAR
                res.mipmaps = true
            } else if (glMagFilter == PGL.LINEAR && glMinFilter == PGL.LINEAR) {
                res.sampling = BILINEAR
                res.mipmaps = false
            } else if (glMagFilter == PGL.LINEAR &&
                    glMinFilter == PGL.LINEAR_MIPMAP_NEAREST) {
                res.sampling = BILINEAR
                res.mipmaps = true
            } else if (glMagFilter == PGL.LINEAR &&
                    glMinFilter == PGL.LINEAR_MIPMAP_LINEAR) {
                res.sampling = TRILINEAR
                res.mipmaps = true
            }

            if (glWrapS == PGL.CLAMP_TO_EDGE) {
                res.wrapU = PConstants.CLAMP
            } else if (glWrapS == PGL.REPEAT) {
                res.wrapU = PConstants.REPEAT
            }
            if (glWrapT == PGL.CLAMP_TO_EDGE) {
                res.wrapV = PConstants.CLAMP
            } else if (glWrapT == PGL.REPEAT) {
                res.wrapV = PConstants.REPEAT
            }

            return res
        }

        protected set(params) {
            glTarget = if (params.target == TEX2D) {
                PGL.TEXTURE_2D
            } else {
                throw RuntimeException("Unknown texture target")
            }

            glFormat = if (params.format == PConstants.RGB) {
                PGL.RGB
            } else if (params.format == PConstants.ARGB) {
                PGL.RGBA
            } else if (params.format == PConstants.ALPHA) {
                PGL.ALPHA
            } else {
                throw RuntimeException("Unknown texture format")
            }

            var mipmaps = params.mipmaps && PGL.MIPMAPS_ENABLED

            if (mipmaps && !PGraphicsOpenGL.autoMipmapGenSupported) {
                PGraphics.showWarning("Mipmaps were requested but automatic mipmap " +
                        "generation is not supported and manual " +
                        "generation still not implemented, so mipmaps " +
                        "will be disabled.")
                mipmaps = false
            }

            if (params.sampling == POINT) {
                glMagFilter = PGL.NEAREST
                glMinFilter = PGL.NEAREST
            } else if (params.sampling == LINEAR) {
                glMagFilter = PGL.NEAREST
                glMinFilter = if (mipmaps) PGL.LINEAR_MIPMAP_NEAREST else PGL.LINEAR
            } else if (params.sampling == BILINEAR) {
                glMagFilter = PGL.LINEAR
                glMinFilter = if (mipmaps) PGL.LINEAR_MIPMAP_NEAREST else PGL.LINEAR
            } else if (params.sampling == TRILINEAR) {
                glMagFilter = PGL.LINEAR
                glMinFilter = if (mipmaps) PGL.LINEAR_MIPMAP_LINEAR else PGL.LINEAR
            } else {
                throw RuntimeException("Unknown texture filtering mode")
            }

            glWrapS = if (params.wrapU == PConstants.CLAMP) {
                PGL.CLAMP_TO_EDGE
            } else if (params.wrapU == PConstants.REPEAT) {
                PGL.REPEAT
            } else {
                throw RuntimeException("Unknown wrapping mode")
            }

            glWrapT = if (params.wrapV == PConstants.CLAMP) {
                PGL.CLAMP_TO_EDGE
            } else if (params.wrapV == PConstants.REPEAT) {
                PGL.REPEAT
            } else {
                throw RuntimeException("Unknown wrapping mode")
            }

            usingMipmaps = glMinFilter == PGL.LINEAR_MIPMAP_NEAREST || glMinFilter == PGL.LINEAR_MIPMAP_LINEAR

            usingRepeat = glWrapS == PGL.REPEAT || glWrapT == PGL.REPEAT

            invertedX = false
            invertedY = false
        }

    protected fun fillEdges(x: Int, y: Int, w: Int, h: Int) {
        if ((width < glWidth || height < glHeight) && (x + w == width || y + h == height)) {
            if (x + w == width) {
                val ew = glWidth - width
                edgePixels = IntArray(h * ew)
                for (i in 0 until h) {
                    val c = rgbaPixels!![i * w + (w - 1)]
                    Arrays.fill(edgePixels, i * ew, (i + 1) * ew, c)
                }
                edgeBuffer = PGL.updateIntBuffer(edgeBuffer, edgePixels, true)
                pgl.texSubImage2D(glTarget, 0, width, y, ew, h, PGL.RGBA,
                        PGL.UNSIGNED_BYTE, edgeBuffer)
            }
            if (y + h == height) {
                val eh = glHeight - height
                edgePixels = IntArray(eh * w)
                for (i in 0 until eh) {
                    System.arraycopy(rgbaPixels, (h - 1) * w, edgePixels, i * w, w)
                }
                edgeBuffer = PGL.updateIntBuffer(edgeBuffer, edgePixels, true)
                pgl.texSubImage2D(glTarget, 0, x, height, w, eh, PGL.RGBA,
                        PGL.UNSIGNED_BYTE, edgeBuffer)
            }
            if (x + w == width && y + h == height) {
                val ew = glWidth - width
                val eh = glHeight - height
                val c = rgbaPixels!![w * h - 1]
                edgePixels = IntArray(eh * ew)
                Arrays.fill(edgePixels, 0, eh * ew, c)
                edgeBuffer = PGL.updateIntBuffer(edgeBuffer, edgePixels, true)
                pgl.texSubImage2D(glTarget, 0, width, height, ew, eh, PGL.RGBA,
                        PGL.UNSIGNED_BYTE, edgeBuffer)
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    // Parameters object
    /**
     * This class stores the parameters for a texture: target, internal format,
     * minimization filter and magnification filter.
     */
    class Parameters {
        /**
         * Texture target.
         */
        var target = 0

        /**
         * Texture internal format.
         */
        var format = 0

        /**
         * Texture filtering (POINT, LINEAR, BILINEAR or TRILINEAR).
         */
        var sampling = 0

        /**
         * Use mipmaps or not.
         */
        var mipmaps = false

        /**
         * Wrapping mode along U.
         */
        var wrapU = 0

        /**
         * Wrapping mode along V.
         */
        var wrapV = 0

        /**
         * Sets all the parameters to default values.
         */
        constructor() {
            target = TEX2D
            format = PConstants.ARGB
            sampling = BILINEAR
            mipmaps = true
            wrapU = PConstants.CLAMP
            wrapV = PConstants.CLAMP
        }

        constructor(format: Int) {
            target = TEX2D
            this.format = format
            sampling = BILINEAR
            mipmaps = true
            wrapU = PConstants.CLAMP
            wrapV = PConstants.CLAMP
        }

        constructor(format: Int, sampling: Int) {
            target = TEX2D
            this.format = format
            this.sampling = sampling
            mipmaps = true
            wrapU = PConstants.CLAMP
            wrapV = PConstants.CLAMP
        }

        constructor(format: Int, sampling: Int, mipmaps: Boolean) {
            target = TEX2D
            this.format = format
            this.mipmaps = mipmaps
            if (sampling == TRILINEAR && !mipmaps) {
                this.sampling = BILINEAR
            } else {
                this.sampling = sampling
            }
            wrapU = PConstants.CLAMP
            wrapV = PConstants.CLAMP
        }

        constructor(format: Int, sampling: Int, mipmaps: Boolean, wrap: Int) {
            target = TEX2D
            this.format = format
            this.mipmaps = mipmaps
            if (sampling == TRILINEAR && !mipmaps) {
                this.sampling = BILINEAR
            } else {
                this.sampling = sampling
            }
            wrapU = wrap
            wrapV = wrap
        }

        constructor(src: Parameters) {
            set(src)
        }

        fun set(format: Int) {
            this.format = format
        }

        operator fun set(format: Int, sampling: Int) {
            this.format = format
            this.sampling = sampling
        }

        operator fun set(format: Int, sampling: Int, mipmaps: Boolean) {
            this.format = format
            this.sampling = sampling
            this.mipmaps = mipmaps
        }

        fun set(src: Parameters) {
            target = src.target
            format = src.format
            sampling = src.sampling
            mipmaps = src.mipmaps
            wrapU = src.wrapU
            wrapV = src.wrapV
        }
    }

    /**
     * This class stores a buffer copied from the buffer source.
     *
     */
    protected inner class BufferData internal constructor(// Native buffer object.
            var natBuf: Any?, // Buffer viewed as int.
            var rgbBuf: IntBuffer?, var w: Int, var h: Int) {

        fun dispose() {
            try {
                // Disposing the native buffer.
                disposeBufferMethod!!.invoke(bufferSource, *arrayOf(natBuf))
                natBuf = null
                rgbBuf = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    companion object {
        /**
         * Texture with normalized UV.
         */
        protected const val TEX2D = 0

        /**
         * Texture with un-normalized UV.
         */
        protected const val TEXRECT = 1

        /** Point sampling: both magnification and minification filtering are set
         * to nearest  */
        const val POINT = 2

        /** Linear sampling: magnification filtering is nearest, minification set
         * to linear  */
        const val LINEAR = 3

        /** Bilinear sampling: both magnification filtering is set to linear and
         * minification either to linear-mipmap-nearest (linear interpolation is used
         * within a mipmap, but not between different mipmaps).  */
        const val BILINEAR = 4

        /** Trilinear sampling: magnification filtering set to linear, minification to
         * linear-mipmap-linear, which offers the best mipmap quality since linear
         * interpolation to compute the value in each of two maps and then
         * interpolates linearly between these two values.  */
        const val TRILINEAR = 5

        // This constant controls how many times pixelBuffer and rgbaPixels can be
        // accessed before they are not released anymore. The idea is that if they
        // have been used only a few times, it doesn't make sense to keep them around.
        protected const val MAX_UPDATES = 10

        // The minimum amount of free JVM's memory (in MB) before pixelBuffer and
        // rgbaPixels are released every time after they are used.
        protected const val MIN_MEMORY = 5
        const val MAX_BUFFER_CACHE_SIZE = 3
    }
}