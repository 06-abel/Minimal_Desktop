package com.example.wallpaper;

import android.graphics.drawable.Drawable;

public class AppModel {
    private final String label;
    private final String packageName;
    private final String className;
    private final Drawable icon;
    private boolean isFavorite;

    public AppModel(String label, String packageName, String className, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.className = className;
        this.icon = icon;
        this.isFavorite = false;
    }

    public String getLabel() {
        return label;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getClassName() {
        return className;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
}
