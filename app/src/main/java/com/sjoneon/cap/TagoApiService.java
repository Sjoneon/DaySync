package com.sjoneon.cap;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface TagoApiService {

    // 주변 정류장 검색
    @GET("BusSttnInfoInqireService/getCrdntPrxmtSttnList")
    Call<TagoBusStopResponse> getNearbyBusStops(
            @Query("serviceKey") String serviceKey,
            @Query("gpsLati") double latitude,
            @Query("gpsLong") double longitude,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );

    // [수정] 공식 문서에 명시된 정확한 경로로 수정합니다.
    @GET("ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList")
    Call<TagoBusArrivalResponse> getBusArrivalInfo(
            @Query("serviceKey") String serviceKey,
            @Query("cityCode") String cityCode,
            @Query("nodeId") String nodeId,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );

    // 버스 노선별 경유 정류장 목록 조회
    @GET("BusRouteInfoInqireService/getRouteAcctoThrghSttnList")
    Call<TagoBusRouteStationResponse> getBusRouteStationList(
            @Query("serviceKey") String serviceKey,
            @Query("cityCode") String cityCode,
            @Query("routeId") String routeId,
            @Query("numOfRows") int numOfRows,
            @Query("pageNo") int pageNo,
            @Query("_type") String type
    );
}