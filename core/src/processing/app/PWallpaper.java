package processing.app;

import android.content.Context;
import android.opengl.GLSurfaceView.Renderer;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import android.hardware.display.DisplayManager;


public class PWallpaper extends WallpaperService implements PContainer {

  private DisplayMetrics metrics;
  private PApplet sketch;

  public PWallpaper() {

  }

  public PWallpaper(PApplet sketch) {
    System.err.println("-----> PWallpaper CONSTRUCTOR: " + sketch);
    this.sketch = sketch;
  }

  @Override
  public Engine onCreateEngine() {
    return new PEngine();
  }

  public void initDimensions() {
    metrics = new DisplayMetrics();
//    getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
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
    return WALLPAPER;
  }

  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
  }

  public class PEngine extends Engine {
    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      if (sketch != null) {
        sketch.initSurface(PWallpaper.this, getSurfaceHolder());
      }
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
//      if (LoggerConfig.ON) {
//        Log.d(TAG, "onVisibilityChanged(" + visible + ")");
//      }
//
      super.onVisibilityChanged(visible);
//
      if (visible) {
        sketch.onResume();
      } else {
        sketch.onPause();
      }
    }

    @Override
    public void onDestroy() {
//      if (LoggerConfig.ON) {
//        Log.d(TAG, "onDestroy()");
//      }
//
      super.onDestroy();
      sketch.onDestroy();
    }
  }
}
