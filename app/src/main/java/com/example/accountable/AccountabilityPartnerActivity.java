package com.example.accountable;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AccountabilityPartnerActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    private TextView currentPartnerText;
    private EditText partnerEmailInput;
    private Button addPartnerButton, removePartnerButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accountability_partner);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        setupToolbar();
        initViews();
        setupClickListeners();

        // First, ensure current user document exists
        ensureCurrentUserExists(currentUser);
        loadCurrentPartner();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Accountability Partner");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void initViews() {
        currentPartnerText = findViewById(R.id.currentPartnerText);
        partnerEmailInput = findViewById(R.id.partnerEmailInput);
        addPartnerButton = findViewById(R.id.addPartnerButton);
        removePartnerButton = findViewById(R.id.removePartnerButton);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        addPartnerButton.setOnClickListener(v -> addPartner());
        removePartnerButton.setOnClickListener(v -> removePartner());
    }

    private void loadCurrentPartner() {
        showProgress(true);

        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showProgress(false);
                    if (documentSnapshot.exists()) {
                        String mainPartnerId = documentSnapshot.getString("mainPartnerId");
                        if (mainPartnerId != null) {
                            loadPartnerDetails(mainPartnerId);
                        } else {
                            currentPartnerText.setText("No accountability partner set");
                            removePartnerButton.setVisibility(View.GONE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to load partner info: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void loadPartnerDetails(String partnerId) {
        db.collection("users").document(partnerId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String partnerEmail = documentSnapshot.getString("email");
                        String partnerName = documentSnapshot.getString("displayName");

                        String displayText = partnerName + " (" + partnerEmail + ")";
                        currentPartnerText.setText("Current Partner: " + displayText);
                        removePartnerButton.setVisibility(View.VISIBLE);
                    } else {
                        currentPartnerText.setText("Partner account not found");
                        removePartnerButton.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    currentPartnerText.setText("Failed to load partner details");
                    removePartnerButton.setVisibility(View.GONE);
                });
    }

    private void addPartner() {
        String partnerEmail = partnerEmailInput.getText().toString().trim();

        if (TextUtils.isEmpty(partnerEmail)) {
            Toast.makeText(this, "Please enter partner's email", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);

        // First, let's check what users exist in the database for debugging
        Toast.makeText(this, "Searching for user: " + partnerEmail, Toast.LENGTH_SHORT).show();

        // Find user by email
        db.collection("users")
                .whereEqualTo("email", partnerEmail)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String partnerId = queryDocumentSnapshots.getDocuments().get(0).getId();

                        if (partnerId.equals(currentUserId)) {
                            showProgress(false);
                            Toast.makeText(this, "You cannot be your own accountability partner!",
                                         Toast.LENGTH_SHORT).show();
                            return;
                        }

                        setMainPartner(partnerId, partnerEmail);
                    } else {
                        // Let's check what users actually exist
                        checkExistingUsers(partnerEmail);
                    }
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to search for user: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void checkExistingUsers(String searchedEmail) {
        db.collection("users")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    showProgress(false);

                    if (queryDocumentSnapshots.isEmpty()) {
                        Toast.makeText(this, "❌ No users found in database! Something is wrong.",
                                     Toast.LENGTH_LONG).show();
                        return;
                    }

                    StringBuilder message = new StringBuilder();
                    message.append("❌ User '").append(searchedEmail).append("' not found!\n\n");
                    message.append("📋 Available users in database:\n");

                    int userCount = 0;
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    String currentUserEmail = currentUser != null ? currentUser.getEmail() : "";

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String email = doc.getString("email");
                        String displayName = doc.getString("displayName");

                        if (email != null) {
                            userCount++;
                            message.append(userCount).append(". ");

                            if (email.equals(currentUserEmail)) {
                                message.append("👤 ").append(email).append(" (YOU)");
                            } else {
                                message.append("👥 ").append(email);
                            }

                            if (displayName != null && !displayName.isEmpty()) {
                                message.append(" - ").append(displayName);
                            }
                            message.append("\n");
                        }
                    }

                    message.append("\n💡 Solutions:\n");
                    message.append("• Ask '").append(searchedEmail).append("' to create an account first\n");
                    message.append("• Or use one of the emails above\n");
                    message.append("• Make sure email is typed exactly as shown");

                    Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "❌ Database error: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void ensureCurrentUserExists(FirebaseUser currentUser) {
        Toast.makeText(this, "🔍 Checking user profile...", Toast.LENGTH_SHORT).show();

        // Check if current user document exists, create it if not
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        // User document doesn't exist, create it
                        Toast.makeText(this, "📝 User profile missing, creating it...", Toast.LENGTH_LONG).show();
                        createUserDocument(currentUser);
                    } else {
                        Toast.makeText(this, "✅ User profile exists: " + currentUser.getEmail(), Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    String errorMsg = "❌ Firestore Error: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();

                    // Try to create the document anyway
                    Toast.makeText(this, "🔧 Attempting to create user document directly...", Toast.LENGTH_SHORT).show();
                    createUserDocument(currentUser);
                });
    }

    private void createUserDocument(FirebaseUser user) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", user.getEmail());
        userProfile.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0]);
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("mainPartnerId", null);
        userProfile.put("partners", new java.util.ArrayList<String>());

        String docPath = "users/" + user.getUid();
        Toast.makeText(this, "💾 Creating document at: " + docPath, Toast.LENGTH_SHORT).show();

        db.collection("users").document(user.getUid())
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ SUCCESS! User profile created for: " + user.getEmail(), Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    String detailedError = "❌ CREATE FAILED:\n" +
                                         "• Error Type: " + e.getClass().getSimpleName() + "\n" +
                                         "• Message: " + e.getMessage() + "\n" +
                                         "• User: " + user.getEmail() + "\n" +
                                         "• UID: " + user.getUid();
                    Toast.makeText(this, detailedError, Toast.LENGTH_LONG).show();
                });
    }

    private void setMainPartner(String partnerId, String partnerEmail) {
        Toast.makeText(this, "🔄 Setting up partnership relationships...", Toast.LENGTH_SHORT).show();

        // Update current user's mainPartnerId
        Map<String, Object> updates = new HashMap<>();
        updates.put("mainPartnerId", partnerId);

        db.collection("users").document(currentUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Step 1: You now have " + partnerEmail + " as your partner",
                                 Toast.LENGTH_SHORT).show();

                    // Add current user to partner's partners list
                    db.collection("users").document(partnerId)
                            .update("partners", FieldValue.arrayUnion(currentUserId))
                            .addOnSuccessListener(aVoid2 -> {
                                showProgress(false);
                                Toast.makeText(this, "✅ Step 2: " + partnerEmail + " can now see you in 'People I Help'!",
                                             Toast.LENGTH_LONG).show();
                                partnerEmailInput.setText("");
                                loadCurrentPartner();
                            })
                            .addOnFailureListener(e -> {
                                showProgress(false);
                                Toast.makeText(this, "❌ Step 2 failed: " + e.getMessage(),
                                             Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "❌ Step 1 failed: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void removePartner() {
        showProgress(true);

        // Get current partner ID first
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    String mainPartnerId = documentSnapshot.getString("mainPartnerId");
                    if (mainPartnerId != null) {
                        // Remove partner relationship
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("mainPartnerId", null);

                        db.collection("users").document(currentUserId)
                                .update(updates)
                                .addOnSuccessListener(aVoid -> {
                                    // Remove current user from partner's partners list
                                    db.collection("users").document(mainPartnerId)
                                            .update("partners", FieldValue.arrayRemove(currentUserId))
                                            .addOnCompleteListener(task -> {
                                                showProgress(false);
                                                Toast.makeText(this, "Partner removed successfully",
                                                             Toast.LENGTH_SHORT).show();
                                                loadCurrentPartner();
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    showProgress(false);
                                    Toast.makeText(this, "Failed to remove partner: " + e.getMessage(),
                                                 Toast.LENGTH_LONG).show();
                                });
                    } else {
                        showProgress(false);
                        Toast.makeText(this, "No partner to remove", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to get current partner: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        addPartnerButton.setEnabled(!show);
        removePartnerButton.setEnabled(!show);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
