package com.voidstrap.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps one small secret encrypted at rest. The AES-256-GCM key lives in the Android Keystore and
 * cannot be exported; only ciphertext and its IV are written to app-private preferences.
 * Nothing in this class logs, and there is no plaintext fallback: if encryption is unavailable,
 * storing simply fails.
 */
final class SecureStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "voidstrap.pekora.session";
    private static final String PREFS = "pekora_session";
    private static final String BLOB = "blob";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private SecureStore() {
    }

    static synchronized boolean has(Context c) {
        return prefs(c).contains(BLOB);
    }

    static synchronized boolean put(Context c, String plain) {
        if (plain == null || plain.isEmpty()) return false;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            String blob = encode(cipher.getIV()) + ":" + encode(ct);
            return prefs(c).edit().putString(BLOB, blob).commit();
        } catch (Exception e) {
            return false;
        }
    }

    static synchronized String get(Context c) {
        String blob = prefs(c).getString(BLOB, null);
        if (blob == null) return null;
        int sep = blob.indexOf(':');
        if (sep <= 0) {
            clear(c);
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, decode(blob.substring(0, sep))));
            return new String(cipher.doFinal(decode(blob.substring(sep + 1))), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Key was lost or the blob was restored from another device: treat as signed out.
            clear(c);
            return null;
        }
    }

    static synchronized void clear(Context c) {
        prefs(c).edit().remove(BLOB).commit();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey) ks.getKey(KEY_ALIAS, null);
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return gen.generateKey();
    }

    private static String encode(byte[] b) {
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    private static byte[] decode(String s) {
        return Base64.decode(s, Base64.NO_WRAP);
    }
}
