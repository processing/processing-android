import processing.ar.*;
import processing.ar.render.*;
PShape cube;
public void setup() {
    fullScreen(AR);
    cube = createShape(BOX, 0.25f);
}
public void draw() {
    lights();
    background(0);
    PPlane.setPlaneColor(0x00BCD4FF);
    shape(cube);
}