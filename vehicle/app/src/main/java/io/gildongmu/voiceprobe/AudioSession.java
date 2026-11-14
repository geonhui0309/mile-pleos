package io.gildongmu.voiceprobe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;

/** Owns only this app's audio focus; never restores playback or microphone on gain. */
final class AudioSession {
    static final AudioAttributes ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private final AudioManager manager;
    private final Handler handler;
    private final Runnable interrupted;
    private AudioFocusRequest active;
    private int generation;

    AudioSession(Context context, Handler handler, Runnable interrupted) {
        manager = context.getSystemService(AudioManager.class);
        this.handler = handler;
        this.interrupted = interrupted;
    }
    boolean acquire() {
        if (active != null) return true;
        final int request = ++generation;
        AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ATTRIBUTES).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    if (request == generation && active != null && change < 0) interrupted.run();
                }, handler).build();
        active = focus;
        try {
            if (manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true;
        } catch (RuntimeException ignored) { }
        release();
        return false;
    }
    void release() {
        generation++;
        AudioFocusRequest previous = active;
        active = null;
        if (previous != null) manager.abandonAudioFocusRequest(previous);
    }
}
