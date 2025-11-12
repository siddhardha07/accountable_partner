package com.example.accountable;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Objects;

public class DatabaseDebugActivity extends AppCompatActivity {
    private static final String TAG = "DatabaseDebug";
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;
    private TextView debugOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database_debug);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = currentUser.getUid();
        
        initViews();
        debugDatabase();
    }

    private void initViews() {
        debugOutput = findViewById(R.id.debugOutput);
        Button refreshButton = findViewById(R.id.refreshButton);
        Button fixDataButton = findViewById(R.id.fixDataButton);
        
        refreshButton.setOnClickListener(v -> debugDatabase());
        fixDataButton.setOnClickListener(v -> fixPartnershipData());
    }

    private void debugDatabase() {
        StringBuilder output = new StringBuilder();
        output.append("🔍 DATABASE DEBUG REPORT\n");
        output.append("========================\n\n");
        output.append("Current User ID: ").append(currentUserId.substring(0, 8)).append("...\n\n");

        // Check all users in database
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    output.append("📋 ALL USERS IN DATABASE:\n");
                    output.append("Users found: ").append(queryDocumentSnapshots.size()).append("\n\n");
                    
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String userId = doc.getId();
                        String email = doc.getString("email");
                        String displayName = doc.getString("displayName");
                        String mainPartnerId = doc.getString("mainPartnerId");
                        Object partners = doc.get("partners");
                        
                        output.append("👤 User: ").append(userId.substring(0, 8)).append("...\n");
                        output.append("   Email: ").append(email != null ? email : "NULL").append("\n");
                        output.append("   Name: ").append(displayName != null ? displayName : "NULL").append("\n");
                        output.append("   Main Partner: ").append(mainPartnerId != null ? mainPartnerId.substring(0, 8) + "..." : "NULL").append("\n");
                        output.append("   Partners List: ").append(partners != null ? partners.toString() : "NULL").append("\n");
                        
                        if (userId.equals(currentUserId)) {
                            output.append("   ⭐ THIS IS YOU!\n");
                        }
                        
                        output.append("\n");
                    }
                    
                    output.append("\n🔗 PARTNERSHIP ANALYSIS:\n");
                    output.append("========================\n");
                    
                    // Find people who chose current user as their partner
                    int peopleYouHelp = 0;
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String mainPartnerId = doc.getString("mainPartnerId");
                        if (mainPartnerId != null && mainPartnerId.equals(currentUserId)) {
                            peopleYouHelp++;
                            output.append("✅ ").append(doc.getString("email")).append(" chose you as partner\n");
                        }
                    }
                    
                    if (peopleYouHelp == 0) {
                        output.append("❌ No one has chosen you as their accountability partner\n");
                    }
                    
                    output.append("\nPeople you should see in 'People I Help': ").append(peopleYouHelp).append("\n");
                    
                    debugOutput.setText(output.toString());
                })
                .addOnFailureListener(e -> {
                    output.append("❌ Failed to load users: ").append(e.getMessage()).append("\n");
                    debugOutput.setText(output.toString());
                });
    }
    
    private void fixPartnershipData() {
        Toast.makeText(this, "🔧 Creating test partnership data...", Toast.LENGTH_SHORT).show();
        
        // For now, just refresh to see current state
        debugDatabase();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}