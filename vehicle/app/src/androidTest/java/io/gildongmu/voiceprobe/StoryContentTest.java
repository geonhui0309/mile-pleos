package io.gildongmu.voiceprobe;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StoryContentTest {
    @Test public void scriptsPreserveUncertaintyAndVisibilityLimits() throws Exception {
        StoryContent c = new StoryContent(InstrumentationRegistry.getInstrumentation().getTargetContext());
        String adult = c.story();
        c.childMode = true;
        String child = c.story();
        assertNotEquals(adult, child);
        assertTrue(adult.contains("추정"));
        assertTrue(child.contains("지금 차에서 보이는지는 아직 확인하지 않았어요"));
        assertTrue(c.sources().contains("heritage.go.kr"));
    }
}
