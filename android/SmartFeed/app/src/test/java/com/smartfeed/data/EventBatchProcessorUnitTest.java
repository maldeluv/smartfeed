package com.smartfeed.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.PendingEventMapper;
import com.smartfeed.data.local.dao.PendingEventDao;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.model.SmartFeedEventDto;
import com.smartfeed.worker.EventBatchProcessor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class EventBatchProcessorUnitTest {

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

    private MockWebServer server;
    private FakePendingEventDao eventDao;
    private EventBatchProcessor processor;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        SmartFeedApi api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(SmartFeedApi.class);
        eventDao = new FakePendingEventDao();
        processor = new EventBatchProcessor(eventDao, api);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void successfulBatchDeletesAcceptedEvent() throws Exception {
        eventDao.upsert(testEvent());
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"accepted_count\":1,\"failed_count\":0,\"events\":[{"
                        + "\"event_id\":\"" + EVENT_ID + "\","
                        + "\"status\":\"accepted\",\"delivery\":\"kafka\"}]}"));

        assertEquals(
                EventBatchProcessor.BatchResult.SENT,
                processor.processNextBatch()
        );
        assertEquals(0, eventDao.count());
        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertEquals("POST", request.getMethod());
        assertEquals("/api/v1/events/batch", request.getPath());
        assertTrue(request.getBody().readUtf8().contains(EVENT_ID));
    }

    @Test
    public void httpErrorKeepsEventAndIncrementsRetry() {
        eventDao.upsert(testEvent());
        server.enqueue(new MockResponse().setResponseCode(500));

        assertEquals(
                EventBatchProcessor.BatchResult.RETRY,
                processor.processNextBatch()
        );
        PendingEventEntity pending = eventDao.getOldest(1).get(0);
        assertEquals(1, pending.getRetryCount());
        assertTrue(pending.getLastError().contains("HTTP 500"));
    }

    private PendingEventEntity testEvent() {
        return PendingEventMapper.toEntity(new SmartFeedEventDto(
                EVENT_ID,
                "22222222-2222-2222-2222-222222222222",
                "view_article",
                120L,
                12L,
                "2026-06-21T10:00:00.000Z",
                Collections.singletonMap("platform", "android"),
                Collections.singletonMap("source", "feed")
        ));
    }

    private static final class FakePendingEventDao implements PendingEventDao {

        private final List<PendingEventEntity> events = new ArrayList<>();

        @Override
        public void upsert(PendingEventEntity event) {
            deleteByIds(Collections.singletonList(event.getEventId()));
            events.add(event);
        }

        @Override
        public List<PendingEventEntity> getOldest(int limit) {
            return new ArrayList<>(events.subList(0, Math.min(limit, events.size())));
        }

        @Override
        public int count() {
            return events.size();
        }

        @Override
        public LiveData<Integer> observeCount() {
            return new MutableLiveData<>(events.size());
        }

        @Override
        public void deleteByIds(List<String> eventIds) {
            events.removeIf(event -> eventIds.contains(event.getEventId()));
        }

        @Override
        public void markFailed(String eventId, String lastError) {
            for (int index = 0; index < events.size(); index++) {
                PendingEventEntity event = events.get(index);
                if (event.getEventId().equals(eventId)) {
                    events.set(index, failedCopy(event, lastError));
                    return;
                }
            }
        }

        @Override
        public void markFailedByIds(List<String> eventIds, String lastError) {
            for (String eventId : eventIds) {
                markFailed(eventId, lastError);
            }
        }

        private PendingEventEntity failedCopy(
                PendingEventEntity event,
                String lastError
        ) {
            return new PendingEventEntity(
                    event.getEventId(),
                    event.getSessionId(),
                    event.getEventType(),
                    event.getArticleId(),
                    event.getCategoryId(),
                    event.getTimestamp(),
                    event.getDeviceJson(),
                    event.getMetadataJson(),
                    event.getRetryCount() + 1,
                    lastError,
                    event.getCreatedAt()
            );
        }
    }
}
