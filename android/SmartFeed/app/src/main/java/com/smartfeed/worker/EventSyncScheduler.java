package com.smartfeed.worker;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.smartfeed.BuildConfig;

import java.util.concurrent.TimeUnit;

public final class EventSyncScheduler {

    public static final String ONE_TIME_WORK_NAME = "smartfeed-event-sync-v2";
    public static final String PERIODIC_WORK_NAME = "smartfeed-event-periodic-sync-v3";
    public static final String WORK_TAG = "smartfeed-event-sync";

    private static final String LEGACY_ONE_TIME_WORK_NAME = "smartfeed-event-sync";
    private static final String LEGACY_PERIODIC_WORK_NAME =
            "smartfeed-event-periodic-sync";
    private static final String LEGACY_PERIODIC_WORK_NAME_V2 =
            "smartfeed-event-periodic-sync-v2";
    private static final long PERIODIC_INTERVAL_HOURS = 6L;

    private EventSyncScheduler() {
    }

    public static void enqueueOneTime(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(EventSyncWorker.class)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                )
                .addTag(WORK_TAG)
                .build();

        WorkManager.getInstance(context.getApplicationContext()).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
        );
    }

    public static void ensurePeriodic(Context context) {
        WorkManager workManager = WorkManager.getInstance(
                context.getApplicationContext()
        );
        workManager.cancelUniqueWork(LEGACY_ONE_TIME_WORK_NAME);
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME);
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK_NAME_V2);

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                EventSyncWorker.class,
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS
        )
                .setInitialDelay(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30,
                        TimeUnit.SECONDS
                )
                .addTag(WORK_TAG)
                .build();

        workManager.enqueueUniquePeriodicWork(
                        PERIODIC_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }

    private static Constraints networkConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(
                        BuildConfig.DEBUG
                                ? NetworkType.NOT_REQUIRED
                                : NetworkType.CONNECTED
                )
                .build();
    }
}
