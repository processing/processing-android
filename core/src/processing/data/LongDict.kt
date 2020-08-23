package processing.data

import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.util.*

import processing.core.PApplet


/**
 * @author Aditya Rana
 * A simple class to use a String as a lookup for an int value.
 *
 * @webref data:composite
 * @see FloatDict
 *
 * @see StringDict
 */
open class LongDict {
    /** Number of elements in the table  */
    protected var count = 0
    protected var keys: Array<String?>
    protected var values: LongArray

    /** Internal implementation for faster lookups  */
    private var indices = HashMap<String, Int>()

    constructor() {
        count = 0
        keys = arrayOfNulls(10)
        values = LongArray(10)
    }

    /**
     * Create a new lookup with a specific size. This is more efficient than not
     * specifying a size. Use it when you know the rough size of the thing you're creating.
     *
     * @nowebref
     */
    constructor(length: Int) {
        count = 0
        keys = arrayOfNulls(length)
        values = LongArray(length)
    }

    /**
     * Read a set of entries from a Reader that has each key/value pair on
     * a single line, separated by a tab.
     *
     * @nowebref
     */
    constructor(reader: BufferedReader?) {
        val lines = PApplet.loadStrings(reader!!)
        keys = arrayOfNulls(lines!!.size)
        values = LongArray(lines!!.size)
        for (i in lines!!.indices) {
            val pieces = PApplet.split(lines!![i], '\t')
            if (pieces!!.size == 2) {
                keys[count] = pieces!![0]
                values[count] = PApplet.parseInt(pieces!![1]!!).toLong()
                indices[pieces!![0]!!] = count
                count++
            }
        }
    }

    /**
     * @nowebref
     */
    constructor(keys: Array<String?>, values: LongArray) {
        require(keys.size == values.size) { "key and value arrays must be the same length" }
        this.keys = keys
        this.values = values
        count = keys.size
        for (i in 0 until count) {
            indices[keys[i]!!] = i
        }
    }

    /**
     * Constructor to allow (more intuitive) inline initialization, e.g.:
     * <pre>
     * new FloatDict(new Object[][] {
     * { "key1", 1 },
     * { "key2", 2 }
     * });
    </pre> *
     */
    constructor(pairs: Array<Array<Any>>) {
        count = pairs.size
        keys = arrayOfNulls(count)
        values = LongArray(count)
        for (i in 0 until count) {
            keys[i] = pairs[i][0] as String
            values[i] = (pairs[i][1] as Int).toLong()
            indices[keys[i]!!] = i
        }
    }

    /**
     * Returns the number of key/value pairs
     *
     * @webref intdict:method
     * @brief Returns the number of key/value pairs
     */
    fun size(): Int {
        return count
    }

    /**
     * Resize the internal data, this can only be used to shrink the list.
     * Helpful for situations like sorting and then grabbing the top 50 entries.
     */
    fun resize(length: Int) {
        require(length <= count) { "resize() can only be used to shrink the dictionary" }
        require(length >= 1) { "resize($length) is too small, use 1 or higher" }
        val newKeys = arrayOfNulls<String>(length)
        val newValues = LongArray(length)
        PApplet.arrayCopy(keys, newKeys, length)
        PApplet.arrayCopy(values, newValues, length)
        keys = newKeys
        values = newValues
        count = length
        resetIndices()
    }

    /**
     * Remove all entries.
     *
     * @webref intdict:method
     * @brief Remove all entries
     */
    fun clear() {
        count = 0
        indices = HashMap()
    }

    private fun resetIndices() {
        indices = HashMap(count)
        for (i in 0 until count) {
            indices[keys[i]!!] = i
        }
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    inner class Entry internal constructor(var key: String?, var value: Long)

    fun entries(): Iterable<Entry?> {
        return object : Iterable<Entry?> {
            override fun iterator(): Iterator<Entry?> {
                return entryIterator()
            }
        }
    }

    fun entryIterator(): MutableIterator<Entry?> {
        return object : MutableIterator<Entry?> {
            var index = -1
            override fun remove() {
                removeIndex(index)
                index--
            }

            override fun next(): Entry {
                ++index
                return Entry(keys[index], values[index])
            }

            override fun hasNext(): Boolean {
                return index + 1 < size()
            }
        }
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun key(index: Int): String {
        return keys[index]!!
    }

    protected fun crop() {
        if (count != keys.size) {
            keys = PApplet.subset(keys, 0, count)
            values = PApplet.subset(values, 0, count)
        }
    }

    fun keys(): Iterable<String?> {
        return object : Iterable<String?> {
            override fun iterator(): Iterator<String?> {
                return keyIterator()
            }
        }
    }

    // Use this to iterate when you want to be able to remove elements along the way
    fun keyIterator(): MutableIterator<String?> {
        return object : MutableIterator<String?> {
            var index = -1
            override fun remove() {
                removeIndex(index)
                index--
            }

            override fun next(): String {
                return key(++index)
            }

            override fun hasNext(): Boolean {
                return index + 1 < size()
            }
        }
    }

    /**
     * Return a copy of the internal keys array. This array can be modified.
     *
     * @webref intdict:method
     * @brief Return a copy of the internal keys array
     */
    fun keyArray(): Array<String?> {
        crop()
        return keyArray(null)
    }

    fun keyArray(outgoing: Array<String?>?): Array<String?> {
        var outgoing = outgoing
        if (outgoing == null || outgoing.size != count) {
            outgoing = arrayOfNulls(count)
        }
        System.arraycopy(keys, 0, outgoing, 0, count)
        return outgoing
    }

    fun value(index: Int): Long {
        return values[index]
    }

    /**
     * @webref intdict:method
     * @brief Return the internal array being used to store the values
     */
    fun values(): Iterable<Long?> {
        return object : Iterable<Long?> {
            override fun iterator(): Iterator<Long?> {
                return valueIterator()
            }
        }
    }

    fun valueIterator(): MutableIterator<Long?> {
        return object : MutableIterator<Long?> {
            var index = -1
            override fun remove() {
                removeIndex(index)
                index--
            }

            override fun next(): Long {
                return value(++index)
            }

            override fun hasNext(): Boolean {
                return index + 1 < size()
            }
        }
    }

    /**
     * Create a new array and copy each of the values into it.
     *
     * @webref intdict:method
     * @brief Create a new array and copy each of the values into it
     */
    fun valueArray(): IntArray {
        crop()
        return valueArray(null)
    }

    /**
     * Fill an already-allocated array with the values (more efficient than
     * creating a new array each time). If 'array' is null, or not the same
     * size as the number of values, a new array will be allocated and returned.
     *
     * @param array values to copy into the array
     */
    fun valueArray(array: IntArray?): IntArray {
        var array = array
        if (array == null || array.size != size()) {
            array = IntArray(count)
        }
        System.arraycopy(values, 0, array, 0, count)
        return array
    }

    /**
     * Return a value for the specified key.
     *
     * @webref intdict:method
     * @brief Return a value for the specified key
     */
    operator fun get(key: String): Long {
        val index = index(key)
        require(index != -1) { "No key named '$key'" }
        return values[index]
    }

    operator fun get(key: String?, alternate: Long): Long {
        val index = index(key)
        return if (index == -1) alternate else values[index]
    }

    /**
     * Create a new key/value pair or change the value of one.
     *
     * @webref intdict:method
     * @brief Create a new key/value pair or change the value of one
     */
    operator fun set(key: String, amount: Long) {
        val index = index(key)
        if (index == -1) {
            create(key, amount)
        } else {
            values[index] = amount
        }
    }

    fun setIndex(index: Int, key: String, value: Long) {
        if (index < 0 || index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        keys[index] = key
        values[index] = value
    }

    /**
     * @webref intdict:method
     * @brief Check if a key is a part of the data structure
     */
    fun hasKey(key: String?): Boolean {
        return index(key) != -1
    }

    /**
     * Increase the value associated with a specific key by 1.
     *
     * @webref intdict:method
     * @brief Increase the value of a specific key value by 1
     */
    fun increment(key: String) {
        add(key, 1)
    }

    /**
     * Merge another dictionary into this one. Calling this increment()
     * since it doesn't make sense in practice for the other dictionary types,
     * even though it's technically an add().
     */
    fun increment(dict: LongDict) {
        for (i in 0 until dict.count) {
            add(dict.key(i), dict.value(i))
        }
    }

    /**
     * @webref intdict:method
     * @brief Add to a value
     */
    fun add(key: String, amount: Long) {
        val index = index(key)
        if (index == -1) {
            create(key, amount)
        } else {
            values[index] += amount
        }
    }

    /**
     * @webref intdict:method
     * @brief Subtract from a value
     */
    fun sub(key: String, amount: Long) {
        add(key, -amount)
    }

    /**
     * @webref intdict:method
     * @brief Multiply a value
     */
    fun mult(key: String?, amount: Long) {
        val index = index(key)
        if (index != -1) {
            values[index] *= amount
        }
    }

    /**
     * @webref intdict:method
     * @brief Divide a value
     */
    fun div(key: String?, amount: Long) {
        val index = index(key)
        if (index != -1) {
            values[index] /= amount
        }
    }

    private fun checkMinMax(functionName: String) {
        if (count == 0) {
            val msg = String.format("Cannot use %s() on an empty %s.",
                    functionName, javaClass.simpleName)
            throw RuntimeException(msg)
        }
    }

    // return the index of the minimum value
    fun minIndex(): Int {
        //checkMinMax("minIndex");
        if (count == 0) return -1
        var index = 0
        var value = values[0]
        for (i in 1 until count) {
            if (values[i] < value) {
                index = i
                value = values[i]
            }
        }
        return index
    }

    // return the key for the minimum value
    fun minKey(): String? {
        checkMinMax("minKey")
        val index = minIndex()
        return if (index == -1) {
            null
        } else keys[index]
    }

    // return the minimum value, or throw an error if there are no values
    fun minValue(): Long {
        checkMinMax("minValue")
        return values[minIndex()]
    }

    // return the index of the max value
    fun maxIndex(): Int {
        //checkMinMax("maxIndex");
        if (count == 0) {
            return -1
        }
        var index = 0
        var value = values[0]
        for (i in 1 until count) {
            if (values[i] > value) {
                index = i
                value = values[i]
            }
        }
        return index
    }

    /** return the key corresponding to the maximum value or null if no entries  */
    fun maxKey(): String? {
        //checkMinMax("maxKey");
        val index = maxIndex()
        return if (index == -1) {
            null
        } else keys[index]
    }

    // return the maximum value or throw an error if zero length
    fun maxValue(): Long {
        checkMinMax("maxIndex")
        return values[maxIndex()]
    }

    fun sum(): Long {
        var sum: Long = 0
        for (i in 0 until count) {
            sum += values[i]
        }
        return sum
    }

    fun index(what: String?): Int {
        val found = indices[what]
        return found ?: -1
    }

    protected fun create(what: String, much: Long) {
        if (count == keys.size) {
            keys = PApplet.expand(keys)
            values = PApplet.expand(values)
        }
        indices[what] = Integer.valueOf(count)
        keys[count] = what
        values[count] = much
        count++
    }

    /**
     * @webref intdict:method
     * @brief Remove a key/value pair
     */
    fun remove(key: String): Long {
        val index = index(key)
        if (index == -1) {
            throw NoSuchElementException("'$key' not found")
        }
        val value = values[index]
        removeIndex(index)
        return value
    }

    fun removeIndex(index: Int): Long {
        if (index < 0 || index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        val value = values[index]
        indices.remove(keys[index])
        for (i in index until count - 1) {
            keys[i] = keys[i + 1]
            values[i] = values[i + 1]
            indices[keys[i]!!] = i
        }
        count--
        keys[count] = null
        values[count] = 0
        return value
    }

    fun swap(a: Int, b: Int) {
        val tkey = keys[a]
        val tvalue = values[a]
        keys[a] = keys[b]
        values[a] = values[b]
        keys[b] = tkey
        values[b] = tvalue

//    indices.put(keys[a], Integer.valueOf(a));
//    indices.put(keys[b], Integer.valueOf(b));
    }

    /**
     * Sort the keys alphabetically (ignoring case). Uses the value as a
     * tie-breaker (only really possible with a key that has a case change).
     *
     * @webref intdict:method
     * @brief Sort the keys alphabetically
     */
    fun sortKeys() {
        sortImpl(true, false, true)
    }

    /**
     * Sort the keys alphabetically in reverse (ignoring case). Uses the value as a
     * tie-breaker (only really possible with a key that has a case change).
     *
     * @webref intdict:method
     * @brief Sort the keys alphabetically in reverse
     */
    fun sortKeysReverse() {
        sortImpl(true, true, true)
    }
    /**
     * Set true to ensure that the order returned is identical. Slightly
     * slower because the tie-breaker for identical values compares the keys.
     * @param stable
     */
    /**
     * Sort by values in ascending order. The smallest value will be at [0].
     *
     * @webref intdict:method
     * @brief Sort by values in ascending order
     */
    @JvmOverloads
    fun sortValues(stable: Boolean = true) {
        sortImpl(false, false, stable)
    }

    /**
     * Sort by values in descending order. The largest value will be at [0].
     *
     * @webref intdict:method
     * @brief Sort by values in descending order
     */
    @JvmOverloads
    fun sortValuesReverse(stable: Boolean = true) {
        sortImpl(false, true, stable)
    }

    protected fun sortImpl(useKeys: Boolean, reverse: Boolean,
                           stable: Boolean) {
        val s: Sort = object : Sort() {
            override fun size(): Int {
                return count
            }

            override fun compare(a: Int, b: Int): Int {
                var diff: Long = 0
                if (useKeys) {
                    diff = keys[a]!!.compareTo(keys[b]!!, ignoreCase = true).toLong()
                    if (diff == 0L) {
                        diff = values[a] - values[b]
                    }
                } else {  // sort values
                    diff = values[a] - values[b]
                    if (diff == 0L && stable) {
                        diff = keys[a]!!.compareTo(keys[b]!!, ignoreCase = true).toLong()
                    }
                }
                return if (diff == 0L) {
                    0
                } else if (reverse) {
                    if (diff < 0) 1 else -1
                } else {
                    if (diff < 0) -1 else 1
                }
            }

            override fun swap(a: Int, b: Int) {
                this@LongDict.swap(a, b)
            }
        }
        s.run()

        // Set the indices after sort/swaps (performance fix 160411)
        resetIndices()
    }// a little more accuracy

    /**
     * Sum all of the values in this dictionary, then return a new FloatDict of
     * each key, divided by the total sum. The total for all values will be ~1.0.
     * @return an IntDict with the original keys, mapped to their pct of the total
     */
    val percent: FloatDict
        get() {
            val sum = sum().toDouble() // a little more accuracy
            val outgoing = FloatDict()
            for (i in 0 until size()) {
                val percent = value(i) / sum
                outgoing[key(i)] = percent.toFloat()
            }
            return outgoing
        }

    /** Returns a duplicate copy of this object.  */
    fun copy(): LongDict {
        val outgoing = LongDict(count)
        System.arraycopy(keys, 0, outgoing.keys, 0, count)
        System.arraycopy(values, 0, outgoing.values, 0, count)
        for (i in 0 until count) {
            outgoing.indices[keys[i]!!] = i
        }
        outgoing.count = count
        return outgoing
    }

    fun print() {
        for (i in 0 until size()) {
            println(keys[i] + " = " + values[i])
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
     * Write tab-delimited entries to a PrintWriter
     */
    fun write(writer: PrintWriter) {
        for (i in 0 until count) {
            writer.println(keys[i] + "\t" + values[i])
        }
        writer.flush()
    }

    /**
     * Return this dictionary as a String in JSON format.
     */
    fun toJSON(): String {
        val items = StringList()
        for (i in 0 until count) {
            items.append(JSONObject.quote(keys[i]) + ": " + values[i])
        }
        return "{ " + items.join(", ") + " }"
    }

    override fun toString(): String {
        return javaClass.simpleName + " size=" + size() + " " + toJSON()
    }
}