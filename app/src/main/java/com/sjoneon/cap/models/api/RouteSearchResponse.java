package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;

public class RouteSearchResponse {
    @SerializedName("found")
    private boolean found;

    @SerializedName("route")
    private RouteResponse route;

    // Getters
    public boolean isFound() { return found; }
    public RouteResponse getRoute() { return route; }
}