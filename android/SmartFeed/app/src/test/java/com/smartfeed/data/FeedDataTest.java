package com.smartfeed.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.ArticlePageDto;
import com.smartfeed.data.model.SmartFeedEventDto;
import com.smartfeed.util.DateFormatter;
import com.smartfeed.util.OneTimeEvent;

import org.junit.Test;

import java.util.Collections;

public final class FeedDataTest {

    @Test
    public void gsonMapsBackendArticlePage() {
        String json = "{"
                + "\"items\":[{"
                + "\"id\":120,"
                + "\"title\":\"Architecture Article\","
                + "\"summary\":\"Summary\","
                + "\"category_id\":12,"
                + "\"published_at\":\"2026-06-18T23:39:00.682736Z\","
                + "\"created_at\":\"2026-06-18T23:39:00.140970Z\","
                + "\"popularity_score\":55.6,"
                + "\"category\":{"
                + "\"id\":12,"
                + "\"name\":\"Architecture\","
                + "\"slug\":\"architecture\""
                + "}}],"
                + "\"total\":200,"
                + "\"limit\":1,"
                + "\"offset\":0"
                + "}";

        ArticlePageDto page = new Gson().fromJson(json, ArticlePageDto.class);

        assertEquals(200, page.getTotal());
        assertEquals(1, page.getItems().size());
        assertEquals(120L, page.getItems().get(0).getId());
        assertEquals("Architecture", page.getItems().get(0).getCategoryName());
    }

    @Test
    public void dateFormatterFormatsBackendTimestamp() {
        assertEquals(
                "18 июн. 2026",
                DateFormatter.formatArticleDate("2026-06-18T23:39:00.682736Z")
        );
        assertEquals("", DateFormatter.formatArticleDate(null));
    }

    @Test
    public void gsonMapsArticleDetailContent() {
        String json = "{"
                + "\"id\":120,"
                + "\"title\":\"Architecture Article\","
                + "\"summary\":\"Summary\","
                + "\"content\":\"Full article content\","
                + "\"category_id\":12,"
                + "\"published_at\":\"2026-06-18T23:39:00Z\","
                + "\"created_at\":\"2026-06-18T23:39:00Z\","
                + "\"popularity_score\":55.6"
                + "}";

        ArticleDto article = new Gson().fromJson(json, ArticleDto.class);

        assertEquals(120L, article.getId());
        assertEquals("Full article content", article.getContent());
    }

    @Test
    public void gsonSerializesRecommendedArticleEventAliases() {
        SmartFeedEventDto event = new SmartFeedEventDto(
                "event-id",
                "session-id",
                "open_recommended_article",
                120L,
                12L,
                "2026-06-20T12:00:00.000Z",
                Collections.singletonMap("platform", "android"),
                Collections.singletonMap("source", "recommendations")
        );

        JsonObject json = new Gson().toJsonTree(event).getAsJsonObject();

        assertEquals("event-id", json.get("eventId").getAsString());
        assertEquals("session-id", json.get("sessionId").getAsString());
        assertEquals("open_recommended_article", json.get("eventType").getAsString());
        assertEquals(120L, json.get("articleId").getAsLong());
        assertEquals(12L, json.get("categoryId").getAsLong());
    }

    @Test
    public void gsonSerializesOpenRecommendationsWithoutArticle() {
        SmartFeedEventDto event = new SmartFeedEventDto(
                "event-id",
                "session-id",
                "open_recommendations",
                null,
                null,
                "2026-06-20T12:00:00.000Z",
                Collections.singletonMap("platform", "android"),
                Collections.singletonMap("screen", "recommendations")
        );

        JsonObject json = new Gson().toJsonTree(event).getAsJsonObject();

        assertEquals("open_recommendations", json.get("eventType").getAsString());
        assertEquals("recommendations", json.getAsJsonObject("metadata")
                .get("screen").getAsString());
    }

    @Test
    public void oneTimeEventReturnsContentOnce() {
        OneTimeEvent<String> event = new OneTimeEvent<>("message");

        assertEquals("message", event.getContentIfNotHandled());
        assertEquals(null, event.getContentIfNotHandled());
    }
}
