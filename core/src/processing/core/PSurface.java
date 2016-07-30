/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.core;

import java.io.File;
import java.io.InputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import processing.android.AppComponent;

/*
 * Holds the surface view associated with the sketch, and the rendering thread
 * handling
 */
public interface PSurface {
  public Context getContext();
  public Activity getActivity();
  public AppComponent getComponent();

  public void dispose();

  public View getRootView();

  public String getName();

  public void setRootView(View view);

  public SurfaceView getSurfaceView();

  public void initView(int sketchWidth, int sketchHeight);
  public void initView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

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

  public void finish();

  public void setFrameRate(float fps);
}
