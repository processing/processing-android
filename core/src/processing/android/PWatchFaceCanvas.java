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

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Rect;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
//import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.view.WindowManager;
import processing.core.PApplet;
import processing.core.PGraphicsAndroid2D;
import processing.event.MouseEvent;


public class PWatchFaceCanvas extends CanvasWatchFaceService implements AppComponent {
  protected PApplet sketch;
  protected Point size;
//  private DisplayMetrics metrics;
  protected CEngine engine;

  public void initDimensions() {
//    metrics = new DisplayMetrics();
//    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
//    Display display = wm.getDefaultDisplay();
//    display.getRealMetrics(metrics); // only API level 17 o higher
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

  public int getWidth() {
    return size.x;
//    return metrics.widthPixels;
  }

  public int getHeight() {
    return size.y;
//    return metrics.heightPixels;
  }

  public int getKind() {
    return WATCHFACE;
  }

  @Override
  public void startActivity(Intent intent) {
  }

  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
  }

  public void dispose() {
  }

  public void requestPermissions() {

  }

  public void onPermissionsGranted() {
    if (sketch != null) sketch.onPermissionsGranted();
  }

  @Override
  public Engine onCreateEngine() {
    engine = new CEngine();
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

  private class CEngine extends CanvasWatchFaceService.Engine {
    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      setWatchFaceStyle(new WatchFaceStyle.Builder(PWatchFaceCanvas.this)
              .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
              .setAmbientPeekMode(WatchFaceStyle.AMBIENT_PEEK_MODE_HIDDEN)
              .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
              .setShowSystemUiTime(false)
              .setAcceptsTapEvents(true)
              .build());
      if (sketch != null) {
        PGraphicsAndroid2D.useBitmap = false;
        sketch.initSurface(PWatchFaceCanvas.this, null);
        sketch.startSurface();
        requestPermissions();
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
      if (sketch != null) sketch.ambientMode = inAmbientMode;
      // call new event handlers in sketch (?)
    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      if (sketch != null) {
        sketch.lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        sketch.burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
      }
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);
      if (sketch != null) {
        sketch.isRound = insets.isRound();
        sketch.insetLeft = insets.getSystemWindowInsetLeft();
        sketch.insetRight = insets.getSystemWindowInsetRight();
        sketch.insetTop = insets.getSystemWindowInsetTop();
        sketch.insetBottom = insets.getSystemWindowInsetBottom();
      }
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
    public void onDraw(Canvas canvas, Rect bounds) {
      super.onDraw(canvas, bounds);
      if (sketch != null) {
        PGraphicsAndroid2D g2 = (PGraphicsAndroid2D)sketch.g;
        g2.canvas = canvas;
        sketch.handleDraw();
      }
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
      PApplet.println("touch even:" + event.toString());
      if (sketch != null) sketch.surfaceTouchEvent(event);
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      if (sketch != null) sketch.onDestroy();
    }
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    if (engine != null) engine.onDestroy();
  }
}
