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
import processing.app.Platform
import processing.app.RunnerListener
import processing.app.exec.LineProcessor
import processing.app.exec.ProcessRegistry
import processing.app.exec.ProcessResult
import processing.app.exec.StreamPump

import processing.core.PApplet

import java.io.File
import java.io.IOException

import java.util.*
import java.util.regex.Pattern

internal class Device(val env: Devices, val id: String) {
    private val features: String
    private val activeProcesses: MutableSet<Int> = HashSet()
    private val listeners = Collections.synchronizedSet(HashSet<DeviceListener>())

    //  public static final String APP_STARTED = "android.device.app.started";
    //  public static final String APP_ENDED = "android.device.app.ended";
    private var packageName: String? = ""

    // mutable state
    private var logcat: Process? = null

    fun bringLauncherToFront() {
        try {
            adb("shell", "am", "start",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.HOME")
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }
    }

    fun hasFeature(feature: String?): Boolean {
        return -1 < features.indexOf(feature!!)
    }

    //    if (hasFeature("watch")) {
//      name += " (watch)";
//    }
    val name: String
        get() {
            var name = ""
            try {
                var result = env.sdk.runADB("-s", id, "shell", "getprop", "ro.product.brand")
                if (result.succeeded()) {
                    name += result.stdout + " "
                }
                result = env.sdk.runADB("-s", id, "shell", "getprop", "ro.product.model")
                if (result.succeeded()) {
                    name += result.stdout
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            name += " [$id]"

//    if (hasFeature("watch")) {
//      name += " (watch)";
//    }
            return name
        }

    // adb -s emulator-5556 install helloWorld.apk
    // : adb -s HT91MLC00031 install bin/Brightness-debug.apk
    // 532 KB/s (190588 bytes in 0.349s)
    // pkg: /data/local/tmp/Brightness-debug.apk
    // Failure [INSTALL_FAILED_ALREADY_EXISTS]
    // : adb -s HT91MLC00031 install -r bin/Brightness-debug.apk
    // 1151 KB/s (190588 bytes in 0.161s)
    // pkg: /data/local/tmp/Brightness-debug.apk
    // Success
    // safe to just always include the -r (reinstall) flag
    fun installApp(build: AndroidBuild, status: RunnerListener): Boolean {
        if (!isAlive) {
            return false
        }

        bringLauncherToFront()

        val apkPath = build.pathForAPK

        if (apkPath == null) {
            status.statusError("Could not install the sketch.")
            System.err.println("The APK file is missing")
            return false
        }

        try {
            val installResult = adb("install", "-r", apkPath)
            if (!installResult.succeeded()) {
                status.statusError("Could not install the sketch.")
                System.err.println(installResult)
                return false
            }

            var errorMsg: String? = null

            for (line in installResult) {
                if (line.startsWith("Failure")) {
                    errorMsg = line.substring(8)
                    if (line.contains("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES")) {
                        val removeResult = removeApp(build.packageName)
                        if (removeResult) return installApp(build, status)
                    }
                    System.err.println(line)
                }
            }

            if (errorMsg == null) {
                status.statusNotice("Done installing.")
                return true
            }

            status.statusError("Error while installing $errorMsg")

        } catch (e: IOException) {
            status.statusError(e)
        } catch (e: InterruptedException) {
        }
        return false
    }

    @Throws(IOException::class, InterruptedException::class)
    fun removeApp(packageName: String?): Boolean {
        val removeResult = adb("uninstall", packageName!!)
        return if (!removeResult.succeeded()) {
            false
        } else true
    }

    // different version that actually runs through JDI:
    // http://asantoso.wordpress.com/2009/09/26/using-jdb-with-adb-to-debugging-of-android-app-on-a-real-device/
    @Throws(IOException::class, InterruptedException::class)
    fun launchApp(packageName: String, isDebuggerEnabled: Boolean): Boolean {
        if (!isAlive) {
            return false
        }

        val pr: ProcessResult

        pr = if (isDebuggerEnabled) {
            val cmd = arrayOf(
                    "shell", "am", "start",
                    "-e", "debug", "true",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER", "-D",
                    "-n", "$packageName/.MainActivity"
            )
            adb(*cmd)
        } else {
            val cmd = arrayOf(
                    "shell", "am", "start",
                    "-e", "debug", "true",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER",
                    "-n", "$packageName/.MainActivity"
            )
            adb(*cmd)
        }
        //    PApplet.println(cmd);
        if (Base.DEBUG) {
            println(pr.toString())
        }
        // Sometimes this shows up on stdout, even though it returns 'success'
        // Error type 2
        // android.util.AndroidException: Can't connect to activity manager; is the system running?
        if (pr.stdout.contains("android.util.AndroidException")) {
            System.err.println(pr.stdout)
            return false
        }
        return pr.succeeded()
    }

    @Throws(IOException::class, InterruptedException::class)
    fun forwardPort(tcpPort: Int) {
        // Start ADB Server
        adb("start-server")

        val jdwpcmd = generateAdbCommand("jdwp")
        val deviceId = Runtime.getRuntime().exec(jdwpcmd)

        // Get Process ID from ADB command `adb jdwp`
        val pIDProcessor = JDWPProcessor()

        StreamPump(deviceId.inputStream, "jdwp: ").addTarget(
                pIDProcessor).start()
        StreamPump(deviceId.errorStream, "jdwperr: ").addTarget(
                System.err).start()
        Thread.sleep(1000)

        // forward to tcp port
        adb("forward", "tcp:$tcpPort", "jdwp:" + pIDProcessor.getId())
    }

    private inner class JDWPProcessor : LineProcessor {
        private var pId = 0
        override fun processLine(line: String) {
            pId = line.toInt()
        }

        fun getId(): Int {
            return pId
        }
    }

    val isEmulator: Boolean
        get() = id.startsWith("emulator")

    fun setPackageName(pkgName: String?) {
        packageName = pkgName
    }

    private val stackTrace: MutableList<String?> = ArrayList()

    private inner class LogLineProcessor : LineProcessor {
        override fun processLine(line: String) {
            val entry = LogEntry(line)

//      System.out.println("***************************************************");
//      System.out.println(line);
//      System.out.println(activeProcesses);
//      System.out.println(entry.message);

            if (entry.message.startsWith("PROCESSING")) {
                // Old start/stop process detection, does not seem to work anymore.
                // Should be ok to remove at some point.
                if (entry.message.contains("onStart")) {
                    startProc(entry.source, entry.pid)
                } else if (entry.message.contains("onStop")) {
                    endProc(entry.pid)
                }
            } else if (packageName != null && packageName != "" &&
                    entry.message.contains("Start proc") &&
                    entry.message.contains(packageName!!)) {
                // Sample message string from logcat when starting process:
                // "Start proc 29318:processing.test.sketch001/u0a403 for activity processing.test.sketch001/.MainActivity"
                var pidFound = false

                try {
                    val idx0 = entry.message.indexOf("Start proc") + 11
                    val idx1 = entry.message.indexOf(packageName!!) - 1
                    val pidStr = entry.message.substring(idx0, idx1)
                    val pid = pidStr.toInt()
                    startProc(entry.source, pid)
                    pidFound = true
                } catch (ex: Exception) {
                }

                if (!pidFound) {
                    // In some cases (old adb maybe?):
                    // https://github.com/processing/processing-android/issues/331
                    // the process start line is slightly different:
                    // I/ActivityManager(  648): Start proc processing.test.sketch_170818a for activity processing.test.sketch_170818a/.MainActivity: pid=4256 uid=10175 gids={50175}

                    try {
                        val idx0 = entry.message.indexOf("pid=") + 4
                        val idx1 = entry.message.indexOf("uid") - 1
                        val pidStr = entry.message.substring(idx0, idx1)
                        val pid = pidStr.toInt()
                        startProc(entry.source, pid)
                        pidFound = true
                    } catch (ex: Exception) {
                    }

                    if (!pidFound) {
                        System.err.println("AndroidDevice: cannot find process id, console output will be disabled.")
                    }
                }
            } else if (packageName != null && packageName != "" &&
                    entry.message.contains("Killing") &&
                    entry.message.contains(packageName!!)) {
                // Sample message string from logcat when stopping process:
                // "Killing 31360:processing.test.test1/u0a403 (adj 900): remove task"

                try {
                    val idx0 = entry.message.indexOf("Killing") + 8
                    val idx1 = entry.message.indexOf(packageName!!) - 1
                    val pidStr = entry.message.substring(idx0, idx1)
                    val pid = pidStr.toInt()
                    endProc(pid)
                } catch (ex: Exception) {
                    System.err.println("AndroidDevice: cannot find process id, console output will continue. $packageName")
                }
            } else if (entry.source == "Process") {
                handleCrash(entry)
            } else if (activeProcesses.contains(entry.pid)) {
                handleConsole(entry)
            }
        }

        private fun handleCrash(entry: LogEntry) {
            val m = SIG.matcher(entry.message)
            if (m.find()) {
                val pid = m.group(1).toInt()
                val signal = m.group(2).toInt()
                if (activeProcesses.contains(pid)) { // only report crashes of *our* sketches, por favor
                    /*
           * A crashed sketch first gets a signal 3, which causes the
           * "you've crashed" dialog to appear on the device. After
           * the user dismisses the dialog, a sig 9 is sent.
           * TODO: is it possible to forcibly dismiss the crash dialog?
           */
                    if (signal == 3) {
                        endProc(pid)
                        reportStackTrace(entry)
                    }
                }
            }
        }

        private fun handleConsole(entry: LogEntry) {
            val isStackTrace = entry.source == "AndroidRuntime" && entry.severity == LogEntry.Severity.Error
            if (isStackTrace) {
                if (!entry.message.startsWith("Uncaught handler")) {
                    stackTrace.add(entry.message)
                    System.err.println(entry.message)
                }
            } else if (entry.source == "System.out" || entry.source == "System.err") {
                if (entry.severity.useErrorStream) {
                    System.err.println(entry.message)
                } else {
                    println(entry.message)
                }
            }
        }
    }

    private fun reportStackTrace(entry: LogEntry) {
        if (stackTrace.isEmpty()) {
            System.err.println("That's weird. Proc " + entry.pid
                    + " got signal 3, but there's no stack trace.")
        }
        val stackCopy = Collections
                .unmodifiableList(ArrayList(stackTrace))
        for (listener in listeners) {
            listener.stackTrace(stackCopy)
        }
        stackTrace.clear()
    }

    @Throws(IOException::class, InterruptedException::class)
    fun initialize() {
        adb("logcat", "-c")
        val cmd = generateAdbCommand("logcat", "-v", "brief")
        val title = PApplet.join(cmd, ' ')
        logcat = Runtime.getRuntime().exec(cmd)
        ProcessRegistry.watch(logcat)
        StreamPump(logcat!!.inputStream, "log: $title").addTarget(
                LogLineProcessor()).start()
        StreamPump(logcat!!.errorStream, "err: $title").addTarget(
                System.err).start()
        Thread(Runnable {
            try {
                logcat!!.waitFor()
            } catch (e: InterruptedException) {
                System.err
                        .println("AndroidDevice: logcat process monitor interrupted")
            } finally {
                shutdown()
            }
        }, "AndroidDevice: logcat process monitor").start()
        //    System.err.println("Receiving log entries from " + id);
    }

    @Synchronized
    fun shutdown() {
        if (!isAlive) {
            return
        }
        //    System.err.println(id + " is shutting down.");
        if (logcat != null) {
            logcat!!.destroy()
            logcat = null
            ProcessRegistry.unwatch(logcat)
        }
        env.deviceRemoved(this)
        if (activeProcesses.size > 0) {
            for (listener in listeners) {
                listener.sketchStopped()
            }
        }
        listeners.clear()
    }

    @get:Synchronized
    val isAlive: Boolean
        get() = logcat != null

    private fun startProc(name: String, pid: Int) {
        //    System.err.println("Process " + name + " started at pid " + pid);
        activeProcesses.add(pid)
    }

    private fun endProc(pid: Int) {
        //    System.err.println("Process " + pid + " stopped.");
        activeProcesses.remove(pid)
        for (listener in listeners) {
            listener.sketchStopped()
        }
    }

    fun addListener(listener: DeviceListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DeviceListener) {
        listeners.remove(listener)
    }

    @Throws(InterruptedException::class, IOException::class)
    private fun adb(vararg cmd: String): ProcessResult {
        val adbCmd = generateAdbCommand(*cmd)
        return env.sdk.runADB(*adbCmd)
    }

    @Throws(IOException::class)
    private fun generateAdbCommand(vararg cmd: String): Array<String> {
        val toolsPath = env.sdk.platformToolsFolder
        val abdPath = if (Platform.isWindows()) File(toolsPath, "adb.exe") else File(toolsPath, "adb")
        return PApplet.concat(arrayOf(abdPath.canonicalPath, "-s", id), cmd)
    }

    override fun toString(): String {
        return "[AndroidDevice $id]"
    }

    override fun equals(obj: Any?): Boolean {
        return (obj as Device?)!!.id == id
    }

    companion object {
        // I/Process ( 9213): Sending signal. PID: 9213 SIG: 9
        private val SIG = Pattern
                .compile("PID:\\s+(\\d+)\\s+SIG:\\s+(\\d+)")
    }

    // constructor or initializer block
    // this block will be executed very first
    init {
        // http://android.stackexchange.com/questions/82169/howto-get-devices-features-with-adb
        var concat = ""

        try {
            val res = adb("shell", "getprop", "ro.build.characteristics")
            for (line in res) {
                concat += "," + line.toLowerCase()
            }
        } catch (e: Exception) {
        }

        features = concat
    }
}