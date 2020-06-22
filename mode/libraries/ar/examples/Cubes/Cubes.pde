import processing.ar.*;

ARTracker tracker;
ARAnchor touchAnchor;
ArrayList<ARAnchor> trackAnchors;
float angle;

void setup() {
  fullScreen(AR);
  tracker = new ARTracker(this);
  tracker.start();
  trackAnchors = new ArrayList<ARAnchor>();
}

void draw() {
  // The AR Core session, frame and camera can be accessed through Processing's surface object
  // to obtain the full information about the AR scene:
//    ARSurface surface = (ARSurface) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

  lights();

  if (mousePressed) {
    // Create new anchor at the current touch point
    if (touchAnchor != null) touchAnchor.dispose();
    ARTrackable hit = tracker.get(mouseX, mouseY);
    if (hit != null) touchAnchor = new ARAnchor(hit);
    else touchAnchor = null;
  }

  // Draw objects attached to each anchor
  for (ARAnchor anchor : trackAnchors) {
    if (anchor.isTracking()) drawBox(anchor, 255, 255, 255);

    // It is very important to dispose anchors once they are no longer tracked.
    if (anchor.isStopped()) anchor.dispose();
  }
  if (touchAnchor != null) drawBox(touchAnchor, 255, 0, 0);

  // Conveniency function in the tracker object to remove disposed anchors from a list
  tracker.clearAnchors(trackAnchors);

  // Draw trackable planes
  for (int i = 0; i < tracker.count(); i++) {
    ARTrackable trackable = tracker.get(i);
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

void drawBox(ARAnchor anchor, int r, int g, int b) {
  anchor.attach();
  fill(r, g, b);
  rotateY(angle);
  box(0.15f);
  anchor.detach();
}

void trackableEvent(ARTrackable t) {
  if (trackAnchors.size() < 10) {
    float x0 = 0, y0 = 0;
    if (t.isWallPlane()) {
      // The new trackable is a wall, so adding the anchor 0.3 meters to its side
      x0 = 0.3;
    } else if (t.isFloorPlane()) {
      // The new trackable is a floor plane, so adding the anchor 0.3 meters above it
      y0 = 0.3;
    } else {
      // The new trackable is a floor plane, so adding the anchor 0.3 meters below it
      y0 = -0.3;
    }
    trackAnchors.add(new ARAnchor(t, x0, y0, 0.0));
  }
}