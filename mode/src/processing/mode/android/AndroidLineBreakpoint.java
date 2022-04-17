/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2018-21 The Processing Foundation

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

package processing.mode.android;

import com.sun.jdi.ReferenceType;
import processing.mode.java.debug.Debugger;
import processing.mode.java.debug.LineBreakpoint;
import processing.mode.java.debug.LineID;

public class AndroidLineBreakpoint extends LineBreakpoint {
  private boolean alreadyAdded;

  public AndroidLineBreakpoint(LineID line, Debugger dbg) {
    super(line, dbg);
  }

  public AndroidLineBreakpoint(int lineIdx, Debugger dbg) {
    super(lineIdx, dbg);
  }

  @Override public void classLoaded(ReferenceType theClass) {
    if (!isAttached()) {
      addPackageName();
      // try to attach
      attach(theClass);
    }
  }


  /**
   * Add package name to the class name. Needed to match
   * the logical class name to the VM (Physical) class name
   */
  private void addPackageName() {
    if (!alreadyAdded) {
      className = ((AndroidDebugger) dbg).getPackageName() + "." + className;
      alreadyAdded = !alreadyAdded;
    }
  }
}
