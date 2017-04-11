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
import com.google.vr.sdk.base.FieldOfView;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import processing.core.PMatrix3D;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

public class PGraphicsVR extends PGraphics3D {
  private boolean initialized = false;

  // Head properties, independent of eye view
  public int eyeType;
  public float[] headView;
  public float[] headRotation;
  public float[] translationVector;
  public float[] forwardVector;
  public float[] rightVector;
  public float[] upVector;

  // Eye properties
  public FieldOfView eyeFov;
  public Viewport eyeViewPort;
  public float[] eyeView;
  public float[] eyePerspective;

  private PMatrix3D viewMatrix;
  private PMatrix3D perspectiveMatrix;

  @Override
  protected PGL createPGL(PGraphicsOpenGL pg) {
    return new PGLES(pg);
  }


  @Override
  public void beginDraw() {
    super.beginDraw();
    pgl.viewport(eyeViewPort.x, eyeViewPort.y, eyeViewPort.width, eyeViewPort.height);
    // The camera up direction is along -Y, because of the axis inversion
    // in Processing
    camera(0.0f, 0.0f, defCameraZ, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
    setProjection(perspectiveMatrix);
    preApplyMatrix(viewMatrix);
  }


  public void preApplyMatrix(PMatrix3D source) {
    modelview.preApply(source);
    modelviewInv.set(modelview);
    modelviewInv.invert();
    updateProjmodelview();
  }


  protected void headTransform(HeadTransform headTransform) {
    initVR();

    // Get the head view and rotation so the user can use them for object selection and
    // other operations.
    headTransform.getHeadView(headView, 0);
    headTransform.getQuaternion(headRotation, 0);
    headTransform.getTranslation(translationVector, 0);
    headTransform.getForwardVector(forwardVector, 0);
    headTransform.getRightVector(rightVector, 0);
    headTransform.getUpVector(upVector, 0);
  }


  protected void eyeTransform(Eye eye) {
    eyeType = eye.getType();
    eyeViewPort = eye.getViewport();
    eyeFov = eye.getFov();

    // Matrices in Processing are row-major, and GVR API is column-major
    // Also, need to invert Y coordinate, that's why the minus in front of p[5]
    eyePerspective = eye.getPerspective(cameraNear, cameraFar);
    perspectiveMatrix.set(eyePerspective[0],  eyePerspective[4],  eyePerspective[8], eyePerspective[12],
                          eyePerspective[1],  eyePerspective[5],  eyePerspective[9], eyePerspective[13],
                          eyePerspective[2],  eyePerspective[6], eyePerspective[10], eyePerspective[14],
                          eyePerspective[3],  eyePerspective[7], eyePerspective[11], eyePerspective[15]);

    eyeView = eye.getEyeView();
    viewMatrix.set(eyeView[0], eyeView[4],  eyeView[8], eyeView[12],
                   eyeView[1], eyeView[5],  eyeView[9], eyeView[13],
                   eyeView[2], eyeView[6], eyeView[10], eyeView[14],
                   eyeView[3], eyeView[7], eyeView[11], eyeView[15]);
  }


  private void initVR() {
    if (!initialized) {
      headRotation = new float[4];
      headView = new float[16];
      translationVector = new float[3];
      forwardVector = new float[3];
      rightVector = new float[3];
      upVector = new float[3];

      perspectiveMatrix = new PMatrix3D();
      viewMatrix = new PMatrix3D();

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
