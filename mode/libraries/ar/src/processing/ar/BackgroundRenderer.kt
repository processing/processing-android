/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

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

package processing.ar

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20

import com.google.ar.core.Frame

import processing.ar.ShaderUtils.checkGLError
import processing.ar.ShaderUtils.loadGLShader

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BackgroundRenderer(context: Context?) {
    private var textureId: Int = -1
    private val quadVertices: FloatBuffer
    private val quadTexCoord: FloatBuffer
    private val quadTexCoordTransformed: FloatBuffer
    private val quadProgram: Int
    private val quadPositionParam: Int
    private val quadTexCoordParam: Int
    private val VERTICES_ERROR = "Unexpected number of vertices in BackgroundRenderer"
    private val ERROR_TAG = "Error"
    private val CREATION_ERROR = "Program creation"
    private val PARAMETERS_ERROR = "Program parameters"
    private val DRAW_ERROR = "Draw"

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformDisplayUvCoords(quadTexCoord, quadTexCoordTransformed)
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(quadProgram)
        GLES20.glVertexAttribPointer(
                quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadVertices)
        GLES20.glVertexAttribPointer(
                quadTexCoordParam,
                TEXCOORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                0,
                quadTexCoordTransformed)
        GLES20.glEnableVertexAttribArray(quadPositionParam)
        GLES20.glEnableVertexAttribArray(quadTexCoordParam)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(quadPositionParam)
        GLES20.glDisableVertexAttribArray(quadTexCoordParam)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        checkGLError(ERROR_TAG, DRAW_ERROR)
    }

    companion object {
        private const val COORDS_PER_VERTEX = 3
        private const val TEXCOORDS_PER_VERTEX = 2
        private const val FLOAT_SIZE = 4
        private val screenquad_vertex = BackgroundRenderer::class.java.getResource("/assets/shaders/BackgroundVert.glsl")
        private val screenquad_fragment = BackgroundRenderer::class.java.getResource("/assets/shaders/BackgroundFrag.glsl")
        private val QUAD_COORDS = floatArrayOf(
                -1.0f, -1.0f, 0.0f,
                -1.0f, +1.0f, 0.0f,
                +1.0f, -1.0f, 0.0f,
                +1.0f, +1.0f, 0.0f)
        private val QUAD_TEXCOORDS = floatArrayOf(
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 1.0f,
                1.0f, 0.0f)
    }

    init {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        val numVertices = 4
        if (numVertices != QUAD_COORDS.size / COORDS_PER_VERTEX) {
            throw RuntimeException(VERTICES_ERROR)
        }
        val bbVertices = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
        bbVertices.order(ByteOrder.nativeOrder())
        quadVertices = bbVertices.asFloatBuffer()
        quadVertices.put(QUAD_COORDS)
        quadVertices.position(0)
        val bbTexCoords = ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCoords.order(ByteOrder.nativeOrder())
        quadTexCoord = bbTexCoords.asFloatBuffer()
        quadTexCoord.put(QUAD_TEXCOORDS)
        quadTexCoord.position(0)
        val bbTexCoordsTransformed = ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCoordsTransformed.order(ByteOrder.nativeOrder())
        quadTexCoordTransformed = bbTexCoordsTransformed.asFloatBuffer()
        val vertexShader = loadGLShader(ERROR_TAG, context, GLES20.GL_VERTEX_SHADER, screenquad_vertex)
        val fragmentShader = loadGLShader(
                ERROR_TAG, context, GLES20.GL_FRAGMENT_SHADER, screenquad_fragment)
        quadProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(quadProgram, vertexShader)
        GLES20.glAttachShader(quadProgram, fragmentShader)
        GLES20.glLinkProgram(quadProgram)
        GLES20.glUseProgram(quadProgram)
        checkGLError(ERROR_TAG, CREATION_ERROR)
        quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position")
        quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord")
        checkGLError(ERROR_TAG, PARAMETERS_ERROR)
    }

    fun getTextureId(): Int {
        return this.textureId
    }
}