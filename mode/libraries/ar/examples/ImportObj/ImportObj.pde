import processing.ar.*;

Tracker tracker;
Anchor anchor;
PShape arObj;

void setup() {
  fullScreen(AR);
  arObj = loadShape("model.obj");

  tracker = new Tracker(this);
  tracker.start();  
}

void draw() {
  lights();
  
  if (mousePressed) {
    // Delete the old touch anchor, if any.
    if (anchor != null) anchor.dispose();

    // Create a new anchor at the current touch position.
    anchor = new Anchor(tracker, mouseX, mouseY);
  }

  if (anchor != null) {
    anchor.attach();
    shape(arObj);
    anchor.detach();
  }
}
