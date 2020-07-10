/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation
  Copyright (c) 2008-12 Ben Fry and Casey Reas
  Copyright (c) 2008 Dan Shiffman


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

import java.io.Serializable
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ( begin auto-generated from PVector.xml )
 *
 * A class to describe a two or three dimensional vector. This datatype
 * stores two or three variables that are commonly used as a position,
 * velocity, and/or acceleration. Technically, *position* is a point
 * and *velocity* and *acceleration* are vectors, but this is
 * often simplified to consider all three as vectors. For example, if you
 * consider a rectangle moving across the screen, at any given instant it
 * has a position (the object's location, expressed as a point.), a
 * velocity (the rate at which the object's position changes per time unit,
 * expressed as a vector), and acceleration (the rate at which the object's
 * velocity changes per time unit, expressed as a vector). Since vectors
 * represent groupings of values, we cannot simply use traditional
 * addition/multiplication/etc. Instead, we'll need to do some "vector"
 * math, which is made easy by the methods inside the **PVector**
 * class.<br></br>
 * <br></br>
 * The methods for this class are extensive. For a complete list, visit the
 * [developer's reference.](http://processing.googlecode.com/svn/trunk/processing/build/javadoc/core/)
 *
 * ( end auto-generated )
 *
 * A class to describe a two or three dimensional vector.
 *
 *
 * The result of all functions are applied to the vector itself, with the
 * exception of cross(), which returns a new PVector (or writes to a specified
 * 'target' PVector). That is, add() will add the contents of one vector to
 * this one. Using add() with additional parameters allows you to put the
 * result into a new PVector. Functions that act on multiple vectors also
 * include static versions. Because creating new objects can be computationally
 * expensive, most functions include an optional 'target' PVector, so that a
 * new PVector object is not created with each operation.
 *
 *
 * Initially based on the Vector3D class by [Dan Shiffman](http://www.shiffman.net).
 *
 * @webref math
 */
open class PVector : Serializable {

    /**
     * ( begin auto-generated from PVector_x.xml )
     *
     * The x component of the vector. This field (variable) can be used to both
     * get and set the value (see above example.)
     *
     * ( end auto-generated )
     *
     * @webref pvector:field
     * @usage web_application
     * @brief The x component of the vector
     */
    @JvmField
    var x = 0f


    /**
     * ( begin auto-generated from PVector_y.xml )
     *
     * The y component of the vector. This field (variable) can be used to both
     * get and set the value (see above example.)
     *
     * ( end auto-generated )
     *
     * @webref pvector:field
     * @usage web_application
     * @brief The y component of the vector
     */
    @JvmField
    var y = 0f


    /**
     * ( begin auto-generated from PVector_z.xml )
     *
     * The z component of the vector. This field (variable) can be used to both
     * get and set the value (see above example.)
     *
     * ( end auto-generated )
     *
     * @webref pvector:field
     * @usage web_application
     * @brief The z component of the vector
     */
    @JvmField
    var z = 0f


    /** Array so that this can be temporarily used in an array context  */
    @Transient
    protected var array: FloatArray? = null


    /**
     * Constructor for an empty vector: x, y, and z are set to 0.
     */
    constructor() {

    }

    /**
     * Constructor for a 3D vector.
     *
     * @param  x the x coordinate.
     * @param  y the y coordinate.
     * @param  z the z coordinate.
     */
    constructor(x: Float, y: Float, z: Float) {
        this.x = x
        this.y = y
        this.z = z
    }


    /**
     * Constructor for a 2D vector: z coordinate is set to 0.
     */
    constructor(x: Float, y: Float) {
        this.x = x
        this.y = y
        z = 0f
    }


    /**
     * ( begin auto-generated from PVector_set.xml )
     *
     * Sets the x, y, and z component of the vector using two or three separate
     * variables, the data from a PVector, or the values from a float array.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @param x the x component of the vector
     * @param y the y component of the vector
     * @param z the z component of the vector
     * @brief Set the components of the vector
     */
    operator fun set(x: Float, y: Float, z: Float): PVector {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    /**
     * @param x the x component of the vector
     * @param y the y component of the vector
     */
    operator fun set(x: Float, y: Float): PVector {
        this.x = x
        this.y = y
        return this
    }

    /**
     * @param v any variable of type PVector
     */
    fun set(v: PVector): PVector {
        x = v.x
        y = v.y
        z = v.z
        return this
    }

    /**
     * Set the x, y (and maybe z) coordinates using a float[] array as the source.
     * @param source array to copy from
     */
    fun set(source: FloatArray): PVector {
        if (source.size >= 2) {
            x = source[0]
            y = source[1]
        }
        if (source.size >= 3) {
            z = source[2]
        }
        return this
    }

    /**
     * ( begin auto-generated from PVector_copy.xml )
     *
     * Gets a copy of the vector, returns a PVector object.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Get a copy of the vector
     */
    fun copy(): PVector {
        return PVector(x, y, z)
    }

    @Deprecated("", ReplaceWith("copy()"))
    fun get(): PVector {
        return copy()
    }


    /**
     * @param target
     */
    operator fun get(target: FloatArray?): FloatArray {
        if (target == null) {
            return floatArrayOf(x, y, z)
        }
        if (target.size >= 2) {
            target[0] = x
            target[1] = y
        }
        if (target.size >= 3) {
            target[2] = z
        }
        return target
    }


    /**
     * ( begin auto-generated from PVector_mag.xml )
     *
     * Calculates the magnitude (length) of the vector and returns the result
     * as a float (this is simply the equation *sqrt(x*x + y*y + z*z)*.)
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Calculate the magnitude of the vector
     * @return magnitude (length) of the vector
     * @see PVector.magSq
     */
    fun mag(): Float {
        return sqrt(x * x + y * y + (z * z).toDouble()).toFloat()
    }


    /**
     * ( begin auto-generated from PVector_mag.xml )
     *
     * Calculates the squared magnitude of the vector and returns the result
     * as a float (this is simply the equation *(x*x + y*y + z*z)*.)
     * Faster if the real length is not required in the
     * case of comparing vectors, etc.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Calculate the magnitude of the vector, squared
     * @return squared magnitude of the vector
     * @see PVector.mag
     */
    fun magSq(): Float {
        return x * x + y * y + z * z
    }


    /**
     * ( begin auto-generated from PVector_add.xml )
     *
     * Adds x, y, and z components to a vector, adds one vector to another, or
     * adds two independent vectors together. The version of the method that
     * adds two vectors together is a static method and returns a PVector, the
     * others have no return value -- they act directly on the vector. See the
     * examples for more context.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @param v the vector to be added
     * @brief Adds x, y, and z components to a vector, one vector to another, or two independent vectors
     */
    fun add(v: PVector): PVector {
        x += v.x
        y += v.y
        z += v.z
        return this
    }


    /**
     * @param x x component of the vector
     * @param y y component of the vector
     */
    fun add(x: Float, y: Float): PVector {
        this.x += x
        this.y += y
        return this
    }


    /**
     * @param z z component of the vector
     */
    fun add(x: Float, y: Float, z: Float): PVector {
        this.x += x
        this.y += y
        this.z += z
        return this
    }


    /**
     * ( begin auto-generated from PVector_sub.xml )
     *
     * Subtracts x, y, and z components from a vector, subtracts one vector
     * from another, or subtracts two independent vectors. The version of the
     * method that subtracts two vectors is a static method and returns a
     * PVector, the others have no return value -- they act directly on the
     * vector. See the examples for more context.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @param v any variable of type PVector
     * @brief Subtract x, y, and z components from a vector, one vector from another, or two independent vectors
     */
    fun sub(v: PVector): PVector {
        x -= v.x
        y -= v.y
        z -= v.z
        return this
    }


    /**
     * @param x the x component of the vector
     * @param y the y component of the vector
     */
    fun sub(x: Float, y: Float): PVector {
        this.x -= x
        this.y -= y
        return this
    }


    /**
     * @param z the z component of the vector
     */
    fun sub(x: Float, y: Float, z: Float): PVector {
        this.x -= x
        this.y -= y
        this.z -= z
        return this
    }


    /**
     * ( begin auto-generated from PVector_mult.xml )
     *
     * Multiplies a vector by a scalar or multiplies one vector by another.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Multiply a vector by a scalar
     * @param n the number to multiply with the vector
     */
    fun mult(n: Float): PVector {
        x *= n
        y *= n
        z *= n
        return this
    }


    /**
     * ( begin auto-generated from PVector_div.xml )
     *
     * Divides a vector by a scalar or divides one vector by another.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Divide a vector by a scalar
     * @param n the number by which to divide the vector
     */
    operator fun div(n: Float): PVector {
        x /= n
        y /= n
        z /= n
        return this
    }


    /**
     * ( begin auto-generated from PVector_dist.xml )
     *
     * Calculates the Euclidean distance between two points (considering a
     * point as a vector object).
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @param v the x, y, and z coordinates of a PVector
     * @brief Calculate the distance between two points
     */
    fun dist(v: PVector): Float {
        val dx = x - v.x
        val dy = y - v.y
        val dz = z - v.z
        return Math.sqrt(dx * dx + dy * dy + (dz * dz).toDouble()).toFloat()
    }


    /**
     * ( begin auto-generated from PVector_dot.xml )
     *
     * Calculates the dot product of two vectors.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @param v any variable of type PVector
     * @return the dot product
     * @brief Calculate the dot product of two vectors
     */
    fun dot(v: PVector): Float {
        return x * v.x + y * v.y + z * v.z
    }


    /**
     * @param x x component of the vector
     * @param y y component of the vector
     * @param z z component of the vector
     */
    fun dot(x: Float, y: Float, z: Float): Float {
        return this.x * x + this.y * y + this.z * z
    }


    /**
     * ( begin auto-generated from PVector_cross.xml )
     *
     * Calculates and returns a vector composed of the cross product between
     * two vectors.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @param v the vector to calculate the cross product
     * @param target PVector to store the result
     * @brief Calculate and return the cross product
     */
    @JvmOverloads
    fun cross(v: PVector, target: PVector? = null): PVector {
        var target = target
        val crossX = y * v.z - v.y * z
        val crossY = z * v.x - v.z * x
        val crossZ = x * v.y - v.x * y

        if (target == null) {
            target = PVector(crossX, crossY, crossZ)
        }
        else {
            target.set(crossX, crossY, crossZ)
        }

        return target
    }


    /**
     * ( begin auto-generated from PVector_normalize.xml )
     *
     * Normalize the vector to length 1 (make it a unit vector).
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Normalize the vector to a length of 1
     */
    fun normalize(): PVector {
        val m = mag()
        if (m != 0f && m != 1f) {
            div(m)
        }
        return this
    }


    /**
     * @param target Set to null to create a new vector
     * @return a new vector (if target was null), or target
     */
    fun normalize(target: PVector?): PVector {
        var target = target
        if (target == null) {
            target = PVector()
        }
        val m = mag()
        if (m > 0) {
            target[x / m, y / m] = z / m
        } else {
            target[x, y] = z
        }
        return target
    }


    /**
     * ( begin auto-generated from PVector_limit.xml )
     *
     * Limit the magnitude of this vector to the value used for the **max** parameter.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @param max the maximum magnitude for the vector
     * @brief Limit the magnitude of the vector
     */
    fun limit(max: Float): PVector {
        if (magSq() > max * max) {
            normalize()
            mult(max)
        }
        return this
    }


    /**
     * ( begin auto-generated from PVector_setMag.xml )
     *
     * Set the magnitude of this vector to the value used for the **len** parameter.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @param len the new length for this vector
     * @brief Set the magnitude of the vector
     */
    fun setMag(len: Float): PVector {
        normalize()
        mult(len)
        return this
    }


    /**
     * Sets the magnitude of this vector, storing the result in another vector.
     * @param target Set to null to create a new vector
     * @param len the new length for the new vector
     * @return a new vector (if target was null), or target
     */
    fun setMag(target: PVector, len: Float): PVector {
        var target = target
        target = normalize(target)
        target.mult(len)
        return target
    }

    /**
     * ( begin auto-generated from PVector_setMag.xml )
     *
     * Calculate the angle of rotation for this vector (only 2D vectors)
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @return the angle of rotation
     * @brief Calculate the angle of rotation for this vector
     */
    fun heading(): Float {
        return Math.atan2(y.toDouble(), x.toDouble()).toFloat()
    }

    @Deprecated("", ReplaceWith("heading()"))
    fun heading2D(): Float {
        return heading()
    }

    /**
     * ( begin auto-generated from PVector_rotate.xml )
     *
     * Rotate the vector by an angle (only 2D vectors), magnitude remains the same
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Rotate the vector by an angle (2D only)
     * @param theta the angle of rotation
     */
    fun rotate(theta: Float): PVector {
        val temp = x
        // Might need to check for rounding errors like with angleBetween function?
        x = x * PApplet.cos(theta) - y * PApplet.sin(theta)
        y = temp * PApplet.sin(theta) + y * PApplet.cos(theta)
        return this
    }


    /**
     * ( begin auto-generated from PVector_rotate.xml )
     *
     * Linear interpolate the vector to another vector
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage web_application
     * @brief Linear interpolate the vector to another vector
     * @param v the vector to lerp to
     * @param amt  The amount of interpolation; some value between 0.0 (old vector) and 1.0 (new vector). 0.1 is very near the old vector; 0.5 is halfway in between.
     * @see PApplet.lerp
     */
    fun lerp(v: PVector, amt: Float): PVector {
        x = PApplet.lerp(x, v.x, amt)
        y = PApplet.lerp(y, v.y, amt)
        z = PApplet.lerp(z, v.z, amt)
        return this
    }


    /**
     * Linear interpolate the vector to x,y,z values
     * @param x the x component to lerp to
     * @param y the y component to lerp to
     * @param z the z component to lerp to
     */
    fun lerp(x: Float, y: Float, z: Float, amt: Float): PVector {
        this.x = PApplet.lerp(this.x, x, amt)
        this.y = PApplet.lerp(this.y, y, amt)
        this.z = PApplet.lerp(this.z, z, amt)
        return this
    }

    override fun toString(): String {
        return "[ $x, $y, $z ]"
    }


    /**
     * ( begin auto-generated from PVector_array.xml )
     *
     * Return a representation of this vector as a float array. This is only
     * for temporary use. If used in any other fashion, the contents should be
     * copied by using the **PVector.get()** method to copy into your own array.
     *
     * ( end auto-generated )
     *
     * @webref pvector:method
     * @usage: web_application
     * @brief Return a representation of the vector as a float array
     */
    fun array(): FloatArray? {
        if (array == null) {
            array = FloatArray(3)
        }
        array!![0] = x
        array!![1] = y
        array!![2] = z
        return array
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is PVector) {
            return false
        }
        val p = obj
        return x == p.x && y == p.y && z == p.z
    }

    override fun hashCode(): Int {
        var result = 1
        result = 31 * result + java.lang.Float.floatToIntBits(x)
        result = 31 * result + java.lang.Float.floatToIntBits(y)
        result = 31 * result + java.lang.Float.floatToIntBits(z)
        return result
    }

    companion object {

        /**
         * Make a new 2D unit vector with a random direction
         * using Processing's current random number generator
         * @param parent current PApplet instance
         * @return the random PVector
         */
        @JvmStatic
        fun random2D(parent: PApplet?): PVector? {
            return random2D(null, parent)
        }

        /**
         * Make a new 2D unit vector with a random direction. Pass in the parent
         * PApplet if you want randomSeed() to work (and be predictable). Or leave
         * it null and be... random.
         * @return the random PVector
         */
        /**
         * ( begin auto-generated from PVector_random2D.xml )
         *
         * Make a new 2D unit vector with a random direction.  If you pass in "this"
         * as an argument, it will use the PApplet's random number generator.  You can
         * also pass in a target PVector to fill.
         *
         * @webref pvector:method
         * @usage web_application
         * @return the random PVector
         * @brief Make a new 2D unit vector with a random direction.
         * @see PVector.random3D
         */
        /**
         * Set a 2D vector to a random unit vector with a random direction
         * @param target the target vector (if null, a new vector will be created)
         * @return the random PVector
         */
        @JvmStatic
        @JvmOverloads
        fun random2D(target: PVector? = null, parent: PApplet? = null): PVector? {
            return if (parent == null) fromAngle((Math.random() * Math.PI * 2).toFloat(), target) else fromAngle(parent.random(PConstants.TAU), target)
        }


        /**
         * Make a new 3D unit vector with a random direction
         * using Processing's current random number generator
         * @param parent current PApplet instance
         * @return the random PVector
         */
        @JvmStatic
        fun random3D(parent: PApplet?): PVector? {
            return random3D(null, parent)
        }

        /**
         * Make a new 3D unit vector with a random direction
         * @return the random PVector
         */
        /**
         * ( begin auto-generated from PVector_random3D.xml )
         *
         * Make a new 3D unit vector with a random direction.  If you pass in "this"
         * as an argument, it will use the PApplet's random number generator.  You can
         * also pass in a target PVector to fill.
         *
         * @webref pvector:method
         * @usage web_application
         * @return the random PVector
         * @brief Make a new 3D unit vector with a random direction.
         * @see PVector.random2D
         */
        /**
         * Set a 3D vector to a random unit vector with a random direction
         * @param target the target vector (if null, a new vector will be created)
         * @return the random PVector
         */
        @JvmStatic
        @JvmOverloads
        fun random3D(target: PVector? = null, parent: PApplet? = null): PVector? {
            var target = target

            val angle: Float
            val vz: Float

            if (parent == null) {
                angle = (Math.random() * Math.PI * 2).toFloat()
                vz = (Math.random() * 2 - 1).toFloat()
            } else {
                angle = parent.random(PConstants.TWO_PI)
                vz = parent.random(-1f, 1f)
            }

            val vx = (Math.sqrt(1 - vz * vz.toDouble()) * Math.cos(angle.toDouble())).toFloat()
            val vy = (Math.sqrt(1 - vz * vz.toDouble()) * Math.sin(angle.toDouble())).toFloat()

            if (target == null) {
                target = PVector(vx, vy, vz)
                //target.normalize(); // Should be unnecessary
            }
            else {
                target.set(vx, vy, vz)
            }

            return target
        }


        /**
         * Make a new 2D unit vector from an angle
         *
         * @param target the target vector (if null, a new vector will be created)
         * @return the PVector
         */
        /**
         * ( begin auto-generated from PVector_sub.xml )
         *
         * Make a new 2D unit vector from an angle.
         *
         * ( end auto-generated )
         *
         * @webref pvector:method
         * @usage web_application
         * @brief Make a new 2D unit vector from an angle
         * @param angle the angle in radians
         * @return the new unit PVector
         */
        @JvmStatic
        @JvmOverloads
        fun fromAngle(angle: Float, target: PVector? = null): PVector? {
            var target = target

            if(target == null) {
                target = PVector(cos(angle.toDouble()).toFloat(), sin(angle.toDouble()).toFloat(), 0f)
            }
            else {
                target.set(cos(angle.toDouble()).toFloat(), sin(angle.toDouble()).toFloat(), 0f)
            }

            return target
        }


        /**
         * Add two vectors into a target vector
         * @param target the target vector (if null, a new vector will be created)
         */
        /**
         * Add two vectors
         * @param v1 a vector
         * @param v2 another vector
         */
        @JvmStatic
        @JvmOverloads
        fun add(v1: PVector, v2: PVector, target: PVector? = null): PVector {
            var target = target

            if (target == null) {
                target = PVector(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z)
            }
            else {
                target.set(v1.x + v2.x, v1.y + v2.y, v1.z + v2.z)
            }

            return target
        }


        /**
         * Subtract one vector from another and store in another vector
         * @param target PVector in which to store the result
         */
        /**
         * Subtract one vector from another
         * @param v1 the x, y, and z components of a PVector object
         * @param v2 the x, y, and z components of a PVector object
         */
        @JvmStatic
        @JvmOverloads
        fun sub(v1: PVector, v2: PVector, target: PVector? = null): PVector {
            var target = target

            if (target == null) {
                target = PVector(x = v1.x - v2.x, y = v1.y - v2.y, z = v1.z - v2.z)
            }
            else {
                target.set(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z)
            }

            return target
        }


        /**
         * Multiply a vector by a scalar, and write the result into a target PVector.
         * @param target PVector in which to store the result
         */
        /**
         * @param v the vector to multiply by the scalar
         */
        @JvmStatic
        @JvmOverloads
        fun mult(v: PVector, n: Float, target: PVector? = null): PVector {
            var target = target

            if (target == null) {
                target = PVector(v.x * n, v.y * n, v.z * n)
            }
            else {
                target.set(v.x * n, v.y * n, v.z * n)
            }

            return target
        }


        /**
         * Divide a vector by a scalar and store the result in another vector.
         * @param target PVector in which to store the result
         */
        /**
         * Divide a vector by a scalar and return the result in a new vector.
         * @param v the vector to divide by the scalar
         * @return a new vector that is v1 / n
         */
        @JvmStatic
        @JvmOverloads
        fun div(v: PVector, n: Float, target: PVector? = null): PVector {
            var target = target

            if (target == null) {
                target = PVector(v.x / n, v.y / n, v.z / n)
            }
            else {
                target.set(v.x / n, v.y / n, v.z / n)
            }

            return target
        }


        /**
         * @param v1 any variable of type PVector
         * @param v2 any variable of type PVector
         * @return the Euclidean distance between v1 and v2
         */
        @JvmStatic
        fun dist(v1: PVector, v2: PVector): Float {
            val dx = v1.x - v2.x
            val dy = v1.y - v2.y
            val dz = v1.z - v2.z

            return sqrt(dx * dx + dy * dy + (dz * dz).toDouble()).toFloat()
        }

        /**
         * @param v1 any variable of type PVector
         * @param v2 any variable of type PVector
         */
        @JvmStatic
        fun dot(v1: PVector, v2: PVector): Float {
            return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z
        }

        /**
         * @param v1 any variable of type PVector
         * @param v2 any variable of type PVector
         * @param target PVector to store the result
         */
        @JvmStatic
        fun cross(v1: PVector, v2: PVector, target: PVector?): PVector? {
            var target = target

            val crossX = v1.y * v2.z - v2.y * v1.z
            val crossY = v1.z * v2.x - v2.z * v1.x
            val crossZ = v1.x * v2.y - v2.x * v1.y

            if (target == null) {
                target = PVector(crossX, crossY, crossZ)
            }
            else {
                target.set(crossX, crossY, crossZ)
            }

            return target
        }

        /**
         * Linear interpolate between two vectors (returns a new PVector object)
         * @param v1 the vector to start from
         * @param v2 the vector to lerp to
         */
        @JvmStatic
        fun lerp(v1: PVector, v2: PVector, amt: Float): PVector {
            val v = v1.copy()
            v.lerp(v2, amt)

            return v
        }


        /**
         * ( begin auto-generated from PVector_angleBetween.xml )
         *
         * Calculates and returns the angle (in radians) between two vectors.
         *
         * ( end auto-generated )
         *
         * @webref pvector:method
         * @usage web_application
         * @param v1 the x, y, and z components of a PVector
         * @param v2 the x, y, and z components of a PVector
         * @brief Calculate and return the angle between two vectors
         */
        @JvmStatic
        fun angleBetween(v1: PVector, v2: PVector): Float {

            // We get NaN if we pass in a zero vector which can cause problems
            // Zero seems like a reasonable angle between a (0,0,0) vector and something else
            if (v1.x == 0f && v1.y == 0f && v1.z == 0f) return 0.0f
            if (v2.x == 0f && v2.y == 0f && v2.z == 0f) return 0.0f
            val dot = v1.x * v2.x + v1.y * v2.y + (v1.z * v2.z).toDouble()
            val v1mag = Math.sqrt(v1.x * v1.x + v1.y * v1.y + (v1.z * v1.z).toDouble())
            val v2mag = Math.sqrt(v2.x * v2.x + v2.y * v2.y + (v2.z * v2.z).toDouble())
            // This should be a number between -1 and 1, since it's "normalized"
            val amt = dot / (v1mag * v2mag)
            // But if it's not due to rounding error, then we need to fix it
            // http://code.google.com/p/processing/issues/detail?id=340
            // Otherwise if outside the range, acos() will return NaN
            // http://www.cppreference.com/wiki/c/math/acos
            if (amt <= -1) {
                return PConstants.PI
            } else if (amt >= 1) {
                // http://code.google.com/p/processing/issues/detail?id=435
                return 0F
            }
            return acos(amt).toFloat()
        }
    }
}