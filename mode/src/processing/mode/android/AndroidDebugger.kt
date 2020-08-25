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

import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.*
import com.sun.jdi.request.EventRequestManager
import com.sun.jdi.request.StepRequest

import processing.app.Messages

import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.java.Debugger
import processing.mode.java.debug.LineBreakpoint
import processing.mode.java.debug.LineID

import java.io.IOException
import javax.swing.SwingUtilities

/**
 * @author Aditya Rana
 */
internal class AndroidDebugger (editor: AndroidEditor, androidMode: AndroidMode) : Debugger(editor) {

    /// editor window, acting as main view
    var androideditor: AndroidEditor? = null
    var androidruntime: AndroidRunner? = null

    private var androidMode: AndroidMode
    var isEnabled = false

    var packageName = ""
    private var sketchClassName = ""

    // constructor or initializer block
    init {
        this.androideditor = editor
        this.androidMode = androidMode
    }

    fun toggleDebug() {
        isEnabled = !isEnabled
    }

    override fun getEditor(): AndroidEditor? {
        return androideditor
    }

    @Synchronized
    fun startDebug(runner: AndroidRunner, device: Device) {
        //stopDebug(); // stop any running sessions
        if (isStarted) {
            return  // do nothing
        }

        androidruntime = runner
        packageName = runner.build.packageName!!
        sketchClassName = runner.build.sketchClassName
        mainClassName = "$packageName.$sketchClassName"

        try {
            val port = 8000 + (Math.random() * 1000).toInt()
            device.forwardPort(port)

            // connect
            println(getTextString("android_debugger.info.attaching_debugger"))
            val vm = runner.connectVirtualMachine(port)
            println(getTextString("android_debugger.info.debugger_attached"))

            // start receiving vm events
            val eventThread = VMEventReader(vm!!.eventQueue(), vmEventListener)
            eventThread.start()

            // watch for loaded classes
            addClassWatch(vm!!)

            // resume the vm
            vm!!.resume()
        } catch (e: IOException) {
            Messages.log(getTextString("android_debugger.error.debugger_exception", e.message))
            // Retry
            startDebug(runner, device)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @Synchronized
    override fun vmEvent(es: EventSet) {
        val vm = vm()

        if (vm != null && vm !== es.virtualMachine()) {
            // This is no longer VM we are interested in,
            // we already cleaned up and run different VM now.
            return
        }

        for (e in es) {
            // System.out.println("VM Event: " + e);
            if (e is VMStartEvent) {
//        System.out.println("start");
            } else if (e is ClassPrepareEvent) {
                vmClassPrepareEvent(e)
            } else if (e is BreakpointEvent) {
                vmBreakPointEvent(e)
            } else if (e is StepEvent) {
                vmStepEvent(e)
            } else if (e is VMDisconnectEvent) {
                stopDebug()
            } else if (e is VMDeathEvent) {
                started = false
                androideditor!!.statusEmpty()
            }
        }
    }

    private fun vmClassPrepareEvent(ce: ClassPrepareEvent) {
        val rt = ce.referenceType()
        currentThread = ce.thread()

        // for now we're paused
        paused = true

        if (rt.name() == mainClassName) {
            //printType(rt);
            mainClass = rt
            classes.add(rt)
            //      log("main class load: " + rt.name());
            started = true // now that main class is loaded, we're started
        } else {
            classes.add(rt) // save loaded classes
            //      log("class load: {0}" + rt.name());
        }

        // notify listeners
        for (listener in classLoadListeners) {
            listener?.classLoaded(rt)
        }

        paused = false // resuming now
        androidruntime!!.vm()!!.resume()

    }

    private fun vmBreakPointEvent(be: BreakpointEvent) {
        currentThread = be.thread() // save this thread
        updateVariableInspector(currentThread) // this is already on the EDT
        val newCurrentLine = locationToLineID(be.location())

        SwingUtilities.invokeLater {
            androideditor!!.setCurrentLine(newCurrentLine)
            androideditor!!.deactivateStep()
            androideditor!!.deactivateContinue()
        }

        // hit a breakpoint during a step, need to cancel the step.
        if (requestedStep != null) {
            androidruntime!!.vm()!!.eventRequestManager().deleteEventRequest(requestedStep)
            requestedStep = null
        }

        // fix canvas update issue
        // TODO: is this a good solution?
        resumeOtherThreads(currentThread)
        paused = true
        androideditor!!.statusHalted()
    }

    private fun vmStepEvent(se: StepEvent) {
        currentThread = se.thread()

        //printSourceLocation(currentThread);
        updateVariableInspector(currentThread) // this is already on the EDT
        val newCurrentLine = locationToLineID(se.location())

        SwingUtilities.invokeLater {
            androideditor!!.setCurrentLine(newCurrentLine)
            androideditor!!.deactivateStep()
            androideditor!!.deactivateContinue()
        }

        // delete the steprequest that triggered this step so new ones can be placed (only one per thread)
        val mgr: EventRequestManager = androidruntime!!.vm()!!.eventRequestManager()
        mgr.deleteEventRequest(se.request())

        requestedStep = null // mark that there is no step request pending
        paused = true
        androideditor!!.statusHalted()

        // disallow stepping into invisible lines
        if (!locationIsVisible(se.location())) {
            // TODO: this leads to stepping, should it run on the EDT?
            SwingUtilities.invokeLater { stepOutIntoViewOrContinue() }
        }
    }

    @Synchronized
    override fun continueDebug() {
        androideditor!!.activateContinue()
        androideditor!!.variableInspector().lock()
        //editor.clearSelection();
        //clearHighlight();
        androideditor!!.clearCurrentLine()

        if (!isStarted) {
            startDebug()
        } else if (isPaused) {
            androidruntime!!.vm()!!.resume()
            paused = false
            androideditor!!.statusBusy()
        }
    }

    override fun step(stepDepth: Int) {
        if (!isStarted) {
            startDebug()
        } else if (isPaused) {
            androideditor!!.variableInspector().lock()
            androideditor!!.activateStep()

            // use global to mark that there is a step request pending
            requestedStep = androidruntime!!.vm()!!.eventRequestManager().createStepRequest(currentThread, StepRequest.STEP_LINE, stepDepth)

            requestedStep.addCountFilter(1) // valid for one step only
            requestedStep.enable()

            paused = false
            androidruntime!!.vm()!!.resume()
            androideditor!!.statusBusy()
        }
    }

    @Synchronized
    override fun stopDebug() {
        androideditor!!.variableInspector().lock()

        if (androidruntime != null) {
            for (bp in breakpoints) {
                bp.detach()
            }
            androidruntime!!.close()
            androidruntime = null
            //build = null;
            classes.clear()
            // need to clear highlight here because, VMDisconnectedEvent seems to be unreliable. TODO: likely synchronization problem
            androideditor!!.clearCurrentLine()
        }

        stopTrackingLineChanges()

        started = false
        androideditor!!.deactivateDebug()
        androideditor!!.deactivateContinue()
        androideditor!!.deactivateStep()
        androideditor!!.statusEmpty()
    }

    /**
     * Watch all classes ({@value sketchClassName}) variable
     */
    private fun addClassWatch(vm: VirtualMachine) {
        val erm = vm.eventRequestManager()
        val classPrepareRequest = erm.createClassPrepareRequest()
        classPrepareRequest.addClassFilter(mainClassName)
        classPrepareRequest.isEnabled = true
    }

    override fun vm(): VirtualMachine? {
        return if (androidruntime != null) {
            androidruntime!!.vm()
        } else null
    }

    @Synchronized
    override fun isStarted(): Boolean {
        return started && androidruntime != null && androidruntime!!.vm() != null
    }

    /**
     * Get the breakpoint on a certain line, if set.
     *
     * @param line the line to get the breakpoint from
     * @return the breakpoint, or null if no breakpoint is set on the specified
     * line.
     */
    fun breakpointOnLine(line: LineID?): LineBreakpoint? {

        for (bp in breakpoints) {
            if (bp.isOnLine(line)) {
                return bp
            }
        }

        return null
    }

    @Synchronized
    fun toggleBreakpoint(lineIdx: Int) {
        val line: LineID = androideditor!!.getLineIDInCurrentTab(lineIdx)
        val index = line.lineIdx()

        if (hasBreakpoint(line)) {
            removeBreakpoint(index)
        } else {
            // Make sure the line contains actual code before setting the break
            // https://github.com/processing/processing/issues/3765
            if (androideditor!!.getLineText(index).trim({ it <= ' ' }).isNotEmpty()) {
                setBreakpoint(index)
            }
        }
    }

    /**
     * Set a breakpoint on a line in the current tab.
     *
     * @param lineIdx the line index (0-based) of the current tab to set the
     * breakpoint on
     */
    @Synchronized
    fun setBreakpoint(lineIdx: Int) {
        setBreakpoint(androideditor!!.getLineIDInCurrentTab(lineIdx))
    }

    @Synchronized
    fun setBreakpoint(line: LineID?) {
        // do nothing if we are kinda busy
        if (isStarted && !isPaused) {
            return
        }
        // do nothing if there already is a breakpoint on this line
        if (hasBreakpoint(line)) {
            return
        }
        breakpoints.add(AndroidLineBreakpoint(line, this))
    }

    /**
     * Remove a breakpoint from the current line (if set).
     */
    @Synchronized
    fun removeBreakpoint() {
        removeBreakpoint(androideditor!!.currentLineID.lineIdx())
    }

    /**
     * Remove a breakpoint from a line in the current tab.
     *
     * @param lineIdx the line index (0-based) in the current tab to remove the
     * breakpoint from
     */
    fun removeBreakpoint(lineIdx: Int) {
        // do nothing if we are kinda busy
        if (isBusy) {
            return
        }
        val bp = breakpointOnLine(androideditor!!.getLineIDInCurrentTab(lineIdx))

        if (bp != null) {
            bp.remove()
            breakpoints.remove(bp)
        }
    }

}