package fast2d;

import processing.core.PApplet;

public class SketchUserDefinedContours extends PApplet {

  int join = MITER;
  int cap = SQUARE;

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    strokeCap(cap);
    strokeJoin(join);
  }

  public void draw() {
    background(255);

    //from https://processing.org/reference/beginContour_.html

    fill(127, 255, 127);
    stroke(255, 0, 0);
    beginShape();
    // Exterior part of shape, clockwise winding
    vertex(-40, -40);
    vertex(40, -40);
    vertex(40, 40);
    vertex(-40, 40);
    // Interior part of shape, counter-clockwise winding
    beginContour();
    vertex(-20, -20);
    vertex(-20, 20);
    vertex(20, 20);
    vertex(20, -20);
    endContour();
    endShape(CLOSE);
  }
}
