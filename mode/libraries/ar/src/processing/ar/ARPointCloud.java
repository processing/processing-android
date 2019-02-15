package processing.ar;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.ar.core.PointCloud;

import java.net.URL;

public class ARPointCloud {

  static private URL pointcloud_vertex =
      ARPointCloud.class.getResource("/assets/shaders/pointcloud_vertex.glsl");
  static private URL pointcloud_fragment =
      ARPointCloud.class.getResource("/assets/shaders/pointcloud_fragment.glsl");

  private String ERROR_TAG = "Error";
  private String BEF_CREATE = "before create";
  private String BUFF_ALLOC = "buffer alloc";
  private String ERROR_PGM = "program";
  private String ERROR_PARAM = "program  params";
  private String BEF_UPDATE = "before update";
  private String AFT_UPDATE = "after update";
  private String BEF_DRAW_ERROR = "Before draw";
  private String DRAW_ERROR = "Draw";

  private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
  private static final int FLOATS_PER_POINT = 4;
  private static final int BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT;
  private static final int INITIAL_BUFFER_POINTS = 1000;

  private int vbo;
  private int vboSize;

  private int programName;
  private int positionAttribute;
  private int modelViewProjectionUniform;
  private int colorUniform;
  private int pointSizeUniform;

  private int numPoints = 0;


  private PointCloud lastPointCloud = null;

  public ARPointCloud() {
  }

  public void createOnGlThread(Context context) {
    Utils.checkGLError(ERROR_TAG, BEF_CREATE);

    int[] buffers = new int[1];
    GLES20.glGenBuffers(1, buffers, 0);
    vbo = buffers[0];
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);

    vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    Utils.checkGLError(ERROR_TAG, BUFF_ALLOC);

    int vertexShader =
        Utils.loadGLShader(ERROR_TAG, context, GLES20.GL_VERTEX_SHADER, pointcloud_vertex);
    int passthroughShader =
        Utils.loadGLShader(
            ERROR_TAG, context, GLES20.GL_FRAGMENT_SHADER, pointcloud_fragment);

    programName = GLES20.glCreateProgram();
    GLES20.glAttachShader(programName, vertexShader);
    GLES20.glAttachShader(programName, passthroughShader);
    GLES20.glLinkProgram(programName);
    GLES20.glUseProgram(programName);

    Utils.checkGLError(ERROR_TAG, ERROR_PGM);

    positionAttribute = GLES20.glGetAttribLocation(programName, "a_Position");
    colorUniform = GLES20.glGetUniformLocation(programName, "u_Color");
    modelViewProjectionUniform = GLES20.glGetUniformLocation(programName, "u_ModelViewProjection");
    pointSizeUniform = GLES20.glGetUniformLocation(programName, "u_PointSize");

    Utils.checkGLError(ERROR_TAG, ERROR_PARAM);
  }

  public void update(PointCloud cloud) {
    if (lastPointCloud == cloud) {
      return;
    }

    Utils.checkGLError(ERROR_TAG, BEF_UPDATE);

    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
    lastPointCloud = cloud;

    numPoints = lastPointCloud.getPoints().remaining() / FLOATS_PER_POINT;
    if (numPoints * BYTES_PER_POINT > vboSize) {
      while (numPoints * BYTES_PER_POINT > vboSize) {
        vboSize *= 2;
      }
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vboSize, null, GLES20.GL_DYNAMIC_DRAW);
    }
    GLES20.glBufferSubData(
        GLES20.GL_ARRAY_BUFFER, 0, numPoints * BYTES_PER_POINT, lastPointCloud.getPoints());
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    Utils.checkGLError(ERROR_TAG, AFT_UPDATE);
  }

  public void draw(float[] cameraView, float[] cameraPerspective) {
    float[] modelViewProjection = new float[16];
    Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0);

    Utils.checkGLError(ERROR_TAG, BEF_DRAW_ERROR);

    GLES20.glUseProgram(programName);
    GLES20.glEnableVertexAttribArray(positionAttribute);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
    GLES20.glVertexAttribPointer(positionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
    GLES20.glUniform4f(colorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f);
    GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjection, 0);
    GLES20.glUniform1f(pointSizeUniform, 5.0f);

    GLES20.glDrawArrays(GLES20.GL_POINTS, 0, numPoints);
    GLES20.glDisableVertexAttribArray(positionAttribute);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

    Utils.checkGLError(ERROR_TAG, DRAW_ERROR);
  }
}
