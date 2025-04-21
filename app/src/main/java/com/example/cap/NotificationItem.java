package com.example.cap;

/**
 * 알림 정보를 담는 데이터 클래스
 */
public class NotificationItem {
    private int id;
    private String title;
    private String content;
    private long timestamp;
    private boolean isRead;

    public NotificationItem(int id, String title, String content, long timestamp, boolean isRead) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}