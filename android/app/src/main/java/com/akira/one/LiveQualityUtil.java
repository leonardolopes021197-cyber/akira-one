package com.akira.one;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class LiveQualityUtil {
    private static final Pattern BRACKET_QUALITY = Pattern.compile(
            "(?i)[\\[(]\\s*(?:4K|UHD|2160P?|FHD|FULL\\s*HD|FULLHD|1080P?|HD|720P?|SD|480P?|SH)\\s*[\\])]"
    );
    private static final Pattern QUALITY_TOKEN = Pattern.compile(
            "(?i)(^|[\\s|/\\-–—_:]+)(?:4K|UHD|2160P?|FHD|FULL\\s*HD|FULLHD|1080P?|HD|720P?|SD|480P?|SH)(?=$|[\\s|/\\-–—_:]+)"
    );
    private static final Pattern EXTRA_SEPARATORS = Pattern.compile("^[\\s|/\\-–—_:]+|[\\s|/\\-–—_:]+$");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    private LiveQualityUtil() {}

    public static String qualityLabel(String name) {
        if (name == null) return "ORIGINAL";
        String n = normalizeForMatch(name);
        if (containsAny(n, "4k", "uhd", "2160p", "2160")) return "4K";
        if (containsAny(n, "fhd", "full hd", "fullhd", "1080p", "1080")) return "FHD";
        if (hasToken(n, "hd") || containsAny(n, "720p", "720")) return "HD";
        if (hasToken(n, "sd") || hasToken(n, "sh") || containsAny(n, "480p", "480")) return "SD";
        return "ORIGINAL";
    }

    public static String cleanChannelName(String name) {
        if (name == null) return "Canal";
        String clean = BRACKET_QUALITY.matcher(name).replaceAll(" ");
        String previous;
        do {
            previous = clean;
            clean = QUALITY_TOKEN.matcher(clean).replaceAll("$1");
        } while (!clean.equals(previous));
        clean = clean.replaceAll("(?i)\\b(?:H\\.?264|H\\.?265|HEVC)\\b", " ");
        clean = MULTI_SPACE.matcher(clean).replaceAll(" ").trim();
        clean = EXTRA_SEPARATORS.matcher(clean).replaceAll("").trim();
        clean = clean.replaceAll("\\s*([|/\\-–—_:])\\s*([|/\\-–—_:])\\s*", "$1");
        clean = EXTRA_SEPARATORS.matcher(clean).replaceAll("").trim();
        return clean.isBlank() ? name.trim() : clean;
    }

    public static String groupKey(ContentItem item) {
        String base = cleanChannelName(item == null ? "" : item.name);
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized;
    }

    public static List<ContentItem> uniqueQualities(List<ContentItem> variants) {
        Map<String, ContentItem> byQuality = new LinkedHashMap<>();
        if (variants != null) {
            for (ContentItem item : variants) {
                String q = qualityLabel(item.name);
                ContentItem current = byQuality.get(q);
                if (current == null || betterSource(item, current)) byQuality.put(q, item);
            }
        }

        List<ContentItem> out = new ArrayList<>();
        addIfPresent(out, byQuality, "SD");
        addIfPresent(out, byQuality, "HD");
        addIfPresent(out, byQuality, "FHD");
        addIfPresent(out, byQuality, "4K");
        addIfPresent(out, byQuality, "ORIGINAL");
        return out;
    }

    public static ContentItem pickDefault(List<ContentItem> variants) {
        List<ContentItem> unique = uniqueQualities(variants);
        ContentItem found = findQuality(unique, "HD");
        if (found != null) return found;
        found = findQuality(unique, "FHD");
        if (found != null) return found;
        found = findQuality(unique, "4K");
        if (found != null) return found;
        found = findQuality(unique, "SD");
        if (found != null) return found;
        return unique.isEmpty() ? null : unique.get(0);
    }

    public static ContentItem findById(List<ContentItem> variants, String id) {
        if (variants == null || id == null) return null;
        for (ContentItem item : variants) if (id.equals(item.id)) return item;
        return null;
    }

    public static String variantsToJson(List<ContentItem> variants) {
        JSONArray array = new JSONArray();
        if (variants != null) {
            for (ContentItem item : uniqueQualities(variants)) {
                try {
                    JSONObject o = item.toJson();
                    o.put("quality", qualityLabel(item.name));
                    array.put(o);
                } catch (Exception ignored) {}
            }
        }
        return array.toString();
    }

    public static List<ContentItem> variantsFromJson(String raw) {
        List<ContentItem> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) out.add(ContentItem.fromJson(o));
            }
        } catch (Exception ignored) {}
        return uniqueQualities(out);
    }

    public static String qualitySummary(List<ContentItem> variants) {
        List<ContentItem> unique = uniqueQualities(variants);
        if (unique.isEmpty()) return "CANAL AO VIVO";
        ContentItem def = pickDefault(unique);
        String q = def == null ? "AUTO" : qualityLabel(def.name);
        if (unique.size() == 1) return q + " padrão";
        return q + " padrão • " + unique.size() + " qualidades";
    }

    private static ContentItem findQuality(List<ContentItem> variants, String quality) {
        if (variants == null) return null;
        for (ContentItem item : variants) if (quality.equals(qualityLabel(item.name))) return item;
        return null;
    }

    private static void addIfPresent(List<ContentItem> out, Map<String, ContentItem> map, String key) {
        ContentItem item = map.get(key);
        if (item != null) out.add(item);
    }

    private static boolean betterSource(ContentItem candidate, ContentItem current) {
        boolean cDirect = candidate != null && candidate.sourceUrl != null && !candidate.sourceUrl.isBlank();
        boolean oDirect = current != null && current.sourceUrl != null && !current.sourceUrl.isBlank();
        return cDirect && !oDirect;
    }

    private static String normalizeForMatch(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('|', ' ')
                .replace('/', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean containsAny(String input, String... values) {
        for (String value : values) if (input.contains(value)) return true;
        return false;
    }

    private static boolean hasToken(String input, String token) {
        return (" " + input + " ").contains(" " + token + " ");
    }
}
