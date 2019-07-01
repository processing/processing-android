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

public class VRSelector {
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

  public VRSelector(PApplet parent) {
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