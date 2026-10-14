package com.flutteride.codweva;

import java.util.ArrayList;
import java.util.List;

public interface AiProvider {

    String getId();
    String getDisplayName();

    /**
     * إرسال عادي بدون أدوات (يُستخدم في الوضع الذكي).
     */
    AiResponse send(List<AiMessage> messages, String model,
                    double temperature, int maxTokens) throws Exception;

    /**
     * إرسال مع أدوات (يُستخدم في الوضع الكامل).
     *
     * @return AiResponse يحمل إما نصاً (text) أو استدعاءات أدوات (toolCalls).
     */
    AiResponse sendWithTools(List<AiMessage> messages, String model,
                             double temperature, int maxTokens,
                             List<AgentTool.ToolCall> previousToolResults) throws Exception;
}

/**
 * استجابة موحّدة من أي مزود.
 * تحمل إما نصاً عادياً (text) أو استدعاءات أدوات (toolCalls).
 */
final class AiResponse {

    /** النص النهائي (فارغ إذا كان الرد يحتوي فقط على tool calls). */
    public final String text;

    /** استدعاءات الأدوات إن وُجدت (عادة فارغة). */
    public final List<AgentTool.ToolCall> toolCalls;

    /** معرّف المزود. */
    public final String providerId;

    /** اسم النموذج المستخدم. */
    public final String model;

    /**
     * الرسالة الأصلية بصيغة المزود (تُستخدم لتمرير tool_calls في الجولة التالية لـ OpenAI).
     * مثلاً: JSONObject يحمل {"role":"assistant","content":null,"tool_calls":[...]}
     * أو null إذا لم يكن هناك tool calls.
     */
    public final Object rawAssistantMessage;

    AiResponse(String text, String providerId, String model) {
        this(text, new ArrayList<AgentTool.ToolCall>(), providerId, model, null);
    }

    AiResponse(String text,
               List<AgentTool.ToolCall> toolCalls,
               String providerId,
               String model,
               Object rawAssistantMessage) {
        this.text = text == null ? "" : text;
        this.toolCalls = toolCalls == null ? new ArrayList<AgentTool.ToolCall>() : toolCalls;
        this.providerId = providerId == null ? "" : providerId;
        this.model = model == null ? "" : model;
        this.rawAssistantMessage = rawAssistantMessage;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}