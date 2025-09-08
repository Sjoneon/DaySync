package com.sjoneon.cap;

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
            THREE_DAYS_BEFORE(3 * 24 * 60 * 60 * 1000L, "3일 전"),
            ONE_WEEK_BEFORE(7 * 24 * 60 * 60 * 1000L, "1주일 전");

            private final long milliseconds;
            private final String displayName;

            NotificationType(long milliseconds, String displayName) {
                this.milliseconds = milliseconds;
                this.displayName = displayName;
            }

            public long getMilliseconds() { return milliseconds; }
            public String getDisplayName() { return displayName; }
        }

        private NotificationType type;
        private boolean isEnabled;
        private int requestCode;

        public NotificationSetting(NotificationType type, boolean isEnabled) {
            this.type = type;
            this.isEnabled = isEnabled;
            this.requestCode = (int) (System.currentTimeMillis() & 0xfffffff);
        }

        public NotificationType getType() { return type; }
        public boolean isEnabled() { return isEnabled; }
        public void setEnabled(boolean enabled) { isEnabled = enabled; }
        public int getRequestCode() { return requestCode; }

        public long getNotificationTime(long eventDateTime) {
            return eventDateTime - type.getMilliseconds();
        }
    }
}