package fast2d;

import processing.core.PApplet;
import processing.core.PFont;

public class SketchDisplayText extends PApplet {
  PFont font;

  public void settings() {
    fullScreen(P2DX);
  }

  public void setup() {
    font = createFont("SansSerif", displayDensity * 72);
  }

  public void draw() {
    background(255);

    textFont(font);
    text("Now is the time for all good men to come to the aid of their country.\n"
        + "If they do not the quick brown fox may never jump over the lazy sleeping dog again.\n"
        + "He may, however, take up knitting as a suitable hobby for all retired quick brown foxes.\n"
        + "This is test #1 of 9,876,543,210.\n"
        + "Collect them all!", 0, 100);
  }
}
