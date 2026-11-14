package com.akira.one;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Valida as credenciais contra os servidores Akira embutidos no aplicativo.
 * O usuário não precisa escolher nem visualizar um DNS: o primeiro servidor
 * que autenticar com sucesso passa a ser usado pela sessão.
 */
public final class ServerPool {
    public interface LoginCallback {
        void onSuccess(String serverUrl);
        void onError(String message);
    }

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ServerPool() {}

    public static void login(Context context, String username, String password, LoginCallback callback) {
        final Context app = context.getApplicationContext();
        final String user = username == null ? "" : username.trim();
        final String pass = password == null ? "" : password;

        if (user.isBlank() || pass.isBlank()) {
            callback.onError("Informe usuário e senha.");
            return;
        }

        final String[] servers = Prefs.embeddedServers();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicBoolean anyReachableServer = new AtomicBoolean(false);
        final AtomicInteger remaining = new AtomicInteger(servers.length);

        for (String baseUrl : servers) {
            EXEC.execute(() -> {
                try {
                    String url = baseUrl + "/player_api.php?username=" + ApiClient.urlEncode(user)
                            + "&password=" + ApiClient.urlEncode(pass);
                    String body = ApiClient.getText(url, 10000);
                    anyReachableServer.set(true);

                    JSONObject root = new JSONObject(body == null || body.isBlank() ? "{}" : body);
                    if (isAuthenticated(root) && completed.compareAndSet(false, true)) {
                        Prefs.saveActivation(app, baseUrl, user, pass, "Akira TV");
                        Prefs.setLastWorkingServer(app, baseUrl);
                        saveAccountInfo(app, root);
                        savePlaybackHints(app, baseUrl, root);
                        MAIN.post(() -> callback.onSuccess(baseUrl));
                        return;
                    }
                } catch (Exception ignored) {
                    // O próximo servidor será testado automaticamente.
                }

                if (remaining.decrementAndGet() == 0 && completed.compareAndSet(false, true)) {
                    String message = anyReachableServer.get()
                            ? "Usuário ou senha inválidos. Confira os dados e tente novamente."
                            : "Não foi possível conectar aos servidores Akira. Verifique sua internet e tente novamente.";
                    MAIN.post(() -> callback.onError(message));
                }
            });
        }
    }

    private static void saveAccountInfo(Context app, JSONObject root) {
        try {
            JSONObject userInfo = root.optJSONObject("user_info");
            Prefs.setExpiryEpoch(app, userInfo == null ? "" : userInfo.optString("exp_date", ""));
        } catch (Exception ignored) {
            Prefs.setExpiryEpoch(app, "");
        }
    }

    /** Atualiza silenciosamente o vencimento ao entrar na tela inicial. */
    public static void refreshExpiry(Context context, Runnable onComplete) {
        final Context app = context.getApplicationContext();
        final String baseUrl = Prefs.serverUrl(app);
        final String user = Prefs.username(app);
        final String pass = Prefs.password(app);
        if (baseUrl.isBlank() || user.isBlank() || pass.isBlank()) {
            if (onComplete != null) MAIN.post(onComplete);
            return;
        }

        EXEC.execute(() -> {
            try {
                String url = baseUrl + "/player_api.php?username=" + ApiClient.urlEncode(user)
                        + "&password=" + ApiClient.urlEncode(pass);
                String body = ApiClient.getText(url, 10000);
                JSONObject root = new JSONObject(body == null || body.isBlank() ? "{}" : body);
                if (isAuthenticated(root)) {
                    saveAccountInfo(app, root);
                    savePlaybackHints(app, baseUrl, root);
                }
            } catch (Exception ignored) {
                // Mantém o último vencimento conhecido quando a rede estiver indisponível.
            }
            if (onComplete != null) MAIN.post(onComplete);
        });
    }

    private static void savePlaybackHints(Context app, String loginBase, JSONObject root) {
        try {
            JSONObject userInfo = root.optJSONObject("user_info");
            JSONArray formats = userInfo == null ? null : userInfo.optJSONArray("allowed_output_formats");
            if (formats != null) {
                Set<String> out = new LinkedHashSet<>();
                for (int i = 0; i < formats.length(); i++) {
                    String f = formats.optString(i, "").trim().toLowerCase();
                    if (!f.isBlank()) out.add(f);
                }
                Prefs.setLiveFormats(app, String.join(",", out));
            }

            JSONObject serverInfo = root.optJSONObject("server_info");
            String playbackBase = buildPlaybackBase(loginBase, serverInfo);
            Prefs.setPlaybackBase(app, playbackBase);
        } catch (Exception ignored) {
            Prefs.setPlaybackBase(app, "");
        }
    }

    private static String buildPlaybackBase(String loginBase, JSONObject serverInfo) {
        if (serverInfo == null) return "";
        try {
            String host = serverInfo.optString("url", "").trim();
            if (host.isBlank()) return "";
            if (host.startsWith("http://") || host.startsWith("https://")) {
                URI uri = URI.create(host);
                if (uri.getHost() != null) host = uri.getHost();
            }

            String protocol = serverInfo.optString("server_protocol", "").trim().toLowerCase();
            if (!"https".equals(protocol) && !"http".equals(protocol)) {
                protocol = loginBase != null && loginBase.toLowerCase().startsWith("https://") ? "https" : "http";
            }

            String port = "https".equals(protocol)
                    ? serverInfo.optString("https_port", "").trim()
                    : serverInfo.optString("port", "").trim();

            boolean defaultPort = port.isBlank()
                    || ("http".equals(protocol) && "80".equals(port))
                    || ("https".equals(protocol) && "443".equals(port));
            return protocol + "://" + host + (defaultPort ? "" : ":" + port);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean isAuthenticated(JSONObject root) {
        JSONObject userInfo = root.optJSONObject("user_info");
        if (userInfo == null) return false;

        Object auth = userInfo.opt("auth");
        boolean authenticated = false;
        if (auth instanceof Number) authenticated = ((Number) auth).intValue() == 1;
        else if (auth != null) {
            String value = String.valueOf(auth).trim();
            authenticated = "1".equals(value) || "true".equalsIgnoreCase(value);
        }
        if (!authenticated) return false;

        String status = userInfo.optString("status", "").trim();
        return status.isBlank() || "active".equalsIgnoreCase(status);
    }
}
