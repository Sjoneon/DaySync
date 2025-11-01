package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

public class TmapPedestrianResponse {
    @SerializedName("features")
    private List<Feature> features;

    public List<Feature> getFeatures() {
        return features;
    }

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

    public static class Properties {
        @SerializedName("totalTime")
        private int totalTime;

        @SerializedName("totalDistance")
        private int totalDistance;

        @SerializedName("index")
        private int index;

        @SerializedName("pointIndex")
        private int pointIndex;

        @SerializedName("name")
        private String name;

        @SerializedName("description")
        private String description;

        @SerializedName("direction")
        private String direction;

        @SerializedName("intersectionName")
        private String intersectionName;

        @SerializedName("turnType")
        private int turnType;

        @SerializedName("pointType")
        private String pointType;

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

    public static class Geometry {
        @SerializedName("type")
        private String type;

        @SerializedName("coordinates")
        private JsonElement coordinates;

        public String getType() {
            return type;
        }

        public JsonElement getCoordinates() {
            return coordinates;
        }

        /**
         * coordinates를 안전하게 파싱하여 좌표 배열로 반환
         */
        public List<List<Double>> getCoordinatesAsList() {
            if (coordinates == null || coordinates.isJsonNull()) {
                return null;
            }

            try {
                if (coordinates.isJsonArray()) {
                    return parseCoordinatesArray(coordinates);
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * JsonElement 배열을 좌표 리스트로 변환 (구현 완료)
         */
        private List<List<Double>> parseCoordinatesArray(JsonElement coordsElement) {
            List<List<Double>> result = new ArrayList<>();

            try {
                if (!coordsElement.isJsonArray()) {
                    return result;
                }

                JsonArray coordsArray = coordsElement.getAsJsonArray();

                for (JsonElement element : coordsArray) {
                    if (element.isJsonArray()) {
                        JsonArray coordPair = element.getAsJsonArray();
                        List<Double> pair = new ArrayList<>();

                        // 각 좌표 쌍을 추출 (일반적으로 [경도, 위도])
                        for (JsonElement coord : coordPair) {
                            if (coord.isJsonPrimitive() && coord.getAsJsonPrimitive().isNumber()) {
                                pair.add(coord.getAsDouble());
                            }
                        }

                        // 최소 2개의 값(경도, 위도)이 있어야 유효한 좌표
                        if (pair.size() >= 2) {
                            result.add(pair);
                        }
                    }
                }

            } catch (Exception e) {
                // 파싱 실패 시 빈 리스트 반환
                return new ArrayList<>();
            }

            return result;
        }
    }
}