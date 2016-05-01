package processing.android;

import android.app.Fragment;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import processing.core.PApplet;

public class PFragment extends Fragment implements AppComponent {

  private DisplayMetrics metrics;
  private PApplet sketch;

  public PFragment() {
  }

  public void initDimensions() {
    metrics = new DisplayMetrics();
    getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
  }

  public int getWidth() {
    return metrics.widthPixels;
  }

  public int getHeight() {
    return metrics.heightPixels;
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
      sketch.start();
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
//    System.err.println("----> ON START: " + sketch);
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


  public void requestDraw() {
  }

  public boolean canDraw() {
    return true;
  }
}
