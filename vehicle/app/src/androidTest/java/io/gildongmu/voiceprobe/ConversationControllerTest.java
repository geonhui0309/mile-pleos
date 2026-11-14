package io.gildongmu.voiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationControllerTest {
    @Test public void lateResultsCannotAdvanceCancelledOrNewWork() {
        ConversationController c = new ConversationController();
        c.setConversationEnabled(true);
        c.manualTurn();
        int oldAnswer = c.beginAnswer();
        c.cancel();
        assertFalse(c.answerReady(oldAnswer, true));
        int current = c.beginAnswer();
        assertFalse(c.answerReady(oldAnswer, true));
        assertTrue(c.answerReady(current, true));
        assertFalse(c.speechDone(oldAnswer));
        assertEquals(ConversationController.State.SPEAKING, c.state());
        assertTrue(c.speechDone(current));
        assertFalse(c.speechDone(current));
    }

    @Test public void replyListeningRequiresOptInQuestionAndBudget() {
        ConversationController c = new ConversationController();
        c.manualTurn();
        int request = c.beginAnswer();
        c.answerReady(request, true);
        assertFalse(c.speechDone(request));
        c.setConversationEnabled(true);
        for (int i = 0; i < 3; i++) {
            request = c.beginAnswer();
            c.answerReady(request, true);
            assertEquals(i < 2, c.speechDone(request));
        }
        c.manualTurn();
        request = c.beginAnswer();
        c.answerReady(request, false);
        assertFalse(c.speechDone(request));
        request = c.beginAnswer();
        c.answerReady(request, true);
        c.setConversationEnabled(false);
        assertFalse(c.speechDone(request));
    }

    @Test public void interruptionAndCloseBlockOldWorkWithoutAutoRestart() {
        ConversationController c = new ConversationController();
        int listening = c.beginListening();
        c.interrupt();
        assertFalse(c.isCurrent(listening));
        assertEquals(-1, c.beginListening());
        c.cancel();
        assertEquals(ConversationController.State.INTERRUPTED, c.state());
        c.resumeAvailable();
        assertEquals(ConversationController.State.IDLE, c.state());
        int next = c.beginNarration();
        assertFalse(c.speechDone(listening));
        c.close();
        c.resumeAvailable();
        assertFalse(c.speechDone(next));
        assertEquals(-1, c.beginAnswer());
        assertEquals(ConversationController.State.CLOSED, c.state());
    }
}
