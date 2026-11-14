package io.gildongmu.voiceprobe;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Real service concurrency, not injected recognition callbacks or human speech accuracy. */
public class SdkDuplexTest {
    @Test public void actualSdkAcceptsMicRequestDuringTts() throws Exception {
        android.content.Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(c, MainActivity.class).putExtra("debug_skip_voice_initialization", true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            ai.pleos.playground.tts.TextToSpeech tts = new ai.pleos.playground.tts.TextToSpeech(c, ai.pleos.playground.tts.constant.Mode.ON_DEVICE);
            ai.pleos.playground.stt.SpeechToText stt = new ai.pleos.playground.stt.SpeechToText(c, ai.pleos.playground.stt.constant.Mode.ON_DEVICE);
            List<String> events = new CopyOnWriteArrayList<>();
            CountDownLatch initialized = new CountDownLatch(2), started = new CountDownLatch(1), listening = new CountDownLatch(1);
            AudioSession focus = new AudioSession(c, new Handler(Looper.getMainLooper()), () -> events.add("FOCUS_LOSS"));
            ai.pleos.playground.tts.listener.EventListener playback = new ai.pleos.playground.tts.listener.EventListener() {
                public void onReady() { events.add("TTS_READY"); }
                public void onStart() { events.add("TTS_START"); started.countDown(); }
                public void onUpdatedRms(double rms) {}
                public void onDone() { events.add("TTS_DONE"); }
                public void onStop() { events.add("TTS_STOP"); }
                public void onError(ai.pleos.playground.tts.constant.ErrorType e) { events.add("TTS_ERROR:"+e); }
            };
            ai.pleos.playground.stt.listener.ResultListener recognition = new ai.pleos.playground.stt.listener.ResultListener() {
                public void onReady() { events.add("STT_READY"); listening.countDown(); }
                public void onStartedRecognition() { events.add("STT_LISTENING"); }
                public void onUpdated(String text, boolean completed) { events.add("STT_TEXT:characters="+(text==null?0:text.length())+":final="+completed); }
                public void onUpdatedEpdData(long on,long off) { if(on>=0) events.add("EPD"); }
                public void onUpdatedRms(double rms) {}
                public void onEndedRecognition() { events.add("STT_END"); }
                public void onError(ai.pleos.playground.stt.constant.ErrorType e) { events.add("STT_ERROR:"+e); }
            };
            try {
                tts.initialize(new ai.pleos.playground.tts.listener.OnInitializeListener() {
                    public void onSuccess() { events.add("TTS_INIT"); initialized.countDown(); }
                    public void onFailure(ai.pleos.playground.tts.constant.ErrorType e) { events.add("TTS_INIT_ERROR:"+e); }
                });
                stt.initialize(new ai.pleos.playground.stt.listener.OnInitializeListener() {
                    public void onSuccess() { events.add("STT_INIT"); initialized.countDown(); }
                    public void onFailure(ai.pleos.playground.stt.constant.ErrorType e) { events.add("STT_INIT_ERROR:"+e); }
                });
                assertTrue("SDK init: "+events, initialized.await(12,TimeUnit.SECONDS));
                tts.setLocale(Locale.KOREA); stt.setLocale(Locale.KOREA);
                tts.setAudioAttributes(AudioSession.ATTRIBUTES);
                tts.addEventListener(playback); stt.addListener(recognition);
                assertTrue(focus.acquire());
                tts.speak("첨성대는 신라 시대에 하늘을 살피던 곳이에요. 옛사람들은 별의 움직임을 보며 계절의 변화를 알아보았어요. 하늘을 관찰하는 일은 농사와 생활에도 관련이 있었어요. 오늘 우리는 이곳을 보며 옛사람들의 생각을 상상해 볼 수 있어요.",true);
                assertTrue("TTS start: "+events,started.await(8,TimeUnit.SECONDS));
                stt.request();
                assertTrue("STT alongside TTS: "+events,listening.await(8,TimeUnit.SECONDS));
                Thread.sleep(2500);
                assertFalse("Focus loss: "+events, events.contains("FOCUS_LOSS"));
                assertFalse("TTS unexpectedly stopped: "+events,events.contains("TTS_STOP"));
                assertFalse("TTS ended before overlap checked: "+events,events.contains("TTS_DONE"));
                assertFalse("SDK error: "+events,events.stream().anyMatch(e->e.contains("ERROR")));
            } finally {
                android.util.Log.i("MileDuplexTest", "EVENTS="+events);
                stt.removeListener(recognition); stt.stop(); stt.release();
                tts.removeEventListener(playback); tts.stop(); tts.release(); focus.release();
            }
        }
    }
}
