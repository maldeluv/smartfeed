package com.smartfeed.worker;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.PendingEventMapper;
import com.smartfeed.data.local.dao.PendingEventDao;
import com.smartfeed.data.local.entity.PendingEventEntity;
import com.smartfeed.data.model.BatchEventResponseDto;
import com.smartfeed.data.model.BatchEventsRequestDto;
import com.smartfeed.data.model.EventAcceptedResponseDto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import retrofit2.Response;

public final class EventBatchProcessor {

    private static final int BATCH_SIZE = 100;

    private final PendingEventDao eventDao;
    private final SmartFeedApi api;

    public EventBatchProcessor(PendingEventDao eventDao, SmartFeedApi api) {
        this.eventDao = Objects.requireNonNull(eventDao, "eventDao");
        this.api = Objects.requireNonNull(api, "api");
    }

    public BatchResult processNextBatch() {
        List<PendingEventEntity> pendingEvents = eventDao.getOldest(BATCH_SIZE);
        if (pendingEvents.isEmpty()) {
            return BatchResult.EMPTY;
        }

        List<String> eventIds = eventIds(pendingEvents);
        try {
            Response<BatchEventResponseDto> response = api.sendEvents(
                    new BatchEventsRequestDto(PendingEventMapper.toDtos(pendingEvents))
            ).execute();

            if (!response.isSuccessful() || response.body() == null) {
                String error = "HTTP " + response.code() + " while syncing events";
                eventDao.markFailedByIds(eventIds, error);
                return BatchResult.RETRY;
            }

            return applyBatchResponse(pendingEvents, response.body());
        } catch (IOException exception) {
            String error = safeMessage(exception);
            eventDao.markFailedByIds(eventIds, error);
            return BatchResult.RETRY;
        } catch (RuntimeException exception) {
            String error = safeMessage(exception);
            eventDao.markFailedByIds(eventIds, error);
            return BatchResult.RETRY;
        }
    }

    private BatchResult applyBatchResponse(
            List<PendingEventEntity> pendingEvents,
            BatchEventResponseDto response
    ) {
        Map<String, EventAcceptedResponseDto> responsesById = new HashMap<>();
        if (response.getEvents() != null) {
            for (EventAcceptedResponseDto event : response.getEvents()) {
                if (event.getEventId() != null) {
                    responsesById.put(event.getEventId(), event);
                }
            }
        }

        List<String> acceptedIds = new ArrayList<>();
        boolean hasFailures = false;
        for (PendingEventEntity pendingEvent : pendingEvents) {
            EventAcceptedResponseDto eventResponse = responsesById.get(
                    pendingEvent.getEventId()
            );
            if (eventResponse != null && !"failed".equals(eventResponse.getDelivery())) {
                acceptedIds.add(pendingEvent.getEventId());
                continue;
            }

            hasFailures = true;
            String error = eventResponse == null
                    ? "Missing event result in batch response"
                    : eventResponse.getDetail();
            eventDao.markFailed(
                    pendingEvent.getEventId(),
                    error == null ? "Event delivery failed" : error
            );
        }

        if (!acceptedIds.isEmpty()) {
            eventDao.deleteByIds(acceptedIds);
        }
        return hasFailures || response.getFailedCount() > 0
                ? BatchResult.RETRY
                : BatchResult.SENT;
    }

    private List<String> eventIds(List<PendingEventEntity> events) {
        List<String> ids = new ArrayList<>(events.size());
        for (PendingEventEntity event : events) {
            ids.add(event.getEventId());
        }
        return ids;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }

    public enum BatchResult {
        EMPTY,
        SENT,
        RETRY
    }
}
