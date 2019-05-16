package arscene;

import processing.ar.*;
import processing.core.PApplet;

public class Sketch extends PApplet {
  float angle = 0;

  public void settings() {
    fullScreen(AR);
  }

  public void setup() {
    PAR.planeColor(0xB4E7FF);
  }

  public void draw() {
    // At this point, there is no much AR-specific API, but you can get the AR Core session, frame,
    // and camera to extract more information about the AR scene.
//    PSurfaceAR surface = (PSurfaceAR) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

    anchor();

    background(0);
    lights();


    fill(0xFCB736);
    noStroke();
    sphere(0.10f);
    rotateZ(angle);
    translate(0, 0.3f,0);
    sphere(0.05f);
    angle += 0.1;
  }
}
