package processing.sound;

import com.jsyn.ports.UnitInputPort;
import com.jsyn.unitgen.UnitFilter;

import processing.core.PApplet;

/**
 * Convenience class with a Pan and an Add
 */
abstract class SoundObject {

	// subclasses need to initialise this circuit and set the corresponding amplitude port
	protected JSynCircuit circuit;
	protected UnitInputPort amplitude;

	protected float amp = 1;
	private boolean isPlaying = false;

	protected SoundObject(PApplet parent) {
		Engine.getEngine(parent);
	}

	private void setAmplitude() {
//		this.amplitude.disconnectAll();
		this.amplitude.set(this.amp);
	}

	/**
	 * Offset the output of this generator by given value
	 * @webref sound
	 * @param add A value for offsetting the audio signal.
	 **/
	public final void add(float add) {
		if (this.circuit.processor == null) {
			Engine.printError("stereo sound sources do not support adding");
		} else {
			this.circuit.processor.add(add);
		}
	}

	/**
	 * Changes the amplitude/volume of this sound.
	 * @webref sound
	 * @param amp A float value between 0.0 (complete silence) and 1.0 (full volume)
	 * controlling the amplitude/volume of this sound.
	 **/
	public void amp(float amp) {
		if (Engine.checkAmp(amp)) {
			this.amp = amp;
			if (this.isPlaying()) {
				this.setAmplitude();
			}
		}
	}

	/**
	 * Check if this sound object is currently playing.
	 * @webref sound
	 * @return `true` if this sound object is currently playing, `false` if it is not.
	 */
	public boolean isPlaying() {
		return this.isPlaying;
	}

	/**
	 * Move the sound in a stereo panorama.
	 * @webref sound
	 * @param pos The panoramic position of this sound unit as a float from -1.0 (left) to 1.0 (right).
	 **/
	public final void pan(float pos) {
		if (this.circuit.processor == null) {
			Engine.printError("stereo sound sources do not support panning");
		} else if (Engine.checkPan(pos)) {
			this.circuit.processor.pan(pos);
		}
	}

	/**
	 * Start the generator
	 * @webref sound
	 **/
	public void play() {
		Engine.getEngine().add(this.circuit);
		Engine.getEngine().play(this.circuit);
		this.setAmplitude();
		this.isPlaying = true;
		// TODO rewire effect if one was set previously (before stopping)?
	}

	/**
	 * Stop the generator
	 * @webref sound
	 **/
	public void stop() {
		this.isPlaying = false;
		this.amplitude.set(0);
		Engine.getEngine().stop(this.circuit);
		Engine.getEngine().remove(this.circuit);
		this.removeEffect(this.circuit.effect);
	}

	protected void setEffect(Effect<? extends UnitFilter> effect) {
		// TODO check if same effect is set a second time
		if (this.circuit.effect != null) {
			this.removeEffect(this.circuit.effect);
		}

		Engine.getEngine().add(effect.left);
		Engine.getEngine().add(effect.right);
		this.circuit.setEffect(effect);
	}

	protected void removeEffect (Effect<? extends UnitFilter> effect) {
		if (this.circuit.effect != effect) {
			// possibly a previous effect that's being stopped here, ignore call
			Engine.printError("this effect is not currently processing any signals.");
			return;
		}

		this.circuit.removeEffect();
		Engine.getEngine().remove(effect.left);
		Engine.getEngine().remove(effect.right);
	}
}
