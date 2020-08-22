/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

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

package processing.a2d

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.service.wallpaper.WallpaperService
import android.support.wearable.watchface.CanvasWatchFaceService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import processing.android.AppComponent
import processing.android.PFragment
import processing.core.PApplet
import processing.core.PGraphics
import processing.core.PSurfaceNone

/**
 * @author Aditya Rana
 */
open class PSurfaceAndroid2D : PSurfaceNone {

    constructor() {

    }

    constructor(graphics: PGraphics, component: AppComponent?, holder: SurfaceHolder?) {
        sketch = graphics.parent
        this.graphics = graphics
        this.appcomponent = component
        if (component!!.getKind() == AppComponent.FRAGMENT) {
            val frag = component as PFragment
            appactivity = frag.getActivity()
            msurfaceView = SurfaceViewAndroid2D(appactivity, null)
        } else if (component!!.getKind() == AppComponent.WALLPAPER) {
            wallpaper = component as WallpaperService
            msurfaceView = SurfaceViewAndroid2D(wallpaper, holder)
        } else if (component!!.getKind() == AppComponent.WATCHFACE) {
            watchface = component as CanvasWatchFaceService
            msurfaceView = null
            // Set as ready here, as watch faces don't have a surface view with a
            // surfaceCreate() event to do it.
            surfaceReady = true
        }
    }

    ///////////////////////////////////////////////////////////

    // SurfaceView


    inner class SurfaceViewAndroid2D(context: Context?, private var holder: SurfaceHolder?) : SurfaceView(context), SurfaceHolder.Callback {
        override fun getHolder(): SurfaceHolder? {
            return if (holder == null) {
                super.getHolder()
            } else {
                holder
            }
        }

        // part of SurfaceHolder.Callback
        override fun surfaceCreated(holder: SurfaceHolder) {
            surfaceReady = true
            if (requestedThreadStart) {
                // Only start the thread once the surface has been created, otherwise it will not be able to draw
                startThread()
            }
            if (PApplet.DEBUG) {
                println("surfaceCreated()")
            }
        }

        // part of SurfaceHolder.Callback
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (PApplet.DEBUG) {
                println("surfaceDestroyed()")
            }
        }

        // part of SurfaceHolder.Callback
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, iwidth: Int, iheight: Int) {
            if (PApplet.DEBUG) {
                println("SketchSurfaceView.surfaceChanged() $iwidth $iheight")
            }
            sketch?.surfaceChanged()
            sketch?.setSize(iwidth, iheight)
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            sketch?.surfaceWindowFocusChanged(hasFocus)
        }

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

        // constructor or initializer block
        init {

//    println("surface holder");
            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed
            val h = getHolder()
            h!!.addCallback(this)
            //    surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU); // no longer needed.

//    println("setting focusable, requesting focus");
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            //    println("done making surface view");
            surfaceReady = false // Will be ready when the surfaceCreated() event is called

            // Solves screen flickering:
            // https://github.com/processing/processing-android/issues/570
            setBackgroundColor(Color.argb(0, 0, 0, 0))
            getHolder()!!.setFormat(PixelFormat.TRANSPARENT)
        }
    }
}