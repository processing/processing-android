import processing.ar.*;

ARTracker tracker;
ARAnchor anchor;
PShape earth;

void setup() {
  fullScreen(AR);

  tracker = new ARTracker(this);
  
  PImage earthImg = loadImage("earth.jpg");
  tracker.start();

  // Add the image to use as a marker to the AR tracker
  tracker.addImage("earth", earthImg);

  // If you know the size (in meters) of the image in the physical space,
  // you can specify it in the addImage(), this is optional but it would 
  // speed up the detection since the AR library will know the size of the 
  // marker beforehand
  // tracker.addImage("earth", earthImg, 0.25);
  
  earth = createShape(SPHERE, 0.15);
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