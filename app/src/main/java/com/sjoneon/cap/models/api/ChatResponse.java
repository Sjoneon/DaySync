package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * AI 채팅 응답 모델
 * POST /api/ai/chat 응답
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
    private String error; // 오류 시 사용

    public ChatResponse() {}

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getAiResponse() { return aiResponse; }
    public void setAiResponse(String aiResponse) { this.aiResponse = aiResponse; }
    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }
    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}