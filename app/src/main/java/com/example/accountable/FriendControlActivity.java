package com.example.accountable;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Random;

public class FriendControlActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "AccountablePrefs";
    private static final String KEY_FRIEND_NAME = "friendName";
    private static final String KEY_FRIEND_EMAIL = "friendEmail";
    private static final String KEY_ACCESS_CODE = "accessCode";

    private TextInputEditText friendNameInput;
    private TextInputEditText friendEmailInput;
    private TextView accessCodeText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_control);

        // Set up toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        // Initialize views
        friendNameInput = findViewById(R.id.friendNameInput);
        friendEmailInput = findViewById(R.id.friendEmailInput);
        accessCodeText = findViewById(R.id.accessCodeText);
        Button generateCodeButton = findViewById(R.id.generateCodeButton);
        Button saveFriendButton = findViewById(R.id.saveFriendButton);

        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load saved friend data
        loadFriendData();

        // Set up button click listeners
        generateCodeButton.setOnClickListener(v -> generateAccessCode());
        saveFriendButton.setOnClickListener(v -> saveFriendData());
    }

    private void generateAccessCode() {
        Random random = new Random();
        String code = String.format("%06d", random.nextInt(1000000));
        accessCodeText.setText(code);
    }

    private void loadFriendData() {
        friendNameInput.setText(prefs.getString(KEY_FRIEND_NAME, ""));
        friendEmailInput.setText(prefs.getString(KEY_FRIEND_EMAIL, ""));
        accessCodeText.setText(prefs.getString(KEY_ACCESS_CODE, ""));
    }

    private void saveFriendData() {
        String name = friendNameInput.getText().toString().trim();
        String email = friendEmailInput.getText().toString().trim();
        String code = accessCodeText.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Please fill all fields and generate an access code",
                         Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
            .putString(KEY_FRIEND_NAME, name)
            .putString(KEY_FRIEND_EMAIL, email)
            .putString(KEY_ACCESS_CODE, code)
            .apply();

        Toast.makeText(this, "Friend details saved successfully!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
