package com.miui.tsmclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootRestoreReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null ||
                !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        boolean recoveryEnabled = context.getSharedPreferences(
                "shortcut_prefs", Context.MODE_PRIVATE
        ).getBoolean("auto_restore_enabled", false)
                && context.getSharedPreferences(
                        "shortcut_prefs", Context.MODE_PRIVATE
                ).getBoolean("auto_restore_prompted_v1", false);

        Log.i("WalletBootRestore", "Boot received; enabled=" + recoveryEnabled);
        if (!recoveryEnabled) return;

        context.getSharedPreferences(
                "shortcut_prefs", Context.MODE_PRIVATE
        ).edit().putInt("boot_restore_attempts", 0).commit();

        BootRestoreScheduler.schedule(context);
    }
}
