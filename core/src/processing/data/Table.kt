/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2011-13 Ben Fry and Casey Reas
  Copyright (c) 2006-11 Ben Fry

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

package processing.data

import org.xml.sax.SAXException

import processing.core.PApplet
import processing.core.PConstants

import processing.data.IntList.Companion.fromRange

import java.io.*
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types

import java.util.*
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.ParserConfigurationException

/**
 *
 * Generic class for handling tabular data, typically from a CSV, TSV, or
 * other sort of spreadsheet file.
 *
 * CSV files are
 * [comma separated values](http://en.wikipedia.org/wiki/Comma-separated_values),
 * often with the data in quotes. TSV files use tabs as separators, and usually
 * don't bother with the quotes.
 *
 * File names should end with .csv if they're comma separated.
 *
 * A rough "spec" for CSV can be found [here](http://tools.ietf.org/html/rfc4180).
 *
 * @webref data:composite
 * @see PApplet.loadTable
 * @see PApplet.saveTable
 * @see TableRow
 */
open class Table {
    private var rowCount = 0
    protected var allocCount = 0

    //  protected boolean skipEmptyRows = true;
    //  protected boolean skipCommentLines = true;
    //  protected String extension = null;
    //  protected boolean commaSeparatedValues = false;
    //  protected boolean awfulCSV = false;

    private var missingString: String? = null
    private var missingInt = 0
    private var missingLong: Long = 0
    private var missingFloat = Float.NaN
    private var missingDouble = Double.NaN
    private var missingCategory = -1

    private var columnTitles: Array<String?>? = null
    lateinit var columnCategories: Array<HashMapBlows?>
    var columnIndices: HashMap<String?, Int>? = null
    private lateinit var columns : Array<Any?> // [column]
    private lateinit var columnTypes: IntArray
    private var rowIterator: RowIterator? = null

    // 0 for doubling each time, otherwise the number of rows to increment on
    // each expansion.
    protected var expandIncrement = 0

    /**
     * Creates a new, empty table. Use addRow() to add additional rows.
     */
    constructor() {
        init()
    }
    /**
     * version that uses a File object; future releases (or data types)
     * may include additional optimizations here
     *
     * @nowebref
     */
    /**
     * @nowebref
     */
    @JvmOverloads
    constructor(file: File, options: String? = null) {
        // uses createInput() to handle .gz (and eventually .bz2) files
        init()
        parse(PApplet.createInput(file),
                extensionOptions(true, file.name, options))
    }
    /**
     * Read the table from a stream. Possible options include:
     *
     *  * csv - parse the table as comma-separated values
     *  * tsv - parse the table as tab-separated values
     *  * newlines - this CSV file contains newlines inside individual cells
     *  * header - this table has a header (title) row
     *
     *
     * @nowebref
     * @param input
     * @param options
     * @throws IOException
     */
    /**
     * @nowebref
     */
    @JvmOverloads
    constructor(input: InputStream?, options: String? = null) {
        init()
        parse(input, options)
    }

    constructor(rows: Iterable<TableRow?>) {
        init()
        var row = 0
        var alloc = 10
        for (incoming in rows) {
            if (row == 0) {
                setColumnTypes(incoming!!.getColumnTypes())
                setColumnTitles(incoming!!.getColumnTitles())
                // Do this after setting types, otherwise it'll attempt to parse the
                // allocated but empty rows, and drive CATEGORY columns nutso.
                setRowCount(alloc)
                // sometimes more columns than titles (and types?)
                setColumnCount(incoming!!.getColumnCount())
            } else if (row == alloc) {
                // Far more efficient than re-allocating all columns and doing a copy
                alloc *= 2
                setRowCount(alloc)
            }

            //addRow(row);
//      try {
            setRow(row++, incoming)
            //      } catch (ArrayIndexOutOfBoundsException aioobe) {
//        for (int i = 0; i < incoming.getColumnCount(); i++) {
//          System.out.format("[%d] %s%n", i, incoming.getString(i));
//        }
//        throw aioobe;
//      }
        }
        // Shrink the table to only the rows that were used
        if (row != alloc) {
            setRowCount(row)
        }
    }

    /**
     * @nowebref
     */
    constructor(rs: ResultSet) {
        init()
        try {
            val rsmd = rs.metaData
            val columnCount = rsmd.columnCount
            setColumnCount(columnCount)
            for (col in 0 until columnCount) {
                setColumnTitle(col, rsmd.getColumnName(col + 1))
                val type = rsmd.getColumnType(col + 1)
                when (type) {
                    Types.INTEGER, Types.TINYINT, Types.SMALLINT -> setColumnType(col, INT)
                    Types.BIGINT -> setColumnType(col, LONG)
                    Types.FLOAT -> setColumnType(col, FLOAT)
                    Types.DECIMAL, Types.DOUBLE, Types.REAL -> setColumnType(col, DOUBLE)
                }
            }
            var row = 0
            while (rs.next()) {
                for (col in 0 until columnCount) {
                    when (columnTypes[col]) {
                        STRING -> setString(row, col, rs.getString(col + 1))
                        INT -> setInt(row, col, rs.getInt(col + 1))
                        LONG -> setLong(row, col, rs.getLong(col + 1))
                        FLOAT -> setFloat(row, col, rs.getFloat(col + 1))
                        DOUBLE -> setDouble(row, col, rs.getDouble(col + 1))
                        else -> throw IllegalArgumentException("column type " + columnTypes[col] + " not supported.")
                    }
                }
                row++
                //        String[] row = new String[columnCount];
//        for (int col = 0; col < columnCount; col++) {
//          row[col] = rs.get(col + 1);
//        }
//        addRow(row);
            }
        } catch (s: SQLException) {
            throw RuntimeException(s)
        }
    }

    @Throws(IOException::class)
    fun typedParse(input: InputStream?, options: String?): Table {
        val table = Table()
        table.setColumnTypes(this)
        table.parse(input, options)
        return table
    }

    protected fun init() {
        columns = arrayOfNulls(0)
        columnTypes = IntArray(0)
        columnCategories = arrayOfNulls(0)
    }

    @Throws(IOException::class)
    protected fun parse(input: InputStream?, options: String?) {
//    boolean awfulCSV = false;
        var header = false
        var extension: String? = null
        var binary = false
        var encoding: String = "UTF-8"
        var worksheet: String? = null
        val sheetParam = "worksheet="
        var opts: Array<String>? = null
        if (options != null) {
            opts = PApplet.trim(PApplet.split(options, ','))
            for (opt in opts) {
                if (opt == "tsv") {
                    extension = "tsv"
                } else if (opt == "csv") {
                    extension = "csv"
                } else if (opt == "ods") {
                    extension = "ods"
                } else // ignore option, this is only handled by PApplet
                    require(opt != "newlines") {
                        //awfulCSV = true;
                        //extension = "csv";
                        "The 'newlines' option is no longer necessary."
                    }
                if (opt == "bin") {
                    binary = true
                    extension = "bin"
                } else if (opt == "header") {
                    header = true
                } else if (opt.startsWith(sheetParam)) {
                    worksheet = opt.substring(sheetParam.length)
                } else if (opt.startsWith("dictionary=")) {
                    // ignore option, this is only handled by PApplet
                } else if (opt.startsWith("encoding=")) {
                    encoding = opt.substring(9)
                } else {
                    throw IllegalArgumentException("'$opt' is not a valid option for loading a Table")
                }
            }
        }
        if (extension == null) {
            throw IllegalArgumentException("No extension specified for this Table")
        }
        if (binary) {
            loadBinary(input)
        } else if (extension == "ods") {
            odsParse(input, worksheet, header)
        } else {
            val isr = InputStreamReader(input, encoding)
            val reader = BufferedReader(isr)

            // strip out the Unicode BOM, if present
            reader.mark(1)
            val c = reader.read()
            // if not the BOM, back up to the beginning again
            if (c != '\uFEFF'.toInt()) {
                reader.reset()
            }

            /*
       if (awfulCSV) {
        parseAwfulCSV(reader, header);
      } else if ("tsv".equals(extension)) {
        parseBasic(reader, header, true);
      } else if ("csv".equals(extension)) {
        parseBasic(reader, header, false);
      }
      */parseBasic(reader, header, "tsv" == extension)
        }
    }

    @Throws(IOException::class)
    protected fun parseBasic(reader: BufferedReader,
                             header: Boolean, tsv: Boolean) {
        var header = header
        var line: String? = null
        var mrow: Int = 0
        if (rowCount == 0) {
            setRowCount(10)
        }
        //int prev = 0;  //-1;
        try {
            while ((reader.readLine().also { line = it }) != null) {
                if (mrow == getRowCount()) {
                    setRowCount(mrow shl 1)
                }
                if (mrow == 0 && header) {
                    setColumnTitles(if (tsv) PApplet.split(line, '\t') else splitLineCSV(line, reader))
                    header = false
                } else {
                    val splstr = if (tsv) PApplet.split(line, '\t')  else splitLineCSV(line, reader)
                    setRow(mrow, splstr as Array<Any?>)
                    mrow++
                }
                if (mrow % 10000 == 0) {
                    /*
        // this is problematic unless we're going to calculate rowCount first
        if (row < rowCount) {
          int pct = (100 * row) / rowCount;
          if (pct != prev) {  // also prevents "0%" from showing up
            System.out.println(pct + "%");
            prev = pct;
          }
        }
         */
                    try {
                        // Sleep this thread so that the GC can catch up
                        Thread.sleep(10)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Error reading table on line $mrow", e)
        }
        // shorten or lengthen based on what's left
        if (mrow != getRowCount()) {
            setRowCount(mrow)
        }
    }

    //  public void convertTSV(BufferedReader reader, File outputFile) throws IOException {
    //    convertBasic(reader, true, outputFile);
    //  }
    /*
  protected void parseAwfulCSV(BufferedReader reader,
                               boolean header) throws IOException {
    char[] c = new char[100];
    int count = 0;
    boolean insideQuote = false;

    int alloc = 100;
    setRowCount(100);

    int row = 0;
    int col = 0;
    int ch;
    while ((ch = reader.read()) != -1) {
      if (insideQuote) {
        if (ch == '\"') {
          // this is either the end of a quoted entry, or a quote character
          reader.mark(1);
          if (reader.read() == '\"') {
            // it's "", which means a quote character
            if (count == c.length) {
              c = PApplet.expand(c);
            }
            c[count++] = '\"';
          } else {
            // nope, just the end of a quoted csv entry
            reader.reset();
            insideQuote = false;
            // TODO nothing here that prevents bad csv data from showing up
            // after the quote and before the comma...
//            set(row, col, new String(c, 0, count));
//            count = 0;
//            col++;
//            insideQuote = false;
          }
        } else {  // inside a quote, but the character isn't a quote
          if (count == c.length) {
            c = PApplet.expand(c);
          }
          c[count++] = (char) ch;
        }
      } else {  // not inside a quote
        if (ch == '\"') {
          insideQuote = true;

        } else if (ch == '\r' || ch == '\n') {
          if (ch == '\r') {
            // check to see if next is a '\n'
            reader.mark(1);
            if (reader.read() != '\n') {
              reader.reset();
            }
          }
          setString(row, col, new String(c, 0, count));
          count = 0;
          row++;
          if (row == 1 && header) {
            // Use internal row removal (efficient because only one row).
            removeTitleRow();
            // Un-set the header variable so that next time around, we don't
            // just get stuck into a loop, removing the 0th row repeatedly.
            header = false;
            // Reset the number of rows (removeTitleRow() won't reset our local 'row' counter)
            row = 0;
          }
//          if (row % 1000 == 0) {
//            PApplet.println(PApplet.nfc(row));
//          }
          if (row == alloc) {
            alloc *= 2;
            setRowCount(alloc);
          }
          col = 0;

        } else if (ch == ',') {
          setString(row, col, new String(c, 0, count));
          count = 0;
          // starting a new column, make sure we have room
          col++;
          ensureColumn(col);

        } else {  // just a regular character, add it
          if (count == c.length) {
            c = PApplet.expand(c);
          }
          c[count++] = (char) ch;
        }
      }
    }
    // catch any leftovers
    if (count > 0) {
      setString(row, col, new String(c, 0, count));
    }
    row++;  // set row to row count (the current row index + 1)
    if (alloc != row) {
      setRowCount(row);  // shrink to the actual size
    }
  }
  */

    class CommaSeparatedLine {
        lateinit var c: CharArray
        lateinit var pieces: Array<String?>
        var pieceCount = 0

        //    int offset;
         var start  = 0
        //, stop; = 0

        @Throws(IOException::class)
        fun handle(line: String?, reader: BufferedReader): Array<String?> {
//      PApplet.println("handle() called for: " + line);
            start = 0
            pieceCount = 0
            c = line!!.toCharArray()

            // get tally of number of columns and allocate the array
            var cols = 1 // the first comma indicates the second column
            var quote = false
            for (i in c.indices) {
                if (!quote && (c.get(i) == ',')) {
                    cols++
                } else if (c.get(i) == '\"') {
                    // double double quotes (escaped quotes like "") will simply toggle
                    // this back and forth, so it should remain accurate
                    quote = !quote
                }
            }
            pieces = arrayOfNulls(cols)

//      while (offset < c.length) {
//        start = offset;
            while (start < c.size) {
                val enough = ingest()
                while (!enough) {
                    // found a newline inside the quote, grab another line
                    val nextLine = reader.readLine()
                            ?: //            System.err.println(line);
                            throw IOException("Found a quoted line that wasn't terminated properly.")
                    //          System.out.println("extending to " + nextLine);
                    // for simplicity, not bothering to skip what's already been read
                    // from c (and reset the offset to 0), opting to make a bigger array
                    // with both lines.
                    val temp = CharArray(c.size + 1 + nextLine.length)
                    PApplet.arrayCopy(c, temp, c.size)
                    // NOTE: we're converting to \n here, which isn't perfect
                    temp[c.size] = '\n'
                    nextLine.toCharArray(temp, c.size + 1, 0, nextLine.length)
                    //          c = temp;
                    return handle(String(temp), reader)
                    //System.out.println("  full line is now " + new String(c));
                    //stop = nextComma(c, offset);
                    //System.out.println("stop is now " + stop);
                    //enough = ingest();
                }
            }

            // Make any remaining entries blanks instead of nulls. Empty columns from
            // CSV are always "" not null, so this handles successive commas in a line
            for (i in pieceCount until pieces.size) {
                pieces[i] = ""
            }
            //      PApplet.printArray(pieces);
            return pieces
        }

        protected fun addPiece(start: Int, stop: Int, quotes: Boolean) {
            if (quotes) {
                var dest = start
                var i = start
                while (i < stop) {
                    if (c[i] == '\"') {
                        ++i // step over the quote
                    }
                    if (i != dest) {
                        c[dest] = c[i]
                    }
                    dest++
                    i++
                }
                pieces[pieceCount++] = String(c, start, dest - start)
            } else {
                pieces[pieceCount++] = String(c, start, stop - start)
            }
        }

        /**
         * Returns the next comma (not inside a quote) in the specified array.
         * @param c array to search
         * @param index offset at which to start looking
         * @return index of the comma, or -1 if line ended inside an unclosed quote
         */
        protected fun ingest(): Boolean {
            var hasEscapedQuotes = false
            // not possible
//      if (index == c.length) {  // we're already at the end
//        return c.length;
//      }
            val quoted = c[start] == '\"'
            if (quoted) {
                start++ // step over the quote
            }
            var i = start
            while (i < c.size) {
//        PApplet.println(c[i] + " i=" + i);
                if (c[i] == '\"') {
                    // if this fella started with a quote
                    if (quoted) {
                        if (i == c.size - 1) {
                            // closing quote for field; last field on the line
                            addPiece(start, i, hasEscapedQuotes)
                            start = c.size
                            return true
                        } else if (c[i + 1] == '\"') {
                            // an escaped quote inside a quoted field, step over it
                            hasEscapedQuotes = true
                            i = i + 2
                        } else if (c[i + 1] == ',') {
                            // that was our closing quote, get outta here
                            addPiece(start, i, hasEscapedQuotes)
                            start = i + 2
                            return true
                        } else {
                            // This is a lone-wolf quote, occasionally seen in exports.
                            // It's a single quote in the middle of some other text,
                            // and not escaped properly. Pray for the best!
                            i++
                        }
                    } else {  // not a quoted line
                        if (i == c.size - 1) {
                            // we're at the end of the line, can't have an unescaped quote
                            throw RuntimeException("Unterminated quote at end of line")
                        } else if (c[i + 1] == '\"') {
                            // step over this crummy quote escape
                            hasEscapedQuotes = true
                            i = i + 2
                        } else {
                            throw RuntimeException("Unterminated quoted field mid-line")
                        }
                    }
                } else if (!quoted && c[i] == ',') {
                    addPiece(start, i, hasEscapedQuotes)
                    start = i + 1
                    return true
                } else if (!quoted && i == c.size - 1) {
                    addPiece(start, c.size, hasEscapedQuotes)
                    start = c.size
                    return true
                } else {  // nothing all that interesting
                    i++
                }
            }
            //      if (!quote && (c[i] == ',')) {
//        // found a comma, return this location
//        return i;
//      } else if (c[i] == '\"') {
//        // if it's a quote, then either the next char is another quote,
//        // or if this is a quoted entry, it better be a comma
//        quote = !quote;
//      }
//    }

            // if still inside a quote, indicate that another line should be read
            if (quoted) {
                return false
            }
            throw RuntimeException("not sure how...")
        }
    }

    var csl: CommaSeparatedLine? = null

    /**
     * Parse a line of text as comma-separated values, returning each value as
     * one entry in an array of String objects. Remove quotes from entries that
     * begin and end with them, and convert 'escaped' quotes to actual quotes.
     * @param line line of text to be parsed
     * @return an array of the individual values formerly separated by commas
     */
    @Throws(IOException::class)
    protected fun splitLineCSV(line: String?, reader: BufferedReader): Array<String?> {
        if (csl == null) {
            csl = CommaSeparatedLine()
        }
        return csl!!.handle(line, reader)
    }
    /**
     * Returns the next comma (not inside a quote) in the specified array.
     * @param c array to search
     * @param index offset at which to start looking
     * @return index of the comma, or -1 if line ended inside an unclosed quote
     */
    /*
  static protected int nextComma(char[] c, int index) {
    if (index == c.length) {  // we're already at the end
      return c.length;
    }
    boolean quoted = c[index] == '\"';
    if (quoted) {
      index++; // step over the quote
    }
    for (int i = index; i < c.length; i++) {
      if (c[i] == '\"') {
        // if this fella started with a quote
        if (quoted) {
          if (i == c.length-1) {
            //return -1;  // ran out of chars
            // closing quote for field; last field on the line
            return c.length;
          } else if (c[i+1] == '\"') {
            // an escaped quote inside a quoted field, step over it
            i++;
          } else if (c[i+1] == ',') {
            // that's our closing quote, get outta here
            return i+1;
          }

        } else {  // not a quoted line
          if (i == c.length-1) {
            // we're at the end of the line, can't have an unescaped quote
            //return -1;  // ran out of chars
            throw new RuntimeException("Unterminated quoted field at end of line");
          } else if (c[i+1] == '\"') {
            // step over this crummy quote escape
            ++i;
          } else {
            throw new RuntimeException("Unterminated quoted field mid-line");
          }
        }
      } else if (!quoted && c[i] == ',') {
        return i;
      }
      if (!quote && (c[i] == ',')) {
        // found a comma, return this location
        return i;
      } else if (c[i] == '\"') {
        // if it's a quote, then either the next char is another quote,
        // or if this is a quoted entry, it better be a comma
        quote = !quote;
      }
    }
    // if still inside a quote, indicate that another line should be read
    if (quote) {
      return -1;
    }
    // made it to the end of the array with no new comma
    return c.length;
  }
  */
    /**
     * Read a .ods (OpenDoc spreadsheet) zip file from an InputStream, and
     * return the InputStream for content.xml contained inside.
     */
    private fun odsFindContentXML(input: InputStream?): InputStream? {
        val zis = ZipInputStream(input)
        var entry: ZipEntry? = null
        try {
            while ((zis.nextEntry.also { entry = it }) != null) {
                if ((entry!!.name == "content.xml")) {
                    return zis
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    protected fun odsParse(input: InputStream?, worksheet: String?, header: Boolean) {
        try {
            val contentStream = odsFindContentXML(input)
            val xml = XML(contentStream)

            // table files will have multiple sheets..
            // <table:table table:name="Sheet1" table:style-name="ta1" table:print="false">
            // <table:table table:name="Sheet2" table:style-name="ta1" table:print="false">
            // <table:table table:name="Sheet3" table:style-name="ta1" table:print="false">
            val sheets = xml.getChildren("office:body/office:spreadsheet/table:table")
            var found = false
            for (sheet in sheets) {
//        System.out.println(sheet.getAttribute("table:name"));
                if (worksheet == null || worksheet == sheet.getString("table:name")) {
                    odsParseSheet(sheet, header)
                    found = true
                    if (worksheet == null) {
                        break // only read the first sheet
                    }
                }
            }
            if (!found) {
                if (worksheet == null) {
                    throw RuntimeException("No worksheets found in the ODS file.")
                } else {
                    throw RuntimeException("No worksheet named " + worksheet +
                            " found in the ODS file.")
                }
            }
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ParserConfigurationException) {
            e.printStackTrace()
        } catch (e: SAXException) {
            e.printStackTrace()
        }
    }

    /**
     * Parses a single sheet of XML from this file.
     * @param The XML object for a single worksheet from the ODS file
     */
    private fun odsParseSheet(sheet: XML, header: Boolean) {
        // Extra <p> or <a> tags inside the text tag for the cell will be stripped.
        // Different from showing formulas, and not quite the same as 'save as
        // displayed' option when saving from inside OpenOffice. Only time we
        // wouldn't want this would be so that we could parse hyperlinks and
        // styling information intact, but that's out of scope for the p5 version.
        var header = header
        val ignoreTags = true
        val rows = sheet.getChildren("table:table-row")
        //xml.getChildren("office:body/office:spreadsheet/table:table/table:table-row");
        var rowIndex = 0
        for (row in rows) {
            val rowRepeat = row.getInt("table:number-rows-repeated", 1)
            //      if (rowRepeat != 1) {
//          System.out.println(rowRepeat + " " + rowCount + " " + (rowCount + rowRepeat));
//      }
            var rowNotNull = false
            val cells = row.getChildren()
            var columnIndex = 0
            for (cell in cells) {
                val cellRepeat = cell.getInt("table:number-columns-repeated", 1)

//        <table:table-cell table:formula="of:=SUM([.E7:.E8])" office:value-type="float" office:value="4150">
//        <text:p>4150.00</text:p>
//        </table:table-cell>
                var cellData = if (ignoreTags) cell.getString("office:value") else null

                // if there's an office:value in the cell, just roll with that
                if (cellData == null) {
                    val cellKids = cell.childCount
                    if (cellKids != 0) {
                        val paragraphElements = cell.getChildren("text:p")
                        if (paragraphElements.size != 1) {
                            for (el in paragraphElements) {
                                System.err.println(el.toString())
                            }
                            throw RuntimeException("found more than one text:p element")
                        }
                        val textp = paragraphElements[0]
                        val textpContent = textp.content
                        // if there are sub-elements, the content shows up as a child element
                        // (for which getName() returns null.. which seems wrong)
                        cellData = if (textpContent != null) {
                            textpContent // nothing fancy, the text is in the text:p element
                        } else {
                            val textpKids = textp.getChildren()
                            val cellBuffer = StringBuilder()
                            for (kid: XML in textpKids) {
                                val kidName = kid.name
                                if (kidName == null) {
                                    odsAppendNotNull(kid, cellBuffer)
                                } else if (kidName == "text:s") {
                                    val spaceCount = kid.getInt("text:c", 1)
                                    for (space in 0 until spaceCount) {
                                        cellBuffer.append(' ')
                                    }
                                } else if (kidName == "text:span") {
                                    odsAppendNotNull(kid, cellBuffer)
                                } else if (kidName == "text:a") {
                                    // <text:a xlink:href="http://blah.com/">blah.com</text:a>
                                    if (ignoreTags) {
                                        cellBuffer.append(kid.getString("xlink:href"))
                                    } else {
                                        odsAppendNotNull(kid, cellBuffer)
                                    }
                                } else {
                                    odsAppendNotNull(kid, cellBuffer)
                                    System.err.println(javaClass.name + ": don't understand: " + kid)
                                    //throw new RuntimeException("I'm not used to this.");
                                }
                            }
                            cellBuffer.toString()
                        }
                        //setString(rowIndex, columnIndex, c); //text[0].getContent());
                        //columnIndex++;
                    }
                }
                for (r in 0 until cellRepeat) {
                    cellData?.let { setString(rowIndex, columnIndex, it) }
                    columnIndex++
                    if (cellData != null) {
//            if (columnIndex > columnMax) {
//              columnMax = columnIndex;
//            }
                        rowNotNull = true
                    }
                }
            }
            if (header) {
                removeTitleRow() // efficient enough on the first row
                header = false // avoid infinite loop
            } else {
                if (rowNotNull && rowRepeat > 1) {
                    var rowStrings = getStringRow(rowIndex)
                    for (r in 1 until rowRepeat) {
                        addRow(rowStrings as Array<Any?>)
                    }
                }
                rowIndex += rowRepeat
            }
        }
    }

    private fun odsAppendNotNull(kid: XML, buffer: StringBuilder) {
        val content = kid.content
        if (content != null) {
            buffer.append(content)
        }
    }
    // A 'Class' object is used here, so the syntax for this function is:
    // Table t = loadTable("cars3.tsv", "header");
    // Record[] records = (Record[]) t.parse(Record.class);
    // While t.parse("Record") might be nicer, the class is likely to be an
    // inner class (another tab in a PDE sketch) or even inside a package,
    // so additional information would be needed to locate it. The name of the
    // inner class would be "SketchName$Record" which isn't acceptable syntax
    // to make people use. Better to just introduce the '.class' syntax.
    // Unlike the Table class itself, this accepts char and boolean fields in
    // the target class, since they're much more prevalent, and don't require
    // a zillion extra methods and special cases in the rest of the class here.
    // since this is likely an inner class, needs a reference to its parent,
    // because that's passed to the constructor parameter (inserted by the
    // compiler) of an inner class by the runtime.
    /** incomplete, do not use  */
    fun parseInto(enclosingObject: Any, fieldName: String?) {
        var target: Class<*>? = null
        var outgoing: Any? = null
        var targetField: Field? = null
        try {
            // Object targetObject,
            // Class target -> get this from the type of fieldName
//      Class sketchClass = sketch.getClass();
            val sketchClass: Class<*> = enclosingObject.javaClass
            targetField = sketchClass.getDeclaredField(fieldName)
            //      PApplet.println("found " + targetField);
            val targetArray = targetField.getType()
            if (!targetArray.isArray) {
                // fieldName is not an array
            } else {
                target = targetArray.componentType
                outgoing = java.lang.reflect.Array.newInstance(target, getRowCount())
            }
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

//    Object enclosingObject = sketch;
//    PApplet.println("enclosing obj is " + enclosingObject);
        val enclosingClass = target!!.enclosingClass
        var con: Constructor<*>? = null
        try {
            con = if (enclosingClass == null) {
                target.getDeclaredConstructor() //new Class[] { });
                //        PApplet.println("no enclosing class");
            } else {
                target.getDeclaredConstructor(*arrayOf(enclosingClass))
                //        PApplet.println("enclosed by " + enclosingClass.getName());
            }
            if (!con.isAccessible) {
//        System.out.println("setting constructor to public");
                con.isAccessible = true
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        }
        val fields = target.declaredFields
        val inuse = ArrayList<Field>()
        for (field in fields) {
            val name = field.name
            if (getColumnIndex(name, false) != -1) {
//        System.out.println("found field " + name);
                if (!field.isAccessible) {
//          PApplet.println("  changing field access");
                    field.isAccessible = true
                }
                inuse.add(field)
            } else {
//        System.out.println("skipping field " + name);
            }
        }
        var index = 0
        try {
            for (row in rows()) {
                var item: Any? = null
                item = if (enclosingClass == null) {
                    //item = target.newInstance();
                    con!!.newInstance()
                } else {
                    con!!.newInstance(*arrayOf(enclosingObject))
                }
                //Object item = defaultCons.newInstance(new Object[] { });
                for (field in inuse) {
                    val name = field.name
                    //PApplet.println("gonna set field " + name);
                    if (field.type == String::class.java) {
                        field[item] = row!!.getString(name)
                    } else if (field.type == Integer.TYPE) {
                        field.setInt(item, row!!.getInt(name))
                    } else if (field.type == java.lang.Long.TYPE) {
                        field.setLong(item, row!!.getLong(name))
                    } else if (field.type == java.lang.Float.TYPE) {
                        field.setFloat(item, row!!.getFloat(name))
                    } else if (field.type == java.lang.Double.TYPE) {
                        field.setDouble(item, row!!.getDouble(name))
                    } else if (field.type == java.lang.Boolean.TYPE) {
                        val content = row!!.getString(name)
                        if (content != null) {
                            // Only bother setting if it's true,
                            // otherwise false by default anyway.
                            if (content.toLowerCase() == "true" || content == "1") {
                                field.setBoolean(item, true)
                            }
                        }
                        //            if (content == null) {
//              field.setBoolean(item, false);  // necessary?
//            } else if (content.toLowerCase().equals("true")) {
//              field.setBoolean(item, true);
//            } else if (content.equals("1")) {
//              field.setBoolean(item, true);
//            } else {
//              field.setBoolean(item, false);  // necessary?
//            }
                    } else if (field.type == Character.TYPE) {
                        val content = row!!.getString(name)
                        if (content != null && content.length > 0) {
                            // Otherwise set to \0 anyway
                            field.setChar(item, content[0])
                        }
                    }
                }
                //        list.add(item);
                java.lang.reflect.Array.set(outgoing, index++, item)
            }
            if (!targetField!!.isAccessible) {
//        PApplet.println("setting target field to public");
                targetField.isAccessible = true
            }
            // Set the array in the sketch
//      targetField.set(sketch, outgoing);
            targetField[enclosingObject] = outgoing
        } catch (e: InstantiationException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    fun save(file: File, options: String?): Boolean {
        return save(PApplet.createOutput(file),
                extensionOptions(false, file.name, options))
    }

    fun save(output: OutputStream?, options: String?): Boolean {
        val writer = PApplet.createWriter(output)
        var extension: String? = null
        if (options == null) {
            throw IllegalArgumentException("No extension specified for saving this Table")
        }
        val opts = PApplet.trim(PApplet.split(options, ','))
        // Only option for save is the extension, so we can safely grab the last
        extension = opts[opts.size - 1]
        var found = false
        for (ext in saveExtensions) {
            if (extension == ext) {
                found = true
                break
            }
        }
        // Not providing a fallback; let's make users specify an extension
        if (!found) {
            throw IllegalArgumentException("'$extension' not available for Table")
        }
        if (extension == "csv") {
            writeCSV(writer)
        } else if (extension == "tsv") {
            writeTSV(writer)
        } else if (extension == "ods") {
            try {
                saveODS(output)
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
        } else if (extension == "html") {
            writeHTML(writer)
        } else if (extension == "bin") {
            try {
                saveBinary(output)
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
        }
        writer.flush()
        writer.close()
        return true
    }

    protected fun writeTSV(writer: PrintWriter) {
        if (columnTitles != null) {
            for (col in columns.indices) {
                if (col != 0) {
                    writer.print('\t')
                }
                if (columnTitles!![col] != null) {
                    writer.print(columnTitles!![col])
                }
            }
            writer.println()
        }
        for (row in 0 until rowCount) {
            for (col in 0 until getColumnCount()) {
                if (col != 0) {
                    writer.print('\t')
                }
                val entry = getString(row, col)
                // just write null entries as blanks, rather than spewing 'null'
                // all over the spreadsheet file.
                if (entry != null) {
                    writer.print(entry)
                }
            }
            writer.println()
        }
        writer.flush()
    }

    protected fun writeCSV(writer: PrintWriter) {
        if (columnTitles != null) {
            for (col in 0 until getColumnCount()) {
                if (col != 0) {
                    writer.print(',')
                }
                try {
                    if (columnTitles!![col] != null) {  // col < columnTitles.length &&
                        writeEntryCSV(writer, columnTitles!![col])
                    }
                } catch (e: ArrayIndexOutOfBoundsException) {
                    PApplet.printArray(columnTitles)
                    PApplet.printArray(columns)
                    throw e
                }
            }
            writer.println()
        }
        for (row in 0 until rowCount) {
            for (col in 0 until getColumnCount()) {
                if (col != 0) {
                    writer.print(',')
                }
                val entry = getString(row, col)
                // just write null entries as blanks, rather than spewing 'null'
                // all over the spreadsheet file.
                entry?.let { writeEntryCSV(writer, it) }
            }
            // Prints the newline for the row, even if it's missing
            writer.println()
        }
        writer.flush()
    }

    protected fun writeEntryCSV(writer: PrintWriter, entry: String?) {
        if (entry != null) {
            if (entry.indexOf('\"') != -1) {  // convert quotes to double quotes
                val c = entry.toCharArray()
                writer.print('\"')
                for (i in c.indices) {
                    if (c[i] == '\"') {
                        writer.print("\"\"")
                    } else {
                        writer.print(c[i])
                    }
                }
                writer.print('\"')

                // add quotes if commas or CR/LF are in the entry
            } else if (entry.indexOf(',') != -1 || entry.indexOf('\n') != -1 || entry.indexOf('\r') != -1) {
                writer.print('\"')
                writer.print(entry)
                writer.print('\"')


                // add quotes if leading or trailing space
            } else if (entry.length > 0 &&
                    (entry[0] == ' ' ||
                            entry[entry.length - 1] == ' ')) {
                writer.print('\"')
                writer.print(entry)
                writer.print('\"')
            } else {
                writer.print(entry)
            }
        }
    }

    protected fun writeHTML(writer: PrintWriter) {
        writer.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 3.2//EN\">")
        //    writer.println("<!DOCTYPE html>");
//    writer.println("<meta charset=\"utf-8\">");
        writer.println("<html>")
        writer.println("<head>")
        writer.println("  <meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\" />")
        writer.println("</head>")
        writer.println("<body>")
        writer.println("  <table>")
        if (hasColumnTitles()) {
            writer.println("  <tr>")
            for (entry in getColumnTitles()!!) {
                writer.print("      <th>")
                entry?.let { writeEntryHTML(writer, it) }
                writer.println("</th>")
            }
            writer.println("  </tr>")
        }
        for (row in 0 until getRowCount()) {
            writer.println("    <tr>")
            for (col in 0 until getColumnCount()) {
                val entry = getString(row, col)
                writer.print("      <td>")
                entry?.let { writeEntryHTML(writer, it) }
                writer.println("</td>")
            }
            writer.println("    </tr>")
        }
        writer.println("  </table>")
        writer.println("</body>")
        writer.println("</html>")
        writer.flush()
    }

    protected fun writeEntryHTML(writer: PrintWriter, entry: String) {
        //char[] chars = entry.toCharArray();
        for (c in entry.toCharArray()) {  //chars) {
            if (c == '<') {
                writer.print("&lt;")
            } else if (c == '>') {
                writer.print("&gt;")
            } else if (c == '&') {
                writer.print("&amp;")
                //      } else if (c == '\'') {  // only in XML
//        writer.print("&apos;");
            } else if (c == '"') {
                writer.print("&quot;")
            } else if (c.toInt() < 32 || c.toInt() > 127) {  // keep in ASCII or Tidy complains
                writer.print("&#")
                writer.print(c.toInt())
                writer.print(';')
            } else {
                writer.print(c)
            }
        }
    }

    @Throws(IOException::class)
    protected fun saveODS(os: OutputStream?) {
        val zos = ZipOutputStream(os)
        val xmlHeader = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        var entry: ZipEntry? = ZipEntry("META-INF/manifest.xml")
        var lines: Array<String> = arrayOf(
                xmlHeader,
                "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\">",
                "  <manifest:file-entry manifest:media-type=\"application/vnd.oasis.opendocument.spreadsheet\" manifest:version=\"1.2\" manifest:full-path=\"/\"/>",
                "  <manifest:file-entry manifest:media-type=\"text/xml\" manifest:full-path=\"content.xml\"/>",
                "  <manifest:file-entry manifest:media-type=\"text/xml\" manifest:full-path=\"styles.xml\"/>",
                "  <manifest:file-entry manifest:media-type=\"text/xml\" manifest:full-path=\"meta.xml\"/>",
                "  <manifest:file-entry manifest:media-type=\"text/xml\" manifest:full-path=\"settings.xml\"/>",
                "</manifest:manifest>"
        )
        zos.putNextEntry(entry)
        zos.write(PApplet.join(lines, "\n").toByteArray())
        zos.closeEntry()

        /*
    entry = new ZipEntry("meta.xml");
    lines = new String[] {
      xmlHeader,
      "<office:document-meta office:version=\"1.0\"" +
      " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" />"
    };
    zos.putNextEntry(entry);
    zos.write(PApplet.join(lines, "\n").getBytes());
    zos.closeEntry();

    entry = new ZipEntry("meta.xml");
    lines = new String[] {
      xmlHeader,
      "<office:document-settings office:version=\"1.0\"" +
      " xmlns:config=\"urn:oasis:names:tc:opendocument:xmlns:config:1.0\"" +
      " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\"" +
      " xmlns:ooo=\"http://openoffice.org/2004/office\"" +
      " xmlns:xlink=\"http://www.w3.org/1999/xlink\" />"
    };
    zos.putNextEntry(entry);
    zos.write(PApplet.join(lines, "\n").getBytes());
    zos.closeEntry();

    entry = new ZipEntry("settings.xml");
    lines = new String[] {
      xmlHeader,
      "<office:document-settings office:version=\"1.0\"" +
      " xmlns:config=\"urn:oasis:names:tc:opendocument:xmlns:config:1.0\"" +
      " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\"" +
      " xmlns:ooo=\"http://openoffice.org/2004/office\"" +
      " xmlns:xlink=\"http://www.w3.org/1999/xlink\" />"
    };
    zos.putNextEntry(entry);
    zos.write(PApplet.join(lines, "\n").getBytes());
    zos.closeEntry();

    entry = new ZipEntry("styles.xml");
    lines = new String[] {
      xmlHeader,
      "<office:document-styles office:version=\"1.0\"" +
      " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" />"
    };
    zos.putNextEntry(entry);
    zos.write(PApplet.join(lines, "\n").getBytes());
    zos.closeEntry();
    */
        val dummyFiles = arrayOf(
                "meta.xml", "settings.xml", "styles.xml"
        )
        lines = arrayOf(
                xmlHeader,
                "<office:document-meta office:version=\"1.0\"" +
                        " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" />"
        )
        val dummyBytes = PApplet.join(lines, "\n").toByteArray()
        for (filename in dummyFiles) {
            entry = ZipEntry(filename)
            zos.putNextEntry(entry)
            zos.write(dummyBytes)
            zos.closeEntry()
        }

        //
        entry = ZipEntry("mimetype")
        zos.putNextEntry(entry)
        zos.write("application/vnd.oasis.opendocument.spreadsheet".toByteArray())
        zos.closeEntry()

        //
        entry = ZipEntry("content.xml")
        zos.putNextEntry(entry)
        //lines = new String[] {
        writeUTF(zos, *arrayOf(
                xmlHeader,
                "<office:document-content" +
                        " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\"" +
                        " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\"" +
                        " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\"" +
                        " office:version=\"1.2\">",
                "  <office:body>",
                "    <office:spreadsheet>",
                "      <table:table table:name=\"Sheet1\" table:print=\"false\">"
        ))
        //zos.write(PApplet.join(lines, "\n").getBytes());
        val rowStart = "        <table:table-row>\n".toByteArray()
        val rowStop = "        </table:table-row>\n".toByteArray()
        if (hasColumnTitles()) {
            zos.write(rowStart)
            for (i in 0 until getColumnCount()) {
                saveStringODS(zos, columnTitles!![i])
            }
            zos.write(rowStop)
        }
        for (row in rows()) {
            zos.write(rowStart)
            for (i in 0 until getColumnCount()) {
                if (columnTypes[i] == STRING || columnTypes[i] == CATEGORY) {
                    saveStringODS(zos, row!!.getString(i))
                } else {
                    saveNumberODS(zos, row!!.getString(i))
                }
            }
            zos.write(rowStop)
        }

        //lines = new String[] {
        writeUTF(zos, *arrayOf(
                "      </table:table>",
                "    </office:spreadsheet>",
                "  </office:body>",
                "</office:document-content>"
        ))
        //zos.write(PApplet.join(lines, "\n").getBytes());
        zos.closeEntry()
        zos.flush()
        zos.close()
    }

    @Throws(IOException::class)
    fun saveStringODS(output: OutputStream, text: String?) {
        // At this point, I should have just used the XML library. But this does
        // save us from having to create the entire document in memory again before
        // writing to the file. So while it's dorky, the outcome is still useful.
        val sanitized = StringBuilder()
        if (text != null) {
            val array = text.toCharArray()
            for (c in array) {
                if (c == '&') {
                    sanitized.append("&amp;")
                } else if (c == '\'') {
                    sanitized.append("&apos;")
                } else if (c == '"') {
                    sanitized.append("&quot;")
                } else if (c == '<') {
                    sanitized.append("&lt;")
                } else if (c == '>') {
                    sanitized.append("&rt;")
                } else if (c.toInt() < 32 || c.toInt() > 127) {
                    sanitized.append("&#" + c.toInt() + ";")
                } else {
                    sanitized.append(c)
                }
            }
        }
        writeUTF(output,
                "          <table:table-cell office:value-type=\"string\">",
                "            <text:p>$sanitized</text:p>",
                "          </table:table-cell>")
    }

    @Throws(IOException::class)
    fun saveNumberODS(output: OutputStream, text: String?) {
        writeUTF(output,
                "          <table:table-cell office:value-type=\"float\" office:value=\"$text\">",
                "            <text:p>$text</text:p>",
                "          </table:table-cell>")
    }

    @Throws(IOException::class)
    protected fun saveBinary(os: OutputStream?) {
        val output = DataOutputStream(BufferedOutputStream(os))
        output.writeInt(-0x6ff854e2) // version
        output.writeInt(getRowCount())
        output.writeInt(getColumnCount())
        if (columnTitles != null) {
            output.writeBoolean(true)
            for (title in columnTitles!!) {
                output.writeUTF(title)
            }
        } else {
            output.writeBoolean(false)
        }
        for (i in 0 until getColumnCount()) {
            //System.out.println(i + " is " + columnTypes[i]);
            output.writeInt(columnTypes[i])
        }
        for (i in 0 until getColumnCount()) {
            if (columnTypes[i] == CATEGORY) {
                columnCategories[i]!!.write(output)
            }
        }
        if (missingString == null) {
            output.writeBoolean(false)
        } else {
            output.writeBoolean(true)
            output.writeUTF(missingString)
        }
        output.writeInt(missingInt)
        output.writeLong(missingLong)
        output.writeFloat(missingFloat)
        output.writeDouble(missingDouble)
        output.writeInt(missingCategory)
        for (row in rows()) {
            for (col in 0 until getColumnCount()) {
                when (columnTypes[col]) {
                    STRING -> {
                        val str = row!!.getString(col)
                        if (str == null) {
                            output.writeBoolean(false)
                        } else {
                            output.writeBoolean(true)
                            output.writeUTF(str)
                        }
                    }
                    INT -> output.writeInt(row!!.getInt(col))
                    LONG -> output.writeLong(row!!.getLong(col))
                    FLOAT -> output.writeFloat(row!!.getFloat(col))
                    DOUBLE -> output.writeDouble(row!!.getDouble(col))
                    CATEGORY -> {
                        val peace = row!!.getString(col)
                        if (peace == missingString) {
                            output.writeInt(missingCategory)
                        } else {
                            output.writeInt(columnCategories[col]!!.index(peace))
                        }
                    }
                }
            }
        }
        output.flush()
        output.close()
    }

    @Throws(IOException::class)
    protected fun loadBinary(`is`: InputStream?) {
        val input = DataInputStream(BufferedInputStream(`is`))
        val magic = input.readInt()
        if (magic != -0x6ff854e2) {
            throw IOException("Not a compatible binary table (magic was " + PApplet.hex(magic) + ")")
        }
        val rowCount = input.readInt()
        setRowCount(rowCount)
        val columnCount = input.readInt()
        setColumnCount(columnCount)
        val hasTitles = input.readBoolean()
        if (hasTitles) {
            columnTitles = arrayOfNulls(columnCount)
            for (i in 0 until columnCount) {
                //columnTitles[i] = input.readUTF();
                setColumnTitle(i, input.readUTF())
            }
        }
        for (column in 0 until columnCount) {
            val newType = input.readInt()
            columnTypes[column] = newType
            when (newType) {
                INT -> columns[column] = IntArray(rowCount)
                LONG -> {
                    columns[column] = LongArray(rowCount)
                }
                FLOAT -> {
                    columns[column] = FloatArray(rowCount)
                }
                DOUBLE -> {
                    columns[column] = DoubleArray(rowCount)
                }
                STRING -> {
                    columns[column] = arrayOfNulls<String>(rowCount)
                }
                CATEGORY -> {
                    columns[column] = IntArray(rowCount)
                }
                else -> throw IllegalArgumentException("$newType is not a valid column type.")
            }
        }
        for (i in 0 until columnCount) {
            if (columnTypes[i] == CATEGORY) {
                columnCategories[i] = HashMapBlows(input)
            }
        }
        missingString = if (input.readBoolean()) {
            input.readUTF()
        } else {
            null
        }
        missingInt = input.readInt()
        missingLong = input.readLong()
        missingFloat = input.readFloat()
        missingDouble = input.readDouble()
        missingCategory = input.readInt()
        for (row in 0 until rowCount) {
            for (col in 0 until columnCount) {
                when (columnTypes[col]) {
                    STRING -> {
                        var str: String? = null
                        if (input.readBoolean()) {
                            str = input.readUTF()
                        }
                        setString(row, col, str)
                    }
                    INT -> setInt(row, col, input.readInt())
                    LONG -> setLong(row, col, input.readLong())
                    FLOAT -> setFloat(row, col, input.readFloat())
                    DOUBLE -> setDouble(row, col, input.readDouble())
                    CATEGORY -> {
                        val index = input.readInt()
                        //String name = columnCategories[col].key(index);
                        setInt(row, col, index)
                    }
                }
            }
        }
        input.close()
    }
    /**
     * @param type the type to be used for the new column: INT, LONG, FLOAT, DOUBLE, or STRING
     */
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * @webref table:method
     * @brief Adds a new column to a table
     * @see Table.removeColumn
     */
    /**
     * @param title the title to be used for the new column
     */
    @JvmOverloads
    fun addColumn(title: String? = null, type: Int = STRING) {
        insertColumn(columns.size, title, type)
    }

    @JvmOverloads
    fun insertColumn(index: Int, title: String? = null, type: Int = STRING) {
        if (title != null && columnTitles == null) {
            columnTitles = arrayOfNulls(columns.size)
        }
        if (columnTitles != null) {
            columnTitles = PApplet.splice(columnTitles, title, index)
            columnIndices = null
        }
        columnTypes = PApplet.splice(columnTypes, type, index)

//    columnCategories = (HashMapBlows[])
//      PApplet.splice(columnCategories, new HashMapBlows(), index);
        val catTemp = arrayOfNulls<HashMapBlows>(columns.size + 1)
        // Faster than arrayCopy for a dozen or so entries
        for (i in 0 until index) {
            catTemp[i] = columnCategories[i]
        }
        catTemp[index] = HashMapBlows()
        for (i in index until columns.size) {
            catTemp[i + 1] = columnCategories[i]
        }
        columnCategories = catTemp
        val temp = arrayOfNulls<Any>(columns.size + 1)
        System.arraycopy(columns, 0, temp, 0, index)
        System.arraycopy(columns, index, temp, index + 1, columns.size - index)
        columns = temp
        when (type) {
            INT -> columns[index] = IntArray(rowCount)
            LONG -> columns[index] = LongArray(rowCount)
            FLOAT -> columns[index] = FloatArray(rowCount)
            DOUBLE -> columns[index] = DoubleArray(rowCount)
            STRING -> columns[index] = arrayOfNulls<String>(rowCount)
            CATEGORY -> columns[index] = IntArray(rowCount)
        }
    }

    /**
     * @webref table:method
     * @brief Removes a column from a table
     * @param columnName the title of the column to be removed
     * @see Table.addColumn
     */
    fun removeColumn(columnName: String?) {
        removeColumn(getColumnIndex(columnName))
    }

    /**
     * @param column the index number of the column to be removed
     */
    fun removeColumn(column: Int) {
        val newCount = columns.size - 1
        val columnsTemp = arrayOfNulls<Any>(newCount)
        val catTemp = arrayOfNulls<HashMapBlows>(newCount)
        for (i in 0 until column) {
            columnsTemp[i] = columns[i]
            catTemp[i] = columnCategories[i]
        }
        for (i in column until newCount) {
            columnsTemp[i] = columns[i + 1]
            catTemp[i] = columnCategories[i + 1]
        }
        columns = columnsTemp
        columnCategories = catTemp
        if (columnTitles != null) {
            val titlesTemp = arrayOfNulls<String>(newCount)
            for (i in 0 until column) {
                titlesTemp[i] = columnTitles!![i]
            }
            for (i in column until newCount) {
                titlesTemp[i] = columnTitles!![i + 1]
            }
            columnTitles = titlesTemp
            columnIndices = null
        }
    }

//    lateinit var columnCount: Int
//        get() = columns.size

    /**
     * @webref table:method
     * @brief Gets the number of columns in a table
     * @see Table.getRowCount
     */
    fun getColumnCount(): Int {
        return columns.size
    }

    /**
     * Change the number of columns in this table. Resizes all rows to ensure
     * the same number of columns in each row. Entries in the additional (empty)
     * columns will be set to null.
     * @param newCount
     */
    fun setColumnCount(newCount: Int) {
        val oldCount = columns.size
        if (oldCount != newCount) {
            columns = PApplet.expand(columns, newCount) as Array<Any?>
            // create new columns, default to String as the data type
            for (c in oldCount until newCount) {
                columns[c] = arrayOfNulls<String>(rowCount)
            }
            if (columnTitles != null) {
                columnTitles = PApplet.expand(columnTitles, newCount)
            }
            columnTypes = PApplet.expand(columnTypes, newCount)
            columnCategories = PApplet.expand(columnCategories, newCount) as Array<HashMapBlows?>
        }
    }

    fun setColumnType(columnName: String?, columnType: String?) {
        setColumnType(checkColumnIndex(columnName), columnType)
    }

    /**
     * Set the data type for a column so that using it is more efficient.
     * @param column the column to change
     * @param columnType One of int, long, float, double, string, or category.
     */
    fun setColumnType(column: Int, columnType: String?) {
        setColumnType(column, parseColumnType(columnType))
    }

    fun setColumnType(columnName: String?, newType: Int) {
        setColumnType(checkColumnIndex(columnName), newType)
    }

    /**
     * Sets the column type. If data already exists, then it'll be converted to
     * the new type.
     * @param column the column whose type should be changed
     * @param newType something fresh, maybe try an int or a float for size?
     */
    fun setColumnType(column: Int, newType: Int) {
        when (newType) {
            INT -> {
                val intData = IntArray(rowCount)
                var row = 0
                while (row < rowCount) {
                    val s = getString(row, column)
                    intData[row] = if (s == null) missingInt else PApplet.parseInt(s, missingInt)
                    row++
                }
                columns[column] = intData
            }
            LONG -> {
                val longData = LongArray(rowCount)
                var row = 0
                while (row < rowCount) {
                    val s = getString(row, column)
                    try {
                        longData[row] = s?.toLong() ?: missingLong
                    } catch (nfe: NumberFormatException) {
                        longData[row] = missingLong
                    }
                    row++
                }
                columns[column] = longData
            }
            FLOAT -> {
                val floatData = FloatArray(rowCount)
                var row = 0
                while (row < rowCount) {
                    val s = getString(row, column)
                    floatData[row] = if (s == null) missingFloat else PApplet.parseFloat(s, missingFloat)
                    row++
                }
                columns[column] = floatData
            }
            DOUBLE -> {
                val doubleData = DoubleArray(rowCount)
                var row = 0
                while (row < rowCount) {
                    val s = getString(row, column)
                    try {
                        doubleData[row] = s?.toDouble() ?: missingDouble
                    } catch (nfe: NumberFormatException) {
                        doubleData[row] = missingDouble
                    }
                    row++
                }
                columns[column] = doubleData
            }
            STRING -> {
                if (columnTypes[column] != STRING) {
                    val stringData = arrayOfNulls<String>(rowCount)
                    var row = 0
                    while (row < rowCount) {
                        stringData[row] = getString(row, column)
                        row++
                    }
                    columns[column] = stringData
                }
            }
            CATEGORY -> {
                val indexData = IntArray(rowCount)
                val categories = HashMapBlows()
                var row = 0
                while (row < rowCount) {
                    val s = getString(row, column)
                    indexData[row] = categories.index(s)
                    row++
                }
                columnCategories[column] = categories
                columns[column] = indexData
            }
            else -> {
                throw IllegalArgumentException("That's not a valid column type.")
            }
        }
        //    System.out.println("new type is " + newType);
        columnTypes[column] = newType
    }

    /**
     * Set the entire table to a specific data type.
     */
    fun setTableType(type: String?) {
        for (col in 0 until getColumnCount()) {
            setColumnType(col, type)
        }
    }

    fun setColumnTypes(types: IntArray?) {
        ensureColumn(types!!.size - 1)
        for (col in types.indices) {
            setColumnType(col, types[col])
        }
    }

    /**
     * Set the titles (and if a second column is present) the data types for
     * this table based on a file loaded separately. This will look for the
     * title in column 0, and the type in column 1. Better yet, specify a
     * column named "title" and another named "type" in the dictionary table
     * to future-proof the code.
     * @param dictionary
     */
    fun setColumnTypes(dictionary: Table) {
        ensureColumn(dictionary.getRowCount() - 1)
        var titleCol = 0
        var typeCol = 1
        if (dictionary.hasColumnTitles()) {
            titleCol = dictionary.getColumnIndex("title", true)
            typeCol = dictionary.getColumnIndex("type", true)
        }
        setColumnTitles(dictionary.getStringColumn(titleCol))
        val typeNames = dictionary.getStringColumn(typeCol)
        if (dictionary.getColumnCount() > 1) {
            if (getRowCount() > 1000) {
                val proc = Runtime.getRuntime().availableProcessors()
                val pool = Executors.newFixedThreadPool(proc / 2)
                for (i in 0 until dictionary.getRowCount()) {
                    val col = i
                    pool.execute { setColumnType(col, typeNames[col]) }
                }
                pool.shutdown()
                while (!pool.isTerminated) {
                    Thread.yield()
                }
            } else {
                for (col in 0 until dictionary.getRowCount()) {
//          setColumnType(i, dictionary.getString(i, typeCol));
                    setColumnType(col, typeNames[col])
                }
            }
        }
    }

    fun getColumnType(columnName: String?): Int {
        return getColumnType(getColumnIndex(columnName))
    }

    /** Returns one of Table.STRING, Table.INT, etc...  */
    fun getColumnType(column: Int): Int {
        return columnTypes[column]
    }

    fun getColumnTypes(): IntArray {
        return columnTypes
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Remove the first row from the data set, and use it as the column titles.
     * Use loadTable("table.csv", "header") instead.
     */
    @Deprecated("")
    fun removeTitleRow(): Array<String?> {
        val titles = getStringRow(0)
        removeRow(0)
        setColumnTitles(titles)
        return titles
    }

    fun setColumnTitles(titles: Array<String?>?) {
        if (titles != null) {
            ensureColumn(titles.size - 1)
        }
        columnTitles = titles
        columnIndices = null // remove the cache
    }

    fun setColumnTitle(column: Int, title: String?) {
        ensureColumn(column)
        if (columnTitles == null) {
            columnTitles = arrayOfNulls(getColumnCount())
        }
        columnTitles!![column] = title
        columnIndices = null // reset these fellas
    }

    fun hasColumnTitles(): Boolean {
        return columnTitles != null
    }

    fun getColumnTitles(): Array<String?>? {
        return columnTitles
    }

    fun getColumnTitle(col: Int): String? {
        return if (columnTitles == null) null else columnTitles!![col]
    }

    fun getColumnIndex(columnName: String?): Int {
        return getColumnIndex(columnName, true)
    }

    /**
     * Get the index of a column.
     * @param name Name of the column.
     * @param report Whether to throw an exception if the column wasn't found.
     * @return index of the found column, or -1 if not found.
     */
    protected fun getColumnIndex(name: String?, report: Boolean): Int {
        if (columnTitles == null) {
            if (report) {
                throw IllegalArgumentException("This table has no header, so no column titles are set.")
            }
            return -1
        }
        // only create this on first get(). subsequent calls to set the title will
        // also update this array, but only if it exists.
        if (columnIndices == null) {
            columnIndices = HashMap()
            for (col in columns.indices) {
                columnIndices!![columnTitles!!.get(col)] = col
            }
        }
        val index = columnIndices!![name]
        if (index == null) {
            if (report) {
                // Throws an exception here because the name is known and therefore most useful.
                // (Rather than waiting for it to fail inside, say, getInt())
                throw IllegalArgumentException("This table has no column named '$name'")
            }
            return -1
        }
        return index.toInt()
    }

    /**
     * Same as getColumnIndex(), but creates the column if it doesn't exist.
     * Named this way to not conflict with checkColumn(), an internal function
     * used to ensure that a columns exists, and also to denote that it returns
     * an int for the column index.
     * @param title column title
     * @return index of a new or previously existing column
     */
    fun checkColumnIndex(title: String?): Int {
        val index = getColumnIndex(title, false)
        if (index != -1) {
            return index
        }
        addColumn(title)
        return getColumnCount() - 1
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * @webref table:method
     * @brief Gets the number of rows in a table
     * @see Table.getColumnCount
     */
    fun getRowCount(): Int {
        return rowCount
    }

    fun lastRowIndex(): Int {
        return getRowCount() - 1
    }

    /**
     * @webref table:method
     * @brief Removes all rows from a table
     * @see Table.addRow
     * @see Table.removeRow
     */
    fun clearRows() {
        setRowCount(0)
    }

    fun setRowCount(newCount: Int) {
        if (newCount != rowCount) {
            if (newCount > 1000000) {
                print("Note: setting maximum row count to " + PApplet.nfc(newCount))
            }
            val t = System.currentTimeMillis()
            for (col in columns.indices) {
                when (columnTypes[col]) {
                    INT -> columns[col] = PApplet.expand(columns[col] as IntArray?, newCount)
                    LONG -> columns[col] = PApplet.expand(columns[col] as LongArray?, newCount)
                    FLOAT -> columns[col] = PApplet.expand(columns[col] as FloatArray?, newCount)
                    DOUBLE -> columns[col] = PApplet.expand(columns[col] as DoubleArray?, newCount)
                    STRING -> columns[col] = PApplet.expand(columns[col] as Array<String?>?, newCount)
                    CATEGORY -> columns[col] = PApplet.expand(columns[col] as IntArray?, newCount)
                }
                if (newCount > 1000000) {
                    try {
                        Thread.sleep(10) // gc time!
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
            if (newCount > 1000000) {
                val ms = (System.currentTimeMillis() - t).toInt()
                println(" (resize took " + PApplet.nfc(ms) + " ms)")
            }
        }
        rowCount = newCount
    }

    /**
     * @webref table:method
     * @brief Adds a row to a table
     * @see Table.removeRow
     * @see Table.clearRows
     */
    fun addRow(): TableRow {
        //if (rowIncrement == 0) {
        setRowCount(rowCount + 1)
        return RowPointer(this, rowCount - 1)
    }

    /**
     * @param source a reference to the original row to be duplicated
     */
    fun addRow(source: TableRow): TableRow {
        return setRow(rowCount, source)
    }

    fun setRow(row: Int, source: TableRow?): TableRow {
        // Make sure there are enough columns to add this data
        ensureBounds(row, source!!.getColumnCount() - 1)
        for (col in 0 until Math.min(source!!.getColumnCount(), columns.size)) {
            when (columnTypes[col]) {
                INT -> setInt(row, col, source!!.getInt(col))
                LONG -> setLong(row, col, source!!.getLong(col))
                FLOAT -> setFloat(row, col, source!!.getFloat(col))
                DOUBLE -> setDouble(row, col, source!!.getDouble(col))
                STRING -> setString(row, col, source!!.getString(col))
                CATEGORY -> {
                    val index = source!!.getInt(col)
                    setInt(row, col, index)
                    if (!columnCategories[col]!!.hasCategory(index)) {
                        columnCategories[col]!!.setCategory(index, source.getString(col))
                    }
                }
                else -> throw RuntimeException("no types")
            }
        }
        return RowPointer(this, row)
    }

    /**
     * @nowebref
     */
    fun addRow(columnData: Array<Any?>): TableRow {
        setRow(getRowCount(), columnData)
        return RowPointer(this, rowCount - 1)
    }

    fun addRows(source: Table) {
        var index = getRowCount()
        setRowCount(index + source.getRowCount())
        for (row in source.rows()) {
            setRow(index++, row)
        }
    }

    fun insertRow(insert: Int, columnData: Array<Any?>) {
        for (col in columns.indices) {
            when (columnTypes[col]) {
                CATEGORY, INT -> {
                    val intTemp = IntArray(rowCount + 1)
                    System.arraycopy(columns[col], 0, intTemp, 0, insert)
                    System.arraycopy(columns[col], insert, intTemp, insert + 1, rowCount - insert)
                    columns[col] = intTemp
                }
                LONG -> {
                    val longTemp = LongArray(rowCount + 1)
                    System.arraycopy(columns[col], 0, longTemp, 0, insert)
                    System.arraycopy(columns[col], insert, longTemp, insert + 1, rowCount - insert)
                    columns[col] = longTemp
                }
                FLOAT -> {
                    val floatTemp = FloatArray(rowCount + 1)
                    System.arraycopy(columns[col], 0, floatTemp, 0, insert)
                    System.arraycopy(columns[col], insert, floatTemp, insert + 1, rowCount - insert)
                    columns[col] = floatTemp
                }
                DOUBLE -> {
                    val doubleTemp = DoubleArray(rowCount + 1)
                    System.arraycopy(columns[col], 0, doubleTemp, 0, insert)
                    System.arraycopy(columns[col], insert, doubleTemp, insert + 1, rowCount - insert)
                    columns[col] = doubleTemp
                }
                STRING -> {
                    val stringTemp = arrayOfNulls<String>(rowCount + 1)
                    System.arraycopy(columns[col], 0, stringTemp, 0, insert)
                    System.arraycopy(columns[col], insert, stringTemp, insert + 1, rowCount - insert)
                    columns[col] = stringTemp
                }
            }
        }
        // Need to increment before setRow(), because it calls ensureBounds()
        // https://github.com/processing/processing/issues/5406
        ++rowCount
        setRow(insert, columnData)
    }

    /**
     * @webref table:method
     * @brief Removes a row from a table
     * @param row ID number of the row to remove
     * @see Table.addRow
     * @see Table.clearRows
     */
    fun removeRow(row: Int) {
        for (col in columns.indices) {
            when (columnTypes[col]) {
                CATEGORY, INT -> {
                    val intTemp = IntArray(rowCount - 1)
                    //          int[] intData = (int[]) columns[col];
//          System.arraycopy(intData, 0, intTemp, 0, dead);
//          System.arraycopy(intData, dead+1, intTemp, dead, (rowCount - dead) + 1);
                    System.arraycopy(columns[col], 0, intTemp, 0, row)
                    System.arraycopy(columns[col], row + 1, intTemp, row, rowCount - row - 1)
                    columns[col] = intTemp
                }
                LONG -> {
                    val longTemp = LongArray(rowCount - 1)
                    //          long[] longData = (long[]) columns[col];
//          System.arraycopy(longData, 0, longTemp, 0, dead);
//          System.arraycopy(longData, dead+1, longTemp, dead, (rowCount - dead) + 1);
                    System.arraycopy(columns[col], 0, longTemp, 0, row)
                    System.arraycopy(columns[col], row + 1, longTemp, row, rowCount - row - 1)
                    columns[col] = longTemp
                }
                FLOAT -> {
                    val floatTemp = FloatArray(rowCount - 1)
                    //          float[] floatData = (float[]) columns[col];
//          System.arraycopy(floatData, 0, floatTemp, 0, dead);
//          System.arraycopy(floatData, dead+1, floatTemp, dead, (rowCount - dead) + 1);
                    System.arraycopy(columns[col], 0, floatTemp, 0, row)
                    System.arraycopy(columns[col], row + 1, floatTemp, row, rowCount - row - 1)
                    columns[col] = floatTemp
                }
                DOUBLE -> {
                    val doubleTemp = DoubleArray(rowCount - 1)
                    //          double[] doubleData = (double[]) columns[col];
//          System.arraycopy(doubleData, 0, doubleTemp, 0, dead);
//          System.arraycopy(doubleData, dead+1, doubleTemp, dead, (rowCount - dead) + 1);
                    System.arraycopy(columns[col], 0, doubleTemp, 0, row)
                    System.arraycopy(columns[col], row + 1, doubleTemp, row, rowCount - row - 1)
                    columns[col] = doubleTemp
                }
                STRING -> {
                    val stringTemp = arrayOfNulls<String>(rowCount - 1)
                    System.arraycopy(columns[col], 0, stringTemp, 0, row)
                    System.arraycopy(columns[col], row + 1, stringTemp, row, rowCount - row - 1)
                    columns[col] = stringTemp
                }
            }
        }
        rowCount--
    }

    /*
  public void setRow(int row, String[] pieces) {
    checkSize(row, pieces.length - 1);
    // pieces.length may be less than columns.length, so loop over pieces
    for (int col = 0; col < pieces.length; col++) {
      setRowCol(row, col, pieces[col]);
    }
  }


  protected void setRowCol(int row, int col, String piece) {
    switch (columnTypes[col]) {
    case STRING:
      String[] stringData = (String[]) columns[col];
      stringData[row] = piece;
      break;
    case INT:
      int[] intData = (int[]) columns[col];
      intData[row] = PApplet.parseInt(piece, missingInt);
      break;
    case LONG:
      long[] longData = (long[]) columns[col];
      try {
        longData[row] = Long.parseLong(piece);
      } catch (NumberFormatException nfe) {
        longData[row] = missingLong;
      }
      break;
    case FLOAT:
      float[] floatData = (float[]) columns[col];
      floatData[row] = PApplet.parseFloat(piece, missingFloat);
      break;
    case DOUBLE:
      double[] doubleData = (double[]) columns[col];
      try {
        doubleData[row] = Double.parseDouble(piece);
      } catch (NumberFormatException nfe) {
        doubleData[row] = missingDouble;
      }
      break;
    case CATEGORY:
      int[] indexData = (int[]) columns[col];
      indexData[row] = columnCategories[col].index(piece);
      break;
    default:
      throw new IllegalArgumentException("That's not a valid column type.");
    }
  }
  */

     fun setRow(row: Int, pieces: Array<Any?>) {
        ensureBounds(row, pieces!!.size - 1)
        // pieces.length may be less than columns.length, so loop over pieces
        for (col in pieces!!.indices) {
            setRowCol(row, col, pieces[col])
        }
    }

    protected fun setRowCol(row: Int, col: Int, piece: Any?) {
        when (columnTypes[col]) {
            STRING -> {
                val stringData = columns[col] as Array<String?>?
                if (piece == null) {
                    stringData!![row] = null
                    //        } else if (piece instanceof String) {
//          stringData[row] = (String) piece;
                } else {
                    // Calls toString() on the object, which is 'return this' for String
                    stringData!![row] = piece.toString()
                }
            }
            INT -> {
                val intData = columns[col] as IntArray?
                //intData[row] = PApplet.parseInt(piece, missingInt);
                if (piece == null) {
                    intData!![row] = missingInt
                } else if (piece is Int) {
                    intData!![row] = piece
                } else {
                    intData!![row] = PApplet.parseInt(piece.toString(), missingInt)
                }
            }
            LONG -> {
                val longData = columns[col] as LongArray?
                if (piece == null) {
                    longData!![row] = missingLong
                } else if (piece is Long) {
                    longData!![row] = piece
                } else {
                    try {
                        longData!![row] = piece.toString().toLong()
                    } catch (nfe: NumberFormatException) {
                        longData!![row] = missingLong
                    }
                }
            }
            FLOAT -> {
                val floatData = columns[col] as FloatArray?
                if (piece == null) {
                    floatData!![row] = missingFloat
                } else if (piece is Float) {
                    floatData!![row] = piece
                } else {
                    floatData!![row] = PApplet.parseFloat(piece.toString(), missingFloat)
                }
            }
            DOUBLE -> {
                val doubleData = columns[col] as DoubleArray?
                if (piece == null) {
                    doubleData!![row] = missingDouble
                } else if (piece is Double) {
                    doubleData!![row] = piece
                } else {
                    try {
                        doubleData!![row] = piece.toString().toDouble()
                    } catch (nfe: NumberFormatException) {
                        doubleData!![row] = missingDouble
                    }
                }
            }
            CATEGORY -> {
                val indexData = columns[col] as IntArray?
                if (piece == null) {
                    indexData!![row] = missingCategory
                } else {
                    val peace = piece.toString()
                    if (peace == missingString) {  // missingString might be null
                        indexData!![row] = missingCategory
                    } else {
                        indexData!![row] = columnCategories[col]!!.index(peace)
                    }
                }
            }
            else -> throw IllegalArgumentException("That's not a valid column type.")
        }
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * @webref table:method
     * @brief Gets a row from a table
     * @param row ID number of the row to get
     * @see Table.rows
     * @see Table.findRow
     * @see Table.findRows
     * @see Table.matchRow
     * @see Table.matchRows
     */
    fun getRow(row: Int): TableRow {
        return RowPointer(this, row)
    }

    /**
     * Note that this one iterator instance is shared by any calls to iterate
     * the rows of this table. This is very efficient, but not thread-safe.
     * If you want to iterate in a multi-threaded manner, don't use the iterator.
     *
     * @webref table:method
     * @brief Gets multiple rows from a table
     * @see Table.getRow
     * @see Table.findRow
     * @see Table.findRows
     * @see Table.matchRow
     * @see Table.matchRows
     */
    fun rows(): Iterable<TableRow?> {
        return object : Iterable<TableRow?> {
            override fun iterator(): Iterator<TableRow> {
                if (rowIterator == null) {
                    rowIterator = RowIterator(this@Table)
                } else {
                    rowIterator!!.reset()
                }
                return rowIterator!!
            }
        }
    }

    /**
     * @nowebref
     */
    fun rows(indices: IntArray): Iterable<TableRow?> {
        return object : Iterable<TableRow?> {
            override fun iterator(): Iterator<TableRow> {
                return RowIndexIterator(this@Table, indices)
            }
        }
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    inner class RowPointer(var table: Table, private var row: Int) : TableRow {
        fun setRow(row: Int) {
            this.row = row
        }

        override fun getString(column: Int): String? {
            return table.getString(row, column)
        }

        override fun getString(columnName: String?): String? {
            return table.getString(row, columnName)
        }

        override fun getInt(column: Int): Int {
            return table.getInt(row, column)
        }

        override fun getInt(columnName: String?): Int {
            return table.getInt(row, columnName)
        }

        override fun getLong(column: Int): Long {
            return table.getLong(row, column)
        }

        override fun getLong(columnName: String?): Long {
            return table.getLong(row, columnName)
        }

        override fun getFloat(column: Int): Float {
            return table.getFloat(row, column)
        }

        override fun getFloat(columnName: String?): Float {
            return table.getFloat(row, columnName)
        }

        override fun getDouble(column: Int): Double {
            return table.getDouble(row, column)
        }

        override fun getDouble(columnName: String?): Double {
            return table.getDouble(row, columnName)
        }

        override fun setString(column: Int, value: String?) {
            table.setString(row, column, value)
        }

        override fun setString(columnName: String?, value: String?) {
            table.setString(row, columnName, value)
        }

        override fun setInt(column: Int, value: Int) {
            table.setInt(row, column, value)
        }

        override fun setInt(columnName: String?, value: Int) {
            table.setInt(row, columnName, value)
        }

        override fun setLong(column: Int, value: Long) {
            table.setLong(row, column, value)
        }

        override fun setLong(columnName: String?, value: Long) {
            table.setLong(row, columnName, value)
        }

        override fun setFloat(column: Int, value: Float) {
            table.setFloat(row, column, value)
        }

        override fun setFloat(columnName: String?, value: Float) {
            table.setFloat(row, columnName, value)
        }

        override fun setDouble(column: Int, value: Double) {
            table.setDouble(row, column, value)
        }

        override fun setDouble(columnName: String?, value: Double) {
            table.setDouble(row, columnName, value)
        }

        override fun getColumnCount(): Int {
            return table.getColumnCount()
        }

        override fun getColumnType(columnName: String?): Int {
            return table.getColumnType(columnName)
        }

        override fun getColumnType(column: Int): Int {
            return table.getColumnType(column)
        }

        override fun getColumnTypes(): IntArray? {
            return table.getColumnTypes()
        }

        override fun getColumnTitle(column: Int): String? {
            return table.getColumnTitle(column)
        }

        override fun getColumnTitles(): Array<String?>? {
            return table.getColumnTitles()
        }

        override fun print() {
            write(PrintWriter(System.out))
        }

        override fun write(writer: PrintWriter?) {
            for (i in 0 until getColumnCount()) {
                if (i != 0) {
                    writer!!.print('\t')
                }
                writer!!.print(getString(i))
            }
        }

    }

   inner class RowIterator(var table: Table) : MutableIterator<TableRow> {
        var rp: RowPointer
        var row: Int

        override fun remove() {
            table.removeRow(row)
        }

        override fun next(): TableRow {
            rp.setRow(++row)
            return rp
        }

        override fun hasNext(): Boolean {
            return row + 1 < table.getRowCount()
        }

        fun reset() {
            row = -1
        }

        init {
            row = -1
            rp = RowPointer(table, row)
        }
    }

    inner class RowIndexIterator(var table: Table, var indices: IntArray) : MutableIterator<TableRow> {
        var rp: RowPointer
        var index: Int

        override fun remove() {
            table.removeRow(indices[index])
        }

        override fun next(): TableRow {
            rp.setRow(indices[++index])
            return rp
        }

        override fun hasNext(): Boolean {
            //return row+1 < table.getRowCount();
            return index + 1 < indices.size
        }

        fun reset() {
            index = -1
        }

        init {
            index = -1
            // just set to something arbitrary
            rp = RowPointer(table, -1)
        }
    }
    /*
  static public Iterator<TableRow> createIterator(final ResultSet rs) {
    return new Iterator<TableRow>() {
      boolean already;

      public boolean hasNext() {
        already = true;
        try {
          return rs.next();
        } catch (SQLException e) {
          throw new RuntimeException(e);
        }
      }


      public TableRow next() {
        if (!already) {
          try {
            rs.next();
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        } else {
          already = false;
        }

        return new TableRow() {
          public double getDouble(int column) {
            try {
              return rs.getDouble(column);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public double getDouble(String columnName) {
            try {
              return rs.getDouble(columnName);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public float getFloat(int column) {
            try {
              return rs.getFloat(column);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public float getFloat(String columnName) {
            try {
              return rs.getFloat(columnName);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public int getInt(int column) {
            try {
              return rs.getInt(column);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public int getInt(String columnName) {
            try {
              return rs.getInt(columnName);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public long getLong(int column) {
            try {
              return rs.getLong(column);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public long getLong(String columnName) {
            try {
              return rs.getLong(columnName);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public String getString(int column) {
            try {
              return rs.getString(column);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public String getString(String columnName) {
            try {
              return rs.getString(columnName);
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          }

          public void setString(int column, String value) { immutable(); }
          public void setString(String columnName, String value) { immutable(); }
          public void setInt(int column, int value) { immutable(); }
          public void setInt(String columnName, int value) { immutable(); }
          public void setLong(int column, long value) { immutable(); }
          public void setLong(String columnName, long value) { immutable(); }
          public void setFloat(int column, float value) { immutable(); }
          public void setFloat(String columnName, float value) { immutable(); }
          public void setDouble(int column, double value) { immutable(); }
          public void setDouble(String columnName, double value) { immutable(); }

          private void immutable() {
            throw new IllegalArgumentException("This TableRow cannot be modified.");
          }

          public int getColumnCount() {
            try {
              return rs.getMetaData().getColumnCount();
            } catch (SQLException e) {
              e.printStackTrace();
              return -1;
            }
          }


          public int getColumnType(String columnName) {
            // unimplemented
          }


          public int getColumnType(int column) {
            // unimplemented
          }

        };
      }

      public void remove() {
        throw new IllegalArgumentException("remove() not supported");
      }
    };
  }
  */

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * @webref table:method
     * @brief Get an integer value from the specified row and column
     * @param row ID number of the row to reference
     * @param column ID number of the column to reference
     * @see Table.getFloat
     * @see Table.getString
     * @see Table.getStringColumn
     * @see Table.setInt
     * @see Table.setFloat
     * @see Table.setString
     */
    fun getInt(row: Int, column: Int): Int {
        checkBounds(row, column)
        if (columnTypes[column] == INT ||
                columnTypes[column] == CATEGORY) {
            val intData = columns[column] as IntArray?
            return intData!![row]
        }
        val str = getString(row, column)
        return if (str == null || str == missingString) missingInt else PApplet.parseInt(str, missingInt)
    }

    /**
     * @param columnName title of the column to reference
     */
    fun getInt(row: Int, columnName: String?): Int {
        return getInt(row, getColumnIndex(columnName))
    }

    fun setMissingInt(value: Int) {
        missingInt = value
    }

    /**
     * @webref table:method
     * @brief Store an integer value in the specified row and column
     * @param row ID number of the target row
     * @param column ID number of the target column
     * @param value value to assign
     * @see Table.setFloat
     * @see Table.setString
     * @see Table.getInt
     * @see Table.getFloat
     * @see Table.getString
     * @see Table.getStringColumn
     */
    fun setInt(row: Int, column: Int, value: Int) {
        if (columnTypes[column] == STRING) {
            setString(row, column, value.toString())
        } else {
            ensureBounds(row, column)
            if (columnTypes[column] != INT &&
                    columnTypes[column] != CATEGORY) {
                throw IllegalArgumentException("Column $column is not an int column.")
            }
            val intData = columns[column] as IntArray?
            intData!![row] = value
        }
    }

    /**
     * @param columnName title of the target column
     */
    fun setInt(row: Int, columnName: String?, value: Int) {
        setInt(row, getColumnIndex(columnName), value)
    }

    fun getIntColumn(name: String?): IntArray? {
        val col = getColumnIndex(name)
        return if (col == -1) null else getIntColumn(col)
    }

    fun getIntColumn(col: Int): IntArray {
        val outgoing = IntArray(rowCount)
        for (row in 0 until rowCount) {
            outgoing[row] = getInt(row, col)
        }
        return outgoing
    }

    fun getIntRow(row: Int): IntArray {
        val outgoing = IntArray(columns.size)
        for (col in columns.indices) {
            outgoing[col] = getInt(row, col)
        }
        return outgoing
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun getLong(row: Int, column: Int): Long {
        checkBounds(row, column)
        if (columnTypes[column] == LONG) {
            val longData = columns[column] as LongArray?
            return longData!![row]
        }
        val str = getString(row, column)
        if (str == null || str == missingString) {
            return missingLong
        }
        return try {
            str.toLong()
        } catch (nfe: NumberFormatException) {
            missingLong
        }
    }

    fun getLong(row: Int, columnName: String?): Long {
        return getLong(row, getColumnIndex(columnName))
    }

    fun setMissingLong(value: Long) {
        missingLong = value
    }

    fun setLong(row: Int, column: Int, value: Long) {
        if (columnTypes[column] == STRING) {
            setString(row, column, value.toString())
        } else {
            ensureBounds(row, column)
            if (columnTypes[column] != LONG) {
                throw IllegalArgumentException("Column $column is not a 'long' column.")
            }
            val longData = columns[column] as LongArray?
            longData!![row] = value
        }
    }

    fun setLong(row: Int, columnName: String?, value: Long) {
        setLong(row, getColumnIndex(columnName), value)
    }

    fun getLongColumn(name: String?): LongArray? {
        val col = getColumnIndex(name)
        return if (col == -1) null else getLongColumn(col)
    }

    fun getLongColumn(col: Int): LongArray {
        val outgoing = LongArray(rowCount)
        for (row in 0 until rowCount) {
            outgoing[row] = getLong(row, col)
        }
        return outgoing
    }

    fun getLongRow(row: Int): LongArray {
        val outgoing = LongArray(columns.size)
        for (col in columns.indices) {
            outgoing[col] = getLong(row, col)
        }
        return outgoing
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * Get a float value from the specified row and column. If the value is null
     * or not parseable as a float, the "missing" value is returned. By default,
     * this is Float.NaN, but can be controlled with setMissingFloat().
     *
     * @webref table:method
     * @brief Get a float value from the specified row and column
     * @param row ID number of the row to reference
     * @param column ID number of the column to reference
     * @see Table.getInt
     * @see Table.getString
     * @see Table.getStringColumn
     * @see Table.setInt
     * @see Table.setFloat
     * @see Table.setString
     */
    fun getFloat(row: Int, column: Int): Float {
        checkBounds(row, column)
        if (columnTypes[column] == FLOAT) {
            val floatData = columns[column] as FloatArray?
            return floatData!![row]
        }
        val str = getString(row, column)
        return if (str == null || (str == missingString)) {
            missingFloat
        } else PApplet.parseFloat(str, missingFloat)
    }

    /**
     * @param columnName title of the column to reference
     */
    fun getFloat(row: Int, columnName: String?): Float {
        return getFloat(row, getColumnIndex(columnName))
    }

    fun setMissingFloat(value: Float) {
        missingFloat = value
    }

    /**
     * @webref table:method
     * @brief Store a float value in the specified row and column
     * @param row ID number of the target row
     * @param column ID number of the target column
     * @param value value to assign
     * @see Table.setInt
     * @see Table.setString
     * @see Table.getInt
     * @see Table.getFloat
     * @see Table.getString
     * @see Table.getStringColumn
     */
    fun setFloat(row: Int, column: Int, value: Float) {
        if (columnTypes[column] == STRING) {
            setString(row, column, value.toString())
        } else {
            ensureBounds(row, column)
            if (columnTypes[column] != FLOAT) {
                throw IllegalArgumentException("Column $column is not a float column.")
            }
            val longData = columns[column] as FloatArray?
            longData!![row] = value
        }
    }

    /**
     * @param columnName title of the target column
     */
    fun setFloat(row: Int, columnName: String?, value: Float) {
        setFloat(row, getColumnIndex(columnName), value)
    }

    fun getFloatColumn(name: String?): FloatArray? {
        val col = getColumnIndex(name)
        return if (col == -1) null else getFloatColumn(col)
    }

    fun getFloatColumn(col: Int): FloatArray {
        val outgoing = FloatArray(rowCount)
        for (row in 0 until rowCount) {
            outgoing[row] = getFloat(row, col)
        }
        return outgoing
    }

    fun getFloatRow(row: Int): FloatArray {
        val outgoing = FloatArray(columns.size)
        for (col in columns.indices) {
            outgoing[col] = getFloat(row, col)
        }
        return outgoing
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun getDouble(row: Int, column: Int): Double {
        checkBounds(row, column)
        if (columnTypes[column] == DOUBLE) {
            val doubleData = columns[column] as DoubleArray?
            return doubleData!![row]
        }
        val str = getString(row, column)
        if (str == null || str == missingString) {
            return missingDouble
        }
        return try {
            str.toDouble()
        } catch (nfe: NumberFormatException) {
            missingDouble
        }
    }

    fun getDouble(row: Int, columnName: String?): Double {
        return getDouble(row, getColumnIndex(columnName))
    }

    fun setMissingDouble(value: Double) {
        missingDouble = value
    }

    fun setDouble(row: Int, column: Int, value: Double) {
        if (columnTypes[column] == STRING) {
            setString(row, column, value.toString())
        } else {
            ensureBounds(row, column)
            if (columnTypes[column] != DOUBLE) {
                throw IllegalArgumentException("Column $column is not a 'double' column.")
            }
            val doubleData = columns[column] as DoubleArray?
            doubleData!![row] = value
        }
    }

    fun setDouble(row: Int, columnName: String?, value: Double) {
        setDouble(row, getColumnIndex(columnName), value)
    }

    fun getDoubleColumn(name: String?): DoubleArray? {
        val col = getColumnIndex(name)
        return if (col == -1) null else getDoubleColumn(col)
    }

    fun getDoubleColumn(col: Int): DoubleArray {
        val outgoing = DoubleArray(rowCount)
        for (row in 0 until rowCount) {
            outgoing[row] = getDouble(row, col)
        }
        return outgoing
    }

    fun getDoubleRow(row: Int): DoubleArray {
        val outgoing = DoubleArray(columns.size)
        for (col in columns.indices) {
            outgoing[col] = getDouble(row, col)
        }
        return outgoing
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    //public long getTimestamp(String rowName, int column) {
    //return getTimestamp(getRowIndex(rowName), column);
    //}

    /**
     * Returns the time in milliseconds by parsing a SQL Timestamp at this cell.
     */

    //  public long getTimestamp(int row, int column) {
    //    String str = get(row, column);
    //    java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf(str);
    //    return timestamp.getTime();
    //  }
    //  public long getExcelTimestamp(int row, int column) {
    //    return parseExcelTimestamp(get(row, column));
    //  }
    //  static protected DateFormat excelDateFormat;
    //  static public long parseExcelTimestamp(String timestamp) {
    //    if (excelDateFormat == null) {
    //      excelDateFormat = new SimpleDateFormat("MM/dd/yy HH:mm");
    //    }
    //    try {
    //      return excelDateFormat.parse(timestamp).getTime();
    //    } catch (ParseException e) {
    //      e.printStackTrace();
    //      return -1;
    //    }
    //  }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    //  public void setObject(int row, int column, Object value) {
    //    if (value == null) {
    //      data[row][column] = null;
    //    } else if (value instanceof String) {
    //      set(row, column, (String) value);
    //    } else if (value instanceof Float) {
    //      setFloat(row, column, ((Float) value).floatValue());
    //    } else if (value instanceof Integer) {
    //      setInt(row, column, ((Integer) value).intValue());
    //    } else {
    //      set(row, column, value.toString());
    //    }
    //  }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Get a String value from the table. If the row is longer than the table
     *
     * @webref table:method
     * @brief Get an String value from the specified row and column
     * @param row ID number of the row to reference
     * @param column ID number of the column to reference
     * @see Table.getInt
     * @see Table.getFloat
     * @see Table.getStringColumn
     * @see Table.setInt
     * @see Table.setFloat
     * @see Table.setString
     */
    fun getString(row: Int, column: Int): String? {
        checkBounds(row, column)
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String>?
            return stringData!![row]
        } else if (columnTypes[column] == CATEGORY) {
            val cat = getInt(row, column)
            return if (cat == missingCategory) {
                missingString
            } else columnCategories.get(column)!!.key(cat)
        } else if (columnTypes[column] == FLOAT) {
            if (java.lang.Float.isNaN(getFloat(row, column))) {
                return null
            }
        } else if (columnTypes[column] == DOUBLE) {
            if (java.lang.Double.isNaN(getFloat(row, column).toDouble())) {
                return null
            }
        }
        return java.lang.reflect.Array.get(columns[column], row).toString()
    }

    /**
     * @param columnName title of the column to reference
     */
    fun getString(row: Int, columnName: String?): String? {
        return getString(row, getColumnIndex(columnName))
    }

    /**
     * Treat entries with this string as "missing". Also used for categorial.
     */
    fun setMissingString(value: String?) {
        missingString = value
    }

    /**
     * @webref table:method
     * @brief Store a String value in the specified row and column
     * @param row ID number of the target row
     * @param column ID number of the target column
     * @param value value to assign
     * @see Table.setInt
     * @see Table.setFloat
     * @see Table.getInt
     * @see Table.getFloat
     * @see Table.getString
     * @see Table.getStringColumn
     */
    fun setString(row: Int, column: Int, value: String?) {
        ensureBounds(row, column)
        if (columnTypes[column] != STRING) {
            throw IllegalArgumentException("Column $column is not a String column.")
        }
        val stringData = columns[column] as Array<String?>?
        stringData!![row] = value
    }

    /**
     * @param columnName title of the target column
     */
    fun setString(row: Int, columnName: String?, value: String?) {
        val column = checkColumnIndex(columnName)
        setString(row, column, value)
    }

    /**
     * @webref table:method
     * @brief Gets all values in the specified column
     * @param columnName title of the column to search
     * @see Table.getInt
     * @see Table.getFloat
     * @see Table.getString
     * @see Table.setInt
     * @see Table.setFloat
     * @see Table.setString
     */
    fun getStringColumn(columnName: String?): Array<String?>? {
        val col = getColumnIndex(columnName)
        return if (col == -1) null else getStringColumn(col)
    }

    /**
     * @param column ID number of the column to search
     */
    fun getStringColumn(column: Int): Array<String?> {
        val outgoing = arrayOfNulls<String>(rowCount)
        for (i in 0 until rowCount) {
            outgoing[i] = getString(i, column)
        }
        return outgoing
    }

    fun getStringRow(row: Int): Array<String?> {
        val outgoing = arrayOfNulls<String>(columns.size)
        for (col in columns.indices) {
            outgoing[col] = getString(row, col)
        }
        return outgoing
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Return the row that contains the first String that matches.
     * @param value the String to match
     * @param column ID number of the column to search
     */
    fun findRowIndex(value: String?, column: Int): Int {
        checkColumn(column)
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String?>?
            if (value == null) {
                for (row in 0 until rowCount) {
                    if (stringData!![row] == null) return row
                }
            } else {
                for (row in 0 until rowCount) {
                    if (stringData!![row] != null && stringData[row] == value) {
                        return row
                    }
                }
            }
        } else {  // less efficient, includes conversion as necessary
            for (row in 0 until rowCount) {
                val str = getString(row, column)
                if (str == null) {
                    if (value == null) {
                        return row
                    }
                } else if (str == value) {
                    return row
                }
            }
        }
        return -1
    }

    /**
     * Return the row that contains the first String that matches.
     * @param value the String to match
     * @param columnName title of the column to search
     */
    fun findRowIndex(value: String?, columnName: String?): Int {
        return findRowIndex(value, getColumnIndex(columnName))
    }

    /**
     * Return a list of rows that contain the String passed in. If there are no
     * matches, a zero length array will be returned (not a null array).
     * @param value the String to match
     * @param column ID number of the column to search
     */
    fun findRowIndices(value: String?, column: Int): IntArray {
        val outgoing = IntArray(rowCount)
        var count = 0
        checkColumn(column)
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String?>?
            if (value == null) {
                for (row in 0 until rowCount) {
                    if (stringData!![row] == null) {
                        outgoing[count++] = row
                    }
                }
            } else {
                for (row in 0 until rowCount) {
                    if (stringData!![row] != null && stringData[row] == value) {
                        outgoing[count++] = row
                    }
                }
            }
        } else {  // less efficient, includes conversion as necessary
            for (row in 0 until rowCount) {
                val str = getString(row, column)
                if (str == null) {
                    if (value == null) {
                        outgoing[count++] = row
                    }
                } else if (str == value) {
                    outgoing[count++] = row
                }
            }
        }
        return PApplet.subset(outgoing, 0, count)
    }

    /**
     * Return a list of rows that contain the String passed in. If there are no
     * matches, a zero length array will be returned (not a null array).
     * @param value the String to match
     * @param columnName title of the column to search
     */
    fun findRowIndices(value: String?, columnName: String?): IntArray {
        return findRowIndices(value, getColumnIndex(columnName))
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * @webref table:method
     * @brief Finds a row that contains the given value
     * @param value the value to match
     * @param column ID number of the column to search
     * @see Table.getRow
     * @see Table.rows
     * @see Table.findRows
     * @see Table.matchRow
     * @see Table.matchRows
     */
    fun findRow(value: String?, column: Int): TableRow? {
        val row = findRowIndex(value, column)
        return if (row == -1) null else RowPointer(this, row)
    }

    /**
     * @param columnName title of the column to search
     */
    fun findRow(value: String?, columnName: String?): TableRow? {
        return findRow(value, getColumnIndex(columnName))
    }

    /**
     * @webref table:method
     * @brief Finds multiple rows that contain the given value
     * @param value the value to match
     * @param column ID number of the column to search
     * @see Table.getRow
     * @see Table.rows
     * @see Table.findRow
     * @see Table.matchRow
     * @see Table.matchRows
     */
    fun findRows(value: String?, column: Int): Iterable<TableRow?> {
        return object : Iterable<TableRow?> {
            override fun iterator(): Iterator<TableRow> {
                return findRowIterator(value, column)
            }
        }
    }

    /**
     * @param columnName title of the column to search
     */
    fun findRows(value: String?, columnName: String?): Iterable<TableRow?> {
        return findRows(value, getColumnIndex(columnName))
    }

    /**
     * @brief Finds multiple rows that contain the given value
     * @param value the value to match
     * @param column ID number of the column to search
     */
    fun findRowIterator(value: String?, column: Int): Iterator<TableRow> {
        return RowIndexIterator(this, findRowIndices(value, column))
    }

    /**
     * @param columnName title of the column to search
     */
    fun findRowIterator(value: String?, columnName: String?): Iterator<TableRow> {
        return findRowIterator(value, getColumnIndex(columnName))
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * Return the row that contains the first String that matches.
     * @param regexp the String to match
     * @param column ID number of the column to search
     */
    fun matchRowIndex(regexp: String?, column: Int): Int {
        checkColumn(column)
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String?>?
            for (row in 0 until rowCount) {
                if (stringData!![row] != null &&
                        PApplet.match(stringData[row], regexp) != null) {
                    return row
                }
            }
        } else {  // less efficient, includes conversion as necessary
            for (row in 0 until rowCount) {
                val str = getString(row, column)
                if (str != null &&
                        PApplet.match(str, regexp) != null) {
                    return row
                }
            }
        }
        return -1
    }

    /**
     * Return the row that contains the first String that matches.
     * @param what the String to match
     * @param columnName title of the column to search
     */
    fun matchRowIndex(what: String?, columnName: String?): Int {
        return matchRowIndex(what, getColumnIndex(columnName))
    }

    /**
     * Return a list of rows that contain the String passed in. If there are no
     * matches, a zero length array will be returned (not a null array).
     * @param regexp the String to match
     * @param column ID number of the column to search
     */
    fun matchRowIndices(regexp: String?, column: Int): IntArray {
        val outgoing = IntArray(rowCount)
        var count = 0
        checkColumn(column)
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String?>?
            for (row in 0 until rowCount) {
                if (stringData!![row] != null &&
                        PApplet.match(stringData[row], regexp) != null) {
                    outgoing[count++] = row
                }
            }
        } else {  // less efficient, includes conversion as necessary
            for (row in 0 until rowCount) {
                val str = getString(row, column)
                if (str != null &&
                        PApplet.match(str, regexp) != null) {
                    outgoing[count++] = row
                }
            }
        }
        return PApplet.subset(outgoing, 0, count)
    }

    /**
     * Return a list of rows that match the regex passed in. If there are no
     * matches, a zero length array will be returned (not a null array).
     * @param what the String to match
     * @param columnName title of the column to search
     */
    fun matchRowIndices(what: String?, columnName: String?): IntArray {
        return matchRowIndices(what, getColumnIndex(columnName))
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * @webref table:method
     * @brief Finds a row that matches the given expression
     * @param regexp the regular expression to match
     * @param column ID number of the column to search
     * @see Table.getRow
     * @see Table.rows
     * @see Table.findRow
     * @see Table.findRows
     * @see Table.matchRows
     */
    fun matchRow(regexp: String?, column: Int): TableRow? {
        val row = matchRowIndex(regexp, column)
        return if (row == -1) null else RowPointer(this, row)
    }

    /**
     * @param columnName title of the column to search
     */
    fun matchRow(regexp: String?, columnName: String?): TableRow? {
        return matchRow(regexp, getColumnIndex(columnName))
    }

    /**
     * @webref table:method
     * @brief Finds multiple rows that match the given expression
     * @param regexp the regular expression to match
     * @param column ID number of the column to search
     * @see Table.getRow
     * @see Table.rows
     * @see Table.findRow
     * @see Table.findRows
     * @see Table.matchRow
     */
    fun matchRows(regexp: String?, column: Int): Iterable<TableRow?> {
        return object : Iterable<TableRow?> {
            override fun iterator(): Iterator<TableRow> {
                return matchRowIterator(regexp, column)
            }
        }
    }

    /**
     * @param columnName title of the column to search
     */
    fun matchRows(regexp: String?, columnName: String?): Iterable<TableRow?> {
        return matchRows(regexp, getColumnIndex(columnName))
    }

    /**
     * @webref table:method
     * @brief Finds multiple rows that match the given expression
     * @param value the regular expression to match
     * @param column ID number of the column to search
     */
    fun matchRowIterator(value: String?, column: Int): Iterator<TableRow> {
        return RowIndexIterator(this, matchRowIndices(value, column))
    }

    /**
     * @param columnName title of the column to search
     */
    fun matchRowIterator(value: String?, columnName: String?): Iterator<TableRow> {
        return matchRowIterator(value, getColumnIndex(columnName))
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * Replace a String with another. Set empty entries null by using
     * replace("", null) or use replace(null, "") to go the other direction.
     * If this is a typed table, only String columns will be modified.
     * @param orig
     * @param replacement
     */
    fun replace(orig: String?, replacement: String?) {
        for (col in columns.indices) {
            replace(orig, replacement, col)
        }
    }

    fun replace(orig: String?, replacement: String?, col: Int) {
        if (columnTypes[col] == STRING) {
            val stringData = columns[col] as Array<String?>?
            if (orig != null) {
                for (row in 0 until rowCount) {
                    if (orig == stringData!![row]) {
                        stringData[row] = replacement
                    }
                }
            } else {  // null is a special case (and faster anyway)
                for (row in 0 until rowCount) {
                    if (stringData!![row] == null) {
                        stringData[row] = replacement
                    }
                }
            }
        }
    }

    fun replace(orig: String?, replacement: String?, colName: String?) {
        replace(orig, replacement, getColumnIndex(colName))
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun replaceAll(regex: String, replacement: String?) {
        for (col in columns.indices) {
            replaceAll(regex, replacement, col)
        }
    }

    fun replaceAll(regex: String, replacement: String?, column: Int) {
        checkColumn(column)
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String?>
            for (row in 0 until rowCount) {
                if (stringData[row] != null) {
                    stringData[row] = stringData[row]!!.replace(regex.toRegex(), replacement!!)
                }
            }
        } else {
            throw IllegalArgumentException("replaceAll() can only be used on String columns")
        }
    }

    /**
     * Run String.replaceAll() on all entries in a column.
     * Only works with columns that are already String values.
     * @param regex the String to match
     * @param columnName title of the column to search
     */
    fun replaceAll(regex: String, replacement: String?, columnName: String?) {
        replaceAll(regex, replacement, getColumnIndex(columnName))
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * Remove any of the specified characters from the entire table.
     *
     * @webref table:method
     * @brief Removes characters from the table
     * @param tokens a list of individual characters to be removed
     * @see Table.trim
     */
    fun removeTokens(tokens: String) {
        for (col in 0 until getColumnCount()) {
            removeTokens(tokens, col)
        }
    }

    /**
     * Removed any of the specified characters from a column. For instance,
     * the following code removes dollar signs and commas from column 2:
     * <pre>
     * table.removeTokens(",$", 2);
    </pre> *
     *
     * @param column ID number of the column to process
     */
    fun removeTokens(tokens: String, column: Int) {
        for (row in 0 until rowCount) {
            val s = getString(row, column)
            if (s != null) {
                val c = s.toCharArray()
                var index = 0
                for (j in c.indices) {
                    if (tokens.indexOf(c[j]) == -1) {
                        if (index != j) {
                            c[index] = c[j]
                        }
                        index++
                    }
                }
                if (index != c.size) {
                    setString(row, column, String(c, 0, index))
                }
            }
        }
    }

    /**
     * @param columnName title of the column to process
     */
    fun removeTokens(tokens: String, columnName: String?) {
        removeTokens(tokens, getColumnIndex(columnName))
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * @webref table:method
     * @brief Trims whitespace from values
     * @see Table.removeTokens
     */
    fun trim() {
        columnTitles = PApplet.trim(columnTitles)
        for (col in 0 until getColumnCount()) {
            trim(col)
        }
        // remove empty columns
        var lastColumn = getColumnCount() - 1
        //while (isEmptyColumn(lastColumn) && lastColumn >= 0) {
        while (isEmptyArray(getStringColumn(lastColumn)) && lastColumn >= 0) {
            lastColumn--
        }
        setColumnCount(lastColumn + 1)

        // trim() works from both sides
        while (getColumnCount() > 0 && isEmptyArray(getStringColumn(0))) {
            removeColumn(0)
        }

        // remove empty rows (starting from the end)
        var lastRow = lastRowIndex()
        //while (isEmptyRow(lastRow) && lastRow >= 0) {
        while (isEmptyArray(getStringRow(lastRow)) && lastRow >= 0) {
            lastRow--
        }
        setRowCount(lastRow + 1)
        while (getRowCount() > 0 && isEmptyArray(getStringRow(0))) {
            removeRow(0)
        }
    }

    protected fun isEmptyArray(contents: Array<String?>): Boolean {
        for (entry in contents) {
            if (entry != null && entry.length > 0) {
                return false
            }
        }
        return true
    }
    /*
  protected boolean isEmptyColumn(int column) {
    String[] contents = getStringColumn(column);
    for (String entry : contents) {
      if (entry != null && entry.length() > 0) {
        return false;
      }
    }
    return true;
  }


  protected boolean isEmptyRow(int row) {
    String[] contents = getStringRow(row);
    for (String entry : contents) {
      if (entry != null && entry.length() > 0) {
        return false;
      }
    }
    return true;
  }
  */
    /**
     * @param column ID number of the column to trim
     */
    fun trim(column: Int) {
        if (columnTypes[column] == STRING) {
            val stringData = columns[column] as Array<String?>?
            for (row in 0 until rowCount) {
                if (stringData!![row] != null) {
                    stringData[row] = PApplet.trim(stringData[row])
                }
            }
        }
    }

    /**
     * @param columnName title of the column to trim
     */
    fun trim(columnName: String?) {
        trim(getColumnIndex(columnName))
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /** Make sure this is a legit column, and if not, expand the table.  */
    protected fun ensureColumn(col: Int) {
        if (col >= columns.size) {
            setColumnCount(col + 1)
        }
    }

    /** Make sure this is a legit row, and if not, expand the table.  */
    protected fun ensureRow(row: Int) {
        if (row >= rowCount) {
            setRowCount(row + 1)
        }
    }

    /** Make sure this is a legit row and column. If not, expand the table.  */
    protected fun ensureBounds(row: Int, col: Int) {
        ensureRow(row)
        ensureColumn(col)
    }

    /** Throw an error if this row doesn't exist.  */
    protected fun checkRow(row: Int) {
        if (row < 0 || row >= rowCount) {
            throw ArrayIndexOutOfBoundsException("Row $row does not exist.")
        }
    }

    /** Throw an error if this column doesn't exist.  */
    protected fun checkColumn(column: Int) {
        if (column < 0 || column >= columns.size) {
            throw ArrayIndexOutOfBoundsException("Column $column does not exist.")
        }
    }

    /** Throw an error if this entry is out of bounds.  */
    protected fun checkBounds(row: Int, column: Int) {
        checkRow(row)
        checkColumn(column)
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    class HashMapBlows {
        var dataToIndex = HashMap<String?, Int>()
        var indexToData = ArrayList<String?>()

        constructor() {}
        constructor(input: DataInputStream) {
            read(input)
        }

        /** gets the index, and creates one if it doesn't already exist.  */
        fun index(key: String?): Int {
            val value = dataToIndex[key]
            if (value != null) {
                return value
            }
            val v = dataToIndex.size
            dataToIndex[key] = v
            indexToData.add(key)
            return v
        }

        fun key(index: Int): String? {
            return indexToData[index]
        }

        fun hasCategory(index: Int): Boolean {
            return index < size() && indexToData[index] != null
        }

        fun setCategory(index: Int, name: String?) {
            while (indexToData.size <= index) {
                indexToData.add(null)
            }
            indexToData[index] = name
            dataToIndex[name] = index
        }

        fun size(): Int {
            return dataToIndex.size
        }

        @Throws(IOException::class)
        fun write(output: DataOutputStream) {
            output.writeInt(size())
            for (str in indexToData) {
                output.writeUTF(str)
            }
        }

        @Throws(IOException::class)
        fun writeln(writer: PrintWriter) {
            for (str in indexToData) {
                writer.println(str)
            }
            writer.flush()
            writer.close()
        }

        @Throws(IOException::class)
        fun read(input: DataInputStream) {
            val count = input.readInt()
            //System.out.println("found " + count + " entries in category map");
            dataToIndex = HashMap(count)
            for (i in 0 until count) {
                val str = input.readUTF()
                //System.out.println(i + " " + str);
                dataToIndex[str] = i
                indexToData.add(str)
            }
        }
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    //  class HashMapSucks extends HashMap<String,Integer> {
    //
    //    void increment(String what) {
    //      Integer value = get(what);
    //      if (value == null) {
    //        put(what, 1);
    //      } else {
    //        put(what, value + 1);
    //      }
    //    }
    //
    //    void check(String what) {
    //      if (get(what) == null) {
    //        put(what, 0);
    //      }
    //    }
    //  }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Sorts (orders) a table based on the values in a column.
     *
     * @webref table:method
     * @brief Orders a table based on the values in a column
     * @param columnName the name of the column to sort
     * @see Table.trim
     */
    fun sort(columnName: String?) {
        sort(getColumnIndex(columnName), false)
    }

    /**
     * @param column the column ID, e.g. 0, 1, 2
     */
    fun sort(column: Int) {
        sort(column, false)
    }

    fun sortReverse(columnName: String?) {
        sort(getColumnIndex(columnName), true)
    }

    fun sortReverse(column: Int) {
        sort(column, true)
    }

    protected fun sort(column: Int, reverse: Boolean) {
        val order = fromRange(getRowCount()).array()
        val s: Sort = object : Sort() {
            override fun size(): Int {
                return getRowCount()
            }

            override fun compare(index1: Int, index2: Int): Int {
                val a = if (reverse) order[index2] else order[index1]
                val b = if (reverse) order[index1] else order[index2]
                return when (getColumnType(column)) {
                    INT -> getInt(a, column) - getInt(b, column)
                    LONG -> {
                        val diffl = getLong(a, column) - getLong(b, column)
                        if (diffl == 0L) 0 else if (diffl < 0) -1 else 1
                    }
                    FLOAT -> {
                        val difff = getFloat(a, column) - getFloat(b, column)
                        if (difff == 0f) 0 else if (difff < 0) -1 else 1
                    }
                    DOUBLE -> {
                        val diffd = getDouble(a, column) - getDouble(b, column)
                        if (diffd == 0.0) 0 else if (diffd < 0) -1 else 1
                    }
                    STRING -> {
                        var string1 = getString(a, column)
                        if (string1 == null) {
                            string1 = "" // avoid NPE when cells are left empty
                        }
                        var string2 = getString(b, column)
                        if (string2 == null) {
                            string2 = ""
                        }
                        string1.compareTo(string2, ignoreCase = true)
                    }
                    CATEGORY -> getInt(a, column) - getInt(b, column)
                    else -> throw IllegalArgumentException("Invalid column type: " + getColumnType(column))
                }
            }

            override fun swap(a: Int, b: Int) {
                val temp = order[a]
                order[a] = order[b]
                order[b] = temp
            }
        }
        s.run()

        //Object[] newColumns = new Object[getColumnCount()];
        for (col in 0 until getColumnCount()) {
            when (getColumnType(col)) {
                INT, CATEGORY -> {
                    val oldInt = columns[col] as IntArray?
                    val newInt = IntArray(rowCount)
                    var row = 0
                    while (row < getRowCount()) {
                        newInt[row] = oldInt!![order[row]]
                        row++
                    }
                    columns[col] = newInt
                }
                LONG -> {
                    val oldLong = columns[col] as LongArray?
                    val newLong = LongArray(rowCount)
                    var row = 0
                    while (row < getRowCount()) {
                        newLong[row] = oldLong!![order[row]]
                        row++
                    }
                    columns[col] = newLong
                }
                FLOAT -> {
                    val oldFloat = columns[col] as FloatArray?
                    val newFloat = FloatArray(rowCount)
                    var row = 0
                    while (row < getRowCount()) {
                        newFloat[row] = oldFloat!![order[row]]
                        row++
                    }
                    columns[col] = newFloat
                }
                DOUBLE -> {
                    val oldDouble = columns[col] as DoubleArray?
                    val newDouble = DoubleArray(rowCount)
                    var row = 0
                    while (row < getRowCount()) {
                        newDouble[row] = oldDouble!![order[row]]
                        row++
                    }
                    columns[col] = newDouble
                }
                STRING -> {
                    val oldString = columns[col] as Array<String>?
                    val newString = arrayOfNulls<String>(rowCount)
                    var row = 0
                    while (row < getRowCount()) {
                        newString[row] = oldString!![order[row]]
                        row++
                    }
                    columns[col] = newString
                }
            }
        }
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun getUnique(columnName: String?): Array<String?> {
        return getUnique(getColumnIndex(columnName))
    }

    fun getUnique(column: Int): Array<String?> {
        val list = StringList(getStringColumn(column))
        return list.unique
    }

    fun getTally(columnName: String?): IntDict {
        return getTally(getColumnIndex(columnName))
    }

    fun getTally(column: Int): IntDict {
        val list = StringList(getStringColumn(column))
        return list.tally
    }

    fun getOrder(columnName: String?): IntDict {
        return getOrder(getColumnIndex(columnName))
    }

    fun getOrder(column: Int): IntDict {
        val list = StringList(getStringColumn(column))
        return list.order
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun getIntList(columnName: String?): IntList {
        return IntList(getIntColumn(columnName))
    }

    fun getIntList(column: Int): IntList {
        return IntList(getIntColumn(column))
    }

    fun getFloatList(columnName: String?): FloatList {
        return FloatList(getFloatColumn(columnName))
    }

    fun getFloatList(column: Int): FloatList {
        return FloatList(getFloatColumn(column))
    }

    fun getStringList(columnName: String?): StringList {
        return StringList(getStringColumn(columnName))
    }

    fun getStringList(column: Int): StringList {
        return StringList(getStringColumn(column))
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    fun getIntDict(keyColumnName: String?, valueColumnName: String?): IntDict {
        return IntDict(getStringColumn(keyColumnName)!!,
                getIntColumn(valueColumnName)!!)
    }

    fun getIntDict(keyColumn: Int, valueColumn: Int): IntDict {
        return IntDict(getStringColumn(keyColumn),
                getIntColumn(valueColumn))
    }

    fun getFloatDict(keyColumnName: String?, valueColumnName: String?): FloatDict {
        return FloatDict(getStringColumn(keyColumnName)!!,
                getFloatColumn(valueColumnName)!!)
    }

    fun getFloatDict(keyColumn: Int, valueColumn: Int): FloatDict {
        return FloatDict(getStringColumn(keyColumn),
                getFloatColumn(valueColumn))
    }

    fun getStringDict(keyColumnName: String?, valueColumnName: String?): StringDict {
        return StringDict(getStringColumn(keyColumnName)!!,
                getStringColumn(valueColumnName)!!)
    }

    fun getStringDict(keyColumn: Int, valueColumn: Int): StringDict {
        return StringDict(getStringColumn(keyColumn),
                getStringColumn(valueColumn))
    }

    fun getRowMap(columnName: String?): Map<String?, TableRow>? {
        val col = getColumnIndex(columnName)
        return if (col == -1) null else getRowMap(col)
    }

    /**
     * Return a mapping that connects the entry from a column back to the row
     * from which it came. For instance:
     * <pre>
     * Table t = loadTable("country-data.tsv", "header");
     * // use the contents of the 'country' column to index the table
     * Map<String></String>, TableRow> lookup = t.getRowMap("country");
     * // get the row that has "us" in the "country" column:
     * TableRow usRow = lookup.get("us");
     * // get an entry from the 'population' column
     * int population = usRow.getInt("population");
    </pre> *
     */
    fun getRowMap(column: Int): Map<String?, TableRow>? {
        val outgoing: MutableMap<String?, TableRow>? = HashMap()
        for (row in 0 until getRowCount()) {
            val id = getString(row, column)
            outgoing?.set(id, RowPointer(this, row))
        }
        //    for (TableRow row : rows()) {
//      String id = row.getString(column);
//      outgoing.put(id, row);
//    }
        return outgoing
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    //  /**
    //   * Return an object that maps the String values in one column back to the
    //   * row from which they came. For instance, if the "name" of each row is
    //   * found in the first column, getColumnRowLookup(0) would return an object
    //   * that would map each name back to its row.
    //   */
    //  protected HashMap<String,Integer> getRowLookup(int col) {
    //    HashMap<String,Integer> outgoing = new HashMap<String, Integer>();
    //    for (int row = 0; row < getRowCount(); row++) {
    //      outgoing.put(getString(row, col), row);
    //    }
    //    return outgoing;
    //  }
    // incomplete, basically this is silly to write all this repetitive code when
    // it can be implemented in ~3 lines of code...
    //  /**
    //   * Return an object that maps the data from one column to the data of found
    //   * in another column.
    //   */
    //  public HashMap<?,?> getLookup(int col1, int col2) {
    //    HashMap outgoing = null;
    //
    //    switch (columnTypes[col1]) {
    //      case INT: {
    //        if (columnTypes[col2] == INT) {
    //          outgoing = new HashMap<Integer, Integer>();
    //          for (int row = 0; row < getRowCount(); row++) {
    //            outgoing.put(getInt(row, col1), getInt(row, col2));
    //          }
    //        } else if (columnTypes[col2] == LONG) {
    //          outgoing = new HashMap<Integer, Long>();
    //          for (int row = 0; row < getRowCount(); row++) {
    //            outgoing.put(getInt(row, col1), getLong(row, col2));
    //          }
    //        } else if (columnTypes[col2] == FLOAT) {
    //          outgoing = new HashMap<Integer, Float>();
    //          for (int row = 0; row < getRowCount(); row++) {
    //            outgoing.put(getInt(row, col1), getFloat(row, col2));
    //          }
    //        } else if (columnTypes[col2] == DOUBLE) {
    //          outgoing = new HashMap<Integer, Double>();
    //          for (int row = 0; row < getRowCount(); row++) {
    //            outgoing.put(getInt(row, col1), getDouble(row, col2));
    //          }
    //        } else if (columnTypes[col2] == STRING) {
    //          outgoing = new HashMap<Integer, String>();
    //          for (int row = 0; row < getRowCount(); row++) {
    //            outgoing.put(getInt(row, col1), get(row, col2));
    //          }
    //        }
    //        break;
    //      }
    //    }
    //    return outgoing;
    //  }
    //  public StringIntPairs getColumnRowLookup(int col) {
    //    StringIntPairs sc = new StringIntPairs();
    //    String[] column = getStringColumn(col);
    //    for (int i = 0; i < column.length; i++) {
    //      sc.set(column[i], i);
    //    }
    //    return sc;
    //  }
    //  public String[] getUniqueEntries(int column) {
    ////    HashMap indices = new HashMap();
    ////    for (int row = 0; row < rowCount; row++) {
    ////      indices.put(data[row][column], this);  // 'this' is a dummy
    ////    }
    //    StringIntPairs sc = getStringCount(column);
    //    return sc.keys();
    //  }
    //
    //
    //  public StringIntPairs getStringCount(String columnName) {
    //    return getStringCount(getColumnIndex(columnName));
    //  }
    //
    //
    //  public StringIntPairs getStringCount(int column) {
    //    StringIntPairs outgoing = new StringIntPairs();
    //    for (int row = 0; row < rowCount; row++) {
    //      String entry = data[row][column];
    //      if (entry != null) {
    //        outgoing.increment(entry);
    //      }
    //    }
    //    return outgoing;
    //  }
    //
    //
    //  /**
    //   * Return an object that maps the String values in one column back to the
    //   * row from which they came. For instance, if the "name" of each row is
    //   * found in the first column, getColumnRowLookup(0) would return an object
    //   * that would map each name back to its row.
    //   */
    //  public StringIntPairs getColumnRowLookup(int col) {
    //    StringIntPairs sc = new StringIntPairs();
    //    String[] column = getStringColumn(col);
    //    for (int i = 0; i < column.length; i++) {
    //      sc.set(column[i], i);
    //    }
    //    return sc;
    //  }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    // TODO naming/whether to include
    protected fun createSubset(rowSubset: IntArray): Table {
        val newbie = Table()
        newbie.setColumnTitles(columnTitles) // also sets columns.length
        newbie.columnTypes = columnTypes
        newbie.setRowCount(rowSubset.size)
        for (i in rowSubset.indices) {
            val row = rowSubset[i]
            for (col in columns.indices) {
                when (columnTypes[col]) {
                    STRING -> newbie.setString(i, col, getString(row, col))
                    INT -> newbie.setInt(i, col, getInt(row, col))
                    LONG -> newbie.setLong(i, col, getLong(row, col))
                    FLOAT -> newbie.setFloat(i, col, getFloat(row, col))
                    DOUBLE -> newbie.setDouble(i, col, getDouble(row, col))
                }
            }
        }
        return newbie
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    /**
     * Searches the entire table for float values.
     * Returns missing float (Float.NaN by default) if no valid numbers found.
     */
    protected fun getMaxFloat(): Float {
        var found = false
        var max = PConstants.MIN_FLOAT
        for (row in 0 until getRowCount()) {
            for (col in 0 until getColumnCount()) {
                val value = getFloat(row, col)
                if (!java.lang.Float.isNaN(value)) {  // TODO no, this should be comparing to the missing value
                    if (!found) {
                        max = value
                        found = true
                    } else if (value > max) {
                        max = value
                    }
                }
            }
        }
        return if (found) max else missingFloat
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    // converts a TSV or CSV file to binary.. do not use
    @Throws(IOException::class)
    protected fun convertBasic(reader: BufferedReader?, tsv: Boolean,
                               outputFile: File?) {
        val fos = FileOutputStream(outputFile)
        val bos = BufferedOutputStream(fos, 16384)
        val output = DataOutputStream(bos)
        output.writeInt(0) // come back for row count
        output.writeInt(getColumnCount())
        if (columnTitles != null) {
            output.writeBoolean(true)
            for (title: String? in columnTitles!!) {
                output.writeUTF(title)
            }
        } else {
            output.writeBoolean(false)
        }
        for (type: Int in columnTypes) {
            output.writeInt(type)
        }
        var line: String? = null
        //setRowCount(1);
        var prev = -1
        var row = 0
        while ((reader!!.readLine().also { line = it }) != null) {
            convertRow(output, if (tsv) PApplet.split(line, '\t') else splitLineCSV(line, reader))
            row++
            if (row % 10000 == 0) {
                if (row < rowCount) {
                    val pct = (100 * row) / rowCount
                    if (pct != prev) {
                        println("$pct%")
                        prev = pct
                    }
                }
                //        try {
//          Thread.sleep(5);
//        } catch (InterruptedException e) {
//          e.printStackTrace();
//        }
            }
        }
        // shorten or lengthen based on what's left
//    if (row != getRowCount()) {
//      setRowCount(row);
//    }

        // has to come afterwards, since these tables get built out during the conversion
        var col = 0
        for (hmb: HashMapBlows? in columnCategories) {
            if (hmb == null) {
                output.writeInt(0)
            } else {
                hmb.write(output)
                hmb.writeln(PApplet.createWriter(File(columnTitles!!.get(col).toString() + ".categories")))
                //        output.writeInt(hmb.size());
//        for (Map.Entry<String,Integer> e : hmb.entrySet()) {
//          output.writeUTF(e.getKey());
//          output.writeInt(e.getValue());
//        }
            }
            col++
        }
        output.flush()
        output.close()

        // come back and write the row count
        val raf = RandomAccessFile(outputFile, "rw")
        raf.writeInt(rowCount)
        raf.close()
    }

    @Throws(IOException::class)
    protected fun convertRow(output: DataOutputStream, pieces: Array<String?>) {
        if (pieces.size > getColumnCount()) {
            throw IllegalArgumentException("Row with too many columns: " +
                    PApplet.join(pieces, ","))
        }
        // pieces.length may be less than columns.length, so loop over pieces
        for (col in pieces.indices) {
            when (columnTypes[col]) {
                STRING -> output.writeUTF(pieces[col])
                INT -> output.writeInt(PApplet.parseInt(pieces[col], missingInt))
                LONG -> try {
                    output.writeLong(pieces[col]!!.toLong())
                } catch (nfe: NumberFormatException) {
                    output.writeLong(missingLong)
                }
                FLOAT -> output.writeFloat(PApplet.parseFloat(pieces[col], missingFloat))
                DOUBLE -> try {
                    output.writeDouble(pieces[col]!!.toDouble())
                } catch (nfe: NumberFormatException) {
                    output.writeDouble(missingDouble)
                }
                CATEGORY -> {
                    val peace = pieces[col]
                    if (peace == missingString) {
                        output.writeInt(missingCategory)
                    } else {
                        output.writeInt(columnCategories[col]!!.index(peace))
                    }
                }
            }
        }
        for (col in pieces.size until getColumnCount()) {
            when (columnTypes[col]) {
                STRING -> output.writeUTF("")
                INT -> output.writeInt(missingInt)
                LONG -> output.writeLong(missingLong)
                FLOAT -> output.writeFloat(missingFloat)
                DOUBLE -> output.writeDouble(missingDouble)
                CATEGORY -> output.writeInt(missingCategory)
            }
        }
    }
    /*
  private void convertRowCol(DataOutputStream output, int row, int col, String piece) {
    switch (columnTypes[col]) {
      case STRING:
        String[] stringData = (String[]) columns[col];
        stringData[row] = piece;
        break;
      case INT:
        int[] intData = (int[]) columns[col];
        intData[row] = PApplet.parseInt(piece, missingInt);
        break;
      case LONG:
        long[] longData = (long[]) columns[col];
        try {
          longData[row] = Long.parseLong(piece);
        } catch (NumberFormatException nfe) {
          longData[row] = missingLong;
        }
        break;
      case FLOAT:
        float[] floatData = (float[]) columns[col];
        floatData[row] = PApplet.parseFloat(piece, missingFloat);
        break;
      case DOUBLE:
        double[] doubleData = (double[]) columns[col];
        try {
          doubleData[row] = Double.parseDouble(piece);
        } catch (NumberFormatException nfe) {
          doubleData[row] = missingDouble;
        }
        break;
      default:
        throw new IllegalArgumentException("That's not a valid column type.");
    }
  }
  */
    /** Make a copy of the current table  */
    fun copy(): Table {
        return Table(rows())
    }

    fun write(writer: PrintWriter) {
        writeTSV(writer)
    }

    fun print() {
        writeTSV(PrintWriter(System.out))
    }

    companion object {
        // accessible for advanced users
        const val STRING = 0
        const val INT = 1
        const val LONG = 2
        const val FLOAT = 3
        const val DOUBLE = 4
        const val CATEGORY = 5

        /*
  protected String checkOptions(File file, String options) throws IOException {
    String extension = null;
    String filename = file.getName();
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex != -1) {
      extension = filename.substring(dotIndex + 1).toLowerCase();
      if (!extension.equals("csv") &&
          !extension.equals("tsv") &&
          !extension.equals("html") &&
          !extension.equals("bin")) {
        // ignore extension
        extension = null;
      }
    }
    if (extension == null) {
      if (options == null) {
        throw new IOException("This table filename has no extension, and no options are set.");
      }
    } else {  // extension is not null
      if (options == null) {
        options = extension;
      } else {
        // prepend the extension, it will be overridden if there's an option for it.
        options = extension + "," + options;
      }
    }
    return options;
  }
  */
        val loadExtensions = arrayOf("csv", "tsv", "ods", "bin")
        val saveExtensions = arrayOf("csv", "tsv", "ods", "bin", "html")
        fun extensionOptions(loading: Boolean, filename: String?, options: String?): String? {
            val extension = PApplet.checkExtension(filename)
            if (extension != null) {
                for (possible in if (loading) loadExtensions else saveExtensions) {
                    if (extension == possible) {
                        return if (options == null) {
                            extension
                        } else {
                            // prepend the extension to the options (will be replaced by other
                            // options that override it later in the load loop)
                            "$extension,$options"
                        }
                    }
                }
            }
            return options
        }

        var utf8: Charset? = null

        @Throws(IOException::class)
        fun writeUTF(output: OutputStream, vararg lines: String) {
            if (utf8 == null) {
                utf8 = Charset.forName("UTF-8")
            }
            for (str in lines) {
                output.write(str.toByteArray(utf8!!))
                output.write('\n'.toInt())
            }
        }

        fun parseColumnType(columnType: String?): Int {
            var columnType = columnType
            columnType = columnType!!.toLowerCase()
            var type = -1
            type = if (columnType == "string") {
                STRING
            } else if (columnType == "int") {
                INT
            } else if (columnType == "long") {
                LONG
            } else if (columnType == "float") {
                FLOAT
            } else if (columnType == "double") {
                DOUBLE
            } else if (columnType == "category") {
                CATEGORY
            } else {
                throw IllegalArgumentException("'$columnType' is not a valid column type.")
            }
            return type
        }
    }
}