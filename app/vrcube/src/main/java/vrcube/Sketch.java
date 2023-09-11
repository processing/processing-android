package vrcube;

import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PVector;
import processing.vr.*;

public class Sketch extends PApplet {
  float boxSize = 140;
  VRCamera vrcam;
  Selector vrsel;

  public void settings() {
    fullScreen(VR);
  }

  public void setup() {
    vrcam = new VRCamera(this);
    vrsel = new Selector(this);
//    vrcam.setNear(1000);
//    vrcam.setFar(1100);
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
    vrcam.sticky();
    stroke(47, 177, 234, 150);
    strokeWeight(50);
    point(0, 0, 100);
    vrcam.noSticky();
  }

  class Selector {
    protected PApplet parent;

    protected PVector dir = new PVector();
    protected PVector cam = new PVector();

    protected PMatrix3D eyeMat = new PMatrix3D();
    protected PMatrix3D objMat = new PMatrix3D();

    protected PVector front = new PVector();
    protected PVector objCam = new PVector();
    protected PVector objFront = new PVector();
    protected PVector objDir = new PVector();

    protected PVector hit = new PVector();

    public Selector(PApplet parent) {
      this.parent = parent;
    }

    public void update() {
      parent.getEyeMatrix(eyeMat);
      cam.set(eyeMat.m03, eyeMat.m13, eyeMat.m23);
      dir.set(eyeMat.m02, eyeMat.m12, eyeMat.m22);
      PVector.add(cam, dir, front);
    }

    public boolean hit(PMatrix3D mat, float boxSize) {
      objMat.set(mat);
      return hitImpl(boxSize);
    }

    public boolean hit(float boxSize) {
      parent.getObjectMatrix(objMat);
      return hitImpl(boxSize);
    }

    protected boolean hitImpl(float boxSize) {
      objMat.mult(cam, objCam);
      objMat.mult(front, objFront);
      PVector.sub(objFront, objCam, objDir);
      PVector boxMin = new PVector(-boxSize/2, -boxSize/2, -boxSize/2);
      PVector boxMax = new PVector(+boxSize/2, +boxSize/2, +boxSize/2);
      return intersectsLine(objCam, objDir, boxMin, boxMax, 0, 1000, hit);
    }

    protected boolean intersectsLine(PVector orig, PVector dir,
                                     PVector minPos, PVector maxPos, float minDist, float maxDist, PVector hit) {
      PVector bbox;
      PVector invDir = new PVector(1/dir.x, 1/dir.y, 1/dir.z);

      boolean signDirX = invDir.x < 0;
      boolean signDirY = invDir.y < 0;
      boolean signDirZ = invDir.z < 0;

      bbox = signDirX ? maxPos : minPos;
      float txmin = (bbox.x - orig.x) * invDir.x;
      bbox = signDirX ? minPos : maxPos;
      float txmax = (bbox.x - orig.x) * invDir.x;
      bbox = signDirY ? maxPos : minPos;
      float tymin = (bbox.y - orig.y) * invDir.y;
      bbox = signDirY ? minPos : maxPos;
      float tymax = (bbox.y - orig.y) * invDir.y;

      if ((txmin > tymax) || (tymin > txmax)) {
        return false;
      }
      if (tymin > txmin) {
        txmin = tymin;
      }
      if (tymax < txmax) {
        txmax = tymax;
      }

      bbox = signDirZ ? maxPos : minPos;
      float tzmin = (bbox.z - orig.z) * invDir.z;
      bbox = signDirZ ? minPos : maxPos;
      float tzmax = (bbox.z - orig.z) * invDir.z;

      if ((txmin > tzmax) || (tzmin > txmax)) {
        return false;
      }
      if (tzmin > txmin) {
        txmin = tzmin;
      }
      if (tzmax < txmax) {
        txmax = tzmax;
      }
      if ((txmin < maxDist) && (txmax > minDist)) {
        hit.x = orig.x + txmin * dir.x;
        hit.y = orig.y + txmin * dir.y;
        hit.z = orig.z + txmin * dir.z;
        return true;
      }
      return false;
    }
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
