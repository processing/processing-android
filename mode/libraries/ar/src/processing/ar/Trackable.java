package processing.ar;

import processing.core.PMatrix3D;

public class Trackable {
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

  public int status() {
    int idx = g.trackableIndex(id);
    return g.trackableStatus(idx);
  }

  public int type() {
    int idx = g.trackableIndex(id);
    return g.trackableType(idx);
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

  boolean selected(int mx, int my) {
    int idx = g.trackableIndex(id);
    return g.trackableSelected(idx, mx, my);
  }
}
