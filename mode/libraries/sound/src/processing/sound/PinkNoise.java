package processing.sound;

import processing.core.PApplet;

/**
* This is a pink noise generator. Pink Noise has a decrease of 3dB per octave.
* @webref sound
* @param parent PApplet: typically use "this"	
**/
public class PinkNoise extends Noise<com.jsyn.unitgen.PinkNoise> {

	public PinkNoise(PApplet theParent) {
		super(theParent, new com.jsyn.unitgen.PinkNoise());
		this.amplitude = this.noise.amplitude;
	}
}
