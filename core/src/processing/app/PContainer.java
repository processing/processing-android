package processing.app;

import android.content.Intent;
import android.view.SurfaceHolder;
import processing.core.PApplet;
import processing.core.PConstants;

abstract public interface PContainer extends PConstants {
  static public final int FRAGMENT  = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE_GLES = 2;

  public void initDimensions();
  public int getWidth();
  public int getHeight();
  public int getKind();
  public void setSketch(PApplet sketch);

  public void startActivity(Intent intent);
}
