package io.gildongmu.voiceprobe;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AiClientTest {
    @Test public void requiresValidStatusProviderTextAndEvidence() throws Exception {
        assertEquals("answered", AiClient.parse(new JSONObject("{\"provider\":\"openai\",\"status\":\"answered\",\"answer\":\"약 9미터예요.\",\"evidenceIds\":[\"height\"]}")).getString("status"));
        for (String ids : new String[]{"[]", "[\"invented\"]"}) {
            try {
                AiClient.parse(new JSONObject("{\"provider\":\"openai\",\"status\":\"answered\",\"answer\":\"답\",\"evidenceIds\":" + ids + "}"));
                fail("Unsupported output accepted");
            } catch (java.io.IOException expected) { }
        }
    }

    @Test public void validatesReflectionAndFollowupIntent() throws Exception {
        JSONObject response = new JSONObject().put("provider", "openai").put("status", "answered")
                .put("answer", "별이 사는 집을 상상했구나.").put("responseKind", "reflection")
                .put("evidenceIds", new org.json.JSONArray()).put("followupQuestion", "").put("followupIntent", "none");
        assertEquals("reflection", AiClient.parse(response).getString("responseKind"));
        response.put("followupQuestion", "그 집에서 어떤 이야기가 들릴까요?").put("followupIntent", "imagination").put("followupTopic", "experience");
        assertEquals("experience", AiClient.parse(response).getString("followupTopic"));
        response.put("followupTopic", "none");
        try { AiClient.parse(response); fail("Missing generated-question topic accepted"); }
        catch (java.io.IOException expected) { }
        response.put("followupIntent", "override");
        try { AiClient.parse(response); fail("Invalid intent accepted"); }
        catch (java.io.IOException expected) { }
    }

    @Test public void cancellationPreventsNetworkRequest() throws Exception {
        AiClient client = new AiClient();
        client.cancel();
        try { client.ask("http://127.0.0.1:1/answer", new JSONObject()); fail("Cancelled request started"); }
        catch (java.io.IOException expected) { assertEquals("not_configured", expected.getMessage()); }
    }
}
