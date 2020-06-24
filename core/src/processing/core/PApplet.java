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

package processing.core;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import android.app.FragmentManager;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.LayoutRes;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import processing.a2d.PGraphicsAndroid2D;
import processing.android.ActivityAPI;
import processing.android.AppComponent;
import processing.android.CompatUtils;
import processing.data.*;
import processing.event.*;
import processing.opengl.*;

public class PApplet extends Object implements ActivityAPI, PConstants {

  static final public boolean DEBUG = false;
//  static final public boolean DEBUG = true;

  // Convenience public constant holding the SDK version, akin to platform in Java mode
  static final public int SDK = Build.VERSION.SDK_INT;

  //static final public int SDK = Build.VERSION_CODES.ICE_CREAM_SANDWICH; // Forcing older SDK for testing

  /**
   * The surface this sketch draws to.
   */
  protected PSurface surface;

  /**
   * The view group containing the surface view of the PApplet.
   */
  public @LayoutRes int parentLayout = -1;

  /** The PGraphics renderer associated with this PApplet */
  public PGraphics g;

  /**
   * The screen size when the sketch was started. This is initialized inside
   * onCreate().
   * <p>
   * Note that this won't update if you change the resolution
   * of your screen once the the applet is running.
   * <p>
   * This variable is not static because in the desktop version of Processing,
   * not all instances of PApplet will necessarily be started on a screen of
   * the same size.
   */
  public int displayWidth, displayHeight;

  /**
   * Command line options passed in from main().
   * <P>
   * This does not include the arguments passed in to PApplet itself.
   */
//  public String[] args;

  /**
   * Path to where sketch can read/write files (read-only).
   * Android: This is the writable area for the Activity, which is correct
   * for purposes of how sketchPath is used in practice from a sketch,
   * even though it's technically different than the desktop version.
   */
  public String sketchPath; //folder;

  /** When debugging headaches */
//  static final boolean THREAD_DEBUG = false;

  /** Default width and height for applet when not specified */
  static public final int DEFAULT_WIDTH = -1;
  static public final int DEFAULT_HEIGHT = -1;

  /**
   * Set true when the surface dimensions have changed, so that the PGraphics
   * object can be resized on the next trip through handleDraw().
   */
  protected boolean surfaceChanged;

  /**
   * Pixel buffer from this applet's PGraphics.
   * <P>
   * When used with OpenGL or Java2D, this value will
   * be null until loadPixels() has been called.
   */
  public int[] pixels;

  /** width of this applet's associated PGraphics */
  public int width = DEFAULT_WIDTH;

  /** height of this applet's associated PGraphics */
  public int height = DEFAULT_HEIGHT;

  /** The logical density of the display from getDisplayMetrics().density
   * According to Android's documentation:
   * This is a scaling factor for the Density Independent Pixel unit,
   * where one DIP is one pixel on an approximately 160 dpi screen
   * (for example a 240x320, 1.5"x2" screen), providing the baseline of the
   * system's display. Thus on a 160dpi screen this density value will be 1;
   * on a 120 dpi screen it would be .75; etc.
   */
  public float displayDensity = 1;

  // For future use
  public int pixelDensity = 1;
  public int pixelWidth;
  public int pixelHeight;

  ///////////////////////////////////////////////////////////////
  // Mouse events

  /** absolute x position of input on screen */
  public int mouseX;

  /** absolute x position of input on screen */
  public int mouseY;


  /**
   * Previous x/y position of the mouse. This will be a different value
   * when inside a mouse handler (like the mouseMoved() method) versus
   * when inside draw(). Inside draw(), pmouseX is updated once each
   * frame, but inside mousePressed() and friends, it's updated each time
   * an event comes through. Be sure to use only one or the other type of
   * means for tracking pmouseX and pmouseY within your sketch, otherwise
   * you're gonna run into trouble.
   */
  public int pmouseX, pmouseY;

  public int mouseButton;

  public boolean mousePressed;


  public boolean touchIsStarted;


  public TouchEvent.Pointer[] touches = new TouchEvent.Pointer[0];


  /**
   * previous mouseX/Y for the draw loop, separated out because this is
   * separate from the pmouseX/Y when inside the mouse event handlers.
   */
  protected int dmouseX, dmouseY;


  /**
   * pmotionX/Y for the event handlers (motionPressed(), motionDragged() etc)
   * these are different because motion events are queued to the end of
   * draw, so the previous position has to be updated on each event,
   * as opposed to the pmotionX/Y that's used inside draw, which is expected
   * to be updated once per trip through draw().
   */
  protected int emouseX, emouseY;


  /**
   * ID of the pointer tracked for mouse events.
   */
  protected int mousePointerId;


  /**
   * ID of the most recently touch pointer gone up or down.
   */
  protected int touchPointerId;

  ///////////////////////////////////////////////////////////////
  // Key events

  /**
   * Last key pressed.
   * <P>
   * If it's a coded key, i.e. UP/DOWN/CTRL/SHIFT/ALT,
   * this will be set to CODED (0xffff or 65535).
   */
  public char key;

  /**
   * When "key" is set to CODED, this will contain a Java key code.
   * <P>
   * For the arrow keys, keyCode will be one of UP, DOWN, LEFT and RIGHT.
   * Also available are ALT, CONTROL and SHIFT. A full set of constants
   * can be obtained from java.awt.event.KeyEvent, from the VK_XXXX variables.
   */
  public int keyCode;

  /**
   * true if the mouse is currently pressed.
   */
  public boolean keyPressed;

  /**
   * the last KeyEvent object passed into a mouse function.
   */
//  public KeyEvent keyEvent;

  /**
   * Gets set to true/false as the applet gains/loses focus.
   */
  public boolean focused = false;

  /**
   * Keeps track of ENABLE_KEY_REPEAT hint
   */
  protected boolean keyRepeatEnabled = false;

  /**
   * Set to open when openKeyboard() is called, and used to close the keyboard when the sketch is
   * paused, otherwise it remains visible.
   */
  boolean keyboardIsOpen = false;

  /**
   * Flag to determine if the back key was pressed.
   */
  private boolean requestedBackPress = false;

  /**
   * Flag to determine if the user handled the back press.
   */
  public boolean handledBackPressed = true;

  ///////////////////////////////////////////////////////////////
  // Permission handling

  /**
   * Callback methods to handle permission requests
   */
  protected HashMap<String, String> permissionMethods = new HashMap<String, String>();


  /**
   * Permissions requested during one frame
   */
  protected ArrayList<String> reqPermissions = new ArrayList<String>();


  ///////////////////////////////////////////////////////////////
  // Rendering/timing

  /**
   * Time in milliseconds when the applet was started.
   * <P>
   * Used by the millis() function.
   */
  long millisOffset = System.currentTimeMillis();

  protected boolean insideDraw;

  /** Last time in nanoseconds that frameRate was checked */
  protected long frameRateLastNanos = 0;

  /**
   * The current value of frames per second.
   * <P>
   * The initial value will be 10 fps, and will be updated with each
   * frame thereafter. The value is not instantaneous (since that
   * wouldn't be very useful since it would jump around so much),
   * but is instead averaged (integrated) over several frames.
   * As such, this value won't be valid until after 5-10 frames.
   */
  public float frameRate = 10;

  protected boolean looping;

  /** flag set to true when a redraw is asked for by the user */
  protected boolean redraw;

  /**
   * How many frames have been displayed since the applet started.
   * <P>
   * This value is read-only <EM>do not</EM> attempt to set it,
   * otherwise bad things will happen.
   * <P>
   * Inside setup(), frameCount is 0.
   * For the first iteration of draw(), frameCount will equal 1.
   */
  public int frameCount;

  /**
   * true if this applet has had it.
   */
  public boolean finished;

  /**
   * true if exit() has been called so that things shut down
   * once the main thread kicks off.
   */
  protected boolean exitCalled;

  boolean insideSettings;

  String renderer = JAVA2D;

  int smooth = 1;  // default smoothing (whatever that means for the renderer)

  boolean fullScreen = false;

  int display = -1;  // use default

  // Background default needs to be different from the default value in
  // PGraphics.backgroundColor, otherwise size(100, 100) bg spills over.
  // https://github.com/processing/processing/issues/2297
  int windowColor = 0xffDDDDDD;

  ///////////////////////////////////////////////////////////////
  // Error messages

  static final String ERROR_MIN_MAX =
    "Cannot use min() or max() on an empty array.";

  ///////////////////////////////////////////////////////////////
  // Command line options

  /**
   * Position of the upper-lefthand corner of the editor window
   * that launched this applet.
   */
  static public final String ARGS_EDITOR_LOCATION = "--editor-location";

  /**
   * Location for where to position the applet window on screen.
   * <P>
   * This is used by the editor to when saving the previous applet
   * location, or could be used by other classes to launch at a
   * specific position on-screen.
   */
  static public final String ARGS_EXTERNAL = "--external";

  static public final String ARGS_LOCATION = "--location";

  static public final String ARGS_DISPLAY = "--display";

  static public final String ARGS_BGCOLOR = "--bgcolor";

  static public final String ARGS_PRESENT = "--present";

  static public final String ARGS_EXCLUSIVE = "--exclusive";

  static public final String ARGS_STOP_COLOR = "--stop-color";

  static public final String ARGS_HIDE_STOP = "--hide-stop";

  /**
   * Allows the user or PdeEditor to set a specific sketch folder path.
   * <P>
   * Used by PdeEditor to pass in the location where saveFrame()
   * and all that stuff should write things.
   */
  static public final String ARGS_SKETCH_FOLDER = "--sketch-path";

  /**
   * When run externally to a PdeEditor,
   * this is sent by the applet when it quits.
   */
  //static public final String EXTERNAL_QUIT = "__QUIT__";
  static public final String EXTERNAL_STOP = "__STOP__";

  /**
   * When run externally to a PDE Editor, this is sent by the applet
   * whenever the window is moved.
   * <P>
   * This is used so that the editor can re-open the sketch window
   * in the same position as the user last left it.
   */
  static public final String EXTERNAL_MOVE = "__MOVE__";

  /** true if this sketch is being run by the PDE */
  boolean external = false;


  //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////

  /**
   * Required empty constructor.
   */
  public PApplet() {

  }


  public PSurface getSurface() {
    return surface;
  }


  public Context getContext() {
    return surface.getContext();
  }


  public Activity getActivity() {
    return surface.getActivity();
  }


  public void initSurface(AppComponent component, SurfaceHolder holder) {
    parentLayout = -1;
    initSurface(null, null,  null, component, holder);
  }


  public void initSurface(LayoutInflater inflater, ViewGroup container,
                          Bundle savedInstanceState,
                          AppComponent component, SurfaceHolder holder) {
    if (DEBUG) println("initSurface() happening here: " + Thread.currentThread().getName());

    component.initDimensions();
    displayWidth = component.getDisplayWidth();
    displayHeight = component.getDisplayHeight();
    displayDensity = component.getDisplayDensity();

    handleSettings();

    boolean parentSize = false;
    if (parentLayout == -1) {
      if (fullScreen || width == -1 || height == -1) {
        // Either sketch explicitly set to full-screen mode, or not
        // size/fullScreen provided, so sketch uses the entire display
        width = displayWidth;
        height = displayHeight;
      }
    } else {
      if (fullScreen || width == -1 || height == -1) {
        // Dummy weight and height to initialize the PGraphics, will be resized
        // when the view associated to the parent layout is created
        width = 100;
        height = 100;
        parentSize = true;
      }
    }

    pixelWidth = width * pixelDensity;
    pixelHeight = height * pixelDensity;

    String rendererName = sketchRenderer();
    if (DEBUG) println("Renderer " + rendererName);
    g = makeGraphics(width, height, rendererName, true);
    if (DEBUG) println("Created renderer");
    surface = g.createSurface(component, holder, false);
    if (DEBUG) println("Created surface");

    if (parentLayout == -1) {
      setFullScreenVisibility();
      surface.initView(width, height);
    } else {
      surface.initView(width, height, parentSize,
                       inflater, container, savedInstanceState);
    }

    finished = false; // just for clarity
    // this will be cleared by draw() if it is not overridden
    looping = true;
    redraw = true;  // draw this guy once

    sketchPath = surface.getFilesDir().getAbsolutePath();

    surface.startThread();

    if (DEBUG) println("Done with init surface");
  }


  private void setFullScreenVisibility() {
    if (fullScreen) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          int visibility;
          if (SDK < 19) {
            // Pre-4.4
            visibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
          } else {
            // 4.4 and higher. Integer instead of constants defined in View so it can
            // build with SDK < 4.4
            visibility = 256 |   // View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                         512 |   // View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                         1024 |  // View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                         View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                         4 |     // View.SYSTEM_UI_FLAG_FULLSCREEN
                         4096;   // View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            // However, this visibility does not fix a bug where the navigation area
            // turns black after resuming the app:
            // https://code.google.com/p/android/issues/detail?id=170752
          }
          surface.setSystemUiVisibility(visibility);
        }
      });
    }
  }


  public void onResume() {
    if (DEBUG) System.out.println("PApplet.onResume() called");
    if (parentLayout == -1) {
      setFullScreenVisibility();
    }

    handleMethods("resume");

    // Don't call resume() when the app is starting and setup() has not been called yet:
    // https://github.com/processing/processing-android/issues/274
    // Also, there is no need to call resume() from anywhere else (for example, from
    // onStart) since onResume() is always called in the activity lifecyle:
    // https://developer.android.com/guide/components/activities/activity-lifecycle.html
    if (0 < frameCount) {
      resume();
    }

    // Set the handledBackPressed to true to handle the situation where a fragment is popping
    // right back after pressing the back button (the sketch does not exit).
    handledBackPressed = true;

    if (g != null) {
      g.restoreState();
    }

    surface.resumeThread();
  }


  public void onPause() {
    surface.pauseThread();

    // Make sure that the keyboard is not left open after leaving the app
    closeKeyboard();

    if (g != null) {
      g.saveState();
    }

    handleMethods("pause");

    pause();  // handler for others to write
  }


  public void onStart() {
    start();
  }


  public void onStop() {
    stop();
  }


  public void onCreate(Bundle savedInstanceState) {
    create();
  }


  public void onDestroy() {
    handleMethods("onDestroy");

    surface.stopThread();

    dispose();
  }


  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    handleMethods("onActivityResult", new Object[] { requestCode, resultCode, data });
  }


  public void onNewIntent(Intent intent) {
    handleMethods("onNewIntent", new Object[] { intent });
  }


  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){

  }


  public boolean onOptionsItemSelected(MenuItem item) {
    return false;
  }


  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {

  }


  public boolean onContextItemSelected(MenuItem item) {
    return false;
  }


  public void setHasOptionsMenu(boolean hasMenu) {
    surface.setHasOptionsMenu(hasMenu);
  }


  synchronized public void onBackPressed() {
    requestedBackPress = true;
  }


  public FragmentManager getFragmentManager() {
    if (getActivity() != null) {
      return getActivity().getFragmentManager();
    }
    return null;
  }


  public Window getWindow(){
    if (getActivity() != null) {
      return getActivity().getWindow();
    }
    return null;
  }


  public void startActivity(Intent intent) {
    surface.startActivity(intent);
  }


  public void runOnUiThread(Runnable action) {
    surface.runOnUiThread(action);
  }


  public boolean hasPermission(String permission) {
    return surface.hasPermission(permission);
  }


  public void requestPermission(String permission) {
    if (!hasPermission(permission)) {
      reqPermissions.add(permission);
    }
  }


  public void requestPermission(String permission, String callback) {
    requestPermission(permission, callback, this);
  }


  public void requestPermission(String permission, String callback, Object target) {
    registerWithArgs(callback, target, new Class[] { boolean.class });
    if (hasPermission(permission)) {
      // If the app already has permission, still call the handle method as it
      // may be doing some initialization
      handleMethods(callback, new Object[] { true });
    } else {
      permissionMethods.put(permission, callback);
      // Accumulating permissions so they requested all at once at the end
      // of draw.
      reqPermissions.add(permission);
    }
  }


  public void onRequestPermissionsResult(int requestCode,
                                         String permissions[],
                                         int[] grantResults) {
    if (requestCode == PSurface.REQUEST_PERMISSIONS) {
      for (int i = 0; i < grantResults.length; i++) {
        boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
        handlePermissionsResult(permissions[i], granted);
      }
    }
  }


  private void handlePermissionsResult(String permission, final boolean granted) {
    String methodName = permissionMethods.get(permission);
    final RegisteredMethods meth = registerMap.get(methodName);
    if (meth != null) {
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(new Runnable() {
        @Override
        public void run() {
          meth.handle(new Object[] { granted });
        }
      });
    }
  }


  private void handlePermissions() {
    if (0 < reqPermissions.size()) {
      String[] req = reqPermissions.toArray(new String[reqPermissions.size()]);
      surface.requestPermissions(req);
      reqPermissions.clear();
    }
  }

  synchronized private void handleBackPressed() {
    if (requestedBackPress) {
      requestedBackPress = false;
      backPressed();
      if (!handledBackPressed) {
        if (getActivity() != null) {
          // Services don't have an activity associated to them, but back press could not be triggered for those anyways
          getActivity().finish();
        }
        handledBackPressed = false;
      }
    }
  }

  /**
   * @param method "size" or "fullScreen"
   * @param args parameters passed to the function so we can show the user
   * @return true if safely inside the settings() method
   */
  boolean insideSettings(String method, Object... args) {
    if (insideSettings) {
      return true;
    }
    final String url = "https://processing.org/reference/" + method + "_.html";
    if (!external) {  // post a warning for users of Eclipse and other IDEs
      StringList argList = new StringList(args);
      System.err.println("When not using the PDE, " + method + "() can only be used inside settings().");
      System.err.println("Remove the " + method + "() method from setup(), and add the following:");
      System.err.println("public void settings() {");
      System.err.println("  " + method + "(" + argList.join(", ") + ");");
      System.err.println("}");
    }
    throw new IllegalStateException(method + "() cannot be used here, see " + url);
  }


  void handleSettings() {
    insideSettings = true;
    //Do stuff
    settings();
    insideSettings = false;
  }


  public void settings() {
    //It'll be empty. Will be overridden by user's sketch class.
  }


  final public int sketchWidth() {
    return width;
  }


  final public  int sketchHeight() {
    return height;
  }


  final public String sketchRenderer() {
    return renderer;
  }


  public int sketchSmooth() {
    return smooth;
  }


  final public boolean sketchFullScreen() {
    return fullScreen;
  }


  final public int sketchDisplay() {
    return display;
  }


  final public String sketchOutputPath() {
    return null;
  }


  final public OutputStream sketchOutputStream() {
    return null;
  }


  final public int sketchWindowColor() {
    return windowColor;
  }


  final public int sketchPixelDensity() {
    return pixelDensity;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public void surfaceChanged() {
    surfaceChanged = true;
    g.surfaceChanged();
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Called by the sketch surface view, thought it could conceivably be called
   * by Android as well.
   */
  public void surfaceWindowFocusChanged(boolean hasFocus) {
    focused = hasFocus;
    if (focused) {
      focusGained();
    } else {
      focusLost();
    }
  }


  /**
   * If you override this function without calling super.onTouchEvent(),
   * then motionX, motionY, motionPressed, and motionEvent will not be set.
   */
  public boolean surfaceTouchEvent(MotionEvent event) {
    nativeMotionEvent(event);
    return true;
  }


  public void surfaceKeyDown(int code, android.view.KeyEvent event) {
    nativeKeyEvent(event);
  }


  public void surfaceKeyUp(int code, android.view.KeyEvent event) {
    nativeKeyEvent(event);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * Called by the browser or applet viewer to inform this applet that it
   * should start its execution. It is called after the init method and
   * each time the applet is revisited in a Web page.
   * <p/>
   * Called explicitly via the first call to PApplet.paint(), because
   * PAppletGL needs to have a usable screen before getting things rolling.
   */
  public void start() {
  }


  /**
   * Called by the browser or applet viewer to inform
   * this applet that it should stop its execution.
   * <p/>
   * Unfortunately, there are no guarantees from the Java spec
   * when or if stop() will be called (i.e. on browser quit,
   * or when moving between web pages), and it's not always called.
   */
  public void stop() {
  }


  /**
   * Developers can override here to save state. The 'paused' variable will be
   * set before this function is called.
   */
  public void pause() {
  }


  /**
   * Developers can override here to restore state. The 'paused' variable
   * will be cleared before this function is called.
   */
  public void resume() {
  }


  public void backPressed() {
    handledBackPressed = false;
  }

  //////////////////////////////////////////////////////////////


  /** Map of registered methods, stored by name. */
  HashMap<String, RegisteredMethods> registerMap =
    new HashMap<String, PApplet.RegisteredMethods>();


  class RegisteredMethods {
    int count;
    Object[] objects;
    // Because the Method comes from the class being called,
    // it will be unique for most, if not all, objects.
    Method[] methods;
    Object[] emptyArgs = new Object[] { };


    void handle() {
      handle(emptyArgs);
    }


    void handle(Object[] args) {
      for (int i = 0; i < count; i++) {
        try {
          methods[i].invoke(objects[i], args);
        } catch (Exception e) {
          // check for wrapped exception, get root exception
          Throwable t;
          if (e instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) e;
            t = ite.getCause();
          } else {
            t = e;
          }
          // check for RuntimeException, and allow to bubble up
          if (t instanceof RuntimeException) {
            // re-throw exception
            throw (RuntimeException) t;
          } else {
            // trap and print as usual
            t.printStackTrace();
          }
        }
      }
    }


    void add(Object object, Method method) {
      if (findIndex(object) == -1) {
        if (objects == null) {
          objects = new Object[5];
          methods = new Method[5];

        } else if (count == objects.length) {
          objects = (Object[]) PApplet.expand(objects);
          methods = (Method[]) PApplet.expand(methods);
        }
        objects[count] = object;
        methods[count] = method;
        count++;
      } else {
        die(method.getName() + "() already added for this instance of " +
            object.getClass().getName());
      }
    }


    /**
     * Removes first object/method pair matched (and only the first,
     * must be called multiple times if object is registered multiple times).
     * Does not shrink array afterwards, silently returns if method not found.
     */
//    public void remove(Object object, Method method) {
//      int index = findIndex(object, method);
    public void remove(Object object) {
      int index = findIndex(object);
      if (index != -1) {
        // shift remaining methods by one to preserve ordering
        count--;
        for (int i = index; i < count; i++) {
          objects[i] = objects[i+1];
          methods[i] = methods[i+1];
        }
        // clean things out for the gc's sake
        objects[count] = null;
        methods[count] = null;
      }
    }


//    protected int findIndex(Object object, Method method) {
    protected int findIndex(Object object) {
      for (int i = 0; i < count; i++) {
        if (objects[i] == object) {
//        if (objects[i] == object && methods[i].equals(method)) {
          //objects[i].equals() might be overridden, so use == for safety
          // since here we do care about actual object identity
          //methods[i]==method is never true even for same method, so must use
          // equals(), this should be safe because of object identity
          return i;
        }
      }
      return -1;
    }
  }


  /**
   * Register a built-in event so that it can be fired for libraries, etc.
   * Supported events include:
   * <ul>
   * <li>pre – at the very top of the draw() method (safe to draw)
   * <li>draw – at the end of the draw() method (safe to draw)
   * <li>post – after draw() has exited (not safe to draw)
   * <li>pause – called when the sketch is paused
   * <li>resume – called when the sketch is resumed
   * <li>dispose – when the sketch is shutting down (definitely not safe to draw)
   * <ul>
   * In addition, the new (for 2.0) processing.event classes are passed to
   * the following event types:
   * <ul>
   * <li>mouseEvent
   * <li>keyEvent
   * <li>touchEvent
   * </ul>
   * The older java.awt events are no longer supported.
   * See the Library Wiki page for more details.
   * @param methodName name of the method to be called
   * @param target the target object that should receive the event
   */
  public void registerMethod(String methodName, Object target) {
    if (methodName.equals("mouseEvent")) {
      registerWithArgs("mouseEvent", target, new Class[] { processing.event.MouseEvent.class });

    } else if (methodName.equals("keyEvent")) {
      registerWithArgs("keyEvent", target, new Class[] { processing.event.KeyEvent.class });

    } else if (methodName.equals("touchEvent")) {
      registerWithArgs("touchEvent", target, new Class[] { processing.event.TouchEvent.class });

    // Android-lifecycle event handlers
    } else if (methodName.equals("onDestroy")) {
      registerNoArgs(methodName, target);
    } else if (methodName.equals("onActivityResult")) {
      registerWithArgs("onActivityResult", target, new Class[] { int.class, int.class, Intent.class });
    } else if (methodName.equals("onNewIntent")) {
      registerWithArgs("onNewIntent", target, new Class[] { Intent.class });

    } else {
      registerNoArgs(methodName, target);
    }
  }


  private void registerNoArgs(String name, Object o) {
    RegisteredMethods meth = registerMap.get(name);
    if (meth == null) {
      meth = new RegisteredMethods();
      registerMap.put(name, meth);
    }
    Class<?> c = o.getClass();
    try {
      Method method = c.getMethod(name, new Class[] {});
      meth.add(o, method);

    } catch (NoSuchMethodException nsme) {
      die("There is no public " + name + "() method in the class " +
          o.getClass().getName());

    } catch (Exception e) {
      die("Could not register " + name + " + () for " + o, e);
    }
  }


  private void registerWithArgs(String name, Object o, Class<?> cargs[]) {
    RegisteredMethods meth = registerMap.get(name);
    if (meth == null) {
      meth = new RegisteredMethods();
      registerMap.put(name, meth);
    }
    Class<?> c = o.getClass();
    try {
      Method method = c.getMethod(name, cargs);
      meth.add(o, method);

    } catch (NoSuchMethodException nsme) {
      die("There is no public " + name + "() method in the class " +
          o.getClass().getName());

    } catch (Exception e) {
      die("Could not register " + name + " + () for " + o, e);
    }
  }


//  public void registerMethod(String methodName, Object target, Object... args) {
//    registerWithArgs(methodName, target, args);
//  }


  public void unregisterMethod(String name, Object target) {
    RegisteredMethods meth = registerMap.get(name);
    if (meth == null) {
      die("No registered methods with the name " + name + "() were found.");
    }
    try {
//      Method method = o.getClass().getMethod(name, new Class[] {});
//      meth.remove(o, method);
      meth.remove(target);
    } catch (Exception e) {
      die("Could not unregister " + name + "() for " + target, e);
    }
  }


  protected void handleMethods(String methodName) {
    RegisteredMethods meth = registerMap.get(methodName);
    if (meth != null) {
      meth.handle();
    }
  }


  protected void handleMethods(String methodName, final Object[] args) {
    final RegisteredMethods meth = registerMap.get(methodName);
    if (meth != null) {
      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(new Runnable() {
        @Override
        public void run() {
          meth.handle(args);
        }
      });
    }
  }


  @Deprecated
  public void registerSize(Object o) {
    System.err.println("The registerSize() command is no longer supported.");
//    Class<?> methodArgs[] = new Class[] { Integer.TYPE, Integer.TYPE };
//    registerWithArgs(sizeMethods, "size", o, methodArgs);
  }


  @Deprecated
  public void registerPre(Object o) {
    registerNoArgs("pre", o);
  }


  @Deprecated
  public void registerDraw(Object o) {
    registerNoArgs("draw", o);
  }


  @Deprecated
  public void registerPost(Object o) {
    registerNoArgs("post", o);
  }


  @Deprecated
  public void registerDispose(Object o) {
    registerNoArgs("dispose", o);
  }


  @Deprecated
  public void unregisterSize(Object o) {
    System.err.println("The unregisterSize() command is no longer supported.");
//    Class<?> methodArgs[] = new Class[] { Integer.TYPE, Integer.TYPE };
//    unregisterWithArgs(sizeMethods, "size", o, methodArgs);
  }


  @Deprecated
  public void unregisterPre(Object o) {
    unregisterMethod("pre", o);
  }


  @Deprecated
  public void unregisterDraw(Object o) {
    unregisterMethod("draw", o);
  }


  @Deprecated
  public void unregisterPost(Object o) {
    unregisterMethod("post", o);
  }


  @Deprecated
  public void unregisterDispose(Object o) {
    unregisterMethod("dispose", o);
  }


  //////////////////////////////////////////////////////////////


  public void setup() {
  }


  public void draw() {
    // if no draw method, then shut things down
    //System.out.println("no draw method, goodbye");
    finished = true;
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
  public void fullScreen() {
    if (!fullScreen) {
      if (insideSettings("fullScreen")) {
        this.fullScreen = true;
      }
    }
  }


  public void fullScreen(int display) {
    //Display index doesn't make sense in Android.
    //Should we throw some error in log ?
    if (!fullScreen /*|| display != this.display*/) {
      if (insideSettings("fullScreen", display)) {
        this.fullScreen = true;
//        this.display = display;
      }
    }
  }


  public void fullScreen(String renderer) {
    if (!fullScreen ||
        !renderer.equals(this.renderer)) {
      if (insideSettings("fullScreen", renderer)) {
        this.fullScreen = true;
        this.renderer = renderer;
      }
    }
  }


  public void fullScreen(String renderer, int display) {
    if (!fullScreen ||
        !renderer.equals(this.renderer) /*||
        display != this.display*/) {
      if (insideSettings("fullScreen", renderer, display)) {
        this.fullScreen = true;
        this.renderer = renderer;
//        this.display = display;
      }
    }
  }


  /**
   * Starts up and creates a two-dimensional drawing surface, or resizes the
   * current drawing surface.
   * <P>
   * This should be the first thing called inside of setup().
   * <P>
   * If called once a renderer has already been set, this will use the
   * previous renderer and simply resize it.
   */
  public void size(int iwidth, int iheight) {
    if (iwidth != this.width || iheight != this.height) {
      if (insideSettings("size", iwidth, iheight)) {
        this.width = iwidth;
        this.height = iheight;
      }
    }
  }


  public void size(int iwidth, int iheight, String irenderer) {
    if (iwidth != this.width || iheight != this.height ||
        !this.renderer.equals(irenderer)) {
      if (insideSettings("size", iwidth, iheight, irenderer)) {
        this.width = iwidth;
        this.height = iheight;
        this.renderer = irenderer;
      }
    }
  }


  public void setSize(int width, int height) {
    if (fullScreen) {
      this.displayWidth = width;
      this.displayHeight = height;
    }
    this.width = width;
    this.height = height;
    pixelWidth = width * pixelDensity;
    pixelHeight = height * pixelDensity;
    g.setSize(sketchWidth(), sketchHeight());
  }


  public void setExternal(boolean external) {
    this.external = external;
  }


//. . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public void smooth() {
   smooth(1);
 }


  public void smooth(int level) {
    if (insideSettings) {
      this.smooth = level;
    } else if (this.smooth != level) {
      smoothWarning("smooth");
    }
  }


  public void noSmooth() {
    if (insideSettings) {
      this.smooth = 0;
    } else if (this.smooth != 0) {
      smoothWarning("noSmooth");
    }
  }

 private void smoothWarning(String method) {
   // When running from the PDE, say setup(), otherwise say settings()
   final String where = external ? "setup" : "settings";
   PGraphics.showWarning("%s() can only be used inside %s()", method, where);
 }


 // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public PGraphics getGraphics() {
    return g;
  }

  public void orientation(int which) {
   surface.setOrientation(which);
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
  public void size(final int iwidth, final int iheight,
                   final String irenderer, final String ipath) {
    if (iwidth != this.width || iheight != this.height ||
        !this.renderer.equals(irenderer)) {
      if (insideSettings("size", iwidth, iheight, irenderer,
          ipath)) {
        this.width = iwidth;
        this.height = iheight;
        this.renderer = irenderer;
      }
    }
  }


  public PGraphics createGraphics(int iwidth, int iheight) {
    return createGraphics(iwidth, iheight, JAVA2D);
  }


  /**
   * Create an offscreen PGraphics object for drawing. This can be used
   * for bitmap or vector images drawing or rendering.
   * <UL>
   * <LI>Do not use "new PGraphicsXxxx()", use this method. This method
   * ensures that internal variables are set up properly that tie the
   * new graphics context back to its parent PApplet.
   * <LI>The basic way to create bitmap images is to use the <A
   * HREF="http://processing.org/reference/saveFrame_.html">saveFrame()</A>
   * function.
   * <LI>If you want to create a really large scene and write that,
   * first make sure that you've allocated a lot of memory in the Preferences.
   * <LI>If you want to create images that are larger than the screen,
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
   *   big = createGraphics(3000, 3000, P3D);
   *
   *   big.beginDraw();
   *   big.background(128);
   *   big.line(20, 1800, 1800, 900);
   *   // etc..
   *   big.endDraw();
   *
   *   // make sure the file is written to the sketch folder
   *   big.save("big.tif");
   * }
   *
   * </PRE>
   * <LI>It's important to always wrap drawing to createGraphics() with
   * beginDraw() and endDraw() (beginFrame() and endFrame() prior to
   * revision 0115). The reason is that the renderer needs to know when
   * drawing has stopped, so that it can update itself internally.
   * This also handles calling the defaults() method, for people familiar
   * with that.
   * <LI>It's not possible to use createGraphics() with the OPENGL renderer,
   * because it doesn't allow offscreen use.
   * <LI>With Processing 0115 and later, it's possible to write images in
   * formats other than the default .tga and .tiff. The exact formats and
   * background information can be found in the developer's reference for
   * <A HREF="http://dev.processing.org/reference/core/javadoc/processing/core/PImage.html#save(java.lang.String)">PImage.save()</A>.
   * </UL>
   */
  public PGraphics createGraphics(int iwidth, int iheight, String irenderer) {
    return makeGraphics(iwidth, iheight, irenderer, false);
  }


  protected PGraphics makeGraphics(int w, int h,
                                   String renderer, boolean primary) {
    PGraphics pg = null;
    if (renderer.equals(JAVA2D)) {
      pg = new PGraphicsAndroid2D();
    } else if (renderer.equals(P2D)) {
      if (!primary && !g.isGL()) {
        throw new RuntimeException("createGraphics() with P2D requires size() to use P2D or P3D");
      }
      pg = new PGraphics2D();

    } else if (renderer.equals(P3D)) {
      if (!primary && !g.isGL()) {
        throw new RuntimeException("createGraphics() with P3D or OPENGL requires size() to use P2D or P3D");
      }
      pg = new PGraphics3D();
    } else {
      Class<?> rendererClass = null;
      Constructor<?> constructor = null;
      try {
        // http://code.google.com/p/android/issues/detail?id=11101
        rendererClass = Thread.currentThread().getContextClassLoader().loadClass(renderer);
      } catch (ClassNotFoundException cnfe) {
        throw new RuntimeException("Missing renderer class");
      }

      if (rendererClass != null) {
        try {
          constructor = rendererClass.getConstructor(new Class[] { });
        } catch (NoSuchMethodException nsme) {
          throw new RuntimeException("Missing renderer constructor");
        }

        if (constructor != null) {
          try {
            pg = (PGraphics) constructor.newInstance();
          } catch (InvocationTargetException e) {
            printStackTrace(e);
            throw new RuntimeException(e.getMessage());
          } catch (IllegalAccessException e) {
            printStackTrace(e);
            throw new RuntimeException(e.getMessage());
          } catch (InstantiationException e) {
            printStackTrace(e);
            throw new RuntimeException(e.getMessage());
          } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            printStackTrace(e);
          }
        }
      }
    }

    pg.setParent(this);
    pg.setPrimary(primary);
    pg.setSize(w, h);
    return pg;
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
   *               in cases where this is the main drawing surface object.
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
   * Creates a new PImage (the datatype for storing images). This provides a fresh buffer of pixels to play with. Set the size of the buffer with the <b>width</b> and <b>height</b> parameters. The <b>format</b> parameter defines how the pixels are stored. See the PImage reference for more information.
   */
  public PImage createImage(int wide, int high, int format) {
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
    PImage image = new PImage(wide, high, format);
//    if (params != null) {
//      image.setParams(g, params);
//    }
    image.parent = this;  // make save() work
    return image;
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

  public void handleDraw() {
    //debug("handleDraw() " + g + " " + looping + " " + redraw + " valid:" + this.isValid() + " visible:" + this.isVisible());

    if (g == null) return;

    if (!surfaceChanged && parentLayout != -1) {
      // When using a parent layout, don't start drawing until the sketch
      // has been properly sized.
      return;
    }

    if (!looping && !redraw) return;

    if (insideDraw) {
      System.err.println("handleDraw() called before finishing");
      System.exit(1);
    }

    insideDraw = true;

//    if (recorder != null) {
//      recorder.beginDraw();
//    }

    if (handleSpecialDraw()) return;

    g.beginDraw();

    long now = System.nanoTime();

    if (frameCount == 0) {
      setup();

    } else {  // frameCount > 0, meaning an actual draw()
      // update the current frameRate
      double rate = 1000000.0 / ((now - frameRateLastNanos) / 1000000.0);
      float instantaneousRate = (float) (rate / 1000.0);
      frameRate = (frameRate * 0.9f) + (instantaneousRate * 0.1f);

      if (frameCount != 0) {
        handleMethods("pre");
      }

      // use dmouseX/Y as previous mouse pos, since this is the
      // last position the mouse was in during the previous draw.
      pmouseX = dmouseX;
      pmouseY = dmouseY;

      draw();

      // dmouseX/Y is updated only once per frame (unlike emouseX/Y)
      dmouseX = mouseX;
      dmouseY = mouseY;

      // these are called *after* loop so that valid
      // drawing commands can be run inside them. it can't
      // be before, since a call to background() would wipe
      // out anything that had been drawn so far.
      dequeueEvents();

      handleMethods("draw");
      handlePermissions();
      handleBackPressed();

      redraw = false;  // unset 'redraw' flag in case it was set
      // (only do this once draw() has run, not just setup())
    }
    g.endDraw();

//    if (recorder != null) {
//      recorder.endDraw();
//    }
    insideDraw = false;

    if (frameCount != 0) {
      handleMethods("post");
    }

    frameRateLastNanos = now;
    frameCount++;
  }


  // This method handles some special situations on Android where beginDraw/endDraw are needed,
  // but not to render the actual contents of draw(). In general, these situations arise from
  // having to refresh/restore the screen after requesting no loop, or resuming the sketch in
  // no-loop state.
  protected boolean handleSpecialDraw() {
    boolean handled = false;

    if (g.restoringState()) {
      // The sketch is restoring, so begin/end the frame properly and quit drawing.
      g.beginDraw();
      g.endDraw();

      handled = true;
    } else if (g.requestedNoLoop) {
      // noLoop() was called sometime in the previous frame with a GL renderer, but only now
      // we are sure that the frame is properly displayed.
      looping = false;

      // Perform a full frame draw, to ensure that the previous frame is properly displayed (see
      // comment in the declaration of requestedNoLoop).
      g.beginDraw();
      g.endDraw();

      g.requestedNoLoop = false;
      handled = true;
    }

    if (handled) {
      insideDraw = false;
      return true;
    } else {
      return false;
    }
  }


  //////////////////////////////////////////////////////////////


  synchronized public void redraw() {
    if (!looping) {
      redraw = true;
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


  synchronized public void loop() {
    if (!looping) {
      looping = true;
    }
  }


  synchronized public void noLoop() {
    if (looping) {
      if (g.requestNoLoop()) {
        g.requestedNoLoop = true;
      } else {
        looping = false;
      }
    }
  }


  public boolean isLooping() {
    return looping;
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


  InternalEventQueue eventQueue = new InternalEventQueue();


  class InternalEventQueue {
    protected Event queue[] = new Event[10];
    protected int offset;
    protected int count;

    synchronized void add(Event e) {
      if (count == queue.length) {
        queue = (Event[]) expand(queue);
      }
      queue[count++] = e;
    }

    synchronized Event remove() {
      if (offset == count) {
        throw new RuntimeException("Nothing left on the event queue.");
      }
      Event outgoing = queue[offset++];
      if (offset == count) {
        // All done, time to reset
        offset = 0;
        count = 0;
      }
      return outgoing;
    }

    synchronized boolean available() {
      return count != 0;
    }
  }

  /**
   * Add an event to the internal event queue, or process it immediately if
   * the sketch is not currently looping.
   */
  public void postEvent(processing.event.Event pe) {
    eventQueue.add(pe);

    if (!looping) {
      dequeueEvents();
    }
  }


  protected void dequeueEvents() {
    while (eventQueue.available()) {
      Event e = eventQueue.remove();

      switch (e.flavor) {
      case Event.TOUCH:
        handleTouchEvent((TouchEvent) e);
        break;
      case Event.MOUSE:
        handleMouseEvent((MouseEvent) e);
        break;
      case Event.KEY:
        handleKeyEvent((KeyEvent) e);
        break;
      }
    }
  }


  //////////////////////////////////////////////////////////////


  protected void handleMouseEvent(MouseEvent event) {
//    mouseEvent = event;

    // http://dev.processing.org/bugs/show_bug.cgi?id=170
    // also prevents mouseExited() on the mac from hosing the mouse
    // position, because x/y are bizarre values on the exit event.
    // see also the id check below.. both of these go together
//  if ((id == java.awt.event.MouseEvent.MOUSE_DRAGGED) ||
//      (id == java.awt.event.MouseEvent.MOUSE_MOVED)) {
    if (event.action == MouseEvent.DRAG ||
        event.action == MouseEvent.MOVE) {
      pmouseX = emouseX;
      pmouseY = emouseY;
      mouseX = event.getX();
      mouseY = event.getY();
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
    mouseButton = event.getButton();

    // Added in 0215 (2.0b7) so that pmouseX/Y behave more like one would
    // expect from the desktop. This makes the ContinousLines example behave.
    if (event.action == MouseEvent.PRESS) {
      mouseX = event.getX();
      mouseY = event.getY();
      pmouseX = mouseX;
      pmouseY = mouseY;
      dmouseX = mouseX;
      dmouseY = mouseY;
    }

//    if (event.getAction() == MouseEvent.RELEASE) {
//      mouseX = event.getX();
//      mouseY = event.getY();
//    }

//    mouseEvent = event;

    // Do this up here in case a registered method relies on the
    // boolean for mousePressed.

    switch (event.action) {
    case MouseEvent.PRESS:
      mousePressed = true;
      break;
    case MouseEvent.RELEASE:
      mousePressed = false;
      break;
    }

    handleMethods("mouseEvent", new Object[] { event });

    switch (event.action) {
    case MouseEvent.PRESS:
      mousePressed(event);
      break;
    case MouseEvent.RELEASE:
      mouseReleased(event);
      break;
    case MouseEvent.CLICK:
      mouseClicked(event);
      break;
    case MouseEvent.DRAG:
      mouseDragged(event);
      break;
    case MouseEvent.MOVE:
      mouseMoved(event);
      break;
    case MouseEvent.ENTER:
      mouseEntered(event);
      break;
    case MouseEvent.EXIT:
      mouseExited(event);
      break;
    }

    if ((event.action == MouseEvent.DRAG) ||
        (event.action == MouseEvent.MOVE)) {
      emouseX = mouseX;
      emouseY = mouseY;
    }
    if (event.action == MouseEvent.PRESS) {  // Android-only
      emouseX = mouseX;
      emouseY = mouseY;
    }
//    if (event.getAction() == MouseEvent.RELEASE) {  // Android-only
//      emouseX = mouseX;
//      emouseY = mouseY;
//    }
  }


  protected void handleTouchEvent(TouchEvent event) {
    touches = event.getTouches(touches);

    switch (event.getAction()) {
    case TouchEvent.START:
      touchIsStarted = true;
      break;
    case TouchEvent.END:
      touchIsStarted = false;
      break;
    }

    handleMethods("touchEvent", new Object[] { event });

    switch (event.getAction()) {
    case TouchEvent.START:
      touchStarted(event);
      break;
    case TouchEvent.END:
      touchEnded(event);
      break;
    case TouchEvent.MOVE:
      touchMoved(event);
      break;
    case TouchEvent.CANCEL:
      touchCancelled(event);
      break;
    }
  }


  /**
   * Figure out how to process a mouse event. When loop() has been
   * called, the events will be queued up until drawing is complete.
   * If noLoop() has been called, then events will happen immediately.
   */
  protected void nativeMotionEvent(MotionEvent motionEvent) {
    int metaState = motionEvent.getMetaState();
    int modifiers = 0;
    if ((metaState & android.view.KeyEvent.META_SHIFT_ON) != 0) {
      modifiers |= Event.SHIFT;
    }
    if ((metaState & android.view.KeyEvent.META_CTRL_ON) != 0) {
      modifiers |= Event.CTRL;
    }
    if ((metaState & android.view.KeyEvent.META_META_ON) != 0) {
      modifiers |= Event.META;
    }
    if ((metaState & android.view.KeyEvent.META_ALT_ON) != 0) {
      modifiers |= Event.ALT;
    }

    int button;
    int state = motionEvent.getButtonState();
    switch (state) {
      case MotionEvent.BUTTON_PRIMARY:
        button = LEFT;
        break;
      case MotionEvent.BUTTON_SECONDARY:
        button = RIGHT;
        break;
      case MotionEvent.BUTTON_TERTIARY:
        button = CENTER;
        break;
      default:
        // Covers the BUTTON_FORWARD, BUTTON_BACK,
        // BUTTON_STYLUS_PRIMARY, and BUTTON_STYLUS_SECONDARY
        button = state;
    }

    enqueueMouseEvents(motionEvent, button, modifiers);
    enqueueTouchEvents(motionEvent, button, modifiers);
  }


  protected void enqueueTouchEvents(MotionEvent event, int button, int modifiers) {
    int action = event.getAction();
    int actionMasked = action & MotionEvent.ACTION_MASK;
    int paction = 0;
    switch (actionMasked) {
    case MotionEvent.ACTION_DOWN:
      paction = TouchEvent.START;
      break;
    case MotionEvent.ACTION_POINTER_DOWN:
      paction = TouchEvent.START;
      break;
    case MotionEvent.ACTION_MOVE:
      paction = TouchEvent.MOVE;
      break;
    case MotionEvent.ACTION_UP:
      paction = TouchEvent.END;
      break;
    case MotionEvent.ACTION_POINTER_UP:
      paction = TouchEvent.END;
      break;
    default:
      // Covers any other action value, including ACTION_CANCEL
      paction = TouchEvent.CANCEL;
      break;
    }

    if (paction == TouchEvent.START || paction == TouchEvent.END) {
      touchPointerId = event.getPointerId(0);
    }

    int pointerCount = event.getPointerCount();

    if (actionMasked == MotionEvent.ACTION_MOVE) {
      // Post historical movement events, if any.
      int historySize = event.getHistorySize();
      for (int h = 0; h < historySize; h++) {
        TouchEvent touchEvent = new TouchEvent(event, event.getHistoricalEventTime(h),
                                               paction, modifiers, button);
        touchEvent.setNumPointers(pointerCount);
        for (int p = 0; p < pointerCount; p++) {
          touchEvent.setPointer(p, event.getPointerId(p), event.getHistoricalX(p, h), event.getHistoricalY(p, h),
                                event.getHistoricalSize(p, h), event.getHistoricalPressure(p, h));
        }
        postEvent(touchEvent);
      }
    }

    // Current event
    TouchEvent touchEvent = new TouchEvent(event, event.getEventTime(),
                                           paction, modifiers, button);
    if (actionMasked == MotionEvent.ACTION_UP) {
      // Last pointer up
      touchEvent.setNumPointers(0);
    } else {
      // We still have some pointers left
      touchEvent.setNumPointers(pointerCount);
      for (int p = 0; p < event.getPointerCount(); p++) {
        touchEvent.setPointer(p, event.getPointerId(p), event.getX(p), event.getY(p),
                                 event.getSize(p), event.getPressure(p));
      }
    }
    postEvent(touchEvent);
  }


  protected void enqueueMouseEvents(MotionEvent event, int button, int modifiers) {
    int action = event.getAction();

    int clickCount = 1;  // not really set... (i.e. not catching double taps)
    int index;

    switch (action & MotionEvent.ACTION_MASK) {
    case MotionEvent.ACTION_DOWN:
      mousePointerId = event.getPointerId(0);
      postEvent(new MouseEvent(event, event.getEventTime(),
                               MouseEvent.PRESS, modifiers,
                               (int) event.getX(), (int) event.getY(),
                               button, clickCount));
      break;
    case MotionEvent.ACTION_MOVE:
      index = event.findPointerIndex(mousePointerId);
      if (index != -1) {
        postEvent(new MouseEvent(event, event.getEventTime(),
                                 MouseEvent.DRAG, modifiers,
                                 (int) event.getX(index), (int) event.getY(index),
                                 button, clickCount));
      }
      break;
    case MotionEvent.ACTION_UP:
      index = event.findPointerIndex(mousePointerId);
      if (index != -1) {
        postEvent(new MouseEvent(event, event.getEventTime(),
                                 MouseEvent.RELEASE, modifiers,
                                 (int) event.getX(index), (int) event.getY(index),
                                 button, clickCount));
      }
      break;
    }
  }

  public void mousePressed() { }


  public void mousePressed(MouseEvent event) {
    mousePressed();
  }


  public void mouseReleased() { }


  public void mouseReleased(MouseEvent event) {
    mouseReleased();
  }


  /**
   * mouseClicked is currently not fired at all (no direct match on Android).
   * http://code.google.com/p/processing/issues/detail?id=215
   */
  public void mouseClicked() { }


  public void mouseClicked(MouseEvent event) {
    mouseClicked();
  }


  public void mouseDragged() { }


  public void mouseDragged(MouseEvent event) {
    mouseDragged();
  }


  public void mouseMoved() { }


  public void mouseMoved(MouseEvent event) {
    mouseMoved();
  }


  public void mouseEntered() { }


  public void mouseEntered(MouseEvent event) {
    mouseEntered();
  }


  public void mouseExited() { }


  public void mouseExited(MouseEvent event) {
    mouseExited();
  }


  public void touchStarted() { }


  public void touchStarted(TouchEvent event) {
    touchStarted();
  }


  public void touchMoved() { }


  public void touchMoved(TouchEvent event) {
    touchMoved();
  }


  public void touchEnded() { }


  public void touchEnded(TouchEvent event) {
    touchEnded();
  }


  public void touchCancelled() { }


  public void touchCancelled(TouchEvent event) {
    touchCancelled();
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


  protected void handleKeyEvent(KeyEvent event) {

    // Get rid of auto-repeating keys if desired and supported
    if (!keyRepeatEnabled && event.isAutoRepeat) return;

//    keyEvent = event;
    key = event.key;
    keyCode = event.keyCode;

    switch (event.action) {
    case KeyEvent.PRESS:
      keyPressed = true;
      keyPressed(event);
      break;
    case KeyEvent.RELEASE:
      keyPressed = false;
      keyReleased(event);
      break;
    }

    handleMethods("keyEvent", new Object[] { event });
  }


  protected void nativeKeyEvent(android.view.KeyEvent event) {
    // event.isPrintingKey() returns false for whitespace and others,
    // which is a problem if the space bar or tab key are used.
    char key = (char) event.getUnicodeChar();
    // if not mappable to a unicode character, instead mark as coded key
    if (key == 0 || key == 0xFFFF) {
      key = CODED;
    }

    int keyCode = event.getKeyCode();

    int keAction = 0;
    int action = event.getAction();
    if (action == android.view.KeyEvent.ACTION_DOWN) {
      keAction = KeyEvent.PRESS;
    } else if (action == android.view.KeyEvent.ACTION_UP) {
      keAction = KeyEvent.RELEASE;
    }

    // TODO set up proper key modifier handling
    int keModifiers = 0;

    KeyEvent ke = new KeyEvent(event, event.getEventTime(),
                               keAction, keModifiers, key, keyCode, 0 < event.getRepeatCount());

    postEvent(ke);
  }


  public void openKeyboard() {
    Context context = surface.getContext();
    InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    keyboardIsOpen = true;
  }


  public void closeKeyboard() {
    if (keyboardIsOpen) {
      Context context = surface.getContext();
      InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
      keyboardIsOpen = false;
      if (parentLayout == -1) {
        setFullScreenVisibility();
      }
    }
  }


  public void keyPressed() { }


  public void keyPressed(KeyEvent event) {
    keyPressed();
  }


  /**
   * See keyPressed().
   */
  public void keyReleased() { }


  public void keyReleased(KeyEvent event) {
    keyReleased();
  }


  public void keyTyped() { }


  public void keyTyped(KeyEvent event) {
    keyTyped();
  }


  //////////////////////////////////////////////////////////////


  public void focusGained() { }

//  public void focusGained(FocusEvent e) {
//    focused = true;
//    focusGained();
//  }


  public void focusLost() { }

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
   */
  public int millis() {
    return (int) (System.currentTimeMillis() - millisOffset);
  }

  /** Seconds position of the current time. */
  static public int second() {
    return Calendar.getInstance().get(Calendar.SECOND);
  }

  /** Minutes position of the current time. */
  static public int minute() {
    return Calendar.getInstance().get(Calendar.MINUTE);
  }

  /**
   * Hour position of the current time in international format (0-23).
   * <P>
   * To convert this value to American time: <BR>
   * <PRE>int yankeeHour = (hour() % 12);
   * if (yankeeHour == 0) yankeeHour = 12;</PRE>
   */
  static public int hour() {
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
  }

  /**
   * Get the current day of the month (1 through 31).
   * <P>
   * If you're looking for the day of the week (M-F or whatever)
   * or day of the year (1..365) then use java's Calendar.get()
   */
  static public int day() {
    return Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
  }

  /**
   * Get the current month in range 1 through 12.
   */
  static public int month() {
    // months are number 0..11 so change to colloquial 1..12
    return Calendar.getInstance().get(Calendar.MONTH) + 1;
  }

  /**
   * Get the current year.
   */
  static public int year() {
    return Calendar.getInstance().get(Calendar.YEAR);
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
  public void delay(int napTime) {
    //if (frameCount != 0) {
    //if (napTime > 0) {
    try {
      Thread.sleep(napTime);
    } catch (InterruptedException e) { }
    //}
    //}
  }


  /**
   * ( begin auto-generated from frameRate.xml )
   *
   * Specifies the number of frames to be displayed every second. If the
   * processor is not fast enough to maintain the specified rate, it will not
   * be achieved. For example, the function call <b>frameRate(30)</b> will
   * attempt to refresh 30 times a second. It is recommended to set the frame
   * rate within <b>setup()</b>. The default rate is 60 frames per second.
   *
   * ( end auto-generated )
   */
  public void frameRate(float fps) {
//
//    frameRateTarget = newRateTarget;
//    frameRatePeriod = (long) (1000000000.0 / frameRateTarget);
//    g.setFrameRate(newRateTarget);
    surface.setFrameRate(fps);
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


  public void link(String here) {
    link(here, null);
  }


  /**
   * Link to an external page without all the muss.
   * <P>
   * When run with an applet, uses the browser to open the url,
   * for applications, attempts to launch a browser with the url.
   * <P>
   * Works on Mac OS X and Windows. For Linux, use:
   * <PRE>open(new String[] { "firefox", url });</PRE>
   * or whatever you want as your browser, since Linux doesn't
   * yet have a standard method for launching URLs.
   */
  public void link(String url, String frameTitle) {
    Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse(url));
    surface.startActivity(viewIntent);
  }


  /**
   * Attempt to open a file using the platform's shell.
   */
  static public void open(String filename) {
    open(new String[] { filename });
  }


  /**
   * Launch a process using a platforms shell. This version uses an array
   * to make it easier to deal with spaces in the individual elements.
   * (This avoids the situation of trying to put single or double quotes
   * around different bits).
   */
  static public Process open(String argv[]) {
    return exec(argv);
  }


  static public Process exec(String[] argv) {
    try {
      return Runtime.getRuntime().exec(argv);
    } catch (Exception e) {
      throw new RuntimeException("Could not open " + join(argv, ' '));
    }
  }


  //////////////////////////////////////////////////////////////


  /**
   * Better way of handling e.printStackTrace() calls so that they can be
   * handled by subclasses as necessary.
   */
  protected void printStackTrace(Throwable t) {
    t.printStackTrace();
  }


  /**
   * Function for an applet/application to kill itself and
   * display an error. Mostly this is here to be improved later.
   */
  public void die(String what) {
    stop();
    throw new RuntimeException(what);
  }


  /**
   * Same as above but with an exception. Also needs work.
   */
  public void die(String what, Exception e) {
    if (e != null) e.printStackTrace();
    die(what);
  }


  /**
   * Conveniency method so perform initialization tasks when the activity is
   * created, while avoiding the ackward call to onCreate() with the bundle
   * and super.onCreate().
   */
  public void create() {

  }

  /**
   * Should trigger a graceful activity/service shutdown (calling onPause/onStop, etc).
   */
  public void exit() {
    surface.finish();
  }


  /**
   * Called to dispose of resources and shut down the sketch.
   * Destroys the thread, dispose the renderer, and notify listeners.
   * <p>
   * Not to be called or overriden by users. If called multiple times,
   * will only notify listeners once. Register a dispose listener instead.
   */
  final public void dispose() {
    // moved here from stop()
    finished = true;  // let the sketch know it is shut down time

    // call to shut down renderer, in case it needs it (pdf does)
    if (surface != null) {
      surface.stopThread();
      surface.dispose();
    }
    if (g != null) {
      g.clearState(); // This should probably go in dispose, but for the time being...
      g.dispose();
    }

    handleMethods("dispose");
  }



  //////////////////////////////////////////////////////////////


  /**
   * Call a method in the current class based on its name.
   * <p/>
   * Note that the function being called must be public. Inside the PDE,
   * 'public' is automatically added, but when used without the preprocessor,
   * (like from Eclipse) you'll have to do it yourself.
   */
  public void method(String name) {
    try {
      Method method = getClass().getMethod(name, new Class[] {});
      method.invoke(this, new Object[] { });

    } catch (IllegalArgumentException e) {
      printStackTrace(e);
    } catch (IllegalAccessException e) {
      printStackTrace(e);
    } catch (InvocationTargetException e) {
      e.getTargetException().printStackTrace();
    } catch (NoSuchMethodException nsme) {
      System.err.println("There is no public " + name + "() method " +
                         "in the class " + getClass().getName());
    } catch (Exception e) {
      printStackTrace(e);
    }
  }


  /**
   * Launch a new thread and call the specified function from that new thread.
   * This is a very simple way to do a thread without needing to get into
   * classes, runnables, etc.
   * <p/>
   * Note that the function being called must be public. Inside the PDE,
   * 'public' is automatically added, but when used without the preprocessor,
   * (like from Eclipse) you'll have to do it yourself.
   */
  public void thread(final String name) {
    Thread later = new Thread() {
      @Override
      public void run() {
        method(name);
      }
    };
    later.start();
  }



  //////////////////////////////////////////////////////////////

  // SCREEN GRABASS


  /**
   * Intercepts any relative paths to make them absolute (relative
   * to the sketch folder) before passing to save() in PImage.
   * (Changed in 0100)
   */
  public void save(String filename) {
    g.save(savePath(filename));
  }


  /**
   * Grab an image of what's currently in the drawing area and save it
   * as a .tif or .tga file.
   * <P>
   * Best used just before endDraw() at the end of your draw().
   * This can only create .tif or .tga images, so if neither extension
   * is specified it defaults to writing a tiff and adds a .tif suffix.
   */
  public void saveFrame() {
    try {
      g.save(savePath("screen-" + nf(frameCount, 4) + ".tif"));
    } catch (SecurityException se) {
      System.err.println("Can't use saveFrame() when running in a browser, " +
                         "unless using a signed applet.");
    }
  }


  /**
   * Save the current frame as a .tif or .tga image.
   * <P>
   * The String passed in can contain a series of # signs
   * that will be replaced with the screengrab number.
   * <PRE>
   * i.e. saveFrame("blah-####.tif");
   *      // saves a numbered tiff image, replacing the
   *      // #### signs with zeros and the frame number </PRE>
   */
  public void saveFrame(String what) {
    try {
      g.save(savePath(insertFrame(what)));
    } catch (SecurityException se) {
      System.err.println("Can't use saveFrame() when running in a browser, " +
                         "unless using a signed applet.");
    }
  }


  /**
   * Check a string for #### signs to see if the frame number should be
   * inserted. Used for functions like saveFrame() and beginRecord() to
   * replace the # marks with the frame number. If only one # is used,
   * it will be ignored, under the assumption that it's probably not
   * intended to be the frame number.
   */
  protected String insertFrame(String what) {
    int first = what.indexOf('#');
    int last = what.lastIndexOf('#');

    if ((first != -1) && (last - first > 0)) {
      String prefix = what.substring(0, first);
      int count = last - first + 1;
      String suffix = what.substring(last + 1);
      return prefix + nf(frameCount, count) + suffix;
    }
    return what;  // no change
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
   */
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


  static public void print(byte what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(boolean what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(char what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(int what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(float what) {
    System.out.print(what);
    System.out.flush();
  }

  static public void print(String what) {
    System.out.print(what);
    System.out.flush();
  }

  /**
   * @param variables list of data, separated by commas
   */
  static public void print(Object... variables) {
    StringBuilder sb = new StringBuilder();
    for (Object o : variables) {
      if (sb.length() != 0) {
        sb.append(" ");
      }
      if (o == null) {
        sb.append("null");
      } else {
        sb.append(o.toString());
      }
    }
    System.out.print(sb.toString());
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

  //

  static public void println() {
    System.out.println();
  }

  //

  static public void println(byte what) {
    print(what); System.out.println();
  }

  static public void println(boolean what) {
    print(what); System.out.println();
  }

  static public void println(char what) {
    print(what); System.out.println();
  }

  static public void println(int what) {
    print(what); System.out.println();
  }

  static public void println(float what) {
    print(what); System.out.println();
  }

  static public void println(String what) {
    print(what); System.out.println();
  }

  /**
   * @param variables list of data, separated by commas
   */
  static public void println(Object... variables) {
//    System.out.println("got " + variables.length + " variables");
    print(variables);
    println();
  }

  static public void println(Object what) {
    if (what == null) {
      // special case since this does fuggly things on > 1.1
      System.out.println("null");

    } else {
      String name = what.getClass().getName();
      if (name.charAt(0) == '[') {
        switch (name.charAt(1)) {
        case '[':
          // don't even mess with multi-dimensional arrays (case '[')
          // or anything else that's not int, float, boolean, char
          System.out.println(what);
          break;

        case 'L':
          // print a 1D array of objects as individual elements
          Object poo[] = (Object[]) what;
          for (int i = 0; i < poo.length; i++) {
            if (poo[i] instanceof String) {
              System.out.println("[" + i + "] \"" + poo[i] + "\"");
            } else {
              System.out.println("[" + i + "] " + poo[i]);
            }
          }
          break;

        case 'Z':  // boolean
          boolean zz[] = (boolean[]) what;
          for (int i = 0; i < zz.length; i++) {
            System.out.println("[" + i + "] " + zz[i]);
          }
          break;

        case 'B':  // byte
          byte bb[] = (byte[]) what;
          for (int i = 0; i < bb.length; i++) {
            System.out.println("[" + i + "] " + bb[i]);
          }
          break;

        case 'C':  // char
          char cc[] = (char[]) what;
          for (int i = 0; i < cc.length; i++) {
            System.out.println("[" + i + "] '" + cc[i] + "'");
          }
          break;

        case 'I':  // int
          int ii[] = (int[]) what;
          for (int i = 0; i < ii.length; i++) {
            System.out.println("[" + i + "] " + ii[i]);
          }
          break;

        case 'F':  // float
          float ff[] = (float[]) what;
          for (int i = 0; i < ff.length; i++) {
            System.out.println("[" + i + "] " + ff[i]);
          }
          break;

          /*
        case 'D':  // double
          double dd[] = (double[]) what;
          for (int i = 0; i < dd.length; i++) {
            System.out.println("[" + i + "] " + dd[i]);
          }
          break;
          */

        default:
          System.out.println(what);
        }
      } else {  // not an array
        System.out.println(what);
      }
    }
  }


/**
 * @webref output:text_area
 * @param what one-dimensional array
 * @usage IDE
 * @see PApplet#print(byte)
 * @see PApplet#println()
 */
  static public void printArray(Object what) {
    if (what == null) {
      // special case since this does fuggly things on > 1.1
      System.out.println("null");

    } else {
      String name = what.getClass().getName();
      if (name.charAt(0) == '[') {
        switch (name.charAt(1)) {
        case '[':
          // don't even mess with multi-dimensional arrays (case '[')
          // or anything else that's not int, float, boolean, char
          System.out.println(what);
          break;

        case 'L':
          // print a 1D array of objects as individual elements
          Object poo[] = (Object[]) what;
          for (int i = 0; i < poo.length; i++) {
            if (poo[i] instanceof String) {
              System.out.println("[" + i + "] \"" + poo[i] + "\"");
            } else {
              System.out.println("[" + i + "] " + poo[i]);
            }
          }
          break;

        case 'Z':  // boolean
          boolean zz[] = (boolean[]) what;
          for (int i = 0; i < zz.length; i++) {
            System.out.println("[" + i + "] " + zz[i]);
          }
          break;

        case 'B':  // byte
          byte bb[] = (byte[]) what;
          for (int i = 0; i < bb.length; i++) {
            System.out.println("[" + i + "] " + bb[i]);
          }
          break;

        case 'C':  // char
          char cc[] = (char[]) what;
          for (int i = 0; i < cc.length; i++) {
            System.out.println("[" + i + "] '" + cc[i] + "'");
          }
          break;

        case 'I':  // int
          int ii[] = (int[]) what;
          for (int i = 0; i < ii.length; i++) {
            System.out.println("[" + i + "] " + ii[i]);
          }
          break;

        case 'J':  // int
          long jj[] = (long[]) what;
          for (int i = 0; i < jj.length; i++) {
            System.out.println("[" + i + "] " + jj[i]);
          }
          break;

        case 'F':  // float
          float ff[] = (float[]) what;
          for (int i = 0; i < ff.length; i++) {
            System.out.println("[" + i + "] " + ff[i]);
          }
          break;

        case 'D':  // double
          double dd[] = (double[]) what;
          for (int i = 0; i < dd.length; i++) {
            System.out.println("[" + i + "] " + dd[i]);
          }
          break;

        default:
          System.out.println(what);
        }
      } else {  // not an array
        System.out.println(what);
      }
    }
    System.out.flush();
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


  static public final float abs(float n) {
    return (n < 0) ? -n : n;
  }

  static public final int abs(int n) {
    return (n < 0) ? -n : n;
  }

  static public final float sq(float a) {
    return a*a;
  }

  static public final float sqrt(float a) {
    return (float)Math.sqrt(a);
  }

  static public final float log(float a) {
    return (float)Math.log(a);
  }

  static public final float exp(float a) {
    return (float)Math.exp(a);
  }

  static public final float pow(float a, float b) {
    return (float)Math.pow(a, b);
  }


  static public final int max(int a, int b) {
    return (a > b) ? a : b;
  }

  static public final float max(float a, float b) {
    return (a > b) ? a : b;
  }


  static public final int max(int a, int b, int c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }

  static public final float max(float a, float b, float c) {
    return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
  }


  /**
   * Find the maximum value in an array.
   * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
   * @param list the source array
   * @return The maximum value
   */
  static public final int max(int[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    int max = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] > max) max = list[i];
    }
    return max;
  }

  /**
   * Find the maximum value in an array.
   * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
   * @param list the source array
   * @return The maximum value
   */
  static public final float max(float[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    float max = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] > max) max = list[i];
    }
    return max;
  }


  static public final int min(int a, int b) {
    return (a < b) ? a : b;
  }

  static public final float min(float a, float b) {
    return (a < b) ? a : b;
  }


  static public final int min(int a, int b, int c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }

  static public final float min(float a, float b, float c) {
    return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
  }


  /**
   * Find the minimum value in an array.
   * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
   * @param list the source array
   * @return The minimum value
   */
  static public final int min(int[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    int min = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] < min) min = list[i];
    }
    return min;
  }
  /**
   * Find the minimum value in an array.
   * Throws an ArrayIndexOutOfBoundsException if the array is length 0.
   * @param list the source array
   * @return The minimum value
   */
  static public final float min(float[] list) {
    if (list.length == 0) {
      throw new ArrayIndexOutOfBoundsException(ERROR_MIN_MAX);
    }
    float min = list[0];
    for (int i = 1; i < list.length; i++) {
      if (list[i] < min) min = list[i];
    }
    return min;
  }


  static public final int constrain(int amt, int low, int high) {
    return (amt < low) ? low : ((amt > high) ? high : amt);
  }

  static public final float constrain(float amt, float low, float high) {
    return (amt < low) ? low : ((amt > high) ? high : amt);
  }


  static public final float sin(float angle) {
    return (float)Math.sin(angle);
  }

  static public final float cos(float angle) {
    return (float)Math.cos(angle);
  }

  static public final float tan(float angle) {
    return (float)Math.tan(angle);
  }


  static public final float asin(float value) {
    return (float)Math.asin(value);
  }

  static public final float acos(float value) {
    return (float)Math.acos(value);
  }

  static public final float atan(float value) {
    return (float)Math.atan(value);
  }

  static public final float atan2(float a, float b) {
    return (float)Math.atan2(a, b);
  }


  static public final float degrees(float radians) {
    return radians * RAD_TO_DEG;
  }

  static public final float radians(float degrees) {
    return degrees * DEG_TO_RAD;
  }


  static public final int ceil(float what) {
    return (int) Math.ceil(what);
  }

  static public final int floor(float what) {
    return (int) Math.floor(what);
  }

  static public final int round(float what) {
    return (int) Math.round(what);
  }


  static public final float mag(float a, float b) {
    return (float)Math.sqrt(a*a + b*b);
  }

  static public final float mag(float a, float b, float c) {
    return (float)Math.sqrt(a*a + b*b + c*c);
  }


  static public final float dist(float x1, float y1, float x2, float y2) {
    return sqrt(sq(x2-x1) + sq(y2-y1));
  }

  static public final float dist(float x1, float y1, float z1,
                                 float x2, float y2, float z2) {
    return sqrt(sq(x2-x1) + sq(y2-y1) + sq(z2-z1));
  }


  static public final float lerp(float start, float stop, float amt) {
    return start + (stop-start) * amt;
  }

  /**
   * Normalize a value to exist between 0 and 1 (inclusive).
   * Mathematically the opposite of lerp(), figures out what proportion
   * a particular value is relative to start and stop coordinates.
   */
  static public final float norm(float value, float start, float stop) {
    return (value - start) / (stop - start);
  }

  /**
   * Convenience function to map a variable from one coordinate space
   * to another. Equivalent to unlerp() followed by lerp().
   */
  static public final float map(float value,
                                float istart, float istop,
                                float ostart, float ostop) {
    return ostart + (ostop - ostart) * ((value - istart) / (istop - istart));
  }



  //////////////////////////////////////////////////////////////

  // RANDOM NUMBERS


  Random internalRandom;

  /**
   *
   */
  public final float random(float high) {
    // avoid an infinite loop when 0 or NaN are passed in
    if (high == 0 || high != high) {
      return 0;
    }

    if (internalRandom == null) {
      internalRandom = new Random();
    }

    // for some reason (rounding error?) Math.random() * 3
    // can sometimes return '3' (once in ~30 million tries)
    // so a check was added to avoid the inclusion of 'howbig'
    float value = 0;
    do {
      value = internalRandom.nextFloat() * high;
    } while (value == high);
    return value;
  }

  /**
   * ( begin auto-generated from randomGaussian.xml )
   *
   * Returns a float from a random series of numbers having a mean of 0
   * and standard deviation of 1. Each time the <b>randomGaussian()</b>
   * function is called, it returns a number fitting a Gaussian, or
   * normal, distribution. There is theoretically no minimum or maximum
   * value that <b>randomGaussian()</b> might return. Rather, there is
   * just a very low probability that values far from the mean will be
   * returned; and a higher probability that numbers near the mean will
   * be returned.
   *
   * ( end auto-generated )
   * @webref math:random
   * @see PApplet#random(float,float)
   * @see PApplet#noise(float, float, float)
   */
  public final float randomGaussian() {
    if (internalRandom == null) {
      internalRandom = new Random();
    }
    return (float) internalRandom.nextGaussian();
  }


  /**
   * ( begin auto-generated from random.xml )
   *
   * Generates random numbers. Each time the <b>random()</b> function is
   * called, it returns an unexpected value within the specified range. If
   * one parameter is passed to the function it will return a <b>float</b>
   * between zero and the value of the <b>high</b> parameter. The function
   * call <b>random(5)</b> returns values between 0 and 5 (starting at zero,
   * up to but not including 5). If two parameters are passed, it will return
   * a <b>float</b> with a value between the the parameters. The function
   * call <b>random(-5, 10.2)</b> returns values starting at -5 up to (but
   * not including) 10.2. To convert a floating-point random number to an
   * integer, use the <b>int()</b> function.
   *
   * ( end auto-generated )
   * @webref math:random
   * @param low lower limit
   * @param high upper limit
   * @see PApplet#randomSeed(long)
   * @see PApplet#noise(float, float, float)
   */
  public final float random(float low, float high) {
    if (low >= high) return low;
    float diff = high - low;
    float value = 0;
    // because of rounding error, can't just add low, otherwise it may hit high
    // https://github.com/processing/processing/issues/4551
    do {
      value = random(diff) + low;
    } while (value == high);
    return value;
  }


  /**
   * ( begin auto-generated from randomSeed.xml )
   *
   * Sets the seed value for <b>random()</b>. By default, <b>random()</b>
   * produces different results each time the program is run. Set the
   * <b>value</b> parameter to a constant to return the same pseudo-random
   * numbers each time the software is run.
   *
   * ( end auto-generated )
   * @webref math:random
   * @param seed seed value
   * @see PApplet#random(float,float)
   * @see PApplet#noise(float, float, float)
   * @see PApplet#noiseSeed(long)
   */
  public final void randomSeed(long seed) {
    if (internalRandom == null) {
      internalRandom = new Random();
    }
    internalRandom.setSeed(seed);
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

  static final int PERLIN_YWRAPB = 4;
  static final int PERLIN_YWRAP = 1<<PERLIN_YWRAPB;
  static final int PERLIN_ZWRAPB = 8;
  static final int PERLIN_ZWRAP = 1<<PERLIN_ZWRAPB;
  static final int PERLIN_SIZE = 4095;

  int perlin_octaves = 4; // default to medium smooth
  float perlin_amp_falloff = 0.5f; // 50% reduction/octave

  // [toxi 031112]
  // new vars needed due to recent change of cos table in PGraphics
  int perlin_TWOPI, perlin_PI;
  float[] perlin_cosTable;
  float[] perlin;

  Random perlinRandom;


  /**
   */
  public float noise(float x) {
    // is this legit? it's a dumb way to do it (but repair it later)
    return noise(x, 0f, 0f);
  }

  /**
   */
  public float noise(float x, float y) {
    return noise(x, y, 0f);
  }

  /**
   * ( begin auto-generated from noise.xml )
   *
   * Returns the Perlin noise value at specified coordinates. Perlin noise is
   * a random sequence generator producing a more natural ordered, harmonic
   * succession of numbers compared to the standard <b>random()</b> function.
   * It was invented by Ken Perlin in the 1980s and been used since in
   * graphical applications to produce procedural textures, natural motion,
   * shapes, terrains etc.<br /><br /> The main difference to the
   * <b>random()</b> function is that Perlin noise is defined in an infinite
   * n-dimensional space where each pair of coordinates corresponds to a
   * fixed semi-random value (fixed only for the lifespan of the program).
   * The resulting value will always be between 0.0 and 1.0. Processing can
   * compute 1D, 2D and 3D noise, depending on the number of coordinates
   * given. The noise value can be animated by moving through the noise space
   * as demonstrated in the example above. The 2nd and 3rd dimension can also
   * be interpreted as time.<br /><br />The actual noise is structured
   * similar to an audio signal, in respect to the function's use of
   * frequencies. Similar to the concept of harmonics in physics, perlin
   * noise is computed over several octaves which are added together for the
   * final result. <br /><br />Another way to adjust the character of the
   * resulting sequence is the scale of the input coordinates. As the
   * function works within an infinite space the value of the coordinates
   * doesn't matter as such, only the distance between successive coordinates
   * does (eg. when using <b>noise()</b> within a loop). As a general rule
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
   * @see PApplet#noiseSeed(long)
   * @see PApplet#noiseDetail(int, float)
   * @see PApplet#random(float,float)
   */
  public float noise(float x, float y, float z) {
    if (perlin == null) {
      if (perlinRandom == null) {
        perlinRandom = new Random();
      }
      perlin = new float[PERLIN_SIZE + 1];
      for (int i = 0; i < PERLIN_SIZE + 1; i++) {
        perlin[i] = perlinRandom.nextFloat(); //(float)Math.random();
      }
      // [toxi 031112]
      // noise broke due to recent change of cos table in PGraphics
      // this will take care of it
      perlin_cosTable = PGraphics.cosLUT;
      perlin_TWOPI = perlin_PI = PGraphics.SINCOS_LENGTH;
      perlin_PI >>= 1;
    }

    if (x<0) x=-x;
    if (y<0) y=-y;
    if (z<0) z=-z;

    int xi=(int)x, yi=(int)y, zi=(int)z;
    float xf = x - xi;
    float yf = y - yi;
    float zf = z - zi;
    float rxf, ryf;

    float r=0;
    float ampl=0.5f;

    float n1,n2,n3;

    for (int i=0; i<perlin_octaves; i++) {
      int of=xi+(yi<<PERLIN_YWRAPB)+(zi<<PERLIN_ZWRAPB);

      rxf=noise_fsc(xf);
      ryf=noise_fsc(yf);

      n1  = perlin[of&PERLIN_SIZE];
      n1 += rxf*(perlin[(of+1)&PERLIN_SIZE]-n1);
      n2  = perlin[(of+PERLIN_YWRAP)&PERLIN_SIZE];
      n2 += rxf*(perlin[(of+PERLIN_YWRAP+1)&PERLIN_SIZE]-n2);
      n1 += ryf*(n2-n1);

      of += PERLIN_ZWRAP;
      n2  = perlin[of&PERLIN_SIZE];
      n2 += rxf*(perlin[(of+1)&PERLIN_SIZE]-n2);
      n3  = perlin[(of+PERLIN_YWRAP)&PERLIN_SIZE];
      n3 += rxf*(perlin[(of+PERLIN_YWRAP+1)&PERLIN_SIZE]-n3);
      n2 += ryf*(n3-n2);

      n1 += noise_fsc(zf)*(n2-n1);

      r += n1*ampl;
      ampl *= perlin_amp_falloff;
      xi<<=1; xf*=2;
      yi<<=1; yf*=2;
      zi<<=1; zf*=2;

      if (xf>=1.0f) { xi++; xf--; }
      if (yf>=1.0f) { yi++; yf--; }
      if (zf>=1.0f) { zi++; zf--; }
    }
    return r;
  }

  // [toxi 031112]
  // now adjusts to the size of the cosLUT used via
  // the new variables, defined above
  private float noise_fsc(float i) {
    // using bagel's cosine table instead
    return 0.5f*(1.0f-perlin_cosTable[(int)(i*perlin_PI)%perlin_TWOPI]);
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
   * result in greater than 1.0 values returned by <b>noise()</b>.<br /><br
   * />By changing these parameters, the signal created by the <b>noise()</b>
   * function can be adapted to fit very specific needs and characteristics.
   *
   * ( end auto-generated )
   * @webref math:random
   * @param lod number of octaves to be used by the noise
   * @see PApplet#noise(float, float, float)
   */
  public void noiseDetail(int lod) {
    if (lod>0) perlin_octaves=lod;
  }

  /**
   * @see #noiseDetail(int)
   * @param falloff falloff factor for each octave
   */
  public void noiseDetail(int lod, float falloff) {
    if (lod>0) perlin_octaves=lod;
    if (falloff>0) perlin_amp_falloff=falloff;
  }

  /**
   * ( begin auto-generated from noiseSeed.xml )
   *
   * Sets the seed value for <b>noise()</b>. By default, <b>noise()</b>
   * produces different results each time the program is run. Set the
   * <b>value</b> parameter to a constant to return the same pseudo-random
   * numbers each time the software is run.
   *
   * ( end auto-generated )
   * @webref math:random
   * @param seed seed value
   * @see PApplet#noise(float, float, float)
   * @see PApplet#noiseDetail(int, float)
   * @see PApplet#random(float,float)
   * @see PApplet#randomSeed(long)
   */
  public void noiseSeed(long seed) {
    if (perlinRandom == null) perlinRandom = new Random();
    perlinRandom.setSeed(seed);
    // force table reset after changing the random number seed [0122]
    perlin = null;
  }



  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .



  public PImage loadImage(String filename) { //, Object params) {
//    return loadImage(filename, null);
    InputStream stream = createInput(filename);
    if (stream == null) {
      System.err.println("Could not find the image " + filename + ".");
      return null;
    }
//    long t = System.currentTimeMillis();
    Bitmap bitmap = null;
    try {
      bitmap = BitmapFactory.decodeStream(stream);
    } finally {
      try {
        stream.close();
        stream = null;
      } catch (IOException e) { }
    }
//    int much = (int) (System.currentTimeMillis() - t);
//    println("loadImage(" + filename + ") was " + nfc(much));
    if (bitmap == null) {
      System.err.println("Could not load the image because the bitmap was empty.");
      return null;
    } else {
      PImage image = new PImage(bitmap);
      image.parent = this;
      return image;
    }
  }


  public PImage loadImage(String filename, String extension) {
    return loadImage(filename);
  }


  public PImage requestImage(String filename) {
    PImage vessel = createImage(0, 0, ARGB);
    AsyncImageLoader ail = new AsyncImageLoader(filename, vessel);
    ail.start();
    return vessel;
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
  public int requestImageMax = 4;
  volatile int requestImageCount;

  // Removed 'extension' from the android version. If the extension is needed
  // later, re-copy this from the original PApplet code.
  class AsyncImageLoader extends Thread {
    String filename;
    PImage vessel;

    public AsyncImageLoader(String filename, PImage vessel) {
      this.filename = filename;
      this.vessel = vessel;
    }

    @Override
    public void run() {
      while (requestImageCount == requestImageMax) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) { }
      }
      requestImageCount++;

      PImage actual = loadImage(filename);

      // An error message should have already printed
      if (actual == null) {
        vessel.width = -1;
        vessel.height = -1;

      } else {
        vessel.width = actual.width;
        vessel.height = actual.height;
        vessel.format = actual.format;
        vessel.pixels = actual.pixels;
        // an android, pixels[] will probably be null, we want this one
        vessel.bitmap = actual.bitmap;

        vessel.pixelWidth = actual.width;
        vessel.pixelHeight = actual.height;
        vessel.pixelDensity = 1;
      }
      requestImageCount--;
    }
  }



  //////////////////////////////////////////////////////////////

  // DATA I/O


  public XML createXML(String name) {
    try {
      return new XML(name);
    } catch (Exception e) {
      printStackTrace(e);
      return null;
    }
  }


  /**
   * @webref input:files
   * @param filename name of a file in the data folder or a URL.
   * @see XML#parse(String)
   * @see PApplet#loadBytes(String)
   * @see PApplet#loadStrings(String)
   * @see PApplet#loadTable(String)
   */
  public XML loadXML(String filename) {
    return loadXML(filename, null);
  }


  // version that uses 'options' though there are currently no supported options
  public XML loadXML(String filename, String options) {
    try {
      return new XML(createInput(filename), options);
    } catch (Exception e) {
      printStackTrace(e);
      return null;
    }
  }


  public XML parseXML(String xmlString) {
    return parseXML(xmlString, null);
  }


  public XML parseXML(String xmlString, String options) {
    try {
      return XML.parse(xmlString, options);
    } catch (Exception e) {
      printStackTrace(e);
      return null;
    }
  }


  public boolean saveXML(XML xml, String filename) {
    return saveXML(xml, filename, null);
  }


  public boolean saveXML(XML xml, String filename, String options) {
    return xml.save(saveFile(filename), options);
  }


  /**
   * @webref input:files
   * @param input String to parse as a JSONObject
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   */
  public JSONObject parseJSONObject(String input) {
    return new JSONObject(new StringReader(input));
  }


  /**
   * @webref input:files
   * @param filename name of a file in the data folder or a URL
   * @see JSONObject
   * @see JSONArray
   * @see PApplet#loadJSONArray(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   * @see PApplet#saveJSONArray(JSONArray, String)
   */
  public JSONObject loadJSONObject(String filename) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(filename);
    JSONObject outgoing = new JSONObject(reader);
    try {
      reader.close();
    } catch (IOException e) {  // not sure what would cause this
      e.printStackTrace();
    }
    return outgoing;
  }


  static public JSONObject loadJSONObject(File file) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(file);
    JSONObject outgoing = new JSONObject(reader);
    try {
      reader.close();
    } catch (IOException e) {  // not sure what would cause this
      e.printStackTrace();
    }
    return outgoing;
  }


  /**
   * @webref output:files
   * @see JSONObject
   * @see JSONArray
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#loadJSONArray(String)
   * @see PApplet#saveJSONArray(JSONArray, String)
   */
  public boolean saveJSONObject(JSONObject json, String filename) {
    return saveJSONObject(json, filename, null);
  }


  /**
   * @nowebref
   */
  public boolean saveJSONObject(JSONObject json, String filename, String options) {
    return json.save(saveFile(filename), options);
  }


  /**
   * @webref input:files
   * @param input String to parse as a JSONArray
   * @see JSONObject
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   */
  public JSONArray parseJSONArray(String input) {
    return new JSONArray(new StringReader(input));
  }


  /**
   * @webref input:files
   * @param filename name of a file in the data folder or a URL
   * @see JSONArray
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   * @see PApplet#saveJSONArray(JSONArray, String)
   */
  public JSONArray loadJSONArray(String filename) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(filename);
    JSONArray outgoing = new JSONArray(reader);
    try {
      reader.close();
    } catch (IOException e) {  // not sure what would cause this
      e.printStackTrace();
    }
    return outgoing;
  }


  static public JSONArray loadJSONArray(File file) {
    // can't pass of createReader() to the constructor b/c of resource leak
    BufferedReader reader = createReader(file);
    JSONArray outgoing = new JSONArray(reader);
    try {
      reader.close();
    } catch (IOException e) {  // not sure what would cause this
      e.printStackTrace();
    }
    return outgoing;
  }


  /**
   * @webref output:files
   * @see JSONObject
   * @see JSONArray
   * @see PApplet#loadJSONObject(String)
   * @see PApplet#loadJSONArray(String)
   * @see PApplet#saveJSONObject(JSONObject, String)
   */
  public boolean saveJSONArray(JSONArray json, String filename) {
    return saveJSONArray(json, filename, null);
  }


  public boolean saveJSONArray(JSONArray json, String filename, String options) {
    return json.save(saveFile(filename), options);
  }


  public Table createTable() {
    return new Table();
  }


  /**
   * @webref input:files
   * @param filename name of a file in the data folder or a URL.
   * @see PApplet#loadBytes(String)
   * @see PApplet#loadStrings(String)
   * @see PApplet#loadXML(String)
   */
  public Table loadTable(String filename) {
    return loadTable(filename, null);
  }


  public Table loadTable(String filename, String options) {
    try {
      String ext = checkExtension(filename);
      if (ext != null) {
        if (ext.equals("csv") || ext.equals("tsv")) {
          if (options == null) {
            options = ext;
          } else {
            options = ext + "," + options;
          }
        }
      }
      return new Table(createInput(filename), options);

    } catch (IOException e) {
      printStackTrace(e);
      return null;
    }
  }


  public boolean saveTable(Table table, String filename) {
    return saveTable(table, filename, null);
  }


  public boolean saveTable(Table table, String filename, String options) {
    try {
      table.save(saveFile(filename), options);
      return true;
    } catch (IOException e) {
      printStackTrace(e);
    }
    return false;
  }



  // FONT I/O


  public PFont loadFont(String filename) {
    try {
      InputStream input = createInput(filename);
      return new PFont(input);

    } catch (Exception e) {
      die("Could not load font " + filename + ". " +
          "Make sure that the font has been copied " +
          "to the data folder of your sketch.", e);
    }
    return null;
  }


  /**
   * Used by PGraphics to remove the requirement for loading a font!
   */
  protected PFont createDefaultFont(float size) {
    return createFont("SansSerif", size, true, null);
  }


  public PFont createFont(String name, float size) {
    return createFont(name, size, true, null);
  }


  public PFont createFont(String name, float size, boolean smooth) {
    return createFont(name, size, smooth, null);
  }


  /**
   * Create a bitmap font on the fly from either a font name that's
   * installed on the system, or from a .ttf or .otf that's inside
   * the data folder of this sketch.
   * <P/>
   * Use 'null' for the charset if you want to dynamically create
   * character bitmaps only as they're needed.
   */
  public PFont createFont(String name, float size,
                          boolean smooth, char[] charset) {
    String lowerName = name.toLowerCase();
    Typeface baseFont = null;

    if (lowerName.endsWith(".otf") || lowerName.endsWith(".ttf")) {
      AssetManager assets = surface.getAssets();
      baseFont = Typeface.createFromAsset(assets, name);
    } else {
      baseFont = (Typeface) PFont.findNative(name);
    }
    return new PFont(baseFont, round(size), smooth, charset);
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


  public String[] listPaths(String path, String... options) {
    File[] list = listFiles(path, options);

    int offset = 0;
    for (String opt : options) {
      if (opt.equals("relative")) {
        if (!path.endsWith(File.pathSeparator)) {
          path += File.pathSeparator;
        }
        offset = path.length();
        break;
      }
    }
    String[] outgoing = new String[list.length];
    for (int i = 0; i < list.length; i++) {
      // as of Java 1.8, substring(0) returns the original object
      outgoing[i] = list[i].getAbsolutePath().substring(offset);
    }
    return outgoing;
  }


  public File[] listFiles(String path, String... options) {
    File file = new File(path);
    // if not an absolute path, make it relative to the sketch folder
    if (!file.isAbsolute()) {
      file = sketchFile(path);
    }
    return listFiles(file, options);
  }


  // "relative" -> no effect with the Files version, but important for listPaths
  // "recursive"
  // "extension=js" or "extensions=js|csv|txt" (no dot)
  // "directories" -> only directories
  // "files" -> only files
  // "hidden" -> include hidden files (prefixed with .) disabled by default
  static public File[] listFiles(File base, String... options) {
    boolean recursive = false;
    String[] extensions = null;
    boolean directories = true;
    boolean files = true;
    boolean hidden = false;

    for (String opt : options) {
      if (opt.equals("recursive")) {
        recursive = true;
      } else if (opt.startsWith("extension=")) {
        extensions = new String[] { opt.substring(10) };
      } else if (opt.startsWith("extensions=")) {
        extensions = split(opt.substring(10), ',');
      } else if (opt.equals("files")) {
        directories = false;
      } else if (opt.equals("directories")) {
        files = false;
      } else if (opt.equals("hidden")) {
        hidden = true;
      } else if (opt.equals("relative")) {
        // ignored
      } else {
        throw new RuntimeException(opt + " is not a listFiles() option");
      }
    }

    if (extensions != null) {
      for (int i = 0; i < extensions.length; i++) {
        extensions[i] = "." + extensions[i];
      }
    }

    if (!files && !directories) {
      // just make "only files" and "only directories" mean... both
      files = true;
      directories = true;
    }

    if (!base.canRead()) {
      return null;
    }

    List<File> outgoing = new ArrayList<>();
    listFilesImpl(base, recursive, extensions, hidden, directories, files, outgoing);
    return outgoing.toArray(new File[0]);
  }


  static void listFilesImpl(File folder, boolean recursive,
                            String[] extensions, boolean hidden,
                            boolean directories, boolean files,
                            List<File> list) {
    File[] items = folder.listFiles();
    if (items != null) {
      for (File item : items) {
        String name = item.getName();
        if (!hidden && name.charAt(0) == '.') {
          continue;
        }
        if (item.isDirectory()) {
          if (recursive) {
            listFilesImpl(item, recursive, extensions, hidden, directories, files, list);
          }
          if (directories) {
            list.add(item);
          }
        } else if (files) {
          if (extensions == null) {
            list.add(item);
          } else {
            for (String ext : extensions) {
              if (item.getName().toLowerCase().endsWith(ext)) {
                list.add(item);
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
  static public String checkExtension(String filename) {
    // Don't consider the .gz as part of the name, createInput()
    // and createOuput() will take care of fixing that up.
    if (filename.toLowerCase().endsWith(".gz")) {
      filename = filename.substring(0, filename.length() - 3);
    }
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex != -1) {
      return filename.substring(dotIndex + 1).toLowerCase();
    }
    return null;
  }


  //////////////////////////////////////////////////////////////

  // READERS AND WRITERS


  /**
   * I want to read lines from a file. I have RSI from typing these
   * eight lines of code so many times.
   */
  public BufferedReader createReader(String filename) {
    try {
      InputStream is = createInput(filename);
      if (is == null) {
        System.err.println(filename + " does not exist or could not be read");
        return null;
      }
      return createReader(is);

    } catch (Exception e) {
      if (filename == null) {
        System.err.println("Filename passed to reader() was null");
      } else {
        System.err.println("Couldn't create a reader for " + filename);
      }
    }
    return null;
  }


  /**
   * I want to read lines from a file. And I'm still annoyed.
   */
  static public BufferedReader createReader(File file) {
    try {
      InputStream is = new FileInputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        is = new GZIPInputStream(is);
      }
      return createReader(is);

    } catch (Exception e) {
      if (file == null) {
        throw new RuntimeException("File passed to createReader() was null");
      } else {
        e.printStackTrace();
        throw new RuntimeException("Couldn't create a reader for " +
                                   file.getAbsolutePath());
      }
    }
    //return null;
  }


  /**
   * I want to read lines from a stream. If I have to type the
   * following lines any more I'm gonna send Sun my medical bills.
   */
  static public BufferedReader createReader(InputStream input) {
    InputStreamReader isr =
      new InputStreamReader(input, CompatUtils.getCharsetUTF8());

    BufferedReader reader = new BufferedReader(isr);
    // consume the Unicode BOM (byte order marker) if present
    try {
      reader.mark(1);
      int c = reader.read();
      // if not the BOM, back up to the beginning again
      if (c != '\uFEFF') {
        reader.reset();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return reader;
  }


  /**
   * I want to print lines to a file. Why can't I?
   */
  public PrintWriter createWriter(String filename) {
    return createWriter(saveFile(filename));
  }


  /**
   * I want to print lines to a file. I have RSI from typing these
   * eight lines of code so many times.
   */
  static public PrintWriter createWriter(File file) {
    try {
      OutputStream output = new FileOutputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        output = new GZIPOutputStream(output);
      }
      return createWriter(output);

    } catch (Exception e) {
      if (file == null) {
        throw new RuntimeException("File passed to createWriter() was null");
      } else {
        e.printStackTrace();
        throw new RuntimeException("Couldn't create a writer for " +
                                   file.getAbsolutePath());
      }
    }
    //return null;
  }


  /**
   * I want to print lines to a file. Why am I always explaining myself?
   * It's the JavaSoft API engineers who need to explain themselves.
   */
  static public PrintWriter createWriter(OutputStream output) {
    BufferedOutputStream bos = new BufferedOutputStream(output, 8192);
    OutputStreamWriter osw =
      new OutputStreamWriter(bos, CompatUtils.getCharsetUTF8());
    return new PrintWriter(osw);
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
   * <P>
   * If the requested item doesn't exist, null is returned.
   * (Prior to 0096, die() would be called, killing the applet)
   * <P>
   * For 0096+, the "data" folder is exported intact with subfolders,
   * and openStream() properly handles subdirectories from the data folder
   * <P>
   * If not online, this will also check to see if the user is asking
   * for a file whose name isn't properly capitalized. This helps prevent
   * issues when a sketch is exported to the web, where case sensitivity
   * matters, as opposed to Windows and the Mac OS default where
   * case sensitivity is preserved but ignored.
   * <P>
   * It is strongly recommended that libraries use this method to open
   * data files, so that the loading sequence is handled in the same way
   * as functions like loadBytes(), loadImage(), etc.
   * <P>
   * The filename passed in can be:
   * <UL>
   * <LI>A URL, for instance openStream("http://processing.org/");
   * <LI>A file in the sketch's data folder
   * <LI>Another file to be opened locally (when running as an application)
   * </UL>
   */
  public InputStream createInput(String filename) {
    InputStream input = createInputRaw(filename);
    final String lower = filename.toLowerCase();
    if ((input != null) &&
        (lower.endsWith(".gz") || lower.endsWith(".svgz"))) {
      try {
        // buffered has to go *around* the GZ, otherwise 25x slower
        return new BufferedInputStream(new GZIPInputStream(input));
      } catch (IOException e) {
        printStackTrace(e);
        return null;
      }
    }
    return new BufferedInputStream(input);
  }


  /**
   * Call createInput() without automatic gzip decompression.
   */
  public InputStream createInputRaw(String filename) {
    // Additional considerations for Android version:
    // http://developer.android.com/guide/topics/resources/resources-i18n.html
    InputStream stream = null;

    if (filename == null) return null;

    if (filename.length() == 0) {
      // an error will be called by the parent function
      //System.err.println("The filename passed to openStream() was empty.");
      return null;
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
    	URL url = new URL(filename);
    	HttpURLConnection con = (HttpURLConnection) url.openConnection();
    	con.setRequestMethod("GET");
    	con.setDoInput(true);
    	con.connect();
    	return con.getInputStream();
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

      } catch (MalformedURLException mfue) {
        // not a url, that's fine

      } catch (FileNotFoundException fnfe) {
        // Java 1.5 likes to throw this when URL not available. (fix for 0119)
        // http://dev.processing.org/bugs/show_bug.cgi?id=403

      } catch (IOException e) {
        // changed for 0117, shouldn't be throwing exception
        printStackTrace(e);
        //System.err.println("Error downloading from URL " + filename);
        return null;
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
    AssetManager assets = surface.getAssets();
    try {
      stream = assets.open(filename);
      if (stream != null) {
        return stream;
      }
    } catch (IOException e) {
      // ignore this and move on
      //e.printStackTrace();
    }

    // Maybe this is an absolute path, didja ever think of that?
    File absFile = new File(filename);
    if (absFile.exists()) {
      try {
        stream = new FileInputStream(absFile);
        if (stream != null) {
          return stream;
        }
      } catch (FileNotFoundException fnfe) {
        //fnfe.printStackTrace();
      }
    }

    // Maybe this is a file that was written by the sketch on another occasion.
    File sketchFile = new File(sketchPath(filename));
    if (sketchFile.exists()) {
      try {
        stream = new FileInputStream(sketchFile);
        if (stream != null) {
          return stream;
        }
      } catch (FileNotFoundException fnfe) {
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

    return surface.openFileInput(filename);
  }


  static public InputStream createInput(File file) {
    if (file == null) {
      throw new IllegalArgumentException("File passed to createInput() was null");
    }
    try {
      InputStream input = new FileInputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        return new BufferedInputStream(new GZIPInputStream(input));
      }
      return new BufferedInputStream(input);

    } catch (IOException e) {
      System.err.println("Could not createInput() for " + file);
      e.printStackTrace();
      return null;
    }
  }


  public byte[] loadBytes(String filename) {
    InputStream is = createInput(filename);
    if (is != null) return loadBytes(is);

    System.err.println("The file \"" + filename + "\" " +
                       "is missing or inaccessible, make sure " +
                       "the URL is valid or that the file has been " +
                       "added to your sketch and is readable.");
    return null;
  }


  static public byte[] loadBytes(InputStream input) {
    try {
      BufferedInputStream bis = new BufferedInputStream(input);
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      int c = bis.read();
      while (c != -1) {
        out.write(c);
        c = bis.read();
      }
      return out.toByteArray();

    } catch (IOException e) {
      e.printStackTrace();
      //throw new RuntimeException("Couldn't load bytes from stream");
    }
    return null;
  }


  static public byte[] loadBytes(File file) {
    InputStream is = createInput(file);
    return loadBytes(is);
  }


  static public String[] loadStrings(File file) {
    InputStream is = createInput(file);
    if (is != null) return loadStrings(is);
    return null;
  }


  static public String[] loadStrings(BufferedReader reader) {
    try {
      String lines[] = new String[100];
      int lineCount = 0;
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (lineCount == lines.length) {
          String temp[] = new String[lineCount << 1];
          System.arraycopy(lines, 0, temp, 0, lineCount);
          lines = temp;
        }
        lines[lineCount++] = line;
      }
      reader.close();

      if (lineCount == lines.length) {
        return lines;
      }

      // resize array to appropriate amount for these lines
      String output[] = new String[lineCount];
      System.arraycopy(lines, 0, output, 0, lineCount);
      return output;

    } catch (IOException e) {
      e.printStackTrace();
      //throw new RuntimeException("Error inside loadStrings()");
    }
    return null;
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
   */
  public String[] loadStrings(String filename) {
    InputStream is = createInput(filename);
    if (is != null) return loadStrings(is);

    System.err.println("The file \"" + filename + "\" " +
                       "is missing or inaccessible, make sure " +
                       "the URL is valid or that the file has been " +
                       "added to your sketch and is readable.");
    return null;
  }


  static public String[] loadStrings(InputStream input) {
    try {
      BufferedReader reader =
        new BufferedReader(new InputStreamReader(input, "UTF-8"));

      String lines[] = new String[100];
      int lineCount = 0;
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (lineCount == lines.length) {
          String temp[] = new String[lineCount << 1];
          System.arraycopy(lines, 0, temp, 0, lineCount);
          lines = temp;
        }
        lines[lineCount++] = line;
      }
      reader.close();

      if (lineCount == lines.length) {
        return lines;
      }

      // resize array to appropriate amount for these lines
      String output[] = new String[lineCount];
      System.arraycopy(lines, 0, output, 0, lineCount);
      return output;

    } catch (IOException e) {
      e.printStackTrace();
      //throw new RuntimeException("Error inside loadStrings()");
    }
    return null;
  }



  //////////////////////////////////////////////////////////////

  // FILE OUTPUT


  /**
   * Similar to createInput() (formerly openStream), this creates a Java
   * OutputStream for a given filename or path. The file will be created in
   * the sketch folder, or in the same folder as an exported application.
   * <p/>
   * If the path does not exist, intermediate folders will be created. If an
   * exception occurs, it will be printed to the console, and null will be
   * returned.
   * <p/>
   * Future releases may also add support for handling HTTP POST via this
   * method (for better symmetry with createInput), however that's maybe a
   * little too clever (and then we'd have to add the same features to the
   * other file functions like createWriter). Who you callin' bloated?
   */
  public OutputStream createOutput(String filename) {
    try {
      // in spite of appearing to be the 'correct' option, this doesn't allow
      // for paths, so no subfolders, none of that savePath() goodness.
//      Context context = getApplicationContext();
//      // MODE_PRIVATE is default, should we use that instead?
//      return context.openFileOutput(filename, MODE_WORLD_READABLE);

      File file = new File(filename);
      if (!file.isAbsolute()) {
        file = new File(sketchPath(filename));
      }
      FileOutputStream fos = new FileOutputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        return new GZIPOutputStream(fos);
      }
      return fos;

    } catch (IOException e) {
      printStackTrace(e);
    }
    return null;
  }


  static public OutputStream createOutput(File file) {
    try {
      createPath(file);  // make sure the path exists
      OutputStream output = new FileOutputStream(file);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        return new BufferedOutputStream(new GZIPOutputStream(output));
      }
      return new BufferedOutputStream(output);

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }


  /**
   * Save the contents of a stream to a file in the sketch folder.
   * This is basically saveBytes(blah, loadBytes()), but done
   * more efficiently (and with less confusing syntax).
   */
  public boolean saveStream(String targetFilename, String sourceLocation) {
    return saveStream(saveFile(targetFilename), sourceLocation);
  }


  /**
   * Identical to the other saveStream(), but writes to a File
   * object, for greater control over the file location.
   * Note that unlike other api methods, this will not automatically
   * compress or uncompress gzip files.
   */
  public boolean saveStream(File targetFile, String sourceLocation) {
    return saveStream(targetFile, createInputRaw(sourceLocation));
  }


  public boolean saveStream(String targetFilename, InputStream sourceStream) {
    return saveStream(saveFile(targetFilename), sourceStream);
  }


  static public boolean saveStream(File target, InputStream source) {
    File tempFile = null;
    try {
      // make sure that this path actually exists before writing
      createPath(target);
      tempFile = createTempFile(target);
      FileOutputStream targetStream = new FileOutputStream(tempFile);

      saveStream(targetStream, source);
      targetStream.close();
      targetStream = null;

      if (target.exists()) {
        if (!target.delete()) {
          System.err.println("Could not replace " +
                             target.getAbsolutePath() + ".");
        }
      }
      if (!tempFile.renameTo(target)) {
        System.err.println("Could not rename temporary file " +
                           tempFile.getAbsolutePath());
        return false;
      }
      return true;

    } catch (IOException e) {
      if (tempFile != null) {
        tempFile.delete();
      }
      e.printStackTrace();
      return false;
    }
  }


  static public void saveStream(OutputStream target,
                                InputStream source) throws IOException {
    BufferedInputStream bis = new BufferedInputStream(source, 16384);
    BufferedOutputStream bos = new BufferedOutputStream(target);

    byte[] buffer = new byte[8192];
    int bytesRead;
    while ((bytesRead = bis.read(buffer)) != -1) {
      bos.write(buffer, 0, bytesRead);
    }

    bos.flush();
  }


  /**
   * Saves bytes to a file to inside the sketch folder.
   * The filename can be a relative path, i.e. "poo/bytefun.txt"
   * would save to a file named "bytefun.txt" to a subfolder
   * called 'poo' inside the sketch folder. If the in-between
   * subfolders don't exist, they'll be created.
   */
  public void saveBytes(String filename, byte[] data) {
    saveBytes(saveFile(filename), data);
  }


  /**
   * Creates a temporary file based on the name/extension of another file
   * and in the same parent directory. Ensures that the same extension is used
   * (i.e. so that .gz files are gzip compressed on output) and that it's done
   * from the same directory so that renaming the file later won't cross file
   * system boundaries.
   */
  static private File createTempFile(File file) throws IOException {
    File parentDir = file.getParentFile();
    String name = file.getName();
    String prefix;
    String suffix = null;
    int dot = name.lastIndexOf('.');
    if (dot == -1) {
      prefix = name;
    } else {
      // preserve the extension so that .gz works properly
      prefix = name.substring(0, dot);
      suffix = name.substring(dot);
    }
    // Prefix must be three characters
    if (prefix.length() < 3) {
      prefix += "processing";
    }
    return File.createTempFile(prefix, suffix, parentDir);
  }


  /**
   * Saves bytes to a specific File location specified by the user.
   */
  static public void saveBytes(File file, byte[] data) {
    File tempFile = null;
    try {
      tempFile = createTempFile(file);

      OutputStream output = createOutput(tempFile);
      saveBytes(output, data);
      output.close();
      output = null;

      if (file.exists()) {
        if (!file.delete()) {
          System.err.println("Could not replace " + file.getAbsolutePath());
        }
      }

      if (!tempFile.renameTo(file)) {
        System.err.println("Could not rename temporary file " +
                           tempFile.getAbsolutePath());
      }

    } catch (IOException e) {
      System.err.println("error saving bytes to " + file);
      if (tempFile != null) {
        tempFile.delete();
      }
      e.printStackTrace();
    }
  }


  /**
   * Spews a buffer of bytes to an OutputStream.
   */
  static public void saveBytes(OutputStream output, byte[] data) {
    try {
      output.write(data);
      output.flush();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //

  public void saveStrings(String filename, String strings[]) {
    saveStrings(saveFile(filename), strings);
  }


  static public void saveStrings(File file, String strings[]) {
    try {
      String location = file.getAbsolutePath();
      createPath(location);
      OutputStream output = new FileOutputStream(location);
      if (file.getName().toLowerCase().endsWith(".gz")) {
        output = new GZIPOutputStream(output);
      }
      saveStrings(output, strings);
      output.close();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  static public void saveStrings(OutputStream output, String strings[]) {
    try {
      OutputStreamWriter osw = new OutputStreamWriter(output, "UTF-8");
      PrintWriter writer = new PrintWriter(osw);
      for (int i = 0; i < strings.length; i++) {
        writer.println(strings[i]);
      }
      writer.flush();
    } catch (UnsupportedEncodingException e) { }  // will not happen
  }


  //////////////////////////////////////////////////////////////


  /**
   * Prepend the sketch folder path to the filename (or path) that is
   * passed in. External libraries should use this function to save to
   * the sketch folder.
   * <p/>
   * Note that when running as an applet inside a web browser,
   * the sketchPath will be set to null, because security restrictions
   * prevent applets from accessing that information.
   * <p/>
   * This will also cause an error if the sketch is not inited properly,
   * meaning that init() was never called on the PApplet when hosted
   * my some other main() or by other code. For proper use of init(),
   * see the examples in the main description text for PApplet.
   */
  public String sketchPath(String where) {
    if (sketchPath == null) {
      return where;
//      throw new RuntimeException("The applet was not inited properly, " +
//                                 "or security restrictions prevented " +
//                                 "it from determining its path.");
    }

    // isAbsolute() could throw an access exception, but so will writing
    // to the local disk using the sketch path, so this is safe here.
    // for 0120, added a try/catch anyways.
    try {
      if (new File(where).isAbsolute()) return where;
    } catch (Exception e) { }

    return surface.getFileStreamPath(where).getAbsolutePath();
  }


  public File sketchFile(String where) {
    return new File(sketchPath(where));
  }


  /**
   * Returns a path inside the applet folder to save to. Like sketchPath(),
   * but creates any in-between folders so that things save properly.
   * <p/>
   * All saveXxxx() functions use the path to the sketch folder, rather than
   * its data folder. Once exported, the data folder will be found inside the
   * jar file of the exported application or applet. In this case, it's not
   * possible to save data into the jar file, because it will often be running
   * from a server, or marked in-use if running from a local file system.
   * With this in mind, saving to the data path doesn't make sense anyway.
   * If you know you're running locally, and want to save to the data folder,
   * use <TT>saveXxxx("data/blah.dat")</TT>.
   */
  public String savePath(String where) {
    if (where == null) return null;
//    System.out.println("filename before sketchpath is " + where);
    String filename = sketchPath(where);
//    System.out.println("filename after sketchpath is " + filename);
    createPath(filename);
    return filename;
  }


  /**
   * Identical to savePath(), but returns a File object.
   */
  public File saveFile(String where) {
    return new File(savePath(where));
  }


  /**
   * Return a full path to an item in the data folder.
   * <p>
   * The behavior of this function differs from the equivalent on the Java mode: files stored in
   * the data folder of the sketch get packed as assets in the apk, and the path to the data folder
   * is no longer valid. Only the name is needed to open them. However, if the file is not an asset,
   * we can assume it has been created by the sketch, so it should have the sketch path.
   * Discussed here:
   * https://github.com/processing/processing-android/issues/450
   */
  public String dataPath(String where) {
    // First, we check if it is asset:
    boolean isAsset = false;
    AssetManager assets = surface.getAssets();
    InputStream is = null;
    try {
      is = assets.open(where);
      isAsset = true;
    } catch (IOException ex) {
      //file does not exist
    } finally {
      try {
        is.close();
      } catch (Exception ex) { }
    }
    if (isAsset) return where;
    // Not an asset, let's just use sketch path:
    return sketchPath(where);
  }


  /**
   * Return a full path to an item in the data folder as a File object.
   * See the dataPath() method for more information.
   */
  public File dataFile(String where) {
    return new File(dataPath(where));
  }


  /**
   * Takes a path and creates any in-between folders if they don't
   * already exist. Useful when trying to save to a subfolder that
   * may not actually exist.
   */
  static public void createPath(String path) {
    createPath(new File(path));
  }


  static public void createPath(File file) {
    try {
      String parent = file.getParent();
      if (parent != null) {
        File unit = new File(parent);
        if (!unit.exists()) unit.mkdirs();
      }
    } catch (SecurityException se) {
      System.err.println("You don't have permissions to create " + file.getAbsolutePath());
    }
  }


  static public String getExtension(String filename) {
    String extension;

    String lower = filename.toLowerCase();
    int dot = filename.lastIndexOf('.');
    if (dot == -1) {
      extension = "unknown";  // no extension found
    }
    extension = lower.substring(dot + 1);

    // check for, and strip any parameters on the url, i.e.
    // filename.jpg?blah=blah&something=that
    int question = extension.indexOf('?');
    if (question != -1) {
      extension = extension.substring(0, question);
    }

    return extension;
  }


  //////////////////////////////////////////////////////////////

  // URL ENCODING

  static public String urlEncode(String what) {
    try {
      return URLEncoder.encode(what, "UTF-8");
    } catch (UnsupportedEncodingException e) {  // oh c'mon
      return null;
    }
  }


  static public String urlDecode(String what) {
    try {
      return URLDecoder.decode(what, "UTF-8");
    } catch (UnsupportedEncodingException e) {  // safe per the JDK source
      return null;
    }
  }



  //////////////////////////////////////////////////////////////

  // SORT


  static public byte[] sort(byte what[]) {
    return sort(what, what.length);
  }


  static public byte[] sort(byte[] what, int count) {
    byte[] outgoing = new byte[what.length];
    System.arraycopy(what, 0, outgoing, 0, what.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }


  static public char[] sort(char what[]) {
    return sort(what, what.length);
  }


  static public char[] sort(char[] what, int count) {
    char[] outgoing = new char[what.length];
    System.arraycopy(what, 0, outgoing, 0, what.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }


  static public int[] sort(int what[]) {
    return sort(what, what.length);
  }


  static public int[] sort(int[] what, int count) {
    int[] outgoing = new int[what.length];
    System.arraycopy(what, 0, outgoing, 0, what.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }


  static public float[] sort(float what[]) {
    return sort(what, what.length);
  }


  static public float[] sort(float[] what, int count) {
    float[] outgoing = new float[what.length];
    System.arraycopy(what, 0, outgoing, 0, what.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }


  static public String[] sort(String what[]) {
    return sort(what, what.length);
  }


  static public String[] sort(String[] what, int count) {
    String[] outgoing = new String[what.length];
    System.arraycopy(what, 0, outgoing, 0, what.length);
    Arrays.sort(outgoing, 0, count);
    return outgoing;
  }



  //////////////////////////////////////////////////////////////

  // ARRAY UTILITIES


  /**
   * Calls System.arraycopy(), included here so that we can
   * avoid people needing to learn about the System object
   * before they can just copy an array.
   */
  static public void arrayCopy(Object src, int srcPosition,
                               Object dst, int dstPosition,
                               int length) {
    System.arraycopy(src, srcPosition, dst, dstPosition, length);
  }


  /**
   * Convenience method for arraycopy().
   * Identical to <CODE>arraycopy(src, 0, dst, 0, length);</CODE>
   */
  static public void arrayCopy(Object src, Object dst, int length) {
    System.arraycopy(src, 0, dst, 0, length);
  }


  /**
   * Shortcut to copy the entire contents of
   * the source into the destination array.
   * Identical to <CODE>arraycopy(src, 0, dst, 0, src.length);</CODE>
   */
  static public void arrayCopy(Object src, Object dst) {
    System.arraycopy(src, 0, dst, 0, Array.getLength(src));
  }

  //

  static public boolean[] expand(boolean list[]) {
    return expand(list, list.length << 1);
  }

  static public boolean[] expand(boolean list[], int newSize) {
    boolean temp[] = new boolean[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }


  static public byte[] expand(byte list[]) {
    return expand(list, list.length << 1);
  }

  static public byte[] expand(byte list[], int newSize) {
    byte temp[] = new byte[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }


  static public char[] expand(char list[]) {
    return expand(list, list.length << 1);
  }

  static public char[] expand(char list[], int newSize) {
    char temp[] = new char[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }


  static public int[] expand(int list[]) {
    return expand(list, list.length << 1);
  }

  static public int[] expand(int list[], int newSize) {
    int temp[] = new int[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public long[] expand(long list[]) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public long[] expand(long list[], int newSize) {
    long temp[] = new long[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public float[] expand(float list[]) {
    return expand(list, list.length << 1);
  }

  static public float[] expand(float list[], int newSize) {
    float temp[] = new float[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public double[] expand(double list[]) {
    return expand(list, list.length > 0 ? list.length << 1 : 1);
  }

  static public double[] expand(double list[], int newSize) {
    double temp[] = new double[newSize];
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }

  static public String[] expand(String list[]) {
    return expand(list, list.length << 1);
  }

  static public String[] expand(String list[], int newSize) {
    String temp[] = new String[newSize];
    // in case the new size is smaller than list.length
    System.arraycopy(list, 0, temp, 0, Math.min(newSize, list.length));
    return temp;
  }


  static public Object expand(Object array) {
    return expand(array, Array.getLength(array) << 1);
  }

  static public Object expand(Object list, int newSize) {
    Class<?> type = list.getClass().getComponentType();
    Object temp = Array.newInstance(type, newSize);
    System.arraycopy(list, 0, temp, 0,
                     Math.min(Array.getLength(list), newSize));
    return temp;
  }

  //

  // contract() has been removed in revision 0124, use subset() instead.
  // (expand() is also functionally equivalent)

  //

  static public byte[] append(byte b[], byte value) {
    b = expand(b, b.length + 1);
    b[b.length-1] = value;
    return b;
  }

  static public char[] append(char b[], char value) {
    b = expand(b, b.length + 1);
    b[b.length-1] = value;
    return b;
  }

  static public int[] append(int b[], int value) {
    b = expand(b, b.length + 1);
    b[b.length-1] = value;
    return b;
  }

  static public float[] append(float b[], float value) {
    b = expand(b, b.length + 1);
    b[b.length-1] = value;
    return b;
  }

  static public String[] append(String b[], String value) {
    b = expand(b, b.length + 1);
    b[b.length-1] = value;
    return b;
  }

  static public Object append(Object b, Object value) {
    int length = Array.getLength(b);
    b = expand(b, length + 1);
    Array.set(b, length, value);
    return b;
  }

  //

  static public boolean[] shorten(boolean list[]) {
    return subset(list, 0, list.length-1);
  }

  static public byte[] shorten(byte list[]) {
    return subset(list, 0, list.length-1);
  }

  static public char[] shorten(char list[]) {
    return subset(list, 0, list.length-1);
  }

  static public int[] shorten(int list[]) {
    return subset(list, 0, list.length-1);
  }

  static public float[] shorten(float list[]) {
    return subset(list, 0, list.length-1);
  }

  static public String[] shorten(String list[]) {
    return subset(list, 0, list.length-1);
  }

  static public Object shorten(Object list) {
    int length = Array.getLength(list);
    return subset(list, 0, length - 1);
  }

  //

  static final public boolean[] splice(boolean list[],
                                       boolean v, int index) {
    boolean outgoing[] = new boolean[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = v;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public boolean[] splice(boolean list[],
                                       boolean v[], int index) {
    boolean outgoing[] = new boolean[list.length + v.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(v, 0, outgoing, index, v.length);
    System.arraycopy(list, index, outgoing, index + v.length,
                     list.length - index);
    return outgoing;
  }


  static final public byte[] splice(byte list[],
                                    byte v, int index) {
    byte outgoing[] = new byte[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = v;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public byte[] splice(byte list[],
                                    byte v[], int index) {
    byte outgoing[] = new byte[list.length + v.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(v, 0, outgoing, index, v.length);
    System.arraycopy(list, index, outgoing, index + v.length,
                     list.length - index);
    return outgoing;
  }


  static final public char[] splice(char list[],
                                    char v, int index) {
    char outgoing[] = new char[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = v;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public char[] splice(char list[],
                                    char v[], int index) {
    char outgoing[] = new char[list.length + v.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(v, 0, outgoing, index, v.length);
    System.arraycopy(list, index, outgoing, index + v.length,
                     list.length - index);
    return outgoing;
  }


  static final public int[] splice(int list[],
                                   int v, int index) {
    int outgoing[] = new int[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = v;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public int[] splice(int list[],
                                   int v[], int index) {
    int outgoing[] = new int[list.length + v.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(v, 0, outgoing, index, v.length);
    System.arraycopy(list, index, outgoing, index + v.length,
                     list.length - index);
    return outgoing;
  }


  static final public float[] splice(float list[],
                                     float v, int index) {
    float outgoing[] = new float[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = v;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public float[] splice(float list[],
                                     float v[], int index) {
    float outgoing[] = new float[list.length + v.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(v, 0, outgoing, index, v.length);
    System.arraycopy(list, index, outgoing, index + v.length,
                     list.length - index);
    return outgoing;
  }


  static final public String[] splice(String list[],
                                      String v, int index) {
    String outgoing[] = new String[list.length + 1];
    System.arraycopy(list, 0, outgoing, 0, index);
    outgoing[index] = v;
    System.arraycopy(list, index, outgoing, index + 1,
                     list.length - index);
    return outgoing;
  }

  static final public String[] splice(String list[],
                                      String v[], int index) {
    String outgoing[] = new String[list.length + v.length];
    System.arraycopy(list, 0, outgoing, 0, index);
    System.arraycopy(v, 0, outgoing, index, v.length);
    System.arraycopy(list, index, outgoing, index + v.length,
                     list.length - index);
    return outgoing;
  }


  static final public Object splice(Object list, Object v, int index) {
    Object[] outgoing = null;
    int length = Array.getLength(list);

    // check whether item being spliced in is an array
    if (v.getClass().getName().charAt(0) == '[') {
      int vlength = Array.getLength(v);
      outgoing = new Object[length + vlength];
      System.arraycopy(list, 0, outgoing, 0, index);
      System.arraycopy(v, 0, outgoing, index, vlength);
      System.arraycopy(list, index, outgoing, index + vlength, length - index);

    } else {
      outgoing = new Object[length + 1];
      System.arraycopy(list, 0, outgoing, 0, index);
      Array.set(outgoing, index, v);
      System.arraycopy(list, index, outgoing, index + 1, length - index);
    }
    return outgoing;
  }

  //

  static public boolean[] subset(boolean list[], int start) {
    return subset(list, start, list.length - start);
  }

  static public boolean[] subset(boolean list[], int start, int count) {
    boolean output[] = new boolean[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public byte[] subset(byte list[], int start) {
    return subset(list, start, list.length - start);
  }

  static public byte[] subset(byte list[], int start, int count) {
    byte output[] = new byte[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public char[] subset(char list[], int start) {
    return subset(list, start, list.length - start);
  }

  static public char[] subset(char list[], int start, int count) {
    char output[] = new char[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public int[] subset(int list[], int start) {
    return subset(list, start, list.length - start);
  }

  static public int[] subset(int list[], int start, int count) {
    int output[] = new int[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }

  static public long[] subset(long[] list, int start) {
    return subset(list, start, list.length - start);
  }

  static public long[] subset(long[] list, int start, int count) {
    long[] output = new long[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }

  static public float[] subset(float list[], int start) {
    return subset(list, start, list.length - start);
  }

  static public float[] subset(float list[], int start, int count) {
    float output[] = new float[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }

  static public double[] subset(double[] list, int start) {
    return subset(list, start, list.length - start);
  }

  static public double[] subset(double[] list, int start, int count) {
    double[] output = new double[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }

  static public String[] subset(String list[], int start) {
    return subset(list, start, list.length - start);
  }

  static public String[] subset(String list[], int start, int count) {
    String output[] = new String[count];
    System.arraycopy(list, start, output, 0, count);
    return output;
  }


  static public Object subset(Object list, int start) {
    int length = Array.getLength(list);
    return subset(list, start, length - start);
  }

  static public Object subset(Object list, int start, int count) {
    Class<?> type = list.getClass().getComponentType();
    Object outgoing = Array.newInstance(type, count);
    System.arraycopy(list, start, outgoing, 0, count);
    return outgoing;
  }

  //

  static public boolean[] concat(boolean a[], boolean b[]) {
    boolean c[] = new boolean[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public byte[] concat(byte a[], byte b[]) {
    byte c[] = new byte[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public char[] concat(char a[], char b[]) {
    char c[] = new char[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public int[] concat(int a[], int b[]) {
    int c[] = new int[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public float[] concat(float a[], float b[]) {
    float c[] = new float[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public String[] concat(String a[], String b[]) {
    String c[] = new String[a.length + b.length];
    System.arraycopy(a, 0, c, 0, a.length);
    System.arraycopy(b, 0, c, a.length, b.length);
    return c;
  }

  static public Object concat(Object a, Object b) {
    Class<?> type = a.getClass().getComponentType();
    int alength = Array.getLength(a);
    int blength = Array.getLength(b);
    Object outgoing = Array.newInstance(type, alength + blength);
    System.arraycopy(a, 0, outgoing, 0, alength);
    System.arraycopy(b, 0, outgoing, alength, blength);
    return outgoing;
  }

  //

  static public boolean[] reverse(boolean list[]) {
    boolean outgoing[] = new boolean[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public byte[] reverse(byte list[]) {
    byte outgoing[] = new byte[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public char[] reverse(char list[]) {
    char outgoing[] = new char[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public int[] reverse(int list[]) {
    int outgoing[] = new int[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public float[] reverse(float list[]) {
    float outgoing[] = new float[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public String[] reverse(String list[]) {
    String outgoing[] = new String[list.length];
    int length1 = list.length - 1;
    for (int i = 0; i < list.length; i++) {
      outgoing[i] = list[length1 - i];
    }
    return outgoing;
  }

  static public Object reverse(Object list) {
    Class<?> type = list.getClass().getComponentType();
    int length = Array.getLength(list);
    Object outgoing = Array.newInstance(type, length);
    for (int i = 0; i < length; i++) {
      Array.set(outgoing, i, Array.get(list, (length - 1) - i));
    }
    return outgoing;
  }



  //////////////////////////////////////////////////////////////

  // STRINGS


  /**
   * Remove whitespace characters from the beginning and ending
   * of a String. Works like String.trim() but includes the
   * unicode nbsp character as well.
   */
  static public String trim(String str) {
    return str.replace('\u00A0', ' ').trim();
  }


  /**
   * Trim the whitespace from a String array. This returns a new
   * array and does not affect the passed-in array.
   */
  static public String[] trim(String[] array) {
    String[] outgoing = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      if (array[i] != null) {
        outgoing[i] = array[i].replace('\u00A0', ' ').trim();
      }
    }
    return outgoing;
  }


  /**
   * Join an array of Strings together as a single String,
   * separated by the whatever's passed in for the separator.
   */
  static public String join(String str[], char separator) {
    return join(str, String.valueOf(separator));
  }


  /**
   * Join an array of Strings together as a single String,
   * separated by the whatever's passed in for the separator.
   * <P>
   * To use this on numbers, first pass the array to nf() or nfs()
   * to get a list of String objects, then use join on that.
   * <PRE>
   * e.g. String stuff[] = { "apple", "bear", "cat" };
   *      String list = join(stuff, ", ");
   *      // list is now "apple, bear, cat"</PRE>
   */
  static public String join(String str[], String separator) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < str.length; i++) {
      if (i != 0) buffer.append(separator);
      buffer.append(str[i]);
    }
    return buffer.toString();
  }


  /**
   * Split the provided String at wherever whitespace occurs.
   * Multiple whitespace (extra spaces or tabs or whatever)
   * between items will count as a single break.
   * <P>
   * The whitespace characters are "\t\n\r\f", which are the defaults
   * for java.util.StringTokenizer, plus the unicode non-breaking space
   * character, which is found commonly on files created by or used
   * in conjunction with Mac OS X (character 160, or 0x00A0 in hex).
   * <PRE>
   * i.e. splitTokens("a b") -> { "a", "b" }
   *      splitTokens("a    b") -> { "a", "b" }
   *      splitTokens("a\tb") -> { "a", "b" }
   *      splitTokens("a \t  b  ") -> { "a", "b" }</PRE>
   */
  static public String[] splitTokens(String what) {
    return splitTokens(what, WHITESPACE);
  }


  /**
   * Splits a string into pieces, using any of the chars in the
   * String 'delim' as separator characters. For instance,
   * in addition to white space, you might want to treat commas
   * as a separator. The delimeter characters won't appear in
   * the returned String array.
   * <PRE>
   * i.e. splitTokens("a, b", " ,") -> { "a", "b" }
   * </PRE>
   * To include all the whitespace possibilities, use the variable
   * WHITESPACE, found in PConstants:
   * <PRE>
   * i.e. splitTokens("a   | b", WHITESPACE + "|");  ->  { "a", "b" }</PRE>
   */
  static public String[] splitTokens(String what, String delim) {
    StringTokenizer toker = new StringTokenizer(what, delim);
    String pieces[] = new String[toker.countTokens()];

    int index = 0;
    while (toker.hasMoreTokens()) {
      pieces[index++] = toker.nextToken();
    }
    return pieces;
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
   */
  static public String[] split(String what, char delim) {
    // do this so that the exception occurs inside the user's
    // program, rather than appearing to be a bug inside split()
    if (what == null) return null;
    //return split(what, String.valueOf(delim));  // huh

    char chars[] = what.toCharArray();
    int splitCount = 0; //1;
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == delim) splitCount++;
    }
    // make sure that there is something in the input string
    //if (chars.length > 0) {
      // if the last char is a delimeter, get rid of it..
      //if (chars[chars.length-1] == delim) splitCount--;
      // on second thought, i don't agree with this, will disable
    //}
    if (splitCount == 0) {
      String splits[] = new String[1];
      splits[0] = new String(what);
      return splits;
    }
    //int pieceCount = splitCount + 1;
    String splits[] = new String[splitCount + 1];
    int splitIndex = 0;
    int startIndex = 0;
    for (int i = 0; i < chars.length; i++) {
      if (chars[i] == delim) {
        splits[splitIndex++] =
          new String(chars, startIndex, i-startIndex);
        startIndex = i + 1;
      }
    }
    //if (startIndex != chars.length) {
      splits[splitIndex] =
        new String(chars, startIndex, chars.length-startIndex);
    //}
    return splits;
  }


  /**
   * Split a String on a specific delimiter. Unlike Java's String.split()
   * method, this does not parse the delimiter as a regexp because it's more
   * confusing than necessary, and String.split() is always available for
   * those who want regexp.
   */
  static public String[] split(String what, String delim) {
    ArrayList<String> items = new ArrayList<String>();
    int index;
    int offset = 0;
    while ((index = what.indexOf(delim, offset)) != -1) {
      items.add(what.substring(offset, index));
      offset = index + delim.length();
    }
    items.add(what.substring(offset));
    String[] outgoing = new String[items.size()];
    items.toArray(outgoing);
    return outgoing;
  }


  static protected HashMap<String, Pattern> matchPatterns;

  static Pattern matchPattern(String regexp) {
    Pattern p = null;
    if (matchPatterns == null) {
      matchPatterns = new HashMap<String, Pattern>();
    } else {
      p = matchPatterns.get(regexp);
    }
    if (p == null) {
      if (matchPatterns.size() == 10) {
        // Just clear out the match patterns here if more than 10 are being
        // used. It's not terribly efficient, but changes that you have >10
        // different match patterns are very slim, unless you're doing
        // something really tricky (like custom match() methods), in which
        // case match() won't be efficient anyway. (And you should just be
        // using your own Java code.) The alternative is using a queue here,
        // but that's a silly amount of work for negligible benefit.
        matchPatterns.clear();
      }
      p = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
      matchPatterns.put(regexp, p);
    }
    return p;
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
  static public String[] match(String what, String regexp) {
    Pattern p = matchPattern(regexp);
    Matcher m = p.matcher(what);
    if (m.find()) {
      int count = m.groupCount() + 1;
      String[] groups = new String[count];
      for (int i = 0; i < count; i++) {
        groups[i] = m.group(i);
      }
      return groups;
    }
    return null;
  }


  /**
   * Identical to match(), except that it returns an array of all matches in
   * the specified String, rather than just the first.
   */
  static public String[][] matchAll(String what, String regexp) {
    Pattern p = matchPattern(regexp);
    Matcher m = p.matcher(what);
    ArrayList<String[]> results = new ArrayList<String[]>();
    int count = m.groupCount() + 1;
    while (m.find()) {
      String[] groups = new String[count];
      for (int i = 0; i < count; i++) {
        groups[i] = m.group(i);
      }
      results.add(groups);
    }
    if (results.isEmpty()) {
      return null;
    }
    String[][] matches = new String[results.size()][count];
    for (int i = 0; i < matches.length; i++) {
      matches[i] = (String[]) results.get(i);
    }
    return matches;
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
   * <p>Convert an integer to a boolean. Because of how Java handles upgrading
   * numbers, this will also cover byte and char (as they will upgrade to
   * an int without any sort of explicit cast).</p>
   * <p>The preprocessor will convert boolean(what) to parseBoolean(what).</p>
   * @return false if 0, true if any other number
   */
  static final public boolean parseBoolean(int what) {
    return (what != 0);
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
  static final public boolean parseBoolean(String what) {
    return new Boolean(what).booleanValue();
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
  static final public boolean[] parseBoolean(byte what[]) {
    boolean outgoing[] = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (what[i] != 0);
    }
    return outgoing;
  }

  /**
   * Convert an int array to a boolean array. An int equal
   * to zero will return false, and any other value will return true.
   * @return array of boolean elements
   */
  static final public boolean[] parseBoolean(int what[]) {
    boolean outgoing[] = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (what[i] != 0);
    }
    return outgoing;
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

  static final public boolean[] parseBoolean(String what[]) {
    boolean outgoing[] = new boolean[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = new Boolean(what[i]).booleanValue();
    }
    return outgoing;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public byte parseByte(boolean what) {
    return what ? (byte)1 : 0;
  }

  static final public byte parseByte(char what) {
    return (byte) what;
  }

  static final public byte parseByte(int what) {
    return (byte) what;
  }

  static final public byte parseByte(float what) {
    return (byte) what;
  }

  /*
  // nixed, no precedent
  static final public byte[] parseByte(String what) {  // note: array[]
    return what.getBytes();
  }
  */

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public byte[] parseByte(boolean what[]) {
    byte outgoing[] = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = what[i] ? (byte)1 : 0;
    }
    return outgoing;
  }

  static final public byte[] parseByte(char what[]) {
    byte outgoing[] = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (byte) what[i];
    }
    return outgoing;
  }

  static final public byte[] parseByte(int what[]) {
    byte outgoing[] = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (byte) what[i];
    }
    return outgoing;
  }

  static final public byte[] parseByte(float what[]) {
    byte outgoing[] = new byte[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (byte) what[i];
    }
    return outgoing;
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

  static final public char parseChar(byte what) {
    return (char) (what & 0xff);
  }

  static final public char parseChar(int what) {
    return (char) what;
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

  static final public char[] parseChar(byte what[]) {
    char outgoing[] = new char[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (char) (what[i] & 0xff);
    }
    return outgoing;
  }

  static final public char[] parseChar(int what[]) {
    char outgoing[] = new char[what.length];
    for (int i = 0; i < what.length; i++) {
      outgoing[i] = (char) what[i];
    }
    return outgoing;
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

  static final public int parseInt(boolean what) {
    return what ? 1 : 0;
  }

  /**
   * Note that parseInt() will un-sign a signed byte value.
   */
  static final public int parseInt(byte what) {
    return what & 0xff;
  }

  /**
   * Note that parseInt('5') is unlike String in the sense that it
   * won't return 5, but the ascii value. This is because ((int) someChar)
   * returns the ascii value, and parseInt() is just longhand for the cast.
   */
  static final public int parseInt(char what) {
    return what;
  }

  /**
   * Same as floor(), or an (int) cast.
   */
  static final public int parseInt(float what) {
    return (int) what;
  }

  /**
   * Parse a String into an int value. Returns 0 if the value is bad.
   */
  static final public int parseInt(String what) {
    return parseInt(what, 0);
  }

  /**
   * Parse a String to an int, and provide an alternate value that
   * should be used when the number is invalid.
   */
  static final public int parseInt(String what, int otherwise) {
    try {
      int offset = what.indexOf('.');
      if (offset == -1) {
        return Integer.parseInt(what);
      } else {
        return Integer.parseInt(what.substring(0, offset));
      }
    } catch (NumberFormatException e) { }
    return otherwise;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public int[] parseInt(boolean what[]) {
    int list[] = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      list[i] = what[i] ? 1 : 0;
    }
    return list;
  }

  static final public int[] parseInt(byte what[]) {  // note this unsigns
    int list[] = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      list[i] = (what[i] & 0xff);
    }
    return list;
  }

  static final public int[] parseInt(char what[]) {
    int list[] = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      list[i] = what[i];
    }
    return list;
  }

  static public int[] parseInt(float what[]) {
    int inties[] = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      inties[i] = (int)what[i];
    }
    return inties;
  }

  /**
   * Make an array of int elements from an array of String objects.
   * If the String can't be parsed as a number, it will be set to zero.
   *
   * String s[] = { "1", "300", "44" };
   * int numbers[] = parseInt(s);
   *
   * numbers will contain { 1, 300, 44 }
   */
  static public int[] parseInt(String what[]) {
    return parseInt(what, 0);
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
  static public int[] parseInt(String what[], int missing) {
    int output[] = new int[what.length];
    for (int i = 0; i < what.length; i++) {
      try {
        output[i] = Integer.parseInt(what[i]);
      } catch (NumberFormatException e) {
        output[i] = missing;
      }
    }
    return output;
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
  static final public float parseFloat(int what) {  // also handles byte
    return (float)what;
  }

  static final public float parseFloat(String what) {
    return parseFloat(what, Float.NaN);
  }

  static final public float parseFloat(String what, float otherwise) {
    try {
      return new Float(what).floatValue();
    } catch (NumberFormatException e) { }

    return otherwise;
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

  static final public float[] parseByte(byte what[]) {
    float floaties[] = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      floaties[i] = what[i];
    }
    return floaties;
  }

  static final public float[] parseFloat(int what[]) {
    float floaties[] = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      floaties[i] = what[i];
    }
    return floaties;
  }

  static final public float[] parseFloat(String what[]) {
    return parseFloat(what, Float.NaN);
  }

  static final public float[] parseFloat(String what[], float missing) {
    float output[] = new float[what.length];
    for (int i = 0; i < what.length; i++) {
      try {
        output[i] = new Float(what[i]).floatValue();
      } catch (NumberFormatException e) {
        output[i] = missing;
      }
    }
    return output;
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public String str(boolean x) {
    return String.valueOf(x);
  }

  static final public String str(byte x) {
    return String.valueOf(x);
  }

  static final public String str(char x) {
    return String.valueOf(x);
  }

  static final public String str(int x) {
    return String.valueOf(x);
  }

  static final public String str(float x) {
    return String.valueOf(x);
  }

  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  static final public String[] str(boolean x[]) {
    String s[] = new String[x.length];
    for (int i = 0; i < x.length; i++) s[i] = String.valueOf(x[i]);
    return s;
  }

  static final public String[] str(byte x[]) {
    String s[] = new String[x.length];
    for (int i = 0; i < x.length; i++) s[i] = String.valueOf(x[i]);
    return s;
  }

  static final public String[] str(char x[]) {
    String s[] = new String[x.length];
    for (int i = 0; i < x.length; i++) s[i] = String.valueOf(x[i]);
    return s;
  }

  static final public String[] str(int x[]) {
    String s[] = new String[x.length];
    for (int i = 0; i < x.length; i++) s[i] = String.valueOf(x[i]);
    return s;
  }

  static final public String[] str(float x[]) {
    String s[] = new String[x.length];
    for (int i = 0; i < x.length; i++) s[i] = String.valueOf(x[i]);
    return s;
  }


  //////////////////////////////////////////////////////////////

  // INT NUMBER FORMATTING


  /**
   * Integer number formatter.
   */
  static private NumberFormat int_nf;
  static private int int_nf_digits;
  static private boolean int_nf_commas;


  static public String[] nf(int num[], int digits) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nf(num[i], digits);
    }
    return formatted;
  }


  static public String nf(int num, int digits) {
    if ((int_nf != null) &&
        (int_nf_digits == digits) &&
        !int_nf_commas) {
      return int_nf.format(num);
    }

    int_nf = NumberFormat.getInstance();
    int_nf.setGroupingUsed(false); // no commas
    int_nf_commas = false;
    int_nf.setMinimumIntegerDigits(digits);
    int_nf_digits = digits;
    return int_nf.format(num);
  }


  static public String[] nfc(int num[]) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfc(num[i]);
    }
    return formatted;
  }


  static public String nfc(int num) {
    if ((int_nf != null) &&
        (int_nf_digits == 0) &&
        int_nf_commas) {
      return int_nf.format(num);
    }

    int_nf = NumberFormat.getInstance();
    int_nf.setGroupingUsed(true);
    int_nf_commas = true;
    int_nf.setMinimumIntegerDigits(0);
    int_nf_digits = 0;
    return int_nf.format(num);
  }


  /**
   * number format signed (or space)
   * Formats a number but leaves a blank space in the front
   * when it's positive so that it can be properly aligned with
   * numbers that have a negative sign in front of them.
   */
  static public String nfs(int num, int digits) {
    return (num < 0) ? nf(num, digits) : (' ' + nf(num, digits));
  }

  static public String[] nfs(int num[], int digits) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfs(num[i], digits);
    }
    return formatted;
  }

  //

  /**
   * number format positive (or plus)
   * Formats a number, always placing a - or + sign
   * in the front when it's negative or positive.
   */
  static public String nfp(int num, int digits) {
    return (num < 0) ? nf(num, digits) : ('+' + nf(num, digits));
  }

  static public String[] nfp(int num[], int digits) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfp(num[i], digits);
    }
    return formatted;
  }



  //////////////////////////////////////////////////////////////

  // FLOAT NUMBER FORMATTING


  static private NumberFormat float_nf;
  static private int float_nf_left, float_nf_right;
  static private boolean float_nf_commas;


  static public String[] nf(float num[], int left, int right) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nf(num[i], left, right);
    }
    return formatted;
  }


  static public String nf(float num, int left, int right) {
    if ((float_nf != null) &&
        (float_nf_left == left) &&
        (float_nf_right == right) &&
        !float_nf_commas) {
      return float_nf.format(num);
    }

    float_nf = NumberFormat.getInstance();
    float_nf.setGroupingUsed(false);
    float_nf_commas = false;

    if (left != 0) float_nf.setMinimumIntegerDigits(left);
    if (right != 0) {
      float_nf.setMinimumFractionDigits(right);
      float_nf.setMaximumFractionDigits(right);
    }
    float_nf_left = left;
    float_nf_right = right;
    return float_nf.format(num);
  }


  static public String[] nfc(float num[], int right) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfc(num[i], right);
    }
    return formatted;
  }


  static public String nfc(float num, int right) {
    if ((float_nf != null) &&
        (float_nf_left == 0) &&
        (float_nf_right == right) &&
        float_nf_commas) {
      return float_nf.format(num);
    }

    float_nf = NumberFormat.getInstance();
    float_nf.setGroupingUsed(true);
    float_nf_commas = true;

    if (right != 0) {
      float_nf.setMinimumFractionDigits(right);
      float_nf.setMaximumFractionDigits(right);
    }
    float_nf_left = 0;
    float_nf_right = right;
    return float_nf.format(num);
  }


  /**
   * Number formatter that takes into account whether the number
   * has a sign (positive, negative, etc) in front of it.
   */
  static public String[] nfs(float num[], int left, int right) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfs(num[i], left, right);
    }
    return formatted;
  }

  static public String nfs(float num, int left, int right) {
    return (num < 0) ? nf(num, left, right) :  (' ' + nf(num, left, right));
  }


  static public String[] nfp(float num[], int left, int right) {
    String formatted[] = new String[num.length];
    for (int i = 0; i < formatted.length; i++) {
      formatted[i] = nfp(num[i], left, right);
    }
    return formatted;
  }

  static public String nfp(float num, int left, int right) {
    return (num < 0) ? nf(num, left, right) :  ('+' + nf(num, left, right));
  }



  //////////////////////////////////////////////////////////////

  // HEX/BINARY CONVERSION


  /**
   * Convert a byte into a two digit hex string.
   */
  static final public String hex(byte what) {
    return hex(what, 2);
  }

  /**
   * Convert a Unicode character into a four digit hex string.
   */
  static final public String hex(char what) {
    return hex(what, 4);
  }

  /**
   * Convert an integer into an eight digit hex string.
   */
  static final public String hex(int what) {
    return hex(what, 8);
  }

  /**
   * Format an integer as a hex string using the specified number of digits.
   * @param what the value to format
   * @param digits the number of digits (maximum 8)
   * @return a String object with the formatted values
   */
  static final public String hex(int what, int digits) {
    String stuff = Integer.toHexString(what).toUpperCase();
    if (digits > 8) {
      digits = 8;
    }

    int length = stuff.length();
    if (length > digits) {
      return stuff.substring(length - digits);

    } else if (length < digits) {
      return "00000000".substring(8 - (digits-length)) + stuff;
    }
    return stuff;
  }

  static final public int unhex(String what) {
    // has to parse as a Long so that it'll work for numbers bigger than 2^31
    return (int) (Long.parseLong(what, 16));
  }

  //

  /**
   * Returns a String that contains the binary value of a byte.
   * The returned value will always have 8 digits.
   */
  static final public String binary(byte what) {
    return binary(what, 8);
  }

  /**
   * Returns a String that contains the binary value of a char.
   * The returned value will always have 16 digits because chars
   * are two bytes long.
   */
  static final public String binary(char what) {
    return binary(what, 16);
  }

  /**
   * Returns a String that contains the binary value of an int. The length
   * depends on the size of the number itself. If you want a specific number
   * of digits use binary(int what, int digits) to specify how many.
   */
  static final public String binary(int what) {
    return binary(what, 32);
  }

  /**
   * Returns a String that contains the binary value of an int.
   * The digits parameter determines how many digits will be used.
   */
  static final public String binary(int what, int digits) {
    String stuff = Integer.toBinaryString(what);
    if (digits > 32) {
      digits = 32;
    }

    int length = stuff.length();
    if (length > digits) {
      return stuff.substring(length - digits);

    } else if (length < digits) {
      int offset = 32 - (digits-length);
      return "00000000000000000000000000000000".substring(offset) + stuff;
    }
    return stuff;
  }


  /**
   * Unpack a binary String into an int.
   * i.e. unbinary("00001000") would return 8.
   */
  static final public int unbinary(String what) {
    return Integer.parseInt(what, 2);
  }



  //////////////////////////////////////////////////////////////

  // COLOR FUNCTIONS

  // moved here so that they can work without
  // the graphics actually being instantiated (outside setup)


  public final int color(int gray) {
    if (g == null) {
      if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
      return 0xff000000 | (gray << 16) | (gray << 8) | gray;
    }
    return g.color(gray);
  }


  public final int color(float fgray) {
    if (g == null) {
      int gray = (int) fgray;
      if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
      return 0xff000000 | (gray << 16) | (gray << 8) | gray;
    }
    return g.color(fgray);
  }


  /**
   * As of 0116 this also takes color(#FF8800, alpha)
   */
  public final int color(int gray, int alpha) {
    if (g == null) {
      if (alpha > 255) alpha = 255; else if (alpha < 0) alpha = 0;
      if (gray > 255) {
        // then assume this is actually a #FF8800
        return (alpha << 24) | (gray & 0xFFFFFF);
      } else {
        //if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
        return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
      }
    }
    return g.color(gray, alpha);
  }


  public final int color(float fgray, float falpha) {
    if (g == null) {
      int gray = (int) fgray;
      int alpha = (int) falpha;
      if (gray > 255) gray = 255; else if (gray < 0) gray = 0;
      if (alpha > 255) alpha = 255; else if (alpha < 0) alpha = 0;
      return 0xff000000 | (gray << 16) | (gray << 8) | gray;
    }
    return g.color(fgray, falpha);
  }


  public final int color(int x, int y, int z) {
    if (g == null) {
      if (x > 255) x = 255; else if (x < 0) x = 0;
      if (y > 255) y = 255; else if (y < 0) y = 0;
      if (z > 255) z = 255; else if (z < 0) z = 0;

      return 0xff000000 | (x << 16) | (y << 8) | z;
    }
    return g.color(x, y, z);
  }


  public final int color(float x, float y, float z) {
    if (g == null) {
      if (x > 255) x = 255; else if (x < 0) x = 0;
      if (y > 255) y = 255; else if (y < 0) y = 0;
      if (z > 255) z = 255; else if (z < 0) z = 0;

      return 0xff000000 | ((int)x << 16) | ((int)y << 8) | (int)z;
    }
    return g.color(x, y, z);
  }


  public final int color(int x, int y, int z, int a) {
    if (g == null) {
      if (a > 255) a = 255; else if (a < 0) a = 0;
      if (x > 255) x = 255; else if (x < 0) x = 0;
      if (y > 255) y = 255; else if (y < 0) y = 0;
      if (z > 255) z = 255; else if (z < 0) z = 0;

      return (a << 24) | (x << 16) | (y << 8) | z;
    }
    return g.color(x, y, z, a);
  }


  public final int color(float x, float y, float z, float a) {
    if (g == null) {
      if (a > 255) a = 255; else if (a < 0) a = 0;
      if (x > 255) x = 255; else if (x < 0) x = 0;
      if (y > 255) y = 255; else if (y < 0) y = 0;
      if (z > 255) z = 255; else if (z < 0) z = 0;

      return ((int)a << 24) | ((int)x << 16) | ((int)y << 8) | (int)z;
    }
    return g.color(x, y, z, a);
  }


  static public int blendColor(int c1, int c2, int mode) {
    return PImage.blendColor(c1, c2, mode);
  }



  //////////////////////////////////////////////////////////////

  // MAIN


  /**
   * Set this sketch to communicate its state back to the PDE.
   * <p/>
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
  static public final byte[] ICON_IMAGE = {
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
  };


  /**
   * main() method for running this class from the command line.
   * <P>
   * <B>The options shown here are not yet finalized and will be
   * changing over the next several releases.</B>
   * <P>
   * The simplest way to turn and applet into an application is to
   * add the following code to your program:
   * <PRE>static public void main(String args[]) {
   *   PApplet.main(new String[] { "YourSketchName" });
   * }</PRE>
   * This will properly launch your applet from a double-clickable
   * .jar or from the command line.
   * <PRE>
   * Parameters useful for launching or also used by the PDE:
   *
   * --location=x,y        upper-lefthand corner of where the applet
   *                       should appear on screen. if not used,
   *                       the default is to center on the main screen.
   *
   * --present             put the applet into full screen presentation
   *                       mode. requires java 1.4 or later.
   *
   * --exclusive           use full screen exclusive mode when presenting.
   *                       disables new windows or interaction with other
   *                       monitors, this is like a "game" mode.
   *
   * --hide-stop           use to hide the stop button in situations where
   *                       you don't want to allow users to exit. also
   *                       see the FAQ on information for capturing the ESC
   *                       key when running in presentation mode.
   *
   * --stop-color=#xxxxxx  color of the 'stop' text used to quit an
   *                       sketch when it's in present mode.
   *
   * --bgcolor=#xxxxxx     background color of the window.
   *
   * --sketch-path         location of where to save files from functions
   *                       like saveStrings() or saveFrame(). defaults to
   *                       the folder that the java application was
   *                       launched from, which means if this isn't set by
   *                       the pde, everything goes into the same folder
   *                       as processing.exe.
   *
   * --display=n           set what display should be used by this applet.
   *                       displays are numbered starting from 1.
   *
   * Parameters used by Processing when running via the PDE
   *
   * --external            set when the applet is being used by the PDE
   *
   * --editor-location=x,y position of the upper-lefthand corner of the
   *                       editor window, for placement of applet window
   * </PRE>
   */
  static public void main(String args[]) {
    // just do a no-op for now
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
  public void loadPixels() {
    g.loadPixels();
    pixels = g.pixels;
  }


  public void updatePixels() {
    g.updatePixels();
  }


  public void updatePixels(int x1, int y1, int x2, int y2) {
    g.updatePixels(x1, y1, x2, y2);
  }


  //////////////////////////////////////////////////////////////

  // ANDROID-SPECIFIC API


  // Wallpaper and wear API


  public boolean wallpaperPreview() {
    return surface.getEngine().isPreview();
  }


  public float wallpaperOffset() {
    return surface.getEngine().getXOffset();
  }


  public int wallpaperHomeCount() {
    float step = surface.getEngine().getXOffsetStep();
    if (0 < step) {
      return (int)(1 + 1 / step);
    } else {
      return 1;
    }
  }


  public boolean wearAmbient() {
    return surface.getEngine().isInAmbientMode();
  }


  public boolean wearInteractive() {
    return !surface.getEngine().isInAmbientMode();
  }


  public boolean wearRound() {
    return surface.getEngine().isRound();
  }


  public boolean wearSquare() {
    return !surface.getEngine().isRound();
  }


  public Rect wearInsets() {
    return surface.getEngine().getInsets();
  }


  public boolean wearLowBit() {
    return surface.getEngine().useLowBitAmbient();
  }


  public boolean wearBurnIn() {
    return surface.getEngine().requireBurnInProtection();
  }


  // Ray casting API


  public PVector[] getRayFromScreen(float screenX, float screenY, PVector[] ray) {
    return g.getRayFromScreen(screenX, screenY, ray);
  }


  public void getRayFromScreen(float screenX, float screenY, PVector origin, PVector direction) {
    g.getRayFromScreen(screenX, screenY, origin, direction);
  }


  public boolean intersectsSphere(float r, float screenX, float screenY) {
    return g.intersectsSphere(r, screenX, screenY);
  }


  public boolean intersectsSphere(float r, PVector origin, PVector direction) {
    return g.intersectsSphere(r, origin, direction);
  }


  public boolean intersectsBox(float w, float screenX, float screenY) {
    return g.intersectsBox(w, screenX, screenY);
  }


  public boolean intersectsBox(float w, float h, float d, float screenX, float screenY) {
    return g.intersectsBox(w, h, d, screenX, screenY);
  }


  public boolean intersectsBox(float size, PVector origin, PVector direction) {
    return g.intersectsBox(size, origin, direction);
  }


  public boolean intersectsBox(float w, float h, float d, PVector origin, PVector direction) {
    return g.intersectsBox(w, h, d, origin, direction);
  }


  public PVector intersectsPlane(float screenX, float screenY) {
    return g.intersectsPlane(screenX, screenY);
  }


  public PVector intersectsPlane(PVector origin, PVector direction) {
    return g.intersectsPlane(origin, direction);
  }


  public void eye() {
    g.eye();
  }


  public void calculate() {
  }
  

  /**
   * Sets the coordinate system in 3D centered at (width/2, height/2)
   * and with the Y axis pointing up.
   */

  public void cameraUp() {
    g.cameraUp();
  }


  /**
   * Returns a copy of the current object matrix.
   * Pass in null to create a new matrix.
   */
  public PMatrix3D getObjectMatrix() {
    return g.getObjectMatrix();
  }


  /**
   * Copy the current object matrix into the specified target.
   * Pass in null to create a new matrix.
   */
  public PMatrix3D getObjectMatrix(PMatrix3D target) {
    return g.getObjectMatrix(target);
  }


  /**
   * Returns a copy of the current eye matrix.
   * Pass in null to create a new matrix.
   */
  public PMatrix3D getEyeMatrix() {
    return g.getEyeMatrix();
  }


  /**
   * Copy the current eye matrix into the specified target.
   * Pass in null to create a new matrix.
   */
  public PMatrix3D getEyeMatrix(PMatrix3D target) {
    return g.getEyeMatrix(target);
  }


  //////////////////////////////////////////////////////////////

  // EVERYTHING BELOW THIS LINE IS AUTOMATICALLY GENERATED. DO NOT TOUCH!
  // This includes the Javadoc comments, which are automatically copied from
  // the PImage and PGraphics source code files.

  // public functions for processing.core


  public PGL beginPGL() {
    return g.beginPGL();
  }


  public void endPGL() {
    g.endPGL();
  }


  public void flush() {
    g.flush();
  }


  public void hint(int which) {
    g.hint(which);
  }


  /**
   * Start a new shape of type POLYGON
   */
  public void beginShape() {
    g.beginShape();
  }


  /**
   * ( begin auto-generated from beginShape.xml )
   *
   * Using the <b>beginShape()</b> and <b>endShape()</b> functions allow
   * creating more complex forms. <b>beginShape()</b> begins recording
   * vertices for a shape and <b>endShape()</b> stops recording. The value of
   * the <b>MODE</b> parameter tells it which types of shapes to create from
   * the provided vertices. With no mode specified, the shape can be any
   * irregular polygon. The parameters available for beginShape() are POINTS,
   * LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP, QUADS, and QUAD_STRIP.
   * After calling the <b>beginShape()</b> function, a series of
   * <b>vertex()</b> commands must follow. To stop drawing the shape, call
   * <b>endShape()</b>. The <b>vertex()</b> function with two parameters
   * specifies a position in 2D and the <b>vertex()</b> function with three
   * parameters specifies a position in 3D. Each shape will be outlined with
   * the current stroke color and filled with the fill color.
   * <br/> <br/>
   * Transformations such as <b>translate()</b>, <b>rotate()</b>, and
   * <b>scale()</b> do not work within <b>beginShape()</b>. It is also not
   * possible to use other shapes, such as <b>ellipse()</b> or <b>rect()</b>
   * within <b>beginShape()</b>.
   * <br/> <br/>
   * The P3D renderer settings allow <b>stroke()</b> and <b>fill()</b>
   * settings to be altered per-vertex, however the default P2D renderer does
   * not. Settings such as <b>strokeWeight()</b>, <b>strokeCap()</b>, and
   * <b>strokeJoin()</b> cannot be changed while inside a
   * <b>beginShape()</b>/<b>endShape()</b> block with any renderer.
   *
   * ( end auto-generated )
   * @webref shape:vertex
   * @param kind Either POINTS, LINES, TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP, QUADS, or QUAD_STRIP
   * @see PShape
   * @see PGraphics#endShape()
   * @see PGraphics#vertex(float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float, float, float, float)
   */
  public void beginShape(int kind) {
    g.beginShape(kind);
  }


  /**
   * Sets whether the upcoming vertex is part of an edge.
   * Equivalent to glEdgeFlag(), for people familiar with OpenGL.
   */
  public void edge(boolean edge) {
    g.edge(edge);
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
   * @see PGraphics#beginShape(int)
   * @see PGraphics#endShape(int)
   * @see PGraphics#lights()
   */
  public void normal(float nx, float ny, float nz) {
    g.normal(nx, ny, nz);
  }


  public void attribPosition(String name, float x, float y, float z) {
    g.attribPosition(name, x, y, z);
  }


  public void attribNormal(String name, float nx, float ny, float nz) {
    g.attribNormal(name, nx, ny, nz);
  }


  public void attribColor(String name, int color) {
    g.attribColor(name, color);
  }


  public void attrib(String name, float... values) {
    g.attrib(name, values);
  }


  public void attrib(String name, int... values) {
    g.attrib(name, values);
  }


  public void attrib(String name, boolean... values) {
    g.attrib(name, values);
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
   * @see PGraphics#texture(PImage)
   * @see PGraphics#textureWrap(int)
   */
  public void textureMode(int mode) {
    g.textureMode(mode);
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
   * @see PGraphics#texture(PImage)
   * @see PGraphics#textureMode(int)
   */
  public void textureWrap(int wrap) {
    g.textureWrap(wrap);
  }


  /**
   * ( begin auto-generated from texture.xml )
   *
   * Sets a texture to be applied to vertex points. The <b>texture()</b>
   * function must be called between <b>beginShape()</b> and
   * <b>endShape()</b> and before any calls to <b>vertex()</b>.
   * <br/> <br/>
   * When textures are in use, the fill color is ignored. Instead, use tint()
   * to specify the color of the texture as it is applied to the shape.
   *
   * ( end auto-generated )
   * @webref image:textures
   * @param image reference to a PImage object
   * @see PGraphics#textureMode(int)
   * @see PGraphics#textureWrap(int)
   * @see PGraphics#beginShape(int)
   * @see PGraphics#endShape(int)
   * @see PGraphics#vertex(float, float, float, float, float)
   */
  public void texture(PImage image) {
    g.texture(image);
  }


  /**
   * Removes texture image for current shape.
   * Needs to be called between beginShape and endShape
   *
   */
  public void noTexture() {
    g.noTexture();
  }


  public void vertex(float x, float y) {
    g.vertex(x, y);
  }


  public void vertex(float x, float y, float z) {
    g.vertex(x, y, z);
  }


  /**
   * Used by renderer subclasses or PShape to efficiently pass in already
   * formatted vertex information.
   * @param v vertex parameters, as a float array of length VERTEX_FIELD_COUNT
   */
  public void vertex(float[] v) {
    g.vertex(v);
  }


  public void vertex(float x, float y, float u, float v) {
    g.vertex(x, y, u, v);
  }


  /**
   * ( begin auto-generated from vertex.xml )
   *
   * All shapes are constructed by connecting a series of vertices.
   * <b>vertex()</b> is used to specify the vertex coordinates for points,
   * lines, triangles, quads, and polygons and is used exclusively within the
   * <b>beginShape()</b> and <b>endShape()</b> function.<br />
   * <br />
   * Drawing a vertex in 3D using the <b>z</b> parameter requires the P3D
   * parameter in combination with size as shown in the above example.<br />
   * <br />
   * This function is also used to map a texture onto the geometry. The
   * <b>texture()</b> function declares the texture to apply to the geometry
   * and the <b>u</b> and <b>v</b> coordinates set define the mapping of this
   * texture to the form. By default, the coordinates used for <b>u</b> and
   * <b>v</b> are specified in relation to the image's size in pixels, but
   * this relation can be changed with <b>textureMode()</b>.
   *
   * ( end auto-generated )
   * @webref shape:vertex
   * @param x x-coordinate of the vertex
   * @param y y-coordinate of the vertex
   * @param z z-coordinate of the vertex
   * @param u horizontal coordinate for the texture mapping
   * @param v vertical coordinate for the texture mapping
   * @see PGraphics#beginShape(int)
   * @see PGraphics#endShape(int)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float, float, float, float)
   * @see PGraphics#quadraticVertex(float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#texture(PImage)
   */
  public void vertex(float x, float y, float z, float u, float v) {
    g.vertex(x, y, z, u, v);
  }


  /**
   * @webref shape:vertex
   */
  public void beginContour() {
    g.beginContour();
  }


  /**
   * @webref shape:vertex
   */
  public void endContour() {
    g.endContour();
  }


  public void endShape() {
    g.endShape();
  }


  /**
   * ( begin auto-generated from endShape.xml )
   *
   * The <b>endShape()</b> function is the companion to <b>beginShape()</b>
   * and may only be called after <b>beginShape()</b>. When <b>endshape()</b>
   * is called, all of image data defined since the previous call to
   * <b>beginShape()</b> is written into the image buffer. The constant CLOSE
   * as the value for the MODE parameter to close the shape (to connect the
   * beginning and the end).
   *
   * ( end auto-generated )
   * @webref shape:vertex
   * @param mode use CLOSE to close the shape
   * @see PShape
   * @see PGraphics#beginShape(int)
   */
  public void endShape(int mode) {
    g.endShape(mode);
  }


  /**
   * @webref shape
   * @param filename name of file to load, can be .svg or .obj
   * @see PShape
   * @see PApplet#createShape()
   */
  public PShape loadShape(String filename) {
    return g.loadShape(filename);
  }


  /**
   * @nowebref
   */
  public PShape loadShape(String filename, String options) {
    return g.loadShape(filename, options);
  }


  /**
   * @webref shape
   * @see PShape
   * @see PShape#endShape()
   * @see PApplet#loadShape(String)
   */
  public PShape createShape() {
    return g.createShape();
  }


  public PShape createShape(int type) {
    return g.createShape(type);
  }


  /**
   * @param kind either POINT, LINE, TRIANGLE, QUAD, RECT, ELLIPSE, ARC, BOX, SPHERE
   * @param p parameters that match the kind of shape
   */
  public PShape createShape(int kind, float... p) {
    return g.createShape(kind, p);
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
  public PShader loadShader(String fragFilename) {
    return g.loadShader(fragFilename);
  }


  /**
   * @param vertFilename name of vertex shader file
   */
  public PShader loadShader(String fragFilename, String vertFilename) {
    return g.loadShader(fragFilename, vertFilename);
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
  public void shader(PShader shader) {
    g.shader(shader);
  }


  /**
   * @param kind type of shader, either POINTS, LINES, or TRIANGLES
   */
  public void shader(PShader shader, int kind) {
    g.shader(shader, kind);
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
  public void resetShader() {
    g.resetShader();
  }


  /**
   * @param kind type of shader, either POINTS, LINES, or TRIANGLES
   */
  public void resetShader(int kind) {
    g.resetShader(kind);
  }


  /**
   * @param shader the fragment shader to apply
   */
  public void filter(PShader shader) {
    g.filter(shader);
  }


  /**
   * ( begin auto-generated from clip.xml )
   *
   * Limits the rendering to the boundaries of a rectangle defined
   * by the parameters. The boundaries are drawn based on the state
   * of the <b>imageMode()</b> fuction, either CORNER, CORNERS, or CENTER.
   *
   * ( end auto-generated )
   *
   * @webref rendering
   * @param a x-coordinate of the rectangle, by default
   * @param b y-coordinate of the rectangle, by default
   * @param c width of the rectangle, by default
   * @param d height of the rectangle, by default
   */
  public void clip(float a, float b, float c, float d) {
    g.clip(a, b, c, d);
  }


  /**
   * ( begin auto-generated from noClip.xml )
   *
   * Disables the clipping previously started by the <b>clip()</b> function.
   *
   * ( end auto-generated )
   *
   * @webref rendering
   */
  public void noClip() {
    g.noClip();
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
  public void blendMode(int mode) {
    g.blendMode(mode);
  }


  public void bezierVertex(float x2, float y2,
                           float x3, float y3,
                           float x4, float y4) {
    g.bezierVertex(x2, y2, x3, y3, x4, y4);
  }


  /**
   * ( begin auto-generated from bezierVertex.xml )
   *
   * Specifies vertex coordinates for Bezier curves. Each call to
   * <b>bezierVertex()</b> defines the position of two control points and one
   * anchor point of a Bezier curve, adding a new segment to a line or shape.
   * The first time <b>bezierVertex()</b> is used within a
   * <b>beginShape()</b> call, it must be prefaced with a call to
   * <b>vertex()</b> to set the first anchor point. This function must be
   * used between <b>beginShape()</b> and <b>endShape()</b> and only when
   * there is no MODE parameter specified to <b>beginShape()</b>. Using the
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
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#vertex(float, float, float, float, float)
   * @see PGraphics#quadraticVertex(float, float, float, float, float, float)
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void bezierVertex(float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float x4, float y4, float z4) {
    g.bezierVertex(x2, y2, z2, x3, y3, z3, x4, y4, z4);
  }


  /**
   * @webref shape:vertex
   * @param cx the x-coordinate of the control point
   * @param cy the y-coordinate of the control point
   * @param x3 the x-coordinate of the anchor point
   * @param y3 the y-coordinate of the anchor point
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#vertex(float, float, float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void quadraticVertex(float cx, float cy,
                              float x3, float y3) {
    g.quadraticVertex(cx, cy, x3, y3);
  }


  /**
   * @param cz the z-coordinate of the control point
   * @param z3 the z-coordinate of the anchor point
   */
  public void quadraticVertex(float cx, float cy, float cz,
                              float x3, float y3, float z3) {
    g.quadraticVertex(cx, cy, cz, x3, y3, z3);
  }


  /**
   * ( begin auto-generated from curveVertex.xml )
   *
   * Specifies vertex coordinates for curves. This function may only be used
   * between <b>beginShape()</b> and <b>endShape()</b> and only when there is
   * no MODE parameter specified to <b>beginShape()</b>. The first and last
   * points in a series of <b>curveVertex()</b> lines will be used to guide
   * the beginning and end of a the curve. A minimum of four points is
   * required to draw a tiny curve between the second and third points.
   * Adding a fifth point with <b>curveVertex()</b> will draw the curve
   * between the second, third, and fourth points. The <b>curveVertex()</b>
   * function is an implementation of Catmull-Rom splines. Using the 3D
   * version requires rendering with P3D (see the Environment reference for
   * more information).
   *
   * ( end auto-generated )
   *
   * @webref shape:vertex
   * @param x the x-coordinate of the vertex
   * @param y the y-coordinate of the vertex
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#beginShape(int)
   * @see PGraphics#endShape(int)
   * @see PGraphics#vertex(float, float, float, float, float)
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#quadraticVertex(float, float, float, float, float, float)
   */
  public void curveVertex(float x, float y) {
    g.curveVertex(x, y);
  }


  /**
   * @param z the z-coordinate of the vertex
   */
  public void curveVertex(float x, float y, float z) {
    g.curveVertex(x, y, z);
  }


  /**
   * ( begin auto-generated from point.xml )
   *
   * Draws a point, a coordinate in space at the dimension of one pixel. The
   * first parameter is the horizontal value for the point, the second value
   * is the vertical value for the point, and the optional third value is the
   * depth value. Drawing this shape in 3D with the <b>z</b> parameter
   * requires the P3D parameter in combination with <b>size()</b> as shown in
   * the above example.
   *
   * ( end auto-generated )
   *
   * @webref shape:2d_primitives
   * @param x x-coordinate of the point
   * @param y y-coordinate of the point
   * @see PGraphics#stroke(int)
   */
  public void point(float x, float y) {
    g.point(x, y);
  }


  /**
   * @param z z-coordinate of the point
   */
  public void point(float x, float y, float z) {
    g.point(x, y, z);
  }


  /**
   * ( begin auto-generated from line.xml )
   *
   * Draws a line (a direct path between two points) to the screen. The
   * version of <b>line()</b> with four parameters draws the line in 2D.  To
   * color a line, use the <b>stroke()</b> function. A line cannot be filled,
   * therefore the <b>fill()</b> function will not affect the color of a
   * line. 2D lines are drawn with a width of one pixel by default, but this
   * can be changed with the <b>strokeWeight()</b> function. The version with
   * six parameters allows the line to be placed anywhere within XYZ space.
   * Drawing this shape in 3D with the <b>z</b> parameter requires the P3D
   * parameter in combination with <b>size()</b> as shown in the above example.
   *
   * ( end auto-generated )
   * @webref shape:2d_primitives
   * @param x1 x-coordinate of the first point
   * @param y1 y-coordinate of the first point
   * @param x2 x-coordinate of the second point
   * @param y2 y-coordinate of the second point
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeJoin(int)
   * @see PGraphics#strokeCap(int)
   * @see PGraphics#beginShape()
   */
  public void line(float x1, float y1, float x2, float y2) {
    g.line(x1, y1, x2, y2);
  }


  /**
   * @param z1 z-coordinate of the first point
   * @param z2 z-coordinate of the second point
   */
  public void line(float x1, float y1, float z1,
                   float x2, float y2, float z2) {
    g.line(x1, y1, z1, x2, y2, z2);
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
   * @see PApplet#beginShape()
   */
  public void triangle(float x1, float y1, float x2, float y2,
                       float x3, float y3) {
    g.triangle(x1, y1, x2, y2, x3, y3);
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
  public void quad(float x1, float y1, float x2, float y2,
                   float x3, float y3, float x4, float y4) {
    g.quad(x1, y1, x2, y2, x3, y3, x4, y4);
  }


  /**
   * ( begin auto-generated from rectMode.xml )
   *
   * Modifies the location from which rectangles draw. The default mode is
   * <b>rectMode(CORNER)</b>, which specifies the location to be the upper
   * left corner of the shape and uses the third and fourth parameters of
   * <b>rect()</b> to specify the width and height. The syntax
   * <b>rectMode(CORNERS)</b> uses the first and second parameters of
   * <b>rect()</b> to set the location of one corner and uses the third and
   * fourth parameters to set the opposite corner. The syntax
   * <b>rectMode(CENTER)</b> draws the image from its center point and uses
   * the third and forth parameters of <b>rect()</b> to specify the image's
   * width and height. The syntax <b>rectMode(RADIUS)</b> draws the image
   * from its center point and uses the third and forth parameters of
   * <b>rect()</b> to specify half of the image's width and height. The
   * parameter must be written in ALL CAPS because Processing is a case
   * sensitive language. Note: In version 125, the mode named CENTER_RADIUS
   * was shortened to RADIUS.
   *
   * ( end auto-generated )
   * @webref shape:attributes
   * @param mode either CORNER, CORNERS, CENTER, or RADIUS
   * @see PGraphics#rect(float, float, float, float)
   */
  public void rectMode(int mode) {
    g.rectMode(mode);
  }


  /**
   * ( begin auto-generated from rect.xml )
   *
   * Draws a rectangle to the screen. A rectangle is a four-sided shape with
   * every angle at ninety degrees. By default, the first two parameters set
   * the location of the upper-left corner, the third sets the width, and the
   * fourth sets the height. These parameters may be changed with the
   * <b>rectMode()</b> function.
   *
   * ( end auto-generated )
   *
   * @webref shape:2d_primitives
   * @param a x-coordinate of the rectangle by default
   * @param b y-coordinate of the rectangle by default
   * @param c width of the rectangle by default
   * @param d height of the rectangle by default
   * @see PGraphics#rectMode(int)
   * @see PGraphics#quad(float, float, float, float, float, float, float, float)
   */
  public void rect(float a, float b, float c, float d) {
    g.rect(a, b, c, d);
  }


  /**
   * @param r radii for all four corners
   */
  public void rect(float a, float b, float c, float d, float r) {
    g.rect(a, b, c, d, r);
  }


  /**
   * @param tl radius for top-left corner
   * @param tr radius for top-right corner
   * @param br radius for bottom-right corner
   * @param bl radius for bottom-left corner
   */
  public void rect(float a, float b, float c, float d,
                   float tl, float tr, float br, float bl) {
    g.rect(a, b, c, d, tl, tr, br, bl);
  }


  /**
   * ( begin auto-generated from square.xml )
   *
   * Draws a square to the screen. A square is a four-sided shape with
   * every angle at ninety degrees and each side is the same length.
   * By default, the first two parameters set the location of the
   * upper-left corner, the third sets the width and height. The way
   * these parameters are interpreted, however, may be changed with the
   * <b>rectMode()</b> function.
   *
   * ( end auto-generated )
   *
   * @webref shape:2d_primitives
   * @param x x-coordinate of the rectangle by default
   * @param y y-coordinate of the rectangle by default
   * @param extent width and height of the rectangle by default
   * @see PGraphics#rect(float, float, float, float)
   * @see PGraphics#rectMode(int)
   */
  public void square(float x, float y, float extent) {
    g.square(x, y, extent);
  }


  /**
   * ( begin auto-generated from ellipseMode.xml )
   *
   * The origin of the ellipse is modified by the <b>ellipseMode()</b>
   * function. The default configuration is <b>ellipseMode(CENTER)</b>, which
   * specifies the location of the ellipse as the center of the shape. The
   * <b>RADIUS</b> mode is the same, but the width and height parameters to
   * <b>ellipse()</b> specify the radius of the ellipse, rather than the
   * diameter. The <b>CORNER</b> mode draws the shape from the upper-left
   * corner of its bounding box. The <b>CORNERS</b> mode uses the four
   * parameters to <b>ellipse()</b> to set two opposing corners of the
   * ellipse's bounding box. The parameter must be written in ALL CAPS
   * because Processing is a case-sensitive language.
   *
   * ( end auto-generated )
   * @webref shape:attributes
   * @param mode either CENTER, RADIUS, CORNER, or CORNERS
   * @see PApplet#ellipse(float, float, float, float)
   * @see PApplet#arc(float, float, float, float, float, float)
   */
  public void ellipseMode(int mode) {
    g.ellipseMode(mode);
  }


  /**
   * ( begin auto-generated from ellipse.xml )
   *
   * Draws an ellipse (oval) in the display window. An ellipse with an equal
   * <b>width</b> and <b>height</b> is a circle. The first two parameters set
   * the location, the third sets the width, and the fourth sets the height.
   * The origin may be changed with the <b>ellipseMode()</b> function.
   *
   * ( end auto-generated )
   * @webref shape:2d_primitives
   * @param a x-coordinate of the ellipse
   * @param b y-coordinate of the ellipse
   * @param c width of the ellipse by default
   * @param d height of the ellipse by default
   * @see PApplet#ellipseMode(int)
   * @see PApplet#arc(float, float, float, float, float, float)
   */
  public void ellipse(float a, float b, float c, float d) {
    g.ellipse(a, b, c, d);
  }


  /**
   * ( begin auto-generated from arc.xml )
   *
   * Draws an arc in the display window. Arcs are drawn along the outer edge
   * of an ellipse defined by the <b>x</b>, <b>y</b>, <b>width</b> and
   * <b>height</b> parameters. The origin or the arc's ellipse may be changed
   * with the <b>ellipseMode()</b> function. The <b>start</b> and <b>stop</b>
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
   * @see PApplet#ellipse(float, float, float, float)
   * @see PApplet#ellipseMode(int)
   * @see PApplet#radians(float)
   * @see PApplet#degrees(float)
   */
  public void arc(float a, float b, float c, float d,
                  float start, float stop) {
    g.arc(a, b, c, d, start, stop);
  }


  /*
   * @param mode either OPEN, CHORD, or PIE
   */
  public void arc(float a, float b, float c, float d,
                  float start, float stop, int mode) {
    g.arc(a, b, c, d, start, stop, mode);
  }


  /**
   * ( begin auto-generated from circle.xml )
   *
   * Draws a circle to the screen. By default, the first two parameters
   * set the location of the center, and the third sets the shape's width
   * and height. The origin may be changed with the <b>ellipseMode()</b>
   * function.
   *
   * ( end auto-generated )
   * @webref shape:2d_primitives
   * @param x x-coordinate of the ellipse
   * @param y y-coordinate of the ellipse
   * @param extent width and height of the ellipse by default
   * @see PApplet#ellipse(float, float, float, float)
   * @see PApplet#ellipseMode(int)
   */
  public void circle(float x, float y, float extent) {
    g.circle(x, y, extent);
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
   * @see PGraphics#sphere(float)
   */
  public void box(float size) {
    g.box(size);
  }


  /**
   * @param w dimension of the box in the x-dimension
   * @param h dimension of the box in the y-dimension
   * @param d dimension of the box in the z-dimension
   */
  public void box(float w, float h, float d) {
    g.box(w, h, d);
  }


  /**
   * ( begin auto-generated from sphereDetail.xml )
   *
   * Controls the detail used to render a sphere by adjusting the number of
   * vertices of the sphere mesh. The default resolution is 30, which creates
   * a fairly detailed sphere definition with vertices every 360/30 = 12
   * degrees. If you're going to render a great number of spheres per frame,
   * it is advised to reduce the level of detail using this function. The
   * setting stays active until <b>sphereDetail()</b> is called again with a
   * new parameter and so should <i>not</i> be called prior to every
   * <b>sphere()</b> statement, unless you wish to render spheres with
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
   * @see PGraphics#sphere(float)
   */
  public void sphereDetail(int res) {
    g.sphereDetail(res);
  }


  /**
   * @param ures number of segments used longitudinally per full circle revolutoin
   * @param vres number of segments used latitudinally from top to bottom
   */
  public void sphereDetail(int ures, int vres) {
    g.sphereDetail(ures, vres);
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
   * <P>
   * cache all the points of the sphere in a static array
   * top and bottom are just a bunch of triangles that land
   * in the center point
   * <P>
   * sphere is a series of concentric circles who radii vary
   * along the shape, based on, er.. cos or something
   * <PRE>
   * [toxi 031031] new sphere code. removed all multiplies with
   * radius, as scale() will take care of that anyway
   *
   * [toxi 031223] updated sphere code (removed modulos)
   * and introduced sphereAt(x,y,z,r)
   * to avoid additional translate()'s on the user/sketch side
   *
   * [davbol 080801] now using separate sphereDetailU/V
   * </PRE>
   *
   * @webref shape:3d_primitives
   * @param r the radius of the sphere
   * @see PGraphics#sphereDetail(int)
   */
  public void sphere(float r) {
    g.sphere(r);
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
   *   float t = i / 10.0f;
   *   float x = bezierPoint(85, 10, 90, 15, t);
   *   float y = bezierPoint(20, 10, 90, 80, t);
   *   vertex(x, y);
   * }
   * endShape();</PRE>
   *
   * @webref shape:curves
   * @param a coordinate of first point on the curve
   * @param b coordinate of first control point
   * @param c coordinate of second control point
   * @param d coordinate of second point on the curve
   * @param t value between 0 and 1
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#curvePoint(float, float, float, float, float)
   */
  public float bezierPoint(float a, float b, float c, float d, float t) {
    return g.bezierPoint(a, b, c, d, t);
  }


  /**
   * ( begin auto-generated from bezierTangent.xml )
   *
   * Calculates the tangent of a point on a Bezier curve. There is a good
   * definition of <a href="http://en.wikipedia.org/wiki/Tangent"
   * target="new"><em>tangent</em> on Wikipedia</a>.
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
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#curvePoint(float, float, float, float, float)
   */
  public float bezierTangent(float a, float b, float c, float d, float t) {
    return g.bezierTangent(a, b, c, d, t);
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
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float, float)
   * @see PGraphics#curveTightness(float)
   */
  public void bezierDetail(int detail) {
    g.bezierDetail(detail);
  }


  public void bezier(float x1, float y1,
                     float x2, float y2,
                     float x3, float y3,
                     float x4, float y4) {
    g.bezier(x1, y1, x2, y2, x3, y3, x4, y4);
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
   * <PRE>beginShape();
   * vertex(x1, y1);
   * bezierVertex(x2, y2, x3, y3, x4, y4);
   * endShape();
   * </PRE>
   * In Postscript-speak, this would be:
   * <PRE>moveto(x1, y1);
   * curveto(x2, y2, x3, y3, x4, y4);</PRE>
   * If you were to try and continue that curve like so:
   * <PRE>curveto(x5, y5, x6, y6, x7, y7);</PRE>
   * This would be done in processing by adding these statements:
   * <PRE>bezierVertex(x5, y5, x6, y6, x7, y7)
   * </PRE>
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
   * @see PGraphics#bezierVertex(float, float, float, float, float, float)
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void bezier(float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float x3, float y3, float z3,
                     float x4, float y4, float z4) {
    g.bezier(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
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
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#bezierPoint(float, float, float, float, float)
   */
  public float curvePoint(float a, float b, float c, float d, float t) {
    return g.curvePoint(a, b, c, d, t);
  }


  /**
   * ( begin auto-generated from curveTangent.xml )
   *
   * Calculates the tangent of a point on a curve. There's a good definition
   * of <em><a href="http://en.wikipedia.org/wiki/Tangent"
   * target="new">tangent</em> on Wikipedia</a>.
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
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#curvePoint(float, float, float, float, float)
   * @see PGraphics#bezierTangent(float, float, float, float, float)
   */
  public float curveTangent(float a, float b, float c, float d, float t) {
    return g.curveTangent(a, b, c, d, t);
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
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#curveTightness(float)
   */
  public void curveDetail(int detail) {
    g.curveDetail(detail);
  }


  /**
   * ( begin auto-generated from curveTightness.xml )
   *
   * Modifies the quality of forms created with <b>curve()</b> and
   * <b>curveVertex()</b>. The parameter <b>squishy</b> determines how the
   * curve fits to the vertex points. The value 0.0 is the default value for
   * <b>squishy</b> (this value defines the curves to be Catmull-Rom splines)
   * and the value 1.0 connects all the points with straight lines. Values
   * within the range -5.0 and 5.0 will deform the curves but will leave them
   * recognizable and as values increase in magnitude, they will continue to deform.
   *
   * ( end auto-generated )
   *
   * @webref shape:curves
   * @param tightness amount of deformation from the original vertices
   * @see PGraphics#curve(float, float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#curveVertex(float, float)
   */
  public void curveTightness(float tightness) {
    g.curveTightness(tightness);
  }


  /**
   * ( begin auto-generated from curve.xml )
   *
   * Draws a curved line on the screen. The first and second parameters
   * specify the beginning control point and the last two parameters specify
   * the ending control point. The middle parameters specify the start and
   * stop of the curve. Longer curves can be created by putting a series of
   * <b>curve()</b> functions together or using <b>curveVertex()</b>. An
   * additional function called <b>curveTightness()</b> provides control for
   * the visual quality of the curve. The <b>curve()</b> function is an
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
   * Identical to typing out:<PRE>
   * beginShape();
   * curveVertex(x1, y1);
   * curveVertex(x2, y2);
   * curveVertex(x3, y3);
   * curveVertex(x4, y4);
   * endShape();
   * </PRE>
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
   * @see PGraphics#curveVertex(float, float)
   * @see PGraphics#curveTightness(float)
   * @see PGraphics#bezier(float, float, float, float, float, float, float, float, float, float, float, float)
   */
  public void curve(float x1, float y1,
                    float x2, float y2,
                    float x3, float y3,
                    float x4, float y4) {
    g.curve(x1, y1, x2, y2, x3, y3, x4, y4);
  }


  /**
   * @param z1 coordinates for the beginning control point
   * @param z2 coordinates for the first point
   * @param z3 coordinates for the second point
   * @param z4 coordinates for the ending control point
   */
  public void curve(float x1, float y1, float z1,
                    float x2, float y2, float z2,
                    float x3, float y3, float z3,
                    float x4, float y4, float z4) {
    g.curve(x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
  }


  /**
   * ( begin auto-generated from imageMode.xml )
   *
   * Modifies the location from which images draw. The default mode is
   * <b>imageMode(CORNER)</b>, which specifies the location to be the upper
   * left corner and uses the fourth and fifth parameters of <b>image()</b>
   * to set the image's width and height. The syntax
   * <b>imageMode(CORNERS)</b> uses the second and third parameters of
   * <b>image()</b> to set the location of one corner of the image and uses
   * the fourth and fifth parameters to set the opposite corner. Use
   * <b>imageMode(CENTER)</b> to draw images centered at the given x and y
   * position.<br />
   * <br />
   * The parameter to <b>imageMode()</b> must be written in ALL CAPS because
   * Processing is a case-sensitive language.
   *
   * ( end auto-generated )
   *
   * @webref image:loading_displaying
   * @param mode either CORNER, CORNERS, or CENTER
   * @see PApplet#loadImage(String, String)
   * @see PImage
   * @see PGraphics#image(PImage, float, float, float, float)
   * @see PGraphics#background(float, float, float, float)
   */
  public void imageMode(int mode) {
    g.imageMode(mode);
  }


  /**
   * ( begin auto-generated from image.xml )
   *
   * Displays images to the screen. The images must be in the sketch's "data"
   * directory to load correctly. Select "Add file..." from the "Sketch" menu
   * to add the image. Processing currently works with GIF, JPEG, and Targa
   * images. The <b>img</b> parameter specifies the image to display and the
   * <b>x</b> and <b>y</b> parameters define the location of the image from
   * its upper-left corner. The image is displayed at its original size
   * unless the <b>width</b> and <b>height</b> parameters specify a different
   * size.<br />
   * <br />
   * The <b>imageMode()</b> function changes the way the parameters work. For
   * example, a call to <b>imageMode(CORNERS)</b> will change the
   * <b>width</b> and <b>height</b> parameters to define the x and y values
   * of the opposite corner of the image.<br />
   * <br />
   * The color of an image may be modified with the <b>tint()</b> function.
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
   * @see PApplet#loadImage(String, String)
   * @see PImage
   * @see PGraphics#imageMode(int)
   * @see PGraphics#tint(float)
   * @see PGraphics#background(float, float, float, float)
   * @see PGraphics#alpha(int)
   */
  public void image(PImage img, float a, float b) {
    g.image(img, a, b);
  }


  /**
   * @param c width to display the image by default
   * @param d height to display the image by default
   */
  public void image(PImage img, float a, float b, float c, float d) {
    g.image(img, a, b, c, d);
  }


  /**
   * Draw an image(), also specifying u/v coordinates.
   * In this method, the  u, v coordinates are always based on image space
   * location, regardless of the current textureMode().
   *
   * @nowebref
   */
  public void image(PImage img,
                    float a, float b, float c, float d,
                    int u1, int v1, int u2, int v2) {
    g.image(img, a, b, c, d, u1, v1, u2, v2);
  }


  /**
   * ( begin auto-generated from shapeMode.xml )
   *
   * Modifies the location from which shapes draw. The default mode is
   * <b>shapeMode(CORNER)</b>, which specifies the location to be the upper
   * left corner of the shape and uses the third and fourth parameters of
   * <b>shape()</b> to specify the width and height. The syntax
   * <b>shapeMode(CORNERS)</b> uses the first and second parameters of
   * <b>shape()</b> to set the location of one corner and uses the third and
   * fourth parameters to set the opposite corner. The syntax
   * <b>shapeMode(CENTER)</b> draws the shape from its center point and uses
   * the third and forth parameters of <b>shape()</b> to specify the width
   * and height. The parameter must be written in "ALL CAPS" because
   * Processing is a case sensitive language.
   *
   * ( end auto-generated )
   *
   * @webref shape:loading_displaying
   * @param mode either CORNER, CORNERS, CENTER
   * @see PShape
   * @see PGraphics#shape(PShape)
   * @see PGraphics#rectMode(int)
   */
  public void shapeMode(int mode) {
    g.shapeMode(mode);
  }


  public void shape(PShape shape) {
    g.shape(shape);
  }


  /**
   * ( begin auto-generated from shape.xml )
   *
   * Displays shapes to the screen. The shapes must be in the sketch's "data"
   * directory to load correctly. Select "Add file..." from the "Sketch" menu
   * to add the shape. Processing currently works with SVG shapes only. The
   * <b>sh</b> parameter specifies the shape to display and the <b>x</b> and
   * <b>y</b> parameters define the location of the shape from its upper-left
   * corner. The shape is displayed at its original size unless the
   * <b>width</b> and <b>height</b> parameters specify a different size. The
   * <b>shapeMode()</b> function changes the way the parameters work. A call
   * to <b>shapeMode(CORNERS)</b>, for example, will change the width and
   * height parameters to define the x and y values of the opposite corner of
   * the shape.
   * <br /><br />
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
   * @see PApplet#loadShape(String)
   * @see PGraphics#shapeMode(int)
   *
   * Convenience method to draw at a particular location.
   */
  public void shape(PShape shape, float x, float y) {
    g.shape(shape, x, y);
  }


  /**
   * @param a x-coordinate of the shape
   * @param b y-coordinate of the shape
   * @param c width to display the shape
   * @param d height to display the shape
   */
  public void shape(PShape shape, float a, float b, float c, float d) {
    g.shape(shape, a, b, c, d);
  }


  public void textAlign(int alignX) {
    g.textAlign(alignX);
  }


  /**
   * ( begin auto-generated from textAlign.xml )
   *
   * Sets the current alignment for drawing text. The parameters LEFT,
   * CENTER, and RIGHT set the display characteristics of the letters in
   * relation to the values for the <b>x</b> and <b>y</b> parameters of the
   * <b>text()</b> function.
   * <br/> <br/>
   * In Processing 0125 and later, an optional second parameter can be used
   * to vertically align the text. BASELINE is the default, and the vertical
   * alignment will be reset to BASELINE if the second parameter is not used.
   * The TOP and CENTER parameters are straightforward. The BOTTOM parameter
   * offsets the line based on the current <b>textDescent()</b>. For multiple
   * lines, the final line will be aligned to the bottom, with the previous
   * lines appearing above it.
   * <br/> <br/>
   * When using <b>text()</b> with width and height parameters, BASELINE is
   * ignored, and treated as TOP. (Otherwise, text would by default draw
   * outside the box, since BASELINE is the default setting. BASELINE is not
   * a useful drawing mode for text drawn in a rectangle.)
   * <br/> <br/>
   * The vertical alignment is based on the value of <b>textAscent()</b>,
   * which many fonts do not specify correctly. It may be necessary to use a
   * hack and offset by a few pixels by hand so that the offset looks
   * correct. To do this as less of a hack, use some percentage of
   * <b>textAscent()</b> or <b>textDescent()</b> so that the hack works even
   * if you change the size of the font.
   *
   * ( end auto-generated )
   *
   * @webref typography:attributes
   * @param alignX horizontal alignment, either LEFT, CENTER, or RIGHT
   * @param alignY vertical alignment, either TOP, BOTTOM, CENTER, or BASELINE
   * @see PApplet#loadFont(String)
   * @see PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textSize(float)
   * @see PGraphics#textAscent()
   * @see PGraphics#textDescent()
   */
  public void textAlign(int alignX, int alignY) {
    g.textAlign(alignX, alignY);
  }


  /**
   * ( begin auto-generated from textAscent.xml )
   *
   * Returns ascent of the current font at its current size. This information
   * is useful for determining the height of the font above the baseline. For
   * example, adding the <b>textAscent()</b> and <b>textDescent()</b> values
   * will give you the total height of the line.
   *
   * ( end auto-generated )
   *
   * @webref typography:metrics
   * @see PGraphics#textDescent()
   */
  public float textAscent() {
    return g.textAscent();
  }


  /**
   * ( begin auto-generated from textDescent.xml )
   *
   * Returns descent of the current font at its current size. This
   * information is useful for determining the height of the font below the
   * baseline. For example, adding the <b>textAscent()</b> and
   * <b>textDescent()</b> values will give you the total height of the line.
   *
   * ( end auto-generated )
   *
   * @webref typography:metrics
   * @see PGraphics#textAscent()
   */
  public float textDescent() {
    return g.textDescent();
  }


  /**
   * ( begin auto-generated from textFont.xml )
   *
   * Sets the current font that will be drawn with the <b>text()</b>
   * function. Fonts must be loaded with <b>loadFont()</b> before it can be
   * used. This font will be used in all subsequent calls to the
   * <b>text()</b> function. If no <b>size</b> parameter is input, the font
   * will appear at its original size (the size it was created at with the
   * "Create Font..." tool) until it is changed with <b>textSize()</b>. <br
   * /> <br /> Because fonts are usually bitmaped, you should create fonts at
   * the sizes that will be used most commonly. Using <b>textFont()</b>
   * without the size parameter will result in the cleanest-looking text. <br
   * /><br /> With the default (JAVA2D) and PDF renderers, it's also possible
   * to enable the use of native fonts via the command
   * <b>hint(ENABLE_NATIVE_FONTS)</b>. This will produce vector text in
   * JAVA2D sketches and PDF output in cases where the vector data is
   * available: when the font is still installed, or the font is created via
   * the <b>createFont()</b> function (rather than the Create Font tool).
   *
   * ( end auto-generated )
   *
   * @webref typography:loading_displaying
   * @param which any variable of the type PFont
   * @see PApplet#createFont(String, float, boolean)
   * @see PApplet#loadFont(String)
   * @see PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textSize(float)
   */
  public void textFont(PFont which) {
    g.textFont(which);
  }


  /**
   * @param size the size of the letters in units of pixels
   */
  public void textFont(PFont which, float size) {
    g.textFont(which, size);
  }


  /**
   * ( begin auto-generated from textLeading.xml )
   *
   * Sets the spacing between lines of text in units of pixels. This setting
   * will be used in all subsequent calls to the <b>text()</b> function.
   *
   * ( end auto-generated )
   *
   * @webref typography:attributes
   * @param leading the size in pixels for spacing between lines
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#textSize(float)
   */
  public void textLeading(float leading) {
    g.textLeading(leading);
  }


  /**
   * ( begin auto-generated from textMode.xml )
   *
   * Sets the way text draws to the screen. In the default configuration, the
   * <b>MODEL</b> mode, it's possible to rotate, scale, and place letters in
   * two and three dimensional space.<br />
   * <br />
   * The <b>SHAPE</b> mode draws text using the the glyph outlines of
   * individual characters rather than as textures. This mode is only
   * supported with the <b>PDF</b> and <b>P3D</b> renderer settings. With the
   * <b>PDF</b> renderer, you must call <b>textMode(SHAPE)</b> before any
   * other drawing occurs. If the outlines are not available, then
   * <b>textMode(SHAPE)</b> will be ignored and <b>textMode(MODEL)</b> will
   * be used instead.<br />
   * <br />
   * The <b>textMode(SHAPE)</b> option in <b>P3D</b> can be combined with
   * <b>beginRaw()</b> to write vector-accurate text to 2D and 3D output
   * files, for instance <b>DXF</b> or <b>PDF</b>. The <b>SHAPE</b> mode is
   * not currently optimized for <b>P3D</b>, so if recording shape data, use
   * <b>textMode(MODEL)</b> until you're ready to capture the geometry with <b>beginRaw()</b>.
   *
   * ( end auto-generated )
   *
   * @webref typography:attributes
   * @param mode either MODEL or SHAPE
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#beginRaw(PGraphics)
   * @see PApplet#createFont(String, float, boolean)
   */
  public void textMode(int mode) {
    g.textMode(mode);
  }


  /**
   * ( begin auto-generated from textSize.xml )
   *
   * Sets the current font size. This size will be used in all subsequent
   * calls to the <b>text()</b> function. Font size is measured in units of pixels.
   *
   * ( end auto-generated )
   *
   * @webref typography:attributes
   * @param size the size of the letters in units of pixels
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   */
  public void textSize(float size) {
    g.textSize(size);
  }


  /**
   * @param c the character to measure
   */
  public float textWidth(char c) {
    return g.textWidth(c);
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
   * @see PApplet#loadFont(String)
   * @see PFont#PFont
   * @see PGraphics#text(String, float, float)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#textSize(float)
   */
  public float textWidth(String str) {
    return g.textWidth(str);
  }


  /**
   * @nowebref
   */
  public float textWidth(char[] chars, int start, int length) {
    return g.textWidth(chars, start, length);
  }


  /**
   * ( begin auto-generated from text.xml )
   *
   * Draws text to the screen. Displays the information specified in the
   * <b>data</b> or <b>stringdata</b> parameters on the screen in the
   * position specified by the <b>x</b> and <b>y</b> parameters and the
   * optional <b>z</b> parameter. A default font will be used unless a font
   * is set with the <b>textFont()</b> function. Change the color of the text
   * with the <b>fill()</b> function. The text displays in relation to the
   * <b>textAlign()</b> function, which gives the option to draw to the left,
   * right, and center of the coordinates.
   * <br /><br />
   * The <b>x2</b> and <b>y2</b> parameters define a rectangular area to
   * display within and may only be used with string data. For text drawn
   * inside a rectangle, the coordinates are interpreted based on the current
   * <b>rectMode()</b> setting.
   *
   * ( end auto-generated )
   *
   * @webref typography:loading_displaying
   * @param c the alphanumeric character to be displayed
   * @param x x-coordinate of text
   * @param y y-coordinate of text
   * @see PGraphics#textAlign(int, int)
   * @see PGraphics#textFont(PFont)
   * @see PGraphics#textMode(int)
   * @see PGraphics#textSize(float)
   * @see PGraphics#textLeading(float)
   * @see PGraphics#textWidth(String)
   * @see PGraphics#textAscent()
   * @see PGraphics#textDescent()
   * @see PGraphics#rectMode(int)
   * @see PGraphics#fill(int, float)
   * @see_external String
   */
  public void text(char c, float x, float y) {
    g.text(c, x, y);
  }


  /**
   * @param z z-coordinate of text
   */
  public void text(char c, float x, float y, float z) {
    g.text(c, x, y, z);
  }


  /**
   * <h3>Advanced</h3>
   * Draw a chunk of text.
   * Newlines that are \n (Unix newline or linefeed char, ascii 10)
   * are honored, but \r (carriage return, Windows and Mac OS) are
   * ignored.
   */
  public void text(String str, float x, float y) {
    g.text(str, x, y);
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
  public void text(char[] chars, int start, int stop, float x, float y) {
    g.text(chars, start, stop, x, y);
  }


  /**
   * Same as above but with a z coordinate.
   */
  public void text(String str, float x, float y, float z) {
    g.text(str, x, y, z);
  }


  public void text(char[] chars, int start, int stop,
                   float x, float y, float z) {
    g.text(chars, start, stop, x, y, z);
  }


  /**
   * <h3>Advanced</h3>
   * Draw text in a box that is constrained to a particular size.
   * The current rectMode() determines what the coordinates mean
   * (whether x1/y1/x2/y2 or x/y/w/h).
   * <P/>
   * Note that the x,y coords of the start of the box
   * will align with the *ascent* of the text, not the baseline,
   * as is the case for the other text() functions.
   * <P/>
   * Newlines that are \n (Unix newline or linefeed char, ascii 10)
   * are honored, and \r (carriage return, Windows and Mac OS) are
   * ignored.
   *
   * @param x1 by default, the x-coordinate of text, see rectMode() for more info
   * @param y1 by default, the y-coordinate of text, see rectMode() for more info
   * @param x2 by default, the width of the text box, see rectMode() for more info
   * @param y2 by default, the height of the text box, see rectMode() for more info
   */
  public void text(String str, float x1, float y1, float x2, float y2) {
    g.text(str, x1, y1, x2, y2);
  }


  public void text(int num, float x, float y) {
    g.text(num, x, y);
  }


  public void text(int num, float x, float y, float z) {
    g.text(num, x, y, z);
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
  public void text(float num, float x, float y) {
    g.text(num, x, y);
  }


  public void text(float num, float x, float y, float z) {
    g.text(num, x, y, z);
  }


  /**
   * ( begin auto-generated from push.xml )
   *
   * The <b>push()</b> function saves the current drawing style
   * settings and transformations, while <b>pop()</b> restores these
   * settings. Note that these functions are always used together.
   * They allow you to change the style and transformation settings
   * and later return to what you had. When a new state is started
   * with push(), it builds on the current style and transform
   * information.<br />
   * <br />
   * <b>push()</b> stores information related to the current
   * transformation state and style settings controlled by the
   * following functions: <b>rotate()</b>, <b>translate()</b>,
   * <b>scale()</b>, <b>fill()</b>, <b>stroke()</b>, <b>tint()</b>,
   * <b>strokeWeight()</b>, <b>strokeCap()</b>, <b>strokeJoin()</b>,
   * <b>imageMode()</b>, <b>rectMode()</b>, <b>ellipseMode()</b>,
   * <b>colorMode()</b>, <b>textAlign()</b>, <b>textFont()</b>,
   * <b>textMode()</b>, <b>textSize()</b>, <b>textLeading()</b>.<br />
   * <br />
   * The <b>push()</b> and <b>pop()</b> functions were added with
   * Processing 3.5. They can be used in place of <b>pushMatrix()</b>,
   * <b>popMatrix()</b>, <b>pushStyles()</b>, and <b>popStyles()</b>.
   * The difference is that push() and pop() control both the
   * transformations (rotate, scale, translate) and the drawing styles
   * at the same time.
   *
   * ( end auto-generated )
   *
   * @webref structure
   * @see PGraphics#pop()
   */
  public void push() {
    g.push();
  }


  /**
   * ( begin auto-generated from pop.xml )
   *
   * The <b>pop()</b> function restores the previous drawing style
   * settings and transformations after <b>push()</b> has changed them.
   * Note that these functions are always used together. They allow
   * you to change the style and transformation settings and later
   * return to what you had. When a new state is started with push(),
   * it builds on the current style and transform information.<br />
   * <br />
   * <br />
   * <b>push()</b> stores information related to the current
   * transformation state and style settings controlled by the
   * following functions: <b>rotate()</b>, <b>translate()</b>,
   * <b>scale()</b>, <b>fill()</b>, <b>stroke()</b>, <b>tint()</b>,
   * <b>strokeWeight()</b>, <b>strokeCap()</b>, <b>strokeJoin()</b>,
   * <b>imageMode()</b>, <b>rectMode()</b>, <b>ellipseMode()</b>,
   * <b>colorMode()</b>, <b>textAlign()</b>, <b>textFont()</b>,
   * <b>textMode()</b>, <b>textSize()</b>, <b>textLeading()</b>.<br />
   * <br />
   * The <b>push()</b> and <b>pop()</b> functions were added with
   * Processing 3.5. They can be used in place of <b>pushMatrix()</b>,
   * <b>popMatrix()</b>, <b>pushStyles()</b>, and <b>popStyles()</b>.
   * The difference is that push() and pop() control both the
   * transformations (rotate, scale, translate) and the drawing styles
   * at the same time.
   *
   * ( end auto-generated )
   *
   * @webref structure
   * @see PGraphics#push()
   */
  public void pop() {
    g.pop();
  }


  /**
   * ( begin auto-generated from pushMatrix.xml )
   *
   * Pushes the current transformation matrix onto the matrix stack.
   * Understanding <b>pushMatrix()</b> and <b>popMatrix()</b> requires
   * understanding the concept of a matrix stack. The <b>pushMatrix()</b>
   * function saves the current coordinate system to the stack and
   * <b>popMatrix()</b> restores the prior coordinate system.
   * <b>pushMatrix()</b> and <b>popMatrix()</b> are used in conjuction with
   * the other transformation functions and may be embedded to control the
   * scope of the transformations.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @see PGraphics#popMatrix()
   * @see PGraphics#translate(float, float, float)
   * @see PGraphics#scale(float)
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   */
  public void pushMatrix() {
    g.pushMatrix();
  }


  /**
   * ( begin auto-generated from popMatrix.xml )
   *
   * Pops the current transformation matrix off the matrix stack.
   * Understanding pushing and popping requires understanding the concept of
   * a matrix stack. The <b>pushMatrix()</b> function saves the current
   * coordinate system to the stack and <b>popMatrix()</b> restores the prior
   * coordinate system. <b>pushMatrix()</b> and <b>popMatrix()</b> are used
   * in conjuction with the other transformation functions and may be
   * embedded to control the scope of the transformations.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @see PGraphics#pushMatrix()
   */
  public void popMatrix() {
    g.popMatrix();
  }


  /**
   * ( begin auto-generated from translate.xml )
   *
   * Specifies an amount to displace objects within the display window. The
   * <b>x</b> parameter specifies left/right translation, the <b>y</b>
   * parameter specifies up/down translation, and the <b>z</b> parameter
   * specifies translations toward/away from the screen. Using this function
   * with the <b>z</b> parameter requires using P3D as a parameter in
   * combination with size as shown in the above example. Transformations
   * apply to everything that happens after and subsequent calls to the
   * function accumulates the effect. For example, calling <b>translate(50,
   * 0)</b> and then <b>translate(20, 0)</b> is the same as <b>translate(70,
   * 0)</b>. If <b>translate()</b> is called within <b>draw()</b>, the
   * transformation is reset when the loop begins again. This function can be
   * further controlled by the <b>pushMatrix()</b> and <b>popMatrix()</b>.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param x left/right translation
   * @param y up/down translation
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   */
  public void translate(float x, float y) {
    g.translate(x, y);
  }


  /**
   * @param z forward/backward translation
   */
  public void translate(float x, float y, float z) {
    g.translate(x, y, z);
  }


  /**
   * ( begin auto-generated from rotate.xml )
   *
   * Rotates a shape the amount specified by the <b>angle</b> parameter.
   * Angles should be specified in radians (values from 0 to TWO_PI) or
   * converted to radians with the <b>radians()</b> function.
   * <br/> <br/>
   * Objects are always rotated around their relative position to the origin
   * and positive numbers rotate objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent
   * calls to the function accumulates the effect. For example, calling
   * <b>rotate(HALF_PI)</b> and then <b>rotate(HALF_PI)</b> is the same as
   * <b>rotate(PI)</b>. All tranformations are reset when <b>draw()</b>
   * begins again.
   * <br/> <br/>
   * Technically, <b>rotate()</b> multiplies the current transformation
   * matrix by a rotation matrix. This function can be further controlled by
   * the <b>pushMatrix()</b> and <b>popMatrix()</b>.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   * @see PApplet#radians(float)
   */
  public void rotate(float angle) {
    g.rotate(angle);
  }


  /**
   * ( begin auto-generated from rotateX.xml )
   *
   * Rotates a shape around the x-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always rotated around their relative position to
   * the origin and positive numbers rotate objects in a counterclockwise
   * direction. Transformations apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example,
   * calling <b>rotateX(PI/2)</b> and then <b>rotateX(PI/2)</b> is the same
   * as <b>rotateX(PI)</b>. If <b>rotateX()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * This function requires using P3D as a third parameter to <b>size()</b>
   * as shown in the example above.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   */
  public void rotateX(float angle) {
    g.rotateX(angle);
  }


  /**
   * ( begin auto-generated from rotateY.xml )
   *
   * Rotates a shape around the y-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always rotated around their relative position to
   * the origin and positive numbers rotate objects in a counterclockwise
   * direction. Transformations apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example,
   * calling <b>rotateY(PI/2)</b> and then <b>rotateY(PI/2)</b> is the same
   * as <b>rotateY(PI)</b>. If <b>rotateY()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * This function requires using P3D as a third parameter to <b>size()</b>
   * as shown in the examples above.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateZ(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   */
  public void rotateY(float angle) {
    g.rotateY(angle);
  }


  /**
   * ( begin auto-generated from rotateZ.xml )
   *
   * Rotates a shape around the z-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always rotated around their relative position to
   * the origin and positive numbers rotate objects in a counterclockwise
   * direction. Transformations apply to everything that happens after and
   * subsequent calls to the function accumulates the effect. For example,
   * calling <b>rotateZ(PI/2)</b> and then <b>rotateZ(PI/2)</b> is the same
   * as <b>rotateZ(PI)</b>. If <b>rotateZ()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * This function requires using P3D as a third parameter to <b>size()</b>
   * as shown in the examples above.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param angle angle of rotation specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   */
  public void rotateZ(float angle) {
    g.rotateZ(angle);
  }


  /**
   * <h3>Advanced</h3>
   * Rotate about a vector in space. Same as the glRotatef() function.
   * @nowebref
   * @param x
   * @param y
   * @param z
   */
  public void rotate(float angle, float x, float y, float z) {
    g.rotate(angle, x, y, z);
  }


  /**
   * ( begin auto-generated from scale.xml )
   *
   * Increases or decreases the size of a shape by expanding and contracting
   * vertices. Objects always scale from their relative origin to the
   * coordinate system. Scale values are specified as decimal percentages.
   * For example, the function call <b>scale(2.0)</b> increases the dimension
   * of a shape by 200%. Transformations apply to everything that happens
   * after and subsequent calls to the function multiply the effect. For
   * example, calling <b>scale(2.0)</b> and then <b>scale(1.5)</b> is the
   * same as <b>scale(3.0)</b>. If <b>scale()</b> is called within
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * Using this fuction with the <b>z</b> parameter requires using P3D as a
   * parameter for <b>size()</b> as shown in the example above. This function
   * can be further controlled by <b>pushMatrix()</b> and <b>popMatrix()</b>.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param s percentage to scale the object
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#translate(float, float, float)
   * @see PGraphics#rotate(float)
   * @see PGraphics#rotateX(float)
   * @see PGraphics#rotateY(float)
   * @see PGraphics#rotateZ(float)
   */
  public void scale(float s) {
    g.scale(s);
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
  public void scale(float x, float y) {
    g.scale(x, y);
  }


  /**
   * @param z percentage to scale the object in the z-axis
   */
  public void scale(float x, float y, float z) {
    g.scale(x, y, z);
  }


  /**
   * ( begin auto-generated from shearX.xml )
   *
   * Shears a shape around the x-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always sheared around their relative position to
   * the origin and positive numbers shear objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent
   * calls to the function accumulates the effect. For example, calling
   * <b>shearX(PI/2)</b> and then <b>shearX(PI/2)</b> is the same as
   * <b>shearX(PI)</b>. If <b>shearX()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * <br/> <br/>
   * Technically, <b>shearX()</b> multiplies the current transformation
   * matrix by a rotation matrix. This function can be further controlled by
   * the <b>pushMatrix()</b> and <b>popMatrix()</b> functions.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param angle angle of shear specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#shearY(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   * @see PApplet#radians(float)
   */
  public void shearX(float angle) {
    g.shearX(angle);
  }


  /**
   * ( begin auto-generated from shearY.xml )
   *
   * Shears a shape around the y-axis the amount specified by the
   * <b>angle</b> parameter. Angles should be specified in radians (values
   * from 0 to PI*2) or converted to radians with the <b>radians()</b>
   * function. Objects are always sheared around their relative position to
   * the origin and positive numbers shear objects in a clockwise direction.
   * Transformations apply to everything that happens after and subsequent
   * calls to the function accumulates the effect. For example, calling
   * <b>shearY(PI/2)</b> and then <b>shearY(PI/2)</b> is the same as
   * <b>shearY(PI)</b>. If <b>shearY()</b> is called within the
   * <b>draw()</b>, the transformation is reset when the loop begins again.
   * <br/> <br/>
   * Technically, <b>shearY()</b> multiplies the current transformation
   * matrix by a rotation matrix. This function can be further controlled by
   * the <b>pushMatrix()</b> and <b>popMatrix()</b> functions.
   *
   * ( end auto-generated )
   *
   * @webref transform
   * @param angle angle of shear specified in radians
   * @see PGraphics#popMatrix()
   * @see PGraphics#pushMatrix()
   * @see PGraphics#shearX(float)
   * @see PGraphics#scale(float, float, float)
   * @see PGraphics#translate(float, float, float)
   * @see PApplet#radians(float)
   */
  public void shearY(float angle) {
    g.shearY(angle);
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
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#applyMatrix(PMatrix)
   * @see PGraphics#printMatrix()
   */
  public void resetMatrix() {
    g.resetMatrix();
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
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#resetMatrix()
   * @see PGraphics#printMatrix()
   */
  public void applyMatrix(PMatrix source) {
    g.applyMatrix(source);
  }


  public void applyMatrix(PMatrix2D source) {
    g.applyMatrix(source);
  }


  /**
   * @param n00 numbers which define the 4x4 matrix to be multiplied
   * @param n01 numbers which define the 4x4 matrix to be multiplied
   * @param n02 numbers which define the 4x4 matrix to be multiplied
   * @param n10 numbers which define the 4x4 matrix to be multiplied
   * @param n11 numbers which define the 4x4 matrix to be multiplied
   * @param n12 numbers which define the 4x4 matrix to be multiplied
   */
  public void applyMatrix(float n00, float n01, float n02,
                          float n10, float n11, float n12) {
    g.applyMatrix(n00, n01, n02, n10, n11, n12);
  }


  public void applyMatrix(PMatrix3D source) {
    g.applyMatrix(source);
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
  public void applyMatrix(float n00, float n01, float n02, float n03,
                          float n10, float n11, float n12, float n13,
                          float n20, float n21, float n22, float n23,
                          float n30, float n31, float n32, float n33) {
    g.applyMatrix(n00, n01, n02, n03, n10, n11, n12, n13, n20, n21, n22, n23, n30, n31, n32, n33);
  }


  public PMatrix getMatrix() {
    return g.getMatrix();
  }


  /**
   * Copy the current transformation matrix into the specified target.
   * Pass in null to create a new matrix.
   */
  public PMatrix2D getMatrix(PMatrix2D target) {
    return g.getMatrix(target);
  }


  /**
   * Copy the current transformation matrix into the specified target.
   * Pass in null to create a new matrix.
   */
  public PMatrix3D getMatrix(PMatrix3D target) {
    return g.getMatrix(target);
  }


  /**
   * Set the current transformation matrix to the contents of another.
   */
  public void setMatrix(PMatrix source) {
    g.setMatrix(source);
  }


  /**
   * Set the current transformation to the contents of the specified source.
   */
  public void setMatrix(PMatrix2D source) {
    g.setMatrix(source);
  }


  /**
   * Set the current transformation to the contents of the specified source.
   */
  public void setMatrix(PMatrix3D source) {
    g.setMatrix(source);
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
   * @see PGraphics#pushMatrix()
   * @see PGraphics#popMatrix()
   * @see PGraphics#resetMatrix()
   * @see PGraphics#applyMatrix(PMatrix)
   */
  public void printMatrix() {
    g.printMatrix();
  }


  /**
   * ( begin auto-generated from beginCamera.xml )
   *
   * The <b>beginCamera()</b> and <b>endCamera()</b> functions enable
   * advanced customization of the camera space. The functions are useful if
   * you want to more control over camera movement, however for most users,
   * the <b>camera()</b> function will be sufficient.<br /><br />The camera
   * functions will replace any transformations (such as <b>rotate()</b> or
   * <b>translate()</b>) that occur before them in <b>draw()</b>, but they
   * will not automatically replace the camera transform itself. For this
   * reason, camera functions should be placed at the beginning of
   * <b>draw()</b> (so that transformations happen afterwards), and the
   * <b>camera()</b> function can be used after <b>beginCamera()</b> if you
   * want to reset the camera before applying transformations.<br /><br
   * />This function sets the matrix mode to the camera matrix so calls such
   * as <b>translate()</b>, <b>rotate()</b>, applyMatrix() and resetMatrix()
   * affect the camera. <b>beginCamera()</b> should always be used with a
   * following <b>endCamera()</b> and pairs of <b>beginCamera()</b> and
   * <b>endCamera()</b> cannot be nested.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:camera
   * @see PGraphics#camera()
   * @see PGraphics#endCamera()
   * @see PGraphics#applyMatrix(PMatrix)
   * @see PGraphics#resetMatrix()
   * @see PGraphics#translate(float, float, float)
   * @see PGraphics#scale(float, float, float)
   */
  public void beginCamera() {
    g.beginCamera();
  }


  /**
   * ( begin auto-generated from endCamera.xml )
   *
   * The <b>beginCamera()</b> and <b>endCamera()</b> functions enable
   * advanced customization of the camera space. Please see the reference for
   * <b>beginCamera()</b> for a description of how the functions are used.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:camera
   * @see PGraphics#beginCamera()
   * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
   */
  public void endCamera() {
    g.endCamera();
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
   * are <b>camera(width/2.0, height/2.0, (height/2.0) / tan(PI*30.0 /
   * 180.0), width/2.0, height/2.0, 0, 0, 1, 0)</b>. This function is similar
   * to <b>gluLookAt()</b> in OpenGL, but it first clears the current camera settings.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:camera
   * @see PGraphics#beginCamera()
   * @see PGraphics#endCamera()
   * @see PGraphics#frustum(float, float, float, float, float, float)
   */
  public void camera() {
    g.camera();
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
  public void camera(float eyeX, float eyeY, float eyeZ,
                     float centerX, float centerY, float centerZ,
                     float upX, float upY, float upZ) {
    g.camera(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ);
  }


  /**
   * ( begin auto-generated from printCamera.xml )
   *
   * Prints the current camera matrix to the Console (the text window at the
   * bottom of Processing).
   *
   * ( end auto-generated )
   * @webref lights_camera:camera
   * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
   */
  public void printCamera() {
    g.printCamera();
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
  public void ortho() {
    g.ortho();
  }


  /**
   * @param left left plane of the clipping volume
   * @param right right plane of the clipping volume
   * @param bottom bottom plane of the clipping volume
   * @param top top plane of the clipping volume
   */
  public void ortho(float left, float right,
                    float bottom, float top) {
    g.ortho(left, right, bottom, top);
  }


  /**
   * @param near maximum distance from the origin to the viewer
   * @param far maximum distance from the origin away from the viewer
   */
  public void ortho(float left, float right,
                    float bottom, float top,
                    float near, float far) {
    g.ortho(left, right, bottom, top, near, far);
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
  public void perspective() {
    g.perspective();
  }


  /**
   * @param fovy field-of-view angle (in radians) for vertical direction
   * @param aspect ratio of width to height
   * @param zNear z-position of nearest clipping plane
   * @param zFar z-position of farthest clipping plane
   */
  public void perspective(float fovy, float aspect, float zNear, float zFar) {
    g.perspective(fovy, aspect, zNear, zFar);
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
   * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
   * @see PGraphics#beginCamera()
   * @see PGraphics#endCamera()
   * @see PGraphics#perspective(float, float, float, float)
   */
  public void frustum(float left, float right,
                      float bottom, float top,
                      float near, float far) {
    g.frustum(left, right, bottom, top, near, far);
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
   * @see PGraphics#camera(float, float, float, float, float, float, float, float, float)
   */
  public void printProjection() {
    g.printProjection();
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
   * @see PGraphics#screenY(float, float, float)
   * @see PGraphics#screenZ(float, float, float)
   */
  public float screenX(float x, float y) {
    return g.screenX(x, y);
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
   * @see PGraphics#screenX(float, float, float)
   * @see PGraphics#screenZ(float, float, float)
   */
  public float screenY(float x, float y) {
    return g.screenY(x, y);
  }


  /**
   * @param z 3D z-coordinate to be mapped
   */
  public float screenX(float x, float y, float z) {
    return g.screenX(x, y, z);
  }


  /**
   * @param z 3D z-coordinate to be mapped
   */
  public float screenY(float x, float y, float z) {
    return g.screenY(x, y, z);
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
   * @see PGraphics#screenX(float, float, float)
   * @see PGraphics#screenY(float, float, float)
   */
  public float screenZ(float x, float y, float z) {
    return g.screenZ(x, y, z);
  }


  /**
   * ( begin auto-generated from modelX.xml )
   *
   * Returns the three-dimensional X, Y, Z position in model space. This
   * returns the X value for a given coordinate based on the current set of
   * transformations (scale, rotate, translate, etc.) The X value can be used
   * to place an object in space relative to the location of the original
   * point once the transformations are no longer in use.
   * <br/> <br/>
   * In the example, the <b>modelX()</b>, <b>modelY()</b>, and
   * <b>modelZ()</b> functions record the location of a box in space after
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
   * @see PGraphics#modelY(float, float, float)
   * @see PGraphics#modelZ(float, float, float)
   */
  public float modelX(float x, float y, float z) {
    return g.modelX(x, y, z);
  }


  /**
   * ( begin auto-generated from modelY.xml )
   *
   * Returns the three-dimensional X, Y, Z position in model space. This
   * returns the Y value for a given coordinate based on the current set of
   * transformations (scale, rotate, translate, etc.) The Y value can be used
   * to place an object in space relative to the location of the original
   * point once the transformations are no longer in use.<br />
   * <br />
   * In the example, the <b>modelX()</b>, <b>modelY()</b>, and
   * <b>modelZ()</b> functions record the location of a box in space after
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
   * @see PGraphics#modelX(float, float, float)
   * @see PGraphics#modelZ(float, float, float)
   */
  public float modelY(float x, float y, float z) {
    return g.modelY(x, y, z);
  }


  /**
   * ( begin auto-generated from modelZ.xml )
   *
   * Returns the three-dimensional X, Y, Z position in model space. This
   * returns the Z value for a given coordinate based on the current set of
   * transformations (scale, rotate, translate, etc.) The Z value can be used
   * to place an object in space relative to the location of the original
   * point once the transformations are no longer in use.<br />
   * <br />
   * In the example, the <b>modelX()</b>, <b>modelY()</b>, and
   * <b>modelZ()</b> functions record the location of a box in space after
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
   * @see PGraphics#modelX(float, float, float)
   * @see PGraphics#modelY(float, float, float)
   */
  public float modelZ(float x, float y, float z) {
    return g.modelZ(x, y, z);
  }


  /**
   * ( begin auto-generated from pushStyle.xml )
   *
   * The <b>pushStyle()</b> function saves the current style settings and
   * <b>popStyle()</b> restores the prior settings. Note that these functions
   * are always used together. They allow you to change the style settings
   * and later return to what you had. When a new style is started with
   * <b>pushStyle()</b>, it builds on the current style information. The
   * <b>pushStyle()</b> and <b>popStyle()</b> functions can be embedded to
   * provide more control (see the second example above for a demonstration.)
   * <br /><br />
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
   * @see PGraphics#popStyle()
   */
  public void pushStyle() {
    g.pushStyle();
  }


  /**
   * ( begin auto-generated from popStyle.xml )
   *
   * The <b>pushStyle()</b> function saves the current style settings and
   * <b>popStyle()</b> restores the prior settings; these functions are
   * always used together. They allow you to change the style settings and
   * later return to what you had. When a new style is started with
   * <b>pushStyle()</b>, it builds on the current style information. The
   * <b>pushStyle()</b> and <b>popStyle()</b> functions can be embedded to
   * provide more control (see the second example above for a demonstration.)
   *
   * ( end auto-generated )
   *
   * @webref structure
   * @see PGraphics#pushStyle()
   */
  public void popStyle() {
    g.popStyle();
  }


  public void style(PStyle s) {
    g.style(s);
  }


  /**
   * ( begin auto-generated from strokeWeight.xml )
   *
   * Sets the width of the stroke used for lines, points, and the border
   * around shapes. All widths are set in units of pixels.
   * <br/> <br/>
   * When drawing with P3D, series of connected lines (such as the stroke
   * around a polygon, triangle, or ellipse) produce unattractive results
   * when a thick stroke weight is set (<a
   * href="http://code.google.com/p/processing/issues/detail?id=123">see
   * Issue 123</a>). With P3D, the minimum and maximum values for
   * <b>strokeWeight()</b> are controlled by the graphics card and the
   * operating system's OpenGL implementation. For instance, the thickness
   * may not go higher than 10 pixels.
   *
   * ( end auto-generated )
   *
   * @webref shape:attributes
   * @param weight the weight (in pixels) of the stroke
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#strokeJoin(int)
   * @see PGraphics#strokeCap(int)
   */
  public void strokeWeight(float weight) {
    g.strokeWeight(weight);
  }


  /**
   * ( begin auto-generated from strokeJoin.xml )
   *
   * Sets the style of the joints which connect line segments. These joints
   * are either mitered, beveled, or rounded and specified with the
   * corresponding parameters MITER, BEVEL, and ROUND. The default joint is
   * MITER.
   * <br/> <br/>
   * This function is not available with the P3D renderer, (<a
   * href="http://code.google.com/p/processing/issues/detail?id=123">see
   * Issue 123</a>). More information about the renderers can be found in the
   * <b>size()</b> reference.
   *
   * ( end auto-generated )
   *
   * @webref shape:attributes
   * @param join either MITER, BEVEL, ROUND
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeCap(int)
   */
  public void strokeJoin(int join) {
    g.strokeJoin(join);
  }


  /**
   * ( begin auto-generated from strokeCap.xml )
   *
   * Sets the style for rendering line endings. These ends are either
   * squared, extended, or rounded and specified with the corresponding
   * parameters SQUARE, PROJECT, and ROUND. The default cap is ROUND.
   * <br/> <br/>
   * This function is not available with the P3D renderer (<a
   * href="http://code.google.com/p/processing/issues/detail?id=123">see
   * Issue 123</a>). More information about the renderers can be found in the
   * <b>size()</b> reference.
   *
   * ( end auto-generated )
   *
   * @webref shape:attributes
   * @param cap either SQUARE, PROJECT, or ROUND
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeJoin(int)
   * @see PApplet#size(int, int, String, String)
   */
  public void strokeCap(int cap) {
    g.strokeCap(cap);
  }


  /**
   * ( begin auto-generated from noStroke.xml )
   *
   * Disables drawing the stroke (outline). If both <b>noStroke()</b> and
   * <b>noFill()</b> are called, nothing will be drawn to the screen.
   *
   * ( end auto-generated )
   *
   * @webref color:setting
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#fill(float, float, float, float)
   * @see PGraphics#noFill()
   */
  public void noStroke() {
    g.noStroke();
  }


  /**
   * ( begin auto-generated from stroke.xml )
   *
   * Sets the color used to draw lines and borders around shapes. This color
   * is either specified in terms of the RGB or HSB color depending on the
   * current <b>colorMode()</b> (the default color space is RGB, with each
   * value in the range from 0 to 255).
   * <br/> <br/>
   * When using hexadecimal notation to specify a color, use "#" or "0x"
   * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
   * digits to specify a color (the way colors are specified in HTML and
   * CSS). When using the hexadecimal notation starting with "0x", the
   * hexadecimal value must be specified with eight characters; the first two
   * characters define the alpha component and the remainder the red, green,
   * and blue components.
   * <br/> <br/>
   * The value for the parameter "gray" must be less than or equal to the
   * current maximum value as specified by <b>colorMode()</b>. The default
   * maximum value is 255.
   *
   * ( end auto-generated )
   *
   * @param rgb color value in hexadecimal notation
   * @see PGraphics#noStroke()
   * @see PGraphics#strokeWeight(float)
   * @see PGraphics#strokeJoin(int)
   * @see PGraphics#strokeCap(int)
   * @see PGraphics#fill(int, float)
   * @see PGraphics#noFill()
   * @see PGraphics#tint(int, float)
   * @see PGraphics#background(float, float, float, float)
   * @see PGraphics#colorMode(int, float, float, float, float)
   */
  public void stroke(int rgb) {
    g.stroke(rgb);
  }


  /**
   * @param alpha opacity of the stroke
   */
  public void stroke(int rgb, float alpha) {
    g.stroke(rgb, alpha);
  }


  /**
   * @param gray specifies a value between white and black
   */
  public void stroke(float gray) {
    g.stroke(gray);
  }


  public void stroke(float gray, float alpha) {
    g.stroke(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   * @webref color:setting
   */
  public void stroke(float v1, float v2, float v3) {
    g.stroke(v1, v2, v3);
  }


  public void stroke(float v1, float v2, float v3, float alpha) {
    g.stroke(v1, v2, v3, alpha);
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
   * @see PGraphics#tint(float, float, float, float)
   * @see PGraphics#image(PImage, float, float, float, float)
   */
  public void noTint() {
    g.noTint();
  }


  /**
   * ( begin auto-generated from tint.xml )
   *
   * Sets the fill value for displaying images. Images can be tinted to
   * specified colors or made transparent by setting the alpha.<br />
   * <br />
   * To make an image transparent, but not change it's color, use white as
   * the tint color and specify an alpha value. For instance, tint(255, 128)
   * will make an image 50% transparent (unless <b>colorMode()</b> has been
   * used).<br />
   * <br />
   * When using hexadecimal notation to specify a color, use "#" or "0x"
   * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
   * digits to specify a color (the way colors are specified in HTML and
   * CSS). When using the hexadecimal notation starting with "0x", the
   * hexadecimal value must be specified with eight characters; the first two
   * characters define the alpha component and the remainder the red, green,
   * and blue components.<br />
   * <br />
   * The value for the parameter "gray" must be less than or equal to the
   * current maximum value as specified by <b>colorMode()</b>. The default
   * maximum value is 255.<br />
   * <br />
   * The <b>tint()</b> function is also used to control the coloring of
   * textures in 3D.
   *
   * ( end auto-generated )
   *
   * @webref image:loading_displaying
   * @usage web_application
   * @param rgb color value in hexadecimal notation
   * @see PGraphics#noTint()
   * @see PGraphics#image(PImage, float, float, float, float)
   */
  public void tint(int rgb) {
    g.tint(rgb);
  }


  /**
   * @param alpha opacity of the image
   */
  public void tint(int rgb, float alpha) {
    g.tint(rgb, alpha);
  }


  /**
   * @param gray specifies a value between white and black
   */
  public void tint(float gray) {
    g.tint(gray);
  }


  public void tint(float gray, float alpha) {
    g.tint(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void tint(float v1, float v2, float v3) {
    g.tint(v1, v2, v3);
  }


  public void tint(float v1, float v2, float v3, float alpha) {
    g.tint(v1, v2, v3, alpha);
  }


  /**
   * ( begin auto-generated from noFill.xml )
   *
   * Disables filling geometry. If both <b>noStroke()</b> and <b>noFill()</b>
   * are called, nothing will be drawn to the screen.
   *
   * ( end auto-generated )
   *
   * @webref color:setting
   * @usage web_application
   * @see PGraphics#fill(float, float, float, float)
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#noStroke()
   */
  public void noFill() {
    g.noFill();
  }


  /**
   * ( begin auto-generated from fill.xml )
   *
   * Sets the color used to fill shapes. For example, if you run <b>fill(204,
   * 102, 0)</b>, all subsequent shapes will be filled with orange. This
   * color is either specified in terms of the RGB or HSB color depending on
   * the current <b>colorMode()</b> (the default color space is RGB, with
   * each value in the range from 0 to 255).
   * <br/> <br/>
   * When using hexadecimal notation to specify a color, use "#" or "0x"
   * before the values (e.g. #CCFFAA, 0xFFCCFFAA). The # syntax uses six
   * digits to specify a color (the way colors are specified in HTML and
   * CSS). When using the hexadecimal notation starting with "0x", the
   * hexadecimal value must be specified with eight characters; the first two
   * characters define the alpha component and the remainder the red, green,
   * and blue components.
   * <br/> <br/>
   * The value for the parameter "gray" must be less than or equal to the
   * current maximum value as specified by <b>colorMode()</b>. The default
   * maximum value is 255.
   * <br/> <br/>
   * To change the color of an image (or a texture), use tint().
   *
   * ( end auto-generated )
   *
   * @webref color:setting
   * @usage web_application
   * @param rgb color variable or hex value
   * @see PGraphics#noFill()
   * @see PGraphics#stroke(int, float)
   * @see PGraphics#noStroke()
   * @see PGraphics#tint(int, float)
   * @see PGraphics#background(float, float, float, float)
   * @see PGraphics#colorMode(int, float, float, float, float)
   */
  public void fill(int rgb) {
    g.fill(rgb);
  }


  /**
   * @param alpha opacity of the fill
   */
  public void fill(int rgb, float alpha) {
    g.fill(rgb, alpha);
  }


  /**
   * @param gray number specifying value between white and black
   */
  public void fill(float gray) {
    g.fill(gray);
  }


  public void fill(float gray, float alpha) {
    g.fill(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void fill(float v1, float v2, float v3) {
    g.fill(v1, v2, v3);
  }


  public void fill(float v1, float v2, float v3, float alpha) {
    g.fill(v1, v2, v3, alpha);
  }


  /**
   * ( begin auto-generated from ambient.xml )
   *
   * Sets the ambient reflectance for shapes drawn to the screen. This is
   * combined with the ambient light component of environment. The color
   * components set through the parameters define the reflectance. For
   * example in the default color mode, setting v1=255, v2=126, v3=0, would
   * cause all the red light to reflect and half of the green light to
   * reflect. Used in combination with <b>emissive()</b>, <b>specular()</b>,
   * and <b>shininess()</b> in setting the material properties of shapes.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:material_properties
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#emissive(float, float, float)
   * @see PGraphics#specular(float, float, float)
   * @see PGraphics#shininess(float)
   */
  public void ambient(int rgb) {
    g.ambient(rgb);
  }


  /**
   * @param gray number specifying value between white and black
   */
  public void ambient(float gray) {
    g.ambient(gray);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void ambient(float v1, float v2, float v3) {
    g.ambient(v1, v2, v3);
  }


  /**
   * ( begin auto-generated from specular.xml )
   *
   * Sets the specular color of the materials used for shapes drawn to the
   * screen, which sets the color of hightlights. Specular refers to light
   * which bounces off a surface in a perferred direction (rather than
   * bouncing in all directions like a diffuse light). Used in combination
   * with <b>emissive()</b>, <b>ambient()</b>, and <b>shininess()</b> in
   * setting the material properties of shapes.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:material_properties
   * @usage web_application
   * @param rgb color to set
   * @see PGraphics#lightSpecular(float, float, float)
   * @see PGraphics#ambient(float, float, float)
   * @see PGraphics#emissive(float, float, float)
   * @see PGraphics#shininess(float)
   */
  public void specular(int rgb) {
    g.specular(rgb);
  }


  /**
   * gray number specifying value between white and black
   *
   * @param gray value between black and white, by default 0 to 255
   */
  public void specular(float gray) {
    g.specular(gray);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void specular(float v1, float v2, float v3) {
    g.specular(v1, v2, v3);
  }


  /**
   * ( begin auto-generated from shininess.xml )
   *
   * Sets the amount of gloss in the surface of shapes. Used in combination
   * with <b>ambient()</b>, <b>specular()</b>, and <b>emissive()</b> in
   * setting the material properties of shapes.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:material_properties
   * @usage web_application
   * @param shine degree of shininess
   * @see PGraphics#emissive(float, float, float)
   * @see PGraphics#ambient(float, float, float)
   * @see PGraphics#specular(float, float, float)
   */
  public void shininess(float shine) {
    g.shininess(shine);
  }


  /**
   * ( begin auto-generated from emissive.xml )
   *
   * Sets the emissive color of the material used for drawing shapes drawn to
   * the screen. Used in combination with <b>ambient()</b>,
   * <b>specular()</b>, and <b>shininess()</b> in setting the material
   * properties of shapes.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:material_properties
   * @usage web_application
   * @param rgb color to set
   * @see PGraphics#ambient(float, float, float)
   * @see PGraphics#specular(float, float, float)
   * @see PGraphics#shininess(float)
   */
  public void emissive(int rgb) {
    g.emissive(rgb);
  }


  /**
   * gray number specifying value between white and black
   *
   * @param gray value between black and white, by default 0 to 255
   */
  public void emissive(float gray) {
    g.emissive(gray);
  }


  /**
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   */
  public void emissive(float v1, float v2, float v3) {
    g.emissive(v1, v2, v3);
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
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#noLights()
   */
  public void lights() {
    g.lights();
  }


  /**
   * ( begin auto-generated from noLights.xml )
   *
   * Disable all lighting. Lighting is turned off by default and enabled with
   * the <b>lights()</b> function. This function can be used to disable
   * lighting so that 2D geometry (which does not require lighting) can be
   * drawn after a set of lighted 3D geometry.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:lights
   * @usage web_application
   * @see PGraphics#lights()
   */
  public void noLights() {
    g.noLights();
  }


  /**
   * ( begin auto-generated from ambientLight.xml )
   *
   * Adds an ambient light. Ambient light doesn't come from a specific
   * direction, the rays have light have bounced around so much that objects
   * are evenly lit from all sides. Ambient lights are almost always used in
   * combination with other types of lights. Lights need to be included in
   * the <b>draw()</b> to remain persistent in a looping program. Placing
   * them in the <b>setup()</b> of a looping program will cause them to only
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
   * @see PGraphics#lights()
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   */
  public void ambientLight(float v1, float v2, float v3) {
    g.ambientLight(v1, v2, v3);
  }


  /**
   * @param x x-coordinate of the light
   * @param y y-coordinate of the light
   * @param z z-coordinate of the light
   */
  public void ambientLight(float v1, float v2, float v3,
                           float x, float y, float z) {
    g.ambientLight(v1, v2, v3, x, y, z);
  }


  /**
   * ( begin auto-generated from directionalLight.xml )
   *
   * Adds a directional light. Directional light comes from one direction and
   * is stronger when hitting a surface squarely and weaker if it hits at a a
   * gentle angle. After hitting a surface, a directional lights scatters in
   * all directions. Lights need to be included in the <b>draw()</b> to
   * remain persistent in a looping program. Placing them in the
   * <b>setup()</b> of a looping program will cause them to only have an
   * effect the first time through the loop. The affect of the <b>v1</b>,
   * <b>v2</b>, and <b>v3</b> parameters is determined by the current color
   * mode. The <b>nx</b>, <b>ny</b>, and <b>nz</b> parameters specify the
   * direction the light is facing. For example, setting <b>ny</b> to -1 will
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
   * @see PGraphics#lights()
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   */
  public void directionalLight(float v1, float v2, float v3,
                               float nx, float ny, float nz) {
    g.directionalLight(v1, v2, v3, nx, ny, nz);
  }


  /**
   * ( begin auto-generated from pointLight.xml )
   *
   * Adds a point light. Lights need to be included in the <b>draw()</b> to
   * remain persistent in a looping program. Placing them in the
   * <b>setup()</b> of a looping program will cause them to only have an
   * effect the first time through the loop. The affect of the <b>v1</b>,
   * <b>v2</b>, and <b>v3</b> parameters is determined by the current color
   * mode. The <b>x</b>, <b>y</b>, and <b>z</b> parameters set the position
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
   * @see PGraphics#lights()
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   */
  public void pointLight(float v1, float v2, float v3,
                         float x, float y, float z) {
    g.pointLight(v1, v2, v3, x, y, z);
  }


  /**
   * ( begin auto-generated from spotLight.xml )
   *
   * Adds a spot light. Lights need to be included in the <b>draw()</b> to
   * remain persistent in a looping program. Placing them in the
   * <b>setup()</b> of a looping program will cause them to only have an
   * effect the first time through the loop. The affect of the <b>v1</b>,
   * <b>v2</b>, and <b>v3</b> parameters is determined by the current color
   * mode. The <b>x</b>, <b>y</b>, and <b>z</b> parameters specify the
   * position of the light and <b>nx</b>, <b>ny</b>, <b>nz</b> specify the
   * direction or light. The <b>angle</b> parameter affects angle of the
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
   * @see PGraphics#lights()
   * @see PGraphics#directionalLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   */
  public void spotLight(float v1, float v2, float v3,
                        float x, float y, float z,
                        float nx, float ny, float nz,
                        float angle, float concentration) {
    g.spotLight(v1, v2, v3, x, y, z, nx, ny, nz, angle, concentration);
  }


  /**
   * ( begin auto-generated from lightFalloff.xml )
   *
   * Sets the falloff rates for point lights, spot lights, and ambient
   * lights. The parameters are used to determine the falloff with the
   * following equation:<br /><br />d = distance from light position to
   * vertex position<br />falloff = 1 / (CONSTANT + d * LINEAR + (d*d) *
   * QUADRATIC)<br /><br />Like <b>fill()</b>, it affects only the elements
   * which are created after it in the code. The default value if
   * <b>LightFalloff(1.0, 0.0, 0.0)</b>. Thinking about an ambient light with
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
   * @see PGraphics#lights()
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   * @see PGraphics#lightSpecular(float, float, float)
   */
  public void lightFalloff(float constant, float linear, float quadratic) {
    g.lightFalloff(constant, linear, quadratic);
  }


  /**
   * ( begin auto-generated from lightSpecular.xml )
   *
   * Sets the specular color for lights. Like <b>fill()</b>, it affects only
   * the elements which are created after it in the code. Specular refers to
   * light which bounces off a surface in a perferred direction (rather than
   * bouncing in all directions like a diffuse light) and is used for
   * creating highlights. The specular quality of a light interacts with the
   * specular material qualities set through the <b>specular()</b> and
   * <b>shininess()</b> functions.
   *
   * ( end auto-generated )
   *
   * @webref lights_camera:lights
   * @usage web_application
   * @param v1 red or hue value (depending on current color mode)
   * @param v2 green or saturation value (depending on current color mode)
   * @param v3 blue or brightness value (depending on current color mode)
   * @see PGraphics#specular(float, float, float)
   * @see PGraphics#lights()
   * @see PGraphics#ambientLight(float, float, float, float, float, float)
   * @see PGraphics#pointLight(float, float, float, float, float, float)
   * @see PGraphics#spotLight(float, float, float, float, float, float, float, float, float, float, float)
   */
  public void lightSpecular(float v1, float v2, float v3) {
    g.lightSpecular(v1, v2, v3);
  }


  /**
   * ( begin auto-generated from background.xml )
   *
   * The <b>background()</b> function sets the color used for the background
   * of the Processing window. The default background is light gray. In the
   * <b>draw()</b> function, the background color is used to clear the
   * display window at the beginning of each frame.
   * <br/> <br/>
   * An image can also be used as the background for a sketch, however its
   * width and height must be the same size as the sketch window. To resize
   * an image 'b' to the size of the sketch window, use b.resize(width, height).
   * <br/> <br/>
   * Images used as background will ignore the current <b>tint()</b> setting.
   * <br/> <br/>
   * It is not possible to use transparency (alpha) in background colors with
   * the main drawing surface, however they will work properly with <b>createGraphics()</b>.
   *
   * ( end auto-generated )
   *
   * <h3>Advanced</h3>
   * <p>Clear the background with a color that includes an alpha value. This can
   * only be used with objects created by createGraphics(), because the main
   * drawing surface cannot be set transparent.</p>
   * <p>It might be tempting to use this function to partially clear the screen
   * on each frame, however that's not how this function works. When calling
   * background(), the pixels will be replaced with pixels that have that level
   * of transparency. To do a semi-transparent overlay, use fill() with alpha
   * and draw a rectangle.</p>
   *
   * @webref color:setting
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#stroke(float)
   * @see PGraphics#fill(float)
   * @see PGraphics#tint(float)
   * @see PGraphics#colorMode(int)
   */
  public void background(int rgb) {
    g.background(rgb);
  }


  /**
   * @param alpha opacity of the background
   */
  public void background(int rgb, float alpha) {
    g.background(rgb, alpha);
  }


  /**
   * @param gray specifies a value between white and black
   */
  public void background(float gray) {
    g.background(gray);
  }


  public void background(float gray, float alpha) {
    g.background(gray, alpha);
  }


  /**
   * @param v1 red or hue value (depending on the current color mode)
   * @param v2 green or saturation value (depending on the current color mode)
   * @param v3 blue or brightness value (depending on the current color mode)
   */
  public void background(float v1, float v2, float v3) {
    g.background(v1, v2, v3);
  }


  public void background(float v1, float v2, float v3, float alpha) {
    g.background(v1, v2, v3, alpha);
  }


  /**
   * @webref color:setting
   */
  public void clear() {
    g.clear();
  }


  /**
   * Takes an RGB or ARGB image and sets it as the background.
   * The width and height of the image must be the same size as the sketch.
   * Use image.resize(width, height) to make short work of such a task.<br/>
   * <br/>
   * Note that even if the image is set as RGB, the high 8 bits of each pixel
   * should be set opaque (0xFF000000) because the image data will be copied
   * directly to the screen, and non-opaque background images may have strange
   * behavior. Use image.filter(OPAQUE) to handle this easily.<br/>
   * <br/>
   * When using 3D, this will also clear the zbuffer (if it exists).
   *
   * @param image PImage to set as background (must be same size as the sketch window)
   */
  public void background(PImage image) {
    g.background(image);
  }


  /**
   * ( begin auto-generated from colorMode.xml )
   *
   * Changes the way Processing interprets color data. By default, the
   * parameters for <b>fill()</b>, <b>stroke()</b>, <b>background()</b>, and
   * <b>color()</b> are defined by values between 0 and 255 using the RGB
   * color model. The <b>colorMode()</b> function is used to change the
   * numerical range used for specifying colors and to switch color systems.
   * For example, calling <b>colorMode(RGB, 1.0)</b> will specify that values
   * are specified between 0 and 1. The limits for defining colors are
   * altered by setting the parameters range1, range2, range3, and range 4.
   *
   * ( end auto-generated )
   *
   * @webref color:setting
   * @usage web_application
   * @param mode Either RGB or HSB, corresponding to Red/Green/Blue and Hue/Saturation/Brightness
   * @see PGraphics#background(float)
   * @see PGraphics#fill(float)
   * @see PGraphics#stroke(float)
   */
  public void colorMode(int mode) {
    g.colorMode(mode);
  }


  /**
   * @param max range for all color elements
   */
  public void colorMode(int mode, float max) {
    g.colorMode(mode, max);
  }


  /**
   * @param max1 range for the red or hue depending on the current color mode
   * @param max2 range for the green or saturation depending on the current color mode
   * @param max3 range for the blue or brightness depending on the current color mode
   */
  public void colorMode(int mode, float max1, float max2, float max3) {
    g.colorMode(mode, max1, max2, max3);
  }


  /**
   * @param maxA range for the alpha
   */
  public void colorMode(int mode,
                        float max1, float max2, float max3, float maxA) {
    g.colorMode(mode, max1, max2, max3, maxA);
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
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   */
  public final float alpha(int rgb) {
    return g.alpha(rgb);
  }


  /**
   * ( begin auto-generated from red.xml )
   *
   * Extracts the red value from a color, scaled to match current
   * <b>colorMode()</b>. This value is always returned as a  float so be
   * careful not to assign it to an int value.<br /><br />The red() function
   * is easy to use and undestand, but is slower than another technique. To
   * achieve the same results when working in <b>colorMode(RGB, 255)</b>, but
   * with greater speed, use the &gt;&gt; (right shift) operator with a bit
   * mask. For example, the following two lines of code are equivalent:<br
   * /><pre>float r1 = red(myColor);<br />float r2 = myColor &gt;&gt; 16
   * &amp; 0xFF;</pre>
   *
   * ( end auto-generated )
   *
   * @webref color:creating_reading
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   * @see_external rightshift
   */
  public final float red(int rgb) {
    return g.red(rgb);
  }


  /**
   * ( begin auto-generated from green.xml )
   *
   * Extracts the green value from a color, scaled to match current
   * <b>colorMode()</b>. This value is always returned as a  float so be
   * careful not to assign it to an int value.<br /><br />The <b>green()</b>
   * function is easy to use and undestand, but is slower than another
   * technique. To achieve the same results when working in <b>colorMode(RGB,
   * 255)</b>, but with greater speed, use the &gt;&gt; (right shift)
   * operator with a bit mask. For example, the following two lines of code
   * are equivalent:<br /><pre>float r1 = green(myColor);<br />float r2 =
   * myColor &gt;&gt; 8 &amp; 0xFF;</pre>
   *
   * ( end auto-generated )
   *
   * @webref color:creating_reading
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   * @see_external rightshift
   */
  public final float green(int rgb) {
    return g.green(rgb);
  }


  /**
   * ( begin auto-generated from blue.xml )
   *
   * Extracts the blue value from a color, scaled to match current
   * <b>colorMode()</b>. This value is always returned as a  float so be
   * careful not to assign it to an int value.<br /><br />The <b>blue()</b>
   * function is easy to use and undestand, but is slower than another
   * technique. To achieve the same results when working in <b>colorMode(RGB,
   * 255)</b>, but with greater speed, use a bit mask to remove the other
   * color components. For example, the following two lines of code are
   * equivalent:<br /><pre>float r1 = blue(myColor);<br />float r2 = myColor
   * &amp; 0xFF;</pre>
   *
   * ( end auto-generated )
   *
   * @webref color:creating_reading
   * @usage web_application
   * @param rgb any value of the color datatype
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   * @see_external rightshift
   */
  public final float blue(int rgb) {
    return g.blue(rgb);
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
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#saturation(int)
   * @see PGraphics#brightness(int)
   */
  public final float hue(int rgb) {
    return g.hue(rgb);
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
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#brightness(int)
   */
  public final float saturation(int rgb) {
    return g.saturation(rgb);
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
   * @see PGraphics#red(int)
   * @see PGraphics#green(int)
   * @see PGraphics#blue(int)
   * @see PGraphics#alpha(int)
   * @see PGraphics#hue(int)
   * @see PGraphics#saturation(int)
   */
  public final float brightness(int rgb) {
    return g.brightness(rgb);
  }


  /**
   * @nowebref
   * Interpolate between two colors. Like lerp(), but for the
   * individual color components of a color supplied as an int value.
   */
  static public int lerpColor(int c1, int c2, float amt, int mode) {
    return PGraphics.lerpColor(c1, c2, amt, mode);
  }


  /**
   * Display a warning that the specified method is only available with 3D.
   * @param method The method name (no parentheses)
   */
  static public void showDepthWarning(String method) {
    PGraphics.showDepthWarning(method);
  }


  /**
   * Display a warning that the specified method that takes x, y, z parameters
   * can only be used with x and y parameters in this renderer.
   * @param method The method name (no parentheses)
   */
  static public void showDepthWarningXYZ(String method) {
    PGraphics.showDepthWarningXYZ(method);
  }


  /**
   * Display a warning that the specified method is simply unavailable.
   */
  static public void showMethodWarning(String method) {
    PGraphics.showMethodWarning(method);
  }


  /**
   * Error that a particular variation of a method is unavailable (even though
   * other variations are). For instance, if vertex(x, y, u, v) is not
   * available, but vertex(x, y) is just fine.
   */
  static public void showVariationWarning(String str) {
    PGraphics.showVariationWarning(str);
  }


  /**
   * Display a warning that the specified method is not implemented, meaning
   * that it could be either a completely missing function, although other
   * variations of it may still work properly.
   */
  static public void showMissingWarning(String method) {
    PGraphics.showMissingWarning(method);
  }


  /**
   * ( begin auto-generated from PImage_get.xml )
   *
   * Reads the color of any pixel or grabs a section of an image. If no
   * parameters are specified, the entire image is returned. Use the <b>x</b>
   * and <b>y</b> parameters to get the value of one pixel. Get a section of
   * the display window by specifying an additional <b>width</b> and
   * <b>height</b> parameter. When getting an image, the <b>x</b> and
   * <b>y</b> parameters define the coordinates for the upper-left corner of
   * the image, regardless of the current <b>imageMode()</b>.<br />
   * <br />
   * If the pixel requested is outside of the image window, black is
   * returned. The numbers returned are scaled according to the current color
   * ranges, but only RGB values are returned by this function. For example,
   * even though you may have drawn a shape with <b>colorMode(HSB)</b>, the
   * numbers returned will be in RGB format.<br />
   * <br />
   * Getting the color of a single pixel with <b>get(x, y)</b> is easy, but
   * not as fast as grabbing the data directly from <b>pixels[]</b>. The
   * equivalent statement to <b>get(x, y)</b> using <b>pixels[]</b> is
   * <b>pixels[y*width+x]</b>. See the reference for <b>pixels[]</b> for more information.
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
   * <P>
   * If the image is in ALPHA format, this returns a white with its
   * alpha value set.
   * <P>
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
   * @see PApplet#set(int, int, int)
   * @see PApplet#pixels
   * @see PApplet#copy(PImage, int, int, int, int, int, int, int, int)
   */
  public int get(int x, int y) {
    return g.get(x, y);
  }


  /**
   * @param w width of pixel rectangle to get
   * @param h height of pixel rectangle to get
   */
  public PImage get(int x, int y, int w, int h) {
    return g.get(x, y, w, h);
  }


  /**
   * Returns a copy of this PImage. Equivalent to get(0, 0, width, height).
   * Deprecated, just use copy() instead.
   */
  public PImage get() {
    return g.get();
  }


  public PImage copy() {
    return g.copy();
  }


  /**
   * ( begin auto-generated from PImage_set.xml )
   *
   * Changes the color of any pixel or writes an image directly into the
   * display window.<br />
   * <br />
   * The <b>x</b> and <b>y</b> parameters specify the pixel to change and the
   * <b>color</b> parameter specifies the color value. The color parameter is
   * affected by the current color mode (the default is RGB values from 0 to
   * 255). When setting an image, the <b>x</b> and <b>y</b> parameters define
   * the coordinates for the upper-left corner of the image, regardless of
   * the current <b>imageMode()</b>.
   * <br /><br />
   * Setting the color of a single pixel with <b>set(x, y)</b> is easy, but
   * not as fast as putting the data directly into <b>pixels[]</b>. The
   * equivalent statement to <b>set(x, y, #000000)</b> using <b>pixels[]</b>
   * is <b>pixels[y*width+x] = #000000</b>. See the reference for
   * <b>pixels[]</b> for more information.
   *
   * ( end auto-generated )
   *
   * @webref image:pixels
   * @brief writes a color to any pixel or writes an image into another
   * @usage web_application
   * @param x x-coordinate of the pixel
   * @param y y-coordinate of the pixel
   * @param c any value of the color datatype
   * @see PImage#get(int, int, int, int)
   * @see PImage#pixels
   * @see PImage#copy(PImage, int, int, int, int, int, int, int, int)
   */
  public void set(int x, int y, int c) {
    g.set(x, y, c);
  }


  /**
   * <h3>Advanced</h3>
   * Efficient method of drawing an image's pixels directly to this surface.
   * No variations are employed, meaning that any scale, tint, or imageMode
   * settings will be ignored.
   *
   * @param img image to copy into the original image
   */
  public void set(int x, int y, PImage img) {
    g.set(x, y, img);
  }


  /**
   * ( begin auto-generated from PImage_mask.xml )
   *
   * Masks part of an image from displaying by loading another image and
   * using it as an alpha channel. This mask image should only contain
   * grayscale data, but only the blue color channel is used. The mask image
   * needs to be the same size as the image to which it is applied.<br />
   * <br />
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
   */
  public void mask(PImage img) {
    g.mask(img);
  }


  public void filter(int kind) {
    g.filter(kind);
  }


  /**
   * ( begin auto-generated from PImage_filter.xml )
   *
   * Filters an image as defined by one of the following modes:<br /><br
   * />THRESHOLD - converts the image to black and white pixels depending if
   * they are above or below the threshold defined by the level parameter.
   * The level must be between 0.0 (black) and 1.0(white). If no level is
   * specified, 0.5 is used.<br />
   * <br />
   * GRAY - converts any colors in the image to grayscale equivalents<br />
   * <br />
   * INVERT - sets each pixel to its inverse value<br />
   * <br />
   * POSTERIZE - limits each channel of the image to the number of colors
   * specified as the level parameter<br />
   * <br />
   * BLUR - executes a Guassian blur with the level parameter specifying the
   * extent of the blurring. If no level parameter is used, the blur is
   * equivalent to Guassian blur of radius 1<br />
   * <br />
   * OPAQUE - sets the alpha channel to entirely opaque<br />
   * <br />
   * ERODE - reduces the light areas with the amount defined by the level
   * parameter<br />
   * <br />
   * DILATE - increases the light areas with the amount defined by the level parameter
   *
   * ( end auto-generated )
   *
   * <h3>Advanced</h3>
   * Method to apply a variety of basic filters to this image.
   * <P>
   * <UL>
   * <LI>filter(BLUR) provides a basic blur.
   * <LI>filter(GRAY) converts the image to grayscale based on luminance.
   * <LI>filter(INVERT) will invert the color components in the image.
   * <LI>filter(OPAQUE) set all the high bits in the image to opaque
   * <LI>filter(THRESHOLD) converts the image to black and white.
   * <LI>filter(DILATE) grow white/light areas
   * <LI>filter(ERODE) shrink white/light areas
   * </UL>
   * Luminance conversion code contributed by
   * <A HREF="http://www.toxi.co.uk">toxi</A>
   * <P/>
   * Gaussian blur code contributed by
   * <A HREF="http://incubator.quasimondo.com">Mario Klingemann</A>
   *
   * @webref image:pixels
   * @brief Converts the image to grayscale or black and white
   * @usage web_application
   * @param kind Either THRESHOLD, GRAY, OPAQUE, INVERT, POSTERIZE, BLUR, ERODE, or DILATE
   * @param param unique for each, see above
   */
  public void filter(int kind, float param) {
    g.filter(kind, param);
  }


  /**
   * ( begin auto-generated from PImage_copy.xml )
   *
   * Copies a region of pixels from one image into another. If the source and
   * destination regions aren't the same size, it will automatically resize
   * source pixels to fit the specified target region. No alpha information
   * is used in the process, however if the source image has an alpha channel
   * set, it will be copied as well.
   * <br /><br />
   * As of release 0149, this function ignores <b>imageMode()</b>.
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
   * @see PGraphics#alpha(int)
   * @see PImage#blend(PImage, int, int, int, int, int, int, int, int, int)
   */
  public void copy(int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    g.copy(sx, sy, sw, sh, dx, dy, dw, dh);
  }


  /**
   * @param src an image variable referring to the source image.
   */
  public void copy(PImage src,
                   int sx, int sy, int sw, int sh,
                   int dx, int dy, int dw, int dh) {
    g.copy(src, sx, sy, sw, sh, dx, dy, dw, dh);
  }


  public void blend(int sx, int sy, int sw, int sh,
                    int dx, int dy, int dw, int dh, int mode) {
    g.blend(sx, sy, sw, sh, dx, dy, dw, dh, mode);
  }


  /**
   * ( begin auto-generated from PImage_blend.xml )
   *
   * Blends a region of pixels into the image specified by the <b>img</b>
   * parameter. These copies utilize full alpha channel support and a choice
   * of the following modes to blend the colors of source pixels (A) with the
   * ones of pixels in the destination image (B):<br />
   * <br />
   * BLEND - linear interpolation of colours: C = A*factor + B<br />
   * <br />
   * ADD - additive blending with white clip: C = min(A*factor + B, 255)<br />
   * <br />
   * SUBTRACT - subtractive blending with black clip: C = max(B - A*factor,
   * 0)<br />
   * <br />
   * DARKEST - only the darkest colour succeeds: C = min(A*factor, B)<br />
   * <br />
   * LIGHTEST - only the lightest colour succeeds: C = max(A*factor, B)<br />
   * <br />
   * DIFFERENCE - subtract colors from underlying image.<br />
   * <br />
   * EXCLUSION - similar to DIFFERENCE, but less extreme.<br />
   * <br />
   * MULTIPLY - Multiply the colors, result will always be darker.<br />
   * <br />
   * SCREEN - Opposite multiply, uses inverse values of the colors.<br />
   * <br />
   * OVERLAY - A mix of MULTIPLY and SCREEN. Multiplies dark values,
   * and screens light values.<br />
   * <br />
   * HARD_LIGHT - SCREEN when greater than 50% gray, MULTIPLY when lower.<br />
   * <br />
   * SOFT_LIGHT - Mix of DARKEST and LIGHTEST.
   * Works like OVERLAY, but not as harsh.<br />
   * <br />
   * DODGE - Lightens light tones and increases contrast, ignores darks.
   * Called "Color Dodge" in Illustrator and Photoshop.<br />
   * <br />
   * BURN - Darker areas are applied, increasing contrast, ignores lights.
   * Called "Color Burn" in Illustrator and Photoshop.<br />
   * <br />
   * All modes use the alpha information (highest byte) of source image
   * pixels as the blending factor. If the source and destination regions are
   * different sizes, the image will be automatically resized to match the
   * destination size. If the <b>srcImg</b> parameter is not used, the
   * display window is used as the source image.<br />
   * <br />
   * As of release 0149, this function ignores <b>imageMode()</b>.
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
   * @see PApplet#alpha(int)
   * @see PImage#copy(PImage, int, int, int, int, int, int, int, int)
   * @see PImage#blendColor(int,int,int)
   */
  public void blend(PImage src,
                    int sx, int sy, int sw, int sh,
                    int dx, int dy, int dw, int dh, int mode) {
    g.blend(src, sx, sy, sw, sh, dx, dy, dw, dh, mode);
  }
}
