package com.sjoneon.cap.models.local;

/**
 * 채팅 메시지를 나타내는 데이터 클래스
 */
public class Message {
    private String content;     // 메시지 내용
    private boolean isUser;     // 사용자 메시지 여부 (true: 사용자, false: AI)
    private long timestamp;     // 메시지 타임스탬프

    /**
     * 생성자
     * @param content 메시지 내용
     * @param isUser 사용자 메시지 여부
     */
    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 메시지 내용 반환
     * @return 메시지 내용
     */
    public String getContent() {
        return content;
    }

    /**
     * 사용자 메시지 여부 반환
     * @return 사용자 메시지 여부 (true: 사용자, false: AI)
     */
    public boolean isUser() {
        return isUser;
    }

    /**
     * 메시지 타임스탬프 반환
     * @return 메시지 타임스탬프
     */
    public long getTimestamp() {
        return timestamp;
    }
}