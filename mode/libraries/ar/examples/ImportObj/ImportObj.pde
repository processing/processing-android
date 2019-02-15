import processing.ar.*;

PShape arObj;

void setup() {
  fullScreen(AUGMENT);

  // Setting Color of the detected plane - BLUE in this case
  PAR.planeColor(#BCD4FF);

  arObj = loadShape("model.obj");
}

void draw() {
  lights();
  background(0);
  if (mousePressed) {
    // Placing the AR object on encountering touch events
    shape(arObj);
  }
}
