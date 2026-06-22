package com.smartfeed.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.UserAnalyticsDto;
import com.smartfeed.data.model.UserDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class AnalyticsProfileDataTest {

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
    public void analyticsEndpointMapsPersonalMetrics() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{"
                        + "\"source\":\"postgres.pending_events\","
                        + "\"favorite_categories\":[{"
                        + "\"category_id\":12,"
                        + "\"category_name\":\"Architecture\","
                        + "\"score\":14.0,"
                        + "\"events_count\":6,"
                        + "\"views_count\":3,"
                        + "\"likes_count\":2,"
                        + "\"saves_count\":1}],"
                        + "\"views_count\":8,"
                        + "\"likes_count\":4,"
                        + "\"saves_count\":2,"
                        + "\"recommendations_opened\":5,"
                        + "\"recommended_articles_opened\":2,"
                        + "\"recommendation_ctr\":0.4"
                        + "}"));

        Response<UserAnalyticsDto> response = api.getMyAnalytics(5).execute();

        assertNotNull(response.body());
        assertEquals(8, response.body().getViewsCount());
        assertEquals(4, response.body().getLikesCount());
        assertEquals(2, response.body().getSavesCount());
        assertEquals(5, response.body().getRecommendationsOpened());
        assertEquals(0.4, response.body().getRecommendationCtr(), 0.001);
        assertEquals(
                "Architecture",
                response.body().getFavoriteCategories().get(0).getCategoryName()
        );
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/v1/analytics/me?limit=5", request.getPath());
    }

    @Test
    public void profileEndpointMapsEmail() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{"
                        + "\"id\":7,"
                        + "\"email\":\"demo@smartfeed.local\","
                        + "\"full_name\":\"Demo User\","
                        + "\"role\":\"user\","
                        + "\"created_at\":\"2026-06-20T10:00:00Z\","
                        + "\"last_login_at\":null,"
                        + "\"is_active\":true"
                        + "}"));

        Response<UserDto> response = api.getCurrentUser().execute();

        assertNotNull(response.body());
        assertEquals("demo@smartfeed.local", response.body().getEmail());
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/api/v1/users/me", request.getPath());
    }
}
