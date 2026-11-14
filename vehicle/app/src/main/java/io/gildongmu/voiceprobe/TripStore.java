package io.gildongmu.voiceprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Only explicitly selected words are persisted; unselected conversation stays in memory. */
final class TripStore {
    private final SharedPreferences preferences;
    private final java.io.File artDirectory;
    TripStore(Context context) { preferences = context.getSharedPreferences("mile_trips", Context.MODE_PRIVATE); artDirectory = new java.io.File(context.getFilesDir(), "trip-art"); }

    JSONArray read() throws Exception {
        JSONArray rows = new JSONArray(preferences.getString("trips", "[]"));
        if (rows.length() > 50) throw new IllegalStateException("Invalid trip collection");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            row.getString("id"); row.getString("quote"); row.getString("place"); row.getLong("createdAt");
        }
        return rows;
    }

    synchronized void save(String id, String quote, boolean childMode) throws Exception {
        if (id == null || id.isEmpty() || quote == null || quote.trim().isEmpty() || quote.length() > 1000)
            throw new IllegalArgumentException("Invalid selected words");
        JSONArray old = read(), next = new JSONArray();
        JSONObject saved = new JSONObject().put("id", id).put("quote", quote)
                .put("place", "경주 첨성대").put("contentId", "gyeongju_cheomseongdae_v1")
                .put("createdAt", System.currentTimeMillis()).put("childMode", childMode)
                .put("source", "user_selected_words");
        boolean existing = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject row = old.getJSONObject(i);
            if (id.equals(row.getString("id"))) {
                existing = true;
                saved.put("createdAt", row.getLong("createdAt"));
                if (quote.equals(row.getString("quote")) && row.has("artFile")) {
                    saved.put("artFile", row.getString("artFile"));
                    saved.put("artModel", row.optString("artModel"));
                }
            }
        }
        if (!existing && old.length() == 50) throw new java.io.IOException("Trip collection full");
        next.put(saved);
        for (int i = 0; i < old.length() && next.length() < 50; i++)
            if (!id.equals(old.getJSONObject(i).getString("id"))) next.put(old.getJSONObject(i));
        if (!preferences.edit().putString("trips", next.toString()).commit())
            throw new java.io.IOException("Trip save failed");
    }
    java.io.File artFile(JSONObject row) {
        String name = row.optString("artFile");
        if (!name.matches("[a-f0-9]{64}\\.png")) return null;
        java.io.File file = new java.io.File(artDirectory, name);
        return file.isFile() ? file : null;
    }

    synchronized void saveArt(String id, String quote, byte[] png, String model) throws Exception {
        JSONArray rows = read();
        JSONObject match = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            if (id.equals(row.getString("id")) && quote.equals(row.getString("quote"))) match = row;
        }
        if (match == null) throw new IllegalStateException("Saved words changed");
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length, bounds);
        if (png.length > 8_000_000 || bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 2048 || bounds.outHeight > 2048)
            throw new java.io.IOException("Invalid art");
        StringBuilder name = new StringBuilder();
        for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(png)) name.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        name.append(".png");
        if (!artDirectory.isDirectory() && !artDirectory.mkdirs()) throw new java.io.IOException("Art directory unavailable");
        java.io.File file = new java.io.File(artDirectory, name.toString());
        android.util.AtomicFile atomic = new android.util.AtomicFile(file);
        java.io.FileOutputStream stream = null;
        try { stream = atomic.startWrite(); stream.write(png); atomic.finishWrite(stream); }
        catch (Exception error) { if (stream != null) atomic.failWrite(stream); throw error; }
        match.put("artFile", name.toString()).put("artModel", model);
        if (!preferences.edit().putString("trips", rows.toString()).commit()) throw new java.io.IOException("Art link save failed");
    }

}
