package com.flutteride.codweva;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * مدير القوالب — v4.0
 *
 * التحسينات:
 *   - ✅ cache أيقونة القالب يعتمد على محتوى ZIP (size + checksum) وليس فقط size
 *   - ✅ cache معاينات القالب بالمفتاح نفسه
 *   - ✅ يعمل مع TemplateInstaller v4.0
 *   - ✅ حماية من cache القديم بعد تحديث القالب
 */
public final class TemplateManager {

    private static final String TAG = "TemplateManager";
    private static final String ASSETS_TEMPLATES_DIR = "templates";
    private static final String ZIP_EXTENSION = ".zip";

    // ============================================================
    // القالب الافتراضي (To-Do)
    // ============================================================

    public static final String TEMPLATE_TODO = "todo";
    public static final String DEFAULT_TEMPLATE = TEMPLATE_TODO;

    public static boolean writeTemplate(String templateId, File projectDir,
                                         String packageName, String appName,
                                         String apiLevel) {
        if (templateId == null) templateId = DEFAULT_TEMPLATE;
        if (projectDir == null || !projectDir.isDirectory()) return false;

        switch (templateId) {
            case TEMPLATE_TODO:
            default:
                return TodoTemplate.write(projectDir, packageName, appName, apiLevel);
        }
    }

    public static String getDefaultTemplateDisplayName(String templateId) {
        if (templateId == null) return getDefaultTemplateDisplayName(DEFAULT_TEMPLATE);
        switch (templateId) {
            case TEMPLATE_TODO:
            default:
                return "مدير المهام";
        }
    }

    public static String getDefaultTemplateDescription(String templateId) {
        if (templateId == null) return getDefaultTemplateDescription(DEFAULT_TEMPLATE);
        switch (templateId) {
            case TEMPLATE_TODO:
            default:
                return "تطبيق لإدارة المهام اليومية مع حفظ محلي.";
        }
    }

    // ============================================================
    // تحميل القوالب
    // ============================================================

    public static List<TemplateInfo> loadTemplates(Context context) {
        List<TemplateInfo> templates = new ArrayList<>();

        try {
            AssetManager assets = context.getAssets();
            String[] files = assets.list(ASSETS_TEMPLATES_DIR);

            if (files == null || files.length == 0) {
                return templates;
            }

            for (String fileName : files) {
                if (fileName == null) continue;
                if (!fileName.toLowerCase(Locale.US).endsWith(ZIP_EXTENSION)) continue;

                String id = fileName.substring(0, fileName.length() - ZIP_EXTENSION.length());
                if (id.isEmpty()) continue;

                String assetPath = ASSETS_TEMPLATES_DIR + "/" + fileName;

                TemplateInfo info = new TemplateInfo(id, null, assetPath);
                info.sizeBytes = getAssetSize(context, assetPath);
                loadTemplateMeta(context, assetPath, info);

                templates.add(info);
            }
        } catch (Exception e) {
            Log.w(TAG, "فشل تحميل القوالب: " + e.getMessage());
        }

        Collections.sort(templates, (a, b) -> a.id.compareToIgnoreCase(b.id));
        return templates;
    }

    public static TemplateInfo findById(Context context, String id) {
        if (id == null) return null;
        List<TemplateInfo> all = loadTemplates(context);
        for (TemplateInfo t : all) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public static boolean hasTemplates(Context context) {
        try {
            AssetManager assets = context.getAssets();
            String[] files = assets.list(ASSETS_TEMPLATES_DIR);
            if (files == null) return false;
            for (String f : files) {
                if (f != null && f.toLowerCase(Locale.US).endsWith(ZIP_EXTENSION)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ============================================================
    // ✅ cache أيقونة القالب (محسّن)
    // ============================================================

    /**
     * ✅ المفتاح الآن يعتمد على:
     *   - template.id (فريد)
     *   - template.sizeBytes (للتفريق بين إصدارات مختلفة)
     *   - checksum (للتفريق بين ZIPs بنفس الحجم)
     *
     * هذا يمنع:
     *   - قراءة cache قديم بعد تحديث قالب
     *   - تعارض بين قوالب مختلفة بنفس الحجم
     */
    private static String templateCacheKey(Context context, TemplateInfo template) {
        // المفتاح الأساسي = id + size
        String base = template.id + "_" + template.sizeBytes;

        // أضف checksum لو أمكن (لتمييز ZIPs بنفس الحجم)
        try {
            String checksum = getAssetChecksum(context, template.assetsPath);
            if (checksum != null && !checksum.isEmpty()) {
                base = base + "_" + checksum;
            }
        } catch (Exception ignored) {}

        return sha256(base);
    }

    /**
     * ✅ cache أيقونة القالب
     */
    public static File prepareShowcase(Context context, TemplateInfo template) {
        if (context == null || template == null) return null;

        // 1. جرّب الأيقونة المخزنة على القرص أولًا
        File cachedIcon = getCachedTemplateIconFile(context, template);
        if (cachedIcon != null && cachedIcon.exists() && cachedIcon.length() > 0) {
            template.iconFile = cachedIcon;
            // ✅ حمّل المعاينات أيضًا
            loadCachedPreviews(context, template);
            return cachedIcon.getParentFile();
        }

        // 2. استخرج من ZIP (المرة الأولى فقط)
        File showcaseDir = null;
        try {
            showcaseDir = TemplateInstaller.extractShowcase(context, template);
        } catch (Exception e) {
            Log.w(TAG, "فشل استخراج العرض: " + e.getMessage());
            return null;
        }

        // 3. احفظ الأيقونة في cache دائم
        if (showcaseDir != null && template.iconFile != null && template.iconFile.exists()) {
            saveIconToPersistentCache(context, template, template.iconFile);
        }

        // 4. احفظ الـ previews أيضًا
        if (showcaseDir != null && !template.previewFiles.isEmpty()) {
            savePreviewsToPersistentCache(context, template);
        }

        return showcaseDir;
    }

    // ============================================================
    // cache الأيقونة
    // ============================================================

    private static File getPersistentIconDir(Context context) {
        File dir = new File(context.getCacheDir(), "template_icons");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File getPersistentPreviewsDir(Context context, TemplateInfo template) {
        String key = templateCacheKey(context, template);
        File dir = new File(context.getCacheDir(), "template_previews/" + key);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File getCachedTemplateIconFile(Context context, TemplateInfo template) {
        File dir = getPersistentIconDir(context);
        String key = templateCacheKey(context, template);
        File f = new File(dir, key + ".png");
        return f.isFile() ? f : null;
    }

    private static void saveIconToPersistentCache(Context context, TemplateInfo template, File sourceIcon) {
        try {
            File destDir = getPersistentIconDir(context);
            String key = templateCacheKey(context, template);
            File dest = new File(destDir, key + ".png");

            // ✅ احفظ فقط لو غير موجود
            if (dest.exists() && dest.length() > 100) return;

            // ✅ احفظ في ملف مؤقت ثم انقل (حماية من الملفات النصفية)
            File tempFile = new File(dest.getAbsolutePath() + ".tmp");
            Bitmap bmp = BitmapFactory.decodeFile(sourceIcon.getAbsolutePath());
            if (bmp == null || bmp.isRecycled()) return;

            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            if (!bmp.isRecycled()) bmp.recycle();

            // ✅ نقل ذري
            if (tempFile.length() < 100) {
                tempFile.delete();
                return;
            }
            if (dest.exists()) dest.delete();
            if (!tempFile.renameTo(dest)) {
                copyFile(tempFile, dest);
                tempFile.delete();
            }
        } catch (Exception e) {
            Log.w(TAG, "فشل حفظ أيقونة القالب: " + e.getMessage());
        }
    }

    private static void loadCachedPreviews(Context context, TemplateInfo template) {
        template.previewFiles.clear();
        File dir = getPersistentPreviewsDir(context, template);
        if (!dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        List<File> previews = new ArrayList<>();
        for (File f : files) {
            String name = f.getName().toLowerCase(Locale.US);
            if (name.startsWith("preview_")
                    && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
                    && f.length() > 100) {
                previews.add(f);
            }
        }
        Collections.sort(previews, (a, b) ->
                a.getName().compareToIgnoreCase(b.getName()));
        template.previewFiles.addAll(previews);
    }

    private static void savePreviewsToPersistentCache(Context context, TemplateInfo template) {
        try {
            File destDir = getPersistentPreviewsDir(context, template);
            for (int i = 0; i < template.previewFiles.size(); i++) {
                File src = template.previewFiles.get(i);
                if (src == null || !src.isFile()) continue;

                File dest = new File(destDir, "preview_" + (i + 1) + ".png");
                if (dest.exists() && dest.length() > 100) continue;

                FileInputStream in = new FileInputStream(src);
                java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                in.close();
                out.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "فشل حفظ معاينات القالب: " + e.getMessage());
        }
    }

    // ============================================================
    // ✅ تنظيف cache القالب
    // ============================================================

    /**
     * حذف كل cache يخص قالب معين
     */
    public static void clearTemplateCache(Context context, TemplateInfo template) {
        if (context == null || template == null) return;
        try {
            // الأيقونة
            File iconDir = getPersistentIconDir(context);
            String iconKey = templateCacheKey(context, template);
            File iconFile = new File(iconDir, iconKey + ".png");
            if (iconFile.exists()) iconFile.delete();

            // المعاينات
            File previewDir = getPersistentPreviewsDir(context, template);
            deleteRecursive(previewDir);

            // showcase المؤقت
            File showcaseDir = new File(context.getCacheDir(),
                    "template_showcase/" + template.id);
            deleteRecursive(showcaseDir);

        } catch (Exception e) {
            Log.w(TAG, "فشل حذف cache القالب: " + e.getMessage());
        }
    }

    /**
     * ✅ حذف كل cache القوالب
     */
    public static void clearAllTemplatesCache(Context context) {
        if (context == null) return;
        try {
            deleteRecursive(new File(context.getCacheDir(), "template_icons"));
            deleteRecursive(new File(context.getCacheDir(), "template_previews"));
            deleteRecursive(new File(context.getCacheDir(), "template_showcase"));
        } catch (Exception e) {
            Log.w(TAG, "فشل حذف cache القوالب: " + e.getMessage());
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

    private static void copyFile(File source, File target) {
        FileInputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new java.io.FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.flush();
        } catch (Exception ignored) {
        } finally {
            if (in != null) try { in.close(); } catch (Exception ignored) {}
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // تثبيت قالب
    // ============================================================

    public static boolean install(Context context, TemplateInfo template,
                                   File targetDir, String appName,
                                   String packageName, String apiLevel,
                                   boolean isPublicRepo) {
        if (context == null || template == null || targetDir == null) return false;
        return TemplateInstaller.install(
                context, template, targetDir,
                appName, packageName, apiLevel);
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================

    /**
     * ✅ حجم الـ asset
     */
    private static long getAssetSize(Context context, String assetPath) {
        InputStream is = null;
        try {
            is = context.getAssets().open(assetPath);
            long total = 0;
            byte[] buffer = new byte[8192];
            int count;
            while ((count = is.read(buffer)) != -1) {
                total += count;
                if (total > 50L * 1024L * 1024L) break;
            }
            return total;
        } catch (Exception e) {
            return 0L;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * ✅ checksum مختصر (يعتمد على أول 64KB من الـ asset)
     *
     * لا نقرأ كل الملف — أسرع.
     */
    private static String getAssetChecksum(Context context, String assetPath) {
        InputStream is = null;
        try {
            is = context.getAssets().open(assetPath);
            byte[] buffer = new byte[64 * 1024]; // أول 64KB
            int read = 0;
            int total = 0;
            while (total < buffer.length
                    && (read = is.read(buffer, total, buffer.length - total)) > 0) {
                total += read;
            }
            if (total <= 0) return null;

            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(buffer, 0, total);
            byte[] hash = digest.digest();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                sb.append(String.format(Locale.US, "%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }

    private static void loadTemplateMeta(Context context, String assetPath, TemplateInfo info) {
        InputStream zipStream = null;
        java.util.zip.ZipInputStream zip = null;
        try {
            zipStream = context.getAssets().open(assetPath);
            zip = new java.util.zip.ZipInputStream(zipStream);

            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                while (name.startsWith("./")) name = name.substring(2);

                if (name.equals("template.json") || name.endsWith("/template.json")) {
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        bos.write(buffer, 0, count);
                        if (bos.size() > 20 * 1024) break;
                    }
                    String json = new String(bos.toByteArray(), "UTF-8");
                    parseTemplateJson(json, info);
                    break;
                }
                zip.closeEntry();
            }
        } catch (Exception ignored) {
        } finally {
            if (zip != null) try { zip.close(); } catch (IOException ignored) {}
            if (zipStream != null) try { zipStream.close(); } catch (IOException ignored) {}
        }
    }

    private static void parseTemplateJson(String json, TemplateInfo info) {
        if (json == null || json.isEmpty()) return;

        String name = extractJsonString(json, "name");
        String nameEn = extractJsonString(json, "name_en");

        if (name != null && !name.isEmpty()) {
            info.displayName = name;
        }
        if (nameEn != null && !nameEn.isEmpty()) {
            info.englishName = nameEn;
        }
    }

    private static String extractJsonString(String json, String key) {
        if (json == null || key == null) return null;

        int keyIdx = json.indexOf("\"" + key + "\"");
        if (keyIdx < 0) return null;

        int colon = json.indexOf(':', keyIdx + key.length() + 2);
        if (colon < 0) return null;

        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;

        int end = start + 1;
        StringBuilder sb = new StringBuilder();
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\' && end + 1 < json.length()) {
                char next = json.charAt(end + 1);
                if (next == '"' || next == '\\' || next == 'n') {
                    if (next == 'n') sb.append('\n');
                    else sb.append(next);
                    end += 2;
                    continue;
                }
            }
            if (c == '"') break;
            sb.append(c);
            end++;
        }
        return sb.toString();
    }

    private static String sha256(String value) {
        if (value == null) return "null";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    // ============================================================
    // معلومات مساعدة للواجهة
    // ============================================================

    public static String getDisplayName(TemplateInfo template) {
        if (template == null) return "";
        return template.displayName != null ? template.displayName : template.id;
    }

    public static String getSecondaryName(TemplateInfo template) {
        if (template == null) return "";
        if (template.englishName != null && !template.englishName.isEmpty()) {
            return template.englishName;
        }
        return template.id;
    }
}