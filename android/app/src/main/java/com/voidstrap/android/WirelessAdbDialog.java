package com.voidstrap.android;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;

/** Asks for the pairing address and six-digit code from Wireless debugging, then pairs, connects, and starts. */
final class WirelessAdbDialog {
    private WirelessAdbDialog() {
    }

    static void show(Activity a) {
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_wireless_adb, null);
        EditText host = v.findViewById(R.id.wadb_host);
        EditText port = v.findViewById(R.id.wadb_port);
        EditText code = v.findViewById(R.id.wadb_code);
        TextView status = v.findViewById(R.id.wadb_status);
        host.setText(io.github.muntashirakon.adb.android.AndroidUtils.getHostIpAddress(a));
        Store store = Store.get(a);

        AlertDialog dialog = Ui.alert(a)
                .setTitle(R.string.wadb_title)
                .setView(v)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.wadb_start, null)
                .show();
        MaterialButton start = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        start.setOnClickListener(x -> {
            String address = host.getText().toString().trim();
            String pairingCode = code.getText().toString().trim();
            int pairingPort;
            try {
                pairingPort = Integer.parseInt(port.getText().toString().trim());
            } catch (NumberFormatException e) {
                status.setText(R.string.wadb_need_port);
                return;
            }
            if (address.isEmpty() || !pairingCode.matches("\\d{6}")) {
                status.setText(R.string.wadb_need_code);
                return;
            }
            start.setEnabled(false);
            status.setText(R.string.wadb_working);
            store.work.execute(() -> {
                String error = WirelessAdb.pairConnectRun(a, address, pairingPort, pairingCode);
                store.main.post(() -> {
                    if (error == null) {
                        Notify.say(a, Notify.GENERAL, R.string.wadb_done);
                        store.changed();
                        dialog.dismiss();
                    } else {
                        start.setEnabled(true);
                        status.setText(a.getString(R.string.wadb_failed, error));
                    }
                });
            });
        });
    }
}
