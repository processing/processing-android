import processing.ar.*;
import processing.ar.render.*;
PObject arObj;
public void setup() {
    fullScreen(AR);
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
    if(mousePressed){
      //Placing the AR object on encountering touch events
      arObj.place();
    }
}