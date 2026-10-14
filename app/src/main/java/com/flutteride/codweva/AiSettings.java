package com.flutteride.codweva;

import android.content.Context;
import android.content.SharedPreferences;

public final class AiSettings {
    public static final String OPENAI = "openai";
    public static final String GEMINI = "gemini";
    public static final String OPENROUTER = "openrouter";

    private static final String PREFS = "ai_settings";
    private static final String DEFAULT_PROVIDER = "default_provider";
    private static final String MODEL_PREFIX = "model_";
    private static final String TEMPERATURE = "temperature";
    private static final String MAX_TOKENS = "max_tokens";

    private final SharedPreferences prefs;

    public AiSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getDefaultProvider() {
        return prefs.getString(DEFAULT_PROVIDER, OPENAI);
    }

    public void setDefaultProvider(String providerId) {
        prefs.edit().putString(DEFAULT_PROVIDER, providerId).apply();
    }

    public String getModel(String providerId) {
        String defaultModel = OPENAI.equals(providerId) ? "gpt-4o-mini"
                : GEMINI.equals(providerId) ? "gemini-2.0-flash"
                : "openai/gpt-4o-mini";
        return prefs.getString(MODEL_PREFIX + providerId, defaultModel);
    }

    public void setModel(String providerId, String model) {
        prefs.edit().putString(MODEL_PREFIX + providerId, model == null ? "" : model.trim()).apply();
    }

    public double getTemperature() {
        return Double.longBitsToDouble(prefs.getLong(TEMPERATURE, Double.doubleToLongBits(0.2d)));
    }

    public void setTemperature(double value) {
        prefs.edit().putLong(TEMPERATURE, Double.doubleToLongBits(Math.max(0d, Math.min(2d, value)))).apply();
    }

    public int getMaxTokens() {
        return prefs.getInt(MAX_TOKENS, 4096);
    }

    public void setMaxTokens(int value) {
        prefs.edit().putInt(MAX_TOKENS, Math.max(256, Math.min(32768, value))).apply();
    }
}
