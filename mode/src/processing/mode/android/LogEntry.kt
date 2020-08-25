/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2013-16 The Processing Foundation
 
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License version 2
 as published by the Free Software Foundation.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package processing.mode.android

import java.util.regex.Pattern

/**
 * @author Aditya Rana
 */
internal class LogEntry(line: String) {
    enum class Severity(val useErrorStream: Boolean) {
        Verbose(false),
        Debug(false),
        Info(false),
        Warning(true),
        Error(true),
        Fatal(true);

        companion object {
            fun fromChar(c: Char): Severity {
                return if (c == 'V') {
                    Verbose
                } else if (c == 'D') {
                    Debug
                } else if (c == 'I') {
                    Info
                } else if (c == 'W') {
                    Warning
                } else if (c == 'E') {
                    Error
                } else if (c == 'F') {
                    Fatal
                } else {
                    throw IllegalArgumentException("I don't know how to interpret '"
                            + c + "' as a log severity")
                }
            }
        }

    }

    val severity: Severity
    val source: String
    val pid: Int
    val message: String

    override fun toString(): String {
        return "$severity/$source($pid): $message"
    }

    companion object {
        private val PARSER = Pattern
                .compile("^([VDIWEF])/([^\\(\\s]+)\\s*\\(\\s*(\\d+)\\): (.+)$")
    }

    // constructor or initializer block
    // will be executed before any other operation
    init {
        val m = PARSER.matcher(line)

        if (!m.matches()) {
            throw RuntimeException("I can't understand log entry\n$line")
        }

        severity = Severity.fromChar(m.group(1)[0])
        source = m.group(2)
        pid = m.group(3).toInt()
        message = m.group(4)
    }
}