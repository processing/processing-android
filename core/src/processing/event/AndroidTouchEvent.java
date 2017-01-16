package processing.event;

import android.view.MotionEvent;

// this class is hiding inside PApplet for now,
// until I have a chance to get the API right inside TouchEvent.
public class AndroidTouchEvent extends TouchEvent {
  MotionEvent event;
  int action;
  int numPointers;
  float[] motionX, motionY;
  int[] motionID;
  float[] motionPressure;
  int[] mouseX, mouseY;

  PPointer[] pointers;

  public AndroidTouchEvent(Object nativeObject, long millis, int action, int modifiers) {
    super(nativeObject, millis, action, modifiers);
    this.action = action;
    this.millis = millis;
    this.modifiers = modifiers;
    event = (MotionEvent) nativeObject;
  }

//  void setAction(int action) {
//    this.action = action;
//  }

 public void setNumPointers(int n) {
    numPointers = n;
    motionX = new float[n];
    motionY = new float[n];
    motionPressure = new float[n];
    motionID = new int[n];
    mouseX = new int[n];
    mouseY = new int[n];
  }

 public void setPointers(MotionEvent event) {
    for (int ptIdx = 0; ptIdx < numPointers; ptIdx++) {
      motionX[ptIdx] = event.getX(ptIdx);
      motionY[ptIdx] = event.getY(ptIdx);
      motionID[ptIdx] = event.getPointerId(ptIdx);
      motionPressure[ptIdx] = event.getPressure(ptIdx);  // should this be constrained?
      mouseX[ptIdx] = (int) motionX[ptIdx];  //event.getRawX();
      mouseY[ptIdx] = (int) motionY[ptIdx];  //event.getRawY();
    }
  }

  // Sets the pointers for the historical event histIdx
  public void setPointers(MotionEvent event, int hisIdx) {
    for (int ptIdx = 0; ptIdx < numPointers; ptIdx++) {
      motionX[ptIdx] = event.getHistoricalX(ptIdx, hisIdx);
      motionY[ptIdx] = event.getHistoricalY(ptIdx, hisIdx);
      motionID[ptIdx] = event.getPointerId(ptIdx);
      motionPressure[ptIdx] = event.getHistoricalPressure(ptIdx, hisIdx);  // should this be constrained?
      mouseX[ptIdx] = (int) motionX[ptIdx];  //event.getRawX();
      mouseY[ptIdx] = (int) motionY[ptIdx];  //event.getRawY();
    }
  }

  public PPointer[] getTouches() {
    PPointer[] touches = new PPointer[numPointers];
    for (int i = 0; i < numPointers; i++) {
      touches[i] = new PPointer();
      touches[i].id = motionID[i];
      touches[i].x = motionX[i];
      touches[i].y = motionY[i];
//      touches[i].px = motionX[i]; ??
//      touches[i].py = motionY[i]; ??
      touches[i].pressure = motionPressure[i];
    }
    return touches;
  }

  public class PPointer {
    public float x, y;
    public float px, py;
    public float pressure;
    public int id;
  }
}