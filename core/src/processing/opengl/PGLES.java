package processing.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.EGLConfigChooser;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;
import processing.core.PApplet;
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
  public static EGLContext context;

  /** The current surface view */
  public static GLSurfaceView glview;

  // ........................................................

  // Internal objects

  /** The renderer object driving the rendering loop, analogous to the
   * GLEventListener in JOGL */
  protected static AndroidRenderer renderer;
  protected static AndroidConfigChooser configChooser;

  // ........................................................

  // Static initialization for some parameters that need to be different for
  // GLES

  static {
    MIN_DIRECT_BUFFER_SIZE = 1;
    INDEX_TYPE             = GLES20.GL_UNSIGNED_SHORT;

    SAVE_SURFACE_TO_PIXELS_HACK = false;
    MIPMAPS_ENABLED     = false;

    DEFAULT_IN_VERTICES   = 16;
    DEFAULT_IN_EDGES      = 32;
    DEFAULT_IN_TEXTURES   = 16;
    DEFAULT_TESS_VERTICES = 16;
    DEFAULT_TESS_INDICES  = 32;

    MIN_FONT_TEX_SIZE = 128;
    MAX_FONT_TEX_SIZE = 512;

    MAX_CAPS_JOINS_LENGTH = 1000;
  }

  public static final boolean ENABLE_MULTISAMPLING = false;

  // Some EGL constants needed to initialize a GLES2 context.
  protected static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
  protected static final int EGL_OPENGL_ES2_BIT         = 0x0004;

  // Coverage multisampling identifiers for nVidia Tegra2
  protected static final int EGL_COVERAGE_BUFFERS_NV    = 0x30E0;
  protected static final int EGL_COVERAGE_SAMPLES_NV    = 0x30E1;

  ///////////////////////////////////////////////////////////

  // Initialization, finalization


  public PGLES(PGraphicsOpenGL pg) {
    super(pg);
    glu = new PGLU();
  }


  @Override
  public GLSurfaceView getCanvas() {
    return glview;
  }


  @Override
  protected void setFps(float fps) { }


  @Override
  protected void initSurface(int antialias) {
    glview = (GLSurfaceView)pg.parent.getSurfaceView();
    reqNumSamples = qualityToSamples(antialias);

    registerListeners();

    fboLayerCreated = false;
    fboLayerInUse = false;
    firstFrame = true;
    setFps = false;
  }


  @Override
  protected void reinitSurface() { }


  @Override
  protected void registerListeners() { }


  ///////////////////////////////////////////////////////////

  // Frame rendering


  @Override
  protected void getGL(PGL pgl) {
    PGLES pgles = (PGLES)pgl;
    this.gl = pgles.gl;
  }


  @Override
  protected boolean canDraw() {
    return true;
  }


  @Override
  protected void requestFocus() { }


  @Override
  protected void requestDraw() {
    if (pg.initialized && pg.parent.canDraw()) {
      glview.requestRender();
    }
  }


  @Override
  protected void swapBuffers() { }


  ///////////////////////////////////////////////////////////

  // Android specific classes (Renderer, ConfigChooser)


  public AndroidRenderer getRenderer() {
    renderer = new AndroidRenderer();
    return renderer;
  }


  public AndroidContextFactory getContextFactory() {
    return new AndroidContextFactory();
  }


  public AndroidConfigChooser getConfigChooser() {
    configChooser = new AndroidConfigChooser(5, 6, 5, 4, 16, 1);
    return configChooser;
  }


  public AndroidConfigChooser getConfigChooser(int r, int g, int b, int a,
                                               int d, int s) {
    configChooser = new AndroidConfigChooser(r, g, b, a, d, s);
    return configChooser;
  }


  protected class AndroidRenderer implements Renderer {
    public AndroidRenderer() {
    }

    public void onDrawFrame(GL10 igl) {
      gl = igl;
      glThread = Thread.currentThread();
      pg.parent.handleDraw();
    }

    public void onSurfaceChanged(GL10 igl, int iwidth, int iheight) {
      gl = igl;

      // Here is where we should initialize native libs...
      // lib.init(iwidth, iheight);

      pg.setSize(iwidth, iheight);
    }

    public void onSurfaceCreated(GL10 igl, EGLConfig config) {
      gl = igl;
      context = ((EGL10)EGLContext.getEGL()).eglGetCurrentContext();
      glContext = context.hashCode();

      if (!hasFBOs()) {
        throw new RuntimeException(MISSING_FBO_ERROR);
      }
      if (!hasShaders()) {
        throw new RuntimeException(MISSING_GLSL_ERROR);
      }
    }
  }


  protected class AndroidContextFactory implements
    GLSurfaceView.EGLContextFactory {
    public EGLContext createContext(EGL10 egl, EGLDisplay display,
        EGLConfig eglConfig) {
      int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2,
                            EGL10.EGL_NONE };
      EGLContext context = egl.eglCreateContext(display, eglConfig,
                                                EGL10.EGL_NO_CONTEXT,
                                                attrib_list);
      return context;
    }

    public void destroyContext(EGL10 egl, EGLDisplay display,
                               EGLContext context) {
      egl.eglDestroyContext(display, context);
    }
  }


  protected class AndroidConfigChooser implements EGLConfigChooser {
    // Desired size (in bits) for the rgba color, depth and stencil buffers.
    public int redTarget;
    public int greenTarget;
    public int blueTarget;
    public int alphaTarget;
    public int depthTarget;
    public int stencilTarget;

    // Actual rgba color, depth and stencil sizes (in bits) supported by the
    // device.
    public int redBits;
    public int greenBits;
    public int blueBits;
    public int alphaBits;
    public int depthBits;
    public int stencilBits;
    public int[] tempValue = new int[1];

    // The attributes we want in the frame buffer configuration for Processing.
    // For more details on other attributes, see:
    // http://www.khronos.org/opengles/documentation/opengles1_0/html/eglChooseConfig.html
    protected int[] configAttribsGL_MSAA = {
      EGL10.EGL_RED_SIZE, 5,
      EGL10.EGL_GREEN_SIZE, 6,
      EGL10.EGL_BLUE_SIZE, 5,
      EGL10.EGL_ALPHA_SIZE, 4,
      EGL10.EGL_DEPTH_SIZE, 16,
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_SAMPLE_BUFFERS, 1,
      EGL10.EGL_SAMPLES, 2,
      EGL10.EGL_NONE };

    protected int[] configAttribsGL_CovMSAA = {
      EGL10.EGL_RED_SIZE, 5,
      EGL10.EGL_GREEN_SIZE, 6,
      EGL10.EGL_BLUE_SIZE, 5,
      EGL10.EGL_ALPHA_SIZE, 4,
      EGL10.EGL_DEPTH_SIZE, 16,
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL_COVERAGE_BUFFERS_NV, 1,
      EGL_COVERAGE_SAMPLES_NV, 2,
      EGL10.EGL_NONE };

    protected int[] configAttribsGL_NoMSAA = {
      EGL10.EGL_RED_SIZE, 5,
      EGL10.EGL_GREEN_SIZE, 6,
      EGL10.EGL_BLUE_SIZE, 5,
      EGL10.EGL_ALPHA_SIZE, 4,
      EGL10.EGL_DEPTH_SIZE, 16,
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_NONE };

    protected int[] configAttribsGL_Good = {
      EGL10.EGL_RED_SIZE, 8,
      EGL10.EGL_GREEN_SIZE, 8,
      EGL10.EGL_BLUE_SIZE, 8,
      EGL10.EGL_ALPHA_SIZE, 8,
      EGL10.EGL_DEPTH_SIZE, 16,
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_NONE };

    protected int[] configAttribsGL_TestMSAA = {
      EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
      EGL10.EGL_SAMPLE_BUFFERS, 1,
      EGL10.EGL_SAMPLES, 2,
      EGL10.EGL_NONE };

    public AndroidConfigChooser(int r, int g, int b, int a, int d, int s) {
      redTarget = r;
      greenTarget = g;
      blueTarget = b;
      alphaTarget = a;
      depthTarget = d;
      stencilTarget = s;
    }

    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
      EGLConfig[] configs = chooseConfigWithAttribs(egl, display, configAttribsGL_MSAA);
      if (configs == null) {
        chooseConfigWithAttribs(egl, display, configAttribsGL_CovMSAA);
        if (configs == null) {
          chooseConfigWithAttribs(egl, display, configAttribsGL_NoMSAA);
        }
      }

      if (configs == null) {
        throw new IllegalArgumentException("No EGL configs match configSpec");
      }

      if (PApplet.DEBUG) {
        for (EGLConfig config : configs) {
          String configStr = "P3D - selected EGL config : "
            + printConfig(egl, display, config);
          System.out.println(configStr);
        }
      }

      // Now return the configuration that best matches the target one.
      return chooseBestConfig(egl, display, configs);
    }

    public EGLConfig chooseBestConfig(EGL10 egl, EGLDisplay display,
                                      EGLConfig[] configs) {
      EGLConfig bestConfig = null;
      float bestScore = Float.MAX_VALUE;

      for (EGLConfig config : configs) {
        int gl = findConfigAttrib(egl, display, config,
                                  EGL10.EGL_RENDERABLE_TYPE, 0);
        boolean isGLES2 = (gl & EGL_OPENGL_ES2_BIT) != 0;
        if (isGLES2) {
          int d = findConfigAttrib(egl, display, config,
                                   EGL10.EGL_DEPTH_SIZE, 0);
          int s = findConfigAttrib(egl, display, config,
                                   EGL10.EGL_STENCIL_SIZE, 0);

          int r = findConfigAttrib(egl, display, config,
                                   EGL10.EGL_RED_SIZE, 0);
          int g = findConfigAttrib(egl, display, config,
                                   EGL10.EGL_GREEN_SIZE, 0);
          int b = findConfigAttrib(egl, display, config,
                                   EGL10.EGL_BLUE_SIZE, 0);
          int a = findConfigAttrib(egl, display, config,
                                   EGL10.EGL_ALPHA_SIZE, 0);

          float score = 0.20f * PApplet.abs(r - redTarget) +
                        0.20f * PApplet.abs(g - greenTarget) +
                        0.20f * PApplet.abs(b - blueTarget) +
                        0.15f * PApplet.abs(a - alphaTarget) +
                        0.15f * PApplet.abs(d - depthTarget) +
                        0.10f * PApplet.abs(s - stencilTarget);

          if (score < bestScore) {
            // We look for the config closest to the target config.
            // Closeness is measured by the score function defined above:
            // we give more weight to the RGB components, followed by the
            // alpha, depth and finally stencil bits.
            bestConfig = config;
            bestScore = score;

            redBits = r;
            greenBits = g;
            blueBits = b;
            alphaBits = a;
            depthBits = d;
            stencilBits = s;
          }
        }
      }

      if (PApplet.DEBUG) {
        String configStr = "P3D - selected EGL config : "
          + printConfig(egl, display, bestConfig);
        System.out.println(configStr);
      }
      return bestConfig;
    }

    protected String printConfig(EGL10 egl, EGLDisplay display,
                                 EGLConfig config) {
      int r = findConfigAttrib(egl, display, config,
                               EGL10.EGL_RED_SIZE, 0);
      int g = findConfigAttrib(egl, display, config,
                               EGL10.EGL_GREEN_SIZE, 0);
      int b = findConfigAttrib(egl, display, config,
                               EGL10.EGL_BLUE_SIZE, 0);
      int a = findConfigAttrib(egl, display, config,
                               EGL10.EGL_ALPHA_SIZE, 0);
      int d = findConfigAttrib(egl, display, config,
                               EGL10.EGL_DEPTH_SIZE, 0);
      int s = findConfigAttrib(egl, display, config,
                               EGL10.EGL_STENCIL_SIZE, 0);
      int type = findConfigAttrib(egl, display, config,
                                  EGL10.EGL_RENDERABLE_TYPE, 0);
      int nat = findConfigAttrib(egl, display, config,
                                 EGL10.EGL_NATIVE_RENDERABLE, 0);
      int bufSize = findConfigAttrib(egl, display, config,
                                     EGL10.EGL_BUFFER_SIZE, 0);
      int bufSurf = findConfigAttrib(egl, display, config,
                                     EGL10.EGL_RENDER_BUFFER, 0);

      return String.format("EGLConfig rgba=%d%d%d%d depth=%d stencil=%d",
                           r,g,b,a,d,s)
        + " type=" + type
        + " native=" + nat
        + " buffer size=" + bufSize
        + " buffer surface=" + bufSurf +
        String.format(" caveat=0x%04x",
                      findConfigAttrib(egl, display, config,
                                       EGL10.EGL_CONFIG_CAVEAT, 0));
    }

    protected int findConfigAttrib(EGL10 egl, EGLDisplay display,
      EGLConfig config, int attribute, int defaultValue) {
      if (egl.eglGetConfigAttrib(display, config, attribute, tempValue)) {
        return tempValue[0];
      }
      return defaultValue;
    }

     protected EGLConfig[] chooseConfigWithAttribs(EGL10 egl,
                                                   EGLDisplay display,
                                                   int[] configAttribs) {
       // Get the number of minimally matching EGL configurations
       int[] configCounts = new int[1];
       egl.eglChooseConfig(display, configAttribs, null, 0, configCounts);

       int count = configCounts[0];

       if (count <= 0) {
         //throw new IllegalArgumentException("No EGL configs match configSpec");
         return null;
       }

       // Allocate then read the array of minimally matching EGL configs
       EGLConfig[] configs = new EGLConfig[count];
       egl.eglChooseConfig(display, configAttribs, configs, count, configCounts);
       return configs;

       // Get the number of minimally matching EGL configurations
//     int[] num_config = new int[1];
//     egl.eglChooseConfig(display, configAttribsGL, null, 0, num_config);
//
//     int numConfigs = num_config[0];
//
//     if (numConfigs <= 0) {
//       throw new IllegalArgumentException("No EGL configs match configSpec");
//     }
//
//     // Allocate then read the array of minimally matching EGL configs
//     EGLConfig[] configs = new EGLConfig[numConfigs];
//     egl.eglChooseConfig(display, configAttribsGL, configs, numConfigs,
//         num_config);

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
    ALPHA_TEST      = 0x0BC0;

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

    STENCIL_INDEX  = GLES20.GL_STENCIL_INDEX;
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

    MULTISAMPLE    = 0x809D;
    POINT_SMOOTH   = 0x0B10;
    LINE_SMOOTH    = 0x0B10;
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
    GLES20.glViewport(x, y, w, h);
  }

  //////////////////////////////////////////////////////////////////////////////

  // Reading Pixels

  @Override
  public void readPixelsImpl(int x, int y, int width, int height, int format, int type, Buffer buffer) {
    GLES20.glReadPixels(x, y, width, height, format, type, buffer);
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
  public void vertexAttri4fv(int index, FloatBuffer values) {
    GLES20.glVertexAttrib4fv(index, values);
  }

  @Override
  public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset) {
    GLES20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
  }

  @Override
  public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, Buffer data) {
    GLES20.glVertexAttribPointer(index, size, type, normalized, stride, data);
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
  public void drawArrays(int mode, int first, int count) {
    GLES20.glDrawArrays(mode, first, count);
  }

  @Override
  public void drawElements(int mode, int count, int type, int offset) {
    GLES20.glDrawElements(mode, count, type, offset);
  }

  @Override
  public void drawElements(int mode, int count, int type, Buffer indices) {
    GLES20.glDrawElements(mode, count, type, indices);
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
    GLES20.glCopyTexSubImage2D(target, level, x, y, xOffset, xOffset, width, height);
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
    gl.glTexParameterf(target, pname, param);
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

  @Override
  public void alphaFunc(int func, float ref) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glAlphaFunc()"));
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
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glBlitFramebuffer()"));
  }

  @Override
  public void renderbufferStorageMultisample(int target, int samples, int format, int width, int height) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glRenderbufferStorageMultisample()"));
  }

  @Override
  public void readBuffer(int buf) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glReadBuffer()"));
  }

  @Override
  public void drawBuffer(int buf) {
    throw new RuntimeException(String.format(MISSING_GLFUNC_ERROR, "glDrawBuffer()"));
  }
}
