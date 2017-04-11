import processing.vr.*;

float[] r1, r2;

void setup() {
  fullScreen(PVR.STEREO);  
  //size(1920, 1080, P3D);
  r1 = new float[100]; 
  r2 = new float[100];
  for (int i = 0; i < 100; i++) {
    r1[i] = random(0, 1);
    r2[i] = random(0, 1);
  }  
}

void draw() {
  background(150);
  PGraphicsVR pvr = (PGraphicsVR)g; 
  
  // Some lights
  pointLight(255, 255, 255, pvr.cameraX, pvr.cameraY, pvr.cameraZ);

  // Floor
  beginShape(QUADS); 
  fill(255, 0, 0);
  normal(0, 0, +1);
  vertex(-width/2, -500, -width/2);
  fill(0, 0, 255);
  vertex(-width/2, -500, +width/2);
  vertex(+width/2, -500, +width/2);
  fill(255, 0, 0);
  vertex(+width/2, -500, -width/2);
  endShape();
  
  pushMatrix();
  rotateY(millis()/1000.0);
  fill(220);
  box(200);
  popMatrix();
  
  // Red box, X axis
  pushMatrix();
  translate(200, 0, 0);
  fill(255, 0, 0);
  box(100);
  popMatrix();
  
  // Green box, Y axis 
  pushMatrix();
  translate(0, 200, 0);
  fill(0, 255, 0);
  box(100);
  popMatrix();
  
  // Blue box, Z axis
  pushMatrix();
  translate(0, 0, 200);
  fill(0, 0, 255);
  box(100);
  popMatrix();
  
  // Place the aim at 300 units from the camera eye
  float d = 300;
  float x = pvr.cameraX + d * pvr.forwardVector[0];
  float y = pvr.cameraY + d * pvr.forwardVector[1];
  float z = pvr.cameraZ + d * pvr.forwardVector[2];
  stroke(200, 50, 150);
  strokeWeight(50);
  point(x, y, z);
  noStroke();
  popMatrix();
}