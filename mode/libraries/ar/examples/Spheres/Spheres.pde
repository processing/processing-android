import processing.ar.*;

Tracker tracker;
Anchor anchor;
float angle;

void setup() {
  fullScreen(AR);
  noStroke();

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
    fill(217, 121, 255);
    sphere(0.10f);
    rotateY(angle);
    translate(0, 0, 0.3f);
    sphere(0.05f);
    angle += 0.1;
    anchor.detach();
  }

  // Draw trackable planes
  for (int i = 0; i < tracker.count(); i++) {
    Trackable trackable = tracker.get(i);

    int status = trackable.status();
    if (status ==  PAR.PAUSED || status == PAR.STOPPED) continue;


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
}
