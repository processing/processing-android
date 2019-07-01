package vrcube;

import processing.core.PApplet;
import processing.vr.*;

public class Sketch extends PApplet {
  float boxSize = 140;
  VRCamera vrcam;
  VRSelector vrsel;

  public void settings() {
    fullScreen(VR);
  }

  public void setup() {
    vrcam = new VRCamera(this);
    vrsel = new VRSelector(this);
  }

  public void draw() {
    vrsel.update();
    background(120);
    translate(width/2, height/2);
    lights();
    drawGrid();
    drawAim();
  }

  void drawGrid() {
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 4; j++) {
        float x = map(i, 0, 3, -350, +350);
        float y = map(j, 0, 3, -350, +350);
        pushMatrix();
        translate(x, y);
        rotateY(millis()/1000.0f);
        if (vrsel.hit(boxSize)) {
          strokeWeight(5);
          stroke(0xFF2FB1EA);
          if (mousePressed) {
            fill(0xFF2FB1EA);
          } else {
            fill(0xFFE3993E);
          }
        } else {
          noStroke();
          fill(0xFFE3993E);
        }
        box(boxSize);
        popMatrix();
      }
    }
  }

  void drawAim() {
    vrcam.begin();
    stroke(47, 177, 234, 150);
    strokeWeight(50);
    point(0, 0, 100);
    vrcam.end();
  }



/*
  public void settings() {
    fullScreen(STEREO);
  }

  public void setup() { }

  public void draw() {
    background(157);
    lights();
    translate(width / 2, height / 2);
    rotateX(frameCount * 0.01f);
    rotateY(frameCount * 0.01f);
    box(350);
  }
*/
}
