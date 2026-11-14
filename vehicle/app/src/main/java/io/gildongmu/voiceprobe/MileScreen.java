package io.gildongmu.voiceprobe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.function.Consumer;

/** Native presentation only. SDK, conversation and persistence remain in the Activity/controller. */
final class MileScreen extends LinearLayout {
    final TextView journeyStage, output, transcript, answer;
    final Button discoveryButton, recordsButton, storyButton, ask, stop, pause, send;
    final EditText input;
    final Switch childMode, conversationMode, bargeInMode;
    final LinearLayout voicePanel, quoteChoices;
    private final LinearLayout header, card, actions, settings, recordRows, footer;
    private final FrameLayout content;
    private final ScrollView reviewScroll, recordsScroll, settingsScroll;
    private final TextView eyebrow, microphone, disclosure, subtitle, pageTitle;
    private final ImageView logo, stateIcon;
    private final GalleryPhoto placePhoto;
    private final SearchingCompanion companion;
    private final View gallerySpace, introBottomSpace;
    private final TextView photoCredit, demoStatus;
    private final ImageButton back;
    private final Button preferences;
    private final Typeface font;
    private final JSONObject tokens;
    private String phase = "start";
    private ConversationController.State state = ConversationController.State.IDLE;
    private boolean compact, settingsOpen, recordDetail, narration;
    private boolean microphoneOpen, finishingInput, bargeMode, bargeReady;
    private int themeChoice;
    private final TextView locationNotice;
    private boolean liveLocation;
    private boolean parkedFeaturesBlocked;
    void setParkedFeaturesBlocked(boolean blocked) {
        parkedFeaturesBlocked = blocked;
        preferences.setEnabled(!blocked);
        demoStatus.setText(blocked ? "주행 제한" : "정차 시연");
    }
    boolean usesLiveLocation() { return liveLocation; }
    private int voiceRate, voicePitch;
    private boolean adjustableVoiceAvailable;
    final Button voicePreview;
    final Button visibilityReplayButton;
    private final TextView voiceAvailability;
    private final android.widget.SeekBar rateSlider, pitchSlider;
    private Runnable onSettingsClosed;
    float voiceRate() { return voiceRate / 100f; }
    float voicePitch() { return voicePitch / 100f; }
    boolean wantsAdjustedVoice() { return voiceRate != 100 || voicePitch != 100; }
    void setVoiceAvailable(boolean available) {
        adjustableVoiceAvailable = available;
        rateSlider.setEnabled(available);
        pitchSlider.setEnabled(available);
        voiceAvailability.setText(available
                ? "속도나 음높이를 바꾸면 해설·답변 텍스트를 개발 서버로 보내 음성을 만들어요."
                : "이 빌드에서는 Pleos 기본 음성을 사용해요.");
    }
    boolean usesAdjustedVoice() { return adjustableVoiceAvailable && wantsAdjustedVoice(); }
    void setOnSettingsClosed(Runnable action) { onSettingsClosed = action; }
    private JSONArray records = new JSONArray();
    private JSONObject activeRecord;
    private final java.util.Set<String> generatingArtIds = new java.util.HashSet<>();
    private java.util.function.Consumer<JSONObject> tripSync;
    void setTripSync(java.util.function.Consumer<JSONObject> sync) { tripSync = sync; }
    private java.util.function.Consumer<JSONObject> tripExport;
    void setTripExport(java.util.function.Consumer<JSONObject> export) { tripExport = export; }
    private java.util.function.Consumer<JSONObject> artRequest;
    void setArtRequest(java.util.function.Consumer<JSONObject> request) { artRequest = request; }
    void artState(String id, boolean busy, JSONArray rows) {
        if (busy) generatingArtIds.add(id); else generatingArtIds.remove(id);
        records = rows;
        if (recordDetail && activeRecord != null) {
            for (int i = 0; i < rows.length(); i++) if (activeRecord.optString("id").equals(rows.optJSONObject(i).optString("id"))) {
                showRecord(rows.optJSONObject(i)); return;
            }
        } else if (phase.equals("records")) showRecords(rows);
    }
    private Button save;
    private Runnable onBack;
    private final android.content.SharedPreferences uiPreferences;

    MileScreen(Context context) {
        super(context);
        try {
            font = Typeface.createFromAsset(context.getAssets(), "fonts/asta_sans.ttf");
            try (java.io.InputStream in = context.getAssets().open("ui/mile.tokens.json")) {
                tokens = new JSONObject(new java.util.Scanner(in, "UTF-8").useDelimiter("\\A").next());
            }
        } catch (Exception e) { throw new IllegalStateException("MILE design assets unavailable", e); }
        uiPreferences = context.getSharedPreferences("mile_ui", Context.MODE_PRIVATE);
        themeChoice = uiPreferences.getInt("theme", 0);
        voiceRate = Math.max(80, Math.min(120, uiPreferences.getInt("voice_rate", 100)));
        voicePitch = Math.max(85, Math.min(115, uiPreferences.getInt("voice_pitch", 100)));
        setOrientation(VERTICAL);
        header = column(HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        addView(header, new LayoutParams(-1, dp(80)));
        back = new ImageButton(context); back.setImageResource(R.drawable.mile_back);
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE); back.setContentDescription("이전 화면");
        back.setTag("tertiary"); back.setBackgroundColor(Color.TRANSPARENT); header.addView(back, new LayoutParams(dp(80), dp(80)));
        back.setOnClickListener(v -> { if (onBack != null) onBack.run(); });
        logo = new ImageView(context); logo.setImageResource(R.drawable.mile_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setContentDescription("MILE");
        header.addView(logo, new LayoutParams(dp(136), dp(41)));
        pageTitle = text("", 36, "ink", 700); pageTitle.setId(R.id.page_title);
        pageTitle.setMaxLines(1); pageTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(pageTitle, new LayoutParams(-2, -2));
        View gap = new View(context); header.addView(gap, new LayoutParams(0, 1, 1));
        demoStatus = text("정차 시연", 24, "muted", 400);
        header.addView(demoStatus, new LayoutParams(dp(140), -2));
        recordsButton = button("여정", R.id.journey_records, "tertiary");
        header.addView(recordsButton, new LayoutParams(dp(120), dp(80)));
        preferences = button("설정", R.id.screen_settings, "tertiary");
        preferences.setContentDescription("대화와 화면 설정");
        header.addView(preferences, new LayoutParams(dp(120), dp(80)));
        preferences.setOnClickListener(v -> setSettingsOpen(!settingsOpen));
        content = new FrameLayout(context);
        LayoutParams area = new LayoutParams(-1, 0, 1); area.topMargin = dp(32);
        addView(content, area);
        placePhoto = new GalleryPhoto(context);
        content.addView(placePhoto, new FrameLayout.LayoutParams(-1, -1));
        companion = new SearchingCompanion(context);
        content.addView(companion, new FrameLayout.LayoutParams(dp(512), dp(512), Gravity.CENTER_VERTICAL | Gravity.END));
        card = column(VERTICAL); card.setPadding(dp(48), dp(48), dp(48), dp(48)); card.setTag("canvas");
        content.addView(card, new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.START));
        LinearLayout status = column(HORIZONTAL); status.setGravity(Gravity.CENTER_VERTICAL);
        stateIcon = new ImageView(context); stateIcon.setImageResource(R.drawable.mile_book);
        status.addView(stateIcon, new LayoutParams(dp(36), dp(36)));
        output = text("", 28, "muted", 400); output.setMaxLines(2);
        LayoutParams statusText = new LayoutParams(-1, -2); statusText.leftMargin = dp(16);
        status.addView(output, statusText); card.addView(status);
        gallerySpace = new View(context); card.addView(gallerySpace, new LayoutParams(1, 0, 1));
        eyebrow = text("주변 이야기", 28, "muted", 400); card.addView(eyebrow, spaced(24));
        journeyStage = text("창밖 풍경에서\n이야기를 시작해요", 56, "ink", 700);
        journeyStage.setId(R.id.journey_stage); journeyStage.setMaxLines(3); card.addView(journeyStage, spaced(24));
        subtitle = text("지나치는 풍경에, 함께 나눌 이야기가 있어요.", 28, "muted", 400);
        card.addView(subtitle, spaced(24));
        voicePanel = column(VERTICAL);
        transcript = text("", 28, "muted", 400); transcript.setId(R.id.transcript_text); transcript.setMaxLines(2);
        answer = text("", 28, "ink", 400); answer.setId(R.id.answer_text); answer.setMaxLines(3);
        answer.setEllipsize(android.text.TextUtils.TruncateAt.END);
        voicePanel.addView(transcript); voicePanel.addView(answer, spaced(24));
        card.addView(voicePanel, spaced(32));
        quoteChoices = column(VERTICAL); quoteChoices.setId(R.id.quote_choices);
        reviewScroll = new ScrollView(context); reviewScroll.setFillViewport(false); reviewScroll.addView(quoteChoices);
        card.addView(reviewScroll, new LayoutParams(-1, 0, 1));
        recordRows = column(VERTICAL); recordsScroll = new ScrollView(context); recordsScroll.addView(recordRows);
        card.addView(recordsScroll, new LayoutParams(-1, 0, 1));
        View flexible = new View(context); flexible.setTag("flexible"); card.addView(flexible, new LayoutParams(1, 0, 1));
        microphone = text("마이크 꺼짐", 24, "muted", 400); microphone.setId(R.id.microphone_state); card.addView(microphone, spaced(24));
        actions = column(HORIZONTAL); actions.setGravity(Gravity.CENTER_VERTICAL); card.addView(actions, spaced(24));
        discoveryButton = button("주변 이야기 시작", R.id.discover_story, "primary");
        storyButton = button("이야기 듣기", R.id.story_play, "primary");
        ask = button("질문하기", R.id.ask_voice, "primary"); ask.setEnabled(false);
        pause = button("잠시 멈춤", R.id.pause_audio, "primary");
        stop = button("마치기", R.id.stop_audio, "tertiary");
        header.addView(stop, header.getChildCount() - 1, new LayoutParams(dp(160), dp(80)));
        for (Button b : new Button[]{discoveryButton, storyButton, pause, ask}) addAction(b);
        introBottomSpace = new View(context);
        card.addView(introBottomSpace, new LayoutParams(1, 0, 1));
        disclosure = text("", 20, "muted", 400); card.addView(disclosure, spaced(20));
        settings = column(VERTICAL); settings.setPadding(0, dp(32), 0, dp(32)); settings.setTag("canvas");
        settingsScroll = new ScrollView(context); settingsScroll.setFillViewport(false); settingsScroll.addView(settings);
        content.addView(settingsScroll, new FrameLayout.LayoutParams(-1, -1));
        settings.addView(text("대화와 화면", 48, "ink", 500));
        childMode = new Switch(context); childMode.setText("어린이 동반 설명"); styleSwitch(childMode); settings.addView(childMode, spaced(32));
        conversationMode = new Switch(context); conversationMode.setText("답변 뒤 대화 이어가기"); conversationMode.setId(R.id.conversation_mode);
        styleSwitch(conversationMode); settings.addView(conversationMode, spaced(24));
        settings.addView(text("마일이 질문한 뒤 답을 들어요. 자동 듣기는 한 번 시작할 때 최대 두 번이며, 무응답이나 종료 요청에는 멈춥니다.", 26, "muted", 400), spaced(24));
        bargeInMode = new Switch(context); bargeInMode.setText("해설 중 음성 질문 · 시험");
        bargeInMode.setId(R.id.barge_in_mode);
        bargeInMode.setChecked(uiPreferences.getBoolean("narration_voice_questions", false));
        styleSwitch(bargeInMode); settings.addView(bargeInMode, spaced(32));
        settings.addView(text("해설 중 “첨성대는 언제 만들었어?”처럼 질문해 보세요. 이 설정을 켜면 해설을 듣는 동안 마이크를 열고, 해설이 끝나거나 멈추면 닫아요.", 26, "muted", 400), spaced(24));
        Switch locationMode = new Switch(context);
        locationMode.setText("현재 위치로 주변 이야기 찾기");
        styleSwitch(locationMode);
        settings.addView(locationMode, spaced(32));
        settings.addView(text("첨성대 근처에서 장소를 제안해요. 위치는 기기에서만 사용하며, 창밖에서 보이는지는 판단하지 않아요.", 24, "muted", 400), spaced(12));
        settings.addView(text("출력 음성", 30, "ink", 500), spaced(40));
        voiceAvailability = text("출력 음성 설정을 준비하고 있어요.", 24, "muted", 400);
        settings.addView(voiceAvailability, spaced(16));
        rateSlider = addVoiceSlider("말하는 속도", 80, 120, voiceRate, "voice_rate", v -> voiceRate = v);
        pitchSlider = addVoiceSlider("음높이", 85, 115, voicePitch, "voice_pitch", v -> voicePitch = v);
        voicePreview = button("미리 듣기", View.generateViewId(), "secondary");
        settings.addView(voicePreview, spaced(16));
        Button resetVoice = button("기본 음성으로", View.generateViewId(), "tertiary");
        resetVoice.setOnClickListener(v -> { rateSlider.setProgress(20); pitchSlider.setProgress(15); });
        settings.addView(resetVoice, spaced(8));
        settings.addView(text("화면 모드", 30, "ink", 500), spaced(40));
        LinearLayout themeRow = column(HORIZONTAL); settings.addView(themeRow, spaced(24));
        int[] themeIds = {R.id.theme_system, R.id.theme_light, R.id.theme_dark};
        String[] labels = {"차량 설정", "라이트", "다크"};
        for (int i = 0; i < labels.length; i++) {
            final int choice = i; Button b = button(labels[i], themeIds[i], "secondary");
            LayoutParams lp = new LayoutParams(0, dp(80), 1); lp.rightMargin = dp(12); themeRow.addView(b, lp);
            b.setOnClickListener(v -> { themeChoice = choice; uiPreferences.edit().putInt("theme", choice).apply(); applyTheme(); });
        }
        settings.addView(text(BuildConfig.MILE_API_URL.isEmpty()
                ? "선택한 한마디는 이 기기에 저장해요. AI 답변과 그림 생성은 서버 연결이 필요해요."
                : "AI 질문은 인식 문장과 최근 대화를 서버·OpenAI로 전송합니다. 녹음 파일은 전송하지 않습니다. 선택한 한마디를 저장하면 그 말과 장소를 전송해 그림을 자동 생성하고, 한마디와 그림을 이 기기에 저장합니다.", 26, "muted", 400), spaced(40));
        input = new EditText(context); input.setId(R.id.question_input); input.setSingleLine(true);
        input.setHint("개발용 질문 입력"); input.setTextSize(26); input.setTypeface(font);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)});
        send = button("입력한 질문 시험", R.id.question_send, "secondary");
        visibilityReplayButton = button("울산바위 안내 재생 시험", View.generateViewId(), "secondary");
        if (BuildConfig.DEBUG) {
            settings.addView(visibilityReplayButton, spaced(32));
            settings.addView(text("개발 도구 · 정차 시연용", 26, "muted", 500), spaced(40));
            settings.addView(input, spaced(20)); settings.addView(send, spaced(20));
        } else { input.setVisibility(GONE); send.setVisibility(GONE); settings.addView(input); settings.addView(send); }
        locationNotice = text("거리 재생 시연 · 실제 위치 미사용", 20, "muted", 400);
        footer = column(HORIZONTAL); footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(16), 0, 0); addView(footer, new LayoutParams(-1, -2));
        footer.addView(locationNotice, new LayoutParams(0, -2, 1));
        locationMode.setOnCheckedChangeListener((button, checked) -> {
            liveLocation = checked;
            locationNotice.setText(checked ? "현재 위치로 탐색 중"
                    : "거리 재생 시연 · 실제 위치 미사용");
        });
        photoCredit = text("장소 참고 사진 · Dudva / CC0", 20, "muted", 400);
        LayoutParams credit = new LayoutParams(-2, -2); credit.leftMargin = dp(24); footer.addView(photoCredit, credit);
        setSettingsOpen(false); render("start", state, false); applyTheme();
    }

    private android.widget.SeekBar addVoiceSlider(String title, int minimum, int maximum, int initial,
                                                  String key, java.util.function.IntConsumer changed) {
        TextView label = text(title + " " + initial + "%", 26, "ink", 500);
        settings.addView(label, spaced(16));
        android.widget.SeekBar slider = new android.widget.SeekBar(getContext());
        slider.setContentDescription(title);
        slider.setMax(maximum - minimum);
        slider.setProgress(initial - minimum);
        slider.setMinimumHeight(dp(64));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                int value = minimum + progress;
                changed.accept(value);
                label.setText(title + " " + value + "%");
                if (android.os.Build.VERSION.SDK_INT >= 30) bar.setStateDescription(value + "%");
                uiPreferences.edit().putInt(key, value).apply();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        if (android.os.Build.VERSION.SDK_INT >= 30) slider.setStateDescription(initial + "%");
        settings.addView(slider, spaced(4));
        return slider;
    }

    int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int sp(float value) { return Math.round(value * getResources().getDisplayMetrics().scaledDensity); }
    private LinearLayout column(int orientation) { LinearLayout l = new LinearLayout(getContext()); l.setOrientation(orientation); return l; }
    private LayoutParams spaced(int top) { LayoutParams p = new LayoutParams(-1, -2); p.topMargin = dp(top); return p; }
    TextView text(String value, int size, String role, int weight) {
        TextView t = new TextView(getContext()); t.setText(value); t.setTag(role); t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(font, weight, false)); t.setTextSize(size);
        int line = size == 56 ? 72 : size == 48 ? 64 : size == 40 ? 52 : size == 30 ? 38 : size == 28 ? 36 : size == 24 ? 28 : Math.round(size * 1.3f);
        t.setLineHeight(sp(line)); return t;
    }
    private Button button(String label, int id, String role) {
        Button b = new Button(getContext()); b.setId(id); b.setText(label); b.setAllCaps(false); b.setTag(role);
        b.setTypeface(Typeface.create(font, 700, false)); b.setTextSize(30); b.setIncludeFontPadding(false);
        b.setPadding(dp(24), 0, dp(24), 0); b.setMinHeight(dp(80)); b.setMinimumHeight(dp(80)); b.setStateListAnimator(null);
        return b;
    }
    private void styleSwitch(Switch s) { s.setTextSize(28); s.setTypeface(Typeface.create(font, 500, false)); s.setTag("ink"); s.setMinHeight(dp(80)); }
    private void addAction(Button b) { LayoutParams p = new LayoutParams(0, dp(80), 1); p.rightMargin = dp(16); actions.addView(b, p); }
    void setBackAction(Runnable action) { onBack = action; }
    boolean closeOverlay() {
        if (settingsOpen) { setSettingsOpen(false); return true; }
        if (recordDetail && phase.equals("records")) { showRecords(records); return true; }
        return false;
    }
    private void updatePageHeader() {
        String title = settingsOpen ? "설정" : phase.equals("records") ? recordDetail ? "경주 나들이" : "저장한 여정"
                : phase.equals("review") ? "여정에 남기기" : phase.equals("conversation") ? "첨성대 이야기"
                : phase.equals("found") ? "주변 이야기" : "";
        pageTitle.setText(title); pageTitle.setVisibility(title.isEmpty() ? GONE : VISIBLE);
        logo.setVisibility(title.isEmpty() ? VISIBLE : GONE);
    }
    void dismissSettings() { setSettingsOpen(false); }
    private void setSettingsOpen(boolean value) {
        if (settingsOpen && !value && onSettingsClosed != null) onSettingsClosed.run();
        settingsOpen = value; settingsScroll.setVisibility(value ? VISIBLE : GONE); card.setVisibility(value ? GONE : VISIBLE);
        back.setVisibility(value || !phase.equals("start") ? VISIBLE : compact ? GONE : INVISIBLE); preferences.setText(value ? "닫기" : "설정");
        updatePageHeader(); applyTheme();
    }
    private String feedbackMessage = "";

    void render(String next, ConversationController.State nextState, boolean sttReady) {
        if (!phase.equals(next)) feedbackMessage = "";
        phase = next; state = nextState;
        boolean start = phase.equals("start"), search = phase.equals("searching"), found = phase.equals("found");
        boolean talking = phase.equals("conversation"), review = phase.equals("review"), browsing = phase.equals("records");
        boolean busy = state == ConversationController.State.SPEAKING || state == ConversationController.State.GENERATING || state == ConversationController.State.LISTENING;
        back.setVisibility(!start || settingsOpen ? VISIBLE : compact ? GONE : INVISIBLE);
        eyebrow.setText(start || search ? "주변 이야기" : found || talking ? "경주 · 첨성대" : "이번 여정");
        if (start) journeyStage.setText("창밖 풍경에서\n이야기를 시작해요");
        else if (search) journeyStage.setText("주변 이야기를\n찾고 있어요");
        else if (found || talking && narration) journeyStage.setText("별을 읽던\n사람들");
        subtitle.setText(start ? "지나치는 풍경에, 함께 나눌 이야기가 있어요." : search ? "이야기를 만나면 알려드릴게요." : found ? "별을 살피던 사람들의 이야기를 들어볼까요?" : "");
        subtitle.setVisibility(start || search || found ? VISIBLE : GONE);
        ((View)output.getParent()).setVisibility(talking ? VISIBLE : GONE);
        voicePanel.setVisibility(talking && !narration ? VISIBLE : GONE);
        transcript.setVisibility(talking && !narration && transcript.length() > 0 ? VISIBLE : GONE);
        answer.setVisibility(talking && !narration && answer.length() > 0 ? VISIBLE : GONE);
        reviewScroll.setVisibility(review ? VISIBLE : GONE); quoteChoices.setVisibility(review ? VISIBLE : GONE);
        recordsScroll.setVisibility(browsing ? VISIBLE : GONE);
        for (int i = 0; i < card.getChildCount(); i++) if ("flexible".equals(card.getChildAt(i).getTag())) card.getChildAt(i).setVisibility(review || browsing ? GONE : VISIBLE);
        discoveryButton.setVisibility(start || search || review && save == null ? VISIBLE : GONE);
        storyButton.setVisibility(found ? VISIBLE : GONE);
        ask.setVisibility(talking && state != ConversationController.State.LISTENING && state != ConversationController.State.GENERATING ? VISIBLE : GONE);
        ask.setEnabled(sttReady && state != ConversationController.State.LISTENING && state != ConversationController.State.GENERATING);
        ask.setTag(busy ? "secondary" : "primary");
        pause.setVisibility(talking && busy ? VISIBLE : GONE);
        pause.setText(narrationPaused ? "이어듣기" : state == ConversationController.State.SPEAKING ? (narration ? "잠시 멈춤" : "재생 멈춤") : state == ConversationController.State.LISTENING ? "듣기 취소" : "취소");
        stop.setVisibility(talking ? VISIBLE : GONE);
        recordsButton.setVisibility(parkedFeaturesBlocked || talking || browsing ? GONE : VISIBLE);
        if (save != null) save.setVisibility(review ? VISIBLE : GONE);
        microphone.setVisibility(talking ? VISIBLE : GONE);
        if (state != ConversationController.State.LISTENING && !(state == ConversationController.State.SPEAKING && bargeMode)) { microphoneOpen = false; finishingInput = false; bargeMode = false; }
        updateMicrophone();
        stateIcon.setVisibility(state == ConversationController.State.LISTENING ? GONE : VISIBLE);
        disclosure.setText(!feedbackMessage.isEmpty() ? feedbackMessage
                : found ? (BuildConfig.MILE_API_URL.isEmpty() ? "AI 답변은 서버 연결이 필요해요."
                        : "AI 질문은 인식 문장과 최근 대화를 서버로 전송해요.")
                : review ? "직접 선택한 한마디만 이 기기에 남아요." : "");
        disclosure.setVisibility(!feedbackMessage.isEmpty() || found || review ? VISIBLE : GONE);
        actions.setVisibility(browsing ? GONE : VISIBLE);
        childMode.setEnabled(!busy); conversationMode.setEnabled(true);
        card.setTag("canvas");
        if (!browsing) recordDetail = false;
        eyebrow.setVisibility(browsing ? GONE : VISIBLE);
        journeyStage.setVisibility(browsing ? GONE : VISIBLE);
        updatePageHeader();
        requestLayout();
        applyTheme();
    }

    void review(java.util.List<String> candidates, Consumer<String> onSave) {
        quoteChoices.removeAllViews();
        if (save != null) { actions.removeView(save); save = null; }
        TextView notice = text(candidates.isEmpty() ? "아직 저장할 말이 없어요. 이야기를 나눈 뒤 한마디를 남길 수 있어요." : "우리의 대화에서 남기고 싶은 말을 골라보세요.", 28, "muted", 400);
        quoteChoices.addView(notice, spaced(16));
        RadioGroup group = new RadioGroup(getContext()); quoteChoices.addView(group, spaced(24));
        for (String words : candidates) {
            RadioButton choice = new RadioButton(getContext()); choice.setId(View.generateViewId()); choice.setText(words);
            choice.setTextSize(30); choice.setTypeface(Typeface.create(font, 500, false)); choice.setTag("ink");
            choice.setPadding(dp(16), dp(20), dp(16), dp(20)); choice.setMinHeight(dp(96)); group.addView(choice);
        }
        if (!candidates.isEmpty()) {
            save = button("한마디와 그림 저장", R.id.save_quote, "primary"); save.setEnabled(false); addAction(save);
            group.setOnCheckedChangeListener((g, id) -> save.setEnabled(id != -1));
            quoteChoices.addView(text("저장하면 선택한 말과 장소를 서버·OpenAI로 보내 상상 그림을 자동으로 만들어요. 이미지 API 이용료가 발생합니다.", 24, "muted", 400), spaced(20));
            save.setOnClickListener(v -> { RadioButton chosen = group.findViewById(group.getCheckedRadioButtonId()); if (chosen != null) onSave.accept(chosen.getText().toString()); });
        }
        reviewScroll.scrollTo(0, 0); applyTheme();
    }
    void saved() { if (save != null) save.setEnabled(false); }
    void setNarration(boolean value) { narration = value; }
    private boolean narrationPaused;
    void setNarrationPaused(boolean value) { narrationPaused = value; }
    boolean isNarration() { return narration; }
    void saveNarrationVoiceQuestions(boolean enabled) {
        uiPreferences.edit().putBoolean("narration_voice_questions", enabled).apply();
    }
    private boolean bargeCueMissed;
    void voiceEvent(String event) {
        if (event.equals("BARGE_REQUESTED")) { bargeMode = true; bargeReady = false; microphoneOpen = false; }
        else if (event.equals("BARGE_READY")) { bargeMode = true; bargeReady = true; }
        else if (event.equals("BARGE_LISTENING")) { bargeMode = true; microphoneOpen = true; bargeCueMissed = false; }
        else if (event.equals("BARGE_CUE_MISSED")) { bargeCueMissed = true; }
        else if (event.equals("BARGE_OFF")) { bargeMode = false; microphoneOpen = false; bargeCueMissed = false; }
        else if (event.equals("STT_REQUESTED")) { microphoneOpen = false; finishingInput = false; }
        else if (event.equals("STT_LISTENING")) { microphoneOpen = true; finishingInput = false; }
        else if (event.equals("STT_FINISHING") || event.equals("STT_THINKING")) { microphoneOpen = false; finishingInput = true; }
        updateMicrophone();
    }
    private void updateMicrophone() {
        microphone.setText(bargeMode ? microphoneOpen ? "마이크 켜짐 · 질문 확인 중" : bargeCueMissed ? "질문을 알아듣지 못했어요" : bargeReady ? "마이크 준비됨 · 질문해 보세요" : "마이크 준비 중" : state != ConversationController.State.LISTENING ? "마이크 꺼짐"
                : microphoneOpen ? "마이크 켜짐 · 답을 기다리고 있어요"
                : finishingInput ? "마이크 꺼짐 · 인식 문장 확인 중" : "마이크 준비 중");
    }
    void feedback(String message) {
        feedbackMessage = message;
        disclosure.setText(message);
        disclosure.setVisibility(VISIBLE);
    }
    void showRecords(JSONArray rows) {
        records = rows; recordDetail = false; recordRows.removeAllViews(); recordsScroll.setFillViewport(false); journeyStage.setText("저장한 여정"); updatePageHeader();
        if (rows.length() == 0) recordRows.addView(text("아직 남겨둔 한마디가 없어요.\n풍경 속 이야기를 나눈 뒤 여기에 모아보세요.", 30, "muted", 400), spaced(32));
        int columns = compact ? 2 : 3;
        for (int i = 0; i < rows.length(); i += columns) {
            LinearLayout line = column(HORIZONTAL); recordRows.addView(line, spaced(24));
            for (int j = i; j < Math.min(rows.length(), i + columns); j++) {
            JSONObject row = rows.optJSONObject(j); if (row == null) continue;
            LinearLayout item = column(VERTICAL); item.setTag("record_tile"); item.setPadding(dp(24), dp(24), dp(24), dp(24));
            java.io.File art = new TripStore(getContext()).artFile(row);
            android.widget.ImageView cover = new android.widget.ImageView(getContext());
            if (art != null) {
                android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inSampleSize = 4;
                cover.setImageBitmap(android.graphics.BitmapFactory.decodeFile(art.getPath(), options));
            } else cover.setImageResource(R.drawable.cheomseongdae_reference);
            cover.setScaleType(art == null ? android.widget.ImageView.ScaleType.FIT_CENTER : android.widget.ImageView.ScaleType.CENTER_CROP);
            cover.setTag("panel"); cover.setBackground(background(color("surface.panel"), 24)); cover.setClipToOutline(true);
            cover.setContentDescription(art == null ? "첨성대 장소 참고 사진 · Dudva / CC0" : "AI 상상 그림");
            item.addView(cover, new LayoutParams(-1, dp(compact ? 200 : 240)));
            item.addView(text(art == null ? "장소 참고 사진 · Dudva / CC0" : "AI 상상 그림", 20, "muted", 400), spaced(12));
            TextView words = text(row.optString("quote"), 30, "ink", 500);
            words.setMaxLines(2); words.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.addView(words, spaced(20));
            item.addView(text(new java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.KOREA).format(new java.util.Date(row.optLong("createdAt"))) + " · " + row.optString("place"), 22, "muted", 400), spaced(24));
            item.setFocusable(true); item.setClickable(true); item.setContentDescription("저장한 한마디 열기: " + row.optString("quote"));
            item.setOnClickListener(v -> showRecord(row));
            LayoutParams cell = new LayoutParams(0, -2, 1); if (j > i) cell.leftMargin = dp(24); line.addView(item, cell);
            }
            int filled = Math.min(columns, rows.length() - i);
            for (int j = filled; j < columns; j++) {
                LayoutParams empty = new LayoutParams(0, 1, 1); empty.leftMargin = dp(24); line.addView(new View(getContext()), empty);
            }
        }
        applyTheme();
    }
    private LinearLayout detailPanel(String heading) {
        LinearLayout panel = column(VERTICAL); panel.setTag("panel"); panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setContentDescription(heading);
        return panel;
    }
    private ImageView detailImage() {
        ImageView image = new ImageView(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(MeasureSpec.getSize(widthSpec) * 2f / 3f), MeasureSpec.EXACTLY));
            }
        };
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(background(galleryCanvas(), 24)); image.setClipToOutline(true);
        return image;
    }
    private void showRecord(JSONObject row) {
        activeRecord = row; recordDetail = true; recordRows.removeAllViews(); recordsScroll.setFillViewport(false); journeyStage.setText("경주 나들이"); updatePageHeader();
        String date = new java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(new java.util.Date(row.optLong("createdAt")));
        recordRows.addView(text(date + " · " + row.optString("place") + "에서 나눈 이야기", 26, "muted", 400), spaced(24));
        LinearLayout body = column(compact ? VERTICAL : HORIZONTAL);
        LayoutParams bodySize = spaced(16);
        bodySize.topMargin = dp(24); recordRows.addView(body, bodySize);
        LinearLayout art = detailPanel("AI 상상 그림"), words = detailPanel("남겨둔 한마디");
        body.addView(art, compact ? new LayoutParams(-1, -2) : new LayoutParams(0, -1, 1.7f));
        if (compact) {
            LayoutParams wordSize = new LayoutParams(-1, -2); wordSize.topMargin = dp(24); body.addView(words, wordSize);
        } else {
            LayoutParams wordSize = new LayoutParams(0, -1, 1); wordSize.leftMargin = dp(24); body.addView(words, wordSize);
        }
        java.io.File file = new TripStore(getContext()).artFile(row);
        boolean busy = generatingArtIds.contains(row.optString("id"));
        if (file != null) {
            ImageView image = detailImage();
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inSampleSize = 2;
            image.setImageBitmap(android.graphics.BitmapFactory.decodeFile(file.getPath(), options));
            image.setContentDescription("AI 상상 그림: " + row.optString("quote"));
            art.addView(image, new LayoutParams(-1, -2));
        } else {
            FrameLayout placeholder = new FrameLayout(getContext()) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.round(MeasureSpec.getSize(widthSpec) * 2f / 3f), MeasureSpec.EXACTLY));
                }
            };
            placeholder.setContentDescription("상상 그림 영역");
            TextView waiting = text(busy ? "상상 그림을\n만들고 있어요" : "한마디를 그림으로 남겨요", 26, "muted", 400);
            waiting.setGravity(Gravity.CENTER); placeholder.addView(waiting, new FrameLayout.LayoutParams(-1, -1));
            if (artRequest == null) waiting.setText("그림 생성은 서버 연결이 필요해요.");
            else {
                Button generate = button(busy ? "그림을 만들고 있어요…" : "이 말로 그림 만들기", View.generateViewId(), "primary");
                generate.setEnabled(!busy);
                generate.setOnClickListener(v -> artRequest.accept(row));
                FrameLayout.LayoutParams retry = new FrameLayout.LayoutParams(-1, dp(80), Gravity.BOTTOM);
                retry.setMargins(dp(16), 0, dp(16), dp(16)); placeholder.addView(generate, retry);
                waiting.setPadding(dp(16), dp(16), dp(16), dp(96));
            }
            art.addView(placeholder, new LayoutParams(-1, -2));
        }
        art.addView(text("AI 상상 그림", 30, "ink", 500), spaced(24));
        art.addView(text("실제 사진이 아닌, 우리가 나눈 상상이에요.", 22, "muted", 400), spaced(24));
        ImageView book = new ImageView(getContext()); book.setImageResource(R.drawable.mile_book); book.setTag("ink_icon");
        words.addView(book, new LayoutParams(dp(36), dp(36)));
        words.addView(text("남겨둔 한마디", 26, "muted", 400), spaced(24));
        words.addView(text("“" + row.optString("quote") + "”", compact ? 32 : 38, "ink", 500), spaced(32));
        View space = new View(getContext()); words.addView(space, compact ? new LayoutParams(1, dp(32)) : new LayoutParams(1, 0, 1));
        if (tripSync != null) {
            Button sync = button("모바일 계정에 보내기", View.generateViewId(), "secondary");
            sync.setOnClickListener(v -> tripSync.accept(row));
            words.addView(sync, spaced(24));
        }
        if (tripExport != null) {
            Button export = button("모바일로 여정 옮기기", View.generateViewId(), "secondary");
            export.setOnClickListener(v -> tripExport.accept(row));
            words.addView(export, spaced(24));
        }
        applyTheme();
    }
    private boolean dark() { return themeChoice == 2 || themeChoice == 0 && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    int color(String semantic) {
        try {
            String ref = tokens.getJSONObject("mileSemanticProposed").getJSONObject(dark() ? "dark" : "light").getString(semantic);
            return Color.parseColor(tokens.getJSONObject("primitive").getJSONObject("neutral").getString(ref.substring(ref.indexOf('.') + 1)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private GradientDrawable background(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    void applyTheme() {
        setBackgroundColor(galleryCanvas()); tint(this);
        placePhoto.theme(galleryCanvas(), dark());
        card.setBackgroundColor(Color.TRANSPARENT);
        logo.setImageTintList(ColorStateList.valueOf(color("text.primary")));
        back.setImageTintList(ColorStateList.valueOf(color("text.primary")));
        stateIcon.setImageTintList(ColorStateList.valueOf(color("text.primary")));
        input.setTextColor(color("text.primary")); input.setHintTextColor(color("text.secondary"));
    }
    private void tint(View v) {
        String role = v.getTag() instanceof String ? (String)v.getTag() : "";
        int ink = color("text.primary"), panel = color("surface.panel"), muted = color("text.secondary");
        if (role.equals("panel") || role.equals("soft") || role.equals("canvas")) v.setBackground(background(role.equals("panel") ? panel : role.equals("soft") ? color("brand.background") : galleryCanvas(), 32));
        if (role.equals("record_tile")) {
            GradientDrawable tile = background(panel, 32);
            tile.setStroke(dp(1), Color.argb(dark() ? 80 : 40, Color.red(ink), Color.green(ink), Color.blue(ink)));
            v.setBackground(tile);
        }
        if (v instanceof Button && !(v instanceof Switch) && !(v instanceof RadioButton)) {
            Button b = (Button)v; int fill = role.equals("primary") ? color("action.fill") : role.equals("secondary") ? panel : Color.TRANSPARENT;
            StateListDrawable backgrounds = new StateListDrawable();
            backgrounds.addState(new int[]{-android.R.attr.state_enabled}, background(color("brand.background"), 24));
            GradientDrawable normal = background(fill, 24);
            int selectedTheme = themeChoice == 0 ? R.id.theme_system : themeChoice == 1 ? R.id.theme_light : R.id.theme_dark;
            if (v.getId() == selectedTheme) normal.setStroke(dp(2), ink);
            backgrounds.addState(new int[]{}, normal); b.setBackground(backgrounds); b.setBackgroundTintList(null);
            b.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{muted,role.equals("primary") ? color("action.label") : ink}));
        } else if (v instanceof TextView) ((TextView)v).setTextColor(role.equals("muted") ? muted : ink);
        if (v instanceof RadioButton) ((RadioButton)v).setButtonTintList(ColorStateList.valueOf(ink));
        if (v instanceof Switch) { ((Switch)v).setThumbTintList(ColorStateList.valueOf(ink)); ((Switch)v).setTrackTintList(ColorStateList.valueOf(color("brand.background"))); }
        if (v instanceof ImageView && role.equals("ink_icon")) ((ImageView)v).setImageTintList(ColorStateList.valueOf(ink));
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)v).getChildCount(); i++) tint(((ViewGroup)v).getChildAt(i));
    }
    private boolean gallery() {
        return !parkedFeaturesBlocked && !compact && !settingsOpen && (phase.equals("found") || phase.equals("conversation") && narration);
    }
    private boolean editorial() {
        return !compact && !settingsOpen && (phase.equals("start") || phase.equals("searching") || gallery());
    }
    private int galleryCanvas() { return dark() ? color("surface.canvas") : color("brand.background"); }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (compact && companion.getVisibility() == VISIBLE) {
            int start = card.getTop() + subtitle.getBottom() + dp(24);
            int end = card.getTop() + actions.getTop() - dp(24);
            int size = Math.max(0, Math.min(dp(288), end - start));
            int x = (card.getWidth() - size) / 2 + card.getLeft();
            int y = start + Math.max(0, (end - start - size) / 2);
            companion.layout(x, y, x + size, y + size);
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updatePageHeader();
        int w = MeasureSpec.getSize(widthMeasureSpec);
        boolean changed = compact != (w < dp(1100));
        compact = w < dp(1100); int padding = compact ? 32 : 48;
        boolean gallery = gallery(), editorial = editorial();
        boolean intro = phase.equals("start") || phase.equals("searching");
        boolean inlineHero = !compact && (intro || phase.equals("found"));
        int actionIndex = card.indexOfChild(inlineHero ? subtitle : microphone) + 1;
        if (card.indexOfChild(actions) != actionIndex) {
            LayoutParams actionLayout = (LayoutParams)actions.getLayoutParams();
            card.removeView(actions);
            card.addView(actions, card.indexOfChild(inlineHero ? subtitle : microphone) + 1, actionLayout);
        }
        introBottomSpace.setVisibility(inlineHero ? VISIBLE : GONE);
        introBottomSpace.setMinimumHeight(0);
        ((LayoutParams)gallerySpace.getLayoutParams()).weight = inlineHero ? .7f : 1f;
        ((LayoutParams)introBottomSpace.getLayoutParams()).weight = inlineHero ? 1.3f : 1f;
        setPadding(dp(padding), dp(32), dp(padding), dp(24));
        int gutter = compact ? 0 : dp(80);
        pageTitle.setTextSize(compact ? 30 : 36);
        pageTitle.setMaxWidth(Math.max(dp(120), w - dp(compact ? 360 : 700)));
        header.setPadding(0, 0, gutter, 0);
        back.setVisibility(settingsOpen || !phase.equals("start") ? VISIBLE : compact ? GONE : INVISIBLE);
        card.setPadding(gutter, dp(32), gutter, dp(32));
        footer.setPadding(gutter, dp(16), gutter, 0);
        placePhoto.setVisibility(gallery ? VISIBLE : GONE);
        photoCredit.setVisibility(gallery ? VISIBLE : GONE);
        demoStatus.setVisibility(!compact && !settingsOpen ? VISIBLE : GONE);
        gallerySpace.setVisibility(editorial ? VISIBLE : GONE);
        companion.setPresentation((phase.equals("start") || phase.equals("searching")) && !settingsOpen, phase.equals("searching") && !settingsOpen);
        FrameLayout.LayoutParams character = (FrameLayout.LayoutParams)companion.getLayoutParams();
        int characterSize = dp(compact ? 288 : 512);
        int characterGravity = (compact ? Gravity.TOP : Gravity.CENTER_VERTICAL) | Gravity.END;
        if (character.width != characterSize || character.gravity != characterGravity || character.rightMargin != gutter) {
            character.width = character.height = characterSize; character.gravity = characterGravity; character.rightMargin = gutter;
            companion.setLayoutParams(character);
        }
        for (int i = 0; i < card.getChildCount(); i++) if ("flexible".equals(card.getChildAt(i).getTag()))
            card.getChildAt(i).setVisibility(editorial || phase.equals("review") || phase.equals("records") ? GONE : VISIBLE);
        int available = w - dp(padding * 2);
        int gridWidth = available - gutter * 2;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)card.getLayoutParams();
        int cardWidth = -1;
        if (lp.width != cardWidth) { lp.width = cardWidth; card.setLayoutParams(lp); }
        boolean compactIntro = compact && (phase.equals("start") || phase.equals("searching"));
        for (TextView label : new TextView[]{eyebrow, journeyStage, subtitle}) {
            label.setGravity(compactIntro ? Gravity.CENTER : Gravity.START);
            LayoutParams p = (LayoutParams)label.getLayoutParams();
            int width = editorial ? Math.round(gridWidth * .46f) : -1;
            if (p.width != width) { p.width = width; label.setLayoutParams(p); }
        }
        ((LayoutParams)journeyStage.getLayoutParams()).topMargin = dp(inlineHero ? 40 : 24);
        ((LayoutParams)subtitle.getLayoutParams()).topMargin = dp(inlineHero ? 56 : 24);
        LayoutParams controls = (LayoutParams)actions.getLayoutParams();
        controls.topMargin = dp(inlineHero ? 112 : 24);
        int visibleActions = 0;
        for (int i = 0; i < actions.getChildCount(); i++) if (actions.getChildAt(i).getVisibility() == VISIBLE) visibleActions++;
        int controlsWidth = !compact ? Math.min(dp(inlineHero ? 440 : visibleActions > 1 ? 640 : 340), Math.round(gridWidth * .8f)) : -1;
        int remainingActions = visibleActions;
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i); if (child.getVisibility() != VISIBLE) continue;
            LayoutParams actionSize = (LayoutParams)child.getLayoutParams();
            int space = --remainingActions > 0 ? dp(16) : 0;
            if (actionSize.rightMargin != space) { actionSize.rightMargin = space; child.setLayoutParams(actionSize); }
        }
        if (controls.width != controlsWidth) { controls.width = controlsWidth; actions.setLayoutParams(controls); }
        controls.gravity = compact || inlineHero ? Gravity.START : Gravity.END;
        FrameLayout.LayoutParams settingsSize = (FrameLayout.LayoutParams)settingsScroll.getLayoutParams();
        int settingsWidth = compact ? -1 : Math.min(gridWidth, dp(1100));
        int settingsGravity = compact ? Gravity.START : Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        if (settingsSize.width != settingsWidth || settingsSize.gravity != settingsGravity) {
            settingsSize.width = settingsWidth; settingsSize.gravity = settingsGravity; settingsScroll.setLayoutParams(settingsSize);
        }
        if (changed) {
            journeyStage.setTextSize(compact ? 48 : 56); journeyStage.setLineHeight(sp(compact ? 64 : 72));
            logo.getLayoutParams().width = dp(compact ? 120 : 136);
            recordsButton.getLayoutParams().width = dp(compact ? 100 : 120);
            preferences.getLayoutParams().width = dp(compact ? 100 : 120);
            if (phase.equals("records")) post(() -> {
                if (recordDetail && activeRecord != null) showRecord(activeRecord); else showRecords(records);
            });
        }
        journeyStage.setTextSize(inlineHero ? 64 : compact ? 48 : 56);
        journeyStage.setLineHeight(sp(inlineHero ? 88 : compact ? 64 : 72));
        eyebrow.setTextSize(inlineHero ? 32 : 28); eyebrow.setLineHeight(sp(inlineHero ? 44 : 36));
        subtitle.setTextSize(inlineHero ? 32 : 28); subtitle.setLineHeight(sp(inlineHero ? 44 : 36));
        for (Button button : new Button[]{discoveryButton, storyButton}) {
            button.setTextSize(inlineHero ? 34 : 30);
            button.getLayoutParams().height = dp(inlineHero ? 104 : 80);
        }
        if (changed) applyTheme();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    }
}
