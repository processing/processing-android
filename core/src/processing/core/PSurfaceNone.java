/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.WatchFaceService;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.support.v4.os.ResultReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import processing.android.AppComponent;
import processing.android.PFragment;
import processing.android.ServiceEngine;
import processing.android.PermissionRequestor;

/**
 * Base surface for Android2D and OpenGL renderers.
 * It includes the implementation of the rendering thread.
 */
public class PSurfaceNone implements PSurface, PConstants {
  protected PApplet sketch;
  protected PGraphics graphics;
  protected AppComponent component;
  protected Activity activity;

  protected boolean surfaceReady;
  protected SurfaceView surfaceView;
  protected View view;

  protected WallpaperService wallpaper;
  protected WatchFaceService watchface;

  protected boolean requestedThreadStart = false;
  protected Thread thread;
  protected boolean paused;
  protected Object pauseObject = new Object();

  protected float frameRateTarget = 60;
  protected long frameRatePeriod = 1000000000L / 60L;


  @Override
  public AppComponent getComponent() {
    return component;
  }


  @Override
  public Context getContext() {
    if (component.getKind() == AppComponent.FRAGMENT) {
      return activity;
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      return wallpaper;
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      return watchface;
    }
    return null;
  }


  @Override
  public Activity getActivity() {
    return activity;
  }


  @Override
  public ServiceEngine getEngine() {
    return component.getEngine();
  }


  @Override
  public View getRootView() {
    return view;
  }


  @Override
  public String getName() {
    if (component.getKind() == AppComponent.FRAGMENT) {
      return activity.getComponentName().getPackageName();
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      return wallpaper.getPackageName();
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      return watchface.getPackageName();
    }
    return "";
  }


  @Override
  public View getResource(int id) {
    return activity.findViewById(id);
  }


  @Override
  public Rect getVisibleFrame() {
    Rect frame = new Rect();
    if (view != null) {
      // According to the docs:
      // https://developer.android.com/reference/android/view/View.html#getWindowVisibleDisplayFrame(android.graphics.Rect)
      // don't use in performance critical code like drawing.
      view.getWindowVisibleDisplayFrame(frame);
    }
    return frame;
  }


  @Override
  public void dispose() {
    sketch = null;
    graphics = null;

    if (activity != null) {
      // In API level 21 you can do
      // activity.releaseInstance();
      // to ask the app to free up its memory.
      // https://developer.android.com/reference/android/app/Activity.html#releaseInstance()
      // but seems redundant to call it here, since dispose() is triggered by
      // the onDestroy() handler, which means that the app is already
      // being destroyed.
    }

    if (view != null) {
      view.destroyDrawingCache();
    }

    if (component != null) {
      component.dispose();
    }

    if (surfaceView != null) {
      surfaceView.getHolder().getSurface().release();
    }
  }


  @Override
  public void setRootView(View view) {
    this.view = view;
  }


  @Override
  public SurfaceView getSurfaceView() {
    return surfaceView;
  }


  @Override
  // TODO this is only used by A2D, when finishing up a draw. but if the
  // surfaceview has changed, then it might belong to an a3d surfaceview. hrm.
  public SurfaceHolder getSurfaceHolder() {
    SurfaceView view = getSurfaceView();
    if (view == null) {
      // Watch faces don't have a surface view associated to them.
      return null;
    } else {
      return view.getHolder();
    }
  }


  @Override
  public void initView(int sketchWidth, int sketchHeight) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      int displayWidth = component.getDisplayWidth();
      int displayHeight = component.getDisplayHeight();
      View rootView;
      if (sketchWidth == displayWidth && sketchHeight == displayHeight) {
        rootView = getSurfaceView();
      } else {
        RelativeLayout overallLayout = new RelativeLayout(activity);
        RelativeLayout.LayoutParams lp =
          new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                          ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);

        LinearLayout layout = new LinearLayout(activity);
        layout.addView(getSurfaceView(), sketchWidth, sketchHeight);
        overallLayout.addView(layout, lp);
        overallLayout.setBackgroundColor(sketch.sketchWindowColor());
        rootView = overallLayout;
      }
      setRootView(rootView);
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      setRootView(getSurfaceView());
    }
  }


  @Override
  public void initView(int sketchWidth, int sketchHeight, boolean parentSize,
                       LayoutInflater inflater, ViewGroup container,
                       Bundle savedInstanceState) {
    // https://www.bignerdranch.com/blog/understanding-androids-layoutinflater-inflate/
    ViewGroup rootView = (ViewGroup)inflater.inflate(sketch.parentLayout, container, false);

    View view = getSurfaceView();
    if (parentSize) {
      LinearLayout.LayoutParams lp;
      lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                                         LayoutParams.MATCH_PARENT);
      lp.weight = 1.0f;
      lp.setMargins(0, 0, 0, 0);
      view.setPadding(0,0,0,0);
      rootView.addView(view, lp);
    } else {
      RelativeLayout layout = new RelativeLayout(activity);
      RelativeLayout.LayoutParams lp =
        new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.addRule(RelativeLayout.CENTER_IN_PARENT);

      layout.addView(view, sketchWidth, sketchHeight);
      rootView.addView(layout, lp);
    }
    rootView.setBackgroundColor(sketch.sketchWindowColor());
    setRootView(rootView);
  }


  @Override
  public void startActivity(Intent intent) {
    component.startActivity(intent);
  }


  @Override
  public void runOnUiThread(Runnable action) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      activity.runOnUiThread(action);
    }
  }


  @Override
  public void setOrientation(int which) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      if (which == PORTRAIT) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      } else if (which == LANDSCAPE) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      }
    }
  }


  public void setHasOptionsMenu(boolean hasMenu) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      ((PFragment)component).setHasOptionsMenu(hasMenu);
    }
  }



  @Override
  public File getFilesDir() {
    if (component.getKind() == AppComponent.FRAGMENT) {
      return activity.getFilesDir();
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      return wallpaper.getFilesDir();
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      return watchface.getFilesDir();
    }
    return null;
  }


  @Override
  public File getFileStreamPath(String path) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      return activity.getFileStreamPath(path);
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      return wallpaper.getFileStreamPath(path);
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      return watchface.getFileStreamPath(path);
    }
    return null;
  }


  @Override
  public InputStream openFileInput(String filename) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      try {
        return activity.openFileInput(filename);
      } catch (FileNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }


  @Override
  public AssetManager getAssets() {
    if (component.getKind() == AppComponent.FRAGMENT) {
      return activity.getAssets();
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      return wallpaper.getBaseContext().getAssets();
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      return watchface.getBaseContext().getAssets();
    }
    return null;
  }


  @Override
  public void setSystemUiVisibility(int visibility) {
    int kind = component.getKind();
    if (kind == AppComponent.FRAGMENT || kind == AppComponent.WALLPAPER) {
      surfaceView.setSystemUiVisibility(visibility);
    }
  }


  @Override
  public void finish() {
    if (component == null) return;

    if (component.getKind() == AppComponent.FRAGMENT) {
      // This is the correct way to stop the sketch programmatically, according to the developer's docs:
      // https://developer.android.com/reference/android/app/Activity.html#onDestroy()
      // https://developer.android.com/reference/android/app/Activity.html#finish()
      // and online discussions:
      // http://stackoverflow.com/questions/2033914/quitting-an-application-is-that-frowned-upon/2034238
      // finish() it will trigger an onDestroy() event, which will translate down through the
      // activity hierarchy and eventually pausing and stopping Processing's animation thread, etc.
      activity.finish();
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      // stopSelf() stops a service from within:
      // https://developer.android.com/reference/android/app/Service.html#stopSelf()
      wallpaper.stopSelf();
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      watchface.stopSelf();
    }
  }

  ///////////////////////////////////////////////////////////

  // Thread handling


  public Thread createThread() {
    return new AnimationThread();
  }


  @Override
  public void startThread() {
    if (!surfaceReady) {
      requestedThreadStart = true;
      return;
    }

    if (thread == null) {
      thread = createThread();
      thread.start();
      requestedThreadStart = false;
    } else {
      throw new IllegalStateException("Thread already started in " +
                                      getClass().getSimpleName());
    }
  }


  @Override
  public void pauseThread() {
    if (!surfaceReady) return;

    paused = true;
  }


  @Override
  public void resumeThread() {
    if (!surfaceReady) return;

    if (thread == null) {
      thread = createThread();
      thread.start();
    }

    paused = false;
    synchronized (pauseObject) {
      pauseObject.notifyAll();  // wake up the animation thread
    }
  }


  @Override
  public boolean stopThread() {
    if (!surfaceReady) return true;

    if (thread == null) {
      return false;
    }

    thread.interrupt();
    thread = null;

    return true;
  }


  @Override
  public boolean isStopped() {
    return thread == null;
  }


  public void setFrameRate(float fps) {
    frameRateTarget = fps;
    frameRatePeriod = (long) (1000000000.0 / frameRateTarget);
  }


  protected void checkPause() throws InterruptedException {
    synchronized (pauseObject) {
      while (paused) {
        pauseObject.wait();
      }
    }
  }


  protected void callDraw() {
    component.requestDraw();
    if (component.canDraw() && sketch != null) {
      sketch.handleDraw();
    }
  }


  public class AnimationThread extends Thread {
    public AnimationThread() {
      super("Animation Thread");
    }

    /**
     * Main method for the primary animation thread.
     * <A HREF="http://java.sun.com/products/jfc/tsc/articles/painting/">Painting in AWT and Swing</A>
     */
    @Override
    public void run() {  // not good to make this synchronized, locks things up
      long beforeTime = System.nanoTime();
      long overSleepTime = 0L;

      int noDelays = 0;
      // Number of frames with a delay of 0 ms before the
      // animation thread yields to other running threads.
      final int NO_DELAYS_PER_YIELD = 15;

      if (sketch == null) return;

      // un-pause the sketch and get rolling
      sketch.start();

      while ((Thread.currentThread() == thread) &&
             (sketch != null && !sketch.finished)) {
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
        try {
          checkPause();
        } catch (InterruptedException e) {
          return;
        }

        callDraw();

        // wait for update & paint to happen before drawing next frame
        // this is necessary since the drawing is sometimes in a
        // separate thread, meaning that the next frame will start
        // before the update/paint is completed

        long afterTime = System.nanoTime();
        long timeDiff = afterTime - beforeTime;
        //System.out.println("time diff is " + timeDiff);
        long sleepTime = (frameRatePeriod - timeDiff) - overSleepTime;

        if (sleepTime > 0) {  // some time left in this cycle
          try {
            Thread.sleep(sleepTime / 1000000L, (int) (sleepTime % 1000000L));
            noDelays = 0;  // Got some sleep, not delaying anymore
          } catch (InterruptedException ex) { }

          overSleepTime = (System.nanoTime() - afterTime) - sleepTime;

        } else {    // sleepTime <= 0; the frame took longer than the period
          overSleepTime = 0L;
          noDelays++;

          if (noDelays > NO_DELAYS_PER_YIELD) {
            Thread.yield();   // give another thread a chance to run
            noDelays = 0;
          }
        }

        beforeTime = System.nanoTime();
      }
    }
  }


  public boolean hasPermission(String permission) {
    int res = ContextCompat.checkSelfPermission(getContext(), permission);
    return res == PackageManager.PERMISSION_GRANTED;
  }


  public void requestPermissions(String[] permissions) {
      if (component.isService()) {
      // https://developer.android.com/training/articles/wear-permissions.html
      // Inspired by PermissionHelper.java from Michael von Glasow:
      // https://github.com/mvglasow/satstat/blob/master/src/com/vonglasow/michael/satstat/utils/PermissionHelper.java
      // Example of use:
      // https://github.com/mvglasow/satstat/blob/master/src/com/vonglasow/michael/satstat/PasvLocListenerService.java
      final ServiceEngine eng = getEngine();
      if (eng != null) { // A valid service should have a non-null engine at this point, but just in case
        ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
          @Override
          protected void onReceiveResult (int resultCode, Bundle resultData) {
            String[] outPermissions = resultData.getStringArray(PermissionRequestor.KEY_PERMISSIONS);
            int[] grantResults = resultData.getIntArray(PermissionRequestor.KEY_GRANT_RESULTS);
            eng.onRequestPermissionsResult(resultCode, outPermissions, grantResults);
          }
        };
        final Intent permIntent = new Intent(getContext(), PermissionRequestor.class);
        permIntent.putExtra(PermissionRequestor.KEY_RESULT_RECEIVER, resultReceiver);
        permIntent.putExtra(PermissionRequestor.KEY_PERMISSIONS, permissions);
        permIntent.putExtra(PermissionRequestor.KEY_REQUEST_CODE, REQUEST_PERMISSIONS);
        // Show the dialog requesting the permissions
        permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(permIntent);
      }
    } else if (activity != null) {
      // Requesting permissions from user when the app resumes.
      // Nice example on how to handle user response
      // http://stackoverflow.com/a/35495855
      // More on permission in Android 23:
      // https://inthecheesefactory.com/blog/things-you-need-to-know-about-android-m-permission-developer-edition/en
      ActivityCompat.requestPermissions(activity, permissions, REQUEST_PERMISSIONS);
    }
  }
}
