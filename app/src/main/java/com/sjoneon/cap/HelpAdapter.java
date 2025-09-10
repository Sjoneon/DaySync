package com.sjoneon.cap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 도움말 아이템들을 표시하기 위한 RecyclerView 어댑터
 */
public class HelpAdapter extends RecyclerView.Adapter<HelpAdapter.HelpViewHolder> {

    private List<HelpFragment.HelpItem> helpItems;

    public HelpAdapter(List<HelpFragment.HelpItem> helpItems) {
        this.helpItems = helpItems;
    }

    @NonNull
    @Override
    public HelpViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_help, parent, false);
        return new HelpViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HelpViewHolder holder, int position) {
        HelpFragment.HelpItem item = helpItems.get(position);

        holder.textTitle.setText(item.getTitle());
        holder.textSummary.setText(item.getSummary());
        holder.textDetails.setText(item.getDetails());

        // 확장/축소 상태에 따라 세부사항 표시/숨김
        if (item.isExpanded()) {
            holder.textDetails.setVisibility(View.VISIBLE);
            holder.imageExpand.setRotation(180); // 화살표를 위로 향하게
        } else {
            holder.textDetails.setVisibility(View.GONE);
            holder.imageExpand.setRotation(0); // 화살표를 아래로 향하게
        }

        // 클릭 리스너 설정
        holder.layoutHeader.setOnClickListener(v -> {
            item.setExpanded(!item.isExpanded());
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return helpItems.size();
    }

    /**
     * ViewHolder 클래스
     */
    public static class HelpViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutHeader;
        TextView textTitle;
        TextView textSummary;
        TextView textDetails;
        ImageView imageExpand;

        public HelpViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutHeader = itemView.findViewById(R.id.layoutHeader);
            textTitle = itemView.findViewById(R.id.textTitle);
            textSummary = itemView.findViewById(R.id.textSummary);
            textDetails = itemView.findViewById(R.id.textDetails);
            imageExpand = itemView.findViewById(R.id.imageExpand);
        }
    }
}