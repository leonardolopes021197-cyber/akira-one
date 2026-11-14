package com.akira.one;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PosterGridAdapter extends RecyclerView.Adapter<PosterGridAdapter.Holder> {
    public interface Listener {
        void onClick(ContentItem item);
        void onLongClick(ContentItem item);
        void onMoveLeft();
    }

    private final List<ContentItem> items;
    private final Listener listener;
    private int spanCount;
    private int cellWidthPx;

    public PosterGridAdapter(List<ContentItem> items, int spanCount, Listener listener) {
        this.items = items;
        this.spanCount = Math.max(1, spanCount);
        this.listener = listener;
    }

    public void updateGridGeometry(int spanCount, int cellWidthPx) {
        int nextSpan = Math.max(1, spanCount);
        int nextWidth = Math.max(0, cellWidthPx);
        if (this.spanCount == nextSpan && this.cellWidthPx == nextWidth) return;
        this.spanCount = nextSpan;
        this.cellWidthPx = nextWidth;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster_grid, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ContentItem item = items.get(position);
        h.name.setText(item.name);
        h.meta.setText(metaLabel(h.itemView, item));

        // A capa preserva uma proporção próxima de 2:3 e se adapta à largura
        // real de cada célula, evitando distorção em celular, TV e 4K.
        if (cellWidthPx > 0) {
            float density = h.itemView.getResources().getDisplayMetrics().density;
            int horizontalChrome = Math.round(22f * density); // margem + padding da célula
            int innerWidth = Math.max(Math.round(82f * density), cellWidthPx - horizontalChrome);
            int posterHeight = Math.round(innerWidth * 1.48f);
            int minHeight = Math.round(120f * density);
            int maxHeight = Math.round(330f * density);
            posterHeight = Math.max(minHeight, Math.min(maxHeight, posterHeight));
            ViewGroup.LayoutParams params = h.posterFrame.getLayoutParams();
            if (params.height != posterHeight) {
                params.height = posterHeight;
                h.posterFrame.setLayoutParams(params);
            }
        }

        if (item.icon != null && !item.icon.isBlank()) {
            Glide.with(h.poster).load(item.icon).centerCrop().placeholder(R.drawable.akira_icon).into(h.poster);
        } else {
            h.poster.setImageResource(R.drawable.akira_icon);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(item));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(item);
            return true;
        });
        h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.035f : 1f).scaleY(hasFocus ? 1.035f : 1f).setDuration(100).start();
            h.overlay.setVisibility(hasFocus ? View.VISIBLE : View.GONE);
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                int adapterPosition = h.getBindingAdapterPosition();
                if (adapterPosition != RecyclerView.NO_POSITION && adapterPosition % Math.max(1, spanCount) == 0) {
                    listener.onMoveLeft();
                    return true;
                }
            }
            return false;
        });
    }

    private String metaLabel(View view, ContentItem item) {
        if ("movie".equals(item.type)) {
            if (Prefs.hasUnfinishedProgress(view.getContext(), item)) {
                long pos = Prefs.playbackPosition(view.getContext(), item);
                long dur = Prefs.playbackDuration(view.getContext(), item);
                if (dur > 0) return "▶ Continuar • " + Math.min(99, Math.max(1, (pos * 100 / dur))) + "%";
                return "▶ Continuar";
            }
            return item.extension == null || item.extension.isBlank() ? "Filme" : item.extension.toUpperCase();
        }
        if ("series".equals(item.type)) {
            org.json.JSONObject progress = Prefs.seriesProgress(view.getContext(), item.id);
            if (progress != null) {
                if (progress.optBoolean("completed", false)) return "✓ Último episódio salvo";
                return "▶ Continuar série";
            }
            return "Série";
        }
        return "Akira";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final FrameLayout posterFrame;
        final ImageView poster;
        final TextView name;
        final TextView meta;
        final View overlay;

        Holder(View view) {
            super(view);
            posterFrame = view.findViewById(R.id.gridPosterFrame);
            poster = view.findViewById(R.id.gridPoster);
            name = view.findViewById(R.id.gridTitle);
            meta = view.findViewById(R.id.gridMeta);
            overlay = view.findViewById(R.id.gridFocusOverlay);
        }
    }
}
