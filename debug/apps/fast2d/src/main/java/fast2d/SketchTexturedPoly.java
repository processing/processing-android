package fast2d;

import processing.core.PApplet;
import processing.core.PImage;

public class SketchTexturedPoly extends PApplet {
  PImage img;

  int join = MITER;
  int cap = SQUARE;

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    img = loadImage("leaves.jpg");
    strokeCap(cap);
    strokeJoin(join);
  }

  public void draw() {
    background(255);

    translate(100, 200);

    //test that textured shapes and tint() work correctly
    float s = 4;
    beginShape();
    texture(img);
    vertex(10*s, 20*s, 0, 0);
    tint(0, 255, 127, 127);
    vertex(80*s, 5*s, 800, 0);
    vertex(95*s, 90*s, 800, 800);
    noTint();
    vertex(40*s, 95*s, 0, 800);
    endShape();

    //test that image() function works correctly
    tint(255, 31);
    rotate(-1);
    image(img, -200, 100);
    rotate(1);
    tint(255);
    image(img, 700, 100, 200, 100);
  }
}
