package io.gildongmu.voiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class TurnInputTest {
    @Test public void joinsHesitationWithoutDelayingCompleteAnswers() {
        for (int pause : new int[]{1000, 2000, 2900}) {
            TurnInput t = new TurnInput(0);
            assertEquals(TurnInput.Action.WAIT, t.accept("음… 그게…", 500));
            assertEquals(TurnInput.Action.ANSWER, t.accept("별이 사는 집 같아", 500 + pause));
            assertTrue(t.answer().endsWith("별이 사는 집 같아"));
        }
        TurnInput t = new TurnInput(0);
        assertEquals(TurnInput.Action.ANSWER, t.accept("음, 농사 시기를 알려고!", 300));
    }
    @Test public void silenceSpeechAndContinuationHaveSeparateBoundaries() {
        assertTrue(new TurnInput(0).expired(12000));
        TurnInput t = new TurnInput(0);
        t.partial("별", 11000);
        assertFalse(t.expired(12000));
        assertTrue(t.expired(30000));
        t = new TurnInput(0);
        t.accept("음", 1000);
        assertTrue(t.expired(4000));
        assertEquals(TurnInput.Action.EMPTY, t.accept("별", 4000));
        t = new TurnInput(0);
        t.accept("음", 1000);
        t.partial("별이", 3500);
        assertFalse(t.expired(4500));
    }
    @Test public void stopIsAnExactIntentNotSubstring() {
        assertTrue(TurnInput.isStop("그만 해줘!"));
        assertEquals(TurnInput.Action.STOP, new TurnInput(0).accept("여기까지", 1000));
        assertFalse(TurnInput.isStop("그만큼 중요한가?"));
        assertFalse(TurnInput.isStop("멈춰 있는 별도 있어?"));
    }
}
