package com.akira.one;

import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Home da Akira TV (layout nativo, v1.8).
 *
 * Menu lateral, banner, atalhos (TV/Filmes/Séries) e o carrossel
 * "Lançamentos" são views reais: adaptam a qualquer tela, aceitam toque e
 * controle remoto, e os pôsteres vêm do catálogo do próprio usuário.
 */
public class MainActivity extends BaseActivity {
    private View root;
    private View homeContent;
    private TextView expiryText;

    private RecyclerView launchRecycler;
    private TextView launchEmpty;
    private HomePosterAdapter launchAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int launchAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Prefs.directMode(this) && !Prefs.hasActivation(this)) {
            openLogin();
            return;
        }

        // Aquece o cache salvo e atualiza o catálogo em segundo plano enquanto
        // o usuário navega pela home. Assim TV/Filmes/Séries abrem muito mais rápido.
        CatalogCache.warmFromDisk(this);
        CatalogCache.preloadAll(this, false);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.rootMain);
        homeContent = findViewById(R.id.homeContent);
        expiryText = findViewById(R.id.expiryText);
        enterImmersiveMode();
        updateExpiryLabel();
        if (Prefs.hasActivation(this)) {
            ServerPool.refreshExpiry(this, this::updateExpiryLabel);
        }

        applySafeInsets();
        applyClockPref();
        setupHero();
        setupLaunchRow();
        bindActions();
        findViewById(R.id.navHome).setSelected(true);
        findViewById(R.id.navHome).requestFocus();
        loadLaunchRow();
        UpdateManager.checkOnStartup(this);
    }

    /** Mantém o conteúdo fora do notch/câmera mesmo em modo imersivo. */
    /** Configurações > Ocultar relógio. */
    private void applyClockPref() {
        View clock = findViewById(R.id.homeClock);
        if (clock != null) clock.setVisibility(Prefs.hideClock(this) ? View.GONE : View.VISIBLE);
    }

    private void applySafeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            homeContent.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Banner em "center crop" com foco na parte de baixo da arte, assim o
     * logo e a frase continuam visíveis mesmo em telas baixas e largas.
     */
    private void setupHero() {
        final ImageView hero = findViewById(R.id.homeHero);
        hero.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                applyHeroCrop(hero);
            }
        });
    }

    private void applyHeroCrop(ImageView hero) {
        Drawable d = hero.getDrawable();
        int vw = hero.getWidth();
        int vh = hero.getHeight();
        if (d == null || vw <= 0 || vh <= 0) return;
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        float scale = Math.max(vw / (float) dw, vh / (float) dh);
        float dx = (vw - dw * scale) / 2f;
        float dy = (vh - dh * scale) * 0.75f; // 0 = topo, 0.5 = centro, 1 = base
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(dx, dy);
        hero.setScaleType(ImageView.ScaleType.MATRIX);
        hero.setImageMatrix(m);
    }

    // ---------------------------------------------------------------- Lançamentos

    private void setupLaunchRow() {
        launchRecycler = findViewById(R.id.launchRecycler);
        launchEmpty = findViewById(R.id.launchEmpty);
        launchAdapter = new HomePosterAdapter(item -> {
            if (requireActivation()) ContentNavigator.open(MainActivity.this, item);
        });
        launchRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        launchRecycler.setAdapter(launchAdapter);
        launchRecycler.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            final int inner = (bottom - top) - launchRecycler.getPaddingTop() - launchRecycler.getPaddingBottom();
            launchRecycler.post(() -> launchAdapter.setPosterHeight(inner));
        });
    }

    /** Lê os filmes do cache (em segundo plano) e tenta de novo enquanto o catálogo carrega. */
    private void loadLaunchRow() {
        if (launchAdapter == null) return;
        if (!Prefs.hasActivation(this)) {
            launchEmpty.setText("Entre com sua conta para ver os lançamentos");
            launchEmpty.setVisibility(View.VISIBLE);
            return;
        }
        final Context app = getApplicationContext();
        new Thread(() -> {
            final List<ContentItem> items = buildLaunchItems(app);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (!items.isEmpty()) {
                    launchAdapter.setItems(items);
                    launchEmpty.setVisibility(View.GONE);
                } else if (launchAttempts < 10) {
                    launchAttempts++;
                    handler.postDelayed(this::loadLaunchRow, 2500);
                } else {
                    launchEmpty.setText("Abra Filmes para explorar o catálogo");
                    launchEmpty.setVisibility(View.VISIBLE);
                }
            });
        }, "akira-home-row").start();
    }

    /** Os 16 filmes mais recentes (campo "added" do Xtream) que tenham capa. */
    private static List<ContentItem> buildLaunchItems(Context app) {
        List<ContentItem> out = new ArrayList<>();
        JSONArray all = CatalogCache.peekContent(app, "movie");
        if (all == null || all.length() == 0) return out;

        final int want = 16;
        long[] bestAdded = new long[want];
        JSONObject[] best = new JSONObject[want];
        int count = 0;

        // Percorre do fim para o começo: em empate (sem "added") ficam os últimos itens do servidor.
        for (int i = all.length() - 1; i >= 0; i--) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            String icon = o.optString("stream_icon", "");
            if (!icon.startsWith("http")) continue;

            long added = 0;
            try {
                added = Long.parseLong(o.optString("added", "0").trim());
            } catch (Exception ignored) {}

            int pos;
            if (count < want) {
                pos = count;
                count++;
            } else if (added > bestAdded[want - 1]) {
                pos = want - 1;
            } else {
                continue;
            }
            while (pos > 0 && bestAdded[pos - 1] < added) {
                bestAdded[pos] = bestAdded[pos - 1];
                best[pos] = best[pos - 1];
                pos--;
            }
            bestAdded[pos] = added;
            best[pos] = o;
        }

        for (int i = 0; i < count; i++) {
            JSONObject o = best[i];
            if (o == null) continue;
            out.add(new ContentItem(
                    o.optString("stream_id"),
                    o.optString("name", "Sem nome"),
                    o.optString("stream_icon"),
                    "movie",
                    o.optString("container_extension", "mp4"),
                    "Filme",
                    o.optString("category_id"),
                    ""));
        }
        return out;
    }

    private void updateExpiryLabel() {
        if (expiryText == null) return;
        expiryText.setText("Vencimento: " + Prefs.expiryDisplay(this));
    }

    private void bindActions() {
        // Navegação só pelo menu da esquerda.
        findViewById(R.id.navHome).setOnClickListener(v -> { /* já está na Início */ });
        findViewById(R.id.navSearch).setOnClickListener(v -> openSearch());
        findViewById(R.id.navLive).setOnClickListener(v -> openCatalog("live"));
        findViewById(R.id.navMovies).setOnClickListener(v -> openCatalog("movie"));
        findViewById(R.id.navSeries).setOnClickListener(v -> openCatalog("series"));
        findViewById(R.id.navFavorites).setOnClickListener(v -> openFavorites());
        findViewById(R.id.navSettings).setOnClickListener(v -> openSettings());
        findViewById(R.id.moviesAllButton).setOnClickListener(v -> openCatalog("movie"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        updateExpiryLabel();
        if (Prefs.directMode(this)) {
            if (!Prefs.hasActivation(this)) openLogin();
        } else {
            connectPanel();
        }
        applyClockPref();
        if (launchAdapter != null && launchAdapter.getItemCount() == 0) {
            handler.removeCallbacksAndMessages(null);
            launchAttempts = 0;
            loadLaunchRow();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        enableTrueFullscreen();
    }

    private void connectPanel() {
        ApiClient.register(this, new ApiClient.JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                String token = json.optString("device_token", "");
                if (!token.isBlank()) Prefs.setDeviceToken(MainActivity.this, token);
                loadConfig();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this,
                        "Painel indisponível: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadConfig() {
        ApiClient.fetchConfig(this, new ApiClient.JsonCallback() {
            @Override
            public void onSuccess(JSONObject json) {
                JSONArray lists = json.optJSONArray("activations");
                if (lists != null) Prefs.saveLists(MainActivity.this, lists);

                if (json.optBoolean("active", false)) {
                    JSONObject activation = json.optJSONObject("activation");
                    if (activation != null) {
                        Prefs.saveActivation(MainActivity.this,
                                activation.optString("base_url"),
                                activation.optString("username"),
                                activation.optString("password"),
                                activation.optString("list_name", "Principal"));
                        ServerPool.refreshExpiry(MainActivity.this, MainActivity.this::updateExpiryLabel);
                        CatalogCache.warmFromDisk(MainActivity.this);
                        CatalogCache.preloadAll(MainActivity.this, false);
                    }
                } else {
                    Prefs.clearActivation(MainActivity.this);
                }

                JSONObject update = json.optJSONObject("update");
                if (update != null) UpdateManager.check(MainActivity.this, update);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this,
                        "Erro de ativação: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openCatalog(String type) {
        if (!requireActivation()) return;
        Intent i = new Intent(this, CatalogActivity.class);
        i.putExtra("type", type);
        startActivity(i);
    }

    private void openFavorites() {
        if (requireActivation()) startActivity(new Intent(this, FavoritesActivity.class));
    }

    private void openSearch() {
        if (requireActivation()) startActivity(new Intent(this, SearchActivity.class));
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private boolean requireActivation() {
        if (!Prefs.hasActivation(this)) {
            Toast.makeText(this, "Entre com seu usuário e senha para continuar.", Toast.LENGTH_LONG).show();
            openLogin();
            return false;
        }
        return true;
    }

    private void openLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.putExtra("force_login", true);
        startActivity(i);
        finish();
    }
}
