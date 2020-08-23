package processing.data

import java.io.File
import java.io.PrintWriter
import java.util.*

import processing.core.PApplet

/**
 * @author Aditya Rana
 * Helper class for a list of floats. Lists are designed to have some of the
 * features of ArrayLists, but to maintain the simplicity and efficiency of
 * working with arrays.
 *
 * Functions like sort() and shuffle() always act on the list itself. To get
 * a sorted copy, use list.copy().sort().
 *
 * @webref data:composite
 * @see IntList
 *
 * @see StringList
 */
open class DoubleList : Iterable<Double?> {
    var count = 0
    var data: DoubleArray

    constructor() {
        data = DoubleArray(10)
    }

    /**
     * @nowebref
     */
    constructor(length: Int) {
        data = DoubleArray(length)
    }

    /**
     * @nowebref
     */
    constructor(list: DoubleArray) {
        count = list.size
        data = DoubleArray(count)
        System.arraycopy(list, 0, data, 0, count)
    }

    /**
     * Construct an FloatList from an iterable pile of objects.
     * For instance, a double array, an array of strings, who knows).
     * Un-parseable or null values will be set to NaN.
     * @nowebref
     */
    constructor(iter: Iterable<Any?>) : this(10) {
        for (o in iter) {
            if (o == null) {
                append(Double.NaN)
            } else if (o is Number) {
                append(o.toDouble())
            } else {
                append(PApplet.parseFloat(o.toString().trim { it <= ' ' }).toDouble())
            }
        }
        crop()
    }

    /**
     * Construct an FloatList from a random pile of objects.
     * Un-parseable or null values will be set to NaN.
     */
    constructor(vararg items: Any?) {
        // nuts, no good way to pass missingValue to this fn (varargs must be last)
        val missingValue = Double.NaN
        count = items.size
        data = DoubleArray(count)
        var index = 0
        for (o in items) {
            var value = missingValue
            if (o != null) {
                if (o is Number) {
                    value = o.toDouble()
                } else {
                    value = try {
                        o.toString().trim { it <= ' ' }.toDouble()
                    } catch (nfe: NumberFormatException) {
                        missingValue
                    }
                }
            }
            data[index++] = value
        }
    }

    /**
     * Improve efficiency by removing allocated but unused entries from the
     * internal array used to store the data. Set to private, though it could
     * be useful to have this public if lists are frequently making drastic
     * size changes (from very large to very small).
     */
    private fun crop() {
        if (count != data.size) {
            data = PApplet.subset(data, 0, count)
        }
    }

    /**
     * Get the length of the list.
     *
     * @webref doublelist:method
     * @brief Get the length of the list
     */
    fun size(): Int {
        return count
    }

    fun resize(length: Int) {
        if (length > data.size) {
            val temp = DoubleArray(length)
            System.arraycopy(data, 0, temp, 0, count)
            data = temp
        } else if (length > count) {
            Arrays.fill(data, count, length, 0.0)
        }
        count = length
    }

    /**
     * Remove all entries from the list.
     *
     * @webref doublelist:method
     * @brief Remove all entries from the list
     */
    fun clear() {
        count = 0
    }

    /**
     * Get an entry at a particular index.
     *
     * @webref doublelist:method
     * @brief Get an entry at a particular index
     */
    operator fun get(index: Int): Double {
        if (index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        return data[index]
    }

    /**
     * Set the entry at a particular index. If the index is past the length of
     * the list, it'll expand the list to accommodate, and fill the intermediate
     * entries with 0s.
     *
     * @webref doublelist:method
     * @brief Set the entry at a particular index
     */
    operator fun set(index: Int, what: Double) {
        if (index >= count) {
            data = PApplet.expand(data, index + 1)
            for (i in count until index) {
                data[i] = 0.0
            }
            count = index + 1
        }
        data[index] = what
    }

    /** Just an alias for append(), but matches pop()  */
    fun push(value: Double) {
        append(value)
    }

    fun pop(): Double {
        if (count == 0) {
            throw RuntimeException("Can't call pop() on an empty list")
        }
        val value = get(count - 1)
        count--
        return value
    }

    /**
     * Remove an element from the specified index.
     *
     * @webref doublelist:method
     * @brief Remove an element from the specified index
     */
    fun remove(index: Int): Double {
        if (index < 0 || index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        val entry = data[index]
        //    int[] outgoing = new int[count - 1];
//    System.arraycopy(data, 0, outgoing, 0, index);
//    count--;
//    System.arraycopy(data, index + 1, outgoing, 0, count - index);
//    data = outgoing;
        // For most cases, this actually appears to be faster
        // than arraycopy() on an array copying into itself.
        for (i in index until count - 1) {
            data[i] = data[i + 1]
        }
        count--
        return entry
    }

    // Remove the first instance of a particular value,
    // and return the index at which it was found.
    fun removeValue(value: Int): Int {
        val index = index(value.toDouble())
        if (index != -1) {
            remove(index)
            return index
        }
        return -1
    }

    // Remove all instances of a particular value,
    // and return the number of values found and removed
    fun removeValues(value: Int): Int {
        var ii = 0
        if (java.lang.Double.isNaN(value.toDouble())) {
            for (i in 0 until count) {
                if (!java.lang.Double.isNaN(data[i])) {
                    data[ii++] = data[i]
                }
            }
        } else {
            for (i in 0 until count) {
                if (data[i] != value.toDouble()) {
                    data[ii++] = data[i]
                }
            }
        }
        val removed = count - ii
        count = ii
        return removed
    }

    /** Replace the first instance of a particular value  */
    fun replaceValue(value: Double, newValue: Double): Boolean {
        if (java.lang.Double.isNaN(value)) {
            for (i in 0 until count) {
                if (java.lang.Double.isNaN(data[i])) {
                    data[i] = newValue
                    return true
                }
            }
        } else {
            val index = index(value)
            if (index != -1) {
                data[index] = newValue
                return true
            }
        }
        return false
    }

    /** Replace all instances of a particular value  */
    fun replaceValues(value: Double, newValue: Double): Boolean {
        var changed = false
        if (java.lang.Double.isNaN(value)) {
            for (i in 0 until count) {
                if (java.lang.Double.isNaN(data[i])) {
                    data[i] = newValue
                    changed = true
                }
            }
        } else {
            for (i in 0 until count) {
                if (data[i] == value) {
                    data[i] = newValue
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * Add a new entry to the list.
     *
     * @webref doublelist:method
     * @brief Add a new entry to the list
     */
    fun append(value: Double) {
        if (count == data.size) {
            data = PApplet.expand(data)
        }
        data[count++] = value
    }

    fun append(values: DoubleArray) {
        for (v in values) {
            append(v)
        }
    }

    fun append(list: DoubleList) {
        for (v in list.values()) {  // will concat the list...
            append(v)
        }
    }

    /** Add this value, but only if it's not already in the list.  */
    fun appendUnique(value: Double) {
        if (!hasValue(value)) {
            append(value)
        }
    }

    //  public void insert(int index, int value) {
    //    if (index+1 > count) {
    //      if (index+1 < data.length) {
    //    }
    //  }
    //    if (index >= data.length) {
    //      data = PApplet.expand(data, index+1);
    //      data[index] = value;
    //      count = index+1;
    //
    //    } else if (count == data.length) {
    //    if (index >= count) {
    //      //int[] temp = new int[count << 1];
    //      System.arraycopy(data, 0, temp, 0, index);
    //      temp[index] = value;
    //      System.arraycopy(data, index, temp, index+1, count - index);
    //      data = temp;
    //
    //    } else {
    //      // data[] has room to grow
    //      // for() loop believed to be faster than System.arraycopy over itself
    //      for (int i = count; i > index; --i) {
    //        data[i] = data[i-1];
    //      }
    //      data[index] = value;
    //      count++;
    //    }
    //  }

    fun insert(index: Int, value: Double) {
        insert(index, doubleArrayOf(value))
    }

    // same as splice
    fun insert(index: Int, values: DoubleArray) {
        require(index >= 0) { "insert() index cannot be negative: it was $index" }
        require(index < data.size) { "insert() index $index is past the end of this list" }
        val temp = DoubleArray(count + values.size)

        // Copy the old values, but not more than already exist
        System.arraycopy(data, 0, temp, 0, Math.min(count, index))

        // Copy the new values into the proper place
        System.arraycopy(values, 0, temp, index, values.size)

//    if (index < count) {
        // The index was inside count, so it's a true splice/insert
        System.arraycopy(data, index, temp, index + values.size, count - index)
        count += values.size
        //    } else {
//      // The index was past 'count', so the new count is weirder
//      count = index + values.length;
//    }
        data = temp
    }

    fun insert(index: Int, list: DoubleList) {
        insert(index, list.values())
    }
    // below are aborted attempts at more optimized versions of the code
    // that are harder to read and debug...
    //    if (index + values.length >= count) {
    //      // We're past the current 'count', check to see if we're still allocated
    //      // index 9, data.length = 10, values.length = 1
    //      if (index + values.length < data.length) {
    //        // There's still room for these entries, even though it's past 'count'.
    //        // First clear out the entries leading up to it, however.
    //        for (int i = count; i < index; i++) {
    //          data[i] = 0;
    //        }
    //        data[index] =
    //      }
    //      if (index >= data.length) {
    //        int length = index + values.length;
    //        int[] temp = new int[length];
    //        System.arraycopy(data, 0, temp, 0, count);
    //        System.arraycopy(values, 0, temp, index, values.length);
    //        data = temp;
    //        count = data.length;
    //      } else {
    //
    //      }
    //
    //    } else if (count == data.length) {
    //      int[] temp = new int[count << 1];
    //      System.arraycopy(data, 0, temp, 0, index);
    //      temp[index] = value;
    //      System.arraycopy(data, index, temp, index+1, count - index);
    //      data = temp;
    //
    //    } else {
    //      // data[] has room to grow
    //      // for() loop believed to be faster than System.arraycopy over itself
    //      for (int i = count; i > index; --i) {
    //        data[i] = data[i-1];
    //      }
    //      data[index] = value;
    //      count++;
    //    }

    /** Return the first index of a particular value.  */
    fun index(what: Double): Int {
        /*
    if (indexCache != null) {
      try {
        return indexCache.get(what);
      } catch (Exception e) {  // not there
        return -1;
      }
    }
    */
        for (i in 0 until count) {
            if (data[i] == what) {
                return i
            }
        }
        return -1
    }

    /**
     * @webref doublelist:method
     * @brief Check if a number is a part of the list
     */
    fun hasValue(value: Double): Boolean {
        if (java.lang.Double.isNaN(value)) {
            for (i in 0 until count) {
                if (java.lang.Double.isNaN(data[i])) {
                    return true
                }
            }
        } else {
            for (i in 0 until count) {
                if (data[i] == value) {
                    return true
                }
            }
        }
        return false
    }

    private fun boundsProblem(index: Int, method: String) {
        val msg = String.format("The list size is %d. " +
                "You cannot %s() to element %d.", count, method, index)
        throw ArrayIndexOutOfBoundsException(msg)
    }

    /**
     * @webref doublelist:method
     * @brief Add to a value
     */
    fun add(index: Int, amount: Double) {
        if (index < count) {
            data[index] += amount
        } else {
            boundsProblem(index, "add")
        }
    }

    /**
     * @webref doublelist:method
     * @brief Subtract from a value
     */
    fun sub(index: Int, amount: Double) {
        if (index < count) {
            data[index] -= amount
        } else {
            boundsProblem(index, "sub")
        }
    }

    /**
     * @webref doublelist:method
     * @brief Multiply a value
     */
    fun mult(index: Int, amount: Double) {
        if (index < count) {
            data[index] *= amount
        } else {
            boundsProblem(index, "mult")
        }
    }

    /**
     * @webref doublelist:method
     * @brief Divide a value
     */
    fun div(index: Int, amount: Double) {
        if (index < count) {
            data[index] /= amount
        } else {
            boundsProblem(index, "div")
        }
    }

    private fun checkMinMax(functionName: String) {
        if (count == 0) {
            val msg = String.format("Cannot use %s() on an empty %s.",
                    functionName, javaClass.simpleName)
            throw RuntimeException(msg)
        }
    }

    /**
     * @webref doublelist:method
     * @brief Return the smallest value
     */
    fun min(): Double {
        checkMinMax("min")
        val index = minIndex()
        return if (index == -1) Double.NaN else data[index]
    }

    fun minIndex(): Int {
        checkMinMax("minIndex")
        var m = Double.NaN
        var mi = -1
        for (i in 0 until count) {
            // find one good value to start
            if (data[i] == data[i]) {
                m = data[i]
                mi = i

                // calculate the rest
                for (j in i + 1 until count) {
                    val d = data[j]
                    if (!java.lang.Double.isNaN(d) && d < m) {
                        m = data[j]
                        mi = j
                    }
                }
                break
            }
        }
        return mi
    }

    /**
     * @webref doublelist:method
     * @brief Return the largest value
     */
    fun max(): Double {
        checkMinMax("max")
        val index = maxIndex()
        return if (index == -1) Double.NaN else data[index]
    }

    fun maxIndex(): Int {
        checkMinMax("maxIndex")
        var m = Double.NaN
        var mi = -1
        for (i in 0 until count) {
            // find one good value to start
            if (data[i] == data[i]) {
                m = data[i]
                mi = i

                // calculate the rest
                for (j in i + 1 until count) {
                    val d = data[j]
                    if (!java.lang.Double.isNaN(d) && d > m) {
                        m = data[j]
                        mi = j
                    }
                }
                break
            }
        }
        return mi
    }

    fun sum(): Double {
        var sum = 0.0
        for (i in 0 until count) {
            sum += data[i]
        }
        return sum
    }

    /**
     * Sorts the array in place.
     *
     * @webref doublelist:method
     * @brief Sorts an array, lowest to highest
     */
    fun sort() {
        Arrays.sort(data, 0, count)
    }

    /**
     * Reverse sort, orders values from highest to lowest
     *
     * @webref doublelist:method
     * @brief Reverse sort, orders values from highest to lowest
     */
    fun sortReverse() {
        object : Sort() {
            override fun size(): Int {
                // if empty, don't even mess with the NaN check, it'll AIOOBE
                if (count == 0) {
                    return 0
                }
                // move NaN values to the end of the list and don't sort them
                var right = count - 1
                while (data[right] != data[right]) {
                    right--
                    if (right == -1) {  // all values are NaN
                        return 0
                    }
                }
                for (i in right downTo 0) {
                    val v = data[i]
                    if (v != v) {
                        data[i] = data[right]
                        data[right] = v
                        --right
                    }
                }
                return right + 1
            }

            override fun compare(a: Int, b: Int): Int {
                val diff = data[b] - data[a]
                return if (diff == 0.0) 0 else if (diff < 0) -1 else 1
            }

            override fun swap(a: Int, b: Int) {
                val temp = data[a]
                data[a] = data[b]
                data[b] = temp
            }
        }.run()
    }

    // use insert()
    //  public void splice(int index, int value) {
    //  }
    //  public void subset(int start) {
    //    subset(start, count - start);
    //  }
    //  public void subset(int start, int num) {
    //    for (int i = 0; i < num; i++) {
    //      data[i] = data[i+start];
    //    }
    //    count = num;
    //  }

    /**
     * @webref doublelist:method
     * @brief Reverse the order of the list elements
     */
    fun reverse() {
        var ii = count - 1
        for (i in 0 until count / 2) {
            val t = data[i]
            data[i] = data[ii]
            data[ii] = t
            --ii
        }
    }

    /**
     * Randomize the order of the list elements. Note that this does not
     * obey the randomSeed() function in PApplet.
     *
     * @webref doublelist:method
     * @brief Randomize the order of the list elements
     */
    fun shuffle() {
        val r = Random()
        var num = count
        while (num > 1) {
            val value = r.nextInt(num)
            num--
            val temp = data[num]
            data[num] = data[value]
            data[value] = temp
        }
    }

    /**
     * Randomize the list order using the random() function from the specified
     * sketch, allowing shuffle() to use its current randomSeed() setting.
     */
    fun shuffle(sketch: PApplet) {
        var num = count
        while (num > 1) {
            val value = sketch.random(num.toFloat()).toInt()
            num--
            val temp = data[num]
            data[num] = data[value]
            data[value] = temp
        }
    }

    fun copy(): DoubleList {
        val outgoing = DoubleList(data)
        outgoing.count = count
        return outgoing
    }

    /**
     * Returns the actual array being used to store the data. For advanced users,
     * this is the fastest way to access a large list. Suitable for iterating
     * with a for() loop, but modifying the list will have terrible consequences.
     */
    fun values(): DoubleArray {
        crop()
        return data
    }

    /** Implemented this way so that we can use a FloatList in a for loop.  */
    override fun iterator(): MutableIterator<Double?> {
//  }
//
//
//  public Iterator<Float> valueIterator() {
        return object : MutableIterator<Double?> {
            var index = -1
            override fun remove() {
                this@DoubleList.remove(index)
                index--
            }

            override fun next(): Double {
                return data[++index]
            }

            override fun hasNext(): Boolean {
                return index + 1 < count
            }
        }
    }

    /**
     * Copy values into the specified array. If the specified array is null or
     * not the same size, a new array will be allocated.
     * @param array
     */
    /**
     * Create a new array with a copy of all the values.
     * @return an array sized by the length of the list with each of the values.
     * @webref doublelist:method
     * @brief Create a new array with a copy of all the values
     */
    @JvmOverloads
    fun array(array: DoubleArray? = null): DoubleArray {
        var array = array
        if (array == null || array.size != count) {
            array = DoubleArray(count)
        }
        System.arraycopy(data, 0, array, 0, count)
        return array
    }

    /**
     * Returns a normalized version of this array. Called getPercent() for
     * consistency with the Dict classes. It's a getter method because it needs
     * to returns a new list (because IntList/Dict can't do percentages or
     * normalization in place on int values).
     */
    val percent: DoubleList
        get() {
            var sum = 0.0
            for (value in array()) {
                sum += value
            }
            val outgoing = DoubleList(count)
            for (i in 0 until count) {
                val percent = data[i] / sum
                outgoing[i] = percent
            }
            return outgoing
        }

    fun getSubset(start: Int): DoubleList {
        return getSubset(start, count - start)
    }

    fun getSubset(start: Int, num: Int): DoubleList {
        val subset = DoubleArray(num)
        System.arraycopy(data, start, subset, 0, num)
        return DoubleList(subset)
    }

    fun join(separator: String?): String {
        if (count == 0) {
            return ""
        }
        val sb = StringBuilder()
        sb.append(data[0])
        for (i in 1 until count) {
            sb.append(separator)
            sb.append(data[i])
        }
        return sb.toString()
    }

    fun print() {
        for (i in 0 until count) {
            System.out.format("[%d] %f%n", i, data[i])
        }
    }

    /**
     * Save tab-delimited entries to a file (TSV format, UTF-8 encoding)
     */
    fun save(file: File?) {
        val writer = PApplet.createWriter(file)
        write(writer)
        writer.close()
    }

    /**
     * Write entries to a PrintWriter, one per line
     */
    fun write(writer: PrintWriter) {
        for (i in 0 until count) {
            writer.println(data[i])
        }
        writer.flush()
    }

    /**
     * Return this dictionary as a String in JSON format.
     */
    fun toJSON(): String {
        return "[ " + join(", ") + " ]"
    }

    override fun toString(): String {
        return javaClass.simpleName + " size=" + size() + " " + toJSON()
    }
}