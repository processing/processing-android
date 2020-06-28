package processing.data

import java.io.PrintWriter

/**
 * @webref data:composite
 * @see Table
 *
 * @see Table.addRow
 * @see Table.removeRow
 * @see Table.clearRows
 * @see Table.getRow
 * @see Table.rows
 */
interface TableRow {
    /**
     * @webref tablerow:method
     * @brief Get an String value from the specified column
     * @param column ID number of the column to reference
     * @see TableRow.getInt
     * @see TableRow.getFloat
     */
    fun getString(column: Int): String?

    /**
     * @param columnName title of the column to reference
     */
    fun getString(columnName: String?): String?

    /**
     * @webref tablerow:method
     * @brief Get an integer value from the specified column
     * @param column ID number of the column to reference
     * @see TableRow.getFloat
     * @see TableRow.getString
     */
    fun getInt(column: Int): Int

    /**
     * @param columnName title of the column to reference
     */
    fun getInt(columnName: String?): Int

    /**
     * @brief Get a long value from the specified column
     * @param column ID number of the column to reference
     * @see TableRow.getFloat
     * @see TableRow.getString
     */
    fun getLong(column: Int): Long

    /**
     * @param columnName title of the column to reference
     */
    fun getLong(columnName: String?): Long

    /**
     * @webref tablerow:method
     * @brief Get a float value from the specified column
     * @param column ID number of the column to reference
     * @see TableRow.getInt
     * @see TableRow.getString
     */
    fun getFloat(column: Int): Float

    /**
     * @param columnName title of the column to reference
     */
    fun getFloat(columnName: String?): Float

    /**
     * @brief Get a double value from the specified column
     * @param column ID number of the column to reference
     * @see TableRow.getInt
     * @see TableRow.getString
     */
    fun getDouble(column: Int): Double

    /**
     * @param columnName title of the column to reference
     */
    fun getDouble(columnName: String?): Double

    /**
     * @webref tablerow:method
     * @brief Store a String value in the specified column
     * @param column ID number of the target column
     * @param value value to assign
     * @see TableRow.setInt
     * @see TableRow.setFloat
     */
    fun setString(column: Int, value: String?)

    /**
     * @param columnName title of the target column
     */
    fun setString(columnName: String?, value: String?)

    /**
     * @webref tablerow:method
     * @brief Store an integer value in the specified column
     * @param column ID number of the target column
     * @param value value to assign
     * @see TableRow.setFloat
     * @see TableRow.setString
     */
    fun setInt(column: Int, value: Int)

    /**
     * @param columnName title of the target column
     */
    fun setInt(columnName: String?, value: Int)

    /**
     * @brief Store a long value in the specified column
     * @param column ID number of the target column
     * @param value value to assign
     * @see TableRow.setFloat
     * @see TableRow.setString
     */
    fun setLong(column: Int, value: Long)

    /**
     * @param columnName title of the target column
     */
    fun setLong(columnName: String?, value: Long)

    /**
     * @webref tablerow:method
     * @brief Store a float value in the specified column
     * @param column ID number of the target column
     * @param value value to assign
     * @see TableRow.setInt
     * @see TableRow.setString
     */
    fun setFloat(column: Int, value: Float)

    /**
     * @param columnName title of the target column
     */
    fun setFloat(columnName: String?, value: Float)

    /**
     * @brief Store a double value in the specified column
     * @param column ID number of the target column
     * @param value value to assign
     * @see TableRow.setFloat
     * @see TableRow.setString
     */
    fun setDouble(column: Int, value: Double)

    /**
     * @param columnName title of the target column
     */
    fun setDouble(columnName: String?, value: Double)

    /**
     * @webref tablerow:method
     * @brief Get the column count.
     * @return count of all columns
     */
    fun getColumnCount(): Int

    /**
     * @brief Get the column type.
     * @param columnName title of the target column
     * @return type of the column
     */
    fun getColumnType(columnName: String?): Int

    /**
     * @param column ID number of the target column
     */
    fun getColumnType(column: Int): Int

    /**
     * @brief Get the all column types
     * @return list of all column types
     */
    fun getColumnTypes(): IntArray?

    /**
     * @webref tablerow:method
     * @brief Get the column title.
     * @param column ID number of the target column
     * @return title of the column
     */
    fun getColumnTitle(column: Int): String?

    /**
     * @brief Get the all column titles
     * @return list of all column titles
     */
    fun getColumnTitles(): Array<String?>?

    fun write(writer: PrintWriter?)

    fun print()
}