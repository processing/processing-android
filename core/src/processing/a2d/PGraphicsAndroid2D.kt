/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */ /*
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
package processing.a2d

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Environment
import android.view.SurfaceHolder
import processing.android.AppComponent
import processing.core.*
import processing.data.XML
import java.io.*
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream

/**
 * Subclass for PGraphics that implements the graphics API using
 * the Android 2D graphics model. Similar tradeoffs to JAVA2D mode
 * with the original (desktop) version of Processing.
 */
open class PGraphicsAndroid2D : PGraphics() {
    @JvmField
    var canvas // like g2 for PGraphicsJava2D
            : Canvas? = null

    /// break the shape at the next vertex (next vertex() call is a moveto())
    @JvmField
    var breakShape = false

    /// coordinates for internal curve calculation
    @JvmField
    var curveCoordX: FloatArray? = null

    lateinit var curveCoordY: FloatArray
    lateinit var curveDrawX: FloatArray
    lateinit var curveDrawY: FloatArray

    protected var transformStack: Array<FloatArray>
    var transform: PMatrix2D
    protected var transformMatrix: Matrix
    protected var transformArray: FloatArray

    @JvmField
    var transformCount = 0

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
    var mpath: Path? = null

    /** Temporary rectangle object.  */
    var rect: RectF

    //  protected Color tintColorObject;
    //  protected Color fillColorObject;
    //  public boolean fillGradient;
    //  public Paint fillGradientObject;
    //  protected Color strokeColorObject;
    //  public boolean strokeGradient;
    //  public Paint strokeGradientObject;
    var fillPaint: Paint
    var strokePaint: Paint
    var tintPaint: Paint

    /**
     * Marks when changes to the size have occurred, so that the backing bitmap
     * can be recreated.
     */
    protected var sized = false

    /**
     * Marks when some changes have occurred, to the surface view.
     */
    protected var changed = false

    //public void setParent(PApplet parent)
    //public void setPrimary(boolean primary)
    //public void setPath(String path)
    override fun surfaceChanged() {
        changed = true
    }

    override fun setSize(iwidth: Int, iheight: Int) {
        sized = iwidth != width || iheight != height
        super.setSize(iwidth, iheight)
    }

    override fun dispose() {
        if (bitmap != null) bitmap.recycle()
    }

    override fun createSurface(component: AppComponent, holder: SurfaceHolder?, reset: Boolean): PSurface {  // ignore
        return PSurfaceAndroid2D(this, component, holder)
    }

    //////////////////////////////////////////////////////////////
    // FRAME
    @SuppressLint("NewApi")
    protected fun checkCanvas(): Canvas? {
        if ((canvas == null || sized) && (useBitmap || !primaryGraphics)) {
            if (bitmap == null || bitmap.width * bitmap.height < width * height || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                if (bitmap != null) bitmap.recycle()
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            } else {
                // reconfigure is only available in API level 19 or higher.
                bitmap.reconfigure(width, height, bitmap.config)
            }
            canvas = Canvas(bitmap)
            sized = false
        }
        restoreSurface()
        return canvas
    }

    override fun beginDraw() {
        canvas = checkCanvas()
        checkSettings()
        resetMatrix() // reset model matrix

        // reset vertices
        vertexCount = 0
    }

    override fun endDraw() {
        if (bitmap == null) return
        if (primaryGraphics) {
            val holder = parent.surface.surfaceHolder
            if (holder != null) {
                var screen: Canvas? = null
                try {
                    screen = holder.lockCanvas(null)
                    screen?.drawBitmap(bitmap, Matrix(), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (screen != null) {
                        try {
                            holder.unlockCanvasAndPost(screen)
                        } catch (ex: IllegalStateException) {
                        } catch (ex: IllegalArgumentException) {
                        }
                    }
                }
            }
        } else {
            // TODO this is probably overkill for most tasks...
            loadPixels()
        }

        // Marking as modified, and then calling updatePixels() in
        // the super class, which just sets the mx1, my1, mx2, my2
        // coordinates of the modified area. This avoids doing the
        // full copy of the pixels to the surface in this.updatePixels().
        setModified()
        super.updatePixels()
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
    override fun beginShape(kind: Int) {
        //super.beginShape(kind);
        shape = kind
        vertexCount = 0
        curveVertexCount = 0

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
    override fun texture(image: PImage) {
        showMethodWarning("texture")
    }

    override fun vertex(x: Float, y: Float) {
        // POLYGON and POINTS are broken out for efficiency
        if (shape == PConstants.POLYGON) {
//      if (path == null) {
//        path = new Path();
//        path.moveTo(x, y);
            //if (pathReset) {
            if (vertexCount == 0) {
                mpath!!.reset()
                mpath!!.moveTo(x, y)
                vertexCount = 1
                //        pathReset = false;
            } else if (breakShape) {
                mpath!!.moveTo(x, y)
                breakShape = false
            } else {
                mpath!!.lineTo(x, y)
            }

            // this is way too slow, otherwise what's the point of using beginShape()
//    } else if (shape == POINTS) {
//      point(x, y);
        } else {
            curveVertexCount = 0
            if (vertexCount == vertices.size) {
                val temp = Array(vertexCount shl 1) { FloatArray(VERTEX_FIELD_COUNT) }
                System.arraycopy(vertices, 0, temp, 0, vertexCount)
                vertices = temp
            }
            // not everyone needs this, but just easier to store rather
            // than adding another moving part to the code...
            vertices[vertexCount][PConstants.X] = x
            vertices[vertexCount][PConstants.Y] = y
            vertexCount++
            when (shape) {
                PConstants.POINTS -> {
                }
                PConstants.LINES -> if (vertexCount % 2 == 0) {
                    line(vertices[vertexCount - 2][PConstants.X],
                            vertices[vertexCount - 2][PConstants.Y], x, y)
                    vertexCount = 0
                }
                PConstants.LINE_STRIP, PConstants.LINE_LOOP -> if (vertexCount >= 2) {
                    line(vertices[vertexCount - 2][PConstants.X],
                            vertices[vertexCount - 2][PConstants.Y], x, y)
                }
                PConstants.TRIANGLES -> if (vertexCount % 3 == 0) {
                    triangle(vertices[vertexCount - 3][PConstants.X],
                            vertices[vertexCount - 3][PConstants.Y],
                            vertices[vertexCount - 2][PConstants.X],
                            vertices[vertexCount - 2][PConstants.Y],
                            x, y)
                    vertexCount = 0
                }
                PConstants.TRIANGLE_STRIP -> if (vertexCount >= 3) {
                    triangle(vertices[vertexCount - 2][PConstants.X],
                            vertices[vertexCount - 2][PConstants.Y],
                            x,  //vertices[vertexCount - 1][X],
                            y,  //vertices[vertexCount - 1][Y],
                            vertices[vertexCount - 3][PConstants.X],
                            vertices[vertexCount - 3][PConstants.Y])
                }
                PConstants.TRIANGLE_FAN -> if (vertexCount >= 3) {
                    triangle(vertices[0][PConstants.X],
                            vertices[0][PConstants.Y],
                            vertices[vertexCount - 2][PConstants.X],
                            vertices[vertexCount - 2][PConstants.Y],
                            x, y)
                }
                PConstants.QUAD, PConstants.QUADS -> if (vertexCount % 4 == 0) {
                    quad(vertices[vertexCount - 4][PConstants.X],
                            vertices[vertexCount - 4][PConstants.Y],
                            vertices[vertexCount - 3][PConstants.X],
                            vertices[vertexCount - 3][PConstants.Y],
                            vertices[vertexCount - 2][PConstants.X],
                            vertices[vertexCount - 2][PConstants.Y],
                            x, y)
                    vertexCount = 0
                }
                PConstants.QUAD_STRIP ->         // 0---2---4
                    // |   |   |
                    // 1---3---5
                    if (vertexCount >= 4 && vertexCount % 2 == 0) {
                        quad(vertices[vertexCount - 4][PConstants.X],
                                vertices[vertexCount - 4][PConstants.Y],
                                vertices[vertexCount - 2][PConstants.X],
                                vertices[vertexCount - 2][PConstants.Y],
                                x, y,
                                vertices[vertexCount - 3][PConstants.X],
                                vertices[vertexCount - 3][PConstants.Y])
                    }
            }
        }
    }

    override fun vertex(x: Float, y: Float, z: Float) {
        showDepthWarningXYZ("vertex")
    }

    override fun vertex(x: Float, y: Float, u: Float, v: Float) {
        showVariationWarning("vertex(x, y, u, v)")
    }

    override fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        showDepthWarningXYZ("vertex")
    }

    override fun breakShape() {
        breakShape = true
    }

    override fun endShape(mode: Int) {
        if (shape == PConstants.POINTS && stroke && vertexCount > 0) {
            val m = matrixImp
            if (strokeWeight == 1f && m.isIdentity) {
                if (screenPoint == null) {
                    screenPoint = FloatArray(2)
                }
                for (i in 0 until vertexCount) {
                    screenPoint!![0] = vertices[i][PConstants.X]
                    screenPoint!![1] = vertices[i][PConstants.Y]
                    m.mapPoints(screenPoint)
                    set(PApplet.round(screenPoint!![0]), PApplet.round(screenPoint!![1]), strokeColor)
                    val x = vertices[i][PConstants.X]
                    val y = vertices[i][PConstants.Y]
                    set(PApplet.round(screenX(x, y)), PApplet.round(screenY(x, y)), strokeColor)
                }
            } else {
                val sw = strokeWeight / 2
                // temporarily use the stroke Paint as a fill
                strokePaint.style = Paint.Style.FILL
                for (i in 0 until vertexCount) {
                    val x = vertices[i][PConstants.X]
                    val y = vertices[i][PConstants.Y]
                    rect[x - sw, y - sw, x + sw] = y + sw
                    canvas!!.drawOval(rect, strokePaint)
                }
                strokePaint.style = Paint.Style.STROKE
            }
        } else if (shape == PConstants.POLYGON) {
            if (!mpath!!.isEmpty()) {
                if (mode == PConstants.CLOSE) {
                    mpath!!.close()
                }
                drawPath()
            }
        } else if (shape == PConstants.LINE_LOOP && vertexCount >= 2) {
            line(vertices[vertexCount - 1][PConstants.X],
                    vertices[vertexCount - 1][PConstants.Y],
                    vertices[0][PConstants.X],
                    vertices[0][PConstants.Y])
        }
        shape = 0
    }

    //////////////////////////////////////////////////////////////
    // CLIPPING
    override fun clipImpl(x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas!!.clipRect(x1, y1, x2, y2)
    }

    override fun noClip() {
        canvas!!.clipRect(0f, 0f, width.toFloat(), height.toFloat(), Region.Op.REPLACE)
    }

    //////////////////////////////////////////////////////////////
    // BEZIER VERTICES
    override fun bezierVertex(x1: Float, y1: Float,
                              x2: Float, y2: Float,
                              x3: Float, y3: Float) {
        // will check to make sure that vertexCount > 0
        bezierVertexCheck()
        mpath!!.cubicTo(x1, y1, x2, y2, x3, y3)
    }

    override fun bezierVertex(x2: Float, y2: Float, z2: Float,
                              x3: Float, y3: Float, z3: Float,
                              x4: Float, y4: Float, z4: Float) {
        showDepthWarningXYZ("bezierVertex")
    }

    //////////////////////////////////////////////////////////////
    // QUADRATIC BEZIER VERTICES
    override fun quadraticVertex(ctrlX: Float, ctrlY: Float,
                                 endX: Float, endY: Float) {
        bezierVertexCheck()
        mpath!!.quadTo(ctrlX, ctrlY, endX, endY)
    }

    override fun quadraticVertex(x2: Float, y2: Float, z2: Float,
                                 x4: Float, y4: Float, z4: Float) {
        showDepthWarningXYZ("quadVertex")
    }

    //////////////////////////////////////////////////////////////
    // CURVE VERTICES
    override fun curveVertexCheck() {
        super.curveVertexCheck()
        if (curveCoordX == null) {
            curveCoordX = FloatArray(4)
            curveCoordY = FloatArray(4)
            curveDrawX = FloatArray(4)
            curveDrawY = FloatArray(4)
        }
    }

    override fun curveVertexSegment(x1: Float, y1: Float,
                                    x2: Float, y2: Float,
                                    x3: Float, y3: Float,
                                    x4: Float, y4: Float) {
        curveCoordX!![0] = x1
        curveCoordY[0] = y1
        curveCoordX!![1] = x2
        curveCoordY[1] = y2
        curveCoordX!![2] = x3
        curveCoordY[2] = y3
        curveCoordX!![3] = x4
        curveCoordY[3] = y4
        curveToBezierMatrix.mult(curveCoordX, curveDrawX)
        curveToBezierMatrix.mult(curveCoordY, curveDrawY)

        // since the paths are continuous,
        // only the first point needs the actual moveto
        if (vertexCount == 0) {
//    if (path == null) {
//      path = new Path();
            mpath!!.moveTo(curveDrawX[0], curveDrawY[0])
            vertexCount = 1
        }
        mpath!!.cubicTo(curveDrawX[1], curveDrawY[1],
                curveDrawX[2], curveDrawY[2],
                curveDrawX[3], curveDrawY[3])
    }

    override fun curveVertex(x: Float, y: Float, z: Float) {
        showDepthWarningXYZ("curveVertex")
    }

    //////////////////////////////////////////////////////////////
    // RENDERER
    //public void flush()
    //////////////////////////////////////////////////////////////
    // POINT, LINE, TRIANGLE, QUAD
    override fun point(x: Float, y: Float) {
        // this is a slow function to call on its own anyway.
        // most people will use set().
        beginShape(PConstants.POINTS)
        vertex(x, y)
        endShape()
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

    override fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
//    line.setLine(x1, y1, x2, y2);
//    strokeShape(line);
        if (stroke) {
            canvas!!.drawLine(x1, y1, x2, y2, strokePaint)
        }
    }

    override fun triangle(x1: Float, y1: Float, x2: Float, y2: Float,
                          x3: Float, y3: Float) {
        mpath!!.reset()
        mpath!!.moveTo(x1, y1)
        mpath!!.lineTo(x2, y2)
        mpath!!.lineTo(x3, y3)
        mpath!!.close()
        drawPath()
    }

    override fun quad(x1: Float, y1: Float, x2: Float, y2: Float,
                      x3: Float, y3: Float, x4: Float, y4: Float) {
        mpath!!.reset()
        mpath!!.moveTo(x1, y1)
        mpath!!.lineTo(x2, y2)
        mpath!!.lineTo(x3, y3)
        mpath!!.lineTo(x4, y4)
        mpath!!.close()
        drawPath()
    }

    //////////////////////////////////////////////////////////////
    // RECT
    //public void rectMode(int mode)
    //public void rect(float a, float b, float c, float d)
    override fun rectImpl(x1: Float, y1: Float, x2: Float, y2: Float) {
//    rect.setFrame(x1, y1, x2-x1, y2-y1);
//    drawShape(rect);
        //rect.set(x1, y1, x2, y2);
        if (fill) {
            canvas!!.drawRect(x1, y1, x2, y2, fillPaint)
        }
        if (stroke) {
            canvas!!.drawRect(x1, y1, x2, y2, strokePaint)
        }
    }

    //////////////////////////////////////////////////////////////
    // ELLIPSE
    //public void ellipseMode(int mode)
    //public void ellipse(float a, float b, float c, float d)
    override fun ellipseImpl(x: Float, y: Float, w: Float, h: Float) {
//    ellipse.setFrame(x, y, w, h);
//    drawShape(ellipse);
        rect[x, y, x + w] = y + h
        if (fill) {
            canvas!!.drawOval(rect, fillPaint)
        }
        if (stroke) {
            canvas!!.drawOval(rect, strokePaint)
        }
    }

    //////////////////////////////////////////////////////////////
    // ARC
    //public void arc(float a, float b, float c, float d,
    //                float start, float stop)
    override fun arcImpl(x: Float, y: Float, w: Float, h: Float,
                         start: Float, stop: Float, mode: Int) {
        // 0 to 90 in java would be 0 to -90 for p5 renderer
        // but that won't work, so -90 to 0?
        var start = start
        var stop = stop
        if (stop - start >= PConstants.TWO_PI) {
            ellipseImpl(x, y, w, h)
        } else {
            // Android agrees with us, so don't set start/stop negative like Java 2D
            start = start * PConstants.RAD_TO_DEG
            stop = stop * PConstants.RAD_TO_DEG

            // ok to do this because already checked for NaN
            while (start < 0) {
                start += 360f
                stop += 360f
            }
            if (start > stop) {
                val temp = start
                start = stop
                stop = temp
            }
            val sweep = stop - start
            rect[x, y, x + w] = y + h
            if (mode == 0) {
                if (fill) {
                    canvas!!.drawArc(rect, start, sweep, true, fillPaint)
                }
                if (stroke) {
                    canvas!!.drawArc(rect, start, sweep, false, strokePaint)
                }
            } else if (mode == PConstants.OPEN) {
                if (fill) {
                    // Android does not support stroke and fill with different color
                    // after drawing the arc,draw the arc with Paint.Style.Stroke style
                    // again
                    canvas!!.drawArc(rect, start, sweep, false, fillPaint)
                    canvas!!.drawArc(rect, start, sweep, false, strokePaint)
                }
                if (stroke) {
                    canvas!!.drawArc(rect, start, sweep, false, strokePaint)
                }
            } else if (mode == PConstants.CHORD) {
                // Draw an extra line between start angle point and end point to
                // achieve the chord
                val endAngle = start + sweep
                val halfRectWidth = rect.width() / 2
                val halfRectHeight = rect.height() / 2
                val centerX = rect.centerX()
                val centerY = rect.centerY()
                val startX = (halfRectWidth * Math.cos(Math.toRadians(start.toDouble()))).toFloat() + centerX
                val startY = (halfRectHeight * Math.sin(Math.toRadians(start.toDouble()))).toFloat() + centerY
                val endX = (halfRectWidth * Math.cos(Math.toRadians(endAngle.toDouble()))).toFloat() + centerX
                val endY = (halfRectHeight * Math.sin(Math.toRadians(endAngle.toDouble()))).toFloat() + centerY
                if (fill) {
                    // draw the fill arc
                    canvas!!.drawArc(rect, start, sweep, false, fillPaint)
                    // draw the arc round border
                    canvas!!.drawArc(rect, start, sweep, false, strokePaint)
                    // draw the straight border
                    canvas!!.drawLine(startX, startY, endX, endY, strokePaint)
                }
                if (stroke) {
                    // draw the arc
                    canvas!!.drawArc(rect, start, sweep, false, strokePaint)
                    // draw the straight border
                    canvas!!.drawLine(startX, startY, endX, endY, strokePaint)
                }
            } else if (mode == PConstants.PIE) {
                if (fill) {
                    canvas!!.drawArc(rect, start, sweep, true, fillPaint)
                }
                if (stroke) {
                    canvas!!.drawArc(rect, start, sweep, true, strokePaint)
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
    protected fun drawPath() {
        if (fill) {
            canvas!!.drawPath(mpath, fillPaint)
        }
        if (stroke) {
            canvas!!.drawPath(mpath, strokePaint)
        }
    }

    //////////////////////////////////////////////////////////////
    // BOX
    //public void box(float size)
    override fun box(w: Float, h: Float, d: Float) {
        showMethodWarning("box")
    }

    //////////////////////////////////////////////////////////////
    // SPHERE
    //public void sphereDetail(int res)
    //public void sphereDetail(int ures, int vres)
    override fun sphere(r: Float) {
        showMethodWarning("sphere")
    }
    //////////////////////////////////////////////////////////////
    // BEZIER
    //public float bezierPoint(float a, float b, float c, float d, float t)
    //public float bezierTangent(float a, float b, float c, float d, float t)
    //protected void bezierInitCheck()
    //protected void bezierInit()
    /** Ignored (not needed) in Java 2D.  */
    override fun bezierDetail(detail: Int) {}
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
    /** Ignored (not needed) in Java 2D.  */
    override fun curveDetail(detail: Int) {}

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
    override fun smooth(quality: Int) {  // ignore
        super.smooth(quality)
        //    smooth = true;
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                        RenderingHints.VALUE_ANTIALIAS_ON);
//    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        strokePaint.isAntiAlias = true
        fillPaint.isAntiAlias = true
    }

    override fun noSmooth() {
        super.noSmooth()
        //    smooth = false;
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
//                        RenderingHints.VALUE_ANTIALIAS_OFF);
//    canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        strokePaint.isAntiAlias = false
        fillPaint.isAntiAlias = false
    }

    //////////////////////////////////////////////////////////////
    // IMAGE
    //public void imageMode(int mode)
    //public void image(PImage image, float x, float y)
    //public void image(PImage image, float x, float y, float c, float d)
    //public void image(PImage image,
    //                  float a, float b, float c, float d,
    //                  int u1, int v1, int u2, int v2)
    var imageImplSrcRect: Rect? = null
    var imageImplDstRect: RectF? = null
    //android.widget.ImageView imv;
    /**
     * Handle renderer-specific image drawing.
     */
    override fun imageImpl(src: PImage,
                           x1: Float, y1: Float, x2: Float, y2: Float,
                           u1: Int, v1: Int, u2: Int, v2: Int) {
        var bitmap: Bitmap? = src.native as Bitmap?
        if (bitmap != null && bitmap.isRecycled) {
            // Let's make sure it is recreated
            bitmap = null
        }
        if (bitmap == null && src.format == PConstants.ALPHA) {
            // create an alpha bitmap for this feller
            bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val px = IntArray(src.pixels.size)
            for (i in px.indices) {
                px[i] = src.pixels[i] shl 24 or 0xFFFFFF
            }
            bitmap.setPixels(px, 0, src.width, 0, 0, src.width, src.height)
            modified = false
            src.native = bitmap
        }

        // this version's not usable because it doesn't allow you to set output w/h
//    if (src.bitmap == null) {  // format is ARGB or RGB
//      int offset = v1*src.width + u1;
//      canvas.drawBitmap(src.pixels, offset, src.width,
//                        x1, y1, u2-u1, v2-v1,
//                        src.format == ARGB, tint ? tintPaint : null);
//    } else {
        if (bitmap == null || src.width != bitmap.width || src.height != bitmap.height) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            modified = true
            src.native = bitmap
        }
        if (src.isModified) {
            //System.out.println("mutable, recycled = " + who.bitmap.isMutable() + ", " + who.bitmap.isRecycled());
            if (!bitmap!!.isMutable) {
                bitmap.recycle()
                bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                src.native = bitmap
            }
            if (src.pixels != null) {
                bitmap!!.setPixels(src.pixels, 0, src.width, 0, 0, src.width, src.height)
            }
            src.isModified = false
        }
        if (imageImplSrcRect == null) {
            imageImplSrcRect = Rect(u1, v1, u2, v2)
            imageImplDstRect = RectF(x1, y1, x2, y2)
        } else {
            imageImplSrcRect!![u1, v1, u2] = v2
            imageImplDstRect!![x1, y1, x2] = y2
        }
        //canvas.drawBitmap(who.bitmap, imageImplSrcRect, imageImplDstRect, tint ? tintPaint : null);
        //System.out.println(PApplet.hex(fillPaint.getColor()));
        //canvas.drawBitmap(who.bitmap, imageImplSrcRect, imageImplDstRect, fillPaint);
        //      System.out.println("drawing lower, tint = " + tint + " " + PApplet.hex(tintPaint.getColor()));
        canvas!!.drawBitmap(bitmap, imageImplSrcRect, imageImplDstRect, if (tint) tintPaint else null)

        // If the OS things the memory is low, then recycles bitmaps automatically...
        // but I don't think it is particularly efficient, as the bitmaps are stored
        // in native heap for Android 10 and older.
        val mi = ActivityManager.MemoryInfo()
        val activity = parent.surface.activity ?: return
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)
        if (mi.lowMemory) {
            bitmap!!.recycle()
            src.native = null
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
    override fun loadShape(filename: String): PShape {
        val extension = PApplet.getExtension(filename)
        var svg: PShapeSVG? = null
        if (extension == "svg") {
            svg = PShapeSVG(parent.loadXML(filename))
        } else if (extension == "svgz") {
            try {
                val input: InputStream = GZIPInputStream(parent.createInput(filename))
                val xml = XML(input)
                svg = PShapeSVG(xml)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            showWarning("Unsupported format")
        }
        return svg!!
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
    override fun textFont(which: PFont?) {
        super.textFont(which)
        fillPaint.typeface = which?.native as Typeface?
        fillPaint.textSize = which?.defaultSize!!.toFloat()
    }

    override fun textFont(which: PFont?, size: Float) {
        super.textFont(which, size)
        fillPaint.typeface = which?.native as Typeface?
        fillPaint.textSize = size
    }

    //public void textLeading(float leading)
    //public void textMode(int mode)
    override fun textModeCheck(mode: Int): Boolean {
        return mode == PConstants.MODEL
    }

    /**
     * Same as parent, but override for native version of the font.
     *
     *
     * Also gets called by textFont, so the metrics
     * will get recorded properly.
     */
    override fun textSize(size: Float) {
        if (textFont == null) {
            defaultFontOrDeath("textSize", size)
        }
        val font = textFont.native as Typeface?
        if (font != null) {
            fillPaint.textSize = size
        }
        handleTextSize(size)
    }

    protected fun beginTextScreenMode() {
        loadPixels()
    }

    protected fun endTextScreenMode() {
        updatePixels()
    }

    //public float textWidth(char c)
    //public float textWidth(String str)
    override fun textWidthImpl(buffer: CharArray, start: Int, stop: Int): Float {
//    Font font = textFont.getFont();
        val font = textFont.native as (Typeface?) ?: return super.textWidthImpl(buffer, start, stop)
        // maybe should use one of the newer/fancier functions for this?
        val length = stop - start
        //    FontMetrics metrics = canvas.getFontMetrics(font);
//    return metrics.charsWidth(buffer, start, length);
        return fillPaint.measureText(buffer, start, length)
    }

    //////////////////////////////////////////////////////////////
    // TEXT
    // None of the variations of text() are overridden from PGraphics.
    //////////////////////////////////////////////////////////////
    // TEXT IMPL
    //protected void textLineAlignImpl(char buffer[], int start, int stop,
    //                                 float x, float y)
    override fun textLineImpl(buffer: CharArray, start: Int, stop: Int,
                              x: Float, y: Float) {
        val font = textFont.native as Typeface?
        if (font == null) {
            showWarning("Inefficient font rendering: use createFont() with a TTF/OTF instead of loadFont().")
            //new Exception().printStackTrace(System.out);
            super.textLineImpl(buffer, start, stop, x, y)
            return
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
        fillPaint.isAntiAlias = textFont.isSmooth

        //System.out.println("setting frac metrics");
        //g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
        //                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);

//    canvas.setColor(fillColorObject);
        val length = stop - start
        //    canvas.drawChars(buffer, start, length, (int) (x + 0.5f), (int) (y + 0.5f));
        canvas!!.drawText(buffer, start, length, x, y, fillPaint)

        // return to previous smoothing state if it was changed
//    canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        fillPaint.isAntiAlias = 0 < smooth

//    textX = x + textWidthImpl(buffer, start, stop);
//    textY = y;
//    textZ = 0;  // this will get set by the caller if non-zero
    }

    //////////////////////////////////////////////////////////////
    // MATRIX STACK
    override fun pushMatrix() {
        if (transformCount == transformStack.size) {
            throw RuntimeException("pushMatrix() cannot use push more than " +
                    transformStack.size + " times")
        }
        transform[transformStack[transformCount]]
        transformCount++

//    canvas.save();
    }

    override fun popMatrix() {
        if (transformCount == 0) {
            throw RuntimeException("missing a popMatrix() " +
                    "to go with that pushMatrix()")
        }
        transformCount--
        transform.set(transformStack[transformCount])
        updateTransformMatrix()

        // Using canvas.restore() here and canvas.save() in popMatrix() and  should achieve
        // the same effect as setting copying transform into transformMatrix with updateTransformMatrix()
        // and setting it below, although it has been reported that with the later approach, a push/pop
        // would not result in the initial matrix state:
        // https://github.com/processing/processing-android/issues/445
        // However, cannot find
        canvas!!.matrix = transformMatrix
        //    canvas.restore();
    }

    //////////////////////////////////////////////////////////////
    // MATRIX TRANSFORMS
    override fun translate(tx: Float, ty: Float) {
        transform.translate(tx, ty)
        canvas!!.translate(tx, ty)
    }

    override fun rotate(angle: Float) {
        transform.rotate(angle)
        canvas!!.rotate(angle * PConstants.RAD_TO_DEG)
    }

    override fun rotateX(angle: Float) {
        showDepthWarning("rotateX")
    }

    override fun rotateY(angle: Float) {
        showDepthWarning("rotateY")
    }

    override fun rotateZ(angle: Float) {
        showDepthWarning("rotateZ")
    }

    override fun rotate(angle: Float, vx: Float, vy: Float, vz: Float) {
        showVariationWarning("rotate")
    }

    override fun scale(s: Float) {
        transform.scale(s, s)
        canvas!!.scale(s, s)
    }

    override fun scale(sx: Float, sy: Float) {
        transform.scale(sx, sy)
        canvas!!.scale(sx, sy)
    }

    override fun scale(sx: Float, sy: Float, sz: Float) {
        showDepthWarningXYZ("scale")
    }

    override fun shearX(angle: Float) {
        val t = Math.tan(angle.toDouble()).toFloat()
        transform.apply(1f, t, 0f, 0f, 1f, 0f)
        canvas!!.skew(t, 0f)
    }

    override fun shearY(angle: Float) {
        val t = Math.tan(angle.toDouble()).toFloat()
        transform.apply(1f, 0f, 0f, t, 1f, 0f)
        canvas!!.skew(0f, t)
    }

    //////////////////////////////////////////////////////////////
    // MATRIX MORE
    override fun resetMatrix() {
        transform.reset()
        canvas!!.matrix = null
    }

    //public void applyMatrix(PMatrix2D source)
    override fun applyMatrix(n00: Float, n01: Float, n02: Float,
                             n10: Float, n11: Float, n12: Float) {
        transform.apply(n00, n01, n02, n10, n11, n12)
        updateTransformMatrix()
        canvas!!.concat(transformMatrix)
    }

    //public void applyMatrix(PMatrix3D source)
    override fun applyMatrix(n00: Float, n01: Float, n02: Float, n03: Float,
                             n10: Float, n11: Float, n12: Float, n13: Float,
                             n20: Float, n21: Float, n22: Float, n23: Float,
                             n30: Float, n31: Float, n32: Float, n33: Float) {
        showVariationWarning("applyMatrix")
    }

    //////////////////////////////////////////////////////////////
    // MATRIX GET/SET
    override fun getMatrix(): PMatrix {
        return getMatrix((null as PMatrix2D?)!!)
    }

    override fun getMatrix(target: PMatrix2D): PMatrix2D {
        var target: PMatrix2D? = target
        if (target == null) {
            target = PMatrix2D()
        }
        target.set(transform)
        return target
    }

    override fun getMatrix(target: PMatrix3D): PMatrix3D {
        showVariationWarning("getMatrix")
        return target
    }

    //public void setMatrix(PMatrix source)
    override fun setMatrix(source: PMatrix2D) {
        transform.set(source)
        updateTransformMatrix()
        canvas!!.matrix = transformMatrix
    }

    override fun setMatrix(source: PMatrix3D) {
        showVariationWarning("setMatrix")
    }

    override fun printMatrix() {
        getMatrix((null as PMatrix2D?)!!).print()
    }

    //    return canvas.getMatrix();
    protected val matrixImp: Matrix
        protected get() {
            val m = Matrix()
            updateTransformMatrix()
            m.set(transformMatrix)
            return m
            //    return canvas.getMatrix();
        }

    fun updateTransformMatrix() {
        transformArray[0] = transform.m00
        transformArray[1] = transform.m01
        transformArray[2] = transform.m02
        transformArray[3] = transform.m10
        transformArray[4] = transform.m11
        transformArray[5] = transform.m12
        transformArray[6] = 0F
        transformArray[7] = 0F
        transformArray[8] = 1F
        transformMatrix.setValues(transformArray)
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
    var screenPoint: FloatArray? = null

    override fun screenX(x: Float, y: Float): Float {
        if (screenPoint == null) {
            screenPoint = FloatArray(2)
        }
        screenPoint!![0] = x
        screenPoint!![1] = y
        matrixImp.mapPoints(screenPoint)
        return screenPoint!![0]
    }

    override fun screenY(x: Float, y: Float): Float {
        if (screenPoint == null) {
            screenPoint = FloatArray(2)
        }
        screenPoint!![0] = x
        screenPoint!![1] = y
        matrixImp.mapPoints(screenPoint)
        return screenPoint!![1]
    }

    override fun screenX(x: Float, y: Float, z: Float): Float {
        showDepthWarningXYZ("screenX")
        return 0F
    }

    override fun screenY(x: Float, y: Float, z: Float): Float {
        showDepthWarningXYZ("screenY")
        return 0F
    }

    override fun screenZ(x: Float, y: Float, z: Float): Float {
        showDepthWarningXYZ("screenZ")
        return 0F
    }

    //public float modelX(float x, float y, float z)
    //public float modelY(float x, float y, float z)
    //public float modelZ(float x, float y, float z)
    //////////////////////////////////////////////////////////////
    // STYLE
    // pushStyle(), popStyle(), style() and getStyle() inherited.
    //////////////////////////////////////////////////////////////
    // STROKE CAP/JOIN/WEIGHT
    override fun strokeCap(cap: Int) {
        super.strokeCap(cap)
        if (strokeCap == PConstants.ROUND) {
            strokePaint.strokeCap = Paint.Cap.ROUND
        } else if (strokeCap == PConstants.PROJECT) {
            strokePaint.strokeCap = Paint.Cap.SQUARE
        } else {
            strokePaint.strokeCap = Paint.Cap.BUTT
        }
    }

    override fun strokeJoin(join: Int) {
        super.strokeJoin(join)
        if (strokeJoin == PConstants.MITER) {
            strokePaint.strokeJoin = Paint.Join.MITER
        } else if (strokeJoin == PConstants.ROUND) {
            strokePaint.strokeJoin = Paint.Join.ROUND
        } else {
            strokePaint.strokeJoin = Paint.Join.BEVEL
        }
    }

    override fun strokeWeight(weight: Float) {
        super.strokeWeight(weight)
        strokePaint.strokeWidth = weight
    }

    //////////////////////////////////////////////////////////////
    // STROKE
    // noStroke() and stroke() inherited from PGraphics.
    override fun strokeFromCalc() {
        super.strokeFromCalc()
        //    strokeColorObject = new Color(strokeColor, true);
        strokePaint.color = strokeColor
        //    strokeGradient = false;
        strokePaint.shader = null
    }

    //////////////////////////////////////////////////////////////
    // TINT
    // noTint() and tint() inherited from PGraphics.
    override fun tintFromCalc() {
        super.tintFromCalc()
        tintPaint.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.MULTIPLY)
    }

    //////////////////////////////////////////////////////////////
    // FILL
    // noFill() and fill() inherited from PGraphics.
    override fun fillFromCalc() {
        super.fillFromCalc()
        //    fillColorObject = new Color(fillColor, true);
        fillPaint.color = fillColor
        //    fillGradient = false;
        fillPaint.shader = null
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
    public override fun backgroundImpl() {
        canvas!!.drawColor(backgroundColor)

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
    override fun beginRaw(recorderRaw: PGraphics) {
        showMethodWarning("beginRaw")
    }

    override fun endRaw() {
        showMethodWarning("endRaw")
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
    override fun loadPixels() {
        if (bitmap == null) {
            throw RuntimeException("The pixels array is not available in this " +
                    "renderer withouth a backing bitmap")
        }
        if (pixels == null || pixels.size != width * height) {
            pixels = IntArray(width * height)
        }
        //    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(0, 0, width, height, pixels);
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Update the pixels[] buffer to the PGraphics image.
     * <P>
     * Unlike in PImage, where updatePixels() only requests that the
     * update happens, in PGraphicsJava2D, this will happen immediately.
    </P> */
    override fun updatePixels() {
        if (bitmap == null) {
            throw RuntimeException("The pixels array is not available in this " +
                    "renderer withouth a backing bitmap")
        }

//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.setDataElements(0, 0, width, height, pixels);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Update the pixels[] buffer to the PGraphics image.
     * <P>
     * Unlike in PImage, where updatePixels() only requests that the
     * update happens, in PGraphicsJava2D, this will happen immediately.
    </P> */
    override fun updatePixels(x: Int, y: Int, c: Int, d: Int) {
        //if ((x == 0) && (y == 0) && (c == width) && (d == height)) {
        if (x != 0 || y != 0 || c != width || d != height) {
            // Show a warning message, but continue anyway.
            showVariationWarning("updatePixels(x, y, w, h)")
        }
        updatePixels()
    }

    override fun resize(wide: Int, high: Int) {
        showMethodWarning("resize")
    }

    override fun clearState() {
        super.clearState()
        if (restoreFilename != null) {
            val cacheFile = File(restoreFilename)
            cacheFile.delete()
        }
    }

    override fun saveState() {
        super.saveState()
        val context = parent.context
        if (context == null || bitmap == null || parent.surface.component.isService()) return
        try {
            // Saving current width and height to avoid restoring the screen after a screen rotation
            restoreWidth = pixelWidth
            restoreHeight = pixelHeight
            val size = bitmap.height * bitmap.rowBytes
            val restoreBitmap = ByteBuffer.allocate(size)
            bitmap.copyPixelsToBuffer(restoreBitmap)

            // Tries to use external but if not mounted, falls back on internal storage, as shown in
            // https://developer.android.com/topic/performance/graphics/cache-bitmap#java
            val cacheDir = if (Environment.MEDIA_MOUNTED === Environment.getExternalStorageState() || !Environment.isExternalStorageRemovable()) context.externalCacheDir else context.cacheDir
            val cacheFile = File(cacheDir.toString() + File.separator + "restore_pixels")
            restoreFilename = cacheFile.absolutePath
            val stream = FileOutputStream(cacheFile)
            val dout = ObjectOutputStream(stream)
            val array = ByteArray(size)
            restoreBitmap.rewind()
            restoreBitmap[array]
            dout.writeObject(array)
            dout.flush()
            stream.fd.sync()
            stream.close()
        } catch (ex: Exception) {
            showWarning("Could not save screen contents to cache")
            ex.printStackTrace()
        }
    }

    override fun restoreSurface() {
        if (changed) {
            changed = false
            if (restoreFilename != null && restoreWidth == pixelWidth && restoreHeight == pixelHeight) {
                // Set the counter to 1 so the restore bitmap is drawn in the next frame.
                restoreCount = 1
            }
        } else if (restoreCount > 0) {
            restoreCount--
            if (restoreCount == 0) {
                val context = parent.context ?: return
                try {
                    // Load cached bitmap and draw
                    val cacheFile = File(restoreFilename)
                    val inStream = FileInputStream(cacheFile)
                    val din = ObjectInputStream(inStream)
                    val array = din.readObject() as ByteArray
                    val restoreBitmap = ByteBuffer.wrap(array)
                    if (restoreBitmap.capacity() == bitmap.height * bitmap.rowBytes) {
                        restoreBitmap.rewind()
                        bitmap.copyPixelsFromBuffer(restoreBitmap)
                    }
                    inStream.close()
                    cacheFile.delete()
                } catch (ex: Exception) {
                    showWarning("Could not restore screen contents from cache")
                    ex.printStackTrace()
                } finally {
                    restoreFilename = null
                    restoreWidth = -1
                    restoreHeight = -1
                    restoredSurface = true
                }
            }
        }
        super.restoreSurface()
    }

    override fun get(x: Int, y: Int): Int {
        return if (bitmap == null || x < 0 || y < 0 || x >= width || y >= height) 0 else bitmap.getPixel(x, y)
        //    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.getDataElements(x, y, getset);
//    return getset[0];
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
    override fun get(): PImage {
        return get(0, 0, width, height)
    }

    override fun set(x: Int, y: Int, argb: Int) {
        if (bitmap == null || x < 0 || y < 0 || x >= width || y >= height) return
        //    getset[0] = argb;
//    WritableRaster raster = ((BufferedImage) image).getRaster();
//    raster.setDataElements(x, y, getset);
        bitmap.setPixel(x, y, argb)
    }

    override fun set(x: Int, y: Int, src: PImage) {
        if (src.format == PConstants.ALPHA) {
            // set() doesn't really make sense for an ALPHA image, since it
            // directly replaces pixels and does no blending.
            throw RuntimeException("set() not available for ALPHA images")
        }
        var bitmap: Bitmap? = src.native as Bitmap
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            src.native = bitmap
            src.setModified()
        }
        if (src.width != bitmap!!.width ||
                src.height != bitmap.height) {
            bitmap.recycle()
            bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            src.native = bitmap
            src.setModified()
        }
        if (src.isModified) {
            if (!bitmap!!.isMutable) {
                bitmap.recycle()
                bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                native = bitmap
            }
            bitmap!!.setPixels(src.pixels, 0, src.width, 0, 0, src.width, src.height)
            src.isModified = false
        }
        // set() happens in screen coordinates, so need to clear the ctm
        pushMatrix()
        canvas!!.matrix = null // set to identity
        canvas!!.drawBitmap(bitmap, x.toFloat(), y.toFloat(), null)
        popMatrix()
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
    override fun mask(alpha: IntArray) {
        showMethodWarning("mask")
    }

    override fun mask(alpha: PImage) {
        showMethodWarning("mask")
    }

    //////////////////////////////////////////////////////////////
    // FILTER
    // Because the PImage versions call loadPixels() and
    // updatePixels(), no need to override anything here.
    //public void filter(int kind)
    //public void filter(int kind, float param)
    //////////////////////////////////////////////////////////////
    // COPY
    override fun copy(sx: Int, sy: Int, sw: Int, sh: Int,
                      dx: Int, dy: Int, dw: Int, dh: Int) {
        if (bitmap == null) {
            throw RuntimeException("The pixels array is not available in this " +
                    "renderer withouth a backing bitmap")
        }

//    Bitmap bitsy = Bitmap.createBitmap(image, sx, sy, sw, sh);
//    rect.set(dx, dy, dx + dw, dy + dh);
//    canvas.drawBitmap(bitsy,
        rect[dx.toFloat(), dy.toFloat(), dx + dw.toFloat()] = dy + dh.toFloat()
        val src = Rect(sx, sy, sx + sw, sy + sh)
        canvas!!.drawBitmap(bitmap, src, rect, null)

//    if ((sw != dw) || (sh != dh)) {
//      // use slow version if changing size
//      copy(this, sx, sy, sw, sh, dx, dy, dw, dh);
//
//    } else {
//      dx = dx - sx;  // java2d's "dx" is the delta, not dest
//      dy = dy - sy;
//      canvas.copyArea(sx, sy, sw, sh, dx, dy);
//    }
    } //  public void copy(PImage src,

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
    companion object {
        @JvmField
        var useBitmap = true
        protected const val MATRIX_STACK_DEPTH = 32

        //////////////////////////////////////////////////////////////
        // GET/SET
        var getset = IntArray(1)
    }

    //////////////////////////////////////////////////////////////
    // INTERNAL

    // constructor or initializer block
    init {
        transformStack = Array(MATRIX_STACK_DEPTH) { FloatArray(6) }
        transform = PMatrix2D()
        transformMatrix = Matrix()
        transformArray = FloatArray(9)
        mpath = Path()
        rect = RectF()
        fillPaint = Paint()
        fillPaint.style = Paint.Style.FILL
        strokePaint = Paint()
        strokePaint.style = Paint.Style.STROKE
        tintPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    }
}