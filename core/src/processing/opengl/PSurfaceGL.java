package processing.opengl;

import java.lang.reflect.Constructor;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import processing.core.PApplet;
import processing.core.PConstants;
import processing.core.PGraphics;
import processing.core.PSurface;

public class PSurfaceGL implements PSurface, PConstants {

  private PApplet sketch;
  private Activity activity;
  private View view;
  private SketchSurfaceViewGL surface;

  public PSurfaceGL(PApplet sketch, Activity activity, Class<?> rendererClass, int sw, int sh) {
    this.sketch = sketch;
    this.activity = activity;
    surface = new SketchSurfaceViewGL(activity, sw, sh,
      (Class<? extends PGraphicsOpenGL>)rendererClass);
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
    public SketchSurfaceViewGL(Context context, int wide, int high,
                               Class<? extends PGraphicsOpenGL> clazz) {
      super(context);

      // Check if the system supports OpenGL ES 2.0.
      final ActivityManager activityManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
      final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
      final boolean supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000;

      if (!supportsGLES2) {
        throw new RuntimeException("OpenGL ES 2.0 is not supported by this device.");
      }

      surfaceHolder = getHolder();
      // are these two needed?
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU);

      // The PGraphics object needs to be created here so the renderer is not
      // null. This is required because PApplet.onResume events (which call
      // this.onResume() and thus require a valid renderer) are triggered
      // before surfaceChanged() is ever called.

      if (clazz.equals(PGraphics2D.class)) { // P2D
        g3 = new PGraphics2D();
      } else if (clazz.equals(PGraphics3D.class)) { // P3D
        g3 = new PGraphics3D();
      } else { // something that extends P2D, P3D, or PGraphicsOpenGL
        try {
          Constructor<? extends PGraphicsOpenGL> constructor =
            clazz.getConstructor();
          g3 = constructor.newInstance();
        } catch (Exception exception) {
          throw new RuntimeException(
            "Error: Failed to initialize custom OpenGL renderer",
            exception);
        }
      }

      //set it up
      g3.setParent(sketch);
      g3.setPrimary(true);
      // Set semi-arbitrary size; will be set properly when surfaceChanged() called
      g3.setSize(wide, high);

      // Tells the default EGLContextFactory and EGLConfigChooser to create an GLES2 context.
      setEGLContextClientVersion(2);

      int quality = sketch.sketchQuality();
      if (1 < quality) {
        setEGLConfigChooser(((PGLES)g3.pgl).getConfigChooser(quality));
      }

      // The renderer can be set only once.
      setRenderer(((PGLES)g3.pgl).getRenderer());
      setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

      // assign this g to the PApplet
      sketch.g = g3;

      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();
    }


    public PGraphics getGraphics() {
      return g3;
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
}
