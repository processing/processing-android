package processing.ar;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.google.ar.core.Frame;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class BackgroundRenderer {

  private static final int COORDS_PER_VERTEX = 3;
  private static final int TEXCOORDS_PER_VERTEX = 2;
  private static final int FLOAT_SIZE = 4;

  private FloatBuffer quadVertices;
  private FloatBuffer quadTexCoord;
  private FloatBuffer quadTexCoordTransformed;

  private int quadProgram;

  private int quadPositionParam;
  private int quadTexCoordParam;
  private int textureId = -1;

  static private URL screenquad_vertex =
      BackgroundRenderer.class.getResource("/assets/shaders/screenquad_vertex.glsl");
  static private URL screenquad_fragment =
      BackgroundRenderer.class.getResource("/assets/shaders/screenquad_fragment.glsl");

  private String VERTICES_ERROR = "Unexpected number of vertices in BackgroundRenderer";
  private String ERROR_TAG = "Error";
  private String CREATION_ERROR = "Program creation";
  private String PARAMETERS_ERROR = "Program parameters";
  private String DRAW_ERROR = "Draw";

  public BackgroundRenderer(Context context) {
    int[] textures = new int[1];
    GLES20.glGenTextures(1, textures, 0);
    textureId = textures[0];
    int textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    GLES20.glBindTexture(textureTarget, textureId);
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);

    int numVertices = 4;
    if (numVertices != QUAD_COORDS.length / COORDS_PER_VERTEX) {
      throw new RuntimeException(VERTICES_ERROR);
    }

    ByteBuffer bbVertices = ByteBuffer.allocateDirect(QUAD_COORDS.length * FLOAT_SIZE);
    bbVertices.order(ByteOrder.nativeOrder());
    quadVertices = bbVertices.asFloatBuffer();
    quadVertices.put(QUAD_COORDS);
    quadVertices.position(0);

    ByteBuffer bbTexCoords =
            ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
    bbTexCoords.order(ByteOrder.nativeOrder());
    quadTexCoord = bbTexCoords.asFloatBuffer();
    quadTexCoord.put(QUAD_TEXCOORDS);
    quadTexCoord.position(0);

    ByteBuffer bbTexCoordsTransformed =
            ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
    bbTexCoordsTransformed.order(ByteOrder.nativeOrder());
    quadTexCoordTransformed = bbTexCoordsTransformed.asFloatBuffer();

    int vertexShader =
            Utils.loadGLShader(ERROR_TAG, context, GLES20.GL_VERTEX_SHADER, screenquad_vertex);
    int fragmentShader =
            Utils.loadGLShader(
                    ERROR_TAG, context, GLES20.GL_FRAGMENT_SHADER, screenquad_fragment);

    quadProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(quadProgram, vertexShader);
    GLES20.glAttachShader(quadProgram, fragmentShader);
    GLES20.glLinkProgram(quadProgram);
    GLES20.glUseProgram(quadProgram);

    Utils.checkGLError(ERROR_TAG, CREATION_ERROR);

    quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position");
    quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord");

    Utils.checkGLError(ERROR_TAG, PARAMETERS_ERROR);
  }

  public int getTextureId() {
    return textureId;
  }

  public void draw(Frame frame) {

    if (frame.hasDisplayGeometryChanged()) {
      frame.transformDisplayUvCoords(quadTexCoord, quadTexCoordTransformed);
    }

    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    GLES20.glDepthMask(false);

    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

    GLES20.glUseProgram(quadProgram);

    GLES20.glVertexAttribPointer(
        quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadVertices);

    GLES20.glVertexAttribPointer(
        quadTexCoordParam,
        TEXCOORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        0,
        quadTexCoordTransformed);

    GLES20.glEnableVertexAttribArray(quadPositionParam);
    GLES20.glEnableVertexAttribArray(quadTexCoordParam);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    GLES20.glDisableVertexAttribArray(quadPositionParam);
    GLES20.glDisableVertexAttribArray(quadTexCoordParam);

    GLES20.glDepthMask(true);
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);

    Utils.checkGLError(ERROR_TAG, DRAW_ERROR);
  }

  private static final float[] QUAD_COORDS =
      new float[]{
          -1.0f, -1.0f, 0.0f,
          -1.0f, +1.0f, 0.0f,
          +1.0f, -1.0f, 0.0f,
          +1.0f, +1.0f, 0.0f,
      };

  private static final float[] QUAD_TEXCOORDS =
      new float[]{
          0.0f, 1.0f,
          0.0f, 0.0f,
          1.0f, 1.0f,
          1.0f, 0.0f,
      };
}
