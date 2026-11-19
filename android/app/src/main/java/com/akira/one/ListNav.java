package com.akira.one;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Navegação por controle remoto em listas (RecyclerView) sem "vazar" o foco.
 *
 * O problema: ao segurar a seta, o próximo item ainda não foi criado pelo RecyclerView, então a
 * busca de foco do Android não acha nada ali e salta para outra coisa da tela (menu lateral,
 * botões...). Aqui o movimento é feito à mão: rola até o item e foca nele assim que existir.
 */
final class ListNav {
    /**
     * EDITE AQUI: intervalo mínimo entre movimentos ao SEGURAR a seta (em milissegundos).
     * Maior = mais lento e suave; menor = mais rápido. Um toque simples nunca é limitado.
     */
    static final long REPEAT_INTERVAL_MS = 110L;

    private static long lastMoveAt = 0L;

    private ListNav() {}

    /** Segurando a seta, deixa passar só um movimento a cada REPEAT_INTERVAL_MS (navegação suave). */
    static boolean allow(android.view.KeyEvent event) {
        long now = android.os.SystemClock.uptimeMillis();
        if (event != null && event.getRepeatCount() > 0 && now - lastMoveAt < REPEAT_INTERVAL_MS) return false;
        lastMoveAt = now;
        return true;
    }

    /**
     * Anda {@code delta} itens a partir de {@code fromPos}.
     * Retorna o valor para o OnKeyListener: true = tecla consumida; false = deixa o Android
     * procurar foco fora da lista (só acontece ao tentar sair pelo início).
     */
    static boolean step(RecyclerView rv, int fromPos, int delta, android.view.KeyEvent event) {
        RecyclerView.Adapter<?> adapter = rv.getAdapter();
        if (adapter == null || fromPos == RecyclerView.NO_POSITION) return true;
        int target = fromPos + delta;
        if (target < 0) return false;                      // início da lista: pode sair
        if (target >= adapter.getItemCount()) return true; // fim da lista: segura o foco aqui
        if (!allow(event)) return true;                    // rápido demais: consome sem mover
        focus(rv, target);
        return true;
    }

    /** Foca o item da posição, rolando até ele se ainda não estiver na tela. */
    static void focus(RecyclerView rv, int position) {
        focus(rv, position, 0);
    }

    private static void focus(RecyclerView rv, int position, int attempt) {
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
        if (vh != null) {
            vh.itemView.requestFocus();
            return;
        }
        if (attempt == 0) rv.scrollToPosition(position);
        if (attempt < 10) rv.postDelayed(() -> focus(rv, position, attempt + 1), 16L);
    }

    /** RecyclerView pai da linha (ou null). */
    static RecyclerView parentList(android.view.View item) {
        return item.getParent() instanceof RecyclerView ? (RecyclerView) item.getParent() : null;
    }
}
