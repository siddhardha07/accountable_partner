package com.example.accountable;

import android.graphics.drawable.Drawable;

// This is a simple data class (POJO) to hold information about each app.
public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable icon;
    private boolean isSelected; // To track the checkbox state

    public AppInfo(String appName, String packageName, Drawable icon) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isSelected = false; // Default to not selected
    }

    // Getters
    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Drawable getIcon() {
        return icon;
    }

    public boolean isSelected() {
        return isSelected;
    }

    // Setter
    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}