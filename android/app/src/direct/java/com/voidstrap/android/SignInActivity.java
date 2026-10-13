package com.voidstrap.android;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

/** Loads pekora.pro login in a WebView and, once signed in, moves the session cookie into encrypted storage. */
public final class SignInActivity extends AppCompatActivity {
    private WebView web;
    private boolean warned;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        setContentView(web);
        ViewCompat.setOnApplyWindowInsetsListener(web, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        CookieManager.getInstance().setAcceptCookie(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !allowed(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                check();
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });
        if (saved == null) web.loadUrl(PekoraSession.LOGIN_URL);
        else web.restoreState(saved);
    }

    private static boolean allowed(Uri u) {
        if (u == null || !"https".equals(u.getScheme()) || u.getHost() == null) return false;
        String host = u.getHost().toLowerCase(Locale.ROOT);
        return host.equals("pekora.pro") || host.endsWith(".pekora.pro")
                || host.endsWith(".arkoselabs.com") || host.endsWith(".funcaptcha.com");
    }

    private void check() {
        if (!PekoraSession.loggedInInWebView()) return;
        if (!PekoraSession.capture(this)) {
            if (!warned) {
                warned = true;
                Notify.say(this, Notify.GENERAL, R.string.pekora_session_failed);
            }
            return;
        }
        CookieManager.getInstance().flush();
        Store.get(this).changed();
        Notify.say(this, Notify.GENERAL, R.string.pekora_signed_in_toast);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        web.stopLoading();
        web.destroy();
        super.onDestroy();
    }
}
