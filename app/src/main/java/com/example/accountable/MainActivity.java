package com.example.accountable;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String NOTIFICATION_CHANNEL_ID = "access_requests";
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration accessRequestListener;
    private android.os.Handler listenerHealthHandler;
    private Runnable listenerHealthCheck;
    private boolean waitingForOverlayPermission = false;
    private boolean waitingForUsageStatsPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User not signed in, go to auth activity
            Intent intent = new Intent(MainActivity.this, AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Ensure user document exists with all required fields
        ensureUserDocumentExists(currentUser);

        // Get and save FCM token
        getFCMToken();

        // Setup notification channel and request permissions
        createNotificationChannel();
        requestNotificationPermission();

        // Start listening for access requests
        setupAccessRequestListener();
        startListenerHealthCheck();

        setContentView(R.layout.activity_main);

        Button myAppsBtn = findViewById(R.id.myAppsButton);
        Button accountabilityPartnerBtn = findViewById(R.id.accountabilityPartnerButton);
        Button peopleIHelpBtn = findViewById(R.id.peopleIHelpButton);
        Button signOutBtn = findViewById(R.id.signOutButton);

        myAppsBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, MyAppsActivity.class));
        });

        accountabilityPartnerBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AccountabilityPartnerActivity.class));
        });

        peopleIHelpBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, PeopleIHelpActivity.class));
        });

        signOutBtn.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(MainActivity.this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void ensureUserDocumentExists(FirebaseUser user) {
        // Simplified and fast - just ensure document exists
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        createUserDocument(user);
                    }
                    // Skip heavy updates - keep it fast
                });
    }

    private void createUserDocument(FirebaseUser user) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", user.getEmail());
        userProfile.put("displayName", user.getDisplayName() != null ? user.getDisplayName()
                : (user.getEmail() != null ? user.getEmail().split("@")[0] : "User"));
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("mainPartnerId", null);
        userProfile.put("partners", new java.util.ArrayList<String>());

        db.collection("users").document(user.getUid()).set(userProfile);
        // Removed toasts for speed - silent creation
    }

    private void getFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String token = task.getResult();
                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        if (currentUser != null) {
                            // Silent token save - no logs for speed
                            db.collection("users").document(currentUser.getUid()).update("fcmToken", token);
                        }
                    }
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Access Requests";
            String description = "Notifications for partner access requests";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted!", Toast.LENGTH_SHORT).show();
                // Request next permission after a short delay
                new android.os.Handler().postDelayed(() -> {
                    requestOverlayPermission();
                }, 1000);
            } else {
                Toast.makeText(this, "Notification permission denied. You won't receive access requests.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startListenerHealthCheck() {
        listenerHealthHandler = new android.os.Handler();
        listenerHealthCheck = new Runnable() {
            @Override
            public void run() {
                // Check if listener is still active
                if (accessRequestListener == null) {
                    setupAccessRequestListener(); // Silent reconnection
                }
                // Schedule next check in 2 minutes (less frequent)
                listenerHealthHandler.postDelayed(this, 120000);
            }
        };
        // Start the first check in 2 minutes
        listenerHealthHandler.postDelayed(listenerHealthCheck, 120000);
    }

    private void stopListenerHealthCheck() {
        if (listenerHealthHandler != null && listenerHealthCheck != null) {
            listenerHealthHandler.removeCallbacks(listenerHealthCheck);
        }
    }

    private void setupAccessRequestListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        String userId = currentUser.getUid();

        // Get user's FCM token to match notifications
        db.collection("users").document(userId).get()
                .addOnSuccessListener(userDoc -> {
                    String userFCMToken = userDoc.getString("fcmToken");
                    if (userFCMToken != null) {
                        // Listen for new pending notifications for this user's FCM token
                        accessRequestListener = db.collection("pendingNotifications")
                                .whereEqualTo("fcmToken", userFCMToken)
                                .whereEqualTo("status", "pending")
                                .addSnapshotListener((snapshots, e) -> {
                                    if (e != null) {
                                        // Silent reconnect after delay
                                        new android.os.Handler().postDelayed(() -> {
                                            accessRequestListener = null;
                                            setupAccessRequestListener();
                                        }, 5000);
                                        return;
                                    }

                                    if (snapshots != null) {
                                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                                            if (dc.getType() == DocumentChange.Type.ADDED) {
                                                // New notification found
                                                DocumentSnapshot document = dc.getDocument();
                                                String requesterName = document.getString("userName");
                                                String appName = document.getString("appName");
                                                String requestId = document.getString("requestId");
                                                Long requestedSeconds = document.getLong("requestedSeconds");

                                                showAccessRequestNotification(requestId, requesterName, appName, requestedSeconds != null ? requestedSeconds : 0L);

                                                // Mark notification as delivered
                                                document.getReference().update("status", "delivered");
                                            }
                                        }
                                    }
                                });
                    }
                });
    }

    private void showAccessRequestNotification(String requestId, String requesterName, String appName, long requestedSeconds) {
        // Create intent that opens PartnerApprovalActivity
        Intent intent = new Intent(this, PartnerApprovalActivity.class);
        intent.putExtra("requestId", requestId);
        intent.putExtra("requesterName", requesterName);
        intent.putExtra("appName", appName);
        intent.putExtra("requestedSeconds", requestedSeconds); // Pass the requested seconds
        intent.putExtra("requestTime", new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Access Request")
                .setContentText(requesterName + " wants to use " + appName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(requesterName + " is asking for permission to use " + appName + ". Tap to approve or deny."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accessRequestListener == null) {
            setupAccessRequestListener();
        }

        // Handle permission flow continuation
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false;
            // After overlay permission, request usage stats permission
            new Handler().postDelayed(() -> {
                requestUsageStatsPermission();
            }, 1000);
        } else if (waitingForUsageStatsPermission) {
            waitingForUsageStatsPermission = false;
            // After usage stats permission, request accessibility permission
            new Handler().postDelayed(() -> {
                requestAccessibilityPermission();
            }, 1000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Keep listener active in background
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListenerHealthCheck();
        if (accessRequestListener != null) {
            accessRequestListener.remove();
        }
    }

    // Simple permission requests - just like requestNotificationPermission()
    // Individual permission methods - call them separately as needed
    public void requestOverlayPermission() {
        waitingForOverlayPermission = true;
        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    public void requestUsageStatsPermission() {
        waitingForUsageStatsPermission = true;
        Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
        startActivity(intent);
    }

    public void requestAccessibilityPermission() {
        Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);

    }

}
