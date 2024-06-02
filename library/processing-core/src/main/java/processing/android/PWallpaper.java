/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-21 The Processing Foundation

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
import android.util.DisplayMetrics;
import android.view.Display;
import android.graphics.Point;
import android.graphics.Rect;

import processing.core.PApplet;

public class PWallpaper extends WallpaperService implements AppComponent {
  private Point size;
  private DisplayMetrics metrics;
  private WallpaperEngine engine;


  public void initDimensions() {
    metrics = new DisplayMetrics();
    size = new Point();
    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    CompatUtils.getDisplayParams(display, metrics, size);
  }


  public int getDisplayWidth() {
    return size.x;
  }


  public int getDisplayHeight() {
    return size.y;
  }


  public float getDisplayDensity() {
    return metrics.density;
  }


  public int getKind() {
    return WALLPAPER;
  }


  public PApplet createSketch() {
    return new PApplet();
  }


  public void setSketch(PApplet sketch) {
    engine.sketch = sketch;
  }


  public PApplet getSketch() {
    return engine.sketch;
  }


  public boolean isService() {
    return true;
  }


  public ServiceEngine getEngine() {
    return engine;
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


  @Override
  public Engine onCreateEngine() {
    engine = new WallpaperEngine();
    return engine;
  }


  @Override
  public void onDestroy() {
    super.onDestroy();

    if (engine != null){
      //engine.sketch = null;
      engine.onDestroy();
    }
  }


  public class WallpaperEngine extends Engine implements ServiceEngine {
    PApplet sketch;
    private float xOffset, xOffsetStep;
    private float yOffset, yOffsetStep;
    private int xPixelOffset, yPixelOffset;


    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      sketch = createSketch();
      sketch.initSurface(PWallpaper.this, getSurfaceHolder());
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
      // When the surface of a live wallpaper changes (eg: after a screen rotation) the same sketch
      // continues to run (unlike the case of regular apps, where its re-created) so we need to
      // force a reset of the renderer so the backing FBOs (in the case of the OpenGL renderers)
      // get reinitalized with the correct size.
      sketch.g.reset();
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
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.xOffsetStep = xOffsetStep;
        this.yOffsetStep = yOffsetStep;
        this.xPixelOffset = xPixelOffset;
        this.yPixelOffset = yPixelOffset;
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


    @Override
    public float getXOffset() {
      return xOffset;
    }


    @Override
    public float getYOffset() {
      return yOffset;
    }


    @Override
    public float getXOffsetStep() {
      return xOffsetStep;
    }


    @Override
    public float getYOffsetStep() {
      return yOffsetStep;
    }


    @Override
    public int getXPixelOffset() {
      return xPixelOffset;
    }


    @Override
    public int getYPixelOffset() {
      return yPixelOffset;
    }


    @Override
    public boolean isInAmbientMode() {
      return false;
    }


    @Override
    public boolean isRound() {
      return false;
    }


    @Override
    public Rect getInsets() {
      return null;
    }


    @Override
    public boolean useLowBitAmbient() {
      return false;
    }


    @Override
    public boolean requireBurnInProtection() {
      return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults) {
      if (sketch != null) {
        sketch.onRequestPermissionsResult(requestCode, permissions, grantResults);
      }
    }
  }
}
