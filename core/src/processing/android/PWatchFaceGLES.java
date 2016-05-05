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
import android.support.wearable.watchface.Gles2WatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import processing.event.MouseEvent;

import android.graphics.Rect;

public class PWatchFaceGLES extends Gles2WatchFaceService implements AppComponent {
  private DisplayMetrics metrics;
  private PApplet sketch;
  private GLEngine engine;

  public void initDimensions() {
    metrics = new DisplayMetrics();
    WindowManager man = (WindowManager) getSystemService(WINDOW_SERVICE);
    man.getDefaultDisplay().getMetrics(metrics);
  }

  public int getWidth() {
    return metrics.widthPixels;
  }

  public int getHeight() {
    return metrics.heightPixels;
  }

  public int getKind() {
    return WATCHFACE_GLES;
  }

  @Override
  public void startActivity(Intent intent) {
  }

  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
  }

  @Override
  public Engine onCreateEngine() {
    engine = new GLEngine();
    return engine;
  }

  public void requestDraw() {
    engine.invalidateIfNecessary();
  }

  public boolean canDraw() {
    return engine.isVisible() && !engine.isInAmbientMode();
  }

  private class GLEngine extends Gles2WatchFaceService.Engine {

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
      if (sketch != null) {
        sketch.initSurface(PWatchFaceGLES.this, null);
        sketch.start();
      }
    }


    private void invalidateIfNecessary() {
      if (isVisible() && !isInAmbientMode()) {
        invalidate();
      }
    }


    @Override
    public void onGlContextCreated() {
      super.onGlContextCreated();
    }


    @Override
    public void onGlSurfaceCreated(int width, int height) {
      super.onGlSurfaceCreated(width, height);
      sketch.displayWidth = width;
      sketch.displayHeight = height;
      sketch.g.setSize(sketch.sketchWidth(), sketch.sketchHeight());
      sketch.surfaceChanged();
    }


    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
      invalidateIfNecessary();
      // call new event handlers in sketch (?)
    }


    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);
      if (visible) {
        sketch.onResume();
      } else {
        sketch.onPause();
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
      sketch.handleDraw();
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
      PApplet.println("touch even:" + event.toString());
      sketch.surfaceTouchEvent(event);
      super.onTouchEvent(event);
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
      sketch.onDestroy();
    }
  }


  @Override
  public void onDestroy() {
    sketch.onDestroy();
    super.onDestroy();
  }
}
