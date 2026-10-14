package com.flutteride.codweva;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** مخزن داخلي مستقل لنسخ APK التي نجح بناؤها. */
public final class BuiltAppsStore {
    private final File libraryDir;

    public BuiltAppsStore(Context context) {
        libraryDir = new File(context.getFilesDir(), "built_apps");
        if (!libraryDir.exists()) {
            libraryDir.mkdirs();
        }
    }

    public File save(File apkFile, String projectName) throws IOException {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0) {
            throw new IOException("ملف APK غير موجود أو فارغ");
        }
        if (!libraryDir.exists() && !libraryDir.mkdirs()) {
            throw new IOException("تعذر إنشاء مكتبة التطبيقات");
        }

        String safeName = projectName == null ? "app" : projectName.trim();
        safeName = safeName.replaceAll("[^a-zA-Z0-9._-]+", "_");
        if (safeName.isEmpty()) {
            safeName = "app";
        }

        File destination = new File(
                libraryDir,
                safeName + "_" + System.currentTimeMillis() + ".apk"
        );
        copy(apkFile, destination);
        return destination;
    }

    public List<File> list() {
        File[] files = libraryDir.listFiles((dir, name) ->
                name != null && name.toLowerCase().endsWith(".apk")
        );
        List<File> result = new ArrayList<>();
        if (files != null) {
            result.addAll(Arrays.asList(files));
        }
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public void delete(File file) throws IOException {
        File safeFile = safeFile(file);
        if (!safeFile.exists() || !safeFile.delete()) {
            throw new IOException("تعذر حذف التطبيق");
        }
    }

    public File getLibraryDir() {
        return libraryDir;
    }

    private File safeFile(File file) throws IOException {
        if (file == null) {
            throw new IOException("ملف غير صالح");
        }

        String root = libraryDir.getCanonicalPath() + File.separator;
        String path = file.getCanonicalPath();
        if (!path.startsWith(root) || !path.toLowerCase().endsWith(".apk")) {
            throw new IOException("مسار التطبيق خارج المكتبة");
        }
        return file;
    }

    private void copy(File source, File destination) throws IOException {
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(destination);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
