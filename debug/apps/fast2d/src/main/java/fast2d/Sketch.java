package fast2d;

import android.opengl.GLES20;

import processing.core.PApplet;
import processing.core.PShape;
import processing.opengl.PShader;
import processing.core.PImage;
import processing.core.PFont;
import processing.core.PVector;
import java.util.ArrayList;
import processing.opengl.PGraphics2DX;

public class Sketch extends PApplet {
  boolean keyboard = false;
  boolean wireframe = false;

  int join = MITER, cap = SQUARE, mode = OPEN;

  PImage img;
  PFont font;

  float sc = 1;
  float weight = 1;

  boolean runDemo[] = new boolean[10];

  //useful for debugging
  private boolean printDemo = false;

  //data for demo 2
  int[] c = new int[4096];
  ArrayList<PVector> points = new ArrayList<PVector>();


  public void settings() {
    fullScreen(P2DX);
//    fullScreen(P2D);
  }

  public void setup() {
//    orientation(LANDSCAPE);

    //pardon the silly image
    img = loadImage("balmer_developers_poster.png");
    font = createFont("SansSerif", displayDensity * 72);

    //setup for demo 2
    for (int i = 0; i < c.length; ++i) {
      c[i] = color(random(255), random(255), random(255));
    }
  }

   public void draw() {
    background(255);

    fill(255, 0, 63, 127);
    stroke(255, 0, 255, 127);
    strokeWeight(12 * displayDensity);
    strokeJoin(ROUND);
    noStroke();

    if (keyPressed && key == 'z') {
      sc /= 1.01;
    } else if (keyPressed && key == 'x') {
      sc *= 1.01;
    } else if (keyPressed && key == 'c') {
      weight /= 1.01;
    } else if (keyPressed && key == 'v') {
      weight *= 1.01;
    }

    scale(sc);

//    println();
//    println("FRAME #" + frameCount);
//    println();

    if (frameCount % 10 == 0) println((int) frameRate + " fps");

    strokeCap(cap);
    strokeJoin(join);

    if (runDemo[5]) demo5();
    if (runDemo[2]) demo2();
    fill(255, 0, 255, 127);
    if (runDemo[1]) demo1();
    if (runDemo[3]) demo3();
    if (runDemo[4]) demo4();
    translate(100, 200);
    if (runDemo[3]) demo3();
    if (runDemo[6]) demo6();
    if (runDemo[7]) demo7();
    if (runDemo[8]) demo8();
    if (runDemo[9]) demo9();
    if (runDemo[0]) demo10();
  }

   //basic self-intersecting polygon
  private void demo1() {
    if (printDemo) println("demo1");

    strokeWeight(6 * weight * displayDensity);
    stroke(0, 127, 95, 191);
    beginShape();
    vertex(100, 200);
    vertex(200, 100);
    vertex(300, 200);
    vertex(400, 100);
    vertex(350, 200);
    vertex(450, 100);

    vertex(300, 300);
    vertex(mouseX, mouseY);
    vertex(600, 200);

    vertex(550, 100);
    vertex(550, 400);
    vertex(750, 400);
    vertex(750, 600);
    vertex(100, 600);
    endShape(mode);
  }


  //mouse controlled polygon
  private void demo2() {
    if (printDemo) println("demo2");

    //NOTE: we draw each vertex with a random fill color to test how it behaves.
    //in P2D, the colors are interpolated across the triangles output by the GLU tessellator.
    //in JAVA2D, when endShape() is called, the currently active color is used for all vertices.
    //for now P4D follows the behavior of P2D, but switching to JAVA2D's behavior
    //would allow us to simplify our implementation a bit
    beginShape();
    for (int i = 0; i < points.size(); ++i) {
      fill(c[i]);
      vertex(points.get(i).x, points.get(i).y);
    }
    endShape();
  }


  //textured polygon
  private void demo3() {
    if (printDemo) println("demo3");

    //test that textured shapes and tint() work correctly
    float s = 4;
    beginShape();
    texture(img);
    vertex(10*s, 20*s, 0, 0);
    tint(0, 255, 127, 127);
    vertex(80*s, 5*s, 800, 0);
    vertex(95*s, 90*s, 800, 800);
    noTint();
    vertex(40*s, 95*s, 0, 800);
    endShape();

    //test that image() function works correctly
    tint(255, 31);
    rotate(-1);
    image(img, -200, 100);
    rotate(1);
    tint(255);
    image(img, 700, 100, 200, 100);
  }

  //text rendering
  private void demo4() {
    if (printDemo) println("demo4");

    textFont(font);
    text("Now is the time for all good men to come to the aid of their country.\n"
        + "If they do not the quick brown fox may never jump over the lazy sleeping dog again.\n"
        + "He may, however, take up knitting as a suitable hobby for all retired quick brown foxes.\n"
        + "This is test #1 of 9,876,543,210.\n"
        + "Collect them all!", 0, 100);
  }

  //shapes benchmark
  private void demo5() {
    if (printDemo) println("demo5");

    strokeWeight(2 * displayDensity);
    stroke(0);
    fill(200);

    float dev = 10; //deviation

    //change these parameters to benchmark various things
    int unit = 10;
    //line, triangle, rect, ellipse, point
    int[] amount = { 20, 15, 10,  5, 40 };

    for (int i = 0; i < amount[0]*unit; ++i) {
      float x = random(width);
      float y = random(height);
      line(x, y, x + random(-dev, dev), y + random(-dev, dev));
    }

    for (int i = 0; i < amount[1]*unit; ++i) {
      float x = random(width);
      float y = random(height);
      triangle(x, y,
               x + random(-dev*2, dev*2), y + random(-dev*2, dev*2),
               x + random(-dev*2, dev*2), y + random(-dev*2, dev*2));
    }

    for (int i = 0; i < amount[2]*unit; ++i) {
      rect(random(width), random(height), random(dev), random(dev));
    }

    for (int i = 0; i < amount[3]*unit; ++i) {
      ellipse(random(width), random(height), random(dev*2), random(dev*2));
    }

    for (int i = 0; i < amount[4]*unit; ++i) {
      point(random(width), random(height));
    }

    //large ellipse to test smoothness of outline
    ellipse(width/2, height/2, width/2, height/4);
  }

  //duplicate vertex test
  private void demo6() {
    if (printDemo) println("demo6");

    //NOTE: yes, this produces the wrong result in P4D
    //see PGraphics4D.shapeVertex() for why
    beginShape();
    vertex(500, 300);
    vertex(600, 400); //dupe
    vertex(700, 300);
    vertex(650, 300);
    vertex(600, 400); //dupe
    vertex(550, 300);
    endShape(CLOSE);
  }

  //user-define contours
  private void demo7() {
    if (printDemo) println("demo7");

    //from https://processing.org/reference/beginContour_.html

    fill(127, 255, 127);
    stroke(255, 0, 0);
    beginShape();
    // Exterior part of shape, clockwise winding
    vertex(-40, -40);
    vertex(40, -40);
    vertex(40, 40);
    vertex(-40, 40);
    // Interior part of shape, counter-clockwise winding
    beginContour();
    vertex(-20, -20);
    vertex(-20, 20);
    vertex(20, 20);
    vertex(20, -20);
    endContour();
    endShape(CLOSE);
  }

  //primitive types
  private void demo8() {
    if (printDemo) println("demo8");

    //from https://processing.org/reference/beginShape_.html

    stroke(0);
    strokeWeight(4 * displayDensity);
    fill(255, 127, 127);

    pushMatrix();
    resetMatrix();
    scale(2);

    beginShape();
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape(CLOSE);

    translate(100, 0);

    beginShape(POINTS);
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape();

    translate(100, 0);

    beginShape(LINES);
    vertex(30, 40);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape();

    translate(100, 0);

    pushStyle();
    noFill();
    beginShape();
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape();
    popStyle();

    translate(100, 0);

    pushStyle();
    noFill();
    beginShape();
    vertex(30, 20);
    vertex(85, 20);
    vertex(85, 75);
    vertex(30, 75);
    endShape(CLOSE);
    popStyle();

    translate(100, 0);

    beginShape(TRIANGLES);
    vertex(30, 75);
    vertex(40, 20);
    vertex(50, 75);
    vertex(60, 20);
    vertex(70, 75);
    vertex(80, 20);
    endShape();

    resetMatrix();
    scale(2);
    translate(0, 100);

    beginShape(TRIANGLE_STRIP);
    vertex(30, 75);
    vertex(40, 20);
    vertex(50, 75);
    vertex(60, 20);
    vertex(70, 75);
    vertex(80, 20);
    vertex(90, 75);
    endShape();

    translate(100, 0);

    beginShape(TRIANGLE_FAN);
    vertex(57.5f, 50);
    vertex(57.5f, 15);
    vertex(92, 50);
    vertex(57.5f, 85);
    vertex(22, 50);
    vertex(57.5f, 15);
    endShape();

    translate(100, 0);

    beginShape(QUADS);
    vertex(30, 20);
    vertex(30, 75);
    vertex(50, 75);
    vertex(50, 20);
    vertex(65, 20);
    vertex(65, 75);
    vertex(85, 75);
    vertex(85, 20);
    endShape();

    translate(100, 0);

    beginShape(QUAD_STRIP);
    vertex(30, 20);
    vertex(30, 75);
    vertex(50, 20);
    vertex(50, 75);
    vertex(65, 20);
    vertex(65, 75);
    vertex(85, 20);
    vertex(85, 75);
    endShape();

    translate(100, 0);

    beginShape();
    vertex(20, 20);
    vertex(40, 20);
    vertex(40, 40);
    vertex(60, 40);
    vertex(60, 60);
    vertex(20, 60);
    endShape(CLOSE);

    //test handling of concave and self-intersecting quads
    //NOTE: JAVA2D currently draws these correctly, but P2D does not
    resetMatrix();
    scale(2);
    translate(0, 200);
    strokeWeight(2 * displayDensity);
    float t = frameCount * 0.01f;

    beginShape(QUADS);
    vertex(50, 10);
    vertex(90, 50);
    vertex(30 + 20*sin(t), 70 + 20*cos(t));
    vertex(30 - 20*sin(t), 70 - 20*cos(t));
    endShape(CLOSE);

    translate(100, 0);

    beginShape(QUAD_STRIP);
    vertex(50, 10);
    vertex(90, 50);
    vertex(30 + 20*sin(t), 70 + 20*cos(t));
    vertex(30 - 20*sin(t), 70 - 20*cos(t));
    endShape(CLOSE);

    popMatrix();
  }

  //testing angular stuff
  private void demo9() {
    if (printDemo) println("demo9");

    strokeWeight(4 * displayDensity);
    stroke(127, 0, 0);
    fill(255, 255, 255);

    //testing the behavior of floating point % operator (for dealing with angles)
    float py = 0;
    for (int i = 0; i < width; ++i) {
      float x = (i - width/2) * 0.1f;
      float y = height/2 - (x % PI) * 10;
      line(i, y, i - 1, py);
      py = y;
    }

    //testing the behavior of P2D arc() at various angles
    //NOTE: arcs with negative angle aren't drawn
    arc(100, 100, 100, 100, -1, new PVector(mouseX, mouseY).sub(100, 100).heading());

    //test for whether LINES primitive type has self-overlap
    //NOTE: it does in JAVA2D, but not in P2D
    stroke(0, 127, 127, 127);
    beginShape(LINES);
    vertex(0, 0);
    vertex(width, height + 100);
    vertex(width, 0);
    vertex(0, height + 100);
    endShape();
  }

  //curve tests
  private void demo10() {
    if (printDemo) println("demo10");

    //these cause errors in P4D because we haven't implemented them yet
    //so they're disabled in the demo for now
    if (getGraphics() instanceof PGraphics2DX) {
      return;
    }

    noFill();
    stroke(0);
    strokeWeight(4 * displayDensity);
    pushMatrix();
    scale(2);

    beginShape();
    curveVertex(84,  91);
    curveVertex(84,  91);
    curveVertex(68,  19);
    curveVertex(21,  17);
    curveVertex(32, 100);
    curveVertex(32, 100);
    endShape();

    translate(100, 0);

    beginShape();
    vertex(30, 20);
    bezierVertex(80, 0, 80, 75, 30, 75);
    bezierVertex(50, 80, 60, 25, 30, 20);
    endShape();

    translate(100, 0);

    beginShape();
    vertex(20, 20);
    quadraticVertex(80, 20, 50, 50);
    quadraticVertex(20, 80, 80, 80);
    vertex(80, 60);
    endShape();

    popMatrix();
  }

  public void mousePressed() {
    if (keyboard) {
      closeKeyboard();
      keyboard = false;
    } else {
      if (0.9 * height < mouseY) {
        openKeyboard();
        keyboard = true;
      } else {
        // behavior for demo 2
        points.add(new PVector(mouseX, mouseY));
      }
    }
  }

  public void mouseDragged() {
    if ( mouseY < 0.1 * height) {
      points.get(points.size() - 1).x = mouseX;
      points.get(points.size() - 1).y = mouseY;
    }
  }

  public void keyPressed() {
    if (key == 'q') {
      join = MITER;
    } else if (key == 'w') {
      join = BEVEL;
    } else if (key == 'e') {
      join = ROUND;
    } else if (key == 'a') {
      cap = SQUARE;
    } else if (key == 's') {
      cap = PROJECT;
    } else if (key == 'd') {
      cap = ROUND;
    } else if (key == 'r') {
      mode = OPEN;
    } else if (key == 'f') {
      mode = CLOSE;
    } else if (key == 't') {
      PGraphics2DX.premultiplyMatrices = true;
    } else if (key == 'g') {
      PGraphics2DX.premultiplyMatrices = false;
    } else if (key == ' ') {
//      PJOGL pgl = (PJOGL)((PGraphics2D)this.g).pgl;
//      if (wireframe)
//        pgl.gl.getGL4().glPolygonMode(GL4.GL_FRONT_AND_BACK, GL4.GL_FILL);
//      else
//        pgl.gl.getGL4().glPolygonMode(GL4.GL_FRONT_AND_BACK, GL4.GL_LINE);
//      wireframe = !wireframe;
    } else if (key - '0' >= 0 && key - '0' < 10) {
      runDemo[key - '0'] = !runDemo[key - '0'];
    }
  }
}
