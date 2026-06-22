package com.smartfeed.data.repository;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.ArticleDto;
import com.smartfeed.data.model.ArticlePageDto;

import java.util.Objects;

import retrofit2.Call;

public final class ArticleRepository {

    private final SmartFeedApi api;

    public ArticleRepository(SmartFeedApi api) {
        this.api = Objects.requireNonNull(api, "api");
    }

    public Call<ArticlePageDto> loadArticles(
            int limit,
            int offset,
            RepositoryCallback<ArticlePageDto> callback
    ) {
        Call<ArticlePageDto> call = api.getArticles(null, null, limit, offset);
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }

    public Call<ArticleDto> loadArticle(
            long articleId,
            RepositoryCallback<ArticleDto> callback
    ) {
        Call<ArticleDto> call = api.getArticle(articleId);
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }

    public Call<ArticlePageDto> loadSavedArticles(
            int limit,
            int offset,
            RepositoryCallback<ArticlePageDto> callback
    ) {
        Call<ArticlePageDto> call = api.getSavedArticles(limit, offset);
        NetworkRequestExecutor.enqueue(call, callback);
        return call;
    }
}
