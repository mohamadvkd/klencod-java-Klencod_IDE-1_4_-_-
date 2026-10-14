package com.flutteride.codweva;

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

/** تخزين محلي مشفّر لتوكن GitHub واسم المستخدم باستخدام Android Keystore. */
public final class SecureStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "klencod_ide_secure_store";
    private static final String PREFS = "klencod_secure_store";
    private static final String VALUE = "github_access_token";
    private static final String IV = "github_access_token_iv";
    private static final String USERNAME = "github_username";
    private static final String USERNAME_IV = "github_username_iv";

    private final Context context;

    public SecureStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean saveGitHubToken(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return prefs().edit()
                    .putString(VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    public String readGitHubToken() {
        try {
            String encoded = prefs().getString(VALUE, null);
            String encodedIv = prefs().getString(IV, null);
            if (encoded == null || encodedIv == null) return "";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    /** يحفظ اسم مستخدم GitHub بشكل مشفّر لاستخدامه في واجهة الدخول بدون إنترنت. */
    public boolean saveGitHubUsername(String username) {
        if (username == null || username.trim().isEmpty()) return false;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] encrypted = cipher.doFinal(username.getBytes(StandardCharsets.UTF_8));
            return prefs().edit()
                    .putString(USERNAME, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(USERNAME_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .commit();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** يقرأ اسم المستخدم المحفوظ، ويُرجع قيمة افتراضية إن لم يوجد. */
    public String readGitHubUsername() {
        try {
            String encoded = prefs().getString(USERNAME, null);
            String encodedIv = prefs().getString(USERNAME_IV, null);
            if (encoded == null || encodedIv == null) return "GitHub user";
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "GitHub user";
        }
    }

    public void clearGitHubToken() {
        prefs().edit()
                .remove(VALUE)
                .remove(IV)
                .remove(USERNAME)
                .remove(USERNAME_IV)
                .apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}