package com.voidstrap.android;

import android.content.Context;
import android.os.Build;

import java.security.PrivateKey;
import java.security.cert.Certificate;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;

/** Wireless debugging connection for Pekostrap, using the identity from AdbIdentity. */
final class PekostrapAdbManager extends AbsAdbConnectionManager {
    private static PekostrapAdbManager instance;

    private final PrivateKey privateKey;
    private final Certificate certificate;

    private PekostrapAdbManager(AdbIdentity identity) {
        this.privateKey = identity.privateKey;
        this.certificate = identity.certificate;
        setApi(Build.VERSION.SDK_INT);
    }

    static synchronized PekostrapAdbManager get(Context c) throws Exception {
        if (instance == null) instance = new PekostrapAdbManager(AdbIdentity.loadOrCreate(c));
        return instance;
    }

    @Override
    protected PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    protected Certificate getCertificate() {
        return certificate;
    }

    @Override
    protected String getDeviceName() {
        return "Pekostrap";
    }
}
