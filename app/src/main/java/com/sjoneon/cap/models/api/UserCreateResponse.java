package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * 사용자 생성 응답 모델
 * POST /api/users/ 응답
 */
public class UserCreateResponse {

    @SerializedName("uuid")
    private String uuid;

    @SerializedName("nickname")
    private String nickname;

    @SerializedName("prep_time")
    private int prepTime;

    @SerializedName("message")
    private String message;

    public UserCreateResponse() {}

    // Getters and Setters
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getPrepTime() { return prepTime; }
    public void setPrepTime(int prepTime) { this.prepTime = prepTime; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}