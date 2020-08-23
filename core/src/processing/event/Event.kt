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
 * Get the platform-native event object. This might be the java.awt event
 * on the desktop, though if you're using OpenGL on the desktop it'll be a
 * NEWT event that JOGL uses. Android events are something else altogether.
 * Bottom line, use this only if you know what you're doing, and don't make
 * assumptions about the class type.
 */
open class Event(
        @JvmField
        open var native: Any,
        @JvmField
        open var millis: Long,
        @kotlin.jvm.JvmField
        open var action: Int,
        @JvmField
        open var modifiers: Int) {
    @JvmField
    var flavor = 0
//        protected set

    //  public void setNative(Object nativeObject) {
    //    this.nativeObject = nativeObject;
    //  }

    //  public void setModifiers(int modifiers) {
    //    this.modifiers = modifiers;
    //  }

    //  public void setMillis(long millis) {
    //    this.millis = millis;
    //  }

    //  public void setAction(int action) {
    //    this.action = action;
    //  }

    val isShiftDown: Boolean
        get() = modifiers and SHIFT != 0

    val isControlDown: Boolean
        get() = modifiers and CTRL != 0

    val isMetaDown: Boolean
        get() = modifiers and META != 0

    val isAltDown: Boolean
        get() = modifiers and ALT != 0

    companion object {
        // These correspond to the java.awt.Event modifiers (not to be confused with
        // the newer getModifiersEx), though they're not guaranteed to in the future.
        const val SHIFT = 1 shl 0
        const val CTRL = 1 shl 1
        const val META = 1 shl 2
        const val ALT = 1 shl 3

        // Types of events. As with all constants in Processing, brevity's preferred.
        const val KEY = 1
        const val MOUSE = 2
        const val TOUCH = 3
    }
}