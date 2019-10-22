import processing.vr.*;

VRCamera cam;
float x, y;

void setup() {
  fullScreen(VR);
  cameraUp();
  VRCamera cam;
}

void draw() {
  backgroundColor(200, 0, 150);

  cam.setPosition(0, 0, 400);
  fill(255,170,238);
//  plane(400, 400);
  fill(255, 0, 0);
  translate(x, y, 0);
  PVector offset;
  if (intersectsBox(60)) {
    translate(-x, -y, 0);
    fill(0, 0, 255);
    offset = intersectsPlane(0, 0);
    x = offset.x;
    y = offset.y;
  }
  translate(x, y, 0);
  box(60);
}