package com.example.accountable;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class AppLimitAdapter extends RecyclerView.Adapter<AppLimitAdapter.ViewHolder> {

    private List<PartnerControlActivity.AppLimitInfo> apps;
    private OnLimitChangeListener limitChangeListener;

    public interface OnLimitChangeListener {
        void onLimitChanged(PartnerControlActivity.AppLimitInfo app, int newLimitMinutes);
    }

    public AppLimitAdapter(List<PartnerControlActivity.AppLimitInfo> apps, OnLimitChangeListener listener) {
        this.apps = apps;
        this.limitChangeListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_limit, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PartnerControlActivity.AppLimitInfo app = apps.get(position);

        holder.appNameText.setText(app.appName);

        // Set up the editable limit field
        holder.limitEditText.setText(String.valueOf(app.dailyLimitMinutes));

        // Set up text change listener for direct editing
        holder.limitEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String text = s.toString().trim();
                    if (!text.isEmpty()) {
                        int minutes = Integer.parseInt(text);
                        if (minutes >= 0 && minutes <= 480) { // 0 to 8 hours
                            app.dailyLimitMinutes = minutes;
                            if (limitChangeListener != null) {
                                limitChangeListener.onLimitChanged(app, minutes);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid number, ignore
                }
            }
        });

        // Set up increase/decrease buttons
        holder.decreaseButton.setOnClickListener(v -> {
            if (app.dailyLimitMinutes > 0) { // Minimum 0 minutes (blocked)
                app.dailyLimitMinutes = Math.max(0, app.dailyLimitMinutes - 5);
                updateLimitDisplay(holder, app);
            }
        });

        holder.increaseButton.setOnClickListener(v -> {
            if (app.dailyLimitMinutes < 480) { // Maximum 8 hours
                app.dailyLimitMinutes += 5;
                updateLimitDisplay(holder, app);
            }
        });

        // Preset buttons
        holder.preset0Button.setOnClickListener(v -> setLimit(holder, app, 0));
        holder.preset15Button.setOnClickListener(v -> setLimit(holder, app, 15));
        holder.preset30Button.setOnClickListener(v -> setLimit(holder, app, 30));
        holder.preset60Button.setOnClickListener(v -> setLimit(holder, app, 60));
    }

    private void updateLimitDisplay(ViewHolder holder, PartnerControlActivity.AppLimitInfo app) {
        holder.limitEditText.setText(String.valueOf(app.dailyLimitMinutes));
        if (limitChangeListener != null) {
            limitChangeListener.onLimitChanged(app, app.dailyLimitMinutes);
        }
    }

    private void setLimit(ViewHolder holder, PartnerControlActivity.AppLimitInfo app, int minutes) {
        app.dailyLimitMinutes = minutes;
        holder.limitEditText.setText(String.valueOf(minutes));
        if (limitChangeListener != null) {
            limitChangeListener.onLimitChanged(app, minutes);
        }
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appNameText;
        EditText limitEditText;
        Button decreaseButton;
        Button increaseButton;
        Button preset0Button;
        Button preset15Button;
        Button preset30Button;
        Button preset60Button;

        ViewHolder(View itemView) {
            super(itemView);
            appNameText = itemView.findViewById(R.id.appNameText);
            limitEditText = itemView.findViewById(R.id.limitEditText);
            decreaseButton = itemView.findViewById(R.id.decreaseButton);
            increaseButton = itemView.findViewById(R.id.increaseButton);
            preset0Button = itemView.findViewById(R.id.preset0Button);
            preset15Button = itemView.findViewById(R.id.preset15Button);
            preset30Button = itemView.findViewById(R.id.preset30Button);
            preset60Button = itemView.findViewById(R.id.preset60Button);
        }
    }
}
