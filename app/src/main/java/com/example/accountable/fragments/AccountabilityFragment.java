package com.example.accountable.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.accountable.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AccountabilityFragment extends Fragment {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentUserId;

    private TextView currentPartnerText;
    private EditText partnerEmailInput;
    private Button addPartnerButton, removePartnerButton;
    private ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accountability, container, false);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return view;
        }
        currentUserId = currentUser.getUid();

        initViews(view);
        setupClickListeners();

        // First, ensure current user document exists
        ensureCurrentUserExists(currentUser);
        loadCurrentPartner();

        return view;
    }
    
    private void initViews(View view) {
        currentPartnerText = view.findViewById(R.id.currentPartnerText);
        partnerEmailInput = view.findViewById(R.id.partnerEmailInput);
        addPartnerButton = view.findViewById(R.id.addPartnerButton);
        removePartnerButton = view.findViewById(R.id.removePartnerButton);
        progressBar = view.findViewById(R.id.progressBar);
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
                    Toast.makeText(getContext(), "Failed to load partner info: " + e.getMessage(),
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
            Toast.makeText(getContext(), "Please enter partner's email", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);

        // Find user by email
        db.collection("users")
                .whereEqualTo("email", partnerEmail)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String partnerId = queryDocumentSnapshots.getDocuments().get(0).getId();

                        if (partnerId.equals(currentUserId)) {
                            showProgress(false);
                            Toast.makeText(getContext(), "You cannot be your own accountability partner!",
                                         Toast.LENGTH_SHORT).show();
                            return;
                        }

                        setMainPartner(partnerId, partnerEmail);
                    } else {
                        showProgress(false);
                        Toast.makeText(getContext(), "User with email '" + partnerEmail + "' not found. Please ask them to create an account first.",
                                     Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Failed to search for user: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void setMainPartner(String partnerId, String partnerEmail) {
        // Update current user's mainPartnerId
        Map<String, Object> updates = new HashMap<>();
        updates.put("mainPartnerId", partnerId);

        db.collection("users").document(currentUserId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    // Add current user to partner's partners list
                    db.collection("users").document(partnerId)
                            .update("partners", FieldValue.arrayUnion(currentUserId))
                            .addOnSuccessListener(aVoid2 -> {
                                showProgress(false);
                                Toast.makeText(getContext(), "✅ Partnership established with " + partnerEmail + "!",
                                             Toast.LENGTH_LONG).show();
                                partnerEmailInput.setText("");
                                loadCurrentPartner();
                            })
                            .addOnFailureListener(e -> {
                                showProgress(false);
                                Toast.makeText(getContext(), "Failed to complete partnership setup: " + e.getMessage(),
                                             Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Failed to set accountability partner: " + e.getMessage(),
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
                                                Toast.makeText(getContext(), "Partner removed successfully",
                                                             Toast.LENGTH_SHORT).show();
                                                loadCurrentPartner();
                                            });
                                })
                                .addOnFailureListener(e -> {
                                    showProgress(false);
                                    Toast.makeText(getContext(), "Failed to remove partner: " + e.getMessage(),
                                                 Toast.LENGTH_LONG).show();
                                });
                    } else {
                        showProgress(false);
                        Toast.makeText(getContext(), "No partner to remove", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(getContext(), "Failed to get current partner: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void ensureCurrentUserExists(FirebaseUser currentUser) {
        // Check if current user document exists, create it if not
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        createUserDocument(currentUser);
                    }
                })
                .addOnFailureListener(e -> createUserDocument(currentUser));
    }

    private void createUserDocument(FirebaseUser user) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", user.getEmail());
        userProfile.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0]);
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("mainPartnerId", null);
        userProfile.put("partners", new java.util.ArrayList<String>());

        db.collection("users").document(user.getUid())
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    // User created successfully
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to create user profile: " + e.getMessage(),
                                 Toast.LENGTH_LONG).show();
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        addPartnerButton.setEnabled(!show);
        removePartnerButton.setEnabled(!show);
    }
}