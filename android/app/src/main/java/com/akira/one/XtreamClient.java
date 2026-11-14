package com.akira.one;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class XtreamClient {
    public interface ArrayCallback { void onSuccess(JSONArray array); void onError(Exception error); }
    public interface ObjectCallback { void onSuccess(JSONObject object); void onError(Exception error); }

    private static final ExecutorService EXEC=Executors.newCachedThreadPool();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private XtreamClient(){}

    private static String api(Context c, String action) {
        return Prefs.serverUrl(c)+"/player_api.php?username="+ApiClient.urlEncode(Prefs.username(c))+"&password="+ApiClient.urlEncode(Prefs.password(c))+"&action="+action;
    }

    public static void getLive(Context c, ArrayCallback cb){ getLive(c,"",cb); }
    public static void getLive(Context c,String categoryId,ArrayCallback cb){ array(api(c,"get_live_streams")+cat(categoryId),cb); }
    public static void getMovies(Context c, ArrayCallback cb){ getMovies(c,"",cb); }
    public static void getMovies(Context c,String categoryId,ArrayCallback cb){ array(api(c,"get_vod_streams")+cat(categoryId),cb); }
    public static void getSeries(Context c, ArrayCallback cb){ getSeries(c,"",cb); }
    public static void getSeries(Context c,String categoryId,ArrayCallback cb){ array(api(c,"get_series")+cat(categoryId),cb); }

    public static void getCategories(Context c,String type,ArrayCallback cb){
        String action="live".equals(type)?"get_live_categories":"movie".equals(type)?"get_vod_categories":"get_series_categories";
        array(api(c,action),cb);
    }

    public static void getSeriesInfo(Context c, String id, ObjectCallback cb){
        object(api(c,"get_series_info")+"&series_id="+ApiClient.urlEncode(id),cb);
    }

    public static void getShortEpg(Context c,String streamId,ObjectCallback cb){
        object(api(c,"get_short_epg")+"&stream_id="+ApiClient.urlEncode(streamId)+"&limit=8",cb);
    }

    private static String cat(String id){
        return id==null||id.isBlank()?"":"&category_id="+ApiClient.urlEncode(id);
    }

    private static void array(String url, ArrayCallback cb){
        EXEC.execute(()->{
            try{
                JSONArray a=new JSONArray(ApiClient.getText(url,25000));
                MAIN.post(()->cb.onSuccess(a));
            }catch(Exception e){
                MAIN.post(()->cb.onError(e));
            }
        });
    }

    private static void object(String url, ObjectCallback cb){
        EXEC.execute(()->{
            try{
                JSONObject o=new JSONObject(ApiClient.getText(url,25000));
                MAIN.post(()->cb.onSuccess(o));
            }catch(Exception e){
                MAIN.post(()->cb.onError(e));
            }
        });
    }

    public static String liveUrl(Context c,String streamId){
        return buildLiveUrl(Prefs.serverUrl(c), c, streamId, "ts");
    }

    /**
     * Gera alternativas de reprodução. Isto corrige provedores que autenticam
     * normalmente mas entregam o canal por HLS (.m3u8), TS, outro host/porta
     * ou via direct_source.
     */
    public static List<String> liveCandidates(Context c, String streamId, String directSource) {
        Set<String> out = new LinkedHashSet<>();
        if (directSource != null && !directSource.isBlank()) out.add(directSource.trim());

        List<String> bases = new ArrayList<>();
        String playback = Prefs.playbackBase(c);
        String login = Prefs.serverUrl(c);
        if (playback != null && !playback.isBlank()) bases.add(trimBase(playback));
        if (login != null && !login.isBlank() && !bases.contains(trimBase(login))) bases.add(trimBase(login));

        List<String> formats = preferredFormats(c);
        for (String base : bases) {
            for (String format : formats) {
                if ("rtmp".equals(format)) continue;
                out.add(buildLiveUrl(base, c, streamId, format));
            }
            // Alguns painéis aceitam o stream sem extensão.
            out.add(trimBase(base)+"/live/"+enc(Prefs.username(c))+"/"+enc(Prefs.password(c))+"/"+streamId);
        }
        return new ArrayList<>(out);
    }

    private static List<String> preferredFormats(Context c) {
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        String raw = Prefs.liveFormats(c);
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                String f = part.trim().toLowerCase();
                if ("m3u8".equals(f) || "ts".equals(f) || "rtmp".equals(f)) formats.add(f);
            }
        }
        // HLS costuma ser o mais tolerante no ExoPlayer; TS fica como fallback.
        if (formats.contains("m3u8")) {
            formats.remove("m3u8");
            LinkedHashSet<String> ordered = new LinkedHashSet<>();
            ordered.add("m3u8");
            ordered.addAll(formats);
            formats = ordered;
        }
        formats.add("ts");
        formats.add("m3u8");
        return new ArrayList<>(formats);
    }

    private static String buildLiveUrl(String base, Context c, String streamId, String ext) {
        return trimBase(base)+"/live/"+enc(Prefs.username(c))+"/"+enc(Prefs.password(c))+"/"+streamId+"."+ext;
    }

    public static String movieUrl(Context c,String streamId,String ext){
        if(ext==null||ext.isBlank())ext="mp4";
        return Prefs.serverUrl(c)+"/movie/"+enc(Prefs.username(c))+"/"+enc(Prefs.password(c))+"/"+streamId+"."+ext;
    }

    public static String seriesUrl(Context c,String streamId,String ext){
        if(ext==null||ext.isBlank())ext="mp4";
        return Prefs.serverUrl(c)+"/series/"+enc(Prefs.username(c))+"/"+enc(Prefs.password(c))+"/"+streamId+"."+ext;
    }

    private static String trimBase(String s){
        if(s==null)return "";
        s=s.trim();
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        return s;
    }

    private static String enc(String s){return ApiClient.urlEncode(s);}
}
