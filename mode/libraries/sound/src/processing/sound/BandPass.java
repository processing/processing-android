package processing.sound;

import com.jsyn.unitgen.FilterBandPass;

import processing.core.PApplet;

/**
 * This is a band pass filter.
 * @webref sound
 * @param parent PApplet: typically use "this"
 **/
public class BandPass extends Effect<FilterBandPass> {

	public BandPass(PApplet parent) {
		super(parent);
	}

	@Override
	protected FilterBandPass newInstance() {
		return new com.jsyn.unitgen.FilterBandPass();
	}

	/**
	 * Set the bandwidth for the filter.
	 * @webref sound
	 * @param freq Bandwidth in Hz
	 **/
	public void bw(float bw) {
		// TODO check filter quality
		this.left.Q.set(this.left.frequency.get() / bw);
		this.right.Q.set(this.right.frequency.get() / bw);
	}

	/**
	 * Set the cutoff frequency for the filter 
	 * @webref sound
	 * @param freq Cutoff frequency between 0 and 20000
	 **/
	public void freq(float freq) {
		this.left.frequency.set(freq);
		this.right.frequency.set(freq);
	}

	public void process(SoundObject input, float freq) {
		this.freq(freq);
		this.process(input);
	}

	public void process(SoundObject input, float freq, float bw) {
		this.freq(freq);
		this.bw(bw);
		this.process(input);
	}

	/**
	 * Sets frequency and bandwidth of the filter with one method. 
	 * @webref sound
	 * @param freq Set the frequency
	 * @param bw Set the bandwidth
	 **/
	public void set(float freq, float bw) {
		this.freq(freq);
		this.bw(bw);
	}
}
