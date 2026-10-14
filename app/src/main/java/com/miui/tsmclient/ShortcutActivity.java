package com.miui.tsmclient;

import android.app.Activity;
import android.os.Bundle;

/** Receives HyperOS' Mi Pay shortcut intent and forwards it to Google Wallet. */
public class ShortcutActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WalletLauncher.LaunchResult result = WalletLauncher.open(this);

        if (result != WalletLauncher.LaunchResult.SUCCESS) {
            LaunchErrorReporter.report(this, result);
        }

        finish();
    }
}
