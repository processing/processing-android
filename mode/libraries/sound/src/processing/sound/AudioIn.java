package processing.sound;

import com.jsyn.unitgen.ChannelIn;

import processing.core.PApplet;

/**
 * AudioIn lets you grab the audio input from your soundcard.
 * 
 * @webref sound
 * @param parent
 *            PApplet: typically use "this"
 * @param in
 *            Input Channel number
 **/
public class AudioIn extends SoundObject {

	private ChannelIn input;

	public AudioIn(PApplet parent, int in) {
		super(parent);
		// ChannelIn for mono, LineIn for stereo
		this.input = new ChannelIn(in);
		this.circuit = new JSynCircuit(this.input.output);
	}

	/**
	 * Not implemented yet.
	 * 
	 * @webref sound
	 **/
	public void amp(float amp) {
		// TODO
	}

	/**
	 * Start the Input Stream and route it to the Audio Hardware Output
	 * 
	 * @webref sound
	 **/
	public void play() {
		super.play();
	}

	/**
	 * Start the input stream without routing it to the audio hardware output.
	 * 
	 * @webref sound
	 */
	public void start() {
		// TODO don't route to lineout
		super.play();
	}

	public void start(float amp) {
		this.amp(amp);
		this.start();
	}

	public void start(float amp, float add) {
		this.add(add);
		this.start(amp);
	}

	public void start(float amp, float add, float pos) {
		this.set(amp, add, pos);
		this.start();
	}

	/**
	 * Sets amplitude, add and pan position with one method.
	 * 
	 * @webref sound
	 * @param amp
	 *            Amplitude value between 0.0 and 1.0
	 * @param add
	 *            Offset the generator by a given value
	 * @param pos
	 *            Pan the generator in stereo panorama. Allowed values are between
	 *            -1.0 and 1.0.
	 **/
	public void set(float amp, float add, float pos) {
		this.amp(amp);
		this.add(add);
		this.pan(pos);
	}
}
