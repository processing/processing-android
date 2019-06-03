package fast2d;

import processing.core.PApplet;
import processing.core.PImage;
import processing.opengl.PShader;

public class SketchCustomShader extends PApplet {
  PShader edges;
  PImage img;
  boolean enabled = false;

  public void settings() {
//    fullScreen(P2D);
    fullScreen(P2DX);
  }

  public void setup() {
    orientation(LANDSCAPE);
    img = loadImage("leaves.jpg");
    edges = loadShader("edges.glsl");
  }

  public void draw() {
//    if (enabled == true) {
//      shader(edges);
//    }
    image(img, 0, 0, width, height);
  }

  public void mousePressed() {
    enabled = !enabled;
    if (!enabled == true) {
      resetShader();
    }
  }
}
