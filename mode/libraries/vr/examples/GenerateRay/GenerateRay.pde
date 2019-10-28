import processing.vr.*;

PVector origin, direction;
float[] randomx = new float[5];
float[] randomy = new float[5];
float[] randomz = new float[5];
VRCamera cam;

void setup() {
  fullScreen(VR);
  cameraUp();
  cam = new VRCamera(this);

  for (int i = 0; i < 5; ++i) {
     randomx[i] = random(-500, 500);
     randomy[i] = random(-500, 500);
    randomz[i] = random(-100, 100);
  }
  noStroke();

  origin = new PVector(randomx[0], randomy[0], randomz[0]);
  direction = new PVector();
}

void draw() {
  background(200, 0, 150);
  lights();

  cam.setPosition(0, 0, 400);
  push();
  translate(randomx[0], randomy[0], randomz[0]);
  fill(0, 255, 0);
  sphere(30);

  int r = floor(random(1, 5));
  float rx = randomx[r] - randomx[0];
  float ry = randomy[r] - randomy[0];
  float rz = randomz[r] - randomz[0];
  stroke(0);
  strokeWeight(1 * displayDensity);
  line(0, 0, 0, rx, ry, rz);
  noStroke();

  direction.set(rx, ry, rz);
  direction.normalize();

  pop();

  for (int i = 1; i < 5; ++i) {
    push();
    translate(randomx[i], randomy[i], randomz[i]);
    fill(255, 0, 0);
    if (intersectsSphere(70, origin, direction)) {
      fill(0, 0, 255);
    }
    sphere(70);
    pop();
  }
}