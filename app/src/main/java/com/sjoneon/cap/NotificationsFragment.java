package com.sjoneon.cap;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * 알림 목록을 표시하는 프래그먼트
 */
public class NotificationsFragment extends Fragment {

    private TextView textNoNotifications;
    private RecyclerView recyclerViewNotifications;
    private Button buttonMarkAllRead;
    private NotificationAdapter notificationAdapter;
    private NotificationRepository notificationRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        // 뷰 초기화
        textNoNotifications = view.findViewById(R.id.textNoNotifications);
        recyclerViewNotifications = view.findViewById(R.id.recyclerViewNotifications);
        buttonMarkAllRead = view.findViewById(R.id.buttonMarkAllRead);

        // 알림 저장소 초기화
        notificationRepository = NotificationRepository.getInstance(requireContext());

        // 리사이클러뷰 설정
        recyclerViewNotifications.setLayoutManager(new LinearLayoutManager(getContext()));

        // 모두 읽음 버튼 클릭 리스너
        buttonMarkAllRead.setOnClickListener(v -> {
            notificationRepository.markAllAsRead();
            loadNotifications();
            Toast.makeText(getContext(), R.string.all_notifications_read, Toast.LENGTH_SHORT).show();
        });

        // 알림 목록 로드
        loadNotifications();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 화면이 다시 표시될 때 알림 목록 갱신
        loadNotifications();
    }

    /**
     * 알림 목록 로드
     */
    private void loadNotifications() {
        List<NotificationItem> notifications = notificationRepository.getAllNotifications();

        if (notifications.isEmpty()) {
            textNoNotifications.setVisibility(View.VISIBLE);
            recyclerViewNotifications.setVisibility(View.GONE);
        } else {
            textNoNotifications.setVisibility(View.GONE);
            recyclerViewNotifications.setVisibility(View.VISIBLE);

            // 어댑터 설정
            notificationAdapter = new NotificationAdapter(notifications);
            recyclerViewNotifications.setAdapter(notificationAdapter);
        }
    }

    /**
     * 알림 어댑터
     */
    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

        private List<NotificationItem> notifications;

        public NotificationAdapter(List<NotificationItem> notifications) {
            this.notifications = notifications;
        }

        @NonNull
        @Override
        public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
            NotificationItem notification = notifications.get(position);

            // 알림 내용 설정
            holder.textTitle.setText(notification.getTitle());
            holder.textContent.setText(notification.getContent());

            // 시간 표시 (상대적 시간, 예: "3분 전")
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    notification.getTimestamp(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            holder.textTime.setText(timeAgo);

            // 읽음 상태에 따라 표시기 설정
            holder.viewUnread.setVisibility(notification.isRead() ? View.INVISIBLE : View.VISIBLE);

            // 전체 아이템 클릭 리스너 (알림 읽음 처리)
            holder.itemView.setOnClickListener(v -> {
                notificationRepository.markAsRead(notification.getId());
                holder.viewUnread.setVisibility(View.INVISIBLE);
                Toast.makeText(getContext(), R.string.notification_read, Toast.LENGTH_SHORT).show();
            });

            // 삭제 버튼 클릭 리스너
            holder.buttonDelete.setOnClickListener(v -> {
                notificationRepository.deleteNotification(notification.getId());
                notifications.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, notifications.size());

                // 알림이 모두 삭제됐는지 확인
                if (notifications.isEmpty()) {
                    textNoNotifications.setVisibility(View.VISIBLE);
                    recyclerViewNotifications.setVisibility(View.GONE);
                }

                Toast.makeText(getContext(), R.string.notification_deleted, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public int getItemCount() {
            return notifications.size();
        }

        class NotificationViewHolder extends RecyclerView.ViewHolder {
            TextView textTitle;
            TextView textContent;
            TextView textTime;
            View viewUnread;
            ImageButton buttonDelete;

            NotificationViewHolder(View itemView) {
                super(itemView);
                textTitle = itemView.findViewById(R.id.textNotificationTitle);
                textContent = itemView.findViewById(R.id.textNotificationContent);
                textTime = itemView.findViewById(R.id.textNotificationTime);
                viewUnread = itemView.findViewById(R.id.viewUnread);
                buttonDelete = itemView.findViewById(R.id.buttonDeleteNotification);
            }
        }
    }
}