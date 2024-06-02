import processing.ar.*;

ARTracker tracker;
ARAnchor anchor;
PShape arObj;
float angle;

void setup() {
  fullScreen(AR);

  noStroke();

  tracker = new ARTracker(this);
  tracker.start();
}

void draw() {
  lights();

  if (mousePressed) {
    // Create new anchor at the current touch point
    if (anchor != null) anchor.dispose();
    ARTrackable hit = tracker.get(mouseX, mouseY);
    if (hit != null) anchor = new ARAnchor(hit);
    else anchor = null;
  }

  if (anchor != null) {
    anchor.attach();
    fill(217, 121, 255);
    sphere(0.1);
    rotateY(angle);
    translate(0, 0, 0.3);
    sphere(0.05);
    angle += 0.1;
    anchor.detach();
  }

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

    beginShape(QUADS);
    float lx = trackable.lengthX();
    float lz = trackable.lengthZ();
    vertex(-lx/2, 0, -lz/2);
    vertex(-lx/2, 0, +lz/2);
    vertex(+lx/2, 0, +lz/2);
    vertex(+lx/2, 0, -lz/2);
    endShape();
    popMatrix();
  }
}
