package com.flutteride.codweva;

/** أوضاع تشغيل مساعد الذكاء الاصطناعي. */
public enum AiMode {

    /** مساعد ذكي: يرى المشروع كاملاً في كل رسالة ويعدّل أي ملف. */
    SMART("smart", "مساعد ذكي", "يرى المشروع كاملاً في كل رسالة. مثالي للمشاريع الصغيرة والمتوسطة."),

    /** وكيل كامل: يستخدم أدوات للقراءة والبحث والاستكشاف (قادم قريباً). */
    AGENTIC("agentic", "وكيل كامل", "يستكشف المشروع بنفسه ويقرأ الملفات حسب الحاجة. مثالي للمشاريع الكبيرة.");

    public final String id;
    public final String displayName;
    public final String description;

    AiMode(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public static AiMode fromId(String id) {
        if (id == null) return SMART;
        for (AiMode mode : values()) {
            if (mode.id.equals(id)) return mode;
        }
        return SMART;
    }
}