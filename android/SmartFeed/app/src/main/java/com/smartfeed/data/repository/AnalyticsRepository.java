package com.smartfeed.data.repository;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.UserAnalyticsDto;

import java.util.Objects;

import retrofit2.Call;

public final class AnalyticsRepository {

    private final SmartFeedApi api;

    public AnalyticsRepository(SmartFeedApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Call<UserAnalyticsDto> loadMyAnalytics(
            int categoryLimit,
            RepositoryCallback<UserAnalyticsDto> callback
    ) {
        Call<UserAnalyticsDto> call = api.getMyAnalytics(categoryLimit);
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }
}
