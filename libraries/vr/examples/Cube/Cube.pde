import processing.vr.*;

void setup() {
  fullScreen(PVR.STEREO);
}

void draw() {
//  PGraphicsVR pvr = (PGraphicsVR)g;
//  if (pvr.eyeType == PVR.LEFT) {
//    background(200, 50, 50);
//  } else if (pvr.eyeType == PVR.RIGHT) {
//    background(50, 50, 200);
//  } else if (pvr.eyeType == PVR.MONOCULAR) {
//    background(50, 200, 50);
//  }
      
  background(157);
  lights();
//  translate(width/2, height/2);
  rotateX(frameCount * 0.01f);
  rotateY(frameCount * 0.01f);  
  box(500);
}