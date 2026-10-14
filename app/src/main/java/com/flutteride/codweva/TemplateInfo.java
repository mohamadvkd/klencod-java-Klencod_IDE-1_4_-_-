package com.flutteride.codweva;

import java.io.File;

/**
 * نموذج بيانات القالب.
 * يُقرأ من assets/templates/ أو من داخل ZIP.
 */
public class TemplateInfo {

    /** معرّف القالب (اسم ملف ZIP بدون .zip) */
    public final String id;

    /** الاسم المعروض (بالعربية إن وُجد) */
    public String displayName;

    /** الاسم الإنجليزي (اختياري) */
    public String englishName;

    /** ملف ZIP المصدر */
    public final File zipFile;

    /** المسار داخل assets (مثلاً "templates/quran.zip") — null إذا كان ملفاً خارجياً */
    public final String assetsPath;

    /** حجم القالب بالبايت */
    public long sizeBytes;

    /** هل هو من assets (لا يمكن حذفه) */
    public final boolean isBuiltIn;

    // مسارات مؤقتة للأيقونة والمعاينات (بعد فكّها)
    public File iconFile;
    public java.util.List<File> previewFiles = new java.util.ArrayList<>();

    public TemplateInfo(String id, File zipFile, String assetsPath) {
        this.id = id;
        this.zipFile = zipFile;
        this.assetsPath = assetsPath;
        this.isBuiltIn = assetsPath != null;
        this.displayName = capitalize(id);
        this.englishName = capitalize(id);
        this.sizeBytes = zipFile != null && zipFile.exists() ? zipFile.length() : 0L;
    }

    /** يحوّل "quran" إلى "Quran" */
    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    /** حجم القالب بصيغة مقروءة */
    public String getSizeLabel() {
        if (sizeBytes < 1024) return sizeBytes + " B";
        if (sizeBytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", sizeBytes / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return "TemplateInfo{" + id + ", name=" + displayName + ", size=" + sizeBytes + "}";
    }
}