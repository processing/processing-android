/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.vr;

import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PVector;

public class VRCamera {
  protected PApplet parent;
  protected VRGraphics graphics;
  protected PMatrix3D eyeMat;

  public VRCamera(PApplet parent) {
    if (parent.g instanceof VRGraphics) {
      this.parent = parent;
      this.graphics = (VRGraphics)(parent.g);
    } else {
      System.err.println("The VR camera can only be created when the VR renderer is in use");
    }
  }

  public void sticky() {
    parent.pushMatrix();
    parent.eye();
  }

  public void noSticky() {
    parent.popMatrix();
  }

  public void setPosition(float x, float y, float z) {
    eyeMat = graphics.getEyeMatrix(eyeMat);
    float x0 = eyeMat.m03;
    float y0 = eyeMat.m13;
    float z0 = eyeMat.m23;
    graphics.translate(x0 - x, y0 - y, z0 - z);
  }

  public void setNear(float near) {
    graphics.defCameraNear = near;
  }

  public void setFar(float far) {
    graphics.defCameraFar = far;
  }
}
