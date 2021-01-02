/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2017-21 The Processing Foundation

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

package processing.android;

import android.annotation.SuppressLint;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.graphics.Point;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compatibility utilities that work across versions of Android. Even though
 * the mode sets API level 17 (Android 4.2) as the minimum target, because the
 * core library could be used from another IDE and lower targets, then this
 * compatibility methods are still needed.
 */
public class CompatUtils {
  // Start at 15,000,000, taking into account the comment from Singed
  // http://stackoverflow.com/a/39307421
  private static final AtomicInteger nextId = new AtomicInteger(15000000);


  /**
   * This method retrieves the "real" display metrics and size, without
   * subtracting any window decor or applying any compatibility scale factors.
   * @param display the Display object
   * @param metrics the metrics to retrieve
   * @param size the size to retrieve
   */
  static public void getDisplayParams(Display display,
                                      DisplayMetrics metrics, Point size) {
    if (Build.VERSION_CODES.JELLY_BEAN_MR1 <= Build.VERSION.SDK_INT) {
      display.getRealMetrics(metrics);
      display.getRealSize(size);
    } if (Build.VERSION_CODES.ICE_CREAM_SANDWICH <= Build.VERSION.SDK_INT) {
      display.getMetrics(metrics);
      // Use undocumented methods getRawWidth, getRawHeight
      try {
        size.x = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
        size.y = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
      } catch (Exception e) {
        display.getSize(size);
      }
    } else {
      display.getMetrics(metrics);
      display.getSize(size);
    }
  }


  /**
   * This method generates a unique View ID's. Handles the lack of
   * View.generateViewId() in Android versions lower than 17, using a technique
   * based on fantouch's code at http://stackoverflow.com/a/21000252
   * @return view ID
   */
  @SuppressLint("NewApi")
  static public int getUniqueViewId() {
    if (Build.VERSION_CODES.JELLY_BEAN_MR1 <= Build.VERSION.SDK_INT) {
      return View.generateViewId();
    } else {
      for (;;) {
        final int result = nextId.get();
        // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
        int newValue = result + 1;
        if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
        if (nextId.compareAndSet(result, newValue)) {
          return result;
        }
      }
    }
  }


  /**
   * This method returns the UTF-8 charset
   * @return UTF-8 charset
   */
  @SuppressLint("NewApi")
  static public Charset getCharsetUTF8() {
    if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
      return StandardCharsets.UTF_8;
    } else {
      return Charset.forName("UTF-8");
    }
  }
}
