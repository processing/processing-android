/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package procesing.a2d;

import android.content.Context;

import android.service.wallpaper.WallpaperService;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import processing.android.AppComponent;
import processing.android.PFragment;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.core.PSurfaceNone;

public class PSurfaceAndroid2D extends PSurfaceNone {

  public PSurfaceAndroid2D() { }

  public PSurfaceAndroid2D(PGraphics graphics, AppComponent component, SurfaceHolder holder) {
    this.sketch = graphics.parent;
    this.graphics = graphics;
    this.component = component;
    if (component.getKind() == AppComponent.FRAGMENT) {
      PFragment frag = (PFragment)component;
      activity = frag.getActivity();
      surface = new SketchSurfaceView(activity, null);
    } else if (component.getKind() == AppComponent.WALLPAPER) {
      wallpaper = (WallpaperService)component;
      surface = new SketchSurfaceView(wallpaper, holder);
    } else if (component.getKind() == AppComponent.WATCHFACE) {
      watchface = (CanvasWatchFaceService)component;
      surface = null;
    }
  }

  ///////////////////////////////////////////////////////////

  // SurfaceView

  public class SketchSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    SurfaceHolder holder;

    public SketchSurfaceView(Context context, SurfaceHolder holder) {
      super(context);
      this.holder = holder;

//    println("surface holder");
    // Install a SurfaceHolder.Callback so we get notified when the
    // underlying surface is created and destroyed
      SurfaceHolder h = getHolder();
      h.addCallback(this);
//    surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU); // no longer needed.

//    println("setting focusable, requesting focus");
      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();
//    println("done making surface view");
    }

    @Override
    public SurfaceHolder getHolder() {
      if (holder == null) {
        return super.getHolder();
      } else {
        return holder;
      }
    }
//  public PGraphics getGraphics() {
//    return g2;
//  }

    // part of SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
    }


    // part of SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
      //g2.dispose();
    }


    // part of SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int iwidth, int iheight) {
      if (PApplet.DEBUG) {
        System.out.println("SketchSurfaceView.surfaceChanged() " + iwidth + " " + iheight);
      }

      if (sketch.fullScreen) {
        sketch.displayWidth = iwidth;
        sketch.displayHeight = iheight;
      }
      sketch.width = iwidth;
      sketch.height = iheight;

      graphics.setSize(sketch.sketchWidth(), sketch.sketchHeight());
      sketch.surfaceChanged();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
      super.onWindowFocusChanged(hasFocus);
      sketch.surfaceWindowFocusChanged(hasFocus);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      return sketch.surfaceTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent event) {
      sketch.surfaceKeyDown(code, event);
      return super.onKeyDown(code, event);
    }

    @Override
    public boolean onKeyUp(int code, android.view.KeyEvent event) {
      sketch.surfaceKeyUp(code, event);
      return super.onKeyUp(code, event);
    }
  }
}
