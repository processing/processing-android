package processing.data

import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.util.*

import processing.core.PApplet

/**
 * A simple table class to use a String as a lookup for an double value.
 *
 * @webref data:composite
 * @see IntDict
 *
 * @see StringDict
 */
open class DoubleDict {
    /** Number of elements in the table  */
    protected var count = 0
    protected var keys: Array<String?>
    protected var values: DoubleArray

    /** Internal implementation for faster lookups  */
    private var indices = HashMap<String, Int>()

    constructor() {
        count = 0
        keys = arrayOfNulls(10)
        values = DoubleArray(10)
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
        values = DoubleArray(length)
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
        values = DoubleArray(lines!!.size)
        for (i in lines!!.indices) {
            val pieces = PApplet.split(lines!![i], '\t')
            if (pieces!!.size == 2) {
                keys[count] = pieces[0]!!
                values[count] = PApplet.parseFloat(pieces!![1]!!).toDouble()
                indices[pieces!![0]!!] = count
                count++
            }
        }
    }

    /**
     * @nowebref
     */
    constructor(keys: Array<String?>, values: DoubleArray) {
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
        values = DoubleArray(count)
        for (i in 0 until count) {
            keys[i] = pairs[i][0] as String
            values[i] = (pairs[i][1] as Float).toDouble()
            indices[keys[i]!!] = i
        }
    }

    constructor(incoming: Map<String, Double>) {
        count = incoming.size
        keys = arrayOfNulls(count)
        values = DoubleArray(count)
        var index = 0
        for ((key, value) in incoming) {
            keys[index] = key
            values[index] = value
            indices[keys[index]!!] = index
            index++
        }
    }

    /**
     * @webref doubledict:method
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
        if (length == count) return
        require(length <= count) { "resize() can only be used to shrink the dictionary" }
        require(length >= 1) { "resize($length) is too small, use 1 or higher" }
        val newKeys = arrayOfNulls<String>(length)
        val newValues = DoubleArray(length)
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
     * @webref doubledict:method
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

    inner class Entry internal constructor(var key: String?, var value: Double)

    fun entries(): Iterable<Entry?> {
        return object : Iterable<Entry?> {
            override fun iterator(): Iterator<Entry?> {
                return entryIterator()
            }
        }
    }

    fun entryIterator(): Iterator<Entry?> {
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
    fun keyIterator(): Iterator<String?> {
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
     * @webref doubledict:method
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

    fun value(index: Int): Double {
        return values[index]
    }

    /**
     * @webref doubledict:method
     * @brief Return the internal array being used to store the values
     */
    fun values(): Iterable<Double?> {
        return object : Iterable<Double?> {
            override fun iterator(): Iterator<Double?> {
                return valueIterator()
            }
        }
    }

    fun valueIterator(): Iterator<Double?> {
        return object : MutableIterator<Double?> {
            var index = -1
            override fun remove() {
                removeIndex(index)
                index--
            }

            override fun next(): Double {
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
     * @webref doubledict:method
     * @brief Create a new array and copy each of the values into it
     */
    fun valueArray(): DoubleArray {
        crop()
        return valueArray(null)
    }

    /**
     * Fill an already-allocated array with the values (more efficient than
     * creating a new array each time). If 'array' is null, or not the same
     * size as the number of values, a new array will be allocated and returned.
     */
    fun valueArray(array: DoubleArray?): DoubleArray {
        var array = array
        if (array == null || array.size != size()) {
            array = DoubleArray(count)
        }
        System.arraycopy(values, 0, array, 0, count)
        return array
    }

    /**
     * Return a value for the specified key.
     *
     * @webref doubledict:method
     * @brief Return a value for the specified key
     */
    operator fun get(key: String): Double {
        val index = index(key)
        require(index != -1) { "No key named '$key'" }
        return values[index]
    }

    operator fun get(key: String?, alternate: Double): Double {
        val index = index(key)
        return if (index == -1) {
            alternate
        } else values[index]
    }

    /**
     * @webref doubledict:method
     * @brief Create a new key/value pair or change the value of one
     */
    operator fun set(key: String, amount: Double) {
        val index = index(key)
        if (index == -1) {
            create(key, amount)
        } else {
            values[index] = amount
        }
    }

    fun setIndex(index: Int, key: String, value: Double) {
        if (index < 0 || index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        keys[index] = key
        values[index] = value
    }

    /**
     * @webref doubledict:method
     * @brief Check if a key is a part of the data structure
     */
    fun hasKey(key: String?): Boolean {
        return index(key) != -1
    }

    /**
     * @webref doubledict:method
     * @brief Add to a value
     */
    fun add(key: String, amount: Double) {
        val index = index(key)
        if (index == -1) {
            create(key, amount)
        } else {
            values[index] += amount
        }
    }

    /**
     * @webref doubledict:method
     * @brief Subtract from a value
     */
    fun sub(key: String, amount: Double) {
        add(key, -amount)
    }

    /**
     * @webref doubledict:method
     * @brief Multiply a value
     */
    fun mult(key: String?, amount: Double) {
        val index = index(key)
        if (index != -1) {
            values[index] *= amount
        }
    }

    /**
     * @webref doubledict:method
     * @brief Divide a value
     */
    fun div(key: String?, amount: Double) {
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

    /**
     * @webref doublelist:method
     * @brief Return the smallest value
     */
    fun minIndex(): Int {
        //checkMinMax("minIndex");
        if (count == 0) return -1

        // Will still return NaN if there are 1 or more entries, and they're all NaN
        var m = Float.NaN.toDouble()
        var mi = -1
        for (i in 0 until count) {
            // find one good value to start
            if (values[i] == values[i]) {
                m = values[i]
                mi = i

                // calculate the rest
                for (j in i + 1 until count) {
                    val d = values[j]
                    if (d == d && d < m) {
                        m = values[j]
                        mi = j
                    }
                }
                break
            }
        }
        return mi
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
    fun minValue(): Double {
        checkMinMax("minValue")
        val index = minIndex()
        return if (index == -1) {
            Float.NaN.toDouble()
        } else values[index]
    }

    /**
     * @webref doublelist:method
     * @brief Return the largest value
     */
    // The index of the entry that has the max value. Reference above is incorrect.
    fun maxIndex(): Int {
        //checkMinMax("maxIndex");
        if (count == 0) {
            return -1
        }
        // Will still return NaN if there is 1 or more entries, and they're all NaN
        var m = Double.NaN
        var mi = -1
        for (i in 0 until count) {
            // find one good value to start
            if (values[i] == values[i]) {
                m = values[i]
                mi = i

                // calculate the rest
                for (j in i + 1 until count) {
                    val d = values[j]
                    if (!java.lang.Double.isNaN(d) && d > m) {
                        m = values[j]
                        mi = j
                    }
                }
                break
            }
        }
        return mi
    }

    /** The key for a max value; null if empty or everything is NaN (no max).  */
    fun maxKey(): String? {
        //checkMinMax("maxKey");
        val index = maxIndex()
        return if (index == -1) {
            null
        } else keys[index]
    }

    /** The max value. (Or NaN if no entries or they're all NaN.)  */
    fun maxValue(): Double {
        //checkMinMax("maxValue");
        val index = maxIndex()
        return if (index == -1) {
            Float.NaN.toDouble()
        } else values[index]
    }

    fun sum(): Double {
        var sum = 0.0
        for (i in 0 until count) {
            sum += values[i]
        }
        return sum
    }

    fun index(what: String?): Int {
        val found = indices[what]
        return found ?: -1
    }

    protected fun create(what: String, much: Double) {
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
     * @webref doubledict:method
     * @brief Remove a key/value pair
     */
    fun remove(key: String): Double {
        val index = index(key)
        if (index == -1) {
            throw NoSuchElementException("'$key' not found")
        }
        val value = values[index]
        removeIndex(index)
        return value
    }

    fun removeIndex(index: Int): Double {
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
        values[count] = 0.0
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
     * @webref doubledict:method
     * @brief Sort the keys alphabetically
     */
    fun sortKeys() {
        sortImpl(true, false, true)
    }

    /**
     * @webref doubledict:method
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
     * Sort by values in descending order (largest value will be at [0]).
     *
     * @webref doubledict:method
     * @brief Sort by values in ascending order
     */
    @JvmOverloads
    fun sortValues(stable: Boolean = true) {
        sortImpl(false, false, stable)
    }

    /**
     * @webref doubledict:method
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
                return if (useKeys) {
                    count // don't worry about NaN values
                } else if (count == 0) {  // skip the NaN check, it'll AIOOBE
                    0
                } else {  // first move NaN values to the end of the list
                    var right = count - 1
                    while (values[right] != values[right]) {
                        right--
                        if (right == -1) {
                            return 0 // all values are NaN
                        }
                    }
                    for (i in right downTo 0) {
                        if (java.lang.Double.isNaN(values[i])) {
                            swap(i, right)
                            --right
                        }
                    }
                    right + 1
                }
            }

            override fun compare(a: Int, b: Int): Int {
                var diff = 0.0
                if (useKeys) {
                    diff = keys[a]!!.compareTo(keys[b]!!, ignoreCase = true).toDouble()
                    if (diff == 0.0) {
                        diff = values[a] - values[b]
                    }
                } else {  // sort values
                    diff = values[a] - values[b]
                    if (diff == 0.0 && stable) {
                        diff = keys[a]!!.compareTo(keys[b]!!, ignoreCase = true).toDouble()
                    }
                }
                return if (diff == 0.0) {
                    0
                } else if (reverse) {
                    if (diff < 0) 1 else -1
                } else {
                    if (diff < 0) -1 else 1
                }
            }

            override fun swap(a: Int, b: Int) {
                this@DoubleDict.swap(a, b)
            }
        }
        s.run()

        // Set the indices after sort/swaps (performance fix 160411)
        resetIndices()
    }

    /**
     * Sum all of the values in this dictionary, then return a new FloatDict of
     * each key, divided by the total sum. The total for all values will be ~1.0.
     * @return a FloatDict with the original keys, mapped to their pct of the total
     */
    val percent: DoubleDict
        get() {
            val sum = sum()
            val outgoing = DoubleDict()
            for (i in 0 until size()) {
                val percent = value(i) / sum
                outgoing[key(i)] = percent
            }
            return outgoing
        }

    /** Returns a duplicate copy of this object.  */
    fun copy(): DoubleDict {
        val outgoing = DoubleDict(count)
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
     * Write tab-delimited entries out to
     * @param writer
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