/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2013-16 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation, version 2.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty
  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  See the GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.data;


/**
 * Internal sorter used by several data classes.
 * Advanced users only, not official API.
 */
public abstract class Sort implements Runnable {

  public Sort() { }


  public void run() {
    int c = size();
    if (c > 1) {
      sort(0, c - 1);
    }
  }


  protected void sort(int i, int j) {
    int pivotIndex = (i+j)/2;
    swap(pivotIndex, j);
    int k = partition(i-1, j);
    swap(k, j);
    if ((k-i) > 1) sort(i, k-1);
    if ((j-k) > 1) sort(k+1, j);
  }


  protected int partition(int left, int right) {
    int pivot = right;
    do {
      while (compare(++left, pivot) < 0) { }
      while ((right != 0) && (compare(--right, pivot) > 0)) { }
      swap(left, right);
    } while (left < right);
    swap(left, right);
    return left;
  }


  abstract public int size();
  abstract public float compare(int a, int b);
  abstract public void swap(int a, int b);
}