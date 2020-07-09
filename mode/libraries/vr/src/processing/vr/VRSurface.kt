/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
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

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.WindowManager

import com.google.vr.sdk.base.*
import com.google.vr.sdk.base.GvrView.StereoRenderer

import processing.android.AppComponent
import processing.core.PGraphics
import processing.opengl.PGLES
import processing.opengl.PGraphicsOpenGL
import processing.opengl.PSurfaceGLES

import java.io.File
import java.io.InputStream
import javax.microedition.khronos.egl.EGLConfig

open class VRSurface(graphics: PGraphics, component: AppComponent, holder: SurfaceHolder?, vr: Boolean) : PSurfaceGLES() {
    protected var vrView: SurfaceViewVR
    protected var pvr: VRGraphics
    protected var vrActivity: GvrActivity
    protected var VRrenderer: AndroidVRStereoRenderer? = null
    private var needCalculate = false
    override fun getContext(): Context {
        return vrActivity
    }

    override fun getActivity(): Activity {
        return vrActivity
    }

    override fun finish() {
        vrActivity.finish()
    }

    override fun getAssets(): AssetManager {
        return vrActivity.assets
    }

    override fun startActivity(intent: Intent?) {
        vrActivity.startActivity(intent)
    }

    override fun initView(sketchWidth: Int, sketchHeight: Int) {
        val window = vrActivity.window

        // Take up as much area as possible
        //requestWindowFeature(Window.FEATURE_NO_TITLE);  // may need to set in theme properties
        // the above line does not seem to be needed when using VR
        //  android:theme="@android:style/Theme.Holo.NoActionBar.Fullscreen" >
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

        // This does the actual full screen work
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setContentView(vrView)
    }

    override fun getName(): String {
        return vrActivity.componentName.packageName
    }

    override fun setOrientation(which: Int) {
        PGraphics.showWarning("Orientation in VR apps cannot be changed")
    }

    override fun getFilesDir(): File {
        return vrActivity.filesDir
    }

    override fun openFileInput(filename: String?): InputStream? {
        return null
    }

    override fun getFileStreamPath(path: String?): File? {
        return vrActivity.getFileStreamPath(path)
    }

    override fun dispose() {
//    surface.onDestroy();
    }

    ///////////////////////////////////////////////////////////

    // Thread handling
    private var running = false

    override fun startThread() {
        vrView.onResume()
        running = true
    }

    override fun pauseThread() {
        vrView.onPause()
        running = false
    }

    override fun resumeThread() {
        vrView.onResume()
        running = true
    }

    override fun stopThread(): Boolean {
        running = false
        return true
    }

    override fun isStopped(): Boolean {
        return !running
    }

    ///////////////////////////////////////////////////////////

    inner class SurfaceViewVR(context: Context) : GvrView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            return sketch!!.surfaceTouchEvent(event)
        }

        override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
            sketch?.surfaceKeyDown(code, event)
            return super.onKeyDown(code, event)
        }

        override fun onKeyUp(code: Int, event: KeyEvent): Boolean {
            sketch?.surfaceKeyUp(code, event)
            return super.onKeyUp(code, event)
        }

        init {

            // Check if the system supports OpenGL ES 2.0.
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val configurationInfo = activityManager.deviceConfigurationInfo
            val supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000

            if (!supportsGLES2) {
                throw RuntimeException("OpenGL ES 2.0 is not supported by this device.")
            }

            isFocusable = true
            isFocusableInTouchMode = true

            requestFocus()
            val samples = sketch!!.sketchSmooth()

            if (1 < samples) {
                setMultisampling(samples)
            } else {
                // use default EGL config chooser for now
//        setEGLConfigChooser(8, 8, 8, 8, 16, 8);
            }

            // The renderer can be set only once.
            setRenderer(vRStereoRenderer)
        }
    }

    ///////////////////////////////////////////////////////////
    // Android specific classes (Renderer, ConfigChooser)
    val vRStereoRenderer: AndroidVRStereoRenderer
        get() {
            VRrenderer = AndroidVRStereoRenderer()
            return VRrenderer!!
        }

    inner class AndroidVRStereoRenderer : StereoRenderer {
        override fun onNewFrame(transform: HeadTransform) {
            hadnleGVREnumError()
            pgl?.getGL(null)
            pvr.headTransform(transform)
            needCalculate = true
        }

        override fun onDrawEye(eye: Eye) {
            pvr.eyeTransform(eye)
            if (needCalculate) {
                // Call calculate() right after we have the first eye transform.
                // This allows to update the modelview and projection matrices, so
                // geometry-related calculations can also be conducted in calculate().
                pvr.updateView()
                sketch?.calculate()
                needCalculate = false
            }
            sketch?.handleDraw()
        }

        override fun onFinishFrame(arg0: Viewport) {

        }

        override fun onRendererShutdown() {

        }

        override fun onSurfaceChanged(iwidth: Int, iheight: Int) {
            sketch?.surfaceChanged()
            graphics?.surfaceChanged()
            sketch?.setSize(iwidth, iheight)
            graphics?.setSize(sketch!!.sketchWidth(), sketch!!.sketchHeight())
        }

        override fun onSurfaceCreated(arg0: EGLConfig) {}

        // Don't print the invalid enum error:
        // https://github.com/processing/processing-android/issues/281
        // seems harmless as it happens in the first frame only
        // TODO: need to find the reason for the error (gl config?)
        private fun hadnleGVREnumError() {
            val err = pgl?.error
            if (err != 0 && err != 1280) {
                val where = "top onNewFrame"
                val errString = err?.let { pgl?.errorString(it) }
                val msg = "OpenGL error $err at $where: $errString"
                PGraphics.showWarning(msg)
            }
        }
    }

    // constructor or initializer block of VRSurface class
    init {
        sketch = graphics.parent
        this.graphics = graphics
        this.appcomponent = component

        pgl = (graphics as PGraphicsOpenGL).pgl as PGLES
        vrActivity = component as GvrActivity
        appactivity = vrActivity
        pvr = graphics as VRGraphics
        vrView = SurfaceViewVR(vrActivity)

        // Enables/disables the transition view used to prompt the user to place
        // their phone into a GVR viewer.
        vrView.setTransitionViewEnabled(true)

        // Enables Cardboard-trigger feedback with Daydream headsets. This is a simple way of supporting
        // Daydream controller input for basic interactions using the existing Cardboard trigger API.
        vrView.enableCardboardTriggerEmulation()
        vrView.stereoModeEnabled = vr

        if (vr) {
            vrView.distortionCorrectionEnabled = true
            vrView.setNeckModelEnabled(true)
        }

        if (vrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(vrActivity, true)
        }
        vrActivity.gvrView = vrView
        msurfaceView = null

        // The glview is ready right after creation, does not need to wait for a
        // surfaceCreate() event.
        surfaceReady = true
    }
}