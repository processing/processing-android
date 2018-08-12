import processing.ar.*;
import processing.ar.render.*;
PShape sphere;
public void setup() {
    fullScreen(AR);
    sphere = createShape(SPHERE, 400);
}
public void draw() {
    lights();
    background(0);
    PPlane.setPlaneColor(0x00BCD4FF);
    shape(sphere);
}