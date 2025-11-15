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
        adapter = new AppAdapter(this, appList);
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
