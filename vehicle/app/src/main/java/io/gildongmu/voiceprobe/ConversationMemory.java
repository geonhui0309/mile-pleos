package io.gildongmu.voiceprobe;

import org.json.JSONArray;
import org.json.JSONObject;

/** In-memory session ledger, committed only after completed playback. */
final class ConversationMemory {
    private final JSONArray asked = new JSONArray();
    private final JSONArray covered = new JSONArray();
    private final JSONArray answers = new JSONArray();
    private JSONObject pending;

    void clear() {
        while (asked.length() > 0) asked.remove(0);
        while (covered.length() > 0) covered.remove(0);
        while (answers.length() > 0) answers.remove(0);
        pending = null;
    }
    void clearPending() { pending = null; }
    JSONObject snapshot() throws Exception {
        return new JSONObject().put("askedQuestions", new JSONArray(asked.toString()))
                .put("coveredFactIds", new JSONArray(covered.toString()))
                .put("recentAnswers", new JSONArray(answers.toString()))
                .put("pendingQuestion", pending == null ? JSONObject.NULL : new JSONObject(pending.toString()));
    }
    void completed(JSONObject response) throws Exception {
        JSONArray ids = response.getJSONArray("evidenceIds");
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.getString(i);
            boolean known = false;
            for (int j = 0; j < covered.length(); j++) known |= covered.getString(j).equals(id);
            if (!known) covered.put(id);
        }
        answers.put(response.getString("answer"));
        if (answers.length() > 6) answers.remove(0);
        pending = null;
        if (!response.optString("followupQuestion", "").isEmpty() && asked.length() < 20) {
            pending = new JSONObject().put("question", response.getString("followupQuestion"))
                    .put("intent", response.getString("followupIntent"))
                    .put("topic", response.getString("followupTopic"));
            asked.put(pending);
        }
    }
}
