/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2012-16 The Processing Foundation
 Copyright (c) 2008-12 Ben Fry and Casey Reas

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

import processing.app.*
import processing.app.contrib.ModeContribution

import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.io.UnsupportedEncodingException
import kotlin.system.exitProcess

/**
 * Class to handle running Android mode of Processing from the command line.
 *
 * @author ostap.andrusiv
 * @author Aditya Rana
 */
internal class Commander(args: Array<String>) : RunnerListener {

    private var androidMode: AndroidMode? = null
    private var task = HELP

    private var sketch: Sketch? = null
    private var systemOut: PrintStream? = null
    private var systemErr: PrintStream? = null
    private var sketchPath: String? = null
    private var sketchFolder: File? = null
    private var pdePath: String? = null // path to the .pde file
    private var outputPath: String? = null
    private var outputFolder: File? = null

    private var appComponent = AndroidBuild.APP
    private var force = false // replace that no good output folder
    private var device = runArg_DEVICE
    private var target = targetArg_DEBUG


    // constructor or initialization block
    init {
        if (Base.DEBUG) {
            println(args.contentToString())
        }

        // Turns out the output goes as MacRoman or something else useless.
        // http://code.google.com/p/processing/issues/detail?id=1418

        try {
            systemOut = PrintStream(System.out, true, "UTF-8")
            systemErr = PrintStream(System.err, true, "UTF-8")
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
            exitProcess(1)
        }

        parseArgs(args)

        initValues()
    }

    private fun parseArgs(args: Array<String>) {
        for (arg in args) {
            if (arg.isEmpty()) {
                // ignore it, just the crappy shell script
            } else if (arg == helpArg) {
                // mode already set to HELP
            } else if (arg.startsWith(targetArg)) {
                target = extractValue(arg, targetArg, targetArg_DEBUG)
            } else if (arg.startsWith(componentArg)) {
                val compStr = extractValue(arg, targetArg, targetArg_FRAGMENT)
                if (compStr == targetArg_FRAGMENT) {
                    appComponent = AndroidBuild.APP
                } else if (compStr == targetArg_WALLPAPER) {
                    appComponent = AndroidBuild.WALLPAPER
                } else if (compStr == targetArg_WATCHFACE) {
                    appComponent = AndroidBuild.WATCHFACE
                } else if (compStr == targetArg_VR) {
                    appComponent = AndroidBuild.VR
                } else if (compStr == targetArg_AR) {
                    appComponent = AndroidBuild.AR
                }
            } else if (arg == buildArg) {
                task = BUILD
            } else if (arg.startsWith(runArg)) {
                task = RUN
                device = extractValue(arg, runArg, runArg_DEVICE)
            } else if (arg == exportApplicationArg) {
                task = EXPORT
            } else if (arg.startsWith(sketchArg)) {
                sketchPath = arg.substring(sketchArg.length)
                sketchFolder = File(sketchPath)
                checkOrQuit(sketchFolder!!.exists(), sketchFolder.toString() + " does not exist.", false)

                val pdeFile = File(sketchFolder, sketchFolder!!.name + ".pde")
                checkOrQuit(pdeFile.exists(), "Not a valid sketch folder. $pdeFile does not exist.", true)

                pdePath = pdeFile.absolutePath
            } else if (arg.startsWith(outputArg)) {
                outputPath = arg.substring(outputArg.length)
            } else if (arg == forceArg) {
                force = true
            } else {
                complainAndQuit("I don't know anything about $arg.", true)
            }

        }

    }

    private fun initValues() {
        checkOrQuit(outputPath != null, "An output path must be specified.", true)

        outputFolder = File(outputPath)

        if (outputFolder!!.exists()) {
            if (force) {
                Util.removeDir(outputFolder)
            } else {
                complainAndQuit("The output folder already exists. " + "Use --force to remove it.", false)
            }
        }

        Preferences.init()
        Base.locateSketchbookFolder()

        checkOrQuit(sketchPath != null, "No sketch path specified.", true)
        checkOrQuit(outputPath != sketchPath, "The sketch path and output path cannot be identical.", false)

        androidMode = ModeContribution.load(null, Platform.getContentFile("modes/android"),
                "processing.mode.android.AndroidMode").mode as AndroidMode
        androidMode!!.checkSDK(null)
    }

    private fun execute() {
        if (Base.DEBUG) {
            systemOut!!.println("Build status: ")
            systemOut!!.println("Sketch:    $sketchPath")
            systemOut!!.println("Output:    $outputPath")
            systemOut!!.println("Force:     $force")
            systemOut!!.println("Target:    $target")
            systemOut!!.println("Component: $appComponent")
            systemOut!!.println("==== Task ====")
            systemOut!!.println("--build:   " + (task == BUILD))
            systemOut!!.println("--run:     " + (task == RUN))
            systemOut!!.println("--export:  " + (task == EXPORT))
            systemOut!!.println()
        }

        if (task == HELP) {
            printCommandLine(systemOut)
            exitProcess(0)
        }

        checkOrQuit(outputFolder!!.mkdirs(), "Could not create the output folder.", false)

        var success = false

        try {
            val runOnEmu = runArg_EMULATOR == device
            sketch = Sketch(pdePath, androidMode)
            if (task == BUILD || task == RUN) {
                val build = androidMode?.let { AndroidBuild(sketch, it, appComponent) }
                build!!.build(target)

                if (task == RUN) {
                    val runner = AndroidRunner(build, this)
                    runner.launch(if (runOnEmu) Devices.instance.getEmulator(build!!.isWear) else Devices.instance.hardware,
                            build!!.appComponent,
                            runOnEmu)

                }

                success = true

            } else if (task == EXPORT) {
                val build = androidMode?.let { AndroidBuild(sketch, it, appComponent) }
                build!!.exportProject()

                success = true
            }

            if (!success) { // error already printed
                exitProcess(1)
            }

            systemOut!!.println("Finished.")
            exitProcess(0)

        } catch (re: SketchException) {
            statusError(re)
        } catch (e: IOException) {
            e.printStackTrace()
            exitProcess(1)
        }

    }

    override fun statusNotice(message: String) {
        systemErr!!.println(message)
    }

    override fun statusError(message: String) {
        systemErr!!.println(message)
    }

    override fun statusError(exception: Exception) {
        if (exception is SketchException) {
            val re = exception

            val codeIndex = re.codeIndex

            if (codeIndex != -1) {
                // format the runner exception like emacs
                // blah.java:2:10:2:13: Syntax Error: This is a big error message
                val filename = sketch!!.getCode(codeIndex).fileName
                val line = re.codeLine + 1
                val column = re.codeColumn + 1
                // if (column == -1) column = 0;
                // TODO if column not specified, should just select the whole line.
                systemErr!!.println(filename + ":" + line + ":" + column + ":" + line + ":" + column + ":" + " "
                        + re.message)

            } else { // no line number, pass the trace along to the user
                exception.printStackTrace()
            }
        } else {
            exception.printStackTrace()
        }

    }

    private fun checkOrQuit(condition: Boolean, lastWords: String, schoolEmFirst: Boolean) {
        if (!condition) {
            complainAndQuit(lastWords, schoolEmFirst)
        }
    }

    private fun complainAndQuit(lastWords: String?, schoolEmFirst: Boolean) {

        if (schoolEmFirst) {
            printCommandLine(systemErr)
        }

        systemErr!!.println(lastWords)
        exitProcess(1)
    }


    companion object {
        const val helpArg = "--help"
        const val buildArg = "--build"
        const val runArg = "--run"
        const val runArg_DEVICE = "d"
        const val runArg_EMULATOR = "e"
        const val targetArg = "--target"
        const val targetArg_DEBUG = "debug"
        const val targetArg_RELEASE = "release"
        const val componentArg = "--component"
        const val targetArg_FRAGMENT = "fragment"
        const val targetArg_WALLPAPER = "wallpaper"
        const val targetArg_WATCHFACE = "watchface"
        const val targetArg_VR = "vr"
        const val targetArg_AR = "ar"
        const val sketchArg = "--sketch="
        const val forceArg = "--force"
        const val outputArg = "--output="
        const val exportApplicationArg = "--export"

        const val HELP = -1
        const val BUILD = 1
        const val RUN = 2
        const val EXPORT = 4

        @JvmStatic
        fun main(args: Array<String>) {
            // Do this early so that error messages go to the console
            Base.setCommandLine()
            // init the platform so that prefs and other native code is ready to go
            Platform.init()

            // launch command line handler
            val commander = Commander(args)
            commander.execute()
        }

        /**
         * <pre>
         * extractValue("--target=release", "--target", "debug") ==> "release"
         * extractValue("--target=",        "--target", "debug") ==> ""
         * extractValue("--target",         "--target", "debug") ==> "debug"
        </pre>
         *
         * @param arg
         * @param template
         * @param def
         * @return
         */
        private fun extractValue(arg: String, template: String, def: String): String {
            var result = def
            val withEq = arg.substring(template.length)
            if (withEq.startsWith("=")) {
                result = withEq.substring(1)
            }
            return result
        }

        fun printCommandLine(out: PrintStream?) {
            out!!.println()
            out.println("Command line edition for Processing " + Base.getVersionName() + " (Android Mode)")
            out.println()
            out.println("--help               Show this help text. Congratulations.")
            out.println()
            out.println("--sketch=<name>      Specify the sketch folder (required)")
            out.println("--output=<name>      Specify the output folder (required and")
            out.println("                     cannot be the same as the sketch folder.)")
            out.println()
            out.println("--force              The sketch will not build if the output")
            out.println("                     folder already exists, because the contents")
            out.println("                     will be replaced. This option erases the")
            out.println("                     folder first. Use with extreme caution!")
            out.println()
            out.println("--target=<target>    \"debug\" or \"release\" target.")
            out.println("                     \"debug\" by default.")
            out.println("--build              Preprocess and compile a sketch into .apk file.")
            out.println("--run=<d|e>          Preprocess, compile, and run a sketch on device")
            out.println("                     or emulator. Device will be used by default.")
            out.println()
            out.println("--export             Export an application.")
            out.println()
        }

    }


    override fun startIndeterminate() {

    }

    override fun stopIndeterminate() {

    }

    override fun statusHalt() {

    }

    override fun isHalted(): Boolean {
        return false
    }

}