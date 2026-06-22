package com.smartfeed.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateFormatter {

    private DateFormatter() {
    }

    public static String formatArticleDate(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.length() < 10) {
            return "";
        }

        String datePart = isoDateTime.substring(0, 10);
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        parser.setLenient(false);
        try {
            Date date = parser.parse(datePart);
            if (date == null) {
                return datePart;
            }
            return new SimpleDateFormat(
                    "dd MMM yyyy",
                    Locale.forLanguageTag("ru-RU")
            ).format(date);
        } catch (ParseException ignored) {
            return datePart;
        }
    }
}
