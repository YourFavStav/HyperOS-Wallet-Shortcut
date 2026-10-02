package com.miui.tsmclient;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class LauncherActivity extends Activity {
    private static final String SETUP_COMMAND =
            "settings put system double_click_power_key mi_pay";

    private boolean dark;
    private int bg, card, text, secondary, accent, border, success, warning;

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

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        setContentView(buildUi());
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
        TextView status = label(
                walletInstalled ? "●  Google Wallet detected" : "●  Google Wallet not detected",
                14,
                walletInstalled ? success : warning);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(dp(14), dp(10), dp(14), dp(10));
        status.setBackground(round(
                dark ? Color.rgb(27, 36, 31) : Color.rgb(232, 245, 235),
                18, 0, Color.TRANSPARENT));
        root.addView(status);

        space(root, 14);

        Button test = actionButton("Test Google Wallet", true);
        test.setOnClickListener(v -> {
            if (!WalletLauncher.open(this)) {
                Toast.makeText(this, "Google Wallet could not be opened.", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(test);

        space(root, 24);

        addSection(root, "About this project",
                "HyperOS Wallet Shortcut is a tiny utility that lets compatible Xiaomi, Redmi and POCO devices use the physical power-button double press to launch Google Wallet.\n\n"
                        + "The app does not handle payments itself and does not access your cards, NFC data or Google Wallet account.");

        addSection(root, "How it works",
                "Power ×2  →  HyperOS “mi_pay”  →  Wallet Shortcut  →  Google Wallet\n\n"
                        + "HyperOS contains an internal shortcut named “mi_pay”. When Power is double-pressed, HyperOS tries to open Xiaomi's wallet package.\n\n"
                        + "This app intentionally uses the package name com.miui.tsmclient, receives that shortcut intent, and immediately forwards it to Google Wallet.");

        LinearLayout setup = sectionCard();
        setup.addView(sectionTitle("Setup"));
        setup.addView(body("Run this command once using a Shizuku-authorized shell such as aShell:"));
        space(setup, 12);

        TextView command = label(SETUP_COMMAND, 13, text);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setPadding(dp(13), dp(13), dp(13), dp(13));
        command.setBackground(round(
                dark ? Color.rgb(17, 19, 23) : Color.rgb(242, 244, 247),
                12, 1, border));
        setup.addView(command);

        space(setup, 10);

        Button copy = actionButton("Copy setup command", false);
        copy.setOnClickListener(v -> copyCommand());
        setup.addView(copy);

        root.addView(setup);
        space(root, 18);

        addSection(root, "Compatibility & privacy",
                "No root is required. This app contains no analytics and does not collect payment, card or personal data.\n\n"
                        + "The shortcut relies on an internal HyperOS implementation, so a future Xiaomi update could change or remove it.\n\n"
                        + "Do not install this app on a device that already contains Xiaomi's real com.miui.tsmclient / Mi Pay package.");

        Button playStore = actionButton("Google Wallet on Play Store", false);
        playStore.setOnClickListener(v -> openWalletListing());
        root.addView(playStore);

        space(root, 10);

        Button appSettings = actionButton("App settings", false);
        appSettings.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));
        root.addView(appSettings);

        space(root, 24);

        TextView footer = label(
                "No root  •  No card access  •  No analytics\nUnofficial project — not affiliated with Xiaomi or Google.",
                12, secondary);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(0, 1.3f);
        root.addView(footer);

        scroll.addView(root);
        return scroll;
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
                ViewGroup.LayoutParams.WRAP_CONTENT));
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
        button.setTextColor(primary
                ? (dark ? Color.rgb(15, 17, 21) : Color.WHITE)
                : text);
        button.setBackground(round(
                primary ? accent : card,
                15,
                primary ? 0 : 1,
                primary ? Color.TRANSPARENT : border));
        button.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)));
        return button;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private GradientDrawable round(int fill, float radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private void space(LinearLayout parent, int height) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        parent.addView(space);
    }

    private void copyCommand() {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("HyperOS Wallet Shortcut setup", SETUP_COMMAND));
            Toast.makeText(this, "Setup command copied.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isWalletInstalled() {
        try {
            getPackageManager().getPackageInfo(WalletLauncher.WALLET_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openWalletListing() {
        Uri marketUri = Uri.parse("market://details?id=" + WalletLauncher.WALLET_PACKAGE);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, marketUri));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="
                            + WalletLauncher.WALLET_PACKAGE)));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
