package com.smartfeed.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.smartfeed.data.local.PendingEventMapper;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.model.SmartFeedEventDto;

import org.junit.Test;

import java.util.Collections;

public final class PendingEventMapperTest {

    @Test
    public void eventRoundTripKeepsStableIdAndPayload() {
        SmartFeedEventDto source = new SmartFeedEventDto(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                "view_article",
                120L,
                12L,
                "2026-06-21T10:00:00.000Z",
                Collections.singletonMap("platform", "android"),
                Collections.singletonMap("source", "feed")
        );

        PendingEventEntity entity = PendingEventMapper.toEntity(source);
        SmartFeedEventDto restored = PendingEventMapper.toDto(entity);

        assertEquals(source.getEventId(), restored.getEventId());
        assertEquals(source.getSessionId(), restored.getSessionId());
        assertEquals("view_article", restored.getEventType());
        assertEquals(Long.valueOf(120L), restored.getArticleId());
        assertEquals("android", restored.getDevice().get("platform"));
        assertEquals("feed", restored.getMetadata().get("source"));
        assertEquals(0, entity.getRetryCount());
        assertNull(entity.getLastError());
    }
}
