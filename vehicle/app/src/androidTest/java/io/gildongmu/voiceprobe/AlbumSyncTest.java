package io.gildongmu.voiceprobe;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

public class AlbumSyncTest {
    private View findText(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = findText(group.getChildAt(i), label);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Test public void savedJourneyCanBeSentFromDetailToAccount() throws Exception {
        String key = InstrumentationRegistry.getArguments().getString("album_test_key");
        assumeNotNull(key);
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var preferences = context.getSharedPreferences("mile_trips", 0);
        String previous = preferences.getString("trips", "[]");
        try {
            new TripStore(context).save("album-device-qa", "별들이 쉬는 집 같아", false);
            try (var scenario = ActivityScenario.<MainActivity>launch(new Intent(context, MainActivity.class)
                    .putExtra("debug_skip_voice_initialization", true))) {
                scenario.onActivity(activity -> {
                    var root = activity.findViewById(android.R.id.content);
                    activity.findViewById(R.id.journey_records).performClick();
                    View card = findText(root, "별들이 쉬는 집 같아");
                    assertNotNull(card);
                    for (; !card.isClickable() && card.getParent() instanceof View; card = (View) card.getParent()) { }
                    assertTrue(card.performClick());
                    Button send = (Button) findText(root, "모바일 계정에 보내기");
                    assertNotNull(send);
                    send.performClick();
                    try {
                        var field = MainActivity.class.getDeclaredField("artConfirmation");
                        field.setAccessible(true);
                        var dialog = (android.app.AlertDialog) field.get(activity);
                        assertNotNull(dialog);
                        EditText input = findEdit(dialog.getWindow().getDecorView());
                        assertNotNull(input);
                        input.setText(key);
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                });
                for (int i = 0; i < 30; i++) {
                    java.util.concurrent.atomic.AtomicBoolean sent = new java.util.concurrent.atomic.AtomicBoolean();
                    scenario.onActivity(activity -> sent.set(findText(activity.findViewById(android.R.id.content),
                            "계정에 여정을 보냈어요.") != null));
                    if (sent.get()) return;
                    Thread.sleep(300);
                }
                fail("Vehicle did not report a successful upload");
            }
        } finally {
            assertTrue(preferences.edit().putString("trips", previous).commit());
        }
    }

    @Test public void detailExportsReadableJourneyWithArt() throws Exception {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        var context = instrumentation.getTargetContext();
        var preferences = context.getSharedPreferences("mile_trips", 0);
        String previous = preferences.getString("trips", "[]");
        var exports = new java.io.File(context.getCacheDir(), "trip-export");
        var before = java.util.Set.of(exports.list() == null ? new String[0] : exports.list());
        java.io.File art = null;
        try {
            String id = java.util.UUID.randomUUID().toString();
            String quote = "창밖의 별을 기억해";
            var bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888);
            var png = new java.io.ByteArrayOutputStream();
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png));
            bitmap.recycle();
            var store = new TripStore(context);
            store.save(id, quote, false);
            store.saveArt(id, quote, png.toByteArray(), "fixture");
            art = store.artFile(store.read().getJSONObject(0));

            try (var scenario = ActivityScenario.<MainActivity>launch(new Intent(context, MainActivity.class)
                    .putExtra("debug_skip_voice_initialization", true))) {
                scenario.onActivity(activity -> {
                    var root = activity.findViewById(android.R.id.content);
                    activity.findViewById(R.id.journey_records).performClick();
                    View card = findText(root, quote);
                    assertNotNull(card);
                    while (!card.isClickable() && card.getParent() instanceof View parent) card = parent;
                    assertTrue(card.performClick());
                    View button = findText(root, "모바일로 여정 옮기기");
                    assertNotNull(button);
                    assertTrue(button.performClick());
                });
                String[] created = exports.list((directory, name) -> !before.contains(name));
                assertNotNull(created);
                assertEquals(1, created.length);
                var uri = android.net.Uri.parse("content://" + context.getPackageName() + ".trip-export/" + created[0]);
                try (var input = context.getContentResolver().openInputStream(uri)) {
                    assertNotNull(input);
                    var payload = new org.json.JSONObject(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals(id, payload.getString("id"));
                    assertEquals(quote, payload.getString("quote"));
                    assertArrayEquals(png.toByteArray(), android.util.Base64.decode(payload.getString("art"), android.util.Base64.DEFAULT));
                    assertFalse(payload.has("history"));
                    if ("true".equals(InstrumentationRegistry.getArguments().getString("emit_test_export"))) {
                        var result = new android.os.Bundle();
                        result.putString("mile_fixture", android.util.Base64.encodeToString(
                                payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                android.util.Base64.NO_WRAP));
                        instrumentation.sendStatus(0, result);
                    }
                }
            }
        } finally {
            assertTrue(preferences.edit().putString("trips", previous).commit());
            if (art != null) art.delete();
            var created = exports.listFiles((directory, name) -> !before.contains(name));
            if (created != null) for (var file : created) file.delete();
        }
    }

    private EditText findEdit(View view) {
        if (view instanceof EditText input) return input;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText result = findEdit(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
}
