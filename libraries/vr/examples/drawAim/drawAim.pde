import processing.vr.*;

PGraphicsVR pg;

void setup() {
  fullScreen(PVR.STEREO);  
  pg = PVR.getRenderer(this);
  pg.registerUpdate();
}

void update() {
  println("in update function");
}

void draw() {
  background(150);
  
  println("in draw function for eye " + pg.eyeType);
  
  // Some lights
  pointLight(255, 255, 255, pg.cameraX, pg.cameraY, pg.cameraZ);
  
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
  float x = d * pg.forwardX;
  float y = d * pg.forwardY;
  float z = pg.cameraZ + d * pg.forwardZ;
  stroke(255, 200);
  strokeWeight(50);
  point(x, y, z);  
}