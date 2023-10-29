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

import android.graphics.Bitmap;

import com.google.ar.core.HitResult;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Session;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import processing.core.PApplet;
import processing.core.PImage;

public class ARTracker {
  protected PApplet p;
  protected ARGraphics g;
  protected AugmentedImageDatabase db;

  private HashMap<String, ARTrackable> trackables = new HashMap<String, ARTrackable>();
  private ArrayList<ARAnchor> toRemove = new ArrayList<ARAnchor>();
  private Method trackableEventMethod;

  public ARTracker(PApplet parent) {
    this.p = parent;
    this.g = (ARGraphics)p.g;
    setEventHandler();
  }

  public void addImage(String name, PImage img) {
    addImageImp(name, img, null);
  }

  public void addImage(String name, PImage img, float size) {
    addImageImp(name, img, size);
  }

  private void addImageImp(String name, PImage img, Float size) {
    if (db == null) {
      // Creating a new database of augmented images.
      db = new AugmentedImageDatabase(g.surfar.session); 
    }    

    Bitmap bitmap = (Bitmap)img.getNative();
    if (size != null) {
      db.addImage(name, bitmap, size);      
    } else {
      db.addImage(name, bitmap);
    }

    // Reset the session config with the updated image database
    Config config = new Config(g.surfar.session);
    config.setAugmentedImageDatabase(db);
    g.surfar.session.configure(config);
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

  public ARTrackable get(int idx) {
    int id = g.trackableId(idx);
    String name = g.trackableName(idx);
    String sid = String.valueOf(id);
    if (!trackables.containsKey(sid)) {
      ARTrackable t = new ARTrackable(g, id, name);
      trackables.put(sid, t);
    }
    return get(sid);
  }

  public ARTrackable get(String id) {
    return trackables.get(id);
  }

  public ARTrackable get(int mx, int my) {
    HitResult hit = g.getHitResult(mx, my);
    if (hit != null) {
      int idx = g.getTrackable(hit);
      ARTrackable t = get(idx);
      t.hit = hit;
      return t;
    } else {
      return null;
    }
  }

  protected void create(int idx) {
    if (trackableEventMethod != null) {
      try {
        ARTrackable t = get(idx);
        trackableEventMethod.invoke(p, t);
      } catch (Exception e) {
        System.err.println("error, disabling trackableEventMethod() for AR tracker");
        e.printStackTrace();
        trackableEventMethod = null;
      }
    }
  }

  public void clearAnchors(Collection<ARAnchor> anchors) {
    for (ARAnchor anchor : anchors) {
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
      ARTrackable t = trackables.get(id);
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
      trackableEventMethod = p.getClass().getMethod("trackableEvent", ARTrackable.class);
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
