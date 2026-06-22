package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public final class SmartFeedEventDto {

    @SerializedName("eventId")
    private final String eventId;

    @SerializedName("sessionId")
    private final String sessionId;

    @SerializedName("eventType")
    private final String eventType;

    @SerializedName("articleId")
    private final Long articleId;

    @SerializedName("categoryId")
    private final Long categoryId;

    private final String timestamp;
    private final Map<String, Object> device;
    private final Map<String, Object> metadata;

    public SmartFeedEventDto(
            String eventId,
            String sessionId,
            String eventType,
            Long articleId,
            Long categoryId,
            String timestamp,
            Map<String, Object> device,
            Map<String, Object> metadata
    ) {
        this.eventId = eventId;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.articleId = articleId;
        this.categoryId = categoryId;
        this.timestamp = timestamp;
        this.device = device;
        this.metadata = metadata;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getArticleId() {
        return articleId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getDevice() {
        return device;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
