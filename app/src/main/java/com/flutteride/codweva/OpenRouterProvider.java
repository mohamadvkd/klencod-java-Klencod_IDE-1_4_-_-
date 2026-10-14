package com.flutteride.codweva;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class OpenRouterProvider implements AiProvider {
    private final String apiKey;
    private final String endpoint;

    public OpenRouterProvider(String apiKey) {
        this(apiKey, "https://openrouter.ai/api/v1/chat/completions");
    }

    public OpenRouterProvider(String apiKey, String endpoint) {
        this.apiKey = apiKey;
        this.endpoint = endpoint == null || endpoint.trim().isEmpty()
                ? "https://openrouter.ai/api/v1/chat/completions" : endpoint.trim();
    }

    @Override public String getId() { return AiSettings.OPENROUTER; }
    @Override public String getDisplayName() { return "OpenRouter"; }

    // ============================================================
    // الإرسال العادي
    // ============================================================
    @Override
    public AiResponse send(List<AiMessage> messages, String model, double temperature, int maxTokens) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("أدخل مفتاح OpenRouter أولًا");
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", OpenAiProvider.toMessages(messages));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        JSONObject response = AiHttpClient.postJson(endpoint, apiKey, body, false);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new Exception("لم يُرجع OpenRouter أي استجابة");
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String text = message == null ? "" : message.optString("content", "");
        return new AiResponse(text, getId(), model);
    }

    // ============================================================
    // الإرسال مع أدوات
    // ============================================================
    @Override
    public AiResponse sendWithTools(List<AiMessage> messages, String model,
                                    double temperature, int maxTokens,
                                    List<AgentTool.ToolCall> previousToolResults) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("أدخل مفتاح OpenRouter أولًا");

        JSONObject body = new JSONObject();
        body.put("model", model);
        // ✅ نستخدم النسخة الموسّعة من OpenAiProvider
        body.put("messages", OpenAiProvider.toMessagesWithTools(messages));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        JSONArray tools = AgentTool.buildOpenAiTools();
        if (tools.length() > 0) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        JSONObject response = AiHttpClient.postJson(endpoint, apiKey, body, false,
                30000, 180000);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new Exception("لم يُرجع OpenRouter أي استجابة");
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.optJSONObject("message");
        if (message == null) throw new Exception("رد OpenRouter لا يحتوي message");

        String text = message.optString("content", "");
        if (text == null) text = "";

        List<AgentTool.ToolCall> calls = AgentTool.parseOpenAiToolCalls(message);

        if (calls.isEmpty()) {
            return new AiResponse(text, getId(), model);
        }

        return new AiResponse("", calls, getId(), model, message);
    }
}