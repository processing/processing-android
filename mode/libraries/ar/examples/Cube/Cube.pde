import processing.ar.*;

PShape cube;

void setup() {
  fullScreen(AR);
  cube = createShape(BOX, 0.25);
  PAR.planeColor(#BCD4FF);
}

void draw() {
  lights();
  background(0);  
  shape(cube);
}