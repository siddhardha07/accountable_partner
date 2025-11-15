package com.example.accountable;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    private final Context context;
    private final List<AppModel> appList;
    private final List<AppModel> filteredAppList;
    private boolean isPartnerMode = false;

    public interface UnrestrictRequestListener {
        void onUnrestrictRequested(String packageName, String appName);
    }

    private UnrestrictRequestListener unrestrictListener;

    public AppAdapter(Context context, List<AppModel> appList) {
        this.context = context;
        this.appList = appList != null ? appList : new ArrayList<>();
        this.filteredAppList = new ArrayList<>(this.appList);
    }

    public void setPartnerMode(boolean isPartnerMode) {
        this.isPartnerMode = isPartnerMode;
        notifyDataSetChanged();
    }

    public void setUnrestrictListener(UnrestrictRequestListener listener) {
        this.unrestrictListener = listener;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppModel item = filteredAppList.get(position);
        holder.appName.setText(item.getAppName());
        Drawable icon = item.getIcon();
        if (icon != null) holder.appIcon.setImageDrawable(icon);
        else holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon);

        // prevent triggering listener when programmatically setting checked
        holder.appCheckbox.setOnCheckedChangeListener(null);
        holder.appCheckbox.setChecked(item.isSelected());

        if (isPartnerMode) {
            // Partner mode: Full control over restrictions
            holder.appCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setSelected(isChecked);
            });

            holder.itemView.setOnClickListener(v -> {
                boolean newState = !item.isSelected();
                item.setSelected(newState);
                holder.appCheckbox.setChecked(newState);
            });
        } else {
            // User mode: Can ADD restrictions, but need partner approval to REMOVE
            holder.appCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Allow adding new restrictions
                    item.setSelected(true);
                } else {
                    // Prevent removing restrictions - revert and request partner approval
                    holder.appCheckbox.setChecked(true);
                    if (unrestrictListener != null) {
                        unrestrictListener.onUnrestrictRequested(item.getPackageName(), item.getAppName());
                    }
                }
            });

            holder.itemView.setOnClickListener(v -> {
                if (!item.isSelected()) {
                    // Allow adding restriction
                    item.setSelected(true);
                    holder.appCheckbox.setChecked(true);
                } else {
                    // Request partner approval to remove restriction
                    if (unrestrictListener != null) {
                        unrestrictListener.onUnrestrictRequested(item.getPackageName(), item.getAppName());
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return filteredAppList.size();
    }

    // Helper to return selected package names
    public List<String> getSelectedPackageNames() {
        List<String> selected = new ArrayList<>();
        for (AppModel a : appList) {
            if (a.isSelected()) selected.add(a.getPackageName());
        }
        return selected;
    }

    // Optionally update the list (for loading saved selections)
    public void updateList(List<AppModel> newList) {
        appList.clear();
        appList.addAll(newList);
        filteredAppList.clear();
        filteredAppList.addAll(newList);
        notifyDataSetChanged();
    }

    // Filter the list based on search query
    public void filter(String query) {
        filteredAppList.clear();
        if (query.isEmpty()) {
            filteredAppList.addAll(appList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (AppModel app : appList) {
                if (app.getAppName().toLowerCase().contains(lowerCaseQuery) ||
                    app.getPackageName().toLowerCase().contains(lowerCaseQuery)) {
                    filteredAppList.add(app);
                }
            }
        }
        notifyDataSetChanged();
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView appIcon;
        TextView appName;
        CheckBox appCheckbox;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            appIcon = itemView.findViewById(R.id.app_icon);
            appName = itemView.findViewById(R.id.app_name);
            appCheckbox = itemView.findViewById(R.id.app_checkbox);
        }
    }
}
