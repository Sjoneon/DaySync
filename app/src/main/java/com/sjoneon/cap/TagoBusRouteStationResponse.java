package com.sjoneon.cap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

// 국토교통부_버스 노선정보 조회 서비스의 '노선별 경유 정류소 목록 조회' 응답을 위한 데이터 클래스
public class TagoBusRouteStationResponse {

    @SerializedName("response")
    public ResponseData response;

    public static class ResponseData {
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
        @SerializedName("items")
        public Items items;
    }

    public static class Items {
        @SerializedName("item")
        public List<RouteStation> item;
    }

    public static class RouteStation {
        // [핵심] API 응답에 맞춰 위도(gpslati), 경도(gpslong) 변수 선언
        @SerializedName("gpslati")
        public double gpslati; // 위도

        @SerializedName("gpslong")
        public double gpslong; // 경도

        @SerializedName("nodeid")
        public String nodeid; // 정류소 ID

        @SerializedName("nodenm")
        public String nodenm; // 정류소명

        @SerializedName("ord")
        public int ord; // 순번
    }
}