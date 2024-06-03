/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-21 The Processing Foundation

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
 * IMPORTANT NOTICE: None of the methods and inner classes in TouchEvent are
 * part of the Processing API. Don't use them! They might be changed or removed
 * without notice.
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
  protected float[] pointerArea;
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
    pointerArea = new float[n];
    pointerPressure = new float[n];
 }


 public void setPointer(int idx, int id, float x, float y, float a, float p) {
   pointerId[idx] = id;
   pointerX[idx] = x;
   pointerY[idx] = y;
   pointerArea[idx] = a;
   pointerPressure[idx] = p;
 }


 public int getNumPointers() {
   return numPointers;
 }


 public Pointer getPointer(int idx) {
   Pointer pt = new Pointer();
   pt.id = pointerId[idx];
   pt.x = pointerX[idx];
   pt.y = pointerY[idx];
   pt.area = pointerArea[idx];
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


 public float getPointerArea(int idx) {
   return pointerArea[idx];
 }


 public float getPointerPressure(int idx) {
   return pointerPressure[idx];
 }


 public int getButton() {
   return button;
 }


 public Pointer[] getTouches(Pointer[] touches) {
   if (touches == null || touches.length != numPointers) {
     touches = new Pointer[numPointers];
     for (int idx = 0; idx < numPointers; idx++) {
       touches[idx] = new Pointer();
     }
   }
   for (int idx = 0; idx < numPointers; idx++) {
     touches[idx].id = pointerId[idx];
     touches[idx].x = pointerX[idx];
     touches[idx].y = pointerY[idx];
     touches[idx].area = pointerArea[idx];
     touches[idx].pressure = pointerPressure[idx];
   }
   return touches;
 }


  public class Pointer {
    public int id;
    public float x, y;
    public float area;
    public float pressure;
  }
}