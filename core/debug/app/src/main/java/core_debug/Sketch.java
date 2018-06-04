package core_debug;

import processing.core.PApplet;
import processing.core.PImage;

public class Sketch extends PApplet {

  PImage maple;

  public void settings() {
    fullScreen();
  }

  public void setup() {
    maple = loadImage("maple.png");
  }

  public void draw() {
    background(9);
    image(maple, mouseX, mouseY);
  }
}
