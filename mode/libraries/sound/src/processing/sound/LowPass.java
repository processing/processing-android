package processing.sound;

import com.jsyn.unitgen.FilterLowPass;

import processing.core.PApplet;

/**
 * This is a low pass filter
 * @sound webref
 * @param parent PApplet: typically use "this"
 **/
public class LowPass extends Effect<FilterLowPass> {

	public LowPass(PApplet parent) {
		super(parent);
	}

	@Override
	protected FilterLowPass newInstance() {
		return new com.jsyn.unitgen.FilterLowPass();
	}

	/**
	 * Set the cut off frequency for the filter
	 * @webref sound
	 * @param freq The frequency value as a float
	 */
	public void freq(float freq) {
		this.left.frequency.set(freq);
		this.right.frequency.set(freq);
	}

	public void process(SoundObject input, float freq) {
		this.freq(freq);
		this.process(input);
	}
}
