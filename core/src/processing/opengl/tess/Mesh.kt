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

internal object Mesh {

    /************************ Utility Routines  */
    /* MakeEdge creates a new pair of half-edges which form their own loop.
 * No vertex or face structures are allocated, but these must be assigned
 * before the current edge operation is completed.
 */
    @JvmStatic
    fun MakeEdge(eNext: GLUhalfEdge?): GLUhalfEdge {
        var eNext = eNext
        val e: GLUhalfEdge
        val eSym: GLUhalfEdge
        val ePrev: GLUhalfEdge?

//        EdgePair * pair = (EdgePair *)
//        memAlloc(sizeof(EdgePair));
//        if (pair == NULL) return NULL;
//
//        e = &pair - > e;
        e = GLUhalfEdge(true)
        //        eSym = &pair - > eSym;
        eSym = GLUhalfEdge(false)


        /* Make sure eNext points to the first edge of the edge pair */if (!eNext!!.first) {
            eNext = eNext.Sym
        }

        /* Insert in circular doubly-linked list before eNext.
         * Note that the prev pointer is stored in Sym->next.
         */
        ePrev = eNext!!.Sym!!.next
        eSym.next = ePrev
        ePrev!!.Sym!!.next = e
        e.next = eNext
        eNext.Sym!!.next = eSym
        e.Sym = eSym
        e.Onext = e
        e.Lnext = eSym
        e.Org = null
        e.Lface = null
        e.winding = 0
        e.activeRegion = null
        eSym.Sym = e
        eSym.Onext = eSym
        eSym.Lnext = e
        eSym.Org = null
        eSym.Lface = null
        eSym.winding = 0
        eSym.activeRegion = null
        return e
    }

    /* Splice( a, b ) is best described by the Guibas/Stolfi paper or the
 * CS348a notes (see mesh.h).  Basically it modifies the mesh so that
 * a->Onext and b->Onext are exchanged.  This can have various effects
 * depending on whether a and b belong to different face or vertex rings.
 * For more explanation see __gl_meshSplice() below.
 */
    @JvmStatic
    fun Splice(a: GLUhalfEdge?, b: GLUhalfEdge?) {
        val aOnext = a!!.Onext
        val bOnext = b!!.Onext
        aOnext!!.Sym!!.Lnext = b
        bOnext!!.Sym!!.Lnext = a
        a.Onext = bOnext
        b.Onext = aOnext
    }

    /* MakeVertex( newVertex, eOrig, vNext ) attaches a new vertex and makes it the
 * origin of all edges in the vertex loop to which eOrig belongs. "vNext" gives
 * a place to insert the new vertex in the global vertex list.  We insert
 * the new vertex *before* vNext so that algorithms which walk the vertex
 * list will not see the newly created vertices.
 */
    @JvmStatic
    fun MakeVertex(newVertex: GLUvertex?,
                   eOrig: GLUhalfEdge?, vNext: GLUvertex?) {
        var e: GLUhalfEdge?
        val vPrev: GLUvertex?
        assert(newVertex != null)

        /* insert in circular doubly-linked list before vNext */vPrev = vNext!!.prev
        newVertex!!.prev = vPrev
        vPrev!!.next = newVertex
        newVertex.next = vNext
        vNext.prev = newVertex
        newVertex.anEdge = eOrig
        newVertex.data = null
        /* leave coords, s, t undefined */

        /* fix other edges on this vertex loop */e = eOrig
        do {
            e!!.Org = newVertex
            e = e.Onext
        } while (e != eOrig)
    }

    /* MakeFace( newFace, eOrig, fNext ) attaches a new face and makes it the left
 * face of all edges in the face loop to which eOrig belongs.  "fNext" gives
 * a place to insert the new face in the global face list.  We insert
 * the new face *before* fNext so that algorithms which walk the face
 * list will not see the newly created faces.
 */
    @JvmStatic
    fun MakeFace(newFace: GLUface?, eOrig: GLUhalfEdge, fNext: GLUface?) {
        var e: GLUhalfEdge?
        val fPrev: GLUface?
        assert(newFace != null)

        /* insert in circular doubly-linked list before fNext */fPrev = fNext!!.prev
        newFace!!.prev = fPrev
        fPrev!!.next = newFace
        newFace.next = fNext
        fNext.prev = newFace
        newFace.anEdge = eOrig
        newFace.data = null
        newFace.trail = null
        newFace.marked = false

        /* The new face is marked "inside" if the old one was.  This is a
         * convenience for the common case where a face has been split in two.
         */newFace.inside = fNext.inside

        /* fix other edges on this face loop */e = eOrig
        do {
            e!!.Lface = newFace
            e = e.Lnext
        } while (e != eOrig)
    }

    /* KillEdge( eDel ) destroys an edge (the half-edges eDel and eDel->Sym),
 * and removes from the global edge list.
 */
    @JvmStatic
    fun KillEdge(eDel: GLUhalfEdge?) {
        var eDel = eDel
        val ePrev: GLUhalfEdge?
        val eNext: GLUhalfEdge?

        /* Half-edges are allocated in pairs, see EdgePair above */if (!eDel!!.first) {
            eDel = eDel.Sym
        }

        /* delete from circular doubly-linked list */eNext = eDel!!.next
        ePrev = eDel.Sym!!.next
        eNext!!.Sym!!.next = ePrev
        ePrev!!.Sym!!.next = eNext
    }

    /* KillVertex( vDel ) destroys a vertex and removes it from the global
 * vertex list.  It updates the vertex loop to point to a given new vertex.
 */
    @JvmStatic
    fun KillVertex(vDel: GLUvertex?, newOrg: GLUvertex?) {
        var e: GLUhalfEdge?
        val eStart = vDel!!.anEdge
        val vPrev: GLUvertex?
        val vNext: GLUvertex?

        /* change the origin of all affected edges */e = eStart
        do {
            e!!.Org = newOrg
            e = e.Onext
        } while (e != eStart)

        /* delete from circular doubly-linked list */vPrev = vDel.prev
        vNext = vDel.next
        vNext!!.prev = vPrev
        vPrev!!.next = vNext
    }

    /* KillFace( fDel ) destroys a face and removes it from the global face
 * list.  It updates the face loop to point to a given new face.
 */
    @JvmStatic
    fun KillFace(fDel: GLUface?, newLface: GLUface?) {
        var e: GLUhalfEdge?
        val eStart = fDel!!.anEdge
        val fPrev: GLUface?
        val fNext: GLUface?

        /* change the left face of all affected edges */e = eStart
        do {
            e!!.Lface = newLface
            e = e.Lnext
        } while (e != eStart)

        /* delete from circular doubly-linked list */fPrev = fDel.prev
        fNext = fDel.next
        fNext!!.prev = fPrev
        fPrev!!.next = fNext
    }

    /****************** Basic Edge Operations  */ /* __gl_meshMakeEdge creates one edge, two vertices, and a loop (face).
 * The loop consists of the two new half-edges.
 */
    @JvmStatic
    fun __gl_meshMakeEdge(mesh: GLUmesh): GLUhalfEdge? {
        val newVertex1 = GLUvertex()
        val newVertex2 = GLUvertex()
        val newFace = GLUface()
        val e: GLUhalfEdge
        e = MakeEdge(mesh.eHead)
        if (e == null) return null
        MakeVertex(newVertex1, e, mesh.vHead)
        MakeVertex(newVertex2, e.Sym, mesh.vHead)
        MakeFace(newFace, e, mesh.fHead)
        return e
    }

    /* __gl_meshSplice( eOrg, eDst ) is the basic operation for changing the
 * mesh connectivity and topology.  It changes the mesh so that
 *    eOrg->Onext <- OLD( eDst->Onext )
 *    eDst->Onext <- OLD( eOrg->Onext )
 * where OLD(...) means the value before the meshSplice operation.
 *
 * This can have two effects on the vertex structure:
 *  - if eOrg->Org != eDst->Org, the two vertices are merged together
 *  - if eOrg->Org == eDst->Org, the origin is split into two vertices
 * In both cases, eDst->Org is changed and eOrg->Org is untouched.
 *
 * Similarly (and independently) for the face structure,
 *  - if eOrg->Lface == eDst->Lface, one loop is split into two
 *  - if eOrg->Lface != eDst->Lface, two distinct loops are joined into one
 * In both cases, eDst->Lface is changed and eOrg->Lface is unaffected.
 *
 * Some special cases:
 * If eDst == eOrg, the operation has no effect.
 * If eDst == eOrg->Lnext, the new face will have a single edge.
 * If eDst == eOrg->Lprev, the old face will have a single edge.
 * If eDst == eOrg->Onext, the new vertex will have a single edge.
 * If eDst == eOrg->Oprev, the old vertex will have a single edge.
 */
    @JvmStatic
    fun __gl_meshSplice(eOrg: GLUhalfEdge, eDst: GLUhalfEdge): Boolean {
        var joiningLoops = false
        var joiningVertices = false
        if (eOrg == eDst) return true
        if (eDst.Org != eOrg.Org) {
            /* We are merging two disjoint vertices -- destroy eDst->Org */
            joiningVertices = true
            KillVertex(eDst.Org, eOrg.Org)
        }
        if (eDst.Lface != eOrg.Lface) {
            /* We are connecting two disjoint loops -- destroy eDst.Lface */
            joiningLoops = true
            KillFace(eDst.Lface, eOrg.Lface)
        }

        /* Change the edge structure */Splice(eDst, eOrg)
        if (!joiningVertices) {
            val newVertex = GLUvertex()

            /* We split one vertex into two -- the new vertex is eDst.Org.
             * Make sure the old vertex points to a valid half-edge.
             */MakeVertex(newVertex, eDst, eOrg.Org)
            eOrg.Org!!.anEdge = eOrg
        }
        if (!joiningLoops) {
            val newFace = GLUface()

            /* We split one loop into two -- the new loop is eDst.Lface.
             * Make sure the old face points to a valid half-edge.
             */MakeFace(newFace, eDst, eOrg.Lface)
            eOrg.Lface!!.anEdge = eOrg
        }
        return true
    }

    /* __gl_meshDelete( eDel ) removes the edge eDel.  There are several cases:
 * if (eDel.Lface != eDel.Rface), we join two loops into one; the loop
 * eDel.Lface is deleted.  Otherwise, we are splitting one loop into two;
 * the newly created loop will contain eDel.Dst.  If the deletion of eDel
 * would create isolated vertices, those are deleted as well.
 *
 * This function could be implemented as two calls to __gl_meshSplice
 * plus a few calls to memFree, but this would allocate and delete
 * unnecessary vertices and faces.
 */
    @JvmStatic
    fun __gl_meshDelete(eDel: GLUhalfEdge): Boolean {
        val eDelSym = eDel.Sym
        var joiningLoops = false

        /* First step: disconnect the origin vertex eDel.Org.  We make all
         * changes to get a consistent mesh in this "intermediate" state.
         */if (eDel.Lface != eDel.Sym!!.Lface) {
            /* We are joining two loops into one -- remove the left face */
            joiningLoops = true
            KillFace(eDel.Lface, eDel.Sym!!.Lface)
        }
        if (eDel.Onext == eDel) {
            KillVertex(eDel.Org, null)
        } else {
            /* Make sure that eDel.Org and eDel.Sym.Lface point to valid half-edges */
            eDel.Sym!!.Lface!!.anEdge = eDel.Sym!!.Lnext
            eDel.Org!!.anEdge = eDel.Onext
            Splice(eDel, eDel.Sym!!.Lnext)
            if (!joiningLoops) {
                val newFace = GLUface()

                /* We are splitting one loop into two -- create a new loop for eDel. */MakeFace(newFace, eDel, eDel.Lface)
            }
        }

        /* Claim: the mesh is now in a consistent state, except that eDel.Org
         * may have been deleted.  Now we disconnect eDel.Dst.
         */if (eDelSym!!.Onext == eDelSym) {
            KillVertex(eDelSym.Org, null)
            KillFace(eDelSym.Lface, null)
        } else {
            /* Make sure that eDel.Dst and eDel.Lface point to valid half-edges */
            eDel.Lface!!.anEdge = eDelSym.Sym!!.Lnext
            eDelSym.Org!!.anEdge = eDelSym.Onext
            Splice(eDelSym, eDelSym.Sym!!.Lnext)
        }

        /* Any isolated vertices or faces have already been freed. */KillEdge(eDel)
        return true
    }

    /******************** Other Edge Operations  */ /* All these routines can be implemented with the basic edge
 * operations above.  They are provided for convenience and efficiency.
 */
    /* __gl_meshAddEdgeVertex( eOrg ) creates a new edge eNew such that
 * eNew == eOrg.Lnext, and eNew.Dst is a newly created vertex.
 * eOrg and eNew will have the same left face.
 */
    @JvmStatic
    fun __gl_meshAddEdgeVertex(eOrg: GLUhalfEdge): GLUhalfEdge {
        val eNewSym: GLUhalfEdge
        val eNew = MakeEdge(eOrg)
        eNewSym = eNew.Sym!!

        /* Connect the new edge appropriately */Splice(eNew, eOrg.Lnext)

        /* Set the vertex and face information */eNew.Org = eOrg.Sym!!.Org
        run {
            val newVertex = GLUvertex()
            MakeVertex(newVertex, eNewSym, eNew.Org)
        }
        eNewSym.Lface = eOrg.Lface
        eNew.Lface = eNewSym.Lface
        return eNew
    }

    /* __gl_meshSplitEdge( eOrg ) splits eOrg into two edges eOrg and eNew,
 * such that eNew == eOrg.Lnext.  The new vertex is eOrg.Sym.Org == eNew.Org.
 * eOrg and eNew will have the same left face.
 */
    @JvmStatic
    fun __gl_meshSplitEdge(eOrg: GLUhalfEdge): GLUhalfEdge? {
        val eNew: GLUhalfEdge
        val tempHalfEdge = __gl_meshAddEdgeVertex(eOrg)
        eNew = tempHalfEdge.Sym!!

        /* Disconnect eOrg from eOrg.Sym.Org and connect it to eNew.Org */Splice(eOrg.Sym, eOrg.Sym!!.Sym!!.Lnext)
        Splice(eOrg.Sym, eNew)

        /* Set the vertex and face information */eOrg.Sym!!.Org = eNew.Org
        eNew.Sym!!.Org!!.anEdge = eNew.Sym /* may have pointed to eOrg.Sym */
        eNew.Sym!!.Lface = eOrg.Sym!!.Lface
        eNew.winding = eOrg.winding /* copy old winding information */
        eNew.Sym!!.winding = eOrg.Sym!!.winding
        return eNew
    }

    /* __gl_meshConnect( eOrg, eDst ) creates a new edge from eOrg.Sym.Org
 * to eDst.Org, and returns the corresponding half-edge eNew.
 * If eOrg.Lface == eDst.Lface, this splits one loop into two,
 * and the newly created loop is eNew.Lface.  Otherwise, two disjoint
 * loops are merged into one, and the loop eDst.Lface is destroyed.
 *
 * If (eOrg == eDst), the new face will have only two edges.
 * If (eOrg.Lnext == eDst), the old face is reduced to a single edge.
 * If (eOrg.Lnext.Lnext == eDst), the old face is reduced to two edges.
 */
    @JvmStatic
    fun __gl_meshConnect(eOrg: GLUhalfEdge, eDst: GLUhalfEdge): GLUhalfEdge {
        val eNewSym: GLUhalfEdge
        var joiningLoops = false
        val eNew = MakeEdge(eOrg)
        eNewSym = eNew.Sym!!
        if (eDst.Lface != eOrg.Lface) {
            /* We are connecting two disjoint loops -- destroy eDst.Lface */
            joiningLoops = true
            KillFace(eDst.Lface, eOrg.Lface)
        }

        /* Connect the new edge appropriately */Splice(eNew, eOrg.Lnext)
        Splice(eNewSym, eDst)

        /* Set the vertex and face information */eNew.Org = eOrg.Sym!!.Org
        eNewSym.Org = eDst.Org
        eNewSym.Lface = eOrg.Lface
        eNew.Lface = eNewSym.Lface

        /* Make sure the old face points to a valid half-edge */eOrg.Lface!!.anEdge = eNewSym
        if (!joiningLoops) {
            val newFace = GLUface()

            /* We split one loop into two -- the new loop is eNew.Lface */MakeFace(newFace, eNew, eOrg.Lface)
        }
        return eNew
    }

    /******************** Other Operations  */ /* __gl_meshZapFace( fZap ) destroys a face and removes it from the
 * global face list.  All edges of fZap will have a null pointer as their
 * left face.  Any edges which also have a null pointer as their right face
 * are deleted entirely (along with any isolated vertices this produces).
 * An entire mesh can be deleted by zapping its faces, one at a time,
 * in any order.  Zapped faces cannot be used in further mesh operations!
 */
    @JvmStatic
    fun __gl_meshZapFace(fZap: GLUface?) {
        val eStart = fZap!!.anEdge
        var e: GLUhalfEdge?
        var eNext: GLUhalfEdge?
        var eSym: GLUhalfEdge?
        val fPrev: GLUface?
        val fNext: GLUface?

        /* walk around face, deleting edges whose right face is also null */eNext = eStart!!.Lnext
        do {
            e = eNext
            eNext = e!!.Lnext
            e.Lface = null
            if (e.Sym!!.Lface == null) {
                /* delete the edge -- see __gl_MeshDelete above */
                if (e.Onext == e) {
                    KillVertex(e.Org, null)
                } else {
                    /* Make sure that e.Org points to a valid half-edge */
                    e.Org!!.anEdge = e.Onext
                    Splice(e, e.Sym!!.Lnext)
                }
                eSym = e.Sym
                if (eSym!!.Onext == eSym) {
                    KillVertex(eSym.Org, null)
                } else {
                    /* Make sure that eSym.Org points to a valid half-edge */
                    eSym.Org!!.anEdge = eSym.Onext
                    Splice(eSym, eSym.Sym!!.Lnext)
                }
                KillEdge(e)
            }
        } while (e != eStart)

        /* delete from circular doubly-linked list */fPrev = fZap.prev
        fNext = fZap.next
        fNext!!.prev = fPrev
        fPrev!!.next = fNext
    }

    /* __gl_meshNewMesh() creates a new mesh with no edges, no vertices,
 * and no loops (what we usually call a "face").
 */
    @JvmStatic
    fun __gl_meshNewMesh(): GLUmesh {
        val v: GLUvertex
        val f: GLUface
        val e: GLUhalfEdge
        val eSym: GLUhalfEdge
        val mesh = GLUmesh()
        v = mesh.vHead
        f = mesh.fHead
        e = mesh.eHead
        eSym = mesh.eHeadSym
        v.prev = v
        v.next = v.prev
        v.anEdge = null
        v.data = null
        f.prev = f
        f.next = f.prev
        f.anEdge = null
        f.data = null
        f.trail = null
        f.marked = false
        f.inside = false
        e.next = e
        e.Sym = eSym
        e.Onext = null
        e.Lnext = null
        e.Org = null
        e.Lface = null
        e.winding = 0
        e.activeRegion = null
        eSym.next = eSym
        eSym.Sym = e
        eSym.Onext = null
        eSym.Lnext = null
        eSym.Org = null
        eSym.Lface = null
        eSym.winding = 0
        eSym.activeRegion = null
        return mesh
    }

    /* __gl_meshUnion( mesh1, mesh2 ) forms the union of all structures in
 * both meshes, and returns the new mesh (the old meshes are destroyed).
 */
    @JvmStatic
    fun __gl_meshUnion(mesh1: GLUmesh, mesh2: GLUmesh): GLUmesh {
        val f1 = mesh1.fHead
        val v1 = mesh1.vHead
        val e1 = mesh1.eHead
        val f2 = mesh2.fHead
        val v2 = mesh2.vHead
        val e2 = mesh2.eHead

        /* Add the faces, vertices, and edges of mesh2 to those of mesh1 */if (f2.next != f2) {
            f1.prev!!.next = f2.next
            f2.next!!.prev = f1.prev
            f2.prev!!.next = f1
            f1.prev = f2.prev
        }
        if (v2.next != v2) {
            v1.prev!!.next = v2.next
            v2.next!!.prev = v1.prev
            v2.prev!!.next = v1
            v1.prev = v2.prev
        }
        if (e2.next != e2) {
            e1.Sym!!.next!!.Sym!!.next = e2.next
            e2.next!!.Sym!!.next = e1.Sym!!.next
            e2.Sym!!.next!!.Sym!!.next = e1
            e1.Sym!!.next = e2.Sym!!.next
        }
        return mesh1
    }

    /* __gl_meshDeleteMesh( mesh ) will free all storage for any valid mesh.
 */
    @JvmStatic
    fun __gl_meshDeleteMeshZap(mesh: GLUmesh) {
        val fHead = mesh.fHead
        while (fHead.next != fHead) {
            __gl_meshZapFace(fHead.next)
        }
        assert(mesh.vHead.next == mesh.vHead)
    }

    /* __gl_meshDeleteMesh( mesh ) will free all storage for any valid mesh.
 */
    @JvmStatic
    fun __gl_meshDeleteMesh(mesh: GLUmesh) {
        var f: GLUface?
        var fNext: GLUface?
        var v: GLUvertex?
        var vNext: GLUvertex?
        var e: GLUhalfEdge?
        var eNext: GLUhalfEdge?
        f = mesh.fHead.next
        while (f != mesh.fHead) {
            fNext = f!!.next
            f = fNext
        }
        v = mesh.vHead.next
        while (v != mesh.vHead) {
            vNext = v!!.next
            v = vNext
        }
        e = mesh.eHead.next
        while (e != mesh.eHead) {

            /* One call frees both e and e.Sym (see EdgePair above) */eNext = e!!.next
            e = eNext
        }
    }

    /* __gl_meshCheckMesh( mesh ) checks a mesh for self-consistency.
 */
    @JvmStatic
    fun __gl_meshCheckMesh(mesh: GLUmesh) {
        val fHead = mesh.fHead
        val vHead = mesh.vHead
        val eHead = mesh.eHead
        var f: GLUface
        var fPrev: GLUface
        var v: GLUvertex
        var vPrev: GLUvertex
        var e: GLUhalfEdge?
        var ePrev: GLUhalfEdge?
        fPrev = fHead
        fPrev = fHead
        while (fPrev.next.also { f = it!! } != fHead) {
            assert(f.prev == fPrev)
            e = f.anEdge
            do {
                assert(e!!.Sym != e)
                assert(e.Sym!!.Sym == e)
                assert(e.Lnext!!.Onext!!.Sym == e)
                assert(e.Onext!!.Sym!!.Lnext == e)
                assert(e.Lface == f)
                e = e.Lnext
            } while (e != f.anEdge)
            fPrev = f
        }
        assert(f.prev == fPrev && f.anEdge == null && f.data == null)
        vPrev = vHead
        vPrev = vHead
        while (vPrev.next.also { v = it!! } != vHead) {
            assert(v.prev == vPrev)
            e = v.anEdge
            do {
                assert(e!!.Sym != e)
                assert(e.Sym!!.Sym == e)
                assert(e.Lnext!!.Onext!!.Sym == e)
                assert(e.Onext!!.Sym!!.Lnext == e)
                assert(e.Org == v)
                e = e.Onext
            } while (e != v.anEdge)
            vPrev = v
        }
        assert(v.prev == vPrev && v.anEdge == null && v.data == null)
        ePrev = eHead
        ePrev = eHead
        while (ePrev!!.next.also { e = it } != eHead) {
            assert(e!!.Sym!!.next == ePrev.Sym)
            assert(e!!.Sym != e)
            assert(e!!.Sym!!.Sym == e)
            assert(e!!.Org != null)
            assert(e!!.Sym!!.Org != null)
            assert(e!!.Lnext!!.Onext!!.Sym == e)
            assert(e!!.Onext!!.Sym!!.Lnext == e)
            ePrev = e
        }
        assert(e!!.Sym!!.next == ePrev.Sym && e!!.Sym == mesh.eHeadSym && e!!.Sym!!.Sym == e && e!!.Org == null && e!!.Sym!!.Org == null && e!!.Lface == null && e!!.Sym!!.Lface == null)
    }
}