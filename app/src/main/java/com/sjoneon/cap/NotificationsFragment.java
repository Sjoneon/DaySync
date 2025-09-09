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

import java.util.ArrayList;
import java.util.List;

/**
 * 알림 목록을 표시하는 프래그먼트 (완전히 개선된 버전)
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
        initializeViews(view);

        // 알림 저장소 초기화
        notificationRepository = NotificationRepository.getInstance(requireContext());

        // 리사이클러뷰 설정
        setupRecyclerView();

        // 버튼 리스너 설정
        setupButtonListeners();

        // 알림 목록 로드
        loadNotifications();

        return view;
    }

    /**
     * 뷰 초기화
     */
    private void initializeViews(View view) {
        textNoNotifications = view.findViewById(R.id.textNoNotifications);
        recyclerViewNotifications = view.findViewById(R.id.recyclerViewNotifications);
        buttonMarkAllRead = view.findViewById(R.id.buttonMarkAllRead);
    }

    /**
     * 리사이클러뷰 설정
     */
    private void setupRecyclerView() {
        recyclerViewNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerViewNotifications.setHasFixedSize(true); // 성능 최적화
    }

    /**
     * 버튼 리스너 설정
     */
    private void setupButtonListeners() {
        // 모두 읽음 버튼 클릭 리스너
        buttonMarkAllRead.setOnClickListener(v -> {
            boolean success = notificationRepository.markAllAsRead();
            if (success) {
                loadNotifications();
                Toast.makeText(getContext(), R.string.all_notifications_read, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "읽지 않은 알림이 없습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // 화면이 다시 표시될 때 알림 목록 갱신
        loadNotifications();
    }

    /**
     * 알림 목록 로드 (개선된 버전)
     */
    private void loadNotifications() {
        try {
            List<NotificationItem> notifications = notificationRepository.getAllNotifications();

            if (notifications == null || notifications.isEmpty()) {
                // UI 스레드에서 실행
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        textNoNotifications.setVisibility(View.VISIBLE);
                        recyclerViewNotifications.setVisibility(View.GONE);

                        // 모두 읽음 버튼 숨기기
                        buttonMarkAllRead.setVisibility(View.GONE);
                    });
                }
            } else {
                // UI 스레드에서 실행
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        textNoNotifications.setVisibility(View.GONE);
                        recyclerViewNotifications.setVisibility(View.VISIBLE);

                        // 읽지 않은 알림이 있는 경우에만 모두 읽음 버튼 표시
                        int unreadCount = notificationRepository.getUnreadCount();
                        buttonMarkAllRead.setVisibility(unreadCount > 0 ? View.VISIBLE : View.GONE);

                        // 어댑터 설정 또는 업데이트
                        if (notificationAdapter == null) {
                            notificationAdapter = new NotificationAdapter(new ArrayList<>(notifications));
                            recyclerViewNotifications.setAdapter(notificationAdapter);
                        } else {
                            // 기존 어댑터의 데이터 업데이트
                            notificationAdapter.updateNotifications(notifications);
                        }
                    });
                }
            }
        } catch (Exception e) {
            // 예외 발생 시 기본 UI 상태로 설정
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    textNoNotifications.setVisibility(View.VISIBLE);
                    recyclerViewNotifications.setVisibility(View.GONE);
                    buttonMarkAllRead.setVisibility(View.GONE);

                    // 오류 메시지 표시
                    Toast.makeText(getContext(), "알림 목록을 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    /**
     * 알림 어댑터 (완전히 개선된 버전)
     */
    private class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

        private List<NotificationItem> notifications;

        public NotificationAdapter(List<NotificationItem> notifications) {
            this.notifications = notifications != null ? notifications : new ArrayList<>();
        }

        /**
         * 알림 목록 업데이트
         * @param newNotifications 새로운 알림 목록
         */
        public void updateNotifications(List<NotificationItem> newNotifications) {
            if (newNotifications != null) {
                this.notifications.clear();
                this.notifications.addAll(newNotifications);
                notifyDataSetChanged();
            }
        }

        /**
         * 특정 위치의 알림 안전하게 제거
         * @param position 제거할 위치
         * @return 제거 성공 여부
         */
        public boolean removeNotificationAt(int position) {
            if (position >= 0 && position < notifications.size()) {
                notifications.remove(position);
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, notifications.size());
                return true;
            }
            return false;
        }

        /**
         * ID로 알림 찾기
         * @param id 알림 ID
         * @return 해당 알림의 위치 (없으면 -1)
         */
        public int findNotificationPosition(int id) {
            for (int i = 0; i < notifications.size(); i++) {
                if (notifications.get(i).getId() == id) {
                    return i;
                }
            }
            return -1;
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
            if (position >= notifications.size()) {
                return; // 안전성 체크
            }

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
                // 현재 position을 다시 확인
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION ||
                        currentPosition >= notifications.size() ||
                        currentPosition < 0) {
                    return; // 유효하지 않은 position이면 처리하지 않음
                }

                try {
                    NotificationItem currentNotification = notifications.get(currentPosition);
                    if (!currentNotification.isRead()) {
                        boolean success = notificationRepository.markAsRead(currentNotification.getId());
                        if (success) {
                            holder.viewUnread.setVisibility(View.INVISIBLE);

                            // 모두 읽음 버튼 상태 업데이트
                            int unreadCount = notificationRepository.getUnreadCount();
                            buttonMarkAllRead.setVisibility(unreadCount > 0 ? View.VISIBLE : View.GONE);

                            Toast.makeText(getContext(), R.string.notification_read, Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(getContext(), "알림 읽음 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                }
            });

            // 삭제 버튼 클릭 리스너 (최종 개선된 버전)
            holder.buttonDelete.setOnClickListener(v -> {
                // 중복 클릭 방지
                if (!v.isClickable()) {
                    return;
                }
                v.setClickable(false);

                // 현재 position을 다시 확인
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition == RecyclerView.NO_POSITION) {
                    v.setClickable(true);
                    return;
                }

                // 유효성 검사
                if (currentPosition < 0 || currentPosition >= notifications.size()) {
                    v.setClickable(true);
                    Toast.makeText(getContext(), "알림 삭제 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    NotificationItem currentNotification = notifications.get(currentPosition);

                    // Repository에서 삭제 (성공/실패 확인)
                    boolean deleteSuccess = notificationRepository.deleteNotification(currentNotification.getId());

                    if (deleteSuccess) {
                        // 어댑터에서 안전하게 제거
                        boolean removeSuccess = removeNotificationAt(currentPosition);

                        if (removeSuccess) {
                            // 알림이 모두 삭제됐는지 확인
                            if (notifications.isEmpty()) {
                                textNoNotifications.setVisibility(View.VISIBLE);
                                recyclerViewNotifications.setVisibility(View.GONE);
                                buttonMarkAllRead.setVisibility(View.GONE);
                            } else {
                                // 읽지 않은 알림 개수에 따라 모두 읽음 버튼 상태 업데이트
                                int unreadCount = notificationRepository.getUnreadCount();
                                buttonMarkAllRead.setVisibility(unreadCount > 0 ? View.VISIBLE : View.GONE);
                            }

                            Toast.makeText(getContext(), R.string.notification_deleted, Toast.LENGTH_SHORT).show();
                        } else {
                            // 어댑터에서 제거 실패 시 전체 새로고침
                            loadNotifications();
                            Toast.makeText(getContext(), "알림이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getContext(), "알림 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    }

                } catch (Exception e) {
                    // 예외 발생 시 전체 목록 새로고침
                    loadNotifications();
                    Toast.makeText(getContext(), "알림 삭제 중 오류가 발생했습니다. 목록을 새로고침합니다.", Toast.LENGTH_SHORT).show();
                } finally {
                    // 클릭 가능 상태로 복원 (약간의 지연을 두어 중복 클릭 방지)
                    v.postDelayed(() -> {
                        if (v != null) {
                            v.setClickable(true);
                        }
                    }, 500);
                }
            });
        }

        @Override
        public int getItemCount() {
            return notifications != null ? notifications.size() : 0;
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

    /**
     * 프래그먼트가 소멸될 때 리소스 정리
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 메모리 누수 방지를 위한 정리
        if (notificationAdapter != null) {
            notificationAdapter = null;
        }

        if (recyclerViewNotifications != null) {
            recyclerViewNotifications.setAdapter(null);
        }
    }
}