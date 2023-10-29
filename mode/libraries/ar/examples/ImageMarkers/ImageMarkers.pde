import processing.ar.*;

ARTracker tracker;
ARAnchor anchor;
PShape earth;

void setup() {
  fullScreen(AR);

  tracker = new ARTracker(this);
  
  PImage earthImg = loadImage("earth.jpg");
  tracker.start();  
  tracker.addImage("earth", earthImg);
  
  earth = createShape(SPHERE, 0.5);
}

void draw() {
  lights();
  
  if (mousePressed) {
    // Create new anchor at the current touch point
    if (anchor != null) anchor.dispose();
    ARTrackable hit = tracker.get(mouseX, mouseY);
    if (hit != null && hit.isImage() && hit.getName().equals("earth")) anchor = new ARAnchor(hit);
    else anchor = null;
  }

  if (anchor != null) {
    anchor.attach();
    shape(earth);
    anchor.detach();
  }
}