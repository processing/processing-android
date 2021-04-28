// Stereoscopy in VR:
// https://github.com/processing/processing-android/issues/593
// By Javier Marco Rubio (https://github.com/jmarco2000)

import processing.vr.*;

int eyedist = 50;

VRCamera cam;

float angulo=0;

float cx, cz;
float px, pz;
float ang;
float orbita = 400;
float vorbita = 0.01;

// eye matrix
PMatrix3D eyeMat = new PMatrix3D();

boolean ciclo = false;  //test witch eye is beign draw
float orientacion;

void setup() {
  fullScreen(VR);
  cameraUp();
  rectMode(CENTER);
  cam = new VRCamera(this);
  cam.setNear(10);
  cam.setFar(2500);

  //posicion planeta
  //centro
  cx=width/2;
  cz=-200;
  ang=0;
}

void draw() {
  ciclo = !ciclo;

  background(0);
  lights();
  if (!ciclo) {
    getEyeMatrix(eyeMat);
    orientacion = acos(eyeMat.m00);
    if (eyeMat.m02 < 0) orientacion =- orientacion;
    println(degrees(orientacion));
  }
  if (ciclo) {
    cam.setPosition(+(eyedist/2) * cos(orientacion), 100, 500 + (eyedist/2) * sin(orientacion));
  } else {
    cam.setPosition(-(eyedist/2) * cos(orientacion), 100, 500 - (eyedist/2) * sin(orientacion));
  }
  
  pushMatrix();
  px = cx + cos(ang) * orbita;
  pz = cz + sin(ang) * orbita;
  translate(px, 100, pz);
  ang = ang + vorbita;
  rotateY(1.25);
  rotateX(-0.4);
  rotateZ(angulo); //angulo=angulo+0.01;
  noStroke();
  fill(251, 100, 10);
  box(100);
  popMatrix();

  pushMatrix();
  translate(width/2, 100, cz);
  rotateY(1.25);
  rotateX(-0.4);
  rotateZ(angulo); //angulo=angulo+0.01;
  noStroke();
  fill(0, 200, 10);
  sphere(70);
  popMatrix();
}

void mousePressed() {
  vorbita = 0;
}

void mouseReleased() {
  vorbita = 0.01;
}

