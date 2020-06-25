/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2017 The Processing Foundation

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

import android.graphics.Rect
import processing.core.PConstants

interface ServiceEngine : PConstants {
    // wallpapers
    fun isPreview(): Boolean
    fun getXOffset(): Float
    fun getYOffset(): Float
    fun getXOffsetStep(): Float
    fun getYOffsetStep(): Float
    fun getXPixelOffset(): Int
    fun getYPixelOffset(): Int

    // wear
    fun isInAmbientMode(): Boolean
    fun isRound(): Boolean
    fun getInsets(): Rect?
    fun useLowBitAmbient(): Boolean
    fun requireBurnInProtection(): Boolean

    // Service permissions
    fun onRequestPermissionsResult(requestCode: Int,
                                   permissions: Array<String?>?,
                                   grantResults: IntArray?)
}