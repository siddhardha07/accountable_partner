package com.example.accountable;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyAppsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private List<AppModel> appList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_apps);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("My Apps");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        recyclerView = findViewById(R.id.appsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        db = FirebaseFirestore.getInstance();

        loadInstalledApps();

        // Create adapter first
        adapter = new AppAdapter(this, appList);

        // Set partner mode - for now assume user mode, will implement partner detection later
        boolean isPartnerMode = false; // TODO: Implement proper partner detection
        adapter.setPartnerMode(isPartnerMode);
        adapter.setUnrestrictListener(this::handleUnrestrictRequest);

        recyclerView.setAdapter(adapter);

        loadSavedSelectionFromFirestore();
    }

    private void loadInstalledApps() {
        appList = new ArrayList<>();
        PackageManager pm = getPackageManager();

        // Get only apps that can be launched (have launcher icons)
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> packages = pm.queryIntentActivities(mainIntent, 0);

        for (ResolveInfo resolveInfo : packages) {
            ApplicationInfo appInfo = resolveInfo.activityInfo.applicationInfo;

            try {
                String label = pm.getApplicationLabel(appInfo).toString();
                Drawable icon = pm.getApplicationIcon(appInfo);
                String pkgName = appInfo.packageName;
                AppModel model = new AppModel(label, pkgName, icon);
                appList.add(model);
            } catch (Exception e) {
                // Skip apps that can't be loaded
                continue;
            }
        }

        // Show how many launchable apps we found
        Toast.makeText(this, "Found " + appList.size() + " launchable apps", Toast.LENGTH_SHORT).show();
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_my_apps, menu);

        // Setup search functionality in toolbar
        MenuItem searchItem = menu.findItem(R.id.action_search);
        androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();

        if (searchView != null) {
            searchView.setQueryHint("Search apps...");
            searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    if (adapter != null) {
                        adapter.filter(newText);
                    }
                    return true;
                }
            });
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_save) {
            saveSelectedAppsToFirestore();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveSelectedAppsToFirestore() {
        List<String> selectedPackages = adapter.getSelectedPackageNames();

        // Allow saving even if no apps are selected (user wants to unrestrict all apps)
        // Build a simple serializable structure
        Map<String, Object> data = new HashMap<>();
        data.put("selectedApps", selectedPackages);

        // Use authenticated user ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String userId = currentUser.getUid();

        // Show saving feedback
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, "Saving... (Removing all restrictions)", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Saving " + selectedPackages.size() + " apps...", Toast.LENGTH_SHORT).show();
        }

        db.collection("users").document(userId)
                .update(data)  // Use update() instead of set() to preserve other fields
                .addOnSuccessListener(aVoid -> {
                    if (selectedPackages.isEmpty()) {
                        Toast.makeText(MyAppsActivity.this, "✅ All app restrictions removed!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MyAppsActivity.this, "✅ Saved " + selectedPackages.size() + " restricted apps!", Toast.LENGTH_SHORT).show();
                    }

                    // Auto-navigate back to MainActivity after successful save
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MyAppsActivity.this, "❌ Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void loadSavedSelectionFromFirestore() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> selectedPackages = (List<String>) documentSnapshot.get("selectedApps");
                        if (selectedPackages != null && adapter != null) {
                            // set selected = true for matching apps
                            for (AppModel am : appList) {
                                if (selectedPackages.contains(am.getPackageName())) {
                                    am.setSelected(true);
                                }
                            }
                            // Refresh the adapter to show checkboxes
                            adapter.notifyDataSetChanged();

                            // Show feedback
                            Toast.makeText(MyAppsActivity.this, "Loaded " + selectedPackages.size() + " previously selected apps", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MyAppsActivity.this, "No previous selections found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MyAppsActivity.this, "Failed to load selections: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void handleUnrestrictRequest(String packageName, String appName) {


        // Show confirmation dialog first
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Request Permission")
            .setMessage("You need partner approval to remove " + appName + " from restrictions. Send request?")
            .setPositiveButton("Send Request", (dialog, which) -> {
                sendUnrestrictRequest(packageName, appName);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void sendUnrestrictRequest(String packageName, String appName) {
        // Create unrestrict request
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String currentUserId = currentUser.getUid();
            String userName = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "User";

            AccessRequest unrestrictRequest = new AccessRequest(
                currentUserId,
                userName,
                packageName,
                appName,
                "UNRESTRICT_APP",
                0, // No time limit for unrestrict requests
                "User wants to remove " + appName + " from restrictions"
            );

            // Save request to Firebase
            db.collection("requests").document(unrestrictRequest.getRequestId())
                .set(unrestrictRequest)
                .addOnSuccessListener(aVoid -> {
                    // Get partner ID and send notification
                    db.collection("users").document(currentUserId)
                        .get()
                        .addOnSuccessListener(userDoc -> {
                            String partnerId = userDoc.getString("mainPartnerId");
                            if (partnerId != null) {
                                sendUnrestrictNotification(partnerId, userName, appName, unrestrictRequest.getRequestId());
                                Toast.makeText(this, "Unrestrict request sent to partner", Toast.LENGTH_SHORT).show();

                                // Start monitoring for response
                                monitorUnrestrictResponse(unrestrictRequest.getRequestId(), packageName);
                            } else {
                                Toast.makeText(this, "No partner configured", Toast.LENGTH_SHORT).show();
                            }
                        });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void sendUnrestrictNotification(String partnerId, String userName, String appName, String requestId) {
        // Get partner's FCM token and send notification
        db.collection("users").document(partnerId)
            .get()
            .addOnSuccessListener(partnerDoc -> {
                String fcmToken = partnerDoc.getString("fcmToken");
                if (fcmToken != null) {
                    // Create notification data for unrestrict request
                    Map<String, Object> notificationData = new HashMap<>();
                    notificationData.put("fcmToken", fcmToken);
                    notificationData.put("requestId", requestId);
                    notificationData.put("userName", userName);
                    notificationData.put("appName", appName);
                    notificationData.put("requestedSeconds", 0L); // No time for unrestrict
                    notificationData.put("message", userName + " wants to remove " + appName + " from restrictions");
                    notificationData.put("type", "unrestrict_request");
                    notificationData.put("timestamp", System.currentTimeMillis());
                    notificationData.put("status", "pending");

                    // Save to notifications collection
                    db.collection("pendingNotifications").add(notificationData)
                        .addOnFailureListener(e -> {

                        });
                }
            });
    }

    private void monitorUnrestrictResponse(String requestId, String packageName) {
        // Listen for response to unrestrict request
        db.collection("requests").document(requestId)
            .addSnapshotListener((documentSnapshot, error) -> {
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    String status = documentSnapshot.getString("status");
                    String appName = documentSnapshot.getString("appName");
                    String partnerName = getPartnerName(); // Get partner's name

                    if ("approved".equals(status)) {
                        // Automatically uncheck the app
                        for (AppModel app : appList) {
                            if (app.getPackageName().equals(packageName)) {
                                app.setSelected(false);
                                break;
                            }
                        }
                        adapter.notifyDataSetChanged();
                        saveSelectedAppsToFirestore(); // Save updated selections to Firebase

                        // Show detailed approval notification
                        String message = "✅ " + partnerName + " approved removing " + appName + " from restrictions";
                        showDetailedNotification("Request Approved", message, true);


                    } else if ("denied".equals(status)) {
                        // Show detailed denial notification
                        String message = "❌ " + partnerName + " denied removing " + appName + " from restrictions";
                        showDetailedNotification("Request Denied", message, false);


                    }
                }
            });
    }

    private String getPartnerName() {
        // Get partner name from shared preferences or Firebase
        android.content.SharedPreferences prefs = getSharedPreferences("AccountablePrefs", android.content.Context.MODE_PRIVATE);
        String partnerName = prefs.getString("partner_name", null);

        if (partnerName != null && !partnerName.trim().isEmpty()) {
            return partnerName;
        }

        // Fallback to generic name if not found
        return "Your Partner";
    }

    private void showDetailedNotification(String title, String message, boolean isApproved) {
        // Show toast message
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();

        // Create a proper Android notification
        android.app.NotificationManager notificationManager =
            (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);

        // Create notification channel for Android 8.0+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                "unrestrict_responses",
                "Unrestrict Responses",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for partner responses to unrestrict requests");
            notificationManager.createNotificationChannel(channel);
        }

        // Build the notification
        androidx.core.app.NotificationCompat.Builder builder =
            new androidx.core.app.NotificationCompat.Builder(this, "unrestrict_responses")
                .setSmallIcon(isApproved ? android.R.drawable.ic_dialog_info : android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Show the notification
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
