/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2009-12 Ben Fry and Casey Reas

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package processing.mode.android

import com.sun.jdi.ReferenceType

import processing.mode.java.Debugger
import processing.mode.java.debug.LineBreakpoint
import processing.mode.java.debug.LineID

/**
 * @author Aditya Rana
 */
internal class AndroidLineBreakpoint : LineBreakpoint {
    private var alreadyAdded = false

    constructor(line: LineID?, dbg: Debugger?) : super(line, dbg) {

    }
    constructor(lineIdx: Int, dbg: Debugger?) : super(lineIdx, dbg) {

    }

    override fun classLoaded(theClass: ReferenceType) {
        if (!isAttached) {
            addPackageName()

            // try to attach
            attach(theClass)
        }
    }

    /**
     * Add package name to the class name. Needed to match
     * the logical class name to the VM (Physical) class name
     */
    private fun addPackageName() {
        if (!alreadyAdded) {

            className = (dbg as AndroidDebugger).packageName + "." + className

            alreadyAdded = !alreadyAdded

        }
    }
}