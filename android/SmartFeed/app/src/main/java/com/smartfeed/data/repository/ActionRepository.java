package com.smartfeed.data.repository;

import android.content.Context;

import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.model.StatusResponse;
import com.smartfeed.worker.EventSyncScheduler;

import java.util.Objects;

import retrofit2.Call;

public final class ActionRepository {

    private final SmartFeedApi api;
    private final Context applicationContext;

    public ActionRepository(Context context, SmartFeedApi api) {
        applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        this.api = Objects.requireNonNull(api, "api");
    }

    public Call<StatusResponse> setArticleLiked(
            long articleId,
            boolean liked,
            RepositoryCallback<StatusResponse> callback
    ) {
        Call<StatusResponse> call = liked
                ? api.likeArticle(articleId)
                : api.unlikeArticle(articleId);
        return enqueueAction(call, callback);
    }

    public Call<StatusResponse> setArticleSaved(
            long articleId,
            boolean saved,
            RepositoryCallback<StatusResponse> callback
    ) {
        Call<StatusResponse> call = saved
                ? api.saveArticle(articleId)
                : api.unsaveArticle(articleId);
        return enqueueAction(call, callback);
    }

    public Call<StatusResponse> setCategorySubscribed(
            long categoryId,
            boolean subscribed,
            RepositoryCallback<StatusResponse> callback
    ) {
        Call<StatusResponse> call = subscribed
                ? api.subscribeCategory(categoryId)
                : api.unsubscribeCategory(categoryId);
        return enqueueAction(call, callback);
    }

    private Call<StatusResponse> enqueueAction(
            Call<StatusResponse> call,
            RepositoryCallback<StatusResponse> callback
    ) {
        NetworkRequestExecutor.enqueue(call, result -> {
            if (result.isSuccess()) {
                // Action endpoints publish their own event; only drain client events here.
                EventSyncScheduler.enqueueOneTime(applicationContext);
            }
            callback.onResult(result);
        });
        return call;
    }
}
