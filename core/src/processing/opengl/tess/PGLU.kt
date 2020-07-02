package processing.opengl.tess

import android.opengl.GLES20

open class  PGLU {
    companion object {
        const val GLU_FALSE = 0
        const val GLU_TRUE = 1
        const val GLU_INVALID_ENUM = 100900
        const val GLU_INVALID_VALUE = 100901
        const val GLU_OUT_OF_MEMORY = 100902
        const val GLU_INVALID_OPERATION = 100904
        const val GLU_POINT = 100010
        const val GLU_LINE = 100011
        const val GLU_FILL = 100012
        const val GLU_SILHOUETTE = 100013
        const val GLU_SMOOTH = 100000
        const val GLU_FLAT = 100001
        const val GLU_NONE = 100002
        const val GLU_OUTSIDE = 100020
        const val GLU_INSIDE = 100021
        const val GLU_ERROR = 100103
        const val GLU_TESS_ERROR = 100103
        const val GLU_TESS_BEGIN = 100100
        const val GLU_BEGIN = 100100
        const val GLU_TESS_VERTEX = 100101
        const val GLU_VERTEX = 100101
        const val GLU_TESS_END = 100102
        const val GLU_END = 100102
        const val GLU_TESS_EDGE_FLAG = 100104
        const val GLU_EDGE_FLAG = 100104
        const val GLU_TESS_COMBINE = 100105
        const val GLU_TESS_BEGIN_DATA = 100106
        const val GLU_TESS_VERTEX_DATA = 100107
        const val GLU_TESS_END_DATA = 100108
        const val GLU_TESS_ERROR_DATA = 100109
        const val GLU_TESS_EDGE_FLAG_DATA = 100110
        const val GLU_TESS_COMBINE_DATA = 100111
        const val GLU_CW = 100120
        const val GLU_CCW = 100121
        const val GLU_INTERIOR = 100122
        const val GLU_EXTERIOR = 100123
        const val GLU_UNKNOWN = 100124
        const val GLU_TESS_WINDING_RULE = 100140
        const val GLU_TESS_BOUNDARY_ONLY = 100141
        const val GLU_TESS_TOLERANCE = 100142
        const val GLU_TESS_AVOID_DEGENERATE_TRIANGLES = 100149
        const val GLU_TESS_ERROR1 = 100151
        const val GLU_TESS_ERROR2 = 100152
        const val GLU_TESS_ERROR3 = 100153
        const val GLU_TESS_ERROR4 = 100154
        const val GLU_TESS_ERROR5 = 100155
        const val GLU_TESS_ERROR6 = 100156
        const val GLU_TESS_ERROR7 = 100157
        const val GLU_TESS_ERROR8 = 100158
        const val GLU_TESS_MISSING_BEGIN_POLYGON = 100151
        const val GLU_TESS_MISSING_BEGIN_CONTOUR = 100152
        const val GLU_TESS_MISSING_END_POLYGON = 100153
        const val GLU_TESS_MISSING_END_CONTOUR = 100154
        const val GLU_TESS_COORD_TOO_LARGE = 100155
        const val GLU_TESS_NEED_COMBINE_CALLBACK = 100156
        const val GLU_TESS_WINDING_ODD = 100130
        const val GLU_TESS_WINDING_NONZERO = 100131
        const val GLU_TESS_WINDING_POSITIVE = 100132
        const val GLU_TESS_WINDING_NEGATIVE = 100133
        const val GLU_TESS_WINDING_ABS_GEQ_TWO = 100134
        const val GLU_TESS_MAX_COORD = 1.0E150
        private val glErrorStrings = arrayOf(
                "invalid enumerant",
                "invalid value",
                "invalid operation",
                "stack overflow",
                "stack underflow",
                "out of memory",
                "invalid framebuffer operation"
        )
        private val gluErrorStrings = arrayOf(
                "invalid enumerant",
                "invalid value",
                "out of memory",
                "",
                "invalid operation"
        )
        private val gluTessErrors = arrayOf(
                " ",
                "gluTessBeginPolygon() must precede a gluTessEndPolygon",
                "gluTessBeginContour() must precede a gluTessEndContour()",
                "gluTessEndPolygon() must follow a gluTessBeginPolygon()",
                "gluTessEndContour() must follow a gluTessBeginContour()",
                "a coordinate is too large",
                "need combine callback"
        )

        @JvmStatic
        fun gluNewTess(): PGLUtessellator {
            return GLUtessellatorImpl.gluNewTess()
        }

        @JvmStatic
        fun gluTessCallback(tess: PGLUtessellator, which: Int, callback: PGLUtessellatorCallback?) {
            (tess as GLUtessellatorImpl).gluTessCallback(which, callback)
        }

        @JvmStatic
        fun gluTessBeginPolygon(tess: PGLUtessellator, data: Any?) {
            (tess as GLUtessellatorImpl).gluTessBeginPolygon(data)
        }

        @JvmStatic
        fun gluTessEndPolygon(tess: PGLUtessellator) {
            (tess as GLUtessellatorImpl).gluTessEndPolygon()
        }

        @JvmStatic
        fun gluTessProperty(tess: PGLUtessellator, which: Int, value: Double) {
            (tess as GLUtessellatorImpl).gluTessProperty(which, value)
        }

        @JvmStatic
        fun gluTessBeginContour(tess: PGLUtessellator) {
            (tess as GLUtessellatorImpl).gluTessBeginContour()
        }

        @JvmStatic
        fun gluTessEndContour(tess: PGLUtessellator) {
            (tess as GLUtessellatorImpl).gluTessEndContour()
        }

        @JvmStatic
        fun gluTessVertex(tess: PGLUtessellator, coords: DoubleArray?, offset: Int, vdata: Any?) {
            (tess as GLUtessellatorImpl).gluTessVertex(coords, offset, vdata)
        }

        @JvmStatic
        fun gluErrorString(errorCode: Int): String {
            if (errorCode == 0) {
                return "no error"
            }
            if (errorCode >= GLES20.GL_INVALID_ENUM && errorCode <= GLES20.GL_INVALID_FRAMEBUFFER_OPERATION) {
                return glErrorStrings[errorCode - GLES20.GL_INVALID_ENUM]
            }
            if (errorCode == 0x8031 /* GL.GL_TABLE_TOO_LARGE */) {
                return "table too large"
            }
            if (errorCode in GLU_INVALID_ENUM..GLU_INVALID_OPERATION) {
                return gluErrorStrings[errorCode - GLU_INVALID_ENUM]
            }
            return if (errorCode in GLU_TESS_ERROR1..GLU_TESS_ERROR8) {
                gluTessErrors[errorCode - (GLU_TESS_ERROR1 - 1)]
            } else "error ($errorCode)"
        }
    }
}