package com.sjoneon.cap.adapters;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.sjoneon.cap.R;
import com.sjoneon.cap.models.api.SessionInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 세션 목록을 표시하기 위한 RecyclerView 어댑터
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    private List<SessionInfo> sessionList;
    private Integer currentSessionId;
    private OnSessionClickListener listener;

    /**
     * 세션 클릭 리스너 인터페이스
     */
    public interface OnSessionClickListener {
        void onSessionClick(SessionInfo session);
        void onEditTitle(SessionInfo session);
        void onDeleteSession(SessionInfo session);
    }

    public SessionAdapter(List<SessionInfo> sessionList, Integer currentSessionId, OnSessionClickListener listener) {
        this.sessionList = sessionList;
        this.currentSessionId = currentSessionId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        SessionInfo session = sessionList.get(position);

        holder.textSessionTitle.setText(session.getTitle());
        holder.textSessionTime.setText(formatRelativeTime(session.getUpdatedAt()));

        // 현재 보고 있는 세션 강조 표시
        if (currentSessionId != null && session.getId() == currentSessionId) {
            holder.cardSession.setCardBackgroundColor(
                    holder.itemView.getContext().getColor(R.color.background_card_transparent)
            );
        } else {
            holder.cardSession.setCardBackgroundColor(
                    holder.itemView.getContext().getColor(R.color.background_card)
            );
        }

        // 세션 클릭 리스너
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(session);
            }
        });

        // 메뉴 버튼 클릭 리스너
        holder.buttonMenu.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(v.getContext(), v);
            popupMenu.inflate(R.menu.session_item_menu);

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit_title) {
                    if (listener != null) {
                        listener.onEditTitle(session);
                    }
                    return true;
                } else if (itemId == R.id.action_delete) {
                    if (listener != null) {
                        listener.onDeleteSession(session);
                    }
                    return true;
                }
                return false;
            });

            popupMenu.show();
        });
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    /**
     * 상대 시간 포맷팅
     */
    private String formatRelativeTime(String isoTimestamp) {
        try {
            // ISO 8601 형식의 UTC 시간을 파싱
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(isoTimestamp);
            if (date == null) return "알 수 없음";

            // 현재 시간과의 차이 계산
            long diff = System.currentTimeMillis() - date.getTime();
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (seconds < 60) {
                return "방금 전";
            } else if (minutes < 60) {
                return minutes + "분 전";
            } else if (hours < 24) {
                return hours + "시간 전";
            } else if (days < 7) {
                return days + "일 전";
            } else {
                SimpleDateFormat dateFormat = new SimpleDateFormat("MM월 dd일", Locale.KOREAN);
                return dateFormat.format(date);
            }
        } catch (Exception e) {
            Log.e("SessionAdapter", "시간 파싱 오류: " + e.getMessage());
            return "알 수 없음";
        }
    }

    /**
     * ViewHolder 클래스
     */
    static class SessionViewHolder extends RecyclerView.ViewHolder {
        CardView cardSession;
        TextView textSessionTitle;
        TextView textSessionTime;
        ImageButton buttonMenu;

        SessionViewHolder(View itemView) {
            super(itemView);
            cardSession = itemView.findViewById(R.id.cardSession);
            textSessionTitle = itemView.findViewById(R.id.textSessionTitle);
            textSessionTime = itemView.findViewById(R.id.textSessionTime);
            buttonMenu = itemView.findViewById(R.id.buttonMenu);
        }
    }
}