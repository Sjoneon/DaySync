package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RouteResponse {
    @SerializedName("id")
    private int id;

    @SerializedName("start_lat")
    private double startLat;

    @SerializedName("start_lng")
    private double startLng;

    @SerializedName("end_lat")
    private double endLat;

    @SerializedName("end_lng")
    private double endLng;

    @SerializedName("route_data")
    private List<RouteSaveRequest.RouteDataItem> routeData;

    @SerializedName("created_at")
    private String createdAt;

    // Getters
    public int getId() { return id; }
    public double getStartLat() { return startLat; }
    public double getStartLng() { return startLng; }
    public double getEndLat() { return endLat; }
    public double getEndLng() { return endLng; }
    public List<RouteSaveRequest.RouteDataItem> getRouteData() { return routeData; }
    public String getCreatedAt() { return createdAt; }
}