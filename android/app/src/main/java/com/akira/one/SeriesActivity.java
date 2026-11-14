package com.akira.one;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SeriesActivity extends BaseActivity {
    private final List<CategoryItem> seasonCategories = new ArrayList<>();
    private final Map<String, List<ContentItem>> episodesBySeason = new LinkedHashMap<>();
    private final List<ContentItem> allOrderedEpisodes = new ArrayList<>();
    private final List<ContentItem> shownEpisodes = new ArrayList<>();

    private CategoryAdapter seasonAdapter;
    private ContentAdapter episodeAdapter;
    private RecyclerView seasonRecycler;
    private RecyclerView episodeRecycler;
    private TextView status;
    private TextView selectedSeasonTitle;

    private String seriesId;
    private String seriesName;
    private String seriesCover;
    private String seriesCategoryId;
    private String selectedSeason = "";
    private boolean loaded = false;
    private boolean initialFocusDone = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_series);

        seriesId = getIntent().getStringExtra("series_id");
        seriesName = getIntent().getStringExtra("series_name");
        seriesCover = getIntent().getStringExtra("series_cover");
        seriesCategoryId = getIntent().getStringExtra("series_category_id");
        if (seriesName == null || seriesName.isBlank()) seriesName = "Série";
        if (seriesCover == null) seriesCover = "";
        if (seriesCategoryId == null) seriesCategoryId = "";

        ((TextView) findViewById(R.id.seriesTitle)).setText(seriesName);
        status = findViewById(R.id.seriesStatus);
        selectedSeasonTitle = findViewById(R.id.selectedSeasonTitle);
        seasonRecycler = findViewById(R.id.seasonsRecycler);
        episodeRecycler = findViewById(R.id.episodesRecycler);

        seasonRecycler.setLayoutManager(new LinearLayoutManager(this));
        seasonAdapter = new CategoryAdapter(seasonCategories, new CategoryAdapter.Listener() {
            @Override
            public void onSelected(CategoryItem item, int position) {
                selectSeason(item.id, false);
            }

            @Override
            public void onMoveRight() {
                focusFirstEpisode();
            }
        });
        seasonRecycler.setAdapter(seasonAdapter);

        episodeRecycler.setLayoutManager(new LinearLayoutManager(this));
        episodeAdapter = new ContentAdapter(shownEpisodes, new ContentAdapter.Listener() {
            @Override
            public void onClick(ContentItem item) { play(item); }

            @Override
            public void onLongClick(ContentItem item) {
                boolean fav = Prefs.toggleFavorite(SeriesActivity.this, item);
                Toast.makeText(SeriesActivity.this,
                        fav ? "Episódio adicionado aos favoritos" : "Episódio removido dos favoritos",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMoveLeft() { focusSelectedSeason(); }
        });
        episodeRecycler.setAdapter(episodeAdapter);

        load(seriesId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loaded) refreshSelectedSeason(false);
    }

    private void load(String id) {
        status.setText("Carregando temporadas e episódios...");
        XtreamClient.getSeriesInfo(this, id, new XtreamClient.ObjectCallback() {
            @Override
            public void onSuccess(JSONObject root) {
                seasonCategories.clear();
                episodesBySeason.clear();
                allOrderedEpisodes.clear();

                JSONObject seasons = root.optJSONObject("episodes");
                if (seasons != null) {
                    List<String> seasonKeys = new ArrayList<>();
                    Iterator<String> keys = seasons.keys();
                    while (keys.hasNext()) seasonKeys.add(keys.next());
                    seasonKeys.sort(SeriesActivity::compareSeasonKeys);

                    for (String season : seasonKeys) {
                        JSONArray arr = seasons.optJSONArray(season);
                        if (arr == null) continue;
                        List<ContentItem> seasonEpisodes = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject ep = arr.optJSONObject(i);
                            if (ep == null) continue;
                            JSONObject info = ep.optJSONObject("info");
                            String icon = info == null ? "" : info.optString("movie_image", "");
                            String episodeNum = ep.optString("episode_num", String.valueOf(i + 1));
                            String title = ep.optString("title", "Episódio " + episodeNum);
                            String meta = "Temporada " + season + " • Episódio " + episodeNum;
                            ContentItem item = new ContentItem(
                                    ep.optString("id"),
                                    title,
                                    icon,
                                    "episode",
                                    ep.optString("container_extension", "mp4"),
                                    meta
                            );
                            seasonEpisodes.add(item);
                            allOrderedEpisodes.add(item);
                        }
                        if (!seasonEpisodes.isEmpty()) {
                            episodesBySeason.put(season, seasonEpisodes);
                            seasonCategories.add(new CategoryItem(season, seasonLabel(season), seasonEpisodes.size()));
                        }
                    }
                }

                loaded = true;
                seasonAdapter.notifyDataSetChanged();
                chooseInitialSeasonFromProgress();
            }

            @Override
            public void onError(Exception e) {
                status.setText("Falha ao carregar episódios: " + e.getMessage());
            }
        });
    }

    private void chooseInitialSeasonFromProgress() {
        if (seasonCategories.isEmpty()) {
            status.setText("Nenhuma temporada encontrada.");
            return;
        }

        String targetEpisodeId = "";
        JSONObject progress = Prefs.seriesProgress(this, seriesId);
        if (progress != null) {
            JSONObject last = progress.optJSONObject("episode");
            if (last != null) targetEpisodeId = last.optString("id", "");
            boolean completed = progress.optBoolean("completed", false);
            int idx = indexOfEpisode(targetEpisodeId);
            if (completed && idx >= 0 && idx + 1 < allOrderedEpisodes.size()) {
                targetEpisodeId = allOrderedEpisodes.get(idx + 1).id;
            }
        }

        String season = seasonForEpisode(targetEpisodeId);
        if (season.isBlank()) season = seasonCategories.get(0).id;
        selectSeason(season, true);
    }

    private void selectSeason(String season, boolean initial) {
        if (season == null || season.isBlank() || !episodesBySeason.containsKey(season)) return;
        selectedSeason = season;
        seasonAdapter.setSelected(season);
        selectedSeasonTitle.setText(seasonLabel(season));
        refreshSelectedSeason(initial);
    }

    private void refreshSelectedSeason(boolean allowInitialFocus) {
        List<ContentItem> source = episodesBySeason.get(selectedSeason);
        if (source == null) return;

        JSONObject progress = Prefs.seriesProgress(this, seriesId);
        String lastEpisodeId = "";
        long position = 0L;
        boolean completed = false;
        String lastTitle = "";
        if (progress != null) {
            JSONObject last = progress.optJSONObject("episode");
            if (last != null) {
                lastEpisodeId = last.optString("id", "");
                lastTitle = last.optString("name", "");
            }
            position = progress.optLong("position_ms", 0L);
            completed = progress.optBoolean("completed", false);
        }

        shownEpisodes.clear();
        int localLast = -1;
        for (int i = 0; i < source.size(); i++) {
            ContentItem base = source.get(i);
            String meta = base.meta;
            if (!lastEpisodeId.isBlank() && lastEpisodeId.equals(base.id)) {
                localLast = i;
                if (completed) meta = "✓ Assistido • " + meta;
                else if (position >= 5000) meta = "▶ Continuar em " + formatTime(position) + " • " + meta;
                else meta = "▶ Último assistido • " + meta;
            }
            shownEpisodes.add(new ContentItem(base.id, base.name, base.icon, base.type, base.extension, meta, base.categoryId, base.sourceUrl));
        }
        episodeAdapter.notifyDataSetChanged();

        String baseStatus = shownEpisodes.size() + (shownEpisodes.size() == 1 ? " episódio" : " episódios") + " em " + seasonLabel(selectedSeason);
        if (progress != null && !lastTitle.isBlank()) {
            baseStatus += completed ? " • último: " + lastTitle + " concluído" : " • último: " + lastTitle + " em " + formatTime(position);
        }
        status.setText(baseStatus);

        if (allowInitialFocus && !initialFocusDone) {
            String targetId = lastEpisodeId;
            int globalIdx = indexOfEpisode(lastEpisodeId);
            if (completed && globalIdx >= 0 && globalIdx + 1 < allOrderedEpisodes.size()) targetId = allOrderedEpisodes.get(globalIdx + 1).id;
            int localTarget = indexInList(source, targetId);
            if (localTarget < 0) localTarget = 0;
            initialFocusDone = true;
            final int focusPosition = localTarget;
            episodeRecycler.scrollToPosition(focusPosition);
            episodeRecycler.post(() -> {
                RecyclerView.ViewHolder holder = episodeRecycler.findViewHolderForAdapterPosition(focusPosition);
                if (holder != null) holder.itemView.requestFocus();
            });
        }
    }

    private void focusFirstEpisode() {
        if (shownEpisodes.isEmpty()) return;
        episodeRecycler.scrollToPosition(0);
        episodeRecycler.post(() -> {
            RecyclerView.ViewHolder h = episodeRecycler.findViewHolderForAdapterPosition(0);
            if (h != null) h.itemView.requestFocus();
            else episodeRecycler.requestFocus();
        });
    }

    private void focusSelectedSeason() {
        int position = 0;
        for (int i = 0; i < seasonCategories.size(); i++) {
            if (seasonCategories.get(i).id.equals(selectedSeason)) { position = i; break; }
        }
        final int p = position;
        seasonRecycler.scrollToPosition(p);
        seasonRecycler.post(() -> {
            RecyclerView.ViewHolder h = seasonRecycler.findViewHolderForAdapterPosition(p);
            if (h != null) h.itemView.requestFocus();
            else seasonRecycler.requestFocus();
        });
    }

    private void play(ContentItem episode) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", XtreamClient.seriesUrl(this, episode.id, episode.extension));
        i.putExtra("title", episode.name);
        ContentNavigator.putContent(i, episode);
        i.putExtra("series_id", seriesId);
        i.putExtra("series_name", seriesName);
        i.putExtra("series_cover", seriesCover);
        i.putExtra("series_category_id", seriesCategoryId);
        startActivity(i);
    }

    private int indexOfEpisode(String id) {
        if (id == null || id.isBlank()) return -1;
        for (int i = 0; i < allOrderedEpisodes.size(); i++) if (id.equals(allOrderedEpisodes.get(i).id)) return i;
        return -1;
    }

    private int indexInList(List<ContentItem> list, String id) {
        if (id == null || id.isBlank()) return -1;
        for (int i = 0; i < list.size(); i++) if (id.equals(list.get(i).id)) return i;
        return -1;
    }

    private String seasonForEpisode(String episodeId) {
        if (episodeId == null || episodeId.isBlank()) return "";
        for (Map.Entry<String, List<ContentItem>> e : episodesBySeason.entrySet()) {
            if (indexInList(e.getValue(), episodeId) >= 0) return e.getKey();
        }
        return "";
    }

    private static String seasonLabel(String season) {
        if (season == null || season.isBlank()) return "Temporada";
        if ("0".equals(season)) return "Especiais";
        return "Temporada " + season;
    }

    private static int compareSeasonKeys(String a, String b) {
        try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
        catch (Exception ignored) { return a.compareToIgnoreCase(b); }
    }

    private static String formatTime(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
    }
}
