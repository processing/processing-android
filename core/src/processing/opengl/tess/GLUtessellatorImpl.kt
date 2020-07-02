/*
* Portions Copyright (C) 2003-2006 Sun Microsystems, Inc.
* All rights reserved.
*/

/*
** License Applicability. Except to the extent portions of this file are
** made subject to an alternative license as permitted in the SGI Free
** Software License B, Version 2.0 (the "License"), the contents of this
** file are subject only to the provisions of the License. You may not use
** this file except in compliance with the License. You may obtain a copy
** of the License at Silicon Graphics, Inc., attn: Legal Services, 1600
** Amphitheatre Parkway, Mountain View, CA 94043-1351, or at:
**
** http://oss.sgi.com/projects/FreeB
**
** Note that, as provided in the License, the Software is distributed on an
** "AS IS" basis, with ALL EXPRESS AND IMPLIED WARRANTIES AND CONDITIONS
** DISCLAIMED, INCLUDING, WITHOUT LIMITATION, ANY IMPLIED WARRANTIES AND
** CONDITIONS OF MERCHANTABILITY, SATISFACTORY QUALITY, FITNESS FOR A
** PARTICULAR PURPOSE, AND NON-INFRINGEMENT.
**
** NOTE:  The Original Code (as defined below) has been licensed to Sun
** Microsystems, Inc. ("Sun") under the SGI Free Software License B
** (Version 1.1), shown above ("SGI License").   Pursuant to Section
** 3.2(3) of the SGI License, Sun is distributing the Covered Code to
** you under an alternative license ("Alternative License").  This
** Alternative License includes all of the provisions of the SGI License
** except that Section 2.2 and 11 are omitted.  Any differences between
** the Alternative License and the SGI License are offered solely by Sun
** and not by SGI.
**
** Original Code. The Original Code is: OpenGL Sample Implementation,
** Version 1.2.1, released January 26, 2000, developed by Silicon Graphics,
** Inc. The Original Code is Copyright (c) 1991-2000 Silicon Graphics, Inc.
** Copyright in any portions created by third parties is as indicated
** elsewhere herein. All Rights Reserved.
**
** Additional Notice Provisions: The application programming interfaces
** established by SGI in conjunction with the Original Code are The
** OpenGL(R) Graphics System: A Specification (Version 1.2.1), released
** April 1, 1999; The OpenGL(R) Graphics System Utility Library (Version
** 1.3), released November 4, 1998; and OpenGL(R) Graphics with the X
** Window System(R) (Version 1.3), released October 19, 1998. This software
** was created using the OpenGL(R) version 1.2.1 Sample Implementation
** published by SGI, but has not been independently verified as being
** compliant with the OpenGL(R) version 1.2.1 Specification.
**
** Author: Eric Veach, July 1994
** Java Port: Pepijn Van Eeckhoudt, July 2003
** Java Port: Nathan Parker Burg, August 2003
** Processing integration: Andres Colubri, February 2012
*/

package processing.opengl.tess

import processing.opengl.tess.Mesh.__gl_meshCheckMesh
import processing.opengl.tess.Mesh.__gl_meshDeleteMesh
import processing.opengl.tess.Mesh.__gl_meshMakeEdge
import processing.opengl.tess.Mesh.__gl_meshNewMesh
import processing.opengl.tess.Mesh.__gl_meshSplice
import processing.opengl.tess.Mesh.__gl_meshSplitEdge

internal open class GLUtessellatorImpl private constructor() : PGLUtessellator {

    private var state: Int /* what begin/end calls have we seen? */
    private var lastEdge: GLUhalfEdge? = null /* lastEdge->Org is the most recent vertex */

    @JvmField
    var mesh: GLUmesh? = null  /* stores the input contours, and eventually
                                   the tessellation itself */

    /*** state needed for projecting onto the sweep plane  */
    @JvmField
    var normal = DoubleArray(3) /* user-specified normal (if provided) */
    @JvmField
    var sUnit = DoubleArray(3) /* unit vector in s-direction (debugging) */
    @JvmField
    var tUnit = DoubleArray(3) /* unit vector in t-direction (debugging) */

    /*** state needed for the line sweep  */
    private var relTolerance: Double /* tolerance for merging features */

    @JvmField
    var windingRule /* rule for determining polygon interior */: Int

    @JvmField
    var fatalError /* fatal error: needed combine callback */ = false

    @JvmField
    var dict /* edge dictionary for sweep line */: Dict? = null

    @JvmField
    var pq: PriorityQ? = null /* priority queue of vertex events */

    @JvmField
    var event: GLUvertex? = null /* current sweep event being processed */

    /*** state needed for rendering callbacks (see render.c)  */
    @JvmField
    var flagBoundary: Boolean /* mark boundary edges (use EdgeFlag) */
    @JvmField
    var boundaryOnly /* Extract contours, not triangles */: Boolean

    @JvmField
    var avoidDegenerateTris = false /* JOGL-specific hint to try to improve triangulation
                                    by avoiding producing degenerate (zero-area) triangles;
                                    has not been tested exhaustively and is therefore an option */
    @JvmField
    var lonelyTriList: GLUface? = null

    /* list of triangles which could not be rendered as strips or fans */
    /*** state needed to cache single-contour polygons for renderCache()  */
    private var flushCacheOnNextVertex = false /* empty cache on next vertex() call */

    @JvmField
    var cacheCount = 0 /* number of cached vertices */

    @JvmField
    var cache = arrayOfNulls<CachedVertex>(TESS_MAX_CACHE) /* the vertex data */

    /*** rendering callbacks that also pass polygon data   */
    private var polygonData /* client data for current polygon */: Any?
    private var callBegin: PGLUtessellatorCallback
    private var callEdgeFlag: PGLUtessellatorCallback
    private var callVertex: PGLUtessellatorCallback
    private var callEnd: PGLUtessellatorCallback

    //    private GLUtessellatorCallback callMesh;
    private var callError: PGLUtessellatorCallback
    private var callCombine: PGLUtessellatorCallback
    private var callBeginData: PGLUtessellatorCallback
    private var callEdgeFlagData: PGLUtessellatorCallback
    private var callVertexData: PGLUtessellatorCallback
    private var callEndData: PGLUtessellatorCallback

    //    private GLUtessellatorCallback callMeshData;
    private var callErrorData: PGLUtessellatorCallback
    private var callCombineData: PGLUtessellatorCallback

    private fun makeDormant() {
        /* Return the tessellator to its original dormant state. */
        if (mesh != null) {
            __gl_meshDeleteMesh(mesh!!)
        }
        state = TessState.T_DORMANT
        lastEdge = null
        mesh = null
    }

    private fun requireState(newState: Int) {
        if (state != newState) gotoState(newState)
    }

    private fun gotoState(newState: Int) {
        while (state != newState) {
            /* We change the current state one level at a time, to get to
             * the desired state.
             */
            if (state < newState) {
                if (state == TessState.T_DORMANT) {
                    callErrorOrErrorData(PGLU.GLU_TESS_MISSING_BEGIN_POLYGON)
                    gluTessBeginPolygon(null)
                } else if (state == TessState.T_IN_POLYGON) {
                    callErrorOrErrorData(PGLU.GLU_TESS_MISSING_BEGIN_CONTOUR)
                    gluTessBeginContour()
                }
            } else {
                if (state == TessState.T_IN_CONTOUR) {
                    callErrorOrErrorData(PGLU.GLU_TESS_MISSING_END_CONTOUR)
                    gluTessEndContour()
                } else if (state == TessState.T_IN_POLYGON) {
                    callErrorOrErrorData(PGLU.GLU_TESS_MISSING_END_POLYGON)
                    /* gluTessEndPolygon( tess ) is too much work! */makeDormant()
                }
            }
        }
    }

    fun gluDeleteTess() {
        requireState(TessState.T_DORMANT)
    }

    fun gluTessProperty(which: Int, value: Double) {
        when (which) {
            PGLU.GLU_TESS_TOLERANCE -> run{
                // need to test the break here
                if (value < 0.0 || value > 1.0) return@run
                relTolerance = value
                return
            }
            PGLU.GLU_TESS_WINDING_RULE -> run{
                val windingRule = value.toInt()
                // need to test the break here
                if (windingRule.toDouble() != value) return@run /* not an integer */
                when (windingRule) {
                    PGLU.GLU_TESS_WINDING_ODD, PGLU.GLU_TESS_WINDING_NONZERO, PGLU.GLU_TESS_WINDING_POSITIVE, PGLU.GLU_TESS_WINDING_NEGATIVE, PGLU.GLU_TESS_WINDING_ABS_GEQ_TWO -> {
                        this.windingRule = windingRule
                        return
                    }
                    else -> {
                    }
                }
                boundaryOnly = value != 0.0
                return
            }
            PGLU.GLU_TESS_BOUNDARY_ONLY -> {
                boundaryOnly = value != 0.0
                return
            }
            PGLU.GLU_TESS_AVOID_DEGENERATE_TRIANGLES -> {
                avoidDegenerateTris = value != 0.0
                return
            }
            else -> {
                callErrorOrErrorData(PGLU.GLU_INVALID_ENUM)
                return
            }
        }
        callErrorOrErrorData(PGLU.GLU_INVALID_VALUE)
    }

    /* Returns tessellator property */
    fun gluGetTessProperty(which: Int, value: DoubleArray, value_offset: Int) {
        when (which) {
            PGLU.GLU_TESS_TOLERANCE -> {
                assert(0.0 <= relTolerance && relTolerance <= 1.0)
                value[value_offset] = relTolerance
            }
            PGLU.GLU_TESS_WINDING_RULE -> {
                assert(windingRule == PGLU.GLU_TESS_WINDING_ODD || windingRule == PGLU.GLU_TESS_WINDING_NONZERO || windingRule == PGLU.GLU_TESS_WINDING_POSITIVE || windingRule == PGLU.GLU_TESS_WINDING_NEGATIVE || windingRule == PGLU.GLU_TESS_WINDING_ABS_GEQ_TWO)
                value[value_offset] = windingRule.toDouble()
            }
            PGLU.GLU_TESS_BOUNDARY_ONLY -> {
                assert(boundaryOnly == true || boundaryOnly == false)
                value[value_offset] = if (boundaryOnly) 1.0 else 0.0
            }
            PGLU.GLU_TESS_AVOID_DEGENERATE_TRIANGLES -> value[value_offset] = if (avoidDegenerateTris) 1.0 else 0.0
            else -> {
                value[value_offset] = 0.0
                callErrorOrErrorData(PGLU.GLU_INVALID_ENUM)
            }
        }
    } /* gluGetTessProperty() */

    fun gluTessNormal(x: Double, y: Double, z: Double) {
        normal[0] = x
        normal[1] = y
        normal[2] = z
    }

    fun gluTessCallback(which: Int, aCallback: PGLUtessellatorCallback?) {
        when (which) {
            PGLU.GLU_TESS_BEGIN -> {
                callBegin = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_BEGIN_DATA -> {
                callBeginData = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_EDGE_FLAG -> {
                callEdgeFlag = aCallback ?: NULL_CB
                /* If the client wants boundary edges to be flagged,
 * we render everything as separate triangles (no strips or fans).
 */
                flagBoundary = aCallback != null
                return
            }
            PGLU.GLU_TESS_EDGE_FLAG_DATA -> {
                run {
                    callBegin = aCallback ?: NULL_CB
                    callEdgeFlagData = callBegin
                }

                /* If the client wants boundary edges to be flagged,
* we render everything as separate triangles (no strips or fans).
 */
                flagBoundary = aCallback != null
                return
            }
            PGLU.GLU_TESS_VERTEX -> {
                callVertex = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_VERTEX_DATA -> {
                callVertexData = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_END -> {
                callEnd = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_END_DATA -> {
                callEndData = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_ERROR -> {
                callError = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_ERROR_DATA -> {
                callErrorData = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_COMBINE -> {
                callCombine = aCallback ?: NULL_CB
                return
            }
            PGLU.GLU_TESS_COMBINE_DATA -> {
                callCombineData = aCallback ?: NULL_CB
                return
            }
            else -> {
                callErrorOrErrorData(PGLU.GLU_INVALID_ENUM)
                return
            }
        }
    }

    private fun addVertex(coords: DoubleArray, vertexData: Any?): Boolean {
        var e: GLUhalfEdge?
        e = lastEdge
        if (e == null) {

/* Make a self-loop (one vertex, one edge). */

            e = __gl_meshMakeEdge(mesh!!)
            if (e == null) return false
            if (!__gl_meshSplice(e, e.Sym!!)) return false
        } else {

/* Create a new vertex and edge which immediately follow e
 * in the ordering around the left face.
 */
            if (__gl_meshSplitEdge(e) == null) return false
            e = e.Lnext
        }

/* The new vertex is now e.Org. */e!!.Org!!.data = vertexData
        e.Org!!.coords[0] = coords[0]
        e.Org!!.coords[1] = coords[1]
        e.Org!!.coords[2] = coords[2]

/* The winding of an edge says how the winding number changes as we
 * cross from the edge''s right face to its left face.  We add the
 * vertices in such an order that a CCW contour will add +1 to
 * the winding number of the region inside the contour.
 */
        e.winding = 1
        e.Sym!!.winding = -1
        lastEdge = e
        return true
    }

    private fun cacheVertex(coords: DoubleArray, vertexData: Any?) {
        if (cache[cacheCount] == null) {
            cache[cacheCount] = CachedVertex()
        }
        val v = cache[cacheCount]
        v!!.data = vertexData
        v.coords[0] = coords[0]
        v.coords[1] = coords[1]
        v.coords[2] = coords[2]
        ++cacheCount
    }

    private fun flushCache(): Boolean {
        val v = cache
        mesh = __gl_meshNewMesh()
        if (mesh == null) return false
        for (i in 0 until cacheCount) {
            val vertex = v[i]
            if (!addVertex(vertex!!.coords, vertex.data)) return false
        }
        cacheCount = 0
        flushCacheOnNextVertex = false
        return true
    }

    fun gluTessVertex(coords: DoubleArray?, coords_offset: Int, vertexData: Any?) {
        var i: Int
        var tooLarge = false
        var x: Double
        val clamped = DoubleArray(3)
        requireState(TessState.T_IN_CONTOUR)
        if (flushCacheOnNextVertex) {
            if (!flushCache()) {
                callErrorOrErrorData(PGLU.GLU_OUT_OF_MEMORY)
                return
            }
            lastEdge = null
        }
        i = 0
        while (i < 3) {
            x = coords!![i + coords_offset]
            if (x < -PGLU.GLU_TESS_MAX_COORD) {
                x = -PGLU.GLU_TESS_MAX_COORD
                tooLarge = true
            }
            if (x > PGLU.GLU_TESS_MAX_COORD) {
                x = PGLU.GLU_TESS_MAX_COORD
                tooLarge = true
            }
            clamped[i] = x
            ++i
        }
        if (tooLarge) {
            callErrorOrErrorData(PGLU.GLU_TESS_COORD_TOO_LARGE)
        }
        if (mesh == null) {
            if (cacheCount < TESS_MAX_CACHE) {
                cacheVertex(clamped, vertexData)
                return
            }
            if (!flushCache()) {
                callErrorOrErrorData(PGLU.GLU_OUT_OF_MEMORY)
                return
            }
        }
        if (!addVertex(clamped, vertexData)) {
            callErrorOrErrorData(PGLU.GLU_OUT_OF_MEMORY)
        }
    }

    fun gluTessBeginPolygon(data: Any?) {
        requireState(TessState.T_DORMANT)
        state = TessState.T_IN_POLYGON
        cacheCount = 0
        flushCacheOnNextVertex = false
        mesh = null
        polygonData = data
    }

    fun gluTessBeginContour() {
        requireState(TessState.T_IN_POLYGON)
        state = TessState.T_IN_CONTOUR
        lastEdge = null
        if (cacheCount > 0) {

/* Just set a flag so we don't get confused by empty contours
 * -- these can be generated accidentally with the obsolete
 * NextContour() interface.
 */
            flushCacheOnNextVertex = true
        }
    }

    fun gluTessEndContour() {
        requireState(TessState.T_IN_CONTOUR)
        state = TessState.T_IN_POLYGON
    }

    fun gluTessEndPolygon() {
        var mesh: GLUmesh?
        try {
            requireState(TessState.T_IN_POLYGON)
            state = TessState.T_DORMANT
            if (this.mesh == null) {
                if (!flagBoundary /*&& callMesh == NULL_CB*/) {

/* Try some special code to make the easy cases go quickly
 * (eg. convex polygons).  This code does NOT handle multiple contours,
 * intersections, edge flags, and of course it does not generate
 * an explicit mesh either.
 */
                    if (Render.__gl_renderCache(this)) {
                        polygonData = null
                        return
                    }
                }
                if (!flushCache()) throw RuntimeException() /* could've used a label*/
            }

/* Determine the polygon normal and project vertices onto the plane
         * of the polygon.
         */
            Normal.__gl_projectPolygon(this)

/* __gl_computeInterior( tess ) computes the planar arrangement specified
 * by the given contours, and further subdivides this arrangement
 * into regions.  Each region is marked "inside" if it belongs
 * to the polygon, according to the rule given by windingRule.
 * Each interior region is guaranteed be monotone.
 */
            if (!Sweep.__gl_computeInterior(this)) {
                throw RuntimeException() /* could've used a label */
            }
            mesh = this.mesh
            if (!fatalError) {
                var rc = true

/* If the user wants only the boundary contours, we throw away all edges
 * except those which separate the interior from the exterior.
 * Otherwise we tessellate all the regions marked "inside".
 */
                rc = if (boundaryOnly) {
                    TessMono.__gl_meshSetWindingNumber(mesh, 1, true)
                } else {
                    TessMono.__gl_meshTessellateInterior(mesh, avoidDegenerateTris)
                }
                if (!rc) throw RuntimeException() /* could've used a label */
                __gl_meshCheckMesh(mesh!!)
                if (callBegin !== NULL_CB || callEnd !== NULL_CB || callVertex !== NULL_CB || callEdgeFlag !== NULL_CB || callBeginData !== NULL_CB || callEndData !== NULL_CB || callVertexData !== NULL_CB || callEdgeFlagData !== NULL_CB) {
                    if (boundaryOnly) {
                        Render.__gl_renderBoundary(this, mesh) /* output boundary contours */
                    } else {
                        Render.__gl_renderMesh(this, mesh) /* output strips and fans */
                    }
                }

                //                if (callMesh != NULL_CB) {
//
///* Throw away the exterior faces, so that all faces are interior.
//                 * This way the user doesn't have to check the "inside" flag,
//                 * and we don't need to even reveal its existence.  It also leaves
//                 * the freedom for an implementation to not generate the exterior
//                 * faces in the first place.
//                 */
//                    TessMono.__gl_meshDiscardExterior(mesh);
//                    callMesh.mesh(mesh);        /* user wants the mesh itself */
//                    mesh = null;
//                    polygonData = null;
//                    return;
//                }
            }
            __gl_meshDeleteMesh(mesh!!)
            polygonData = null
            mesh = null
        } catch (e: Exception) {
            e.printStackTrace()
            callErrorOrErrorData(PGLU.GLU_OUT_OF_MEMORY)
        }
    }

    /** */ /* Obsolete calls -- for backward compatibility */
    fun gluBeginPolygon() {
        gluTessBeginPolygon(null)
        gluTessBeginContour()
    }

    /*ARGSUSED*/
    fun gluNextContour(type: Int) {
        gluTessEndContour()
        gluTessBeginContour()
    }

    fun gluEndPolygon() {
        gluTessEndContour()
        gluTessEndPolygon()
    }

    fun callBeginOrBeginData(a: Int) {
        if (callBeginData !== NULL_CB) callBeginData.beginData(a, polygonData) else callBegin.begin(a)
    }

    fun callVertexOrVertexData(a: Any?) {
        if (callVertexData !== NULL_CB) callVertexData.vertexData(a, polygonData) else callVertex.vertex(a)
    }

    fun callEdgeFlagOrEdgeFlagData(a: Boolean) {
        if (callEdgeFlagData !== NULL_CB) callEdgeFlagData.edgeFlagData(a, polygonData) else callEdgeFlag.edgeFlag(a)
    }

    fun callEndOrEndData() {
        if (callEndData !== NULL_CB) callEndData.endData(polygonData) else callEnd.end()
    }

    fun callCombineOrCombineData(coords: DoubleArray?, vertexData: Array<Any?>?, weights: FloatArray?, outData: Array<Any?>?) {
        if (callCombineData !== NULL_CB) callCombineData.combineData(coords, vertexData, weights, outData, polygonData) else callCombine.combine(coords, vertexData, weights, outData)
    }

    fun callErrorOrErrorData(a: Int) {
        if (callErrorData !== NULL_CB) callErrorData.errorData(a, polygonData) else callError.error(a)
    }

    companion object {
        const val TESS_MAX_CACHE = 100
        private const val GLU_TESS_DEFAULT_TOLERANCE = 0.0

        //    private static final int GLU_TESS_MESH = 100112;    /* void (*)(GLUmesh *mesh)        */
        private val NULL_CB: PGLUtessellatorCallback = PGLUtessellatorCallbackAdapter()

        @JvmStatic
        fun gluNewTess(): PGLUtessellator {
            return GLUtessellatorImpl()
        }

    }

    //    #define MAX_FAST_ALLOC    (MAX(sizeof(EdgePair), \
    //                 MAX(sizeof(GLUvertex),sizeof(GLUface))))

    // constructor or initializer block
    init {
        state = TessState.T_DORMANT
        normal[0] = 0.0
        normal[1] = 0.0
        normal[2] = 0.0
        relTolerance = GLU_TESS_DEFAULT_TOLERANCE
        windingRule = PGLU.GLU_TESS_WINDING_ODD
        flagBoundary = false
        boundaryOnly = false
        callBegin = NULL_CB
        callEdgeFlag = NULL_CB
        callVertex = NULL_CB
        callEnd = NULL_CB
        callError = NULL_CB
        callCombine = NULL_CB
        //        callMesh = NULL_CB;
        callBeginData = NULL_CB
        callEdgeFlagData = NULL_CB
        callVertexData = NULL_CB
        callEndData = NULL_CB
        callErrorData = NULL_CB
        callCombineData = NULL_CB
        polygonData = null

        for (i in cache.indices) {
            cache[i] = CachedVertex()
        }
    }

}