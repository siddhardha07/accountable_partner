package com.example.accountable.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.example.accountable.fragments.AppsFragment;
import com.example.accountable.fragments.PartnersFragment;
import com.example.accountable.fragments.AccountabilityFragment;
import com.example.accountable.fragments.NotificationsFragment;
import com.example.accountable.fragments.ProfileFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new AppsFragment();
            case 1:
                return new PartnersFragment();
            case 2:
                return new AccountabilityFragment();
            case 3:
                return new NotificationsFragment();
            case 4:
                return new ProfileFragment();
            default:
                return new AppsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 5; // 5 tabs
    }
}