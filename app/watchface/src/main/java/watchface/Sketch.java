package watchface;

import processing.core.PApplet; 


public class Sketch extends PApplet {
  public void settings() {  
    fullScreen();
  }

  public void setup() {  
    frameRate(1);
    textFont(createFont("Serif-Bold", 48 * displayDensity));
    textAlign(CENTER, CENTER);
    fill(255);  
  }

  public void draw() {
    background(0);
    if (wearInteractive()) {
      String str = hour() + ":" + nfs(minute(), 2) + ":" + nfs(second(), 2);
      text(str, width/2, height/2);     
    }  
  }
}
