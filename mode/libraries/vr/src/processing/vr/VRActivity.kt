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

import android.content.Intent
import android.util.DisplayMetrics
import android.view.SurfaceHolder

import com.google.vr.sdk.base.GvrActivity

import processing.android.AppComponent
import processing.android.ServiceEngine
import processing.core.PApplet

open class VRActivity : GvrActivity, AppComponent {

    private var metrics: DisplayMetrics? = null
    private var sketch: PApplet? = null

    // empty constructor
    constructor() {

    }

    constructor(sketch: PApplet?) {
        this.sketch = sketch
    }

    override fun initDimensions() {
        metrics = resources.displayMetrics
    }

    override fun getDisplayWidth(): Int {
        return metrics!!.widthPixels
    }

    override fun getDisplayHeight(): Int {
        return metrics!!.heightPixels
    }

    override fun getDisplayDensity(): Float {
        return metrics!!.density
    }

    override fun getKind(): Int {
        return GVR
    }

    override fun dispose() {

    }

    override fun setSketch(sketch: PApplet?) {
        this.sketch = sketch
        if (sketch != null) {
            sketch!!.initSurface(this@VRActivity, null as SurfaceHolder?)
            // Required to read the paired viewer's distortion parameters.
            sketch!!.requestPermission("android.permission.READ_EXTERNAL_STORAGE")
        }
    }

    override fun getSketch(): PApplet? {
        return sketch!!
    }

    override fun isService(): Boolean {
        return false
    }

    override fun getEngine(): ServiceEngine? {
        return null
    }

    public override fun onResume() {
        super.onResume()
        if (sketch != null) {
            sketch!!.onResume()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (sketch != null) {
            sketch!!.onPause()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (sketch != null) {
            sketch!!.onDestroy()
        }
    }

    public override fun onStart() {
        super.onStart()
        if (sketch != null) {
            sketch!!.onStart()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (sketch != null) {
            sketch!!.onStop()
        }
    }

    override fun requestDraw() {

    }

    override fun canDraw(): Boolean {
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (sketch != null) {
            sketch!!.onRequestPermissionsResult(requestCode, permissions as Array<String?>?, grantResults)
        }
    }

    public override fun onNewIntent(intent: Intent) {
        if (sketch != null) {
            sketch!!.onNewIntent(intent)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (sketch != null) {
            sketch!!.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if (sketch != null) {
            sketch!!.onBackPressed()
        }
    }

    companion object {
        const val GVR = 3

        @JvmStatic
        fun getRenderer(p: PApplet): VRGraphics {
            return p.graphics as VRGraphics
        }
    }
}