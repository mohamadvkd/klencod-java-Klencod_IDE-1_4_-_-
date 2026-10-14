package com.flutteride.codweva;

import org.json.JSONObject;

import java.io.Serializable;

/**
 * رسالة موحدة تستخدمها كل مزودات الذكاء الاصطناعي.
 *
 * الحقول:
 *   - role         : user / assistant / system / tool
 *   - content      : نص الرسالة
 *   - toolCallId   : (اختياري) معرّف الاستدعاء — يُستخدم في رسائل "tool"
 *   - rawMessage   : (اختياري) الرسالة الأصلية بصيغة JSON من المزود.
 *                    تُستخدم في OpenAI لإعادة رسالة assistant التي تحتوي tool_calls.
 *
 * ملاحظة: rawMessage غير قابل للتسلسل (Serializable) — لا يُحفظ على القرص.
 */
public final class AiMessage implements Serializable {
    public final String role;
    public final String content;

    /** معرّف الاستدعاء — يُستخدم في رسائل "tool" فقط. */
    public transient String toolCallId;

    /** الرسالة الأصلية من المزود (JSONObject) — للردود المركّبة. */
    public transient JSONObject rawMessage;

    public AiMessage(String role, String content) {
        this.role = role == null ? "user" : role;
        this.content = content == null ? "" : content;
        this.toolCallId = null;
        this.rawMessage = null;
    }

    // ============================================================
    // مُنشئات مساعدة
    // ============================================================
    public static AiMessage system(String text) {
        return new AiMessage("system", text);
    }

    public static AiMessage user(String text) {
        return new AiMessage("user", text);
    }

    public static AiMessage assistant(String text) {
        return new AiMessage("assistant", text);
    }

    /**
     * رسالة "tool" — نتيجة تنفيذ أداة.
     *
     * @param toolCallId معرّف الاستدعاء الأصلي.
     * @param content    محتوى النتيجة (نص عادي).
     */
    public static AiMessage tool(String toolCallId, String content) {
        AiMessage msg = new AiMessage("tool", content);
        msg.toolCallId = toolCallId;
        return msg;
    }

    /**
     * رسالة assistant تحتوي tool_calls.
     * نحتفظ بالرسالة الأصلية للاستخدام في الجولة التالية.
     */
    public static AiMessage assistantWithToolCalls(JSONObject rawMessage) {
        AiMessage msg = new AiMessage("assistant", "");
        msg.rawMessage = rawMessage;
        return msg;
    }
}