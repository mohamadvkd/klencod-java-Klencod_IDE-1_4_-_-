package com.flutteride.codweva;

/** إعدادات GitHub العامة اللازمة لتدفق Device Flow. */
public final class GitHubConfig {
    private GitHubConfig() {}

    // Client ID عام لتطبيق OAuth؛ لا نضع Client Secret داخل APK.
    public static final String CLIENT_ID = "Ov23liD3bSfWr2wF5e1B";
    public static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    public static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    public static final String API_URL = "https://api.github.com";
    public static final String VERIFICATION_URL = "https://github.com/login/device";
    public static final String API_VERSION = "2026-03-10";
    public static final String WORKFLOW_FILE = "main.yml";
    public static final String ARTIFACT_NAME = "app-release";
}
