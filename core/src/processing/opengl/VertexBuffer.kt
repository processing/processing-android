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

// TODO: need to combine with PGraphicsOpenGL.VertexAttribute
open class VertexBuffer @JvmOverloads internal constructor(pg: PGraphicsOpenGL?, target: Int, ncoords: Int, esize: Int, index: Boolean = false) {
    @JvmField
    var glId = 0
    @JvmField
    var target: Int
    @JvmField
    var elementSize: Int
    @JvmField
    var ncoords: Int
    @JvmField
    var index: Boolean

    @JvmField
    var pgl: PGL? // The interface between Processing and OpenGL.

    @JvmField
    var context: Int // The context that created this texture.

    private var glres: PGraphicsOpenGL.GLResourceVertexBuffer? = null

    protected fun create() {
        context = pgl!!.currentContext
        glres = PGraphicsOpenGL.GLResourceVertexBuffer(this)
    }

    protected fun init() {
        val size = if (index) ncoords * INIT_INDEX_BUFFER_SIZE * elementSize else ncoords * INIT_VERTEX_BUFFER_SIZE * elementSize
        pgl?.bindBuffer(target, glId)
        pgl?.bufferData(target, size, null, PGL.STATIC_DRAW)
    }

    fun dispose() {
        if (glres != null) {
            glres!!.dispose()
            glId = 0
            glres = null
        }
    }

    protected fun contextIsOutdated(): Boolean {
        val outdated = !(pgl!!.contextIsCurrent(context))
        if (outdated) {
            dispose()
        }
        return outdated
    }

    companion object {
        protected const val INIT_VERTEX_BUFFER_SIZE = 256
        protected const val INIT_INDEX_BUFFER_SIZE = 512
    }

    init {
        pgl = pg?.pgl
        context = pgl!!.createEmptyContext()
        this.target = target
        this.ncoords = ncoords
        elementSize = esize
        this.index = index
        create()
        init()
    }
}