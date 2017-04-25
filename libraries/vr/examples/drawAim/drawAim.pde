import processing.vr.*;


void setup() {
  fullScreen(PVR.STEREO);
}

void draw() {
  background(150);
  PGraphicsVR pvr = (PGraphicsVR)g; 

  // Some lights
  pointLight(255, 255, 255, pvr.cameraX, pvr.cameraY, pvr.cameraZ);
  
  translate(width/2, height/2);

  noStroke();

  // Floor
  beginShape(QUADS); 
  fill(255, 0, 0);
  normal(0, 0, -1);
  vertex(-width/2, +500, -width/2);
  fill(0, 0, 255);
  vertex(-width/2, +500, +width/2);
  vertex(+width/2, +500, +width/2);
  fill(255, 0, 0);
  vertex(+width/2, +500, -width/2);
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
  
  // Place the aim at 100 units from the camera eye
  float d = 100;
  float x = d * pvr.forwardX;
  float y = d * pvr.forwardY;
  float z = pvr.cameraZ + d * pvr.forwardZ;
  stroke(255, 200);
  strokeWeight(50);
  point(x, y, z);  
}