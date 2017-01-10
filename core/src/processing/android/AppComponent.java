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

package processing.android;

import android.content.Intent;
import processing.core.PApplet;
import processing.core.PConstants;

abstract public interface AppComponent extends PConstants {
  static public final int FRAGMENT  = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE = 2;

  public void initDimensions();
  public int getDisplayWidth();
  public int getDisplayHeight();
  public float getDisplayDensity();
  public int getKind();
  public void setSketch(PApplet sketch);
  public PApplet getSketch();

  public void startActivity(Intent intent);

  public void requestDraw();
  public boolean canDraw();

  public void dispose();
  public void onPermissionsGranted();
}
