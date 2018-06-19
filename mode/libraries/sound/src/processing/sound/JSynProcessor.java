package processing.sound;

import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.UnitFilter;

/**
 * A custom JSyn unit generator that takes care of adding and panning
 */
class JSynProcessor extends UnitFilter {

    private float add;
    private float pan;

	public JSynProcessor() {
		this.input = new UnitInputPort("Input");
	    this.output = new UnitOutputPort(2, "Output");
        this.addPort(this.input);
        this.addPort(this.output);
    }

    @Override
    public void generate(int start, int limit) {
        double[] input = this.input.getValues();
        double right = 0.5 + this.pan * 0.5;
        double left = 1 - right;
        double[] outleft = output.getValues(0);
        double[] outright = output.getValues(1);
        for (int i = start; i < limit; i++) {
            outleft[i] = ( input[i] + this.add ) * left;
            outright[i] = ( input[i] + this.add ) * right;
        }
    }

    public void add(float add) {
    	this.add = add;
    }

    public void pan(float pos) {
    	this.pan = pos;
    }
}
