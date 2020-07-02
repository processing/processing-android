/*
* Portions Copyright (C) 2003-2006 Sun Microsystems, Inc.
* All rights reserved.
*/

/*
** License Applicability. Except to the extent portions of this file are
** made subject to an alternative license as permitted in the SGI Free
** Software License B, Version 2.0 (the "License"), the contents of this
** file are subject only to the provisions of the License. You may not use
** this file except in compliance with the License. You may obtain a copy
** of the License at Silicon Graphics, Inc., attn: Legal Services, 1600
** Amphitheatre Parkway, Mountain View, CA 94043-1351, or at:
**
** http://oss.sgi.com/projects/FreeB
**
** Note that, as provided in the License, the Software is distributed on an
** "AS IS" basis, with ALL EXPRESS AND IMPLIED WARRANTIES AND CONDITIONS
** DISCLAIMED, INCLUDING, WITHOUT LIMITATION, ANY IMPLIED WARRANTIES AND
** CONDITIONS OF MERCHANTABILITY, SATISFACTORY QUALITY, FITNESS FOR A
** PARTICULAR PURPOSE, AND NON-INFRINGEMENT.
**
** NOTE:  The Original Code (as defined below) has been licensed to Sun
** Microsystems, Inc. ("Sun") under the SGI Free Software License B
** (Version 1.1), shown above ("SGI License").   Pursuant to Section
** 3.2(3) of the SGI License, Sun is distributing the Covered Code to
** you under an alternative license ("Alternative License").  This
** Alternative License includes all of the provisions of the SGI License
** except that Section 2.2 and 11 are omitted.  Any differences between
** the Alternative License and the SGI License are offered solely by Sun
** and not by SGI.
**
** Original Code. The Original Code is: OpenGL Sample Implementation,
** Version 1.2.1, released January 26, 2000, developed by Silicon Graphics,
** Inc. The Original Code is Copyright (c) 1991-2000 Silicon Graphics, Inc.
** Copyright in any portions created by third parties is as indicated
** elsewhere herein. All Rights Reserved.
**
** Additional Notice Provisions: The application programming interfaces
** established by SGI in conjunction with the Original Code are The
** OpenGL(R) Graphics System: A Specification (Version 1.2.1), released
** April 1, 1999; The OpenGL(R) Graphics System Utility Library (Version
** 1.3), released November 4, 1998; and OpenGL(R) Graphics with the X
** Window System(R) (Version 1.3), released October 19, 1998. This software
** was created using the OpenGL(R) version 1.2.1 Sample Implementation
** published by SGI, but has not been independently verified as being
** compliant with the OpenGL(R) version 1.2.1 Specification.
**
** Author: Eric Veach, July 1994
** Java Port: Pepijn Van Eeckhoudt, July 2003
** Java Port: Nathan Parker Burg, August 2003
** Processing integration: Andres Colubri, February 2012
*/

package processing.opengl.tess

internal class PriorityQHeap(leq: Leq) : PriorityQ() {
    var nodes: Array<PQnode?>?
    var handles: Array<PQhandleElem?>?
    var size = 0
    var max: Int
    var freeList: Int
    var initialized: Boolean
    var leq: Leq

    /* really __gl_pqHeapDeletePriorityQ */
    override fun pqDeletePriorityQ() {
        handles = null
        nodes = null
    }

    fun FloatDown(curr: Int) {
        var curr = curr
        val n = nodes
        val h = handles
        val hCurr: Int
        var hChild: Int
        var child: Int
        hCurr = n!![curr]!!.handle
        while (true) {
            child = curr shl 1
            if (child < size && LEQ(leq, h!![n[child + 1]!!.handle]!!.key,
                            h[n[child]!!.handle]!!.key)) {
                ++child
            }
            assert(child <= max)
            hChild = n[child]!!.handle
            if (child > size || LEQ(leq, h!![hCurr]!!.key, h[hChild]!!.key)) {
                n[curr]!!.handle = hCurr
                h!![hCurr]!!.node = curr
                break
            }
            n[curr]!!.handle = hChild
            h!![hChild]!!.node = curr
            curr = child
        }
    }

    fun FloatUp(curr: Int) {
        var curr = curr
        val n = nodes
        val h = handles
        val hCurr: Int
        var hParent: Int
        var parent: Int
        hCurr = n!![curr]!!.handle
        while (true) {
            parent = curr shr 1
            hParent = n[parent]!!.handle
            if (parent == 0 || LEQ(leq, h!![hParent]!!.key, h[hCurr]!!.key)) {
                n[curr]!!.handle = hCurr
                h!![hCurr]!!.node = curr
                break
            }
            n[curr]!!.handle = hParent
            h!![hParent]!!.node = curr
            curr = parent
        }
    }

    /* really __gl_pqHeapInit */
    override fun pqInit(): Boolean {
        var i: Int

        /* This method of building a heap is O(n), rather than O(n lg n). */i = size
        while (i >= 1) {
            FloatDown(i)
            --i
        }
        initialized = true
        return true
    }

    /* really __gl_pqHeapInsert */ /* returns LONG_MAX iff out of memory */
    override fun pqInsert(keyNew: Any?): Int {
        val curr: Int
        val free: Int
        curr = ++size
        if (curr * 2 > max) {
            val saveNodes = nodes
            val saveHandles = handles

            /* If the heap overflows, double its size. */max = max shl 1
            //            pq->nodes = (PQnode *)memRealloc( pq->nodes, (size_t) ((pq->max + 1) * sizeof( pq->nodes[0] )));
            val pqNodes = arrayOfNulls<PQnode>(max + 1)
            System.arraycopy(nodes, 0, pqNodes, 0, nodes!!.size)
            for (i in nodes!!.size until pqNodes.size) {
                pqNodes[i] = PQnode()
            }
            nodes = pqNodes
            if (nodes == null) {
                nodes = saveNodes /* restore ptr to free upon return */
                return Int.MAX_VALUE
            }

//            pq->handles = (PQhandleElem *)memRealloc( pq->handles,(size_t)((pq->max + 1) * sizeof( pq->handles[0] )));
            val pqHandles = arrayOfNulls<PQhandleElem>(max + 1)
            System.arraycopy(handles, 0, pqHandles, 0, handles!!.size)
            for (i in handles!!.size until pqHandles.size) {
                pqHandles[i] = PQhandleElem()
            }
            handles = pqHandles
            if (handles == null) {
                handles = saveHandles /* restore ptr to free upon return */
                return Int.MAX_VALUE
            }
        }
        if (freeList == 0) {
            free = curr
        } else {
            free = freeList
            freeList = handles!![free]!!.node
        }
        nodes!![curr]!!.handle = free
        handles!![free]!!.node = curr
        handles!![free]!!.key = keyNew
        if (initialized) {
            FloatUp(curr)
        }
        assert(free != Int.MAX_VALUE)
        return free
    }

    /* really __gl_pqHeapExtractMin */
    override fun pqExtractMin(): Any? {
        val n = nodes
        val h = handles
        val hMin = n!![1]!!.handle
        val min = h!![hMin]!!.key
        if (size > 0) {
            n[1]!!.handle = n[size]!!.handle
            h[n[1]!!.handle]!!.node = 1
            h[hMin]!!.key = null
            h[hMin]!!.node = freeList
            freeList = hMin
            if (--size > 0) {
                FloatDown(1)
            }
        }
        return min
    }

    /* really __gl_pqHeapDelete */
    override fun pqDelete(hCurr: Int) {
        val n = nodes
        val h = handles
        val curr: Int
        assert(hCurr >= 1 && hCurr <= max && h!![hCurr]!!.key != null)
        curr = h!![hCurr]!!.node
        n!![curr]!!.handle = n[size]!!.handle
        h[n[curr]!!.handle]!!.node = curr
        if (curr <= --size) {
            if (curr <= 1 || LEQ(leq, h[n[curr shr 1]!!.handle]!!.key, h[n[curr]!!.handle]!!.key)) {
                FloatDown(curr)
            } else {
                FloatUp(curr)
            }
        }
        h[hCurr]!!.key = null
        h[hCurr]!!.node = freeList
        freeList = hCurr
    }

    override fun pqMinimum(): Any? {
        return handles!![nodes!![1]!!.handle]!!.key
    }

    override fun pqIsEmpty(): Boolean {
        return size == 0
    }

    /* really __gl_pqHeapNewPriorityQ */
    init {
        max = INIT_SIZE
        nodes = arrayOfNulls(INIT_SIZE + 1)
        for (i in nodes!!.indices) {
            nodes!![i] = PQnode()
        }
        handles = arrayOfNulls(INIT_SIZE + 1)
        for (i in handles!!.indices) {
            handles!![i] = PQhandleElem()
        }
        initialized = false
        freeList = 0
        this.leq = leq
        nodes!![1]!!.handle = 1 /* so that Minimum() returns NULL */
        handles!![1]!!.key = null
    }
}