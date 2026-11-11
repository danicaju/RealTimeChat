package com.danicaju.chat.client.ui;

import com.danicaju.chat.client.ChatClientConnection;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Base64;
import java.util.function.DoubleConsumer;

/**
 * Motor de audio en tiempo real (16 kHz PCM mono) para canales de voz
 * con cálculo de nivel RMS (indicador de quién habla), Mute y Deafen.
 */
public class VoiceAudioManager {

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final int FRAME_BYTES = 640; // 20 ms de audio 16kHz 16-bit

    private final ChatClientConnection connection;
    private final DoubleConsumer localLevelCallback;

    private volatile boolean active = false;
    private volatile boolean muted = false;
    private volatile boolean deafened = false;
    private volatile String activeVoiceChannel;

    private TargetDataLine micLine;
    private SourceDataLine speakerLine;
    private Thread captureThread;

    public VoiceAudioManager(ChatClientConnection connection, DoubleConsumer localLevelCallback) {
        this.connection = connection;
        this.localLevelCallback = localLevelCallback;
    }

    public synchronized boolean joinVoice(String channel) {
        if (active && channel.equalsIgnoreCase(activeVoiceChannel)) {
            return true;
        }
        leaveVoice();
        this.activeVoiceChannel = channel;
        this.active = true;

        try {
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
            if (AudioSystem.isLineSupported(speakerInfo)) {
                speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                speakerLine.open(AUDIO_FORMAT, 8192);
                speakerLine.start();
            }
        } catch (Exception ignored) {
            speakerLine = null;
        }

        try {
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            if (AudioSystem.isLineSupported(micInfo)) {
                micLine = (TargetDataLine) AudioSystem.getLine(micInfo);
                micLine.open(AUDIO_FORMAT, 8192);
                micLine.start();
                startCaptureLoop();
            }
        } catch (Exception ignored) {
            micLine = null;
        }

        connection.sendVoiceState(channel, muted, deafened, 0.0, false);
        connection.sendWebrtcSignal(null, channel, "offer", "rtc-audio-pcm-16k");
        return micLine != null || speakerLine != null;
    }

    public synchronized void leaveVoice() {
        if (!active) return;
        String prev = activeVoiceChannel;
        active = false;
        activeVoiceChannel = null;

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (micLine != null) {
            try {
                micLine.stop();
                micLine.close();
            } catch (Exception ignored) {
            }
            micLine = null;
        }
        if (speakerLine != null) {
            try {
                speakerLine.stop();
                speakerLine.close();
            } catch (Exception ignored) {
            }
            speakerLine = null;
        }
        if (prev != null && connection.isConnected()) {
            connection.sendVoiceState(prev, muted, deafened, 0.0, true);
        }
        if (localLevelCallback != null) {
            localLevelCallback.accept(0.0);
        }
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        if (active && activeVoiceChannel != null) {
            connection.sendVoiceState(activeVoiceChannel, muted, deafened, 0.0, false);
        }
    }

    public void setDeafened(boolean deafened) {
        this.deafened = deafened;
        if (active && activeVoiceChannel != null) {
            connection.sendVoiceState(activeVoiceChannel, muted, deafened, 0.0, false);
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isDeafened() {
        return deafened;
    }

    public String getActiveVoiceChannel() {
        return activeVoiceChannel;
    }

    public void playRemoteAudio(String channel, String base64Pcm) {
        if (!active || deafened || speakerLine == null || base64Pcm == null) return;
        if (activeVoiceChannel != null && !activeVoiceChannel.equalsIgnoreCase(channel)) return;
        try {
            byte[] pcm = Base64.getDecoder().decode(base64Pcm);
            speakerLine.write(pcm, 0, pcm.length);
        } catch (Exception ignored) {
        }
    }

    private void startCaptureLoop() {
        captureThread = new Thread(() -> {
            byte[] buffer = new byte[FRAME_BYTES];
            long lastStateSent = 0L;
            while (active && micLine != null && !Thread.currentThread().isInterrupted()) {
                int read = micLine.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                double rms = muted ? 0.0 : computeRms(buffer, read);
                if (localLevelCallback != null) {
                    localLevelCallback.accept(rms);
                }

                long now = System.currentTimeMillis();
                if (now - lastStateSent > 300) {
                    lastStateSent = now;
                    connection.sendVoiceState(activeVoiceChannel, muted, deafened, rms, false);
                }

                // Voice Activity Detection (VAD) sencilla + silenciado cuando está muteado
                if (!muted && rms > 0.015 && connection.isConnected()) {
                    byte[] slice = (read == buffer.length) ? buffer : java.util.Arrays.copyOf(buffer, read);
                    connection.sendVoiceAudio(activeVoiceChannel, Base64.getEncoder().encodeToString(slice));
                }
            }
        }, "voice-audio-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private static double computeRms(byte[] pcm, int len) {
        long sumSq = 0;
        int samples = len / 2;
        if (samples == 0) return 0.0;
        for (int i = 0; i < samples * 2; i += 2) {
            short s = (short) ((pcm[i + 1] << 8) | (pcm[i] & 0xFF));
            sumSq += (long) s * s;
        }
        return Math.min(1.0, Math.sqrt(sumSq / (double) samples) / 32768.0 * 4.0);
    }
}
