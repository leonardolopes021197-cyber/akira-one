package com.akira.one;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    public interface JsonCallback { void onSuccess(JSONObject json); void onError(Exception error); }
    private static final ExecutorService EXEC = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private ApiClient() {}

    public static void register(Context c, JsonCallback cb) {
        String root = Prefs.panelUrl(c);
        if (root.isBlank()) { cb.onError(new IllegalStateException("Configure a URL do painel.")); return; }
        JSONObject body = new JSONObject();
        try {
            body.put("device_id", DeviceIdentity.get(c));
            body.put("device_name", Build.MANUFACTURER + " " + Build.MODEL);
            body.put("model", Build.MODEL);
            body.put("app_version", AppVersion.name(c));
        } catch (Exception ignored) {}
        request(root + "/api/register.php", "POST", body, null, cb);
    }

    public static void fetchConfig(Context c, JsonCallback cb) {
        String root = Prefs.panelUrl(c);
        String token = Prefs.deviceToken(c);
        if (root.isBlank() || token.isBlank()) { cb.onError(new IllegalStateException("Dispositivo ainda não registrado.")); return; }
        String url = root + "/api/config.php?device_id=" + urlEncode(DeviceIdentity.get(c));
        request(url, "GET", null, token, cb);
    }


    public static void health(Context c, JsonCallback cb) {
        String root = Prefs.panelUrl(c);
        if (root.isBlank()) { cb.onError(new IllegalStateException("Informe a URL do painel.")); return; }
        request(root + "/api/health.php", "GET", null, null, cb);
    }

    private static void request(String url, String method, JSONObject body, String token, JsonCallback cb) {
        EXEC.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(20000); conn.setRequestMethod(method);
                conn.setRequestProperty("Accept", "application/json");
                if (token != null && !token.isBlank()) conn.setRequestProperty("X-Device-Token", token);
                if (body != null) {
                    conn.setDoOutput(true); conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
                }
                int status = conn.getResponseCode();
                InputStream is = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
                String text = readAll(is);
                JSONObject json = new JSONObject(text.isBlank() ? "{}" : text);
                if (status < 200 || status >= 300) throw new IllegalStateException(json.optString("error", "HTTP " + status));
                MAIN.post(() -> cb.onSuccess(json));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(e));
            } finally { if (conn != null) conn.disconnect(); }
        });
    }

    static String getText(String url, int timeoutMs) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(timeoutMs); conn.setReadTimeout(timeoutMs); conn.setRequestProperty("Accept","application/json");
        int status=conn.getResponseCode(); String text=readAll(status>=200&&status<300?conn.getInputStream():conn.getErrorStream()); conn.disconnect();
        if(status<200||status>=300) throw new IllegalStateException("HTTP "+status+" ao acessar servidor");
        return text;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
    public static String urlEncode(String s) { try { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8.toString()); } catch(Exception e){ return s; } }
}
