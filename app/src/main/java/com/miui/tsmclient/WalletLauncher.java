package com.miui.tsmclient;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

final class WalletLauncher {
    static final String WALLET_PACKAGE =
            "com.google.android.apps.walletnfcrel";

    static final String WALLET_ACTIVITY =
            "com.google.commerce.tapandpay.android.wallet.WalletActivity";

    static final String QUICKDRAW_ACTION =
            "com.google.android.apps.wallet.main.QUICKDRAW";

    private WalletLauncher() {}

    static boolean open(Activity activity) {
        if (isDeviceLocked(activity) && openQuickDraw(activity)) {
            return true;
        }

        return openNormalWallet(activity);
    }

    private static boolean isDeviceLocked(Activity activity) {
        KeyguardManager keyguardManager =
                (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);

        return keyguardManager != null && keyguardManager.isKeyguardLocked();
    }

    private static boolean openQuickDraw(Activity activity) {
        Intent wallet = new Intent(QUICKDRAW_ACTION);
        wallet.setPackage(WALLET_PACKAGE);
        wallet.addCategory(Intent.CATEGORY_DEFAULT);
        wallet.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        try {
            activity.startActivity(wallet);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private static boolean openNormalWallet(Activity activity) {
        Intent wallet = new Intent();
        wallet.setClassName(WALLET_PACKAGE, WALLET_ACTIVITY);
        wallet.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        try {
            activity.startActivity(wallet);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }
}
