package processing.sound;

import com.jsyn.unitgen.UnitOscillator;

import processing.core.PApplet;

abstract class Oscillator<JSynOscillator extends UnitOscillator> extends SoundObject {

	protected JSynOscillator oscillator;

	protected Oscillator(PApplet theParent, JSynOscillator oscillator) {
		super(theParent);
		this.oscillator = oscillator;
		this.circuit = new JSynCircuit(this.oscillator.getOutput());
		this.amplitude = this.oscillator.amplitude;
	}

	/**
	 * Set the freuquency of the oscillator in Hz.
	 * @webref sound
	 * @param freq A floating point value of the oscillator in Hz.
	 **/
	public void freq(float freq) {
		// TODO check positive?
		this.oscillator.frequency.set(freq);
	}

	/**
	 * Starts the oscillator
	 * @webref sound
	 **/
	public void play() {
		super.play();
	}

	public void play(float freq, float amp) {
		this.freq(freq);
		this.amp(amp);
		this.play();
	}

	public void play(float freq, float amp, float add) {
		this.add(add);
		this.play(freq, amp);
	}

	public void play(float freq, float amp, float add, float pos) {
		this.set(freq, amp, add, pos);
		this.play();
	}

	/**
	 * Set multiple parameters at once
	 * @webref sound
	 * @param freq The frequency value of the oscillator in Hz.
	 * @param amp The amplitude of the oscillator as a value between 0.0 and 1.0.
	 * @param add A value for modulating other audio signals.
	 * @param pos The panoramic position of the oscillator as a float from -1.0 to 1.0.
	 **/
	public void set(float freq, float amp, float add, float pos) {
		this.freq(freq);
		this.amp(amp);
		this.add(add);
		this.pan(pos);
	}
}
