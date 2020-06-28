package processing.data

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader

import java.lang.RuntimeException
import java.lang.StringBuilder

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
 */

/**
 * A JSONTokener takes a source string and extracts characters and tokens from
 * it. It is used by the JSONObject and JSONArray constructors to parse
 * JSON source strings.
 * @author JSON.org
 * @version 2012-02-16
 */
class JSONTokener(reader: Reader) {
    private var character: Long
    private var eof: Boolean
    private var index: Long
    private var line: Long
    private var previous: Char
    private val reader: Reader
    private var usePrevious: Boolean

    /**
     * Construct a JSONTokener from an InputStream.
     */
    constructor(inputStream: InputStream?) : this(InputStreamReader(inputStream)) {}

    /**
     * Construct a JSONTokener from a string.
     *
     * @param s     A source string.
     */
    constructor(s: String?) : this(StringReader(s)) {

    }

    /**
     * Back up one character. This provides a sort of lookahead capability,
     * so that you can test for a digit or letter before attempting to parse
     * the next number or identifier.
     */
    fun back() {
        if (usePrevious || index <= 0) {
            throw RuntimeException("Stepping back two steps is not supported")
        }
        index -= 1
        character -= 1
        usePrevious = true
        eof = false
    }

    fun end(): Boolean {
        return eof && !usePrevious
    }

    /**
     * Determine if the source string still contains characters that next()
     * can consume.
     * @return true if not yet at the end of the source.
     */
    fun more(): Boolean {
        this.next()
        if (end()) {
            return false
        }
        back()
        return true
    }

    /**
     * Get the next character in the source string.
     *
     * @return The next character, or 0 if past the end of the source string.
     */
    operator fun next(): Char {
        var c: Int
        if (usePrevious) {
            usePrevious = false
            c = previous.toInt()
        } else {
            c = try {
                reader.read()
            } catch (exception: IOException) {
                throw RuntimeException(exception)
            }
            if (c <= 0) { // End of stream
                eof = true
                c = 0
            }
        }
        index += 1
        if (previous == '\r') {
            line += 1
            character = if (c == '\n'.toInt()) 0 else 1.toLong()
        } else if (c == '\n'.toInt()) {
            line += 1
            character = 0
        } else {
            character += 1
        }
        previous = c.toChar()
        return previous
    }

    /**
     * Consume the next character, and check that it matches a specified
     * character.
     * @param c The character to match.
     * @return The character.
     * @throws JSONException if the character does not match.
     */
    fun next(c: Char): Char {
        val n = this.next()
        if (n != c) {
            throw RuntimeException("Expected '$c' and instead saw '$n'")
        }
        return n
    }

    /**
     * Get the next n characters.
     *
     * @param n     The number of characters to take.
     * @return      A string of n characters.
     * @throws JSONException
     * Substring bounds error if there are not
     * n characters remaining in the source string.
     */
    fun next(n: Int): String {
        if (n == 0) {
            return ""
        }
        val chars = CharArray(n)
        var pos = 0
        while (pos < n) {
            chars[pos] = this.next()
            if (end()) {
                throw RuntimeException("Substring bounds error")
            }
            pos += 1
        }
        return String(chars)
    }

    /**
     * Get the next char in the string, skipping whitespace.
     * @throws JSONException
     * @return  A character, or 0 if there are no more characters.
     */
    fun nextClean(): Char {
        while (true) {
            val c = this.next()
            if (c.toInt() == 0 || c > ' ') {
                return c
            }
        }
    }

    /**
     * Return the characters up to the next close quote character.
     * Backslash processing is done. The formal JSON format does not
     * allow strings in single quotes, but an implementation is allowed to
     * accept them.
     * @param quote The quoting character, either
     * `"`&nbsp;<small>(double quote)</small> or
     * `'`&nbsp;<small>(single quote)</small>.
     * @return      A String.
     * @throws JSONException Unterminated string.
     */
    fun nextString(quote: Char): String {
        var c: Char
        val sb = StringBuilder()
        while (true) {
            c = this.next()
            when (c) {
                0.toChar(), '\n', '\r' -> throw RuntimeException("Unterminated string")
                '\\' -> {
                    c = this.next()
                    when (c) {
                        'b' -> sb.append('\b')
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        // using encoding here for formfeed character because kotlin doesnot support it.
                        // valid escape charcters are  \t, \b, \n, \r, \', \", \\ and \$ and rest need to be used as unicodes
                        // https://kotlinlang.org/docs/reference/basic-types.html#characters
                        'f' -> sb.append('\u000C')
                        'r' -> sb.append('\r')
                        'u' -> sb.append(this.next(4).toInt(16).toChar())
                        '"', '\'', '\\', '/' -> sb.append(c)
                        else -> throw RuntimeException("Illegal escape.")
                    }
                }
                else -> {
                    if (c == quote) {
                        return sb.toString()
                    }
                    sb.append(c)
                }
            }
        }
    }

    /**
     * Get the text up but not including the specified character or the
     * end of line, whichever comes first.
     * @param  delimiter A delimiter character.
     * @return   A string.
     */
    fun nextTo(delimiter: Char): String {
        val sb = StringBuilder()
        while (true) {
            val c = this.next()
            if (c == delimiter || c.toInt() == 0 || c == '\n' || c == '\r') {
                if (c.toInt() != 0) {
                    back()
                }
                return sb.toString().trim { it <= ' ' }
            }
            sb.append(c)
        }
    }

    /**
     * Get the text up but not including one of the specified delimiter
     * characters or the end of line, whichever comes first.
     * @param delimiters A set of delimiter characters.
     * @return A string, trimmed.
     */
    fun nextTo(delimiters: String): String {
        var c: Char
        val sb = StringBuilder()
        while (true) {
            c = this.next()
            if (delimiters.indexOf(c) >= 0 || c.toInt() == 0 || c == '\n' || c == '\r') {
                if (c.toInt() != 0) {
                    back()
                }
                return sb.toString().trim { it <= ' ' }
            }
            sb.append(c)
        }
    }

    /**
     * Get the next value. The value can be a Boolean, Double, Integer,
     * JSONArray, JSONObject, Long, or String, or the JSONObject.NULL object.
     * @throws JSONException If syntax error.
     *
     * @return An object.
     */
    fun nextValue(): Any {
        var c = nextClean()
        val string: String
        when (c) {
            '"', '\'' -> return nextString(c)
            '{' -> {
                back()
                return JSONObject(this)
            }
            '[' -> {
                back()
                return JSONArray(this)
            }
        }

        /*
     * Handle unquoted text. This could be the values true, false, or
     * null, or it can be a number. An implementation (such as this one)
     * is allowed to also accept non-standard forms.
     *
     * Accumulate characters until we reach the end of the text or a
     * formatting character.
     */
        val sb = StringBuilder()
        while (c >= ' ' && ",:]}/\\\"[{;=#".indexOf(c) < 0) {
            sb.append(c)
            c = this.next()
        }
        back()
        string = sb.toString().trim { it <= ' ' }
        if ("" == string) {
            throw RuntimeException("Missing value")
        }
        return JSONObject.stringToValue(string)!!
    }

    /**
     * Skip characters until the next character is the requested character.
     * If the requested character is not found, no characters are skipped.
     * @param to A character to skip to.
     * @return The requested character, or zero if the requested character
     * is not found.
     */
    fun skipTo(to: Char): Char {
        var c: Char
        try {
            val startIndex = index
            val startCharacter = character
            val startLine = line
            reader.mark(1000000)
            do {
                c = this.next()
                if (c.toInt() == 0) {
                    reader.reset()
                    index = startIndex
                    character = startCharacter
                    line = startLine
                    return c
                }
            } while (c != to)
        } catch (exc: IOException) {
            throw RuntimeException(exc)
        }
        back()
        return c
    }

    /**
     * Make a printable string of this JSONTokener.
     *
     * @return " at {index} [character {character} line {line}]"
     */
    override fun toString(): String {
        return " at " + index + " [character " + character + " line " +
                line + "]"
    }

    companion object {
        /**
         * Get the hex value of a character (base16).
         * @param c A character between '0' and '9' or between 'A' and 'F' or
         * between 'a' and 'f'.
         * @return  An int between 0 and 15, or -1 if c was not a hex digit.
         */
        @JvmStatic
        fun dehexchar(c: Char): Int {
            if (c in '0'..'9') {
                return c - '0'
            }
            if (c in 'A'..'F') {
                return c.toInt() - ('A'.toInt() - 10)
            }
            return if (c in 'a'..'f') {
                c.toInt() - ('a'.toInt() - 10)
            } else -1
        }
    }

    /**
     * Construct a JSONTokener from a Reader.
     *
     * @param reader     A reader.
     */
    init {
        this.reader = if (reader.markSupported()) reader else BufferedReader(reader)
        eof = false
        usePrevious = false
        previous = 0.toChar()
        index = 0
        character = 1
        line = 1
    }
}