/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-17 The Processing Foundation

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

import java.lang.reflect.Method;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.graphics.Rect;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.WindowManager;
import processing.a2d.PGraphicsAndroid2D;
import processing.core.PApplet;

@TargetApi(21)
public class PWatchFaceCanvas extends CanvasWatchFaceService implements AppComponent {
  private Point size;
  private DisplayMetrics metrics;
  private CanvasEngine engine;

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
    return WATCHFACE;
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
    if (engine != null) engine.invalidateIfNecessary();
  }


  public boolean canDraw() {
    // The rendering loop should never call handleDraw() directly,
    // it only needs to invalidate the screen
    return false;
  }


  public void dispose() {
  }


  public void requestPermissions() {
  }


  @Override
  public Engine onCreateEngine() {
    engine = new CanvasEngine();
    return engine;
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    if (engine != null) engine.onDestroy();
  }


  private class CanvasEngine extends CanvasWatchFaceService.Engine implements ServiceEngine {
    private PApplet sketch;
    private Method compUpdatedMethod;
    private Method tapCommandMethod;
    private boolean isRound = false;
    private Rect insets = new Rect();
    private boolean lowBitAmbient = false;
    private boolean burnInProtection = false;

    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      setWatchFaceStyle(new WatchFaceStyle.Builder(PWatchFaceCanvas.this)
              .setAcceptsTapEvents(true)
              .build());
      sketch = createSketch();
      PGraphicsAndroid2D.useBitmap = false;
      sketch.initSurface(PWatchFaceCanvas.this, null);
      initTapEvents();
      initComplications();
      requestPermissions();
    }


    private void initTapEvents() {
      try {
        tapCommandMethod = sketch.getClass().getMethod("onTapCommand",
          new Class[] {int.class, int.class, int.class, long.class});
      } catch (Exception e) {
        tapCommandMethod = null;
      }
    }


    private void initComplications() {
      try {
        compUpdatedMethod = sketch.getClass().getMethod("onComplicationDataUpdate",
          new Class[] {int.class, ComplicationData.class});
      } catch (Exception e) {
        compUpdatedMethod = null;
      }
    }


    private void invalidateIfNecessary() {
      if (isVisible() && !isInAmbientMode()) {
        invalidate();
      }
    }


    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      invalidateIfNecessary();
      // call new event handlers in sketch (?)
    }


    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
      burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
    }


    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);
      isRound = insets.isRound();
      this.insets.set(insets.getSystemWindowInsetLeft(),
                      insets.getSystemWindowInsetTop(),
                      insets.getSystemWindowInsetRight(),
                      insets.getSystemWindowInsetBottom());
    }


    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);
      if (sketch != null) {
        if (visible) {
          sketch.onResume();
        } else {
          sketch.onPause();
        }
      }
    }


    @Override
    public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      super.onSurfaceChanged(holder, format, width, height);
      if (sketch != null) {
        sketch.surfaceChanged();
        sketch.setSize(width, height);
      }
    }


    @Override
    public void onPeekCardPositionUpdate(Rect rect) { }


    @Override
    public void onTimeTick() {
      invalidate();
    }


    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      super.onDraw(canvas, bounds);
      if (sketch != null) {
        PGraphicsAndroid2D g2 = (PGraphicsAndroid2D)sketch.graphics;
        g2.canvas = canvas;
        sketch.handleDraw();
      }
    }


    @Override
    public void onTouchEvent(MotionEvent event) {
      super.onTouchEvent(event);
      if (sketch != null) sketch.surfaceTouchEvent(event);
    }


    @Override
    public void onTapCommand(@TapType int tapType, int x, int y, long eventTime) {
      if (tapCommandMethod != null) {
        try {
          tapCommandMethod.invoke(tapType, x, y, eventTime);
        } catch (Exception e) { }
        invalidate();
      }
    }


    @Override
    public void onComplicationDataUpdate(int complicationId,
                                         ComplicationData complicationData) {
      if (compUpdatedMethod != null) {
        try {
          compUpdatedMethod.invoke(complicationId, complicationData);
        } catch (Exception e) { }
        invalidate();
      }
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
      return 0;
    }


    @Override
    public float getYOffset() {
      return 0;
    }


    @Override
    public float getXOffsetStep() {
      return 0;
    }


    @Override
    public float getYOffsetStep() {
      return 0;
    }


    @Override
    public int getXPixelOffset() {
      return 0;
    }


    @Override
    public int getYPixelOffset() {
      return 0;
    }


    @Override
    public boolean isRound() {
      return isRound;
    }


    @Override
    public Rect getInsets() {
      return insets;
    }


    @Override
    public boolean useLowBitAmbient() {
      return lowBitAmbient;
    }


    @Override
    public boolean requireBurnInProtection() {
      return burnInProtection;
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
