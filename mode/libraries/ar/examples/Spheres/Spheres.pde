import processing.ar.*;

float angle = 0;

void setup() {
  fullScreen(AUGMENT);
  PAR.planeColor(0xB4E7FF);
}

void draw() {
  // At this point, there is no much AR-specific API, but you can get the AR Core session, frame,
  // and camera to extract more information about the AR scene.
//    PSurfaceAR surface = (PSurfaceAR) getSurface();
//    surface.camera.getPose();
//    surface.frame.getLightEstimate();

  background(0);
  lights();
  fill(0xFCB736);
  noStroke();
  sphere(0.10);
  rotateZ(angle);
  translate(0, 0.3,0);
  sphere(0.05);
  angle += 0.1;
}
