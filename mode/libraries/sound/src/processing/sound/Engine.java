package processing.sound;

import com.jsyn.JSyn;
import com.jsyn.Synthesizer;
import com.jsyn.devices.AudioDeviceFactory;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.unitgen.LineOut;
import com.jsyn.unitgen.UnitGenerator;
import com.jsyn.unitgen.UnitSource;

import processing.core.PApplet;

// class needs to be public for registered callback methods to be callable
public class Engine {

	private static Engine singleton;

	private AudioDeviceManager audioManager;
	protected Synthesizer synth;
	private LineOut lineOut;
	private int numInputs;
	private int numOutputs;

	private Engine(PApplet parent) {
		try {
			Class.forName("javax.sound.sampled.AudioSystem");
			this.audioManager = AudioDeviceFactory.createAudioDeviceManager();
		} catch (ClassNotFoundException e) {
			this.audioManager = new JSynAndroidAudioDeviceManager();
		}
		this.synth = JSyn.createSynthesizer(this.audioManager);

		int numDevices = audioManager.getDeviceCount();
		for (int i = 0; i < numDevices; i++) {
			String deviceName = audioManager.getDeviceName(i);
			int maxInputs = audioManager.getMaxInputChannels(i);
			int maxOutputs = audioManager.getMaxOutputChannels(i);
			boolean isDefaultInput = (i == audioManager.getDefaultInputDeviceID());
			boolean isDefaultOutput = (i == audioManager.getDefaultOutputDeviceID());
			if (isDefaultInput) {
				this.numInputs = maxInputs;
			}
			if (isDefaultOutput) {
				// could try to grab more output channels if we wanted to?
				this.numOutputs = Math.min(maxOutputs, 2);
			}
			System.out.println("#" + i + " : " + deviceName);
			System.out.println("  max inputs : " + maxInputs + (isDefaultInput ? "   (default)" : ""));
			System.out.println("  max outputs: " + maxOutputs + (isDefaultOutput ? "   (default)" : ""));
		}

		this.startSynth(44100);

		this.lineOut = new LineOut(); // stereo lineout by default
		this.synth.add(lineOut);
		this.lineOut.start();

		if (parent != null) {
			parent.registerMethod("dispose", this);
			// Android only
			parent.registerMethod("pause", this);
			parent.registerMethod("resume", this);
		}
	}

	protected void startSynth(int sampleRate) {
		if (this.synth.isRunning()) {
			this.synth.stop();
		}
		this.synth.start(sampleRate, AudioDeviceManager.USE_DEFAULT_DEVICE, this.numInputs,
				AudioDeviceManager.USE_DEFAULT_DEVICE, this.numOutputs);
	}

	public void dispose() {
		this.lineOut.stop();
		this.synth.stop();
	}

	public void pause() {
		// TODO android only
	}

	public void resume() {
		// TODO android only
	}

	protected static Engine getEngine() {
		return Engine.getEngine(null);
	}

	protected static Engine getEngine(PApplet parent) {
		if (Engine.singleton == null) {
			Engine.singleton = new Engine(parent);
		}
		return Engine.singleton;
	}

	protected void add(UnitGenerator generator) {
		if (generator.getSynthesisEngine() == null) {
			this.synth.add(generator);
		}
	}

	protected void remove(UnitGenerator generator) {
		this.synth.remove(generator);
	}

	protected void play(UnitSource source) {
		// TODO check if unit is already connected
		source.getOutput().connect(0, this.lineOut.input, 0);
		source.getOutput().connect(1, this.lineOut.input, 1);
	}

	protected void stop(UnitSource source) {
		source.getOutput().disconnect(0, this.lineOut.input, 0);
		source.getOutput().disconnect(1, this.lineOut.input, 1);
	}

	protected static boolean checkAmp(float amp) {
		if (amp < -1 || amp > 1) {
			Engine.printError("amplitude has to be in [-1,1]");
			return false;
		}
		return true;
	}

	protected static boolean checkPan(float pan) {
		if (pan < -1 || pan > 1) {
			Engine.printError("pan has to be in [-1,1]");
			return false;
		}
		return true;
	}

	protected static void printWarning(String message) {
		PApplet.println("Sound library warning: " + message);
	}

	protected static void printError(String message) {
		PApplet.println("Sound library error: " + message);
	}
}
