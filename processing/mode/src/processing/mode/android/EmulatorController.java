/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-21 The Processing Foundation

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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import processing.app.Base;
import processing.app.exec.*;

import processing.core.PApplet;


class EmulatorController {
  public static enum State {
    NOT_RUNNING, WAITING_FOR_BOOT, RUNNING
  }

  private volatile State state = State.NOT_RUNNING;


  public State getState() {
    return state;
  }

  
  public void setState(final State state) {
    if (processing.app.Base.DEBUG) {
      //System.out.println("Emulator state: " + state);
      new Exception("setState(" + state + ") called").printStackTrace(System.out);
    }
    this.state = state;
  }
  

  /**
   * Blocks until emulator is running, or some catastrophe happens.
   * @throws IOException
   */
  synchronized public void launch(final AndroidSDK sdk, final boolean wear) 
      throws IOException {
    if (state != State.NOT_RUNNING) {
      String illegal = "You can't launch an emulator whose state is " + state;
      throw new IllegalStateException(illegal);
    }

    // Emulator options:
    // https://developer.android.com/studio/run/emulator-commandline.html
    String avdName = AVD.getName(wear);
    
    final String portString = AVD.getPreferredPort(wear);
        
    // We let the emulator decide what's better for hardware acceleration:
    // https://developer.android.com/studio/run/emulator-acceleration.html#accel-graphics
    String gpuFlag = "auto";
    
    final File emulator = sdk.getEmulatorTool();
    if (emulator == null || !emulator.exists()) {
      System.err.println("EmulatorController: Emulator is not available.");
      return;      
    }
    
    final String[] cmd = new String[] {
      emulator.getCanonicalPath(),
      "-avd", avdName,
      "-port", portString,
      "-gpu", gpuFlag
    };
    
    //System.err.println("EmulatorController: Launching emulator");
    if (Base.DEBUG) {
      System.out.println(processing.core.PApplet.join(cmd, " "));
    }
    //ProcessResult adbResult = new ProcessHelper(adbCmd).execute();
    final Process p = Runtime.getRuntime().exec(cmd);
    ProcessRegistry.watch(p);
//    new StreamPump(p.getInputStream(), "emulator: ").addTarget(System.out).start();

    // if we've gotten this far, then we've at least succeeded in finding and
    // beginning execution of the emulator, so we are now officially "Launched"
    setState(State.WAITING_FOR_BOOT);

    final String title = PApplet.join(cmd, ' ');

    // when this shows up on stdout:
    // emulator: ERROR: the cache image is used by another emulator. aborting
    // need to reset adb and try again, since it's running but adb is hosed
    StreamPump outie = new StreamPump(p.getInputStream(), "out: " + title);
    outie.addTarget(new LineProcessor() {
      public void processLine(String line) {
        if (line.contains("the cache image is used by another emulator")) {

        } else {
//          System.out.println(line);
          System.out.println(title + ": " + line);
        }
      }
    });
    //new StreamPump(p.getInputStream(), "out: " + title).addTarget(System.out).start();

    // suppress this warning on OS X, otherwise we're gonna get a lot of reports:
    // 2010-04-13 15:26:56.380 emulator[91699:903] Warning once: This
    // application, or a library it uses, is using NSQuickDrawView, which has
    // been deprecated. Apps should cease use of QuickDraw and move to Quartz.
    StreamPump errie = new StreamPump(p.getErrorStream(), "err: " + title);
    errie.addTarget(new LineProcessor() {
      public void processLine(String line) {
        if (line.contains("This application, or a library it uses, is using NSQuickDrawView")) {
          // i don't really care
        } else {
//          System.err.println(line);
          System.err.println(title + ": " + line);
        }
      }
    });
    //new StreamPump(p.getErrorStream(), "err: " + title).addTarget(System.err).start();

    final CountDownLatch latch = new CountDownLatch(1);
    new Thread(new Runnable() {
      public void run() {
        try {
          //System.err.println("EmulatorController: Waiting for boot.");
          while (state == State.WAITING_FOR_BOOT) {
            if (processing.app.Base.DEBUG) {
              System.out.println("sleeping for 2 seconds " + new java.util.Date().toString());
            }
            Thread.sleep(2000);
            //System.out.println("done sleeping");
            ProcessResult result = sdk.runAdb("-s", "emulator-" + portString,
              "shell", "getprop", "dev.bootcomplete");
            if (result.getStdout().trim().equals("1")) {
              setState(State.RUNNING);
              return;
            }
          }
          System.err.println("EmulatorController: Emulator never booted. " + state);
        } catch (Exception e) {
          System.err.println("Exception while waiting for emulator to boot:");
          e.printStackTrace();
          p.destroy();
        } finally {
          latch.countDown();
        }
      }
    }, "EmulatorController: Wait for emulator to boot").start();
    new Thread(new Runnable() {
      public void run() {
        try {
          final int result = p.waitFor();
          // On Windows (as of SDK tools 15), emulator.exe process will terminate
          // immediately, even though the emulator itself is launching correctly.
          // However on OS X and Linux the process will stay open.
          if (result != 0) {
            System.err.println("Emulator process exited with status " + result + ".");
            setState(State.NOT_RUNNING);
          }
        } catch (InterruptedException e) {
          System.err.println("Emulator was interrupted.");
          setState(State.NOT_RUNNING);
        } finally {
          p.destroy();
          ProcessRegistry.unwatch(p);
        }
      }
    }, "EmulatorController: emulator process waitFor()").start();
    try {
      latch.await();
    } catch (final InterruptedException drop) {
      System.err.println("Interrupted while waiting for emulator to launch.");
    }
  }

  
  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
  
  
  // whoever called them "design patterns" certainly wasn't a f*king designer.
  
  public static EmulatorController getInstance(boolean wear) {
    if (wear) {
      return INSTANCE_WEAR;
    } else {
      return INSTANCE;
    }
  }

  private static final EmulatorController INSTANCE = new EmulatorController();
  private static final EmulatorController INSTANCE_WEAR = new EmulatorController();
}
