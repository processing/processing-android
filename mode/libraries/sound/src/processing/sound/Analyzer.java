package processing.sound;

import com.jsyn.ports.UnitOutputPort;

import processing.core.PApplet;

abstract class Analyzer {

	protected SoundObject input;

	protected Analyzer(PApplet parent) {
		Engine.getEngine(parent);
	}

	/**
	 * Define the audio input for the analyzer.
	 * 
	 * @webref sound
	 * @param input The input sound source
	 **/
	public void input(SoundObject input) {
		if (this.input == input) {
			// TODO print warning?
		} else {
			if (this.input != null) {
				// TODO disconnect unit (and remove from synth?)
			}
			this.input = input;
			Engine.getEngine().add(input.circuit);
	
			this.setInput(input.circuit.output.output);
		}
	}

	protected abstract void setInput(UnitOutputPort input);
}
