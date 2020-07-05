/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package processing.opengl

import processing.core.PMatrix2D
import processing.opengl.LinePath.Companion.FloatToS15_16
import processing.opengl.LinePath.Companion.hypot

open class LineStroker {
    private var output: LineStroker? = null
    private var capStyle = 0
    private var joinStyle = 0
    private var m00 = 0
    private var m01 = 0
    private var m10 = 0
    private var m11 = 0
    private var lineWidth2 = 0
    private var scaledLineWidth2: Long = 0

    // For any pen offset (pen_dx, pen_dy) that does not depend on
    // the line orientation, the pen should be transformed so that:
    //
    // pen_dx' = m00*pen_dx + m01*pen_dy
    // pen_dy' = m10*pen_dx + m11*pen_dy
    //
    // For a round pen, this means:
    //
    // pen_dx(r, theta) = r*cos(theta)
    // pen_dy(r, theta) = r*sin(theta)
    //
    // pen_dx'(r, theta) = r*(m00*cos(theta) + m01*sin(theta))
    // pen_dy'(r, theta) = r*(m10*cos(theta) + m11*sin(theta))

    private var numPenSegments = 0
    private var pen_dx: IntArray? = null
    private lateinit var pen_dy: IntArray
    private lateinit var penIncluded: BooleanArray
    private lateinit var join: IntArray
    private val offset = IntArray(2)
    private var reverse = IntArray(100)
    private val miter = IntArray(2)
    private var miterLimitSq: Long = 0
    private var prev = 0
    private var rindex = 0
    private var started = false
    private var lineToOrigin = false
    private var joinToOrigin = false
    private var sx0 = 0
    private var sy0 = 0
    private var sx1 = 0
    private var sy1 = 0
    private var x0 = 0
    private var y0 = 0
    private var scolor0 = 0
    private var pcolor0 = 0
    private var color0 = 0
    private var mx0 = 0
    private var my0 = 0
    private var omx = 0
    private var omy = 0
    private var px0 = 0
    private var py0 = 0
    private var m00_2_m01_2 = 0.0
    private var m10_2_m11_2 = 0.0
    private var m00_m10_m01_m11 = 0.0

    /**
     * Empty constructor. `setOutput` and `setParameters`
     * must be called prior to calling any other methods.
     */
    constructor() {

    }

    /**
     * Constructs a `LineStroker`.
     *
     * @param output
     * an output `LineStroker`.
     * @param lineWidth
     * the desired line width in pixels, in S15.16 format.
     * @param capStyle
     * the desired end cap style, one of `CAP_BUTT`,
     * `CAP_ROUND` or `CAP_SQUARE`.
     * @param joinStyle
     * the desired line join style, one of `JOIN_MITER`,
     * `JOIN_ROUND` or `JOIN_BEVEL`.
     * @param miterLimit
     * the desired miter limit, in S15.16 format.
     * @param transform
     * a `Transform4` object indicating the transform that has
     * been previously applied to all incoming coordinates. This is
     * required in order to produce consistently shaped end caps and
     * joins.
     */
    constructor(output: LineStroker?, lineWidth: Int, capStyle: Int, joinStyle: Int,
                miterLimit: Int, transform: PMatrix2D) {
        setOutput(output)
        setParameters(lineWidth, capStyle, joinStyle, miterLimit, transform)
    }

    /**
     * Sets the output `LineStroker` of this `LineStroker`.
     *
     * @param output
     * an output `LineStroker`.
     */
    fun setOutput(output: LineStroker?) {
        this.output = output
    }

    /**
     * Sets the parameters of this `LineStroker`.
     *
     * @param lineWidth
     * the desired line width in pixels, in S15.16 format.
     * @param capStyle
     * the desired end cap style, one of `CAP_BUTT`,
     * `CAP_ROUND` or `CAP_SQUARE`.
     * @param joinStyle
     * the desired line join style, one of `JOIN_MITER`,
     * `JOIN_ROUND` or `JOIN_BEVEL`.
     * @param miterLimit
     * the desired miter limit, in S15.16 format.
     * @param transform
     * a `Transform4` object indicating the transform that has
     * been previously applied to all incoming coordinates. This is
     * required in order to produce consistently shaped end caps and
     * joins.
     */
    fun setParameters(lineWidth: Int, capStyle: Int, joinStyle: Int,
                      miterLimit: Int, transform: PMatrix2D) {
        m00 = FloatToS15_16(transform.m00)
        m01 = FloatToS15_16(transform.m01)
        m10 = FloatToS15_16(transform.m10)
        m11 = FloatToS15_16(transform.m11)
        lineWidth2 = lineWidth shr 1
        scaledLineWidth2 = m00.toLong() * lineWidth2 shr 16
        this.capStyle = capStyle
        this.joinStyle = joinStyle
        m00_2_m01_2 = m00.toDouble() * m00 + m01.toDouble() * m01
        m10_2_m11_2 = m10.toDouble() * m10 + m11.toDouble() * m11
        m00_m10_m01_m11 = m00.toDouble() * m10 + m01.toDouble() * m11

        val dm00 = m00 / 65536.0
        val dm01 = m01 / 65536.0
        val dm10 = m10 / 65536.0
        val dm11 = m11 / 65536.0
        val determinant = dm00 * dm11 - dm01 * dm10

        if (joinStyle == LinePath.JOIN_MITER) {
            val limit = (miterLimit / 65536.0 * (lineWidth2 / 65536.0)
                    * determinant)
            val limitSq = limit * limit
            miterLimitSq = (limitSq * 65536.0 * 65536.0).toLong()
        }
        numPenSegments = (3.14159f * lineWidth / 65536.0f).toInt()
        if (pen_dx == null || pen_dx!!.size < numPenSegments) {
            pen_dx = IntArray(numPenSegments)
            pen_dy = IntArray(numPenSegments)
            penIncluded = BooleanArray(numPenSegments)
            join = IntArray(2 * numPenSegments)
        }

        for (i in 0 until numPenSegments) {
            val r = lineWidth / 2.0
            val theta = i * 2 * Math.PI / numPenSegments
            val cos = Math.cos(theta)
            val sin = Math.sin(theta)
            pen_dx!![i] = (r * (dm00 * cos + dm01 * sin)).toInt()
            pen_dy[i] = (r * (dm10 * cos + dm11 * sin)).toInt()
        }

        prev = LinePath.SEG_CLOSE.toInt()
        rindex = 0
        started = false
        lineToOrigin = false
    }

    private fun computeOffset(x0: Int, y0: Int, x1: Int, y1: Int, m: IntArray) {
        val lx = x1.toLong() - x0.toLong()
        val ly = y1.toLong() - y0.toLong()
        val dx: Int
        val dy: Int
        if (m00 > 0 && m00 == m11 && m01 == 0 && m10 == 0) {
            val ilen = hypot(lx, ly)
            if (ilen == 0L) {
                dy = 0
                dx = dy
            } else {
                dx = (ly * scaledLineWidth2 / ilen).toInt()
                dy = (-(lx * scaledLineWidth2) / ilen).toInt()
            }
        } else {
            val dlx = x1 - x0.toDouble()
            val dly = y1 - y0.toDouble()
            val det = m00.toDouble() * m11 - m01.toDouble() * m10
            val sdet = if (det > 0) 1 else -1
            val a = dly * m00 - dlx * m10
            val b = dly * m01 - dlx * m11
            val dh = hypot(a, b)
            val div = sdet * lineWidth2 / (65536.0 * dh)
            val ddx = dly * m00_2_m01_2 - dlx * m00_m10_m01_m11
            val ddy = dly * m00_m10_m01_m11 - dlx * m10_2_m11_2
            dx = (ddx * div).toInt()
            dy = (ddy * div).toInt()
        }
        m[0] = dx
        m[1] = dy
    }

    private fun ensureCapacity(newrindex: Int) {
        if (reverse.size < newrindex) {
            val tmp = IntArray(Math.max(newrindex, 6 * reverse.size / 5))
            System.arraycopy(reverse, 0, tmp, 0, rindex)
            reverse = tmp
        }
    }

    private fun isCCW(x0: Int, y0: Int, x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
        val dx0 = x1 - x0
        val dy0 = y1 - y0
        val dx1 = x2 - x1
        val dy1 = y2 - y1
        return dx0.toLong() * dy1 < dy0.toLong() * dx1
    }

    private fun side(x: Int, y: Int, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        val lx = x.toLong()
        val ly = y.toLong()
        val lx0 = x0.toLong()
        val ly0 = y0.toLong()
        val lx1 = x1.toLong()
        val ly1 = y1.toLong()
        return (ly0 - ly1) * lx + (lx1 - lx0) * ly + (lx0 * ly1 - lx1 * ly0) > 0
    }

    private fun computeRoundJoin(cx: Int, cy: Int, xa: Int, ya: Int, xb: Int, yb: Int,
                                 side: Int, flip: Boolean, join: IntArray): Int {
        var px: Int
        var py: Int
        var ncoords = 0
        val centerSide: Boolean
        centerSide = if (side == 0) {
            side(cx, cy, xa, ya, xb, yb)
        } else {
            if (side == 1) true else false
        }
        for (i in 0 until numPenSegments) {
            px = cx + pen_dx!![i]
            py = cy + pen_dy[i]
            val penSide = side(px, py, xa, ya, xb, yb)
            penIncluded[i] = penSide != centerSide
        }
        var start = -1
        var end = -1
        for (i in 0 until numPenSegments) {
            if (penIncluded[i]
                    && !penIncluded[(i + numPenSegments - 1) % numPenSegments]) {
                start = i
            }
            if (penIncluded[i] && !penIncluded[(i + 1) % numPenSegments]) {
                end = i
            }
        }
        if (end < start) {
            end += numPenSegments
        }
        if (start != -1 && end != -1) {
            val dxa = cx + pen_dx!![start] - xa.toLong()
            val dya = cy + pen_dy[start] - ya.toLong()
            val dxb = cx + pen_dx!![start] - xb.toLong()
            val dyb = cy + pen_dy[start] - yb.toLong()
            val rev = dxa * dxa + dya * dya > dxb * dxb + dyb * dyb
            var i = if (rev) end else start
            val incr = if (rev) -1 else 1
            while (true) {
                val idx = i % numPenSegments
                px = cx + pen_dx!![idx]
                py = cy + pen_dy[idx]
                join[ncoords++] = px
                join[ncoords++] = py
                if (i == (if (rev) start else end)) {
                    break
                }
                i += incr
            }
        }
        return ncoords / 2
    }

    private fun drawRoundJoin(x: Int, y: Int, omx: Int, omy: Int, mx: Int, my: Int,
                              side: Int, color: Int,
                              flip: Boolean, rev: Boolean, threshold: Long) {
        var omx = omx
        var omy = omy
        var mx = mx
        var my = my
        if (omx == 0 && omy == 0 || mx == 0 && my == 0) {
            return
        }
        val domx = omx.toLong() - mx
        val domy = omy.toLong() - my
        val len = domx * domx + domy * domy
        if (len < threshold) {
            return
        }
        if (rev) {
            omx = -omx
            omy = -omy
            mx = -mx
            my = -my
        }
        val bx0 = x + omx
        val by0 = y + omy
        val bx1 = x + mx
        val by1 = y + my
        val npoints = computeRoundJoin(x, y, bx0, by0, bx1, by1, side, flip, join)
        for (i in 0 until npoints) {
            emitLineTo(join[2 * i], join[2 * i + 1], color, rev)
        }
    }

    // Return the intersection point of the lines (ix0, iy0) -> (ix1, iy1)
    // and (ix0p, iy0p) -> (ix1p, iy1p) in m[0] and m[1]
    private fun computeMiter(ix0: Int, iy0: Int, ix1: Int, iy1: Int, ix0p: Int,
                             iy0p: Int, ix1p: Int, iy1p: Int, m: IntArray) {
        val x0 = ix0.toLong()
        val y0 = iy0.toLong()
        val x1 = ix1.toLong()
        val y1 = iy1.toLong()
        val x0p = ix0p.toLong()
        val y0p = iy0p.toLong()
        val x1p = ix1p.toLong()
        val y1p = iy1p.toLong()
        val x10 = x1 - x0
        val y10 = y1 - y0
        val x10p = x1p - x0p
        val y10p = y1p - y0p
        val den = x10 * y10p - x10p * y10 shr 16
        if (den == 0L) {
            m[0] = ix0
            m[1] = iy0
            return
        }
        val t = x1p * (y0 - y0p) - x0 * y10p + x0p * (y1p - y0) shr 16
        m[0] = (x0 + t * x10 / den).toInt()
        m[1] = (y0 + t * y10 / den).toInt()
    }

    private fun drawMiter(px0: Int, py0: Int, x0: Int, y0: Int, x1: Int, y1: Int,
                          omx: Int, omy: Int, mx: Int, my: Int, color: Int,
                          rev: Boolean) {
        var omx = omx
        var omy = omy
        var mx = mx
        var my = my
        if (mx == omx && my == omy) {
            return
        }
        if (px0 == x0 && py0 == y0) {
            return
        }
        if (x0 == x1 && y0 == y1) {
            return
        }
        if (rev) {
            omx = -omx
            omy = -omy
            mx = -mx
            my = -my
        }
        computeMiter(px0 + omx, py0 + omy, x0 + omx, y0 + omy, x0 + mx, y0 + my, x1
                + mx, y1 + my, miter)

        // Compute miter length in untransformed coordinates
        val dx = miter[0].toLong() - x0
        val dy = miter[1].toLong() - y0
        val a = dy * m00 - dx * m10 shr 16
        val b = dy * m01 - dx * m11 shr 16
        val lenSq = a * a + b * b
        if (lenSq < miterLimitSq) {
            emitLineTo(miter[0], miter[1], color, rev)
        }
    }

    open fun moveTo(x0: Int, y0: Int, c0: Int) {
        // System.out.println("LineStroker.moveTo(" + x0/65536.0 + ", " + y0/65536.0 + ")");
        if (lineToOrigin) {
            // not closing the path, do the previous lineTo
            lineToImpl(sx0, sy0, scolor0, joinToOrigin)
            lineToOrigin = false
        }
        if (prev == LinePath.SEG_LINETO.toInt()) {
            finish()
        }
        this.x0 = x0
        sx0 = this.x0
        this.y0 = y0
        sy0 = this.y0
        color0 = c0
        scolor0 = color0
        rindex = 0
        started = false
        joinSegment = false
        prev = LinePath.SEG_MOVETO.toInt()
    }

    var joinSegment = false
    open fun lineJoin() {
        // System.out.println("LineStroker.lineJoin()");
        joinSegment = true
    }

    open fun lineTo(x1: Int, y1: Int, c1: Int) {
        // System.out.println("LineStroker.lineTo(" + x1/65536.0 + ", " + y1/65536.0 + ")");
        if (lineToOrigin) {
            if (x1 == sx0 && y1 == sy0) {
                // staying in the starting point
                return
            }

            // not closing the path, do the previous lineTo
            lineToImpl(sx0, sy0, scolor0, joinToOrigin)
            lineToOrigin = false
        } else if (x1 == x0 && y1 == y0) {
            return
        } else if (x1 == sx0 && y1 == sy0) {
            lineToOrigin = true
            joinToOrigin = joinSegment
            joinSegment = false
            return
        }
        lineToImpl(x1, y1, c1, joinSegment)
        joinSegment = false
    }

    private fun lineToImpl(x1: Int, y1: Int, c1: Int, joinSegment: Boolean) {
        computeOffset(x0, y0, x1, y1, offset)
        val mx = offset[0]
        val my = offset[1]
        if (!started) {
            emitMoveTo(x0 + mx, y0 + my, color0)
            sx1 = x1
            sy1 = y1
            mx0 = mx
            my0 = my
            started = true
        } else {
            val ccw = isCCW(px0, py0, x0, y0, x1, y1)
            if (joinSegment) {
                if (joinStyle == LinePath.JOIN_MITER) {
                    drawMiter(px0, py0, x0, y0, x1, y1, omx, omy, mx, my, color0, ccw)
                } else if (joinStyle == LinePath.JOIN_ROUND) {
                    drawRoundJoin(x0, y0, omx, omy, mx, my, 0, color0, false, ccw,
                            ROUND_JOIN_THRESHOLD)
                }
            } else {
                // Draw internal joins as round
                drawRoundJoin(x0, y0, omx, omy, mx, my, 0, color0, false, ccw,
                        ROUND_JOIN_INTERNAL_THRESHOLD)
            }
            emitLineTo(x0, y0, color0, !ccw)
        }
        emitLineTo(x0 + mx, y0 + my, color0, false)
        emitLineTo(x1 + mx, y1 + my, c1, false)
        emitLineTo(x0 - mx, y0 - my, color0, true)
        emitLineTo(x1 - mx, y1 - my, c1, true)
        omx = mx
        omy = my
        px0 = x0
        py0 = y0
        pcolor0 = color0
        x0 = x1
        y0 = y1
        color0 = c1
        prev = LinePath.SEG_LINETO.toInt()
    }

    open fun close() {
        if (lineToOrigin) {
            // ignore the previous lineTo
            lineToOrigin = false
        }
        if (!started) {
            finish()
            return
        }
        computeOffset(x0, y0, sx0, sy0, offset)
        val mx = offset[0]
        val my = offset[1]

        // Draw penultimate join
        var ccw = isCCW(px0, py0, x0, y0, sx0, sy0)
        if (joinSegment) {
            if (joinStyle == LinePath.JOIN_MITER) {
                drawMiter(px0, py0, x0, y0, sx0, sy0, omx, omy, mx, my, pcolor0, ccw)
            } else if (joinStyle == LinePath.JOIN_ROUND) {
                drawRoundJoin(x0, y0, omx, omy, mx, my, 0, color0, false, ccw,
                        ROUND_JOIN_THRESHOLD)
            }
        } else {
            // Draw internal joins as round
            drawRoundJoin(x0, y0, omx, omy, mx, my, 0, color0, false, ccw,
                    ROUND_JOIN_INTERNAL_THRESHOLD)
        }
        emitLineTo(x0 + mx, y0 + my, color0)
        emitLineTo(sx0 + mx, sy0 + my, scolor0)
        ccw = isCCW(x0, y0, sx0, sy0, sx1, sy1)

        // Draw final join on the outside
        if (!ccw) {
            if (joinStyle == LinePath.JOIN_MITER) {
                drawMiter(x0, y0, sx0, sy0, sx1, sy1, mx, my, mx0, my0, color0, false)
            } else if (joinStyle == LinePath.JOIN_ROUND) {
                drawRoundJoin(sx0, sy0, mx, my, mx0, my0, 0, scolor0, false, false,
                        ROUND_JOIN_THRESHOLD)
            }
        }
        emitLineTo(sx0 + mx0, sy0 + my0, scolor0)
        emitLineTo(sx0 - mx0, sy0 - my0, scolor0) // same as reverse[0], reverse[1]

        // Draw final join on the inside
        if (ccw) {
            if (joinStyle == LinePath.JOIN_MITER) {
                drawMiter(x0, y0, sx0, sy0, sx1, sy1, -mx, -my, -mx0, -my0, color0,
                        false)
            } else if (joinStyle == LinePath.JOIN_ROUND) {
                drawRoundJoin(sx0, sy0, -mx, -my, -mx0, -my0, 0, scolor0, true, false,
                        ROUND_JOIN_THRESHOLD)
            }
        }
        emitLineTo(sx0 - mx, sy0 - my, scolor0)
        emitLineTo(x0 - mx, y0 - my, color0)
        var i = rindex - 3
        while (i >= 0) {
            emitLineTo(reverse[i], reverse[i + 1], reverse[i + 2])
            i -= 3
        }
        x0 = sx0
        y0 = sy0
        rindex = 0
        started = false
        joinSegment = false
        prev = LinePath.SEG_CLOSE.toInt()
        emitClose()
    }

    open fun end() {
        if (lineToOrigin) {
            // not closing the path, do the previous lineTo
            lineToImpl(sx0, sy0, scolor0, joinToOrigin)
            lineToOrigin = false
        }
        if (prev == LinePath.SEG_LINETO.toInt()) {
            finish()
        }
        output!!.end()
        joinSegment = false
        prev = LinePath.SEG_MOVETO.toInt()
    }

    fun lineLength(ldx: Long, ldy: Long): Long {
        val ldet = m00.toLong() * m11 - m01.toLong() * m10 shr 16
        val la = (ldy * m00 - ldx * m10) / ldet
        val lb = (ldy * m01 - ldx * m11) / ldet
        val llen: Long = hypot(la, lb)
        return llen
    }

    private fun finish() {
        if (capStyle == LinePath.CAP_ROUND) {
            drawRoundJoin(x0, y0, omx, omy, -omx, -omy, 1, color0, false, false,
                    ROUND_JOIN_THRESHOLD)
        } else if (capStyle == LinePath.CAP_SQUARE) {
            val ldx = px0 - x0.toLong()
            val ldy = py0 - y0.toLong()
            val llen = lineLength(ldx, ldy)
            if (0 < llen) {
                val s = lineWidth2.toLong() * 65536 / llen
                val capx = x0 - (ldx * s shr 16).toInt()
                val capy = y0 - (ldy * s shr 16).toInt()
                emitLineTo(capx + omx, capy + omy, color0)
                emitLineTo(capx - omx, capy - omy, color0)
            }
        }
        var i = rindex - 3
        while (i >= 0) {
            emitLineTo(reverse[i], reverse[i + 1], reverse[i + 2])
            i -= 3
        }
        rindex = 0
        if (capStyle == LinePath.CAP_ROUND) {
            drawRoundJoin(sx0, sy0, -mx0, -my0, mx0, my0, 1, scolor0, false, false,
                    ROUND_JOIN_THRESHOLD)
        } else if (capStyle == LinePath.CAP_SQUARE) {
            val ldx = sx1 - sx0.toLong()
            val ldy = sy1 - sy0.toLong()
            val llen = lineLength(ldx, ldy)
            if (0 < llen) {
                val s = lineWidth2.toLong() * 65536 / llen
                val capx = sx0 - (ldx * s shr 16).toInt()
                val capy = sy0 - (ldy * s shr 16).toInt()
                emitLineTo(capx - mx0, capy - my0, scolor0)
                emitLineTo(capx + mx0, capy + my0, scolor0)
            }
        }
        emitClose()
        joinSegment = false
    }

    private fun emitMoveTo(x0: Int, y0: Int, c0: Int) {
        output!!.moveTo(x0, y0, c0)
    }

    private fun emitLineTo(x1: Int, y1: Int, c1: Int) {
        output!!.lineTo(x1, y1, c1)
    }

    private fun emitLineTo(x1: Int, y1: Int, c1: Int, rev: Boolean) {
        if (rev) {
            ensureCapacity(rindex + 3)
            reverse[rindex++] = x1
            reverse[rindex++] = y1
            reverse[rindex++] = c1
        } else {
            emitLineTo(x1, y1, c1)
        }
    }

    private fun emitClose() {
        output!!.close()
    }

    companion object {
        //private static final long ROUND_JOIN_THRESHOLD = 1000L;
        private const val ROUND_JOIN_THRESHOLD = 100000000L
        private const val ROUND_JOIN_INTERNAL_THRESHOLD = 1000000000L
    }
}