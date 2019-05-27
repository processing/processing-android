/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

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

package processing.ar;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.*;

import com.google.ar.core.*;
import com.google.ar.core.exceptions.*;

import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.opengl.PGLES;
import processing.opengl.PGraphicsOpenGL;
import processing.opengl.PSurfaceGLES;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.io.File;
import java.io.InputStream;

public class PSurfaceAR extends PSurfaceGLES {
  private static String T_ALERT_MESSAGE = "ALERT";
  private static String C_NOT_SUPPORTED = "ARCore SDK required to run this app type";
  private static String T_PROMPT_MESSAGE = "PROMPT";
  private static String C_SUPPORTED = "ARCore SDK is installed";
  private static String C_EXCEPT_INSTALL = "Please install ARCore";
  private static String C_EXCEPT_UPDATE_SDK = "Please update ARCore";
  private static String C_EXCEPT_UPDATE_APP = "Please update this app";
  private static String C_DEVICE = "This device does not support AR";

  // Made these public so they can be accessed from the sketch
  public Session session;
  public Frame frame;
  public Camera camera;

  protected GLSurfaceView surfaceView;
  protected AndroidARRenderer renderer;
  protected PGraphicsAR par;

  protected RotationHandler displayRotationHelper;

  public PSurfaceAR(PGraphics graphics, AppComponent appComponent, SurfaceHolder surfaceHolder) {
    super(graphics, appComponent, surfaceHolder);
    this.sketch = graphics.parent;
    this.graphics = graphics;
    this.component = appComponent;
    this.pgl = (PGLES) ((PGraphicsOpenGL) graphics).pgl;

    par = (PGraphicsAR) graphics;

    displayRotationHelper = new RotationHandler(activity);
    surfaceView = new SurfaceViewAR(activity);
  }

  @Override
  public Context getContext() {
    return activity;
  }

  @Override
  public void finish() {
    sketch.getActivity().finish();
  }

  @Override
  public AssetManager getAssets() {
    return sketch.getContext().getAssets();
  }

  @Override
  public void startActivity(Intent intent) {
    sketch.getContext().startActivity(intent);
  }

  @Override
  public void initView(int sketchWidth, int sketchHeight) {
    Window window = sketch.getActivity().getWindow();

    window.getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    window.setContentView(surfaceView);
  }

  @Override
  public String getName() {
    return sketch.getActivity().getComponentName().getPackageName();
  }

  @Override
  public void setOrientation(int which) {
  }

  @Override
  public File getFilesDir() {
    return sketch.getActivity().getFilesDir();
  }

  @Override
  public InputStream openFileInput(String filename) {
    return null;
  }

  @Override
  public File getFileStreamPath(String path) {
    return sketch.getActivity().getFileStreamPath(path);
  }

  @Override
  public void dispose() {
  }


  public class SurfaceViewAR extends GLSurfaceView {
    public SurfaceViewAR(Context context) {
      super(context);

      final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
      final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
      final boolean supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000;

      if (!supportsGLES2) {
        throw new RuntimeException("OpenGL ES 2.0 is not supported by this device.");
      }

      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();

      setPreserveEGLContextOnPause(true);
      setEGLContextClientVersion(2);
      setEGLConfigChooser(8, 8, 8, 8, 16, 0);
      setRenderer(getARRenderer());
      setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
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

  public AndroidARRenderer getARRenderer() {
    renderer = new AndroidARRenderer();
    return renderer;
  }

  protected class AndroidARRenderer implements GLSurfaceView.Renderer {
    public AndroidARRenderer() {
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
      pgl.getGL(null);
      par.createBackgroundRenderer();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
      displayRotationHelper.onSurfaceChanged(width, height);
      GLES20.glViewport(0, 0, width, height);

      sketch.surfaceChanged();
      graphics.surfaceChanged();

      sketch.setSize(width, height);
      graphics.setSize(sketch.sketchWidth(), sketch.sketchHeight());
    }

    @Override
    public void onDrawFrame(GL10 gl) {
      if (session == null) return;

      displayRotationHelper.updateSessionIfNeeded(session);
      try {

        par.setCameraTexture();
        frame = session.update();
        camera = frame.getCamera();

        if (camera.getTrackingState() == TrackingState.TRACKING) par.updateTrackables();
        par.updateMatrices();

        sketch.calculate();
        sketch.handleDraw();


      } catch (Throwable tr) {
        PGraphics.showWarning("An error occurred in ARCORE: " + tr.getMessage());
      }
    }
  }


  @Override
  public void startThread() {
  }

  @Override
  public void pauseThread() {
    if (session != null) {
      displayRotationHelper.onPause();
      surfaceView.onPause();
      session.pause();
    }
  }

  @Override
  public void resumeThread() {
    if (!sketch.hasPermission("android.permission.CAMERA")) return;

    if (session == null) {
      String message = null;
      String exception = null;
      try {
        // Perhaps this should be done in the MainActivity?
        // https://github.com/google-ar/arcore-android-sdk/blob/master/samples/hello_ar_java/app/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java
        switch (ArCoreApk.getInstance().requestInstall(sketch.getActivity(), true)) {
          case INSTALL_REQUESTED:
            message(T_ALERT_MESSAGE, C_NOT_SUPPORTED);
            return;
          case INSTALLED:
            break;
        }

        session = new Session(activity);
      } catch (UnavailableArcoreNotInstalledException
          | UnavailableUserDeclinedInstallationException e) {
        message = C_EXCEPT_INSTALL;
        exception = e.toString();
      } catch (UnavailableApkTooOldException e) {
        message = C_EXCEPT_UPDATE_SDK;
        exception = e.toString();
      } catch (UnavailableSdkTooOldException e) {
        message = C_EXCEPT_UPDATE_APP;
        exception = e.toString();
      } catch (Exception e) {
        e.printStackTrace();
        System.out.println("That's that");
      }

      if (message != null) {
        message(T_ALERT_MESSAGE, message + " -- " + exception);
      }

      Config config = new Config(session);
      if (!session.isSupported(config)) {
        message(T_PROMPT_MESSAGE, C_DEVICE);
      }
      session.configure(config);
    }
    try {
      session.resume();
    } catch (CameraNotAvailableException e) {
    }
    surfaceView.onResume();
    displayRotationHelper.onResume();
  }

  public void message(String _title, String _message) {
    final Activity parent = activity;
    final String message = _message;
    final String title = _title;

    parent.runOnUiThread(new Runnable() {
      public void run() {
        new AlertDialog.Builder(parent)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog,
                                      int which) {
                  }
                }).show();
      }
    });
  }
}
