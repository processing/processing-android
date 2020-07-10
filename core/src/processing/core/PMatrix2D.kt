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

package processing.core

/**
 * 3x2 affine matrix implementation.
 */
open class PMatrix2D : PMatrix {
    @JvmField
    var m00 = 0f

    @JvmField
    var m01 = 0f

    @JvmField
    var m02 = 0f

    @JvmField
    var m10 = 0f

    @JvmField
    var m11 = 0f

    @JvmField
    var m12 = 0f

    constructor() {
        reset()
    }

    constructor(m00: Float, m01: Float, m02: Float,
                m10: Float, m11: Float, m12: Float) {
        set(m00, m01, m02,
                m10, m11, m12)
    }

    constructor(matrix: PMatrix?) {
        set(matrix)
    }

    override fun reset() {
        set(1f, 0f, 0f, 0f, 1f, 0f)
    }

    /**
     * Returns a copy of this PMatrix.
     */
    override fun get(): PMatrix2D? {
        val outgoing = PMatrix2D()
        outgoing.set(this)
        return outgoing
    }

    /**
     * Copies the matrix contents into a 6 entry float array.
     * If target is null (or not the correct size), a new array will be created.
     */
    override fun get(target: FloatArray?): FloatArray? {
        var target = target
        if (target == null || target.size != 6) {
            target = FloatArray(6)
        }
        target[0] = m00
        target[1] = m01
        target[2] = m02
        target[3] = m10
        target[4] = m11
        target[5] = m12
        return target
    }

    override fun set(matrix: PMatrix?) {
        if (matrix is PMatrix2D) {
            val src = matrix
            set(src.m00, src.m01, src.m02,
                    src.m10, src.m11, src.m12)
        } else {
            throw IllegalArgumentException("PMatrix2D.set() only accepts PMatrix2D objects.")
        }
    }

    fun set(src: PMatrix3D?) {}
    override fun set(source: FloatArray?) {
        m00 = source!![0]
        m01 = source[1]
        m02 = source[2]
        m10 = source[3]
        m11 = source[4]
        m12 = source[5]
    }

    override fun set(m00: Float, m01: Float, m02: Float,
                     m10: Float, m11: Float, m12: Float) {
        this.m00 = m00
        this.m01 = m01
        this.m02 = m02
        this.m10 = m10
        this.m11 = m11
        this.m12 = m12
    }

    override fun set(m00: Float, m01: Float, m02: Float, m03: Float,
                     m10: Float, m11: Float, m12: Float, m13: Float,
                     m20: Float, m21: Float, m22: Float, m23: Float,
                     m30: Float, m31: Float, m32: Float, m33: Float) {
    }

    override fun translate(tx: Float, ty: Float) {
        m02 = tx * m00 + ty * m01 + m02
        m12 = tx * m10 + ty * m11 + m12
    }

    override fun translate(x: Float, y: Float, z: Float) {
        throw IllegalArgumentException("Cannot use translate(x, y, z) on a PMatrix2D.")
    }

    // Implementation roughly based on AffineTransform.
    override fun rotate(angle: Float) {
        val s = sin(angle)
        val c = cos(angle)
        var temp1 = m00
        var temp2 = m01
        m00 = c * temp1 + s * temp2
        m01 = -s * temp1 + c * temp2
        temp1 = m10
        temp2 = m11
        m10 = c * temp1 + s * temp2
        m11 = -s * temp1 + c * temp2
    }

    override fun rotateX(angle: Float) {
        throw IllegalArgumentException("Cannot use rotateX() on a PMatrix2D.")
    }

    override fun rotateY(angle: Float) {
        throw IllegalArgumentException("Cannot use rotateY() on a PMatrix2D.")
    }

    override fun rotateZ(angle: Float) {
        rotate(angle)
    }

    override fun rotate(angle: Float, v0: Float, v1: Float, v2: Float) {
        throw IllegalArgumentException("Cannot use this version of rotate() on a PMatrix2D.")
    }

    override fun scale(s: Float) {
        scale(s, s)
    }

    override fun scale(sx: Float, sy: Float) {
        m00 *= sx
        m01 *= sy
        m10 *= sx
        m11 *= sy
    }

    override fun scale(x: Float, y: Float, z: Float) {
        throw IllegalArgumentException("Cannot use this version of scale() on a PMatrix2D.")
    }

    override fun shearX(angle: Float) {
        apply(1f, 0f, 1f, tan(angle), 0f, 0f)
    }

    override fun shearY(angle: Float) {
        apply(1f, 0f, 1f, 0f, tan(angle), 0f)
    }

    override fun apply(source: PMatrix?) {
        if (source is PMatrix2D) {
            apply(source as PMatrix2D?)
        } else if (source is PMatrix3D) {
            apply(source as PMatrix3D?)
        }
    }

    override fun apply(source: PMatrix2D?) {
        apply(source!!.m00, source.m01, source.m02,
                source.m10, source.m11, source.m12)
    }

    override fun apply(source: PMatrix3D?) {
        throw IllegalArgumentException("Cannot use apply(PMatrix3D) on a PMatrix2D.")
    }

    override fun apply(n00: Float, n01: Float, n02: Float,
                       n10: Float, n11: Float, n12: Float) {
        var t0 = m00
        var t1 = m01
        m00 = n00 * t0 + n10 * t1
        m01 = n01 * t0 + n11 * t1
        m02 += n02 * t0 + n12 * t1
        t0 = m10
        t1 = m11
        m10 = n00 * t0 + n10 * t1
        m11 = n01 * t0 + n11 * t1
        m12 += n02 * t0 + n12 * t1
    }

    override fun apply(n00: Float, n01: Float, n02: Float, n03: Float,
                       n10: Float, n11: Float, n12: Float, n13: Float,
                       n20: Float, n21: Float, n22: Float, n23: Float,
                       n30: Float, n31: Float, n32: Float, n33: Float) {
        throw IllegalArgumentException("Cannot use this version of apply() on a PMatrix2D.")
    }

    /**
     * Apply another matrix to the left of this one.
     */
    override fun preApply(source: PMatrix?) {
        if (source is PMatrix2D) {
            preApply(source as PMatrix2D?)
        } else if (source is PMatrix3D) {
            preApply(source as PMatrix3D?)
        }
    }

    /**
     * Apply another matrix to the left of this one.
     */
    override fun preApply(left: PMatrix2D?) {
        preApply(left!!.m00, left.m01, left.m02,
                left.m10, left.m11, left.m12)
    }

    override fun preApply(left: PMatrix3D?) {
        throw IllegalArgumentException("Cannot use preApply(PMatrix3D) on a PMatrix2D.")
    }

    override fun preApply(n00: Float, n01: Float, n02: Float,
                          n10: Float, n11: Float, n12: Float) {
        var n02 = n02
        var n12 = n12
        var t0 = m02
        var t1 = m12
        n02 += t0 * n00 + t1 * n01
        n12 += t0 * n10 + t1 * n11
        m02 = n02
        m12 = n12
        t0 = m00
        t1 = m10
        m00 = t0 * n00 + t1 * n01
        m10 = t0 * n10 + t1 * n11
        t0 = m01
        t1 = m11
        m01 = t0 * n00 + t1 * n01
        m11 = t0 * n10 + t1 * n11
    }

    override fun preApply(n00: Float, n01: Float, n02: Float, n03: Float,
                          n10: Float, n11: Float, n12: Float, n13: Float,
                          n20: Float, n21: Float, n22: Float, n23: Float,
                          n30: Float, n31: Float, n32: Float, n33: Float) {
        throw IllegalArgumentException("Cannot use this version of preApply() on a PMatrix2D.")
    }
    //////////////////////////////////////////////////////////////
    /**
     * Multiply the x and y coordinates of a PVector against this matrix.
     */
    override fun mult(source: PVector?, target: PVector?): PVector? {
        var target = target
        if (target == null) {
            target = PVector()
        }
        target.x = m00 * source!!.x + m01 * source.y + m02
        target.y = m10 * source.x + m11 * source.y + m12
        return target
    }

    /**
     * Multiply a two element vector against this matrix.
     * If out is null or not length four, a new float array will be returned.
     * The values for vec and out can be the same (though that's less efficient).
     */
    override fun mult(vec: FloatArray?, out: FloatArray?): FloatArray? {
        var out = out
        if (out == null || out.size != 2) {
            out = FloatArray(2)
        }
        if (vec == out) {
            val tx = m00 * vec[0] + m01 * vec[1] + m02
            val ty = m10 * vec[0] + m11 * vec[1] + m12
            out[0] = tx
            out[1] = ty
        } else {
            out[0] = m00 * vec!![0] + m01 * vec[1] + m02
            out[1] = m10 * vec[0] + m11 * vec[1] + m12
        }
        return out
    }

    fun multX(x: Float, y: Float): Float {
        return m00 * x + m01 * y + m02
    }

    fun multY(x: Float, y: Float): Float {
        return m10 * x + m11 * y + m12
    }

    /**
     * Transpose this matrix.
     */
    override fun transpose() {}

    /**
     * Invert this matrix. Implementation stolen from OpenJDK.
     * @return true if successful
     */
    override fun invert(): Boolean {
        val determinant = determinant()
        if (Math.abs(determinant) <= Float.MIN_VALUE) {
            return false
        }
        val t00 = m00
        val t01 = m01
        val t02 = m02
        val t10 = m10
        val t11 = m11
        val t12 = m12
        m00 = t11 / determinant
        m10 = -t10 / determinant
        m01 = -t01 / determinant
        m11 = t00 / determinant
        m02 = (t01 * t12 - t11 * t02) / determinant
        m12 = (t10 * t02 - t00 * t12) / determinant
        return true
    }

    /**
     * @return the determinant of the matrix
     */
    override fun determinant(): Float {
        return m00 * m11 - m01 * m10
    }

    //////////////////////////////////////////////////////////////
    fun print() {
        var big = abs(max(PApplet.max(abs(m00), abs(m01), abs(m02)),
                PApplet.max(abs(m10), abs(m11), abs(m12)))).toInt()
        var digits = 1
        if (java.lang.Float.isNaN(big.toFloat()) || java.lang.Float.isInfinite(big.toFloat())) {  // avoid infinite loop
            digits = 5
        } else {
            while (10.let { big /= it; big } != 0) digits++ // cheap log()
        }
        println(PApplet.nfs(m00, digits, 4) + " " +
                PApplet.nfs(m01, digits, 4) + " " +
                PApplet.nfs(m02, digits, 4))
        println(PApplet.nfs(m10, digits, 4) + " " +
                PApplet.nfs(m11, digits, 4) + " " +
                PApplet.nfs(m12, digits, 4))
        println()
    }

    //////////////////////////////////////////////////////////////
    // TODO these need to be added as regular API, but the naming and
    // implementation needs to be improved first. (e.g. actually keeping track
    // of whether the matrix is in fact identity internally.)
    protected val isIdentity: Boolean
        protected get() = m00 == 1f && m01 == 0f && m02 == 0f &&
                m10 == 0f && m11 == 1f && m12 == 0f// was &&, but changed so shearX and shearY will work

    // TODO make this more efficient, or move into PMatrix2D
    protected val isWarped: Boolean
        protected get() =// was &&, but changed so shearX and shearY will work
            m00 != 1f || m01 != 0f ||
                    m10 != 0f || m11 != 1f

    //////////////////////////////////////////////////////////////
    private fun max(a: Float, b: Float): Float {
        return if (a > b) a else b
    }

    private fun abs(a: Float): Float {
        return if (a < 0) -a else a
    }

    private fun sin(angle: Float): Float {
        return Math.sin(angle.toDouble()).toFloat()
    }

    private fun cos(angle: Float): Float {
        return Math.cos(angle.toDouble()).toFloat()
    }

    private fun tan(angle: Float): Float {
        return Math.tan(angle.toDouble()).toFloat()
    }
}