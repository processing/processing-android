import processing.ar.*;

float angle;
Anchor selAnchor;
ArrayList<Anchor> regAnchors;

Tracker tracker;

void setup() {
  fullScreen(AR);
  mat = new PMatrix3D();

  tracker = new Tracker(this);
  tracker.start();
  regAnchors = new ArrayList<Anchor>();
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
    if (selAnchor != null) selAnchor.dispose();
    selAnchor = new Anchor(tracker, mouseX, mouseY);
  }

  // Draw objects attached to each anchor
  for (Anchor anchor: regAnchors) {

    int status = anchor.status(i);
    if (status ==  PAR.PAUSED || status == PAR.STOPPED) {
      if (status == PAR.STOPPED) anchor.dispose();
      continue;
    }

    anchor.attach();
    fill(255);
    rotateY(angle);
    box(0.15);
    anchor.detach();
  }

  if (selAnchor != null) {
    selAnchor.attach();
    fill(255, 0, 0);
    rotateY(angle);
    box(0.15);
    selAnchor.detach();
  }
  

  // Draw trackable planes
  for (int i = 0; i < tracker.count(); i++) {
    Trackable trackable = tracker.get(i);

    int status = trackable.status();
    if (status ==  PAR.PAUSED || status == PAR.STOPPED) continue;

    if (status == PAR.CREATED && regAnchor.size() < 10) {
      // Add new anchor associated to this trackable, 0.3 meters above it
      Anchor anchor;
      if (trackable.type() == PAR.PLANE_WALL) {
        anchor = new Anchor(trackable, 0.3, 0, 0);
      } else {
        anchor = new Anchor(trackable, 0, 0.3, 0);
      }
      regAnchors.add(anchor);
    }

    float[] points = trackable.getPolygon();
    
    pushMatrix();
    trackable.transform();
    if (mousePressed && trackable.selected(mouseX, mouseY)) {
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
