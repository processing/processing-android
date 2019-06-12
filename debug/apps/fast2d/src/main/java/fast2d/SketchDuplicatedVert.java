package fast2d;

import processing.core.PApplet;

public class SketchDuplicatedVert extends PApplet {
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

    //NOTE: yes, this produces the wrong result in P4D
    //see PGraphics4D.shapeVertex() for why
    beginShape();
    vertex(500, 300);
    vertex(600, 400); //dupe
    vertex(700, 300);
    vertex(650, 300);
    vertex(600, 400); //dupe
    vertex(550, 300);
    endShape(CLOSE);
  }
}
