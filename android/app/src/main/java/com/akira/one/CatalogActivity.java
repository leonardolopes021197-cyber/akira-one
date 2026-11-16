package com.akira.one;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CatalogActivity extends BaseActivity {
    private static final int DEFAULT_POSTER_SPAN_COUNT = 3;
    private static final String CAT_RECENT = "__recent__";
    private static final String CAT_CONTINUE = "__continue__";
    private static final long PREVIEW_DELAY_MS = 500L;

    private final List<ContentItem> masterItems = new ArrayList<>();
    private final List<ContentItem> shown = new ArrayList<>();
    private final List<CategoryItem> allCategories = new ArrayList<>();
    private final List<CategoryItem> categories = new ArrayList<>();
    private final Set<String> adultCategoryIds = new HashSet<>();
    // Para TV ao vivo mostramos apenas um canal por nome e guardamos as qualidades aqui.
    private final Map<String, List<ContentItem>> liveVariantsByDisplayKey = new HashMap<>();
    private final Map<String, ContentItem> liveSelectedVariantByDisplayKey = new HashMap<>();
    private final Map<String, Set<String>> liveCategoryIdsByDisplayKey = new HashMap<>();

    private CatalogContentAdapter liveAdapter;
    private PosterGridAdapter posterAdapter;
    private CategoryAdapter categoryAdapter;
    private RecyclerView categoriesRecycler;
    private RecyclerView contentRecycler;
    private EditText search;
    private EditText categorySearch;
    private TextView status;
    private TextView selectedCategoryTitle;
    private String type;
    private String currentCategory = "";
    private int selectedCategoryPosition = 0;
    private boolean contentReady = false;
    private boolean adultSessionUnlocked = false;

    // TV ao vivo: preview embutido
    private PlayerView previewPlayerView;
    private TextView previewTitle;
    private TextView previewHint;
    private TextView previewInstruction;
    private TextView previewEpgButton;
    private TextView previewFavoriteButton;
    private TextView previewQualityButton;
    private ExoPlayer previewPlayer;
    private ContentItem previewItem;
    private ContentItem previewVariant;
    private final List<String> previewCandidates = new ArrayList<>();
    private int previewCandidateIndex = 0;
    private boolean previewReady = false;
    private boolean previewFailed = false;
    private boolean previewWasPlaying = false;
    private final Handler previewHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingPreviewRunnable;

    // TV ao vivo: painel de informações do canal (sem vídeo)
    private android.widget.ImageView infoLogo;
    private android.widget.ImageView infoHeart;
    private TextView infoEpgStatus;
    private android.widget.LinearLayout infoEpgList;
    private TextView infoWatchButton;
    private Runnable pendingInfoRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        type = getIntent().getStringExtra("type");
        if (type == null) type = "live";
        setContentView("live".equals(type) ? R.layout.activity_catalog_live : R.layout.activity_catalog);

        TextView title = findViewById(R.id.titleText);
        title.setText("live".equals(type) ? "TV ao Vivo" : "movie".equals(type) ? "Filmes" : "Séries");

        status = findViewById(R.id.subtitleText);
        selectedCategoryTitle = findViewById(R.id.selectedCategoryTitle);
        categoriesRecycler = findViewById(R.id.categoriesRecycler);
        contentRecycler = findViewById(R.id.recycler);
        search = findViewById(R.id.catalogSearch);
        categorySearch = findViewById(R.id.categorySearch);
        search.setHint("live".equals(type) ? "Pesquisar canais" : "movie".equals(type) ? "Pesquisar filmes" : "Pesquisar séries");

        categoriesRecycler.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter(categories, new CategoryAdapter.Listener() {
            @Override
            public void onSelected(CategoryItem item, int position) {
                if (isProtectedAdultCategory(item.id) && !adultSessionUnlocked) {
                    requestAdultPin(item, position);
                } else {
                    selectCategoryInternal(item, position);
                }
            }

            @Override
            public void onMoveRight() { focusFirstContent(); }
        });
        categoriesRecycler.setAdapter(categoryAdapter);

        setupContentRecycler();
        if ("live".equals(type)) {
            setupRail();
            setupLiveInfoPanel();
        }

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyFilter(); }
            @Override public void afterTextChanged(Editable e) {}
        });

        if (categorySearch != null) {
            categorySearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { applyCategoryFilter(); }
                @Override public void afterTextChanged(Editable e) {}
            });
        }

        loadCategories();
        loadAllContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (contentReady) {
            updateCategoryCounts();
            applyFilter();
        }
        if ("live".equals(type) && previewPlayer != null && previewWasPlaying) {
            previewWasPlaying = false;
            previewPlayer.play();
        }
    }

    @Override
    protected void onPause() {
        if (previewPlayer != null) {
            previewWasPlaying = previewPlayer.isPlaying();
            previewPlayer.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        previewHandler.removeCallbacksAndMessages(null);
        if (previewPlayer != null) {
            previewPlayer.release();
            previewPlayer = null;
        }
        super.onDestroy();
    }

    private void setupContentRecycler() {
        if (isPosterGrid()) {
            contentRecycler.setLayoutManager(new GridLayoutManager(this, DEFAULT_POSTER_SPAN_COUNT));
            posterAdapter = new PosterGridAdapter(shown, DEFAULT_POSTER_SPAN_COUNT, new PosterGridAdapter.Listener() {
                @Override public void onClick(ContentItem item) { openItem(item); }
                @Override public void onLongClick(ContentItem item) { context(item); }
                @Override public void onMoveLeft() { focusSelectedCategory(); }
            });
            contentRecycler.setAdapter(posterAdapter);
            contentRecycler.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> contentRecycler.post(this::updatePosterGridGeometry));
            contentRecycler.post(this::updatePosterGridGeometry);
        } else {
            contentRecycler.setLayoutManager(new LinearLayoutManager(this));
            liveAdapter = new CatalogContentAdapter(shown, new CatalogContentAdapter.Listener() {
                @Override public void onClick(ContentItem item) { openLiveFullScreen(item); }
                @Override public void onLongClick(ContentItem item) { context(item); }
                @Override public void onFocus(ContentItem item) { scheduleLiveInfo(item); }
                @Override public void onMoveLeft() {
                    View rail = findViewById(R.id.railLive);
                    if (rail != null) rail.requestFocus(); else focusSelectedCategory();
                }
                @Override public void onMoveRight() {
                    if (infoWatchButton != null) infoWatchButton.requestFocus();
                }
            });
            // Sem animação de "troca": ela recriaria a linha focada ao marcar o canal selecionado.
            androidx.recyclerview.widget.RecyclerView.ItemAnimator animator = contentRecycler.getItemAnimator();
            if (animator instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
                ((androidx.recyclerview.widget.SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
            }
            contentRecycler.setAdapter(liveAdapter);
        }
    }

    private void setupLivePreview() {
        previewPlayerView = findViewById(R.id.livePreviewPlayer);
        previewTitle = findViewById(R.id.previewChannelTitle);
        previewHint = findViewById(R.id.previewHint);
        previewInstruction = findViewById(R.id.previewInstruction);
        previewEpgButton = findViewById(R.id.previewEpgButton);
        previewFavoriteButton = findViewById(R.id.previewFavoriteButton);
        previewQualityButton = findViewById(R.id.previewQualityButton);

        if (previewPlayerView != null) previewPlayerView.setUseController(false);
        if (previewEpgButton != null) previewEpgButton.setOnClickListener(v -> openPreviewEpg());
        if (previewQualityButton != null) previewQualityButton.setOnClickListener(v -> showQualityDialog(previewItem));
        if (previewFavoriteButton != null) previewFavoriteButton.setOnClickListener(v -> togglePreviewFavorite());
    }

    // ------------------------------------------------------------ Menu lateral (TV ao vivo)

    private void setupRail() {
        View live = findViewById(R.id.railLive);
        if (live != null) live.setSelected(true);

        bindRail(R.id.railSearch, () -> startActivity(new Intent(this, SearchActivity.class)));
        bindRail(R.id.railHome, this::finish);
        bindRail(R.id.railLive, () -> { /* já está em TV ao Vivo */ });
        bindRail(R.id.railMovies, () -> switchCatalog("movie"));
        bindRail(R.id.railSeries, () -> switchCatalog("series"));
        bindRail(R.id.railFavorites, () -> startActivity(new Intent(this, FavoritesActivity.class)));
        bindRail(R.id.railSettings, () -> startActivity(new Intent(this, SettingsActivity.class)));
        bindRail(R.id.railLogout, this::confirmLogout);
    }

    private void bindRail(int id, Runnable action) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(x -> action.run());
    }

    private void switchCatalog(String nextType) {
        Intent i = new Intent(this, CatalogActivity.class);
        i.putExtra("type", nextType);
        startActivity(i);
        finish();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Sair da conta?")
                .setMessage("Você vai precisar entrar de novo com usuário e senha.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sair", (d, w) -> {
                    CatalogCache.clearAll(this);
                    Prefs.clearActivation(this);
                    Prefs.saveLists(this, null);
                    Intent i = new Intent(this, LoginActivity.class);
                    i.putExtra("force_login", true);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .show();
    }

    // ------------------------------------------------------------ Informações do canal (sem vídeo)

    private void setupLiveInfoPanel() {
        previewTitle = findViewById(R.id.previewChannelTitle);
        previewEpgButton = findViewById(R.id.previewEpgButton);
        previewFavoriteButton = findViewById(R.id.previewFavoriteButton);
        infoLogo = findViewById(R.id.infoLogo);
        infoHeart = findViewById(R.id.infoHeart);
        infoEpgStatus = findViewById(R.id.infoEpgStatus);
        infoEpgList = findViewById(R.id.infoEpgList);
        infoWatchButton = findViewById(R.id.infoWatchButton);

        if (previewEpgButton != null) previewEpgButton.setOnClickListener(v -> openPreviewEpg());
        if (infoHeart != null) infoHeart.setOnClickListener(v -> togglePreviewFavorite());
        if (infoWatchButton != null) {
            infoWatchButton.setOnClickListener(v -> {
                if (previewItem != null) openLiveFullScreen(previewItem);
            });
        }
    }

    /** Atualiza a coluna do EPG do canal focado, com um pequeno atraso para não pedir a cada rolagem. */
    private void scheduleLiveInfo(ContentItem item) {
        if (!"live".equals(type) || item == null || previewTitle == null) return;
        if (pendingInfoRunnable != null) previewHandler.removeCallbacks(pendingInfoRunnable);
        pendingInfoRunnable = () -> showLiveInfo(item);
        previewHandler.postDelayed(pendingInfoRunnable, 300L);
    }

    private void showLiveInfo(final ContentItem item) {
        previewItem = item;
        previewTitle.setText(item.name);
        if (liveAdapter != null) liveAdapter.setSelectedItem(item);
        if (infoLogo != null) {
            if (item.icon != null && !item.icon.isBlank()) {
                com.bumptech.glide.Glide.with(this).load(item.icon).centerInside()
                        .placeholder(R.drawable.akira_icon).into(infoLogo);
            } else {
                infoLogo.setImageResource(R.drawable.akira_icon);
            }
        }
        updatePreviewFavoriteButton();
        showEpgStatus("Carregando programação…");

        final String key = item.key();
        XtreamClient.getShortEpg(this, item.id, new XtreamClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject o) {
                if (previewItem == null || !key.equals(previewItem.key())) return;
                applyEpg(o);
            }

            @Override public void onError(Exception e) {
                if (previewItem == null || !key.equals(previewItem.key())) return;
                showEpgStatus("Programação indisponível para este canal");
            }
        });
    }

    /** Mostra uma mensagem no lugar da lista de programas. */
    private void showEpgStatus(String message) {
        if (infoEpgList != null) infoEpgList.removeAllViews();
        if (infoEpgStatus != null) {
            infoEpgStatus.setText(message);
            infoEpgStatus.setVisibility(View.VISIBLE);
        }
    }

    /** Lista os próximos programas; o que está no ar fica destacado, com barra de progresso. */
    private void applyEpg(JSONObject o) {
        if (infoEpgList == null) return;
        JSONArray a = o == null ? null : o.optJSONArray("epg_listings");
        if (a == null && o != null) a = o.optJSONArray("epg_list");

        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        long nowSec = System.currentTimeMillis() / 1000L;
        List<long[]> times = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject e = a.optJSONObject(i);
                if (e == null) continue;
                long start = e.optLong("start_timestamp", 0L);
                long stop = e.optLong("stop_timestamp", 0L);
                if (stop > 0 && nowSec >= stop) continue; // já terminou
                String title = EpgNowCache.decode(e.optString("title", ""));
                times.add(new long[]{start, stop});
                titles.add(title.isEmpty() ? "Sem título" : title);
            }
        }
        if (titles.isEmpty()) {
            showEpgStatus("Programação indisponível para este canal");
            return;
        }

        infoEpgList.removeAllViews();
        if (infoEpgStatus != null) infoEpgStatus.setVisibility(View.GONE);
        android.view.LayoutInflater inflater = android.view.LayoutInflater.from(this);
        for (int i = 0; i < titles.size() && i < 8; i++) {
            long start = times.get(i)[0];
            long stop = times.get(i)[1];
            boolean hasTimes = start > 0 && stop > start;
            boolean current = hasTimes && nowSec >= start && nowSec < stop;

            View row = inflater.inflate(R.layout.item_epg_row, infoEpgList, false);
            TextView time = row.findViewById(R.id.epgRowTime);
            TextView title = row.findViewById(R.id.epgRowTitle);
            android.widget.ProgressBar progress = row.findViewById(R.id.epgRowProgress);

            title.setText(titles.get(i));
            String range = hasTimes
                    ? fmt.format(new java.util.Date(start * 1000L)) + " – " + fmt.format(new java.util.Date(stop * 1000L))
                    : "";
            if (current) {
                long left = Math.max(1L, (stop - nowSec + 59L) / 60L);
                String remaining = left >= 60 ? (left / 60) + " h " + (left % 60) + " min" : left + " min";
                time.setText(range + "  •  AO VIVO  •  faltam " + remaining);
                time.setTextColor(0xFFF5D547);
                row.setBackgroundResource(R.drawable.bg_epg_current);
                progress.setProgress((int) (1000L * (nowSec - start) / (stop - start)));
                progress.setVisibility(View.VISIBLE);
            } else {
                time.setText(range);
            }
            infoEpgList.addView(row);
        }
    }

    private void ensurePreviewPlayer() {
        if (previewPlayer != null || !"live".equals(type)) return;

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 AkiraTV/1.7.0")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(25000);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(3500, 20000, 1000, 1800)
                .build();

        previewPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build();
        if (previewPlayerView != null) previewPlayerView.setPlayer(previewPlayer);

        previewPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    previewReady = true;
                    if (previewHint != null) previewHint.setVisibility(View.GONE);
                    if (previewInstruction != null) previewInstruction.setText("Preview ativo • OK novamente abre tela cheia • em tela cheia, OK muda a qualidade");
                    if (previewItem != null) {
                        Prefs.markRecent(CatalogActivity.this, previewItem);
                        updateCategoryCounts();
                    }
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                tryNextPreviewSource();
            }
        });
    }

    private void schedulePreview(ContentItem item) {
        if (!"live".equals(type) || item == null) return;
        if (pendingPreviewRunnable != null) previewHandler.removeCallbacks(pendingPreviewRunnable);
        pendingPreviewRunnable = () -> startPreview(item);
        previewHandler.postDelayed(pendingPreviewRunnable, PREVIEW_DELAY_MS);
    }

    private void startPreview(ContentItem item) {
        if (item == null || !"live".equals(type)) return;
        if (!adultSessionUnlocked && isAdultItem(item)) return;
        ContentItem desiredVariant = selectedVariantFor(item);
        if (desiredVariant == null) desiredVariant = item;
        if (previewItem != null && previewItem.key().equals(item.key()) && previewReady
                && previewVariant != null && previewVariant.id.equals(desiredVariant.id)) return;

        ensurePreviewPlayer();
        previewItem = item;
        previewVariant = desiredVariant;
        previewReady = false;
        previewFailed = false;
        previewCandidates.clear();
        previewCandidates.addAll(XtreamClient.liveCandidates(this, previewVariant.id, previewVariant.sourceUrl));
        previewCandidateIndex = 0;

        if (liveAdapter != null) liveAdapter.setSelectedItem(item);
        if (previewTitle != null) previewTitle.setText(item.name + " • " + LiveQualityUtil.qualityLabel(previewVariant.name));
        if (previewHint != null) {
            previewHint.setVisibility(View.VISIBLE);
            previewHint.setText("Carregando preview...");
        }
        if (previewInstruction != null) previewInstruction.setText("Aguarde o preview carregar • depois pressione OK para tela cheia");
        updatePreviewFavoriteButton();
        updatePreviewQualityButton();

        if (previewCandidates.isEmpty()) {
            if (previewHint != null) previewHint.setText("Preview indisponível");
            return;
        }
        playPreviewCandidate(0);
    }

    private void playPreviewCandidate(int index) {
        if (previewPlayer == null || index < 0 || index >= previewCandidates.size()) return;
        previewCandidateIndex = index;
        String url = previewCandidates.get(index);

        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8") || low.contains("format=m3u8") || low.contains("output=m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (low.contains(".ts")) {
            item.setMimeType(MimeTypes.VIDEO_MP2T);
        }

        previewReady = false;
        previewPlayer.stop();
        previewPlayer.clearMediaItems();
        previewPlayer.setMediaItem(item.build());
        previewPlayer.prepare();
        previewPlayer.play();
    }

    private void tryNextPreviewSource() {
        int next = previewCandidateIndex + 1;
        if (next < previewCandidates.size()) {
            if (previewHint != null) previewHint.setText("Tentando outra rota...");
            playPreviewCandidate(next);
        } else {
            previewReady = false;
            previewFailed = true;
            if (previewHint != null) {
                previewHint.setVisibility(View.VISIBLE);
                previewHint.setText("Não foi possível carregar o preview deste canal");
            }
            if (previewInstruction != null) previewInstruction.setText("Você ainda pode pressionar OK para tentar abrir o canal em tela cheia");
        }
    }

    private void handleLiveClick(ContentItem item) {
        if (item == null) return;
        if (previewItem == null || !previewItem.key().equals(item.key())) {
            startPreview(item);
            return;
        }
        if (previewFailed) {
            openLiveFullScreen(item);
            return;
        }
        if (!previewReady) {
            startPreview(item);
            Toast.makeText(this, "Aguarde o preview carregar e pressione OK novamente.", Toast.LENGTH_SHORT).show();
            return;
        }
        openLiveFullScreen(item);
    }

    private void focusPreviewActions() {
        if (previewEpgButton != null) previewEpgButton.requestFocus();
    }

    private void openPreviewEpg() {
        if (previewItem == null) {
            Toast.makeText(this, "Selecione um canal primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, EpgActivity.class);
        ContentItem epgVariant = selectedVariantFor(previewItem);
        intent.putExtra("stream_id", epgVariant == null ? previewItem.id : epgVariant.id);
        intent.putExtra("title", previewItem.name);
        startActivity(intent);
    }

    private void togglePreviewFavorite() {
        if (previewItem == null) {
            Toast.makeText(this, "Selecione um canal primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean favorite = Prefs.toggleFavorite(this, previewItem);
        Toast.makeText(this, favorite ? "Adicionado aos favoritos" : "Removido dos favoritos", Toast.LENGTH_SHORT).show();
        updatePreviewFavoriteButton();
    }

    private void updatePreviewFavoriteButton() {
        if (infoHeart != null) {
            boolean fav = previewItem != null && Prefs.isFavorite(this, previewItem);
            infoHeart.setImageResource(fav ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        }
        if (previewFavoriteButton == null) return;
        if (previewItem == null) {
            previewFavoriteButton.setText("Adicionar aos favoritos ♡");
        } else if (Prefs.isFavorite(this, previewItem)) {
            previewFavoriteButton.setText("Remover dos favoritos ♥");
        } else {
            previewFavoriteButton.setText("Adicionar aos favoritos ♡");
        }
    }

    private List<ContentItem> variantsFor(ContentItem displayItem) {
        if (displayItem == null) return new ArrayList<>();
        List<ContentItem> variants = liveVariantsByDisplayKey.get(displayItem.key());
        if (variants == null || variants.isEmpty()) {
            List<ContentItem> single = new ArrayList<>();
            single.add(displayItem);
            return single;
        }
        return variants;
    }

    private ContentItem selectedVariantFor(ContentItem displayItem) {
        if (displayItem == null) return null;
        ContentItem selected = liveSelectedVariantByDisplayKey.get(displayItem.key());
        if (selected != null) return selected;
        selected = LiveQualityUtil.pickDefault(variantsFor(displayItem));
        if (selected != null) liveSelectedVariantByDisplayKey.put(displayItem.key(), selected);
        return selected;
    }

    private void showQualityDialog(ContentItem displayItem) {
        if (displayItem == null) {
            Toast.makeText(this, "Selecione um canal primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ContentItem> variants = LiveQualityUtil.uniqueQualities(variantsFor(displayItem));
        if (variants.size() <= 1) {
            Toast.makeText(this, "Este canal possui apenas uma qualidade.", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentItem selected = selectedVariantFor(displayItem);
        String[] labels = new String[variants.size()];
        int checked = 0;
        for (int i = 0; i < variants.size(); i++) {
            ContentItem variant = variants.get(i);
            labels[i] = LiveQualityUtil.qualityLabel(variant.name);
            if (selected != null && selected.id.equals(variant.id)) checked = i;
        }

        new AlertDialog.Builder(this)
                .setTitle(displayItem.name + " • Qualidade")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    ContentItem chosen = variants.get(which);
                    liveSelectedVariantByDisplayKey.put(displayItem.key(), chosen);
                    dialog.dismiss();
                    startPreview(displayItem);
                    Toast.makeText(this, "Qualidade " + LiveQualityUtil.qualityLabel(chosen.name), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void updatePreviewQualityButton() {
        if (previewQualityButton == null) return;
        if (previewItem == null) {
            previewQualityButton.setText("Qualidade");
            previewQualityButton.setEnabled(false);
            return;
        }
        ContentItem selected = selectedVariantFor(previewItem);
        String q = selected == null ? "AUTO" : LiveQualityUtil.qualityLabel(selected.name);
        int count = LiveQualityUtil.uniqueQualities(variantsFor(previewItem)).size();
        previewQualityButton.setText(count > 1 ? "Qualidade " + q : q);
        previewQualityButton.setEnabled(count > 1);
    }

    private void openLiveFullScreen(ContentItem item) {
        ContentNavigator.open(this, item);
    }

    private void clearPreview() {
        if (!"live".equals(type)) return;
        if (pendingPreviewRunnable != null) previewHandler.removeCallbacks(pendingPreviewRunnable);
        previewReady = false;
        previewFailed = false;
        previewItem = null;
        previewVariant = null;
        previewCandidates.clear();
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.clearMediaItems();
        }
        if (liveAdapter != null) liveAdapter.setSelectedItem(null);
        if (previewTitle != null) previewTitle.setText("Nenhum canal selecionado");
        if (previewHint != null) {
            previewHint.setVisibility(View.VISIBLE);
            previewHint.setText("Selecione um canal para visualizar");
        }
        if (previewInstruction != null) previewInstruction.setText("Navegue pelos canais para ver o preview • OK novamente para tela cheia");
        updatePreviewFavoriteButton();
        updatePreviewQualityButton();
    }

    private void updatePosterGridGeometry() {
        if (!isPosterGrid() || posterAdapter == null || contentRecycler == null || contentRecycler.getWidth() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        int usable = Math.max(1, contentRecycler.getWidth() - contentRecycler.getPaddingLeft() - contentRecycler.getPaddingRight());
        int targetCell = Math.max(1, Math.round(155f * density));
        int spans = Math.max(2, Math.min(7, usable / targetCell));
        if (usable < Math.round(300f * density)) spans = 2;

        RecyclerView.LayoutManager lm = contentRecycler.getLayoutManager();
        if (lm instanceof GridLayoutManager) ((GridLayoutManager) lm).setSpanCount(spans);
        posterAdapter.updateGridGeometry(spans, Math.max(1, usable / spans));
    }

    private boolean isPosterGrid() { return "movie".equals(type) || "series".equals(type); }

    private void loadCategories() {
        rebuildCategories(null);

        JSONArray cached = CatalogCache.peekCategories(this, type);
        if (cached != null) rebuildCategories(cached);

        // Se o preload feito na home já deixou o cache fresco, não precisamos
        // esperar outra chamada de rede ao abrir a tela.
        if (cached != null && CatalogCache.isCategoriesFresh(this, type)) return;

        XtreamClient.getCategories(this, type, new XtreamClient.ArrayCallback() {
            @Override
            public void onSuccess(JSONArray array) {
                CatalogCache.saveCategories(CatalogActivity.this, type, array);
                rebuildCategories(array);
            }

            @Override
            public void onError(Exception error) {
                // Se existe cache, continuamos usando-o silenciosamente.
                if (CatalogCache.peekCategories(CatalogActivity.this, type) == null) applyCategoryFilter();
            }
        });
    }

    private void rebuildCategories(JSONArray array) {
        String previousCategory = currentCategory == null ? "" : currentCategory;
        allCategories.clear();
        categories.clear();
        adultCategoryIds.clear();

        allCategories.add(new CategoryItem("", "Todos", 0));
        allCategories.add(new CategoryItem(CAT_RECENT, "Assistidos recentemente", 0));
        if (isPosterGrid()) allCategories.add(new CategoryItem(CAT_CONTINUE, "Continuar assistindo", 0));

        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String id = object.optString("category_id");
                String rawName = object.optString("category_name", "Categoria");
                boolean adult = "live".equals(type) && isAdultCategoryName(rawName);
                if (adult) adultCategoryIds.add(id);
                allCategories.add(new CategoryItem(id, adult ? "🔒 " + rawName : rawName, 0));
            }
        }

        boolean previousStillExists = previousCategory.isBlank();
        for (CategoryItem c : allCategories) {
            if (c.id.equals(previousCategory)) {
                previousStillExists = true;
                break;
            }
        }
        if (!previousStillExists) previousCategory = "";

        currentCategory = previousCategory;
        updateCategoryCounts();
        applyCategoryFilter();

        String title = "Todos";
        for (CategoryItem c : allCategories) {
            if (c.id.equals(currentCategory)) {
                title = cleanCategoryName(c.name);
                break;
            }
        }
        selectedCategoryTitle.setText(title);
        categoriesRecycler.post(this::focusSelectedCategory);
    }

    private void applyCategoryFilter() {
        String query = categorySearch == null ? "" : categorySearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        categories.clear();
        for (CategoryItem item : allCategories) {
            String clean = cleanCategoryName(item.name).toLowerCase(Locale.ROOT);
            if (query.isBlank() || clean.contains(query)) categories.add(item);
        }
        if (categories.isEmpty() && !allCategories.isEmpty() && query.isBlank()) categories.addAll(allCategories);

        selectedCategoryPosition = indexOfDisplayedCategory(currentCategory);
        if (selectedCategoryPosition < 0) selectedCategoryPosition = 0;
        categoryAdapter.setSelected(currentCategory);
        categoryAdapter.notifyDataSetChanged();
    }

    private int indexOfDisplayedCategory(String id) {
        for (int i = 0; i < categories.size(); i++) if (categories.get(i).id.equals(id)) return i;
        return -1;
    }

    private void loadAllContent() {
        status.setText("Preparando catálogo • " + Prefs.listName(this) + "...");

        JSONArray cached = CatalogCache.peekContent(this, type);
        boolean hasCache = cached != null;
        if (hasCache) applyContentArray(cached);

        // O preload iniciado no login/home já deixou os dados prontos.
        if (hasCache && CatalogCache.isContentFresh(this, type)) return;

        XtreamClient.ArrayCallback callback = new XtreamClient.ArrayCallback() {
            @Override
            public void onSuccess(JSONArray array) {
                CatalogCache.saveContent(CatalogActivity.this, type, array);
                applyContentArray(array);
            }

            @Override
            public void onError(Exception error) {
                if (CatalogCache.peekContent(CatalogActivity.this, type) != null) {
                    // Mantém o cache já exibido, mesmo se a atualização falhar.
                    updateStatus();
                    return;
                }
                contentReady = true;
                status.setText("Falha ao carregar: " + error.getMessage());
                Toast.makeText(CatalogActivity.this, "Verifique DNS, usuário e senha.", Toast.LENGTH_LONG).show();
            }
        };

        if ("movie".equals(type)) XtreamClient.getMovies(this, callback);
        else if ("series".equals(type)) XtreamClient.getSeries(this, callback);
        else XtreamClient.getLive(this, callback);
    }

    private void applyContentArray(JSONArray array) {
        masterItems.clear();
        liveVariantsByDisplayKey.clear();
        liveSelectedVariantByDisplayKey.clear();
        liveCategoryIdsByDisplayKey.clear();

        List<ContentItem> rawItems = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                String id = "series".equals(type) ? object.optString("series_id") : object.optString("stream_id");
                String icon = "series".equals(type) ? object.optString("cover") : object.optString("stream_icon");
                String ext = object.optString("container_extension", "mp4");
                String categoryId = object.optString("category_id", "");
                String source = "live".equals(type) ? object.optString("direct_source", "") : "";
                rawItems.add(new ContentItem(id, object.optString("name", "Sem nome"), icon, type, ext, label(), categoryId, source));
            }
        }

        if ("live".equals(type)) {
            // Canais exatamente como o provedor entrega: um item por canal, sem agrupar qualidades.
            for (ContentItem raw : rawItems) {
                masterItems.add(raw);
                Set<String> categoryIds = new HashSet<>();
                if (raw.categoryId != null && !raw.categoryId.isBlank()) categoryIds.add(raw.categoryId);
                liveCategoryIdsByDisplayKey.put(raw.key(), categoryIds);
            }
        } else {
            masterItems.addAll(rawItems);
        }

        contentReady = true;
        updateCategoryCounts();
        applyCategoryFilter();
        applyFilter();
    }

    private void updateCategoryCounts() {
        if (!contentReady || allCategories.isEmpty()) return;
        Map<String, Integer> counts = new HashMap<>();
        for (ContentItem item : masterItems) {
            if ("live".equals(type)) {
                Set<String> ids = liveCategoryIdsByDisplayKey.get(item.key());
                if (ids == null || ids.isEmpty()) counts.put(item.categoryId, counts.getOrDefault(item.categoryId, 0) + 1);
                else for (String id : ids) counts.put(id, counts.getOrDefault(id, 0) + 1);
            } else {
                counts.put(item.categoryId, counts.getOrDefault(item.categoryId, 0) + 1);
            }
        }

        for (CategoryItem category : allCategories) {
            if (category.id.isBlank()) category.count = visibleAllItems().size();
            else if (CAT_RECENT.equals(category.id)) category.count = recentMatchingMaster().size();
            else if (CAT_CONTINUE.equals(category.id)) category.count = continueMatchingMaster().size();
            else category.count = counts.getOrDefault(category.id, 0);
        }
        categoryAdapter.notifyDataSetChanged();
    }

    private void selectCategoryInternal(CategoryItem category, int position) {
        boolean changed = !currentCategory.equals(category.id);
        currentCategory = category.id;
        selectedCategoryPosition = Math.max(0, position);
        categoryAdapter.setSelected(category.id);
        selectedCategoryTitle.setText(cleanCategoryName(category.name));
        applyFilter();
        if (changed && "live".equals(type)) clearPreview();
    }

    private void requestAdultPin(CategoryItem category, int position) {
        EditText input = new EditText(this);
        input.setHint("Senha de 4 dígitos");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        input.setSingleLine(true);
        input.setPadding(28, 16, 28, 16);

        new AlertDialog.Builder(this)
                .setTitle("Conteúdo protegido")
                .setMessage("Digite a senha para acessar os canais adultos. A senha padrão é 0000.")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Entrar", (dialog, which) -> {
                    if (Prefs.verifyAdultPin(this, input.getText().toString())) {
                        adultSessionUnlocked = true;
                        Toast.makeText(this, "Canais adultos desbloqueados nesta sessão.", Toast.LENGTH_SHORT).show();
                        updateCategoryCounts();
                        selectCategoryInternal(category, position);
                    } else {
                        Toast.makeText(this, "Senha incorreta.", Toast.LENGTH_SHORT).show();
                        focusSelectedCategory();
                    }
                })
                .show();
    }

    private void applyFilter() {
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();

        List<ContentItem> source;
        if (CAT_RECENT.equals(currentCategory)) source = recentMatchingMaster();
        else if (CAT_CONTINUE.equals(currentCategory)) source = continueMatchingMaster();
        else if (currentCategory.isBlank()) source = visibleAllItems();
        else source = masterItems;

        for (ContentItem item : source) {
            if (!adultSessionUnlocked && isAdultItem(item)) continue;
            boolean categoryMatches = currentCategory.isBlank()
                    || CAT_RECENT.equals(currentCategory)
                    || CAT_CONTINUE.equals(currentCategory)
                    || itemBelongsToCategory(item, currentCategory);
            boolean queryMatches = query.isBlank() || item.name.toLowerCase(Locale.ROOT).contains(query);
            if (categoryMatches && queryMatches) shown.add(item);
        }

        notifyContentChanged();
        if (contentRecycler != null) contentRecycler.scrollToPosition(0);
        updateStatus();

        if ("live".equals(type) && previewItem != null && !containsShown(previewItem)) clearPreview();
    }

    private boolean containsShown(ContentItem target) {
        if (target == null) return false;
        for (ContentItem item : shown) if (target.key().equals(item.key())) return true;
        return false;
    }

    private List<ContentItem> visibleAllItems() {
        List<ContentItem> out = new ArrayList<>();
        for (ContentItem item : masterItems) if (adultSessionUnlocked || !isAdultItem(item)) out.add(item);
        return out;
    }

    private List<ContentItem> recentMatchingMaster() {
        List<ContentItem> recents = Prefs.recent(this, type);
        Map<String, ContentItem> current = new HashMap<>();
        for (ContentItem item : masterItems) current.put(item.key(), item);
        List<ContentItem> out = new ArrayList<>();
        for (ContentItem recent : recents) {
            ContentItem item = current.get(recent.key());
            if (item != null && (adultSessionUnlocked || !isAdultItem(item))) out.add(item);
        }
        return out;
    }

    private List<ContentItem> continueMatchingMaster() {
        List<ContentItem> out = new ArrayList<>();
        if ("movie".equals(type)) {
            for (ContentItem item : masterItems) if (Prefs.hasUnfinishedProgress(this, item)) out.add(item);
            out.sort((a, b) -> Long.compare(Prefs.progressUpdatedAt(this, b), Prefs.progressUpdatedAt(this, a)));
        } else if ("series".equals(type)) {
            for (ContentItem item : masterItems) if (Prefs.hasSeriesProgress(this, item.id)) out.add(item);
            out.sort((a, b) -> Long.compare(Prefs.seriesProgressUpdatedAt(this, b.id), Prefs.seriesProgressUpdatedAt(this, a.id)));
        }
        return out;
    }

    private boolean isProtectedAdultCategory(String id) {
        return "live".equals(type) && id != null && adultCategoryIds.contains(id);
    }

    private boolean itemBelongsToCategory(ContentItem item, String categoryId) {
        if (item == null || categoryId == null) return false;
        if (!"live".equals(type)) return categoryId.equals(item.categoryId);
        Set<String> ids = liveCategoryIdsByDisplayKey.get(item.key());
        if (ids == null || ids.isEmpty()) return categoryId.equals(item.categoryId);
        return ids.contains(categoryId);
    }

    private boolean isAdultItem(ContentItem item) {
        if (item == null || !"live".equals(type)) return false;
        Set<String> ids = liveCategoryIdsByDisplayKey.get(item.key());
        if (ids == null || ids.isEmpty()) return adultCategoryIds.contains(item.categoryId);
        for (String id : ids) if (adultCategoryIds.contains(id)) return true;
        return false;
    }

    private static boolean isAdultCategoryName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a")
                .replace("é", "e").replace("ê", "e")
                .replace("í", "i")
                .replace("ó", "o").replace("ô", "o").replace("õ", "o")
                .replace("ú", "u");
        return n.contains("adult") || n.contains("+18") || n.contains("18+") || n.contains("xxx")
                || n.contains("erotic") || n.contains("porn") || n.contains("sexo") || n.contains("sex ");
    }

    private static String cleanCategoryName(String name) {
        if (name == null) return "Categoria";
        return name.startsWith("🔒 ") ? name.substring(3) : name;
    }

    private void notifyContentChanged() {
        if (liveAdapter != null) liveAdapter.notifyDataSetChanged();
        if (posterAdapter != null) posterAdapter.notifyDataSetChanged();
    }

    private void updateStatus() {
        if (!contentReady) return;
        String suffix = shown.size() == 1 ? " item" : " itens";
        if (CAT_CONTINUE.equals(currentCategory)) status.setText(shown.size() + suffix + " • retoma do ponto salvo automaticamente");
        else if (CAT_RECENT.equals(currentCategory)) status.setText(shown.size() + suffix + " • histórico dos últimos assistidos");
        else if (isPosterGrid()) status.setText(shown.size() + suffix + " • OK abre • segure OK para Favorito");
        else status.setText(shown.size() + suffix + " • OK abre em tela cheia • segure OK para opções");
    }

    private String label() { return "live".equals(type) ? "Canal" : "movie".equals(type) ? "Filme" : "Série"; }

    private void focusFirstContent() {
        if (shown.isEmpty()) return;
        contentRecycler.scrollToPosition(0);
        contentRecycler.post(() -> {
            RecyclerView.ViewHolder holder = contentRecycler.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
            else contentRecycler.requestFocus();
        });
    }

    private void focusSelectedCategory() {
        if (categories.isEmpty()) return;
        int position = Math.min(Math.max(0, selectedCategoryPosition), categories.size() - 1);
        categoriesRecycler.scrollToPosition(position);
        categoriesRecycler.post(() -> {
            RecyclerView.ViewHolder holder = categoriesRecycler.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
            else categoriesRecycler.requestFocus();
        });
    }

    private void context(ContentItem item) {
        List<String> options = new ArrayList<>();
        options.add(Prefs.isFavorite(this, item) ? "★ Remover dos favoritos" : "☆ Adicionar aos favoritos");
        if ("live".equals(item.type)) options.add("📅 Ver programação (EPG)");

        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        boolean favorite = Prefs.toggleFavorite(this, item);
                        Toast.makeText(this, favorite ? "Adicionado aos favoritos" : "Removido dos favoritos", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent intent = new Intent(this, EpgActivity.class);
                        ContentItem epgVariant = selectedVariantFor(item);
                        intent.putExtra("stream_id", epgVariant == null ? item.id : epgVariant.id);
                        intent.putExtra("title", item.name);
                        startActivity(intent);
                    }
                })
                .show();
    }

    private void openItem(ContentItem item) { ContentNavigator.open(this, item); }
}
