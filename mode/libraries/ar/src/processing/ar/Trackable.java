package processing.ar;

import processing.core.PMatrix3D;

public class Trackable implements PAR {
  protected PGraphicsAR g;

  private int id;
  private PMatrix3D m;
  private float[] points;

  public Trackable(PGraphicsAR g, int id) {
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
    return g.trackableStatus(idx) == TRACKING;
  }

  public boolean isPaused() {
    int idx = g.trackableIndex(id);
    return g.trackableStatus(idx) == PAUSED;
  }

  public boolean isStopped() {
    int idx = g.trackableIndex(id);
    return g.trackableStatus(idx) == STOPPED;
  }

  public boolean isPlane() {
    return true;
  }

  public boolean isPointCloud() {
    return false;
  }

  public boolean isFloorPlane() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx) == PLANE_FLOOR;
  }

  public boolean isCeilingPlane() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx) == PLANE_CEILING;
  }

  public boolean isWallPlane() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx) == PLANE_WALL;
  }
}
