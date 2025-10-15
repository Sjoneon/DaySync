package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * 메시지 정보 모델
 * GET /api/ai/sessions/{session_id}/messages
 */
public class MessageInfo {

    @SerializedName("id")
    private int id;

    @SerializedName("content")
    private String content;

    @SerializedName("is_user")
    private boolean isUser;

    @SerializedName("created_at")
    private String createdAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}