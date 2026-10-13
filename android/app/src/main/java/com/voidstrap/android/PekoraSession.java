package com.voidstrap.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Pekora sign-in state. The .PEKORASECURITY session cookie is read once from the WebView cookie jar
 * after login and kept only as ciphertext (see SecureStore). Its value is never logged or shown.
 */
final class PekoraSession {
    static final String SITE = "https://pekora.pro";
    static final String LOGIN_URL = SITE + "/login";
    static final String COOKIE = ".PEKORASECURITY";
    private static final String NOTICE_ACK = "pekoraNoticeAck";
    private static final int NOTICE_SECONDS = 5;
    static final String NAME_SETTING = "pekoraName";
    static final String VAULT = "pekora_session";
    static final String USER_API = SITE + "/apisite/users/v1/users/authenticated";

    private PekoraSession() {
    }

    static boolean signedIn(Context c) {
        return SecureStore.has(c, VAULT);
    }

    static String statusText(Context c) {
        return c.getString(signedIn(c) ? R.string.pekora_account_signed_in : R.string.pekora_account_signed_out);
    }

    /** The decrypted session value, or null when signed out. Callers must not log or display it. */
    static String cookie(Context c) {
        return SecureStore.get(c, VAULT);
    }

    /** Session value for authenticated API calls. Callers must not log it. */
    static String session(Context c) {
        return SecureStore.get(c, VAULT);
    }

    /** Copies the session cookie from the WebView into encrypted storage. True when it was stored. */
    static boolean capture(Context c) {
        String value = fromWebView();
        if (value == null || !SecureStore.put(c, VAULT, value)) return false;
        purgeWebViewCopy();
        return true;
    }

    /**
     * The sign-in WebView keeps its own plaintext copy of the session cookie. Once the value is encrypted
     * here, expire every copy in the WebView jar so the plaintext is not left on disk.
     */
    static void purgeWebViewCopy() {
        try {
            CookieManager cm = CookieManager.getInstance();
            String expired = COOKIE + "=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure";
            cm.setCookie(SITE, expired + "; Domain=pekora.pro");
            cm.setCookie(SITE, expired + "; Domain=.pekora.pro");
            cm.flush();
        } catch (RuntimeException ignored) {
        }
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
            if (!SignInLauncher.available()) return;
            if ("1".equals(Store.get(a).setting(NOTICE_ACK, "0"))) openSignIn(a);
            else notice(a);
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

    private static void openSignIn(Activity a) {
        SignInLauncher.open(a);
    }

    /** Shows the privacy notice; Continue unlocks after a 5 second countdown. */
    private static void notice(Activity a) {
        Handler main = new Handler(Looper.getMainLooper());
        int[] left = {NOTICE_SECONDS};
        AlertDialog d = Ui.alert(a)
                .setTitle(R.string.pekora_notice_title)
                .setMessage(R.string.pekora_notice_body)
                .setPositiveButton(a.getString(R.string.pekora_notice_wait, left[0]), (dl, w) -> openSignIn(a))
                .setNegativeButton(R.string.common_cancel, null)
                .setNeutralButton(R.string.pekora_notice_dont_show, (dl, w) -> {
                    Store.get(a).putSetting(NOTICE_ACK, "1");
                    openSignIn(a);
                })
                .show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            if (!d.isShowing()) return;
            left[0]--;
            if (left[0] > 0) {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setText(a.getString(R.string.pekora_notice_wait, left[0]));
                main.postDelayed(tick[0], 1000);
            } else {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setText(R.string.pekora_notice_continue);
                d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            }
        };
        d.setOnDismissListener(x -> main.removeCallbacks(tick[0]));
        main.postDelayed(tick[0], 1000);
    }

    /**
     * Username of the signed-in account, or null on any failure. The session value is used only for
     * this request, redirects are not followed so it cannot leak to another host, and nothing is logged.
     */
    static String fetchUsername(Context c) {
        String session = SecureStore.get(c, VAULT);
        if (session == null) return null;
        Object[] res = Http.request("GET", USER_API,
                new String[]{"Cookie", COOKIE + "=" + session, "Accept", "application/json", "User-Agent", "Voidstrap Android"},
                null, 15_000, 64_000, false);
        if (res == null || ((int[]) res[0])[0] != 200) return null;
        try {
            JSONObject o = new JSONObject(new String((byte[]) res[2], StandardCharsets.UTF_8));
            String name = o.optString("name", "");
            return name.isEmpty() ? o.optString("displayName", null) : name;
        } catch (JSONException e) {
            return null;
        }
    }

    static void signOut(Context c) {
        SecureStore.clear(c, VAULT);
        Store.get(c).putSetting(NAME_SETTING, "");
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();
        } catch (RuntimeException ignored) {
        }
    }
}
