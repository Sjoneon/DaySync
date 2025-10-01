// NaverDirectionsResponse.java 파일의 내용을 아래 코드로 교체하세요.

package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class NaverDirectionsResponse {

    @SerializedName("route") // 최상위 'route' 객체 추가
    private Route route;

    public Route getRoute() {
        return route;
    }

    public static class Route {
        @SerializedName("traoptimal")
        private List<Traoptimal> traoptimal;

        public List<Traoptimal> getTraoptimal() {
            return traoptimal;
        }
    }

    public static class Traoptimal {
        @SerializedName("summary")
        private Summary summary;
        @SerializedName("path")
        private List<List<Double>> path;

        public Summary getSummary() {
            return summary;
        }

        public List<List<Double>> getPath() {
            return path;
        }
    }

    public static class Summary {
        @SerializedName("distance")
        private int distance;
        @SerializedName("duration")
        private int duration;
        @SerializedName("taxiFare")
        private int taxiFare;

        public int getDistance() {
            return distance;
        }

        public int getDuration() {
            return duration;
        }

        public int getTaxiFare() {
            return taxiFare;
        }
    }
}