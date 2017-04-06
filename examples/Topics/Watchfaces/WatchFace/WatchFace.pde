float angle = 0;

void setup() {
  fullScreen(P2D);
  frameRate(15);
}

void draw() {
  translate(0, +wearInsets().bottom/2);

  if (wearAmbient()) {
    background(0);
    stroke(255);
    noFill();
  } else {
    background(157);
    stroke(0);
    fill(255);
  }

  line(0, 0, width, height);
  line(width, 0, 0, height);

  translate(width/2, height/2);
  rotate(angle);
  rect(-50, -50, 100, 100);
  angle += 0.01;
}