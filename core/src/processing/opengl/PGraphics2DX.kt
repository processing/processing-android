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

package processing.opengl

import processing.core.*
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Super fast OpenGL 2D renderer originally contributed by Miles Fogle:
 * https://github.com/hazmatsuitor
 *
 * It speeds-up rendering of 2D geometry by essentially two key optimizations: packing all the
 * vertex data in a single VBO, and using a custom stroke tessellator (see StrokeRenderer class
 * at the end). There are a number of other, less critical optimizations, for example using a single
 * shader for textured and non-textured geometry and a depth algorithm that allows stacking a large
 * number of 2D shapes without z-fighting (so occlusion is based on drawing order).
 *
 * Some notes from Miles:
 *
 * for testing purposes, I found it easier to create a separate class and avoid
 * touching existing code for now, rather than directly editing PGraphics2D/PGraphicsOpenGL
 * if this code becomes the new P2D implementation, then it will be properly migrated/integrated
 *
 * NOTE: this implementation doesn't use some of Processing's OpenGL wrappers
 * (e.g. PShader, Texture) because I found it more convenient to handle them manually
 * it could probably be made to use those classes with a bit of elbow grease and a spot of guidance
 * but it may not be worth it - I doubt it would reduce complexity much, if at all
 * (if there are reasons we need to use those classes, let me know)
 *
 */
//TODO: track debug performance stats
open class PGraphics2DX : PGraphicsOpenGL() {
    // Uses the implementations in the parent PGraphicsOpenGL class, which is needed to to draw obj files
    // and apply shader filters.
    protected var useParentImpl = false
    protected var initialized = false
    private var tess: PGL.Tessellator? = null
    protected var twoShader: PShader? = null
    protected var defTwoShader: PShader? = null
    protected var positionLoc = 0
    protected var colorLoc = 0
    protected var texCoordLoc = 0
    protected var texFactorLoc = 0
    protected var transformLoc = 0
    protected var texScaleLoc = 0

    //////////////////////////////////////////////////////////////

    // RENDERER SUPPORT QUERIES

    override fun is2D(): Boolean {
        return true
    }

    override fun is3D(): Boolean {
        return false
    }

    //////////////////////////////////////////////////////////////

    // RENDERING

    override fun beginDraw() {
        super.beginDraw()
        if (!useParentImpl) {
            pgl.depthFunc(PGL.LESS)
            depth = 1.0f
        }
    }

    override fun flush() {
        // If no vertices where created with the base implementation, then flush() will do nothing.
        super.flush()
        flushBuffer()
    }

    // These two methods are meant for debugging (comparing the new and old P2D renderers)
    // and will go away.
    fun useOldP2D() {
        useParentImpl = true
        pgl.depthFunc(PGL.LEQUAL)
    }

    fun useNewP2D() {
        useParentImpl = false
        pgl.depthFunc(PGL.LESS)
    }

    //////////////////////////////////////////////////////////////

    // HINTS

    override fun hint(which: Int) {
        if (which == PConstants.ENABLE_STROKE_PERSPECTIVE) {
            PGraphics.showWarning(STROKE_PERSPECTIVE_ERROR)
            return
        }
        super.hint(which)
    }

    //////////////////////////////////////////////////////////////

    // PROJECTION

    override fun ortho() {
        PGraphics.showMethodWarning("ortho")
    }

    override fun ortho(left: Float, right: Float,
                       bottom: Float, top: Float) {
        PGraphics.showMethodWarning("ortho")
    }

    override fun ortho(left: Float, right: Float,
                       bottom: Float, top: Float,
                       near: Float, far: Float) {
        PGraphics.showMethodWarning("ortho")
    }

    override fun perspective() {
        PGraphics.showMethodWarning("perspective")
    }

    override fun perspective(fov: Float, aspect: Float, zNear: Float, zFar: Float) {
        PGraphics.showMethodWarning("perspective")
    }

    override fun frustum(left: Float, right: Float, bottom: Float, top: Float,
                         znear: Float, zfar: Float) {
        PGraphics.showMethodWarning("frustum")
    }

    override fun defaultPerspective() {
        super.ortho(0f, width.toFloat(), -height.toFloat(), 0f, -1f, +1f)
    }

    //////////////////////////////////////////////////////////////

    // CAMERA

    override fun beginCamera() {
        PGraphics.showMethodWarning("beginCamera")
    }

    override fun endCamera() {
        PGraphics.showMethodWarning("endCamera")
    }

    override fun camera() {
        PGraphics.showMethodWarning("camera")
    }

    override fun camera(eyeX: Float, eyeY: Float, eyeZ: Float,
                        centerX: Float, centerY: Float, centerZ: Float,
                        upX: Float, upY: Float, upZ: Float) {
        PGraphics.showMethodWarning("camera")
    }

    override fun defaultCamera() {
        eyeDist = 1f
        resetMatrix()
    }

    //////////////////////////////////////////////////////////////

    // SHAPE

    override fun shape(shape: PShape) {
        if (shape.is2D) {
            if (!useParentImpl) {
                useOldP2D()
                super.shape(shape)
                useNewP2D()
            } else {
                super.shape(shape)
            }
        } else {
            PGraphics.showWarning(NON_2D_SHAPE_ERROR)
        }
    }

    override fun shape(shape: PShape, x: Float, y: Float) {
        if (shape.is2D) {
            if (!useParentImpl) {
                useOldP2D()
                super.shape(shape, x, y)
                useNewP2D()
            } else {
                super.shape(shape, x, y)
            }
        } else {
            PGraphics.showWarning(NON_2D_SHAPE_ERROR)
        }
    }

    override fun shape(shape: PShape, a: Float, b: Float, c: Float, d: Float) {
        if (shape.is2D) {
            if (!useParentImpl) {
                useOldP2D()
                super.shape(shape, a, b, c, d)
                useNewP2D()
            } else {
                super.shape(shape, a, b, c, d)
            }
        } else {
            PGraphics.showWarning(NON_2D_SHAPE_ERROR)
        }
    }

    public override fun shape(shape: PShape, x: Float, y: Float, z: Float) {
        PGraphics.showDepthWarningXYZ("shape")
    }

    public override fun shape(shape: PShape, x: Float, y: Float, z: Float,
                              c: Float, d: Float, e: Float) {
        PGraphics.showDepthWarningXYZ("shape")
    }

    //////////////////////////////////////////////////////////////

    // SCREEN TRANSFORMS

    override fun modelX(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarning("modelX")
        return 0F
    }

    override fun modelY(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarning("modelY")
        return 0F
    }

    override fun modelZ(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarning("modelZ")
        return 0F
    }

    //////////////////////////////////////////////////////////////
    // VERTEX SHAPES
    override fun texture(image: PImage) {
        super.texture(image)
        if (image == null) {
            return
        }
        val t = currentPG.getTexture(image)
        texWidth = t.width
        texHeight = t.height
        imageTex = t.glName
        textureImpl(imageTex)
    }

    override fun beginShape(kind: Int) {
        if (useParentImpl) {
            super.beginShape(kind)
            return
        }
        shapeType = kind
        vertCount = 0
        contourCount = 0
    }

    override fun endShape(mode: Int) {
        if (useParentImpl) {
            super.endShape(mode)
            return
        }

        //end the current contour
        appendContour(vertCount)
        if (fill) {
            incrementDepth()
            if (shapeType == PConstants.POLYGON) {
                if (knownConvexPolygon) {
                    for (i in 2 until vertCount) {
                        check(3)
                        vertexImpl(shapeVerts[0])
                        vertexImpl(shapeVerts[i - 1])
                        vertexImpl(shapeVerts[i])
                    }
                    knownConvexPolygon = false
                } else {
                    tess!!.beginPolygon(this)
                    tess!!.beginContour()
                    var c = 0
                    for (i in 0 until vertCount) {
                        if (contours[c] == i) {
                            tess!!.endContour()
                            tess!!.beginContour()
                            c++ //lol no, this is java
                        }
                        tempDoubles[0] = shapeVerts[i]!!.x.toDouble()
                        tempDoubles[1] = shapeVerts[i]!!.y.toDouble()
                        tess!!.addVertex(tempDoubles, 0, shapeVerts[i])
                    }
                    tess!!.endContour()
                    tess!!.endPolygon()
                }
            } else if (shapeType == PConstants.QUAD_STRIP) {
                var i = 0
                while (i <= vertCount - 4) {
                    check(6)
                    vertexImpl(shapeVerts[i + 0])
                    vertexImpl(shapeVerts[i + 1])
                    vertexImpl(shapeVerts[i + 2])
                    vertexImpl(shapeVerts[i + 1])
                    vertexImpl(shapeVerts[i + 2])
                    vertexImpl(shapeVerts[i + 3])
                    i += 2
                }
            } else if (shapeType == PConstants.QUADS) {
                var i = 0
                while (i <= vertCount - 4) {
                    check(6)
                    vertexImpl(shapeVerts[i + 0])
                    vertexImpl(shapeVerts[i + 1])
                    vertexImpl(shapeVerts[i + 2])
                    vertexImpl(shapeVerts[i + 0])
                    vertexImpl(shapeVerts[i + 2])
                    vertexImpl(shapeVerts[i + 3])
                    i += 4
                }
            } else if (shapeType == PConstants.TRIANGLE_STRIP) {
                var i = 0
                while (i <= vertCount - 3) {
                    check(3)
                    vertexImpl(shapeVerts[i + 0])
                    vertexImpl(shapeVerts[i + 1])
                    vertexImpl(shapeVerts[i + 2])
                    i += 1
                }
            } else if (shapeType == PConstants.TRIANGLE_FAN) {
                var i = 0
                while (i <= vertCount - 3) {
                    check(3)
                    vertexImpl(shapeVerts[0 + 0])
                    vertexImpl(shapeVerts[i + 1])
                    vertexImpl(shapeVerts[i + 2])
                    i += 1
                }

                //close the fan
                if (vertCount >= 3) {
                    check(3)
                    vertexImpl(shapeVerts[0])
                    vertexImpl(shapeVerts[vertCount - 1])
                    vertexImpl(shapeVerts[1])
                }
            } else if (shapeType == PConstants.TRIANGLES) {
                var i = 0
                while (i <= vertCount - 3) {
                    check(3)
                    vertexImpl(shapeVerts[i + 0])
                    vertexImpl(shapeVerts[i + 1])
                    vertexImpl(shapeVerts[i + 2])
                    i += 3
                }
            }
        }
        if (stroke) {
            incrementDepth()
            if (shapeType == PConstants.POLYGON) {
                if (vertCount < 3) {
                    return
                }
                var c = 0
                sr.beginLine()
                for (i in 0 until vertCount) {
                    if (contours[c] == i) {
                        sr.endLine(mode == PConstants.CLOSE)
                        sr.beginLine()
                        c++
                    }
                    sr.lineVertex(shapeVerts[i]!!.x, shapeVerts[i]!!.y)
                }
                sr.endLine(mode == PConstants.CLOSE)
            } else if (shapeType == PConstants.QUAD_STRIP) {
                var i = 0
                while (i <= vertCount - 4) {
                    sr.beginLine()
                    sr.lineVertex(shapeVerts[i + 0]!!.x, shapeVerts[i + 0]!!.y)
                    sr.lineVertex(shapeVerts[i + 1]!!.x, shapeVerts[i + 1]!!.y)
                    sr.lineVertex(shapeVerts[i + 3]!!.x, shapeVerts[i + 3]!!.y)
                    sr.lineVertex(shapeVerts[i + 2]!!.x, shapeVerts[i + 2]!!.y)
                    sr.endLine(true)
                    i += 2
                }
            } else if (shapeType == PConstants.QUADS) {
                var i = 0
                while (i <= vertCount - 4) {
                    sr.beginLine()
                    sr.lineVertex(shapeVerts[i + 0]!!.x, shapeVerts[i + 0]!!.y)
                    sr.lineVertex(shapeVerts[i + 1]!!.x, shapeVerts[i + 1]!!.y)
                    sr.lineVertex(shapeVerts[i + 2]!!.x, shapeVerts[i + 2]!!.y)
                    sr.lineVertex(shapeVerts[i + 3]!!.x, shapeVerts[i + 3]!!.y)
                    sr.endLine(true)
                    i += 4
                }
            } else if (shapeType == PConstants.TRIANGLE_STRIP) {
                var i = 0
                while (i <= vertCount - 3) {
                    sr.beginLine()
                    sr.lineVertex(shapeVerts[i + 0]!!.x, shapeVerts[i + 0]!!.y)
                    sr.lineVertex(shapeVerts[i + 1]!!.x, shapeVerts[i + 1]!!.y)
                    sr.lineVertex(shapeVerts[i + 2]!!.x, shapeVerts[i + 2]!!.y)
                    sr.endLine(true)
                    i += 1
                }
            } else if (shapeType == PConstants.TRIANGLE_FAN) {
                var i = 0
                while (i <= vertCount - 3) {
                    sr.beginLine()
                    sr.lineVertex(shapeVerts[0 + 0]!!.x, shapeVerts[0 + 0]!!.y)
                    sr.lineVertex(shapeVerts[i + 1]!!.x, shapeVerts[i + 1]!!.y)
                    sr.lineVertex(shapeVerts[i + 2]!!.x, shapeVerts[i + 2]!!.y)
                    sr.endLine(true)
                    i += 1
                }

                //close the fan
                if (vertCount >= 3) {
                    sr.beginLine()
                    sr.lineVertex(shapeVerts[0]!!.x, shapeVerts[0]!!.y)
                    sr.lineVertex(shapeVerts[vertCount - 1]!!.x, shapeVerts[vertCount - 1]!!.y)
                    sr.lineVertex(shapeVerts[1]!!.x, shapeVerts[1]!!.y)
                    sr.endLine(true)
                }
            } else if (shapeType == PConstants.TRIANGLES) {
                var i = 0
                while (i <= vertCount - 3) {
                    sr.beginLine()
                    sr.lineVertex(shapeVerts[i + 0]!!.x, shapeVerts[i + 0]!!.y)
                    sr.lineVertex(shapeVerts[i + 1]!!.x, shapeVerts[i + 1]!!.y)
                    sr.lineVertex(shapeVerts[i + 2]!!.x, shapeVerts[i + 2]!!.y)
                    sr.endLine(true)
                    i += 3
                }
            } else if (shapeType == PConstants.LINES) {
                var i = 0
                while (i <= vertCount - 2) {
                    val s1 = shapeVerts[i + 0]
                    val s2 = shapeVerts[i + 1]
                    singleLine(s1!!.x, s1.y, s2!!.x, s2.y, strokeColor)
                    i += 2
                }
            } else if (shapeType == PConstants.POINTS) {
                var i = 0
                while (i <= vertCount - 1) {
                    singlePoint(shapeVerts[i]!!.x, shapeVerts[i]!!.y, strokeColor)
                    i += 1
                }
            }
        }
    }

    override fun beginContour() {
        super.beginContour()
        if (useParentImpl) {
            return
        }

        //XXX: not sure what the exact behavior should be for invalid calls to begin/endContour()
        //but this should work for valid cases for now
        appendContour(vertCount)
    }

    override fun vertex(x: Float, y: Float) {
        if (useParentImpl) {
            super.vertex(x, y)
            return
        }
        curveVerts = 0
        shapeVertex(x, y, 0f, 0f, fillColor, 0f)
    }

    override fun vertex(x: Float, y: Float, u: Float, v: Float) {
        if (useParentImpl) {
            super.vertex(x, y, u, v)
            return
        }
        curveVerts = 0
        textureImpl(imageTex)
        shapeVertex(x, y, u, v, if (tint) tintColor else -0x1, 1f)
    }

    override fun vertex(x: Float, y: Float, z: Float) {
        PGraphics.showDepthWarningXYZ("vertex")
    }

    override fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        PGraphics.showDepthWarningXYZ("vertex")
    }

    //////////////////////////////////////////////////////////////

    // BEZIER VERTICES

    //this method is almost wholesale copied from PGraphics.bezierVertex()
    //TODO: de-duplicate this code if there is a convenient way to do so
    override fun bezierVertex(x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float) {
        if (useParentImpl) {
            super.bezierVertex(x2, y2, x3, y3, x4, y4)
            return
        }
        bezierInitCheck()
        //    bezierVertexCheck(); //TODO: re-implement this (and other run-time sanity checks)
        val draw = bezierDrawMatrix

        //(these are the only lines that are different)
        var x1 = shapeVerts[vertCount - 1]!!.x
        var y1 = shapeVerts[vertCount - 1]!!.y
        var xplot1 = draw.m10 * x1 + draw.m11 * x2 + draw.m12 * x3 + draw.m13 * x4
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
            shapeVertex(x1, y1, 0f, 0f, fillColor, 0f)
        }
    }

    override fun bezierVertex(x2: Float, y2: Float, z2: Float,
                              x3: Float, y3: Float, z3: Float,
                              x4: Float, y4: Float, z4: Float) {
        PGraphics.showDepthWarningXYZ("bezierVertex")
    }

    //////////////////////////////////////////////////////////////
    // QUADRATIC BEZIER VERTICES
    //this method is almost wholesale copied from PGraphics.quadraticVertex()
    //TODO: de-duplicate this code if there is a convenient way to do so
    override fun quadraticVertex(cx: Float, cy: Float,
                                 x3: Float, y3: Float) {
        if (useParentImpl) {
            super.quadraticVertex(cx, cy, x3, y3)
            return
        }

        //(these are the only lines that are different)
        val x1 = shapeVerts[vertCount - 1]!!.x
        val y1 = shapeVerts[vertCount - 1]!!.y

        //TODO: optimize this?
        bezierVertex(x1 + (cx - x1) * 2 / 3.0f, y1 + (cy - y1) * 2 / 3.0f,
                x3 + (cx - x3) * 2 / 3.0f, y3 + (cy - y3) * 2 / 3.0f,
                x3, y3)
    }

    override fun quadraticVertex(x2: Float, y2: Float, z2: Float,
                                 x4: Float, y4: Float, z4: Float) {
        PGraphics.showDepthWarningXYZ("quadVertex")
    }

    //////////////////////////////////////////////////////////////
    // CURVE VERTICES
    //curve vertices
    private var cx1 = 0f
    private var cy1 = 0f
    private var cx2 = 0f
    private var cy2 = 0f
    private var cx3 = 0f
    private var cy3 = 0f
    private var cx4 = 0f
    private var cy4 = 0f
    private var curveVerts = 0
    override fun curveVertex(x: Float, y: Float) {
        if (useParentImpl) {
            super.curveVertex(x, y)
            return
        }

//    curveVertexCheck(); //TODO: re-implement this (and other runtime checks)
        curveInitCheck()
        cx1 = cx2
        cx2 = cx3
        cx3 = cx4
        cy1 = cy2
        cy2 = cy3
        cy3 = cy4
        cx4 = x
        cy4 = y
        curveVerts += 1
        if (curveVerts > 3) {
            PApplet.println("drawing curve...")
            val draw = curveDrawMatrix
            var xplot1 = draw.m10 * cx1 + draw.m11 * cx2 + draw.m12 * cx3 + draw.m13 * cx4
            var xplot2 = draw.m20 * cx1 + draw.m21 * cx2 + draw.m22 * cx3 + draw.m23 * cx4
            val xplot3 = draw.m30 * cx1 + draw.m31 * cx2 + draw.m32 * cx3 + draw.m33 * cx4
            var yplot1 = draw.m10 * cy1 + draw.m11 * cy2 + draw.m12 * cy3 + draw.m13 * cy4
            var yplot2 = draw.m20 * cy1 + draw.m21 * cy2 + draw.m22 * cy3 + draw.m23 * cy4
            val yplot3 = draw.m30 * cy1 + draw.m31 * cy2 + draw.m32 * cy3 + draw.m33 * cy4
            var x0 = cx2
            var y0 = cy2
            if (curveVerts == 4) {
                shapeVertex(x0, y0, 0f, 0f, fillColor, 0f)
            }
            for (j in 0 until curveDetail) {
                x0 += xplot1
                xplot1 += xplot2
                xplot2 += xplot3
                y0 += yplot1
                yplot1 += yplot2
                yplot2 += yplot3
                shapeVertex(x0, y0, 0f, 0f, fillColor, 0f)
            }
        }
    }

    override fun curveVertex(x: Float, y: Float, z: Float) {
        PGraphics.showDepthWarningXYZ("curveVertex")
    }

    //////////////////////////////////////////////////////////////

    // PRIMITIVES

    /*
   * Re-implementations of the various shape drawing methods.
   *
   * Ideally we could just call the versions in PGraphics,
   * since most of those will work correctly without modification,
   * but there's no good way to do that in Java,
   * so as long as we're inheriting from PGraphicsOpenGL,
   * we need to re-implement them.
   */
    override fun quad(x1: Float, y1: Float, x2: Float, y2: Float,
                      x3: Float, y3: Float, x4: Float, y4: Float) {
        if (useParentImpl) {
            super.quad(x1, y1, x2, y2, x3, y3, x4, y4)
            return
        }
        beginShape(PConstants.QUADS)
        vertex(x1, y1)
        vertex(x2, y2)
        vertex(x3, y3)
        vertex(x4, y4)
        endShape()
    }

    override fun triangle(x1: Float, y1: Float, x2: Float, y2: Float,
                          x3: Float, y3: Float) {
        if (useParentImpl) {
            super.triangle(x1, y1, x2, y2, x3, y3)
            return
        }
        beginShape(PConstants.TRIANGLES)
        vertex(x1, y1)
        vertex(x2, y2)
        vertex(x3, y3)
        endShape()
    }

    override fun ellipseImpl(a: Float, b: Float, c: Float, d: Float) {
        if (useParentImpl) {
            super.ellipseImpl(a, b, c, d)
            return
        }
        beginShape(PConstants.POLYGON)

        //convert corner/diameter to center/radius
        val rx = c * 0.5f
        val ry = d * 0.5f
        val x = a + rx
        val y = b + ry

        //since very wide stroke and/or very small radius might cause the
        //stroke to account for a significant portion of the overall radius,
        //we take it into account when calculating detail, just to be safe
        val segments = circleDetail(PApplet.max(rx, ry) + if (stroke) strokeWeight else 0F, PConstants.TWO_PI)
        val step = PConstants.TWO_PI / segments
        val cos = PApplet.cos(step)
        val sin = PApplet.sin(step)
        var dx = 0f
        var dy = 1f
        for (i in 0 until segments) {
            shapeVertex(x + dx * rx, y + dy * ry, 0f, 0f, fillColor, 0f)
            //this is the equivalent of multiplying the vector <dx, dy> by the 2x2 rotation matrix [[cos -sin] [sin cos]]
            val tempx = dx * cos - dy * sin
            dy = dx * sin + dy * cos
            dx = tempx
        }
        knownConvexPolygon = true
        endShape(PConstants.CLOSE)
    }

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        if (useParentImpl) {
            super.line(x1, y1, x2, y2)
            return
        }
        incrementDepth()
        singleLine(x1, y1, x2, y2, strokeColor)
    }

    override fun point(x: Float, y: Float) {
        if (useParentImpl) {
            super.point(x, y)
            return
        }
        incrementDepth()
        singlePoint(x, y, strokeColor)
    }

    override fun arcImpl(x: Float, y: Float, w: Float, h: Float, start: Float, stop: Float, mode: Int) {
        var x = x
        var y = y
        var w = w
        var h = h
        if (useParentImpl) {
            super.arcImpl(x, y, w, h, start, stop, mode)
            return
        }

        //INVARIANT: stop > start
        //INVARIANT: stop - start <= TWO_PI

        //convert corner/diameter to center/radius
        w *= 0.5f
        h *= 0.5f
        x += w
        y += h
        val diff = stop - start
        val segments = circleDetail(PApplet.max(w, h), diff)
        val step = diff / segments
        beginShape(PConstants.POLYGON)

        //no constant is defined for the default arc mode, so we just use a literal 0
        //(this is consistent with code elsewhere)
        if (mode == 0 || mode == PConstants.PIE) {
            vertex(x, y)
        }
        if (mode == 0) {
            //kinda hacky way to disable drawing a stroke along the first edge
            appendContour(vertCount)
        }
        var dx = PApplet.cos(start)
        var dy = PApplet.sin(start)
        val c = PApplet.cos(step)
        val s = PApplet.sin(step)
        for (i in 0..segments) {
            shapeVertex(x + dx * w, y + dy * h, 0f, 0f, fillColor, 0f)
            //this is the equivalent of multiplying the vector <dx, dy> by the 2x2 rotation matrix [[c -s] [s c]]
            val tempx = dx * c - dy * s
            dy = dx * s + dy * c
            dx = tempx
        }

        //for the case `(mode == PIE || mode == 0) && diff > HALF_PI`, the polygon
        //will not actually be convex, but due to known vertex order, we can still safely tessellate as if it is
        knownConvexPolygon = true
        if (mode == PConstants.CHORD || mode == PConstants.PIE) {
            endShape(PConstants.CLOSE)
        } else {
            endShape()
        }
    }

    override fun rectImpl(x1: Float, y1: Float, x2: Float, y2: Float,
                          tl: Float, tr: Float, br: Float, bl: Float) {
        if (useParentImpl) {
            super.rectImpl(x1, y1, x2, y2, tl, tr, br, bl)
            return
        }
        beginShape()
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
        knownConvexPolygon = true
        endShape(PConstants.CLOSE)
    }

    //////////////////////////////////////////////////////////////

    // BOX

    override fun box(w: Float, h: Float, d: Float) {
        PGraphics.showMethodWarning("box")
    }

    //////////////////////////////////////////////////////////////

    // SPHERE

    override fun sphere(r: Float) {
        PGraphics.showMethodWarning("sphere")
    }

    //////////////////////////////////////////////////////////////

    // PIXELS

    override fun loadPixels() {
        super.loadPixels()
        allocatePixels()
        readPixels()
    }

    override fun updatePixels() {
        super.updatePixels()
        image(this, 0f, 0f, width * 2.toFloat(), height * 2.toFloat(), 0, 0, pixelWidth, pixelHeight)
        flushBuffer()
    }

    //////////////////////////////////////////////////////////////

    // CLIPPING

    /*
  @Override
  public void clipImpl(float x1, float y1, float x2, float y2) {
    //XXX: exactly the same as the implementation in PGraphicsOpenGL,
    //but calls flushBuffer() instead of flush()
    flushBuffer();
    pgl.enable(PGL.SCISSOR_TEST);

    float h = y2 - y1;
    clipRect[0] = (int)x1;
    clipRect[1] = (int)(height - y1 - h);
    clipRect[2] = (int)(x2 - x1);
    clipRect[3] = (int)h;
    pgl.scissor(clipRect[0], clipRect[1], clipRect[2], clipRect[3]);

    clip = true;
  }

  @Override
  public void noClip() {
    //XXX: exactly the same as the implementation in PGraphicsOpenGL,
    //but calls flushBuffer() instead of flush()
    if (clip) {
      flushBuffer();
      pgl.disable(PGL.SCISSOR_TEST);
      clip = false;
    }
  }
*/
    //////////////////////////////////////////////////////////////

    // TEXT

    //NOTE: a possible improvement to text rendering performance is to batch all glyphs
    //from the same texture page together instead of rendering each char strictly in sequence.
    //it remains to be seen whether this would improve performance in practice
    //(I don't know how common it is for a font to occupy multiple texture pages)
    override fun textCharModelImpl(info: FontTexture.TextureInfo,
                                   x0: Float, y0: Float, x1: Float, y1: Float) {
        incrementDepth()
        check(6)
        textureImpl(textTex.textures!![info.texIndex]!!.glName)
        vertexImpl(x0, y0, info.u0, info.v0, fillColor, 1f)
        vertexImpl(x1, y0, info.u1, info.v0, fillColor, 1f)
        vertexImpl(x0, y1, info.u0, info.v1, fillColor, 1f)
        vertexImpl(x1, y0, info.u1, info.v0, fillColor, 1f)
        vertexImpl(x0, y1, info.u0, info.v1, fillColor, 1f)
        vertexImpl(x1, y1, info.u1, info.v1, fillColor, 1f)
    }

    //////////////////////////////////////////////////////////////

    // MATRIX MORE!

    override fun begin2D() {
        pushProjection()
        defaultPerspective()
        pushMatrix()
        defaultCamera()
    }

    override fun end2D() {
        popMatrix()
        popProjection()
    }

    //////////////////////////////////////////////////////////////

    // SHADER FILTER

    override fun filter(shader: PShader) {
        // TODO: not working... the loadShader() method uses the P2 vertex stage
        // The filter method needs to use the geometry-generation in the base class.
        // We could re-implement it here, but this is easier.
        if (!useParentImpl) {
            useOldP2D()
            super.filter(shader)
            useNewP2D()
        } else {
            super.filter(shader)
        }
    }

    //////////////////////////////////////////////////////////////

    // SHADER API

    override fun loadShader(fragFilename: String): PShader? {
        if (fragFilename == null || fragFilename == "") {
            PGraphics.showWarning(MISSING_FRAGMENT_SHADER)
            return null
        }
        val shader = PShader(parent)
        shader.setFragmentShader(fragFilename)
        val vertSource = pgl.loadVertexShader(defP2DShaderVertURL)
        shader.setVertexShader(vertSource)
        return shader
    }

    override fun shader(shader: PShader) {
        if (useParentImpl) {
            super.shader(shader)
            return
        }
        flushBuffer() // Flushing geometry drawn with a different shader.
        shader?.init()
        val res = checkShaderLocs(shader)
        if (res) {
            twoShader = shader
            shader.type = SHADER2D
        } else {
            PGraphics.showWarning(NON_2D_SHADER_ERROR)
        }
    }

    override fun shader(shader: PShader, kind: Int) {
        if (useParentImpl) {
            super.shader(shader, kind)
            return
        }
        PGraphics.showWarning(WRONG_SHADER_PARAMS)
    }

    override fun resetShader() {
        if (useParentImpl) {
            super.resetShader()
            return
        }
        flushBuffer()
        twoShader = null
    }

    override fun resetShader(kind: Int) {
        if (useParentImpl) {
            super.resetShader(kind)
            return
        }
        PGraphics.showWarning(WRONG_SHADER_PARAMS)
    }

    //////////////////////////////////////////////////////////////

    // MATRIX TRANSFORMATIONS

    override fun translate(tx: Float, ty: Float, tz: Float) {
        PGraphics.showDepthWarningXYZ("translate")
    }

    override fun rotateX(angle: Float) {
        PGraphics.showDepthWarning("rotateX")
    }

    override fun rotateY(angle: Float) {
        PGraphics.showDepthWarning("rotateY")
    }

    override fun rotateZ(angle: Float) {
        PGraphics.showDepthWarning("rotateZ")
    }

    override fun rotate(angle: Float, vx: Float, vy: Float, vz: Float) {
        PGraphics.showVariationWarning("rotate")
    }

    override fun applyMatrix(source: PMatrix3D) {
        PGraphics.showVariationWarning("applyMatrix")
    }

    override fun applyMatrix(n00: Float, n01: Float, n02: Float, n03: Float,
                             n10: Float, n11: Float, n12: Float, n13: Float,
                             n20: Float, n21: Float, n22: Float, n23: Float,
                             n30: Float, n31: Float, n32: Float, n33: Float) {
        PGraphics.showVariationWarning("applyMatrix")
    }

    override fun scale(sx: Float, sy: Float, sz: Float) {
        PGraphics.showDepthWarningXYZ("scale")
    }

    //////////////////////////////////////////////////////////////

    // SCREEN AND MODEL COORDS

    override fun screenX(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarningXYZ("screenX")
        return 0F
    }

    override fun screenY(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarningXYZ("screenY")
        return 0F
    }

    override fun screenZ(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarningXYZ("screenZ")
        return 0F
    }

    override fun getMatrix(target: PMatrix3D): PMatrix3D {
        PGraphics.showVariationWarning("getMatrix")
        return target
    }

    override fun setMatrix(source: PMatrix3D) {
        PGraphics.showVariationWarning("setMatrix")
    }

    //////////////////////////////////////////////////////////////

    // LIGHTS

    override fun lights() {
        PGraphics.showMethodWarning("lights")
    }

    override fun noLights() {
        PGraphics.showMethodWarning("noLights")
    }

    override fun ambientLight(red: Float, green: Float, blue: Float) {
        PGraphics.showMethodWarning("ambientLight")
    }

    override fun ambientLight(red: Float, green: Float, blue: Float,
                              x: Float, y: Float, z: Float) {
        PGraphics.showMethodWarning("ambientLight")
    }

    override fun directionalLight(red: Float, green: Float, blue: Float,
                                  nx: Float, ny: Float, nz: Float) {
        PGraphics.showMethodWarning("directionalLight")
    }

    override fun pointLight(red: Float, green: Float, blue: Float,
                            x: Float, y: Float, z: Float) {
        PGraphics.showMethodWarning("pointLight")
    }

    override fun spotLight(red: Float, green: Float, blue: Float,
                           x: Float, y: Float, z: Float,
                           nx: Float, ny: Float, nz: Float,
                           angle: Float, concentration: Float) {
        PGraphics.showMethodWarning("spotLight")
    }

    override fun lightFalloff(constant: Float, linear: Float, quadratic: Float) {
        PGraphics.showMethodWarning("lightFalloff")
    }

    override fun lightSpecular(v1: Float, v2: Float, v3: Float) {
        PGraphics.showMethodWarning("lightSpecular")
    }

    //////////////////////////////////////////////////////////////

    // PRIVATE IMPLEMENTATION

    //maxVerts can be tweaked for memory/performance trade-off
    //in my testing, performance seems to plateau after around 6000 (= 2000*3)
    //memory usage should be around ~165kb for 6000 verts
    private val maxVerts = 2000 * 3
    private val vertSize = 7 * java.lang.Float.BYTES //xyzuvcf
    private val vertexData = FloatArray(maxVerts * 7)
    private var usedVerts = 0
    private var depth = 1.0f
    private var imageTex = 0
    private var tex = 0
    private var vbo = 0
    private var texWidth = 0
    private var texHeight = 0

    // Determination of the smallest increments and largest-greater-than-minus-one
    // https://en.wikipedia.org/wiki/Half-precision_floating-point_format
    // Using the smallest positive normal number in half (16-bit) precision, which is how the depth
    // buffer is initialized in mobile
    private val smallestDepthIncrement = Math.pow(2.0, -14.0).toFloat()

    // As the limit for the depth increase, we take the minus the largest number less than one in
    // half (16-bit) precision
    private val largestNumberLessThanOne = 1 - Math.pow(2.0, -11.0).toFloat()
    private fun incrementDepth() {
        // By resetting the depth buffer when needed, we are able to have arbitrarily many
        // layers, unlimited by depth buffer precision. In practice, the precision of this
        // algorithm seems to be acceptable (exactly (1 + 1 - pow(2, -11))/pow(2, -14) = 32,760 layers)
        // for mobile.
        if (depth < -largestNumberLessThanOne) {
            flushBuffer()
            pgl.clear(PGL.DEPTH_BUFFER_BIT)
            // Depth test will fail at depth = 1.0 after clearing the depth buffer,
            // But since we always increment before drawing anything, this should be okay
            depth = 1.0f
        }
        depth -= smallestDepthIncrement
    }

    private fun initTess() {
        val callback: PGL.TessellatorCallback = object : PGL.TessellatorCallback {
            override fun begin(type: Int) {
                // TODO Auto-generated method stub
            }

            override fun end() {
                // TODO Auto-generated method stub
            }

            override fun vertex(data: Any) {
                if (usedVerts % 3 == 0) {
                    check(3)
                }
                val vert = data as TessVertex
                vertexImpl(vert.x, vert.y, vert.u, vert.v, vert.c, vert.f)
            }

            override fun combine(coords: DoubleArray, data: Array<Any>, weights: FloatArray, outData: Array<Any>) {
                //here we do some horrible things to blend the colors
                var r = 0f
                var g = 0f
                var b = 0f
                var a = 0f
                for (i in data.indices) {
                    val c = (data[i] as TessVertex).c
                    a += weights[i] * (c shr 24 and 0xFF)
                    r += weights[i] * (c shr 16 and 0xFF)
                    g += weights[i] * (c shr 8 and 0xFF)
                    b += weights[i] * (c and 0xFF)
                }
                val c = (a.toInt() shl 24) + (r.toInt() shl 16) + (g.toInt() shl 8) + b.toInt()
                var u = 0f
                var v = 0f
                var f = 0f
                for (i in data.indices) {
                    u += weights[i] * (data[i] as TessVertex).u
                    v += weights[i] * (data[i] as TessVertex).v
                    f += weights[i] * (data[i] as TessVertex).f
                }
                outData[0] = TessVertex(coords[0].toFloat(), coords[1].toFloat(), u, v, c, f)
            }

            override fun error(err: Int) {
                PApplet.println("glu error: $err")
            }
        }
        tess = pgl.createTessellator(callback)

        // We specify the edge flag callback as a no-op to force the tesselator to only pass us
        // triangle primitives (no triangle fans or triangle strips), for simplicity
        tess?.setCallback(PGL.TESS_EDGE_FLAG)
        tess?.setWindingRule(PGL.TESS_WINDING_NONZERO)
    }

    private fun initVerts() {
        for (i in shapeVerts.indices) {
            shapeVerts[i] = TessVertex()
        }
    }

    private fun flushBuffer() {
        if (usedVerts == 0) {
            return
        }
        if (vbo == 0) {
            // Generate vbo
            val vboBuff = IntBuffer.allocate(1)
            pgl.genBuffers(1, vboBuff)
            vbo = vboBuff[0]
        }

        // Upload vertex data
        pgl.bindBuffer(PGL.ARRAY_BUFFER, vbo)
        pgl.bufferData(PGL.ARRAY_BUFFER, usedVerts * vertSize,
                FloatBuffer.wrap(vertexData), PGL.DYNAMIC_DRAW)
        val shader = shader
        shader!!.bind()
        setAttribs()
        loadUniforms()
        pgl.drawArrays(PGL.TRIANGLES, 0, usedVerts)
        usedVerts = 0
        shader.unbind()

        //XXX: DEBUG
//    println("flushed: " + tex + ", " + imageTex);
    }

    private fun checkShaderLocs(shader: PShader): Boolean {
        var positionLoc = shader.getAttributeLoc("position")
        if (positionLoc == -1) {
            positionLoc = shader.getAttributeLoc("vertex")
        }
        //    int colorLoc = shader.getAttributeLoc("color");
        var transformLoc = shader.getUniformLoc("transform")
        if (transformLoc == -1) {
            transformLoc = shader.getUniformLoc("transformMatrix")
        }

        /*
    // Became less demanding and 2D shaders do not need to have texture uniforms/attribs
    int texScaleLoc = shader.getUniformLoc("texScale");
    if (texScaleLoc == -1) {
      texScaleLoc = shader.getUniformLoc("texOffset");
    }
    int texCoordLoc = shader.getAttributeLoc("texCoord");
    int texFactorLoc = shader.getAttributeLoc("texFactor");
    */return positionLoc != -1 && transformLoc != -1
        //         colorLoc != -1 && texCoordLoc != -1 && texFactorLoc != -1 && texScaleLoc != -1;
    }

    private fun loadShaderLocs(shader: PShader?) {
        positionLoc = shader!!.getAttributeLoc("position")
        if (positionLoc == -1) {
            positionLoc = shader.getAttributeLoc("vertex")
        }
        colorLoc = shader.getAttributeLoc("color")
        texCoordLoc = shader.getAttributeLoc("texCoord")
        texFactorLoc = shader.getAttributeLoc("texFactor")
        transformLoc = shader.getUniformLoc("transform")
        if (transformLoc == -1) {
            transformLoc = shader.getUniformLoc("transformMatrix")
        }
        texScaleLoc = shader.getUniformLoc("texScale")
        if (texScaleLoc == -1) {
            texScaleLoc = shader.getUniformLoc("texOffset")
        }
    }

    // TODO: Perhaps a better way to handle the new 2D rendering would be to define a PShader2D
    // subclass of PShader...
    private val shader: PShader?
        private get() {
            // TODO: Perhaps a better way to handle the new 2D rendering would be to define a PShader2D
            // subclass of PShader...
            val shader: PShader?
            if (twoShader == null) {
                if (defTwoShader == null) {
                    val vertSource = pgl.loadVertexShader(defP2DShaderVertURL)
                    val fragSource = pgl.loadFragmentShader(defP2DShaderFragURL)
                    defTwoShader = PShader(parent, vertSource, fragSource)
                }
                shader = defTwoShader
            } else {
                shader = twoShader
            }
            //    if (shader != defTwoShader) {
            loadShaderLocs(shader)
            //    }
            return shader
        }

    override fun getPolyShader(lit: Boolean, tex: Boolean): PShader {
        return super.getPolyShader(lit, tex)
    }

    private fun setAttribs() {
        pgl.vertexAttribPointer(positionLoc, 3, PGL.FLOAT, false, vertSize, 0)
        pgl.enableVertexAttribArray(positionLoc)
        if (-1 < texCoordLoc) {
            pgl.vertexAttribPointer(texCoordLoc, 2, PGL.FLOAT, false, vertSize, 3 * java.lang.Float.BYTES)
            pgl.enableVertexAttribArray(texCoordLoc)
        }
        pgl.vertexAttribPointer(colorLoc, 4, PGL.UNSIGNED_BYTE, true, vertSize, 5 * java.lang.Float.BYTES)
        pgl.enableVertexAttribArray(colorLoc)
        if (-1 < texFactorLoc) {
            pgl.vertexAttribPointer(texFactorLoc, 1, PGL.FLOAT, false, vertSize, 6 * java.lang.Float.BYTES)
            pgl.enableVertexAttribArray(texFactorLoc)
        }
    }

    private fun loadUniforms() {
        //set matrix uniform
        pgl.uniformMatrix4fv(transformLoc, 1, true, FloatBuffer.wrap(PMatrix3D()[null]))

        //set texture info
        pgl.activeTexture(PGL.TEXTURE0)
        pgl.bindTexture(PGL.TEXTURE_2D, tex)
        if (-1 < texScaleLoc) {
            //enable uv scaling only for use-defined images, not for fonts
            if (tex == imageTex) {
                pgl.uniform2f(texScaleLoc, 1f / texWidth, 1f / texHeight)
            } else {
                pgl.uniform2f(texScaleLoc, 1f, 1f)
            }
        }
    }

    private fun textureImpl(glId: Int) {
        if (glId == tex) {
            return  //texture is already bound; no work to be done
        }
        flushBuffer()
        tex = glId
    }

    private fun check(newVerts: Int) {
        if (usedVerts + newVerts > maxVerts) {
            flushBuffer()
        }
    }

    private fun vertexImpl(x: Float, y: Float, u: Float, v: Float, c: Int, f: Float) {
        val idx = usedVerts * 7
        //inline multiply only x and y to avoid an allocation and a few flops
        vertexData[idx + 0] = projmodelview.m00 * x + projmodelview.m01 * y + projmodelview.m03
        vertexData[idx + 1] = projmodelview.m10 * x + projmodelview.m11 * y + projmodelview.m13
        vertexData[idx + 2] = depth
        vertexData[idx + 3] = u
        vertexData[idx + 4] = v
        vertexData[idx + 5] = java.lang.Float.intBitsToFloat(c)
        vertexData[idx + 6] = f
        usedVerts++
    }

    private fun vertexImpl(vert: TessVertex?) {
        vertexImpl(vert!!.x, vert.y, vert.u, vert.v, vert.c, vert.f)
    }

    //one of POINTS, LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP, QUADS, QUAD_STRIP, POLYGON
    private var shapeType = 0
    private var vertCount = 0
    private var shapeVerts = arrayOfNulls<TessVertex>(16) //initial size is arbitrary

    //list of indices (into shapeVerts array) at which a new contour begins
    private var contours = IntArray(2) //initial size is arbitrary
    private var contourCount = 0
    private fun appendContour(vertIndex: Int) {
        //dynamically expand contour array as needed
        if (contourCount >= contours.size) {
            contours = PApplet.expand(contours, contours.size * 2)
        }
        contours[contourCount] = vertIndex
        contourCount += 1
    }

    //used by endShape() as a temporary to avoid unnecessary allocations
    private val tempDoubles = DoubleArray(3)

    //If this flag is set, then the next call to endShape() with shape type of POLYGON
    //will triangulate blindly instead of going through the GLU tessellator (for performance).
    //This is useful for shapes (like ellipse(), rect(), etc.) that we know will always be convex.
    //TODO: Make this an optional argument to endShape()
    //once we start integrating PGraphics4D into the rest of the codebase.
    private var knownConvexPolygon = false
    private fun shapeVertex(x: Float, y: Float, u: Float, v: Float, c: Int, f: Float) {
        //avoid adding a duplicate because it will cause the GLU tess to fail spectacularly
        //by spitting out-of-memory errors and passing null parameters to the combine() callback
        //TODO: figure out why that happens and how to stop it
        //(P2D renderer doesn't appear to have such a problem, so presumably there must be a way)
        for (i in 0 until vertCount) {
            if (shapeVerts[i]!!.x == x && shapeVerts[i]!!.y == y) {
                return
            }
        }

        //dynamically expand input vertex array as needed
        if (vertCount >= shapeVerts.size) {
            shapeVerts = PApplet.expand(shapeVerts, shapeVerts.size * 2) as Array<TessVertex?>

            //allocate objects for the new half of the array so we don't NPE ourselves
            for (i in shapeVerts.size / 2 until shapeVerts.size) {
                shapeVerts[i] = TessVertex()
            }
        }
        shapeVerts[vertCount]!![x, y, u, v, c] = f
        vertCount += 1
    }

    private fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        check(3)
        vertexImpl(x1, y1, 0f, 0f, color, 0f)
        vertexImpl(x2, y2, 0f, 0f, color, 0f)
        vertexImpl(x3, y3, 0f, 0f, color, 0f)
    }

    private fun singleLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        var x1 = x1
        var y1 = y1
        var x2 = x2
        var y2 = y2
        val r = strokeWeight * 0.5f
        val dx = x2 - x1
        val dy = y2 - y1
        val d = PApplet.sqrt(dx * dx + dy * dy)
        var tx = dy / d * r
        var ty = dx / d * r
        if (strokeCap == PConstants.PROJECT) {
            x1 -= ty
            x2 += ty
            y1 -= tx
            y2 += tx
        }
        triangle(x1 - tx, y1 + ty, x1 + tx, y1 - ty, x2 - tx, y2 + ty, color)
        triangle(x2 + tx, y2 - ty, x2 - tx, y2 + ty, x1 + tx, y1 - ty, color)
        if (r >= LINE_DETAIL_LIMIT && strokeCap == PConstants.ROUND) {
            val segments = circleDetail(r, PConstants.HALF_PI)
            val step = PConstants.HALF_PI / segments
            val c = PApplet.cos(step)
            val s = PApplet.sin(step)
            for (i in 0 until segments) {
                //this is the equivalent of multiplying the vector <tx, ty> by the 2x2 rotation matrix [[c -s] [s c]]
                val nx = c * tx - s * ty
                val ny = s * tx + c * ty
                triangle(x2, y2, x2 + ty, y2 + tx, x2 + ny, y2 + nx, color)
                triangle(x2, y2, x2 - tx, y2 + ty, x2 - nx, y2 + ny, color)
                triangle(x1, y1, x1 - ty, y1 - tx, x1 - ny, y1 - nx, color)
                triangle(x1, y1, x1 + tx, y1 - ty, x1 + nx, y1 - ny, color)
                tx = nx
                ty = ny
            }
        }
    }

    private fun singlePoint(x: Float, y: Float, color: Int) {
        val r = strokeWeight * 0.5f
        if (r >= LINE_DETAIL_LIMIT && strokeCap == PConstants.ROUND) {
            val segments = circleDetail(r)
            val step = PConstants.QUARTER_PI / segments
            var x1 = 0f
            var y1 = r
            val c = PApplet.cos(step)
            val s = PApplet.sin(step)
            for (i in 0 until segments) {
                //this is the equivalent of multiplying the vector <x1, y1> by the 2x2 rotation matrix [[c -s] [s c]]
                val x2 = c * x1 - s * y1
                val y2 = s * x1 + c * y1
                triangle(x, y, x + x1, y + y1, x + x2, y + y2, strokeColor)
                triangle(x, y, x + x1, y - y1, x + x2, y - y2, strokeColor)
                triangle(x, y, x - x1, y + y1, x - x2, y + y2, strokeColor)
                triangle(x, y, x - x1, y - y1, x - x2, y - y2, strokeColor)
                triangle(x, y, x + y1, y + x1, x + y2, y + x2, strokeColor)
                triangle(x, y, x + y1, y - x1, x + y2, y - x2, strokeColor)
                triangle(x, y, x - y1, y + x1, x - y2, y + x2, strokeColor)
                triangle(x, y, x - y1, y - x1, x - y2, y - x2, strokeColor)
                x1 = x2
                y1 = y2
            }
        } else {
            triangle(x - r, y - r, x + r, y - r, x - r, y + r, color)
            triangle(x + r, y - r, x - r, y + r, x + r, y + r, color)
        }
    }

    private val sr = StrokeRenderer()

    private inner class StrokeRenderer {
        var lineVertexCount = 0
        var fx = 0f
        var fy = 0f
        var sx = 0f
        var sy = 0f
        var sdx = 0f
        var sdy = 0f
        var px = 0f
        var py = 0f
        var pdx = 0f
        var pdy = 0f
        var lx = 0f
        var ly = 0f
        var r = 0f
        fun arcJoin(x: Float, y: Float, dx1: Float, dy1: Float, dx2: Float, dy2: Float) {
            //we don't need to normalize before doing these products
            //since the vectors are the same length and only used as arguments to atan2()
            var dx1 = dx1
            var dy1 = dy1
            val cross = dx1 * dy2 - dy1 * dx2
            val dot = dx1 * dx2 + dy1 * dy2
            val theta = PApplet.atan2(cross, dot)
            val segments = circleDetail(r, theta)
            var px = x + dx1
            var py = y + dy1
            if (segments > 1) {
                val c = PApplet.cos(theta / segments)
                val s = PApplet.sin(theta / segments)
                for (i in 1 until segments) {
                    //this is the equivalent of multiplying the vector <dx1, dy1> by the 2x2 rotation matrix [[c -s] [s c]]
                    val tempx = c * dx1 - s * dy1
                    dy1 = s * dx1 + c * dy1
                    dx1 = tempx
                    val nx = x + dx1
                    val ny = y + dy1
                    triangle(x, y, px, py, nx, ny, strokeColor)
                    px = nx
                    py = ny
                }
            }
            triangle(x, y, px, py, x + dx2, y + dy2, strokeColor)
        }

        fun beginLine() {
            lineVertexCount = 0
            r = strokeWeight * 0.5f
        }

        fun lineVertex(x: Float, y: Float) {
            //disallow adding consecutive duplicate vertices,
            //as it is pointless and just creates an extra edge case
            if (lineVertexCount > 0 && x == lx && y == ly) {
                return
            }
            if (lineVertexCount == 0) {
                fx = x
                fy = y
            } else if (r < LINE_DETAIL_LIMIT) {
                singleLine(lx, ly, x, y, strokeColor)
            } else if (lineVertexCount == 1) {
                sx = x
                sy = y
            } else {
                //calculate normalized direction vectors for each leg
                var leg1x = lx - px
                var leg1y = ly - py
                var leg2x = x - lx
                var leg2y = y - ly
                val len1 = PApplet.sqrt(leg1x * leg1x + leg1y * leg1y)
                val len2 = PApplet.sqrt(leg2x * leg2x + leg2y * leg2y)
                leg1x /= len1
                leg1y /= len1
                leg2x /= len2
                leg2y /= len2
                val legDot = -leg1x * leg2x - leg1y * leg2y
                val cosPiOver15 = 0.97815f
                if (strokeJoin == PConstants.BEVEL || strokeJoin == PConstants.ROUND || legDot > cosPiOver15 || legDot < -0.999) {
                    val tx = leg1y * r
                    val ty = -leg1x * r
                    if (lineVertexCount == 2) {
                        sdx = tx
                        sdy = ty
                    } else {
                        triangle(px - pdx, py - pdy, px + pdx, py + pdy, lx - tx, ly - ty, strokeColor)
                        triangle(px + pdx, py + pdy, lx - tx, ly - ty, lx + tx, ly + ty, strokeColor)
                    }
                    val nx = leg2y * r
                    val ny = -leg2x * r
                    val legCross = leg1x * leg2y - leg1y * leg2x
                    if (strokeJoin == PConstants.ROUND) {
                        if (legCross > 0) {
                            arcJoin(lx, ly, tx, ty, nx, ny)
                        } else {
                            arcJoin(lx, ly, -tx, -ty, -nx, -ny)
                        }
                    } else if (legCross > 0) {
                        triangle(lx, ly, lx + tx, ly + ty, lx + nx, ly + ny, strokeColor)
                    } else {
                        triangle(lx, ly, lx - tx, ly - ty, lx - nx, ly - ny, strokeColor)
                    }
                    pdx = nx
                    pdy = ny
                } else { //miter joint
                    //find the bisecting vector
                    val x1 = leg2x - leg1x
                    val y1 = leg2y - leg1y
                    //find a (normalized) vector perpendicular to one of the legs
                    val x2 = leg1y
                    val y2 = -leg1x
                    //scale the bisecting vector to the correct length using magic (not sure how to explain this one)
                    val dot = x1 * x2 + y1 * y2
                    val bx = x1 * (r / dot)
                    val by = y1 * (r / dot)
                    if (lineVertexCount == 2) {
                        sdx = bx
                        sdy = by
                    } else {
                        triangle(px - pdx, py - pdy, px + pdx, py + pdy, lx - bx, ly - by, strokeColor)
                        triangle(px + pdx, py + pdy, lx - bx, ly - by, lx + bx, ly + by, strokeColor)
                    }
                    pdx = bx
                    pdy = by
                }
            }
            px = lx
            py = ly
            lx = x
            ly = y
            lineVertexCount += 1
        }

        fun lineCap(x: Float, y: Float, dx: Float, dy: Float) {
            val segments = circleDetail(r, PConstants.HALF_PI)
            var px = dy
            var py = -dx
            if (segments > 1) {
                val c = PApplet.cos(PConstants.HALF_PI / segments)
                val s = PApplet.sin(PConstants.HALF_PI / segments)
                for (i in 1 until segments) {
                    //this is the equivalent of multiplying the vector <px, py> by the 2x2 rotation matrix [[c -s] [s c]]
                    val nx = c * px - s * py
                    val ny = s * px + c * py
                    triangle(x, y, x + px, y + py, x + nx, y + ny, strokeColor)
                    triangle(x, y, x - py, y + px, x - ny, y + nx, strokeColor)
                    px = nx
                    py = ny
                }
            }
            triangle(x, y, x + px, y + py, x + dx, y + dy, strokeColor)
            triangle(x, y, x - py, y + px, x - dy, y + dx, strokeColor)
        }

        fun endLine(closed: Boolean) {
            if (lineVertexCount < 2) {
                return
            }
            if (lineVertexCount == 2) {
                singleLine(px, py, lx, ly, strokeColor)
                return
            }
            if (r < LINE_DETAIL_LIMIT) {
                if (closed) {
                    singleLine(lx, ly, fx, fy, strokeColor)
                }
                return
            }
            if (closed) {
                //draw the last two legs
                lineVertex(fx, fy)
                lineVertex(sx, sy)

                //connect first and second vertices
                triangle(px - pdx, py - pdy, px + pdx, py + pdy, sx - sdx, sy - sdy, strokeColor)
                triangle(px + pdx, py + pdy, sx - sdx, sy - sdy, sx + sdx, sy + sdy, strokeColor)
            } else {
                //draw last line (with cap)
                var dx = lx - px
                var dy = ly - py
                var d = PApplet.sqrt(dx * dx + dy * dy)
                var tx = dy / d * r
                var ty = -dx / d * r
                if (strokeCap == PConstants.PROJECT) {
                    lx -= ty
                    ly += tx
                }
                triangle(px - pdx, py - pdy, px + pdx, py + pdy, lx - tx, ly - ty, strokeColor)
                triangle(px + pdx, py + pdy, lx - tx, ly - ty, lx + tx, ly + ty, strokeColor)
                if (strokeCap == PConstants.ROUND) {
                    lineCap(lx, ly, -ty, tx)
                }

                //draw first line (with cap)
                dx = fx - sx
                dy = fy - sy
                d = PApplet.sqrt(dx * dx + dy * dy)
                tx = dy / d * r
                ty = -dx / d * r
                if (strokeCap == PConstants.PROJECT) {
                    fx -= ty
                    fy += tx
                }
                triangle(sx - sdx, sy - sdy, sx + sdx, sy + sdy, fx + tx, fy + ty, strokeColor)
                triangle(sx + sdx, sy + sdy, fx + tx, fy + ty, fx - tx, fy - ty, strokeColor)
                if (strokeCap == PConstants.ROUND) {
                    lineCap(fx, fy, -ty, tx)
                }
            }
        }
    }

    //returns the total number of points needed to approximate an arc of a given radius and extent
    //returns the number of points per quadrant needed to approximate a circle of a given radius
    @JvmOverloads
    fun circleDetail(radius: Float, delta: Float = PConstants.QUARTER_PI): Int {
        //this serves as a rough approximation of how much the longest axis
        //of an ellipse will be scaled by a given matrix
        //(in other words, the amount by which its on-screen size changes)
        var radius = radius
        val sxi = projmodelview.m00 * width / 2
        val syi = projmodelview.m10 * height / 2
        val sxj = projmodelview.m01 * width / 2
        val syj = projmodelview.m11 * height / 2
        val Imag2 = sxi * sxi + syi * syi
        val Jmag2 = sxj * sxj + syj * syj
        val ellipseDetailMultiplier = PApplet.sqrt(PApplet.max(Imag2, Jmag2))
        radius *= ellipseDetailMultiplier
        return (PApplet.min(127f, PApplet.sqrt(radius) / PConstants.QUARTER_PI * PApplet.abs(delta) * 0.75f) + 1).toInt()
    }

    private inner class TessVertex {
        @JvmField
        var x = 0f
        @JvmField
        var y = 0f
        @JvmField
        var u = 0f
        @JvmField
        var v = 0f
        @JvmField
        var c = 0
        @JvmField
        var f = 0f //1.0 if textured, 0.0 if flat = 0f

        constructor() {
            //no-op
        }

        constructor(x: Float, y: Float, u: Float, v: Float, c: Int, f: Float) {
            set(x, y, u, v, c, f)
        }

        operator fun set(x: Float, y: Float, u: Float, v: Float, c: Int, f: Float) {
            this.x = x
            this.y = y
            this.u = u
            this.v = v
            this.c = c
            this.f = f
        }

        override fun toString(): String {
            return "$x, $y"
        }
    }

    companion object {
        const val NON_2D_SHAPE_ERROR = "The shape object is not 2D, cannot be displayed with this renderer"
        const val STROKE_PERSPECTIVE_ERROR = "Strokes cannot be perspective-corrected in 2D"
        const val NON_2D_SHADER_ERROR = "This shader cannot be used for 2D rendering"
        const val WRONG_SHADER_PARAMS = "The P2D renderer does not accept shaders of different tyes"
        protected const val SHADER2D = 7
        protected var defP2DShaderVertURL = PGraphicsOpenGL::class.java.getResource("/assets/shaders/P2DVert.glsl")
        protected var defP2DShaderFragURL = PGraphicsOpenGL::class.java.getResource("/assets/shaders/P2DFrag.glsl")

        //////////////////////////////////////////////////////////////
        // SHAPE I/O
        @JvmStatic
        protected fun isSupportedExtension(extension: String): Boolean {
            return extension == "svg" || extension == "svgz"
        }

        @JvmStatic
        protected fun loadShapeImpl(pg: PGraphics,
                                    filename: String?, extension: String): PShape? {
            if (extension == "svg" || extension == "svgz") {
                val svg = PShapeSVG(pg.parent.loadXML(filename))
                return PShapeOpenGL.createShape(pg as PGraphicsOpenGL, svg)
            }
            return null
        }

        //below r == LINE_DETAIL_LIMIT, all lines will be drawn as plain rectangles
        //instead of using fancy stroke rendering algorithms, since the result is visually indistinguishable
        private const val LINE_DETAIL_LIMIT = 1.0f
    }

    // constructor or initializer block
    init {
        initTess()
        initVerts()
    }
}