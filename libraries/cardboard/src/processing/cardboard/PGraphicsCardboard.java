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

package processing.cardboard;

import com.google.vrtoolkit.cardboard.Eye;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;

import processing.core.PMatrix3D;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

public class PGraphicsCardboard extends PGraphics3D {
  private boolean initialized = false;

  public int eyeType;
  public float[] headView;
  public float[] headRotation;

  private Viewport viewPort;
  private PMatrix3D viewMatrix;
  private PMatrix3D perspectiveMatrix;


  protected PGL createPGL(PGraphicsOpenGL pg) {
    return new PGLES(pg);
  }


  public void beginDraw() {
    super.beginDraw();
    pgl.viewport(viewPort.x, viewPort.y, viewPort.width, viewPort.height);
    camera(0.0f, 0.0f, cameraZ, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
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
    initCardboard();

    // Get the head view and rotation so the user can use them for object selecton and
    // other operations.
    headTransform.getHeadView(headView, 0);
    headTransform.getQuaternion(headRotation, 0);
  }


  protected void eyeTransform(Eye eye) {
    eyeType = eye.getType();
    viewPort = eye.getViewport();

    // Matrices in Processing are row-major, and Cardboard API is column-major
    float[] p = eye.getPerspective(cameraNear, cameraFar);
    perspectiveMatrix.set(p[0], p[4],  p[8], p[12],
                          p[1], p[5],  p[9], p[13],
                          p[2], p[6], p[10], p[14],
                          p[3], p[7], p[11], p[15]);

    float[] v = eye.getEyeView();
    viewMatrix.set(v[0], v[4],  v[8], v[12],
                   v[1], v[5],  v[9], v[13],
                   v[2], v[6], v[10], v[14],
                   v[3], v[7], v[11], v[15]);
  }


  private void initCardboard() {
    if (!initialized) {
      headRotation = new float[4];
      headView = new float[16];

      perspectiveMatrix = new PMatrix3D();
      viewMatrix = new PMatrix3D();

      initialized = true;
    }
  }
}
