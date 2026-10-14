package com.flutteride.codweva;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * TemplateInstaller v4.0 — استبدال ذكي بدون اكتشاف مسبق
 *
 * الفكرة:
 *   - لا نحتاج معرفة اسم الحزمة/التطبيق/المشروع الأصلي
 *   - نبحث عن الأنماط المعروفة في الملفات ونستبدل ما فيها
 *   - نفس أسلوب MT Manager
 */
public final class TemplateInstaller {

    private static final String TAG = "TemplateInstaller";
    private static final String SHOWCASE_DIR = "assets/showcase";
    private static final String ICON_NAME = "icon.png";
    private static final String PREVIEW_PREFIX = "preview_";
    private static final int MAX_PREVIEWS = 10;
    private static final int MAX_SCAN_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>();
    static {
        TEXT_EXTENSIONS.add("dart");
        TEXT_EXTENSIONS.add("kt");
        TEXT_EXTENSIONS.add("kts");
        TEXT_EXTENSIONS.add("java");
        TEXT_EXTENSIONS.add("xml");
        TEXT_EXTENSIONS.add("gradle");
        TEXT_EXTENSIONS.add("yaml");
        TEXT_EXTENSIONS.add("yml");
        TEXT_EXTENSIONS.add("json");
        TEXT_EXTENSIONS.add("properties");
        TEXT_EXTENSIONS.add("md");
        TEXT_EXTENSIONS.add("txt");
        TEXT_EXTENSIONS.add("html");
        TEXT_EXTENSIONS.add("css");
        TEXT_EXTENSIONS.add("js");
        TEXT_EXTENSIONS.add("pro");
        TEXT_EXTENSIONS.add("plist");
        TEXT_EXTENSIONS.add("pbxproj");
        TEXT_EXTENSIONS.add("gitignore");
        TEXT_EXTENSIONS.add("metadata");
        TEXT_EXTENSIONS.add("lock");
    }

    private TemplateInstaller() {}

    // ============================================================
    // التحقق والتنظيف
    // ============================================================

    public static String validateAppName(String appName) {
        if (appName == null) return "اسم التطبيق فارغ";
        String trimmed = appName.trim();
        if (trimmed.isEmpty()) return "اسم التطبيق فارغ";
        if (trimmed.length() > 60) return "اسم التطبيق طويل جدًا (الحد الأقصى 60 حرفًا)";

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return "اسم التطبيق يحتوي على محارف غير مرئية";
            }
        }

        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains(":")
                || trimmed.contains("*") || trimmed.contains("?") || trimmed.contains("\"")
                || trimmed.contains("<") || trimmed.contains(">") || trimmed.contains("|")) {
            return "اسم التطبيق يحتوي على محارف غير مسموح بها";
        }

        if (trimmed.replaceAll("[.\\s]", "").isEmpty()) {
            return "اسم التطبيق لا يمكن أن يكون نقاطًا أو مسافات فقط";
        }

        return null;
    }

    private static String sanitizeAppName(String appName) {
        if (appName == null) return "App";
        String result = appName.trim();

        StringBuilder sb = new StringBuilder(result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            sb.append((c < 0x20 || c == 0x7F) ? ' ' : c);
        }
        result = sb.toString().replaceAll("\\s+", " ").trim();

        if (result.length() > 60) result = result.substring(0, 60).trim();
        if (result.isEmpty()) result = "App";

        return result;
    }

    public static String sanitizeAppNameForDisplay(String appName) {
        return sanitizeAppName(appName);
    }

    // ============================================================
    // التثبيت
    // ============================================================

    public static boolean install(Context context, TemplateInfo template,
                                   File targetDir, String appName,
                                   String packageName, String apiLevel) {
        if (template == null || targetDir == null || packageName == null) return false;
        if (!isValidPackageName(packageName)) {
            Log.w(TAG, "اسم حزمة غير صالح: " + packageName);
            return false;
        }

        String nameError = validateAppName(appName);
        if (nameError != null) {
            Log.w(TAG, "اسم التطبيق غير صالح: " + nameError);
            return false;
        }

        try {
            if (!targetDir.exists() && !targetDir.mkdirs()) return false;

            // 1) فك الـ ZIP
            InputStream zipStream;
            if (template.isBuiltIn) {
                zipStream = context.getAssets().open(template.assetsPath);
            } else {
                zipStream = new FileInputStream(template.zipFile);
            }
            extractZip(zipStream, targetDir);
            zipStream.close();

            // 2) استبدال placeholders القديمة ({{APP_NAME}} إلخ)
            replacePlaceholders(targetDir, appName, packageName, apiLevel);

            // 3) ✅ الاستبدال الذكي الجديد — بدون اكتشاف
            String newProjectName = packageNameToProjectName(packageName);
            String safeAppName = sanitizeAppName(appName);

            int updatedFiles = smartRename(targetDir, packageName, safeAppName, newProjectName);
            Log.i(TAG, "تم تعديل " + updatedFiles + " ملف");

            // 4) نقل مجلدات الحزمة (Kotlin/Java)
            relocatePackageFoldersSmart(targetDir, packageName);

            // 5) حذف showcase
            removeShowcaseAssets(targetDir);

            return true;

        } catch (Exception e) {
            Log.e(TAG, "فشل التثبيت: " + e.getMessage(), e);
            return false;
        }
    }

    // ============================================================
    // ✅ الاستبدال الذكي — بدون معرفة الأسماء الأصلية
    // ============================================================

    /**
     * يستبدل:
     *   - اسم الحزمة: أينما ظهر في build.gradle / Manifest / MainActivity
     *   - اسم التطبيق: في AndroidManifest.xml / strings.xml / main.dart
     *   - اسم المشروع: في pubspec.yaml / settings.gradle
     *
     * لا يحتاج معرفة القيم الأصلية — يلتقطها بـ Regex.
     */
    private static int smartRename(File projectDir, String newPackage,
                                    String newAppName, String newProjectName) {
        List<File> textFiles = new ArrayList<>();
        collectTextFiles(projectDir, textFiles);

        int updatedCount = 0;

        for (File file : textFiles) {
            try {
                String content = readTextFile(file);
                if (content == null || content.isEmpty()) continue;

                String updated = content;

                // ══════════════════════════════════════════════════
                // 1. اسم الحزمة — أنماط متعددة
                // ══════════════════════════════════════════════════

                // build.gradle: applicationId "..." أو applicationId '...'
                updated = replaceRegexValue(updated,
                        "(applicationId\\s+)([\"'])([^\"']+)([\"'])",
                        2, 3, newPackage);

                // build.gradle: namespace "..." أو namespace '...'
                updated = replaceRegexValue(updated,
                        "(namespace\\s+)([\"'])([^\"']+)([\"'])",
                        2, 3, newPackage);

                // build.gradle.kts: applicationId = "..."
                updated = replaceRegexValue(updated,
                        "(applicationId\\s*=\\s*)([\"'])([^\"']+)([\"'])",
                        2, 3, newPackage);

                // build.gradle.kts: namespace = "..."
                updated = replaceRegexValue(updated,
                        "(namespace\\s*=\\s*)([\"'])([^\"']+)([\"'])",
                        2, 3, newPackage);

                // AndroidManifest.xml: package="..."
                updated = replaceRegexValue(updated,
                        "(<manifest[^>]*?package\\s*=\\s*)([\"'])([^\"']+)([\"'])",
                        2, 3, newPackage);

                // MainActivity.kt / .java: package xxx.yyy.zzz
                updated = replacePackageDeclaration(updated, newPackage);

                // android:name=".MainActivity" — ليس بحاجة للتغيير

                // ══════════════════════════════════════════════════
                // 2. اسم التطبيق
                // ══════════════════════════════════════════════════

                // AndroidManifest.xml: android:label="..."
                updated = replaceRegexValue(updated,
                        "(android:label\\s*=\\s*)([\"'])([^\"']*)([\"'])",
                        2, 3, newAppName);

                // strings.xml: <string name="app_name">...</string>
                updated = replaceStringResource(updated, "app_name", newAppName);

                // pubspec.yaml: description — لا نلمسها
                // main.dart: title: '...'
                updated = replaceDartTitle(updated, newAppName);

                // ══════════════════════════════════════════════════
                // 3. اسم المشروع (snake_case)
                // ══════════════════════════════════════════════════

                // pubspec.yaml: name: ...
                updated = replacePubspecName(updated, newProjectName);

                // settings.gradle: rootProject.name = "..."
                updated = replaceSettingsGradleName(updated, newProjectName);

                if (!updated.equals(content)) {
                    writeTextFile(file, updated);
                    updatedCount++;
                }
            } catch (Exception e) {
                Log.w(TAG, "فشل تعديل " + file.getName() + ": " + e.getMessage());
            }
        }

        return updatedCount;
    }

    /**
     * ✅ استبدال قيمة بين علامتي اقتباس
     *
     * Pattern: prefix + quote + value + quote
     * Group 1: prefix
     * Group 2: opening quote
     * Group 3: value ← يُستبدَل
     * Group 4: closing quote (مفقود في المعامل — لكن نستخدم group 2)
     */
    private static String replaceRegexValue(String content, String pattern,
                                             int prefixGroup, int valueGroup,
                                             String newValue) {
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String prefix = m.group(1);
                String quote = m.group(2);
                String value = m.group(3);
                String replacement = escapeForContext(newValue, quote);
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(prefix + quote + replacement + quote));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * ✅ استبدال package declaration في Kotlin/Java
     * package com.example.app → package com.new.pkg
     */
    private static String replacePackageDeclaration(String content, String newPackage) {
        try {
            Pattern p = Pattern.compile("^(\\s*package\\s+)([a-zA-Z][a-zA-Z0-9_.]+)(\\s*;?\\s*)$",
                    Pattern.MULTILINE);
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(m.group(1) + newPackage + m.group(3)));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * ✅ استبدال <string name="app_name">...</string>
     */
    private static String replaceStringResource(String content, String name, String newValue) {
        try {
            Pattern p = Pattern.compile(
                    "(<string\\s+name\\s*=\\s*[\"']" + Pattern.quote(name) + "[\"']\\s*>)([^<]*)(</string>)");
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(m.group(1) + escapeXml(newValue) + m.group(3)));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * ✅ استبدال title: '...' في Dart (main.dart)
     */
    private static String replaceDartTitle(String content, String newValue) {
        try {
            // title: 'xxx' أو title: "xxx"
            Pattern p = Pattern.compile(
                    "(title\\s*:\\s*)([\"'])((?:\\\\.|(?!\\2).)*)(\\2)");
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String prefix = m.group(1);
                String quote = m.group(2);
                String replacement = escapeDart(newValue, quote);
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(prefix + quote + replacement + quote));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * ✅ استبدال name: ... في pubspec.yaml
     */
    private static String replacePubspecName(String content, String newValue) {
        try {
            Pattern p = Pattern.compile("^(name\\s*:\\s*)([^\\s#]+)(\\s*)$", Pattern.MULTILINE);
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(m.group(1) + newValue + m.group(3)));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return content;
        }
    }

    /**
     * ✅ استبدال rootProject.name في settings.gradle
     */
    private static String replaceSettingsGradleName(String content, String newValue) {
        try {
            Pattern p = Pattern.compile("(rootProject\\.name\\s*=\\s*)([\"'])([^\"']+)([\"'])");
            Matcher m = p.matcher(content);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb,
                        Matcher.quoteReplacement(m.group(1) + m.group(2) + newValue + m.group(2)));
            }
            m.appendTail(sb);
            return sb.toString();
        } catch (Exception e) {
            return content;
        }
    }

    // ============================================================
    // نقل مجلدات الحزمة (Kotlin/Java)
    // ============================================================

    /**
     * ✅ النقل الذكي — يبحث عن أي مجلد `kotlin/com/` أو `java/com/`
     * وينقله إلى المسار الجديد.
     *
     * لا يحتاج معرفة الاسم الأصلي.
     */
    private static void relocatePackageFoldersSmart(File projectDir, String newPackage) {
        String newPath = newPackage.replace('.', '/');

        String[] roots = {
                "android/app/src/main/kotlin/",
                "android/app/src/main/java/",
                "android/app/src/debug/kotlin/",
                "android/app/src/debug/java/",
                "android/app/src/profile/kotlin/",
                "android/app/src/profile/java/",
        };

        for (String root : roots) {
            File rootDir = new File(projectDir, root);
            if (!rootDir.isDirectory()) continue;

            // ابحث عن أي ملفات .kt أو .java في هذا المجلد
            List<File> sourceFiles = new ArrayList<>();
            collectSourceFiles(rootDir, sourceFiles);

            if (sourceFiles.isEmpty()) continue;

            // لكل ملف: انقله إلى المسار الجديد
            for (File sourceFile : sourceFiles) {
                try {
                    String fileName = sourceFile.getName();
                    File newFile = new File(rootDir, newPath + "/" + fileName);

                    // لو كان في نفس المكان، لا شيء
                    if (sourceFile.getCanonicalPath().equals(newFile.getCanonicalPath())) continue;

                    // أنشئ المجلد الجديد
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    // انقل الملف
                    if (sourceFile.renameTo(newFile)) {
                        Log.i(TAG, "نقل: " + sourceFile.getName() + " → " + newPath);
                    } else {
                        // إذا فشل rename، انسخ ثم احذف
                        copyFile(sourceFile, newFile);
                        sourceFile.delete();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "فشل نقل " + sourceFile.getName() + ": " + e.getMessage());
                }
            }

            // احذف المجلدات الفارغة المتبقية
            deleteEmptyDirs(rootDir, rootDir);
        }
    }

    private static void collectSourceFiles(File dir, List<File> result) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectSourceFiles(child, result);
            } else {
                String name = child.getName().toLowerCase(Locale.US);
                if (name.endsWith(".kt") || name.endsWith(".java")) {
                    result.add(child);
                }
            }
        }
    }

    private static void deleteEmptyDirs(File dir, File stopAt) {
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteEmptyDirs(child, stopAt);
            }
        }
        if (dir.equals(stopAt)) return;
        File[] remaining = dir.listFiles();
        if (remaining != null && remaining.length == 0) {
            dir.delete();
        }
    }

    // ============================================================
    // استبدال placeholders القديمة ({{APP_NAME}} إلخ)
    // ============================================================

    private static void replacePlaceholders(File projectDir, String appName,
                                             String packageName, String apiLevel) {
        String safeAppName = appName == null ? "App" : appName;
        String safePackage = packageName == null ? "com.example.app" : packageName;
        String safeApi = apiLevel == null ? "33" : apiLevel;
        String projectNameSnake = packageNameToProjectName(packageName);

        List<File> targets = new ArrayList<>();
        addIfExists(targets, new File(projectDir, "pubspec.yaml"));
        addIfExists(targets, new File(projectDir, "lib/main.dart"));
        addIfExists(targets, new File(projectDir, "android/app/build.gradle"));
        addIfExists(targets, new File(projectDir, "android/app/build.gradle.kts"));
        addIfExists(targets, new File(projectDir, "android/build.gradle"));
        addIfExists(targets, new File(projectDir, "android/settings.gradle"));
        addIfExists(targets, new File(projectDir, "android/app/src/main/AndroidManifest.xml"));

        for (File file : targets) {
            try {
                String content = readTextFile(file);
                if (content == null) continue;

                String updated = content
                        .replace("{{APP_NAME}}", safeAppName)
                        .replace("{{PACKAGE_NAME}}", safePackage)
                        .replace("{{API_LEVEL}}", safeApi)
                        .replace("{{PROJECT_NAME}}", projectNameSnake);

                if (!updated.equals(content)) writeTextFile(file, updated);
            } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // أدوات مساعدة
    // ============================================================

    private static String packageNameToProjectName(String packageName) {
        if (packageName == null || packageName.isEmpty()) return "flutter_app";

        String[] parts = packageName.split("\\.");
        String last = parts[parts.length - 1];

        last = last.toLowerCase(Locale.US).replaceAll("[^a-z0-9_]", "_");

        if (last.isEmpty() || Character.isDigit(last.charAt(0))) {
            last = "app_" + last;
        }
        if (last.length() > 40) last = last.substring(0, 40);

        if (isDartReserved(last)) last = last + "_app";

        return last;
    }

    private static boolean isDartReserved(String name) {
        switch (name) {
            case "abstract": case "as": case "assert": case "async": case "await":
            case "break": case "case": case "catch": case "class": case "const":
            case "continue": case "default": case "do": case "else": case "enum":
            case "export": case "extends": case "false": case "final": case "finally":
            case "for": case "if": case "import": case "in": case "is": case "library":
            case "new": case "null": case "on": case "return": case "set":
            case "static": case "super": case "switch": case "this": case "throw":
            case "true": case "try": case "var": case "void": case "while":
            case "with": case "yield": case "test": case "main":
                return true;
            default:
                return false;
        }
    }

    private static boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        String[] parts = packageName.split("\\.");
        if (parts.length < 2) return false;
        for (String part : parts) {
            if (part.isEmpty()) return false;
            if (!part.matches("[a-z][a-z0-9_]*")) return false;
        }
        return true;
    }

    private static String escapeForContext(String value, String quote) {
        if (value == null) return "";
        if ("\"".equals(quote)) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapeDart(String value, String quote) {
        if (value == null) return "";
        if ("\"".equals(quote)) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String readTextFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;
        if (file.length() > MAX_SCAN_FILE_SIZE) return null;
        if (looksBinary(file)) return null;

        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) sb.append(buf, 0, n);
            reader.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private static boolean looksBinary(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buf = new byte[512];
            int n = in.read(buf);
            if (n <= 0) return false;
            for (int i = 0; i < n; i++) if (buf[i] == 0) return true;
            return false;
        } catch (Exception e) { return true; }
        finally { if (in != null) try { in.close(); } catch (IOException ignored) {} }
    }

    private static void writeTextFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content == null ? "" : content);
            writer.flush();
        } finally { writer.close(); }
    }

    private static void collectTextFiles(File root, List<File> result) {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                String name = child.getName().toLowerCase(Locale.US);
                if (name.equals(".git") || name.equals("build")
                        || name.equals(".dart_tool") || name.equals(".gradle")
                        || name.equals("node_modules")) {
                    continue;
                }
                collectTextFiles(child, result);
            } else if (isTextFile(child)) {
                result.add(child);
            }
        }
    }

    private static boolean isTextFile(File file) {
        String name = file.getName().toLowerCase(Locale.US);
        if (name.equals(".gitignore") || name.equals(".metadata")
                || name.equals("pubspec.lock") || name.equals("gradlew")
                || name.equals("gradlew.bat")) {
            return true;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return false;
        String ext = name.substring(dot + 1);
        return TEXT_EXTENSIONS.contains(ext);
    }

    private static void addIfExists(List<File> list, File file) {
        if (file != null && file.exists() && file.isFile()) list.add(file);
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (IOException ignored) {}
            try { out.close(); } catch (IOException ignored) {}
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        file.delete();
    }

    // ============================================================
    // استخراج ZIP
    // ============================================================

    private static void extractZip(InputStream input, File destination) throws IOException {
        ZipInputStream zip = new ZipInputStream(input);
        byte[] buffer = new byte[8192];
        ZipEntry entry;

        try {
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                if (entryName.isEmpty()) { zip.closeEntry(); continue; }

                if (entryName.startsWith("__MACOSX/") || entryName.equals(".DS_Store")) {
                    zip.closeEntry();
                    continue;
                }
                if (entryName.startsWith(SHOWCASE_DIR + "/") || entryName.equals(SHOWCASE_DIR)) {
                    zip.closeEntry();
                    continue;
                }

                while (entryName.startsWith("./")) entryName = entryName.substring(2);
                while (entryName.startsWith("/")) entryName = entryName.substring(1);

                if (entryName.isEmpty() || entryName.contains("..")) {
                    zip.closeEntry();
                    continue;
                }

                File output = new File(destination, entryName);

                String destPath = destination.getCanonicalPath();
                String outPath = output.getCanonicalPath();
                if (!outPath.equals(destPath) && !outPath.startsWith(destPath + File.separator)) {
                    zip.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!output.exists()) output.mkdirs();
                } else {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    FileOutputStream fos = new FileOutputStream(output);
                    try {
                        int count;
                        while ((count = zip.read(buffer)) != -1) fos.write(buffer, 0, count);
                    } finally { fos.close(); }
                }
                zip.closeEntry();
            }
        } finally { zip.close(); }
    }

    // ============================================================
    // حذف showcase
    // ============================================================

    private static void removeShowcaseAssets(File projectDir) {
        File showcaseDir = new File(projectDir, SHOWCASE_DIR);
        if (showcaseDir.exists()) deleteRecursive(showcaseDir);

        File assetsDir = new File(projectDir, "assets");
        if (assetsDir.exists() && isEmptyDir(assetsDir)) assetsDir.delete();

        File pubspecFile = new File(projectDir, "pubspec.yaml");
        if (!pubspecFile.exists()) return;
        try {
            String content = readTextFile(pubspecFile);
            if (content == null) return;
            String updated = removeShowcaseFromPubspec(content);
            if (!updated.equals(content)) writeTextFile(pubspecFile, updated);
        } catch (Exception ignored) {}
    }

    private static boolean isEmptyDir(File dir) {
        File[] children = dir.listFiles();
        return children == null || children.length == 0;
    }

    private static String removeShowcaseFromPubspec(String content) {
        if (content == null) return "";
        String[] lines = content.split("\n", -1);
        StringBuilder result = new StringBuilder();
        boolean insideAssets = false;
        boolean hasOtherAssets = false;
        int assetsLineStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (!insideAssets && trimmed.equals("assets:")) {
                insideAssets = true;
                assetsLineStart = result.length();
                result.append(line);
                if (i < lines.length - 1) result.append("\n");
                continue;
            }

            if (insideAssets) {
                if (trimmed.startsWith("- ")) {
                    String path = trimmed.substring(2).trim();
                    if (!path.startsWith("assets/showcase")) {
                        hasOtherAssets = true;
                        result.append(line);
                        if (i < lines.length - 1) result.append("\n");
                    }
                } else {
                    insideAssets = false;
                    if (!hasOtherAssets && assetsLineStart >= 0) result.setLength(assetsLineStart);
                    assetsLineStart = -1;
                    result.append(line);
                    if (i < lines.length - 1) result.append("\n");
                }
            } else {
                result.append(line);
                if (i < lines.length - 1) result.append("\n");
            }
        }

        if (insideAssets && !hasOtherAssets && assetsLineStart >= 0) result.setLength(assetsLineStart);
        return result.toString();
    }

    // ============================================================
    // استخراج showcase (للعرض في المتجر)
    // ============================================================

    public static File extractShowcase(Context context, TemplateInfo template) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "template_showcase/" + template.id);
        if (cacheDir.exists()) deleteRecursive(cacheDir);
        if (!cacheDir.mkdirs()) throw new IOException("تعذر إنشاء مجلد cache");

        InputStream zipStream;
        if (template.isBuiltIn) zipStream = context.getAssets().open(template.assetsPath);
        else zipStream = new FileInputStream(template.zipFile);

        ZipInputStream zip = new ZipInputStream(zipStream);
        byte[] buffer = new byte[8192];
        ZipEntry entry;

        try {
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                while (name.startsWith("./")) name = name.substring(2);

                if (!name.startsWith(SHOWCASE_DIR + "/") && !name.startsWith("showcase/")) {
                    zip.closeEntry(); continue;
                }
                if (entry.isDirectory()) { zip.closeEntry(); continue; }

                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (fileName.isEmpty()) { zip.closeEntry(); continue; }

                File output = new File(cacheDir, fileName);
                FileOutputStream fos = new FileOutputStream(output);
                try {
                    int count;
                    while ((count = zip.read(buffer)) != -1) fos.write(buffer, 0, count);
                } finally { fos.close(); }
                zip.closeEntry();
            }
        } finally { zip.close(); }

        File iconFile = new File(cacheDir, ICON_NAME);
        if (iconFile.exists()) template.iconFile = iconFile;
        else {
            File[] files = cacheDir.listFiles();
            if (files != null) for (File f : files) {
                if (f.getName().startsWith("icon")) { template.iconFile = f; break; }
            }
        }

        template.previewFiles.clear();
        for (int i = 1; i <= MAX_PREVIEWS; i++) {
            File preview = new File(cacheDir, PREVIEW_PREFIX + i + ".png");
            if (preview.exists()) { template.previewFiles.add(preview); continue; }
            File previewJpg = new File(cacheDir, PREVIEW_PREFIX + i + ".jpg");
            if (previewJpg.exists()) template.previewFiles.add(previewJpg);
        }

        if (template.previewFiles.isEmpty()) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                List<File> previews = new ArrayList<>();
                for (File f : files) {
                    String n = f.getName().toLowerCase(Locale.US);
                    if (n.startsWith(PREVIEW_PREFIX)
                            && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
                        previews.add(f);
                    }
                }
                previews.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                template.previewFiles.addAll(previews);
            }
        }

        return cacheDir;
    }
}