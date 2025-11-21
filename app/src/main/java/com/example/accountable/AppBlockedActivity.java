package com.example.accountable;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppBlockedActivity extends AppCompatActivity {

    private TextView blockedAppName;
    private TextView blockReason;
    private TextView usageStats;
    private Button requestAccessButton;
    private Button goHomeButton;

    private String packageName;
    private String appName;
    private long usedTime;
    private long timeLimit;

    private FirebaseFirestore db;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_blocked);

        // Initialize Firebase
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        // Get app details from intent
        Intent intent = getIntent();
        packageName = intent.getStringExtra("packageName");
        appName = intent.getStringExtra("appName");
        usedTime = intent.getLongExtra("usedTime", 0);
        timeLimit = intent.getLongExtra("timeLimit", 0);

        initViews();
        setupClickListeners();
        displayBlockingInfo();
    }

    private void initViews() {
        blockedAppName = findViewById(R.id.blockedAppName);
        blockReason = findViewById(R.id.blockReason);
        usageStats = findViewById(R.id.usageStats);
        requestAccessButton = findViewById(R.id.requestAccessButton);
        goHomeButton = findViewById(R.id.goHomeButton);
    }

    private void setupClickListeners() {
        requestAccessButton.setOnClickListener(v -> requestPartnerAccess());
        goHomeButton.setOnClickListener(v -> goToHomeScreen());
    }

    private void displayBlockingInfo() {
        blockedAppName.setText(appName != null ? appName : packageName);

        if (timeLimit == 0) {
            blockReason.setText("This app has been blocked by your accountability partner.");
            usageStats.setText("Contact your partner if you need access to this app.");
        } else {
            blockReason.setText("Limit exceeded");
            usageStats.setText("Contact your partner if you need access to this app.");
        }
    }

    private void requestPartnerAccess() {
        if (currentUserId == null) {
            return;
        }

        // Immediate feedback - disable button and show processing state
        requestAccessButton.setText("Processing...");
        requestAccessButton.setEnabled(false);

        // Show time selection dialog first
        TimeSelectionDialog.show(this, new TimeSelectionDialog.TimeSelectionListener() {
            @Override
            public void onTimeSelected(long totalSeconds) {
                db.collection("users").document(currentUserId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String partnerId = documentSnapshot.getString("mainPartnerId");
                            if (partnerId != null) {
                                sendAccessRequest(partnerId, totalSeconds);
                            }
                        })
                        .addOnFailureListener(e -> {
                            // Re-enable button on error
                            requestAccessButton.setText("Request Failed - Retry");
                            requestAccessButton.setEnabled(true);
                        });
            }

            @Override
            public void onCancelled() {
                // Re-enable button if user cancels
                requestAccessButton.setText("Request Access");
                requestAccessButton.setEnabled(true);
            }
        });
    }

    private void sendAccessRequest(String partnerId, long requestedSeconds) {

        // Get user's name first
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String userName = userDoc.getString("displayName");
                    final String finalUserName = (userName == null) ? "Someone" : userName;
                    final String finalAppName = appName;

                    // Create enhanced access request using new AccessRequest class
                    String timeDescription = formatTimeDescription(requestedSeconds);

                    AccessRequest accessRequest = new AccessRequest(
                            currentUserId,
                            finalUserName,
                            packageName,
                            finalAppName,
                            "APP_ACCESS",
                            requestedSeconds,
                            "User requested " + timeDescription + " of access"
                    );

                    // Save AccessRequest to Firebase using the new structure
                    db.collection("requests").document(accessRequest.getRequestId())
                            .set(accessRequest)
                            .addOnSuccessListener(aVoid -> {
                                // Send FCM notification to partner
                                sendNotificationToPartner(partnerId, finalUserName, finalAppName,
                                        accessRequest.getRequestId(), requestedSeconds);

                                requestAccessButton.setText("Request Sent ✓ (" + timeDescription + ")");
                                requestAccessButton.setEnabled(false);

                                // Start monitoring for response
                                monitorRequestResponse(accessRequest.getRequestId());
                            })
                            .addOnFailureListener(e -> {
                                // Handle error
                                requestAccessButton.setText("Request Failed - Retry");
                            });
                });
    }

    private void sendNotificationToPartner(String partnerId, String userName, String appName, String requestId, long requestedSeconds) {
        // Get partner's FCM token
        db.collection("users").document(partnerId)
                .get()
                .addOnSuccessListener(partnerDoc -> {
                    String fcmToken = partnerDoc.getString("fcmToken");
                    if (fcmToken != null) {
                        // Send actual FCM notification using HTTP API
                        sendFCMNotification(fcmToken, userName, appName, requestId, requestedSeconds);
                    } else {
                        // FCM token not found
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FCM", "Failed to get partner FCM token", e);
                });
    }

    private void sendFCMNotification(String fcmToken, String userName, String appName, String requestId, long requestedSeconds) {
        // For free Firebase plan, we use a combination of Firestore trigger + local notification
        // This creates a Firestore document that triggers the FCM service on the partner's device
        String timeDescription = formatTimeDescription(requestedSeconds);

        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("fcmToken", fcmToken);
        notificationData.put("requestId", requestId);
        notificationData.put("userName", userName);
        notificationData.put("appName", appName);
        notificationData.put("requestedSeconds", requestedSeconds);
        notificationData.put("timeDescription", timeDescription);
        notificationData.put("message", userName + " wants " + appName + " access for " + timeDescription);
        notificationData.put("type", "access_request");
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("status", "pending");
        notificationData.put("priority", "high"); // Mark as high priority for immediate delivery

        // Save to Firestore - this will be picked up by the FCM service via real-time listeners
        // The FCM service will display the notification even if app is closed
        db.collection("instantNotifications").add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("FCM", "Instant notification created: " + documentReference.getId());

                    // Also save to pendingNotifications as backup for MainActivity listener
                    db.collection("pendingNotifications").add(notificationData);
                })
                .addOnFailureListener(e -> {
                    Log.e("FCM", "Failed to create instant notification", e);
                });
    }

    private void monitorRequestResponse(String requestId) {
        // Listen for real-time updates to the access request
        db.collection("requests").document(requestId)
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String status = documentSnapshot.getString("status");
                        if ("approved".equals(status)) {
                            // Get duration if available
                            Long durationSeconds = documentSnapshot.getLong("durationSeconds");
                            Long accessExpiresAt = documentSnapshot.getLong("accessExpiresAt");

                            if (durationSeconds != null && accessExpiresAt != null) {
                                // Save temporary access grant to SharedPreferences or Firestore
                                saveTemporaryAccess(packageName, accessExpiresAt);

                            }

                            // Access approved - close blocking screen
                            finish();
                        } else if ("denied".equals(status)) {
                            // Access denied - update UI
                            requestAccessButton.setText("Request Denied");
                            requestAccessButton.setEnabled(true);
                        }
                    }
                });
    }

    private void saveTemporaryAccess(String packageName, long expiresAt) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();

            // Convert expiresAt to remainingMillis for wallet-based system
            long currentTime = System.currentTimeMillis();
            long remainingMillis = Math.max(0, expiresAt - currentTime);

            java.util.Map<String, Object> accessData = new java.util.HashMap<>();
            accessData.put("remainingMillis", remainingMillis);
            accessData.put("grantedAt", currentTime);
            accessData.put("packageName", packageName);

            db.collection("users").document(userId)
                    .collection("temporaryAccess").document(packageName)
                    .set(accessData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("AppBlocked", "Saved wallet-based temporary access for " + packageName + " (" + (remainingMillis / 60000) + " min)");
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AppBlocked", "❌ Failed to save temporary access", e);
                    });
        }
    }

    private void goToHomeScreen() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Prevent going back to the blocked app
        goToHomeScreen();
    }
}
