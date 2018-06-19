package processing.sound;

import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.Circuit;
import com.jsyn.unitgen.TwoInDualOut;
import com.jsyn.unitgen.UnitFilter;
import com.jsyn.unitgen.UnitGenerator;
import com.jsyn.unitgen.UnitSource;

/**
 * Helper class wrapping a source unit generator, add/pan processor and effect into one circuit.
 */
class JSynCircuit extends Circuit implements UnitSource {

	private UnitGenerator source;
	protected JSynProcessor processor;
	protected UnitOutputPort preEffect;
	protected Effect<? extends UnitFilter> effect;
	protected TwoInDualOut output;

	public JSynCircuit(UnitOutputPort input) {
		this.output = new TwoInDualOut();
		this.add(this.output);

		this.source = input.getUnitGenerator();
		this.add(this.source);

		if (input.getNumParts() == 2) {
			// stereo source - no need for pan, so bypass processor
			this.preEffect = input;
		} else {
			this.processor = new JSynProcessor();
			this.add(this.processor);
			this.processor.input.connect(input);
			this.preEffect = this.processor.output;
		}
		this.wireBypass();
	}

	protected void wireBypass() {
		this.preEffect.connect(0, this.output.inputA, 0);
		this.preEffect.connect(1, this.output.inputB, 0);
	}

	protected void removeEffect() {
		this.wireBypass();
		this.effect.left.output.disconnect(this.output.inputA);
		this.effect.right.output.disconnect(this.output.inputB);
		this.preEffect.disconnect(0, this.effect.left.input, 0);
		this.preEffect.disconnect(1, this.effect.right.input, 0);
		this.effect = null;
	}

	protected void setEffect(Effect<? extends UnitFilter> effect) {
		this.effect = effect;
		this.preEffect.connect(0, this.effect.left.input, 0);
		this.preEffect.connect(1, this.effect.right.input, 0);

		this.effect.left.output.connect(this.output.inputA);
		this.preEffect.disconnect(0, this.output.inputA, 0);

		this.effect.right.output.connect(this.output.inputB);
		this.preEffect.disconnect(1, this.output.inputB, 0);
	}

	@Override
	public UnitOutputPort getOutput() {
		return this.output.output;
	}
}
