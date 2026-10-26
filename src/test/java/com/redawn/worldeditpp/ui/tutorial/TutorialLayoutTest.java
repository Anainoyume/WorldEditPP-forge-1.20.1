package com.redawn.common.ui.tutorial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialLayoutTest {
    @Test void dimensionsRespectAutoPercentPixelsAndContainerWidth() {
        var auto = TutorialSize.AUTO;
        assertEquals(new TutorialSize.Dimensions(270, 135), TutorialSize.image(auto, auto, 300, 200, 800, 400));
        assertEquals(TutorialSize.parse("50"), TutorialSize.parse("50px"));
        assertEquals(new TutorialSize.Dimensions(100, 50), TutorialSize.image(TutorialSize.parse("33.3%"), auto, 300, 200, 800, 400));
        assertEquals(new TutorialSize.Dimensions(270, 68), TutorialSize.image(TutorialSize.parse("600"), TutorialSize.parse("150px"), 300, 200, 800, 400));
        assertEquals(new TutorialSize.Dimensions(270, 100), TutorialSize.image(auto, TutorialSize.parse("50%"), 300, 200, 800, 400));
        assertEquals(new TutorialSize.Dimensions(64, 64), TutorialSize.image(TutorialSize.parse("64px"), auto, 300, 200, 64, 64));
        assertEquals(new TutorialSize.Dimensions(270, 135), TutorialSize.image(TutorialSize.parse("100%"), auto, 300, 200, 800, 400));
        assertThrows(IllegalArgumentException.class, () -> TutorialSize.parse("NaN"));
        assertThrows(IllegalArgumentException.class, () -> TutorialSize.parse("-5px"));
    }
    @Test void scrollingClampsAndDraggingReachesBothEndsWithoutNaN() {
        var scroll = new TutorialScroll();
        scroll.layout(100, 1000);
        scroll.drag(100, scroll.thumb() / 2.0);
        assertEquals(900, scroll.offset());
        scroll.drag(-50, 0);
        assertEquals(0, scroll.offset());
        scroll.wheel(-2);
        assertEquals(56, scroll.offset());
        scroll.layout(100, 20);
        scroll.drag(50, 5);
        assertEquals(0, scroll.offset());
        assertEquals(100, scroll.thumb());
    }
}
