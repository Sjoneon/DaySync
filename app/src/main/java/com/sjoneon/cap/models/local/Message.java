package com.sjoneon.cap.models.local;

/**
 * 채팅 메시지 데이터 클래스
 */
public class Message {
    private String content;     // 메시지 내용
    private boolean isUser;     // 사용자 메시지 여부 (true: 사용자, false: AI)
    private long timestamp;     // 메시지 타임스탬프

    /**
     * 기본 생성자 (현재 시간 사용)
     */
    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 타임스탬프 지정 생성자 (서버 데이터 복원용)
     */
    public Message(String content, boolean isUser, long timestamp) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
    }

    public String getContent() {
        return content;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }
}