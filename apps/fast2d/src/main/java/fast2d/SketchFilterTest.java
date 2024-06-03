package fast2d;

import processing.core.PApplet;
import processing.opengl.PShader;

public class SketchFilterTest extends PApplet {
  PShader blur;

  public void settings() {
//    fullScreen(P2D);
    fullScreen(P2DX);
  }

  public void setup() {
//    orientation(LANDSCAPE);
    blur = loadShader("blur.glsl");
    stroke(255, 0, 0);
    rectMode(CENTER);
    strokeWeight(5 * displayDensity);
  }

  public void draw() {
    filter(blur);
    rect(mouseX, mouseY, 150, 150);
    ellipse(mouseX, mouseY, 100, 100);
  }
}
