package vrcube;

import android.os.Bundle;

import processing.vr.PVR;
import processing.core.PApplet;

public class MainActivity extends PVR {
  private PApplet sketch;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    sketch = new Sketch();
    sketch.setExternal(true);
    setSketch(sketch);
  }

  @Override
  public void onBackPressed() {
    if (sketch != null) {
      sketch.onBackPressed();
      if (sketch.handledBackPressed) return;
    }
    super.onBackPressed();
  }
}