/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-16 The Processing Foundation

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

import processing.app.Base
import processing.app.exec.ProcessResult

import processing.mode.android.AVD.Companion.getPreferredPort
import processing.mode.android.AndroidMode.Companion.getTextString
import processing.mode.android.AndroidUtil.showMessage

import java.io.IOException
import java.util.*
import java.util.concurrent.*

//import processing.app.EditorConsole;
/**
 * <pre> AndroidEnvironment env = AndroidEnvironment.getInstance();
 * AndroidDevice n1 = env.getHardware();
 * AndroidDevice emu = env.getEmulator();</pre>
 * @author Jonathan Feinberg &lt;jdf@pobox.com&gt;
 */
internal class Devices private constructor() {
    private var showPermissionsErrorMessage = true
    var sDK: AndroidSDK? = null
    var selectedDevice: Device? = null
    private val devices: MutableMap<String, Device?> = ConcurrentHashMap()
    private val deviceLaunchThread = Executors.newSingleThreadExecutor()

    fun killAdbServer() {
        print("Shutting down any existing adb server...")
        System.out.flush()
        try {
            sDK!!.runADB("kill-server")
            println(" Done.")
        } catch (e: Exception) {
            System.err.println("/nDevices.killAdbServer() failed.")
            e.printStackTrace()
        }
    }

    fun startAdbServer() {
        print("Starting a new adb server...")
        System.out.flush()
        try {
            sDK!!.runADB("start-server")
            println(" Done.")
        } catch (e: Exception) {
            System.err.println("/nDevices.startAdbServer() failed.")
            e.printStackTrace()
        }
    }

    fun enableBluetoothDebugging() {
        val devices = instance
        val deviceList = devices.findMultiple(false)
        if (deviceList.size != 1) {
            // There is more than one non-emulator device connected to the computer,
            // but don't know which one the watch could be paired to... or the watch
            // is already paired to the phone, in which case we don't need to keep
            // trying to connect.
            return
        }
        val device = deviceList[0]
        try {
            // Try Enable debugging over bluetooth
            // http://developer.android.com/training/wearables/apps/bt-debugging.html
            sDK!!.runADB("-s", device!!.id, "forward", "tcp:$BT_DEBUG_PORT",
                    "localabstract:/adb-hub")
            sDK!!.runADB("connect", "127.0.0.1:$BT_DEBUG_PORT")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getEmulator(wear: Boolean): FutureTask<Device?> {
        val androidFinder: Callable<Device?> = Callable { blockingGetEmulator(wear) }
        val task = FutureTask(androidFinder)
        deviceLaunchThread.execute(task)
        return task
    }

    private fun blockingGetEmulator(wear: Boolean): Device? {
        val port = getPreferredPort(wear)
        var emu = find(true, port)
        if (emu != null) {
            return emu
        }
        val emuController = EmulatorController.getInstance(wear)

        if (emuController.state == EmulatorController.State.RUNNING) {
            // The emulator is in running state, but did not find any emulator device,
            // to the most common cause is that it was closed, so we will re-launch it.
            emuController.state = EmulatorController.State.NOT_RUNNING
        }
        if (emuController.state == EmulatorController.State.NOT_RUNNING) {
            try {
                emuController.launch(sDK!!, wear) // this blocks until emulator boots
            } catch (e: IOException) {
                System.err.println("Problem while launching emulator.")
                e.printStackTrace(System.err)
                return null
            }
        } else {
            return null
        }

        while (!Thread.currentThread().isInterrupted) {
            //      System.err.println("AndroidEnvironment: looking for emulator in loop.");
            //      System.err.println("AndroidEnvironment: emulatorcontroller state is "
            //          + emuController.getState());
            if (emuController.state == EmulatorController.State.NOT_RUNNING) {
                System.err.println("Error while starting the emulator. (" +
                        emuController.state + ")")
                return null
            }
            emu = find(true, port)

            if (emu != null) {
                //        System.err.println("AndroidEnvironment: returning " + emu.getId()
                //            + " from loop.");
                return emu
            }

            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                System.err.println("Devices: interrupted in loop.")
                return null
            }
        }
        return null
    }

    private fun find(wantEmulator: Boolean, port: String?): Device? {
        refresh()

        synchronized(devices) {
            for (device in devices.values) {
                if (port != null && device!!.name.indexOf(port) == -1) continue
                val isEmulator = device!!.id.contains("emulator")
                if (isEmulator && wantEmulator || !isEmulator && !wantEmulator) {
                    return device
                }
            }
        }
        return null
    }

    fun findMultiple(wantEmulator: Boolean): List<Device?> {
        val deviceList: MutableList<Device?> = ArrayList()
        refresh()

        synchronized(devices) {
            for (device in devices.values) {
                val isEmulator = device!!.id.contains("emulator")
                if (isEmulator && wantEmulator || !isEmulator && !wantEmulator) {
                    deviceList.add(device)
                }
            }
        }
        return deviceList
    }

    /**
     * @return the first Android hardware device known to be running, or null if there are none.
     */
    val hardware: Future<Device?>
        get() {
            var device = selectedDevice
            if (device == null || !device.isAlive) device = blockingGetHardware()
            return getHardware(device)
        }

    fun getHardware(device: Device?): FutureTask<Device?> {
        val androidFinder: Callable<Device?> = Callable { device }
        val task = FutureTask(androidFinder)
        deviceLaunchThread.execute(task)
        return task
    }

    private fun blockingGetHardware(): Device? {
        var hardware = find(false, null)
        if (hardware != null) {
            return hardware
        }

        while (!Thread.currentThread().isInterrupted) {
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                return null
            }
            hardware = find(false, null)
            if (hardware != null) {
                return hardware
            }
        }
        return null
    }

    private fun refresh() {
        val activeDevices = list()
        for (deviceId in activeDevices) {
            if (!devices.containsKey(deviceId)) {
                val device = Device(this, deviceId)
                addDevice(device)
            }
        }
    }

    private fun addDevice(device: Device) {
        //    System.err.println("AndroidEnvironment: adding " + device.getId());
        try {
            device.initialize()
            if (devices.put(device.id, device) != null) {
                // TODO: Silently add existing device, this may be indicating, like in 
                // deviceRemoved() below, different threads trying to add the same 
                // device. Does not seem to have any negative effect.
//        throw new IllegalStateException("Adding " + device
//            + ", which already exists!");
            }
        } catch (e: Exception) {
            System.err.println("While initializing " + device.id + ": " + e)
        }
    }

    fun deviceRemoved(device: Device) {
        val id = device.id

        if (devices.containsKey(id)) {
            devices.remove(device.id)
        } else {
            // TODO: Device already removed, don't throw exception as this seems to 
            // happen quite often when removing a device, perhaps shutdown() gets 
            // called twice?
//      throw new IllegalStateException("I didn't know about device "
//          + device.getId() + "!");      
        }
    }

    /**
     *
     * First line starts "List of devices"
     *
     *
     * When an emulator is started with a debug port, then it shows up
     * in the list of devices.
     *
     *
     * List of devices attached
     * <br></br>HT91MLC00031 device
     * <br></br>emulator-5554 offline
     *
     *
     * List of devices attached
     * <br></br>HT91MLC00031 device
     * <br></br>emulator-5554 device
     *
     * @return list of device identifiers
     * @throws IOException
     */
    fun list(): List<String> {
        if (AndroidSDK.adbDisabled) {
            return emptyList()
        }

        val result: ProcessResult

        result = try {
//      System.out.println("listing devices 00");
            sDK!!.runADB("devices")
            //      System.out.println("listing devices 05");
        } catch (e: InterruptedException) {
            return emptyList()
        } catch (e: IOException) {
            System.err.println("Problem inside Devices.list()")
            e.printStackTrace()
            //      System.err.println(e);
//      System.err.println("checking devices");
//      e.printStackTrace(EditorConsole.systemErr);
            return emptyList()
        }
        //    System.out.println("listing devices 10");
        if (!result.succeeded()) {
            if (result.stderr.contains("protocol fault (no status)")) {
                System.err.println("bleh: $result") // THIS IS WORKING
            } else {
                System.err.println("nope: $result")
            }
            return emptyList()
        }
        //    System.out.println("listing devices 20");

        // might read "List of devices attached"
        val stdout = result.stdout

        if (!(stdout.contains("List of devices") || stdout.trim { it <= ' ' }.length == 0)) {
            System.err.println(getTextString("android_devices.error.cannot_get_device_list"))
            System.err.println(stdout)
            return emptyList()
        }

//    System.out.println("listing devices 30");
        val devices: MutableList<String> = ArrayList()

        for (line in result) {
            if (line.contains("\t")) {
                val fields = line.split("\t").toTypedArray()
                if (fields[1] == "device") {
                    devices.add(fields[0])
                } else if (fields[1].contains("no permissions") && showPermissionsErrorMessage) {
                    showMessage(getTextString("android_devices.error.no_permissions_title"),
                            getTextString("android_devices.error.no_permissions_body", DEVICE_PERMISSIONS_URL))
                    showPermissionsErrorMessage = false
                }
            }
        }
        return devices
    }

    companion object {
        private const val DEVICE_PERMISSIONS_URL = "https://developer.android.com/studio/run/device.html"
        val instance = Devices()
        private const val BT_DEBUG_PORT = "4444"
    }

    // constructor or initializer block
    // this block will be executed before any other operation
    init {
        if (Base.DEBUG) {
            println("Starting up Devices")
        }

        //    killAdbServer();
        Runtime.getRuntime().addShutdownHook(
                object : Thread("processing.mode.android.Devices Shutdown") {
                    override fun run() {

                        //System.out.println("Shutting down Devices");
                        //System.out.flush();
                        for (device in ArrayList(devices.values)) {
                            device!!.shutdown()
                        }

                        // Don't do this, it'll just make Eclipse and others freak out.
                        //killAdbServer();
                    }
                })
    }
}