package com.miui.tsmclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Toast;

final class LaunchErrorReporter {
    private static final String CHANNEL_ID = "wallet_launch_errors";
    private static final String POST_NOTIFICATIONS_PERMISSION =
            "android.permission.POST_NOTIFICATIONS";
    private static final int NOTIFICATION_ID = 1201;

    private LaunchErrorReporter() {}

    static void report(Context context, WalletLauncher.LaunchResult result) {
        if (result == WalletLauncher.LaunchResult.SUCCESS) {
            return;
        }

        String message = WalletLauncher.errorMessage(result);

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null || !canNotify(context, manager)) {
            showToast(context, message);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Wallet shortcut errors",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription(
                    "Warnings when the power-button shortcut cannot open Google Wallet."
            );

            manager.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, LauncherActivity.class);
        openApp.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
            builder.setPriority(Notification.PRIORITY_HIGH);
        }

        builder
                .setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle("Wallet shortcut failed")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ERROR);

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            showToast(context, message);
        }
    }

    private static boolean canNotify(
            Context context,
            NotificationManager manager
    ) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(POST_NOTIFICATIONS_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && !manager.areNotificationsEnabled()) {
            return false;
        }

        return true;
    }

    private static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
