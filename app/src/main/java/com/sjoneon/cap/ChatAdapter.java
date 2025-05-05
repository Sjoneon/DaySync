package com.sjoneon.cap;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 채팅 메시지를 표시하기 위한 RecyclerView 어댑터
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;

    private List<Message> messageList;

    /**
     * 생성자
     * @param messageList 채팅 메시지 목록
     */
    public ChatAdapter(List<Message> messageList) {
        this.messageList = messageList;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_USER) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_ai, parent, false);
            return new AiMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        if (holder instanceof UserMessageViewHolder) {
            UserMessageViewHolder userHolder = (UserMessageViewHolder) holder;
            userHolder.textViewMessage.setText(message.getContent());
            userHolder.textViewTime.setText(formatTime(message.getTimestamp()));
        } else if (holder instanceof AiMessageViewHolder) {
            AiMessageViewHolder aiHolder = (AiMessageViewHolder) holder;
            aiHolder.textViewMessage.setText(message.getContent());
            aiHolder.textViewTime.setText(formatTime(message.getTimestamp()));
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }

    /**
     * 새 메시지 추가 메서드
     * @param message 추가할 메시지
     */
    public void addMessage(Message message) {
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }

    /**
     * 시간 포맷팅 메서드
     * @param timestamp 타임스탬프
     * @return 포맷팅된 시간 문자열 (HH:mm)
     */
    private String formatTime(long timestamp) {
        Calendar calendar = Calendar.getInstance(Locale.getDefault());
        calendar.setTimeInMillis(timestamp);
        return DateFormat.format("HH:mm", calendar).toString();
    }

    /**
     * 사용자 메시지용 ViewHolder
     */
    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textViewMessage;
        TextView textViewTime;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            textViewMessage = itemView.findViewById(R.id.textViewMessage);
            textViewTime = itemView.findViewById(R.id.textViewTime);
        }
    }

    /**
     * AI 메시지용 ViewHolder
     */
    static class AiMessageViewHolder extends RecyclerView.ViewHolder {
        TextView textViewMessage;
        TextView textViewTime;

        AiMessageViewHolder(View itemView) {
            super(itemView);
            textViewMessage = itemView.findViewById(R.id.textViewMessage);
            textViewTime = itemView.findViewById(R.id.textViewTime);
        }
    }
}