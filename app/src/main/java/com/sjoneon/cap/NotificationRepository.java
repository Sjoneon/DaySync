package com.sjoneon.cap;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 알림 데이터를 관리하는 저장소 클래스 (동시성 안전 버전)
 */
public class NotificationRepository {
    private static final String PREFS_NAME = "notification_prefs";
    private static final String KEY_NOTIFICATIONS = "notifications";

    private static NotificationRepository instance;
    private final Context context;
    private List<NotificationItem> notifications;
    private final Gson gson;

    /**
     * 생성자 (private - 싱글톤 패턴)
     * @param context 컨텍스트
     */
    private NotificationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        loadNotifications();
    }

    /**
     * 싱글톤 인스턴스 가져오기
     * @param context 컨텍스트
     * @return NotificationRepository 인스턴스
     */
    public static synchronized NotificationRepository getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationRepository(context);
        }
        return instance;
    }

    /**
     * 저장된 알림 로드
     */
    private synchronized void loadNotifications() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_NOTIFICATIONS, null);

            if (json != null) {
                Type type = new TypeToken<ArrayList<NotificationItem>>() {}.getType();
                notifications = gson.fromJson(json, type);

                // null 체크
                if (notifications == null) {
                    notifications = new ArrayList<>();
                }
            } else {
                notifications = new ArrayList<>();
            }
        } catch (Exception e) {
            // 예외 발생 시 빈 리스트로 초기화
            notifications = new ArrayList<>();
        }
    }

    /**
     * 알림 저장
     */
    private synchronized void saveNotifications() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            String json = gson.toJson(notifications);
            editor.putString(KEY_NOTIFICATIONS, json);
            editor.apply();
        } catch (Exception e) {
            // 저장 실패 시 로그만 출력 (실제 앱에서는 로깅 라이브러리 사용 권장)
            e.printStackTrace();
        }
    }

    /**
     * 모든 알림 가져오기 (동시성 안전 버전)
     * @return 알림 목록 복사본 (최신순)
     */
    public synchronized List<NotificationItem> getAllNotifications() {
        try {
            if (notifications == null) {
                notifications = new ArrayList<>();
            }

            // 원본 리스트의 복사본을 생성하여 반환
            List<NotificationItem> copyList = new ArrayList<>(notifications);

            // 시간 역순으로 정렬 (최신 알림이 먼저 표시되도록)
            Collections.sort(copyList, new Comparator<NotificationItem>() {
                @Override
                public int compare(NotificationItem o1, NotificationItem o2) {
                    return Long.compare(o2.getTimestamp(), o1.getTimestamp());
                }
            });

            return copyList;
        } catch (Exception e) {
            return new ArrayList<>(); // 예외 발생 시 빈 리스트 반환
        }
    }

    /**
     * 새 알림 추가
     * @param notification 추가할 알림
     * @return 추가 성공 여부
     */
    public synchronized boolean addNotification(NotificationItem notification) {
        try {
            if (notifications == null) {
                notifications = new ArrayList<>();
            }

            if (notification != null) {
                notifications.add(notification);
                saveNotifications();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 알림 삭제 (동시성 안전 버전)
     * @param id 삭제할 알림 ID
     * @return 삭제 성공 여부
     */
    public synchronized boolean deleteNotification(int id) {
        try {
            if (notifications == null) {
                return false;
            }

            for (int i = 0; i < notifications.size(); i++) {
                if (notifications.get(i).getId() == id) {
                    notifications.remove(i);
                    saveNotifications();
                    return true;
                }
            }
            return false; // 해당 ID의 알림을 찾지 못함
        } catch (Exception e) {
            // 예외 발생 시 로그 출력 후 false 반환
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 알림 읽음 처리 (동시성 안전 버전)
     * @param id 알림 ID
     * @return 처리 성공 여부
     */
    public synchronized boolean markAsRead(int id) {
        try {
            if (notifications == null) {
                return false;
            }

            for (NotificationItem notification : notifications) {
                if (notification.getId() == id) {
                    notification.setRead(true);
                    saveNotifications();
                    return true;
                }
            }
            return false; // 해당 ID의 알림을 찾지 못함
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 모든 알림 읽음 처리
     * @return 처리 성공 여부
     */
    public synchronized boolean markAllAsRead() {
        try {
            if (notifications == null || notifications.isEmpty()) {
                return false;
            }

            boolean hasUnread = false;
            for (NotificationItem notification : notifications) {
                if (!notification.isRead()) {
                    notification.setRead(true);
                    hasUnread = true;
                }
            }

            if (hasUnread) {
                saveNotifications();
                return true;
            }
            return false; // 읽지 않은 알림이 없었음
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 읽지 않은 알림 개수
     * @return 읽지 않은 알림 개수
     */
    public synchronized int getUnreadCount() {
        try {
            if (notifications == null) {
                return 0;
            }

            int count = 0;
            for (NotificationItem notification : notifications) {
                if (!notification.isRead()) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 모든 알림 삭제
     * @return 삭제 성공 여부
     */
    public synchronized boolean clearAllNotifications() {
        try {
            if (notifications != null) {
                notifications.clear();
                saveNotifications();
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 특정 ID의 알림이 존재하는지 확인
     * @param id 확인할 알림 ID
     * @return 존재 여부
     */
    public synchronized boolean hasNotification(int id) {
        try {
            if (notifications == null) {
                return false;
            }

            for (NotificationItem notification : notifications) {
                if (notification.getId() == id) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}