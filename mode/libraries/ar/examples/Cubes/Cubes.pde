import processing.ar.*;

float[] points;
PMatrix3D mat;
float angle;
int oldSelAnchor;
int selAnchor;

void setup() {
  fullScreen(AR);
  mat = new PMatrix3D();
}

void draw() {
  // The AR Core session, frame and camera can be accessed through Processing's surface object
  // to obtain the full information about the AR scene:
//    PSurfaceAR surface = (PSurfaceAR) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

  // No background call is needed, the screen is refreshed each frame with the image from the camera

  lights();


  if (mousePressed) {
    // Create new anchor at the current touch point
    oldSelAnchor = selAnchor;
    selAnchor = createAnchor(mouseX, mouseY);
  }

  // Draw objects attached to each anchor
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
    box(0.15);
    popMatrix();
  }

  // Draw trackable planes
  for (int i = 0; i < trackableCount(); i++) {
    int status = trackableStatus(i);
    if (status ==  PAR.PAUSED || status == PAR.STOPPED) continue;

    if (status == PAR.CREATED && trackableCount() < 10) {
      // Add new anchor associated to this trackable, 0.3 meters above it
      if (trackableType(i) == PAR.PLANE_WALL) {
        createAnchor(i, 0.3, 0, 0);
      } else {
        createAnchor(i, 0, 0.3, 0);
      }
    }

    points = getTrackablePolygon(i, points);

    getTrackableMatrix(i, mat);
    pushMatrix();
    applyMatrix(mat);
    if (mousePressed && trackableSelected(i, mouseX, mouseY)) {
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

  angle += 0.1;    
}