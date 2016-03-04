package processing.app;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.app.Fragment;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import processing.core.PApplet;
import processing.core.PConstants;

public class PFragment extends Fragment implements PConstants {

  private PApplet sketch;

  public PFragment() {
  }

  public PFragment(PApplet sketch) {
    System.err.println("-----> PFragment CONSTRUCTOR: " + sketch);
    this.sketch = sketch;
  }

  public void setSketch(PApplet sketch) {
    this.sketch = sketch;
  }

  @Override
  public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    if (sketch != null) {
      sketch.initSurface(getActivity());
      return sketch.surface.getRootView();
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
    System.err.println("----> ON START: " + sketch);
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
    sketch.onBackPressed();
  }


  public void setOrientation(int which) {
    if (which == PORTRAIT) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    } else if (which == LANDSCAPE) {
      getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
  }
}
