package com.sjoneon.cap.models.local;

import java.util.ArrayList;
import java.util.List;

/**
 * 일정 데이터 클래스 (알림 설정 포함)
 */
public class CalendarEvent {
    private long id;
    private String title;
    private String description;
    private long dateTime;
    private List<NotificationSetting> notificationSettings;
    private long createdAt;
    private long updatedAt;
    private Integer serverId;  // 서버 DB ID 추가

    public CalendarEvent(long id, String title, String description, long dateTime) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.dateTime = dateTime;
        this.notificationSettings = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public CalendarEvent(String title, String description, long dateTime) {
        this(System.currentTimeMillis(), title, description, dateTime);
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getDateTime() { return dateTime; }
    public void setDateTime(long dateTime) {
        this.dateTime = dateTime;
        this.updatedAt = System.currentTimeMillis();
    }

    public List<NotificationSetting> getNotificationSettings() { return notificationSettings; }
    public void setNotificationSettings(List<NotificationSetting> notificationSettings) {
        this.notificationSettings = notificationSettings;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() { return createdAt; }
    public long getUpdatedAt() { return updatedAt; }

    // 서버 ID getter/setter
    public Integer getServerId() { return serverId; }
    public void setServerId(Integer serverId) { this.serverId = serverId; }

    public void addNotificationSetting(NotificationSetting setting) {
        if (notificationSettings == null) {
            notificationSettings = new ArrayList<>();
        }
        notificationSettings.add(setting);
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isPastEvent() {
        return dateTime < System.currentTimeMillis();
    }

    /**
     * 알림 설정 클래스
     */
    public static class NotificationSetting {
        public enum NotificationType {
            THREE_HOURS_BEFORE(3 * 60 * 60 * 1000L, "3시간 전"),
            ONE_DAY_BEFORE(24 * 60 * 60 * 1000L, "1일 전"),
            ONE_HOUR_BEFORE(60 * 60 * 1000L, "1시간 전"),
            THIRTY_MINUTES_BEFORE(30 * 60 * 1000L, "30분 전"),
            FIFTEEN_MINUTES_BEFORE(15 * 60 * 1000L, "15분 전"),
            FIVE_MINUTES_BEFORE(5 * 60 * 1000L, "5분 전"),
            AT_TIME_OF_EVENT(0L, "일정 시간");

            private final long millisBefore;
            private final String displayName;

            NotificationType(long millisBefore, String displayName) {
                this.millisBefore = millisBefore;
                this.displayName = displayName;
            }

            public long getMillisBefore() { return millisBefore; }
            public String getDisplayName() { return displayName; }
        }

        private NotificationType type;
        private boolean enabled;

        public NotificationSetting(NotificationType type, boolean enabled) {
            this.type = type;
            this.enabled = enabled;
        }

        public NotificationType getType() { return type; }
        public void setType(NotificationType type) { this.type = type; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public long getNotificationTime(long eventTime) {
            return eventTime - type.getMillisBefore();
        }
    }
}