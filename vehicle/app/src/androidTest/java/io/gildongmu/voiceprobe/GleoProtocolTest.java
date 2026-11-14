package io.gildongmu.voiceprobe;

import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class GleoProtocolTest {
    @Test public void acceptsGleoQualifiedActionsAndExistingShortActions() {
        for (String action : new String[]{GleoProtocol.ASK, GleoProtocol.STOP}) {
            assertEquals(action, GleoProtocol.action(new Intent(action)));
            assertEquals(action, GleoProtocol.action(new Intent("io.gildongmu.voiceprobe." + action)));
        }
        assertNull(GleoProtocol.action(new Intent()));
        assertEquals("other.ASK_STORY_QUESTION", GleoProtocol.action(new Intent("other.ASK_STORY_QUESTION")));
    }
    @Test public void acceptsCompleteFreeQuestion() throws Exception {
        String question = "저 고분은 왜 여러 개 모여 있고 누가 만들었어?";
        assertEquals(question, GleoProtocol.question(new Intent(GleoProtocol.ASK)
                .putExtra("ACTION_PARAM", new JSONObject().put("question", question).toString())));
        assertEquals(question, GleoProtocol.question(new Intent(GleoProtocol.ASK)
                .putExtra("question", question)));
        String wrapped = JSONObject.quote(new JSONObject().put("question", question).toString());
        assertEquals(question, GleoProtocol.question(new Intent(GleoProtocol.ASK)
                .putExtra("ACTION_PARAM", wrapped)));
    }

    @Test public void rejectsInvalidInputs() throws Exception {
        for (String raw : new String[]{"{}", "{bad", "[]", "{\"question\":3}",
                "{\"question\":\"  \"}"}) {
            try {
                GleoProtocol.question(new Intent().putExtra("ACTION_PARAM", raw));
                fail("Invalid input was accepted: " + raw);
            } catch (JSONException expected) { }
        }
        try {
            GleoProtocol.question(new Intent().putExtra("question", "가".repeat(1001)));
            fail("Oversized question was accepted");
        } catch (JSONException expected) { }
    }

    @Test public void callbackIsSilentAndDoesNotClaimAnAnswer() throws Exception {
        JSONObject result = new JSONObject(GleoProtocol.callback(GleoProtocol.ASK, "success",
                "The question was received.", "insufficient_evidence"));
        assertEquals("silent", result.getString("mode"));
        assertEquals(GleoProtocol.ASK, result.getString("functionName"));
        assertEquals("insufficient_evidence", new org.json.JSONArray(result.getString("data"))
                .getJSONObject(0).getString("answerState"));
        assertTrue(GleoProtocol.answer().contains("보류"));
    }
}
