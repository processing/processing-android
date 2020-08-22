/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2019 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.1.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.ar

import processing.core.PApplet
import java.lang.reflect.Method
import java.util.*

/**
 * @author Aditya Rana
 */
open class ARTracker(private var p: PApplet) {

    private var g: ARGraphics? = p.graphics!! as ARGraphics
    private val trackables = HashMap<String, ARTrackable>()
    private val toRemove = ArrayList<ARAnchor>()
    private var trackableEventMethod: Method? = null

    fun start() {
        cleanup()
        g!!.addTracker(this)
    }

    fun stop() {
        g!!.removeTracker(this)
    }

    fun count(): Int {
        return g!!.trackableCount()
    }

    operator fun get(idx: Int): ARTrackable? {
        val id = g!!.trackableId(idx)
        val sid = id.toString()
        if (!trackables.containsKey(sid)) {
            val t = ARTrackable(g!!, id)
            trackables[sid] = t
        }
        return get(sid)
    }

    operator fun get(id: String): ARTrackable? {
        return trackables[id]
    }

    operator fun get(mx: Int, my: Int): ARTrackable? {
        val hit = g!!.getHitResult(mx, my)
        return if (hit != null) {
            val idx = g!!.getTrackable(hit)
            val t = get(idx)
            t!!.hit = hit
            t
        } else {
            null
        }
    }

    fun create(idx: Int) {
        if (trackableEventMethod != null) {
            try {
                val t = get(idx)
                trackableEventMethod!!.invoke(p, t)
            } catch (e: Exception) {
                System.err.println("error, disabling trackableEventMethod() for AR tracker")
                e.printStackTrace()
                trackableEventMethod = null
            }
        }
    }

    fun clearAnchors(anchors: MutableCollection<ARAnchor>) {
        for (anchor in anchors) {
            if (anchor.isStopped || anchor.isDisposed) {
                anchor.dispose()
                toRemove.add(anchor)
            }
        }
        anchors.removeAll(toRemove)
        toRemove.clear()
    }

    private fun cleanup() {
        // Remove any inactive trackables left over in the tracker.
        val ids: Set<String> = trackables.keys
        for (id in ids) {
            val t = trackables[id]
            if (t!!.isStopped) trackables.remove(id)
        }
    }

    fun remove(idx: Int) {
        val id = g!!.trackableId(idx)
        val sid = id.toString()
        remove(sid)
    }

    private fun remove(id: String) {
        trackables.remove(id)
    }

    private fun setEventHandler() {
        try {
            trackableEventMethod = p.javaClass.getMethod("trackableEvent", ARTrackable::class.java)
            return
        } catch (e: Exception) {
            // no such method, or an error... which is fine, just ignore
        }

        // trackableEvent can alternatively be defined as receiving an Object, to allow
        // Processing mode implementors to support the video library without linking
        // to it at build-time.
        try {
            trackableEventMethod = p.javaClass.getMethod("trackableEvent", Any::class.java)
        } catch (e: Exception) {
            // no such method, or an error... which is fine, just ignore
        }
    }

    init {
        setEventHandler()
    }
}