package com.miui.tsmclient;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;

final class WalletLauncher {
    static final String WALLET_PACKAGE = "com.google.android.apps.walletnfcrel";
    static final String WALLET_ACTIVITY =
            "com.google.commerce.tapandpay.android.wallet.WalletActivity";

    private WalletLauncher() {}

    static boolean open(Activity activity) {
        Intent wallet = new Intent();
        wallet.setClassName(WALLET_PACKAGE, WALLET_ACTIVITY);
        wallet.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        try {
            activity.startActivity(wallet);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }
}
