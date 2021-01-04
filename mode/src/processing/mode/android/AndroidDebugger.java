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

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.StepRequest;
import processing.app.Messages;
import processing.mode.java.Debugger;
import processing.mode.java.debug.ClassLoadListener;
import processing.mode.java.debug.LineBreakpoint;
import processing.mode.java.debug.LineID;

import java.io.IOException;

// Developed by Manav Jain as part of GSoC 2018
public class AndroidDebugger extends Debugger {
  /// editor window, acting as main view
  protected AndroidEditor editor;
  protected AndroidRunner runtime;
  protected AndroidMode androidMode;

  protected boolean isEnabled;

  private String pkgName = "";
  private String sketchClassName = "";

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

    mainClassName = pkgName + "." + sketchClassName;

    try {
      int port = 8000 + (int) (Math.random() * 1000);
      device.forwardPort(port);

      // connect
      System.out.println(AndroidMode.getTextString("android_debugger.info.attaching_debugger"));
      VirtualMachine vm = runner.connectVirtualMachine(port);
      System.out.println(AndroidMode.getTextString("android_debugger.info.debugger_attached"));

      // start receiving vm events
      VMEventReader eventThread = new VMEventReader(vm.eventQueue(), vmEventListener);
      eventThread.start();

      // watch for loaded classes
      addClassWatch(vm);

      // resume the vm
      vm.resume();

    } catch (IOException e) {
      Messages.log(AndroidMode.getTextString("android_debugger.error.debugger_exception", e.getMessage()));
      // Retry
      startDebug(runner, device);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override public synchronized void vmEvent(EventSet es) {
    VirtualMachine vm = vm();
    if (vm != null && vm != es.virtualMachine()) {
      // This is no longer VM we are interested in,
      // we already cleaned up and run different VM now.
      return;
    }
    for (Event e : es) {
      // System.out.println("VM Event: " + e);
      if (e instanceof VMStartEvent) {
//        System.out.println("start");

      } else if (e instanceof ClassPrepareEvent) {
        vmClassPrepareEvent((ClassPrepareEvent) e);

      } else if (e instanceof BreakpointEvent) {
        vmBreakPointEvent((BreakpointEvent) e);

      } else if (e instanceof StepEvent) {
        vmStepEvent(((StepEvent) e));

      } else if (e instanceof VMDisconnectEvent) {
        stopDebug();

      } else if (e instanceof VMDeathEvent) {
        started = false;
        editor.statusEmpty();
      }
    }
  }

  private void vmClassPrepareEvent(ClassPrepareEvent ce) {
    ReferenceType rt = ce.referenceType();
    currentThread = ce.thread();
    paused = true; // for now we're paused

    if (rt.name().equals(mainClassName)) {
      //printType(rt);
      mainClass = rt;
      classes.add(rt);
//      log("main class load: " + rt.name());
      started = true; // now that main class is loaded, we're started
    } else {
      classes.add(rt); // save loaded classes
//      log("class load: {0}" + rt.name());
    }

    // notify listeners
    for (ClassLoadListener listener : classLoadListeners) {
      if (listener != null) {
        listener.classLoaded(rt);
      }
    }
    paused = false; // resuming now
    runtime.vm().resume();
  }

  private void vmBreakPointEvent(BreakpointEvent be) {
    currentThread = be.thread(); // save this thread
    updateVariableInspector(currentThread); // this is already on the EDT
    final LineID newCurrentLine = locationToLineID(be.location());
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      @Override public void run() {
        editor.setCurrentLine(newCurrentLine);
        editor.deactivateStep();
        editor.deactivateContinue();
      }
    });

    // hit a breakpoint during a step, need to cancel the step.
    if (requestedStep != null) {
      runtime.vm().eventRequestManager().deleteEventRequest(requestedStep);
      requestedStep = null;
    }

    // fix canvas update issue
    // TODO: is this a good solution?
    resumeOtherThreads(currentThread);

    paused = true;
    editor.statusHalted();
  }

  private void vmStepEvent(StepEvent se) {
    currentThread = se.thread();

    //printSourceLocation(currentThread);
    updateVariableInspector(currentThread); // this is already on the EDT
    final LineID newCurrentLine = locationToLineID(se.location());
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        editor.setCurrentLine(newCurrentLine);
        editor.deactivateStep();
        editor.deactivateContinue();
      }
    });

    // delete the steprequest that triggered this step so new ones can be placed (only one per thread)
    EventRequestManager mgr = runtime.vm().eventRequestManager();
    mgr.deleteEventRequest(se.request());
    requestedStep = null; // mark that there is no step request pending
    paused = true;
    editor.statusHalted();

    // disallow stepping into invisible lines
    if (!locationIsVisible(se.location())) {
      // TODO: this leads to stepping, should it run on the EDT?
      javax.swing.SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          stepOutIntoViewOrContinue();
        }
      });
    }
  }

  @Override public synchronized void continueDebug() {
    editor.activateContinue();
    editor.variableInspector().lock();
    //editor.clearSelection();
    //clearHighlight();
    editor.clearCurrentLine();
    if (!isStarted()) {
      startDebug();
    } else if (isPaused()) {
      runtime.vm().resume();
      paused = false;
      editor.statusBusy();
    }
  }

  @Override protected void step(int stepDepth) {
    if (!isStarted()) {
      startDebug();
    } else if (isPaused()) {
      editor.variableInspector().lock();
      editor.activateStep();

      // use global to mark that there is a step request pending
      requestedStep = runtime.vm().eventRequestManager().createStepRequest(currentThread, StepRequest.STEP_LINE, stepDepth);
      requestedStep.addCountFilter(1); // valid for one step only
      requestedStep.enable();
      paused = false;
      runtime.vm().resume();
      editor.statusBusy();
    }
  }

  @Override public synchronized void stopDebug() {
    editor.variableInspector().lock();
    if (runtime != null) {

      for (LineBreakpoint bp : breakpoints) {
        bp.detach();
      }

      runtime.close();
      runtime = null;
      //build = null;
      classes.clear();
      // need to clear highlight here because, VMDisconnectedEvent seems to be unreliable. TODO: likely synchronization problem
      editor.clearCurrentLine();
    }
    stopTrackingLineChanges();
    started = false;

    editor.deactivateDebug();
    editor.deactivateContinue();
    editor.deactivateStep();

    editor.statusEmpty();
  }

  /**
   * Watch all classes ({@value sketchClassName}) variable
   */
  private void addClassWatch(VirtualMachine vm) {
    EventRequestManager erm = vm.eventRequestManager();
    ClassPrepareRequest classPrepareRequest = erm.createClassPrepareRequest();
    classPrepareRequest.addClassFilter(mainClassName);
    classPrepareRequest.setEnabled(true);
  }

  @Override
  public VirtualMachine vm() {
    if (runtime != null) {
      return runtime.vm();
    }
    return null;
  }

  @Override public synchronized boolean isStarted() {
    return started && runtime != null && runtime.vm() != null;
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
    breakpoints.add(new AndroidLineBreakpoint(line, this));
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

  public String getPackageName() {
    return pkgName;
  }
}
