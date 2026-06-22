package com.smartfeed;

import android.app.Application;

import com.smartfeed.worker.EventSyncScheduler;

public final class SmartFeedApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        EventSyncScheduler.ensurePeriodic(this);
        EventSyncScheduler.enqueueOneTime(this);
    }
}
