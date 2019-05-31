package fast2d;

import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PVector;

public class SketchMousePoly extends PApplet {
  float weight = 1;

  //data for demo 2
  int[] c = new int[4096];
  ArrayList<PVector> points = new ArrayList<PVector>();

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    //setup for demo 2
    for (int i = 0; i < c.length; ++i) {
      c[i] = color(random(255), random(255), random(255));
    }
  }

  public void draw() {
    background(255);

    fill(255, 0, 63, 127);
    noStroke();

    //NOTE: we draw each vertex with a random fill color to test how it behaves.
    //in P2D, the colors are interpolated across the triangles output by the GLU tessellator.
    //in JAVA2D, when endShape() is called, the currently active color is used for all vertices.
    //for now P4D follows the behavior of P2D, but switching to JAVA2D's behavior
    //would allow us to simplify our implementation a bit
    beginShape();
    for (int i = 0; i < points.size(); ++i) {
      fill(c[i]);
      vertex(points.get(i).x, points.get(i).y);
    }
    endShape();
  }

  public void mousePressed() {
    points.add(new PVector(mouseX, mouseY));
  }

  public void mouseDragged() {
    points.get(points.size() - 1).x = mouseX;
    points.get(points.size() - 1).y = mouseY;
  }
}
