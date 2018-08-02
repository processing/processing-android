package arscene;

import processing.core.PApplet;
import processing.ar.*;
import processing.ar.render.*;
import processing.core.PShape;

public class Sketch extends PApplet {
  PShape sphere;
  
  public void settings() {
    fullScreen(ARCORE);
  }

  public void setup() {
    // I think this should go here but not 100% sure
//    if (!hasPermission("android.permission.CAMERA")) {
//      requestPermission("android.permission.CAMERA");
//    }
    sphere = createShape(SPHERE, 400);
  }

  public void draw() {
    lights();
    background(0);
    PPlane.setPlaneColor(0x00BCD4FF);
    shape(sphere);
  }
}
