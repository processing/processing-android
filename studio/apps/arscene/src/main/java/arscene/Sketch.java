package arscene;

import processing.core.PApplet;
import processing.ar.*;
import processing.ar.render.*;

public class Sketch extends PApplet {
  PShape sphere;
  
  public void settings() {
    fullScreen(AR);
  }

  public void setup() { 
    sphere = createShape(SPHERE, 400);
  }

  public void draw() {
    lights();
    background(0);
    PPlane.setPlaneColor(0x00BCD4FF);
    shape(sphere);
  }
}
