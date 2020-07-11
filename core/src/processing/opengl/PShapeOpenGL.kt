/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-15 The Processing Foundation
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
import java.util.*

/**
 * This class holds a 3D model composed of vertices, normals, colors
 * (per vertex) and texture coordinates (also per vertex). All this data is
 * stored in Vertex Buffer Objects (VBO) in GPU memory for very fast access.
 * OBJ loading implemented using code from Saito's OBJLoader library:
 * http://code.google.com/p/saitoobjloader/
 * and OBJReader from Ahmet Kizilay
 * http://www.openprocessing.org/visuals/?visualID=191
 * By Andres Colubri
 *
 *
 * Other formats to consider:
 * AMF: http://en.wikipedia.org/wiki/Additive_Manufacturing_File_Format
 * STL: http://en.wikipedia.org/wiki/STL_(file_format)
 * OFF: http://people.sc.fsu.edu/~jburkardt/data/off/off.html(file_format)
 * DXF: http://en.wikipedia.org/wiki/AutoCAD_DXF
 */
open class PShapeOpenGL : PShape {
    protected var pg: PGraphicsOpenGL? = null
    protected var pgl: PGL? = null
    protected  var context = 0 // The context that created this shape.
    protected var root: PShapeOpenGL? = null

    // ........................................................
    // Input, tessellated geometry
    private var inGeo: PGraphicsOpenGL.InGeometry? = null
    private var tessGeo: PGraphicsOpenGL.TessGeometry? = null
    private var tessellator: PGraphicsOpenGL.Tessellator? = null
    private var polyAttribs: PGraphicsOpenGL.AttributeMap? = null

    // ........................................................
    // Texturing
    protected var textures: HashSet<PImage?>? = null
    protected var strokedTexture = false
    protected var untexChild = false

    // ........................................................
    // OpenGL buffers
    protected var bufPolyVertex: VertexBuffer? = null
    protected var bufPolyColor: VertexBuffer? = null
    protected var bufPolyNormal: VertexBuffer? = null
    protected var bufPolyTexcoord: VertexBuffer? = null
    protected var bufPolyAmbient: VertexBuffer? = null
    protected var bufPolySpecular: VertexBuffer? = null
    protected var bufPolyEmissive: VertexBuffer? = null
    protected var bufPolyShininess: VertexBuffer? = null
    protected var bufPolyIndex: VertexBuffer? = null
    protected var bufLineVertex: VertexBuffer? = null
    protected var bufLineColor: VertexBuffer? = null
    protected var bufLineAttrib: VertexBuffer? = null
    protected var bufLineIndex: VertexBuffer? = null
    protected var bufPointVertex: VertexBuffer? = null
    protected var bufPointColor: VertexBuffer? = null
    protected var bufPointAttrib: VertexBuffer? = null
    protected var bufPointIndex: VertexBuffer? = null

    // Testing this field, not use as it might go away...
    var glUsage = PGL.STATIC_DRAW

    // ........................................................
    // Offsets for geometry aggregation and update.
    protected var polyVertCopyOffset = 0
    protected var polyIndCopyOffset = 0
    protected var lineVertCopyOffset = 0
    protected var lineIndCopyOffset = 0
    protected var pointVertCopyOffset = 0
    protected var pointIndCopyOffset = 0
    protected var polyIndexOffset = 0
    protected var polyVertexOffset = 0
    protected var polyVertexAbs = 0
    protected var polyVertexRel = 0
    protected var lineIndexOffset = 0
    protected var lineVertexOffset = 0
    protected var lineVertexAbs = 0
    protected var lineVertexRel = 0
    protected var pointIndexOffset = 0
    protected var pointVertexOffset = 0
    protected var pointVertexAbs = 0
    protected var pointVertexRel = 0
    protected var firstPolyIndexCache = 0
    protected var lastPolyIndexCache = 0
    protected var firstLineIndexCache = 0
    protected var lastLineIndexCache = 0
    protected var firstPointIndexCache = 0
    protected var lastPointIndexCache = 0
    protected var firstPolyVertex = 0
    protected var lastPolyVertex = 0
    protected var firstLineVertex = 0
    protected var lastLineVertex = 0
    protected var firstPointVertex = 0
    protected var lastPointVertex = 0

    // ........................................................
    // Geometric transformations.
    protected var transform: PMatrix? = null
    protected var transformInv: PMatrix? = null
    protected var matrixInv: PMatrix? = null

    // ........................................................
    // State/rendering flags
    protected var tessellated = false
    protected var needBufferInit = false

    // Flag to indicate if the shape can have holes or not.
    protected var solid = true
    protected var breakShape = false
    protected var shapeCreated = false

    // These variables indicate if the shape contains
    // polygon, line and/or point geometry. In the case of
    // 3D shapes, poly geometry is coincident with the fill
    // triangles, as the lines and points are stored separately.
    // However, for 2D shapes the poly geometry contains all of
    // the three since the same rendering shader applies to
    // fill, line and point geometry.
    protected var hasPolys = false
    protected var hasLines = false
    protected var hasPoints = false

    // ........................................................
    // Bezier and Catmull-Rom curves
    protected var bezierDetail = 0
    protected var curveDetail = 0
    protected var curveTightness = 0f
    protected var savedBezierDetail = 0
    protected var savedCurveDetail = 0
    protected var savedCurveTightness = 0f

    // ........................................................
    // Normals
    protected var normalX = 0f
    protected var normalY = 0f
    protected var normalZ = 0f

    // Current mode for normals, one of AUTO, SHAPE, or VERTEX
    protected var normalMode = 0

    // ........................................................
    // Modification variables (used only by the root shape)
    protected var modified = false
    protected var modifiedPolyVertices = false
    protected var modifiedPolyColors = false
    protected var modifiedPolyNormals = false
    protected var modifiedPolyTexCoords = false
    protected var modifiedPolyAmbient = false
    protected var modifiedPolySpecular = false
    protected var modifiedPolyEmissive = false
    protected var modifiedPolyShininess = false
    protected var modifiedLineVertices = false
    protected var modifiedLineColors = false
    protected var modifiedLineAttributes = false
    protected var modifiedPointVertices = false
    protected var modifiedPointColors = false
    protected var modifiedPointAttributes = false
    protected var firstModifiedPolyVertex = 0
    protected var lastModifiedPolyVertex = 0
    protected var firstModifiedPolyColor = 0
    protected var lastModifiedPolyColor = 0
    protected var firstModifiedPolyNormal = 0
    protected var lastModifiedPolyNormal = 0
    protected var firstModifiedPolyTexcoord = 0
    protected var lastModifiedPolyTexcoord = 0
    protected var firstModifiedPolyAmbient = 0
    protected var lastModifiedPolyAmbient = 0
    protected var firstModifiedPolySpecular = 0
    protected var lastModifiedPolySpecular = 0
    protected var firstModifiedPolyEmissive = 0
    protected var lastModifiedPolyEmissive = 0
    protected var firstModifiedPolyShininess = 0
    protected var lastModifiedPolyShininess = 0
    protected var firstModifiedLineVertex = 0
    protected var lastModifiedLineVertex = 0
    protected var firstModifiedLineColor = 0
    protected var lastModifiedLineColor = 0
    protected var firstModifiedLineAttribute = 0
    protected var lastModifiedLineAttribute = 0
    protected var firstModifiedPointVertex = 0
    protected var lastModifiedPointVertex = 0
    protected var firstModifiedPointColor = 0
    protected var lastModifiedPointColor = 0
    protected var firstModifiedPointAttribute = 0
    protected var lastModifiedPointAttribute = 0

    // ........................................................
    // Saved style variables to style can be re-enabled after disableStyle,
    // although it won't work if properties are defined on a per-vertex basis.
    protected var savedStroke = false
    protected var savedStrokeColor = 0
    protected var savedStrokeWeight = 0f
    protected var savedStrokeCap = 0
    protected var savedStrokeJoin = 0
    protected var savedFill = false
    protected var savedFillColor = 0
    protected var savedTint = false
    protected var savedTintColor = 0
    protected var savedAmbientColor = 0
    protected var savedSpecularColor = 0
    protected var savedEmissiveColor = 0
    protected var savedShininess = 0f
    protected var savedTextureMode = 0

    internal constructor() {

    }

    constructor(pg: PGraphicsOpenGL, family: Int) {
        this.pg = pg
        this.family = family
        pgl = pg.pgl
        context = pgl!!.createEmptyContext()
        bufPolyVertex = null
        bufPolyColor = null
        bufPolyNormal = null
        bufPolyTexcoord = null
        bufPolyAmbient = null
        bufPolySpecular = null
        bufPolyEmissive = null
        bufPolyShininess = null
        bufPolyIndex = null
        bufLineVertex = null
        bufLineColor = null
        bufLineAttrib = null
        bufLineIndex = null
        bufPointVertex = null
        bufPointColor = null
        bufPointAttrib = null
        bufPointIndex = null
        tessellator = pg.tessellator
        this.root = this
        parent = null
        tessellated = false
        if (family == GEOMETRY || family == PRIMITIVE || family == PATH) {
            polyAttribs = PGraphicsOpenGL.newAttributeMap()
            inGeo = PGraphicsOpenGL.newInGeometry(pg, polyAttribs, PGraphicsOpenGL.RETAINED)
        }

        // Style parameters are retrieved from the current values in the renderer.
        textureMode = pg.textureMode
        colorMode(pg.colorMode,
                pg.colorModeX, pg.colorModeY, pg.colorModeZ, pg.colorModeA)

        // Initial values for fill, stroke and tint colors are also imported from
        // the renderer. This is particular relevant for primitive shapes, since is
        // not possible to set their color separately when creating them, and their
        // input vertices are actually generated at rendering time, by which the
        // color configuration of the renderer might have changed.
        fill = pg.fill
        fillColor = pg.fillColor
        stroke = pg.stroke
        strokeColor = pg.strokeColor
        strokeWeight = pg.strokeWeight
        strokeCap = pg.strokeCap
        strokeJoin = pg.strokeJoin
        tint = pg.tint
        tintColor = pg.tintColor
        setAmbient = pg.setAmbient
        ambientColor = pg.ambientColor
        specularColor = pg.specularColor
        emissiveColor = pg.emissiveColor
        shininess = pg.shininess
        sphereDetailU = pg.sphereDetailU
        sphereDetailV = pg.sphereDetailV
        bezierDetail = pg.bezierDetail
        curveDetail = pg.curveDetail
        curveTightness = pg.curveTightness
        rectMode = pg.rectMode
        ellipseMode = pg.ellipseMode
        normalY = 0f
        normalX = normalY
        normalZ = 1f
        normalMode = NORMAL_MODE_AUTO

        // To make sure that the first vertex is marked as a break.
        // Same behavior as in the immediate mode.
        breakShape = false
        if (family == PConstants.GROUP) {
            // GROUP shapes are always marked as ended.
            shapeCreated = true
        }

        // OpenGL supports per-vertex coloring (unlike Java2D)
        perVertexStyles = true
    }

    /** Create a shape from the PRIMITIVE family, using this kind and these params  */
    constructor(pg: PGraphicsOpenGL, kind: Int, vararg p: Float) : this(pg, PRIMITIVE) {
        setKind(kind)
        setParams(p)
    }

    override fun addChild(who: PShape) {
        if (who is PShapeOpenGL) {
            if (family == PConstants.GROUP) {
                val c3d = who
                super.addChild(c3d)
                c3d.updateRoot(root)
                markForTessellation()
                if (c3d.family == PConstants.GROUP) {
                    if (c3d.textures != null) {
                        for (tex in c3d.textures!!) {
                            addTexture(tex)
                        }
                    } else {
                        untexChild(true)
                    }
                    if (c3d.strokedTexture) {
                        strokedTexture(true)
                    }
                } else {
                    if (c3d.image != null) {
                        addTexture(c3d.image)
                        if (c3d.stroke) {
                            strokedTexture(true)
                        }
                    } else {
                        untexChild(true)
                    }
                }
            } else {
                PGraphics.showWarning("Cannot add child shape to non-group shape.")
            }
        } else {
            PGraphics.showWarning("Shape must be OpenGL to be added to the group.")
        }
    }

    override fun addChild(who: PShape, idx: Int) {
        if (who is PShapeOpenGL) {
            if (family == PConstants.GROUP) {
                val c3d = who
                super.addChild(c3d, idx)
                c3d.updateRoot(root)
                markForTessellation()
                if (c3d.family == PConstants.GROUP) {
                    if (c3d.textures != null) {
                        for (tex in c3d.textures!!) {
                            addTexture(tex)
                        }
                    } else {
                        untexChild(true)
                    }
                    if (c3d.strokedTexture) {
                        strokedTexture(true)
                    }
                } else {
                    if (c3d.image != null) {
                        addTexture(c3d.image)
                        if (c3d.stroke) {
                            strokedTexture(true)
                        }
                    } else {
                        untexChild(true)
                    }
                }
            } else {
                PGraphics.showWarning("Cannot add child shape to non-group shape.")
            }
        } else {
            PGraphics.showWarning("Shape must be OpenGL to be added to the group.")
        }
    }

    override fun removeChild(idx: Int) {
        super.removeChild(idx)
        strokedTexture(false)
        untexChild(false)
        markForTessellation()
    }

    protected fun updateRoot(root: PShape?) {
        this.root = root as PShapeOpenGL?
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.updateRoot(root)
            }
        }
    }

    /*
  static public void copyGroup2D(PGraphicsOpenGL pg, PShape src, PShape dest) {
    copyMatrix(src, dest);
    copyStyles(src, dest);
    copyImage(src, dest);

    for (int i = 0; i < src.getChildCount(); i++) {
      PShape c = createShape2D(pg, src.getChild(i));
      dest.addChild(c);
    }
  }
*/
    ///////////////////////////////////////////////////////////
    //
    // Query methods
    override fun getWidth(): Float {
        val min = PVector(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = PVector(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        if (shapeCreated) {
            getVertexMin(min)
            getVertexMax(max)
        }
        width = max.x - min.x
        return width
    }

    override fun getHeight(): Float {
        val min = PVector(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = PVector(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        if (shapeCreated) {
            getVertexMin(min)
            getVertexMax(max)
        }
        height = max.y - min.y
        return height
    }

    override fun getDepth(): Float {
        val min = PVector(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val max = PVector(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        if (shapeCreated) {
            getVertexMin(min)
            getVertexMax(max)
        }
        depth = max.z - min.z
        return depth
    }

    protected fun getVertexMin(min: PVector?) {
        updateTessellation()
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.getVertexMin(min)
            }
        } else {
            if (hasPolys) {
                tessGeo!!.getPolyVertexMin(min, firstPolyVertex, lastPolyVertex)
            }
            if (is3D()) {
                if (hasLines) {
                    tessGeo!!.getLineVertexMin(min, firstLineVertex, lastLineVertex)
                }
                if (hasPoints) {
                    tessGeo!!.getPointVertexMin(min, firstPointVertex, lastPointVertex)
                }
            }
        }
    }

    protected fun getVertexMax(max: PVector?) {
        updateTessellation()
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.getVertexMax(max)
            }
        } else {
            if (hasPolys) {
                tessGeo!!.getPolyVertexMax(max, firstPolyVertex, lastPolyVertex)
            }
            if (is3D()) {
                if (hasLines) {
                    tessGeo!!.getLineVertexMax(max, firstLineVertex, lastLineVertex)
                }
                if (hasPoints) {
                    tessGeo!!.getPointVertexMax(max, firstPointVertex, lastPointVertex)
                }
            }
        }
    }

    protected fun getVertexSum(sum: PVector?, count: Int): Int {
        var count = count
        updateTessellation()
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                count += child.getVertexSum(sum, count)
            }
        } else {
            if (hasPolys) {
                count += tessGeo!!.getPolyVertexSum(sum, firstPolyVertex, lastPolyVertex)
            }
            if (is3D()) {
                if (hasLines) {
                    count += tessGeo!!.getLineVertexSum(sum, firstLineVertex,
                            lastLineVertex)
                }
                if (hasPoints) {
                    count += tessGeo!!.getPointVertexSum(sum, firstPointVertex,
                            lastPointVertex)
                }
            }
        }
        return count
    }

    ///////////////////////////////////////////////////////////

    //

    // Drawing methods

    override fun setTextureMode(mode: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setTextureMode()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setTextureMode(mode)
            }
        } else {
            setTextureModeImpl(mode)
        }
    }

    protected fun setTextureModeImpl(mode: Int) {
        if (textureMode == mode) return
        textureMode = mode
        if (image != null) {
            var uFactor = image.width.toFloat()
            var vFactor = image.height.toFloat()
            if (textureMode == NORMAL) {
                uFactor = 1.0f / uFactor
                vFactor = 1.0f / vFactor
            }
            scaleTextureUV(uFactor, vFactor)
        }
    }

    override fun setTexture(tex: PImage) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setTexture()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setTexture(tex)
            }
        } else {
            setTextureImpl(tex)
        }
    }

    protected fun setTextureImpl(tex: PImage) {
        val image0 = image
        image = tex
        if (textureMode == PConstants.IMAGE && image0 !== image) {
            // Need to rescale the texture coordinates
            var uFactor = 1f
            var vFactor = 1f
            if (image != null) {
                uFactor /= image.width.toFloat()
                vFactor /= image.height.toFloat()
            }
            if (image0 != null) {
                uFactor *= image0.width.toFloat()
                vFactor *= image0.height.toFloat()
            }
            scaleTextureUV(uFactor, vFactor)
        }
        if (image0 !== tex && parent != null) {
            (parent as PShapeOpenGL).removeTexture(image0, this)
        }
        if (parent != null) {
            (parent as PShapeOpenGL).addTexture(image)
            if (is2D && stroke) {
                (parent as PShapeOpenGL).strokedTexture(true)
            }
        }
    }

    protected fun scaleTextureUV(uFactor: Float, vFactor: Float) {
        if (PGraphicsOpenGL.same(uFactor, 1f) &&
                PGraphicsOpenGL.same(vFactor, 1f)) return
        for (i in 0 until inGeo!!.vertexCount) {
            val u = inGeo!!.texcoords[2 * i + 0]
            val v = inGeo!!.texcoords[2 * i + 1]
            inGeo!!.texcoords[2 * i + 0] = PApplet.min(1f, u * uFactor)
            inGeo!!.texcoords[2 * i + 1] = PApplet.min(1f, v * uFactor)
        }
        if (shapeCreated && tessellated && hasPolys) {
            var last1 = 0
            if (is3D()) {
                last1 = lastPolyVertex + 1
            } else if (is2D) {
                last1 = lastPolyVertex + 1
                if (-1 < firstLineVertex) last1 = firstLineVertex
                if (-1 < firstPointVertex) last1 = firstPointVertex
            }
            for (i in firstLineVertex until last1) {
                val u = tessGeo!!.polyTexCoords[2 * i + 0]
                val v = tessGeo!!.polyTexCoords[2 * i + 1]
                tessGeo!!.polyTexCoords[2 * i + 0] = PApplet.min(1f, u * uFactor)
                tessGeo!!.polyTexCoords[2 * i + 1] = PApplet.min(1f, v * uFactor)
            }
            root!!.setModifiedPolyTexCoords(firstPolyVertex, last1 - 1)
        }
    }

    protected fun addTexture(tex: PImage?) {
        if (textures == null) {
            textures = HashSet()
        }
        textures!!.add(tex)
        if (parent != null) {
            (parent as PShapeOpenGL).addTexture(tex)
        }
    }

    protected fun removeTexture(tex: PImage?, caller: PShapeOpenGL) {
        if (textures == null || !textures!!.contains(tex)) return  // Nothing to remove.

        // First check that none of the child shapes have texture tex...
        var childHasTex = false
        for (i in 0 until childCount) {
            val child = children[i] as PShapeOpenGL
            if (child === caller) continue
            if (child.hasTexture(tex)) {
                childHasTex = true
                break
            }
        }
        if (!childHasTex) {
            // ...if not, it is safe to remove from this shape.
            textures!!.remove(tex)
            if (textures!!.size == 0) {
                textures = null
            }
        }

        // Since this shape and all its child shapes don't contain
        // tex anymore, we now can remove it from the parent.
        if (parent != null) {
            (parent as PShapeOpenGL).removeTexture(tex, this)
        }
    }

    protected fun strokedTexture(newValue: Boolean, caller: PShapeOpenGL? = null) {
        if (strokedTexture == newValue) return  // Nothing to change.
        if (newValue) {
            strokedTexture = true
        } else {
            // Check that none of the child shapes have a stroked texture...
            strokedTexture = false
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                if (child === caller) continue
                if (child.hasStrokedTexture()) {
                    strokedTexture = true
                    break
                }
            }
        }

        // Now we can update the parent shape.
        if (parent != null) {
            (parent as PShapeOpenGL).strokedTexture(newValue, this)
        }
    }

    protected fun untexChild(newValue: Boolean, caller: PShapeOpenGL? = null) {
        if (untexChild == newValue) return  // Nothing to change.
        if (newValue) {
            untexChild = true
        } else {
            // Check if any of the child shapes is not textured...
            untexChild = false
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                if (child === caller) continue
                if (!child.hasTexture()) {
                    untexChild = true
                    break
                }
            }
        }

        // Now we can update the parent shape.
        if (parent != null) {
            (parent as PShapeOpenGL).untexChild(newValue, this)
        }
    }

    protected fun hasTexture(): Boolean {
        return if (family == PConstants.GROUP) {
            textures != null && 0 < textures!!.size
        } else {
            image != null
        }
    }

    protected fun hasTexture(tex: PImage?): Boolean {
        return if (family == PConstants.GROUP) {
            textures != null && textures!!.contains(tex)
        } else {
            image === tex
        }
    }

    protected fun hasStrokedTexture(): Boolean {
        return if (family == PConstants.GROUP) {
            strokedTexture
        } else {
            image != null && stroke
        }
    }

    public override fun solid(solid: Boolean) {
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.solid(solid)
            }
        } else {
            this.solid = solid
        }
    }

    override fun beginContourImpl() {
        breakShape = true
    }

    override fun endContourImpl() {}
    override fun vertex(x: Float, y: Float) {
        vertexImpl(x, y, 0f, 0f, 0f)
        if (image != null) PGraphics.showWarning(PGraphicsOpenGL.MISSING_UV_TEXCOORDS_ERROR)
    }

    override fun vertex(x: Float, y: Float, u: Float, v: Float) {
        vertexImpl(x, y, 0f, u, v)
    }

    override fun vertex(x: Float, y: Float, z: Float) {
        vertexImpl(x, y, z, 0f, 0f)
        if (image != null) PGraphics.showWarning(PGraphicsOpenGL.MISSING_UV_TEXCOORDS_ERROR)
    }

    override fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        vertexImpl(x, y, z, u, v)
    }

    protected fun vertexImpl(x: Float, y: Float, z: Float, u: Float, v: Float) {
        var u = u
        var v = v
        if (!openShape) {
            PGraphics.showWarning(OUTSIDE_BEGIN_END_ERROR, "vertex()")
            return
        }
        if (family == PConstants.GROUP) {
            PGraphics.showWarning("Cannot add vertices to GROUP shape")
            return
        }
        val textured = image != null
        var fcolor = 0x00
        if (fill || textured) {
            fcolor = if (!textured) {
                fillColor
            } else {
                if (tint) {
                    tintColor
                } else {
                    -0x1
                }
            }
        }
        if (textureMode == PConstants.IMAGE && image != null) {
            u /= image.width.toFloat()
            v /= image.height.toFloat()
        }
        var scolor = 0x00
        var sweight = 0f
        if (stroke) {
            scolor = strokeColor
            sweight = strokeWeight
        }
        inGeo!!.addVertex(x, y, z,
                fcolor,
                normalX, normalY, normalZ,
                u, v,
                scolor, sweight,
                ambientColor, specularColor, emissiveColor, shininess,
                PConstants.VERTEX, vertexBreak())
        markForTessellation()
    }

    protected fun vertexBreak(): Boolean {
        if (breakShape) {
            breakShape = false
            return true
        }
        return false
    }

    override fun normal(nx: Float, ny: Float, nz: Float) {
        if (!openShape) {
            PGraphics.showWarning(OUTSIDE_BEGIN_END_ERROR, "normal()")
            return
        }
        if (family == PConstants.GROUP) {
            PGraphics.showWarning("Cannot set normal in GROUP shape")
            return
        }
        normalX = nx
        normalY = ny
        normalZ = nz

        // if drawing a shape and the normal hasn't been set yet,
        // then we need to set the normals for each vertex so far
        if (normalMode == NORMAL_MODE_AUTO) {
            // One normal per begin/end shape
            normalMode = NORMAL_MODE_SHAPE
        } else if (normalMode == NORMAL_MODE_SHAPE) {
            // a separate normal for each vertex
            normalMode = NORMAL_MODE_VERTEX
        }
    }

    override fun attribPosition(name: String, x: Float, y: Float, z: Float) {
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.POSITION,
                PGL.FLOAT, 3)
        attrib?.set(x, y, z)
    }

    override fun attribNormal(name: String, nx: Float, ny: Float, nz: Float) {
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.NORMAL,
                PGL.FLOAT, 3)
        attrib?.set(nx, ny, nz)
    }

    override fun attribColor(name: String, color: Int) {
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.COLOR, PGL.INT, 1)
        attrib?.set(intArrayOf(color))
    }

    override fun attrib(name: String, vararg values: Float) {
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.OTHER, PGL.FLOAT,
                values.size)
        attrib?.set(values)
    }

    override fun attrib(name: String, vararg values: Int) {
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.OTHER, PGL.INT,
                values.size)
        attrib?.set(values)
    }

    override fun attrib(name: String, vararg values: Boolean) {
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.OTHER, PGL.BOOL,
                values.size)
        attrib?.set(values)
    }

    private fun attribImpl(name: String?, kind: Int, type: Int, size: Int): PGraphicsOpenGL.VertexAttribute? {
        if (4 < size) {
            PGraphics.showWarning("Vertex attributes cannot have more than 4 values")
            return null
        }
        var attrib = polyAttribs!![name]
        if (attrib == null) {
            attrib = PGraphicsOpenGL.VertexAttribute(pg, name, kind, type, size)
            polyAttribs!![name] = attrib
            inGeo!!.initAttrib(attrib)
        }
        if (attrib.kind != kind) {
            PGraphics.showWarning("The attribute kind cannot be changed after creation")
            return null
        }
        if (attrib.type != type) {
            PGraphics.showWarning("The attribute type cannot be changed after creation")
            return null
        }
        if (attrib.size != size) {
            PGraphics.showWarning("New value for vertex attribute has wrong number of values")
            return null
        }
        return attrib
    }

    override fun endShape(mode: Int) {
        super.endShape(mode)

        // Input arrays are trimmed since they are expanded by doubling their old
        // size, which might lead to arrays larger than the vertex counts.
        inGeo!!.trim()
        close = mode == PConstants.CLOSE
        markForTessellation()
        shapeCreated = true
    }

    public override fun setParams(source: FloatArray) {
        if (family != PRIMITIVE) {
            PGraphics.showWarning("Parameters can only be set to PRIMITIVE shapes")
            return
        }
        super.setParams(source)
        markForTessellation()
        shapeCreated = true
    }

    public override fun setPath(vcount: Int, verts: Array<FloatArray>, ccount: Int, codes: IntArray) {
        if (family != PATH) {
            PGraphics.showWarning("Vertex coordinates and codes can only be set to " +
                    "PATH shapes")
            return
        }
        super.setPath(vcount, verts, ccount, codes)
        markForTessellation()
        shapeCreated = true
    }

    ///////////////////////////////////////////////////////////

    //

    // Geometric transformations

    override fun translate(tx: Float, ty: Float) {
        if (is3D) {
            transform(TRANSLATE, tx, ty, 0f)
        } else {
            transform(TRANSLATE, tx, ty)
        }
    }

    override fun translate(tx: Float, ty: Float, tz: Float) {
        transform(TRANSLATE, tx, ty, tz)
    }

    override fun rotate(angle: Float) {
        transform(ROTATE, angle)
    }

    override fun rotateX(angle: Float) {
        rotate(angle, 1f, 0f, 0f)
    }

    override fun rotateY(angle: Float) {
        rotate(angle, 0f, 1f, 0f)
    }

    override fun rotateZ(angle: Float) {
        transform(ROTATE, angle)
    }

    override fun rotate(angle: Float, v0: Float, v1: Float, v2: Float) {
        transform(ROTATE, angle, v0, v1, v2)
    }

    override fun scale(s: Float) {
        if (is3D) {
            transform(SCALE, s, s, s)
        } else {
            transform(SCALE, s, s)
        }
    }

    override fun scale(x: Float, y: Float) {
        if (is3D) {
            transform(SCALE, x, y, 1f)
        } else {
            transform(SCALE, x, y)
        }
    }

    override fun scale(x: Float, y: Float, z: Float) {
        transform(SCALE, x, y, z)
    }

    override fun applyMatrix(source: PMatrix2D) {
        transform(MATRIX, source.m00, source.m01, source.m02,
                source.m10, source.m11, source.m12)
    }

    override fun applyMatrix(n00: Float, n01: Float, n02: Float,
                             n10: Float, n11: Float, n12: Float) {
        transform(MATRIX, n00, n01, n02,
                n10, n11, n12)
    }

    override fun applyMatrix(n00: Float, n01: Float, n02: Float, n03: Float,
                             n10: Float, n11: Float, n12: Float, n13: Float,
                             n20: Float, n21: Float, n22: Float, n23: Float,
                             n30: Float, n31: Float, n32: Float, n33: Float) {
        transform(MATRIX, n00, n01, n02, n03,
                n10, n11, n12, n13,
                n20, n21, n22, n23,
                n30, n31, n32, n33)
    }

    override fun resetMatrix() {
        if (shapeCreated && matrix != null && matrixInv != null) {
            if (family == PConstants.GROUP) {
                updateTessellation()
            }
            if (tessellated) {
                applyMatrixImpl(matrixInv)
            }
            matrix.reset()
            matrixInv!!.reset()
        }
    }

    protected fun transform(type: Int, vararg args: Float) {
        val dimensions = if (is3D) 3 else 2
        var invertible = true
        checkMatrix(dimensions)
        if (transform == null) {
            if (dimensions == 2) {
                transform = PMatrix2D()
                transformInv = PMatrix2D()
            } else {
                transform = PMatrix3D()
                transformInv = PMatrix3D()
            }
        } else {
            transform!!.reset()
            transformInv!!.reset()
        }
        var ncoords = args.size
        if (type == ROTATE) {
            ncoords = if (args.size == 1) 2 else 3
        } else if (type == MATRIX) {
            ncoords = if (args.size == 6) 2 else 3
        }
        when (type) {
            TRANSLATE -> if (ncoords == 3) {
                transform!!.translate(args[0], args[1], args[2])
                PGraphicsOpenGL.invTranslate(transformInv as PMatrix3D?, args[0], args[1], args[2])
            } else {
                transform!!.translate(args[0], args[1])
                PGraphicsOpenGL.invTranslate(transformInv as PMatrix2D?, args[0], args[1])
            }
            ROTATE -> if (ncoords == 3) {
                transform!!.rotate(args[0], args[1], args[2], args[3])
                PGraphicsOpenGL.invRotate(transformInv as PMatrix3D?, args[0], args[1], args[2], args[3])
            } else {
                transform!!.rotate(args[0])
                PGraphicsOpenGL.invRotate(transformInv as PMatrix2D?, -args[0])
            }
            SCALE -> if (ncoords == 3) {
                transform!!.scale(args[0], args[1], args[2])
                PGraphicsOpenGL.invScale(transformInv as PMatrix3D?, args[0], args[1], args[2])
            } else {
                transform!!.scale(args[0], args[1])
                PGraphicsOpenGL.invScale(transformInv as PMatrix2D?, args[0], args[1])
            }
            MATRIX -> {
                if (ncoords == 3) {
                    transform!![args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8], args[9], args[10], args[11], args[12], args[13], args[14]] = args[15]
                } else {
                    transform!![args[0], args[1], args[2], args[3], args[4]] = args[5]
                }
                transformInv!!.set(transform)
                invertible = transformInv!!.invert()
            }
        }
        matrix.preApply(transform)
        if (invertible) {
            matrixInv!!.apply(transformInv)
        } else {
            PGraphics.showWarning("Transformation applied on the shape cannot be inverted")
        }
        if (tessellated) applyMatrixImpl(transform)
    }

    protected fun applyMatrixImpl(matrix: PMatrix?) {
        if (hasPolys) {
            tessGeo!!.applyMatrixOnPolyGeometry(matrix,
                    firstPolyVertex, lastPolyVertex)
            root!!.setModifiedPolyVertices(firstPolyVertex, lastPolyVertex)
            root!!.setModifiedPolyNormals(firstPolyVertex, lastPolyVertex)
            for (attrib in polyAttribs!!.values) {
                if (attrib.isPosition || attrib.isNormal) {
                    root!!.setModifiedPolyAttrib(attrib, firstPolyVertex, lastPolyVertex)
                }
            }
        }
        if (is3D()) {
            if (hasLines) {
                tessGeo!!.applyMatrixOnLineGeometry(matrix,
                        firstLineVertex, lastLineVertex)
                root!!.setModifiedLineVertices(firstLineVertex, lastLineVertex)
                root!!.setModifiedLineAttributes(firstLineVertex, lastLineVertex)
            }
            if (hasPoints) {
                tessGeo!!.applyMatrixOnPointGeometry(matrix,
                        firstPointVertex, lastPointVertex)
                root!!.setModifiedPointVertices(firstPointVertex, lastPointVertex)
                root!!.setModifiedPointAttributes(firstPointVertex, lastPointVertex)
            }
        }
    }

    override fun checkMatrix(dimensions: Int) {
        if (matrix == null) {
            if (dimensions == 2) {
                matrix = PMatrix2D()
                matrixInv = PMatrix2D()
            } else {
                matrix = PMatrix3D()
                matrixInv = PMatrix3D()
            }
        } else if (dimensions == 3 && matrix is PMatrix2D) {
            matrix = PMatrix3D(matrix)
            matrixInv = PMatrix3D(matrixInv)
        }
    }

    ///////////////////////////////////////////////////////////

    //

    // Bezier curves

    override fun bezierDetail(detail: Int) {
        bezierDetail = detail
        if (0 < inGeo!!.codeCount) {
            markForTessellation()
        }
        //pg.bezierDetail(detail); // setting the detail in the renderer, WTF??
    }

    override fun bezierVertex(x2: Float, y2: Float,
                              x3: Float, y3: Float,
                              x4: Float, y4: Float) {
        bezierVertexImpl(x2, y2, 0f,
                x3, y3, 0f,
                x4, y4, 0f)
    }

    override fun bezierVertex(x2: Float, y2: Float, z2: Float,
                              x3: Float, y3: Float, z3: Float,
                              x4: Float, y4: Float, z4: Float) {
        bezierVertexImpl(x2, y2, z2,
                x3, y3, z3,
                x4, y4, z4)
    }

    protected fun bezierVertexImpl(x2: Float, y2: Float, z2: Float,
                                   x3: Float, y3: Float, z3: Float,
                                   x4: Float, y4: Float, z4: Float) {
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addBezierVertex(x2, y2, z2,
                x3, y3, z3,
                x4, y4, z4, vertexBreak())
    }

    override fun quadraticVertex(cx: Float, cy: Float,
                                 x3: Float, y3: Float) {
        quadraticVertexImpl(cx, cy, 0f,
                x3, y3, 0f)
    }

    override fun quadraticVertex(cx: Float, cy: Float, cz: Float,
                                 x3: Float, y3: Float, z3: Float) {
        quadraticVertexImpl(cx, cy, cz,
                x3, y3, z3)
    }

    protected fun quadraticVertexImpl(cx: Float, cy: Float, cz: Float,
                                      x3: Float, y3: Float, z3: Float) {
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addQuadraticVertex(cx, cy, cz,
                x3, y3, z3, vertexBreak())
    }

    ///////////////////////////////////////////////////////////

    //

    // Catmull-Rom curves

    override fun curveDetail(detail: Int) {
        curveDetail = detail
        //    pg.curveDetail(detail);
        if (0 < inGeo!!.codeCount) {
            markForTessellation()
        }
    }

    override fun curveTightness(tightness: Float) {
        curveTightness = tightness
        //    pg.curveTightness(tightness);
        if (0 < inGeo!!.codeCount) {
            markForTessellation()
        }
    }

    override fun curveVertex(x: Float, y: Float) {
        curveVertexImpl(x, y, 0f)
    }

    override fun curveVertex(x: Float, y: Float, z: Float) {
        curveVertexImpl(x, y, z)
    }

    protected fun curveVertexImpl(x: Float, y: Float, z: Float) {
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addCurveVertex(x, y, z, vertexBreak())
    }

    ///////////////////////////////////////////////////////////

    //

    // Setters/getters of individual vertices

    override fun getVertexCount(): Int {
        return if (family == PConstants.GROUP) 0 // Group shapes don't have vertices
        else {
            if (family == PRIMITIVE || family == PATH) {
                // the input geometry of primitive and path shapes is built during
                // tessellation
                updateTessellation()
            }
            inGeo!!.vertexCount
        }
    }

    override fun getVertex(index: Int, vec: PVector): PVector {
        var vec: PVector? = vec
        if (vec == null) {
            vec = PVector()
        }
        vec.x = inGeo!!.vertices[3 * index + 0]
        vec.y = inGeo!!.vertices[3 * index + 1]
        vec.z = inGeo!!.vertices[3 * index + 2]
        return vec
    }

    override fun getVertexX(index: Int): Float {
        return inGeo!!.vertices[3 * index + 0]
    }

    override fun getVertexY(index: Int): Float {
        return inGeo!!.vertices[3 * index + 1]
    }

    override fun getVertexZ(index: Int): Float {
        return inGeo!!.vertices[3 * index + 2]
    }

    override fun setVertex(index: Int, x: Float, y: Float) {
        setVertex(index, x, y, 0f)
    }

    override fun setVertex(index: Int, x: Float, y: Float, z: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setVertex()")
            return
        }

        // TODO: in certain cases (kind = TRIANGLE, etc) the correspondence between
        // input and tessellated vertices is 1-1, so in those cases re-tessellation
        // wouldn't be necessary. But in order to reasonable take care of that
        // situation, we would need a complete rethinking of the rendering architecture
        // in Processing :-)
        if (family == PATH) {
            if (vertexCodes != null && vertexCodeCount > 0 && vertexCodes[index] != PConstants.VERTEX) {
                PGraphics.showWarning(NOT_A_SIMPLE_VERTEX, "setVertex()")
                return
            }
            vertices[index][PConstants.X] = x
            vertices[index][PConstants.Y] = y
            if (is3D && vertices[index].size > 2) {
                // P3D allows to modify 2D shapes, ignoring the Z coordinate.
                vertices[index][PConstants.Z] = z
            }
        } else {
            inGeo!!.vertices[3 * index + 0] = x
            inGeo!!.vertices[3 * index + 1] = y
            inGeo!!.vertices[3 * index + 2] = z
        }
        markForTessellation()
    }

    override fun setVertex(index: Int, vec: PVector) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setVertex()")
            return
        }
        if (family == PATH) {
            if (vertexCodes != null && vertexCodeCount > 0 && vertexCodes[index] != PConstants.VERTEX) {
                PGraphics.showWarning(NOT_A_SIMPLE_VERTEX, "setVertex()")
                return
            }
            vertices[index][PConstants.X] = vec.x
            vertices[index][PConstants.Y] = vec.y
            if (is3D && vertices[index].size > 2) {
                vertices[index][PConstants.Z] = vec.z
            }
        } else {
            inGeo!!.vertices[3 * index + 0] = vec.x
            inGeo!!.vertices[3 * index + 1] = vec.y
            inGeo!!.vertices[3 * index + 2] = vec.z
        }
        markForTessellation()
    }

    override fun getNormal(index: Int, vec: PVector): PVector {
        var vec: PVector? = vec
        if (vec == null) {
            vec = PVector()
        }
        vec.x = inGeo!!.normals[3 * index + 0]
        vec.y = inGeo!!.normals[3 * index + 1]
        vec.z = inGeo!!.normals[3 * index + 2]
        return vec
    }

    override fun getNormalX(index: Int): Float {
        return inGeo!!.normals[3 * index + 0]
    }

    override fun getNormalY(index: Int): Float {
        return inGeo!!.normals[3 * index + 1]
    }

    override fun getNormalZ(index: Int): Float {
        return inGeo!!.normals[3 * index + 2]
    }

    override fun setNormal(index: Int, nx: Float, ny: Float, nz: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setNormal()")
            return
        }
        inGeo!!.normals[3 * index + 0] = nx
        inGeo!!.normals[3 * index + 1] = ny
        inGeo!!.normals[3 * index + 2] = nz
        markForTessellation()
    }

    override fun setAttrib(name: String, index: Int, vararg values: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setNormal()")
            return
        }
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.OTHER, PGL.FLOAT,
                values.size)
        val array = inGeo!!.fattribs[name]
        for (i in 0 until values.size) {
            array!![attrib!!.size * index + i] = values[i]
        }
        markForTessellation()
    }

    override fun setAttrib(name: String, index: Int, vararg values: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setNormal()")
            return
        }
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.OTHER, PGL.INT,
                values.size)
        val array = inGeo!!.iattribs[name]
        for (i in 0 until values.size) {
            array!![attrib!!.size * index + i] = values[i]
        }
        markForTessellation()
    }

    override fun setAttrib(name: String, index: Int, vararg values: Boolean) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setNormal()")
            return
        }
        val attrib = attribImpl(name, PGraphicsOpenGL.VertexAttribute.OTHER, PGL.BOOL,
                values.size)
        val array = inGeo!!.battribs[name]
        for (i in 0 until values.size) {
            array!![attrib!!.size * index + i] = (if (values[i]) 1 else 0).toByte()
        }
        markForTessellation()
    }

    override fun getTextureU(index: Int): Float {
        return inGeo!!.texcoords[2 * index + 0]
    }

    override fun getTextureV(index: Int): Float {
        return inGeo!!.texcoords[2 * index + 1]
    }

    override fun setTextureUV(index: Int, u: Float, v: Float) {
        var u = u
        var v = v
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setTextureUV()")
            return
        }
        if (textureMode == PConstants.IMAGE && image != null) {
            u /= image.width.toFloat()
            v /= image.height.toFloat()
        }
        inGeo!!.texcoords[2 * index + 0] = u
        inGeo!!.texcoords[2 * index + 1] = v
        markForTessellation()
    }

    override fun getFill(index: Int): Int {
        return if (family != PConstants.GROUP && image == null) {
            PGL.nativeToJavaARGB(inGeo!!.colors[index])
        } else {
            0
        }
    }

    override fun setFill(fill: Boolean) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setFill()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setFill(fill)
            }
        } else if (this.fill != fill) {
            markForTessellation()
        }
        this.fill = fill
    }

    override fun setFill(fill: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setFill()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setFill(fill)
            }
        } else {
            setFillImpl(fill)
        }
    }

    protected fun setFillImpl(fill: Int) {
        if (fillColor == fill) return
        fillColor = fill
        if (image == null) {
            Arrays.fill(inGeo!!.colors, 0, inGeo!!.vertexCount,
                    PGL.javaToNativeARGB(fillColor))
            if (shapeCreated && tessellated && hasPolys) {
                if (is3D()) {
                    Arrays.fill(tessGeo!!.polyColors, firstPolyVertex, lastPolyVertex + 1,
                            PGL.javaToNativeARGB(fillColor))
                    root!!.setModifiedPolyColors(firstPolyVertex, lastPolyVertex)
                } else if (is2D) {
                    var last1 = lastPolyVertex + 1
                    if (-1 < firstLineVertex) last1 = firstLineVertex
                    if (-1 < firstPointVertex) last1 = firstPointVertex
                    Arrays.fill(tessGeo!!.polyColors, firstPolyVertex, last1,
                            PGL.javaToNativeARGB(fillColor))
                    root!!.setModifiedPolyColors(firstPolyVertex, last1 - 1)
                }
            }
        }
        if (!setAmbient) {
            // Setting the ambient color from the current fill
            // is what the old P3D did and allows to have an
            // default ambient color when the user doesn't specify
            // it explicitly.
            setAmbientImpl(fill)
            setAmbient = false
        }
    }

    override fun setFill(index: Int, fill: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setFill()")
            return
        }
        if (image == null) {
            inGeo!!.colors[index] = PGL.javaToNativeARGB(fill)
            markForTessellation()
        }
    }

    override fun getTint(index: Int): Int {
        return if (family != PConstants.GROUP && image != null) {
            PGL.nativeToJavaARGB(inGeo!!.colors[index])
        } else {
            0
        }
    }

    override fun setTint(tint: Boolean) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setTint()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setTint(fill)
            }
        } else if (this.tint && !tint) {
            setTintImpl(-0x1)
        }
        this.tint = tint
    }

    override fun setTint(tint: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setTint()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setTint(tint)
            }
        } else {
            setTintImpl(tint)
        }
    }

    protected fun setTintImpl(tint: Int) {
        if (tintColor == tint) return
        tintColor = tint
        if (image != null) {
            Arrays.fill(inGeo!!.colors, 0, inGeo!!.vertexCount,
                    PGL.javaToNativeARGB(tintColor))
            if (shapeCreated && tessellated && hasPolys) {
                if (is3D()) {
                    Arrays.fill(tessGeo!!.polyColors, firstPolyVertex, lastPolyVertex + 1,
                            PGL.javaToNativeARGB(tintColor))
                    root!!.setModifiedPolyColors(firstPolyVertex, lastPolyVertex)
                } else if (is2D) {
                    var last1 = lastPolyVertex + 1
                    if (-1 < firstLineVertex) last1 = firstLineVertex
                    if (-1 < firstPointVertex) last1 = firstPointVertex
                    Arrays.fill(tessGeo!!.polyColors, firstPolyVertex, last1,
                            PGL.javaToNativeARGB(tintColor))
                    root!!.setModifiedPolyColors(firstPolyVertex, last1 - 1)
                }
            }
        }
    }

    override fun setTint(index: Int, tint: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setTint()")
            return
        }
        if (image != null) {
            inGeo!!.colors[index] = PGL.javaToNativeARGB(tint)
            markForTessellation()
        }
    }

    override fun getStroke(index: Int): Int {
        return if (family != PConstants.GROUP) {
            PGL.nativeToJavaARGB(inGeo!!.strokeColors[index])
        } else {
            0
        }
    }

    override fun setStroke(stroke: Boolean) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStroke()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setStroke(stroke)
            }
            this.stroke = stroke
        } else {
            setStrokeImpl(stroke)
        }
    }

    protected fun setStrokeImpl(stroke: Boolean) {
        if (this.stroke != stroke) {
            if (stroke) {
                // Before there was no stroke, now there is stroke, so current stroke
                // color should be copied to the input geometry, and geometry should
                // be marked as modified in case it needs to be re-tessellated.
                val color = strokeColor
                strokeColor += 1 // Forces a color change
                setStrokeImpl(color)
            }
            markForTessellation()
            if (is2D && parent != null) {
                (parent as PShapeOpenGL).strokedTexture(stroke && image != null)
            }
            this.stroke = stroke
        }
    }

    override fun setStroke(stroke: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStroke()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setStroke(stroke)
            }
        } else {
            setStrokeImpl(stroke)
        }
    }

    protected fun setStrokeImpl(stroke: Int) {
        if (strokeColor == stroke) return
        strokeColor = stroke
        Arrays.fill(inGeo!!.strokeColors, 0, inGeo!!.vertexCount,
                PGL.javaToNativeARGB(strokeColor))
        if (shapeCreated && tessellated && (hasLines || hasPoints)) {
            if (hasLines) {
                if (is3D()) {
                    Arrays.fill(tessGeo!!.lineColors, firstLineVertex, lastLineVertex + 1,
                            PGL.javaToNativeARGB(strokeColor))
                    root!!.setModifiedLineColors(firstLineVertex, lastLineVertex)
                } else if (is2D) {
                    Arrays.fill(tessGeo!!.polyColors, firstLineVertex, lastLineVertex + 1,
                            PGL.javaToNativeARGB(strokeColor))
                    root!!.setModifiedPolyColors(firstLineVertex, lastLineVertex)
                }
            }
            if (hasPoints) {
                if (is3D()) {
                    Arrays.fill(tessGeo!!.pointColors, firstPointVertex, lastPointVertex + 1,
                            PGL.javaToNativeARGB(strokeColor))
                    root!!.setModifiedPointColors(firstPointVertex, lastPointVertex)
                } else if (is2D) {
                    Arrays.fill(tessGeo!!.polyColors, firstPointVertex, lastPointVertex + 1,
                            PGL.javaToNativeARGB(strokeColor))
                    root!!.setModifiedPolyColors(firstPointVertex, lastPointVertex)
                }
            }
        }
    }

    override fun setStroke(index: Int, stroke: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStroke()")
            return
        }
        inGeo!!.strokeColors[index] = PGL.javaToNativeARGB(stroke)
        markForTessellation()
    }

    override fun getStrokeWeight(index: Int): Float {
        return if (family != PConstants.GROUP) {
            inGeo!!.strokeWeights[index]
        } else {
            0F
        }
    }

    override fun setStrokeWeight(weight: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStrokeWeight()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setStrokeWeight(weight)
            }
        } else {
            setStrokeWeightImpl(weight)
        }
    }

    protected fun setStrokeWeightImpl(weight: Float) {
        if (PGraphicsOpenGL.same(strokeWeight, weight)) return
        val oldWeight = strokeWeight
        strokeWeight = weight
        Arrays.fill(inGeo!!.strokeWeights, 0, inGeo!!.vertexCount, strokeWeight)
        if (shapeCreated && tessellated && (hasLines || hasPoints)) {
            val resizeFactor = weight / oldWeight
            if (hasLines) {
                if (is3D()) {
                    for (i in firstLineVertex..lastLineVertex) {
                        tessGeo!!.lineDirections[4 * i + 3] *= resizeFactor
                    }
                    root!!.setModifiedLineAttributes(firstLineVertex, lastLineVertex)
                } else if (is2D) {
                    // Changing the stroke weight on a 2D shape needs a
                    // re-tessellation in order to replace the old line
                    // geometry.
                    markForTessellation()
                }
            }
            if (hasPoints) {
                if (is3D()) {
                    for (i in firstPointVertex..lastPointVertex) {
                        tessGeo!!.pointOffsets[2 * i + 0] *= resizeFactor
                        tessGeo!!.pointOffsets[2 * i + 1] *= resizeFactor
                    }
                    root!!.setModifiedPointAttributes(firstPointVertex, lastPointVertex)
                } else if (is2D) {
                    // Changing the stroke weight on a 2D shape needs a
                    // re-tessellation in order to replace the old point
                    // geometry.
                    markForTessellation()
                }
            }
        }
    }

    override fun setStrokeWeight(index: Int, weight: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStrokeWeight()")
            return
        }
        inGeo!!.strokeWeights[index] = weight
        markForTessellation()
    }

    override fun setStrokeJoin(join: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStrokeJoin()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setStrokeJoin(join)
            }
        } else {
            if (is2D && strokeJoin != join) {
                // Changing the stroke join on a 2D shape needs a
                // re-tessellation in order to replace the old join
                // geometry.
                markForTessellation()
            }
            strokeJoin = join
        }
    }

    override fun setStrokeCap(cap: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setStrokeCap()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setStrokeCap(cap)
            }
        } else {
            if (is2D && strokeCap != cap) {
                // Changing the stroke cap on a 2D shape needs a
                // re-tessellation in order to replace the old cap
                // geometry.
                markForTessellation()
            }
            strokeCap = cap
        }
    }

    override fun getAmbient(index: Int): Int {
        return if (family != PConstants.GROUP) {
            PGL.nativeToJavaARGB(inGeo!!.ambient[index])
        } else {
            0
        }
    }

    override fun setAmbient(ambient: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setAmbient()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setAmbient(ambient)
            }
        } else {
            setAmbientImpl(ambient)
        }
    }

    protected fun setAmbientImpl(ambient: Int) {
        if (ambientColor == ambient) return
        ambientColor = ambient
        Arrays.fill(inGeo!!.ambient, 0, inGeo!!.vertexCount,
                PGL.javaToNativeARGB(ambientColor))
        if (shapeCreated && tessellated && hasPolys) {
            if (is3D()) {
                Arrays.fill(tessGeo!!.polyAmbient, firstPolyVertex, lastPolyVertex + 1,
                        PGL.javaToNativeARGB(ambientColor))
                root!!.setModifiedPolyAmbient(firstPolyVertex, lastPolyVertex)
            } else if (is2D) {
                var last1 = lastPolyVertex + 1
                if (-1 < firstLineVertex) last1 = firstLineVertex
                if (-1 < firstPointVertex) last1 = firstPointVertex
                Arrays.fill(tessGeo!!.polyAmbient, firstPolyVertex, last1,
                        PGL.javaToNativeARGB(ambientColor))
                root!!.setModifiedPolyColors(firstPolyVertex, last1 - 1)
            }
        }
        setAmbient = true
    }

    override fun setAmbient(index: Int, ambient: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setAmbient()")
            return
        }
        inGeo!!.ambient[index] = PGL.javaToNativeARGB(ambient)
        markForTessellation()
        setAmbient = true
    }

    override fun getSpecular(index: Int): Int {
        return if (family == PConstants.GROUP) {
            PGL.nativeToJavaARGB(inGeo!!.specular[index])
        } else {
            0
        }
    }

    override fun setSpecular(specular: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setSpecular()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setSpecular(specular)
            }
        } else {
            setSpecularImpl(specular)
        }
    }

    protected fun setSpecularImpl(specular: Int) {
        if (specularColor == specular) return
        specularColor = specular
        Arrays.fill(inGeo!!.specular, 0, inGeo!!.vertexCount,
                PGL.javaToNativeARGB(specularColor))
        if (shapeCreated && tessellated && hasPolys) {
            if (is3D()) {
                Arrays.fill(tessGeo!!.polySpecular, firstPolyVertex, lastPolyVertex + 1,
                        PGL.javaToNativeARGB(specularColor))
                root!!.setModifiedPolySpecular(firstPolyVertex, lastPolyVertex)
            } else if (is2D) {
                var last1 = lastPolyVertex + 1
                if (-1 < firstLineVertex) last1 = firstLineVertex
                if (-1 < firstPointVertex) last1 = firstPointVertex
                Arrays.fill(tessGeo!!.polySpecular, firstPolyVertex, last1,
                        PGL.javaToNativeARGB(specularColor))
                root!!.setModifiedPolyColors(firstPolyVertex, last1 - 1)
            }
        }
    }

    override fun setSpecular(index: Int, specular: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setSpecular()")
            return
        }
        inGeo!!.specular[index] = PGL.javaToNativeARGB(specular)
        markForTessellation()
    }

    override fun getEmissive(index: Int): Int {
        return if (family == PConstants.GROUP) {
            PGL.nativeToJavaARGB(inGeo!!.emissive[index])
        } else {
            0
        }
    }

    override fun setEmissive(emissive: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setEmissive()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setEmissive(emissive)
            }
        } else {
            setEmissiveImpl(emissive)
        }
    }

    protected fun setEmissiveImpl(emissive: Int) {
        if (emissiveColor == emissive) return
        emissiveColor = emissive
        Arrays.fill(inGeo!!.emissive, 0, inGeo!!.vertexCount,
                PGL.javaToNativeARGB(emissiveColor))
        if (shapeCreated && tessellated && 0 < tessGeo!!.polyVertexCount) {
            if (is3D()) {
                Arrays.fill(tessGeo!!.polyEmissive, firstPolyVertex, lastPolyVertex + 1,
                        PGL.javaToNativeARGB(emissiveColor))
                root!!.setModifiedPolyEmissive(firstPolyVertex, lastPolyVertex)
            } else if (is2D) {
                var last1 = lastPolyVertex + 1
                if (-1 < firstLineVertex) last1 = firstLineVertex
                if (-1 < firstPointVertex) last1 = firstPointVertex
                Arrays.fill(tessGeo!!.polyEmissive, firstPolyVertex, last1,
                        PGL.javaToNativeARGB(emissiveColor))
                root!!.setModifiedPolyColors(firstPolyVertex, last1 - 1)
            }
        }
    }

    override fun setEmissive(index: Int, emissive: Int) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setEmissive()")
            return
        }
        inGeo!!.emissive[index] = PGL.javaToNativeARGB(emissive)
        markForTessellation()
    }

    override fun getShininess(index: Int): Float {
        return if (family == PConstants.GROUP) {
            inGeo!!.shininess[index]
        } else {
            0F
        }
    }

    override fun setShininess(shininess: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setShininess()")
            return
        }
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.setShininess(shininess)
            }
        } else {
            setShininessImpl(shininess)
        }
    }

    protected fun setShininessImpl(shininess: Float) {
        if (PGraphicsOpenGL.same(this.shininess, shininess)) return
        this.shininess = shininess
        Arrays.fill(inGeo!!.shininess, 0, inGeo!!.vertexCount, shininess)
        if (shapeCreated && tessellated && hasPolys) {
            if (is3D()) {
                Arrays.fill(tessGeo!!.polyShininess, firstPolyVertex, lastPolyVertex + 1,
                        shininess)
                root!!.setModifiedPolyShininess(firstPolyVertex, lastPolyVertex)
            } else if (is2D) {
                var last1 = lastPolyVertex + 1
                if (-1 < firstLineVertex) last1 = firstLineVertex
                if (-1 < firstPointVertex) last1 = firstPointVertex
                Arrays.fill(tessGeo!!.polyShininess, firstPolyVertex, last1, shininess)
                root!!.setModifiedPolyColors(firstPolyVertex, last1 - 1)
            }
        }
    }

    override fun setShininess(index: Int, shine: Float) {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "setShininess()")
            return
        }
        inGeo!!.shininess[index] = shine
        markForTessellation()
    }

    ///////////////////////////////////////////////////////////

    //

    // Vertex codes

    override fun getVertexCodes(): IntArray? {
        return if (family == PConstants.GROUP) null else {
            if (family == PRIMITIVE || family == PATH) {
                // the input geometry of primitive and path shapes is built during
                // tessellation
                updateTessellation()
            }
            if (inGeo!!.codes == null) null else inGeo!!.codes
        }
    }

    override fun getVertexCodeCount(): Int {
        return if (family == PConstants.GROUP) 0 else {
            if (family == PRIMITIVE || family == PATH) {
                // the input geometry of primitive and path shapes is built during
                // tessellation
                updateTessellation()
            }
            inGeo!!.codeCount
        }
    }

    /**
     * One of VERTEX, BEZIER_VERTEX, CURVE_VERTEX, or BREAK.
     */
    override fun getVertexCode(index: Int): Int {
        return inGeo!!.codes[index]
    }

    ///////////////////////////////////////////////////////////

    //

    // Tessellated geometry getter.

    override fun getTessellation(): PShape {
        updateTessellation()
        val vertices = tessGeo!!.polyVertices
        val normals = tessGeo!!.polyNormals
        val color = tessGeo!!.polyColors
        val uv = tessGeo!!.polyTexCoords
        val indices = tessGeo!!.polyIndices
        val tess: PShape
        //    if (is3D()) {
//      //tess = PGraphics3D.createShapeImpl(pg, PShape.GEOMETRY);
//      tess = pg.createShapeFamily(PShape.GEOMETRY);
//    } else if (is2D()) {
//      //tess = PGraphics2D.createShapeImpl(pg, PShape.GEOMETRY);
//      tess = pg.createShapeFamily(PShape.GEOMETRY);
//    } else {
//      PGraphics.showWarning("This shape is not either 2D or 3D!");
//      return null;
//    }
        tess = pg!!.createShapeFamily(GEOMETRY)
        tess.is3D = is3D // if this is a 3D shape, make the new shape 3D as well
        tess.beginShape(PConstants.TRIANGLES)
        tess.noStroke()
        val cache = tessGeo!!.polyIndexCache
        for (n in firstPolyIndexCache..lastPolyIndexCache) {
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            for (tr in ioffset / 3 until (ioffset + icount) / 3) {
                val i0 = voffset + indices[3 * tr + 0]
                val i1 = voffset + indices[3 * tr + 1]
                val i2 = voffset + indices[3 * tr + 2]
                if (is3D()) {
                    val x0 = vertices[4 * i0 + 0]
                    val y0 = vertices[4 * i0 + 1]
                    val z0 = vertices[4 * i0 + 2]
                    val x1 = vertices[4 * i1 + 0]
                    val y1 = vertices[4 * i1 + 1]
                    val z1 = vertices[4 * i1 + 2]
                    val x2 = vertices[4 * i2 + 0]
                    val y2 = vertices[4 * i2 + 1]
                    val z2 = vertices[4 * i2 + 2]
                    val nx0 = normals[3 * i0 + 0]
                    val ny0 = normals[3 * i0 + 1]
                    val nz0 = normals[3 * i0 + 2]
                    val nx1 = normals[3 * i1 + 0]
                    val ny1 = normals[3 * i1 + 1]
                    val nz1 = normals[3 * i1 + 2]
                    val nx2 = normals[3 * i2 + 0]
                    val ny2 = normals[3 * i2 + 1]
                    val nz2 = normals[3 * i2 + 2]
                    val argb0 = PGL.nativeToJavaARGB(color[i0])
                    val argb1 = PGL.nativeToJavaARGB(color[i1])
                    val argb2 = PGL.nativeToJavaARGB(color[i2])
                    tess.fill(argb0)
                    tess.normal(nx0, ny0, nz0)
                    tess.vertex(x0, y0, z0, uv[2 * i0 + 0], uv[2 * i0 + 1])
                    tess.fill(argb1)
                    tess.normal(nx1, ny1, nz1)
                    tess.vertex(x1, y1, z1, uv[2 * i1 + 0], uv[2 * i1 + 1])
                    tess.fill(argb2)
                    tess.normal(nx2, ny2, nz2)
                    tess.vertex(x2, y2, z2, uv[2 * i2 + 0], uv[2 * i2 + 1])
                } else if (is2D) {
                    val x0 = vertices[4 * i0 + 0]
                    val y0 = vertices[4 * i0 + 1]
                    val x1 = vertices[4 * i1 + 0]
                    val y1 = vertices[4 * i1 + 1]
                    val x2 = vertices[4 * i2 + 0]
                    val y2 = vertices[4 * i2 + 1]
                    val argb0 = PGL.nativeToJavaARGB(color[i0])
                    val argb1 = PGL.nativeToJavaARGB(color[i1])
                    val argb2 = PGL.nativeToJavaARGB(color[i2])
                    tess.fill(argb0)
                    tess.vertex(x0, y0, uv[2 * i0 + 0], uv[2 * i0 + 1])
                    tess.fill(argb1)
                    tess.vertex(x1, y1, uv[2 * i1 + 0], uv[2 * i1 + 1])
                    tess.fill(argb2)
                    tess.vertex(x2, y2, uv[2 * i2 + 0], uv[2 * i2 + 1])
                }
            }
        }
        tess.endShape()
        return tess
    }

    // Testing this method, not use as it might go away...
    fun getTessellation(kind: Int, data: Int): FloatArray? {
        updateTessellation()
        if (kind == PConstants.TRIANGLES) {
            if (data == POSITION) {
                if (is3D()) {
                    root!!.setModifiedPolyVertices(firstPolyVertex, lastPolyVertex)
                } else if (is2D) {
                    var last1 = lastPolyVertex + 1
                    if (-1 < firstLineVertex) last1 = firstLineVertex
                    if (-1 < firstPointVertex) last1 = firstPointVertex
                    root!!.setModifiedPolyVertices(firstPolyVertex, last1 - 1)
                }
                return tessGeo!!.polyVertices
            } else if (data == NORMAL) {
                if (is3D()) {
                    root!!.setModifiedPolyNormals(firstPolyVertex, lastPolyVertex)
                } else if (is2D) {
                    var last1 = lastPolyVertex + 1
                    if (-1 < firstLineVertex) last1 = firstLineVertex
                    if (-1 < firstPointVertex) last1 = firstPointVertex
                    root!!.setModifiedPolyNormals(firstPolyVertex, last1 - 1)
                }
                return tessGeo!!.polyNormals
            } else if (data == TEXCOORD) {
                if (is3D()) {
                    root!!.setModifiedPolyTexCoords(firstPolyVertex, lastPolyVertex)
                } else if (is2D) {
                    var last1 = lastPolyVertex + 1
                    if (-1 < firstLineVertex) last1 = firstLineVertex
                    if (-1 < firstPointVertex) last1 = firstPointVertex
                    root!!.setModifiedPolyTexCoords(firstPolyVertex, last1 - 1)
                }
                return tessGeo!!.polyTexCoords
            }
        } else if (kind == PConstants.LINES) {
            if (data == POSITION) {
                if (is3D()) {
                    root!!.setModifiedLineVertices(firstLineVertex, lastLineVertex)
                } else if (is2D) {
                    root!!.setModifiedPolyVertices(firstLineVertex, lastLineVertex)
                }
                return tessGeo!!.lineVertices
            } else if (data == DIRECTION) {
                if (is2D) {
                    root!!.setModifiedLineAttributes(firstLineVertex, lastLineVertex)
                }
                return tessGeo!!.lineDirections
            }
        } else if (kind == PConstants.POINTS) {
            if (data == POSITION) {
                if (is3D()) {
                    root!!.setModifiedPointVertices(firstPointVertex, lastPointVertex)
                } else if (is2D) {
                    root!!.setModifiedPolyVertices(firstPointVertex, lastPointVertex)
                }
                return tessGeo!!.pointVertices
            } else if (data == OFFSET) {
                if (is2D) {
                    root!!.setModifiedPointAttributes(firstPointVertex, lastPointVertex)
                }
                return tessGeo!!.pointOffsets
            }
        }
        return null
    }

    ///////////////////////////////////////////////////////////

    //

    // Geometry utils
    // http://www.ecse.rpi.edu/Homepages/wrf/Research/Short_Notes/pnpoly.html

    override fun contains(x: Float, y: Float): Boolean {
        return if (family == PATH) {
            var c = false
            var i = 0
            var j = inGeo!!.vertexCount - 1
            while (i < inGeo!!.vertexCount) {
                if (inGeo!!.vertices[3 * i + 1] > y != inGeo!!.vertices[3 * j + 1] > y &&
                        x <
                        (inGeo!!.vertices[3 * j] - inGeo!!.vertices[3 * i]) *
                        (y - inGeo!!.vertices[3 * i + 1]) /
                        (inGeo!!.vertices[3 * j + 1] - inGeo!!.vertices[3 * i + 1]) +
                        inGeo!!.vertices[3 * i]) {
                    c = !c
                }
                j = i++
            }
            c
        } else {
            throw IllegalArgumentException("The contains() method is only implemented for paths.")
        }
    }

    ///////////////////////////////////////////////////////////

    //

    // Tessellation

    protected fun updateTessellation() {
        if (!root!!.tessellated) {
            root!!.tessellate()
            root!!.aggregate()
            root!!.initModified()
            root!!.needBufferInit = true
        }
    }

    protected fun markForTessellation() {
        root!!.tessellated = false
        tessellated = false
    }

    protected fun initModified() {
        modified = false
        modifiedPolyVertices = false
        modifiedPolyColors = false
        modifiedPolyNormals = false
        modifiedPolyTexCoords = false
        modifiedPolyAmbient = false
        modifiedPolySpecular = false
        modifiedPolyEmissive = false
        modifiedPolyShininess = false
        modifiedLineVertices = false
        modifiedLineColors = false
        modifiedLineAttributes = false
        modifiedPointVertices = false
        modifiedPointColors = false
        modifiedPointAttributes = false
        firstModifiedPolyVertex = PConstants.MAX_INT
        lastModifiedPolyVertex = PConstants.MIN_INT
        firstModifiedPolyColor = PConstants.MAX_INT
        lastModifiedPolyColor = PConstants.MIN_INT
        firstModifiedPolyNormal = PConstants.MAX_INT
        lastModifiedPolyNormal = PConstants.MIN_INT
        firstModifiedPolyTexcoord = PConstants.MAX_INT
        lastModifiedPolyTexcoord = PConstants.MIN_INT
        firstModifiedPolyAmbient = PConstants.MAX_INT
        lastModifiedPolyAmbient = PConstants.MIN_INT
        firstModifiedPolySpecular = PConstants.MAX_INT
        lastModifiedPolySpecular = PConstants.MIN_INT
        firstModifiedPolyEmissive = PConstants.MAX_INT
        lastModifiedPolyEmissive = PConstants.MIN_INT
        firstModifiedPolyShininess = PConstants.MAX_INT
        lastModifiedPolyShininess = PConstants.MIN_INT
        firstModifiedLineVertex = PConstants.MAX_INT
        lastModifiedLineVertex = PConstants.MIN_INT
        firstModifiedLineColor = PConstants.MAX_INT
        lastModifiedLineColor = PConstants.MIN_INT
        firstModifiedLineAttribute = PConstants.MAX_INT
        lastModifiedLineAttribute = PConstants.MIN_INT
        firstModifiedPointVertex = PConstants.MAX_INT
        lastModifiedPointVertex = PConstants.MIN_INT
        firstModifiedPointColor = PConstants.MAX_INT
        lastModifiedPointColor = PConstants.MIN_INT
        firstModifiedPointAttribute = PConstants.MAX_INT
        lastModifiedPointAttribute = PConstants.MIN_INT
    }

    protected fun tessellate() {
        if (root === this && parent == null) { // Root shape
            var initAttr = false
            if (polyAttribs == null) {
                polyAttribs = PGraphicsOpenGL.newAttributeMap()
                initAttr = true
            }
            if (tessGeo == null) {
                tessGeo = PGraphicsOpenGL.newTessGeometry(pg, polyAttribs, PGraphicsOpenGL.RETAINED)
            }
            tessGeo!!.clear()
            if (initAttr) {
                collectPolyAttribs()
            }
            for (i in 0 until polyAttribs!!.size) {
                val attrib = polyAttribs!![i]
                tessGeo!!.initAttrib(attrib)
            }
            tessellateImpl()

            // Tessellated arrays are trimmed since they are expanded
            // by doubling their old size, which might lead to arrays
            // larger than the vertex counts.
            tessGeo!!.trim()
        }
    }

    protected fun collectPolyAttribs() {
        val rootAttribs = root!!.polyAttribs
        tessGeo = root!!.tessGeo
        if (family == PConstants.GROUP) {
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.collectPolyAttribs()
            }
        } else {
            for (i in 0 until polyAttribs!!.size) {
                val attrib = polyAttribs!![i]
                tessGeo!!.initAttrib(attrib)
                if (rootAttribs!!.containsKey(attrib!!.name)) {
                    val rattrib = rootAttribs[attrib.name]
                    if (rattrib!!.diff(attrib)) {
                        throw RuntimeException("Children shapes cannot have different attributes with same name")
                    }
                } else {
                    rootAttribs[attrib.name] = attrib
                }
            }
        }
    }

    protected fun tessellateImpl() {
        tessGeo = root!!.tessGeo
        firstPolyIndexCache = -1
        lastPolyIndexCache = -1
        firstLineIndexCache = -1
        lastLineIndexCache = -1
        firstPointIndexCache = -1
        lastPointIndexCache = -1
        if (family == PConstants.GROUP) {
            if (polyAttribs == null) {
                polyAttribs = PGraphicsOpenGL.newAttributeMap()
                collectPolyAttribs()
            }
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.tessellateImpl()
            }
        } else {
            if (shapeCreated) {
                // If the geometry was tessellated previously, then
                // the edges information will still be stored in the
                // input object, so it needs to be removed to avoid
                // duplication.
                inGeo!!.clearEdges()
                tessellator!!.setInGeometry(inGeo)
                tessellator!!.setTessGeometry(tessGeo)
                tessellator!!.setFill(fill || image != null)
                tessellator!!.setTexCache(null, null)
                tessellator!!.setStroke(stroke)
                tessellator!!.setStrokeColor(strokeColor)
                tessellator!!.setStrokeWeight(strokeWeight)
                tessellator!!.setStrokeCap(strokeCap)
                tessellator!!.setStrokeJoin(strokeJoin)
                tessellator!!.setRenderer(pg)
                tessellator!!.setTransform(matrix)
                tessellator!!.set3D(is3D())
                if (family == GEOMETRY) {
                    if (kind == PConstants.POINTS) {
                        tessellator!!.tessellatePoints()
                    } else if (kind == PConstants.LINES) {
                        tessellator!!.tessellateLines()
                    } else if (kind == PConstants.LINE_STRIP) {
                        tessellator!!.tessellateLineStrip()
                    } else if (kind == PConstants.LINE_LOOP) {
                        tessellator!!.tessellateLineLoop()
                    } else if (kind == PConstants.TRIANGLE || kind == PConstants.TRIANGLES) {
                        if (stroke) inGeo!!.addTrianglesEdges()
                        if (normalMode == NORMAL_MODE_AUTO) inGeo!!.calcTrianglesNormals()
                        tessellator!!.tessellateTriangles()
                    } else if (kind == PConstants.TRIANGLE_FAN) {
                        if (stroke) inGeo!!.addTriangleFanEdges()
                        if (normalMode == NORMAL_MODE_AUTO) inGeo!!.calcTriangleFanNormals()
                        tessellator!!.tessellateTriangleFan()
                    } else if (kind == PConstants.TRIANGLE_STRIP) {
                        if (stroke) inGeo!!.addTriangleStripEdges()
                        if (normalMode == NORMAL_MODE_AUTO) inGeo!!.calcTriangleStripNormals()
                        tessellator!!.tessellateTriangleStrip()
                    } else if (kind == PConstants.QUAD || kind == PConstants.QUADS) {
                        if (stroke) inGeo!!.addQuadsEdges()
                        if (normalMode == NORMAL_MODE_AUTO) inGeo!!.calcQuadsNormals()
                        tessellator!!.tessellateQuads()
                    } else if (kind == PConstants.QUAD_STRIP) {
                        if (stroke) inGeo!!.addQuadStripEdges()
                        if (normalMode == NORMAL_MODE_AUTO) inGeo!!.calcQuadStripNormals()
                        tessellator!!.tessellateQuadStrip()
                    } else if (kind == PConstants.POLYGON) {
                        val bez = inGeo!!.hasBezierVertex()
                        val quad = inGeo!!.hasQuadraticVertex()
                        val curv = inGeo!!.hasCurveVertex()
                        if (bez || quad) saveBezierVertexSettings()
                        if (curv) {
                            saveCurveVertexSettings()
                            tessellator!!.resetCurveVertexCount()
                        }
                        tessellator!!.tessellatePolygon(solid, close,
                                normalMode == NORMAL_MODE_AUTO)
                        if (bez || quad) restoreBezierVertexSettings()
                        if (curv) restoreCurveVertexSettings()
                    }
                } else if (family == PRIMITIVE) {
                    // The input geometry needs to be cleared because the geometry
                    // generation methods in InGeometry add the vertices of the
                    // new primitive to what is already stored.
                    inGeo!!.clear()
                    if (kind == PConstants.POINT) {
                        tessellatePoint()
                    } else if (kind == PConstants.LINE) {
                        tessellateLine()
                    } else if (kind == PConstants.TRIANGLE) {
                        tessellateTriangle()
                    } else if (kind == PConstants.QUAD) {
                        tessellateQuad()
                    } else if (kind == PConstants.RECT) {
                        tessellateRect()
                    } else if (kind == PConstants.ELLIPSE) {
                        tessellateEllipse()
                    } else if (kind == PConstants.ARC) {
                        tessellateArc()
                    } else if (kind == PConstants.BOX) {
                        tessellateBox()
                    } else if (kind == PConstants.SPHERE) {
                        tessellateSphere()
                    }
                } else if (family == PATH) {
                    inGeo!!.clear()
                    tessellatePath()
                }
                if (image != null && parent != null) {
                    (parent as PShapeOpenGL).addTexture(image)
                }
                firstPolyIndexCache = tessellator!!.firstPolyIndexCache
                lastPolyIndexCache = tessellator!!.lastPolyIndexCache
                firstLineIndexCache = tessellator!!.firstLineIndexCache
                lastLineIndexCache = tessellator!!.lastLineIndexCache
                firstPointIndexCache = tessellator!!.firstPointIndexCache
                lastPointIndexCache = tessellator!!.lastPointIndexCache
            }
        }
        lastPolyVertex = -1
        firstPolyVertex = lastPolyVertex
        lastLineVertex = -1
        firstLineVertex = lastLineVertex
        lastPointVertex = -1
        firstPointVertex = lastPointVertex
        tessellated = true
    }

    protected fun tessellatePoint() {
        var x = 0f
        var y = 0f
        var z = 0f
        if (params.size == 2) {
            x = params[0]
            y = params[1]
            z = 0f
        } else if (params.size == 3) {
            x = params[0]
            y = params[1]
            z = params[2]
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addPoint(x, y, z, fill, stroke)
        tessellator!!.tessellatePoints()
    }

    protected fun tessellateLine() {
        var x1 = 0f
        var y1 = 0f
        var z1 = 0f
        var x2 = 0f
        var y2 = 0f
        var z2 = 0f
        if (params.size == 4) {
            x1 = params[0]
            y1 = params[1]
            x2 = params[2]
            y2 = params[3]
        } else if (params.size == 6) {
            x1 = params[0]
            y1 = params[1]
            z1 = params[2]
            x2 = params[3]
            y2 = params[4]
            z2 = params[5]
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addLine(x1, y1, z1,
                x2, y2, z2,
                fill, stroke)
        tessellator!!.tessellateLines()
    }

    protected fun tessellateTriangle() {
        var x1 = 0f
        var y1 = 0f
        var x2 = 0f
        var y2 = 0f
        var x3 = 0f
        var y3 = 0f
        if (params.size == 6) {
            x1 = params[0]
            y1 = params[1]
            x2 = params[2]
            y2 = params[3]
            x3 = params[4]
            y3 = params[5]
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addTriangle(x1, y1, 0f,
                x2, y2, 0f,
                x3, y3, 0f,
                fill, stroke)
        tessellator!!.tessellateTriangles()
    }

    protected fun tessellateQuad() {
        var x1 = 0f
        var y1 = 0f
        var x2 = 0f
        var y2 = 0f
        var x3 = 0f
        var y3 = 0f
        var x4 = 0f
        var y4 = 0f
        if (params.size == 8) {
            x1 = params[0]
            y1 = params[1]
            x2 = params[2]
            y2 = params[3]
            x3 = params[4]
            y3 = params[5]
            x4 = params[6]
            y4 = params[7]
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addQuad(x1, y1, 0f,
                x2, y2, 0f,
                x3, y3, 0f,
                x4, y4, 0f,
                stroke)
        tessellator!!.tessellateQuads()
    }

    protected fun tessellateRect() {
        var a = 0f
        var b = 0f
        var c = 0f
        var d = 0f
        var tl = 0f
        var tr = 0f
        var br = 0f
        var bl = 0f
        var rounded = false
        val mode = rectMode
        if (params.size == 4 || params.size == 5) {
            a = params[0]
            b = params[1]
            c = params[2]
            d = params[3]
            rounded = false
            if (params.size == 5) {
                tl = params[4]
                tr = params[4]
                br = params[4]
                bl = params[4]
                rounded = true
            }
        } else if (params.size == 8) {
            a = params[0]
            b = params[1]
            c = params[2]
            d = params[3]
            tl = params[4]
            tr = params[5]
            br = params[6]
            bl = params[7]
            rounded = true
        }
        val hradius: Float
        val vradius: Float
        when (mode) {
            PConstants.CORNERS -> {
            }
            PConstants.CORNER -> {
                c += a
                d += b
            }
            PConstants.RADIUS -> {
                hradius = c
                vradius = d
                c = a + hradius
                d = b + vradius
                a -= hradius
                b -= vradius
            }
            PConstants.CENTER -> {
                hradius = c / 2.0f
                vradius = d / 2.0f
                c = a + hradius
                d = b + vradius
                a -= hradius
                b -= vradius
            }
        }
        if (a > c) {
            val temp = a
            a = c
            c = temp
        }
        if (b > d) {
            val temp = b
            b = d
            d = temp
        }
        val maxRounding = PApplet.min((c - a) / 2, (d - b) / 2)
        if (tl > maxRounding) tl = maxRounding
        if (tr > maxRounding) tr = maxRounding
        if (br > maxRounding) br = maxRounding
        if (bl > maxRounding) bl = maxRounding
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        if (rounded) {
            saveBezierVertexSettings()
            inGeo!!.addRect(a, b, c, d, tl, tr, br, bl, stroke)
            tessellator!!.tessellatePolygon(true, true, true)
            restoreBezierVertexSettings()
        } else {
            inGeo!!.addRect(a, b, c, d, stroke)
            tessellator!!.tessellateQuads()
        }
    }

    protected fun tessellateEllipse() {
        var a = 0f
        var b = 0f
        var c = 0f
        var d = 0f
        val mode = ellipseMode
        if (4 <= params.size) {
            a = params[0]
            b = params[1]
            c = params[2]
            d = params[3]
        }
        var x = a
        var y = b
        var w = c
        var h = d
        if (mode == PConstants.CORNERS) {
            w = c - a
            h = d - b
        } else if (mode == PConstants.RADIUS) {
            x = a - c
            y = b - d
            w = c * 2
            h = d * 2
        } else if (mode == PConstants.DIAMETER) {
            x = a - c / 2f
            y = b - d / 2f
        }
        if (w < 0) {  // undo negative width
            x += w
            w = -w
        }
        if (h < 0) {  // undo negative height
            y += h
            h = -h
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.setNormal(normalX, normalY, normalZ)
        inGeo!!.addEllipse(x, y, w, h, fill, stroke)
        tessellator!!.tessellateTriangleFan()
    }

    protected fun tessellateArc() {
        var a = 0f
        var b = 0f
        var c = 0f
        var d = 0f
        var start = 0f
        var stop = 0f
        val mode = ellipseMode
        var arcMode = 0
        if (6 <= params.size) {
            a = params[0]
            b = params[1]
            c = params[2]
            d = params[3]
            start = params[4]
            stop = params[5]
            if (params.size == 7) {
                arcMode = params[6].toInt()
            }
        }
        var x = a
        var y = b
        var w = c
        var h = d
        if (mode == PConstants.CORNERS) {
            w = c - a
            h = d - b
        } else if (mode == PConstants.RADIUS) {
            x = a - c
            y = b - d
            w = c * 2
            h = d * 2
        } else if (mode == PConstants.CENTER) {
            x = a - c / 2f
            y = b - d / 2f
        }

        // make sure the loop will exit before starting while
        if (!java.lang.Float.isInfinite(start) && !java.lang.Float.isInfinite(stop)) {
            // ignore equal and degenerate cases
            if (stop > start) {
                // make sure that we're starting at a useful point
                while (start < 0) {
                    start += PConstants.TWO_PI
                    stop += PConstants.TWO_PI
                }
                if (stop - start > PConstants.TWO_PI) {
                    // don't change start, it is visible in PIE mode
                    stop = start + PConstants.TWO_PI
                }
                inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                        ambientColor, specularColor, emissiveColor, shininess)
                inGeo!!.setNormal(normalX, normalY, normalZ)
                inGeo!!.addArc(x, y, w, h, start, stop, fill, stroke, arcMode)
                tessellator!!.tessellateTriangleFan()
            }
        }
    }

    protected fun tessellateBox() {
        var w = 0f
        var h = 0f
        var d = 0f
        if (params.size == 1) {
            d = params[0]
            h = d
            w = h
        } else if (params.size == 3) {
            w = params[0]
            h = params[1]
            d = params[2]
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        inGeo!!.addBox(w, h, d, fill, stroke)
        tessellator!!.tessellateQuads()
    }

    protected fun tessellateSphere() {
        var r = 0f
        var nu = sphereDetailU
        var nv = sphereDetailV
        if (1 <= params.size) {
            r = params[0]
            if (params.size == 2) {
                nv = params[1].toInt()
                nu = nv
            } else if (params.size == 3) {
                nu = params[1].toInt()
                nv = params[2].toInt()
            }
        }
        if (nu < 3 || nv < 2) {
            nv = 30
            nu = nv
        }
        val savedDetailU = pg!!.sphereDetailU
        val savedDetailV = pg!!.sphereDetailV
        if (pg!!.sphereDetailU != nu || pg!!.sphereDetailV != nv) {
            pg!!.sphereDetail(nu, nv)
        }
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        val indices = inGeo!!.addSphere(r, nu, nv, fill, stroke)
        tessellator!!.tessellateTriangles(indices)
        if (0 < savedDetailU && savedDetailU != nu ||
                0 < savedDetailV && savedDetailV != nv) {
            pg!!.sphereDetail(savedDetailU, savedDetailV)
        }
    }

    protected fun tessellatePath() {
        if (vertices == null) return
        inGeo!!.setMaterial(fillColor, strokeColor, strokeWeight,
                ambientColor, specularColor, emissiveColor, shininess)
        if (vertexCodeCount == 0) {  // each point is a simple vertex
            if (vertices[0].size == 2) {  // tessellating 2D vertices
                for (i in 0 until vertexCount) {
                    inGeo!!.addVertex(vertices[i][PConstants.X], vertices[i][PConstants.Y], PConstants.VERTEX, false)
                }
            } else {  // drawing 3D vertices
                for (i in 0 until vertexCount) {
                    inGeo!!.addVertex(vertices[i][PConstants.X], vertices[i][PConstants.Y], vertices[i][PConstants.Z],
                            PConstants.VERTEX, false)
                }
            }
        } else {  // coded set of vertices
            var idx = 0
            var brk = true
            if (vertices[0].size == 2) {  // tessellating a 2D path
                for (j in 0 until vertexCodeCount) {
                    when (vertexCodes[j]) {
                        PConstants.VERTEX -> {
                            inGeo!!.addVertex(vertices[idx][PConstants.X], vertices[idx][PConstants.Y], PConstants.VERTEX, brk)
                            brk = false
                            idx++
                        }
                        PConstants.QUADRATIC_VERTEX -> {
                            inGeo!!.addQuadraticVertex(vertices[idx + 0][PConstants.X], vertices[idx + 0][PConstants.Y], 0f,
                                    vertices[idx + 1][PConstants.X], vertices[idx + 1][PConstants.Y], 0f,
                                    brk)
                            brk = false
                            idx += 2
                        }
                        PConstants.BEZIER_VERTEX -> {
                            inGeo!!.addBezierVertex(vertices[idx + 0][PConstants.X], vertices[idx + 0][PConstants.Y], 0f,
                                    vertices[idx + 1][PConstants.X], vertices[idx + 1][PConstants.Y], 0f,
                                    vertices[idx + 2][PConstants.X], vertices[idx + 2][PConstants.Y], 0f,
                                    brk)
                            brk = false
                            idx += 3
                        }
                        PConstants.CURVE_VERTEX -> {
                            inGeo!!.addCurveVertex(vertices[idx][PConstants.X], vertices[idx][PConstants.Y], 0f, brk)
                            brk = false
                            idx++
                        }
                        PConstants.BREAK -> brk = true
                    }
                }
            } else {  // tessellating a 3D path
                for (j in 0 until vertexCodeCount) {
                    when (vertexCodes[j]) {
                        PConstants.VERTEX -> {
                            inGeo!!.addVertex(vertices[idx][PConstants.X], vertices[idx][PConstants.Y],
                                    vertices[idx][PConstants.Z], brk)
                            brk = false
                            idx++
                        }
                        PConstants.QUADRATIC_VERTEX -> {
                            inGeo!!.addQuadraticVertex(vertices[idx + 0][PConstants.X],
                                    vertices[idx + 0][PConstants.Y],
                                    vertices[idx + 0][PConstants.Z],
                                    vertices[idx + 1][PConstants.X],
                                    vertices[idx + 1][PConstants.Y],
                                    vertices[idx + 0][PConstants.Z],
                                    brk)
                            brk = false
                            idx += 2
                        }
                        PConstants.BEZIER_VERTEX -> {
                            inGeo!!.addBezierVertex(vertices[idx + 0][PConstants.X],
                                    vertices[idx + 0][PConstants.Y],
                                    vertices[idx + 0][PConstants.Z],
                                    vertices[idx + 1][PConstants.X],
                                    vertices[idx + 1][PConstants.Y],
                                    vertices[idx + 1][PConstants.Z],
                                    vertices[idx + 2][PConstants.X],
                                    vertices[idx + 2][PConstants.Y],
                                    vertices[idx + 2][PConstants.Z],
                                    brk)
                            brk = false
                            idx += 3
                        }
                        PConstants.CURVE_VERTEX -> {
                            inGeo!!.addCurveVertex(vertices[idx][PConstants.X],
                                    vertices[idx][PConstants.Y],
                                    vertices[idx][PConstants.Z],
                                    brk)
                            brk = false
                            idx++
                        }
                        PConstants.BREAK -> brk = true
                    }
                }
            }
        }
        val bez = inGeo!!.hasBezierVertex()
        val quad = inGeo!!.hasQuadraticVertex()
        val curv = inGeo!!.hasCurveVertex()
        if (bez || quad) saveBezierVertexSettings()
        if (curv) {
            saveCurveVertexSettings()
            tessellator!!.resetCurveVertexCount()
        }
        tessellator!!.tessellatePolygon(true, close, true)
        if (bez || quad) restoreBezierVertexSettings()
        if (curv) restoreCurveVertexSettings()
    }

    protected fun saveBezierVertexSettings() {
        savedBezierDetail = pg!!.bezierDetail
        if (pg!!.bezierDetail != bezierDetail) {
            pg!!.bezierDetail(bezierDetail)
        }
    }

    protected fun restoreBezierVertexSettings() {
        if (savedBezierDetail != bezierDetail) {
            pg!!.bezierDetail(savedBezierDetail)
        }
    }

    protected fun saveCurveVertexSettings() {
        savedCurveDetail = pg!!.curveDetail
        savedCurveTightness = pg!!.curveTightness
        if (pg!!.curveDetail != curveDetail) {
            pg!!.curveDetail(curveDetail)
        }
        if (pg!!.curveTightness != curveTightness) {
            pg!!.curveTightness(curveTightness)
        }
    }

    protected fun restoreCurveVertexSettings() {
        if (savedCurveDetail != curveDetail) {
            pg!!.curveDetail(savedCurveDetail)
        }
        if (savedCurveTightness != curveTightness) {
            pg!!.curveTightness(savedCurveTightness)
        }
    }

    ///////////////////////////////////////////////////////////

    //

    // Aggregation

    protected fun aggregate() {
        if (root === this && parent == null) {
            // Initializing auxiliary variables in root node
            // needed for aggregation.
            polyIndexOffset = 0
            polyVertexOffset = 0
            polyVertexAbs = 0
            polyVertexRel = 0
            lineIndexOffset = 0
            lineVertexOffset = 0
            lineVertexAbs = 0
            lineVertexRel = 0
            pointIndexOffset = 0
            pointVertexOffset = 0
            pointVertexAbs = 0
            pointVertexRel = 0

            // Recursive aggregation.
            aggregateImpl()
        }
    }

    // This method is very important, as it is responsible of generating the
    // correct vertex and index offsets for each level of the shape hierarchy.
    // This is the core of the recursive algorithm that calculates the indices
    // for the vertices accumulated in a single VBO.
    // Basically, the algorithm traverses all the shapes in the hierarchy and
    // updates the index cache for each child shape holding geometry (those being
    // the leaf nodes in the hierarchy tree), and creates index caches for the
    // group shapes so that the draw() method can be called from any shape in the
    // hierarchy and the correct piece of geometry will be rendered.
    //
    // For example, in the following hierarchy:
    //
    //                     ROOT GROUP
    //                         |
    //       /-----------------0-----------------\
    //       |                                   |
    //  CHILD GROUP 0                       CHILD GROUP 1
    //       |                                   |
    //       |                   /---------------0-----------------\
    //       |                   |               |                 |
    //   GEO SHAPE 0         GEO SHAPE 0     GEO SHAPE 1       GEO SHAPE 2
    //   4 vertices          5 vertices      6 vertices        3 vertices
    //
    // calling draw() from the root group should result in all the
    // vertices (4 + 5 + 6 + 3 = 18) being rendered, while calling
    // draw() from either child groups 0 or 1 should result in the first
    // 4 vertices or the last 14 vertices being rendered, respectively.
    protected fun aggregateImpl() {
        if (family == PConstants.GROUP) {
            // Recursively aggregating the child shapes.
            hasPolys = false
            hasLines = false
            hasPoints = false
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                child.aggregateImpl()
                hasPolys = hasPolys or child.hasPolys
                hasLines = hasLines or child.hasLines
                hasPoints = hasPoints or child.hasPoints
            }
        } else { // LEAF SHAPE (family either GEOMETRY, PATH or PRIMITIVE)
            hasPolys = -1 < firstPolyIndexCache && -1 < lastPolyIndexCache
            hasLines = -1 < firstLineIndexCache && -1 < lastLineIndexCache
            hasPoints = -1 < firstPointIndexCache && -1 < lastPointIndexCache
        }
        if (hasPolys) {
            updatePolyIndexCache()
        }
        if (is3D()) {
            if (hasLines) updateLineIndexCache()
            if (hasPoints) updatePointIndexCache()
        }
        if (matrix != null) {
            // Some geometric transformations were applied on
            // this shape before tessellation, so they are applied now.
            if (hasPolys) {
                tessGeo!!.applyMatrixOnPolyGeometry(matrix,
                        firstPolyVertex, lastPolyVertex)
            }
            if (is3D()) {
                if (hasLines) {
                    tessGeo!!.applyMatrixOnLineGeometry(matrix,
                            firstLineVertex, lastLineVertex)
                }
                if (hasPoints) {
                    tessGeo!!.applyMatrixOnPointGeometry(matrix,
                            firstPointVertex, lastPointVertex)
                }
            }
        }
    }

    // Updates the index cache for the range that corresponds to this shape.
    protected fun updatePolyIndexCache() {
        val cache = tessGeo!!.polyIndexCache
        if (family == PConstants.GROUP) {
            // Updates the index cache to include the elements corresponding to
            // a group shape, using the cache entries of the child shapes. The
            // index cache has a pyramidal structure where the base is formed
            // by the entries corresponding to the leaf (geometry) shapes, and
            // each subsequent level is determined by the higher-level group shapes
            // The index pyramid is flattened into arrays in order to use simple
            // data structures, so each shape needs to store the positions in the
            // cache that corresponds to itself.

            // The index ranges of the child shapes that share the vertex offset
            // are unified into a single range in the parent level.
            lastPolyIndexCache = -1
            firstPolyIndexCache = lastPolyIndexCache
            var gindex = -1
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                val first = child.firstPolyIndexCache
                val count = if (-1 < first) child.lastPolyIndexCache - first + 1 else -1
                for (n in first until first + count) {
                    if (gindex == -1) {
                        gindex = cache.addNew(n)
                        firstPolyIndexCache = gindex
                    } else {
                        if (cache.vertexOffset[gindex] == cache.vertexOffset[n]) {
                            // When the vertex offsets are the same, this means that the
                            // current index range in the group shape can be extended to
                            // include the index range in the current child shape.
                            // This is a result of how the indices are updated for the
                            // leaf shapes.
                            cache.incCounts(gindex,
                                    cache.indexCount[n], cache.vertexCount[n])
                        } else {
                            gindex = cache.addNew(n)
                        }
                    }
                }

                // Updating the first and last poly vertices for this group shape.
                if (-1 < child.firstPolyVertex) {
                    if (firstPolyVertex == -1) {
                        firstPolyVertex = Int.MAX_VALUE
                    }
                    firstPolyVertex = PApplet.min(firstPolyVertex, child.firstPolyVertex)
                }
                if (-1 < child.lastPolyVertex) {
                    lastPolyVertex = PApplet.max(lastPolyVertex, child.lastPolyVertex)
                }
            }
            lastPolyIndexCache = gindex
        } else {
            // The index cache is updated in order to reflect the fact that all
            // the vertices will be stored in a single VBO in the root shape.
            // This update works as follows (the methodology is the same for
            // poly, line and point): the VertexAbs variable in the root shape
            // stores the index of the last vertex up to this shape (plus one)
            // without taking into consideration the MAX_VERTEX_INDEX limit, so
            // it effectively runs over the entire range.
            // VertexRel, on the other hand, is reset every time the limit is
            // exceeded, therefore creating the start of a new index group in the
            // root shape. When this happens, the indices in the child shape need
            // to be restarted as well to reflect the new index offset.
            lastPolyVertex = cache.vertexOffset[firstPolyIndexCache]
            firstPolyVertex = lastPolyVertex
            for (n in firstPolyIndexCache..lastPolyIndexCache) {
                val ioffset = cache.indexOffset[n]
                val icount = cache.indexCount[n]
                val vcount = cache.vertexCount[n]
                if (PGL.MAX_VERTEX_INDEX1 <= root!!.polyVertexRel + vcount ||  // Too many vertices already signal the start of a new cache...
                        is2D && startStrokedTex(n)) {                      // ... or, in 2D, the beginning of line or points.
                    root!!.polyVertexRel = 0
                    root!!.polyVertexOffset = root!!.polyVertexAbs
                    cache.indexOffset[n] = root!!.polyIndexOffset
                } else {
                    tessGeo!!.incPolyIndices(ioffset, ioffset + icount - 1,
                            root!!.polyVertexRel)
                }
                cache.vertexOffset[n] = root!!.polyVertexOffset
                if (is2D) {
                    setFirstStrokeVertex(n, lastPolyVertex)
                }
                root!!.polyIndexOffset += icount
                root!!.polyVertexAbs += vcount
                root!!.polyVertexRel += vcount
                lastPolyVertex += vcount
            }
            lastPolyVertex--
            if (is2D) {
                setLastStrokeVertex(lastPolyVertex)
            }
        }
    }

    protected fun startStrokedTex(n: Int): Boolean {
        return image != null && (n == firstLineIndexCache ||
                n == firstPointIndexCache)
    }

    protected fun setFirstStrokeVertex(n: Int, vert: Int) {
        if (n == firstLineIndexCache && firstLineVertex == -1) {
            lastLineVertex = vert
            firstLineVertex = lastLineVertex
        }
        if (n == firstPointIndexCache && firstPointVertex == -1) {
            lastPointVertex = vert
            firstPointVertex = lastPointVertex
        }
    }

    protected fun setLastStrokeVertex(vert: Int) {
        if (-1 < lastLineVertex) {
            lastLineVertex = vert
        }
        if (-1 < lastPointVertex) {
            lastPointVertex += vert
        }
    }

    protected fun updateLineIndexCache() {
        val cache = tessGeo!!.lineIndexCache
        if (family == PConstants.GROUP) {
            lastLineIndexCache = -1
            firstLineIndexCache = lastLineIndexCache
            var gindex = -1
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                val first = child.firstLineIndexCache
                val count = if (-1 < first) child.lastLineIndexCache - first + 1 else -1
                for (n in first until first + count) {
                    if (gindex == -1) {
                        gindex = cache.addNew(n)
                        firstLineIndexCache = gindex
                    } else {
                        if (cache.vertexOffset[gindex] == cache.vertexOffset[n]) {
                            cache.incCounts(gindex, cache.indexCount[n],
                                    cache.vertexCount[n])
                        } else {
                            gindex = cache.addNew(n)
                        }
                    }
                }

                // Updating the first and last line vertices for this group shape.
                if (-1 < child.firstLineVertex) {
                    if (firstLineVertex == -1) firstLineVertex = Int.MAX_VALUE
                    firstLineVertex = PApplet.min(firstLineVertex, child.firstLineVertex)
                }
                if (-1 < child.lastLineVertex) {
                    lastLineVertex = PApplet.max(lastLineVertex, child.lastLineVertex)
                }
            }
            lastLineIndexCache = gindex
        } else {
            lastLineVertex = cache.vertexOffset[firstLineIndexCache]
            firstLineVertex = lastLineVertex
            for (n in firstLineIndexCache..lastLineIndexCache) {
                val ioffset = cache.indexOffset[n]
                val icount = cache.indexCount[n]
                val vcount = cache.vertexCount[n]
                if (PGL.MAX_VERTEX_INDEX1 <= root!!.lineVertexRel + vcount) {
                    root!!.lineVertexRel = 0
                    root!!.lineVertexOffset = root!!.lineVertexAbs
                    cache.indexOffset[n] = root!!.lineIndexOffset
                } else {
                    tessGeo!!.incLineIndices(ioffset, ioffset + icount - 1,
                            root!!.lineVertexRel)
                }
                cache.vertexOffset[n] = root!!.lineVertexOffset
                root!!.lineIndexOffset += icount
                root!!.lineVertexAbs += vcount
                root!!.lineVertexRel += vcount
                lastLineVertex += vcount
            }
            lastLineVertex--
        }
    }

    protected fun updatePointIndexCache() {
        val cache = tessGeo!!.pointIndexCache
        if (family == PConstants.GROUP) {
            lastPointIndexCache = -1
            firstPointIndexCache = lastPointIndexCache
            var gindex = -1
            for (i in 0 until childCount) {
                val child = children[i] as PShapeOpenGL
                val first = child.firstPointIndexCache
                val count = if (-1 < first) child.lastPointIndexCache - first + 1 else -1
                for (n in first until first + count) {
                    if (gindex == -1) {
                        gindex = cache.addNew(n)
                        firstPointIndexCache = gindex
                    } else {
                        if (cache.vertexOffset[gindex] == cache.vertexOffset[n]) {
                            // When the vertex offsets are the same, this means that the
                            // current index range in the group shape can be extended to
                            // include either the index range in the current child shape.
                            // This is a result of how the indices are updated for the
                            // leaf shapes in aggregateImpl().
                            cache.incCounts(gindex, cache.indexCount[n],
                                    cache.vertexCount[n])
                        } else {
                            gindex = cache.addNew(n)
                        }
                    }
                }

                // Updating the first and last point vertices for this group shape.
                if (-1 < child.firstPointVertex) {
                    if (firstPointVertex == -1) firstPointVertex = Int.MAX_VALUE
                    firstPointVertex = PApplet.min(firstPointVertex,
                            child.firstPointVertex)
                }
                if (-1 < child.lastPointVertex) {
                    lastPointVertex = PApplet.max(lastPointVertex, child.lastPointVertex)
                }
            }
            lastPointIndexCache = gindex
        } else {
            lastPointVertex = cache.vertexOffset[firstPointIndexCache]
            firstPointVertex = lastPointVertex
            for (n in firstPointIndexCache..lastPointIndexCache) {
                val ioffset = cache.indexOffset[n]
                val icount = cache.indexCount[n]
                val vcount = cache.vertexCount[n]
                if (PGL.MAX_VERTEX_INDEX1 <= root!!.pointVertexRel + vcount) {
                    root!!.pointVertexRel = 0
                    root!!.pointVertexOffset = root!!.pointVertexAbs
                    cache.indexOffset[n] = root!!.pointIndexOffset
                } else {
                    tessGeo!!.incPointIndices(ioffset, ioffset + icount - 1,
                            root!!.pointVertexRel)
                }
                cache.vertexOffset[n] = root!!.pointVertexOffset
                root!!.pointIndexOffset += icount
                root!!.pointVertexAbs += vcount
                root!!.pointVertexRel += vcount
                lastPointVertex += vcount
            }
            lastPointVertex--
        }
    }

    ///////////////////////////////////////////////////////////

    //

    //  Buffer initialization

    protected fun initBuffers() {
        val outdated = contextIsOutdated()
        context = pgl!!.currentContext
        if (hasPolys && (needBufferInit || outdated)) {
            initPolyBuffers()
        }
        if (hasLines && (needBufferInit || outdated)) {
            initLineBuffers()
        }
        if (hasPoints && (needBufferInit || outdated)) {
            initPointBuffers()
        }
        needBufferInit = false
    }

    protected fun initPolyBuffers() {
        val size = tessGeo!!.polyVertexCount
        val sizef = size * PGL.SIZEOF_FLOAT
        val sizei = size * PGL.SIZEOF_INT
        tessGeo!!.updatePolyVerticesBuffer()
        if (bufPolyVertex == null) bufPolyVertex = VertexBuffer(pg, PGL.ARRAY_BUFFER, 4, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyVertex!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 4 * sizef,
                tessGeo!!.polyVerticesBuffer, glUsage)
        tessGeo!!.updatePolyColorsBuffer()
        if (bufPolyColor == null) bufPolyColor = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_INT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyColor!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizei,
                tessGeo!!.polyColorsBuffer, glUsage)
        tessGeo!!.updatePolyNormalsBuffer()
        if (bufPolyNormal == null) bufPolyNormal = VertexBuffer(pg, PGL.ARRAY_BUFFER, 3, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyNormal!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 3 * sizef,
                tessGeo!!.polyNormalsBuffer, glUsage)
        tessGeo!!.updatePolyTexCoordsBuffer()
        if (bufPolyTexcoord == null) bufPolyTexcoord = VertexBuffer(pg, PGL.ARRAY_BUFFER, 2, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyTexcoord!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 2 * sizef,
                tessGeo!!.polyTexCoordsBuffer, glUsage)
        tessGeo!!.updatePolyAmbientBuffer()
        if (bufPolyAmbient == null) bufPolyAmbient = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_INT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyAmbient!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizei,
                tessGeo!!.polyAmbientBuffer, glUsage)
        tessGeo!!.updatePolySpecularBuffer()
        if (bufPolySpecular == null) bufPolySpecular = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_INT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolySpecular!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizei,
                tessGeo!!.polySpecularBuffer, glUsage)
        tessGeo!!.updatePolyEmissiveBuffer()
        if (bufPolyEmissive == null) bufPolyEmissive = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_INT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyEmissive!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizei,
                tessGeo!!.polyEmissiveBuffer, glUsage)
        tessGeo!!.updatePolyShininessBuffer()
        if (bufPolyShininess == null) bufPolyShininess = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyShininess!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizef,
                tessGeo!!.polyShininessBuffer, glUsage)
        for (name in polyAttribs!!.keys) {
            val attrib = polyAttribs!![name]
            tessGeo!!.updateAttribBuffer(attrib!!.name)
            if (!attrib.bufferCreated()) attrib.createBuffer(pgl)
            pgl!!.bindBuffer(PGL.ARRAY_BUFFER, attrib.buf.glId)
            pgl!!.bufferData(PGL.ARRAY_BUFFER, attrib.sizeInBytes(size),
                    tessGeo!!.polyAttribBuffers[name], glUsage)
        }
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
        tessGeo!!.updatePolyIndicesBuffer()
        if (bufPolyIndex == null) bufPolyIndex = VertexBuffer(pg, PGL.ELEMENT_ARRAY_BUFFER, 1, PGL.SIZEOF_INDEX, true)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, bufPolyIndex!!.glId)
        pgl!!.bufferData(PGL.ELEMENT_ARRAY_BUFFER,
                tessGeo!!.polyIndexCount * PGL.SIZEOF_INDEX,
                tessGeo!!.polyIndicesBuffer, glUsage)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, 0)
    }

    protected fun initLineBuffers() {
        val size = tessGeo!!.lineVertexCount
        val sizef = size * PGL.SIZEOF_FLOAT
        val sizei = size * PGL.SIZEOF_INT
        tessGeo!!.updateLineVerticesBuffer()
        if (bufLineVertex == null) bufLineVertex = VertexBuffer(pg, PGL.ARRAY_BUFFER, 4, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufLineVertex!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 4 * sizef,
                tessGeo!!.lineVerticesBuffer, glUsage)
        tessGeo!!.updateLineColorsBuffer()
        if (bufLineColor == null) bufLineColor = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_INT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufLineColor!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizei,
                tessGeo!!.lineColorsBuffer, glUsage)
        tessGeo!!.updateLineDirectionsBuffer()
        if (bufLineAttrib == null) bufLineAttrib = VertexBuffer(pg, PGL.ARRAY_BUFFER, 4, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufLineAttrib!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 4 * sizef,
                tessGeo!!.lineDirectionsBuffer, glUsage)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
        tessGeo!!.updateLineIndicesBuffer()
        if (bufLineIndex == null) bufLineIndex = VertexBuffer(pg, PGL.ELEMENT_ARRAY_BUFFER, 1, PGL.SIZEOF_INDEX, true)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, bufLineIndex!!.glId)
        pgl!!.bufferData(PGL.ELEMENT_ARRAY_BUFFER,
                tessGeo!!.lineIndexCount * PGL.SIZEOF_INDEX,
                tessGeo!!.lineIndicesBuffer, glUsage)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, 0)
    }

    protected fun initPointBuffers() {
        val size = tessGeo!!.pointVertexCount
        val sizef = size * PGL.SIZEOF_FLOAT
        val sizei = size * PGL.SIZEOF_INT
        tessGeo!!.updatePointVerticesBuffer()
        if (bufPointVertex == null) bufPointVertex = VertexBuffer(pg, PGL.ARRAY_BUFFER, 4, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPointVertex!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 4 * sizef,
                tessGeo!!.pointVerticesBuffer, glUsage)
        tessGeo!!.updatePointColorsBuffer()
        if (bufPointColor == null) bufPointColor = VertexBuffer(pg, PGL.ARRAY_BUFFER, 1, PGL.SIZEOF_INT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPointColor!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, sizei,
                tessGeo!!.pointColorsBuffer, glUsage)
        tessGeo!!.updatePointOffsetsBuffer()
        if (bufPointAttrib == null) bufPointAttrib = VertexBuffer(pg, PGL.ARRAY_BUFFER, 2, PGL.SIZEOF_FLOAT)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPointAttrib!!.glId)
        pgl!!.bufferData(PGL.ARRAY_BUFFER, 2 * sizef,
                tessGeo!!.pointOffsetsBuffer, glUsage)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
        tessGeo!!.updatePointIndicesBuffer()
        if (bufPointIndex == null) bufPointIndex = VertexBuffer(pg, PGL.ELEMENT_ARRAY_BUFFER, 1, PGL.SIZEOF_INDEX, true)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, bufPointIndex!!.glId)
        pgl!!.bufferData(PGL.ELEMENT_ARRAY_BUFFER,
                tessGeo!!.pointIndexCount * PGL.SIZEOF_INDEX,
                tessGeo!!.pointIndicesBuffer, glUsage)
        pgl!!.bindBuffer(PGL.ELEMENT_ARRAY_BUFFER, 0)
    }

    protected fun contextIsOutdated(): Boolean {
        val outdated = !pgl!!.contextIsCurrent(context)
        if (outdated) {
            bufPolyVertex!!.dispose()
            bufPolyColor!!.dispose()
            bufPolyNormal!!.dispose()
            bufPolyTexcoord!!.dispose()
            bufPolyAmbient!!.dispose()
            bufPolySpecular!!.dispose()
            bufPolyEmissive!!.dispose()
            bufPolyShininess!!.dispose()
            for (attrib in polyAttribs!!.values) {
                attrib.buf.dispose()
            }
            bufPolyIndex!!.dispose()
            bufLineVertex!!.dispose()
            bufLineColor!!.dispose()
            bufLineAttrib!!.dispose()
            bufLineIndex!!.dispose()
            bufPointVertex!!.dispose()
            bufPointColor!!.dispose()
            bufPointAttrib!!.dispose()
            bufPointIndex!!.dispose()
        }
        return outdated
    }

    ///////////////////////////////////////////////////////////

    //

    //  Geometry update

    protected fun updateGeometry() {
        root!!.initBuffers()
        if (root!!.modified) {
            root!!.updateGeometryImpl()
        }
    }

    protected fun updateGeometryImpl() {
        if (modifiedPolyVertices) {
            val offset = firstModifiedPolyVertex
            val size = lastModifiedPolyVertex - offset + 1
            copyPolyVertices(offset, size)
            modifiedPolyVertices = false
            firstModifiedPolyVertex = PConstants.MAX_INT
            lastModifiedPolyVertex = PConstants.MIN_INT
        }
        if (modifiedPolyColors) {
            val offset = firstModifiedPolyColor
            val size = lastModifiedPolyColor - offset + 1
            copyPolyColors(offset, size)
            modifiedPolyColors = false
            firstModifiedPolyColor = PConstants.MAX_INT
            lastModifiedPolyColor = PConstants.MIN_INT
        }
        if (modifiedPolyNormals) {
            val offset = firstModifiedPolyNormal
            val size = lastModifiedPolyNormal - offset + 1
            copyPolyNormals(offset, size)
            modifiedPolyNormals = false
            firstModifiedPolyNormal = PConstants.MAX_INT
            lastModifiedPolyNormal = PConstants.MIN_INT
        }
        if (modifiedPolyTexCoords) {
            val offset = firstModifiedPolyTexcoord
            val size = lastModifiedPolyTexcoord - offset + 1
            copyPolyTexCoords(offset, size)
            modifiedPolyTexCoords = false
            firstModifiedPolyTexcoord = PConstants.MAX_INT
            lastModifiedPolyTexcoord = PConstants.MIN_INT
        }
        if (modifiedPolyAmbient) {
            val offset = firstModifiedPolyAmbient
            val size = lastModifiedPolyAmbient - offset + 1
            copyPolyAmbient(offset, size)
            modifiedPolyAmbient = false
            firstModifiedPolyAmbient = PConstants.MAX_INT
            lastModifiedPolyAmbient = PConstants.MIN_INT
        }
        if (modifiedPolySpecular) {
            val offset = firstModifiedPolySpecular
            val size = lastModifiedPolySpecular - offset + 1
            copyPolySpecular(offset, size)
            modifiedPolySpecular = false
            firstModifiedPolySpecular = PConstants.MAX_INT
            lastModifiedPolySpecular = PConstants.MIN_INT
        }
        if (modifiedPolyEmissive) {
            val offset = firstModifiedPolyEmissive
            val size = lastModifiedPolyEmissive - offset + 1
            copyPolyEmissive(offset, size)
            modifiedPolyEmissive = false
            firstModifiedPolyEmissive = PConstants.MAX_INT
            lastModifiedPolyEmissive = PConstants.MIN_INT
        }
        if (modifiedPolyShininess) {
            val offset = firstModifiedPolyShininess
            val size = lastModifiedPolyShininess - offset + 1
            copyPolyShininess(offset, size)
            modifiedPolyShininess = false
            firstModifiedPolyShininess = PConstants.MAX_INT
            lastModifiedPolyShininess = PConstants.MIN_INT
        }
        for (name in polyAttribs!!.keys) {
            val attrib = polyAttribs!![name]
            if (attrib!!.modified) {
                val offset = firstModifiedPolyVertex
                val size = lastModifiedPolyVertex - offset + 1
                copyPolyAttrib(attrib, offset, size)
                attrib.modified = false
                attrib.firstModified = PConstants.MAX_INT
                attrib.lastModified = PConstants.MIN_INT
            }
        }
        if (modifiedLineVertices) {
            val offset = firstModifiedLineVertex
            val size = lastModifiedLineVertex - offset + 1
            copyLineVertices(offset, size)
            modifiedLineVertices = false
            firstModifiedLineVertex = PConstants.MAX_INT
            lastModifiedLineVertex = PConstants.MIN_INT
        }
        if (modifiedLineColors) {
            val offset = firstModifiedLineColor
            val size = lastModifiedLineColor - offset + 1
            copyLineColors(offset, size)
            modifiedLineColors = false
            firstModifiedLineColor = PConstants.MAX_INT
            lastModifiedLineColor = PConstants.MIN_INT
        }
        if (modifiedLineAttributes) {
            val offset = firstModifiedLineAttribute
            val size = lastModifiedLineAttribute - offset + 1
            copyLineAttributes(offset, size)
            modifiedLineAttributes = false
            firstModifiedLineAttribute = PConstants.MAX_INT
            lastModifiedLineAttribute = PConstants.MIN_INT
        }
        if (modifiedPointVertices) {
            val offset = firstModifiedPointVertex
            val size = lastModifiedPointVertex - offset + 1
            copyPointVertices(offset, size)
            modifiedPointVertices = false
            firstModifiedPointVertex = PConstants.MAX_INT
            lastModifiedPointVertex = PConstants.MIN_INT
        }
        if (modifiedPointColors) {
            val offset = firstModifiedPointColor
            val size = lastModifiedPointColor - offset + 1
            copyPointColors(offset, size)
            modifiedPointColors = false
            firstModifiedPointColor = PConstants.MAX_INT
            lastModifiedPointColor = PConstants.MIN_INT
        }
        if (modifiedPointAttributes) {
            val offset = firstModifiedPointAttribute
            val size = lastModifiedPointAttribute - offset + 1
            copyPointAttributes(offset, size)
            modifiedPointAttributes = false
            firstModifiedPointAttribute = PConstants.MAX_INT
            lastModifiedPointAttribute = PConstants.MIN_INT
        }
        modified = false
    }

    protected fun copyPolyVertices(offset: Int, size: Int) {
        tessGeo!!.updatePolyVerticesBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyVertex!!.glId)
        tessGeo!!.polyVerticesBuffer.position(4 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 4 * offset * PGL.SIZEOF_FLOAT,
                4 * size * PGL.SIZEOF_FLOAT, tessGeo!!.polyVerticesBuffer)
        tessGeo!!.polyVerticesBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolyColors(offset: Int, size: Int) {
        tessGeo!!.updatePolyColorsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyColor!!.glId)
        tessGeo!!.polyColorsBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_INT,
                size * PGL.SIZEOF_INT, tessGeo!!.polyColorsBuffer)
        tessGeo!!.polyColorsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolyNormals(offset: Int, size: Int) {
        tessGeo!!.updatePolyNormalsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyNormal!!.glId)
        tessGeo!!.polyNormalsBuffer.position(3 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 3 * offset * PGL.SIZEOF_FLOAT,
                3 * size * PGL.SIZEOF_FLOAT, tessGeo!!.polyNormalsBuffer)
        tessGeo!!.polyNormalsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolyTexCoords(offset: Int, size: Int) {
        tessGeo!!.updatePolyTexCoordsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyTexcoord!!.glId)
        tessGeo!!.polyTexCoordsBuffer.position(2 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 2 * offset * PGL.SIZEOF_FLOAT,
                2 * size * PGL.SIZEOF_FLOAT, tessGeo!!.polyTexCoordsBuffer)
        tessGeo!!.polyTexCoordsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolyAmbient(offset: Int, size: Int) {
        tessGeo!!.updatePolyAmbientBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyAmbient!!.glId)
        tessGeo!!.polyAmbientBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_INT,
                size * PGL.SIZEOF_INT, tessGeo!!.polyAmbientBuffer)
        tessGeo!!.polyAmbientBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolySpecular(offset: Int, size: Int) {
        tessGeo!!.updatePolySpecularBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolySpecular!!.glId)
        tessGeo!!.polySpecularBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_INT,
                size * PGL.SIZEOF_INT, tessGeo!!.polySpecularBuffer)
        tessGeo!!.polySpecularBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolyEmissive(offset: Int, size: Int) {
        tessGeo!!.updatePolyEmissiveBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyEmissive!!.glId)
        tessGeo!!.polyEmissiveBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_INT,
                size * PGL.SIZEOF_INT, tessGeo!!.polyEmissiveBuffer)
        tessGeo!!.polyEmissiveBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPolyShininess(offset: Int, size: Int) {
        tessGeo!!.updatePolyShininessBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPolyShininess!!.glId)
        tessGeo!!.polyShininessBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_FLOAT,
                size * PGL.SIZEOF_FLOAT, tessGeo!!.polyShininessBuffer)
        tessGeo!!.polyShininessBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    private fun copyPolyAttrib(attrib: PGraphicsOpenGL.VertexAttribute?, offset: Int, size: Int) {
        tessGeo!!.updateAttribBuffer(attrib!!.name, offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, attrib.buf.glId)
        val buf = tessGeo!!.polyAttribBuffers[attrib.name]
        buf!!.position(attrib.size * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, attrib.sizeInBytes(offset),
                attrib.sizeInBytes(size), buf)
        buf.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyLineVertices(offset: Int, size: Int) {
        tessGeo!!.updateLineVerticesBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufLineVertex!!.glId)
        tessGeo!!.lineVerticesBuffer.position(4 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 4 * offset * PGL.SIZEOF_FLOAT,
                4 * size * PGL.SIZEOF_FLOAT, tessGeo!!.lineVerticesBuffer)
        tessGeo!!.lineVerticesBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyLineColors(offset: Int, size: Int) {
        tessGeo!!.updateLineColorsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufLineColor!!.glId)
        tessGeo!!.lineColorsBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_INT,
                size * PGL.SIZEOF_INT, tessGeo!!.lineColorsBuffer)
        tessGeo!!.lineColorsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyLineAttributes(offset: Int, size: Int) {
        tessGeo!!.updateLineDirectionsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufLineAttrib!!.glId)
        tessGeo!!.lineDirectionsBuffer.position(4 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 4 * offset * PGL.SIZEOF_FLOAT,
                4 * size * PGL.SIZEOF_FLOAT, tessGeo!!.lineDirectionsBuffer)
        tessGeo!!.lineDirectionsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPointVertices(offset: Int, size: Int) {
        tessGeo!!.updatePointVerticesBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPointVertex!!.glId)
        tessGeo!!.pointVerticesBuffer.position(4 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 4 * offset * PGL.SIZEOF_FLOAT,
                4 * size * PGL.SIZEOF_FLOAT, tessGeo!!.pointVerticesBuffer)
        tessGeo!!.pointVerticesBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPointColors(offset: Int, size: Int) {
        tessGeo!!.updatePointColorsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPointColor!!.glId)
        tessGeo!!.pointColorsBuffer.position(offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, offset * PGL.SIZEOF_INT,
                size * PGL.SIZEOF_INT, tessGeo!!.pointColorsBuffer)
        tessGeo!!.pointColorsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun copyPointAttributes(offset: Int, size: Int) {
        tessGeo!!.updatePointOffsetsBuffer(offset, size)
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, bufPointAttrib!!.glId)
        tessGeo!!.pointOffsetsBuffer.position(2 * offset)
        pgl!!.bufferSubData(PGL.ARRAY_BUFFER, 2 * offset * PGL.SIZEOF_FLOAT,
                2 * size * PGL.SIZEOF_FLOAT, tessGeo!!.pointOffsetsBuffer)
        tessGeo!!.pointOffsetsBuffer.rewind()
        pgl!!.bindBuffer(PGL.ARRAY_BUFFER, 0)
    }

    protected fun setModifiedPolyVertices(first: Int, last: Int) {
        if (first < firstModifiedPolyVertex) firstModifiedPolyVertex = first
        if (last > lastModifiedPolyVertex) lastModifiedPolyVertex = last
        modifiedPolyVertices = true
        modified = true
    }

    protected fun setModifiedPolyColors(first: Int, last: Int) {
        if (first < firstModifiedPolyColor) firstModifiedPolyColor = first
        if (last > lastModifiedPolyColor) lastModifiedPolyColor = last
        modifiedPolyColors = true
        modified = true
    }

    protected fun setModifiedPolyNormals(first: Int, last: Int) {
        if (first < firstModifiedPolyNormal) firstModifiedPolyNormal = first
        if (last > lastModifiedPolyNormal) lastModifiedPolyNormal = last
        modifiedPolyNormals = true
        modified = true
    }

    protected fun setModifiedPolyTexCoords(first: Int, last: Int) {
        if (first < firstModifiedPolyTexcoord) firstModifiedPolyTexcoord = first
        if (last > lastModifiedPolyTexcoord) lastModifiedPolyTexcoord = last
        modifiedPolyTexCoords = true
        modified = true
    }

    protected fun setModifiedPolyAmbient(first: Int, last: Int) {
        if (first < firstModifiedPolyAmbient) firstModifiedPolyAmbient = first
        if (last > lastModifiedPolyAmbient) lastModifiedPolyAmbient = last
        modifiedPolyAmbient = true
        modified = true
    }

    protected fun setModifiedPolySpecular(first: Int, last: Int) {
        if (first < firstModifiedPolySpecular) firstModifiedPolySpecular = first
        if (last > lastModifiedPolySpecular) lastModifiedPolySpecular = last
        modifiedPolySpecular = true
        modified = true
    }

    protected fun setModifiedPolyEmissive(first: Int, last: Int) {
        if (first < firstModifiedPolyEmissive) firstModifiedPolyEmissive = first
        if (last > lastModifiedPolyEmissive) lastModifiedPolyEmissive = last
        modifiedPolyEmissive = true
        modified = true
    }

    protected fun setModifiedPolyShininess(first: Int, last: Int) {
        if (first < firstModifiedPolyShininess) firstModifiedPolyShininess = first
        if (last > lastModifiedPolyShininess) lastModifiedPolyShininess = last
        modifiedPolyShininess = true
        modified = true
    }

    private fun setModifiedPolyAttrib(attrib: PGraphicsOpenGL.VertexAttribute, first: Int, last: Int) {
        if (first < attrib.firstModified) attrib.firstModified = first
        if (last > attrib.lastModified) attrib.lastModified = last
        attrib.modified = true
        modified = true
    }

    protected fun setModifiedLineVertices(first: Int, last: Int) {
        if (first < firstModifiedLineVertex) firstModifiedLineVertex = first
        if (last > lastModifiedLineVertex) lastModifiedLineVertex = last
        modifiedLineVertices = true
        modified = true
    }

    protected fun setModifiedLineColors(first: Int, last: Int) {
        if (first < firstModifiedLineColor) firstModifiedLineColor = first
        if (last > lastModifiedLineColor) lastModifiedLineColor = last
        modifiedLineColors = true
        modified = true
    }

    protected fun setModifiedLineAttributes(first: Int, last: Int) {
        if (first < firstModifiedLineAttribute) firstModifiedLineAttribute = first
        if (last > lastModifiedLineAttribute) lastModifiedLineAttribute = last
        modifiedLineAttributes = true
        modified = true
    }

    protected fun setModifiedPointVertices(first: Int, last: Int) {
        if (first < firstModifiedPointVertex) firstModifiedPointVertex = first
        if (last > lastModifiedPointVertex) lastModifiedPointVertex = last
        modifiedPointVertices = true
        modified = true
    }

    protected fun setModifiedPointColors(first: Int, last: Int) {
        if (first < firstModifiedPointColor) firstModifiedPointColor = first
        if (last > lastModifiedPointColor) lastModifiedPointColor = last
        modifiedPointColors = true
        modified = true
    }

    protected fun setModifiedPointAttributes(first: Int, last: Int) {
        if (first < firstModifiedPointAttribute) firstModifiedPointAttribute = first
        if (last > lastModifiedPointAttribute) lastModifiedPointAttribute = last
        modifiedPointAttributes = true
        modified = true
    }

    ///////////////////////////////////////////////////////////

    //

    // Style handling

    override fun disableStyle() {
        if (openShape) {
            PGraphics.showWarning(INSIDE_BEGIN_END_ERROR, "disableStyle()")
            return
        }

        // Saving the current values to use if the style is re-enabled later
        savedStroke = stroke
        savedStrokeColor = strokeColor
        savedStrokeWeight = strokeWeight
        savedStrokeCap = strokeCap
        savedStrokeJoin = strokeJoin
        savedFill = fill
        savedFillColor = fillColor
        savedTint = tint
        savedTintColor = tintColor
        savedAmbientColor = ambientColor
        savedSpecularColor = specularColor
        savedEmissiveColor = emissiveColor
        savedShininess = shininess
        savedTextureMode = textureMode
        super.disableStyle()
    }

    override fun enableStyle() {
        if (savedStroke) {
            setStroke(true)
            setStroke(savedStrokeColor)
            setStrokeWeight(savedStrokeWeight)
            setStrokeCap(savedStrokeCap)
            setStrokeJoin(savedStrokeJoin)
        } else {
            setStroke(false)
        }
        if (savedFill) {
            setFill(true)
            setFill(savedFillColor)
        } else {
            setFill(false)
        }
        if (savedTint) {
            setTint(true)
            setTint(savedTintColor)
        }
        setAmbient(savedAmbientColor)
        setSpecular(savedSpecularColor)
        setEmissive(savedEmissiveColor)
        setShininess(savedShininess)
        if (image != null) {
            setTextureMode(savedTextureMode)
        }
        super.enableStyle()
    }

    override fun styles(g: PGraphics) {
        if (g is PGraphicsOpenGL) {
            if (g.stroke) {
                setStroke(true)
                setStroke(g.strokeColor)
                setStrokeWeight(g.strokeWeight)
                setStrokeCap(g.strokeCap)
                setStrokeJoin(g.strokeJoin)
            } else {
                setStroke(false)
            }
            if (g.fill) {
                setFill(true)
                setFill(g.fillColor)
            } else {
                setFill(false)
            }
            if (g.tint) {
                setTint(true)
                setTint(g.tintColor)
            }
            setAmbient(g.ambientColor)
            setSpecular(g.specularColor)
            setEmissive(g.emissiveColor)
            setShininess(g.shininess)
            if (image != null) {
                setTextureMode(g.textureMode)
            }
        } else {
            super.styles(g)
        }
    }

    ///////////////////////////////////////////////////////////

    //

    // Rendering methods

    /*
  public void draw() {
    draw(pg);
  }
  */

    override fun draw(g: PGraphics) {
        if (g is PGraphicsOpenGL) {
            val gl = g
            if (visible) {
                pre(gl)
                updateTessellation()
                updateGeometry()
                if (family == PConstants.GROUP) {
                    if (fragmentedGroup(gl)) {
                        for (i in 0 until childCount) {
                            (children[i] as PShapeOpenGL).draw(gl)
                        }
                    } else {
                        var tex: PImage? = null
                        if (textures != null && textures!!.size == 1) {
                            tex = textures!!.toTypedArray()[0]
                        }
                        render(gl, tex)
                    }
                } else {
                    render(gl, image)
                }
                post(gl)
            }
        } else {
            if (family == GEOMETRY) {
                inGeoToVertices()
            }
            pre(g)
            drawImpl(g)
            post(g)
        }
    }

    private fun inGeoToVertices() {
        vertexCount = 0
        vertexCodeCount = 0
        if (inGeo!!.codeCount == 0) {
            for (i in 0 until inGeo!!.vertexCount) {
                var index = 3 * i
                val x = inGeo!!.vertices[index++]
                val y = inGeo!!.vertices[index]
                super.vertex(x, y)
            }
        } else {
            var v: Int
            var x: Float
            var y: Float
            var cx: Float
            var cy: Float
            var x2: Float
            var y2: Float
            var x3: Float
            var y3: Float
            var x4: Float
            var y4: Float
            var idx = 0
            var insideContour = false
            for (j in 0 until inGeo!!.codeCount) {
                when (inGeo!!.codes[j]) {
                    PConstants.VERTEX -> {
                        v = 3 * idx
                        x = inGeo!!.vertices[v++]
                        y = inGeo!!.vertices[v]
                        super.vertex(x, y)
                        idx++
                    }
                    PConstants.QUADRATIC_VERTEX -> {
                        v = 3 * idx
                        cx = inGeo!!.vertices[v++]
                        cy = inGeo!!.vertices[v]
                        v = 3 * (idx + 1)
                        x3 = inGeo!!.vertices[v++]
                        y3 = inGeo!!.vertices[v]
                        super.quadraticVertex(cx, cy, x3, y3)
                        idx += 2
                    }
                    PConstants.BEZIER_VERTEX -> {
                        v = 3 * idx
                        x2 = inGeo!!.vertices[v++]
                        y2 = inGeo!!.vertices[v]
                        v = 3 * (idx + 1)
                        x3 = inGeo!!.vertices[v++]
                        y3 = inGeo!!.vertices[v]
                        v = 3 * (idx + 2)
                        x4 = inGeo!!.vertices[v++]
                        y4 = inGeo!!.vertices[v]
                        super.bezierVertex(x2, y2, x3, y3, x4, y4)
                        idx += 3
                    }
                    PConstants.CURVE_VERTEX -> {
                        v = 3 * idx
                        x = inGeo!!.vertices[v++]
                        y = inGeo!!.vertices[v]
                        super.curveVertex(x, y)
                        idx++
                    }
                    PConstants.BREAK -> {
                        if (insideContour) {
                            super.endContourImpl()
                        }
                        super.beginContourImpl()
                        insideContour = true
                    }
                }
            }
            if (insideContour) {
                super.endContourImpl()
            }
        }
    }

    // Returns true if some child shapes below this one either
    // use different texture maps (or only one texture is used by some while
    // others are untextured), or have stroked textures,
    // so they cannot rendered in a single call.
    // Or accurate 2D mode is enabled, which forces each
    // shape to be rendered separately.
    protected fun fragmentedGroup(g: PGraphicsOpenGL): Boolean {
        return g.getHint(PConstants.DISABLE_OPTIMIZED_STROKE) ||
                textures != null && (1 < textures!!.size || untexChild) ||
                strokedTexture
    }

    override fun pre(g: PGraphics) {
        if (g is PGraphicsOpenGL) {
            if (!style) {
                styles(g)
            }
        } else {
            super.pre(g)
        }
    }

    override fun post(g: PGraphics) {
        if (g is PGraphicsOpenGL) {
        } else {
            super.post(g)
        }
    }

    override fun drawGeometry(g: PGraphics) {
        vertexCount = inGeo!!.vertexCount
        vertices = inGeo!!.vertexData
        super.drawGeometry(g)
        vertexCount = 0
        vertices = null
    }

    // Render the geometry stored in the root shape as VBOs, for the vertices
    // corresponding to this shape. Sometimes we can have root == this.
    protected fun render(g: PGraphicsOpenGL, texture: PImage?) {
        if (root == null) {
            // Some error. Root should never be null. At least it should be 'this'.
            throw RuntimeException("Error rendering PShapeOpenGL, root shape is " +
                    "null")
        }
        if (hasPolys) {
            renderPolys(g, texture)
            if (g.haveRaw()) {
                rawPolys(g, texture)
            }
        }
        if (is3D()) {
            // In 3D mode, the lines and points need to be rendered separately
            // as they require their own shaders.
            if (hasLines) {
                renderLines(g)
                if (g.haveRaw()) {
                    rawLines(g)
                }
            }
            if (hasPoints) {
                renderPoints(g)
                if (g.haveRaw()) {
                    rawPoints(g)
                }
            }
        }
    }

    protected fun renderPolys(g: PGraphicsOpenGL, textureImage: PImage?) {
        val customShader = g.polyShader != null
        val needNormals = if (customShader) g.polyShader.accessNormals() else false
        val needTexCoords = if (customShader) g.polyShader.accessTexCoords() else false
        var tex = if (textureImage != null) g.getTexture(textureImage) else null
        var renderingFill = false
        var renderingStroke = false
        var shader: PShader? = null
        val cache = tessGeo!!.polyIndexCache
        for (n in firstPolyIndexCache..lastPolyIndexCache) {
            if (is3D() || tex != null && (firstLineIndexCache == -1 ||
                            n < firstLineIndexCache) &&
                    (firstPointIndexCache == -1 ||
                            n < firstPointIndexCache)) {
                // Rendering fill triangles, which can be lit and textured.
                if (!renderingFill) {
                    shader = g.getPolyShader(g.lights, tex != null)
                    shader.bind()
                    renderingFill = true
                }
            } else {
                // Rendering line or point triangles, which are never lit nor textured.
                if (!renderingStroke) {
                    if (tex != null) {
                        tex.unbind()
                        tex = null
                    }
                    if (shader != null && shader.bound()) {
                        shader.unbind()
                    }

                    // If the renderer is 2D, then g.lights should always be false,
                    // so no need to worry about that.
                    shader = g.getPolyShader(g.lights, false)
                    shader.bind()
                    renderingFill = false
                    renderingStroke = true
                }
            }
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            shader!!.setVertexAttribute(root!!.bufPolyVertex!!.glId, 4, PGL.FLOAT,
                    0, 4 * voffset * PGL.SIZEOF_FLOAT)
            shader.setColorAttribute(root!!.bufPolyColor!!.glId, 4, PGL.UNSIGNED_BYTE,
                    0, 4 * voffset * PGL.SIZEOF_BYTE)
            if (g.lights) {
                shader.setNormalAttribute(root!!.bufPolyNormal!!.glId, 3, PGL.FLOAT,
                        0, 3 * voffset * PGL.SIZEOF_FLOAT)
                shader.setAmbientAttribute(root!!.bufPolyAmbient!!.glId, 4, PGL.UNSIGNED_BYTE,
                        0, 4 * voffset * PGL.SIZEOF_BYTE)
                shader.setSpecularAttribute(root!!.bufPolySpecular!!.glId, 4, PGL.UNSIGNED_BYTE,
                        0, 4 * voffset * PGL.SIZEOF_BYTE)
                shader.setEmissiveAttribute(root!!.bufPolyEmissive!!.glId, 4, PGL.UNSIGNED_BYTE,
                        0, 4 * voffset * PGL.SIZEOF_BYTE)
                shader.setShininessAttribute(root!!.bufPolyShininess!!.glId, 1, PGL.FLOAT,
                        0, voffset * PGL.SIZEOF_FLOAT)
            }
            if (g.lights || needNormals) {
                shader.setNormalAttribute(root!!.bufPolyNormal!!.glId, 3, PGL.FLOAT,
                        0, 3 * voffset * PGL.SIZEOF_FLOAT)
            }
            if (tex != null || needTexCoords) {
                shader.setTexcoordAttribute(root!!.bufPolyTexcoord!!.glId, 2, PGL.FLOAT,
                        0, 2 * voffset * PGL.SIZEOF_FLOAT)
                shader.setTexture(tex)
            }
            for (attrib in polyAttribs!!.values) {
                if (!attrib.active(shader)) continue
                attrib.bind(pgl)
                shader.setAttributeVBO(attrib.glLoc, attrib.buf.glId,
                        attrib.tessSize, attrib.type,
                        attrib.isColor, 0, attrib.sizeInBytes(voffset))
            }
            shader.draw(root!!.bufPolyIndex!!.glId, icount, ioffset)
        }
        for (attrib in polyAttribs!!.values) {
            if (attrib.active(shader)) attrib.unbind(pgl)
        }
        if (shader != null && shader.bound()) {
            shader.unbind()
        }
    }

    protected fun rawPolys(g: PGraphicsOpenGL, textureImage: PImage?) {
        val raw = g.raw
        raw!!.colorMode(PConstants.RGB)
        raw!!.noStroke()
        raw!!.beginShape(PConstants.TRIANGLES)
        val vertices = tessGeo!!.polyVertices
        val color = tessGeo!!.polyColors
        val uv = tessGeo!!.polyTexCoords
        val indices = tessGeo!!.polyIndices
        val cache = tessGeo!!.polyIndexCache
        for (n in firstPolyIndexCache..lastPolyIndexCache) {
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            for (tr in ioffset / 3 until (ioffset + icount) / 3) {
                val i0 = voffset + indices[3 * tr + 0]
                val i1 = voffset + indices[3 * tr + 1]
                val i2 = voffset + indices[3 * tr + 2]
                val src0 = floatArrayOf(0f, 0f, 0f, 0f)
                val src1 = floatArrayOf(0f, 0f, 0f, 0f)
                val src2 = floatArrayOf(0f, 0f, 0f, 0f)
                val pt0 = floatArrayOf(0f, 0f, 0f, 0f)
                val pt1 = floatArrayOf(0f, 0f, 0f, 0f)
                val pt2 = floatArrayOf(0f, 0f, 0f, 0f)
                val argb0 = PGL.nativeToJavaARGB(color[i0])
                val argb1 = PGL.nativeToJavaARGB(color[i1])
                val argb2 = PGL.nativeToJavaARGB(color[i2])
                PApplet.arrayCopy(vertices, 4 * i0, src0, 0, 4)
                PApplet.arrayCopy(vertices, 4 * i1, src1, 0, 4)
                PApplet.arrayCopy(vertices, 4 * i2, src2, 0, 4)
                // Applying any transformation is currently stored in the
                // modelview matrix of the renderer.
                g.modelview.mult(src0, pt0)
                g.modelview.mult(src1, pt1)
                g.modelview.mult(src2, pt2)
                if (textureImage != null) {
                    raw!!.texture(textureImage)
                    if (raw!!.is3D()) {
                        raw!!.fill(argb0)
                        raw!!.vertex(pt0[PConstants.X], pt0[PConstants.Y], pt0[PConstants.Z], uv[2 * i0 + 0], uv[2 * i0 + 1])
                        raw!!.fill(argb1)
                        raw!!.vertex(pt1[PConstants.X], pt1[PConstants.Y], pt1[PConstants.Z], uv[2 * i1 + 0], uv[2 * i1 + 1])
                        raw!!.fill(argb2)
                        raw!!.vertex(pt2[PConstants.X], pt2[PConstants.Y], pt2[PConstants.Z], uv[2 * i2 + 0], uv[2 * i2 + 1])
                    } else if (raw!!.is2D()) {
                        val sx0 = g.screenXImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                        val sy0 = g.screenYImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                        val sx1 = g.screenXImpl(pt1[0], pt1[1], pt1[2], pt1[3])
                        val sy1 = g.screenYImpl(pt1[0], pt1[1], pt1[2], pt1[3])
                        val sx2 = g.screenXImpl(pt2[0], pt2[1], pt2[2], pt2[3])
                        val sy2 = g.screenYImpl(pt2[0], pt2[1], pt2[2], pt2[3])
                        raw!!.fill(argb0)
                        raw!!.vertex(sx0, sy0, uv[2 * i0 + 0], uv[2 * i0 + 1])
                        raw!!.fill(argb1)
                        raw!!.vertex(sx1, sy1, uv[2 * i1 + 0], uv[2 * i1 + 1])
                        raw!!.fill(argb1)
                        raw!!.vertex(sx2, sy2, uv[2 * i2 + 0], uv[2 * i2 + 1])
                    }
                } else {
                    if (raw!!.is3D()) {
                        raw!!.fill(argb0)
                        raw!!.vertex(pt0[PConstants.X], pt0[PConstants.Y], pt0[PConstants.Z])
                        raw!!.fill(argb1)
                        raw!!.vertex(pt1[PConstants.X], pt1[PConstants.Y], pt1[PConstants.Z])
                        raw!!.fill(argb2)
                        raw!!.vertex(pt2[PConstants.X], pt2[PConstants.Y], pt2[PConstants.Z])
                    } else if (raw!!.is2D()) {
                        val sx0 = g.screenXImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                        val sy0 = g.screenYImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                        val sx1 = g.screenXImpl(pt1[0], pt1[1], pt1[2], pt1[3])
                        val sy1 = g.screenYImpl(pt1[0], pt1[1], pt1[2], pt1[3])
                        val sx2 = g.screenXImpl(pt2[0], pt2[1], pt2[2], pt2[3])
                        val sy2 = g.screenYImpl(pt2[0], pt2[1], pt2[2], pt2[3])
                        raw!!.fill(argb0)
                        raw!!.vertex(sx0, sy0)
                        raw!!.fill(argb1)
                        raw!!.vertex(sx1, sy1)
                        raw!!.fill(argb2)
                        raw!!.vertex(sx2, sy2)
                    }
                }
            }
        }
        raw!!.endShape()
    }

    protected fun renderLines(g: PGraphicsOpenGL) {
        val shader = g.getLineShader()
        shader.bind()
        val cache = tessGeo!!.lineIndexCache
        for (n in firstLineIndexCache..lastLineIndexCache) {
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            shader.setVertexAttribute(root!!.bufLineVertex!!.glId, 4, PGL.FLOAT,
                    0, 4 * voffset * PGL.SIZEOF_FLOAT)
            shader.setColorAttribute(root!!.bufLineColor!!.glId, 4, PGL.UNSIGNED_BYTE,
                    0, 4 * voffset * PGL.SIZEOF_BYTE)
            shader.setLineAttribute(root!!.bufLineAttrib!!.glId, 4, PGL.FLOAT,
                    0, 4 * voffset * PGL.SIZEOF_FLOAT)
            shader.draw(root!!.bufLineIndex!!.glId, icount, ioffset)
        }
        shader.unbind()
    }

    protected fun rawLines(g: PGraphicsOpenGL) {
        val raw = g.raw
        raw!!.colorMode(PConstants.RGB)
        raw!!.noFill()
        raw!!.strokeCap(strokeCap)
        raw!!.strokeJoin(strokeJoin)
        raw!!.beginShape(PConstants.LINES)
        val vertices = tessGeo!!.lineVertices
        val color = tessGeo!!.lineColors
        val attribs = tessGeo!!.lineDirections
        val indices = tessGeo!!.lineIndices
        val cache = tessGeo!!.lineIndexCache
        for (n in firstLineIndexCache..lastLineIndexCache) {
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            for (ln in ioffset / 6 until (ioffset + icount) / 6) {
                // Each line segment is defined by six indices since its
                // formed by two triangles. We only need the first and last
                // vertices.
                // This bunch of vertices could also be the bevel triangles,
                // with we detect this situation by looking at the line weight.
                val i0 = voffset + indices[6 * ln + 0]
                val i1 = voffset + indices[6 * ln + 5]
                val sw0 = 2 * attribs[4 * i0 + 3]
                val sw1 = 2 * attribs[4 * i1 + 3]
                if (PGraphicsOpenGL.zero(sw0)) continue  // Bevel triangles, skip.
                val src0 = floatArrayOf(0f, 0f, 0f, 0f)
                val src1 = floatArrayOf(0f, 0f, 0f, 0f)
                val pt0 = floatArrayOf(0f, 0f, 0f, 0f)
                val pt1 = floatArrayOf(0f, 0f, 0f, 0f)
                val argb0 = PGL.nativeToJavaARGB(color[i0])
                val argb1 = PGL.nativeToJavaARGB(color[i1])
                PApplet.arrayCopy(vertices, 4 * i0, src0, 0, 4)
                PApplet.arrayCopy(vertices, 4 * i1, src1, 0, 4)
                // Applying any transformation is currently stored in the
                // modelview matrix of the renderer.
                g.modelview.mult(src0, pt0)
                g.modelview.mult(src1, pt1)
                if (raw!!.is3D()) {
                    raw!!.strokeWeight(sw0)
                    raw!!.stroke(argb0)
                    raw!!.vertex(pt0[PConstants.X], pt0[PConstants.Y], pt0[PConstants.Z])
                    raw!!.strokeWeight(sw1)
                    raw!!.stroke(argb1)
                    raw!!.vertex(pt1[PConstants.X], pt1[PConstants.Y], pt1[PConstants.Z])
                } else if (raw!!.is2D()) {
                    val sx0 = g.screenXImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                    val sy0 = g.screenYImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                    val sx1 = g.screenXImpl(pt1[0], pt1[1], pt1[2], pt1[3])
                    val sy1 = g.screenYImpl(pt1[0], pt1[1], pt1[2], pt1[3])
                    raw!!.strokeWeight(sw0)
                    raw!!.stroke(argb0)
                    raw!!.vertex(sx0, sy0)
                    raw!!.strokeWeight(sw1)
                    raw!!.stroke(argb1)
                    raw!!.vertex(sx1, sy1)
                }
            }
        }
        raw!!.endShape()
    }

    protected fun renderPoints(g: PGraphicsOpenGL) {
        val shader = g.getPointShader()
        shader.bind()
        val cache = tessGeo!!.pointIndexCache
        for (n in firstPointIndexCache..lastPointIndexCache) {
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            shader.setVertexAttribute(root!!.bufPointVertex!!.glId, 4, PGL.FLOAT,
                    0, 4 * voffset * PGL.SIZEOF_FLOAT)
            shader.setColorAttribute(root!!.bufPointColor!!.glId, 4, PGL.UNSIGNED_BYTE,
                    0, 4 * voffset * PGL.SIZEOF_BYTE)
            shader.setPointAttribute(root!!.bufPointAttrib!!.glId, 2, PGL.FLOAT,
                    0, 2 * voffset * PGL.SIZEOF_FLOAT)
            shader.draw(root!!.bufPointIndex!!.glId, icount, ioffset)
        }
        shader.unbind()
    }

    protected fun rawPoints(g: PGraphicsOpenGL) {
        val raw = g.raw
        raw!!.colorMode(PConstants.RGB)
        raw!!.noFill()
        raw!!.strokeCap(strokeCap)
        raw!!.beginShape(PConstants.POINTS)
        val vertices = tessGeo!!.pointVertices
        val color = tessGeo!!.pointColors
        val attribs = tessGeo!!.pointOffsets
        val indices = tessGeo!!.pointIndices
        val cache = tessGeo!!.pointIndexCache
        for (n in 0 until cache.size) {
            val ioffset = cache.indexOffset[n]
            val icount = cache.indexCount[n]
            val voffset = cache.vertexOffset[n]
            var pt = ioffset
            while (pt < (ioffset + icount) / 3) {
                val size = attribs[2 * pt + 2]
                var weight: Float
                var perim: Int
                if (0 < size) { // round point
                    weight = +size / 0.5f
                    perim = PApplet.min(PGraphicsOpenGL.MAX_POINT_ACCURACY,
                            PApplet.max(PGraphicsOpenGL.MIN_POINT_ACCURACY,
                                    (PConstants.TWO_PI * weight /
                                            PGraphicsOpenGL.POINT_ACCURACY_FACTOR).toInt())) + 1
                } else {        // Square point
                    weight = -size / 0.5f
                    perim = 5
                }
                val i0 = voffset + indices[3 * pt]
                val argb0 = PGL.nativeToJavaARGB(color[i0])
                val pt0 = floatArrayOf(0f, 0f, 0f, 0f)
                val src0 = floatArrayOf(0f, 0f, 0f, 0f)
                PApplet.arrayCopy(vertices, 4 * i0, src0, 0, 4)
                g.modelview.mult(src0, pt0)
                if (raw!!.is3D()) {
                    raw!!.strokeWeight(weight)
                    raw!!.stroke(argb0)
                    raw!!.vertex(pt0[PConstants.X], pt0[PConstants.Y], pt0[PConstants.Z])
                } else if (raw!!.is2D()) {
                    val sx0 = g.screenXImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                    val sy0 = g.screenYImpl(pt0[0], pt0[1], pt0[2], pt0[3])
                    raw!!.strokeWeight(weight)
                    raw!!.stroke(argb0)
                    raw!!.vertex(sx0, sy0)
                }
                pt += perim
            }
        }
        raw!!.endShape()
    }

    companion object {
        // Testing these constants, not use as they might go away...
        const val POSITION = 0
        const val NORMAL = 1
        const val TEXCOORD = 2
        const val DIRECTION = 3
        const val OFFSET = 4
        protected const val TRANSLATE = 0
        protected const val ROTATE = 1
        protected const val SCALE = 2
        protected const val MATRIX = 3

        // normal calculated per triangle
        protected const val NORMAL_MODE_AUTO = 0

        // one normal manually specified per shape
        protected const val NORMAL_MODE_SHAPE = 1

        // normals specified for each shape vertex
        protected const val NORMAL_MODE_VERTEX = 2

        ///////////////////////////////////////////////////////////

        //

        // Shape creation (temporary hack)
        @JvmStatic
        fun createShape(pg: PGraphicsOpenGL, src: PShape): PShapeOpenGL? {
            var dest: PShapeOpenGL? = null
            if (src.family == PConstants.GROUP) {
                //dest = PGraphics3D.createShapeImpl(pg, GROUP);
                dest = pg.createShapeFamily(PConstants.GROUP) as PShapeOpenGL
                copyGroup(pg, src, dest)
            } else if (src.family == PRIMITIVE) {
                //dest = PGraphics3D.createShapeImpl(pg, src.getKind(), src.getParams());
                dest = pg.createShapePrimitive(src.kind, *src.params) as PShapeOpenGL
                copyPrimitive(src, dest)
            } else if (src.family == GEOMETRY) {
                //dest = PGraphics3D.createShapeImpl(pg, PShape.GEOMETRY);
                dest = pg.createShapeFamily(GEOMETRY) as PShapeOpenGL
                copyGeometry(src, dest)
            } else if (src.family == PATH) {
                dest = pg.createShapeFamily(PATH) as PShapeOpenGL
                //dest = PGraphics3D.createShapeImpl(pg, PATH);
                copyPath(src, dest)
            }
            dest!!.setName(src.name)
            dest.width = src.width
            dest.height = src.height
            dest.depth = src.depth
            return dest
        }

        /*
  static public PShapeOpenGL createShape2D(PGraphicsOpenGL pg, PShape src) {
    PShapeOpenGL dest = null;
    if (src.getFamily() == GROUP) {
      //dest = PGraphics2D.createShapeImpl(pg, GROUP);
      dest = (PShapeOpenGL) pg.createShapeFamily(GROUP);
      copyGroup2D(pg, src, dest);
    } else if (src.getFamily() == PRIMITIVE) {
      //dest = PGraphics2D.createShapeImpl(pg, src.getKind(), src.getParams());
      dest = (PShapeOpenGL) pg.createShapePrimitive(src.getKind(), src.getParams());
      PShape.copyPrimitive(src, dest);
    } else if (src.getFamily() == GEOMETRY) {
      //dest = PGraphics2D.createShapeImpl(pg, PShape.GEOMETRY);
      dest = (PShapeOpenGL) pg.createShapeFamily(PShape.GEOMETRY);
      PShape.copyGeometry(src, dest);
    } else if (src.getFamily() == PATH) {
      //dest = PGraphics2D.createShapeImpl(pg, PATH);
      dest = (PShapeOpenGL) pg.createShapeFamily(PShape.PATH);
      PShape.copyPath(src, dest);
    }
    dest.setName(src.getName());
    dest.width = src.width;
    dest.height = src.height;
    return dest;
  }
*/
        @JvmStatic
        fun copyGroup(pg: PGraphicsOpenGL, src: PShape, dest: PShape?) {
            copyMatrix(src, dest)
            copyStyles(src, dest)
            copyImage(src, dest)
            for (i in 0 until src.childCount) {
                val c: PShape? = createShape(pg, src.getChild(i))
                dest!!.addChild(c)
            }
        }
    }
}