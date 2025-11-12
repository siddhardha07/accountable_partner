package com.example.accountable;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PartnerApprovalActivity extends AppCompatActivity {
    private static final String TAG = "PartnerApproval";
    
    private FirebaseFirestore db;
    private String requestId;
    private String requesterName;
    private String appName;
    private String requestTime;
    
    private TextView titleText;
    private TextView messageText;
    private Button allowButton;
    private Button denyButton;
    private NumberPicker minutesPicker;
    private NumberPicker secondsPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_partner_approval);
        
        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Access Request");
        }
        
        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        
        // Get request data from intent
        requestId = getIntent().getStringExtra("requestId");
        requesterName = getIntent().getStringExtra("requesterName");
        appName = getIntent().getStringExtra("appName");
        requestTime = getIntent().getStringExtra("requestTime");
        
        Log.d(TAG, "Approval request: " + requestId + " for " + appName + " from " + requesterName);
        
        // Initialize views
        initializeViews();
        setupClickListeners();
        displayRequestInfo();
    }
    
    private void initializeViews() {
        titleText = findViewById(R.id.titleText);
        messageText = findViewById(R.id.messageText);
        allowButton = findViewById(R.id.allowButton);
        denyButton = findViewById(R.id.denyButton);
        minutesPicker = findViewById(R.id.minutesPicker);
        secondsPicker = findViewById(R.id.secondsPicker);
        
        // Setup NumberPickers
        setupNumberPickers();
    }
    
    private void setupNumberPickers() {
        // Minutes picker (0-60 minutes)
        minutesPicker.setMinValue(0);
        minutesPicker.setMaxValue(60);
        minutesPicker.setValue(5); // Default 5 minutes
        minutesPicker.setWrapSelectorWheel(true);
        
        // Seconds picker (0-59 seconds)
        secondsPicker.setMinValue(0);
        secondsPicker.setMaxValue(59);
        secondsPicker.setValue(0); // Default 0 seconds
        secondsPicker.setWrapSelectorWheel(true);
    }
    
    private void setupClickListeners() {
        allowButton.setOnClickListener(v -> handleApproval(true));
        denyButton.setOnClickListener(v -> handleApproval(false));
    }
    
    private void displayRequestInfo() {
        titleText.setText("Access Request");
        
        String message = String.format(
            "%s wants to access %s\n\nRequested at: %s\n\nDo you want to allow access?",
            requesterName != null ? requesterName : "Someone",
            appName != null ? appName : "a restricted app",
            requestTime != null ? requestTime : "just now"
        );
        
        messageText.setText(message);
    }
    
    private void handleApproval(boolean approved) {
        if (requestId == null) {
            Toast.makeText(this, "Invalid request", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Disable buttons to prevent double-clicking
        allowButton.setEnabled(false);
        denyButton.setEnabled(false);
        
        Log.d(TAG, "Processing approval: " + approved + " for request: " + requestId);
        
        // Get selected duration
        int minutes = minutesPicker.getValue();
        int seconds = secondsPicker.getValue();
        int totalSeconds = (minutes * 60) + seconds;
        
        // Update the access request in Firestore
        Map<String, Object> response = new HashMap<>();
        response.put("status", approved ? "approved" : "denied");
        response.put("responseTime", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        response.put("respondedAt", System.currentTimeMillis());
        
        if (approved && totalSeconds > 0) {
            response.put("durationSeconds", totalSeconds);
            response.put("accessExpiresAt", System.currentTimeMillis() + (totalSeconds * 1000));
        }
        
        db.collection("accessRequests").document(requestId)
                .update(response)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Access request " + (approved ? "approved" : "denied"));
                    
                    String message;
                    if (approved && totalSeconds > 0) {
                        String timeStr = minutes > 0 ? 
                            String.format("%dm %ds", minutes, seconds) : 
                            String.format("%d seconds", seconds);
                        message = String.format("✅ Access granted to %s for %s", requesterName, timeStr);
                    } else if (approved) {
                        message = "✅ Access granted to " + requesterName;
                    } else {
                        message = "❌ Access denied to " + requesterName;
                    }
                    
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    
                    // Close the activity
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update access request", e);
                    Toast.makeText(this, "Failed to process request. Please try again.", Toast.LENGTH_SHORT).show();
                    
                    // Re-enable buttons
                    allowButton.setEnabled(true);
                    denyButton.setEnabled(true);
                });
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        // Treat back navigation as deny
        handleApproval(false);
        return true;
    }
    
    @Override
    public void onBackPressed() {
        // Treat back press as deny
        handleApproval(false);
    }
}