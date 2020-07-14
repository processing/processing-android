/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-17 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

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

package processing.core

import android.app.Activity
import android.app.FragmentManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.annotation.LayoutRes
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.view.inputmethod.InputMethodManager
import processing.a2d.PGraphicsAndroid2D
import processing.android.ActivityAPI
import processing.android.AppComponent
import processing.android.CompatUtils.charsetUTF8
import processing.core.PFont.Companion.findNative
import processing.core.PGraphics.Companion.showWarning
import processing.data.*
import processing.data.XML.Companion.parse
import processing.event.Event
import processing.event.KeyEvent
import processing.event.MouseEvent
import processing.event.TouchEvent
import processing.opengl.PGL
import processing.opengl.PGraphics2D
import processing.opengl.PGraphics3D
import processing.opengl.PShader
import java.io.*
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.net.*
import java.text.NumberFormat
import java.util.*
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.experimental.and


open class PApplet: Any, ActivityAPI, PConstants {

    /**
     * Required empty constructor.
     */
    constructor() { }

    //static final public int SDK = Build.VERSION_CODES.ICE_CREAM_SANDWICH; // Forcing older SDK for testing

    /**
     * The surface this sketch draws to.
     */
    //todo  need to make it public as kotlin code will not be having getSurface()
    var surface: PSurface? = null
        protected set

    /**
     * The view group containing the surface view of the PApplet.
     */
    @JvmField
    @LayoutRes
    var parentLayout = -1

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    // may throw JVM-CLash

    /** The PGraphics renderer associated with this PApplet  */
    // todo - nullable type
    @JvmField
    var graphics: PGraphics? = null

    /**
     * The screen size when the sketch was started. This is initialized inside
     * onCreate().
     *
     *
     * Note that this won't update if you change the resolution
     * of your screen once the the applet is running.
     *
     *
     * This variable is not static because in the desktop version of Processing,
     * not all instances of PApplet will necessarily be started on a screen of
     * the same size.
     */
    @JvmField
    var displayWidth = 0

    @JvmField
    var displayHeight = 0

    /**
     * Command line options passed in from main().
     * <P>
     * This does not include the arguments passed in to PApplet itself.
    </P> */
    //  public String[] args;
    /**
     * Path to where sketch can read/write files (read-only).
     * Android: This is the writable area for the Activity, which is correct
     * for purposes of how sketchPath is used in practice from a sketch,
     * even though it's technically different than the desktop version.
     */
    // todo nullable type
    @JvmField
    var sketchPath: String? = null  //folder;

    /**
     * Set true when the surface dimensions have changed, so that the PGraphics
     * object can be resized on the next trip through handleDraw().
     */
    // todo take care of surfaceChanged() function
    @JvmField
    protected var surfaceChanged = false

    /**
     * Pixel buffer from this applet's PGraphics.
     * <P>
     * When used with OpenGL or Java2D, this value will
     * be null until loadPixels() has been called.
    </P> */

    // todo - nullable type - not sure
    @JvmField
    var pixels: IntArray? = null

    /** width of this applet's associated PGraphics  */
    @JvmField
    var width = DEFAULT_WIDTH

    /** height of this applet's associated PGraphics  */
    @JvmField
    var height = DEFAULT_HEIGHT

    /** The logical density of the display from getDisplayMetrics().density
     * According to Android's documentation:
     * This is a scaling factor for the Density Independent Pixel unit,
     * where one DIP is one pixel on an approximately 160 dpi screen
     * (for example a 240x320, 1.5"x2" screen), providing the baseline of the
     * system's display. Thus on a 160dpi screen this density value will be 1;
     * on a 120 dpi screen it would be .75; etc.
     */
    @JvmField
    var displayDensity = 1f

    // For future use
    @JvmField
    var pixelDensity = 1

    @JvmField
    var pixelWidth = 0

    @JvmField
    var pixelHeight = 0


    ///////////////////////////////////////////////////////////////

    // Mouse events

    /** absolute x position of input on screen  */
    @JvmField
    var mouseX = 0

    /** absolute x position of input on screen  */
    @JvmField
    var mouseY = 0

    /**
     * Previous x/y position of the mouse. This will be a different value
     * when inside a mouse handler (like the mouseMoved() method) versus
     * when inside draw(). Inside draw(), pmouseX is updated once each
     * frame, but inside mousePressed() and friends, it's updated each time
     * an event comes through. Be sure to use only one or the other type of
     * means for tracking pmouseX and pmouseY within your sketch, otherwise
     * you're gonna run into trouble.
     */
    @JvmField
    var pmouseX = 0

    @JvmField
    var pmouseY = 0

    @JvmField
    var mouseButton = 0

    @JvmField
    var mousePressed = false

    @JvmField
    var touchIsStarted = false

    // todo - I think itself is not nullable but elements are of nullable type as per gettouches() in TouchEvent class
    @JvmField
    var touches = arrayOfNulls<TouchEvent.Pointer>(0)

    /**
     * previous mouseX/Y for the draw loop, separated out because this is
     * separate from the pmouseX/Y when inside the mouse event handlers.
     */
    @JvmField
    protected var dmouseX = 0

    @JvmField
    protected var dmouseY = 0

    /**
     * pmotionX/Y for the event handlers (motionPressed(), motionDragged() etc)
     * these are different because motion events are queued to the end of
     * draw, so the previous position has to be updated on each event,
     * as opposed to the pmotionX/Y that's used inside draw, which is expected
     * to be updated once per trip through draw().
     */
    @JvmField
    protected var emouseX = 0

    @JvmField
    protected var emouseY = 0

    /**
     * ID of the pointer tracked for mouse events.
     */
    @JvmField
    protected var mousePointerId = 0

    /**
     * ID of the most recently touch pointer gone up or down.
     */
    @JvmField
    protected var touchPointerId = 0


    ///////////////////////////////////////////////////////////////

    // Key events

    /**
     * Last key pressed.
     * <P>
     * If it's a coded key, i.e. UP/DOWN/CTRL/SHIFT/ALT,
     * this will be set to CODED (0xffff or 65535).
    </P> */
    @JvmField
    var key = 0.toChar()

    /**
     * When "key" is set to CODED, this will contain a Java key code.
     * <P>
     * For the arrow keys, keyCode will be one of UP, DOWN, LEFT and RIGHT.
     * Also available are ALT, CONTROL and SHIFT. A full set of constants
     * can be obtained from java.awt.event.KeyEvent, from the VK_XXXX variables.
    </P> */
    @JvmField
    var keyCode = 0

    /**
     * true if the mouse is currently pressed.
     */
    @JvmField
    var keyPressed = false

    /**
     * the last KeyEvent object passed into a mouse function.
     */
    //  public KeyEvent keyEvent;
    /**
     * Gets set to true/false as the applet gains/loses focus.
     */
    @JvmField
    var focused = false

    /**
     * Keeps track of ENABLE_KEY_REPEAT hint
     */
    @JvmField
    var keyRepeatEnabled = false

    /**
     * Set to open when openKeyboard() is called, and used to close the keyboard when the sketch is
     * paused, otherwise it remains visible.
     */
    @JvmField
    var keyboardIsOpen = false

    /**
     * Flag to determine if the back key was pressed.
     */
    private var requestedBackPress = false

    /**
     * Flag to determine if the user handled the back press.
     */
    @JvmField
    var handledBackPressed = true


    ///////////////////////////////////////////////////////////////

    // Permission handling

    /**
     * Callback methods to handle permission requests
     */
    // todo - not sure about nullability; recommendation is to not take them as  nullable
    @JvmField
    protected var permissionMethods = HashMap<String, String>()

    /**
     * Permissions requested during one frame
     */
    //  todo - not sure ; but as recommendation non-nullable
    @JvmField
    protected var reqPermissions = ArrayList<String?>()
    ///////////////////////////////////////////////////////////////
    // Rendering/timing
    /**
     * Time in milliseconds when the applet was started.
     * <P>
     * Used by the millis() function.
    </P> */
    @JvmField
    var millisOffset = System.currentTimeMillis()

    @JvmField
    protected var insideDraw = false

    /** Last time in nanoseconds that frameRate was checked  */
    @JvmField
    protected var frameRateLastNanos: Long = 0

    /**
     * The current value of frames per second.
     * <P>
     * The initial value will be 10 fps, and will be updated with each
     * frame thereafter. The value is not instantaneous (since that
     * wouldn't be very useful since it would jump around so much),
     * but is instead averaged (integrated) over several frames.
     * As such, this value won't be valid until after 5-10 frames.
    </P> */
    @JvmField
    var frameRate = 10f

    // JVM-Clash
    var isLooping = false
        protected set

    /** flag set to true when a redraw is asked for by the user  */
    @JvmField
    protected var redraw = false

    /**
     * How many frames have been displayed since the applet started.
     * <P>
     * This value is read-only <EM>do not</EM> attempt to set it,
     * otherwise bad things will happen.
    </P> * <P>
     * Inside setup(), frameCount is 0.
     * For the first iteration of draw(), frameCount will equal 1.
    </P> */
    @JvmField
    var frameCount = 0

    /**
     * true if this applet has had it.
     */
    @JvmField
    var finished = false

    /**
     * true if exit() has been called so that things shut down
     * once the main thread kicks off.
     */
    @JvmField
    protected var exitCalled = false

    @JvmField
    var insideSettings = false

    // renderer is of non-nullable type
    @JvmField
    var renderer = PConstants.JAVA2D

    @JvmField
    var smooth = 1 // default smoothing (whatever that means for the renderer)

    @JvmField
    var fullScreen = false

    @JvmField
    var display = -1 // use default

    // Background default needs to be different from the default value in
    // PGraphics.backgroundColor, otherwise size(100, 100) bg spills over.
    // https://github.com/processing/processing/issues/2297
    // todo - need to test this value as previous value was 0xffDDDDDD
    @JvmField
    var windowColor = -0x222223

    /** true if this sketch is being run by the PDE  */ // need to remove setExternal(value)

    private var external = false


    val context: Context?
        get() = surface!!.getContext()

    val activity: Activity?
        get() = surface!!.getActivity()

    // all nullable args
    open fun initSurface(component: AppComponent, holder: SurfaceHolder?) {
        parentLayout = -1
        initSurface(null, null, null, component, holder)
    }

    // todo - all args nullable
    open fun initSurface(inflater: LayoutInflater?, container: ViewGroup?,
                    savedInstanceState: Bundle?,
                    component: AppComponent, holder: SurfaceHolder?) {
        if (DEBUG) println("initSurface() happening here: " + Thread.currentThread().name)
        component.initDimensions()

        displayWidth = component.getDisplayWidth()
        displayHeight = component.getDisplayHeight()
        displayDensity = component.getDisplayDensity()
        handleSettings()

        var parentSize = false
        if (parentLayout == -1) {
            if (fullScreen || width == -1 || height == -1) {
                // Either sketch explicitly set to full-screen mode, or not
                // size/fullScreen provided, so sketch uses the entire display
                width = displayWidth
                height = displayHeight
            }
        } else {
            if (fullScreen || width == -1 || height == -1) {
                // Dummy weight and height to initialize the PGraphics, will be resized
                // when the view associated to the parent layout is created
                width = 100
                height = 100
                parentSize = true
            }
        }

        pixelWidth = width * pixelDensity
        pixelHeight = height * pixelDensity
        val rendererName = sketchRenderer()

        if (DEBUG) println("Renderer $rendererName")
        graphics = makeGraphics(width, height, rendererName, true)
        if (DEBUG) println("Created renderer")
        surface = graphics!!.createSurface(component, holder, false)
        if (DEBUG) println("Created surface")
        if (parentLayout == -1) {
            setFullScreenVisibility()
            surface!!.initView(width, height)
        } else {
            surface!!.initView(width, height, parentSize,
                    inflater, container, savedInstanceState)
        }

        finished = false // just for clarity
        // this will be cleared by draw() if it is not overridden
        isLooping = true
        redraw = true // draw this guy once
        sketchPath = surface!!.getFilesDir()!!.absolutePath
        surface!!.startThread()
        if (DEBUG) println("Done with init surface")
    }


    private fun setFullScreenVisibility() {
        if (fullScreen) {
            runOnUiThread(Runnable {
                val visibility: Int
                visibility = if (SDK < 19) {
                    // Pre-4.4
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                } else {
                    // 4.4 and higher. Integer instead of constants defined in View so it can
                    // build with SDK < 4.4
                    256 or  // View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            512 or  // View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            1024 or  // View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            4 or  // View.SYSTEM_UI_FLAG_FULLSCREEN
                            4096 // View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // However, this visibility does not fix a bug where the navigation area
                    // turns black after resuming the app:
                    // https://code.google.com/p/android/issues/detail?id=170752
                }
                surface!!.setSystemUiVisibility(visibility)
            })
        }
    }


    override fun onResume() {
        if (DEBUG) println("PApplet.onResume() called")
        if (parentLayout == -1) {
            setFullScreenVisibility()
        }
        handleMethods("resume")

        // Don't call resume() when the app is starting and setup() has not been called yet:
        // https://github.com/processing/processing-android/issues/274
        // Also, there is no need to call resume() from anywhere else (for example, from
        // onStart) since onResume() is always called in the activity lifecyle:
        // https://developer.android.com/guide/components/activities/activity-lifecycle.html
        if (0 < frameCount) {
            resume()
        }

        // Set the handledBackPressed to true to handle the situation where a fragment is popping
        // right back after pressing the back button (the sketch does not exit).
        handledBackPressed = true
        if (graphics != null) {
            graphics!!.restoreState()
        }
        surface!!.resumeThread()
    }

    override fun onPause() {
        surface!!.pauseThread()

        // Make sure that the keyboard is not left open after leaving the app
        closeKeyboard()
        if (graphics != null) {
            graphics!!.saveState()
        }
        handleMethods("pause")
        pause() // handler for others to write
    }

    override fun onStart() {
        start()
    }

    override fun onStop() {
        stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        create()
    }

    override fun onDestroy() {
        handleMethods("onDestroy")
        surface!!.stopThread()
        dispose()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        handleMethods("onActivityResult", arrayOf(requestCode, resultCode, data))
    }

    override fun onNewIntent(intent: Intent?) {
        handleMethods("onNewIntent", arrayOf(intent))
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {}

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return false
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo?) {}

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        return false
    }

    override fun setHasOptionsMenu(hasMenu: Boolean) {
        surface!!.setHasOptionsMenu(hasMenu)
    }

    @Synchronized
    override fun onBackPressed() {
        requestedBackPress = true
    }

    override val fragmentManager: FragmentManager?
        get() = if (activity != null) {
            activity!!.fragmentManager
        } else null


    override val window: Window?
        get() = if (activity != null) {
            activity!!.window
        } else null


    open fun startActivity(intent: Intent?) {
        surface!!.startActivity(intent)
    }

    open fun runOnUiThread(action: Runnable?) {
        surface!!.runOnUiThread(action)
    }

    // todo - nullable arg
    open fun hasPermission(permission: String?): Boolean {
        return surface!!.hasPermission(permission)
    }

    open fun requestPermission(permission: String?) {
        if (!hasPermission(permission)) {
            reqPermissions.add(permission)
        }
    }

    // todo - permission is nullable ;
    @JvmOverloads
    open fun requestPermission(permission: String, callback: String, target: Any = this) {
        registerWithArgs(callback, target, arrayOf(Boolean::class.javaPrimitiveType))
        if (hasPermission(permission)) {
            // If the app already has permission, still call the handle method as it
            // may be doing some initialization
            handleMethods(callback, arrayOf(true))
        } else {
            permissionMethods[permission] = callback
            // Accumulating permissions so they requested all at once at the end
            // of draw.
            reqPermissions.add(permission)
        }
    }

    open fun onRequestPermissionsResult(requestCode: Int,
                                   permissions: Array<String?>?,
                                   grantResults: IntArray?) {
        if (requestCode == PSurface.REQUEST_PERMISSIONS) {
            for (i in grantResults!!.indices) {
                val granted = grantResults!![i] == PackageManager.PERMISSION_GRANTED
                handlePermissionsResult(permissions!![i], granted)
            }
        }
    }

    private fun handlePermissionsResult(permission: String?, granted: Boolean) {
        val methodName = permissionMethods[permission]
        val meth = registerMap[methodName]
        if (meth != null) {
            val handler = Handler(Looper.getMainLooper())
            handler.post { meth.handle(arrayOf(granted)) }
        }
    }

    private fun handlePermissions() {
        if (0 < reqPermissions.size) {
            val req = reqPermissions.toTypedArray()
            surface!!.requestPermissions(req)
            reqPermissions.clear()
        }
    }

    @Synchronized
    private fun handleBackPressed() {
        if (requestedBackPress) {
            requestedBackPress = false
            backPressed()
            if (!handledBackPressed) {
                if (activity != null) {
                    // Services don't have an activity associated to them, but back press could not be triggered for those anyways
                    activity!!.finish()
                }
                handledBackPressed = false
            }
        }
    }

    /**
     * @param method "size" or "fullScreen"
     * @param args parameters passed to the function so we can show the user
     * @return true if safely inside the settings() method
     */
    // todo -  method non-nullable type
    open fun insideSettings(method: String, vararg args: Any?): Boolean {
        if (insideSettings) {
            return true
        }
        val url = "https://processing.org/reference/" + method + "_.html"
        if (!external) {  // post a warning for users of Eclipse and other IDEs
            val argList = StringList(*args)
            System.err.println("When not using the PDE, $method() can only be used inside settings().")
            System.err.println("Remove the $method() method from setup(), and add the following:")
            System.err.println("public void settings() {")
            System.err.println("  " + method + "(" + argList.join(", ") + ");")
            System.err.println("}")
        }
        throw IllegalStateException("$method() cannot be used here, see $url")
    }

    open fun handleSettings() {
        insideSettings = true
        //Do stuff
        settings()
        insideSettings = false
    }

    open fun settings() {
        //It'll be empty. Will be overridden by user's sketch class.
    }

    open fun sketchWidth(): Int {
        return width
    }

    open fun sketchHeight(): Int {
        return height
    }

    open fun sketchRenderer(): String {
        return renderer
    }

    open fun sketchSmooth(): Int {
        return smooth
    }

    open fun sketchFullScreen(): Boolean {
        return fullScreen
    }

    open fun sketchDisplay(): Int {
        return display
    }

    open fun sketchOutputPath(): String? {
        return null
    }

    open fun sketchOutputStream(): OutputStream? {
        return null
    }

    open fun sketchWindowColor(): Int {
        return windowColor
    }

    open fun sketchPixelDensity(): Int {
        return pixelDensity
    }


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

   open fun surfaceChanged() {
        surfaceChanged = true
        graphics!!.surfaceChanged()
    }


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Called by the sketch surface view, thought it could conceivably be called
     * by Android as well.
     */
    open fun surfaceWindowFocusChanged(hasFocus: Boolean) {
        focused = hasFocus
        if (focused) {
            focusGained()
        } else {
            focusLost()
        }
    }


    /**
     * If you override this function without calling super.onTouchEvent(),
     * then motionX, motionY, motionPressed, and motionEvent will not be set.
     */
    open fun surfaceTouchEvent(event: MotionEvent): Boolean {
        nativeMotionEvent(event)
        return true
    }

    open fun surfaceKeyDown(code: Int, event: android.view.KeyEvent) {
        nativeKeyEvent(event)
    }

    open fun surfaceKeyUp(code: Int, event: android.view.KeyEvent) {
        nativeKeyEvent(event)
    }


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    /**
     * Called by the browser or applet viewer to inform this applet that it
     * should start its execution. It is called after the init method and
     * each time the applet is revisited in a Web page.
     *
     *
     * Called explicitly via the first call to PApplet.paint(), because
     * PAppletGL needs to have a usable screen before getting things rolling.
     */
    open fun start() {

    }

    /**
     * Called by the browser or applet viewer to inform
     * this applet that it should stop its execution.
     *
     *
     * Unfortunately, there are no guarantees from the Java spec
     * when or if stop() will be called (i.e. on browser quit,
     * or when moving between web pages), and it's not always called.
     */
    open fun stop() {

    }

    /**
     * Developers can override here to save state. The 'paused' variable will be
     * set before this function is called.
     */
    open fun pause() {

    }

    /**
     * Developers can override here to restore state. The 'paused' variable
     * will be cleared before this function is called.
     */
    open fun resume() {

    }

    open fun backPressed() {
        handledBackPressed = false
    }


    //////////////////////////////////////////////////////////////

    /** Map of registered methods, stored by name.  */ // value is of nullable type
    @JvmField
    var registerMap = HashMap<String, RegisteredMethods?>()


    inner class RegisteredMethods {
        var count = 0
        var objects: Array<Any?>? = null

        // Because the Method comes from the class being called,
        // it will be unique for most, if not all, objects.
        lateinit var methods: Array<Method?>

        var emptyArgs = arrayOf<Any?>()

        @JvmOverloads
        fun handle(args: Array<Any?> = emptyArgs) {
            for (i in 0 until count) {
                try {
                    methods[i]!!.invoke(objects!![i], *args)
                } catch (e: Exception) {
                    // check for wrapped exception, get root exception
                    var t: Throwable
                    if (e is InvocationTargetException) {
                        t = e.cause!!
                    } else {
                        t = e
                    }
                    // check for RuntimeException, and allow to bubble up
                    if (t is RuntimeException) {
                        // re-throw exception
                        throw t
                    } else {
                        // trap and print as usual
                        t.printStackTrace()
                    }
                }
            }
        }


        fun add(`object`: Any, method: Method) {
            if (findIndex(`object`) == -1) {
                if (objects == null) {
                    objects = arrayOfNulls(5)
                    methods = arrayOfNulls(5)
                } else if (count == objects!!.size) {
                    objects = expand(objects!!) as Array<Any?>
                    methods = expand(methods) as Array<Method?>
                }
                objects!![count] = `object`
                methods[count] = method
                count++
            } else {
                die(method.name + "() already added for this instance of " +
                        `object`.javaClass.name)
            }
        }


        /**
         * Removes first object/method pair matched (and only the first,
         * must be called multiple times if object is registered multiple times).
         * Does not shrink array afterwards, silently returns if method not found.
         */
        //    public void remove(Object object, Method method) {
        //      int index = findIndex(object, method);
        fun remove(`object`: Any) {
            val index = findIndex(`object`)
            if (index != -1) {
                // shift remaining methods by one to preserve ordering
                count--
                for (i in index until count) {
                    objects!![i] = objects!![i + 1]
                    methods[i] = methods[i + 1]
                }
                // clean things out for the gc's sake
                objects!![count] = null
                methods[count] = null
            }
        }


        //    protected int findIndex(Object object, Method method) {
        protected fun findIndex(`object`: Any): Int {
            for (i in 0 until count) {
                if (objects!![i] === `object`) {
//        if (objects[i] == object && methods[i].equals(method)) {
                    //objects[i].equals() might be overridden, so use == for safety
                    // since here we do care about actual object identity
                    //methods[i]==method is never true even for same method, so must use
                    // equals(), this should be safe because of object identity
                    return i
                }
            }
            return -1
        }
    }


    /**
     * Register a built-in event so that it can be fired for libraries, etc.
     * Supported events include:
     *
     *  * pre – at the very top of the draw() method (safe to draw)
     *  * draw – at the end of the draw() method (safe to draw)
     *  * post – after draw() has exited (not safe to draw)
     *  * pause – called when the sketch is paused
     *  * resume – called when the sketch is resumed
     *  * dispose – when the sketch is shutting down (definitely not safe to draw)
     *
     * In addition, the new (for 2.0) processing.event classes are passed to
     * the following event types:
     *
     *  * mouseEvent
     *  * keyEvent
     *  * touchEvent
     *
     * The older java.awt events are no longer supported.
     * See the Library Wiki page for more details.
     * @param methodName name of the method to be called
     * @param target the target object that should receive the event
     */
    open fun registerMethod(methodName: String, target: Any) {
        if (methodName == "mouseEvent") {
            registerWithArgs("mouseEvent", target, arrayOf(MouseEvent::class.java))
        } else if (methodName == "keyEvent") {
            registerWithArgs("keyEvent", target, arrayOf(KeyEvent::class.java))
        } else if (methodName == "touchEvent") {
            registerWithArgs("touchEvent", target, arrayOf(TouchEvent::class.java))

            // Android-lifecycle event handlers
        } else if (methodName == "onDestroy") {
            registerNoArgs(methodName, target)
        } else if (methodName == "onActivityResult") {
            registerWithArgs("onActivityResult", target, arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java))
        } else if (methodName == "onNewIntent") {
            registerWithArgs("onNewIntent", target, arrayOf(Intent::class.java))
        } else {
            registerNoArgs(methodName, target)
        }
    }


    private fun registerNoArgs(name: String, o: Any) {
        var meth = registerMap[name]
        if (meth == null) {
            meth = RegisteredMethods()
            registerMap[name] = meth
        }
        val c: Class<*> = o.javaClass
        try {
            val method = c.getMethod(name, *arrayOf())
            meth.add(o, method)
        } catch (nsme: NoSuchMethodException) {
            die("There is no public " + name + "() method in the class " +
                    o.javaClass.name)
        } catch (e: Exception) {
            die("Could not register $name + () for $o", e)
        }
    }

    private fun registerWithArgs(name: String, o: Any, cargs: Array<Class<*>?>) {
        var meth = registerMap[name]
        if (meth == null) {
            meth = RegisteredMethods()
            registerMap[name] = meth
        }
        val c: Class<*> = o.javaClass
        try {
            val method = c.getMethod(name, *cargs)
            meth.add(o, method)
        } catch (nsme: NoSuchMethodException) {
            die("There is no public " + name + "() method in the class " +
                    o.javaClass.name)
        } catch (e: Exception) {
            die("Could not register $name + () for $o", e)
        }
    }


    //  public void registerMethod(String methodName, Object target, Object... args) {
    //    registerWithArgs(methodName, target, args);
    //  }
    open fun unregisterMethod(name: String, target: Any) {
        val meth = registerMap[name]
        if (meth == null) {
            die("No registered methods with the name $name() were found.")
        }
        try {
//      Method method = o.getClass().getMethod(name, new Class[] {});
//      meth.remove(o, method);
            meth!!.remove(target)
        } catch (e: Exception) {
            die("Could not unregister $name() for $target", e)
        }
    }

    protected fun handleMethods(methodName: String?) {
        val meth = registerMap[methodName]
        meth?.handle()
    }

    protected fun handleMethods(methodName: String?, args: Array<Any?>) {
        val meth = registerMap[methodName]
        if (meth != null) {
            val handler = Handler(Looper.getMainLooper())
            handler.post { meth.handle(args) }
        }
    }

    @Deprecated("")
    open fun registerSize(o: Any?) {
        System.err.println("The registerSize() command is no longer supported.")
        //    Class<?> methodArgs[] = new Class[] { Integer.TYPE, Integer.TYPE };
//    registerWithArgs(sizeMethods, "size", o, methodArgs);
    }

    @Deprecated("")
    open fun registerPre(o: Any) {
        registerNoArgs("pre", o)
    }

    @Deprecated("")
    open fun registerDraw(o: Any) {
        registerNoArgs("draw", o)
    }

    @Deprecated("")
    open fun registerPost(o: Any) {
        registerNoArgs("post", o)
    }

    @Deprecated("")
    open fun registerDispose(o: Any) {
        registerNoArgs("dispose", o)
    }

    @Deprecated("")
    open fun unregisterSize(o: Any?) {
        System.err.println("The unregisterSize() command is no longer supported.")
        //    Class<?> methodArgs[] = new Class[] { Integer.TYPE, Integer.TYPE };
//    unregisterWithArgs(sizeMethods, "size", o, methodArgs);
    }

    @Deprecated("")
    open fun unregisterPre(o: Any) {
        unregisterMethod("pre", o)
    }

    @Deprecated("")
    open fun unregisterDraw(o: Any) {
        unregisterMethod("draw", o)
    }

    @Deprecated("")
    open fun unregisterPost(o: Any) {
        unregisterMethod("post", o)
    }

    @Deprecated("")
    open fun unregisterDispose(o: Any) {
        unregisterMethod("dispose", o)
    }

    //////////////////////////////////////////////////////////////

    open fun setup() {

    }

    open fun draw() {
        // if no draw method, then shut things down
        //System.out.println("no draw method, goodbye");
        finished = true
    }


    //////////////////////////////////////////////////////////////
    //  protected void resizeRenderer(int iwidth, int iheight) {
    ////    println("resizeRenderer request for " + iwidth + " " + iheight);
    //    if (width != iwidth || height != iheight) {
    ////      int left = (screenWidth - iwidth) / 2;
    ////      int right = screenWidth - (left + iwidth);
    ////      int top = (screenHeight - iheight) / 2;
    ////      int bottom = screenHeight - (top + iheight);
    ////      surfaceView.setPadding(left, top, right, bottom);
    //
    //      g.setSize(iwidth, iheight);
    //      width = iwidth;
    //      height = iheight;
    //      overallLayout.invalidate();
    //      layout.invalidate();
    //    }
    //  }


    /**
     * Create a full-screen sketch using the default renderer.
     */
    open fun fullScreen() {
        if (!fullScreen) {
            if (insideSettings("fullScreen")) {
                fullScreen = true
            }
        }
    }

    open fun fullScreen(display: Int) {
        //Display index doesn't make sense in Android.
        //Should we throw some error in log ?
        if (!fullScreen /*|| display != this.display*/) {
            if (insideSettings("fullScreen", display)) {
                fullScreen = true
                //        this.display = display;
            }
        }
    }

    open fun fullScreen(renderer: String) {
        if (!fullScreen ||
                renderer != this.renderer) {
            if (insideSettings("fullScreen", renderer)) {
                fullScreen = true
                this.renderer = renderer
            }
        }
    }

    open fun fullScreen(renderer: String, display: Int) {
        if (!fullScreen ||
                renderer != this.renderer /*||
        display != this.display*/) {
            if (insideSettings("fullScreen", renderer, display)) {
                fullScreen = true
                this.renderer = renderer
                //        this.display = display;
            }
        }
    }


    /**
     * Starts up and creates a two-dimensional drawing surface, or resizes the
     * current drawing surface.
     * <P>
     * This should be the first thing called inside of setup().
    </P> * <P>
     * If called once a renderer has already been set, this will use the
     * previous renderer and simply resize it.
    </P> */
    open fun size(iwidth: Int, iheight: Int) {
        if (iwidth != width || iheight != height) {
            if (insideSettings("size", iwidth, iheight)) {
                width = iwidth
                height = iheight
            }
        }
    }

    open fun size(iwidth: Int, iheight: Int, irenderer: String) {
        if (iwidth != width || iheight != height ||
                renderer != irenderer) {
            if (insideSettings("size", iwidth, iheight, irenderer)) {
                width = iwidth
                height = iheight
                renderer = irenderer
            }
        }
    }

    open fun setSize(width: Int, height: Int) {
        if (fullScreen) {
            displayWidth = width
            displayHeight = height
        }
        this.width = width
        this.height = height
        pixelWidth = width * pixelDensity
        pixelHeight = height * pixelDensity
        graphics!!.setSize(sketchWidth(), sketchHeight())
    }

    // will throw JVM Clash due to var external: boolean
    open fun setExternal(external: Boolean) {
        this.external = external
    }

    //. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    @JvmOverloads
   open fun smooth(level: Int = 1) {
        if (insideSettings) {
            smooth = level
        } else if (smooth != level) {
            smoothWarning("smooth")
        }
    }

    open fun noSmooth() {
        if (insideSettings) {
            smooth = 0
        } else if (smooth != 0) {
            smoothWarning("noSmooth")
        }
    }

    // may be non-nullable
    private fun smoothWarning(method: String) {
        // When running from the PDE, say setup(), otherwise say settings()
        val where = if (external) "setup" else "settings"
        showWarning("%s() can only be used inside %s()", method, where)
    }

    open fun orientation(which: Int) {
        surface!!.setOrientation(which)
    }
    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    // not finished yet--will swap the renderer at a bad time
    /*
  public void renderer(String name) {
    if (name.equals(A2D)) {
      if (!(surfaceView instanceof SketchSurfaceView2D)) {
        surfaceView = new SketchSurfaceView2D(this);
        getWindow().setContentView(surfaceView);  // set full screen
      }
    } else if (name.equals(A3D)) {
      if (!(surfaceView instanceof SketchSurfaceView3D)) {
        surfaceView = new SketchSurfaceView3D(this);
        getWindow().setContentView(surfaceView);  // set full screen
      }
    }
  }
  */

    /**
     * Creates a new PGraphics object and sets it to the specified size.
     *
     * Note that you cannot change the renderer once outside of setup().
     * In most cases, you can call size() to give it a new size,
     * but you need to always ask for the same renderer, otherwise
     * you're gonna run into trouble.
     *
     * The size() method should *only* be called from inside the setup() or
     * draw() methods, so that it is properly run on the main animation thread.
     * To change the size of a PApplet externally, use setSize(), which will
     * update the component size, and queue a resize of the renderer as well.
     */
    open fun size(iwidth: Int, iheight: Int,
             irenderer: String, ipath: String?) {
        if (iwidth != width || iheight != height ||
                renderer != irenderer) {
            if (insideSettings("size", iwidth, iheight, irenderer,
                            ipath)) {
                width = iwidth
                height = iheight
                renderer = irenderer
            }
        }
    }

    /**
     * Create an offscreen PGraphics object for drawing. This can be used
     * for bitmap or vector images drawing or rendering.
     * <UL>
     * <LI>Do not use "new PGraphicsXxxx()", use this method. This method
     * ensures that internal variables are set up properly that tie the
     * new graphics context back to its parent PApplet.
    </LI> * <LI>The basic way to create bitmap images is to use the <A HREF="http://processing.org/reference/saveFrame_.html">saveFrame()</A>
     * function.
    </LI> * <LI>If you want to create a really large scene and write that,
     * first make sure that you've allocated a lot of memory in the Preferences.
    </LI> * <LI>If you want to create images that are larger than the screen,
     * you should create your own PGraphics object, draw to that, and use
     * <A HREF="http://processing.org/reference/save_.html">save()</A>.
     * For now, it's best to use <A HREF="http://dev.processing.org/reference/everything/javadoc/processing/core/PGraphics3D.html">P3D</A> in this scenario.
     * P2D is currently disabled, and the JAVA2D default will give mixed
     * results. An example of using P3D:
     * <PRE>
     *
     * PGraphics big;
     *
     * void setup() {
     * big = createGraphics(3000, 3000, P3D);
     *
     * big.beginDraw();
     * big.background(128);
     * big.line(20, 1800, 1800, 900);
     * // etc..
     * big.endDraw();
     *
     * // make sure the file is written to the sketch folder
     * big.save("big.tif");
     * }
     *
    </PRE> *
    </LI> * <LI>It's important to always wrap drawing to createGraphics() with
     * beginDraw() and endDraw() (beginFrame() and endFrame() prior to
     * revision 0115). The reason is that the renderer needs to know when
     * drawing has stopped, so that it can update itself internally.
     * This also handles calling the defaults() method, for people familiar
     * with that.
    </LI> * <LI>It's not possible to use createGraphics() with the OPENGL renderer,
     * because it doesn't allow offscreen use.
    </LI> * <LI>With Processing 0115 and later, it's possible to write images in
     * formats other than the default .tga and .tiff. The exact formats and
     * background information can be found in the developer's reference for
     * <A HREF="http://dev.processing.org/reference/core/javadoc/processing/core/PImage.html#save(java.lang.String)">PImage.save()</A>.
    </LI></UL> *
     */
    @JvmOverloads
    open fun createGraphics(iwidth: Int, iheight: Int, irenderer: String = PConstants.JAVA2D): PGraphics? {
        return makeGraphics(iwidth, iheight, irenderer, false)
    }

    // return nullability
    protected fun makeGraphics(w: Int, h: Int,
                               renderer: String, primary: Boolean): PGraphics? {
        var pg: PGraphics? = null
        if (renderer == PConstants.JAVA2D) {
            pg = PGraphicsAndroid2D()
        } else if (renderer == PConstants.P2D) {
            if (!primary && !graphics!!.isGL) {
                throw RuntimeException("createGraphics() with P2D requires size() to use P2D or P3D")
            }
            pg = PGraphics2D()
        } else if (renderer == PConstants.P3D) {
            if (!primary && !graphics!!.isGL) {
                throw RuntimeException("createGraphics() with P3D or OPENGL requires size() to use P2D or P3D")
            }
            pg = PGraphics3D()
        } else {
            var rendererClass: Class<*>? = null
            var constructor: Constructor<*>? = null
            rendererClass = try {
                // http://code.google.com/p/android/issues/detail?id=11101
                Thread.currentThread().contextClassLoader.loadClass(renderer)
            } catch (cnfe: ClassNotFoundException) {
                throw RuntimeException("Missing renderer class")
            }
            if (rendererClass != null) {
                constructor = try {
                    rendererClass.getConstructor(*arrayOf())
                } catch (nsme: NoSuchMethodException) {
                    throw RuntimeException("Missing renderer constructor")
                }
                if (constructor != null) {
                    try {
                        pg = constructor.newInstance() as PGraphics
                    } catch (e: InvocationTargetException) {
                        printStackTrace(e)
                        throw RuntimeException(e.message)
                    } catch (e: IllegalAccessException) {
                        printStackTrace(e)
                        throw RuntimeException(e.message)
                    } catch (e: InstantiationException) {
                        printStackTrace(e)
                        throw RuntimeException(e.message)
                    } catch (e: IllegalArgumentException) {
                        // TODO Auto-generated catch block
                        printStackTrace(e)
                    }
                }
            }
        }
        pg!!.setparent(this)
        pg.setprimary(primary)
        pg.setSize(w, h)
        return pg
    }

    /**
     * Create an offscreen graphics surface for drawing, in this case
     * for a renderer that writes to a file (such as PDF or DXF).
     * @param ipath can be an absolute or relative path
     */
    //  public PGraphics createGraphics(int iwidth, int iheight,
    //                                  String irenderer, String ipath) {
    //    if (ipath != null) {
    //      ipath = savePath(ipath);
    //    }
    //    PGraphics pg = makeGraphics(iwidth, iheight, irenderer, ipath, false);
    //    pg.parent = this;  // make save() work
    //    return pg;
    //  }
    /**
     * Version of createGraphics() used internally.
     *
     * @param ipath must be an absolute path, usually set via savePath()
     * @oaram applet the parent applet object, this should only be non-null
     * in cases where this is the main drawing surface object.
     */
    /*
  protected PGraphics makeGraphics(int iwidth, int iheight,
                                   String irenderer, String ipath,
                                   boolean iprimary) {
    try {
      Class<?> rendererClass =
        Thread.currentThread().getContextClassLoader().loadClass(irenderer);

      Constructor<?> constructor = rendererClass.getConstructor(new Class[] { });
      PGraphics pg = (PGraphics) constructor.newInstance();

      pg.setParent(this);
      pg.setPrimary(iprimary);
      if (ipath != null) pg.setPath(ipath);
      pg.setSize(iwidth, iheight);

      // everything worked, return it
      return pg;

    } catch (InvocationTargetException ite) {
      ite.getTargetException().printStackTrace();
      Throwable target = ite.getTargetException();
      throw new RuntimeException(target.getMessage());

    } catch (ClassNotFoundException cnfe) {
      throw new RuntimeException("You need to use \"Import Library\" " +
                                 "to add " + irenderer + " to your sketch.");
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage());
    }
  }
  */

    /**
     * Creates a new PImage (the datatype for storing images). This provides a fresh buffer of pixels to play with. Set the size of the buffer with the **width** and **height** parameters. The **format** parameter defines how the pixels are stored. See the PImage reference for more information.
     */
    // todo - may be return nullability
    open fun createImage(wide: Int, high: Int, format: Int): PImage {
//    return createImage(wide, high, format, null);
//  }
//
//
//  /**
//   * Preferred method of creating new PImage objects, ensures that a
//   * reference to the parent PApplet is included, which makes save() work
//   * without needing an absolute path.
//   */
//  public PImage createImage(int wide, int high, int format, Object params) {
        val image = PImage(wide, high, format)
        //    if (params != null) {
//      image.setParams(g, params);
//    }
        image.parent = this // make save() work
        return image
    }

    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
    // not necessary, ja?
    //  public void update(Graphics screen) {
    //    paint(screen);
    //  }
    /*
  //synchronized public void paint(Graphics screen) {  // shutting off for 0146
  public void paint(Graphics screen) {
    // ignore the very first call to paint, since it's coming
    // from the o.s., and the applet will soon update itself anyway.
    if (frameCount == 0) {
//      println("Skipping frame");
      // paint() may be called more than once before things
      // are finally painted to the screen and the thread gets going
      return;
    }

    // without ignoring the first call, the first several frames
    // are confused because paint() gets called in the midst of
    // the initial nextFrame() call, so there are multiple
    // updates fighting with one another.

    // g.image is synchronized so that draw/loop and paint don't
    // try to fight over it. this was causing a randomized slowdown
    // that would cut the frameRate into a third on macosx,
    // and is probably related to the windows sluggishness bug too

    // make sure the screen is visible and usable
    // (also prevents over-drawing when using PGraphicsOpenGL)
    if ((g != null) && (g.image != null)) {
//      println("inside paint(), screen.drawImage()");
      screen.drawImage(g.image, 0, 0, null);
    }
  }
  */
    // active paint method
    /*
  protected void paint() {
    try {
      Graphics screen = this.getGraphics();
      if (screen != null) {
        if ((g != null) && (g.image != null)) {
          screen.drawImage(g.image, 0, 0, null);
        }
        Toolkit.getDefaultToolkit().sync();
      }
    } catch (Exception e) {
      // Seen on applet destroy, maybe can ignore?
      e.printStackTrace();

    } finally {
      if (g != null) {
        g.dispose();
      }
    }
  }
  */
    //////////////////////////////////////////////////////////////
    /*
  public void run() {  // not good to make this synchronized, locks things up
    long beforeTime = System.nanoTime();
    long overSleepTime = 0L;

    int noDelays = 0;
    // Number of frames with a delay of 0 ms before the
    // animation thread yields to other running threads.
    final int NO_DELAYS_PER_YIELD = 15;

    while (!finished) {

      while (paused) {
        try{
          Thread.sleep(100L);
        } catch (InterruptedException e) {
          //ignore?
        }
      }

      // render a single frame
      if (g != null) g.requestDraw();

      // wait for update & paint to happen before drawing next frame
      // this is necessary since the drawing is sometimes in a
      // separate thread, meaning that the next frame will start
      // before the update/paint is completed

      long afterTime = System.nanoTime();
      long timeDiff = afterTime - beforeTime;
      //System.out.println("time diff is " + timeDiff);
      long sleepTime = (frameRatePeriod - timeDiff) - overSleepTime;

      if (sleepTime > 0) {  // some time left in this cycle
        try {
//          Thread.sleep(sleepTime / 1000000L);  // nanoseconds -> milliseconds
          Thread.sleep(sleepTime / 1000000L, (int) (sleepTime % 1000000L));
          noDelays = 0;  // Got some sleep, not delaying anymore
        } catch (InterruptedException ex) { }

        overSleepTime = (System.nanoTime() - afterTime) - sleepTime;
        //System.out.println("  oversleep is " + overSleepTime);

      } else {    // sleepTime <= 0; the frame took longer than the period
//        excess -= sleepTime;  // store excess time value
        overSleepTime = 0L;

        if (noDelays > NO_DELAYS_PER_YIELD) {
          Thread.yield();   // give another thread a chance to run
          noDelays = 0;
        }
      }

      beforeTime = System.nanoTime();
    }

    // if this isn't just a pause, shut it all down
    if (!paused) {
      stop();  // call to shutdown libs?

      // If the user called the exit() function, the window should close,
      // rather than the sketch just halting.
      if (exitCalled) {
        exitActual();
      }
    }
  }
*/

    open fun handleDraw() {
        //debug("handleDraw() " + g + " " + looping + " " + redraw + " valid:" + this.isValid() + " visible:" + this.isVisible());
        if (graphics == null) return
        if (!surfaceChanged && parentLayout != -1) {
            // When using a parent layout, don't start drawing until the sketch
            // has been properly sized.
            return
        }
        if (!isLooping && !redraw) return
        if (insideDraw) {
            System.err.println("handleDraw() called before finishing")
            System.exit(1)
        }
        insideDraw = true

//    if (recorder != null) {
//      recorder.beginDraw();
//    }
        if (handleSpecialDraw()) return
        graphics!!.beginDraw()
        val now = System.nanoTime()
        if (frameCount == 0) {
            setup()
        } else {  // frameCount > 0, meaning an actual draw()
            // update the current frameRate
            val rate = 1000000.0 / ((now - frameRateLastNanos) / 1000000.0)
            val instantaneousRate = (rate / 1000.0).toFloat()
            frameRate = frameRate * 0.9f + instantaneousRate * 0.1f
            if (frameCount != 0) {
                handleMethods("pre")
            }

            // use dmouseX/Y as previous mouse pos, since this is the
            // last position the mouse was in during the previous draw.
            pmouseX = dmouseX
            pmouseY = dmouseY
            draw()

            // dmouseX/Y is updated only once per frame (unlike emouseX/Y)
            dmouseX = mouseX
            dmouseY = mouseY

            // these are called *after* loop so that valid
            // drawing commands can be run inside them. it can't
            // be before, since a call to background() would wipe
            // out anything that had been drawn so far.
            dequeueEvents()
            handleMethods("draw")
            handlePermissions()
            handleBackPressed()
            redraw = false // unset 'redraw' flag in case it was set
            // (only do this once draw() has run, not just setup())
        }
        graphics!!.endDraw()

//    if (recorder != null) {
//      recorder.endDraw();
//    }
        insideDraw = false
        if (frameCount != 0) {
            handleMethods("post")
        }
        frameRateLastNanos = now
        frameCount++
    }

    // This method handles some special situations on Android where beginDraw/endDraw are needed,
    // but not to render the actual contents of draw(). In general, these situations arise from
    // having to refresh/restore the screen after requesting no loop, or resuming the sketch in
    // no-loop state.
    protected fun handleSpecialDraw(): Boolean {
        var handled = false
        if (graphics!!.restoringState()) {
            // The sketch is restoring, so begin/end the frame properly and quit drawing.
            graphics!!.beginDraw()
            graphics!!.endDraw()
            handled = true
        } else if (graphics!!.requestedNoLoop) {
            // noLoop() was called sometime in the previous frame with a GL renderer, but only now
            // we are sure that the frame is properly displayed.
            isLooping = false

            // Perform a full frame draw, to ensure that the previous frame is properly displayed (see
            // comment in the declaration of requestedNoLoop).
            graphics!!.beginDraw()
            graphics!!.endDraw()
            graphics!!.requestedNoLoop = false
            handled = true
        }
        return if (handled) {
            insideDraw = false
            true
        } else {
            false
        }
    }

    //////////////////////////////////////////////////////////////

    @Synchronized
    open fun redraw() {
        if (!isLooping) {
            redraw = true
            //      if (thread != null) {
//        // wake from sleep (necessary otherwise it'll be
//        // up to 10 seconds before update)
//        if (CRUSTY_THREADS) {
//          thread.interrupt();
//        } else {
//          synchronized (blocker) {
//            blocker.notifyAll();
//          }
//        }
//      }
        }
    }

    @Synchronized
    open fun loop() {
        if (!isLooping) {
            isLooping = true
        }
    }

    @Synchronized
    open fun noLoop() {
        if (isLooping) {
            if (graphics!!.requestNoLoop()) {
                graphics!!.requestedNoLoop = true
            } else {
                isLooping = false
            }
        }
    }


    //////////////////////////////////////////////////////////////

    // all these are handled in SurfaceView, which is a listener for all of em
    //  public void addListeners() {
    //    addMouseListener(this);
    //    addMouseMotionListener(this);
    //    addKeyListener(this);
    //    addFocusListener(this);
    //
    //    addComponentListener(new ComponentAdapter() {
    //      public void componentResized(ComponentEvent e) {
    //        Component c = e.getComponent();
    //        //System.out.println("componentResized() " + c);
    //        Rectangle bounds = c.getBounds();
    //        resizeRequest = true;
    //        resizeWidth = bounds.width;
    //        resizeHeight = bounds.height;
    //      }
    //    });
    //  }


    //////////////////////////////////////////////////////////////

    // todo - not sure about nullability
    var eventQueue = InternalEventQueue()

    inner class InternalEventQueue {
        protected var queue = arrayOfNulls<Event>(10)
        protected var offset = 0
        protected var count = 0

        @Synchronized
        fun add(e: Event?) {
            if (count == queue.size) {
                queue = expand(queue) as Array<Event?>
            }
            queue[count++] = e
        }

        @Synchronized
        fun remove(): Event? {
            if (offset == count) {
                throw RuntimeException("Nothing left on the event queue.")
            }
            val outgoing = queue[offset++]
            if (offset == count) {
                // All done, time to reset
                offset = 0
                count = 0
            }
            return outgoing
        }

        @Synchronized
        fun available(): Boolean {
            return count != 0
        }

    }

    /**
     * Add an event to the internal event queue, or process it immediately if
     * the sketch is not currently looping.
     */
    open fun postEvent(pe: Event?) {
        eventQueue.add(pe)
        if (!isLooping) {
            dequeueEvents()
        }
    }

    protected fun dequeueEvents() {
        while (eventQueue.available()) {
            val e = eventQueue.remove()
            when (e!!.flavor) {
                Event.TOUCH -> handleTouchEvent(e as TouchEvent?)
                Event.MOUSE -> handleMouseEvent(e as MouseEvent?)
                Event.KEY -> handleKeyEvent(e as KeyEvent?)
            }
        }
    }


    //////////////////////////////////////////////////////////////

    protected fun handleMouseEvent(event: MouseEvent?) {
//    mouseEvent = event;

        // http://dev.processing.org/bugs/show_bug.cgi?id=170
        // also prevents mouseExited() on the mac from hosing the mouse
        // position, because x/y are bizarre values on the exit event.
        // see also the id check below.. both of these go together
//  if ((id == java.awt.event.MouseEvent.MOUSE_DRAGGED) ||
//      (id == java.awt.event.MouseEvent.MOUSE_MOVED)) {
        if (event!!.action == MouseEvent.DRAG ||
                event.action == MouseEvent.MOVE) {
            pmouseX = emouseX
            pmouseY = emouseY
            mouseX = event.x
            mouseY = event.y
        }

        // Because we only get DRAGGED (and no MOVED) events, pmouseX/Y will make
        // things jump because they aren't updated while a finger isn't on the
        // screen. This makes for weirdness with the continuous lines example,
        // causing it to jump. Since we're emulating the mouse here, do the right
        // thing for mouse events. It breaks the situation where random taps/clicks
        // to the screen won't show up as 'previous' values, but that's probably
        // less common.
//    if (event.getAction() == MouseEvent.PRESS) {
//      System.out.println("resetting");
////      pmouseX = event.getX();
////      pmouseY = event.getY();
//      firstMotion = true;
//    }

        // Get the (already processed) button code
        mouseButton = event.button

        // Added in 0215 (2.0b7) so that pmouseX/Y behave more like one would
        // expect from the desktop. This makes the ContinousLines example behave.
        if (event.action == MouseEvent.PRESS) {
            mouseX = event.x
            mouseY = event.y
            pmouseX = mouseX
            pmouseY = mouseY
            dmouseX = mouseX
            dmouseY = mouseY
        }
        when (event.action) {
            MouseEvent.PRESS -> mousePressed = true
            MouseEvent.RELEASE -> mousePressed = false
        }
        handleMethods("mouseEvent", arrayOf(event))
        when (event.action) {
            MouseEvent.PRESS -> mousePressed(event)
            MouseEvent.RELEASE -> mouseReleased(event)
            MouseEvent.CLICK -> mouseClicked(event)
            MouseEvent.DRAG -> mouseDragged(event)
            MouseEvent.MOVE -> mouseMoved(event)
            MouseEvent.ENTER -> mouseEntered(event)
            MouseEvent.EXIT -> mouseExited(event)
        }
        if (event.action == MouseEvent.DRAG ||
                event.action == MouseEvent.MOVE) {
            emouseX = mouseX
            emouseY = mouseY
        }
        if (event.action == MouseEvent.PRESS) {  // Android-only
            emouseX = mouseX
            emouseY = mouseY
        }
        //    if (event.getAction() == MouseEvent.RELEASE) {  // Android-only
//      emouseX = mouseX;
//      emouseY = mouseY;
//    }
    }

    protected fun handleTouchEvent(event: TouchEvent?) {
        touches = event!!.getTouches(touches)
        when (event.action) {
            TouchEvent.START -> touchIsStarted = true
            TouchEvent.END -> touchIsStarted = false
        }
        handleMethods("touchEvent", arrayOf(event))
        when (event.action) {
            TouchEvent.START -> touchStarted(event)
            TouchEvent.END -> touchEnded(event)
            TouchEvent.MOVE -> touchMoved(event)
            TouchEvent.CANCEL -> touchCancelled(event)
        }
    }

    /**
     * Figure out how to process a mouse event. When loop() has been
     * called, the events will be queued up until drawing is complete.
     * If noLoop() has been called, then events will happen immediately.
     */
    protected fun nativeMotionEvent(motionEvent: MotionEvent) {
        val metaState = motionEvent.metaState
        var modifiers = 0
        if (metaState and android.view.KeyEvent.META_SHIFT_ON != 0) {
            modifiers = modifiers or Event.SHIFT
        }
        if (metaState and android.view.KeyEvent.META_CTRL_ON != 0) {
            modifiers = modifiers or Event.CTRL
        }
        if (metaState and android.view.KeyEvent.META_META_ON != 0) {
            modifiers = modifiers or Event.META
        }
        if (metaState and android.view.KeyEvent.META_ALT_ON != 0) {
            modifiers = modifiers or Event.ALT
        }
        val button: Int
        val state = motionEvent.buttonState
        button = when (state) {
            MotionEvent.BUTTON_PRIMARY -> PConstants.LEFT
            MotionEvent.BUTTON_SECONDARY -> PConstants.RIGHT
            MotionEvent.BUTTON_TERTIARY -> PConstants.CENTER
            else ->         // Covers the BUTTON_FORWARD, BUTTON_BACK,
                // BUTTON_STYLUS_PRIMARY, and BUTTON_STYLUS_SECONDARY
                state
        }
        enqueueMouseEvents(motionEvent, button, modifiers)
        enqueueTouchEvents(motionEvent, button, modifiers)
    }


    protected fun enqueueTouchEvents(event: MotionEvent, button: Int, modifiers: Int) {
        val action = event.action
        val actionMasked = action and MotionEvent.ACTION_MASK
        var paction = 0
        paction = when (actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchEvent.START
            MotionEvent.ACTION_POINTER_DOWN -> TouchEvent.START
            MotionEvent.ACTION_MOVE -> TouchEvent.MOVE
            MotionEvent.ACTION_UP -> TouchEvent.END
            MotionEvent.ACTION_POINTER_UP -> TouchEvent.END
            else ->       // Covers any other action value, including ACTION_CANCEL
                TouchEvent.CANCEL
        }
        if (paction == TouchEvent.START || paction == TouchEvent.END) {
            touchPointerId = event.getPointerId(0)
        }
        val pointerCount = event.pointerCount
        if (actionMasked == MotionEvent.ACTION_MOVE) {
            // Post historical movement events, if any.
            val historySize = event.historySize
            for (h in 0 until historySize) {
                val touchEvent = TouchEvent(event, event.getHistoricalEventTime(h),
                        paction, modifiers, button)
                touchEvent.setNumPointers(pointerCount)
                for (p in 0 until pointerCount) {
                    touchEvent.setPointer(p, event.getPointerId(p), event.getHistoricalX(p, h), event.getHistoricalY(p, h),
                            event.getHistoricalSize(p, h), event.getHistoricalPressure(p, h))
                }
                postEvent(touchEvent)
            }
        }

        // Current event
        val touchEvent = TouchEvent(event, event.eventTime,
                paction, modifiers, button)
        if (actionMasked == MotionEvent.ACTION_UP) {
            // Last pointer up
            touchEvent.setNumPointers(0)
        } else {
            // We still have some pointers left
            touchEvent.setNumPointers(pointerCount)
            for (p in 0 until event.pointerCount) {
                touchEvent.setPointer(p, event.getPointerId(p), event.getX(p), event.getY(p),
                        event.getSize(p), event.getPressure(p))
            }
        }
        postEvent(touchEvent)
    }


    protected fun enqueueMouseEvents(event: MotionEvent, button: Int, modifiers: Int) {
        val action = event.action
        val clickCount = 1 // not really set... (i.e. not catching double taps)
        val index: Int
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mousePointerId = event.getPointerId(0)
                postEvent(MouseEvent(event, event.eventTime,
                        MouseEvent.PRESS, modifiers,
                        event.x.toInt(), event.y.toInt(),
                        button, clickCount))
            }
            MotionEvent.ACTION_MOVE -> {
                index = event.findPointerIndex(mousePointerId)
                if (index != -1) {
                    postEvent(MouseEvent(event, event.eventTime,
                            MouseEvent.DRAG, modifiers,
                            event.getX(index).toInt(), event.getY(index).toInt(),
                            button, clickCount))
                }
            }
            MotionEvent.ACTION_UP -> {
                index = event.findPointerIndex(mousePointerId)
                if (index != -1) {
                    postEvent(MouseEvent(event, event.eventTime,
                            MouseEvent.RELEASE, modifiers,
                            event.getX(index).toInt(), event.getY(index).toInt(),
                            button, clickCount))
                }
            }
        }
    }

    open fun mousePressed() {

    }

    open fun mousePressed(event: MouseEvent?) {
        mousePressed()
    }

    open fun mouseReleased() {

    }

    open fun mouseReleased(event: MouseEvent?) {
        mouseReleased()
    }

    /**
     * mouseClicked is currently not fired at all (no direct match on Android).
     * http://code.google.com/p/processing/issues/detail?id=215
     */
    open fun mouseClicked() {

    }

    open fun mouseClicked(event: MouseEvent?) {
        mouseClicked()
    }

    open fun mouseDragged() {

    }

    open fun mouseDragged(event: MouseEvent?) {
        mouseDragged()
    }

    open fun mouseMoved() {

    }

    open fun mouseMoved(event: MouseEvent?) {
        mouseMoved()
    }

    open fun mouseEntered() {

    }

    open fun mouseEntered(event: MouseEvent?) {
        mouseEntered()
    }

    open fun mouseExited() {

    }

    open fun mouseExited(event: MouseEvent?) {
        mouseExited()
    }

    open fun touchStarted() {

    }

    open fun touchStarted(event: TouchEvent?) {
        touchStarted()
    }

    open fun touchMoved() {

    }

    open fun touchMoved(event: TouchEvent?) {
        touchMoved()
    }

    open fun touchEnded() {

    }

    open fun touchEnded(event: TouchEvent?) {
        touchEnded()
    }

    open fun touchCancelled() {

    }

    open fun touchCancelled(event: TouchEvent?) {
        touchCancelled()
    }

    //////////////////////////////////////////////////////////////
    //  KeyEvent[] keyEventQueue = new KeyEvent[10];
    //  int keyEventCount;
    //
    //  protected void enqueueKeyEvent(KeyEvent e) {
    //    synchronized (keyEventQueue) {
    //      if (keyEventCount == keyEventQueue.length) {
    //        keyEventQueue = (KeyEvent[]) expand(keyEventQueue);
    //      }
    //      keyEventQueue[keyEventCount++] = e;
    //    }
    //  }
    //
    //  protected void dequeueKeyEvents() {
    //    synchronized (keyEventQueue) {
    //      for (int i = 0; i < keyEventCount; i++) {
    //        handleKeyEvent(keyEventQueue[i]);
    //      }
    //      keyEventCount = 0;
    //    }
    //  }


    protected fun handleKeyEvent(event: KeyEvent?) {

        // Get rid of auto-repeating keys if desired and supported
        if (!keyRepeatEnabled && event!!.isAutoRepeat) return

//    keyEvent = event;
        key = event!!.key
        keyCode = event.keyCode
        when (event.action) {
            KeyEvent.PRESS -> {
                keyPressed = true
                keyPressed(event)
            }
            KeyEvent.RELEASE -> {
                keyPressed = false
                keyReleased(event)
            }
        }
        handleMethods("keyEvent", arrayOf(event))
    }


    protected fun nativeKeyEvent(event: android.view.KeyEvent) {
        // event.isPrintingKey() returns false for whitespace and others,
        // which is a problem if the space bar or tab key are used.
        var key = event.unicodeChar.toChar()
        // if not mappable to a unicode character, instead mark as coded key
        if (key.toInt() == 0 || key.toInt() == 0xFFFF) {
            key = PConstants.CODED.toChar()
        }
        val keyCode = event.keyCode
        var keAction = 0
        val action = event.action
        if (action == android.view.KeyEvent.ACTION_DOWN) {
            keAction = KeyEvent.PRESS
        } else if (action == android.view.KeyEvent.ACTION_UP) {
            keAction = KeyEvent.RELEASE
        }

        // TODO set up proper key modifier handling
        val keModifiers = 0
        val ke = KeyEvent(event, event.eventTime,
                keAction, keModifiers, key, keyCode, 0 < event.repeatCount)
        postEvent(ke)
    }


    open fun openKeyboard() {
        val context = surface!!.getContext()
        val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        keyboardIsOpen = true
    }

    open fun closeKeyboard() {
        if (keyboardIsOpen) {
            val context = surface!!.getContext()
            val imm = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
            keyboardIsOpen = false
            if (parentLayout == -1) {
                setFullScreenVisibility()
            }
        }
    }

    open fun keyPressed() {

    }

    open fun keyPressed(event: KeyEvent?) {
        keyPressed()
    }

    /**
     * See keyPressed().
     */
    open fun keyReleased() {

    }

    open fun keyReleased(event: KeyEvent?) {
        keyReleased()
    }

    open fun keyTyped() {

    }

    open fun keyTyped(event: KeyEvent?) {
        keyTyped()
    }

    //////////////////////////////////////////////////////////////
    open fun focusGained() {

    }

    //  public void focusGained(FocusEvent e) {
    //    focused = true;
    //    focusGained();
    //  }

    open fun focusLost() {

    }

    //  public void focusLost(FocusEvent e) {
    //    focused = false;
    //    focusLost();
    //  }

    //////////////////////////////////////////////////////////////

    // getting the time
    /**
     * Get the number of milliseconds since the applet started.
     * <P>
     * This is a function, rather than a variable, because it may
     * change multiple times per frame.
    </P> */
    open fun millis(): Int {
        return (System.currentTimeMillis() - millisOffset).toInt()
    }

    //////////////////////////////////////////////////////////////

    // controlling time (playing god)
    /**
     * The delay() function causes the program to halt for a specified time.
     * Delay times are specified in thousandths of a second. For example,
     * running delay(3000) will stop the program for three seconds and
     * delay(500) will stop the program for a half-second.
     *
     * The screen only updates when the end of draw() is reached, so delay()
     * cannot be used to slow down drawing. For instance, you cannot use delay()
     * to control the timing of an animation.
     *
     * The delay() function should only be used for pausing scripts (i.e.
     * a script that needs to pause a few seconds before attempting a download,
     * or a sketch that needs to wait a few milliseconds before reading from
     * the serial port).
     */
    open fun delay(napTime: Int) {
        //if (frameCount != 0) {
        //if (napTime > 0) {
        try {
            Thread.sleep(napTime.toLong())
        } catch (e: InterruptedException) {
        }
        //}
        //}
    }

    /**
     * ( begin auto-generated from frameRate.xml )
     *
     * Specifies the number of frames to be displayed every second. If the
     * processor is not fast enough to maintain the specified rate, it will not
     * be achieved. For example, the function call **frameRate(30)** will
     * attempt to refresh 30 times a second. It is recommended to set the frame
     * rate within **setup()**. The default rate is 60 frames per second.
     *
     * ( end auto-generated )
     */
    open fun frameRate(fps: Float) {
//
//    frameRateTarget = newRateTarget;
//    frameRatePeriod = (long) (1000000000.0 / frameRateTarget);
//    g.setFrameRate(newRateTarget);
        surface!!.setFrameRate(fps)
    }


    //////////////////////////////////////////////////////////////

    /**
     * Get a param from the web page, or (eventually)
     * from a properties file.
     */
    //  public String param(String what) {
    //    if (online) {
    //      return getParameter(what);
    //
    //    } else {
    //      System.err.println("param() only works inside a web browser");
    //    }
    //    return null;
    //  }
    /**
     * Show status in the status bar of a web browser, or in the
     * System.out console. Eventually this might show status in the
     * p5 environment itself, rather than relying on the console.
     */
    //  public void status(String what) {
    //    if (online) {
    //      showStatus(what);
    //
    //    } else {
    //      System.out.println(what);  // something more interesting?
    //    }
    //  }
    open fun link(here: String?) {
        link(here, null)
    }

    /**
     * Link to an external page without all the muss.
     * <P>
     * When run with an applet, uses the browser to open the url,
     * for applications, attempts to launch a browser with the url.
    </P> * <P>
     * Works on Mac OS X and Windows. For Linux, use:
    </P> * <PRE>open(new String[] { "firefox", url });</PRE>
     * or whatever you want as your browser, since Linux doesn't
     * yet have a standard method for launching URLs.
     */
    // todo - frameTitle is nullable; not sure about url
    open fun link(url: String?, frameTitle: String?) {
        val viewIntent = Intent("android.intent.action.VIEW", Uri.parse(url))
        surface!!.startActivity(viewIntent)
    }


    //////////////////////////////////////////////////////////////

    /**
     * Better way of handling e.printStackTrace() calls so that they can be
     * handled by subclasses as necessary.
     */
    protected fun printStackTrace(t: Throwable) {
        t.printStackTrace()
    }

    /**
     * Function for an applet/application to kill itself and
     * display an error. Mostly this is here to be improved later.
     */
    // todo - maybe nullable
    open fun die(what: String?) {
        stop()
        throw RuntimeException(what)
    }

    /**
     * Same as above but with an exception. Also needs work.
     */
    // todo - e is nullable
    open fun die(what: String?, e: Exception?) {
        e?.printStackTrace()
        die(what)
    }

    /**
     * Conveniency method so perform initialization tasks when the activity is
     * created, while avoiding the ackward call to onCreate() with the bundle
     * and super.onCreate().
     */
    open fun create() {

    }

    /**
     * Should trigger a graceful activity/service shutdown (calling onPause/onStop, etc).
     */
    open fun exit() {
        surface!!.finish()
    }

    /**
     * Called to dispose of resources and shut down the sketch.
     * Destroys the thread, dispose the renderer, and notify listeners.
     *
     *
     * Not to be called or overriden by users. If called multiple times,
     * will only notify listeners once. Register a dispose listener instead.
     */
    open fun dispose() {
        // moved here from stop()
        finished = true // let the sketch know it is shut down time

        // call to shut down renderer, in case it needs it (pdf does)
        if (surface != null) {
            surface!!.stopThread()
            surface!!.dispose()
        }
        if (graphics != null) {
            graphics!!.clearState() // This should probably go in dispose, but for the time being...
            graphics!!.dispose()
        }
        handleMethods("dispose")
    }


    //////////////////////////////////////////////////////////////

    /**
     * Call a method in the current class based on its name.
     *
     *
     * Note that the function being called must be public. Inside the PDE,
     * 'public' is automatically added, but when used without the preprocessor,
     * (like from Eclipse) you'll have to do it yourself.
     */
    // TODO - must be non-nullable as per declaration of getClass().getMethod(name)...
    fun method(name: String) {
        try {
            val method = javaClass.getMethod(name, *arrayOf())
            method.invoke(this, *arrayOf())
        } catch (e: IllegalArgumentException) {
            printStackTrace(e)
        } catch (e: IllegalAccessException) {
            printStackTrace(e)
        } catch (e: InvocationTargetException) {
            e.targetException.printStackTrace()
        } catch (nsme: NoSuchMethodException) {
            System.err.println("There is no public " + name + "() method " +
                    "in the class " + javaClass.name)
        } catch (e: Exception) {
            printStackTrace(e)
        }
    }

    /**
     * Launch a new thread and call the specified function from that new thread.
     * This is a very simple way to do a thread without needing to get into
     * classes, runnables, etc.
     *
     *
     * Note that the function being called must be public. Inside the PDE,
     * 'public' is automatically added, but when used without the preprocessor,
     * (like from Eclipse) you'll have to do it yourself.
     */
    // TODO - non-nullable arg {name} as per upper todo()
    open fun thread(name: String) {
        val later: Thread = object : Thread() {
            override fun run() {
                method(name)
            }
        }
        later.start()
    }


    //////////////////////////////////////////////////////////////

    // SCREEN GRABASS

    /**
     * Intercepts any relative paths to make them absolute (relative
     * to the sketch folder) before passing to save() in PImage.
     * (Changed in 0100)
     */
    // TODO - NON-NULLABLE arg {filename} as per g.save(name) in parent class
    open fun save(filename: String?) {
        graphics!!.save(savePath(filename)!!)
    }

    /**
     * Grab an image of what's currently in the drawing area and save it
     * as a .tif or .tga file.
     * <P>
     * Best used just before endDraw() at the end of your draw().
     * This can only create .tif or .tga images, so if neither extension
     * is specified it defaults to writing a tiff and adds a .tif suffix.
    </P> */
    open fun saveFrame() {
        try {
            graphics!!.save(savePath("screen-" + nf(frameCount, 4) + ".tif")!!)
        } catch (se: SecurityException) {
            System.err.println("Can't use saveFrame() when running in a browser, " +
                    "unless using a signed applet.")
        }
    }

    /**
     * Save the current frame as a .tif or .tga image.
     * <P>
     * The String passed in can contain a series of # signs
     * that will be replaced with the screengrab number.
    </P> * <PRE>
     * i.e. saveFrame("blah-####.tif");
     * // saves a numbered tiff image, replacing the
     * // #### signs with zeros and the frame number </PRE>
     */
    open fun saveFrame(what: String) {
        try {
            graphics!!.save(savePath(insertFrame(what))!!)
        } catch (se: SecurityException) {
            System.err.println("Can't use saveFrame() when running in a browser, " +
                    "unless using a signed applet.")
        }
    }

    /**
     * Check a string for #### signs to see if the frame number should be
     * inserted. Used for functions like saveFrame() and beginRecord() to
     * replace the # marks with the frame number. If only one # is used,
     * it will be ignored, under the assumption that it's probably not
     * intended to be the frame number.
     */
    // todo - what is og non-nullable type
    protected fun insertFrame(what: String): String {
        val first = what.indexOf('#')
        val last = what.lastIndexOf('#')
        if (first != -1 && last - first > 0) {
            val prefix = what.substring(0, first)
            val count = last - first + 1
            val suffix = what.substring(last + 1)
            return prefix + nf(frameCount, count) + suffix
        }
        return what // no change
    }

    //////////////////////////////////////////////////////////////

    // RANDOM NUMBERS

    // todo - nullable type
    var internalRandom: Random? = null

    /**
     *
     */
    open fun random(high: Float): Float {
        // avoid an infinite loop when 0 or NaN are passed in
        if (high == 0F || high != high) {
            return 0F
        }
        if (internalRandom == null) {
            internalRandom = Random()
        }

        // for some reason (rounding error?) Math.random() * 3
        // can sometimes return '3' (once in ~30 million tries)
        // so a check was added to avoid the inclusion of 'howbig'
        var value = 0f
        do {
            value = internalRandom!!.nextFloat() * high
        } while (value == high)
        return value
    }

    /**
     * ( begin auto-generated from randomGaussian.xml )
     *
     * Returns a float from a random series of numbers having a mean of 0
     * and standard deviation of 1. Each time the **randomGaussian()**
     * function is called, it returns a number fitting a Gaussian, or
     * normal, distribution. There is theoretically no minimum or maximum
     * value that **randomGaussian()** might return. Rather, there is
     * just a very low probability that values far from the mean will be
     * returned; and a higher probability that numbers near the mean will
     * be returned.
     *
     * ( end auto-generated )
     * @webref math:random
     * @see PApplet.random
     * @see PApplet.noise
     */
    open fun randomGaussian(): Float {
        if (internalRandom == null) {
            internalRandom = Random()
        }
        return internalRandom!!.nextGaussian().toFloat()
    }

    /**
     * ( begin auto-generated from random.xml )
     *
     * Generates random numbers. Each time the **random()** function is
     * called, it returns an unexpected value within the specified range. If
     * one parameter is passed to the function it will return a **float**
     * between zero and the value of the **high** parameter. The function
     * call **random(5)** returns values between 0 and 5 (starting at zero,
     * up to but not including 5). If two parameters are passed, it will return
     * a **float** with a value between the the parameters. The function
     * call **random(-5, 10.2)** returns values starting at -5 up to (but
     * not including) 10.2. To convert a floating-point random number to an
     * integer, use the **int()** function.
     *
     * ( end auto-generated )
     * @webref math:random
     * @param low lower limit
     * @param high upper limit
     * @see PApplet.randomSeed
     * @see PApplet.noise
     */
    open fun random(low: Float, high: Float): Float {
        if (low >= high) return low
        val diff = high - low
        var value = 0f
        // because of rounding error, can't just add low, otherwise it may hit high
        // https://github.com/processing/processing/issues/4551
        do {
            value = random(diff) + low
        } while (value == high)
        return value
    }

    /**
     * ( begin auto-generated from randomSeed.xml )
     *
     * Sets the seed value for **random()**. By default, **random()**
     * produces different results each time the program is run. Set the
     * **value** parameter to a constant to return the same pseudo-random
     * numbers each time the software is run.
     *
     * ( end auto-generated )
     * @webref math:random
     * @param seed seed value
     * @see PApplet.random
     * @see PApplet.noise
     * @see PApplet.noiseSeed
     */
    open fun randomSeed(seed: Long) {
        if (internalRandom == null) {
            internalRandom = Random()
        }
        internalRandom!!.setSeed(seed)
    }

    var perlin_octaves = 4 // default to medium smooth

    var perlin_amp_falloff = 0.5f // 50% reduction/octave

    // [toxi 031112]
    // new vars needed due to recent change of cos table in PGraphics
    var perlin_TWOPI = 0

    var perlin_PI = 0

    lateinit var perlin_cosTable: FloatArray

    var perlin: FloatArray? = null

    // nullable type
    var perlinRandom: Random? = null

    /**
     * ( begin auto-generated from noise.xml )
     *
     * Returns the Perlin noise value at specified coordinates. Perlin noise is
     * a random sequence generator producing a more natural ordered, harmonic
     * succession of numbers compared to the standard **random()** function.
     * It was invented by Ken Perlin in the 1980s and been used since in
     * graphical applications to produce procedural textures, natural motion,
     * shapes, terrains etc.<br></br><br></br> The main difference to the
     * **random()** function is that Perlin noise is defined in an infinite
     * n-dimensional space where each pair of coordinates corresponds to a
     * fixed semi-random value (fixed only for the lifespan of the program).
     * The resulting value will always be between 0.0 and 1.0. Processing can
     * compute 1D, 2D and 3D noise, depending on the number of coordinates
     * given. The noise value can be animated by moving through the noise space
     * as demonstrated in the example above. The 2nd and 3rd dimension can also
     * be interpreted as time.<br></br><br></br>The actual noise is structured
     * similar to an audio signal, in respect to the function's use of
     * frequencies. Similar to the concept of harmonics in physics, perlin
     * noise is computed over several octaves which are added together for the
     * final result. <br></br><br></br>Another way to adjust the character of the
     * resulting sequence is the scale of the input coordinates. As the
     * function works within an infinite space the value of the coordinates
     * doesn't matter as such, only the distance between successive coordinates
     * does (eg. when using **noise()** within a loop). As a general rule
     * the smaller the difference between coordinates, the smoother the
     * resulting noise sequence will be. Steps of 0.005-0.03 work best for most
     * applications, but this will differ depending on use.
     *
     * ( end auto-generated )
     *
     * @webref math:random
     * @param x x-coordinate in noise space
     * @param y y-coordinate in noise space
     * @param z z-coordinate in noise space
     * @see PApplet.noiseSeed
     * @see PApplet.noiseDetail
     * @see PApplet.random
     */
    /**
     */
    /**
     */
    @JvmOverloads
    open fun noise(x: Float, y: Float = 0f, z: Float = 0f): Float {
        var x = x
        var y = y
        var z = z
        if (perlin == null) {
            if (perlinRandom == null) {
                perlinRandom = Random()
            }
            perlin = FloatArray(PERLIN_SIZE + 1)
            for (i in 0 until PERLIN_SIZE + 1) {
                perlin!![i] = perlinRandom!!.nextFloat() //(float)Math.random();
            }
            // [toxi 031112]
            // noise broke due to recent change of cos table in PGraphics
            // this will take care of it
            perlin_cosTable = PGraphics.cosLUT
            perlin_PI = PGraphics.SINCOS_LENGTH
            perlin_TWOPI = perlin_PI
            perlin_PI = perlin_PI shr 1
        }

        if (x < 0) x = -x
        if (y < 0) y = -y
        if (z < 0) z = -z
        var xi = x.toInt()
        var yi = y.toInt()
        var zi = z.toInt()
        var xf = x - xi
        var yf = y - yi
        var zf = z - zi
        var rxf: Float
        var ryf: Float
        var r = 0f
        var ampl = 0.5f
        var n1: Float
        var n2: Float
        var n3: Float

        for (i in 0 until perlin_octaves) {
            var of = xi + (yi shl PERLIN_YWRAPB) + (zi shl PERLIN_ZWRAPB)
            rxf = noise_fsc(xf)
            ryf = noise_fsc(yf)
            n1 = perlin!![of and PERLIN_SIZE]
            n1 += rxf * (perlin!![of + 1 and PERLIN_SIZE] - n1)
            n2 = perlin!![of + PERLIN_YWRAP and PERLIN_SIZE]
            n2 += rxf * (perlin!![of + PERLIN_YWRAP + 1 and PERLIN_SIZE] - n2)
            n1 += ryf * (n2 - n1)
            of += PERLIN_ZWRAP
            n2 = perlin!![of and PERLIN_SIZE]
            n2 += rxf * (perlin!![of + 1 and PERLIN_SIZE] - n2)
            n3 = perlin!![of + PERLIN_YWRAP and PERLIN_SIZE]
            n3 += rxf * (perlin!![of + PERLIN_YWRAP + 1 and PERLIN_SIZE] - n3)
            n2 += ryf * (n3 - n2)
            n1 += noise_fsc(zf) * (n2 - n1)
            r += n1 * ampl
            ampl *= perlin_amp_falloff
            xi = xi shl 1
            xf *= 2f
            yi = yi shl 1
            yf *= 2f
            zi = zi shl 1
            zf *= 2f
            if (xf >= 1.0f) {
                xi++
                xf--
            }
            if (yf >= 1.0f) {
                yi++
                yf--
            }
            if (zf >= 1.0f) {
                zi++
                zf--
            }
        }
        return r
    }

    // [toxi 031112]
    // now adjusts to the size of the cosLUT used via
    // the new variables, defined above
    private fun noise_fsc(i: Float): Float {
        // using bagel's cosine table instead
        return 0.5f * (1.0f - perlin_cosTable[(i * perlin_PI).toInt() % perlin_TWOPI])
    }

    // [toxi 040903]
    // make perlin noise quality user controlled to allow
    // for different levels of detail. lower values will produce
    // smoother results as higher octaves are surpressed

    /**
     * ( begin auto-generated from noiseDetail.xml )
     *
     * Adjusts the character and level of detail produced by the Perlin noise
     * function. Similar to harmonics in physics, noise is computed over
     * several octaves. Lower octaves contribute more to the output signal and
     * as such define the overal intensity of the noise, whereas higher octaves
     * create finer grained details in the noise sequence. By default, noise is
     * computed over 4 octaves with each octave contributing exactly half than
     * its predecessor, starting at 50% strength for the 1st octave. This
     * falloff amount can be changed by adding an additional function
     * parameter. Eg. a falloff factor of 0.75 means each octave will now have
     * 75% impact (25% less) of the previous lower octave. Any value between
     * 0.0 and 1.0 is valid, however note that values greater than 0.5 might
     * result in greater than 1.0 values returned by **noise()**.<br></br><br></br>By changing these parameters, the signal created by the **noise()**
     * function can be adapted to fit very specific needs and characteristics.
     *
     * ( end auto-generated )
     * @webref math:random
     * @param lod number of octaves to be used by the noise
     * @see PApplet.noise
     */
    open fun noiseDetail(lod: Int) {
        if (lod > 0) perlin_octaves = lod
    }

    /**
     * @see .noiseDetail
     * @param falloff falloff factor for each octave
     */
    open fun noiseDetail(lod: Int, falloff: Float) {
        if (lod > 0) perlin_octaves = lod
        if (falloff > 0) perlin_amp_falloff = falloff
    }

    /**
     * ( begin auto-generated from noiseSeed.xml )
     *
     * Sets the seed value for **noise()**. By default, **noise()**
     * produces different results each time the program is run. Set the
     * **value** parameter to a constant to return the same pseudo-random
     * numbers each time the software is run.
     *
     * ( end auto-generated )
     * @webref math:random
     * @param seed seed value
     * @see PApplet.noise
     * @see PApplet.noiseDetail
     * @see PApplet.random
     * @see PApplet.randomSeed
     */
    open fun noiseSeed(seed: Long) {
        if (perlinRandom == null) perlinRandom = Random()
        perlinRandom!!.setSeed(seed)
        // force table reset after changing the random number seed [0122]
        perlin = null
    }


    // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

    // todo - return @ nullable; {filename} may also nullable type
    open fun loadImage(filename: String): PImage? { //, Object params) {
//    return loadImage(filename, null);
        var stream = createInput(filename)
        if (stream == null) {
            System.err.println("Could not find the image $filename.")
            return null
        }
        //    long t = System.currentTimeMillis();
        var bitmap: Bitmap? = null
        bitmap = try {
            BitmapFactory.decodeStream(stream)
        } finally {
            try {
                stream.close()
                stream = null
            } catch (e: IOException) {
            }
        }

        //    int much = (int) (System.currentTimeMillis() - t);
       //    println("loadImage(" + filename + ") was " + nfc(much));

        return if (bitmap == null) {
            System.err.println("Could not load the image because the bitmap was empty.")
            null
        } else {
            val image = PImage(bitmap)
            image.parent = this
            image
        }
    }

    // todo - both args are fo nullable type
    open fun loadImage(filename: String, extension: String?): PImage? {
        return loadImage(filename)
    }

    // todo - both args are of nullable type and return @ nullable
    open fun requestImage(filename: String?): PImage {
        val vessel = createImage(0, 0, PConstants.ARGB)
        val ail = AsyncImageLoader(filename!!, vessel)
        ail.start()
        return vessel
    }

    /**
     * By trial and error, four image loading threads seem to work best when
     * loading images from online. This is consistent with the number of open
     * connections that web browsers will maintain. The variable is made public
     * (however no accessor has been added since it's esoteric) if you really
     * want to have control over the value used. For instance, when loading local
     * files, it might be better to only have a single thread (or two) loading
     * images so that you're disk isn't simply jumping around.
     */
    var requestImageMax = 4

    @Volatile
    var requestImageCount = 0

    // Removed 'extension' from the android version. If the extension is needed
    // later, re-copy this from the original PApplet code.
    internal inner class AsyncImageLoader(var filename: String, var vessel: PImage) : Thread() {
        override fun run() {
            while (requestImageCount == requestImageMax) {
                try {
                    sleep(10)
                } catch (e: InterruptedException) {
                }
            }
            requestImageCount++
            val actual = loadImage(filename)

            // An error message should have already printed
            if (actual == null) {
                vessel.width = -1
                vessel.height = -1
            } else {
                vessel.width = actual.width
                vessel.height = actual.height
                vessel.format = actual.format
                vessel.pixels = actual.pixels
                // an android, pixels[] will probably be null, we want this one
                vessel.bitmap = actual.bitmap
                vessel.pixelWidth = actual.width
                vessel.pixelHeight = actual.height
                vessel.pixelDensity = 1
            }
            requestImageCount--
        }

    }


    //////////////////////////////////////////////////////////////

    // DATA I/O

    // todo - return @ nullable and {name} is nullable
    open fun createXML(name: String?): XML? {
        return try {
            XML(name)
        } catch (e: Exception) {
            printStackTrace(e)
            null
        }
    }

    // version that uses 'options' though there are currently no supported options

    /**
     * @webref input:files
     * @param filename name of a file in the data folder or a URL.
     * @see XML.parse
     * @see PApplet.loadBytes
     * @see PApplet.loadStrings
     * @see PApplet.loadTable
     */
    // todo - return @ nullable and {filename} is of nullable type
    @JvmOverloads
    open fun loadXML(filename: String?, options: String? = null): XML? {
        return try {
            XML(createInput(filename), options)
        } catch (e: Exception) {
            printStackTrace(e)
            null
        }
    }

    // todo nullable type both args and return type
    @JvmOverloads
    open fun parseXML(xmlString: String?, options: String? = null): XML? {
        return try {
            parse(xmlString, options)
        } catch (e: Exception) {
            printStackTrace(e)
            null
        }
    }

    // todo both args @ nullable-type
    @JvmOverloads
    open fun saveXML(xml: XML, filename: String?, options: String? = null): Boolean {
        return xml.save(saveFile(filename), options)
    }

    /**
     * @webref input:files
     * @param input String to parse as a JSONObject
     * @see PApplet.loadJSONObject
     * @see PApplet.saveJSONObject
     */
    // todo - nullable-type @ args and return type
    open fun parseJSONObject(input: String?): JSONObject {
        return JSONObject(StringReader(input))
    }

    /**
     * @webref input:files
     * @param filename name of a file in the data folder or a URL
     * @see JSONObject
     *
     * @see JSONArray
     *
     * @see PApplet.loadJSONArray
     * @see PApplet.saveJSONObject
     * @see PApplet.saveJSONArray
     */
    // todo - nullable-type both args and return-type
    open fun loadJSONObject(filename: String?): JSONObject {
        // can't pass of createReader() to the constructor b/c of resource leak
        val reader = createReader(filename)
        val outgoing = JSONObject(reader)
        try {
            reader!!.close()
        } catch (e: IOException) {  // not sure what would cause this
            e.printStackTrace()
        }
        return outgoing
    }


    /**
     * @nowebref
     */
    /**
     * @webref output:files
     * @see JSONObject
     *
     * @see JSONArray
     *
     * @see PApplet.loadJSONObject
     * @see PApplet.loadJSONArray
     * @see PApplet.saveJSONArray
     */
    // todo - nullable-type args and return-type
    @JvmOverloads
    open fun saveJSONObject(json: JSONObject, filename: String?, options: String? = null): Boolean {
        return json.save(saveFile(filename), options)
    }

    /**
     * @webref input:files
     * @param input String to parse as a JSONArray
     * @see JSONObject
     *
     * @see PApplet.loadJSONObject
     * @see PApplet.saveJSONObject
     */
    // todo - nullable-type args and return-type
    open fun parseJSONArray(input: String?): JSONArray {
        return JSONArray(StringReader(input))
    }

    /**
     * @webref input:files
     * @param filename name of a file in the data folder or a URL
     * @see JSONArray
     *
     * @see PApplet.loadJSONObject
     * @see PApplet.saveJSONObject
     * @see PApplet.saveJSONArray
     */
    // todo - nullable-type args and return-type
    open fun loadJSONArray(filename: String?): JSONArray {
        // can't pass of createReader() to the constructor b/c of resource leak
        val reader = createReader(filename)
        val outgoing = JSONArray(reader)
        try {
            reader!!.close()
        } catch (e: IOException) {  // not sure what would cause this
            e.printStackTrace()
        }
        return outgoing
    }


    // todo - nullable-type args and
    /**
     * @webref output:files
     * @see JSONObject
     *
     * @see JSONArray
     *
     * @see PApplet.loadJSONObject
     * @see PApplet.loadJSONArray
     * @see PApplet.saveJSONObject
     */
    // todo - nullable-type args
    @JvmOverloads
    open fun saveJSONArray(json: JSONArray, filename: String?, options: String? = null): Boolean {
        return json.save(saveFile(filename), options)
    }

    open fun createTable(): Table {
        return Table()
    }


    // todo - nullable-type args and return-type
    /**
     * @webref input:files
     * @param filename name of a file in the data folder or a URL.
     * @see PApplet.loadBytes
     * @see PApplet.loadStrings
     * @see PApplet.loadXML
     */
    // todo - nullable-type args and return-type
    @JvmOverloads
    open fun loadTable(filename: String, options: String? = null): Table {
        var options = options
        val ext = checkExtension(filename)
        if (ext != null) {
            if (ext == "csv" || ext == "tsv") {
                options = if (options == null) {
                    ext
                } else {
                    "$ext,$options"
                }
            }
        }
        return Table(createInput(filename), options)
    }

    // todo - nullable-type args
    @JvmOverloads
    open fun saveTable(table: Table, filename: String?, options: String? = null): Boolean {
        try {
            table.save(saveFile(filename), options)
            return true
        } catch (e: IOException) {
            printStackTrace(e)
        }
        return false
    }

    // FONT I/O

    // todo - nullable-type args and return-type
    open fun loadFont(filename: String): PFont? {
        try {
            val input = createInput(filename)
            return PFont(input)
        } catch (e: Exception) {
            die("Could not load font " + filename + ". " +
                    "Make sure that the font has been copied " +
                    "to the data folder of your sketch.", e)
        }
        return null
    }

    /**
     * Used by PGraphics to remove the requirement for loading a font!
     */
    // todo - nullable-type return-type
    open fun createDefaultFont(size: Float): PFont {
        return createFont("SansSerif", size, true, null)
    }

    /**
     * Create a bitmap font on the fly from either a font name that's
     * installed on the system, or from a .ttf or .otf that's inside
     * the data folder of this sketch.
     * <P></P>
     * Use 'null' for the charset if you want to dynamically create
     * character bitmaps only as they're needed.
     */
    // todo - nullable-type args and return-type
    @JvmOverloads
    open fun createFont(name: String, size: Float,
                   smooth: Boolean = true, charset: CharArray? = null): PFont {
        val lowerName = name.toLowerCase()
        var baseFont: Typeface? = null
        baseFont = if (lowerName.endsWith(".otf") || lowerName.endsWith(".ttf")) {
            val assets = surface!!.getAssets()
            Typeface.createFromAsset(assets, name)
        } else {
            findNative(name) as Typeface?
        }
        return PFont(baseFont, round(size), smooth, charset)
    }


    //////////////////////////////////////////////////////////////

    // FILE/FOLDER SELECTION

    // Doesn't appear to be implemented by Android, but this article might help:
    // http://linuxdevices.com/articles/AT6247038002.html
    //  public File selectedFile;
    //  protected Frame parentFrame;
    //
    //
    //  protected void checkParentFrame() {
    //    if (parentFrame == null) {
    //      Component comp = getParent();
    //      while (comp != null) {
    //        if (comp instanceof Frame) {
    //          parentFrame = (Frame) comp;
    //          break;
    //        }
    //        comp = comp.getParent();
    //      }
    //      // Who you callin' a hack?
    //      if (parentFrame == null) {
    //        parentFrame = new Frame();
    //      }
    //    }
    //  }
    //
    //
    //  /**
    //   * Open a platform-specific file chooser dialog to select a file for input.
    //   * @return full path to the selected file, or null if no selection.
    //   */
    //  public String selectInput() {
    //    return selectInput("Select a file...");
    //  }
    //
    //
    //  /**
    //   * Open a platform-specific file chooser dialog to select a file for input.
    //   * @param prompt Mesage to show the user when prompting for a file.
    //   * @return full path to the selected file, or null if canceled.
    //   */
    //  public String selectInput(String prompt) {
    //    return selectFileImpl(prompt, FileDialog.LOAD);
    //  }
    //
    //
    //  /**
    //   * Open a platform-specific file save dialog to select a file for output.
    //   * @return full path to the file entered, or null if canceled.
    //   */
    //  public String selectOutput() {
    //    return selectOutput("Save as...");
    //  }
    //
    //
    //  /**
    //   * Open a platform-specific file save dialog to select a file for output.
    //   * @param prompt Mesage to show the user when prompting for a file.
    //   * @return full path to the file entered, or null if canceled.
    //   */
    //  public String selectOutput(String prompt) {
    //    return selectFileImpl(prompt, FileDialog.SAVE);
    //  }
    //
    //
    //  protected String selectFileImpl(final String prompt, final int mode) {
    //    checkParentFrame();
    //
    //    try {
    //      SwingUtilities.invokeAndWait(new Runnable() {
    //        public void run() {
    //          FileDialog fileDialog =
    //            new FileDialog(parentFrame, prompt, mode);
    //          fileDialog.setVisible(true);
    //          String directory = fileDialog.getDirectory();
    //          String filename = fileDialog.getFile();
    //          selectedFile =
    //            (filename == null) ? null : new File(directory, filename);
    //        }
    //      });
    //      return (selectedFile == null) ? null : selectedFile.getAbsolutePath();
    //
    //    } catch (Exception e) {
    //      e.printStackTrace();
    //      return null;
    //    }
    //  }
    //
    //
    //  /**
    //   * Open a platform-specific folder chooser dialog.
    //   * @return full path to the selected folder, or null if no selection.
    //   */
    //  public String selectFolder() {
    //    return selectFolder("Select a folder...");
    //  }
    //
    //
    //  /**
    //   * Open a platform-specific folder chooser dialog.
    //   * @param prompt Mesage to show the user when prompting for a file.
    //   * @return full path to the selected folder, or null if no selection.
    //   */
    //  public String selectFolder(final String prompt) {
    //    checkParentFrame();
    //
    //    try {
    //      SwingUtilities.invokeAndWait(new Runnable() {
    //        public void run() {
    //          if (platform == MACOSX) {
    //            FileDialog fileDialog =
    //              new FileDialog(parentFrame, prompt, FileDialog.LOAD);
    //            System.setProperty("apple.awt.fileDialogForDirectories", "true");
    //            fileDialog.setVisible(true);
    //            System.setProperty("apple.awt.fileDialogForDirectories", "false");
    //            String filename = fileDialog.getFile();
    //            selectedFile = (filename == null) ? null :
    //              new File(fileDialog.getDirectory(), fileDialog.getFile());
    //          } else {
    //            JFileChooser fileChooser = new JFileChooser();
    //            fileChooser.setDialogTitle(prompt);
    //            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    //
    //            int returned = fileChooser.showOpenDialog(parentFrame);
    //            System.out.println(returned);
    //            if (returned == JFileChooser.CANCEL_OPTION) {
    //              selectedFile = null;
    //            } else {
    //              selectedFile = fileChooser.getSelectedFile();
    //            }
    //          }
    //        }
    //      });
    //      return (selectedFile == null) ? null : selectedFile.getAbsolutePath();
    //
    //    } catch (Exception e) {
    //      e.printStackTrace();
    //      return null;
    //    }
    //  }


    //////////////////////////////////////////////////////////////

    // LISTING DIRECTORIES

    open fun listPaths(path: String, vararg options: String): Array<String?> {
        var path = path
        val list = listFiles(path, *options)
        var offset = 0
        for (opt in options) {
            if (opt == "relative") {
                if (!path.endsWith(File.pathSeparator)) {
                    path += File.pathSeparator
                }
                offset = path.length
                break
            }
        }
        val outgoing = arrayOfNulls<String>(list!!.size)
        for (i in list.indices) {
            // as of Java 1.8, substring(0) returns the original object
            outgoing[i] = list[i].absolutePath.substring(offset)
        }
        return outgoing
    }

    open fun listFiles(path: String?, vararg options: String?): Array<File>? {
        var file = File(path)
        // if not an absolute path, make it relative to the sketch folder
        if (!file.isAbsolute) {
            file = sketchFile(path)
        }
        return Companion.listFiles(file, *options as Array<out String>)
    }


    //////////////////////////////////////////////////////////////

    // READERS AND WRITERS

    /**
     * I want to read lines from a file. I have RSI from typing these
     * eight lines of code so many times.
     */
    // todo - nullable-type args and return-type
    open fun createReader(filename: String?): BufferedReader? {
        try {
            val `is` = createInput(filename)
            if (`is` == null) {
                System.err.println("$filename does not exist or could not be read")
                return null
            }
            return createReader(`is`)
        } catch (e: Exception) {
            if (filename == null) {
                System.err.println("Filename passed to reader() was null")
            } else {
                System.err.println("Couldn't create a reader for $filename")
            }
        }
        return null
    }

    /**
     * I want to print lines to a file. Why can't I?
     */
    // todo - nullable-type args and return-type
    open fun createWriter(filename: String?): PrintWriter {
        return createWriter(saveFile(filename))
    }


    //////////////////////////////////////////////////////////////

    // FILE INPUT

    /**
     * Simplified method to open a Java InputStream.
     * <P>
     * This method is useful if you want to use the facilities provided
     * by PApplet to easily open things from the data folder or from a URL,
     * but want an InputStream object so that you can use other Java
     * methods to take more control of how the stream is read.
    </P> * <P>
     * If the requested item doesn't exist, null is returned.
     * (Prior to 0096, die() would be called, killing the applet)
    </P> * <P>
     * For 0096+, the "data" folder is exported intact with subfolders,
     * and openStream() properly handles subdirectories from the data folder
    </P> * <P>
     * If not online, this will also check to see if the user is asking
     * for a file whose name isn't properly capitalized. This helps prevent
     * issues when a sketch is exported to the web, where case sensitivity
     * matters, as opposed to Windows and the Mac OS default where
     * case sensitivity is preserved but ignored.
    </P> * <P>
     * It is strongly recommended that libraries use this method to open
     * data files, so that the loading sequence is handled in the same way
     * as functions like loadBytes(), loadImage(), etc.
    </P> * <P>
     * The filename passed in can be:
    </P> * <UL>
     * <LI>A URL, for instance openStream("http://processing.org/");
    </LI> * <LI>A file in the sketch's data folder
    </LI> * <LI>Another file to be opened locally (when running as an application)
    </LI></UL> *
     */
    // todo return-type @ nullable; {filename} may also be nullable
    open fun createInput(filename: String?): InputStream? {
        val input = createInputRaw(filename)
        val lower = filename!!.toLowerCase()
        return if (input != null &&
                (lower.endsWith(".gz") || lower.endsWith(".svgz"))) {
            try {
                // buffered has to go *around* the GZ, otherwise 25x slower
                BufferedInputStream(GZIPInputStream(input))
            } catch (e: IOException) {
                printStackTrace(e)
                null
            }
        } else BufferedInputStream(input)
    }

    /**
     * Call createInput() without automatic gzip decompression.
     */
    // todo - nullable-type args and return-type
    open fun createInputRaw(filename: String?): InputStream? {
        // Additional considerations for Android version:
        // http://developer.android.com/guide/topics/resources/resources-i18n.html
        var stream: InputStream? = null
        if (filename == null) return null
        if (filename.length == 0) {
            // an error will be called by the parent function
            //System.err.println("The filename passed to openStream() was empty.");
            return null
        }

        // safe to check for this as a url first. this will prevent online
        // access logs from being spammed with GET /sketchfolder/http://blahblah
        if (filename.indexOf(":") != -1) {  // at least smells like URL
            try {
                // Workaround for Android bug 6066
                // http://code.google.com/p/android/issues/detail?id=6066
                // http://code.google.com/p/processing/issues/detail?id=629
//      URL url = new URL(filename);
//      stream = url.openStream();
//      return stream;
                val url = URL(filename)
                val con = url.openConnection() as HttpURLConnection
                con.requestMethod = "GET"
                con.doInput = true
                con.connect()
                return con.inputStream
                //The following code is deprecaded by Android
//        HttpGet httpRequest = null;
//        httpRequest = new HttpGet(URI.create(filename));
//        HttpClient httpclient = new DefaultHttpClient();
//        HttpResponse response = (HttpResponse) httpclient.execute(httpRequest);
//        HttpEntity entity = response.getEntity();
//        return entity.getContent();
                // can't use BufferedHttpEntity because it may try to allocate a byte
                // buffer of the size of the download, bad when DL is 25 MB... [0200]
//        BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
//        return bufHttpEntity.getContent();
            } catch (mfue: MalformedURLException) {
                // not a url, that's fine
            } catch (fnfe: FileNotFoundException) {
                // Java 1.5 likes to throw this when URL not available. (fix for 0119)
                // http://dev.processing.org/bugs/show_bug.cgi?id=403
            } catch (e: IOException) {
                // changed for 0117, shouldn't be throwing exception
                printStackTrace(e)
                //System.err.println("Error downloading from URL " + filename);
                return null
                //throw new RuntimeException("Error downloading from URL " + filename);
            }
        }

        /*
    // Moved this earlier than the getResourceAsStream() checks, because
    // calling getResourceAsStream() on a directory lists its contents.
    // http://dev.processing.org/bugs/show_bug.cgi?id=716
    try {
      // First see if it's in a data folder. This may fail by throwing
      // a SecurityException. If so, this whole block will be skipped.
      File file = new File(dataPath(filename));
      if (!file.exists()) {
        // next see if it's just in the sketch folder
        file = new File(sketchPath, filename);
      }
      if (file.isDirectory()) {
        return null;
      }
      if (file.exists()) {
        try {
          // handle case sensitivity check
          String filePath = file.getCanonicalPath();
          String filenameActual = new File(filePath).getName();
          // make sure there isn't a subfolder prepended to the name
          String filenameShort = new File(filename).getName();
          // if the actual filename is the same, but capitalized
          // differently, warn the user.
          //if (filenameActual.equalsIgnoreCase(filenameShort) &&
          //!filenameActual.equals(filenameShort)) {
          if (!filenameActual.equals(filenameShort)) {
            throw new RuntimeException("This file is named " +
                                       filenameActual + " not " +
                                       filename + ". Rename the file " +
            "or change your code.");
          }
        } catch (IOException e) { }
      }

      // if this file is ok, may as well just load it
      stream = new FileInputStream(file);
      if (stream != null) return stream;

      // have to break these out because a general Exception might
      // catch the RuntimeException being thrown above
    } catch (IOException ioe) {
    } catch (SecurityException se) { }
     */

        // Using getClassLoader() prevents Java from converting dots
        // to slashes or requiring a slash at the beginning.
        // (a slash as a prefix means that it'll load from the root of
        // the jar, rather than trying to dig into the package location)

        /*
    // this works, but requires files to be stored in the src folder
    ClassLoader cl = getClass().getClassLoader();
    stream = cl.getResourceAsStream(filename);
    if (stream != null) {
      String cn = stream.getClass().getName();
      // this is an irritation of sun's java plug-in, which will return
      // a non-null stream for an object that doesn't exist. like all good
      // things, this is probably introduced in java 1.5. awesome!
      // http://dev.processing.org/bugs/show_bug.cgi?id=359
      if (!cn.equals("sun.plugin.cache.EmptyInputStream")) {
        return stream;
      }
    }
     */

        // Try the assets folder
        val assets = surface!!.getAssets()
        try {
            stream = assets!!.open(filename)
            if (stream != null) {
                return stream
            }
        } catch (e: IOException) {
            // ignore this and move on
            //e.printStackTrace();
        }

        // Maybe this is an absolute path, didja ever think of that?
        val absFile = File(filename)
        if (absFile.exists()) {
            try {
                stream = FileInputStream(absFile)
                if (stream != null) {
                    return stream
                }
            } catch (fnfe: FileNotFoundException) {
                //fnfe.printStackTrace();
            }
        }

        // Maybe this is a file that was written by the sketch on another occasion.
        val sketchFile = File(sketchPath(filename))
        if (sketchFile.exists()) {
            try {
                stream = FileInputStream(sketchFile)
                if (stream != null) {
                    return stream
                }
            } catch (fnfe: FileNotFoundException) {
                //fnfe.printStackTrace();
            }
        }

        // Attempt to load the file more directly. Doesn't like paths.
//    try {
//      // MODE_PRIVATE is default, should we use something else?
//      stream = surface.openFileInput(filename);
//      if (stream != null) {
//        return stream;
//      }
//    } catch (FileNotFoundException e) {
//      // ignore this and move on
//      //e.printStackTrace();
//    }
        return surface!!.openFileInput(filename)
    }

    // todo - nullable-type args and return-type
    open fun loadBytes(filename: String): ByteArray? {
        val `is` = createInput(filename)
        if (`is` != null) return loadBytes(`is`)
        System.err.println("The file \"" + filename + "\" " +
                "is missing or inaccessible, make sure " +
                "the URL is valid or that the file has been " +
                "added to your sketch and is readable.")
        return null
    }

    /**
     * Load data from a file and shove it into a String array.
     * <P>
     * Exceptions are handled internally, when an error, occurs, an
     * exception is printed to the console and 'null' is returned,
     * but the program continues running. This is a tradeoff between
     * 1) showing the user that there was a problem but 2) not requiring
     * that all i/o code is contained in try/catch blocks, for the sake
     * of new users (or people who are just trying to get things done
     * in a "scripting" fashion. If you want to handle exceptions,
     * use Java methods for I/O.
    </P> */
    // todo - nullable-type args and return-type
    open fun loadStrings(filename: String): Array<String?>? {
        val `is` = createInput(filename)
        if (`is` != null) return loadStrings(`is`)
        System.err.println("The file \"" + filename + "\" " +
                "is missing or inaccessible, make sure " +
                "the URL is valid or that the file has been " +
                "added to your sketch and is readable.")
        return null
    }


    //////////////////////////////////////////////////////////////

    // FILE OUTPUT

    /**
     * Similar to createInput() (formerly openStream), this creates a Java
     * OutputStream for a given filename or path. The file will be created in
     * the sketch folder, or in the same folder as an exported application.
     *
     *
     * If the path does not exist, intermediate folders will be created. If an
     * exception occurs, it will be printed to the console, and null will be
     * returned.
     *
     *
     * Future releases may also add support for handling HTTP POST via this
     * method (for better symmetry with createInput), however that's maybe a
     * little too clever (and then we'd have to add the same features to the
     * other file functions like createWriter). Who you callin' bloated?
     */
    // todo - nullable-type args and return-type
    open fun createOutput(filename: String?): OutputStream? {
        try {
            // in spite of appearing to be the 'correct' option, this doesn't allow
            // for paths, so no subfolders, none of that savePath() goodness.
//      Context context = getApplicationContext();
//      // MODE_PRIVATE is default, should we use that instead?
//      return context.openFileOutput(filename, MODE_WORLD_READABLE);
            var file = File(filename)
            if (!file.isAbsolute) {
                file = File(sketchPath(filename))
            }
            val fos = FileOutputStream(file)
            return if (file.name.toLowerCase().endsWith(".gz")) {
                GZIPOutputStream(fos)
            } else fos
        } catch (e: IOException) {
            printStackTrace(e)
        }
        return null
    }

    /**
     * Save the contents of a stream to a file in the sketch folder.
     * This is basically saveBytes(blah, loadBytes()), but done
     * more efficiently (and with less confusing syntax).
     */
    open fun saveStream(targetFilename: String?, sourceLocation: String?): Boolean {
        return saveStream(saveFile(targetFilename), sourceLocation)
    }

    /**
     * Identical to the other saveStream(), but writes to a File
     * object, for greater control over the file location.
     * Note that unlike other api methods, this will not automatically
     * compress or uncompress gzip files.
     */
    // todo - nullable-type args
    open fun saveStream(targetFile: File, sourceLocation: String?): Boolean {
        return saveStream(targetFile, createInputRaw(sourceLocation))
    }

    // todo - nullable-type args
    open fun saveStream(targetFilename: String?, sourceStream: InputStream?): Boolean {
        return saveStream(saveFile(targetFilename), sourceStream)
    }

    /**
     * Saves bytes to a file to inside the sketch folder.
     * The filename can be a relative path, i.e. "poo/bytefun.txt"
     * would save to a file named "bytefun.txt" to a subfolder
     * called 'poo' inside the sketch folder. If the in-between
     * subfolders don't exist, they'll be created.
     */
    open fun saveBytes(filename: String?, data: ByteArray?) {
        saveBytes(saveFile(filename), data)
    }

    // todo - nullable-type args
    open fun saveStrings(filename: String?, strings: Array<String?>) {
        saveStrings(saveFile(filename), strings)
    }


    //////////////////////////////////////////////////////////////

    /**
     * Prepend the sketch folder path to the filename (or path) that is
     * passed in. External libraries should use this function to save to
     * the sketch folder.
     *
     *
     * Note that when running as an applet inside a web browser,
     * the sketchPath will be set to null, because security restrictions
     * prevent applets from accessing that information.
     *
     *
     * This will also cause an error if the sketch is not inited properly,
     * meaning that init() was never called on the PApplet when hosted
     * my some other main() or by other code. For proper use of init(),
     * see the examples in the main description text for PApplet.
     */
    // todo - nullable-type args and return-type
    open fun sketchPath(where: String?): String? {
        if (sketchPath == null) {
            return where
            //      throw new RuntimeException("The applet was not inited properly, " +
//                                 "or security restrictions prevented " +
//                                 "it from determining its path.");
        }

        // isAbsolute() could throw an access exception, but so will writing
        // to the local disk using the sketch path, so this is safe here.
        // for 0120, added a try/catch anyways.
        try {
            if (File(where).isAbsolute) return where
        } catch (e: Exception) {
        }
        return surface!!.getFileStreamPath(where)!!.absolutePath
    }

    // todo - nullable-type args and return-type
    open fun sketchFile(where: String?): File {
        return File(sketchPath(where))
    }

    /**
     * Returns a path inside the applet folder to save to. Like sketchPath(),
     * but creates any in-between folders so that things save properly.
     *
     *
     * All saveXxxx() functions use the path to the sketch folder, rather than
     * its data folder. Once exported, the data folder will be found inside the
     * jar file of the exported application or applet. In this case, it's not
     * possible to save data into the jar file, because it will often be running
     * from a server, or marked in-use if running from a local file system.
     * With this in mind, saving to the data path doesn't make sense anyway.
     * If you know you're running locally, and want to save to the data folder,
     * use <TT>saveXxxx("data/blah.dat")</TT>.
     */
    // todo - nullable return type but expected is non-nullable type or has to change the PImage.save() return type nullable heirarchy to figure this out
    open fun savePath(where: String?): String? {
        if (where == null) return null
        //    System.out.println("filename before sketchpath is " + where);
        val filename = sketchPath(where)
        //    System.out.println("filename after sketchpath is " + filename);
        createPath(filename)
        return filename
    }

    /**
     * Identical to savePath(), but returns a File object.
     */
    // todo nullable type both args and return type
    open fun saveFile(where: String?): File {
        return File(savePath(where))
    }

    /**
     * Return a full path to an item in the data folder.
     *
     *
     * The behavior of this function differs from the equivalent on the Java mode: files stored in
     * the data folder of the sketch get packed as assets in the apk, and the path to the data folder
     * is no longer valid. Only the name is needed to open them. However, if the file is not an asset,
     * we can assume it has been created by the sketch, so it should have the sketch path.
     * Discussed here:
     * https://github.com/processing/processing-android/issues/450
     */
    // todo - nullable-type args and return-type
    open fun dataPath(where: String?): String? {
        // First, we check if it is asset:
        var isAsset = false
        val assets = surface!!.getAssets()
        var `is`: InputStream? = null
        try {
            `is` = assets!!.open(where)
            isAsset = true
        } catch (ex: IOException) {
            //file does not exist
        } finally {
            try {
                `is`!!.close()
            } catch (ex: Exception) {
            }
        }
        return if (isAsset) where else sketchPath(where)
        // Not an asset, let's just use sketch path:
    }

    /**
     * Return a full path to an item in the data folder as a File object.
     * See the dataPath() method for more information.
     */
    // todo - nullable-type args and return-type
    open fun dataFile(where: String?): File {
        return File(dataPath(where))
    }


    //////////////////////////////////////////////////////////////

    // COLOR FUNCTIONS

    // moved here so that they can work without
    // the graphics actually being instantiated (outside setup)
    open fun color(gray: Int): Int {
        var gray = gray
        if (graphics == null) {
            if (gray > 255) gray = 255 else if (gray < 0) gray = 0
            return -0x1000000 or (gray shl 16) or (gray shl 8) or gray
        }
        return graphics!!.color(gray)
    }

    open fun color(fgray: Float): Int {
        if (graphics == null) {
            var gray = fgray.toInt()
            if (gray > 255) gray = 255 else if (gray < 0) gray = 0
            return -0x1000000 or (gray shl 16) or (gray shl 8) or gray
        }
        return graphics!!.color(fgray)
    }

    /**
     * As of 0116 this also takes color(#FF8800, alpha)
     */
    open fun color(gray: Int, alpha: Int): Int {
        var alpha = alpha
        if (graphics == null) {
            if (alpha > 255) alpha = 255 else if (alpha < 0) alpha = 0
            return if (gray > 255) {
                // then assume this is actually a #FF8800
                alpha shl 24 or (gray and 0xFFFFFF)
            } else {
                //if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
                alpha shl 24 or (gray shl 16) or (gray shl 8) or gray
            }
        }
        return graphics!!.color(gray, alpha)
    }

    open fun color(fgray: Float, falpha: Float): Int {
        if (graphics == null) {
            var gray = fgray.toInt()
            var alpha = falpha.toInt()
            if (gray > 255) gray = 255 else if (gray < 0) gray = 0
            if (alpha > 255) alpha = 255 else if (alpha < 0) alpha = 0
            return -0x1000000 or (gray shl 16) or (gray shl 8) or gray
        }
        return graphics!!.color(fgray, falpha)
    }

    open fun color(x: Int, y: Int, z: Int): Int {
        var x = x
        var y = y
        var z = z
        if (graphics == null) {
            if (x > 255) x = 255 else if (x < 0) x = 0
            if (y > 255) y = 255 else if (y < 0) y = 0
            if (z > 255) z = 255 else if (z < 0) z = 0
            return -0x1000000 or (x shl 16) or (y shl 8) or z
        }
        return graphics!!.color(x, y, z)
    }

    open fun color(x: Float, y: Float, z: Float): Int {
        var x = x
        var y = y
        var z = z
        if (graphics == null) {
            if (x > 255) x = 255f else if (x < 0) x = 0f
            if (y > 255) y = 255f else if (y < 0) y = 0f
            if (z > 255) z = 255f else if (z < 0) z = 0f
            return -0x1000000 or (x.toInt() shl 16) or (y.toInt() shl 8) or z.toInt()
        }
        return graphics!!.color(x, y, z)
    }

    open fun color(x: Int, y: Int, z: Int, a: Int): Int {
        var x = x
        var y = y
        var z = z
        var a = a
        if (graphics == null) {
            if (a > 255) a = 255 else if (a < 0) a = 0
            if (x > 255) x = 255 else if (x < 0) x = 0
            if (y > 255) y = 255 else if (y < 0) y = 0
            if (z > 255) z = 255 else if (z < 0) z = 0
            return a shl 24 or (x shl 16) or (y shl 8) or z
        }
        return graphics!!.color(x, y, z, a)
    }

    open fun color(x: Float, y: Float, z: Float, a: Float): Int {
        var x = x
        var y = y
        var z = z
        var a = a
        if (graphics == null) {
            if (a > 255) a = 255f else if (a < 0) a = 0f
            if (x > 255) x = 255f else if (x < 0) x = 0f
            if (y > 255) y = 255f else if (y < 0) y = 0f
            if (z > 255) z = 255f else if (z < 0) z = 0f
            return a.toInt() shl 24 or (x.toInt() shl 16) or (y.toInt() shl 8) or z.toInt()
        }
        return graphics!!.color(x, y, z, a)
    }


    //  static public void main(String args[]) {
    //    // Disable abyssmally slow Sun renderer on OS X 10.5.
    //    if (platform == MACOSX) {
    //      // Only run this on OS X otherwise it can cause a permissions error.
    //      // http://dev.processing.org/bugs/show_bug.cgi?id=976
    //      System.setProperty("apple.awt.graphics.UseQuartz", "true");
    //    }
    //
    //    // This doesn't do anything.
    ////    if (platform == WINDOWS) {
    ////      // For now, disable the D3D renderer on Java 6u10 because
    ////      // it causes problems with Present mode.
    ////      // http://dev.processing.org/bugs/show_bug.cgi?id=1009
    ////      System.setProperty("sun.java2d.d3d", "false");
    ////    }
    //
    //    if (args.length < 1) {
    //      System.err.println("Usage: PApplet <appletname>");
    //      System.err.println("For additional options, " +
    //                         "see the Javadoc for PApplet");
    //      System.exit(1);
    //    }
    //
    //    try {
    //      boolean external = false;
    //      int[] location = null;
    //      int[] editorLocation = null;
    //
    //      String name = null;
    //      boolean present = false;
    //      boolean exclusive = false;
    //      Color backgroundColor = Color.BLACK;
    //      Color stopColor = Color.GRAY;
    //      GraphicsDevice displayDevice = null;
    //      boolean hideStop = false;
    //
    //      String param = null, value = null;
    //
    //      // try to get the user folder. if running under java web start,
    //      // this may cause a security exception if the code is not signed.
    //      // http://processing.org/discourse/yabb_beta/YaBB.cgi?board=Integrate;action=display;num=1159386274
    //      String folder = null;
    //      try {
    //        folder = System.getProperty("user.dir");
    //      } catch (Exception e) { }
    //
    //      int argIndex = 0;
    //      while (argIndex < args.length) {
    //        int equals = args[argIndex].indexOf('=');
    //        if (equals != -1) {
    //          param = args[argIndex].substring(0, equals);
    //          value = args[argIndex].substring(equals + 1);
    //
    //          if (param.equals(ARGS_EDITOR_LOCATION)) {
    //            external = true;
    //            editorLocation = parseInt(split(value, ','));
    //
    //          } else if (param.equals(ARGS_DISPLAY)) {
    //            int deviceIndex = Integer.parseInt(value) - 1;
    //
    //            //DisplayMode dm = device.getDisplayMode();
    //            //if ((dm.getWidth() == 1024) && (dm.getHeight() == 768)) {
    //
    //            GraphicsEnvironment environment =
    //              GraphicsEnvironment.getLocalGraphicsEnvironment();
    //            GraphicsDevice devices[] = environment.getScreenDevices();
    //            if ((deviceIndex >= 0) && (deviceIndex < devices.length)) {
    //              displayDevice = devices[deviceIndex];
    //            } else {
    //              System.err.println("Display " + value + " does not exist, " +
    //                                 "using the default display instead.");
    //            }
    //
    //          } else if (param.equals(ARGS_BGCOLOR)) {
    //            if (value.charAt(0) == '#') value = value.substring(1);
    //            backgroundColor = new Color(Integer.parseInt(value, 16));
    //
    //          } else if (param.equals(ARGS_STOP_COLOR)) {
    //            if (value.charAt(0) == '#') value = value.substring(1);
    //            stopColor = new Color(Integer.parseInt(value, 16));
    //
    //          } else if (param.equals(ARGS_SKETCH_FOLDER)) {
    //            folder = value;
    //
    //          } else if (param.equals(ARGS_LOCATION)) {
    //            location = parseInt(split(value, ','));
    //          }
    //
    //        } else {
    //          if (args[argIndex].equals(ARGS_PRESENT)) {
    //            present = true;
    //
    //          } else if (args[argIndex].equals(ARGS_EXCLUSIVE)) {
    //            exclusive = true;
    //
    //          } else if (args[argIndex].equals(ARGS_HIDE_STOP)) {
    //            hideStop = true;
    //
    //          } else if (args[argIndex].equals(ARGS_EXTERNAL)) {
    //            external = true;
    //
    //          } else {
    //            name = args[argIndex];
    //            break;
    //          }
    //        }
    //        argIndex++;
    //      }
    //
    //      // Set this property before getting into any GUI init code
    //      //System.setProperty("com.apple.mrj.application.apple.menu.about.name", name);
    //      // This )*)(*@#$ Apple crap don't work no matter where you put it
    //      // (static method of the class, at the top of main, wherever)
    //
    //      if (displayDevice == null) {
    //        GraphicsEnvironment environment =
    //          GraphicsEnvironment.getLocalGraphicsEnvironment();
    //        displayDevice = environment.getDefaultScreenDevice();
    //      }
    //
    //      Frame frame = new Frame(displayDevice.getDefaultConfiguration());
    //      /*
    //      Frame frame = null;
    //      if (displayDevice != null) {
    //        frame = new Frame(displayDevice.getDefaultConfiguration());
    //      } else {
    //        frame = new Frame();
    //      }
    //      */
    //      //Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
    //
    //      // remove the grow box by default
    //      // users who want it back can call frame.setResizable(true)
    //      frame.setResizable(false);
    //
    //      // Set the trimmings around the image
    //      Image image = Toolkit.getDefaultToolkit().createImage(ICON_IMAGE);
    //      frame.setIconImage(image);
    //      frame.setTitle(name);
    //
    ////    Class c = Class.forName(name);
    //      Class<?> c = Thread.currentThread().getContextClassLoader().loadClass(name);
    //      final PApplet applet = (PApplet) c.newInstance();
    //
    //      // these are needed before init/start
    //      applet.frame = frame;
    //      applet.sketchPath = folder;
    //      applet.args = PApplet.subset(args, 1);
    //      applet.external = external;
    //
    //      // Need to save the window bounds at full screen,
    //      // because pack() will cause the bounds to go to zero.
    //      // http://dev.processing.org/bugs/show_bug.cgi?id=923
    //      Rectangle fullScreenRect = null;
    //
    //      // For 0149, moving this code (up to the pack() method) before init().
    //      // For OpenGL (and perhaps other renderers in the future), a peer is
    //      // needed before a GLDrawable can be created. So pack() needs to be
    //      // called on the Frame before applet.init(), which itself calls size(),
    //      // and launches the Thread that will kick off setup().
    //      // http://dev.processing.org/bugs/show_bug.cgi?id=891
    //      // http://dev.processing.org/bugs/show_bug.cgi?id=908
    //      if (present) {
    //        frame.setUndecorated(true);
    //        frame.setBackground(backgroundColor);
    //        if (exclusive) {
    //          displayDevice.setFullScreenWindow(frame);
    //          fullScreenRect = frame.getBounds();
    //        } else {
    //          DisplayMode mode = displayDevice.getDisplayMode();
    //          fullScreenRect = new Rectangle(0, 0, mode.getWidth(), mode.getHeight());
    //          frame.setBounds(fullScreenRect);
    //          frame.setVisible(true);
    //        }
    //      }
    //      frame.setLayout(null);
    //      frame.add(applet);
    //      if (present) {
    //        frame.invalidate();
    //      } else {
    //        frame.pack();
    //      }
    //      // insufficient, places the 100x100 sketches offset strangely
    //      //frame.validate();
    //
    //      applet.init();
    //
    //      // Wait until the applet has figured out its width.
    //      // In a static mode app, this will be after setup() has completed,
    //      // and the empty draw() has set "finished" to true.
    //      // TODO make sure this won't hang if the applet has an exception.
    //      while (applet.defaultSize && !applet.finished) {
    //        //System.out.println("default size");
    //        try {
    //          Thread.sleep(5);
    //
    //        } catch (InterruptedException e) {
    //          //System.out.println("interrupt");
    //        }
    //      }
    //      //println("not default size " + applet.width + " " + applet.height);
    //      //println("  (g width/height is " + applet.g.width + "x" + applet.g.height + ")");
    //
    //      if (present) {
    //        // After the pack(), the screen bounds are gonna be 0s
    //        frame.setBounds(fullScreenRect);
    //        applet.setBounds((fullScreenRect.width - applet.width) / 2,
    //                         (fullScreenRect.height - applet.height) / 2,
    //                         applet.width, applet.height);
    //
    //        if (!hideStop) {
    //          Label label = new Label("stop");
    //          label.setForeground(stopColor);
    //          label.addMouseListener(new MouseAdapter() {
    //              public void mousePressed(MouseEvent e) {
    //                System.exit(0);
    //              }
    //            });
    //          frame.add(label);
    //
    //          Dimension labelSize = label.getPreferredSize();
    //          // sometimes shows up truncated on mac
    //          //System.out.println("label width is " + labelSize.width);
    //          labelSize = new Dimension(100, labelSize.height);
    //          label.setSize(labelSize);
    //          label.setLocation(20, fullScreenRect.height - labelSize.height - 20);
    //        }
    //
    //        // not always running externally when in present mode
    //        if (external) {
    //          applet.setupExternalMessages();
    //        }
    //
    //      } else {  // if not presenting
    //        // can't do pack earlier cuz present mode don't like it
    //        // (can't go full screen with a frame after calling pack)
    ////        frame.pack();  // get insets. get more.
    //        Insets insets = frame.getInsets();
    //
    //        int windowW = Math.max(applet.width, MIN_WINDOW_WIDTH) +
    //          insets.left + insets.right;
    //        int windowH = Math.max(applet.height, MIN_WINDOW_HEIGHT) +
    //          insets.top + insets.bottom;
    //
    //        frame.setSize(windowW, windowH);
    //
    //        if (location != null) {
    //          // a specific location was received from PdeRuntime
    //          // (applet has been run more than once, user placed window)
    //          frame.setLocation(location[0], location[1]);
    //
    //        } else if (external) {
    //          int locationX = editorLocation[0] - 20;
    //          int locationY = editorLocation[1];
    //
    //          if (locationX - windowW > 10) {
    //            // if it fits to the left of the window
    //            frame.setLocation(locationX - windowW, locationY);
    //
    //          } else {  // doesn't fit
    //            // if it fits inside the editor window,
    //            // offset slightly from upper lefthand corner
    //            // so that it's plunked inside the text area
    //            locationX = editorLocation[0] + 66;
    //            locationY = editorLocation[1] + 66;
    //
    //            if ((locationX + windowW > applet.screen.width - 33) ||
    //                (locationY + windowH > applet.screen.height - 33)) {
    //              // otherwise center on screen
    //              locationX = (applet.screen.width - windowW) / 2;
    //              locationY = (applet.screen.height - windowH) / 2;
    //            }
    //            frame.setLocation(locationX, locationY);
    //          }
    //        } else {  // just center on screen
    //          frame.setLocation((applet.screen.width - applet.width) / 2,
    //                            (applet.screen.height - applet.height) / 2);
    //        }
    //
    ////        frame.setLayout(null);
    ////        frame.add(applet);
    //
    //        if (backgroundColor == Color.black) {  //BLACK) {
    //          // this means no bg color unless specified
    //          backgroundColor = SystemColor.control;
    //        }
    //        frame.setBackground(backgroundColor);
    //
    //        int usableWindowH = windowH - insets.top - insets.bottom;
    //        applet.setBounds((windowW - applet.width)/2,
    //                         insets.top + (usableWindowH - applet.height)/2,
    //                         applet.width, applet.height);
    //
    //        if (external) {
    //          applet.setupExternalMessages();
    //
    //        } else {  // !external
    //          frame.addWindowListener(new WindowAdapter() {
    //              public void windowClosing(WindowEvent e) {
    //                System.exit(0);
    //              }
    //            });
    //        }
    //
    //        // handle frame resizing events
    //        applet.setupFrameResizeListener();
    //
    //        // all set for rockin
    //        if (applet.displayable()) {
    //          frame.setVisible(true);
    //        }
    //      }
    //
    //      applet.requestFocus(); // ask for keydowns
    //      //System.out.println("exiting main()");
    //
    //    } catch (Exception e) {
    //      e.printStackTrace();
    //      System.exit(1);
    //    }
    //  }


    //////////////////////////////////////////////////////////////

    /**
     * Begin recording to a new renderer of the specified type, using the width
     * and height of the main drawing surface.
     */
    //  public PGraphics beginRecord(String renderer, String filename) {
    //    filename = insertFrame(filename);
    //    PGraphics rec = createGraphics(width, height, renderer, filename);
    //    beginRecord(rec);
    //    return rec;
    //  }
    /**
     * Begin recording (echoing) commands to the specified PGraphics object.
     */
    //  public void beginRecord(PGraphics recorder) {
    //    PGraphics.showMethodWarning("beginRecord");
    //  }
    //  public void endRecord() {
    //    PGraphics.showMethodWarning("endRecord");
    //  }
    /**
     * Begin recording raw shape data to a renderer of the specified type,
     * using the width and height of the main drawing surface.
     *
     * If hashmarks (###) are found in the filename, they'll be replaced
     * by the current frame number (frameCount).
     */
    //  public PGraphics beginRaw(String renderer, String filename) {
    //    filename = insertFrame(filename);
    //    PGraphics rec = createGraphics(width, height, renderer, filename);
    //    g.beginRaw(rec);
    //    return rec;
    //  }
    /**
     * Begin recording raw shape data to the specified renderer.
     *
     * This simply echoes to g.beginRaw(), but since is placed here (rather than
     * generated by preproc.pl) for clarity and so that it doesn't echo the
     * command should beginRecord() be in use.
     */
    //  public void beginRaw(PGraphics rawGraphics) {
    //    g.beginRaw(rawGraphics);
    //  }
    /**
     * Stop recording raw shape data to the specified renderer.
     *
     * This simply echoes to g.beginRaw(), but since is placed here (rather than
     * generated by preproc.pl) for clarity and so that it doesn't echo the
     * command should beginRecord() be in use.
     */
    //  public void endRaw() {
    //    g.endRaw();
    //  }


    //////////////////////////////////////////////////////////////

    /**
     * Override the g.pixels[] function to set the pixels[] array
     * that's part of the PApplet object. Allows the use of
     * pixels[] in the code, rather than g.pixels[].
     */
    open fun loadPixels() {
        graphics!!.loadPixels()
        pixels = graphics!!.pixels
    }

    open fun updatePixels() {
        graphics!!.updatePixels()
    }

    open fun updatePixels(x1: Int, y1: Int, x2: Int, y2: Int) {
        graphics!!.updatePixels(x1, y1, x2, y2)
    }

    //////////////////////////////////////////////////////////////

    // ANDROID-SPECIFIC API

    // Wallpaper and wear API

    open fun wallpaperPreview(): Boolean {
        return surface!!.getEngine()!!.isPreview()
    }

    open fun wallpaperOffset(): Float {
        return surface!!.getEngine()!!.getXOffset()
    }

    open fun wallpaperHomeCount(): Int {
        val step = surface!!.getEngine()!!.getXOffsetStep()
        return if (0 < step) {
            (1 + 1 / step).toInt()
        } else {
            1
        }
    }

    open fun wearAmbient(): Boolean {
        return surface!!.getEngine()!!.isInAmbientMode()
    }

    open fun wearInteractive(): Boolean {
        return !surface!!.getEngine()!!.isInAmbientMode()
    }

    open fun wearRound(): Boolean {
        return surface!!.getEngine()!!.isRound()
    }

    open fun wearSquare(): Boolean {
        return !surface!!.getEngine()!!.isRound()
    }

    open fun wearInsets(): Rect? {
        return surface!!.getEngine()!!.getInsets()
    }

    open fun wearLowBit(): Boolean {
        return surface!!.getEngine()!!.useLowBitAmbient()
    }

    open fun wearBurnIn(): Boolean {
        return surface!!.getEngine()!!.requireBurnInProtection()
    }

    // Ray casting API

    // todo - nullable-type arg {ray} and return-type
    open fun getRayFromScreen(screenX: Float, screenY: Float, ray: Array<PVector?>?): Array<PVector?>? {
        return graphics!!.getRayFromScreen(screenX, screenY, ray)
    }

    // todo - nullable-type arg and return-type
    open fun getRayFromScreen(screenX: Float, screenY: Float, origin: PVector?, direction: PVector?) {
        graphics!!.getRayFromScreen(screenX, screenY, origin, direction)
    }

    open fun intersectsSphere(r: Float, screenX: Float, screenY: Float): Boolean {
        return graphics!!.intersectsSphere(r, screenX, screenY)
    }

    open fun intersectsSphere(r: Float, origin: PVector?, direction: PVector?): Boolean {
        return graphics!!.intersectsSphere(r, origin, direction)
    }

    open fun intersectsBox(w: Float, screenX: Float, screenY: Float): Boolean {
        return graphics!!.intersectsBox(w, screenX, screenY)
    }

    open fun intersectsBox(w: Float, h: Float, d: Float, screenX: Float, screenY: Float): Boolean {
        return graphics!!.intersectsBox(w, h, d, screenX, screenY)
    }

    open fun intersectsBox(size: Float, origin: PVector?, direction: PVector?): Boolean {
        return graphics!!.intersectsBox(size, origin, direction)
    }

    open fun intersectsBox(w: Float, h: Float, d: Float, origin: PVector?, direction: PVector?): Boolean {
        return graphics!!.intersectsBox(w, h, d, origin, direction)
    }

    // todo - nullable-type return
    open fun intersectsPlane(screenX: Float, screenY: Float): PVector? {
        return graphics!!.intersectsPlane(screenX, screenY)
    }

    // todo - return-type @ Nullable
    open fun intersectsPlane(origin: PVector?, direction: PVector?): PVector? {
        return graphics!!.intersectsPlane(origin, direction)
    }

    open fun eye() {
        graphics!!.eye()
    }

    open fun calculate() {

    }

    /**
     * Sets the coordinate system in 3D centered at (width/2, height/2)
     * and with the Y axis pointing up.
     */
    open fun cameraUp() {
        graphics!!.cameraUp()
    }

    /**
     * Returns a copy of the current object matrix.
     * Pass in null to create a new matrix.
     */
    // todo - return-type @ Nullable
    val objectMatrix: PMatrix3D?
        get() = graphics!!.objectMatrix

    /**
     * Copy the current object matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    // todo - return-type @ Nullable
    open fun getObjectMatrix(target: PMatrix3D?): PMatrix3D? {
        return graphics!!.getObjectMatrix(target)
    }

    /**
     * Returns a copy of the current eye matrix.
     * Pass in null to create a new matrix.
     */
    // todo - return-type @ Nullable
    val eyeMatrix: PMatrix3D?
        get() = graphics!!.eyeMatrix

    /**
     * Copy the current eye matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    // todo - return-type @ Nullable
    open fun getEyeMatrix(target: PMatrix3D?): PMatrix3D? {
        return graphics!!.getEyeMatrix(target)
    }

    //////////////////////////////////////////////////////////////
    // EVERYTHING BELOW THIS LINE IS AUTOMATICALLY GENERATED. DO NOT TOUCH!
    // This includes the Javadoc comments, which are automatically copied from
    // the PImage and PGraphics source code files.
    // public functions for processing.core
    // todo - return-type @ Nullable
    open fun beginPGL(): PGL? {
        return graphics!!.beginPGL()
    }

    open fun endPGL() {
        graphics!!.endPGL()
    }

    open fun flush() {
        graphics!!.flush()
    }

    open fun hint(which: Int) {
        graphics!!.hint(which)
    }

    /**
     * Start a new shape of type POLYGON
     */
    open fun beginShape() {
        graphics!!.beginShape()
    }


    /**
     * ( begin auto-generated from beginShape.xml )
     *
     * Using the **beginShape()** and **endShape()** functions allow
     * creating more complex forms. **beginShape()** begins recording
     * vertices for a shape and **endShape()** stops recording. The value of
     * the **MODE** parameter tells it which types of shapes to create from
     * the provided vertices. With no mode specified, the shape can be any
     * irregular polygon. The parameters available for beginShape() are POINTS,
     * LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP, QUADS, and QUAD_STRIP.
     * After calling the **beginShape()** function, a series of
     * **vertex()** commands must follow. To stop drawing the shape, call
     * **endShape()**. The **vertex()** function with two parameters
     * specifies a position in 2D and the **vertex()** function with three
     * parameters specifies a position in 3D. Each shape will be outlined with
     * the current stroke color and filled with the fill color.
     * <br></br> <br></br>
     * Transformations such as **translate()**, **rotate()**, and
     * **scale()** do not work within **beginShape()**. It is also not
     * possible to use other shapes, such as **ellipse()** or **rect()**
     * within **beginShape()**.
     * <br></br> <br></br>
     * The P3D renderer settings allow **stroke()** and **fill()**
     * settings to be altered per-vertex, however the default P2D renderer does
     * not. Settings such as **strokeWeight()**, **strokeCap()**, and
     * **strokeJoin()** cannot be changed while inside a
     * **beginShape()**/
    /**endShape()** block with any renderer.
    *
    * ( end auto-generated )
    * @webref shape:vertex
    * @param kind Either POINTS, LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP, QUADS, or QUAD_STRIP
    * @see PShape
    *
    * @see PGraphics.endShape
    * @see PGraphics.vertex
    * @see PGraphics.curveVertex
    * @see PGraphics.bezierVertex
    */
    open fun beginShape(kind: Int) {
        graphics!!.beginShape(kind)
    }

    /**
     * Sets whether the upcoming vertex is part of an edge.
     * Equivalent to glEdgeFlag(), for people familiar with OpenGL.
     */
    open fun edge(edge: Boolean) {
        graphics!!.edge(edge)
    }

    /**
     * ( begin auto-generated from normal.xml )
     *
     * Sets the current normal vector. This is for drawing three dimensional
     * shapes and surfaces and specifies a vector perpendicular to the surface
     * of the shape which determines how lighting affects it. Processing
     * attempts to automatically assign normals to shapes, but since that's
     * imperfect, this is a better option when you want more control. This
     * function is identical to glNormal3f() in OpenGL.
     *
     * ( end auto-generated )
     * @webref lights_camera:lights
     * @param nx x direction
     * @param ny y direction
     * @param nz z direction
     * @see PGraphics.beginShape
     * @see PGraphics.endShape
     * @see PGraphics.lights
     */
    open fun normal(nx: Float, ny: Float, nz: Float) {
        graphics!!.normal(nx, ny, nz)
    }

    open fun attribPosition(name: String?, x: Float, y: Float, z: Float) {
        graphics!!.attribPosition(name, x, y, z)
    }

    open fun attribNormal(name: String?, nx: Float, ny: Float, nz: Float) {
        graphics!!.attribNormal(name, nx, ny, nz)
    }

    open fun attribColor(name: String?, color: Int) {
        graphics!!.attribColor(name, color)
    }

    open fun attrib(name: String?, vararg values: Float) {
        graphics!!.attrib(name, *values)
    }

    open fun attrib(name: String?, vararg values: Int) {
        graphics!!.attrib(name, *values)
    }

    open fun attrib(name: String?, vararg values: Boolean) {
        graphics!!.attrib(name, *values)
    }

    /**
     * ( begin auto-generated from textureMode.xml )
     *
     * Sets the coordinate space for texture mapping. There are two options,
     * IMAGE, which refers to the actual coordinates of the image, and
     * NORMAL, which refers to a normalized space of values ranging from 0
     * to 1. The default mode is IMAGE. In IMAGE, if an image is 100 x 200
     * pixels, mapping the image onto the entire size of a quad would require
     * the points (0,0) (0,100) (100,200) (0,200). The same mapping in
     * NORMAL_SPACE is (0,0) (0,1) (1,1) (0,1).
     *
     * ( end auto-generated )
     * @webref image:textures
     * @param mode either IMAGE or NORMAL
     * @see PGraphics.texture
     * @see PGraphics.textureWrap
     */
    open fun textureMode(mode: Int) {
        graphics!!.textureMode(mode)
    }

    /**
     * ( begin auto-generated from textureWrap.xml )
     *
     * Description to come...
     *
     * ( end auto-generated from textureWrap.xml )
     *
     * @webref image:textures
     * @param wrap Either CLAMP (default) or REPEAT
     * @see PGraphics.texture
     * @see PGraphics.textureMode
     */
    open fun textureWrap(wrap: Int) {
        graphics!!.textureWrap(wrap)
    }

    /**
     * ( begin auto-generated from texture.xml )
     *
     * Sets a texture to be applied to vertex points. The **texture()**
     * function must be called between **beginShape()** and
     * **endShape()** and before any calls to **vertex()**.
     * <br></br> <br></br>
     * When textures are in use, the fill color is ignored. Instead, use tint()
     * to specify the color of the texture as it is applied to the shape.
     *
     * ( end auto-generated )
     * @webref image:textures
     * @param image reference to a PImage object
     * @see PGraphics.textureMode
     * @see PGraphics.textureWrap
     * @see PGraphics.beginShape
     * @see PGraphics.endShape
     * @see PGraphics.vertex
     */
    open fun texture(image: PImage?) {
        graphics!!.texture(image)
    }

    /**
     * Removes texture image for current shape.
     * Needs to be called between beginShape and endShape
     *
     */
    open fun noTexture() {
        graphics!!.noTexture()
    }

    open fun vertex(x: Float, y: Float) {
        graphics!!.vertex(x, y)
    }

    open fun vertex(x: Float, y: Float, z: Float) {
        graphics!!.vertex(x, y, z)
    }

    /**
     * Used by renderer subclasses or PShape to efficiently pass in already
     * formatted vertex information.
     * @param v vertex parameters, as a float array of length VERTEX_FIELD_COUNT
     */
    open fun vertex(v: FloatArray?) {
        graphics!!.vertex(v)
    }

    open fun vertex(x: Float, y: Float, u: Float, v: Float) {
        graphics!!.vertex(x, y, u, v)
    }

    /**
     * ( begin auto-generated from vertex.xml )
     *
     * All shapes are constructed by connecting a series of vertices.
     * **vertex()** is used to specify the vertex coordinates for points,
     * lines, triangles, quads, and polygons and is used exclusively within the
     * **beginShape()** and **endShape()** function.<br></br>
     * <br></br>
     * Drawing a vertex in 3D using the **z** parameter requires the P3D
     * parameter in combination with size as shown in the above example.<br></br>
     * <br></br>
     * This function is also used to map a texture onto the geometry. The
     * **texture()** function declares the texture to apply to the geometry
     * and the **u** and **v** coordinates set define the mapping of this
     * texture to the form. By default, the coordinates used for **u** and
     * **v** are specified in relation to the image's size in pixels, but
     * this relation can be changed with **textureMode()**.
     *
     * ( end auto-generated )
     * @webref shape:vertex
     * @param x x-coordinate of the vertex
     * @param y y-coordinate of the vertex
     * @param z z-coordinate of the vertex
     * @param u horizontal coordinate for the texture mapping
     * @param v vertical coordinate for the texture mapping
     * @see PGraphics.beginShape
     * @see PGraphics.endShape
     * @see PGraphics.bezierVertex
     * @see PGraphics.quadraticVertex
     * @see PGraphics.curveVertex
     * @see PGraphics.texture
     */
    open fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        graphics!!.vertex(x, y, z, u, v)
    }

    /**
     * @webref shape:vertex
     */
    open fun beginContour() {
        graphics!!.beginContour()
    }

    /**
     * @webref shape:vertex
     */
    open fun endContour() {
        graphics!!.endContour()
    }

    open fun endShape() {
        graphics!!.endShape()
    }

    /**
     * ( begin auto-generated from endShape.xml )
     *
     * The **endShape()** function is the companion to **beginShape()**
     * and may only be called after **beginShape()**. When **endshape()**
     * is called, all of image data defined since the previous call to
     * **beginShape()** is written into the image buffer. The constant CLOSE
     * as the value for the MODE parameter to close the shape (to connect the
     * beginning and the end).
     *
     * ( end auto-generated )
     * @webref shape:vertex
     * @param mode use CLOSE to close the shape
     * @see PShape
     *
     * @see PGraphics.beginShape
     */
    open fun endShape(mode: Int) {
        graphics!!.endShape(mode)
    }

    /**
     * @webref shape
     * @param filename name of file to load, can be .svg or .obj
     * @see PShape
     *
     * @see PApplet.createShape
     */
    // todo both arg and return-type are nullable
    open fun loadShape(filename: String?): PShape? {
        return graphics!!.loadShape(filename)
    }

    /**
     * @nowebref
     */
    // todo both args and return-type are nullable
    open fun loadShape(filename: String?, options: String?): PShape? {
        return graphics!!.loadShape(filename, options)
    }

    /**
     * @webref shape
     * @see PShape
     *
     * @see PShape.endShape
     * @see PApplet.loadShape
     */
    // todo - return-type @ Non-nullable
    open fun createShape(): PShape {
        return graphics!!.createShape()
    }

    // todo - return-type @ Non-nullable
    open fun createShape(type: Int): PShape {
        return graphics!!.createShape(type)
    }

    /**
     * @param kind either POINT, LINE, TRIANGLE, QUAD, RECT, ELLIPSE, ARC, BOX, SPHERE
     * @param p parameters that match the kind of shape
     */
    // todo - return-type @ Non-nullable
    open fun createShape(kind: Int, vararg p: Float): PShape {
        return graphics!!.createShape(kind, *p)
    }

    /**
     * ( begin auto-generated from loadShader.xml )
     *
     * This is a new reference entry for Processing 2.0. It will be updated shortly.
     *
     * ( end auto-generated )
     *
     * @webref rendering:shaders
     * @param fragFilename name of fragment shader file
     */
    // todo - both arg and return-type are Nullable
    open fun loadShader(fragFilename: String?): PShader? {
        return graphics!!.loadShader(fragFilename)
    }

    /**
     * @param vertFilename name of vertex shader file
     */
    // todo - both arg and return-type are Nullable
    open fun loadShader(fragFilename: String?, vertFilename: String?): PShader? {
        return graphics!!.loadShader(fragFilename, vertFilename)
    }

    /**
     * ( begin auto-generated from shader.xml )
     *
     * This is a new reference entry for Processing 2.0. It will be updated shortly.
     *
     * ( end auto-generated )
     *
     * @webref rendering:shaders
     * @param shader name of shader file
     */
    // todo - arg @ Nullable
    open fun shader(shader: PShader?) {
        graphics!!.shader(shader)
    }

    /**
     * @param kind type of shader, either POINTS, LINES, or TRIANGLES
     */
    // todo - nullable {shader}
    open fun shader(shader: PShader?, kind: Int) {
        graphics!!.shader(shader, kind)
    }

    /**
     * ( begin auto-generated from resetShader.xml )
     *
     * This is a new reference entry for Processing 2.0. It will be updated shortly.
     *
     * ( end auto-generated )
     *
     * @webref rendering:shaders
     */
    open fun resetShader() {
        graphics!!.resetShader()
    }

    /**
     * @param kind type of shader, either POINTS, LINES, or TRIANGLES
     */
    open fun resetShader(kind: Int) {
        graphics!!.resetShader(kind)
    }

    /**
     * @param shader the fragment shader to apply
     */
    open fun filter(shader: PShader?) {
        graphics!!.filter(shader)
    }

    /**
     * ( begin auto-generated from clip.xml )
     *
     * Limits the rendering to the boundaries of a rectangle defined
     * by the parameters. The boundaries are drawn based on the state
     * of the **imageMode()** fuction, either CORNER, CORNERS, or CENTER.
     *
     * ( end auto-generated )
     *
     * @webref rendering
     * @param a x-coordinate of the rectangle, by default
     * @param b y-coordinate of the rectangle, by default
     * @param c width of the rectangle, by default
     * @param d height of the rectangle, by default
     */
    open fun clip(a: Float, b: Float, c: Float, d: Float) {
        graphics!!.clip(a, b, c, d)
    }

    /**
     * ( begin auto-generated from noClip.xml )
     *
     * Disables the clipping previously started by the **clip()** function.
     *
     * ( end auto-generated )
     *
     * @webref rendering
     */
    open fun noClip() {
        graphics!!.noClip()
    }

    /**
     * ( begin auto-generated from blendMode.xml )
     *
     * This is a new reference entry for Processing 2.0. It will be updated shortly.
     *
     * ( end auto-generated )
     *
     * @webref rendering
     * @param mode the blending mode to use
     */
    open fun blendMode(mode: Int) {
        graphics!!.blendMode(mode)
    }

    open fun bezierVertex(x2: Float, y2: Float,
                     x3: Float, y3: Float,
                     x4: Float, y4: Float) {
        graphics!!.bezierVertex(x2, y2, x3, y3, x4, y4)
    }

    /**
     * ( begin auto-generated from bezierVertex.xml )
     *
     * Specifies vertex coordinates for Bezier curves. Each call to
     * **bezierVertex()** defines the position of two control points and one
     * anchor point of a Bezier curve, adding a new segment to a line or shape.
     * The first time **bezierVertex()** is used within a
     * **beginShape()** call, it must be prefaced with a call to
     * **vertex()** to set the first anchor point. This function must be
     * used between **beginShape()** and **endShape()** and only when
     * there is no MODE parameter specified to **beginShape()**. Using the
     * 3D version requires rendering with P3D (see the Environment reference
     * for more information).
     *
     * ( end auto-generated )
     * @webref shape:vertex
     * @param x2 the x-coordinate of the 1st control point
     * @param y2 the y-coordinate of the 1st control point
     * @param z2 the z-coordinate of the 1st control point
     * @param x3 the x-coordinate of the 2nd control point
     * @param y3 the y-coordinate of the 2nd control point
     * @param z3 the z-coordinate of the 2nd control point
     * @param x4 the x-coordinate of the anchor point
     * @param y4 the y-coordinate of the anchor point
     * @param z4 the z-coordinate of the anchor point
     * @see PGraphics.curveVertex
     * @see PGraphics.vertex
     * @see PGraphics.quadraticVertex
     * @see PGraphics.bezier
     */
    open fun bezierVertex(x2: Float, y2: Float, z2: Float,
                     x3: Float, y3: Float, z3: Float,
                     x4: Float, y4: Float, z4: Float) {
        graphics!!.bezierVertex(x2, y2, z2, x3, y3, z3, x4, y4, z4)
    }

    /**
     * @webref shape:vertex
     * @param cx the x-coordinate of the control point
     * @param cy the y-coordinate of the control point
     * @param x3 the x-coordinate of the anchor point
     * @param y3 the y-coordinate of the anchor point
     * @see PGraphics.curveVertex
     * @see PGraphics.vertex
     * @see PGraphics.bezierVertex
     * @see PGraphics.bezier
     */
    open fun quadraticVertex(cx: Float, cy: Float,
                        x3: Float, y3: Float) {
        graphics!!.quadraticVertex(cx, cy, x3, y3)
    }

    /**
     * @param cz the z-coordinate of the control point
     * @param z3 the z-coordinate of the anchor point
     */
    open fun quadraticVertex(cx: Float, cy: Float, cz: Float,
                        x3: Float, y3: Float, z3: Float) {
        graphics!!.quadraticVertex(cx, cy, cz, x3, y3, z3)
    }

    /**
     * ( begin auto-generated from curveVertex.xml )
     *
     * Specifies vertex coordinates for curves. This function may only be used
     * between **beginShape()** and **endShape()** and only when there is
     * no MODE parameter specified to **beginShape()**. The first and last
     * points in a series of **curveVertex()** lines will be used to guide
     * the beginning and end of a the curve. A minimum of four points is
     * required to draw a tiny curve between the second and third points.
     * Adding a fifth point with **curveVertex()** will draw the curve
     * between the second, third, and fourth points. The **curveVertex()**
     * function is an implementation of Catmull-Rom splines. Using the 3D
     * version requires rendering with P3D (see the Environment reference for
     * more information).
     *
     * ( end auto-generated )
     *
     * @webref shape:vertex
     * @param x the x-coordinate of the vertex
     * @param y the y-coordinate of the vertex
     * @see PGraphics.curve
     * @see PGraphics.beginShape
     * @see PGraphics.endShape
     * @see PGraphics.vertex
     * @see PGraphics.bezier
     * @see PGraphics.quadraticVertex
     */
    open fun curveVertex(x: Float, y: Float) {
        graphics!!.curveVertex(x, y)
    }

    /**
     * @param z the z-coordinate of the vertex
     */
    open fun curveVertex(x: Float, y: Float, z: Float) {
        graphics!!.curveVertex(x, y, z)
    }

    /**
     * ( begin auto-generated from point.xml )
     *
     * Draws a point, a coordinate in space at the dimension of one pixel. The
     * first parameter is the horizontal value for the point, the second value
     * is the vertical value for the point, and the optional third value is the
     * depth value. Drawing this shape in 3D with the **z** parameter
     * requires the P3D parameter in combination with **size()** as shown in
     * the above example.
     *
     * ( end auto-generated )
     *
     * @webref shape:2d_primitives
     * @param x x-coordinate of the point
     * @param y y-coordinate of the point
     * @see PGraphics.stroke
     */
    open fun point(x: Float, y: Float) {
        graphics!!.point(x, y)
    }

    /**
     * @param z z-coordinate of the point
     */
    open fun point(x: Float, y: Float, z: Float) {
        graphics!!.point(x, y, z)
    }

    /**
     * ( begin auto-generated from line.xml )
     *
     * Draws a line (a direct path between two points) to the screen. The
     * version of **line()** with four parameters draws the line in 2D.  To
     * color a line, use the **stroke()** function. A line cannot be filled,
     * therefore the **fill()** function will not affect the color of a
     * line. 2D lines are drawn with a width of one pixel by default, but this
     * can be changed with the **strokeWeight()** function. The version with
     * six parameters allows the line to be placed anywhere within XYZ space.
     * Drawing this shape in 3D with the **z** parameter requires the P3D
     * parameter in combination with **size()** as shown in the above example.
     *
     * ( end auto-generated )
     * @webref shape:2d_primitives
     * @param x1 x-coordinate of the first point
     * @param y1 y-coordinate of the first point
     * @param x2 x-coordinate of the second point
     * @param y2 y-coordinate of the second point
     * @see PGraphics.strokeWeight
     * @see PGraphics.strokeJoin
     * @see PGraphics.strokeCap
     * @see PGraphics.beginShape
     */
    open fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        graphics!!.line(x1, y1, x2, y2)
    }

    /**
     * @param z1 z-coordinate of the first point
     * @param z2 z-coordinate of the second point
     */
    open fun line(x1: Float, y1: Float, z1: Float,
             x2: Float, y2: Float, z2: Float) {
        graphics!!.line(x1, y1, z1, x2, y2, z2)
    }

    /**
     * ( begin auto-generated from triangle.xml )
     *
     * A triangle is a plane created by connecting three points. The first two
     * arguments specify the first point, the middle two arguments specify the
     * second point, and the last two arguments specify the third point.
     *
     * ( end auto-generated )
     * @webref shape:2d_primitives
     * @param x1 x-coordinate of the first point
     * @param y1 y-coordinate of the first point
     * @param x2 x-coordinate of the second point
     * @param y2 y-coordinate of the second point
     * @param x3 x-coordinate of the third point
     * @param y3 y-coordinate of the third point
     * @see PApplet.beginShape
     */
    open fun triangle(x1: Float, y1: Float, x2: Float, y2: Float,
                 x3: Float, y3: Float) {
        graphics!!.triangle(x1, y1, x2, y2, x3, y3)
    }

    /**
     * ( begin auto-generated from quad.xml )
     *
     * A quad is a quadrilateral, a four sided polygon. It is similar to a
     * rectangle, but the angles between its edges are not constrained to
     * ninety degrees. The first pair of parameters (x1,y1) sets the first
     * vertex and the subsequent pairs should proceed clockwise or
     * counter-clockwise around the defined shape.
     *
     * ( end auto-generated )
     * @webref shape:2d_primitives
     * @param x1 x-coordinate of the first corner
     * @param y1 y-coordinate of the first corner
     * @param x2 x-coordinate of the second corner
     * @param y2 y-coordinate of the second corner
     * @param x3 x-coordinate of the third corner
     * @param y3 y-coordinate of the third corner
     * @param x4 x-coordinate of the fourth corner
     * @param y4 y-coordinate of the fourth corner
     */
    open fun quad(x1: Float, y1: Float, x2: Float, y2: Float,
             x3: Float, y3: Float, x4: Float, y4: Float) {
        graphics!!.quad(x1, y1, x2, y2, x3, y3, x4, y4)
    }

    /**
     * ( begin auto-generated from rectMode.xml )
     *
     * Modifies the location from which rectangles draw. The default mode is
     * **rectMode(CORNER)**, which specifies the location to be the upper
     * left corner of the shape and uses the third and fourth parameters of
     * **rect()** to specify the width and height. The syntax
     * **rectMode(CORNERS)** uses the first and second parameters of
     * **rect()** to set the location of one corner and uses the third and
     * fourth parameters to set the opposite corner. The syntax
     * **rectMode(CENTER)** draws the image from its center point and uses
     * the third and forth parameters of **rect()** to specify the image's
     * width and height. The syntax **rectMode(RADIUS)** draws the image
     * from its center point and uses the third and forth parameters of
     * **rect()** to specify half of the image's width and height. The
     * parameter must be written in ALL CAPS because Processing is a case
     * sensitive language. Note: In version 125, the mode named CENTER_RADIUS
     * was shortened to RADIUS.
     *
     * ( end auto-generated )
     * @webref shape:attributes
     * @param mode either CORNER, CORNERS, CENTER, or RADIUS
     * @see PGraphics.rect
     */
    open fun rectMode(mode: Int) {
        graphics!!.rectMode(mode)
    }

    /**
     * ( begin auto-generated from rect.xml )
     *
     * Draws a rectangle to the screen. A rectangle is a four-sided shape with
     * every angle at ninety degrees. By default, the first two parameters set
     * the location of the upper-left corner, the third sets the width, and the
     * fourth sets the height. These parameters may be changed with the
     * **rectMode()** function.
     *
     * ( end auto-generated )
     *
     * @webref shape:2d_primitives
     * @param a x-coordinate of the rectangle by default
     * @param b y-coordinate of the rectangle by default
     * @param c width of the rectangle by default
     * @param d height of the rectangle by default
     * @see PGraphics.rectMode
     * @see PGraphics.quad
     */
    open fun rect(a: Float, b: Float, c: Float, d: Float) {
        graphics!!.rect(a, b, c, d)
    }

    /**
     * @param r radii for all four corners
     */
    open fun rect(a: Float, b: Float, c: Float, d: Float, r: Float) {
        graphics!!.rect(a, b, c, d, r)
    }

    /**
     * @param tl radius for top-left corner
     * @param tr radius for top-right corner
     * @param br radius for bottom-right corner
     * @param bl radius for bottom-left corner
     */
    open fun rect(a: Float, b: Float, c: Float, d: Float,
             tl: Float, tr: Float, br: Float, bl: Float) {
        graphics!!.rect(a, b, c, d, tl, tr, br, bl)
    }

    /**
     * ( begin auto-generated from square.xml )
     *
     * Draws a square to the screen. A square is a four-sided shape with
     * every angle at ninety degrees and each side is the same length.
     * By default, the first two parameters set the location of the
     * upper-left corner, the third sets the width and height. The way
     * these parameters are interpreted, however, may be changed with the
     * **rectMode()** function.
     *
     * ( end auto-generated )
     *
     * @webref shape:2d_primitives
     * @param x x-coordinate of the rectangle by default
     * @param y y-coordinate of the rectangle by default
     * @param extent width and height of the rectangle by default
     * @see PGraphics.rect
     * @see PGraphics.rectMode
     */
    open fun square(x: Float, y: Float, extent: Float) {
        graphics!!.square(x, y, extent)
    }

    /**
     * ( begin auto-generated from ellipseMode.xml )
     *
     * The origin of the ellipse is modified by the **ellipseMode()**
     * function. The default configuration is **ellipseMode(CENTER)**, which
     * specifies the location of the ellipse as the center of the shape. The
     * **RADIUS** mode is the same, but the width and height parameters to
     * **ellipse()** specify the radius of the ellipse, rather than the
     * diameter. The **CORNER** mode draws the shape from the upper-left
     * corner of its bounding box. The **CORNERS** mode uses the four
     * parameters to **ellipse()** to set two opposing corners of the
     * ellipse's bounding box. The parameter must be written in ALL CAPS
     * because Processing is a case-sensitive language.
     *
     * ( end auto-generated )
     * @webref shape:attributes
     * @param mode either CENTER, RADIUS, CORNER, or CORNERS
     * @see PApplet.ellipse
     * @see PApplet.arc
     */
    open fun ellipseMode(mode: Int) {
        graphics!!.ellipseMode(mode)
    }

    /**
     * ( begin auto-generated from ellipse.xml )
     *
     * Draws an ellipse (oval) in the display window. An ellipse with an equal
     * **width** and **height** is a circle. The first two parameters set
     * the location, the third sets the width, and the fourth sets the height.
     * The origin may be changed with the **ellipseMode()** function.
     *
     * ( end auto-generated )
     * @webref shape:2d_primitives
     * @param a x-coordinate of the ellipse
     * @param b y-coordinate of the ellipse
     * @param c width of the ellipse by default
     * @param d height of the ellipse by default
     * @see PApplet.ellipseMode
     * @see PApplet.arc
     */
    open fun ellipse(a: Float, b: Float, c: Float, d: Float) {
        graphics!!.ellipse(a, b, c, d)
    }

    /**
     * ( begin auto-generated from arc.xml )
     *
     * Draws an arc in the display window. Arcs are drawn along the outer edge
     * of an ellipse defined by the **x**, **y**, **width** and
     * **height** parameters. The origin or the arc's ellipse may be changed
     * with the **ellipseMode()** function. The **start** and **stop**
     * parameters specify the angles at which to draw the arc.
     *
     * ( end auto-generated )
     * @webref shape:2d_primitives
     * @param a x-coordinate of the arc's ellipse
     * @param b y-coordinate of the arc's ellipse
     * @param c width of the arc's ellipse by default
     * @param d height of the arc's ellipse by default
     * @param start angle to start the arc, specified in radians
     * @param stop angle to stop the arc, specified in radians
     * @see PApplet.ellipse
     * @see PApplet.ellipseMode
     * @see PApplet.radians
     * @see PApplet.degrees
     */
    open fun arc(a: Float, b: Float, c: Float, d: Float,
            start: Float, stop: Float) {
        graphics!!.arc(a, b, c, d, start, stop)
    }

    /**
   * @param mode either OPEN, CHORD, or PIE
   */
    open fun arc(a: Float, b: Float, c: Float, d: Float,
            start: Float, stop: Float, mode: Int) {
        graphics!!.arc(a, b, c, d, start, stop, mode)
    }

    /**
     * ( begin auto-generated from circle.xml )
     *
     * Draws a circle to the screen. By default, the first two parameters
     * set the location of the center, and the third sets the shape's width
     * and height. The origin may be changed with the **ellipseMode()**
     * function.
     *
     * ( end auto-generated )
     * @webref shape:2d_primitives
     * @param x x-coordinate of the ellipse
     * @param y y-coordinate of the ellipse
     * @param extent width and height of the ellipse by default
     * @see PApplet.ellipse
     * @see PApplet.ellipseMode
     */
    open fun circle(x: Float, y: Float, extent: Float) {
        graphics!!.circle(x, y, extent)
    }

    /**
     * ( begin auto-generated from box.xml )
     *
     * A box is an extruded rectangle. A box with equal dimension on all sides
     * is a cube.
     *
     * ( end auto-generated )
     *
     * @webref shape:3d_primitives
     * @param size dimension of the box in all dimensions (creates a cube)
     * @see PGraphics.sphere
     */
    open fun box(size: Float) {
        graphics!!.box(size)
    }

    /**
     * @param w dimension of the box in the x-dimension
     * @param h dimension of the box in the y-dimension
     * @param d dimension of the box in the z-dimension
     */
    open fun box(w: Float, h: Float, d: Float) {
        graphics!!.box(w, h, d)
    }

    /**
     * ( begin auto-generated from sphereDetail.xml )
     *
     * Controls the detail used to render a sphere by adjusting the number of
     * vertices of the sphere mesh. The default resolution is 30, which creates
     * a fairly detailed sphere definition with vertices every 360/30 = 12
     * degrees. If you're going to render a great number of spheres per frame,
     * it is advised to reduce the level of detail using this function. The
     * setting stays active until **sphereDetail()** is called again with a
     * new parameter and so should *not* be called prior to every
     * **sphere()** statement, unless you wish to render spheres with
     * different settings, e.g. using less detail for smaller spheres or ones
     * further away from the camera. To control the detail of the horizontal
     * and vertical resolution independently, use the version of the functions
     * with two parameters.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Code for sphereDetail() submitted by toxi [031031].
     * Code for enhanced u/v version from davbol [080801].
     *
     * @param res number of segments (minimum 3) used per full circle revolution
     * @webref shape:3d_primitives
     * @see PGraphics.sphere
     */
    open fun sphereDetail(res: Int) {
        graphics!!.sphereDetail(res)
    }

    /**
     * @param ures number of segments used longitudinally per full circle revolutoin
     * @param vres number of segments used latitudinally from top to bottom
     */
    open fun sphereDetail(ures: Int, vres: Int) {
        graphics!!.sphereDetail(ures, vres)
    }

    /**
     * ( begin auto-generated from sphere.xml )
     *
     * A sphere is a hollow ball made from tessellated triangles.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * <P>
     * Implementation notes:
    </P> * <P>
     * cache all the points of the sphere in a static array
     * top and bottom are just a bunch of triangles that land
     * in the center point
    </P> * <P>
     * sphere is a series of concentric circles who radii vary
     * along the shape, based on, er.. cos or something
    </P> * <PRE>
     * [toxi 031031] new sphere code. removed all multiplies with
     * radius, as scale() will take care of that anyway
     *
     * [toxi 031223] updated sphere code (removed modulos)
     * and introduced sphereAt(x,y,z,r)
     * to avoid additional translate()'s on the user/sketch side
     *
     * [davbol 080801] now using separate sphereDetailU/V
    </PRE> *
     *
     * @webref shape:3d_primitives
     * @param r the radius of the sphere
     * @see PGraphics.sphereDetail
     */
    open fun sphere(r: Float) {
        graphics!!.sphere(r)
    }

    /**
     * ( begin auto-generated from bezierPoint.xml )
     *
     * Evaluates the Bezier at point t for points a, b, c, d. The parameter t
     * varies between 0 and 1, a and d are points on the curve, and b and c are
     * the control points. This can be done once with the x coordinates and a
     * second time with the y coordinates to get the location of a bezier curve
     * at t.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * For instance, to convert the following example:<PRE>
     * stroke(255, 102, 0);
     * line(85, 20, 10, 10);
     * line(90, 90, 15, 80);
     * stroke(0, 0, 0);
     * bezier(85, 20, 10, 10, 90, 90, 15, 80);
     *
     * // draw it in gray, using 10 steps instead of the default 20
     * // this is a slower way to do it, but useful if you need
     * // to do things with the coordinates at each step
     * stroke(128);
     * beginShape(LINE_STRIP);
     * for (int i = 0; i <= 10; i++) {
     * float t = i / 10.0f;
     * float x = bezierPoint(85, 10, 90, 15, t);
     * float y = bezierPoint(20, 10, 90, 80, t);
     * vertex(x, y);
     * }
     * endShape();</PRE>
     *
     * @webref shape:curves
     * @param a coordinate of first point on the curve
     * @param b coordinate of first control point
     * @param c coordinate of second control point
     * @param d coordinate of second point on the curve
     * @param t value between 0 and 1
     * @see PGraphics.bezier
     * @see PGraphics.bezierVertex
     * @see PGraphics.curvePoint
     */
    open fun bezierPoint(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        return graphics!!.bezierPoint(a, b, c, d, t)
    }

    /**
     * ( begin auto-generated from bezierTangent.xml )
     *
     * Calculates the tangent of a point on a Bezier curve. There is a good
     * definition of [*tangent* on Wikipedia](http://en.wikipedia.org/wiki/Tangent).
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Code submitted by Dave Bollinger (davol) for release 0136.
     *
     * @webref shape:curves
     * @param a coordinate of first point on the curve
     * @param b coordinate of first control point
     * @param c coordinate of second control point
     * @param d coordinate of second point on the curve
     * @param t value between 0 and 1
     * @see PGraphics.bezier
     * @see PGraphics.bezierVertex
     * @see PGraphics.curvePoint
     */
    open fun bezierTangent(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        return graphics!!.bezierTangent(a, b, c, d, t)
    }

    /**
     * ( begin auto-generated from bezierDetail.xml )
     *
     * Sets the resolution at which Beziers display. The default value is 20.
     * This function is only useful when using the P3D renderer as the default
     * P2D renderer does not use this information.
     *
     * ( end auto-generated )
     *
     * @webref shape:curves
     * @param detail resolution of the curves
     * @see PGraphics.curve
     * @see PGraphics.curveVertex
     * @see PGraphics.curveTightness
     */
    open fun bezierDetail(detail: Int) {
        graphics!!.bezierDetail(detail)
    }

    open fun bezier(x1: Float, y1: Float,
               x2: Float, y2: Float,
               x3: Float, y3: Float,
               x4: Float, y4: Float) {
        graphics!!.bezier(x1, y1, x2, y2, x3, y3, x4, y4)
    }

    /**
     * ( begin auto-generated from bezier.xml )
     *
     * Draws a Bezier curve on the screen. These curves are defined by a series
     * of anchor and control points. The first two parameters specify the first
     * anchor point and the last two parameters specify the other anchor point.
     * The middle parameters specify the control points which define the shape
     * of the curve. Bezier curves were developed by French engineer Pierre
     * Bezier. Using the 3D version requires rendering with P3D (see the
     * Environment reference for more information).
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Draw a cubic bezier curve. The first and last points are
     * the on-curve points. The middle two are the 'control' points,
     * or 'handles' in an application like Illustrator.
     * <P>
     * Identical to typing:
    </P> * <PRE>beginShape();
     * vertex(x1, y1);
     * bezierVertex(x2, y2, x3, y3, x4, y4);
     * endShape();
    </PRE> *
     * In Postscript-speak, this would be:
     * <PRE>moveto(x1, y1);
     * curveto(x2, y2, x3, y3, x4, y4);</PRE>
     * If you were to try and continue that curve like so:
     * <PRE>curveto(x5, y5, x6, y6, x7, y7);</PRE>
     * This would be done in processing by adding these statements:
     * <PRE>bezierVertex(x5, y5, x6, y6, x7, y7)
    </PRE> *
     * To draw a quadratic (instead of cubic) curve,
     * use the control point twice by doubling it:
     * <PRE>bezier(x1, y1, cx, cy, cx, cy, x2, y2);</PRE>
     *
     * @webref shape:curves
     * @param x1 coordinates for the first anchor point
     * @param y1 coordinates for the first anchor point
     * @param z1 coordinates for the first anchor point
     * @param x2 coordinates for the first control point
     * @param y2 coordinates for the first control point
     * @param z2 coordinates for the first control point
     * @param x3 coordinates for the second control point
     * @param y3 coordinates for the second control point
     * @param z3 coordinates for the second control point
     * @param x4 coordinates for the second anchor point
     * @param y4 coordinates for the second anchor point
     * @param z4 coordinates for the second anchor point
     *
     * @see PGraphics.bezierVertex
     * @see PGraphics.curve
     */
    open fun bezier(x1: Float, y1: Float, z1: Float,
               x2: Float, y2: Float, z2: Float,
               x3: Float, y3: Float, z3: Float,
               x4: Float, y4: Float, z4: Float) {
        graphics!!.bezier(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4)
    }

    /**
     * ( begin auto-generated from curvePoint.xml )
     *
     * Evalutes the curve at point t for points a, b, c, d. The parameter t
     * varies between 0 and 1, a and d are the control points, and b and c are
     * the points on the curve. This can be done once with the x coordinates and a
     * second time with the y coordinates to get the location of a curve at t.
     *
     * ( end auto-generated )
     *
     * @webref shape:curves
     * @param a coordinate of first control point
     * @param b coordinate of first point on the curve
     * @param c coordinate of second point on the curve
     * @param d coordinate of second control point
     * @param t value between 0 and 1
     * @see PGraphics.curve
     * @see PGraphics.curveVertex
     * @see PGraphics.bezierPoint
     */
    open fun curvePoint(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        return graphics!!.curvePoint(a, b, c, d, t)
    }

    /**
     * ( begin auto-generated from curveTangent.xml )
     *
     * Calculates the tangent of a point on a curve. There's a good definition
     * of *[tangent](http://en.wikipedia.org/wiki/Tangent)* on Wikipedia.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Code thanks to Dave Bollinger (Bug #715)
     *
     * @webref shape:curves
     * @param a coordinate of first point on the curve
     * @param b coordinate of first control point
     * @param c coordinate of second control point
     * @param d coordinate of second point on the curve
     * @param t value between 0 and 1
     * @see PGraphics.curve
     * @see PGraphics.curveVertex
     * @see PGraphics.curvePoint
     * @see PGraphics.bezierTangent
     */
    open fun curveTangent(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        return graphics!!.curveTangent(a, b, c, d, t)
    }

    /**
     * ( begin auto-generated from curveDetail.xml )
     *
     * Sets the resolution at which curves display. The default value is 20.
     * This function is only useful when using the P3D renderer as the default
     * P2D renderer does not use this information.
     *
     * ( end auto-generated )
     *
     * @webref shape:curves
     * @param detail resolution of the curves
     * @see PGraphics.curve
     * @see PGraphics.curveVertex
     * @see PGraphics.curveTightness
     */
    open fun curveDetail(detail: Int) {
        graphics!!.curveDetail(detail)
    }

    /**
     * ( begin auto-generated from curveTightness.xml )
     *
     * Modifies the quality of forms created with **curve()** and
     * **curveVertex()**. The parameter **squishy** determines how the
     * curve fits to the vertex points. The value 0.0 is the default value for
     * **squishy** (this value defines the curves to be Catmull-Rom splines)
     * and the value 1.0 connects all the points with straight lines. Values
     * within the range -5.0 and 5.0 will deform the curves but will leave them
     * recognizable and as values increase in magnitude, they will continue to deform.
     *
     * ( end auto-generated )
     *
     * @webref shape:curves
     * @param tightness amount of deformation from the original vertices
     * @see PGraphics.curve
     * @see PGraphics.curveVertex
     */
    open fun curveTightness(tightness: Float) {
        graphics!!.curveTightness(tightness)
    }

    /**
     * ( begin auto-generated from curve.xml )
     *
     * Draws a curved line on the screen. The first and second parameters
     * specify the beginning control point and the last two parameters specify
     * the ending control point. The middle parameters specify the start and
     * stop of the curve. Longer curves can be created by putting a series of
     * **curve()** functions together or using **curveVertex()**. An
     * additional function called **curveTightness()** provides control for
     * the visual quality of the curve. The **curve()** function is an
     * implementation of Catmull-Rom splines. Using the 3D version requires
     * rendering with P3D (see the Environment reference for more information).
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * As of revision 0070, this function no longer doubles the first
     * and last points. The curves are a bit more boring, but it's more
     * mathematically correct, and properly mirrored in curvePoint().
     * <P>
     * Identical to typing out:</P><PRE>
     * beginShape();
     * curveVertex(x1, y1);
     * curveVertex(x2, y2);
     * curveVertex(x3, y3);
     * curveVertex(x4, y4);
     * endShape();
    </PRE> *
     *
     * @webref shape:curves
     * @param x1 coordinates for the beginning control point
     * @param y1 coordinates for the beginning control point
     * @param x2 coordinates for the first point
     * @param y2 coordinates for the first point
     * @param x3 coordinates for the second point
     * @param y3 coordinates for the second point
     * @param x4 coordinates for the ending control point
     * @param y4 coordinates for the ending control point
     * @see PGraphics.curveVertex
     * @see PGraphics.curveTightness
     * @see PGraphics.bezier
     */
    open fun curve(x1: Float, y1: Float,
              x2: Float, y2: Float,
              x3: Float, y3: Float,
              x4: Float, y4: Float) {
        graphics!!.curve(x1, y1, x2, y2, x3, y3, x4, y4)
    }

    /**
     * @param z1 coordinates for the beginning control point
     * @param z2 coordinates for the first point
     * @param z3 coordinates for the second point
     * @param z4 coordinates for the ending control point
     */
    open fun curve(x1: Float, y1: Float, z1: Float,
              x2: Float, y2: Float, z2: Float,
              x3: Float, y3: Float, z3: Float,
              x4: Float, y4: Float, z4: Float) {
        graphics!!.curve(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4)
    }

    /**
     * ( begin auto-generated from imageMode.xml )
     *
     * Modifies the location from which images draw. The default mode is
     * **imageMode(CORNER)**, which specifies the location to be the upper
     * left corner and uses the fourth and fifth parameters of **image()**
     * to set the image's width and height. The syntax
     * **imageMode(CORNERS)** uses the second and third parameters of
     * **image()** to set the location of one corner of the image and uses
     * the fourth and fifth parameters to set the opposite corner. Use
     * **imageMode(CENTER)** to draw images centered at the given x and y
     * position.<br></br>
     * <br></br>
     * The parameter to **imageMode()** must be written in ALL CAPS because
     * Processing is a case-sensitive language.
     *
     * ( end auto-generated )
     *
     * @webref image:loading_displaying
     * @param mode either CORNER, CORNERS, or CENTER
     * @see PApplet.loadImage
     * @see PImage
     *
     * @see PGraphics.image
     * @see PGraphics.background
     */
    open fun imageMode(mode: Int) {
        graphics!!.imageMode(mode)
    }

    /**
     * ( begin auto-generated from image.xml )
     *
     * Displays images to the screen. The images must be in the sketch's "data"
     * directory to load correctly. Select "Add file..." from the "Sketch" menu
     * to add the image. Processing currently works with GIF, JPEG, and Targa
     * images. The **img** parameter specifies the image to display and the
     * **x** and **y** parameters define the location of the image from
     * its upper-left corner. The image is displayed at its original size
     * unless the **width** and **height** parameters specify a different
     * size.<br></br>
     * <br></br>
     * The **imageMode()** function changes the way the parameters work. For
     * example, a call to **imageMode(CORNERS)** will change the
     * **width** and **height** parameters to define the x and y values
     * of the opposite corner of the image.<br></br>
     * <br></br>
     * The color of an image may be modified with the **tint()** function.
     * This function will maintain transparency for GIF and PNG images.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Starting with release 0124, when using the default (JAVA2D) renderer,
     * smooth() will also improve image quality of resized images.
     *
     * @webref image:loading_displaying
     * @param img the image to display
     * @param a x-coordinate of the image by default
     * @param b y-coordinate of the image by default
     * @see PApplet.loadImage
     * @see PImage
     *
     * @see PGraphics.imageMode
     * @see PGraphics.tint
     * @see PGraphics.background
     * @see PGraphics.alpha
     */
    open fun image(img: PImage?, a: Float, b: Float) {
        graphics!!.image(img!!, a, b)
    }

    /**
     * @param c width to display the image by default
     * @param d height to display the image by default
     */
    open fun image(img: PImage?, a: Float, b: Float, c: Float, d: Float) {
        graphics!!.image(img!!, a, b, c, d)
    }

    /**
     * Draw an image(), also specifying u/v coordinates.
     * In this method, the  u, v coordinates are always based on image space
     * location, regardless of the current textureMode().
     *
     * @nowebref
     */
    open fun image(img: PImage?,
              a: Float, b: Float, c: Float, d: Float,
              u1: Int, v1: Int, u2: Int, v2: Int) {
        graphics!!.image(img!!, a, b, c, d, u1, v1, u2, v2)
    }

    /**
     * ( begin auto-generated from shapeMode.xml )
     *
     * Modifies the location from which shapes draw. The default mode is
     * **shapeMode(CORNER)**, which specifies the location to be the upper
     * left corner of the shape and uses the third and fourth parameters of
     * **shape()** to specify the width and height. The syntax
     * **shapeMode(CORNERS)** uses the first and second parameters of
     * **shape()** to set the location of one corner and uses the third and
     * fourth parameters to set the opposite corner. The syntax
     * **shapeMode(CENTER)** draws the shape from its center point and uses
     * the third and forth parameters of **shape()** to specify the width
     * and height. The parameter must be written in "ALL CAPS" because
     * Processing is a case sensitive language.
     *
     * ( end auto-generated )
     *
     * @webref shape:loading_displaying
     * @param mode either CORNER, CORNERS, CENTER
     * @see PShape
     *
     * @see PGraphics.shape
     * @see PGraphics.rectMode
     */
    open fun shapeMode(mode: Int) {
        graphics!!.shapeMode(mode)
    }

    open fun shape(shape: PShape?) {
        graphics!!.shape(shape!!)
    }

    /**
     * ( begin auto-generated from shape.xml )
     *
     * Displays shapes to the screen. The shapes must be in the sketch's "data"
     * directory to load correctly. Select "Add file..." from the "Sketch" menu
     * to add the shape. Processing currently works with SVG shapes only. The
     * **sh** parameter specifies the shape to display and the **x** and
     * **y** parameters define the location of the shape from its upper-left
     * corner. The shape is displayed at its original size unless the
     * **width** and **height** parameters specify a different size. The
     * **shapeMode()** function changes the way the parameters work. A call
     * to **shapeMode(CORNERS)**, for example, will change the width and
     * height parameters to define the x and y values of the opposite corner of
     * the shape.
     * <br></br><br></br>
     * Note complex shapes may draw awkwardly with P3D. This renderer does not
     * yet support shapes that have holes or complicated breaks.
     *
     * ( end auto-generated )
     *
     * @webref shape:loading_displaying
     * @param shape the shape to display
     * @param x x-coordinate of the shape
     * @param y y-coordinate of the shape
     * @see PShape
     *
     * @see PApplet.loadShape
     * @see PGraphics.shapeMode
     */
    open fun shape(shape: PShape?, x: Float, y: Float) {
        graphics!!.shape(shape!!, x, y)
    }

    /**
     * @param a x-coordinate of the shape
     * @param b y-coordinate of the shape
     * @param c width to display the shape
     * @param d height to display the shape
     */
    open fun shape(shape: PShape?, a: Float, b: Float, c: Float, d: Float) {
        graphics!!.shape(shape!!, a, b, c, d)
    }

    open fun textAlign(alignX: Int) {
        graphics!!.textAlign(alignX)
    }

    /**
     * ( begin auto-generated from textAlign.xml )
     *
     * Sets the current alignment for drawing text. The parameters LEFT,
     * CENTER, and RIGHT set the display characteristics of the letters in
     * relation to the values for the **x** and **y** parameters of the
     * **text()** function.
     * <br></br> <br></br>
     * In Processing 0125 and later, an optional second parameter can be used
     * to vertically align the text. BASELINE is the default, and the vertical
     * alignment will be reset to BASELINE if the second parameter is not used.
     * The TOP and CENTER parameters are straightforward. The BOTTOM parameter
     * offsets the line based on the current **textDescent()**. For multiple
     * lines, the final line will be aligned to the bottom, with the previous
     * lines appearing above it.
     * <br></br> <br></br>
     * When using **text()** with width and height parameters, BASELINE is
     * ignored, and treated as TOP. (Otherwise, text would by default draw
     * outside the box, since BASELINE is the default setting. BASELINE is not
     * a useful drawing mode for text drawn in a rectangle.)
     * <br></br> <br></br>
     * The vertical alignment is based on the value of **textAscent()**,
     * which many fonts do not specify correctly. It may be necessary to use a
     * hack and offset by a few pixels by hand so that the offset looks
     * correct. To do this as less of a hack, use some percentage of
     * **textAscent()** or **textDescent()** so that the hack works even
     * if you change the size of the font.
     *
     * ( end auto-generated )
     *
     * @webref typography:attributes
     * @param alignX horizontal alignment, either LEFT, CENTER, or RIGHT
     * @param alignY vertical alignment, either TOP, BOTTOM, CENTER, or BASELINE
     * @see PApplet.loadFont
     * @see PFont
     *
     * @see PGraphics.text
     * @see PGraphics.textSize
     * @see PGraphics.textAscent
     * @see PGraphics.textDescent
     */
    open fun textAlign(alignX: Int, alignY: Int) {
        graphics!!.textAlign(alignX, alignY)
    }

    /**
     * ( begin auto-generated from textAscent.xml )
     *
     * Returns ascent of the current font at its current size. This information
     * is useful for determining the height of the font above the baseline. For
     * example, adding the **textAscent()** and **textDescent()** values
     * will give you the total height of the line.
     *
     * ( end auto-generated )
     *
     * @webref typography:metrics
     * @see PGraphics.textDescent
     */
    open fun textAscent(): Float {
        return graphics!!.textAscent()
    }

    /**
     * ( begin auto-generated from textDescent.xml )
     *
     * Returns descent of the current font at its current size. This
     * information is useful for determining the height of the font below the
     * baseline. For example, adding the **textAscent()** and
     * **textDescent()** values will give you the total height of the line.
     *
     * ( end auto-generated )
     *
     * @webref typography:metrics
     * @see PGraphics.textAscent
     */
    open fun textDescent(): Float {
        return graphics!!.textDescent()
    }

    /**
     * ( begin auto-generated from textFont.xml )
     *
     * Sets the current font that will be drawn with the **text()**
     * function. Fonts must be loaded with **loadFont()** before it can be
     * used. This font will be used in all subsequent calls to the
     * **text()** function. If no **size** parameter is input, the font
     * will appear at its original size (the size it was created at with the
     * "Create Font..." tool) until it is changed with **textSize()**. <br></br> <br></br> Because fonts are usually bitmaped, you should create fonts at
     * the sizes that will be used most commonly. Using **textFont()**
     * without the size parameter will result in the cleanest-looking text. <br></br><br></br> With the default (JAVA2D) and PDF renderers, it's also possible
     * to enable the use of native fonts via the command
     * **hint(ENABLE_NATIVE_FONTS)**. This will produce vector text in
     * JAVA2D sketches and PDF output in cases where the vector data is
     * available: when the font is still installed, or the font is created via
     * the **createFont()** function (rather than the Create Font tool).
     *
     * ( end auto-generated )
     *
     * @webref typography:loading_displaying
     * @param which any variable of the type PFont
     * @see PApplet.createFont
     * @see PApplet.loadFont
     * @see PFont
     *
     * @see PGraphics.text
     * @see PGraphics.textSize
     */
    open fun textFont(which: PFont?) {
        graphics!!.textFont(which)
    }

    /**
     * @param size the size of the letters in units of pixels
     */
    open fun textFont(which: PFont?, size: Float) {
        graphics!!.textFont(which, size)
    }

    /**
     * ( begin auto-generated from textLeading.xml )
     *
     * Sets the spacing between lines of text in units of pixels. This setting
     * will be used in all subsequent calls to the **text()** function.
     *
     * ( end auto-generated )
     *
     * @webref typography:attributes
     * @param leading the size in pixels for spacing between lines
     * @see PApplet.loadFont
     * @see PFont.PFont
     *
     * @see PGraphics.text
     * @see PGraphics.textFont
     * @see PGraphics.textSize
     */
    open fun textLeading(leading: Float) {
        graphics!!.textLeading(leading)
    }

    /**
     * ( begin auto-generated from textMode.xml )
     *
     * Sets the way text draws to the screen. In the default configuration, the
     * **MODEL** mode, it's possible to rotate, scale, and place letters in
     * two and three dimensional space.<br></br>
     * <br></br>
     * The **SHAPE** mode draws text using the the glyph outlines of
     * individual characters rather than as textures. This mode is only
     * supported with the **PDF** and **P3D** renderer settings. With the
     * **PDF** renderer, you must call **textMode(SHAPE)** before any
     * other drawing occurs. If the outlines are not available, then
     * **textMode(SHAPE)** will be ignored and **textMode(MODEL)** will
     * be used instead.<br></br>
     * <br></br>
     * The **textMode(SHAPE)** option in **P3D** can be combined with
     * **beginRaw()** to write vector-accurate text to 2D and 3D output
     * files, for instance **DXF** or **PDF**. The **SHAPE** mode is
     * not currently optimized for **P3D**, so if recording shape data, use
     * **textMode(MODEL)** until you're ready to capture the geometry with **beginRaw()**.
     *
     * ( end auto-generated )
     *
     * @webref typography:attributes
     * @param mode either MODEL or SHAPE
     * @see PApplet.loadFont
     * @see PFont.PFont
     *
     * @see PGraphics.text
     * @see PGraphics.textFont
     * @see PGraphics.beginRaw
     * @see PApplet.createFont
     */
    open fun textMode(mode: Int) {
        graphics!!.textMode(mode)
    }

    /**
     * ( begin auto-generated from textSize.xml )
     *
     * Sets the current font size. This size will be used in all subsequent
     * calls to the **text()** function. Font size is measured in units of pixels.
     *
     * ( end auto-generated )
     *
     * @webref typography:attributes
     * @param size the size of the letters in units of pixels
     * @see PApplet.loadFont
     * @see PFont.PFont
     *
     * @see PGraphics.text
     * @see PGraphics.textFont
     */
    open fun textSize(size: Float) {
        graphics!!.textSize(size)
    }

    /**
     * @param c the character to measure
     */
    open fun textWidth(c: Char): Float {
        return graphics!!.textWidth(c)
    }

    /**
     * ( begin auto-generated from textWidth.xml )
     *
     * Calculates and returns the width of any character or text string.
     *
     * ( end auto-generated )
     *
     * @webref typography:attributes
     * @param str the String of characters to measure
     * @see PApplet.loadFont
     * @see PFont.PFont
     *
     * @see PGraphics.text
     * @see PGraphics.textFont
     * @see PGraphics.textSize
     */
    open fun textWidth(str: String?): Float {
        return graphics!!.textWidth(str!!)
    }

    /**
     * @nowebref
     */
    open fun textWidth(chars: CharArray?, start: Int, length: Int): Float {
        return graphics!!.textWidth(chars!!, start, length)
    }

    /**
     * ( begin auto-generated from text.xml )
     *
     * Draws text to the screen. Displays the information specified in the
     * **data** or **stringdata** parameters on the screen in the
     * position specified by the **x** and **y** parameters and the
     * optional **z** parameter. A default font will be used unless a font
     * is set with the **textFont()** function. Change the color of the text
     * with the **fill()** function. The text displays in relation to the
     * **textAlign()** function, which gives the option to draw to the left,
     * right, and center of the coordinates.
     * <br></br><br></br>
     * The **x2** and **y2** parameters define a rectangular area to
     * display within and may only be used with string data. For text drawn
     * inside a rectangle, the coordinates are interpreted based on the current
     * **rectMode()** setting.
     *
     * ( end auto-generated )
     *
     * @webref typography:loading_displaying
     * @param c the alphanumeric character to be displayed
     * @param x x-coordinate of text
     * @param y y-coordinate of text
     * @see PGraphics.textAlign
     * @see PGraphics.textFont
     * @see PGraphics.textMode
     * @see PGraphics.textSize
     * @see PGraphics.textLeading
     * @see PGraphics.textWidth
     * @see PGraphics.textAscent
     * @see PGraphics.textDescent
     * @see PGraphics.rectMode
     * @see PGraphics.fill
     * @see_external String
     */
    open fun text(c: Char, x: Float, y: Float) {
        graphics!!.text(c, x, y)
    }

    /**
     * @param z z-coordinate of text
     */
    open fun text(c: Char, x: Float, y: Float, z: Float) {
        graphics!!.text(c, x, y, z)
    }

    /**
     * <h3>Advanced</h3>
     * Draw a chunk of text.
     * Newlines that are \n (Unix newline or linefeed char, ascii 10)
     * are honored, but \r (carriage return, Windows and Mac OS) are
     * ignored.
     */
    open fun text(str: String?, x: Float, y: Float) {
        graphics!!.text(str!!, x, y)
    }

    /**
     * <h3>Advanced</h3>
     * Method to draw text from an array of chars. This method will usually be
     * more efficient than drawing from a String object, because the String will
     * not be converted to a char array before drawing.
     * @param chars the alphanumberic symbols to be displayed
     * @param start array index at which to start writing characters
     * @param stop array index at which to stop writing characters
     */
    open fun text(chars: CharArray?, start: Int, stop: Int, x: Float, y: Float) {
        graphics!!.text(chars!!, start, stop, x, y)
    }

    /**
     * Same as above but with a z coordinate.
     */
    open fun text(str: String?, x: Float, y: Float, z: Float) {
        graphics!!.text(str!!, x, y, z)
    }

    open fun text(chars: CharArray?, start: Int, stop: Int,
             x: Float, y: Float, z: Float) {
        graphics!!.text(chars!!, start, stop, x, y, z)
    }

    /**
     * <h3>Advanced</h3>
     * Draw text in a box that is constrained to a particular size.
     * The current rectMode() determines what the coordinates mean
     * (whether x1/y1/x2/y2 or x/y/w/h).
     * <P></P>
     * Note that the x,y coords of the start of the box
     * will align with the *ascent* of the text, not the baseline,
     * as is the case for the other text() functions.
     * <P></P>
     * Newlines that are \n (Unix newline or linefeed char, ascii 10)
     * are honored, and \r (carriage return, Windows and Mac OS) are
     * ignored.
     *
     * @param x1 by default, the x-coordinate of text, see rectMode() for more info
     * @param y1 by default, the y-coordinate of text, see rectMode() for more info
     * @param x2 by default, the width of the text box, see rectMode() for more info
     * @param y2 by default, the height of the text box, see rectMode() for more info
     */
    open fun text(str: String?, x1: Float, y1: Float, x2: Float, y2: Float) {
        graphics!!.text(str!!, x1, y1, x2, y2)
    }

    open fun text(num: Int, x: Float, y: Float) {
        graphics!!.text(num, x, y)
    }

    open fun text(num: Int, x: Float, y: Float, z: Float) {
        graphics!!.text(num, x, y, z)
    }

    /**
     * This does a basic number formatting, to avoid the
     * generally ugly appearance of printing floats.
     * Users who want more control should use their own nf() cmmand,
     * or if they want the long, ugly version of float,
     * use String.valueOf() to convert the float to a String first.
     *
     * @param num the numeric value to be displayed
     */
    open fun text(num: Float, x: Float, y: Float) {
        graphics!!.text(num, x, y)
    }

    open fun text(num: Float, x: Float, y: Float, z: Float) {
        graphics!!.text(num, x, y, z)
    }

    /**
     * ( begin auto-generated from push.xml )
     *
     * The **push()** function saves the current drawing style
     * settings and transformations, while **pop()** restores these
     * settings. Note that these functions are always used together.
     * They allow you to change the style and transformation settings
     * and later return to what you had. When a new state is started
     * with push(), it builds on the current style and transform
     * information.<br></br>
     * <br></br>
     * **push()** stores information related to the current
     * transformation state and style settings controlled by the
     * following functions: **rotate()**, **translate()**,
     * **scale()**, **fill()**, **stroke()**, **tint()**,
     * **strokeWeight()**, **strokeCap()**, **strokeJoin()**,
     * **imageMode()**, **rectMode()**, **ellipseMode()**,
     * **colorMode()**, **textAlign()**, **textFont()**,
     * **textMode()**, **textSize()**, **textLeading()**.<br></br>
     * <br></br>
     * The **push()** and **pop()** functions were added with
     * Processing 3.5. They can be used in place of **pushMatrix()**,
     * **popMatrix()**, **pushStyles()**, and **popStyles()**.
     * The difference is that push() and pop() control both the
     * transformations (rotate, scale, translate) and the drawing styles
     * at the same time.
     *
     * ( end auto-generated )
     *
     * @webref structure
     * @see PGraphics.pop
     */
    open fun push() {
        graphics!!.push()
    }

    /**
     * ( begin auto-generated from pop.xml )
     *
     * The **pop()** function restores the previous drawing style
     * settings and transformations after **push()** has changed them.
     * Note that these functions are always used together. They allow
     * you to change the style and transformation settings and later
     * return to what you had. When a new state is started with push(),
     * it builds on the current style and transform information.<br></br>
     * <br></br>
     * <br></br>
     * **push()** stores information related to the current
     * transformation state and style settings controlled by the
     * following functions: **rotate()**, **translate()**,
     * **scale()**, **fill()**, **stroke()**, **tint()**,
     * **strokeWeight()**, **strokeCap()**, **strokeJoin()**,
     * **imageMode()**, **rectMode()**, **ellipseMode()**,
     * **colorMode()**, **textAlign()**, **textFont()**,
     * **textMode()**, **textSize()**, **textLeading()**.<br></br>
     * <br></br>
     * The **push()** and **pop()** functions were added with
     * Processing 3.5. They can be used in place of **pushMatrix()**,
     * **popMatrix()**, **pushStyles()**, and **popStyles()**.
     * The difference is that push() and pop() control both the
     * transformations (rotate, scale, translate) and the drawing styles
     * at the same time.
     *
     * ( end auto-generated )
     *
     * @webref structure
     * @see PGraphics.push
     */
    open fun pop() {
        graphics!!.pop()
    }

    /**
     * ( begin auto-generated from pushMatrix.xml )
     *
     * Pushes the current transformation matrix onto the matrix stack.
     * Understanding **pushMatrix()** and **popMatrix()** requires
     * understanding the concept of a matrix stack. The **pushMatrix()**
     * function saves the current coordinate system to the stack and
     * **popMatrix()** restores the prior coordinate system.
     * **pushMatrix()** and **popMatrix()** are used in conjuction with
     * the other transformation functions and may be embedded to control the
     * scope of the transformations.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @see PGraphics.popMatrix
     * @see PGraphics.translate
     * @see PGraphics.scale
     * @see PGraphics.rotate
     * @see PGraphics.rotateX
     * @see PGraphics.rotateY
     * @see PGraphics.rotateZ
     */
    open fun pushMatrix() {
        graphics!!.pushMatrix()
    }

    /**
     * ( begin auto-generated from popMatrix.xml )
     *
     * Pops the current transformation matrix off the matrix stack.
     * Understanding pushing and popping requires understanding the concept of
     * a matrix stack. The **pushMatrix()** function saves the current
     * coordinate system to the stack and **popMatrix()** restores the prior
     * coordinate system. **pushMatrix()** and **popMatrix()** are used
     * in conjuction with the other transformation functions and may be
     * embedded to control the scope of the transformations.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @see PGraphics.pushMatrix
     */
    open fun popMatrix() {
        graphics!!.popMatrix()
    }

    /**
     * ( begin auto-generated from translate.xml )
     *
     * Specifies an amount to displace objects within the display window. The
     * **x** parameter specifies left/right translation, the **y**
     * parameter specifies up/down translation, and the **z** parameter
     * specifies translations toward/away from the screen. Using this function
     * with the **z** parameter requires using P3D as a parameter in
     * combination with size as shown in the above example. Transformations
     * apply to everything that happens after and subsequent calls to the
     * function accumulates the effect. For example, calling **translate(50,
     * 0)** and then **translate(20, 0)** is the same as **translate(70,
     * 0)**. If **translate()** is called within **draw()**, the
     * transformation is reset when the loop begins again. This function can be
     * further controlled by the **pushMatrix()** and **popMatrix()**.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param x left/right translation
     * @param y up/down translation
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.rotate
     * @see PGraphics.rotateX
     * @see PGraphics.rotateY
     * @see PGraphics.rotateZ
     * @see PGraphics.scale
     */
    open fun translate(x: Float, y: Float) {
        graphics!!.translate(x, y)
    }

    /**
     * @param z forward/backward translation
     */
    open fun translate(x: Float, y: Float, z: Float) {
        graphics!!.translate(x, y, z)
    }

    /**
     * ( begin auto-generated from rotate.xml )
     *
     * Rotates a shape the amount specified by the **angle** parameter.
     * Angles should be specified in radians (values from 0 to TWO_PI) or
     * converted to radians with the **radians()** function.
     * <br></br> <br></br>
     * Objects are always rotated around their relative position to the origin
     * and positive numbers rotate objects in a clockwise direction.
     * Transformations apply to everything that happens after and subsequent
     * calls to the function accumulates the effect. For example, calling
     * **rotate(HALF_PI)** and then **rotate(HALF_PI)** is the same as
     * **rotate(PI)**. All tranformations are reset when **draw()**
     * begins again.
     * <br></br> <br></br>
     * Technically, **rotate()** multiplies the current transformation
     * matrix by a rotation matrix. This function can be further controlled by
     * the **pushMatrix()** and **popMatrix()**.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param angle angle of rotation specified in radians
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.rotateX
     * @see PGraphics.rotateY
     * @see PGraphics.rotateZ
     * @see PGraphics.scale
     * @see PApplet.radians
     */
    open fun rotate(angle: Float) {
        graphics!!.rotate(angle)
    }

    /**
     * ( begin auto-generated from rotateX.xml )
     *
     * Rotates a shape around the x-axis the amount specified by the
     * **angle** parameter. Angles should be specified in radians (values
     * from 0 to PI*2) or converted to radians with the **radians()**
     * function. Objects are always rotated around their relative position to
     * the origin and positive numbers rotate objects in a counterclockwise
     * direction. Transformations apply to everything that happens after and
     * subsequent calls to the function accumulates the effect. For example,
     * calling **rotateX(PI/2)** and then **rotateX(PI/2)** is the same
     * as **rotateX(PI)**. If **rotateX()** is called within the
     * **draw()**, the transformation is reset when the loop begins again.
     * This function requires using P3D as a third parameter to **size()**
     * as shown in the example above.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param angle angle of rotation specified in radians
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.rotate
     * @see PGraphics.rotateY
     * @see PGraphics.rotateZ
     * @see PGraphics.scale
     * @see PGraphics.translate
     */
    open fun rotateX(angle: Float) {
        graphics!!.rotateX(angle)
    }

    /**
     * ( begin auto-generated from rotateY.xml )
     *
     * Rotates a shape around the y-axis the amount specified by the
     * **angle** parameter. Angles should be specified in radians (values
     * from 0 to PI*2) or converted to radians with the **radians()**
     * function. Objects are always rotated around their relative position to
     * the origin and positive numbers rotate objects in a counterclockwise
     * direction. Transformations apply to everything that happens after and
     * subsequent calls to the function accumulates the effect. For example,
     * calling **rotateY(PI/2)** and then **rotateY(PI/2)** is the same
     * as **rotateY(PI)**. If **rotateY()** is called within the
     * **draw()**, the transformation is reset when the loop begins again.
     * This function requires using P3D as a third parameter to **size()**
     * as shown in the examples above.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param angle angle of rotation specified in radians
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.rotate
     * @see PGraphics.rotateX
     * @see PGraphics.rotateZ
     * @see PGraphics.scale
     * @see PGraphics.translate
     */
    open fun rotateY(angle: Float) {
        graphics!!.rotateY(angle)
    }

    /**
     * ( begin auto-generated from rotateZ.xml )
     *
     * Rotates a shape around the z-axis the amount specified by the
     * **angle** parameter. Angles should be specified in radians (values
     * from 0 to PI*2) or converted to radians with the **radians()**
     * function. Objects are always rotated around their relative position to
     * the origin and positive numbers rotate objects in a counterclockwise
     * direction. Transformations apply to everything that happens after and
     * subsequent calls to the function accumulates the effect. For example,
     * calling **rotateZ(PI/2)** and then **rotateZ(PI/2)** is the same
     * as **rotateZ(PI)**. If **rotateZ()** is called within the
     * **draw()**, the transformation is reset when the loop begins again.
     * This function requires using P3D as a third parameter to **size()**
     * as shown in the examples above.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param angle angle of rotation specified in radians
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.rotate
     * @see PGraphics.rotateX
     * @see PGraphics.rotateY
     * @see PGraphics.scale
     * @see PGraphics.translate
     */
    open fun rotateZ(angle: Float) {
        graphics!!.rotateZ(angle)
    }

    /**
     * <h3>Advanced</h3>
     * Rotate about a vector in space. Same as the glRotatef() function.
     * @nowebref
     * @param x
     * @param y
     * @param z
     */
    open fun rotate(angle: Float, x: Float, y: Float, z: Float) {
        graphics!!.rotate(angle, x, y, z)
    }

    /**
     * ( begin auto-generated from scale.xml )
     *
     * Increases or decreases the size of a shape by expanding and contracting
     * vertices. Objects always scale from their relative origin to the
     * coordinate system. Scale values are specified as decimal percentages.
     * For example, the function call **scale(2.0)** increases the dimension
     * of a shape by 200%. Transformations apply to everything that happens
     * after and subsequent calls to the function multiply the effect. For
     * example, calling **scale(2.0)** and then **scale(1.5)** is the
     * same as **scale(3.0)**. If **scale()** is called within
     * **draw()**, the transformation is reset when the loop begins again.
     * Using this fuction with the **z** parameter requires using P3D as a
     * parameter for **size()** as shown in the example above. This function
     * can be further controlled by **pushMatrix()** and **popMatrix()**.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param s percentage to scale the object
     * @see PGraphics.pushMatrix
     * @see PGraphics.popMatrix
     * @see PGraphics.translate
     * @see PGraphics.rotate
     * @see PGraphics.rotateX
     * @see PGraphics.rotateY
     * @see PGraphics.rotateZ
     */
    open fun scale(s: Float) {
        graphics!!.scale(s)
    }

    /**
     * <h3>Advanced</h3>
     * Scale in X and Y. Equivalent to scale(sx, sy, 1).
     *
     * Not recommended for use in 3D, because the z-dimension is just
     * scaled by 1, since there's no way to know what else to scale it by.
     *
     * @param x percentage to scale the object in the x-axis
     * @param y percentage to scale the object in the y-axis
     */
    open fun scale(x: Float, y: Float) {
        graphics!!.scale(x, y)
    }

    /**
     * @param z percentage to scale the object in the z-axis
     */
    open fun scale(x: Float, y: Float, z: Float) {
        graphics!!.scale(x, y, z)
    }

    /**
     * ( begin auto-generated from shearX.xml )
     *
     * Shears a shape around the x-axis the amount specified by the
     * **angle** parameter. Angles should be specified in radians (values
     * from 0 to PI*2) or converted to radians with the **radians()**
     * function. Objects are always sheared around their relative position to
     * the origin and positive numbers shear objects in a clockwise direction.
     * Transformations apply to everything that happens after and subsequent
     * calls to the function accumulates the effect. For example, calling
     * **shearX(PI/2)** and then **shearX(PI/2)** is the same as
     * **shearX(PI)**. If **shearX()** is called within the
     * **draw()**, the transformation is reset when the loop begins again.
     * <br></br> <br></br>
     * Technically, **shearX()** multiplies the current transformation
     * matrix by a rotation matrix. This function can be further controlled by
     * the **pushMatrix()** and **popMatrix()** functions.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param angle angle of shear specified in radians
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.shearY
     * @see PGraphics.scale
     * @see PGraphics.translate
     * @see PApplet.radians
     */
    open fun shearX(angle: Float) {
        graphics!!.shearX(angle)
    }

    /**
     * ( begin auto-generated from shearY.xml )
     *
     * Shears a shape around the y-axis the amount specified by the
     * **angle** parameter. Angles should be specified in radians (values
     * from 0 to PI*2) or converted to radians with the **radians()**
     * function. Objects are always sheared around their relative position to
     * the origin and positive numbers shear objects in a clockwise direction.
     * Transformations apply to everything that happens after and subsequent
     * calls to the function accumulates the effect. For example, calling
     * **shearY(PI/2)** and then **shearY(PI/2)** is the same as
     * **shearY(PI)**. If **shearY()** is called within the
     * **draw()**, the transformation is reset when the loop begins again.
     * <br></br> <br></br>
     * Technically, **shearY()** multiplies the current transformation
     * matrix by a rotation matrix. This function can be further controlled by
     * the **pushMatrix()** and **popMatrix()** functions.
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @param angle angle of shear specified in radians
     * @see PGraphics.popMatrix
     * @see PGraphics.pushMatrix
     * @see PGraphics.shearX
     * @see PGraphics.scale
     * @see PGraphics.translate
     * @see PApplet.radians
     */
    open fun shearY(angle: Float) {
        graphics!!.shearY(angle)
    }

    /**
     * ( begin auto-generated from resetMatrix.xml )
     *
     * Replaces the current matrix with the identity matrix. The equivalent
     * function in OpenGL is glLoadIdentity().
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @see PGraphics.pushMatrix
     * @see PGraphics.popMatrix
     * @see PGraphics.applyMatrix
     * @see PGraphics.printMatrix
     */
    open fun resetMatrix() {
        graphics!!.resetMatrix()
    }

    /**
     * ( begin auto-generated from applyMatrix.xml )
     *
     * Multiplies the current matrix by the one specified through the
     * parameters. This is very slow because it will try to calculate the
     * inverse of the transform, so avoid it whenever possible. The equivalent
     * function in OpenGL is glMultMatrix().
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @source
     * @see PGraphics.pushMatrix
     * @see PGraphics.popMatrix
     * @see PGraphics.resetMatrix
     * @see PGraphics.printMatrix
     */
    open fun applyMatrix(source: PMatrix?) {
        graphics!!.applyMatrix(source)
    }

    open fun applyMatrix(source: PMatrix2D?) {
        graphics!!.applyMatrix(source)
    }

    /**
     * @param n00 numbers which define the 4x4 matrix to be multiplied
     * @param n01 numbers which define the 4x4 matrix to be multiplied
     * @param n02 numbers which define the 4x4 matrix to be multiplied
     * @param n10 numbers which define the 4x4 matrix to be multiplied
     * @param n11 numbers which define the 4x4 matrix to be multiplied
     * @param n12 numbers which define the 4x4 matrix to be multiplied
     */
    open fun applyMatrix(n00: Float, n01: Float, n02: Float,
                    n10: Float, n11: Float, n12: Float) {
        graphics!!.applyMatrix(n00, n01, n02, n10, n11, n12)
    }

    open fun applyMatrix(source: PMatrix3D?) {
        graphics!!.applyMatrix(source)
    }

    /**
     * @param n03 numbers which define the 4x4 matrix to be multiplied
     * @param n13 numbers which define the 4x4 matrix to be multiplied
     * @param n20 numbers which define the 4x4 matrix to be multiplied
     * @param n21 numbers which define the 4x4 matrix to be multiplied
     * @param n22 numbers which define the 4x4 matrix to be multiplied
     * @param n23 numbers which define the 4x4 matrix to be multiplied
     * @param n30 numbers which define the 4x4 matrix to be multiplied
     * @param n31 numbers which define the 4x4 matrix to be multiplied
     * @param n32 numbers which define the 4x4 matrix to be multiplied
     * @param n33 numbers which define the 4x4 matrix to be multiplied
     */
    open fun applyMatrix(n00: Float, n01: Float, n02: Float, n03: Float,
                    n10: Float, n11: Float, n12: Float, n13: Float,
                    n20: Float, n21: Float, n22: Float, n23: Float,
                    n30: Float, n31: Float, n32: Float, n33: Float) {
        graphics!!.applyMatrix(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22, n23, n30, n31, n32, n33)
    }

    /**
     * Set the current transformation matrix to the contents of another.
     */
    // todo - arg @ Nullable
    // todo - nullable return-type
    var matrix: PMatrix?
        get() = graphics!!.getMatrix()
        set(source) {
            graphics!!.setMatrix(source)
        }

    /**
     * Copy the current transformation matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    // todo - both arg and return-type are Nullable
    open fun getMatrix(target: PMatrix2D?): PMatrix2D? {
        return graphics!!.getMatrix(target)
    }

    /**
     * Copy the current transformation matrix into the specified target.
     * Pass in null to create a new matrix.
     */
    // todo - both arg and return-type are Nullable
    open fun getMatrix(target: PMatrix3D?): PMatrix3D? {
        return graphics!!.getMatrix(target)
    }

    /**
     * Set the current transformation to the contents of the specified source.
     */
    open fun setMatrix(source: PMatrix2D?) {
        graphics!!.setMatrix(source)
    }

    /**
     * Set the current transformation to the contents of the specified source.
     */
    open fun setMatrix(source: PMatrix3D?) {
        graphics!!.setMatrix(source)
    }

    /**
     * ( begin auto-generated from printMatrix.xml )
     *
     * Prints the current matrix to the Console (the text window at the bottom
     * of Processing).
     *
     * ( end auto-generated )
     *
     * @webref transform
     * @see PGraphics.pushMatrix
     * @see PGraphics.popMatrix
     * @see PGraphics.resetMatrix
     * @see PGraphics.applyMatrix
     */
    open fun printMatrix() {
        graphics!!.printMatrix()
    }

    /**
     * ( begin auto-generated from beginCamera.xml )
     *
     * The **beginCamera()** and **endCamera()** functions enable
     * advanced customization of the camera space. The functions are useful if
     * you want to more control over camera movement, however for most users,
     * the **camera()** function will be sufficient.<br></br><br></br>The camera
     * functions will replace any transformations (such as **rotate()** or
     * **translate()**) that occur before them in **draw()**, but they
     * will not automatically replace the camera transform itself. For this
     * reason, camera functions should be placed at the beginning of
     * **draw()** (so that transformations happen afterwards), and the
     * **camera()** function can be used after **beginCamera()** if you
     * want to reset the camera before applying transformations.<br></br><br></br>This function sets the matrix mode to the camera matrix so calls such
     * as **translate()**, **rotate()**, applyMatrix() and resetMatrix()
     * affect the camera. **beginCamera()** should always be used with a
     * following **endCamera()** and pairs of **beginCamera()** and
     * **endCamera()** cannot be nested.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     * @see PGraphics.camera
     * @see PGraphics.endCamera
     * @see PGraphics.applyMatrix
     * @see PGraphics.resetMatrix
     * @see PGraphics.translate
     * @see PGraphics.scale
     */
    open fun beginCamera() {
        graphics!!.beginCamera()
    }

    /**
     * ( begin auto-generated from endCamera.xml )
     *
     * The **beginCamera()** and **endCamera()** functions enable
     * advanced customization of the camera space. Please see the reference for
     * **beginCamera()** for a description of how the functions are used.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     * @see PGraphics.beginCamera
     * @see PGraphics.camera
     */
    open fun endCamera() {
        graphics!!.endCamera()
    }

    /**
     * ( begin auto-generated from camera.xml )
     *
     * Sets the position of the camera through setting the eye position, the
     * center of the scene, and which axis is facing upward. Moving the eye
     * position and the direction it is pointing (the center of the scene)
     * allows the images to be seen from different angles. The version without
     * any parameters sets the camera to the default position, pointing to the
     * center of the display window with the Y axis as up. The default values
     * are **camera(width/2.0, height/2.0, (height/2.0) / tan(PI*30.0 /
     * 180.0), width/2.0, height/2.0, 0, 0, 1, 0)**. This function is similar
     * to **gluLookAt()** in OpenGL, but it first clears the current camera settings.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     * @see PGraphics.beginCamera
     * @see PGraphics.endCamera
     * @see PGraphics.frustum
     */
    open fun camera() {
        graphics!!.camera()
    }

    /**
     * @param eyeX x-coordinate for the eye
     * @param eyeY y-coordinate for the eye
     * @param eyeZ z-coordinate for the eye
     * @param centerX x-coordinate for the center of the scene
     * @param centerY y-coordinate for the center of the scene
     * @param centerZ z-coordinate for the center of the scene
     * @param upX usually 0.0, 1.0, or -1.0
     * @param upY usually 0.0, 1.0, or -1.0
     * @param upZ usually 0.0, 1.0, or -1.0
     */
    open fun camera(eyeX: Float, eyeY: Float, eyeZ: Float,
               centerX: Float, centerY: Float, centerZ: Float,
               upX: Float, upY: Float, upZ: Float) {
        graphics!!.camera(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
    }

    /**
     * ( begin auto-generated from printCamera.xml )
     *
     * Prints the current camera matrix to the Console (the text window at the
     * bottom of Processing).
     *
     * ( end auto-generated )
     * @webref lights_camera:camera
     * @see PGraphics.camera
     */
    open fun printCamera() {
        graphics!!.printCamera()
    }

    /**
     * ( begin auto-generated from ortho.xml )
     *
     * Sets an orthographic projection and defines a parallel clipping volume.
     * All objects with the same dimension appear the same size, regardless of
     * whether they are near or far from the camera. The parameters to this
     * function specify the clipping volume where left and right are the
     * minimum and maximum x values, top and bottom are the minimum and maximum
     * y values, and near and far are the minimum and maximum z values. If no
     * parameters are given, the default is used: ortho(0, width, 0, height,
     * -10, 10).
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     */
    open fun ortho() {
        graphics!!.ortho()
    }

    /**
     * @param left left plane of the clipping volume
     * @param right right plane of the clipping volume
     * @param bottom bottom plane of the clipping volume
     * @param top top plane of the clipping volume
     */
    open fun ortho(left: Float, right: Float,
              bottom: Float, top: Float) {
        graphics!!.ortho(left, right, bottom, top)
    }

    /**
     * @param near maximum distance from the origin to the viewer
     * @param far maximum distance from the origin away from the viewer
     */
    open fun ortho(left: Float, right: Float,
              bottom: Float, top: Float,
              near: Float, far: Float) {
        graphics!!.ortho(left, right, bottom, top, near, far)
    }

    /**
     * ( begin auto-generated from perspective.xml )
     *
     * Sets a perspective projection applying foreshortening, making distant
     * objects appear smaller than closer ones. The parameters define a viewing
     * volume with the shape of truncated pyramid. Objects near to the front of
     * the volume appear their actual size, while farther objects appear
     * smaller. This projection simulates the perspective of the world more
     * accurately than orthographic projection. The version of perspective
     * without parameters sets the default perspective and the version with
     * four parameters allows the programmer to set the area precisely. The
     * default values are: perspective(PI/3.0, width/height, cameraZ/10.0,
     * cameraZ*10.0) where cameraZ is ((height/2.0) / tan(PI*60.0/360.0));
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     */
    open fun perspective() {
        graphics!!.perspective()
    }

    /**
     * @param fovy field-of-view angle (in radians) for vertical direction
     * @param aspect ratio of width to height
     * @param zNear z-position of nearest clipping plane
     * @param zFar z-position of farthest clipping plane
     */
    open fun perspective(fovy: Float, aspect: Float, zNear: Float, zFar: Float) {
        graphics!!.perspective(fovy, aspect, zNear, zFar)
    }

    /**
     * ( begin auto-generated from frustum.xml )
     *
     * Sets a perspective matrix defined through the parameters. Works like
     * glFrustum, except it wipes out the current perspective matrix rather
     * than muliplying itself with it.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     * @param left left coordinate of the clipping plane
     * @param right right coordinate of the clipping plane
     * @param bottom bottom coordinate of the clipping plane
     * @param top top coordinate of the clipping plane
     * @param near near component of the clipping plane; must be greater than zero
     * @param far far component of the clipping plane; must be greater than the near value
     * @see PGraphics.camera
     * @see PGraphics.beginCamera
     * @see PGraphics.endCamera
     * @see PGraphics.perspective
     */
    open fun frustum(left: Float, right: Float,
                bottom: Float, top: Float,
                near: Float, far: Float) {
        graphics!!.frustum(left, right, bottom, top, near, far)
    }

    /**
     * ( begin auto-generated from printProjection.xml )
     *
     * Prints the current projection matrix to the Console (the text window at
     * the bottom of Processing).
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:camera
     * @see PGraphics.camera
     */
    open fun printProjection() {
        graphics!!.printProjection()
    }

    /**
     * ( begin auto-generated from screenX.xml )
     *
     * Takes a three-dimensional X, Y, Z position and returns the X value for
     * where it will appear on a (two-dimensional) screen.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:coordinates
     * @param x 3D x-coordinate to be mapped
     * @param y 3D y-coordinate to be mapped
     * @see PGraphics.screenY
     * @see PGraphics.screenZ
     */
    open fun screenX(x: Float, y: Float): Float {
        return graphics!!.screenX(x, y)
    }

    /**
     * ( begin auto-generated from screenY.xml )
     *
     * Takes a three-dimensional X, Y, Z position and returns the Y value for
     * where it will appear on a (two-dimensional) screen.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:coordinates
     * @param x 3D x-coordinate to be mapped
     * @param y 3D y-coordinate to be mapped
     * @see PGraphics.screenX
     * @see PGraphics.screenZ
     */
    open fun screenY(x: Float, y: Float): Float {
        return graphics!!.screenY(x, y)
    }

    /**
     * @param z 3D z-coordinate to be mapped
     */
    open fun screenX(x: Float, y: Float, z: Float): Float {
        return graphics!!.screenX(x, y, z)
    }

    /**
     * @param z 3D z-coordinate to be mapped
     */
    open fun screenY(x: Float, y: Float, z: Float): Float {
        return graphics!!.screenY(x, y, z)
    }

    /**
     * ( begin auto-generated from screenZ.xml )
     *
     * Takes a three-dimensional X, Y, Z position and returns the Z value for
     * where it will appear on a (two-dimensional) screen.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:coordinates
     * @param x 3D x-coordinate to be mapped
     * @param y 3D y-coordinate to be mapped
     * @param z 3D z-coordinate to be mapped
     * @see PGraphics.screenX
     * @see PGraphics.screenY
     */
    open fun screenZ(x: Float, y: Float, z: Float): Float {
        return graphics!!.screenZ(x, y, z)
    }

    /**
     * ( begin auto-generated from modelX.xml )
     *
     * Returns the three-dimensional X, Y, Z position in model space. This
     * returns the X value for a given coordinate based on the current set of
     * transformations (scale, rotate, translate, etc.) The X value can be used
     * to place an object in space relative to the location of the original
     * point once the transformations are no longer in use.
     * <br></br> <br></br>
     * In the example, the **modelX()**, **modelY()**, and
     * **modelZ()** functions record the location of a box in space after
     * being placed using a series of translate and rotate commands. After
     * popMatrix() is called, those transformations no longer apply, but the
     * (x, y, z) coordinate returned by the model functions is used to place
     * another box in the same location.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:coordinates
     * @param x 3D x-coordinate to be mapped
     * @param y 3D y-coordinate to be mapped
     * @param z 3D z-coordinate to be mapped
     * @see PGraphics.modelY
     * @see PGraphics.modelZ
     */
    open fun modelX(x: Float, y: Float, z: Float): Float {
        return graphics!!.modelX(x, y, z)
    }

    /**
     * ( begin auto-generated from modelY.xml )
     *
     * Returns the three-dimensional X, Y, Z position in model space. This
     * returns the Y value for a given coordinate based on the current set of
     * transformations (scale, rotate, translate, etc.) The Y value can be used
     * to place an object in space relative to the location of the original
     * point once the transformations are no longer in use.<br></br>
     * <br></br>
     * In the example, the **modelX()**, **modelY()**, and
     * **modelZ()** functions record the location of a box in space after
     * being placed using a series of translate and rotate commands. After
     * popMatrix() is called, those transformations no longer apply, but the
     * (x, y, z) coordinate returned by the model functions is used to place
     * another box in the same location.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:coordinates
     * @param x 3D x-coordinate to be mapped
     * @param y 3D y-coordinate to be mapped
     * @param z 3D z-coordinate to be mapped
     * @see PGraphics.modelX
     * @see PGraphics.modelZ
     */
    open fun modelY(x: Float, y: Float, z: Float): Float {
        return graphics!!.modelY(x, y, z)
    }

    /**
     * ( begin auto-generated from modelZ.xml )
     *
     * Returns the three-dimensional X, Y, Z position in model space. This
     * returns the Z value for a given coordinate based on the current set of
     * transformations (scale, rotate, translate, etc.) The Z value can be used
     * to place an object in space relative to the location of the original
     * point once the transformations are no longer in use.<br></br>
     * <br></br>
     * In the example, the **modelX()**, **modelY()**, and
     * **modelZ()** functions record the location of a box in space after
     * being placed using a series of translate and rotate commands. After
     * popMatrix() is called, those transformations no longer apply, but the
     * (x, y, z) coordinate returned by the model functions is used to place
     * another box in the same location.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:coordinates
     * @param x 3D x-coordinate to be mapped
     * @param y 3D y-coordinate to be mapped
     * @param z 3D z-coordinate to be mapped
     * @see PGraphics.modelX
     * @see PGraphics.modelY
     */
    open fun modelZ(x: Float, y: Float, z: Float): Float {
        return graphics!!.modelZ(x, y, z)
    }

    /**
     * ( begin auto-generated from pushStyle.xml )
     *
     * The **pushStyle()** function saves the current style settings and
     * **popStyle()** restores the prior settings. Note that these functions
     * are always used together. They allow you to change the style settings
     * and later return to what you had. When a new style is started with
     * **pushStyle()**, it builds on the current style information. The
     * **pushStyle()** and **popStyle()** functions can be embedded to
     * provide more control (see the second example above for a demonstration.)
     * <br></br><br></br>
     * The style information controlled by the following functions are included
     * in the style:
     * fill(), stroke(), tint(), strokeWeight(), strokeCap(), strokeJoin(),
     * imageMode(), rectMode(), ellipseMode(), shapeMode(), colorMode(),
     * textAlign(), textFont(), textMode(), textSize(), textLeading(),
     * emissive(), specular(), shininess(), ambient()
     *
     * ( end auto-generated )
     *
     * @webref structure
     * @see PGraphics.popStyle
     */
    open fun pushStyle() {
        graphics!!.pushStyle()
    }

    /**
     * ( begin auto-generated from popStyle.xml )
     *
     * The **pushStyle()** function saves the current style settings and
     * **popStyle()** restores the prior settings; these functions are
     * always used together. They allow you to change the style settings and
     * later return to what you had. When a new style is started with
     * **pushStyle()**, it builds on the current style information. The
     * **pushStyle()** and **popStyle()** functions can be embedded to
     * provide more control (see the second example above for a demonstration.)
     *
     * ( end auto-generated )
     *
     * @webref structure
     * @see PGraphics.pushStyle
     */
    open fun popStyle() {
        graphics!!.popStyle()
    }

    open fun style(s: PStyle?) {
        graphics!!.style(s)
    }

    /**
     * ( begin auto-generated from strokeWeight.xml )
     *
     * Sets the width of the stroke used for lines, points, and the border
     * around shapes. All widths are set in units of pixels.
     * <br></br> <br></br>
     * When drawing with P3D, series of connected lines (such as the stroke
     * around a polygon, triangle, or ellipse) produce unattractive results
     * when a thick stroke weight is set ([see
 * Issue 123](http://code.google.com/p/processing/issues/detail?id=123)). With P3D, the minimum and maximum values for
     * **strokeWeight()** are controlled by the graphics card and the
     * operating system's OpenGL implementation. For instance, the thickness
     * may not go higher than 10 pixels.
     *
     * ( end auto-generated )
     *
     * @webref shape:attributes
     * @param weight the weight (in pixels) of the stroke
     * @see PGraphics.stroke
     * @see PGraphics.strokeJoin
     * @see PGraphics.strokeCap
     */
    open fun strokeWeight(weight: Float) {
        graphics!!.strokeWeight(weight)
    }

    /**
     * ( begin auto-generated from strokeJoin.xml )
     *
     * Sets the style of the joints which connect line segments. These joints
     * are either mitered, beveled, or rounded and specified with the
     * corresponding parameters MITER, BEVEL, and ROUND. The default joint is
     * MITER.
     * <br></br> <br></br>
     * This function is not available with the P3D renderer, ([see
 * Issue 123](http://code.google.com/p/processing/issues/detail?id=123)). More information about the renderers can be found in the
     * **size()** reference.
     *
     * ( end auto-generated )
     *
     * @webref shape:attributes
     * @param join either MITER, BEVEL, ROUND
     * @see PGraphics.stroke
     * @see PGraphics.strokeWeight
     * @see PGraphics.strokeCap
     */
    open fun strokeJoin(join: Int) {
        graphics!!.strokeJoin(join)
    }

    /**
     * ( begin auto-generated from strokeCap.xml )
     *
     * Sets the style for rendering line endings. These ends are either
     * squared, extended, or rounded and specified with the corresponding
     * parameters SQUARE, PROJECT, and ROUND. The default cap is ROUND.
     * <br></br> <br></br>
     * This function is not available with the P3D renderer ([see
 * Issue 123](http://code.google.com/p/processing/issues/detail?id=123)). More information about the renderers can be found in the
     * **size()** reference.
     *
     * ( end auto-generated )
     *
     * @webref shape:attributes
     * @param cap either SQUARE, PROJECT, or ROUND
     * @see PGraphics.stroke
     * @see PGraphics.strokeWeight
     * @see PGraphics.strokeJoin
     * @see PApplet.size
     */
    open fun strokeCap(cap: Int) {
        graphics!!.strokeCap(cap)
    }

    /**
     * ( begin auto-generated from noStroke.xml )
     *
     * Disables drawing the stroke (outline). If both **noStroke()** and
     * **noFill()** are called, nothing will be drawn to the screen.
     *
     * ( end auto-generated )
     *
     * @webref color:setting
     * @see PGraphics.stroke
     * @see PGraphics.fill
     * @see PGraphics.noFill
     */
    open fun noStroke() {
        graphics!!.noStroke()
    }

    /**
     * ( begin auto-generated from stroke.xml )
     *
     * Sets the color used to draw lines and borders around shapes. This color
     * is either specified in terms of the RGB or HSB color depending on the
     * current **colorMode()** (the default color space is RGB, with each
     * value in the range from 0 to 255).
     * <br></br> <br></br>
     * When using hexadecimal notation to specify a color, use "#" or "0x"
     * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
     * digits to specify a color (the way colors are specified in HTML and
     * CSS). When using the hexadecimal notation starting with "0x", the
     * hexadecimal value must be specified with eight characters; the first two
     * characters define the alpha component and the remainder the red, green,
     * and blue components.
     * <br></br> <br></br>
     * The value for the parameter "gray" must be less than or equal to the
     * current maximum value as specified by **colorMode()**. The default
     * maximum value is 255.
     *
     * ( end auto-generated )
     *
     * @param rgb color value in hexadecimal notation
     * @see PGraphics.noStroke
     * @see PGraphics.strokeWeight
     * @see PGraphics.strokeJoin
     * @see PGraphics.strokeCap
     * @see PGraphics.fill
     * @see PGraphics.noFill
     * @see PGraphics.tint
     * @see PGraphics.background
     * @see PGraphics.colorMode
     */
    open fun stroke(rgb: Int) {
        graphics!!.stroke(rgb)
    }

    /**
     * @param alpha opacity of the stroke
     */
    open fun stroke(rgb: Int, alpha: Float) {
        graphics!!.stroke(rgb, alpha)
    }

    /**
     * @param gray specifies a value between white and black
     */
    open fun stroke(gray: Float) {
        graphics!!.stroke(gray)
    }

    open fun stroke(gray: Float, alpha: Float) {
        graphics!!.stroke(gray, alpha)
    }

    /**
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     * @webref color:setting
     */
    open fun stroke(v1: Float, v2: Float, v3: Float) {
        graphics!!.stroke(v1, v2, v3)
    }

    open fun stroke(v1: Float, v2: Float, v3: Float, alpha: Float) {
        graphics!!.stroke(v1, v2, v3, alpha)
    }

    /**
     * ( begin auto-generated from noTint.xml )
     *
     * Removes the current fill value for displaying images and reverts to
     * displaying images with their original hues.
     *
     * ( end auto-generated )
     *
     * @webref image:loading_displaying
     * @usage web_application
     * @see PGraphics.tint
     * @see PGraphics.image
     */
    open fun noTint() {
        graphics!!.noTint()
    }

    /**
     * ( begin auto-generated from tint.xml )
     *
     * Sets the fill value for displaying images. Images can be tinted to
     * specified colors or made transparent by setting the alpha.<br></br>
     * <br></br>
     * To make an image transparent, but not change it's color, use white as
     * the tint color and specify an alpha value. For instance, tint(255, 128)
     * will make an image 50% transparent (unless **colorMode()** has been
     * used).<br></br>
     * <br></br>
     * When using hexadecimal notation to specify a color, use "#" or "0x"
     * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
     * digits to specify a color (the way colors are specified in HTML and
     * CSS). When using the hexadecimal notation starting with "0x", the
     * hexadecimal value must be specified with eight characters; the first two
     * characters define the alpha component and the remainder the red, green,
     * and blue components.<br></br>
     * <br></br>
     * The value for the parameter "gray" must be less than or equal to the
     * current maximum value as specified by **colorMode()**. The default
     * maximum value is 255.<br></br>
     * <br></br>
     * The **tint()** function is also used to control the coloring of
     * textures in 3D.
     *
     * ( end auto-generated )
     *
     * @webref image:loading_displaying
     * @usage web_application
     * @param rgb color value in hexadecimal notation
     * @see PGraphics.noTint
     * @see PGraphics.image
     */
    open fun tint(rgb: Int) {
        graphics!!.tint(rgb)
    }

    /**
     * @param alpha opacity of the image
     */
    open fun tint(rgb: Int, alpha: Float) {
        graphics!!.tint(rgb, alpha)
    }

    /**
     * @param gray specifies a value between white and black
     */
    open fun tint(gray: Float) {
        graphics!!.tint(gray)
    }

    open fun tint(gray: Float, alpha: Float) {
        graphics!!.tint(gray, alpha)
    }

    /**
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     */
    open fun tint(v1: Float, v2: Float, v3: Float) {
        graphics!!.tint(v1, v2, v3)
    }

    open fun tint(v1: Float, v2: Float, v3: Float, alpha: Float) {
        graphics!!.tint(v1, v2, v3, alpha)
    }

    /**
     * ( begin auto-generated from noFill.xml )
     *
     * Disables filling geometry. If both **noStroke()** and **noFill()**
     * are called, nothing will be drawn to the screen.
     *
     * ( end auto-generated )
     *
     * @webref color:setting
     * @usage web_application
     * @see PGraphics.fill
     * @see PGraphics.stroke
     * @see PGraphics.noStroke
     */
    open fun noFill() {
        graphics!!.noFill()
    }

    /**
     * ( begin auto-generated from fill.xml )
     *
     * Sets the color used to fill shapes. For example, if you run **fill(204,
     * 102, 0)**, all subsequent shapes will be filled with orange. This
     * color is either specified in terms of the RGB or HSB color depending on
     * the current **colorMode()** (the default color space is RGB, with
     * each value in the range from 0 to 255).
     * <br></br> <br></br>
     * When using hexadecimal notation to specify a color, use "#" or "0x"
     * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
     * digits to specify a color (the way colors are specified in HTML and
     * CSS). When using the hexadecimal notation starting with "0x", the
     * hexadecimal value must be specified with eight characters; the first two
     * characters define the alpha component and the remainder the red, green,
     * and blue components.
     * <br></br> <br></br>
     * The value for the parameter "gray" must be less than or equal to the
     * current maximum value as specified by **colorMode()**. The default
     * maximum value is 255.
     * <br></br> <br></br>
     * To change the color of an image (or a texture), use tint().
     *
     * ( end auto-generated )
     *
     * @webref color:setting
     * @usage web_application
     * @param rgb color variable or hex value
     * @see PGraphics.noFill
     * @see PGraphics.stroke
     * @see PGraphics.noStroke
     * @see PGraphics.tint
     * @see PGraphics.background
     * @see PGraphics.colorMode
     */
    open fun fill(rgb: Int) {
        graphics!!.fill(rgb)
    }

    /**
     * @param alpha opacity of the fill
     */
    open fun fill(rgb: Int, alpha: Float) {
        graphics!!.fill(rgb, alpha)
    }

    /**
     * @param gray number specifying value between white and black
     */
    open fun fill(gray: Float) {
        graphics!!.fill(gray)
    }

    open fun fill(gray: Float, alpha: Float) {
        graphics!!.fill(gray, alpha)
    }

    /**
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     */
    open fun fill(v1: Float, v2: Float, v3: Float) {
        graphics!!.fill(v1, v2, v3)
    }

    open fun fill(v1: Float, v2: Float, v3: Float, alpha: Float) {
        graphics!!.fill(v1, v2, v3, alpha)
    }

    /**
     * ( begin auto-generated from ambient.xml )
     *
     * Sets the ambient reflectance for shapes drawn to the screen. This is
     * combined with the ambient light component of environment. The color
     * components set through the parameters define the reflectance. For
     * example in the default color mode, setting v1=255, v2=126, v3=0, would
     * cause all the red light to reflect and half of the green light to
     * reflect. Used in combination with **emissive()**, **specular()**,
     * and **shininess()** in setting the material properties of shapes.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:material_properties
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.emissive
     * @see PGraphics.specular
     * @see PGraphics.shininess
     */
    open fun ambient(rgb: Int) {
        graphics!!.ambient(rgb)
    }

    /**
     * @param gray number specifying value between white and black
     */
    open fun ambient(gray: Float) {
        graphics!!.ambient(gray)
    }

    /**
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     */
    open fun ambient(v1: Float, v2: Float, v3: Float) {
        graphics!!.ambient(v1, v2, v3)
    }

    /**
     * ( begin auto-generated from specular.xml )
     *
     * Sets the specular color of the materials used for shapes drawn to the
     * screen, which sets the color of hightlights. Specular refers to light
     * which bounces off a surface in a perferred direction (rather than
     * bouncing in all directions like a diffuse light). Used in combination
     * with **emissive()**, **ambient()**, and **shininess()** in
     * setting the material properties of shapes.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:material_properties
     * @usage web_application
     * @param rgb color to set
     * @see PGraphics.lightSpecular
     * @see PGraphics.ambient
     * @see PGraphics.emissive
     * @see PGraphics.shininess
     */
    open fun specular(rgb: Int) {
        graphics!!.specular(rgb)
    }

    /**
     * gray number specifying value between white and black
     *
     * @param gray value between black and white, by default 0 to 255
     */
    open fun specular(gray: Float) {
        graphics!!.specular(gray)
    }

    /**
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     */
    open fun specular(v1: Float, v2: Float, v3: Float) {
        graphics!!.specular(v1, v2, v3)
    }

    /**
     * ( begin auto-generated from shininess.xml )
     *
     * Sets the amount of gloss in the surface of shapes. Used in combination
     * with **ambient()**, **specular()**, and **emissive()** in
     * setting the material properties of shapes.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:material_properties
     * @usage web_application
     * @param shine degree of shininess
     * @see PGraphics.emissive
     * @see PGraphics.ambient
     * @see PGraphics.specular
     */
    open fun shininess(shine: Float) {
        graphics!!.shininess(shine)
    }

    /**
     * ( begin auto-generated from emissive.xml )
     *
     * Sets the emissive color of the material used for drawing shapes drawn to
     * the screen. Used in combination with **ambient()**,
     * **specular()**, and **shininess()** in setting the material
     * properties of shapes.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:material_properties
     * @usage web_application
     * @param rgb color to set
     * @see PGraphics.ambient
     * @see PGraphics.specular
     * @see PGraphics.shininess
     */
    open fun emissive(rgb: Int) {
        graphics!!.emissive(rgb)
    }

    /**
     * gray number specifying value between white and black
     *
     * @param gray value between black and white, by default 0 to 255
     */
    open fun emissive(gray: Float) {
        graphics!!.emissive(gray)
    }

    /**
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     */
    open fun emissive(v1: Float, v2: Float, v3: Float) {
        graphics!!.emissive(v1, v2, v3)
    }

    /**
     * ( begin auto-generated from lights.xml )
     *
     * Sets the default ambient light, directional light, falloff, and specular
     * values. The defaults are ambientLight(128, 128, 128) and
     * directionalLight(128, 128, 128, 0, 0, -1), lightFalloff(1, 0, 0), and
     * lightSpecular(0, 0, 0). Lights need to be included in the draw() to
     * remain persistent in a looping program. Placing them in the setup() of a
     * looping program will cause them to only have an effect the first time
     * through the loop.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @see PGraphics.ambientLight
     * @see PGraphics.directionalLight
     * @see PGraphics.pointLight
     * @see PGraphics.spotLight
     * @see PGraphics.noLights
     */
    open fun lights() {
        graphics!!.lights()
    }

    /**
     * ( begin auto-generated from noLights.xml )
     *
     * Disable all lighting. Lighting is turned off by default and enabled with
     * the **lights()** function. This function can be used to disable
     * lighting so that 2D geometry (which does not require lighting) can be
     * drawn after a set of lighted 3D geometry.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @see PGraphics.lights
     */
    open fun noLights() {
        graphics!!.noLights()
    }

    /**
     * ( begin auto-generated from ambientLight.xml )
     *
     * Adds an ambient light. Ambient light doesn't come from a specific
     * direction, the rays have light have bounced around so much that objects
     * are evenly lit from all sides. Ambient lights are almost always used in
     * combination with other types of lights. Lights need to be included in
     * the **draw()** to remain persistent in a looping program. Placing
     * them in the **setup()** of a looping program will cause them to only
     * have an effect the first time through the loop. The effect of the
     * parameters is determined by the current color mode.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     * @see PGraphics.lights
     * @see PGraphics.directionalLight
     * @see PGraphics.pointLight
     * @see PGraphics.spotLight
     */
    open fun ambientLight(v1: Float, v2: Float, v3: Float) {
        graphics!!.ambientLight(v1, v2, v3)
    }

    /**
     * @param x x-coordinate of the light
     * @param y y-coordinate of the light
     * @param z z-coordinate of the light
     */
    open fun ambientLight(v1: Float, v2: Float, v3: Float,
                     x: Float, y: Float, z: Float) {
        graphics!!.ambientLight(v1, v2, v3, x, y, z)
    }

    /**
     * ( begin auto-generated from directionalLight.xml )
     *
     * Adds a directional light. Directional light comes from one direction and
     * is stronger when hitting a surface squarely and weaker if it hits at a a
     * gentle angle. After hitting a surface, a directional lights scatters in
     * all directions. Lights need to be included in the **draw()** to
     * remain persistent in a looping program. Placing them in the
     * **setup()** of a looping program will cause them to only have an
     * effect the first time through the loop. The affect of the **v1**,
     * **v2**, and **v3** parameters is determined by the current color
     * mode. The **nx**, **ny**, and **nz** parameters specify the
     * direction the light is facing. For example, setting **ny** to -1 will
     * cause the geometry to be lit from below (the light is facing directly upward).
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     * @param nx direction along the x-axis
     * @param ny direction along the y-axis
     * @param nz direction along the z-axis
     * @see PGraphics.lights
     * @see PGraphics.ambientLight
     * @see PGraphics.pointLight
     * @see PGraphics.spotLight
     */
    open fun directionalLight(v1: Float, v2: Float, v3: Float,
                         nx: Float, ny: Float, nz: Float) {
        graphics!!.directionalLight(v1, v2, v3, nx, ny, nz)
    }

    /**
     * ( begin auto-generated from pointLight.xml )
     *
     * Adds a point light. Lights need to be included in the **draw()** to
     * remain persistent in a looping program. Placing them in the
     * **setup()** of a looping program will cause them to only have an
     * effect the first time through the loop. The affect of the **v1**,
     * **v2**, and **v3** parameters is determined by the current color
     * mode. The **x**, **y**, and **z** parameters set the position
     * of the light.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     * @param x x-coordinate of the light
     * @param y y-coordinate of the light
     * @param z z-coordinate of the light
     * @see PGraphics.lights
     * @see PGraphics.directionalLight
     * @see PGraphics.ambientLight
     * @see PGraphics.spotLight
     */
    open fun pointLight(v1: Float, v2: Float, v3: Float,
                   x: Float, y: Float, z: Float) {
        graphics!!.pointLight(v1, v2, v3, x, y, z)
    }

    /**
     * ( begin auto-generated from spotLight.xml )
     *
     * Adds a spot light. Lights need to be included in the **draw()** to
     * remain persistent in a looping program. Placing them in the
     * **setup()** of a looping program will cause them to only have an
     * effect the first time through the loop. The affect of the **v1**,
     * **v2**, and **v3** parameters is determined by the current color
     * mode. The **x**, **y**, and **z** parameters specify the
     * position of the light and **nx**, **ny**, **nz** specify the
     * direction or light. The **angle** parameter affects angle of the
     * spotlight cone.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     * @param x x-coordinate of the light
     * @param y y-coordinate of the light
     * @param z z-coordinate of the light
     * @param nx direction along the x axis
     * @param ny direction along the y axis
     * @param nz direction along the z axis
     * @param angle angle of the spotlight cone
     * @param concentration exponent determining the center bias of the cone
     * @see PGraphics.lights
     * @see PGraphics.directionalLight
     * @see PGraphics.pointLight
     * @see PGraphics.ambientLight
     */
    open fun spotLight(v1: Float, v2: Float, v3: Float,
                  x: Float, y: Float, z: Float,
                  nx: Float, ny: Float, nz: Float,
                  angle: Float, concentration: Float) {
        graphics!!.spotLight(v1, v2, v3, x, y, z, nx, ny, nz, angle, concentration)
    }

    /**
     * ( begin auto-generated from lightFalloff.xml )
     *
     * Sets the falloff rates for point lights, spot lights, and ambient
     * lights. The parameters are used to determine the falloff with the
     * following equation:<br></br><br></br>d = distance from light position to
     * vertex position<br></br>falloff = 1 / (CONSTANT + d * LINEAR + (d*d) *
     * QUADRATIC)<br></br><br></br>Like **fill()**, it affects only the elements
     * which are created after it in the code. The default value if
     * **LightFalloff(1.0, 0.0, 0.0)**. Thinking about an ambient light with
     * a falloff can be tricky. It is used, for example, if you wanted a region
     * of your scene to be lit ambiently one color and another region to be lit
     * ambiently by another color, you would use an ambient light with location
     * and falloff. You can think of it as a point light that doesn't care
     * which direction a surface is facing.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @param constant constant value or determining falloff
     * @param linear linear value for determining falloff
     * @param quadratic quadratic value for determining falloff
     * @see PGraphics.lights
     * @see PGraphics.ambientLight
     * @see PGraphics.pointLight
     * @see PGraphics.spotLight
     * @see PGraphics.lightSpecular
     */
    open fun lightFalloff(constant: Float, linear: Float, quadratic: Float) {
        graphics!!.lightFalloff(constant, linear, quadratic)
    }

    /**
     * ( begin auto-generated from lightSpecular.xml )
     *
     * Sets the specular color for lights. Like **fill()**, it affects only
     * the elements which are created after it in the code. Specular refers to
     * light which bounces off a surface in a perferred direction (rather than
     * bouncing in all directions like a diffuse light) and is used for
     * creating highlights. The specular quality of a light interacts with the
     * specular material qualities set through the **specular()** and
     * **shininess()** functions.
     *
     * ( end auto-generated )
     *
     * @webref lights_camera:lights
     * @usage web_application
     * @param v1 red or hue value (depending on current color mode)
     * @param v2 green or saturation value (depending on current color mode)
     * @param v3 blue or brightness value (depending on current color mode)
     * @see PGraphics.specular
     * @see PGraphics.lights
     * @see PGraphics.ambientLight
     * @see PGraphics.pointLight
     * @see PGraphics.spotLight
     */
    open fun lightSpecular(v1: Float, v2: Float, v3: Float) {
        graphics!!.lightSpecular(v1, v2, v3)
    }

    /**
     * ( begin auto-generated from background.xml )
     *
     * The **background()** function sets the color used for the background
     * of the Processing window. The default background is light gray. In the
     * **draw()** function, the background color is used to clear the
     * display window at the beginning of each frame.
     * <br></br> <br></br>
     * An image can also be used as the background for a sketch, however its
     * width and height must be the same size as the sketch window. To resize
     * an image 'b' to the size of the sketch window, use b.resize(width, height).
     * <br></br> <br></br>
     * Images used as background will ignore the current **tint()** setting.
     * <br></br> <br></br>
     * It is not possible to use transparency (alpha) in background colors with
     * the main drawing surface, however they will work properly with **createGraphics()**.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     *
     * Clear the background with a color that includes an alpha value. This can
     * only be used with objects created by createGraphics(), because the main
     * drawing surface cannot be set transparent.
     *
     * It might be tempting to use this function to partially clear the screen
     * on each frame, however that's not how this function works. When calling
     * background(), the pixels will be replaced with pixels that have that level
     * of transparency. To do a semi-transparent overlay, use fill() with alpha
     * and draw a rectangle.
     *
     * @webref color:setting
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.stroke
     * @see PGraphics.fill
     * @see PGraphics.tint
     * @see PGraphics.colorMode
     */
    open fun background(rgb: Int) {
        graphics!!.background(rgb)
    }

    /**
     * @param alpha opacity of the background
     */
    open fun background(rgb: Int, alpha: Float) {
        graphics!!.background(rgb, alpha)
    }

    /**
     * @param gray specifies a value between white and black
     */
    open fun background(gray: Float) {
        graphics!!.background(gray)
    }

    open fun background(gray: Float, alpha: Float) {
        graphics!!.background(gray, alpha)
    }

    /**
     * @param v1 red or hue value (depending on the current color mode)
     * @param v2 green or saturation value (depending on the current color mode)
     * @param v3 blue or brightness value (depending on the current color mode)
     */
    open fun background(v1: Float, v2: Float, v3: Float) {
        graphics!!.background(v1, v2, v3)
    }

    open fun background(v1: Float, v2: Float, v3: Float, alpha: Float) {
        graphics!!.background(v1, v2, v3, alpha)
    }

    /**
     * @webref color:setting
     */
    open fun clear() {
        graphics!!.clear()
    }

    /**
     * Takes an RGB or ARGB image and sets it as the background.
     * The width and height of the image must be the same size as the sketch.
     * Use image.resize(width, height) to make short work of such a task.<br></br>
     * <br></br>
     * Note that even if the image is set as RGB, the high 8 bits of each pixel
     * should be set opaque (0xFF000000) because the image data will be copied
     * directly to the screen, and non-opaque background images may have strange
     * behavior. Use image.filter(OPAQUE) to handle this easily.<br></br>
     * <br></br>
     * When using 3D, this will also clear the zbuffer (if it exists).
     *
     * @param image PImage to set as background (must be same size as the sketch window)
     */
    open fun background(image: PImage?) {
        graphics!!.background(image!!)
    }

    /**
     * ( begin auto-generated from colorMode.xml )
     *
     * Changes the way Processing interprets color data. By default, the
     * parameters for **fill()**, **stroke()**, **background()**, and
     * **color()** are defined by values between 0 and 255 using the RGB
     * color model. The **colorMode()** function is used to change the
     * numerical range used for specifying colors and to switch color systems.
     * For example, calling **colorMode(RGB, 1.0)** will specify that values
     * are specified between 0 and 1. The limits for defining colors are
     * altered by setting the parameters range1, range2, range3, and range 4.
     *
     * ( end auto-generated )
     *
     * @webref color:setting
     * @usage web_application
     * @param mode Either RGB or HSB, corresponding to Red/Green/Blue and Hue/Saturation/Brightness
     * @see PGraphics.background
     * @see PGraphics.fill
     * @see PGraphics.stroke
     */
    open fun colorMode(mode: Int) {
        graphics!!.colorMode(mode)
    }

    /**
     * @param max range for all color elements
     */
    open fun colorMode(mode: Int, max: Float) {
        graphics!!.colorMode(mode, max)
    }

    /**
     * @param max1 range for the red or hue depending on the current color mode
     * @param max2 range for the green or saturation depending on the current color mode
     * @param max3 range for the blue or brightness depending on the current color mode
     */
    open fun colorMode(mode: Int, max1: Float, max2: Float, max3: Float) {
        graphics!!.colorMode(mode, max1, max2, max3)
    }

    /**
     * @param maxA range for the alpha
     */
    open fun colorMode(mode: Int,
                  max1: Float, max2: Float, max3: Float, maxA: Float) {
        graphics!!.colorMode(mode, max1, max2, max3, maxA)
    }

    /**
     * ( begin auto-generated from alpha.xml )
     *
     * Extracts the alpha value from a color.
     *
     * ( end auto-generated )
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.red
     * @see PGraphics.green
     * @see PGraphics.blue
     * @see PGraphics.hue
     * @see PGraphics.saturation
     * @see PGraphics.brightness
     */
    open fun alpha(rgb: Int): Float {
        return graphics!!.alpha(rgb)
    }

    /**
     * ( begin auto-generated from red.xml )
     *
     * Extracts the red value from a color, scaled to match current
     * **colorMode()**. This value is always returned as a  float so be
     * careful not to assign it to an int value.<br></br><br></br>The red() function
     * is easy to use and undestand, but is slower than another technique. To
     * achieve the same results when working in **colorMode(RGB, 255)**, but
     * with greater speed, use the &gt;&gt; (right shift) operator with a bit
     * mask. For example, the following two lines of code are equivalent:<br></br><pre>float r1 = red(myColor);<br></br>float r2 = myColor &gt;&gt; 16
     * &amp; 0xFF;</pre>
     *
     * ( end auto-generated )
     *
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.green
     * @see PGraphics.blue
     * @see PGraphics.alpha
     * @see PGraphics.hue
     * @see PGraphics.saturation
     * @see PGraphics.brightness
     * @see_external rightshift
     */
    open fun red(rgb: Int): Float {
        return graphics!!.red(rgb)
    }

    /**
     * ( begin auto-generated from green.xml )
     *
     * Extracts the green value from a color, scaled to match current
     * **colorMode()**. This value is always returned as a  float so be
     * careful not to assign it to an int value.<br></br><br></br>The **green()**
     * function is easy to use and undestand, but is slower than another
     * technique. To achieve the same results when working in **colorMode(RGB,
     * 255)**, but with greater speed, use the &gt;&gt; (right shift)
     * operator with a bit mask. For example, the following two lines of code
     * are equivalent:<br></br><pre>float r1 = green(myColor);<br></br>float r2 =
     * myColor &gt;&gt; 8 &amp; 0xFF;</pre>
     *
     * ( end auto-generated )
     *
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.red
     * @see PGraphics.blue
     * @see PGraphics.alpha
     * @see PGraphics.hue
     * @see PGraphics.saturation
     * @see PGraphics.brightness
     * @see_external rightshift
     */
    open fun green(rgb: Int): Float {
        return graphics!!.green(rgb)
    }

    /**
     * ( begin auto-generated from blue.xml )
     *
     * Extracts the blue value from a color, scaled to match current
     * **colorMode()**. This value is always returned as a  float so be
     * careful not to assign it to an int value.<br></br><br></br>The **blue()**
     * function is easy to use and undestand, but is slower than another
     * technique. To achieve the same results when working in **colorMode(RGB,
     * 255)**, but with greater speed, use a bit mask to remove the other
     * color components. For example, the following two lines of code are
     * equivalent:<br></br><pre>float r1 = blue(myColor);<br></br>float r2 = myColor
     * &amp; 0xFF;</pre>
     *
     * ( end auto-generated )
     *
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.red
     * @see PGraphics.green
     * @see PGraphics.alpha
     * @see PGraphics.hue
     * @see PGraphics.saturation
     * @see PGraphics.brightness
     * @see_external rightshift
     */
    open fun blue(rgb: Int): Float {
        return graphics!!.blue(rgb)
    }

    /**
     * ( begin auto-generated from hue.xml )
     *
     * Extracts the hue value from a color.
     *
     * ( end auto-generated )
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.red
     * @see PGraphics.green
     * @see PGraphics.blue
     * @see PGraphics.alpha
     * @see PGraphics.saturation
     * @see PGraphics.brightness
     */
    open fun hue(rgb: Int): Float {
        return graphics!!.hue(rgb)
    }

    /**
     * ( begin auto-generated from saturation.xml )
     *
     * Extracts the saturation value from a color.
     *
     * ( end auto-generated )
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.red
     * @see PGraphics.green
     * @see PGraphics.blue
     * @see PGraphics.alpha
     * @see PGraphics.hue
     * @see PGraphics.brightness
     */
    open fun saturation(rgb: Int): Float {
        return graphics!!.saturation(rgb)
    }

    /**
     * ( begin auto-generated from brightness.xml )
     *
     * Extracts the brightness value from a color.
     *
     * ( end auto-generated )
     *
     * @webref color:creating_reading
     * @usage web_application
     * @param rgb any value of the color datatype
     * @see PGraphics.red
     * @see PGraphics.green
     * @see PGraphics.blue
     * @see PGraphics.alpha
     * @see PGraphics.hue
     * @see PGraphics.saturation
     */
    open fun brightness(rgb: Int): Float {
        return graphics!!.brightness(rgb)
    }

    /**
     * ( begin auto-generated from PImage_get.xml )
     *
     * Reads the color of any pixel or grabs a section of an image. If no
     * parameters are specified, the entire image is returned. Use the **x**
     * and **y** parameters to get the value of one pixel. Get a section of
     * the display window by specifying an additional **width** and
     * **height** parameter. When getting an image, the **x** and
     * **y** parameters define the coordinates for the upper-left corner of
     * the image, regardless of the current **imageMode()**.<br></br>
     * <br></br>
     * If the pixel requested is outside of the image window, black is
     * returned. The numbers returned are scaled according to the current color
     * ranges, but only RGB values are returned by this function. For example,
     * even though you may have drawn a shape with **colorMode(HSB)**, the
     * numbers returned will be in RGB format.<br></br>
     * <br></br>
     * Getting the color of a single pixel with **get(x, y)** is easy, but
     * not as fast as grabbing the data directly from **pixels[]**. The
     * equivalent statement to **get(x, y)** using **pixels[]** is
     * **pixels[y*width+x]**. See the reference for **pixels[]** for more information.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Returns an ARGB "color" type (a packed 32 bit int with the color.
     * If the coordinate is outside the image, zero is returned
     * (black, but completely transparent).
     * <P>
     * If the image is in RGB format (i.e. on a PVideo object),
     * the value will get its high bits set, just to avoid cases where
     * they haven't been set already.
    </P> * <P>
     * If the image is in ALPHA format, this returns a white with its
     * alpha value set.
    </P> * <P>
     * This function is included primarily for beginners. It is quite
     * slow because it has to check to see if the x, y that was provided
     * is inside the bounds, and then has to check to see what image
     * type it is. If you want things to be more efficient, access the
     * pixels[] array directly.
     *
     * @webref image:pixels
     * @brief Reads the color of any pixel or grabs a rectangle of pixels
     * @usage web_application
     * @param x x-coordinate of the pixel
     * @param y y-coordinate of the pixel
     * @see PApplet.set
     * @see PApplet.pixels
     *
     * @see PApplet.copy
    </P> */
    operator fun get(x: Int, y: Int): Int {
        return graphics!![x, y]
    }

    /**
     * @param w width of pixel rectangle to get
     * @param h height of pixel rectangle to get
     */
    // todo - return @ Non-nullable
    operator fun get(x: Int, y: Int, w: Int, h: Int): PImage {
        return graphics!![x, y, w, h]
    }

    /**
     * Returns a copy of this PImage. Equivalent to get(0, 0, width, height).
     * Deprecated, just use copy() instead.
     */
    // todo - return @ Non-nullable
    fun get(): PImage {
        return graphics!!.get()
    }

    // todo - return @ Non-nullable
    fun copy(): PImage {
        return graphics!!.copy()
    }

    /**
     * ( begin auto-generated from PImage_set.xml )
     *
     * Changes the color of any pixel or writes an image directly into the
     * display window.<br></br>
     * <br></br>
     * The **x** and **y** parameters specify the pixel to change and the
     * **color** parameter specifies the color value. The color parameter is
     * affected by the current color mode (the default is RGB values from 0 to
     * 255). When setting an image, the **x** and **y** parameters define
     * the coordinates for the upper-left corner of the image, regardless of
     * the current **imageMode()**.
     * <br></br><br></br>
     * Setting the color of a single pixel with **set(x, y)** is easy, but
     * not as fast as putting the data directly into **pixels[]**. The
     * equivalent statement to **set(x, y, #000000)** using **pixels[]**
     * is **pixels[y*width+x] = #000000**. See the reference for
     * **pixels[]** for more information.
     *
     * ( end auto-generated )
     *
     * @webref image:pixels
     * @brief writes a color to any pixel or writes an image into another
     * @usage web_application
     * @param x x-coordinate of the pixel
     * @param y y-coordinate of the pixel
     * @param c any value of the color datatype
     * @see PImage.get
     * @see PImage.pixels
     *
     * @see PImage.copy
     */
    operator fun set(x: Int, y: Int, c: Int) {
        graphics!![x, y] = c
    }

    /**
     * <h3>Advanced</h3>
     * Efficient method of drawing an image's pixels directly to this surface.
     * No variations are employed, meaning that any scale, tint, or imageMode
     * settings will be ignored.
     *
     * @param img image to copy into the original image
     */
    operator fun set(x: Int, y: Int, img: PImage?) {
        graphics!![x, y] = img!!
    }

    /**
     * ( begin auto-generated from PImage_mask.xml )
     *
     * Masks part of an image from displaying by loading another image and
     * using it as an alpha channel. This mask image should only contain
     * grayscale data, but only the blue color channel is used. The mask image
     * needs to be the same size as the image to which it is applied.<br></br>
     * <br></br>
     * In addition to using a mask image, an integer array containing the alpha
     * channel data can be specified directly. This method is useful for
     * creating dynamically generated alpha masks. This array must be of the
     * same length as the target image's pixels array and should contain only
     * grayscale data of values between 0-255.
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     *
     * Set alpha channel for an image. Black colors in the source
     * image will make the destination image completely transparent,
     * and white will make things fully opaque. Gray values will
     * be in-between steps.
     * <P>
     * Strictly speaking the "blue" value from the source image is
     * used as the alpha color. For a fully grayscale image, this
     * is correct, but for a color image it's not 100% accurate.
     * For a more accurate conversion, first use filter(GRAY)
     * which will make the image into a "correct" grayscale by
     * performing a proper luminance-based conversion.
     *
     * @webref pimage:method
     * @usage web_application
     * @param img image to use as the mask
     * @brief Masks part of an image with another image as an alpha channel
    </P> */
    // todo - arg @ Non-nullable
    open fun mask(img: PImage?) {
        graphics!!.mask(img!!)
    }

    open fun filter(kind: Int) {
        graphics!!.filter(kind)
    }

    /**
     * ( begin auto-generated from PImage_filter.xml )
     *
     * Filters an image as defined by one of the following modes:<br></br><br></br>THRESHOLD - converts the image to black and white pixels depending if
     * they are above or below the threshold defined by the level parameter.
     * The level must be between 0.0 (black) and 1.0(white). If no level is
     * specified, 0.5 is used.<br></br>
     * <br></br>
     * GRAY - converts any colors in the image to grayscale equivalents<br></br>
     * <br></br>
     * INVERT - sets each pixel to its inverse value<br></br>
     * <br></br>
     * POSTERIZE - limits each channel of the image to the number of colors
     * specified as the level parameter<br></br>
     * <br></br>
     * BLUR - executes a Guassian blur with the level parameter specifying the
     * extent of the blurring. If no level parameter is used, the blur is
     * equivalent to Guassian blur of radius 1<br></br>
     * <br></br>
     * OPAQUE - sets the alpha channel to entirely opaque<br></br>
     * <br></br>
     * ERODE - reduces the light areas with the amount defined by the level
     * parameter<br></br>
     * <br></br>
     * DILATE - increases the light areas with the amount defined by the level parameter
     *
     * ( end auto-generated )
     *
     * <h3>Advanced</h3>
     * Method to apply a variety of basic filters to this image.
     * <P>
    </P> * <UL>
     * <LI>filter(BLUR) provides a basic blur.
    </LI> * <LI>filter(GRAY) converts the image to grayscale based on luminance.
    </LI> * <LI>filter(INVERT) will invert the color components in the image.
    </LI> * <LI>filter(OPAQUE) set all the high bits in the image to opaque
    </LI> * <LI>filter(THRESHOLD) converts the image to black and white.
    </LI> * <LI>filter(DILATE) grow white/light areas
    </LI> * <LI>filter(ERODE) shrink white/light areas
    </LI></UL> *
     * Luminance conversion code contributed by
     * <A HREF="http://www.toxi.co.uk">toxi</A>
     * <P></P>
     * Gaussian blur code contributed by
     * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
     *
     * @webref image:pixels
     * @brief Converts the image to grayscale or black and white
     * @usage web_application
     * @param kind Either THRESHOLD, GRAY, OPAQUE, INVERT, POSTERIZE, BLUR, ERODE, or DILATE
     * @param param unique for each, see above
     */
    open fun filter(kind: Int, param: Float) {
        graphics!!.filter(kind, param)
    }

    /**
     * ( begin auto-generated from PImage_copy.xml )
     *
     * Copies a region of pixels from one image into another. If the source and
     * destination regions aren't the same size, it will automatically resize
     * source pixels to fit the specified target region. No alpha information
     * is used in the process, however if the source image has an alpha channel
     * set, it will be copied as well.
     * <br></br><br></br>
     * As of release 0149, this function ignores **imageMode()**.
     *
     * ( end auto-generated )
     *
     * @webref image:pixels
     * @brief Copies the entire image
     * @usage web_application
     * @param sx X coordinate of the source's upper left corner
     * @param sy Y coordinate of the source's upper left corner
     * @param sw source image width
     * @param sh source image height
     * @param dx X coordinate of the destination's upper left corner
     * @param dy Y coordinate of the destination's upper left corner
     * @param dw destination image width
     * @param dh destination image height
     * @see PGraphics.alpha
     * @see PImage.blend
     */
    fun copy(sx: Int, sy: Int, sw: Int, sh: Int,
             dx: Int, dy: Int, dw: Int, dh: Int) {
        graphics!!.copy(sx, sy, sw, sh, dx, dy, dw, dh)
    }

    /**
     * @param src an image variable referring to the source image.
     */
    fun copy(src: PImage?,
             sx: Int, sy: Int, sw: Int, sh: Int,
             dx: Int, dy: Int, dw: Int, dh: Int) {
        graphics!!.copy(src!!, sx, sy, sw, sh, dx, dy, dw, dh)
    }

    open fun blend(sx: Int, sy: Int, sw: Int, sh: Int,
              dx: Int, dy: Int, dw: Int, dh: Int, mode: Int) {
        graphics!!.blend(sx, sy, sw, sh, dx, dy, dw, dh, mode)
    }

    /**
     * ( begin auto-generated from PImage_blend.xml )
     *
     * Blends a region of pixels into the image specified by the **img**
     * parameter. These copies utilize full alpha channel support and a choice
     * of the following modes to blend the colors of source pixels (A) with the
     * ones of pixels in the destination image (B):<br></br>
     * <br></br>
     * BLEND - linear interpolation of colours: C = A*factor + B<br></br>
     * <br></br>
     * ADD - additive blending with white clip: C = min(A*factor + B, 255)<br></br>
     * <br></br>
     * SUBTRACT - subtractive blending with black clip: C = max(B - A*factor,
     * 0)<br></br>
     * <br></br>
     * DARKEST - only the darkest colour succeeds: C = min(A*factor, B)<br></br>
     * <br></br>
     * LIGHTEST - only the lightest colour succeeds: C = max(A*factor, B)<br></br>
     * <br></br>
     * DIFFERENCE - subtract colors from underlying image.<br></br>
     * <br></br>
     * EXCLUSION - similar to DIFFERENCE, but less extreme.<br></br>
     * <br></br>
     * MULTIPLY - Multiply the colors, result will always be darker.<br></br>
     * <br></br>
     * SCREEN - Opposite multiply, uses inverse values of the colors.<br></br>
     * <br></br>
     * OVERLAY - A mix of MULTIPLY and SCREEN. Multiplies dark values,
     * and screens light values.<br></br>
     * <br></br>
     * HARD_LIGHT - SCREEN when greater than 50% gray, MULTIPLY when lower.<br></br>
     * <br></br>
     * SOFT_LIGHT - Mix of DARKEST and LIGHTEST.
     * Works like OVERLAY, but not as harsh.<br></br>
     * <br></br>
     * DODGE - Lightens light tones and increases contrast, ignores darks.
     * Called "Color Dodge" in Illustrator and Photoshop.<br></br>
     * <br></br>
     * BURN - Darker areas are applied, increasing contrast, ignores lights.
     * Called "Color Burn" in Illustrator and Photoshop.<br></br>
     * <br></br>
     * All modes use the alpha information (highest byte) of source image
     * pixels as the blending factor. If the source and destination regions are
     * different sizes, the image will be automatically resized to match the
     * destination size. If the **srcImg** parameter is not used, the
     * display window is used as the source image.<br></br>
     * <br></br>
     * As of release 0149, this function ignores **imageMode()**.
     *
     * ( end auto-generated )
     *
     * @webref image:pixels
     * @brief  Copies a pixel or rectangle of pixels using different blending modes
     * @param src an image variable referring to the source image
     * @param sx X coordinate of the source's upper left corner
     * @param sy Y coordinate of the source's upper left corner
     * @param sw source image width
     * @param sh source image height
     * @param dx X coordinate of the destinations's upper left corner
     * @param dy Y coordinate of the destinations's upper left corner
     * @param dw destination image width
     * @param dh destination image height
     * @param mode Either BLEND, ADD, SUBTRACT, LIGHTEST, DARKEST, DIFFERENCE, EXCLUSION, MULTIPLY, SCREEN, OVERLAY, HARD_LIGHT, SOFT_LIGHT, DODGE, BURN
     *
     * @see PApplet.alpha
     * @see PImage.copy
     * @see PImage.blendColor
     */
    open fun blend(src: PImage?,
              sx: Int, sy: Int, sw: Int, sh: Int,
              dx: Int, dy: Int, dw: Int, dh: Int, mode: Int) {
        graphics!!.blend(src!!, sx, sy, sw, sh, dx, dy, dw, dh, mode)
    }

    companion object {
        const val DEBUG = false

        //  static final public boolean DEBUG = true;
        // Convenience public constant holding the SDK version, akin to platform in Java mode
        val SDK = Build.VERSION.SDK_INT

        /** When debugging headaches  */ //  static final boolean THREAD_DEBUG = false;
        /** Default width and height for applet when not specified  */
        const val DEFAULT_WIDTH = -1

        const val DEFAULT_HEIGHT = -1

        ///////////////////////////////////////////////////////////////
        // Error messages
        const val ERROR_MIN_MAX = "Cannot use min() or max() on an empty array."

        ///////////////////////////////////////////////////////////////
        // Command line options
        /**
         * Position of the upper-lefthand corner of the editor window
         * that launched this applet.
         */
        const val ARGS_EDITOR_LOCATION = "--editor-location"

        /**
         * Location for where to position the applet window on screen.
         * <P>
         * This is used by the editor to when saving the previous applet
         * location, or could be used by other classes to launch at a
         * specific position on-screen.
        </P> */
        const val ARGS_EXTERNAL = "--external"
        const val ARGS_LOCATION = "--location"
        const val ARGS_DISPLAY = "--display"
        const val ARGS_BGCOLOR = "--bgcolor"
        const val ARGS_PRESENT = "--present"
        const val ARGS_EXCLUSIVE = "--exclusive"
        const val ARGS_STOP_COLOR = "--stop-color"
        const val ARGS_HIDE_STOP = "--hide-stop"

        /**
         * Allows the user or PdeEditor to set a specific sketch folder path.
         * <P>
         * Used by PdeEditor to pass in the location where saveFrame()
         * and all that stuff should write things.
        </P> */
        const val ARGS_SKETCH_FOLDER = "--sketch-path"

        /**
         * When run externally to a PdeEditor,
         * this is sent by the applet when it quits.
         */
        //static public final String EXTERNAL_QUIT = "__QUIT__";
        const val EXTERNAL_STOP = "__STOP__"

        /**
         * When run externally to a PDE Editor, this is sent by the applet
         * whenever the window is moved.
         * <P>
         * This is used so that the editor can re-open the sketch window
         * in the same position as the user last left it.
        </P> */
        const val EXTERNAL_MOVE = "__MOVE__"

        /** Seconds position of the current time.  */
        @JvmStatic
        fun second(): Int {
            return Calendar.getInstance()[Calendar.SECOND]
        }

        /** Minutes position of the current time.  */
        @JvmStatic
        fun minute(): Int {
            return Calendar.getInstance()[Calendar.MINUTE]
        }

        /**
         * Hour position of the current time in international format (0-23).
         * <P>
         * To convert this value to American time: <BR></BR>
        </P> * <PRE>int yankeeHour = (hour() % 12);
         * if (yankeeHour == 0) yankeeHour = 12;</PRE>
         */
        @JvmStatic
        fun hour(): Int {
            return Calendar.getInstance()[Calendar.HOUR_OF_DAY]
        }

        /**
         * Get the current day of the month (1 through 31).
         * <P>
         * If you're looking for the day of the week (M-F or whatever)
         * or day of the year (1..365) then use java's Calendar.get()
        </P> */
        @JvmStatic
        fun day(): Int {
            return Calendar.getInstance()[Calendar.DAY_OF_MONTH]
        }

        /**
         * Get the current month in range 1 through 12.
         */
        @JvmStatic
        fun month(): Int {
            // months are number 0..11 so change to colloquial 1..12
            return Calendar.getInstance()[Calendar.MONTH] + 1
        }

        /**
         * Get the current year.
         */
        @JvmStatic
        fun year(): Int {
            return Calendar.getInstance()[Calendar.YEAR]
        }

        /**
         * Attempt to open a file using the platform's shell.
         */
        // todo - not sure about nullability but mostly nullable
        @JvmStatic
        fun open(filename: String) {
            open(arrayOf(filename))
        }

        /**
         * Launch a process using a platforms shell. This version uses an array
         * to make it easier to deal with spaces in the individual elements.
         * (This avoids the situation of trying to put single or double quotes
         * around different bits).
         */
        @JvmStatic
        fun open(argv: Array<String>?): Process {
            return exec(argv)
        }

        @JvmStatic
        fun exec(argv: Array<String>?): Process {
            return try {
                Runtime.getRuntime().exec(argv!!)
            } catch (e: Exception) {
                throw RuntimeException("Could not open " + join(argv, ' '))
            }
        }


        //////////////////////////////////////////////////////////////

        // CURSOR

        // Removed, this doesn't make sense in a touch interface.
        //  int cursorType = ARROW; // cursor type
        //  boolean cursorVisible = true; // cursor visibility flag
        //  PImage invisibleCursor;
        /**
         * Set the cursor type
         */
        //  public void cursor(int cursorType) {
        //    setCursor(Cursor.getPredefinedCursor(cursorType));
        //    cursorVisible = true;
        //    this.cursorType = cursorType;
        //  }
        /**
         * Replace the cursor with the specified PImage. The x- and y-
         * coordinate of the center will be the center of the image.
         */
        //  public void cursor(PImage image) {
        //    cursor(image, image.width/2, image.height/2);
        //  }
        /**
         * Set a custom cursor to an image with a specific hotspot.
         * Only works with JDK 1.2 and later.
         * Currently seems to be broken on Java 1.4 for Mac OS X
         * <P>
         * Based on code contributed by Amit Pitaru, plus additional
         * code to handle Java versions via reflection by Jonathan Feinberg.
         * Reflection removed for release 0128 and later.
        </P> */
        //  public void cursor(PImage image, int hotspotX, int hotspotY) {
        //    // don't set this as cursor type, instead use cursor_type
        //    // to save the last cursor used in case cursor() is called
        //    //cursor_type = Cursor.CUSTOM_CURSOR;
        //    Image jimage =
        //      createImage(new MemoryImageSource(image.width, image.height,
        //                                        image.pixels, 0, image.width));
        //    Point hotspot = new Point(hotspotX, hotspotY);
        //    Toolkit tk = Toolkit.getDefaultToolkit();
        //    Cursor cursor = tk.createCustomCursor(jimage, hotspot, "Custom Cursor");
        //    setCursor(cursor);
        //    cursorVisible = true;
        //  }
        /**
         * Show the cursor after noCursor() was called.
         * Notice that the program remembers the last set cursor type
         */
        //  public void cursor() {
        //    // maybe should always set here? seems dangerous, since
        //    // it's likely that java will set the cursor to something
        //    // else on its own, and the applet will be stuck b/c bagel
        //    // thinks that the cursor is set to one particular thing
        //    if (!cursorVisible) {
        //      cursorVisible = true;
        //      setCursor(Cursor.getPredefinedCursor(cursorType));
        //    }
        //  }
        /**
         * Hide the cursor by creating a transparent image
         * and using it as a custom cursor.
         */
        //  public void noCursor() {
        //    if (!cursorVisible) return;  // don't hide if already hidden.
        //
        //    if (invisibleCursor == null) {
        //      invisibleCursor = new PImage(16, 16, ARGB);
        //    }
        //    // was formerly 16x16, but the 0x0 was added by jdf as a fix
        //    // for macosx, which wasn't honoring the invisible cursor
        //    cursor(invisibleCursor, 8, 8);
        //    cursorVisible = false;
        //  }


        //////////////////////////////////////////////////////////////

        @JvmStatic
        fun print(what: Byte) {
            kotlin.io.print(what)
            System.out.flush()
        }

        @JvmStatic
        fun print(what: Boolean) {
            kotlin.io.print(what)
            System.out.flush()
        }

        @JvmStatic
        fun print(what: Char) {
            kotlin.io.print(what)
            System.out.flush()
        }

        @JvmStatic
        fun print(what: Int) {
            kotlin.io.print(what)
            System.out.flush()
        }

        @JvmStatic
        fun print(what: Float) {
            kotlin.io.print(what)
            System.out.flush()
        }

        @JvmStatic
        fun print(what: String?) {
            kotlin.io.print(what)
            System.out.flush()
        }

        /**
         * @param variables list of data, separated by commas
         */
        // todo - args nullable type
        @JvmStatic
        fun print(vararg variables: Any?) {
            val sb = StringBuilder()
            for (o in variables) {
                if (sb.isNotEmpty()) {
                    sb.append(" ")
                }
                if (o == null) {
                    sb.append("null")
                } else {
                    sb.append(o.toString())
                }
            }
            kotlin.io.print(sb.toString())
        }

        /*
  static public void print(Object what) {
    if (what == null) {
      // special case since this does fuggly things on > 1.1
      System.out.print("null");
    } else {
      System.out.println(what.toString());
    }
  }
  */

        @JvmStatic
        fun println() {
            //println()
            kotlin.io.println()
        }


        @JvmStatic
        fun println(what: Byte) {
            print(what)
            kotlin.io.println()
        }

        @JvmStatic
        fun println(what: Boolean) {
            print(what)
            kotlin.io.println()
        }

        @JvmStatic
        fun println(what: Char) {
            print(what)
            kotlin.io.println()
        }

        @JvmStatic
        fun println(what: Int) {
            print(what)
            kotlin.io.println()
        }

        @JvmStatic
        fun println(what: Float) {
            print(what)
            kotlin.io.println()
        }

        @JvmStatic
        fun println(what: String?) {
            print(what)
            kotlin.io.println()
        }

        /**
         * @param variables list of data, separated by commas
         */
        @JvmStatic
        fun println(vararg variables: Any?) {
//    System.out.println("got " + variables.length + " variables");
            print(*variables)
            kotlin.io.println()
        }

        // todo - {what} is of nullable type
        @JvmStatic
        fun println(what: Any?) {
            if (what == null) {
                // special case since this does fuggly things on > 1.1
                kotlin.io.println("null")
            } else {
                val name = what!!.javaClass.name
                if (name[0] == '[') {
                    when (name[1]) {
                        '[' ->           // don't even mess with multi-dimensional arrays (case '[')
                            // or anything else that's not int, float, boolean, char
                            kotlin.io.println(what)
                        'L' -> {
                            // print a 1D array of objects as individual elements
                            val poo = what as Array<Any>
                            var i = 0
                            while (i < poo.size) {
                                if (poo[i] is String) {
                                    kotlin.io.println("[" + i + "] \"" + poo[i] + "\"")
                                } else {
                                    kotlin.io.println("[" + i + "] " + poo[i])
                                }
                                i++
                            }
                        }
                        'Z' -> {
                            val zz = what as BooleanArray
                            var i = 0
                            while (i < zz.size) {
                                kotlin.io.println("[" + i + "] " + zz[i])
                                i++
                            }
                        }
                        'B' -> {
                            val bb = what as ByteArray
                            var i = 0
                            while (i < bb.size) {
                                kotlin.io.println("[" + i + "] " + bb[i])
                                i++
                            }
                        }
                        'C' -> {
                            val cc = what as CharArray
                            var i = 0
                            while (i < cc.size) {
                                kotlin.io.println("[" + i + "] '" + cc[i] + "'")
                                i++
                            }
                        }
                        'I' -> {
                            val ii = what as IntArray
                            var i = 0
                            while (i < ii.size) {
                                kotlin.io.println("[" + i + "] " + ii[i])
                                i++
                            }
                        }
                        'F' -> {
                            val ff = what as FloatArray
                            var i = 0
                            while (i < ff.size) {
                                kotlin.io.println("[" + i + "] " + ff[i])
                                i++
                            }
                        }
                        else -> println(what)
                    }
                } else {  // not an array
                    kotlin.io.println(what)
                }
            }
        }

        /**
         * @webref output:text_area
         * @param what one-dimensional array
         * @usage IDE
         * @see PApplet.print
         * @see PApplet.println
         */
        // todo - {what} is of nullable type
        @JvmStatic
        fun printArray(what: Any?) {
            if (what == null) {
                // special case since this does fuggly things on > 1.1
                kotlin.io.println("null")
            } else {
                val name = what!!.javaClass.name
                if (name[0] == '[') {
                    when (name[1]) {
                        '[' ->           // don't even mess with multi-dimensional arrays (case '[')
                            // or anything else that's not int, float, boolean, char
                            kotlin.io.println(what)
                        'L' -> {
                            // print a 1D array of objects as individual elements
                            val poo = what as Array<Any>
                            var i = 0
                            while (i < poo.size) {
                                if (poo[i] is String) {
                                    kotlin.io.println("[" + i + "] \"" + poo[i] + "\"")
                                } else {
                                    kotlin.io.println("[" + i + "] " + poo[i])
                                }
                                i++
                            }
                        }
                        'Z' -> {
                            val zz = what as BooleanArray
                            var i = 0
                            while (i < zz.size) {
                                kotlin.io.println("[" + i + "] " + zz[i])
                                i++
                            }
                        }
                        'B' -> {
                            val bb = what as ByteArray
                            var i = 0
                            while (i < bb.size) {
                                kotlin.io.println("[" + i + "] " + bb[i])
                                i++
                            }
                        }
                        'C' -> {
                            val cc = what as CharArray
                            var i = 0
                            while (i < cc.size) {
                                kotlin.io.println("[" + i + "] '" + cc[i] + "'")
                                i++
                            }
                        }
                        'I' -> {
                            val ii = what as IntArray
                            var i = 0
                            while (i < ii.size) {
                                kotlin.io.println("[" + i + "] " + ii[i])
                                i++
                            }
                        }
                        'J' -> {
                            val jj = what as LongArray
                            var i = 0
                            while (i < jj.size) {
                                kotlin.io.println("[" + i + "] " + jj[i])
                                i++
                            }
                        }
                        'F' -> {
                            val ff = what as FloatArray
                            var i = 0
                            while (i < ff.size) {
                                kotlin.io.println("[" + i + "] " + ff[i])
                                i++
                            }
                        }
                        'D' -> {
                            val dd = what as DoubleArray
                            var i = 0
                            while (i < dd.size) {
                                kotlin.io.println("[" + i + "] " + dd[i])
                                i++
                            }
                        }
                        else -> println(what)
                    }
                } else {  // not an array
                    kotlin.io.println(what)
                }
            }
            System.out.flush()
        }

        //
        /*
  // not very useful, because it only works for public (and protected?)
  // fields of a class, not local variables to methods
  public void printvar(String name) {
    try {
      Field field = getClass().getDeclaredField(name);
      println(name + " = " + field.get(this));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  */
        //////////////////////////////////////////////////////////////

        // MATH

        // lots of convenience methods for math with floats.
        // doubles are overkill for processing applets, and casting
        // things all the time is annoying, thus the functions below.
        @JvmStatic
        fun abs(n: Float): Float {
            return if (n < 0) -n else n
        }

        @JvmStatic
        fun abs(n: Int): Int {
            return if (n < 0) -n else n
        }

        @JvmStatic
        fun sq(a: Float): Float {
            return a * a
        }

        @JvmStatic
        fun sqrt(a: Float): Float {
            return Math.sqrt(a.toDouble()).toFloat()
        }

        @JvmStatic
        fun log(a: Float): Float {
            return Math.log(a.toDouble()).toFloat()
        }

        @JvmStatic
        fun exp(a: Float): Float {
            return Math.exp(a.toDouble()).toFloat()
        }

        @JvmStatic
        fun pow(a: Float, b: Float): Float {
            return Math.pow(a.toDouble(), b.toDouble()).toFloat()
        }

        @JvmStatic
        fun max(a: Int, b: Int): Int {
            return if (a > b) a else b
        }

        @JvmStatic
        fun max(a: Float, b: Float): Float {
            return if (a > b) a else b
        }

        @JvmStatic
        fun max(a: Int, b: Int, c: Int): Int {
            return if (a > b) if (a > c) a else c else if (b > c) b else c
        }

        @JvmStatic
        fun max(a: Float, b: Float, c: Float): Float {
            return if (a > b) if (a > c) a else c else if (b > c) b else c
        }

        /**
         * Find the maximum value in an array.
         * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
         * @param list the source array
         * @return The maximum value
         */
        @JvmStatic
        fun max(list: IntArray): Int {
            if (list.size == 0) {
                throw ArrayIndexOutOfBoundsException(ERROR_MIN_MAX)
            }
            var max = list[0]
            for (i in 1 until list.size) {
                if (list[i] > max) max = list[i]
            }
            return max
        }

        /**
         * Find the maximum value in an array.
         * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
         * @param list the source array
         * @return The maximum value
         */
        @JvmStatic
        fun max(list: FloatArray): Float {
            if (list.size == 0) {
                throw ArrayIndexOutOfBoundsException(ERROR_MIN_MAX)
            }
            var max = list[0]
            for (i in 1 until list.size) {
                if (list[i] > max) max = list[i]
            }
            return max
        }

        @JvmStatic
        fun min(a: Int, b: Int): Int {
            return if (a < b) a else b
        }

        @JvmStatic
        fun min(a: Float, b: Float): Float {
            return if (a < b) a else b
        }

        @JvmStatic
        fun min(a: Int, b: Int, c: Int): Int {
            return if (a < b) if (a < c) a else c else if (b < c) b else c
        }

        @JvmStatic
        fun min(a: Float, b: Float, c: Float): Float {
            return if (a < b) if (a < c) a else c else if (b < c) b else c
        }

        /**
         * Find the minimum value in an array.
         * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
         * @param list the source array
         * @return The minimum value
         */
        @JvmStatic
        fun min(list: IntArray): Int {
            if (list.size == 0) {
                throw ArrayIndexOutOfBoundsException(ERROR_MIN_MAX)
            }
            var min = list[0]
            for (i in 1 until list.size) {
                if (list[i] < min) min = list[i]
            }
            return min
        }

        /**
         * Find the minimum value in an array.
         * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
         * @param list the source array
         * @return The minimum value
         */
        @JvmStatic
        fun min(list: FloatArray): Float {
            if (list.size == 0) {
                throw ArrayIndexOutOfBoundsException(ERROR_MIN_MAX)
            }
            var min = list[0]
            for (i in 1 until list.size) {
                if (list[i] < min) min = list[i]
            }
            return min
        }

        @JvmStatic
        fun constrain(amt: Int, low: Int, high: Int): Int {
            return if (amt < low) low else if (amt > high) high else amt
        }

        @JvmStatic
        fun constrain(amt: Float, low: Float, high: Float): Float {
            return if (amt < low) low else if (amt > high) high else amt
        }

        @JvmStatic
        fun sin(angle: Float): Float {
            return Math.sin(angle.toDouble()).toFloat()
        }

        @JvmStatic
        fun cos(angle: Float): Float {
            return Math.cos(angle.toDouble()).toFloat()
        }

        @JvmStatic
        fun tan(angle: Float): Float {
            return Math.tan(angle.toDouble()).toFloat()
        }

        @JvmStatic
        fun asin(value: Float): Float {
            return Math.asin(value.toDouble()).toFloat()
        }

        @JvmStatic
        fun acos(value: Float): Float {
            return Math.acos(value.toDouble()).toFloat()
        }

        @JvmStatic
        fun atan(value: Float): Float {
            return Math.atan(value.toDouble()).toFloat()
        }

        @JvmStatic
        fun atan2(a: Float, b: Float): Float {
            return Math.atan2(a.toDouble(), b.toDouble()).toFloat()
        }

        @JvmStatic
        fun degrees(radians: Float): Float {
            return radians * PConstants.RAD_TO_DEG
        }

        @JvmStatic
        fun radians(degrees: Float): Float {
            return degrees * PConstants.DEG_TO_RAD
        }

        @JvmStatic
        fun ceil(what: Float): Int {
            return Math.ceil(what.toDouble()).toInt()
        }

        @JvmStatic
        fun floor(what: Float): Int {
            return Math.floor(what.toDouble()).toInt()
        }

        @JvmStatic
        fun round(what: Float): Int {
            return Math.round(what)
        }

        @JvmStatic
        fun mag(a: Float, b: Float): Float {
            return Math.sqrt(a * a + b * b.toDouble()).toFloat()
        }

        @JvmStatic
        fun mag(a: Float, b: Float, c: Float): Float {
            return Math.sqrt(a * a + b * b + (c * c).toDouble()).toFloat()
        }

        @JvmStatic
        fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            return sqrt(sq(x2 - x1) + sq(y2 - y1))
        }

        @JvmStatic
        fun dist(x1: Float, y1: Float, z1: Float,
                 x2: Float, y2: Float, z2: Float): Float {
            return sqrt(sq(x2 - x1) + sq(y2 - y1) + sq(z2 - z1))
        }

        @JvmStatic
        fun lerp(start: Float, stop: Float, amt: Float): Float {
            return start + (stop - start) * amt
        }

        /**
         * Normalize a value to exist between 0 and 1 (inclusive).
         * Mathematically the opposite of lerp(), figures out what proportion
         * a particular value is relative to start and stop coordinates.
         */
        @JvmStatic
        fun norm(value: Float, start: Float, stop: Float): Float {
            return (value - start) / (stop - start)
        }

        /**
         * Convenience function to map a variable from one coordinate space
         * to another. Equivalent to unlerp() followed by lerp().
         */
        @JvmStatic
        fun map(value: Float,
                istart: Float, istop: Float,
                ostart: Float, ostop: Float): Float {
            return ostart + (ostop - ostart) * ((value - istart) / (istop - istart))
        }


        //////////////////////////////////////////////////////////////

        // PERLIN NOISE

        // [toxi 040903]
        // octaves and amplitude amount per octave are now user controlled
        // via the noiseDetail() function.
        // [toxi 030902]
        // cleaned up code and now using bagel's cosine table to speed up
        // [toxi 030901]
        // implementation by the german demo group farbrausch
        // as used in their demo "art": http://www.farb-rausch.de/fr010src.zip

        const val PERLIN_YWRAPB = 4

        const val PERLIN_YWRAP = 1 shl PERLIN_YWRAPB

        const val PERLIN_ZWRAPB = 8

        const val PERLIN_ZWRAP = 1 shl PERLIN_ZWRAPB

        const val PERLIN_SIZE = 4095

        // todo - nullable type both args and return-type
        @JvmStatic
        fun loadJSONObject(file: File?): JSONObject {
            // can't pass of createReader() to the constructor b/c of resource leak
            val reader = createReader(file)
            val outgoing = JSONObject(reader)
            try {
                reader.close()
            } catch (e: IOException) {  // not sure what would cause this
                e.printStackTrace()
            }
            return outgoing
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun loadJSONArray(file: File?): JSONArray {
            // can't pass of createReader() to the constructor b/c of resource leak
            val reader = createReader(file)
            val outgoing = JSONArray(reader)
            try {
                reader.close()
            } catch (e: IOException) {  // not sure what would cause this
                e.printStackTrace()
            }
            return outgoing
        }

        // "relative" -> no effect with the Files version, but important for listPaths
        // "recursive"
        // "extension=js" or "extensions=js|csv|txt" (no dot)
        // "directories" -> only directories
        // "files" -> only files
        // "hidden" -> include hidden files (prefixed with .) disabled by default

        @JvmStatic
        fun listFiles(base: File, vararg options: String): Array<File>? {
            var recursive = false
            var extensions: Array<String?>? = null
            var directories = true
            var files = true
            var hidden = false
            for (opt in options) {
                if (opt == "recursive") {
                    recursive = true
                } else if (opt.startsWith("extension=")) {
                    extensions = arrayOf(opt.substring(10))
                } else if (opt.startsWith("extensions=")) {
                    extensions = split(opt.substring(10), ',')
                } else if (opt == "files") {
                    directories = false
                } else if (opt == "directories") {
                    files = false
                } else if (opt == "hidden") {
                    hidden = true
                } else if (opt == "relative") {
                    // ignored
                } else {
                    throw RuntimeException("$opt is not a listFiles() option")
                }
            }
            if (extensions != null) {
                for (i in extensions.indices) {
                    extensions[i] = "." + extensions[i]
                }
            }
            if (!files && !directories) {
                // just make "only files" and "only directories" mean... both
                files = true
                directories = true
            }
            if (!base.canRead()) {
                return null
            }
            val outgoing: MutableList<File> = ArrayList()
            listFilesImpl(base, recursive, extensions, hidden, directories, files, outgoing)
            return outgoing.toTypedArray()
        }

        @JvmStatic
        fun listFilesImpl(folder: File, recursive: Boolean,
                          extensions: Array<String?>?, hidden: Boolean,
                          directories: Boolean, files: Boolean,
                          list: MutableList<File>) {
            val items = folder.listFiles()
            if (items != null) {
                for (item in items) {
                    val name = item.name
                    if (!hidden && name[0] == '.') {
                        continue
                    }
                    if (item.isDirectory) {
                        if (recursive) {
                            listFilesImpl(item, recursive, extensions, hidden, directories, files, list)
                        }
                        if (directories) {
                            list.add(item)
                        }
                    } else if (files) {
                        if (extensions == null) {
                            list.add(item)
                        } else {
                            for (ext in extensions) {
                                if (item.name.toLowerCase().endsWith(ext!!)) {
                                    list.add(item)
                                }
                            }
                        }
                    }
                }
            }
        }


        //////////////////////////////////////////////////////////////

        // EXTENSIONS

        /**
         * Get the compression-free extension for this filename.
         * @param filename The filename to check
         * @return an extension, skipping past .gz if it's present
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        fun checkExtension(filename: String): String? {
            // Don't consider the .gz as part of the name, createInput()
            // and createOuput() will take care of fixing that up.
            var filename = filename
            if (filename.toLowerCase().endsWith(".gz")) {
                filename = filename.substring(0, filename.length - 3)
            }
            val dotIndex = filename.lastIndexOf('.')
            return if (dotIndex != -1) {
                filename.substring(dotIndex + 1).toLowerCase()
            } else null
        }

        /**
         * I want to read lines from a file. And I'm still annoyed.
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        fun createReader(file: File?): BufferedReader {
            return try {
                var `is`: InputStream = FileInputStream(file)
                if (file!!.name.toLowerCase().endsWith(".gz")) {
                    `is` = GZIPInputStream(`is`)
                }
                createReader(`is`)
            } catch (e: Exception) {
                if (file == null) {
                    throw RuntimeException("File passed to createReader() was null")
                } else {
                    e.printStackTrace()
                    throw RuntimeException("Couldn't create a reader for " +
                            file.absolutePath)
                }
            }
            //return null;
        }

        /**
         * I want to read lines from a stream. If I have to type the
         * following lines any more I'm gonna send Sun my medical bills.
         */
        // todo - nullable-type args and return-type - not sure
        @JvmStatic
        fun createReader(input: InputStream?): BufferedReader {
            val isr = InputStreamReader(input, charsetUTF8)
            val reader = BufferedReader(isr)
            // consume the Unicode BOM (byte order marker) if present
            try {
                reader.mark(1)
                val c = reader.read()
                // if not the BOM, back up to the beginning again
                if (c != '\uFEFF'.toInt()) {
                    reader.reset()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return reader
        }

        /**
         * I want to print lines to a file. I have RSI from typing these
         * eight lines of code so many times.
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        fun createWriter(file: File?): PrintWriter {
            return try {
                var output: OutputStream = FileOutputStream(file)
                if (file!!.name.toLowerCase().endsWith(".gz")) {
                    output = GZIPOutputStream(output)
                }
                createWriter(output)
            } catch (e: Exception) {
                if (file == null) {
                    throw RuntimeException("File passed to createWriter() was null")
                } else {
                    e.printStackTrace()
                    throw RuntimeException("Couldn't create a writer for " +
                            file.absolutePath)
                }
            }
            //return null;
        }

        /**
         * I want to print lines to a file. Why am I always explaining myself?
         * It's the JavaSoft API engineers who need to explain themselves.
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        fun createWriter(output: OutputStream?): PrintWriter {
            val bos = BufferedOutputStream(output, 8192)
            val osw = OutputStreamWriter(bos, charsetUTF8)
            return PrintWriter(osw)
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun createInput(file: File?): InputStream? {
            requireNotNull(file) { "File passed to createInput() was null" }
            return try {
                val input: InputStream = FileInputStream(file)
                if (file.name.toLowerCase().endsWith(".gz")) {
                    BufferedInputStream(GZIPInputStream(input))
                } else BufferedInputStream(input)
            } catch (e: IOException) {
                System.err.println("Could not createInput() for $file")
                e.printStackTrace()
                null
            }
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun loadBytes(input: InputStream?): ByteArray? {
            try {
                val bis = BufferedInputStream(input)
                val out = ByteArrayOutputStream()
                var c = bis.read()
                while (c != -1) {
                    out.write(c)
                    c = bis.read()
                }
                return out.toByteArray()
            } catch (e: IOException) {
                e.printStackTrace()
                //throw new RuntimeException("Couldn't load bytes from stream");
            }
            return null
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun loadBytes(file: File?): ByteArray? {
            val `is` = createInput(file)
            return loadBytes(`is`)
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun loadStrings(file: File?): Array<String?>? {
            val `is` = createInput(file)
            return `is`?.let { loadStrings(it) }
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun loadStrings(reader: BufferedReader): Array<String?>? {
            try {
                var lines = arrayOfNulls<String>(100)
                var lineCount = 0
                var line: String? = null
                while (reader.readLine().also { line = it } != null) {
                    if (lineCount == lines.size) {
                        val temp = arrayOfNulls<String>(lineCount shl 1)
                        System.arraycopy(lines, 0, temp, 0, lineCount)
                        lines = temp
                    }
                    lines[lineCount++] = line
                }
                reader.close()
                if (lineCount == lines.size) {
                    return lines
                }

                // resize array to appropriate amount for these lines
                val output = arrayOfNulls<String>(lineCount)
                System.arraycopy(lines, 0, output, 0, lineCount)
                return output
            } catch (e: IOException) {
                e.printStackTrace()
                //throw new RuntimeException("Error inside loadStrings()");
            }
            return null
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun loadStrings(input: InputStream?): Array<String?>? {
            try {
                val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
                var lines = arrayOfNulls<String>(100)
                var lineCount = 0
                var line: String? = null
                while (reader.readLine().also { line = it } != null) {
                    if (lineCount == lines.size) {
                        val temp = arrayOfNulls<String>(lineCount shl 1)
                        System.arraycopy(lines, 0, temp, 0, lineCount)
                        lines = temp
                    }
                    lines[lineCount++] = line
                }
                reader.close()
                if (lineCount == lines.size) {
                    return lines
                }

                // resize array to appropriate amount for these lines
                val output = arrayOfNulls<String>(lineCount)
                System.arraycopy(lines, 0, output, 0, lineCount)
                return output
            } catch (e: IOException) {
                e.printStackTrace()
                //throw new RuntimeException("Error inside loadStrings()");
            }
            return null
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun createOutput(file: File?): OutputStream? {
            try {
                createPath(file) // make sure the path exists
                val output: OutputStream = FileOutputStream(file)
                return if (file!!.name.toLowerCase().endsWith(".gz")) {
                    BufferedOutputStream(GZIPOutputStream(output))
                } else BufferedOutputStream(output)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        // todo - nullable-type args
        @JvmStatic
        fun saveStream(target: File, source: InputStream?): Boolean {
            var tempFile: File? = null
            return try {
                // make sure that this path actually exists before writing
                createPath(target)
                tempFile = createTempFile(target)
                var targetStream: FileOutputStream? = FileOutputStream(tempFile)
                saveStream(targetStream, source)
                targetStream!!.close()
                targetStream = null
                if (target.exists()) {
                    if (!target.delete()) {
                        System.err.println("Could not replace " +
                                target.absolutePath + ".")
                    }
                }
                if (!tempFile.renameTo(target)) {
                    System.err.println("Could not rename temporary file " +
                            tempFile.absolutePath)
                    return false
                }
                true
            } catch (e: IOException) {
                tempFile?.delete()
                e.printStackTrace()
                false
            }
        }

        // todo - nullable-type args
        @JvmStatic
        @Throws(IOException::class)
        fun saveStream(target: OutputStream?,
                       source: InputStream?) {
            val bis = BufferedInputStream(source, 16384)
            val bos = BufferedOutputStream(target)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (bis.read(buffer).also { bytesRead = it } != -1) {
                bos.write(buffer, 0, bytesRead)
            }
            bos.flush()
        }

        /**
         * Creates a temporary file based on the name/extension of another file
         * and in the same parent directory. Ensures that the same extension is used
         * (i.e. so that .gz files are gzip compressed on output) and that it's done
         * from the same directory so that renaming the file later won't cross file
         * system boundaries.
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        @Throws(IOException::class)
        private fun createTempFile(file: File): File {
            val parentDir = file.parentFile
            val name = file.name
            var prefix: String
            var suffix: String? = null
            val dot = name.lastIndexOf('.')
            if (dot == -1) {
                prefix = name
            } else {
                // preserve the extension so that .gz works properly
                prefix = name.substring(0, dot)
                suffix = name.substring(dot)
            }
            // Prefix must be three characters
            if (prefix.length < 3) {
                prefix += "processing"
            }
            return File.createTempFile(prefix, suffix, parentDir)
        }

        /**
         * Saves bytes to a specific File location specified by the user.
         */
        // todo - nullable-type args
        @JvmStatic
        fun saveBytes(file: File, data: ByteArray?) {
            var tempFile: File? = null
            try {
                tempFile = createTempFile(file)
                var output = createOutput(tempFile)
                saveBytes(output, data)
                output!!.close()
                output = null
                if (file.exists()) {
                    if (!file.delete()) {
                        System.err.println("Could not replace " + file.absolutePath)
                    }
                }
                if (!tempFile.renameTo(file)) {
                    System.err.println("Could not rename temporary file " +
                            tempFile.absolutePath)
                }
            } catch (e: IOException) {
                System.err.println("error saving bytes to $file")
                tempFile?.delete()
                e.printStackTrace()
            }
        }

        /**
         * Spews a buffer of bytes to an OutputStream.
         */
        // todo - nullable-type args
        @JvmStatic
        fun saveBytes(output: OutputStream?, data: ByteArray?) {
            try {
                output!!.write(data)
                output.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // todo - nullable-type args
        @JvmStatic
        fun saveStrings(file: File, strings: Array<String?>) {
            try {
                val location = file.absolutePath
                createPath(location)
                var output: OutputStream = FileOutputStream(location)
                if (file.name.toLowerCase().endsWith(".gz")) {
                    output = GZIPOutputStream(output)
                }
                saveStrings(output, strings)
                output.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // todo - nullable-type args
        @JvmStatic
        fun saveStrings(output: OutputStream?, strings: Array<String?>) {
            try {
                val osw = OutputStreamWriter(output, "UTF-8")
                val writer = PrintWriter(osw)
                for (i in strings.indices) {
                    writer.println(strings[i])
                }
                writer.flush()
            } catch (e: UnsupportedEncodingException) {
            } // will not happen
        }

        /**
         * Takes a path and creates any in-between folders if they don't
         * already exist. Useful when trying to save to a subfolder that
         * may not actually exist.
         */
        // todo - nullable-type args
        @JvmStatic
        fun createPath(path: String?) {
            createPath(File(path))
        }

        // todo - nullable-type args
        @JvmStatic
        fun createPath(file: File?) {
            try {
                val parent = file!!.parent
                if (parent != null) {
                    val unit = File(parent)
                    if (!unit.exists()) unit.mkdirs()
                }
            } catch (se: SecurityException) {
                System.err.println("You don't have permissions to create " + file!!.absolutePath)
            }
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun getExtension(filename: String): String {
            var extension: String
            val lower = filename.toLowerCase()
            val dot = filename.lastIndexOf('.')
            if (dot == -1) {
                extension = "unknown" // no extension found
            }
            extension = lower.substring(dot + 1)

            // check for, and strip any parameters on the url, i.e.
            // filename.jpg?blah=blah&something=that
            val question = extension.indexOf('?')
            if (question != -1) {
                extension = extension.substring(0, question)
            }
            return extension
        }


        //////////////////////////////////////////////////////////////

        // URL ENCODING

        // todo - nullable-type args and return-type
        @JvmStatic
        fun urlEncode(what: String?): String? {
            return try {
                URLEncoder.encode(what, "UTF-8")
            } catch (e: UnsupportedEncodingException) {  // oh c'mon
                null
            }
        }

        // todo - nullable-type args and return-type
        @JvmStatic
        fun urlDecode(what: String?): String? {
            return try {
                URLDecoder.decode(what, "UTF-8")
            } catch (e: UnsupportedEncodingException) {  // safe per the JDK source
                null
            }
        }


        //////////////////////////////////////////////////////////////

        // SORT

        @JvmStatic
        @JvmOverloads
        fun sort(what: ByteArray, count: Int = what.size): ByteArray {
            val outgoing = ByteArray(what.size)
            System.arraycopy(what, 0, outgoing, 0, what.size)
            Arrays.sort(outgoing, 0, count)
            return outgoing
        }

        @JvmStatic
        @JvmOverloads
        fun sort(what: CharArray, count: Int = what.size): CharArray {
            val outgoing = CharArray(what.size)
            System.arraycopy(what, 0, outgoing, 0, what.size)
            Arrays.sort(outgoing, 0, count)
            return outgoing
        }

        @JvmStatic
        @JvmOverloads
        fun sort(what: IntArray, count: Int = what.size): IntArray {
            val outgoing = IntArray(what.size)
            System.arraycopy(what, 0, outgoing, 0, what.size)
            Arrays.sort(outgoing, 0, count)
            return outgoing
        }

        @JvmStatic
        @JvmOverloads
        fun sort(what: FloatArray, count: Int = what.size): FloatArray {
            val outgoing = FloatArray(what.size)
            System.arraycopy(what, 0, outgoing, 0, what.size)
            Arrays.sort(outgoing, 0, count)
            return outgoing
        }

        @JvmStatic
        @JvmOverloads
        fun sort(what: Array<String?>, count: Int = what.size): Array<String?> {
            val outgoing = arrayOfNulls<String>(what.size)
            System.arraycopy(what, 0, outgoing, 0, what.size)
            Arrays.sort(outgoing, 0, count)
            return outgoing
        }


        //////////////////////////////////////////////////////////////

        // ARRAY UTILITIES

        /**
         * Calls System.arraycopy(), included here so that we can
         * avoid people needing to learn about the System object
         * before they can just copy an array.
         */
        // todo - some args are @ Non-nullable
        @JvmStatic
        fun arrayCopy(src: Any?, srcPosition: Int,
                      dst: Any?, dstPosition: Int,
                      length: Int) {
            System.arraycopy(src, srcPosition, dst, dstPosition, length)
        }

        /**
         * Convenience method for arraycopy().
         * Identical to <CODE>arraycopy(src, 0, dst, 0, length);</CODE>
         */
        // todo - some args are @ Non-nullable
        @JvmStatic
        fun arrayCopy(src: Any?, dst: Any?, length: Int) {
            System.arraycopy(src, 0, dst, 0, length)
        }

        /**
         * Shortcut to copy the entire contents of
         * the source into the destination array.
         * Identical to <CODE>arraycopy(src, 0, dst, 0, src.length);</CODE>
         */
        @JvmStatic
        fun arrayCopy(src: Any?, dst: Any?) {
            System.arraycopy(src, 0, dst, 0, java.lang.reflect.Array.getLength(src))
        }

        //
        @JvmStatic
        @JvmOverloads
        fun expand(list: BooleanArray, newSize: Int = list.size shl 1): BooleanArray {
            val temp = BooleanArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        @JvmOverloads
        fun expand(list: ByteArray, newSize: Int = list.size shl 1): ByteArray {
            val temp = ByteArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        @JvmOverloads
        fun expand(list: CharArray, newSize: Int = list.size shl 1): CharArray {
            val temp = CharArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        @JvmOverloads
        fun expand(list: IntArray, newSize: Int = list.size shl 1): IntArray {
            val temp = IntArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        @JvmOverloads
        fun expand(list: LongArray, newSize: Int = if (list.size > 0) list.size shl 1 else 1): LongArray {
            val temp = LongArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        @JvmOverloads
        fun expand(list: FloatArray, newSize: Int = list.size shl 1): FloatArray {
            val temp = FloatArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        @JvmOverloads
        fun expand(list: DoubleArray, newSize: Int = if (list.size > 0) list.size shl 1 else 1): DoubleArray {
            val temp = DoubleArray(newSize)
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }


        @JvmStatic
        @JvmOverloads
        fun expand(list: Array<String?>, newSize: Int = list.size shl 1): Array<String?> {
            val temp = arrayOfNulls<String>(newSize)
            // in case the new size is smaller than list.length
            System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.size))
            return temp
        }

        @JvmStatic
        fun expand(array: Any): Any {
            return expand(array, java.lang.reflect.Array.getLength(array) shl 1)
        }

        @JvmStatic
        fun expand(list: Any, newSize: Int): Any {
            val type = list.javaClass.componentType
            val temp = java.lang.reflect.Array.newInstance(type, newSize)
            System.arraycopy(list, 0, temp, 0,
                    Math.min(java.lang.reflect.Array.getLength(list), newSize))
            return temp
        }

        //
        // contract() has been removed in revision 0124, use subset() instead.
        // (expand() is also functionally equivalent)
        //

        @JvmStatic
        fun append(b: ByteArray, value: Byte): ByteArray {
            var b = b
            b = expand(b, b.size + 1)
            b[b.size - 1] = value
            return b
        }

        @JvmStatic
        fun append(b: CharArray, value: Char): CharArray {
            var b = b
            b = expand(b, b.size + 1)
            b[b.size - 1] = value
            return b
        }

        @JvmStatic
        fun append(b: IntArray, value: Int): IntArray {
            var b = b
            b = expand(b, b.size + 1)
            b[b.size - 1] = value
            return b
        }

        @JvmStatic
        fun append(b: FloatArray, value: Float): FloatArray {
            var b = b
            b = expand(b, b.size + 1)
            b[b.size - 1] = value
            return b
        }

        @JvmStatic
        fun append(b: Array<String?>, value: String?): Array<String?> {
            var b = b
            b = expand(b, b.size + 1)
            b[b.size - 1] = value
            return b
        }

        @JvmStatic
        fun append(b: Any, value: Any?): Any {
            var b = b
            val length = java.lang.reflect.Array.getLength(b)
            b = expand(b, length + 1)
            java.lang.reflect.Array.set(b, length, value)
            return b
        }


        @JvmStatic
        fun shorten(list: BooleanArray): BooleanArray {
            return subset(list, 0, list.size - 1)
        }

        @JvmStatic
        fun shorten(list: ByteArray): ByteArray {
            return subset(list, 0, list.size - 1)
        }

        @JvmStatic
        fun shorten(list: CharArray): CharArray {
            return subset(list, 0, list.size - 1)
        }

        @JvmStatic
        fun shorten(list: IntArray): IntArray {
            return subset(list, 0, list.size - 1)
        }

        @JvmStatic
        fun shorten(list: FloatArray): FloatArray {
            return subset(list, 0, list.size - 1)
        }

        @JvmStatic
        fun shorten(list: Array<String?>): Array<String?> {
            return subset(list, 0, list.size - 1)
        }

        @JvmStatic
        fun shorten(list: Any): Any {
            val length = java.lang.reflect.Array.getLength(list)
            return subset(list, 0, length - 1)
        }

        //
        @JvmStatic
        fun splice(list: BooleanArray,
                   v: Boolean, index: Int): BooleanArray {
            val outgoing = BooleanArray(list.size + 1)
            System.arraycopy(list, 0, outgoing, 0, index)
            outgoing[index] = v
            System.arraycopy(list, index, outgoing, index + 1,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: BooleanArray,
                   v: BooleanArray, index: Int): BooleanArray {
            val outgoing = BooleanArray(list.size + v.size)
            System.arraycopy(list, 0, outgoing, 0, index)
            System.arraycopy(v, 0, outgoing, index, v.size)
            System.arraycopy(list, index, outgoing, index + v.size,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: ByteArray,
                   v: Byte, index: Int): ByteArray {
            val outgoing = ByteArray(list.size + 1)
            System.arraycopy(list, 0, outgoing, 0, index)
            outgoing[index] = v
            System.arraycopy(list, index, outgoing, index + 1,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: ByteArray,
                   v: ByteArray, index: Int): ByteArray {
            val outgoing = ByteArray(list.size + v.size)
            System.arraycopy(list, 0, outgoing, 0, index)
            System.arraycopy(v, 0, outgoing, index, v.size)
            System.arraycopy(list, index, outgoing, index + v.size,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: CharArray,
                   v: Char, index: Int): CharArray {
            val outgoing = CharArray(list.size + 1)
            System.arraycopy(list, 0, outgoing, 0, index)
            outgoing[index] = v
            System.arraycopy(list, index, outgoing, index + 1,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: CharArray,
                   v: CharArray, index: Int): CharArray {
            val outgoing = CharArray(list.size + v.size)
            System.arraycopy(list, 0, outgoing, 0, index)
            System.arraycopy(v, 0, outgoing, index, v.size)
            System.arraycopy(list, index, outgoing, index + v.size,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: IntArray,
                   v: Int, index: Int): IntArray {
            val outgoing = IntArray(list.size + 1)
            System.arraycopy(list, 0, outgoing, 0, index)
            outgoing[index] = v
            System.arraycopy(list, index, outgoing, index + 1,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: IntArray,
                   v: IntArray, index: Int): IntArray {
            val outgoing = IntArray(list.size + v.size)
            System.arraycopy(list, 0, outgoing, 0, index)
            System.arraycopy(v, 0, outgoing, index, v.size)
            System.arraycopy(list, index, outgoing, index + v.size,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: FloatArray,
                   v: Float, index: Int): FloatArray {
            val outgoing = FloatArray(list.size + 1)
            System.arraycopy(list, 0, outgoing, 0, index)
            outgoing[index] = v
            System.arraycopy(list, index, outgoing, index + 1,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: FloatArray,
                   v: FloatArray, index: Int): FloatArray {
            val outgoing = FloatArray(list.size + v.size)
            System.arraycopy(list, 0, outgoing, 0, index)
            System.arraycopy(v, 0, outgoing, index, v.size)
            System.arraycopy(list, index, outgoing, index + v.size,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: Array<String?>,
                   v: String?, index: Int): Array<String?> {
            val outgoing = arrayOfNulls<String>(list.size + 1)
            System.arraycopy(list, 0, outgoing, 0, index)
            outgoing[index] = v
            System.arraycopy(list, index, outgoing, index + 1,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: Array<String?>,
                   v: Array<String?>, index: Int): Array<String?> {
            val outgoing = arrayOfNulls<String>(list.size + v.size)
            System.arraycopy(list, 0, outgoing, 0, index)
            System.arraycopy(v, 0, outgoing, index, v.size)
            System.arraycopy(list, index, outgoing, index + v.size,
                    list.size - index)
            return outgoing
        }

        @JvmStatic
        fun splice(list: Any?, v: Any, index: Int): Any {
            var outgoing: Array<Any?>? = null
            val length = java.lang.reflect.Array.getLength(list)

            // check whether item being spliced in is an array
            if (v.javaClass.name[0] == '[') {
                val vlength = java.lang.reflect.Array.getLength(v)
                outgoing = arrayOfNulls(length + vlength)
                System.arraycopy(list, 0, outgoing, 0, index)
                System.arraycopy(v, 0, outgoing, index, vlength)
                System.arraycopy(list, index, outgoing, index + vlength, length - index)
            } else {
                outgoing = arrayOfNulls(length + 1)
                System.arraycopy(list, 0, outgoing, 0, index)
                java.lang.reflect.Array.set(outgoing, index, v)
                System.arraycopy(list, index, outgoing, index + 1, length - index)
            }
            return outgoing
        }


        @JvmStatic
        @JvmOverloads
        fun subset(list: BooleanArray, start: Int, count: Int = list.size - start): BooleanArray {
            val output = BooleanArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmStatic
        @JvmOverloads
        fun subset(list: ByteArray, start: Int, count: Int = list.size - start): ByteArray {
            val output = ByteArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmStatic
        @JvmOverloads
        fun subset(list: CharArray, start: Int, count: Int = list.size - start): CharArray {
            val output = CharArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmOverloads
        fun subset(list: IntArray, start: Int, count: Int = list.size - start): IntArray {
            val output = IntArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmStatic
        @JvmOverloads
        fun subset(list: LongArray, start: Int, count: Int = list.size - start): LongArray {
            val output = LongArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmStatic
        @JvmOverloads
        fun subset(list: FloatArray, start: Int, count: Int = list.size - start): FloatArray {
            val output = FloatArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmOverloads
        fun subset(list: DoubleArray, start: Int, count: Int = list.size - start): DoubleArray {
            val output = DoubleArray(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmStatic
        @JvmOverloads
        fun subset(list: Array<String?>, start: Int, count: Int = list.size - start): Array<String?> {
            val output = arrayOfNulls<String>(count)
            System.arraycopy(list, start, output, 0, count)
            return output
        }

        @JvmStatic
        fun subset(list: Any, start: Int): Any {
            val length = java.lang.reflect.Array.getLength(list)
            return subset(list, start, length - start)
        }

        @JvmStatic
        fun subset(list: Any, start: Int, count: Int): Any {
            val type = list.javaClass.componentType
            val outgoing = java.lang.reflect.Array.newInstance(type, count)
            System.arraycopy(list, start, outgoing, 0, count)
            return outgoing
        }


        @JvmStatic
        fun concat(a: BooleanArray, b: BooleanArray): BooleanArray {
            val c = BooleanArray(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun concat(a: ByteArray, b: ByteArray): ByteArray {
            val c = ByteArray(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun concat(a: CharArray, b: CharArray): CharArray {
            val c = CharArray(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun concat(a: IntArray, b: IntArray): IntArray {
            val c = IntArray(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun concat(a: FloatArray, b: FloatArray): FloatArray {
            val c = FloatArray(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun concat(a: Array<String?>, b: Array<String?>): Array<String?> {
            val c = arrayOfNulls<String>(a.size + b.size)
            System.arraycopy(a, 0, c, 0, a.size)
            System.arraycopy(b, 0, c, a.size, b.size)
            return c
        }

        @JvmStatic
        fun concat(a: Any, b: Any?): Any {
            val type = a.javaClass.componentType
            val alength = java.lang.reflect.Array.getLength(a)
            val blength = java.lang.reflect.Array.getLength(b)
            val outgoing = java.lang.reflect.Array.newInstance(type, alength + blength)
            System.arraycopy(a, 0, outgoing, 0, alength)
            System.arraycopy(b, 0, outgoing, alength, blength)
            return outgoing
        }

        //
        @JvmStatic
        fun reverse(list: BooleanArray): BooleanArray {
            val outgoing = BooleanArray(list.size)
            val length1 = list.size - 1
            for (i in list.indices) {
                outgoing[i] = list[length1 - i]
            }
            return outgoing
        }

        @JvmStatic
        fun reverse(list: ByteArray): ByteArray {
            val outgoing = ByteArray(list.size)
            val length1 = list.size - 1
            for (i in list.indices) {
                outgoing[i] = list[length1 - i]
            }
            return outgoing
        }

        @JvmStatic
        fun reverse(list: CharArray): CharArray {
            val outgoing = CharArray(list.size)
            val length1 = list.size - 1
            for (i in list.indices) {
                outgoing[i] = list[length1 - i]
            }
            return outgoing
        }

        @JvmStatic
        fun reverse(list: IntArray): IntArray {
            val outgoing = IntArray(list.size)
            val length1 = list.size - 1
            for (i in list.indices) {
                outgoing[i] = list[length1 - i]
            }
            return outgoing
        }

        @JvmStatic
        fun reverse(list: FloatArray): FloatArray {
            val outgoing = FloatArray(list.size)
            val length1 = list.size - 1
            for (i in list.indices) {
                outgoing[i] = list[length1 - i]
            }
            return outgoing
        }

        @JvmStatic
        fun reverse(list: Array<String?>): Array<String?> {
            val outgoing = arrayOfNulls<String>(list.size)
            val length1 = list.size - 1
            for (i in list.indices) {
                outgoing[i] = list[length1 - i]
            }
            return outgoing
        }

        @JvmStatic
        fun reverse(list: Any): Any {
            val type = list.javaClass.componentType
            val length = java.lang.reflect.Array.getLength(list)
            val outgoing = java.lang.reflect.Array.newInstance(type, length)
            for (i in 0 until length) {
                java.lang.reflect.Array.set(outgoing, i, java.lang.reflect.Array.get(list, length - 1 - i))
            }
            return outgoing
        }


        //////////////////////////////////////////////////////////////

        // STRINGS

        /**
         * Remove whitespace characters from the beginning and ending
         * of a String. Works like String.trim() but includes the
         * unicode nbsp character as well.
         */
        @JvmStatic
        fun trim(str: String): String {
            return str.replace('\u00A0', ' ').trim { it <= ' ' }
        }

        /**
         * Trim the whitespace from a String array. This returns a new
         * array and does not affect the passed-in array.
         */
        @JvmStatic
        fun trim(array: Array<String?>): Array<String?> {
            val outgoing = arrayOfNulls<String>(array.size)
            for (i in array.indices) {
                if (array[i] != null) {
                    outgoing[i] = array[i]!!.replace('\u00A0', ' ').trim { it <= ' ' }
                }
            }
            return outgoing
        }

        /**
         * Join an array of Strings together as a single String,
         * separated by the whatever's passed in for the separator.
         */
        @JvmStatic
        fun join(str: Array<String>?, separator: Char): String {
            return join(str, separator.toString())
        }

        /**
         * Join an array of Strings together as a single String,
         * separated by the whatever's passed in for the separator.
         * <P>
         * To use this on numbers, first pass the array to nf() or nfs()
         * to get a list of String objects, then use join on that.
        </P> * <PRE>
         * e.g. String stuff[] = { "apple", "bear", "cat" };
         * String list = join(stuff, ", ");
         * // list is now "apple, bear, cat"</PRE>
         */
        @JvmStatic
        fun join(str: Array<String>?, separator: String): String {
            val buffer = StringBuffer()
            for (i in str!!.indices) {
                if (i != 0) buffer.append(separator)
                buffer.append(str[i])
            }
            return buffer.toString()
        }
        /**
         * Splits a string into pieces, using any of the chars in the
         * String 'delim' as separator characters. For instance,
         * in addition to white space, you might want to treat commas
         * as a separator. The delimeter characters won't appear in
         * the returned String array.
         * <PRE>
         * i.e. splitTokens("a, b", " ,") -> { "a", "b" }
        </PRE> *
         * To include all the whitespace possibilities, use the variable
         * WHITESPACE, found in PConstants:
         * <PRE>
         * i.e. splitTokens("a   | b", WHITESPACE + "|");  ->  { "a", "b" }</PRE>
         */
        /**
         * Split the provided String at wherever whitespace occurs.
         * Multiple whitespace (extra spaces or tabs or whatever)
         * between items will count as a single break.
         * <P>
         * The whitespace characters are "\t\n\r\f", which are the defaults
         * for java.util.StringTokenizer, plus the unicode non-breaking space
         * character, which is found commonly on files created by or used
         * in conjunction with Mac OS X (character 160, or 0x00A0 in hex).
        </P> * <PRE>
         * i.e. splitTokens("a b") -> { "a", "b" }
         * splitTokens("a    b") -> { "a", "b" }
         * splitTokens("a\tb") -> { "a", "b" }
         * splitTokens("a \t  b  ") -> { "a", "b" }</PRE>
         */
        @JvmStatic
        @JvmOverloads
        fun splitTokens(what: String?, delim: String? = PConstants.WHITESPACE): Array<String?> {
            val toker = StringTokenizer(what, delim)
            val pieces = arrayOfNulls<String>(toker.countTokens())
            var index = 0
            while (toker.hasMoreTokens()) {
                pieces[index++] = toker.nextToken()
            }
            return pieces
        }

        /**
         * Split a string into pieces along a specific character.
         * Most commonly used to break up a String along a space or a tab
         * character.
         * <P>
         * This operates differently than the others, where the
         * single delimeter is the only breaking point, and consecutive
         * delimeters will produce an empty string (""). This way,
         * one can split on tab characters, but maintain the column
         * alignments (of say an excel file) where there are empty columns.
        </P> */
        @JvmStatic
        fun split(what: String?, delim: Char): Array<String?>? {
            // do this so that the exception occurs inside the user's
            // program, rather than appearing to be a bug inside split()
            if (what == null) return null
            //return split(what, String.valueOf(delim));  // huh
            val chars = what.toCharArray()
            var splitCount = 0 //1;

            for (i in chars.indices) {
                if (chars[i] == delim) splitCount++
            }
            // make sure that there is something in the input string
            //if (chars.length > 0) {
            // if the last char is a delimeter, get rid of it..
            //if (chars[chars.length-1] == delim) splitCount--;
            // on second thought, i don't agree with this, will disable
            //}
            if (splitCount == 0) {
                val splits = arrayOfNulls<String>(1)
                splits[0] = what.toString()
                return splits
            }
            //int pieceCount = splitCount + 1;
            val splits = arrayOfNulls<String>(splitCount + 1)
            var splitIndex = 0
            var startIndex = 0
            for (i in chars.indices) {
                if (chars[i] == delim) {
                    splits[splitIndex++] = String(chars, startIndex, i - startIndex)
                    startIndex = i + 1
                }
            }
            //if (startIndex != chars.length) {
            splits[splitIndex] = String(chars, startIndex, chars.size - startIndex)
            //}
            return splits
        }

        /**
         * Split a String on a specific delimiter. Unlike Java's String.split()
         * method, this does not parse the delimiter as a regexp because it's more
         * confusing than necessary, and String.split() is always available for
         * those who want regexp.
         */
        @JvmStatic
        fun split(what: String, delim: String): Array<String?> {
            val items = ArrayList<String>()
            var index: Int
            var offset = 0
            while (what.indexOf(delim, offset).also { index = it } != -1) {
                items.add(what.substring(offset, index))
                offset = index + delim.length
            }
            items.add(what.substring(offset))
            val outgoing = arrayOfNulls<String>(items.size)
            items.toArray(outgoing)
            return outgoing
        }

        protected var matchPatterns: HashMap<String, Pattern?>? = null

        // todo - nullable-type args and return-type
        @JvmStatic
        fun matchPattern(regexp: String): Pattern? {
            var p: Pattern? = null
            if (matchPatterns == null) {
                matchPatterns = HashMap()
            } else {
                p = matchPatterns!![regexp]
            }
            if (p == null) {
                if (matchPatterns!!.size == 10) {
                    // Just clear out the match patterns here if more than 10 are being
                    // used. It's not terribly efficient, but changes that you have >10
                    // different match patterns are very slim, unless you're doing
                    // something really tricky (like custom match() methods), in which
                    // case match() won't be efficient anyway. (And you should just be
                    // using your own Java code.) The alternative is using a queue here,
                    // but that's a silly amount of work for negligible benefit.
                    matchPatterns!!.clear()
                }
                p = Pattern.compile(regexp, Pattern.MULTILINE or Pattern.DOTALL)
                matchPatterns!![regexp] = p
            }
            return p
        }

        /**
         * Match a string with a regular expression, and returns the match as an
         * array. The first index is the matching expression, and array elements
         * [1] and higher represent each of the groups (sequences found in parens).
         *
         * This uses multiline matching (Pattern.MULTILINE) and dotall mode
         * (Pattern.DOTALL) by default, so that ^ and $ match the beginning and
         * end of any lines found in the source, and the . operator will also
         * pick up newline characters.
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        fun match(what: String?, regexp: String): Array<String?>? {
            val p = matchPattern(regexp)
            val m = p!!.matcher(what)
            if (m.find()) {
                val count = m.groupCount() + 1
                val groups = arrayOfNulls<String>(count)
                for (i in 0 until count) {
                    groups[i] = m.group(i)
                }
                return groups
            }
            return null
        }

        /**
         * Identical to match(), except that it returns an array of all matches in
         * the specified String, rather than just the first.
         */
        // todo - nullable-type args and return-type
        @JvmStatic
        fun matchAll(what: String?, regexp: String): Array<Array<String?>>? {
            val p = matchPattern(regexp)
            val m = p!!.matcher(what)
            val results = ArrayList<Array<String?>>()
            val count = m.groupCount() + 1
            while (m.find()) {
                val groups = arrayOfNulls<String>(count)
                for (i in 0 until count) {
                    groups[i] = m.group(i)
                }
                results.add(groups)
            }
            if (results.isEmpty()) {
                return null
            }
            val matches = Array(results.size) { arrayOfNulls<String>(count) }
            for (i in matches.indices) {
                matches[i] = results[i]
            }
            return matches
        }


        //////////////////////////////////////////////////////////////

        // CASTING FUNCTIONS, INSERTED BY PREPROC

        /**
         * Convert a char to a boolean. 'T', 't', and '1' will become the
         * boolean value true, while 'F', 'f', or '0' will become false.
         */
        /*
  static final public boolean parseBoolean(char what) {
    return ((what == 't') || (what == 'T') || (what == '1'));
  }
  */
        /**
         *
         * Convert an integer to a boolean. Because of how Java handles upgrading
         * numbers, this will also cover byte and char (as they will upgrade to
         * an int without any sort of explicit cast).
         *
         * The preprocessor will convert boolean(what) to parseBoolean(what).
         * @return false if 0, true if any other number
         */
        @JvmStatic
        fun parseBoolean(what: Int): Boolean {
            return what != 0
        }

        /*
  // removed because this makes no useful sense
  static final public boolean parseBoolean(float what) {
    return (what != 0);
  }
  */
        /**
         * Convert the string "true" or "false" to a boolean.
         * @return true if 'what' is "true" or "TRUE", false otherwise
         */
        @JvmStatic
        fun parseBoolean(what: String): Boolean {
            return what.toBoolean()
        }


        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        /*
  // removed, no need to introduce strange syntax from other languages
  static final public boolean[] parseBoolean(char what[]) {
    boolean outgoing[] = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] =
        ((what[i] == 't') || (what[i] == 'T') || (what[i] == '1'));
    }
    return outgoing;
  }
  */
        /**
         * Convert a byte array to a boolean array. Each element will be
         * evaluated identical to the integer case, where a byte equal
         * to zero will return false, and any other value will return true.
         * @return array of boolean elements
         */
        @JvmStatic
        fun parseBoolean(what: ByteArray): BooleanArray {
            val outgoing = BooleanArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i] != 0.toByte()
            }
            return outgoing
        }

        /**
         * Convert an int array to a boolean array. An int equal
         * to zero will return false, and any other value will return true.
         * @return array of boolean elements
         */
        @JvmStatic
        fun parseBoolean(what: IntArray): BooleanArray {
            val outgoing = BooleanArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i] != 0
            }
            return outgoing
        }

        /*
  // removed, not necessary... if necessary, convert to int array first
  static final public boolean[] parseBoolean(float what[]) {
    boolean outgoing[] = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (what[i] != 0);
    }
    return outgoing;
  }
  */
        @JvmStatic
        fun parseBoolean(what: Array<String>): BooleanArray {
            val outgoing = BooleanArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i].toBoolean()
            }
            return outgoing
        }

        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        @JvmStatic
        fun parseByte(what: Boolean): Byte {
            return if (what) 1.toByte() else 0
        }

        @JvmStatic
        fun parseByte(what: Char): Byte {
            return what.toByte()
        }

        @JvmStatic
        fun parseByte(what: Int): Byte {
            return what.toByte()
        }

        @JvmStatic
        fun parseByte(what: Float): Byte {
            return what.toByte()
        }

        /*
  // nixed, no precedent
  static final public byte[] parseByte(String what) {  // note: array[]
    return what.getBytes();
  }
  */
        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        @JvmStatic
        fun parseByte(what: BooleanArray): ByteArray {
            val outgoing = ByteArray(what.size)
            for (i in what.indices) {
                outgoing[i] = if (what[i]) 1.toByte() else 0
            }
            return outgoing
        }

        @JvmStatic
        fun parseByte(what: CharArray): ByteArray {
            val outgoing = ByteArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i].toByte()
            }
            return outgoing
        }

        @JvmStatic
        fun parseByte(what: IntArray): ByteArray {
            val outgoing = ByteArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i].toByte()
            }
            return outgoing
        }

        @JvmStatic
        fun parseByte(what: FloatArray): ByteArray {
            val outgoing = ByteArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i].toByte()
            }
            return outgoing
        }

        /*
  static final public byte[][] parseByte(String what[]) {  // note: array[][]
    byte outgoing[][] = new byte[what.length][];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = what[i].getBytes();
    }
    return outgoing;
  }
  */

        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        /*
  static final public char parseChar(boolean what) {  // 0/1 or T/F ?
    return what ? 't' : 'f';
  }
  */
        @JvmStatic
        fun parseChar(what: Byte): Char {
            return (what and 0xff.toByte()) as Char
        }

        @JvmStatic
        fun parseChar(what: Int): Char {
            return what.toChar()
        }

        /*
  static final public char parseChar(float what) {  // nonsensical
    return (char) what;
  }

  static final public char[] parseChar(String what) {  // note: array[]
    return what.toCharArray();
  }
  */
        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        /*
  static final public char[] parseChar(boolean what[]) {  // 0/1 or T/F ?
    char outgoing[] = new char[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = what[i] ? 't' : 'f';
    }
    return outgoing;
  }
  */
        @JvmStatic
        fun parseChar(what: ByteArray): CharArray {
            val outgoing = CharArray(what.size)
            for (i in what.indices) {
                outgoing[i] = (what[i] and 0xff.toByte()) as Char
            }
            return outgoing
        }

        @JvmStatic
        fun parseChar(what: IntArray): CharArray {
            val outgoing = CharArray(what.size)
            for (i in what.indices) {
                outgoing[i] = what[i].toChar()
            }
            return outgoing
        }

        /*
  static final public char[] parseChar(float what[]) {  // nonsensical
    char outgoing[] = new char[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (char) what[i];
    }
    return outgoing;
  }

  static final public char[][] parseChar(String what[]) {  // note: array[][]
    char outgoing[][] = new char[what.length][];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = what[i].toCharArray();
    }
    return outgoing;
  }
  */
        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        @JvmStatic
        fun parseInt(what: Boolean): Int {
            return if (what) 1 else 0
        }

        /**
         * Note that parseInt() will un-sign a signed byte value.
         */
        @JvmStatic
        fun parseInt(what: Byte): Int {
            return (what and 0xff.toByte()).toInt()
        }

        /**
         * Note that parseInt('5') is unlike String in the sense that it
         * won't return 5, but the ascii value. This is because ((int) someChar)
         * returns the ascii value, and parseInt() is just longhand for the cast.
         */
        @JvmStatic
        fun parseInt(what: Char): Int {
            return what.toInt()
        }

        /**
         * Same as floor(), or an (int) cast.
         */
        @JvmStatic
        fun parseInt(what: Float): Int {
            return what.toInt()
        }
        /**
         * Parse a String to an int, and provide an alternate value that
         * should be used when the number is invalid.
         */
        /**
         * Parse a String into an int value. Returns 0 if the value is bad.
         */
        @JvmStatic
        @JvmOverloads
        fun parseInt(what: String, otherwise: Int = 0): Int {
            try {
                val offset = what.indexOf('.')
                return if (offset == -1) {
                    what.toInt()
                } else {
                    what.substring(0, offset).toInt()
                }
            } catch (e: NumberFormatException) {
            }
            return otherwise
        }


        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        @JvmStatic
        fun parseInt(what: BooleanArray): IntArray {
            val list = IntArray(what.size)
            for (i in what.indices) {
                list[i] = if (what[i]) 1 else 0
            }
            return list
        }

        @JvmStatic
        fun parseInt(what: ByteArray): IntArray {  // note this unsigns
            val list = IntArray(what.size)
            for (i in what.indices) {
                list[i] = (what[i] and 0xff.toByte()).toInt()
            }
            return list
        }

        @JvmStatic
        fun parseInt(what: CharArray): IntArray {
            val list = IntArray(what.size)
            for (i in what.indices) {
                list[i] = what[i].toInt()
            }
            return list
        }

        @JvmStatic
        fun parseInt(what: FloatArray): IntArray {
            val inties = IntArray(what.size)
            for (i in what.indices) {
                inties[i] = what[i].toInt()
            }
            return inties
        }
        /**
         * Make an array of int elements from an array of String objects.
         * If the String can't be parsed as a number, its entry in the
         * array will be set to the value of the "missing" parameter.
         *
         * String s[] = { "1", "300", "apple", "44" };
         * int numbers[] = parseInt(s, 9999);
         *
         * numbers will contain { 1, 300, 9999, 44 }
         */
        /**
         * Make an array of int elements from an array of String objects.
         * If the String can't be parsed as a number, it will be set to zero.
         *
         * String s[] = { "1", "300", "44" };
         * int numbers[] = parseInt(s);
         *
         * numbers will contain { 1, 300, 44 }
         */
        @JvmStatic
        @JvmOverloads
        fun parseInt(what: Array<String>, missing: Int = 0): IntArray {
            val output = IntArray(what.size)
            for (i in what.indices) {
                try {
                    output[i] = what[i].toInt()
                } catch (e: NumberFormatException) {
                    output[i] = missing
                }
            }
            return output
        }


        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        /*
  static final public float parseFloat(boolean what) {
    return what ? 1 : 0;
  }
  */
        /**
         * Convert an int to a float value. Also handles bytes because of
         * Java's rules for upgrading values.
         */
        @JvmStatic
        fun parseFloat(what: Int): Float {  // also handles byte
            return what.toFloat()
        }

        @JvmStatic
        @JvmOverloads
        fun parseFloat(what: String, otherwise: Float = Float.NaN): Float {
            try {
                return what.toFloat()
            } catch (e: NumberFormatException) {
            }
            return otherwise
        }

        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        /*
  static final public float[] parseFloat(boolean what[]) {
    float floaties[] = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      floaties[i] = what[i] ? 1 : 0;
    }
    return floaties;
  }

  static final public float[] parseFloat(char what[]) {
    float floaties[] = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      floaties[i] = (char) what[i];
    }
    return floaties;
  }
  */
        @JvmStatic
        fun parseByte(what: ByteArray): FloatArray {
            val floaties = FloatArray(what.size)
            for (i in what.indices) {
                floaties[i] = what[i].toFloat()
            }
            return floaties
        }

        @JvmStatic
        fun parseFloat(what: IntArray): FloatArray {
            val floaties = FloatArray(what.size)
            for (i in what.indices) {
                floaties[i] = what[i].toFloat()
            }
            return floaties
        }

        @JvmStatic
        @JvmOverloads
        fun parseFloat(what: Array<String>, missing: Float = Float.NaN): FloatArray {
            val output = FloatArray(what.size)
            for (i in what.indices) {
                try {
                    output[i] = what[i].toFloat()
                } catch (e: NumberFormatException) {
                    output[i] = missing
                }
            }
            return output
        }

        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        @JvmStatic
        fun str(x: Boolean): String {
            return x.toString()
        }

        @JvmStatic
        fun str(x: Byte): String {
            return x.toString()
        }

        @JvmStatic
        fun str(x: Char): String {
            return x.toString()
        }

        @JvmStatic
        fun str(x: Int): String {
            return x.toString()
        }

        @JvmStatic
        fun str(x: Float): String {
            return x.toString()
        }


        // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

        @JvmStatic
        fun str(x: BooleanArray): Array<String?> {
            val s = arrayOfNulls<String>(x.size)
            for (i in x.indices) s[i] = x[i].toString()
            return s
        }

        @JvmStatic
        fun str(x: ByteArray): Array<String?> {
            val s = arrayOfNulls<String>(x.size)
            for (i in x.indices) s[i] = x[i].toString()
            return s
        }

        @JvmStatic
        fun str(x: CharArray): Array<String?> {
            val s = arrayOfNulls<String>(x.size)
            for (i in x.indices) s[i] = x[i].toString()
            return s
        }

        @JvmStatic
        fun str(x: IntArray): Array<String?> {
            val s = arrayOfNulls<String>(x.size)
            for (i in x.indices) s[i] = x[i].toString()
            return s
        }

        @JvmStatic
        fun str(x: FloatArray): Array<String?> {
            val s = arrayOfNulls<String>(x.size)
            for (i in x.indices) s[i] = x[i].toString()
            return s
        }

        //////////////////////////////////////////////////////////////

        // INT NUMBER FORMATTING

        /**
         * Integer number formatter.
         */
        private var int_nf: NumberFormat? = null
        private var int_nf_digits = 0
        private var int_nf_commas = false

        // todo - nullable return type
        @JvmStatic
        fun nf(num: IntArray, digits: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nf(num[i], digits)
            }
            return formatted
        }

        // todo - nullable return type
        @JvmStatic
        fun nf(num: Int, digits: Int): String {
            if (int_nf != null &&
                    int_nf_digits == digits &&
                    !int_nf_commas) {
                return int_nf!!.format(num.toLong())
            }
            int_nf = NumberFormat.getInstance()
            int_nf!!.isGroupingUsed = false // no commas
            int_nf_commas = false
            int_nf!!.minimumIntegerDigits = digits
            int_nf_digits = digits
            return int_nf!!.format(num.toLong())
        }

        @JvmStatic
        fun nfc(num: IntArray): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nfc(num[i])
            }
            return formatted
        }

        // todo - return-type may be nullable
        @JvmStatic
        fun nfc(num: Int): String {
            if (int_nf != null &&
                    int_nf_digits == 0 &&
                    int_nf_commas) {
                return int_nf!!.format(num.toLong())
            }
            int_nf = NumberFormat.getInstance()
            int_nf!!.setGroupingUsed(true)
            int_nf_commas = true
            int_nf!!.setMinimumIntegerDigits(0)
            int_nf_digits = 0
            return int_nf!!.format(num.toLong())
        }

        /**
         * number format signed (or space)
         * Formats a number but leaves a blank space in the front
         * when it's positive so that it can be properly aligned with
         * numbers that have a negative sign in front of them.
         */
        // todo - return @ Nullable
        @JvmStatic
        fun nfs(num: Int, digits: Int): String {
            return if (num < 0) nf(num, digits) else ' '.toString() + nf(num, digits)
        }

        @JvmStatic
        fun nfs(num: IntArray, digits: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nfs(num[i], digits)
            }
            return formatted
        }

        /**
         * number format positive (or plus)
         * Formats a number, always placing a - or + sign
         * in the front when it's negative or positive.
         */
        @JvmStatic
        fun nfp(num: Int, digits: Int): String {
            return if (num < 0) nf(num, digits) else '+'.toString() + nf(num, digits)
        }

        @JvmStatic
        fun nfp(num: IntArray, digits: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nfp(num[i], digits)
            }
            return formatted
        }

        //////////////////////////////////////////////////////////////

        // FLOAT NUMBER FORMATTING

        private var float_nf: NumberFormat? = null
        private var float_nf_left = 0
        private var float_nf_right = 0
        private var float_nf_commas = false

        @JvmStatic
        fun nf(num: FloatArray, left: Int, right: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nf(num[i], left, right)
            }
            return formatted
        }

        @JvmStatic
        fun nf(num: Float, left: Int, right: Int): String {
            if (float_nf != null &&
                    float_nf_left == left &&
                    float_nf_right == right &&
                    !float_nf_commas) {
                return float_nf!!.format(num.toDouble())
            }
            float_nf = NumberFormat.getInstance()
            float_nf!!.setGroupingUsed(false)
            float_nf_commas = false
            if (left != 0) float_nf!!.setMinimumIntegerDigits(left)
            if (right != 0) {
                float_nf!!.setMinimumFractionDigits(right)
                float_nf!!.setMaximumFractionDigits(right)
            }
            float_nf_left = left
            float_nf_right = right
            return float_nf!!.format(num.toDouble())
        }

        @JvmStatic
        fun nfc(num: FloatArray, right: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nfc(num[i], right)
            }
            return formatted
        }

        @JvmStatic
        fun nfc(num: Float, right: Int): String {
            if (float_nf != null &&
                    float_nf_left == 0 &&
                    float_nf_right == right &&
                    float_nf_commas) {
                return float_nf!!.format(num.toDouble())
            }
            float_nf = NumberFormat.getInstance()
            float_nf!!.setGroupingUsed(true)
            float_nf_commas = true
            if (right != 0) {
                float_nf!!.setMinimumFractionDigits(right)
                float_nf!!.setMaximumFractionDigits(right)
            }
            float_nf_left = 0
            float_nf_right = right
            return float_nf!!.format(num.toDouble())
        }

        /**
         * Number formatter that takes into account whether the number
         * has a sign (positive, negative, etc) in front of it.
         */
        @JvmStatic
        fun nfs(num: FloatArray, left: Int, right: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nfs(num[i], left, right)
            }
            return formatted
        }

        @JvmStatic
        fun nfs(num: Float, left: Int, right: Int): String {
            return if (num < 0) nf(num, left, right) else ' '.toString() + nf(num, left, right)
        }

        @JvmStatic
        fun nfp(num: FloatArray, left: Int, right: Int): Array<String?> {
            val formatted = arrayOfNulls<String>(num.size)
            for (i in formatted.indices) {
                formatted[i] = nfp(num[i], left, right)
            }
            return formatted
        }

        @JvmStatic
        fun nfp(num: Float, left: Int, right: Int): String {
            return if (num < 0) nf(num, left, right) else '+'.toString() + nf(num, left, right)
        }


        //////////////////////////////////////////////////////////////

        // HEX/BINARY CONVERSION

        /**
         * Convert a byte into a two digit hex string.
         */
        @JvmStatic
        fun hex(what: Byte): String {
            return hex(what.toInt(), 2)
        }

        /**
         * Convert a Unicode character into a four digit hex string.
         */
        @JvmStatic
        fun hex(what: Char): String {
            return hex(what.toInt(), 4)
        }
        /**
         * Format an integer as a hex string using the specified number of digits.
         * @param what the value to format
         * @param digits the number of digits (maximum 8)
         * @return a String object with the formatted values
         */
        /**
         * Convert an integer into an eight digit hex string.
         */
        @JvmStatic
        @JvmOverloads
        fun hex(what: Int, digits: Int = 8): String {
            var digits = digits
            val stuff = Integer.toHexString(what).toUpperCase()
            if (digits > 8) {
                digits = 8
            }
            val length = stuff.length
            if (length > digits) {
                return stuff.substring(length - digits)
            } else if (length < digits) {
                return "00000000".substring(8 - (digits - length)) + stuff
            }
            return stuff
        }

        @JvmStatic
        fun unhex(what: String): Int {
            // has to parse as a Long so that it'll work for numbers bigger than 2^31
            return what.toLong(16).toInt()
        }

        /**
         * Returns a String that contains the binary value of a byte.
         * The returned value will always have 8 digits.
         */
        @JvmStatic
        fun binary(what: Byte): String {
            return binary(what.toInt(), 8)
        }

        /**
         * Returns a String that contains the binary value of a char.
         * The returned value will always have 16 digits because chars
         * are two bytes long.
         */
        @JvmStatic
        fun binary(what: Char): String {
            return binary(what.toInt(), 16)
        }
        /**
         * Returns a String that contains the binary value of an int.
         * The digits parameter determines how many digits will be used.
         */
        /**
         * Returns a String that contains the binary value of an int. The length
         * depends on the size of the number itself. If you want a specific number
         * of digits use binary(int what, int digits) to specify how many.
         */
        @JvmStatic
        @JvmOverloads
        fun binary(what: Int, digits: Int = 32): String {
            var digits = digits
            val stuff = Integer.toBinaryString(what)
            if (digits > 32) {
                digits = 32
            }
            val length = stuff.length
            if (length > digits) {
                return stuff.substring(length - digits)
            } else if (length < digits) {
                val offset = 32 - (digits - length)
                return "00000000000000000000000000000000".substring(offset) + stuff
            }
            return stuff
        }

        /**
         * Unpack a binary String into an int.
         * i.e. unbinary("00001000") would return 8.
         */
        @JvmStatic
        fun unbinary(what: String): Int {
            return what.toInt(2)
        }

        @JvmStatic
        fun blendColor(c1: Int, c2: Int, mode: Int): Int {
            return PImage.blendColor(c1, c2, mode)
        }

        //////////////////////////////////////////////////////////////

        // MAIN

        /**
         * Set this sketch to communicate its state back to the PDE.
         *
         *
         * This uses the stderr stream to write positions of the window
         * (so that it will be saved by the PDE for the next run) and
         * notify on quit. See more notes in the Worker class.
         */
        //  public void setupExternalMessages() {
        //
        //    frame.addComponentListener(new ComponentAdapter() {
        //        public void componentMoved(ComponentEvent e) {
        //          Point where = ((Frame) e.getSource()).getLocation();
        //          System.err.println(PApplet.EXTERNAL_MOVE + " " +
        //                             where.x + " " + where.y);
        //          System.err.flush();  // doesn't seem to help or hurt
        //        }
        //      });
        //
        //    frame.addWindowListener(new WindowAdapter() {
        //        public void windowClosing(WindowEvent e) {
        //          exit();  // don't quit, need to just shut everything down (0133)
        //        }
        //      });
        //  }
        /**
         * Set up a listener that will fire proper component resize events
         * in cases where frame.setResizable(true) is called.
         */
        //  public void setupFrameResizeListener() {
        //    frame.addComponentListener(new ComponentAdapter() {
        //
        //        public void componentResized(ComponentEvent e) {
        //          // Ignore bad resize events fired during setup to fix
        //          // http://dev.processing.org/bugs/show_bug.cgi?id=341
        //          // This should also fix the blank screen on Linux bug
        //          // http://dev.processing.org/bugs/show_bug.cgi?id=282
        //          if (frame.isResizable()) {
        //            // might be multiple resize calls before visible (i.e. first
        //            // when pack() is called, then when it's resized for use).
        //            // ignore them because it's not the user resizing things.
        //            Frame farm = (Frame) e.getComponent();
        //            if (farm.isVisible()) {
        //              Insets insets = farm.getInsets();
        //              Dimension windowSize = farm.getSize();
        //              int usableW = windowSize.width - insets.left - insets.right;
        //              int usableH = windowSize.height - insets.top - insets.bottom;
        //
        //              // the ComponentListener in PApplet will handle calling size()
        //              setBounds(insets.left, insets.top, usableW, usableH);
        //            }
        //          }
        //        }
        //      });
        //  }
        /**
         * GIF image of the Processing logo.
         */
        val ICON_IMAGE = byteArrayOf(
                71, 73, 70, 56, 57, 97, 16, 0, 16, 0, -77, 0, 0, 0, 0, 0, -1, -1, -1, 12,
                12, 13, -15, -15, -14, 45, 57, 74, 54, 80, 111, 47, 71, 97, 62, 88, 117,
                1, 14, 27, 7, 41, 73, 15, 52, 85, 2, 31, 55, 4, 54, 94, 18, 69, 109, 37,
                87, 126, -1, -1, -1, 33, -7, 4, 1, 0, 0, 15, 0, 44, 0, 0, 0, 0, 16, 0, 16,
                0, 0, 4, 122, -16, -107, 114, -86, -67, 83, 30, -42, 26, -17, -100, -45,
                56, -57, -108, 48, 40, 122, -90, 104, 67, -91, -51, 32, -53, 77, -78, -100,
                47, -86, 12, 76, -110, -20, -74, -101, 97, -93, 27, 40, 20, -65, 65, 48,
                -111, 99, -20, -112, -117, -123, -47, -105, 24, 114, -112, 74, 69, 84, 25,
                93, 88, -75, 9, 46, 2, 49, 88, -116, -67, 7, -19, -83, 60, 38, 3, -34, 2,
                66, -95, 27, -98, 13, 4, -17, 55, 33, 109, 11, 11, -2, -128, 121, 123, 62,
                91, 120, -128, 127, 122, 115, 102, 2, 119, 0, -116, -113, -119, 6, 102,
                121, -108, -126, 5, 18, 6, 4, -102, -101, -100, 114, 15, 17, 0, 59
        )

        /**
         * main() method for running this class from the command line.
         * <P>
         * <B>The options shown here are not yet finalized and will be
         * changing over the next several releases.</B>
        </P> * <P>
         * The simplest way to turn and applet into an application is to
         * add the following code to your program:
        </P> * <PRE>static public void main(String args[]) {
         * PApplet.main(new String[] { "YourSketchName" });
         * }</PRE>
         * This will properly launch your applet from a double-clickable
         * .jar or from the command line.
         * <PRE>
         * Parameters useful for launching or also used by the PDE:
         *
         * --location=x,y        upper-lefthand corner of where the applet
         * should appear on screen. if not used,
         * the default is to center on the main screen.
         *
         * --present             put the applet into full screen presentation
         * mode. requires java 1.4 or later.
         *
         * --exclusive           use full screen exclusive mode when presenting.
         * disables new windows or interaction with other
         * monitors, this is like a "game" mode.
         *
         * --hide-stop           use to hide the stop button in situations where
         * you don't want to allow users to exit. also
         * see the FAQ on information for capturing the ESC
         * key when running in presentation mode.
         *
         * --stop-color=#xxxxxx  color of the 'stop' text used to quit an
         * sketch when it's in present mode.
         *
         * --bgcolor=#xxxxxx     background color of the window.
         *
         * --sketch-path         location of where to save files from functions
         * like saveStrings() or saveFrame(). defaults to
         * the folder that the java application was
         * launched from, which means if this isn't set by
         * the pde, everything goes into the same folder
         * as processing.exe.
         *
         * --display=n           set what display should be used by this applet.
         * displays are numbered starting from 1.
         *
         * Parameters used by Processing when running via the PDE
         *
         * --external            set when the applet is being used by the PDE
         *
         * --editor-location=x,y position of the upper-lefthand corner of the
         * editor window, for placement of applet window
        </PRE> *
         */
        @JvmStatic
        fun main(args: Array<String>) {
            // just do a no-op for now
        }

        /**
         * @nowebref
         * Interpolate between two colors. Like lerp(), but for the
         * individual color components of a color supplied as an int value.
         */
        @JvmStatic
        fun lerpColor(c1: Int, c2: Int, amt: Float, mode: Int): Int {
            return PGraphics.lerpColor(c1, c2, amt, mode)
        }

        /**
         * Display a warning that the specified method is only available with 3D.
         * @param method The method name (no parentheses)
         */
        @JvmStatic
        fun showDepthWarning(method: String?) {
            PGraphics.showDepthWarning(method!!)
        }

        /**
         * Display a warning that the specified method that takes x, y, z parameters
         * can only be used with x and y parameters in this renderer.
         * @param method The method name (no parentheses)
         */
        @JvmStatic
        fun showDepthWarningXYZ(method: String?) {
            PGraphics.showDepthWarningXYZ(method!!)
        }

        /**
         * Display a warning that the specified method is simply unavailable.
         */
        @JvmStatic
        fun showMethodWarning(method: String?) {
            PGraphics.showMethodWarning(method!!)
        }

        /**
         * Error that a particular variation of a method is unavailable (even though
         * other variations are). For instance, if vertex(x, y, u, v) is not
         * available, but vertex(x, y) is just fine.
         */
        @JvmStatic
        fun showVariationWarning(str: String?) {
            PGraphics.showVariationWarning(str!!)
        }

        /**
         * Display a warning that the specified method is not implemented, meaning
         * that it could be either a completely missing function, although other
         * variations of it may still work properly.
         */
        @JvmStatic
        fun showMissingWarning(method: String?) {
            PGraphics.showMissingWarning(method!!)
        }
    }
}