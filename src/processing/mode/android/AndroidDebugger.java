package processing.mode.android;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ModificationWatchpointRequest;
import processing.app.Sketch;
import processing.app.SketchException;
import processing.mode.android.debugger.VMAcquirer;
import processing.mode.java.Debugger;
import processing.mode.java.debug.LineBreakpoint;
import processing.mode.java.debug.LineID;

import java.io.IOException;
import java.util.List;

public class AndroidDebugger extends Debugger {
  /// editor window, acting as main view
  protected AndroidEditor editor;
  protected AndroidRunner runner;
  protected AndroidMode androidMode;

  protected boolean isEnabled;

  private static final int TCP_PORT = 7777;

  protected VirtualMachine vm;

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

  public void toggleDebug(){
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

//    // we are busy now
//    this.editor.statusBusy();
//
//    // clear console
//    this.editor.clearConsole();
//
//    // clear variable inspector (also resets expanded states)
//    this.editor.variableInspector().reset();
//
//    // load edits into sketch obj, etc...
//    this.editor.prepareRun();

    pkgName = runner.build.getPackageName();
    sketchClassName = runner.build.getSketchClassName();

    try {
      device.forwardPort(TCP_PORT);
      attachDebugger();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void attachDebugger() throws IOException, InterruptedException {
    // connect
    System.out.println(":debugger:Attaching Debugger");
    vm = new VMAcquirer().connect(TCP_PORT);
    // wait to connect
    Thread.sleep(3000);
    // set watch field on already loaded classes
    List<ReferenceType> referenceTypes = vm.classesByName(pkgName+ "." + sketchClassName);

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

    // process events
    new Thread(new Runnable() {
      @Override
      public void run() {
        EventQueue eventQueue = vm.eventQueue();
        while (true) {
          EventSet eventSet = null;
          try {
            eventSet = eventQueue.remove();
          } catch (InterruptedException e) {
          }
          for (Event event : eventSet) {
            if (event instanceof VMDeathEvent
                || event instanceof VMDisconnectEvent) {
              // exit
              System.out.println(":debugger:app killed");
              return;
            } else if (event instanceof ClassPrepareEvent) {
              // watch field on loaded class
              ClassPrepareEvent classPrepEvent = (ClassPrepareEvent) event;
              ReferenceType refType = classPrepEvent
                  .referenceType();
              addFieldWatch(vm, refType);
            } else if (event instanceof ModificationWatchpointEvent) {
              ModificationWatchpointEvent modEvent = (ModificationWatchpointEvent) event;
              System.out.println("watching mouseX:");
              System.out.println("old="
                  + modEvent.valueCurrent());
              System.out.println("new=" + modEvent.valueToBe());
              System.out.println();
            } else if (event instanceof BreakpointEvent) {
              System.out.println("breakpoint at : " + ((BreakpointEvent) event).location().lineNumber());
              vm.suspend();
            }
          }
          eventSet.resume();
        }
      }
    }).start();
  }

  /**
   * Watch all classes of name `sketchClassName` variable
   */
  private void addClassWatch(VirtualMachine vm) {
    EventRequestManager erm = vm.eventRequestManager();
    ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
    classPrepareRequest.addClassFilter(sketchClassName);
    classPrepareRequest.setEnabled(true);
  }

  /**
   * Watch field of name "mouseX"
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
      return vm;
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
