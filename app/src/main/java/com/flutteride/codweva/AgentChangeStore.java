package com.flutteride.codweva;

import java.util.ArrayList;
import java.util.List;

/** يحتفظ بنسخ ما قبل التعديل لتمكين التراجع الفوري عن آخر عملية للوكيل. */
public final class AgentChangeStore {
    private AgentChangeStore() { }

    /**
     * سجل تغيير واحد:
     *   - WRITE: نحفظ النسخة القديمة (أو عدم الوجود).
     *   - DELETE: نحفظ المحتوى (لإعادة إنشائه).
     *   - MOVE: نحفظ المسار الأصلي والوجهة.
     */
    public static final class PreviousFile {
        public final String path;
        public final boolean existed;
        public final String content;

        /** للـ move: الوجهة (المسار الجديد). null للأنواع الأخرى. */
        public final String moveDestination;

        /** هل هذا السجل لعملية نقل؟ */
        public final boolean isMove;

        public PreviousFile(String path, boolean existed, String content) {
            this(path, existed, content, null, false);
        }

        public PreviousFile(String path, boolean existed, String content,
                            String moveDestination, boolean isMove) {
            this.path = path;
            this.existed = existed;
            this.content = content == null ? "" : content;
            this.moveDestination = moveDestination;
            this.isMove = isMove;
        }
    }

    // ============================================================
    // الحفظ
    // ============================================================

    public static List<PreviousFile> capture(ProjectFileService files,
                                             List<AgentEngine.AgentAction> actions) throws Exception {
        ArrayList<PreviousFile> result = new ArrayList<>();
        for (AgentEngine.AgentAction action : actions) {
            if (action == null) continue;

            if (action.isMove) {
                // ✅ نقل: نحفظ المصدر والوجهة
                if (action.path == null || action.path.trim().isEmpty()) continue;
                if (action.destination == null || action.destination.trim().isEmpty()) continue;
                boolean fromExists = files.exists(action.path);
                String fromContent = fromExists ? files.readFile(action.path) : "";
                result.add(new PreviousFile(
                        action.path,
                        fromExists,
                        fromContent,
                        action.destination,
                        true));
                continue;
            }

            // write / delete
            if (action.path == null || action.path.trim().isEmpty()) continue;
            boolean exists = files.exists(action.path);
            String old = exists ? files.readFile(action.path) : "";
            result.add(new PreviousFile(action.path, exists, old));
        }
        return result;
    }

    // ============================================================
    // الاستعادة (بالعكس)
    // ============================================================

    public static void restore(ProjectFileService files, List<PreviousFile> previous) throws Exception {
        // نستعيد بترتيب معكوس — آخر تغيير أولاً
        for (int i = previous.size() - 1; i >= 0; i--) {
            PreviousFile file = previous.get(i);

            if (file.isMove) {
                // ✅ عكس النقل: من destination → path
                if (file.moveDestination == null) continue;
                try {
                    // إذا كان الوجهة موجودة، نرجعها إلى المصدر
                    if (files.exists(file.moveDestination)) {
                        // تأكد أن المصدر الأصلي غير موجود
                        if (files.exists(file.path)) {
                            // حذف المصدر الحالي (لأنه سيعود من الوجهة)
                            try { files.deleteFile(file.path); } catch (Exception ignored) {}
                        }
                        files.moveFile(file.moveDestination, file.path);
                    }
                } catch (Exception ignored) {
                    // إذا فشل، نحاول الحل اليدوي
                    try {
                        if (files.exists(file.moveDestination) && !files.exists(file.path)) {
                            files.moveFile(file.moveDestination, file.path);
                        }
                    } catch (Exception ignored2) {}
                }
                continue;
            }

            if (file.existed) {
                files.writeFile(file.path, file.content);
            } else if (files.exists(file.path)) {
                files.deleteFile(file.path);
            }
        }
    }
}