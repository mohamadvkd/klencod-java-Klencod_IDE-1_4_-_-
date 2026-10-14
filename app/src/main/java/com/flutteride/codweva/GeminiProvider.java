package com.flutteride.codweva;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class GeminiProvider implements AiProvider {
    private final String apiKey;
    private final String endpointBase;

    public GeminiProvider(String apiKey) {
        this(apiKey, "https://generativelanguage.googleapis.com/v1beta");
    }

    public GeminiProvider(String apiKey, String endpointBase) {
        this.apiKey = apiKey;
        this.endpointBase = endpointBase == null || endpointBase.trim().isEmpty()
                ? "https://generativelanguage.googleapis.com/v1beta" : endpointBase.trim();
    }

    @Override public String getId() { return AiSettings.GEMINI; }
    @Override public String getDisplayName() { return "Gemini"; }

    // ============================================================
    // الإرسال العادي (بدون أدوات)
    // ============================================================
    @Override
    public AiResponse send(List<AiMessage> messages, String model, double temperature, int maxTokens) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("أدخل مفتاح Gemini أولًا");
        String cleanModel = cleanModelName(model);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        String systemText = null;
        if (messages != null) {
            for (AiMessage message : messages) {
                if ("system".equals(message.role)) {
                    systemText = systemText == null ? message.content : systemText + "\n" + message.content;
                    continue;
                }
                contents.put(buildContent(message));
            }
        }
        body.put("contents", contents);
        if (systemText != null && !systemText.isEmpty()) {
            body.put("systemInstruction", new JSONObject().put("parts", new JSONArray()
                    .put(new JSONObject().put("text", systemText))));
        }
        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxTokens);
        body.put("generationConfig", generationConfig);

        String endpoint = endpointBase + "/models/" + cleanModel + ":generateContent";
        JSONObject response = AiHttpClient.postJson(endpoint, apiKey, body, true);
        return parseStandardResponse(response, cleanModel);
    }

    // ============================================================
    // الإرسال مع أدوات
    // ============================================================
    @Override
    public AiResponse sendWithTools(List<AiMessage> messages, String model,
                                    double temperature, int maxTokens,
                                    List<AgentTool.ToolCall> previousToolResults) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("أدخل مفتاح Gemini أولًا");
        String cleanModel = cleanModelName(model);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        String systemText = null;

        if (messages != null) {
            for (AiMessage message : messages) {
                if ("system".equals(message.role)) {
                    systemText = systemText == null ? message.content : systemText + "\n" + message.content;
                    continue;
                }
                if ("tool".equals(message.role)) {
                    // رسالة tool في Gemini: parts: [{functionResponse: {name, response}}]
                    // نغلّفها داخل رسالة user (لأن Gemini لا يعرف دور "tool")
                    JSONObject part = new JSONObject();
                    JSONObject fnResp = new JSONObject();
                    JSONObject resp = new JSONObject();
                    resp.put("content", message.content == null ? "" : message.content);
                    fnResp.put("name", message.toolCallId == null ? "" : message.toolCallId);
                    fnResp.put("response", resp);
                    part.put("functionResponse", fnResp);

                    JSONObject content = new JSONObject();
                    content.put("role", "user");
                    JSONArray parts = new JSONArray();
                    parts.put(part);
                    content.put("parts", parts);
                    contents.put(content);
                    continue;
                }

                contents.put(buildContent(message));
            }
        }

        body.put("contents", contents);
        if (systemText != null && !systemText.isEmpty()) {
            body.put("systemInstruction", new JSONObject().put("parts", new JSONArray()
                    .put(new JSONObject().put("text", systemText))));
        }

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxTokens);
        body.put("generationConfig", generationConfig);

        // ✅ إضافة تعريف الأدوات بصيغة Gemini
        JSONArray declarations = AgentTool.buildGeminiFunctionDeclarations();
        if (declarations.length() > 0) {
            JSONObject toolWrapper = new JSONObject();
            toolWrapper.put("functionDeclarations", declarations);
            JSONArray tools = new JSONArray();
            tools.put(toolWrapper);
            body.put("tools", tools);
        }

        String endpoint = endpointBase + "/models/" + cleanModel + ":generateContent";
        JSONObject response = AiHttpClient.postJson(endpoint, apiKey, body, true,
                30000, 180000);

        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new Exception("لم يُرجع Gemini أي استجابة");
        }
        JSONObject candidate = candidates.optJSONObject(0);
        if (candidate == null) throw new Exception("رد Gemini فارغ");
        JSONObject content = candidate.optJSONObject("content");
        if (content == null) throw new Exception("رد Gemini لا يحتوي content");
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null) parts = new JSONArray();

        // جمع النص و function calls
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            if (part.has("text")) {
                text.append(part.optString("text", ""));
            }
        }

        List<AgentTool.ToolCall> calls = AgentTool.parseGeminiFunctionCalls(parts);

        if (calls.isEmpty()) {
            return new AiResponse(text.toString(), getId(), cleanModel);
        }

        // Gemini لا يعيد tool_call_id — نُولّد واحداً لكل call بشكل مختلف
        // لكن في الواقع نحن نخزن content الـ assistant الأصلي (بـ parts) في rawMessage.
        // عند الإرسال في الجولة التالية، نُرسله كما هو.

        // نُخزّن الرسالة الأصلية
        JSONObject rawAssistant = new JSONObject();
        rawAssistant.put("role", "model");
        JSONArray rawParts = new JSONArray();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject p = parts.optJSONObject(i);
            if (p != null) rawParts.put(p);
        }
        rawAssistant.put("parts", rawParts);

        return new AiResponse(text.toString(), calls, getId(), cleanModel, rawAssistant);
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================

    private String cleanModelName(String model) {
        String clean = model == null ? "gemini-2.0-flash" : model.trim();
        if (clean.startsWith("models/")) clean = clean.substring("models/".length());
        return clean;
    }

    /**
     * بناء content لرسالة عادية (user / assistant).
     * Gemini يستخدم "model" بدلاً من "assistant".
     */
    private JSONObject buildContent(AiMessage message) throws Exception {
        JSONObject content = new JSONObject();
        content.put("role", "assistant".equals(message.role) ? "model" : "user");
        JSONArray parts = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("text", message.content == null ? "" : message.content);
        parts.put(textPart);
        content.put("parts", parts);
        return content;
    }

    /**
     * تحليل رد Gemini عادي (بدون أدوات).
     */
    private AiResponse parseStandardResponse(JSONObject response, String model) throws Exception {
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new Exception("لم يُرجع Gemini أي استجابة");
        }
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder text = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) text.append(part.optString("text", ""));
            }
        }
        return new AiResponse(text.toString(), getId(), model);
    }
}