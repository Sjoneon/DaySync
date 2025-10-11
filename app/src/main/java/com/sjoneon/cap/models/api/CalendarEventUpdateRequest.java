package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class CalendarEventUpdateRequest {
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

    public CalendarEventUpdateRequest(String eventTitle, String eventStartTime) {
        this.eventTitle = eventTitle;
        this.eventStartTime = eventStartTime;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public String getEventStartTime() {
        return eventStartTime;
    }

    public void setEventStartTime(String eventStartTime) {
        this.eventStartTime = eventStartTime;
    }

    public String getEventEndTime() {
        return eventEndTime;
    }

    public void setEventEndTime(String eventEndTime) {
        this.eventEndTime = eventEndTime;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocationAlias() {
        return locationAlias;
    }

    public void setLocationAlias(String locationAlias) {
        this.locationAlias = locationAlias;
    }
}