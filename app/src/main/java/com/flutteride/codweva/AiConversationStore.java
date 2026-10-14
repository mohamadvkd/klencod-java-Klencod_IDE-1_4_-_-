package com.flutteride.codweva;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** تخزين محادثة مستقلة لكل مشروع داخل مساحة التطبيق الخاصة. */
public final class AiConversationStore {
    private final File directory;

    public AiConversationStore(Context context) {
        directory = new File(context.getFilesDir(), "ai_conversations");
        if (!directory.exists()) directory.mkdirs();
    }

    public List<AiMessage> load(String projectPath) {
        List<AiMessage> result = new ArrayList<>();
        if (projectPath == null || projectPath.trim().isEmpty()) return result;
        File file = conversationFile(projectPath);
        if (!file.exists()) return result;
        try {
            String json = read(file);
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String role = item.optString("role", "assistant");
                String content = item.optString("content", "");
                if (!content.isEmpty()) result.add(new AiMessage(role, content));
            }
        } catch (Exception ignored) { }
        return result;
    }

    public void save(String projectPath, List<AiMessage> messages) {
        if (projectPath == null || projectPath.trim().isEmpty() || messages == null) return;
        try {
            JSONArray array = new JSONArray();
            for (AiMessage message : messages) {
                if (message == null || "system".equals(message.role)) continue;
                JSONObject item = new JSONObject();
                item.put("role", message.role);
                item.put("content", message.content);
                array.put(item);
            }
            write(conversationFile(projectPath), array.toString());
        } catch (Exception ignored) { }
    }

    public void clear(String projectPath) {
        File file = conversationFile(projectPath);
        if (file.exists()) file.delete();
    }

    private File conversationFile(String projectPath) {
        return new File(directory, sha256(projectPath) + ".json");
    }

    private String read(File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        } finally {
            reader.close();
        }
        return builder.toString();
    }

    private void write(File file, String content) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        try {
            writer.write(content);
            writer.flush();
        } finally {
            writer.close();
            output.close();
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
