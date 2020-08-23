/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.opengl

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.EGLConfigChooser
import android.opengl.GLSurfaceView.EGLContextFactory
import android.service.wallpaper.WallpaperService
import android.support.wearable.watchface.Gles2WatchFaceService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import processing.android.AppComponent
import processing.android.PFragment
import processing.core.PApplet
import processing.core.PGraphics
import processing.core.PSurfaceNone
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10

/**
 * @author Aditya Rana
 */
open class PSurfaceGLES : PSurfaceNone {

    var pgl: PGLES? = null

    private var glsurf: SurfaceViewGLES? = null

    constructor() {

    }

    constructor(graphics: PGraphics?, component: AppComponent?, holder: SurfaceHolder?) {
        sketch = graphics?.parent
        this.graphics = graphics
        this.appcomponent = component
        pgl = (graphics as PGraphicsOpenGL).pgl as PGLES

        if (component!!.getKind() == AppComponent.FRAGMENT) {
            val frag = component as PFragment
            appactivity = frag.activity
            msurfaceView = SurfaceViewGLES(appactivity!!, null)
        } else if (component!!.getKind() == AppComponent.WALLPAPER) {
            wallpaper = component as WallpaperService
            msurfaceView = SurfaceViewGLES(wallpaper!!, holder)
        } else if (component!!.getKind() == AppComponent.WATCHFACE) {
            watchface = component as Gles2WatchFaceService
            // Set as ready here, as watch faces don't have a surface view with a
            // surfaceCreate() event to do it.
            surfaceReady = true
        }
        glsurf = msurfaceView as SurfaceViewGLES
    }

    override fun dispose() {
        super.dispose()
        if (glsurf != null) {
            glsurf!!.dispose()
            glsurf = null
        }
    }

    ///////////////////////////////////////////////////////////

    // Thread handling

    override fun callDraw() {
        appcomponent?.requestDraw()
        if (appcomponent!!.canDraw() && glsurf != null) {
            glsurf!!.requestRender()
        }
    }

    ///////////////////////////////////////////////////////////

    // GL SurfaceView

    inner class SurfaceViewGLES(context: Context, @JvmField var holder: SurfaceHolder?) : GLSurfaceView(context) {

        override fun getHolder(): SurfaceHolder? {
            return if (holder == null) {
                super.getHolder()
            } else {
                holder
            }
        }

        fun dispose() {
            super.destroyDrawingCache()
            super.onDetachedFromWindow()
        }

        override fun surfaceChanged(holder: SurfaceHolder?, format: Int, w: Int, h: Int) {
            super.surfaceChanged(holder, format, w, h)

//        if (PApplet.DEBUG) {
//          System.out.println("SketchSurfaceView3D.surfaceChanged() " + w + " " + h);
//        }
//        System.out.println("SketchSurfaceView3D.surfaceChanged() " + w + " " + h + " " + sketch);
//        sketch.surfaceChanged();
        }

        override fun surfaceCreated(holder: SurfaceHolder?) {
            super.surfaceCreated(holder)
            surfaceReady = true
            if (requestedThreadStart) {
                startThread()
            }
            if (PApplet.DEBUG) {
                println("surfaceCreated()")
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder?) {
            super.surfaceDestroyed(holder)

            if (PApplet.DEBUG) {
                println("surfaceDestroyed()")
            }
        }

        // Inform the view that the window focus has changed.
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            sketch?.surfaceWindowFocusChanged(hasFocus)
        }

        // Do we need these to capture events...?
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val fullscreen = sketch?.width == sketch?.displayWidth &&
                    sketch?.height == sketch?.displayHeight
            if (fullscreen && PApplet.SDK < 19) {
                // The best we can do pre-KitKat to keep the navigation bar hidden
                systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
            return sketch!!.surfaceTouchEvent(event)
        }

        override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
            sketch?.surfaceKeyDown(code, event)
            return super.onKeyDown(code, event)
        }

        override fun onKeyUp(code: Int, event: KeyEvent): Boolean {
            sketch?.surfaceKeyUp(code, event)
            return super.onKeyUp(code, event)
        }

        init {

            // Check if the system supports OpenGL ES 2.0.
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val configurationInfo = activityManager.deviceConfigurationInfo
            val supportsGLES2 = configurationInfo.reqGlEsVersion >= 0x20000

            if (!supportsGLES2) {
                throw RuntimeException("OpenGL ES 2.0 is not supported by this device.")
            }

            val h = getHolder()
            h?.addCallback(this)

            // Tells the default EGLContextFactory and EGLConfigChooser to create an GLES2 context.
            setEGLContextClientVersion(PGLES.version)
            preserveEGLContextOnPause = true

            val samples = sketch!!.sketchSmooth()

            if (1 < samples) {
                setEGLConfigChooser(getConfigChooser(samples))
            } else {
                // use default EGL config chooser for now...
//        setEGLConfigChooser(getConfigChooser(5, 6, 5, 4, 16, 1, samples));

                // Some notes on how to choose an EGL configuration:
                // https://github.com/mapbox/mapbox-gl-native/issues/574
                // http://malideveloper.arm.com/sample-code/selecting-the-correct-eglconfig/
            }


            // The renderer can be set only once.
            setRenderer(renderer)
            renderMode = RENDERMODE_WHEN_DIRTY
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            surfaceReady = false // Will be ready when the surfaceCreated() event is called
        }
    }
    //    renderer = new AndroidRenderer();
    //    return renderer;

    ///////////////////////////////////////////////////////////

    // Android specific classes (Renderer, ConfigChooser)

    open val renderer: RendererGLES
        get() = RendererGLES() //    renderer = new AndroidRenderer();
                               //    return renderer;

    val contextFactory: ContextFactoryGLES
        get() = ContextFactoryGLES()

    fun getConfigChooser(samples: Int): ConfigChooserGLES {
        return ConfigChooserGLES(5, 6, 5, 4, 16, 1, samples)
        //  return new AndroidConfigChooser(8, 8, 8, 8, 16, 8, samples);
    }

    fun getConfigChooser(r: Int, g: Int, b: Int, a: Int,
                         d: Int, s: Int, samples: Int): ConfigChooserGLES {
        return ConfigChooserGLES(r, g, b, a, d, s, samples)
    }

    inner class RendererGLES : GLSurfaceView.Renderer {
        override fun onDrawFrame(igl: GL10) {
            pgl!!.getGL(igl)
            sketch?.handleDraw()
        }

        override fun onSurfaceChanged(igl: GL10, iwidth: Int, iheight: Int) {
            if (PApplet.DEBUG) {
                println("AndroidRenderer.onSurfaceChanged() $iwidth $iheight")
            }
            pgl!!.getGL(igl)

            // Here is where we should initialize native libs...
            // lib.init(iwidth, iheight);

//      sketch.surfaceChanged();
//      graphics.surfaceChanged();
//
//      sketch.setSize(iwidth, iheight);
//      graphics.setSize(sketch.sketchWidth(), sketch.sketchHeight());
            sketch?.surfaceChanged()
            sketch?.setSize(iwidth, iheight)
        }

        override fun onSurfaceCreated(igl: GL10, config: EGLConfig) {
            pgl!!.init(igl)
        }
    }

    inner class ContextFactoryGLES : EGLContextFactory {
        override fun createContext(egl: EGL10, display: EGLDisplay,
                                   eglConfig: EGLConfig): EGLContext {
            val attrib_list = intArrayOf(PGLES.EGL_CONTEXT_CLIENT_VERSION, PGLES.version,
                    EGL10.EGL_NONE)
            return egl.eglCreateContext(display, eglConfig,
                    EGL10.EGL_NO_CONTEXT,
                    attrib_list)
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay,
                                    context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }
    }

    // Desired size (in bits) for the rgba color, depth and stencil buffers.
    inner class ConfigChooserGLES(
            @JvmField var redTarget: Int, @JvmField var greenTarget: Int, @JvmField var blueTarget: Int, @JvmField var alphaTarget: Int,
            @JvmField var depthTarget: Int, @JvmField var stencilTarget: Int, @JvmField var numSamples: Int) : EGLConfigChooser {

        // Actual rgba color, depth and stencil sizes (in bits) supported by the
        // device.
        @JvmField
        var redBits = 0

        @JvmField
        var greenBits = 0

        @JvmField
        var blueBits = 0

        @JvmField
        var alphaBits = 0

        @JvmField
        var depthBits = 0

        @JvmField
        var stencilBits = 0

        @JvmField
        var tempValue = IntArray(1)

        /*
    The GLES2 extensions supported are:
      GL_OES_rgb8_rgba8 GL_OES_depth24 GL_OES_vertex_half_float
      GL_OES_texture_float GL_OES_texture_half_float
      GL_OES_element_index_uint GL_OES_mapbuffer
      GL_OES_fragment_precision_high GL_OES_compressed_ETC1_RGB8_texture
      GL_OES_EGL_image GL_OES_required_internalformat GL_OES_depth_texture
      GL_OES_get_program_binary GL_OES_packed_depth_stencil
      GL_OES_standard_derivatives GL_OES_vertex_array_object GL_OES_egl_sync
      GL_EXT_multi_draw_arrays GL_EXT_texture_format_BGRA8888
      GL_EXT_discard_framebuffer GL_EXT_shader_texture_lod
      GL_IMG_shader_binary GL_IMG_texture_compression_pvrtc
      GL_IMG_texture_stream2 GL_IMG_texture_npot
      GL_IMG_texture_format_BGRA8888 GL_IMG_read_format
      GL_IMG_program_binary GL_IMG_multisampled_render_to_texture
      */
        /*
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
    */
        protected var attribsNoMSAA = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, PGLES.EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SAMPLE_BUFFERS, 0,
                EGL10.EGL_NONE)

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            var configs: Array<EGLConfig?>? = null
            if (1 < numSamples) {
                val attribs = intArrayOf(
                        EGL10.EGL_RENDERABLE_TYPE, PGLES.EGL_OPENGL_ES2_BIT,
                        EGL10.EGL_SAMPLE_BUFFERS, 1,
                        EGL10.EGL_SAMPLES, numSamples,
                        EGL10.EGL_NONE)
                configs = chooseConfigWithAttribs(egl, display, attribs)
                if (configs == null) {
                    // No normal multisampling config was found. Try to create a
                    // coverage multisampling configuration, for the nVidia Tegra2.
                    // See the EGL_NV_coverage_sample documentation.
                    val attribsCov = intArrayOf(
                            EGL10.EGL_RENDERABLE_TYPE, PGLES.EGL_OPENGL_ES2_BIT,
                            PGLES.EGL_COVERAGE_BUFFERS_NV, 1,
                            PGLES.EGL_COVERAGE_SAMPLES_NV, numSamples,
                            EGL10.EGL_NONE)
                    configs = chooseConfigWithAttribs(egl, display, attribsCov)
                    if (configs == null) {
                        configs = chooseConfigWithAttribs(egl, display, attribsNoMSAA)
                    } else {
                        PGLES.usingMultisampling = true
                        PGLES.usingCoverageMultisampling = true
                        PGLES.multisampleCount = numSamples
                    }
                } else {
                    PGLES.usingMultisampling = true
                    PGLES.usingCoverageMultisampling = false
                    PGLES.multisampleCount = numSamples
                }
            } else {
                configs = chooseConfigWithAttribs(egl, display, attribsNoMSAA)
            }
            requireNotNull(configs) { "No EGL configs match configSpec" }
            if (PApplet.DEBUG) {
                for (config in configs) {
                    val configStr = ("P3D - selected EGL config : "
                            + printConfig(egl, display, config))
                    println(configStr)
                }
            }

            // Now return the configuration that best matches the target one.
            return chooseBestConfig(egl, display, configs)!!
        }

        fun chooseBestConfig(egl: EGL10, display: EGLDisplay?,
                             configs: Array<EGLConfig?>): EGLConfig? {
            var bestConfig: EGLConfig? = null
            var bestScore = Float.MAX_VALUE
            for (config in configs) {
                val gl = findConfigAttrib(egl, display, config,
                        EGL10.EGL_RENDERABLE_TYPE, 0)
                val isGLES2 = gl and PGLES.EGL_OPENGL_ES2_BIT != 0
                if (isGLES2) {
                    val d = findConfigAttrib(egl, display, config,
                            EGL10.EGL_DEPTH_SIZE, 0)

                    val s = findConfigAttrib(egl, display, config,
                            EGL10.EGL_STENCIL_SIZE, 0)

                    val r = findConfigAttrib(egl, display, config,
                            EGL10.EGL_RED_SIZE, 0)

                    val g = findConfigAttrib(egl, display, config,
                            EGL10.EGL_GREEN_SIZE, 0)

                    val b = findConfigAttrib(egl, display, config,
                            EGL10.EGL_BLUE_SIZE, 0)

                    val a = findConfigAttrib(egl, display, config,
                            EGL10.EGL_ALPHA_SIZE, 0)

                    val score = 0.20f * PApplet.abs(r - redTarget) + 0.20f * PApplet.abs(g - greenTarget) + 0.20f * PApplet.abs(b - blueTarget) + 0.15f * PApplet.abs(a - alphaTarget) + 0.15f * PApplet.abs(d - depthTarget) + 0.10f * PApplet.abs(s - stencilTarget)
                    if (score < bestScore) {
                        // We look for the config closest to the target config.
                        // Closeness is measured by the score function defined above:
                        // we give more weight to the RGB components, followed by the
                        // alpha, depth and finally stencil bits.
                        bestConfig = config
                        bestScore = score
                        redBits = r
                        greenBits = g
                        blueBits = b
                        alphaBits = a
                        depthBits = d
                        stencilBits = s
                    }
                }
            }
            if (PApplet.DEBUG) {
                val configStr = ("P3D - selected EGL config : "
                        + printConfig(egl, display, bestConfig))
                println(configStr)
            }
            return bestConfig
        }

        protected fun printConfig(egl: EGL10, display: EGLDisplay?,
                                  config: EGLConfig?): String {
            val r = findConfigAttrib(egl, display, config,
                    EGL10.EGL_RED_SIZE, 0)

            val g = findConfigAttrib(egl, display, config,
                    EGL10.EGL_GREEN_SIZE, 0)

            val b = findConfigAttrib(egl, display, config,
                    EGL10.EGL_BLUE_SIZE, 0)

            val a = findConfigAttrib(egl, display, config,
                    EGL10.EGL_ALPHA_SIZE, 0)

            val d = findConfigAttrib(egl, display, config,
                    EGL10.EGL_DEPTH_SIZE, 0)

            val s = findConfigAttrib(egl, display, config,
                    EGL10.EGL_STENCIL_SIZE, 0)

            val type = findConfigAttrib(egl, display, config,
                    EGL10.EGL_RENDERABLE_TYPE, 0)

            val nat = findConfigAttrib(egl, display, config,
                    EGL10.EGL_NATIVE_RENDERABLE, 0)

            val bufSize = findConfigAttrib(egl, display, config,
                    EGL10.EGL_BUFFER_SIZE, 0)

            val bufSurf = findConfigAttrib(egl, display, config,
                    EGL10.EGL_RENDER_BUFFER, 0)

            return (String.format("EGLConfig rgba=%d%d%d%d depth=%d stencil=%d",
                    r, g, b, a, d, s) + " type=" + type
                    + " native=" + nat
                    + " buffer size=" + bufSize
                    + " buffer surface=" + bufSurf + String.format(" caveat=0x%04x",
                    findConfigAttrib(egl, display, config,
                            EGL10.EGL_CONFIG_CAVEAT, 0)))
        }

        protected fun findConfigAttrib(egl: EGL10, display: EGLDisplay?,
                                       config: EGLConfig?, attribute: Int, defaultValue: Int): Int {
            return if (egl.eglGetConfigAttrib(display, config, attribute, tempValue)) {
                tempValue[0]
            } else defaultValue
        }

        protected fun chooseConfigWithAttribs(egl: EGL10,
                                              display: EGLDisplay?,
                                              configAttribs: IntArray?): Array<EGLConfig?>? {
            // Get the number of minimally matching EGL configurations
            val configCounts = IntArray(1)
            egl.eglChooseConfig(display, configAttribs, null, 0, configCounts)
            val count = configCounts[0]
            if (count <= 0) {
                //throw new IllegalArgumentException("No EGL configs match configSpec");
                return null
            }

            // Allocate then read the array of minimally matching EGL configs
            val configs = arrayOfNulls<EGLConfig>(count)
            egl.eglChooseConfig(display, configAttribs, configs, count, configCounts)
            return configs


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
}