package com.flutteride.codweva;

import android.content.Context;
import android.content.SharedPreferences;

/** تخزين محلي بسيط لاختيار وضع المساعد (عام للتطبيق). */
public final class AiModeStore {

    private static final String PREFS = "ai_mode_prefs";
    private static final String KEY_MODE = "selected_mode";

    private final SharedPreferences prefs;

    public AiModeStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public AiMode getMode() {
        return AiMode.fromId(prefs.getString(KEY_MODE, AiMode.SMART.id));
    }

    public void setMode(AiMode mode) {
        if (mode == null) mode = AiMode.SMART;
        prefs.edit().putString(KEY_MODE, mode.id).apply();
    }

    public boolean isAgenticAvailable() {
        return true;
    }
}