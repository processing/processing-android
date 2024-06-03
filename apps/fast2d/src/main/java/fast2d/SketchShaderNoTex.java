package fast2d;

import processing.core.PApplet;
import processing.opengl.PShader;

public class SketchShaderNoTex extends PApplet {
  PShader sh;

  public void settings() {
//    fullScreen(P2D);
    fullScreen(P2DX);
  }

  public void setup() {
    orientation(LANDSCAPE);
    sh = loadShader("frag.glsl", "vert.glsl");
    shader(sh);
  }

  public void draw() {
    translate(mouseX, mouseY);
    rect(0, 0, 400, 400);
  }
}
