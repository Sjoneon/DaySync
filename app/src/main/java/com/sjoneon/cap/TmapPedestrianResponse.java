package com.sjoneon.cap;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmapPedestrianResponse {
    @SerializedName("features")
    private List<Feature> features;

    public List<Feature> getFeatures() { return features; }

    public static class Feature {
        @SerializedName("geometry")
        private Geometry geometry;

        @SerializedName("properties")
        private Properties properties;

        public Geometry getGeometry() { return geometry; }
        public Properties getProperties() { return properties; }
    }

    public static class Geometry {
        @SerializedName("type")
        private String type;

        // [핵심 수정] 우리가 직접 만들 Deserializer를 지정합니다.
        @SerializedName("coordinates")
        @JsonAdapter(CoordinatesDeserializer.class)
        private List<List<Double>> coordinates;

        public String getType() { return type; }
        public List<List<Double>> getCoordinates() { return coordinates; }
    }

    public static class Properties {
        @SerializedName("totalTime")
        private int totalTime;

        public int getTotalTime() { return totalTime; }
    }
}