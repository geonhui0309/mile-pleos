package io.gildongmu.voiceprobe;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

final class AiClient {
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;

    void cancel() {
        cancelled = true;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    JSONObject ask(String endpoint, JSONObject request) throws Exception {
        if (cancelled || endpoint.isEmpty()) throw new java.io.IOException("not_configured");
        HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
        connection = c;
        try {
            if (cancelled) throw new java.io.IOException("cancelled");
            c.setConnectTimeout(3000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream output = c.getOutputStream()) { output.write(body); }
            if (c.getResponseCode() != 200) {
                throw new java.io.IOException(c.getResponseCode() == 503 ? "ai_not_ready" : "ai_unavailable");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (java.io.InputStream input = c.getInputStream()) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (cancelled || output.size() + read > 16384) throw new java.io.IOException("invalid_response");
                    output.write(buffer, 0, read);
                }
            }
            return parse(new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8)));
        } finally { c.disconnect(); }
    }

    static JSONObject parse(JSONObject result) throws Exception {
        String status = result.getString("status");
        String text = result.getString("answer");
        if (!result.getString("provider").equals("openai") || text.trim().isEmpty() || text.length() > 600
                || !(status.equals("answered") || status.equals("clarify") || status.equals("insufficient_evidence")))
            throw new java.io.IOException("invalid_response");
        JSONArray ids = result.getJSONArray("evidenceIds");
        Object followup = result.opt("followupQuestion");
        if (followup != null && (!(followup instanceof String) || ((String)followup).length() > 120))
            throw new java.io.IOException("invalid_followup");
        String kind = result.optString("responseKind", "factual");
        String intent = result.optString("followupIntent", "none");
        if (!java.util.Arrays.asList("none", "factual", "observation", "imagination", "reflection").contains(intent))
            throw new java.io.IOException("invalid_intent");
        String topic = result.optString("followupTopic", "none");
        if (!java.util.Arrays.asList("none", "purpose", "date", "height", "tiers", "shape", "method", "experience").contains(topic))
            throw new java.io.IOException("invalid_topic");
        boolean hasQuestion = !result.optString("followupQuestion", "").trim().isEmpty();
        if (hasQuestion ? intent.equals("none") || topic.equals("none") : !intent.equals("none") || !topic.equals("none"))
            throw new java.io.IOException("inconsistent_followup");
        if (!(kind.equals("factual") || kind.equals("reflection"))) throw new java.io.IOException("invalid_kind");
        if (kind.equals("reflection") && (!status.equals("answered") || ids.length() != 0 || (hasQuestion && (!topic.equals("experience") || !(intent.equals("imagination") || intent.equals("reflection"))))))
            throw new java.io.IOException("invalid_reflection");
        if (status.equals("answered") && kind.equals("factual") && ids.length() == 0) throw new java.io.IOException("missing_evidence");
        for (int i = 0; i < ids.length(); i++) {
            if (!java.util.Arrays.asList("purpose", "date", "height", "tiers", "shape", "method").contains(ids.getString(i)))
                throw new java.io.IOException("unknown_evidence");
        }
        return result;
    }
}
