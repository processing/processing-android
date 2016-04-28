package processing.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import processing.app.PContainer;
import processing.app.PFragment;
import processing.opengl.PSurfaceGLES;

public class PSurfaceAndroid2D implements PSurface, PConstants {
  protected PApplet sketch;
  protected PGraphics graphics;
  protected PContainer container;
  protected Activity activity;
  protected WallpaperService wallpaper;
  protected CanvasWatchFaceService watchface;

  protected View view;

  protected SketchSurfaceView surface;

  public PSurfaceAndroid2D() {

  }

  public PSurfaceAndroid2D(PGraphics graphics, PContainer container, SurfaceHolder holder) {
    this.sketch = graphics.parent;
    this.graphics = graphics;
    this.container = container;
    if (container.getKind() == PContainer.FRAGMENT) {
      PFragment frag = (PFragment)container;
      activity = frag.getActivity();
      surface = new SketchSurfaceView(activity, null);
    } else if (container.getKind() == PContainer.WALLPAPER) {
      wallpaper = (WallpaperService)container;
      surface = new SketchSurfaceView(wallpaper, holder);
    } else if (container.getKind() == PContainer.WATCHFACE_CANVAS) {
      watchface = (CanvasWatchFaceService)container;
      surface = null;
    }
  }

  @Override
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


  public AssetManager getAssets() {
    if (container.getKind() == PContainer.FRAGMENT) {
      return activity.getAssets();
    } else if (container.getKind() == PContainer.WALLPAPER) {
      return wallpaper.getBaseContext().getAssets();
    } else if (container.getKind() == PContainer.WATCHFACE_CANVAS) {
      return watchface.getBaseContext().getAssets();
    }
    return null;
  }


  public void startActivity(Intent intent) {
    if (container.getKind() == PContainer.FRAGMENT) {
      container.startActivity(intent);
    }
  }


  public void setSystemUiVisibility(int visibility) {
    int kind = container.getKind();
    if (kind == PContainer.FRAGMENT || kind == PContainer.WALLPAPER) {
      surface.setSystemUiVisibility(visibility);
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
        overallLayout.setBackgroundColor(sketch.sketchWindowColor());
//        window.setContentView(overallLayout);
        rootView = overallLayout;
      }
      setRootView(rootView);
    } else if (container.getKind() == PContainer.WALLPAPER) {
      int displayWidth = container.getWidth();
      int displayHeight = container.getHeight();
      View rootView;
      // Looks like a wallpaper can be larger than the screen res, and have an offset, need to
      // look more into that.
      if (sketchWidth == displayWidth && sketchHeight == displayHeight) {
        // If using the full screen, don't embed inside other layouts
//        window.setContentView(surfaceView);
        rootView = getSurfaceView();
      } else {
        // If not using full screen, setup awkward view-inside-a-view so that
        // the sketch can be centered on screen. (If anyone has a more efficient
        // way to do this, please file an issue on Google Code, otherwise you
        // can keep your "talentless hack" comments to yourself. Ahem.)
        RelativeLayout overallLayout = new RelativeLayout(wallpaper);
        RelativeLayout.LayoutParams lp =
          new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
                                          LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);

        LinearLayout layout = new LinearLayout(wallpaper);
        layout.addView(getSurfaceView(), sketchWidth, sketchHeight);
        overallLayout.addView(layout, lp);
        overallLayout.setBackgroundColor(sketch.sketchWindowColor());
//        window.setContentView(overallLayout);
        rootView = overallLayout;
      }
      setRootView(rootView);
    }
  }


  public String getName() {
    if (container.getKind() == PContainer.FRAGMENT) {
      return activity.getComponentName().getPackageName();
    } else if (container.getKind() == PContainer.WALLPAPER) {
      return wallpaper.getPackageName();
    } else if (container.getKind() == PContainer.WATCHFACE_CANVAS) {
      return watchface.getPackageName();
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
    } else if (container.getKind() == PContainer.WATCHFACE_CANVAS) {
      return watchface.getFilesDir();
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
    } else if (container.getKind() == PContainer.WATCHFACE_CANVAS) {
      return watchface.getFileStreamPath(path);
    }
    return null;
  }



  public void dispose() {
    // TODO Auto-generated method stub

  }

  ///////////////////////////////////////////////////////////

  // Thread handling

  private final Handler handler = new Handler();
  private final Runnable drawRunnable = new Runnable() {
    public void run() {
      if (sketch != null) {
        sketch.handleDraw();
      }
      scheduleNextDraw();
    }
  };

  private void scheduleNextDraw() {
    handler.removeCallbacks(drawRunnable);
    container.requestDraw();
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
    if (container.canDraw()) {
      handler.postDelayed(drawRunnable, waitMillis);
    }
  }


  private void pauseNextDraw() {
    handler.removeCallbacks(drawRunnable);
  }


  private void requestNextDraw() {
    handler.post(drawRunnable);
  }


  public void startThread() {
    requestNextDraw();
  }


  public void pauseThread() {
    pauseNextDraw();
  }


  public void resumeThread() {
    scheduleNextDraw();
  }


  public boolean stopThread() {
    pauseNextDraw();
    return true;
  }


  public boolean isStopped() {
    return !handler.hasMessages(0);
  }

  ///////////////////////////////////////////////////////////

  // GL SurfaceView

  public class SketchSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    SurfaceHolder holder;

    public SketchSurfaceView(Context context, SurfaceHolder holder) {
      super(context);
      this.holder = holder;

//    println("surface holder");
    // Install a SurfaceHolder.Callback so we get notified when the
    // underlying surface is created and destroyed
      SurfaceHolder h = getHolder();
      h.addCallback(this);
//    surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU); // no longer needed.

//    println("setting focusable, requesting focus");
      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();
//    println("done making surface view");
    }

    @Override
    public SurfaceHolder getHolder() {
      if (holder == null) {
        return super.getHolder();
      } else {
        return holder;
      }
    }
//  public PGraphics getGraphics() {
//    return g2;
//  }

    // part of SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
    }


    // part of SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
      //g2.dispose();
    }


    // part of SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
      if (PApplet.DEBUG) {
        System.out.println("SketchSurfaceView2D.surfaceChanged() " + w + " " + h);
      }

      sketch.displayHeight = w;
      sketch.displayHeight = h;
      graphics.setSize(sketch.sketchWidth(), sketch.sketchHeight());
      sketch.surfaceChanged();

//    width = w;
//    height = h;
//
//    g.setSize(w, h);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
      super.onWindowFocusChanged(hasFocus);
      sketch.surfaceWindowFocusChanged(hasFocus);
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
}
