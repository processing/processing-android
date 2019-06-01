package fast2d;

import processing.core.PApplet;
import processing.core.PVector;

public class SketchArcTest extends PApplet {
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

    strokeWeight(4 * displayDensity);
    stroke(127, 0, 0);
    fill(255, 255, 255);

    //testing the behavior of floating point % operator (for dealing with angles)
    float py = 0;
    for (int i = 0; i < width; ++i) {
      float x = (i - width/2) * 0.1f;
      float y = height/2 - (x % PI) * 10;
      line(i, y, i - 1, py);
      py = y;
    }

    //testing the behavior of P2D arc() at various angles
    //NOTE: arcs with negative angle aren't drawn
    arc(100, 100, 100, 100, -1, new PVector(mouseX, mouseY).sub(100, 100).heading());

    //test for whether LINES primitive type has self-overlap
    //NOTE: it does in JAVA2D, but not in P2D
    stroke(0, 127, 127, 127);
    beginShape(LINES);
    vertex(0, 0);
    vertex(width, height + 100);
    vertex(width, 0);
    vertex(0, height + 100);
    endShape();
  }
}
