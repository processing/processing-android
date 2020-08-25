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
import processing.app.exec.ProcessRegistry
import processing.app.exec.StreamPump

import processing.core.PApplet

import processing.mode.android.AVD.Companion.getName
import processing.mode.android.AVD.Companion.getPreferredPort

import java.io.IOException
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * @author Aditya Rana
 */
internal class EmulatorController {
    enum class State {
        NOT_RUNNING, WAITING_FOR_BOOT, RUNNING
    }

    @Volatile
    var state = State.NOT_RUNNING

    fun setstate(state: State) {

        if (Base.DEBUG) {
            //System.out.println("Emulator state: " + state);
            Exception("setState($state) called").printStackTrace(System.out)
        }

        this.state = state
    }

    /**
     * Blocks until emulator is running, or some catastrophe happens.
     * @throws IOException
     */
    @Synchronized
    @Throws(IOException::class)
    fun launch(sdk: AndroidSDK, wear: Boolean) {
        if (state != State.NOT_RUNNING) {
            val illegal = "You can't launch an emulator whose state is $state"
            throw IllegalStateException(illegal)
        }

        // Emulator options:
        // https://developer.android.com/studio/run/emulator-commandline.html
        val avdName = getName(wear)
        val portString = getPreferredPort(wear)

        // We let the emulator decide what's better for hardware acceleration:
        // https://developer.android.com/studio/run/emulator-acceleration.html#accel-graphics
        val gpuFlag = "auto"
        val emulator = sdk.emulatorTool
        val cmd = arrayOf(
                emulator!!.canonicalPath,
                "-avd", avdName,
                "-port", portString,
                "-gpu", gpuFlag
        )

        //System.err.println("EmulatorController: Launching emulator");
        if (Base.DEBUG) {
            println(PApplet.join(cmd, " "))
        }

        //ProcessResult adbResult = new ProcessHelper(adbCmd).execute();
        val p = Runtime.getRuntime().exec(cmd)

        ProcessRegistry.watch(p)

        //    new StreamPump(p.getInputStream(), "emulator: ").addTarget(System.out).start();

        // if we've gotten this far, then we've at least succeeded in finding and
        // beginning execution of the emulator, so we are now officially "Launched"
        setstate(State.WAITING_FOR_BOOT)

        val title = PApplet.join(cmd, ' ')

        // when this shows up on stdout:
        // emulator: ERROR: the cache image is used by another emulator. aborting
        // need to reset adb and try again, since it's running but adb is hosed
        val outie = StreamPump(p.inputStream, "out: $title")

        outie.addTarget { line ->
            if (line.contains("the cache image is used by another emulator")) {
            } else {
//          System.out.println(line);
                println("$title: $line")
            }
        }

        //new StreamPump(p.getInputStream(), "out: " + title).addTarget(System.out).start();

        // suppress this warning on OS X, otherwise we're gonna get a lot of reports:
        // 2010-04-13 15:26:56.380 emulator[91699:903] Warning once: This
        // application, or a library it uses, is using NSQuickDrawView, which has
        // been deprecated. Apps should cease use of QuickDraw and move to Quartz.
        val errie = StreamPump(p.errorStream, "err: $title")

        errie.addTarget { line ->
            if (line.contains("This application, or a library it uses, is using NSQuickDrawView")) {
                // i don't really care
            } else {
//          System.err.println(line);
                System.err.println("$title: $line")
            }
        }

        //new StreamPump(p.getErrorStream(), "err: " + title).addTarget(System.err).start();
        val latch = CountDownLatch(1)

        Thread(Runnable {
            try {
                //System.err.println("EmulatorController: Waiting for boot.");
                while (state == State.WAITING_FOR_BOOT) {
                    if (Base.DEBUG) {
                        println("sleeping for 2 seconds " + Date().toString())
                    }
                    Thread.sleep(2000)
                    //System.out.println("done sleeping");
                    val result = sdk.runADB("-s", "emulator-$portString",
                            "shell", "getprop", "dev.bootcomplete")
                    if (result.stdout.trim { it <= ' ' } == "1") {
                        setstate(State.RUNNING)
                        return@Runnable
                    }
                }
                System.err.println("EmulatorController: Emulator never booted. $state")
            } catch (e: Exception) {
                System.err.println("Exception while waiting for emulator to boot:")
                e.printStackTrace()
                p.destroy()
            } finally {
                latch.countDown()
            }
        }, "EmulatorController: Wait for emulator to boot").start()

        Thread(Runnable {
            try {
                val result = p.waitFor()

                // On Windows (as of SDK tools 15), emulator.exe process will terminate
                // immediately, even though the emulator itself is launching correctly.
                // However on OS X and Linux the process will stay open.
                if (result != 0) {
                    System.err.println("Emulator process exited with status $result.")
                    setstate(State.NOT_RUNNING)
                }
            } catch (e: InterruptedException) {
                System.err.println("Emulator was interrupted.")
                setstate(State.NOT_RUNNING)
            } finally {
                p.destroy()
                ProcessRegistry.unwatch(p)
            }
        }, "EmulatorController: emulator process waitFor()").start()

        try {
            latch.await()
        } catch (drop: InterruptedException) {
            System.err.println("Interrupted while waiting for emulator to launch.")
        }
    }

    companion object {
        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
        // whoever called them "design patterns" certainly wasn't a f*king designer.
        fun getInstance(wear: Boolean): EmulatorController {
            return if (wear) {
                INSTANCE_WEAR
            } else {
                INSTANCE
            }
        }

        private val INSTANCE = EmulatorController()
        private val INSTANCE_WEAR = EmulatorController()
    }
}