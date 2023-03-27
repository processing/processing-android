package vrcube;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import processing.vr.VRActivity;
import processing.core.PApplet;

public class MainActivity extends VRActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
      getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }

    PApplet sketch = new Sketch();
    setSketch(sketch);
  }
}