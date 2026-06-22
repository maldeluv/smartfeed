package com.smartfeed.data;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.smartfeed.data.model.RecommendationPageDto;

import org.junit.Test;

public final class RecommendationDataTest {

    @Test
    public void gsonMapsBackendRecommendationPage() {
        String json = "{"
                + "\"items\":[{"
                + "\"id\":10,"
                + "\"user_id\":7,"
                + "\"article_id\":120,"
                + "\"score\":0.87,"
                + "\"reason\":\"als_implicit_feedback\","
                + "\"model_version\":\"als-v1\","
                + "\"created_at\":\"2026-06-20T12:00:00Z\","
                + "\"article\":{"
                + "\"id\":120,"
                + "\"title\":\"Architecture Article\","
                + "\"summary\":\"Summary\","
                + "\"category_id\":12,"
                + "\"category_name\":\"Architecture\","
                + "\"published_at\":\"2026-06-18T23:39:00Z\""
                + "}}],"
                + "\"total\":1,"
                + "\"limit\":50,"
                + "\"offset\":0"
                + "}";

        RecommendationPageDto page = new Gson().fromJson(
                json,
                RecommendationPageDto.class
        );

        assertEquals(1, page.getTotal());
        assertEquals(0.87, page.getItems().get(0).getScore(), 0.001);
        assertEquals("als_implicit_feedback", page.getItems().get(0).getReason());
        assertEquals("Architecture Article", page.getItems().get(0).getArticle().getTitle());
    }
}
