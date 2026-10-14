package com.flutteride.codweva;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ProjectContextBuilder — يبني سياقاً حقيقياً كاملاً للمشروع لعرضه على نموذج AI.
 *
 * المبدأ:
 *   1. الخريطة الحقيقية لكل ملفات المشروع (بدون استثناءات ما عدا مجلدات البناء).
 *   2. محتوى كامل للملفات المهمة فقط (لتوفير التوكن).
 *   3. عند الطلب: بعض الإحصاءات المفيدة (إجمالي الملفات، الأسطر).
 *
 * ملاحظات الأداء:
 *   - يقرأ من القرص في كل مرة → يعكس دائماً الوضع الحقيقي.
 *   - الحد الأقصى لحجم الملف القابل للقراءة: 250KB (نفس ما في ProjectFileService).
 */
public final class ProjectContextBuilder {

    // ============================================================
    // المستثنيات
    // ============================================================
    private static final Set<String> EXCLUDED_DIRS = new HashSet<>();
    static {
        EXCLUDED_DIRS.add(".git");
        EXCLUDED_DIRS.add(".gradle");
        EXCLUDED_DIRS.add(".dart_tool");
        EXCLUDED_DIRS.add("build");
        EXCLUDED_DIRS.add("node_modules");
        EXCLUDED_DIRS.add(".idea");
        EXCLUDED_DIRS.add(".vscode");
        EXCLUDED_DIRS.add("__pycache__");
        EXCLUDED_DIRS.add(".next");
        EXCLUDED_DIRS.add("Pods"); // ios/Pods
        EXCLUDED_DIRS.add(".symlinks");
        EXCLUDED_DIRS.add("DerivedData");
    }

    private static final Set<String> EXCLUDED_FILES = new HashSet<>();
    static {
        EXCLUDED_FILES.add(".DS_Store");
        EXCLUDED_FILES.add("Thumbs.db");
    }

    /** ملفات تعتبر "ثنائية" ولا نحاول قراءة محتواها كنص. */
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>();
    static {
        BINARY_EXTENSIONS.add("png");
        BINARY_EXTENSIONS.add("jpg");
        BINARY_EXTENSIONS.add("jpeg");
        BINARY_EXTENSIONS.add("gif");
        BINARY_EXTENSIONS.add("webp");
        BINARY_EXTENSIONS.add("bmp");
        BINARY_EXTENSIONS.add("ico");
        BINARY_EXTENSIONS.add("svg"); // SVG نصي، لكن نعامله كملف عرض
        BINARY_EXTENSIONS.add("ttf");
        BINARY_EXTENSIONS.add("otf");
        BINARY_EXTENSIONS.add("woff");
        BINARY_EXTENSIONS.add("woff2");
        BINARY_EXTENSIONS.add("mp3");
        BINARY_EXTENSIONS.add("mp4");
        BINARY_EXTENSIONS.add("wav");
        BINARY_EXTENSIONS.add("apk");
        BINARY_EXTENSIONS.add("aab");
        BINARY_EXTENSIONS.add("jar");
        BINARY_EXTENSIONS.add("so");
        BINARY_EXTENSIONS.add("dex");
        BINARY_EXTENSIONS.add("keystore");
        BINARY_EXTENSIONS.add("jks");
        BINARY_EXTENSIONS.add("zip");
        BINARY_EXTENSIONS.add("rar");
        BINARY_EXTENSIONS.add("7z");
    }

    // ============================================================
    // الحدود
    // ============================================================
    /** حجم أقصى لقراءة ملف كنص (نفس ما في ProjectFileService). */
    private static final long MAX_FILE_SIZE = 250L * 1024L; // 250 KB

    /** حجم أقصى لملف يُقرأ كاملاً في السياق دون اختصار. */
    private static final long FULL_CONTENT_THRESHOLD = 30L * 1024L; // 30 KB

    /** حجم صغير → يُقرأ كاملاً دون سؤال. */
    private static final long SMALL_FILE_THRESHOLD = 5L * 1024L; // 5 KB

    /** عدد أسطر الملفات الكبيرة (رأس + ذيل). */
    private static final int LARGE_FILE_HEAD_LINES = 50;
    private static final int LARGE_FILE_TAIL_LINES = 10;

    /** حد أقصى إجمالي لعدد الملفات المسرودة في الخريطة (حماية من مشاريع ضخمة). */
    private static final int MAX_MAP_FILES = 500;

    /** حد أقصى لطول السياق الكلي (حماية tokens). */
    private static final int MAX_CONTEXT_CHARS = 60000;

    // ============================================================
    // أنواع النتائج
    // ============================================================
    public static final class Context {
        /** نص كامل جاهز للإدراج في system prompt. */
        public final String text;
        /** عدد الملفات في الخريطة. */
        public final int totalFiles;
        /** عدد الملفات التي أُرفق محتواها كاملاً. */
        public final int fullContentFiles;

        Context(String text, int totalFiles, int fullContentFiles) {
            this.text = text;
            this.totalFiles = totalFiles;
            this.fullContentFiles = fullContentFiles;
        }
    }

    private static final class FileEntry {
        final File file;
        final String relativePath;
        final long size;
        final boolean isBinary;
        int lineCount; // -1 = لم يُحسب

        FileEntry(File file, String relativePath, long size, boolean isBinary) {
            this.file = file;
            this.relativePath = relativePath;
            this.size = size;
            this.isBinary = isBinary;
            this.lineCount = -1;
        }
    }

    private ProjectContextBuilder() {}

    // ============================================================
    // الدالة الرئيسية
    // ============================================================

    /**
     * يبني السياق الكامل.
     *
     * @param projectDir جذر المشروع (مطلوب).
     * @param currentFile الملف المفتوح حالياً (قد يكون null).
     * @param userMessage رسالة المستخدم الحالية (لبحث محتمل عن أسماء ملفات).
     * @param manuallyAttachedContent محتوى أرفقه المستخدم يدوياً (قد يكون null).
     * @return سياق جاهز للإدراج.
     */
    public static Context build(File projectDir,
                                String currentFile,
                                String userMessage,
                                String manuallyAttachedContent) {
        if (projectDir == null || !projectDir.isDirectory()) {
            return new Context("", 0, 0);
        }

        // 1) اجمع كل الملفات النصية والثنائية
        List<FileEntry> allFiles = new ArrayList<>();
        collectFiles(projectDir, projectDir, allFiles, 0);

        // 2) رتّب أبجدياً حسب المسار النسبي
        Collections.sort(allFiles, (a, b) -> a.relativePath.compareToIgnoreCase(b.relativePath));

        // 3) حدد الملفات المهمة (التي سنُرفق محتواها)
        Set<String> importantPaths = determineImportantFiles(
                projectDir, currentFile, userMessage, allFiles);

        // 4) ابنِ النص
        StringBuilder sb = new StringBuilder(16384);
        int fullContentCount = 0;

        // --- 4.1: رأس السياق
        sb.append("=== سياق المشروع ===\n");
        sb.append("جذر المشروع: ").append(projectDir.getName()).append("\n");
        sb.append("الملفات: ").append(allFiles.size()).append("\n");
        if (currentFile != null && !currentFile.isEmpty()) {
            sb.append("الملف المفتوح حالياً: ").append(currentFile).append("\n");
        }
        sb.append("\n");

        // --- 4.2: خريطة المشروع الكاملة
        sb.append("=== الخريطة الكاملة ===\n");
        int mapCount = 0;
        for (FileEntry entry : allFiles) {
            if (mapCount >= MAX_MAP_FILES) {
                sb.append("... (عدد الملفات يتجاوز الحد الأقصى للعرض: ")
                  .append(MAX_MAP_FILES).append(")\n");
                break;
            }
            sb.append(entry.relativePath);
            sb.append("  (").append(formatSize(entry.size)).append(")");
            if (!entry.isBinary && entry.lineCount > 0) {
                sb.append(", ").append(entry.lineCount).append(" سطر");
            }
            if (entry.isBinary) {
                sb.append(" [binary]");
            }
            sb.append("\n");
            mapCount++;
        }
        sb.append("\n");

        // --- 4.3: محتوى الملفات المهمة
        if (!importantPaths.isEmpty()) {
            sb.append("=== محتوى الملفات المهمة ===\n\n");
            int writtenChars = sb.length();
            for (FileEntry entry : allFiles) {
                if (!importantPaths.contains(entry.relativePath)) continue;
                if (entry.isBinary) continue;
                if (entry.size > MAX_FILE_SIZE) continue;

                // تحقق من الحد الأقصى للسياق
                if (writtenChars > MAX_CONTEXT_CHARS) {
                    sb.append("... (تم الوصول للحد الأقصى لحجم السياق) ...\n");
                    break;
                }

                String content = readFileQuietly(entry.file);
                if (content == null) continue;

                String block = formatFileBlock(entry, content);
                sb.append(block);
                writtenChars += block.length();
                fullContentCount++;
            }
        }

        // --- 4.4: محتوى إرفاق يدوي (إن وُجد)
        if (manuallyAttachedContent != null && !manuallyAttachedContent.trim().isEmpty()) {
            sb.append("\n=== محتوى أرفقه المستخدم يدوياً ===\n");
            sb.append(manuallyAttachedContent);
            sb.append("\n=== نهاية الإرفاق ===\n");
        }

        sb.append("\n=== نهاية سياق المشروع ===\n");

        return new Context(sb.toString(), allFiles.size(), fullContentCount);
    }

    // ============================================================
    // جمع الملفات
    // ============================================================

    private static void collectFiles(File root, File dir, List<FileEntry> out, int depth) {
        if (depth > 20) return; // حماية من التداخل العميق جداً
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            String name = child.getName();
            if (name == null) continue;
            if (EXCLUDED_FILES.contains(name)) continue;

            if (child.isDirectory()) {
                if (EXCLUDED_DIRS.contains(name)) continue;
                collectFiles(root, child, out, depth + 1);
            } else if (child.isFile()) {
                String relative = relativePath(root, child);
                if (relative == null) continue;
                long size = child.length();
                boolean binary = isBinary(relative);
                FileEntry entry = new FileEntry(child, relative, size, binary);
                if (!binary && size <= MAX_FILE_SIZE) {
                    entry.lineCount = countLinesQuietly(child);
                }
                out.add(entry);
            }
        }
    }

    // ============================================================
    // تحديد الملفات المهمة
    // ============================================================

    private static Set<String> determineImportantFiles(File projectDir,
                                                       String currentFile,
                                                       String userMessage,
                                                       List<FileEntry> allFiles) {
        Set<String> important = new HashSet<>();

        // 1) الملف المفتوح حالياً
        if (currentFile != null && !currentFile.isEmpty()) {
            String normalized = currentFile.replace('\\', '/');
            while (normalized.startsWith("./")) normalized = normalized.substring(2);
            if (normalized.startsWith("/")) normalized = normalized.substring(1);
            if (!normalized.isEmpty()) important.add(normalized);
        }

        // 2) lib/main.dart إن وُجد
        for (FileEntry entry : allFiles) {
            if ("lib/main.dart".equals(entry.relativePath)) {
                important.add(entry.relativePath);
                break;
            }
        }

        // 3) الملفات المذكورة في رسالة المستخدم
        if (userMessage != null && !userMessage.isEmpty()) {
            for (FileEntry entry : allFiles) {
                if (entry.isBinary) continue;
                String fileName = lastSegment(entry.relativePath);
                if (fileName == null || fileName.isEmpty()) continue;
                // نتحقق من ذكر الاسم الكامل مع الامتداد في الرسالة
                if (containsWord(userMessage, fileName)) {
                    important.add(entry.relativePath);
                    continue;
                }
                // أو ذكر المسار النسبي
                if (containsWord(userMessage, entry.relativePath)) {
                    important.add(entry.relativePath);
                }
            }
        }

        // 4) الملفات الصغيرة (≤ 5KB) — تُضاف كاملة
        for (FileEntry entry : allFiles) {
            if (entry.isBinary) continue;
            if (entry.size <= SMALL_FILE_THRESHOLD && entry.size > 0) {
                important.add(entry.relativePath);
            }
        }

        return important;
    }

    // ============================================================
    // تنسيق محتوى ملف
    // ============================================================

    private static String formatFileBlock(FileEntry entry, String content) {
        StringBuilder sb = new StringBuilder(content.length() + 256);
        sb.append("--- ").append(entry.relativePath)
          .append("  (").append(formatSize(entry.size))
          .append(", ").append(entry.lineCount > 0 ? entry.lineCount : "?").append(" سطر) ---\n");

        if (entry.size > FULL_CONTENT_THRESHOLD) {
            // ملف كبير: رأس + ذيل
            String[] lines = content.split("\n", -1);
            int total = lines.length;
            int head = Math.min(LARGE_FILE_HEAD_LINES, total);
            int tail = Math.min(LARGE_FILE_TAIL_LINES, Math.max(0, total - head));

            for (int i = 0; i < head; i++) {
                sb.append(lines[i]).append("\n");
            }
            if (head + tail < total) {
                sb.append("... [").append(total - head - tail)
                  .append(" سطر محذوف — حجم الملف كبير] ...\n");
            }
            for (int i = total - tail; i < total; i++) {
                if (i < 0) continue;
                sb.append(lines[i]).append("\n");
            }
        } else {
            // ملف صغير/متوسط: كل المحتوى
            sb.append(content);
            if (!content.endsWith("\n")) sb.append("\n");
        }

        sb.append("--- نهاية ").append(entry.relativePath).append(" ---\n\n");
        return sb.toString();
    }

    // ============================================================
    // أدوات
    // ============================================================

    private static String relativePath(File root, File file) {
        try {
            String base = root.getCanonicalPath();
            String path = file.getCanonicalPath();
            if (path.equals(base)) return "";
            if (path.startsWith(base + File.separator)) {
                return path.substring(base.length() + 1).replace(File.separatorChar, '/');
            }
        } catch (IOException ignored) {}
        return file.getName();
    }

    private static String lastSegment(String path) {
        if (path == null) return null;
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private static boolean containsWord(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return false;
        int idx = haystack.indexOf(needle);
        while (idx >= 0) {
            boolean leftOk = idx == 0 || !isWordChar(haystack.charAt(idx - 1));
            int end = idx + needle.length();
            boolean rightOk = end >= haystack.length() || !isWordChar(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            idx = haystack.indexOf(needle, idx + 1);
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/';
    }

    private static boolean isBinary(String relativePath) {
        if (relativePath == null) return false;
        int dot = relativePath.lastIndexOf('.');
        if (dot < 0 || dot == relativePath.length() - 1) return false;
        String ext = relativePath.substring(dot + 1).toLowerCase(Locale.US);
        return BINARY_EXTENSIONS.contains(ext);
    }

    private static int countLinesQuietly(File file) {
        try {
            String content = readFileQuietly(file);
            if (content == null) return -1;
            if (content.isEmpty()) return 0;
            int count = 1;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '\n') count++;
            }
            return count;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String readFileQuietly(File file) {
        try {
            if (file == null || !file.isFile() || file.length() > MAX_FILE_SIZE) return null;
            StringBuilder sb = new StringBuilder((int) Math.min(file.length(), 65536));
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(file), "UTF-8"));
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

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    // ============================================================
    // للاستخدام من AiChatActivity
    // ============================================================

    /** نسخة مختصرة — تُبنى بدون userMessage أو attached content. */
    public static Context buildDefault(File projectDir, String currentFile) {
        return build(projectDir, currentFile, null, null);
    }
}