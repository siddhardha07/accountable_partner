package com.example.accountable.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.accountable.AuthActivity;
import com.example.accountable.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private TextView userNameTextView, userEmailTextView;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        mAuth = FirebaseAuth.getInstance();
        
        // Initialize views
        userNameTextView = view.findViewById(R.id.userNameTextView);
        userEmailTextView = view.findViewById(R.id.userEmailTextView);
        
        // Load user info
        loadUserInfo();
        
        // Set up click listeners
        view.findViewById(R.id.cardChangePassword).setOnClickListener(v -> {
            // TODO: Implement password change functionality
            Toast.makeText(getContext(), "Password change coming soon", Toast.LENGTH_SHORT).show();
        });
        
        view.findViewById(R.id.cardSignOut).setOnClickListener(v -> {
            signOut();
        });
        
        return view;
    }
    
    private void loadUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            String email = currentUser.getEmail();
            
            // Set user name (use email prefix if display name is not available)
            if (displayName != null && !displayName.isEmpty()) {
                userNameTextView.setText(displayName);
            } else if (email != null) {
                userNameTextView.setText(email.split("@")[0]);
            } else {
                userNameTextView.setText("User");
            }
            
            // Set email
            if (email != null) {
                userEmailTextView.setText(email);
            } else {
                userEmailTextView.setText("No email");
            }
        }
    }
    
    private void signOut() {
        mAuth.signOut();
        Intent intent = new Intent(getActivity(), AuthActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}