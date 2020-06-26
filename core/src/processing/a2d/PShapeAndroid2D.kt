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

package processing.a2d

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import processing.core.PGraphics
import processing.core.PShapeSVG
import processing.data.XML

open class PShapeAndroid2D : PShapeSVG {
    protected var strokeGradientPaint: Shader? = null
    protected var fillGradientPaint: Shader? = null

    constructor(svg: XML?) : super(svg) {

    }

    constructor(parent: PShapeSVG?, properties: XML?, parseKids: Boolean) : super(parent, properties, parseKids) {

    }

    override fun setParent(parent: PShapeSVG) {
        super.setParent(parent)
        if (parent is PShapeAndroid2D) {
            val pj = parent
            fillGradientPaint = pj.fillGradientPaint
            strokeGradientPaint = pj.strokeGradientPaint
        } else {  // parent is null or not Android2D
            fillGradientPaint = null
            strokeGradientPaint = null
        }
    }

    /** Factory method for subclasses.  */
    override fun createShape(parent: PShapeSVG, properties: XML, parseKids: Boolean): PShapeSVG {
        return PShapeAndroid2D(parent, properties, parseKids)
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    protected fun calcGradientPaint(gradient: Gradient): Shader? {
        // TODO just do this with the other parsing
        val colors = IntArray(gradient.count)
        val opacityMask = (opacity * 255).toInt() shl 24
        for (i in 0 until gradient.count) {
            colors[i] = opacityMask or (gradient.color[i] and 0xFFFFFF)
        }
        if (gradient is LinearGradient) {
            val grad = gradient
            //      return new LinearGradientPaint(grad.x1, grad.y1, grad.x2, grad.y2,
//                                     grad.offset, grad.color, grad.count,
//                                     opacity);
            return LinearGradient(grad.x1, grad.y1,
                    grad.x2, grad.y2,
                    colors, grad.offset,
                    Shader.TileMode.CLAMP)
        } else if (gradient is RadialGradient) {
            val grad = gradient
            //      return new RadialGradientPaint(grad.cx, grad.cy, grad.r,
//                                     grad.offset, grad.color, grad.count,
//                                     opacity);
            return RadialGradient(grad.cx, grad.cy, grad.r,
                    colors, grad.offset,
                    Shader.TileMode.CLAMP)
        }
        return null
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    override fun styles(g: PGraphics) {
        super.styles(g)
        if (g is PGraphicsAndroid2D) {
            val gg = g
            if (strokeGradient != null) {
//        gg.strokeGradient = true;
                if (strokeGradientPaint == null) {
                    strokeGradientPaint = calcGradientPaint(strokeGradient)
                }
                gg.strokePaint.shader = strokeGradientPaint
            }
            if (fillGradient != null) {
//        gg.fillGradient = true;
                if (fillGradientPaint == null) {
                    fillGradientPaint = calcGradientPaint(fillGradient)
                }
                gg.fillPaint.shader = fillGradientPaint
            } else {
                // need to shut off, in case parent object has a gradient applied
                //gg.fillGradient = false;
            }
        }
    }
}