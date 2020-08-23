/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org
  Copyright (c) 2012-16 The Processing Foundation
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

package processing.event

/**
 * @author Aditya Rana
 * IMPORTANT NOTICE: None of the methods and inner classes in TouchEvent are
 * part of the Processing API. Don't use them! They might be changed or removed
 * without notice.
*/
open class TouchEvent(nativeObject: Any?, millis: Long, action: Int, modifiers: Int,
                      button: Int) : Event(nativeObject!!, millis, action, modifiers) {
    override var action = 0

    override var native: Any
        get() = super.native
        set(value) {
            native = value
        }

    var button: Int
        protected set
    private var numPointers = 0
    private lateinit var pointerId: IntArray
    private lateinit var pointerX: FloatArray
    private lateinit var pointerY: FloatArray
    private lateinit var pointerArea: FloatArray
    private lateinit var pointerPressure: FloatArray
    fun setNumPointers(n: Int) {
        numPointers = n
        pointerId = IntArray(n)
        pointerX = FloatArray(n)
        pointerY = FloatArray(n)
        pointerArea = FloatArray(n)
        pointerPressure = FloatArray(n)
    }

    fun setPointer(idx: Int, id: Int, x: Float, y: Float, a: Float, p: Float) {
        pointerId[idx] = id
        pointerX[idx] = x
        pointerY[idx] = y
        pointerArea[idx] = a
        pointerPressure[idx] = p
    }

    fun getNumPointers(): Int {
        return numPointers
    }

    fun getPointer(idx: Int): Pointer {
        val pt = Pointer()
        pt.id = pointerId[idx]
        pt.x = pointerX[idx]
        pt.y = pointerY[idx]
        pt.area = pointerArea[idx]
        pt.pressure = pointerPressure[idx]
        return pt
    }

    fun getPointerId(idx: Int): Int {
        return pointerId[idx]
    }

    fun getPointerX(idx: Int): Float {
        return pointerX[idx]
    }

    fun getPointerY(idx: Int): Float {
        return pointerY[idx]
    }

    fun getPointerArea(idx: Int): Float {
        return pointerArea[idx]
    }

    fun getPointerPressure(idx: Int): Float {
        return pointerPressure[idx]
    }

    fun getTouches(touches: Array<Pointer?>?): Array<Pointer?> {
        var touches = touches
        if (touches == null || touches.size != numPointers) {
            touches = arrayOfNulls(numPointers)
            for (idx in 0 until numPointers) {
                touches[idx] = Pointer()
            }
        }
        for (idx in 0 until numPointers) {
            touches[idx]!!.id = pointerId[idx]
            touches[idx]!!.x = pointerX[idx]
            touches[idx]!!.y = pointerY[idx]
            touches[idx]!!.area = pointerArea[idx]
            touches[idx]!!.pressure = pointerPressure[idx]
        }
        return touches
    }

    inner class Pointer {
        var id = 0
        var x = 0f
        var y = 0f
        var area = 0f
        var pressure = 0f
    }

    companion object {
        const val START = 1
        const val END = 2
        const val CANCEL = 3
        const val MOVE = 4
    }

    // constructor or initializer block
    init {
        flavor = TOUCH
        this.button = button
    }
}