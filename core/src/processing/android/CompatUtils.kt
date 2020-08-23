/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2017 The Processing Foundation

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

package processing.android

import android.annotation.SuppressLint
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Aditya Rana
 * Compatibility utilities that work across versions of Android. Even though
 * the mode sets API level 17 (Android 4.2) as the minimum target, because the
 * core library could be used from another IDE and lower targets, then this
 * compatibility methods are still needed.
 */
object CompatUtils {
    // Start at 15,000,000, taking into account the comment from Singed
    // http://stackoverflow.com/a/39307421
    private val nextId = AtomicInteger(15000000)

    /**
     * This method retrieves the "real" display metrics and size, without
     * subtracting any window decor or applying any compatibility scale factors.
     * @param display the Display object
     * @param metrics the metrics to retrieve
     * @param size the size to retrieve
     */
    @JvmStatic
    fun getDisplayParams(display: Display,
                         metrics: DisplayMetrics?, size: Point) {
        if (Build.VERSION_CODES.JELLY_BEAN_MR1 <= Build.VERSION.SDK_INT) {
            display.getRealMetrics(metrics)
            display.getRealSize(size)
        }
        if (Build.VERSION_CODES.ICE_CREAM_SANDWICH <= Build.VERSION.SDK_INT) {
            display.getMetrics(metrics)
            // Use undocumented methods getRawWidth, getRawHeight
            try {
                size.x = (Display::class.java.getMethod("getRawWidth").invoke(display) as Int)
                size.y = (Display::class.java.getMethod("getRawHeight").invoke(display) as Int)
            } catch (e: Exception) {
                display.getSize(size)
            }
        } else {
            display.getMetrics(metrics)
            display.getSize(size)
        }
    }// aapt-generated IDs have the high byte nonzero; clamp to the range under that.
    // Roll over to 1, not 0.

    /**
     * This method generates a unique View ID's. Handles the lack of
     * View.generateViewId() in Android versions lower than 17, using a technique
     * based on fantouch's code at http://stackoverflow.com/a/21000252
     * @return view ID
     */
    @JvmStatic
    @get:SuppressLint("NewApi")
    val uniqueViewId: Int
        get() {
            if (Build.VERSION_CODES.JELLY_BEAN_MR1 <= Build.VERSION.SDK_INT) {
                return View.generateViewId()
            } else {
                while (true) {
                    val result = nextId.get()
                    // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
                    var newValue = result + 1
                    if (newValue > 0x00FFFFFF) newValue = 1 // Roll over to 1, not 0.
                    if (nextId.compareAndSet(result, newValue)) {
                        return result
                    }
                }
            }
        }

    /**
     * This method returns the UTF-8 charset
     * @return UTF-8 charset
     */
    @JvmStatic
    @get:SuppressLint("NewApi")
    val charsetUTF8: Charset
        get() = if (Build.VERSION_CODES.KITKAT <= Build.VERSION.SDK_INT) {
            StandardCharsets.UTF_8
        } else {
            Charset.forName("UTF-8")
        }
}