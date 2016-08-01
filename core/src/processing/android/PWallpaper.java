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

package processing.android;

import android.service.wallpaper.WallpaperService;
//import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import android.util.Log;
import android.os.Build;
//import android.view.WindowManager;
import android.view.Display;
import android.graphics.Point;

public class PWallpaper extends WallpaperService implements AppComponent {
  String TAG = "PWallpaper";

  protected Point size;
//  private DisplayMetrics metrics;
  protected PEngine engine;

  public PWallpaper() {
  }

  public PWallpaper(PApplet sketch) {
  }

  public void initDimensions() {
//    metrics = new DisplayMetrics();
//    getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
//    display.getRealMetrics(metrics); // API 17 or higher
//    display.getRealSize(size);
//    display.getMetrics(metrics);

    size = new Point();
    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    if (Build.VERSION.SDK_INT >= 17) {
      display.getRealSize(size);
    } else if (Build.VERSION.SDK_INT >= 14) {
      // Use undocumented methods getRawWidth, getRawHeight
      try {
        size.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
        size.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
      } catch (Exception e) {
        display.getSize(size);
      }
    }
  }

  public int getKind() {
    return WALLPAPER;
  }

  public int getDisplayWidth() {
    return size.x;
//    return metrics.widthPixels;
  }

  public int getDisplayHeight() {
    return size.y;
//    return metrics.heightPixels;
  }

  public void setSketch(PApplet sketch) {
//    engine.sketch = sketch;
  }

  public PApplet getSketch() {
    return engine.sketch;
  }

  public PApplet createSketch() {
    return new PApplet();
  }

  public void requestDraw() {

  }

  public boolean canDraw() {
    return true;
  }

  public void dispose() {
  }

  public void requestPermissions() {

  }

  public void onPermissionsGranted() {
    if (engine != null) engine.onPermissionsGranted();
  }

  @Override
  public Engine onCreateEngine() {
    engine = new PEngine();
    return engine;
  }

  public class PEngine extends Engine {
    private PApplet sketch;
    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      sketch = createSketch();
      if (sketch != null) {
        sketch.initSurface(PWallpaper.this, getSurfaceHolder());
        sketch.startSurface();
        sketch.preview = isPreview();
        // By default we don't get touch events, so enable them.
        setTouchEventsEnabled(true);
        if (!sketch.preview) requestPermissions();
      }
    }

    @Override
    public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
      super.onSurfaceCreated(surfaceHolder);
      Log.d(TAG, "onSurfaceCreated()");
    }

    @Override
    public void onSurfaceChanged(final SurfaceHolder holder, final int format,
                                 final int width, final int height) {
      super.onSurfaceChanged(holder, format, width, height);
      Log.d(TAG, "onSurfaceChanged()");
      if (sketch != null) {
        sketch.g.setSize(width, height);
      }
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
//      if (LoggerConfig.ON) {
//        Log.d(TAG, "onVisibilityChanged(" + visible + ")");
//      }
//
      super.onVisibilityChanged(visible);
      if (sketch != null) {
        if (visible) {
          sketch.onResume();
        } else {
          sketch.onPause();
        }
      }
    }

    /*
     * Store the position of the touch event so we can use it for drawing
     * later
     */
    @Override
    public void onTouchEvent(MotionEvent event) {
      super.onTouchEvent(event);
      if (sketch != null) sketch.surfaceTouchEvent(event);
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xStep, float yStep, int xPixels, int yPixels) {
      if (sketch != null) {
        sketch.offsetX = xOffset;
        sketch.offsetY = yOffset;
      }
    }

    @Override
    public void onSurfaceDestroyed(SurfaceHolder holder) {
      // This is called immediately before a surface is being destroyed. After returning from this
      // call, you should no longer try to access this surface. If you have a rendering thread that
      // directly accesses the surface, you must ensure that thread is no longer touching the
      // Surface before returning from this function.
      /*
      PApplet sketchToDestroy = null;
      if (deadSketch != null) {
        sketchToDestroy = deadSketch;
      } else {
        handler.removeCallbacks(drawRunnable);
        System.out.println("Removed handler draw callback!!!!!!!!!!!!!!!!");
        sketchToDestroy = sketch;
      }

      if (sketchToDestroy != null) {
        System.out.println("Pausing sketch on surface destroy " + sketchToDestroy);
        sketchToDestroy.onPause();
      }
      */
    }

    @Override
    public void onDestroy() {
      // Called right before the engine is going away.
//      if (LoggerConfig.ON) {
//        Log.d(TAG, "onDestroy()");
//      }
//
      super.onDestroy();
      if (sketch != null) sketch.onDestroy();
    }

    public void onPermissionsGranted() {
      if (sketch != null) sketch.onPermissionsGranted();
    }
  }
}
