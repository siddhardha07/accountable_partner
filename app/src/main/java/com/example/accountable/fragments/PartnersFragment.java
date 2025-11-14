package com.example.accountable.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.accountable.PartnerControlActivity;
import com.example.accountable.PartnerControlAdapter;
import com.example.accountable.PeopleIHelpActivity;
import com.example.accountable.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

public class PartnersFragment extends Fragment {

    private RecyclerView partnersRecyclerView;
    private SearchView searchView;
    private FloatingActionButton addPartnerFab;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String currentUserId;
    
    private PartnerControlAdapter adapter;
    private List<PeopleIHelpActivity.PartnerInfo> partnersList = new ArrayList<>();
    private List<PeopleIHelpActivity.PartnerInfo> originalPartnersList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_partners, container, false);
        
        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }
        
        // Initialize views
        partnersRecyclerView = view.findViewById(R.id.partnersRecyclerView);
        searchView = view.findViewById(R.id.searchView);
        addPartnerFab = view.findViewById(R.id.addPartnerFab);
        
        // Setup RecyclerView
        partnersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new PartnerControlAdapter(partnersList, this::openPartnerControl);
        partnersRecyclerView.setAdapter(adapter);
        
        // Setup search functionality
        setupSearch();
        
        // Setup FAB
        addPartnerFab.setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), PeopleIHelpActivity.class));
        });
        
        // Load real partner data
        loadRealPartners();
        
        return view;
    }

    private void setupSearch() {
        if (searchView == null) return;
        
        searchView.setQueryHint("Search partners...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterPartners(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterPartners(newText);
                return false;
            }
        });
    }

    private void filterPartners(String query) {
        List<PeopleIHelpActivity.PartnerInfo> filteredList = new ArrayList<>();

        if (TextUtils.isEmpty(query)) {
            filteredList.addAll(originalPartnersList);
        } else {
            for (PeopleIHelpActivity.PartnerInfo partner : originalPartnersList) {
                if (partner.displayName != null && partner.displayName.toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(partner);
                }
            }
        }
        
        partnersList.clear();
        partnersList.addAll(filteredList);
        
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void loadRealPartners() {
        if (currentUserId == null) return;
        
        partnersList.clear();
        originalPartnersList.clear();

        // Query Firebase for real partners - people who have this user as their main partner
        db.collection("users")
                .whereEqualTo("mainPartnerId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        PeopleIHelpActivity.PartnerInfo partner = new PeopleIHelpActivity.PartnerInfo();
                        partner.userId = doc.getId();
                        partner.email = doc.getString("email");
                        partner.displayName = doc.getString("displayName");

                        // Use fallback logic for display name
                        if (partner.displayName == null || partner.displayName.trim().isEmpty()) {
                            if (partner.email != null && !partner.email.trim().isEmpty()) {
                                partner.displayName = partner.email.split("@")[0];
                            } else {
                                partner.displayName = "User-" + partner.userId.substring(0, 6);
                            }
                        }
                        if (partner.email == null) {
                            partner.email = "No email";
                        }

                        partnersList.add(partner);
                        originalPartnersList.add(partner);
                    }

                    updateUI();
                })
                .addOnFailureListener(e -> updateUI());
    }

    private void updateUI() {
        // Show RecyclerView and update adapter
        partnersRecyclerView.setVisibility(View.VISIBLE);
        
        // Update adapter
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void openPartnerControl(PeopleIHelpActivity.PartnerInfo partner) {
        Intent intent = new Intent(getActivity(), PartnerControlActivity.class);
        intent.putExtra("partnerId", partner.userId);
        intent.putExtra("partnerEmail", partner.email);
        intent.putExtra("partnerName", partner.displayName);
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload partners when returning to fragment
        loadRealPartners();
    }
}