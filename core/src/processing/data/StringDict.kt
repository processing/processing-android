package processing.data

import processing.core.PApplet
import processing.data.IntList.Companion.fromRange
import java.io.BufferedReader
import java.io.File
import java.io.PrintWriter
import java.util.*

/**
 * A simple table class to use a String as a lookup for another String value.
 *
 * @webref data:composite
 * @see IntDict
 *
 * @see FloatDict
 */
class StringDict {
    /** Number of elements in the table  */
    protected var count = 0
    protected var keys: Array<String?>
    protected var values: Array<String?>

    /** Internal implementation for faster lookups  */
    private var indices = HashMap<String, Int>()

    constructor() {
        count = 0
        keys = arrayOfNulls(10)
        values = arrayOfNulls(10)
    }

    /**
     * Create a new lookup pre-allocated to a specific length. This will not
     * change the size(), but is more efficient than not specifying a length.
     * Use it when you know the rough size of the thing you're creating.
     *
     * @nowebref
     */
    constructor(length: Int) {
        count = 0
        keys = arrayOfNulls(length)
        values = arrayOfNulls(length)
    }

    /**
     * Read a set of entries from a Reader that has each key/value pair on
     * a single line, separated by a tab.
     *
     * @nowebref
     */
    constructor(reader: BufferedReader?) {
        val lines = PApplet.loadStrings(reader)
        keys = arrayOfNulls(lines.size)
        values = arrayOfNulls(lines.size)
        for (i in lines.indices) {
            val pieces = PApplet.split(lines[i], '\t')
            if (pieces.size == 2) {
                keys[count] = pieces[0]
                values[count] = pieces[1]
                indices[keys[count]!!] = count
                count++
            }
        }
    }

    /**
     * @nowebref
     */
    constructor(keys: Array<String?>, values: Array<String?>) {
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
     * new StringDict(new String[][] {
     * { "key1", "value1" },
     * { "key2", "value2" }
     * });
    </pre> *
     * It's no Python, but beats a static { } block with HashMap.put() statements.
     */
    constructor(pairs: Array<Array<String>>) {
        count = pairs.size
        keys = arrayOfNulls(count)
        values = arrayOfNulls(count)
        for (i in 0 until count) {
            keys[i] = pairs[i][0]
            values[i] = pairs[i][1]
            indices[keys[i]!!] = i
        }
    }

    /**
     * Create a dictionary that maps between column titles and cell entries
     * in a TableRow. If two columns have the same name, the later column's
     * values will override the earlier values.
     */
    constructor(row: TableRow) : this(row.getColumnCount()) {
        var titles = row.getColumnTitles()
        if (titles == null) {
            titles = StringList(fromRange(row.getColumnCount())).array()
        }
        for (col in 0 until row.getColumnCount()) {
            set(titles[col]!!, row.getString(col)!!)
        }
        // remove unused and overwritten entries
        crop()
    }

    /**
     * @webref stringdict:method
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
        val newValues = arrayOfNulls<String>(length)
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
     * @webref stringdict:method
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
    inner class Entry internal constructor(var key: String?, var value: String?)

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
     * @webref stringdict:method
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

    fun value(index: Int): String {
        return values[index]!!
    }

    /**
     * @webref stringdict:method
     * @brief Return the internal array being used to store the values
     */
    fun values(): Iterable<String?> {
        return object : Iterable<String?> {
            override fun iterator(): Iterator<String?> {
                return valueIterator()
            }
        }
    }

    fun valueIterator(): MutableIterator<String?> {
        return object : MutableIterator<String?> {
            var index = -1
            override fun remove() {
                removeIndex(index)
                index--
            }

            override fun next(): String {
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
     * @webref stringdict:method
     * @brief Create a new array and copy each of the values into it
     */
    fun valueArray(): Array<String?> {
        crop()
        return valueArray(null)
    }

    /**
     * Fill an already-allocated array with the values (more efficient than
     * creating a new array each time). If 'array' is null, or not the same
     * size as the number of values, a new array will be allocated and returned.
     */
    fun valueArray(array: Array<String?>?): Array<String?> {
        var array = array
        if (array == null || array.size != size()) {
            array = arrayOfNulls(count)
        }
        System.arraycopy(values, 0, array, 0, count)
        return array
    }

    /**
     * Return a value for the specified key.
     *
     * @webref stringdict:method
     * @brief Return a value for the specified key
     */
    operator fun get(key: String?): String? {
        val index = index(key)
        return if (index == -1) null else values[index]
    }

    operator fun get(key: String?, alternate: String): String {
        val index = index(key)
        return if (index == -1) alternate else values[index]!!
    }

    /**
     * @webref stringdict:method
     * @brief Create a new key/value pair or change the value of one
     */
    operator fun set(key: String, value: String) {
        val index = index(key)
        if (index == -1) {
            create(key, value)
        } else {
            values[index] = value
        }
    }

    fun setIndex(index: Int, key: String, value: String) {
        if (index < 0 || index >= count) {
            throw ArrayIndexOutOfBoundsException(index)
        }
        keys[index] = key
        values[index] = value
    }

    fun index(what: String?): Int {
        val found = indices[what]
        return found ?: -1
    }

    /**
     * @webref stringdict:method
     * @brief Check if a key is a part of the data structure
     */
    fun hasKey(key: String?): Boolean {
        return index(key) != -1
    }

    protected fun create(key: String, value: String) {
        if (count == keys.size) {
            keys = PApplet.expand(keys)
            values = PApplet.expand(values)
        }
        indices[key] = Integer.valueOf(count)
        keys[count] = key
        values[count] = value
        count++
    }

    /**
     * @webref stringdict:method
     * @brief Remove a key/value pair
     */
    fun remove(key: String): String? {
        val index = index(key)
        if (index == -1) {
            throw NoSuchElementException("'$key' not found")
        }
        val value = values[index]
        removeIndex(index)
        return value
    }

    fun removeIndex(index: Int): String? {
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
        values[count] = null
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
     * @webref stringdict:method
     * @brief Sort the keys alphabetically
     */
    fun sortKeys() {
        sortImpl(true, false)
    }

    /**
     * @webref stringdict:method
     * @brief Sort the keys alphabetically in reverse
     */
    fun sortKeysReverse() {
        sortImpl(true, true)
    }

    /**
     * Sort by values in descending order (largest value will be at [0]).
     *
     * @webref stringdict:method
     * @brief Sort by values in ascending order
     */
    fun sortValues() {
        sortImpl(false, false)
    }

    /**
     * @webref stringdict:method
     * @brief Sort by values in descending order
     */
    fun sortValuesReverse() {
        sortImpl(false, true)
    }

    protected fun sortImpl(useKeys: Boolean, reverse: Boolean) {
        val s: Sort = object : Sort() {
            override fun size(): Int {
                return count
            }

            override fun compare(a: Int, b: Int): Int {
                var diff = 0
                if (useKeys) {
                    diff = keys[a]!!.compareTo(keys[b]!!, ignoreCase = true)
                    if (diff == 0) {
                        diff = values[a]!!.compareTo(values[b]!!, ignoreCase = true)
                    }
                } else {  // sort values
                    diff = values[a]!!.compareTo(values[b]!!, ignoreCase = true)
                    if (diff == 0) {
                        diff = keys[a]!!.compareTo(keys[b]!!, ignoreCase = true)
                    }
                }
                return if (reverse) -diff else diff
            }

            override fun swap(a: Int, b: Int) {
                this@StringDict.swap(a, b)
            }
        }
        s.run()

        // Set the indices after sort/swaps (performance fix 160411)
        resetIndices()
    }

    /** Returns a duplicate copy of this object.  */
    fun copy(): StringDict {
        val outgoing = StringDict(count)
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
            items.append(JSONObject.quote(keys[i]) + ": " + JSONObject.quote(values[i]))
        }
        return "{ " + items.join(", ") + " }"
    }

    override fun toString(): String {
        return javaClass.simpleName + " size=" + size() + " " + toJSON()
    }
}