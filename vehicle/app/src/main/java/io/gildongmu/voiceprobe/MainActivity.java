package io.gildongmu.voiceprobe;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.constant.ErrorType;
import ai.pleos.playground.tts.constant.Mode;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.listener.OnInitializeListener;

public final class MainActivity extends Activity {
    private static final String TAG = "GildongmuProbe";
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech speech;
    private android.media.MediaPlayer adjustedSpeech;
    private final java.util.concurrent.ExecutorService voiceWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> voiceTask;
    private boolean playingAdjustedVoice;
    private boolean voicePreview;
    private TextView output;
    private boolean ready;
    private boolean closed;
    private String pending;
    private String pleosNarrationRemaining;
    private String loadingAdjustedNarration;
    private boolean narrationPaused;
    private final ConversationController conversation = new ConversationController();
    private EventListener playbackListener;
    private AudioSession audio;
    private TurnInput turnInput;
    private int recognitionSegment;
    private String previousQuestionIntent = "none";
    private final ConversationMemory memory = new ConversationMemory();
    private org.json.JSONObject responseForMemory;
    private String responseUserQuestion;
    private String responseSpokenText;
    private ai.pleos.playground.stt.SpeechToText recognition;
    private ai.pleos.playground.stt.listener.ResultListener recognitionListener;
    private boolean sttReady;
    private boolean bargeMonitoring;
    private long bargeDeadline;
    private Button ask;
    private TextView transcript;
    private TextView answer;
    private StoryContent content;
    private final java.util.concurrent.ExecutorService aiWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private AiClient aiRequest;
    private LinearLayout voicePanel, quoteChoices;
    private Button discoveryButton, recordsButton, storyButton;
    private TextView journeyStage;
    private TripStore trips;
    private final java.util.concurrent.ExecutorService artWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile ArtClient artClient;
    private final java.util.Map<String, org.json.JSONObject> queuedArtRows = new java.util.HashMap<>();
    String artEndpoint = BuildConfig.MILE_API_URL;
    private final java.util.Set<String> generatingArtIds = new java.util.HashSet<>();
    private String tripId = java.util.UUID.randomUUID().toString();
    private final java.util.ArrayList<String> selectedCandidates = new java.util.ArrayList<>();
    private int discoveryGeneration;
    private boolean discovering;
    private int visibilityReplayId;
    private boolean visibilityReplayRunning;
    private android.car.Car car;
    private android.car.drivingstate.CarUxRestrictionsManager restrictions;
    private android.app.AlertDialog artConfirmation;
    private boolean parkedFeaturesBlocked = !BuildConfig.DEBUG;

    private void connectVehicleRestrictions() {
        if (getIntent().getBooleanExtra("debug_skip_voice_initialization", false) && BuildConfig.DEBUG) return;
        try {
            car = android.car.Car.createCar(this, main, 0, (connection, connected) -> {
                if (closed) return;
                restrictions = null;
                if (connected) {
                    try {
                        restrictions = (android.car.drivingstate.CarUxRestrictionsManager)
                                connection.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE);
                        if (restrictions != null) {
                            restrictions.registerListener(this::applyVehicleRestrictions);
                            applyVehicleRestrictions(restrictions.getCurrentCarUxRestrictions());
                            return;
                        }
                    } catch (RuntimeException unavailable) { Log.w(TAG, "UX_RESTRICTIONS_UNAVAILABLE"); }
                }
                applyVehicleRestrictions(null);
            });
        } catch (RuntimeException unavailable) { applyVehicleRestrictions(null); }
    }

    private void applyVehicleRestrictions(android.car.drivingstate.CarUxRestrictions signal) {
        parkedFeaturesBlocked = signal == null ? !BuildConfig.DEBUG : signal.isRequiresDistractionOptimization();
        Log.i(TAG, "UX_RESTRICTIONS blocked=" + parkedFeaturesBlocked + " available=" + (signal != null));
        if (parkedFeaturesBlocked) {
            dismissArtConfirmation();
            stopVisibilityReplay();
            screen.dismissSettings();
            if ("records".equals(journeyPhase) || "review".equals(journeyPhase)) {
                journeyPhase = "start";
                discoveryButton.setText("주변 이야기 시작");
                discoveryButton.setOnClickListener(v -> startDiscovery());
            }
        }
        screen.setParkedFeaturesBlocked(parkedFeaturesBlocked);
        refreshScreen();
    }
    private android.os.CancellationSignal locationRequest;
    private static final int LOCATION_PERMISSION = 501;
    private String journeyPhase = "start";
    private MileScreen screen;
    private String recordsReturnPhase = "start";
    private String recordsReturnTitle = "";
    private org.json.JSONArray history = new org.json.JSONArray();

    private void show(String state) {
        Log.i(TAG, state);
        main.post(() -> {
            if (closed) return;
            String message = state;
            if (state.equals("STT_REQUESTED") || state.equals("STT_READY")) message = "마이크 준비 중";
            else if (state.equals("STT_LISTENING")) message = "듣는 중";
            else if (state.equals("STT_TIMEOUT") || state.equals("STT_ENDED_NO_FINAL")) message = "질문을 듣지 못했어요. 버튼으로 다시 시작해주세요.";
            else if (state.equals("STT_FINISHING")) message = "질문을 확인하고 있어요.";
            else if (state.equals("STT_THINKING")) message = "기다리고 있어요.";
            else if (state.equals("VOICE_STOPPED")) message = "대화를 멈췄어요.";
            else if (state.equals("AUDIO_INTERRUPTED")) message = "차량 안내에 양보했어요. 원할 때 다시 시작할 수 있어요.";
            else if (state.equals("AUDIO_UNAVAILABLE")) message = "지금은 음성을 사용할 수 없어요.";
            else if (state.equals("AI_WAITING")) message = "함께 생각하는 중";
            else if (state.equals("AI_UNAVAILABLE")) message = "지금 AI에 연결할 수 없어요. 잠시 후 다시 질문해주세요. 이야기 듣기는 계속 사용할 수 있어요.";
            else if (state.startsWith("STT_ERROR") || state.startsWith("STT_INIT_FAILED") || state.startsWith("STT_REQUEST_FAILED")) message = "음성 입력을 사용할 수 없어요. 마이크 설정을 확인하거나 입력창으로 시험해주세요.";
            else if (state.equals("TTS_STARTED")) message = "이야기 중";
            else if (state.equals("TTS_DONE")) message = "궁금한 이야기를 이어가요";
            else if (state.equals("TTS_STOPPED")) message = "재생을 멈췄어요.";
            else if (state.equals("TTS_PAUSED")) message = "해설을 잠시 멈췄어요.";
            else if (state.equals("TTS_INITIALIZED") || state.equals("TTS_READY")) message = "첨성대 이야기와 질문을 준비했어요.";
            else if (state.startsWith("QUESTION_RECEIVED")) message = "질문을 받았어요.";
            else if (state.equals("QUESTION_REJECTED")) message = "질문을 짧게 다시 입력해주세요.";
            output.setText(message);
            refreshScreen();
            screen.voiceEvent(state);
            if (!journeyPhase.equals("conversation") && (state.startsWith("선택한") || state.startsWith("저장") || state.startsWith("이야기 자료") || state.contains("FAILED")))
                screen.feedback(message);
        });
    }

    private EventListener playbackEvents(int request) {
        return new EventListener() {
            private boolean current() { return !closed && !narrationPaused && conversation.isCurrent(request, ConversationController.State.SPEAKING); }
            @Override public void onReady() { main.post(() -> { if (current()) show("TTS_READY"); }); }
            @Override public void onStart() { main.post(() -> { if (current()) {
                show("TTS_STARTED");
                if (!voicePreview && screen.bargeInMode.isChecked() && screen.isNarration()) startNarrationMonitoring(request);
            } }); }
            @Override public void onUpdatedRms(double rms) { }
            @Override public void onDone() {
                main.post(() -> {
                    if (!current()) return;
                    if (pleosNarrationRemaining != null) {
                        String remaining = afterFirstSentence(pleosNarrationRemaining);
                        pleosNarrationRemaining = null;
                        if (!remaining.isEmpty()) { speakWithPleos(remaining); return; }
                    }
                    stopNarrationMonitoring();
                    releaseAdjustedVoice();
                    playingAdjustedVoice = false;
                    if (voicePreview) {
                        Log.i(TAG, "VOICE_PREVIEW_DONE");
                        voicePreview = false;
                        screen.voicePreview.setText("미리 듣기");
                        conversation.speechDone(request);
                        audio.release();
                        refreshScreen();
                        return;
                    }
                    show("TTS_DONE");
                    try {
                        if (responseForMemory != null) {
                            memory.completed(responseForMemory);
                            previousQuestionIntent = responseForMemory.optString("followupIntent", "none");
                            if (responseUserQuestion != null) {
                                history.put(new org.json.JSONObject().put("role", "user").put("content", responseUserQuestion));
                                history.put(new org.json.JSONObject().put("role", "assistant").put("content", responseSpokenText));
                                while (history.length() > 6) history.remove(0);
                            }
                            responseForMemory = null;
                        }
                    } catch (Exception error) { cancel(); show("AI_UNAVAILABLE"); return; }
                    boolean listen = conversation.speechDone(request);
                    audio.release();
                    if (listen) startListening();
                });
            }
            @Override public void onStop() {
                main.post(() -> { if (current()) { cancel(); show("TTS_STOPPED"); } });
            }
            @Override public void onError(ErrorType error) {
                main.post(() -> { if (current()) { cancel(); show("TTS_ERROR " + error); } });
            }
        };
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            setTaskDescription(new android.app.ActivityManager.TaskDescription.Builder()
                    .setLabel("마일").setIcon(R.mipmap.ic_mile)
                    .setPrimaryColor(getColor(R.color.mile_icon_background)).build());
        } else {
            android.graphics.drawable.Drawable appIcon = getPackageManager().getApplicationIcon(getApplicationInfo());
            android.graphics.Bitmap taskIcon = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888);
            appIcon.setBounds(0, 0, 192, 192); appIcon.draw(new android.graphics.Canvas(taskIcon));
            setTaskDescription(new android.app.ActivityManager.TaskDescription("마일", taskIcon, getColor(R.color.mile_icon_background)));
        }
        audio = new AudioSession(this, main, this::interruptAudio);
        screen = new MileScreen(this);
        setContentView(screen);
        screen.setVoiceAvailable(!BuildConfig.MILE_API_URL.isEmpty());
        trips = new TripStore(this);
        journeyStage = screen.journeyStage;
        output = screen.output;
        transcript = screen.transcript;
        answer = screen.answer;
        voicePanel = screen.voicePanel;
        quoteChoices = screen.quoteChoices;
        discoveryButton = screen.discoveryButton;
        recordsButton = screen.recordsButton;
        storyButton = screen.storyButton;
        ask = screen.ask;
        try { content = new StoryContent(this); }
        catch (Exception error) { show("CONTENT_LOAD_FAILED"); }
        screen.childMode.setOnCheckedChangeListener((button, checked) -> { if (content != null) content.childMode = checked; });
        screen.conversationMode.setOnCheckedChangeListener((button, checked) -> {
            conversation.setConversationEnabled(checked);
            if (!checked) cancel();
            refreshScreen();
        });
        discoveryButton.setOnClickListener(v -> startDiscovery());
        recordsButton.setOnClickListener(v -> showTrips());
        screen.setTripExport(this::exportTrip);
        if (!BuildConfig.MILE_API_URL.isEmpty()) {
            screen.setArtRequest(this::confirmArt);
            screen.setTripSync(this::confirmTripSync);
        }
        storyButton.setOnClickListener(v -> {
            enterConversation();
            screen.setNarration(true);
            journeyStage.setText("별을 읽던 사람들");
            cancel();
            history = new org.json.JSONArray();
            previousQuestionIntent = "none";
            memory.clear();
            conversation.resumeAvailable();
            try {
                conversation.beginNarration();
                responseForMemory = new org.json.JSONObject().put("answer", content.story()).put("evidenceIds", content.storyEvidence()).put("followupQuestion", "").put("followupIntent", "none").put("followupTopic", "none");
                play(content.story());
            } catch (Exception error) { show("CONTENT_UNAVAILABLE"); }
            refreshScreen();
        });
        ask.setOnClickListener(v -> { conversation.resumeAvailable(); conversation.manualTurn(); startListening(); });
        screen.send.setOnClickListener(v -> {
            screen.dismissSettings();
            conversation.resumeAvailable(); conversation.manualTurn(); answerQuestion(screen.input.getText().toString());
        });
        screen.bargeInMode.setOnCheckedChangeListener((button, checked) -> {
            screen.saveNarrationVoiceQuestions(checked);
            if (!checked) stopNarrationMonitoring();
            else if (!voicePreview && screen.isNarration()) startNarrationMonitoring(conversation.generation());
        });
        screen.stop.setOnClickListener(v -> stopConversation());
        screen.pause.setOnClickListener(v -> {
            if (narrationPaused) { resumeNarration(); return; }
            if (screen.isNarration() && conversation.state() == ConversationController.State.SPEAKING) {
                if (adjustedSpeech != null && adjustedSpeech.isPlaying()) {
                    adjustedSpeech.pause();
                    narrationPaused = true;
                } else if (pleosNarrationRemaining != null || loadingAdjustedNarration != null || pending != null) {
                    if (pleosNarrationRemaining != null) pending = pleosNarrationRemaining;
                    else if (loadingAdjustedNarration != null) pending = loadingAdjustedNarration;
                    conversation.beginNarration();
                    releaseAdjustedVoice();
                    playingAdjustedVoice = false;
                    if (playbackListener != null) {
                        speech.removeEventListener(playbackListener);
                        playbackListener = null;
                        speech.stop();
                    }
                    narrationPaused = true;
                }
                if (narrationPaused) {
                    stopNarrationMonitoring();
                    audio.release();
                    show("TTS_PAUSED");
                    return;
                }
            }
            cancel(); memory.clearPending(); previousQuestionIntent = "none"; show("TTS_STOPPED");
        });
        screen.setBackAction(this::navigateBack);
        screen.setOnSettingsClosed(() -> {
            stopVisibilityReplay();
            if (voicePreview) cancel();
        });
        screen.visibilityReplayButton.setOnClickListener(v -> {
            if (visibilityReplayRunning) { stopVisibilityReplay(); return; }
            if (parkedFeaturesBlocked || conversation.state() != ConversationController.State.IDLE
                    || (!ready && !screen.usesAdjustedVoice())) return;
            try (java.io.InputStream input = getAssets().open("content/sokcho-visibility-replay.json");
                 java.util.Scanner scanner = new java.util.Scanner(input, "UTF-8")) {
                org.json.JSONObject route = new org.json.JSONObject(scanner.useDelimiter("\\A").next());
                double start = route.getDouble("start") + 150;
                double end = route.getDouble("end") - 150;
                double length = route.getDouble("length");
                if (!Double.isFinite(start) || !Double.isFinite(end) || !Double.isFinite(length)
                        || start < 0 || start >= end || end > length) {
                    throw new IllegalArgumentException("Invalid visibility window");
                }
                visibilityReplayRunning = true;
                screen.visibilityReplayButton.setText("울산바위 재생 시험 중지");
                advanceVisibilityReplay(++visibilityReplayId, new VisibilityReplay(), 0, start, end, length);
            } catch (Exception error) {
                screen.visibilityReplayButton.setText("시험 경로를 읽지 못했어요");
            }
        });
        screen.voicePreview.setOnClickListener(v -> {
            if (visibilityReplayRunning) stopVisibilityReplay();
            if (voicePreview) { cancel(); return; }
            if (conversation.state() != ConversationController.State.IDLE) {
                screen.voicePreview.setText("대화가 끝난 뒤 미리 듣기");
                return;
            }
            if (!ready && !screen.usesAdjustedVoice()) {
                screen.voicePreview.setText("음성 준비 중 · 다시 시도");
                return;
            }
            voicePreview = true;
            screen.voicePreview.setText("미리 듣기 중지");
            conversation.beginNarration();
            pending = "안녕하세요. 마일이에요. 지나치는 풍경 속 이야기를 들려드릴게요.";
            speakPending();
        });
        refreshScreen();
        // SDK lifetime is exercised separately on Pleos 2.1; UI/storage tests must not depend on it.
        screen.setParkedFeaturesBlocked(parkedFeaturesBlocked);
        connectVehicleRestrictions();
        if (BuildConfig.DEBUG && getIntent().getBooleanExtra("debug_skip_voice_initialization", false)) return;
        speech = new TextToSpeech(getApplicationContext(), Mode.ON_DEVICE);
        speech.initialize(new OnInitializeListener() {
            @Override public void onSuccess() {
                main.post(() -> {
                    if (closed) return;
                    ready = true;
                    speech.setLocale(Locale.KOREA);
                    speech.setAudioAttributes(AudioSession.ATTRIBUTES);
                    show("TTS_INITIALIZED");
                    speakPending();
                });
            }
            @Override public void onFailure(ErrorType error) {
                main.post(() -> { pending = null; show("TTS_INIT_FAILED " + error); });
            }
        });
        recognition = new ai.pleos.playground.stt.SpeechToText(getApplicationContext(),
                ai.pleos.playground.stt.constant.Mode.ON_DEVICE);
        recognition.initialize(new ai.pleos.playground.stt.listener.OnInitializeListener() {
            @Override public void onSuccess() {
                main.post(() -> {
                    if (closed) return;
                    sttReady = true;
                    recognition.setLocale(Locale.KOREA);
                    ask.setEnabled(true);
                    Log.i(TAG, "STT_INITIALIZED");
                    if (!voicePreview && screen.isNarration() && screen.bargeInMode.isChecked())
                        startNarrationMonitoring(conversation.generation());
                    refreshScreen();
                });
            }
            @Override public void onFailure(ai.pleos.playground.stt.constant.ErrorType error) {
                main.post(() -> { if (!closed) show("STT_INIT_FAILED " + error); });
            }
        });
        handle(getIntent());
    }

    private void refreshScreen() {
        if (screen != null) {
            screen.setNarrationPaused(narrationPaused);
            screen.render(journeyPhase, conversation.state(), sttReady);
        }
    }

    private void resumeNarration() {
        narrationPaused = false;
        if (!audio.acquire()) { cancel(); show("AUDIO_UNAVAILABLE"); return; }
        if (adjustedSpeech != null) {
            adjustedSpeech.start();
            playbackEvents(conversation.generation()).onStart();
        } else speakPending();
    }

    private void navigateBack() {
        if (screen.closeOverlay()) return;
        if ("records".equals(journeyPhase)) {
            journeyPhase = recordsReturnPhase;
            journeyStage.setText(recordsReturnTitle);
        } else if ("conversation".equals(journeyPhase)) {
            stopConversation(); return;
        } else {
            cancel(); stopLocationRequest(); discovering = false; discoveryGeneration++;
            journeyPhase = "start";
            discoveryButton.setText("주변 이야기 시작");
            discoveryButton.setOnClickListener(v -> startDiscovery());
        }
        refreshScreen();
    }

    @Override public void onBackPressed() {
        if (screen.closeOverlay()) return;
        if (journeyPhase.equals("start")) super.onBackPressed();
        else navigateBack();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        screen.applyTheme();
    }

    private void advanceVisibilityReplay(int id, VisibilityReplay replay, double position,
                                         double start, double end, double length) {
        if (!visibilityReplayRunning || id != visibilityReplayId || closed || parkedFeaturesBlocked) return;
        VisibilityReplay.Cue cue = replay.step(position, 25, 60 / 3.6, 0.25, start, end,
                conversation.state() != ConversationController.State.IDLE);
        if (cue != VisibilityReplay.Cue.NONE) {
            Log.i(TAG, "VISIBILITY_REPLAY " + cue + " distance_m=" + Math.round(position));
            voicePreview = true;
            conversation.beginNarration();
            pending = cue == VisibilityReplay.Cue.PREVIEW ? "시연입니다. 잠시 후 울산바위 관찰 후보 구간에 들어가요."
                    : "시연입니다. 울산바위 왼쪽 창밖 안내가 나오는 구간이에요.";
            speakPending();
        }
        if (position >= length) {
            visibilityReplayRunning = false;
            screen.visibilityReplayButton.setText("울산바위 안내 재생 시험");
            return;
        }
        main.postDelayed(() -> advanceVisibilityReplay(id, replay, position + (60 / 3.6) * 0.25,
                start, end, length), 250);
    }

    private void stopVisibilityReplay() {
        if (!visibilityReplayRunning) return;
        visibilityReplayRunning = false;
        visibilityReplayId++;
        if (voicePreview) cancel();
        screen.visibilityReplayButton.setText("울산바위 안내 재생 시험");
    }

    private void startDiscovery() {
        if (screen.usesLiveLocation() && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION);
            return;
        }
        if (content == null) { show("이야기 자료를 읽지 못했어요. 앱을 다시 열어주세요."); return; }
        cancel();
        conversation.resumeAvailable();
        history = new org.json.JSONArray();
        memory.clear();
        previousQuestionIntent = "none";
        selectedCandidates.clear();
        tripId = java.util.UUID.randomUUID().toString();
        journeyPhase = "searching";
        discovering = true;
        final int token = ++discoveryGeneration;
        NearbyStory nearby = new NearbyStory();
        quoteChoices.setVisibility(android.view.View.GONE);
        voicePanel.setVisibility(android.view.View.GONE);
        discoveryButton.setText("탐색 중지");
        discoveryButton.setOnClickListener(v -> {
            stopLocationRequest();
            discovering = false;
            discoveryGeneration++;
            journeyPhase = "start";
            discoveryButton.setText("주변 이야기 시작");
            discoveryButton.setOnClickListener(w -> startDiscovery());
            journeyStage.setText("주변 탐색을 멈췄어요.");
            refreshScreen();
        });
        transcript.setText(""); answer.setText("");
        journeyStage.setText("주변 이야기를\n찾고 있어요");
        refreshScreen();
        if (screen.usesLiveLocation()) {
            findCurrentLocation(token, nearby);
            return;
        }
        double[] replayDistances = {900, 500, 250};
        for (int i = 0; i < replayDistances.length; i++) {
            final double distance = replayDistances[i];
            main.postDelayed(() -> {
                if (closed || !discovering || token != discoveryGeneration) return;
                Log.i(TAG, "DISCOVERY_REPLAY_DISTANCE_M=" + (int)distance);
                if (nearby.offer(distance, discovering, conversation.state() != ConversationController.State.IDLE)) {
                    showNearbyStory("거리 재생 시연");
                }
            }, (i + 1) * 700L);
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != LOCATION_PERMISSION || closed || !screen.usesLiveLocation()) return;
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if ("start".equals(journeyPhase)) startDiscovery();
        } else screen.feedback("정확한 위치 권한이 필요해요. 설정에서 거리 재생으로 전환할 수 있어요.");
    }

    private void findCurrentLocation(int token, NearbyStory nearby) {
        android.location.LocationManager manager = getSystemService(android.location.LocationManager.class);
        android.os.CancellationSignal request = new android.os.CancellationSignal();
        locationRequest = request;
        main.postDelayed(() -> finishLocationSearch(token, "현재 위치를 받지 못했어요. 위치 설정을 확인해 주세요."), 10000);
        try {
            if (android.os.Build.VERSION.SDK_INT < 30 || manager == null
                    || !manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                finishLocationSearch(token, "GPS를 사용할 수 없어요. 설정에서 거리 재생으로 전환할 수 있어요.");
                return;
            }
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                finishLocationSearch(token, "정확한 위치 권한이 필요해요. 설정에서 거리 재생으로 전환할 수 있어요.");
                return;
            }
            manager.getCurrentLocation(android.location.LocationManager.GPS_PROVIDER, request, getMainExecutor(), fix -> {
                if (!discovering || token != discoveryGeneration) return;
                if (fix == null || !fix.hasAccuracy() || fix.getAccuracy() > 50
                        || SystemClock.elapsedRealtimeNanos() - fix.getElapsedRealtimeNanos() > 15_000_000_000L) {
                    finishLocationSearch(token, "정확한 현재 위치를 받지 못했어요. 다시 시도해 주세요.");
                    return;
                }
                float[] distance = new float[1];
                android.location.Location.distanceBetween(fix.getLatitude(), fix.getLongitude(), 35.83463, 129.21910, distance);
                if (nearby.offer(distance[0] + fix.getAccuracy(), true, conversation.state() != ConversationController.State.IDLE)) {
                    showNearbyStory("현재 위치");
                } else finishLocationSearch(token, "지금 위치에서 소개할 주변 이야기가 없어요.");
            });
        } catch (RuntimeException failure) {
            finishLocationSearch(token, "현재 위치를 사용할 수 없어요. 위치 설정을 확인해 주세요.");
        }
    }

    private void finishLocationSearch(int token, String message) {
        if (!discovering || token != discoveryGeneration) return;
        discoveryButton.performClick();
        screen.feedback(message);
    }

    private void stopLocationRequest() {
        if (locationRequest != null) { locationRequest.cancel(); locationRequest = null; }
    }

    private void showNearbyStory(String source) {
        stopLocationRequest();
        discovering = false;
        journeyPhase = "found";
        discoveryButton.setVisibility(android.view.View.GONE);
        discoveryButton.setOnClickListener(v -> startDiscovery());
        journeyStage.setText("별을 읽던 사람들");
        voicePanel.setVisibility(android.view.View.VISIBLE);
        storyButton.setVisibility(android.view.View.VISIBLE);
        ask.setVisibility(android.view.View.GONE);
        show("NEARBY_STORY_FOUND / " + source);
    }

    private void enterConversation() {
        if ("review".equals(journeyPhase)) {
            tripId = java.util.UUID.randomUUID().toString();
            selectedCandidates.clear();
        }
        discovering = false;
        discoveryGeneration++;
        journeyPhase = "conversation";
        quoteChoices.setVisibility(android.view.View.GONE);
        voicePanel.setVisibility(android.view.View.VISIBLE);
        discoveryButton.setVisibility(android.view.View.GONE);
        storyButton.setVisibility(android.view.View.GONE);
        ask.setVisibility(android.view.View.VISIBLE);
        journeyStage.setText("함께 나누는 이야기");
        screen.dismissSettings();
        refreshScreen();
    }

    private void showReview() {
        if (parkedFeaturesBlocked) { journeyPhase = "start"; refreshScreen(); return; }
        discovering = false;
        discoveryGeneration++;
        journeyPhase = "review";
        journeyStage.setText("이번 여정에 남길 한마디");
        voicePanel.setVisibility(android.view.View.GONE);
        screen.review(selectedCandidates, words -> {
            if (parkedFeaturesBlocked || !"review".equals(journeyPhase)) return;
            try {
                trips.save(tripId, words, content.childMode);
                screen.saved();
                journeyStage.setText("경주 여정에 한마디를 남겼어요");
                if (artEndpoint.isEmpty()) {
                    show("한마디를 이 기기에 저장했어요.");
                    return;
                }
                show("한마디를 저장했어요. 상상 그림을 만들고 있어요.");
                org.json.JSONArray savedRows = trips.read();
                for (int i = 0; i < savedRows.length(); i++) if (tripId.equals(savedRows.getJSONObject(i).getString("id"))) {
                    generateArt(savedRows.getJSONObject(i)); break;
                }
            } catch (Exception error) {
                show("저장하지 못했어요. 기존 기록은 유지했습니다. 다시 시도해주세요.");
            }
        });
        discoveryButton.setText("주변 이야기 시작");
        discoveryButton.setOnClickListener(v -> startDiscovery());
        refreshScreen();
    }

    private void showTrips() {
        if (parkedFeaturesBlocked) return;
        if (journeyPhase.equals("records")) return;
        recordsReturnPhase = journeyPhase;
        recordsReturnTitle = journeyStage.getText().toString();
        cancel(); memory.clearPending(); previousQuestionIntent = "none";
        try {
            org.json.JSONArray rows = trips.read();
            journeyPhase = "records";
            screen.showRecords(rows);
            for (String id : generatingArtIds) screen.artState(id, true, rows);
            refreshScreen();
        } catch (Exception error) { show("저장한 여정을 읽지 못했어요. 기존 데이터는 변경하지 않았습니다."); }
    }

    private org.json.JSONObject tripPayload(org.json.JSONObject row) throws Exception {
            org.json.JSONObject export = new org.json.JSONObject().put("version", 1)
                    .put("id", row.getString("id")).put("quote", row.getString("quote"))
                    .put("place", row.getString("place")).put("createdAt", row.getLong("createdAt"));
            java.io.File art = trips.artFile(row);
            if (art != null && art.length() <= 8_000_000) {
                export.put("art", android.util.Base64.encodeToString(java.nio.file.Files.readAllBytes(art.toPath()), android.util.Base64.NO_WRAP));
            }
        return export;
    }

    private void confirmTripSync(org.json.JSONObject row) {
        if (parkedFeaturesBlocked || !"records".equals(journeyPhase)) return;
        dismissArtConfirmation();
        android.widget.EditText keyInput = new android.widget.EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setHint("64자리 계정 키");
        keyInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
        artConfirmation = new android.app.AlertDialog.Builder(this)
                .setTitle("모바일 계정에 여정 보내기")
                .setMessage("선택한 한마디와 AI 그림을 이 계정으로 보냅니다.")
                .setView(keyInput).setNegativeButton("취소", null)
                .setPositiveButton("보내기", (dialog, which) -> {
                    if (parkedFeaturesBlocked || !"records".equals(journeyPhase)) return;
                    String key = keyInput.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
                    if (!key.matches("[a-f0-9]{64}")) { screen.feedback("계정 키를 확인해 주세요."); return; }
                    artWorker.execute(() -> sendTripToAlbum(row, key));
                }).show();
    }

    private void sendTripToAlbum(org.json.JSONObject row, String key) {
        java.net.HttpURLConnection connection = null;
        boolean sent = false;
        try {
            connection = (java.net.HttpURLConnection) java.net.URI.create(BuildConfig.MILE_API_URL)
                    .resolve("/album").toURL().openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "application/json");
            byte[] body = tripPayload(row).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            sent = connection.getResponseCode() == 200;
        } catch (Exception ignored) {
        } finally { if (connection != null) connection.disconnect(); }
        boolean success = sent;
        main.post(() -> {
            if (!closed && !parkedFeaturesBlocked && "records".equals(journeyPhase))
                screen.feedback(success ? "계정에 여정을 보냈어요." : "여정을 보내지 못했어요. 연결을 확인해 주세요.");
        });
    }

    private void exportTrip(org.json.JSONObject row) {
        if (parkedFeaturesBlocked || !"records".equals(journeyPhase)) return;
        try {
            org.json.JSONObject export = tripPayload(row);
            java.io.File directory = new java.io.File(getCacheDir(), "trip-export");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Export directory unavailable");
            String filename = java.util.UUID.randomUUID() + ".mile";
            java.io.File output = new java.io.File(directory, filename);
            try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
                stream.write(export.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            android.net.Uri uri = new android.net.Uri.Builder().scheme("content")
                    .authority(getPackageName() + ".trip-export").appendPath(filename).build();
            Intent share = new Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(android.content.ClipData.newRawUri("MILE 여정", uri));
            startActivity(Intent.createChooser(share, "MILE 여정 보내기"));
        } catch (Exception error) { screen.feedback("여정 파일을 공유하지 못했어요."); }
    }

    private void confirmArt(org.json.JSONObject row) {
        if (generatingArtIds.contains(row.optString("id")) || closed || parkedFeaturesBlocked || !"records".equals(journeyPhase)) return;
        dismissArtConfirmation();
        artConfirmation = new android.app.AlertDialog.Builder(this).setTitle("이 말로 그림 만들기")
            .setMessage("“" + row.optString("quote") + "”\n\n선택한 한마디와 장소를 서버·OpenAI로 전송합니다. 이미지 API 이용료가 발생하며, 완성한 AI 상상 그림은 이 기기의 여정에 저장합니다.")
            .setNegativeButton("취소", null).setPositiveButton("그림 만들기", (dialog, which) -> {
                if (!parkedFeaturesBlocked && "records".equals(journeyPhase)) generateArt(row);
            }).show();
    }

    private void dismissArtConfirmation() {
        if (artConfirmation != null) {
            artConfirmation.dismiss();
            artConfirmation = null;
        }
    }

    private void generateArt(org.json.JSONObject row) {
        if (closed) return;
        if (generatingArtIds.contains(row.optString("id"))) { queuedArtRows.put(row.optString("id"), row); return; }
        final String id = row.optString("id"), quote = row.optString("quote");
        try {
            org.json.JSONArray current = trips.read();
            for (int i = 0; i < current.length(); i++) {
                org.json.JSONObject saved = current.getJSONObject(i);
                if (id.equals(saved.getString("id")) && quote.equals(saved.getString("quote")) && trips.artFile(saved) != null) return;
            }
        } catch (Exception error) { screen.feedback("여정을 읽지 못해 그림 생성을 멈췄어요."); return; }
        generatingArtIds.add(id);
        try { screen.artState(id, true, trips.read()); } catch (Exception error) { generatingArtIds.remove(id); return; }
        final String endpoint = artEndpoint;
        artWorker.execute(() -> {
            final ArtClient client = new ArtClient(); artClient = client;
            if (closed) { client.cancel(); return; }
            boolean saved = false;
            try {
                org.json.JSONObject result = client.generate(endpoint, row);
                byte[] png = android.util.Base64.decode(result.getString("imageBase64"), android.util.Base64.DEFAULT);
                if (closed || Thread.currentThread().isInterrupted()) return;
                trips.saveArt(id, quote, png, result.getString("model")); saved = true;
            } catch (Exception error) { /* Selected words remain saved when generation fails. */ }
            final boolean success = saved;
            main.post(() -> {
                if (closed) return;
                generatingArtIds.remove(id);
                try { screen.artState(id, false, trips.read()); } catch (Exception error) { }
                org.json.JSONObject queued = queuedArtRows.remove(id);
                if (queued != null) generateArt(queued);
                screen.feedback(success ? "상상 그림을 여정에 저장했어요." : "그림을 만들지 못했어요. 저장한 말은 그대로예요. 연결·이미지 API 권한을 확인한 뒤 다시 시도할 수 있어요.");
            });
        });
    }

    private void startListening() {
        if (!sttReady || closed || !conversation.canStart() || conversation.state() == ConversationController.State.LISTENING) return;
        screen.setNarration(false);
        enterConversation();
        cancel();
        final int request = conversation.beginListening();
        ask.setEnabled(false);
        turnInput = new TurnInput(SystemClock.elapsedRealtime());
        if (!audio.acquire()) { cancel(); show("AUDIO_UNAVAILABLE"); return; }
        transcript.setText("듣고 있어요.");
        requestSegment(request);
        checkListeningDeadline(request);
    }

    private void checkListeningDeadline(int request) {
        main.postDelayed(() -> {
            if (!conversation.isCurrent(request, ConversationController.State.LISTENING) || closed) return;
            if (turnInput.expired(SystemClock.elapsedRealtime())) { cancel(); show("STT_TIMEOUT"); }
            else checkListeningDeadline(request);
        }, Math.max(1, turnInput.deadline() - SystemClock.elapsedRealtime()));
    }

    private void startNarrationMonitoring(int request) {
        if (!sttReady || narrationPaused || bargeMonitoring || !conversation.isCurrent(request, ConversationController.State.SPEAKING)) return;
        bargeMonitoring = true;
        bargeDeadline = SystemClock.elapsedRealtime() + 90000;
        requestSegment(request, new BargeInGate(answer.getText().toString()));
        main.postDelayed(() -> {
            if (bargeMonitoring && conversation.isCurrent(request)) stopNarrationMonitoring();
        }, 90000);
    }

    private void stopNarrationMonitoring() {
        if (!bargeMonitoring) return;
        bargeMonitoring = false; recognitionSegment++;
        if (recognition != null && recognitionListener != null) {
            recognition.removeListener(recognitionListener); recognitionListener = null; recognition.stop();
        }
        screen.voiceEvent("BARGE_OFF");
    }

    private int takeOverNarration() {
        pleosNarrationRemaining = null;
        // Keep the same STT request: stopping/restarting it would lose the first question syllables.
        bargeMonitoring = false;
        conversation.manualTurn();
        int request = conversation.beginListening();
        if (playbackListener != null) { speech.removeEventListener(playbackListener); playbackListener = null; }
        pending = null; responseForMemory = null; responseUserQuestion = null; responseSpokenText = null;
        if (playingAdjustedVoice) {
            releaseAdjustedVoice();
            playingAdjustedVoice = false;
        } else speech.stop();
        answer.setText(""); transcript.setText("");
        screen.setNarration(false); screen.voiceEvent("BARGE_OFF");
        turnInput = new TurnInput(SystemClock.elapsedRealtime());
        checkListeningDeadline(request);
        show("STT_LISTENING");
        Log.i(TAG, "BARGE_TAKEOVER");
        return request;
    }

    private void requestSegment(int request) { requestSegment(request, null); }
    private void requestSegment(int request, BargeInGate gate) {
        if (!conversation.isCurrent(request, gate == null ? ConversationController.State.LISTENING : ConversationController.State.SPEAKING) || closed) return;
        if (recognitionListener != null) {
            recognition.removeListener(recognitionListener);
            recognition.stop();
        }
        final int segment = ++recognitionSegment;
        recognitionListener = new ai.pleos.playground.stt.listener.ResultListener() {
            private int activeRequest = request;
            private boolean monitoring = gate != null;
            private boolean reportedCapture;
            private boolean current() { return !closed && segment == recognitionSegment && conversation.isCurrent(activeRequest,
                    monitoring ? ConversationController.State.SPEAKING : ConversationController.State.LISTENING); }
            private void monitorEnded() {
                if (SystemClock.elapsedRealtime() >= bargeDeadline) { stopNarrationMonitoring(); return; }
                recognitionSegment++;
                screen.voiceEvent("BARGE_REQUESTED");
                main.postDelayed(() -> {
                    if (bargeMonitoring && conversation.isCurrent(request, ConversationController.State.SPEAKING)) requestSegment(request, gate);
                }, 150);
            }
            @Override public void onReady() { main.post(() -> { if (current()) {
                if (monitoring) {
                    Log.i(TAG, "BARGE_READY");
                    screen.voiceEvent("BARGE_READY");
                } else show("STT_READY");
            } }); }
            @Override public void onStartedRecognition() { main.post(() -> { if (current()) {
                if (monitoring) screen.voiceEvent("BARGE_LISTENING"); else show("STT_LISTENING");
            } }); }
            @Override public void onUpdated(String text, boolean completed) {
                main.post(() -> {
                    if (!current()) return;
                    if (monitoring) {
                        if (!gate.shouldInterrupt(text) && !(completed && gate.isDirectQuestion(text))) {
                            if (completed) {
                                if (text != null && !text.isBlank()) screen.voiceEvent("BARGE_CUE_MISSED");
                                monitorEnded();
                            }
                            return;
                        }
                        // A partial wake word alone may be revised by the recognizer.
                        if (!completed && BargeInGate.questionText(text).isEmpty()) return;
                        activeRequest = takeOverNarration();
                        monitoring = false;
                    }
                    String question = gate == null ? text : BargeInGate.questionText(text);
                    transcript.setText(question);
                    if (!completed) turnInput.partial(question, SystemClock.elapsedRealtime());
                    if (completed) {
                        Log.i(TAG, "STT_FINAL characters=" + (question == null ? 0 : question.length()));
                        if (gate != null && question.isEmpty()) {
                            recognitionSegment++;
                            show("STT_THINKING");
                            main.postDelayed(() -> requestSegment(activeRequest), 100);
                            return;
                        }
                        TurnInput.Action action = turnInput.accept(question, SystemClock.elapsedRealtime());
                        if (action == TurnInput.Action.STOP) { stopConversation(); show("VOICE_STOPPED"); }
                        else if (action == TurnInput.Action.ANSWER) answerQuestion(turnInput.answer());
                        else if (action == TurnInput.Action.WAIT) {
                            recognitionSegment++;
                            show("STT_THINKING");
                            checkListeningDeadline(activeRequest);
                            main.postDelayed(() -> requestSegment(activeRequest), 100);
                        } else { cancel(); show("STT_TIMEOUT"); }
                    }
                });
            }
            @Override public void onUpdatedEpdData(long on, long off) { }
            @Override public void onUpdatedRms(double rms) {
                main.post(() -> { if (current() && monitoring && !reportedCapture) {
                    reportedCapture = true; screen.voiceEvent("BARGE_LISTENING");
                } });
            }
            @Override public void onEndedRecognition() {
                // Pleos 2.1 can emit the end event before the final transcript; keep the bounded request open.
                main.post(() -> { if (current()) {
                    if (!monitoring) show("STT_FINISHING");
                    else main.postDelayed(() -> { if (current() && monitoring) monitorEnded(); }, 500);
                } });
            }
            @Override public void onError(ai.pleos.playground.stt.constant.ErrorType error) {
                main.post(() -> { if (current()) {
                    if (monitoring) { stopNarrationMonitoring(); screen.feedback("말로 질문 대기를 종료했어요. 질문하기 버튼은 계속 사용할 수 있어요."); }
                    else { cancel(); show("STT_ERROR " + error); }
                } });
            }
        };
        recognition.addListener(recognitionListener);
        try { recognition.request(); if (gate != null) screen.voiceEvent("BARGE_REQUESTED"); else show("STT_REQUESTED"); }
        catch (RuntimeException error) {
            if (gate != null) { stopNarrationMonitoring(); screen.feedback("말로 질문을 준비하지 못했어요. 질문하기 버튼을 이용해주세요."); }
            else { cancel(); show("STT_REQUEST_FAILED " + error.getClass().getSimpleName()); }
        }
    }

    private void answerQuestion(String question) {
        if (closed || !conversation.canStart()) return;
        cancel();
        if (question == null || question.trim().isEmpty() || question.length() > 1000) {
            show("QUESTION_REJECTED"); return;
        }
        if (TurnInput.isStop(question)) { stopConversation(); show("VOICE_STOPPED"); return; }
        screen.setNarration(false);
        enterConversation();
        if (!selectedCandidates.contains(question)) {
            if (selectedCandidates.size() == 20) selectedCandidates.remove(0);
            selectedCandidates.add(question);
        }
        transcript.setText(question);
        if (BuildConfig.MILE_API_URL.isEmpty()) {
            answer.setText("");
            show("AI_UNAVAILABLE");
            return;
        }
        final int request = conversation.beginAnswer();
        if (!audio.acquire()) { cancel(); show("AUDIO_UNAVAILABLE"); return; }
        final AiClient client = new AiClient();
        aiRequest = client;
        show("AI_WAITING");
        answer.setText("");
        try {
            org.json.JSONObject input = new org.json.JSONObject()
                    .put("question", question).put("contentId", "gyeongju_cheomseongdae_v1")
                    .put("mode", content.childMode ? "child" : "adult")
                    .put("previousQuestionIntent", previousQuestionIntent)
                    .put("allowFollowup", conversation.mayAskFollowup())
                    .put("conversationMemory", memory.snapshot())
                    .put("history", new org.json.JSONArray(history.toString()));
            aiWorker.execute(() -> {
                try {
                    org.json.JSONObject result = client.ask(BuildConfig.MILE_API_URL, input);
                    main.post(() -> {
                        if (closed || !conversation.isCurrent(request, ConversationController.State.GENERATING)) return;
                        try {
                            String text = result.getString("answer");
                            String followup = result.optString("followupQuestion", "").trim();
                            boolean expectsReply = conversation.mayAskFollowup() && !followup.isEmpty();
                            if (expectsReply) text += " " + followup;
                            if (!conversation.answerReady(request, expectsReply)) return;
                            responseForMemory = new org.json.JSONObject(result.toString());
                            if (!expectsReply) responseForMemory.put("followupQuestion", "").put("followupIntent", "none").put("followupTopic", "none");
                            responseUserQuestion = question;
                            responseSpokenText = text;
                            Log.i(TAG, "AI_RESPONSE status=" + result.getString("status") + " evidenceCount=" + result.getJSONArray("evidenceIds").length());
                            aiRequest = null;
                            play(text);
                        } catch (Exception error) { cancel(); show("AI_UNAVAILABLE"); }
                    });
                } catch (Exception error) {
                    main.post(() -> { if (!closed && conversation.isCurrent(request, ConversationController.State.GENERATING)) { cancel(); show("AI_UNAVAILABLE"); } });
                }
            });
        } catch (Exception error) { cancel(); show("AI_UNAVAILABLE"); }
    }

    private void play(String text) {
        answer.setText(text);
        refreshScreen();
        pending = text;
        speakPending();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent intent) {
        String action = GleoProtocol.action(intent);
        if (GleoProtocol.STOP.equals(action)) {
            stopConversation();
            respond(action, "success", "The spoken response was stopped.", "stopped");
        } else if (GleoProtocol.ASK.equals(action)) {
            long received = SystemClock.elapsedRealtime();
            try {
                String question = GleoProtocol.question(intent);
                // Questions are deliberately kept in memory, not written to logcat or storage.
                show("QUESTION_RECEIVED characters=" + question.length());
                respond(action, "success", "The question was accepted; MILE will handle the spoken response.", "accepted");
                Log.i(TAG, "CALLBACK_MS=" + (SystemClock.elapsedRealtime() - received));
                conversation.resumeAvailable();
                answerQuestion(question);
            } catch (Exception error) {
                cancel();
                show("QUESTION_REJECTED");
                respond(action, "fail", "A valid question string is required.", "error");
            }
        }
    }

    private void speakPending() {
        if (pending == null || closed || narrationPaused || (!ready && !screen.usesAdjustedVoice())) return;
        String text = pending;
        pending = null;
        if (conversation.state() != ConversationController.State.SPEAKING) return;
        if (!audio.acquire()) { cancel(); show("AUDIO_UNAVAILABLE"); return; }
        if (screen.usesAdjustedVoice()) {
            playAdjustedVoice(text, conversation.generation());
            return;
        }
        speakWithPleos(text);
    }

    private void speakWithPleos(String text) {
        if (playbackListener != null) speech.removeEventListener(playbackListener);
        playbackListener = playbackEvents(conversation.generation());
        speech.addEventListener(playbackListener);
        if (!voicePreview && screen.isNarration()) {
            pleosNarrationRemaining = text;
            text = text.substring(0, text.length() - afterFirstSentence(text).length()).trim();
        }
        try { speech.speak(text, true); }
        catch (RuntimeException error) { cancel(); show("TTS_REQUEST_FAILED " + error.getClass().getSimpleName()); }
    }

    private static String afterFirstSentence(String text) {
        String[] parts = text.split("(?<=[.!?。！？])\\s+", 2);
        return parts.length == 2 ? parts[1] : "";
    }

    private void playAdjustedVoice(String text, int request) {
        EventListener events = playbackEvents(request);
        playingAdjustedVoice = true;
        if (!voicePreview && screen.isNarration()) loadingAdjustedNarration = text;
        float rate = screen.voiceRate(), pitch = screen.voicePitch();
        voiceTask = voiceWorker.submit(() -> {
            java.io.File file = null;
            java.net.HttpURLConnection connection = null;
            try {
                java.net.URI endpoint = java.net.URI.create(BuildConfig.MILE_API_URL).resolve("/voice");
                connection = (java.net.HttpURLConnection) endpoint.toURL().openConnection();
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] body = new org.json.JSONObject().put("text", text).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
                if (connection.getResponseCode() != 200 || !"audio/wav".equals(connection.getContentType()))
                    throw new java.io.IOException("voice unavailable");
                file = java.io.File.createTempFile("mile-voice-", ".wav", getCacheDir());
                try (java.io.InputStream in = connection.getInputStream();
                     java.io.OutputStream out = new java.io.FileOutputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    for (int count; (count = in.read(buffer)) != -1;) {
                        total += count;
                        if (total > 4_000_000 || Thread.currentThread().isInterrupted())
                            throw new java.io.IOException("voice transfer cancelled");
                        out.write(buffer, 0, count);
                    }
                }
                java.io.File downloaded = file;
                file = null;
                main.post(() -> {
                    try {
                        if (closed || !conversation.isCurrent(request, ConversationController.State.SPEAKING)) return;
                        adjustedSpeech = new android.media.MediaPlayer();
                        adjustedSpeech.setAudioAttributes(AudioSession.ATTRIBUTES);
                        try (java.io.FileInputStream input = new java.io.FileInputStream(downloaded)) {
                            adjustedSpeech.setDataSource(input.getFD());
                        }
                        adjustedSpeech.setOnPreparedListener(player -> {
                            if (player != adjustedSpeech) return;
                            try {
                                player.setPlaybackParams(new android.media.PlaybackParams().setSpeed(rate).setPitch(pitch));
                                player.start();
                                loadingAdjustedNarration = null;
                                Log.i(TAG, "ADJUSTED_VOICE_STARTED rate=" + rate + " pitch=" + pitch);
                                events.onStart();
                            } catch (RuntimeException error) { adjustedVoiceFailed(request); }
                        });
                        adjustedSpeech.setOnCompletionListener(player -> events.onDone());
                        adjustedSpeech.setOnErrorListener((player, what, extra) -> { adjustedVoiceFailed(request); return true; });
                        adjustedSpeech.prepareAsync();
                    } catch (Exception error) { adjustedVoiceFailed(request); }
                    finally { downloaded.delete(); }
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (closed || !conversation.isCurrent(request, ConversationController.State.SPEAKING)) return;
                    if (!ready || voicePreview) { adjustedVoiceFailed(request); return; }
                    releaseAdjustedVoice();
                    playingAdjustedVoice = false;
                    Log.i(TAG, "ADJUSTED_VOICE_FALLBACK");
                    speakWithPleos(text);
                });
            }
            finally {
                if (file != null) file.delete();
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void releaseAdjustedVoice() {
        loadingAdjustedNarration = null;
        if (voiceTask != null) { voiceTask.cancel(true); voiceTask = null; }
        if (adjustedSpeech != null) { adjustedSpeech.release(); adjustedSpeech = null; }
    }

    private void adjustedVoiceFailed(int request) {
        if (!conversation.isCurrent(request, ConversationController.State.SPEAKING)) return;
        boolean preview = voicePreview;
        cancel();
        if (preview) screen.voicePreview.setText("재생 실패 · 다시 듣기");
        else show("TTS_REQUEST_FAILED");
    }

    private void respond(String action, String status, String message, String answerState) {
        try {
            String response = GleoProtocol.callback(action, status, message, answerState);
            sendBroadcast(new Intent(GleoProtocol.RESPONSE)
                    .putExtra("packageName", getPackageName()).putExtra("response", response));
            Log.i(TAG, "CALLBACK_SENT " + response);
        } catch (org.json.JSONException error) { show("CALLBACK_ENCODING_FAILED"); }
    }

    private void cancel() {
        narrationPaused = false;
        pleosNarrationRemaining = null;
        boolean wasSpeaking = conversation.state() == ConversationController.State.SPEAKING;
        conversation.cancel();
        if (voicePreview) {
            voicePreview = false;
            screen.voicePreview.setText("미리 듣기");
        }
        boolean wasAdjusted = playingAdjustedVoice;
        playingAdjustedVoice = false;
        if (wasAdjusted) releaseAdjustedVoice();
        stopNarrationMonitoring();
        recognitionSegment++;
        if (aiRequest != null) { aiRequest.cancel(); aiRequest = null; }
        pending = null;
        responseForMemory = null;
        responseUserQuestion = null;
        responseSpokenText = null;
        if (recognition != null && recognitionListener != null) {
            recognition.removeListener(recognitionListener);
            recognitionListener = null;
            recognition.stop();
        }
        if (audio != null) audio.release();
        if (ask != null) ask.setEnabled(sttReady && !closed);
        if (ready) {
            if (playbackListener != null) { speech.removeEventListener(playbackListener); playbackListener = null; }
            // The shared SDK stop is asynchronous; an idle stop can arrive during the next playback.
            if (wasSpeaking && !wasAdjusted) { speech.stop(); show("TTS_STOPPED"); }
        }
    }

    private void stopConversation() {
        cancel();
        memory.clearPending();
        previousQuestionIntent = "none";
        showReview();
    }

    private void interruptAudio() {
        if (closed) return;
        cancel();
        conversation.interrupt();
        memory.clearPending();
        previousQuestionIntent = "none";
        show("AUDIO_INTERRUPTED");
    }

    @Override protected void onStop() {
        stopVisibilityReplay();
        dismissArtConfirmation();
        stopLocationRequest();
        discovering = false;
        discoveryGeneration++;
        if ("searching".equals(journeyPhase)) {
            journeyPhase = "start";
            discoveryButton.setVisibility(android.view.View.VISIBLE);
            discoveryButton.setText("주변 이야기 시작");
            discoveryButton.setOnClickListener(v -> startDiscovery());
            journeyStage.setText("탐색을 멈췄어요. 원할 때 다시 시작하세요.");
        }
        cancel();
        refreshScreen();
        super.onStop();
    }

    @Override protected void onDestroy() {
        cancel();
        conversation.close();
        memory.clear();
        closed = true;
        if (restrictions != null) restrictions.unregisterListener();
        if (car != null) car.disconnect();
        aiWorker.shutdownNow();
        if (artClient != null) artClient.cancel();
        artWorker.shutdownNow();
        history = new org.json.JSONArray();

        if (speech != null) speech.release();
        voiceWorker.shutdownNow();
        if (recognition != null) recognition.release();
        super.onDestroy();
    }
}
