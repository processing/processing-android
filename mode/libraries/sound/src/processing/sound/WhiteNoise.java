package processing.sound;

import processing.core.PApplet;

/**
 * This is a White Noise Generator. White Noise has a flat spectrum. 
 * @webref sound
 * @param parent PApplet: typically use "this"	
 **/
public class WhiteNoise extends Noise<com.jsyn.unitgen.WhiteNoise> {

	public WhiteNoise(PApplet theParent) {
		super(theParent, new com.jsyn.unitgen.WhiteNoise());
		this.amplitude = this.noise.amplitude;
	}
}
