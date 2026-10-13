package com.voidstrap.android;

import android.app.Activity;
import android.content.Intent;

/** Sign-in is available in the direct build only. */
final class SignInLauncher {
    private SignInLauncher() {
    }

    static boolean available() {
        return true;
    }

    static void open(Activity a) {
        a.startActivity(new Intent(a, SignInActivity.class));
    }
}
