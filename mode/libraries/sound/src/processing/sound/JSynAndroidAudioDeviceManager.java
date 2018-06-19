package processing.sound;

import java.util.ArrayList;

import com.jsyn.devices.AudioDeviceInputStream;
import com.jsyn.devices.AudioDeviceManager;
import com.jsyn.devices.AudioDeviceOutputStream;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

class JSynAndroidAudioDeviceManager implements AudioDeviceManager {

	ArrayList<DeviceInfo> deviceRecords;
	private double suggestedOutputLatency = 0.1;
	private double suggestedInputLatency = 0.1;
	private int defaultInputDeviceID = 0;
	private int defaultOutputDeviceID = 0;

	public JSynAndroidAudioDeviceManager() {
		this.deviceRecords = new ArrayList<DeviceInfo>();
		DeviceInfo deviceInfo = new DeviceInfo();

		deviceInfo.name = "Android Audio";
		deviceInfo.maxInputs = 0;
		deviceInfo.maxOutputs = 2;
		this.deviceRecords.add(deviceInfo);
	}

	public String getName() {
		return "JSyn Android Audio for Processing";
	}

	class DeviceInfo {
		String name;
		int maxInputs;
		int maxOutputs;

		public String toString() {
			return "AudioDevice: " + name + ", max in = " + maxInputs + ", max out = " + maxOutputs;
		}
	}

	private class AndroidAudioStream {
		short[] shortBuffer;
		int frameRate;
		int samplesPerFrame;
		AudioTrack audioTrack;
		int minBufferSize;
		int bufferSize;

		public AndroidAudioStream(int deviceID, int frameRate, int samplesPerFrame) {
			this.frameRate = frameRate;
			this.samplesPerFrame = samplesPerFrame;
		}

		public double getLatency() {
			int numFrames = this.bufferSize / this.samplesPerFrame;
			return ((double) numFrames) / this.frameRate;
		}

	}

	private class AndroidAudioOutputStream extends AndroidAudioStream implements AudioDeviceOutputStream {
		public AndroidAudioOutputStream(int deviceID, int frameRate, int samplesPerFrame) {
			super(deviceID, frameRate, samplesPerFrame);
		}

		public void start() {
			this.minBufferSize = AudioTrack.getMinBufferSize(this.frameRate, AudioFormat.CHANNEL_OUT_STEREO,
					AudioFormat.ENCODING_PCM_16BIT);
			this.bufferSize = (3 * (this.minBufferSize / 2)) & ~3;
			this.audioTrack = new AudioTrack.Builder()
					.setAudioAttributes(new AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
					.setAudioFormat(new AudioFormat.Builder()
							.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(this.frameRate)
							.build())
					.setBufferSizeInBytes(this.bufferSize)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.build();
			this.audioTrack.play();
		}

		public void write(double value) {
			double[] buffer = new double[1];
			buffer[0] = value;
			this.write(buffer, 0, 1);
		}

		public void write(double[] buffer) {
			this.write(buffer, 0, buffer.length);
		}

		public void write(double[] buffer, int start, int count) {
			if ((this.shortBuffer == null) || (this.shortBuffer.length < count)) {
				this.shortBuffer = new short[count];
			}

			for (int i = 0; i < count; i++) {
				int sample = (int) (32767.0 * buffer[i + start]);
				if (sample > Short.MAX_VALUE) {
					sample = Short.MAX_VALUE;
				} else if (sample < Short.MIN_VALUE) {
					sample = Short.MIN_VALUE;
				}
				this.shortBuffer[i] = (short) sample;
			}

			this.audioTrack.write(this.shortBuffer, 0, count);
		}

		public void stop() {
			this.audioTrack.stop();
			this.audioTrack.release();
		}

		public void close() {
		}

	}

	private class AndroidAudioInputStream extends AndroidAudioStream implements AudioDeviceInputStream {

		public AndroidAudioInputStream(int deviceID, int frameRate, int samplesPerFrame) {
			super(deviceID, frameRate, samplesPerFrame);
		}

		public void start() {
		}

		public double read() {
			double[] buffer = new double[1];
			this.read(buffer, 0, 1);
			return buffer[0];
		}

		public int read(double[] buffer) {
			return this.read(buffer, 0, buffer.length);
		}

		public int read(double[] buffer, int start, int count) {
			return 0;
		}

		public void stop() {
		}

		public int available() {
			return 0;
		}

		public void close() {
		}
	}

	public AudioDeviceOutputStream createOutputStream(int deviceID, int frameRate, int samplesPerFrame) {
		return new AndroidAudioOutputStream(deviceID, frameRate, samplesPerFrame);
	}

	public AudioDeviceInputStream createInputStream(int deviceID, int frameRate, int samplesPerFrame) {
		return new AndroidAudioInputStream(deviceID, frameRate, samplesPerFrame);
	}

	public double getDefaultHighInputLatency(int deviceID) {
		return 0.3;
	}

	public double getDefaultHighOutputLatency(int deviceID) {
		return 0.3;
	}

	public int getDefaultInputDeviceID() {
		return this.defaultInputDeviceID;
	}

	public int getDefaultOutputDeviceID() {
		return this.defaultOutputDeviceID;
	}

	public double getDefaultLowInputLatency(int deviceID) {
		return 0.1;
	}

	public double getDefaultLowOutputLatency(int deviceID) {
		return 0.1;
	}

	public int getDeviceCount() {
		return this.deviceRecords.size();
	}

	public String getDeviceName(int deviceID) {
		return this.deviceRecords.get(deviceID).name;
	}

	public int getMaxInputChannels(int deviceID) {
		return this.deviceRecords.get(deviceID).maxInputs;
	}

	public int getMaxOutputChannels(int deviceID) {
		return this.deviceRecords.get(deviceID).maxOutputs;
	}

	public int setSuggestedOutputLatency(double latency) {
		this.suggestedOutputLatency = latency;
		return 0;
	}

	public int setSuggestedInputLatency(double latency) {
		this.suggestedInputLatency = latency;
		return 0;
	}

}
