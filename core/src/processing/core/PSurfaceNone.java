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
import android.content.res.AssetManager;
import android.service.wallpaper.WallpaperService;
import android.support.wearable.watchface.WatchFaceService;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import processing.android.AppComponent;

/**
 * Base surface for Android2D and OpenGL renderers. It includes the standard rendering loop.
 */
public class PSurfaceNone implements PSurface, PConstants {
  protected PApplet sketch;
  protected PGraphics graphics;
  protected AppComponent component;
  protected Activity activity;
  protected View view;

  protected WallpaperService wallpaper;
  protected WatchFaceService watchface;
  protected SurfaceView surface;

  protected Thread thread;
  protected boolean paused;
  protected Object pauseObject = new Object();

  protected float frameRateTarget = 60;
  protected long frameRatePeriod = 1000000000L / 60L;

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
  public AppComponent getComponent() {
    return component;
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

      activity = null;
    }

    if (view != null) {
      view.destroyDrawingCache();
      view = null;
    }

    if (component != null) {
      component.dispose();
      component = null;
    }

    if (surface != null) {
      surface.getHolder().getSurface().release();
      surface = null;
    }
  }

  @Override
  public void setRootView(View view) {
    this.view = view;
  }

  @Override
  public SurfaceView getSurfaceView() {
    return surface;
  }

  @Override
  public void initView(int sketchWidth, int sketchHeight) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      int displayWidth = component.getWidth();
      int displayHeight = component.getHeight();
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
  public void startActivity(Intent intent) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      component.startActivity(intent);
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
      surface.setSystemUiVisibility(visibility);
    }
  }

  @Override
  public void finish() {
    if (component == null) return;

    if (component.getKind() == AppComponent.FRAGMENT) {
      activity.finish();
    } else if (component.getKind() == AppComponent.WALLPAPER) {
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
    if (thread == null) {
      thread = createThread();
      thread.start();
    } else {
      throw new IllegalStateException("Thread already started in " +
                                      getClass().getSimpleName());
    }
  }

  @Override
  public void pauseThread() {
    paused = true;
  }

  @Override
  public void resumeThread() {
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
    if (thread == null) {
      return false;
    }
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

  protected void checkPause() {
    if (paused) {
      synchronized (pauseObject) {
        try {
          pauseObject.wait();
        } catch (InterruptedException e) {
          // waiting for this interrupt on a start() (resume) call
        }
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

      while ((Thread.currentThread() == thread) && !sketch.finished) {
        checkPause();
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

      finish();
    }
  }
}
