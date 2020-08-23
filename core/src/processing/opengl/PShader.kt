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

import processing.core.*
import java.net.URL
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.*

/**
 * @author Aditya Rana
 * This class encapsulates a GLSL shader program, including a vertex
 * and a fragment shader. Based on the GLSLShader class from GLGraphics, which
 * in turn was originally based in the code by JohnG:
 * http://processing.org/discourse/beta/num_1159494801.html
 *
 * @webref rendering:shaders
 */
open class PShader : PConstants {

    protected var parent: PApplet?

    // The main renderer associated to the parent PApplet.
    //protected PGraphicsOpenGL pgMain;
    // We need a reference to the renderer since a shader might
    // be called by different renderers within a single application
    // (the one corresponding to the main surface, or other offscreen
    // renderers).
    @JvmField
    protected var primaryPG: PGraphicsOpenGL? = null

    @JvmField
    protected var currentPG: PGraphicsOpenGL? = null

    @JvmField
    var pgl: PGL?

    @JvmField
    var context = 0 // The context that created this shader.

    @JvmField var type = 0

    @JvmField
    var glProgram: Int

    @JvmField
    var glVertex: Int

    @JvmField
    var glFragment: Int

    private var glres: PGraphicsOpenGL.GLResourceShader? = null

    protected var vertexURL: URL?
    protected var fragmentURL: URL?

    protected var vertexFilename: String?
    protected var fragmentFilename: String?

    protected var vertexShaderSource: Array<String>?    = null
    protected var fragmentShaderSource: Array<String>?  = null

    protected var bound = false

    protected var uniformValues: HashMap<Int, UniformValue>? = null
    protected var textures: HashMap<Int, Texture>? = null
    protected var texUnits: HashMap<Int, Int?>? = null

    // Direct buffers to pass shader data to GL
    protected var intBuffer: IntBuffer
    protected var floatBuffer: FloatBuffer
    protected var loadedAttributes = false
    protected var loadedUniforms = false

    // Uniforms common to all shader types
    protected var transformMatLoc   = 0
    protected var modelviewMatLoc   = 0
    protected var projectionMatLoc  = 0
    protected var ppixelsLoc        = 0
    protected var ppixelsUnit       = 0
    protected var viewportLoc       = 0
    protected var resolutionLoc     = 0

    // Uniforms only for lines and points
    protected var perspectiveLoc    = 0
    protected var scaleLoc          = 0

    // Lighting uniforms
    protected var lightCountLoc     = 0
    protected var lightPositionLoc  = 0
    protected var lightNormalLoc    = 0
    protected var lightAmbientLoc   = 0
    protected var lightDiffuseLoc   = 0
    protected var lightSpecularLoc  = 0
    protected var lightFalloffLoc   = 0
    protected var lightSpotLoc      = 0

    // Texturing uniforms
    private var texture: Texture? = null

    protected var texUnit       = 0
    protected var textureLoc    = 0
    protected var texMatrixLoc  = 0
    protected var texOffsetLoc  = 0
    protected var tcmat: FloatArray? = null

    // Vertex attributes
    protected var vertexLoc     = 0
    protected var colorLoc      = 0
    protected var normalLoc     = 0
    protected var texCoordLoc   = 0
    protected var normalMatLoc  = 0
    protected var directionLoc  = 0
    protected var offsetLoc     = 0
    protected var ambientLoc    = 0
    protected var specularLoc   = 0
    protected var emissiveLoc   = 0
    protected var shininessLoc  = 0

    constructor() {
        parent = null
        pgl = null

        context = -1

        vertexURL = null
        fragmentURL = null
        vertexFilename = null
        fragmentFilename = null

        glProgram = 0
        glVertex = 0
        glFragment = 0

        intBuffer = PGL.allocateIntBuffer(1)
        floatBuffer = PGL.allocateFloatBuffer(1)

        bound = false

        type = -1
    }

    constructor(parent: PApplet?) : this() {
        this.parent = parent
        primaryPG = parent?.graphics as PGraphicsOpenGL?
        pgl = primaryPG!!.pgl
        context = pgl!!.createEmptyContext()
    }

    /**
     * Creates a shader program using the specified vertex and fragment
     * shaders.
     *
     * @param parent the parent program
     * @param vertFilename name of the vertex shader
     * @param fragFilename name of the fragment shader
     */
    constructor(parent: PApplet?, vertFilename: String?, fragFilename: String?) {
        this.parent = parent

        primaryPG = parent?.graphics as PGraphicsOpenGL?
        pgl = primaryPG!!.pgl

        vertexURL = null
        fragmentURL = null

        vertexFilename = vertFilename
        fragmentFilename = fragFilename

        fragmentShaderSource = pgl?.loadFragmentShader(fragFilename)
        vertexShaderSource = pgl?.loadVertexShader(vertFilename)

        glProgram = 0
        glVertex = 0
        glFragment = 0

        intBuffer = PGL.allocateIntBuffer(1)
        floatBuffer = PGL.allocateFloatBuffer(1)

        val vertType = getShaderType(vertexShaderSource, -1)
        val fragType = getShaderType(fragmentShaderSource, -1)

        if (vertType == -1 && fragType == -1) {
            type = POLY
        } else if (vertType == -1) {
            type = fragType
        } else if (fragType == -1) {
            type = vertType
        } else if (fragType == vertType) {
            type = vertType
        } else {
            PGraphics.showWarning(PGraphicsOpenGL.INCONSISTENT_SHADER_TYPES)
        }
    }

    /**
     * @param vertURL network location of the vertex shader
     * @param fragURL network location of the fragment shader
     */
    constructor(parent: PApplet?, vertURL: URL?, fragURL: URL?) {
        this.parent = parent
        primaryPG = parent?.graphics as PGraphicsOpenGL?
        pgl = primaryPG!!.pgl

        vertexURL = vertURL
        fragmentURL = fragURL

        vertexFilename = null
        fragmentFilename = null

        fragmentShaderSource = pgl?.loadFragmentShader(fragURL)
        vertexShaderSource = pgl?.loadVertexShader(vertURL)

        glProgram   = 0
        glVertex    = 0
        glFragment  = 0

        intBuffer = PGL.allocateIntBuffer(1)
        floatBuffer = PGL.allocateFloatBuffer(1)

        val vertType = getShaderType(vertexShaderSource, -1)
        val fragType = getShaderType(fragmentShaderSource, -1)

        if (vertType == -1 && fragType == -1) {
            type = POLY
        } else if (vertType == -1) {
            type = fragType
        } else if (fragType == -1) {
            type = vertType
        } else if (fragType == vertType) {
            type = vertType
        } else {
            PGraphics.showWarning(PGraphicsOpenGL.INCONSISTENT_SHADER_TYPES)
        }
    }

    constructor(parent: PApplet?, vertSource: Array<String>?, fragSource: Array<String>?) {
        this.parent = parent
        primaryPG = parent?.graphics as PGraphicsOpenGL?
        pgl = primaryPG!!.pgl
        vertexURL = null
        fragmentURL = null
        vertexFilename = null
        fragmentFilename = null

        vertexShaderSource = vertSource
        fragmentShaderSource = fragSource

        glProgram = 0
        glVertex = 0
        glFragment = 0
        intBuffer = PGL.allocateIntBuffer(1)
        floatBuffer = PGL.allocateFloatBuffer(1)

        val vertType = getShaderType(vertexShaderSource, -1)
        val fragType = getShaderType(fragmentShaderSource, -1)

        if (vertType == -1 && fragType == -1) {
            type = POLY
        } else if (vertType == -1) {
            type = fragType
        } else if (fragType == -1) {
            type = vertType
        } else if (fragType == vertType) {
            type = vertType
        } else {
            PGraphics.showWarning(PGraphicsOpenGL.INCONSISTENT_SHADER_TYPES)
        }
    }

    fun setVertexShader(vertFilename: String?) {
        vertexFilename = vertFilename
        vertexShaderSource = pgl!!.loadVertexShader(vertFilename)
    }

    fun setVertexShader(vertURL: URL?) {
        vertexURL = vertURL
        vertexShaderSource = pgl!!.loadVertexShader(vertURL)
    }

    fun setVertexShader(vertSource: Array<String>?) {
        vertexShaderSource = vertSource
    }

    fun setFragmentShader(fragFilename: String?) {
        fragmentFilename = fragFilename
        fragmentShaderSource = pgl!!.loadFragmentShader(fragFilename)
    }

    fun setFragmentShader(fragURL: URL?) {
        fragmentURL = fragURL
        fragmentShaderSource = pgl!!.loadFragmentShader(fragURL)
    }

    fun setFragmentShader(fragSource: Array<String>?) {
        fragmentShaderSource = fragSource
    }

    /**
     * Initializes (if needed) and binds the shader program.
     */
    fun bind() {
        init()
        if (!bound) {
            pgl!!.useProgram(glProgram)
            bound = true
            consumeUniforms()
            bindTextures()
        }
        if (hasType()) bindTyped()
    }

    /**
     * Unbinds the shader program.
     */
    fun unbind() {
        if (hasType()) unbindTyped()
        if (bound) {
            unbindTextures()
            pgl!!.useProgram(0)
            bound = false
        }
    }

    /**
     * Returns true if the shader is bound, false otherwise.
     */
    fun bound(): Boolean {
        return bound
    }

    /**
     * @webref rendering:shaders
     * @brief Sets a variable within the shader
     * @param name the name of the uniform variable to modify
     * @param x first component of the variable to modify
     */
    operator fun set(name: String, x: Int) {
        setUniformImpl(name, UniformValue.INT1, intArrayOf(x))
    }

    /**
     * @param y second component of the variable to modify. The variable has to be declared with an array/vector type in the shader (i.e.: int[2], vec2)
     */
    operator fun set(name: String, x: Int, y: Int) {
        setUniformImpl(name, UniformValue.INT2, intArrayOf(x, y))
    }

    /**
     * @param z third component of the variable to modify. The variable has to be declared with an array/vector type in the shader (i.e.: int[3], vec3)
     */
    operator fun set(name: String, x: Int, y: Int, z: Int) {
        setUniformImpl(name, UniformValue.INT3, intArrayOf(x, y, z))
    }

    /**
     * @param w fourth component of the variable to modify. The variable has to be declared with an array/vector type in the shader (i.e.: int[4], vec4)
     */
    operator fun set(name: String, x: Int, y: Int, z: Int, w: Int) {
        setUniformImpl(name, UniformValue.INT4, intArrayOf(x, y, z, w))
    }

    operator fun set(name: String, x: Float) {
        setUniformImpl(name, UniformValue.FLOAT1, floatArrayOf(x))
    }

    operator fun set(name: String, x: Float, y: Float) {
        setUniformImpl(name, UniformValue.FLOAT2, floatArrayOf(x, y))
    }

    operator fun set(name: String, x: Float, y: Float, z: Float) {
        setUniformImpl(name, UniformValue.FLOAT3, floatArrayOf(x, y, z))
    }

    operator fun set(name: String, x: Float, y: Float, z: Float, w: Float) {
        setUniformImpl(name, UniformValue.FLOAT4, floatArrayOf(x, y, z, w))
    }

    /**
     * @param vec modifies all the components of an array/vector uniform variable. PVector can only be used if the type of the variable is vec3.
     */
    operator fun set(name: String, vec: PVector) {
        setUniformImpl(name, UniformValue.FLOAT3, floatArrayOf(vec.x, vec.y, vec.z))
    }

    operator fun set(name: String, x: Boolean) {
        setUniformImpl(name, UniformValue.INT1, intArrayOf(if (x) 1 else 0))
    }

    operator fun set(name: String, x: Boolean, y: Boolean) {
        setUniformImpl(name, UniformValue.INT2, intArrayOf(if (x) 1 else 0, if (y) 1 else 0))
    }

    operator fun set(name: String, x: Boolean, y: Boolean, z: Boolean) {
        setUniformImpl(name, UniformValue.INT3, intArrayOf(if (x) 1 else 0, if (y) 1 else 0, if (z) 1 else 0))
    }

    operator fun set(name: String, x: Boolean, y: Boolean, z: Boolean, w: Boolean) {
        setUniformImpl(name, UniformValue.INT4, intArrayOf(if (x) 1 else 0, if (y) 1 else 0, if (z) 1 else 0, if (w) 1 else 0))
    }

    /**
     * @param ncoords number of coordinates per element, max 4
     */
    operator fun set(name: String, vec: IntArray, ncoords: Int) {
        if (ncoords == 1) {
            setUniformImpl(name, UniformValue.INT1VEC, vec)
        } else if (ncoords == 2) {
            setUniformImpl(name, UniformValue.INT2VEC, vec)
        } else if (ncoords == 3) {
            setUniformImpl(name, UniformValue.INT3VEC, vec)
        } else if (ncoords == 4) {
            setUniformImpl(name, UniformValue.INT4VEC, vec)
        } else if (4 < ncoords) {
            PGraphics.showWarning("Only up to 4 coordinates per element are " +
                    "supported.")
        } else {
            PGraphics.showWarning("Wrong number of coordinates: it is negative!")
        }
    }

    @JvmOverloads
    fun set(name: String, vec: FloatArray, ncoords: Int = 1) {
        if (ncoords == 1) {
            setUniformImpl(name, UniformValue.FLOAT1VEC, vec)
        } else if (ncoords == 2) {
            setUniformImpl(name, UniformValue.FLOAT2VEC, vec)
        } else if (ncoords == 3) {
            setUniformImpl(name, UniformValue.FLOAT3VEC, vec)
        } else if (ncoords == 4) {
            setUniformImpl(name, UniformValue.FLOAT4VEC, vec)
        } else if (4 < ncoords) {
            PGraphics.showWarning("Only up to 4 coordinates per element are " +
                    "supported.")
        } else {
            PGraphics.showWarning("Wrong number of coordinates: it is negative!")
        }
    }

    operator fun set(name: String, vec: BooleanArray) {
        set(name, vec, 1)
    }

    operator fun set(name: String, boolvec: BooleanArray, ncoords: Int) {
        val vec = IntArray(boolvec.size)
        for (i in boolvec.indices) {
            vec[i] = if (boolvec[i]) 1 else 0
        }
        set(name, vec, ncoords)
    }

    /**
     * @param mat matrix of values
     */
    operator fun set(name: String, mat: PMatrix2D) {
        val matv = floatArrayOf(mat.m00, mat.m01,
                mat.m10, mat.m11)
        setUniformImpl(name, UniformValue.MAT2, matv)
    }

    /**
     * @param use3x3 enforces the matrix is 3 x 3
     */
    @JvmOverloads
    fun set(name: String, mat: PMatrix3D, use3x3: Boolean = false) {
        if (use3x3) {
            val matv = floatArrayOf(mat.m00, mat.m01, mat.m02,
                    mat.m10, mat.m11, mat.m12,
                    mat.m20, mat.m21, mat.m22)
            setUniformImpl(name, UniformValue.MAT3, matv)
        } else {
            val matv = floatArrayOf(mat.m00, mat.m01, mat.m02, mat.m03,
                    mat.m10, mat.m11, mat.m12, mat.m13,
                    mat.m20, mat.m21, mat.m22, mat.m23,
                    mat.m30, mat.m31, mat.m32, mat.m33)
            setUniformImpl(name, UniformValue.MAT4, matv)
        }
    }

    /**
     * @param tex sets the sampler uniform variable to read from this image texture
     */
    operator fun set(name: String, tex: PImage) {
        setUniformImpl(name, UniformValue.SAMPLER2D, tex)
    }

    /**
     * Extra initialization method that can be used by subclasses, called after
     * compiling and attaching the vertex and fragment shaders, and before
     * linking the shader program.
     *
     */
    protected fun setup() {}
    fun draw(idxId: Int, count: Int, offset: Int) {
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, idxId)
        pgl!!.drawElements(PGL.TRIANGLES, count, PGL.INDEX_TYPE,
                offset * PGL.SIZEOF_INDEX)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, 0)
    }

    /**
     * Returns the ID location of the attribute parameter given its name.
     *
     * @param name String
     * @return int
     */
    fun getAttributeLoc(name: String?): Int {
        init()
        return pgl!!.getAttribLocation(glProgram, name)
    }

    /**
     * Returns the ID location of the uniform parameter given its name.
     *
     * @param name String
     * @return int
     */
    fun getUniformLoc(name: String?): Int {
        init()
        return pgl!!.getUniformLocation(glProgram, name)
    }

    fun setAttributeVBO(loc: Int, vboId: Int, size: Int, type: Int,
                        normalized: Boolean, stride: Int, offset: Int) {
        if (-1 < loc) {
            pgl!!.bindBuffer(PGL.ARRAY_BUFFER, vboId)
            pgl!!.vertexAttribPointer(loc, size, type, normalized, stride, offset)
        }
    }

    protected fun setUniformValue(loc: Int, x: Int) {
        if (-1 < loc) {
            pgl!!.uniform1i(loc, x)
        }
    }

    protected fun setUniformValue(loc: Int, x: Int, y: Int) {
        if (-1 < loc) {
            pgl!!.uniform2i(loc, x, y)
        }
    }

    protected fun setUniformValue(loc: Int, x: Int, y: Int, z: Int) {
        if (-1 < loc) {
            pgl!!.uniform3i(loc, x, y, z)
        }
    }

    protected fun setUniformValue(loc: Int, x: Int, y: Int, z: Int, w: Int) {
        if (-1 < loc) {
            pgl!!.uniform4i(loc, x, y, z, w)
        }
    }

    protected fun setUniformValue(loc: Int, x: Float) {
        if (-1 < loc) {
            pgl!!.uniform1f(loc, x)
        }
    }

    protected fun setUniformValue(loc: Int, x: Float, y: Float) {
        if (-1 < loc) {
            pgl!!.uniform2f(loc, x, y)
        }
    }

    protected fun setUniformValue(loc: Int, x: Float, y: Float, z: Float) {
        if (-1 < loc) {
            pgl!!.uniform3f(loc, x, y, z)
        }
    }

    protected fun setUniformValue(loc: Int, x: Float, y: Float, z: Float, w: Float) {
        if (-1 < loc) {
            pgl!!.uniform4f(loc, x, y, z, w)
        }
    }

    protected fun setUniformVector(loc: Int, vec: IntArray?, ncoords: Int,
                                   length: Int) {
        if (-1 < loc) {
            updateIntBuffer(vec)
            if (ncoords == 1) {
                pgl!!.uniform1iv(loc, length, intBuffer)
            } else if (ncoords == 2) {
                pgl!!.uniform2iv(loc, length, intBuffer)
            } else if (ncoords == 3) {
                pgl!!.uniform3iv(loc, length, intBuffer)
            } else if (ncoords == 4) {
                pgl!!.uniform3iv(loc, length, intBuffer)
            }
        }
    }

    protected fun setUniformVector(loc: Int, vec: FloatArray?, ncoords: Int,
                                   length: Int) {
        if (-1 < loc) {
            updateFloatBuffer(vec)
            if (ncoords == 1) {
                pgl!!.uniform1fv(loc, length, floatBuffer)
            } else if (ncoords == 2) {
                pgl!!.uniform2fv(loc, length, floatBuffer)
            } else if (ncoords == 3) {
                pgl!!.uniform3fv(loc, length, floatBuffer)
            } else if (ncoords == 4) {
                pgl!!.uniform4fv(loc, length, floatBuffer)
            }
        }
    }

    protected fun setUniformMatrix(loc: Int, mat: FloatArray) {
        if (-1 < loc) {
            updateFloatBuffer(mat)
            if (mat.size == 4) {
                pgl!!.uniformMatrix2fv(loc, 1, false, floatBuffer)
            } else if (mat.size == 9) {
                pgl!!.uniformMatrix3fv(loc, 1, false, floatBuffer)
            } else if (mat.size == 16) {
                pgl!!.uniformMatrix4fv(loc, 1, false, floatBuffer)
            }
        }
    }

    protected fun setUniformTex(loc: Int, tex: Texture) {
        if (texUnits != null) {
            val unit = texUnits!![loc]
            if (unit != null) {
                pgl!!.activeTexture(PGL.TEXTURE0 + unit)
                tex.bind()
            } else {
                throw RuntimeException("Cannot find unit for texture $tex")
            }
        }
    }

    protected fun setUniformImpl(name: String, type: Int, value: Any) {
        val loc = getUniformLoc(name)
        if (-1 < loc) {
            if (uniformValues == null) {
                uniformValues = HashMap()
            }
            uniformValues!![loc] = UniformValue(type, value)
        } else {
            PGraphics.showWarning("The shader doesn't have a uniform called \"" +
                    name + "\" OR the uniform was removed during " +
                    "compilation because it was unused.")
        }
    }

    protected fun consumeUniforms() {
        if (uniformValues != null && 0 < uniformValues!!.size) {
            var unit = 0
            for (loc in uniformValues!!.keys) {
                val `val` = uniformValues!![loc]
                if (`val`!!.type == UniformValue.INT1) {
                    val v = `val`.value as IntArray
                    pgl!!.uniform1i(loc, v[0])
                } else if (`val`.type == UniformValue.INT2) {
                    val v = `val`.value as IntArray
                    pgl!!.uniform2i(loc, v[0], v[1])
                } else if (`val`.type == UniformValue.INT3) {
                    val v = `val`.value as IntArray
                    pgl!!.uniform3i(loc, v[0], v[1], v[2])
                } else if (`val`.type == UniformValue.INT4) {
                    val v = `val`.value as IntArray
                    pgl!!.uniform4i(loc, v[0], v[1], v[2], v[3])
                } else if (`val`.type == UniformValue.FLOAT1) {
                    val v = `val`.value as FloatArray
                    pgl!!.uniform1f(loc, v[0])
                } else if (`val`.type == UniformValue.FLOAT2) {
                    val v = `val`.value as FloatArray
                    pgl!!.uniform2f(loc, v[0], v[1])
                } else if (`val`.type == UniformValue.FLOAT3) {
                    val v = `val`.value as FloatArray
                    pgl!!.uniform3f(loc, v[0], v[1], v[2])
                } else if (`val`.type == UniformValue.FLOAT4) {
                    val v = `val`.value as FloatArray
                    pgl!!.uniform4f(loc, v[0], v[1], v[2], v[3])
                } else if (`val`.type == UniformValue.INT1VEC) {
                    val v = `val`.value as IntArray
                    updateIntBuffer(v)
                    pgl!!.uniform1iv(loc, v.size, intBuffer)
                } else if (`val`.type == UniformValue.INT2VEC) {
                    val v = `val`.value as IntArray
                    updateIntBuffer(v)
                    pgl!!.uniform2iv(loc, v.size / 2, intBuffer)
                } else if (`val`.type == UniformValue.INT3VEC) {
                    val v = `val`.value as IntArray
                    updateIntBuffer(v)
                    pgl!!.uniform3iv(loc, v.size / 3, intBuffer)
                } else if (`val`.type == UniformValue.INT4VEC) {
                    val v = `val`.value as IntArray
                    updateIntBuffer(v)
                    pgl!!.uniform4iv(loc, v.size / 4, intBuffer)
                } else if (`val`.type == UniformValue.FLOAT1VEC) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniform1fv(loc, v.size, floatBuffer)
                } else if (`val`.type == UniformValue.FLOAT2VEC) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniform2fv(loc, v.size / 2, floatBuffer)
                } else if (`val`.type == UniformValue.FLOAT3VEC) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniform3fv(loc, v.size / 3, floatBuffer)
                } else if (`val`.type == UniformValue.FLOAT4VEC) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniform4fv(loc, v.size / 4, floatBuffer)
                } else if (`val`.type == UniformValue.MAT2) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniformMatrix2fv(loc, 1, false, floatBuffer)
                } else if (`val`.type == UniformValue.MAT3) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniformMatrix3fv(loc, 1, false, floatBuffer)
                } else if (`val`.type == UniformValue.MAT4) {
                    val v = `val`.value as FloatArray
                    updateFloatBuffer(v)
                    pgl!!.uniformMatrix4fv(loc, 1, false, floatBuffer)
                } else if (`val`.type == UniformValue.SAMPLER2D) {
                    val img = `val`.value as PImage
                    val tex = currentPG!!.getTexture(img)
                    if (textures == null) textures = HashMap()
                    textures!![loc] = tex
                    if (texUnits == null) texUnits = HashMap()
                    if (texUnits!!.containsKey(loc)) {
                        unit = texUnits!![loc]!!
                        pgl!!.uniform1i(loc, unit)
                    } else {
                        texUnits!![loc] = unit
                        pgl!!.uniform1i(loc, unit)
                    }
                    unit++
                }
            }
            uniformValues!!.clear()
        }
    }

    protected fun updateIntBuffer(vec: IntArray?) {
        intBuffer = PGL.updateIntBuffer(intBuffer, vec, false)
    }

    protected fun updateFloatBuffer(vec: FloatArray?) {
        floatBuffer = PGL.updateFloatBuffer(floatBuffer, vec, false)
    }

    protected fun bindTextures() {
        if (textures != null && texUnits != null) {
            for (loc in textures!!.keys) {
                val tex = textures!![loc]
                val unit = texUnits!![loc]
                if (unit != null) {
                    pgl!!.activeTexture(PGL.TEXTURE0 + unit)
                    tex!!.bind()
                } else {
                    throw RuntimeException("Cannot find unit for texture $tex")
                }
            }
        }
    }

    protected fun unbindTextures() {
        if (textures != null && texUnits != null) {
            for (loc in textures!!.keys) {
                val tex = textures!![loc]
                val unit = texUnits!![loc]
                if (unit != null) {
                    pgl!!.activeTexture(PGL.TEXTURE0 + unit)
                    tex!!.unbind()
                } else {
                    throw RuntimeException("Cannot find unit for texture $tex")
                }
            }
            pgl!!.activeTexture(PGL.TEXTURE0)
        }
    }

    fun init() {
        if (glProgram == 0 || contextIsOutdated()) {
            create()
            if (compile()) {
                pgl!!.attachShader(glProgram, glVertex)
                pgl!!.attachShader(glProgram, glFragment)
                setup()
                pgl!!.linkProgram(glProgram)
                validate()
            }
        }
    }

    protected fun create() {
        context = pgl!!.currentContext
        glres = PGraphicsOpenGL.GLResourceShader(this)
    }

    protected fun compile(): Boolean {
        var vertRes = true
        if (hasVertexShader()) {
            vertRes = compileVertexShader()
        } else {
            PGraphics.showException("Doesn't have a vertex shader")
        }
        var fragRes = true
        if (hasFragmentShader()) {
            fragRes = compileFragmentShader()
        } else {
            PGraphics.showException("Doesn't have a fragment shader")
        }
        return vertRes && fragRes
    }

    protected fun validate() {
        pgl!!.getProgramiv(glProgram, PGL.LINK_STATUS, intBuffer)
        val linked = intBuffer[0] != 0
        if (!linked) {
            PGraphics.showException("""
    Cannot link shader program:
    ${pgl!!.getProgramInfoLog(glProgram)}
    """.trimIndent())
        }
        pgl!!.validateProgram(glProgram)
        pgl!!.getProgramiv(glProgram, PGL.VALIDATE_STATUS, intBuffer)
        val validated = intBuffer[0] != 0
        if (!validated) {
            PGraphics.showException("""
    Cannot validate shader program:
    ${pgl!!.getProgramInfoLog(glProgram)}
    """.trimIndent())
        }
    }

    protected fun contextIsOutdated(): Boolean {
        val outdated = !pgl!!.contextIsCurrent(context)
        if (outdated) {
            dispose()
        }
        return outdated
    }

    protected fun hasVertexShader(): Boolean {
        return vertexShaderSource != null && 0 < vertexShaderSource!!.size
    }

    protected fun hasFragmentShader(): Boolean {
        return fragmentShaderSource != null && 0 < fragmentShaderSource!!.size
    }

    /**
     * @param shaderSource a string containing the shader's code
     */
    protected fun compileVertexShader(): Boolean {
        pgl!!.shaderSource(glVertex, PApplet.join(vertexShaderSource, "\n"))
        pgl!!.compileShader(glVertex)
        pgl!!.getShaderiv(glVertex, PGL.COMPILE_STATUS, intBuffer)
        val compiled = intBuffer[0] != 0
        return if (!compiled) {
            PGraphics.showException("""
    Cannot compile vertex shader:
    ${pgl!!.getShaderInfoLog(glVertex)}
    """.trimIndent())
            false
        } else {
            true
        }
    }

    /**
     * @param shaderSource a string containing the shader's code
     */
    protected fun compileFragmentShader(): Boolean {
        pgl!!.shaderSource(glFragment, PApplet.join(fragmentShaderSource, "\n"))
        pgl!!.compileShader(glFragment)
        pgl!!.getShaderiv(glFragment, PGL.COMPILE_STATUS, intBuffer)
        val compiled = intBuffer[0] != 0
        return if (!compiled) {
            PGraphics.showException("""
    Cannot compile fragment shader:
    ${pgl!!.getShaderInfoLog(glFragment)}
    """.trimIndent())
            false
        } else {
            true
        }
    }

    protected fun dispose() {
        if (glres != null) {
            glres!!.dispose()
            glVertex = 0
            glFragment = 0
            glProgram = 0
            glres = null
        }
    }

    protected fun hasType(): Boolean {
        return type in POINT..TEXLIGHT
    }

    val isPointShader: Boolean
        get() = type == POINT

    val isLineShader: Boolean
        get() = type == LINE

    val isPolyShader: Boolean
        get() = type in POLY..TEXLIGHT

    fun checkPolyType(type: Int): Boolean {
        if (type == POLY) return true
        if (type != type) {
            if (type == TEXLIGHT) {
                PGraphics.showWarning(PGraphicsOpenGL.NO_TEXLIGHT_SHADER_ERROR)
            } else if (type == LIGHT) {
                PGraphics.showWarning(PGraphicsOpenGL.NO_LIGHT_SHADER_ERROR)
            } else if (type == TEXTURE) {
                PGraphics.showWarning(PGraphicsOpenGL.NO_TEXTURE_SHADER_ERROR)
            } else if (type == COLOR) {
                PGraphics.showWarning(PGraphicsOpenGL.NO_COLOR_SHADER_ERROR)
            }
            return false
        }
        return true
    }

    protected val lastTexUnit: Int
        get() = if (texUnits == null) -1 else texUnits!!.size - 1

    fun setRenderer(pg: PGraphicsOpenGL?) {
        currentPG = pg
    }

    fun loadAttributes() {
        if (loadedAttributes) return

        vertexLoc = getAttributeLoc("vertex")

        if (vertexLoc == -1) vertexLoc = getAttributeLoc("position")

        colorLoc = getAttributeLoc("color")
        texCoordLoc = getAttributeLoc("texCoord")
        normalLoc = getAttributeLoc("normal")
        ambientLoc = getAttributeLoc("ambient")
        specularLoc = getAttributeLoc("specular")
        emissiveLoc = getAttributeLoc("emissive")
        shininessLoc = getAttributeLoc("shininess")
        directionLoc = getAttributeLoc("direction")
        offsetLoc = getAttributeLoc("offset")
        directionLoc = getAttributeLoc("direction")
        offsetLoc = getAttributeLoc("offset")
        loadedAttributes = true
    }

    fun loadUniforms() {
        if (loadedUniforms) return
        transformMatLoc = getUniformLoc("transform")

        if (transformMatLoc == -1) transformMatLoc = getUniformLoc("transformMatrix")
        modelviewMatLoc = getUniformLoc("modelview")

        if (modelviewMatLoc == -1) modelviewMatLoc = getUniformLoc("modelviewMatrix")
        projectionMatLoc = getUniformLoc("projection")

        if (projectionMatLoc == -1) projectionMatLoc = getUniformLoc("projectionMatrix")
        viewportLoc = getUniformLoc("viewport")

        resolutionLoc = getUniformLoc("resolution")
        ppixelsLoc = getUniformLoc("ppixels")
        normalMatLoc = getUniformLoc("normalMatrix")
        lightCountLoc = getUniformLoc("lightCount")
        lightPositionLoc = getUniformLoc("lightPosition")
        lightNormalLoc = getUniformLoc("lightNormal")
        lightAmbientLoc = getUniformLoc("lightAmbient")
        lightDiffuseLoc = getUniformLoc("lightDiffuse")
        lightSpecularLoc = getUniformLoc("lightSpecular")
        lightFalloffLoc = getUniformLoc("lightFalloff")
        lightSpotLoc = getUniformLoc("lightSpot")
        textureLoc = getUniformLoc("texture")

        if (textureLoc == -1) {
            textureLoc = getUniformLoc("texMap")
        }

        texMatrixLoc = getUniformLoc("texMatrix")
        texOffsetLoc = getUniformLoc("texOffset")
        perspectiveLoc = getUniformLoc("perspective")
        scaleLoc = getUniformLoc("scale")
        loadedUniforms = true
    }

    protected fun setCommonUniforms() {
        if (-1 < transformMatLoc) {
            currentPG!!.updateGLProjmodelview()
            setUniformMatrix(transformMatLoc, currentPG!!.glProjmodelview)
        }
        if (-1 < modelviewMatLoc) {
            currentPG!!.updateGLModelview()
            setUniformMatrix(modelviewMatLoc, currentPG!!.glModelview)
        }
        if (-1 < projectionMatLoc) {
            currentPG!!.updateGLProjection()
            setUniformMatrix(projectionMatLoc, currentPG!!.glProjection)
        }
        if (-1 < viewportLoc) {
            val x = currentPG!!.viewport[0].toFloat()
            val y = currentPG!!.viewport[1].toFloat()
            val w = currentPG!!.viewport[2].toFloat()
            val h = currentPG!!.viewport[3].toFloat()
            setUniformValue(viewportLoc, x, y, w, h)
        }
        if (-1 < resolutionLoc) {
            val w = currentPG!!.viewport[2].toFloat()
            val h = currentPG!!.viewport[3].toFloat()
            setUniformValue(resolutionLoc, w, h)
        }
        if (-1 < ppixelsLoc) {
            ppixelsUnit = lastTexUnit + 1
            setUniformValue(ppixelsLoc, ppixelsUnit)
            pgl!!.activeTexture(PGL.TEXTURE0 + ppixelsUnit)
            currentPG!!.bindFrontTexture()
        } else {
            ppixelsUnit = -1
        }
    }

    protected fun bindTyped() {
        if (currentPG == null) {
            setRenderer(primaryPG!!.getCurrentPG())
            loadAttributes()
            loadUniforms()
        }

        setCommonUniforms()

        if (-1 < vertexLoc) pgl!!.enableVertexAttribArray(vertexLoc)
        if (-1 < colorLoc) pgl!!.enableVertexAttribArray(colorLoc)
        if (-1 < texCoordLoc) pgl!!.enableVertexAttribArray(texCoordLoc)
        if (-1 < normalLoc) pgl!!.enableVertexAttribArray(normalLoc)
        if (-1 < normalMatLoc) {
            currentPG!!.updateGLNormal()
            setUniformMatrix(normalMatLoc, currentPG!!.glNormal)
        }

        if (-1 < ambientLoc) pgl!!.enableVertexAttribArray(ambientLoc)
        if (-1 < specularLoc) pgl!!.enableVertexAttribArray(specularLoc)
        if (-1 < emissiveLoc) pgl!!.enableVertexAttribArray(emissiveLoc)
        if (-1 < shininessLoc) pgl!!.enableVertexAttribArray(shininessLoc)

        val count = currentPG!!.lightCount

        setUniformValue(lightCountLoc, count)

        if (0 < count) {
            setUniformVector(lightPositionLoc, currentPG!!.lightPosition, 4, count)
            setUniformVector(lightNormalLoc, currentPG!!.lightNormal, 3, count)
            setUniformVector(lightAmbientLoc, currentPG!!.lightAmbient, 3, count)
            setUniformVector(lightDiffuseLoc, currentPG!!.lightDiffuse, 3, count)
            setUniformVector(lightSpecularLoc, currentPG!!.lightSpecular, 3, count)
            setUniformVector(lightFalloffLoc, currentPG!!.lightFalloffCoefficients,
                    3, count)
            setUniformVector(lightSpotLoc, currentPG!!.lightSpotParameters, 2, count)
        }

        if (-1 < directionLoc) pgl!!.enableVertexAttribArray(directionLoc)

        if (-1 < offsetLoc) pgl!!.enableVertexAttribArray(offsetLoc)

        if (-1 < perspectiveLoc) {
            if (currentPG!!.getHint(PConstants.ENABLE_STROKE_PERSPECTIVE) &&
                    currentPG!!.nonOrthoProjection()) {
                setUniformValue(perspectiveLoc, 1)
            } else {
                setUniformValue(perspectiveLoc, 0)
            }
        }

        if (-1 < scaleLoc) {
            if (currentPG!!.getHint(PConstants.DISABLE_OPTIMIZED_STROKE)) {
                setUniformValue(scaleLoc, 1.0f, 1.0f, 1.0f)
            } else {
                val f = PGL.STROKE_DISPLACEMENT
                if (currentPG!!.orthoProjection()) {
                    setUniformValue(scaleLoc, 1f, 1f, f)
                } else {
                    setUniformValue(scaleLoc, f, f, f)
                }
            }
        }
    }

    protected fun unbindTyped() {
        if (-1 < offsetLoc) pgl!!.disableVertexAttribArray(offsetLoc)


        if (-1 < directionLoc) pgl!!.disableVertexAttribArray(directionLoc)

        if (-1 < textureLoc && texture != null) {
            pgl!!.activeTexture(PGL.TEXTURE0 + texUnit)
            texture!!.unbind()
            pgl!!.activeTexture(PGL.TEXTURE0)
            texture = null
        }

        if (-1 < ambientLoc) pgl!!.disableVertexAttribArray(ambientLoc)

        if (-1 < specularLoc) pgl!!.disableVertexAttribArray(specularLoc)

        if (-1 < emissiveLoc) pgl!!.disableVertexAttribArray(emissiveLoc)

        if (-1 < shininessLoc) pgl!!.disableVertexAttribArray(shininessLoc)

        if (-1 < vertexLoc) pgl!!.disableVertexAttribArray(vertexLoc)

        if (-1 < colorLoc) pgl!!.disableVertexAttribArray(colorLoc)

        if (-1 < texCoordLoc) pgl!!.disableVertexAttribArray(texCoordLoc)

        if (-1 < normalLoc) pgl!!.disableVertexAttribArray(normalLoc)

        if (-1 < ppixelsLoc) {
            pgl!!.enableFBOLayer()
            pgl!!.activeTexture(PGL.TEXTURE0 + ppixelsUnit)
            currentPG!!.unbindFrontTexture()
            pgl!!.activeTexture(PGL.TEXTURE0)
        }
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    fun setTexture(tex: Texture?) {
        texture = tex
        var scaleu = 1f
        var scalev = 1f
        var dispu = 0f
        var dispv = 0f

        if (tex != null) {

            if (tex.invertedX()) {
                scaleu = -1f
                dispu = 1f
            }

            if (tex.invertedY()) {
                scalev = -1f
                dispv = 1f
            }

            scaleu *= tex.maxTexcoordU()
            dispu *= tex.maxTexcoordU()
            scalev *= tex.maxTexcoordV()
            dispv *= tex.maxTexcoordV()

            setUniformValue(texOffsetLoc, 1.0f / tex.width, 1.0f / tex.height)

            if (-1 < textureLoc) {
                texUnit = if (-1 < ppixelsUnit) ppixelsUnit + 1 else lastTexUnit + 1
                setUniformValue(textureLoc, texUnit)
                pgl!!.activeTexture(PGL.TEXTURE0 + texUnit)
                tex.bind()
            }
        }

        if (-1 < texMatrixLoc) {
            if (tcmat == null) {
                tcmat = FloatArray(16)
            }

            tcmat!![0] = scaleu
            tcmat!![4] = 0F
            tcmat!![8] = 0F
            tcmat!![12] = dispu
            tcmat!![1] = 0F
            tcmat!![5] = scalev
            tcmat!![9] = 0F
            tcmat!![13] = dispv
            tcmat!![2] = 0F
            tcmat!![6] = 0F
            tcmat!![10] = 0F
            tcmat!![14] = 0F
            tcmat!![3] = 0F
            tcmat!![7] = 0F
            tcmat!![11] = 0F
            tcmat!![15] = 0F

            setUniformMatrix(texMatrixLoc, tcmat!!)
        }
    }

    protected fun supportsTexturing(): Boolean {
        return -1 < textureLoc
    }

    protected fun supportLighting(): Boolean {
        return -1 < lightCountLoc || -1 < lightPositionLoc || -1 < lightNormalLoc
    }

    fun accessTexCoords(): Boolean {
        return -1 < texCoordLoc
    }

    fun accessNormals(): Boolean {
        return -1 < normalLoc
    }

    fun accessLightAttribs(): Boolean {
        return -1 < ambientLoc || -1 < specularLoc || -1 < emissiveLoc || -1 < shininessLoc
    }

    fun setVertexAttribute(vboId: Int, size: Int, type: Int,
                           stride: Int, offset: Int) {
        setAttributeVBO(vertexLoc, vboId, size, type, false, stride, offset)
    }

    fun setColorAttribute(vboId: Int, size: Int, type: Int,
                          stride: Int, offset: Int) {
        setAttributeVBO(colorLoc, vboId, size, type, true, stride, offset)
    }

    fun setNormalAttribute(vboId: Int, size: Int, type: Int,
                           stride: Int, offset: Int) {
        setAttributeVBO(normalLoc, vboId, size, type, false, stride, offset)
    }

    fun setTexcoordAttribute(vboId: Int, size: Int, type: Int,
                             stride: Int, offset: Int) {
        setAttributeVBO(texCoordLoc, vboId, size, type, false, stride, offset)
    }

    fun setAmbientAttribute(vboId: Int, size: Int, type: Int,
                            stride: Int, offset: Int) {
        setAttributeVBO(ambientLoc, vboId, size, type, true, stride, offset)
    }

    fun setSpecularAttribute(vboId: Int, size: Int, type: Int,
                             stride: Int, offset: Int) {
        setAttributeVBO(specularLoc, vboId, size, type, true, stride, offset)
    }

    fun setEmissiveAttribute(vboId: Int, size: Int, type: Int,
                             stride: Int, offset: Int) {
        setAttributeVBO(emissiveLoc, vboId, size, type, true, stride, offset)
    }

    fun setShininessAttribute(vboId: Int, size: Int, type: Int,
                              stride: Int, offset: Int) {
        setAttributeVBO(shininessLoc, vboId, size, type, false, stride, offset)
    }

    fun setLineAttribute(vboId: Int, size: Int, type: Int,
                         stride: Int, offset: Int) {
        setAttributeVBO(directionLoc, vboId, size, type, false, stride, offset)
    }

    fun setPointAttribute(vboId: Int, size: Int, type: Int,
                          stride: Int, offset: Int) {
        setAttributeVBO(offsetLoc, vboId, size, type, false, stride, offset)
    }

    // ***************************************************************************

    // Class to store a user-specified value for a uniform parameter in the shader

    protected class UniformValue internal constructor(@JvmField var type: Int, @JvmField var value: Any) {

        companion object {
            const val INT1 = 0
            const val INT2 = 1
            const val INT3 = 2
            const val INT4 = 3
            const val FLOAT1 = 4
            const val FLOAT2 = 5
            const val FLOAT3 = 6
            const val FLOAT4 = 7
            const val INT1VEC = 8
            const val INT2VEC = 9
            const val INT3VEC = 10
            const val INT4VEC = 11
            const val FLOAT1VEC = 12
            const val FLOAT2VEC = 13
            const val FLOAT3VEC = 14
            const val FLOAT4VEC = 15
            const val MAT2 = 16
            const val MAT3 = 17
            const val MAT4 = 18
            const val SAMPLER2D = 19
        }

    }

    // ***************************************************************************

    // Processing specific

    // The shader type: POINT, LINE, POLY, etc.

    companion object {
        const val POINT = 0
        const val LINE = 1
        const val POLY = 2
        const val COLOR = 3
        const val LIGHT = 4
        const val TEXTURE = 5
        const val TEXLIGHT = 6

        protected var pointShaderAttrRegexp = "attribute *vec2 *offset"
        protected var pointShaderInRegexp = "in *vec2 *offset;"
        protected var lineShaderAttrRegexp = "attribute *vec4 *direction"
        protected var lineShaderInRegexp = "in *vec4 *direction"
        protected var pointShaderDefRegexp = "#define *PROCESSING_POINT_SHADER"
        protected var lineShaderDefRegexp = "#define *PROCESSING_LINE_SHADER"
        protected var colorShaderDefRegexp = "#define *PROCESSING_COLOR_SHADER"
        protected var lightShaderDefRegexp = "#define *PROCESSING_LIGHT_SHADER"
        protected var texShaderDefRegexp = "#define *PROCESSING_TEXTURE_SHADER"
        protected var texlightShaderDefRegexp = "#define *PROCESSING_TEXLIGHT_SHADER"
        protected var polyShaderDefRegexp = "#define *PROCESSING_POLYGON_SHADER"
        protected var triShaderAttrRegexp = "#define *PROCESSING_TRIANGLES_SHADER"
        protected var quadShaderAttrRegexp = "#define *PROCESSING_QUADS_SHADER"

        @JvmStatic
        fun getShaderType(source: Array<String>?, defaultType: Int): Int {
            for (i in source!!.indices) {
                val line = source[i].trim { it <= ' ' }
                if (PApplet.match(line, colorShaderDefRegexp) != null) return COLOR else if (PApplet.match(line, lightShaderDefRegexp) != null) return LIGHT else if (PApplet.match(line, texShaderDefRegexp) != null) return TEXTURE else if (PApplet.match(line, texlightShaderDefRegexp) != null) return TEXLIGHT else if (PApplet.match(line, polyShaderDefRegexp) != null) return POLY else if (PApplet.match(line, triShaderAttrRegexp) != null) return POLY else if (PApplet.match(line, quadShaderAttrRegexp) != null) return POLY else if (PApplet.match(line, pointShaderDefRegexp) != null) return POINT else if (PApplet.match(line, lineShaderDefRegexp) != null) return LINE else if (PApplet.match(line, pointShaderAttrRegexp) != null) return POINT else if (PApplet.match(line, pointShaderInRegexp) != null) return POINT else if (PApplet.match(line, lineShaderAttrRegexp) != null) return LINE else if (PApplet.match(line, lineShaderInRegexp) != null) return LINE
            }
            return defaultType
        }
    }
}