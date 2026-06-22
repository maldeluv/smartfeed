package com.smartfeed.ui.recommendations;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public final class RecommendationTextFormatterTest {

    @Test
    public void scoreInUnitRangeIsShownAsPercentage() {
        assertEquals(
                "87%",
                RecommendationTextFormatter.formatScoreValue(0.87, Locale.US)
        );
    }

    @Test
    public void unboundedScoreIsShownAsRoundedNumber() {
        assertEquals(
                "2.35",
                RecommendationTextFormatter.formatScoreValue(2.345, Locale.US)
        );
    }

    @Test
    public void technicalFallbackReasonIsHumanized() {
        assertEquals(
                "Test seed",
                RecommendationTextFormatter.humanizeUnknownReason("test_seed")
        );
    }
}
