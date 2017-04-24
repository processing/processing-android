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

import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowInsets;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.watchface.Gles2WatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import processing.event.MouseEvent;
import java.lang.reflect.Method;

import android.graphics.Rect;

public class PWatchFaceGLES extends Gles2WatchFaceService implements AppComponent {
  private Point size;
  private DisplayMetrics metrics;
  private GLES2Engine engine;


  public void initDimensions() {
    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    metrics = new DisplayMetrics();
    display.getMetrics(metrics);

//  display.getRealMetrics(metrics); // API 17 or higher
//  display.getRealSize(size);

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
    // The rendering loop should never call handleDraw() directly, it only needs to invalidate the
    // screen
    return false;
  }


  public void dispose() {
  }


  public void requestPermissions() {
  }


  @Override
  public Engine onCreateEngine() {
    engine = new GLES2Engine();
    return engine;
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    if (engine != null) engine.onDestroy();
  }


  private class GLES2Engine extends Gles2WatchFaceService.Engine implements
  ServiceEngine {
    private PApplet sketch;
    private Method compUpdatedMethod;
    private boolean isRound = false;
    private Rect insets = new Rect();
    private boolean lowBitAmbient = false;
    private boolean burnInProtection = false;


    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      setWatchFaceStyle(new WatchFaceStyle.Builder(PWatchFaceGLES.this)
              .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
              .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
              .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
              .setShowSystemUiTime(false)
              .setAcceptsTapEvents(true)
              .build());
      sketch = createSketch();
      sketch.initSurface(PWatchFaceGLES.this, null);
      initComplications();
      requestPermissions();
    }


    @Override
    public void onGlContextCreated() {
      super.onGlContextCreated();
    }


    @Override
    public void onGlSurfaceCreated(int width, int height) {
      super.onGlSurfaceCreated(width, height);
      if (sketch != null) {
        sketch.displayWidth = width;
        sketch.displayHeight = height;
        sketch.g.setSize(sketch.sketchWidth(), sketch.sketchHeight());
        sketch.surfaceChanged();
      }
    }


    private void initComplications() {
      try {
        compUpdatedMethod = sketch.getClass().getMethod("complicationsUpdated",
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
    public void onPeekCardPositionUpdate(Rect rect) {

    }


    @Override
    public void onTimeTick() {
      invalidate();
    }


    @Override
    public void onDraw() {
      super.onDraw();
//      PApplet.println("Calling handleDraw: " + sketch.width + " " + sketch.height);
      if (sketch != null) sketch.handleDraw();
    }


    @Override
    public void onTapCommand(
            @TapType int tapType, int x, int y, long eventTime) {
      switch (tapType) {
        case WatchFaceService.TAP_TYPE_TOUCH:
          // The system sends the first command, TAP_TYPE_TOUCH, when the user initially touches the screen
//          if (withinTapRegion(x, y)) {
//            // Provide visual feedback of touch event
//            startTapHighlight(x, y, eventTime);
//          }
          sketch.postEvent(new MouseEvent(null, eventTime,
                  MouseEvent.PRESS, 0,
                  x, y, LEFT, 1));
          invalidate();
          break;


        case WatchFaceService.TAP_TYPE_TAP:
          // Before sending the next command, the system judges whether the contact is a single tap,
          // which is the only gesture allowed. If the user immediately lifts their finger,
          // the system determines that a single tap took place, and forwards a TAP_TYPE_TAP event
          sketch.postEvent(new MouseEvent(null, eventTime,
                  MouseEvent.RELEASE, 0,
                  x, y, LEFT, 1));

//          hideTapHighlight();
//          if (withinTapRegion(x, y)) {
//            // Implement the tap action
//            // (e.g. show detailed step count)
//            onWatchFaceTap();
//          }


          invalidate();
          break;

        case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
          // If the user does not immediately lift their finger, the system forwards a
          // TAP_TYPE_TOUCH_CANCEL event. Once the user has triggered a TAP_TYPE_TOUCH_CANCEL event,
          // they cannot trigger a TAP_TYPE_TAP event until they make a new contact with the screen.
          //hideTapHighlight();

          // New type of event...
          sketch.postEvent(new MouseEvent(null, eventTime,
                  MouseEvent.RELEASE, 0,
                  x, y, LEFT, 1));
          invalidate();
          break;

        default:
          super.onTapCommand(tapType, x, y, eventTime);
          break;
      }
    }


    @Override
    public void onTouchEvent(MotionEvent event) {
      super.onTouchEvent(event);
      if (sketch != null) sketch.surfaceTouchEvent(event);
    }


    @Override
    public void onComplicationDataUpdate(
            int complicationId, ComplicationData complicationData) {
      if (compUpdatedMethod != null) {
        try {
          compUpdatedMethod.invoke(complicationId, complicationData);
        } catch (Exception e) {
        }
        invalidate();
      }
    }


    @Override
    public void onDestroy() {
      super.onDestroy();
      if (sketch != null) sketch.onDestroy();
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
