// app/src/main/java/com/sjoneon/cap/WeatherResponse.java

package com.sjoneon.cap.models.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// API 응답 전체를 감싸는 클래스
public class WeatherResponse {
    @SerializedName("response")
    public Response response;

    public static class Response {
        @SerializedName("header")
        public Header header;
        @SerializedName("body")
        public Body body;
    }

    public static class Header {
        @SerializedName("resultCode")
        public String resultCode;
        @SerializedName("resultMsg")
        public String resultMsg;
    }

    public static class Body {
        @SerializedName("dataType")
        public String dataType;
        @SerializedName("items")
        public Items items;
    }

    public static class Items {
        @SerializedName("item")
        public List<WeatherItem> item;
    }

    // 실제 날씨 데이터를 담는 클래스
    public static class WeatherItem {
        @SerializedName("baseDate")
        public String baseDate; // 발표일자
        @SerializedName("baseTime")
        public String baseTime; // 발표시각
        @SerializedName("category")
        public String category; // 자료구분코드
        @SerializedName("fcstDate")
        public String fcstDate; // 예보일자
        @SerializedName("fcstTime")
        public String fcstTime; // 예보시각
        @SerializedName("fcstValue")
        public String fcstValue; // 예보값
        @SerializedName("nx")
        public int nx; // x좌표
        @SerializedName("ny")
        public int ny; // y좌표
    }
}