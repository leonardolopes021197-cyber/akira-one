package com.akira.one;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Carrossel "Lançamentos" da home. Cada cartão tem proporção 2:3 e acompanha
 * a altura disponível da faixa, então funciona em celular, TV 1080p e 4K.
 */
public class HomePosterAdapter extends RecyclerView.Adapter<HomePosterAdapter.Holder> {
    public interface Listener {
        void onClick(ContentItem item);
    }

    private final List<ContentItem> items = new ArrayList<>();
    private final Listener listener;
    private int posterHeightPx = 0;

    public HomePosterAdapter(Listener listener) {
        this.listener = listener;
    }

    /** Altura útil da faixa (sem padding). A largura vira 2/3 dela. */
    public void setPosterHeight(int px) {
        // Ignora variações de 1px (arredondamento) para não refazer a lista à toa.
        if (px <= 0 || Math.abs(px - posterHeightPx) < 2) return;
        posterHeightPx = px;
        notifyDataSetChanged();
    }

    public void setItems(List<ContentItem> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_poster, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ContentItem item = items.get(position);
        h.title.setText(item.name);

        if (posterHeightPx > 0) {
            ViewGroup.LayoutParams lp = h.itemView.getLayoutParams();
            int width = Math.round(posterHeightPx * 2f / 3f);
            if (lp != null && lp.width != width) {
                lp.width = width;
                h.itemView.setLayoutParams(lp);
            }
        }

        if (item.icon != null && !item.icon.isBlank()) {
            Glide.with(h.poster).load(item.icon).centerCrop().placeholder(R.drawable.akira_icon).into(h.poster);
        } else {
            h.poster.setImageResource(R.drawable.akira_icon);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(item));
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                RecyclerView rv = ListNav.parentList(v);
                if (rv != null) return ListNav.step(rv, h.getBindingAdapterPosition(), keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title;

        Holder(View view) {
            super(view);
            poster = view.findViewById(R.id.homePoster);
            title = view.findViewById(R.id.homePosterTitle);
        }
    }
}
