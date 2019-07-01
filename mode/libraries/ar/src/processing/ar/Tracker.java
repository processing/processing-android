package processing.ar;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import processing.core.PApplet;

public class Tracker implements PAR {
  protected PApplet p;
  protected PGraphicsAR g;

  private HashMap<String, Trackable> trackables = new HashMap<String, Trackable>();
  private ArrayList<Anchor> toRemove = new ArrayList<Anchor>();
  private Method trackableEventMethod;

  public Tracker(PApplet parent) {
    this.p = parent;
    this.g = (PGraphicsAR) p.g;
    setEventHandler();
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

  protected void create(int idx) {
    if (trackableEventMethod != null) {
      try {
        Trackable t = get(idx);
        trackableEventMethod.invoke(p, t);
      } catch (Exception e) {
        System.err.println("error, disabling trackableEventMethod() for AR tracker");
        e.printStackTrace();
        trackableEventMethod = null;
      }
    }
  }

  public void clearAnchors(Collection<Anchor> anchors) {
    for (Anchor anchor : anchors) {
      if (anchor.isStopped() || anchor.isDisposed()) {
        anchor.dispose();
        toRemove.add(anchor);
      }
    }
    anchors.removeAll(toRemove);
    toRemove.clear();
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


  protected void setEventHandler() {
    try {
      trackableEventMethod = p.getClass().getMethod("trackableEvent", Trackable.class);
      return;
    } catch (Exception e) {
      // no such method, or an error... which is fine, just ignore
    }

    // trackableEvent can alternatively be defined as receiving an Object, to allow
    // Processing mode implementors to support the video library without linking
    // to it at build-time.
    try {
      trackableEventMethod = p.getClass().getMethod("trackableEvent", Object.class);
    } catch (Exception e) {
      // no such method, or an error... which is fine, just ignore
    }
  }
}
