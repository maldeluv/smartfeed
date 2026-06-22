package com.smartfeed.ui.recommendations;

import java.text.NumberFormat;
import java.util.Locale;

public final class RecommendationTextFormatter {

    private RecommendationTextFormatter() {
    }

    public static String formatScoreValue(double score, Locale locale) {
        if (!Double.isFinite(score)) {
            return "—";
        }
        if (score >= 0.0 && score <= 1.0) {
            NumberFormat percentFormat = NumberFormat.getPercentInstance(locale);
            percentFormat.setMaximumFractionDigits(score < 0.1 ? 1 : 0);
            return percentFormat.format(score);
        }
        NumberFormat numberFormat = NumberFormat.getNumberInstance(locale);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(score);
    }

    public static String humanizeUnknownReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return "";
        }
        String readable = reason.trim().replace('_', ' ').replace('-', ' ');
        if (readable.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }
}
