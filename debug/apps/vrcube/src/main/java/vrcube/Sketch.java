package vrcube;

import processing.core.PApplet;
import processing.vr.*; 

public class Sketch extends PApplet {

  public void settings() {
    fullScreen(STEREO);
  }

  public void setup() { }

  public void draw() {
    background(157);
    lights();
    translate(width/2, height/2);
    rotateX(frameCount * 0.01f);
    rotateY(frameCount * 0.01f);  
    box(350);
  }
}
