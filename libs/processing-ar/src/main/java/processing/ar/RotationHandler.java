package processing.ar;


import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.WindowManager;

import com.google.ar.core.Session;


public class RotationHandler implements DisplayManager.DisplayListener {
  private boolean viewportChanged;
  private int viewportWidth;
  private int viewportHeight;
  private final Context context;
  private final Display display;

  public RotationHandler(Context context) {
    this.context = context;
    WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    display = windowManager.getDefaultDisplay();
  }

  public void onResume() {
    ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).registerDisplayListener(this, null);
  }

  public void onPause() {
    ((DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE)).unregisterDisplayListener(this);
  }

  public void onSurfaceChanged(int width, int height) {
    viewportWidth = width;
    viewportHeight = height;
    viewportChanged = true;
  }

  public void updateSessionIfNeeded(Session session) {
    if (viewportChanged) {
      int displayRotation = display.getRotation();
      session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight);
      viewportChanged = false;
    }
  }

  public int getRotation() {
    return display.getRotation();
  }

  @Override
  public void onDisplayAdded(int displayId) {
  }

  @Override
  public void onDisplayRemoved(int displayId) {
  }

  @Override
  public void onDisplayChanged(int displayId) {
    viewportChanged = true;
  }
}