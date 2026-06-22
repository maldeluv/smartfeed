package com.smartfeed.data.model;

public final class StatusResponse {

    private String status;
    private EventAcceptedResponseDto event;

    public String getStatus() {
        return status;
    }

    public EventAcceptedResponseDto getEvent() {
        return event;
    }
}
