package com.akira.one;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class ContentAdapter extends RecyclerView.Adapter<ContentAdapter.Holder> {
    public interface Listener {
        void onClick(ContentItem item);
        void onLongClick(ContentItem item);
        default void onMoveLeft() {}
    }

    private final List<ContentItem> items;
    private final Listener listener;

    public ContentAdapter(List<ContentItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_content, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int p) {
        ContentItem item = items.get(p);
        h.name.setText(item.name);
        h.meta.setText(item.meta == null || item.meta.isBlank() ? label(item.type) : item.meta);
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
        h.itemView.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.025f : 1f).scaleY(has ? 1.025f : 1f).setDuration(120).start();
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                listener.onMoveLeft();
                return true;
            }
            return false;
        });
    }

    private String label(String t) {
        if ("live".equals(t)) return "TV ao Vivo";
        if ("movie".equals(t)) return "Filme";
        if ("series".equals(t)) return "Série";
        if ("episode".equals(t)) return "Episódio";
        return "Akira";
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        ImageView poster;
        TextView name, meta;
        Holder(View v) {
            super(v);
            poster = v.findViewById(R.id.poster);
            name = v.findViewById(R.id.name);
            meta = v.findViewById(R.id.meta);
        }
    }
}
