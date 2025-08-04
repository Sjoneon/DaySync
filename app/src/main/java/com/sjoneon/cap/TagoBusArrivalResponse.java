package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TagoBusArrivalResponse {
    @SerializedName("response")
    public Response response;

    public static class Response {
        @SerializedName("body")
        public Body body;
    }

    public static class Body {
        @SerializedName("items")
        public Items items;
    }

    public static class Items {
        @SerializedName("item")
        public List<BusArrival> item;
    }

    public static class BusArrival {
        @SerializedName("routeid")
        public String routeid; // 노선 ID
        @SerializedName("routeno")
        public String routeno; // 노선 번호
        @SerializedName("arrprevstationcnt")
        public int arrprevstationcnt; // 남은 정류장 수
        @SerializedName("arrtime")
        public int arrtime; // 도착예정시간(초)
    }
}