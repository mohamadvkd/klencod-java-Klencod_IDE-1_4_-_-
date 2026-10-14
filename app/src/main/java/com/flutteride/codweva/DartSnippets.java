package com.flutteride.codweva;

import java.util.Locale;

/**
 * DartSnippets — مدير snippets لإدراج أكواد متعددة الأسطر.
 *
 * الميزات:
 *   - دعم $0 كموضع نهائي للمؤشر.
 *   - محاذاة تلقائية (auto-indent) حسب السطر الحالي.
 *   - استبدال البادئة الفعلية بـ snippet.
 *   - حساب موضع المؤشر النهائي.
 *
 * مثال:
 *   المستخدم كتب: "Col" والمؤشر بعده.
 *   السnippet الخاص بـ Column:
 *     "Column(\n  mainAxisAlignment: MainAxisAlignment.start,\n  ...\n)"
 *   بعد الإدراج، النتيجة:
 *     Column(
 *       mainAxisAlignment: MainAxisAlignment.start,
 *       ...
 *     )_
 *   والمؤشر بعد ")" أو داخل children (حسب موضع $0).
 */
public final class DartSnippets {

    private DartSnippets() {}

    // ============================================================
    // النتيجة
    // ============================================================
    public static final class InsertionResult {
        /** النص الكامل الجديد بعد الإدراج. */
        public final String newText;
        /** موضع المؤشر الجديد في newText. */
        public final int newCursorPosition;

        InsertionResult(String newText, int newCursorPosition) {
            this.newText = newText;
            this.newCursorPosition = newCursorPosition;
        }
    }

    // ============================================================
    // الدالة الرئيسية
    // ============================================================

    /**
     * يُدرج snippet مكان الكلمة الحالية (بين prefixStart و prefixEnd).
     *
     * @param fullText      النص الكامل للمحرر.
     * @param prefixStart   بداية الكلمة الحالية (التي ستُستبدل).
     * @param prefixEnd     نهاية الكلمة الحالية (= موضع المؤشر عادة).
     * @param snippet       النص المُدرج (قد يحتوي $0).
     * @return نتيجة الإدراج.
     */
    public static InsertionResult insert(String fullText,
                                         int prefixStart,
                                         int prefixEnd,
                                         String snippet) {
        if (fullText == null) fullText = "";
        if (snippet == null) snippet = "";

        // 1) تحقق من صحة المواضع
        prefixStart = clamp(prefixStart, 0, fullText.length());
        prefixEnd = clamp(prefixEnd, prefixStart, fullText.length());

        // 2) استنتاج المسافة البادئة للسطر الحالي
        String indent = detectIndent(fullText, prefixStart);

        // 3) تعديل الـ snippet:
        //    - إزالة $0 وحفظ موضعه.
        //    - تطبيق indentation على كل سطر.
        SnippetPrepared prepared = prepareSnippet(snippet, indent);

        // 4) بناء النص الجديد
        String before = fullText.substring(0, prefixStart);
        String after = fullText.substring(prefixEnd);
        String newText = before + prepared.text + after;

        // 5) حساب موضع المؤشر الجديد
        int newCursor = prefixStart + prepared.cursorOffset;

        return new InsertionResult(newText, newCursor);
    }

    // ============================================================
    // معالجة الـ snippet
    // ============================================================
    private static final class SnippetPrepared {
        final String text;
        final int cursorOffset; // نسبة إلى بداية snippet

        SnippetPrepared(String text, int cursorOffset) {
            this.text = text;
            this.cursorOffset = cursorOffset;
        }
    }

    /**
     * يُعدّ snippet للإدراج:
     *   - يستخرج موضع $0.
     *   - يطبّق indent على كل سطر.
     *   - يعيد النص + موضع المؤشر.
     */
    private static SnippetPrepared prepareSnippet(String snippet, String baseIndent) {
        if (snippet == null) snippet = "";

        // 1) ابحث عن $0 (أول ظهور)
        int placeholderPos = snippet.indexOf("$0");
        boolean hasPlaceholder = placeholderPos >= 0;

        // 2) أزل $0 من النص (سنعيد موضعه يدوياً)
        String cleaned = hasPlaceholder
                ? snippet.substring(0, placeholderPos) + snippet.substring(placeholderPos + 2)
                : snippet;

        // 3) طبّق indent على كل سطر بعد الأول
        String[] lines = cleaned.split("\n", -1);
        StringBuilder sb = new StringBuilder(cleaned.length() + baseIndent.length() * lines.length);
        int cursorOffset = 0;
        boolean cursorSet = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == 0) {
                // أول سطر: يُدرج مباشرة (بدون indent إضافي)
                if (hasPlaceholder && !cursorSet && placeholderPos <= line.length()) {
                    cursorOffset = placeholderPos;
                    cursorSet = true;
                }
                sb.append(line);
            } else {
                sb.append('\n');
                // إضافة indent قبل السطر إن لم يكن فارغاً أو كان يحوي مسافات
                if (!line.isEmpty()) {
                    sb.append(baseIndent).append(line);
                }
                // ضبط cursorOffset
                if (hasPlaceholder && !cursorSet) {
                    int targetPos = placeholderPos - (sb.length() - baseIndent.length() - line.length());
                    if (targetPos >= 0) {
                        cursorOffset = sb.length() - line.length() - baseIndent.length() + targetPos;
                        if (cursorOffset >= 0 && cursorOffset <= sb.length()) {
                            cursorSet = true;
                        }
                    }
                }
            }
        }

        // إذا لم يُضبط cursorOffset بعد، اجعله في نهاية النص
        if (!cursorSet) {
            cursorOffset = sb.length();
        }

        return new SnippetPrepared(sb.toString(), cursorOffset);
    }

    // ============================================================
    // استنتاج المسافة البادئة
    // ============================================================

    /**
     * يستنتج المسافة البادئة للسطر الذي يحتوي على الموضع المعطى.
     * مثلاً، إذا كان السطر يبدأ بـ "    " (4 مسافات)، يٌعيد "    ".
     */
    public static String detectIndent(String text, int position) {
        if (text == null || position < 0 || position > text.length()) return "";

        // 1) اذهب لبداية السطر
        int lineStart = position;
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }

        // 2) اقرأ المسافات البادئة
        int i = lineStart;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                break;
            }
        }

        return text.substring(lineStart, i);
    }

    // ============================================================
    // استبدال $0 فقط (بدون indentation)
    // ============================================================

    /**
     * استبدال $0 بـ position معيّن، دون تطبيق indentation.
     * مفيد عندما نُدرج snippet في سياق موجود.
     */
    public static String replacePlaceholder(String snippet, int cursorOffset) {
        if (snippet == null) return "";
        int pos = snippet.indexOf("$0");
        if (pos < 0) return snippet;
        return snippet.substring(0, pos) + snippet.substring(pos + 2);
    }

    /**
     * يُرجع موضع $0 في snippet (أو -1 إذا لم يوجد).
     */
    public static int findPlaceholder(String snippet) {
        if (snippet == null) return -1;
        return snippet.indexOf("$0");
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    /**
     * يستنتج "indent step" (مسافتان أو 4 أو tab) من تحليل النص.
     * الافتراضي: مسافتان.
     */
    public static String detectIndentStep(String text) {
        if (text == null || text.isEmpty()) return "  ";

        // ابحث عن أول سطر يبدأ بمسافات متسقة
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.isEmpty()) continue;
            int leadingSpaces = 0;
            int leadingTabs = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ') leadingSpaces++;
                else if (line.charAt(i) == '\t') leadingTabs++;
                else break;
            }

            if (leadingTabs > 0) return "\t";
            if (leadingSpaces >= 4) return "    "; // 4 مسافات
            if (leadingSpaces >= 2) return "  ";  // مسافتان
        }

        return "  "; // افتراضي
    }

    /**
     * تحقق: هل يحتوي snippet على $0؟
     */
    public static boolean hasPlaceholder(String snippet) {
        return snippet != null && snippet.contains("$0");
    }

    // ============================================================
    // اختبار سريع (يمكن حذفه)
    // ============================================================
    /**
     * مثال للاستخدام:
     *
     *   String fullText = "    Col";
     *   int prefixStart = 4;   // موضع "C"
     *   int prefixEnd = 7;     // بعد "Col"
     *   String snippet = "Column(\n  children: [\n    $0\n  ],\n)";
     *   InsertionResult r = DartSnippets.insert(fullText, prefixStart, prefixEnd, snippet);
     *
     *   النتيجة:
     *     newText = "    Column(\n      children: [\n        \n      ],\n    )"
     *     newCursorPosition = موضع بعد "        " (داخل children)
     */
    // ============================================================
}