import processing.ar.*;

ARTracker tracker;
ARAnchor anchor;
PShape arObj;

void setup() {
  fullScreen(AR);
  arObj = loadShape("model.obj");

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
    shape(arObj);
    anchor.detach();
  }
}
