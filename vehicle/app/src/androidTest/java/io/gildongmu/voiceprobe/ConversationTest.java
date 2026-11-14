package io.gildongmu.voiceprobe;

import android.widget.Button;
import android.widget.Switch;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConversationTest {
    private ai.pleos.playground.tts.listener.EventListener callback;
    private static Field field(String name) throws Exception {
        Field f = MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @Test public void optedInQuestionCanListenOnceAndStopPreventsLateRestart() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean();
            for (int i = 0; i < 40 && !ready.get(); i++) {
                scenario.onActivity(a -> ready.set(((Button)a.findViewById(R.id.ask_voice)).isEnabled()));
                Thread.sleep(250);
            }
            assertTrue(ready.get());
            scenario.onActivity(a -> {
                assertFalse(((Switch)a.findViewById(R.id.conversation_mode)).isChecked());
                ((Switch)a.findViewById(R.id.conversation_mode)).setChecked(true);
                try {
                    ConversationController controller = (ConversationController)field("conversation").get(a);
                    controller.manualTurn();
                    int request = controller.beginAnswer();
                    assertTrue(controller.answerReady(request, true));
                    org.json.JSONObject generated = new org.json.JSONObject().put("answer", "하늘을 함께 상상했구나.")
                            .put("evidenceIds", new org.json.JSONArray()).put("followupQuestion", "그 별집에서 어떤 이야기가 들릴 것 같아요?")
                            .put("followupIntent", "imagination").put("followupTopic", "experience");
                    field("responseForMemory").set(a, generated);
                    field("responseUserQuestion").set(a, "별집 같아");
                    field("responseSpokenText").set(a, "하늘을 함께 상상했구나. 그 별집에서 어떤 이야기가 들릴 것 같아요?");
                    ConversationMemory memory = (ConversationMemory)field("memory").get(a);
                    assertTrue(memory.snapshot().isNull("pendingQuestion"));
                    java.lang.reflect.Method factory = MainActivity.class.getDeclaredMethod("playbackEvents", int.class);
                    factory.setAccessible(true);
                    callback = (ai.pleos.playground.tts.listener.EventListener)factory.invoke(a, request);
                    callback.onDone();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            scenario.onActivity(a -> {
                try {
                    assertEquals(ConversationController.State.LISTENING, ((ConversationController)field("conversation").get(a)).state());
                    ConversationMemory memory = (ConversationMemory)field("memory").get(a);
                    assertEquals("그 별집에서 어떤 이야기가 들릴 것 같아요?", memory.snapshot().getJSONObject("pendingQuestion").getString("question"));
                    assertEquals(2, ((org.json.JSONArray)field("history").get(a)).length());
                    a.findViewById(R.id.stop_audio).performClick();
                    callback.onDone();
                } catch (Exception e) { throw new AssertionError(e); }
            });
            scenario.onActivity(a -> {
                try { assertEquals(ConversationController.State.IDLE, ((ConversationController)field("conversation").get(a)).state()); }
                catch (Exception e) { throw new AssertionError(e); }
                try { assertTrue(((ConversationMemory)field("memory").get(a)).snapshot().isNull("pendingQuestion")); }
                catch (Exception e) { throw new AssertionError(e); }
                ((Switch)a.findViewById(R.id.conversation_mode)).setChecked(false);
            });
        }
    }
}
