package com.smartfeed.data.model;

import com.google.gson.annotations.SerializedName;

public final class EventAcceptedResponseDto {

    @SerializedName("event_id")
    private String eventId;

    private String status;
    private String delivery;
    private String detail;

    public String getEventId() {
        return eventId;
    }

    public String getStatus() {
        return status;
    }

    public String getDelivery() {
        return delivery;
    }

    public String getDetail() {
        return detail;
    }
}
