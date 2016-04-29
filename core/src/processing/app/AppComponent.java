package processing.app;

import android.content.Intent;
import processing.core.PApplet;
import processing.core.PConstants;

abstract public interface AppComponent extends PConstants {
  static public final int FRAGMENT  = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE_CANVAS = 2;
  static public final int WATCHFACE_GLES = 3;

  public void initDimensions();
  public int getWidth();
  public int getHeight();
  public int getKind();
  public void setSketch(PApplet sketch);

  public void startActivity(Intent intent);

  public void requestDraw();
  public boolean canDraw();
}
