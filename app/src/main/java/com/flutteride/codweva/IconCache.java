package com.flutteride.codweva;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * تخزين دائم (على القرص) لأيقونات APK والقوالب.
 * المفتاح = مسار الملف + آخر تعديل -> أي تغيير = أيقونة جديدة.
 *
 * ✅ v2.0: حفظ ذري + فحص سلامة الملفات
 */
public final class IconCache {

    private static final String APK_ICONS_DIR = "apk_icons";
    private static final String TEMPLATE_ICONS_DIR = "template_icons";

    /** الحد الأدنى لحجم ملف صالح (100 بايت) */
    private static final long MIN_VALID_SIZE = 100;

    private IconCache() {}

    // ============================================================
    // أيقونات APK
    // ============================================================

    public static Bitmap loadApkIcon(Context context, File apkFile) {
        if (context == null || apkFile == null || !apkFile.isFile()) return null;
        File cacheFile = apkIconCacheFile(context, apkFile);
        return loadBitmap(cacheFile);
    }

    public static void saveApkIcon(Context context, File apkFile, Bitmap bitmap) {
        if (context == null || apkFile == null || bitmap == null || bitmap.isRecycled()) return;
        File cacheFile = apkIconCacheFile(context, apkFile);
        saveBitmap(cacheFile, bitmap);
    }

    public static void removeApkIcon(Context context, File apkFile) {
        if (context == null || apkFile == null) return;
        File cacheFile = apkIconCacheFile(context, apkFile);
        if (cacheFile.exists()) cacheFile.delete();
        File dir = new File(context.getCacheDir(), APK_ICONS_DIR);
        if (dir.isDirectory()) {
            String prefix = sha256(apkFile.getAbsolutePath());
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().startsWith(prefix + "_")) child.delete();
                }
            }
        }
    }

    private static File apkIconCacheFile(Context context, File apkFile) {
        File dir = new File(context.getCacheDir(), APK_ICONS_DIR);
        if (!dir.exists()) dir.mkdirs();
        String key = sha256(apkFile.getAbsolutePath()) + "_" + apkFile.lastModified();
        return new File(dir, key + ".png");
    }

    // ============================================================
    // أيقونات القوالب
    // ============================================================

    public static Bitmap loadTemplateIcon(Context context, TemplateInfo template) {
        if (context == null || template == null) return null;
        File cacheFile = templateIconCacheFile(context, template);
        return loadBitmap(cacheFile);
    }

    public static void saveTemplateIcon(Context context, TemplateInfo template, Bitmap bitmap) {
        if (context == null || template == null || bitmap == null || bitmap.isRecycled()) return;
        File cacheFile = templateIconCacheFile(context, template);
        saveBitmap(cacheFile, bitmap);
    }

    public static void removeTemplateIcon(Context context, TemplateInfo template) {
        if (context == null || template == null) return;
        File cacheFile = templateIconCacheFile(context, template);
        if (cacheFile.exists()) cacheFile.delete();
    }

    private static File templateIconCacheFile(Context context, TemplateInfo template) {
        File dir = new File(context.getCacheDir(), TEMPLATE_ICONS_DIR);
        if (!dir.exists()) dir.mkdirs();
        String key = sha256(template.id + "_" + template.sizeBytes);
        return new File(dir, key + ".png");
    }

    // ============================================================
    // أدوات مساعدة - محسّنة
    // ============================================================

    /**
     * ✅ تحميل آمن للصورة مع فحص سلامة
     */
    private static Bitmap loadBitmap(File file) {
        if (file == null || !file.isFile() || file.length() == 0) return null;

        // ✅ فحص الحجم الأدنى
        if (file.length() < MIN_VALID_SIZE) {
            file.delete();
            return null;
        }

        try {
            // 1. اقرأ الأبعاد أولاً
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            // 2. تحقق من الأبعاد
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                file.delete();
                return null;
            }

            // 3. فك الصورة الفعلي
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            if (bmp == null || bmp.isRecycled()) {
                file.delete();
                return null;
            }

            // 4. تحقق من تطابق الأبعاد
            if (bmp.getWidth() != opts.outWidth || bmp.getHeight() != opts.outHeight) {
                bmp.recycle();
                file.delete();
                return null;
            }

            return bmp;
        } catch (Exception e) {
            // ملف تالف — احذفه
            if (file.exists()) file.delete();
            return null;
        }
    }

    /**
     * ✅ حفظ ذري للصورة (لا يترك ملفات نصف مكتوبة)
     */
    private static void saveBitmap(File file, Bitmap bitmap) {
        if (file == null || bitmap == null || bitmap.isRecycled()) return;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        // احفظ في ملف مؤقت أولاً
        File tempFile = new File(file.getAbsolutePath() + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } catch (Exception ignored) {
            if (tempFile.exists()) tempFile.delete();
            return;
        } finally {
            if (out != null) try { out.close(); } catch (Exception ignored) {}
        }

        // تحقق من أن الملف المؤقت صالح
        if (!tempFile.exists() || tempFile.length() < MIN_VALID_SIZE) {
            if (tempFile.exists()) tempFile.delete();
            return;
        }

        // ✅ نقل ذري
        try {
            if (file.exists()) file.delete();
            if (!tempFile.renameTo(file)) {
                // فشل rename — انسخ يدويًا
                copyFile(tempFile, file);
                tempFile.delete();
            }
        } catch (Exception ignored) {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    /**
     * نسخة احتياطية للـ rename
     */
    private static void copyFile(File source, File target) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target);
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
    // تنظيف كامل (اختياري)
    // ============================================================

    public static void clearAll(Context context) {
        if (context == null) return;
        deleteRecursive(new File(context.getCacheDir(), APK_ICONS_DIR));
        deleteRecursive(new File(context.getCacheDir(), TEMPLATE_ICONS_DIR));
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        file.delete();
    }
}