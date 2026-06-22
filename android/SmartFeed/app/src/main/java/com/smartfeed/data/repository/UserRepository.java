package com.smartfeed.data.repository;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.UserDto;

import java.util.Objects;

import retrofit2.Call;

public final class UserRepository {

    private final SmartFeedApi api;

    public UserRepository(SmartFeedApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Call<UserDto> loadCurrentUser(RepositoryCallback<UserDto> callback) {
        Call<UserDto> call = api.getCurrentUser();
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }
}
