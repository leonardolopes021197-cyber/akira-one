package com.akira.one;

public class CategoryItem {
    public final String id;
    public final String name;
    public int count;

    public CategoryItem(String id, String name) {
        this(id, name, 0);
    }

    public CategoryItem(String id, String name, int count) {
        this.id = id == null ? "" : id;
        this.name = name == null || name.isBlank() ? "Categoria" : name;
        this.count = Math.max(0, count);
    }
}
