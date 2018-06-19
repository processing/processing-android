package processing.sound;

import com.jsyn.unitgen.SawtoothOscillator;

import processing.core.PApplet;

/**
 * This is a simple Saw Wave Oscillator 
 * @webref sound
 * @param parent PApplet: typically use "this"
 **/
public class SawOsc extends Oscillator<SawtoothOscillator> {
	public SawOsc(PApplet theParent) {
		super(theParent, new SawtoothOscillator());
	}
}
