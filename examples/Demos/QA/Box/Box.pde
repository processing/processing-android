float angle = 0;

void setup() {
  fullScreen(P3D);
}

void draw() {
  background(180);
  lights();
  translate(mouseX, mouseY);
  rotateX(angle);
  rotateY(angle);
  box(100);
  angle += 0.01;
}