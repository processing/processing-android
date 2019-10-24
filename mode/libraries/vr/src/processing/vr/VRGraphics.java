/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-19 The Processing Foundation

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

import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.FieldOfView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
import processing.core.PVector;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

public class VRGraphics extends PGraphics3D {
  static public final int LEFT      = Eye.Type.LEFT;
  static public final int RIGHT     = Eye.Type.RIGHT;
  static public final int MONOCULAR = Eye.Type.MONOCULAR;

  private boolean initialized = false;

  public HeadTransform headTransform;
  public Eye eye;
  public int eyeType;
  public float forwardX, forwardY, forwardZ;
  public float rightX, rightY, rightZ;
  public float upX, upY, upZ;

  private float[] forwardVector;
  private float[] rightVector;
  private float[] upVector;
  private Viewport eyeViewport;
  private float[] eyeView;
  private float[] eyePerspective;

  private PMatrix3D eyeMatrix;
  private PMatrix3D objMatrix;

  // Variables used in ray casting
  private PVector[] ray;
  private PVector hit = new PVector();
  private PVector screen = new PVector();
  private PVector objCenter = new PVector();
  private PVector objToOrig = new PVector();
  private PVector origInObjCoord = new PVector();
  private PVector hitInObjCoord = new PVector();
  private PVector dirInObjCoord = new PVector();

  @Override
  protected PGL createPGL(PGraphicsOpenGL pg) {
    return new PGLES(pg);
  }


  @Override
  public PMatrix3D getEyeMatrix() {
    return getEyeMatrix(null);
  }


  @Override
  public PMatrix3D getEyeMatrix(PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    float sign = cameraUp ? +1 : -1;
    target.set(rightX, sign * upX, forwardX, cameraX,
               rightY, sign * upY, forwardY, cameraY,
               rightZ, sign * upZ, forwardZ, cameraZ,
              0,          0,  0, 1);
    return target;
  }


  @Override
  public PMatrix3D getObjectMatrix() {
    PMatrix3D mat = new PMatrix3D();
    mat.set(modelviewInv);
    mat.apply(camera);
    return mat;
  }


  @Override
  public PMatrix3D getObjectMatrix(PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    target.set(modelviewInv);
    target.apply(camera);
    return target;
  }


  @Override
  public void eye() {
    eyeMatrix = getEyeMatrix(eyeMatrix);

    // Erasing any previous transformation in modelview
    modelview.set(camera);
    modelview.apply(eyeMatrix);

    // The 3x3 block of eyeMatrix is orthogonal, so taking the transpose inverts it...
    eyeMatrix.transpose();
    // ...and then invert the translation separately:
    eyeMatrix.m03 = -cameraX;
    eyeMatrix.m13 = -cameraY;
    eyeMatrix.m23 = -cameraZ;
    eyeMatrix.m30 = 0;
    eyeMatrix.m31 = 0;
    eyeMatrix.m32 = 0;

    // Applying the inverse of the previous transformations in the opposite order
    // to compute the modelview inverse
    modelviewInv.set(eyeMatrix);
    modelviewInv.preApply(cameraInv);

    updateProjmodelview();
  }


  @Override
  public PVector[] getRayFromScreen(float screenX, float screenY, PVector[] ray) {
    if (ray == null || ray.length < 2) {
      ray = new PVector[2];
      ray[0] = new PVector();
      ray[1] = new PVector();
    }
    getRayFromScreen(screenX, screenY, ray[0], ray[1]);
    return ray;
  }


  @Override
  public void getRayFromScreen(float screenX, float screenY, PVector origin, PVector direction) {
    eyeMatrix = getEyeMatrix(eyeMatrix);

    // Transforming screen coordinates to world coordinates
    screen.x = screenX;
    screen.y = screenY;
    screen.z = 0;
    eyeMatrix.mult(screen, origin);

    // The direction of the ray is simply extracted from the third column of the eye matrix (the
    // forward vector).
    direction.set(eyeMatrix.m02, eyeMatrix.m12, eyeMatrix.m22);
  }


  @Override
  public boolean intersectsSphere(float r, float screenX, float screenY) {
    ray = getRayFromScreen(screenX, screenY, ray);
    return intersectsSphere(r, ray[0], ray[1]);
  }


  @Override
  public boolean intersectsSphere(float r, PVector origin, PVector direction) {
    // Get the center of the sphere in world coordinates
    objCenter.x = modelview.m03;
    objCenter.y = modelview.m13;
    objCenter.z = modelview.m23;

    PVector.sub(origin, objCenter, objToOrig);
    float d = objToOrig.mag();

    // The eye is inside the sphere
    if (d <= r) return true;

    // Check if sphere is in front of ray
    if (PVector.dot(objToOrig, direction) > 0) return false;

    // Check intersection of ray with sphere
    float b = 2 * PVector.dot(direction, objToOrig);
    float c = d * d - r * r;
    float det = b * b - 4 * c;
    return det >= 0;
  }


  @Override
  public boolean intersectsBox(float size, float screenX, float screenY) {
    ray = getRayFromScreen(screenX, screenY, ray);
    return intersectsBox(size, size, size, ray[0], ray[1]);
  }


  @Override
  public boolean intersectsBox(float w, float h, float d, float screenX, float screenY) {
    ray = getRayFromScreen(screenX, screenY, ray);
    return intersectsBox(w, h, d, ray[0], ray[1]);
  }


  @Override
  public boolean intersectsBox(float size, PVector origin, PVector direction) {
    return intersectsBox(size, size, size, origin, direction);
  }


  @Override
  public boolean intersectsBox(float w, float h, float d, PVector origin, PVector direction) {
    objMatrix = getObjectMatrix(objMatrix);
    objMatrix.mult(origin, origInObjCoord);
    PVector.add(origin, direction, hit);
    objMatrix.mult(hit, hitInObjCoord);

    PVector.sub(hitInObjCoord, origInObjCoord, dirInObjCoord);
    return lineIntersectsAABB(origInObjCoord, dirInObjCoord, w, h, d);
  }


  // Line intersection with an axis-aligned bounding box (AABB), calculated using the algorithm
  // from Amy William et al: http:// dl.acm.org/citation.cfm?id=1198748
  protected boolean lineIntersectsAABB(PVector orig, PVector dir, float w, float h, float d) {
    float minx = -w/2;
    float miny = -h/2;
    float minz = -d/2;

    float maxx = +w/2;
    float maxy = +h/2;
    float maxz = +d/2;

    float idx = 1/dir.x;
    float idy = 1/dir.y;
    float idz = 1/dir.z;

    boolean sdx = idx < 0;
    boolean sdy = idy < 0;
    boolean sdz = idz < 0;

    float bbx = sdx ? maxx : minx;
    float txmin = (bbx - orig.x) * idx;
    bbx = sdx ? minx : maxx;
    float txmax = (bbx - orig.x) * idx;
    float bby = sdy ? maxy : miny;
    float tymin = (bby - orig.y) * idy;
    bby = sdy ? miny : maxy;
    float tymax = (bby - orig.y) * idy;

    if ((txmin > tymax) || (tymin > txmax)) {
      return false;
    }
    if (tymin > txmin) {
      txmin = tymin;
    }
    if (tymax < txmax) {
      txmax = tymax;
    }

    float bbz = sdz ? maxz : minz;
    float tzmin = (bbz - orig.z) * idz;
    bbz = sdz ? minz : maxz;
    float tzmax = (bbz - orig.z) * idz;

    if ((txmin > tzmax) || (tzmin > txmax)) {
      return false;
    }
    if (tzmin > txmin) {
      txmin = tzmin;
    }
    if (tzmax < txmax) {
      txmax = tzmax;
    }

    if ((txmin < defCameraFar) && (txmax > 0)) {
      // The intersection coordinates:
      // x = orig.x + txmin * dir.x;
      // y = orig.y + txmin * dir.y;
      // z = orig.z + txmin * dir.z;
      return true;
    }

    return false;
  }




  @Override
  public PVector intersectsPlane(float screenX, float screenY) {
    ray = getRayFromScreen(screenX, screenY, ray);
    return intersectsPlane(ray[0], ray[1]);
  }


  @Override
  public PVector intersectsPlane(PVector origin, PVector direction) {
    showMissingWarning("intersectsPlane");
    return null;
  }


  @Override
  public void beginDraw() {
    super.beginDraw();
    updateView();
  }


  @Override
  public void camera(float eyeX, float eyeY, float eyeZ,
                     float centerX, float centerY, float centerZ,
                     float upX, float upY, float upZ) {
    PGraphics.showWarning("The camera cannot be set in VR");
  }


  @Override
  public void perspective(float fov, float aspect, float zNear, float zFar) {
    PGraphics.showWarning("Perspective cannot be set in VR");
  }


  @Override
  protected void defaultCamera() {
    // do nothing
  }


  @Override
  protected void defaultPerspective() {
    // do nothing
  }


  @Override
  protected void saveState() {
  }


  @Override
  protected void restoreState() {
  }


  @Override
  protected void restoreSurface() {
  }


  protected void updateView() {
    setVRViewport();
    setVRCamera();
    setVRProjection();
  }


  protected void eyeTransform(Eye e) {
    eye = e;
    eyeType = eye.getType();
    eyeViewport = eye.getViewport();
    eyePerspective = eye.getPerspective(defCameraNear, defCameraFar);
    eyeView = eye.getEyeView();

    // Adjust the camera Z position to it fits the (width,height) rect at Z = 0, given
    // the fov settings.
    FieldOfView fov = eye.getFov();
    defCameraFOV = fov.getTop()* DEG_TO_RAD;
    defCameraZ = (float) (height / (2 * Math.tan(defCameraFOV)));
    cameraAspect = (float)width / height;
    if (cameraUp) {
      defCameraX = 0;
      defCameraY = 0;
    } else {
      defCameraX = +width / 2.0f;
      defCameraY = +height / 2.0f;
    }
  }


  protected void headTransform(HeadTransform ht) {
    initVR();

    headTransform = ht;

    // Forward, right, and up vectors are given in the original system with Y
    // pointing up. Need to invert y coords in the non-gl case:
    float yf = cameraUp ? +1 : -1;

    headTransform.getForwardVector(forwardVector, 0);
    headTransform.getRightVector(rightVector, 0);
    headTransform.getUpVector(upVector, 0);

    forwardX = forwardVector[0];
    forwardY = yf * forwardVector[1];
    forwardZ = forwardVector[2];

    rightX = rightVector[0];
    rightY = yf * rightVector[1];
    rightZ = rightVector[2];

    upX = upVector[0];
    upY = yf * upVector[1];
    upZ = upVector[2];
  }


  protected void initVR() {
    if (!initialized) {
      forwardVector = new float[3];
      rightVector = new float[3];
      upVector = new float[3];
      initialized = true;
    }
  }


  protected void setVRViewport() {
    pgl.viewport(eyeViewport.x, eyeViewport.y, eyeViewport.width, eyeViewport.height);
  }


  protected void setVRCamera() {
    cameraX = defCameraX;
    cameraY = defCameraY;
    cameraZ = defCameraZ;

    // Calculating Z vector
    float z0 = 0;
    float z1 = 0;
    float z2 = defCameraZ;
    eyeDist = PApplet.abs(z2);
    if (nonZero(eyeDist)) {
      z0 /= eyeDist;
      z1 /= eyeDist;
      z2 /= eyeDist;
    }

    // Calculating Y vector
    float y0 = 0;
    float y1 = cameraUp ? + 1: -1;
    float y2 = 0;

    // Computing X vector as Y cross Z
    float x0 =  y1 * z2 - y2 * z1;
    float x1 = -y0 * z2 + y2 * z0;
    float x2 =  y0 * z1 - y1 * z0;
    if (!cameraUp) {
      // Inverting X axis
      x0 *= -1;
      x1 *= -1;
      x2 *= -1;
    }

    // Cross product gives area of parallelogram, which is < 1.0 for
    // non-perpendicular unit-length vectors; so normalize x, y here:
    float xmag = PApplet.sqrt(x0 * x0 + x1 * x1 + x2 * x2);
    if (nonZero(xmag)) {
      x0 /= xmag;
      x1 /= xmag;
      x2 /= xmag;
    }

    float ymag = PApplet.sqrt(y0 * y0 + y1 * y1 + y2 * y2);
    if (nonZero(ymag)) {
      y0 /= ymag;
      y1 /= ymag;
      y2 /= ymag;
    }

    // Pre-apply the eye view matrix:
    // https://developers.google.com/vr/android/reference/com/google/vr/sdk/base/Eye.html#getEyeView()
    modelview.set(eyeView[0], eyeView[4], eyeView[8],  eyeView[12],
                  eyeView[1], eyeView[5], eyeView[9],  eyeView[13],
                  eyeView[2], eyeView[6], eyeView[10], eyeView[14],
                  eyeView[3], eyeView[7], eyeView[11], eyeView[15]);
    modelview.apply(x0, x1, x2, 0,
                    y0, y1, y2, 0,
                    z0, z1, z2, 0,
                     0,  0,  0, 1);
    float tx = -defCameraX;
    float ty = -defCameraY;
    float tz = -defCameraZ;
    modelview.translate(tx, ty, tz);

    modelviewInv.set(modelview);
    modelviewInv.invert();

    camera.set(modelview);
    cameraInv.set(modelviewInv);
  }


  protected void setVRProjection() {
    // Matrices in Processing are row-major, and GVR API is column-major
    projection.set(eyePerspective[0], eyePerspective[4], eyePerspective[8], eyePerspective[12],
                   eyePerspective[1], eyePerspective[5], eyePerspective[9], eyePerspective[13],
                   eyePerspective[2], eyePerspective[6], eyePerspective[10], eyePerspective[14],
                   eyePerspective[3], eyePerspective[7], eyePerspective[11], eyePerspective[15]);
    updateProjmodelview();
  }
}
