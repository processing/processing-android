package arscene;

import processing.core.PApplet;
import processing.ar.*;
import processing.ar.render.*;
import processing.core.PShape;

public class Sketch extends PApplet {
  PShape sphere;
  PObject arObj;
  
  public void settings() {
    fullScreen(ARCORE);
  }

  public void setup() {
    // I think this should go here but not 100% sure
//    if (!hasPermission("android.permission.CAMERA")) {
//      requestPermission("android.permission.CAMERA");
//    }
//    sphere = createShape(SPHERE, 400);

    //Initialisation of AR Object
    arObj = new PObject();
    //Setting Color of the detected plane - BLUE in this case
    PPlane.setPlaneColor(0x00BCD4FF);
    //Applying custom textures to the detected plane
    PPlane.setPlaneTexture("triangle.png");
    //Loading .obj file of the AR object
    arObj.load("model.obj","grey.png");
  }

  public void draw() {
    lights();
    background(0);
//    PPlane.setPlaneColor(0x00BCD4FF);
//    shape(sphere);

    fill(2555, 0, 0);
    // Pulsating box to test scale...
//    box(map(sin(frameCount/100.0f), -1, 1, 0, 500));
    box(0.25f);

//    arObj.place();
  }
}
