package com.miui.tsmclient;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** A short-lived JobScheduler worker; never runs on the main thread. */
public final class UpdateCheckJobService extends JobService {
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(JobParameters params) {
        stopped = false;
        new Thread(() -> {
            try {
                UpdateChecker.UpdateInfo update = UpdateChecker.fetchIfDue(getApplicationContext());
                if (!stopped && update != null) {
                    UpdateChecker.notifyIfNewer(getApplicationContext(), update);
                }
            } finally {
                if (!stopped) {
                    jobFinished(params, false);
                }
            }
        }, "wallet-shortcut-background-update").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        // Android may retry later when power/network conditions allow.
        return true;
    }
}
