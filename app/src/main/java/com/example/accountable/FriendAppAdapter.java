package com.example.accountable;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class FriendAppAdapter extends RecyclerView.Adapter<FriendAppAdapter.ViewHolder> {

    private List<AppInfo> restrictedApps;

    public FriendAppAdapter(List<AppInfo> restrictedApps) {
        this.restrictedApps = restrictedApps;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = restrictedApps.get(position);
        holder.appName.setText(appInfo.getAppName());
        holder.packageName.setText(appInfo.getPackageName());
    }

    @Override
    public int getItemCount() {
        return restrictedApps.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appName;
        TextView packageName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.app_name_text_view);
            packageName = itemView.findViewById(R.id.package_name_text_view);
        }
    }
}