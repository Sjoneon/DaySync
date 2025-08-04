// TagoBusArrivalResponse.java
package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TagoBusArrivalResponse {
    @SerializedName("response") public Response response;
    public static class Response { @SerializedName("body") public Body body; }
    public static class Body { @SerializedName("items") public Items items; }
    public static class Items { @SerializedName("item") public List<BusArrival> item; }
    public static class BusArrival {
        @SerializedName("routeid") public String routeid;
        @SerializedName("routeno") public String routeno;
        @SerializedName("arrprevstationcnt") public int arrprevstationcnt;
        @SerializedName("arrtime") public int arrtime; // 초 단위
    }
}