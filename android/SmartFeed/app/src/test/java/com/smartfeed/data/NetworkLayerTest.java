package com.smartfeed.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.smartfeed.data.api.AuthInterceptor;
import com.smartfeed.data.model.AuthRequest;
import com.smartfeed.data.model.AuthResponse;
import com.smartfeed.data.repository.ApiResult;
import com.smartfeed.data.repository.NetworkRequestExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.GET;

public final class NetworkLayerTest {

    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void authInterceptorAddsBearerToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new AuthInterceptor(() -> "test-jwt"))
                .build();

        Request request = new Request.Builder().url(server.url("/profile")).build();
        try (okhttp3.Response ignored = client.newCall(request).execute()) {
            RecordedRequest recordedRequest = server.takeRequest(5, TimeUnit.SECONDS);
            assertNotNull(recordedRequest);
            assertEquals("Bearer test-jwt", recordedRequest.getHeader("Authorization"));
        }
    }

    @Test
    public void repositoryHelperMapsFastApiHttpError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"Invalid request\"}"));

        TestApi api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .build()
                .create(TestApi.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<ResponseBody>> resultRef = new AtomicReference<>();

        NetworkRequestExecutor.enqueue(api.request(), result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        ApiResult<ResponseBody> result = resultRef.get();
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals(ApiResult.Type.HTTP_ERROR, result.getType());
        assertEquals(422, result.getHttpCode());
        assertEquals("Invalid request", result.getMessage());
    }

    @Test
    public void gsonMapsBackendAuthResponse() {
        String json = "{"
                + "\"access_token\":\"jwt\","
                + "\"token_type\":\"bearer\","
                + "\"user\":{"
                + "\"id\":7,"
                + "\"email\":\"demo@smartfeed.local\","
                + "\"full_name\":\"Demo User\","
                + "\"role\":\"user\","
                + "\"created_at\":\"2026-06-20T10:00:00Z\","
                + "\"last_login_at\":null,"
                + "\"is_active\":true"
                + "}}";

        AuthResponse response = new Gson().fromJson(json, AuthResponse.class);

        assertEquals("jwt", response.getAccessToken());
        assertEquals("bearer", response.getTokenType());
        assertEquals("Demo User", response.getUser().getFullName());
        assertTrue(response.getUser().isActive());
    }

    @Test
    public void gsonBuildsBackendAuthRequests() {
        Gson gson = new Gson();
        JsonObject login = gson.toJsonTree(
                AuthRequest.forLogin("demo@smartfeed.local", "demo12345")
        ).getAsJsonObject();
        JsonObject registration = gson.toJsonTree(
                AuthRequest.forRegistration(
                        "new@smartfeed.local",
                        "New User",
                        "password123"
                )
        ).getAsJsonObject();

        assertEquals("demo@smartfeed.local", login.get("email").getAsString());
        assertEquals("demo12345", login.get("password").getAsString());
        assertFalse(login.has("full_name"));
        assertEquals("New User", registration.get("full_name").getAsString());
        assertEquals("password123", registration.get("password").getAsString());
    }

    private interface TestApi {

        @GET("test")
        Call<ResponseBody> request();
    }
}
