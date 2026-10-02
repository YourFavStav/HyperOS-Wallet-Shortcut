# HyperOS Wallet Shortcut

A tiny, rootless compatibility shim that lets supported Xiaomi/Redmi HyperOS devices use the physical **Power button double-press** shortcut to open **Google Wallet** in regions/devices where HyperOS exposes its Mi Pay shortcut but Mi Pay itself is unavailable.

> Unofficial project. Not affiliated with Xiaomi or Google.

## How it works

On the tested HyperOS firmware, Xiaomi's power-key shortcut dispatcher recognizes the internal function identifier `mi_pay`. That path sends an intent with action:

```text
com.miui.intent.action.DOUBLE_CLICK
```

to package:

```text
com.miui.tsmclient
```

This app intentionally uses that package name, receives the shortcut intent, and immediately launches the installed Google Wallet activity:

```text
com.google.android.apps.walletnfcrel/com.google.commerce.tapandpay.android.wallet.WalletActivity
```

The important discovery is that **`mi_pay` is the shortcut function identifier**. `launchMiPay` is the private framework method name and is not the value HyperOS expects in `double_click_power_key`.

## Setup

### Requirements

- A Xiaomi/Redmi device whose HyperOS build contains the Mi Pay power-key shortcut path.
- Google Wallet installed.
- A shell with permission to modify the relevant system setting. The tested setup used **Shizuku + aShell**.
- No root is required for the tested setup.

### 1. Install the APK

Install a release build of HyperOS Wallet Shortcut. Because the app intentionally uses `com.miui.tsmclient`, installation will conflict with Xiaomi's real Mi Pay/TSM Client if that package is already installed on your device.

### 2. Configure Power ×2

In a Shizuku-authorized shell such as aShell, run:

```sh
settings put system double_click_power_key mi_pay
```

Verify it with:

```sh
settings get system double_click_power_key
```

Expected output:

```text
mi_pay
```

### 3. Test

Double-press the physical Power button. HyperOS should invoke its Mi Pay shortcut and this app should forward it to Google Wallet.

You can also open the app normally and tap **Test Google Wallet** to verify the forwarding side independently.

## Restore the camera shortcut

If your device previously used Power ×2 for Camera, the tested HyperOS value was:

```sh
settings put system double_click_power_key launch_camera
```

Exact available shortcut values can vary by firmware.

## Compatibility

This is an undocumented HyperOS implementation detail, not a public Android API. Xiaomi can change or remove it in a system update. A device is not automatically compatible merely because it runs HyperOS.

The initial proof of concept was developed and tested on a Redmi device running Android 16 / HyperOS 3. Additional device/firmware reports are welcome.

## Troubleshooting

### Opening the app works, but Power ×2 does nothing

Check:

```sh
settings get system double_click_power_key
```

It must be `mi_pay` on the firmware this workaround was designed for.

For debugging, clear logs, press Power twice, then inspect the Xiaomi input shortcut messages:

```sh
logcat -c
# Double-press Power, then:
logcat -d -v time | grep -i -E "ShortCutActionsUtils|com.miui.tsmclient|mi_pay|double_click_power|MiuiInputKeyEventLog|ActivityTaskManager"
```

### Google Wallet does not open from the app

Make sure the Google Wallet package `com.google.android.apps.walletnfcrel` is installed and enabled. Google can change internal activity names in future Wallet versions; if that happens, the forwarding component may need to be updated.

### `com.miui.tsmclient` is already installed

This project cannot be installed alongside another app using the same package name. Do not remove a system payment app merely to install this project unless you understand the consequences for your device and region.

## Privacy

The app has no network permission and does not read or store payment information. It only forwards the shortcut to Google Wallet and provides a local setup screen.

## Building

Open the repository in Android Studio with Android SDK 35 installed, then build the `app` module. The project uses Java and has no third-party runtime dependencies.

Do **not** commit signing keys. Use your own private release key for releases.

## Repository hygiene

Proprietary Xiaomi framework JARs, decompiled framework sources, DEX files, signing keys, and local reverse-engineering artifacts are intentionally **not** included in this repository.

## License

Apache-2.0. See `LICENSE`.
