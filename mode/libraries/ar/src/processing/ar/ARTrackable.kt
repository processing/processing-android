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

import com.google.ar.core.HitResult
import processing.core.PMatrix3D

class ARTrackable(@JvmField var g: ARGraphics?, @JvmField private val id: Int?) {

    @JvmField
    var hit: HitResult? = null

    private var m: PMatrix3D? = null
    private var points: FloatArray = FloatArray(10)

    fun id(): String {
        return id!!.toString()
    }

    fun matrix(): PMatrix3D? {
        val idx = g!!.trackableIndex(id!!)
        m = g!!.getTrackableMatrix(idx, m)
        return m
    }

    fun transform() {
        g!!.applyMatrix(matrix())
    }

    val polygon: FloatArray
        get() {
            val idx = g!!.trackableIndex(id!!)
            points = g!!.getTrackablePolygon(idx, points)
            return points
        }

    fun lengthX(): Float {
        val idx = g!!.trackableIndex(id!!)
        return g!!.getTrackableExtentX(idx)
    }

    fun lengthY(): Float {
        return 0F
    }

    fun lengthZ(): Float {
        val idx = g!!.trackableIndex(id!!)
        return g!!.getTrackableExtentZ(idx)
    }

    fun isSelected(mx: Int, my: Int): Boolean {
        val idx = g!!.trackableIndex(id!!)
        return g!!.trackableSelected(idx, mx, my)
    }

    val isNew: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableNew(idx)
        }

    val isTracking: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableStatus(idx) == ARGraphics.TRACKING
        }

    val isPaused: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableStatus(idx) == ARGraphics.PAUSED
        }

    val isStopped: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableStatus(idx) == ARGraphics.STOPPED
        }

    val isPlane: Boolean
        get() = true

    val isPointCloud: Boolean
        get() = false

    val isFloorPlane: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableType(idx) == ARGraphics.PLANE_FLOOR
        }

    val isCeilingPlane: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableType(idx) == ARGraphics.PLANE_CEILING
        }

    val isWallPlane: Boolean
        get() {
            val idx = g!!.trackableIndex(id!!)
            return g!!.trackableType(idx) == ARGraphics.PLANE_WALL
        }

}