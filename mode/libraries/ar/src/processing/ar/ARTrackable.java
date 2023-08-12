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

import processing.core.PMatrix3D;

import com.google.ar.core.HitResult;

public class ARTrackable {
  protected ARGraphics g;
  protected HitResult hit;

  private int id;
  private PMatrix3D m;
  private float[] points;

  public ARTrackable(ARGraphics g, int id) {
    this.g = g;
    this.id = id;
  }

  public String id() {
    return String.valueOf(id);
  }

  public PMatrix3D matrix() {
    int idx = g.trackableIndex(id);
    m = g.getTrackableMatrix(idx, m);
    return m;
  }

  public void transform() {
    g.applyMatrix(matrix());
  }

  public float[] getPolygon() {
    int idx = g.trackableIndex(id);
    points = g.getTrackablePolygon(idx, points);
    return points;
  }


  public float lengthX() {
    int idx = g.trackableIndex(id);
    return g.getTrackableExtentX(idx);
  }


  public float lengthY() {
    return 0;
  }


  public float lengthZ() {
    int idx = g.trackableIndex(id);
    return g.getTrackableExtentZ(idx);
  }


  public boolean isSelected(int mx, int my) {
    int idx = g.trackableIndex(id);
    return g.trackableSelected(idx, mx, my);
  }

  public boolean isNew() {
    int idx = g.trackableIndex(id);
    return g.trackableNew(idx);
  }

  public boolean isTracking() {
    int idx = g.trackableIndex(id);
    return g.trackableStatus(idx) == ARGraphics.TRACKING;
  }

  public boolean isPaused() {
    int idx = g.trackableIndex(id);
    return g.trackableStatus(idx) == ARGraphics.PAUSED;
  }

  public boolean isStopped() {
    int idx = g.trackableIndex(id);
    return g.trackableStatus(idx) == ARGraphics.STOPPED;
  }

  public boolean isPlane() {
    return true;
  }

  public boolean isPointCloud() {
    return false;
  }

  public boolean isFloorPlane() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx) == ARGraphics.PLANE_FLOOR;
  }

//  public boolean isImage(){
//    System.out.println("crosschecking values");
//    int idx = g.trackableIndex(id);
//    System.out.println(g.trackableType(idx)+" "+ARGraphics.IMAGE);
//    return g.trackableType(idx)== ARGraphics.IMAGE;
//  }

  public boolean isImage() {
    System.out.println("crosschecking values");
    int idx = -1;
    idx= g.trackableIndex(id);
    System.out.println("idx value is:" + idx);
    int trackableType = g.trackableType(idx);

    System.out.println("TrackableType is:"+ trackableType);

    System.out.println("Argraphics is:"+ ARGraphics.IMAGE);
    // Check if trackableType is null before invoking intValue()

      //THis IS CAUSING THE ERROR!
    if (ARGraphics.IMAGE== trackableType)
        return true;

    return false;
  }


  public boolean isCeilingPlane() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx) == ARGraphics.PLANE_CEILING;
  }

  public boolean isWallPlane() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx) == ARGraphics.PLANE_WALL;
  }
}