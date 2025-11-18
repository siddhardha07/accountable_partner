package com.example.accountable;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class AccountableFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "access_requests";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupBackgroundListener();
    }

    private void setupBackgroundListener() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        // Listen for pending notifications when app is closed
        FirebaseFirestore.getInstance()
            .collection("pendingNotifications")
            .whereEqualTo("status", "pending")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null) return;

                for (com.google.firebase.firestore.DocumentChange change : snapshots.getDocumentChanges()) {
                    if (change.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        com.google.firebase.firestore.DocumentSnapshot doc = change.getDocument();
                        java.util.Map<String, Object> data = doc.getData();
                        if (data != null) {
                            String userName = (String) data.get("userName");
                            String appName = (String) data.get("appName");
                            String requestId = (String) data.get("requestId");
                            Long requestedSeconds = (Long) data.get("requestedSeconds");

                            if (userName != null && appName != null && requestId != null) {
                                showNotification(userName, appName, requestId, requestedSeconds != null ? requestedSeconds : 0L);
                            }
                        }
                    }
                }
            });
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);


        // Save token to user's Firestore document
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUser.getUid())
                .update("fcmToken", token)
                .addOnSuccessListener(aVoid -> {})
                .addOnFailureListener(e -> {});
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        if (remoteMessage.getData().size() > 0) {
            String userName = remoteMessage.getData().get("userName");
            String appName = remoteMessage.getData().get("appName");
            String requestId = remoteMessage.getData().get("requestId");
            String requestedSecondsStr = remoteMessage.getData().get("requestedSeconds");

            long requestedSeconds = 0;
            if (requestedSecondsStr != null) {
                try {
                    requestedSeconds = Long.parseLong(requestedSecondsStr);
                } catch (NumberFormatException e) {
                    Log.e("FCM", "Failed to parse requestedSeconds: " + requestedSecondsStr, e);
                }
            }


            showNotification(userName, appName, requestId, requestedSeconds);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Access Requests",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("App access request notifications");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void showNotification(String userName, String appName, String requestId, long requestedSeconds) {
        // Create intent for PartnerApprovalActivity
        Intent intent = new Intent(this, PartnerApprovalActivity.class);
        intent.putExtra("requestId", requestId);
        intent.putExtra("requesterName", userName);
        intent.putExtra("appName", appName);
        intent.putExtra("requestTime", new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date()));
        intent.putExtra("requestedSeconds", requestedSeconds); // Pass the requested seconds
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            requestId.hashCode(), // Use unique ID
            intent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🔒 Access Request")
            .setContentText(userName + " wants to access " + appName)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(userName + " is requesting access to " + appName + ". Tap to approve or deny."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(requestId.hashCode(), builder.build());
    }
}
