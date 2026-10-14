package com.flutteride.codweva;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public final class OpenAiProvider implements AiProvider {
    private final String apiKey;
    private final String endpoint;

    public OpenAiProvider(String apiKey) {
        this(apiKey, "https://api.openai.com/v1/chat/completions");
    }

    public OpenAiProvider(String apiKey, String endpoint) {
        this.apiKey = apiKey;
        this.endpoint = endpoint == null || endpoint.trim().isEmpty()
                ? "https://api.openai.com/v1/chat/completions" : endpoint.trim();
    }

    @Override public String getId() { return AiSettings.OPENAI; }
    @Override public String getDisplayName() { return "OpenAI"; }

    // ============================================================
    // الإرسال العادي (بدون أدوات)
    // ============================================================
    @Override
    public AiResponse send(List<AiMessage> messages, String model, double temperature, int maxTokens) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("أدخل مفتاح OpenAI أولًا");
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", toMessages(messages));
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        JSONObject response = AiHttpClient.postJson(endpoint, apiKey, body, false);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new Exception("لم يُرجع OpenAI أي استجابة");
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
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("أدخل مفتاح OpenAI أولًا");

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("messages", toMessagesWithTools(messages));
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
        if (choices == null || choices.length() == 0) throw new Exception("لم يُرجع OpenAI أي استجابة");
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.optJSONObject("message");
        if (message == null) throw new Exception("رد OpenAI لا يحتوي message");

        String text = message.optString("content", "");
        if (text == null) text = "";

        List<AgentTool.ToolCall> calls = AgentTool.parseOpenAiToolCalls(message);

        if (calls.isEmpty()) {
            return new AiResponse(text, getId(), model);
        }

        // رد يحتوي tool calls — نحتفظ بالرسالة الأصلية
        return new AiResponse("", calls, getId(), model, message);
    }

    // ============================================================
    // تحويلات الرسائل
    // ============================================================

    /** للاستخدام العام (OpenRouter تعتمد عليها). */
    static JSONArray toMessages(List<AiMessage> messages) throws Exception {
        JSONArray array = new JSONArray();
        if (messages != null) {
            for (AiMessage message : messages) {
                JSONObject item = new JSONObject();
                item.put("role", message.role);
                item.put("content", message.content);
                array.put(item);
            }
        }
        return array;
    }

    /**
     * تحويل موسّع يدعم:
     *   - رسائل assistant عادية
     *   - رسائل assistant التي تحتوي tool_calls (rawMessage)
     *   - رسائل tool (toolCallId)
     */
    static JSONArray toMessagesWithTools(List<AiMessage> messages) throws Exception {
        JSONArray array = new JSONArray();
        if (messages == null) return array;

        for (AiMessage message : messages) {
            // 1) رسالة assistant تحتوي tool_calls — استخدم rawMessage الأصلية
            if ("assistant".equals(message.role) && message.rawMessage != null) {
                array.put(message.rawMessage);
                continue;
            }

            // 2) رسالة tool — تحتاج tool_call_id
            if ("tool".equals(message.role)) {
                JSONObject item = new JSONObject();
                item.put("role", "tool");
                item.put("tool_call_id", message.toolCallId == null ? "" : message.toolCallId);
                item.put("content", message.content == null ? "" : message.content);
                array.put(item);
                continue;
            }

            // 3) رسالة عادية (system / user / assistant)
            JSONObject item = new JSONObject();
            item.put("role", message.role);
            item.put("content", message.content == null ? "" : message.content);
            array.put(item);
        }
        return array;
    }
}