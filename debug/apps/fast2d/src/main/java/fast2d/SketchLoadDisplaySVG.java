package fast2d;

import processing.core.PApplet;
import processing.core.PShape;

public class SketchLoadDisplaySVG extends PApplet {
  PShape bot;

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    bot = loadShape("bot1.svg");
  }

  public void draw() {
    background(102);
    shape(bot, 110, 90, 100, 100);  // Draw at coordinate (110, 90) at size 100 x 100
    shape(bot, 280, 40);            // Draw at coordinate (280, 40) at the default size
  }
}
