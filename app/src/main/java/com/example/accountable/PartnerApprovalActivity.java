package com.example.accountable;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

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
    private TextView appNameText;
    private TextView requestedTimeText;
    private TextView requestTimeText;
    private Button allowButton;
    private Button denyButton;

    private long userRequestedSeconds = 0;

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
        userRequestedSeconds = getIntent().getLongExtra("requestedSeconds", 0);

        Log.d(TAG, "Approval request: " + requestId + " for " + appName + " from " + requesterName);

        // Initialize views
        initializeViews();
        setupClickListeners();

        // Load full request data from Firebase to get request type
        loadRequestData();
    }

    private void initializeViews() {
        titleText = findViewById(R.id.titleText);
        messageText = findViewById(R.id.messageText);
        appNameText = findViewById(R.id.appNameText);
        requestedTimeText = findViewById(R.id.requestedTimeText);
        requestTimeText = findViewById(R.id.requestTimeText);
        allowButton = findViewById(R.id.allowButton);
        denyButton = findViewById(R.id.denyButton);
    }

    private String formatTimeDescription(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0 && seconds > 0) {
            return minutes + "m " + seconds + "s";
        } else if (minutes > 0) {
            return minutes + " minutes";
        } else {
            return seconds + " seconds";
        }
    }

    private void setupClickListeners() {
        allowButton.setOnClickListener(v -> handleApproval(true));
        denyButton.setOnClickListener(v -> handleApproval(false));
    }

    private void loadRequestData() {
        if (requestId != null) {
            db.collection("requests").document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String requestType = documentSnapshot.getString("requestType");
                        displayRequestInfo(requestType);
                    } else {
                        displayRequestInfo(null);
                    }
                })
                .addOnFailureListener(e -> {
                    displayRequestInfo(null);
                });
        } else {
            displayRequestInfo(null);
        }
    }

    private void displayRequestInfo(String requestType) {
        boolean isUnrestrictRequest = "UNRESTRICT_APP".equals(requestType);

        if (isUnrestrictRequest) {
            // Unrestrict request
            titleText.setText("Unrestrict Request");
            messageText.setText((requesterName != null ? requesterName : "Someone") + " wants to remove restrictions from:");

            // Hide time information for unrestrict requests
            if (requestedTimeText != null) {
                requestedTimeText.setText("📋 Type: Remove from restrictions");
            }
        } else {
            // Regular access request
            titleText.setText("Access Request");
            messageText.setText((requesterName != null ? requesterName : "Someone") + " wants to access:");

            // Show time information for access requests
            if (requestedTimeText != null && userRequestedSeconds > 0) {
                String timeDescription = formatTimeDescription(userRequestedSeconds);
                requestedTimeText.setText("⏱️ Requested: " + timeDescription);
            }
        }

        // Display app name (same for both types)
        if (appNameText != null) {
            appNameText.setText(appName != null ? appName : "Unknown App");
        }

        // Format and display request time
        if (requestTimeText != null && requestTime != null) {
            requestTimeText.setText("📅 Requested at " + requestTime);
        }
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

        // Use the user's requested duration (no AP time selection)
        long totalSeconds = userRequestedSeconds;

        // Update the access request in Firestore
        Map<String, Object> response = new HashMap<>();
        response.put("status", approved ? "approved" : "denied");
        response.put("responseTime", new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        response.put("respondedAt", System.currentTimeMillis());

        if (approved && totalSeconds > 0) {
            // Grant exactly what the user requested
            response.put("durationSeconds", totalSeconds);
            response.put("grantedDuration", totalSeconds); // Use user's requested time
            response.put("accessExpiresAt", System.currentTimeMillis() + (totalSeconds * 1000));
        }

        db.collection("requests").document(requestId)
                .update(response)
                .addOnSuccessListener(aVoid -> {
                    // Check if this is an unrestrict request or access request
                    db.collection("requests").document(requestId).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String requestType = documentSnapshot.getString("requestType");
                            boolean isUnrestrictRequest = "UNRESTRICT_APP".equals(requestType);

                            Log.d(TAG, "Request " + (approved ? "approved" : "denied") + " - Type: " + requestType);

                            String message;
                            if (approved) {
                                if (isUnrestrictRequest) {
                                    message = String.format("✅ Approved removing %s from restrictions for %s", appName, requesterName);
                                } else if (totalSeconds > 0) {
                                    String timeDescription = formatTimeDescription(totalSeconds);
                                    message = String.format("✅ Granted %s access to %s for %s", requesterName, appName, timeDescription);
                                } else {
                                    message = String.format("✅ Granted %s access to %s", requesterName, appName);
                                }
                            } else {
                                if (isUnrestrictRequest) {
                                    message = String.format("❌ Denied removing %s from restrictions for %s", appName, requesterName);
                                } else {
                                    message = String.format("❌ Denied %s access to %s", requesterName, appName);
                                }
                            }

                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            android.util.Log.d(TAG, "Partner response sent: " + message);

                            // Close the activity
                            finish();
                        });
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
