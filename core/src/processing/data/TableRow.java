/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-16 The Processing Foundation

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

package processing.data;

import java.io.PrintWriter;

/**
 * @webref data:composite
 * @see Table
 * @see Table#addRow()
 * @see Table#removeRow(int)
 * @see Table#clearRows()
 * @see Table#getRow(int)
 * @see Table#rows()
 */
public interface TableRow {

  /**
   * @webref tablerow:method
   * @brief Get an String value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getInt(int)
   * @see TableRow#getFloat(int)
   */
  public String getString(int column);

  /**
   * @param columnName title of the column to reference
   */
  public String getString(String columnName);

  /**
   * @webref tablerow:method
   * @brief Get an integer value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getFloat(int)
   * @see TableRow#getString(int)
   */
  public int getInt(int column);

  /**
   * @param columnName title of the column to reference
   */
  public int getInt(String columnName);

  /**
   * @brief Get a long value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getFloat(int)
   * @see TableRow#getString(int)
   */

  public long getLong(int column);

  /**
   * @param columnName title of the column to reference
   */
  public long getLong(String columnName);

  /**
   * @webref tablerow:method
   * @brief Get a float value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getInt(int)
   * @see TableRow#getString(int)
   */
  public float getFloat(int column);

  /**
   * @param columnName title of the column to reference
   */
  public float getFloat(String columnName);

  /**
   * @brief Get a double value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getInt(int)
   * @see TableRow#getString(int)
   */
  public double getDouble(int column);

  /**
   * @param columnName title of the column to reference
   */
  public double getDouble(String columnName);

  /**
   * @webref tablerow:method
   * @brief Store a String value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setInt(int, int)
   * @see TableRow#setFloat(int, float)
   */
  public void setString(int column, String value);
  /**
   * @param columnName title of the target column
   */
  public void setString(String columnName, String value);

  /**
   * @webref tablerow:method
   * @brief Store an integer value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setFloat(int, float)
   * @see TableRow#setString(int, String)
   */
  public void setInt(int column, int value);

  /**
   * @param columnName title of the target column
   */
  public void setInt(String columnName, int value);

  /**
   * @brief Store a long value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setFloat(int, float)
   * @see TableRow#setString(int, String)
   */
  public void setLong(int column, long value);

  /**
   * @param columnName title of the target column
   */
  public void setLong(String columnName, long value);

  /**
   * @webref tablerow:method
   * @brief Store a float value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setInt(int, int)
   * @see TableRow#setString(int, String)
   */
  public void setFloat(int column, float value);

  /**
   * @param columnName title of the target column
   */
  public void setFloat(String columnName, float value);

  /**
   * @brief Store a double value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setFloat(int, float)
   * @see TableRow#setString(int, String)
   */
  public void setDouble(int column, double value);

  /**
   * @param columnName title of the target column
   */
  public void setDouble(String columnName, double value);

  /**
   * @webref tablerow:method
   * @brief Get the column count.
   * @return count of all columns
   */
  public int getColumnCount();

  /**
   * @brief Get the column type.
   * @param columnName title of the target column
   * @return type of the column
   */
  public int getColumnType(String columnName);

  /**
   * @param column ID number of the target column
   */
  public int getColumnType(int column);

  /**
   * @brief Get the all column types
   * @return list of all column types
   */
  public int[] getColumnTypes();

  /**
   * @webref tablerow:method
   * @brief Get the column title.
   * @param column ID number of the target column
   * @return title of the column
   */
  public String getColumnTitle(int column);

  /**
   * @brief Get the all column titles
   * @return list of all column titles
   */
  public String[] getColumnTitles();

  public void write(PrintWriter writer);
  public void print();
}
