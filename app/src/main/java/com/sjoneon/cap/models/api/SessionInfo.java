package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * 세션 정보 모델
 * GET /api/ai/sessions/{user_uuid}
 */
public class SessionInfo {

    @SerializedName("id")
    private int id;

    @SerializedName("title")
    private String title;

    @SerializedName("category")
    private String category;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("updated_at")
    private String updatedAt;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}