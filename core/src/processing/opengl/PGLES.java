/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-15 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.view.SurfaceView;
import processing.opengl.tess.PGLU;
import processing.opengl.tess.PGLUtessellator;
import processing.opengl.tess.PGLUtessellatorCallbackAdapter;

public class PGLES extends PGL {
  // ........................................................

  // Public members to access the underlying GL objects and glview

  /** Basic GLES 1.0 interface */
  public GL10 gl;

  /** GLU interface **/
  public PGLU glu;

  /** The current opengl context */
  public EGLContext context;

  /** The current surface view */
  public GLSurfaceView glview;

  // ........................................................

  // Static initialization for some parameters that need to be different for
  // GLES

  static {
    SINGLE_BUFFERED = true;

    MIN_DIRECT_BUFFER_SIZE = 1;
    INDEX_TYPE             = GLES20.GL_UNSIGNED_SHORT;

    MIPMAPS_ENABLED        = false;

    DEFAULT_IN_VERTICES   = 16;
    DEFAULT_IN_EDGES      = 32;
    DEFAULT_IN_TEXTURES   = 16;
    DEFAULT_TESS_VERTICES = 16;
    DEFAULT_TESS_INDICES  = 32;

    MIN_FONT_TEX_SIZE = 128;
    MAX_FONT_TEX_SIZE = 512;

    MAX_CAPS_JOINS_LENGTH = 1000;
  }

  // Some EGL constants needed to initialize a GLES2 context.
  public static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
  public static final int EGL_OPENGL_ES2_BIT         = 0x0004;

  // Coverage multisampling identifiers for nVidia Tegra2
  public static final int EGL_COVERAGE_BUFFERS_NV    = 0x30E0;
  public static final int EGL_COVERAGE_SAMPLES_NV    = 0x30E1;
  public static final int GL_COVERAGE_BUFFER_BIT_NV  = 0x8000;

  public static boolean usingMultisampling = false;
  public static boolean usingCoverageMultisampling = false;
  public static int multisampleCount = 1;

  ///////////////////////////////////////////////////////////

  // Initialization, finalization


  public PGLES(PGraphicsOpenGL pg) {
    super(pg);
    glu = new PGLU();
  }


  @Override
  public GLSurfaceView getNative() {
    return glview;
  }


  @Override
  protected void initSurface(int antialias) {
    SurfaceView surf = sketch.getSurfaceView();
    if (surf != null) {
      glview = (GLSurfaceView)surf;
    }
    reqNumSamples = qualityToSamples(antialias);
    registerListeners();
  }


  @Override
  protected void reinitSurface() { }


  @Override
  protected void registerListeners() { }


  @Override
  protected int getDepthBits()  {
    intBuffer.rewind();
    getIntegerv(DEPTH_BITS, intBuffer);
    return intBuffer.get(0);
  }


  @Override
  protected int getStencilBits()  {
    intBuffer.rewind();
    getIntegerv(STENCIL_BITS, intBuffer);
    return intBuffer.get(0);
  }


  @Override
  protected int getDefaultDrawBuffer()  {
    return fboLayerEnabled ? COLOR_ATTACHMENT0 : FRONT;
  }


  @Override
  protected int getDefaultReadBuffer()  {
    return fboLayerEnabled ? COLOR_ATTACHMENT0 : FRONT;
  }


  public void init(GL10 igl) {
    gl = igl;
    context = ((EGL10)EGLContext.getEGL()).eglGetCurrentContext();
    glContext = context.hashCode();
    glThread = Thread.currentThread();

    if (!hasFBOs()) {
      throw new RuntimeException(PGL.MISSING_FBO_ERROR);
    }
    if (!hasShaders()) {
      throw new RuntimeException(PGL.MISSING_GLSL_ERROR);
    }
  }


  ///////////////////////////////////////////////////////////

  // Frame rendering


  @Override
  protected float getPixelScale() {
    return 1;
  }


  @Override
  protected void getGL(PGL pgl) {
    PGLES pgles = (PGLES)pgl;
    this.gl = pgles.gl;
    setThread(pgles.glThread);
  }

  public void getGL(GL10 igl) {
    gl = igl;
    glThread = Thread.currentThread();
  }


  @Override
  protected boolean canDraw() {
    return true;
  }


  @Override
  protected void requestFocus() { }


  @Override
  protected void requestDraw() {
    if (graphics.initialized && sketch.canDraw() && glview != null) {
      glview.requestRender();
    }
  }


  @Override
  protected void swapBuffers() { }


  @Override
  protected int getGLSLVersion() {
    return 100;
  }


  @Override
  protected void initFBOLayer() {
    if (0 < sketch.frameCount) {
      IntBuffer buf = allocateDirectIntBuffer(fboWidth * fboHeight);

      if (hasReadBuffer()) readBuffer(BACK);
      readPixelsImpl(0, 0, fboWidth, fboHeight, RGBA, UNSIGNED_BYTE, buf);
      bindTexture(TEXTURE_2D, glColorTex.get(frontTex));
      texSubImage2D(TEXTURE_2D, 0, 0, 0, fboWidth, fboHeight, RGBA, UNSIGNED_BYTE, buf);

      bindTexture(TEXTURE_2D, glColorTex.get(backTex));
      texSubImage2D(TEXTURE_2D, 0, 0, 0, fboWidth, fboHeight, RGBA, UNSIGNED_BYTE, buf);

      bindTexture(TEXTURE_2D, 0);
      bindFramebufferImpl(FRAMEBUFFER, 0);
    }
  }


  ///////////////////////////////////////////////////////////

  // Tessellator interface


  @Override
  protected Tessellator createTessellator(TessellatorCallback callback) {
    return new Tessellator(callback);
  }


  protected class Tessellator implements PGL.Tessellator {
    protected PGLUtessellator tess;
    protected TessellatorCallback callback;
    protected GLUCallback gluCallback;

    public Tessellator(TessellatorCallback callback) {
      this.callback = callback;
      tess = PGLU.gluNewTess();
      gluCallback = new GLUCallback();

      PGLU.gluTessCallback(tess, PGLU.GLU_TESS_BEGIN, gluCallback);
      PGLU.gluTessCallback(tess, PGLU.GLU_TESS_END, gluCallback);
      PGLU.gluTessCallback(tess, PGLU.GLU_TESS_VERTEX, gluCallback);
      PGLU.gluTessCallback(tess, PGLU.GLU_TESS_COMBINE, gluCallback);
      PGLU.gluTessCallback(tess, PGLU.GLU_TESS_ERROR, gluCallback);
    }

    public void beginPolygon() {
      PGLU.gluTessBeginPolygon(tess, null);
    }

    public void endPolygon() {
      PGLU.gluTessEndPolygon(tess);
    }

    public void setWindingRule(int rule) {
      PGLU.gluTessProperty(tess, PGLU.GLU_TESS_WINDING_RULE, rule);
    }

    public void beginContour() {
      PGLU.gluTessBeginContour(tess);
    }

    public void endContour() {
      PGLU.gluTessEndContour(tess);
    }

    public void addVertex(double[] v) {
      PGLU.gluTessVertex(tess, v, 0, v);
    }

    protected class GLUCallback extends PGLUtessellatorCallbackAdapter {
      @Override
      public void begin(int type) {
        callback.begin(type);
      }

      @Override
      public void end() {
        callback.end();
      }

      @Override
      public void vertex(Object data) {
        callback.vertex(data);
      }

      @Override
      public void combine(double[] coords, Object[] data,
                          float[] weight, Object[] outData) {
        callback.combine(coords, data, weight, outData);
      }

      @Override
      public void error(int errnum) {
        callback.error(errnum);
      }
    }
  }


  @Override
  protected String tessError(int err) {
    return PGLU.gluErrorString(err);
  }


  ///////////////////////////////////////////////////////////

  // Font outline


  static {
    SHAPE_TEXT_SUPPORTED = false;
  }


  @Override
  protected FontOutline createFontOutline(char ch, Object font) {
    return null;
  }


  ///////////////////////////////////////////////////////////

  // Constants
  //
  // The values for constants not defined in the GLES20 interface can be found
  // in this file:
  // http://code.metager.de/source/xref/android/4.0.3/development/tools/glesv2debugger/src/com/android/glesv2debugger/GLEnum.java


  static {
    FALSE = GLES20.GL_FALSE;
    TRUE  = GLES20.GL_TRUE;

    INT            = GLES20.GL_INT;
    BYTE           = GLES20.GL_BYTE;
    SHORT          = GLES20.GL_SHORT;
    FLOAT          = GLES20.GL_FLOAT;
    BOOL           = GLES20.GL_BOOL;
    UNSIGNED_INT   = GLES20.GL_UNSIGNED_INT;
    UNSIGNED_BYTE  = GLES20.GL_UNSIGNED_BYTE;
    UNSIGNED_SHORT = GLES20.GL_UNSIGNED_SHORT;

    RGB             = GLES20.GL_RGB;
    RGBA            = GLES20.GL_RGBA;
    ALPHA           = GLES20.GL_ALPHA;
    LUMINANCE       = GLES20.GL_LUMINANCE;
    LUMINANCE_ALPHA = GLES20.GL_LUMINANCE_ALPHA;

    UNSIGNED_SHORT_5_6_5   = GLES20.GL_UNSIGNED_SHORT_5_6_5;
    UNSIGNED_SHORT_4_4_4_4 = GLES20.GL_UNSIGNED_SHORT_4_4_4_4;
    UNSIGNED_SHORT_5_5_5_1 = GLES20.GL_UNSIGNED_SHORT_5_5_5_1;

    RGBA4   = GLES20.GL_RGBA4;
    RGB5_A1 = GLES20.GL_RGB5_A1;
    RGB565  = GLES20.GL_RGB565;
    RGB8   = 0x8051;
    RGBA8  = 0x8058;
    ALPHA8 = -1;

    READ_ONLY  = -1;
    WRITE_ONLY = 0x88B9;
    READ_WRITE = -1;

    TESS_WINDING_NONZERO = PGLU.GLU_TESS_WINDING_NONZERO;
    TESS_WINDING_ODD     = PGLU.GLU_TESS_WINDING_ODD;

    GENERATE_MIPMAP_HINT = GLES20.GL_GENERATE_MIPMAP_HINT;
    FASTEST              = GLES20.GL_FASTEST;
    NICEST               = GLES20.GL_NICEST;
    DONT_CARE            = GLES20.GL_DONT_CARE;

    VENDOR                   = GLES20.GL_VENDOR;
    RENDERER                 = GLES20.GL_RENDERER;
    VERSION                  = GLES20.GL_VERSION;
    EXTENSIONS               = GLES20.GL_EXTENSIONS;
    SHADING_LANGUAGE_VERSION = GLES20.GL_SHADING_LANGUAGE_VERSION;

    MAX_SAMPLES = -1;
    SAMPLES     = GLES20.GL_SAMPLES;

    ALIASED_LINE_WIDTH_RANGE = GLES20.GL_ALIASED_LINE_WIDTH_RANGE;
    ALIASED_POINT_SIZE_RANGE = GLES20.GL_ALIASED_POINT_SIZE_RANGE;

    DEPTH_BITS   = GLES20.GL_DEPTH_BITS;
    STENCIL_BITS = GLES20.GL_STENCIL_BITS;

    CCW = GLES20.GL_CCW;
    CW  = GLES20.GL_CW;

    VIEWPORT = GLES20.GL_VIEWPORT;

    ARRAY_BUFFER         = GLES20.GL_ARRAY_BUFFER;
    ELEMENT_ARRAY_BUFFER = GLES20.GL_ELEMENT_ARRAY_BUFFER;

    MAX_VERTEX_ATTRIBS  = GLES20.GL_MAX_VERTEX_ATTRIBS;

    STATIC_DRAW  = GLES20.GL_STATIC_DRAW;
    DYNAMIC_DRAW = GLES20.GL_DYNAMIC_DRAW;
    STREAM_DRAW  = GLES20.GL_STREAM_DRAW;

    BUFFER_SIZE  = GLES20.GL_BUFFER_SIZE;
    BUFFER_USAGE = GLES20.GL_BUFFER_USAGE;

    POINTS         = GLES20.GL_POINTS;
    LINE_STRIP     = GLES20.GL_LINE_STRIP;
    LINE_LOOP      = GLES20.GL_LINE_LOOP;
    LINES          = GLES20.GL_LINES;
    TRIANGLE_FAN   = GLES20.GL_TRIANGLE_FAN;
    TRIANGLE_STRIP = GLES20.GL_TRIANGLE_STRIP;
    TRIANGLES      = GLES20.GL_TRIANGLES;

    CULL_FACE      = GLES20.GL_CULL_FACE;
    FRONT          = GLES20.GL_FRONT;
    BACK           = GLES20.GL_BACK;
    FRONT_AND_BACK = GLES20.GL_FRONT_AND_BACK;

    POLYGON_OFFSET_FILL = GLES20.GL_POLYGON_OFFSET_FILL;

    UNPACK_ALIGNMENT = GLES20.GL_UNPACK_ALIGNMENT;
    PACK_ALIGNMENT   = GLES20.GL_PACK_ALIGNMENT;

    TEXTURE_2D        = GLES20.GL_TEXTURE_2D;
    TEXTURE_RECTANGLE = -1;

    TEXTURE_BINDING_2D        = GLES20.GL_TEXTURE_BINDING_2D;
    TEXTURE_BINDING_RECTANGLE = -1;

    MAX_TEXTURE_SIZE           = GLES20.GL_MAX_TEXTURE_SIZE;
    TEXTURE_MAX_ANISOTROPY     = 0x84FE;
    MAX_TEXTURE_MAX_ANISOTROPY = 0x84FF;

    MAX_VERTEX_TEXTURE_IMAGE_UNITS   = GLES20.GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS;
    MAX_TEXTURE_IMAGE_UNITS          = GLES20.GL_MAX_TEXTURE_IMAGE_UNITS;
    MAX_COMBINED_TEXTURE_IMAGE_UNITS = GLES20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS;

    NUM_COMPRESSED_TEXTURE_FORMATS = GLES20.GL_NUM_COMPRESSED_TEXTURE_FORMATS;
    COMPRESSED_TEXTURE_FORMATS     = GLES20.GL_COMPRESSED_TEXTURE_FORMATS;

    NEAREST               = GLES20.GL_NEAREST;
    LINEAR                = GLES20.GL_LINEAR;
    LINEAR_MIPMAP_NEAREST = GLES20.GL_LINEAR_MIPMAP_NEAREST;
    LINEAR_MIPMAP_LINEAR  = GLES20.GL_LINEAR_MIPMAP_LINEAR;

    CLAMP_TO_EDGE = GLES20.GL_CLAMP_TO_EDGE;
    REPEAT        = GLES20.GL_REPEAT;

    TEXTURE0           = GLES20.GL_TEXTURE0;
    TEXTURE1           = GLES20.GL_TEXTURE1;
    TEXTURE2           = GLES20.GL_TEXTURE2;
    TEXTURE3           = GLES20.GL_TEXTURE3;
    TEXTURE_MIN_FILTER = GLES20.GL_TEXTURE_MIN_FILTER;
    TEXTURE_MAG_FILTER = GLES20.GL_TEXTURE_MAG_FILTER;
    TEXTURE_WRAP_S     = GLES20.GL_TEXTURE_WRAP_S;
    TEXTURE_WRAP_T     = GLES20.GL_TEXTURE_WRAP_T;
    TEXTURE_WRAP_R     = 0x8072;

    TEXTURE_CUBE_MAP = GLES20.GL_TEXTURE_CUBE_MAP;
    TEXTURE_CUBE_MAP_POSITIVE_X = GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_X;
    TEXTURE_CUBE_MAP_POSITIVE_Y = GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Y;
    TEXTURE_CUBE_MAP_POSITIVE_Z = GLES20.GL_TEXTURE_CUBE_MAP_POSITIVE_Z;
    TEXTURE_CUBE_MAP_NEGATIVE_X = GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_X;
    TEXTURE_CUBE_MAP_NEGATIVE_Y = GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Y;
    TEXTURE_CUBE_MAP_NEGATIVE_Z = GLES20.GL_TEXTURE_CUBE_MAP_NEGATIVE_Z;

    VERTEX_SHADER        = GLES20.GL_VERTEX_SHADER;
    FRAGMENT_SHADER      = GLES20.GL_FRAGMENT_SHADER;
    INFO_LOG_LENGTH      = GLES20.GL_INFO_LOG_LENGTH;
    SHADER_SOURCE_LENGTH = GLES20.GL_SHADER_SOURCE_LENGTH;
    COMPILE_STATUS       = GLES20.GL_COMPILE_STATUS;
    LINK_STATUS          = GLES20.GL_LINK_STATUS;
    VALIDATE_STATUS      = GLES20.GL_VALIDATE_STATUS;
    SHADER_TYPE          = GLES20.GL_SHADER_TYPE;
    DELETE_STATUS        = GLES20.GL_DELETE_STATUS;

    FLOAT_VEC2   = GLES20.GL_FLOAT_VEC2;
    FLOAT_VEC3   = GLES20.GL_FLOAT_VEC3;
    FLOAT_VEC4   = GLES20.GL_FLOAT_VEC4;
    FLOAT_MAT2   = GLES20.GL_FLOAT_MAT2;
    FLOAT_MAT3   = GLES20.GL_FLOAT_MAT3;
    FLOAT_MAT4   = GLES20.GL_FLOAT_MAT4;
    INT_VEC2     = GLES20.GL_INT_VEC2;
    INT_VEC3     = GLES20.GL_INT_VEC3;
    INT_VEC4     = GLES20.GL_INT_VEC4;
    BOOL_VEC2    = GLES20.GL_BOOL_VEC2;
    BOOL_VEC3    = GLES20.GL_BOOL_VEC3;
    BOOL_VEC4    = GLES20.GL_BOOL_VEC4;
    SAMPLER_2D   = GLES20.GL_SAMPLER_2D;
    SAMPLER_CUBE = GLES20.GL_SAMPLER_CUBE;

    LOW_FLOAT    = GLES20.GL_LOW_FLOAT;
    MEDIUM_FLOAT = GLES20.GL_MEDIUM_FLOAT;
    HIGH_FLOAT   = GLES20.GL_HIGH_FLOAT;
    LOW_INT      = GLES20.GL_LOW_INT;
    MEDIUM_INT   = GLES20.GL_MEDIUM_INT;
    HIGH_INT     = GLES20.GL_HIGH_INT;

    CURRENT_VERTEX_ATTRIB = GLES20.GL_CURRENT_VERTEX_ATTRIB;

    VERTEX_ATTRIB_ARRAY_BUFFER_BINDING = GLES20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING;
    VERTEX_ATTRIB_ARRAY_ENABLED        = GLES20.GL_VERTEX_ATTRIB_ARRAY_ENABLED;
    VERTEX_ATTRIB_ARRAY_SIZE           = GLES20.GL_VERTEX_ATTRIB_ARRAY_SIZE;
    VERTEX_ATTRIB_ARRAY_STRIDE         = GLES20.GL_VERTEX_ATTRIB_ARRAY_STRIDE;
    VERTEX_ATTRIB_ARRAY_TYPE           = GLES20.GL_VERTEX_ATTRIB_ARRAY_TYPE;
    VERTEX_ATTRIB_ARRAY_NORMALIZED     = GLES20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED;
    VERTEX_ATTRIB_ARRAY_POINTER        = GLES20.GL_VERTEX_ATTRIB_ARRAY_POINTER;

    BLEND               = GLES20.GL_BLEND;
    ONE                 = GLES20.GL_ONE;
    ZERO                = GLES20.GL_ZERO;
    SRC_ALPHA           = GLES20.GL_SRC_ALPHA;
    DST_ALPHA           = GLES20.GL_DST_ALPHA;
    ONE_MINUS_SRC_ALPHA = GLES20.GL_ONE_MINUS_SRC_ALPHA;
    ONE_MINUS_DST_COLOR = GLES20.GL_ONE_MINUS_DST_COLOR;
    ONE_MINUS_SRC_COLOR = GLES20.GL_ONE_MINUS_SRC_COLOR;
    DST_COLOR           = GLES20.GL_DST_COLOR;
    SRC_COLOR           = GLES20.GL_SRC_COLOR;

    SAMPLE_ALPHA_TO_COVERAGE = GLES20.GL_SAMPLE_ALPHA_TO_COVERAGE;
    SAMPLE_COVERAGE          = GLES20.GL_SAMPLE_COVERAGE;

    KEEP      = GLES20.GL_KEEP;
    REPLACE   = GLES20.GL_REPLACE;
    INCR      = GLES20.GL_INCR;
    DECR      = GLES20.GL_DECR;
    INVERT    = GLES20.GL_INVERT;
    INCR_WRAP = GLES20.GL_INCR_WRAP;
    DECR_WRAP = GLES20.GL_DECR_WRAP;
    NEVER     = GLES20.GL_NEVER;
    ALWAYS    = GLES20.GL_ALWAYS;

    EQUAL    = GLES20.GL_EQUAL;
    LESS     = GLES20.GL_LESS;
    LEQUAL   = GLES20.GL_LEQUAL;
    GREATER  = GLES20.GL_GREATER;
    GEQUAL   = GLES20.GL_GEQUAL;
    NOTEQUAL = GLES20.GL_NOTEQUAL;

    FUNC_ADD              = GLES20.GL_FUNC_ADD;
    FUNC_MIN              = 0x8007;
    FUNC_MAX              = 0x8008;
    FUNC_REVERSE_SUBTRACT = GLES20.GL_FUNC_REVERSE_SUBTRACT;
    FUNC_SUBTRACT         = GLES20.GL_FUNC_SUBTRACT;

    DITHER = GLES20.GL_DITHER;

    CONSTANT_COLOR           = GLES20.GL_CONSTANT_COLOR;
    CONSTANT_ALPHA           = GLES20.GL_CONSTANT_ALPHA;
    ONE_MINUS_CONSTANT_COLOR = GLES20.GL_ONE_MINUS_CONSTANT_COLOR;
    ONE_MINUS_CONSTANT_ALPHA = GLES20.GL_ONE_MINUS_CONSTANT_ALPHA;
    SRC_ALPHA_SATURATE       = GLES20.GL_SRC_ALPHA_SATURATE;

    SCISSOR_TEST    = GLES20.GL_SCISSOR_TEST;
    STENCIL_TEST    = GLES20.GL_STENCIL_TEST;
    DEPTH_TEST      = GLES20.GL_DEPTH_TEST;
    DEPTH_WRITEMASK = GLES20.GL_DEPTH_WRITEMASK;

    COLOR_BUFFER_BIT   = GLES20.GL_COLOR_BUFFER_BIT;
    DEPTH_BUFFER_BIT   = GLES20.GL_DEPTH_BUFFER_BIT;
    STENCIL_BUFFER_BIT = GLES20.GL_STENCIL_BUFFER_BIT;

    FRAMEBUFFER        = GLES20.GL_FRAMEBUFFER;
    COLOR_ATTACHMENT0  = GLES20.GL_COLOR_ATTACHMENT0;
    COLOR_ATTACHMENT1  = -1;
    COLOR_ATTACHMENT2  = -1;
    COLOR_ATTACHMENT3  = -1;
    RENDERBUFFER       = GLES20.GL_RENDERBUFFER;
    DEPTH_ATTACHMENT   = GLES20.GL_DEPTH_ATTACHMENT;
    STENCIL_ATTACHMENT = GLES20.GL_STENCIL_ATTACHMENT;
    READ_FRAMEBUFFER   = -1;
    DRAW_FRAMEBUFFER   = -1;

    DEPTH24_STENCIL8 = 0x88F0;

    DEPTH_COMPONENT   = GLES20.GL_DEPTH_COMPONENT;
    DEPTH_COMPONENT16 = GLES20.GL_DEPTH_COMPONENT16;
    DEPTH_COMPONENT24 = 0x81A6;
    DEPTH_COMPONENT32 = 0x81A7;

    STENCIL_INDEX  = 6401; // GLES20.GL_STENCIL_INDEX is marked as deprecated
    STENCIL_INDEX1 = 0x8D46;
    STENCIL_INDEX4 = 0x8D47;
    STENCIL_INDEX8 = GLES20.GL_STENCIL_INDEX8;

    DEPTH_STENCIL = 0x84F9;

    FRAMEBUFFER_COMPLETE                      = GLES20.GL_FRAMEBUFFER_COMPLETE;
    FRAMEBUFFER_INCOMPLETE_ATTACHMENT         = GLES20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT;
    FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = GLES20.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT;
    FRAMEBUFFER_INCOMPLETE_DIMENSIONS         = GLES20.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS;
    FRAMEBUFFER_INCOMPLETE_FORMATS            = 0x8CDA;
    FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER        = -1;
    FRAMEBUFFER_INCOMPLETE_READ_BUFFER        = -1;
    FRAMEBUFFER_UNSUPPORTED                   = GLES20.GL_FRAMEBUFFER_UNSUPPORTED;

    FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE           = GLES20.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
    FRAMEBUFFER_ATTACHMENT_OBJECT_NAME           = GLES20.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
    FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL         = GLES20.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL;
    FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE = GLES20.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE;

    RENDERBUFFER_WIDTH           = GLES20.GL_RENDERBUFFER_WIDTH;
    RENDERBUFFER_HEIGHT          = GLES20.GL_RENDERBUFFER_HEIGHT;
    RENDERBUFFER_RED_SIZE        = GLES20.GL_RENDERBUFFER_RED_SIZE;
    RENDERBUFFER_GREEN_SIZE      = GLES20.GL_RENDERBUFFER_GREEN_SIZE;
    RENDERBUFFER_BLUE_SIZE       = GLES20.GL_RENDERBUFFER_BLUE_SIZE;
    RENDERBUFFER_ALPHA_SIZE      = GLES20.GL_RENDERBUFFER_ALPHA_SIZE;
    RENDERBUFFER_DEPTH_SIZE      = GLES20.GL_RENDERBUFFER_DEPTH_SIZE;
    RENDERBUFFER_STENCIL_SIZE    = GLES20.GL_RENDERBUFFER_STENCIL_SIZE;
    RENDERBUFFER_INTERNAL_FORMAT = GLES20.GL_RENDERBUFFER_INTERNAL_FORMAT;

    MULTISAMPLE    = -1;
    LINE_SMOOTH    = -1;
    POLYGON_SMOOTH = -1;
  }

  ///////////////////////////////////////////////////////////

  // Special Functions

  @Override
  public void flush() {
    GLES20.glFlush();
  }

  @Override
  public void finish() {
    GLES20.glFinish();
  }

  @Override
  public void hint(int target, int hint) {
    GLES20.glHint(target, hint);
  }

  ///////////////////////////////////////////////////////////

  // State and State Requests

  @Override
  public void enable(int value) {
    if (-1 < value) {
      GLES20.glEnable(value);
    }
  }

  @Override
  public void disable(int value) {
    if (-1 < value) {
      GLES20.glDisable(value);
    }
  }

  @Override
  public void getBooleanv(int name, IntBuffer values) {
    if (-1 < name) {
      GLES20.glGetBooleanv(name, values);
    } else {
      fillIntBuffer(values, 0, values.capacity(), 0);
    }
  }

  @Override
  public void getIntegerv(int value, IntBuffer data) {
    if (-1 < value) {
      GLES20.glGetIntegerv(value, data);
    } else {
      fillIntBuffer(data, 0, data.capacity() - 1, 0);
    }
  }

  @Override
  public void getFloatv(int value, FloatBuffer data) {
    if (-1 < value) {
      GLES20.glGetFloatv(value, data);
    } else {
      fillFloatBuffer(data, 0, data.capacity() - 1, 0);
    }
  }

  @Override
  public boolean isEnabled(int value) {
    return GLES20.glIsEnabled(value);
  }

  @Override
  public String getString(int name) {
    return GLES20.glGetString(name);
  }

  ///////////////////////////////////////////////////////////

  // Error Handling

  @Override
  public int getError() {
    return GLES20.glGetError();
  }

  @Override
  public String errorString(int err) {
    return GLU.gluErrorString(err);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Buffer Objects

  @Override
  public void genBuffers(int n, IntBuffer buffers) {
    GLES20.glGenBuffers(n, buffers);
  }

  @Override
  public void deleteBuffers(int n, IntBuffer buffers) {
    GLES20.glDeleteBuffers(n, buffers);
  }

  @Override
  public void bindBuffer(int target, int buffer) {
    GLES20.glBindBuffer(target, buffer);
  }

  @Override
  public void bufferData(int target, int size, Buffer data, int usage) {
    GLES20.glBufferData(target, size, data, usage);
  }

  @Override
  public void bufferSubData(int target, int offset, int size, Buffer data) {
    GLES20.glBufferSubData(target, offset, size, data);
  }

  @Override
  public void isBuffer(int buffer) {
    GLES20.glIsBuffer(buffer);
  }

  @Override
  public void getBufferParameteriv(int target, int value, IntBuffer data) {
    GLES20.glGetBufferParameteriv(target, value, data);
  }

  @Override
  public ByteBuffer mapBuffer(int target, int access) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glMapBuffer"));
  }

  @Override
  public ByteBuffer mapBufferRange(int target, int offset, int length, int access) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glMapBufferRange"));
  }

  @Override
  public void unmapBuffer(int target) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glUnmapBuffer"));
  }

  //////////////////////////////////////////////////////////////////////////////

  // Viewport and Clipping

  @Override
  public void depthRangef(float n, float f) {
    GLES20.glDepthRangef(n, f);
  }

  @Override
  public void viewport(int x, int y, int w, int h) {
    float scale = getPixelScale();
    viewportImpl((int)scale * x, (int)(scale * y), (int)(scale * w), (int)(scale * h));
  }

  @Override
  protected void viewportImpl(int x, int y, int w, int h) {
    GLES20.glViewport(x, y, w, h);
  }


  //////////////////////////////////////////////////////////////////////////////

  // Reading Pixels

  @Override
  public void readPixelsImpl(int x, int y, int width, int height, int format, int type, Buffer buffer) {
    GLES20.glReadPixels(x, y, width, height, format, type, buffer);
  }


  @Override
  protected void readPixelsImpl(int x, int y, int width, int height, int format,
                                int type, long offset) {
    // TODO Auto-generated method stub
  }


  //////////////////////////////////////////////////////////////////////////////

  // Vertices

  @Override
  public void vertexAttrib1f(int index, float value) {
    GLES20.glVertexAttrib1f(index, value);
  }

  @Override
  public void vertexAttrib2f(int index, float value0, float value1) {
    GLES20.glVertexAttrib2f(index, value0, value1);
  }

  @Override
  public void vertexAttrib3f(int index, float value0, float value1, float value2) {
    GLES20.glVertexAttrib3f(index, value0, value1, value2);
  }

  @Override
  public void vertexAttrib4f(int index, float value0, float value1, float value2, float value3) {
    GLES20.glVertexAttrib4f(index, value0, value1, value2, value3);
  }

  @Override
  public void vertexAttrib1fv(int index, FloatBuffer values) {
    GLES20.glVertexAttrib1fv(index, values);
  }

  @Override
  public void vertexAttrib2fv(int index, FloatBuffer values) {
    GLES20.glVertexAttrib2fv(index, values);
  }

  @Override
  public void vertexAttrib3fv(int index, FloatBuffer values) {
    GLES20.glVertexAttrib3fv(index, values);
  }

  @Override
  public void vertexAttrib4fv(int index, FloatBuffer values) {
    GLES20.glVertexAttrib4fv(index, values);
  }

  @Override
  public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
    GLES20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
  }

  @Override
  public void enableVertexAttribArray(int index) {
    GLES20.glEnableVertexAttribArray(index);
  }

  @Override
  public void disableVertexAttribArray(int index) {
    GLES20.glDisableVertexAttribArray(index);
  }

  @Override
  public void drawArraysImpl(int mode, int first, int count) {
    GLES20.glDrawArrays(mode, first, count);
  }

  @Override
  public void drawElementsImpl(int mode, int count, int type, int offset) {
    GLES20.glDrawElements(mode, count, type, offset);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Rasterization

  @Override
  public void lineWidth(float width) {
    GLES20.glLineWidth(width);
  }

  @Override
  public void frontFace(int dir) {
    GLES20.glFrontFace(dir);
  }

  @Override
  public void cullFace(int mode) {
    GLES20.glCullFace(mode);
  }

  @Override
  public void polygonOffset(float factor, float units) {
    GLES20.glPolygonOffset(factor, units);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Pixel Rectangles

  @Override
  public void pixelStorei(int pname, int param) {
    GLES20.glPixelStorei(pname, param);
  }

  ///////////////////////////////////////////////////////////

  // Texturing

  @Override
  public void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, Buffer data) {
    GLES20.glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);
  }

  @Override
  public void copyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
    GLES20.glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
  }

  @Override
  public void texSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, Buffer data) {
    GLES20.glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, data);
  }

  @Override
  public void copyTexSubImage2D(int target, int level, int xOffset, int yOffset, int x, int y, int width, int height) {
    GLES20.glCopyTexSubImage2D(target, level, x, y, xOffset, yOffset, width, height);
  }

  @Override
  public void compressedTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int imageSize, Buffer data) {
    GLES20.glCompressedTexImage2D(target, level, internalFormat, width, height, border, imageSize, data);
  }

  @Override
  public void compressedTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int imageSize, Buffer data) {
    GLES20.glCompressedTexSubImage2D(target, level, xOffset, yOffset, width, height, format, imageSize, data);
  }

  @Override
  public void texParameteri(int target, int pname, int param) {
    GLES20.glTexParameteri(target, pname, param);
  }

  @Override
  public void texParameterf(int target, int pname, float param) {
    GLES20.glTexParameterf(target, pname, param);
  }

  @Override
  public void texParameteriv(int target, int pname, IntBuffer params) {
    GLES20.glTexParameteriv(target, pname, params);
  }

  @Override
  public void texParameterfv(int target, int pname, FloatBuffer params) {
    GLES20.glTexParameterfv(target, pname, params);
  }

  @Override
  public void generateMipmap(int target) {
    GLES20.glGenerateMipmap(target);
  }

  @Override
  public void genTextures(int n, IntBuffer textures) {
    GLES20.glGenTextures(n, textures);
  }

  @Override
  public void deleteTextures(int n, IntBuffer textures) {
    GLES20.glDeleteTextures(n, textures);
  }

  @Override
  public void getTexParameteriv(int target, int pname, IntBuffer params) {
    GLES20.glGetTexParameteriv(target, pname, params);
  }

  @Override
  public void getTexParameterfv(int target, int pname, FloatBuffer params) {
    GLES20.glGetTexParameterfv(target, pname, params);
  }

  @Override
  public boolean isTexture(int texture) {
    return GLES20.glIsTexture(texture);
  }

  @Override
  protected void activeTextureImpl(int texture) {
    GLES20.glActiveTexture(texture);
  }

  @Override
  protected void bindTextureImpl(int target, int texture) {
    GLES20.glBindTexture(target, texture);
  }

  ///////////////////////////////////////////////////////////

  // Shaders and Programs

  @Override
  public int createShader(int type) {
    return GLES20.glCreateShader(type);
  }

  @Override
  public void shaderSource(int shader, String source) {
    GLES20.glShaderSource(shader, source);
  }

  @Override
  public void compileShader(int shader) {
    GLES20.glCompileShader(shader);
  }

  @Override
  public void releaseShaderCompiler() {
    GLES20.glReleaseShaderCompiler();
  }

  @Override
  public void deleteShader(int shader) {
    GLES20.glDeleteShader(shader);
  }

  @Override
  public void shaderBinary(int count, IntBuffer shaders, int binaryFormat, Buffer binary, int length) {
    GLES20.glShaderBinary(count, shaders, binaryFormat, binary, length);
  }

  @Override
  public int createProgram() {
    return GLES20.glCreateProgram();
  }

  @Override
  public void attachShader(int program, int shader) {
    GLES20.glAttachShader(program, shader);
  }

  @Override
  public void detachShader(int program, int shader) {
    GLES20.glDetachShader(program, shader);
  }

  @Override
  public void linkProgram(int program) {
    GLES20.glLinkProgram(program);
  }

  @Override
  public void useProgram(int program) {
    GLES20.glUseProgram(program);
  }

  @Override
  public void deleteProgram(int program) {
    GLES20.glDeleteProgram(program);
  }

  @Override
  public String getActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
    int[] tmp = {0, 0, 0};
    byte[] namebuf = new byte[1024];
    GLES20.glGetActiveAttrib(program, index, 1024, tmp, 0, tmp, 1, tmp, 2, namebuf, 0);
    size.put(tmp[1]);
    type.put(tmp[2]);
    String name = new String(namebuf, 0, tmp[0]);
    return name;
  }

  @Override
  public int getAttribLocation(int program, String name) {
    return GLES20.glGetAttribLocation(program, name);
  }

  @Override
  public void bindAttribLocation(int program, int index, String name) {
    GLES20.glBindAttribLocation(program, index, name);
  }

  @Override
  public int getUniformLocation(int program, String name) {
    return GLES20.glGetUniformLocation(program, name);
  }

  @Override
  public String getActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
    int[] tmp= {0, 0, 0};
    byte[] namebuf = new byte[1024];
    GLES20.glGetActiveUniform(program, index, 1024, tmp, 0, tmp, 1, tmp, 2, namebuf, 0);
    size.put(tmp[1]);
    type.put(tmp[2]);
    String name = new String(namebuf, 0, tmp[0]);
    return name;
  }

  @Override
  public void uniform1i(int location, int value) {
    GLES20.glUniform1i(location, value);
  }

  @Override
  public void uniform2i(int location, int value0, int value1) {
    GLES20.glUniform2i(location, value0, value1);
  }

  @Override
  public void uniform3i(int location, int value0, int value1, int value2) {
    GLES20.glUniform3i(location, value0, value1, value2);
  }

  @Override
  public void uniform4i(int location, int value0, int value1, int value2, int value3) {
    GLES20.glUniform4i(location, value0, value1, value2, value3);
  }

  @Override
  public void uniform1f(int location, float value) {
    GLES20.glUniform1f(location, value);
  }

  @Override
  public void uniform2f(int location, float value0, float value1) {
    GLES20.glUniform2f(location, value0, value1);
  }

  @Override
  public void uniform3f(int location, float value0, float value1, float value2) {
    GLES20.glUniform3f(location, value0, value1, value2);
  }

  @Override
  public void uniform4f(int location, float value0, float value1, float value2, float value3) {
    GLES20.glUniform4f(location, value0, value1, value2, value3);
  }

  @Override
  public void uniform1iv(int location, int count, IntBuffer v) {
    GLES20.glUniform1iv(location, count, v);
  }

  @Override
  public void uniform2iv(int location, int count, IntBuffer v) {
    GLES20.glUniform2iv(location, count, v);
  }

  @Override
  public void uniform3iv(int location, int count, IntBuffer v) {
    GLES20.glUniform3iv(location, count, v);
  }

  @Override
  public void uniform4iv(int location, int count, IntBuffer v) {
    GLES20.glUniform4iv(location, count, v);
  }

  @Override
  public void uniform1fv(int location, int count, FloatBuffer v) {
    GLES20.glUniform1fv(location, count, v);
  }

  @Override
  public void uniform2fv(int location, int count, FloatBuffer v) {
    GLES20.glUniform2fv(location, count, v);
  }

  @Override
  public void uniform3fv(int location, int count, FloatBuffer v) {
    GLES20.glUniform3fv(location, count, v);
  }

  @Override
  public void uniform4fv(int location, int count, FloatBuffer v) {
    GLES20.glUniform4fv(location, count, v);
  }

  @Override
  public void uniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer mat) {
    GLES20.glUniformMatrix2fv(location, count, transpose, mat);
  }

  @Override
  public void uniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer mat) {
    GLES20.glUniformMatrix3fv(location, count, transpose, mat);
  }

  @Override
  public void uniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer mat) {
    GLES20.glUniformMatrix4fv(location, count, transpose, mat);
  }

  @Override
  public void validateProgram(int program) {
    GLES20.glValidateProgram(program);
  }

  @Override
  public boolean isShader(int shader) {
    return GLES20.glIsShader(shader);
  }

  @Override
  public void getShaderiv(int shader, int pname, IntBuffer params) {
    GLES20.glGetShaderiv(shader, pname, params);
  }

  @Override
  public void getAttachedShaders(int program, int maxCount, IntBuffer count, IntBuffer shaders) {
    GLES20.glGetAttachedShaders(program, maxCount, count, shaders);
  }

  @Override
  public String getShaderInfoLog(int shader) {
    return GLES20.glGetShaderInfoLog(shader);
  }

  @Override
  public String getShaderSource(int shader) {
    int[] len = {0};
    byte[] buf = new byte[1024];
    GLES20.glGetShaderSource(shader, 1024, len, 0, buf, 0);
    return new String(buf, 0, len[0]);
  }

  @Override
  public void getShaderPrecisionFormat(int shaderType, int precisionType, IntBuffer range, IntBuffer precision) {
    GLES20.glGetShaderPrecisionFormat(shaderType, precisionType, range, precision);
  }

  @Override
  public void getVertexAttribfv(int index, int pname, FloatBuffer params) {
    GLES20.glGetVertexAttribfv(index, pname, params);
  }

  @Override
  public void getVertexAttribiv(int index, int pname, IntBuffer params) {
    GLES20.glGetVertexAttribiv(index, pname, params);
  }

  @Override
  public void getVertexAttribPointerv(int index, int pname, ByteBuffer data) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glGetVertexAttribPointerv()"));
  }

  @Override
  public void getUniformfv(int program, int location, FloatBuffer params) {
    GLES20.glGetUniformfv(program, location, params);
  }

  @Override
  public void getUniformiv(int program, int location, IntBuffer params) {
    GLES20.glGetUniformiv(program, location, params);
  }

  @Override
  public boolean isProgram(int program) {
    return GLES20.glIsProgram(program);
  }

  @Override
  public void getProgramiv(int program, int pname, IntBuffer params) {
    GLES20.glGetProgramiv(program, pname, params);
  }

  @Override
  public String getProgramInfoLog(int program) {
    return GLES20.glGetProgramInfoLog(program);
  }

  ///////////////////////////////////////////////////////////

  // Per-Fragment Operations

  @Override
  public void scissor(int x, int y, int w, int h) {
    GLES20.glScissor(x, y, w, h);
  }

  @Override
  public void sampleCoverage(float value, boolean invert) {
    GLES20.glSampleCoverage(value, invert);
  }

  @Override
  public void stencilFunc(int func, int ref, int mask) {
    GLES20.glStencilFunc(func, ref, mask);
  }

  @Override
  public void stencilFuncSeparate(int face, int func, int ref, int mask) {
    GLES20.glStencilFuncSeparate(face, func, ref, mask);
  }

  @Override
  public void stencilOp(int sfail, int dpfail, int dppass) {
    GLES20.glStencilOp(sfail, dpfail, dppass);
  }

  @Override
  public void stencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
    GLES20.glStencilOpSeparate(face, sfail, dpfail, dppass);
  }

  @Override
  public void depthFunc(int func) {
    GLES20.glDepthFunc(func);
  }

  @Override
  public void blendEquation(int mode) {
    GLES20.glBlendEquation(mode);
  }

  @Override
  public void blendEquationSeparate(int modeRGB, int modeAlpha) {
    GLES20.glBlendEquationSeparate(modeRGB, modeAlpha);
  }

  @Override
  public void blendFunc(int src, int dst) {
    GLES20.glBlendFunc(src, dst);
  }

  @Override
  public void blendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
    GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
  }

  @Override
  public void blendColor(float red, float green, float blue, float alpha) {
    GLES20.glBlendColor(red, green, blue, alpha);
  }

  ///////////////////////////////////////////////////////////

  // Whole Framebuffer Operations

  @Override
  public void colorMask(boolean r, boolean g, boolean b, boolean a) {
    GLES20.glColorMask(r, g, b, a);
  }

  @Override
  public void depthMask(boolean mask) {
    GLES20.glDepthMask(mask);
  }

  @Override
  public void stencilMask(int mask) {
    GLES20.glStencilMask(mask);
  }

  @Override
  public void stencilMaskSeparate(int face, int mask) {
    GLES20.glStencilMaskSeparate(face, mask);
  }

  @Override
  public void clear(int buf) {
    if (usingMultisampling && usingCoverageMultisampling) {
      buf |= GL_COVERAGE_BUFFER_BIT_NV;
    }
    GLES20.glClear(buf);
  }

  @Override
  public void clearColor(float r, float g, float b, float a) {
    GLES20.glClearColor(r, g, b, a);
  }

  @Override
  public void clearDepth(float d) {
    GLES20.glClearDepthf(d);
  }

  @Override
  public void clearStencil(int s) {
    GLES20.glClearStencil(s);
  }

  ///////////////////////////////////////////////////////////

  // Framebuffers Objects

  @Override
  protected void bindFramebufferImpl(int target, int framebuffer) {
    GLES20.glBindFramebuffer(target, framebuffer);
  }

  @Override
  public void deleteFramebuffers(int n, IntBuffer framebuffers) {
    GLES20.glDeleteFramebuffers(n, framebuffers);
  }

  @Override
  public void genFramebuffers(int n, IntBuffer framebuffers) {
    GLES20.glGenFramebuffers(n, framebuffers);
  }

  @Override
  public void bindRenderbuffer(int target, int renderbuffer) {
    GLES20.glBindRenderbuffer(target, renderbuffer);
  }

  @Override
  public void deleteRenderbuffers(int n, IntBuffer renderbuffers) {
    GLES20.glDeleteRenderbuffers(n, renderbuffers);
  }

  @Override
  public void genRenderbuffers(int n, IntBuffer renderbuffers) {
    GLES20.glGenRenderbuffers(n, renderbuffers);
  }

  @Override
  public void renderbufferStorage(int target, int internalFormat, int width, int height) {
    GLES20.glRenderbufferStorage(target, internalFormat, width, height);
  }

  @Override
  public void framebufferRenderbuffer(int target, int attachment, int rendbuferfTarget, int renderbuffer) {
    GLES20.glFramebufferRenderbuffer(target, attachment, rendbuferfTarget, renderbuffer);
  }

  @Override
  public void framebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
    GLES20.glFramebufferTexture2D(target, attachment, texTarget, texture, level);
  }

  @Override
  public int checkFramebufferStatus(int target) {
    return GLES20.glCheckFramebufferStatus(target);
  }

  @Override
  public boolean isFramebuffer(int framebuffer) {
    return GLES20.glIsFramebuffer(framebuffer);
  }

  @Override
  public void getFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params) {
    GLES20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params);
  }

  @Override
  public boolean isRenderbuffer(int renderbuffer) {
    return GLES20.glIsRenderbuffer(renderbuffer);
  }

  @Override
  public void getRenderbufferParameteriv(int target, int pname, IntBuffer params) {
    GLES20.glGetRenderbufferParameteriv(target, pname, params);
  }

  @Override
  public void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
//    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glBlitFramebuffer()"));
  }

  @Override
  public void renderbufferStorageMultisample(int target, int samples, int format, int width, int height) {
//    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glRenderbufferStorageMultisample()"));
  }

  @Override
  public void readBuffer(int buf) {
//    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glReadBuffer()"));
  }

  @Override
  public void drawBuffer(int buf) {
//    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glDrawBuffer()"));
  }


  @Override
  protected int getFontAscent(Object font) {
    // TODO Auto-generated method stub
    return 0;
  }


  @Override
  protected int getFontDescent(Object font) {
    // TODO Auto-generated method stub
    return 0;
  }


  @Override
  protected int getTextWidth(Object font, char[] buffer, int start, int stop) {
    // TODO Auto-generated method stub
    return 0;
  }


  @Override
  protected Object getDerivedFont(Object font, float size) {
    // TODO Auto-generated method stub
    return null;
  }

  //////////////////////////////////////////////////////////////////////////////

  // Synchronization

  @Override
  public long fenceSync(int condition, int flags) {
    return 0;
//    if (gl3es3 != null) {
//      return gl3es3.glFenceSync(condition, flags);
//    } else {
//      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "fenceSync()"));
//    }
  }

  @Override
  public void deleteSync(long sync) {
//    if (gl3es3 != null) {
//      gl3es3.glDeleteSync(sync);
//    } else {
//      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "deleteSync()"));
//    }
  }

  @Override
  public int clientWaitSync(long sync, int flags, long timeout) {
    return 0;
//    if (gl3es3 != null) {
//      return gl3es3.glClientWaitSync(sync, flags, timeout);
//    } else {
//      throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "clientWaitSync()"));
//    }
  }

}
