package io.gildongmu.voiceprobe;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.view.ViewGroup;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AlbumArtTest {
    private byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(android.graphics.Color.BLUE);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); return out.toByteArray();
    }
    @Test public void artSurvivesReopenAndCannotAttachToChangedWords() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences p = c.getSharedPreferences("mile_trips", 0);
        String backup = p.getString("trips", "[]"); java.io.File fixture = null;
        try {
            p.edit().putString("trips", "[]").commit();
            TripStore store = new TripStore(c); store.save("art-test", "별들이 쉬는 집 같아", true);
            store.saveArt("art-test", "별들이 쉬는 집 같아", png(), "fixture");
            JSONObject row = new TripStore(c).read().getJSONObject(0); fixture = store.artFile(row);
            assertNotNull(fixture); assertTrue(fixture.isFile());
            store.save("art-test", "별들이 쉬는 집 같아", true);
            assertNotNull(store.artFile(store.read().getJSONObject(0)));
            store.save("art-test", "다른 말", true);
            assertNull(store.artFile(store.read().getJSONObject(0)));
            try { store.saveArt("art-test", "별들이 쉬는 집 같아", png(), "fixture"); fail(); }
            catch (IllegalStateException expected) { }
            try { store.saveArt("art-test", "다른 말", new byte[]{1,2}, "fixture"); fail(); }
            catch (java.io.IOException expected) { }
            assertEquals("다른 말", store.read().getJSONObject(0).getString("quote"));
        } finally { p.edit().putString("trips", backup).commit(); if (fixture != null) fixture.delete(); }
    }
    private Button button(View view) {
        if (view instanceof Button && ((Button)view).getText().toString().contains("그림")) return (Button)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            Button found=button(((ViewGroup)view).getChildAt(i)); if(found!=null)return found;
        }
        return null;
    }
    @Test public void detailsRequireExplicitGenerationAndBlockDuplicateClicks() throws Exception {
        JSONObject row = new JSONObject().put("id","fixture").put("quote","별의 집").put("place","경주 첨성대").put("createdAt",0);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
                final int[] calls={0}; screen.setArtRequest(value->calls[0]++);
                JSONArray rows=new JSONArray().put(row);screen.showRecords(rows);
                screen.render("records",ConversationController.State.IDLE,true);
                // The record tile opens details; opening the collection never starts generation.
                assertEquals(0,calls[0]);
                java.lang.reflect.Method detail=MileScreen.class.getDeclaredMethod("showRecord",JSONObject.class);
                detail.setAccessible(true);detail.invoke(screen,row);
                Button generate=button(screen);assertNotNull(generate);assertTrue(generate.isEnabled());
                generate.performClick();assertEquals(1,calls[0]);
                screen.artState("fixture",true,rows);assertFalse(button(screen).isEnabled());
                screen.artState("fixture",false,rows);assertTrue(button(screen).isEnabled());
            } catch(Exception e) {throw new AssertionError(e);}
        });
    }
    @Test public void compactCharacterSitsBetweenTextAndBottomButton() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
            for(int width:new int[]{700,840}) {
                screen.render("start",ConversationController.State.IDLE,true);
                int w=(int)(width*screen.getResources().getDisplayMetrics().density),h=(int)(1100*screen.getResources().getDisplayMetrics().density);
                screen.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));screen.layout(0,0,w,h);
                View character=screen.findViewById(R.id.search_companion);
                assertTrue(character.getHeight()>0);
                assertTrue(character.getTop()>screen.journeyStage.getBottom());
                assertTrue(character.getBottom()<=screen.discoveryButton.getTop()+((View)screen.discoveryButton.getParent()).getTop()+((View)screen.discoveryButton.getParent().getParent()).getTop());
            }
        });
    }
    @Test public void savingSelectedWordsAutomaticallyGeneratesArtThroughHttp() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.SharedPreferences preferences=context.getSharedPreferences("mile_trips",0);
        String backup=preferences.getString("trips","[]"); java.io.File fixture=null;
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try (java.net.ServerSocket server=new java.net.ServerSocket(0)) {
            preferences.edit().putString("trips","[]").commit();
            java.util.concurrent.Future<JSONObject> received=worker.submit(()->{
                try(java.net.Socket socket=server.accept()) {
                    socket.setSoTimeout(5000);
                    java.io.InputStream input=socket.getInputStream();
                    java.io.ByteArrayOutputStream header=new java.io.ByteArrayOutputStream();
                    int b;while((b=input.read())!=-1){header.write(b);if(header.toString("UTF-8").endsWith("\r\n\r\n"))break;}
                    String headers=header.toString("UTF-8");
                    java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)Content-Length: (\\d+)").matcher(headers);
                    if(!match.find())throw new AssertionError("No body length");
                    byte[] body=new byte[Integer.parseInt(match.group(1))];new java.io.DataInputStream(input).readFully(body);
                    JSONObject request=new JSONObject(new String(body,java.nio.charset.StandardCharsets.UTF_8));
                    byte[] response=new JSONObject().put("imageBase64",android.util.Base64.encodeToString(png(),android.util.Base64.NO_WRAP))
                        .put("mimeType","image/png").put("model","fixture").put("source","ai_imagination").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    java.io.OutputStream output=socket.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+response.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));output.write(response);output.flush();
                    assertTrue(headers.startsWith("POST /art "));return request;
                }
            });
            android.content.Intent intent=new android.content.Intent(context,MainActivity.class).putExtra("debug_skip_voice_initialization",true);
            try(androidx.test.core.app.ActivityScenario<MainActivity> scenario=androidx.test.core.app.ActivityScenario.launch(intent)) {
                scenario.onActivity(activity->{
                    activity.artEndpoint="http://127.0.0.1:"+server.getLocalPort()+"/answer";
                    try {
                        java.lang.reflect.Field candidates=MainActivity.class.getDeclaredField("selectedCandidates");candidates.setAccessible(true);
                        ((java.util.List<String>)candidates.get(activity)).add("星の家 · 별들의 집");
                        java.lang.reflect.Method review=MainActivity.class.getDeclaredMethod("showReview");review.setAccessible(true);review.invoke(activity);
                        android.widget.LinearLayout choices=activity.findViewById(R.id.quote_choices);
                        ((android.widget.RadioButton)((android.widget.RadioGroup)choices.getChildAt(1)).getChildAt(0)).setChecked(true);
                        activity.findViewById(R.id.save_quote).performClick();
                    } catch(Exception error){throw new AssertionError(error);}
                });
                JSONObject request=received.get(5,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals("星の家 · 별들의 집",request.getString("quote"));assertEquals(2,request.length());
                for(int i=0;i<50;i++) {
                    JSONArray rows=new TripStore(context).read();
                    if(rows.length()>0 && new TripStore(context).artFile(rows.getJSONObject(0))!=null) {fixture=new TripStore(context).artFile(rows.getJSONObject(0));break;}
                    Thread.sleep(50);
                }
                assertNotNull("Save must automatically attach generated art",fixture);
            }
        } finally { worker.shutdownNow();preferences.edit().putString("trips",backup).commit();if(fixture!=null)fixture.delete(); }
    }

    @Test public void detailShowsLargeLandscapeArtWithoutReferencePhoto() throws Exception {
        JSONObject row = new JSONObject().put("id","height-fixture").put("quote","별들의 집").put("place","경주 첨성대").put("createdAt",0);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try {
                MileScreen screen=new MileScreen(InstrumentationRegistry.getInstrumentation().getTargetContext());
                screen.render("records",ConversationController.State.IDLE,true);
                java.lang.reflect.Method detail=MileScreen.class.getDeclaredMethod("showRecord",JSONObject.class);detail.setAccessible(true);detail.invoke(screen,row);
                int width=screen.dp(1700),height=screen.dp(1100);
                screen.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));screen.layout(0,0,width,height);
                assertEquals("경주 나들이",((android.widget.TextView)screen.findViewById(R.id.page_title)).getText().toString());
                java.util.ArrayList<View> panels=new java.util.ArrayList<>();screen.findViewsWithText(panels,"남겨둔 한마디",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                assertEquals(1,panels.size());
                java.util.ArrayList<View> photos=new java.util.ArrayList<>();screen.findViewsWithText(photos,"첨성대 장소 참고 사진 · Dudva / CC0",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                assertEquals("Reference photo must not appear in detail",0,photos.size());
                java.util.ArrayList<View> art=new java.util.ArrayList<>();screen.findViewsWithText(art,"상상 그림 영역",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                assertEquals(1,art.size());assertTrue(art.get(0).getWidth()>0);
                assertEquals("Generated art frame must stay 3:2",Math.round(art.get(0).getWidth()*2f/3f),art.get(0).getHeight());
                assertTrue("Art must have more width than the quote panel",art.get(0).getWidth()>panels.get(0).getWidth());
                View panel=panels.get(0), parent=panel;
                int y=0;while(parent!=screen){y+=parent.getTop();parent=(View)parent.getParent();}
                assertTrue("Card bottom must remain within viewport", y+panel.getHeight() <= screen.getHeight()-screen.dp(48));
                screen.findViewById(R.id.screen_settings).performClick();assertEquals("설정",((android.widget.TextView)screen.findViewById(R.id.page_title)).getText().toString());
                screen.findViewById(R.id.screen_settings).performClick();assertEquals("경주 나들이",((android.widget.TextView)screen.findViewById(R.id.page_title)).getText().toString());
                screen.showRecords(new JSONArray().put(row));assertEquals("저장한 여정",((android.widget.TextView)screen.findViewById(R.id.page_title)).getText().toString());
            } catch(Exception error) {throw new AssertionError(error);}
        });
    }

}
