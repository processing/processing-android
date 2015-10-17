package processing.core;

import android.app.Activity;
import android.view.SurfaceView;
import android.view.View;

public interface PSurface {

  public Activity getActivity();

  public View getRootView();

  public void setRootView(View view);

  public SurfaceView getSurfaceView();
}
