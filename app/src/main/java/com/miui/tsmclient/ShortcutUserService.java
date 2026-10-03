package com.miui.tsmclient;

import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class ShortcutUserService extends Binder {
    public static final int TRANSACTION_GET = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TRANSACTION_PUT = IBinder.FIRST_CALL_TRANSACTION + 1;

    // Shizuku's reserved UserService destroy transaction code.
    private static final int TRANSACTION_DESTROY = 16777115;

    private static final String SETTINGS = "/system/bin/settings";
    private static final String NAMESPACE = "system";
    private static final String KEY = "double_click_power_key";

    public ShortcutUserService() {
    }

    // Shizuku v13+ tries this constructor first.
    public ShortcutUserService(Context context) {
    }

    @Override
    protected boolean onTransact(
            int code,
            Parcel data,
            Parcel reply,
            int flags
    ) throws RemoteException {
        if (code == TRANSACTION_GET) {
            CommandResult result = runCommand(
                    SETTINGS,
                    "get",
                    NAMESPACE,
                    KEY
            );
            writeResult(reply, result);
            return true;
        }

        if (code == TRANSACTION_PUT) {
            String value = data.readString();

            if (value == null || value.length() == 0) {
                writeResult(
                        reply,
                        new CommandResult(
                                -1,
                                "",
                                "Shortcut value was empty."
                        )
                );
                return true;
            }

            CommandResult result = runCommand(
                    SETTINGS,
                    "put",
                    NAMESPACE,
                    KEY,
                    value
            );
            writeResult(reply, result);
            return true;
        }

        if (code == TRANSACTION_DESTROY) {
            if (reply != null) {
                reply.writeInt(0);
            }

            new Thread(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }

                System.exit(0);
            }, "wallet-shortcut-destroy").start();

            return true;
        }

        return super.onTransact(code, data, reply, flags);
    }

    private void writeResult(Parcel reply, CommandResult result) {
        if (reply == null) return;

        reply.writeInt(result.exitCode);
        reply.writeString(result.output);
        reply.writeString(result.error);
    }

    private CommandResult runCommand(String... command) {
        Process process = null;

        try {
            process = new ProcessBuilder(command).start();

            String output = readStream(process.getInputStream());
            String error = readStream(process.getErrorStream());
            int exitCode = process.waitFor();

            return new CommandResult(exitCode, output, error);
        } catch (Throwable e) {
            String message = e.getMessage();

            if (message == null || message.trim().length() == 0) {
                message = e.getClass().getSimpleName();
            }

            return new CommandResult(-1, "", message);
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private String readStream(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream)
        );

        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            if (builder.length() > 0) {
                builder.append('\n');
            }

            builder.append(line);
        }

        reader.close();
        return builder.toString();
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        final String error;

        CommandResult(
                int exitCode,
                String output,
                String error
        ) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
        }
    }
}
