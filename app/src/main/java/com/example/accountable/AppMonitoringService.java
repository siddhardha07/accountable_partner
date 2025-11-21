package com.example.accountable;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AppMonitoringService extends AccessibilityService {

    private static final String TAG = "AppMonitoringService";
    private static final String PREFS_NAME = "app_monitoring";
    private static final long CHECK_INTERVAL = 5000L;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;

    private final Map<String, Long> appStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> totalUsageToday = new ConcurrentHashMap<>();
    private final Map<String, Long> appLimits = new ConcurrentHashMap<>();
    private final Map<String, Long> lastBlockTime = new ConcurrentHashMap<>();
    private Map<String, Long> remainingWallet = new ConcurrentHashMap<>();
    private Map<String, Long> lastWalletUpdate = new ConcurrentHashMap<>();
    private Map<String, Long> walletSessionStartTime = new ConcurrentHashMap<>();

    private final Set<String> cachedBlockedApps = ConcurrentHashMap.newKeySet();
    private final Set<String> userSelectedApps = ConcurrentHashMap.newKeySet();

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable usageChecker;
    private Runnable blockEnforcer;

    // Firestore listener for temporary access
    private com.google.firebase.firestore.ListenerRegistration temporaryAccessListener;

    private String currentForegroundApp = "";
    private long currentAppStartTime = 0L;
    private boolean isBlocking = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {

            if (event.getPackageName() != null) {
                String packageName = event.getPackageName().toString();
                boolean isSystem = isSystemApp(packageName);
                boolean isOurApp = packageName.equals(getPackageName());
                if (isSystem || isOurApp) {
                    return;
                }
                handleAppSwitch(packageName);
            }
        }
    }

    private boolean isSystemApp(String packageName) {
        return packageName.startsWith("android.")
                || packageName.equals("com.android.systemui")
                || packageName.equals("com.android.launcher")
                || packageName.equals("com.android.launcher3")
                || packageName.equals("com.android.settings")
                || packageName.equals("com.google.android.googlequicksearchbox")
                || packageName.equals("com.android.inputmethod.latin")
                || packageName.equals("com.android.phone")
                || packageName.equals("com.android.dialer");
    }

    private void handleAppSwitch(String newPackageName) {
        long now = System.currentTimeMillis();
        Log.d(TAG, "App switch: " + currentForegroundApp + " -> " + newPackageName);

        // Handle previous app usage tracking (wallet deduction handled in updateCurrentAppUsage)
        if (!currentForegroundApp.isEmpty() && currentAppStartTime > 0) {
            long sessionDuration = now - currentAppStartTime;
            // Only update regular usage tracking - wallet deduction is handled by updateCurrentAppUsage() timer
            updateAppUsage(currentForegroundApp, sessionDuration);
        }

        // Check if we're trying to open a blocked app - THIS IS THE KEY CHECK
        boolean shouldBlock = isAppCurrentlyBlocked(newPackageName);
        Log.d(TAG, "Should block " + newPackageName + ": " + shouldBlock);

        if (shouldBlock) {
            Log.d(TAG, "BLOCKING " + newPackageName);
            blockAppImmediately(newPackageName);
            return;
        }

        // Start tracking new app
        Log.d(TAG, "ALLOWING " + newPackageName);
        currentForegroundApp = newPackageName;
        currentAppStartTime = now;
        checkAppRestrictions(newPackageName);
    }

    private boolean isAppCurrentlyBlocked(String packageName) {
        if (currentUserId == null) {
            Log.d(TAG, "No userId - not blocking " + packageName);
            return false;
        }

        // First check if app is in user's selected apps
        if (!isAppInUserSelection(packageName)) {
            cachedBlockedApps.remove(packageName);
            Log.d(TAG, packageName + " not in user selection - not blocking");
            return false;
        }

        // Check for wallet-based temporary access first - THIS IS CRITICAL
        if (hasRemainingTime(packageName)) {
            cachedBlockedApps.remove(packageName);
            long remaining = remainingWallet.getOrDefault(packageName, 0L);
            Log.d(TAG, packageName + " has wallet time remaining: " + (remaining / 1000) + "s - not blocking");
            return false;
        }

        // Fast path: check cache
        if (cachedBlockedApps.contains(packageName)) {
            Log.d(TAG, packageName + " in blocked cache - blocking");
            return true;
        }

        // Slow path: check if app should be blocked
        Long lastBlocked = lastBlockTime.get(packageName);
        if (lastBlocked == null) {
            Log.d(TAG, packageName + " never blocked - not blocking");
            return false;
        }

        boolean sameDay = isSameDay(lastBlocked, System.currentTimeMillis());
        boolean overLimit = isAppOverLimit(packageName);
        boolean isBlocked = sameDay && overLimit;

        if (isBlocked) {
            cachedBlockedApps.add(packageName);
            Log.d(TAG, packageName + " over limit and same day - blocking");
        } else {
            Log.d(TAG, packageName + " not blocked (sameDay:" + sameDay + ", overLimit:" + overLimit + ")");
        }

        return isBlocked;
    }

    private boolean hasRemainingTime(String packageName) {
        long remaining = remainingWallet.getOrDefault(packageName, 0L);
        return remaining > 0;
    }

    private boolean isAppInUserSelection(String packageName) {
        return userSelectedApps.contains(packageName);
    }

    public void refreshTemporaryAccess(String packageName) {
        if (currentUserId == null || db == null) {
            return;
        }

        db.collection("users").document(currentUserId)
                .collection("temporaryAccess").document(packageName)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long remaining = documentSnapshot.getLong("remainingMillis");
                        if (remaining != null && remaining > 0) {

                            // CRITICAL FIX: Only update wallet time if not currently being tracked
                            if (!remainingWallet.containsKey(packageName)) {
                                remainingWallet.put(packageName, remaining);
                                lastWalletUpdate.put(packageName, System.currentTimeMillis());
                                walletSessionStartTime.put(packageName, System.currentTimeMillis());
                            } else {
                                // Already tracking - only update if less time (security)
                                long currentLocal = remainingWallet.getOrDefault(packageName, 0L);
                                if (remaining < currentLocal) {
                                    remainingWallet.put(packageName, remaining);
                                }
                                lastWalletUpdate.put(packageName, System.currentTimeMillis());
                            }

                            lastBlockTime.remove(packageName);
                            cachedBlockedApps.remove(packageName);

                            handler.post(() -> {
                                String appName = packageName.substring(packageName.lastIndexOf('.') + 1);
                                Toast.makeText(this, appName + " unlocked (" + (remaining / 60000) + " min)", Toast.LENGTH_SHORT).show();
                            });

                        } else {
                            documentSnapshot.getReference().delete();
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to refresh temporary access for " + packageName, e));
    }

    private void checkTemporaryAccessAsync(String packageName) {
        if (currentUserId == null || db == null) {
            return;
        }

        db.collection("users").document(currentUserId)
                .collection("temporaryAccess").document(packageName)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Long remaining = documentSnapshot.getLong("remainingMillis");

                        if (remaining != null && remaining > 0) {
                            // CRITICAL FIX: Only update wallet time if not currently being tracked
                            if (!remainingWallet.containsKey(packageName)) {
                                remainingWallet.put(packageName, remaining);
                                lastWalletUpdate.put(packageName, System.currentTimeMillis());
                                walletSessionStartTime.put(packageName, System.currentTimeMillis());
                            } else {
                                // Already tracking - only update if less time (security)
                                long currentLocal = remainingWallet.getOrDefault(packageName, 0L);
                                if (remaining < currentLocal) {
                                    remainingWallet.put(packageName, remaining);
                                }
                                lastWalletUpdate.put(packageName, System.currentTimeMillis());
                            }
                            lastBlockTime.remove(packageName);

                            handler.post(() -> {
                                String appName = packageName.substring(packageName.lastIndexOf('.') + 1);
                                Toast.makeText(this, "Temporary access active (" + (remaining / 60000) + " min)", Toast.LENGTH_SHORT).show();
                            });

                        } else {
                            documentSnapshot.getReference().delete();
                        }
                    }
                });
    }

    private boolean isSameDay(long t1, long t2) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTimeInMillis(t1);
        c2.setTimeInMillis(t2);
        return c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR) && c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR);
    }

    private boolean isAppOverLimit(String packageName) {
        long dailyUsage = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L);
        return dailyUsage >= limit;
    }

    private void blockAppImmediately(String packageName) {
        isBlocking = true;
        showBlockScreenImmediately(packageName);
        scheduleBlockEnforcement(packageName);
        isBlocking = false;
    }

    private void updateAppUsage(String packageName, long sessionDuration) {
        long current = totalUsageToday.getOrDefault(packageName, 0L);
        totalUsageToday.put(packageName, current + sessionDuration);
        Log.d(TAG, packageName + " used for " + (sessionDuration / 1000) + " seconds. Total today: " + ((current + sessionDuration) / 60000) + " minutes");
    }

    private void checkAppRestrictions(String packageName) {
        if (currentUserId == null) {
            return;
        }

        // CRITICAL: Don't check restrictions if wallet time is active
        if (hasRemainingTime(packageName)) {
            long walletTime = remainingWallet.getOrDefault(packageName, 0L);
            Log.d(TAG, packageName + " has wallet time (" + (walletTime / 1000) + "s) - skipping restriction checks");
            return;
        }

        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> selectedApps = (List<String>) documentSnapshot.get("selectedApps");
                        if (selectedApps != null && selectedApps.contains(packageName)) {
                            Log.d(TAG, "🔍 " + packageName + " is restricted - checking time limits");
                            checkTimeLimit(packageName);
                        } else {
                            Log.d(TAG, "✅ " + packageName + " not in restricted apps - allowing");
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "❌ Failed to check app restrictions for " + packageName, e));
    }

    private void checkTimeLimit(String packageName) {
        if (!appLimits.containsKey(packageName)) {
            loadAppLimit(packageName, () -> checkTimeLimitWithLoadedData(packageName));
        } else {
            checkTimeLimitWithLoadedData(packageName);
        }
    }

    private void checkTimeLimitWithLoadedData(String packageName) {
        // CRITICAL FIX: Check wallet time FIRST before applying regular limits
        if (hasRemainingTime(packageName)) {
            long walletTime = remainingWallet.getOrDefault(packageName, 0L);
            Log.d(TAG, packageName + " has wallet time (" + (walletTime / 1000) + "s) - skipping regular limit checks");
            return; // Skip all regular limit checks if wallet time exists
        }

        long dailyUsage = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L);

        Log.d(TAG, "Checking regular limits for " + packageName + ": usage=" + (dailyUsage / 60000) + "min, limit=" + (limit / 60000) + "min");

        if (limit == 0) {
            Log.d(TAG, packageName + " has 0-minute limit - blocking");
            blockApp(packageName, dailyUsage, limit);
            return;
        }
        if (dailyUsage >= limit) {
            Log.d(TAG, packageName + " over daily limit - blocking");
            blockApp(packageName, dailyUsage, limit);
        } else {
            long remaining = limit - dailyUsage;
            Log.d(TAG, packageName + " within limits - " + (remaining / 60000) + "min remaining");
            if (remaining <= 5 * 60 * 1000) {
                showTimeWarning(packageName, remaining);
            }
        }
    }

    private void loadAppLimit(String packageName, Runnable onComplete) {
        if (currentUserId == null) {
            return;
        }
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
                        long defaultLimit = 30 * 60 * 1000L;
                        appLimits.put(packageName, defaultLimit);
                        Log.d(TAG, "No specific limit for " + packageName + ", using default: 30 minutes");
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load app limit for " + packageName, e);
                    appLimits.put(packageName, 0L);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
    }

    private void blockApp(String packageName, long usedTime, long limit) {
        lastBlockTime.put(packageName, System.currentTimeMillis());
        cachedBlockedApps.add(packageName);

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(homeIntent);

        showBlockedMessage(packageName, usedTime, limit);
        scheduleBlockEnforcement(packageName);
        notifyPartner(packageName, usedTime, limit);
    }

    private void scheduleBlockEnforcement(String packageName) {
        handler.removeCallbacks(blockEnforcer);
        blockEnforcer = new Runnable() {
            private int attempts = 0;
            private final int maxAttempts = 60;

            @Override
            public void run() {
                if (attempts >= maxAttempts) {
                    return;
                }
                if (currentForegroundApp.equals(packageName)) {
                    if (isAppCurrentlyBlocked(packageName)) {
                        Log.d(TAG, "Re-blocking persistent app: " + packageName);
                        blockAppImmediately(packageName);
                    } else {
                        Log.d(TAG, "Stopping block enforcement - wallet active for: " + packageName);
                        return;
                    }
                }
                attempts++;
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(blockEnforcer, 1000);
    }

    private void cancelBlockEnforcement() {
        if (blockEnforcer != null) {
            handler.removeCallbacks(blockEnforcer);
        }
    }

    private void showBlockScreenImmediately(String packageName) {
        String appName = getAppName(packageName);
        long usedTime = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L);

        Intent blockIntent = new Intent(this, AppBlockedActivity.class);
        blockIntent.putExtra("packageName", packageName);
        blockIntent.putExtra("appName", appName);
        blockIntent.putExtra("usedTime", usedTime);
        blockIntent.putExtra("timeLimit", limit);
        blockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                | Intent.FLAG_ACTIVITY_NO_HISTORY
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(blockIntent);
    }

    private void showPersistentBlockMessage(String packageName) {
        String appName = getAppName(packageName);
        long usedTime = totalUsageToday.getOrDefault(packageName, 0L);
        long limit = appLimits.getOrDefault(packageName, 0L);

        Intent blockIntent = new Intent(this, AppBlockedActivity.class);
        blockIntent.putExtra("packageName", packageName);
        blockIntent.putExtra("appName", appName);
        blockIntent.putExtra("usedTime", usedTime);
        blockIntent.putExtra("timeLimit", limit);
        blockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(blockIntent);
        Toast.makeText(this, "Access blocked", Toast.LENGTH_SHORT).show();
    }

    private void showTimeWarning(String packageName, long remainingTime) {
        String appName = getAppName(packageName);
        long minutes = remainingTime / 60000;
        long seconds = (remainingTime % 60000) / 1000;
        String message = "⚠️ " + appName + " - " + minutes + ":" + String.format("%02d", seconds) + " remaining";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showBlockedMessage(String packageName, long usedTime, long limit) {
        Toast.makeText(this, "Access blocked - Partner notified", Toast.LENGTH_LONG).show();
    }

    private void notifyPartner(String packageName, long usedTime, long limit) {
        if (currentUserId == null) {
            return;
        }
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String partnerId = documentSnapshot.getString("mainPartnerId");
                    if (partnerId != null) {
                        sendPartnerNotification(partnerId, packageName, usedTime, limit);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get partner info", e));
    }

    private void sendPartnerNotification(String partnerId, String packageName, long usedTime, long limit) {
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
                .addOnSuccessListener(documentReference -> Log.d(TAG, "Partner notification sent: " + documentReference.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to send partner notification", e));
    }

    private String getAppName(String packageName) {
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (temporaryAccessListener != null) {
            temporaryAccessListener.remove();
        }
        if (handler != null) {
            handler.removeCallbacks(usageChecker);
            handler.removeCallbacks(blockEnforcer);
        }
        Log.d(TAG, "AppMonitoringService destroyed");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
            Log.d(TAG, "User authenticated: " + currentUser.getEmail() + " (UID: " + currentUserId + ")");
        } else {
            Log.d(TAG, "❌ NO USER AUTHENTICATED - Service will not block apps");
        }

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        setServiceInfo(info);

        Log.d(TAG, "Accessibility service configured");
        startUsageMonitoring();
    }

    private void startUsageMonitoring() {
        loadUserRestrictedApps();
        setupTemporaryAccessListener();

        usageChecker = new Runnable() {
            @Override
            public void run() {
                checkDailyReset();
                updateCurrentAppUsage();
                refreshAppLimits();
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
        handler.post(usageChecker);
    }

    private void updateCurrentAppUsage() {
        if (currentForegroundApp.isEmpty() || currentAppStartTime == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long sessionDuration = now - currentAppStartTime;

        // Update total usage tracking
        long currentUsage = totalUsageToday.getOrDefault(currentForegroundApp, 0L);
        long newTotal = currentUsage + sessionDuration;
        totalUsageToday.put(currentForegroundApp, newTotal);
        // CRITICAL FIX: Don't reset currentAppStartTime - this was causing wallet deduction to only count 5-second intervals
        // currentAppStartTime should only be reset when app switches, not during continuous usage tracking

        Log.d(TAG, currentForegroundApp + " session: +" + (sessionDuration / 1000) + "s, Total today: " + (newTotal / 60000) + "min");

        // Handle wallet deduction if active - ONLY for current foreground app
        if (remainingWallet.containsKey(currentForegroundApp)) {
            // CRITICAL FIX: Use the 5-second interval (CHECK_INTERVAL) for deduction, not session time
            // This ensures consistent 5-second deductions regardless of session start time manipulation

            long remaining = remainingWallet.getOrDefault(currentForegroundApp, 0L);
            remaining = Math.max(0, remaining - CHECK_INTERVAL);

            // DO NOT reset walletSessionStartTime - keep it stable for the entire session
            Log.d(TAG, "Wallet deduction: -" + (CHECK_INTERVAL / 1000) + "s, remaining: " + (remaining / 1000) + "s for " + currentForegroundApp);

            if (remaining <= 0) {
                remainingWallet.remove(currentForegroundApp);
                walletSessionStartTime.remove(currentForegroundApp);

                Log.d(TAG, "Wallet time expired for " + currentForegroundApp + " - removing access");

                // Update Firestore to remove temporary access
                if (currentUserId != null && db != null) {
                    db.collection("users").document(currentUserId)
                            .collection("temporaryAccess").document(currentForegroundApp)
                            .delete()
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Removed expired wallet access from Firestore"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to remove expired access", e));
                }

                blockAppImmediately(currentForegroundApp);
                return;
            } else {
                remainingWallet.put(currentForegroundApp, remaining);

                // SECURITY FIX: Update Firestore immediately on every deduction to prevent time manipulation
                if (currentUserId != null && db != null) {
                    db.collection("users").document(currentUserId)
                            .collection("temporaryAccess").document(currentForegroundApp)
                            .update("remainingMillis", remaining)
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to update remaining time", e));
                }
            }
        } else {
            // No wallet active, check regular restrictions
            checkAppRestrictions(currentForegroundApp);
        }
    }

    private void loadUserRestrictedApps() {
        if (currentUserId == null) {
            return;
        }
        db.collection("users").document(currentUserId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Failed to listen for user's restricted apps changes", e);
                        return;
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        List<String> selectedApps = (List<String>) documentSnapshot.get("selectedApps");
                        if (selectedApps != null) {
                            Log.d(TAG, "Updated restricted apps list: " + selectedApps.size() + " apps");
                            userSelectedApps.clear();
                            userSelectedApps.addAll(selectedApps);
                            Set<String> currentlySelected = new HashSet<>(selectedApps);
                            Set<String> toRemoveFromCache = new HashSet<>();
                            for (String cachedApp : cachedBlockedApps) {
                                if (!currentlySelected.contains(cachedApp)) {
                                    toRemoveFromCache.add(cachedApp);
                                }
                            }
                            for (String appToRemove : toRemoveFromCache) {
                                cachedBlockedApps.remove(appToRemove);
                                Log.d(TAG, "🗑️ Removed " + appToRemove + " from blocked cache (unchecked)");
                            }
                            for (String packageName : selectedApps) {
                                loadAppLimit(packageName, null);
                            }
                        } else {
                            userSelectedApps.clear();
                            cachedBlockedApps.clear();
                            Log.d(TAG, "🗑️ Cleared all caches (no apps selected)");
                        }
                    }
                });
    }

    private void setupTemporaryAccessListener() {
        if (currentUserId == null || db == null) {
            return;
        }

        Log.d(TAG, "Setting up temporary access listener (wallet based) for user: " + currentUserId);

        temporaryAccessListener = db.collection("users")
                .document(currentUserId)
                .collection("temporaryAccess")
                .addSnapshotListener((snapshots, e) -> {

                    if (e != null) {
                        Log.w(TAG, "Temporary access listener failed", e);
                        return;
                    }

                    if (snapshots == null) {
                        return;
                    }

                    for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {

                        com.google.firebase.firestore.DocumentSnapshot document = dc.getDocument();
                        String packageName = document.getId();

                        switch (dc.getType()) {

                            case ADDED:
                            case MODIFIED: {
                                Long remaining = document.getLong("remainingMillis");

                                if (remaining != null && remaining > 0) {
                                    // CRITICAL FIX: Only update wallet time if not currently being tracked
                                    // This prevents overwriting local deductions and fixes "more time than requested" bug
                                    if (!remainingWallet.containsKey(packageName)) {
                                        // New wallet access - set initial values
                                        remainingWallet.put(packageName, remaining);
                                        lastWalletUpdate.put(packageName, System.currentTimeMillis());
                                        walletSessionStartTime.put(packageName, System.currentTimeMillis());
                                    } else {
                                        // Already tracking - only update if Firestore has LESS time (security)
                                        long currentLocal = remainingWallet.getOrDefault(packageName, 0L);
                                        if (remaining < currentLocal) {
                                            remainingWallet.put(packageName, remaining);
                                        }
                                        lastWalletUpdate.put(packageName, System.currentTimeMillis());
                                    }

                                    // Remove previous block state
                                    lastBlockTime.remove(packageName);
                                    cachedBlockedApps.remove(packageName);

                                    // Stop any active blocking enforcement
                                    if (currentForegroundApp.equals(packageName)) {
                                        handler.removeCallbacks(blockEnforcer);
                                    }

                                    long finalRemaining = remainingWallet.getOrDefault(packageName, remaining);
                                    Log.d(TAG,
                                            "Temporary access (wallet) for "
                                            + packageName + " - remaining: "
                                            + (finalRemaining / 1000) + " seconds");

                                    handler.post(() -> {
                                        String appName = packageName.substring(packageName.lastIndexOf('.') + 1);
                                        long displayRemaining = remainingWallet.getOrDefault(packageName, remaining);
                                        Toast.makeText(this,
                                                appName + " unlocked (" + (displayRemaining / 60000) + " min)",
                                                Toast.LENGTH_SHORT).show();
                                    });
                                } else {
                                    // No remaining time, clean up
                                    remainingWallet.remove(packageName);
                                    lastWalletUpdate.remove(packageName);
                                    walletSessionStartTime.remove(packageName);
                                }
                                break;
                            }

                            case REMOVED: {
                                remainingWallet.remove(packageName);
                                lastWalletUpdate.remove(packageName);
                                walletSessionStartTime.remove(packageName);

                                Log.d(TAG, "Temporary access revoked for " + packageName);

                                // If this app is currently in foreground, block it
                                if (currentForegroundApp.equals(packageName)) {
                                    handler.post(() -> blockAppImmediately(packageName));
                                }
                                break;
                            }
                        }
                    }
                });
    }

    private void checkDailyReset() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastResetDate = prefs.getString("last_reset_date", "");
        Calendar calendar = Calendar.getInstance();
        String currentDate = String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
        if (!currentDate.equals(lastResetDate)) {
            Log.d(TAG, "Midnight reset: " + lastResetDate + " -> " + currentDate);
            totalUsageToday.clear();
            lastBlockTime.clear();
            cachedBlockedApps.clear();
            prefs.edit().putString("last_reset_date", currentDate).apply();
            Log.d(TAG, "Daily limits reset at midnight");
        }
    }

    private void refreshAppLimits() {
        if (currentUserId != null && !appLimits.isEmpty()) {
            for (String packageName : appLimits.keySet()) {
                loadAppLimit(packageName, null);
            }
        }
    }
}
