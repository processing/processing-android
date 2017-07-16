/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation
  Copyright (c) 2005-12 Ben Fry and Casey Reas

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

package processing.a2d;

import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import processing.android.AppComponent;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import processing.core.PImage;
import processing.core.PMatrix;
import processing.core.PMatrix2D;
import processing.core.PMatrix3D;
import processing.core.PShape;
import processing.core.PShapeSVG;
import processing.core.PSurface;
import processing.data.XML;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.Style;
import android.view.SurfaceHolder;


/**
 * Subclass for PGraphics that implements the graphics API using
 * the Android 2D graphics model. Similar tradeoffs to JAVA2D mode
 * with the original (desktop) version of Processing.
 */
public class PGraphicsAndroid2D extends PGraphics {
  static public boolean useBitmap = true;

  public Canvas canvas;  // like g2 for PGraphicsJava2D

  /// break the shape at the next vertex (next vertex() call is a moveto())
  boolean breakShape;

  /// coordinates for internal curve calculation
  float[] curveCoordX;
  float[] curveCoordY;
  float[] curveDrawX;
  float[] curveDrawY;

  static protected final int MATRIX_STACK_DEPTH = 32;
  protected float[][] transformStack;
  public PMatrix2D transform;
  protected Matrix tmpMatrix;
  protected float[] tmpArray;
  int transformCount;

//  Line2D.Float line = new Line2D.Float();
//  Ellipse2D.Float ellipse = new Ellipse2D.Float();
//  Rectangle2D.Float rect = new Rectangle2D.Float();
//  Arc2D.Float arc = new Arc2D.Float();
  /**
   * The temporary path object that does most of the drawing work. If there are
   * any points in the path (meaning that moveto has been called), then
   * vertexCount will be 1 (or more). In the POLYGON case, vertexCount is only
   * set to 1 after the first point is drawn (to indicate a moveto) and not
   * incremented after, since the variable isn't used for POLYGON paths.
   */
  Path path;

  /** Temporary rectangle object. */
  RectF rect;

//  protected Color tintColorObject;

//  protected Color fillColorObject;
//  public boolean fillGradient;
//  public Paint fillGradientObject;

//  protected Color strokeColorObject;
//  public boolean strokeGradient;
//  public Paint strokeGradientObject;

  Paint fillPaint;
  Paint strokePaint;
  Paint tintPaint;


  /**
   * Marks when changes to the size have occurred, so that the backing bitmap
   * can be recreated.
   */
  protected boolean sized;

  //////////////////////////////////////////////////////////////

  // INTERNAL


  public PGraphicsAndroid2D() {
    transformStack = new float[MATRIX_STACK_DEPTH][6];
    transform = new PMatrix2D();
    tmpMatrix = new Matrix();
    tmpArray = new float[9];

    path = new Path();
    rect = new RectF();

    fillPaint = new Paint();
    fillPaint.setStyle(Style.FILL);
    strokePaint = new Paint();
    strokePaint.setStyle(Style.STROKE);
    tintPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  }


  //public void setParent(PApplet parent)


  //public void setPrimary(boolean primary)


  //public void setPath(String path)


  @Override
  public void setSize(int iwidth, int iheight) {
    super.setSize(iwidth, iheight);
    sized = true;
  }


  @Override
  public void dispose() {
    if (bitmap != null) bitmap.recycle();
  }


  @Override
  public PSurface createSurface(AppComponent component, SurfaceHolder holder, boolean reset) {  // ignore
    return new PSurfaceAndroid2D(this, component, holder);
  }


  //////////////////////////////////////////////////////////////

  // FRAME

  /*
  public void requestDraw() {
	  parent.surfaceView.requestRender();
  }
  */

//  public boolean canDraw() {
//    return true;
//  }


//  @Override
//  public void requestDraw() {
//parent.handleDraw();
//  }


  protected Canvas checkCanvas() {
    if ((canvas == null || sized) && (useBitmap || !primaryGraphics)) {
      if (bitmap == null || bitmap.getWidth() * bitmap.getHeight() < width * height) {
        if (bitmap != null) bitmap.recycle();
        bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
      } else {
        bitmap.reconfigure(width, height, bitmap.getConfig());
      }
      canvas = new Canvas(bitmap);
      sized = false;
    }
    return canvas;
  }


  @Override
  public void beginDraw() {
    canvas = checkCanvas();

//    if (primaryGraphics) {
//      canvas = parent.getSurfaceHolder().lockCanvas(null);
//      if (canvas == null) {
//        throw new RuntimeException("canvas is still null");
//      }
//    } else {
//      throw new RuntimeException("not primary surface");
//    }

    checkSettings();

    resetMatrix(); // reset model matrix

    // reset vertices
    vertexCount = 0;
  }


  @Override
  public void endDraw() {
    if (bitmap == null) return;

    // hm, mark pixels as changed, because this will instantly do a full
    // copy of all the pixels to the surface.. so that's kind of a mess.
    //updatePixels();

//    if (primaryGraphics) {
//      if (canvas != null) {
//        parent.getSurfaceHolder().unlockCanvasAndPost(canvas);
//      }
//    }

    if (primaryGraphics) {
      SurfaceHolder holder = parent.getSurface().getSurfaceHolder();
      if (holder != null) {
        Canvas screen = null;
        try {
          screen = holder.lockCanvas(null);
          if (screen != null) {
            screen.drawBitmap(bitmap, new Matrix(), null);
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          if (screen != null) {
            try {
              holder.unlockCanvasAndPost(screen);
            } catch (IllegalStateException ex) {
            }
          }
        }
      }
    } else {
      // TODO this is probably overkill for most tasks...
      loadPixels();
    }

    // Marking as modified, and then calling updatePixels() in
    // the super class, which just sets the mx1, my1, mx2, my2
    // coordinates of the modified area. This avoids doing the
    // full copy of the pixels to the surface in this.updatePixels().
    setModified();
    super.updatePixels();
  }



  //////////////////////////////////////////////////////////////

  // SETTINGS


  //protected void checkSettings()


  //protected void defaultSettings()


  //protected void reapplySettings()



  //////////////////////////////////////////////////////////////

  // HINT


  //public void hint(int which)



  //////////////////////////////////////////////////////////////

  // SHAPES


  //public void beginShape(int kind)


  @Override
  public void beginShape(int kind) {
    //super.beginShape(kind);
    shape = kind;
    vertexCount = 0;
    curveVertexCount = 0;

    // reset the path, because when mixing curves and straight
    // lines, vertexCount will be set back to zero, so vertexCount == 1
    // is no longer a good indicator of whether the shape is new.
    // this way, just check to see if gpath is null, and if it isn't
    // then just use it to continue the shape.
    //path = null;
//    path.reset();
//    pathReset = true;
  }


  //public boolean edge(boolean e)


  //public void normal(float nx, float ny, float nz) {


  //public void textureMode(int mode)


  @Override
  public void texture(PImage image) {
    showMethodWarning("texture");
  }


  @Override
  public void vertex(float x, float y) {
    // POLYGON and POINTS are broken out for efficiency
    if (shape == POLYGON) {
//      if (path == null) {
//        path = new Path();
//        path.moveTo(x, y);
      //if (pathReset) {
      if (vertexCount == 0) {
        path.reset();
        path.moveTo(x, y);
        vertexCount = 1;
//        pathReset = false;
      } else if (breakShape) {
        path.moveTo(x, y);
        breakShape = false;
      } else {
        path.lineTo(x, y);
      }

    // this is way too slow, otherwise what's the point of using beginShape()
//    } else if (shape == POINTS) {
//      point(x, y);

    } else {
      curveVertexCount = 0;

      if (vertexCount == vertices.length) {
        float temp[][] = new float[vertexCount<<1][VERTEX_FIELD_COUNT];
        System.arraycopy(vertices, 0, temp, 0, vertexCount);
        vertices = temp;
      }
      // not everyone needs this, but just easier to store rather
      // than adding another moving part to the code...
      vertices[vertexCount][X] = x;
      vertices[vertexCount][Y] = y;
      vertexCount++;

      switch (shape) {

      case POINTS:
        // store them for later
        break;

      case LINES:
        if ((vertexCount % 2) == 0) {
          line(vertices[vertexCount-2][X],
               vertices[vertexCount-2][Y], x, y);
          vertexCount = 0;
        }
        break;

      case LINE_STRIP:
      case LINE_LOOP:
        if (vertexCount >= 2) {
          line(vertices[vertexCount-2][X],
               vertices[vertexCount-2][Y], x, y);
        }
        break;

      case TRIANGLES:
        if ((vertexCount % 3) == 0) {
          triangle(vertices[vertexCount - 3][X],
                   vertices[vertexCount - 3][Y],
                   vertices[vertexCount - 2][X],
                   vertices[vertexCount - 2][Y],
                   x, y);
          vertexCount = 0;
        }
        break;

      case TRIANGLE_STRIP:
        if (vertexCount >= 3) {
          triangle(vertices[vertexCount - 2][X],
                   vertices[vertexCount - 2][Y],
                   x, //vertices[vertexCount - 1][X],
                   y, //vertices[vertexCount - 1][Y],
                   vertices[vertexCount - 3][X],
                   vertices[vertexCount - 3][Y]);
        }
        break;

      case TRIANGLE_FAN:
        if (vertexCount >= 3) {
          triangle(vertices[0][X],
                   vertices[0][Y],
                   vertices[vertexCount - 2][X],
                   vertices[vertexCount - 2][Y],
                   x, y);
        }
        break;

      case QUAD:
      case QUADS:
        if ((vertexCount % 4) == 0) {
          quad(vertices[vertexCount - 4][X],
               vertices[vertexCount - 4][Y],
               vertices[vertexCount - 3][X],
               vertices[vertexCount - 3][Y],
               vertices[vertexCount - 2][X],
               vertices[vertexCount - 2][Y],
               x, y);
          vertexCount = 0;
        }
        break;

      case QUAD_STRIP:
        // 0---2---4
        // |   |   |
        // 1---3---5
        if ((vertexCount >= 4) && ((vertexCount % 2) == 0)) {
          quad(vertices[vertexCount - 4][X],
               vertices[vertexCount - 4][Y],
               vertices[vertexCount - 2][X],
               vertices[vertexCount - 2][Y],
               x, y,
               vertices[vertexCount - 3][X],
               vertices[vertexCount - 3][Y]);
        }
        break;
      }
    }
  }


  @Override
  public void vertex(float x, float y, float z) {
    showDepthWarningXYZ("vertex");
  }


  @Override
  public void vertex(float x, float y, float u, float v) {
    showVariationWarning("vertex(x, y, u, v)");
  }


  @Override
  public void vertex(float x, float y, float z, float u, float v) {
    showDepthWarningXYZ("vertex");
  }


  @Override
  public void breakShape() {
    breakShape = true;
  }


  @Override
  public void endShape(int mode) {
    if (shape == POINTS && stroke && vertexCount > 0) {
      Matrix m = getMatrixImp();
      if (strokeWeight == 1 && m.isIdentity()) {
        if (screenPoint == null) {
          screenPoint = new float[2];
        }
        for (int i = 0; i < vertexCount; i++) {
          screenPoint[0] = vertices[i][X];
          screenPoint[1] = vertices[i][Y];
          m.mapPoints(screenPoint);
          set(PApplet.round(screenPoint[0]), PApplet.round(screenPoint[1]), strokeColor);
          float x = vertices[i][X];
          float y = vertices[i][Y];
          set(PApplet.round(screenX(x, y)), PApplet.round(screenY(x, y)), strokeColor);
        }
      } else {
        float sw = strokeWeight / 2;
        // temporarily use the stroke Paint as a fill
        strokePaint.setStyle(Style.FILL);
        for (int i = 0; i < vertexCount; i++) {
          float x = vertices[i][X];
          float y = vertices[i][Y];
          rect.set(x - sw, y - sw, x + sw, y + sw);
          canvas.drawOval(rect, strokePaint);
        }
        strokePaint.setStyle(Style.STROKE);
      }
    } else if (shape == POLYGON) {
      if (!path.isEmpty()) {
        if (mode == CLOSE) {
          path.close();
        }
        drawPath();
      }
    } else if (shape == LINE_LOOP && vertexCount >= 2) {
    line(vertices[vertexCount-1][X],
         vertices[vertexCount-1][Y],
         vertices[0][X],
         vertices[0][Y]);
    }
    shape = 0;
  }


  //////////////////////////////////////////////////////////////

  // CLIPPING


  @Override
  protected void clipImpl(float x1, float y1, float x2, float y2) {
    canvas.clipRect(x1, y1, x2, y2);
  }


  @Override
  public void noClip() {
    canvas.clipRect(0, 0, width, height, Region.Op.REPLACE);
  }


  //////////////////////////////////////////////////////////////

  // BEZIER VERTICES


  @Override
  public void bezierVertex(float x1, float y1,
                           float x2, float y2,
                           float x3, float y3) {
    // will check to make sure that vertexCount > 0
    bezierVertexCheck();
    path.cubicTo(x1, y1, x2, y2, x3, y3);
  }


  @Override
  public void bezierVertex(float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4) {
    showDepthWarningXYZ("bezierVertex");
  }



  //////////////////////////////////////////////////////////////

  // QUADRATIC BEZIER VERTICES


  @Override
  public void quadraticVertex(float ctrlX, float ctrlY,
                         float endX, float endY) {
    bezierVertexCheck();
    path.quadTo(ctrlX, ctrlY, endX, endY);
  }


  @Override
  public void quadraticVertex(float x2, float y2, float z2,
                         float x4, float y4, float z4) {
    showDepthWarningXYZ("quadVertex");
  }



  //////////////////////////////////////////////////////////////

  // CURVE VERTICES


  @Override
  protected void curveVertexCheck() {
    super.curveVertexCheck();

    if (curveCoordX == null) {
      curveCoordX = new float[4];
      curveCoordY = new float[4];
      curveDrawX = new float[4];
      curveDrawY = new float[4];
    }
  }


  @Override
  protected void curveVertexSegment(float x1, float y1,
                                    float x2, float y2,
                                    float x3, float y3,
                                    float x4, float y4) {
    curveCoordX[0] = x1;
    curveCoordY[0] = y1;

    curveCoordX[1] = x2;
    curveCoordY[1] = y2;

    curveCoordX[2] = x3;
    curveCoordY[2] = y3;

    curveCoordX[3] = x4;
    curveCoordY[3] = y4;

    curveToBezierMatrix.mult(curveCoordX, curveDrawX);
    curveToBezierMatrix.mult(curveCoordY, curveDrawY);

    // since the paths are continuous,
    // only the first point needs the actual moveto
    if (vertexCount == 0) {
//    if (path == null) {
//      path = new Path();
      path.moveTo(curveDrawX[0], curveDrawY[0]);
      vertexCount = 1;
    }

    path.cubicTo(curveDrawX[1], curveDrawY[1],
                 curveDrawX[2], curveDrawY[2],
                 curveDrawX[3], curveDrawY[3]);
  }


  @Override
  public void curveVertex(float x, float y, float z) {
    showDepthWarningXYZ("curveVertex");
  }



  //////////////////////////////////////////////////////////////

  // RENDERER


  //public void flush()



  //////////////////////////////////////////////////////////////

  // POINT, LINE, TRIANGLE, QUAD


  @Override
  public void point(float x, float y) {
    // this is a slow function to call on its own anyway.
    // most people will use set().
    beginShape(POINTS);
    vertex(x, y);
    endShape();
//    if (strokeWeight > 1) {
//      //line(x, y, x + EPSILON, y + EPSILON);
//      float sw = strokeWeight / 2;
//      rect.set(x - sw, y - sw, x + sw, y + sw);
//      strokePaint.setStyle(Style.FILL);
//      canvas.drawOval(rect, strokePaint);
//      strokePaint.setStyle(Style.STROKE);
//    } else {
//      // TODO this isn't accurate, really we need to
//      set(PApplet.round(screenX(x, y)), PApplet.round(screenY(x, y)), strokeColor);
//    }
  }


  @Override
  public void line(float x1, float y1, float x2, float y2) {
//    line.setLine(x1, y1, x2, y2);
//    strokeShape(line);
    if (stroke) {
      canvas.drawLine(x1, y1, x2, y2, strokePaint);
    }
  }


  @Override
  public void triangle(float x1, float y1, float x2, float y2,
                       float x3, float y3) {
    path.reset();
    path.moveTo(x1, y1);
    path.lineTo(x2, y2);
    path.lineTo(x3, y3);
    path.close();
    drawPath();
  }


  @Override
  public void quad(float x1, float y1, float x2, float y2,
                   float x3, float y3, float x4, float y4) {
    path.reset();
    path.moveTo(x1, y1);
    path.lineTo(x2, y2);
    path.lineTo(x3, y3);
    path.lineTo(x4, y4);
    path.close();
    drawPath();
  }



  //////////////////////////////////////////////////////////////

  // RECT


  //public void rectMode(int mode)


  //public void rect(float a, float b, float c, float d)


  @Override
  protected void rectImpl(float x1, float y1, float x2, float y2) {
//    rect.setFrame(x1, y1, x2-x1, y2-y1);
//    drawShape(rect);
    //rect.set(x1, y1, x2, y2);
    if (fill) {
      canvas.drawRect(x1, y1, x2, y2, fillPaint);
    }
    if (stroke) {
      canvas.drawRect(x1, y1, x2, y2, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // ELLIPSE


  //public void ellipseMode(int mode)


  //public void ellipse(float a, float b, float c, float d)


  @Override
  protected void ellipseImpl(float x, float y, float w, float h) {
//    ellipse.setFrame(x, y, w, h);
//    drawShape(ellipse);
    rect.set(x, y, x+w, y+h);
    if (fill) {
      canvas.drawOval(rect, fillPaint);
    }
    if (stroke) {
      canvas.drawOval(rect, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // ARC


  //public void arc(float a, float b, float c, float d,
  //                float start, float stop)


  @Override
  protected void arcImpl(float x, float y, float w, float h,
                         float start, float stop, int mode) {
    // 0 to 90 in java would be 0 to -90 for p5 renderer
    // but that won't work, so -90 to 0?

    if (stop - start >= TWO_PI) {
      ellipseImpl(x, y, w, h);

    } else {
      // Android agrees with us, so don't set start/stop negative like Java 2D
      start = start * RAD_TO_DEG;
      stop = stop * RAD_TO_DEG;

      // ok to do this because already checked for NaN
      while (start < 0) {
        start += 360;
        stop += 360;
      }
      if (start > stop) {
        float temp = start;
        start = stop;
        stop = temp;
      }

      float sweep = stop - start;
      rect.set(x, y, x+w, y+h);

      if (mode == 0) {
        if (fill) {
          canvas.drawArc(rect, start, sweep, true, fillPaint);
        }
        if (stroke) {
          canvas.drawArc(rect, start, sweep, false, strokePaint);
        }
      } else if (mode == OPEN) {
        if (fill) {
          // Android does not support stroke and fill with different color
          // after drawing the arc,draw the arc with Paint.Style.Stroke style
          // again
          canvas.drawArc(rect, start, sweep, false, fillPaint);
          canvas.drawArc(rect, start, sweep, false, strokePaint);
        }
        if (stroke) {
          canvas.drawArc(rect, start, sweep, false, strokePaint);
        }
      } else if (mode == CHORD) {
        // Draw an extra line between start angle point and end point to
        // achieve the chord
      	float endAngle = start + sweep;
      	float halfRectWidth = rect.width()/2;
        float halfRectHeight = rect.height()/2;
      	float centerX = rect.centerX();
      	float centerY = rect.centerY();

        float startX = (float) (halfRectWidth* Math.cos(Math.toRadians(start))) + centerX;
        float startY = (float) (halfRectHeight * Math.sin(Math.toRadians(start))) + centerY;
        float endX = (float) (halfRectWidth * Math.cos(Math.toRadians(endAngle))) + centerX;
        float endY = (float) (halfRectHeight * Math.sin(Math.toRadians(endAngle))) + centerY;

        if (fill) {
          // draw the fill arc
          canvas.drawArc(rect,start,sweep,false,fillPaint);
          // draw the arc round border
          canvas.drawArc(rect,start,sweep,false,strokePaint);
          // draw the straight border
          canvas.drawLine(startX,startY,endX,endY,strokePaint);
        }
        if (stroke) {
          // draw the arc
          canvas.drawArc(rect,start,sweep,false,strokePaint);
          // draw the straight border
          canvas.drawLine(startX,startY,endX,endY,strokePaint);
		    }
      } else if (mode == PIE) {
        if (fill) {
          canvas.drawArc(rect, start, sweep, true, fillPaint);
        }
        if (stroke) {
          canvas.drawArc(rect, start, sweep, true, strokePaint);
        }
      }
    }
  }



  //////////////////////////////////////////////////////////////

  // JAVA2D SHAPE/PATH HANDLING


//  protected void fillShape(Shape s) {
//    if (fillGradient) {
//      canvas.setPaint(fillGradientObject);
//      canvas.fill(s);
//    } else if (fill) {
//      canvas.setColor(fillColorObject);
//      canvas.fill(s);
//    }
//  }


//  protected void strokeShape(Shape s) {
//    if (strokeGradient) {
//      canvas.setPaint(strokeGradientObject);
//      canvas.draw(s);
//    } else if (stroke) {
//      canvas.setColor(strokeColorObject);
//      canvas.draw(s);
//    }
//  }


//  protected void drawShape(Shape s) {
//    if (fillGradient) {
//      canvas.setPaint(fillGradientObject);
//      canvas.fill(s);
//    } else if (fill) {
//      canvas.setColor(fillColorObject);
//      canvas.fill(s);
//    }
//    if (strokeGradient) {
//      canvas.setPaint(strokeGradientObject);
//      canvas.draw(s);
//    } else if (stroke) {
//      canvas.setColor(strokeColorObject);
//      canvas.draw(s);
//    }
//  }


  protected void drawPath() {
    if (fill) {
      canvas.drawPath(path, fillPaint);
    }
    if (stroke) {
      canvas.drawPath(path, strokePaint);
    }
  }



  //////////////////////////////////////////////////////////////

  // BOX


  //public void box(float size)


  @Override
  public void box(float w, float h, float d) {
    showMethodWarning("box");
  }



  //////////////////////////////////////////////////////////////

  // SPHERE


  //public void sphereDetail(int res)


  //public void sphereDetail(int ures, int vres)


  @Override
  public void sphere(float r) {
    showMethodWarning("sphere");
  }



  //////////////////////////////////////////////////////////////

  // BEZIER


  //public float bezierPoint(float a, float b, float c, float d, float t)


  //public float bezierTangent(float a, float b, float c, float d, float t)


  //protected void bezierInitCheck()


  //protected void bezierInit()


  /** Ignored (not needed) in Java 2D. */
  @Override
  public void bezierDetail(int detail) {
  }


  //public void bezier(float x1, float y1,
  //                   float x2, float y2,
  //                   float x3, float y3,
  //                   float x4, float y4)


  //public void bezier(float x1, float y1, float z1,
  //                   float x2, float y2, float z2,
  //                   float x3, float y3, float z3,
  //                   float x4, float y4, float z4)



  //////////////////////////////////////////////////////////////

  // CURVE


  //public float curvePoint(float a, float b, float c, float d, float t)


  //public float curveTangent(float a, float b, float c, float d, float t)


  /** Ignored (not needed) in Java 2D. */
  @Override
  public void curveDetail(int detail) {
  }

  //public void curveTightness(float tightness)


  //protected void curveInitCheck()


  //protected void curveInit()


  //public void curve(float x1, float y1,
  //                  float x2, float y2,
  //                  float x3, float y3,
  //                  float x4, float y4)


  //public void curve(float x1, float y1, float z1,
  //                  float x2, float y2, float z2,
  //                  float x3, float y3, float z3,
  //                  float x4, float y4, float z4)



  //////////////////////////////////////////////////////////////

  // SMOOTH


  @Override
  public void smooth(int quality) {  // ignore
    super.smooth(quality);
//    smooth = true;
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                        RenderingHints.VALUE_ANTIALIAS_ON);
//    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    strokePaint.setAntiAlias(true);
    fillPaint.setAntiAlias(true);
  }


  @Override
  public void noSmooth() {
    super.noSmooth();
//    smooth = false;
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                        RenderingHints.VALUE_ANTIALIAS_OFF);
//    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    strokePaint.setAntiAlias(false);
    fillPaint.setAntiAlias(false);
  }



  //////////////////////////////////////////////////////////////

  // IMAGE


  //public void imageMode(int mode)


  //public void image(PImage image, float x, float y)


  //public void image(PImage image, float x, float y, float c, float d)


  //public void image(PImage image,
  //                  float a, float b, float c, float d,
  //                  int u1, int v1, int u2, int v2)


  Rect imageImplSrcRect;
  RectF imageImplDstRect;
  //android.widget.ImageView imv;

  /**
   * Handle renderer-specific image drawing.
   */
  @Override
  protected void imageImpl(PImage src,
                           float x1, float y1, float x2, float y2,
                           int u1, int v1, int u2, int v2) {
    Bitmap bitmap = (Bitmap)src.getNative();

    if (bitmap != null && bitmap.isRecycled()) {
      // Let's make sure it is recreated
      bitmap = null;
    }

    if (bitmap == null && src.format == ALPHA) {
      // create an alpha bitmap for this feller
      bitmap = Bitmap.createBitmap(src.width, src.height, Config.ARGB_8888);
      int[] px = new int[src.pixels.length];
      for (int i = 0; i < px.length; i++) {
        px[i] = src.pixels[i] << 24 | 0xFFFFFF;
      }
      bitmap.setPixels(px, 0, src.width, 0, 0, src.width, src.height);
      modified = false;
      src.setNative(bitmap);
    }

    // this version's not usable because it doesn't allow you to set output w/h
//    if (src.bitmap == null) {  // format is ARGB or RGB
//      int offset = v1*src.width + u1;
//      canvas.drawBitmap(src.pixels, offset, src.width,
//                        x1, y1, u2-u1, v2-v1,
//                        src.format == ARGB, tint ? tintPaint : null);
//    } else {

    if (bitmap == null ||
        src.width != bitmap.getWidth() ||
        src.height != bitmap.getHeight()) {
      if (bitmap != null) bitmap.recycle();
      bitmap = Bitmap.createBitmap(src.width, src.height, Config.ARGB_8888);
      modified = true;
      src.setNative(bitmap);
    }

    if (src.isModified()) {
      //System.out.println("mutable, recycled = " + who.bitmap.isMutable() + ", " + who.bitmap.isRecycled());
      if (!bitmap.isMutable()) {
        bitmap.recycle();
        bitmap = Bitmap.createBitmap(src.width, src.height, Config.ARGB_8888);
        src.setNative(bitmap);
      }
      if (src.pixels != null) {
        bitmap.setPixels(src.pixels, 0, src.width, 0, 0, src.width, src.height);
      }
      src.setModified(false);
    }

    if (imageImplSrcRect == null) {
      imageImplSrcRect = new Rect(u1, v1, u2, v2);
      imageImplDstRect = new RectF(x1, y1, x2, y2);
    } else {
      imageImplSrcRect.set(u1, v1, u2, v2);
      imageImplDstRect.set(x1, y1, x2, y2);
    }
    //canvas.drawBitmap(who.bitmap, imageImplSrcRect, imageImplDstRect, tint ? tintPaint : null);
    //System.out.println(PApplet.hex(fillPaint.getColor()));
    //canvas.drawBitmap(who.bitmap, imageImplSrcRect, imageImplDstRect, fillPaint);
    //      System.out.println("drawing lower, tint = " + tint + " " + PApplet.hex(tintPaint.getColor()));
    canvas.drawBitmap(bitmap, imageImplSrcRect, imageImplDstRect, tint ? tintPaint : null);

    // If the OS things the memory is low, then recycles bitmaps automatically...
    // but I don't think it is particularly efficient, as the bitmaps are stored
    // in native heap for Android 10 and older.
    MemoryInfo mi = new MemoryInfo();
    Activity activity = parent.getSurface().getActivity();
    if (activity == null) return;
    ActivityManager activityManager = (ActivityManager) activity.getSystemService(android.content.Context.ACTIVITY_SERVICE);
    activityManager.getMemoryInfo(mi);
    if (mi.lowMemory) {
      bitmap.recycle();
      src.setNative(null);
    }
  }



  //////////////////////////////////////////////////////////////

  // SHAPE


  //public void shapeMode(int mode)


  //public void shape(PShape shape)


  //public void shape(PShape shape, float x, float y)


  //public void shape(PShape shape, float x, float y, float c, float d)


  //////////////////////////////////////////////////////////////

  // SHAPE I/O


  @Override
  public PShape loadShape(String filename) {
    String extension = PApplet.getExtension(filename);

    PShapeSVG svg = null;

    if (extension.equals("svg")) {
      svg = new PShapeSVG(parent.loadXML(filename));

    } else if (extension.equals("svgz")) {
      try {
        InputStream input = new GZIPInputStream(parent.createInput(filename));
        XML xml = new XML(input);
        svg = new PShapeSVG(xml);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      PGraphics.showWarning("Unsupported format");
    }

    return svg;
  }


  //////////////////////////////////////////////////////////////

  // TEXT ATTRIBTUES


  //public void textAlign(int align)


  //public void textAlign(int alignX, int alignY)


//  public float textAscent() {
//    if (textFont == null) {
//      defaultFontOrDeath("textAscent");
//    }
////    Font font = textFont.getFont();
//    Typeface font = textFont.getTypeface();
//    if (font == null) {
//      return super.textAscent();
//    }
//    return fillPaint.ascent();
//  }


//  public float textDescent() {
//    if (textFont == null) {
//      defaultFontOrDeath("textDescent");
//    }
////    Font font = textFont.getFont();
//    Typeface font = textFont.getTypeface();
//    if (font == null) {
//      return super.textDescent();
//    }
//    return fillPaint.descent();
//  }


  @Override
  public void textFont(PFont which) {
    super.textFont(which);
    fillPaint.setTypeface((Typeface) which.getNative());
    fillPaint.setTextSize(which.getDefaultSize());
  }


  @Override
  public void textFont(PFont which, float size) {
    super.textFont(which, size);
    fillPaint.setTypeface((Typeface) which.getNative());
    fillPaint.setTextSize(size);
  }


  //public void textLeading(float leading)


  //public void textMode(int mode)


  @Override
  protected boolean textModeCheck(int mode) {
    return mode == MODEL;
  }


  /**
   * Same as parent, but override for native version of the font.
   * <p/>
   * Also gets called by textFont, so the metrics
   * will get recorded properly.
   */
  @Override
  public void textSize(float size) {
    if (textFont == null) {
      defaultFontOrDeath("textSize", size);
    }

    Typeface font = (Typeface) textFont.getNative();
    if (font != null) {
      fillPaint.setTextSize(size);
    }

    handleTextSize(size);
  }


  protected void beginTextScreenMode() {
    loadPixels();
  }

  protected void endTextScreenMode() {
    updatePixels();
  }

  //public float textWidth(char c)


  //public float textWidth(String str)


  @Override
  protected float textWidthImpl(char buffer[], int start, int stop) {
//    Font font = textFont.getFont();
    Typeface font = (Typeface) textFont.getNative();
    if (font == null) {
      return super.textWidthImpl(buffer, start, stop);
    }
    // maybe should use one of the newer/fancier functions for this?
    int length = stop - start;
//    FontMetrics metrics = canvas.getFontMetrics(font);
//    return metrics.charsWidth(buffer, start, length);
    return fillPaint.measureText(buffer, start, length);
  }



  //////////////////////////////////////////////////////////////

  // TEXT

  // None of the variations of text() are overridden from PGraphics.



  //////////////////////////////////////////////////////////////

  // TEXT IMPL


  //protected void textLineAlignImpl(char buffer[], int start, int stop,
  //                                 float x, float y)


  @Override
  protected void textLineImpl(char buffer[], int start, int stop,
                              float x, float y) {
    Typeface font = (Typeface) textFont.getNative();
    if (font == null) {
      showWarning("Inefficient font rendering: use createFont() with a TTF/OTF instead of loadFont().");
      //new Exception().printStackTrace(System.out);
      super.textLineImpl(buffer, start, stop, x, y);
      return;
    }

    /*
    // save the current setting for text smoothing. note that this is
    // different from the smooth() function, because the font smoothing
    // is controlled when the font is created, not now as it's drawn.
    // fixed a bug in 0116 that handled this incorrectly.
    Object textAntialias =
      g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);

    // override the current text smoothing setting based on the font
    // (don't change the global smoothing settings)
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        textFont.smooth ?
                        RenderingHints.VALUE_ANTIALIAS_ON :
                        RenderingHints.VALUE_ANTIALIAS_OFF);
    */

//    Object antialias =
//      canvas.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
//    if (antialias == null) {
//      // if smooth() and noSmooth() not called, this will be null (0120)
//      antialias = RenderingHints.VALUE_ANTIALIAS_DEFAULT;
//    }

    // override the current smoothing setting based on the font
    // also changes global setting for antialiasing, but this is because it's
    // not possible to enable/disable them independently in some situations.
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                            textFont.smooth ?
//                            RenderingHints.VALUE_ANTIALIAS_ON :
//                            RenderingHints.VALUE_ANTIALIAS_OFF);
    fillPaint.setAntiAlias(textFont.isSmooth());

    //System.out.println("setting frac metrics");
    //g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
    //                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

//    canvas.setColor(fillColorObject);
    int length = stop - start;
//    canvas.drawChars(buffer, start, length, (int) (x + 0.5f), (int) (y + 0.5f));
    canvas.drawText(buffer, start, length, x, y, fillPaint);

    // return to previous smoothing state if it was changed
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    fillPaint.setAntiAlias(0 < smooth);

//    textX = x + textWidthImpl(buffer, start, stop);
//    textY = y;
//    textZ = 0;  // this will get set by the caller if non-zero
  }



  //////////////////////////////////////////////////////////////

  // MATRIX STACK


  @Override
  public void pushMatrix() {
    if (transformCount == transformStack.length) {
      throw new RuntimeException("pushMatrix() cannot use push more than " +
                                 transformStack.length + " times");
    }
    transform.get(transformStack[transformCount]);
    transformCount++;
//    canvas.save(Canvas.MATRIX_SAVE_FLAG);
  }


  @Override
  public void popMatrix() {
    if (transformCount == 0) {
      throw new RuntimeException("missing a popMatrix() " +
                                 "to go with that pushMatrix()");
    }
    transformCount--;
    transform.set(transformStack[transformCount]);
    updateTmpMatrix();
    canvas.setMatrix(tmpMatrix);
//    canvas.restore();
  }


  //////////////////////////////////////////////////////////////

  // MATRIX TRANSFORMS


  @Override
  public void translate(float tx, float ty) {
    transform.translate(tx, ty);
    canvas.translate(tx, ty);
  }


  @Override
  public void rotate(float angle) {
    transform.rotate(angle * RAD_TO_DEG);
    canvas.rotate(angle * RAD_TO_DEG);
  }


  @Override
  public void rotateX(float angle) {
    showDepthWarning("rotateX");
  }


  @Override
  public void rotateY(float angle) {
    showDepthWarning("rotateY");
  }


  @Override
  public void rotateZ(float angle) {
    showDepthWarning("rotateZ");
  }


  @Override
  public void rotate(float angle, float vx, float vy, float vz) {
    showVariationWarning("rotate");
  }


  @Override
  public void scale(float s) {
    transform.scale(s, s);
    canvas.scale(s, s);
  }


  @Override
  public void scale(float sx, float sy) {
    transform.scale(sx, sy);
    canvas.scale(sx, sy);
  }


  @Override
  public void scale(float sx, float sy, float sz) {
    showDepthWarningXYZ("scale");
  }


  @Override
  public void shearX(float angle) {
    float t = (float) Math.tan(angle);
    transform.apply(1, t, 0, 0, 1, 0);
    canvas.skew(t, 0);
  }


  @Override
  public void shearY(float angle) {
    float t = (float) Math.tan(angle);
    transform.apply(1, 0, 0, t, 1, 0);
    canvas.skew(0, t);
  }


  //////////////////////////////////////////////////////////////

  // MATRIX MORE


  @Override
  public void resetMatrix() {
    transform.reset();
    canvas.setMatrix(null);
  }


  //public void applyMatrix(PMatrix2D source)


  @Override
  public void applyMatrix(float n00, float n01, float n02,
                          float n10, float n11, float n12) {
    transform.apply(n00, n01, n02, n10, n11, n12);
    updateTmpMatrix();
    canvas.concat(tmpMatrix);
  }


  //public void applyMatrix(PMatrix3D source)


  @Override
  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    showVariationWarning("applyMatrix");
  }



  //////////////////////////////////////////////////////////////

  // MATRIX GET/SET


  @Override
  public PMatrix getMatrix() {
    return getMatrix((PMatrix2D) null);
  }


  @Override
  public PMatrix2D getMatrix(PMatrix2D target) {
    if (target == null) {
      target = new PMatrix2D();
    }
    target.set(transform);
    return target;
  }


  @Override
  public PMatrix3D getMatrix(PMatrix3D target) {
    showVariationWarning("getMatrix");
    return target;
  }


  //public void setMatrix(PMatrix source)


  @Override
  public void setMatrix(PMatrix2D source) {
    transform.set(source);
    updateTmpMatrix();
    canvas.setMatrix(tmpMatrix);
  }


  @Override
  public void setMatrix(PMatrix3D source) {
    showVariationWarning("setMatrix");
  }


  @Override
  public void printMatrix() {
    getMatrix((PMatrix2D) null).print();
  }


  protected Matrix getMatrixImp() {
    Matrix m = new Matrix();
    updateTmpMatrix();
    m.set(tmpMatrix);
    return m;
//    return canvas.getMatrix();
  }


  protected void updateTmpMatrix() {
    tmpArray[0] = transform.m00;
    tmpArray[1] = transform.m01;
    tmpArray[2] = transform.m02;
    tmpArray[3] = transform.m10;
    tmpArray[4] = transform.m11;
    tmpArray[5] = transform.m12;
    tmpArray[6] = 0;
    tmpArray[7] = 0;
    tmpArray[8] = 1;
    tmpMatrix.setValues(tmpArray);
  }


  //////////////////////////////////////////////////////////////

  // CAMERA and PROJECTION

  // Inherit the plaintive warnings from PGraphics


  //public void beginCamera()
  //public void endCamera()
  //public void camera()
  //public void camera(float eyeX, float eyeY, float eyeZ,
  //                   float centerX, float centerY, float centerZ,
  //                   float upX, float upY, float upZ)
  //public void printCamera()

  //public void ortho()
  //public void ortho(float left, float right,
  //                  float bottom, float top,
  //                  float near, float far)
  //public void perspective()
  //public void perspective(float fov, float aspect, float near, float far)
  //public void frustum(float left, float right,
  //                    float bottom, float top,
  //                    float near, float far)
  //public void printProjection()



  //////////////////////////////////////////////////////////////

  // SCREEN and MODEL transforms

  float[] screenPoint;

  @Override
  public float screenX(float x, float y) {
//    canvas.getTransform().getMatrix(transform);
//    return (float)transform[0]*x + (float)transform[2]*y + (float)transform[4];
    if (screenPoint == null) {
      screenPoint = new float[2];
    }
    screenPoint[0] = x;
    screenPoint[1] = y;
//    canvas.getMatrix().mapPoints(screenPoint);
    getMatrixImp().mapPoints(screenPoint);
    return screenPoint[0];
  }


  @Override
  public float screenY(float x, float y) {
//    canvas.getTransform().getMatrix(transform);
//    return (float)transform[1]*x + (float)transform[3]*y + (float)transform[5];
    if (screenPoint == null) {
      screenPoint = new float[2];
    }
    screenPoint[0] = x;
    screenPoint[1] = y;
//    canvas.getMatrix().mapPoints(screenPoint);
    getMatrixImp().mapPoints(screenPoint);
    return screenPoint[1];
  }


  @Override
  public float screenX(float x, float y, float z) {
    showDepthWarningXYZ("screenX");
    return 0;
  }


  @Override
  public float screenY(float x, float y, float z) {
    showDepthWarningXYZ("screenY");
    return 0;
  }


  @Override
  public float screenZ(float x, float y, float z) {
    showDepthWarningXYZ("screenZ");
    return 0;
  }


  //public float modelX(float x, float y, float z)


  //public float modelY(float x, float y, float z)


  //public float modelZ(float x, float y, float z)



  //////////////////////////////////////////////////////////////

  // STYLE

  // pushStyle(), popStyle(), style() and getStyle() inherited.



  //////////////////////////////////////////////////////////////

  // STROKE CAP/JOIN/WEIGHT


  @Override
  public void strokeCap(int cap) {
    super.strokeCap(cap);

    if (strokeCap == ROUND) {
      strokePaint.setStrokeCap(Paint.Cap.ROUND);
    } else if (strokeCap == PROJECT) {
      strokePaint.setStrokeCap(Paint.Cap.SQUARE);
    } else {
      strokePaint.setStrokeCap(Paint.Cap.BUTT);
    }
  }


  @Override
  public void strokeJoin(int join) {
    super.strokeJoin(join);

    if (strokeJoin == MITER) {
      strokePaint.setStrokeJoin(Paint.Join.MITER);
    } else if (strokeJoin == ROUND) {
      strokePaint.setStrokeJoin(Paint.Join.ROUND);
    } else {
      strokePaint.setStrokeJoin(Paint.Join.BEVEL);
    }
  }


  @Override
  public void strokeWeight(float weight) {
    super.strokeWeight(weight);
    strokePaint.setStrokeWidth(weight);
  }



  //////////////////////////////////////////////////////////////

  // STROKE

  // noStroke() and stroke() inherited from PGraphics.


  @Override
  protected void strokeFromCalc() {
    super.strokeFromCalc();
//    strokeColorObject = new Color(strokeColor, true);
    strokePaint.setColor(strokeColor);
//    strokeGradient = false;
    strokePaint.setShader(null);
  }



  //////////////////////////////////////////////////////////////

  // TINT

  // noTint() and tint() inherited from PGraphics.


  @Override
  protected void tintFromCalc() {
    super.tintFromCalc();
    tintPaint.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.MULTIPLY));
  }



  //////////////////////////////////////////////////////////////

  // FILL

  // noFill() and fill() inherited from PGraphics.


  @Override
  protected void fillFromCalc() {
    super.fillFromCalc();
//    fillColorObject = new Color(fillColor, true);
    fillPaint.setColor(fillColor);
//    fillGradient = false;
    fillPaint.setShader(null);
  }



  //////////////////////////////////////////////////////////////

  // MATERIAL PROPERTIES


  //public void ambient(int rgb)
  //public void ambient(float gray)
  //public void ambient(float x, float y, float z)
  //protected void ambientFromCalc()
  //public void specular(int rgb)
  //public void specular(float gray)
  //public void specular(float x, float y, float z)
  //protected void specularFromCalc()
  //public void shininess(float shine)
  //public void emissive(int rgb)
  //public void emissive(float gray)
  //public void emissive(float x, float y, float z )
  //protected void emissiveFromCalc()



  //////////////////////////////////////////////////////////////

  // LIGHTS


  //public void lights()
  //public void noLights()
  //public void ambientLight(float red, float green, float blue)
  //public void ambientLight(float red, float green, float blue,
  //                         float x, float y, float z)
  //public void directionalLight(float red, float green, float blue,
  //                             float nx, float ny, float nz)
  //public void pointLight(float red, float green, float blue,
  //                       float x, float y, float z)
  //public void spotLight(float red, float green, float blue,
  //                      float x, float y, float z,
  //                      float nx, float ny, float nz,
  //                      float angle, float concentration)
  //public void lightFalloff(float constant, float linear, float quadratic)
  //public void lightSpecular(float x, float y, float z)
  //protected void lightPosition(int num, float x, float y, float z)
  //protected void lightDirection(int num, float x, float y, float z)



  //////////////////////////////////////////////////////////////

  // BACKGROUND

  // background() methods inherited from PGraphics, along with the
  // PImage version of backgroundImpl(), since it just calls set().


  //public void backgroundImpl(PImage image)


//  int[] clearPixels;

  @Override
  public void backgroundImpl() {
    canvas.drawColor(backgroundColor);

//    if (backgroundAlpha) {
//      WritableRaster raster = ((BufferedImage) image).getRaster();
//      if ((clearPixels == null) || (clearPixels.length < width)) {
//        clearPixels = new int[width];
//      }
//      java.util.Arrays.fill(clearPixels, backgroundColor);
//      for (int i = 0; i < height; i++) {
//        raster.setDataElements(0, i, width, 1, clearPixels);
//      }
//    } else {
//      //new Exception().printStackTrace(System.out);
//      // in case people do transformations before background(),
//      // need to handle this with a push/reset/pop
//      pushMatrix();
//      resetMatrix();
//      canvas.setColor(new Color(backgroundColor)); //, backgroundAlpha));
//      canvas.fillRect(0, 0, width, height);
//      popMatrix();
//    }
  }



  //////////////////////////////////////////////////////////////

  // COLOR MODE

  // All colorMode() variations are inherited from PGraphics.



  //////////////////////////////////////////////////////////////

  // COLOR CALC

  // colorCalc() and colorCalcARGB() inherited from PGraphics.



  //////////////////////////////////////////////////////////////

  // COLOR DATATYPE STUFFING

  // final color() variations inherited.



  //////////////////////////////////////////////////////////////

  // COLOR DATATYPE EXTRACTION

  // final methods alpha, red, green, blue,
  // hue, saturation, and brightness all inherited.



  //////////////////////////////////////////////////////////////

  // COLOR DATATYPE INTERPOLATION

  // both lerpColor variants inherited.



  //////////////////////////////////////////////////////////////

  // BEGIN/END RAW


  @Override
  public void beginRaw(PGraphics recorderRaw) {
    showMethodWarning("beginRaw");
  }


  @Override
  public void endRaw() {
    showMethodWarning("endRaw");
  }



  //////////////////////////////////////////////////////////////

  // WARNINGS and EXCEPTIONS

  // showWarning and showException inherited.



  //////////////////////////////////////////////////////////////

  // RENDERER SUPPORT QUERIES


  //public boolean displayable()  // true


  //public boolean is2D()  // true


  //public boolean is3D()  // false



  //////////////////////////////////////////////////////////////

  // PIMAGE METHODS


  // getImage, setCache, getCache, removeCache, isModified, setModified


  @Override
  public void loadPixels() {
    if (bitmap == null) return;

    if ((pixels == null) || (pixels.length != width * height)) {
      pixels = new int[width * height];
    }
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(0, 0, width, height, pixels);
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
  }


  /**
   * Update the pixels[] buffer to the PGraphics image.
   * <P>
   * Unlike in PImage, where updatePixels() only requests that the
   * update happens, in PGraphicsJava2D, this will happen immediately.
   */
  @Override
  public void updatePixels() {
    if (bitmap == null) return;

//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.setDataElements(0, 0, width, height, pixels);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
  }


  /**
   * Update the pixels[] buffer to the PGraphics image.
   * <P>
   * Unlike in PImage, where updatePixels() only requests that the
   * update happens, in PGraphicsJava2D, this will happen immediately.
   */
  @Override
  public void updatePixels(int x, int y, int c, int d) {
    //if ((x == 0) && (y == 0) && (c == width) && (d == height)) {
    if ((x != 0) || (y != 0) || (c != width) || (d != height)) {
      // Show a warning message, but continue anyway.
      showVariationWarning("updatePixels(x, y, w, h)");
    }
    updatePixels();
  }


  @Override
  public void resize(int wide, int high) {
    showMethodWarning("resize");
  }



  //////////////////////////////////////////////////////////////

  // GET/SET


  static int getset[] = new int[1];


  @Override
  public int get(int x, int y) {
    if ((bitmap == null) || (x < 0) || (y < 0) || (x >= width) || (y >= height)) return 0;
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(x, y, getset);
//    return getset[0];
    return bitmap.getPixel(x, y);
  }


  //public PImage get(int x, int y, int w, int h)


//  @Override
//  public PImage getImpl(int x, int y, int w, int h) {
//    PImage output = new PImage();
//    output.width = w;
//    output.height = h;
//    output.parent = parent;
//
//    Bitmap bitsy = Bitmap.createBitmap(bitmap, x, y, w, h);
//    // guessing it's more efficient to use Bitmap instead of pixels[]
//    //bitsy.getPixels(output.pixels, 0, w, 0, 0, w, h);
//    output.bitmap = bitsy;
//
//    return output;
//  }


  @Override
  public PImage get() {
    return get(0, 0, width, height);
  }


  @Override
  public void set(int x, int y, int argb) {
    if ((bitmap == null) || (x < 0) || (y < 0) || (x >= width) || (y >= height)) return;
//    getset[0] = argb;
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.setDataElements(x, y, getset);
    bitmap.setPixel(x, y, argb);
  }


  @Override
  public void set(int x, int y, PImage src) {
    if (src.format == ALPHA) {
      // set() doesn't really make sense for an ALPHA image, since it
      // directly replaces pixels and does no blending.
      throw new RuntimeException("set() not available for ALPHA images");
    }

    Bitmap bitmap = (Bitmap)src.getNative();
    if (bitmap == null) {
      bitmap = Bitmap.createBitmap(src.width, src.height, Config.ARGB_8888);
      src.setNative(bitmap);
      src.setModified();
    }
    if (src.width != bitmap.getWidth() ||
      src.height != bitmap.getHeight()) {
      bitmap.recycle();
      bitmap = Bitmap.createBitmap(src.width, src.height, Config.ARGB_8888);
      src.setNative(bitmap);
      src.setModified();
    }
    if (src.isModified()) {
      if (!bitmap.isMutable()) {
        bitmap.recycle();
        bitmap = Bitmap.createBitmap(src.width, src.height, Config.ARGB_8888);
        setNative(bitmap);
      }
      bitmap.setPixels(src.pixels, 0, src.width, 0, 0, src.width, src.height);
      src.setModified(false);
    }
    // set() happens in screen coordinates, so need to clear the ctm
//    canvas.save(Canvas.MATRIX_SAVE_FLAG);
    pushMatrix();
    canvas.setMatrix(null);  // set to identity
    canvas.drawBitmap(bitmap, x, y, null);
    popMatrix();
//    canvas.restore();
  }


  // elaborate but silly version, since android will happily do this work
//  private Rect setImplSrcRect;
//  private Rect setImplDstRect;
//
//  protected void setImpl(int dx, int dy, int sx, int sy, int sw, int sh,
//                         PImage src) {
//    if (setImplSrcRect == null) {
//      setImplSrcRect = new Rect(sx, sy, sx+sw, sy+sh);
//      setImplDstRect = new Rect(dx, dy, dx+sw, dy+sh);
//    } else {
//      setImplSrcRect.set(sx, sy, sx+sw, sy+sh);
//      setImplDstRect.set(dx, dy, dx+sw, dy+sh);
//    }
//    // set() happens in screen coordinates, so need to nuke the ctm
//    canvas.save(Canvas.MATRIX_SAVE_FLAG);
//    canvas.setMatrix(null);  // set to identity
//    canvas.drawBitmap(src.image, setImplSrcRect, setImplDstRect, null);
//    canvas.restore();
//  }

    // PImage version
//    int srcOffset = sy * src.width + sx;
//    int dstOffset = dy * width + dx;
//
//    for (int y = sy; y < sy + sh; y++) {
//      System.arraycopy(src.pixels, srcOffset, pixels, dstOffset, sw);
//      srcOffset += src.width;
//      dstOffset += width;
//    }
//    updatePixelsImpl(sx, sy, sx+sw, sy+sh);

    // PGraphicsJava2D version
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    if ((sx == 0) && (sy == 0) && (sw == src.width) && (sh == src.height)) {
//      raster.setDataElements(dx, dy, src.width, src.height, src.pixels);
//    } else {
//      // TODO Optimize, incredibly inefficient to reallocate this much memory
//      PImage temp = src.get(sx, sy, sw, sh);
//      raster.setDataElements(dx, dy, temp.width, temp.height, temp.pixels);
//    }



  //////////////////////////////////////////////////////////////

  // MASK


  @Override
  public void mask(int alpha[]) {
    showMethodWarning("mask");
  }


  @Override
  public void mask(PImage alpha) {
    showMethodWarning("mask");
  }



  //////////////////////////////////////////////////////////////

  // FILTER

  // Because the PImage versions call loadPixels() and
  // updatePixels(), no need to override anything here.


  //public void filter(int kind)


  //public void filter(int kind, float param)



  //////////////////////////////////////////////////////////////

  // COPY


  @Override
  public void copy(int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    if (bitmap == null) return;

//    Bitmap bitsy = Bitmap.createBitmap(image, sx, sy, sw, sh);
//    rect.set(dx, dy, dx + dw, dy + dh);
//    canvas.drawBitmap(bitsy,
    rect.set(sx, sy, sx+sw, sy+sh);
    Rect src = new Rect(dx, dy, dx+dw, dy+dh);
    canvas.drawBitmap(bitmap, src, rect, null);

//    if ((sw != dw) || (sh != dh)) {
//      // use slow version if changing size
//      copy(this, sx, sy, sw, sh, dx, dy, dw, dh);
//
//    } else {
//      dx = dx - sx;  // java2d's "dx" is the delta, not dest
//      dy = dy - sy;
//      canvas.copyArea(sx, sy, sw, sh, dx, dy);
//    }
  }


//  public void copy(PImage src,
//                   int sx1, int sy1, int sx2, int sy2,
//                   int dx1, int dy1, int dx2, int dy2) {
//    loadPixels();
//    super.copy(src, sx1, sy1, sx2, sy2, dx1, dy1, dx2, dy2);
//    updatePixels();
//  }



  //////////////////////////////////////////////////////////////

  // BLEND


//  static public int blendColor(int c1, int c2, int mode)


//  public void blend(int sx, int sy, int sw, int sh,
//                    int dx, int dy, int dw, int dh, int mode)


//  public void blend(PImage src,
//                    int sx, int sy, int sw, int sh,
//                    int dx, int dy, int dw, int dh, int mode)



  //////////////////////////////////////////////////////////////

  // SAVE


//  public void save(String filename) {
//    loadPixels();
//    super.save(filename);
//  }
}