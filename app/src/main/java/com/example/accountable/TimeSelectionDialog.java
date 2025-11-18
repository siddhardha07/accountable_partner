package com.example.accountable;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;

public class TimeSelectionDialog {

    public interface TimeSelectionListener {
        void onTimeSelected(long totalSeconds);
        void onCancelled();
    }

    public static void show(Context context, TimeSelectionListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_time_selection, null);

        NumberPicker minutesPicker = dialogView.findViewById(R.id.minutesPicker);
        NumberPicker secondsPicker = dialogView.findViewById(R.id.secondsPicker);

        // Configure minutes picker (0-120 minutes = 2 hours max)
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(120);
        minutesPicker.setValue(0); // Default 0 minutes - user starts from zero
        minutesPicker.setWrapSelectorWheel(false);

        // Configure seconds picker (0-59 seconds)
        secondsPicker.setMinValue(0);
        secondsPicker.setMaxValue(59);
        secondsPicker.setValue(0); // Default 0 seconds
        secondsPicker.setWrapSelectorWheel(true);

        // Quick select buttons
        Button quick15min = dialogView.findViewById(R.id.quick15min);
        Button quick30min = dialogView.findViewById(R.id.quick30min);
        Button quick1hour = dialogView.findViewById(R.id.quick1hour);
        Button quick2hour = dialogView.findViewById(R.id.quick2hour);

        quick15min.setOnClickListener(v -> {
            minutesPicker.setValue(15);
            secondsPicker.setValue(0);
        });

        quick30min.setOnClickListener(v -> {
            minutesPicker.setValue(30);
            secondsPicker.setValue(0);
        });

        quick1hour.setOnClickListener(v -> {
            minutesPicker.setValue(60);
            secondsPicker.setValue(0);
        });

        quick2hour.setOnClickListener(v -> {
            minutesPicker.setValue(120);
            secondsPicker.setValue(0);
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(dialogView)
                .setPositiveButton("Request Access", (d, which) -> {
                    int minutes = minutesPicker.getValue();
                    int seconds = secondsPicker.getValue();
                    long totalSeconds = (minutes * 60L) + seconds;



                    if (totalSeconds > 0) {
                        listener.onTimeSelected(totalSeconds);
                    } else {
                        // Show error for zero time
                        listener.onCancelled();
                    }
                })
                .setNegativeButton("Cancel", (d, which) -> listener.onCancelled())
                .create();

        dialog.show();
    }
}
