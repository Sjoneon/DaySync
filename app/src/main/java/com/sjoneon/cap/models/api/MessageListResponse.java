package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 메시지 목록 응답 모델
 * GET /api/ai/sessions/{session_id}/messages
 */
public class MessageListResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("messages")
    private List<MessageInfo> messages;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<MessageInfo> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageInfo> messages) {
        this.messages = messages;
    }
}