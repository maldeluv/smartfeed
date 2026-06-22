package com.smartfeed.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.smartfeed.data.local.AppDatabase;
import com.smartfeed.data.local.PendingEventMapper;
import com.smartfeed.data.model.SmartFeedEventDto;
import com.smartfeed.worker.EventSyncScheduler;

import java.util.Objects;

public final class EventRepository {

    private final Context applicationContext;
    private final AppDatabase database;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public EventRepository(Context context) {
        applicationContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        database = AppDatabase.getInstance(applicationContext);
    }

    public void enqueueEvent(
            SmartFeedEventDto event,
            RepositoryCallback<Void> callback
    ) {
        AppDatabase.DATABASE_EXECUTOR.execute(() -> {
            try {
                database.pendingEventDao().upsert(PendingEventMapper.toEntity(event));
                EventSyncScheduler.enqueueOneTime(applicationContext);
                mainHandler.post(() -> callback.onResult(ApiResult.success(null)));
            } catch (RuntimeException exception) {
                mainHandler.post(() -> callback.onResult(ApiResult.unexpectedError(
                        "Unable to save event to the local queue",
                        exception
                )));
            }
        });
    }

    public LiveData<Integer> observePendingCount() {
        return database.pendingEventDao().observeCount();
    }

    public void requestSync() {
        EventSyncScheduler.enqueueOneTime(applicationContext);
    }
}
