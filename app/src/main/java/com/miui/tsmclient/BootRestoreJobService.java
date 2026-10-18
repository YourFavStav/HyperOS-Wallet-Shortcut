package com.miui.tsmclient;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;

import rikka.shizuku.Shizuku;

public final class BootRestoreJobService extends JobService {
    private static final String TAG = "WalletBootRestore";
    private static final String PREFS = "shortcut_prefs";
    private static final String ENABLED = "auto_restore_enabled";
    private static final String CONSENT = "auto_restore_prompted_v1";
    private static final String ATTEMPTS = "boot_restore_attempts";
    private static final int MAX_ATTEMPTS = 10;

    private final Handler main = new Handler(Looper.getMainLooper());

    private JobParameters parameters;
    private Shizuku.UserServiceArgs args;
    private ServiceConnection connection;
    private Runnable timeout;
    private volatile boolean active;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "Recovery job started");
        parameters = params;
        active = true;

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int attempt = prefs.getInt(ATTEMPTS, 0) + 1;
        prefs.edit().putInt(ATTEMPTS, attempt).apply();

        main.post(() -> {
            if (!active) return;

            if (!recoveryAllowed(prefs)
                    || attempt > MAX_ATTEMPTS) {
                finish(false);
                return;
            }

            Log.i(TAG, "Recovery attempt " + attempt);

            try {
                if (!Shizuku.pingBinder()) {
                    finish(true);
                    return;
                }

                if (Shizuku.checkSelfPermission()
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Shizuku permission not granted");
                    RecoveryFailureReporter.report(this,
                            "Shizuku permission is missing. Open Shizuku and grant access to HyperOS Wallet Shortcut.");
                    finish(false);
                    return;
                }

                bindToShizuku();
            } catch (Throwable e) {
                Log.w(TAG, "Shizuku unavailable", e);
                finish(true);
            }
        });

        return true;
    }

    private void bindToShizuku() {
        args = new Shizuku.UserServiceArgs(
                new ComponentName(this, ShortcutUserService.class))
                .daemon(false)
                .processNameSuffix("wallet_boot_restore")
                .tag("wallet-boot-restore")
                .version(1);

        connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                if (!active) return;

                if (timeout != null) main.removeCallbacks(timeout);

                timeout = () -> {
                    Log.w(TAG, "Recovery operation timed out");
                    finish(true);
                };
                main.postDelayed(timeout, 45000L);

                new Thread(() -> restoreIfNeeded(binder),
                        "wallet-boot-recovery").start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                main.post(() -> finish(true));
            }
        };

        timeout = () -> {
            Log.w(TAG, "Shizuku binding timed out");
            finish(true);
        };

        main.postDelayed(timeout, 15000L);
        Shizuku.bindUserService(args, connection);
    }

    private void restoreIfNeeded(IBinder binder) {
        boolean retry = false;

        try {
            if (!active) return;

            Result current = transact(
                    binder, ShortcutUserService.TRANSACTION_GET, null);

            if (!current.ok()) {
                retry = true;
            } else {
                String value = current.output.trim();

                if ("mi_pay".equals(value)) {
                    Log.i(TAG, "Shortcut already enabled");
                    RecoveryFailureReporter.clear(this);
                } else if ("null".equals(value)) {
                    boolean enabled = recoveryAllowed(getSharedPreferences(
                            PREFS, MODE_PRIVATE));

                    if (enabled && active
                            && recoveryAllowed(getSharedPreferences(
                                    PREFS, MODE_PRIVATE))) {
                        Result write = transact(
                                binder,
                                ShortcutUserService.TRANSACTION_PUT,
                                "mi_pay");

                        Result verify = transact(
                                binder,
                                ShortcutUserService.TRANSACTION_GET,
                                null);

                        retry = !write.ok()
                                || !verify.ok()
                                || !"mi_pay".equals(verify.output.trim());

                        Log.i(TAG, retry
                                ? "Shortcut restoration failed"
                                : "Shortcut restored successfully");
                        if (!retry) RecoveryFailureReporter.clear(this);
                    }
                } else {
                    Log.i(TAG, "Leaving existing shortcut unchanged: " + value);
                    RecoveryFailureReporter.clear(this);
                }
            }
        } catch (Throwable e) {
            Log.w(TAG, "Recovery operation failed", e);
            retry = true;
        }

        final boolean shouldRetry = retry;
        main.post(() -> finish(shouldRetry));
    }

    private Result transact(IBinder binder, int code, String value)
            throws Exception {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            if (value != null) data.writeString(value);

            if (!binder.transact(code, data, reply, 0)) {
                return new Result(-1, "");
            }

            int exit = reply.readInt();
            String output = reply.readString();
            reply.readString();

            return new Result(exit, output == null ? "" : output);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void finish(boolean retry) {
        if (!active) return;
        active = false;

        if (timeout != null) main.removeCallbacks(timeout);

        if (args != null && connection != null) {
            try {
                Shizuku.unbindUserService(args, connection, true);
            } catch (Throwable ignored) {
            }
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean allowed = recoveryAllowed(prefs);
        int attempts = prefs.getInt(ATTEMPTS, 0);

        if (retry && allowed && attempts >= MAX_ATTEMPTS) {
            RecoveryFailureReporter.report(this,
                    "Automatic recovery couldn't restore the shortcut after several attempts. Check that Shizuku is running and try again.");
        }

        boolean shouldReschedule = retry
                && allowed
                && attempts < MAX_ATTEMPTS;

        jobFinished(parameters, shouldReschedule);
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        active = false;
        if (timeout != null) main.removeCallbacks(timeout);

        if (args != null && connection != null) {
            try {
                Shizuku.unbindUserService(args, connection, true);
            } catch (Throwable ignored) {
            }
        }

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return recoveryAllowed(prefs)
                && prefs.getInt(ATTEMPTS, 0) < MAX_ATTEMPTS;
    }

    private static boolean recoveryAllowed(SharedPreferences prefs) {
        return prefs.getBoolean(ENABLED, false)
                && prefs.getBoolean(CONSENT, false);
    }

    private static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }

        boolean ok() {
            return exitCode == 0;
        }
    }
}
