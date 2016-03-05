package processing.app;

import android.content.Intent;
import processing.core.PConstants;

abstract public interface PContainer extends PConstants {
  static public final int FRAGMENT  = 0;
  static public final int WALLPAPER = 1;
  static public final int WATCHFACE = 2;
  static public final int CARDBOARD = 3;

  public void initDimensions();
  public int getWidth();
  public int getHeight();
  public int getKind();

  public void startActivity(Intent intent);
}
