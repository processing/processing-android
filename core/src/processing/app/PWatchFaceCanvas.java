package processing.app;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import processing.core.PGraphicsAndroid2D;

public class PWatchFaceCanvas extends CanvasWatchFaceService implements AppComponent {
  private DisplayMetrics metrics;
  private PApplet sketch;
  private CEngine engine;

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
    return WATCHFACE_CANVAS;
  }

  @Override
  public void startActivity(Intent intent) {
  }

  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
  }

  @Override
  public Engine onCreateEngine() {
    engine = new CEngine();
    return engine;
  }

  public void requestDraw() {
    engine.invalidateIfNecessary();
  }

  public boolean canDraw() {
    return engine.isVisible() && !engine.isInAmbientMode();
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
        sketch.initSurface(PWatchFaceCanvas.this, null);
        sketch.start();
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


    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
        super.onDraw(canvas, bounds);
        PGraphicsAndroid2D g2 = (PGraphicsAndroid2D)sketch.g;
        g2.canvas = canvas;
        sketch.handleDraw();

//        watchFace.draw(canvas, bounds);
    }
  }
}
