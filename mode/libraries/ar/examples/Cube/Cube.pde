import processing.ar.*;

PShape cube;

void setup() {
  fullScreen(AUGMENT);
  cube = createShape(BOX, 0.25);
  PAR.planeColor(#BCD4FF);
}

void draw() {
  lights();
  background(0);  
  shape(cube);
}