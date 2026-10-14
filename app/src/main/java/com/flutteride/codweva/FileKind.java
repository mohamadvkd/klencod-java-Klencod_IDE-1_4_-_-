package com.flutteride.codweva;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * تصنيف أنواع الملفات لتحديد العارض المناسب.
 *
 * الفئات:
 *   - TEXT       : يُعرض في CodeEditorView (مع تلوين)
 *   - IMAGE      : يُعرض في ImageViewer
 *   - VIDEO      : يُعرض في VideoViewer
 *   - AUDIO      : يُعرض في AudioViewer
 *   - PDF        : يُفتح عبر Intent
 *   - APK        : يُفتح عبر Intent (تثبيت)
 *   - ARCHIVE    : يُفتح عبر Intent (ZIP/RAR)
 *   - BINARY     : غير قابل للعرض (ملف غير مدعوم)
 *   - UNKNOWN    : امتداد غير معروف → نحاول عرضه كنص
 */
public enum FileKind {

    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    APK,
    ARCHIVE,
    BINARY,
    UNKNOWN;

    // ============================================================
    // قائمة الملفات النصية (موسّعة)
    // ============================================================
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
            // لغات برمجة
            "dart", "java", "kt", "kts", "py", "pyw", "js", "mjs", "ts", "tsx", "jsx",
            "c", "cpp", "cc", "cxx", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift",
            "m", "mm", "scala", "clj", "cljs", "lua", "pl", "r", "jl", "vb",
            // ويب
            "html", "htm", "xhtml", "css", "scss", "sass", "less", "vue", "svelte",
            // بيانات وتهيئة
            "xml", "json", "yaml", "yml", "toml", "ini", "conf", "cfg", "properties",
            "env", "lock", "gradle", "pro", "plist", "pbxproj", "xcconfig",
            // نصوص
            "md", "markdown", "txt", "rtf", "log", "csv", "tsv",
            // سكربتات
            "sh", "bash", "zsh", "fish", "bat", "cmd", "ps1", "makefile", "mk",
            // أخرى
            "gitignore", "gitattributes", "editorconfig", "dockerignore",
            "sql", "graphql", "proto", "diff", "patch"
    ));

    // أسماء ملفات كاملة بدون امتداد
    private static final Set<String> TEXT_FILENAMES = new HashSet<>(Arrays.asList(
            "makefile", "dockerfile", "gradlew", "gradlew.bat", "procfile",
            "rakefile", "gemfile", "podfile", "vagrantfile"
    ));

    // ============================================================
    // الامتدادات المدعومة لكل نوع
    // ============================================================
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "ico", "tiff", "tif"
    ));

    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4v", "mpeg", "mpg", "wmv"
    ));

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "3gp", "amr", "wma"
    ));

    private static final Set<String> PDF_EXTENSIONS = new HashSet<>(Arrays.asList("pdf"));

    private static final Set<String> APK_EXTENSIONS = new HashSet<>(Arrays.asList("apk", "aab", "xapk"));

    private static final Set<String> ARCHIVE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz"
    ));

    /**
     * يصنّف ملف حسب اسمه.
     */
    public static FileKind detect(String fileNameOrPath) {
        if (fileNameOrPath == null) return UNKNOWN;
        String name = fileNameOrPath;
        // نأخذ اسم الملف فقط (بعد آخر /)
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        int bslash = name.lastIndexOf('\\');
        if (bslash >= 0) name = name.substring(bslash + 1);

        String lower = name.toLowerCase(Locale.US);

        // 1) اسم ملف كامل بدون امتداد
        if (TEXT_FILENAMES.contains(lower)) return TEXT;

        // 2) استخراج الامتداد
        String ext = extensionOf(lower);
        if (ext.isEmpty()) return UNKNOWN; // بدون امتداد → نحاول كنص

        // 3) البحث في القوائم
        if (TEXT_EXTENSIONS.contains(ext)) return TEXT;
        if (IMAGE_EXTENSIONS.contains(ext)) return IMAGE;
        if (VIDEO_EXTENSIONS.contains(ext)) return VIDEO;
        if (AUDIO_EXTENSIONS.contains(ext)) return AUDIO;
        if (PDF_EXTENSIONS.contains(ext)) return PDF;
        if (APK_EXTENSIONS.contains(ext)) return APK;
        if (ARCHIVE_EXTENSIONS.contains(ext)) return ARCHIVE;

        // 4) امتداد غير معروف → UNKNOWN (سيُعرض كنص بحذر)
        return UNKNOWN;
    }

    /**
     * هل الملف قابل للعرض في المحرر النصي؟
     */
    public static boolean isTextLike(String fileNameOrPath) {
        FileKind kind = detect(fileNameOrPath);
        return kind == TEXT || kind == UNKNOWN;
    }

    /**
     * يُرجع الامتداد بدون نقطة (أو "").
     */
    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.US);
        // نُقصّر الامتدادات الطويلة جداً (حماية)
        if (ext.length() > 12) return ext.substring(0, 12);
        return ext;
    }

    /**
     * وصف عربي للنوع (للعرض في الواجهة).
     */
    public String getArabicName() {
        switch (this) {
            case TEXT: return "ملف نصي";
            case IMAGE: return "صورة";
            case VIDEO: return "فيديو";
            case AUDIO: return "ملف صوتي";
            case PDF: return "مستند PDF";
            case APK: return "حزمة تطبيق";
            case ARCHIVE: return "أرشيف";
            case BINARY: return "ملف ثنائي";
            default: return "ملف";
        }
    }
}