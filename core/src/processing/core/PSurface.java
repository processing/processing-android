package processing.core;

import java.io.File;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.view.SurfaceView;
import android.view.View;
import processing.app.PContainer;

/*
 * Holds the surface view associated with the sketch, and the rendering thread
 * handling
 */
public interface PSurface {
  public Activity getActivity();
  public PContainer getContainer();

  public void dispose();

  public View getRootView();

  public String getName();

  public void setRootView(View view);

  public SurfaceView getSurfaceView();

  public void initView(int sketchWidth, int sketchHeight);

  public void startActivity(Intent intent);

  public void setOrientation(int which);

  public File getFilesDir();

  public File getFileStreamPath(String path);

  public InputStream openFileInput(String filename);

  public AssetManager getAssets();

  public void setSystemUiVisibility(int visibility);

  public void startThread();

  public void pauseThread();

  public void resumeThread();

  public boolean stopThread();

  public boolean isStopped();
}
