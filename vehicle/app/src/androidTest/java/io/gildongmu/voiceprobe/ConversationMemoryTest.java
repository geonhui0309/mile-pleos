package io.gildongmu.voiceprobe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationMemoryTest {
    private JSONObject response(String question, String topic) throws Exception {
        return new JSONObject().put("answer", "현재 답변").put("evidenceIds", new JSONArray().put("purpose"))
                .put("followupQuestion", question).put("followupIntent", question.isEmpty() ? "none" : "factual")
                .put("followupTopic", topic);
    }
    @Test public void remembersCompletedQuestionBeyondRecentHistoryAndClearsPending() throws Exception {
        ConversationMemory memory = new ConversationMemory();
        JSONObject generated = response("별을 살피며 농사 때를 어떻게 생각했을까요?", "purpose");
        assertTrue(memory.snapshot().isNull("pendingQuestion"));
        memory.completed(generated);
        for (int i = 0; i < 8; i++) memory.completed(response("", "none"));
        JSONObject snapshot = memory.snapshot();
        assertEquals(1, snapshot.getJSONArray("askedQuestions").length());
        assertEquals(1, snapshot.getJSONArray("coveredFactIds").length());
        assertEquals(6, snapshot.getJSONArray("recentAnswers").length());
        assertTrue(snapshot.isNull("pendingQuestion"));
        memory.completed(generated);
        assertEquals(generated.getString("followupQuestion"), memory.snapshot().getJSONObject("pendingQuestion").getString("question"));
        memory.clearPending();
        assertEquals(2, memory.snapshot().getJSONArray("askedQuestions").length());
        assertTrue(memory.snapshot().isNull("pendingQuestion"));
        snapshot.getJSONArray("askedQuestions").put(new JSONObject());
        assertEquals(2, memory.snapshot().getJSONArray("askedQuestions").length());
        memory.clear();
        assertEquals(0, memory.snapshot().getJSONArray("askedQuestions").length());
    }
}
