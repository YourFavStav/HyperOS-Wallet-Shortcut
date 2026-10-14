package com.miui.tsmclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Shared update logic for the foreground UI and persisted background job. */
final class UpdateChecker {
    private static final String PREFS = "shortcut_prefs";
    private static final String UPDATE_INFO_URL =
            "https://raw.githubusercontent.com/YourFavStav/HyperOS-Wallet-Shortcut/main/update.json";
    private static final String RELEASES_URL =
            "https://github.com/YourFavStav/HyperOS-Wallet-Shortcut/releases/latest";

    // Keep the keys used by v1.2.0's existing in-app checker.
    private static final String PREF_LAST_CHECK = "last_update_check_v1";
    private static final String PREF_CACHED_CODE = "cached_update_code_v1";
    private static final String PREF_CACHED_NAME = "cached_update_name_v1";
    private static final String PREF_NOTIFIED_CODE = "last_update_notification_version_code_v1";
    private static final long INTERVAL_MS = 12L * 60L * 60L * 1000L;
    private static final String CHANNEL_ID = "wallet_app_updates";
    private static final int NOTIFICATION_ID = 1203;

    private UpdateChecker() {}

    static final class UpdateInfo {
        final int versionCode;
        final String versionName;

        UpdateInfo(int versionCode, String versionName) {
            this.versionCode = versionCode;
            this.versionName = versionName == null ? "" : versionName;
        }
    }

    static int installedVersionCode(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            return -1;
        }
    }

    static UpdateInfo cached(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int code = prefs.getInt(PREF_CACHED_CODE, -1);
        return code > 0 ? new UpdateInfo(code, prefs.getString(PREF_CACHED_NAME, "")) : null;
    }

    /** Synchronized to avoid two checks racing when the app opens during a job. */
    static synchronized UpdateInfo fetchIfDue(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L);
        long now = System.currentTimeMillis();
        if (lastCheck > 0 && now >= lastCheck && now - lastCheck < INTERVAL_MS) {
            return cached(context);
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(UPDATE_INFO_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "HyperOS-Wallet-Shortcut");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return cached(context);
            }

            StringBuilder payload = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) payload.append(line);
            }
            JSONObject json = new JSONObject(payload.toString());
            int code = json.getInt("versionCode");
            if (code <= 0) return cached(context);
            String name = json.optString("versionName", "").trim();
            prefs.edit()
                    .putInt(PREF_CACHED_CODE, code)
                    .putString(PREF_CACHED_NAME, name)
                    .putLong(PREF_LAST_CHECK, System.currentTimeMillis())
                    .apply();
            return new UpdateInfo(code, name);
        } catch (Exception ignored) {
            // No network or malformed metadata is never fatal.
            return cached(context);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Send at most one update alert for each newer version code. */
    static synchronized void notifyIfNewer(Context context, UpdateInfo update) {
        int current = installedVersionCode(context);
        if (current < 0 || update.versionCode <= current) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getInt(PREF_NOTIFIED_CODE, -1) >= update.versionCode) return;

        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "App update notifications", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Notify when a new HyperOS Wallet Shortcut release is available.");
            manager.createNotificationChannel(channel);
            NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
            if (existing != null
                    && existing.getImportance() == NotificationManager.IMPORTANCE_NONE) return;
        }

        Intent release = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL));
        PendingIntent openRelease = PendingIntent.getActivity(
                context, 0, release,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String name = update.versionName.length() == 0
                ? "A new version" : "Version " + update.versionName;
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle("Wallet Shortcut update available")
                .setContentText(name + " is available. Tap to view the release.")
                .setContentIntent(openRelease)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS);
        if (Build.VERSION.SDK_INT < 26) {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build());
            prefs.edit().putInt(PREF_NOTIFIED_CODE, update.versionCode).apply();
        } catch (RuntimeException ignored) {
            // A missing notification channel or vendor restriction is not fatal.
        }
    }
}
