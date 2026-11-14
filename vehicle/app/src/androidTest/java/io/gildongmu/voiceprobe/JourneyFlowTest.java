package io.gildongmu.voiceprobe;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class JourneyFlowTest {
    private android.content.Intent viewOnly() {
        return new android.content.Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class)
                .putExtra("debug_skip_voice_initialization", true);
    }
    @Test public void nearbyDoesNotInterruptAndOnlyOffersOncePerSession() {
        NearbyStory nearby = new NearbyStory();
        assertFalse(nearby.offer(Double.NaN, true, false));
        assertFalse(nearby.offer(-1, true, false));
        assertFalse(nearby.offer(900, true, false));
        assertFalse(nearby.offer(250, false, false));
        assertFalse(nearby.offer(250, true, true));
        assertTrue(nearby.offer(250, true, false));
        assertFalse(nearby.offer(200, true, false));
    }

    @Test public void storageSurvivesReopenDeduplicatesAndDoesNotOverwriteCorruption() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences p = c.getSharedPreferences("mile_trips", Context.MODE_PRIVATE);
        String backup = p.getString("trips", "[]");
        try {
            assertTrue(p.edit().putString("trips", "[]").commit());
            new TripStore(c).save("test-trip", "별들이 쉬는 집 같아", true);
            new TripStore(c).save("test-trip", "같이 별을 보고 싶어", true);
            org.json.JSONArray saved = new TripStore(c).read();
            assertEquals(1, saved.length());
            assertEquals("같이 별을 보고 싶어", saved.getJSONObject(0).getString("quote"));
            try { new TripStore(c).save("bad", "", false); fail(); }
            catch (IllegalArgumentException expected) { }
            assertTrue(p.edit().putString("trips", "broken").commit());
            try { new TripStore(c).save("new", "남길 말", false); fail(); }
            catch (org.json.JSONException expected) { }
            assertEquals("broken", p.getString("trips", ""));
        } finally { assertTrue(p.edit().putString("trips", backup).commit()); }
    }

    @Test public void replayCanStopWithoutOpeningVoice() throws Exception {
        try (ActivityScenario<MainActivity> s = ActivityScenario.launch(viewOnly())) {
            s.onActivity(a -> {
                a.findViewById(R.id.discover_story).performClick();
                a.findViewById(R.id.discover_story).performClick();
            });
            Thread.sleep(2500);
            s.onActivity(a -> {
                assertEquals(View.VISIBLE, a.findViewById(R.id.discover_story).getVisibility());
                assertFalse(a.findViewById(R.id.story_play).isShown());
            });
        }
    }

    @Test public void discoveryQuestionEndAndExplicitSelectionSaveOnlyChosenWords() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences p = c.getSharedPreferences("mile_trips", Context.MODE_PRIVATE);
        String backup = p.getString("trips", "[]");
        boolean liveArt = "true".equals(InstrumentationRegistry.getArguments().getString("live_art"));
        java.io.File generated = null;
        try {
            assertTrue(p.edit().putString("trips", "[]").commit());
            try (ActivityScenario<MainActivity> s = ActivityScenario.launch(viewOnly())) {
                s.onActivity(a -> {
                    if (!liveArt) a.artEndpoint = "http://127.0.0.1:1/answer";
                    a.findViewById(R.id.discover_story).performClick();
                });
                Thread.sleep(2600);
                s.onActivity(a -> {
                    assertTrue(a.findViewById(R.id.story_play).isShown());
                    a.findViewById(R.id.story_play).performClick();
                    assertFalse(a.findViewById(R.id.story_play).isShown());
                    // Synthetic text exercises the same accepted-question path; not a real microphone trial.
                    ((EditText)a.findViewById(R.id.question_input)).setText("별들이 쉬는 집 같아");
                    a.findViewById(R.id.question_send).performClick();
                    a.findViewById(R.id.stop_audio).performClick();
                    assertEquals("이번 여정에 남길 한마디", ((TextView)a.findViewById(R.id.journey_stage)).getText().toString());
                    Button save = a.findViewById(R.id.save_quote);
                    assertNotNull(save);
                    assertFalse(save.isEnabled());
                    try { assertEquals(0, new TripStore(a).read().length()); }
                    catch (Exception e) { throw new AssertionError(e); }
                    LinearLayout review = a.findViewById(R.id.quote_choices);
                    RadioGroup choices = (RadioGroup)review.getChildAt(1);
                    ((RadioButton)choices.getChildAt(0)).setChecked(true);
                    assertTrue(save.isEnabled());
                    save.performClick();
                    assertFalse(save.isEnabled());
                });
                s.onActivity(a -> {
                    try {
                        org.json.JSONArray rows = new TripStore(a).read();
                        assertEquals(1, rows.length());
                        assertEquals("별들이 쉬는 집 같아", rows.getJSONObject(0).getString("quote"));
                        assertFalse(rows.getJSONObject(0).has("history"));
                    } catch (Exception e) { throw new AssertionError(e); }
                });
                if (!liveArt) {
                    var failed = new java.util.concurrent.atomic.AtomicBoolean();
                    for (int attempt = 0; attempt < 30 && !failed.get(); attempt++) {
                        s.onActivity(a -> {
                            View root = a.findViewById(android.R.id.content);
                            var matches = new java.util.ArrayList<View>();
                            root.findViewsWithText(matches, "그림을 만들지 못했어요", View.FIND_VIEWS_WITH_TEXT);
                            failed.set(matches.stream().anyMatch(View::isShown));
                        });
                        if (!failed.get()) Thread.sleep(100);
                    }
                    assertTrue("Image failure must appear after saving", failed.get());
                    var store = new TripStore(c);
                    assertNull(store.artFile(store.read().getJSONObject(0)));
                    s.onActivity(a -> {
                        a.findViewById(R.id.journey_records).performClick();
                        View root = a.findViewById(android.R.id.content);
                        assertTrue(findDescription(root, "저장한 한마디 열기: 별들이 쉬는 집 같아").performClick());
                        assertTrue(findDescription(root, "남겨둔 한마디").isShown());
                    });
                }
                if (liveArt) {
                    String quote = "별들이 쉬는 집 같아";
                    long deadline = android.os.SystemClock.elapsedRealtime() + 165_000;
                    TripStore store = new TripStore(c);
                    while (generated == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                        generated = store.artFile(store.read().getJSONObject(0));
                        if (generated == null) Thread.sleep(250);
                    }
                    assertNotNull("Live image was not saved", generated);
                    s.onActivity(a -> {
                        a.findViewById(R.id.journey_records).performClick();
                        View root = a.findViewById(android.R.id.content);
                        findDescription(root, "저장한 한마디 열기: " + quote).performClick();
                        View picture = findDescription(root, "AI 상상 그림: " + quote);
                        assertTrue(picture.isShown());
                    });
                }
            }
        } finally {
            assertTrue(p.edit().putString("trips", backup).commit());
            if (generated != null) generated.delete();
        }
    }

    @Test public void adjustedNarrationPlaysThroughLocalServer() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(
                InstrumentationRegistry.getArguments().getString("local_voice")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("mile_ui", Context.MODE_PRIVATE);
        java.util.Map<String, ?> original = prefs.getAll();
        try {
            assertTrue(prefs.edit().putInt("voice_rate", 109).putInt("voice_pitch", 95).commit());
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(a -> a.findViewById(R.id.discover_story).performClick());
                Thread.sleep(2700);
                scenario.onActivity(a -> {
                    View story = a.findViewById(R.id.story_play);
                    assertTrue(story.isShown());
                    assertTrue(story.performClick());
                });
                long deadline = android.os.SystemClock.elapsedRealtime() + 50_000;
                boolean expectFallback = "true".equals(
                        InstrumentationRegistry.getArguments().getString("expect_voice_fallback"));
                String started = expectFallback ? "ADJUSTED_VOICE_FALLBACK" : "ADJUSTED_VOICE_STARTED rate=1.09 pitch=0.95";
                int pid = android.os.Process.myPid();
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    String command = "logcat -d --pid=" + pid + " -s GildongmuProbe:I '*:S'";
                    try (java.io.InputStream input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
                        String output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        if (output.contains(started) && output.indexOf("TTS_DONE", output.indexOf(started)) >= 0) return;
                        assertFalse("Adjusted narration request failed", output.contains("TTS_REQUEST_FAILED"));
                    }
                    Thread.sleep(500);
                }
                fail("Adjusted narration did not finish");
            }
        } finally {
            android.content.SharedPreferences.Editor restore = prefs.edit();
            for (String key : new String[]{"voice_rate", "voice_pitch"}) {
                if (original.containsKey(key)) restore.putInt(key, (Integer) original.get(key));
                else restore.remove(key);
            }
            assertTrue(restore.commit());
        }
    }

    private View findDescription(View root, String description) {
        java.util.ArrayList<View> views = new java.util.ArrayList<>();
        root.findViewsWithText(views, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        for (View view : views) {
            if (description.contentEquals(view.getContentDescription())) return view;
        }
        throw new AssertionError("Missing view: " + description);
    }
}
