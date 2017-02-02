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
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import android.util.DisplayMetrics;
import android.os.Build;
import android.view.Display;
import android.graphics.Point;

public class PWallpaper extends WallpaperService implements AppComponent {
  String TAG = "PWallpaper";

  protected Point size;
  private DisplayMetrics metrics;
  protected PEngine engine;

  public PWallpaper() {
  }

  public PWallpaper(PApplet sketch) {
  }

  public void initDimensions() {
    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    metrics = new DisplayMetrics();
    display.getMetrics(metrics);

//    display.getRealMetrics(metrics); // API 17 or higher
//    display.getRealSize(size);

    size = new Point();
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

  public float getDisplayDensity() {
    return metrics.density;
  }

  public void setSketch(PApplet sketch) {
    engine.sketch = sketch;
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
    protected PApplet sketch;

    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      sketch = createSketch();
      sketch.initSurface(PWallpaper.this, getSurfaceHolder());
      sketch.startSurface();
      sketch.preview = isPreview();
      if (isPreview()) requestPermissions();
      setTouchEventsEnabled(true);
    }

    @Override
    public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
      super.onSurfaceCreated(surfaceHolder);
    }

    @Override
    public void onSurfaceChanged(final SurfaceHolder holder, final int format,
                                 final int width, final int height) {
      if (sketch != null) {
        sketch.g.setSize(width, height);
      }
      super.onSurfaceChanged(holder, format, width, height);
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      if (sketch != null) {
        if (visible) {
          sketch.onResume();
        } else {
          sketch.onPause();
        }
      }
      super.onVisibilityChanged(visible);
    }

    /*
     * Store the position of the touch event so we can use it for drawing
     * later
     */
    @Override
    public void onTouchEvent(MotionEvent event) {
      super.onTouchEvent(event);
      if (sketch != null) {
        sketch.surfaceTouchEvent(event);
      }
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {

      if (sketch != null) {
        sketch.homeScreenOffset = xOffset;
        if (0 < xOffsetStep) {
          sketch.homeScreenCount = (int)(1 + 1 / xOffsetStep);
        } else {
          sketch.homeScreenCount = 1;
        }
      }
    }

    @Override
    public void onSurfaceDestroyed(SurfaceHolder holder) {
      // This is called immediately before a surface is being destroyed.
      // After returning from this call, you should no longer try to access this
      // surface. If you have a rendering thread that directly accesses the
      // surface, you must ensure that thread is no longer touching the Surface
      // before returning from this function.
      super.onSurfaceDestroyed(holder);
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      if (sketch != null) {
        sketch.onDestroy();
      }
    }

    public void onPermissionsGranted() {
      if (sketch != null) {
        sketch.onPermissionsGranted();
      }
    }
  }
}
