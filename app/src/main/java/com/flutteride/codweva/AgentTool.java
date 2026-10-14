package com.flutteride.codweva;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * نموذج موحد لأدوات الوكيل.
 *
 * الأدوات المتاحة:
 *   - read_file(path)           : يقرأ محتوى ملف
 *   - list_files()              : يسرد كل ملفات المشروع
 *   - get_file_info(path)       : معلومات (حجم + أسطر) بدون قراءة كاملة
 *   - search(query, ext?)       : يبحث عن نص في كل الملفات
 *   - move_file(from, to)       : ينقل أو يُعيد تسمية ملف (يُنشئ المجلدات تلقائياً)
 *
 * ملاحظة أمنية:
 *   الوكيل لا يستطيع الكتابة أو الحذف مباشرة. الكتابة/النقل فقط عبر
 *   وسوم <klencod_action> + موافقة المستخدم.
 */
public final class AgentTool {

    // ============================================================
    // أسماء الأدوات (ثوابت)
    // ============================================================
    public static final String TOOL_READ_FILE = "read_file";
    public static final String TOOL_LIST_FILES = "list_files";
    public static final String TOOL_FILE_INFO = "get_file_info";
    public static final String TOOL_SEARCH = "search";

    // ✅ أداة النقل (مُضافة)
    public static final String TOOL_MOVE_FILE = "move_file";

    private AgentTool() {}

    // ============================================================
    // نموذج استدعاء أداة من الـ AI
    // ============================================================
    public static final class ToolCall {
        public final String id;
        public final String name;
        public final JSONObject arguments;

        public ToolCall(String id, String name, JSONObject arguments) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.arguments = arguments == null ? new JSONObject() : arguments;
        }

        public String getString(String key) {
            if (arguments == null) return "";
            String v = arguments.optString(key, "");
            return v == null ? "" : v;
        }

        public String getOptionalString(String key) {
            if (arguments == null) return null;
            if (!arguments.has(key)) return null;
            String v = arguments.optString(key, "");
            return (v == null || v.trim().isEmpty()) ? null : v;
        }

        @Override
        public String toString() {
            return "ToolCall{" + name + "(" + arguments + ")}";
        }
    }

    // ============================================================
    // نموذج نتيجة تنفيذ أداة
    // ============================================================
    public static final class ToolResult {
        public final String callId;
        public final String toolName;
        public final String content;
        public final boolean isError;

        public ToolResult(String callId, String toolName, String content, boolean isError) {
            this.callId = callId == null ? "" : callId;
            this.toolName = toolName == null ? "" : toolName;
            this.content = content == null ? "" : content;
            this.isError = isError;
        }

        public static ToolResult success(String callId, String toolName, String content) {
            return new ToolResult(callId, toolName, content, false);
        }

        public static ToolResult error(String callId, String toolName, String message) {
            return new ToolResult(callId, toolName,
                    "ERROR: " + (message == null ? "unknown" : message), true);
        }
    }

    // ============================================================
    // Schemas بصيغة OpenAI
    // ============================================================
    public static JSONArray buildOpenAiTools() {
        JSONArray tools = new JSONArray();
        try {
            tools.put(buildOpenAiFunction(
                    TOOL_READ_FILE,
                    "يقرأ محتوى ملف نصي داخل المشروع. استخدم هذا إذا احتجت رؤية كود ملف قبل تعديله.",
                    new String[][]{
                            {"path", "string", "المسار النسبي من جذر المشروع (مثال: lib/main.dart)"}
                    },
                    new String[]{"path"}
            ));

            tools.put(buildOpenAiFunction(
                    TOOL_LIST_FILES,
                    "يسرد كل ملفات المشروع مع أحجامها وعدد أسطرها. استخدمه للاستكشاف الأولي.",
                    new String[][]{},
                    new String[]{}
            ));

            tools.put(buildOpenAiFunction(
                    TOOL_FILE_INFO,
                    "يُعطي معلومات عن ملف (حجم + عدد أسطر) بدون قراءة محتواه. أسرع من read_file.",
                    new String[][]{
                            {"path", "string", "المسار النسبي للملف"}
                    },
                    new String[]{"path"}
            ));

            tools.put(buildOpenAiFunction(
                    TOOL_SEARCH,
                    "يبحث عن نص أو نمط في كل ملفات المشروع. مثال: ابحث عن 'setState' أو 'MyWidget'.",
                    new String[][]{
                            {"query", "string", "النص المطلوب البحث عنه"},
                            {"extension", "string", "امتداد الملفات (اختياري، مثال: dart). إن لم يُحدَّد، يبحث في كل الملفات النصية."}
                    },
                    new String[]{"query"}
            ));

            // ✅ أداة النقل
            tools.put(buildOpenAiFunction(
                    TOOL_MOVE_FILE,
                    "ينقل ملفاً من مسار إلى آخر، أو يُعيد تسميته. " +
                    "يُنشئ المجلدات الوجهة تلقائياً. " +
                    "استخدمه لتغيير بنية المشروع (مثل إعادة تسمية مجلد الحزمة)، أو نقل ملف بين مجلدات. " +
                    "إذا لم تكن متأكداً أن الملف الوجهة غير موجود، تحقق أولاً بـ list_files.",
                    new String[][]{
                            {"from", "string", "المسار الحالي للملف (نسبي من جذر المشروع)"},
                            {"to", "string", "المسار الجديد (نسبي من جذر المشروع)"}
                    },
                    new String[]{"from", "to"}
            ));
        } catch (Exception ignored) {}
        return tools;
    }

    private static JSONObject buildOpenAiFunction(String name, String description,
                                                   String[][] params, String[] required) throws Exception {
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", description);
        JSONObject paramsSchema = new JSONObject();
        paramsSchema.put("type", "object");
        JSONObject props = new JSONObject();
        for (String[] p : params) {
            JSONObject prop = new JSONObject();
            prop.put("type", p[1]);
            prop.put("description", p[2]);
            props.put(p[0], prop);
        }
        paramsSchema.put("properties", props);
        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        paramsSchema.put("required", req);
        fn.put("parameters", paramsSchema);
        JSONObject wrapper = new JSONObject();
        wrapper.put("type", "function");
        wrapper.put("function", fn);
        return wrapper;
    }

    // ============================================================
    // Schemas بصيغة Gemini
    // ============================================================
    public static JSONArray buildGeminiFunctionDeclarations() {
        JSONArray arr = new JSONArray();
        try {
            arr.put(buildGeminiFunction(
                    TOOL_READ_FILE,
                    "يقرأ محتوى ملف نصي داخل المشروع.",
                    new String[][]{
                            {"path", "STRING", "المسار النسبي من جذر المشروع"}
                    },
                    new String[]{"path"}
            ));
            arr.put(buildGeminiFunction(
                    TOOL_LIST_FILES,
                    "يسرد كل ملفات المشروع مع أحجامها.",
                    new String[][]{},
                    new String[]{}
            ));
            arr.put(buildGeminiFunction(
                    TOOL_FILE_INFO,
                    "معلومات عن ملف (حجم + عدد أسطر) بدون قراءته.",
                    new String[][]{
                            {"path", "STRING", "المسار النسبي للملف"}
                    },
                    new String[]{"path"}
            ));
            arr.put(buildGeminiFunction(
                    TOOL_SEARCH,
                    "يبحث عن نص في كل ملفات المشروع.",
                    new String[][]{
                            {"query", "STRING", "النص المطلوب البحث عنه"},
                            {"extension", "STRING", "امتداد الملفات (اختياري)"}
                    },
                    new String[]{"query"}
            ));
            // ✅ أداة النقل
            arr.put(buildGeminiFunction(
                    TOOL_MOVE_FILE,
                    "ينقل ملفاً من مسار إلى آخر، أو يُعيد تسميته. " +
                    "يُنشئ المجلدات الوجهة تلقائياً. مفيد لإعادة هيكلة المشروع.",
                    new String[][]{
                            {"from", "STRING", "المسار الحالي للملف"},
                            {"to", "STRING", "المسار الجديد"}
                    },
                    new String[]{"from", "to"}
            ));
        } catch (Exception ignored) {}
        return arr;
    }

    private static JSONObject buildGeminiFunction(String name, String description,
                                                   String[][] params, String[] required) throws Exception {
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", description);
        JSONObject paramsSchema = new JSONObject();
        paramsSchema.put("type", "OBJECT");
        JSONObject props = new JSONObject();
        for (String[] p : params) {
            JSONObject prop = new JSONObject();
            prop.put("type", p[1]);
            prop.put("description", p[2]);
            props.put(p[0], prop);
        }
        paramsSchema.put("properties", props);
        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        paramsSchema.put("required", req);
        fn.put("parameters", paramsSchema);
        return fn;
    }

    // ============================================================
    // تحليل Tool Calls
    // ============================================================
    public static List<ToolCall> parseOpenAiToolCalls(JSONObject message) {
        List<ToolCall> calls = new ArrayList<>();
        if (message == null) return calls;
        JSONArray arr = message.optJSONArray("tool_calls");
        if (arr == null) return calls;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject item = arr.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            JSONObject fn = item.optJSONObject("function");
            if (fn == null) continue;
            String name = fn.optString("name", "");
            String argsRaw = fn.optString("arguments", "{}");
            JSONObject args;
            try {
                args = new JSONObject(argsRaw);
            } catch (Exception e) {
                args = new JSONObject();
            }
            if (name.isEmpty()) continue;
            calls.add(new ToolCall(id, name, args));
        }
        return calls;
    }

    public static List<ToolCall> parseGeminiFunctionCalls(JSONArray parts) {
        List<ToolCall> calls = new ArrayList<>();
        if (parts == null) return calls;
        int counter = 0;
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            JSONObject fnCall = part.optJSONObject("functionCall");
            if (fnCall == null) continue;
            String name = fnCall.optString("name", "");
            JSONObject args = fnCall.optJSONObject("args");
            if (args == null) args = new JSONObject();
            if (name.isEmpty()) continue;
            String id = "gemini_call_" + System.currentTimeMillis() + "_" + (counter++);
            calls.add(new ToolCall(id, name, args));
        }
        return calls;
    }

    // ============================================================
    // تنسيق النتائج
    // ============================================================
    public static JSONObject buildOpenAiToolMessage(ToolResult result) throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("role", "tool");
        msg.put("tool_call_id", result.callId);
        msg.put("content", result.content);
        return msg;
    }

    public static JSONObject buildGeminiFunctionResponse(ToolResult result) throws Exception {
        JSONObject fnResp = new JSONObject();
        JSONObject resp = new JSONObject();
        resp.put("content", result.content);
        if (result.isError) resp.put("error", true);
        fnResp.put("name", result.toolName);
        fnResp.put("response", resp);
        JSONObject part = new JSONObject();
        part.put("functionResponse", fnResp);
        return part;
    }
}