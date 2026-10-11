package com.miui.tsmclient;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

final class WalletLauncher {
    static final String WALLET_PACKAGE =
            "com.google.android.apps.walletnfcrel";

    static final String WALLET_ACTIVITY =
            "com.google.commerce.tapandpay.android.wallet.WalletActivity";

    static final String QUICKDRAW_ACTION =
            "com.google.android.apps.wallet.main.QUICKDRAW";

    enum LaunchResult {
        SUCCESS,
        WALLET_NOT_INSTALLED,
        ACTIVITY_NOT_FOUND,
        BLOCKED_BY_ANDROID,
        UNKNOWN_ERROR
    }

    private WalletLauncher() {}

    static LaunchResult open(Activity activity) {
        if (!isWalletInstalled(activity)) {
            return LaunchResult.WALLET_NOT_INSTALLED;
        }

        if (isDeviceLocked(activity)) {
            LaunchResult quickDrawResult = openQuickDraw(activity);

            if (quickDrawResult == LaunchResult.SUCCESS) {
                return LaunchResult.SUCCESS;
            }
        }

        return openNormalWallet(activity);
    }

    static String errorMessage(LaunchResult result) {
        switch (result) {
            case WALLET_NOT_INSTALLED:
                return "Google Wallet is not installed.";

            case ACTIVITY_NOT_FOUND:
                return "Google Wallet could not be opened.";

            case BLOCKED_BY_ANDROID:
                return "Android blocked Google Wallet from opening.";

            case UNKNOWN_ERROR:
                return "Something went wrong while opening Google Wallet.";

            case SUCCESS:
            default:
                return "";
        }
    }

    private static boolean isWalletInstalled(Activity activity) {
        try {
            activity.getPackageManager().getApplicationInfo(WALLET_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private static boolean isDeviceLocked(Activity activity) {
        KeyguardManager keyguardManager =
                (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);

        return keyguardManager != null && keyguardManager.isKeyguardLocked();
    }

    private static LaunchResult openQuickDraw(Activity activity) {
        Intent wallet = new Intent(QUICKDRAW_ACTION);
        wallet.setPackage(WALLET_PACKAGE);
        wallet.addCategory(Intent.CATEGORY_DEFAULT);
        wallet.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        return launch(activity, wallet);
    }

    private static LaunchResult openNormalWallet(Activity activity) {
        Intent wallet = new Intent();
        wallet.setClassName(WALLET_PACKAGE, WALLET_ACTIVITY);
        wallet.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        return launch(activity, wallet);
    }

    private static LaunchResult launch(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return LaunchResult.SUCCESS;
        } catch (ActivityNotFoundException ignored) {
            return LaunchResult.ACTIVITY_NOT_FOUND;
        } catch (SecurityException ignored) {
            return LaunchResult.BLOCKED_BY_ANDROID;
        } catch (RuntimeException ignored) {
            return LaunchResult.UNKNOWN_ERROR;
        }
    }
}
