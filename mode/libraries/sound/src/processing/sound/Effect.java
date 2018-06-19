package processing.sound;

import java.util.HashSet;
import java.util.Set;

import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.TwoInDualOut;
import com.jsyn.unitgen.UnitFilter;

import processing.core.PApplet;

/**
 * A helper class for applying the same effect (with the same parameters) on two channels.
 * @param <EffectType>
 */
abstract class Effect<EffectType extends UnitFilter> {

	protected Set<SoundObject> inputs = new HashSet<SoundObject>();

	// FIXME what do we do if the same effect is applied to several different
	// input sources -- do we consider them all to feed into the same effect
	// unit(s), or should we instantiate new units every time process() is called?
	protected EffectType left;
	protected EffectType right;
	protected UnitOutputPort output;

	Effect(PApplet parent) {
		Engine.getEngine(parent);
		this.left = this.newInstance();
		this.right = this.newInstance();
		TwoInDualOut merge = new TwoInDualOut();
		merge.inputA.connect(this.left.output);
		merge.inputB.connect(this.right.output);
		this.output = merge.output;
	}

	protected abstract EffectType newInstance();

	/**
	* Start the Filter
	* @webref sound
	* @param input Input sound source
	**/
	public void process(SoundObject input) {
		if (this.inputs.add(input)) {
			// attach effect to circuit until removed with effect.stop()
			input.setEffect(this);
		} else {
			Engine.printWarning("the effect is already processing this sound source");
		}
	}

	/**
	 * 	Stops the Filter.
	 */
	public void stop() {
		if (this.inputs.isEmpty()) {
			Engine.printWarning("this effect is not currently processing any signals.");
		} else {
			for (SoundObject o : this.inputs) {
				o.removeEffect(this);
			}
			this.inputs.clear();
		}
	}
}
