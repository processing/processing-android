import processing.ar.*;

PShape arObj;

void setup() {
  fullScreen(AR);
  arObj = loadShape("model.obj");
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
    anchor(0);
    shape(arObj);
  }
}
