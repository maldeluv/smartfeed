package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class BatchEventResponseDto {

    @SerializedName("accepted_count")
    private int acceptedCount;

    @SerializedName("failed_count")
    private int failedCount;

    private List<EventAcceptedResponseDto> events;

    public int getAcceptedCount() {
        return acceptedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public List<EventAcceptedResponseDto> getEvents() {
        return events;
    }
}
