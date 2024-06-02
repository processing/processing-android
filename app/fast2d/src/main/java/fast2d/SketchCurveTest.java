package fast2d;

import processing.core.PApplet;
import processing.opengl.PGraphics2DX;

public class SketchCurveTest extends PApplet {
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

    //these cause errors in P4D because we haven't implemented them yet
    //so they're disabled in the demo for now
    if (getGraphics() instanceof PGraphics2DX) {
      return;
    }

    noFill();
    stroke(0);
    strokeWeight(4 * displayDensity);
    pushMatrix();
    scale(2);

    beginShape();
    curveVertex(84,  91);
    curveVertex(84,  91);
    curveVertex(68,  19);
    curveVertex(21,  17);
    curveVertex(32, 100);
    curveVertex(32, 100);
    endShape();

    translate(100, 0);

    beginShape();
    vertex(30, 20);
    bezierVertex(80, 0, 80, 75, 30, 75);
    bezierVertex(50, 80, 60, 25, 30, 20);
    endShape();

    translate(100, 0);

    beginShape();
    vertex(20, 20);
    quadraticVertex(80, 20, 50, 50);
    quadraticVertex(20, 80, 80, 80);
    vertex(80, 60);
    endShape();

    popMatrix();
  }
}
