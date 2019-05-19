package arscene;

import processing.ar.*;
import processing.core.PApplet;
import processing.core.PMatrix3D;

public class Sketch extends PApplet {
  float angle = 0;
  float[] points;
  PMatrix3D mat = new PMatrix3D();
  int anchorId;

  public void settings() {
    fullScreen(AR);
  }

  public void setup() {

  }

  public void draw() {
    // At this point, there is no much AR-specific API, but you can get the AR Core session, frame,
    // and camera to extract more information about the AR scene.
//    PSurfaceAR surface = (PSurfaceAR) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

    background(0);
    lights();


    for (int i = 0; i < trackableCount(); i++) {
      int status = trackableStatus(i);
      if (status ==  PAR.PAUSED || status ==  PAR.STOPPED) continue;
      if (!trackableSelected(i)) continue;

      if (status == PAR.CREATED) {
        anchorId = createAnchor(trackableId(i), 0, 0.3f, 0);
      }

      float lenx = trackableExtentX(i);
      float lenz = trackableExtentZ(i);
      points = getTrackablePolygon(i, points);

      getTrackableMatrix(i, mat);
      pushMatrix();
      applyMatrix(mat);
      fill(255, 0, 0, 100);
      beginShape();
      for (int n = 0; n < points.length/2; n++) {
        float x = points[2 * n];
        float z = points[2 * n + 1];
        vertex(x, 0, z);
      }

//      vertex(-lenx/2, 0, -lenz/2);
//      vertex(+lenx/2, 0, -lenz/2);
//      vertex(+lenx/2, 0, +lenz/2);
//      vertex(-lenx/2, 0, +lenz/2);
      endShape();
      popMatrix();
    }


    if (0 < anchorCount()) {
      anchor(anchorId);
      fill(0xFCB736);
      noStroke();
      sphere(0.10f);
      rotateZ(angle);
      translate(0, 0.3f, 0);
      sphere(0.05f);
      angle += 0.1;
    }
  }
}
