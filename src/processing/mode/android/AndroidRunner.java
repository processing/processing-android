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

package processing.mode.android;

import java.io.PrintStream;
import java.security.PublicKey;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.nashorn.internal.runtime.linker.Bootstrap;
import processing.app.ui.Editor;
import processing.app.Messages;
import processing.app.RunnerListener;
import processing.app.SketchException;
import processing.mode.java.runner.Runner;

/** 
 * Launches an app on the device or in the emulator.
 */
public class AndroidRunner implements DeviceListener {
  AndroidBuild build;
  RunnerListener listener;

  protected PrintStream sketchErr;
  protected PrintStream sketchOut;

  public boolean debugEnabled;

  public AndroidRunner(AndroidBuild build, RunnerListener listener) {
    this.build = build;
    this.listener = listener;

    if (listener instanceof Editor) {
      Editor editor = (Editor) listener;
      sketchErr = editor.getConsole().getErr();
      sketchOut = editor.getConsole().getOut();
    } else {
      sketchErr = System.err;
      sketchOut = System.out;
    }
  }


  public boolean launch(Future<Device> deviceFuture, int comp, boolean emu) {
    String devStr = emu ? "emulator" : "device";
    listener.statusNotice("Waiting for " + devStr + " to become available...");
    
    final Device device = waitForDevice(deviceFuture, listener);
    if (device == null || !device.isAlive()) {
      listener.statusError("Lost connection with " + devStr + " while launching. Try again.");
      // Reset the server, in case that's the problem. Sometimes when
      // launching the emulator times out, the device list refuses to update.
      final Devices devices = Devices.getInstance();
      devices.killAdbServer();
      return false;
    }
    
    if (comp == AndroidBuild.WATCHFACE && !device.hasFeature("watch")) {
      listener.statusError("Could not install the sketch.");
      Messages.showWarning("Selected device is not a watch...",
          "You are trying to install a watch face on a non-watch device.\n" +
          "Select the correct device, or use the emulator.");      
      return false;
    }
    
    if (comp != AndroidBuild.WATCHFACE && device.hasFeature("watch")) {
      listener.statusError("Could not install the sketch.");
      Messages.showWarning("Selected device is a watch...",
          "You are trying to install a non-watch app on a watch.\n" +
          "Select the correct device, or use the emulator.");      
      return false;
    }

    device.addListener(this);
    device.setPackageName(build.getPackageName());
    device.setSketchClassName(build.getSketchClassName());
    device.debugEnabled = debugEnabled;
    listener.statusNotice("Installing sketch on " + device.getId());
    // this stopped working with Android SDK tools revision 17
    if (!device.installApp(build, listener)) {
      listener.statusError("Lost connection with " + devStr + " while installing. Try again.");
      final Devices devices = Devices.getInstance();
      devices.killAdbServer();  // see above
      return false;
    }

    boolean status = false;
    if (comp == AndroidBuild.WATCHFACE || comp == AndroidBuild.WALLPAPER) {
      if (startSketch(build, device)) {
        listener.statusNotice("Sketch installed "
                              + (device.isEmulator() ? "in the emulator" : "on the device") + ".");
        status = true;
      } else {
        listener.statusError("Could not install the sketch.");
      }
    } else {
      listener.statusNotice("Starting sketch on " + device.getId());
      if (startSketch(build, device)) {
        listener.statusNotice("Sketch launched "
                              + (device.isEmulator() ? "in the emulator" : "on the device") + ".");
        status = true;
      } else {
        listener.statusError("Could not start the sketch.");
      }
    }
    
    listener.stopIndeterminate();
    lastRunDevice = device;
    return status;
  }


  private volatile Device lastRunDevice = null;


  // if user asks for 480x320, 320x480, 854x480 etc, then launch like that
  // though would need to query the emulator to see if it can do that

  private boolean startSketch(AndroidBuild build, final Device device) {
    final String packageName = build.getPackageName();
    try {
      if (device.launchApp(packageName)) {
        return true;
      }
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }
    return false;
  }


  private Device waitForDevice(Future<Device> deviceFuture, RunnerListener listener) {
    for (int i = 0; i < 120; i++) {
      if (listener.isHalted()) {
        deviceFuture.cancel(true);
        return null;
      }
      try {
        return deviceFuture.get(1, TimeUnit.SECONDS);
      } catch (final InterruptedException e) {
        listener.statusError("Interrupted.");
        return null;
      } catch (final ExecutionException e) {
        listener.statusError(e);
        return null;
      } catch (final TimeoutException expected) {
      }
    }
    listener.statusError("No, on second thought, I'm giving up " +
                         "on waiting for that device to show up.");
    return null;
  }


  private static final Pattern LOCATION =
    Pattern.compile("\\(([^:]+):(\\d+)\\)");
  private static final Pattern EXCEPTION_PARSER =
    Pattern.compile("^\\s*([a-z]+(?:\\.[a-z]+)+)(?:: .+)?$",
                    Pattern.CASE_INSENSITIVE);

  /**
   * Currently figures out the first relevant stack trace line
   * by looking for the telltale presence of "processing.android"
   * in the package. If the packaging for droid sketches changes,
   * this method will have to change too.
   */
  public void stackTrace(final List<String> trace) {
    final Iterator<String> frames = trace.iterator();
    final String exceptionLine = frames.next();

    final Matcher m = EXCEPTION_PARSER.matcher(exceptionLine);
    if (!m.matches()) {
      System.err.println("Can't parse this exception line:");
      System.err.println(exceptionLine);
      listener.statusError("Unknown exception");
      return;
    }
    final String exceptionClass = m.group(1);
    Runner.handleCommonErrors(exceptionClass, exceptionLine, listener, sketchErr);

    while (frames.hasNext()) {
      final String line = frames.next();
      if (line.contains("processing.android")) {
        final Matcher lm = LOCATION.matcher(line);
        if (lm.find()) {
          final String filename = lm.group(1);
          final int lineNumber = Integer.parseInt(lm.group(2)) - 1;
          final SketchException rex =
            build.placeException(exceptionLine, filename, lineNumber);
          listener.statusError(rex == null ? new SketchException(exceptionLine, false) : rex);
          return;
        }
      }
    }
  }


  // called by AndroidMode.handleStop()...
  public void close() {
    if (lastRunDevice != null) {
      lastRunDevice.bringLauncherToFront();
    }
  }


  // sketch stopped on the device
  public void sketchStopped() {
    listener.stopIndeterminate();
    listener.statusHalt();
  }
}
