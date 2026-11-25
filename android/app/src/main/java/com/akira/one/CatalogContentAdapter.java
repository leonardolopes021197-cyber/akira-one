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

/**
 * Lista de canais ao vivo: número, logo, nome e, embaixo do nome em fonte menor,
 * o programa que está passando agora (buscado só para os canais visíveis).
 */
public class CatalogContentAdapter extends RecyclerView.Adapter<CatalogContentAdapter.Holder> {
    public interface Listener {
        void onClick(ContentItem item);
        void onLongClick(ContentItem item);
        void onFocus(ContentItem item);
        void onMoveLeft();
        void onMoveRight();
    }

    private final List<ContentItem> items;
    private final Listener listener;
    private String selectedKey = "";

    public CatalogContentAdapter(List<ContentItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelectedItem(ContentItem item) {
        String next = item == null ? "" : item.key();
        if (selectedKey.equals(next)) return;
        String old = selectedKey;
        selectedKey = next;
        int oldPos = positionOf(old);
        int newPos = positionOf(selectedKey);
        if (oldPos >= 0) notifyItemChanged(oldPos);
        if (newPos >= 0) notifyItemChanged(newPos);
    }

    private int positionOf(String key) {
        if (key == null || key.isBlank()) return -1;
        for (int i = 0; i < items.size(); i++) if (key.equals(items.get(i).key())) return i;
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_catalog_row, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ContentItem item = items.get(position);
        h.boundKey = item.key();
        h.number.setText(String.valueOf(position + 1));
        h.name.setText(item.name);
        String quality = qualityTag(item.name);
        if (quality.isEmpty()) {
            h.quality.setVisibility(View.GONE);
        } else {
            h.quality.setText(quality);
            h.quality.setTextColor(qualityColor(quality));
            h.quality.setVisibility(View.VISIBLE);
        }
        h.itemView.setSelected(selectedKey.equals(item.key()));

        // "O que está passando": usa o cache e, se faltar, pede (só enquanto o item estiver na tela).
        final android.content.Context ctx = h.itemView.getContext();
        String cached = EpgNowCache.peek(ctx, item.id);
        if (cached != null) {
            h.meta.setText(cached);
        } else {
            h.meta.setText("");
            final String key = item.key();
            EpgNowCache.request(ctx, item.id, title -> {
                if (key.equals(h.boundKey)) h.meta.setText(title);
            });
        }

        if (item.icon != null && !item.icon.isBlank()) {
            Glide.with(h.icon).load(item.icon).centerInside().placeholder(R.drawable.akira_icon).into(h.icon);
        } else {
            h.icon.setImageResource(R.drawable.akira_icon);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(item));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(item);
            return true;
        });
        h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.012f : 1f).scaleY(hasFocus ? 1.012f : 1f).setDuration(100).start();
            if (hasFocus) listener.onFocus(item);
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                RecyclerView rv = ListNav.parentList(v);
                if (rv != null) return ListNav.step(rv, h.getBindingAdapterPosition(), keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1, event);
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                listener.onMoveLeft();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                listener.onMoveRight();
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    /** Qualidade lida do nome do canal (4K, FHD, HD ou SD); vazio se o nome não diz. */
    static String qualityTag(String name) {
        if (name == null) return "";
        String n = name.toUpperCase(java.util.Locale.ROOT);
        if (java.util.regex.Pattern.compile("\\b(4K|UHD)\\b").matcher(n).find()) return "4K";
        if (java.util.regex.Pattern.compile("\\b(FHD|FULL ?HD)\\b").matcher(n).find()) return "FHD";
        if (java.util.regex.Pattern.compile("\\bHD\\b").matcher(n).find()) return "HD";
        if (java.util.regex.Pattern.compile("\\bSD\\b").matcher(n).find()) return "SD";
        return "";
    }

    private static int qualityColor(String tag) {
        switch (tag) {
            case "4K": return 0xFFF5D547;
            case "FHD": return 0xFF3AA8FF;
            case "HD": return 0xFFDDE4F0;
            default: return 0xFF8F9BB3;
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView name;
        final TextView meta;
        final ImageView icon;
        final TextView quality;
        String boundKey = "";

        Holder(View view) {
            super(view);
            number = view.findViewById(R.id.rowNumber);
            name = view.findViewById(R.id.rowName);
            meta = view.findViewById(R.id.rowMeta);
            icon = view.findViewById(R.id.rowIcon);
            quality = view.findViewById(R.id.rowQuality);
        }
    }
}
