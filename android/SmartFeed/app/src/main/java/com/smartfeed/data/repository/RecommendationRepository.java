package com.smartfeed.data.repository;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.RecommendationPageDto;

import java.util.Objects;

import retrofit2.Call;

public final class RecommendationRepository {

    private final SmartFeedApi api;

    public RecommendationRepository(SmartFeedApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Call<RecommendationPageDto> loadRecommendations(
            int limit,
            int offset,
            RepositoryCallback<RecommendationPageDto> callback
    ) {
        Call<RecommendationPageDto> call = api.getRecommendations(limit, offset);
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }
}
