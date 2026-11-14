package io.gildongmu.voiceprobe;

/** Bounded, conservative fragment joining. Times are monotonic milliseconds. */
final class TurnInput {
    enum Action { WAIT, ANSWER, STOP, EMPTY }
    private final long started;
    private long firstText = -1;
    private long fragmentAt = -1;
    private String fragment = "";
    private String answer = "";

    TurnInput(long now) { started = now; }
    void partial(String text, long now) {
        if (text != null && !text.trim().isEmpty()) {
            if (firstText < 0) firstText = now;
            if (!fragment.isEmpty()) fragmentAt = -1;
        }
    }
    long deadline() {
        long limit = Math.min(started + 30000, firstText < 0 ? started + 12000 : firstText + 20000);
        return fragmentAt < 0 ? limit : Math.min(limit, fragmentAt + 3000);
    }
    boolean expired(long now) { return now >= deadline(); }
    static boolean isStop(String text) {
        return text != null && text.replaceAll("[\\s.!?。]", "").matches("그만|그만해|그만해줘|그만해주세요|여기까지|멈춰|멈춰줘|종료|종료해줘");
    }
    Action accept(String text, long now) {
        if (isStop(text)) return Action.STOP;
        if (expired(now)) return Action.EMPTY;
        if (text == null || text.trim().isEmpty()) return Action.EMPTY;
        partial(text, now);
        String clean = text.trim();
        // shortcut: known hesitation fragments only; upgrade after Korean speech-corpus evaluation.
        if (clean.replaceAll("[\\s.,…!?]", "").matches("(?:음+|어+|그게|그러니까|저기|잠깐|생각중이야)+")) {
            fragment = (fragment + " " + clean).trim();
            if (fragment.length() > 200) return Action.EMPTY;
            fragmentAt = now;
            return Action.WAIT;
        }
        answer = (fragment + " " + clean).trim();
        return answer.length() <= 1000 ? Action.ANSWER : Action.EMPTY;
    }
    String answer() { return answer; }
}
