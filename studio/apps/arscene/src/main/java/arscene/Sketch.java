package arscene;

import processing.core.PApplet;
import processing.ar.*;
import processing.ar.render.*;
import processing.core.PShape;

public class Sketch extends PApplet {
  public void settings() {
    fullScreen(ARCORE);
  }

  public void setup() {
    PPlane.setPlaneColor(0x00BCD4FF);
    PPlane.setPlaneTexture("triangle.png");
  }

  public void draw() {
    lights();
    background(0);
//    PPlane.setPlaneColor(0x00BCD4FF);
//    shape(sphere);

    fill(0x769FE0);
    // Pulsating box to test scale...
//    box(map(sin(frameCount/100.0f), -1, 1, 0, 500));
    //box(0.25f);

    sphere(0.10f);
    rotateZ(frameCount * 0.1f);
    translate(0, 0.3f,0);
    sphere(0.05f);
  }
}
