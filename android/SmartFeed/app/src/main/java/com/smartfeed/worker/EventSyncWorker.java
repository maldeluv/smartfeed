package com.smartfeed.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.smartfeed.data.api.ApiClient;
import com.smartfeed.data.api.SmartFeedApi;
import com.smartfeed.data.local.AppDatabase;
import com.smartfeed.data.local.SessionManager;
import com.smartfeed.data.local.dao.PendingEventDao;
import com.smartfeed.util.NetworkMonitor;

import java.util.concurrent.locks.ReentrantLock;

public final class EventSyncWorker extends Worker {

    private static final String TAG = "EventSyncWorker";
    private static final int MAX_BATCHES_PER_RUN = 10;
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    public EventSyncWorker(
            @NonNull Context applicationContext,
            @NonNull WorkerParameters workerParameters
    ) {
        super(applicationContext, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!SYNC_LOCK.tryLock()) {
            return Result.retry();
        }
        try {
            return syncEvents();
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    private Result syncEvents() {
        SessionManager sessionManager = new SessionManager(getApplicationContext());
        if (!sessionManager.hasAccessToken()) {
            return Result.success();
        }
        if (!NetworkMonitor.isConnected(getApplicationContext())) {
            return Result.retry();
        }

        PendingEventDao eventDao = AppDatabase.getInstance(getApplicationContext())
                .pendingEventDao();
        SmartFeedApi api = ApiClient.create(sessionManager);
        EventBatchProcessor processor = new EventBatchProcessor(eventDao, api);

        for (int batchNumber = 0; batchNumber < MAX_BATCHES_PER_RUN; batchNumber++) {
            EventBatchProcessor.BatchResult result;
            try {
                result = processor.processNextBatch();
            } catch (RuntimeException exception) {
                Log.e(TAG, "Unable to process pending events", exception);
                return Result.retry();
            }

            if (result == EventBatchProcessor.BatchResult.EMPTY) {
                return Result.success();
            }
            if (result == EventBatchProcessor.BatchResult.RETRY) {
                return Result.retry();
            }
        }

        return eventDao.count() == 0 ? Result.success() : Result.retry();
    }
}
