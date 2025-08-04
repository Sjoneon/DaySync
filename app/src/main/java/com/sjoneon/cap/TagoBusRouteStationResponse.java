package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TagoBusRouteStationResponse {
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
        public List<RouteStation> item;
    }

    public static class RouteStation {
        @SerializedName("nodeid")
        public String nodeid;
        @SerializedName("nodenm")
        public String nodenm;
        @SerializedName("ord")
        public int ord; // 순번
    }
}