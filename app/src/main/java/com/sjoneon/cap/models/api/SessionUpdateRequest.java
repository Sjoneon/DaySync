package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

/**
 * 세션 제목 수정 요청 모델
 * PUT /api/ai/sessions/{session_id}
 */
public class SessionUpdateRequest {

    @SerializedName("title")
    private String title;

    public SessionUpdateRequest(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}