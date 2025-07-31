package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// 자동차 길찾기 응답을 위한 데이터 모델
public class NaverDirectionsResponse {
    @SerializedName("route")
    private Route route;
    public Route getRoute() { return route; }

    public static class Route {
        @SerializedName("traoptimal")
        private List<Traoptimal> traoptimal;
        public List<Traoptimal> getTraoptimal() { return traoptimal; }
    }

    public static class Traoptimal {
        @SerializedName("summary")
        private Summary summary;
        @SerializedName("path")
        private List<List<Double>> path;
        public Summary getSummary() { return summary; }
        public List<List<Double>> getPath() { return path; }
    }

    public static class Summary {
        @SerializedName("distance")
        private int distance;
        @SerializedName("duration")
        private int duration;
        @SerializedName("taxiFare")
        private int taxiFare;
        public int getDistance() { return distance; }
        public int getDuration() { return duration; }
        public int getTaxiFare() { return taxiFare; }
    }
}