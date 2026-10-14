package com.voidstrap.android;

import android.content.Context;
import android.os.Build;

import java.io.InputStream;

import io.github.muntashirakon.adb.AdbStream;

/** Pairs with the code from Wireless debugging, connects over TLS, and runs the helper start command. */
final class WirelessAdb {
    static final int MIN_API = 30;

    private WirelessAdb() {
    }

    static boolean available() {
        return Build.VERSION.SDK_INT >= MIN_API;
    }

    /** Returns null on success, or a short error message. Blocks, so call it off the main thread. */
    static String pairConnectRun(Context c, String host, int pairingPort, String code) {
        PekostrapAdbManager manager;
        try {
            manager = PekostrapAdbManager.get(c);
        } catch (Exception e) {
            return "identity";
        }
        try {
            if (!manager.pair(host, pairingPort, code)) return "pairing";
        } catch (Exception e) {
            return "pairing";
        }
        try {
            if (!manager.connectTls(c, 15000)) return "connect";
        } catch (Exception e) {
            return "connect";
        }
        try (AdbStream stream = manager.openStream("shell:" + Helper.deviceCommand(c));
             InputStream output = stream.openInputStream()) {
            byte[] buffer = new byte[256];
            int total = 0;
            while (total < 32 * 1024 && output.read(buffer) != -1) total += buffer.length;
        } catch (Exception e) {
            return "run";
        }
        return null;
    }
}
