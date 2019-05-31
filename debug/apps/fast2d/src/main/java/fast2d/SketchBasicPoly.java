package fast2d;

import processing.core.PApplet;
import processing.core.PShape;

public class SketchBasicPoly extends PApplet {
  float weight = 1;

  int join = MITER;
  int cap = SQUARE;
  int mode = OPEN;

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    strokeCap(cap);
    strokeJoin(join);
  }

  public void draw() {
    background(255);

    fill(255, 0, 63, 127);
    stroke(255, 0, 255, 127);
    strokeWeight(12 * displayDensity);
    strokeJoin(ROUND);
    noStroke();

    strokeWeight(6 * weight * displayDensity);
    stroke(0, 127, 95, 191);
    beginShape();
    vertex(100, 200);
    vertex(200, 100);
    vertex(300, 200);
    vertex(400, 100);
    vertex(350, 200);
    vertex(450, 100);

    vertex(300, 300);
    vertex(mouseX, mouseY);
    vertex(600, 200);

    vertex(550, 100);
    vertex(550, 400);
    vertex(750, 400);
    vertex(750, 600);
    vertex(100, 600);
    endShape(mode);
  }
}