package processing.ar;

import android.content.Context;
import android.opengl.GLES20;

import processing.core.PApplet;
import processing.core.PGraphics;

import java.io.IOException;
import java.net.URL;

public class ShaderUtils {
  public static int loadGLShader(String tag, Context context, int type, URL resUrl) {
    String code = readRawTextFile(resUrl);
    int shader = GLES20.glCreateShader(type);
    GLES20.glShaderSource(shader, code);
    GLES20.glCompileShader(shader);

    final int[] compileStatus = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

    if (compileStatus[0] == 0) {
      PGraphics.showWarning("Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
      GLES20.glDeleteShader(shader);
      shader = 0;
    }

    if (shader == 0) {
      throw new RuntimeException("Error creating shader.");
    }

    return shader;
  }

  public static void checkGLError(String tag, String label) {
    int lastError = GLES20.GL_NO_ERROR;
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      PGraphics.showWarning(label + ": glError " + error);
      lastError = error;
    }
    if (lastError != GLES20.GL_NO_ERROR) {
      throw new RuntimeException(label + ": glError " + lastError);
    }
  }

  private static String readRawTextFile(URL url) {
    try {
      String[] sample = PApplet.loadStrings(url.openStream());
      StringBuilder stringBuilder = new StringBuilder();
      for (String sam : sample) {
        stringBuilder.append(sam).append("\n");
      }
      return stringBuilder.toString();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
