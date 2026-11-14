package io.gildongmu.voiceprobe;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import android.widget.Switch;
import android.widget.TextView;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Injected text callbacks verify routing, not acoustic echo cancellation or transcription accuracy. */
public class BargeInTest {
    @org.junit.Rule public final org.junit.rules.ExternalResource defaultVoice = new org.junit.rules.ExternalResource() {
        private android.content.SharedPreferences prefs;
        private java.util.Map<String, ?> original;
        @Override protected void before() {
            prefs = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getSharedPreferences("mile_ui", android.content.Context.MODE_PRIVATE);
            original = prefs.getAll();
            assertTrue(prefs.edit().putInt("voice_rate", 100).putInt("voice_pitch", 100).commit());
        }
        @Override protected void after() {
            var editor = prefs.edit();
            if (original.containsKey("narration_voice_questions"))
                editor.putBoolean("narration_voice_questions", (Boolean) original.get("narration_voice_questions"));
            else editor.remove("narration_voice_questions");
            for (String key : new String[]{"voice_rate", "voice_pitch"}) {
                if (original.containsKey(key)) editor.putInt(key, (Integer) original.get(key));
                else editor.remove(key);
            }
            assertTrue(editor.commit());
        }
    };
    private static Object field(MainActivity a,String name) {
        try { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(a); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private ConversationController controller(MainActivity a) { return (ConversationController)field(a,"conversation"); }
    private ai.pleos.playground.stt.listener.ResultListener listener(MainActivity a) {
        return (ai.pleos.playground.stt.listener.ResultListener)field(a,"recognitionListener");
    }
    private void settle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void start(ActivityScenario<MainActivity> s) throws Exception {
        AtomicBoolean ready=new AtomicBoolean();
        for(int i=0;i<40&&!ready.get();i++) {
            s.onActivity(a->ready.set((boolean)field(a,"ready")&&(boolean)field(a,"sttReady"))); Thread.sleep(100);
        }
        assertTrue("SDK initialization",ready.get());
        s.onActivity(a->{
            ((Switch)a.findViewById(R.id.barge_in_mode)).setChecked(true);
            a.findViewById(R.id.story_play).performClick();
        });
        ready.set(false);
        for(int i=0;i<40&&!ready.get();i++) {
            s.onActivity(a->ready.set((boolean)field(a,"bargeMonitoring")&&listener(a)!=null)); Thread.sleep(100);
        }
        assertTrue("No recognition session during narration",ready.get());
    }
    @Test public void explicitAddressRejectsEvenMisrecognizedNarration() {
        BargeInGate gate=new BargeInGate("첨성대는 신라 시대에 하늘을 살피던 곳이에요.");
        for(String text:new String[]{"", "음…", "첨성대는", "첨성대는 신라시대에 별을 살핀 곳입니다", "첨성대는 언제 만들었어?", "왜?", "그만해줘", "마일리지 적립"})
            assertFalse(text,gate.shouldInterrupt(text));
        assertFalse(gate.shouldInterrupt(null));assertFalse(gate.shouldInterrupt("가".repeat(1001)));
        assertTrue(gate.shouldInterrupt("마일, 언제 만들었어?"));
        assertTrue(gate.shouldInterrupt("마일"));
        assertFalse(gate.shouldInterrupt("마일, 첨성대는 신라 시대에 하늘을 살피던 곳이에요."));
        assertTrue(gate.shouldInterrupt("마일, 첨성대는 신라 시대에 누가 만들었어?"));
        assertEquals("언제 만들었어?",BargeInGate.questionText("마일, 언제 만들었어?"));
        assertEquals("그만해줘",BargeInGate.questionText("마일아, 그만해줘"));
        assertEquals("",BargeInGate.questionText("마일"));
        for (String variant : new String[]{"마일", "매일", "말일", "말이"}) {
            String cue = variant + ", 질문할게. ";
            assertTrue(gate.shouldInterrupt(cue + "언제 만들었어?"));
            assertEquals("언제 만들었어?", BargeInGate.questionText(cue + "언제 만들었어?"));
            assertFalse(gate.shouldInterrupt(cue + "첨성대는 신라 시대에 하늘을 살피던 곳이에요."));
        }
        assertFalse(gate.shouldInterrupt("매일 첨성대를 봐요"));
        assertFalse(gate.shouldInterrupt("말이 많아요"));
        assertTrue(gate.isDirectQuestion("첨성대는 언제 만들었어?"));
        assertFalse(gate.isDirectQuestion("옛날 사람들은 별을 살폈어요."));
        assertTrue(gate.shouldInterrupt("질문할게. 첨성대는 언제 만들었어?"));
        assertEquals("첨성대는 언제 만들었어?", BargeInGate.questionText("질문할게. 첨성대는 언제 만들었어?"));
        assertFalse(gate.shouldInterrupt("그런데 질문할게 있었어요"));
        assertFalse(new BargeInGate("제가 질문할게요").shouldInterrupt("질문할게. 언제 만들었어?"));
        assertFalse(new BargeInGate("이 해설에서는 마일을 소개해요").shouldInterrupt("마일"));
    }
    @Test public void questionStopsTtsKeepsSameSttAndRejectsLatePlayback() throws Exception {
        java.util.concurrent.CountDownLatch releaseNetwork = new java.util.concurrent.CountDownLatch(1);
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            // Keep the AI queue deterministic: this test verifies dispatch, not a paid network response.
            s.onActivity(a->((java.util.concurrent.ExecutorService)field(a,"aiWorker")).execute(()->{
                try { releaseNetwork.await(8, java.util.concurrent.TimeUnit.SECONDS); }
                catch(InterruptedException e){Thread.currentThread().interrupt();}
            }));
            AtomicReference<ai.pleos.playground.stt.listener.ResultListener> stt=new AtomicReference<>();
            AtomicReference<ai.pleos.playground.tts.listener.EventListener> tts=new AtomicReference<>();
            s.onActivity(a->{
                stt.set(listener(a)); tts.set((ai.pleos.playground.tts.listener.EventListener)field(a,"playbackListener"));
                stt.get().onUpdated("첨성대는",false);
                stt.get().onUpdated("마일",false);
            });settle();
            s.onActivity(a->{assertEquals(ConversationController.State.SPEAKING,controller(a).state());stt.get().onUpdated("첨성대는 언제 만들었어",false);});settle();
            s.onActivity(a->{assertEquals(ConversationController.State.SPEAKING,controller(a).state());stt.get().onUpdated("첨성대는 언제 만들었어?",true);});settle();
            s.onActivity(a->{
                assertEquals(ConversationController.State.GENERATING,controller(a).state());
                assertNull(field(a,"playbackListener"));
                assertFalse((boolean)field(a,"bargeMonitoring"));
                tts.get().onDone();tts.get().onStop();stt.get().onEndedRecognition();stt.get().onUpdated("매일, 질문할게. 첨성대는 언제 만들었어?",true);
            });settle();
            s.onActivity(a->{
                assertEquals(ConversationController.State.GENERATING,controller(a).state());
                assertNotNull(field(a,"aiRequest"));
                assertEquals("첨성대는 언제 만들었어?",((TextView)a.findViewById(R.id.transcript_text)).getText().toString());
                a.findViewById(R.id.stop_audio).performClick();
                stt.get().onUpdated("높이는?",true);tts.get().onDone();
            });settle();
            s.onActivity(a->{assertEquals(ConversationController.State.IDLE,controller(a).state());assertNull(listener(a));assertNull(field(a,"aiRequest"));});
        } finally { releaseNetwork.countDown(); }
    }
    @Test public void optingOutClosesMonitorAndLateResultsDoNothing() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            s.onActivity(a->{
                ai.pleos.playground.stt.listener.ResultListener old=listener(a);
                ((Switch)a.findViewById(R.id.barge_in_mode)).setChecked(false);
                assertNull(listener(a));assertFalse((boolean)field(a,"bargeMonitoring"));
                old.onUpdated("마일, 언제 만들었어",true);
            });settle();
            s.onActivity(a->assertEquals(ConversationController.State.SPEAKING,controller(a).state()));
        }
    }
    @Test public void adjustedNarrationStopsWithoutClosingRecognition() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("local_voice")));
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var prefs = context.getSharedPreferences("mile_ui", android.content.Context.MODE_PRIVATE);
        int originalRate = prefs.getInt("voice_rate", 100);
        boolean hadRate = prefs.contains("voice_rate");
        assertTrue(prefs.edit().putInt("voice_rate", 109).commit());
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(MainActivity.class)) {
            start(s);
            s.onActivity(a -> {
                assertNotNull(field(a, "adjustedSpeech"));
                listener(a).onUpdated("마일, 질문할게. 언제 만들었어?", false);
            });
            settle();
            s.onActivity(a -> {
                assertEquals(ConversationController.State.LISTENING, controller(a).state());
                assertNull(field(a, "adjustedSpeech"));
                assertNotNull(listener(a));
            });
        } finally {
            var editor = prefs.edit();
            assertTrue((hadRate ? editor.putInt("voice_rate", originalRate) : editor.remove("voice_rate")).commit());
        }
    }
    @Test public void narrationCompletionClosesMicAndMonitorErrorKeepsNarration() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            AtomicReference<ai.pleos.playground.tts.listener.EventListener> tts=new AtomicReference<>();
            s.onActivity(a->{
                tts.set((ai.pleos.playground.tts.listener.EventListener)field(a,"playbackListener"));
                listener(a).onError(ai.pleos.playground.stt.constant.ErrorType.MIC_ERROR);
            });settle();
            s.onActivity(a->{
                assertEquals(ConversationController.State.SPEAKING,controller(a).state());assertNull(listener(a));
                tts.get().onDone();
            });settle();
            s.onActivity(a->{assertEquals(ConversationController.State.IDLE,controller(a).state());assertNull(listener(a));
                assertEquals("마이크 꺼짐",((TextView)a.findViewById(R.id.microphone_state)).getText().toString());});
        }
    }
    @Test public void endingNarrationWhileMonitoringClosesMic() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            s.onActivity(a->{
                MileScreen screen=(MileScreen)field(a,"screen");
                screen.voiceEvent("BARGE_LISTENING");screen.render("conversation",controller(a).state(),true);
                assertTrue(((TextView)a.findViewById(R.id.microphone_state)).getText().toString().startsWith("마이크 켜짐"));
                ((ai.pleos.playground.tts.listener.EventListener)field(a,"playbackListener")).onDone();
            });settle();
            s.onActivity(a->{assertFalse((boolean)field(a,"bargeMonitoring"));assertNull(listener(a));
                assertEquals(ConversationController.State.IDLE,controller(a).state());});
        }
    }
    @Test public void navigationFocusLossClosesBargeMonitorAndDoesNotRestart() throws Exception {
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.media.AudioManager audio=c.getSystemService(android.media.AudioManager.class);
        android.media.AudioFocusRequest navigation=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build()).build();
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            AtomicReference<ai.pleos.playground.stt.listener.ResultListener> old=new AtomicReference<>();
            s.onActivity(a->old.set(listener(a)));
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,audio.requestAudioFocus(navigation));
            AtomicBoolean interrupted=new AtomicBoolean();
            for(int i=0;i<30&&!interrupted.get();i++) {
                s.onActivity(a->interrupted.set(controller(a).state()==ConversationController.State.INTERRUPTED));Thread.sleep(100);
            }
            assertTrue("Actual focus loss",interrupted.get());
            audio.abandonAudioFocusRequest(navigation); old.get().onUpdated("마일, 언제 만들었어",true);Thread.sleep(300);settle();
            s.onActivity(a->{assertFalse((boolean)field(a,"bargeMonitoring"));assertNull(listener(a));
                assertEquals(ConversationController.State.INTERRUPTED,controller(a).state());});
        } finally {audio.abandonAudioFocusRequest(navigation);}
    }

    @Test public void completedEchoRearmsWithinNarrationAndDiscardsOldSegment() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            AtomicReference<ai.pleos.playground.stt.listener.ResultListener> old=new AtomicReference<>();
            s.onActivity(a->{old.set(listener(a));old.get().onUpdated("첨성대는",true);});
            Thread.sleep(400);settle();
            s.onActivity(a->{assertNotNull(listener(a));assertNotSame(old.get(),listener(a));
                old.get().onUpdated("마일, 언제 만들었어",true);});settle();
            s.onActivity(a->assertEquals(ConversationController.State.SPEAKING,controller(a).state()));
        }
    }
    @Test public void expiredMonitoringClosesMicWithoutStoppingNarration() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            s.onActivity(a->{
                try {Field f=MainActivity.class.getDeclaredField("bargeDeadline");f.setAccessible(true);f.setLong(a,0);}
                catch(Exception e){throw new AssertionError(e);}
                listener(a).onUpdated("",true);
            });settle();
            s.onActivity(a->{assertNull(listener(a));assertFalse((boolean)field(a,"bargeMonitoring"));
                assertEquals(ConversationController.State.SPEAKING,controller(a).state());});
        }
    }

    @Test public void alteredNarrationCannotDispatchAiAndWakeOnlyWaitsForQuestion() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)) {
            start(s);
            s.onActivity(a->listener(a).onUpdated("첨성대는 신라 시대 별을 살펴보던 곳입니다",false));settle();
            s.onActivity(a->{assertEquals(ConversationController.State.SPEAKING,controller(a).state());assertNull(field(a,"aiRequest"));
                listener(a).onUpdated("마일",true);});Thread.sleep(350);settle();
            s.onActivity(a->{assertEquals(ConversationController.State.LISTENING,controller(a).state());assertNull(field(a,"aiRequest"));
                listener(a).onUpdated("그만해줘",true);});settle();
            s.onActivity(a->{assertEquals(ConversationController.State.IDLE,controller(a).state());assertNull(field(a,"aiRequest"));});
        }
    }

}
