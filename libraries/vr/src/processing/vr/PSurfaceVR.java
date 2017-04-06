/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.vr;

import java.io.File;
import java.io.InputStream;

import javax.microedition.khronos.egl.EGLConfig;

import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.opengl.PGLES;
import processing.opengl.PGraphicsOpenGL;
import processing.opengl.PSurfaceGLES;
import android.view.Window;
import android.view.WindowManager;

public class PSurfaceVR extends PSurfaceGLES {
  protected GLVRSurfaceView glview;
  protected PGraphicsVR pvr;

  protected GvrActivity vrActivity;
  protected AndroidVRStereoRenderer renderer;

  public PSurfaceVR(PGraphics graphics, AppComponent component, SurfaceHolder holder, boolean vr) {
    this.sketch = graphics.parent;
    this.graphics = graphics;
    this.component = component;
    this.pgl = (PGLES)((PGraphicsOpenGL)graphics).pgl;

    vrActivity = (GvrActivity)component;
    this.activity = vrActivity;
    pvr = (PGraphicsVR)graphics;

    glview = new GLVRSurfaceView(vrActivity);
    glview.setStereoModeEnabled(vr);
    if (vr) {
      glview.setDistortionCorrectionEnabled(true);
      glview.setNeckModelEnabled(true);
//      glview.setElectronicDisplayStabilizationEnabled(true);
    }

    // Enable Cardboard-trigger feedback with Daydream headsets. This is a simple way of supporting
    // Daydream controller input for basic interactions using the existing Cardboard trigger API.
    glview.enableCardboardTriggerEmulation();

    if (glview.setAsyncReprojectionEnabled(true)) {
      // Async reprojection decouples the app framerate from the display framerate,
      // allowing immersive interaction even at the throttled clockrates set by
      // sustained performance mode.
      AndroidCompat.setSustainedPerformanceMode(vrActivity, true);
    }
    vrActivity.setGvrView(glview);

    surfaceView = null;
  }

  @Override
  public Context getContext() {
    return vrActivity;
  }

  @Override
  public Activity getActivity() {
    return vrActivity;
  }

  @Override
  public void finish() {
    vrActivity.finish();
  }

  @Override
  public AssetManager getAssets() {
    return vrActivity.getAssets();
  }

  @Override
  public void startActivity(Intent intent) {
    vrActivity.startActivity(intent);
  }

  @Override
  public void initView(int sketchWidth, int sketchHeight) {
    Window window = vrActivity.getWindow();

    // Take up as much area as possible
    //requestWindowFeature(Window.FEATURE_NO_TITLE);  // may need to set in theme properties
    // the above line does not seem to be needed when using VR
    //  android:theme="@android:style/Theme.Holo.NoActionBar.Fullscreen" >
    window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

    // This does the actual full screen work
    window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);

    window.setContentView(glview);
  }

  @Override
  public String getName() {
    return vrActivity.getComponentName().getPackageName();
  }

  @Override
  public void setOrientation(int which) {
    if (component.getKind() == AppComponent.FRAGMENT) {
      if (which == PORTRAIT) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      } else if (which == LANDSCAPE) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      }
    }
  }

  @Override
  public File getFilesDir() {
    return vrActivity.getFilesDir();
  }

  @Override
  public InputStream openFileInput(String filename) {
    return null;
  }

  @Override
  public File getFileStreamPath(String path) {
    return vrActivity.getFileStreamPath(path);
  }

  @Override
  public void dispose() {
//    surface.onDestroy();
  }

  ///////////////////////////////////////////////////////////

  // Thread handling

  private boolean running = false;

  @Override
  public void startThread() {
    glview.onResume();
    running = true;
  }

  @Override
  public void pauseThread() {
    glview.onPause();
    running = false;
  }

  @Override
  public void resumeThread() {
    glview.onResume();
    running = true;
  }

  @Override
  public boolean stopThread() {
    running = false;
    return true;
  }

  @Override
  public boolean isStopped() {
    return !running;
  }

  ///////////////////////////////////////////////////////////

  public class GLVRSurfaceView extends GvrView {
    public GLVRSurfaceView(Context context) {
      super(context);

      // Check if the system supports OpenGL ES 2.0.
      final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
      final boolean supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000;

      if (!supportsGLES2) {
        throw new RuntimeException("OpenGL ES 2.0 is not supported by this device.");
      }

      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();

      int quality = sketch.sketchQuality();
      if (1 < quality) {
        setEGLConfigChooser(8, 8, 8, 8, 16, 1);
      }
      // The renderer can be set only once.
      setRenderer(getVRStereoRenderer());
    }

    /*
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
*/

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

  ///////////////////////////////////////////////////////////

  // Android specific classes (Renderer, ConfigChooser)


  public AndroidVRStereoRenderer getVRStereoRenderer() {
    renderer = new AndroidVRStereoRenderer();
    return renderer;
  }


  protected class AndroidVRStereoRenderer implements GvrView.StereoRenderer {
    public AndroidVRStereoRenderer() {

    }


    @Override
    public void onNewFrame(HeadTransform transform) {
      pgl.getGL(null);
      pvr.headTransform(transform);
    }

    @Override
    public void onDrawEye(Eye eye) {
      pvr.eyeTransform(eye);
      sketch.handleDraw();
    }

    @Override
    public void onFinishFrame(Viewport arg0) {
    }

    @Override
    public void onRendererShutdown() {
    }

    @Override
    public void onSurfaceChanged(int arg0, int arg1) {
    }

    @Override
    public void onSurfaceCreated(EGLConfig arg0) {
    }
  }
}
