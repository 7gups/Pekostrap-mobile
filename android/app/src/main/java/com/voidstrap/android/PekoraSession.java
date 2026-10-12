package com.voidstrap.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.webkit.CookieManager;

/**
 * Pekora sign-in state. The .PEKORASECURITY session cookie is read once from the WebView cookie jar
 * after login and kept only as ciphertext (see SecureStore). Its value is never logged or shown.
 */
final class PekoraSession {
    static final String SITE = "https://pekora.pro";
    static final String LOGIN_URL = SITE + "/login";
    static final String COOKIE = ".PEKORASECURITY";

    private PekoraSession() {
    }

    static boolean signedIn(Context c) {
        return SecureStore.has(c);
    }

    static String statusText(Context c) {
        return c.getString(signedIn(c) ? R.string.pekora_account_signed_in : R.string.pekora_account_signed_out);
    }

    /** The decrypted session value, or null when signed out. Callers must not log or display it. */
    static String cookie(Context c) {
        return SecureStore.get(c);
    }

    /** Copies the session cookie from the WebView into encrypted storage. True when it was stored. */
    static boolean capture(Context c) {
        String value = fromWebView();
        return value != null && SecureStore.put(c, value);
    }

    /** True when the WebView already holds a session cookie for pekora.pro. */
    static boolean loggedInInWebView() {
        return fromWebView() != null;
    }

    private static String fromWebView() {
        try {
            String all = CookieManager.getInstance().getCookie(SITE);
            if (all == null) return null;
            for (String part : all.split(";")) {
                String p = part.trim();
                if (p.startsWith(COOKIE + "=")) {
                    String v = p.substring(COOKIE.length() + 1);
                    return v.isEmpty() ? null : v;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    static void toggle(Activity a) {
        if (!signedIn(a)) {
            a.startActivity(new Intent(a, SignInActivity.class));
            return;
        }
        Ui.alert(a)
                .setTitle(R.string.pekora_sign_out_title)
                .setMessage(R.string.pekora_sign_out_body)
                .setPositiveButton(R.string.pekora_sign_out, (d, w) -> {
                    signOut(a);
                    Store.get(a).changed();
                    Notify.say(a, Notify.GENERAL, R.string.pekora_signed_out_toast);
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    static void signOut(Context c) {
        SecureStore.clear(c);
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (RuntimeException ignored) {
        }
    }
}
