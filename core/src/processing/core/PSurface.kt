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

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import java.io.File
import java.io.InputStream

import processing.android.AppComponent
import processing.android.ServiceEngine
import android.content.res.AssetManager
import android.content.Context
import android.app.Activity
import android.graphics.Rect
import android.view.SurfaceView
import android.view.SurfaceHolder


/**
 * @author Aditya Rana
 * Holds the surface view associated with the sketch, and the rendering thread
 * handling
 */
interface PSurface {
    fun getComponent(): AppComponent?

    fun getContext(): Context?

    fun getActivity(): Activity?

    fun getEngine(): ServiceEngine?

    fun dispose()

    fun getName(): String?

    fun getResource(id: Int): View?

    fun getVisibleFrame(): Rect?

    fun getSurfaceView(): SurfaceView?

    fun getSurfaceHolder(): SurfaceHolder?

    fun getRootView(): View?
    fun setRootView(view: View?)

    fun initView(sketchWidth: Int, sketchHeight: Int)
    fun initView(sketchWidth: Int, sketchHeight: Int, parentSize: Boolean,
                 inflater: LayoutInflater?, container: ViewGroup?,
                 savedInstanceState: Bundle?)

    fun startActivity(intent: Intent?)

    fun runOnUiThread(action: Runnable?)

    fun setOrientation(which: Int)

    fun setHasOptionsMenu(hasMenu: Boolean)

    fun getFilesDir(): File?

    fun getFileStreamPath(path: String?): File?

    fun openFileInput(filename: String?): InputStream?

    fun getAssets(): AssetManager?

    fun setSystemUiVisibility(visibility: Int)

    fun startThread()

    fun pauseThread()

    fun resumeThread()

    fun stopThread(): Boolean

    fun isStopped(): Boolean

    fun finish()

    fun setFrameRate(fps: Float)

    fun hasPermission(permission: String?): Boolean

    fun requestPermissions(permissions: Array<String?>?)

    companion object {
        const val REQUEST_PERMISSIONS = 1
    }
}