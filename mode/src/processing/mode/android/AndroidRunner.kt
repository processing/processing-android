/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-17 The Processing Foundation
 Copyright (c) 2011-12 Ben Fry and Casey Reas

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

import com.sun.jdi.VMDisconnectedException
import com.sun.jdi.VirtualMachine
import com.sun.jdi.connect.AttachingConnector
import com.sun.jdi.connect.IllegalConnectorArgumentsException

import org.eclipse.jdi.Bootstrap

import processing.app.Messages
import processing.app.RunnerListener
import processing.app.SketchException
import processing.app.ui.Editor
import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.java.runner.Runner

import java.io.IOException
import java.io.PrintStream
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

/**
 * Launches an app on the device or in the emulator.
 */
internal class AndroidRunner(var build: AndroidBuild, var listener: RunnerListener) : DeviceListener {
    private var sketchErr: PrintStream? = null
    private var sketchOut: PrintStream? = null
    private var vm: VirtualMachine? = null
    private var isDebugEnabled = false

    fun launch(deviceFuture: Future<Device>, comp: Int, emu: Boolean): Boolean {
        val devStr = if (emu) "emulator" else "device"
        listener.statusNotice(getTextString("android_runner.status.waiting_for_device", devStr))
        val device = waitForDevice(deviceFuture, listener)
        if (device == null || !device.isAlive) {
            listener.statusError(getTextString("android_runner.status.lost_connection_with_device", devStr))
            // Reset the server, in case that's the problem. Sometimes when
            // launching the emulator times out, the device list refuses to update.
            val devices = Devices.getInstance()
            devices.killAdbServer()
            return false
        }
        if (comp == AndroidBuild.WATCHFACE && !device.hasFeature("watch")) {
            listener.statusError(getTextString("android_runner.status.cannot_install_sketch"))
            Messages.showWarning(getTextString("android_runner.warn.non_watch_device_title"),
                    getTextString("android_runner.warn.non_watch_device_body"))
            return false
        }
        if (comp != AndroidBuild.WATCHFACE && device.hasFeature("watch")) {
            listener.statusError(getTextString("android_runner.status.cannot_install_sketch"))
            Messages.showWarning(getTextString("android_runner.warn.watch_device_title"),
                    getTextString("android_runner.warn.watch_device_body"))
            return false
        }
        device.addListener(this)
        device.setPackageName(build.packageName)
        listener.statusNotice(getTextString("android_runner.status.installing_sketch", device.id))
        // this stopped working with Android SDK tools revision 17
        if (!device.installApp(build, listener)) {
            listener.statusError(getTextString("android_runner.status.lost_connection", devStr))
            val devices = Devices.getInstance()
            devices.killAdbServer() // see above
            return false
        }
        var status = false
        if (comp == AndroidBuild.WATCHFACE || comp == AndroidBuild.WALLPAPER) {
            if (startSketch(build, device)) {
                listener.statusNotice(getTextString("android_runner.status.sketch_installed")
                        + (if (device.isEmulator) " " + getTextString("android_runner.status.in_emulator") else " " +
                        getTextString("android_runner.status.on_device")) + ".")
                status = true
            } else {
                listener.statusError(getTextString("android_runner.status.cannot_install_sketch"))
            }
        } else {
            listener.statusNotice(getTextString("android_runner.status.launching_sketch", device.id))
            if (startSketch(build, device)) {
                listener.statusNotice(getTextString("android_runner.status.sketch_launched")
                        + (if (device.isEmulator) " " + getTextString("android_runner.status.in_emulator") else " " +
                        getTextString("android_runner.status.on_device")) + ".")
                status = true
            } else {
                listener.statusError(getTextString("android_runner.status.cannot_launch_sketch"))
            }
        }

        // Start Debug if Debugger is enabled
        if (isDebugEnabled) {
            (listener as AndroidEditor).debugger
                    .startDebug(this, device)
        }
        listener.stopIndeterminate()
        lastRunDevice = device
        return status
    }

    @Throws(IOException::class)
    fun connectVirtualMachine(port: Int): VirtualMachine? {
        val strPort = Integer.toString(port)
        val connector = connector

        return try {
            vm = connect(connector, strPort)
            vm
        } catch (e: IllegalConnectorArgumentsException) {
            throw IllegalStateException(e)
        }
    }

    private val connector: AttachingConnector
        private get() {
            val vmManager = Bootstrap.virtualMachineManager()
            for (connector in vmManager.attachingConnectors()) {
                if ("com.sun.jdi.SocketAttach" == connector.name()) {
                    return connector as AttachingConnector
                }
            }
            throw IllegalStateException()
        }

    @Throws(IllegalConnectorArgumentsException::class, IOException::class)
    private fun connect(
            connector: AttachingConnector, port: String): VirtualMachine {
        val args = connector
                .defaultArguments()

        val pidArgument = args["port"] ?: throw IllegalStateException()
        pidArgument.setValue(port)

        return connector.attach(args)
    }

    fun vm(): VirtualMachine? {
        return vm
    }

    @Volatile
    private var lastRunDevice: Device? = null

    // if user asks for 480x320, 320x480, 854x480 etc, then launch like that
    // though would need to query the emulator to see if it can do that
    private fun startSketch(build: AndroidBuild, device: Device): Boolean {
        val packageName = build.packageName

        try {
            if (device.launchApp(packageName, isDebugEnabled)) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }

        return false
    }

    private fun waitForDevice(deviceFuture: Future<Device>, listener: RunnerListener): Device? {
        for (i in 0..119) {
            if (listener.isHalted) {
                deviceFuture.cancel(true)
                return null
            }

            try {
                return deviceFuture[1, TimeUnit.SECONDS]
            } catch (e: InterruptedException) {
                listener.statusError("Interrupted.")
                return null
            } catch (e: ExecutionException) {
                listener.statusError(e)
                return null
            } catch (expected: TimeoutException) {
            }
        }

        listener.statusError(getTextString("android_runner.status.cancel_waiting_for_device"))

        return null
    }

    /**
     * Currently figures out the first relevant stack trace line
     * by looking for the telltale presence of "processing.android"
     * in the package. If the packaging for droid sketches changes,
     * this method will have to change too.
     */
    override fun stackTrace(trace: List<String?>?) {
        val frames = trace!!.iterator()
        val exceptionLine = frames.next()
        val m = EXCEPTION_PARSER.matcher(exceptionLine)

        if (!m.matches()) {
            System.err.println(getTextString("android_runner.error.cannot_parse_stacktrace"))
            System.err.println(exceptionLine)
            listener.statusError(getTextString("android_runner.status.unknwon_exception"))
            return
        }

        val exceptionClass = m.group(1)

        Runner.handleCommonErrors(exceptionClass, exceptionLine, listener, sketchErr)

        while (frames.hasNext()) {
            val line = frames.next()
            if (line!!.contains(DEFAULT_PACKAGE_NAME)) {
                val lm = LOCATION.matcher(line)
                if (lm.find()) {
                    val filename = lm.group(1)
                    val lineNumber = lm.group(2).toInt() - 1
                    val rex = build.placeException(exceptionLine, filename, lineNumber)
                    listener.statusError(rex ?: SketchException(exceptionLine, false))
                    return
                }
            }
        }
    }

    // called by AndroidMode.handleStop()...
    fun close() {
        if (lastRunDevice != null) {
            lastRunDevice!!.bringLauncherToFront()
        }

        if (vm != null) {
            try {
                vm!!.exit(0)
            } catch (vmde: VMDisconnectedException) {
                // if the vm has disconnected on its own, ignore message
                //System.out.println("harmless disconnect " + vmde.getMessage());
                // TODO shouldn't need to do this, need to do more cleanup
            }
        }
    }

    // sketch stopped on the device
    override fun sketchStopped() {
        listener.stopIndeterminate()
        listener.statusHalt()
    }

    companion object {

        private const val DEFAULT_PACKAGE_NAME = "processing.android"

        private val LOCATION = Pattern.compile("\\(([^:]+):(\\d+)\\)")

        private val EXCEPTION_PARSER = Pattern.compile("^\\s*([a-z]+(?:\\.[a-z]+)+)(?:: .+)?$",
                Pattern.CASE_INSENSITIVE)
    }

    // constructor or initializer
    init {
        if (listener is AndroidEditor) {
            isDebugEnabled = (listener as AndroidEditor).isDebuggerEnabled
        }
        if (listener is Editor) {
            val editor = listener as Editor
            sketchErr = editor.console.err
            sketchOut = editor.console.out
        } else {
            sketchErr = System.err
            sketchOut = System.out
        }
    }
}