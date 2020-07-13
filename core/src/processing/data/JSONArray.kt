package processing.data

import processing.core.PApplet

import processing.data.JSONObject.Companion.indent
import processing.data.JSONObject.Companion.testValidity
import processing.data.JSONObject.Companion.valueToString
import processing.data.JSONObject.Companion.wrap
import processing.data.JSONObject.Companion.writeValue

import java.io.*
import java.util.*

// This code has been modified heavily to more closely match the rest of the
// Processing API. In the spirit of the rest of the project, where we try to
// keep the API as simple as possible, we have erred on the side of being
// conservative in choosing which functions to include, since we haven't yet
// decided what's truly necessary. Power users looking for a full-featured
// version can use the original version from json.org, or one of the many
// other APIs that are available. As with all Processing API, if there's a
// function that should be added, please let use know, and have others vote:
// http://code.google.com/p/processing/issues/list
/*
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */ /**
 * A JSONArray is an ordered sequence of values. Its external text form is a
 * string wrapped in square brackets with commas separating the values. The
 * internal form is an object having `get` and `opt`
 * methods for accessing the values by index, and `put` methods for
 * adding or replacing values. The values can be any of these types:
 * `Boolean`, `JSONArray`, `JSONObject`,
 * `Number`, `String`, or the
 * `JSONObject.NULL object`.
 *
 *
 * The constructor can convert a JSON text into a Java object. The
 * `toString` method converts to JSON text.
 *
 *
 * A `get` method returns a value if one can be found, and throws an
 * exception if one cannot be found. An `opt` method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 *
 *
 * The generic `get()` and `opt()` methods return an
 * object which you can cast or query for type. There are also typed
 * `get` and `opt` methods that do type checking and type
 * coercion for you.
 *
 *
 * The texts produced by the `toString` methods strictly conform to
 * JSON syntax rules. The constructors are more forgiving in the texts they will
 * accept:
 *
 *  * An extra `,`&nbsp;<small>(comma)</small> may appear just
 * before the closing bracket.
 *  * The `null` value will be inserted when there is `,`
 * &nbsp;<small>(comma)</small> elision.
 *  * Strings may be quoted with `'`&nbsp;<small>(single
 * quote)</small>.
 *  * Strings do not need to be quoted at all if they do not begin with a quote
 * or single quote, and if they do not contain leading or trailing spaces, and
 * if they do not contain any of these characters:
 * `{ } [ ] / \ : , = ; #` and if they do not look like numbers and
 * if they are not the reserved words `true`, `false`, or
 * `null`.
 *  * Values can be separated by `;` <small>(semicolon)</small> as
 * well as by `,` <small>(comma)</small>.
 *
 *
 * @author JSON.org
 * @version 2012-11-13
 * @webref data:composite
 * @see JSONObject
 *
 * @see PApplet.loadJSONObject
 * @see PApplet.loadJSONArray
 * @see PApplet.saveJSONObject
 * @see PApplet.saveJSONArray
 */
open class JSONArray {
    /**
     * The arrayList where the JSONArray's properties are kept.
     */
    private val myArrayList: ArrayList<Any?>

    /**
     * Construct an empty JSONArray.
     */
    constructor() {
        myArrayList = ArrayList<Any?>()
    }

    /**
     * @nowebref
     */
    constructor(reader: Reader?) : this(JSONTokener(reader!!)) {}

    /**
     * Construct a JSONArray from a JSONTokener.
     *
     * @param x A JSONTokener
     * @throws RuntimeException If there is a syntax error.
     * @nowebref
     */
     constructor(x: JSONTokener) : this() {
        if (x.nextClean() != '[') {
            throw RuntimeException("A JSONArray text must start with '['")
        }
        if (x.nextClean() != ']') {
            x.back()
            while (true) {
                if (x.nextClean() == ',') {
                    x.back()
                    myArrayList.add(JSONObject.NULL)
                } else {
                    x.back()
                    myArrayList.add(x.nextValue())
                }
                when (x.nextClean()) {
                    ';', ',' -> {
                        if (x.nextClean() == ']') {
                            return
                        }
                        x.back()
                    }
                    ']' -> return
                    else -> throw RuntimeException("Expected a ',' or ']'")
                }
            }
        }
    }

    /**
     * @nowebref
     */
    constructor(list: IntList) {
        myArrayList = ArrayList()
        for (item in list.values()) {
            myArrayList.add(Integer.valueOf(item))
        }
    }

    /**
     * @nowebref
     */
    constructor(list: FloatList) {
        myArrayList = ArrayList()
        for (item in list.values()) {
            myArrayList.add(java.lang.Float.valueOf(item))
        }
    }

    /**
     * @nowebref
     */
    constructor(list: StringList) {
        myArrayList = ArrayList()
        for (item in list.values()) {
            myArrayList.add(item)
        }
    }

    //  /**
    //   * Construct a JSONArray from a Collection.
    //   * @param collection     A Collection.
    //   */
    //  public JSONArray(Collection collection) {
    //    myArrayList = new ArrayList<Object>();
    //    if (collection != null) {
    //      Iterator iter = collection.iterator();
    //      while (iter.hasNext()) {
    //        myArrayList.add(JSONObject.wrap(iter.next()));
    //      }
    //    }
    //  }
    // TODO not decided whether we keep this one, but used heavily by JSONObject

    /**
     * Construct a JSONArray from an array
     * @throws RuntimeException If not an array.
     */
     constructor(array: Any?) : this() {
        if (array!!.javaClass.isArray) {
            val length = java.lang.reflect.Array.getLength(array)
            var i = 0
            while (i < length) {
                this.append(wrap(java.lang.reflect.Array.get(array, i)))
                i += 1
            }
        } else {
            throw RuntimeException("JSONArray initial value should be a string or collection or array.")
        }
    }

    /**
     * Get the optional object value associated with an index.
     * @param index must be between 0 and length() - 1
     * @return      An object value, or null if there is no
     * object at that index.
     */
    private fun opt(index: Int): Any? {
        return if (index < 0 || index >= size()) {
            null
        } else myArrayList[index]
    }

    /**
     * Get the object value associated with an index.
     * @param index must be between 0 and length() - 1
     * @return An object value.
     * @throws RuntimeException If there is no value for the index.
     */
    operator fun get(index: Int): Any? {
        return opt(index) ?: throw RuntimeException("JSONArray[$index] not found.")
    }

    /**
     * Get the string associated with an index.
     *
     * @webref jsonarray:method
     * @brief Gets the String value associated with an index
     * @param index must be between 0 and length() - 1
     * @return      A string value.
     * @throws RuntimeException If there is no string value for the index.
     * @see JSONArray.getInt
     * @see JSONArray.getFloat
     * @see JSONArray.getBoolean
     */
    fun getString(index: Int): String {
        val `object` = this[index]
        if (`object` is String) {
            return `object`
        }
        throw RuntimeException("JSONArray[$index] not a string.")
    }

    /**
     * Get the optional string associated with an index.
     * The defaultValue is returned if the key is not found.
     *
     * @param index The index must be between 0 and length() - 1.
     * @param defaultValue     The default value.
     * @return      A String value.
     */
    fun getString(index: Int, defaultValue: String): String {
        val `object` = opt(index)
        return if (JSONObject.NULL == `object`) defaultValue else `object`.toString()
    }

    /**
     * Get the int value associated with an index.
     *
     * @webref jsonarray:method
     * @brief Gets the int value associated with an index
     * @param index must be between 0 and length() - 1
     * @return The value.
     * @throws RuntimeException If the key is not found or if the value is not a number.
     * @see JSONArray.getFloat
     * @see JSONArray.getString
     * @see JSONArray.getBoolean
     */
    fun getInt(index: Int): Int {
        val `object` = this[index]
        return try {
            if (`object` is Number) `object`.toInt() else (`object` as String?)!!.toInt ()
        } catch (e: Exception) {
            throw RuntimeException("JSONArray[$index] is not a number.")
        }
    }

    /**
     * Get the optional int value associated with an index.
     * The defaultValue is returned if there is no value for the index,
     * or if the value is not a number and cannot be converted to a number.
     * @param index The index must be between 0 and length() - 1.
     * @param defaultValue     The default value.
     * @return      The value.
     */
    fun getInt(index: Int, defaultValue: Int): Int {
        return try {
            getInt(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get the long value associated with an index.
     *
     * @param index The index must be between 0 and length() - 1
     * @return      The value.
     * @throws   RuntimeException If the key is not found or if the value cannot
     * be converted to a number.
     */
    fun getLong(index: Int): Long {
        val `object` = this[index]
        return try {
            if (`object` is Number) `object`.toLong() else (`object` as String?)!!.toLong ()
        } catch (e: Exception) {
            throw RuntimeException("JSONArray[$index] is not a number.")
        }
    }

    /**
     * Get the optional long value associated with an index.
     * The defaultValue is returned if there is no value for the index,
     * or if the value is not a number and cannot be converted to a number.
     * @param index The index must be between 0 and length() - 1.
     * @param defaultValue     The default value.
     * @return      The value.
     */
    fun getLong(index: Int, defaultValue: Long): Long {
        return try {
            this.getLong(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get a value from an index as a float. JSON uses 'double' values
     * internally, so this is simply getDouble() cast to a float.
     *
     * @webref jsonarray:method
     * @brief Gets the float value associated with an index
     * @param index must be between 0 and length() - 1
     * @see JSONArray.getInt
     * @see JSONArray.getString
     * @see JSONArray.getBoolean
     */
    fun getFloat(index: Int): Float {
        return getDouble(index).toFloat()
    }

    fun getFloat(index: Int, defaultValue: Float): Float {
        return try {
            getFloat(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get the double value associated with an index.
     *
     * @param index must be between 0 and length() - 1
     * @return      The value.
     * @throws   RuntimeException If the key is not found or if the value cannot
     * be converted to a number.
     */
    fun getDouble(index: Int): Double {
        val `object` = this[index]
        return try {
            if (`object` is Number) `object`.toDouble() else (`object` as String?)!!.toDouble ()
        } catch (e: Exception) {
            throw RuntimeException("JSONArray[$index] is not a number.")
        }
    }

    /**
     * Get the optional double value associated with an index.
     * The defaultValue is returned if there is no value for the index,
     * or if the value is not a number and cannot be converted to a number.
     *
     * @param index subscript
     * @param defaultValue     The default value.
     * @return      The value.
     */
    fun getDouble(index: Int, defaultValue: Double): Double {
        return try {
            this.getDouble(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get the boolean value associated with an index.
     * The string values "true" and "false" are converted to boolean.
     *
     * @webref jsonarray:method
     * @brief Gets the boolean value associated with an index
     * @param index must be between 0 and length() - 1
     * @return      The truth.
     * @throws RuntimeException If there is no value for the index or if the
     * value is not convertible to boolean.
     * @see JSONArray.getInt
     * @see JSONArray.getFloat
     * @see JSONArray.getString
     */
    fun getBoolean(index: Int): Boolean {
        val `object` = this[index]
        if (`object` == java.lang.Boolean.FALSE ||
                `object` is String &&
                `object`.equals("false", ignoreCase = true)) {
            return false
        } else if (`object` == java.lang.Boolean.TRUE ||
                `object` is String &&
                `object`.equals("true", ignoreCase = true)) {
            return true
        }
        throw RuntimeException("JSONArray[$index] is not a boolean.")
    }

    /**
     * Get the optional boolean value associated with an index.
     * It returns the defaultValue if there is no value at that index or if
     * it is not a Boolean or the String "true" or "false" (case insensitive).
     *
     * @param index The index must be between 0 and length() - 1.
     * @param defaultValue     A boolean default.
     * @return      The truth.
     */
    fun getBoolean(index: Int, defaultValue: Boolean): Boolean {
        return try {
            getBoolean(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get the JSONArray associated with an index.
     *
     * @webref jsonobject:method
     * @brief Gets the JSONArray associated with an index value
     * @param index must be between 0 and length() - 1
     * @return A JSONArray value.
     * @throws RuntimeException If there is no value for the index. or if the
     * value is not a JSONArray
     * @see JSONArray.getJSONObject
     * @see JSONArray.setJSONObject
     * @see JSONArray.setJSONArray
     */
    fun getJSONArray(index: Int): JSONArray {
        val `object` = this[index]
        if (`object` is JSONArray) {
            return `object`
        }
        throw RuntimeException("JSONArray[$index] is not a JSONArray.")
    }

    fun getJSONArray(index: Int, defaultValue: JSONArray): JSONArray {
        return try {
            getJSONArray(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Get the JSONObject associated with an index.
     *
     * @webref jsonobject:method
     * @brief Gets the JSONObject associated with an index value
     * @param index the index value of the object to get
     * @return A JSONObject value.
     * @throws RuntimeException If there is no value for the index or if the
     * value is not a JSONObject
     * @see JSONArray.getJSONArray
     * @see JSONArray.setJSONObject
     * @see JSONArray.setJSONArray
     */
    fun getJSONObject(index: Int): JSONObject {
        val `object` = this[index]
        if (`object` is JSONObject) {
            return `object`
        }
        throw RuntimeException("JSONArray[$index] is not a JSONObject.")
    }

    fun getJSONObject(index: Int, defaultValue: JSONObject): JSONObject {
        return try {
            getJSONObject(index)
        } catch (e: Exception) {
            defaultValue
        }
    }

    val stringArray: Array<String?>
        get() {
            val outgoing = arrayOfNulls<String>(size())
            for (i in 0 until size()) {
                outgoing[i] = getString(i)
            }
            return outgoing
        }

    /**
     * Get this entire array as a String array.
     *
     * @webref jsonarray:method
     * @brief Gets the entire array as an array of Strings
     * @see JSONArray.getIntArray
     */

//    fun getStringArray(): Array<String?> {
//        val outgoing = arrayOfNulls<String>(size())
//        for (i in 0 until size()) {
//            outgoing[i] = getString(i)
//        }
//        return outgoing
//    }

    /**
     * Get this entire array as an int array. Everything must be an int.
     *
     * @webref jsonarray:method
     * @brief Gets the entire array as array of ints
     * @see JSONArray.getStringArray
     */
    fun getIntArray(): IntArray {
        val outgoing = IntArray(size())
        for (i in 0 until size()) {
            outgoing[i] = getInt(i)
        }
        return outgoing
    }

    /** Get this entire array as a long array. Everything must be an long.  */
    fun getLongArray(): LongArray {
        val outgoing = LongArray(size())
        for (i in 0 until size()) {
            outgoing[i] = getLong(i)
        }
        return outgoing
    }

    /** Get this entire array as a float array. Everything must be an float.  */
    fun getFloatArray(): FloatArray {
        val outgoing = FloatArray(size())
        for (i in 0 until size()) {
            outgoing[i] = getFloat(i)
        }
        return outgoing
    }

    /** Get this entire array as a double array. Everything must be an double.  */
    fun getDoubleArray(): DoubleArray {
        val outgoing = DoubleArray(size())
        for (i in 0 until size()) {
            outgoing[i] = getDouble(i)
        }
        return outgoing
    }

    /** Get this entire array as a boolean array. Everything must be a boolean.  */
    fun getBooleanArray(): BooleanArray {
        val outgoing = BooleanArray(size())
        for (i in 0 until size()) {
            outgoing[i] = getBoolean(i)
        }
        return outgoing
    }

    //  /**
    //   * Get the optional boolean value associated with an index.
    //   * It returns false if there is no value at that index,
    //   * or if the value is not Boolean.TRUE or the String "true".
    //   *
    //   * @param index The index must be between 0 and length() - 1.
    //   * @return      The truth.
    //   */
    //  public boolean optBoolean(int index)  {
    //    return this.optBoolean(index, false);
    //  }
    //
    //
    //  /**
    //   * Get the optional double value associated with an index.
    //   * NaN is returned if there is no value for the index,
    //   * or if the value is not a number and cannot be converted to a number.
    //   *
    //   * @param index The index must be between 0 and length() - 1.
    //   * @return      The value.
    //   */
    //  public double optDouble(int index) {
    //    return this.optDouble(index, Double.NaN);
    //  }
    //
    //
    //  /**
    //   * Get the optional int value associated with an index.
    //   * Zero is returned if there is no value for the index,
    //   * or if the value is not a number and cannot be converted to a number.
    //   *
    //   * @param index The index must be between 0 and length() - 1.
    //   * @return      The value.
    //   */
    //  public int optInt(int index) {
    //    return this.optInt(index, 0);
    //  }
    //
    //
    //  /**
    //   * Get the optional long value associated with an index.
    //   * Zero is returned if there is no value for the index,
    //   * or if the value is not a number and cannot be converted to a number.
    //   *
    //   * @param index The index must be between 0 and length() - 1.
    //   * @return      The value.
    //   */
    //  public long optLong(int index) {
    //    return this.optLong(index, 0);
    //  }
    //
    //
    //  /**
    //   * Get the optional string value associated with an index. It returns an
    //   * empty string if there is no value at that index. If the value
    //   * is not a string and is not null, then it is coverted to a string.
    //   *
    //   * @param index The index must be between 0 and length() - 1.
    //   * @return      A String value.
    //   */
    //  public String optString(int index) {
    //    return this.optString(index, "");
    //  }

    /**
     * Append an String value. This increases the array's length by one.
     *
     * @webref jsonarray:method
     * @brief Appends a value, increasing the array's length by one
     * @param value a String value
     * @return this.
     * @see JSONArray.size
     * @see JSONArray.remove
     */
    fun append(value: String?): JSONArray {
        this.append(value as Any?)
        return this
    }

    /**
     * Append an int value. This increases the array's length by one.
     *
     * @param value an int value
     * @return this.
     */
    fun append(value: Int): JSONArray {
        this.append(Integer.valueOf(value))
        return this
    }

    /**
     * Append an long value. This increases the array's length by one.
     *
     * @nowebref
     * @param value A long value.
     * @return this.
     */
    fun append(value: Long): JSONArray {
        this.append(java.lang.Long.valueOf(value))
        return this
    }

    /**
     * Append a float value. This increases the array's length by one.
     * This will store the value as a double, since there are no floats in JSON.
     *
     * @param value a float value
     * @throws RuntimeException if the value is not finite.
     * @return this.
     */
    fun append(value: Float): JSONArray {
        return append(value.toDouble())
    }

    /**
     * Append a double value. This increases the array's length by one.
     *
     * @nowebref
     * @param value A double value.
     * @throws RuntimeException if the value is not finite.
     * @return this.
     */
    fun append(value: Double): JSONArray {
        testValidity(value)
        this.append(value)
        return this
    }

    /**
     * Append a boolean value. This increases the array's length by one.
     *
     * @param value a boolean value
     * @return this.
     */
    fun append(value: Boolean): JSONArray {
        this.append(if (value) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE)
        return this
    }

    //  /**
    //   * Put a value in the JSONArray, where the value will be a
    //   * JSONArray which is produced from a Collection.
    //   * @param value A Collection value.
    //   * @return      this.
    //   */
    //  public JSONArray append(Collection value) {
    //    this.append(new JSONArray(value));
    //    return this;
    //  }
    //  /**
    //   * Put a value in the JSONArray, where the value will be a
    //   * JSONObject which is produced from a Map.
    //   * @param value A Map value.
    //   * @return      this.
    //   */
    //  public JSONArray append(Map value) {
    //    this.append(new JSONObject(value));
    //    return this;
    //  }

    /**
     * @param value a JSONArray value
     */
    fun append(value: JSONArray?): JSONArray {
        myArrayList.add(value)
        return this
    }

    /**
     * @param value a JSONObject value
     */
    fun append(value: JSONObject?): JSONArray {
        myArrayList.add(value)
        return this
    }

    /**
     * Append an object value. This increases the array's length by one.
     * @param value An object value.  The value should be a
     * Boolean, Double, Integer, JSONArray, JSONObject, Long, or String, or the
     * JSONObject.NULL object.
     * @return this.
     */
    protected fun append(value: Any?): JSONArray {
        myArrayList.add(value)
        return this
    }

    //  /**
    //   * Put a value in the JSONArray, where the value will be a
    //   * JSONArray which is produced from a Collection.
    //   * @param index The subscript.
    //   * @param value A Collection value.
    //   * @return      this.
    //   * @throws RuntimeException If the index is negative or if the value is
    //   * not finite.
    //   */
    //  public JSONArray set(int index, Collection value) {
    //    this.set(index, new JSONArray(value));
    //    return this;
    //  }

    /**
     * Put or replace a String value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad
     * it out.
     *
     * @webref jsonarray:method
     * @brief Put a String value in the JSONArray
     * @param index an index value
     * @param value the value to assign
     * @return this.
     * @throws RuntimeException If the index is negative.
     * @see JSONArray.setInt
     * @see JSONArray.setFloat
     * @see JSONArray.setBoolean
     */
    fun setString(index: Int, value: String?): JSONArray? {
        this[index] = value
        return this
    }

    /**
     * Put or replace an int value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad
     * it out.
     *
     * @webref jsonarray:method
     * @brief Put an int value in the JSONArray
     * @param index an index value
     * @param value the value to assign
     * @return this.
     * @throws RuntimeException If the index is negative.
     * @see JSONArray.setFloat
     * @see JSONArray.setString
     * @see JSONArray.setBoolean
     */
    fun setInt(index: Int, value: Int): JSONArray? {
        this[index] = Integer.valueOf(value)
        return this
    }

    /**
     * Put or replace a long value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad
     * it out.
     * @param index The subscript.
     * @param value A long value.
     * @return this.
     * @throws RuntimeException If the index is negative.
     */
    fun setLong(index: Int, value: Long): JSONArray {
        return set(index, java.lang.Long.valueOf(value))
    }

    /**
     * Put or replace a float value. If the index is greater than the length
     * of the JSONArray, then null elements will be added as necessary to pad
     * it out. There are no 'double' values in JSON, so this is passed to
     * setDouble(value).
     *
     * @webref jsonarray:method
     * @brief Put a float value in the JSONArray
     * @param index an index value
     * @param value the value to assign
     * @return this.
     * @throws RuntimeException If the index is negative or if the value is
     * not finite.
     * @see JSONArray.setInt
     * @see JSONArray.setString
     * @see JSONArray.setBoolean
     */
    fun setFloat(index: Int, value: Float): JSONArray {
        return setDouble(index, value.toDouble())
    }

    /**
     * Put or replace a double value. If the index is greater than the length of
     * the JSONArray, then null elements will be added as necessary to pad
     * it out.
     * @param index The subscript.
     * @param value A double value.
     * @return this.
     * @throws RuntimeException If the index is negative or if the value is
     * not finite.
     */
    fun setDouble(index: Int, value: Double): JSONArray {
        return set(index, java.lang.Double.valueOf(value))
    }

    /**
     * Put or replace a boolean value in the JSONArray. If the index is greater
     * than the length of the JSONArray, then null elements will be added as
     * necessary to pad it out.
     *
     * @webref jsonarray:method
     * @brief Put a boolean value in the JSONArray
     * @param index an index value
     * @param value the value to assign
     * @return this.
     * @throws RuntimeException If the index is negative.
     * @see JSONArray.setInt
     * @see JSONArray.setFloat
     * @see JSONArray.setString
     */
    fun setBoolean(index: Int, value: Boolean): JSONArray {
        return set(index, if (value) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE)
    }
    //  /**
    //   * Put a value in the JSONArray, where the value will be a
    //   * JSONObject that is produced from a Map.
    //   * @param index The subscript.
    //   * @param value The Map value.
    //   * @return      this.
    //   * @throws RuntimeException If the index is negative or if the the value is
    //   *  an invalid number.
    //   */
    //  public JSONArray set(int index, Map value) {
    //    this.set(index, new JSONObject(value));
    //    return this;
    //  }
    /**
     * @webref jsonarray:method
     * @brief Sets the JSONArray value associated with an index value
     * @param index the index value to target
     * @param value the value to assign
     * @see JSONArray.setJSONObject
     * @see JSONArray.getJSONObject
     * @see JSONArray.getJSONArray
     */
    fun setJSONArray(index: Int, value: JSONArray): JSONArray {
        set(index, value)
        return this
    }

    /**
     * @webref jsonarray:method
     * @brief Sets the JSONObject value associated with an index value
     * @param index the index value to target
     * @param value the value to assign
     * @see JSONArray.setJSONArray
     * @see JSONArray.getJSONObject
     * @see JSONArray.getJSONArray
     */
    fun setJSONObject(index: Int, value: JSONObject): JSONArray {
        set(index, value)
        return this
    }

    /**
     * Put or replace an object value in the JSONArray. If the index is greater
     * than the length of the JSONArray, then null elements will be added as
     * necessary to pad it out.
     * @param index The subscript.
     * @param value The value to put into the array. The value should be a
     * Boolean, Double, Integer, JSONArray, JSONObject, Long, or String, or the
     * JSONObject.NULL object.
     * @return this.
     * @throws RuntimeException If the index is negative or if the the value is
     * an invalid number.
     */
    private operator fun set(index: Int, value: Any?): JSONArray {
        testValidity(value)
        if (index < 0) {
            throw RuntimeException("JSONArray[$index] not found.")
        }
        if (index < size()) {
            myArrayList[index] = value
        } else {
            while (index != size()) {
                this.append(JSONObject.NULL)
            }
            this.append(value)
        }
        return this
    }

    /**
     * Get the number of elements in the JSONArray, included nulls.
     *
     * @webref jsonarray:method
     * @brief Gets the number of elements in the JSONArray
     * @return The length (or size).
     * @see JSONArray.append
     * @see JSONArray.remove
     */
    fun size(): Int {
        return myArrayList.size
    }

    /**
     * Determine if the value is null.
     * @webref
     * @param index must be between 0 and length() - 1
     * @return true if the value at the index is null, or if there is no value.
     */
    fun isNull(index: Int): Boolean {
        return JSONObject.NULL == opt(index)
    }

    /**
     * Remove an index and close the hole.
     *
     * @webref jsonarray:method
     * @brief Removes an element
     * @param index the index value of the element to be removed
     * @return The value that was associated with the index, or null if there was no value.
     * @see JSONArray.size
     * @see JSONArray.append
     */
    fun remove(index: Int): Any? {
        val o = opt(index)
        myArrayList.removeAt(index)
        return o
    }

    //  /**
    //   * Produce a JSONObject by combining a JSONArray of names with the values
    //   * of this JSONArray.
    //   * @param names A JSONArray containing a list of key strings. These will be
    //   * paired with the values.
    //   * @return A JSONObject, or null if there are no names or if this JSONArray
    //   * has no values.
    //   * @throws JSONException If any of the names are null.
    //   */
    //  public JSON toJSONObject(JSONArray names) {
    //    if (names == null || names.length() == 0 || this.length() == 0) {
    //      return null;
    //    }
    //    JSON jo = new JSON();
    //    for (int i = 0; i < names.length(); i += 1) {
    //      jo.put(names.getString(i), this.opt(i));
    //    }
    //    return jo;
    //  }
    //  protected boolean save(OutputStream output) {
    //    return write(PApplet.createWriter(output), null);
    //  }

    fun save(file: File?, options: String?): Boolean {
        val writer = PApplet.createWriter(file)
        val success = write(writer, options)
        writer.close()
        return success
    }

    @JvmOverloads
    fun write(output: PrintWriter, options: String? = null): Boolean {
        var indentFactor = 2
        if (options != null) {
            val opts = PApplet.split(options, ',')
            for (opt in opts!!) {
                if (opt == "compact") {
                    indentFactor = -1
                } else if (opt!!.startsWith("indent=")) {
                    indentFactor = PApplet.parseInt(opt!!.substring(7), -2)
                    require(indentFactor != -2) { "Could not read a number from $opt" }
                } else {
                    System.err.println("Ignoring $opt")
                }
            }
        }
        output.print(format(indentFactor))
        output.flush()
        return true
    }

    /**
     * Return the JSON data formatted with two spaces for indents.
     * Chosen to do this since it's the most common case (e.g. with println()).
     * Same as format(2). Use the format() function for more options.
     */
    override fun toString(): String {
        return try {
            format(2)
        } catch (e: Exception) {
             null as String
        }
    }

    /**
     * Make a pretty-printed JSON text of this JSONArray.
     * Warning: This method assumes that the data structure is acyclical.
     * @param indentFactor The number of spaces to add to each level of
     * indentation. Use -1 to specify no indentation and no newlines.
     * @return a printable, displayable, transmittable
     * representation of the object, beginning
     * with `[`&nbsp;<small>(left bracket)</small> and ending
     * with `]`&nbsp;<small>(right bracket)</small>.
     */
    fun format(indentFactor: Int): String {
        val sw = StringWriter()
        synchronized(sw.buffer) { return writeInternal(sw, indentFactor, 0).toString() }
    }

    //  /**
    //   * Write the contents of the JSONArray as JSON text to a writer. For
    //   * compactness, no whitespace is added.
    //   * <p>
    //   * Warning: This method assumes that the data structure is acyclic.
    //   *
    //   * @return The writer.
    //   */
    //  protected Writer write(Writer writer) {
    //    return this.write(writer, -1, 0);
    //  }

    /**
     * Write the contents of the JSONArray as JSON text to a writer.
     *
     *
     * Warning: This method assumes that the data structure is acyclic.
     *
     * @param indentFactor
     * The number of spaces to add to each level of indentation.
     * Use -1 to specify no indentation and no newlines.
     * @param indent
     * The indention of the top level.
     * @return The writer.
     * @throws RuntimeException
     */
     fun writeInternal(writer: Writer?, indentFactor: Int, indent: Int): Writer? {
        return try {
            var commanate = false
            val length = size()
            writer!!.write('['.toInt())

            // Use -1 to signify 'no indent'
            val thisFactor = if (indentFactor == -1) 0 else indentFactor
            if (length == 1) {
                writeValue(writer, myArrayList[0],
                        indentFactor, indent)
                //                              thisFactor, indent);
            } else if (length != 0) {
                val newIndent = indent + thisFactor
                var i = 0
                while (i < length) {
                    if (commanate) {
                        writer!!.write(','.toInt())
                    }
                    if (indentFactor != -1) {
                        writer.write('\n'.toInt())
                    }
                    indent(writer, newIndent)
                    //          JSONObject.writeValue(writer, this.myArrayList.get(i),
//                                thisFactor, newIndent);
                    writeValue(writer, myArrayList[i],
                            indentFactor, newIndent)
                    commanate = true
                    i += 1
                }
                if (indentFactor != -1) {
                    writer!!.write('\n'.toInt())
                }
                indent(writer, indent)
            }
            writer!!.write(']'.toInt())
            writer
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Make a string from the contents of this JSONArray. The
     * `separator` string is inserted between each element.
     * Warning: This method assumes that the data structure is acyclic.
     * @param separator A string that will be inserted between the elements.
     * @return a string.
     * @throws RuntimeException If the array contains an invalid number.
     */
    fun join(separator: String?): String {
        val len = size()
        val sb = StringBuilder()
        var i = 0
        while (i < len) {
            if (i > 0) {
                sb.append(separator)
            }
            sb.append(valueToString(myArrayList[i]))
            i += 1
        }
        return sb.toString()
    }

    companion object {
        /**
         * Construct a JSONArray from a source JSON text.
         * @param source     A string that begins with
         * `[`&nbsp;<small>(left bracket)</small>
         * and ends with `]`&nbsp;<small>(right bracket)</small>.
         * @return `null` if there is a syntax error.
         */
        @JvmStatic
        fun parse(source: String?): JSONArray? {
            return try {
                JSONArray(JSONTokener(source))
            } catch (e: Exception) {
                null
            }
        }
    }
}