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
  backgroundColor(200, 0, 150);

  cam.setPosition(0, 0, 400);
  push();
  translate(randomx[0], randomy[0]);
  fill(0, 255, 0);
  sphere(30);
  rotateZ(millis() / 1000);
  line(0, 0, 0, 1000, 1000, 0);
  PVector[] ray = generateRay(0, 0, 0, 1000, 1000, 0);

  pop();
  for (int i = 1; i < 5; ++i) {
    push();
    translate(randomx[i], randomy[i]);
    fill(255, 0, 0);
    if (intersectsSphere(70, ray)) {
      fill(0, 0, 255);
    }
    sphere(70);
    pop();
  }
}