package processing.mode.android;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ModificationWatchpointRequest;
import processing.mode.java.Debugger;
import processing.mode.java.debug.LineBreakpoint;
import processing.mode.java.debug.LineID;

import java.io.IOException;
import java.util.List;

public class AndroidDebugger extends Debugger {
  /// editor window, acting as main view
  protected AndroidEditor editor;
  protected AndroidRunner runtime;
  protected AndroidMode androidMode;

  protected boolean isEnabled;

  private static final int TCP_PORT = 7777;

  private String pkgName = "";
  private String sketchClassName = "";

  public static final String FIELD_NAME = "mouseX";

  public AndroidDebugger(AndroidEditor editor, AndroidMode androidMode) {
    super(editor);
    this.editor = editor;
    this.androidMode = androidMode;
  }

  public boolean isEnabled() {
    return isEnabled;
  }

  public void toggleDebug() {
    isEnabled = !isEnabled;
  }

  @Override
  public AndroidEditor getEditor() {
    return editor;
  }

  public synchronized void startDebug(AndroidRunner runner, Device device) {
    //stopDebug(); // stop any running sessions
    if (isStarted()) {
      return; // do nothing
    }

    runtime = runner;
    pkgName = runner.build.getPackageName();
    sketchClassName = runner.build.getSketchClassName();

    try {
      device.forwardPort(TCP_PORT);

      // connect
      System.out.println("\n\n\n:debugger:Attaching Debugger");
      VirtualMachine vm = runner.connectVirtualMachine(TCP_PORT);

      // set watch field on already loaded classes
      List<ReferenceType> referenceTypes = vm.classesByName(pkgName + "." + sketchClassName);

      for (ReferenceType refType : referenceTypes) {
        addFieldWatch(vm, refType);

        // Adding breakpoint at line 27
//      try {
//        List<Location> locations = refType.locationsOfLine(28);
//        if (locations.isEmpty()){
//          System.out.println("no location found for line 27");
//        } else {
//          BreakpointRequest bpr = vm.eventRequestManager().createBreakpointRequest(locations.get(0));
//          bpr.enable();
//        }
//      } catch (AbsentInformationException e) {
//        e.printStackTrace();
//      }
      }
      // watch for loaded classes
      addClassWatch(vm);

      // resume the vm
      vm.resume();

      // start receiving vm events
      VMEventReader eventThread = new VMEventReader(vm.eventQueue(), vmEventListener);
      eventThread.start();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Watch all classes ({@value sketchClassName}) variable
   */
  private void addClassWatch(VirtualMachine vm) {
    EventRequestManager erm = vm.eventRequestManager();
    ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
    classPrepareRequest.addClassFilter(sketchClassName);
    classPrepareRequest.setEnabled(true);
  }

  /**
   * Watch field ({@value FIELD_NAME})
   */
  private void addFieldWatch(VirtualMachine vm,
                             ReferenceType refType) {
    EventRequestManager erm = vm.eventRequestManager();
    Field field = refType.fieldByName(FIELD_NAME);
    ModificationWatchpointRequest modificationWatchpointRequest = erm.createModificationWatchpointRequest(field);
    modificationWatchpointRequest.setEnabled(true);
  }

  @Override
  public VirtualMachine vm() {
    if (runtime != null) {
      return runtime.vm();
    }
    return null;
  }

  /**
   * Get the breakpoint on a certain line, if set.
   *
   * @param line the line to get the breakpoint from
   * @return the breakpoint, or null if no breakpoint is set on the specified
   * line.
   */
  LineBreakpoint breakpointOnLine(LineID line) {
    for (LineBreakpoint bp : breakpoints) {
      if (bp.isOnLine(line)) {
        return bp;
      }
    }
    return null;
  }

  synchronized void toggleBreakpoint(int lineIdx) {
    LineID line = editor.getLineIDInCurrentTab(lineIdx);
    int index = line.lineIdx();
    if (hasBreakpoint(line)) {
      removeBreakpoint(index);
    } else {
      // Make sure the line contains actual code before setting the break
      // https://github.com/processing/processing/issues/3765
      if (editor.getLineText(index).trim().length() != 0) {
        setBreakpoint(index);
      }
    }
  }

  /**
   * Set a breakpoint on a line in the current tab.
   *
   * @param lineIdx the line index (0-based) of the current tab to set the
   *                breakpoint on
   */
  synchronized void setBreakpoint(int lineIdx) {
    setBreakpoint(editor.getLineIDInCurrentTab(lineIdx));
  }


  synchronized void setBreakpoint(LineID line) {
    // do nothing if we are kinda busy
    if (isStarted() && !isPaused()) {
      return;
    }
    // do nothing if there already is a breakpoint on this line
    if (hasBreakpoint(line)) {
      return;
    }
    breakpoints.add(new LineBreakpoint(line, this));
  }


  /**
   * Remove a breakpoint from the current line (if set).
   */
  synchronized void removeBreakpoint() {
    removeBreakpoint(editor.getCurrentLineID().lineIdx());
  }


  /**
   * Remove a breakpoint from a line in the current tab.
   *
   * @param lineIdx the line index (0-based) in the current tab to remove the
   *                breakpoint from
   */
  void removeBreakpoint(int lineIdx) {
    // do nothing if we are kinda busy
    if (isBusy()) {
      return;
    }

    LineBreakpoint bp = breakpointOnLine(editor.getLineIDInCurrentTab(lineIdx));
    if (bp != null) {
      bp.remove();
      breakpoints.remove(bp);
    }
  }
}
