float scale;

void setup() {
  fullScreen();
  noStroke();
  scale = displayDensity / 2.65;
  colorMode(HSB, 350, 100, 100);
  textFont(createFont("SansSerif", scale * 60));
}

void draw() {
  background(30, 0, 100);
  fill(30, 0, 20);
  text("Number of touch points: " + touches.length, 20, scale * 100);
  for (int i = 0; i < touches.length; i++) {
    float s = scale * map(touches[i].area, 0, 1, 50, 500);
    println(touches[i].area);
    fill(30, map(touches[i].pressure, 0.6, 1.6, 0, 100), 70, 200);
    ellipse(touches[i].x, touches[i].y, s, s);
  }
}

void touchStarted() {
  println("Touch started");
}

void touchEnded() {
  println("Touch ended");
}

void touchMoved() {
  println("Touch moved");
}