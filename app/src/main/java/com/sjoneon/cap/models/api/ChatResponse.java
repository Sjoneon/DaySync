package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * AI 채팅 응답 모델
 * FastAPI 백엔드의 ChatResponse와 매핑됩니다.
 */
public class ChatResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("ai_response")
    private String aiResponse;

    @SerializedName("session_id")
    private Integer sessionId;

    @SerializedName("message_id")
    private Integer messageId;

    @SerializedName("error")
    private String error;

    // ===== [새로 추가] Function Call 필드 =====
    @SerializedName("function_called")
    private String functionCalled;
    // ==========================================

    // 생성자
    public ChatResponse(boolean success, String aiResponse, Integer sessionId, Integer messageId) {
        this.success = success;
        this.aiResponse = aiResponse;
        this.sessionId = sessionId;
        this.messageId = messageId;
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getAiResponse() {
        return aiResponse;
    }

    public Integer getSessionId() {
        return sessionId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public String getError() {
        return error;
    }

    // ===== [새로 추가] Getter =====
    public String getFunctionCalled() {
        return functionCalled;
    }
    // ================================

    // Setters (필요시 사용)
    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void setAiResponse(String aiResponse) {
        this.aiResponse = aiResponse;
    }

    public void setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setFunctionCalled(String functionCalled) {
        this.functionCalled = functionCalled;
    }

    @Override
    public String toString() {
        return "ChatResponse{" +
                "success=" + success +
                ", aiResponse='" + aiResponse + '\'' +
                ", sessionId=" + sessionId +
                ", messageId=" + messageId +
                ", error='" + error + '\'' +
                ", functionCalled='" + functionCalled + '\'' +
                '}';
    }
}