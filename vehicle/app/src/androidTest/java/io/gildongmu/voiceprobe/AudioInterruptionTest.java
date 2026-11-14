package io.gildongmu.voiceprobe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioInterruptionTest {
    @Test public void navigationFocusLossCancelsListeningAndDoesNotReopenOnRelease() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AudioManager audio = context.getSystemService(AudioManager.class);
        AudioFocusRequest navigation = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            for (int i = 0; i < 40 && !ready.get(); i++) {
                scenario.onActivity(a -> ready.set(((Button)a.findViewById(R.id.ask_voice)).isEnabled()));
                Thread.sleep(250);
            }
            assertTrue(ready.get());
            scenario.onActivity(a -> a.findViewById(R.id.ask_voice).performClick());
            Thread.sleep(300);
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(navigation));
            for (int i = 0; i < 20; i++) {
                scenario.onActivity(a -> ready.set(controller(a).state() == ConversationController.State.INTERRUPTED));
                if (ready.get()) break;
                Thread.sleep(100);
            }
            assertTrue("No actual audio-focus loss reached the app", ready.get());
            audio.abandonAudioFocusRequest(navigation);
            Thread.sleep(300);
            scenario.onActivity(a -> assertEquals(ConversationController.State.INTERRUPTED, controller(a).state()));
        } finally { audio.abandonAudioFocusRequest(navigation); }
    }
    @Test public void navigationFocusStopsPlayingNarrationWithoutRestart() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AudioManager audio = context.getSystemService(AudioManager.class);
        AudioFocusRequest navigation = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).build();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> a.findViewById(R.id.discover_story).performClick());
            Thread.sleep(2700);
            scenario.onActivity(a -> {
                android.view.View story = a.findViewById(R.id.story_play);
                assertTrue(story.isShown());
                story.performClick();
            });
            java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
            for (int i = 0; i < 60 && !started.get(); i++) {
                scenario.onActivity(a -> started.set("이야기 중".contentEquals(
                        ((MileScreen) ((android.view.ViewGroup) a.findViewById(android.R.id.content))
                                .getChildAt(0)).output.getText())));
                if (!started.get()) Thread.sleep(100);
            }
            assertTrue("Pleos TTS did not start narration", started.get());
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(navigation));
            java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
            for (int i = 0; i < 30 && !interrupted.get(); i++) {
                scenario.onActivity(a -> interrupted.set(controller(a).state() == ConversationController.State.INTERRUPTED));
                if (!interrupted.get()) Thread.sleep(100);
            }
            assertTrue("Navigation audio focus did not interrupt narration", interrupted.get());
            audio.abandonAudioFocusRequest(navigation);
            Thread.sleep(350);
            scenario.onActivity(a -> assertEquals(ConversationController.State.INTERRUPTED, controller(a).state()));
        } finally { audio.abandonAudioFocusRequest(navigation); }
    }

    private static ConversationController controller(MainActivity a) {
        try {
            java.lang.reflect.Field f = MainActivity.class.getDeclaredField("conversation");
            f.setAccessible(true);
            return (ConversationController)f.get(a);
        } catch (Exception e) { throw new AssertionError(e); }
    }
}
