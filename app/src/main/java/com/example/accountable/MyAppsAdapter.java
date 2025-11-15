package com.example.accountable;

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

public class MyAppsAdapter extends RecyclerView.Adapter<MyAppsAdapter.AppViewHolder> {

    private List<AppInfo> appList;

    public MyAppsAdapter(List<AppInfo> appList) {
        this.appList = appList;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        AppInfo currentApp = appList.get(position);

        holder.appName.setText(currentApp.getAppName());
        holder.appIcon.setImageDrawable(currentApp.getIcon());
        holder.appCheckbox.setChecked(currentApp.isSelected());

        // Update the isSelected status in our AppInfo object when the checkbox is clicked
        holder.appCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            currentApp.setSelected(isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return appList.size();
    }

    // This is the method your Activity needs to get the selected apps
    public List<AppInfo> getSelectedApps() {
        List<AppInfo> selectedApps = new ArrayList<>();
        for (AppInfo app : appList) {
            if (app.isSelected()) {
                selectedApps.add(app);
            }
        }
        return selectedApps;
    }

    // ViewHolder class
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