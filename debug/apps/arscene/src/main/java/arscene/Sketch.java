package arscene;

import java.util.ArrayList;

import processing.ar.*;
import processing.core.PApplet;

public class Sketch extends PApplet {
  float angle;
  Anchor selAnchor;
  ArrayList<Anchor> regAnchors;

  Tracker tracker;

  public void settings() {
    fullScreen(AR);
  }

  public void setup() {
    tracker = new Tracker(this);
    tracker.start();
    regAnchors = new ArrayList<Anchor>();
  }

  public void draw() {
    // The AR Core session, frame and camera can be accessed through Processing's surface object
    // to obtain the full information about the AR scene:
//    PSurfaceAR surface = (PSurfaceAR) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

    lights();

/*
    if (mousePressed) {
      // Create new anchor at the current touch point
      if (selAnchor != null) selAnchor.dispose();
      selAnchor = new Anchor(tracker, mouseX, mouseY);
    }
*/

    // Draw objects attached to each anchor
    for (Anchor anchor : regAnchors) {
      if (anchor.isTracking()) drawBox(anchor, 255, 255, 255);
      if (anchor.isStopped()) anchor.dispose();
    }

//    for (int i =  : regAnchors){
//      if (status == PAR.STOPPED) anchor.dispose();
//    }


    if (selAnchor != null) {
      drawBox(selAnchor, 255, 0, 0);
    }

    // Draw trackable planes
    for (int i = 0; i < tracker.count(); i++) {
      Trackable trackable = tracker.get(i);
      if (!trackable.isTracking()) continue;

      pushMatrix();
      trackable.transform();
      if (mousePressed && trackable.isSelected(mouseX, mouseY)) {
        fill(255, 0, 0, 100);
      } else {
        fill(255, 100);
      }
      beginShape();
      float[] points = trackable.getPolygon();
      for (int n = 0; n < points.length / 2; n++) {
        float x = points[2 * n];
        float z = points[2 * n + 1];
        vertex(x, 0, z);
      }
      endShape();
      popMatrix();
    }

    angle += 0.1;
  }

  public void drawBox(Anchor anchor, int r, int g, int b) {
    anchor.attach();
    fill(r, g, b);
    rotateY(angle);
    box(0.15f);
    anchor.detach();
  }

  public void trackableEvent(Trackable t) {
    if (regAnchors.size() < 10) {
      float x0 = 0, y0 = 0;
      if (t.isWallPlane()) {
        // The new trackable is a wall, so adding the anchor 0.3 meters to its side
        x0 = 0.3f;
      } else if (t.isFloorPlane()) {
        // The new trackable is a floor plane, so adding the anchor 0.3 meters above it
        y0 = 0.3f;
      } else {
        // The new trackable is a floor plane, so adding the anchor 0.3 meters below it
        y0 = -0.3f;
      }
      regAnchors.add(new Anchor(t, x0, y0, 0));
    }
  }
}
