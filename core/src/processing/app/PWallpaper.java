package processing.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
//import android.opengl.GLSurfaceView.Renderer;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
//import processing.opengl.PGLES;
import processing.opengl.PGraphicsOpenGL;
import processing.opengl.PSurfaceGLES;
import android.util.Log;
//import android.hardware.display.DisplayManager;


public class PWallpaper extends WallpaperService implements PContainer {
  String TAG = "PWallpaper";

  private DisplayMetrics metrics;
//  private PApplet deadSketch = null;
//  private PApplet sketch = null;
  private PEngine engine;

  private final Handler handler = new Handler();

  public PWallpaper() {
  }

  public PWallpaper(PApplet sketch) {
  }

  public void initDimensions() {
    metrics = new DisplayMetrics();
//    getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
    WindowManager man = (WindowManager) getSystemService(WINDOW_SERVICE);
    man.getDefaultDisplay().getMetrics(metrics);
  }

  public int getKind() {
    return WALLPAPER;
  }

  public int getWidth() {
    return metrics.widthPixels;
  }

  public int getHeight() {
    return metrics.heightPixels;
  }

  public void setSketch(PApplet sketch) {
     engine.sketch = sketch;
  }

  public PApplet createSketch() {
    return new PApplet();
  }

  @Override
  public Engine onCreateEngine() {
    engine = new PEngine();
    return engine;
  }

  public class PEngine extends Engine {
    private PApplet sketch = null;
    private GLWallpaperSurfaceView view;

    // Some notes on how to control FPS:
    // http://stackoverflow.com/questions/4772693/how-to-limit-framerate-when-using-androids-glsurfaceview-rendermode-continuousl
    // https://github.com/LWJGL/lwjgl/blob/master/src/java/org/lwjgl/opengl/Sync.java
    private final Runnable drawRunnable = new Runnable() {
	  public void run() {
//        if (sketch.frameCount % 15 == 0) {
//          System.out.println("requesting draw for " + sketch);
//        }
        if (sketch != null) {
          sketch.g.requestDraw();
        }
        scheduleNextDraw();
      }
	};

    private void scheduleNextDraw() {
      handler.removeCallbacks(drawRunnable);

      int waitMillis = 1000 / 15;
      if (sketch != null) {
        final PSurfaceGLES glsurf = (PSurfaceGLES) sketch.surface;

        float targetfps = glsurf.pgl.getFrameRate();
        float targetMillisPerFrame = 1000 / targetfps;

//            float actualFps = sketch.frameRate;
//            float actualMillisPerFrame = 1000 / actualFps;
//            int waitMillis = (int)PApplet.max(0, targetMillisPerFrame - actualMillisPerFrame);
        waitMillis = (int) targetMillisPerFrame;
      }

//      if (sketch.frameCount % 15 == 0) {
//        System.out.println("scheduling next draw for " + sketch);
//      }
      handler.postDelayed(drawRunnable, waitMillis);
    }

    private void pauseNextDraw() {
      handler.removeCallbacks(drawRunnable);
    }

    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      Log.d(TAG, "onCreate(SurfaceHolder)");


      sketch = createSketch();
      System.out.println("initializing sketch " + sketch);
      view = new GLWallpaperSurfaceView(PWallpaper.this);
      sketch.initSurface(PWallpaper.this, view);
      view.initRenderer();

      // By default we don't get touch events, so enable them.
      setTouchEventsEnabled(true);

      scheduleNextDraw();

//        sketch.start();
    }

    @Override
    public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
      super.onSurfaceCreated(surfaceHolder);
      Log.d(TAG, "onSurfaceCreated()");
    }

    @Override
    public void onSurfaceChanged (final SurfaceHolder holder, final int format,
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
          scheduleNextDraw();
        } else {
          sketch.onPause();
          pauseNextDraw();
        }
      }
/*
      if (visible) {
        if (sketch != null) {
          System.out.println("Resume sketch on visibility change " + sketch);
          sketch.onResume();
//          ((GLWallpaperSurfaceView)sketch.surface.getSurfaceView()).onResume();
          scheduleNextDraw();
//          handler.postDelayed(drawRunnable, 40);
        }
      } else{
        PApplet sketchToPause = null;
        if (deadSketch != null) {
          sketchToPause = deadSketch;
        } else {
          handler.removeCallbacks(drawRunnable);
          System.out.println("Removed handler draw callback!!!!!!!!!!!!!!!!");
          sketchToPause = sketch;
        }

        if (sketchToPause != null) {
          System.out.println("Pausing sketch in visibility change " + sketchToPause);
          sketchToPause.onPause();
//          ((GLWallpaperSurfaceView)sketchToPause.surface.getSurfaceView()).onResume();
        }
      }
      */
    }

    /*
     * Store the position of the touch event so we can use it for drawing
     * later
     */
    @Override
    public void onTouchEvent(MotionEvent event) {
//      if (event.getAction() == MotionEvent.ACTION_MOVE) {
//        mTouchX = event.getX();
//        mTouchY = event.getY();
//      } else {
//        mTouchX = -1;
//        mTouchY = -1;
//      }
      if (sketch != null) {
        sketch.surfaceTouchEvent(event);
      }
      super.onTouchEvent(event);
    }

	@Override
	public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xStep, float yStep, int xPixels, int yPixels) {
//	  mOffset = xOffset;
//	  drawFrame();
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
//      Log.d(TAG, "onDestroy()");
      super.onDestroy();
      pauseNextDraw();
      view.onDestroy();
      sketch.onDestroy();


      /*
      if (deadSketch != null) {
//        System.out.println("will destroy dead sketch " + deadSketch);

        System.out.println("will destroy dead sketch " + deadSketch);
        deadSketch.onDestroy();
        deadSketch = null;
      } else {
        System.out.println("will destroy sketch " + sketch);
        handler.removeCallbacks(drawRunnable);
        System.out.println("Removed handler draw callback!!!!!!!!!!!!!!!!");

        sketch.onDestroy();
        sketch = null;
      }
      */
    }




    public class GLWallpaperSurfaceView extends GLSurfaceView {
      //      PGraphicsOpenGL g3;
      SurfaceHolder surfaceHolder;

      @SuppressWarnings("deprecation")
      public GLWallpaperSurfaceView(Context context) {
        super(context);

        // Check if the system supports OpenGL ES 2.0.
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        final boolean supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000;

        if (!supportsGLES2) {
          throw new RuntimeException("OpenGL ES 2.0 is not supported by this device.");
        }

        surfaceHolder = getHolder();
        // are these two needed?
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU);

        // Tells the default EGLContextFactory and EGLConfigChooser to create an GLES2 context.
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
      }


      public void initRenderer() {
        PSurfaceGLES surf = (PSurfaceGLES)(sketch.surface);

        int quality = sketch.sketchQuality();
        if (1 < quality) {
          setEGLConfigChooser(surf.getConfigChooser(quality));
        }
        // The renderer can be set only once.
        setRenderer(surf.getRenderer());
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
      }

      @Override
      public SurfaceHolder getHolder() {
        return getSurfaceHolder();
     }


      public void onDestroy() {
        super.onDetachedFromWindow();
      }


      @Override
      public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        super.surfaceChanged(holder, format, w, h);

//        if (PApplet.DEBUG) {
//          System.out.println("SketchSurfaceView3D.surfaceChanged() " + w + " " + h);
//        }
        System.out.println("SketchSurfaceView3D.surfaceChanged() " + w + " " + h + " " + sketch);
        sketch.surfaceChanged();
      }

      /*
      @Override
      public boolean onTouchEvent(MotionEvent event) {
        return sketch.surfaceTouchEvent(event);
      }

      @Override
      public boolean onKeyDown(int code, android.view.KeyEvent event) {
        sketch.surfaceKeyDown(code, event);
        return super.onKeyDown(code, event);
      }

      @Override
      public boolean onKeyUp(int code, android.view.KeyEvent event) {
        sketch.surfaceKeyUp(code, event);
        return super.onKeyUp(code, event);
      }
       */
    }
  }
}
