package io.gildongmu.voiceprobe;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** One explicit album-art request; never sends the conversation history. */
final class ArtClient {
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    void cancel() { cancelled = true; if (connection != null) connection.disconnect(); }

    JSONObject generate(String answerEndpoint, JSONObject row) throws Exception {
        if (cancelled || !answerEndpoint.endsWith("/answer")) throw new java.io.IOException("not_configured");
        HttpURLConnection c = (HttpURLConnection) new URL(answerEndpoint.substring(0, answerEndpoint.length() - 7) + "/art").openConnection();
        connection = c;
        try {
            if (cancelled) throw new java.io.IOException("cancelled");
            c.setConnectTimeout(5000); c.setReadTimeout(170000); c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
            byte[] body = new JSONObject().put("quote", row.getString("quote")).put("contentId", row.getString("contentId")).toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = c.getOutputStream()) { out.write(body); }
            if (c.getResponseCode() != 200) throw new java.io.IOException("art_unavailable");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (java.io.InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (cancelled || out.size() + count > 12_000_000) throw new java.io.IOException("invalid_image");
                    out.write(buffer, 0, count);
                }
            }
            JSONObject result = new JSONObject(out.toString("UTF-8"));
            if (!result.getString("source").equals("ai_imagination") || !result.getString("mimeType").equals("image/png")) throw new java.io.IOException("invalid_image");
            return result;
        } finally { c.disconnect(); }
    }
}
