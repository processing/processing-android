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

import android.view.SurfaceHolder;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;

import java.net.URL;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.core.PMatrix3D;
import processing.core.PSurface;
import processing.opengl.PGL;
import processing.opengl.PGLES;
import processing.opengl.PGraphics3D;
import processing.opengl.PGraphicsOpenGL;
import processing.opengl.PShader;

public class PGraphicsAR extends PGraphics3D {
  static protected final int UNKNOWN       = -1;

  static protected final int PLANE_FLOOR   = 0;
  static protected final int PLANE_CEILING = 1;
  static protected final int PLANE_WALL    = 2;
  static protected final int POINT         = 3;

  static protected final int TRACKING  = 0;
  static protected final int PAUSED    = 1;
  static protected final int STOPPED   = 2;

  // Convenience reference to the AR surface. It is the same object one gets from PApplet.getSurface().
  protected PSurfaceAR surfar;

  protected BackgroundRenderer backgroundRenderer;

  protected float[] projMatrix = new float[16];
  protected float[] viewMatrix = new float[16];
  protected float[] anchorMatrix = new float[16];
  protected float[] colorCorrection = new float[4];

  protected ArrayList<Tracker> trackers = new ArrayList<Tracker>();
  protected ArrayList<Plane> trackPlanes = new ArrayList<Plane>();
  protected HashMap<Plane, float[]> trackMatrices = new HashMap<Plane, float[]>();
  protected HashMap<Plane, Integer> trackIds = new HashMap<Plane, Integer>();
  protected HashMap<Integer, Integer> trackIdx = new HashMap<Integer, Integer>();

  protected ArrayList<Plane> newPlanes = new ArrayList<Plane>();
  protected ArrayList<Integer> delAnchors = new ArrayList<Integer>();

  protected HashMap<Integer, Anchor> anchors = new HashMap<Integer, Anchor>();

  protected float[] pointIn = new float[3];
  protected float[] pointOut = new float[3];

  protected int lastTrackableId = 0;
  protected int lastAnchorId = 0;

  static protected URL arLightShaderVertURL =
          PGraphicsOpenGL.class.getResource("/assets/shaders/ARLightVert.glsl");
  static protected URL arTexlightShaderVertURL =
          PGraphicsOpenGL.class.getResource("/assets/shaders/ARTexLightVert.glsl");
  static protected URL arLightShaderFragURL =
          PGraphicsOpenGL.class.getResource("/assets/shaders/ARLightFrag.glsl");
  static protected URL arTexlightShaderFragURL =
          PGraphicsOpenGL.class.getResource("/assets/shaders/ARTexLightFrag.glsl");

  protected PShader arLightShader;
  protected PShader arTexlightShader;

  public PGraphicsAR() {
  }


  static processing.ar.Trackable[] getTrackables() {
    return null;
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

    // Always clear the screen and draw the background
    background(0);
    backgroundRenderer.draw(surfar.frame);
  }

  public void endDraw() {
    cleanup();
    super.endDraw();
  }


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
    if (projMatrix != null && viewMatrix != null) {

      // Fist, set all matrices to identity
      resetProjection();
      resetMatrix();

      // Apply the projection matrix
      applyProjection(projMatrix[0], projMatrix[4], projMatrix[8], projMatrix[12],
                      projMatrix[1], projMatrix[5], projMatrix[9], projMatrix[13],
                      projMatrix[2], projMatrix[6], projMatrix[10], projMatrix[14],
                      projMatrix[3], projMatrix[7], projMatrix[11], projMatrix[15]);

      // make modelview = view
      applyMatrix(viewMatrix[0], viewMatrix[4], viewMatrix[8], viewMatrix[12],
                  viewMatrix[1], viewMatrix[5], viewMatrix[9], viewMatrix[13],
                  viewMatrix[2], viewMatrix[6], viewMatrix[10], viewMatrix[14],
                  viewMatrix[3], viewMatrix[7], viewMatrix[11], viewMatrix[15]);
    }
  }


  public void addTracker(Tracker tracker) {
    trackers.add(tracker);
  }


  public void removeTracker(Tracker tracker) {
    trackers.remove(tracker);
  }


  public int trackableCount() {
    return trackPlanes.size();
  }



  public int trackableId(int i) {
    return trackIds.get(trackPlanes.get(i));
  }


  public int trackableIndex(int id) {
    return trackIdx.get(id);
  }

  public int trackableType(int i) {
    Plane plane = trackPlanes.get(i);
    if (plane.getType() == Plane.Type.HORIZONTAL_UPWARD_FACING) {
      return PLANE_FLOOR;
    } else if (plane.getType() == Plane.Type.HORIZONTAL_DOWNWARD_FACING) {
      return PLANE_CEILING;
    } else if (plane.getType() == Plane.Type.VERTICAL) {
      return PLANE_WALL;
    }
    return UNKNOWN;
  }

  public int trackableStatus(int i) {
    Plane plane = trackPlanes.get(i);
     if (plane.getTrackingState() == TrackingState.PAUSED) {
      return PAUSED;
    } else if (plane.getTrackingState() == TrackingState.TRACKING) {
      return TRACKING;
    } else if (plane.getTrackingState() == TrackingState.STOPPED) {
      return STOPPED;
    }
    return UNKNOWN;
  }

  public boolean trackableNew(int i) {
    Plane plane = trackPlanes.get(i);
    return newPlanes.contains(plane);
  }

  public boolean trackableSelected(int i, int mx, int my) {
    Plane planei = trackPlanes.get(i);
    for (HitResult hit : surfar.frame.hitTest(mx, my)) {
      Trackable trackable = hit.getTrackable();
      if (trackable instanceof Plane) {
        Plane plane = (Plane)trackable;
        if (planei.equals(plane) && plane.isPoseInPolygon(hit.getHitPose())) {
          return true;
        }
      }
    }
    return false;
  }


  protected HitResult getHitResult(int mx, int my) {
    for (HitResult hit : surfar.frame.hitTest(mx, my)) {
      Trackable trackable = hit.getTrackable();
      if (trackable instanceof Plane) {
        Plane plane = (Plane)trackable;
        if (trackPlanes.contains(plane) && plane.isPoseInPolygon(hit.getHitPose())) {
          return hit;
        }
      }
    }
    return null;
  }

  protected int getTrackable(HitResult hit) {
    Plane plane = (Plane) hit.getTrackable();
    return trackPlanes.indexOf(plane);
  }

  public float[] getTrackablePolygon(int i) {
    return getTrackablePolygon(i, null);
  }


  public float[] getTrackablePolygon(int i, float[] points) {
    Plane plane = trackPlanes.get(i);
    FloatBuffer buffer = plane.getPolygon();
    buffer.rewind();
    if (points == null || points.length < buffer.capacity()) {
      points = new float[buffer.capacity()];
    }
    buffer.get(points, 0, buffer.capacity());
    return points;
  }


  public PMatrix3D getTrackableMatrix(int i) {
    return getTrackableMatrix(i, null);
  }


  public PMatrix3D getTrackableMatrix(int i, PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }

    Plane plane = trackPlanes.get(i);
    float[] mat = trackMatrices.get(plane);
    target.set(mat[0], mat[4], mat[8], mat[12],
               mat[1], mat[5], mat[9], mat[13],
               mat[2], mat[6], mat[10], mat[14],
               mat[3], mat[7], mat[11], mat[15]);

    return target;
  }


  public int createAnchor(int i, float x, float y, float z) {
    Plane plane = trackPlanes.get(i);
    Pose planePose = plane.getCenterPose();
    pointIn[0] = x;
    pointIn[1] = y;
    pointIn[2] = z;
    planePose.transformPoint(pointIn, 0, pointOut, 0);
    Pose anchorPose = Pose.makeTranslation(pointOut);
    Anchor anchor = plane.createAnchor(anchorPose);
    anchors.put(++lastAnchorId, anchor);
    return lastAnchorId;
  }


  public int createAnchor(int mx, int my) {
    for (HitResult hit : surfar.frame.hitTest(mx, my)) {
      Trackable trackable = hit.getTrackable();
      if (trackable instanceof Plane) {
        Plane plane = (Plane)trackable;
        if (trackPlanes.contains(plane) && plane.isPoseInPolygon(hit.getHitPose())) {
          return createAnchor(hit);
        }
      }
    }
    return 0;
  }


  protected int createAnchor(HitResult hit) {
    Anchor anchor = hit.createAnchor();
    anchors.put(++lastAnchorId, anchor);
    return lastAnchorId;
  }


  public void deleteAnchor(int id) {
    delAnchors.add(id);
  }


  public int anchorCount() {
    return anchors.size();
  }


  public int anchorStatus(int id) {
    Anchor anchor = anchors.get(id);
    if (anchor.getTrackingState() == TrackingState.PAUSED) {
      return PAUSED;
    } else if (anchor.getTrackingState() == TrackingState.TRACKING) {
      return TRACKING;
    } else if (anchor.getTrackingState() == TrackingState.STOPPED) {
      return STOPPED;
    }
    return UNKNOWN;
  }


  public PMatrix3D getAnchorMatrix(int id) {
    return getAnchorMatrix(id, null);
  }


  public PMatrix3D getAnchorMatrix(int id, PMatrix3D target) {
    if (target == null) {
      target = new PMatrix3D();
    }
    Anchor anchor = anchors.get(id);
    anchor.getPose().toMatrix(anchorMatrix, 0);
    target.set(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
               anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
               anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
               anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15]);
    return target;
  }


  public void anchor(int id) {
    Anchor anchor = anchors.get(id);
    anchor.getPose().toMatrix(anchorMatrix, 0);

    // now, modelview = view * anchor
    applyMatrix(anchorMatrix[0], anchorMatrix[4], anchorMatrix[8], anchorMatrix[12],
                anchorMatrix[1], anchorMatrix[5], anchorMatrix[9], anchorMatrix[13],
                anchorMatrix[2], anchorMatrix[6], anchorMatrix[10], anchorMatrix[14],
                anchorMatrix[3], anchorMatrix[7], anchorMatrix[11], anchorMatrix[15]);
  }


  protected void createBackgroundRenderer() {
    backgroundRenderer = new BackgroundRenderer(surfar.getActivity());
  }

  protected void setCameraTexture() {
    surfar.session.setCameraTextureName(backgroundRenderer.getTextureId());
  }

  protected void updateMatrices() {
    surfar.camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f);
    surfar.camera.getViewMatrix(viewMatrix, 0);
    surfar.frame.getLightEstimate().getColorCorrection(colorCorrection, 0);
  }


  protected void updateTrackables() {
    Collection<Plane> planes = surfar.frame.getUpdatedTrackables(Plane.class);
    for (Plane plane: planes) {
      if (plane.getSubsumedBy() != null) continue;
      float[] mat;
      if (trackMatrices.containsKey(plane)) {
        mat = trackMatrices.get(plane);
      } else {
        mat = new float[16];
        trackMatrices.put(plane, mat);
        trackPlanes.add(plane);
        trackIds.put(plane, ++lastTrackableId);
        newPlanes.add(plane);
      }
      Pose pose = plane.getCenterPose();
      pose.toMatrix(mat, 0);
    }

    // Remove stopped and subsummed trackables
    for (int i = trackPlanes.size() - 1; i >= 0; i--) {
      Plane plane = trackPlanes.get(i);
      if (plane.getTrackingState() == TrackingState.STOPPED || plane.getSubsumedBy() != null) {
        trackPlanes.remove(i);
        trackMatrices.remove(plane);
        int pid = trackIds.remove(plane);
        trackIdx.remove(pid);
        for (Tracker t: trackers) t.remove(pid);
      }
    }

    // Update indices
    for (int i = 0; i < trackPlanes.size(); i++) {
      Plane plane = trackPlanes.get(i);
      int pid = trackIds.get(plane);
      trackIdx.put(pid, i);
      if (newPlanes.contains(plane)) {
        for (Tracker t: trackers) t.create(i);
      }
    }
  }


  protected void cleanup() {
    newPlanes.clear();

    for (int id: delAnchors) {
      Anchor anchor = anchors.remove(id);
      anchor.detach();
    }
    delAnchors.clear();
  }


  @Override
  protected PShader getPolyShader(boolean lit, boolean tex) {
    if (getPrimaryPG() != this) {
      // An offscreen surface will use the default shaders from the parent OpenGL renderer
      return super.getPolyShader(lit, tex);
    }

    PShader shader;
    boolean useDefault = polyShader == null;
    if (lit) {
      if (tex) {
        if (useDefault || !isPolyShaderTexLight(polyShader)) {
          if (arTexlightShader == null) {
            arTexlightShader = loadShaderFromURL(arTexlightShaderFragURL, arTexlightShaderVertURL);
          }
          shader = arTexlightShader;
        } else {
          shader = polyShader;
        }
      } else {
        if (useDefault || !isPolyShaderLight(polyShader)) {
          if (arLightShader == null) {
            arLightShader = loadShaderFromURL(arLightShaderFragURL, arLightShaderVertURL);
          }
          shader = arLightShader;
        } else {
          shader = polyShader;
        }
      }
      updateShader(shader);
      return shader;
    } else {
      // Non-lit shaders use the default shaders from the parent OpenGL renderer
      return super.getPolyShader(lit, tex);
    }
  }


  @Override
  protected void updateShader(PShader shader) {
    super.updateShader(shader);
    shader.set("colorCorrection", colorCorrection, 4);
  }
}
