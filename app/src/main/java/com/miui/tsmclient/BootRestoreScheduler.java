package com.miui.tsmclient;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

final class BootRestoreScheduler {
    static final int JOB_ID = 1205;
    private static final String TAG = "WalletBootRestore";

    private BootRestoreScheduler() {}

    static void schedule(Context context) {
        scheduleAfter(context, 180_000L);
    }

    static boolean scheduleAfter(Context context, long delayMs) {
        JobScheduler scheduler = (JobScheduler)
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if (scheduler == null) return false;

        try {
            JobInfo info = new JobInfo.Builder(
                    JOB_ID,
                    new ComponentName(context, BootRestoreJobService.class)
            )
                    .setMinimumLatency(delayMs)
                    .setOverrideDeadline(delayMs + 120_000L)
                    .setBackoffCriteria(
                            120_000L,
                            JobInfo.BACKOFF_POLICY_LINEAR)
                    .build();

            return scheduler.schedule(info)
                    == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not schedule boot recovery", e);
            return false;
        }
    }
}
