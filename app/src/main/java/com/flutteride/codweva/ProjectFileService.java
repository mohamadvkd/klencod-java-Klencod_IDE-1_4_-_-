package com.flutteride.codweva;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** وصول محدود إلى ملفات المشروع فقط. لا يتعامل مع FlutterBuildApi أو إعدادات البناء. */
public final class ProjectFileService {
    private static final int MAX_READ_BYTES = 250 * 1024;
    private final File projectRoot;

    public ProjectFileService(File projectRoot) {
        if (projectRoot == null) throw new IllegalArgumentException("مجلد المشروع غير موجود");
        try {
            this.projectRoot = projectRoot.getCanonicalFile();
        } catch (Exception e) {
            throw new IllegalArgumentException("تعذر تحديد مجلد المشروع", e);
        }
    }

    public File getProjectRoot() { return projectRoot; }

    public String readFile(String relativePath) throws Exception {
        File file = safeFile(relativePath);
        if (!file.isFile()) throw new Exception("الملف غير موجود: " + relativePath);
        if (file.length() > MAX_READ_BYTES) throw new Exception("الملف كبير جدًا لإرساله: " + relativePath);
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        reader.close();
        return builder.toString();
    }

    public List<String> listFiles() throws Exception {
        ArrayList<String> result = new ArrayList<>();
        collect(projectRoot, result);
        Collections.sort(result);
        return result;
    }

    public boolean exists(String relativePath) throws Exception {
        return safeFile(relativePath).exists();
    }

    public void deleteFile(String relativePath) throws Exception {
        File file = safeFile(relativePath);
        if (!file.isFile()) throw new Exception("الملف غير موجود: " + relativePath);
        backupExisting(file, relativePath);
        if (!file.delete()) throw new Exception("تعذر حذف الملف: " + relativePath);
        cleanupEmptyParentDirs(file.getParentFile());
    }

    public void writeFile(String relativePath, String content) throws Exception {
        File file = safeFile(relativePath);
        if (content == null) content = "";
        if (content.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) {
            throw new Exception("التغيير كبير جدًا");
        }
        if (file.exists()) backupExisting(file, relativePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("تعذر إنشاء مجلد الملف");
        }
        FileOutputStream output = new FileOutputStream(file);
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.flush();
        output.close();
    }

    // ============================================================
    // نقل / إعادة تسمية ملف
    // ============================================================

    public void moveFile(String fromRelative, String toRelative) throws Exception {
        File from = safeFile(fromRelative);
        File to = safeFile(toRelative);

        if (!from.isFile()) {
            throw new Exception("الملف المصدر غير موجود: " + fromRelative);
        }
        if (to.exists()) {
            throw new Exception("الملف الوجهة موجود بالفعل: " + toRelative);
        }

        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("تعذر إنشاء مجلد الوجهة");
        }

        if (from.renameTo(to)) {
            cleanupEmptyParentDirs(from.getParentFile());
            return;
        }

        copyFile(from, to);
        if (!from.delete()) {
            try { to.delete(); } catch (Exception ignored) {}
            throw new Exception("تعذر حذف الملف الأصلي بعد النقل");
        }
        cleanupEmptyParentDirs(from.getParentFile());
    }

    // ============================================================
    // أدوات داخلية
    // ============================================================

    private void backupExisting(File file, String relativePath) throws Exception {
        File backup = new File(projectRoot, ".klencod_backup");
        if (!backup.exists() && !backup.mkdirs()) throw new Exception("تعذر إنشاء النسخة الاحتياطية");
        File backupFile = new File(backup, relativePath.replace('/', '_').replace('\\', '_'));
        copyFile(file, backupFile);
    }

    private void copyFile(File source, File target) throws Exception {
        FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        } finally {
            try { input.close(); } catch (Exception ignored) {}
            try { output.close(); } catch (Exception ignored) {}
        }
    }

    private void cleanupEmptyParentDirs(File dir) {
        if (dir == null) return;
        try {
            String rootPath = projectRoot.getCanonicalPath();
            File current = dir.getCanonicalFile();
            while (current != null) {
                String currentPath = current.getCanonicalPath();
                if (currentPath.equals(rootPath)) return;
                if (!currentPath.startsWith(rootPath + File.separator)) return;
                File[] children = current.listFiles();
                if (children == null || children.length > 0) return;
                if (!current.delete()) return;
                current = current.getParentFile();
            }
        } catch (Exception ignored) {}
    }

    public File safeFile(String relativePath) throws Exception {
        if (relativePath == null || relativePath.trim().isEmpty()) throw new Exception("مسار فارغ");
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..")) {
            throw new SecurityException("مسار خارج المشروع مرفوض");
        }
        File file = new File(projectRoot, normalized).getCanonicalFile();
        String root = projectRoot.getPath();
        if (!file.getPath().equals(root) && !file.getPath().startsWith(root + File.separator)) {
            throw new SecurityException("مسار خارج المشروع مرفوض");
        }
        if (isSensitive(normalized)) throw new SecurityException("الملف محمي ولا يُرسل للوكيل");
        return file;
    }

    private boolean isSensitive(String path) {
        String lower = path.toLowerCase(java.util.Locale.US);
        return lower.equals("local.properties") || lower.startsWith(".git/") || lower.contains("keystore")
                || lower.endsWith(".jks") || lower.endsWith(".keystore") || lower.endsWith(".pem");
    }

    /**
     * ✅ التعديل: يُدرج كل الملفات في القائمة (بما فيها الصور والفيديو).
     * الحد الأقصى للحجم يُطبَّق عند القراءة (readFile)، وليس عند السرد.
     * هذا يسمح للمستخدم برؤية كل ملفات مشروعه في شجرة الملفات.
     */
    private void collect(File directory, List<String> result) throws Exception {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String relative = projectRoot.toPath().relativize(child.getCanonicalFile().toPath()).toString();
            if (isSensitive(relative)) continue;
            if (child.isDirectory()) {
                collect(child, result);
            } else {
                // ✅ كل الملفات — التصنيف يحدث في FileKind عند العرض
                result.add(relative.replace(File.separatorChar, '/'));
            }
        }
    }
}