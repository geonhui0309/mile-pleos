package io.gildongmu.voiceprobe;

import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VoiceFlowTest {
    @Test public void acceptsFinalAfterEndAndIgnoresResultsAfterCancellation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            java.util.concurrent.atomic.AtomicReference<ai.pleos.playground.stt.listener.ResultListener> callback = new java.util.concurrent.atomic.AtomicReference<>();
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 40 && !ready.get(); i++) {
                scenario.onActivity(a -> ready.set(((Button)a.findViewById(R.id.ask_voice)).isEnabled()));
                Thread.sleep(250);
            }
            assertTrue(ready.get());
            scenario.onActivity(a -> {
                a.findViewById(R.id.ask_voice).performClick();
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("recognitionListener");
                    field.setAccessible(true);
                    callback.set((ai.pleos.playground.stt.listener.ResultListener)field.get(a));
                } catch (Exception error) { throw new AssertionError(error); }
                callback.get().onEndedRecognition();
                callback.get().onUpdated("첨성대는 언제 만들었어", true);
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> {
                assertTrue(((TextView)a.findViewById(R.id.transcript_text)).getText().toString().contains("첨성대는 언제 만들었어"));
                a.findViewById(R.id.stop_audio).performClick();
                callback.get().onUpdated("첨성대 높이는?", true);
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> assertTrue(((TextView)a.findViewById(R.id.transcript_text)).getText().toString().contains("첨성대는 언제 만들었어")));
        }
    }
    @Test public void hesitationRearmsRecognitionAndExplicitStopSkipsAi() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 40 && !ready.get(); i++) {
                scenario.onActivity(a -> ready.set(((Button)a.findViewById(R.id.ask_voice)).isEnabled()));
                Thread.sleep(250);
            }
            assertTrue(ready.get());
            java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(a -> {
                a.findViewById(R.id.ask_voice).performClick();
                ai.pleos.playground.stt.listener.ResultListener old = listener(a);
                old.onUpdated("음… 그게…", true);
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.postDelayed(() -> {
                    try {
                        assertNotNull(listener(a));
                        assertNotSame(old, listener(a));
                        old.onUpdated("늦은 예전 결과", true);
                        listener(a).onUpdated("별이 사는 집 같아", true);
                        handler.post(() -> {
                            try {
                                assertTrue(((TextView)a.findViewById(R.id.transcript_text)).getText().toString().endsWith("별이 사는 집 같아"));
                                a.findViewById(R.id.stop_audio).performClick();
                                a.findViewById(R.id.ask_voice).performClick();
                                listener(a).onUpdated("그만 해줘", true);
                                handler.post(() -> {
                                    try {
                                        java.lang.reflect.Field field = MainActivity.class.getDeclaredField("aiRequest");
                                        field.setAccessible(true);
                                        assertNull(field.get(a));
                                    } catch (Throwable error) { failure.set(error); }
                                    finally { completed.countDown(); }
                                });
                            } catch (Throwable error) { failure.set(error); completed.countDown(); }
                        });
                    } catch (Throwable error) { failure.set(error); completed.countDown(); }
                }, 350);
            });
            assertTrue("Continuation callback did not finish", completed.await(8, java.util.concurrent.TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }
    private static ai.pleos.playground.stt.listener.ResultListener listener(MainActivity a) {
        try {
            java.lang.reflect.Field f = MainActivity.class.getDeclaredField("recognitionListener");
            f.setAccessible(true);
            return (ai.pleos.playground.stt.listener.ResultListener)f.get(a);
        } catch (Exception e) { throw new AssertionError(e); }
    }

    @Test public void voiceInputCanStartStopAndBeRequestedAgain() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 40 && !ready.get(); i++) {
                scenario.onActivity(a -> ready.set(((Button)a.findViewById(R.id.ask_voice)).isEnabled()));
                Thread.sleep(250);
            }
            assertTrue("STT initialization did not enable the microphone button", ready.get());
            for (int attempt = 0; attempt < 2; attempt++) {
                scenario.onActivity(a -> a.findViewById(R.id.ask_voice).performClick());
                Thread.sleep(500);
                scenario.onActivity(a -> {
                    a.findViewById(R.id.stop_audio).performClick();
                    assertTrue(((Button)a.findViewById(R.id.ask_voice)).isEnabled());
                });
            }
            scenario.onActivity(a -> {
                ((EditText)a.findViewById(R.id.question_input)).setText("첨성대는 언제 만들어졌어?");
                a.findViewById(R.id.question_send).performClick();
                assertTrue(((TextView)a.findViewById(R.id.transcript_text)).getText().toString().contains("첨성대는 언제 만들어졌어"));
                a.findViewById(R.id.stop_audio).performClick();
                ((EditText)a.findViewById(R.id.question_input)).setText("높이는?");
                a.findViewById(R.id.question_send).performClick();
                assertTrue(((TextView)a.findViewById(R.id.transcript_text)).getText().toString().contains("높이는"));
                a.findViewById(R.id.stop_audio).performClick();
            });
        }
    }
}
