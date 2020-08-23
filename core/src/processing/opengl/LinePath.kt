/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package processing.opengl

import processing.core.PMatrix2D
import processing.opengl.LinePath.PathIterator

/**
 * @author Processing migrated to kotlin: Aditya Rana
 * The `LinePath` class allows to represent polygonal paths,
 * potentially composed by several disjoint polygonal segments.
 * It can be iterated by the [PathIterator] class including all
 * of its segment types and winding rules
 *
 */
open class LinePath @JvmOverloads constructor(rule: Int = WIND_NON_ZERO, initialCapacity: Int = INIT_SIZE) {

    protected var pointTypes: ByteArray
    protected var floatCoords: FloatArray
    protected var pointColors: IntArray

    protected var numTypes  = 0
    protected var numCoords = 0
    private var windingRule = 0

    fun needRoom(needMove: Boolean, newPoints: Int) {
        if (needMove && numTypes == 0) {
            throw RuntimeException("missing initial moveto "
                    + "in path definition")
        }

        var size = pointTypes.size

        if (numTypes >= size) {
            var grow = size
            if (grow > EXPAND_MAX) {
                grow = EXPAND_MAX
            }
            pointTypes = copyOf(pointTypes, size + grow)
        }

        size = floatCoords.size

        if (numCoords + newPoints * 2 > size) {
            var grow = size
            if (grow > EXPAND_MAX * 2) {
                grow = EXPAND_MAX * 2
            }
            if (grow < newPoints * 2) {
                grow = newPoints * 2
            }
            floatCoords = copyOf(floatCoords, size + grow)
        }

        size = pointColors.size

        if (numCoords / 2 + newPoints > size) {
            var grow = size
            if (grow > EXPAND_MAX) {
                grow = EXPAND_MAX
            }
            if (grow < newPoints) {
                grow = newPoints
            }
            pointColors = copyOf(pointColors, size + grow)
        }
    }

    /**
     * Adds a point to the path by moving to the specified coordinates specified
     * in float precision.
     *
     *
     * This method provides a single precision variant of the double precision
     * `moveTo()` method on the base `LinePath` class.
     *
     * @param x
     * the specified X coordinate
     * @param y
     * the specified Y coordinate
     * @see LinePath.moveTo
     */
    fun moveTo(x: Float, y: Float, c: Int) {
        if (numTypes > 0 && pointTypes[numTypes - 1] == SEG_MOVETO) {
            floatCoords[numCoords - 2] = x
            floatCoords[numCoords - 1] = y
            pointColors[numCoords / 2 - 1] = c
        } else {
            needRoom(false, 1)
            pointTypes[numTypes++] = SEG_MOVETO
            floatCoords[numCoords++] = x
            floatCoords[numCoords++] = y
            pointColors[numCoords / 2 - 1] = c
        }
    }

    /**
     * Adds a point to the path by drawing a straight line from the current
     * coordinates to the new specified coordinates specified in float precision.
     *
     *
     * This method provides a single precision variant of the double precision
     * `lineTo()` method on the base `LinePath` class.
     *
     * @param x
     * the specified X coordinate
     * @param y
     * the specified Y coordinate
     * @see LinePath.lineTo
     */
    fun lineTo(x: Float, y: Float, c: Int) {
        needRoom(true, 1)
        pointTypes[numTypes++] = SEG_LINETO
        floatCoords[numCoords++] = x
        floatCoords[numCoords++] = y
        pointColors[numCoords / 2 - 1] = c
    }

    /**
     * The iterator for this class is not multi-threaded safe, which means that
     * the `LinePath` class does not guarantee that modifications to the
     * geometry of this `LinePath` object do not affect any iterations of that
     * geometry that are already in process.
     */
    val pathIterator: PathIterator
        get() = PathIterator(this)

    /**
     * Closes the current subpath by drawing a straight line back to the
     * coordinates of the last `moveTo`. If the path is already closed then
     * this method has no effect.
     */
    fun closePath() {
        if (numTypes == 0 || pointTypes[numTypes - 1] != SEG_CLOSE) {
            needRoom(false, 0)
            pointTypes[numTypes++] = SEG_CLOSE
        }
    }

    /**
     * Returns the fill style winding rule.
     *
     * @return an integer representing the current winding rule.
     * @see .WIND_EVEN_ODD
     *
     * @see .WIND_NON_ZERO
     *
     * @see .setWindingRule
     */
    fun getWindingRule(): Int {
        return windingRule
    }

    /**
     * Sets the winding rule for this path to the specified value.
     *
     * @param rule
     * an integer representing the specified winding rule
     * @exception IllegalArgumentException
     * if `rule` is not either [.WIND_EVEN_ODD] or
     * [.WIND_NON_ZERO]
     * @see .getWindingRule
     */
    fun setWindingRule(rule: Int) {
        require(!(rule != WIND_EVEN_ODD && rule != WIND_NON_ZERO)) {
            ("winding rule must be "
                    + "WIND_EVEN_ODD or " + "WIND_NON_ZERO")
        }
        windingRule = rule
    }

    /**
     * Resets the path to empty. The append position is set back to the beginning
     * of the path and all coordinates and point types are forgotten.
     */
    fun reset() {
        numCoords = 0
        numTypes = numCoords
    }

    class PathIterator internal constructor(@JvmField var path: LinePath) {
        var floatCoords: FloatArray
        var typeIdx = 0

        var pointIdx: Int
        var colorIdx: Int

        fun currentSegment(coords: FloatArray): Int {
            val type = path.pointTypes[typeIdx].toInt()
            val numCoords = curvecoords[type]
            if (numCoords > 0) {
                System.arraycopy(floatCoords, pointIdx, coords, 0, numCoords)
                val color = path.pointColors[colorIdx]
                coords[numCoords + 0] = (color shr 24 and 0xFF).toFloat()
                coords[numCoords + 1] = (color shr 16 and 0xFF).toFloat()
                coords[numCoords + 2] = (color shr 8 and 0xFF).toFloat()
                coords[numCoords + 3] = (color shr 0 and 0xFF).toFloat()
            }
            return type
        }

        fun currentSegment(coords: DoubleArray): Int {
            val type = path.pointTypes[typeIdx].toInt()
            val numCoords = curvecoords[type]
            if (numCoords > 0) {
                for (i in 0 until numCoords) {
                    coords[i] = floatCoords[pointIdx + i].toDouble()
                }
                val color = path.pointColors[colorIdx]
                coords[numCoords + 0] = (color shr 24 and 0xFF).toDouble()
                coords[numCoords + 1] = (color shr 16 and 0xFF).toDouble()
                coords[numCoords + 2] = (color shr 8 and 0xFF).toDouble()
                coords[numCoords + 3] = (color shr 0 and 0xFF).toDouble()
            }
            return type
        }

        fun getWindingRule(): Int {
            return path.getWindingRule()
        }

        val isDone: Boolean
            get() = typeIdx >= path.numTypes

        operator fun next() {
            val type = path.pointTypes[typeIdx++].toInt()
            if (0 < curvecoords[type]) {
                pointIdx += curvecoords[type]
                colorIdx++
            }
        }

        companion object {
            val curvecoords = intArrayOf(2, 2, 0)
        }

        init {
            floatCoords = path.floatCoords
            pointIdx = 0
            colorIdx = 0
        }
    }

    companion object {
        /**
         * The winding rule constant for specifying an even-odd rule
         * for determining the interior of a path.
         * The even-odd rule specifies that a point lies inside the
         * path if a ray drawn in any direction from that point to
         * infinity is crossed by path segments an odd number of times.
         */
        const val WIND_EVEN_ODD = 0

        /**
         * The winding rule constant for specifying a non-zero rule
         * for determining the interior of a path.
         * The non-zero rule specifies that a point lies inside the
         * path if a ray drawn in any direction from that point to
         * infinity is crossed by path segments a different number
         * of times in the counter-clockwise direction than the
         * clockwise direction.
         */
        const val WIND_NON_ZERO = 1

        /**
         * Starts segment at a given position.
         */
        const val SEG_MOVETO: Byte = 0

        /**
         * Extends segment by adding a line to a given position.
         */
        const val SEG_LINETO: Byte = 1

        /**
         * Closes segment at current position.
         */
        const val SEG_CLOSE: Byte = 2

        /**
         * Joins path segments by extending their outside edges until they meet.
         */
        const val JOIN_MITER = 0

        /**
         * Joins path segments by rounding off the corner at a radius of half the line
         * width.
         */
        const val JOIN_ROUND = 1

        /**
         * Joins path segments by connecting the outer corners of their wide outlines
         * with a straight segment.
         */
        const val JOIN_BEVEL = 2

        /**
         * Ends unclosed subpaths and dash segments with no added decoration.
         */
        const val CAP_BUTT = 0

        /**
         * Ends unclosed subpaths and dash segments with a round decoration that has a
         * radius equal to half of the width of the pen.
         */
        const val CAP_ROUND = 1

        /**
         * Ends unclosed subpaths and dash segments with a square projection that
         * extends beyond the end of the segment to a distance equal to half of the
         * line width.
         */
        const val CAP_SQUARE = 2
        private val identity = PMatrix2D()
        private const val defaultMiterlimit = 10.0f
        const val INIT_SIZE = 20
        const val EXPAND_MAX = 500

        /**
         * Constructs a solid `LinePath` with the specified attributes.
         *
         * @param src
         * the original path to be stroked
         * @param weight
         * the weight of the stroked path
         * @param cap
         * the decoration of the ends of the segments in the path
         * @param join
         * the decoration applied where path segments meet
         * @param miterlimit
         * @param transform
         */

        /////////////////////////////////////////////////////////////////////////////

        // Stroked path methods


        @JvmStatic
        @JvmOverloads
        fun createStrokedPath(src: LinePath, weight: Float,
                              caps: Int, join: Int,
                              miterlimit: Float = defaultMiterlimit, transform: PMatrix2D? = null): LinePath {
            val dest = LinePath()
            strokeTo(src, weight, caps, join, miterlimit, transform, object : LineStroker() {
                override fun moveTo(x0: Int, y0: Int, c0: Int) {
                    dest.moveTo(S15_16ToFloat(x0), S15_16ToFloat(y0), c0)
                }

                override fun lineJoin() {

                }

                override fun lineTo(x1: Int, y1: Int, c1: Int) {
                    dest.lineTo(S15_16ToFloat(x1), S15_16ToFloat(y1), c1)
                }

                override fun close() {
                    dest.closePath()
                }

                override fun end() {}
            })
            return dest
        }

        @JvmStatic
        private fun strokeTo(src: LinePath, width: Float, caps: Int, join: Int,
                             miterlimit: Float, transform: PMatrix2D?,
                             lsink: LineStroker) {
            var lsink: LineStroker? = lsink
            lsink = LineStroker(lsink, FloatToS15_16(width), caps, join,
                    FloatToS15_16(miterlimit),
                    transform ?: identity)
            val pi = src.pathIterator
            pathTo(pi, lsink)
        }

        @JvmStatic
        private fun pathTo(pi: PathIterator, lsink: LineStroker) {
            val coords = FloatArray(6)
            while (!pi.isDone) {
                var color: Int
                when (pi.currentSegment(coords)) {

                    SEG_MOVETO.toInt() -> {
                        color = coords[2].toInt() shl 24 or
                                (coords[3].toInt() shl 16) or
                                (coords[4].toInt() shl 8) or
                                coords[5].toInt()
                        lsink.moveTo(FloatToS15_16(coords[0]), FloatToS15_16(coords[1]), color)
                    }

                    SEG_LINETO.toInt() -> {
                        color = coords[2].toInt() shl 24 or
                                (coords[3].toInt() shl 16) or
                                (coords[4].toInt() shl 8) or
                                coords[5].toInt()
                        lsink.lineJoin()
                        lsink.lineTo(FloatToS15_16(coords[0]), FloatToS15_16(coords[1]), color)
                    }

                    SEG_CLOSE.toInt() -> {
                        lsink.lineJoin()
                        lsink.close()
                    }

                    else -> throw InternalError("unknown flattened segment type")
                }
                pi.next()
            }
            lsink.end()
        }

        /////////////////////////////////////////////////////////////////////////////

        // Utility methods


        @JvmStatic
        fun copyOf(source: FloatArray, length: Int): FloatArray {
            val target = FloatArray(length)
            for (i in target.indices) {
                if (i > source.size - 1) target[i] = 0f else target[i] = source[i]
            }
            return target
        }

        @JvmStatic
        fun copyOf(source: ByteArray, length: Int): ByteArray {
            val target = ByteArray(length)
            for (i in target.indices) {
                if (i > source.size - 1) target[i] = 0 else target[i] = source[i]
            }
            return target
        }

        @JvmStatic
        fun copyOf(source: IntArray, length: Int): IntArray {
            val target = IntArray(length)
            for (i in target.indices) {
                if (i > source.size - 1) target[i] = 0 else target[i] = source[i]
            }
            return target
        }

        // From Ken Turkowski, _Fixed-Point Square Root_, In Graphics Gems V
        @JvmStatic
        fun isqrt(x: Int): Int {
            val fracbits = 16
            var root = 0
            var remHi = 0
            var remLo = x

            var count = 15 + fracbits / 2

            do {
                remHi = remHi shl 2 or (remLo ushr 30) // N.B. - unsigned shift R
                remLo = remLo shl 2
                root = root shl 1
                val testdiv = (root shl 1) + 1
                if (remHi >= testdiv) {
                    remHi -= testdiv
                    root++
                }
            } while (count-- != 0)
            return root
        }

        @JvmStatic
        fun lsqrt(x: Long): Long {
            val fracbits = 16
            var root: Long = 0
            var remHi: Long = 0
            var remLo = x
            var count = 31 + fracbits / 2
            do {
                remHi = remHi shl 2 or (remLo ushr 62) // N.B. - unsigned shift R
                remLo = remLo shl 2
                root = root shl 1
                val testDiv = (root shl 1) + 1
                if (remHi >= testDiv) {
                    remHi -= testDiv
                    root++
                }
            } while (count-- != 0)
            return root
        }

        @JvmStatic
        fun hypot(x: Double, y: Double): Double {
            return Math.sqrt(x * x + y * y)
        }

        @JvmStatic
        fun hypot(x: Int, y: Int): Int {
            return (lsqrt(x.toLong() * x + y.toLong() * y) + 128 shr 8).toInt()
        }

        @JvmStatic
        fun hypot(x: Long, y: Long): Long {
            return lsqrt(x * x + y * y) + 128 shr 8
        }

        @JvmStatic
        fun FloatToS15_16(flt: Float): Int {
            var flt = flt
            flt = flt * 65536f + 0.5f
            return if (flt <= -(65536f * 65536f)) {
                Int.MIN_VALUE
            } else if (flt >= 65536f * 65536f) {
                Int.MAX_VALUE
            } else {
                Math.floor(flt.toDouble()).toInt()
            }
        }

        @JvmStatic
        fun S15_16ToFloat(fix: Int): Float {
            return fix / 65536f
        }
    }

    /**
     * Constructs a new `LinePath` object from the given specified initial
     * values. This method is only intended for internal use and should not be
     * made public if the other constructors for this class are ever exposed.
     *
     * @param rule
     * the winding rule
     * @param initialTypes
     * the size to make the initial array to store the path segment types
     */
    /**
     * Constructs a new empty single precision `LinePath` object with a
     * default winding rule of [.WIND_NON_ZERO].
     */
    /**
     * Constructs a new empty single precision `LinePath` object with the
     * specified winding rule to control operations that require the interior of
     * the path to be defined.
     *
     * @param rule
     * the winding rule
     * @see .WIND_EVEN_ODD
     *
     * @see .WIND_NON_ZERO
     */
    init {
        setWindingRule(rule)
        pointTypes = ByteArray(initialCapacity)
        floatCoords = FloatArray(initialCapacity * 2)
        pointColors = IntArray(initialCapacity)
    }
}