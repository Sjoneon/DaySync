package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * AI 채팅 요청 모델
 * POST /api/ai/chat
 */
public class ChatRequest {

    @SerializedName("user_uuid")
    private String userUuid;

    @SerializedName("message")
    private String message;

    @SerializedName("session_id")
    private Integer sessionId; // nullable, 첫 메시지는 null

    @SerializedName("context")
    private Map<String, Object> context; // nullable, 추가 컨텍스트 정보

    /**
     * 생성자 (새 세션, context 없음)
     */
    public ChatRequest(String userUuid, String message) {
        this.userUuid = userUuid;
        this.message = message;
        this.sessionId = null;
        this.context = null;
    }

    /**
     * 생성자 (기존 세션, context 없음)
     */
    public ChatRequest(String userUuid, String message, Integer sessionId) {
        this.userUuid = userUuid;
        this.message = message;
        this.sessionId = sessionId;
        this.context = null;
    }

    /**
     * 생성자 (기존 세션 + context)
     */
    public ChatRequest(String userUuid, String message, Integer sessionId, Map<String, Object> context) {
        this.userUuid = userUuid;
        this.message = message;
        this.sessionId = sessionId;
        this.context = context;
    }

    // Getters and Setters
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
}