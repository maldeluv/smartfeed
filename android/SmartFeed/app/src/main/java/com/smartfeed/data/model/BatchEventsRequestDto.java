package com.smartfeed.data.model;

import java.util.List;

public final class BatchEventsRequestDto {

    private final List<SmartFeedEventDto> events;

    public BatchEventsRequestDto(List<SmartFeedEventDto> events) {
        this.events = events;
    }

    public List<SmartFeedEventDto> getEvents() {
        return events;
    }
}
