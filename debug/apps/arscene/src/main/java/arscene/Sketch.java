package arscene;

import processing.ar.*;
import processing.core.PApplet;
import processing.core.PMatrix3D;

public class Sketch extends PApplet {
  float angle = 0;
  float[] points;
  PMatrix3D mat = new PMatrix3D();
  int oldSelAnchor;
  int selAnchor;

  public void settings() {
    fullScreen(AR);
  }

  public void setup() {
    noStroke();
  }

  public void draw() {
    // The AR Core session, frame and camera can be accessed through Processing's surface object
    // to obtain the full information about the AR scene:
//    PSurfaceAR surface = (PSurfaceAR) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

    lights();


    for (int i = 0; i < trackableCount(); i++) {
      int status = trackableStatus(i);
      if (status ==  PAR.PAUSED || status == PAR.STOPPED) continue;

      if (status == PAR.CREATED && trackableCount() < 10) {
        if (trackableType(i) == PAR.PLANE_WALL) {
          createAnchor(i, 0.3f, 0, 0);
        } else {
          createAnchor(i, 0, 0.3f, 0);
        }
      }

      points = getTrackablePolygon(i, points);

      getTrackableMatrix(i, mat);
      pushMatrix();
      applyMatrix(mat);
      if (trackableSelected(i, mouseX, mouseY)) {
        fill(255, 0, 0, 100);
      } else {
        fill(255, 100);
      }

      beginShape();
      for (int n = 0; n < points.length/2; n++) {
        float x = points[2 * n];
        float z = points[2 * n + 1];
        vertex(x, 0, z);
      }
      endShape();
      popMatrix();
    }

    if (mousePressed) {
      oldSelAnchor = selAnchor;
      selAnchor = createAnchor(mouseX, mouseY);
    }

    for (int i = 0; i < anchorCount(); i++) {
      int id = anchorId(i);
      if (oldSelAnchor == id) {
        deleteAnchor(i);
        continue;
      }

      int status = anchorStatus(i);
      if (status ==  PAR.PAUSED || status == PAR.STOPPED) {
        if (status == PAR.STOPPED) deleteAnchor(i);
        continue;
      }

      pushMatrix();
      anchor(i);

      if (selAnchor == id) {
        fill(255, 0, 0);
      } else {
        fill(255);
      }

      rotateY(angle);
      box(0.15f);
      popMatrix();
    }

    angle += 0.1;
  }
}
