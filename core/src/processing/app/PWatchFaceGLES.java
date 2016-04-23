package processing.app;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.wearable.watchface.Gles2WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.event.MouseEvent;

import android.graphics.Rect;

public class PWatchFaceGLES extends Gles2WatchFaceService implements PContainer {
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
//    private static final long TICK_PERIOD_MILLIS = 100;
//    private Handler timeTick;

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
//        graphics = sketch.g;
        sketch.start();
      }

//      timeTick = new Handler(Looper.myLooper());
//      startTimerIfNecessary();

    }

    /*
    private void startTimerIfNecessary() {
      timeTick.removeCallbacks(timeRunnable);
      if (isVisible() && !isInAmbientMode()) {
        timeTick.post(timeRunnable);
      }
    }


    private final Runnable timeRunnable = new Runnable() {
      @Override
      public void run() {
        onSecondTick();
        if (isVisible() && !isInAmbientMode()) {
          timeTick.postDelayed(this, TICK_PERIOD_MILLIS);
        }
      }
    };


    private void onSecondTick() {
      invalidateIfNecessary();
    }
*/

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
//      graphics.setSize(width, height);
      sketch.g.setSize(width, height);
      sketch.surfaceChanged();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);
//      invalidate();
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
//      startTimerIfNecessary();
    }

    @Override
    public void onPeekCardPositionUpdate(Rect rect) {

    }

    @Override
    public void onTimeTick() {
      invalidate();
    }

    int frame = 0;
    @Override
    public void onDraw() {
      super.onDraw();

//      GLES20.glClearColor(sketch.random(1), sketch.random(1), sketch.random(1), 1);
//      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
//      PApplet.println("FRAME: " + frame);
//      frame++;
      PApplet.println("Calling handleDraw: " + sketch.width + " " + sketch.height);
      sketch.handleDraw();

      // Draw every frame as long as we're visible and in interactive mode.
//      if (isVisible() && !isInAmbientMode()) {
//          invalidate();
//      }
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
      PApplet.println("touch even:" + event.toString());
      sketch.surfaceTouchEvent(event);
      super.onTouchEvent(event);
    }

    @Override
    public void onTapCommand(
            @TapType int tapType, int x, int y, long eventTime) {
      switch (tapType) {
        case Gles2WatchFaceService.TAP_TYPE_TOUCH:
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


        case Gles2WatchFaceService.TAP_TYPE_TAP:
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

        case Gles2WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
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
