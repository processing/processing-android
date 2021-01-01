/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-17 The Processing Foundation

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

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.util.DisplayMetrics;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
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
    metrics = new DisplayMetrics();
    size = new Point();
    WindowManager wm = getActivity().getWindowManager();
    Display display = wm.getDefaultDisplay();
    CompatUtils.getDisplayParams(display, metrics, size);
  }


  public int getDisplayWidth() {
    return size.x;
  }


  public int getDisplayHeight() {
    return size.y;
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

      // For compatibility with older sketches that run some hardware initialization
      // inside onCreate(), don't call from Fragment.onCreate() because the surface
      // will not be yet ready, and so the reference to the activity and other
      // system variables will be null. In any case, onCreateView() is called
      // immediately after onCreate():
      // https://developer.android.com/reference/android/app/Fragment.html#Lifecycle
      sketch.onCreate(savedInstanceState);

      return sketch.getSurface().getRootView();
    } else {
      return null;
    }
  }


  @Override
  public void onStart() {
    super.onStart();
    if (sketch != null) {
      sketch.onStart();
    }
  }


  @Override
  public void onResume() {
    super.onResume();
    if (sketch != null) {
      sketch.onResume();
    }
  }


  @Override
  public void onPause() {
    super.onPause();
    if (sketch != null) {
      sketch.onPause();
    }
  }


  @Override
  public void onStop() {
    super.onStop();
    if (sketch != null) {
      sketch.onStop();
    }
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    if (sketch != null) {
      sketch.onDestroy();
    }
  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (sketch != null) sketch.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    if (sketch != null) sketch.onCreateOptionsMenu(menu, inflater);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    if (sketch != null) return sketch.onOptionsItemSelected(item);
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    if (sketch != null) sketch.onCreateContextMenu(menu, v, menuInfo);
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if (sketch != null) return sketch.onContextItemSelected(item);
    return super.onContextItemSelected(item);
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
    return sketch != null && sketch.isLooping();
  }
}
