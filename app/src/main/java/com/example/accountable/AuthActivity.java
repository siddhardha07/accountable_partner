package com.example.accountable;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AuthActivity extends AppCompatActivity {
    
    private static final int RC_SIGN_IN = 9001;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private GoogleSignInClient mGoogleSignInClient;
    
    private EditText nameEditText, emailEditText, passwordEditText;
    private Button signInButton, signUpButton, googleSignInButton;
    private TextView toggleModeText, loadingText;
    private ProgressBar progressBar;
    
    private boolean isSignUpMode = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        
        try {
            mAuth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            Toast.makeText(this, "Firebase initialized successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Firebase init failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        
        // Configure Google Sign In - temporarily disabled until web client ID is configured
        // GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        //         .requestIdToken(getString(R.string.default_web_client_id))
        //         .requestEmail()
        //         .build();
        // mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        
        initViews();
        setupClickListeners();
        
        // Check if user is already signed in
        checkCurrentUser();
    }
    
    private void initViews() {
        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        signInButton = findViewById(R.id.signInButton);
        signUpButton = findViewById(R.id.signUpButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        toggleModeText = findViewById(R.id.toggleModeText);
        progressBar = findViewById(R.id.progressBar);
        loadingText = findViewById(R.id.loadingText);
    }
    
    private void setupClickListeners() {
        signInButton.setOnClickListener(v -> {
            if (isSignUpMode) {
                signUpUser();
            } else {
                signInUser();
            }
        });
        
        signUpButton.setOnClickListener(v -> {
            toggleMode();
        });
        
        googleSignInButton.setOnClickListener(v -> {
            Toast.makeText(this, "Google Sign-In setup needed. Use email/password for now.", Toast.LENGTH_LONG).show();
            // signInWithGoogle();
        });
        
        toggleModeText.setOnClickListener(v -> {
            toggleMode();
        });
    }
    
    private void toggleMode() {
        isSignUpMode = !isSignUpMode;
        if (isSignUpMode) {
            // Sign Up mode - show name field
            findViewById(R.id.nameInputLayout).setVisibility(View.VISIBLE);
            signInButton.setText("Sign Up");
            signUpButton.setText("Sign In Instead");
            toggleModeText.setText("Already have an account? Sign In");
        } else {
            // Sign In mode - hide name field
            findViewById(R.id.nameInputLayout).setVisibility(View.GONE);
            signInButton.setText("Sign In");
            signUpButton.setText("Create Account");
            toggleModeText.setText("Don't have an account? Sign Up");
        }
    }
    
    private void signInUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showProgress(true);
        Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show();
        
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    showProgress(false);
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Sign in failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private void signUpUser() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter name, email and password", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showProgress(true);
        Toast.makeText(this, "Creating account...", Toast.LENGTH_SHORT).show();
        
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        createUserProfile(user, name);
                    } else {
                        showProgress(false);
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Sign up failed: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }
    
    private void createUserProfile(FirebaseUser user) {
        createUserProfile(user, null);
    }
    
    private void createUserProfile(FirebaseUser user, String displayName) {
        if (user == null) return;
        
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("email", user.getEmail());
        userProfile.put("displayName", displayName != null ? displayName : 
                       (user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0]));
        userProfile.put("createdAt", System.currentTimeMillis());
        userProfile.put("mainPartnerId", null);
        userProfile.put("partners", new ArrayList<String>());
        
        db.collection("users").document(user.getUid())
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    showProgress(false);
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                    goToMainActivity();
                })
                .addOnFailureListener(e -> {
                    showProgress(false);
                    Toast.makeText(this, "Failed to create profile: " + e.getMessage(), 
                                 Toast.LENGTH_LONG).show();
                });
    }
    
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Toast.makeText(this, "Google sign in failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void firebaseAuthWithGoogle(String idToken) {
        showProgress(true);
        
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        // Check if this is a new user
                        if (task.getResult().getAdditionalUserInfo().isNewUser()) {
                            createUserProfile(user);
                        } else {
                            showProgress(false);
                            Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                            goToMainActivity();
                        }
                    } else {
                        showProgress(false);
                        Toast.makeText(this, "Authentication failed: " + task.getException().getMessage(), 
                                     Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        signInButton.setEnabled(!show);
        signUpButton.setEnabled(!show);
        googleSignInButton.setEnabled(!show);
    }
    
    private void checkCurrentUser() {
        loadingText.setVisibility(View.VISIBLE);
        
        // Use a slight delay to let Firebase Auth initialize properly
        new android.os.Handler().postDelayed(() -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            loadingText.setVisibility(View.GONE);
            
            if (currentUser != null) {
                Toast.makeText(this, "Welcome back, " + 
                    (currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getEmail()), 
                    Toast.LENGTH_SHORT).show();
                goToMainActivity();
            } else {
                // Show the sign in form
                showSignInForm();
            }
        }, 1500); // 1.5 second delay to allow Firebase to initialize
    }
    
    private void showSignInForm() {
        // Show all form elements
        findViewById(R.id.emailInputLayout).setVisibility(View.VISIBLE);
        findViewById(R.id.passwordInputLayout).setVisibility(View.VISIBLE);
        signInButton.setVisibility(View.VISIBLE);
        signUpButton.setVisibility(View.VISIBLE);
        findViewById(R.id.divider).setVisibility(View.VISIBLE);
        findViewById(R.id.orText).setVisibility(View.VISIBLE);
        googleSignInButton.setVisibility(View.VISIBLE);
        toggleModeText.setVisibility(View.VISIBLE);
        
        Toast.makeText(this, "Please sign in to continue", Toast.LENGTH_SHORT).show();
    }
    
    private void goToMainActivity() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}