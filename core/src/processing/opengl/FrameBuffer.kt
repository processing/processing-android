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
import java.nio.IntBuffer

/**
 * Encapsulates a Frame Buffer Object for offscreen rendering.
 * When created with onscreen == true, it represents the normal
 * framebuffer. Needed by the stack mechanism in OPENGL2 to return
 * to onscreen rendering after a sequence of pushFramebuffer calls.
 * It transparently handles the situations when the FBO extension is
 * not available.
 *
 * By Andres Colubri.
 */
open class FrameBuffer(@JvmField var pg: PGraphicsOpenGL?, @JvmField var screenFb: Boolean) : PConstants {
    protected var pgl: PGL?

    @JvmField
    var context // The context that created this framebuffer.
            : Int

    @JvmField
    var glFbo: Int = 0

    @JvmField
    var glDepth: Int = 0

    @JvmField
    var glStencil: Int = 0

    @JvmField
    var glDepthStencil: Int = 0

    @JvmField
    var glMultisample: Int = 0

    @JvmField
    var width: Int = 0

    @JvmField
    var height: Int = 0

    private var glres: PGraphicsOpenGL.GLResourceFrameBuffer? = null

    @JvmField
    var depthBits = 0

    @JvmField
    var stencilBits = 0

    @JvmField
    var packedDepthStencil = false

    @JvmField
    var multisample = false

    protected var nsamples = 0

    protected var numColorBuffers: Int = 0

    protected lateinit var colorBufferTex: Array<Texture?>

    protected var noDepth: Boolean = false

    @JvmField
    var pixelBuffer: IntBuffer? = null

    @JvmOverloads
    constructor(pg: PGraphicsOpenGL?, w: Int, h: Int, samples: Int = 1, colorBuffers: Int = 1,
                depthBits: Int = 0, stencilBits: Int = 0, packedDepthStencil: Boolean = false,
                screen: Boolean = false) : this(pg, false) {

        var samples = samples
        var colorBuffers = colorBuffers
        var depthBits = depthBits
        var stencilBits = stencilBits

        glFbo = 0
        glDepth = 0
        glStencil = 0
        glDepthStencil = 0
        glMultisample = 0

        if (screen) {
            // If this framebuffer is used to represent a on-screen buffer,
            // then it doesn't make it sense for it to have multisampling,
            // color, depth or stencil buffers.
            colorBuffers = 0
            samples = colorBuffers
            stencilBits = samples
            depthBits = stencilBits
        }

        width = w
        height = h

        if (1 < samples) {
            multisample = true
            nsamples = samples
        } else {
            multisample = false
            nsamples = 1
        }

        numColorBuffers = colorBuffers
        colorBufferTex = arrayOfNulls(numColorBuffers)

        for (i in 0 until numColorBuffers) {
            colorBufferTex[i] = null
        }

        if (depthBits < 1 && stencilBits < 1) {
            this.depthBits = 0
            this.stencilBits = 0
            this.packedDepthStencil = false
        } else {
            if (packedDepthStencil) {
                // When combined depth/stencil format is required, the depth and stencil
                // bits are overriden and the 24/8 combination for a 32 bits surface is
                // used.
                this.depthBits = 24
                this.stencilBits = 8
                this.packedDepthStencil = true
            } else {
                this.depthBits = depthBits
                this.stencilBits = stencilBits
                this.packedDepthStencil = false
            }
        }

        screenFb = screen
        allocate()
        noDepth = false
        pixelBuffer = null
    }

    constructor(pg: PGraphicsOpenGL?, w: Int, h: Int, screen: Boolean) : this(pg, w, h, 1, 1, 0, 0, false, screen) {

    }

    fun clear() {
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)
        pgl?.clearDepth(1f)
        pgl?.clearStencil(0)
        pgl?.clearColor(0f, 0f, 0f, 0f)
        pgl?.clear(PGL.DEPTH_BUFFER_BIT or
                PGL.STENCIL_BUFFER_BIT or
                PGL.COLOR_BUFFER_BIT)
        pg?.popFramebuffer()
    }

    fun copyColor(dest: FrameBuffer) {
        copy(dest, PGL.COLOR_BUFFER_BIT)
    }

    fun copyDepth(dest: FrameBuffer) {
        copy(dest, PGL.DEPTH_BUFFER_BIT)
    }

    fun copyStencil(dest: FrameBuffer) {
        copy(dest, PGL.STENCIL_BUFFER_BIT)
    }

    fun copy(dest: FrameBuffer, mask: Int) {
        pgl?.bindFramebufferImpl(PGL.READ_FRAMEBUFFER, glFbo)
        pgl?.bindFramebufferImpl(PGL.DRAW_FRAMEBUFFER, dest.glFbo)
        pgl?.blitFramebuffer(0, 0, width, height,
                0, 0, dest.width, dest.height, mask, PGL.NEAREST)
        pgl?.bindFramebufferImpl(PGL.READ_FRAMEBUFFER, pg?.currentFB!!.glFbo)
        pgl?.bindFramebufferImpl(PGL.DRAW_FRAMEBUFFER, pg?.currentFB!!.glFbo)
    }

    fun bind() {
        pgl?.bindFramebufferImpl(PGL.FRAMEBUFFER, glFbo)
    }

    fun disableDepthTest() {
        noDepth = true
    }

    fun finish() {
        if (noDepth) {
            // No need to clear depth buffer because depth testing was disabled.
            if (pg?.getHint(PConstants.ENABLE_DEPTH_TEST)!!) {
                pgl?.enable(PGL.DEPTH_TEST)
            } else {
                pgl?.disable(PGL.DEPTH_TEST)
            }
        }
    }

    fun readPixels() {
        if (pixelBuffer == null) createPixelBuffer()
        pixelBuffer!!.rewind()
        pgl?.readPixels(0, 0, width, height, PGL.RGBA, PGL.UNSIGNED_BYTE,
                pixelBuffer)
    }

    fun getPixels(pixels: IntArray) {
        if (pixelBuffer != null) {
            pixelBuffer!![pixels, 0, pixels.size]
            pixelBuffer!!.rewind()
        }
    }

    fun hasDepthBuffer(): Boolean {
        return 0 < depthBits
    }

    fun hasStencilBuffer(): Boolean {
        return 0 < stencilBits
    }

    fun setFBO(id: Int) {
        if (screenFb) {
            glFbo = id
        }
    }

    ///////////////////////////////////////////////////////////

    // Color buffer setters.
    fun setColorBuffer(tex: Texture) {
        setColorBuffers(arrayOf(tex), 1)
    }

    fun setColorBuffers(textures: Array<Texture>) {
        setColorBuffers(textures, textures.size)
    }

    fun setColorBuffers(textures: Array<Texture>, n: Int) {
        if (screenFb) return
        if (numColorBuffers != PApplet.min(n, textures.size)) {
            throw RuntimeException("Wrong number of textures to set the color " +
                    "buffers.")
        }
        for (i in 0 until numColorBuffers) {
            colorBufferTex[i] = textures[i]
        }
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)

        // Making sure nothing is attached.
        for (i in 0 until numColorBuffers) {
            pgl?.framebufferTexture2D(PGL.FRAMEBUFFER, PGL.COLOR_ATTACHMENT0 + i,
                    PGL.TEXTURE_2D, 0, 0)
        }
        for (i in 0 until numColorBuffers) {
            pgl?.framebufferTexture2D(PGL.FRAMEBUFFER, PGL.COLOR_ATTACHMENT0 + i,
                    colorBufferTex[i]!!.glTarget,
                    colorBufferTex[i]!!.glName, 0)
        }
        pgl?.validateFramebuffer()
        pg?.popFramebuffer()
    }

    fun swapColorBuffers() {
        for (i in 0 until numColorBuffers - 1) {
            val i1 = i + 1
            val tmp = colorBufferTex[i]
            colorBufferTex[i] = colorBufferTex[i1]
            colorBufferTex[i1] = tmp
        }
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)
        for (i in 0 until numColorBuffers) {
            pgl?.framebufferTexture2D(PGL.FRAMEBUFFER, PGL.COLOR_ATTACHMENT0 + i,
                    colorBufferTex[i]!!.glTarget,
                    colorBufferTex[i]!!.glName, 0)
        }
        pgl?.validateFramebuffer()
        pg?.popFramebuffer()
    }

    val defaultReadBuffer: Int
        get() = if (screenFb) {
            pgl?.defaultReadBuffer!!
        } else {
            PGL.COLOR_ATTACHMENT0
        }

    val defaultDrawBuffer: Int
        get() = if (screenFb) {
            pgl?.defaultDrawBuffer!!
        } else {
            PGL.COLOR_ATTACHMENT0
        }

    ///////////////////////////////////////////////////////////

    // Allocate/release framebuffer.
    protected fun allocate() {
        dispose() // Just in the case this object is being re-allocated.
        context = pgl?.currentContext!!
        glres = PGraphicsOpenGL.GLResourceFrameBuffer(this) // create the FBO resources...
        if (screenFb) {
            glFbo = 0
        } else {
            if (multisample) {
                initColorBufferMultisample()
            }
            if (packedDepthStencil) {
                initPackedDepthStencilBuffer()
            } else {
                if (0 < depthBits) {
                    initDepthBuffer()
                }
                if (0 < stencilBits) {
                    initStencilBuffer()
                }
            }
        }
    }

    fun dispose() {
        if (screenFb) return
        if (glres != null) {
            glres!!.dispose()
            glFbo = 0
            glDepth = 0
            glStencil = 0
            glMultisample = 0
            glDepthStencil = 0
            glres = null
        }
    }

    fun contextIsOutdated(): Boolean {
        if (screenFb) return false
        val outdated = !(pgl?.contextIsCurrent(context))!!
        if (outdated) {
            dispose()
            for (i in 0 until numColorBuffers) {
                colorBufferTex[i] = null
            }
        }
        return outdated
    }

    protected fun initColorBufferMultisample() {
        if (screenFb) return
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)
        pgl?.bindRenderbuffer(PGL.RENDERBUFFER, glMultisample)
        pgl?.renderbufferStorageMultisample(PGL.RENDERBUFFER, nsamples,
                PGL.RGBA8, width, height)
        pgl?.framebufferRenderbuffer(PGL.FRAMEBUFFER, PGL.COLOR_ATTACHMENT0,
                PGL.RENDERBUFFER, glMultisample)
        pg?.popFramebuffer()
    }

    protected fun initPackedDepthStencilBuffer() {
        if (screenFb) return
        if (width == 0 || height == 0) {
            throw RuntimeException("PFramebuffer: size undefined.")
        }
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)
        pgl?.bindRenderbuffer(PGL.RENDERBUFFER, glDepthStencil)
        if (multisample) {
            pgl?.renderbufferStorageMultisample(PGL.RENDERBUFFER, nsamples,
                    PGL.DEPTH24_STENCIL8, width, height)
        } else {
            pgl?.renderbufferStorage(PGL.RENDERBUFFER, PGL.DEPTH24_STENCIL8,
                    width, height)
        }
        pgl?.framebufferRenderbuffer(PGL.FRAMEBUFFER, PGL.DEPTH_ATTACHMENT,
                PGL.RENDERBUFFER, glDepthStencil)
        pgl?.framebufferRenderbuffer(PGL.FRAMEBUFFER, PGL.STENCIL_ATTACHMENT,
                PGL.RENDERBUFFER, glDepthStencil)
        pg?.popFramebuffer()
    }

    protected fun initDepthBuffer() {
        if (screenFb) return
        if (width == 0 || height == 0) {
            throw RuntimeException("PFramebuffer: size undefined.")
        }
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)
        pgl?.bindRenderbuffer(PGL.RENDERBUFFER, glDepth)
        var glConst = PGL.DEPTH_COMPONENT16
        if (depthBits == 16) {
            glConst = PGL.DEPTH_COMPONENT16
        } else if (depthBits == 24) {
            glConst = PGL.DEPTH_COMPONENT24
        } else if (depthBits == 32) {
            glConst = PGL.DEPTH_COMPONENT32
        }
        if (multisample) {
            pgl?.renderbufferStorageMultisample(PGL.RENDERBUFFER, nsamples, glConst,
                    width, height)
        } else {
            pgl?.renderbufferStorage(PGL.RENDERBUFFER, glConst, width, height)
        }
        pgl?.framebufferRenderbuffer(PGL.FRAMEBUFFER, PGL.DEPTH_ATTACHMENT,
                PGL.RENDERBUFFER, glDepth)
        pg?.popFramebuffer()
    }

    protected fun initStencilBuffer() {
        if (screenFb) return
        if (width == 0 || height == 0) {
            throw RuntimeException("PFramebuffer: size undefined.")
        }
        pg?.pushFramebuffer()
        pg?.setFramebuffer(this)
        pgl?.bindRenderbuffer(PGL.RENDERBUFFER, glStencil)
        var glConst = PGL.STENCIL_INDEX1
        if (stencilBits == 1) {
            glConst = PGL.STENCIL_INDEX1
        } else if (stencilBits == 4) {
            glConst = PGL.STENCIL_INDEX4
        } else if (stencilBits == 8) {
            glConst = PGL.STENCIL_INDEX8
        }
        if (multisample) {
            pgl?.renderbufferStorageMultisample(PGL.RENDERBUFFER, nsamples, glConst,
                    width, height)
        } else {
            pgl?.renderbufferStorage(PGL.RENDERBUFFER, glConst, width, height)
        }
        pgl?.framebufferRenderbuffer(PGL.FRAMEBUFFER, PGL.STENCIL_ATTACHMENT,
                PGL.RENDERBUFFER, glStencil)
        pg?.popFramebuffer()
    }

    protected fun createPixelBuffer() {
        pixelBuffer = IntBuffer.allocate(width * height)
//        pixelBuffer.rewind()
        pixelBuffer?.rewind()
    }

    // constructor or initializer block
    init {
        pgl = pg?.pgl
        context = pgl?.createEmptyContext()!!
    }
}