package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class CalendarEventResponse {
    @SerializedName("id")
    private int id;

    @SerializedName("user_uuid")
    private String userUuid;

    @SerializedName("event_title")
    private String eventTitle;

    @SerializedName("event_start_time")
    private String eventStartTime;

    @SerializedName("event_end_time")
    private String eventEndTime;

    @SerializedName("description")
    private String description;

    @SerializedName("location_alias")
    private String locationAlias;

    @SerializedName("created_at")
    private String createdAt;

    // Getter
    public int getId() { return id; }
    public String getUserUuid() { return userUuid; }
    public String getEventTitle() { return eventTitle; }
    public String getEventStartTime() { return eventStartTime; }
    public String getEventEndTime() { return eventEndTime; }
    public String getDescription() { return description; }
    public String getLocationAlias() { return locationAlias; }
    public String getCreatedAt() { return createdAt; }
}