package processing.ar

import android.content.Context
import processing.ar.ShaderUtils
import android.opengl.GLES20
import processing.core.PApplet
import processing.core.PGraphics
import java.io.IOException
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.net.URL

internal object ShaderUtils {
    @JvmStatic
    fun loadGLShader(tag: String?, context: Context?, type: Int, resUrl: URL): Int {
        val code = readRawTextFile(resUrl)
        var shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            PGraphics.showWarning("Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            shader = 0
        }
        if (shader == 0) {
            throw RuntimeException("Error creating shader.")
        }
        return shader
    }

    @JvmStatic
    fun checkGLError(tag: String?, label: String) {
        var lastError = GLES20.GL_NO_ERROR
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            PGraphics.showWarning("$label: glError $error")
            lastError = error
        }
        if (lastError != GLES20.GL_NO_ERROR) {
            throw RuntimeException("$label: glError $lastError")
        }
    }

    private fun readRawTextFile(url: URL): String? {
        try {
            val sample = PApplet.loadStrings(url.openStream())
            val stringBuilder = StringBuilder()
            for (sam in sample) {
                stringBuilder.append(sam).append("\n")
            }
            return stringBuilder.toString()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}