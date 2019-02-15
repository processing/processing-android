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

package processing.ar;

import android.view.SurfaceHolder;

import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.core.PSurface;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

public class PGraphicsAR extends PGraphics3D {
  // Convenience reference to the AR surface. It is the same object one gets from PApplet.getSurface().
  protected PSurfaceAR surfar;


  public PGraphicsAR() {
  }


  @Override
  public PSurface createSurface(AppComponent appComponent, SurfaceHolder surfaceHolder, boolean reset) {
    if (reset) pgl.resetFBOLayer();
    surfar = new PSurfaceAR(this, appComponent, surfaceHolder);
    return surfar;
  }


  @Override
  protected PGL createPGL(PGraphicsOpenGL pGraphicsOpenGL) {
    return new PGLES(pGraphicsOpenGL);
  }


  @Override
  public void beginDraw() {
    super.beginDraw();
    updateView();
  }


  @Override
  protected void backgroundImpl() {
    surfar.renderBackground();

    surfar.getAnchors();


    // The helpers (planes, point clouds, should be drawn using Processing primitives, so this could
    // go after updateView() in beginDraw().
    surfar.renderHelpers();
  }


  @Override
  public void camera(float eyeX, float eyeY, float eyeZ,
                     float centerX, float centerY, float centerZ,
                     float upX, float upY, float upZ) {
    PGraphics.showWarning("The camera cannot be set in AR");
  }


  @Override
  public void perspective(float fov, float aspect, float zNear, float zFar) {
    PGraphics.showWarning("Perspective cannot be set in AR");
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
    if (surfar.projmtx != null && surfar.viewmtx != null && surfar.anchorMatrix != null) {
      float[] prj = surfar.projmtx;
      float[] view = surfar.viewmtx;
      float[] anchor = surfar.anchorMatrix;

      // Fist, set all matrices to identity
      resetProjection();
      resetMatrix();

      // Apply the projection matrix
      applyProjection(prj[0], prj[4], prj[8], prj[12],
                      prj[1], prj[5], prj[9], prj[13],
                      prj[2], prj[6], prj[10], prj[14],
                      prj[3], prj[7], prj[11], prj[15]);

      // make modelview = view
      applyMatrix(view[0], view[4], view[8], view[12],
                  view[1], view[5], view[9], view[13],
                  view[2], view[6], view[10], view[14],
                  view[3], view[7], view[11], view[15]);

      // now, modelview = view * anchor
      applyMatrix(anchor[0], anchor[4], anchor[8], anchor[12],
          anchor[1], anchor[5], anchor[9], anchor[13],
          anchor[2], anchor[6], anchor[10], anchor[14],
          anchor[3], anchor[7], anchor[11], anchor[15]);
    }
  }
}
