package com.flutteride.codweva;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** ينسق نشر مشروع Flutter وتشغيل GitHub Actions من الهاتف مباشرة. */
public final class GitHubProjectPublisher {
    public interface Listener {
        void onProgress(String message, int progress);
        void onSuccess(GitHubApi.Repository repository, GitHubApi.Run run);
        void onError(String message);
    }

    private GitHubProjectPublisher() {}

    public static void publish(final String token, final File project,
                                final String customRepoName,
                                final boolean isPublicRepo,
                                final Listener listener) {
        if (token == null || token.trim().isEmpty()) {
            listener.onError("اربط حساب GitHub أولًا");
            return;
        }
        if (project == null || !project.isDirectory()) {
            listener.onError("مجلد المشروع غير صالح");
            return;
        }

        // ✅ وقت بدء النشر (لضمان جلب Run جديد فقط)
        final long publishStartedAtMs = System.currentTimeMillis();

        listener.onProgress("التحقق من حساب GitHub...", 5);
        GitHubApi.getUser(token, new GitHubApi.Callback<String>() {
            @Override public void onSuccess(final String owner) {
                String rawName = (customRepoName != null && !customRepoName.trim().isEmpty())
                        ? customRepoName
                        : project.getName();
                final String repoName = repositoryName(rawName);

                GitHubApi.getRepository(token, owner, repoName, new GitHubApi.Callback<GitHubApi.Repository>() {
                    @Override public void onSuccess(GitHubApi.Repository repository) {
                        listener.onProgress("تم العثور على المستودع، جارٍ تحديث الملفات...", 18);
                        uploadFiles(token, project, repository, publishStartedAtMs, listener);
                    }

                    @Override public void onError(String message) {
                        if (message != null && message.contains("GitHub HTTP 404")) {
                            createRepository(token, repoName, project, isPublicRepo, publishStartedAtMs, listener);
                        } else {
                            listener.onError("فشل الوصول للمستودع:\n" + message);
                        }
                    }
                });
            }

            @Override public void onError(String message) {
                listener.onError("فشل التعرف على الحساب:\n" + message);
            }
        });
    }

    // ============================================================
    // إنشاء المستودع
    // ============================================================
    private static void createRepository(final String token, final String name,
                                          final File project, final boolean isPublicRepo,
                                          final long publishStartedAtMs, final Listener listener) {
        String typeLabel = isPublicRepo ? "عام" : "خاص";
        listener.onProgress("لم يوجد المستودع، جارٍ إنشاؤه (" + typeLabel + ")...", 10);
        createRepositoryWithRetry(token, name, 0, project, isPublicRepo, publishStartedAtMs, listener);
    }

    private static void createRepositoryWithRetry(final String token, final String baseName,
                                                    final int suffix, final File project,
                                                    final boolean isPublicRepo,
                                                    final long publishStartedAtMs,
                                                    final Listener listener) {
        if (suffix > 50) {
            listener.onError("تعذر إيجاد اسم مستودع متاح (جرب 50 اسماً)");
            return;
        }

        final String candidate = suffix == 0 ? baseName : (baseName + "_" + suffix);

        if (suffix > 0) {
            listener.onProgress("جارٍ تجربة الاسم: \"" + candidate + "\"...", 12);
        }

        GitHubApi.createRepository(token, candidate, !isPublicRepo, new GitHubApi.Callback<GitHubApi.Repository>() {
            @Override public void onSuccess(GitHubApi.Repository repository) {
                String typeLabel = isPublicRepo ? "عام" : "خاص";
                if (suffix > 0) {
                    listener.onProgress("الاسم \"" + baseName + "\" كان مستخدماً، تم استخدام \"" + candidate + "\" (" + typeLabel + ")", 20);
                } else {
                    listener.onProgress("تم إنشاء المستودع \"" + candidate + "\" (" + typeLabel + ") بنجاح", 20);
                }
                uploadFiles(token, project, repository, publishStartedAtMs, listener);
            }

            @Override public void onError(String message) {
                if (isNameConflict(message)) {
                    createRepositoryWithRetry(token, baseName, suffix + 1, project, isPublicRepo, publishStartedAtMs, listener);
                } else {
                    listener.onError("فشل إنشاء المستودع:\n" + message);
                }
            }
        });
    }

    private static boolean isNameConflict(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.US);
        return lower.contains("http 422")
                || lower.contains("already exists")
                || lower.contains("name already")
                || lower.contains("repository creation failed")
                || lower.contains("validation failed")
                || lower.contains("name is not available");
    }

    // ============================================================
    // رفع الملفات
    // ============================================================
    private static void uploadFiles(final String token, final File project,
                                     final GitHubApi.Repository repository,
                                     final long publishStartedAtMs,
                                     final Listener listener) {
        final List<File> files = new ArrayList<File>();
        collect(project, project, files);
        if (files.isEmpty()) {
            listener.onError("لا توجد ملفات قابلة للرفع داخل المشروع");
            return;
        }
        Collections.sort(files, new Comparator<File>() {
            @Override public int compare(File first, File second) {
                return relative(project, first).compareTo(relative(project, second));
            }
        });
        uploadNext(token, project, repository, files, 0, publishStartedAtMs, listener);
    }

    private static void uploadNext(final String token, final File project,
                                    final GitHubApi.Repository repository,
                                    final List<File> files, final int index,
                                    final long publishStartedAtMs,
                                    final Listener listener) {
        if (index >= files.size()) {
            listener.onProgress("اكتمل رفع الملفات، جارٍ تشغيل GitHub Actions...", 82);
            GitHubApi.triggerBuild(token, repository.owner, repository.name, repository.defaultBranch, new GitHubApi.Callback<Boolean>() {
                @Override public void onSuccess(Boolean value) {
                    listener.onProgress("تم تشغيل GitHub Actions، جارٍ انتظار البناء...", 85);
                    findStartedRun(token, repository, publishStartedAtMs, listener, 0);
                }

                @Override public void onError(String message) {
                    listener.onError("فشل تشغيل GitHub Actions:\n" + message);
                }
            });
            return;
        }

        final File file = files.get(index);
        final String path = relative(project, file);
        final int progress = 25 + (index * 55 / Math.max(1, files.size()));

        listener.onProgress("__UPLOAD__" + (index + 1) + "/" + files.size(), progress);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final byte[] bytes = readBytes(file);
                    GitHubApi.uploadBytes(token, repository.owner, repository.name,
                            repository.defaultBranch, path, bytes,
                            "Update " + path, new GitHubApi.Callback<Boolean>() {
                        @Override public void onSuccess(Boolean value) {
                            uploadNext(token, project, repository, files, index + 1, publishStartedAtMs, listener);
                        }

                        @Override public void onError(String message) {
                            listener.onError("فشل رفع \"" + path + "\":\n" + message);
                        }
                    });
                } catch (Exception error) {
                    listener.onError("تعذر قراءة \"" + path + "\":\n" + error.getMessage());
                }
            }
        }, "klencod-file-read").start();
    }

    // ============================================================
    // انتظار بدء الـ Run
    // ============================================================
    private static void findStartedRun(final String token, final GitHubApi.Repository repository,
                                        final long publishStartedAtMs, final Listener listener,
                                        final int attempt) {
        // ✅ نمرر publishStartedAtMs لضمان جلب Run جديد فقط
        GitHubApi.latestRun(token, repository.owner, repository.name, publishStartedAtMs,
                new GitHubApi.Callback<GitHubApi.Run>() {
            @Override public void onSuccess(GitHubApi.Run run) {
                listener.onSuccess(repository, run);
            }

            @Override public void onError(String message) {
                if (attempt >= 8) {
                    listener.onError("تم تشغيل البناء، لكن تعذر الحصول على رقم العملية:\n" + message);
                    return;
                }
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { Thread.sleep(2500L); } catch (InterruptedException ignored) {}
                        findStartedRun(token, repository, publishStartedAtMs, listener, attempt + 1);
                    }
                }, "klencod-run-wait").start();
            }
        });
    }

    // ============================================================
    // الأدوات
    // ============================================================
    private static void collect(File root, File directory, List<File> result) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String path = relative(root, child);
            if (isExcluded(path)) continue;
            if (child.isDirectory()) collect(root, child, result);
            else if (child.isFile() && child.length() <= 10L * 1024L * 1024L) result.add(child);
        }
    }

    private static boolean isExcluded(String path) {
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase(java.util.Locale.US);
        return lower.equals("local.properties") || lower.startsWith(".git/")
                || lower.startsWith(".klencod_backup/") || lower.contains("keystore")
                || lower.endsWith(".jks") || lower.endsWith(".keystore") || lower.endsWith(".pem");
    }

    private static byte[] readBytes(File file) throws IOException {
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        try {
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } finally {
            input.close();
        }
        return output.toByteArray();
    }

    private static String relative(File root, File file) {
        try {
            String base = root.getCanonicalPath();
            String value = file.getCanonicalPath();
            if (value.startsWith(base + File.separator)) {
                return value.substring(base.length() + 1).replace(File.separatorChar, '/');
            }
        } catch (IOException ignored) {}
        return file.getName();
    }

    private static String repositoryName(String raw) {
        String name = raw == null ? "flutter-app" : raw.trim();
        if (name.isEmpty()) name = "flutter-app";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean isArabic = (c >= '\u0600' && c <= '\u06FF')
                    || (c >= '\u0750' && c <= '\u077F')
                    || (c >= '\uFB50' && c <= '\uFDFF')
                    || (c >= '\uFE70' && c <= '\uFEFF');
            boolean isLatinDigit = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9');
            boolean isSafe = c == '-' || c == '_' || c == '.';

            if (isArabic || isLatinDigit || isSafe) sb.append(c);
            else if (c == ' ') sb.append('-');
        }

        name = sb.toString();
        while (name.startsWith("-") || name.startsWith(".")) name = name.substring(1);
        while (name.endsWith("-") || name.endsWith(".")) name = name.substring(0, name.length() - 1);
        if (name.isEmpty()) name = "flutter-app";
        return name.length() > 90 ? name.substring(0, 90) : name;
    }
}