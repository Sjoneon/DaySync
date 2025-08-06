package com.sjoneon.cap;

import com.google.gson.JsonElement; // JsonElement 임포트
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TagoBusArrivalResponse {
    @SerializedName("response") public Response response;

    public static class Response {
        @SerializedName("body") public Body body;
    }

    public static class Body {
        // [수정] String 또는 Object 타입을 모두 받을 수 있도록 JsonElement로 변경
        @SerializedName("items") public JsonElement items;
    }

    // 이 클래스들은 이제 직접 사용되지 않고, 아래 Deserializer에서 사용됩니다.
    public static class ItemsContainer {
        @SerializedName("item") public List<BusArrival> item;
    }

    public static class BusArrival {
        @SerializedName("routeid") public String routeid;
        @SerializedName("routeno") public String routeno;
        @SerializedName("arrprevstationcnt") public int arrprevstationcnt;
        @SerializedName("arrtime") public int arrtime;
    }
}