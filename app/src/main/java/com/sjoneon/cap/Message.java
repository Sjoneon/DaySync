package com.sjoneon.cap;

/**
 * 채팅 메시지를 나타내는 데이터 클래스
 */
public class Message {
    private String content;     // 메시지 내용
    private boolean isUser;     // 사용자 메시지 여부 (true: 사용자, false: AI)
    private long timestamp;     // 메시지 타임스탬프

    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
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