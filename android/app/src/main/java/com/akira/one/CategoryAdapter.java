package com.akira.one;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.H> {
    public interface Listener {
        void onSelected(CategoryItem item, int position);
        void onMoveRight();
    }

    private final List<CategoryItem> items;
    private final Listener listener;
    private String selected = "";

    public CategoryAdapter(List<CategoryItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setSelected(String id) {
        String next = id == null ? "" : id;
        if (selected.equals(next)) return;
        int oldPosition = positionOf(selected);
        selected = next;
        int newPosition = positionOf(selected);
        if (oldPosition >= 0) notifyItemChanged(oldPosition);
        if (newPosition >= 0) notifyItemChanged(newPosition);
    }

    private int positionOf(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    public String getSelectedId() {
        return selected;
    }

    @NonNull
    @Override
    public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new H(view);
    }

    @Override
    public void onBindViewHolder(@NonNull H h, int position) {
        CategoryItem item = items.get(position);
        boolean isSelected = selected.equals(item.id);

        h.name.setText(item.name);
        h.count.setText(String.valueOf(item.count));
        h.itemView.setSelected(isSelected);
        h.itemView.setAlpha(isSelected ? 1f : 0.90f);

        h.itemView.setOnClickListener(v -> listener.onSelected(item, h.getBindingAdapterPosition()));
        h.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                int p = h.getBindingAdapterPosition();
                if (p != RecyclerView.NO_POSITION) listener.onSelected(items.get(p), p);
            }
        });
        h.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
                RecyclerView rv = ListNav.parentList(v);
                if (rv != null) return ListNav.step(rv, h.getBindingAdapterPosition(), keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1, event);
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                listener.onMoveRight();
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class H extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;

        H(View view) {
            super(view);
            name = view.findViewById(R.id.categoryName);
            count = view.findViewById(R.id.categoryCount);
        }
    }
}
