package processing.ar;

import processing.core.PMatrix3D;

public class Anchor implements PAR {
  protected PGraphicsAR g;

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
    g.deleteAnchor(id);
  }

  public String id() {
    return String.valueOf(id);
  }

  public int status() {
    return g.anchorStatus(id);
  }

  public PMatrix3D matrix() {
    return m;
  }

  public void attach() {
    g.pushMatrix();
    g.applyMatrix(matrix());
  }

  public void detach() {
    g.popMatrix();
  }
}
