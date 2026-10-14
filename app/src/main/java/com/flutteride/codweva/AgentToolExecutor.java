package com.flutteride.codweva;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ينفّذ استدعاءات أدوات القراءة (read_file/list_files/get_file_info/search).
 *
 * ملاحظة مهمة:
 *   move_file لا تُنفّذ هنا. الوكيل يقترحها عبر <klencod_action>، فتمر
 *   بموافقة المستخدم ثم تُطبَّق عبر AgentEngine.
 */
public final class AgentToolExecutor {

    private static final int MAX_READ_BYTES = 250 * 1024;
    private static final int MAX_SEARCH_RESULTS = 200;
    private static final int SEARCH_SNIPPET_LEN = 120;
    private static final int SEARCH_CONTEXT_LINES = 1;

    private final ProjectFileService files;
    private final File projectRoot;

    public AgentToolExecutor(File projectRoot) {
        if (projectRoot == null) throw new IllegalArgumentException("projectRoot == null");
        this.projectRoot = projectRoot;
        this.files = new ProjectFileService(projectRoot);
    }

    // ============================================================
    // نقطة الدخول
    // ============================================================
    public AgentTool.ToolResult execute(AgentTool.ToolCall call) {
        if (call == null) {
            return AgentTool.ToolResult.error("", "", "استدعاء فارغ");
        }
        String name = call.name;
        try {
            switch (name) {
                case AgentTool.TOOL_READ_FILE:   return readFile(call);
                case AgentTool.TOOL_LIST_FILES:  return listFiles(call);
                case AgentTool.TOOL_FILE_INFO:   return getFileInfo(call);
                case AgentTool.TOOL_SEARCH:      return search(call);
                case AgentTool.TOOL_MOVE_FILE:
                    // ✅ move_file لا تُنفّذ فوراً — يجب أن تمر عبر klencod_action
                    return AgentTool.ToolResult.error(call.id, name,
                            "move_file يجب أن تُرسَل كإجراء عبر <klencod_action> " +
                            "ليتمكن المستخدم من الموافقة عليها. أعد إرسالها كوسم JSON.");
                default:
                    return AgentTool.ToolResult.error(call.id, name,
                            "أداة غير معروفة: " + name);
            }
        } catch (SecurityException se) {
            return AgentTool.ToolResult.error(call.id, name,
                    "مسار مرفوض: " + se.getMessage());
        } catch (Exception e) {
            return AgentTool.ToolResult.error(call.id, name,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    // ============================================================
    // read_file(path)
    // ============================================================
    private AgentTool.ToolResult readFile(AgentTool.ToolCall call) throws Exception {
        String path = call.getString("path");
        if (path.trim().isEmpty()) {
            return AgentTool.ToolResult.error(call.id, call.name, "المسار مطلوب");
        }
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) {
            return AgentTool.ToolResult.error(call.id, call.name, "مسار غير صالح");
        }

        File file = files.safeFile(normalized);
        if (!file.exists() || !file.isFile()) {
            return AgentTool.ToolResult.error(call.id, call.name,
                    "الملف غير موجود: " + normalized);
        }
        if (file.length() > MAX_READ_BYTES) {
            return AgentTool.ToolResult.error(call.id, call.name,
                    "الملف كبير جداً (" + formatSize(file.length()) +
                    "). استخدم search بدلاً من read_file.");
        }

        String content = readText(file);
        if (content == null) {
            return AgentTool.ToolResult.error(call.id, call.name,
                    "تعذر قراءة الملف (قد يكون ثنائياً)");
        }

        int lines = countLines(content);
        StringBuilder sb = new StringBuilder(content.length() + 128);
        sb.append("--- ").append(normalized)
          .append("  (").append(formatSize(file.length()))
          .append(", ").append(lines).append(" سطر) ---\n");
        sb.append(content);
        if (!content.endsWith("\n")) sb.append("\n");
        sb.append("--- نهاية ").append(normalized).append(" ---");

        return AgentTool.ToolResult.success(call.id, call.name, sb.toString());
    }

    // ============================================================
    // list_files()
    // ============================================================
    private AgentTool.ToolResult listFiles(AgentTool.ToolCall call) throws Exception {
        List<String> relativePaths = files.listFiles();
        if (relativePaths.isEmpty()) {
            return AgentTool.ToolResult.success(call.id, call.name,
                    "المشروع لا يحتوي أي ملفات نصية.");
        }
        relativePaths.sort(String.CASE_INSENSITIVE_ORDER);

        StringBuilder sb = new StringBuilder(relativePaths.size() * 48 + 64);
        sb.append("ملفات المشروع (").append(relativePaths.size()).append("):\n");
        for (String rel : relativePaths) {
            try {
                File f = files.safeFile(rel);
                long size = f.length();
                sb.append(rel).append("  (").append(formatSize(size)).append(")");
                if (size <= 30 * 1024) {
                    int lines = countLinesQuietly(f);
                    if (lines >= 0) sb.append(", ").append(lines).append(" سطر");
                }
                sb.append("\n");
            } catch (Exception ignored) {}
        }
        return AgentTool.ToolResult.success(call.id, call.name, sb.toString());
    }

    // ============================================================
    // get_file_info(path)
    // ============================================================
    private AgentTool.ToolResult getFileInfo(AgentTool.ToolCall call) throws Exception {
        String path = call.getString("path");
        if (path.trim().isEmpty()) {
            return AgentTool.ToolResult.error(call.id, call.name, "المسار مطلوب");
        }
        String normalized = normalizePath(path);
        File file = files.safeFile(normalized);
        if (!file.exists() || !file.isFile()) {
            return AgentTool.ToolResult.error(call.id, call.name,
                    "الملف غير موجود: " + normalized);
        }
        long size = file.length();
        StringBuilder sb = new StringBuilder(128);
        sb.append("الملف: ").append(normalized).append("\n");
        sb.append("الحجم: ").append(formatSize(size)).append("\n");
        if (size <= MAX_READ_BYTES) {
            int lines = countLinesQuietly(file);
            if (lines >= 0) sb.append("عدد الأسطر: ").append(lines).append("\n");
        } else {
            sb.append("عدد الأسطر: (الملف كبير جداً للعد)\n");
        }
        sb.append("ثنائي: ").append(isBinaryPath(normalized) ? "نعم" : "لا").append("\n");
        return AgentTool.ToolResult.success(call.id, call.name, sb.toString());
    }

    // ============================================================
    // search(query, extension?)
    // ============================================================
    private AgentTool.ToolResult search(AgentTool.ToolCall call) throws Exception {
        String query = call.getString("query");
        if (query.trim().isEmpty()) {
            return AgentTool.ToolResult.error(call.id, call.name, "نص البحث مطلوب");
        }
        String ext = call.getOptionalString("extension");
        if (ext != null) {
            ext = ext.trim().toLowerCase(Locale.US);
            if (ext.startsWith(".")) ext = ext.substring(1);
        }

        String queryLower = query.toLowerCase(Locale.US);
        List<String> allFiles = files.listFiles();
        StringBuilder sb = new StringBuilder(1024);
        int matchCount = 0;
        int fileCount = 0;

        for (String rel : allFiles) {
            if (matchCount >= MAX_SEARCH_RESULTS) break;
            if (ext != null && !rel.toLowerCase(Locale.US).endsWith("." + ext)) continue;

            try {
                File file = files.safeFile(rel);
                if (file.length() > MAX_READ_BYTES) continue;
                if (isBinaryPath(rel)) continue;

                String content = readText(file);
                if (content == null) continue;

                String[] lines = content.split("\n", -1);
                List<Integer> hits = new ArrayList<>();
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].toLowerCase(Locale.US).contains(queryLower)) {
                        hits.add(i);
                    }
                }
                if (hits.isEmpty()) continue;

                fileCount++;
                sb.append("\n=== ").append(rel)
                  .append(" (").append(hits.size()).append(" نتيجة) ===\n");

                for (Integer lineIdx : hits) {
                    if (matchCount >= MAX_SEARCH_RESULTS) {
                        sb.append("... (تم الوصول للحد الأقصى من النتائج)\n");
                        break;
                    }
                    int start = Math.max(0, lineIdx - SEARCH_CONTEXT_LINES);
                    int end = Math.min(lines.length - 1, lineIdx + SEARCH_CONTEXT_LINES);
                    for (int i = start; i <= end; i++) {
                        String prefix = (i == lineIdx) ? "> " : "  ";
                        String snippet = lines[i];
                        if (snippet.length() > SEARCH_SNIPPET_LEN) {
                            snippet = snippet.substring(0, SEARCH_SNIPPET_LEN) + "…";
                        }
                        sb.append(prefix)
                          .append("سطر ").append(i + 1).append(": ")
                          .append(snippet).append("\n");
                    }
                    matchCount++;
                }
            } catch (Exception ignored) {}
        }

        if (matchCount == 0) {
            return AgentTool.ToolResult.success(call.id, call.name,
                    "لا نتائج للبحث عن: \"" + query + "\"" +
                    (ext != null ? " (النطاق: *." + ext + ")" : ""));
        }

        StringBuilder header = new StringBuilder(128);
        header.append("نتائج البحث عن \"").append(query).append("\"");
        if (ext != null) header.append(" (النطاق: *.").append(ext).append(")");
        header.append(":\n");
        header.append("ملفات مطابقة: ").append(fileCount)
              .append(", نتائج: ").append(matchCount).append("\n");
        header.append(sb);

        return AgentTool.ToolResult.success(call.id, call.name, header.toString());
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================
    private String normalizePath(String path) {
        if (path == null) return "";
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("./")) p = p.substring(2);
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }

    private String readText(File file) {
        try {
            StringBuilder sb = new StringBuilder((int) Math.min(file.length(), 65536));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            try {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
            } finally {
                reader.close();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    private int countLinesQuietly(File file) {
        try {
            String content = readText(file);
            if (content == null) return -1;
            return countLines(content);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean isBinaryPath(String path) {
        if (path == null) return false;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return false;
        String ext = path.substring(dot + 1).toLowerCase(Locale.US);
        switch (ext) {
            case "png": case "jpg": case "jpeg": case "gif": case "webp":
            case "bmp": case "ico": case "ttf": case "otf": case "woff":
            case "woff2": case "mp3": case "mp4": case "wav":
            case "apk": case "aab": case "jar": case "so": case "dex":
            case "keystore": case "jks": case "zip": case "rar": case "7z":
                return true;
            default:
                return false;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}