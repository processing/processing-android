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

class KeyEvent : Event {
    @JvmField
    var key: Char
    @JvmField
    var keyCode: Int
    @JvmField
    var isAutoRepeat = false

    constructor(nativeObject: Any?,
                millis: Long, action: Int, modifiers: Int,
                key: Char, keyCode: Int) : super(nativeObject!!, millis, action, modifiers) {
        flavor = KEY
        this.key = key
        this.keyCode = keyCode
    }

    constructor(nativeObject: Any?,
                millis: Long, action: Int, modifiers: Int,
                key: Char, keyCode: Int, isAutoRepeat: Boolean) : super(nativeObject!!, millis, action, modifiers) {
        flavor = KEY
        this.key = key
        this.keyCode = keyCode
        this.isAutoRepeat = isAutoRepeat
    }

    companion object {
        const val PRESS = 1
        const val RELEASE = 2
        const val TYPE = 3
    }
}