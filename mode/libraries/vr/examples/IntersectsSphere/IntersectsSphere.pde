import processing.vr.*;

float[] randomx = new float[5];
float[] randomy = new float[5];
VRCamera cam;

void setup() {
  fullScreen(VR);
  cameraUp();
  cam = new VRCamera(this);

  for (int i = 0; i < 5; ++i) {
    randomx[i] = random(-500, 500);
    randomy[i] = random(-500, 500);
  }
}

void draw() {
  background(200, 0, 150);

  lights();
  noStroke();
  cam.setPosition(0, 0, 400);
  for (int i = 0; i < 5; ++i) {
    push();
    translate(randomx[i], randomy[i]);
    fill(255, 0, 0);
    if (intersectsSphere(70, 0, 0)) {
      fill(0, 0, 255);
    }
    sphere(70);
    pop();
  }

  cam.sticky();
  strokeWeight(5 * displayDensity);
  stroke(0, 0, 255);
  noFill();
  translate(0, 0, 200);
  circle(0, 0, 50);
  cam.noSticky();
}