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
 * @author Aditya Rana
 */
interface PMatrix {

    fun reset()

    /**
     * Returns a copy of this PMatrix.
     */
    fun get(): PMatrix?

    /**
     * Copies the matrix contents into a float array.
     * If target is null (or not the correct size), a new array will be created.
     */
    operator fun get(target: FloatArray?): FloatArray?

    fun set(src: PMatrix?)

    fun set(source: FloatArray?)

    operator fun set(m00: Float, m01: Float, m02: Float,
                     m10: Float, m11: Float, m12: Float)

    operator fun set(m00: Float, m01: Float, m02: Float, m03: Float,
                     m10: Float, m11: Float, m12: Float, m13: Float,
                     m20: Float, m21: Float, m22: Float, m23: Float,
                     m30: Float, m31: Float, m32: Float, m33: Float)


    fun translate(tx: Float, ty: Float)

    fun translate(tx: Float, ty: Float, tz: Float)

    fun rotate(angle: Float)

    fun rotateX(angle: Float)

    fun rotateY(angle: Float)

    fun rotateZ(angle: Float)

    fun rotate(angle: Float, v0: Float, v1: Float, v2: Float)

    fun scale(s: Float)

    fun scale(sx: Float, sy: Float)

    fun scale(x: Float, y: Float, z: Float)

    fun shearX(angle: Float)

    fun shearY(angle: Float)


    /**
     * Multiply this matrix by another.
     */
    fun apply(source: PMatrix?)

    fun apply(source: PMatrix2D?)

    fun apply(source: PMatrix3D?)

    fun apply(n00: Float, n01: Float, n02: Float,
              n10: Float, n11: Float, n12: Float)

    fun apply(n00: Float, n01: Float, n02: Float, n03: Float,
              n10: Float, n11: Float, n12: Float, n13: Float,
              n20: Float, n21: Float, n22: Float, n23: Float,
              n30: Float, n31: Float, n32: Float, n33: Float)


    /**
     * Apply another matrix to the left of this one.
     */
    fun preApply(left: PMatrix?)

    /**
     * Apply another matrix to the left of this one.
     */
    fun preApply(left: PMatrix2D?)

    /**
     * Apply another matrix to the left of this one. 3D only.
     */
    fun preApply(left: PMatrix3D?)

    /**
     * Apply another matrix to the left of this one.
     */
    fun preApply(n00: Float, n01: Float, n02: Float,
                 n10: Float, n11: Float, n12: Float)

    /**
     * Apply another matrix to the left of this one. 3D only.
     */
    fun preApply(n00: Float, n01: Float, n02: Float, n03: Float,
                 n10: Float, n11: Float, n12: Float, n13: Float,
                 n20: Float, n21: Float, n22: Float, n23: Float,
                 n30: Float, n31: Float, n32: Float, n33: Float)


    /**
     * Multiply source by this matrix, and return the result.
     * The result will be stored in target if target is non-null, and target
     * will then be the matrix returned. This improves performance if you reuse
     * target, so it's recommended if you call this many times in draw().
     */
    fun mult(source: PVector?, target: PVector?): PVector?

    /**
     * Multiply a multi-element vector against this matrix.
     * Supplying and recycling a target array improves performance, so it's
     * recommended if you call this many times in draw().
     */
    fun mult(source: FloatArray?, target: FloatArray?): FloatArray?


    //  public float multX(float x, float y);
    //  public float multY(float x, float y);

    //  public float multX(float x, float y, float z);
    //  public float multY(float x, float y, float z);
    //  public float multZ(float x, float y, float z);


    /**
     * Transpose this matrix; rows become columns and columns rows.
     */
    fun transpose()

    /**
     * Invert this matrix. Will not necessarily succeed, because some matrices
     * map more than one point to the same image point, and so are irreversible.
     * @return true if successful
     */
    fun invert(): Boolean

    /**
     * @return the determinant of the matrix
     */
    fun determinant(): Float
}