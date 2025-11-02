package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class RouteListResponse {
    @SerializedName("routes")
    private List<RouteResponse> routes;

    // Getter
    public List<RouteResponse> getRoutes() { return routes; }
}