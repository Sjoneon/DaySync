package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * 사용자 정보 응답 모델
 * GET /api/users/{uuid} 응답
 */
public class UserResponse {

    @SerializedName("id")
    private int id;

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("nickname")
    private String nickname;

    @SerializedName("prep_time")
    private int prepTime;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("last_active")
    private String lastActive;

    @SerializedName("is_deleted")
    private boolean isDeleted;

    public UserResponse() {}

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getPrepTime() { return prepTime; }
    public void setPrepTime(int prepTime) { this.prepTime = prepTime; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getLastActive() { return lastActive; }
    public void setLastActive(String lastActive) { this.lastActive = lastActive; }
    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}