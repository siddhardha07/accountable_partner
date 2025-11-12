package com.example.accountable;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PeopleIHelpActivity extends AppCompatActivity {
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;
    
    private RecyclerView partnersRecyclerView;
    private TextView emptyStateText;
    private PartnerControlAdapter adapter;
    private List<PartnerInfo> partnersList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_people_i_help);

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
        loadPeopleIHelp();
    }
    
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("People I Help");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }
    
    private void initViews() {
        partnersRecyclerView = findViewById(R.id.partnersRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);
        
        adapter = new PartnerControlAdapter(partnersList, this::openPartnerControl);
        partnersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        partnersRecyclerView.setAdapter(adapter);
    }
    
    private void loadPeopleIHelp() {
        partnersList.clear();
        
        // Use the same query as the working debug button
        db.collection("users")
                .whereEqualTo("mainPartnerId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        PartnerInfo partner = new PartnerInfo();
                        partner.userId = doc.getId();
                        partner.email = doc.getString("email");
                        partner.displayName = doc.getString("displayName");
                        
                        // Use the old fallback logic that was working  
                        if (partner.displayName == null || partner.displayName.trim().isEmpty()) {
                            if (partner.email != null && !partner.email.trim().isEmpty()) {
                                partner.displayName = partner.email.split("@")[0];
                            } else {
                                partner.displayName = "User-" + partner.userId.substring(0, 6);
                            }
                        }
                        if (partner.email == null) {
                            partner.email = "No email in database";
                        }
                        
                        partnersList.add(partner);
                    }
                    
                    updateUI();
                })
                .addOnFailureListener(e -> updateUI());
    }

    
    private void updateUI() {
        if (partnersList.isEmpty()) {
            // Show empty state, hide RecyclerView
            if (partnersRecyclerView != null) {
                partnersRecyclerView.setVisibility(android.view.View.GONE);
            }
            if (emptyStateText != null) {
                emptyStateText.setVisibility(android.view.View.VISIBLE);
                emptyStateText.setText("No partnerships found");
            }
        } else {
            // Show RecyclerView, hide empty state  
            if (partnersRecyclerView != null) {
                partnersRecyclerView.setVisibility(android.view.View.VISIBLE);
            }
            if (emptyStateText != null) {
                emptyStateText.setVisibility(android.view.View.GONE);
            }
            
            // Update adapter
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }
    
    private void openPartnerControl(PartnerInfo partner) {
        Intent intent = new Intent(this, PartnerControlActivity.class);
        intent.putExtra("partnerId", partner.userId);
        intent.putExtra("partnerEmail", partner.email);
        intent.putExtra("partnerName", partner.displayName);
        startActivity(intent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
    
    // Data class for partner information
    public static class PartnerInfo {
        public String userId;
        public String email;
        public String displayName;
    }
}