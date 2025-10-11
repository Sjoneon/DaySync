package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class AlarmUpdateRequest {
    @SerializedName("alarm_time")
    private String alarmTime;

    @SerializedName("label")
    private String label;

    @SerializedName("repeat_days")
    private String repeatDays;

    public AlarmUpdateRequest(String alarmTime, String label) {
        this.alarmTime = alarmTime;
        this.label = label;
    }

    public String getAlarmTime() {
        return alarmTime;
    }

    public void setAlarmTime(String alarmTime) {
        this.alarmTime = alarmTime;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRepeatDays() {
        return repeatDays;
    }

    public void setRepeatDays(String repeatDays) {
        this.repeatDays = repeatDays;
    }
}