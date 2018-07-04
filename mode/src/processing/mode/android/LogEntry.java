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

package processing.mode.android;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LogEntry {
  public static enum Severity {
    Verbose(false), Debug(false), Info(false), Warning(true), Error(true), Fatal(
        true);
    public final boolean useErrorStream;

    private Severity(final boolean useErrorStream) {
      this.useErrorStream = useErrorStream;
    }

    private static Severity fromChar(final char c) {
      if (c == 'V') {
        return Verbose;
      } else if (c == 'D') {
        return Debug;
      } else if (c == 'I') {
        return Info;
      } else if (c == 'W') {
        return Warning;
      } else if (c == 'E') {
        return Error;
      } else if (c == 'F') {
        return Fatal;
      } else {
        throw new IllegalArgumentException("I don't know how to interpret '"
            + c + "' as a log severity");
      }
    }
  }

  public final Severity severity;
  public final String source;
  public final int pid;
  public final String message;

  private static final Pattern PARSER = Pattern
      .compile("^([VDIWEF])/([^\\(\\s]+)\\s*\\(\\s*(\\d+)\\): (.+)$");

  public LogEntry(final String line) {
    final Matcher m = PARSER.matcher(line);
    if (!m.matches()) {
      throw new RuntimeException("I can't understand log entry\n" + line);
    }
    this.severity = Severity.fromChar(m.group(1).charAt(0));
    this.source = m.group(2);
    this.pid = Integer.parseInt(m.group(3));
    this.message = m.group(4);
  }

  @Override
  public String toString() {
    return severity + "/" + source + "(" + pid + "): " + message;
  }
}
