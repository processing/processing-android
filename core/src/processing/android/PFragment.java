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

package processing.android;

import android.app.Fragment;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import processing.core.PApplet;

public class PFragment extends Fragment implements AppComponent {
  private DisplayMetrics metrics;
  private Point size;
  private PApplet sketch;

  public PFragment() {
  }

  public void initDimensions() {
    /*
    metrics = new DisplayMetrics();
//    getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
    WindowManager wm = getActivity().getWindowManager();
    Display display = wm.getDefaultDisplay();
    display.getRealMetrics(metrics);
    */

//    metrics = new DisplayMetrics();
//    getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
//    display.getRealMetrics(metrics); // API 17 or higher
//    display.getRealSize(size);

//    display.getMetrics(metrics);

    size = new Point();
    WindowManager wm = getActivity().getWindowManager();
    Display display = wm.getDefaultDisplay();
    if (Build.VERSION.SDK_INT >= 17) {
      display.getRealSize(size);
    } else if (Build.VERSION.SDK_INT >= 14) {
      // Use undocumented methods getRawWidth, getRawHeight
      try {
        size.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
        size.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
      } catch (Exception e) {
        display.getSize(size);
      }
    }
  }

  public int getWidth() {
    return size.x;
//    return metrics.widthPixels;
  }

  public int getHeight() {
    return size.y;
//    return metrics.heightPixels;
  }

  public int getKind() {
    return FRAGMENT;
  }

  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    if (sketch != null) {
      sketch.initSurface(this, null);
      sketch.startSurface();
      return sketch.getRootView();
    } else {
      return null;
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    sketch.onResume();
  }


  @Override
  public void onPause() {
    super.onPause();
    sketch.onPause();
  }


  @Override
  public void onDestroy() {
    sketch.onDestroy();
    super.onDestroy();
  }


  @Override
  public void onStart() {
    super.onStart();
    sketch.onStart();
  }


  @Override
  public void onStop() {
    sketch.onStop();
    super.onStop();
  }


  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    if (PApplet.DEBUG) System.out.println("configuration changed: " + newConfig);
    super.onConfigurationChanged(newConfig);
  }


  public void onBackPressed() {
    sketch.exit();
  }


  public void setOrientation(int which) {
    if (which == PORTRAIT) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    } else if (which == LANDSCAPE) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
  }


  public void requestDraw() {
  }

  public boolean canDraw() {
    return sketch.isLooping();
  }
}
