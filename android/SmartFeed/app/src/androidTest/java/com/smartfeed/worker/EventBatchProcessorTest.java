package com.smartfeed.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.AppDatabase;
import com.smartfeed.data.local.PendingEventMapper;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.model.SmartFeedEventDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@RunWith(AndroidJUnit4.class)
public final class EventBatchProcessorTest {

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

    private AppDatabase database;
    private MockWebServer server;
    private EventBatchProcessor processor;

    @Before
    public void setUp() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        server = new MockWebServer();
        server.start();
        SmartFeedApi api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SmartFeedApi.class);
        processor = new EventBatchProcessor(database.pendingEventDao(), api);
    }

    @After
    public void tearDown() throws IOException {
        database.close();
        server.shutdown();
    }

    @Test
    public void successfulBatchDeletesAcceptedEvent() throws Exception {
        database.pendingEventDao().upsert(testEvent());
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"accepted_count\":1,\"failed_count\":0,\"events\":[{"
                        + "\"event_id\":\"" + EVENT_ID + "\","
                        + "\"status\":\"accepted\",\"delivery\":\"kafka\"}]}"));

        EventBatchProcessor.BatchResult result = processor.processNextBatch();

        assertEquals(EventBatchProcessor.BatchResult.SENT, result);
        assertEquals(0, database.pendingEventDao().count());
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/events/batch", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"eventType\":\"view_article\""));
        assertTrue(body.contains(EVENT_ID));
    }

    @Test
    public void failedBatchKeepsEventAndIncrementsRetryCount() {
        database.pendingEventDao().upsert(testEvent());
        server.enqueue(new MockResponse().setResponseCode(500));

        EventBatchProcessor.BatchResult result = processor.processNextBatch();

        assertEquals(EventBatchProcessor.BatchResult.RETRY, result);
        assertEquals(1, database.pendingEventDao().count());
        PendingEventEntity pending = database.pendingEventDao().getOldest(1).get(0);
        assertEquals(1, pending.getRetryCount());
        assertTrue(pending.getLastError().contains("HTTP 500"));
    }

    private PendingEventEntity testEvent() {
        SmartFeedEventDto event = new SmartFeedEventDto(
                EVENT_ID,
                "22222222-2222-2222-2222-222222222222",
                "view_article",
                120L,
                12L,
                "2026-06-21T10:00:00.000Z",
                Collections.singletonMap("platform", "android"),
                Collections.singletonMap("source", "feed")
        );
        return PendingEventMapper.toEntity(event);
    }
}
