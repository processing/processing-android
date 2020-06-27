package processing.data

import java.io.File
import java.io.PrintWriter
import java.util.*

import processing.core.PApplet


// splice, slice, subset, concat, reverse
// trim, join for String versions
/**
 * Helper class for a list of ints. Lists are designed to have some of the
 * features of ArrayLists, but to maintain the simplicity and efficiency of
 * working with arrays.
 *
 * Functions like sort() and shuffle() always act on the list itself. To get
 * a sorted copy, use list.copy().sort().
 *
 * @webref data:composite
 * @see FloatList
 *
 * @see StringList
 */
open class LongList : Iterable<Long?> {
    protected var count = 0
    protected var data: LongArray

    constructor() {
        data = LongArray(10)
    }

    /**
     * @nowebref
     */
    constructor(length: Int) {
        data = LongArray(length)
    }

    /**
     * @nowebref
     */
    constructor(source: IntArray) {
        count = source.size
        data = LongArray(count)
        System.arraycopy(source, 0, data, 0, count)
    }

    /**
     * Construct an IntList from an iterable pile of objects.
     * For instance, a float array, an array of strings, who knows).
     * Un-parseable or null values will be set to 0.
     * @nowebref
     */
    constructor(iter: Iterable<Any?>) : this(10) {
        for (o in iter) {
            if (o == null) {
                append(0) // missing value default
            } else if (o is Number) {
                append(o.toInt().toLong())
            } else {
                append(PApplet.parseInt(o.toString().trim { it <= ' ' }).toLong())
            }
        }
        crop()
    }

    /**
     * Construct an IntList from a random pile of objects.
     * Un-parseable or null values will be set to zero.
     */
    constructor(vararg items: Any?) {
        val missingValue = 0 // nuts, can't be last/final/second arg
        count = items.size
        data = LongArray(count)
        var index = 0
        for (o in items) {
            var value = missingValue
            if (o != null) {
                if (o is Number) {
                    value = o.toInt()
                } else {
                    value = PApplet.parseInt(o.toString().trim { it <= ' ' }, missingValue)
                }
            }
            data[index++] = value.toLong()
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
     * @webref intlist:method
     * @brief Get the length of the list
     */
    fun size(): Int {
        return count
    }

    fun resize(length: Int) {
        if (length > data.size) {
            val temp = LongArray(length)
            System.arraycopy(data, 0, temp, 0, count)
            data = temp
        } else if (length > count) {
            Arrays.fill(data, count, length, 0)
        }
        count = length
    }

    /**
     * Remove all entries from the list.
     *
     * @webref intlist:method
     * @brief Remove all entries from the list
     */
    fun clear() {
        count = 0
    }

    /**
     * Get an entry at a particular index.
     *
     * @webref intlist:method
     * @brief Get an entry at a particular index
     */
    operator fun get(index: Int): Long {
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
     * @webref intlist:method
     * @brief Set the entry at a particular index
     */
    operator fun set(index: Int, what: Int) {
        if (index >= count) {
            data = PApplet.expand(data, index + 1)
            for (i in count until index) {
                data[i] = 0
            }
            count = index + 1
        }
        data[index] = what.toLong()
    }

    /** Just an alias for append(), but matches pop()  */
    fun push(value: Int) {
        append(value.toLong())
    }

    fun pop(): Long {
        if (count == 0) {
            throw RuntimeException("Can't call pop() on an empty list")
        }
        val value = get(count - 1)
        count--
        return value
    }

    /**
     * Remove an element from the specified index
     *
     * @webref intlist:method
     * @brief Remove an element from the specified index
     */
    fun remove(index: Int): Long {
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
        val index = index(value)
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
        for (i in 0 until count) {
            if (data[i] != value.toLong()) {
                data[ii++] = data[i]
            }
        }
        val removed = count - ii
        count = ii
        return removed
    }

    /**
     * Add a new entry to the list.
     *
     * @webref intlist:method
     * @brief Add a new entry to the list
     */
    fun append(value: Long) {
        if (count == data.size) {
            data = PApplet.expand(data)
        }
        data[count++] = value
    }

    fun append(values: IntArray) {
        for (v in values) {
            append(v.toLong())
        }
    }

    fun append(list: LongList) {
        for (v in list.values()) {  // will concat the list...
            append(v)
        }
    }

    /** Add this value, but only if it's not already in the list.  */
    fun appendUnique(value: Int) {
        if (!hasValue(value)) {
            append(value.toLong())
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

    fun insert(index: Int, value: Long) {
        insert(index, longArrayOf(value))
    }

    // same as splice
    fun insert(index: Int, values: LongArray) {
        require(index >= 0) { "insert() index cannot be negative: it was $index" }
        require(index < data.size) { "insert() index $index is past the end of this list" }
        val temp = LongArray(count + values.size)

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

    fun insert(index: Int, list: LongList) {
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
    fun index(what: Int): Int {
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
            if (data[i] == what.toLong()) {
                return i
            }
        }
        return -1
    }
    // !!! TODO this is not yet correct, because it's not being reset when
    // the rest of the entries are changed
    //  protected void cacheIndices() {
    //    indexCache = new HashMap<Integer, Integer>();
    //    for (int i = 0; i < count; i++) {
    //      indexCache.put(data[i], i);
    //    }
    //  }
    /**
     * @webref intlist:method
     * @brief Check if a number is a part of the list
     */
    fun hasValue(value: Int): Boolean {
//    if (indexCache == null) {
//      cacheIndices();
//    }
//    return index(what) != -1;
        for (i in 0 until count) {
            if (data[i] == value.toLong()) {
                return true
            }
        }
        return false
    }

    /**
     * @webref intlist:method
     * @brief Add one to a value
     */
    fun increment(index: Int) {
        if (count <= index) {
            resize(index + 1)
        }
        data[index]++
    }

    private fun boundsProblem(index: Int, method: String) {
        val msg = String.format("The list size is %d. " +
                "You cannot %s() to element %d.", count, method, index)
        throw ArrayIndexOutOfBoundsException(msg)
    }

    /**
     * @webref intlist:method
     * @brief Add to a value
     */
    fun add(index: Int, amount: Int) {
        if (index < count) {
            data[index] = data[index] + amount
        } else {
            boundsProblem(index, "add")
        }
    }

    /**
     * @webref intlist:method
     * @brief Subtract from a value
     */
    fun sub(index: Int, amount: Int) {
        if (index < count) {
            data[index] = data[index] - amount
        } else {
            boundsProblem(index, "sub")
        }
    }

    /**
     * @webref intlist:method
     * @brief Multiply a value
     */
    fun mult(index: Int, amount: Int) {
        if (index < count) {
            data[index] = data[index] * amount
        } else {
            boundsProblem(index, "mult")
        }
    }

    /**
     * @webref intlist:method
     * @brief Divide a value
     */
    fun div(index: Int, amount: Int) {
        if (index < count) {
            data[index] = data[index] / amount
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
     * @webref intlist:method
     * @brief Return the smallest value
     */
    fun min(): Long {
        checkMinMax("min")
        var outgoing = data[0]
        for (i in 1 until count) {
            if (data[i] < outgoing) outgoing = data[i]
        }
        return outgoing
    }

    // returns the index of the minimum value.
    // if there are ties, it returns the first one found.
    fun minIndex(): Int {
        checkMinMax("minIndex")
        var value = data[0]
        var index = 0
        for (i in 1 until count) {
            if (data[i] < value) {
                value = data[i]
                index = i
            }
        }
        return index
    }

    /**
     * @webref intlist:method
     * @brief Return the largest value
     */
    fun max(): Long {
        checkMinMax("max")
        var outgoing = data[0]
        for (i in 1 until count) {
            if (data[i] > outgoing) outgoing = data[i]
        }
        return outgoing
    }

    // returns the index of the maximum value.
    // if there are ties, it returns the first one found.
    fun maxIndex(): Int {
        checkMinMax("maxIndex")
        var value = data[0]
        var index = 0
        for (i in 1 until count) {
            if (data[i] > value) {
                value = data[i]
                index = i
            }
        }
        return index
    }

    fun sum(): Int {
        val amount = sumLong()
        if (amount > Int.MAX_VALUE) {
            throw RuntimeException("sum() exceeds " + Int.MAX_VALUE + ", use sumLong()")
        }
        if (amount < Int.MIN_VALUE) {
            throw RuntimeException("sum() less than " + Int.MIN_VALUE + ", use sumLong()")
        }
        return amount.toInt()
    }

    fun sumLong(): Long {
        var sum: Long = 0
        for (i in 0 until count) {
            sum += data[i]
        }
        return sum
    }

    /**
     * Sorts the array in place.
     *
     * @webref intlist:method
     * @brief Sorts the array, lowest to highest
     */
    fun sort() {
        Arrays.sort(data, 0, count)
    }

    /**
     * Reverse sort, orders values from highest to lowest.
     *
     * @webref intlist:method
     * @brief Reverse sort, orders values from highest to lowest
     */
    fun sortReverse() {
        object : Sort() {
            override fun size(): Int {
                return count
            }

            override fun compare(a: Int, b: Int): Int {
                val diff = data[b] - data[a]
                return if (diff == 0L) 0 else if (diff < 0) -1 else 1
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
    //
    //
    //  public void subset(int start, int num) {
    //    for (int i = 0; i < num; i++) {
    //      data[i] = data[i+start];
    //    }
    //    count = num;
    //  }
    /**
     * @webref intlist:method
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
     * @webref intlist:method
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

    fun copy(): LongList {
        val outgoing = LongList(*data as Array<out Any?>)
        outgoing.count = count
        return outgoing
    }

    /**
     * Returns the actual array being used to store the data. For advanced users,
     * this is the fastest way to access a large list. Suitable for iterating
     * with a for() loop, but modifying the list will have terrible consequences.
     */
    fun values(): LongArray {
        crop()
        return data
    }

    override fun iterator(): MutableIterator<Long?> {
//  public Iterator<Integer> valueIterator() {
        return object : MutableIterator<Long?> {
            var index = -1
            override fun remove() {
                this@LongList.remove(index)
                index--
            }

            override fun next(): Long {
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
     *
     * @return an array sized by the length of the list with each of the values.
     * @webref intlist:method
     * @brief Create a new array with a copy of all the values
     */
    @JvmOverloads
    fun array(array: IntArray? = null): IntArray {
        var array = array
        if (array == null || array.size != count) {
            array = IntArray(count)
        }
        System.arraycopy(data, 0, array, 0, count)
        return array
    }

    //  public int[] toIntArray() {
    //    int[] outgoing = new int[count];
    //    for (int i = 0; i < count; i++) {
    //      outgoing[i] = (int) data[i];
    //    }
    //    return outgoing;
    //  }
    //  public long[] toLongArray() {
    //    long[] outgoing = new long[count];
    //    for (int i = 0; i < count; i++) {
    //      outgoing[i] = (long) data[i];
    //    }
    //    return outgoing;
    //  }
    //  public float[] toFloatArray() {
    //    float[] outgoing = new float[count];
    //    System.arraycopy(data, 0, outgoing, 0, count);
    //    return outgoing;
    //  }
    //  public double[] toDoubleArray() {
    //    double[] outgoing = new double[count];
    //    for (int i = 0; i < count; i++) {
    //      outgoing[i] = data[i];
    //    }
    //    return outgoing;
    //  }
    //  public String[] toStringArray() {
    //    String[] outgoing = new String[count];
    //    for (int i = 0; i < count; i++) {
    //      outgoing[i] = String.valueOf(data[i]);
    //    }
    //    return outgoing;
    //  }

    /**
     * Returns a normalized version of this array. Called getPercent() for
     * consistency with the Dict classes. It's a getter method because it needs
     * to returns a new list (because IntList/Dict can't do percentages or
     * normalization in place on int values).
     */
    val percent: FloatList
        get() {
            var sum = 0.0
            for (value in array()) {
                sum += value.toDouble()
            }
            val outgoing = FloatList(count)
            for (i in 0 until count) {
                val percent = data[i] / sum
                outgoing[i] = percent.toFloat()
            }
            return outgoing
        }

    //  /**
    //   * Count the number of times each entry is found in this list.
    //   * Converts each entry to a String so it can be used as a key.
    //   */
    //  public IntDict getTally() {
    //    IntDict outgoing = new IntDict();
    //    for (int i = 0; i < count; i++) {
    //      outgoing.increment(String.valueOf(data[i]));
    //    }
    //    return outgoing;
    //  }
    fun getSubset(start: Int): LongList {
        return getSubset(start, count - start)
    }

    fun getSubset(start: Int, num: Int): LongList {
        val subset = IntArray(num)
        System.arraycopy(data, start, subset, 0, num)
        return LongList(subset)
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
            System.out.format("[%d] %d%n", i, data[i])
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

    companion object {
        fun fromRange(stop: Int): LongList {
            return fromRange(0, stop)
        }

        fun fromRange(start: Int, stop: Int): LongList {
            val count = stop - start
            val newbie = LongList(count)
            for (i in 0 until count) {
                newbie[i] = start + i
            }
            return newbie
        }
    }
}