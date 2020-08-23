package processing.data

import java.io.File
import java.io.PrintWriter
import java.util.*

import processing.core.PApplet


/**
 * @author Aditya Rana
 * Helper class for a list of Strings. Lists are designed to have some of the
 * features of ArrayLists, but to maintain the simplicity and efficiency of
 * working with arrays.
 *
 * Functions like sort() and shuffle() always act on the list itself. To get
 * a sorted copy, use list.copy().sort().
 *
 * @webref data:composite
 * @see IntList
 *
 * @see FloatList
 */
open class StringList : Iterable<String?> {
    var count = 0
    var data: Array<String?>

    /**
     * @nowebref
     */
    @JvmOverloads
    constructor(length: Int = 10) {
        data = arrayOfNulls(length)
    }

    /**
     * @nowebref
     */
    constructor(list: Array<String>) {
        count = list.size
        data = arrayOfNulls(count)
        System.arraycopy(list, 0, data, 0, count)
    }

    /**
     * Construct a StringList from a random pile of objects. Null values will
     * stay null, but all the others will be converted to String values.
     */
    constructor(vararg items: Any?) {
        count = items.size
        data = arrayOfNulls(count)
        var index = 0

        for (o in items) {
//      // Not gonna go with null values staying that way because perhaps
//      // the most common case here is to immediately call join() or similar.
//      data[index++] = String.valueOf(o);
            // Keep null values null (because join() will make non-null anyway)
            if (o != null) {  // leave null values null
                data[index] = o.toString()
            }
            index++
        }
    }

    /**
     * Create from something iterable, for instance:
     * StringList list = new StringList(hashMap.keySet());
     *
     * @nowebref
     */
    constructor(iter: Iterable<String>) : this(10) {
        for (s in iter) {
            append(s)
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
     * @webref stringlist:method
     * @brief Get the length of the list
     */
    fun size(): Int {
        return count
    }

    fun resize(length: Int) {
        if (length > data.size) {
            val temp = arrayOfNulls<String>(length)
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
     * @webref stringlist:method
     * @brief Remove all entries from the list
     */
    fun clear() {
        count = 0
    }

    /**
     * Get an entry at a particular index.
     *
     * @webref stringlist:method
     * @brief Get an entry at a particular index
     */
    operator fun get(index: Int): String {
        if (index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        return data[index]!!
    }

    /**
     * Set the entry at a particular index. If the index is past the length of
     * the list, it'll expand the list to accommodate, and fill the intermediate
     * entries with 0s.
     *
     * @webref stringlist:method
     * @brief Set an entry at a particular index
     */
    operator fun set(index: Int, what: String) {
        if (index >= count) {
            data = PApplet.expand(data, index + 1)
            for (i in count until index) {
                data[i] = null
            }
            count = index + 1
        }
        data[index] = what
    }

    /** Just an alias for append(), but matches pop()  */
    fun push(value: String) {
        append(value)
    }

    fun pop(): String {
        if (count == 0) {
            throw RuntimeException("Can't call pop() on an empty list")
        }
        val value = get(count - 1)
        data[--count] = null // avoid leak
        return value
    }

    /**
     * Remove an element from the specified index.
     *
     * @webref stringlist:method
     * @brief Remove an element from the specified index
     */
    fun remove(index: Int): String {
        if (index < 0 || index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        val entry = data[index]
        //    int[] outgoing = new int[count - 1];
//    System.arraycopy(data, 0, outgoing, 0, index);
//    count--;
//    System.arraycopy(data, index + 1, outgoing, 0, count - index);
//    data = outgoing;
        for (i in index until count - 1) {
            data[i] = data[i + 1]
        }
        count--

        return entry!!
    }

    // Remove the first instance of a particular value and return its index.
    fun removeValue(value: String?): Int {
        if (value == null) {
            for (i in 0 until count) {
                if (data[i] == null) {
                    remove(i)
                    return i
                }
            }
        } else {
            val index = index(value)
            if (index != -1) {
                remove(index)
                return index
            }
        }
        return -1
    }

    // Remove all instances of a particular value and return the count removed.
    fun removeValues(value: String?): Int {
        var ii = 0
        if (value == null) {
            for (i in 0 until count) {
                if (data[i] != null) {
                    data[ii++] = data[i]
                }
            }
        } else {
            for (i in 0 until count) {
                if (value != data[i]) {
                    data[ii++] = data[i]
                }
            }
        }
        val removed = count - ii
        count = ii
        return removed
    }

    // replace the first value that matches, return the index that was replaced
    fun replaceValue(value: String?, newValue: String): Int {
        if (value == null) {
            for (i in 0 until count) {
                if (data[i] == null) {
                    data[i] = newValue
                    return i
                }
            }
        } else {
            for (i in 0 until count) {
                if (value == data[i]) {
                    data[i] = newValue
                    return i
                }
            }
        }
        return -1
    }

    // replace all values that match, return the count of those replaced
    fun replaceValues(value: String?, newValue: String): Int {
        var changed = 0
        if (value == null) {
            for (i in 0 until count) {
                if (data[i] == null) {
                    data[i] = newValue
                    changed++
                }
            }
        } else {
            for (i in 0 until count) {
                if (value == data[i]) {
                    data[i] = newValue
                    changed++
                }
            }
        }
        return changed
    }

    /**
     * Add a new entry to the list.
     *
     * @webref stringlist:method
     * @brief Add a new entry to the list
     */
    fun append(value: String?) {
        if (count == data.size) {
            data = PApplet.expand(data)
        }
        data[count++] = value
    }

    fun append(values: Array<String>) {
        for (v in values) {
            append(v)
        }
    }

    fun append(list: StringList) {
        for (v in list.values()) {  // will concat the list...
            append(v)
        }
    }

    /** Add this value, but only if it's not already in the list.  */
    fun appendUnique(value: String) {
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

    fun insert(index: Int, value: String) {
        insert(index, arrayOf(value))
    }

    // same as splice
    fun insert(index: Int, values: Array<String>) {
        require(index >= 0) { "insert() index cannot be negative: it was $index" }
        require(index < data.size) { "insert() index $index is past the end of this list" }
        val temp = arrayOfNulls<String>(count + values.size)

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

    fun insert(index: Int, list: StringList) {
        insert(index, list)
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
    fun index(what: String?): Int {
        if (what == null) {
            for (i in 0 until count) {
                if (data[i] == null) {
                    return i
                }
            }
        } else {
            for (i in 0 until count) {
                if (what == data[i]) {
                    return i
                }
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
     * @webref stringlist:method
     * @brief Check if a value is a part of the list
     */
    fun hasValue(value: String?): Boolean {
        if (value == null) {
            for (i in 0 until count) {
                if (data[i] == null) {
                    return true
                }
            }
        } else {
            for (i in 0 until count) {
                if (value == data[i]) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Sorts the array in place.
     *
     * @webref stringlist:method
     * @brief Sorts the array in place
     */
    fun sort() {
        sortImpl(false)
    }

    /**
     * Reverse sort, orders values from highest to lowest.
     *
     * @webref stringlist:method
     * @brief Reverse sort, orders values from highest to lowest
     */
    fun sortReverse() {
        sortImpl(true)
    }

    private fun sortImpl(reverse: Boolean) {
        object : Sort() {
            override fun size(): Int {
                return count
            }

            override fun compare(a: Int, b: Int): Int {
                val diff = data[a]!!.compareTo(data[b]!!, ignoreCase = true)
                return if (reverse) -diff else diff
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
     * @webref stringlist:method
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
     * @webref stringlist:method
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

    /**
     * Make the entire list lower case.
     *
     * @webref stringlist:method
     * @brief Make the entire list lower case
     */
    fun lower() {
        for (i in 0 until count) {
            if (data[i] != null) {
                data[i] = data[i]!!.toLowerCase()
            }
        }
    }

    /**
     * Make the entire list upper case.
     *
     * @webref stringlist:method
     * @brief Make the entire list upper case
     */
    fun upper() {
        for (i in 0 until count) {
            if (data[i] != null) {
                data[i] = data[i]!!.toUpperCase()
            }
        }
    }

    fun copy(): StringList {
        val outgoing = StringList(data)
        outgoing.count = count
        return outgoing
    }

    /**
     * Returns the actual array being used to store the data. Suitable for
     * iterating with a for() loop, but modifying the list could cause terrible
     * things to happen.
     */
    fun values(): Array<String?> {
        crop()
        return data
    }

    override fun iterator(): MutableIterator<String?> {
//    return valueIterator();
//  }
//
//
//  public Iterator<String> valueIterator() {
        return object : MutableIterator<String?> {
            var index = -1
            override fun remove() {
                this@StringList.remove(index)
                index--
            }

            override fun next(): String {
                return data[++index]!!
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
     * @webref stringlist:method
     * @brief Create a new array with a copy of all the values
     */
    @JvmOverloads
    fun array(array: Array<String?>? = null): Array<String?> {
        var array = array
        if (array == null || array.size != count) {
            array = arrayOfNulls(count)
        }
        System.arraycopy(data, 0, array, 0, count)
        return array
    }

    fun getSubset(start: Int): StringList {
        return getSubset(start, count - start)
    }

    fun getSubset(start: Int, num: Int): StringList {
        val subset = arrayOfNulls<String>(num)
        System.arraycopy(data, start, subset, 0, num)
        return StringList(subset)
    }

    /** Get a list of all unique entries.  */
    val unique: Array<String?>
        get() = tally.keyArray()

    /** Count the number of times each String entry is found in this list.  */
    val tally: IntDict
        get() {
            val outgoing = IntDict()
            for (i in 0 until count) {
                outgoing.increment(data[i]!!)
            }
            return outgoing
        }

    /** Create a dictionary associating each entry in this list to its index.  */
    val order: IntDict
        get() {
            val outgoing = IntDict()
            for (i in 0 until count) {
                outgoing[data[i]!!] = i
            }
            return outgoing
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
            System.out.format("[%d] %s%n", i, data[i])
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
        val temp = StringList()
        for (item in this) {
            temp.append(JSONObject.quote(item))
        }
        return "[ " + temp.join(", ") + " ]"
    }

    override fun toString(): String {
        return javaClass.simpleName + " size=" + size() + " " + toJSON()
    }
}