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

import android.content.Intent
import processing.core.PApplet
import processing.core.PConstants

/**
 * @author Aditya Rana
 */
interface AppComponent : PConstants {

    fun initDimensions()
    fun getDisplayWidth(): Int
    fun getDisplayHeight(): Int
    fun getDisplayDensity(): Float
    fun getKind(): Int
    fun setSketch(sketch: PApplet?)
    fun getSketch(): PApplet?
    fun isService(): Boolean
    fun getEngine(): ServiceEngine?
    fun startActivity(intent: Intent?)
    fun requestDraw()
    fun canDraw(): Boolean
    fun dispose()

    companion object {
        const val FRAGMENT:  Int = 0
        const val WALLPAPER: Int = 1
        const val WATCHFACE: Int = 2
    }
}