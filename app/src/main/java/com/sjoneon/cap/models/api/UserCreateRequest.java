package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * 사용자 생성 요청 모델
 * POST /api/users/
 */
public class UserCreateRequest {

    @SerializedName("nickname")
    private String nickname;

    @SerializedName("prep_time")
    private int prepTime;

    public UserCreateRequest(String nickname) {
        this.nickname = nickname;
        this.prepTime = 1800; // 30분
    }

    public UserCreateRequest(String nickname, int prepTime) {
        this.nickname = nickname;
        this.prepTime = prepTime;
    }

    // Getters and Setters
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public int getPrepTime() { return prepTime; }
    public void setPrepTime(int prepTime) { this.prepTime = prepTime; }
}