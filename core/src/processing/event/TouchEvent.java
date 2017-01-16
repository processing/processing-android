/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-16 The Processing Foundation

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

package processing.event;

/*
http://developer.android.com/guide/topics/ui/ui-events.html
http://developer.android.com/reference/android/view/MotionEvent.html
http://android-developers.blogspot.com/2010/06/making-sense-of-multitouch.html
http://www.techrepublic.com/blog/app-builder/use-androids-gesture-detector-to-translate-a-swipe-into-an-event/1577
*/
public class TouchEvent extends Event {
  static public final int START = 1;
  static public final int END = 2;
  static public final int CANCEL = 3;
  static public final int MOVE = 4;

  protected int action;

  protected int button;
  protected int numPointers;
  protected int[] pointerId;
  protected float[] pointerX;
  protected float[] pointerY;
  protected float[] pointerSize;
  protected float[] pointerPressure;

  public TouchEvent(Object nativeObject, long millis, int action, int modifiers,
                    int button) {
    super(nativeObject, millis, action, modifiers);
    this.flavor = TOUCH;
    this.button = button;
  }

  public void setNumPointers(int n) {
    numPointers = n;
    pointerId = new int[n];
    pointerX = new float[n];
    pointerY = new float[n];
    pointerSize = new float[n];
    pointerPressure = new float[n];
 }


 public void setPointer(int idx, int id, float x, float y, float s, float p) {
   pointerId[idx] = id;
   pointerX[idx] = x;
   pointerY[idx] = y;
   pointerSize[idx] = s;
   pointerPressure[idx] = p;
 }


 public int getNumPointers() {
   return numPointers;
 }


 public PPointer getPointer(int idx) {
   PPointer pt = new PPointer();
   pt.id = pointerId[idx];
   pt.x = pointerX[idx];
   pt.y = pointerY[idx];
   pt.size = pointerSize[idx];
   pt.pressure = pointerPressure[idx];
   return pt;
 }


 public int getPointerId(int idx) {
   return pointerId[idx];
 }


 public float getPointerX(int idx) {
   return pointerX[idx];
 }


 public float getPointerY(int idx) {
   return pointerY[idx];
 }


 public float getPointerSize(int idx) {
   return pointerSize[idx];
 }


 public float getPointerPressure(int idx) {
   return pointerPressure[idx];
 }


 public int getButton() {
   return button;
 }


 public PPointer[] getTouches(PPointer[] touches) {
   if (touches == null || touches.length != numPointers) {
     touches = new PPointer[numPointers];
     for (int idx = 0; idx < numPointers; idx++) {
       touches[idx] = new PPointer();
     }
   }
   for (int idx = 0; idx < numPointers; idx++) {
     touches[idx].id = pointerId[idx];
     touches[idx].x = pointerX[idx];
     touches[idx].y = pointerY[idx];
     touches[idx].size = pointerSize[idx];
     touches[idx].pressure = pointerPressure[idx];
   }
   return touches;
 }


  public class PPointer {
    public int id;
    public float x, y;
    public float size;
    public float pressure;
  }
}
