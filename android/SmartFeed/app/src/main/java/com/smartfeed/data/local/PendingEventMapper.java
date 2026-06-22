package com.smartfeed.data.local;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.model.SmartFeedEventDto;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PendingEventMapper {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private PendingEventMapper() {
    }

    public static PendingEventEntity toEntity(SmartFeedEventDto event) {
        return new PendingEventEntity(
                event.getEventId(),
                event.getSessionId(),
                event.getEventType(),
                event.getArticleId(),
                event.getCategoryId(),
                event.getTimestamp(),
                event.getDevice() == null ? null : GSON.toJson(event.getDevice()),
                GSON.toJson(event.getMetadata()),
                0,
                null,
                System.currentTimeMillis()
        );
    }

    public static SmartFeedEventDto toDto(PendingEventEntity event) {
        return new SmartFeedEventDto(
                event.getEventId(),
                event.getSessionId(),
                event.getEventType(),
                event.getArticleId(),
                event.getCategoryId(),
                event.getTimestamp(),
                parseMap(event.getDeviceJson(), true),
                parseMap(event.getMetadataJson(), false)
        );
    }

    public static List<SmartFeedEventDto> toDtos(List<PendingEventEntity> events) {
        List<SmartFeedEventDto> dtos = new ArrayList<>(events.size());
        for (PendingEventEntity event : events) {
            dtos.add(toDto(event));
        }
        return dtos;
    }

    private static Map<String, Object> parseMap(String json, boolean nullable) {
        if (json == null || json.trim().isEmpty()) {
            return nullable ? null : Collections.emptyMap();
        }
        try {
            Map<String, Object> value = GSON.fromJson(json, MAP_TYPE);
            if (value != null) {
                return value;
            }
        } catch (JsonSyntaxException ignored) {
            // The worker will still send a valid empty object for a corrupt optional field.
        }
        return nullable ? null : Collections.emptyMap();
    }
}
