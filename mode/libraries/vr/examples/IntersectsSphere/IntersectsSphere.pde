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

  cam.setPosition(0, 0, 400);
  for (int i = 0; i < 5; ++i) {
    push();
    translate(randomx[i], randomy[i]);
    fill(255, 0, 0);
    if (intersectsSphere(70, 0, 0)) {
      fill(0, 0, 255);
    }
    sphere(70);
//    noLoop();
    pop();
  }
  cam.sticky();
  circle(windowWidth/2, windowHeight/2, 25);
//  image(pg, -windowWidth/2, -windowHeight/2, windowWidth, windowHeight);
  cam.noSticky();
}