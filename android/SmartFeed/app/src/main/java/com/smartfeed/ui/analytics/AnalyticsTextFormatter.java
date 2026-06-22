package com.smartfeed.ui.analytics;

import java.text.NumberFormat;
import java.util.Locale;

public final class AnalyticsTextFormatter {

    private AnalyticsTextFormatter() {
    }

    public static String formatCtr(double ctr, Locale locale) {
        if (!Double.isFinite(ctr) || ctr < 0.0) {
            return "—";
        }
        NumberFormat formatter = NumberFormat.getPercentInstance(locale);
        formatter.setMaximumFractionDigits(ctr < 0.1 ? 1 : 0);
        return formatter.format(ctr);
    }

    public static int ctrProgress(double ctr) {
        if (!Double.isFinite(ctr) || ctr <= 0.0) {
            return 0;
        }
        return (int) Math.round(Math.min(1.0, ctr) * 100.0);
    }
}
