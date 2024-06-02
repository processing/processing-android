void setup()  {
  fullScreen(P2D);
  noStroke();
  background(255);
}

void draw() {
  if (mousePressed) {
    background(255);
  }
  float x = random(width);
  float y = random(height);
  color c = color(random(255), random(255), random(255));
  float r = random(10, 30);
  fill(c);
  ellipse(x, y, r, r);
}