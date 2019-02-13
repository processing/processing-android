package processing.ar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.*;

public class ARPlane {
  private static String PLANE_TEXTURE = "grid.png";

  static private URL plane_vertex =
      ARPlane.class.getResource("/assets/shaders/plane_vertex.glsl");
  static private URL plane_fragment =
      ARPlane.class.getResource("/assets/shaders/plane_fragment.glsl");

  private String ERROR_TAG = "Error";
  private String CREATION_ERROR = "Program creation";
  private String TEXT_LOAD = "Texture loading";
  private String PARAM_ERROR = "Program parameters";
  private String PLANE_ERROR = "Drawing plane";
  private String PLANE_SETUP_ERROR = "Setting up to draw planes";
  private String CLEAN_UP = "Cleaning up after drawing planes";

  private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
  private static final int BYTES_PER_SHORT = Short.SIZE / 8;
  private static final int COORDS_PER_VERTEX = 3;

  private static final int VERTS_PER_BOUNDARY_VERT = 2;
  private static final int INDICES_PER_BOUNDARY_VERT = 3;
  private static final int INITIAL_BUFFER_BOUNDARY_VERTS = 64;

  private static final int INITIAL_VERTEX_BUFFER_SIZE_BYTES =
      BYTES_PER_FLOAT * COORDS_PER_VERTEX * VERTS_PER_BOUNDARY_VERT * INITIAL_BUFFER_BOUNDARY_VERTS;

  private static final int INITIAL_INDEX_BUFFER_SIZE_BYTES =
      BYTES_PER_SHORT
          * INDICES_PER_BOUNDARY_VERT
          * INDICES_PER_BOUNDARY_VERT
          * INITIAL_BUFFER_BOUNDARY_VERTS;

  private static final float FADE_RADIUS_M = 0.25f;
  private static final float DOTS_PER_METER = 10.0f;
  private static final float EQUILATERAL_TRIANGLE_SCALE = (float) (1 / Math.sqrt(3));

  private static final float[] GRID_CONTROL = {0.2f, 0.4f, 2.0f, 1.5f};

  private int planeProgram;
  private final int[] textures = new int[1];

  private int planeXZPositionAlphaAttribute;

  private int planeModelUniform;
  private int planeModelViewProjectionUniform;
  private int textureUniform;
  private int lineColorUniform;
  private int dotColorUniform;
  private int gridControlUniform;
  private int planeUvMatrixUniform;
  public static int colorValue;

  private FloatBuffer vertexBuffer =
      ByteBuffer.allocateDirect(INITIAL_VERTEX_BUFFER_SIZE_BYTES)
          .order(ByteOrder.nativeOrder())
          .asFloatBuffer();
  private ShortBuffer indexBuffer =
      ByteBuffer.allocateDirect(INITIAL_INDEX_BUFFER_SIZE_BYTES)
          .order(ByteOrder.nativeOrder())
          .asShortBuffer();

  private final float[] modelMatrix = new float[16];
  private final float[] modelViewMatrix = new float[16];
  private final float[] modelViewProjectionMatrix = new float[16];
  private final float[] planeColor = new float[4];
  private final float[] planeAngleUvMatrix =
      new float[4];

  private final Map<Plane, Integer> planeIndexMap = new HashMap<>();

  public ARPlane() {
  }

  public void createOnGlThread(Context context) throws IOException {
    int vertexShader =
        Utils.loadGLShader(ERROR_TAG, context, GLES20.GL_VERTEX_SHADER, plane_vertex);
    int passthroughShader =
        Utils.loadGLShader(ERROR_TAG, context, GLES20.GL_FRAGMENT_SHADER, plane_fragment);

    planeProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(planeProgram, vertexShader);
    GLES20.glAttachShader(planeProgram, passthroughShader);
    GLES20.glLinkProgram(planeProgram);
    GLES20.glUseProgram(planeProgram);

    Utils.checkGLError(ERROR_TAG, CREATION_ERROR);

    Bitmap textureBitmap =
        BitmapFactory.decodeStream(context.getAssets().open(PLANE_TEXTURE));

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glGenTextures(textures.length, textures, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);

    GLES20.glTexParameteri(
        GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
    GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

    Utils.checkGLError(ERROR_TAG, TEXT_LOAD);

    planeXZPositionAlphaAttribute = GLES20.glGetAttribLocation(planeProgram, "a_XZPositionAlpha");

    planeModelUniform = GLES20.glGetUniformLocation(planeProgram, "u_Model");
    planeModelViewProjectionUniform =
        GLES20.glGetUniformLocation(planeProgram, "u_ModelViewProjection");
    textureUniform = GLES20.glGetUniformLocation(planeProgram, "u_Texture");
    lineColorUniform = GLES20.glGetUniformLocation(planeProgram, "u_lineColor");
    dotColorUniform = GLES20.glGetUniformLocation(planeProgram, "u_dotColor");
    gridControlUniform = GLES20.glGetUniformLocation(planeProgram, "u_gridControl");
    planeUvMatrixUniform = GLES20.glGetUniformLocation(planeProgram, "u_PlaneUvMatrix");

    Utils.checkGLError(ERROR_TAG, PARAM_ERROR);
  }

  private void updatePlaneParameters(
      float[] planeMatrix, float extentX, float extentZ, FloatBuffer boundary) {
    System.arraycopy(planeMatrix, 0, modelMatrix, 0, 16);
    if (boundary == null) {
      vertexBuffer.limit(0);
      indexBuffer.limit(0);
      return;
    }

    boundary.rewind();
    int boundaryVertices = boundary.limit() / 2;
    int numVertices;
    int numIndices;

    numVertices = boundaryVertices * VERTS_PER_BOUNDARY_VERT;
    numIndices = boundaryVertices * INDICES_PER_BOUNDARY_VERT;

    if (vertexBuffer.capacity() < numVertices * COORDS_PER_VERTEX) {
      int size = vertexBuffer.capacity();
      while (size < numVertices * COORDS_PER_VERTEX) {
        size *= 2;
      }
      vertexBuffer =
          ByteBuffer.allocateDirect(BYTES_PER_FLOAT * size)
              .order(ByteOrder.nativeOrder())
              .asFloatBuffer();
    }
    vertexBuffer.rewind();
    vertexBuffer.limit(numVertices * COORDS_PER_VERTEX);

    if (indexBuffer.capacity() < numIndices) {
      int size = indexBuffer.capacity();
      while (size < numIndices) {
        size *= 2;
      }
      indexBuffer =
          ByteBuffer.allocateDirect(BYTES_PER_SHORT * size)
              .order(ByteOrder.nativeOrder())
              .asShortBuffer();
    }
    indexBuffer.rewind();
    indexBuffer.limit(numIndices);

    float xScale = Math.max((extentX - 2 * FADE_RADIUS_M) / extentX, 0.0f);
    float zScale = Math.max((extentZ - 2 * FADE_RADIUS_M) / extentZ, 0.0f);

    while (boundary.hasRemaining()) {
      float x = boundary.get();
      float z = boundary.get();
      vertexBuffer.put(x);
      vertexBuffer.put(z);
      vertexBuffer.put(0.0f);
      vertexBuffer.put(x * xScale);
      vertexBuffer.put(z * zScale);
      vertexBuffer.put(1.0f);
    }

    indexBuffer.put((short) ((boundaryVertices - 1) * 2));
    for (int i = 0; i < boundaryVertices; ++i) {
      indexBuffer.put((short) (i * 2));
      indexBuffer.put((short) (i * 2 + 1));
    }
    indexBuffer.put((short) 1);
    for (int i = 1; i < boundaryVertices / 2; ++i) {
      indexBuffer.put((short) ((boundaryVertices - 1 - i) * 2 + 1));
      indexBuffer.put((short) (i * 2 + 1));
    }
    if (boundaryVertices % 2 != 0) {
      indexBuffer.put((short) ((boundaryVertices / 2) * 2 + 1));
    }
  }

  private void draw(float[] cameraView, float[] cameraPerspective) {
    Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0);
    Matrix.multiplyMM(modelViewProjectionMatrix, 0, cameraPerspective, 0, modelViewMatrix, 0);

    vertexBuffer.rewind();
    GLES20.glVertexAttribPointer(
        planeXZPositionAlphaAttribute,
        COORDS_PER_VERTEX,
        GLES20.GL_FLOAT,
        false,
        BYTES_PER_FLOAT * COORDS_PER_VERTEX,
        vertexBuffer);

    GLES20.glUniformMatrix4fv(planeModelUniform, 1, false, modelMatrix, 0);
    GLES20.glUniformMatrix4fv(
        planeModelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0);

    indexBuffer.rewind();
    GLES20.glDrawElements(
        GLES20.GL_TRIANGLE_STRIP, indexBuffer.limit(), GLES20.GL_UNSIGNED_SHORT, indexBuffer);
    Utils.checkGLError(ERROR_TAG, PLANE_ERROR);
  }

  static class SortablePlane {
    final float distance;
    final Plane plane;

    SortablePlane(float distance, Plane plane) {
      this.distance = distance;
      this.plane = plane;
    }
  }

  public void drawPlanes(Collection<Plane> allPlanes, Pose cameraPose, float[] cameraPerspective) {
    List<SortablePlane> sortedPlanes = new ArrayList<>();
    float[] normal = new float[3];
    float cameraX = cameraPose.tx();
    float cameraY = cameraPose.ty();
    float cameraZ = cameraPose.tz();
    for (Plane plane : allPlanes) {
      if (plane.getTrackingState() != TrackingState.TRACKING || plane.getSubsumedBy() != null) {
        continue;
      }

      Pose center = plane.getCenterPose();
      center.getTransformedAxis(1, 1.0f, normal, 0);
      float distance =
          (cameraX - center.tx()) * normal[0]
              + (cameraY - center.ty()) * normal[1]
              + (cameraZ - center.tz()) * normal[2];
      if (distance < 0) {
        continue;
      }
      sortedPlanes.add(new SortablePlane(distance, plane));
    }
    Collections.sort(
        sortedPlanes,
        new Comparator<SortablePlane>() {
          @Override
          public int compare(SortablePlane a, SortablePlane b) {
            return Float.compare(a.distance, b.distance);
          }
        });

    float[] cameraView = new float[16];
    cameraPose.inverse().toMatrix(cameraView, 0);

    GLES20.glClearColor(1, 1, 1, 1);
    GLES20.glColorMask(false, false, false, true);
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glColorMask(true, true, true, true);

    GLES20.glDepthMask(false);

    GLES20.glEnable(GLES20.GL_BLEND);
    GLES20.glBlendFuncSeparate(
        GLES20.GL_DST_ALPHA, GLES20.GL_ONE,
        GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA);

    GLES20.glUseProgram(planeProgram);

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
    GLES20.glUniform1i(textureUniform, 0);

    GLES20.glUniform4fv(gridControlUniform, 1, GRID_CONTROL, 0);

    GLES20.glEnableVertexAttribArray(planeXZPositionAlphaAttribute);

    Utils.checkGLError(ERROR_TAG, PLANE_SETUP_ERROR);

    for (SortablePlane sortedPlane : sortedPlanes) {
      Plane plane = sortedPlane.plane;
      float[] planeMatrix = new float[16];
      plane.getCenterPose().toMatrix(planeMatrix, 0);

      updatePlaneParameters(
          planeMatrix, plane.getExtentX(), plane.getExtentZ(), plane.getPolygon());

      Integer planeIndex = planeIndexMap.get(plane);
      if (planeIndex == null) {
        planeIndex = planeIndexMap.size();
        planeIndexMap.put(plane, planeIndex);
      }

      colorRgbaToFloat(planeColor, colorValue);
      GLES20.glUniform4fv(lineColorUniform, 1, planeColor, 0);
      GLES20.glUniform4fv(dotColorUniform, 1, planeColor, 0);

      float angleRadians = planeIndex * 0.144f;
      float uScale = DOTS_PER_METER;
      float vScale = DOTS_PER_METER * EQUILATERAL_TRIANGLE_SCALE;
      planeAngleUvMatrix[0] = +(float) Math.cos(angleRadians) * uScale;
      planeAngleUvMatrix[1] = -(float) Math.sin(angleRadians) * vScale;
      planeAngleUvMatrix[2] = +(float) Math.sin(angleRadians) * uScale;
      planeAngleUvMatrix[3] = +(float) Math.cos(angleRadians) * vScale;
      GLES20.glUniformMatrix2fv(planeUvMatrixUniform, 1, false, planeAngleUvMatrix, 0);

      draw(cameraView, cameraPerspective);
    }

    GLES20.glDisableVertexAttribArray(planeXZPositionAlphaAttribute);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    GLES20.glDisable(GLES20.GL_BLEND);
    GLES20.glDepthMask(true);

    Utils.checkGLError(ERROR_TAG, CLEAN_UP);
  }

  private static void colorRgbaToFloat(float[] planeColor, int colorRgba) {
    planeColor[0] = ((float) ((colorRgba >> 24) & 0xff)) / 255.0f;
    planeColor[1] = ((float) ((colorRgba >> 16) & 0xff)) / 255.0f;
    planeColor[2] = ((float) ((colorRgba >> 8) & 0xff)) / 255.0f;
    planeColor[3] = ((float) ((colorRgba >> 0) & 0xff)) / 255.0f;
  }

  public static void setPlaneColor(int color) {
    colorValue = color;
  }

  public static void setPlaneTexture(String planeTexture) {
    PLANE_TEXTURE = planeTexture;
  }
}
