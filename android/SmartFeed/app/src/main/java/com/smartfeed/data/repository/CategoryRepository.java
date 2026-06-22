package com.smartfeed.data.repository;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.CategoryDto;

import java.util.List;
import java.util.Objects;

import retrofit2.Call;

public final class CategoryRepository {

    private final SmartFeedApi api;

    public CategoryRepository(SmartFeedApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Call<List<CategoryDto>> loadCategories(
            RepositoryCallback<List<CategoryDto>> callback
    ) {
        Call<List<CategoryDto>> call = api.getCategories();
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }
}
