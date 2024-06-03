/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-21 The Processing Foundation

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

import android.graphics.Shader;

import processing.core.PGraphics;
import processing.core.PShapeSVG;
import processing.data.XML;

public class PShapeAndroid2D extends PShapeSVG {
  protected Shader strokeGradientPaint;
  protected Shader fillGradientPaint;


  public PShapeAndroid2D(XML svg) {
    super(svg);
  }


  public PShapeAndroid2D(PShapeSVG parent, XML properties, boolean parseKids) {
    super(parent, properties, parseKids);
  }


  @Override
  protected void setParent(PShapeSVG parent) {
    super.setParent(parent);

    if (parent instanceof PShapeAndroid2D) {
      PShapeAndroid2D pj = (PShapeAndroid2D) parent;
      fillGradientPaint = pj.fillGradientPaint;
      strokeGradientPaint = pj.strokeGradientPaint;

    } else {  // parent is null or not Android2D
      fillGradientPaint = null;
      strokeGradientPaint = null;
    }
  }


  /** Factory method for subclasses. */
  @Override
  protected PShapeSVG createShape(PShapeSVG parent, XML properties, boolean parseKids) {
    return new PShapeAndroid2D(parent, properties, parseKids);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  protected Shader calcGradientPaint(Gradient gradient) {
    // TODO just do this with the other parsing
    int[] colors = new int[gradient.count];
    int opacityMask = ((int) (opacity * 255)) << 24;
    for (int i = 0; i < gradient.count; i++) {
      colors[i] = opacityMask | (gradient.color[i] & 0xFFFFFF);
    }

    if (gradient instanceof LinearGradient) {
      LinearGradient grad = (LinearGradient) gradient;
//      return new LinearGradientPaint(grad.x1, grad.y1, grad.x2, grad.y2,
//                                     grad.offset, grad.color, grad.count,
//                                     opacity);
      return new android.graphics.LinearGradient(grad.x1, grad.y1,
                                                 grad.x2, grad.y2,
                                                 colors, grad.offset,
                                                 Shader.TileMode.CLAMP );

    } else if (gradient instanceof RadialGradient) {
      RadialGradient grad = (RadialGradient) gradient;
//      return new RadialGradientPaint(grad.cx, grad.cy, grad.r,
//                                     grad.offset, grad.color, grad.count,
//                                     opacity);
      return new android.graphics.RadialGradient(grad.cx, grad.cy, grad.r,
                                                 colors, grad.offset,
                                                 Shader.TileMode.CLAMP);
    }
    return null;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  @Override
  protected void styles(PGraphics g) {
    super.styles(g);

    if (g instanceof PGraphicsAndroid2D) {
      PGraphicsAndroid2D gg = (PGraphicsAndroid2D) g;

      if (strokeGradient != null) {
//        gg.strokeGradient = true;
        if (strokeGradientPaint == null) {
          strokeGradientPaint = calcGradientPaint(strokeGradient);
        }
        gg.strokePaint.setShader(strokeGradientPaint);
      }
      if (fillGradient != null) {
//        gg.fillGradient = true;
        if (fillGradientPaint == null) {
          fillGradientPaint = calcGradientPaint(fillGradient);
        }
        gg.fillPaint.setShader(fillGradientPaint);
      } else {
        // need to shut off, in case parent object has a gradient applied
        //gg.fillGradient = false;
      }
    }
  }
}
