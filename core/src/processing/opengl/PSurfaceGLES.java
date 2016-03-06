package processing.opengl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetManager;
import android.opengl.GLSurfaceView;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import processing.app.PContainer;
import processing.app.PFragment;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PSurface;

public class PSurfaceGLES implements PSurface, PConstants {

  private PContainer container;
  private PGraphics graphics;

  private PApplet sketch;

  private Activity activity;
  private WallpaperService wallpaper;

  private View view;
  private SketchSurfaceViewGL surface;

  public PSurfaceGLES(PGraphics graphics, PContainer container, SurfaceHolder holder) {
    this.sketch = graphics.parent;
    this.graphics = graphics;
    this.container = container;

    if (container.getKind() == PContainer.FRAGMENT) {
      PFragment frag = (PFragment)container;
      activity = frag.getActivity();
      surface = new SketchSurfaceViewGL(activity, holder);
    } else if (container.getKind() == PContainer.WALLPAPER) {
      wallpaper = (WallpaperService)container;
      surface = new SketchSurfaceViewGL(wallpaper, holder);
    }
  }

//  public PSurfaceGLES(PGraphicsOpenGL graphics) {
//    this.graphics = graphics;
//  }
//
//  public PSurfaceGLES(PApplet sketch, Activity activity, Class<?> rendererClass, int sw, int sh) {
//    this.sketch = sketch;
//    this.activity = activity;
//    surface = new SketchSurfaceViewGL(activity, sw, sh,
//      (Class<? extends PGraphicsOpenGL>)rendererClass);
//  }

  public PContainer getContainer() {
    return container;
  }

  @Override
  public Activity getActivity() {
    return activity;
  }

  @Override
  public View getRootView() {
    return view;
  }

  @Override
  public void setRootView(View view) {
    this.view = view;
  }

  @Override
  public SurfaceView getSurfaceView() {
    return surface;
  }

  public class SketchSurfaceViewGL extends GLSurfaceView {
    PGraphicsOpenGL g3;
    SurfaceHolder surfaceHolder;


    @SuppressWarnings("deprecation")
    public SketchSurfaceViewGL(Context context, SurfaceHolder holder) {
      super(context);
      g3 = (PGraphicsOpenGL)graphics;

      // Check if the system supports OpenGL ES 2.0.
      final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
      final boolean supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000;

      if (!supportsGLES2) {
        throw new RuntimeException("OpenGL ES 2.0 is not supported by this device.");
      }

      surfaceHolder = null;
      if (holder == null) {
        surfaceHolder = getHolder();
      } else {
        surfaceHolder = holder;
      }
      // are these two needed?
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU);

      // Tells the default EGLContextFactory and EGLConfigChooser to create an GLES2 context.
      setEGLContextClientVersion(2);

      int quality = sketch.sketchQuality();
      if (1 < quality) {
        setEGLConfigChooser(((PGLES)g3.pgl).getConfigChooser(quality));
      }

      // The renderer can be set only once.
      setRenderer(((PGLES)g3.pgl).getRenderer());
      setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
      setPreserveEGLContextOnPause(true);

      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();
    }



    @Override
    public SurfaceHolder getHolder() {
      if (surfaceHolder != null) return surfaceHolder;
      else return super.getHolder();
    }


    public void onDestroy() {
      super.onDetachedFromWindow();
    }

    // part of SurfaceHolder.Callback
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      super.surfaceCreated(holder);
      if (PApplet.DEBUG) {
        System.out.println("surfaceCreated()");
      }
    }


    // part of SurfaceHolder.Callback
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
      super.surfaceDestroyed(holder);
      if (PApplet.DEBUG) {
        System.out.println("surfaceDestroyed()");
      }

      /*
      // TODO: Check how to make sure of calling g3.dispose() when this call to
      // surfaceDestoryed corresponds to the sketch being shut down instead of just
      // taken to the background.

      // For instance, something like this would be ok?
      // The sketch is being stopped, so we dispose the resources.
      if (!paused) {
        g3.dispose();
      }
      */
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
      super.surfaceChanged(holder, format, w, h);

      if (PApplet.DEBUG) {
        System.out.println("SketchSurfaceView3D.surfaceChanged() " + w + " " + h);
      }
      sketch.surfaceChanged();
//      width = w;
//      height = h;
//      g.setSize(w, h);

      // No need to call g.setSize(width, height) b/c super.surfaceChanged()
      // will trigger onSurfaceChanged in the renderer, which calls setSize().
      // -- apparently not true? (100110)
    }


    /**
     * Inform the view that the window focus has changed.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
      super.onWindowFocusChanged(hasFocus);
      sketch.surfaceWindowFocusChanged(hasFocus);
//      super.onWindowFocusChanged(hasFocus);
//      focused = hasFocus;
//      if (focused) {
////        println("got focus");
//        focusGained();
//      } else {
////        println("lost focus");
//        focusLost();
//      }
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


    // don't think i want to call stop() from here, since it might be swapping renderers
//    @Override
//    protected void onDetachedFromWindow() {
//      super.onDetachedFromWindow();
//      stop();
//    }
  }

  public AssetManager getAssets() {
    if (container.getKind() == PContainer.FRAGMENT) {
      return activity.getAssets();
    } else if (container.getKind() == PContainer.WALLPAPER) {
      return wallpaper.getBaseContext().getAssets();
    }
    return null;
  }

  public void startActivity(Intent intent) {
    if (container.getKind() == PContainer.FRAGMENT) {
      container.startActivity(intent);
    }
  }

  public void initView(int sketchWidth, int sketchHeight) {
    if (container.getKind() == PContainer.FRAGMENT) {
      int displayWidth = container.getWidth();
      int displayHeight = container.getHeight();
      View rootView;
      if (sketchWidth == displayWidth && sketchHeight == displayHeight) {
        // If using the full screen, don't embed inside other layouts
//        window.setContentView(surfaceView);
        rootView = getSurfaceView();
      } else {
        // If not using full screen, setup awkward view-inside-a-view so that
        // the sketch can be centered on screen. (If anyone has a more efficient
        // way to do this, please file an issue on Google Code, otherwise you
        // can keep your "talentless hack" comments to yourself. Ahem.)
        RelativeLayout overallLayout = new RelativeLayout(activity);
        RelativeLayout.LayoutParams lp =
          new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                                          LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);

        LinearLayout layout = new LinearLayout(activity);
        layout.addView(getSurfaceView(), sketchWidth, sketchHeight);
        overallLayout.addView(layout, lp);
//        window.setContentView(overallLayout);
        rootView = overallLayout;
      }
      setRootView(rootView);
    } else if (container.getKind() == PContainer.WALLPAPER) {

    }
  }

  public String getName() {
    if (container.getKind() == PContainer.FRAGMENT) {
      return activity.getComponentName().getPackageName();
    } else if (container.getKind() == PContainer.WALLPAPER) {
      return wallpaper.getPackageName();
    }
    return "";
  }

  public void setOrientation(int which) {
    if (container.getKind() == PContainer.FRAGMENT) {
      if (which == PORTRAIT) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      } else if (which == LANDSCAPE) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      }
    }
  }

  public File getFilesDir() {
    if (container.getKind() == PContainer.FRAGMENT) {
      return activity.getFilesDir();
    } else if (container.getKind() == PContainer.WALLPAPER) {
      return wallpaper.getFilesDir();
    }
    return null;
  }

  public InputStream openFileInput(String filename) {
    if (container.getKind() == PContainer.FRAGMENT) {
      try {
        return activity.openFileInput(filename);
      } catch (FileNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }

  public File getFileStreamPath(String path) {
    if (container.getKind() == PContainer.FRAGMENT) {
      return activity.getFileStreamPath(path);
    } else if (container.getKind() == PContainer.WALLPAPER) {
      return wallpaper.getFileStreamPath(path);
    }
    return null;
  }

  public void dispose() {
    // TODO Auto-generated method stub
    surface.onDestroy();
  }
}
