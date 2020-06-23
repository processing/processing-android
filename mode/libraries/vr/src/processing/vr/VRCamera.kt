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

package processing.vr

import processing.core.PApplet
import processing.core.PMatrix3D

open class VRCamera(parent: PApplet) {
    private var parent: PApplet? = null
    private var graphics: VRGraphics? = null
    private var eyeMat: PMatrix3D? = null

    fun sticky() {
        parent!!.pushMatrix()
        parent!!.eye()
    }

    fun noSticky() {
        parent!!.popMatrix()
    }

    fun setPosition(x: Float, y: Float, z: Float) {
        eyeMat = graphics!!.getEyeMatrix(eyeMat)
        val x0 = eyeMat!!.m03
        val y0 = eyeMat!!.m13
        val z0 = eyeMat!!.m23
        graphics!!.translate(x0 - x, y0 - y, z0 - z)
    }

    fun setNear(near: Float) {
        graphics!!.defCameraNear = near
    }

    fun setFar(far: Float) {
        graphics!!.defCameraFar = far
    }

    // constructor or initializer block
    init {
        if (parent.g is VRGraphics) {
            this.parent = parent
            graphics = parent.g as VRGraphics
        } else {
            System.err.println("The VR camera can only be created when the VR renderer is in use")
        }
    }
}