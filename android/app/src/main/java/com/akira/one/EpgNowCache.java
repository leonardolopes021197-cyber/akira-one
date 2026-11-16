package com.akira.one;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "O que está passando agora" de cada canal, mostrado embaixo do nome na lista.
 *
 * Pede o EPG curto só dos canais que aparecem na tela, no máximo 3 por vez, com
 * cache de 5 minutos. Pedidos de itens que já saíram da tela (rolagem rápida) são
 * descartados, e os mais novos passam na frente. Tudo roda na thread principal
 * (os callbacks do XtreamClient já voltam nela).
 */
public final class EpgNowCache {
    public interface Callback {
        void onResult(String title);
    }

    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final long ERROR_TTL_MS = 30 * 1000L;
    private static final int MAX_PARALLEL = 3;
    private static final int MAX_QUEUE = 40;

    private static final Map<String, Entry> CACHE = new HashMap<>();
    private static final Map<String, Request> PENDING = new HashMap<>();
    private static final ArrayDeque<Request> QUEUE = new ArrayDeque<>();
    private static int running = 0;

    private static final class Entry {
        final String title;
        final long expiresAt;
        Entry(String title, long expiresAt) { this.title = title; this.expiresAt = expiresAt; }
    }

    private static final class Request {
        final Context app;
        final String key;
        final String streamId;
        final List<Callback> callbacks = new ArrayList<>();
        Request(Context app, String key, String streamId) { this.app = app; this.key = key; this.streamId = streamId; }
    }

    private EpgNowCache() {}

    private static String key(Context c, String streamId) {
        return Prefs.username(c) + "|" + streamId;
    }

    /** Título em cache ("" = canal sem programação) ou null se ainda não se sabe. */
    public static String peek(Context c, String streamId) {
        Entry e = CACHE.get(key(c, streamId));
        if (e == null || e.expiresAt < System.currentTimeMillis()) return null;
        return e.title;
    }

    public static void request(Context c, String streamId, Callback cb) {
        if (streamId == null || streamId.isBlank()) return;
        Context app = c.getApplicationContext();
        String k = key(app, streamId);

        Entry e = CACHE.get(k);
        if (e != null && e.expiresAt >= System.currentTimeMillis()) {
            if (cb != null) cb.onResult(e.title);
            return;
        }
        Request pending = PENDING.get(k);
        if (pending != null) {
            if (cb != null) pending.callbacks.add(cb);
            return;
        }

        Request r = new Request(app, k, streamId);
        if (cb != null) r.callbacks.add(cb);
        PENDING.put(k, r);
        QUEUE.addFirst(r); // o mais novo (o que está na tela agora) vai na frente
        while (QUEUE.size() > MAX_QUEUE) {
            Request dropped = QUEUE.pollLast();
            if (dropped != null) PENDING.remove(dropped.key);
        }
        pump();
    }

    private static void pump() {
        while (running < MAX_PARALLEL && !QUEUE.isEmpty()) {
            final Request r = QUEUE.pollFirst();
            if (r == null) break;
            running++;
            XtreamClient.getShortEpg(r.app, r.streamId, new XtreamClient.ObjectCallback() {
                @Override public void onSuccess(JSONObject o) { finish(r, parseNow(o), TTL_MS); }
                @Override public void onError(Exception e) { finish(r, "", ERROR_TTL_MS); }
            });
        }
    }

    private static void finish(Request r, String title, long ttl) {
        running = Math.max(0, running - 1);
        CACHE.put(r.key, new Entry(title, System.currentTimeMillis() + ttl));
        PENDING.remove(r.key);
        for (Callback cb : r.callbacks) cb.onResult(title);
        pump();
    }

    /** Programa em andamento (ou o próximo que ainda não acabou). */
    static String parseNow(JSONObject o) {
        if (o == null) return "";
        JSONArray a = o.optJSONArray("epg_listings");
        if (a == null) a = o.optJSONArray("epg_list");
        if (a == null) return "";
        long now = System.currentTimeMillis() / 1000L;
        String fallback = "";
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e == null) continue;
            long start = e.optLong("start_timestamp", 0L);
            long stop = e.optLong("stop_timestamp", 0L);
            if (stop > 0 && now >= stop) continue; // já terminou
            String title = decode(e.optString("title", ""));
            if (title.isEmpty()) continue;
            if (start > 0 && now >= start) return title; // em andamento
            if (fallback.isEmpty()) fallback = title;
        }
        return fallback;
    }

    /** O Xtream envia título/descrição do EPG em base64. */
    static String decode(String v) {
        if (v == null || v.isBlank()) return "";
        try {
            byte[] b = Base64.decode(v, Base64.DEFAULT);
            String s = new String(b, StandardCharsets.UTF_8);
            int control = 0;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                if (ch < 32 && ch != '\n' && ch != '\r' && ch != '\t') control++;
            }
            return control > 2 ? v.trim() : s.trim();
        } catch (Exception e) {
            return v.trim();
        }
    }
}
