/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

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
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

public class PGraphicsVR extends PGraphics3D {
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

  static {
    Y_AXIS_DOWN = false;
  }

  @Override
  protected PGL createPGL(PGraphicsOpenGL pg) {
    return new PGLES(pg);
  }


  public PMatrix3D getEyeMatrix() {
    PMatrix3D mat = new PMatrix3D();
    mat.set(rightX, upX, forwardX, 0,
            rightY, upY, forwardY, 0,
            rightZ, upZ, forwardZ, 0,
                 0,   0,        0, 1);
    return mat;
  }


  public PMatrix3D getEyeMatrix(PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    target.set(rightX, upX, forwardX, 0,
               rightY, upY, forwardY, 0,
               rightZ, upZ, forwardZ, 0,
                    0,   0,        0, 1);
    return target;
  }


  public PMatrix3D getObjectMatrix() {
    PMatrix3D mat = new PMatrix3D();
    mat.set(modelviewInv);
    mat.apply(camera);
    return mat;
  }


  public PMatrix3D getObjectMatrix(PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    target.set(modelviewInv);
    target.apply(camera);
    return target;
  }


  public void setEyeTransform() {
    eyeMatrix = getEyeMatrix(eyeMatrix);

    // Erasing any previous transformation in modelview
    modelview.set(camera);
    modelview.translate(cameraX, cameraY, cameraZ);
    modelview.apply(eyeMatrix);

    // eyeMatrix is orthogonal, so taking the transpose inverts it.
    eyeMatrix.transpose();

    // Applying the inverse of the previous transformations in the opposite order
    // to compute the modelview inverse
    modelviewInv.set(eyeMatrix);
    modelviewInv.translate(-cameraX, -cameraY, -cameraZ);
    modelviewInv.preApply(cameraInv);

    updateProjmodelview();
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
    PGraphics.showWarning("The camera cannnot be modified in VR mode");
  }


  @Override
  public void perspective(float fov, float aspect, float zNear, float zFar) {
    PGraphics.showWarning("Perspective cannnot be modified in VR mode");
  }


  @Override
  protected void defaultCamera() {
    // do nothing
  }


  @Override
  protected void defaultPerspective() {
    // do nothing
  }


  protected void updateView() {
    pgl.viewport(eyeViewport.x, eyeViewport.y, eyeViewport.width, eyeViewport.height);
    setCameraVR(0.0f, 0.0f, defCameraZ, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
    setProjectionVR();
  }


  protected void setCameraVR(float eyeX, float eyeY, float eyeZ,
                             float centerX, float centerY, float centerZ,
                             float upX, float upY, float upZ) {
    cameraX = eyeX;
    cameraY = eyeY;
    cameraZ = eyeZ;

    // Calculating Z vector
    float z0 = eyeX - centerX;
    float z1 = eyeY - centerY;
    float z2 = eyeZ - centerZ;
    eyeDist = PApplet.sqrt(z0 * z0 + z1 * z1 + z2 * z2);
    if (nonZero(eyeDist)) {
      z0 /= eyeDist;
      z1 /= eyeDist;
      z2 /= eyeDist;
    }

    // Calculating Y vector
    float y0 = upX;
    float y1 = upY;
    float y2 = upZ;

    // Computing X vector as Y cross Z
    float x0 =  y1 * z2 - y2 * z1;
    float x1 = -y0 * z2 + y2 * z0;
    float x2 =  y0 * z1 - y1 * z0;

    // Recompute Y = Z cross X
    y0 =  z1 * x2 - z2 * x1;
    y1 = -z0 * x2 + z2 * x0;
    y2 =  z0 * x1 - z1 * x0;

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
    float tx = -eyeX;
    float ty = -eyeY;
    float tz = -eyeZ;
    modelview.translate(tx, ty, tz);

    modelviewInv.set(modelview);
    modelviewInv.invert();

    camera.set(modelview);
    cameraInv.set(modelviewInv);
  }


  protected void setProjectionVR() {
    // Matrices in Processing are row-major, and GVR API is column-major
    projection.set(eyePerspective[0], eyePerspective[4], eyePerspective[8], eyePerspective[12],
                   eyePerspective[1], eyePerspective[5], eyePerspective[9], eyePerspective[13],
                   eyePerspective[2], eyePerspective[6], eyePerspective[10], eyePerspective[14],
                   eyePerspective[3], eyePerspective[7], eyePerspective[11], eyePerspective[15]);
    updateProjmodelview();
  }


  protected void headTransform(HeadTransform ht) {
    initVR();
    headTransform = ht;
    headTransform.getForwardVector(forwardVector, 0);
    headTransform.getRightVector(rightVector, 0);
    headTransform.getUpVector(upVector, 0);
    forwardX = forwardVector[0];
    forwardY = forwardVector[1];
    forwardZ = forwardVector[2];
    rightX = rightVector[0];
    rightY = rightVector[1];
    rightZ = rightVector[2];
    upX = upVector[0];
    upY = upVector[1];
    upZ = upVector[2];
  }


  protected void eyeTransform(Eye e) {
    eye = e;
    eyeType = eye.getType();
    eyeViewport = eye.getViewport();
    eyePerspective = eye.getPerspective(cameraNear, cameraFar);
    eyeView = eye.getEyeView();
  }


  protected void initVR() {
    if (!initialized) {
      forwardVector = new float[3];
      rightVector = new float[3];
      upVector = new float[3];
      initialized = true;
    }
  }


  @Override
  protected void updateGLNormal() {
    if (glNormal == null) {
      glNormal = new float[9];
    }

    // Since Y is inverted in VR, we need to invert the normal calculation so
    // lighting works as it should, even if we provide the normals as before.
    glNormal[0] = -modelviewInv.m00;
    glNormal[1] = -modelviewInv.m01;
    glNormal[2] = -modelviewInv.m02;

    glNormal[3] = -modelviewInv.m10;
    glNormal[4] = -modelviewInv.m11;
    glNormal[5] = -modelviewInv.m12;

    glNormal[6] = -modelviewInv.m20;
    glNormal[7] = -modelviewInv.m21;
    glNormal[8] = -modelviewInv.m22;
  }

}
