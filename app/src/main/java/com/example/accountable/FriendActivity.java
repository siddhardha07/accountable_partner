package com.example.accountable;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class FriendActivity extends AppCompatActivity {

    private static final String TAG = "FriendActivity";
    private FirebaseFirestore db;
    private RecyclerView recyclerView;
    private FriendAppAdapter adapter;
    private List<AppInfo> restrictedAppsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend);

        db = FirebaseFirestore.getInstance();
        recyclerView = findViewById(R.id.friend_recycler_view);
        restrictedAppsList = new ArrayList<>();

        setupRecyclerView();
        fetchRestrictedApps();
    }

    private void setupRecyclerView() {
        adapter = new FriendAppAdapter(restrictedAppsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void fetchRestrictedApps() {
        String userId = "user1"; // The same static ID

        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            // Firestore returns a list of HashMaps, we convert them to our AppInfo objects
                            List<AppInfo> apps = document.toObject(User.class).selectedApps;
                            if (apps != null) {
                                restrictedAppsList.clear();
                                restrictedAppsList.addAll(apps);
                                adapter.notifyDataSetChanged();
                                Toast.makeText(this, "Loaded " + apps.size() + " apps.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.d(TAG, "No such document");
                            Toast.makeText(this, "User has not saved any apps yet.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(TAG, "get failed with ", task.getException());
                        Toast.makeText(this, "Failed to load apps.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Helper class to match the document structure in Firestore
    private static class User {
        public List<AppInfo> selectedApps;
    }
}