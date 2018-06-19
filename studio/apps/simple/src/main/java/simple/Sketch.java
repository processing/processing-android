package simple;

import processing.core.PApplet;
import processing.sound.SoundFile;

public class Sketch extends PApplet {

  SoundFile soundfile;

  public void settings() {
    fullScreen();
  }

  public void setup() {
    soundfile = new SoundFile(this, "vibraphon.aiff");
    // These methods return useful infos about the file
    println("SFSampleRate= " + soundfile.sampleRate() + " Hz");
    println("SFSamples= " + soundfile.frames() + " samples");
    println("SFDuration= " + soundfile.duration() + " seconds");

    // Play the file in a loop
    soundfile.loop();
  }

  public void draw() {
    background(150);
    // Map mouseX from 0.25 to 4.0 for playback rate. 1 equals original playback
    // speed 2 is an octave up 0.5 is an octave down.
    soundfile.rate(map(mouseX, 0, width, 0.25f, 4.0f));

    // Map mouseY from 0.2 to 1.0 for amplitude
    soundfile.amp(map(mouseY, 0, width, 0.2f, 1.0f));

    // Map mouseY from -1.0 to 1.0 for left to right
    soundfile.pan(map(mouseY, 0, height, -1.0f, 1.0f));
  }
}
