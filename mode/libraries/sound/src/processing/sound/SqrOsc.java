package processing.sound;

import com.jsyn.unitgen.SquareOscillator;

import processing.core.PApplet;

/**
 * This is a simple Square Wave Oscillator 
 * @webref sound
 * @param parent PApplet: typically use "this"
 **/
public class SqrOsc extends Oscillator<SquareOscillator> {
	public SqrOsc(PApplet theParent) {
		super(theParent, new SquareOscillator());
	}
}
