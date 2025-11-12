package com.example.accountable;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AccessRequestActivity extends AppCompatActivity {
    private DatabaseReference dbRef;
    private String requestId;
    private AccessRequest request;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_request);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        requestId = getIntent().getStringExtra("requestId");
        dbRef = FirebaseDatabase.getInstance().getReference("requests").child(requestId);

        TextView requestDetails = findViewById(R.id.requestDetails);
        TextInputEditText timeInput = findViewById(R.id.timeInput);
        Button grantButton = findViewById(R.id.grantButton);
        Button denyButton = findViewById(R.id.denyButton);

        dbRef.get().addOnSuccessListener(snapshot -> {
            request = snapshot.getValue(AccessRequest.class);
            if (request != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                String time = sdf.format(new Date(request.getRequestTime()));

                String details = String.format("%s wants to use %s\nRequested at: %s",
                    request.getUserName(),
                    request.getAppName(),
                    time);
                requestDetails.setText(details);
            }
        });

        grantButton.setOnClickListener(v -> {
            String timeStr = timeInput.getText().toString();
            if (timeStr.isEmpty()) {
                Toast.makeText(this, "Please enter time duration", Toast.LENGTH_SHORT).show();
                return;
            }

            long duration = Long.parseLong(timeStr);
            request.setStatus("granted");
            request.setGrantedDuration(duration);
            dbRef.setValue(request).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Access granted for " + duration + " minutes",
                                 Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });

        denyButton.setOnClickListener(v -> {
            request.setStatus("denied");
            dbRef.setValue(request).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
