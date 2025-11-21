package com.example.accountable;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataRecoveryActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String currentUserId;
    private TextView recoveryStatus;
    private Button scanButton;
    private Button fixButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_recovery);

        // Initialize Firebase
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }

        setupToolbar();
        initViews();
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Data Recovery");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initViews() {
        recoveryStatus = findViewById(R.id.recoveryStatus);
        scanButton = findViewById(R.id.scanButton);
        fixButton = findViewById(R.id.fixButton);

        recoveryStatus.setText("This tool helps recover partnerships that may have been broken by app selections.\n\nClick 'Scan for Issues' to check your account.");
    }

    private void setupClickListeners() {
        scanButton.setOnClickListener(v -> scanForIssues());
        fixButton.setOnClickListener(v -> fixPartnershipIssues());
    }

    private void scanForIssues() {
        recoveryStatus.setText("🔍 Scanning for partnership issues...");

        if (currentUserId == null) {
            recoveryStatus.setText("❌ Not authenticated");
            return;
        }

        // Check if current user document has all required fields
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        StringBuilder status = new StringBuilder("📋 Scan Results:\n\n");

                        String email = documentSnapshot.getString("email");
                        String displayName = documentSnapshot.getString("displayName");
                        String mainPartnerId = documentSnapshot.getString("mainPartnerId");
                        List<String> partners = (List<String>) documentSnapshot.get("partners");
                        List<String> selectedApps = (List<String>) documentSnapshot.get("selectedApps");

                        // Check each field
                        status.append("✅ User document exists\n");
                        status.append("Email: ").append(email != null ? "✅" : "❌ MISSING").append("\n");
                        status.append("Display Name: ").append(displayName != null ? "✅" : "❌ MISSING").append("\n");
                        status.append("Main Partner: ").append(mainPartnerId != null ? "✅ Set" : "⚠️ None").append("\n");
                        status.append("Partners Array: ").append(partners != null ? "✅" : "❌ MISSING").append("\n");
                        status.append("Selected Apps: ").append(selectedApps != null ? "✅" : "⚠️ None").append("\n");

                        if (email == null || displayName == null || partners == null) {
                            status.append("\n🚨 ISSUES FOUND! Use 'Fix Issues' button.");
                            fixButton.setEnabled(true);
                        } else {
                            status.append("\n✅ No critical issues found.");
                            fixButton.setEnabled(false);
                        }

                        recoveryStatus.setText(status.toString());
                    } else {
                        recoveryStatus.setText("❌ User document does not exist! This is a critical issue.");
                        fixButton.setEnabled(true);
                    }
                })
                .addOnFailureListener(e -> {
                    recoveryStatus.setText("❌ Scan failed: " + e.getMessage());
                });
    }

    private void fixPartnershipIssues() {
        recoveryStatus.setText("🔧 Fixing partnership issues...");

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            return;
        }

        // Create/update user document with all required fields
        Map<String, Object> updates = new HashMap<>();

        if (currentUser.getEmail() != null) {
            updates.put("email", currentUser.getEmail());
        }

        String displayName = currentUser.getDisplayName();
        if (displayName == null && currentUser.getEmail() != null) {
            displayName = currentUser.getEmail().split("@")[0];
        }
        if (displayName != null) {
            updates.put("displayName", displayName);
        }

        updates.put("createdAt", System.currentTimeMillis());
        updates.put("partners", new java.util.ArrayList<String>());

        db.collection("users").document(currentUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    recoveryStatus.setText("✅ Fixed user document! Your account should work properly now.\n\n"
                            + "Note: You may need to re-establish partnerships with your accountability partners.");
                    Toast.makeText(this, "Data recovery completed!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    recoveryStatus.setText("❌ Fix failed: " + e.getMessage()
                            + "\n\nTry creating the document from scratch...");
                    createUserDocumentFromScratch(currentUser);
                });
    }

    private void createUserDocumentFromScratch(FirebaseUser user) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", user.getEmail());
        userProfile.put("displayName", user.getDisplayName() != null ? user.getDisplayName()
                : (user.getEmail() != null ? user.getEmail().split("@")[0] : "User"));
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("mainPartnerId", null);
        userProfile.put("partners", new java.util.ArrayList<String>());

        db.collection("users").document(currentUserId)
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    recoveryStatus.setText("✅ Created new user document! Your account is now properly set up.");
                    Toast.makeText(this, "Account recovered successfully!", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    recoveryStatus.setText("❌ Failed to create user document: " + e.getMessage());
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
