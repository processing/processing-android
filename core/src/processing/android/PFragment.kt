/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
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

import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.util.DisplayMetrics
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.os.Bundle
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ContextMenu.ContextMenuInfo;
import processing.core.PApplet
import processing.core.PConstants

class PFragment : Fragment, AppComponent {

    private var metrics: DisplayMetrics? = null
    private var size: Point? = null
    private var sketch: PApplet? = null

    @LayoutRes
    private var layout = -1

    constructor() : super() {

    }

    constructor(sketch: PApplet?) : super() {
        setSketch(sketch)
    }

    override fun initDimensions() {
        metrics = DisplayMetrics()
        size = Point()
        val wm = activity!!.windowManager
        val display = wm.defaultDisplay
        CompatUtils.getDisplayParams(display, metrics, size!!)
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
        return AppComponent.FRAGMENT
    }

    override fun setSketch(sketch: PApplet?) {
        this.sketch = sketch
        if (layout != -1) {
            sketch!!.parentLayout = layout
        }
    }

    override fun getSketch(): PApplet? {
        return sketch
    }

    fun setLayout(@LayoutRes layout: Int, @IdRes id: Int, activity: FragmentActivity) {
        this.layout = layout
        if (sketch != null) {
            sketch!!.parentLayout = layout
        }
        val manager = activity.supportFragmentManager
        val transaction = manager.beginTransaction()
        transaction.add(id, this)
        transaction.commit()
    }

    fun setView(view: View, activity: FragmentActivity) {
        val manager = activity.supportFragmentManager
        val transaction = manager.beginTransaction()
        transaction.add(view.id, this)
        transaction.commit()
    }

    override fun isService(): Boolean {
        return false
    }

    override fun getEngine(): ServiceEngine? {
        return null
    }

    override fun dispose() {

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return if (sketch != null) {
            sketch!!.initSurface(inflater, container, savedInstanceState, this, null)

            // For compatibility with older sketches that run some hardware initialization
            // inside onCreate(), don't call from Fragment.onCreate() because the surface
            // will not be yet ready, and so the reference to the activity and other
            // system variables will be null. In any case, onCreateView() is called
            // immediately after onCreate():
            // https://developer.android.com/reference/android/app/Fragment.html#Lifecycle
            sketch!!.onCreate(savedInstanceState)
            sketch!!.surface.rootView
        } else {
            null
        }
    }

    override fun onStart() {
        super.onStart()
        if (sketch != null) {
            sketch!!.onStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (sketch != null) {
            sketch!!.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (sketch != null) {
            sketch!!.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (sketch != null) {
            sketch!!.onStop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (sketch != null) {
            sketch!!.onDestroy()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (sketch != null) sketch!!.onActivityResult(requestCode, resultCode, data)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (sketch != null) sketch!!.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (sketch != null) sketch!!.onOptionsItemSelected(item) else super.onOptionsItemSelected(item)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo) {
        if (sketch != null) sketch!!.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return if (sketch != null) sketch!!.onContextItemSelected(item) else super.onContextItemSelected(item)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (PApplet.DEBUG) println("configuration changed: $newConfig")
        super.onConfigurationChanged(newConfig)
    }

    fun setOrientation(which: Int) {
        if (which == PConstants.PORTRAIT) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else if (which == PConstants.LANDSCAPE) {
            activity!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun requestDraw() {}

    override fun canDraw(): Boolean {
        return sketch != null && sketch!!.isLooping
    }
}