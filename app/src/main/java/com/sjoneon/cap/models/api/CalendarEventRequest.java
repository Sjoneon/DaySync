package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class CalendarEventRequest {
    @SerializedName("user_uuid")
    private String userUuid;

    @SerializedName("event_title")
    private String eventTitle;

    @SerializedName("event_start_time")
    private String eventStartTime;  // ISO 8601 형식  예시: 2025-10-03T15:00:00

    @SerializedName("event_end_time")
    private String eventEndTime;

    @SerializedName("description")
    private String description;

    @SerializedName("location_alias")
    private String locationAlias;

    @SerializedName("location_lat")
    private Double locationLat;

    @SerializedName("location_lng")
    private Double locationLng;

    // 생성자
    public CalendarEventRequest(String userUuid, String eventTitle, String eventStartTime) {
        this.userUuid = userUuid;
        this.eventTitle = eventTitle;
        this.eventStartTime = eventStartTime;
    }

    // Getter & Setter
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }

    public String getEventTitle() { return eventTitle; }
    public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }

    public String getEventStartTime() { return eventStartTime; }
    public void setEventStartTime(String eventStartTime) { this.eventStartTime = eventStartTime; }

    public String getEventEndTime() { return eventEndTime; }
    public void setEventEndTime(String eventEndTime) { this.eventEndTime = eventEndTime; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocationAlias() { return locationAlias; }
    public void setLocationAlias(String locationAlias) { this.locationAlias = locationAlias; }

    public Double getLocationLat() { return locationLat; }
    public void setLocationLat(Double locationLat) { this.locationLat = locationLat; }

    public Double getLocationLng() { return locationLng; }
    public void setLocationLng(Double locationLng) { this.locationLng = locationLng; }
}