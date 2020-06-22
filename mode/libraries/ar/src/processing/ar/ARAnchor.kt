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

import processing.core.PMatrix3D

class ARAnchor {
    var g: ARGraphics? = null
    var isDisposed = false
        private set
    private var id: Int = 0
    private var m: PMatrix3D? = null

    constructor(trackable: ARTrackable?, x: Float?, y: Float?, z: Float?) {
        g = trackable!!.g!!
        val idx = g!!.trackableIndex(trackable!!.id().toInt())
        id = g!!.createAnchor(idx, x!!, y!!, z!!)
    }

    constructor(trackable: ARTrackable?) {
        g = trackable!!.g!!
        id = g!!.createAnchor(trackable!!.hit!!)
        trackable!!.hit = null
    }

    fun dispose() {
        if (!isDisposed) {
            g!!.deleteAnchor(id)
            isDisposed = true
        }
    }

    fun id(): String? {
        return id.toString()
    }

    fun matrix(): PMatrix3D? {
        m = g!!.getTrackableMatrix(id, m)
        return m!!
    }

    fun attach() {
        g!!.pushMatrix()
        g!!.anchor(id)
    }

    fun detach() {
        g!!.popMatrix()
    }

    val isTracking: Boolean
        get() = g!!.anchorStatus(id) == ARGraphics.TRACKING

    val isPaused: Boolean
        get() = g!!.anchorStatus(id) == ARGraphics.PAUSED

    val isStopped: Boolean
        get() = g!!.anchorStatus(id) == ARGraphics.STOPPED

}