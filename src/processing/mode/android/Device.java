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

package processing.mode.android;

import processing.app.Base;
import processing.app.RunnerListener;
import processing.app.exec.LineProcessor;
import processing.app.exec.ProcessRegistry;
import processing.app.exec.ProcessResult;
import processing.app.exec.StreamPump;
import processing.core.PApplet;
import processing.mode.android.LogEntry.Severity;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class Device {
  private final Devices env;
  private final String id;
  private final String features;  
  private final Set<Integer> activeProcesses = new HashSet<Integer>();
  private final Set<DeviceListener> listeners = 
    Collections.synchronizedSet(new HashSet<DeviceListener>());
  
//  public static final String APP_STARTED = "android.device.app.started";
//  public static final String APP_ENDED = "android.device.app.ended";

  private String packageName = "";
  
  // mutable state
  private Process logcat;

  public Device(final Devices env, final String id) {
    this.env = env;
    this.id = id;
    
    // http://android.stackexchange.com/questions/82169/howto-get-devices-features-with-adb
    String concat = ""; 
    try {
      final ProcessResult res = adb("shell", "getprop", "ro.build.characteristics");
      for (String line : res) {
        concat += "," + line.toLowerCase();     
      }      
    } catch (final Exception e) {
    }
    this.features = concat;
  }

  public void bringLauncherToFront() {
    try {
      adb("shell", "am", "start", 
          "-a", "android.intent.action.MAIN", 
          "-c", "android.intent.category.HOME");
    } catch (final Exception e) {
      e.printStackTrace(System.err);
    }
  }

  public boolean hasFeature(String feature) {
    return -1 < features.indexOf(feature);
  }
  
  public String getName() {
    String name = "";

    try {
      ProcessResult result = AndroidSDK.runADB("-s", id, "shell", "getprop", "ro.product.brand");
      if (result.succeeded()) {
        name += result.getStdout() + " ";
      }

      result = AndroidSDK.runADB("-s", id, "shell", "getprop", "ro.product.model");
      if (result.succeeded()) {
        name += result.getStdout();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    name += " [" + id + "]";
    
//    if (hasFeature("watch")) {
//      name += " (watch)";      
//    }

    return name;
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
  public boolean installApp(final AndroidBuild build, final RunnerListener status) {
    if (!isAlive()) {
      return false;
    }
    bringLauncherToFront();
    
    String apkPath = build.getPathForAPK();
    if (apkPath == null) {
      status.statusError("Could not install the sketch.");
      System.err.println("The APK file is missing");      
      return false;
    }
    
    try {
      final ProcessResult installResult = adb("install", "-r", apkPath);
      if (!installResult.succeeded()) {
        status.statusError("Could not install the sketch.");
        System.err.println(installResult);
        return false;
      }
      String errorMsg = null;
      for (final String line : installResult) {
        if (line.startsWith("Failure")) {
          errorMsg = line.substring(8);

          if (line.contains("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES")) {
            boolean removeResult = removeApp(build.getPackageName());
            if (removeResult) return installApp(build, status);
          }

          System.err.println(line);
        }
      }
      if (errorMsg == null) {
        status.statusNotice("Done installing.");
        return true;
      }
      status.statusError("Error while installing " + errorMsg);
    } catch (final IOException e) {
      status.statusError(e);
    } catch (final InterruptedException e) {
    }
    return false;
  }

  public boolean removeApp(String packageName) throws IOException, InterruptedException {
    final ProcessResult removeResult = adb("uninstall", packageName);

    if (!removeResult.succeeded()) {
      return false;
    }

    return true;
  }

  
  // different version that actually runs through JDI:
  // http://asantoso.wordpress.com/2009/09/26/using-jdb-with-adb-to-debugging-of-android-app-on-a-real-device/
  public boolean launchApp(final String packageName)
      throws IOException, InterruptedException {
    if (!isAlive()) {
      return false;
    }
    String[] cmd = {
      "shell", "am", "start",
      "-e", "debug", "true",
      "-a", "android.intent.action.MAIN",
      "-c", "android.intent.category.LAUNCHER",
      "-n", packageName + "/.MainActivity"
    };
//    PApplet.println(cmd);
    ProcessResult pr = adb(cmd);
    if (Base.DEBUG) {
      System.out.println(pr.toString());
    }
    // Sometimes this shows up on stdout, even though it returns 'success'
    // Error type 2
    // android.util.AndroidException: Can't connect to activity manager; is the system running?
    if (pr.getStdout().contains("android.util.AndroidException")) {
      System.err.println(pr.getStdout());
      return false;
    }
    return pr.succeeded();
  }

  public boolean isEmulator() {
    return id.startsWith("emulator");
  }

  public void setPackageName(String pkgName) {
    packageName = pkgName;
  }
  
  // I/Process ( 9213): Sending signal. PID: 9213 SIG: 9
  private static final Pattern SIG = Pattern
      .compile("PID:\\s+(\\d+)\\s+SIG:\\s+(\\d+)");

  private final List<String> stackTrace = new ArrayList<String>();

  private class LogLineProcessor implements LineProcessor {
    public void processLine(final String line) {
      final LogEntry entry = new LogEntry(line);
//      System.err.println("***************************************************");
//      System.out.println(line);
//      System.err.println(activeProcesses);
//      System.err.println(entry.message);
      
      if (entry.message.startsWith("PROCESSING")) {
        // Old start/stop process detection, does not seem to work anymore. 
        // Should be ok to remove at some point.
        if (entry.message.contains("onStart")) {
          startProc(entry.source, entry.pid);
        } else if (entry.message.contains("onStop")) {
          endProc(entry.pid);
        }
      } else if (packageName != null && !packageName.equals("") &&
                 entry.message.contains("Start proc") &&                  
                 entry.message.contains(packageName)) {
        // Sample message string from logcat when starting process:
        // "Start proc 29318:processing.test.sketch001/u0a403 for activity processing.test.sketch001/.MainActivity"
        try {
          int idx0 = entry.message.indexOf("Start proc") + 11;
          int idx1 = entry.message.indexOf(packageName) - 1;
          String pidStr = entry.message.substring(idx0, idx1);
          int pid = Integer.parseInt(pidStr);
          startProc(entry.source, pid);
        } catch (Exception ex) {
          System.err.println("AndroidDevice: cannot find process id, console output will be disabled.");
        }        
      } else if (packageName != null && !packageName.equals("") &&
                 entry.message.contains("Killing") &&                
                 entry.message.contains(packageName)) { 
        // Sample message string from logcat when stopping process:      
        // "Killing 31360:processing.test.test1/u0a403 (adj 900): remove task"
        try {
          int idx0 = entry.message.indexOf("Killing") + 8;
          int idx1 = entry.message.indexOf(packageName) - 1;
          String pidStr = entry.message.substring(idx0, idx1);
          int pid = Integer.parseInt(pidStr);
          endProc(pid);
        } catch (Exception ex) {
          System.err.println("AndroidDevice: cannot find process id, console output will continue. " + packageName);
        }        
      } else if (entry.source.equals("Process")) {
        handleCrash(entry);
      } else if (activeProcesses.contains(entry.pid)) {
        handleConsole(entry);
      }
    }

    private void handleCrash(final LogEntry entry) {
      final Matcher m = SIG.matcher(entry.message);
      if (m.find()) {
        final int pid = Integer.parseInt(m.group(1));
        final int signal = Integer.parseInt(m.group(2));
        if (activeProcesses.contains(pid)) { // only report crashes of *our* sketches, por favor
          /*
           * A crashed sketch first gets a signal 3, which causes the
           * "you've crashed" dialog to appear on the device. After
           * the user dismisses the dialog, a sig 9 is sent.
           * TODO: is it possible to forcibly dismiss the crash dialog?
           */
          if (signal == 3) {
            endProc(pid);
            reportStackTrace(entry);
          }
        }
      }
    }

    private void handleConsole(final LogEntry entry) {
      final boolean isStackTrace = entry.source.equals("AndroidRuntime")
          && entry.severity == Severity.Error;
      if (isStackTrace) {
        if (!entry.message.startsWith("Uncaught handler")) {
          stackTrace.add(entry.message);
          System.err.println(entry.message);
        }
      } else if (entry.source.equals("System.out")
          || entry.source.equals("System.err")) {
        if (entry.severity.useErrorStream) {
          System.err.println(entry.message);
        } else {
          System.out.println(entry.message);
        }
      }
    }
  }

  private void reportStackTrace(final LogEntry entry) {
    if (stackTrace.isEmpty()) {
      System.err.println("That's weird. Proc " + entry.pid
          + " got signal 3, but there's no stack trace.");
    }
    final List<String> stackCopy = Collections
        .unmodifiableList(new ArrayList<String>(stackTrace));
    for (final DeviceListener listener : listeners) {
      listener.stackTrace(stackCopy);
    }
    stackTrace.clear();
  }

  void initialize() throws IOException, InterruptedException {
    adb("logcat", "-c");
    final String[] cmd = generateAdbCommand("logcat", "-v", "brief");
    final String title = PApplet.join(cmd, ' ');
    logcat = Runtime.getRuntime().exec(cmd);
    ProcessRegistry.watch(logcat);
    new StreamPump(logcat.getInputStream(), "log: " + title).addTarget(
      new LogLineProcessor()).start();
    new StreamPump(logcat.getErrorStream(), "err: " + title).addTarget(
      System.err).start();
    new Thread(new Runnable() {
      public void run() {
        try {
          logcat.waitFor();
          //          final int result = logcat.waitFor();
          //          System.err.println("AndroidDevice: " + getId() + " logcat exited "
          //              + (result == 0 ? "normally" : "with status " + result));
        } catch (final InterruptedException e) {
          System.err
              .println("AndroidDevice: logcat process monitor interrupted");
        } finally {
          shutdown();
        }
      }
    }, "AndroidDevice: logcat process monitor").start();
    //    System.err.println("Receiving log entries from " + id);
  }

  synchronized void shutdown() {
    if (!isAlive()) {
      return;
    }
    //    System.err.println(id + " is shutting down.");
    if (logcat != null) {
      logcat.destroy();
      logcat = null;
      ProcessRegistry.unwatch(logcat);
    }
    env.deviceRemoved(this);
    if (activeProcesses.size() > 0) {
      for (final DeviceListener listener : listeners) {
        listener.sketchStopped();
      }
    }
    listeners.clear();
  }

  synchronized boolean isAlive() {
    return logcat != null;
  }

  public String getId() {
    return id;
  }

  public Devices getEnv() {
    return env;
  }

  private void startProc(final String name, final int pid) {
    //    System.err.println("Process " + name + " started at pid " + pid);
    activeProcesses.add(pid);
  }

  private void endProc(final int pid) {
    //    System.err.println("Process " + pid + " stopped.");
    activeProcesses.remove(pid);
    for (final DeviceListener listener : listeners) {
      listener.sketchStopped();
    }
  }

  public void addListener(final DeviceListener listener) {
    listeners.add(listener);
  }

  public void removeListener(final DeviceListener listener) {
    listeners.remove(listener);
  }

  private ProcessResult adb(final String... cmd) throws InterruptedException, IOException {
    final String[] adbCmd = generateAdbCommand(cmd);
    return AndroidSDK.runADB(adbCmd);
  }

  private String[] generateAdbCommand(final String... cmd) {
    //    final String[] adbCmd = new String[3 + cmd.length];
    //    adbCmd[0] = "adb";
    //    adbCmd[1] = "-s";
    //    adbCmd[2] = getId();
    //    System.arraycopy(cmd, 0, adbCmd, 3, cmd.length);
    //    return adbCmd;
    return PApplet.concat(new String[] { "adb", "-s", getId() }, cmd);
  }

  @Override
  public String toString() {
    return "[AndroidDevice " + getId() + "]";
  }

  @Override
  public boolean equals(Object obj) {
    return ((Device) obj).getId().equals(getId());
  }
}
