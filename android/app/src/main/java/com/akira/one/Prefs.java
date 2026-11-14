package com.akira.one;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Prefs {
    private static final String NAME = "akira_prefs";
    private static final String RECENTS_KEY = "watch_recents_json";
    private static final String PLAYBACK_KEY = "playback_progress_json";
    private static final String SERIES_PROGRESS_KEY = "series_progress_json";
    private static final String FAVORITES_KEY = "favorites_by_account_json";

    // Quatro servidores/DNS embutidos no APK. O usuário não precisa digitá-los.
    private static final String[] EMBEDDED_SERVERS = new String[]{
            "http://akiratv.shop",
            "http://streamix.site",
            "http://atenatop.shop",
            "http://maxatena.online"
    };

    public static final String DEFAULT_DNS = EMBEDDED_SERVERS[0];

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String[] embeddedServers() {
        return EMBEDDED_SERVERS.clone();
    }

    public static boolean isEmbeddedServer(String url) {
        String normalized = trim(url);
        for (String server : EMBEDDED_SERVERS) {
            if (server.equalsIgnoreCase(normalized)) return true;
        }
        return false;
    }

    public static String panelUrl(Context c) { return p(c).getString("panel_url", ""); }
    public static void setPanelUrl(Context c, String v) { p(c).edit().putString("panel_url", trim(v)).apply(); }
    public static String deviceToken(Context c) { return p(c).getString("device_token", ""); }
    public static void setDeviceToken(Context c, String v) { p(c).edit().putString("device_token", v).apply(); }

    public static String lastWorkingServer(Context c) {
        return p(c).getString("last_working_server", DEFAULT_DNS);
    }

    public static void setLastWorkingServer(Context c, String v) {
        p(c).edit().putString("last_working_server", trim(v)).apply();
    }

    public static String playbackBase(Context c) {
        return p(c).getString("playback_base", "");
    }

    public static void setPlaybackBase(Context c, String v) {
        p(c).edit().putString("playback_base", trim(v)).apply();
    }

    public static String liveFormats(Context c) {
        return p(c).getString("live_formats", "");
    }

    public static void setLiveFormats(Context c, String v) {
        p(c).edit().putString("live_formats", v == null ? "" : v.trim()).apply();
    }

    public static String directServerUrl(Context c) { return lastWorkingServer(c); }
    public static String directUsername(Context c) { return p(c).getString("direct_username", ""); }
    public static String directPassword(Context c) { return p(c).getString("direct_password", ""); }
    public static String directListName(Context c) { return p(c).getString("direct_list_name", "Akira TV"); }

    public static void saveDirect(Context c, String baseUrl, String username, String password, String listName) {
        String server = trim(baseUrl);
        if (server.isBlank()) server = lastWorkingServer(c);
        p(c).edit()
                .putString("direct_username", username == null ? "" : username.trim())
                .putString("direct_password", password == null ? "" : password)
                .putString("direct_list_name", listName == null || listName.trim().isBlank() ? "Akira TV" : listName.trim())
                .apply();
        saveActivation(c, server, username, password, listName);
    }

    public static void activateDirect(Context c) {
        if (!directUsername(c).isBlank() && !directPassword(c).isBlank()) {
            saveActivation(c, lastWorkingServer(c), directUsername(c), directPassword(c), directListName(c));
        }
    }

    public static void saveActivation(Context c, String baseUrl, String username, String password, String listName) {
        String server = trim(baseUrl);
        p(c).edit()
                .putString("server_url", server)
                .putString("username", username == null ? "" : username.trim())
                .putString("password", password == null ? "" : password)
                .putString("list_name", listName == null || listName.isBlank() ? "Akira TV" : listName)
                .putString("direct_username", username == null ? "" : username.trim())
                .putString("direct_password", password == null ? "" : password)
                .putString("direct_list_name", listName == null || listName.isBlank() ? "Akira TV" : listName)
                .apply();
        if (!server.isBlank()) setLastWorkingServer(c, server);
    }

    public static void clearActivation(Context c) {
        p(c).edit()
                .remove("server_url")
                .remove("username")
                .remove("password")
                .remove("list_name")
                .remove("direct_username")
                .remove("direct_password")
                .remove("direct_list_name")
                .remove("playback_base")
                .remove("live_formats")
                .remove("expiry_epoch")
                .apply();
    }

    public static String serverUrl(Context c) { return p(c).getString("server_url", ""); }
    public static String username(Context c) { return p(c).getString("username", ""); }
    public static String password(Context c) { return p(c).getString("password", ""); }
    public static String listName(Context c) { return p(c).getString("list_name", "Akira TV"); }

    /** Salva o exp_date retornado pelo Xtream (normalmente epoch em segundos). */
    public static void setExpiryEpoch(Context c, String raw) {
        String value = raw == null ? "" : raw.trim();
        if ("null".equalsIgnoreCase(value)) value = "";
        p(c).edit().putString("expiry_epoch", value).apply();
    }

    public static String expiryEpoch(Context c) {
        return p(c).getString("expiry_epoch", "");
    }

    public static String expiryDisplay(Context c) {
        String raw = expiryEpoch(c);
        if (raw == null || raw.isBlank()) return "Não informado";
        try {
            long value = Long.parseLong(raw);
            if (value <= 0L) return "Sem vencimento";
            long millis = value > 100000000000L ? value : value * 1000L;
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", new Locale("pt", "BR"));
            return format.format(new Date(millis));
        } catch (Exception ignored) {
            return raw;
        }
    }

    public static boolean hasActivation(Context c) {
        return !serverUrl(c).isBlank() && !username(c).isBlank() && !password(c).isBlank();
    }

    public static boolean directMode(Context c) { return panelUrl(c).isBlank(); }

    public static void saveLists(Context c, JSONArray a) {
        p(c).edit().putString("lists_json", a == null ? "[]" : a.toString()).apply();
    }

    public static JSONArray listsJson(Context c) {
        try { return new JSONArray(p(c).getString("lists_json", "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static List<ListProfile> lists(Context c) {
        List<ListProfile> out = new ArrayList<>();
        JSONArray a = listsJson(c);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(ListProfile.fromJson(o));
        }
        return out;
    }

    public static void selectList(Context c, ListProfile p) {
        if (p != null) saveActivation(c, p.baseUrl, p.username, p.password, p.name);
    }

    public static boolean isFavorite(Context c, ContentItem item) { return favoritesObject(c).has(item.key()); }

    public static boolean toggleFavorite(Context c, ContentItem item) {
        JSONObject root = favoritesObject(c);
        boolean now;
        try {
            if (root.has(item.key())) { root.remove(item.key()); now = false; }
            else { root.put(item.key(), item.toJson()); now = true; }
            saveScopedObject(c, FAVORITES_KEY, root);
        } catch (Exception e) { now = false; }
        return now;
    }

    public static List<ContentItem> favorites(Context c) {
        List<ContentItem> out = new ArrayList<>();
        JSONObject root = favoritesObject(c);
        JSONArray names = root.names();
        if (names == null) return out;
        for (int i = 0; i < names.length(); i++) {
            JSONObject o = root.optJSONObject(names.optString(i));
            if (o != null) out.add(ContentItem.fromJson(o));
        }
        return out;
    }

    /** Favoritos separados por usuário (antes eram um único conjunto para todas as contas). */
    private static JSONObject favoritesObject(Context c) {
        migrateLegacyFavorites(c);
        return scopedObject(c, FAVORITES_KEY);
    }

    /** Os favoritos antigos ficam com a conta que estava ativa na primeira leitura. */
    private static void migrateLegacyFavorites(Context c) {
        String legacy = p(c).getString("favorites_json", null);
        if (legacy == null) return;
        try {
            if (!p(c).contains(FAVORITES_KEY)) {
                JSONObject root = new JSONObject();
                root.put(accountKey(c), new JSONObject(legacy));
                p(c).edit().putString(FAVORITES_KEY, root.toString()).remove("favorites_json").apply();
            } else {
                p(c).edit().remove("favorites_json").apply();
            }
        } catch (Exception e) {
            p(c).edit().remove("favorites_json").apply();
        }
    }

    // ---------------- Histórico de assistidos ----------------

    public static void markRecent(Context c, ContentItem item) {
        if (item == null || item.id.isBlank()) return;
        if (!("live".equals(item.type) || "movie".equals(item.type) || "series".equals(item.type))) return;
        JSONObject scoped = scopedObject(c, RECENTS_KEY);
        try {
            JSONObject record = new JSONObject();
            record.put("item", item.toJson());
            record.put("watched_at", System.currentTimeMillis());
            scoped.put(item.key(), record);
            trimRecentObject(scoped, 80);
            saveScopedObject(c, RECENTS_KEY, scoped);
        } catch (Exception ignored) {}
    }

    public static void markRecentSeries(Context c, String seriesId, String seriesName, String cover, String categoryId) {
        if (seriesId == null || seriesId.isBlank()) return;
        ContentItem series = new ContentItem(seriesId, seriesName, cover, "series", "", "Série", categoryId == null ? "" : categoryId, "");
        markRecent(c, series);
    }

    public static List<ContentItem> recent(Context c, String type) {
        List<RecentRecord> records = new ArrayList<>();
        JSONObject scoped = scopedObject(c, RECENTS_KEY);
        JSONArray names = scoped.names();
        if (names == null) return new ArrayList<>();
        for (int i = 0; i < names.length(); i++) {
            JSONObject record = scoped.optJSONObject(names.optString(i));
            if (record == null) continue;
            JSONObject itemJson = record.optJSONObject("item");
            if (itemJson == null) continue;
            ContentItem item = ContentItem.fromJson(itemJson);
            if (type != null && !type.isBlank() && !type.equals(item.type)) continue;
            records.add(new RecentRecord(item, record.optLong("watched_at", 0L)));
        }
        records.sort((a, b) -> Long.compare(b.time, a.time));
        List<ContentItem> out = new ArrayList<>();
        for (RecentRecord r : records) out.add(r.item);
        return out;
    }

    private static void trimRecentObject(JSONObject object, int max) {
        JSONArray names = object.names();
        if (names == null || names.length() <= max) return;
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < names.length(); i++) keys.add(names.optString(i));
        keys.sort(Comparator.comparingLong(k -> {
            JSONObject o = object.optJSONObject(k);
            return o == null ? 0L : o.optLong("watched_at", 0L);
        }));
        for (int i = 0; i < keys.size() - max; i++) object.remove(keys.get(i));
    }

    // ---------------- Progresso de filmes e episódios ----------------

    public static void savePlaybackProgress(Context c, ContentItem item, long positionMs, long durationMs) {
        if (item == null || item.id.isBlank()) return;
        if (!("movie".equals(item.type) || "episode".equals(item.type))) return;

        JSONObject scoped = scopedObject(c, PLAYBACK_KEY);
        try {
            boolean finished = durationMs > 0 && positionMs >= Math.max(0, durationMs - 30000);
            if (finished) {
                scoped.remove(item.key());
            } else if (positionMs >= 5000) {
                JSONObject record = new JSONObject();
                record.put("item", item.toJson());
                record.put("position_ms", positionMs);
                record.put("duration_ms", Math.max(0, durationMs));
                record.put("updated_at", System.currentTimeMillis());
                scoped.put(item.key(), record);
            }
            saveScopedObject(c, PLAYBACK_KEY, scoped);
        } catch (Exception ignored) {}
    }

    public static long playbackPosition(Context c, ContentItem item) {
        JSONObject o = playbackRecord(c, item);
        return o == null ? 0L : o.optLong("position_ms", 0L);
    }

    public static long playbackDuration(Context c, ContentItem item) {
        JSONObject o = playbackRecord(c, item);
        return o == null ? 0L : o.optLong("duration_ms", 0L);
    }

    public static long progressUpdatedAt(Context c, ContentItem item) {
        JSONObject o = playbackRecord(c, item);
        return o == null ? 0L : o.optLong("updated_at", 0L);
    }

    public static boolean hasUnfinishedProgress(Context c, ContentItem item) {
        JSONObject o = playbackRecord(c, item);
        if (o == null) return false;
        long pos = o.optLong("position_ms", 0L);
        long dur = o.optLong("duration_ms", 0L);
        return pos >= 5000 && (dur <= 0 || pos < Math.max(0, dur - 30000));
    }

    public static void clearPlaybackProgress(Context c, ContentItem item) {
        if (item == null) return;
        JSONObject scoped = scopedObject(c, PLAYBACK_KEY);
        scoped.remove(item.key());
        saveScopedObject(c, PLAYBACK_KEY, scoped);
    }

    private static JSONObject playbackRecord(Context c, ContentItem item) {
        if (item == null) return null;
        return scopedObject(c, PLAYBACK_KEY).optJSONObject(item.key());
    }

    // ---------------- Último episódio assistido por série ----------------

    public static void saveSeriesProgress(Context c,
                                          String seriesId,
                                          String seriesName,
                                          String cover,
                                          String categoryId,
                                          ContentItem episode,
                                          long positionMs,
                                          long durationMs) {
        if (seriesId == null || seriesId.isBlank() || episode == null) return;
        JSONObject scoped = scopedObject(c, SERIES_PROGRESS_KEY);
        try {
            boolean completed = durationMs > 0 && positionMs >= Math.max(0, durationMs - 30000);
            JSONObject parent = new ContentItem(seriesId, seriesName, cover, "series", "", "Série", categoryId == null ? "" : categoryId, "").toJson();
            JSONObject record = new JSONObject();
            record.put("series", parent);
            record.put("episode", episode.toJson());
            record.put("position_ms", Math.max(0, positionMs));
            record.put("duration_ms", Math.max(0, durationMs));
            record.put("completed", completed);
            record.put("updated_at", System.currentTimeMillis());
            scoped.put(seriesId, record);
            saveScopedObject(c, SERIES_PROGRESS_KEY, scoped);
        } catch (Exception ignored) {}
    }

    public static JSONObject seriesProgress(Context c, String seriesId) {
        if (seriesId == null) return null;
        return scopedObject(c, SERIES_PROGRESS_KEY).optJSONObject(seriesId);
    }

    public static boolean hasSeriesProgress(Context c, String seriesId) {
        return seriesProgress(c, seriesId) != null;
    }

    public static boolean hasUnfinishedSeriesProgress(Context c, String seriesId) {
        JSONObject o = seriesProgress(c, seriesId);
        if (o == null) return false;
        return !o.optBoolean("completed", false) && o.optLong("position_ms", 0L) >= 5000;
    }

    public static long seriesProgressUpdatedAt(Context c, String seriesId) {
        JSONObject o = seriesProgress(c, seriesId);
        return o == null ? 0L : o.optLong("updated_at", 0L);
    }

    // ---------------- Escopo por usuário ----------------

    private static String accountKey(Context c) {
        String user = username(c);
        return user == null || user.isBlank() ? "default" : user;
    }

    private static JSONObject scopedObject(Context c, String preferenceKey) {
        try {
            JSONObject root = new JSONObject(p(c).getString(preferenceKey, "{}"));
            JSONObject scoped = root.optJSONObject(accountKey(c));
            return scoped == null ? new JSONObject() : scoped;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void saveScopedObject(Context c, String preferenceKey, JSONObject scoped) {
        try {
            JSONObject root = new JSONObject(p(c).getString(preferenceKey, "{}"));
            root.put(accountKey(c), scoped == null ? new JSONObject() : scoped);
            p(c).edit().putString(preferenceKey, root.toString()).apply();
        } catch (Exception ignored) {}
    }

    // ---------------- Controle parental / canais adultos ----------------

    public static String adultPin(Context c) {
        String pin = p(c).getString("adult_pin", "0000");
        return pin == null || pin.length() != 4 ? "0000" : pin;
    }

    public static boolean verifyAdultPin(Context c, String input) {
        return input != null && adultPin(c).equals(input.trim());
    }

    public static boolean setAdultPin(Context c, String pin) {
        if (pin == null || !pin.matches("\\d{4}")) return false;
        p(c).edit().putString("adult_pin", pin).apply();
        return true;
    }

    private static String trim(String s) {
        if (s == null) return "";
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static final class RecentRecord {
        final ContentItem item;
        final long time;
        RecentRecord(ContentItem item, long time) { this.item = item; this.time = time; }
    }
}
