package com.smartfeed.util;

import android.os.Build;

import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.SmartFeedEventDto;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public final class EventFactory {

    public static final String VIEW_ARTICLE = "view_article";
    public static final String OPEN_RECOMMENDATIONS = "open_recommendations";
    public static final String OPEN_RECOMMENDED_ARTICLE = "open_recommended_article";

    private EventFactory() {
    }

    public static SmartFeedEventDto articleOpened(
            SessionManager sessionManager,
            ArticleDto article,
            boolean openedFromRecommendations
    ) {
        Map<String, Object> device = new HashMap<>();
        device.put("platform", "android");
        device.put("model", Build.MODEL);
        device.put("sdk_int", Build.VERSION.SDK_INT);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(
                "source",
                openedFromRecommendations ? "recommendations" : "feed"
        );
        metadata.put("screen", "article_detail");

        return new SmartFeedEventDto(
                UUID.randomUUID().toString(),
                sessionManager.getOrCreateSessionId(),
                openedFromRecommendations ? OPEN_RECOMMENDED_ARTICLE : VIEW_ARTICLE,
                article.getId(),
                article.getCategoryId(),
                utcTimestamp(),
                device,
                metadata
        );
    }

    public static SmartFeedEventDto recommendationsOpened(
            SessionManager sessionManager
    ) {
        Map<String, Object> device = new HashMap<>();
        device.put("platform", "android");
        device.put("model", Build.MODEL);
        device.put("sdk_int", Build.VERSION.SDK_INT);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("screen", "recommendations");

        return new SmartFeedEventDto(
                UUID.randomUUID().toString(),
                sessionManager.getOrCreateSessionId(),
                OPEN_RECOMMENDATIONS,
                null,
                null,
                utcTimestamp(),
                device,
                metadata
        );
    }

    private static String utcTimestamp() {
        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
        );
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(System.currentTimeMillis());
    }
}
