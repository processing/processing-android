import processing.ar.*;

PShape cube;

void setup() {
  fullScreen(ARCORE);
  cube = createShape(BOX, 0.25);
  ARPlane.setPlaneColor(#BCD4FF);
}

void draw() {
  lights();
  background(0);  
  shape(cube);
}