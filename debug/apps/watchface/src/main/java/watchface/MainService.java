package watchface;
        
import processing.android.PWatchFaceCanvas;
//import processing.android.PWatchFaceGLES;
import processing.core.PApplet;

// The service needs to extend PWatchFaceCanvas if the renderer in the sketch is P2D or P3D.
//public class MainService extends PWatchFaceGLES {
public class MainService extends PWatchFaceCanvas {
  @Override
  public PApplet createSketch() {
    PApplet sketch = new Sketch();    
    return sketch;
  }
}
