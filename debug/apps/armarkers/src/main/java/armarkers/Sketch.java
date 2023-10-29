package armarkers;

import java.util.ArrayList;

import processing.ar.*;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PShape;

public class Sketch extends PApplet {
  ARTracker tracker;
  ARAnchor anchor;
  PShape earth;

  public void settings() {
    fullScreen(AR);
  }

  public void setup() {
    fullScreen(AR);

    tracker = new ARTracker(this);

    PImage earthImg = loadImage("earth.jpg");
    tracker.start();
    tracker.addImage("earth", earthImg);

    earth = createShape(SPHERE, 0.25f);
  }

  public void draw() {
    lights();

    if (mousePressed) {
      // Create new anchor at the current touch point
      if (anchor != null) anchor.dispose();
      ARTrackable hit = tracker.get(mouseX, mouseY);
      if (hit != null && hit.isImage() && hit.getName().equals("earth")) {
        anchor = new ARAnchor(hit);
      }
      else anchor = null;
    }

    if (anchor != null) {
      anchor.attach();
      shape(earth);
      anchor.detach();
    }
  }

}
