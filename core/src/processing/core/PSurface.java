package processing.core;

import java.io.File;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.view.SurfaceView;
import android.view.View;
import processing.app.PContainer;

public interface PSurface {
  public Activity getActivity();
  public PContainer getContainer();

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
}
