package com.akira.one;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

public class PlayerActivity extends BaseActivity {
    private ExoPlayer player;
    private TextView statusView;
    private Button qualityButton;
    private boolean playerControllerVisible = true;
    private String title;
    private final List<String> candidates = new ArrayList<>();
    private int candidateIndex = 0;
    private boolean endedByUser = false;
    private boolean resumeApplied = false;
    private boolean recentMarked = false;
    private boolean wasPlayingBeforePause = false;
    private boolean qualityDialogShowing = false;
    private final List<ContentItem> liveVariants = new ArrayList<>();
    private ContentItem currentLiveVariant;
    private static final long LIVE_STARTUP_TIMEOUT_MS = 8000L;
    private final Handler liveStartupHandler = new Handler(Looper.getMainLooper());
    private final Set<String> autoTriedVariantIds = new HashSet<>();
    private final Runnable liveStartupTimeout = new Runnable() {
        @Override public void run() {
            if (endedByUser || player == null || !"live".equals(contentType)) return;
            if (player.getPlaybackState() == Player.STATE_READY && player.isPlaying()) return;
            ContentItem fallback = nextAutoQuality();
            if (fallback != null) {
                String q = LiveQualityUtil.qualityLabel(fallback.name);
                Toast.makeText(PlayerActivity.this, "Canal demorou para abrir. Tentando " + q + "...", Toast.LENGTH_SHORT).show();
                switchLiveQualityInternal(fallback, false);
                return;
            }
            int next = candidateIndex + 1;
            if (next < candidates.size()) {
                showStatus("Tentando outra rota…");
                playCandidate(next);
            }
        }
    };

    private ContentItem contentItem;
    private String contentType = "";
    private String seriesId = "";
    private String seriesName = "";
    private String seriesCover = "";
    private String seriesCategoryId = "";

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (player != null && player.getPlaybackState() == Player.STATE_READY && isProgressContent()) {
                savePlaybackState(false);
            }
            progressHandler.postDelayed(this, 10000L);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);

        // Mantém o título fora do notch/câmera (só topo e início).
        final View overlay = findViewById(R.id.playerOverlay);
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (v, insets) -> {
            Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(safe.left, safe.top, 0, 0);
            return insets;
        });
        ViewCompat.requestApplyInsets(overlay);

        readContentMetadata();

        title = getIntent().getStringExtra("title");
        if (title == null || title.isBlank()) title = contentItem == null ? "Akira TV" : contentItem.name;
        statusView = findViewById(R.id.playerStatus);
        qualityButton = findViewById(R.id.qualityButton);
        setupControls();
        qualityButton.setVisibility(View.GONE);
        qualityButton.setOnClickListener(v -> showQualityDialog());

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 AkiraTV/1.7.10")
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(30000);
        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(5000, 30000, 1500, 2500)
                .build();

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setSeekBackIncrementMs(10000L)
                .setSeekForwardIncrementMs(10000L)
                .build();

        PlayerView view = findViewById(R.id.playerView);
        view.setPlayer(player);
        view.setControllerShowTimeoutMs(4000);
        // O botão HD fica dentro da barra de controles e acompanha a visibilidade dela.
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            playerControllerVisible = visibility == View.VISIBLE;
            updateQualityButton();
        });

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    cancelLiveStartupTimeout();
                    hideStatus();
                    markRecentWhenReady();
                    applySavedPositionOnce();
                } else if (playbackState == Player.STATE_ENDED) {
                    savePlaybackState(true);
                }
            }

            @Override public void onPlayerError(@NonNull PlaybackException error) {
                if (endedByUser) return;
                tryNextSource(error);
            }
        });

        String intentType = getIntent().getStringExtra("type");
        if ("live".equals(intentType) || "live".equals(contentType)) {
            liveVariants.addAll(LiveQualityUtil.variantsFromJson(value("live_quality_variants")));
            String selectedId = value("live_selected_variant_id");
            if (!liveVariants.isEmpty()) {
                currentLiveVariant = LiveQualityUtil.findById(liveVariants, selectedId);
                if (currentLiveVariant == null) currentLiveVariant = LiveQualityUtil.pickDefault(liveVariants);
                rebuildLiveCandidates(currentLiveVariant);
            } else {
                String streamId = getIntent().getStringExtra("stream_id");
                String direct = getIntent().getStringExtra("direct_source");
                if (streamId != null && !streamId.isBlank()) candidates.addAll(XtreamClient.liveCandidates(this, streamId, direct));
            }
        } else {
            String url = getIntent().getStringExtra("url");
            if (url != null && !url.isBlank()) candidates.add(url);
        }

        updateQualityButton();

        if (candidates.isEmpty()) {
            showStatus("Link indisponível");
            Toast.makeText(this, "Não foi possível montar o link deste conteúdo.", Toast.LENGTH_LONG).show();
        } else {
            playCandidate(0);
        }

        progressHandler.postDelayed(progressRunnable, 10000L);
    }

    /** Nome do conteúdo e selo "AO VIVO" ficam só na barra de controles; filmes mostram o tempo. */
    private void setupControls() {
        TextView controlTitle = findViewById(R.id.playerControlTitle);
        controlTitle.setText(title);
        boolean live = "live".equals(contentType) || "live".equals(getIntent().getStringExtra("type"));
        findViewById(R.id.playerLiveBadge).setVisibility(live ? View.VISIBLE : View.GONE);
        findViewById(R.id.playerTimeRow).setVisibility(live ? View.GONE : View.VISIBLE);
        findViewById(R.id.playerRewWrap).setVisibility(live ? View.GONE : View.VISIBLE);
        findViewById(R.id.playerFfwdWrap).setVisibility(live ? View.GONE : View.VISIBLE);
    }

    private void showStatus(String message) {
        if (statusView == null) return;
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        if (statusView != null) statusView.setVisibility(View.GONE);
    }

    private void readContentMetadata() {
        String id = value("content_id");
        String name = value("content_name");
        String icon = value("content_icon");
        contentType = value("content_type");
        String ext = value("content_extension");
        String meta = value("content_meta");
        String category = value("content_category_id");
        String source = value("content_source_url");

        if (contentType.isBlank()) {
            String intentType = value("type");
            if ("live".equals(intentType)) contentType = "live";
        }
        if (id.isBlank() && "live".equals(contentType)) id = value("stream_id");
        if (name.isBlank()) name = value("title");

        if (!id.isBlank()) contentItem = new ContentItem(id, name, icon, contentType, ext, meta, category, source);

        seriesId = value("series_id");
        seriesName = value("series_name");
        seriesCover = value("series_cover");
        seriesCategoryId = value("series_category_id");
    }

    private String value(String key) {
        String v = getIntent().getStringExtra(key);
        return v == null ? "" : v;
    }

    private void playCandidate(int index) {
        if (player == null || index < 0 || index >= candidates.size()) return;
        candidateIndex = index;
        String url = candidates.get(index);
        showStatus("Conectando…");

        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8") || low.contains("format=m3u8") || low.contains("output=m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        } else if (low.contains(".ts")) {
            item.setMimeType(MimeTypes.VIDEO_MP2T);
        }

        resumeApplied = false;
        player.stop();
        player.clearMediaItems();
        player.setMediaItem(item.build());
        player.prepare();
        player.play();
        scheduleLiveStartupTimeout();
    }

    private void applySavedPositionOnce() {
        if (resumeApplied || !isProgressContent() || contentItem == null || player == null) return;
        resumeApplied = true;
        long saved = Prefs.playbackPosition(this, contentItem);
        if (saved >= 5000) {
            player.seekTo(saved);
            Toast.makeText(this, "Continuando de " + formatTime(saved), Toast.LENGTH_SHORT).show();
        }
    }

    private void markRecentWhenReady() {
        if (recentMarked) return;
        recentMarked = true;
        if ("episode".equals(contentType)) {
            Prefs.markRecentSeries(this, seriesId, seriesName, seriesCover, seriesCategoryId);
        } else if (contentItem != null) {
            Prefs.markRecent(this, contentItem);
        }
    }

    private boolean isProgressContent() {
        return "movie".equals(contentType) || "episode".equals(contentType);
    }

    private void savePlaybackState(boolean ended) {
        if (!isProgressContent() || contentItem == null || player == null) return;
        long position = Math.max(0L, player.getCurrentPosition());
        long duration = player.getDuration();
        if (duration == C.TIME_UNSET || duration < 0) duration = 0L;
        if (ended && duration > 0) position = duration;

        Prefs.savePlaybackProgress(this, contentItem, position, duration);
        if ("episode".equals(contentType) && !seriesId.isBlank()) {
            Prefs.saveSeriesProgress(this, seriesId, seriesName, seriesCover, seriesCategoryId, contentItem, position, duration);
            Prefs.markRecentSeries(this, seriesId, seriesName, seriesCover, seriesCategoryId);
        }
    }

    private void tryNextSource(PlaybackException error) {
        int next = candidateIndex + 1;
        if (next < candidates.size()) {
            showStatus("Tentando outra rota…");
            statusView.postDelayed(() -> playCandidate(next), 450);
            return;
        }

        if ("live".equals(contentType)) {
            ContentItem fallback = nextAutoQuality();
            if (fallback != null) {
                String q = LiveQualityUtil.qualityLabel(fallback.name);
                Toast.makeText(this, "Essa qualidade falhou. Tentando " + q + "...", Toast.LENGTH_SHORT).show();
                switchLiveQualityInternal(fallback, false);
                return;
            }
        }

        String reason = error == null ? "erro desconhecido" : error.getErrorCodeName();
        showStatus("Não foi possível reproduzir");
        Toast.makeText(this, "Falha ao iniciar o conteúdo (" + reason + "). Tente novamente ou faça login de novo.", Toast.LENGTH_LONG).show();
    }

    private void rebuildLiveCandidates(ContentItem variant) {
        candidates.clear();
        candidateIndex = 0;
        if (variant == null) return;
        candidates.addAll(XtreamClient.liveCandidates(this, variant.id, variant.sourceUrl));
    }

    private void switchLiveQuality(ContentItem variant) {
        autoTriedVariantIds.clear();
        switchLiveQualityInternal(variant, true);
    }

    private void switchLiveQualityInternal(ContentItem variant, boolean showToast) {
        if (variant == null) return;
        cancelLiveStartupTimeout();
        currentLiveVariant = variant;
        if (variant.id != null && !variant.id.isBlank()) autoTriedVariantIds.add(variant.id);
        rebuildLiveCandidates(variant);
        if (candidates.isEmpty()) {
            Toast.makeText(this, "Qualidade indisponível.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (showToast) {
            Toast.makeText(this, "Qualidade " + LiveQualityUtil.qualityLabel(variant.name), Toast.LENGTH_SHORT).show();
        }
        updateQualityButton();
        playCandidate(0);
    }

    private void scheduleLiveStartupTimeout() {
        cancelLiveStartupTimeout();
        if (!"live".equals(contentType)) return;
        if (currentLiveVariant != null && currentLiveVariant.id != null && !currentLiveVariant.id.isBlank()) {
            autoTriedVariantIds.add(currentLiveVariant.id);
        }
        if (LiveQualityUtil.uniqueQualities(liveVariants).size() > 1) {
            liveStartupHandler.postDelayed(liveStartupTimeout, LIVE_STARTUP_TIMEOUT_MS);
        }
    }

    private void cancelLiveStartupTimeout() {
        liveStartupHandler.removeCallbacks(liveStartupTimeout);
    }

    private ContentItem nextAutoQuality() {
        List<ContentItem> variants = LiveQualityUtil.uniqueQualities(liveVariants);
        if (variants.size() <= 1) return null;

        String current = currentLiveVariant == null ? "AUTO" : LiveQualityUtil.qualityLabel(currentLiveVariant.name);
        String[] order;
        switch (current) {
            case "4K": order = new String[]{"FHD", "HD", "SD", "ORIGINAL"}; break;
            case "FHD": order = new String[]{"HD", "SD", "4K", "ORIGINAL"}; break;
            case "HD": order = new String[]{"SD", "FHD", "4K", "ORIGINAL"}; break;
            case "SD": order = new String[]{"HD", "FHD", "4K", "ORIGINAL"}; break;
            default: order = new String[]{"HD", "SD", "FHD", "4K", "ORIGINAL"}; break;
        }

        for (String wanted : order) {
            for (ContentItem item : variants) {
                if (!wanted.equals(LiveQualityUtil.qualityLabel(item.name))) continue;
                if (item.id != null && autoTriedVariantIds.contains(item.id)) continue;
                return item;
            }
        }
        return null;
    }

    private void updateQualityButton() {
        if (qualityButton == null) return;
        // Canais não são mais agrupados por qualidade: cada canal é aberto como vem da lista.
        if (liveVariants.isEmpty() || !"live".equals(contentType)) {
            qualityButton.setVisibility(View.GONE);
            return;
        }

        List<ContentItem> variants = LiveQualityUtil.uniqueQualities(liveVariants);
        if (!playerControllerVisible) {
            qualityButton.setVisibility(View.GONE);
            return;
        }
        qualityButton.setVisibility(View.VISIBLE);
        String quality = currentLiveVariant == null ? "AUTO" : LiveQualityUtil.qualityLabel(currentLiveVariant.name);
        qualityButton.setText("HD");
        qualityButton.setEnabled(variants.size() > 1);
        qualityButton.setAlpha(variants.size() > 1 ? 1f : 0.65f);
        qualityButton.setContentDescription(variants.size() > 1
                ? "Mudar qualidade do canal. Qualidade atual " + quality
                : "Canal disponível somente em " + quality);
    }

    private void showQualityDialog() {
        List<ContentItem> variants = LiveQualityUtil.uniqueQualities(liveVariants);
        if (variants.size() <= 1) {
            Toast.makeText(this, "Este canal possui apenas uma qualidade.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[variants.size()];
        int checked = 0;
        for (int i = 0; i < variants.size(); i++) {
            ContentItem v = variants.get(i);
            labels[i] = LiveQualityUtil.qualityLabel(v.name);
            if (currentLiveVariant != null && currentLiveVariant.id.equals(v.id)) checked = i;
        }
        qualityDialogShowing = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title + " • Qualidade")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    ContentItem chosen = variants.get(which);
                    d.dismiss();
                    switchLiveQuality(chosen);
                })
                .setNegativeButton("Cancelar", null)
                .create();
        dialog.setOnDismissListener(d -> qualityDialogShowing = false);
        dialog.show();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)
                && "live".equals(contentType)
                && LiveQualityUtil.uniqueQualities(liveVariants).size() > 1
                && !qualityDialogShowing) {
            showQualityDialog();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onPause() {
        if (player != null) {
            savePlaybackState(false);
            wasPlayingBeforePause = player.isPlaying();
            player.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && wasPlayingBeforePause) {
            wasPlayingBeforePause = false;
            player.play();
        }
    }

    @Override
    protected void onDestroy() {
        endedByUser = true;
        progressHandler.removeCallbacksAndMessages(null);
        cancelLiveStartupTimeout();
        if (player != null) {
            savePlaybackState(false);
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private static String formatTime(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }
}
