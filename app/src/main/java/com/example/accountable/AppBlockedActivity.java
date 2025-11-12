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
            long usedMinutes = usedTime / 60000;
            long limitMinutes = timeLimit / 60000;
            
            blockReason.setText("You've reached your daily time limit for this app.");
            usageStats.setText(String.format("Used: %d minutes\nLimit: %d minutes", usedMinutes, limitMinutes));
        }
    }
    
    private void requestPartnerAccess() {
        if (currentUserId == null) return;
        
        // Get user's main partner
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String partnerId = documentSnapshot.getString("mainPartnerId");
                    if (partnerId != null) {
                        sendAccessRequest(partnerId);
                    }
                })
                .addOnFailureListener(e -> {
                    // Handle error
                });
    }
    
    private void sendAccessRequest(String partnerId) {
        // Get user's name first
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    String userName = userDoc.getString("name");
                    final String finalUserName = (userName == null) ? "Someone" : userName;
                    final String finalAppName = appName;
                    
                    // Create access request in Firestore
                    java.util.Map<String, Object> request = new java.util.HashMap<>();
                    request.put("userId", currentUserId);
                    request.put("partnerId", partnerId);
                    request.put("appPackage", packageName);
                    request.put("appName", finalAppName);
                    request.put("userName", finalUserName);
                    request.put("requestType", "temporary_access");
                    request.put("timestamp", System.currentTimeMillis());
                    request.put("status", "pending");
                    
                    db.collection("accessRequests")
                            .add(request)
                            .addOnSuccessListener(documentReference -> {
                                String requestId = documentReference.getId();
                                
                                // Send FCM notification to partner
                                sendNotificationToPartner(partnerId, finalUserName, finalAppName, requestId);
                                
                                requestAccessButton.setText("Request Sent ✓");
                                requestAccessButton.setEnabled(false);
                                
                                // Start monitoring for response
                                monitorRequestResponse(requestId);
                            })
                            .addOnFailureListener(e -> {
                                // Handle error
                                requestAccessButton.setText("Request Failed - Retry");
                            });
                });
    }
    
    private void sendNotificationToPartner(String partnerId, String userName, String appName, String requestId) {
        // Get partner's FCM token
        db.collection("users").document(partnerId)
                .get()
                .addOnSuccessListener(partnerDoc -> {
                    String fcmToken = partnerDoc.getString("fcmToken");
                    if (fcmToken != null) {
                        // Send actual FCM notification using HTTP API
                        sendFCMNotification(fcmToken, userName, appName, requestId);
                    } else {
                        Log.w("FCM", "Partner FCM token not found for user: " + partnerId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("FCM", "Failed to get partner FCM token", e);
                });
    }
    
    private void sendFCMNotification(String fcmToken, String userName, String appName, String requestId) {
        // Create a simple in-app notification approach since FCM HTTP requires server key
        // Instead, let's use a Firestore-based notification that the partner app can listen to
        java.util.Map<String, Object> notificationData = new java.util.HashMap<>();
        notificationData.put("fcmToken", fcmToken);
        notificationData.put("requestId", requestId);
        notificationData.put("userName", userName);
        notificationData.put("appName", appName);
        notificationData.put("type", "access_request");
        notificationData.put("timestamp", System.currentTimeMillis());
        notificationData.put("status", "pending");
        
        // Save to a notifications collection that the partner's app can listen to
        db.collection("pendingNotifications").add(notificationData)
                .addOnSuccessListener(documentReference -> {
                    Log.d("FCM", "Notification saved for partner to receive");
                })
                .addOnFailureListener(e -> {
                    Log.e("FCM", "Failed to save notification", e);
                });
    }
    
    private void monitorRequestResponse(String requestId) {
        // Listen for real-time updates to the access request
        db.collection("accessRequests").document(requestId)
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
                                Log.d("AppBlocked", "Access granted for " + durationSeconds + " seconds");
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
        // Save to user's Firestore document for the AppMonitoringService to check
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            
            java.util.Map<String, Object> accessData = new java.util.HashMap<>();
            accessData.put("packageName", packageName);
            accessData.put("expiresAt", expiresAt);
            accessData.put("grantedAt", System.currentTimeMillis());
            
            db.collection("users").document(userId)
                    .collection("temporaryAccess").document(packageName)
                    .set(accessData)
                    .addOnSuccessListener(aVoid -> {
                        Log.d("AppBlocked", "Temporary access saved for " + packageName);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AppBlocked", "Failed to save temporary access", e);
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
    
    @Override
    public void onBackPressed() {
        // Prevent going back to the blocked app
        goToHomeScreen();
    }
}