package com.sjoneon.cap;

/**
 * 알림 정보를 담는 데이터 클래스
 */
public class NotificationItem {
    private int id;
    private String title;
    private String content;
    private long timestamp;
    private boolean isRead;

    /**
     * 생성자
     * @param id 알림 ID
     * @param title 알림 제목
     * @param content 알림 내용
     * @param timestamp 알림 생성 시간
     * @param isRead 읽음 여부
     */
    public NotificationItem(int id, String title, String content, long timestamp, boolean isRead) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.isRead = isRead;
    }

    /**
     * 알림 ID 반환
     * @return 알림 ID
     */
    public int getId() {
        return id;
    }

    /**
     * 알림 제목 반환
     * @return 알림 제목
     */
    public String getTitle() {
        return title;
    }

    /**
     * 알림 내용 반환
     * @return 알림 내용
     */
    public String getContent() {
        return content;
    }

    /**
     * 알림 생성 시간 반환
     * @return 알림 생성 시간
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 읽음 여부 반환
     * @return 읽음 여부
     */
    public boolean isRead() {
        return isRead;
    }

    /**
     * 읽음 여부 설정
     * @param read 읽음 여부
     */
    public void setRead(boolean read) {
        isRead = read;
    }
}