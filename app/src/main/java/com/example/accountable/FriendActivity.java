package com.example.accountable;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FriendActivity extends AppCompatActivity {

    private static final String TAG = "FriendActivity";
    private FirebaseFirestore db;
    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private List<AppModel> restrictedAppsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);

        // Setup toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Friend's Apps");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        db = FirebaseFirestore.getInstance();
        recyclerView = findViewById(R.id.friend_recycler_view);
        restrictedAppsList = new ArrayList<>();

        setupRecyclerView();
        fetchRestrictedApps();
    }

    private void setupRecyclerView() {
        adapter = new AppAdapter(this, restrictedAppsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void fetchRestrictedApps() {
        // Use Firebase Auth to get current user ID
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String userId = currentUser.getUid();

        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Get the package names list that MyAppsActivity saves
                            List<String> selectedPackages = (List<String>) document.get("selectedApps");
                            if (selectedPackages != null && !selectedPackages.isEmpty()) {
                                restrictedAppsList.clear();

                                // Convert package names to AppInfo objects for display
                                PackageManager pm = getPackageManager();
                                for (String packageName : selectedPackages) {
                                    try {
                                        ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                                        String appName = pm.getApplicationLabel(appInfo).toString();
                                        Drawable icon = pm.getApplicationIcon(appInfo);
                                        AppModel model = new AppModel(appName, packageName, icon);
                                        restrictedAppsList.add(model);
                                    } catch (PackageManager.NameNotFoundException e) {
                                        // App might be uninstalled, create basic AppModel
                                        AppModel model = new AppModel(packageName, packageName, null);
                                        restrictedAppsList.add(model);
                                    }
                                }

                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, "Loaded " + restrictedAppsList.size() + " restricted apps.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "No restricted apps found.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.d(TAG, "No such document");
                            Toast.makeText(this, "User has not saved any apps yet.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "Get failed with ", task.getException());
                        Toast.makeText(this, "Failed to load apps.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
