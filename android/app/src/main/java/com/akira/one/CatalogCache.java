package com.akira.one;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cache local do catálogo Xtream.
 *
 * O objetivo é deixar TV, Filmes e Séries prontos enquanto o usuário está na
 * home. O catálogo completo fica em memória durante a sessão. Apenas as
 * categorias pequenas são persistidas em disco para evitar estouro de memória.
 */
public final class CatalogCache {
    private static final long FRESH_MS = 30L * 60L * 1000L; // 30 minutos
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Map<String, Entry> MEMORY = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private CatalogCache() {}

    private static final class Entry {
        final JSONArray data;
        final long savedAt;
        Entry(JSONArray data, long savedAt) {
            this.data = data;
            this.savedAt = savedAt;
        }
    }

    public static void warmFromDisk(Context context) {
        Context app = context.getApplicationContext();
        if (!Prefs.hasActivation(app)) return;
        // Somente categorias são persistidas em disco. Catálogos completos podem
        // conter dezenas de milhares de itens e serializá-los em uma String JSON
        // gigante pode causar OutOfMemoryError em celulares.
        for (String type : new String[]{"live", "movie", "series"}) {
            final String t = type;
            IO.execute(() -> readDiskIntoMemory(app, t, "categories"));
        }
    }

    /** Inicia os seis downloads em paralelo sem bloquear a home. */
    public static void preloadAll(Context context, boolean force) {
        Context app = context.getApplicationContext();
        if (!Prefs.hasActivation(app)) return;
        for (String type : new String[]{"live", "movie", "series"}) {
            preloadCategories(app, type, force);
            preloadContent(app, type, force);
        }
    }

    public static JSONArray peekCategories(Context context, String type) {
        Entry e = MEMORY.get(memoryKey(context, type, "categories"));
        return e == null ? null : e.data;
    }

    public static JSONArray peekContent(Context context, String type) {
        Entry e = MEMORY.get(memoryKey(context, type, "content"));
        return e == null ? null : e.data;
    }

    /**
     * Quantos dos 6 downloads (categorias + conteúdo de TV, Filmes e Séries)
     * foram gravados depois de {@code sinceMillis}. Usado pela tela de carregamento.
     */
    public static int updatedSince(Context context, long sinceMillis) {
        Context app = context.getApplicationContext();
        int done = 0;
        for (String type : new String[]{"live", "movie", "series"}) {
            for (String kind : new String[]{"categories", "content"}) {
                Entry e = MEMORY.get(memoryKey(app, type, kind));
                if (e != null && e.savedAt >= sinceMillis) done++;
            }
        }
        return done;
    }

    /** Mesma contagem, só de uma seção (0 a 2: categorias e conteúdo). */
    public static int updatedSince(Context context, String type, long sinceMillis) {
        Context app = context.getApplicationContext();
        int done = 0;
        for (String kind : new String[]{"categories", "content"}) {
            Entry e = MEMORY.get(memoryKey(app, type, kind));
            if (e != null && e.savedAt >= sinceMillis) done++;
        }
        return done;
    }

    /** Quantos downloads do catálogo deste usuário ainda estão em andamento. */
    public static int inFlightCount(Context context) {
        Context app = context.getApplicationContext();
        int n = 0;
        for (String type : new String[]{"live", "movie", "series"}) {
            for (String kind : new String[]{"categories", "content"}) {
                if (IN_FLIGHT.contains(memoryKey(app, type, kind))) n++;
            }
        }
        return n;
    }

    public static boolean isCategoriesFresh(Context context, String type) {
        return isFresh(context, type, "categories");
    }

    public static boolean isContentFresh(Context context, String type) {
        return isFresh(context, type, "content");
    }

    public static void saveCategories(Context context, String type, JSONArray data) {
        save(context, type, "categories", data);
    }

    public static void saveContent(Context context, String type, JSONArray data) {
        save(context, type, "content", data);
    }

    private static boolean isFresh(Context context, String type, String kind) {
        Entry e = MEMORY.get(memoryKey(context, type, kind));
        if (e != null) return System.currentTimeMillis() - e.savedAt <= FRESH_MS;
        // Conteúdo completo é cacheado somente em memória para evitar OOM ao
        // transformar arrays enormes em JSON para gravação em disco.
        if ("content".equals(kind)) return false;
        File f = file(context, type, kind);
        return f.exists() && System.currentTimeMillis() - f.lastModified() <= FRESH_MS;
    }

    private static void preloadCategories(Context app, String type, boolean force) {
        if (!force && isCategoriesFresh(app, type)) return;
        String flight = memoryKey(app, type, "categories");
        if (!IN_FLIGHT.add(flight)) return;
        XtreamClient.getCategories(app, type, new XtreamClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray array) {
                IN_FLIGHT.remove(flight);
                saveCategories(app, type, array);
            }
            @Override public void onError(Exception error) { IN_FLIGHT.remove(flight); }
        });
    }

    private static void preloadContent(Context app, String type, boolean force) {
        if (!force && isContentFresh(app, type)) return;
        String flight = memoryKey(app, type, "content");
        if (!IN_FLIGHT.add(flight)) return;

        XtreamClient.ArrayCallback cb = new XtreamClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray array) {
                IN_FLIGHT.remove(flight);
                saveContent(app, type, array);
            }
            @Override public void onError(Exception error) { IN_FLIGHT.remove(flight); }
        };

        if ("movie".equals(type)) XtreamClient.getMovies(app, cb);
        else if ("series".equals(type)) XtreamClient.getSeries(app, cb);
        else XtreamClient.getLive(app, cb);
    }

    private static void save(Context context, String type, String kind, JSONArray data) {
        if (data == null) return;
        Context app = context.getApplicationContext();
        long now = System.currentTimeMillis();
        MEMORY.put(memoryKey(app, type, kind), new Entry(data, now));

        // Catálogos completos (canais/filmes/séries) ficam somente em memória.
        // O antigo root.toString() criava uma segunda cópia gigantesca do
        // catálogo e podia estourar o heap do Android.
        if ("content".equals(kind)) {
            deleteLegacyDiskCache(app, type, kind);
            return;
        }

        // Categorias são pequenas e podem ser persistidas com segurança.
        IO.execute(() -> {
            try {
                File target = file(app, type, kind);
                File dir = target.getParentFile();
                if (dir != null && !dir.exists()) dir.mkdirs();
                JSONObject root = new JSONObject();
                root.put("saved_at", now);
                root.put("data", data);
                byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
                File tmp = new File(target.getAbsolutePath() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(bytes);
                    out.flush();
                }
                if (target.exists()) target.delete();
                if (!tmp.renameTo(target)) {
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        out.write(bytes);
                    }
                    tmp.delete();
                }
            } catch (Exception ignored) {}
        });
    }

    private static void readDiskIntoMemory(Context context, String type, String kind) {
        if ("content".equals(kind)) return;
        String key = memoryKey(context, type, kind);
        if (MEMORY.containsKey(key)) return;
        File f = file(context, type, kind);
        if (!f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            int off = 0;
            while (off < bytes.length) {
                int n = in.read(bytes, off, bytes.length - off);
                if (n < 0) break;
                off += n;
            }
            JSONObject root = new JSONObject(new String(bytes, 0, off, StandardCharsets.UTF_8));
            JSONArray data = root.optJSONArray("data");
            if (data != null) {
                long savedAt = root.optLong("saved_at", f.lastModified());
                MEMORY.put(key, new Entry(data, savedAt));
            }
        } catch (Exception ignored) {}
    }

    private static void deleteLegacyDiskCache(Context context, String type, String kind) {
        IO.execute(() -> {
            try {
                File f = file(context, type, kind);
                if (f.exists()) f.delete();
                File tmp = new File(f.getAbsolutePath() + ".tmp");
                if (tmp.exists()) tmp.delete();
            } catch (Exception ignored) {}
        });
    }

    private static File file(Context context, String type, String kind) {
        File dir = new File(context.getFilesDir(), "catalog_cache");
        String name = identity(context) + "_" + safe(type) + "_" + safe(kind) + ".json";
        return new File(dir, name);
    }

    private static String memoryKey(Context context, String type, String kind) {
        return identity(context) + ":" + type + ":" + kind;
    }

    private static String identity(Context context) {
        String raw = Prefs.serverUrl(context) + "|" + Prefs.username(context);
        return Integer.toHexString(raw.hashCode());
    }

    private static String safe(String s) {
        return s == null ? "" : s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
