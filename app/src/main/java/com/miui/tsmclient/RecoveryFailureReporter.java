package com.miui.tsmclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

final class RecoveryFailureReporter {
    private static final String TAG = "WalletBootRestore";
    private static final String PREFS = "shortcut_prefs";
    private static final String ERROR = "boot_restore_last_error";
    private static final String CHANNEL = "wallet_recovery_errors";
    private static final int NOTIFICATION_ID = 1206;

    private RecoveryFailureReporter() {}

    static String getWarning(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(ERROR, "");
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(ERROR).apply();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            try {
                manager.cancel(NOTIFICATION_ID);
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not dismiss recovery alert", e);
            }
        }
    }

    static void report(Context context, String message) {
        // Save first, so the warning survives even without permission.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(ERROR, message).apply();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(
                        "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= 24 &&
                    !manager.areNotificationsEnabled()) {
                return;
            }

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL,
                        "Automatic recovery alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription(
                        "Problems restoring the power-button shortcut after reboot."
                );
                manager.createNotificationChannel(channel);

                NotificationChannel existing =
                        manager.getNotificationChannel(CHANNEL);
                if (existing != null &&
                        existing.getImportance()
                                == NotificationManager.IMPORTANCE_NONE) {
                    return;
                }
            }

            Intent intent = new Intent(context, LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent openApp = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                            | PendingIntent.FLAG_IMMUTABLE
            );

            Notification.Builder builder =
                    Build.VERSION.SDK_INT >= 26
                            ? new Notification.Builder(context, CHANNEL)
                            : new Notification.Builder(context);

            builder.setSmallIcon(context.getApplicationInfo().icon)
                    .setContentTitle("Automatic shortcut recovery failed")
                    .setContentText(message)
                    .setStyle(new Notification.BigTextStyle().bigText(message))
                    .setContentIntent(openApp)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ERROR);

            if (Build.VERSION.SDK_INT < 26) {
                builder.setPriority(Notification.PRIORITY_DEFAULT);
            }

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (RuntimeException e) {
            // The persistent in-app warning remains available.
            Log.w(TAG, "Could not display recovery notification", e);
        }
    }
}
