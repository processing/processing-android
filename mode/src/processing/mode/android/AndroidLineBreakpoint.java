package processing.mode.android;

import com.sun.jdi.ReferenceType;
import processing.mode.java.Debugger;
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
    if (!alreadyAdded){
      className = ((AndroidDebugger) dbg).getPackageName() + "." + className;
      alreadyAdded = !alreadyAdded;
    }
  }
}
