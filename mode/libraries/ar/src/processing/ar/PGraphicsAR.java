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
import processing.core.PApplet;
import processing.core.PMatrix3D;
import processing.core.PSurface;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;

import static processing.ar.PSurfaceAR.mainPose;
import static processing.ar.PSurfaceAR.session;

public class PGraphicsAR extends PGraphics3D {

  public PGraphicsAR() {
  }

  @Override
  public PSurface createSurface(AppComponent appComponent, SurfaceHolder surfaceHolder, boolean b) {
    if (b) pgl.resetFBOLayer();
    return new PSurfaceAR(this, appComponent, surfaceHolder);
  }

  @Override
  protected PGL createPGL(PGraphicsOpenGL pGraphicsOpenGL) {
    return new PGLES(pGraphicsOpenGL);
  }

  @Override
  public void beginDraw() {
    super.beginDraw();
    updateInferences();
  }

  @Override
  protected void backgroundImpl() {
    if (session != null) {
      PSurfaceAR.performRendering();
    }
  }

  @Override
  public void surfaceChanged() {
  }

  public void updateInferences() {
    setAR();
  }

  protected void setAR() {
    if (PSurfaceAR.projmtx != null && PSurfaceAR.viewmtx != null && PSurfaceAR.anchorMatrix != null) {
      float[] prj = PSurfaceAR.projmtx;
      float[] view = PSurfaceAR.viewmtx;
      float[] anchor = PSurfaceAR.anchorMatrix;

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
