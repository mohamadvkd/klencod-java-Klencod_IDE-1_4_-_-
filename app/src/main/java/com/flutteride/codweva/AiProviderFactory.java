package com.flutteride.codweva;

import android.content.Context;

public final class AiProviderFactory {
    private AiProviderFactory() { }

    public static AiProvider create(Context context, String providerId) {
        AiKeyStore keyStore = new AiKeyStore(context);
        String key = keyStore.get(providerId);
        if (AiSettings.OPENAI.equals(providerId)) return new OpenAiProvider(key);
        if (AiSettings.GEMINI.equals(providerId)) return new GeminiProvider(key);
        if (AiSettings.OPENROUTER.equals(providerId)) return new OpenRouterProvider(key);
        throw new IllegalArgumentException("مزود غير معروف");
    }
}
