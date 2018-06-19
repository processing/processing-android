package processing.sound;

import processing.core.PApplet;

/**
 * AudioDevice allows for configuring the audio server. If you need a low
 * latency server you can reduce the buffer size. Allowed values are power of 2.
 * For changing the sample rate pass the appropriate value in the constructor.
 * 
 * @webref sound
 * @param parent
 *            PApplet: typically use "this"
 * @param sampleRate
 *            Sets the sample rate (default 44100).
 * @param bufferSize
 *            Sets the buffer size (not used).
 **/
public class AudioDevice {

	public AudioDevice(PApplet theParent, int sampleRate, int bufferSize) {
		Engine.getEngine(theParent).startSynth(sampleRate);
		// TODO bufferSize was necessary for original library's FFT to
		// work, but currently ignored
	}

	// TODO original library had other public methods which were however not
	// documented

	// TODO move Engine class infrastructure over to AudioDevice altogether?
}
