float angle = 0;

void setup() {
  fullScreen(P3D);
  //size(800, 1280, P2D);
  //size(640, 360, P3D);
  //orientation(PORTRAIT);
  //orientation(LANDSCAPE);
}

void draw() {
  background(180);
  lights();
  translate(mouseX, mouseY);
  rotateX(angle);
  rotateY(angle);
  box(width * 0.5);
  angle += 0.01;
}