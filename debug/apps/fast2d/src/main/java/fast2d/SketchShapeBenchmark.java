package fast2d;

import processing.core.PApplet;
import processing.opengl.PGraphics2DX;

public class SketchShapeBenchmark extends PApplet {
  int join = MITER;
  int cap = SQUARE;

  boolean premultiply = true;

  float dev = 10; //deviation

  //change these parameters to benchmark various things
  int unit = 10;
  //line, triangle, rect, ellipse, point
  int[] amount = { 20, 15, 10,  5, 40 };

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    strokeCap(cap);
    strokeJoin(join);
    PGraphics2DX.premultiplyMatrices = premultiply;

    textFont(createFont("SansSerif", 15 * displayDensity));
  }

  public void draw() {
    background(255);

    strokeWeight(2 * displayDensity);
    stroke(0);
    fill(200);

    for (int i = 0; i < amount[0]*unit; ++i) {
      float x = random(width);
      float y = random(height);
      line(x, y, x + random(-dev, dev), y + random(-dev, dev));
    }

    for (int i = 0; i < amount[1]*unit; ++i) {
      float x = random(width);
      float y = random(height);
      triangle(x, y,
          x + random(-dev*2, dev*2), y + random(-dev*2, dev*2),
          x + random(-dev*2, dev*2), y + random(-dev*2, dev*2));
    }

    for (int i = 0; i < amount[2]*unit; ++i) {
      rect(random(width), random(height), random(dev), random(dev));
    }

    for (int i = 0; i < amount[3]*unit; ++i) {
      ellipse(random(width), random(height), random(dev*2), random(dev*2));
    }

    for (int i = 0; i < amount[4]*unit; ++i) {
      point(random(width), random(height));
    }

    //large ellipse to test smoothness of outline
    ellipse(width/2, height/2, width/2, height/4);

    fill(255, 0, 0);
    text((int) frameRate + " fps", 30, 30);
  }
}
