package com.example.accountable;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppMonitoringService extends AccessibilityService {

    private static final String TAG = "AppMonitoringService";
    private static final String PREFS_NAME = "app_monitoring";
    private static final long CHECK_INTERVAL = 5000; // 5 seconds

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;

    // Track app usage sessions
    private Map<String, Long> appStartTimes = new ConcurrentHashMap<>();
    private Map<String, Long> totalUsageToday = new ConcurrentHashMap<>();
    private Map<String, Long> appLimits = new ConcurrentHashMap<>(); // Daily limits in milliseconds
    private Map<String, Long> lastBlockTime = new ConcurrentHashMap<>(); // Track when apps were last blocked
    private Map<String, Long> temporaryAccessExpiry = new ConcurrentHashMap<>(); // Track temporary access expiry times

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable usageChecker;
    private Runnable blockEnforcer;

    // Firestore listener for temporary access
    private com.google.firebase.firestore.ListenerRegistration temporaryAccessListener;

    private String currentForegroundApp = "";
    private long currentAppStartTime = 0;
    private boolean isBlocking = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle multiple event types to catch more apps
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {

            if (event.getPackageName() != null) {
                String packageName = event.getPackageName().toString();

                // Check if it's a system app
                boolean isSystem = isSystemApp(packageName);
                boolean isOurApp = packageName.equals(getPackageName());

                // Ignore system apps and our own app
                if (isSystem || isOurApp) {
                    return;
                }

                handleAppSwitch(packageName);
            }
        }
    }

    private boolean isSystemApp(String packageName) {
        // Only filter out core system packages, not all com.android.* apps
        return packageName.startsWith("android.") ||
               packageName.equals("com.android.systemui") ||
               packageName.equals("com.android.launcher") ||
               packageName.equals("com.android.launcher3") ||
               packageName.equals("com.android.settings") ||
               packageName.equals("com.google.android.googlequicksearchbox") ||
               packageName.equals("com.android.inputmethod.latin") ||
               packageName.equals("com.android.phone") ||
               packageName.equals("com.android.dialer");
    }

    private void handleAppSwitch(String newPackageName) {
        long currentTime = System.currentTimeMillis();

        // Save previous app usage if any
        if (!currentForegroundApp.isEmpty() && currentAppStartTime > 0) {
            long sessionDuration = currentTime - currentAppStartTime;
            updateAppUsage(currentForegroundApp, sessionDuration);
        }

        // Check if we're trying to open a blocked app
        if (isAppCurrentlyBlocked(newPackageName)) {
            blockAppImmediately(newPackageName);
            return; // Don't track this as a legitimate app switch
        }

        // Start tracking new app
        currentForegroundApp = newPackageName;
        currentAppStartTime = currentTime;

        // Check if this app is restricted and if user has exceeded limits
        checkAppRestrictions(newPackageName);
    }

    private boolean isAppCurrentlyBlocked(String packageName) {
        if (currentUserId == null) {
            return false;
        }

        // First check if there's a temporary access grant that's still valid
        if (hasValidTemporaryAccess(packageName)) {
            return false; // Not blocked due to temporary access
        }

        Long lastBlocked = lastBlockTime.get(packageName);
        if (lastBlocked == null) {
            return false;
        }

        boolean sameDay = isSameDay(lastBlocked, System.currentTimeMillis());
        boolean overLimit = isAppOverLimit(packageName);

        // Check if it was blocked today and still within the same day
        boolean isBlocked = sameDay && overLimit;

        return isBlocked;
    }

    private boolean hasValidTemporaryAccess(String packageName) {
        if (currentUserId == null) {
            return false;
        }

        // Check cached temporary access expiry
        Long expiryTime = temporaryAccessExpiry.get(packageName);
        long currentTime = System.currentTimeMillis();

        if (expiryTime != null) {
            if (currentTime < expiryTime) {
                return true;
            } else {
                // Access expired, remove from cache
                temporaryAccessExpiry.remove(packageName);

                // Clean up the Firestore document
                if (db != null) {
                    db.collection("users").document(currentUserId)
                            .collection("temporaryAccess").document(packageName)
                            .delete();
                }
            }
        }

        return false;
    }

    public void refreshTemporaryAccess(String packageName) {
        if (currentUserId == null || db == null) return;

        db.collection("users").document(currentUserId)
                .collection("temporaryAccess").document(packageName)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long expiresAt = documentSnapshot.getLong("expiresAt");
                        if (expiresAt != null && System.currentTimeMillis() < expiresAt) {
                            // Cache the expiry time
                            temporaryAccessExpiry.put(packageName, expiresAt);
                            // Remove from blocked apps
                            lastBlockTime.remove(packageName);

                            // Show user-friendly message
                            handler.post(() -> {
                                String appName = packageName.substring(packageName.lastIndexOf('.') + 1);
                                Toast.makeText(this,
                                    "✅ " + appName + " is now accessible",
                                    Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            // Expired - clean up
                            documentSnapshot.getReference().delete();
                            temporaryAccessExpiry.remove(packageName);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to refresh temporary access for " + packageName, e);
                });
    }

    private void checkTemporaryAccessAsync(String packageName) {
        if (currentUserId == null || db == null) return;

        db.collection("users").document(currentUserId)
                .collection("temporaryAccess").document(packageName)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long expiresAt = documentSnapshot.getLong("expiresAt");
                        if (expiresAt != null && System.currentTimeMillis() < expiresAt) {
                            // Valid temporary access - remove from blocked list
                            lastBlockTime.remove(packageName);
                            Log.d(TAG, "Temporary access validated for " + packageName);

                            // Show user-friendly message
                            handler.post(() -> {
                                String appName = packageName.substring(packageName.lastIndexOf('.') + 1);
                                Toast.makeText(this,
                                    "✅ Temporary access granted for " + appName,
                                    Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            // Expired - clean up
                            documentSnapshot.getReference().delete();
                        }
                    }
                });
    }

    private boolean isSameDay(long time1, long time2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
               cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR);
    }

    private boolean isAppOverLimit(String packageName) {
        long dailyUsage = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L); // Default to 0 (blocked)
        return dailyUsage >= limit;
    }

    private void blockAppImmediately(String packageName) {
        // Double-check if this app should still be blocked (might have temporary access)
        if (hasValidTemporaryAccess(packageName)) {
            Log.d(TAG, "🚫 Blocking cancelled - temporary access detected for " + packageName);
            return;
        }

        isBlocking = true;

        // Force close the app by going to home screen
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);

        // Show immediate blocking message
        showPersistentBlockMessage(packageName);

        // Schedule repeated blocking enforcement
        scheduleBlockEnforcement(packageName);

        isBlocking = false;
    }

    private void updateAppUsage(String packageName, long sessionDuration) {
        // Update total usage for today
        long currentUsage = totalUsageToday.getOrDefault(packageName, 0L);
        totalUsageToday.put(packageName, currentUsage + sessionDuration);

        Log.d(TAG, packageName + " used for " + (sessionDuration / 1000) + " seconds. " +
                  "Total today: " + ((currentUsage + sessionDuration) / 60000) + " minutes");
    }

    private void checkAppRestrictions(String packageName) {
        // Check if user is authenticated and has restrictions
        if (currentUserId == null) {
            Log.d(TAG, "❌ NO USER ID - cannot check restrictions for " + packageName);
            return;
        }

        Log.d(TAG, "🔍 CHECKING RESTRICTIONS for " + packageName + " (userId: " + currentUserId + ")");

        // Check if this app is in user's restricted list
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> selectedApps = (List<String>) documentSnapshot.get("selectedApps");
                        Log.d(TAG, "📋 User selected apps: " + (selectedApps != null ? selectedApps.toString() : "null"));
                        if (selectedApps != null && selectedApps.contains(packageName)) {
                            Log.d(TAG, "✅ " + packageName + " IS RESTRICTED - checking time limit");
                            checkTimeLimit(packageName);
                        } else {
                            Log.d(TAG, "✅ " + packageName + " NOT restricted - allowing");
                        }
                    } else {
                        Log.d(TAG, "❌ User document does not exist for: " + currentUserId);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to check app restrictions for " + packageName, e);
                });
    }

    private void checkTimeLimit(String packageName) {
        // Load current limit from Firestore if not cached
        if (!appLimits.containsKey(packageName)) {
            loadAppLimit(packageName, () -> checkTimeLimitWithLoadedData(packageName));
        } else {
            checkTimeLimitWithLoadedData(packageName);
        }
    }

    private void checkTimeLimitWithLoadedData(String packageName) {
        long dailyUsage = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L); // Default to 0 (blocked)

        Log.d(TAG, packageName + " - Usage: " + (dailyUsage/60000) + "min, Limit: " + (limit/60000) + "min");

        // IMMEDIATE BLOCKING: If limit is 0 minutes, block immediately (unless temporary access)
        if (limit == 0) {
            if (hasValidTemporaryAccess(packageName)) {
                Log.d(TAG, "⏳ " + packageName + " has 0-minute limit BUT temporary access granted - ALLOWING");
                return; // Don't block, temporary access overrides 0-minute limit
            } else {
                Log.d(TAG, "🚫 IMMEDIATE BLOCK: " + packageName + " has 0-minute limit and no temporary access");
                blockApp(packageName, dailyUsage, limit);
                return;
            }
        }

        // For non-zero limits, check if usage exceeded
        if (dailyUsage >= limit) {
            if (hasValidTemporaryAccess(packageName)) {
                Log.d(TAG, "⏳ " + packageName + " exceeded limit BUT temporary access granted - ALLOWING");
                return; // Don't block, temporary access overrides limit
            } else {
                blockApp(packageName, dailyUsage, limit);
            }
        } else {
            // Show remaining time warning
            long remainingTime = limit - dailyUsage;
            if (remainingTime <= 5 * 60 * 1000) { // 5 minutes warning
                showTimeWarning(packageName, remainingTime);
            }
        }
    }

    private void loadAppLimit(String packageName, Runnable onComplete) {
        if (currentUserId == null) return;

        // Load from appLimits collection where partnerId = currentUserId (set by accountability partner)
        db.collection("appLimits")
                .whereEqualTo("partnerId", currentUserId)
                .whereEqualTo("packageName", packageName)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Long limitMinutes = queryDocumentSnapshots.getDocuments().get(0).getLong("dailyLimitMinutes");
                        long limitMillis = (limitMinutes != null ? limitMinutes : 0) * 60 * 1000L;
                        appLimits.put(packageName, limitMillis);
                        Log.d(TAG, "Loaded limit for " + packageName + ": " + (limitMinutes != null ? limitMinutes : 0) + " minutes");
                    } else {
                        // No specific limit set by partner, use default limit for restricted apps
                        // This allows temporary access to work properly
                        long defaultLimit = 30 * 60 * 1000L; // 30 minutes default
                        appLimits.put(packageName, defaultLimit);
                        Log.d(TAG, "No specific limit for " + packageName + ", using default: 30 minutes");
                    }
                    if (onComplete != null) onComplete.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load app limit for " + packageName, e);
                    appLimits.put(packageName, 0L); // Default to blocked on error
                    if (onComplete != null) onComplete.run();
                });
    }

    private void blockApp(String packageName, long usedTime, long limit) {
        Log.d(TAG, "Blocking app: " + packageName + ". Used: " + (usedTime/60000) + "min, Limit: " + (limit/60000) + "min");

        // Mark app as blocked
        lastBlockTime.put(packageName, System.currentTimeMillis());

        // Close the app by going to home screen
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);

        // Show blocking message
        showBlockedMessage(packageName, usedTime, limit);

        // Start persistent blocking enforcement
        scheduleBlockEnforcement(packageName);

        // Notify accountability partner
        notifyPartner(packageName, usedTime, limit);
    }

    private void scheduleBlockEnforcement(String packageName) {
        // Cancel any existing enforcement for this app
        handler.removeCallbacks(blockEnforcer);

        // Schedule repeated checks to prevent reopening
        blockEnforcer = new Runnable() {
            private int attempts = 0;
            private final int maxAttempts = 60; // Monitor for 5 minutes (60 * 5 seconds)

            @Override
            public void run() {
                if (attempts >= maxAttempts) return;

                // Check if app still needs blocking (respects temporary access)
                if (currentForegroundApp.equals(packageName) && isAppCurrentlyBlocked(packageName)) {
                    Log.d(TAG, "Re-blocking persistent app: " + packageName);
                    blockAppImmediately(packageName);
                } else if (currentForegroundApp.equals(packageName) && hasValidTemporaryAccess(packageName)) {
                    Log.d(TAG, "Stopping block enforcement - temporary access active for: " + packageName);
                    return; // Stop the enforcement loop
                }

                attempts++;
                handler.postDelayed(this, 5000); // Check every 5 seconds
            }
        };
        handler.postDelayed(blockEnforcer, 5000);
    }

    private void cancelBlockEnforcement() {
        if (blockEnforcer != null) {
            handler.removeCallbacks(blockEnforcer);
            Log.d(TAG, "🛑 Block enforcement cancelled");
        }
    }

    private void showPersistentBlockMessage(String packageName) {
        String appName = getAppName(packageName);
        long usedTime = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L);

        // Show the blocking screen activity
        Intent blockIntent = new Intent(this, AppBlockedActivity.class);
        blockIntent.putExtra("packageName", packageName);
        blockIntent.putExtra("appName", appName);
        blockIntent.putExtra("usedTime", usedTime);
        blockIntent.putExtra("timeLimit", limit);
        blockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                           Intent.FLAG_ACTIVITY_CLEAR_TOP |
                           Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(blockIntent);

        // Also show a brief toast for immediate feedback
        String quickMessage;
        if (limit == 0) {
            quickMessage = "🚫 " + appName + " is blocked by your partner";
        } else {
            long usedMinutes = usedTime / 60000;
            long limitMinutes = limit / 60000;
            quickMessage = "🚫 " + appName + " limit exceeded (" + usedMinutes + "/" + limitMinutes + "min)";
        }
        Toast.makeText(this, quickMessage, Toast.LENGTH_SHORT).show();
    }

    private void showTimeWarning(String packageName, long remainingTime) {
        String appName = getAppName(packageName);
        long minutes = remainingTime / 60000;
        long seconds = (remainingTime % 60000) / 1000;

        String message = "⚠️ " + appName + " - " + minutes + ":" + String.format("%02d", seconds) + " remaining";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showBlockedMessage(String packageName, long usedTime, long limit) {
        String appName = getAppName(packageName);
        long usedMinutes = usedTime / 60000;
        long limitMinutes = limit / 60000;

        String message = "🚫 " + appName + " BLOCKED!\n" +
                        "Used: " + usedMinutes + " min / " + limitMinutes + " min\n" +
                        "Your accountability partner has been notified.";

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void notifyPartner(String packageName, long usedTime, long limit) {
        if (currentUserId == null) return;

        // Get user's main partner
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String partnerId = documentSnapshot.getString("mainPartnerId");
                    if (partnerId != null) {
                        sendPartnerNotification(partnerId, packageName, usedTime, limit);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get partner info", e);
                });
    }

    private void sendPartnerNotification(String partnerId, String packageName, long usedTime, long limit) {
        // Create notification document in Firestore
        Map<String, Object> notification = new java.util.HashMap<>();
        notification.put("userId", currentUserId);
        notification.put("partnerId", partnerId);
        notification.put("appPackage", packageName);
        notification.put("appName", getAppName(packageName));
        notification.put("usedTime", usedTime);
        notification.put("timeLimit", limit);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("type", "app_limit_exceeded");
        notification.put("status", "pending");

        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Partner notification sent: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to send partner notification", e);
                });
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
            ).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

        @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
        // Handle service interruption
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Clean up listeners
        if (temporaryAccessListener != null) {
            temporaryAccessListener.remove();
        }

        // Stop handlers
        if (handler != null) {
            handler.removeCallbacks(usageChecker);
            handler.removeCallbacks(blockEnforcer);
        }

        Log.d(TAG, "AppMonitoringService destroyed");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "🚀 SERVICE CONNECTED - Initializing AppMonitoringService");

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            Log.d(TAG, "✅ USER AUTHENTICATED: " + currentUser.getEmail() + " (UID: " + currentUserId + ")");
        } else {
            Log.d(TAG, "❌ NO USER AUTHENTICATED - Service will not block apps");
        }

        // Configure accessibility service
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        Log.d(TAG, "🔧 ACCESSIBILITY SERVICE configured");
        Toast.makeText(this, "🔍 App monitoring started", Toast.LENGTH_SHORT).show();

        // Start periodic usage checking
        startUsageMonitoring();
    }

    private void startUsageMonitoring() {
        loadUserRestrictedApps();
        setupTemporaryAccessListener();

        usageChecker = new Runnable() {
            @Override
            public void run() {
                // Check if we need to reset daily usage (new day)
                checkDailyReset();

                // Update current app usage continuously
                updateCurrentAppUsage();

                // Refresh app limits periodically
                refreshAppLimits();

                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(usageChecker);
    }

    private void updateCurrentAppUsage() {
        // Only track if we have a current app running
        if (currentForegroundApp.isEmpty() || currentAppStartTime == 0) return;

        long currentTime = System.currentTimeMillis();
        long sessionDuration = currentTime - currentAppStartTime;

        // Update total usage for today
        long currentUsage = totalUsageToday.getOrDefault(currentForegroundApp, 0L);
        long newTotalUsage = currentUsage + sessionDuration;
        totalUsageToday.put(currentForegroundApp, newTotalUsage);

        // Reset the start time for next interval
        currentAppStartTime = currentTime;

        Log.d(TAG, currentForegroundApp + " continuous tracking: +" + (sessionDuration/1000) + "s, " +
                  "Total today: " + (newTotalUsage/60000) + "min");

        // Check if current app has exceeded limits
        checkAppRestrictions(currentForegroundApp);
    }

    private void loadUserRestrictedApps() {
        if (currentUserId == null) return;

        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> selectedApps = (List<String>) documentSnapshot.get("selectedApps");
                        if (selectedApps != null) {
                            Log.d(TAG, "Loaded " + selectedApps.size() + " restricted apps for monitoring");
                            // Pre-load limits for all restricted apps
                            for (String packageName : selectedApps) {
                                loadAppLimit(packageName, null);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load user's restricted apps", e);
                });
    }

    private void setupTemporaryAccessListener() {
        if (currentUserId == null || db == null) return;

        Log.d(TAG, "🔗 Setting up temporary access listener for user: " + currentUserId);

        temporaryAccessListener = db.collection("users").document(currentUserId)
                .collection("temporaryAccess")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Temporary access listener failed", e);
                        return;
                    }

                    if (snapshots != null) {
                        for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                                dc.getType() == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {

                                com.google.firebase.firestore.DocumentSnapshot document = dc.getDocument();
                                String packageName = document.getId();
                                Long expiresAt = document.getLong("expiresAt");

                                if (expiresAt != null && System.currentTimeMillis() < expiresAt) {
                                    // Cache the temporary access
                                    temporaryAccessExpiry.put(packageName, expiresAt);
                                    lastBlockTime.remove(packageName);

                                    // Stop any active blocking enforcement for this app
                                    cancelBlockEnforcement();

                                    Log.d(TAG, "🔓 Temporary access granted for " + packageName +
                                         " (expires in " + ((expiresAt - System.currentTimeMillis()) / 1000) + "s)");

                                    // Show notification
                                    handler.post(() -> {
                                        String appName = packageName.substring(packageName.lastIndexOf('.') + 1);
                                        Toast.makeText(this,
                                            "🔓 " + appName + " unlocked temporarily",
                                            Toast.LENGTH_SHORT).show();
                                    });
                                }
                            } else if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                // Access revoked or expired
                                String packageName = dc.getDocument().getId();
                                temporaryAccessExpiry.remove(packageName);
                                Log.d(TAG, "🔒 Temporary access revoked for " + packageName);
                            }
                        }
                    }
                });
    }

    private void checkDailyReset() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastResetDay = prefs.getLong("last_reset_day", 0);
        long currentDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000); // Current day number

        if (currentDay != lastResetDay) {
            Log.d(TAG, "New day detected, resetting usage statistics");
            totalUsageToday.clear();
            lastBlockTime.clear();

            prefs.edit().putLong("last_reset_day", currentDay).apply();

            Toast.makeText(this, "📊 Daily app usage reset - fresh start!", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAppLimits() {
        // Periodically refresh app limits in case partner changed them
        if (currentUserId != null && !appLimits.isEmpty()) {
            for (String packageName : appLimits.keySet()) {
                loadAppLimit(packageName, null);
            }
        }
    }
}
