package com.akira.one;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Tela de carregamento exibida depois do login enquanto TV ao vivo, Filmes e
 * Séries são atualizados. O progresso é real: conta os 6 downloads do
 * {@link CatalogCache} (categorias + conteúdo de cada seção).
 */
public class LoadingActivity extends BaseActivity {
    private static final long POLL_MS = 250L;
    private static final long MIN_SHOW_MS = 1500L;     // evita "piscar" quando já está tudo em cache
    private static final long SKIP_AFTER_MS = 10000L;  // libera o botão "Continuar sem esperar"
    private static final long TIMEOUT_MS = 60000L;     // segue para a home; o download continua em segundo plano
    private static final int TOTAL = 6;

    private static final String[] TYPES = {"live", "movie", "series"};
    private static final String[] LABELS = {"TV ao vivo", "filmes", "séries"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ProgressBar bar;
    private TextView status;
    private TextView percent;
    private View skip;
    private ObjectAnimator pulse;

    private long startedAt;
    private int idlePolls = 0;
    private int shownTarget = 0;
    private boolean leaving = false;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (leaving) return;
            tick();
            if (!leaving) handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Prefs.hasActivation(this)) {
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
            return;
        }

        setContentView(R.layout.activity_loading);
        bar = findViewById(R.id.loadingBar);
        status = findViewById(R.id.loadingStatus);
        percent = findViewById(R.id.loadingPercent);
        skip = findViewById(R.id.loadingSkip);
        skip.setOnClickListener(v -> goHome());

        View logo = findViewById(R.id.loadingLogo);
        pulse = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.65f, 1f);
        pulse.setDuration(900);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();

        startedAt = System.currentTimeMillis();
        // force=true: baixa de novo as listas ao entrar, assim a playlist sempre abre atualizada.
        CatalogCache.preloadAll(this, true);
        handler.post(poll);
    }

    private void tick() {
        long elapsed = System.currentTimeMillis() - startedAt;
        int done = CatalogCache.updatedSince(this, startedAt);
        int pending = CatalogCache.inFlightCount(this);

        animateProgressTo(done * 100);
        status.setText(currentLabel());
        if (elapsed >= SKIP_AFTER_MS && skip.getVisibility() != View.VISIBLE) {
            skip.setVisibility(View.VISIBLE);
        }

        // "Parado" = nada em andamento. Exige duas leituras seguidas para evitar a
        // janela entre remover o download da fila e gravar o resultado.
        idlePolls = pending == 0 ? idlePolls + 1 : 0;
        boolean finishedLoading = idlePolls >= 2 && elapsed >= MIN_SHOW_MS;
        boolean timedOut = elapsed >= TIMEOUT_MS;
        if (!finishedLoading && !timedOut) return;

        leaving = true;
        handler.removeCallbacks(poll);
        if (done >= TOTAL) {
            animateProgressTo(TOTAL * 100);
            status.setText("Tudo pronto!");
            handler.postDelayed(this::goHome, 500);
        } else {
            status.setText(timedOut
                    ? "Continuando… o restante termina em segundo plano."
                    : "Alguns itens não foram atualizados. Continuando…");
            handler.postDelayed(this::goHome, 1200);
        }
    }

    /** Mensagem da primeira seção que ainda não terminou (TV → Filmes → Séries). */
    private String currentLabel() {
        for (int i = 0; i < TYPES.length; i++) {
            if (CatalogCache.updatedSince(this, TYPES[i], startedAt) < 2) {
                return "Atualizando " + LABELS[i] + "…";
            }
        }
        return "Finalizando…";
    }

    private void animateProgressTo(int target) {
        if (target <= shownTarget) return;
        shownTarget = target;
        ObjectAnimator.ofInt(bar, "progress", bar.getProgress(), target).setDuration(450).start();
        percent.setText(Math.min(100, Math.round(target * 100f / (TOTAL * 100))) + "%");
    }

    private void goHome() {
        if (isFinishing() || isDestroyed()) return;
        leaving = true;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        leaving = true;
        handler.removeCallbacksAndMessages(null);
        if (pulse != null) pulse.cancel();
        super.onDestroy();
    }
}
