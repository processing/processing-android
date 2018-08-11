package wallpaper;
        
import processing.android.PWallpaper;
import processing.core.PApplet;
        
public class MainService extends PWallpaper {  
  @Override
  public PApplet createSketch() {
    // Uncomment the following line when debugging:
//    android.os.Debug.waitForDebugger();
    PApplet sketch = new Sketch();    
    return sketch;
  }
}
