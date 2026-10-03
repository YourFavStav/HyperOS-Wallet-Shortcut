package com.miui.tsmclient;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

public class LauncherActivity extends Activity {
    private static final String SETUP_COMMAND =
            "settings put system double_click_power_key mi_pay";

    private static final String SHORTCUT_VALUE = "mi_pay";
    private static final String PREFS = "shortcut_prefs";
    private static final String PREF_PREVIOUS = "previous_double_click_power_key";

    private static final int SHIZUKU_REQUEST_CODE = 1001;
    private static final int OP_NONE = 0;
    private static final int OP_ENABLE = 1;
    private static final int OP_RESTORE = 2;

    private boolean dark;
    private int bg, card, text, secondary, accent, border, success, warning;

    private TextView shortcutStatus;
    private TextView shizukuStatus;
    private Button enableButton;
    private Button restoreButton;

    private int pendingOperation = OP_NONE;
    private IBinder userServiceBinder;
    private boolean userServiceBound;

    private Shizuku.UserServiceArgs userServiceArgs;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            this::refreshStatuses;

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        userServiceBinder = null;
        userServiceBound = false;
        refreshStatuses();
    };

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE) return;

                refreshStatuses();

                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindForPendingOperation();
                } else {
                    pendingOperation = OP_NONE;
                    setBusy(false, null);
                    Toast.makeText(
                            this,
                            "Shizuku permission was not granted.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            };

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            userServiceBinder = service;
            userServiceBound = true;

            final int operation = pendingOperation;
            pendingOperation = OP_NONE;

            new Thread(() -> {
                try {
                    if (operation == OP_ENABLE) {
                        performEnable(service);
                    } else if (operation == OP_RESTORE) {
                        performRestore(service);
                    }
                } finally {
                    runOnUiThread(() -> {
                        setBusy(false, null);
                        refreshStatuses();
                        unbindUserServiceSafely();
                    });
                }
            }, "wallet-shortcut-user-service").start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            userServiceBinder = null;
            userServiceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            bg = Color.rgb(15, 17, 21);
            card = Color.rgb(25, 28, 34);
            text = Color.rgb(245, 247, 250);
            secondary = Color.rgb(174, 180, 188);
            accent = Color.rgb(138, 180, 248);
            border = Color.rgb(48, 52, 60);
            success = Color.rgb(129, 201, 149);
            warning = Color.rgb(253, 214, 99);
        } else {
            bg = Color.rgb(246, 247, 249);
            card = Color.WHITE;
            text = Color.rgb(24, 26, 30);
            secondary = Color.rgb(96, 101, 110);
            accent = Color.rgb(26, 115, 232);
            border = Color.rgb(226, 230, 235);
            success = Color.rgb(19, 115, 51);
            warning = Color.rgb(168, 91, 0);
        }

        userServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(this, ShortcutUserService.class)
        )
                .daemon(false)
                .processNameSuffix("wallet_shortcut")
                .tag("wallet-shortcut-settings")
                .version(1);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setContentView(buildUi());

        refreshStatuses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatuses();
    }

    @Override
    protected void onDestroy() {
        unbindUserServiceSafely();

        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);

        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = vertical();
        root.setPadding(dp(20), dp(24), dp(20), dp(32));

        TextView eyebrow = label("HYPEROS UTILITY", 12, accent);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(eyebrow);

        TextView title = label("Wallet Shortcut", 30, text);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, dp(5), 0, dp(6));
        root.addView(title);

        TextView subtitle = label(
                "Double-press the physical power button to open Google Wallet on compatible HyperOS devices.",
                16, secondary);
        subtitle.setLineSpacing(0, 1.15f);
        root.addView(subtitle);

        space(root, 18);

        boolean walletInstalled = isWalletInstalled();
        root.addView(statusPill(
                walletInstalled ? "●  Google Wallet detected" : "●  Google Wallet not detected",
                walletInstalled
        ));

        space(root, 10);

        shizukuStatus = statusPill("●  Checking Shizuku…", false);
        root.addView(shizukuStatus);

        space(root, 14);

        Button test = actionButton("Test Google Wallet", false);
        test.setOnClickListener(v -> {
            if (!WalletLauncher.open(this)) {
                Toast.makeText(
                        this,
                        "Google Wallet could not be opened.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        root.addView(test);

        space(root, 24);

        LinearLayout control = sectionCard();
        control.addView(sectionTitle("Power shortcut"));

        shortcutStatus = statusPill("●  Checking shortcut status…", false);
        control.addView(shortcutStatus);

        space(control, 12);

        control.addView(body(
                "Shizuku is only needed while enabling or restoring the HyperOS shortcut. "
                        + "After setup succeeds, Shizuku can be stopped."
        ));

        space(control, 14);

        enableButton = actionButton("Enable shortcut with Shizuku", true);
        enableButton.setOnClickListener(v -> startOperation(OP_ENABLE));
        control.addView(enableButton);

        space(control, 10);

        restoreButton = actionButton("Restore previous shortcut", false);
        restoreButton.setOnClickListener(v -> startOperation(OP_RESTORE));
        control.addView(restoreButton);

        root.addView(control);
        space(root, 18);

        addSection(root, "How it works",
                "Power double press  →  HyperOS “mi_pay”  →  Wallet Shortcut  →  Google Wallet\n\n"
                        + "HyperOS contains an internal shortcut named “mi_pay”. "
                        + "When you tap Enable, this app temporarily uses Shizuku's shell identity "
                        + "to set that shortcut. The normal power-button shortcut does not depend on "
                        + "Shizuku after setup.");

        LinearLayout fallback = sectionCard();
        fallback.addView(sectionTitle("Manual fallback"));
        fallback.addView(body(
                "If the built-in Shizuku setup fails on a device, the original aShell method is still available:"
        ));

        space(fallback, 10);

        TextView command = label(SETUP_COMMAND, 13, text);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setPadding(dp(13), dp(13), dp(13), dp(13));
        command.setBackground(round(
                dark ? Color.rgb(17, 19, 23) : Color.rgb(242, 244, 247),
                12, 1, border));
        fallback.addView(command);

        space(fallback, 10);

        Button copy = actionButton("Copy fallback command", false);
        copy.setOnClickListener(v -> copyCommand());
        fallback.addView(copy);

        root.addView(fallback);
        space(root, 18);

        addSection(root, "Compatibility & privacy",
                "No root is required. This app contains no analytics and does not access your cards, NFC payment data or Google account.\n\n"
                        + "Shizuku provides shell-level access while it is running. This app uses that access only when you tap Enable or Restore, "
                        + "to read or change HyperOS's double-click power setting.\n\n"
                        + "Do not install this app on a device that already contains Xiaomi's real com.miui.tsmclient / Mi Pay package.");

        Button openShizuku = actionButton("Open Shizuku", false);
        openShizuku.setOnClickListener(v -> openShizuku());
        root.addView(openShizuku);

        space(root, 10);

        Button playStore = actionButton("Google Wallet on Play Store", false);
        playStore.setOnClickListener(v -> openWalletListing());
        root.addView(playStore);

        space(root, 10);

        Button appSettings = actionButton("App settings", false);
        appSettings.setOnClickListener(v -> startActivity(
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())
                )
        ));
        root.addView(appSettings);

        space(root, 24);

        TextView footer = label(
                "v1.1.0  •  No root  •  No card access  •  No analytics\n"
                        + "Unofficial project — not affiliated with Xiaomi, Google or Shizuku.",
                12, secondary);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(0, 1.3f);
        root.addView(footer);

        scroll.addView(root);
        return scroll;
    }

    private void startOperation(int operation) {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(
                    this,
                    "Shizuku is not running. Start Shizuku first, then return here.",
                    Toast.LENGTH_LONG
            ).show();
            refreshStatuses();
            return;
        }

        try {
            if (Shizuku.isPreV11()) {
                Toast.makeText(
                        this,
                        "This Shizuku version is too old.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            pendingOperation = operation;
            setBusy(true, operation == OP_ENABLE ? "Preparing setup…" : "Preparing restore…");

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindForPendingOperation();
                return;
            }

            if (Shizuku.shouldShowRequestPermissionRationale()) {
                pendingOperation = OP_NONE;
                setBusy(false, null);
                Toast.makeText(
                        this,
                        "Shizuku permission is denied. Allow this app inside Shizuku and try again.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
        } catch (Throwable e) {
            pendingOperation = OP_NONE;
            setBusy(false, null);
            Toast.makeText(
                    this,
                    "Could not talk to Shizuku: " + safeMessage(e),
                    Toast.LENGTH_LONG
            ).show();
            refreshStatuses();
        }
    }

    private void bindForPendingOperation() {
        if (pendingOperation == OP_NONE) return;

        try {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection);
        } catch (Throwable e) {
            pendingOperation = OP_NONE;
            setBusy(false, null);
            Toast.makeText(
                    this,
                    "Could not start Shizuku UserService: " + safeMessage(e),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void performEnable(IBinder service) {
        RemoteResult before = remoteGet(service);

        if (!before.ok()) {
            showRemoteFailure("Could not read the current shortcut.", before);
            return;
        }

        String previous = normalizeSetting(before.output);
        if (previous.length() == 0) previous = "none";

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!SHORTCUT_VALUE.equals(previous) && !prefs.contains(PREF_PREVIOUS)) {
            prefs.edit().putString(PREF_PREVIOUS, previous).apply();
        }

        RemoteResult write = remotePut(service, SHORTCUT_VALUE);
        if (!write.ok()) {
            showRemoteFailure("Could not enable the shortcut.", write);
            return;
        }

        RemoteResult verify = remoteGet(service);
        boolean enabled = verify.ok()
                && SHORTCUT_VALUE.equals(normalizeSetting(verify.output));

        runOnUiThread(() -> {
            if (enabled) {
                Toast.makeText(
                        this,
                        "Shortcut enabled. You can stop Shizuku now.",
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Toast.makeText(
                        this,
                        "The command ran, but HyperOS did not keep the mi_pay setting.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void performRestore(IBinder service) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        if (!prefs.contains(PREF_PREVIOUS)) {
            runOnUiThread(() -> Toast.makeText(
                    this,
                    "No previous shortcut was saved by this app.",
                    Toast.LENGTH_LONG
            ).show());
            return;
        }

        String previous = prefs.getString(PREF_PREVIOUS, "none");
        if (previous == null || previous.length() == 0) previous = "none";

        RemoteResult write = remotePut(service, previous);
        if (!write.ok()) {
            showRemoteFailure("Could not restore the previous shortcut.", write);
            return;
        }

        RemoteResult verify = remoteGet(service);
        boolean restored = verify.ok()
                && previous.equals(normalizeSetting(verify.output));

        if (restored) {
            prefs.edit().remove(PREF_PREVIOUS).apply();
        }

        final boolean finalRestored = restored;
        runOnUiThread(() -> {
            if (finalRestored) {
                Toast.makeText(
                        this,
                        "Previous shortcut restored.",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this,
                        "The restore command ran, but the value did not match.",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private RemoteResult remoteGet(IBinder service) {
        return transact(service, ShortcutUserService.TRANSACTION_GET, null);
    }

    private RemoteResult remotePut(IBinder service, String value) {
        return transact(service, ShortcutUserService.TRANSACTION_PUT, value);
    }

    private RemoteResult transact(IBinder service, int code, String value) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            if (value != null) data.writeString(value);

            boolean handled = service.transact(code, data, reply, 0);
            if (!handled) {
                return new RemoteResult(-1, "", "UserService did not handle the request.");
            }

            int exitCode = reply.readInt();
            String output = reply.readString();
            String error = reply.readString();

            return new RemoteResult(
                    exitCode,
                    output == null ? "" : output,
                    error == null ? "" : error
            );
        } catch (Throwable e) {
            return new RemoteResult(-1, "", safeMessage(e));
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private void showRemoteFailure(String prefix, RemoteResult result) {
        String detail = firstNonEmpty(result.error, result.output);
        final String message = detail.length() == 0 ? prefix : prefix + " " + detail;

        runOnUiThread(() -> Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show());
    }

    private void unbindUserServiceSafely() {
        if (!userServiceBound && userServiceBinder == null) return;

        try {
            Shizuku.unbindUserService(
                    userServiceArgs,
                    userServiceConnection,
                    true
            );
        } catch (Throwable ignored) {
        }

        userServiceBinder = null;
        userServiceBound = false;
    }

    private void refreshStatuses() {
        runOnUiThread(() -> {
            if (shortcutStatus != null) {
                String localValue = readShortcutValueLocally();
                boolean enabled = SHORTCUT_VALUE.equals(localValue);

                styleStatus(
                        shortcutStatus,
                        enabled
                                ? "●  Power-button shortcut enabled"
                                : "●  Power-button shortcut not enabled",
                        enabled
                );
            }

            if (shizukuStatus != null) {
                boolean running = Shizuku.pingBinder();

                if (!running) {
                    styleStatus(shizukuStatus, "●  Shizuku not running", false);
                } else {
                    boolean granted = false;

                    try {
                        granted = Shizuku.checkSelfPermission()
                                == PackageManager.PERMISSION_GRANTED;
                    } catch (Throwable ignored) {
                    }

                    styleStatus(
                            shizukuStatus,
                            granted
                                    ? "●  Shizuku ready"
                                    : "●  Shizuku running — permission needed",
                            granted
                    );
                }
            }
        });
    }

    private String readShortcutValueLocally() {
        try {
            String value = Settings.System.getString(
                    getContentResolver(),
                    "double_click_power_key"
            );
            return value == null ? "" : value.trim();
        } catch (Throwable e) {
            return "";
        }
    }

    private String normalizeSetting(String value) {
        if (value == null) return "";

        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized)) return "";
        return normalized;
    }

    private void setBusy(boolean busy, String label) {
        if (enableButton != null) {
            enableButton.setEnabled(!busy);
            enableButton.setText(
                    busy && label != null
                            ? label
                            : "Enable shortcut with Shizuku"
            );
        }

        if (restoreButton != null) {
            restoreButton.setEnabled(!busy);
        }
    }

    private void openShizuku() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(
                "moe.shizuku.privileged.api"
        );

        if (intent == null) {
            Toast.makeText(
                    this,
                    "Shizuku is not installed.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(
                    this,
                    "Could not open Shizuku.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void addSection(LinearLayout root, String title, String content) {
        LinearLayout cardView = sectionCard();
        cardView.addView(sectionTitle(title));
        cardView.addView(body(content));
        root.addView(cardView);
        space(root, 18);
    }

    private LinearLayout sectionCard() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(18), dp(18), dp(18), dp(18));
        layout.setBackground(round(card, 18, 1, border));
        layout.setElevation(dp(1));
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return layout;
    }

    private TextView sectionTitle(String value) {
        TextView view = label(value, 18, text);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    private TextView body(String value) {
        TextView view = label(value, 15, secondary);
        view.setLineSpacing(0, 1.22f);
        return view;
    }

    private TextView statusPill(String value, boolean good) {
        TextView view = label(value, 14, good ? success : warning);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackground(round(
                good
                        ? (dark ? Color.rgb(27, 36, 31) : Color.rgb(232, 245, 235))
                        : (dark ? Color.rgb(48, 38, 22) : Color.rgb(255, 244, 229)),
                18,
                0,
                Color.TRANSPARENT
        ));
        return view;
    }

    private void styleStatus(TextView view, String value, boolean good) {
        view.setText(value);
        view.setTextColor(good ? success : warning);
        view.setBackground(round(
                good
                        ? (dark ? Color.rgb(27, 36, 31) : Color.rgb(232, 245, 235))
                        : (dark ? Color.rgb(48, 38, 22) : Color.rgb(255, 244, 229)),
                18,
                0,
                Color.TRANSPARENT
        ));
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button actionButton(String textValue, boolean primary) {
        Button button = new Button(this);
        button.setText(textValue);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(
                primary
                        ? (dark ? Color.rgb(15, 17, 21) : Color.WHITE)
                        : text
        );
        button.setBackground(round(
                primary ? accent : card,
                15,
                primary ? 0 : 1,
                primary ? Color.TRANSPARENT : border
        ));
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));
        return button;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable round(
            int fill,
            float radius,
            int strokeWidth,
            int strokeColor
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));

        if (strokeWidth > 0) {
            drawable.setStroke(dp(strokeWidth), strokeColor);
        }

        return drawable;
    }

    private void space(LinearLayout parent, int height) {
        View spacer = new View(this);
        spacer.setLayoutParams(
                new LinearLayout.LayoutParams(1, dp(height))
        );
        parent.addView(spacer);
    }

    private void copyCommand() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            "HyperOS Wallet Shortcut fallback",
                            SETUP_COMMAND
                    )
            );

            Toast.makeText(
                    this,
                    "Fallback command copied.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private boolean isWalletInstalled() {
        try {
            getPackageManager().getPackageInfo(
                    WalletLauncher.WALLET_PACKAGE,
                    0
            );
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openWalletListing() {
        Uri marketUri = Uri.parse(
                "market://details?id=" + WalletLauncher.WALLET_PACKAGE
        );

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, marketUri));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                            "https://play.google.com/store/apps/details?id="
                                    + WalletLauncher.WALLET_PACKAGE
                    )
            ));
        }
    }

    private String safeMessage(Throwable e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.trim().length() == 0
                ? current.getClass().getSimpleName()
                : message.trim();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }

        return "";
    }

    private int dp(float value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    private static final class RemoteResult {
        final int exitCode;
        final String output;
        final String error;

        RemoteResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }

        boolean ok() {
            return exitCode == 0;
        }
    }
}
