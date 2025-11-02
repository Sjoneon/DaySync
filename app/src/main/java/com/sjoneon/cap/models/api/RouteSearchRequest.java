package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class RouteSearchRequest {
    @SerializedName("start_lat")
    private double startLat;

    @SerializedName("start_lng")
    private double startLng;

    @SerializedName("end_lat")
    private double endLat;

    @SerializedName("end_lng")
    private double endLng;

    public RouteSearchRequest(double startLat, double startLng, double endLat, double endLng) {
        this.startLat = startLat;
        this.startLng = startLng;
        this.endLat = endLat;
        this.endLng = endLng;
    }

    // Getters
    public double getStartLat() { return startLat; }
    public double getStartLng() { return startLng; }
    public double getEndLat() { return endLat; }
    public double getEndLng() { return endLng; }
}