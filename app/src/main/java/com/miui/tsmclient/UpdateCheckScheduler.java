package com.miui.tsmclient;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

/** Inexact, persisted update checks: no foreground service, root or Shizuku. */
final class UpdateCheckScheduler {
    static final int JOB_ID = 1204;
    private static final long INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private UpdateCheckScheduler() {}

    static void schedule(Context context) {
        JobScheduler jobs = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobs == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 24 && jobs.getPendingJob(JOB_ID) != null) return;
            JobInfo info = new JobInfo.Builder(
                    JOB_ID, new ComponentName(context, UpdateCheckJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true)
                    .build();
            jobs.schedule(info);
        } catch (RuntimeException ignored) {
            // Permission/firmware scheduling restrictions must not break the shortcut.
        }
    }
}
