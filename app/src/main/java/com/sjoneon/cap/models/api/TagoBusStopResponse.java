// TagoBusStopResponse.java
package com.sjoneon.cap.models.api;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TagoBusStopResponse {
    @SerializedName("response") public Response response;
    public static class Response { @SerializedName("body") public Body body; }
    public static class Body { @SerializedName("items") public JsonElement items; }
    public static class Items { @SerializedName("item") public List<BusStop> item; }
    public static class BusStop {
        @SerializedName("citycode") public String citycode;
        @SerializedName("gpslati") public double gpslati;
        @SerializedName("gpslong") public double gpslong;
        @SerializedName("nodeid") public String nodeid;
        @SerializedName("nodenm") public String nodenm;
    }
}