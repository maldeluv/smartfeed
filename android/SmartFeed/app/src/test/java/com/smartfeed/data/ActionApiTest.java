package com.smartfeed.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.CategoryDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ActionApiTest {

    private MockWebServer server;
    private SmartFeedApi api;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SmartFeedApi.class);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void actionsUseExpectedHttpMethodsAndPaths() throws Exception {
        enqueueStatusResponses(6);

        assertTrue(api.likeArticle(9).execute().isSuccessful());
        assertRequest("POST", "/api/v1/articles/9/like");
        assertTrue(api.unlikeArticle(9).execute().isSuccessful());
        assertRequest("DELETE", "/api/v1/articles/9/like");
        assertTrue(api.saveArticle(9).execute().isSuccessful());
        assertRequest("POST", "/api/v1/articles/9/save");
        assertTrue(api.unsaveArticle(9).execute().isSuccessful());
        assertRequest("DELETE", "/api/v1/articles/9/save");
        assertTrue(api.subscribeCategory(4).execute().isSuccessful());
        assertRequest("POST", "/api/v1/categories/4/subscribe");
        assertTrue(api.unsubscribeCategory(4).execute().isSuccessful());
        assertRequest("DELETE", "/api/v1/categories/4/subscribe");
    }

    @Test
    public void savedArticlesUseExpectedEndpoint() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"items\":[],\"total\":0,\"limit\":25,\"offset\":0}"));

        assertTrue(api.getSavedArticles(25, 0).execute().isSuccessful());
        assertRequest("GET", "/api/v1/saved?limit=25&offset=0");
    }

    @Test
    public void dtoCopiesKeepIndependentOptimisticState() {
        Gson gson = new Gson();
        ArticleDto article = gson.fromJson(
                "{\"id\":9,\"is_liked\":false,\"is_saved\":true}",
                ArticleDto.class
        );
        CategoryDto category = gson.fromJson(
                "{\"id\":4,\"name\":\"Backend\",\"is_subscribed\":false}",
                CategoryDto.class
        );

        ArticleDto liked = article.withLiked(true);
        ArticleDto unsaved = liked.withSaved(false);
        CategoryDto subscribed = category.withSubscribed(true);

        assertFalse(article.isLiked());
        assertTrue(article.isSaved());
        assertTrue(liked.isLiked());
        assertTrue(liked.isSaved());
        assertTrue(unsaved.isLiked());
        assertFalse(unsaved.isSaved());
        assertFalse(category.isSubscribed());
        assertTrue(subscribed.isSubscribed());
    }

    private void enqueueStatusResponses(int count) {
        for (int index = 0; index < count; index++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"status\":\"ok\"}"));
        }
    }

    private void assertRequest(String method, String path) throws Exception {
        RecordedRequest request = server.takeRequest();
        assertEquals(method, request.getMethod());
        assertEquals(path, request.getPath());
    }
}
