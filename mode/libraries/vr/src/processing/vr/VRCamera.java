package processing.vr;

import processing.core.PApplet;

public class VRCamera {
  protected PApplet parent;

  public VRCamera(PApplet parent) {
    this.parent = parent;
  }

  public void begin() {
    parent.pushMatrix();
    parent.eye();
  }

  public void end() {
    parent.popMatrix();
  }
}
