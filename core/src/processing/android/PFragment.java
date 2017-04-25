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

import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
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
  private @LayoutRes int layout = -1;


  public PFragment() {
    super();
  }


  public PFragment(PApplet sketch) {
    super();
    setSketch(sketch);
  }


  public void initDimensions() {
    WindowManager wm = getActivity().getWindowManager();
    Display display = wm.getDefaultDisplay();
    metrics = new DisplayMetrics();
    display.getMetrics(metrics);

//    display.getRealMetrics(metrics); // API 17 or higher
//    display.getRealSize(size);

    size = new Point();
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


  public int getDisplayWidth() {
    return size.x;
//    return metrics.widthPixels;
  }


  public int getDisplayHeight() {
    return size.y;
//    return metrics.heightPixels;
  }


  public float getDisplayDensity() {
    return metrics.density;
  }


  public int getKind() {
    return FRAGMENT;
  }


  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
    if (layout != -1) {
      sketch.parentLayout = layout;
    }
  }


  public PApplet getSketch() {
    return sketch;
  }


  public void setLayout(@LayoutRes int layout, @IdRes int id, FragmentActivity activity) {
    this.layout = layout;
    if (sketch != null) {
      sketch.parentLayout = layout;
    }
    FragmentManager manager = activity.getSupportFragmentManager();
    FragmentTransaction transaction = manager.beginTransaction();
    transaction.add(id, this);
    transaction.commit();
  }


  public void setView(View view, FragmentActivity activity) {
    FragmentManager manager = activity.getSupportFragmentManager();
    FragmentTransaction transaction = manager.beginTransaction();
    transaction.add(view.getId(), this);
    transaction.commit();
  }


  public boolean isService() {
    return false;
  }


  public ServiceEngine getEngine() {
    return null;
  }


  public void dispose() {
  }


  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    if (sketch != null) {
      sketch.initSurface(inflater, container, savedInstanceState, this, null);
      return sketch.getSurface().getRootView();
    } else {
      return null;
    }
  }


  @Override
  public void onResume() {
    super.onResume();
    if (sketch != null) sketch.onResume();
  }


  @Override
  public void onPause() {
    super.onPause();
    if (sketch != null) sketch.onPause();
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    if (sketch != null) sketch.onDestroy();
  }


  @Override
  public void onStart() {
    super.onStart();
    if (sketch != null) sketch.onStart();
  }


  @Override
  public void onStop() {
    super.onStop();
    if (sketch != null) sketch.onStop();
  }


  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    if (PApplet.DEBUG) System.out.println("configuration changed: " + newConfig);
    super.onConfigurationChanged(newConfig);
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
    if (sketch == null) return false;
    return sketch.isLooping();
  }


//public void onBackPressed() {
//  sketch.exit();
//}
}
