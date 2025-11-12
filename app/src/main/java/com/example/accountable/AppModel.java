package com.example.accountable;

import android.graphics.drawable.Drawable;

public class AppModel {
    private String appName;
    private String packageName;
    private boolean selected;

    // transient runtime field (not serialized)
    private transient Drawable icon;

    // No-arg constructor for Firebase / deserialization
    public AppModel() {}

    public AppModel(String appName, String packageName, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.selected = false;
    }

    // For storing/loading from Firestore use a constructor without Drawable
    public AppModel(String appName, String packageName, boolean selected) {
        this.appName = appName;
        this.packageName = packageName;
        this.selected = selected;
    }

    // Getters & setters
    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    public Drawable getIcon() { return icon; }
    public void setIcon(Drawable icon) { this.icon = icon; }
}
