package com.flutteride.codweva;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class AiHttpClient {
    private AiHttpClient() { }

    /**
     * نسخة قديمة — تستخدم timeouts افتراضية.
     */
    static JSONObject postJson(String endpoint, String apiKey, JSONObject body, boolean keyInQuery) throws Exception {
        return postJson(endpoint, apiKey, body, keyInQuery, 30000, 120000);
    }

    /**
     * نسخة موسّعة — timeouts قابلة للتخصيص.
     */
    static JSONObject postJson(String endpoint, String apiKey, JSONObject body,
                               boolean keyInQuery,
                               int connectTimeoutMs, int readTimeoutMs) throws Exception {
        String finalEndpoint = endpoint;
        if (keyInQuery) {
            finalEndpoint += (endpoint.contains("?") ? "&" : "?") + "key=" + java.net.URLEncoder.encode(apiKey, "UTF-8");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(finalEndpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (!keyInQuery) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream output = connection.getOutputStream();
        output.write(payload);
        output.flush();
        output.close();

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readFully(stream);
        connection.disconnect();

        if (status < 200 || status >= 300) {
            throw new AiApiException(status, extractError(response));
        }
        return new JSONObject(response);
    }

    private static String readFully(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) builder.append(line);
        reader.close();
        return builder.toString();
    }

    private static String extractError(String response) {
        if (response == null || response.trim().isEmpty()) return "استجابة فارغة من مزود الذكاء الاصطناعي";
        try {
            JSONObject json = new JSONObject(response);
            if (json.has("error")) {
                Object error = json.get("error");
                if (error instanceof JSONObject) {
                    return ((JSONObject) error).optString("message", "فشل طلب الذكاء الاصطناعي");
                }
                return String.valueOf(error);
            }
            return json.optString("message", "فشل طلب الذكاء الاصطناعي");
        } catch (Exception ignored) {
            return response.length() > 500 ? response.substring(0, 500) : response;
        }
    }

    static final class AiApiException extends Exception {
        final int statusCode;

        AiApiException(int statusCode, String message) {
            super("HTTP " + statusCode + ": " + message);
            this.statusCode = statusCode;
        }
    }
}