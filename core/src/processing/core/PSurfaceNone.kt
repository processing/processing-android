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

package processing.core

import android.app.Activity

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager

import android.graphics.Rect

import android.os.Bundle
import android.os.Handler
import android.os.Looper

import android.service.wallpaper.WallpaperService
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.os.ResultReceiver
import android.support.wearable.watchface.WatchFaceService

import android.view.*
import android.widget.LinearLayout
import android.widget.RelativeLayout

import processing.android.AppComponent
import processing.android.PFragment
import processing.android.PermissionRequestor
import processing.android.ServiceEngine

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * @author Aditya Rana
 * Base surface for Android2D and OpenGL renderers.
 * It includes the implementation of the rendering thread.
 */
open class PSurfaceNone : PSurface, PConstants {

    protected var sketch: PApplet? = null

    protected var graphics: PGraphics? = null

    protected var appcomponent: AppComponent? = null

    protected var appactivity: Activity? = null

    protected var surfaceReady = false

    protected var msurfaceView: SurfaceView? = null

    protected var view: View? = null

    protected var wallpaper: WallpaperService? = null
    protected var watchface: WatchFaceService? = null

    protected var requestedThreadStart = false

    protected var thread: Thread? = null
    protected var paused = false

    // using Object() instead of Any() here as we are using object.wait() and object.notifyAll() which are not supported by Any()
    protected var pauseObject = Object()

    protected var frameRateTarget = 60f
    protected var frameRatePeriod = 1000000000L / 60L

    override fun getComponent(): AppComponent? {
        return appcomponent
    }

    override fun getContext(): Context? {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            return appactivity
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            return wallpaper
        } else if (appcomponent!!.getKind() == AppComponent.WATCHFACE) {
            return watchface
        }
        return null
    }

    override fun getActivity(): Activity? {
        return appactivity
    }

    override fun getEngine(): ServiceEngine? {
        return appcomponent!!.getEngine()
    }

    override fun getRootView(): View? {
        return view
    }

    override fun getName(): String? {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            return appactivity!!.componentName.packageName
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            return wallpaper!!.packageName
        } else if (appcomponent!!.getKind() == AppComponent.WATCHFACE) {
            return watchface!!.packageName
        }
        return ""
    }

    override fun getResource(id: Int): View? {
        return appactivity!!.findViewById(id)
    }

    override fun getVisibleFrame(): Rect? {
        val frame = Rect()
        if (view != null) {
            // According to the docs:
            // https://developer.android.com/reference/android/view/View.html#getWindowVisibleDisplayFrame(android.graphics.Rect)
            // don't use in performance critical code like drawing.
            view!!.getWindowVisibleDisplayFrame(frame)
        }
        return frame
    }

    override fun dispose() {
        sketch = null
        graphics = null
        if (appactivity != null) {
            // In API level 21 you can do
            // activity.releaseInstance();
            // to ask the app to free up its memory.
            // https://developer.android.com/reference/android/app/Activity.html#releaseInstance()
            // but seems redundant to call it here, since dispose() is triggered by
            // the onDestroy() handler, which means that the app is already
            // being destroyed.
        }
        if (view != null) {
            view!!.destroyDrawingCache()
        }
        if (appcomponent != null) {
            appcomponent!!.dispose()
        }
        if (msurfaceView != null) {
            msurfaceView!!.holder.surface.release()
        }
    }

    override fun setRootView(view: View?) {
        this.view = view
    }

    override fun getSurfaceView(): SurfaceView? {
        return msurfaceView
    }

    // TODO this is only used by A2D, when finishing up a draw. but if the
    // surfaceview has changed, then it might belong to an a3d surfaceview. hrm.
    override fun getSurfaceHolder(): SurfaceHolder? {
        val view = msurfaceView
        return view?.holder
    }

    override fun initView(sketchWidth: Int, sketchHeight: Int) {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            val displayWidth = appcomponent!!.getDisplayWidth()
            val displayHeight = appcomponent!!.getDisplayHeight()
            val rootView: View?
            rootView = if (sketchWidth == displayWidth && sketchHeight == displayHeight) {
                msurfaceView
            } else {
                val overallLayout = RelativeLayout(appactivity)
                val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.addRule(RelativeLayout.CENTER_IN_PARENT)
                val layout = LinearLayout(appactivity)
                layout.addView(msurfaceView, sketchWidth, sketchHeight)
                overallLayout.addView(layout, lp)
                overallLayout.setBackgroundColor(sketch!!.sketchWindowColor())
                overallLayout
            }
            setRootView(rootView)
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            setRootView(msurfaceView)
        }
    }

    override fun initView(sketchWidth: Int, sketchHeight: Int, parentSize: Boolean,
                          inflater: LayoutInflater?, container: ViewGroup?,
                          savedInstanceState: Bundle?) {
        // https://www.bignerdranch.com/blog/understanding-androids-layoutinflater-inflate/
        val rootView = inflater!!.inflate(sketch!!.parentLayout, container, false) as ViewGroup
        val view: View? = msurfaceView
        if (parentSize) {
            val lp: LinearLayout.LayoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT)
            lp.weight = 1.0f
            lp.setMargins(0, 0, 0, 0)
            view!!.setPadding(0, 0, 0, 0)
            rootView.addView(view, lp)
        } else {
            val layout = RelativeLayout(appactivity)
            val lp = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.addRule(RelativeLayout.CENTER_IN_PARENT)
            layout.addView(view, sketchWidth, sketchHeight)
            rootView.addView(layout, lp)
        }
        rootView.setBackgroundColor(sketch!!.sketchWindowColor())
        setRootView(rootView)
    }

    override fun startActivity(intent: Intent?) {
        appcomponent!!.startActivity(intent)
    }

    override fun runOnUiThread(action: Runnable?) {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            appactivity!!.runOnUiThread(action)
        }
    }

    override fun setOrientation(which: Int) {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            if (which == PConstants.PORTRAIT) {
                appactivity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (which == PConstants.LANDSCAPE) {
                appactivity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    override fun setHasOptionsMenu(hasMenu: Boolean) {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            (appcomponent as PFragment?)!!.setHasOptionsMenu(hasMenu)
        }
    }

    override fun getFilesDir(): File? {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            return appactivity!!.filesDir
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            return wallpaper!!.filesDir
        } else if (appcomponent!!.getKind() == AppComponent.WATCHFACE) {
            return watchface!!.filesDir
        }
        return null
    }

    override fun getFileStreamPath(path: String?): File? {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            return appactivity!!.getFileStreamPath(path)
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            return wallpaper!!.getFileStreamPath(path)
        } else if (appcomponent!!.getKind() == AppComponent.WATCHFACE) {
            return watchface!!.getFileStreamPath(path)
        }
        return null
    }

    override fun openFileInput(filename: String?): InputStream? {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            try {
                return appactivity!!.openFileInput(filename)
            } catch (e: FileNotFoundException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }
        }
        return null
    }

    override fun getAssets(): AssetManager? {
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            return appactivity!!.assets
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            return wallpaper!!.baseContext.assets
        } else if (appcomponent!!.getKind() == AppComponent.WATCHFACE) {
            return watchface!!.baseContext.assets
        }
        return null
    }

    override fun setSystemUiVisibility(visibility: Int) {
        val kind = appcomponent!!.getKind()
        if (kind == AppComponent.FRAGMENT || kind == AppComponent.WALLPAPER) {
            msurfaceView!!.systemUiVisibility = visibility
        }
    }

    override fun finish() {
        if (appcomponent == null) return
        if (appcomponent!!.getKind() == AppComponent.FRAGMENT) {
            // This is the correct way to stop the sketch programmatically, according to the developer's docs:
            // https://developer.android.com/reference/android/app/Activity.html#onDestroy()
            // https://developer.android.com/reference/android/app/Activity.html#finish()
            // and online discussions:
            // http://stackoverflow.com/questions/2033914/quitting-an-application-is-that-frowned-upon/2034238
            // finish() it will trigger an onDestroy() event, which will translate down through the
            // activity hierarchy and eventually pausing and stopping Processing's animation thread, etc.
            appactivity!!.finish()
        } else if (appcomponent!!.getKind() == AppComponent.WALLPAPER) {
            // stopSelf() stops a service from within:
            // https://developer.android.com/reference/android/app/Service.html#stopSelf()
            wallpaper!!.stopSelf()
        } else if (appcomponent!!.getKind() == AppComponent.WATCHFACE) {
            watchface!!.stopSelf()
        }
    }

    ///////////////////////////////////////////////////////////

    // Thread handling


    fun createThread(): Thread {
        return AnimationThread()
    }

    override fun startThread() {
        if (!surfaceReady) {
            requestedThreadStart = true
            return
        }
        if (thread == null) {
            thread = createThread()
            thread!!.start()
            requestedThreadStart = false
        } else {
            throw IllegalStateException("Thread already started in " +
                    javaClass.simpleName)
        }
    }

    override fun pauseThread() {
        if (!surfaceReady) return
        paused = true
    }

    override fun resumeThread() {
        if (!surfaceReady) return
        if (thread == null) {
            thread = createThread()
            thread!!.start()
        }
        paused = false
        synchronized(pauseObject) {
            pauseObject.notifyAll() // wake up the animation thread
        }
    }

    override fun stopThread(): Boolean {
        if (!surfaceReady) return true
        if (thread == null) {
            return false
        }
        thread!!.interrupt()
        thread = null
        return true
    }

    override fun isStopped(): Boolean {
        return thread == null
    }

    override fun setFrameRate(fps: Float) {
        frameRateTarget = fps
        frameRatePeriod = (1000000000.0 / frameRateTarget).toLong()
    }

    @Throws(InterruptedException::class)
    protected fun checkPause() {
        synchronized(pauseObject) {
            while (paused) {
                pauseObject.wait()
            }
        }
    }

    protected open fun callDraw() {
        appcomponent!!.requestDraw()
        if (appcomponent!!.canDraw() && sketch != null) {
            sketch!!.handleDraw()
        }
    }

    inner class AnimationThread : Thread("Animation Thread") {

        /**
         * Main method for the primary animation thread.
         * <A HREF="http://java.sun.com/products/jfc/tsc/articles/painting/">Painting in AWT and Swing</A>
         */
        override fun run() {  // not good to make this synchronized, locks things up
            var beforeTime = System.nanoTime()
            var overSleepTime = 0L
            var noDelays = 0
            // Number of frames with a delay of 0 ms before the
            // animation thread yields to other running threads.
            val NO_DELAYS_PER_YIELD = 15
            if (sketch == null) return

            // un-pause the sketch and get rolling
            sketch!!.start()
            while (currentThread() === thread &&
                    sketch != null && !sketch!!.finished) {
                if (currentThread().isInterrupted) {
                    return
                }
                try {
                    checkPause()
                } catch (e: InterruptedException) {
                    return
                }
                callDraw()

                // wait for update & paint to happen before drawing next frame
                // this is necessary since the drawing is sometimes in a
                // separate thread, meaning that the next frame will start
                // before the update/paint is completed
                val afterTime = System.nanoTime()
                val timeDiff = afterTime - beforeTime
                //System.out.println("time diff is " + timeDiff);
                val sleepTime = frameRatePeriod - timeDiff - overSleepTime
                if (sleepTime > 0) {  // some time left in this cycle
                    try {
                        sleep(sleepTime / 1000000L, (sleepTime % 1000000L).toInt())
                        noDelays = 0 // Got some sleep, not delaying anymore
                    } catch (ex: InterruptedException) {
                    }
                    overSleepTime = System.nanoTime() - afterTime - sleepTime
                } else {    // sleepTime <= 0; the frame took longer than the period
                    overSleepTime = 0L
                    noDelays++
                    if (noDelays > NO_DELAYS_PER_YIELD) {
                        yield() // give another thread a chance to run
                        noDelays = 0
                    }
                }
                beforeTime = System.nanoTime()
            }
        }
    }

    override fun hasPermission(permission: String?): Boolean {
        val res = ContextCompat.checkSelfPermission(getContext()!!, permission!!)
        return res == PackageManager.PERMISSION_GRANTED
    }

    override fun requestPermissions(permissions: Array<String?>?) {
        if (appcomponent!!.isService()) {
            // https://developer.android.com/training/articles/wear-permissions.html
            // Inspired by PermissionHelper.java from Michael von Glasow:
            // https://github.com/mvglasow/satstat/blob/master/src/com/vonglasow/michael/satstat/utils/PermissionHelper.java
            // Example of use:
            // https://github.com/mvglasow/satstat/blob/master/src/com/vonglasow/michael/satstat/PasvLocListenerService.java
            val eng = getEngine()
            if (eng != null) { // A valid service should have a non-null engine at this point, but just in case
                val resultReceiver: ResultReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                        val outPermissions = resultData.getStringArray(PermissionRequestor.KEY_PERMISSIONS)
                        val grantResults = resultData.getIntArray(PermissionRequestor.KEY_GRANT_RESULTS)
                        eng.onRequestPermissionsResult(resultCode, outPermissions, grantResults)
                    }
                }
                val permIntent = Intent(getContext(), PermissionRequestor::class.java)
                permIntent.putExtra(PermissionRequestor.KEY_RESULT_RECEIVER, resultReceiver)
                permIntent.putExtra(PermissionRequestor.KEY_PERMISSIONS, permissions)
                permIntent.putExtra(PermissionRequestor.KEY_REQUEST_CODE, PSurface.REQUEST_PERMISSIONS)
                // Show the dialog requesting the permissions
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(permIntent)
            }
        } else if (appactivity != null) {
            // Requesting permissions from user when the app resumes.
            // Nice example on how to handle user response
            // http://stackoverflow.com/a/35495855
            // More on permission in Android 23:
            // https://inthecheesefactory.com/blog/things-you-need-to-know-about-android-m-permission-developer-edition/en
            ActivityCompat.requestPermissions(appactivity!!, permissions!!, PSurface.REQUEST_PERMISSIONS)
        }
    }
}