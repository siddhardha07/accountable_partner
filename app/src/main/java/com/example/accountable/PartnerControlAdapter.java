package com.example.accountable;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PartnerControlAdapter extends RecyclerView.Adapter<PartnerControlAdapter.ViewHolder> {

    private List<PeopleIHelpActivity.PartnerInfo> partners;
    private OnPartnerClickListener clickListener;

    public interface OnPartnerClickListener {
        void onPartnerClick(PeopleIHelpActivity.PartnerInfo partner);
    }

    public PartnerControlAdapter(List<PeopleIHelpActivity.PartnerInfo> partners, OnPartnerClickListener clickListener) {
        this.partners = partners;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_partner_control, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PeopleIHelpActivity.PartnerInfo partner = partners.get(position);

        holder.nameText.setText(partner.displayName);
        holder.emailText.setText(partner.email);

        holder.manageButton.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPartnerClick(partner);
            }
        });
    }

    @Override
    public int getItemCount() {
        return partners.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView emailText;
        Button manageButton;

        ViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.partnerName);
            emailText = itemView.findViewById(R.id.partnerEmail);
            manageButton = itemView.findViewById(R.id.manageButton);
        }
    }
}
