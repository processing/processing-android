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

//import processing.core.PConstants;

/**
 * @author Aditya Rana
 */
open class MouseEvent(nativeObject: Any?,
                 millis: Long, action: Int, modifiers: Int,
                 x: Int, y: Int, button: Int, count: Int) : Event(nativeObject!!, millis, action, modifiers) {
    var x: Int
        protected set
    var y: Int
        protected set
    /** Which button was pressed, either LEFT, CENTER, or RIGHT.  */
    var button: Int
        protected set
    //  public void setButton(int button) {
    //    this.button = button;
    //  }
    /**
     * Number of clicks for mouse button events, or the number of steps (positive
     * or negative depending on direction) for a mouse wheel event.
     * Wheel events follow Java (see [here](http://docs.oracle.com/javase/6/docs/api/java/awt/event/MouseWheelEvent.html#getWheelRotation())), so
     * getAmount() will return "negative values if the mouse wheel was rotated
     * up or away from the user" and positive values in the other direction.
     * On Mac OS X, this will be reversed when "natural" scrolling is enabled
     * in System Preferences &rarr Mouse.
     */ //  public void setClickCount(int clickCount) {
    var clickCount: Int
        protected set
        @Deprecated("") get() = field

    //    this.clickCount = clickCount;
    //  }
    companion object {
        const val PRESS = 1
        const val RELEASE = 2
        const val CLICK = 3
        const val DRAG = 4
        const val MOVE = 5
        const val ENTER = 6
        const val EXIT = 7
    }

    //  public MouseEvent(int x, int y) {
    //    this(null,
    //         System.currentTimeMillis(), PRESSED, 0,
    //         x, y, PConstants.LEFT, 1);
    //  }
    init {
        flavor = MOUSE
        this.x = x
        this.y = y
        this.button = button
        clickCount = count
    }
}