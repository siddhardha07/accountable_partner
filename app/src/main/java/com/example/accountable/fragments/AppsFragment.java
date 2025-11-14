package com.example.accountable.fragments;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.accountable.AppAdapter;
import com.example.accountable.AppModel;
import com.example.accountable.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppsFragment extends Fragment {

    private RecyclerView appsRecyclerView;
    private SearchView searchView;
    private AppAdapter adapter;
    private List<AppModel> appList = new ArrayList<>();
    private List<AppModel> originalAppList = new ArrayList<>();
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_apps, container, false);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();

        // Initialize views
        appsRecyclerView = view.findViewById(R.id.appsRecyclerView);
        searchView = view.findViewById(R.id.searchView);

        // Setup RecyclerView
        appsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Load apps and setup adapter
        loadInstalledApps();
        adapter = new AppAdapter(getContext(), appList);
        appsRecyclerView.setAdapter(adapter);
        
        // Load saved selections
        loadSavedSelectionFromFirestore();
        
        // Setup search functionality
        setupSearch();        return view;
    }

    private void setupSearch() {
        if (searchView == null) return;
        
        // Setup search functionality with inline search bar
        searchView.setQueryHint("Search apps...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterApps(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterApps(newText);
                return false;
            }
        });
    }    private void filterApps(String query) {
        List<AppModel> filteredList = new ArrayList<>();

        if (TextUtils.isEmpty(query)) {
            filteredList.addAll(originalAppList);
        } else {
            for (AppModel app : originalAppList) {
                if (app.getAppName().toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(app);
                }
            }
        }

        appList.clear();
        appList.addAll(filteredList);

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadInstalledApps() {
        appList.clear();
        originalAppList.clear();

        PackageManager pm = getContext().getPackageManager();

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
                originalAppList.add(model);
            } catch (Exception e) {
                // Skip apps that can't be loaded
            }
        }
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
                            // Set selected = true for matching apps
                            for (AppModel app : appList) {
                                if (selectedPackages.contains(app.getPackageName())) {
                                    app.setSelected(true);
                                }
                            }
                            // Refresh the adapter to show checkboxes
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    // Handle error silently for fragment
                });
    }

    // Auto-save when selections change
    public void saveSelectedAppsToFirestore() {
        if (adapter == null) return;

        List<String> selectedPackages = adapter.getSelectedPackageNames();
        Map<String, Object> data = new HashMap<>();
        data.put("selectedApps", selectedPackages);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        db.collection("users").document(userId)
                .update(data)
                .addOnSuccessListener(aVoid -> {
                    // Auto-saved successfully
                })
                .addOnFailureListener(e -> {
                    // Handle error silently for auto-save
                });
    }
}
