package com.powertrack.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ApiClient {

    private static String postJson(String urlStr, String jsonBody, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        conn.setDoOutput(true);

        byte[] outputBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(outputBytes);
            os.flush();
        }

        int statusCode = conn.getResponseCode();
        InputStream is = (statusCode >= 200 && statusCode < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();
        }

        if (statusCode < 200 || statusCode >= 300) {
            String rawErr = sb.toString();
            String message = "HTTP " + statusCode;
            try {
                JSONObject errObj = new JSONObject(rawErr);
                if (errObj.has("error")) {
                    message = errObj.getString("error");
                }
            } catch (Exception ignored) {
                if (!rawErr.isEmpty()) {
                    message += ": " + rawErr;
                }
            }
            throw new Exception(message);
        }
        return sb.toString();
    }

    public static JSONObject register(String baseUrl, String email, String password) throws Exception {
        String endpoint = baseUrl.replaceAll("/+$", "") + "/api/auth/register";
        JSONObject req = new JSONObject();
        req.put("email", email);
        req.put("password", password);
        String res = postJson(endpoint, req.toString(), null);
        return new JSONObject(res);
    }

    public static JSONObject login(String baseUrl, String email, String password) throws Exception {
        String endpoint = baseUrl.replaceAll("/+$", "") + "/api/auth/login";
        JSONObject req = new JSONObject();
        req.put("email", email);
        req.put("password", password);
        String res = postJson(endpoint, req.toString(), null);
        return new JSONObject(res);
    }

    public static JSONObject registerDevice(String baseUrl, String token, String deviceName, String deviceId) throws Exception {
        String endpoint = baseUrl.replaceAll("/+$", "") + "/api/devices/register";
        JSONObject req = new JSONObject();
        req.put("device_name", deviceName);
        req.put("platform", "android");
        req.put("device_id", deviceId);
        String res = postJson(endpoint, req.toString(), token);
        return new JSONObject(res);
    }

    public static JSONObject uploadLocations(String baseUrl, String token, String deviceId, JSONArray locations) throws Exception {
        String endpoint = baseUrl.replaceAll("/+$", "") + "/api/devices/" + deviceId + "/locations";
        JSONObject req = new JSONObject();
        req.put("locations", locations);
        String res = postJson(endpoint, req.toString(), token);
        return new JSONObject(res);
    }
}
