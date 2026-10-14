package com.flutteride.codweva;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GitHubApi {

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public static final class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int expiresIn;
        public final int interval;

        DeviceCode(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    public static final class Repository {
        public final String owner;
        public final String name;
        public final String htmlUrl;
        public final String defaultBranch;

        Repository(String owner, String name, String htmlUrl, String defaultBranch) {
            this.owner = owner;
            this.name = name;
            this.htmlUrl = htmlUrl;
            this.defaultBranch = defaultBranch;
        }
    }

    public static final class Run {
        public final long id;
        public final String status;
        public final String conclusion;
        public final String htmlUrl;
        public final long createdAtMs;
        public final String headSha;

        Run(long id, String status, String conclusion, String htmlUrl) {
            this(id, status, conclusion, htmlUrl, 0L, "");
        }

        Run(long id, String status, String conclusion, String htmlUrl, long createdAtMs) {
            this(id, status, conclusion, htmlUrl, createdAtMs, "");
        }

        Run(long id, String status, String conclusion, String htmlUrl, long createdAtMs, String headSha) {
            this.id = id;
            this.status = status;
            this.conclusion = conclusion;
            this.htmlUrl = htmlUrl;
            this.createdAtMs = createdAtMs;
            this.headSha = headSha == null ? "" : headSha;
        }
    }

    public static final class Artifact {
        public final long id;
        public final String name;
        public final long size;
        public final boolean expired;

        Artifact(long id, String name, long size, boolean expired) {
            this.id = id;
            this.name = name;
            this.size = size;
            this.expired = expired;
        }
    }

    public static final class Step {
        public final int number;
        public final String name;
        public final String status;
        public final String conclusion;
        public final long startedAtMs;
        public final long completedAtMs;

        public Step(int number, String name, String status, String conclusion,
                    long startedAtMs, long completedAtMs) {
            this.number = number;
            this.name = name == null ? "" : name;
            this.status = status == null ? "" : status;
            this.conclusion = conclusion == null ? "" : conclusion;
            this.startedAtMs = startedAtMs;
            this.completedAtMs = completedAtMs;
        }

        public long durationMs() {
            if (startedAtMs <= 0 || completedAtMs <= 0) return 0L;
            return Math.max(0L, completedAtMs - startedAtMs);
        }
    }

    public static final class Job {
        public final long id;
        public final String name;
        public final String status;
        public final String conclusion;
        public final long startedAtMs;
        public final long completedAtMs;
        public final List<Step> steps;

        public Job(long id, String name, String status, String conclusion,
                   long startedAtMs, long completedAtMs, List<Step> steps) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.status = status == null ? "" : status;
            this.conclusion = conclusion == null ? "" : conclusion;
            this.startedAtMs = startedAtMs;
            this.completedAtMs = completedAtMs;
            this.steps = steps == null ? new ArrayList<Step>() : steps;
        }
    }

    private GitHubApi() {}

    public static void requestDeviceCode(final Callback<DeviceCode> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    requireClientId();
                    String form = "client_id=" + enc(GitHubConfig.CLIENT_ID) + "&scope=" + enc("repo workflow");
                    JSONObject data = requestJson("POST", GitHubConfig.DEVICE_CODE_URL, "", form, true);
                    callback.onSuccess(new DeviceCode(
                            data.getString("device_code"),
                            data.getString("user_code"),
                            data.optString("verification_uri", GitHubConfig.VERIFICATION_URL),
                            data.optInt("expires_in", 900),
                            data.optInt("interval", 5)));
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void pollAccessToken(final DeviceCode device, final Callback<String> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    requireClientId();
                    long deadline = System.currentTimeMillis() + device.expiresIn * 1000L;
                    int waitSeconds = Math.max(5, device.interval);
                    while (System.currentTimeMillis() < deadline) {
                        Thread.sleep(waitSeconds * 1000L);
                        String form = "client_id=" + enc(GitHubConfig.CLIENT_ID)
                                + "&device_code=" + enc(device.deviceCode)
                                + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";
                        JSONObject data = requestJson("POST", GitHubConfig.ACCESS_TOKEN_URL, "", form, true);
                        if (data.has("access_token")) {
                            callback.onSuccess(data.getString("access_token"));
                            return;
                        }
                        String error = data.optString("error", "authorization_pending");
                        if ("authorization_pending".equals(error)) continue;
                        if ("slow_down".equals(error)) {
                            waitSeconds += 5;
                            continue;
                        }
                        if ("access_denied".equals(error)) throw new Exception("رفض المستخدم التفويض");
                        if ("expired_token".equals(error)) throw new Exception("انتهت صلاحية رمز الجهاز");
                        throw new Exception(data.optString("error_description", error));
                    }
                    throw new Exception("انتهت مهلة تسجيل الدخول");
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void getUser(final String token, final Callback<String> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject data = requestJson("GET", GitHubConfig.API_URL + "/user", token, null, false);
                    callback.onSuccess(data.optString("login", "GitHub user"));
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void createRepository(final String token, final String name, final boolean isPrivate, final Callback<Repository> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("name", name);
                    body.put("description", "Flutter project created by KLENCOD IDE");
                    body.put("private", isPrivate);
                    body.put("auto_init", true);
                    JSONObject data = requestJson("POST", GitHubConfig.API_URL + "/user/repos", token, body.toString(), false);
                    JSONObject owner = data.optJSONObject("owner");
                    callback.onSuccess(new Repository(
                            owner == null ? "" : owner.optString("login", ""),
                            data.optString("name", name),
                            data.optString("html_url", ""),
                            data.optString("default_branch", "main")));
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void getRepository(final String token, final String owner, final String name, final Callback<Repository> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(name);
                    JSONObject data = requestJson("GET", url, token, null, false);
                    JSONObject repositoryOwner = data.optJSONObject("owner");
                    callback.onSuccess(new Repository(
                            repositoryOwner == null ? owner : repositoryOwner.optString("login", owner),
                            data.optString("name", name),
                            data.optString("html_url", ""),
                            data.optString("default_branch", "main")));
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void uploadFile(final String token, final String owner, final String repo, final String branch, final String path, final String content, final String commitMessage, final Callback<Boolean> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("message", commitMessage);
                    body.put("content", Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                    body.put("branch", branch);
                    String currentSha = existingFileSha(token, owner, repo, branch, path);
                    if (!currentSha.isEmpty()) body.put("sha", currentSha);
                    requestJson("PUT", contentUrl(owner, repo, path), token, body.toString(), false);
                    callback.onSuccess(Boolean.TRUE);
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void uploadBytes(final String token, final String owner, final String repo, final String branch, final String path, final byte[] content, final String commitMessage, final Callback<Boolean> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("message", commitMessage);
                    body.put("content", Base64.encodeToString(content == null ? new byte[0] : content, Base64.NO_WRAP));
                    body.put("branch", branch);
                    String currentSha = existingFileSha(token, owner, repo, branch, path);
                    if (!currentSha.isEmpty()) body.put("sha", currentSha);
                    requestJson("PUT", contentUrl(owner, repo, path), token, body.toString(), false);
                    callback.onSuccess(Boolean.TRUE);
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void triggerBuild(final String token, final String owner, final String repo, final String branch, final Callback<Boolean> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("ref", branch);
                    requestJson("POST", GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/actions/workflows/" + GitHubConfig.WORKFLOW_FILE + "/dispatches", token, body.toString(), false);
                    callback.onSuccess(Boolean.TRUE);
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void latestRun(final String token, final String owner, final String repo, final long notBeforeMs, final Callback<Run> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/actions/runs?event=workflow_dispatch&per_page=20";
                    JSONObject data = requestJson("GET", url, token, null, false);
                    JSONArray runs = data.optJSONArray("workflow_runs");
                    if (runs == null || runs.length() == 0) throw new Exception("لا توجد عملية بناء بعد");
                    for (int i = 0; i < runs.length(); i++) {
                        JSONObject item = runs.getJSONObject(i);
                        long createdAt = parseIsoTime(item.optString("created_at", ""));
                        if (notBeforeMs <= 0L || createdAt >= notBeforeMs - 15000L) {
                            callback.onSuccess(new Run(
                                    item.optLong("id"),
                                    item.optString("status"),
                                    item.optString("conclusion"),
                                    item.optString("html_url"),
                                    createdAt,
                                    item.optString("head_sha", "")));
                            return;
                        }
                    }
                    throw new Exception("تم تشغيل Workflow لكن لم تظهر العملية الجديدة بعد");
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void getRun(final String token, final String owner, final String repo, final long runId, final Callback<Run> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/actions/runs/" + runId;
                    JSONObject item = requestJson("GET", url, token, null, false);
                    callback.onSuccess(new Run(
                            item.optLong("id", runId),
                            item.optString("status"),
                            item.optString("conclusion"),
                            item.optString("html_url"),
                            parseIsoTime(item.optString("created_at", "")),
                            item.optString("head_sha", "")));
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void getRunJobs(final String token, final String owner, final String repo,
                                   final long runId, final Callback<List<Job>> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/"
                            + encPath(repo) + "/actions/runs/" + runId + "/jobs";
                    JSONObject data = requestJson("GET", url, token, null, false);
                    JSONArray jobsArray = data.optJSONArray("jobs");
                    List<Job> jobs = new ArrayList<Job>();
                    if (jobsArray != null) {
                        for (int i = 0; i < jobsArray.length(); i++) {
                            JSONObject jobJson = jobsArray.optJSONObject(i);
                            if (jobJson == null) continue;

                            List<Step> steps = new ArrayList<Step>();
                            JSONArray stepsArray = jobJson.optJSONArray("steps");
                            if (stepsArray != null) {
                                for (int j = 0; j < stepsArray.length(); j++) {
                                    JSONObject stepJson = stepsArray.optJSONObject(j);
                                    if (stepJson == null) continue;
                                    steps.add(new Step(
                                            stepJson.optInt("number", j + 1),
                                            stepJson.optString("name", ""),
                                            stepJson.optString("status", ""),
                                            stepJson.optString("conclusion", ""),
                                            parseIsoTime(stepJson.optString("started_at", "")),
                                            parseIsoTime(stepJson.optString("completed_at", ""))
                                    ));
                                }
                            }

                            jobs.add(new Job(
                                    jobJson.optLong("id", 0L),
                                    jobJson.optString("name", ""),
                                    jobJson.optString("status", ""),
                                    jobJson.optString("conclusion", ""),
                                    parseIsoTime(jobJson.optString("started_at", "")),
                                    parseIsoTime(jobJson.optString("completed_at", "")),
                                    steps
                            ));
                        }
                    }
                    callback.onSuccess(jobs);
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    /**
     * ✅ v1.6: يجلب annotations من GitHub للـ run محدد.
     * annotations تحتوي رسائل GitHub الرسمية (مثل: exceeded minutes, payment failed...).
     * المسار: run → head_sha → check-runs → annotations
     */
    public static void getRunAnnotations(final String token, final String owner, final String repo,
                                          final long runId, final Callback<List<String>> callback) {
        run(new Runnable() {
            @Override public void run() {
                List<String> messages = new ArrayList<String>();
                try {
                    // 1) اجلب الـ run للحصول على head_sha
                    String runUrl = GitHubConfig.API_URL + "/repos/" + encPath(owner)
                            + "/" + encPath(repo) + "/actions/runs/" + runId;
                    JSONObject runJson = requestJson("GET", runUrl, token, null, false);
                    String headSha = runJson.optString("head_sha", "");
                    if (headSha.isEmpty()) {
                        callback.onSuccess(messages);
                        return;
                    }

                    // 2) اجلب check-runs للـ commit
                    String checksUrl = GitHubConfig.API_URL + "/repos/" + encPath(owner)
                            + "/" + encPath(repo) + "/commits/" + headSha + "/check-runs";
                    JSONObject checksJson = requestJson("GET", checksUrl, token, null, false);
                    JSONArray checkRuns = checksJson.optJSONArray("check_runs");
                    if (checkRuns == null || checkRuns.length() == 0) {
                        callback.onSuccess(messages);
                        return;
                    }

                    // 3) لكل check_run: اجلب annotations
                    for (int i = 0; i < checkRuns.length(); i++) {
                        JSONObject check = checkRuns.optJSONObject(i);
                        if (check == null) continue;
                        long checkId = check.optLong("id", 0L);
                        if (checkId <= 0L) continue;

                        String annotUrl = GitHubConfig.API_URL + "/repos/" + encPath(owner)
                                + "/" + encPath(repo) + "/check-runs/" + checkId + "/annotations";
                        try {
                            JSONArray annotations = requestJsonArray("GET", annotUrl, token, null, false);
                            if (annotations != null) {
                                for (int j = 0; j < annotations.length(); j++) {
                                    JSONObject annot = annotations.optJSONObject(j);
                                    if (annot == null) continue;
                                    String msg = annot.optString("message", "");
                                    String title = annot.optString("title", "");
                                    String combined = msg;
                                    if (title != null && !title.isEmpty() && !msg.startsWith(title)) {
                                        combined = title + "\n" + msg;
                                    }
                                    if (combined != null && !combined.trim().isEmpty()) {
                                        // تفادي التكرار
                                        boolean exists = false;
                                        for (String existing : messages) {
                                            if (existing.equals(combined)) { exists = true; break; }
                                        }
                                        if (!exists) messages.add(combined.trim());
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                            // نتجاهل الأخطاء الفردية ونكمل
                        }
                    }
                    callback.onSuccess(messages);
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void downloadJobLogs(final String token, final String owner, final String repo,
                                        final long jobId, final Callback<String> callback) {
        run(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/"
                            + encPath(repo) + "/actions/jobs/" + jobId + "/logs";
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(120000);
                    connection.setReadTimeout(120000);
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setRequestProperty("Accept", "application/vnd.github+json, text/plain, application/zip");
                    connection.setRequestProperty("X-GitHub-Api-Version", GitHubConfig.API_VERSION);
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) {
                        throw new Exception("تعذر تنزيل سجل الخطوة، HTTP " + status);
                    }

                    ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
                    InputStream input = new BufferedInputStream(connection.getInputStream());
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) rawOutput.write(buffer, 0, count);
                    input.close();

                    byte[] payload = rawOutput.toByteArray();
                    String contentType = connection.getHeaderField("Content-Type");
                    String logs;
                    if (isZipPayload(payload) || (contentType != null && contentType.toLowerCase().contains("zip"))) {
                        logs = unzipJobLogs(payload);
                    } else {
                        logs = new String(payload, StandardCharsets.UTF_8);
                    }
                    callback.onSuccess(logs);
                } catch (Exception error) {
                    callback.onError(message(error));
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static String unzipJobLogs(byte[] payload) throws Exception {
        StringBuilder logs = new StringBuilder();
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(payload)));
        try {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream entryOutput = new ByteArrayOutputStream();
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        entryOutput.write(buffer, 0, count);
                    }
                    String content = new String(entryOutput.toByteArray(), StandardCharsets.UTF_8);
                    if (content.trim().length() > 0) {
                        if (logs.length() > 0) logs.append("\n");
                        logs.append(content);
                    }
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        return logs.toString();
    }

    public static void downloadRunLogs(final String token, final String owner, final String repo, final long runId, final Callback<String> callback) {
        run(new Runnable() {
            @Override public void run() {
                HttpURLConnection connection = null;
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/actions/runs/" + runId + "/logs";
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(120000);
                    connection.setReadTimeout(120000);
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setRequestProperty("Accept", "application/vnd.github+json, application/zip, text/plain");
                    connection.setRequestProperty("X-GitHub-Api-Version", GitHubConfig.API_VERSION);
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) throw new Exception("تعذر تنزيل سجل البناء، HTTP " + status);

                    ByteArrayOutputStream rawOutput = new ByteArrayOutputStream();
                    InputStream input = null;
                    try {
                        input = new BufferedInputStream(connection.getInputStream());
                        byte[] buffer = new byte[8192];
                        int count;
                        while ((count = input.read(buffer)) != -1) rawOutput.write(buffer, 0, count);
                    } finally {
                        if (input != null) input.close();
                    }

                    byte[] payload = rawOutput.toByteArray();
                    if (payload.length == 0) throw new Exception("أعاد GitHub سجلًا فارغًا لهذه العملية");

                    String contentType = connection.getHeaderField("Content-Type");
                    String logs;
                    if (isZipPayload(payload) || (contentType != null && contentType.toLowerCase().contains("zip"))) {
                        logs = unzipRunLogs(payload);
                    } else {
                        logs = new String(payload, StandardCharsets.UTF_8);
                    }

                    if (logs == null || logs.trim().isEmpty()) {
                        throw new Exception("تم تنزيل أرشيف سجل GitHub لكنه لا يحتوي على ملفات نصية");
                    }
                    callback.onSuccess(logs);
                } catch (Exception error) {
                    callback.onError(message(error));
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }

    private static boolean isZipPayload(byte[] payload) {
        return payload.length >= 4
                && payload[0] == 'P'
                && payload[1] == 'K'
                && (payload[2] == 3 || payload[2] == 5 || payload[2] == 7)
                && (payload[3] == 4 || payload[3] == 6 || payload[3] == 8);
    }

    private static String unzipRunLogs(byte[] payload) throws Exception {
        StringBuilder logs = new StringBuilder();
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(payload)));
        try {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream entryOutput = new ByteArrayOutputStream();
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        entryOutput.write(buffer, 0, count);
                    }
                    byte[] entryBytes = entryOutput.toByteArray();
                    String content = new String(entryBytes, StandardCharsets.UTF_8);
                    if (content.trim().length() > 0) {
                        if (logs.length() > 0) logs.append("\n\n");
                        logs.append("===== ").append(entry.getName()).append(" =====\n");
                        logs.append(content);
                    }
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
        if (logs.length() == 0) throw new Exception("أرشيف GitHub لا يحتوي على ملفات سجل نصية");
        return logs.toString().trim();
    }

    public static void latestArtifact(final String token, final String owner, final String repo, final Callback<Artifact> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/actions/artifacts?name=" + encPath(GitHubConfig.ARTIFACT_NAME) + "&per_page=1";
                    JSONObject data = requestJson("GET", url, token, null, false);
                    JSONArray artifacts = data.optJSONArray("artifacts");
                    if (artifacts == null || artifacts.length() == 0) throw new Exception("لم يتم العثور على APK");
                    JSONObject item = artifacts.getJSONObject(0);
                    callback.onSuccess(new Artifact(item.optLong("id"), item.optString("name"), item.optLong("size_in_bytes"), item.optBoolean("expired")));
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    public static void downloadArtifact(final String token, final String owner, final String repo, final long artifactId, final File destination, final Callback<File> callback) {
        run(new Runnable() {
            @Override public void run() {
                try {
                    String url = GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/actions/artifacts/" + artifactId + "/zip";
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(120000);
                    connection.setReadTimeout(120000);
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                    connection.setRequestProperty("Accept", "application/vnd.github+json");
                    connection.setRequestProperty("X-GitHub-Api-Version", GitHubConfig.API_VERSION);
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) throw new Exception("تعذر تنزيل artifact، HTTP " + status);
                    ZipInputStream zip = new ZipInputStream(new BufferedInputStream(connection.getInputStream()));
                    ZipEntry entry;
                    boolean extracted = false;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".apk")) {
                            File parent = destination.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();
                            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination));
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                            output.flush();
                            output.close();
                            extracted = true;
                            break;
                        }
                        zip.closeEntry();
                    }
                    zip.close();
                    connection.disconnect();
                    if (!extracted) throw new Exception("لم يوجد ملف APK داخل artifact");
                    callback.onSuccess(destination);
                } catch (Exception error) {
                    callback.onError(message(error));
                }
            }
        });
    }

    private static String existingFileSha(String token, String owner, String repo, String branch, String path) throws Exception {
        String address = contentUrl(owner, repo, path) + "?ref=" + enc(branch);
        try {
            JSONObject data = requestJson("GET", address, token, null, false);
            return data.optString("sha", "");
        } catch (Exception error) {
            String detail = message(error);
            if (detail.contains("GitHub HTTP 404")) return "";
            throw error;
        }
    }

    private static String contentUrl(String owner, String repo, String path) {
        return GitHubConfig.API_URL + "/repos/" + encPath(owner) + "/" + encPath(repo) + "/contents/" + path.replace(" ", "%20");
    }

    private static void requireClientId() throws Exception {
        if (GitHubConfig.CLIENT_ID.startsWith("REPLACE_")) {
            throw new Exception("ضع GitHub OAuth Client ID في GitHubConfig.java أولًا");
        }
    }

    private static void run(final Runnable task) {
        new Thread(task, "klencod-github").start();
    }

    private static JSONObject requestJson(String method, String address, String token, String body, boolean form) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(120000);
        connection.setReadTimeout(120000);
        connection.setDoInput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-GitHub-Api-Version", GitHubConfig.API_VERSION);
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(body);
            writer.flush();
            writer.close();
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String detail = response;
            try { detail = new JSONObject(response).optString("message", response); } catch (Exception ignored) {}
            throw new Exception("GitHub HTTP " + status + ": " + detail);
        }
        if (response == null || response.trim().isEmpty()) return new JSONObject();
        return new JSONObject(response);
    }

    /** ✅ v1.6: نسخة من requestJson لكن تعيد JSONArray (لـ annotations). */
    private static JSONArray requestJsonArray(String method, String address, String token, String body, boolean form) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(120000);
        connection.setReadTimeout(120000);
        connection.setDoInput(true);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", GitHubConfig.API_VERSION);
        if (token != null && !token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(body);
            writer.flush();
            writer.close();
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String detail = response;
            try { detail = new JSONObject(response).optString("message", response); } catch (Exception ignored) {}
            throw new Exception("GitHub HTTP " + status + ": " + detail);
        }
        if (response == null || response.trim().isEmpty()) return new JSONArray();
        return new JSONArray(response);
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) result.append(line);
        reader.close();
        return result.toString();
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String encPath(String value) {
        return value.replace(" ", "%20").replace("/", "%2F");
    }

    private static long parseIsoTime(String iso) {
        if (iso == null || iso.trim().isEmpty()) return 0L;
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                    iso.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US);
            fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date d = fmt.parse(iso.trim());
            return d == null ? 0L : d.getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String message(Exception error) {
        if (error == null) return "خطأ غير معروف";

        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(error.getClass().getName()).append("\n\n");

        String msg = error.getMessage();
        sb.append("Message: ");
        sb.append(msg == null || msg.trim().isEmpty() ? "(no message)" : msg);

        Throwable cause = error.getCause();
        int depth = 1;
        while (cause != null && depth <= 5) {
            sb.append("\n\nCaused by [").append(depth).append("]: ")
              .append(cause.getClass().getName());
            sb.append("\nMessage: ");
            String causeMsg = cause.getMessage();
            sb.append(causeMsg == null || causeMsg.trim().isEmpty() ? "(no message)" : causeMsg);
            cause = cause.getCause();
            depth++;
        }

        StackTraceElement[] stack = error.getStackTrace();
        if (stack != null && stack.length > 0) {
            sb.append("\n\nStack trace (top 5):");
            int count = Math.min(5, stack.length);
            for (int i = 0; i < count; i++) {
                sb.append("\n  at ").append(stack[i].toString());
            }
        }

        return sb.toString();
    }
}