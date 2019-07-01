package processing.ar;

import processing.core.PMatrix3D;

public class Anchor implements PAR {
  protected PGraphicsAR g;
  private boolean disposed = false;

  private int id;
  private PMatrix3D m;

  public Anchor(Trackable trackable, float x, float y, float z) {
    this.g = trackable.g;

    int idx = g.trackableIndex(Integer.parseInt(trackable.id()));
    id = g.createAnchor(idx, x, y, z);
  }

  public Anchor(Tracker tracker, int mx, int my) {
    this.g = tracker.g;
    id = g.createAnchor(mx, my);
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
    return g.anchorStatus(id) == TRACKING;
  }

  public boolean isPaused() {
    return g.anchorStatus(id) == PAUSED;
  }

  public boolean isStopped() {
    return g.anchorStatus(id) == STOPPED;
  }

  public boolean isDisposed() {
    return disposed;
  }
}
