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

import android.view.SurfaceHolder
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState

import processing.android.AppComponent
import processing.core.PGraphics
import processing.core.PMatrix3D
import processing.core.PSurface
import processing.opengl.*

import java.nio.FloatBuffer
import java.util.*

class ARGraphics : PGraphics3D() {
    // Convenience reference to the AR surface. It is the same object one gets from PApplet.getSurface().
    private var surfar: ARSurface? = null
    private var backgroundRenderer: BackgroundRenderer? = null
    private var projMatrix: FloatArray? = FloatArray(16)
    private var viewMatrix: FloatArray? = FloatArray(16)
    private var anchorMatrix = FloatArray(16)
    private var colorCorrection = FloatArray(4)
    private var trackers = ArrayList<ARTracker>()
    private var trackPlanes = ArrayList<Plane>()
    private var trackMatrices = HashMap<Plane, FloatArray>()
    private var trackIds = HashMap<Plane, Int>()
    private var trackIdx = HashMap<Int, Int>()
    private var newPlanes = ArrayList<Plane>()
    private var delAnchors = ArrayList<Int>()
    private var anchors = HashMap<Int, Anchor>()
    private var pointIn = FloatArray(3)
    private var pointOut = FloatArray(3)
    private var lastTrackableId = 0
    private var lastAnchorId = 0
    private var arLightShader: PShader? = null
    private var arTexlightShader: PShader? = null

    override fun createSurface(appComponent: AppComponent?, surfaceHolder: SurfaceHolder?, reset: Boolean): PSurface {
        if (reset) pgl.resetFBOLayer()
        this@ARGraphics.surfar = ARSurface(this@ARGraphics, appComponent, surfaceHolder)
        return surfar!! as PSurface
    }

    override fun createPGL(pGraphicsOpenGL: PGraphicsOpenGL?): PGL {
        return PGLES(pGraphicsOpenGL!!)
    }

    override fun beginDraw() {
        super.beginDraw()
        updateView()

        // Always clear the screen and draw the background
        background(0)
        backgroundRenderer!!.draw(surfar!!.frame!!)
    }

    override fun endDraw() {
        cleanup()
        super.endDraw()
    }

    override fun camera(eyeX: Float, eyeY: Float, eyeZ: Float,
                        centerX: Float, centerY: Float, centerZ: Float,
                        upX: Float, upY: Float, upZ: Float) {
        PGraphics.showWarning("The camera cannot be set in AR")
    }

    override fun perspective(fov: Float, aspect: Float, zNear: Float, zFar: Float) {
        PGraphics.showWarning("Perspective cannot be set in AR")
    }

    override fun defaultCamera() {
        // do nothing
    }

    override fun defaultPerspective() {
        // do nothing
    }

    override fun saveState() {}
    override fun restoreState() {}
    override fun restoreSurface() {}

    private fun updateView() {
        if (projMatrix != null && viewMatrix != null) {

            // Fist, set all matrices to identity
            resetProjection()
            resetMatrix()

            // Apply the projection matrix
            applyProjection(projMatrix!![0], projMatrix!![4], projMatrix!![8], projMatrix!![12],
                    projMatrix!![1], projMatrix!![5], projMatrix!![9], projMatrix!![13],
                    projMatrix!![2], projMatrix!![6], projMatrix!![10], projMatrix!![14],
                    projMatrix!![3], projMatrix!![7], projMatrix!![11], projMatrix!![15])

            // make modelview = view
            applyMatrix(viewMatrix!![0], viewMatrix!![4], viewMatrix!![8], viewMatrix!![12],
                    viewMatrix!![1], viewMatrix!![5], viewMatrix!![9], viewMatrix!![13],
                    viewMatrix!![2], viewMatrix!![6], viewMatrix!![10], viewMatrix!![14],
                    viewMatrix!![3], viewMatrix!![7], viewMatrix!![11], viewMatrix!![15])
        }
    }

    fun addTracker(tracker: ARTracker?) {
        trackers.add(tracker!!)
    }

    fun removeTracker(tracker: ARTracker?) {
        trackers.remove(tracker!!)
    }

    fun trackableCount(): Int {
        return trackPlanes.size
    }

    fun trackableId(i: Int): Int {
        return trackIds[trackPlanes[i]]!!
    }

    fun trackableIndex(id: Int): Int {
        return trackIdx[id]!!
    }

    fun trackableType(i: Int): Int {
        val plane = trackPlanes[i]
        if (plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING) {
            return PLANE_FLOOR
        } else if (plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
            return PLANE_CEILING
        } else if (plane.type == Plane.Type.VERTICAL) {
            return PLANE_WALL
        }
        return UNKNOWN
    }

    fun trackableStatus(i: Int): Int {
        val plane = trackPlanes[i]
        if (plane.trackingState == TrackingState.PAUSED) {
            return PAUSED
        } else if (plane.trackingState == TrackingState.TRACKING) {
            return TRACKING
        } else if (plane.trackingState == TrackingState.STOPPED) {
            return STOPPED
        }
        return UNKNOWN
    }

    fun trackableNew(i: Int): Boolean {
        val plane = trackPlanes[i]
        return newPlanes.contains(plane)
    }

    fun trackableSelected(i: Int, mx: Int, my: Int): Boolean {
        val planei = trackPlanes[i]
        for (hit in surfar!!.frame!!.hitTest(mx.toFloat(), my.toFloat())) {
            val trackable = hit.trackable
            if (trackable is Plane) {
                val plane = trackable
                if (planei == plane && plane.isPoseInPolygon(hit.hitPose)) {
                    return true
                }
            }
        }
        return false
    }

    fun getHitResult(mx: Int, my: Int): HitResult? {
        for (hit in surfar!!.frame!!.hitTest(mx.toFloat(), my.toFloat())) {
            val trackable = hit.trackable
            if (trackable is Plane) {
                val plane = trackable
                if (trackPlanes.contains(plane) && plane.isPoseInPolygon(hit.hitPose)) {
                    return hit
                }
            }
        }
        return null
    }

    fun getTrackable(hit: HitResult): Int {
        val plane = hit.trackable as Plane
        return trackPlanes.indexOf(plane)
    }

    fun getTrackablePolygon(i: Int): FloatArray {
        return getTrackablePolygon(i, null)
    }

    fun getTrackablePolygon(i: Int?, points: FloatArray?): FloatArray {
        var points = points
        val plane = trackPlanes[i!!]
        val buffer = plane.polygon
        buffer.rewind()
        if (points == null || points.size < buffer.capacity()) {
            points = FloatArray(buffer.capacity())
        }
        buffer[points, 0, buffer.capacity()]
        return points
    }

    fun getTrackableExtentX(i: Int): Float {
        val plane = trackPlanes[i]
        return plane.extentX
    }

    fun getTrackableExtentZ(i: Int): Float {
        val plane = trackPlanes[i]
        return plane.extentZ
    }

    fun getTrackableMatrix(i: Int): PMatrix3D {
        return getTrackableMatrix(i, null)
    }

    fun getTrackableMatrix(i: Int, target: PMatrix3D?): PMatrix3D {
        var target = target
        if (target == null) {
            target = PMatrix3D()
        }
        val plane = trackPlanes[i]
        val mat = trackMatrices[plane]
        target[mat!![0], mat[4], mat[8], mat[12], mat[1], mat[5], mat[9], mat[13], mat[2], mat[6], mat[10], mat[14], mat[3], mat[7], mat[11]] = mat[15]
        return target
    }

    fun createAnchor(i: Int, x: Float, y: Float, z: Float): Int {
        val plane = trackPlanes[i]
        val planePose = plane.centerPose
        pointIn[0] = x
        pointIn[1] = y
        pointIn[2] = z
        planePose.transformPoint(pointIn, 0, pointOut, 0)
        val anchorPose = Pose.makeTranslation(pointOut)
        val anchor = plane.createAnchor(anchorPose)
        anchors[++lastAnchorId] = anchor
        return lastAnchorId
    }

    fun createAnchor(mx: Int, my: Int): Int {
        for (hit in surfar!!.frame!!.hitTest(mx.toFloat(), my.toFloat())) {
            val trackable = hit.trackable
            if (trackable is Plane) {
                val plane = trackable
                if (trackPlanes.contains(plane) && plane.isPoseInPolygon(hit.hitPose)) {
                    return createAnchor(hit)
                }
            }
        }
        return 0
    }

    fun createAnchor(hit: HitResult): Int {
        val anchor = hit.createAnchor()
        anchors[++lastAnchorId] = anchor
        return lastAnchorId
    }

    fun deleteAnchor(id: Int) {
        delAnchors.add(id)
    }

    fun anchorCount(): Int {
        return anchors.size
    }

    fun anchorStatus(id: Int): Int {
        val anchor = anchors[id]
        if (anchor!!.trackingState == TrackingState.PAUSED) {
            return PAUSED
        } else if (anchor.trackingState == TrackingState.TRACKING) {
            return TRACKING
        } else if (anchor.trackingState == TrackingState.STOPPED) {
            return STOPPED
        }
        return UNKNOWN
    }

    fun getAnchorMatrix(id: Int): PMatrix3D {
        return getAnchorMatrix(id, null)
    }

    fun getAnchorMatrix(id: Int, target: PMatrix3D?): PMatrix3D {
        var target = target
        if (target == null) {
            target = PMatrix3D()
        }
        val anchor = anchors[id]
        anchor!!.pose.toMatrix(anchorMatrix, 0)
        target[anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12], anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13], anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14], anchorMatrix[3], anchorMatrix[7], anchorMatrix[11]] = anchorMatrix[15]
        return target
    }

    fun anchor(id: Int) {
        val anchor = anchors[id]
        anchor!!.pose.toMatrix(anchorMatrix, 0)

        // now, modelview = view * anchor
        applyMatrix(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
                anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
                anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
                anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15])
    }

    fun createBackgroundRenderer() {
        backgroundRenderer = BackgroundRenderer(surfar!!.activity)
    }

    fun setCameraTexture() {
        surfar!!.session!!.setCameraTextureName(backgroundRenderer!!.getTextureId())
    }

    fun updateMatrices() {
        surfar!!.camera!!.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)
        surfar!!.camera!!.getViewMatrix(viewMatrix, 0)
        surfar!!.frame!!.lightEstimate.getColorCorrection(colorCorrection, 0)
    }

    fun updateTrackables() {
        val planes = surfar!!.frame!!.getUpdatedTrackables(Plane::class.java)
        for (plane in planes) {
            if (plane.subsumedBy != null) continue
            var mat: FloatArray?
            if (trackMatrices.containsKey(plane)) {
                mat = trackMatrices[plane]
            } else {
                mat = FloatArray(16)
                trackMatrices[plane] = mat
                trackPlanes.add(plane)
                trackIds[plane] = ++lastTrackableId
                newPlanes.add(plane)
            }
            val pose = plane.centerPose
            pose.toMatrix(mat, 0)
        }

        // Remove stopped and subsummed trackables
        for (i in trackPlanes.indices.reversed()) {
            val plane = trackPlanes[i]
            if (plane.trackingState == TrackingState.STOPPED || plane.subsumedBy != null) {
                trackPlanes.removeAt(i)
                trackMatrices.remove(plane)
                val pid = trackIds.remove(plane)!!
                trackIdx.remove(pid)
                for (t in trackers) t.remove(pid)
            }
        }

        // Update indices
        for (i in trackPlanes.indices) {
            val plane = trackPlanes[i]
            val pid = trackIds[plane]!!
            trackIdx[pid] = i
            if (newPlanes.contains(plane)) {
                for (t in trackers) t.create(i)
            }
        }
    }

    fun cleanup() {
        newPlanes.clear()
        for (id in delAnchors) {
            val anchor = anchors.remove(id)
            anchor!!.detach()
        }
        delAnchors.clear()
    }

    override fun getPolyShader(lit: Boolean, tex: Boolean): PShader {
        if (primaryPG !== this) {
            // An offscreen surface will use the default shaders from the parent OpenGL renderer
            return super.getPolyShader(lit, tex)
        }
        val shader: PShader
        val useDefault = polyShader == null
        return if (lit) {
            if (tex) {
                if (useDefault || !isPolyShaderTexLight(polyShader)) {
                    if (arTexlightShader == null) {
                        arTexlightShader = loadShaderFromURL(arTexlightShaderFragURL, arTexlightShaderVertURL)
                    }
                    shader = arTexlightShader!!
                } else {
                    shader = polyShader
                }
            } else {
                if (useDefault || !isPolyShaderLight(polyShader)) {
                    if (arLightShader == null) {
                        arLightShader = loadShaderFromURL(arLightShaderFragURL, arLightShaderVertURL)
                    }
                    shader = arLightShader!!
                } else {
                    shader = polyShader
                }
            }
            updateShader(shader)
            shader
        } else {
            // Non-lit shaders use the default shaders from the parent OpenGL renderer
            super.getPolyShader(lit, tex)
        }
    }

    override fun updateShader(shader: PShader) {
        super.updateShader(shader)
        shader["colorCorrection", colorCorrection] = 4
    }

    companion object {
        private const val UNKNOWN = -1
        const val PLANE_FLOOR = 0
        const val PLANE_CEILING = 1
        const val PLANE_WALL = 2
        const val POINT = 3
        const val TRACKING = 0
        const val PAUSED = 1
        const val STOPPED = 2
        private var arLightShaderVertURL = PGraphicsOpenGL::class.java.getResource("/assets/shaders/ARLightVert.glsl")
        private var arTexlightShaderVertURL = PGraphicsOpenGL::class.java.getResource("/assets/shaders/ARTexLightVert.glsl")
        private var arLightShaderFragURL = PGraphicsOpenGL::class.java.getResource("/assets/shaders/ARLightFrag.glsl")
        private var arTexlightShaderFragURL = PGraphicsOpenGL::class.java.getResource("/assets/shaders/ARTexLightFrag.glsl")

        val trackables: Array<ARTrackable>?
            get() = null
    }
}