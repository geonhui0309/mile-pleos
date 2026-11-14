package io.gildongmu.voiceprobe;

import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class GleoProtocol {
    static final String ASK = "ASK_STORY_QUESTION";
    static final String STOP = "STOP_NEARBY_STORY";
    static final String RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE";

    static String action(Intent intent) {
        String action = intent.getAction();
        // Gleo sends package-qualified actions; retain short actions for existing callers.
        String prefix = "io.gildongmu.voiceprobe.";
        return action != null && action.startsWith(prefix) ? action.substring(prefix.length()) : action;
    }

    static String question(Intent intent) throws JSONException {
        // The official prose describes separate extras; its code example uses ACTION_PARAM.
        String question;
        if (intent.hasExtra("ACTION_PARAM")) {
            Object raw = intent.getStringExtra("ACTION_PARAM");
            if (!(raw instanceof String) || ((String) raw).length() > 8192)
                throw new JSONException("Invalid action parameters");
            Object parsed = new org.json.JSONTokener((String) raw).nextValue();
            // Some official examples wrap the JSON object in another JSON string.
            if (parsed instanceof String) parsed = new org.json.JSONTokener((String) parsed).nextValue();
            if (!(parsed instanceof JSONObject)) throw new JSONException("Expected an object");
            Object value = ((JSONObject) parsed).opt("question");
            if (!(value instanceof String)) throw new JSONException("A question string is required");
            question = (String) value;
        } else {
            question = intent.getStringExtra("question");
        }
        if (question == null || question.trim().isEmpty() || question.length() > 1000)
            throw new JSONException("Question must contain 1 to 1000 characters");
        return question.trim();
    }

    static String callback(String action, String status, String message, String answerState)
            throws JSONException {
        return new JSONObject().put("functionName", action).put("status", status)
                .put("message", message).put("mode", "silent")
                .put("data", new JSONArray().put(new JSONObject()
                        .put("answerState", answerState)).toString()).toString();
    }

    static String answer() {
        // shortcut: transport probe only; replace with grounded retrieval before content QA testing.
        return "질문을 전달받았어요. 아직 검수한 자료가 연결되지 않아 답변을 보류할게요.";
    }
}
