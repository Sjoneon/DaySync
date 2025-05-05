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
 * 알림 데이터를 관리하는 저장소 클래스
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
    private void loadNotifications() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_NOTIFICATIONS, null);

        if (json != null) {
            Type type = new TypeToken<ArrayList<NotificationItem>>() {}.getType();
            notifications = gson.fromJson(json, type);
        } else {
            notifications = new ArrayList<>();
        }
    }

    /**
     * 알림 저장
     */
    private void saveNotifications() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String json = gson.toJson(notifications);
        editor.putString(KEY_NOTIFICATIONS, json);
        editor.apply();
    }

    /**
     * 모든 알림 가져오기
     * @return 알림 목록 (최신순)
     */
    public List<NotificationItem> getAllNotifications() {
        // 시간 역순으로 정렬 (최신 알림이 먼저 표시되도록)
        Collections.sort(notifications, new Comparator<NotificationItem>() {
            @Override
            public int compare(NotificationItem o1, NotificationItem o2) {
                return Long.compare(o2.getTimestamp(), o1.getTimestamp());
            }
        });
        return notifications;
    }

    /**
     * 새 알림 추가
     * @param notification 추가할 알림
     */
    public void addNotification(NotificationItem notification) {
        notifications.add(notification);
        saveNotifications();
    }

    /**
     * 알림 삭제
     * @param id 삭제할 알림 ID
     */
    public void deleteNotification(int id) {
        for (int i = 0; i < notifications.size(); i++) {
            if (notifications.get(i).getId() == id) {
                notifications.remove(i);
                saveNotifications();
                break;
            }
        }
    }

    /**
     * 알림 읽음 처리
     * @param id 알림 ID
     */
    public void markAsRead(int id) {
        for (NotificationItem notification : notifications) {
            if (notification.getId() == id) {
                notification.setRead(true);
                saveNotifications();
                break;
            }
        }
    }

    /**
     * 모든 알림 읽음 처리
     */
    public void markAllAsRead() {
        for (NotificationItem notification : notifications) {
            notification.setRead(true);
        }
        saveNotifications();
    }

    /**
     * 읽지 않은 알림 개수
     * @return 읽지 않은 알림 개수
     */
    public int getUnreadCount() {
        int count = 0;
        for (NotificationItem notification : notifications) {
            if (!notification.isRead()) {
                count++;
            }
        }
        return count;
    }
}