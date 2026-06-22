package com.smartfeed.ui.analytics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public final class AnalyticsTextFormatterTest {

    @Test
    public void ctrIsFormattedAsPercentage() {
        assertEquals("40%", AnalyticsTextFormatter.formatCtr(0.4, Locale.US));
    }

    @Test
    public void ctrProgressIsClampedToIndicatorRange() {
        assertEquals(0, AnalyticsTextFormatter.ctrProgress(-0.2));
        assertEquals(40, AnalyticsTextFormatter.ctrProgress(0.4));
        assertEquals(100, AnalyticsTextFormatter.ctrProgress(1.4));
    }
}
