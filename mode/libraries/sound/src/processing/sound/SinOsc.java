package processing.sound;

import com.jsyn.unitgen.SineOscillator;

import processing.core.PApplet;

/**
 * This is a simple Sine Wave Oscillator 
 * @webref sound
 * @param parent PApplet: typically use "this"
 **/
public class SinOsc extends Oscillator<SineOscillator> {
	public SinOsc(PApplet theParent) {
		super(theParent, new SineOscillator());
	}
}
