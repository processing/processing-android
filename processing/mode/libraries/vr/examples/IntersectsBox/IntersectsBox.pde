import processing.vr.*;

VRCamera cam;
float rotSpeed = 0.3; 
float rotAngle = 0;

void setup() {
  fullScreen(VR);
  cameraUp();
  cam = new VRCamera(this);
}

void draw() {
  background(200, 0, 150);

  cam.setPosition(0, 0, 200);
  push();
  rotateZ(radians(rotAngle));
  translate(100, 0, 0);
  fill(255, 0, 0);
  if (intersectsBox(50, 0, 0)) {
    rotAngle += rotSpeed;
    fill(0, 0, 255);
  }
  box(50);
  pop();
}