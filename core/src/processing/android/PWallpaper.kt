/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-17 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.android

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import processing.android.CompatUtils.getDisplayParams
import android.service.wallpaper.WallpaperService
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.SurfaceHolder
import android.view.MotionEvent
import processing.core.PApplet

open class PWallpaper : WallpaperService(), AppComponent {
    private var size: Point? = null
    private var metrics: DisplayMetrics? = null
    private var engine: WallpaperEngine? = null

    override fun initDimensions() {
        metrics = DisplayMetrics()
        size = Point()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        getDisplayParams(display, metrics, size!!)
    }

    override fun getDisplayWidth(): Int {
        return size!!.x
    }

    override fun getDisplayHeight(): Int {
        return size!!.y
    }

    override fun getDisplayDensity(): Float {
        return metrics!!.density
    }

    override fun getKind(): Int {
        return AppComponent.WALLPAPER
    }

    open fun createSketch(): PApplet {
        return PApplet()
    }

    override fun setSketch(sketch: PApplet?) {
        engine!!.sketch = sketch
    }

    override fun getSketch(): PApplet? {
        return engine!!.sketch
    }

    override fun isService(): Boolean {
        return true
    }

    override fun getEngine(): ServiceEngine? {
        return engine
    }

    override fun requestDraw() {

    }
    override fun canDraw(): Boolean {
        return true
    }

    override fun dispose() {

    }

    fun requestPermissions() {

    }

    override fun onCreateEngine(): Engine {
        engine = WallpaperEngine()
        return engine!!
    }

    override fun onDestroy() {
        super.onDestroy()
        if (engine != null) {
            //engine.sketch = null;
            engine!!.onDestroy()
        }
    }

    inner class WallpaperEngine : Engine(), ServiceEngine {
        var sketch: PApplet? = null
        private var xOffset = 0f
        private var xOffsetStep = 0f
        private var yOffset = 0f
        private var yOffsetStep = 0f
        private var xPixelOffset = 0
        private var yPixelOffset = 0

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            sketch = createSketch()
            sketch!!.initSurface(this@PWallpaper, getSurfaceHolder())
            if (isPreview) requestPermissions()
            setTouchEventsEnabled(true)
        }

        override fun onSurfaceCreated(surfaceHolder: SurfaceHolder) {
            super.onSurfaceCreated(surfaceHolder)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int,
                                      width: Int, height: Int) {
            // When the surface of a live wallpaper changes (eg: after a screen rotation) the same sketch
            // continues to run (unlike the case of regular apps, where its re-created) so we need to
            // force a reset of the renderer so the backing FBOs (in the case of the OpenGL renderers)
            // get reinitalized with the correct size.
            sketch!!.g.reset()
            super.onSurfaceChanged(holder, format, width, height)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (sketch != null) {
                if (visible) {
                    sketch!!.onResume()
                } else {
                    sketch!!.onPause()
                }
            }
            super.onVisibilityChanged(visible)
        }

        /*
     * Store the position of the touch event so we can use it for drawing
     * later
     */
        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            if (sketch != null) {
                sketch!!.surfaceTouchEvent(event)
            }
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float,
                                      xOffsetStep: Float, yOffsetStep: Float,
                                      xPixelOffset: Int, yPixelOffset: Int) {
            if (sketch != null) {
                this.xOffset = xOffset
                this.yOffset = yOffset
                this.xOffsetStep = xOffsetStep
                this.yOffsetStep = yOffsetStep
                this.xPixelOffset = xPixelOffset
                this.yPixelOffset = yPixelOffset
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            // This is called immediately before a surface is being destroyed.
            // After returning from this call, you should no longer try to access this
            // surface. If you have a rendering thread that directly accesses the
            // surface, you must ensure that thread is no longer touching the Surface
            // before returning from this function.
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            super.onDestroy()
            if (sketch != null) {
                sketch!!.onDestroy()
            }
        }

        override fun getXOffset(): Float {
            return xOffset
        }

        override fun getYOffset(): Float {
            return yOffset
        }

        override fun getXOffsetStep(): Float {
            return xOffsetStep
        }

        override fun getYOffsetStep(): Float {
            return yOffsetStep
        }

        override fun getXPixelOffset(): Int {
            return xPixelOffset
        }

        override fun getYPixelOffset(): Int {
            return yPixelOffset
        }

        override fun isInAmbientMode(): Boolean {
            return false
        }

        override fun isRound(): Boolean {
            return false
        }

        override fun getInsets(): Rect? {
            return null
        }

        override fun useLowBitAmbient(): Boolean {
            return false
        }

        override fun requireBurnInProtection(): Boolean {
            return false
        }

        override fun onRequestPermissionsResult(requestCode: Int,
                                                permissions: Array<String?>?,
                                                grantResults: IntArray?) {
            if (sketch != null) {
                sketch!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }
}