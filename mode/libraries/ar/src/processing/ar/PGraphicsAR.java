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

import android.opengl.GLES11Ext;
import android.view.SurfaceHolder;

import com.google.ar.core.Anchor;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;

import java.nio.FloatBuffer;
import java.util.Collection;

import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
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

    background(0);
    surfar.renderBackground();

//    flush();
//    pgl.clearBackground(backgroundR, backgroundG, backgroundB, backgroundA,
//        !hints[DISABLE_DEPTH_MASK], true);
  }


//  @Override
//  protected void backgroundImpl() {
//    surfar.renderBackground();
//  }

//  protected void renderBackground() {
//    backgroundImpl();
//    surfar.renderBackground();
//  }


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
    if (surfar.projmtx != null && surfar.viewmtx != null /*&& surfar.anchorMatrix != null*/) {
      float[] prj = surfar.projmtx;
      float[] view = surfar.viewmtx;
//      float[] anchor = surfar.anchorMatrix;

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

//      // now, modelview = view * anchor
//      applyMatrix(anchor[0], anchor[4], anchor[8], anchor[12],
//                  anchor[1], anchor[5], anchor[9], anchor[13],
//                  anchor[2], anchor[6], anchor[10], anchor[14],
//                  anchor[3], anchor[7], anchor[11], anchor[15]);
    }
  }


  @Override
  public int trackableCount() {
    return surfar.trackPlanes.size();
  }


  @Override
  public int trackableId(int i) {
    return surfar.trackPlanes.get(i).hashCode();
  }


  @Override
  public int trackableType(int i) {
    Plane plane = surfar.trackPlanes.get(i);
    if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) {
      return PAR.PLANE_FLOOR;
    } else if (plane.getType() == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
      return PAR.PLANE_CEILING;
    } else if (plane.getType() == Plane.Type.VERTICAL) {
      return PAR.PLANE_WALL;
    }
    return PAR.UNKNOWN;
  }

  @Override
  public int trackableStatus(int i) {
    Plane plane = surfar.trackPlanes.get(i);

    if (surfar.newPlanes.contains(plane)) {
      return PAR.CREATED;
    } else if (surfar.updatedPlanes.contains(plane)) {
      return PAR.UPDATED;
    } else if (plane.getTrackingState() == TrackingState.TRACKING) {
      return PAR.TRACKING;
    } else if (plane.getTrackingState() == TrackingState.PAUSED) {
      return PAR.PAUSED;
    } else if (plane.getTrackingState() == TrackingState.STOPPED) {
      return PAR.STOPPED;
    }

    return 0;
  }

  @Override
  public boolean trackableSelected(int i) {
    return surfar.trackPlanes.indexOf(surfar.selPlane) == i;
  }

  @Override
  public float trackableExtentX(int i) {
    return surfar.trackPlanes.get(i).getExtentX();
  }


  @Override
  public float trackableExtentZ(int i) {
    return surfar.trackPlanes.get(i).getExtentZ();
  }

  @Override
  public float[] getTrackablePolygon(int i) {
    return getTrackablePolygon(i, null);
  }

  @Override
  public float[] getTrackablePolygon(int i, float[] points) {
    Plane plane = surfar.trackPlanes.get(i);
    FloatBuffer buffer = plane.getPolygon();
    buffer.rewind();
    if (points == null || points.length < buffer.capacity()) {
      points = new float[buffer.capacity()];
    }
    buffer.get(points, 0, buffer.capacity());
    return points;
  }

  @Override
  public PMatrix3D getTrackableMatrix(int i) {
    return getTrackableMatrix(i, null);
  }


  @Override
  public PMatrix3D getTrackableMatrix(int i, PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }

    Plane plane = surfar.trackPlanes.get(i);
    float[] mat = surfar.trackMatrices.get(plane);
    target.set(mat[0], mat[4], mat[8], mat[12],
               mat[1], mat[5], mat[9], mat[13],
               mat[2], mat[6], mat[10], mat[14],
               mat[3], mat[7], mat[11], mat[15]);

    return target;
  }


  @Override
  public int anchorCount() {
    return surfar.anchors.size();
  }


//  @Override
//  public boolean anchorPaused(int id) {
//    return surfar.anchors.get(id).getTrackingState() == TrackingState.PAUSED;
//  }
//
//  @Override
//  public boolean anchorStopped(int id) {
//    return surfar.anchors.get(id).getTrackingState() == TrackingState.TRACKING;
//  }


  public int anchorId(int i) {
    return i;
  }

  public int anchorStatus(int id) {
    Anchor anchor = surfar.anchors.get(id);

    if (anchor.getTrackingState() == TrackingState.PAUSED) {
      return PAR.PAUSED;
    } else if (anchor.getTrackingState() == TrackingState.TRACKING) {
      return PAR.TRACKING;
    } else if (anchor.getTrackingState() == TrackingState.STOPPED) {
      return PAR.STOPPED;
    }

    return 0;
  }


  @Override
  public int createAnchor() {
    if (surfar.selAnchor == -1) {
      surfar.selAnchor = surfar.anchors.size();
      surfar.anchors.add(null);
    } else {
      PGraphics.showWarning("Selection anchor already created");
    }
    return surfar.selAnchor;
  }

  protected float[] pointIn = new float[3];
  protected float[] pointOut = new float[3];

  @Override
  public int createAnchor(int trackId, float x, float y, float z) {
    Plane plane = surfar.trackMap.get(trackId);
    Pose planePose = plane.getCenterPose();
    pointIn[0] = x;
    pointIn[1] = y;
    pointIn[2] = z;
    planePose.transformPoint(pointIn, 0, pointOut, 0);
    Pose anchorPose = Pose.makeTranslation(pointOut);
    Anchor anchor = plane.createAnchor(anchorPose);
    surfar.anchors.add(anchor);
    return surfar.anchors.size() - 1;
  }


  @Override
  public void deleteAnchor(int id) {

  }


  @Override
  public PMatrix3D getAnchorMatrix(int id) {
    return getAnchorMatrix(id, null);
  }


  protected float[] anchorMatrix = new float[16];

  @Override
  public PMatrix3D getAnchorMatrix(int id, PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    Anchor anchor = surfar.anchors.get(id);
    anchor.getPose().toMatrix(anchorMatrix, 0);
    target.set(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
               anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
               anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
               anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15]);
    return target;
  }


  @Override
  public void anchor(int id) {
    Anchor anchor = surfar.anchors.get(id);
    anchor.getPose().toMatrix(anchorMatrix, 0);

      // now, modelview = view * anchor
      applyMatrix(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
                  anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
                  anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
                  anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15]);
  }

  @Override
  public void lights() {
    // TODO <---------------------------------------------------------------------------------------
    super.lights();
  }
}
