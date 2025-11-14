package com.example.accountable;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.example.accountable.adapters.MainPagerAdapter;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAuth = FirebaseAuth.getInstance();
        
        // Check if user is signed in
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User not signed in, go to auth activity
            Intent intent = new Intent(MainActivity.this, AuthActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Initialize ViewPager2 and BottomNavigationView
        viewPager = findViewById(R.id.viewPager);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Setup ViewPager with fragments
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Handle bottom navigation clicks
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_apps) {
                viewPager.setCurrentItem(0, true);
                return true;
            } else if (itemId == R.id.nav_partners) {
                viewPager.setCurrentItem(1, true);
                return true;
            } else if (itemId == R.id.nav_accountability) {
                viewPager.setCurrentItem(2, true);
                return true;
            } else if (itemId == R.id.nav_notifications) {
                viewPager.setCurrentItem(3, true);
                return true;
            } else if (itemId == R.id.nav_profile) {
                viewPager.setCurrentItem(4, true);
                return true;
            }
            return false;
        });

        // Handle ViewPager page changes to update bottom navigation
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                switch (position) {
                    case 0:
                        bottomNavigation.setSelectedItemId(R.id.nav_apps);
                        break;
                    case 1:
                        bottomNavigation.setSelectedItemId(R.id.nav_partners);
                        break;
                    case 2:
                        bottomNavigation.setSelectedItemId(R.id.nav_accountability);
                        break;
                    case 3:
                        bottomNavigation.setSelectedItemId(R.id.nav_notifications);
                        break;
                    case 4:
                        bottomNavigation.setSelectedItemId(R.id.nav_profile);
                        break;
                }
            }
        });

        // Set default selection
        bottomNavigation.setSelectedItemId(R.id.nav_apps);
    }
}