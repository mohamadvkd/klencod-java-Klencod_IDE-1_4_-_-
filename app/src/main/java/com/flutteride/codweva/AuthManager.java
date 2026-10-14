package com.flutteride.codweva;

import android.content.Context;
import android.content.SharedPreferences;

/** واجهة توافقية قديمة تستخدمها MainActivity، دون أي خادم وسيط. */
public final class AuthManager {
    private static AuthManager instance;
    private final Context context;
    private final SecureStore secureStore;
    private final SharedPreferences prefs;

    private AuthManager(Context context) {
        this.context = context.getApplicationContext();
        this.secureStore = new SecureStore(this.context);
        this.prefs = this.context.getSharedPreferences("github_profile", Context.MODE_PRIVATE);
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) instance = new AuthManager(context);
        return instance;
    }

    public void saveAccessToken(String token) {
        secureStore.saveGitHubToken(token);
    }

    public String getAccessToken() {
        return secureStore.readGitHubToken();
    }

    public void saveGithubUsername(String username) {
        prefs.edit().putString("github_username", username == null ? "" : username).apply();
    }

    public String getGithubUsername() {
        return prefs.getString("github_username", "");
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public void logout() {
        secureStore.clearGitHubToken();
        prefs.edit().clear().apply();
    }

    public String getAuthHeader() {
        String token = getAccessToken();
        return token == null || token.trim().isEmpty() ? null : "Bearer " + token;
    }

    /** لم يعد هناك refresh token؛ Device Flow يمنح توكنًا محفوظًا محليًا. */
    public boolean refreshAccessToken() {
        return isLoggedIn();
    }
}
