package com.smartfeed.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(
        tableName = "pending_events",
        indices = {
                @Index("event_type"),
                @Index("created_at")
        }
)
public final class PendingEventEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "event_id")
    private final String eventId;
    @ColumnInfo(name = "session_id")
    private final String sessionId;
    @ColumnInfo(name = "event_type")
    private final String eventType;
    @ColumnInfo(name = "article_id")
    private final Long articleId;
    @ColumnInfo(name = "category_id")
    private final Long categoryId;
    private final String timestamp;
    @ColumnInfo(name = "device_json")
    private final String deviceJson;
    @ColumnInfo(name = "metadata_json")
    private final String metadataJson;
    @ColumnInfo(name = "retry_count")
    private final int retryCount;
    @ColumnInfo(name = "last_error")
    private final String lastError;
    @ColumnInfo(name = "created_at")
    private final long createdAt;

    public PendingEventEntity(
            @NonNull String eventId,
            String sessionId,
            String eventType,
            Long articleId,
            Long categoryId,
            String timestamp,
            String deviceJson,
            String metadataJson,
            int retryCount,
            String lastError,
            long createdAt
    ) {
        this.eventId = eventId;
        this.sessionId = sessionId;
        this.eventType = eventType;
        this.articleId = articleId;
        this.categoryId = categoryId;
        this.timestamp = timestamp;
        this.deviceJson = deviceJson;
        this.metadataJson = metadataJson;
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.createdAt = createdAt;
    }

    @NonNull
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

    public String getDeviceJson() {
        return deviceJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
