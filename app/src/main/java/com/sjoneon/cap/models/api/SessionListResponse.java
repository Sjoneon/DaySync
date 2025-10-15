package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 세션 목록 응답 모델
 * GET /api/ai/sessions/{user_uuid}
 */
public class SessionListResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("sessions")
    private List<SessionInfo> sessions;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<SessionInfo> getSessions() {
        return sessions;
    }

    public void setSessions(List<SessionInfo> sessions) {
        this.sessions = sessions;
    }
}