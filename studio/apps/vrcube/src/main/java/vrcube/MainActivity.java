package vrcube;

import android.os.Bundle;

import processing.vr.PVR;
import processing.core.PApplet;

public class MainActivity extends PVR {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    PApplet sketch = new Sketch();
    setSketch(sketch);
  }
}