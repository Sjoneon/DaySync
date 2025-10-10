package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class AlarmRequest {
    @SerializedName("user_uuid")
    private String userUuid;

    @SerializedName("alarm_time")
    private String alarmTime;  // ISO 8601 형식

    @SerializedName("label")
    private String label;

    @SerializedName("calendar_event_id")
    private Integer calendarEventId;

    @SerializedName("is_enabled")
    private boolean isEnabled;

    @SerializedName("repeat_days")
    private String repeatDays;  // 예: "0,1,2,3,4" (월~금)

    @SerializedName("sound_enabled")
    private boolean soundEnabled;

    @SerializedName("vibration_enabled")
    private boolean vibrationEnabled;

    // 생성자
    public AlarmRequest(String userUuid, String alarmTime, String label) {
        this.userUuid = userUuid;
        this.alarmTime = alarmTime;
        this.label = label;
        this.isEnabled = true;
        this.soundEnabled = true;
        this.vibrationEnabled = true;
    }

    // Getter & Setter
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }

    public String getAlarmTime() { return alarmTime; }
    public void setAlarmTime(String alarmTime) { this.alarmTime = alarmTime; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Integer getCalendarEventId() { return calendarEventId; }
    public void setCalendarEventId(Integer calendarEventId) { this.calendarEventId = calendarEventId; }

    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }

    public String getRepeatDays() { return repeatDays; }
    public void setRepeatDays(String repeatDays) { this.repeatDays = repeatDays; }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }

    public boolean isVibrationEnabled() { return vibrationEnabled; }
    public void setVibrationEnabled(boolean vibrationEnabled) { this.vibrationEnabled = vibrationEnabled; }
}