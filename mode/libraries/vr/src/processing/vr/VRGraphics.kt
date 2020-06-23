/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-19 The Processing Foundation

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
package processing.vr

import com.google.vr.sdk.base.Eye
import com.google.vr.sdk.base.HeadTransform
import com.google.vr.sdk.base.Viewport
import processing.core.PApplet
import processing.core.PConstants
import processing.core.PGraphics
import processing.opengl.PGL
import processing.opengl.PGLES
import processing.opengl.PGraphics3D
import processing.opengl.PGraphicsOpenGL

open class VRGraphics : PGraphics3D() {
    protected var isInitialized = false

    var headTransform: HeadTransform? = null
    var eye: Eye? = null
    var eyeType = 0

    private var forwardVector: FloatArray = FloatArray(3)
    private var rightVector: FloatArray = FloatArray(3)
    private var upVector: FloatArray = FloatArray(3)
    private var eyeViewport: Viewport? = null
    private var eyeView: FloatArray = FloatArray(16)
    private var eyePerspective: FloatArray = FloatArray(16)

    override fun createPGL(pg: PGraphicsOpenGL): PGL {
        return PGLES(pg)
    }

    override fun beginDraw() {
        super.beginDraw()
        updateView()
    }

    override fun camera(eyeX: Float, eyeY: Float, eyeZ: Float,
                        centerX: Float, centerY: Float, centerZ: Float,
                        upX: Float, upY: Float, upZ: Float) {
        PGraphics.showWarning("The camera cannot be set in VR")
    }

    override fun perspective(fov: Float, aspect: Float, zNear: Float, zFar: Float) {
        PGraphics.showWarning("Perspective cannot be set in VR")
    }

    override fun defaultCamera() {
        // do nothing
    }

    override fun defaultPerspective() {
        // do nothing
    }

    override fun saveState() {

    }

    override fun restoreState() {

    }

    override fun restoreSurface() {

    }

    fun updateView() {
        setVRViewport()
        setVRCamera()
        setVRProjection()
    }

    fun eyeTransform(e: Eye?) {
        eye = e
        eyeType = eye!!.type
        eyeViewport = eye!!.viewport
        eyePerspective = eye!!.getPerspective(defCameraNear, defCameraFar)
        eyeView = eye!!.eyeView

        // Adjust the camera Z position to it fits the (width,height) rect at Z = 0, given
        // the fov settings.
        val fov = eye!!.fov
        defCameraFOV = fov.top * PConstants.DEG_TO_RAD
        defCameraZ = (height / (2 * Math.tan(defCameraFOV.toDouble()))).toFloat()
        cameraAspect = width.toFloat() / height
        if (cameraUp) {
            defCameraX = 0f
            defCameraY = 0f
        } else {
            defCameraX = +width / 2.0f
            defCameraY = +height / 2.0f
        }
    }

    fun headTransform(ht: HeadTransform?) {
        initVR()
        headTransform = ht

        // Forward, right, and up vectors are given in the original system with Y
        // pointing up. Need to invert y coords in the non-gl case:
        val yf = if (cameraUp) (+1).toFloat() else -1.toFloat()
        headTransform!!.getForwardVector(forwardVector, 0)
        headTransform!!.getRightVector(rightVector, 0)
        headTransform!!.getUpVector(upVector, 0)
        forwardX = forwardVector[0]
        forwardY = yf * forwardVector[1]
        forwardZ = forwardVector[2]
        rightX = rightVector[0]
        rightY = yf * rightVector[1]
        rightZ = rightVector[2]
        upX = upVector[0]
        upY = yf * upVector[1]
        upZ = upVector[2]
    }

    protected fun initVR() {
        if (!isInitialized) {
            forwardVector = FloatArray(3)
            rightVector = FloatArray(3)
            upVector = FloatArray(3)
            isInitialized = true
        }
    }

    protected fun setVRViewport() {
        pgl.viewport(eyeViewport!!.x, eyeViewport!!.y, eyeViewport!!.width, eyeViewport!!.height)
    }

    protected fun setVRCamera() {
        cameraX = defCameraX
        cameraY = defCameraY
        cameraZ = defCameraZ

        // Calculating Z vector
        var z0 = 0f
        var z1 = 0f
        var z2 = defCameraZ
        eyeDist = PApplet.abs(z2)
        if (PGraphicsOpenGL.nonZero(eyeDist)) {
            z0 /= eyeDist
            z1 /= eyeDist
            z2 /= eyeDist
        }

        // Calculating Y vector
        var y0 = 0f
        var y1 = if (cameraUp) (+1).toFloat() else -1.toFloat()
        var y2 = 0f

        // Computing X vector as Y cross Z
        var x0 = y1 * z2 - y2 * z1
        var x1 = -y0 * z2 + y2 * z0
        var x2 = y0 * z1 - y1 * z0
        if (!cameraUp) {
            // Inverting X axis
            x0 *= -1f
            x1 *= -1f
            x2 *= -1f
        }

        // Cross product gives area of parallelogram, which is < 1.0 for
        // non-perpendicular unit-length vectors; so normalize x, y here:
        val xmag = PApplet.sqrt(x0 * x0 + x1 * x1 + x2 * x2)
        if (PGraphicsOpenGL.nonZero(xmag)) {
            x0 /= xmag
            x1 /= xmag
            x2 /= xmag
        }
        val ymag = PApplet.sqrt(y0 * y0 + y1 * y1 + y2 * y2)
        if (PGraphicsOpenGL.nonZero(ymag)) {
            y0 /= ymag
            y1 /= ymag
            y2 /= ymag
        }

        // Pre-apply the eye view matrix:
        // https://developers.google.com/vr/android/reference/com/google/vr/sdk/base/Eye.html#getEyeView()
        modelview[eyeView[0], eyeView[4], eyeView[8], eyeView[12], eyeView[1], eyeView[5], eyeView[9], eyeView[13], eyeView[2], eyeView[6], eyeView[10], eyeView[14], eyeView[3], eyeView[7], eyeView[11]] = eyeView[15]
        modelview.apply(x0, x1, x2, 0f,
                y0, y1, y2, 0f,
                z0, z1, z2, 0f, 0f, 0f, 0f, 1f)
        val tx = -defCameraX
        val ty = -defCameraY
        val tz = -defCameraZ
        modelview.translate(tx, ty, tz)
        modelviewInv.set(modelview)
        modelviewInv.invert()
        camera.set(modelview)
        cameraInv.set(modelviewInv)
    }

    protected fun setVRProjection() {
        // Matrices in Processing are row-major, and GVR API is column-major
        projection[eyePerspective[0], eyePerspective[4], eyePerspective[8], eyePerspective[12], eyePerspective[1], eyePerspective[5], eyePerspective[9], eyePerspective[13], eyePerspective[2], eyePerspective[6], eyePerspective[10], eyePerspective[14], eyePerspective[3], eyePerspective[7], eyePerspective[11]] = eyePerspective[15]
        updateProjmodelview()
    }

    companion object {
        const val LEFT = Eye.Type.LEFT
        const val RIGHT = Eye.Type.RIGHT
        const val MONOCULAR = Eye.Type.MONOCULAR
    }
}