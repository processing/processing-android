import processing.vr.*;

void setup() {
  fullScreen(STEREO);  
}

void calculate() {
  println("in calculate function");
}

void draw() {
  background(150);  
  translate(width/2, height/2);
  noStroke();

  // Some lights
  pointLight(255, 255, 255, 0, 0, 500);

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
  
  // Large box at the center
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
  
  // Use eye coordinates at 100 units from the camera position:;
  eye();
  stroke(255, 200);
  strokeWeight(50);
  point(0, 0, 100);  
}