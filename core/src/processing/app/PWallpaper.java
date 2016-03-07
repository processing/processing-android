package processing.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import processing.core.PApplet;
import processing.opengl.PGLES;
import processing.opengl.PGraphicsOpenGL;
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
    public class GLWallpaperSurfaceView extends GLSurfaceView {
      PGraphicsOpenGL g3;
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


        g3 = (PGraphicsOpenGL)(sketch.g);
        int quality = sketch.sketchQuality();
        if (1 < quality) {
          setEGLConfigChooser(((PGLES)g3.pgl).getConfigChooser(quality));
        }
        // The renderer can be set only once.
        setRenderer(((PGLES)g3.pgl).getRenderer());
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

        if (PApplet.DEBUG) {
          System.out.println("SketchSurfaceView3D.surfaceChanged() " + w + " " + h);
        }
        sketch.surfaceChanged();
//        width = w;
//        height = h;
//        g.setSize(w, h);

        // No need to call g.setSize(width, height) b/c super.surfaceChanged()
        // will trigger onSurfaceChanged in the renderer, which calls setSize().
        // -- apparently not true? (100110)
      }

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

    }


    @Override
    public void onCreate(SurfaceHolder surfaceHolder) {
      super.onCreate(surfaceHolder);
      if (sketch != null) {
        final GLWallpaperSurfaceView view = new GLWallpaperSurfaceView(PWallpaper.this);
        sketch.initSurface(PWallpaper.this, view);
        view.initRenderer();

        // By default we don't get touch events, so enable them.
        setTouchEventsEnabled(true);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable(){
            public void run() {
//                view.requestRender();
                sketch.g.requestDraw();
                handler.postDelayed(this, 30);
            }
        }, 40);

//        sketch.start();
      }
    }

    @Override
    public void onSurfaceCreated(SurfaceHolder surfaceHolder) {
      super.onSurfaceCreated(surfaceHolder);
    }

    @Override
    public void onSurfaceChanged (final SurfaceHolder holder, final int format, final int width, final int height) {
      super.onSurfaceChanged(holder, format, width, height);
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
      sketch.surfaceTouchEvent(event);
      super.onTouchEvent(event);
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
