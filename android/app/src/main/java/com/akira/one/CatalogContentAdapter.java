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
    private int spanCount = 1;

    public CatalogContentAdapter(List<ContentItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    /** Colunas da grade: define quando LEFT/RIGHT saem da lista ou só andam entre os cartões. */
    public void setSpanCount(int span) {
        spanCount = Math.max(1, span);
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel_card, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        ContentItem item = items.get(position);
        h.number.setText(String.valueOf(position + 1));
        h.name.setText(item.name);
        h.meta.setText(metaLabel(item));
        // Canais ao vivo: lista limpa (número, logo e nome), sem a linha "CANAL AO VIVO".
        h.meta.setVisibility("live".equals(item.type) ? View.GONE : View.VISIBLE);
        h.itemView.setSelected(selectedKey.equals(item.key()));

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
            v.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f).setDuration(120).start();
            if (hasFocus) listener.onFocus(item);
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            int column = pos % spanCount;
            // Só sai da grade na primeira/última coluna; no meio, o foco anda entre os cartões.
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && column == 0) {
                listener.onMoveLeft();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && (column == spanCount - 1 || pos == getItemCount() - 1)) {
                listener.onMoveRight();
                return true;
            }
            return false;
        });
    }

    private String metaLabel(ContentItem item) {
        if ("live".equals(item.type)) return item.meta == null || item.meta.isBlank() ? "CANAL AO VIVO" : item.meta.toUpperCase();
        if ("movie".equals(item.type)) return item.extension == null || item.extension.isBlank() ? "FILME" : "FILME • " + item.extension.toUpperCase();
        if ("series".equals(item.type)) return "SÉRIE";
        return "AKIRA";
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView number;
        final TextView name;
        final TextView meta;
        final ImageView icon;

        Holder(View view) {
            super(view);
            number = view.findViewById(R.id.rowNumber);
            name = view.findViewById(R.id.rowName);
            meta = view.findViewById(R.id.rowMeta);
            icon = view.findViewById(R.id.rowIcon);
        }
    }
}
