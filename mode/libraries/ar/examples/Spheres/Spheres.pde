import processing.ar.*;

float angle;
float[] points;
PMatrix3D mat = new PMatrix3D();

void setup() {
  fullScreen(AR);
  noStroke();
  mat = new PMatrix3D();
}

void draw() {
  lights();

  if (mousePressed) {
    // Delete the old touch anchor, if any.
    if (0 < anchorCount()) deleteAnchor(0);

    // Create a new anchor at the current touch position.
    createAnchor(mouseX, mouseY);
  }

  if (0 < anchorCount()) {
    pushMatrix();
    anchor(0);
    fill(217, 121, 255);
    sphere(0.10f);
    rotateY(angle);
    translate(0, 0, 0.3f);
    sphere(0.05f);
    angle += 0.1;
    popMatrix();
  }

  for (int i = 0; i < trackableCount(); i++) {
    int status = trackableStatus(i);
    if (status ==  PAR.PAUSED || status == PAR.STOPPED) continue;
    points = getTrackablePolygon(i, points);

    getTrackableMatrix(i, mat);
    pushMatrix();
    applyMatrix(mat);

    if (mousePressed && trackableSelected(i, mouseX, mouseY)) {
      fill(255, 0, 0, 100);
    } else {
      fill(255, 100);
    }

    float minx = +1000;
    float maxx = -1000;
    float minz = +1000;
    float maxz = -1000;
    for (int n = 0; n < points.length/2; n++) {
      float x = points[2 * n];
      float z = points[2 * n + 1];
      minx = min(minx, x);
      maxx = max(maxx, x);
      minz = min(minz, z);
      maxz = max(maxz, z);
    }
    beginShape(QUADS);
    vertex(minx, 0, minz);
    vertex(minx, 0, maxz);
    vertex(maxx, 0, maxz);
    vertex(maxx, 0, minz);
    endShape();

    popMatrix();
  }
}
