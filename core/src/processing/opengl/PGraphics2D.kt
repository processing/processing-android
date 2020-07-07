/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.opengl

import processing.core.*

open class PGraphics2D : PGraphicsOpenGL() {

    //////////////////////////////////////////////////////////////

    // RENDERER SUPPORT QUERIES

    override fun is2D(): Boolean {
        return true
    }

    override fun is3D(): Boolean {
        return false
    }

    //////////////////////////////////////////////////////////////

    // HINTS

    override fun hint(which: Int) {
        if (which == PConstants.ENABLE_STROKE_PERSPECTIVE) {
            PGraphics.showWarning("Strokes cannot be perspective-corrected in 2D.")
            return
        }
        super.hint(which)
    }

    //////////////////////////////////////////////////////////////

    // PROJECTION

    override fun ortho() {
        PGraphics.showMethodWarning("ortho")
    }

    override fun ortho(left: Float, right: Float,
                       bottom: Float, top: Float) {
        PGraphics.showMethodWarning("ortho")
    }

    override fun ortho(left: Float, right: Float,
                       bottom: Float, top: Float,
                       near: Float, far: Float) {
        PGraphics.showMethodWarning("ortho")
    }

    override fun perspective() {
        PGraphics.showMethodWarning("perspective")
    }

    override fun perspective(fov: Float, aspect: Float, zNear: Float, zFar: Float) {
        PGraphics.showMethodWarning("perspective")
    }

    override fun frustum(left: Float, right: Float, bottom: Float, top: Float,
                         znear: Float, zfar: Float) {
        PGraphics.showMethodWarning("frustum")
    }

    override fun defaultPerspective() {
        super.ortho(0f, width.toFloat(), -height.toFloat(), 0f, -1f, +1f)
    }

    //////////////////////////////////////////////////////////////

    // CAMERA

    override fun beginCamera() {
        PGraphics.showMethodWarning("beginCamera")
    }

    override fun endCamera() {
        PGraphics.showMethodWarning("endCamera")
    }

    override fun camera() {
        PGraphics.showMethodWarning("camera")
    }

    override fun camera(eyeX: Float, eyeY: Float, eyeZ: Float,
                        centerX: Float, centerY: Float, centerZ: Float,
                        upX: Float, upY: Float, upZ: Float) {
        PGraphics.showMethodWarning("camera")
    }

    override fun defaultCamera() {
        eyeDist = 1f
        resetMatrix()
    }

    //////////////////////////////////////////////////////////////

    // MATRIX MORE!

    override fun begin2D() {
        pushProjection()
        defaultPerspective()
        pushMatrix()
        defaultCamera()
    }

    override fun end2D() {
        popMatrix()
        popProjection()
    }

    //////////////////////////////////////////////////////////////

    // SHAPE

    override fun shape(shape: PShape) {
        if (shape.is2D) {
            super.shape(shape)
        } else {
            PGraphics.showWarning("The shape object is not 2D, cannot be displayed with " +
                    "this renderer")
        }
    }

    override fun shape(shape: PShape, x: Float, y: Float) {
        if (shape.is2D) {
            super.shape(shape, x, y)
        } else {
            PGraphics.showWarning("The shape object is not 2D, cannot be displayed with " +
                    "this renderer")
        }
    }

    override fun shape(shape: PShape, a: Float, b: Float, c: Float, d: Float) {
        if (shape.is2D) {
            super.shape(shape, a, b, c, d)
        } else {
            PGraphics.showWarning("The shape object is not 2D, cannot be displayed with " +
                    "this renderer")
        }
    }

    public override fun shape(shape: PShape, x: Float, y: Float, z: Float) {
        PGraphics.showDepthWarningXYZ("shape")
    }

    public override fun shape(shape: PShape, x: Float, y: Float, z: Float,
                              c: Float, d: Float, e: Float) {
        PGraphics.showDepthWarningXYZ("shape")
    }

    //////////////////////////////////////////////////////////////

    // SCREEN TRANSFORMS

    override fun modelX(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarning("modelX")
        return 0F
    }

    override fun modelY(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarning("modelY")
        return 0F
    }

    override fun modelZ(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarning("modelZ")
        return 0F
    }

    //////////////////////////////////////////////////////////////

    // SHAPE CREATION

    //  @Override
    //  protected PShape createShapeFamily(int type) {
    //    return new PShapeOpenGL(this, type);
    //  }
    //
    //
    //  @Override
    //  protected PShape createShapePrimitive(int kind, float... p) {
    //    return new PShapeOpenGL(this, kind, p);
    //  }
    /*
  @Override
  public PShape createShape(PShape source) {
    return PShapeOpenGL.createShape2D(this, source);
  }


  @Override
  public PShape createShape() {
    return createShape(PShape.GEOMETRY);
  }


  @Override
  public PShape createShape(int type) {
    return createShapeImpl(this, type);
  }


  @Override
  public PShape createShape(int kind, float... p) {
    return createShapeImpl(this, kind, p);
  }


  static protected PShapeOpenGL createShapeImpl(PGraphicsOpenGL pg, int type) {
    PShapeOpenGL shape = null;
    if (type == PConstants.GROUP) {
      shape = new PShapeOpenGL(pg, PConstants.GROUP);
    } else if (type == PShape.PATH) {
      shape = new PShapeOpenGL(pg, PShape.PATH);
    } else if (type == PShape.GEOMETRY) {
      shape = new PShapeOpenGL(pg, PShape.GEOMETRY);
    }
    shape.set3D(false);
    return shape;
  }


  static protected PShapeOpenGL createShapeImpl(PGraphicsOpenGL pg,
                                                int kind, float... p) {
    PShapeOpenGL shape = null;
    int len = p.length;

    if (kind == POINT) {
      if (len != 2) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(POINT);
    } else if (kind == LINE) {
      if (len != 4) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(LINE);
    } else if (kind == TRIANGLE) {
      if (len != 6) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(TRIANGLE);
    } else if (kind == QUAD) {
      if (len != 8) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(QUAD);
    } else if (kind == RECT) {
      if (len != 4 && len != 5 && len != 8 && len != 9) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(RECT);
    } else if (kind == ELLIPSE) {
      if (len != 4 && len != 5) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(ELLIPSE);
    } else if (kind == ARC) {
      if (len != 6 && len != 7) {
        showWarning("Wrong number of parameters");
        return null;
      }
      shape = new PShapeOpenGL(pg, PShape.PRIMITIVE);
      shape.setKind(ARC);
    } else if (kind == BOX) {
      showWarning("Primitive not supported in 2D");
    } else if (kind == SPHERE) {
      showWarning("Primitive not supported in 2D");
    } else {
      showWarning("Unrecognized primitive type");
    }

    if (shape != null) {
      shape.setParams(p);
    }

    shape.set3D(false);
    return shape;
  }
  */

    //////////////////////////////////////////////////////////////

    // BEZIER VERTICES

    override fun bezierVertex(x2: Float, y2: Float, z2: Float,
                              x3: Float, y3: Float, z3: Float,
                              x4: Float, y4: Float, z4: Float) {
        PGraphics.showDepthWarningXYZ("bezierVertex")
    }

    //////////////////////////////////////////////////////////////

    // QUADRATIC BEZIER VERTICES

    override fun quadraticVertex(x2: Float, y2: Float, z2: Float,
                                 x4: Float, y4: Float, z4: Float) {
        PGraphics.showDepthWarningXYZ("quadVertex")
    }

    //////////////////////////////////////////////////////////////

    // CURVE VERTICES

    override fun curveVertex(x: Float, y: Float, z: Float) {
        PGraphics.showDepthWarningXYZ("curveVertex")
    }

    //////////////////////////////////////////////////////////////

    // BOX

    override fun box(w: Float, h: Float, d: Float) {
        PGraphics.showMethodWarning("box")
    }

    //////////////////////////////////////////////////////////////

    // SPHERE

    override fun sphere(r: Float) {
        PGraphics.showMethodWarning("sphere")
    }

    //////////////////////////////////////////////////////////////

    // VERTEX SHAPES

    override fun vertex(x: Float, y: Float, z: Float) {
        PGraphics.showDepthWarningXYZ("vertex")
    }

    override fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        PGraphics.showDepthWarningXYZ("vertex")
    }

    //////////////////////////////////////////////////////////////

    // MATRIX TRANSFORMATIONS

    override fun translate(tx: Float, ty: Float, tz: Float) {
        PGraphics.showDepthWarningXYZ("translate")
    }

    override fun rotateX(angle: Float) {
        PGraphics.showDepthWarning("rotateX")
    }

    override fun rotateY(angle: Float) {
        PGraphics.showDepthWarning("rotateY")
    }

    override fun rotateZ(angle: Float) {
        PGraphics.showDepthWarning("rotateZ")
    }

    override fun rotate(angle: Float, vx: Float, vy: Float, vz: Float) {
        PGraphics.showVariationWarning("rotate")
    }

    override fun applyMatrix(source: PMatrix3D) {
        PGraphics.showVariationWarning("applyMatrix")
    }

    override fun applyMatrix(n00: Float, n01: Float, n02: Float, n03: Float,
                             n10: Float, n11: Float, n12: Float, n13: Float,
                             n20: Float, n21: Float, n22: Float, n23: Float,
                             n30: Float, n31: Float, n32: Float, n33: Float) {
        PGraphics.showVariationWarning("applyMatrix")
    }

    override fun scale(sx: Float, sy: Float, sz: Float) {
        PGraphics.showDepthWarningXYZ("scale")
    }

    //////////////////////////////////////////////////////////////

    // SCREEN AND MODEL COORDS

    override fun screenX(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarningXYZ("screenX")
        return 0F
    }

    override fun screenY(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarningXYZ("screenY")
        return 0F
    }

    override fun screenZ(x: Float, y: Float, z: Float): Float {
        PGraphics.showDepthWarningXYZ("screenZ")
        return 0F
    }

    override fun getMatrix(target: PMatrix2D): PMatrix2D {
        var target: PMatrix2D? = target
        if (target == null) {
            target = PMatrix2D()
        }
        // This set operation is well defined, since modelview is a 2D-only
        // transformation matrix in the P2D renderer.
        target[modelview.m00, modelview.m01, modelview.m03, modelview.m10, modelview.m11] = modelview.m13
        return target
    }

    override fun getMatrix(target: PMatrix3D): PMatrix3D {
        PGraphics.showVariationWarning("getMatrix")
        return target
    }

    override fun setMatrix(source: PMatrix3D) {
        PGraphics.showVariationWarning("setMatrix")
    }

    //////////////////////////////////////////////////////////////

    // LIGHTS

    override fun lights() {
        PGraphics.showMethodWarning("lights")
    }

    override fun noLights() {
        PGraphics.showMethodWarning("noLights")
    }

    override fun ambientLight(red: Float, green: Float, blue: Float) {
        PGraphics.showMethodWarning("ambientLight")
    }

    override fun ambientLight(red: Float, green: Float, blue: Float,
                              x: Float, y: Float, z: Float) {
        PGraphics.showMethodWarning("ambientLight")
    }

    override fun directionalLight(red: Float, green: Float, blue: Float,
                                  nx: Float, ny: Float, nz: Float) {
        PGraphics.showMethodWarning("directionalLight")
    }

    override fun pointLight(red: Float, green: Float, blue: Float,
                            x: Float, y: Float, z: Float) {
        PGraphics.showMethodWarning("pointLight")
    }

    override fun spotLight(red: Float, green: Float, blue: Float,
                           x: Float, y: Float, z: Float,
                           nx: Float, ny: Float, nz: Float,
                           angle: Float, concentration: Float) {
        PGraphics.showMethodWarning("spotLight")
    }

    override fun lightFalloff(constant: Float, linear: Float, quadratic: Float) {
        PGraphics.showMethodWarning("lightFalloff")
    }

    override fun lightSpecular(v1: Float, v2: Float, v3: Float) {
        PGraphics.showMethodWarning("lightSpecular")
    }

    companion object {
        //////////////////////////////////////////////////////////////

        // SHAPE I/O

        @JvmStatic
        fun isSupportedExtension(extension: String): Boolean {
            return extension == "svg" || extension == "svgz"
        }

        @JvmStatic
        fun loadShapeImpl(pg: PGraphics,
                          filename: String?, extension: String): PShape? {
            if (extension == "svg" || extension == "svgz") {
                val svg = PShapeSVG(pg.parent.loadXML(filename))
                return PShapeOpenGL.createShape(pg as PGraphicsOpenGL, svg)
            }
            return null
        }
    }
}