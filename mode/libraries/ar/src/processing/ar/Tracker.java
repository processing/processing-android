package processing.ar;

import java.util.HashMap;
import java.util.Set;

import processing.core.PApplet;

public class Tracker implements PAR {
  protected PApplet p;
  protected PGraphicsAR g;

  private HashMap<String, Trackable> trackables = new HashMap<String, Trackable>();

  public Tracker(PApplet parent) {
    this.p = parent;
    this.g = (PGraphicsAR) p.g;
  }

  public void start() {
    cleanup();
    g.addTracker(this);
  }

  public void stop() {
    g.removeTracker(this);
  }

  public int count() {
    return g.trackableCount();
  }

  public Trackable get(int idx) {
    int id = g.trackableId(idx);
    String sid = String.valueOf(id);
    if (!trackables.containsKey(sid)) {
      Trackable t = new Trackable(g, id);
      trackables.put(sid, t);
    }
    return get(sid);
  }

  public Trackable get(String id) {
    return trackables.get(id);
  }

  protected void cleanup() {
    // Remove any inactive trackables left over in the tracker.
    Set<String> ids = trackables.keySet();
    for (String id: ids) {
      Trackable t = trackables.get(id);
      if (t.isStopped()) trackables.remove(id);
    }
  }

  protected void remove(int idx) {
    int id = g.trackableId(idx);
    String sid = String.valueOf(id);
    remove(sid);
  }

  protected void remove(String id) {
    trackables.remove(id);
  }
}
