package wallpaper;

import processing.core.PApplet;

public class Sketch extends PApplet {

  float currH, currB;
  float nextH, nextB;
  float easing = 0.001f;
  int lastChange = 0;

  public void settings() {
    fullScreen();
  }

  public void setup() {
    colorMode(HSB, 100);
    currH = nextH = 100;
    currB = nextB = 100;
  }

  public void draw() {
    background(currH, currB, 100);
    updateCurrColor();
    if (5000 < millis() - lastChange) {
      pickNextColor();
      lastChange = millis();
    }
  }

  public void pickNextColor() {
    nextH = random(100);
    nextB = random(100);
  }

  public void updateCurrColor() {
    // Easing between current and next colors
    currH += easing * (nextH - currH);
    currB += easing * (nextB - currB);
  }
}
