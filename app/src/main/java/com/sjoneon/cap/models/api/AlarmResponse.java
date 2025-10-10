package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class AlarmResponse {
    @SerializedName("id")
    private int id;

    @SerializedName("user_uuid")
    private String userUuid;

    @SerializedName("alarm_time")
    private String alarmTime;

    @SerializedName("label")
    private String label;

    @SerializedName("is_enabled")
    private boolean isEnabled;

    @SerializedName("repeat_days")
    private String repeatDays;

    @SerializedName("created_at")
    private String createdAt;

    // Getter
    public int getId() { return id; }
    public String getUserUuid() { return userUuid; }
    public String getAlarmTime() { return alarmTime; }
    public String getLabel() { return label; }
    public boolean isEnabled() { return isEnabled; }
    public String getRepeatDays() { return repeatDays; }
    public String getCreatedAt() { return createdAt; }
}