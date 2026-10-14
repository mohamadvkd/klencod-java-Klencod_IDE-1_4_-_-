package com.flutteride.codweva;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** واجهة البناء في IDE؛ كل عمليات GitHub تتم مباشرة من الهاتف. */
public class FlutterBuildApi {
    private final Context context;
    private int logMode;

    public FlutterBuildApi(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setLogMode(int mode) { this.logMode = mode; }
    public int getLogMode() { return logMode; }

    // أنواع السجل (للتصنيف بالألوان)
    public static final int LOG_TYPE_NORMAL = 0;
    public static final int LOG_TYPE_SUCCESS = 1;
    public static final int LOG_TYPE_WARNING = 2;
    public static final int LOG_TYPE_ERROR = 3;

    // بادئة كشف رسالة "رصيد GitHub منتهي"
    public static final String GITHUB_QUOTA_PREFIX = "__GITHUB_QUOTA__:";

    // بادئة كشف annotations (رسائل GitHub الرسمية)
    public static final String ANNOTATIONS_PREFIX = "__ANNOTATIONS__:";
    public static final String ANNOTATIONS_SEPARATOR = "__ANN_SEP__";

    // حدود السجل (مفتوحة للعرض التدريجي)
    private static final int MAX_ALL_LOG_LINES = 10000;
    private static final int MAX_ERROR_LOG_LINES = 2000;

    public static final class BuildLogLine {
        public final String text;
        public final String filePath;
        public final int line;
        public final int column;
        public final boolean isError;
        public final int logType;

        public BuildLogLine(String text, String filePath, int line, int column) {
            this(text, filePath, line, column, LOG_TYPE_NORMAL);
        }

        public BuildLogLine(String text, String filePath, int line, int column, int logType) {
            this.text = text == null ? "" : text;
            this.filePath = filePath;
            this.line = line;
            this.column = column;
            this.logType = logType;
            this.isError = (logType == LOG_TYPE_ERROR);
        }

        public BuildLogLine(String text, String filePath, int line, int column, boolean isError) {
            this(text, filePath, line, column, isError ? LOG_TYPE_ERROR : LOG_TYPE_NORMAL);
        }
    }

    public interface BuildCallback {
        void onSuccess(File apkFile);
        void onProgress(String message);
        void onError(String error);
        void onStepsUpdate(List<GitHubApi.Step> steps);
        void onUploadProgress(int current, int total);
        void onStepLogs(long jobId, List<BuildLogLine> allLogs, List<BuildLogLine> errorLogs);
        void onRepositoryReady(String repoName);
    }

    public void buildApk(File projectDir, File cacheDir, BuildCallback callback) {
        buildApkDirect(projectDir, cacheDir, callback);
    }

    public void buildApkDirect(final File projectDir, final File cacheDir, final BuildCallback callback) {
        final String token = new SecureStore(context).readGitHubToken();
        if (token == null || token.trim().isEmpty()) {
            if (callback != null) callback.onError("الرجاء ربط حساب GitHub أولًا");
            return;
        }
        if (projectDir == null || !projectDir.isDirectory()) {
            if (callback != null) callback.onError("مجلد المشروع غير صالح");
            return;
        }

        String customRepoName = "";
        boolean isPublicRepo = true;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "flutter_ide_prefs", android.content.Context.MODE_PRIVATE);
            customRepoName = prefs.getString("name_" + projectDir.getName(), "");
            isPublicRepo = prefs.getBoolean("repo_public_" + projectDir.getName(), true);
        } catch (Exception ignored) { }

        if (callback != null) callback.onProgress("الاتصال المباشر بـ GitHub، دون خادم وسيط...");

        final String finalCustomRepoName = customRepoName;
        final boolean finalIsPublicRepo = isPublicRepo;
        GitHubProjectPublisher.publish(token, projectDir, finalCustomRepoName, finalIsPublicRepo,
                new GitHubProjectPublisher.Listener() {
            @Override public void onProgress(String message, int progress) {
                if (callback == null) return;
                if (message != null && message.startsWith("__UPLOAD__")) {
                    String payload = message.substring("__UPLOAD__".length());
                    int slash = payload.indexOf('/');
                    if (slash > 0) {
                        try {
                            int current = Integer.parseInt(payload.substring(0, slash));
                            int total = Integer.parseInt(payload.substring(slash + 1));
                            callback.onUploadProgress(current, total);
                        } catch (NumberFormatException ignored) { }
                    }
                    return;
                }
                callback.onProgress(message + " (" + progress + "%)");
            }

            @Override public void onSuccess(GitHubApi.Repository repository, GitHubApi.Run run) {
                if (callback != null) {
                    callback.onRepositoryReady(repository.name);
                    callback.onProgress("بدأت GitHub Actions رقم " + run.id + "...");
                }
                pollRun(token, repository, run.id, cacheDir, callback, System.currentTimeMillis(), 0);
            }

            @Override public void onError(String message) {
                if (callback != null) callback.onError(message == null ? "فشل نشر المشروع" : message);
            }
        });
    }

    // ============================================================
    // Polling لمتابعة حالة البناء
    // ============================================================
    private void pollRun(final String token, final GitHubApi.Repository repository, final long runId,
                         final File cacheDir, final BuildCallback callback, final long startedAt, final int attempt) {
        GitHubApi.getRun(token, repository.owner, repository.name, runId, new GitHubApi.Callback<GitHubApi.Run>() {
            @Override public void onSuccess(final GitHubApi.Run run) {
                long elapsed = (System.currentTimeMillis() - startedAt) / 1000L;
                long minutes = elapsed / 60L;
                long seconds = elapsed % 60L;
                String timer = minutes + ":" + String.format(Locale.US, "%02d", seconds);

                GitHubApi.getRunJobs(token, repository.owner, repository.name, runId,
                        new GitHubApi.Callback<List<GitHubApi.Job>>() {
                    @Override public void onSuccess(List<GitHubApi.Job> jobs) {
                        if (callback == null || jobs == null || jobs.isEmpty()) return;
                        GitHubApi.Job job = jobs.get(0);
                        callback.onStepsUpdate(job.steps);

                        if ("completed".equalsIgnoreCase(job.status)) {
                            fetchStepLogs(token, repository, job, callback);
                        }
                    }
                    @Override public void onError(String message) {
                        // نتجاهل ونكمل
                    }
                });

                if (callback != null) callback.onProgress("حالة GitHub: " + run.status + " — الوقت " + timer);

                if ("completed".equalsIgnoreCase(run.status)) {
                    if (!"success".equalsIgnoreCase(run.conclusion)) {
                        handleFailedRun(token, repository, runId, run.conclusion, callback);
                    } else {
                        downloadArtifact(token, repository, cacheDir, callback, 0);
                    }
                    return;
                }
                if (attempt >= 200) {
                    if (callback != null) callback.onError("انتهت مهلة انتظار GitHub Actions");
                    return;
                }
                schedulePoll(token, repository, runId, cacheDir, callback, startedAt, attempt + 1);
            }

            @Override public void onError(final String message) {
                if (attempt >= 5) {
                    if (callback != null) callback.onError("فشل متابعة GitHub Actions:\n" + message);
                    return;
                }
                schedulePoll(token, repository, runId, cacheDir, callback, startedAt, attempt + 1);
            }
        });
    }

    /** ✅ إعادة المحاولة بعد 3 ثواني */
    private void schedulePoll(final String token, final GitHubApi.Repository repository, final long runId,
                              final File cacheDir, final BuildCallback callback, final long startedAt, final int nextAttempt) {
        new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(3000L); } catch (InterruptedException ignored) {}
                pollRun(token, repository, runId, cacheDir, callback, startedAt, nextAttempt);
            }
        }, "klencod-github-poll").start();
    }

    /** ✅ يجلب سجلات step معينة عند اكتمال الـ job */
    private void fetchStepLogs(final String token, final GitHubApi.Repository repository,
                                final GitHubApi.Job job, final BuildCallback callback) {
        GitHubApi.downloadJobLogs(token, repository.owner, repository.name, job.id,
                new GitHubApi.Callback<String>() {
            @Override public void onSuccess(String logs) {
                if (callback == null) return;
                List<BuildLogLine> allLogs = extractAllLines(logs);
                List<BuildLogLine> errorLogs = extractErrorLines(logs);
                callback.onStepLogs(job.id, allLogs, errorLogs);
            }
            @Override public void onError(String message) {
                // نتجاهل
            }
        });
    }

    // ============================================================
    // معالجة الفشل (4 حالات)
    // ============================================================
    /**
     * يعالج الفشل بطريقة ذكية — 4 حالات:
     *   1) Job لم يبدأ إطلاقاً (steps فارغة) → رصيد محتمل
     *   2) Job بدأ لكن توقف فجأة (step لم يكتمل) → رصيد محتمل
     *   3) Job فشل بشكل عادي (كل steps completed) → سجل + annotations
     *   4) فشل عام → عرض الخطأ الحقيقي
     *
     * لا نعرض "رصيد منتهي" إلا بعد التحقق من الرسالة الرسمية لـ GitHub
     */
    private void handleFailedRun(final String token, final GitHubApi.Repository repository,
                                  final long runId, final String conclusion, final BuildCallback callback) {
        if (callback == null) return;

        GitHubApi.getRunJobs(token, repository.owner, repository.name, runId,
                new GitHubApi.Callback<List<GitHubApi.Job>>() {
            @Override public void onSuccess(List<GitHubApi.Job> jobs) {
                // الحالة 1: لا توجد jobs
                if (jobs == null || jobs.isEmpty()) {
                    fetchAnnotationsForQuota(token, repository, runId, conclusion, callback);
                    return;
                }

                GitHubApi.Job job = jobs.get(0);

                // الحالة 2: steps فارغة
                if (job.steps == null || job.steps.isEmpty()) {
                    fetchAnnotationsForQuota(token, repository, runId, conclusion, callback);
                    return;
                }

                // الحالة 3: هل هناك step لم يكتمل؟
                boolean hasIncompleteStep = false;
                for (GitHubApi.Step step : job.steps) {
                    if (!"completed".equalsIgnoreCase(step.status)) {
                        hasIncompleteStep = true;
                        break;
                    }
                }

                if (hasIncompleteStep) {
                    fetchAnnotationsForQuota(token, repository, runId, conclusion, callback);
                    return;
                }

                // الحالة 4: فشل عادي (كل steps completed) — اعرض الخطأ الحقيقي
                fetchFailureLogsAndAnnotations(token, repository, runId, conclusion, callback);
            }

            @Override public void onError(String message) {
                fetchFailureLogsAndAnnotations(token, repository, runId, conclusion, callback);
            }
        });
    }

    /** ✅ يجلب annotations للتحقق من الرصيد — يعرض "رصيد" فقط عند تأكيد الكلمات الرسمية */
    private void fetchAnnotationsForQuota(final String token, final GitHubApi.Repository repository,
                                           final long runId, final String conclusion,
                                           final BuildCallback callback) {
        if (callback != null) callback.onProgress("جارٍ التحقق من سبب فشل GitHub Actions...");

        GitHubApi.getRunAnnotations(token, repository.owner, repository.name, runId,
                new GitHubApi.Callback<List<String>>() {
            @Override public void onSuccess(List<String> annotations) {
                StringBuilder sb = new StringBuilder();
                boolean hasQuotaKeyword = false;

                if (annotations != null) {
                    for (String msg : annotations) {
                        if (msg == null || msg.trim().isEmpty()) continue;

                        String lower = msg.toLowerCase(Locale.US);

                        // الكلمات الرسمية من GitHub عند انتهاء الرصيد
                        if (lower.contains("job was not started")
                                || lower.contains("recent account payments have failed")
                                || lower.contains("spending limit needs to be increased")) {
                            hasQuotaKeyword = true;
                        }

                        if (sb.length() > 0) sb.append("\n\n");
                        sb.append(msg.trim());
                    }
                }

                if (hasQuotaKeyword) {
                    if (callback != null) {
                        callback.onError(GITHUB_QUOTA_PREFIX + sb.toString());
                    }
                } else {
                    // ليس رصيدًا — اعرض الخطأ الحقيقي
                    fetchFailureLogsAndAnnotations(token, repository, runId, conclusion, callback);
                }
            }

            @Override public void onError(String message) {
                // فشل جلب annotations → اعرض الخطأ الحقيقي
                fetchFailureLogsAndAnnotations(token, repository, runId, conclusion, callback);
            }
        });
    }

    /** ✅ يجلب logs + annotations معاً للفشل العادي */
    private void fetchFailureLogsAndAnnotations(
            final String token,
            final GitHubApi.Repository repository,
            final long runId,
            final String conclusion,
            final BuildCallback callback) {

        if (callback != null) callback.onProgress("فشل البناء، جارٍ جلب سجل GitHub...");

        GitHubApi.downloadRunLogs(token, repository.owner, repository.name, runId,
                new GitHubApi.Callback<String>() {
            @Override public void onSuccess(final String logs) {
                // افحص الرصيد أولاً (احتياط)
                String quotaMessage = extractQuotaFromLogs(logs);
                if (quotaMessage != null) {
                    if (callback != null) {
                        callback.onError(GITHUB_QUOTA_PREFIX + quotaMessage);
                    }
                    return;
                }

                // اجلب annotations
                GitHubApi.getRunAnnotations(token, repository.owner, repository.name, runId,
                        new GitHubApi.Callback<List<String>>() {
                    @Override public void onSuccess(List<String> annotations) {
                        // 1. أرسل السجل الكامل
                        if (callback != null) {
                            callback.onProgress("===== سجل GitHub =====\n" + logs);
                        }

                        // 2. أرسل annotations كبادئة خاصة
                        if (annotations != null && !annotations.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < annotations.size(); i++) {
                                String annot = annotations.get(i);
                                if (annot == null || annot.trim().isEmpty()) continue;
                                if (sb.length() > 0) sb.append(ANNOTATIONS_SEPARATOR);
                                sb.append(annot.trim());
                            }
                            if (sb.length() > 0) {
                                if (callback != null) {
                                    callback.onError(ANNOTATIONS_PREFIX + sb.toString());
                                }
                                return;
                            }
                        }

                        // 3. لا annotations → الخطأ العادي
                        if (callback != null) {
                            callback.onError("فشل GitHub Actions: " + conclusion);
                        }
                    }

                    @Override public void onError(String message) {
                        if (callback != null) callback.onProgress("===== سجل GitHub =====\n" + logs);
                        if (callback != null) callback.onError("فشل GitHub Actions: " + conclusion);
                    }
                });
            }

            @Override public void onError(String message) {
                if (callback != null) callback.onError("فشل البناء وتعذر جلب سجل GitHub:\n" + message);
            }
        });
    }

    /** ✅ يفحص الرصيد بدقة — يجب اجتماع 2-3 كلمات رسمية */
    private String extractQuotaFromLogs(String logs) {
        if (logs == null || logs.isEmpty()) return null;

        String lower = logs.toLowerCase(Locale.US);

        boolean hasJobNotStarted = lower.contains("job was not started");
        boolean hasPaymentsFailed = lower.contains("recent account payments have failed");
        boolean hasSpendingLimit = lower.contains("spending limit needs to be increased");

        boolean isQuotaError = hasJobNotStarted && (hasPaymentsFailed || hasSpendingLimit);

        if (!isQuotaError) return null;

        String[] lines = logs.split("\\r?\\n");
        java.util.Set<Integer> indices = new java.util.HashSet<Integer>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) continue;
            String lineLower = line.toLowerCase(Locale.US);

            if (lineLower.contains("job was not started")
                    || lineLower.contains("recent account payments")
                    || lineLower.contains("spending limit")
                    || lineLower.contains("billing")) {
                for (int j = Math.max(0, i - 1); j <= Math.min(lines.length - 1, i + 2); j++) {
                    indices.add(j);
                }
            }
        }

        java.util.List<Integer> sorted = new java.util.ArrayList<Integer>(indices);
        java.util.Collections.sort(sorted);

        StringBuilder sb = new StringBuilder();
        for (Integer idx : sorted) {
            if (idx < 0 || idx >= lines.length) continue;
            String raw = lines[idx];
            if (raw == null) continue;
            String clean = raw.replaceAll("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s*", "").trim();
            if (clean.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(clean);
            if (sb.length() > 800) break;
        }

        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    /** ✅ مصنّف للاستخدام الخارجي */
    public static int classifyAnnotationStatic(String annotation) {
        if (annotation == null || annotation.trim().isEmpty()) return LOG_TYPE_NORMAL;
        String lower = annotation.toLowerCase(Locale.US).trim();

        if (lower.startsWith("error:") || lower.startsWith("error ")) return LOG_TYPE_ERROR;
        if (lower.contains("no file in") && lower.contains("matched to")) return LOG_TYPE_ERROR;
        if (lower.contains("make sure you have checked out")) return LOG_TYPE_ERROR;
        if (lower.contains("process completed with exit code")) return LOG_TYPE_ERROR;
        if (lower.contains("build failed")) return LOG_TYPE_ERROR;
        if (lower.contains("compilation failed")) return LOG_TYPE_ERROR;
        if (lower.contains("command failed")) return LOG_TYPE_ERROR;
        if (lower.contains("not found")) return LOG_TYPE_ERROR;
        if (lower.contains("missing")) return LOG_TYPE_ERROR;

        if (lower.startsWith("warning:") || lower.startsWith("warning ")) return LOG_TYPE_WARNING;
        if (lower.contains("deprecated")) return LOG_TYPE_WARNING;
        if (lower.contains("will no longer receive updates")) return LOG_TYPE_WARNING;
        if (lower.contains("will migrate to")) return LOG_TYPE_WARNING;
        if (lower.contains("node 20 is being deprecated")) return LOG_TYPE_WARNING;

        return LOG_TYPE_NORMAL;
    }

    // ============================================================
    // تنزيل APK
    // ============================================================
    private void downloadArtifact(final String token, final GitHubApi.Repository repository, final File cacheDir,
                                  final BuildCallback callback, final int attempt) {
        if (callback != null) callback.onProgress("اكتمل البناء، جارٍ تنزيل APK من GitHub...");
        GitHubApi.latestArtifact(token, repository.owner, repository.name, new GitHubApi.Callback<GitHubApi.Artifact>() {
            @Override public void onSuccess(final GitHubApi.Artifact artifact) {
                if (artifact.expired) {
                    if (callback != null) callback.onError("Artifact منتهي الصلاحية");
                    return;
                }
                File destination = new File(cacheDir, "klencod-direct-release.apk");
                GitHubApi.downloadArtifact(token, repository.owner, repository.name, artifact.id, destination, new GitHubApi.Callback<File>() {
                    @Override public void onSuccess(File apk) {
                        try {
                            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            if (!downloads.exists() && !downloads.mkdirs()) throw new IOException("تعذر إنشاء مجلد التنزيلات");
                            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                            File saved = new File(downloads, "app_" + safeName(repository.name) + "_" + stamp + ".apk");
                            copy(apk, saved);
                            if (callback != null) {
                                callback.onProgress("تم حفظ APK في مجلد التنزيلات.");
                                callback.onSuccess(saved);
                            }
                        } catch (Exception error) {
                            if (callback != null) callback.onError("تعذر حفظ APK:\n" + error.getMessage());
                        }
                    }

                    @Override public void onError(String message) {
                        if (callback != null) callback.onError("تعذر تنزيل APK:\n" + message);
                    }
                });
            }

            @Override public void onError(final String message) {
                if (attempt < 4) {
                    new Thread(new Runnable() {
                        @Override public void run() {
                            try { Thread.sleep(5000L); } catch (InterruptedException ignored) {}
                            downloadArtifact(token, repository, cacheDir, callback, attempt + 1);
                        }
                    }, "klencod-artifact-retry").start();
                } else if (callback != null) {
                    callback.onError("تعذر العثور على APK بعد نجاح البناء:\n" + message);
                }
            }
        });
    }

    private static String safeName(String value) {
        if (value == null || value.trim().isEmpty()) return "flutter_app";
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private static void copy(File source, File target) throws IOException {
        FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(target);
        byte[] buffer = new byte[8192];
        int count;
        try {
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
        } finally {
            input.close();
            output.close();
        }
    }

    public void installApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(context, "ملف APK غير موجود", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Toast.makeText(context, "لا يمكن تثبيت التطبيقات", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception error) {
            Toast.makeText(context, "خطأ في التثبيت: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ============================================================
    // استخراج السطور من السجل
    // ============================================================
    private List<BuildLogLine> extractAllLines(String logs) {
        List<BuildLogLine> result = new ArrayList<BuildLogLine>();
        if (logs == null || logs.trim().isEmpty()) return result;

        Pattern locationPattern = Pattern.compile(
                "([^\\s]+\\.(?:dart|java|kt|kts|py|js|ts|xml|yaml|yml|json|gradle))[:(](\\d+)(?::(\\d+))?");

        String[] lines = logs.split("\\r?\\n");
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String clean = trimmed.replaceAll("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s*", "");
            if (isAdminLine(clean)) continue;

            String filePath = null;
            int lineNum = 0;
            int colNum = 0;
            Matcher m = locationPattern.matcher(clean);
            if (m.find()) {
                filePath = m.group(1);
                try {
                    lineNum = Integer.parseInt(m.group(2));
                    if (m.group(3) != null) colNum = Integer.parseInt(m.group(3));
                } catch (NumberFormatException ignored) {}
            }

            int logType = classifyLogLine(clean);
            result.add(new BuildLogLine(clean, filePath, lineNum, colNum, logType));
            if (result.size() >= MAX_ALL_LOG_LINES) break;
        }
        return result;
    }

    private List<BuildLogLine> extractErrorLines(String logs) {
        List<BuildLogLine> result = new ArrayList<BuildLogLine>();
        if (logs == null || logs.trim().isEmpty()) return result;

        String[] lines = logs.split("\\r?\\n");
        if (lines.length == 0) return result;

        java.util.Set<Integer> matchedIndices = new java.util.HashSet<Integer>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (isAdminLine(trimmed)) continue;

            int logType = classifyLogLine(trimmed);
            if (logType == LOG_TYPE_ERROR || logType == LOG_TYPE_WARNING) {
                matchedIndices.add(i);
                for (int j = Math.max(0, i - 2); j <= Math.min(lines.length - 1, i + 2); j++) {
                    matchedIndices.add(j);
                }
            }
        }
        if (matchedIndices.isEmpty()) return result;

        Pattern locationPattern = Pattern.compile(
                "([^\\s]+\\.(?:dart|java|kt|kts|py|js|ts|xml|yaml|yml|json|gradle))[:(](\\d+)(?::(\\d+))?");
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        List<Integer> sortedIndices = new ArrayList<Integer>(matchedIndices);
        java.util.Collections.sort(sortedIndices);

        for (Integer idx : sortedIndices) {
            if (idx < 0 || idx >= lines.length) continue;
            String line = lines[idx];
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String clean = trimmed.replaceAll("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s*", "");
            if (isAdminLine(clean)) continue;

            String key = clean.toLowerCase(Locale.US);
            if (seen.contains(key)) continue;
            seen.add(key);

            String filePath = null;
            int lineNum = 0;
            int colNum = 0;
            Matcher m = locationPattern.matcher(clean);
            if (m.find()) {
                filePath = m.group(1);
                try {
                    lineNum = Integer.parseInt(m.group(2));
                    if (m.group(3) != null) colNum = Integer.parseInt(m.group(3));
                } catch (NumberFormatException ignored) {}
            }

            int logType = classifyLogLine(clean);
            result.add(new BuildLogLine(clean, filePath, lineNum, colNum, logType));
            if (result.size() >= MAX_ERROR_LOG_LINES) break;
        }
        return result;
    }

    private boolean isAdminLine(String line) {
        if (line == null || line.isEmpty()) return true;
        String lower = line.toLowerCase(Locale.US);

        if (line.startsWith("##[") || line.startsWith("::")) return true;
        if (lower.contains("current runner version")) return true;
        if (lower.contains("hosted compute agent")) return true;
        if (lower.startsWith("version:")) return true;
        if (lower.startsWith("commit:")) return true;
        if (lower.startsWith("build date:")) return true;
        if (lower.startsWith("worker id:")) return true;
        if (lower.startsWith("azure region:")) return true;
        if (lower.equals("ubuntu")) return true;
        if (lower.equals("lts")) return true;
        if (lower.startsWith("image:")) return true;
        if (lower.startsWith("image release:")) return true;
        if (lower.startsWith("included software:")) return true;
        if (lower.startsWith("contents:")) return true;
        if (lower.startsWith("metadata:")) return true;
        if (lower.startsWith("packages:")) return true;
        if (lower.startsWith("secret source:")) return true;
        if (lower.startsWith("cache mode:")) return true;
        if (lower.contains("prepare workflow directory")) return true;
        if (lower.contains("prepare all required actions")) return true;
        if (lower.contains("getting action download info")) return true;
        if (lower.contains("complete job name")) return true;
        if (lower.startsWith("[command]")) return true;
        if (lower.startsWith("with:")) return true;
        if (lower.startsWith("env:")) return true;
        if (lower.startsWith("shell:")) return true;
        if (lower.startsWith("hint:")) return true;
        if (lower.startsWith("ssh-")) return true;
        if (lower.startsWith("allow-unsafe")) return true;
        if (lower.startsWith("persist-credentials")) return true;
        if (lower.startsWith("fetch-depth")) return true;
        if (lower.startsWith("fetch-tags")) return true;
        if (lower.startsWith("show-progress")) return true;
        if (lower.startsWith("sparse-checkout")) return true;
        if (lower.startsWith("set-safe-directory")) return true;
        if (lower.contains("syncing repository")) return true;
        if (lower.contains("working directory is")) return true;
        if (lower.contains("temporarily overriding home")) return true;
        if (lower.contains("deleting the contents")) return true;
        if (lower.contains("initialized empty git repository")) return true;
        if (lower.contains("node 20 is being deprecated")) return true;
        if (lower.contains("node 24 by default")) return true;
        if (lower.contains("actions_allow_use_unsecure")) return true;
        if (lower.contains("terminate orphan process")) return true;
        if (lower.contains("post job cleanup")) return true;
        if (lower.contains("cleaning up orphan")) return true;

        return false;
    }

    private int classifyLogLine(String line) {
        if (line == null) return LOG_TYPE_NORMAL;
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return LOG_TYPE_NORMAL;
        String lower = trimmed.toLowerCase(Locale.US);

        if (lower.startsWith("(node:")) return LOG_TYPE_NORMAL;
        if (lower.contains("deprecationwarning")) return LOG_TYPE_NORMAL;
        if (lower.contains("--trace-deprecation")) return LOG_TYPE_NORMAL;
        if (lower.contains("use `node --trace")) return LOG_TYPE_NORMAL;
        if (lower.contains("[dep0040]")) return LOG_TYPE_NORMAL;
        if (lower.contains("[dep0169]")) return LOG_TYPE_NORMAL;
        if (lower.contains("punycode")) return LOG_TYPE_NORMAL;
        if (lower.contains("url.parse()")) return LOG_TYPE_NORMAL;

        if (lower.startsWith("resolved java")) return LOG_TYPE_SUCCESS;
        if (lower.startsWith("setting java")) return LOG_TYPE_SUCCESS;
        if (lower.contains("\"install android sdk platform") && lower.contains("complete")) return LOG_TYPE_SUCCESS;
        if (lower.contains("\"install android sdk platform") && lower.contains("finished")) return LOG_TYPE_SUCCESS;
        if (lower.contains("install android sdk build-tools") && lower.contains("complete")) return LOG_TYPE_SUCCESS;
        if (lower.startsWith("running gradle task")) return LOG_TYPE_SUCCESS;
        if (lower.contains("artifact name is valid")) return LOG_TYPE_SUCCESS;
        if (lower.contains("root directory input is valid")) return LOG_TYPE_SUCCESS;
        if (lower.contains("beginning upload of artifact")) return LOG_TYPE_SUCCESS;
        if (lower.startsWith("uploaded bytes")) return LOG_TYPE_SUCCESS;
        if (lower.startsWith("sent ") && lower.contains("bytes")) return LOG_TYPE_SUCCESS;
        if (lower.contains("with the provided path, there will be")) return LOG_TYPE_SUCCESS;
        if (lower.contains("build succeeded")) return LOG_TYPE_SUCCESS;
        if (lower.contains("built build/app")) return LOG_TYPE_SUCCESS;
        if (lower.contains("apk generated")) return LOG_TYPE_SUCCESS;
        if (lower.contains("✓ built")) return LOG_TYPE_SUCCESS;

        if (lower.contains("font asset") && lower.contains("tree-shaken")) return LOG_TYPE_WARNING;
        if (lower.contains("tree-shaking can be disabled")) return LOG_TYPE_WARNING;
        if (lower.contains("--no-tree-shake-icons")) return LOG_TYPE_WARNING;
        if (lower.contains("unable to find directory entry in pubspec.yaml")) return LOG_TYPE_WARNING;
        if (lower.contains("warning:") && !lower.contains("0 warning")) return LOG_TYPE_WARNING;
        if (lower.contains("warn:") && !lower.contains("0 warning")) return LOG_TYPE_WARNING;
        if (lower.startsWith("warning:")) return LOG_TYPE_WARNING;
        if (lower.contains("deprecated") && !lower.contains("deprecationwarning")) return LOG_TYPE_WARNING;
        if (lower.contains("only the first match will be used")) return LOG_TYPE_WARNING;

        if (lower.contains("0 warning") || lower.contains("warnings: 0")) return LOG_TYPE_SUCCESS;
        if (lower.contains("0 error") || lower.contains("errors: 0")) return LOG_TYPE_SUCCESS;

        if (lower.contains("error:") || lower.startsWith("error")) return LOG_TYPE_ERROR;
        if (lower.contains("build failed")) return LOG_TYPE_ERROR;
        if (lower.contains("compilation failed")) return LOG_TYPE_ERROR;
        if (lower.contains("execution failed")) return LOG_TYPE_ERROR;
        if (lower.contains("a problem occurred")) return LOG_TYPE_ERROR;
        if (lower.contains("what went wrong")) return LOG_TYPE_ERROR;
        if (lower.contains("fatal:")) return LOG_TYPE_ERROR;
        if (lower.contains("critical:")) return LOG_TYPE_ERROR;
        if (lower.contains("exception:")) return LOG_TYPE_ERROR;
        if (lower.contains("could not resolve")) return LOG_TYPE_ERROR;
        if (lower.contains("could not find")) return LOG_TYPE_ERROR;
        if (lower.contains("could not download")) return LOG_TYPE_ERROR;
        if (lower.contains("could not create")) return LOG_TYPE_ERROR;
        if (lower.contains("could not determine")) return LOG_TYPE_ERROR;
        if (lower.contains("cannot find")) return LOG_TYPE_ERROR;
        if (lower.contains("cannot resolve")) return LOG_TYPE_ERROR;
        if (lower.contains("unable to load")) return LOG_TYPE_ERROR;
        if (lower.contains("undefined name")) return LOG_TYPE_ERROR;
        if (lower.contains("isn't defined")) return LOG_TYPE_ERROR;
        if (lower.contains("no such file")) return LOG_TYPE_ERROR;
        if (lower.contains("unhandled exception")) return LOG_TYPE_ERROR;
        if (lower.contains("formatexception")) return LOG_TYPE_ERROR;
        if (lower.contains("stateerror")) return LOG_TYPE_ERROR;
        if (lower.contains("assertionerror")) return LOG_TYPE_ERROR;
        if (lower.contains("indexerror")) return LOG_TYPE_ERROR;
        if (lower.contains("rangeerror")) return LOG_TYPE_ERROR;
        if (lower.contains("nosuchmethoderror")) return LOG_TYPE_ERROR;
        if (lower.contains("caused by")) return LOG_TYPE_ERROR;
        if (lower.contains("rejected")) return LOG_TYPE_ERROR;
        if (lower.contains("failed")) return LOG_TYPE_ERROR;
        if (lower.contains("failure")) return LOG_TYPE_ERROR;
        if (lower.contains("fatal")) return LOG_TYPE_ERROR;

        return LOG_TYPE_NORMAL;
    }
}