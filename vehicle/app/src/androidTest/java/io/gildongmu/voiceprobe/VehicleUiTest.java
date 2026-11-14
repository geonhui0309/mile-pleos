package io.gildongmu.voiceprobe;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class VehicleUiTest {
    private static Object field(MainActivity a, String name) {
        try { Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(a); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    @Test public void feedbackSurvivesRefreshUntilNavigation() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen = new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            String message = "정확한 위치 권한이 필요해요.";
            screen.feedback(message);
            screen.render("start", ConversationController.State.IDLE, true);
            java.util.ArrayList<View> results = new java.util.ArrayList<>();
            screen.findViewsWithText(results, message, View.FIND_VIEWS_WITH_TEXT);
            assertEquals(1, results.size());
            assertEquals(View.VISIBLE, results.get(0).getVisibility());
            screen.render("searching", ConversationController.State.IDLE, true);
            assertEquals(View.GONE, results.get(0).getVisibility());
        });
    }

    @Test public void vehicleRestrictionClosesAlbumAndDisablesParkedControls() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(viewOnly())) {
            scenario.onActivity(activity -> {
                MileScreen screen = (MileScreen) field(activity, "screen");
                screen.recordsButton.performClick();
                assertEquals("records", field(activity, "journeyPhase"));
                try {
                    java.lang.reflect.Method confirm = MainActivity.class.getDeclaredMethod("confirmArt", org.json.JSONObject.class);
                    confirm.setAccessible(true);
                    confirm.invoke(activity, new org.json.JSONObject(java.util.Map.of("id", "test", "quote", "별들의 집")));
                    android.app.AlertDialog dialog = (android.app.AlertDialog) field(activity, "artConfirmation");
                    assertTrue(dialog.isShowing());
                    Class<?> type = android.car.drivingstate.CarUxRestrictions.class;
                    java.lang.reflect.Method callback = MainActivity.class.getDeclaredMethod("applyVehicleRestrictions", type);
                    callback.setAccessible(true);
                    callback.invoke(activity, new android.car.drivingstate.CarUxRestrictions.Builder(true, 0x50, 1).build());
                    assertFalse(dialog.isShowing());
                    assertNull(field(activity, "artConfirmation"));
                    assertEquals("start", field(activity, "journeyPhase"));
                    assertEquals(View.GONE, screen.recordsButton.getVisibility());
                    assertFalse(screen.findViewById(R.id.screen_settings).isEnabled());
                    callback.invoke(activity, new android.car.drivingstate.CarUxRestrictions.Builder(false, 0, 2).build());
                    assertEquals(View.VISIBLE, screen.recordsButton.getVisibility());
                    assertTrue(screen.findViewById(R.id.screen_settings).isEnabled());
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        }
    }

    private Intent viewOnly() {
        return new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class)
                .putExtra("debug_skip_voice_initialization", true);
    }
    private boolean visible(View v, View root) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View)p.getParent() : null) {
            if (p.getVisibility() != View.VISIBLE) return false;
            if (p == root) return true;
        }
        return false;
    }
    private int inspect(View v, MileScreen root) {
        if (!visible(v, root)) return 0;
        int count = 0;
        if (v instanceof Button && "primary".equals(v.getTag())) {
            count++;
            assertTrue("Control height", v.getHeight() >= root.dp(80));
            int x = 0, y = 0;
            for (View p = v; p != root; p = (View)p.getParent()) { x += p.getLeft(); y += p.getTop(); }
            assertTrue("CTA horizontal bounds", x >= 0 && x + v.getWidth() <= root.getWidth());
            assertTrue("CTA vertical bounds", y >= 0 && y + v.getHeight() <= root.getHeight());
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) count += inspect(((ViewGroup)v).getChildAt(i), root);
        return count;
    }
    private void layout(MileScreen screen, int width) {
        int w = screen.dp(width), h = screen.dp(1184);
        for (int i = 0; i < 1; i++) {
            screen.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
            screen.layout(0, 0, w, h);
        }
    }
    @Test public void narrowAndWideStatesKeepOneReachablePrimaryAction() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen = new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            for (int width : new int[]{700, 840, 1100, 1700}) {
                for (String phase : new String[]{"start", "searching", "found", "conversation", "review"}) {
                    if (phase.equals("review")) screen.review(java.util.Arrays.asList("별이 사는 집", "별들이 쉬는 집 같아"), words -> {});
                    for (ConversationController.State state : ConversationController.State.values()) {
                        if (state == ConversationController.State.CLOSED) continue;
                        screen.render(phase, state, true); layout(screen, width);
                        assertEquals(phase + " / " + state + " / " + width, 1, inspect(screen, screen));
                    }
                }
            }
        });
    }
    @Test public void wideStoryUsesLeftEdgeAndReferencePhotoButNarrowStoryKeepsVoiceFirst() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen = new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            screen.setNarration(true);
            screen.render("conversation", ConversationController.State.SPEAKING, true);
            layout(screen, 1700);
            View photo = findReferencePhoto(screen);
            assertNotNull(photo);
            assertTrue(visible(photo, screen));
            int x = 0;
            for (View p = screen.journeyStage; p != screen; p = (View)p.getParent()) x += p.getLeft();
            assertEquals(screen.dp(128), x);
            layout(screen, 840);
            assertFalse(visible(photo, screen));
            screen.render("start", ConversationController.State.IDLE, true); layout(screen, 1700);
            assertFalse(visible(photo, screen));
        });
    }
    @Test public void galleryFollowsChosenContentAndLeavesQuestionAndSettingsReadable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            screen.render("found",ConversationController.State.IDLE,true);layout(screen,1700);
            View photo=findReferencePhoto(screen);assertTrue(visible(photo,screen));
            screen.findViewById(R.id.screen_settings).performClick();layout(screen,1700);
            assertFalse(visible(photo,screen));assertFalse(screen.bargeInMode.isChecked());
            screen.dismissSettings();screen.setNarration(false);
            screen.render("conversation",ConversationController.State.LISTENING,true);layout(screen,1700);
            assertFalse(visible(photo,screen));assertFalse(screen.bargeInMode.isChecked());
            screen.setNarration(true);screen.render("conversation",ConversationController.State.SPEAKING,true);layout(screen,1700);
            assertTrue(visible(photo,screen));
            layout(screen,840);assertFalse(visible(photo,screen));
            layout(screen,1700);assertTrue(visible(photo,screen));
        });
    }
    @Test public void companionAppearsOnStartAndSearchAndKeepsSquareSlot() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            SearchingCompanion character=screen.findViewById(R.id.search_companion);
            for(int width:new int[]{700,840,1100,1700}) {
                for(String phase:new String[]{"start","searching","found","conversation","review","records"}) {
                    screen.render(phase,ConversationController.State.IDLE,true);layout(screen,width);
                    assertEquals(phase,phase.equals("start") || phase.equals("searching"),visible(character,screen));
                    if(phase.equals("searching"))assertEquals(character.getWidth(),character.getHeight());
                    assertFalse("Detached preview must not animate",character.isMotionRunning());
                }
            }
        });
    }
    @Test public void searchMotionStopsOnCancelSettingsAndCompletionWithoutOpeningMic() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(viewOnly())) {
            s.onActivity(a->{
                SearchingCompanion character=a.findViewById(R.id.search_companion);
                assertTrue(character.isShown());assertFalse(character.isMotionRunning());assertEquals(0,character.currentFrameIndex());
                a.findViewById(R.id.discover_story).performClick();
            });Thread.sleep(300);
            s.onActivity(a->{
                SearchingCompanion character=a.findViewById(R.id.search_companion);
                assertTrue(character.isShown());
                if(android.animation.ValueAnimator.areAnimatorsEnabled())assertTrue(character.isMotionRunning());
                assertFalse(((android.widget.Switch)a.findViewById(R.id.barge_in_mode)).isChecked());
                a.findViewById(R.id.screen_settings).performClick();
            });Thread.sleep(100);
            s.onActivity(a->assertFalse(((SearchingCompanion)a.findViewById(R.id.search_companion)).isMotionRunning()));
            s.onActivity(a->{a.findViewById(R.id.screen_settings).performClick();a.findViewById(R.id.discover_story).performClick();});Thread.sleep(100);
            s.onActivity(a->{SearchingCompanion character=a.findViewById(R.id.search_companion);
                assertFalse(character.isMotionRunning());assertTrue(character.isShown());
                assertEquals(0f,character.getRotation(),0f);a.findViewById(R.id.discover_story).performClick();});
            Thread.sleep(2500);
            s.onActivity(a->{SearchingCompanion character=a.findViewById(R.id.search_companion);
                assertFalse(character.isShown());assertFalse(character.isMotionRunning());
                assertTrue(a.findViewById(R.id.story_play).isShown());});
        }
    }
    @Test public void wideSettingsColumnHasBalancedSideMarginsAndNarrowSettingsFit() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            screen.findViewById(R.id.screen_settings).performClick();
            for(int width:new int[]{700,840,1100,1700}) {
                layout(screen,width);
                View column=(View)screen.childMode.getParent();
                View scroll=(View)column.getParent();
                View container=(View)scroll.getParent();
                assertTrue(scroll.getWidth()<=container.getWidth());
                assertEquals(container.getWidth()-scroll.getRight(),scroll.getLeft(),screen.dp(1));
            }
        });
    }
    @Test public void wideContentSharesLogoAndHeaderRightControlGuidesAcrossStates() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            screen.setNarration(true);
            for(int width:new int[]{1100,1700}) for(String phase:new String[]{"start","searching","found","conversation","review","records"}) {
                screen.render(phase,ConversationController.State.SPEAKING,true);layout(screen,width);
                java.util.ArrayList<View> logos=new java.util.ArrayList<>();
                screen.findViewsWithText(logos,"MILE",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                View page = screen.findViewById(R.id.page_title);
                if (page.getVisibility() != View.VISIBLE) assertEquals(1,logos.size());
                View heading = page.getVisibility() == View.VISIBLE ? page : logos.get(0);
                if (!phase.equals("records")) assertEquals(phase,rootX(heading,screen),rootX(screen.journeyStage,screen));
                else assertEquals("저장한 여정", ((android.widget.TextView)page).getText().toString());
                int right=-1;
                for(View button:new View[]{screen.discoveryButton,screen.storyButton,screen.ask,screen.pause})
                    if(visible(button,screen))right=Math.max(right,rootX(button,screen)+button.getWidth());
                if(right>=0) {
                    View settings=screen.findViewById(R.id.screen_settings);
                    if (phase.equals("start") || phase.equals("searching") || phase.equals("found")) {
                        View heroAction = phase.equals("found") ? screen.storyButton : screen.discoveryButton;
                        assertEquals(rootX(screen.journeyStage,screen),rootX(heroAction,screen));
                    } else assertEquals(phase,rootX(settings,screen)+settings.getWidth(),right);
                }
            }
        });
    }
    private int rootX(View v,View root) {
        int x=0;for(View p=v;p!=root;p=(View)p.getParent())x+=p.getLeft();return x;
    }
    @Test public void galleryEdgesBlendIntoCanvasInBothThemes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for(boolean dark:new boolean[]{false,true}) {
                int color=android.graphics.Color.parseColor(dark?"#1D1E21":"#EDEEF2");
                GalleryPhoto photo=new GalleryPhoto(InstrumentationRegistry.getInstrumentation().getTargetContext());
                photo.theme(color,dark);photo.layout(0,0,800,600);
                android.graphics.Bitmap pixels=android.graphics.Bitmap.createBitmap(800,600,android.graphics.Bitmap.Config.ARGB_8888);
                photo.draw(new android.graphics.Canvas(pixels));
                for(int y:new int[]{60,180,300,480,590})for(int x:new int[]{0,799}) {
                    int edge=pixels.getPixel(x,y);
                    assertTrue(Math.abs(android.graphics.Color.red(edge)-android.graphics.Color.red(color))<=2);
                    assertTrue(Math.abs(android.graphics.Color.green(edge)-android.graphics.Color.green(color))<=2);
                    assertTrue(Math.abs(android.graphics.Color.blue(edge)-android.graphics.Color.blue(color))<=2);
                }
                pixels.recycle();
            }
        });
    }
    @Test public void searchChangesPoseFramesAndResetsAfterCancellation() throws Exception {
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(viewOnly())) {
            s.onActivity(a->a.findViewById(R.id.discover_story).performClick());Thread.sleep(500);
            s.onActivity(a->{SearchingCompanion character=a.findViewById(R.id.search_companion);
                assertTrue(character.isShown());
                if(android.animation.ValueAnimator.areAnimatorsEnabled())assertTrue(character.currentFrameIndex()>0);
                assertEquals(0f,character.getRotation(),0f);
                a.findViewById(R.id.discover_story).performClick();});Thread.sleep(100);
            s.onActivity(a->{SearchingCompanion character=a.findViewById(R.id.search_companion);
                assertFalse(character.isMotionRunning());assertEquals(0,character.currentFrameIndex());});
        }
    }
    @Test public void applicationUsesMileAdaptiveIconInsteadOfAndroidDefault() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(R.mipmap.ic_mile,context.getApplicationInfo().icon);
        android.content.pm.ActivityInfo activity=context.getPackageManager().getActivityInfo(new android.content.ComponentName(context,MainActivity.class),0);
        assertEquals(R.mipmap.ic_mile,activity.icon);
        android.graphics.drawable.Drawable icon=context.getPackageManager().getApplicationIcon(context.getPackageName());
        assertTrue(icon instanceof android.graphics.drawable.AdaptiveIconDrawable);
        assertNotNull(((android.graphics.drawable.AdaptiveIconDrawable)icon).getForeground());
    }
    private View findReferencePhoto(View v) {
        if (v.getContentDescription() != null && v.getContentDescription().toString().startsWith("첨성대 장소 참고 사진")) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) {
            View found = findReferencePhoto(((ViewGroup)v).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    @Test public void themeChangesKeepConversationAndViewIdentity() {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences prefs = c.getSharedPreferences("mile_ui", Context.MODE_PRIVATE);
        int original = prefs.getInt("theme", 0);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(viewOnly())) {
            scenario.onActivity(a -> {
                MileScreen screen = (MileScreen)field(a, "screen");
                ConversationController controller = (ConversationController)field(a, "conversation");
                int generation = controller.beginNarration();
                Button sameAsk = a.findViewById(R.id.ask_voice);
                screen.render("conversation", controller.state(), true);
                a.findViewById(R.id.screen_settings).performClick();
                a.findViewById(R.id.theme_dark).performClick();
                assertEquals(android.graphics.Color.parseColor("#1D1E21"), screen.color("surface.canvas"));
                a.findViewById(R.id.theme_light).performClick();
                assertEquals(android.graphics.Color.parseColor("#F7F8FA"), screen.color("surface.canvas"));
                assertSame(sameAsk, a.findViewById(R.id.ask_voice));
                assertEquals(ConversationController.State.SPEAKING, controller.state());
                assertTrue(controller.isCurrent(generation));
                assertNotNull(screen.journeyStage.getTypeface());
            });
        } finally { prefs.edit().putInt("theme", original).commit(); }
    }
    @Test public void localRecordListAndDetailHaveNoFabricatedMedia() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                MileScreen screen = new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
                org.json.JSONArray rows = new org.json.JSONArray().put(new org.json.JSONObject()
                        .put("place", "경주 첨성대").put("quote", "별들이 쉬는 집 같아").put("createdAt", 1));
                screen.showRecords(rows); screen.render("records", ConversationController.State.IDLE, true); layout(screen, 840);
                assertEquals("저장한 여정", screen.journeyStage.getText().toString());
                assertEquals(0, inspect(screen, screen));
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    @Test public void microphoneLabelFollowsSdkEventsRatherThanWaitingState() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen = new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            TextView label = screen.findViewById(R.id.microphone_state);
            screen.render("conversation", ConversationController.State.LISTENING, true);
            screen.voiceEvent("STT_REQUESTED");
            assertEquals("마이크 준비 중", label.getText().toString());
            screen.voiceEvent("STT_LISTENING");
            assertTrue(label.getText().toString().startsWith("마이크 켜짐"));
            screen.voiceEvent("STT_FINISHING");
            assertTrue(label.getText().toString().startsWith("마이크 꺼짐"));
            screen.render("conversation", ConversationController.State.IDLE, true);
            assertEquals("마이크 꺼짐", label.getText().toString());
        });
    }
    @Test public void narrationKeepsTheScreenQuietWithoutDeletingConversationText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen = new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            screen.answer.setText("긴 해설은 음성으로 전달해요.");
            screen.setNarration(true);
            screen.render("conversation", ConversationController.State.SPEAKING, true);
            assertEquals(View.GONE, screen.answer.getVisibility());
            assertEquals("긴 해설은 음성으로 전달해요.", screen.answer.getText().toString());
            screen.setNarration(false);
            screen.render("conversation", ConversationController.State.SPEAKING, true);
            assertEquals(View.VISIBLE, screen.answer.getVisibility());
        });
    }
}
