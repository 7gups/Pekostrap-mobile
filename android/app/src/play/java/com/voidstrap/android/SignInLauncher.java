package com.voidstrap.android;

import android.app.Activity;

/** Play builds do not include the sign-in screen. */
final class SignInLauncher {
    private SignInLauncher() {
    }

    static boolean available() {
        return false;
    }

    static void open(Activity a) {
    }
}
