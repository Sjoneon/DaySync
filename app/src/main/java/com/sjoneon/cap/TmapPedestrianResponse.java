package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TmapPedestrianResponse {
    @SerializedName("features")
    private List<Feature> features;

    public List<Feature> getFeatures() { return features; }

    public static class Feature {
        @SerializedName("properties")
        private Properties properties;

        public Properties getProperties() { return properties; }
    }

    public static class Properties {
        @SerializedName("totalTime")
        private int totalTime; // 초 단위

        public int getTotalTime() { return totalTime; }
    }
}