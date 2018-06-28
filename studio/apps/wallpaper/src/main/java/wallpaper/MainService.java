package wallpaper;
        
import processing.android.PWallpaper;
import processing.core.PApplet;
        
public class MainService extends PWallpaper {  
  @Override
  public PApplet createSketch() {
    PApplet sketch = new Sketch();    
    return sketch;
  }
}
