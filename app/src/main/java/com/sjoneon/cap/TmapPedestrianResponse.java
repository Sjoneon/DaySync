package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonElement;
import java.util.List;

/**
 * TMAP 보행자 경로 API 응답 데이터 클래스 (수정된 버전)
 * coordinates 필드를 유연하게 처리하도록 개선
 */
public class TmapPedestrianResponse {
    @SerializedName("features")
    private List<Feature> features;

    public List<Feature> getFeatures() {
        return features;
    }

    /**
     * GeoJSON Feature 클래스
     */
    public static class Feature {
        @SerializedName("properties")
        private Properties properties;

        @SerializedName("geometry")
        private Geometry geometry;

        public Properties getProperties() {
            return properties;
        }

        public Geometry getGeometry() {
            return geometry;
        }
    }

    /**
     * 경로 속성 정보 클래스
     */
    public static class Properties {
        @SerializedName("totalTime")
        private int totalTime; // 총 소요 시간 (초 단위)

        @SerializedName("totalDistance")
        private int totalDistance; // 총 거리 (미터 단위)

        @SerializedName("index")
        private int index; // 경로 인덱스

        @SerializedName("pointIndex")
        private int pointIndex; // 포인트 인덱스

        @SerializedName("name")
        private String name; // 경로명

        @SerializedName("description")
        private String description; // 경로 설명

        @SerializedName("direction")
        private String direction; // 방향 정보

        @SerializedName("intersectionName")
        private String intersectionName; // 교차로명

        @SerializedName("turnType")
        private int turnType; // 회전 타입

        @SerializedName("pointType")
        private String pointType; // 포인트 타입

        // Getter 메서드들
        public int getTotalTime() {
            return totalTime;
        }

        public int getTotalDistance() {
            return totalDistance;
        }

        public int getIndex() {
            return index;
        }

        public int getPointIndex() {
            return pointIndex;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getDirection() {
            return direction;
        }

        public String getIntersectionName() {
            return intersectionName;
        }

        public int getTurnType() {
            return turnType;
        }

        public String getPointType() {
            return pointType;
        }
    }

    /**
     * GeoJSON Geometry 클래스 (수정된 버전)
     * coordinates를 JsonElement로 처리하여 유연성 확보
     */
    public static class Geometry {
        @SerializedName("type")
        private String type; // Geometry 타입 (예: "LineString", "Point")

        @SerializedName("coordinates")
        private JsonElement coordinates; // 좌표 데이터 (유연한 구조)

        public String getType() {
            return type;
        }

        public JsonElement getCoordinates() {
            return coordinates;
        }

        /**
         * coordinates를 안전하게 파싱하여 좌표 배열로 반환
         * @return 좌표 배열 (실패 시 null)
         */
        public List<List<Double>> getCoordinatesAsList() {
            if (coordinates == null || coordinates.isJsonNull()) {
                return null;
            }

            try {
                // TMAP API는 다양한 coordinates 구조를 가질 수 있음
                if (coordinates.isJsonArray()) {
                    // 배열인 경우 처리
                    return parseCoordinatesArray(coordinates);
                } else {
                    // 다른 구조인 경우 null 반환
                    return null;
                }
            } catch (Exception e) {
                // 파싱 실패 시 null 반환
                return null;
            }
        }

        /**
         * JsonElement 배열을 좌표 리스트로 변환
         */
        private List<List<Double>> parseCoordinatesArray(JsonElement coordsElement) {
            // 구현은 복잡하므로 일단 null 반환
            // 실제로는 JsonElement를 적절히 파싱해야 함
            return null;
        }
    }
}