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

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.*

import com.google.ar.core.*
import com.google.ar.core.exceptions.*

import processing.android.AppComponent
import processing.core.PGraphics
import processing.opengl.PGLES
import processing.opengl.PGraphicsOpenGL
import processing.opengl.PSurfaceGLES

import java.io.File
import java.io.InputStream

import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ARSurface(graphics: PGraphics?, appComponent: AppComponent?, surfaceHolder: SurfaceHolder?) : PSurfaceGLES(graphics, appComponent, surfaceHolder) {
    // Made these public so they can be accessed from the sketch
    @JvmField
    var session: Session? = null
    @JvmField
    var frame: Frame? = null
    @JvmField
    var camera: Camera? = null

    private var arsurfaceView: GLSurfaceView? = null
    private var ARrenderer: AndroidARRenderer? = null
    private var par: ARGraphics
    private var displayRotationHelper: RotationHandler

    override fun getContext(): Context {
        return activity
    }

    override fun finish() {
        sketch.activity.finish()
    }

    override fun getAssets(): AssetManager {
        return sketch.context.assets
    }

    override fun startActivity(intent: Intent) {
        sketch.context.startActivity(intent)
    }

    override fun initView(sketchWidth: Int, sketchHeight: Int) {
        val window = sketch.activity.window
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setContentView(arsurfaceView)
    }

    override fun getName(): String {
        return sketch.activity.componentName.packageName
    }

    override fun setOrientation(which: Int) {}
    override fun getFilesDir(): File {
        return sketch.activity.filesDir
    }

    override fun openFileInput(filename: String): InputStream? {
        return null
    }

    override fun getFileStreamPath(path: String): File {
        return sketch.activity.getFileStreamPath(path)
    }

    override fun dispose() {}

    inner class SurfaceViewAR(context: Context) : GLSurfaceView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            return sketch.surfaceTouchEvent(event)
        }

        override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
            sketch.surfaceKeyDown(code, event)
            return super.onKeyDown(code, event)
        }

        override fun onKeyUp(code: Int, event: KeyEvent): Boolean {
            sketch.surfaceKeyUp(code, event)
            return super.onKeyUp(code, event)
        }

        init {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val configurationInfo = activityManager.deviceConfigurationInfo
            val supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000
            if (!supportsGLES2) {
                throw RuntimeException("OpenGL ES 2.0 is not supported by this device.")
            }
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(aRRenderer)
            renderMode = RENDERMODE_CONTINUOUSLY
        }
    }

    val aRRenderer: AndroidARRenderer
        get() {
            ARrenderer = AndroidARRenderer()
            return ARrenderer!!
        }

    inner class AndroidARRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            pgl?.getGL(null)
            par.createBackgroundRenderer()
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            displayRotationHelper.onSurfaceChanged(width, height)
            GLES20.glViewport(0, 0, width, height)
            sketch.surfaceChanged()
            graphics.surfaceChanged()
            sketch.setSize(width, height)
            graphics.setSize(sketch.sketchWidth(), sketch.sketchHeight())
        }

        override fun onDrawFrame(gl: GL10) {
            if (session == null) return
            displayRotationHelper.updateSessionIfNeeded(session!!)
            try {
                par.setCameraTexture()
                frame = session!!.update()
                camera = frame!!.camera
                if (camera!!.trackingState == TrackingState.TRACKING) par.updateTrackables()
                par.updateMatrices()
                sketch.calculate()
                sketch.handleDraw()
            } catch (tr: Throwable) {
                PGraphics.showWarning("An error occurred in ARCORE: " + tr.message)
            }
        }
    }

    override fun startThread() {}
    override fun pauseThread() {
        if (session != null) {
            displayRotationHelper.onPause()
            arsurfaceView!!.onPause()
            session!!.pause()
        }
    }

    override fun resumeThread() {
        if (!sketch.hasPermission("android.permission.CAMERA")) return
        if (session == null) {
            var message: String? = null
            var exception: String? = null
            try {
                // Perhaps this should be done in the MainActivity?
                // https://github.com/google-ar/arcore-android-sdk/blob/master/samples/hello_ar_java/app/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java
                when (ArCoreApk.getInstance().requestInstall(sketch.activity, true)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        message(T_ALERT_MESSAGE, C_NOT_SUPPORTED)
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> {
                    }
                }
                session = Session(activity)
            } catch (e: UnavailableArcoreNotInstalledException) {
                message = C_EXCEPT_INSTALL
                exception = e.toString()
            } catch (e: UnavailableUserDeclinedInstallationException) {
                message = C_EXCEPT_INSTALL
                exception = e.toString()
            } catch (e: UnavailableApkTooOldException) {
                message = C_EXCEPT_UPDATE_SDK
                exception = e.toString()
            } catch (e: UnavailableSdkTooOldException) {
                message = C_EXCEPT_UPDATE_APP
                exception = e.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                println("That's that")
            }
            if (message != null) {
                message(T_ALERT_MESSAGE, "$message -- $exception")
            }
            val config = Config(session)
            if (!session!!.isSupported(config)) {
                message(T_PROMPT_MESSAGE, C_DEVICE)
            }
            session!!.configure(config)
        }
        try {
            session!!.resume()
        } catch (e: CameraNotAvailableException) {
        }
        arsurfaceView!!.onResume()
        displayRotationHelper.onResume()
    }

    fun message(_title: String, _message: String) {
        val parent = activity
        parent.runOnUiThread {
            AlertDialog.Builder(parent)
                    .setTitle(_title)
                    .setMessage(_message)
                    .setPositiveButton("OK"
                    ) { dialog, which -> }.show()
        }
    }

    companion object {
        private const val T_ALERT_MESSAGE = "ALERT"
        private const val C_NOT_SUPPORTED = "ARCore SDK required to run this app type"
        private const val T_PROMPT_MESSAGE = "PROMPT"
        private const val C_SUPPORTED = "ARCore SDK is installed"
        private const val C_EXCEPT_INSTALL = "Please install ARCore"
        private const val C_EXCEPT_UPDATE_SDK = "Please update ARCore"
        private const val C_EXCEPT_UPDATE_APP = "Please update this app"
        private const val C_DEVICE = "This device does not support AR"
    }

    init {
        sketch = graphics!!.parent
        this.graphics = graphics!!
        component = appComponent
        pgl = (graphics!! as PGraphicsOpenGL).pgl as PGLES
        par = graphics!! as ARGraphics
        displayRotationHelper = RotationHandler(activity)
        arsurfaceView = SurfaceViewAR(activity)
    }
}