package io.gildongmu.voiceprobe;

/** Main-thread policy; contains no screen, SDK, network or audio resources. */
final class ConversationController {
    enum State { IDLE, LISTENING, GENERATING, SPEAKING, INTERRUPTED, CLOSED }

    private State state = State.IDLE;
    private int generation;
    private boolean enabled;
    private int remaining;
    private boolean expectsReply;

    State state() { return state; }
    int generation() { return generation; }
    boolean isCurrent(int request) { return state != State.CLOSED && request == generation; }
    boolean isCurrent(int request, State expected) { return isCurrent(request) && state == expected; }
    boolean canStart() { return state != State.CLOSED && state != State.INTERRUPTED; }
    void setConversationEnabled(boolean value) { enabled = value; }
    void manualTurn() { remaining = 2; }
    boolean mayAskFollowup() { return enabled && remaining > 0; }

    private int begin(State next) {
        if (!canStart()) return -1;
        cancel();
        state = next;
        return generation;
    }

    int beginListening() { return begin(State.LISTENING); }
    int beginAnswer() { return begin(State.GENERATING); }
    int beginNarration() { return begin(State.SPEAKING); }

    boolean answerReady(int request, boolean hasQuestion) {
        if (!isCurrent(request, State.GENERATING)) return false;
        state = State.SPEAKING;
        expectsReply = hasQuestion && mayAskFollowup();
        if (expectsReply) remaining--;
        return true;
    }

    boolean speechDone(int request) {
        if (!isCurrent(request, State.SPEAKING)) return false;
        boolean listen = expectsReply && enabled;
        expectsReply = false;
        state = State.IDLE;
        return listen;
    }

    void cancel() {
        generation++;
        expectsReply = false;
        if (state != State.CLOSED && state != State.INTERRUPTED) state = State.IDLE;
    }

    void interrupt() { cancel(); if (state != State.CLOSED) state = State.INTERRUPTED; }
    void resumeAvailable() { if (state == State.INTERRUPTED) state = State.IDLE; }
    void close() { cancel(); state = State.CLOSED; }
}
