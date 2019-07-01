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

public class Anchor {
  protected PGraphicsAR g;
  private boolean disposed = false;

  private int id;
  private PMatrix3D m;

  public Anchor(Trackable trackable, float x, float y, float z) {
    this.g = trackable.g;

    int idx = g.trackableIndex(Integer.parseInt(trackable.id()));
    id = g.createAnchor(idx, x, y, z);
  }

  public Anchor(Trackable trackable) {
    this.g = trackable.g;
    id = g.createAnchor(trackable.hit);
    trackable.hit = null;
  }

  public void dispose() {
    if (!disposed) {
      g.deleteAnchor(id);
      disposed = true;
    }
  }

  public String id() {
    return String.valueOf(id);
  }

  public PMatrix3D matrix() {
    m = g.getTrackableMatrix(id, m);
    return m;
  }

  public void attach() {
    g.pushMatrix();
    g.anchor(id);
  }

  public void detach() {
    g.popMatrix();
  }

  public boolean isTracking() {
    return g.anchorStatus(id) == PGraphicsAR.TRACKING;
  }

  public boolean isPaused() {
    return g.anchorStatus(id) == PGraphicsAR.PAUSED;
  }

  public boolean isStopped() {
    return g.anchorStatus(id) == PGraphicsAR.STOPPED;
  }

  public boolean isDisposed() {
    return disposed;
  }
}
