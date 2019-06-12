package fast2d;

import processing.core.PApplet;

public class SketchPrimitiveTypes extends PApplet {
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

    //from https://processing.org/reference/beginShape_.html

    stroke(0);
    strokeWeight(4 * displayDensity);
    fill(255, 127, 127);

    pushMatrix();
    resetMatrix();
    scale(2);

    beginShape();
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape(CLOSE);

    translate(100, 0);

    beginShape(POINTS);
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape();

    translate(100, 0);

    beginShape(LINES);
    vertex(30, 40);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape();

    translate(100, 0);

    pushStyle();
    noFill();
    beginShape();
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape();
    popStyle();

    translate(100, 0);

    pushStyle();
    noFill();
    beginShape();
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape(CLOSE);
    popStyle();

    translate(100, 0);

    beginShape(TRIANGLES);
    vertex(30, 75);
    vertex(40, 20);
    vertex(50, 75);
    vertex(60, 20);
    vertex(70, 75);
    vertex(80, 20);
    endShape();

    resetMatrix();
    scale(2);
    translate(0, 100);

    beginShape(TRIANGLE_STRIP);
    vertex(30, 75);
    vertex(40, 20);
    vertex(50, 75);
    vertex(60, 20);
    vertex(70, 75);
    vertex(80, 20);
    vertex(90, 75);
    endShape();

    translate(100, 0);

    beginShape(TRIANGLE_FAN);
    vertex(57.5f, 50);
    vertex(57.5f, 15);
    vertex(92, 50);
    vertex(57.5f, 85);
    vertex(22, 50);
    vertex(57.5f, 15);
    endShape();

    translate(100, 0);

    beginShape(QUADS);
    vertex(30, 20);
    vertex(30, 75);
    vertex(50, 75);
    vertex(50, 20);
    vertex(65, 20);
    vertex(65, 75);
    vertex(85, 75);
    vertex(85, 20);
    endShape();

    translate(100, 0);

    beginShape(QUAD_STRIP);
    vertex(30, 20);
    vertex(30, 75);
    vertex(50, 20);
    vertex(50, 75);
    vertex(65, 20);
    vertex(65, 75);
    vertex(85, 20);
    vertex(85, 75);
    endShape();

    translate(100, 0);

    beginShape();
    vertex(20, 20);
    vertex(40, 20);
    vertex(40, 40);
    vertex(60, 40);
    vertex(60, 60);
    vertex(20, 60);
    endShape(CLOSE);

    //test handling of concave and self-intersecting quads
    //NOTE: JAVA2D currently draws these correctly, but P2D does not
    resetMatrix();
    scale(2);
    translate(0, 200);
    strokeWeight(2 * displayDensity);
    float t = frameCount * 0.01f;

    beginShape(QUADS);
    vertex(50, 10);
    vertex(90, 50);
    vertex(30 + 20*sin(t), 70 + 20*cos(t));
    vertex(30 - 20*sin(t), 70 - 20*cos(t));
    endShape(CLOSE);

    translate(100, 0);

    beginShape(QUAD_STRIP);
    vertex(50, 10);
    vertex(90, 50);
    vertex(30 + 20*sin(t), 70 + 20*cos(t));
    vertex(30 - 20*sin(t), 70 - 20*cos(t));
    endShape(CLOSE);

    popMatrix();
  }
}
