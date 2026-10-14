package com.flutteride.codweva;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** وكيل آمن: يحول إجراءات JSON أو كتل الكود المقترحة إلى تغييرات تنتظر موافقة المستخدم. */
public final class AgentEngine {
    private static final Pattern ACTION_PATTERN = Pattern.compile(
            "<klencod_action>\\s*(.*?)\\s*</klencod_action>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:dart)?\\s*(.*?)```",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DART_PATH_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_./-])((?:lib|test|bin|web)/[A-Za-z0-9_./-]+\\.dart|[A-Za-z0-9_.-]+\\.dart)");

    private static final Pattern READ_ONLY_PATTERNS = Pattern.compile(
            "(?i)(?:^|\\s)(?:اقرأ|اقرء|أرني|ارني|اعرض|أعرض|اشرح|وضح|ما\\s+هو|ما\\s+هي|كيف|أين|اين|ماذا|هل\\s+يمكن|اشرح\\s+لي|اقتبس|انقل\\s+لي)(?:\\s|$|[،,.!?])");

    private static final Pattern MODIFY_PATTERNS = Pattern.compile(
            "(?i)(?:^|\\s)(?:أضف|اضف|عدل|عدّل|أصلح|اصلح|غير|غيّر|احذف|امسح|أنشئ|انشئ|اكتب|أكتب|استبدل|استبدلي|حول|حوّل|طور|حسّن|حدث|حدّث|refactor|add|fix|modify|update|create|delete|remove|change|write|insert|append|move|rename|نقل|انقل)(?:\\s|$|[،,.!?])");

    private AgentEngine() { }

    // ============================================================
    // التحليل
    // ============================================================

    public static List<AgentAction> parseActions(String response) {
        ArrayList<AgentAction> actions = new ArrayList<>();
        if (response == null) return actions;
        Matcher matcher = ACTION_PATTERN.matcher(response);
        while (matcher.find()) {
            try {
                JSONObject json = new JSONObject(matcher.group(1));
                String action = json.optString("action", "");
                String path = json.optString("path", "");
                if ("write_file".equals(action) || "create_file".equals(action)) {
                    actions.add(AgentAction.write(path, json.optString("content", "")));
                } else if ("delete_file".equals(action)) {
                    actions.add(AgentAction.delete(path));
                } else if ("move_file".equals(action)) {
                    // ✅ إجراء جديد
                    String from = json.optString("from", path);
                    String to = json.optString("to", "");
                    if (!from.isEmpty() && !to.isEmpty()) {
                        actions.add(AgentAction.move(from, to));
                    }
                }
            } catch (Exception ignored) {
                // الرد النصي يبقى ظاهرًا للمستخدم، والإجراء المعيب لا ينفذ.
            }
        }
        return actions;
    }

    @Deprecated
    public static List<AgentAction> parseCodeBlocks(String response, String fallbackPath) {
        ArrayList<AgentAction> actions = new ArrayList<>();
        if (response == null) return actions;
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(response);
        while (matcher.find()) {
            String content = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (content.isEmpty()) continue;
            String path = findPathBefore(response, matcher.start());
            if (path.isEmpty()) path = fallbackPath == null ? "" : fallbackPath.trim();
            if (!isSafePath(path)) continue;
            actions.add(AgentAction.write(path, content));
        }
        return actions;
    }

    private static String findPathBefore(String response, int end) {
        String prefix = response.substring(0, Math.max(0, end));
        Matcher matcher = DART_PATH_PATTERN.matcher(prefix);
        String path = "";
        while (matcher.find()) path = matcher.group(1);
        return path;
    }

    private static boolean isSafePath(String path) {
        return path != null && !path.isEmpty() && !path.startsWith("/")
                && !path.contains("../") && !path.equals("..");
    }

    public static String removeActionBlocks(String response) {
        if (response == null) return "";
        return ACTION_PATTERN.matcher(response).replaceAll("").trim();
    }

    // ============================================================
    // تعليمات الوكيل
    // ============================================================

    public static String buildInstruction() {
        return "قواعد إلزامية:\n"
                + "\n"
                + "1. عند طلب المستخدم **القراءة فقط** (اقرأ، أرني، اعرض، اشرح، وضّح، ما هو، كيف، أين، ماذا، هل):\n"
                + "   • أجب بالنص العادي فقط.\n"
                + "   • **لا** تُرسل أي وسم <klencod_action>.\n"
                + "   • **لا** تُرسل كود داخل ```dart على أنها تعديل — يمكنك عرضه كنص توضيحي فقط.\n"
                + "   • مثال: «اقرأ أول 5 أسطر» → اكتب الأسطر كنص، ولا تقترح أي تعديل.\n"
                + "\n"
                + "2. عند طلب المستخدم **التعديل** (أضف، عدّل، أصلح، غيّر، احذف، أنشئ، اكتب، استبدل، حدّث، انقل، أعد تسمية):\n"
                + "   • أرسل التعديلات فقط داخل وسوم <klencod_action>.\n"
                + "   • كل إجراء داخل وسم منفصل بصيغة JSON صحيحة.\n"
                + "   • استخدم فقط: write_file، create_file، delete_file، move_file.\n"
                + "\n"
                + "3. لم تُطلب منك تعديلات = لا ترسل أي وسوم.\n"
                + "\n"
                + "4. عند إرسال تعديل، التزم بالصيغ التالية:\n"
                + "   • كتابة/تعديل ملف:\n"
                + "     <klencod_action>{\"action\":\"write_file\",\"path\":\"lib/main.dart\",\"content\":\"...\"}</klencod_action>\n"
                + "   • حذف ملف:\n"
                + "     <klencod_action>{\"action\":\"delete_file\",\"path\":\"lib/old.dart\"}</klencod_action>\n"
                + "   • نقل/إعادة تسمية ملف:\n"
                + "     <klencod_action>{\"action\":\"move_file\",\"from\":\"old/path.dart\",\"to\":\"new/path.dart\"}</klencod_action>\n"
                + "\n"
                + "5. أرسل إجراءً واحداً لكل ملف. لا تُدمج ملفات متعددة في إجراء واحد.\n"
                + "\n"
                + "6. مسارات آمنة فقط: ابدأ من جذر المشروع (lib/, test/, pubspec.yaml).\n"
                + "   لا مسارات تبدأ بـ / ولا تحتوي ../ ولا مسارات خارج المشروع.\n"
                + "\n"
                + "7. لا تعدّل ملفات الخادم أو البناء (build/, .git/, .dart_tool/, .gradle/) — ممنوعة تماماً.\n"
                + "\n"
                + "8. لن يُطبّق أي إجراء إلا بعد موافقة المستخدم. لذلك لا تفترض أنك عدّلت شيئاً.\n"
                + "\n"
                + "9. في ردود القراءة، يمكنك استخدام Markdown عادي (نقاط، اقتباسات، كود توضيحي).\n"
                + "\n"
                + "10. عند تغيير بنية المشروع (مثل إعادة تسمية مجلد حزمة):\n"
                + "    • استخدم move_file لنقل الملفات إلى المسارات الجديدة.\n"
                + "    • ثم استخدم write_file لتعديل محتوى الملفات (استيرادات، package، إلخ).\n"
                + "    • كل ذلك في نفس الرد إذا أمكن.\n"
                + "\n"
                + "11. إذا لم تكن متأكداً مما إذا كان الطلب قراءة أم تعديل، اسأل المستخدم أولاً.";
    }

    // ============================================================
    // دالة مساعدة
    // ============================================================

    public static boolean isReadOnlyRequest(String userMessage) {
        if (userMessage == null) return false;
        String msg = userMessage.trim();
        if (msg.isEmpty()) return false;
        boolean hasModify = MODIFY_PATTERNS.matcher(msg).find();
        if (hasModify) return false;
        boolean hasRead = READ_ONLY_PATTERNS.matcher(msg).find();
        return hasRead;
    }

    // ============================================================
    // التطبيق
    // ============================================================

    public static void apply(ProjectFileService files, AgentAction action) throws Exception {
        if (action == null) throw new IllegalArgumentException("إجراء فارغ");
        if (action.isDelete) {
            files.deleteFile(action.path);
        } else if (action.isMove) {
            files.moveFile(action.path, action.destination);
        } else {
            files.writeFile(action.path, action.content);
        }
    }

    // ============================================================
    // نموذج الإجراء
    // ============================================================

    public static final class AgentAction {
        public final String type;
        public final String path;
        public final String content;
        public final String destination; // ✅ للـ move_file
        public final boolean isDelete;
        public final boolean isMove;

        private AgentAction(String type, String path, String content,
                            String destination, boolean isDelete, boolean isMove) {
            this.type = type;
            this.path = path;
            this.content = content;
            this.destination = destination;
            this.isDelete = isDelete;
            this.isMove = isMove;
        }

        static AgentAction write(String path, String content) {
            return new AgentAction("write_file", path, content, null, false, false);
        }

        static AgentAction delete(String path) {
            return new AgentAction("delete_file", path, "", null, true, false);
        }

        /** ✅ إجراء نقل: from = path، to = destination. */
        static AgentAction move(String from, String to) {
            return new AgentAction("move_file", from, "", to, false, true);
        }

        public String summary() {
            if (isDelete) return "حذف: " + path;
            if (isMove) return "نقل: " + path + " → " + destination;
            return type + ": " + path + " (" + content.length() + " حرف)";
        }
    }
}