package com.example.accountable;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartnerControlActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String partnerId;
    private String partnerEmail;
    private String partnerName;
    
    private RecyclerView appsRecyclerView;
    private TextView headerText;
    private AppLimitAdapter adapter;
    private List<AppLimitInfo> appsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_partner_control);

        db = FirebaseFirestore.getInstance();
        
        // Get partner info from intent
        partnerId = getIntent().getStringExtra("partnerId");
        partnerEmail = getIntent().getStringExtra("partnerEmail");
        partnerName = getIntent().getStringExtra("partnerName");
        
        if (partnerId == null) {
            finish();
            return;
        }

        setupToolbar();
        initViews();
        loadPartnerApps();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Manage " + partnerName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void initViews() {
        headerText = findViewById(R.id.headerText);
        appsRecyclerView = findViewById(R.id.appsRecyclerView);
        
        headerText.setText("Set daily time limits for " + partnerName + "'s apps.\n" +
                          "These limits will be enforced automatically.");
        
        adapter = new AppLimitAdapter(appsList, this::onLimitChanged);
        appsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        appsRecyclerView.setAdapter(adapter);
    }
    
    private void loadPartnerApps() {
        // Get the partner's selected apps
        db.collection("users").document(partnerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        List<String> selectedApps = (List<String>) documentSnapshot.get("selectedApps");
                        if (selectedApps != null && !selectedApps.isEmpty()) {
                            loadAppDetails(selectedApps);
                        } else {
                            showEmptyState();
                        }
                    } else {
                        showEmptyState();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load partner's apps: " + e.getMessage(), 
                                 Toast.LENGTH_LONG).show();
                });
    }
    
    private void loadAppDetails(List<String> packageNames) {
        appsList.clear();
        
        for (String packageName : packageNames) {
            try {
                String appName = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(packageName, 0)
                ).toString();
                
                AppLimitInfo appInfo = new AppLimitInfo();
                appInfo.packageName = packageName;
                appInfo.appName = appName;
                appInfo.dailyLimitMinutes = 0; // Default to 0 minutes (blocked)
                
                appsList.add(appInfo);
            } catch (Exception e) {
                // App not found, skip it
            }
        }
        
        adapter.notifyDataSetChanged();
        loadStoredLimits();
    }
    
    private void loadStoredLimits() {
        // Load existing limits from Firestore
        db.collection("appLimits")
                .whereEqualTo("partnerId", partnerId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String packageName = doc.getString("packageName");
                        Long limitMinutes = doc.getLong("dailyLimitMinutes");
                        
                        // Update the app in our list
                        for (AppLimitInfo app : appsList) {
                            if (app.packageName.equals(packageName)) {
                                app.dailyLimitMinutes = limitMinutes != null ? limitMinutes.intValue() : 0;
                                break;
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load existing limits", Toast.LENGTH_SHORT).show();
                });
    }
    
    private int getStoredLimit(String packageName) {
        // Default 0 minutes (blocked) - will be updated when we load from Firestore
        return 0;
    }
    
    private void showEmptyState() {
        headerText.setText(partnerName + " hasn't selected any apps for accountability yet.\n\n" +
                          "Ask them to:\n" +
                          "1. Open the Accountable app\n" +
                          "2. Go to 'My Apps'\n" +
                          "3. Select apps they want help with\n" +
                          "4. Save their selection");
    }
    
    private void onLimitChanged(AppLimitInfo app, int newLimitMinutes) {
        // Save the new limit to Firestore
        Map<String, Object> limitData = new HashMap<>();
        limitData.put("partnerId", partnerId);
        limitData.put("packageName", app.packageName);
        limitData.put("appName", app.appName);
        limitData.put("dailyLimitMinutes", newLimitMinutes);
        limitData.put("updatedAt", System.currentTimeMillis());
        
        // Use partnerId_packageName as document ID for easy updates
        String docId = partnerId + "_" + app.packageName.replace(".", "_");
        
        db.collection("appLimits").document(docId)
                .set(limitData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, app.appName + " limit set to " + newLimitMinutes + " minutes", 
                                 Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to save limit: " + e.getMessage(), 
                                 Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    // Data class for app limit information
    public static class AppLimitInfo {
        public String packageName;
        public String appName;
        public int dailyLimitMinutes = 15; // Default 15 minutes
    }
}