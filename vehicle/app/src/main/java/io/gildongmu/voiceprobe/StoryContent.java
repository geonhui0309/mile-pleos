package io.gildongmu.voiceprobe;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

final class StoryContent {
    private final JSONObject content;
    boolean childMode;

    StoryContent(Context context) throws Exception {
        try (java.io.InputStream input = context.getAssets().open("content/cheomseongdae.json")) {
            content = new JSONObject(new java.util.Scanner(input, "UTF-8").useDelimiter("\\A").next());
        }
    }

    String story() throws Exception {
        return content.getJSONObject("stories").getString(childMode ? "child" : "adult");
    }

    org.json.JSONArray storyEvidence() throws Exception {
        return content.getJSONObject("storyFactIds").getJSONArray(childMode ? "child" : "adult");
    }

    String sources() throws Exception {
        StringBuilder result = new StringBuilder("사실 확인 2026-10-28 · 창작 대본 · 사진·원음 미사용\n");
        JSONArray sources = content.getJSONArray("sources");
        for (int i = 0; i < sources.length(); i++) {
            JSONObject row = sources.getJSONObject(i);
            result.append(row.getString("title")).append('\n').append(row.getString("url")).append('\n');
        }
        return result.toString();
    }
}
