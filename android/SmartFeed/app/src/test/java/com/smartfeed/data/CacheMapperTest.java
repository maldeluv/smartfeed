package com.smartfeed.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.smartfeed.data.local.CacheMapper;
import com.smartfeed.data.local.entity.ArticleEntity;
import com.smartfeed.data.local.entity.CategoryEntity;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.CategoryDto;

import org.junit.Test;

public final class CacheMapperTest {

    @Test
    public void articleRoundTripKeepsDtoFieldsAndInteractionState() {
        ArticleDto source = new Gson().fromJson(
                "{"
                        + "\"id\":120,"
                        + "\"title\":\"Room article\","
                        + "\"summary\":\"Cached summary\","
                        + "\"content\":\"Cached content\","
                        + "\"source_url\":\"https://example.test/120\","
                        + "\"category_id\":12,"
                        + "\"author\":\"SmartFeed\","
                        + "\"published_at\":\"2026-06-20T12:00:00Z\","
                        + "\"created_at\":\"2026-06-20T12:00:00Z\","
                        + "\"popularity_score\":42.5,"
                        + "\"is_liked\":true,"
                        + "\"is_saved\":true,"
                        + "\"category\":{\"id\":12,\"name\":\"Architecture\"}"
                        + "}",
                ArticleDto.class
        );

        ArticleEntity entity = CacheMapper.toEntity(source, 1000L, true);
        ArticleDto restored = CacheMapper.toDto(entity);

        assertEquals(120L, restored.getId());
        assertEquals("Room article", restored.getTitle());
        assertEquals("Cached content", restored.getContent());
        assertEquals("Architecture", restored.getCategoryName());
        assertEquals(42.5, restored.getPopularityScore(), 0.001);
        assertTrue(restored.isLiked());
        assertTrue(restored.isSaved());
        assertTrue(entity.isInFeed());
    }

    @Test
    public void categoryRoundTripKeepsSubscriptionState() {
        CategoryDto source = new Gson().fromJson(
                "{\"id\":4,\"name\":\"Backend\",\"slug\":\"backend\","
                        + "\"description\":\"Server engineering\","
                        + "\"is_subscribed\":true}",
                CategoryDto.class
        );

        CategoryEntity entity = CacheMapper.toEntity(source, 1000L);
        CategoryDto restored = CacheMapper.toDto(entity);

        assertEquals(4L, restored.getId());
        assertEquals("Backend", restored.getName());
        assertTrue(restored.isSubscribed());
        assertFalse(restored.withSubscribed(false).isSubscribed());
    }
}
